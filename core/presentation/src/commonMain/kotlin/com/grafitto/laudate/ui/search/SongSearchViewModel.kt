package com.grafitto.laudate.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.song.SongSearchCriteria
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.usecase.SearchSongsUseCase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del buscador de cantos (RF-107). Filtra por texto (título, autor, número, letra),
 * momento litúrgico, tiempo litúrgico, tonalidad y favoritos, con orden configurable.
 *
 * La lógica de filtrado vive en [SearchSongsUseCase] (Dominio puro); este ViewModel solo
 * orquesta el estado MVI y aplica un *debounce* al texto para mantener la UI fluida.
 */
@OptIn(FlowPreview::class)
class SongSearchViewModel(
    private val songRepository: SongRepository,
    private val categoryRepository: CategoryRepository,
    private val searchSongsUseCase: SearchSongsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SongSearchUiState())
    val uiState: StateFlow<SongSearchUiState> = _uiState.asStateFlow()

    /** Criterios "duros" (sin el texto): cambian al togglear filtros. */
    private val _criteria = MutableStateFlow(SongSearchCriteria())

    init {
        observeCatalog()
    }

    fun processIntent(intent: SongSearchUiIntent) {
        when (intent) {
            is SongSearchUiIntent.UpdateQuery -> {
                _uiState.update { it.copy(query = intent.query) }
                _criteria.update { it.copy(query = intent.query) }
            }
            is SongSearchUiIntent.ToggleMoment -> {
                val updated = _criteria.value.momentNames.toggle(intent.name)
                _criteria.update { it.copy(momentNames = updated) }
                _uiState.update { it.copy(momentNames = updated) }
            }
            is SongSearchUiIntent.ToggleLiturgicalTime -> {
                val updated = _criteria.value.liturgicalTimes.toggle(intent.name)
                _criteria.update { it.copy(liturgicalTimes = updated) }
                _uiState.update { it.copy(liturgicalTimes = updated) }
            }
            is SongSearchUiIntent.ToggleAdditionalCategory -> {
                val updated = _criteria.value.additionalCategories.toggle(intent.name)
                _criteria.update { it.copy(additionalCategories = updated) }
                _uiState.update { it.copy(additionalCategories = updated) }
            }
            is SongSearchUiIntent.ToggleKey -> {
                val updated = _criteria.value.keys.toggle(intent.key)
                _criteria.update { it.copy(keys = updated) }
                _uiState.update { it.copy(keys = updated) }
            }
            is SongSearchUiIntent.ToggleFavorites -> {
                _criteria.update { it.copy(onlyFavorites = intent.onlyFavorites) }
                _uiState.update { it.copy(onlyFavorites = intent.onlyFavorites) }
            }
            is SongSearchUiIntent.ToggleUncategorized -> {
                _criteria.update { it.copy(onlyUncategorized = intent.onlyUncategorized) }
                _uiState.update { it.copy(onlyUncategorized = intent.onlyUncategorized) }
            }
            is SongSearchUiIntent.UpdateSort -> {
                _criteria.update { it.copy(sort = intent.sort) }
                _uiState.update { it.copy(sort = intent.sort) }
            }
            is SongSearchUiIntent.ClearFilters -> {
                _criteria.value = SongSearchCriteria(sort = _criteria.value.sort)
                _uiState.update {
                    it.copy(
                        query = "",
                        momentNames = emptySet(),
                        liturgicalTimes = emptySet(),
                        additionalCategories = emptySet(),
                        keys = emptySet(),
                        onlyFavorites = false,
                        onlyUncategorized = false
                    )
                }
            }
        }
    }

    /**
     * Observa catálogo y categorías; recalcula resultados al cambiar los criterios.
     *
     * Se recarga el catálogo **con letra** (`getAllSongsWithLyrics`) porque la búsqueda debe
     * cubrir también el contenido del canto. El *debounce* de 150 ms agrupa pulsaciones y
     * toggles seguidos en un solo cálculo.
     */
    private fun observeCatalog() {
        viewModelScope.launch {
            combine(
                songRepository.getAllSongsWithLyrics(),
                categoryRepository.getAllCategories(),
                _criteria.debounce(150)
            ) { songs, categories, criteria -> Triple(songs, categories, criteria) }
                .collect { (songs, categories, criteria) ->
                    val options = SongSearchFilterOptions(
                        moments = categories
                            .filter { it.kind == LiturgicalCategoryKind.MASS_MOMENT }
                            .sortedBy { it.displayOrder }
                            .map { it.name },
                        liturgicalTimes = categories
                            .filter { it.kind == LiturgicalCategoryKind.LITURGICAL_SEASON }
                            .sortedBy { it.displayOrder }
                            .map { it.name },
                        additionalCategories = categories
                            .filter { it.kind == LiturgicalCategoryKind.ADDITIONAL }
                            .sortedBy { it.displayOrder }
                            .map { it.name },
                        keys = songs.map { it.originalKey }.filter { it.isNotBlank() }.distinct().sorted()
                    )
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            results = searchSongsUseCase(songs, criteria),
                            filterOptions = options
                        )
                    }
                }
        }
    }

    private fun Set<String>.toggle(value: String): Set<String> =
        if (value in this) this - value else this + value
}
