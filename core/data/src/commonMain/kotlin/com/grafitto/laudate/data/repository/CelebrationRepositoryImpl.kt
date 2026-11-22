package com.grafitto.laudate.data.repository

import com.grafitto.laudate.data.local.dao.CelebrationDao
import com.grafitto.laudate.data.local.entity.CelebrationEntity
import com.grafitto.laudate.data.mapper.CelebrationMapper
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.repository.CelebrationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implementación de CelebrationRepository con concurrencia segura en IO.
 */
class CelebrationRepositoryImpl(
    private val celebrationDao: CelebrationDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CelebrationRepository {

    override fun getAllCelebrations(): Flow<List<Celebration>> {
        return celebrationDao.getAllCelebrationsWithItemsFlow()
            .map { list -> list.map { CelebrationMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override fun getCelebrationById(id: String): Flow<Celebration?> {
        return celebrationDao.getCelebrationByIdFlow(id)
            .map { relation -> relation?.let { CelebrationMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override suspend fun saveCelebration(celebration: Celebration) = withContext(ioDispatcher) {
        val (celebrationEntity, items) = CelebrationMapper.toEntity(celebration)
        celebrationDao.insertFullCelebration(celebrationEntity, items)
    }

    override suspend fun deleteCelebration(celebrationId: String) = withContext(ioDispatcher) {
        val entity = CelebrationEntity(
            id = celebrationId,
            name = "",
            dateTimestamp = 0,
            description = null,
            location = null
        )
        celebrationDao.deleteCelebration(entity)
    }

    override suspend fun restoreCelebration(celebration: Celebration) = withContext(ioDispatcher) {
        // Se reinserta con los mismos ids (celebración e items) para que no cambie ninguna referencia.
        val (entity, items) = CelebrationMapper.toEntity(celebration)
        celebrationDao.restoreFullCelebration(entity, items)
    }

    override suspend fun renameMoment(oldName: String, newName: String): Int = withContext(ioDispatcher) {
        if (oldName.trim().isEmpty()) return@withContext 0
        celebrationDao.updateItemMomentByMoment(oldName, newName)
    }
}
