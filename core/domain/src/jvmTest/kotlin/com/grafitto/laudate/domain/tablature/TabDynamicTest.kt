package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabDynamic
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **T2 · Dinámicas**: los matices (`pp`…`fff`, `sf`, `sfz`, `fp`) y los reguladores (`cresc.`, `dim.`)
 * se fijan en un compás, se maquetan bajo la cuerda inferior y sobreviven al guardado.
 */
class TabDynamicTest {

    private fun base(measureCount: Int = 3): List<TabMeasure> =
        TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, measureCount, withGuideColumns = true
        )

    private fun tablature(measures: List<TabMeasure>) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.EIGHTH,
        measures = measures
    )

    @Test
    fun setDynamic_fijaElMatizDelCompas() {
        val updated = TablatureEditing.setDynamic(base(), 1, TabDynamic.MF)
        assertEquals(TabDynamic.MF, updated[1].dynamic)
        assertNull(updated[0].dynamic)
        assertNull(updated[2].dynamic)
    }

    @Test
    fun setDynamic_nullQuitaElMatiz() {
        val withMark = TablatureEditing.setDynamic(base(), 0, TabDynamic.FF)
        val cleared = TablatureEditing.setDynamic(withMark, 0, null)
        assertNull(cleared[0].dynamic)
    }

    @Test
    fun losReguladoresSeDistinguenDeLosMatices() {
        assertTrue(TabDynamic.CRESCENDO.isRegulator)
        assertTrue(TabDynamic.DIMINUENDO.isRegulator)
        assertTrue(!TabDynamic.MF.isRegulator)
    }

    @Test
    fun laDinamicaSobreviveAlGuardado() {
        val measures = TablatureEditing.setDynamic(base(2), 1, TabDynamic.SFZ)
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tablature(measures)))
        assertEquals(TabDynamic.SFZ, restored.measures[1].dynamic)
    }

    @Test
    fun laMaquetacionRotulaLaDinamica() {
        val measures = TablatureEditing.setDynamic(base(2), 0, TabDynamic.CRESCENDO)
        val drawing = TablatureLayout.layout(tablature(measures))
        assertTrue(
            "debe rotular el regulador",
            drawing.texts.any { it.text == "cresc." && it.kind == TabTextKind.DYNAMIC }
        )
    }

    @Test
    fun laFranjaInferiorCreceCuandoHayDinamica() {
        val sinDinamica = TablatureLayout.layout(tablature(base(1)))
        val conDinamica = TablatureLayout.layout(
            tablature(TablatureEditing.setDynamic(base(1), 0, TabDynamic.P))
        )
        assertTrue(
            "la dinámica reserva altura bajo la rejilla",
            conDinamica.height > sinDinamica.height
        )
    }
}
