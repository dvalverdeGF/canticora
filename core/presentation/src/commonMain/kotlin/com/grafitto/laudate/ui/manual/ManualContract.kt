package com.grafitto.laudate.ui.manual

import com.grafitto.laudate.domain.manual.ManualSearchHit
import com.grafitto.laudate.domain.model.manual.ManualChapter

/**
 * Estado del **Manual de uso**. Un único estado inmutable emitido por [ManualViewModel].
 *
 * @property query Texto del buscador interno.
 * @property chapters Todos los capítulos del manual (para el índice y el contenido).
 * @property results Coincidencias del buscador cuando [query] no está vacío.
 * @property expandedSectionIds Secciones actualmente desplegadas. Por defecto van **todas
 *   plegadas** para que el índice sea compacto; al buscar se despliega automáticamente la
 *   primera coincidencia.
 * @property expandedChapterIds Capítulos desplegados en el índice.
 */
data class ManualUiState(
    val query: String = "",
    val chapters: List<ManualChapter> = emptyList(),
    val results: List<ManualSearchHit> = emptyList(),
    val expandedSectionIds: Set<String> = emptySet(),
    val expandedChapterIds: Set<String> = emptySet()
) {
    /** `true` mientras el buscador tiene texto activo (se muestra la lista de resultados). */
    val isSearching: Boolean get() = query.isNotBlank()

    /** `true` cuando la búsqueda no ha encontrado ninguna sección. */
    val hasNoResults: Boolean get() = isSearching && results.isEmpty()
}

sealed interface ManualUiIntent {
    /** Actualiza el texto del buscador y recalcula las coincidencias. */
    data class UpdateQuery(val query: String) : ManualUiIntent

    /** Despliega o pliega una sección del manual. */
    data class ToggleSection(val sectionId: String) : ManualUiIntent

    /** Despliega o pliega un capítulo completo del índice. */
    data class ToggleChapter(val chapterId: String) : ManualUiIntent

    /** Salta a una coincidencia de búsqueda: limpia el buscador y abre su sección. */
    data class OpenSearchResult(val hit: ManualSearchHit) : ManualUiIntent
}

sealed interface ManualSideEffect {
    /** Pide desplazar la lista hasta la sección [sectionId] (p. ej. al abrir un resultado). */
    data class ScrollToSection(val sectionId: String) : ManualSideEffect
}
