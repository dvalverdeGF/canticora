package com.grafitto.laudate.data.repository

import com.grafitto.laudate.data.local.dao.CategoryDao
import com.grafitto.laudate.data.local.entity.LiturgicalCategoryEntity
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.repository.CategoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.grafitto.laudate.data.local.newId

class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CategoryRepository {

    override fun getAllCategories(): Flow<List<LiturgicalCategory>> {
        return categoryDao.getAllCategoriesFlow()
            .map { list -> list.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getAllCategoriesSnapshot(): List<LiturgicalCategory> = withContext(ioDispatcher) {
        categoryDao.getAllCategoriesFlow().firstOrNull().orEmpty().map { it.toDomain() }
    }

    override suspend fun getCategoryById(id: String): LiturgicalCategory? = withContext(ioDispatcher) {
        categoryDao.getCategoryById(id)?.toDomain()
    }

    override suspend fun saveCategory(category: LiturgicalCategory) = withContext(ioDispatcher) {
        categoryDao.insertCategory(category.toEntity())
    }

    override suspend fun updateCategory(category: LiturgicalCategory) = withContext(ioDispatcher) {
        categoryDao.updateCategoryFull(
            id = category.id,
            name = category.name,
            description = category.description,
            displayOrder = category.displayOrder,
            isSystemStandard = category.isSystemStandard,
            kind = category.kind.name
        )
    }

    override suspend fun saveCategories(categories: List<LiturgicalCategory>): Int = withContext(ioDispatcher) {
        if (categories.isEmpty()) return@withContext 0

        // `insertCategories` devuelve -1 en las filas ignoradas (nombre ya existente).
        val rowIds = categoryDao.insertCategories(categories.map { it.toEntity() })
        rowIds.count { it != -1L }
    }

    override suspend fun deleteCategory(categoryId: String) = withContext(ioDispatcher) {
        categoryDao.deleteCategoryById(categoryId)
    }

    override suspend fun seedStandardCategoriesIfEmpty() = withContext(ioDispatcher) {
        val count = categoryDao.getCategoryCount()
        if (count == 0) {
            val massMoments = listOf(
                "Entrada",
                "Piedad / Kyrie",
                "Gloria",
                "Salmo Responsorial",
                "Aleluya / Aclamación",
                "Ofertorio",
                "Santo",
                "Padre Nuestro",
                "Paz / Cordero de Dios",
                "Comunión",
                "Meditación / Acción de Gracias",
                "Salida / Canto Mariano"
            )
            val seasons = listOf(
                "Adviento",
                "Navidad",
                "Cuaresma",
                "Triduo Pascual",
                "Pascua",
                "Tiempo Ordinario"
            )

            val massMomentEntities = massMoments.mapIndexed { index, name ->
                LiturgicalCategoryEntity(
                    id = newId(),
                    name = name,
                    description = "Momento litúrgico canónico del rito católico",
                    displayOrder = index,
                    isSystemStandard = true,
                    kind = LiturgicalCategoryKind.MASS_MOMENT.name
                )
            }
            val seasonEntities = seasons.mapIndexed { index, name ->
                LiturgicalCategoryEntity(
                    id = newId(),
                    name = name,
                    description = "Tiempo litúrgico del año cristiano",
                    displayOrder = massMoments.size + index,
                    isSystemStandard = true,
                    kind = LiturgicalCategoryKind.LITURGICAL_SEASON.name
                )
            }
            categoryDao.insertCategories(massMomentEntities + seasonEntities)
        }
    }

    override suspend fun ensureCategories(
        names: Collection<String>,
        kind: LiturgicalCategoryKind
    ): List<String> = withContext(ioDispatcher) {
        val existing = categoryDao.getAllCategoriesFlow().firstOrNull().orEmpty()
        val existingNames = existing.map { it.name.trim().lowercase() }.toSet()
        val maxOrder = existing.maxOfOrNull { it.displayOrder } ?: -1

        val pending = names
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .filterNot { it.lowercase() in existingNames }

        if (pending.isEmpty()) return@withContext emptyList()

        val newEntities = pending.mapIndexed { index, name ->
            LiturgicalCategoryEntity(
                id = newId(),
                name = name,
                description = "Categoría importada automáticamente",
                displayOrder = maxOrder + 1 + index,
                isSystemStandard = false,
                kind = kind.name
            )
        }
        categoryDao.insertCategories(newEntities)
        pending
    }

    private fun LiturgicalCategoryEntity.toDomain() = LiturgicalCategory(
        id = id,
        name = name,
        description = description,
        displayOrder = displayOrder,
        isSystemStandard = isSystemStandard,
        kind = LiturgicalCategoryKind.fromName(kind),
        createdAt = createdAt
    )

    private fun LiturgicalCategory.toEntity() = LiturgicalCategoryEntity(
        id = id,
        name = name,
        description = description,
        displayOrder = displayOrder,
        isSystemStandard = isSystemStandard,
        kind = kind.name,
        createdAt = createdAt
    )
}
