package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.repository.CelebrationRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.transfer.CelebrationBundle
import com.grafitto.laudate.domain.transfer.BundleCelebration
import com.grafitto.laudate.domain.transfer.BundleItem
import com.grafitto.laudate.domain.transfer.BundleSong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la importación de repertorios compartidos.
 *
 * La regla crítica: **nunca sobrescribir una canción del receptor**. Si el canto ya existe, se
 * reutiliza su id; si no, se crea. El repertorio, además, siempre es uno nuevo.
 */
class ImportCelebrationBundleUseCaseTest {

    private val songRepository = FakeSongRepository()
    private val celebrationRepository = FakeCelebrationRepository()
    private var counter = 0
    private val useCase = ImportCelebrationBundleUseCase(
        songRepository = songRepository,
        celebrationRepository = celebrationRepository,
        generateId = { "id-${counter++}" }
    )

    private fun bundle(vararg songs: BundleSong): CelebrationBundle = CelebrationBundle(
        celebration = BundleCelebration(
            name = "Misa del director",
            type = "Misa",
            dateTimestamp = 0,
            items = songs.indices.map { BundleItem(songIndex = it, moment = "Entrada") }
        ),
        songs = songs.toList()
    )

    private fun bundleSong(title: String, lyrics: String = "[C]Aleluya"): BundleSong = BundleSong(
        title = title,
        originalKey = "C",
        chordPro = "{title: $title}\n$lyrics"
    )

    @Test
    fun `crea el repertorio con todos sus elementos`() = runBlocking {
        val result = useCase(bundle(bundleSong("Aleluya"), bundleSong("Gloria")))
        assertEquals(2, result.celebrationItemCount)
        assertNotNull(celebrationRepository.stored)
        assertEquals(2, celebrationRepository.stored!!.items.size)
    }

    @Test
    fun `crea los cantos que no existen`() = runBlocking {
        songRepository.songs.clear()
        val result = useCase(bundle(bundleSong("Aleluya"), bundleSong("Gloria")))
        assertEquals(2, result.songsCreated)
        assertEquals(0, result.songsReused)
        assertEquals(2, songRepository.songs.size)
    }

    @Test
    fun `reutiliza sin sobrescribir un canto que ya existe`() = runBlocking {
        // El receptor ya tiene este canto, con su propio id.
        val existing = storedSong(id = "mi-id", title = "Aleluya", lyrics = "[C]Aleluya")
        songRepository.songs.clear()
        songRepository.songs += existing

        val result = useCase(bundle(bundleSong("Aleluya")))

        assertEquals(0, result.songsCreated)
        assertEquals(1, result.songsReused)
        // El canto del receptor sigue siendo el mismo (no se ha reescrito ni duplicado).
        assertEquals(1, songRepository.songs.size)
        assertEquals("mi-id", songRepository.songs.single().id)
        assertEquals("mi-id", celebrationRepository.stored!!.items.single().songId)
    }

    @Test
    fun `mezcla cantos nuevos y existentes`() = runBlocking {
        songRepository.songs.clear()
        songRepository.songs += storedSong(id = "ya-existe", title = "Aleluya", lyrics = "[C]Aleluya")

        val result = useCase(
            bundle(
                bundleSong("Aleluya", lyrics = "[C]Aleluya"),
                bundleSong("Canto nuevo", lyrics = "[G]Nuevo")
            )
        )

        assertEquals(1, result.songsCreated)
        assertEquals(1, result.songsReused)
        assertEquals(2, songRepository.songs.size)
    }

    @Test
    fun `no sobrescribe aunque el texto difiera mínimamente del receptor`() = runBlocking {
        // El canto del receptor es distinto (otra letra): la huella no coincide y se crea aparte,
        // conservando las dos versiones sin perder la del receptor.
        songRepository.songs.clear()
        songRepository.songs += storedSong(id = "version-local", title = "Aleluya", lyrics = "[C]Otra letra")

        val result = useCase(bundle(bundleSong("Aleluya", lyrics = "[C]Aleluya")))

        assertEquals(1, result.songsCreated)
        assertEquals(0, result.songsReused)
        assertTrue(songRepository.songs.any { it.id == "version-local" })
    }

    @Test
    fun `reutiliza el mismo canto una sola vez si se repite en el repertorio`() = runBlocking {
        songRepository.songs.clear()
        val celebration = CelebrationBundle(
            celebration = BundleCelebration(
                name = "Misa",
                type = "Misa",
                dateTimestamp = 0,
                items = listOf(
                    BundleItem(songIndex = 0, moment = "Entrada"),
                    BundleItem(songIndex = 0, moment = "Comunión")
                )
            ),
            songs = listOf(bundleSong("Aleluya"))
        )
        val result = useCase(celebration)
        assertEquals(1, result.songsCreated)
        assertEquals(2, result.celebrationItemCount)
        assertEquals(1, songRepository.songs.size)
    }

    @Test
    fun `el repertorio conserva tipo lugar y descripción`() = runBlocking {
        val celebration = CelebrationBundle(
            celebration = BundleCelebration(
                name = "Retiro de Cuaresma",
                type = "Retiro",
                dateTimestamp = 1_234L,
                description = "En la parroquia",
                location = "Salón parroquial",
                items = listOf(BundleItem(songIndex = 0, moment = "Inicio"))
            ),
            songs = listOf(bundleSong("Ven Espíritu Santo"))
        )
        useCase(celebration)
        val stored = celebrationRepository.stored!!
        assertEquals("Retiro de Cuaresma", stored.name)
        assertEquals("Retiro", stored.celebrationType)
        assertEquals("Salón parroquial", stored.location)
        assertEquals("En la parroquia", stored.description)
        assertEquals(1_234L, stored.dateTimestamp)
    }

    @Test
    fun `los ajustes de tono y momento se conservan`() = runBlocking {
        val celebration = CelebrationBundle(
            celebration = BundleCelebration(
                name = "Misa",
                type = "Misa",
                dateTimestamp = 0,
                items = listOf(BundleItem(songIndex = 0, moment = "Aleluya", overrideKey = "D", overrideCapo = 2))
            ),
            songs = listOf(bundleSong("Aleluya"))
        )
        useCase(celebration)
        val item = celebrationRepository.stored!!.items.single()
        assertEquals("Aleluya", item.liturgicalMoment)
        assertEquals("D", item.overrideKey)
        assertEquals(2, item.overrideCapo)
    }

    @Test
    fun `un repertorio vacío se importa sin fallar`() = runBlocking {
        val result = useCase(bundle())
        assertEquals(0, result.songsCreated)
        assertEquals(0, result.celebrationItemCount)
        assertEquals(0, songRepository.songs.size)
    }

    /** Canción ya guardada en el dispositivo del receptor, con su huella calculada. */
    private fun storedSong(id: String, title: String, lyrics: String): Song {
        // Se parsea ChordPro real para que la huella se calcule sobre el mismo texto que el
        // canto importado (el serializador canónico trabaja sobre las líneas, no sobre rawTextContent).
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
        override fun getFavoriteSongs(): Flow<List<Song>> = flowOf(songs)
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

        override suspend fun saveSongs(songs: List<Song>) = songs.forEach { saveSong(it) }
        override suspend fun setFavorite(songId: String, isFavorite: Boolean) = Unit
        override suspend fun deleteSong(songId: String) = Unit
        override suspend fun findSongByContentHash(hash: String): Song? =
            songs.firstOrNull { it.contentHash == hash }

        override suspend fun findSongByContentSignature(song: Song): Song? = null

        override suspend fun getSongsMissingContentHash(): List<Song> =
            songs.filter { it.contentHash.isNullOrBlank() }

        override suspend fun updateContentHash(songId: String, hash: String) = Unit

        override suspend fun renameCategoryTag(oldName: String, newName: String): Int = 0
    }

    private class FakeCelebrationRepository : CelebrationRepository {
        var stored: Celebration? = null
        private val all = mutableListOf<Celebration>()

        override fun getAllCelebrations(): Flow<List<Celebration>> = flowOf(all)
        override fun getCelebrationById(id: String): Flow<Celebration?> = flowOf(all.firstOrNull { it.id == id })

        override suspend fun saveCelebration(celebration: Celebration) {
            stored = celebration
            all.removeAll { it.id == celebration.id }
            all += celebration
        }

        override suspend fun deleteCelebration(celebrationId: String) {
            all.removeAll { it.id == celebrationId }
        }

        override suspend fun restoreCelebration(celebration: Celebration) {
            all.removeAll { it.id == celebration.id }
            all += celebration
        }

        override suspend fun renameMoment(oldName: String, newName: String): Int = 0
    }
}
