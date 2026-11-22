package com.grafitto.laudate.ui.catalog

import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongSearchCriteria

data class SongCatalogUiState(
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val categories: List<LiturgicalCategory> = emptyList(),
    /** Nº de canciones etiquetadas con cada categoría, por `id` (RF-103). */
    val categorySongCounts: Map<String, Int> = emptyMap(),
    val searchCriteria: SongSearchCriteria = SongSearchCriteria(),
    /** Total de cantos del catálogo, independiente de los filtros activos. */
    val totalSongCount: Int = 0,
    /** Total de coincidencias con los criterios actuales (puede superar a `songs.size`). */
    val resultCount: Int = 0,
    /** `true` si `songs` es solo una muestra recortada del resultado completo. */
    val isShowingSample: Boolean = false,
    /** Celebraciones disponibles para el atajo «Añadir a misa/evento» desde una tarjeta. */
    val celebrations: List<Celebration> = emptyList(),
    /** Acción recién ejecutada pendiente de confirmación (Snackbar con «Deshacer»). */
    val pendingAction: PendingCelebrationAction? = null,
    val isImporting: Boolean = false,
    /** Preferencia recordada del modo de vista para exportar a PDF. */
    val pdfDisplayMode: com.grafitto.laudate.domain.model.song.SongDisplayMode =
        com.grafitto.laudate.domain.model.song.SongDisplayMode.CHORDS_ABOVE_TEXT,
    /** Ids de cantos que tienen al menos un adjunto (para el icono del listado). */
    val songIdsWithAttachments: Set<String> = emptySet(),
    /** Ids de cantos que tienen al menos una tablatura (para el icono del listado). */
    val songIdsWithTablatures: Set<String> = emptySet(),
    val errorMessage: String? = null
) {
    /** El filtrado por texto/momento/favoritos se aplica en el dominio sobre este estado. */
    val searchText: String get() = searchCriteria.query

    /**
     * ¿Hay algún filtro activo (favoritos, momento, tiempo o tonalidad)?
     *
     * El texto **no** cuenta: la UI lo usa para animar a concretar la búsqueda cuando la lista
     * aún no está acotada.
     */
    val hasActiveFilters: Boolean
        get() = searchCriteria.onlyFavorites ||
            searchCriteria.onlyUncategorized ||
            searchCriteria.momentNames.isNotEmpty() ||
            searchCriteria.liturgicalTimes.isNotEmpty() ||
            searchCriteria.additionalCategories.isNotEmpty() ||
            searchCriteria.keys.isNotEmpty()
}

/**
 * Resultado de añadir un canto a una celebración, listo para ofrecer «Deshacer».
 */
data class PendingCelebrationAction(
    val celebrationId: String,
    val celebrationName: String,
    val song: Song,
    val alreadyPresent: Boolean
)

sealed interface SongCatalogUiIntent {
    data object LoadCatalog : SongCatalogUiIntent
    data class UpdateSearchCriteria(val criteria: SongSearchCriteria) : SongCatalogUiIntent
    data class ToggleSongFavorite(val songId: String, val currentFavorite: Boolean) : SongCatalogUiIntent
    data class DeleteSong(val songId: String) : SongCatalogUiIntent

    /** Comparte el canto como ChordPro por la hoja nativa del sistema. */
    data class ShareSong(val songId: String) : SongCatalogUiIntent
    /**
     * Comparte el canto como **paquete con sus adjuntos** (`canticora.song`, ADR-017).
     * Es la opción «sí» de la pregunta «¿compartir también los adjuntos?».
     */
    data class ShareSongWithAttachments(val songId: String) : SongCatalogUiIntent
    /** Exporta el canto a PDF con la elección de acordes indicada. */
    data class ExportSongPdf(val songId: String, val mode: com.grafitto.laudate.domain.model.song.SongDisplayMode) : SongCatalogUiIntent

    /** Añade un canto a una misa/evento; el momento se sugiere del propio canto. */
    data class AddSongToCelebration(val celebrationId: String, val songId: String) : SongCatalogUiIntent

    /** Deshace la última adición (quita el canto de la celebración). */
    data object UndoCelebrationAdd : SongCatalogUiIntent

    /** Descarta la confirmación pendiente sin deshacer. */
    data object DismissPendingAction : SongCatalogUiIntent

    /**
     * Importa un lote de canciones (p. ej. cancionero digitalizado) y registra
     * automáticamente las categorías/tiempos que falten en el catálogo.
     */
    data class ImportSongs(val songs: List<Song>) : SongCatalogUiIntent
}

sealed interface SongCatalogSideEffect {
    data class NavigateToSongDetail(val songId: String) : SongCatalogSideEffect
    data object NavigateToCreateSong : SongCatalogSideEffect
    data class ShowMessage(val message: String) : SongCatalogSideEffect

    /** Comparte el ChordPro del canto por la hoja nativa (WhatsApp, Telegram, correo…). */
    data class ShareSong(val title: String, val chordPro: String) : SongCatalogSideEffect
    /**
     * Empaqueta el canto con sus adjuntos (`canticora.song`) y lo comparte.
     *
     * El paquete es un ZIP, así que la UI resuelve primero un destino (caché interna) y ejecuta la
     * exportación; aquí viaja el identificador del canto y el nombre de archivo sugerido.
     */
    data class ShareSongPackage(
        val songId: String,
        val title: String,
        val suggestedFileName: String
    ) : SongCatalogSideEffect
    /** Abre el selector de destino (SAF) para guardar el canto como PDF. */
    data class ExportSongPdf(
        val song: Song,
        val suggestedFileName: String,
        val mode: com.grafitto.laudate.domain.model.song.SongDisplayMode =
            com.grafitto.laudate.domain.model.song.SongDisplayMode.CHORDS_ABOVE_TEXT
    ) : SongCatalogSideEffect
}
