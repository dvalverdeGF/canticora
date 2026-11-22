package com.grafitto.laudate.domain.repository

import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import kotlinx.coroutines.flow.Flow

/**
 * Contrato puro de Dominio para la gestión y consulta de categorías/momentos litúrgicos.
 */
interface CategoryRepository {
    fun getAllCategories(): Flow<List<LiturgicalCategory>>
    suspend fun getAllCategoriesSnapshot(): List<LiturgicalCategory>
    suspend fun getCategoryById(id: String): LiturgicalCategory?
    suspend fun saveCategory(category: LiturgicalCategory)

    /**
     * Actualiza una categoría existente **sin cambiar su identidad** (mismo `id`).
     *
     * A diferencia de [saveCategory], que inserta con `REPLACE`, esta operación es un `UPDATE`
     * explícito: permite renombrar y cambiar el tipo sin duplicar filas por nombre. Es la vía
     * que usa [com.grafitto.laudate.domain.usecase.UpdateCategoryUseCase]; el llamante es
     * responsable de propagar el renombrado a canciones y repertorios.
     */
    suspend fun updateCategory(category: LiturgicalCategory)

    /**
     * Guarda un lote de categorías ignorando duplicados por nombre.
     *
     * @return número de categorías realmente creadas (las ya existentes no cuentan).
     */
    suspend fun saveCategories(categories: List<LiturgicalCategory>): Int

    suspend fun deleteCategory(categoryId: String)
    suspend fun seedStandardCategoriesIfEmpty()

    /**
     * Garantiza que existan las categorías indicadas, creando únicamente las que falten.
     * Se usa al importar un cancionero cuyas canciones referencian tiempos litúrgicos,
     * colecciones o etiquetas que aún no existen en el catálogo local.
     *
     * @return lista de nombres de las categorías que se crearon.
     */
    suspend fun ensureCategories(
        names: Collection<String>,
        kind: LiturgicalCategoryKind = LiturgicalCategoryKind.ADDITIONAL
    ): List<String>
}
