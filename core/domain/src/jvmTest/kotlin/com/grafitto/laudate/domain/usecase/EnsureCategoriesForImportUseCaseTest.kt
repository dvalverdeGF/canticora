package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnsureCategoriesForImportUseCaseTest {

    private val useCase = EnsureCategoriesForImportUseCase(
        categoryRepository = FakeCategoryRepository()
    )

    @Test
    fun classifyRecognizesMassMoments() {
        assertEquals(LiturgicalCategoryKind.MASS_MOMENT, useCase.classify("Entrada"))
        assertEquals(LiturgicalCategoryKind.MASS_MOMENT, useCase.classify("Ofertorio"))
        assertEquals(LiturgicalCategoryKind.MASS_MOMENT, useCase.classify("Comunión"))
        assertEquals(LiturgicalCategoryKind.MASS_MOMENT, useCase.classify("gloria"))
    }

    @Test
    fun classifyRecognizesLiturgicalSeasons() {
        assertEquals(LiturgicalCategoryKind.LITURGICAL_SEASON, useCase.classify("Adviento"))
        assertEquals(LiturgicalCategoryKind.LITURGICAL_SEASON, useCase.classify("Cuaresma"))
        assertEquals(LiturgicalCategoryKind.LITURGICAL_SEASON, useCase.classify("Pascua"))
        assertEquals(LiturgicalCategoryKind.LITURGICAL_SEASON, useCase.classify("Tiempo Ordinario"))
    }

    @Test
    fun classifyTreatsUnknownNamesAsAdditional() {
        // "Franciscanas" no es parte de la liturgia ni tiempo litúrgico: es una colección del coro.
        assertEquals(LiturgicalCategoryKind.ADDITIONAL, useCase.classify("Franciscanas"))
        assertEquals(LiturgicalCategoryKind.ADDITIONAL, useCase.classify("Juveniles"))
        assertEquals(LiturgicalCategoryKind.ADDITIONAL, useCase.classify("Marianas"))
    }

    @Test
    fun planSeparatesCategoriesByNature() {
        val plan = useCase.plan(
            referencedCategories = listOf(
                "Entrada",          // ya existe
                "Ofertorio",        // nuevo, momento de misa
                "Adviento",         // nuevo, tiempo litúrgico
                "Franciscanas"      // nueva, adicional
            ),
            existingCategoryNames = listOf("Entrada", "Gloria")
        )

        assertEquals(listOf("Ofertorio"), plan[LiturgicalCategoryKind.MASS_MOMENT])
        assertEquals(listOf("Adviento"), plan[LiturgicalCategoryKind.LITURGICAL_SEASON])
        assertEquals(listOf("Franciscanas"), plan[LiturgicalCategoryKind.ADDITIONAL])
        // "Entrada" no se propone porque ya existe.
        assertTrue(plan.values.flatten().none { it == "Entrada" })
    }

    @Test
    fun planIgnoresCaseAndDuplicates() {
        val plan = useCase.plan(
            referencedCategories = listOf("Adviento", "adviento", "ADVIENTO"),
            existingCategoryNames = emptyList()
        )
        assertEquals(listOf("Adviento"), plan[LiturgicalCategoryKind.LITURGICAL_SEASON])
    }

    @Test
    fun planProducesEmptyMapWhenNothingToCreate() {
        val plan = useCase.plan(
            referencedCategories = listOf("Entrada", "Gloria"),
            existingCategoryNames = listOf("Entrada", "Gloria")
        )
        assertTrue(plan.isEmpty())
    }

    private class FakeCategoryRepository : com.grafitto.laudate.domain.repository.CategoryRepository {
        override fun getAllCategories() = kotlinx.coroutines.flow.flowOf(emptyList<com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory>())
        override suspend fun getAllCategoriesSnapshot() = emptyList<com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory>()
        override suspend fun getCategoryById(id: String) = null
        override suspend fun saveCategory(category: com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory) = Unit
        override suspend fun updateCategory(category: com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory) = Unit
        override suspend fun saveCategories(categories: List<com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory>) = categories.size
        override suspend fun deleteCategory(categoryId: String) = Unit
        override suspend fun seedStandardCategoriesIfEmpty() = Unit
        override suspend fun ensureCategories(
            names: Collection<String>,
            kind: LiturgicalCategoryKind
        ) = names.toList()
    }
}
