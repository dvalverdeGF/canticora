package com.grafitto.laudate.domain.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuitarPatternLibraryTest {

    @Test
    fun allBuiltInPatterns_fillTheirTimeSignature() {
        // Invariante: todo patrón integrado completa exactamente su compás
        GuitarPatternLibrary.builtInPatterns.forEach { pattern ->
            assertTrue(
                "El patrón '${pattern.name}' (${pattern.timeSignature}) no completa el compás: " +
                    "total ${pattern.totalBeatValue} vs ${pattern.timeSignature.totalQuarterBeats}",
                pattern.isValid
            )
        }
    }

    @Test
    fun allBuiltInPatterns_areMarkedAsBuiltIn() {
        assertTrue(GuitarPatternLibrary.builtInPatterns.all { it.isBuiltIn })
    }

    @Test
    fun libraryCoversCommonTimeSignatures() {
        val covered = GuitarPatternLibrary.builtInPatterns.map { it.timeSignature }.toSet()
        assertTrue(covered.contains(TimeSignature.TWO_FOUR))
        assertTrue(covered.contains(TimeSignature.THREE_FOUR))
        assertTrue(covered.contains(TimeSignature.FOUR_FOUR))
        assertTrue(covered.contains(TimeSignature.SIX_EIGHT))
    }

    @Test
    fun patternsFor_filtersByTimeSignature() {
        val fourFour = GuitarPatternLibrary.patternsFor(TimeSignature.FOUR_FOUR)
        assertTrue(fourFour.isNotEmpty())
        assertTrue(fourFour.all { it.timeSignature == TimeSignature.FOUR_FOUR })
    }

    @Test
    fun fourFourHasEnoughVariety() {
        // Debe haber una variedad razonable de patrones típicos en 4/4 (pop, rock, balada...)
        val fourFour = GuitarPatternLibrary.patternsFor(TimeSignature.FOUR_FOUR)
        assertTrue("Se esperaban al menos 8 patrones en 4/4, hay ${fourFour.size}", fourFour.size >= 8)
    }
}

class StrummingAutomationTest {

    @Test
    fun quarterNotes_areAllDown() {
        val events = listOf(
            RhythmEvent(RhythmFigure.QUARTER),
            RhythmEvent(RhythmFigure.QUARTER),
            RhythmEvent(RhythmFigure.QUARTER),
            RhythmEvent(RhythmFigure.QUARTER)
        )
        val result = StrummingAutomation.apply(events)
        assertTrue(result.all { it.direction == StrumDirection.DOWN })
    }

    @Test
    fun eighthNotes_alternateDownUp() {
        val events = List(8) { RhythmEvent(RhythmFigure.EIGHTH) }
        val result = StrummingAutomation.apply(events)
        val expected = listOf(
            StrumDirection.DOWN, StrumDirection.UP,
            StrumDirection.DOWN, StrumDirection.UP,
            StrumDirection.DOWN, StrumDirection.UP,
            StrumDirection.DOWN, StrumDirection.UP
        )
        assertEquals(expected, result.map { it.direction })
    }

    @Test
    fun sixteenthNotes_alternateAndResetOnEachBeat() {
        val events = List(8) { RhythmEvent(RhythmFigure.SIXTEENTH) }
        val result = StrummingAutomation.apply(events)
        // 4 semicorcheas por pulso: ↓ ↑ ↓ ↑ y reinicia en el siguiente pulso
        val expected = listOf(
            StrumDirection.DOWN, StrumDirection.UP, StrumDirection.DOWN, StrumDirection.UP,
            StrumDirection.DOWN, StrumDirection.UP, StrumDirection.DOWN, StrumDirection.UP
        )
        assertEquals(expected, result.map { it.direction })
    }

    @Test
    fun restResetsPendulumToDown() {
        // Corchea ↓, corchea ↑, silencio de corchea, corchea → debe volver a ↓
        val events = listOf(
            RhythmEvent(RhythmFigure.EIGHTH),
            RhythmEvent(RhythmFigure.EIGHTH),
            RhythmEvent(RhythmFigure.EIGHTH_REST),
            RhythmEvent(RhythmFigure.EIGHTH)
        )
        val result = StrummingAutomation.apply(events)
        assertEquals(StrumDirection.DOWN, result[0].direction)
        assertEquals(StrumDirection.UP, result[1].direction)
        assertEquals(StrumDirection.NONE, result[2].direction)
        assertEquals("Tras un silencio el péndulo debe reiniciar a ↓", StrumDirection.DOWN, result[3].direction)
    }

    @Test
    fun muteDirectionIsPreserved() {
        val events = listOf(
            RhythmEvent(RhythmFigure.QUARTER),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.MUTE),
            RhythmEvent(RhythmFigure.QUARTER)
        )
        val result = StrummingAutomation.apply(events)
        assertEquals(StrumDirection.MUTE, result[1].direction)
    }

    @Test
    fun nextDirection_alternatesWithEighths() {
        val twoEighths = listOf(
            RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.UP)
        )
        // Posición 1.0 negra = inicio del pulso → ↓
        assertEquals(StrumDirection.DOWN, StrummingAutomation.nextDirection(twoEighths))

        val oneEighth = listOf(RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN))
        // Posición 0.5 negra = mitad del pulso → ↑
        assertEquals(StrumDirection.UP, StrummingAutomation.nextDirection(oneEighth))
    }

    @Test
    fun nextDirection_afterRestIsDown() {
        val events = listOf(RhythmEvent(RhythmFigure.EIGHTH_REST))
        assertEquals(StrumDirection.DOWN, StrummingAutomation.nextDirection(events))
    }
}

class TimeSignatureTest {

    @Test
    fun totalQuarterBeats_computesCorrectly() {
        assertEquals(2.0, TimeSignature.TWO_FOUR.totalQuarterBeats, 0.001)
        assertEquals(3.0, TimeSignature.THREE_FOUR.totalQuarterBeats, 0.001)
        assertEquals(4.0, TimeSignature.FOUR_FOUR.totalQuarterBeats, 0.001)
        assertEquals(3.0, TimeSignature.SIX_EIGHT.totalQuarterBeats, 0.001)
        assertEquals(6.0, TimeSignature.TWELVE_EIGHT.totalQuarterBeats, 0.001)
    }

    @Test
    fun parse_interpretsValidText() {
        assertEquals(TimeSignature.FOUR_FOUR, TimeSignature.parse("4/4"))
        assertEquals(TimeSignature.SIX_EIGHT, TimeSignature.parse("6/8"))
        assertEquals(TimeSignature.THREE_FOUR, TimeSignature.parse(" 3 / 4 "))
    }

    @Test
    fun parse_returnsNullForInvalidText() {
        assertEquals(null, TimeSignature.parse("4"))
        assertEquals(null, TimeSignature.parse("x/4"))
        assertEquals(null, TimeSignature.parse("0/4"))
    }

    @Test
    fun fromNameOrDefault_fallsBackToFourFour() {
        assertEquals(TimeSignature.FOUR_FOUR, TimeSignature.fromNameOrDefault("indefinido"))
        assertEquals(TimeSignature.THREE_FOUR, TimeSignature.fromNameOrDefault("3/4"))
    }
}

class RhythmPatternTest {

    @Test
    fun isValid_trueWhenEventsFillTheBar() {
        val pattern = RhythmPattern(
            id = "p1",
            name = "Test",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(
                RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
                RhythmEvent(RhythmFigure.QUARTER, StrumDirection.UP),
                RhythmEvent(RhythmFigure.HALF, StrumDirection.DOWN)
            )
        )
        assertTrue(pattern.isValid)
        assertEquals(0.0, pattern.remainingBeatValue, 0.001)
    }

    @Test
    fun isValid_falseWhenEventsUnderfill() {
        val pattern = RhythmPattern(
            id = "p2",
            name = "Incompleto",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN))
        )
        assertFalse(pattern.isValid)
        assertEquals(3.0, pattern.remainingBeatValue, 0.001)
    }

    @Test
    fun isValid_falseWhenEmpty() {
        val pattern = RhythmPattern(id = "p3", name = "Vacío", timeSignature = TimeSignature.THREE_FOUR)
        assertFalse(pattern.isValid)
    }

    @Test
    fun isValid_handlesEighthNoteGrid() {
        val pattern = RhythmPattern(
            id = "p4",
            name = "Corcheas 6/8",
            timeSignature = TimeSignature.SIX_EIGHT,
            events = List(6) { RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN) }
        )
        assertTrue(pattern.isValid)
    }
}

class RhythmFigureTest {

    @Test
    fun beatValues_areCorrect() {
        assertEquals(4.0, RhythmFigure.WHOLE.beatValue, 0.001)
        assertEquals(2.0, RhythmFigure.HALF.beatValue, 0.001)
        assertEquals(1.0, RhythmFigure.QUARTER.beatValue, 0.001)
        assertEquals(0.5, RhythmFigure.EIGHTH.beatValue, 0.001)
        assertEquals(0.25, RhythmFigure.SIXTEENTH.beatValue, 0.001)
    }

    @Test
    fun isRest_distinguishesRestsFromNotes() {
        assertTrue(RhythmFigure.QUARTER_REST.isRest)
        assertFalse(RhythmFigure.QUARTER.isRest)
    }
}
