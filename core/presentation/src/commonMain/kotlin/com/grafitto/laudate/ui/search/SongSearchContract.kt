package com.grafitto.laudate.ui.search

import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongSortOrder

/**
 * Estado del buscador de cantos. Un único estado inmutable emitido por [SongSearchViewModel].
 *
 * [query] refleja el texto tecleado al instante (para que el campo no parpadee), mientras que
 * [results] se recalcula con un pequeño *debounce* para no filtrar en cada pulsación.
 */
data class SongSearchUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val momentNames: Set<String> = emptySet(),
    val liturgicalTimes: Set<String> = emptySet(),
    val additionalCategories: Set<String> = emptySet(),
    val keys: Set<String> = emptySet(),
    val onlyFavorites: Boolean = false,
    /** Si es `true`, solo se muestran cantos sin ninguna categoría asignada. */
    val onlyUncategorized: Boolean = false,
    val sort: SongSortOrder = SongSortOrder.TITLE,
    val results: List<Song> = emptyList(),
    val filterOptions: SongSearchFilterOptions = SongSearchFilterOptions()
) {
    /** Número de filtros estructurados activos (sin contar el texto libre). */
    val activeFilterCount: Int
        get() = momentNames.size + liturgicalTimes.size + additionalCategories.size + keys.size +
            (if (onlyFavorites) 1 else 0) + (if (onlyUncategorized) 1 else 0)

    val hasActiveFilters: Boolean
        get() = activeFilterCount > 0 || query.isNotBlank()
}

/** Opciones disponibles para los filtros estructurados, derivadas del catálogo actual. */
data class SongSearchFilterOptions(
    val moments: List<String> = emptyList(),
    val liturgicalTimes: List<String> = emptyList(),
    val additionalCategories: List<String> = emptyList(),
    val keys: List<String> = emptyList()
)

sealed interface SongSearchUiIntent {
    data class UpdateQuery(val query: String) : SongSearchUiIntent
    data class ToggleMoment(val name: String) : SongSearchUiIntent
    data class ToggleLiturgicalTime(val name: String) : SongSearchUiIntent
    data class ToggleAdditionalCategory(val name: String) : SongSearchUiIntent
    data class ToggleKey(val key: String) : SongSearchUiIntent
    data class ToggleFavorites(val onlyFavorites: Boolean) : SongSearchUiIntent
    data class ToggleUncategorized(val onlyUncategorized: Boolean) : SongSearchUiIntent
    data class UpdateSort(val sort: SongSortOrder) : SongSearchUiIntent
    data object ClearFilters : SongSearchUiIntent
}
