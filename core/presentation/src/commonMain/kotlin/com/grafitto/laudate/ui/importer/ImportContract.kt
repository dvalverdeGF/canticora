package com.grafitto.laudate.ui.importer

import com.grafitto.laudate.domain.transfer.DetectedLaudateFile
import com.grafitto.laudate.domain.transfer.ImportConflict
import com.grafitto.laudate.domain.transfer.ImportConflictPolicy

/**
 * Estado de la pantalla **Importar**.
 *
 * Centraliza las cuatro vías de entrada de datos a Canticora. [isBusy] se activa mientras se procesa
 * una importación; [lastResult] describe el resultado de la última que terminó bien.
 *
 * [pendingFile] está activo cuando la app ha recibido un archivo **desde fuera** (WhatsApp, correo,
 * gestor de archivos o «Compartir»): la pantalla muestra entonces un diálogo que dice qué es y
 * ofrece importarlo, en lugar de dejar al usuario buscarlo a mano.
 */
data class ImportUiState(
    val isBusy: Boolean = false,
    /** Descripción de lo último importado, para mostrar un resumen al usuario. */
    val lastResult: String? = null,
    val errorMessage: String? = null,
    /** Archivo recibido pendiente de confirmar por el usuario. */
    val pendingFile: DetectedLaudateFile? = null,
    val pendingFileName: String? = null,
    /**
     * `Uri` del archivo recibido cuando su contenido **necesita permisos** para leerse (una copia
     * de seguridad o un paquete de canción, ambos ZIP). Para el resto de tipos es `null`.
     */
    val pendingArchiveUri: String? = null,
    /**
     * Conflicto de importación pendiente: lo que llega coincide con algo que ya existe con cambios.
     * No se ha escrito nada; se espera la decisión del usuario (sobrescribir, conservar u omitir).
     */
    val pendingConflicts: PendingImportConflicts? = null,
    /**
     * Contenido pegado o elegido que se ha **reconocido** automáticamente y aún no se ha importado:
     * la pantalla muestra qué es y pide confirmación. Es la base del flujo unificado (un solo botón).
     */
    val pendingContent: DetectedLaudateFile? = null,
    /** Texto pendiente de importar, para poder ejecutarlo cuando el usuario confirme. */
    val pendingContentPreview: String? = null,
    /**
     * `true` si lo pendiente llegÃ³ como **archivo abierto desde fuera** (deep link de Android o
     * asociaciÃ³n de ficheros de escritorio) en vez de haberse pegado o elegido en esta pantalla.
     * Las raíces pueden asÃ distinguir el camino de confirmaciÃ³n.
     */
    val pendingIsIncomingFile: Boolean = false
)

/** Conflicto de importación pendiente de resolver por el usuario. */
data class PendingImportConflicts(
    /** Título del diálogo, p. ej. «Ritmos» o «Repertorio «Misa del domingo»». */
    val title: String,
    val conflicts: List<ImportConflict>,
    val decisions: Map<String, ImportConflictPolicy> = emptyMap()
)

/**
 * Estado de los contadores de la biblioteca que se muestran en la pantalla (antes/después).
 */
data class ImportLibraryStats(
    val songCount: Int = 0,
    val celebrationCount: Int = 0,
    val categoryCount: Int = 0
)

sealed interface ImportUiIntent {
    /** Importa el **catálogo/paquete de Canticora** (JSON `BackupPayload`) ya leído. */
    data class ImportCatalog(val json: String) : ImportUiIntent

    /** Importa un **repertorio de misa/evento** (`.canticora`) ya leído. */
    data class ImportCelebration(val content: String) : ImportUiIntent

    /** Importa **patrones rítmicos** desde un archivo o desde texto pegado. */
    data class ImportRhythms(val content: String) : ImportUiIntent

    /** Importa un lote de **canciones sueltas en ChordPro**. */
    data class ImportChordProFiles(val files: List<ChordProFileInput>) : ImportUiIntent

    /**
     * Registra un archivo recibido desde fuera para mostrar el diálogo de confirmación.
     *
     * El análisis del contenido ([DetectedLaudateFile]) lo hace la capa de UI, que es quien puede
     * leer el `Uri`; aquí solo se decide qué hacer con el resultado.
     */
    data class OfferPendingFile(
        val detected: DetectedLaudateFile,
        val fileName: String?,
        /**
         * `Uri` del archivo cuando su lectura necesita permisos (copia de seguridad o paquete de
         * canción, ambos ZIP). El resto de tipos llegan con el contenido ya en texto.
         */
        val archiveUri: String? = null
    ) : ImportUiIntent

    /**
     * Registra un **texto pegado o leído de un archivo** que la UI ya ha reconocido con
     * [com.grafitto.laudate.domain.transfer.LaudateFileDetector]: la pantalla muestra el diálogo
     * «esto es X, ¿lo importo?» sin que el usuario tenga que elegir la vía.
     *
     * A diferencia de [OfferPendingFile] no hay `Uri`: el contenido ya está en memoria, y solo se
     * ejecutará **un único botón** cuando el usuario confirme ([ConfirmPendingContent]).
     */
    data class OfferContent(
        val detected: DetectedLaudateFile,
        val content: String
    ) : ImportUiIntent

    /** El usuario confirma la importación del contenido pendiente ([ImportUiState.pendingContent]). */
    data object ConfirmPendingContent : ImportUiIntent

    /** El usuario descarta el contenido pendiente. */
    data object DismissPendingContent : ImportUiIntent

    /** El usuario confirma la importación del archivo recibido. */
    data object ConfirmPendingFile : ImportUiIntent

    /** El usuario descarta el archivo recibido. */
    data object DismissPendingFile : ImportUiIntent

    /** Limpia el mensaje de resultado (tras mostrarlo). */
    data object DismissResult : ImportUiIntent

    /** El usuario decide qué hacer con un elemento que ya existe con cambios. */
    data class ResolveImportConflict(val key: String, val policy: ImportConflictPolicy) : ImportUiIntent

    /** Aplica la misma decisión a todos los conflictos pendientes. */
    data class ApplyImportPolicyToAll(val policy: ImportConflictPolicy) : ImportUiIntent

    /** Confirma la importación con las decisiones tomadas. */
    data object ConfirmImportConflicts : ImportUiIntent

    /** Cancela la importación pendiente (no se escribe nada). */
    data object CancelImportConflicts : ImportUiIntent
}

/** Par contenido + nombre de fichero de un ChordPro, tal como llega desde la capa de datos. */
data class ChordProFileInput(
    val content: String,
    val fileName: String? = null
)

/**
 * Efectos puntuales de la pantalla de importación.
 *
 * La copia de seguridad (`.zip`) no se procesa aquí porque necesita un `Uri` con permisos SAF;
 * su restauración vive en la pantalla de Copia de seguridad y se lanza desde `MainActivity`.
 */
sealed interface ImportSideEffect {
    data class ShowMessage(val message: String) : ImportSideEffect

    /** Pide a la UI abrir el selector de archivos para una copia de seguridad (SAF). */
    data object RequestBackupFile : ImportSideEffect

    /** Restaura la copia de seguridad indicada (la pantalla de Copia la abre con ese `Uri`). */
    data class RestoreBackup(val uri: String) : ImportSideEffect

    /** Avisa de que hay que recargar el catálogo tras importar canciones. */
    data object CatalogChanged : ImportSideEffect
}
