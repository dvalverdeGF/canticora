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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Compases irregulares de duración libre** (D-36): un compás cuya duración **real** difiere a
 * propósito de la nominal, como el `Measure::isIrregular()` de MuseScore
 * (`m_timesig != m_len`, `measure.h:202`).
 *
 * En MuseScore la irregularidad se **deriva** comparando la indicación con la longitud real. En
 * Canticora la longitud real también es derivable (suma de las columnas), pero derivarla haría que
 * **todo compás a medio escribir** fuese «irregular» y nunca se avisaría de que le faltan silencios.
 * Por eso es un **flag explícito**: el usuario declara que esa duración es la querida.
 */
class TabIrregularMeasureTest {

    private val cuatroCuartos = ScoreTimeSignature("4", 4)

    private fun tab(vararg measures: TabMeasure) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra",
        tuning = Tuning.GUITAR, time = cuatroCuartos, resolution = TabResolution.QUARTER,
        measures = measures.toList()
    )

    private fun lleno() = TabMeasure(
        slots = List(4) { TabSlot(rhythmicFigure = RhythmFigure.QUARTER) }
    )

    /** Tres negras: en `4/4` le falta una, que es justo lo que el compás irregular permite. */
    private fun tresNegras() = TabMeasure(
        slots = List(3) { TabSlot(rhythmicFigure = RhythmFigure.QUARTER) }
    )

    private fun cincoNegras() = TabMeasure(
        slots = List(5) { TabSlot(rhythmicFigure = RhythmFigure.QUARTER) }
    )

    // ── Modelo ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun unCompasNormalNoTieneDuracionLibre() {
        assertFalse(lleno().hasFreeDuration)
        assertFalse(lleno().isIrregular)
    }

    @Test
    fun laAnacrusaYElIrregularCompartenDuracionLibre() {
        assertTrue(lleno().copy(isPickup = true).hasFreeDuration)
        assertTrue(lleno().copy(isIrregular = true).hasFreeDuration)
    }

    // ── Diagnóstico ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun unCompasIrregularCortoNoEsUnError() {
        val d = TabMeasureMeter.diagnose(tresNegras().copy(isIrregular = true), cuatroCuartos, TabResolution.QUARTER)
        assertEquals(3.0, d.actualBeats, 1e-9)
        assertEquals(4.0, d.expectedBeats, 1e-9)
        assertTrue("no es TOO_SHORT: la duración es una decisión", d.isOk)
        assertFalse(d.isTooShort)
    }

    @Test
    fun unCompasIrregularLargoTampocoEsUnError() {
        val d = TabMeasureMeter.diagnose(cincoNegras().copy(isIrregular = true), cuatroCuartos, TabResolution.QUARTER)
        assertTrue("no es TOO_LONG: la duración es una decisión", d.isOk)
        assertFalse(d.isTooLong)
    }

    @Test
    fun elMismoCompasSinMarcarSiEsUnError() {
        assertTrue(TabMeasureMeter.diagnose(tresNegras(), cuatroCuartos, TabResolution.QUARTER).isTooShort)
        assertTrue(TabMeasureMeter.diagnose(cincoNegras(), cuatroCuartos, TabResolution.QUARTER).isTooLong)
    }

    @Test
    fun laDuracionValidaAceptaElCompasIrregular() {
        assertTrue(
            TabMeasureMeter.isDurationValid(
                tresNegras().copy(isIrregular = true), cuatroCuartos, TabResolution.QUARTER
            )
        )
    }

    // ── Relleno ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun completarCompasesNoTocaElCompasIrregular() {
        val irregular = tresNegras().copy(isIrregular = true)
        assertEquals(irregular, TabMeasureMeter.fillWithRests(irregular, cuatroCuartos, TabResolution.QUARTER))
    }

    @Test
    fun completarTodosLosCompasesRespetaElIrregularYArreglaElResto() {
        val irregular = tresNegras().copy(isIrregular = true)
        val corto = tresNegras()
        val result = TabMeasureMeter.fillAll(listOf(irregular, corto), cuatroCuartos, TabResolution.QUARTER)
        assertEquals("el irregular se queda en 3 negras", 3, result[0].slots.size)
        assertEquals("el otro se completa a 4", 4, result[1].slots.size)
    }

    // ── Edición: toggleIrregular ────────────────────────────────────────────────────────────────

    @Test
    fun marcarIrregularNoTocaElContenido() {
        val measures = listOf(lleno(), tresNegras(), lleno())
        val result = TablatureEditing.toggleIrregular(measures, 1, cuatroCuartos, TabResolution.QUARTER)
        assertTrue(result[1].isIrregular)
        assertEquals("las columnas siguen siendo las mismas", measures[1].slots, result[1].slots)
        assertFalse(result[0].isIrregular)
        assertFalse(result[2].isIrregular)
    }

    @Test
    fun desmarcarIrregularReequilibraALaNominal() {
        val measures = listOf(tresNegras().copy(isIrregular = true))
        val result = TablatureEditing.toggleIrregular(measures, 0, cuatroCuartos, TabResolution.QUARTER)
        assertFalse(result[0].isIrregular)
        assertEquals("vuelve a durar 4 negras", 4.0, TabMeasureMeter.durationOf(result[0], TabResolution.QUARTER), 1e-9)
        assertTrue(TabMeasureMeter.diagnose(result[0], cuatroCuartos, TabResolution.QUARTER).isOk)
    }

    @Test
    fun desmarcarIrregularQueSePasaDeLargoLoDejaVisibleComoError() {
        val measures = listOf(cincoNegras().copy(isIrregular = true))
        val result = TablatureEditing.toggleIrregular(measures, 0, cuatroCuartos, TabResolution.QUARTER)
        assertFalse(result[0].isIrregular)
        assertTrue(
            "no se recortan notas: el exceso se reporta para que el usuario lo arregle",
            TabMeasureMeter.diagnose(result[0], cuatroCuartos, TabResolution.QUARTER).isTooLong
        )
    }

    @Test
    fun elIrregularSeMideConSuPropiaIndicacionDeCompas() {
        val tresCuartos = ScoreTimeSignature("3", 4)
        val t = tab(lleno(), tresNegras().copy(isIrregular = true, time = tresCuartos))
        assertEquals(3.0, t.capacityAt(1), 1e-9)
        assertTrue(TabMeasureMeter.diagnose(t.measures[1], t.timeAt(1), TabResolution.QUARTER).isOk)
    }

    @Test
    fun unIndiceFueraDeRangoNoCambiaNada() {
        val measures = listOf(lleno())
        assertEquals(measures, TablatureEditing.toggleIrregular(measures, 5, cuatroCuartos, TabResolution.QUARTER))
    }

    @Test
    fun elIrregularConservaSusNotasAlDesmarcarlo() {
        val conNota = TabMeasure(
            slots = listOf(
                TabSlot(
                    strings = listOf(TabPluck(TabPosition(stringNumber = 1, fret = 3))),
                    rhythmicFigure = RhythmFigure.QUARTER
                ),
                TabSlot(rhythmicFigure = RhythmFigure.QUARTER)
            ),
            isIrregular = true
        )
        val result = TablatureEditing.toggleIrregular(listOf(conNota), 0, cuatroCuartos, TabResolution.QUARTER)
        assertEquals("la nota sigue en su casilla", 1, result[0].slots[0].strings.size)
        assertEquals(3, result[0].slots[0].strings[0].position.fret)
        // El compás vuelve a durar 4/4 con el relleno **agrupado por pulso**: negra (pulso 2) +
        // blanca (pulsos 3-4), no tres negras sueltas (2026-10-27).
        assertEquals(3, result[0].slots.size)
        assertEquals(RhythmFigure.HALF_REST, result[0].slots[2].rhythmicFigure)
        assertEquals(
            "el compás vuelve a durar 4/4",
            4.0,
            TabMeasureMeter.durationOf(result[0], TabResolution.QUARTER),
            1e-6
        )
    }
}
