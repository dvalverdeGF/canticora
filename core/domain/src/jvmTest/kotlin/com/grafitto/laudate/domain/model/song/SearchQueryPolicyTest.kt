package com.grafitto.laudate.domain.model.song

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica la política de consulta del catálogo: cuándo se muestra el resultado completo y
 * cuándo una muestra acotada (rendimiento al escribir con ~1.000 cantos).
 */
class SearchQueryPolicyTest {

    @Test
    fun shortQueryIsNotASignificantSearch() {
        assertFalse(SearchQueryPolicy.isSearchQueryValid(""))
        assertFalse(SearchQueryPolicy.isSearchQueryValid("a"))
        assertFalse(SearchQueryPolicy.isSearchQueryValid("ab"))
        // Espacios no cuentan: "  ab  " sigue siendo 2 caracteres.
        assertFalse(SearchQueryPolicy.isSearchQueryValid("  ab  "))
    }

    @Test
    fun threeCharactersIsASignificantSearch() {
        assertTrue(SearchQueryPolicy.isSearchQueryValid("abc"))
        assertTrue(SearchQueryPolicy.isSearchQueryValid("gloria"))
    }

    @Test
    fun activeFiltersCountAsConcreteQuery() {
        assertTrue(SearchQueryPolicy.hasActiveFilters(SongSearchCriteria(onlyFavorites = true)))
        assertTrue(SearchQueryPolicy.hasActiveFilters(SongSearchCriteria(momentNames = setOf("Gloria"))))
        assertTrue(SearchQueryPolicy.hasActiveFilters(SongSearchCriteria(liturgicalTimes = setOf("Adviento"))))
        assertTrue(SearchQueryPolicy.hasActiveFilters(SongSearchCriteria(keys = setOf("Do"))))

        // El texto por sí solo NO es un filtro activo.
        assertFalse(SearchQueryPolicy.hasActiveFilters(SongSearchCriteria(query = "gloria")))
    }

    @Test
    fun emptyCriteriaCannotShowAllResults() {
        assertFalse(SearchQueryPolicy.canShowAllResults(SongSearchCriteria()))
        assertFalse(SearchQueryPolicy.canShowAllResults(SongSearchCriteria(query = "ab")))
    }

    @Test
    fun significantQueryOrFilterCanShowAllResults() {
        assertTrue(SearchQueryPolicy.canShowAllResults(SongSearchCriteria(query = "glo")))
        assertTrue(SearchQueryPolicy.canShowAllResults(SongSearchCriteria(onlyFavorites = true)))
    }

    @Test
    fun emptyCriteriaDoesNotLimitAnAlreadySmallList() {
        val criteria = SongSearchCriteria()
        val smallList = (1..50).toList()

        val limited = SearchQueryPolicy.limited(smallList, criteria)

        // Se devuelve la lista tal cual (no se recorta una lista que ya es pequeña).
        assertEquals(50, limited.size)
    }

    @Test
    fun emptyCriteriaLimitsLargeListToTheMaximum() {
        val criteria = SongSearchCriteria()
        val largeList = (1..1000).toList()

        val limited = SearchQueryPolicy.limited(largeList, criteria)

        assertEquals(SearchQueryPolicy.MAX_RESULTS, limited.size)
        assertEquals(200, limited.size)
        // Se conservan los primeros 200 (el recorte es estable).
        assertEquals(listOf(1, 2, 3), limited.take(3))
    }

    @Test
    fun significantQueryReturnsTheWholeList() {
        val criteria = SongSearchCriteria(query = "gloria")
        val largeList = (1..1000).toList()

        assertEquals(1000, SearchQueryPolicy.limited(largeList, criteria).size)
    }

    @Test
    fun hasUsableInputDistinguishesEmptyFromInProgress() {
        assertFalse(SearchQueryPolicy.hasUsableInput(SongSearchCriteria()))
        assertTrue(SearchQueryPolicy.hasUsableInput(SongSearchCriteria(query = "a")))
        assertTrue(SearchQueryPolicy.hasUsableInput(SongSearchCriteria(onlyFavorites = true)))
    }
}
