package com.grafitto.laudate.data.backup

import android.content.Context
import android.net.Uri
import com.grafitto.laudate.domain.backup.BackupArchiveStore
import com.grafitto.laudate.domain.backup.backupAttachmentEntryPath
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Implementación del archivo de copia de seguridad sobre un ZIP.
 *
 * El archivo resultante es **un único fichero portable** (se puede subir a Google Drive, enviar por
 * WhatsApp o descargar y reimportar en otra instalación) que contiene:
 *  - el manifiesto JSON ([com.grafitto.laudate.domain.backup.FullBackup]);
 *  - una carpeta `attachments/` con los ficheros copiados.
 */
class ZipBackupArchiveStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : BackupArchiveStore {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override suspend fun writeArchive(
        destinationUri: String,
        manifestEntryName: String,
        manifestJson: String,
        attachments: List<SongAttachment>
    ) = withContext(ioDispatcher) {
        resolver.openOutputStream(Uri.parse(destinationUri))?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(manifestEntryName))
                zip.write(manifestJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                attachments.filter { !it.isLink }.forEach { attachment ->
                    val file = File(attachment.uri)
                    if (!file.exists()) return@forEach
                    zip.putNextEntry(ZipEntry(backupAttachmentEntryPath(attachment.id)))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: throw IllegalStateException("No se pudo escribir el archivo de copia de seguridad")
    }

    override suspend fun readEntry(archiveUri: String, entryName: String): String? =
        withContext(ioDispatcher) {
            resolver.openInputStream(Uri.parse(archiveUri))?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == entryName) {
                            return@withContext zip.readBytes().toString(Charsets.UTF_8)
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            null
        }

    override suspend fun readEntryEndingWith(archiveUri: String, suffix: String): String? =
        withContext(ioDispatcher) {
            resolver.openInputStream(Uri.parse(archiveUri))?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(suffix, ignoreCase = true)) {
                            return@withContext zip.readBytes().toString(Charsets.UTF_8)
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
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
    ): String = withContext(ioDispatcher) {
        val dir = File(File(appContext.filesDir, "attachments"), songId).apply { mkdirs() }
        val extension = displayName.substringAfterLast('.', "")
        val suffix = if (extension.isNotBlank() && extension.length <= 5) ".$extension" else ""
        val target = File(dir, "${UUID.randomUUID()}$suffix")

        var found = false
        resolver.openInputStream(Uri.parse(archiveUri))?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == entryPath) {
                        target.outputStream().use { zip.copyTo(it) }
                        found = true
                        break
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("No se pudo leer la copia de seguridad")
        if (!found) throw IllegalArgumentException("El adjunto no se encontró en la copia de seguridad")
        target.absolutePath
    }

    override suspend fun copyLocalFileTo(attachment: SongAttachment, destinationUri: String) {
        withContext(ioDispatcher) {
            val source = File(attachment.uri)
            require(source.exists()) { "El archivo adjunto ya no está disponible en el dispositivo" }
            resolver.openOutputStream(Uri.parse(destinationUri))?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("No se pudo abrir el destino elegido")
        }
    }
}
