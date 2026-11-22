package com.grafitto.laudate.domain.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumArticulation
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmPlaybackSchedulerTest {

    private fun patternOf(vararg events: RhythmEvent, ts: TimeSignature = TimeSignature.FOUR_FOUR) =
        RhythmPattern(id = "test", name = "Test", timeSignature = ts, events = events.toList())

    @Test
    fun quarterNoteMillis_at80Bpm_is750() {
        assertEquals(750.0, RhythmPlaybackScheduler.quarterNoteMillis(80), 0.001)
    }

    @Test
    fun defaultBpm_is80() {
        assertEquals(80, RhythmPlaybackScheduler.DEFAULT_BPM)
    }

    @Test
    fun fourQuarterStrokes_areSpacedOneBeatApart() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )
        val schedule = RhythmPlaybackScheduler.build(pattern, bpm = 80)

        assertEquals(4, schedule.steps.size)
        assertEquals(listOf(0L, 750L, 1500L, 2250L), schedule.steps.map { it.offsetMillis })
        assertEquals(3000L, schedule.cycleDurationMillis)
    }

    @Test
    fun eighthNotes_areSpacedHalfBeatApart() {
        val pattern = patternOf(
            *(1..8).map { RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN) }.toTypedArray()
        )
        val schedule = RhythmPlaybackScheduler.build(pattern, bpm = 80)

        assertEquals(8, schedule.steps.size)
        // 8 corcheas → 375 ms entre golpes
        assertEquals(listOf(0L, 375L, 750L, 1125L, 1500L, 1875L, 2250L, 2625L), schedule.steps.map { it.offsetMillis })
    }

    @Test
    fun restsDoNotProduceStepsButAdvanceTime() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER_REST, StrumDirection.NONE),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )
        val schedule = RhythmPlaybackScheduler.build(pattern, bpm = 80)

        // Solo hay 3 golpes; el silencio sigue contando en el tiempo (índice 2 arranca a 1500).
        assertEquals(3, schedule.steps.size)
        assertEquals(listOf(0, 2, 3), schedule.steps.map { it.eventIndex })
        assertEquals(listOf(0L, 1500L, 2250L), schedule.steps.map { it.offsetMillis })
    }

    @Test
    fun emptyPattern_hasNoSteps() {
        val schedule = RhythmPlaybackScheduler.build(patternOf(), bpm = 80)
        assertTrue(schedule.isEmpty)
    }

    @Test
    fun bpmIsClampedToValidRange() {
        val pattern = patternOf(RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN))
        assertEquals(RhythmPlaybackScheduler.MAX_BPM, RhythmPlaybackScheduler.build(pattern, bpm = 999).bpm)
        assertEquals(RhythmPlaybackScheduler.MIN_BPM, RhythmPlaybackScheduler.build(pattern, bpm = 1).bpm)
    }

    @Test
    fun articulationIsPropagatedToSteps() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, StrumArticulation.ACCENT),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.UP, StrumArticulation.STACCATO),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, StrumArticulation.ARPEGGIO),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.MUTE, StrumArticulation.NONE)
        )
        val schedule = RhythmPlaybackScheduler.build(pattern, bpm = 80)

        assertTrue(schedule.steps[0].isAccent)
        assertTrue(schedule.steps[1].isStaccato)
        assertTrue(schedule.steps[2].isArpeggio)
        assertEquals(StrumDirection.MUTE, schedule.steps[3].direction)
    }

    @Test
    fun activeEventIndexAt_resolvesHighlight() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )
        val schedule = RhythmPlaybackScheduler.build(pattern, bpm = 80)

        assertEquals(0, schedule.activeEventIndexAt(0))
        assertEquals(0, schedule.activeEventIndexAt(749))
        assertEquals(1, schedule.activeEventIndexAt(750))
        assertEquals(3, schedule.activeEventIndexAt(2500))
    }

    @Test
    fun activeEventIndexAt_emptySchedule_returnsNull() {
        val schedule = RhythmPlaybackScheduler.build(patternOf(), bpm = 80)
        assertNull(schedule.activeEventIndexAt(1000))
    }

    @Test
    fun compoundMeter6_8_cycleDurationMatchesTimeSignature() {
        // 6/8 → 3 negras por compás; a 80 bpm cada negra = 750 ms → 2250 ms por compás.
        val events = (1..6).map { RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN) }
        val pattern = patternOf(*events.toTypedArray(), ts = TimeSignature.SIX_EIGHT)
        val schedule = RhythmPlaybackScheduler.build(pattern, bpm = 80)

        assertEquals(2250L, schedule.cycleDurationMillis)
        assertFalse(schedule.isEmpty)
    }
}
