package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pruebas de la detección de duplicados por huella de contenido.
 *
 * Escenario central: un canto editado se comparte y se vuelve a importar. Si el contenido coincide
 * con uno ya guardado, el caso de uso debe señalarlo para que la UI pregunte al usuario.
 */
class DetectDuplicateSongUseCaseTest {

    private val repository = FakeSongRepository()
    private val useCase = DetectDuplicateSongUseCase(repository)

    private fun song(id: String, title: String = "Aleluya", lyrics: String = "[C]Aleluya") =
        comTestSong(id = id, title = title, lyrics = lyrics)

    @Test
    fun `devuelve null cuando el catalogo está vacío`() = runBlocking {
        repository.songs.clear()
        assertNull(useCase(song("nueva")))
    }

    @Test
    fun `detecta coincidencia cuando el contenido ya existe con otro id`() = runBlocking {
        repository.songs.clear()
        repository.songs += song("existente")
        val candidate = song("otra-id")
        // Mismo contenido: la huella coincide aunque el id sea distinto.
        val match = useCase(candidate)
        assertNotNull(match)
        assertEquals("existente", match!!.existing.id)
        assertEquals("otra-id", match.candidate.id)
    }

    @Test
    fun `no considera duplicado editar la propia cancion`() = runBlocking {
        repository.songs.clear()
        val original = song("mismo-id")
        repository.songs += original
        assertNull(useCase(original.copy(title = "Aleluya")))
    }

    @Test
    fun `contenidos distintos no coinciden`() = runBlocking {
        repository.songs.clear()
        repository.songs += song("existente", lyrics = "[C]Aleluya")
        assertNull(useCase(song("nueva", lyrics = "[C]Otra letra")))
    }

    @Test
    fun `la coincidencia expone la huella compartida`() = runBlocking {
        repository.songs.clear()
        repository.songs += song("existente")
        val match = useCase(song("nueva"))!!
        assertEquals(SongContentHasher.hash(match.candidate), match.contentHash)
    }

    /** Construye una canción mínima con su huella ya calculada, como al guardarla. */
    private fun comTestSong(id: String, title: String, lyrics: String): Song {
        // Se parsea ChordPro real para que el canto tenga líneas y acordes: el serializador
        // canónico (y por tanto la huella) trabaja sobre `lines`, no sobre `rawTextContent`.
        val base = com.grafitto.laudate.domain.parser.ChordProParser.parse(
            chordProContent = "{title: $title}\n$lyrics",
            songId = id,
            fallbackTitle = title
        )
        return base.copy(contentHash = SongContentHasher.hash(base))
    }

    private class FakeSongRepository : SongRepository {
        val songs = mutableListOf<Song>()

        override fun getAllSongs(): Flow<List<Song>> = flowOf(songs)
        override fun getAllSongsWithLyrics(): Flow<List<Song>> = flowOf(songs)
        override suspend fun getSongCount(): Int = songs.size
        override fun getFavoriteSongs(): Flow<List<Song>> = flowOf(songs.filter { it.isFavorite })
        override fun getSongById(id: String): Flow<Song?> = flowOf(songs.firstOrNull { it.id == id })
        override suspend fun getSongsByIds(ids: List<String>): List<Song> =
            songs.filter { it.id in ids }

        override suspend fun getSongStubsByIds(ids: List<String>): List<Song> = getSongsByIds(ids)
        override suspend fun overwriteSong(song: Song) = saveSong(song)

        override fun searchSongs(query: String): Flow<List<Song>> = flowOf(songs)
        override suspend fun saveSong(song: Song) {
            songs.removeAll { it.id == song.id }
            songs += song.copy(contentHash = SongContentHasher.hash(song))
        }

        override suspend fun saveSongs(songs: List<Song>) {
            songs.forEach { saveSong(it) }
        }

        override suspend fun setFavorite(songId: String, isFavorite: Boolean) = Unit
        override suspend fun deleteSong(songId: String) = Unit
        override suspend fun findSongByContentHash(hash: String): Song? =
            songs.firstOrNull { it.contentHash == hash }

        override suspend fun findSongByContentSignature(song: Song): Song? = null

        override suspend fun getSongsMissingContentHash(): List<Song> =
            songs.filter { it.contentHash.isNullOrBlank() }

        override suspend fun updateContentHash(songId: String, hash: String) {
            val index = songs.indexOfFirst { it.id == songId }
            if (index >= 0) songs[index] = songs[index].copy(contentHash = hash)
        }

        override suspend fun renameCategoryTag(oldName: String, newName: String): Int = 0
    }
}
