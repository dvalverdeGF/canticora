package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.repository.TablatureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Pruebas de la **importación de una partitura de MuseScore (`.mscz`)** como tablatura del canto.
 *
 * Usa el `.mscz` real del arnés (`salmo2.mscz`): extrae su `*.mscx` y comprueba que la importación
 * **reemplaza** la tablatura previa del canto (no acumula copias).
 */
class ImportMsczTablatureUseCaseTest {

    private val repository = FakeTablatureRepository()
    private val useCase = ImportMsczTablatureUseCase(repository, generateId = { "imported" })

    private fun mscxOfSalmo2(): String? {
        val f = listOf(
            File("src/jvmTest/resources/salmo2.mscz"),
            File("core/domain/src/jvmTest/resources/salmo2.mscz")
        ).firstOrNull { it.exists() } ?: return null
        ZipInputStream(f.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name.endsWith(".mscx")) return zip.readBytes().toString(Charsets.UTF_8)
                e = zip.nextEntry
            }
        }
        return null
    }

    @Test
    fun `importa el salmo2 como tablatura del canto`() = runBlocking {
        val xml = mscxOfSalmo2()
        assumeTrue("falta resources/salmo2.mscz", xml != null)

        val result = useCase(mscxXml = xml!!, songId = "song-1", title = "Salmo 2")

        assertTrue("debe tener compases", result.measureCount > 0)
        assertTrue("debe tener notas", result.noteCount > 0)
        assertEquals(0, result.replaced)

        val stored = repository.stored.value
        assertEquals(1, stored.size)
        assertEquals("song-1", stored.single().songId)
        assertEquals("Salmo 2", stored.single().title)
    }

    @Test
    fun `reemplaza la tablatura previa del canto en vez de acumularla`() = runBlocking {
        val xml = mscxOfSalmo2()
        assumeTrue("falta resources/salmo2.mscz", xml != null)

        // Una tablatura que ya existía en el canto.
        repository.saveTablature(
            Tablature(
                id = "vieja", songId = "song-1", title = "Antigua",
                instrument = "Guitarra", tuning = com.grafitto.laudate.domain.model.tablature.Tuning.GUITAR,
                time = com.grafitto.laudate.domain.model.score.ScoreTimeSignature("4", 4)
            )
        )

        val result = useCase(mscxXml = xml!!, songId = "song-1", title = "Salmo 2")

        assertEquals("debe haber reemplazado 1", 1, result.replaced)
        val stored = repository.stored.value
        assertEquals("solo debe quedar la importada", 1, stored.size)
        assertEquals("imported", stored.single().id)
    }

    @Test
    fun `rechaza un xml vacio`() = runBlocking {
        val error = runCatching { useCase(mscxXml = "   ", songId = "song-1", title = "X") }.exceptionOrNull()
        assertTrue("debe rechazar un XML vacío", error is IllegalArgumentException)
    }

    @Test
    fun `rechaza un canto vacio`() = runBlocking {
        val error = runCatching {
            useCase(mscxXml = "<musescore></musescore>", songId = "", title = "X")
        }.exceptionOrNull()
        assertTrue("debe rechazar un canto vacío", error is IllegalArgumentException)
    }

    /** Repositorio de tablaturas en memoria. */
    private class FakeTablatureRepository : TablatureRepository {
        val stored = MutableStateFlow<List<Tablature>>(emptyList())

        override fun observeTablaturesForSong(songId: String): Flow<List<Tablature>> =
            flowOf(stored.value.filter { it.songId == songId })

        override suspend fun getTablaturesForSong(songId: String): List<Tablature> =
            stored.value.filter { it.songId == songId }

        override suspend fun getTablatureById(id: String): Tablature? =
            stored.value.firstOrNull { it.id == id }

        override suspend fun getAllTablatures(): List<Tablature> = stored.value

        override suspend fun saveTablature(tablature: Tablature) {
            stored.value = stored.value.filterNot { it.id == tablature.id } + tablature
        }

        override suspend fun deleteTablature(id: String) {
            stored.value = stored.value.filterNot { it.id == id }
        }

        override suspend fun deleteTablaturesForSong(songId: String) {
            stored.value = stored.value.filterNot { it.songId == songId }
        }

        override fun observeSongIdsWithTablatures(): Flow<Set<String>> =
            flowOf(stored.value.map { it.songId }.toSet())
    }
}
