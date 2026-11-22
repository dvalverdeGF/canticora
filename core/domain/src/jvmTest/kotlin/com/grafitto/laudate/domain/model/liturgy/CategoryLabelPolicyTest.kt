package com.grafitto.laudate.domain.model.liturgy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el reparto de etiquetas del editor por naturaleza de la categoría (RF-103).
 *
 * El canto solo tiene dos campos de texto (`defaultLiturgicalMoments` y
 * `defaultLiturgicalTimes`), así que los tiempos litúrgicos deben ir a su propio campo o el
 * filtro por tiempo no los encontraría.
 */
class CategoryLabelPolicyTest {

    private val entrada = category("c1", "Entrada", LiturgicalCategoryKind.MASS_MOMENT)
    private val navidad = category("c2", "Navidad", LiturgicalCategoryKind.LITURGICAL_SEASON)
    private val franciscanas = category("c3", "Franciscanas", LiturgicalCategoryKind.ADDITIONAL)
    private val categories = listOf(entrada, navidad, franciscanas)

    @Test
    fun liturgicalSeasonsGoToTimes() {
        val labels = CategoryLabelPolicy.splitByKind(setOf("Navidad"), categories)

        assertEquals(listOf("Navidad"), labels.times)
        assertTrue(labels.moments.isEmpty())
    }

    @Test
    fun momentsAndAdditionalCategoriesGoToMoments() {
        val labels = CategoryLabelPolicy.splitByKind(setOf("Entrada", "Franciscanas"), categories)

        assertEquals(setOf("Entrada", "Franciscanas"), labels.moments.toSet())
        assertTrue(labels.times.isEmpty())
    }

    @Test
    fun unknownLabelsAreKeptAsMoments() {
        val labels = CategoryLabelPolicy.splitByKind(setOf("Desconocida"), categories)

        assertEquals(listOf("Desconocida"), labels.moments)
    }

    @Test
    fun kindLookupIgnoresAccentsAndCase() {
        val labels = CategoryLabelPolicy.splitByKind(setOf("navidad"), categories)

        assertEquals(listOf("navidad"), labels.times)
    }

    @Test
    fun normalizeTrimsAccentsAndLowercases() {
        assertEquals("comunion", CategoryLabelPolicy.normalize("  Comunión  "))
    }

    private fun category(id: String, name: String, kind: LiturgicalCategoryKind) =
        LiturgicalCategory(id = id, name = name, kind = kind)
}
