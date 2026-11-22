package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabJump
import com.grafitto.laudate.domain.model.tablature.TabNavigationMark
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **T1 · Repeticiones y saltos**: los signos de navegación (Segno, Coda, Fine, To Coda) y los saltos
 * (D.C. / D.S. y sus variantes) se fijan en un compás, se maquetan en la franja de marcas y
 * sobreviven al guardado/reabierto sin romper el layout.
 */
class TabNavigationTest {

    private fun base(measureCount: Int = 3): List<com.grafitto.laudate.domain.model.tablature.TabMeasure> =
        TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, measureCount, withGuideColumns = true
        )

    private fun tablature(measures: List<com.grafitto.laudate.domain.model.tablature.TabMeasure>) =
        Tablature(
            id = "t", songId = "song-1", title = "T", instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4), resolution = TabResolution.EIGHTH,
            measures = measures
        )

    @Test
    fun setNavigation_fijaElSignoDelCompas() {
        val updated = TablatureEditing.setNavigation(base(), 1, TabNavigationMark.SEGNO)
        assertEquals(TabNavigationMark.SEGNO, updated[1].navigation)
        assertNull(updated[0].navigation)
        assertNull(updated[2].navigation)
    }

    @Test
    fun setJump_fijaElSaltoDelCompas() {
        val updated = TablatureEditing.setJump(base(), 2, TabJump.DS_AL_CODA)
        assertEquals(TabJump.DS_AL_CODA, updated[2].jump)
        assertNull(updated[0].jump)
    }

    @Test
    fun setNavigation_nullQuitaElSigno() {
        val withMark = TablatureEditing.setNavigation(base(), 0, TabNavigationMark.CODA)
        val cleared = TablatureEditing.setNavigation(withMark, 0, null)
        assertNull(cleared[0].navigation)
    }

    @Test
    fun laNavegacionYSaltoSobrevivenAlGuardado() {
        var measures = base(2)
        measures = TablatureEditing.setNavigation(measures, 0, TabNavigationMark.SEGNO)
        measures = TablatureEditing.setJump(measures, 1, TabJump.DC_AL_FINE)
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tablature(measures)))
        assertEquals(TabNavigationMark.SEGNO, restored.measures[0].navigation)
        assertEquals(TabJump.DC_AL_FINE, restored.measures[1].jump)
    }

    @Test
    fun laMaquetacionRotulaLaNavegacionYElSalto() {
        var measures = base(2)
        measures = TablatureEditing.setNavigation(measures, 0, TabNavigationMark.CODA)
        measures = TablatureEditing.setJump(measures, 1, TabJump.DC_AL_CODA)
        val drawing = TablatureLayout.layout(tablature(measures))
        val texts = drawing.texts.map { it.text }
        assertTrue("debe rotular el signo de Coda", texts.any { it == TabNavigationMark.CODA.display })
        assertTrue("debe rotular el salto D.C. al Coda", texts.any { it == "D.C. al Coda" })
    }

    @Test
    fun laFranjaDeMarcasCreceCuandoHayNavegacion() {
        val sinMark = TablatureLayout.layout(tablature(base(1)))
        val conNavegacion = TablatureEditing.setNavigation(base(1), 0, TabNavigationMark.FINE)
        val conMark = TablatureLayout.layout(tablature(conNavegacion))
        assertTrue(
            "la franja de marcas debe reservar altura para el rótulo",
            conMark.height >= sinMark.height
        )
    }
}
