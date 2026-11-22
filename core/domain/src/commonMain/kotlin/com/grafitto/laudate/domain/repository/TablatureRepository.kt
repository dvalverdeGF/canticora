package com.grafitto.laudate.domain.repository

import com.grafitto.laudate.domain.model.tablature.Tablature
import kotlinx.coroutines.flow.Flow

/**
 * Contrato puro de Dominio para las **tablaturas** (ADR-017, *Feature T*).
 *
 * Una tablatura es un **documento propio** asociado a un canto; un canto puede tener **varias**
 * (p. ej. una de guitarra y otra de bajo).
 */
interface TablatureRepository {

    /** Tablaturas de un canto, reactivo. */
    fun observeTablaturesForSong(songId: String): Flow<List<Tablature>>

    /** Lectura puntual de las tablaturas de un canto. */
    suspend fun getTablaturesForSong(songId: String): List<Tablature>

    /** Una tablatura por su id, o `null` si no existe. */
    suspend fun getTablatureById(id: String): Tablature?

    /** Todas las tablaturas almacenadas (para respaldo/intercambio). */
    suspend fun getAllTablatures(): List<Tablature>

    /** Guarda (crea o reemplaza) una tablatura. */
    suspend fun saveTablature(tablature: Tablature)

    /** Elimina una tablatura por su id. */
    suspend fun deleteTablature(id: String)

    /** Elimina todas las tablaturas de un canto. */
    suspend fun deleteTablaturesForSong(songId: String)

    /** Ids de los cantos que tienen al menos una tablatura (icono del catálogo, sin N+1). */
    fun observeSongIdsWithTablatures(): Flow<Set<String>>
}
