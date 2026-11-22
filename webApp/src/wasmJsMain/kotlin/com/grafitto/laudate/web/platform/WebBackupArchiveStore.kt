package com.grafitto.laudate.web.platform

import com.grafitto.laudate.domain.backup.BackupArchiveStore
import com.grafitto.laudate.domain.backup.backupAttachmentEntryPath
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.web.WebPlatform
import com.grafitto.laudate.web.io.WebBytes
import com.grafitto.laudate.web.io.WebZip
import com.grafitto.laudate.web.io.WebZipEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Archivo de copia de seguridad (ZIP) en el **navegador**.
 *
 * El «destino» es un URI simbólico que la UI construye:
 *  - `download:<nombre>` — el usuario se lleva el `.canticora` a su equipo;
 *  - `idb://<clave>` — copia **automática**, guardada en el almacén del navegador;
 *  - `mem://<n>` — uso interno (restauraciones y pruebas dentro de la sesión).
 *
 * El ZIP se escribe sin comprimir (`STORED`): el contenido es JSON y adjuntos ya comprimidos, y
 * así el archivo es válido para cualquier descompresor sin arrastrar un deflactor a Wasm.
 */
class WebBackupArchiveStore : BackupArchiveStore {

    override suspend fun writeArchive(
        destinationUri: String,
        manifestEntryName: String,
        manifestJson: String,
        attachments: List<SongAttachment>
    ) {
        withContext(Dispatchers.Default) {
            val entries = ArrayList<WebZipEntry>(attachments.size + 1)
            entries += WebZipEntry(manifestEntryName, manifestJson.encodeToByteArray())
            attachments.forEach { attachment ->
                if (attachment.isLink) return@forEach
                val stored = WebPlatform.readStoredFile(WebPlatform.keyOf(attachment.uri))
                    ?: return@forEach
                val bytes = runCatching { WebBytes.base64Decode(stored.base64) }.getOrNull()
                    ?: return@forEach
                entries += WebZipEntry(backupAttachmentEntryPath(attachment.id), bytes)
            }
            val archive = WebZip.write(entries)
            val fileName = fileNameOf(destinationUri).takeIf { it.isNotBlank() }
                ?: DEFAULT_BACKUP_NAME
            when {
                destinationUri.startsWith(DOWNLOAD_SCHEME) ->
                    WebPlatform.downloadBytes(fileName, MIME_ZIP, archive)
                destinationUri.startsWith(WebPlatform.IDB_PREFIX) ->
                    WebPlatform.writeStoredFile(
                        key = WebPlatform.keyOf(destinationUri),
                        fileName = fileName,
                        mimeType = MIME_ZIP,
                        bytes = archive
                    )
                else -> WebArchiveRegistry.write(destinationUri, archive, fileName)
            }
            Unit
        }
    }

    override suspend fun readEntry(archiveUri: String, entryName: String): String? =
        WebArchiveRegistry.read(archiveUri)?.textOf(entryName)

    override suspend fun readEntryEndingWith(archiveUri: String, suffix: String): String? =
        WebArchiveRegistry.read(archiveUri)?.textEndingWith(suffix)

    override suspend fun extractAttachment(
        archiveUri: String,
        entryPath: String,
        songId: String,
        displayName: String,
        mimeType: String?
    ): String {
        val archive = WebArchiveRegistry.read(archiveUri)
            ?: error("La copia de seguridad ya no está disponible.")
        val bytes = archive.bytesOf(entryPath)
            ?: error("La copia de seguridad no contiene «$entryPath».")
        val key = "$songId/${newId()}"
        WebPlatform.writeStoredFile(
            key = key,
            fileName = displayName,
            mimeType = mimeType ?: "application/octet-stream",
            bytes = bytes
        )
        return WebPlatform.IDB_PREFIX + key
    }

    override suspend fun copyLocalFileTo(attachment: SongAttachment, destinationUri: String) {
        if (attachment.isLink) {
            WebPlatform.openUrl(attachment.uri)
            return
        }
        val stored = WebPlatform.readStoredFile(WebPlatform.keyOf(attachment.uri))
            ?: error("El fichero ya no está en el almacén del navegador.")
        val bytes = runCatching { WebBytes.base64Decode(stored.base64) }.getOrNull()
            ?: error("El fichero guardado está dañado.")
        val mimeType = attachment.mimeType ?: stored.mimeType.ifBlank { "application/octet-stream" }
        if (destinationUri.startsWith(DOWNLOAD_SCHEME)) {
            WebPlatform.downloadBytes(fileNameOf(destinationUri), mimeType, bytes)
        } else {
            WebArchiveRegistry.register(bytes, fileNameOf(destinationUri))
        }
    }

    private companion object {
        const val MIME_ZIP = "application/zip"
        const val DEFAULT_BACKUP_NAME = "canticora.canticora"
    }
}
