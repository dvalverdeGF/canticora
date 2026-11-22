package com.grafitto.laudate.data.backup

import com.grafitto.laudate.data.attachment.DesktopAttachmentStore
import com.grafitto.laudate.domain.backup.BackupArchiveStore
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Archivo de copia de seguridad (ZIP) en **escritorio (JVM)**.
 *
 * El "destino" y el "archivo" son rutas de fichero normales. Ver ADR-013.
 */
class DesktopBackupArchiveStore(
    private val attachmentStore: DesktopAttachmentStore
) : BackupArchiveStore {

    override suspend fun writeArchive(
        destinationUri: String,
        manifestEntryName: String,
        manifestJson: String,
        attachments: List<SongAttachment>
    ) = withContext(Dispatchers.IO) {
        ZipOutputStream(File(destinationUri).outputStream().buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(manifestEntryName))
            zip.write(manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            attachments.forEach { attachment ->
                if (attachment.isLink) return@forEach
                val file = File(attachment.uri)
                if (!file.exists()) return@forEach
                zip.putNextEntry(ZipEntry(com.grafitto.laudate.domain.backup.backupAttachmentEntryPath(attachment.id)))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    override suspend fun readEntry(archiveUri: String, entryName: String): String? =
        withContext(Dispatchers.IO) {
            ZipInputStream(File(archiveUri).inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == entryName) {
                        return@withContext zip.readBytes().toString(Charsets.UTF_8)
                    }
                    entry = zip.nextEntry
                }
            }
            null
        }

    override suspend fun readEntryEndingWith(archiveUri: String, suffix: String): String? =
        withContext(Dispatchers.IO) {
            ZipInputStream(File(archiveUri).inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.endsWith(suffix, ignoreCase = true)) {
                        return@withContext zip.readBytes().toString(Charsets.UTF_8)
                    }
                    entry = zip.nextEntry
                }
            }
            null
        }

    override suspend fun extractAttachment(
        archiveUri: String,
        entryPath: String,
        songId: String,
        displayName: String,
        mimeType: String?
    ): String = withContext(Dispatchers.IO) {
        ZipInputStream(File(archiveUri).inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == entryPath) {
                    val dir = File(attachmentStoreRoot(songId))
                    dir.mkdirs()
                    val target = File(dir, displayName)
                    target.outputStream().buffered().use { zip.copyTo(it) }
                    return@withContext target.absolutePath
                }
                entry = zip.nextEntry
            }
        }
        throw IllegalArgumentException("No existe la entrada $entryPath en $archiveUri")
    }

    override suspend fun copyLocalFileTo(attachment: SongAttachment, destinationUri: String) =
        withContext(Dispatchers.IO) {
            File(attachment.uri).copyTo(File(destinationUri), overwrite = true)
            Unit
        }

    /** Carpeta de adjuntos de un canto (misma convención que [DesktopAttachmentStore]). */
    private fun attachmentStoreRoot(songId: String): String =
        File(File(System.getProperty("user.home"), ".canticora/attachments"), songId).absolutePath
}
