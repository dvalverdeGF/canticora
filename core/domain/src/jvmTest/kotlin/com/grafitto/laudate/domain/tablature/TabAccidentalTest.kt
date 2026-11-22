package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabAccidental
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabOctaveShift
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** **T4 · Alteraciones**: la alteración se escribe junto a la cifra, se serializa y se maqueta. */
class TabAccidentalTest {

    private fun oneNote(): List<TabMeasure> =
        TablatureEditing.setFret(
            TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.EIGHTH, 1),
            0, 0, 1, 3
        )

    private fun tablature(measures: List<TabMeasure>) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.EIGHTH, measures = measures
    )

    @Test
    fun setAccidental_fijaYQuita() {
        val conAlteracion = TablatureEditing.setAccidental(oneNote(), 0, 0, 1, TabAccidental.SHARP)
        assertEquals(TabAccidental.SHARP, conAlteracion[0].slots[0].strings[0].accidental)
        val limpia = TablatureEditing.setAccidental(conAlteracion, 0, 0, 1, null)
        assertNull(limpia[0].slots[0].strings[0].accidental)
    }

    @Test
    fun laAlteracionSobreviveAlGuardado() {
        val measures = TablatureEditing.setAccidental(oneNote(), 0, 0, 1, TabAccidental.FLAT)
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tablature(measures)))
        assertEquals(TabAccidental.FLAT, restored.measures[0].slots[0].strings[0].accidental)
    }

    @Test
    fun laCifraSeEscribeConLaAlteracion() {
        assertEquals("\u266f3", fretText(3, emptySet(), TabAccidental.SHARP))
        assertEquals("3", fretText(3, emptySet(), null))
    }
}

/** **T4 · Altura**: el cambio de octava (8va / 15ma / 8vb) del compás se serializa y se maqueta. */
class TabOctaveShiftTest {

    private fun base(measureCount: Int = 2): List<TabMeasure> =
        TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, measureCount, withGuideColumns = true
        )

    private fun tablature(measures: List<TabMeasure>) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.EIGHTH, measures = measures
    )

    @Test
    fun setOctaveShift_fijaElCambioDeCompas() {
        val updated = TablatureEditing.setOctaveShift(base(), 1, TabOctaveShift.OTTAVA_BASSA)
        assertEquals(TabOctaveShift.OTTAVA_BASSA, updated[1].octaveShift)
        assertNull(updated[0].octaveShift)
    }

    @Test
    fun elCambioDeOctavaSobreviveYSeMaqueta() {
        val measures = TablatureEditing.setOctaveShift(base(), 0, TabOctaveShift.QUINDICESIMA)
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tablature(measures)))
        assertEquals(TabOctaveShift.QUINDICESIMA, restored.measures[0].octaveShift)
        val drawing = TablatureLayout.layout(tablature(measures))
        assertTrue(
            "debe dibujar el glifo de 15ma",
            drawing.texts.any {
                it.text == TabOctaveShift.QUINDICESIMA.display && it.kind == TabTextKind.OCTAVE_SHIFT
            }
        )
    }
}
