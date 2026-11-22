package com.grafitto.laudate.domain.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El sintetizador de audio es **puro de Dominio** y compartido por Android y escritorio: estas
 * pruebas garantizan que produce señal audible (no silencio) y que respeta la duración del ciclo.
 */
class RhythmPcmRendererTest {

    private fun pattern(vararg directions: StrumDirection) = RhythmPattern(
        id = "test",
        name = "Test",
        timeSignature = TimeSignature.FOUR_FOUR,
        events = directions.map { RhythmEvent(RhythmFigure.QUARTER, it) }
    )

    @Test
    fun `un patron con golpes produce senal audible`() {
        val schedule = RhythmPlaybackScheduler.build(
            pattern(StrumDirection.DOWN, StrumDirection.UP, StrumDirection.DOWN, StrumDirection.UP),
            bpm = 80
        )
        val pcm = RhythmPcmRenderer.renderCycle(schedule, sampleRate = 48_000)

        assertEquals(144_000, pcm.size) // 3 s a 48 kHz
        assertTrue("El buffer debe contener señal", pcm.any { it != 0.toShort() })
    }

    @Test
    fun `un patron solo de silencios no genera pasos`() {
        val schedule = RhythmPlaybackScheduler.build(
            pattern(StrumDirection.NONE, StrumDirection.NONE, StrumDirection.NONE, StrumDirection.NONE),
            bpm = 80
        )
        assertTrue(schedule.isEmpty)
    }

    @Test
    fun `el render es determinista para la misma semilla`() {
        val schedule = RhythmPlaybackScheduler.build(pattern(StrumDirection.DOWN), bpm = 80)
        val a = RhythmPcmRenderer.renderCycle(schedule, 48_000, seed = 42L)
        val b = RhythmPcmRenderer.renderCycle(schedule, 48_000, seed = 42L)
        assertTrue(a.contentEquals(b))
    }
}
