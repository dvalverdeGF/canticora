package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **E1-E3 · Edición en bloque**: selección de compases, borrado/copiado/duplicado/pegado y su
 * interacción con el historial de deshacer/rehacer.
 */
class TablatureSelectionTest {

    private fun measures(count: Int): List<TabMeasure> =
        TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, count, withGuideColumns = true
        ).mapIndexed { index, m ->
            // Se marca cada compás con el traste de un compás para reconocerlo al copiar/pegar.
            TablatureEditing.setFret(listOf(m), 0, 0, 1, index).first()
        }

    @Test
    fun laSeleccionSeNormalizaYAcota() {
        assertEquals(3, TablatureSelection(0..2).size)
        assertTrue(TablatureSelection(0..2).contains(1))
        assertTrue(!TablatureSelection(0..2).contains(3))
        // Un rango invertido se normaliza a `first..last` creciente.
        assertEquals(0..2, TablatureSelection(2..0).clamped(5)?.range)
        // Fuera de rango se acota al último índice.
        assertEquals(3..4, TablatureSelection(3..9).clamped(5)?.range)
        assertNull(TablatureSelection(0..1).clamped(0))
    }

    @Test
    fun removeMeasures_borraElRangoSeleccionado() {
        val result = TablatureEditing.removeMeasures(measures(5), 1..2)
        assertEquals(3, result.size)
        // El primer compás se conserva (traste 0) y el siguiente pasa a ser el que era el 4.º (3).
        assertEquals(0, result[0].slots[0].strings[0].fret)
        assertEquals(3, result[1].slots[0].strings[0].fret)
    }

    @Test
    fun removeMeasures_nuncaDejaSinCompases() {
        val result = TablatureEditing.removeMeasures(measures(3), 0..2)
        assertEquals(1, result.size)
        assertTrue(result[0].slots.all { it.strings.isEmpty() })
    }

    @Test
    fun insertMeasures_pegaElBloqueEnLaPosicion() {
        val block = measures(1).map { it.copy(slots = emptyList()) }
        val result = TablatureEditing.insertMeasures(measures(3), 1, block)
        assertEquals(4, result.size)
        // El compás insertado (vacío) queda en la posición 1; los originales se desplazan.
        assertTrue(result[1].slots.isEmpty())
        assertEquals(0, result[0].slots[0].strings[0].fret)
        assertEquals(1, result[2].slots[0].strings[0].fret)
        assertEquals(2, result[3].slots[0].strings[0].fret)
    }

    @Test
    fun duplicateMeasures_copiaDetrasDeLaSeleccion() {
        val original = measures(3)
        val result = TablatureEditing.duplicateMeasures(original, 0..1)
        assertEquals(5, result.size)
        // La copia (índices 2 y 3) lleva los mismos trastes que el origen (0 y 1).
        assertEquals(0, result[2].slots[0].strings[0].fret)
        assertEquals(1, result[3].slots[0].strings[0].fret)
        assertEquals(2, result[4].slots[0].strings[0].fret)
    }
}
