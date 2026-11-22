package com.grafitto.laudate.domain.backup

import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.transfer.ImportConflictPolicy

/**
 * Modo de restauración de una copia de seguridad.
 *
 * Son las dos operaciones que el usuario percibe como distintas:
 *  - [MERGE]: **sincronizar**. Añade lo que falta y **sobrescribe** las canciones que ya existen
 *    con el mismo id, sin borrar nada más. Es el flujo para mantener varios dispositivos al día.
 *  - [REPLACE]: **restaurar**. Borra la biblioteca local y la deja **exactamente** como el archivo.
 *    Es destructivo y exige una confirmación explícita tras analizar el archivo.
 */
enum class BackupRestoreMode { MERGE, REPLACE }

/**
 * Análisis de un archivo de copia de seguridad **sin escribir nada**, para poder mostrar al usuario
 * qué contiene y pedirle confirmación antes de una restauración destructiva.
 */
data class BackupPreview(
    val songs: Int,
    val celebrations: Int,
    val categories: Int,
    val rhythms: Int,
    val attachments: Int,
    val exportedAt: Long? = null,
    val appVersion: String? = null
) {
    val isEmpty: Boolean
        get() = songs == 0 && celebrations == 0 && categories == 0 && attachments == 0
}

/**
 * Decisión del usuario ante una canción del archivo que **ya existe** en el dispositivo con el
 * mismo identificador pero **contenido distinto**.
 *
 * Es un alias de la política genérica de importación ([ImportConflictPolicy]): la misma decisión se
 * usa al restaurar/sincronizar y al importar contenido compartido (cantos, repertorios, ritmos).
 */
typealias SongConflictPolicy = ImportConflictPolicy

/**
 * Una canción que llega en el archivo y que ya existe localmente con el mismo `id` pero con
 * contenido distinto, pendiente de que el usuario decida qué hacer.
 */
data class SongConflict(
    val songId: String,
    val title: String,
    val number: String? = null,
    /** `updatedAt` de la copia local, para poder mostrar cuál es más reciente. */
    val localUpdatedAt: Long,
    /** `updatedAt` de la versión que viaja en el archivo. */
    val incomingUpdatedAt: Long
) {
    /** `true` si la versión del archivo parece más reciente que la local. */
    val incomingIsNewer: Boolean get() = incomingUpdatedAt > localUpdatedAt
}

/**
 * Resultado de **analizar** un archivo de copia de seguridad sin escribir nada: qué contiene, cuántos
 * cantos son nuevos y qué canciones existentes entrarían en conflicto.
 *
 * Se usa para mostrar al usuario el contenido antes de restaurar y para preguntar qué hacer con las
 * coincidencias en una sincronización (ver [SongConflictPolicy]).
 */
data class BackupImportSummary(
    val preview: BackupPreview,
    val songsInBackup: Int,
    val newSongs: Int,
    val conflicts: List<SongConflict> = emptyList()
) {
    val hasConflicts: Boolean get() = conflicts.isNotEmpty()
}

/**
 * Resultado de importar una copia de seguridad completa.
 *
 * Incluye cuántos elementos se incorporaron y si la importación **reemplazó** la biblioteca
 * existente ([replaced] = `true`) o se **fusionó** con ella (`false`).
 *
 * Las canciones existentes que el usuario decide **sobrescribir** se cuentan en [songsOverwritten]
 * y **no** en [songsImported]; las que decide **omitir** van a [songsSkipped]. Las copias creadas
 * con «conservar ambas» sí cuentan como importadas.
 */
data class BackupImportResult(
    val songsImported: Int,
    val celebrationsImported: Int,
    val categoriesImported: Int,
    val attachmentsImported: Int,
    val rhythmsImported: Int = 0,
    /** Tablaturas importadas (ADR-017, *Feature T*). */
    val tablaturesImported: Int = 0,
    val replaced: Boolean,
    val songsOverwritten: Int = 0,
    val songsSkipped: Int = 0,
    /** Nombre de la copia de seguridad automática creada antes de importar (`null` si no hubo). */
    val safetyBackupFileName: String? = null
)

/**
 * Firma de contenido de un patrón rítmico: nombre, compás y golpes.
 *
 * Se calcula sobre el modelo (no sobre el JSON), de modo que un ritmo llega al mismo resultado
tanto si viene de un archivo como si viene dentro de una copia de seguridad, y un ritmo pegado
como texto se reconoce igual. Sirve para no duplicar al reimportar.
 */
fun RhythmPattern.rhythmSignature(): String = buildString {
    append(name.trim().lowercase())
    append('|')
    append(timeSignature)
    append('|')
    events.forEach { event ->
        append(event.figure.name).append(',')
        append(event.effectiveDirection.name).append(',')
        append(event.effectiveArticulation.name).append(',')
        append(event.effectiveRange.name).append(',')
        append(event.isTied).append(';')
    }
}

/**
 * Resultado de exportar una copia de seguridad: ruta del archivo resultante y número de adjuntos
 * incluidos.
 */
data class BackupExportResult(
    val destinationUri: String,
    val songsExported: Int,
    val attachmentsExported: Int,
    val bytesWritten: Long
)
