package com.grafitto.laudate.data.attachment

import com.grafitto.laudate.data.local.newId
import com.grafitto.laudate.data.local.nowMillis
import com.grafitto.laudate.domain.attachment.AttachmentStore
import com.grafitto.laudate.domain.model.attachment.AttachmentKind
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Almacén de adjuntos en **escritorio (JVM)**.
 *
 * Los ficheros se copian a `~/.canticora/attachments/<songId>/`. En escritorio, el "origen" es una
 * ruta de fichero normal (`/home/usuario/…` o `C:\…`), no un `content://`. Ver ADR-013.
 */
class DesktopAttachmentStore(dataDirectory: File) : AttachmentStore {

    private val root = File(dataDirectory, "attachments")

    override suspend fun importFile(
        songId: String,
        sourceUri: String,
        displayName: String,
        mimeType: String?
    ): SongAttachment = withContext(Dispatchers.IO) {
        val source = File(sourceUri)
        require(source.exists()) { "No se encuentra el fichero: $sourceUri" }

        val dir = File(root, songId)
        dir.mkdirs()
        val id = newId()
        val target = File(dir, "$id-${displayName.replace(Regex("[^\\w.\\-]"), "_")}")
        source.copyTo(target, overwrite = true)

        SongAttachment(
            id = id,
            songId = songId,
            kind = AttachmentKind.fromMimeType(mimeType),
            displayName = displayName,
            uri = target.absolutePath,
            mimeType = mimeType,
            sizeBytes = target.length(),
            createdAt = nowMillis()
        )
    }

    override suspend fun deleteFile(attachment: SongAttachment) = withContext(Dispatchers.IO) {
        if (attachment.isLink) return@withContext
        File(attachment.uri).delete()
        Unit
    }

    override suspend fun deleteFilesForSong(songId: String) = withContext(Dispatchers.IO) {
        File(root, songId).deleteRecursively()
        Unit
    }

    override suspend fun exists(attachment: SongAttachment): Boolean = withContext(Dispatchers.IO) {
        !attachment.isLink && File(attachment.uri).exists()
    }

    override suspend fun exportFile(
        attachment: SongAttachment,
        destinationUri: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            File(attachment.uri).copyTo(File(destinationUri), overwrite = true)
            Unit
        }
    }
}
