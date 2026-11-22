package com.grafitto.laudate.ui.manual

import com.grafitto.laudate.domain.model.manual.UserManualContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del estado del manual: búsqueda, plegado/desplegado y salto a resultados.
 *
 * [ManualViewModel] no usa `viewModelScope` ni dependencias de Android, por lo que se puede
 * ejercitar en JVM puro.
 */
class ManualViewModelTest {

    private val viewModel = ManualViewModel()

    @Test
    fun `arranca con todos los capitulos y el primero desplegado`() {
        val state = viewModel.uiState.value
        assertEquals(UserManualContent.chapters.size, state.chapters.size)
        assertEquals(setOf(UserManualContent.chapters.first().id), state.expandedChapterIds)
        assertTrue(state.expandedSectionIds.isEmpty())
        assertFalse(state.isSearching)
    }

    @Test
    fun `escribir en el buscador produce resultados y no se considera indice`() {
        viewModel.processIntent(ManualUiIntent.UpdateQuery("transponer"))
        val state = viewModel.uiState.value
        assertTrue(state.isSearching)
        assertTrue(state.results.isNotEmpty())
        assertFalse(state.hasNoResults)
    }

    @Test
    fun `una busqueda sin coincidencias marca hasNoResults`() {
        viewModel.processIntent(ManualUiIntent.UpdateQuery("qwertyuiop"))
        assertTrue(viewModel.uiState.value.hasNoResults)
    }

    @Test
    fun `limpiar la busqueda vuelve al indice`() {
        viewModel.processIntent(ManualUiIntent.UpdateQuery("capo"))
        viewModel.processIntent(ManualUiIntent.UpdateQuery(""))
        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertTrue(state.results.isEmpty())
    }

    @Test
    fun `toggleSection abre y cierra una seccion`() {
        val sectionId = UserManualContent.chapters.first().sections.first().id

        viewModel.processIntent(ManualUiIntent.ToggleSection(sectionId))
        assertTrue(sectionId in viewModel.uiState.value.expandedSectionIds)

        viewModel.processIntent(ManualUiIntent.ToggleSection(sectionId))
        assertFalse(sectionId in viewModel.uiState.value.expandedSectionIds)
    }

    @Test
    fun `toggleChapter abre y cierra un capitulo`() {
        val chapterId = UserManualContent.chapters.last().id

        viewModel.processIntent(ManualUiIntent.ToggleChapter(chapterId))
        assertTrue(chapterId in viewModel.uiState.value.expandedChapterIds)

        viewModel.processIntent(ManualUiIntent.ToggleChapter(chapterId))
        assertFalse(chapterId in viewModel.uiState.value.expandedChapterIds)
    }

    @Test
    fun `abrir un resultado limpia el buscador y despliega su capitulo y seccion`() {
        viewModel.processIntent(ManualUiIntent.UpdateQuery("capo"))
        val hit = viewModel.uiState.value.results.first()

        viewModel.processIntent(ManualUiIntent.OpenSearchResult(hit))

        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertTrue(state.results.isEmpty())
        assertTrue(hit.chapterId in state.expandedChapterIds)
        assertTrue(hit.sectionId in state.expandedSectionIds)
    }
}
