package com.grafitto.laudate.domain.repository

import com.grafitto.laudate.domain.model.attachment.SongAttachment
import kotlinx.coroutines.flow.Flow

/**
 * Contrato puro de Dominio para los adjuntos de un canto (audio, vídeo, documentos, enlaces…).
 *
 * Guardar el fichero en disco corresponde a [com.grafitto.laudate.domain.attachment.AttachmentStore];
 * este repositorio solo gestiona los **metadatos** y su persistencia.
 */
interface AttachmentRepository {

    /** Adjuntos de un canto, en orden de incorporación. */
    fun getAttachments(songId: String): Flow<List<SongAttachment>>

    /** Adjuntos de todos los cantos de un repertorio, en una sola consulta (para exportar/bundles). */
    suspend fun getAttachmentsForSongs(songIds: List<String>): List<SongAttachment>

    suspend fun addAttachment(attachment: SongAttachment)

    /** Elimina el registro de un adjunto (el fichero lo borra el llamante). */
    suspend fun deleteAttachment(attachmentId: String)

    /** Elimina los registros de todos los adjuntos de una canción. */
    suspend fun deleteAttachmentsForSong(songId: String)

    /** Recuento de adjuntos de una canción (para la ficha del catálogo). */
    suspend fun countForSong(songId: String): Int

    /** Ids de los cantos que tienen al menos un adjunto (icono del catálogo, sin N+1). */
    fun observeSongIdsWithAttachments(): Flow<Set<String>>
}
