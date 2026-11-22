package com.grafitto.laudate.ui.celebration

import com.grafitto.laudate.domain.export.CelebrationPdfSong
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.celebration.CelebrationItem
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.platform.nowMillis

data class CelebrationListUiState(
    val isLoading: Boolean = true,
    val celebrations: List<Celebration> = emptyList(),
    val selectedTypeFilter: String? = null,
    /** Preferencia recordada del modo de vista para exportar a PDF. */
    val pdfDisplayMode: com.grafitto.laudate.domain.model.song.SongDisplayMode =
        com.grafitto.laudate.domain.model.song.SongDisplayMode.CHORDS_ABOVE_TEXT,
    /**
     * Repertorio recién eliminado, pendiente de confirmación con «Deshacer».
     *
     * Guarda la copia completa (con sus cantos) para poder restaurarlo tal cual si el usuario
     * pulsa «Deshacer» en el snackbar.
     */
    val pendingDelete: Celebration? = null,
    val errorMessage: String? = null
)

sealed interface CelebrationListUiIntent {
    data object LoadCelebrations : CelebrationListUiIntent
    data class FilterByType(val type: String?) : CelebrationListUiIntent
    data class DeleteCelebration(val id: String) : CelebrationListUiIntent

    /** Restaura el repertorio eliminado pendiente (deshacer). */
    data object UndoDeleteCelebration : CelebrationListUiIntent
    /** Descarta la confirmación pendiente sin deshacer. */
    data object DismissPendingDelete : CelebrationListUiIntent

    /** Comparte el repertorio completo como archivo `.canticora` (ChordPro + estructura). */
    data class ShareCelebration(val id: String) : CelebrationListUiIntent
    /** Imprime/exporta a PDF el repertorio completo, con un canto por página. */
    data class PrintCelebration(
        val id: String,
        val mode: com.grafitto.laudate.domain.model.song.SongDisplayMode =
            com.grafitto.laudate.domain.model.song.SongDisplayMode.CHORDS_ABOVE_TEXT
    ) : CelebrationListUiIntent
    /** Importa un repertorio recibido (texto ya leído del archivo). */
    data class ImportCelebrationFile(val content: String) : CelebrationListUiIntent
}

data class CelebrationEditorUiState(
    val celebrationId: String? = null,
    val name: String = "",
    val celebrationType: String = "Misa",
    val description: String = "",
    val location: String = "",
    val dateTimestamp: Long = nowMillis(),
    val items: List<CelebrationItem> = emptyList(),
    val allSongs: List<Song> = emptyList(),
    /** Ids de cantos con al menos una tablatura (para el icono de la púa en el listado). */
    val songIdsWithTablatures: Set<String> = emptySet(),
    /** Ids de cantos con al menos un adjunto (para el icono del clip). */
    val songIdsWithAttachments: Set<String> = emptySet(),
    val availableCategories: List<LiturgicalCategory> = emptyList(),
    val isSaving: Boolean = false,
    /** Preferencia recordada del modo de vista para exportar a PDF. */
    val pdfDisplayMode: com.grafitto.laudate.domain.model.song.SongDisplayMode =
        com.grafitto.laudate.domain.model.song.SongDisplayMode.CHORDS_ABOVE_TEXT,
    val errorMessage: String? = null
)

sealed interface CelebrationEditorUiIntent {
    data class LoadForEdit(val celebrationId: String?) : CelebrationEditorUiIntent
    data class UpdateName(val name: String) : CelebrationEditorUiIntent
    data class UpdateCelebrationType(val type: String) : CelebrationEditorUiIntent
    data class UpdateDescription(val description: String) : CelebrationEditorUiIntent
    data class UpdateLocation(val location: String) : CelebrationEditorUiIntent
    data class AddSongItem(val songId: String, val liturgicalMoment: String, val overrideKey: String?) : CelebrationEditorUiIntent
    data class RemoveItem(val itemId: String) : CelebrationEditorUiIntent
    data class MoveItem(val fromIndex: Int, val toIndex: Int) : CelebrationEditorUiIntent
    data class UpdateItemOverrideKey(val itemId: String, val key: String?) : CelebrationEditorUiIntent

    /** Cambia el momento/sección de un canto ya añadido al repertorio. */
    data class UpdateItemMoment(val itemId: String, val moment: String) : CelebrationEditorUiIntent
    data object SaveCelebration : CelebrationEditorUiIntent

    /** Prepara el repertorio para compartirlo como archivo `.canticora`. */
    data object ShareCelebration : CelebrationEditorUiIntent
    /** Prepara la impresión/exportación a PDF del repertorio completo. */
    data class PrintCelebration(
        val mode: com.grafitto.laudate.domain.model.song.SongDisplayMode =
            com.grafitto.laudate.domain.model.song.SongDisplayMode.CHORDS_ABOVE_TEXT
    ) : CelebrationEditorUiIntent
    /** Elimina el repertorio que se está editando y vuelve al listado. */
    data object DeleteCelebration : CelebrationEditorUiIntent
    /** Descarta la petición de compartir/ imprimir una vez atendida. */
    data object ClearPendingAction : CelebrationEditorUiIntent
}
sealed interface CelebrationEditorSideEffect {
    data object NavigateBackOnSaved : CelebrationEditorSideEffect
    /** El repertorio se ha eliminado: hay que volver al listado. */
    data object NavigateBackOnDeleted : CelebrationEditorSideEffect
    data class ShowMessage(val message: String) : CelebrationEditorSideEffect

    /** Comparte el archivo del repertorio por la hoja nativa del sistema. */
    data class ShareCelebrationFile(
        val name: String,
        val fileName: String,
        val content: String
    ) : CelebrationEditorSideEffect

    /** Abre el selector de destino (SAF) para guardar el PDF del repertorio. */
    data class ExportCelebrationPdf(
        val celebration: Celebration,
        val songs: List<CelebrationPdfSong>,
        val fileName: String,
        val mode: com.grafitto.laudate.domain.model.song.SongDisplayMode =
            com.grafitto.laudate.domain.model.song.SongDisplayMode.CHORDS_ABOVE_TEXT
    ) : CelebrationEditorSideEffect
}
