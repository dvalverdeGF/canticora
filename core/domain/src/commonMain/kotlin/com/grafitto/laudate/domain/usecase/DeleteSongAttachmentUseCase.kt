package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.attachment.AttachmentStore
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.repository.AttachmentRepository

/**
 * Elimina un adjunto de un canto: primero el fichero copiado en el almacén privado y después su
 * registro. Para los enlaces solo hay registro.
 */
class DeleteSongAttachmentUseCase(
    private val attachmentStore: AttachmentStore,
    private val attachmentRepository: AttachmentRepository
) {
    suspend operator fun invoke(attachment: SongAttachment) {
        attachmentStore.deleteFile(attachment)
        attachmentRepository.deleteAttachment(attachment.id)
    }
}
