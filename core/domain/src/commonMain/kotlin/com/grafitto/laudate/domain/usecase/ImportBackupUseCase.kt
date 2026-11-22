package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.backup.BACKUP_DATA_ENTRY
import com.grafitto.laudate.domain.backup.BackupArchiveStore
import com.grafitto.laudate.domain.backup.BackupAttachment
import com.grafitto.laudate.domain.backup.BackupImportResult
import com.grafitto.laudate.domain.backup.BackupImportSummary
import com.grafitto.laudate.domain.backup.BackupLocationProvider
import com.grafitto.laudate.domain.backup.BackupPreview
import com.grafitto.laudate.domain.backup.BackupRestoreMode
import com.grafitto.laudate.domain.backup.BackupSerializer
import com.grafitto.laudate.domain.backup.FullBackup
import com.grafitto.laudate.domain.backup.FullBackupSerializer
import com.grafitto.laudate.domain.backup.LibraryWiper
import com.grafitto.laudate.domain.backup.SongConflict
import com.grafitto.laudate.domain.backup.SongConflictPolicy
import com.grafitto.laudate.domain.backup.rhythmSignature
import com.grafitto.laudate.domain.backup.toBackupAttachments
import com.grafitto.laudate.domain.backup.toDomain
import com.grafitto.laudate.domain.backup.toDomainCategories
import com.grafitto.laudate.domain.backup.toDomainSongs
import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.model.attachment.AttachmentKind
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.platform.ioDispatcher
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.repository.AttachmentRepository
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.CelebrationRepository
import com.grafitto.laudate.domain.repository.RhythmPatternRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.transfer.BundleRhythmPattern
import com.grafitto.laudate.domain.transfer.toDomain
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Caso de uso: **importar** (fusionar) y **restaurar** una copia de seguridad completa.
 *
 * Incorpora las canciones, los repertorios, las categorías, los ritmos y los adjuntos del archivo.
 * En el modo de fusión (el que usa «Importar una biblioteca») **nunca borra**: añade lo que falta y
 * **actualiza** lo que coincide —canciones y misas por id, ritmos por nombre—, que es lo correcto al
 * recibir la biblioteca de un compañero del coro o al reinstalar la app sin perder lo local. En ambos
 * modos (fusión y restauración destructiva) se crea antes una **copia de seguridad automática** del
 * estado actual, por si el usuario quiere volver atrás.
 *
 * Las **canciones** son el único caso que puede requerir una decisión: si un canto del archivo
 * tiene el **mismo `id`** que uno local pero **contenido distinto** (esto es, se corrigió en el
 * otro dispositivo), no basta con fusionar. Para resolverlo:
 *
 *  1. [inspect] analiza el archivo **sin escribir nada** y devuelve un [BackupImportSummary] con la
 *     lista de [SongConflict].
 *  2. La UI puede preguntar al usuario, que elige una [SongConflictPolicy] por canto (o «aplicar a
 *     todos»); si no pregunta, gana el valor por defecto.
 *  3. [invoke] aplica esas decisiones: `OVERWRITE` sustituye la versión local (por defecto al
 *     fusionar), `KEEP_BOTH` añade la del archivo como un canto nuevo y `SKIP` conserva la local.
 */
class ImportBackupUseCase(
    private val songRepository: SongRepository,
    private val celebrationRepository: CelebrationRepository,
    private val categoryRepository: CategoryRepository,
    private val attachmentRepository: AttachmentRepository,
    private val rhythmPatternRepository: RhythmPatternRepository,
    private val archiveStore: BackupArchiveStore,
    private val libraryWiper: LibraryWiper,
    /** Repositorio de tablaturas (ADR-017, *Feature T*). Opcional por retrocompatibilidad. */
    private val tablatureRepository: TablatureRepository? = null,
    /**
     * Dónde se guarda la **copia de seguridad automática** previa a un borrado. Puede ser `null`
     * (p. ej. en pruebas o si la plataforma no la soporta): entonces no se crea red de seguridad.
     */
    private val backupLocationProvider: BackupLocationProvider? = null,
    private val generateId: () -> String = { newId() }
) {

    /**
     * Analiza el archivo **sin modificar la biblioteca**: qué contiene, cuántos cantos trae, cuántos
     * son nuevos y qué canciones existentes entrarían en conflicto (mismo `id`, contenido distinto).
     *
     * Es obligatorio en la restauración destructiva ([BackupRestoreMode.REPLACE]): hay que verificar
     * el archivo y mostrar su contenido **antes** de pedir confirmación y borrar nada.
     */
    suspend fun inspect(archiveUri: String): BackupImportSummary = withContext(ioDispatcher) {
        val manifest = readManifest(archiveUri)
        val songs = manifest.payload.toDomainSongs()

        val localById = resolveLocalHashes(songs)
        val newSongs = mutableListOf<Song>()
        val conflicts = mutableListOf<SongConflict>()

        songs.forEach { song ->
            val local = localById[song.id]
            if (local == null) {
                newSongs += song
            } else if (contentDiffers(local, song)) {
                conflicts += SongConflict(
                    songId = song.id,
                    title = song.title,
                    number = song.number,
                    localUpdatedAt = local.updatedAt,
                    incomingUpdatedAt = song.updatedAt
                )
            }
        }

        BackupImportSummary(
            preview = BackupPreview(
                songs = songs.size,
                celebrations = manifest.payload.celebrations.size,
                categories = manifest.payload.categories.size,
                rhythms = manifest.payload.rhythms.size,
                attachments = manifest.attachments.size,
                exportedAt = manifest.exportedAt.takeIf { it > 0 },
                appVersion = manifest.appVersion.takeIf { it.isNotBlank() && it != "desconocida" }
            ),
            songsInBackup = songs.size,
            newSongs = newSongs.size,
            conflicts = conflicts.sortedBy { it.title.lowercase() }
        )
    }

    /**
     * Restaura la copia.
     *
     * En ambos modos se crea antes una **copia de seguridad automática** del estado actual (se
     * devuelve en [BackupImportResult.safetyBackupFileName]), porque la importación puede actualizar
     * cantos, misas o ritmos que ya existían.
     *
     * @param mode [BackupRestoreMode.MERGE] (sincronizar: añade lo que falta y actualiza lo que
     *   coincide, sin borrar nada) o [BackupRestoreMode.REPLACE] (restaurar: vacía la biblioteca y
     *   la deja como el archivo).
     * @param policies decisión del usuario por cada canto en conflicto. Si un canto no aparece, se
     *   aplica [SongConflictPolicy.OVERWRITE] en [BackupRestoreMode.MERGE] (sincronizar sobrescribe)
     *   y se ignora en [BackupRestoreMode.REPLACE] (el borrado previo ya deja solo la versión nueva).
     */
    suspend operator fun invoke(
        archiveUri: String,
        policies: Map<String, SongConflictPolicy> = emptyMap(),
        mode: BackupRestoreMode = BackupRestoreMode.MERGE
    ): BackupImportResult = withContext(ioDispatcher) {
        val manifest = readManifest(archiveUri)

        // 0) Antes de tocar nada se guarda una **copia automática** del estado actual: es la red de
        //    seguridad del usuario, porque una importación puede actualizar cantos, misas o ritmos
        //    que ya tenía. Se crea en LOS DOS modos: al fusionar (añadir una biblioteca de otro
        //    dispositivo) también conviene poder volver atrás. En la restauración destructiva, además,
        //    se vacía la biblioteca ANTES de escribir la del archivo (el orden importa por las
        //    restricciones de clave foránea: repertorios → canciones).
        val safetyFileName = createSafetyBackup()
        if (mode == BackupRestoreMode.REPLACE) {
            libraryWiper.wipeAll()
        }

        // 1) Categorías (la naturaleza declarada manda; las ya existentes por nombre no se duplican).
        val categories = manifest.payload.toDomainCategories()
        val categoriesImported = if (categories.isEmpty()) {
            0
        } else {
            categoryRepository.saveCategories(categories)
        }

        // 2) Canciones. Se respeta el id original para que los adjuntos y los repertorios del
        //    propio respaldo sigan apuntando al canto correcto.
        val songs = manifest.payload.toDomainSongs()
        val localById = resolveLocalHashes(songs)
        val newSongs = mutableListOf<Song>()
        var songsOverwritten = 0
        var songsSkipped = 0

        songs.forEach { song ->
            val local = localById[song.id]
            when {
                local == null -> newSongs += song

                !contentDiffers(local, song) -> {
                    // Idéntico: no hay nada que hacer (fusionar es idempotente).
                }

                else -> {
                    // En REPLACE la biblioteca está vacía, así que no hay conflicto real; en MERGE
                    // el valor por defecto es sobrescribir (sincronización), salvo decisión explícita.
                    val policy = policies[song.id]
                        ?: if (mode == BackupRestoreMode.MERGE) SongConflictPolicy.OVERWRITE
                        else SongConflictPolicy.SKIP
                    when (policy) {
                        SongConflictPolicy.OVERWRITE -> {
                            songRepository.overwriteSong(song)
                            songsOverwritten++
                        }
                        SongConflictPolicy.KEEP_BOTH -> newSongs += song.asNewCopy()
                        SongConflictPolicy.SKIP -> songsSkipped++
                    }
                }
            }
        }

        if (newSongs.isNotEmpty()) {
            songRepository.saveSongs(newSongs)
        }

        // 3) Repertorios (siempre se crean con los ids del respaldo).
        manifest.payload.celebrations.map { it.toDomain() }.forEach { celebration ->
            celebrationRepository.saveCelebration(celebration)
        }

        // 4) Patrones rítmicos personalizados. Se omiten los que ya existan con el mismo nombre y
        //    compás+contenido, para que restaurar dos veces no duplique. Los integrados no viajan
        //    en la copia; un patrón importado se guarda siempre como personalizado.
        val rhythmsImported = importRhythms(manifest.payload.rhythms)

        // 5) Tablaturas (ADR-017, Feature T). Se reasocian al canto por su id; las que no
        //    encuentran su canto se descartan para no dejar filas huérfanas.
        val tablaturesImported = importTablatures(manifest.payload.tablatures)

        // 6) Adjuntos: los enlaces se recrean a partir de su URL; los ficheros se extraen del ZIP.
        var attachmentsImported = 0
        manifest.attachments.forEach { entry ->
            val kind = AttachmentKind.fromName(entry.kind) ?: AttachmentKind.DOCUMENT
            if (kind == AttachmentKind.LINK) {
                val url = entry.linkUrl ?: return@forEach
                attachmentRepository.addAttachment(
                    SongAttachment(
                        id = entry.id,
                        songId = entry.songId,
                        kind = AttachmentKind.LINK,
                        displayName = entry.displayName,
                        uri = url,
                        mimeType = entry.mimeType,
                        sizeBytes = null,
                        createdAt = entry.createdAt
                    )
                )
                attachmentsImported++
            } else {
                val entryPath = entry.entryPath ?: return@forEach
                val localPath = runCatching {
                    archiveStore.extractAttachment(
                        archiveUri = archiveUri,
                        entryPath = entryPath,
                        songId = entry.songId,
                        displayName = entry.displayName,
                        mimeType = entry.mimeType
                    )
                }.getOrNull() ?: return@forEach

                attachmentRepository.addAttachment(
                    SongAttachment(
                        id = entry.id,
                        songId = entry.songId,
                        kind = kind,
                        displayName = entry.displayName,
                        uri = localPath,
                        mimeType = entry.mimeType,
                        sizeBytes = entry.sizeBytes,
                        createdAt = entry.createdAt
                    )
                )
                attachmentsImported++
            }
        }

        BackupImportResult(
            songsImported = newSongs.size,
            celebrationsImported = manifest.payload.celebrations.size,
            categoriesImported = categoriesImported,
            attachmentsImported = attachmentsImported,
            rhythmsImported = rhythmsImported,
            tablaturesImported = tablaturesImported,
            replaced = mode == BackupRestoreMode.REPLACE,
            songsOverwritten = songsOverwritten,
            songsSkipped = songsSkipped,
            safetyBackupFileName = safetyFileName
        )
    }

    /**
     * ¿Existe una **copia de seguridad automática** previa a la última importación?
     * Si la hay, devuelve una referencia lista para restaurarla o compartirla.
     */
    suspend fun latestSafetyBackup(): String? {
        val provider = backupLocationProvider ?: return null
        return provider.latestAutoBackup(SAFETY_BACKUP_PREFIX)
    }

    /** Guarda una **copia de seguridad automática** del estado actual antes de importar.
     *
     * Devuelve el nombre del archivo creado, o `null` si no hay [BackupLocationProvider] o si el
     * usuario aún no tiene nada que perder (biblioteca vacía). Un fallo al crear la copia **no**
     * aborta la importación: es una ayuda, no un requisito.
     */
    private suspend fun createSafetyBackup(): String? {
        val provider = backupLocationProvider ?: return null
        return runCatching {
            val songs = songRepository.getAllSongsWithLyrics().first()
            val celebrations = celebrationRepository.getAllCelebrations().first()
            val categories = categoryRepository.getAllCategoriesSnapshot()
            val rhythms = rhythmPatternRepository.getCustomPatterns().first()
            // Sin canciones ni repertorios no hay nada que salvaguardar.
            if (songs.isEmpty() && celebrations.isEmpty()) return@runCatching null

            val attachments = if (songs.isEmpty()) {
                emptyList()
            } else {
                attachmentRepository.getAttachmentsForSongs(songs.map { it.id })
            }

            val backupAttachments = attachments.toBackupAttachments()

            val fileName = "$SAFETY_BACKUP_PREFIX${nowStamp()}.canticora"
            val destination = provider.createAutoBackupTarget(fileName)
            val manifest = FullBackup(
                appVersion = "auto",
                payload = BackupSerializer.toPayload(songs, celebrations, categories, rhythms),
                attachments = backupAttachments
            )
            archiveStore.writeArchive(
                destinationUri = destination,
                manifestEntryName = BACKUP_DATA_ENTRY,
                manifestJson = FullBackupSerializer.encode(manifest),
                attachments = attachments
            )
            fileName
        }.getOrNull()
    }

    /** Marca de tiempo `yyyyMMdd-HHmmss` para el nombre de la copia automática. */
    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun nowStamp(): String {
        val totalSeconds = kotlin.time.Clock.System.now().toEpochMilliseconds() / 1000
        val days = totalSeconds / 86_400
        val secondsOfDay = totalSeconds % 86_400
        val (year, month, day) = civilFromDays(days)
        fun pad(value: Long) = value.toString().padStart(2, '0')
        return "$year${pad(month)}${pad(day)}-" +
            "${pad(secondsOfDay / 3600)}${pad((secondsOfDay % 3600) / 60)}${pad(secondsOfDay % 60)}"
    }

    /** Convierte días desde 1970-01-01 a (año, mes, día) con el algoritmo civil de Howard Hinnant. */
    private fun civilFromDays(daysSinceEpoch: Long): Triple<Long, Long, Long> {
        val z = daysSinceEpoch + 719_468
        val era = (if (z >= 0) z else z - 146_096) / 146_097
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = if (mp < 10) mp + 3 else mp - 9
        return Triple(if (m <= 2) y + 1 else y, m, d)
    }

    /** Lee y valida el manifiesto del ZIP, con mensajes de error claros. */
    private suspend fun readManifest(archiveUri: String) =
        (archiveStore.readEntry(archiveUri, BACKUP_DATA_ENTRY)
            ?: throw IllegalArgumentException(
                "El archivo no es una copia de seguridad de Canticora (falta el manifiesto)"
            )).let { manifestJson ->
            val manifest = FullBackupSerializer.decode(manifestJson)
            require(manifest.format in com.grafitto.laudate.domain.backup.CANTICORA_BACKUP_FORMATS) {
                "El archivo pertenece a otro formato (${manifest.format})"
            }
            manifest
        }

    /**
     * Devuelve las canciones locales que coinciden por `id`, con su **huella de contenido** ya
     * resuelta. Las filas antiguas sin huella se cargan con su letra y se calcula al vuelo, para no
     * marcar como conflicto una canción que en realidad es idéntica.
     */
    private suspend fun resolveLocalHashes(incoming: List<Song>): Map<String, Song> {
        if (incoming.isEmpty()) return emptyMap()
        val stubs = songRepository.getSongStubsByIds(incoming.map { it.id })
        if (stubs.isEmpty()) return emptyMap()

        val missingHashIds = stubs.filter { it.contentHash.isNullOrBlank() }.map { it.id }
        val resolved: Map<String, Song> = if (missingHashIds.isEmpty()) {
            stubs.associateBy { it.id }
        } else {
            val full = songRepository.getSongsByIds(missingHashIds)
                .associate { it.id to it.copy(contentHash = SongContentHasher.hash(it)) }
            stubs.associateBy { it.id } + full
        }
        return resolved
    }

    /**
     * Decide si un canto local y uno entrante son **distintos**.
     *
     * Se compara el contenido musical por su huella (título, letra, acordes, tonalidad…) y, además,
     * los **momentos y tiempos litúrgicos**, que no forman parte del texto ChordPro y por tanto no
     * entran en la huella. Se comparan aparte para que un canto al que solo se le ha cambiado la
     * categoría también se detecte como conflicto.
     *
     * **No** se comparan `createdAt`/`updatedAt`: son marcas de tiempo, no contenido, y el mismo
     * canto en dos dispositivos debe seguir considerándose el mismo aunque se creara en días distintos.
     */
    private fun contentDiffers(local: Song, incoming: Song): Boolean {
        if (SongContentHasher.hash(incoming) != local.contentHash) return true
        return !sameTags(local.defaultLiturgicalMoments, incoming.defaultLiturgicalMoments) ||
            !sameTags(local.defaultLiturgicalTimes, incoming.defaultLiturgicalTimes)
    }

    /** Compara dos listas de etiquetas sin importar el orden, las mayúsculas ni los espacios sobrantes. */
    private fun sameTags(a: List<String>, b: List<String>): Boolean =
        a.map { it.trim().lowercase() }.toSet() == b.map { it.trim().lowercase() }.toSet()

    /**
     * Convierte una canción entrante en una **copia nueva e independiente**: id nuevo, título
     * marcado como importado y todos los identificadores internos (secciones y anclajes)
     * regenerados.
     *
     * Es imprescindible regenerar los ids de secciones y anclajes: son claves primarias y, si se
     * reutilizaran, sobrescribirían las de la canción original en lugar de crear una copia.
     */
    private fun Song.asNewCopy(): Song {
        val newSongId = generateId()
        return copy(
            id = newSongId,
            title = "$title (importado)",
            contentHash = null,
            sections = sections.map { section ->
                val newSectionId = generateId()
                section.copy(
                    id = newSectionId,
                    songId = newSongId,
                    lines = section.lines.map { line ->
                        line.copy(
                            chords = line.chords.map { anchor ->
                                anchor.copy(id = generateId(), sectionId = newSectionId)
                            }
                        )
                    }
                )
            }
        )
    }

    /**
     * Guarda los patrones que trae la biblioteca. Un patrón que coincide **por nombre** con uno
     * personalizado se **actualiza** (se conserva su id, para no duplicarlo); los idénticos se
     * omiten (importar dos veces es idempotente) y el resto se añaden.
     *
     * Devuelve cuántos se han incorporado de verdad (nuevos + actualizados), sin contar los idénticos.
     */
    private suspend fun importRhythms(rhythms: List<BundleRhythmPattern>): Int {
        if (rhythms.isEmpty()) return 0
        val existing = rhythmPatternRepository.getAllPatterns().first()
        val bySignature = existing.map { it.rhythmSignature() }.toMutableSet()
        val customByName = existing.filterNot { it.isBuiltIn }
            .associateBy { it.name.trim().lowercase() }

        var imported = 0
        rhythms.forEach { bundle ->
            val pattern = bundle.toDomain(newId())
            if (!bySignature.add(pattern.rhythmSignature())) return@forEach // idéntico: nada que hacer

            val sameName = customByName[pattern.name.trim().lowercase()]
            if (sameName != null) {
                // Actualiza el ritmo existente conservando su id para no duplicarlo.
                rhythmPatternRepository.savePattern(pattern.copy(id = sameName.id))
            } else {
                rhythmPatternRepository.savePattern(pattern)
            }
            imported++
        }
        return imported
    }

    /**
     * Guarda las tablaturas que trae la biblioteca. Se emparejan por `id`: si ya existe una
     * tablatura local con ese id gana la de **`updated_at` más reciente** (la del archivo siempre
     * cuenta como la nueva, para no bloquear una sincronización). Las tablaturas cuyo canto no
     * existe en el destino se descartan (la clave foránea lo exige).
     */
    private suspend fun importTablatures(tablatures: List<com.grafitto.laudate.domain.transfer.BundleTablature>): Int {
        val repo = tablatureRepository ?: return 0
        if (tablatures.isEmpty()) return 0
        val songIds = songRepository.getAllSongs().first().mapTo(mutableSetOf()) { it.id }
        var imported = 0
        tablatures.forEach { bundle ->
            val domain = bundle.toDomain() ?: return@forEach
            val songId = domain.songId
            if (songId !in songIds) return@forEach
            repo.saveTablature(domain)
            imported++
        }
        return imported
    }

    companion object {
        /**
         * Prefijo de las copias de seguridad automáticas previas a una importación.
         * El nombre completo lleva marca de tiempo, de modo que una copia nueva nunca sobrescribe
         * la que se pueda estar leyendo, y siempre se puede ofrecer la más reciente.
         */
        const val SAFETY_BACKUP_PREFIX: String = "canticora_copia_automatica_"
    }
}
