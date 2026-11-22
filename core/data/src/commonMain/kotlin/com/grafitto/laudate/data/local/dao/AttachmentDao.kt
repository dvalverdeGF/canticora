package com.grafitto.laudate.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.grafitto.laudate.data.local.entity.SongAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM song_attachments WHERE song_id = :songId ORDER BY created_at ASC")
    fun getAttachmentsForSongFlow(songId: String): Flow<List<SongAttachmentEntity>>

    @Query("SELECT * FROM song_attachments WHERE song_id IN (:songIds) ORDER BY song_id, created_at ASC")
    suspend fun getAttachmentsForSongs(songIds: List<String>): List<SongAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: SongAttachmentEntity)

    @Query("DELETE FROM song_attachments WHERE id = :attachmentId")
    suspend fun deleteAttachmentById(attachmentId: String)

    @Query("DELETE FROM song_attachments WHERE song_id = :songId")
    suspend fun deleteAttachmentsForSong(songId: String)

    @Query("SELECT COUNT(*) FROM song_attachments WHERE song_id = :songId")
    suspend fun countForSong(songId: String): Int

    /** Cantos que tienen al menos un adjunto (para el icono del catálogo, sin N+1). */
    @Query("SELECT DISTINCT song_id FROM song_attachments")
    fun observeSongIdsWithAttachments(): Flow<List<String>>

    /** Todos los adjuntos, para poder borrar sus ficheros físicos antes de vaciar la biblioteca. */
    @Query("SELECT * FROM song_attachments")
    suspend fun getAllAttachments(): List<SongAttachmentEntity>

    @Query("DELETE FROM song_attachments")
    suspend fun deleteAllAttachments()
}
