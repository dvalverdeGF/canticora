package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.transfer.ImportConflictPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la **importación de canciones sueltas en ChordPro** desde la pantalla «Importar».
 *
 * Se verifica que:
 *  - el título sale de `{title:}` si existe y, si no, del **nombre del fichero**;
 *  - el autor y el tono salen de las directivas (`{artist:}`, `{key:}`);
 *  - se reconocen las categorías citadas por el canto;
 *  - un canto con la misma huella de contenido **no se duplica**.
 */
class ImportChordProSongsUseCaseTest {

    private fun useCase(songs: SongRepository, categories: CategoryRepository) =
        ImportChordProSongsUseCase(
            songRepository = songs,
            ensureCategoriesForImportUseCase = EnsureCategoriesForImportUseCase(categories),
            generateId = { "generated-${songs.let { _ -> idCounter++ }}" },
            computeDispatcher = Dispatchers.Unconfined
        )

    private var idCounter = 0

    @Test
    fun usesTitleAndArtistFromDirectives() = runTest {
        val songs = FakeSongRepository()
        val chordPro = """
            {title: Alabaré a mi Señor}
            {artist: Manuel José Alonso}
            {key: G}
            {comment: Entrada}
            {sov}
            [G]Alabaré, a[C]labaré
            {eov}
        """.trimIndent()

        val result = useCase(songs, FakeCategoryRepository()).invoke(
            listOf(ChordProFile(content = chordPro, fileName = "alabare.cho"))
        )

        assertEquals(1, result.songsCreated)
        val song = songs.songs.single()
        assertEquals("Alabaré a mi Señor", song.title)
        assertEquals("Manuel José Alonso", song.author)
        assertEquals("G", song.originalKey)
    }

    @Test
    fun fallsBackToFileNameWhenTitleDirectiveIsMissing() = runTest {
        val songs = FakeSongRepository()
        val chordPro = """
            {key: D}
            {sov}
            [D]Cantad al Señor
            {eov}
        """.trimIndent()

        useCase(songs, FakeCategoryRepository()).invoke(
            listOf(ChordProFile(content = chordPro, fileName = "cantad_al_senor.chordpro"))
        )

        assertEquals("cantad_al_senor", songs.songs.single().title)
    }

    @Test
    fun importsMultipleFilesAtOnce() = runTest {
        val songs = FakeSongRepository()
        val files = listOf(
            ChordProFile("{title: Uno}\n{sov}\n[C]Uno\n{eov}", "uno.cho"),
            ChordProFile("{title: Dos}\n{sov}\n[G]Dos\n{eov}", "dos.cho"),
            ChordProFile("{title: Tres}\n{sov}\n[D]Tres\n{eov}", "tres.cho")
        )

        val result = useCase(songs, FakeCategoryRepository()).invoke(files)

        assertEquals(3, result.songsCreated)
        assertEquals(3, songs.songs.size)
    }

    @Test
    fun doesNotDuplicateSongThatAlreadyExists() = runTest {
        val songs = FakeSongRepository()
        val chordPro = "{title: Alabaré}\n{artist: Autor}\n{sov}\n[G]Alabaré\n{eov}"
        val uc = useCase(songs, FakeCategoryRepository())

        val first = uc(listOf(ChordProFile(chordPro, "alabare.cho")))
        val second = uc(listOf(ChordProFile(chordPro, "alabare.cho")))

        assertEquals(1, first.songsCreated)
        assertEquals(0, second.songsCreated)
        assertEquals(1, second.songsReused)
        // Sigue habiendo un solo canto.
        assertEquals(1, songs.songs.size)
    }

    @Test
    fun blankFilesAreIgnored() = runTest {        val songs = FakeSongRepository()
        val result = useCase(songs, FakeCategoryRepository()).invoke(
            listOf(ChordProFile(content = "   ", fileName = "vacio.cho"))
        )

        assertEquals(0, result.total)
        assertTrue(songs.songs.isEmpty())
    }

    @Test
    fun inspect_detectaConflictoCuandoElCantoExisteConCambios() = runTest {
        val songs = FakeSongRepository()
        val original = "{title: Alabaré}\n{artist: Autor}\n{sov}\n[G]Alabaré\n{eov}"
        songs.saveSong(parse(original, "s1"))
        val edited = "{title: Alabaré}\n{artist: Autor}\n{sov}\n[G]Alabaré al Señor\n{eov}"

        val preview = useCase(songs, FakeCategoryRepository())
            .inspect(listOf(ChordProFile(edited, "alabare.cho")))

        assertEquals(1, preview.conflicts.size)
        assertEquals("Alabaré", preview.conflicts.single().title)
    }

    @Test
    fun sobrescribeCantoExistenteConPermisoPorDefecto() = runTest {
        val songs = FakeSongRepository()
        val original = "{title: Alabaré}\n{artist: Autor}\n{sov}\n[G]Alabaré\n{eov}"
        songs.saveSong(parse(original, "s1"))
        val edited = "{title: Alabaré}\n{artist: Autor}\n{sov}\n[G]Alabaré al Señor\n{eov}"

        val result = useCase(songs, FakeCategoryRepository()).invoke(listOf(ChordProFile(edited, "alabare.cho")))

        assertEquals(1, result.songsOverwritten)
        assertEquals(1, songs.songs.size)
        // Conserva la identidad (mismo id) pero con el contenido nuevo.
        assertEquals("s1", songs.songs.single().id)
        assertTrue(songs.songs.single().sections.single().lines.single().lyrics.contains("al Señor"))
    }

    @Test
    fun conservarAmbosCreaUnCantoNuevoConPermiso() = runTest {
        val songs = FakeSongRepository()
        val original = "{title: Alabaré}\n{artist: Autor}\n{sov}\n[G]Alabaré\n{eov}"
        songs.saveSong(parse(original, "s1"))
        val edited = "{title: Alabaré}\n{artist: Autor}\n{sov}\n[G]Alabaré al Señor\n{eov}"
        val editedHash = SongContentHasher.hash(parse(edited, "tmp"))

        val result = useCase(songs, FakeCategoryRepository()).invoke(
            listOf(ChordProFile(edited, "alabare.cho")),
            mapOf(editedHash to ImportConflictPolicy.KEEP_BOTH)
        )

        assertEquals(1, result.songsCreated)
        assertEquals(2, songs.songs.size)
    }

    private fun parse(chordPro: String, id: String) =
        com.grafitto.laudate.domain.parser.ChordProParser.parse(
            chordProContent = chordPro, songId = id, fallbackTitle = "Alabaré"
        )

    // ── Apoyo ───────────────────────────────────────────────────────────────────

    private class FakeSongRepository : SongRepository {
        val songs = mutableListOf<Song>()

        override fun getAllSongs(): Flow<List<Song>> = flowOf(songs.toList())
        override fun getAllSongsWithLyrics(): Flow<List<Song>> = flowOf(songs.toList())
        override suspend fun getSongCount(): Int = songs.size
        override fun getFavoriteSongs(): Flow<List<Song>> = flowOf(songs.filter { it.isFavorite })
        override fun getSongById(id: String): Flow<Song?> = flowOf(songs.firstOrNull { it.id == id })
        override suspend fun getSongsByIds(ids: List<String>): List<Song> = songs.filter { it.id in ids }
        override suspend fun getSongStubsByIds(ids: List<String>): List<Song> = getSongsByIds(ids)
        override fun searchSongs(query: String): Flow<List<Song>> =
            flowOf(songs.filter { it.title.contains(query, ignoreCase = true) })

        override suspend fun saveSong(song: Song) {
            songs.removeAll { it.id == song.id }
            // Recalcula la huella como haría la capa de datos al guardar.
            songs.add(
                song.copy(
                    contentHash = com.grafitto.laudate.domain.hashing.SongContentHasher.hash(song)
                )
            )
        }

        override suspend fun saveSongs(songs: List<Song>) = songs.forEach { saveSong(it) }
        override suspend fun setFavorite(songId: String, isFavorite: Boolean) {
            val index = songs.indexOfFirst { it.id == songId }
            if (index >= 0) songs[index] = songs[index].copy(isFavorite = isFavorite)
        }

        override suspend fun deleteSong(songId: String) {
            songs.removeAll { it.id == songId }
        }

        override suspend fun findSongByContentHash(hash: String): Song? =
            songs.firstOrNull { it.contentHash == hash }

        override suspend fun findSongByContentSignature(song: Song): Song? =
            songs.firstOrNull { it.title.equals(song.title, ignoreCase = true) }

        override suspend fun overwriteSong(song: Song) {
            val index = songs.indexOfFirst { it.id == song.id }
            if (index >= 0) songs[index] = song.copy(contentHash = SongContentHasher.hash(song))
        }

        override suspend fun getSongsMissingContentHash(): List<Song> =
            songs.filter { it.contentHash.isNullOrBlank() }

        override suspend fun updateContentHash(songId: String, hash: String) {
            val index = songs.indexOfFirst { it.id == songId }
            if (index >= 0) songs[index] = songs[index].copy(contentHash = hash)
        }

        override suspend fun renameCategoryTag(oldName: String, newName: String): Int = 0
    }

    private class FakeCategoryRepository : CategoryRepository {
        val categories = mutableListOf<com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory>()

        override fun getAllCategories(): Flow<List<com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory>> =
            flowOf(categories.toList())

        override suspend fun getAllCategoriesSnapshot() = categories.toList()
        override suspend fun getCategoryById(id: String) = categories.firstOrNull { it.id == id }
        override suspend fun saveCategory(category: com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory) {
            saveCategories(listOf(category))
        }

        override suspend fun updateCategory(category: com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory) = Unit

        override suspend fun saveCategories(
            categories: List<com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory>
        ): Int {
            val existing = this.categories.map { it.name.lowercase() }.toMutableSet()
            var created = 0
            categories.forEach { if (existing.add(it.name.lowercase())) { this.categories.add(it); created++ } }
            return created
        }

        override suspend fun deleteCategory(categoryId: String) {
            categories.removeAll { it.id == categoryId }
        }

        override suspend fun seedStandardCategoriesIfEmpty() = Unit

        override suspend fun ensureCategories(
            names: Collection<String>,
            kind: LiturgicalCategoryKind
        ): List<String> {
            names.forEach { name ->
                if (categories.none { it.name.equals(name, ignoreCase = true) }) {
                    categories.add(
                        com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory(
                            id = "cat-${categories.size}",
                            name = name,
                            kind = kind
                        )
                    )
                }
            }
            return names.toList()
        }
    }
}
