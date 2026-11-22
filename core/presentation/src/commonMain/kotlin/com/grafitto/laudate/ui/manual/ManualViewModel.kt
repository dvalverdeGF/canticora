package com.grafitto.laudate.ui.manual

import androidx.lifecycle.ViewModel
import com.grafitto.laudate.domain.manual.ManualSearch
import com.grafitto.laudate.domain.model.manual.UserManualContent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel del **Manual de uso**.
 *
 * Es deliberadamente ligero: el manual es contenido estático embarcado
 * ([UserManualContent]) y el único trabajo es filtrar secciones y gestionar qué está
 * desplegado. No hay repositorios ni acceso a datos que orquestar, por lo que no necesita
 * inyección de dependencias (se instancia con `viewModel()`).
 */
class ManualViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(
        ManualUiState(
            chapters = UserManualContent.chapters,
            // Arranque compacto: el primer capítulo abierto para dar pistas de uso.
            expandedChapterIds = setOf(UserManualContent.chapters.firstOrNull()?.id.orEmpty())
        )
    )
    val uiState: StateFlow<ManualUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<ManualSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    fun processIntent(intent: ManualUiIntent) {
        when (intent) {
            is ManualUiIntent.UpdateQuery -> updateQuery(intent.query)
            is ManualUiIntent.ToggleSection -> toggleSection(intent.sectionId)
            is ManualUiIntent.ToggleChapter -> toggleChapter(intent.chapterId)
            is ManualUiIntent.OpenSearchResult -> openSearchResult(intent.hit)
        }
    }

    private fun updateQuery(query: String) {
        _uiState.update { current ->
            val results = ManualSearch.search(current.chapters, query)
            current.copy(
                query = query,
                results = results,
                // La primera coincidencia queda abierta para ver el contenido de inmediato.
                expandedSectionIds = results.firstOrNull()?.let { setOf(it.sectionId) } ?: emptySet()
            )
        }
    }

    private fun toggleSection(sectionId: String) {
        _uiState.update { current ->
            current.copy(expandedSectionIds = current.expandedSectionIds.toggle(sectionId))
        }
    }

    private fun toggleChapter(chapterId: String) {
        _uiState.update { current ->
            current.copy(expandedChapterIds = current.expandedChapterIds.toggle(chapterId))
        }
    }

    private fun openSearchResult(hit: com.grafitto.laudate.domain.manual.ManualSearchHit) {
        _uiState.update { current ->
            current.copy(
                query = "",
                results = emptyList(),
                expandedChapterIds = current.expandedChapterIds + hit.chapterId,
                expandedSectionIds = current.expandedSectionIds + hit.sectionId
            )
        }
        _sideEffect.trySend(ManualSideEffect.ScrollToSection(hit.sectionId))
    }

    private fun Set<String>.toggle(value: String): Set<String> =
        if (value in this) this - value else this + value
}
