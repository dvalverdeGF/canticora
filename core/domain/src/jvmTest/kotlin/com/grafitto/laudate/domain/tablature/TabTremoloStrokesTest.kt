package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del **número de trazos del trémolo** (D-28), la paleta «Trémolos» de MuseScore
 * (`tremolo1`…`tremolo4`): un trazo es una corchea de repetición, cuatro una semifusa.
 */
class TabTremoloStrokesTest {

    private fun tablature(vararg plucks: TabPluck): Tablature = Tablature(
        id = "t",
        songId = "song-1",
        title = "T",
        instrument = Tuning.GUITAR.name,
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.QUARTER,
        measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(
                        strings = plucks.toList(),
                        rhythmicFigure = RhythmFigure.QUARTER
                    )
                )
            )
        )
    )

    private fun tremoloGlyph(tablature: Tablature): TabGlyph? =
        TablatureLayout.layout(tablature, TabLayoutSettings())
            .glyphs
            .firstOrNull { it.kind == TabGlyphKind.TREMOLO }

    // ── Modelo ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun elNumeroDeTrazosPorDefectoEsUno() {
        val pluck = TabPluck(TabPosition(1, 3))
        assertEquals(1, pluck.tremoloStrokes)
        assertEquals(1, pluck.strokes)
    }

    @Test
    fun losTrazosSeAcotanAlRangoDeMuseScore() {
        // MuseScore llega a cuatro trazos (`tremolo4`); por debajo, uno es el mínimo legible.
        assertEquals(1, TabPluck(TabPosition(1, 3), tremoloStrokes = 0).strokes)
        assertEquals(1, TabPluck(TabPosition(1, 3), tremoloStrokes = -7).strokes)
        assertEquals(4, TabPluck(TabPosition(1, 3), tremoloStrokes = 4).strokes)
        assertEquals(4, TabPluck(TabPosition(1, 3), tremoloStrokes = 9).strokes)
    }

    @Test
    fun elMaximoDeTrazosEsCuatro() {
        assertEquals(4, TabPluck.MAX_TREMOLO_STROKES)
    }

    // ── Edición ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun setTremoloStrokesEscribeElNumeroEnLaCuerdaIndicada() {
        val measures = TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true
        )
        val withNote = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        val next = TablatureEditing.setTremoloStrokes(withNote, 0, 0, 1, 3)
        assertEquals(3, next[0].slots[0].strings.first { it.stringNumber == 1 }.strokes)
    }

    @Test
    fun setTremoloStrokesAcotaElValorPedido() {
        val measures = TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true
        )
        val withNote = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        val next = TablatureEditing.setTremoloStrokes(withNote, 0, 0, 1, 99)
        assertEquals(TabPluck.MAX_TREMOLO_STROKES, next[0].slots[0].strings.first().strokes)
    }

    @Test
    fun setTremoloStrokesNoTocaLasDemasCuerdas() {
        val measures = TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true
        )
        val withNotes = TablatureEditing.setFret(
            TablatureEditing.setFret(measures, 0, 0, 1, 3), 0, 0, 2, 5
        )
        val next = TablatureEditing.setTremoloStrokes(withNotes, 0, 0, 1, 4)
        assertEquals(4, next[0].slots[0].strings.first { it.stringNumber == 1 }.strokes)
        assertEquals(1, next[0].slots[0].strings.first { it.stringNumber == 2 }.strokes)
    }

    @Test
    fun setTremoloStrokesNoEnciendeElTremolo() {
        // El número de trazos es una **magnitud**: encender la técnica es cosa de «Técnicas».
        val measures = TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true
        )
        val withNote = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        val next = TablatureEditing.setTremoloStrokes(withNote, 0, 0, 1, 2)
        assertTrue(next[0].slots[0].strings.first().techniques.isEmpty())
    }

    // ── Dibujo ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun elGlifoDelTremoloLlevaElNumeroDeTrazos() {
        val tablature = tablature(
            TabPluck(TabPosition(1, 3), techniques = setOf(TabTechnique.TREMOLO), tremoloStrokes = 3)
        )
        val glyph = tremoloGlyph(tablature)
        assertNotNull("el trémolo debe emitir un glifo", glyph)
        assertEquals(3, glyph!!.strokes)
    }

    @Test
    fun unTremoloDeUnTrazoYOtroDeCuatroSeDibujanDistinto() {
        val uno = tremoloGlyph(
            tablature(TabPluck(TabPosition(1, 3), techniques = setOf(TabTechnique.TREMOLO), tremoloStrokes = 1))
        )
        val cuatro = tremoloGlyph(
            tablature(TabPluck(TabPosition(1, 3), techniques = setOf(TabTechnique.TREMOLO), tremoloStrokes = 4))
        )
        assertEquals(1, uno!!.strokes)
        assertEquals(4, cuatro!!.strokes)
    }

    @Test
    fun elGlifoDelTremoloSeAcotaAunqueElModeloTraigaUnValorRaro() {
        // El modelo acota en `strokes`, así que el glifo nunca sale con 0 ni con 9 trazos.
        val tablature = tablature(
            TabPluck(TabPosition(1, 3), techniques = setOf(TabTechnique.TREMOLO), tremoloStrokes = 9)
        )
        assertEquals(TabPluck.MAX_TREMOLO_STROKES, tremoloGlyph(tablature)!!.strokes)
    }
}
