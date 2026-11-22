package com.grafitto.laudate.domain.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.StrumRange
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmTextNotationTest {

    private fun pattern(vararg events: RhythmEvent, name: String = "Prueba") = RhythmPattern(
        id = "p",
        name = name,
        timeSignature = TimeSignature.FOUR_FOUR,
        events = events.toList()
    )

    @Test
    fun encode_producesHeaderMeasureAndStrokes() {
        val text = RhythmTextNotation.encode(
            pattern(
                RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN),
                RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.UP),
                RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
                RhythmEvent(RhythmFigure.QUARTER_REST),
                RhythmEvent(RhythmFigure.HALF, StrumDirection.DOWN)
            )
        )

        assertTrue(text.contains("Canticora · Ritmo «Prueba»"))
        assertTrue(text.contains("Compás 4/4"))
        // ↓ ↑ – ↓ · ↓ – (la negra y la blanca se escriben como golpe + prolongación).
        assertTrue(text.contains("↓"))
        assertTrue(text.contains("↑"))
        assertTrue(text.contains("|"))
    }

    @Test
    fun roundTrip_rebuildsTheSamePattern() {
        // 2 corcheas (1 negra) + 3 negras = 4 negras: el compás 4/4 completo.
        val original = pattern(
            RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.UP),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.UP)
        )

        val result = RhythmTextNotation.decode(RhythmTextNotation.encode(original))
        assertTrue(result is RhythmTextNotation.DecodeResult.Success)
        val restored = (result as RhythmTextNotation.DecodeResult.Success).pattern

        assertEquals(original.name, restored.name)
        assertEquals(original.timeSignature, restored.timeSignature)
        assertEquals(original.totalBeatValue, restored.totalBeatValue, 1e-6)

        val originalDirections = original.events.filter { it.isStruck }.map { it.direction }
        val restoredDirections = restored.events.filter { it.isStruck }.map { it.direction }
        assertEquals(originalDirections, restoredDirections)
    }

    @Test
    fun rangeMarks_surviveTheRoundTrip() {
        val original = pattern(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, range = StrumRange.BASS),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, range = StrumRange.TREBLE),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )

        val text = RhythmTextNotation.encode(original)
        assertTrue("El registro ha de quedar grabado en el texto", text.contains("↓g"))
        assertTrue(text.contains("↓a"))

        val result = RhythmTextNotation.decode(text) as RhythmTextNotation.DecodeResult.Success
        assertEquals(StrumRange.BASS, result.pattern.events.first().range)
    }

    @Test
    fun decode_acceptsTextWithoutHeader() {
        // Un ritmo pegado puede venir sin la cabecera; solo hacen falta compás y golpes.
        val pasted = """
            Compás 4/4
            ↓ ↑ ↓ ↑ ↓ ↑ ↓ ↑
        """.trimIndent()

        val result = RhythmTextNotation.decode(pasted)
        assertTrue(result is RhythmTextNotation.DecodeResult.Success)
        val restored = (result as RhythmTextNotation.DecodeResult.Success).pattern
        assertEquals(TimeSignature.FOUR_FOUR, restored.timeSignature)
        assertEquals("Ritmo importado", restored.name)
        assertEquals(4.0, restored.totalBeatValue, 1e-6)
    }

    @Test
    fun decode_failsWithoutMeasure() {
        val result = RhythmTextNotation.decode("↓ ↑ ↓ ↑")
        assertTrue(result is RhythmTextNotation.DecodeResult.Failure)
    }

    @Test
    fun looksLikeRhythmText_rejectsPlainText() {
        assertFalse(RhythmTextNotation.looksLikeRhythmText("Hola, ¿cómo estás?"))
        assertFalse(RhythmTextNotation.looksLikeRhythmText("Compás 4/4\nHola mundo"))
        assertTrue(RhythmTextNotation.looksLikeRhythmText("Compás 4/4\n↓ ↑ ↓ ↑"))
    }
}
