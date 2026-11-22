package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la maquetación textual de la tablatura (orden de dibujo y separadores). */
class TablatureTextLayoutTest {

    private fun tab(measures: List<TabMeasure>, tuning: Tuning = Tuning.GUITAR) = Tablature(
        id = "t",
        songId = "song-1",
        title = "T",
        instrument = tuning.name,
        tuning = tuning,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.QUARTER,
        measures = measures
    )

    @Test
    fun rendersOneLinePerString_thinnestOnTop() {
        val lines = TablatureTextLayout.render(tab(TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true)))
        assertEquals(6, lines.size)
        // La primera línea es la cuerda 1 (la más aguda), como se lee en cualquier tablatura.
        assertEquals(1, lines.first().stringNumber)
        assertEquals(6, lines.last().stringNumber)
    }

    @Test
    fun fretNumber_appearsOnTheRightString() {
        val measures = TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true)
        val withNote = TablatureEditing.togglePluck(measures, 0, 0, stringNumber = 1, fret = 3)
        val lines = TablatureTextLayout.render(tab(withNote))
        // La cuerda 1 (primera línea) lleva el traste 3.
        assertTrue(lines.first().body.contains("3"))
        // Las demás cuerdas no llevan cifra.
        assertTrue(lines.drop(1).none { it.body.contains("3") })
    }

    @Test
    fun emptySlot_rendersDash() {
        val lines = TablatureTextLayout.render(tab(TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true)))
        assertTrue(lines.first().body.contains("-"))
    }

    @Test
    fun heldSlot_rendersTilde() {
        val measures = TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true)
        val held = TablatureEditing.toggleHeld(measures, 0, 1)
        val lines = TablatureTextLayout.render(tab(held))
        assertTrue(lines.first().body.contains("~"))
    }

    @Test
    fun barSeparators_areDerived_fromMeasureCount() {
        val measures = TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 2, withGuideColumns = true)
        val lines = TablatureTextLayout.render(tab(measures))
        // Dos compases → tres separadores `|` (inicio, medio, fin).
        assertEquals(3, lines.first().body.count { it == '|' })
    }

    @Test
    fun tuningLabel_isAgudaToGrave() {
        assertEquals("EADGBE", TablatureTextLayout.tuningLabel(tab(emptyList())))
        assertEquals("EADG", TablatureTextLayout.tuningLabel(tab(emptyList(), Tuning.BASS_4)))
    }
}
