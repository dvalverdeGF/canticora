package com.grafitto.laudate.domain.transfer

import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.celebration.CelebrationItem
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongSection
import com.grafitto.laudate.domain.model.song.SectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del **formato de intercambio de repertorios**: es el contrato que viaja entre
 * dispositivos, así que debe serializar, deserializar y rechazar lo ajeno con mensajes claros.
 */
class CelebrationBundleTest {

    private fun song(id: String, title: String, lyrics: String = "[C]Aleluya"): Song {
        val parsed = com.grafitto.laudate.domain.parser.ChordProParser.parse(
            chordProContent = "{title: $title}\n$lyrics",
            songId = id,
            fallbackTitle = title
        )
        return parsed.copy(number = "402", author = "Autor")
    }

    private fun celebration(vararg songIds: String): Celebration = Celebration(
        id = "c1",
        name = "Misa Dominical",
        celebrationType = "Misa",
        dateTimestamp = 1_700_000_000_000L,
        location = "Parroquia",
        items = songIds.mapIndexed { index, songId ->
            CelebrationItem(
                id = "item$index",
                celebrationId = "c1",
                songId = songId,
                liturgicalMoment = if (index == 0) "Entrada" else "Aleluya",
                displayOrder = index
            )
        }
    )

    @Test
    fun `el bundle conserva la cabecera de la celebración`() {
        val songs = mapOf("s1" to song("s1", "Aleluya"), "s2" to song("s2", "Gloria"))
        val bundle = CelebrationBundleSerializer.toBundle(celebration("s1", "s2"), songs, "1.0.1")
        assertEquals("Misa Dominical", bundle.celebration.name)
        assertEquals("Misa", bundle.celebration.type)
        assertEquals("Parroquia", bundle.celebration.location)
        assertEquals(2, bundle.celebration.items.size)
    }

    @Test
    fun `cada canto viaja como texto chordpro`() {
        val bundle = CelebrationBundleSerializer.toBundle(
            celebration("s1"),
            mapOf("s1" to song("s1", "Aleluya")),
            "1.0.1"
        )
        val bundled = bundle.songs.single()
        assertTrue(bundled.chordPro.contains("{title: Aleluya}"))
        assertTrue(bundled.chordPro.contains("[C]Aleluya"))
    }

    @Test
    fun `el bundle incluye la huella de contenido de cada canto`() {
        val original = song("s1", "Aleluya")
        val bundle = CelebrationBundleSerializer.toBundle(celebration("s1"), mapOf("s1" to original), "1.0.1")
        assertEquals(SongContentHasher.hash(original), bundle.songs.single().contentHash)
    }

    @Test
    fun `un canto repetido en el repertorio se guarda una sola vez`() {
        // El mismo Aleluya en dos momentos: dos elementos, un único canto en el archivo.
        val celebration = Celebration(
            id = "c1",
            name = "Misa",
            celebrationType = "Misa",
            dateTimestamp = 0,
            items = listOf(
                CelebrationItem("i1", "c1", "s1", "Entrada", 0),
                CelebrationItem("i2", "c1", "s1", "Comunión", 1)
            )
        )
        val bundle = CelebrationBundleSerializer.toBundle(celebration, mapOf("s1" to song("s1", "Aleluya")), "1.0.1")
        assertEquals(1, bundle.songs.size)
        assertEquals(2, bundle.celebration.items.size)
        assertEquals(bundle.celebration.items[0].songIndex, bundle.celebration.items[1].songIndex)
    }

    @Test
    fun `serializar y deserializar conserva el contenido`() {
        val bundle = CelebrationBundleSerializer.toBundle(
            celebration("s1", "s2"),
            mapOf("s1" to song("s1", "Aleluya"), "s2" to song("s2", "Gloria")),
            "1.0.1"
        )
        val roundTrip = CelebrationBundleSerializer.deserialize(CelebrationBundleSerializer.serialize(bundle))
        assertEquals(bundle.celebration.name, roundTrip.celebration.name)
        assertEquals(bundle.songs.map { it.title }, roundTrip.songs.map { it.title })
        assertEquals(bundle.songs.map { it.contentHash }, roundTrip.songs.map { it.contentHash })
        assertEquals(bundle.celebration.items.size, roundTrip.celebration.items.size)
    }

    @Test
    fun `los ajustes de la celebración viajan con el elemento`() {
        val celebration = Celebration(
            id = "c1",
            name = "Misa",
            celebrationType = "Misa",
            dateTimestamp = 0,
            items = listOf(CelebrationItem("i1", "c1", "s1", "Entrada", 0, overrideKey = "D", overrideCapo = 2))
        )
        val bundle = CelebrationBundleSerializer.toBundle(celebration, mapOf("s1" to song("s1", "Aleluya")), "1.0.1")
        assertEquals("D", bundle.celebration.items.single().overrideKey)
        assertEquals(2, bundle.celebration.items.single().overrideCapo)
    }

    @Test
    fun `reconoce un archivo propio`() {
        val json = CelebrationBundleSerializer.serialize(
            CelebrationBundleSerializer.toBundle(celebration("s1"), mapOf("s1" to song("s1", "X")), "1")
        )
        assertTrue(CelebrationBundleSerializer.looksLikeBundle(json))
    }

    @Test
    fun `las tablaturas del canto viajan en el repertorio`() {
        val tablature = com.grafitto.laudate.domain.model.tablature.Tablature(
            id = "t1",
            songId = "s1",
            title = "Riff",
            instrument = "Guitarra",
            tuning = com.grafitto.laudate.domain.model.tablature.Tuning.GUITAR,
            time = com.grafitto.laudate.domain.model.score.ScoreTimeSignature("4", 4)
        )
        val bundle = CelebrationBundleSerializer.toBundle(
            celebration = celebration("s1"),
            songsById = mapOf("s1" to song("s1", "Aleluya")),
            appVersion = "1.0.1",
            tablaturesBySongId = mapOf("s1" to listOf(tablature))
        )
        assertEquals(1, bundle.songs.single().tablatures.size)
        // Y sobreviven a la serialización del repertorio.
        val roundTrip = CelebrationBundleSerializer.deserialize(CelebrationBundleSerializer.serialize(bundle))
        assertEquals(1, roundTrip.songs.single().tablatures.size)
    }

    @Test
    fun `rechaza un JSON ajeno con mensaje claro`() {
        val error = runCatching { CelebrationBundleSerializer.deserialize("""{"otra":"cosa"}""") }
        assertTrue(error.isFailure)
        assertTrue(error.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `rechaza un formato desconocido`() {
        val json = """{"format":"otro.formato","formatVersion":1,"celebration":{"name":"X","type":"Misa","dateTimestamp":0}}"""
        val error = runCatching { CelebrationBundleSerializer.deserialize(json) }
        assertTrue(error.isFailure)
    }

    @Test
    fun `rechaza una versión de formato futura`() {
        val json = """{"format":"$CANTICORA_EVENT_FORMAT","formatVersion":99,"celebration":{"name":"X","type":"Misa","dateTimestamp":0}}"""
        val error = runCatching { CelebrationBundleSerializer.deserialize(json) }
        assertTrue(error.isFailure)
        assertTrue(error.exceptionOrNull()?.message?.contains("más reciente") == true)
    }

    @Test
    fun `los elementos sin canto se ignoran al construir el bundle`() {
        val celebration = Celebration(
            id = "c1",
            name = "Misa",
            celebrationType = "Misa",
            dateTimestamp = 0,
            items = listOf(CelebrationItem("i1", "c1", "fantasma", "Entrada", 0))
        )
        val bundle = CelebrationBundleSerializer.toBundle(celebration, emptyMap(), "1")
        assertTrue(bundle.songs.isEmpty())
        assertTrue(bundle.celebration.items.isEmpty())
    }
}
