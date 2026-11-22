package com.grafitto.laudate.ui.editor

import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.parser.ChordProValidationIssue
import com.grafitto.laudate.domain.usecase.DuplicateSongMatch

/**
 * Pestañas del editor de canción. La tercera, [TABLATURES], permite crear y editar las tablaturas
 * del canto sin salir del editor (ADR-017). El canto debe existir para poder guardar tablaturas.
 */
enum class SongEditorTab(val label: String) {
    EDITOR("Editor"),
    PREVIEW("Vista Previa"),
    TABLATURES("Tablaturas")
}

data class SongEditorUiState(
    val songId: String? = null,
    val title: String = "",
    val number: String = "",
    val author: String = "",
    val originalKey: String = "C",
    val baseKey: String = "C",
    /**
     * Nomenclatura de acordes de **toda la aplicación** (Do-Re-Mi / C-D-E). Se muestra en el
     * editor para poder cambiarla, pero el valor vive en las preferencias globales.
     */
    val notationSystem: NotationSystem = NotationSystem.LATIN,
    /**
     * Sistema de notación al que ya se ha convertido el texto actual. Evita reconvertir
     * el ChordPro en cada emisión de la preferencia (y machacar la edición en curso).
     */
    val lastAutoNotationSystem: NotationSystem? = null,
    val timeSignature: TimeSignature? = null,
    val selectedPatternId: String? = null,
    val availablePatterns: List<RhythmPattern> = emptyList(),
    val tempoBpm: Int = 0,
    val tempoMarking: String = "",
    val capo: Int = 0,
    val notes: String = "",
    val chordProContent: String = "",
    val availableCategories: List<LiturgicalCategory> = emptyList(),
    val selectedCategories: Set<String> = emptySet(),
    val isPreviewMode: Boolean = false,
    /** Pestaña activa del editor: Editor / Vista previa / Tablaturas. */
    val editorTab: SongEditorTab = SongEditorTab.EDITOR,
    /**
     * Borrador en memoria del canto tal y como está en el editor. Se construye al entrar en la
     * vista previa con el **mismo camino que el guardado**, y nunca se persiste: permite ver el
     * resultado real antes de guardar, incluidos los metadatos que no viven en el texto ChordPro
     * (autor, número, capo, tempo, notas, compás, patrón rítmico y categorías).
     */
    val previewSong: Song? = null,
    val isSaving: Boolean = false,
    /**
     * Firma de los campos editables tal y como se cargaron o guardaron por última vez.
     * Es la referencia para saber si hay cambios pendientes sin tener que marcar el editor
     * a mano en cada intent. `null` mientras no se haya cargado nada.
     */
    val savedSignature: List<Any?>? = null,
    /** El diálogo «cambios sin guardar» está abierto, a la espera de confirmación. */
    val showDiscardDialog: Boolean = false,
    val showImportPrompt: Boolean = false,
    val importText: String = "",
    val importIssues: List<ChordProValidationIssue> = emptyList(),
    val lastImportValid: Boolean? = null,
    val exportedChordPro: String? = null,
    /**
     * Coincidencia de contenido detectada al guardar o importar: la canción ya existe con otro
     * `id`. Mientras no sea `null` se muestra el diálogo que ofrece sobrescribir o crear una nueva.
     */
    val duplicateMatch: DuplicateSongMatch? = null,
    /**
     * Adjuntos del canto (audio, vídeo, documentos, imágenes y enlaces). Solo están disponibles
     * cuando la canción ya existe: en una canción nueva se guardan al crearla por primera vez.
     */
    val attachments: List<SongAttachment> = emptyList(),
    /** Preferencia recordada del modo de vista para exportar a PDF. */
    val pdfDisplayMode: com.grafitto.laudate.domain.model.song.SongDisplayMode =
        com.grafitto.laudate.domain.model.song.SongDisplayMode.CHORDS_ABOVE_TEXT,
    /** El diálogo «modo de vista del PDF» está abierto, a la espera de confirmación. */
    val showPdfDialog: Boolean = false,
    val errorMessage: String? = null
) {
    /** Patrones compatibles con el compás elegido (si lo hay). */
    val patternsForSelectedBar: List<RhythmPattern>
        get() {
            val bar = timeSignature ?: return emptyList()
            return availablePatterns.filter { it.timeSignature == bar }
        }

    /** Patrón actualmente seleccionado, si existe. */
    val selectedPattern: RhythmPattern?
        get() = selectedPatternId?.let { id -> availablePatterns.firstOrNull { it.id == id } }

    /** Campos que el usuario puede modificar en el editor. */
    private fun editableSignature(): List<Any?> = listOf(
        title,
        number,
        author,
        originalKey,
        timeSignature,
        selectedPatternId,
        tempoBpm,
        tempoMarking,
        capo,
        notes,
        chordProContent,
        selectedCategories
    )

    /**
     * Hay cambios en el editor que aún no se han guardado. Se usa para avisar antes de salir y
     * no perder el trabajo (p. ej. veinte minutos montando un canto). Se calcula comparando los
     * campos editables con la última referencia guardada, así que deshacer una edición deja de
     * contar como cambio pendiente.
     */
    val hasUnsavedChanges: Boolean
        get() = savedSignature != null && editableSignature() != savedSignature

    /** Toma el estado actual como referencia: deja de haber cambios pendientes. */
    fun markAsSaved(): SongEditorUiState = copy(savedSignature = editableSignature())
}

sealed interface SongEditorUiIntent {
    data class LoadSongForEditing(val songId: String?) : SongEditorUiIntent
    data class UpdateTitle(val title: String) : SongEditorUiIntent
    data class UpdateNumber(val number: String) : SongEditorUiIntent
    data class UpdateAuthor(val author: String) : SongEditorUiIntent
    data class UpdateOriginalKey(val key: String) : SongEditorUiIntent
    /**
     * Cambia la nomenclatura de acordes de toda la aplicación (no solo de este canto).
     * El cambio se persiste en las preferencias y afecta al visor, al modo celebración, etc.
     */
    data class SetNotationSystem(val system: NotationSystem) : SongEditorUiIntent
    data class SelectTimeSignature(val timeSignature: TimeSignature?) : SongEditorUiIntent
    data class SelectRhythmPattern(val patternId: String?) : SongEditorUiIntent
    data class UpdateTempoBpm(val bpm: Int) : SongEditorUiIntent
    data class UpdateTempoMarking(val marking: String) : SongEditorUiIntent
    data class UpdateCapo(val capo: Int) : SongEditorUiIntent
    data class UpdateNotes(val notes: String) : SongEditorUiIntent
    data class UpdateChordProContent(val content: String) : SongEditorUiIntent
    data class ToggleCategorySelection(val categoryName: String) : SongEditorUiIntent
    data class TogglePreviewMode(val isPreview: Boolean) : SongEditorUiIntent
    /** Cambia la pestaña activa del editor (editor / vista previa / tablaturas). */
    data class SelectEditorTab(val tab: SongEditorTab) : SongEditorUiIntent
    data class InsertChordShortcut(val chord: String) : SongEditorUiIntent
    data class InsertDirectiveShortcut(val directive: String) : SongEditorUiIntent
    data class TransposeSong(val semitonesDelta: Int) : SongEditorUiIntent
    data object ResetTransposition : SongEditorUiIntent
    data object SaveSong : SongEditorUiIntent

    // ── Importación / exportación ChordPro ──────────────────────────────────
    /** Muestra u oculta el diálogo inicial "¿importar desde ChordPro?". */
    data class SetImportPromptVisible(val visible: Boolean) : SongEditorUiIntent
    data class UpdateImportText(val text: String) : SongEditorUiIntent
    /** Valida el texto importado y, si es correcto, lo carga en el editor. */
    data object ValidateAndImport : SongEditorUiIntent
    /** Genera el texto ChordPro de la canción actual para exportarla. */
    data object ExportChordPro : SongEditorUiIntent
    data object DismissExport : SongEditorUiIntent

    // ── Compartir / exportar a PDF (solo desde el editor) ───────────────────
    /** Abre la hoja nativa de compartir de Android con el ChordPro de la canción. */
    data object ShareChordPro : SongEditorUiIntent
    /**
     * Comparte el canto como **paquete con sus adjuntos** (`canticora.song`, ADR-017).
     * Es la opción «sí» de la pregunta «¿compartir también los adjuntos?».
     */
    data object ShareChordProWithAttachments : SongEditorUiIntent
    /** Abre el diálogo que pregunta si el PDF debe llevar acordes o solo la letra. */
    data object ExportPdf : SongEditorUiIntent
    /** Confirma la exportación a PDF con la elección de acordes y la recuerda. */
    data class ConfirmExportPdf(val mode: com.grafitto.laudate.domain.model.song.SongDisplayMode) : SongEditorUiIntent
    /** Cierra el diálogo de exportación a PDF sin exportar. */
    data object DismissPdfDialog : SongEditorUiIntent
    /** Elimina la canción que se está editando y vuelve al listado. */
    data object DeleteSong : SongEditorUiIntent

    // ── Salida del editor ───────────────────────────────────────────────────
    /** El usuario quiere salir: si hay cambios sin guardar se pide confirmación. */
    data object RequestExit : SongEditorUiIntent
    /** Confirma que se descartan los cambios pendientes y se sale del editor. */
    data object ConfirmDiscardChanges : SongEditorUiIntent
    /** Cancela la salida y vuelve al editor. */
    data object DismissDiscardDialog : SongEditorUiIntent

    // ── Duplicados ──────────────────────────────────────────────────────────
    /** Sobrescribe la copia existente conservando su identidad (id y fecha de alta). */
    data object ConfirmOverwrite : SongEditorUiIntent
    /** Guarda la canción como una nueva, sin tocar la existente. */
    data object CreateAsNewSong : SongEditorUiIntent
    /** Cierra el diálogo de duplicados y permite seguir editando. */
    data object DismissDuplicatePrompt : SongEditorUiIntent

    // ── Adjuntos ────────────────────────────────────────────────────────────
    /** Añade un fichero adjunto (audio, vídeo, documento, imagen) elegido con el selector. */
    data class AddAttachmentFromUri(
        val sourceUri: String,
        val displayName: String,
        val mimeType: String?
    ) : SongEditorUiIntent
    /** Añade un enlace externo (YouTube, Drive, web…). */
    data class AddAttachmentLink(val url: String, val title: String? = null) : SongEditorUiIntent
    /** Elimina un adjunto (fichero y registro). */
    data class DeleteAttachment(val attachment: SongAttachment) : SongEditorUiIntent
    /** Abre un adjunto con la app externa correspondiente (el editor delega en la actividad). */
    data class OpenAttachment(val attachment: SongAttachment) : SongEditorUiIntent
    /** Descarga/exporta el fichero local de un adjunto al destino elegido con el selector. */
    data class ExportAttachment(val attachment: SongAttachment, val destinationUri: String) : SongEditorUiIntent
}

sealed interface SongEditorSideEffect {
    data object NavigateBackOnSaved : SongEditorSideEffect
    /** Sale del editor sin guardar (el usuario ha descartado los cambios). */
    data object NavigateBack : SongEditorSideEffect
    /** La canción se ha eliminado: hay que volver al listado. */
    data object NavigateBackOnDeleted : SongEditorSideEffect
    data class ShowMessage(val message: String) : SongEditorSideEffect

    /** Comparte el ChordPro por la hoja nativa (WhatsApp, Telegram, correo…). */
    data class ShareSong(val title: String, val chordPro: String) : SongEditorSideEffect
    /**
     * Empaqueta el canto (ya guardado) con sus adjuntos (`canticora.song`) y lo comparte.
     * La UI resuelve el destino del ZIP y ejecuta [com.grafitto.laudate.domain.usecase.ExportSongPackageUseCase].
     */
    data class ShareSongPackage(
        val songId: String,
        val title: String,
        val suggestedFileName: String
    ) : SongEditorSideEffect
    /** Abre el selector de destino (SAF) para guardar el canto como PDF. */
    data class ExportSongPdf(
        val song: Song,
        val suggestedFileName: String,
        val mode: com.grafitto.laudate.domain.model.song.SongDisplayMode =
            com.grafitto.laudate.domain.model.song.SongDisplayMode.CHORDS_ABOVE_TEXT
    ) : SongEditorSideEffect
    /**
     * Abre un adjunto con la app externa correspondiente (o el navegador, si es un enlace).
     * El editor delega en la actividad, que es quien puede conceder permisos de lectura.
     */
    data class OpenAttachment(val attachment: SongAttachment) : SongEditorSideEffect
}
