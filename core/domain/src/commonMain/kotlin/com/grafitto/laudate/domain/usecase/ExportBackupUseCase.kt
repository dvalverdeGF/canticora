package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.backup.BACKUP_DATA_ENTRY
import com.grafitto.laudate.domain.backup.BackupArchiveStore
import com.grafitto.laudate.domain.backup.BackupExportResult
import com.grafitto.laudate.domain.backup.BackupSerializer
import com.grafitto.laudate.domain.backup.FullBackup
import com.grafitto.laudate.domain.backup.FullBackupSerializer
import com.grafitto.laudate.domain.backup.toBackupAttachments
import com.grafitto.laudate.domain.platform.ioDispatcher
import com.grafitto.laudate.domain.repository.AttachmentRepository
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.CelebrationRepository
import com.grafitto.laudate.domain.repository.RhythmPatternRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.repository.TablatureRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Caso de uso: generar una **copia de seguridad completa** de la biblioteca.
 *
 * Empaqueta todo en un ZIP: las canciones, los repertorios, las categorías (JSON) y los ficheros
 * adjuntos. El archivo resultante se puede descargar, compartir o guardar en una carpeta de Google
 * Drive elegida con el selector del sistema.
 *
 * El destino ([destinationUri]) llega ya resuelto desde la UI (SAF), y el almacén concreto se
 * inyecta como [BackupArchiveStore], de modo que el Dominio no conoce Android.
 */
class ExportBackupUseCase(
    private val songRepository: SongRepository,
    private val celebrationRepository: CelebrationRepository,
    private val categoryRepository: CategoryRepository,
    private val attachmentRepository: AttachmentRepository,
    private val rhythmPatternRepository: RhythmPatternRepository,
    private val archiveStore: BackupArchiveStore,
    private val appVersion: String,
    private val tablatureRepository: TablatureRepository? = null
) {

    suspend operator fun invoke(destinationUri: String): BackupExportResult = withContext(ioDispatcher) {
        val songs = songRepository.getAllSongsWithLyrics().first()
        val celebrations = celebrationRepository.getAllCelebrations().first()
        val categories = categoryRepository.getAllCategoriesSnapshot()
        // Los patrones integrados no se respaldan: viajan dentro de la aplicación.
        val rhythms = rhythmPatternRepository.getCustomPatterns().first()
        val tablatures = tablatureRepository?.getAllTablatures() ?: emptyList()

        val attachments = if (songs.isEmpty()) {
            emptyList()
        } else {
            attachmentRepository.getAttachmentsForSongs(songs.map { it.id })
        }

        val backupAttachments = attachments.toBackupAttachments()

        val manifest = FullBackup(
            appVersion = appVersion,
            payload = BackupSerializer.toPayload(songs, celebrations, categories, rhythms, tablatures),
            attachments = backupAttachments
        )

        archiveStore.writeArchive(
            destinationUri = destinationUri,
            manifestEntryName = BACKUP_DATA_ENTRY,
            manifestJson = FullBackupSerializer.encode(manifest),
            attachments = attachments
        )

        BackupExportResult(
            destinationUri = destinationUri,
            songsExported = songs.size,
            attachmentsExported = attachments.size,
            bytesWritten = 0L
        )
    }
}
