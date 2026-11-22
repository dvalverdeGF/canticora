package com.grafitto.laudate.domain.backup

import com.grafitto.laudate.domain.model.attachment.SongAttachment

/**
 * Convierte los adjuntos de un canto en el **índice** que viaja dentro de un ZIP, ya sea una copia
 * de seguridad completa ([FullBackup]) o un paquete de canción
 * ([com.grafitto.laudate.domain.transfer.SongPackage]).
 *
 * Se comparte para que solo haya **una** definición de cómo se empaqueta un adjunto:
 *  - un **enlace** viaja como URL ([BackupAttachment.linkUrl]) y sin ruta de fichero;
 *  - un **fichero** se referencia por su ruta determinista dentro del archivo
 *    ([backupAttachmentEntryPath]).
 */
fun List<SongAttachment>.toBackupAttachments(): List<BackupAttachment> = map { attachment ->
    BackupAttachment(
        id = attachment.id,
        songId = attachment.songId,
        kind = attachment.kind.name,
        displayName = attachment.displayName,
        mimeType = attachment.mimeType,
        sizeBytes = attachment.sizeBytes,
        createdAt = attachment.createdAt,
        linkUrl = if (attachment.isLink) attachment.uri else null,
        entryPath = if (attachment.isLink) null else backupAttachmentEntryPath(attachment.id)
    )
}
