package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Cambio de compás a mitad de pieza** (D-35): la indicación de compás es una propiedad **del
 * compás**, no del documento, como el `TimeSig` que MuseScore ancla a un `Measure`
 * (`Measure::timesig()`). Rige desde ese compás hasta el siguiente cambio, y cada compás se mide,
 * se rellena, se agrupa y se cuenta con **su** indicación.
 */
class TabTimeChangeTest {

    private val cuatroCuartos = ScoreTimeSignature("4", 4)
    private val tresCuartos = ScoreTimeSignature("3", 4)

    private fun tab(vararg measures: TabMeasure) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra",
        tuning = Tuning.GUITAR, time = cuatroCuartos, resolution = TabResolution.QUARTER,
        measures = measures.toList()
    )

    private fun lleno(time: ScoreTimeSignature = cuatroCuartos) = TabMeasure(
        slots = List(time.totalQuarterBeats.toInt()) { TabSlot(rhythmicFigure = RhythmFigure.QUARTER) }
    )

    // ── Modelo: timeAt / capacityAt / changesTimeAt ──────────────────────────────────────────────

    @Test
    fun sinCambiosLaIndicacionEsLaDelDocumentoEnTodosLosCompases() {
        val t = tab(lleno(), lleno(), lleno())
        assertEquals(cuatroCuartos, t.timeAt(0))
        assertEquals(cuatroCuartos, t.timeAt(2))
        assertEquals(4.0, t.capacityAt(2), 1e-9)
        assertTrue("ningún compás cambia el compás", (0..2).none { t.changesTimeAt(it) })
    }

    @Test
    fun elCambioRigeDesdeSuCompasHastaElSiguienteCambio() {
        val t = tab(lleno(), lleno(tresCuartos).copy(time = tresCuartos), lleno(tresCuartos))
        assertEquals("el compás anterior al cambio sigue en 4/4", cuatroCuartos, t.timeAt(0))
        assertEquals("el compás del cambio ya es 3/4", tresCuartos, t.timeAt(1))
        assertEquals("y el siguiente lo hereda", tresCuartos, t.timeAt(2))
        assertEquals(3.0, t.capacityAt(2), 1e-9)
    }

    @Test
    fun soloSeRotulaElCompasQueIntroduceElCambio() {
        val t = tab(lleno(), lleno(tresCuartos).copy(time = tresCuartos), lleno(tresCuartos))
        assertFalse("el compás 1 no cambia nada", t.changesTimeAt(0))
        assertTrue("el compás 2 introduce el 3/4", t.changesTimeAt(1))
        assertFalse("el compás 3 solo lo hereda", t.changesTimeAt(2))
    }

    @Test
    fun unCambioEnElPrimerCompasNoSeRotulaDentroDelSistema() {
        // En el compás 1 la fracción la pinta la **cabecera** del sistema, así que no hay que
        // reservar franja ni dibujarla otra vez dentro de la rejilla.
        val t = tab(lleno(tresCuartos).copy(time = tresCuartos), lleno(tresCuartos))
        assertFalse(t.changesTimeAt(0))
        assertEquals(tresCuartos, t.timeAt(0))
    }

    // ── Medición: el compás se mide con su indicación ────────────────────────────────────────────

    @Test
    fun unCompasDeTresCuartosDentroDeUnaPiezaDeCuatroCuartosNoEstaCorto() {
        val compas = lleno(tresCuartos).copy(time = tresCuartos)
        val diagnosis = TabMeasureMeter.diagnose(compas, tresCuartos, TabResolution.QUARTER)
        assertTrue("3 negras en 3/4 es un compás completo", diagnosis.isOk)
        assertEquals(3.0, diagnosis.expectedBeats, 1e-9)
    }

    @Test
    fun medidoConLaIndicacionEquivocadaElCompasPareceCorto() {
        // Es el defecto que arregla D-35: con la indicación **global** (4/4) un compás de 3/4 sale
        // corto y «Completar compases» le añade una negra de silencio que no debe llevar.
        val compas = lleno(tresCuartos).copy(time = tresCuartos)
        val diagnosis = TabMeasureMeter.diagnose(compas, cuatroCuartos, TabResolution.QUARTER)
        assertTrue(diagnosis.isTooShort)
        assertEquals(1.0, diagnosis.missingBeats, 1e-9)
    }

    @Test
    fun completarCompasesRespetaLaIndicacionDeCadaCompas() {
        val t = tab(lleno(), lleno(tresCuartos).copy(time = tresCuartos))
        val completados = t.measures.mapIndexed { index, measure ->
            TabMeasureMeter.fillWithRests(measure, t.timeAt(index), TabResolution.QUARTER)
        }
        assertEquals("el 4/4 sigue con 4 columnas", 4, completados[0].slots.size)
        assertEquals("el 3/4 sigue con 3 columnas", 3, completados[1].slots.size)
    }

    // ── Edición: setTime reequilibra el compás ───────────────────────────────────────────────────

    @Test
    fun cambiarElCompasReequilibraSuDuracion() {
        val measures = listOf(lleno(), lleno(), lleno())
        val next = TablatureEditing.setTime(
            measures, 1, tresCuartos, cuatroCuartos, TabResolution.QUARTER
        )
        assertEquals(tresCuartos, next[1].time)
        assertEquals("el compás pasa a durar 3 negras", 3, next[1].slots.size)
        assertEquals("los demás no se tocan", 4, next[0].slots.size)
        assertEquals(4, next[2].slots.size)
    }

    @Test
    fun quitarElCambioDevuelveElCompasALaIndicacionDelDocumento() {
        val measures = listOf(lleno(), lleno(tresCuartos).copy(time = tresCuartos))
        val next = TablatureEditing.setTime(
            measures, 1, null, cuatroCuartos, TabResolution.QUARTER
        )
        assertNull("sin override", next[1].time)
        assertEquals("y vuelve a durar 4 negras", 4, next[1].slots.size)
    }

    @Test
    fun pedirLaIndicacionDelDocumentoNoGuardaUnOverrideInutil() {
        val measures = listOf(lleno(), lleno())
        val next = TablatureEditing.setTime(
            measures, 1, cuatroCuartos, cuatroCuartos, TabResolution.QUARTER
        )
        assertNull("un override que no cambia nada es ruido en el modelo y en el JSON", next[1].time)
        assertEquals(measures, next)
    }

    @Test
    fun cambiarElCompasConservaLasNotasEscritas() {
        val conNota = TabMeasure(
            slots = listOf(
                TabSlot(
                    strings = listOf(TabPluck(TabPosition(1, 3))),
                    rhythmicFigure = RhythmFigure.QUARTER
                ),
                TabSlot(rhythmicFigure = RhythmFigure.QUARTER),
                TabSlot(rhythmicFigure = RhythmFigure.QUARTER),
                TabSlot(rhythmicFigure = RhythmFigure.QUARTER)
            )
        )
        val next = TablatureEditing.setTime(
            listOf(conNota), 0, tresCuartos, cuatroCuartos, TabResolution.QUARTER
        )
        assertEquals("la nota sigue en su cuerda y traste", 3, next[0].slots[0].strings[0].fret)
        assertEquals(3, next[0].slots.size)
    }

    @Test
    fun unIndiceFueraDeRangoNoTocaNada() {
        val measures = listOf(lleno())
        assertEquals(
            measures,
            TablatureEditing.setTime(measures, 5, tresCuartos, cuatroCuartos, TabResolution.QUARTER)
        )
    }

    // ── Maquetación: la fracción se reserva y se dibuja dentro del sistema ───────────────────────

    @Test
    fun elCompasQueCambiaElCompasReservaFranjaParaLaFraccion() {
        val conCambio = tab(lleno(), lleno(tresCuartos).copy(time = tresCuartos))
        val sinCambio = tab(lleno(), lleno(tresCuartos))
        val settings = TabLayoutSettings()
        val anchoCon = TablatureSystemsLayout.naturalWidth(conCambio, settings)
        val anchoSin = TablatureSystemsLayout.naturalWidth(sinCambio, settings)
        assertTrue(
            "la fracción dentro del sistema suma ancho ($anchoCon > $anchoSin)",
            anchoCon > anchoSin
        )
    }

    @Test
    fun laFraccionDelCambioSeDibujaDentroDelSistema() {
        val t = tab(lleno(), lleno(tresCuartos).copy(time = tresCuartos))
        val sistemas = TablatureSystemsLayout.layout(t, maxWidth = 2000f)
        val textos = sistemas.flatMap { it.drawing.texts }
        val numeradores = textos.filter { it.kind == TabTextKind.TIME_NUMERATOR }
        // Cabecera (4) + el cambio dentro del sistema (3).
        assertTrue(
            "debe haber dos numeradores: la cabecera y el cambio",
            numeradores.size >= 2
        )
        assertTrue(
            "el cambio rotula el 3",
            numeradores.any { it.text == "3" }
        )
        assertTrue(
            "y su denominador",
            textos.any { it.kind == TabTextKind.TIME_DENOMINATOR && it.text == "4" }
        )
    }

    @Test
    fun sinCambioNoSeDibujaNingunaFraccionDentroDelSistema() {
        val t = tab(lleno(), lleno())
        val sistemas = TablatureSystemsLayout.layout(t, maxWidth = 2000f)
        val numeradores = sistemas.flatMap { it.drawing.texts }
            .filter { it.kind == TabTextKind.TIME_NUMERATOR }
        assertEquals("solo la de la cabecera", 1, numeradores.size)
    }

    // ── Barras de unión: cada compás corta por su pulso ──────────────────────────────────────────

    @Test
    fun lasBarrasDeUnionSiguenElPulsoDeCadaCompas() {
        // En 4/4 el pulso es la negra; en 6/8 el pulso compuesto es la negra con puntillo (3 corcheas).
        // Un compás de 6/8 con 6 corcheas debe agruparse en **dos** grupos de 3, no en tres de 2.
        val marks = (0 until 6).map { i ->
            TabRhythmMark(
                figure = RhythmFigure.EIGHTH,
                x = i * 10f,
                y = 0f,
                measureIndex = 1,
                beatOffset = i * 0.5
            )
        }
        val seisOctavos = ScoreTimeSignature("6", 8)
        val grupos = TabBeamGrouping.groupsOf(marks, cuatroCuartos) { seisOctavos }
        assertEquals("dos grupos de tres corcheas", 2, grupos.size)
        assertEquals(listOf(3, 3), grupos.map { it.size })
    }

    // ── Reproducción: el metrónomo sigue el compás que suena ─────────────────────────────────────

    @Test
    fun elMetronomoCuentaConLaIndicacionDeCadaCompas() {
        val t = tab(lleno(), lleno(tresCuartos).copy(time = tresCuartos))
        val timeline = TabPlayback.timeline(t, defaultBpm = 60)
        val delPrimero = timeline.clicks.filter { it.measureIndex == 0 }
        val delSegundo = timeline.clicks.filter { it.measureIndex == 1 }
        assertEquals("4 pulsaciones en el 4/4", 4, delPrimero.size)
        assertEquals("3 pulsaciones en el 3/4", 3, delSegundo.size)
        assertTrue("el primer pulso de cada compás va acentuado", delPrimero.first().isAccent)
        assertTrue(delSegundo.first().isAccent)
    }

    @Test
    fun elCompasCompuestoAcentuaLaMitadDelCompas() {
        val seisOctavos = ScoreTimeSignature("6", 8)
        val compas = TabMeasure(
            slots = List(6) { TabSlot(rhythmicFigure = RhythmFigure.EIGHTH) }
        )
        val t = Tablature(
            id = "t", songId = "s", title = "T", instrument = "Guitarra",
            tuning = Tuning.GUITAR, time = seisOctavos, resolution = TabResolution.EIGHTH,
            measures = listOf(compas)
        )
        val clicks = TabPlayback.timeline(t, defaultBpm = 60).clicks
        assertEquals("6 pulsaciones de corchea", 6, clicks.size)
        assertEquals(
            "acentos en 1 y 4 (compás compuesto)",
            listOf(1, 4),
            clicks.filter { it.isAccent }.map { it.beat }
        )
    }
}
