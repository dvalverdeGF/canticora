package com.grafitto.laudate.domain.transfer

import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumArticulation
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.StrumRange
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmBundleSerializerTest {

    private fun pattern() = RhythmPattern(
        id = "p1",
        name = "Balada 4/4",
        timeSignature = TimeSignature.FOUR_FOUR,
        events = listOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.UP, StrumArticulation.ACCENT),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, range = StrumRange.BASS),
            RhythmEvent(RhythmFigure.QUARTER_REST)
        )
    )

    @Test
    fun roundTrip_preservesEveryField() {
        val json = RhythmBundleSerializer.encode(RhythmBundleSerializer.toBundle(listOf(pattern()), "1.0.7"))
        val decoded = RhythmBundleSerializer.decode(json)

        assertEquals(CANTICORA_RHYTHM_FORMAT, decoded.format)
        assertEquals(1, decoded.patterns.size)

        val restored = decoded.patterns.first().toDomain("new-id")
        assertEquals("Balada 4/4", restored.name)
        assertEquals(TimeSignature.FOUR_FOUR, restored.timeSignature)
        assertEquals(4, restored.events.size)

        // La dirección, la articulación y el registro sobreviven intactos.
        assertEquals(StrumDirection.UP, restored.events[1].direction)
        assertEquals(StrumArticulation.ACCENT, restored.events[1].articulation)
        assertTrue(restored.events[1].isAccent)
        assertEquals(StrumRange.BASS, restored.events[2].range)
        assertTrue(restored.events[3].figure.isRest)
    }

    @Test
    fun importedPattern_isNeverBuiltIn() {
        val json = RhythmBundleSerializer.encode(RhythmBundleSerializer.toBundle(listOf(pattern()), "1.0.7"))
        val restored = RhythmBundleSerializer.decode(json).patterns.first().toDomain("x")
        assertEquals(false, restored.isBuiltIn)
    }

    @Test
    fun unknownEnumValues_fallBackInsteadOfFailing() {
        // Un archivo de una versión futura no debe romper la importación.
        val json = """
            {
              "format": "canticora.rhythm",
              "patterns": [
                { "name": "Raro", "numerator": 4, "denominator": 4,
                  "events": [ { "figure": "QUARTER", "direction": "SIDEWAYS", "range": "ULTRA" } ] }
              ]
            }
        """.trimIndent()

        val restored = RhythmBundleSerializer.decode(json).patterns.first().toDomain("x")
        assertEquals(StrumDirection.DOWN, restored.events.first().direction)
        assertEquals(StrumRange.ALL, restored.events.first().range)
    }

    @Test
    fun unknownFigure_fallsBackToSixteenthAndKeepsTheEvent() {
        // Una figura desconocida (p. ej. de una versión futura) no debe descartar el evento:
        // se degrada a la figura más corta para no perder el golpe ni desalinear el compás.
        val json = """
            {
              "format": "canticora.rhythm",
              "patterns": [
                { "name": "Mixto", "numerator": 4, "denominator": 4,
                  "events": [ { "figure": "QUARTER" }, { "figure": "TRIPLET_QUARTER" } ] }
              ]
            }
        """.trimIndent()

        val restored = RhythmBundleSerializer.decode(json).patterns.first().toDomain("x")
        assertEquals(2, restored.events.size)
        assertEquals(RhythmFigure.QUARTER, restored.events[0].figure)
        assertEquals(RhythmFigure.SIXTEENTH, restored.events[1].figure)
    }

    @Test
    fun emptyName_getsAFallback() {
        val json = """
            { "format": "canticora.rhythm",
              "patterns": [ { "name": "  ", "numerator": 3, "denominator": 4, "events": [] } ] }
        """.trimIndent()

        val restored = RhythmBundleSerializer.decode(json).patterns.first().toDomain("x")
        assertEquals("Patrón importado", restored.name)
        assertEquals(TimeSignature.THREE_FOUR, restored.timeSignature)
    }

    @Test
    fun looksLikeRhythms_recognisesTheFormat() {
        val json = RhythmBundleSerializer.encode(RhythmBundleSerializer.toBundle(listOf(pattern()), "1"))
        assertTrue(RhythmBundleSerializer.looksLikeRhythms(json))
    }
}
