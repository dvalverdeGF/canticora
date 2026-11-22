package com.grafitto.laudate.domain.transfer

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Pruebas del transporte de tablaturas en bundles de intercambio (ADR-017, Feature T). */
class BundleTablatureTest {

    private fun sample(): Tablature = Tablature(
        id = "tab-1",
        songId = "song-1",
        title = "Riff",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        measures = listOf(
            TabMeasure(slots = listOf(TabSlot(strings = listOf(TabPluck(6, 0))), TabSlot()))
        )
    )

    @Test
    fun roundTrip_preservesTablature() {
        val bundle = sample().toBundleTablature()
        assertEquals("tab-1", bundle.id)
        assertEquals("song-1", bundle.songId)
        assertEquals("Riff", bundle.name)
        assertEquals("Guitarra", bundle.instrument)

        val restored = bundle.toDomain()
        assertNotNull(restored)
        assertEquals("tab-1", restored!!.id)
        assertEquals("song-1", restored.songId)
        assertEquals("Riff", restored.title)
        assertEquals(1, restored.measures.size)
        assertEquals(0, restored.measures[0].slots[0].strings[0].fret)
    }

    @Test
    fun content_usesSameFormatAsTheSerializer() {
        // El contenido del bundle es exactamente el JSON que guarda la base de datos.
        val bundle = sample().toBundleTablature()
        assertEquals(TablatureSerializer.encode(sample()), bundle.content)
    }

    @Test
    fun corruptedContent_returnsNull() {
        val bundle = BundleTablature(id = "x", songId = "s", name = "N", instrument = "I", content = "no json")
        assertNull(bundle.toDomain())
    }
}
