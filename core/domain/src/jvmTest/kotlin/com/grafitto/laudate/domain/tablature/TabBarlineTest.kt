package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabBarline
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/** La barra de compás (simple/doble/final) se fija, se maqueta y sobrevive al guardado. */
class TabBarlineTest {

    @Test
    fun setBarline_fijaElTipoDelCompas() {
        val measures = TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, 3, withGuideColumns = true
        )
        val updated = TablatureEditing.setBarline(measures, 2, TabBarline.FINAL)
        assertEquals(TabBarline.SINGLE, updated[1].barline)
        assertEquals(TabBarline.FINAL, updated[2].barline)
    }

    @Test
    fun laBarraSobreviveAlGuardado() {
        val base = TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, 2, withGuideColumns = true
        )
        val measures = TablatureEditing.setBarline(base, 1, TabBarline.DOUBLE)
        val tab = com.grafitto.laudate.domain.model.tablature.Tablature(
            id = "t", songId = "song-1", title = "T", instrument = "Guitarra",
            tuning = com.grafitto.laudate.domain.model.tablature.Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4), resolution = TabResolution.EIGHTH,
            measures = measures
        )
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tab))
        assertEquals(TabBarline.DOUBLE, restored.measures[1].barline)
    }

    @Test
    fun laMaquetacionMarcaElTipoDeBarra() {
        val base = TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, 2, withGuideColumns = true
        )
        val measures = TablatureEditing.setBarline(base, 1, TabBarline.FINAL)
        val tab = com.grafitto.laudate.domain.model.tablature.Tablature(
            id = "t", songId = "song-1", title = "T", instrument = "Guitarra",
            tuning = com.grafitto.laudate.domain.model.tablature.Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4), resolution = TabResolution.EIGHTH,
            measures = measures
        )
        val drawing = TablatureLayout.layout(tab)
        val finalBar = drawing.lines.last { it.isBar }
        assertEquals(TabBarline.FINAL, finalBar.barline)
    }

    // ── Repeticiones integradas en la barra (D-65) ──────────────────────────────────────────────

    private fun tablature(measures: List<com.grafitto.laudate.domain.model.tablature.TabMeasure>) =
        Tablature(
            id = "t", songId = "song-1", title = "T", instrument = "Guitarra",
            tuning = Tuning.GUITAR, time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH, measures = measures
        )

    private fun empty(count: Int) = TablatureEditing.emptyMeasures(
        ScoreTimeSignature("4", 4), TabResolution.EIGHTH, count, withGuideColumns = true
    )

    @Test
    fun laAperturaDeRepeticionVaEnElBordeIzquierdoDelCompas() {
        // `|:` es la barra **izquierda** del compás que abre: la misma `x` que la barra derecha del
        // compás anterior. Antes se rotulaba con un texto `|:` encima del pentagrama.
        val measures = TablatureEditing.setRepeat(empty(2), 0, TabRepeatMark.START)
        val drawing = TablatureLayout.layout(tablature(measures))
        val opening = drawing.lines.single { it.repeat == TabRepeatMark.START }
        assertEquals("cae en el margen izquierdo de la rejilla", drawing.gridLeft, opening.x1, 0.01f)
    }

    @Test
    fun elCierreDeRepeticionVaEnElBordeDerechoDelCompas() {
        val measures = TablatureEditing.setRepeat(empty(2), 0, TabRepeatMark.END)
        val drawing = TablatureLayout.layout(tablature(measures))
        val closing = drawing.lines.single { it.repeat == TabRepeatMark.END }
        // La barra de cierre del compás 0 es la primera barra interior: su `x` es el borde derecho.
        val firstBar = drawing.lines.filter { it.isBar }.minByOrNull { it.x1 }!!
        assertEquals(firstBar.x1, closing.x1, 0.01f)
    }

    @Test
    fun cerrarYAbrirEnLaMismaFronteraDaUnaBarraDoble() {
        // `… :| |: …` comparte frontera: MuseScore dibuja los dos signos espejados en la misma `x`.
        val measures = TablatureEditing.setRepeat(
            TablatureEditing.setRepeat(empty(3), 0, TabRepeatMark.END),
            1, TabRepeatMark.START
        )
        val drawing = TablatureLayout.layout(tablature(measures))
        val both = drawing.lines.single { it.repeat == TabRepeatMark.BOTH }
        val firstBar = drawing.lines.filter { it.isBar }.minByOrNull { it.x1 }!!
        assertEquals(firstBar.x1, both.x1, 0.01f)
    }

    @Test
    fun sinRepeticionesNoHayNingunaBarraDeRepeticion() {
        val drawing = TablatureLayout.layout(tablature(empty(2)))
        assertEquals(0, drawing.lines.count { it.repeat != null })
    }

    @Test
    fun laRepeticionYaNoSeRotulaConTexto() {
        // El `|:` / `:|` de texto sobraba: el signo es la propia barra.
        val measures = TablatureEditing.setRepeat(
            TablatureEditing.setRepeat(empty(2), 0, TabRepeatMark.START),
            1, TabRepeatMark.END
        )
        val drawing = TablatureLayout.layout(tablature(measures))
        assertEquals(
            0,
            drawing.texts.count { it.text == "|:" || it.text == ":|" }
        )
    }
}
