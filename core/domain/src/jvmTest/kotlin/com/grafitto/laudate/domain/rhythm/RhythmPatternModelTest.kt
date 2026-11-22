package com.grafitto.laudate.domain.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el principio fundamental:
 * UN PATRÓN = UN ÚNICO MODELO DE DATOS = MÚLTIPLES REPRESENTACIONES EDITABLES.
 */
class RhythmPatternModelTest {

    @Test
    fun figureAndDirectionAreIndependent() {
        // Cambiar la dirección no altera la figura ni la duración.
        val original = RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN)
        val changedDirection = original.copy(direction = StrumDirection.UP)

        assertEquals(RhythmFigure.EIGHTH, changedDirection.figure)
        assertEquals(original.beatValue, changedDirection.beatValue, 0.001)
        assertNotEquals(original.direction, changedDirection.direction)
    }

    @Test
    fun changingFigurePreservesDirection() {
        // Cambiar la figura no altera la dirección.
        val original = RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.UP)
        val changedFigure = original.copy(figure = RhythmFigure.SIXTEENTH)

        assertEquals(StrumDirection.UP, changedFigure.direction)
        assertNotEquals(original.beatValue, changedFigure.beatValue)
    }

    @Test
    fun restHasNoEffectiveStrumDirection() {
        val rest = RhythmEvent(RhythmFigure.QUARTER_REST, StrumDirection.DOWN)
        assertEquals(StrumDirection.NONE, rest.effectiveDirection)
        assertTrue(!rest.isStruck)
    }

    @Test
    fun sameFigureCanCarryDifferentDirections() {
        // Una misma figura puede tener DOWN, UP o MUTE.
        val down = RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN)
        val up = RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.UP)
        val mute = RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.MUTE)

        assertEquals(RhythmFigure.EIGHTH, down.figure)
        assertEquals(RhythmFigure.EIGHTH, up.figure)
        assertEquals(RhythmFigure.EIGHTH, mute.figure)
        assertEquals(3, listOf(down.direction, up.direction, mute.direction).distinct().size)
    }

    @Test
    fun aDirectionCanBeAssociatedWithDifferentFigures() {
        // DOWN puede ir con negra, corchea o semicorchea.
        val quarter = RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        val eighth = RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN)
        val sixteenth = RhythmEvent(RhythmFigure.SIXTEENTH, StrumDirection.DOWN)

        assertTrue(listOf(quarter, eighth, sixteenth).all { it.direction == StrumDirection.DOWN })
        assertEquals(3, listOf(quarter.beatValue, eighth.beatValue, sixteenth.beatValue).distinct().size)
    }

    @Test
    fun dottedFiguresHaveCorrectValues() {
        assertEquals(3.0, RhythmFigure.DOTTED_HALF.beatValue, 0.001)
        assertEquals(1.5, RhythmFigure.DOTTED_QUARTER.beatValue, 0.001)
        assertEquals(0.75, RhythmFigure.DOTTED_EIGHTH.beatValue, 0.001)
        assertTrue(RhythmFigure.DOTTED_QUARTER.isDotted)
        assertTrue(!RhythmFigure.QUARTER.isDotted)
    }

    @Test
    fun restAndNoteEquivalentsMatchByDuration() {
        assertEquals(RhythmFigure.QUARTER_REST, RhythmFigure.restEquivalentOf(RhythmFigure.QUARTER))
        assertEquals(RhythmFigure.QUARTER, RhythmFigure.noteEquivalentOf(RhythmFigure.QUARTER_REST))
        assertEquals(RhythmFigure.DOTTED_QUARTER_REST, RhythmFigure.restEquivalentOf(RhythmFigure.DOTTED_QUARTER))
    }
}

class RhythmPatternMetricGroupingTest {

    @Test
    fun fourFourGroupsByQuarterBeats() {
        // ↓ ↑ | ↓ ↑ | ↓ ↑ | ↓ ↑  (8 corcheas agrupadas de 2 en 2)
        val pattern = RhythmPattern(
            id = "p",
            name = "Corcheas 4/4",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = List(8) { RhythmEvent(RhythmFigure.EIGHTH) }
        )
        val groups = pattern.metricGroups()
        assertEquals(4, groups.size)
        assertTrue(groups.all { it.size == 2 })
    }

    @Test
    fun threeFourGroupsInThreeBeats() {
        val pattern = RhythmPattern(
            id = "p",
            name = "Vals",
            timeSignature = TimeSignature.THREE_FOUR,
            events = List(3) { RhythmEvent(RhythmFigure.QUARTER) }
        )
        assertEquals(3, pattern.metricGroups().size)
    }

    @Test
    fun sixEightGroupsByDottedQuarter() {
        // 6/8: 6 corcheas agrupadas en 2 pulsos de negra con puntillo (1.5 negras = 3 corcheas)
        val pattern = RhythmPattern(
            id = "p",
            name = "6/8",
            timeSignature = TimeSignature.SIX_EIGHT,
            events = List(6) { RhythmEvent(RhythmFigure.EIGHTH) }
        )
        val groups = pattern.metricGroups()
        assertEquals(2, groups.size)
        assertTrue(groups.all { it.size == 3 })
    }

    @Test
    fun separatorsAreDerivedNotStored() {
        // El modelo solo contiene eventos; los separadores son información de presentación.
        val pattern = RhythmPattern(
            id = "p",
            name = "Test",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = List(8) { RhythmEvent(RhythmFigure.EIGHTH) }
        )
        assertEquals(8, pattern.events.size)
        assertEquals(4, pattern.metricGroups().size)
        // Ningún evento contiene un carácter '|'
        assertTrue(pattern.events.none { it.figure.symbol.contains("|") })
    }

    @Test
    fun beatBoundaryIndicesAlignWithGroups() {
        val pattern = RhythmPattern(
            id = "p",
            name = "Test",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = List(8) { RhythmEvent(RhythmFigure.EIGHTH) }
        )
        assertEquals(setOf(0, 2, 4, 6), pattern.beatBoundaryIndices())
    }
}

class TimeSignatureBeatUnitTest {

    @Test
    fun simpleMetersUseQuarterBeat() {
        assertEquals(1.0, TimeSignature.FOUR_FOUR.beatUnitQuarterBeats, 0.001)
        assertEquals(1.0, TimeSignature.THREE_FOUR.beatUnitQuarterBeats, 0.001)
        assertEquals(1.0, TimeSignature.TWO_FOUR.beatUnitQuarterBeats, 0.001)
    }

    @Test
    fun compoundMetersUseDottedQuarterBeat() {
        assertEquals(1.5, TimeSignature.SIX_EIGHT.beatUnitQuarterBeats, 0.001)
        assertEquals(1.5, TimeSignature.NINE_EIGHT.beatUnitQuarterBeats, 0.001)
        assertEquals(1.5, TimeSignature.TWELVE_EIGHT.beatUnitQuarterBeats, 0.001)
    }

    @Test
    fun beatCountMatchesExpected() {
        assertEquals(4, TimeSignature.FOUR_FOUR.beatCount)
        assertEquals(3, TimeSignature.THREE_FOUR.beatCount)
        assertEquals(2, TimeSignature.SIX_EIGHT.beatCount)
        assertEquals(4, TimeSignature.TWELVE_EIGHT.beatCount)
    }
}
