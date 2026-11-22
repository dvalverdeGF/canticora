package com.grafitto.laudate.data.repository

import com.grafitto.laudate.data.local.dao.TablatureDao
import com.grafitto.laudate.data.mapper.TablatureMapper
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.repository.TablatureRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implementación Local-First del repositorio de tablaturas (ADR-017).
 *
 * Toda la E/S va al dispatcher de IO; la serialización y la conversión de modelo son pura CPU.
 */
class TablatureRepositoryImpl(
    private val tablatureDao: TablatureDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TablatureRepository {

    override fun observeTablaturesForSong(songId: String): Flow<List<Tablature>> =
        tablatureDao.observeTablaturesForSong(songId)
            .map { entities -> entities.map { TablatureMapper.toDomain(it) }.sortedBy { it.order } }

    override suspend fun getTablaturesForSong(songId: String): List<Tablature> =
        withContext(ioDispatcher) {
            tablatureDao.getTablaturesForSong(songId)
                .map { TablatureMapper.toDomain(it) }
                .sortedBy { it.order }
        }

    override suspend fun getTablatureById(id: String): Tablature? = withContext(ioDispatcher) {
        tablatureDao.getTablatureById(id)?.let { TablatureMapper.toDomain(it) }
    }

    override suspend fun getAllTablatures(): List<Tablature> = withContext(ioDispatcher) {
        tablatureDao.getAllTablatures().map { TablatureMapper.toDomain(it) }
    }

    override suspend fun saveTablature(tablature: Tablature): Unit = withContext(ioDispatcher) {
        // Conserva la fecha de creación original al actualizar una tablatura existente.
        val createdAt = tablatureDao.getTablatureById(tablature.id)?.createdAt
        tablatureDao.upsertTablature(TablatureMapper.toEntity(tablature, createdAt))
    }

    override suspend fun deleteTablature(id: String): Unit = withContext(ioDispatcher) {
        tablatureDao.deleteTablatureById(id)
    }

    override suspend fun deleteTablaturesForSong(songId: String): Unit = withContext(ioDispatcher) {
        tablatureDao.deleteTablaturesForSong(songId)
    }

    override fun observeSongIdsWithTablatures(): Flow<Set<String>> =
        tablatureDao.observeSongIdsWithTablatures().map { it.toSet() }
}
