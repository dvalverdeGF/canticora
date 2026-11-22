package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.backup.BackupArchiveStore
import com.grafitto.laudate.domain.backup.BackupAttachment
import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.parser.ChordProParser
import com.grafitto.laudate.domain.repository.AttachmentRepository
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.transfer.BundleTablature
import com.grafitto.laudate.domain.transfer.toBundleTablature
import com.grafitto.laudate.domain.transfer.ImportConflictPolicy
import com.grafitto.laudate.domain.transfer.SONG_DATA_ENTRY
import com.grafitto.laudate.domain.transfer.SongPackage
import com.grafitto.laudate.domain.transfer.SongPackageSerializer
import com.grafitto.laudate.domain.transfer.SongPackageSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la importación de un paquete de canción (`canticora.song`, ADR-017). */
class ImportSongPackageUseCaseTest {

    private val songs = FakeSongRepository()
    private val attachments = FakeAttachmentRepository()
    private val archive = FakeArchiveStore()
    private val categories = FakeCategoryRepository()
    private val tablatures = FakeTablatureRepository()
    private val useCase = ImportSongPackageUseCase(
        songRepository = songs,
        attachmentRepository = attachments,
        tablatureRepository = tablatures,
        archiveStore = archive,
        ensureCategoriesForImportUseCase = EnsureCategoriesForImportUseCase(categories)
    )

    private fun manifest(
        title: String = "Aleluya",
        chordPro: String = "{title: Aleluya}\n[G]Aleluya, alelu[D]ya",
        attachments: List<BackupAttachment> = emptyList(),
        moments: List<String> = emptyList(),
        tablatures: List<BundleTablature> = emptyList()
    ): SongPackage = SongPackage(
        exportedAt = 1L,
        appVersion = "1.0.24",
        song = SongPackageSong(
            title = title,
            author = "Kairoi",
            originalKey = "G",
            defaultLiturgicalMoments = moments,
            chordPro = chordPro
        ),
        attachments = attachments,
        tablatures = tablatures
    )

    @Test
    fun `importa un canto nuevo sin adjuntos`() = runBlocking {
        archive.manifest = SongPackageSerializer.encode(manifest())

        val result = useCase("content://paquete.canticora")

        assertEquals("Aleluya", result.title)
        assertTrue(result.created)
        assertEquals(1, songs.stored.size)
        assertEquals(0, result.attachmentsImported)
    }

    @Test
    fun `importa las tablaturas del paquete y las reasocia al canto de destino`() = runBlocking {
        archive.manifest = SongPackageSerializer.encode(
            manifest(tablatures = listOf(tablatura().toBundleTablature()))
        )

        val result = useCase("content://paquete.canticora")

        assertEquals(1, result.tablaturesImported)
        assertEquals(1, tablatures.stored.size)
        val importada = tablatures.stored.single()
        assertEquals("Aleluya", importada.title)
        assertEquals("debe colgar del canto creado", songs.stored.single().id, importada.songId)
        assertEquals(1, importada.measures.size)
    }

    @Test
    fun `un paquete sin tablaturas se sigue importando`() = runBlocking {
        // Compatibilidad: los paquetes creados antes de que existiera el campo no lo llevan.
        archive.manifest = SongPackageSerializer.encode(manifest())

        val result = useCase("content://paquete.canticora")

        assertEquals(0, result.tablaturesImported)
        assertTrue(tablatures.stored.isEmpty())
    }

    @Test
    fun `importa un canto con adjuntos de fichero y enlace`() = runBlocking {
        archive.manifest = SongPackageSerializer.encode(
            manifest(
                attachments = listOf(
                    BackupAttachment(
                        id = "a-file",
                        songId = "s1",
                        kind = "AUDIO",
                        displayName = "pista.mp3",
                        entryPath = "attachments/a-file"
                    ),
                    BackupAttachment(
                        id = "a-link",
                        songId = "s1",
                        kind = "LINK",
                        displayName = "Ensayo",
                        linkUrl = "https://youtube.com/x"
                    )
                )
            )
        )

        val result = useCase("content://paquete.canticora")

        assertEquals(2, result.attachmentsImported)
        assertEquals(2, attachments.stored.size)
        val file = attachments.stored.first { it.id == "a-file" }
        assertEquals("/privado/${songs.stored.single().id}/pista.mp3", file.uri)
        val link = attachments.stored.first { it.id == "a-link" }
        assertEquals("https://youtube.com/x", link.uri)
        assertTrue(link.isLink)
    }

    @Test
    fun `un canto identico no se duplica y no toca sus adjuntos`() = runBlocking {
        val existing = songOf("Aleluya")
        songs.saveSong(existing)
        // El manifiesto debe llevar exactamente el mismo ChordPro que la canción ya guardada.
        archive.manifest = SongPackageSerializer.encode(
            manifest(chordPro = com.grafitto.laudate.domain.serializer.ChordProSerializer.serialize(existing))
        )

        val result = useCase("content://paquete.canticora")

        assertTrue(result.identical)
        assertEquals(1, songs.stored.size)
        assertEquals(0, attachments.stored.size)
    }

    @Test
    fun `un conflicto con overwrite actualiza el canto existente`() = runBlocking {
        songs.saveSong(songOf("Aleluya", lyrics = "Letra vieja"))
        archive.manifest = SongPackageSerializer.encode(
            manifest(chordPro = "{title: Aleluya}\n[G]Letra corregida")
        )

        val result = useCase("content://paquete.canticora", ImportConflictPolicy.OVERWRITE)

        assertEquals(false, result.created)
        assertEquals(1, songs.stored.size)
        assertEquals("song-Aleluya", songs.stored.single().id)
        assertTrue(songs.stored.single().sections.single().lines.single().lyrics.contains("corregida"))
    }

    @Test
    fun `un conflicto con keep_both anade el canto como copia nueva`() = runBlocking {
        songs.saveSong(songOf("Aleluya", lyrics = "Letra vieja"))
        archive.manifest = SongPackageSerializer.encode(
            manifest(chordPro = "{title: Aleluya}\n[G]Letra corregida")
        )

        val result = useCase("content://paquete.canticora", ImportConflictPolicy.KEEP_BOTH)

        assertTrue(result.created)
        assertEquals(2, songs.stored.size)
        assertTrue(songs.stored.any { it.title == "Aleluya (importado)" })
    }

    @Test
    fun `un conflicto con skip conserva la version local`() = runBlocking {
        songs.saveSong(songOf("Aleluya", lyrics = "Letra vieja"))
        archive.manifest = SongPackageSerializer.encode(
            manifest(chordPro = "{title: Aleluya}\n[G]Letra corregida")
        )

        val result = useCase("content://paquete.canticora", ImportConflictPolicy.SKIP)

        assertTrue(result.identical)
        assertEquals(1, songs.stored.size)
        assertTrue(songs.stored.single().sections.single().lines.single().lyrics.contains("vieja"))
        assertEquals(0, attachments.stored.size)
    }

    @Test
    fun `el canto conserva los momentos liturgicos del paquete`() = runBlocking {
        archive.manifest = SongPackageSerializer.encode(
            manifest(moments = listOf("Aleluya / Aclamación"))
        )

        useCase("content://paquete.canticora")

        assertEquals(listOf("Aleluya / Aclamación"), songs.stored.single().defaultLiturgicalMoments)
    }

    @Test
    fun `inspect detecta que un canto ya existe con cambios`() = runBlocking {
        songs.saveSong(songOf("Aleluya", lyrics = "Letra vieja"))
        archive.manifest = SongPackageSerializer.encode(
            manifest(chordPro = "{title: Aleluya}\n[G]Letra corregida")
        )

        val preview = useCase.inspect("content://paquete.canticora")

        assertTrue(preview.hasConflict)
        assertEquals("Aleluya", preview.title)
    }

    @Test
    fun `inspect no marca conflicto para un canto nuevo`() = runBlocking {
        archive.manifest = SongPackageSerializer.encode(manifest())

        val preview = useCase.inspect("content://paquete.canticora")

        assertNull(preview.conflict)
        assertEquals(0, preview.attachmentCount)
    }

    @Test
    fun `un archivo sin manifiesto falla con un mensaje claro`() = runBlocking {
        archive.manifest = null
        val error = runCatching { useCase("content://vacio") }.exceptionOrNull()
        assertNotNull(error)
        assertTrue(error!!.message!!.contains("no es un canto"))
    }

    // ── Utilidades ─────────────────────────────────────────────────────────────────────────────

    private fun songOf(title: String, lyrics: String = title): Song = ChordProParser.parse(
        chordProContent = "{title: $title}\n[G]$lyrics",
        songId = "song-$title",
        fallbackTitle = title
    )

    /** Tablatura mínima del canto, para comprobar que viaja en el paquete. */
    private fun tablatura() = Tablature(
        id = "tab-1",
        songId = "s1",
        title = "Aleluya",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(
                        strings = listOf(TabPluck(position = TabPosition(6, 3))),
                        rhythmicFigure = com.grafitto.laudate.domain.model.rhythm.RhythmFigure.QUARTER
                    )
                )
            )
        )
    )

    private class FakeTablatureRepository : TablatureRepository {
        val stored = mutableListOf<Tablature>()
        override fun observeTablaturesForSong(songId: String): Flow<List<Tablature>> =
            flowOf(stored.filter { it.songId == songId })
        override suspend fun getTablaturesForSong(songId: String): List<Tablature> =
            stored.filter { it.songId == songId }
        override suspend fun getTablatureById(id: String): Tablature? =
            stored.firstOrNull { it.id == id }
        override suspend fun getAllTablatures(): List<Tablature> = stored.toList()
        override suspend fun saveTablature(tablature: Tablature) {
            stored.removeAll { it.id == tablature.id }
            stored += tablature
        }
        override suspend fun deleteTablature(id: String) { stored.removeAll { it.id == id } }
        override suspend fun deleteTablaturesForSong(songId: String) {
            stored.removeAll { it.songId == songId }
        }
        override fun observeSongIdsWithTablatures(): Flow<Set<String>> =
            flowOf(stored.map { it.songId }.toSet())
    }

    private class FakeArchiveStore : BackupArchiveStore {
        var manifest: String? = null
        override suspend fun writeArchive(
            destinationUri: String,
            manifestEntryName: String,
            manifestJson: String,
            attachments: List<SongAttachment>
        ) = Unit

        override suspend fun readEntry(archiveUri: String, entryName: String): String? =
            if (entryName == SONG_DATA_ENTRY) manifest else null

        override suspend fun readEntryEndingWith(archiveUri: String, suffix: String): String? =
            if (SONG_DATA_ENTRY.endsWith(suffix, ignoreCase = true)) manifest else null

        override suspend fun extractAttachment(
            archiveUri: String,
            entryPath: String,
            songId: String,
            displayName: String,
            mimeType: String?
        ): String = "/privado/$songId/$displayName"

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
        override suspend fun findSongByContentSignature(song: Song): Song? =
            stored.firstOrNull { it.title.equals(song.title, ignoreCase = true) }
        override suspend fun getSongsMissingContentHash(): List<Song> = emptyList()
        override suspend fun updateContentHash(songId: String, hash: String) = Unit
        override suspend fun renameCategoryTag(oldName: String, newName: String): Int = 0
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
        override suspend fun ensureCategories(
            names: Collection<String>,
            kind: LiturgicalCategoryKind
        ): List<String> = emptyList()
    }
}
