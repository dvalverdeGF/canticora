package com.grafitto.laudate.data.attachment

import android.content.Context
import android.net.Uri
import com.grafitto.laudate.domain.attachment.AttachmentStore
import com.grafitto.laudate.domain.model.attachment.AttachmentKind
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Implementación Android de [AttachmentStore].
 *
 * Copia cada fichero elegido al **almacén privado** de la app (`files/attachments/<songId>/`), de
 * forma que el adjunto sobreviva a que el usuario borre el original, a la limpieza del gestor de
 * archivos y a reinicios, y quede incluido en las copias de seguridad (tanto la de Canticora como
 * la de Android). Los enlaces no se descargan: solo se guarda su URL.
 */
class DataAttachmentStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AttachmentStore {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Carpeta privada de adjuntos de una canción (se crea si no existe). */
    private fun songDir(songId: String): File =
        File(File(appContext.filesDir, "attachments"), songId).apply { mkdirs() }

    override suspend fun importFile(
        songId: String,
        sourceUri: String,
        displayName: String,
        mimeType: String?
    ): SongAttachment = withContext(ioDispatcher) {
        val uri = Uri.parse(sourceUri)
        val kind = AttachmentKind.fromMimeType(mimeType)

        // Nombre seguro conservando la extensión para que las apps externas puedan abrirlo.
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val baseName = displayName.substringBeforeLast('.', displayName)
            .replace(Regex("[^A-Za-z0-9áéíóúÁÉÍÓÚñÑ _-]"), "_")
            .trim()
            .take(60)
            .ifBlank { "adjunto" }
        val safeName = if (extension.isNotBlank() && extension.length <= 5) "$baseName.$extension" else baseName

        val id = UUID.randomUUID().toString()
        // El id va delante para garantizar unicidad aunque dos adjuntos compartan nombre.
        val target = File(songDir(songId), "${id}__$safeName")

        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalArgumentException("No se pudo abrir el archivo seleccionado")

        SongAttachment(
            id = id,
            songId = songId,
            kind = kind,
            displayName = displayName.ifBlank { safeName },
            uri = target.absolutePath,
            mimeType = mimeType,
            sizeBytes = target.length().takeIf { it > 0 },
            createdAt = System.currentTimeMillis()
        )
    }

    override suspend fun deleteFile(attachment: SongAttachment) = withContext(ioDispatcher) {
        if (attachment.isLink) return@withContext
        runCatching { File(attachment.uri).delete() }
        Unit
    }

    override suspend fun deleteFilesForSong(songId: String) = withContext(ioDispatcher) {
        runCatching { songDir(songId).deleteRecursively() }
        Unit
    }

    override suspend fun exists(attachment: SongAttachment): Boolean = withContext(ioDispatcher) {
        if (attachment.isLink) true else File(attachment.uri).exists()
    }

    override suspend fun exportFile(
        attachment: SongAttachment,
        destinationUri: String
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            if (attachment.isLink) throw IllegalArgumentException("Los enlaces no tienen archivo que descargar")
            val source = File(attachment.uri)
            require(source.exists()) { "El archivo adjunto ya no está disponible en el dispositivo" }
            resolver.openOutputStream(Uri.parse(destinationUri))?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("No se pudo abrir el destino elegido")
            Unit
        }
    }
}
