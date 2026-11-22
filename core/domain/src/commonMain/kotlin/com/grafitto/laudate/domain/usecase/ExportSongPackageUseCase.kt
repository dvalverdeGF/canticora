package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.backup.BackupArchiveStore
import com.grafitto.laudate.domain.backup.toBackupAttachments
import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.platform.ioDispatcher
import com.grafitto.laudate.domain.repository.AttachmentRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.serializer.ChordProSerializer
import com.grafitto.laudate.domain.transfer.SONG_DATA_ENTRY
import com.grafitto.laudate.domain.transfer.SongPackage
import com.grafitto.laudate.domain.transfer.SongPackageSerializer
import com.grafitto.laudate.domain.transfer.SongPackageSong
import com.grafitto.laudate.domain.transfer.toBundleTablature
import kotlinx.coroutines.withContext

/**
 * Resultado de generar el paquete de una canción.
 *
 * @param attachmentsExported Ficheros adjuntos incluidos en el ZIP.
 * @param linksExported Enlaces incluidos (viajan como URL, sin fichero).
 * @param tablaturesExported Tablaturas del canto incluidas en el manifiesto.
 */
data class SongPackageExportResult(
    val destinationUri: String,
    val title: String,
    val attachmentsExported: Int,
    val linksExported: Int,
    val tablaturesExported: Int = 0,
    val bytesWritten: Long = 0L
)

/**
 * Caso de uso: empaquetar **una sola canción con sus adjuntos** en un archivo compartible
 * (`canticora.song`).
 *
 * Es la vía que se ofrece al pulsar «Compartir» en un canto y responder **sí** a la pregunta
 * «¿quieres compartir también los adjuntos?». Empaqueta el ChordPro del canto, sus **tablaturas** y
 * sus ficheros en un único ZIP, reutilizando el mismo almacén de archivos que la copia de seguridad
 * ([BackupArchiveStore]): no hay dos implementaciones de empaquetado que mantener en sincronía.
 *
 * El destino ([destinationUri]) llega ya resuelto desde la UI (SAF en Android, diálogo nativo en
 * escritorio), así que el Dominio no conoce la plataforma. Ver ADR-017.
 */
class ExportSongPackageUseCase(
    private val songRepository: SongRepository,
    private val attachmentRepository: AttachmentRepository,
    private val tablatureRepository: TablatureRepository,
    private val archiveStore: BackupArchiveStore,
    private val appVersion: String
) {

    /**
     * Escribe el paquete de [songId] en [destinationUri] y devuelve un resumen de lo incluido.
     *
     * @throws IllegalArgumentException si el canto ya no existe (p. ej. se borró desde otra pantalla).
     */
    suspend operator fun invoke(destinationUri: String, songId: String): SongPackageExportResult =
        withContext(ioDispatcher) {
            val song = songRepository.getSongsByIds(listOf(songId)).firstOrNull()
                ?: throw IllegalArgumentException("No se encontró el canto que quieres compartir")

            val attachments = attachmentRepository.getAttachmentsForSongs(listOf(songId))
            val tablatures = tablatureRepository.getTablaturesForSong(songId)

            val manifest = SongPackage(
                appVersion = appVersion,
                song = SongPackageSong(
                    title = song.title,
                    number = song.number,
                    author = song.author,
                    originalKey = song.originalKey,
                    contentHash = SongContentHasher.hash(song),
                    defaultLiturgicalMoments = song.defaultLiturgicalMoments,
                    defaultLiturgicalTimes = song.defaultLiturgicalTimes,
                    chordPro = ChordProSerializer.serialize(song)
                ),
                attachments = attachments.toBackupAttachments(),
                tablatures = tablatures.map { it.toBundleTablature() }
            )

            // El manifiesto se escribe SIEMPRE primero: así el detector de archivos reconoce el
            // paquete por la cabecera del ZIP, sin descomprimirlo (ver LaudateFileDetector).
            archiveStore.writeArchive(
                destinationUri = destinationUri,
                manifestEntryName = SONG_DATA_ENTRY,
                manifestJson = SongPackageSerializer.encode(manifest),
                attachments = attachments
            )

            SongPackageExportResult(
                destinationUri = destinationUri,
                title = song.title,
                attachmentsExported = attachments.count { !it.isLink },
                linksExported = attachments.count { it.isLink },
                tablaturesExported = tablatures.size
            )
        }
}
