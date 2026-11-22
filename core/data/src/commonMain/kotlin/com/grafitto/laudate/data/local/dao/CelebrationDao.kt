package com.grafitto.laudate.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.grafitto.laudate.data.local.entity.CelebrationEntity
import com.grafitto.laudate.data.local.entity.CelebrationItemEntity
import com.grafitto.laudate.data.local.relation.CelebrationWithItemsRelation
import kotlinx.coroutines.flow.Flow

@Dao
interface CelebrationDao {

    @Transaction
    @Query("SELECT * FROM celebrations ORDER BY date_timestamp DESC")
    fun getAllCelebrationsWithItemsFlow(): Flow<List<CelebrationWithItemsRelation>>

    @Transaction
    @Query("SELECT * FROM celebrations WHERE id = :id")
    fun getCelebrationByIdFlow(id: String): Flow<CelebrationWithItemsRelation?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCelebration(celebration: CelebrationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCelebrationItems(items: List<CelebrationItemEntity>)

    @Query("DELETE FROM celebration_items WHERE celebration_id = :celebrationId")
    suspend fun deleteItemsByCelebrationId(celebrationId: String)

    @Transaction
    suspend fun insertFullCelebration(
        celebration: CelebrationEntity,
        items: List<CelebrationItemEntity>
    ) {
        insertCelebration(celebration)
        deleteItemsByCelebrationId(celebration.id)
        insertCelebrationItems(items)
    }

    @Delete
    suspend fun deleteCelebration(celebration: CelebrationEntity)

    /** Borra todos los repertorios (sus cantos caen por cascada). Paso previo obligatorio a vaciar canciones. */
    @Query("DELETE FROM celebrations")
    suspend fun deleteAllCelebrations()

    /**
     * Reinserta una celebración **con el mismo id** con el que se eliminó, junto con sus cantos.
     *
     * Se usa para **deshacer** un borrado: a diferencia de [insertFullCelebration], no regenera
     * identificadores ni reordena; respeta los `id` y `displayOrder` originales.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCelebrationItem(item: CelebrationItemEntity)

    @Transaction
    suspend fun restoreFullCelebration(
        celebration: CelebrationEntity,
        items: List<CelebrationItemEntity>
    ) {
        insertCelebration(celebration)
        items.forEach { insertCelebrationItem(it) }
    }

    /**
     * Renombra el momento litúrgico asignado a los cantos de cualquier repertorio.
     *
     * `celebration_items.liturgical_moment` guarda el nombre de la categoría como texto, no como
     * referencia. Al renombrar una categoría hay que reescribirlo o el repertorio seguiría usando
     * el nombre antiguo. Compara sin distinguir mayúsculas y recortando espacios. Devuelve el
     * número de filas modificadas.
     */
    @Query(
        """
        UPDATE celebration_items
        SET liturgical_moment = :newMoment
        WHERE TRIM(LOWER(liturgical_moment)) = TRIM(LOWER(:oldMoment))
        """
    )
    suspend fun updateItemMomentByMoment(oldMoment: String, newMoment: String): Int
}
