package com.grafitto.laudate.data.repository

import com.grafitto.laudate.data.local.dao.AttachmentDao
import com.grafitto.laudate.data.mapper.AttachmentMapper
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.repository.AttachmentRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Implementación de [AttachmentRepository] sobre Room, sin bloquear el hilo principal. */
class AttachmentRepositoryImpl(
    private val attachmentDao: AttachmentDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AttachmentRepository {

    override fun getAttachments(songId: String): Flow<List<SongAttachment>> =
        attachmentDao.getAttachmentsForSongFlow(songId)
            .map { list -> list.map(AttachmentMapper::toDomain) }
            .flowOn(ioDispatcher)

    override suspend fun getAttachmentsForSongs(songIds: List<String>): List<SongAttachment> =
        withContext(ioDispatcher) {
            if (songIds.isEmpty()) return@withContext emptyList()
            attachmentDao.getAttachmentsForSongs(songIds.distinct()).map(AttachmentMapper::toDomain)
        }

    override suspend fun addAttachment(attachment: SongAttachment) = withContext(ioDispatcher) {
        attachmentDao.insertAttachment(AttachmentMapper.toEntity(attachment))
    }

    override suspend fun deleteAttachment(attachmentId: String) = withContext(ioDispatcher) {
        attachmentDao.deleteAttachmentById(attachmentId)
    }

    override suspend fun deleteAttachmentsForSong(songId: String) = withContext(ioDispatcher) {
        attachmentDao.deleteAttachmentsForSong(songId)
    }

    override suspend fun countForSong(songId: String): Int = withContext(ioDispatcher) {
        attachmentDao.countForSong(songId)
    }

    override fun observeSongIdsWithAttachments(): Flow<Set<String>> =
        attachmentDao.observeSongIdsWithAttachments().map { it.toSet() }
}
