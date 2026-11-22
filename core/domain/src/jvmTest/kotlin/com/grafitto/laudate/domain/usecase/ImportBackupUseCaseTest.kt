package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.backup.BACKUP_DATA_ENTRY
import com.grafitto.laudate.domain.backup.BackupArchiveStore
import com.grafitto.laudate.domain.backup.BackupAttachment
import com.grafitto.laudate.domain.backup.BackupSerializer
import com.grafitto.laudate.domain.backup.FullBackup
import com.grafitto.laudate.domain.backup.FullBackupSerializer
import com.grafitto.laudate.domain.backup.BackupLocationProvider
import com.grafitto.laudate.domain.backup.BackupRestoreMode
import com.grafitto.laudate.domain.backup.LibraryWiper
import com.grafitto.laudate.domain.backup.SongConflictPolicy
import com.grafitto.laudate.domain.backup.backupAttachmentEntryPath
import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.domain.parser.ChordProParser
import com.grafitto.laudate.domain.repository.AttachmentRepository
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.CelebrationRepository
import com.grafitto.laudate.domain.repository.RhythmPatternRepository
import com.grafitto.laudate.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la restauración de una copia de seguridad completa. */
class ImportBackupUseCaseTest {

    private val songs = FakeSongRepository()
    private val celebrations = FakeCelebrationRepository()
    private val categories = FakeCategoryRepository()
    private val attachments = FakeAttachmentRepository()
    private val rhythms = FakeRhythmPatternRepository()
    private val archive = FakeArchiveStore()
    private val wiper = FakeLibraryWiper(songs)
    private val locationProvider = FakeBackupLocationProvider()
    private val useCase = ImportBackupUseCase(
        songs, celebrations, categories, attachments, rhythms, archive, wiper,
        backupLocationProvider = locationProvider
    )

    @Test
    fun `restaura canciones, repertorios, categorías y enlaces`() = runBlocking {
        val song = songOf("Aleluya")
        val manifest = FullBackup(
            payload = BackupSerializer.toPayload(
                songs = listOf(song),
                celebrations = listOf(
                    Celebration(id = "cel-1", name = "Misa", dateTimestamp = 0L)
                ),
                categories = listOf(
                    LiturgicalCategory(id = "c1", name = "Aleluya", kind = LiturgicalCategoryKind.MASS_MOMENT)
                )
            ),
            attachments = listOf(
                BackupAttachment(
                    id = "att-link",
                    songId = song.id,
                    kind = "LINK",
                    displayName = "Ensayo",
                    linkUrl = "https://youtube.com/x"
                ),
                BackupAttachment(
                    id = "att-file",
                    songId = song.id,
                    kind = "AUDIO",
                    displayName = "pista.mp3",
                    mimeType = "audio/mpeg",
                    entryPath = backupAttachmentEntryPath("att-file")
                )
            )
        )
        archive.manifest = FullBackupSerializer.encode(manifest)

        val result = useCase("content://copia.laudate")

        assertEquals(1, result.songsImported)
        assertEquals(1, result.celebrationsImported)
        assertEquals(1, result.categoriesImported)
        assertEquals(2, result.attachmentsImported)
        assertTrue(songs.stored.any { it.id == song.id })
        assertNotNull(celebrations.stored)
        assertEquals(2, attachments.stored.size)

        val link = attachments.stored.first { it.id == "att-link" }
        assertEquals("https://youtube.com/x", link.uri)

        val file = attachments.stored.first { it.id == "att-file" }
        // El fichero se ha extraído a una ruta local del almacén privado.
        assertEquals("/privado/${song.id}/pista.mp3", file.uri)
    }

    @Test
    fun `inspect detecta conflicto si solo cambian los momentos liturgicos`() = runBlocking {
        // El texto ChordPro es idéntico: la diferencia está solo en la categoría, que NO viaja en el
        // texto y por tanto no entra en la huella. Aun así debe detectarse como conflicto.
        songs.saveSong(songOf("Aleluya", moments = listOf("Entrada")))
        archive.manifest = FullBackupSerializer.encode(
            FullBackup(payload = BackupSerializer.toPayload(
                songs = listOf(songOf("Aleluya", moments = listOf("Comunión"))),
                celebrations = emptyList(),
                categories = emptyList()
            ))
        )

        val summary = useCase.inspect("content://copia.laudate")

        assertEquals(1, summary.conflicts.size)
    }

    @Test
    fun `inspect no marca conflicto si las etiquetas son iguales aunque cambie el orden`() = runBlocking {
        songs.saveSong(songOf("Aleluya", moments = listOf("Entrada", "Comunión")))
        archive.manifest = FullBackupSerializer.encode(
            FullBackup(payload = BackupSerializer.toPayload(
                songs = listOf(songOf("Aleluya", moments = listOf("comunión ", "ENTRADA"))),
                celebrations = emptyList(),
                categories = emptyList()
            ))
        )

        val summary = useCase.inspect("content://copia.laudate")

        assertTrue(summary.conflicts.isEmpty())
    }

    @Test
    fun `un archivo sin manifiesto falla con un mensaje claro`() = runBlocking {
        archive.manifest = null
        val error = runCatching { useCase("content://vacio") }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message!!.contains("no es una copia de seguridad"))
    }

    // --- Resolución de conflictos (mismo id, contenido distinto) ---------------------------------

    @Test
    fun `inspect detecta conflicto cuando el canto local difiere del archivo`() = runBlocking {
        songs.saveSong(songOf("Aleluya", lyrics = "Letra vieja"))
        archive.manifest = FullBackupSerializer.encode(
            FullBackup(payload = BackupSerializer.toPayload(
                songs = listOf(songOf("Aleluya", lyrics = "Letra corregida")),
                celebrations = emptyList(),
                categories = emptyList()
            ))
        )

        val summary = useCase.inspect("content://copia.laudate")

        assertEquals(1, summary.songsInBackup)
        assertEquals(0, summary.newSongs)
        assertTrue(summary.hasConflicts)
        assertEquals("song-Aleluya", summary.conflicts.single().songId)
        assertEquals("Aleluya", summary.conflicts.single().title)
    }

    @Test
    fun `inspect no marca conflicto si el contenido es identico`() = runBlocking {
        val song = songOf("Aleluya")
        songs.saveSong(song)
        archive.manifest = FullBackupSerializer.encode(
            FullBackup(payload = BackupSerializer.toPayload(
                songs = listOf(song),
                celebrations = emptyList(),
                categories = emptyList()
            ))
        )

        val summary = useCase.inspect("content://copia.laudate")

        assertEquals(0, summary.newSongs)
        assertTrue(summary.conflicts.isEmpty())
    }

    @Test
    fun `overwrite sustituye la version local con la del archivo`() = runBlocking {
        songs.saveSong(songOf("Aleluya", lyrics = "Letra vieja"))
        archive.manifest = FullBackupSerializer.encode(
            FullBackup(payload = BackupSerializer.toPayload(
                songs = listOf(songOf("Aleluya", lyrics = "Letra corregida")),
                celebrations = emptyList(),
                categories = emptyList()
            ))
        )

        val result = useCase(
            "content://copia.laudate",
            mapOf("song-Aleluya" to SongConflictPolicy.OVERWRITE)
        )

        assertEquals(1, result.songsOverwritten)
        assertEquals(0, result.songsImported)
        assertEquals(1, songs.stored.size)
        assertTrue(songs.stored.single().sections.single().lines.single().lyrics.contains("corregida"))
    }

    @Test
    fun `skip conserva la version local`() = runBlocking {
        songs.saveSong(songOf("Aleluya", lyrics = "Letra vieja"))
        archive.manifest = FullBackupSerializer.encode(
            FullBackup(payload = BackupSerializer.toPayload(
                songs = listOf(songOf("Aleluya", lyrics = "Letra corregida")),
                celebrations = emptyList(),
                categories = emptyList()
            ))
        )

        val result = useCase(
            "content://copia.laudate",
            mapOf("song-Aleluya" to SongConflictPolicy.SKIP)
        )

        assertEquals(1, result.songsSkipped)
        assertEquals(1, songs.stored.size)
        assertTrue(songs.stored.single().sections.single().lines.single().lyrics.contains("vieja"))
    }

    @Test
    fun `keep_both anade la version del archivo como canto nuevo sin tocar la local`() = runBlocking {
        songs.saveSong(songOf("Aleluya", lyrics = "Letra vieja"))
        archive.manifest = FullBackupSerializer.encode(
            FullBackup(payload = BackupSerializer.toPayload(
                songs = listOf(songOf("Aleluya", lyrics = "Letra corregida")),
                celebrations = emptyList(),
                categories = emptyList()
            ))
        )

        val result = useCase(
            "content://copia.laudate",
            mapOf("song-Aleluya" to SongConflictPolicy.KEEP_BOTH)
        )

        assertEquals(1, result.songsImported)
        assertEquals(2, songs.stored.size)
        // La original permanece intacta…
        val original = songs.stored.first { it.id == "song-Aleluya" }
        assertTrue(original.sections.single().lines.single().lyrics.contains("vieja"))
        // …y la copia entra con id nuevo, título marcado y secciones con id regenerado.
        val copy = songs.stored.first { it.id != "song-Aleluya" }
        assertEquals("Aleluya (importado)", copy.title)
        assertTrue(copy.sections.single().id != original.sections.single().id)
        assertTrue(copy.sections.single().lines.single().lyrics.contains("corregida"))
    }

    @Test
    fun `el modo por defecto (merge) sobrescribe las coincidencias sin vaciar`() = runBlocking {
        songs.saveSong(songOf("Aleluya", lyrics = "Letra vieja"))
        songs.saveSong(songOf("Otro canto"))
        archive.manifest = FullBackupSerializer.encode(
            FullBackup(payload = BackupSerializer.toPayload(
                songs = listOf(songOf("Aleluya", lyrics = "Letra corregida")),
                celebrations = emptyList(),
                categories = emptyList()
            ))
        )

        // Sin políticas explícitas: sincronizar sobrescribe.
        val result = useCase("content://copia.laudate")

        assertEquals(0, wiper.wiped)
        assertEquals(1, result.songsOverwritten)
        assertEquals(2, songs.stored.size)
        assertTrue(
            songs.stored.first { it.id == "song-Aleluya" }
                .sections.single().lines.single().lyrics.contains("corregida")
        )
    }

    @Test
    fun `el modo replace vacia la biblioteca y deja solo lo del archivo`() = runBlocking {
        songs.saveSong(songOf("Canto local que se borrara"))
        archive.manifest = FullBackupSerializer.encode(
            FullBackup(payload = BackupSerializer.toPayload(
                songs = listOf(songOf("Canto del archivo")),
                celebrations = emptyList(),
                categories = emptyList()
            ))
        )

        val result = useCase(
            archiveUri = "content://copia.laudate",
            policies = emptyMap(),
            mode = BackupRestoreMode.REPLACE
        )

        assertEquals(1, wiper.wiped)
        assertTrue(result.replaced)
        assertEquals(1, songs.stored.size)
        assertEquals("Canto del archivo", songs.stored.single().title)
    }

    @Test
    fun `el modo replace guarda una copia automatica antes de borrar`() = runBlocking {
        songs.saveSong(songOf("Canto que se borrara"))
        archive.manifest = FullBackupSerializer.encode(
            FullBackup(payload = BackupSerializer.toPayload(
                songs = listOf(songOf("Canto del archivo")),
                celebrations = emptyList(),
                categories = emptyList()
            ))
        )

        val result = useCase(
            archiveUri = "content://copia.laudate",
            policies = emptyMap(),
            mode = BackupRestoreMode.REPLACE
        )

        // Se creó la copia automática con el estado anterior…
        assertNotNull(locationProvider.created)
        assertEquals(result.safetyBackupFileName, locationProvider.created)
        // …y queda recuperable.
        assertNotNull(useCase.latestSafetyBackup())
    }

    @Test
    fun `el modo merge tambien crea copia automatica antes de importar`() = runBlocking {
        songs.saveSong(songOf("Aleluya", lyrics = "Letra vieja"))
        archive.manifest = FullBackupSerializer.encode(
            FullBackup(payload = BackupSerializer.toPayload(
                songs = listOf(songOf("Nuevo canto")),
                celebrations = emptyList(),
                categories = emptyList()
            ))
        )

        val result = useCase("content://copia.laudate")

        // Importar una biblioteca nunca borra, pero puede actualizar lo que coincide: siempre
        // se guarda antes una copia automática del estado anterior.
        assertNotNull(result.safetyBackupFileName)
        assertEquals(result.safetyBackupFileName, locationProvider.created)
        assertNotNull(useCase.latestSafetyBackup())
    }

    @Test
    fun `un ritmo que coincide por nombre se actualiza en vez de duplicarse`() = runBlocking {
        // El usuario ya tiene un ritmo personalizado llamado "Rasgueo propio".
        rhythms.stored += RhythmPattern(
            id = "ritmo-local",
            name = "Rasgueo propio",
            timeSignature = TimeSignature(4, 4),
            events = listOf(RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN))
        )
        // En la biblioteca que llega, ese mismo nombre trae un contenido distinto (corregido).
        val corregido = RhythmPattern(
            id = "otro-id", // id distinto: el emparejamiento es por NOMBRE
            name = "Rasgueo propio",
            timeSignature = TimeSignature(4, 4),
            events = listOf(
                RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN),
                RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.UP)
            )
        )
        archive.manifest = FullBackupSerializer.encode(
            FullBackup(payload = BackupSerializer.toPayload(
                songs = emptyList(),
                celebrations = emptyList(),
                categories = emptyList(),
                rhythms = listOf(corregido)
            ))
        )

        val result = useCase("content://copia.laudate")

        assertEquals(1, result.rhythmsImported)
        // Sigue habiendo un único ritmo con ese nombre, y con el contenido nuevo.
        val conEseNombre = rhythms.stored.filter { it.name == "Rasgueo propio" }
        assertEquals(1, conEseNombre.size)
        assertEquals("ritmo-local", conEseNombre.single().id)
        assertEquals(2, conEseNombre.single().events.size)
    }

    @Test
    fun `inspect devuelve el contenido del archivo para mostrarlo antes de restaurar`() = runBlocking {
        archive.manifest = FullBackupSerializer.encode(
            FullBackup(
                appVersion = "1.0.15",
                payload = BackupSerializer.toPayload(
                    songs = listOf(songOf("Aleluya"), songOf("Gloria")),
                    celebrations = listOf(Celebration(id = "cel-1", name = "Misa", dateTimestamp = 0L)),
                    categories = listOf(
                        LiturgicalCategory(id = "c1", name = "Entrada", kind = LiturgicalCategoryKind.MASS_MOMENT)
                    )
                ),
                attachments = listOf(
                    BackupAttachment(
                        id = "att-1", songId = "song-Aleluya", kind = "LINK",
                        displayName = "Ensayo", linkUrl = "https://x"
                    )
                )
            )
        )

        val summary = useCase.inspect("content://copia.laudate")

        assertEquals(2, summary.preview.songs)
        assertEquals(1, summary.preview.celebrations)
        assertEquals(1, summary.preview.categories)
        assertEquals(1, summary.preview.attachments)
        assertEquals("1.0.15", summary.preview.appVersion)
    }

    private fun songOf(
        title: String,
        lyrics: String = title,
        moments: List<String> = emptyList(),
        times: List<String> = emptyList()
    ): Song = ChordProParser.parse(
        chordProContent = "{title: $title}\n[C]$lyrics",
        songId = "song-$title",
        fallbackTitle = title
    ).copy(defaultLiturgicalMoments = moments, defaultLiturgicalTimes = times)

    private class FakeArchiveStore : BackupArchiveStore {
        var manifest: String? = null

        override suspend fun writeArchive(
            destinationUri: String,
            manifestEntryName: String,
            manifestJson: String,
            attachments: List<SongAttachment>
        ) = Unit

        override suspend fun readEntry(archiveUri: String, entryName: String): String? =
            if (entryName == BACKUP_DATA_ENTRY) manifest else null

        override suspend fun readEntryEndingWith(archiveUri: String, suffix: String): String? =
            if (BACKUP_DATA_ENTRY.endsWith(suffix, ignoreCase = true)) manifest else null

        override suspend fun extractAttachment(
            archiveUri: String,
            entryPath: String,
            songId: String,
            displayName: String,
            mimeType: String?
        ): String = "/privado/$songId/${displayName}"

        override suspend fun copyLocalFileTo(attachment: SongAttachment, destinationUri: String) = Unit
    }

    private class FakeSongRepository : SongRepository {
        val stored = mutableListOf<Song>()
        override fun getAllSongs(): Flow<List<Song>> = flowOf(stored)
        override fun getAllSongsWithLyrics(): Flow<List<Song>> = flowOf(stored)
        override suspend fun getSongCount(): Int = stored.size
        override fun getFavoriteSongs(): Flow<List<Song>> = flowOf(stored)
        override fun getSongById(id: String): Flow<Song?> = flowOf(stored.firstOrNull { it.id == id })
        override suspend fun getSongsByIds(ids: List<String>): List<Song> = stored.filter { it.id in ids }
        override suspend fun getSongStubsByIds(ids: List<String>): List<Song> = getSongsByIds(ids)
        override suspend fun overwriteSong(song: Song) = saveSong(song)
        override fun searchSongs(query: String): Flow<List<Song>> = flowOf(stored)
        override suspend fun saveSong(song: Song) {
            stored.removeAll { it.id == song.id }
            stored += song.copy(contentHash = SongContentHasher.hash(song))
        }
        override suspend fun saveSongs(songs: List<Song>) { songs.forEach { saveSong(it) } }
        override suspend fun setFavorite(songId: String, isFavorite: Boolean) = Unit
        override suspend fun deleteSong(songId: String) = Unit
        override suspend fun findSongByContentHash(hash: String): Song? = stored.firstOrNull { it.contentHash == hash }
        override suspend fun findSongByContentSignature(song: Song): Song? = null
        override suspend fun getSongsMissingContentHash(): List<Song> = emptyList()
        override suspend fun updateContentHash(songId: String, hash: String) = Unit
        override suspend fun renameCategoryTag(oldName: String, newName: String): Int = 0
    }

    private class FakeCelebrationRepository : CelebrationRepository {
        var stored: Celebration? = null
        override fun getAllCelebrations(): Flow<List<Celebration>> = flowOf(emptyList())
        override fun getCelebrationById(id: String): Flow<Celebration?> = flowOf(stored)
        override suspend fun saveCelebration(celebration: Celebration) { stored = celebration }
        override suspend fun deleteCelebration(celebrationId: String) = Unit
        override suspend fun restoreCelebration(celebration: Celebration) { stored = celebration }
        override suspend fun renameMoment(oldName: String, newName: String): Int = 0
    }

    private class FakeCategoryRepository : CategoryRepository {
        private val all = mutableListOf<LiturgicalCategory>()
        override fun getAllCategories(): Flow<List<LiturgicalCategory>> = flowOf(all)
        override suspend fun getAllCategoriesSnapshot(): List<LiturgicalCategory> = all.toList()
        override suspend fun getCategoryById(id: String): LiturgicalCategory? = all.firstOrNull { it.id == id }
        override suspend fun saveCategory(category: LiturgicalCategory) { all += category }
        override suspend fun updateCategory(category: LiturgicalCategory) = Unit
        override suspend fun saveCategories(categories: List<LiturgicalCategory>): Int {
            val before = all.size
            all += categories
            return all.size - before
        }
        override suspend fun deleteCategory(categoryId: String) = Unit
        override suspend fun seedStandardCategoriesIfEmpty() = Unit
        override suspend fun ensureCategories(names: Collection<String>, kind: LiturgicalCategoryKind): List<String> = emptyList()
    }

    private class FakeAttachmentRepository : AttachmentRepository {
        val stored = mutableListOf<SongAttachment>()
        override fun getAttachments(songId: String): Flow<List<SongAttachment>> = flowOf(stored)
        override suspend fun getAttachmentsForSongs(songIds: List<String>): List<SongAttachment> =
            stored.filter { it.songId in songIds }
        override suspend fun addAttachment(attachment: SongAttachment) { stored += attachment }
        override suspend fun deleteAttachment(attachmentId: String) { stored.removeAll { it.id == attachmentId } }
        override suspend fun deleteAttachmentsForSong(songId: String) { stored.removeAll { it.songId == songId } }
        override suspend fun countForSong(songId: String): Int = stored.count { it.songId == songId }
        override fun observeSongIdsWithAttachments(): Flow<Set<String>> =
            flowOf(stored.map { it.songId }.toSet())
    }

    private class FakeRhythmPatternRepository : RhythmPatternRepository {
        val stored = mutableListOf<RhythmPattern>()
        override fun getAllPatterns(): Flow<List<RhythmPattern>> = flowOf(stored.toList())
        override fun getCustomPatterns(): Flow<List<RhythmPattern>> = flowOf(stored.toList())
        // Upsert por id, como hace Room con `OnConflictStrategy.REPLACE` en la tabla real: así
        // actualizar un patrón existente lo sustituye en vez de duplicarlo.
        override suspend fun savePattern(pattern: RhythmPattern) {
            stored.removeAll { it.id == pattern.id }
            stored += pattern
        }
        override suspend fun deletePattern(patternId: String) { stored.removeAll { it.id == patternId } }
        override suspend fun restorePattern(pattern: RhythmPattern) { stored += pattern }
    }

    /** Wiper falso: al vaciar, borra las canciones (para verificar el orden y el efecto de REPLACE). */
    private class FakeLibraryWiper(private val songs: FakeSongRepository) : LibraryWiper {
        var wiped = 0
        override suspend fun wipeAll() {
            wiped++
            songs.stored.clear()
        }
    }

    /** Proveedor de ubicación falso: anota la copia automática y permite recuperarla. */
    private class FakeBackupLocationProvider : BackupLocationProvider {
        var created: String? = null
        override suspend fun createAutoBackupTarget(fileName: String): String {
            created = fileName
            return "auto://$fileName"
        }

        override suspend fun latestAutoBackup(prefix: String): String? =
            created?.let { "auto://$it" }
    }
}
