package com.grafitto.laudate.data.mapper

import com.grafitto.laudate.data.local.entity.SongAttachmentEntity
import com.grafitto.laudate.domain.model.attachment.AttachmentKind
import com.grafitto.laudate.domain.model.attachment.SongAttachment

/** Mapeo explícito entre la entidad Room y el modelo de Dominio de un adjunto. */
object AttachmentMapper {

    fun toDomain(entity: SongAttachmentEntity): SongAttachment = SongAttachment(
        id = entity.id,
        songId = entity.songId,
        kind = AttachmentKind.fromName(entity.kind) ?: AttachmentKind.DOCUMENT,
        displayName = entity.displayName,
        uri = entity.uri,
        mimeType = entity.mimeType,
        sizeBytes = entity.sizeBytes,
        createdAt = entity.createdAt
    )

    fun toEntity(domain: SongAttachment): SongAttachmentEntity = SongAttachmentEntity(
        id = domain.id,
        songId = domain.songId,
        kind = domain.kind.name,
        displayName = domain.displayName,
        uri = domain.uri,
        mimeType = domain.mimeType,
        sizeBytes = domain.sizeBytes,
        createdAt = domain.createdAt
    )
}
