package com.grafitto.laudate.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grafitto.laudate.data.local.entity.LiturgicalCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM liturgical_categories ORDER BY display_order ASC, name ASC")
    fun getAllCategoriesFlow(): Flow<List<LiturgicalCategoryEntity>>

    @Query("SELECT * FROM liturgical_categories WHERE id = :id")
    suspend fun getCategoryById(id: String): LiturgicalCategoryEntity?

    @Query("SELECT COUNT(*) FROM liturgical_categories")
    suspend fun getCategoryCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: LiturgicalCategoryEntity)

    /**
     * Actualiza una categoría existente **sin cambiar su identidad** (mismo `id`).
     *
     * Es un `UPDATE` explícito, no un `INSERT OR REPLACE`: renombrar o recategorizar así no
     * puede provocar un borrado+inserción (que con el índice único de `name` fallaría) ni
     * alterar el `created_at`. El llamante propaga el nombre nuevo a canciones y repertorios.
     */
    @Query(
        """
        UPDATE liturgical_categories SET
            name = :name,
            description = :description,
            display_order = :displayOrder,
            is_system_standard = :isSystemStandard,
            kind = :kind
        WHERE id = :id
        """
    )
    suspend fun updateCategoryFull(
        id: String,
        name: String,
        description: String?,
        displayOrder: Int,
        isSystemStandard: Boolean,
        kind: String
    )

    /**
     * Inserta un lote de categorías ignorando las que ya existen (mismo `id` o mismo `name`,
     * que es índice único). Devuelve el `rowid` de cada fila realmente insertada (`-1` si se ignoró),
     * de modo que el llamante pueda informar cuántas se crearon de verdad.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<LiturgicalCategoryEntity>): List<Long>

    /**
     * Borra una categoría por su `id`, **sea o no canónica**.
     *
     * El usuario puede eliminar cualquier categoría: las canciones que la usaran se quedan sin
     * esa etiqueta (no se borra ningún canto). Las categorías canónicas se pueden recrear con
     * «Restaurar categorías estándar».
     */
    @Query("DELETE FROM liturgical_categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: String)

    /** Borra las categorías personalizadas, conservando las canónicas. Paso de la restauración destructiva. */
    @Query("DELETE FROM liturgical_categories WHERE is_system_standard = 0")
    suspend fun deleteCustomCategories()

    @Delete
    suspend fun deleteCategory(category: LiturgicalCategoryEntity)
}
