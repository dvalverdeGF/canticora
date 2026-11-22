package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.attachment.AttachmentStore
import com.grafitto.laudate.domain.model.attachment.AttachmentKind
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.platform.nowMillis
import com.grafitto.laudate.domain.repository.AttachmentRepository

/**
 * Añade un adjunto a un canto.
 *
 * Para ficheros (audio, vídeo, documentos, imágenes) copia el contenido al almacén privado de la
 * app y después guarda sus metadatos; para enlaces solo persiste la URL. La copia local es lo que
 * permite que el adjunto siga disponible aunque el usuario borre el original y que viaje en la
 * copia de seguridad.
 */
class AddAttachmentToSongUseCase(
    private val attachmentStore: AttachmentStore,
    private val attachmentRepository: AttachmentRepository
) {

    /** Importa un fichero elegido con el selector del sistema. */
    suspend operator fun invoke(
        songId: String,
        sourceUri: String,
        displayName: String,
        mimeType: String?
    ): SongAttachment {
        val attachment = attachmentStore.importFile(
            songId = songId,
            sourceUri = sourceUri,
            displayName = displayName,
            mimeType = mimeType
        )
        attachmentRepository.addAttachment(attachment)
        return attachment
    }

    /** Añade un enlace externo (YouTube, Drive, web del autor…). */
    suspend fun addLink(songId: String, url: String, title: String? = null): SongAttachment {
        val cleanUrl = url.trim()
        require(cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
            "El enlace debe empezar por http:// o https://"
        }
        val attachment = SongAttachment(
            id = newId(),
            songId = songId,
            kind = AttachmentKind.LINK,
            displayName = title?.trim()?.takeIf { it.isNotEmpty() } ?: cleanUrl,
            uri = cleanUrl,
            mimeType = "text/uri-list",
            sizeBytes = null,
            createdAt = nowMillis()
        )
        attachmentRepository.addAttachment(attachment)
        return attachment
    }
}
