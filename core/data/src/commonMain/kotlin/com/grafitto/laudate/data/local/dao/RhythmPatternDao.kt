package com.grafitto.laudate.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grafitto.laudate.data.local.entity.RhythmPatternEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RhythmPatternDao {

    /** Patrones personalizados (los integrados viven en la biblioteca y la fila centinela se excluye). */
    @Query("SELECT * FROM rhythm_patterns WHERE is_built_in = 0 ORDER BY time_signature ASC, name ASC")
    fun getAllPatternsFlow(): Flow<List<RhythmPatternEntity>>

    @Query("SELECT * FROM rhythm_patterns WHERE is_built_in = 0 ORDER BY time_signature ASC, name ASC")
    fun getCustomPatternsFlow(): Flow<List<RhythmPatternEntity>>

    @Query("SELECT * FROM rhythm_patterns WHERE id = :id")
    suspend fun getPatternById(id: String): RhythmPatternEntity?

    /**
     * Nombres de los patrones **integrados** que el usuario ha eliminado.
     *
     * Los patrones de la biblioteca (`GuitarPatternLibrary`) no tienen fila propia; para poder
     * borrarlos se guarda una única fila centinela (`is_built_in = 1`) con la lista de nombres
     * ocultos. Esta consulta la lee de forma reactiva.
     */
    @Query("SELECT deleted_built_ins_json FROM rhythm_patterns WHERE is_built_in = 1 LIMIT 1")
    fun observeHiddenBuiltInsFlow(): Flow<String?>

    /** Lectura puntual (suspend) de la lista de patrones integrados ocultos. */
    @Query("SELECT deleted_built_ins_json FROM rhythm_patterns WHERE is_built_in = 1 LIMIT 1")
    suspend fun getHiddenBuiltIns(): String?

    /** Guarda (o reemplaza) la fila centinela con la lista de patrones integrados ocultos. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHiddenBuiltIns(entity: RhythmPatternEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: RhythmPatternEntity)

    @Delete
    suspend fun deletePattern(pattern: RhythmPatternEntity)

    /** Borra un patrón personalizado (nunca uno integrado). */
    @Query("DELETE FROM rhythm_patterns WHERE id = :id AND is_built_in = 0")
    suspend fun deleteCustomPatternById(id: String)

    /** Borra todos los patrones personalizados (la fila centinela de integrados se conserva). */
    @Query("DELETE FROM rhythm_patterns WHERE is_built_in = 0")
    suspend fun deleteAllCustomPatterns()
}
