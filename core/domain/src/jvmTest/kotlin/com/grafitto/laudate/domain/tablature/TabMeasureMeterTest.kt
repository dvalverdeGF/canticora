package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Fase B.1 · Métrica de compases** (port de `TGMeasureError` + `TGMeasureManager.getMeasureErrors`
 * + `TGDuration.splitPreciseDuration` de TuxGuitar).
 *
 * Comprueba la *inteligencia de negocio*: un compás `4/4` **se completa** con silencios, se detecta
 * cuándo está corto (`TOO_SHORT`) o se pasa (`TOO_LONG`), y el relleno usa **pocas figuras grandes**
 * (2,5 negras = blanca + corchea, no cinco corcheas).
 */
class TabMeasureMeterTest {

    private val time = ScoreTimeSignature("4", 4)
    private val res = TabResolution.EIGHTH

    private fun slot(figure: RhythmFigure, withNote: Boolean = false) = TabSlot(
        strings = if (withNote) listOf(com.grafitto.laudate.domain.model.tablature.TabPluck(1, 0)) else emptyList(),
        rhythmicFigure = figure
    )

    // ── Duración ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun laDuracionDeUnCompasEsLaSumaDeSusFiguras() {
        val m = TabMeasure(slots = listOf(slot(RhythmFigure.QUARTER), slot(RhythmFigure.QUARTER), slot(RhythmFigure.HALF)))
        assertEquals(4.0, TabMeasureMeter.durationOf(m, res), 0.001)
        assertEquals(4.0, TabMeasureMeter.expectedBeats(time), 0.001)
    }

    @Test
    fun elTresilloCuentaDosTercios() {
        val m = TabMeasure(slots = listOf(TabSlot(rhythmicFigure = RhythmFigure.QUARTER, tuplet = TabTuplet.TRIPLET)))
        assertEquals(2.0 / 3.0, TabMeasureMeter.durationOf(m, res), 0.001)
    }

    // ── Diagnóstico ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun unCompasCortoSeMarcaComoTooShort() {
        val m = TabMeasure(slots = listOf(slot(RhythmFigure.QUARTER), slot(RhythmFigure.QUARTER), slot(RhythmFigure.QUARTER)))
        val d = TabMeasureMeter.diagnose(m, time, res)
        assertTrue("faltan 1,0 negras", d.isTooShort)
        assertEquals(1.0, d.missingBeats, 0.001)
        assertFalse(d.isTooLong)
    }

    @Test
    fun unCompasCompletoEsValido() {
        val m = TabMeasure(slots = List(4) { slot(RhythmFigure.QUARTER) })
        val d = TabMeasureMeter.diagnose(m, time, res)
        assertTrue("no debe haber error", d.isOk)
        assertTrue(TabMeasureMeter.isDurationValid(m, time, res))
    }

    @Test
    fun unCompasSobrecargadoSeMarcaComoTooLong() {
        val m = TabMeasure(slots = List(5) { slot(RhythmFigure.QUARTER) })
        val d = TabMeasureMeter.diagnose(m, time, res)
        assertTrue(d.isTooLong)
        assertEquals(1.0, d.excessBeats, 0.001)
    }

    @Test
    fun unCompasVacioSeInterpretaComoSilencio() {
        val m = TabMeasure()
        assertTrue(TabMeasureMeter.diagnose(m, time, res).isEmpty)
    }

    // ── Reparto de silencios (splitPreciseDuration) ───────────────────────────────────────────────

    @Test
    fun unHuecoSeReparteEnPocasFigurasGrandes() {
        // 2,5 negras → blanca (2) + corchea (0,5), NO cinco corcheas.
        assertEquals(
            listOf(RhythmFigure.HALF_REST, RhythmFigure.EIGHTH_REST),
            TabMeasureMeter.splitIntoRests(2.5)
        )
        // Un compás 4/4 entero → un solo silencio de redonda.
        assertEquals(listOf(RhythmFigure.WHOLE_REST), TabMeasureMeter.splitIntoRests(4.0))
        // 1,5 negras → negra con puntillo.
        assertEquals(listOf(RhythmFigure.DOTTED_QUARTER_REST), TabMeasureMeter.splitIntoRests(1.5))
    }

    // ── Relleno (fillWithRests / addNote) ─────────────────────────────────────────────────────────

    @Test
    fun rellenaElCompasCortoConLosSilenciosQueFaltan() {
        val m = TabMeasure(slots = listOf(slot(RhythmFigure.QUARTER, withNote = true), slot(RhythmFigure.QUARTER, withNote = true), slot(RhythmFigure.QUARTER, withNote = true)))
        val filled = TabMeasureMeter.fillWithRests(m, time, res)
        // Se añade UNA negra de silencio al final.
        assertEquals(4, filled.slots.size)
        assertEquals(RhythmFigure.QUARTER_REST, filled.slots.last().rhythmicFigure)
        assertEquals(4.0, TabMeasureMeter.durationOf(filled, res), 0.001)
        assertTrue(TabMeasureMeter.isDurationValid(filled, time, res))
    }

    @Test
    fun noTocaUnCompasQueYaEstaCompleto() {
        val m = TabMeasure(slots = List(4) { slot(RhythmFigure.QUARTER) })
        assertEquals(m, TabMeasureMeter.fillWithRests(m, time, res))
    }

    @Test
    fun noTocaUnCompasSobrecargado() {
        val m = TabMeasure(slots = List(5) { slot(RhythmFigure.QUARTER) })
        assertEquals("no se destruye lo escrito", m, TabMeasureMeter.fillWithRests(m, time, res))
    }

    @Test
    fun unCompasNuevoNaceCompleto() {
        // Port de MuseScore/TuxGuitar: al añadir un compás, nace con su silencio de compás.
        val m = TabMeasureMeter.emptyCompleteMeasure(time, res)
        assertEquals(4.0, TabMeasureMeter.durationOf(m, res), 0.001)
        assertTrue(TabMeasureMeter.isAllRests(m))
    }

    // ── Insertar silencio explícito ───────────────────────────────────────────────────────────────

    @Test
    fun insertaUnSilencioEnLaPosicionPedida() {
        // (D-83) El silencio **sustituye** el pulso del cursor en vez de acumularse: el compás nace
        // con un silencio por pulso, así que añadirlo dejaba cinco columnas y el compás pasado.
        val m = TabMeasure(slots = listOf(slot(RhythmFigure.QUARTER), slot(RhythmFigure.QUARTER)))
        val withRest = TabMeasureMeter.insertRest(m, RhythmFigure.QUARTER, slotIndex = 1, resolution = res)
        // La columna 1 (vacía) se consume y en su sitio queda el silencio de negra.
        assertEquals(2, withRest.slots.size)
        assertEquals(RhythmFigure.QUARTER_REST, withRest.slots[1].rhythmicFigure)
    }

    @Test
    fun unSilencioLargoAbsorbeLosPulsosVaciosQueCubre() {
        // Un `4/4` con cuatro silencios de negra: poner uno de redonda en el primer pulso deja
        // **una** columna, no cinco.
        val m = TabMeasure(slots = List(4) { slot(RhythmFigure.QUARTER) })
        val withRest = TabMeasureMeter.insertRest(m, RhythmFigure.WHOLE_REST, slotIndex = 0, resolution = res)
        assertEquals(1, withRest.slots.size)
        assertEquals(RhythmFigure.WHOLE_REST, withRest.slots.single().rhythmicFigure)
        assertEquals(4.0, TabMeasureMeter.durationOf(withRest, res), 0.001)
    }

    @Test
    fun unSilencioNoBorraUnaNota() {
        // El silencio solo consume columnas **vacías**: si encuentra una nota, se detiene ahí.
        val m = TabMeasure(
            slots = listOf(
                slot(RhythmFigure.QUARTER, withNote = true),
                slot(RhythmFigure.QUARTER, withNote = true)
            )
        )
        val withRest = TabMeasureMeter.insertRest(m, RhythmFigure.HALF_REST, slotIndex = 0, resolution = res)
        assertEquals(3, withRest.slots.size)
        assertEquals(2, withRest.slots.count { it.strings.isNotEmpty() })
    }

    @Test
    fun unaFiguraSonoraSeConvierteEnSuSilencioEquivalente() {
        val m = TabMeasure(slots = emptyList())
        val withRest = TabMeasureMeter.insertRest(m, RhythmFigure.HALF, resolution = res)
        assertEquals(RhythmFigure.HALF_REST, withRest.slots.single().rhythmicFigure)
    }

    // ── Multi-compás ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun completaTodosLosCompasesCortosDeUnaTablatura() {
        val measures = listOf(
            TabMeasure(slots = List(4) { slot(RhythmFigure.QUARTER) }),      // completo
            TabMeasure(slots = listOf(slot(RhythmFigure.QUARTER))),          // corto → se completa
            TabMeasure(slots = List(2) { slot(RhythmFigure.HALF) })          // completo
        )
        val filled = TabMeasureMeter.fillAll(measures, time, res)
        assertEquals(3, filled.size)
        assertTrue(filled.all { TabMeasureMeter.isDurationValid(it, time, res) })
    }

    // ── Anacrusa (pickup) ────────────────────────────────────────────────────────────────────────

    @Test
    fun unaAnacrusaCortaNoSeMarcaComoTooShort() {
        // MuseScore: `Measure::isIrregular()` es `m_timesig != m_len`, y la anacrusa es el caso
        // legítimo de compás irregular. Un compás corto marcado como anacrusa no es un error.
        val m = TabMeasure(
            slots = listOf(slot(RhythmFigure.QUARTER, withNote = true)),
            isPickup = true
        )
        val d = TabMeasureMeter.diagnose(m, time, res)
        assertFalse("la anacrusa no es un compás corto", d.isTooShort)
        assertTrue(d.isOk)
    }

    @Test
    fun unaAnacrusaSobrecargadaSiSeMarcaComoTooLong() {
        // La anacrusa exime de la falta de duración, no del exceso: pasarse del compás sigue mal.
        val m = TabMeasure(slots = List(5) { slot(RhythmFigure.QUARTER) }, isPickup = true)
        assertTrue(TabMeasureMeter.diagnose(m, time, res).isTooLong)
    }

    @Test
    fun elRellenoNoTocaLaAnacrusa() {
        // Si «Completar compases» rellenara la anacrusa, la marca no serviría de nada.
        val m = TabMeasure(
            slots = listOf(slot(RhythmFigure.QUARTER, withNote = true)),
            isPickup = true
        )
        assertEquals(m, TabMeasureMeter.fillWithRests(m, time, res))
        assertEquals(m, TabMeasureMeter.fillAll(listOf(m), time, res).single())
    }

    @Test
    fun laTablaturaAdmiteMultiplesCompases() {
        val measures = listOf(
            TabMeasureMeter.emptyCompleteMeasure(time, res),
            TabMeasureMeter.emptyCompleteMeasure(time, res),
            TabMeasureMeter.emptyCompleteMeasure(time, res)
        )
        assertEquals(3, measures.size)
        assertTrue(measures.all { TabMeasureMeter.durationOf(it, res) == 4.0 })
    }
}

/**
 * (D-86) **Un compás `4/4` admite dos blancas, no cuatrocientas mil.**
 *
 * El usuario lo preguntó tal cual —«¿cuántas blancas caben en un compás de 4/4?, ¿cuatrocientas
 * mil?»— porque la aplicación se lo permitía: `rebalance` solo rellenaba el hueco que **faltaba** y
 * dejaba intacto el exceso, así que alargar una nota alargaba el compás sin límite. El diagnóstico
 * (`diagnose`) ya marcaba `TOO_LONG`, pero nadie hacía nada con él.
 *
 * Lo que fija este test es la **regla musical**: la duración del compás es la de su indicación (§ la
 * suma de las figuras), y `isDurationValid` la comprueba. El editor se apoya en ella para no aplicar
 * una figura que no cabe.
 */
class TabMeasureCapacityTest {

    private val cuatroCuartos = ScoreTimeSignature("4", 4)
    private val negras = TabResolution.QUARTER

    private fun silencio(figure: RhythmFigure) = TabSlot(rhythmicFigure = figure)

    @Test
    fun enUnCuatroCuartosCabenDosBlancas() {
        val dosBlancas = TabMeasure(
            slots = listOf(silencio(RhythmFigure.HALF_REST), silencio(RhythmFigure.HALF_REST))
        )
        assertEquals(4.0, TabMeasureMeter.durationOf(dosBlancas, negras), 1e-6)
        assertTrue(TabMeasureMeter.isDurationValid(dosBlancas, cuatroCuartos, negras))
    }

    @Test
    fun tresBlancasYaNoCaben() {
        // Tres blancas son 6 negras en un compás de 4: el compás se pasa.
        val tresBlancas = TabMeasure(
            slots = List(3) { silencio(RhythmFigure.HALF_REST) }
        )
        assertEquals(6.0, TabMeasureMeter.durationOf(tresBlancas, negras), 1e-6)
        assertFalse(
            "tres blancas no caben en 4/4",
            TabMeasureMeter.isDurationValid(tresBlancas, cuatroCuartos, negras)
        )
        assertTrue(TabMeasureMeter.diagnose(tresBlancas, cuatroCuartos, negras).isTooLong)
    }

    @Test
    fun milBlancasTampocoCaben() {
        val mil = TabMeasure(slots = List(1000) { silencio(RhythmFigure.HALF_REST) })
        assertTrue(TabMeasureMeter.diagnose(mil, cuatroCuartos, negras).isTooLong)
        assertFalse(TabMeasureMeter.isDurationValid(mil, cuatroCuartos, negras))
    }
}
