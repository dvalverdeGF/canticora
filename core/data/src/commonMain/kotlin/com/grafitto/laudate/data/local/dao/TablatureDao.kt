package com.grafitto.laudate.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grafitto.laudate.data.local.entity.NumericTablatureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TablatureDao {

    /** Todas las tablaturas de un canto (puede haber varias: guitarra, bajo…). */
    @Query("SELECT * FROM numeric_tablatures WHERE song_id = :songId ORDER BY created_at ASC")
    fun observeTablaturesForSong(songId: String): Flow<List<NumericTablatureEntity>>

    /** Lectura puntual de las tablaturas de un canto. */
    @Query("SELECT * FROM numeric_tablatures WHERE song_id = :songId ORDER BY created_at ASC")
    suspend fun getTablaturesForSong(songId: String): List<NumericTablatureEntity>

    @Query("SELECT * FROM numeric_tablatures WHERE id = :id")
    suspend fun getTablatureById(id: String): NumericTablatureEntity?

    @Query("SELECT * FROM numeric_tablatures")
    suspend fun getAllTablatures(): List<NumericTablatureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTablature(entity: NumericTablatureEntity)

    @Query("DELETE FROM numeric_tablatures WHERE id = :id")
    suspend fun deleteTablatureById(id: String)

    @Query("DELETE FROM numeric_tablatures WHERE song_id = :songId")
    suspend fun deleteTablaturesForSong(songId: String)

    /** Cantos que tienen al menos una tablatura (para el icono del catálogo, sin N+1). */
    @Query("SELECT DISTINCT song_id FROM numeric_tablatures")
    fun observeSongIdsWithTablatures(): Flow<List<String>>

    @Query("DELETE FROM numeric_tablatures")
    suspend fun deleteAllTablatures()
}
