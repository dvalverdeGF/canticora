package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.backup.BackupArchiveStore
import com.grafitto.laudate.domain.backup.BackupAttachment
import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.model.attachment.AttachmentKind
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.parser.ChordProParser
import com.grafitto.laudate.domain.platform.ioDispatcher
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.repository.AttachmentRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.transfer.ImportConflict
import com.grafitto.laudate.domain.transfer.ImportConflictPolicy
import com.grafitto.laudate.domain.transfer.SONG_DATA_ENTRY
import com.grafitto.laudate.domain.transfer.SongPackage
import com.grafitto.laudate.domain.transfer.SongPackageSerializer
import com.grafitto.laudate.domain.transfer.toDomain
import kotlinx.coroutines.withContext

/**
 * Resultado de importar un paquete de canción.
 */
data class SongPackageImportResult(
    val title: String,
    /** `true` si el canto se ha creado nuevo; `false` si se ha actualizado uno existente. */
    val created: Boolean,
    /** `true` si el canto ya existía idéntico y no se ha tocado. */
    val identical: Boolean = false,
    /** Adjuntos incorporados (ficheros extraídos + enlaces recreados). */
    val attachmentsImported: Int = 0,
    /** Categorías nuevas creadas a partir de los momentos/tiempos que citaba el canto. */
    val categoriesCreated: Int = 0,
    /** Tablaturas del paquete incorporadas al canto. */
    val tablaturesImported: Int = 0
)

/**
 * Inspección previa de un paquete **sin escribir nada**.
 *
 * @param conflict conflicto detectado (mismo canto, contenido distinto) o `null` si es nuevo/idéntico.
 * @param identical `true` si ya existe un canto con exactamente el mismo contenido.
 */
data class SongPackageImportPreview(
    val title: String,
    val attachmentCount: Int,
    val linkCount: Int,
    val tablatureCount: Int = 0,
    val identical: Boolean,
    val conflict: ImportConflict? = null
) {
    val hasConflict: Boolean get() = conflict != null
}

/**
 * Caso de uso: **importar un paquete de canción** (`canticora.song`, ADR-017): un canto suelto con
 * sus adjuntos.
 *
 * Reglas:
 *  - Un canto **idéntico** (misma huella de contenido) no se duplica: se reconoce como reutilizado.
 *  - Si ya existe el mismo canto **con cambios** (título/autor/número coinciden, contenido distinto)
 *    se decide con [ImportConflictPolicy]: sobrescribir (por defecto), conservar ambos u omitir. La
 *    aplicación llama antes a [inspect] para poder preguntar con permiso.
 *  - Los **adjuntos** se incorporan siempre que el canto se cree o se sobrescriba. Los **enlaces**
 *    viajan como URL y se recrean; los **ficheros** se extraen del ZIP al almacén privado.
 *  - Las **tablaturas** del paquete se reasocian al canto de destino (el id del canto cambia al
 *    importar, el de la tablatura no) y se guardan sin borrar las que ya tuviera el canto.
 *  - Las categorías citadas por el canto se reconcilian con [EnsureCategoriesForImportUseCase].
 *
 * Kotlin puro: el contenido del archivo llega ya leído y se accede a él a través de abstracciones
 * de Dominio ([BackupArchiveStore]), nunca de Android. Ver ADR-017.
 */
class ImportSongPackageUseCase(
    private val songRepository: SongRepository,
    private val attachmentRepository: AttachmentRepository,
    private val tablatureRepository: TablatureRepository,
    private val archiveStore: BackupArchiveStore,
    private val ensureCategoriesForImportUseCase: EnsureCategoriesForImportUseCase,
    private val generateId: () -> String = { newId() }
) {

    /**
     * Analiza el paquete **sin escribir nada**: qué canto trae, cuántos adjuntos y si ya existe
     * con cambios, para poder pedir permiso antes de sobrescribir.
     *
     * @throws IllegalArgumentException si el archivo no es un paquete de canción de Canticora.
     */
    suspend fun inspect(archiveUri: String): SongPackageImportPreview = withContext(ioDispatcher) {
        val manifest = readManifest(archiveUri)
        val song = parseSong(manifest)
        val hash = SongContentHasher.hash(song)
        val identical = songRepository.findSongByContentHash(hash) != null
        val conflict = if (!identical && songRepository.findSongByContentSignature(song) != null) {
            ImportConflict(key = hash, title = song.title, detail = song.author)
        } else {
            null
        }

        SongPackageImportPreview(
            title = song.title,
            attachmentCount = manifest.attachments.count { it.entryPath != null },
            linkCount = manifest.attachments.count { it.kind == AttachmentKind.LINK.name },
            tablatureCount = manifest.tablatures.size,
            identical = identical,
            conflict = conflict
        )
    }

    /**
     * Importa el paquete.
     *
     * @param policy decisión del usuario cuando el canto ya existe con cambios.
     * @throws IllegalArgumentException si el archivo no es un paquete de canción de Canticora.
     */
    suspend operator fun invoke(
        archiveUri: String,
        policy: ImportConflictPolicy = ImportConflictPolicy.OVERWRITE
    ): SongPackageImportResult = withContext(ioDispatcher) {
        val manifest = readManifest(archiveUri)
        val incoming = parseSong(manifest)
        val hash = SongContentHasher.hash(incoming)

        val identicalSong = songRepository.findSongByContentHash(hash)
        val sameSong = if (identicalSong == null) {
            songRepository.findSongByContentSignature(incoming)
        } else {
            null
        }

        // Idéntico: no se duplica ni se tocan sus adjuntos (idempotente).
        if (identicalSong != null) {
            return@withContext SongPackageImportResult(
                title = identicalSong.title,
                created = false,
                identical = true
            )
        }

        var created = true
        var resultTitle = incoming.title
        val targetSongId: String = when {
            sameSong == null -> {
                val song = incoming.copy(
                    defaultLiturgicalMoments = manifest.song.defaultLiturgicalMoments,
                    defaultLiturgicalTimes = manifest.song.defaultLiturgicalTimes
                )
                songRepository.saveSong(song)
                song.id
            }
            policy == ImportConflictPolicy.SKIP -> {
                return@withContext SongPackageImportResult(
                    title = sameSong.title,
                    created = false,
                    identical = true
                )
            }
            policy == ImportConflictPolicy.KEEP_BOTH -> {
                val song = incoming.copy(
                    id = generateId(),
                    title = "${incoming.title} (importado)",
                    contentHash = null,
                    defaultLiturgicalMoments = manifest.song.defaultLiturgicalMoments,
                    defaultLiturgicalTimes = manifest.song.defaultLiturgicalTimes
                )
                songRepository.saveSong(song)
                resultTitle = song.title
                song.id
            }
            else -> {
                // OVERWRITE: se conserva el id y la identidad de la fila (no se pierden los adjuntos
                // previos ni los vínculos con repertorios) y se escriben los metadatos del archivo.
                val song = incoming.copy(
                    id = sameSong.id,
                    defaultLiturgicalMoments = manifest.song.defaultLiturgicalMoments,
                    defaultLiturgicalTimes = manifest.song.defaultLiturgicalTimes
                )
                songRepository.overwriteSong(song)
                created = false
                resultTitle = sameSong.title
                song.id
            }
        }

        // Adjuntos: se recrean los enlaces y se extraen los ficheros al almacén privado del canto.
        var attachmentsImported = 0
        manifest.attachments.forEach { entry ->
            importAttachment(entry, archiveUri, targetSongId)?.let {
                attachmentsImported++
            }
        }

        // Tablaturas: el canto de destino puede tener otro id que el de origen, así que se
        // reasocian antes de guardarlas. Se conservan las que el canto ya tuviera.
        var tablaturesImported = 0
        manifest.tablatures.forEach { entry ->
            val tablature = entry.toDomain() ?: return@forEach
            tablatureRepository.saveTablature(tablature.copy(songId = targetSongId))
            tablaturesImported++
        }

        val referenced = manifest.song.defaultLiturgicalMoments + manifest.song.defaultLiturgicalTimes
        val categoriesCreated = if (referenced.isNotEmpty()) {
            ensureCategoriesForImportUseCase(referenced).totalCreated
        } else {
            0
        }

        SongPackageImportResult(
            title = resultTitle,
            created = created,
            identical = false,
            attachmentsImported = attachmentsImported,
            categoriesCreated = categoriesCreated,
            tablaturesImported = tablaturesImported
        )
    }

    /**
     * Incorpora un adjunto del paquete: los enlaces se recrean a partir de su URL; los ficheros se
     * extraen del ZIP. Devuelve el adjunto guardado, o `null` si no se pudo incorporar.
     */
    private suspend fun importAttachment(
        entry: BackupAttachment,
        archiveUri: String,
        songId: String
    ): SongAttachment? {
        val kind = AttachmentKind.fromName(entry.kind) ?: AttachmentKind.DOCUMENT
        return if (kind == AttachmentKind.LINK) {
            val url = entry.linkUrl ?: return null
            val attachment = SongAttachment(
                id = entry.id,
                songId = songId,
                kind = AttachmentKind.LINK,
                displayName = entry.displayName,
                uri = url,
                mimeType = entry.mimeType,
                sizeBytes = null,
                createdAt = entry.createdAt
            )
            attachmentRepository.addAttachment(attachment)
            attachment
        } else {
            val entryPath = entry.entryPath ?: return null
            val localPath = runCatching {
                archiveStore.extractAttachment(
                    archiveUri = archiveUri,
                    entryPath = entryPath,
                    songId = songId,
                    displayName = entry.displayName,
                    mimeType = entry.mimeType
                )
            }.getOrNull() ?: return null

            val attachment = SongAttachment(
                id = entry.id,
                songId = songId,
                kind = kind,
                displayName = entry.displayName,
                uri = localPath,
                mimeType = entry.mimeType,
                sizeBytes = entry.sizeBytes,
                createdAt = entry.createdAt
            )
            attachmentRepository.addAttachment(attachment)
            attachment
        }
    }

    /** Parsea el ChordPro del manifiesto a un [Song] con id nuevo (el id local lo decide el importador). */
    private fun parseSong(manifest: SongPackage): Song = ChordProParser.parse(
        chordProContent = manifest.song.chordPro,
        songId = generateId(),
        fallbackTitle = manifest.song.title.ifBlank { "Sin título" }
    )

    /** Lee y valida el manifiesto del ZIP, con mensajes de error claros. */
    private suspend fun readManifest(archiveUri: String): SongPackage {
        val json = archiveStore.readEntry(archiveUri, SONG_DATA_ENTRY)
            ?: throw IllegalArgumentException(
                "El archivo no es un canto de Canticora (falta el manifiesto)"
            )
        return SongPackageSerializer.decode(json)
    }
}
