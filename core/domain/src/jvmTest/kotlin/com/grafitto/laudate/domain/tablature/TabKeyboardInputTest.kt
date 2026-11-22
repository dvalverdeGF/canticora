package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.tablature.TabKeyboardInput.Cursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pruebas del ayudante puro de entrada por teclado físico del editor de tablatura. */
class TabKeyboardInputTest {

    private val maxFret = MAX_FRET // 24

    // ── Movimiento horizontal (por columnas reales de cada compás) ───────────────

    @Test
    fun horizontalMove_staysInsideTheSameMeasure() {
        val counts = listOf(8, 8)
        val cursor = Cursor(0, 2, 3)
        assertEquals(Cursor(0, 3, 3), TabKeyboardInput.moveHorizontal(cursor, counts, 1))
        assertEquals(Cursor(0, 1, 3), TabKeyboardInput.moveHorizontal(cursor, counts, -1))
    }

    @Test
    fun horizontalMove_crossesIntoTheNextMeasure() {
        val counts = listOf(8, 8)
        // Desde la última columna REAL (7), un paso más cae en la posición fantasma (8) del mismo
        // compás; el siguiente paso ya entra en el compás 1.
        assertEquals(Cursor(0, 8, 3), TabKeyboardInput.moveHorizontal(Cursor(0, 7, 3), counts, 1))
        assertEquals(Cursor(1, 0, 3), TabKeyboardInput.moveHorizontal(Cursor(0, 8, 3), counts, 1))
    }

    @Test
    fun horizontalMove_crossesBackToThePreviousMeasure() {
        val counts = listOf(8, 8)
        // Desde la columna 0 del compás 1, un paso atrás cae en la fantasma del compás 0 (8).
        assertEquals(Cursor(0, 8, 3), TabKeyboardInput.moveHorizontal(Cursor(1, 0, 3), counts, -1))
        assertEquals(Cursor(0, 7, 3), TabKeyboardInput.moveHorizontal(Cursor(0, 8, 3), counts, -1))
    }

    @Test
    fun horizontalMove_clampsAtBothEnds() {
        val counts = listOf(8, 8)
        // No sale por la izquierda del primer compás.
        assertEquals(Cursor(0, 0, 3), TabKeyboardInput.moveHorizontal(Cursor(0, 0, 3), counts, -1))
        // Ni por la derecha del último: se queda en su posición fantasma (8).
        assertEquals(Cursor(1, 8, 3), TabKeyboardInput.moveHorizontal(Cursor(1, 8, 3), counts, 1))
    }

    @Test
    fun horizontalMove_usesEachMeasureColumnCount() {
        // El compás 0 tiene 2 columnas (posiciones 0..2) y el 1 tiene 5 (posiciones 0..5).
        val counts = listOf(2, 5)
        assertEquals(Cursor(1, 0, 3), TabKeyboardInput.moveHorizontal(Cursor(0, 2, 3), counts, 1))
        assertEquals(Cursor(0, 2, 3), TabKeyboardInput.moveHorizontal(Cursor(1, 0, 3), counts, -1))
    }

    @Test
    fun horizontalMove_treatsEmptyMeasureAsOnePosition() {
        // Un compás VACÍO (0 columnas) admite su posición fantasma 0 para escribir la primera nota.
        val counts = listOf(0, 3)
        assertEquals(Cursor(0, 0, 1), TabKeyboardInput.moveHorizontal(Cursor(0, 0, 1), counts, -1))
        assertEquals(Cursor(1, 0, 1), TabKeyboardInput.moveHorizontal(Cursor(0, 0, 1), counts, 1))
    }

    @Test
    fun advance_movesOneColumnForward() {
        assertEquals(Cursor(0, 1, 3), TabKeyboardInput.advance(Cursor(0, 0, 3), listOf(8, 8)))
    }

    // ── Movimiento vertical ──────────────────────────────────────────────────────

    @Test
    fun verticalMove_changesStringWithinRange() {
        assertEquals(Cursor(0, 0, 4), TabKeyboardInput.moveVertical(Cursor(0, 0, 3), 6, 1))
        assertEquals(Cursor(0, 0, 2), TabKeyboardInput.moveVertical(Cursor(0, 0, 3), 6, -1))
    }

    @Test
    fun verticalMove_clampsInsteadOfWrapping() {
        // Cuerda 1 es la más aguda (arriba): no sube más.
        assertEquals(Cursor(0, 0, 1), TabKeyboardInput.moveVertical(Cursor(0, 0, 1), 6, -1))
        // No baja más allá de la última cuerda.
        assertEquals(Cursor(0, 0, 6), TabKeyboardInput.moveVertical(Cursor(0, 0, 6), 6, 1))
    }

    // ── Búfer de traste ───────────────────────────────────────────────────────────

    @Test
    fun appendDigit_buildsTwoDigitFrets() {
        val first = TabKeyboardInput.appendDigit("", 1, maxFret)
        assertEquals("1", first)
        assertEquals("12", TabKeyboardInput.appendDigit(first!!, 2, maxFret))
    }

    @Test
    fun appendDigit_rejectsOutOfRangeCombination() {
        // 25 no es un traste válido: el dígito debe empezar un búfer nuevo.
        assertNull(TabKeyboardInput.appendDigit("2", 5, maxFret))
    }

    @Test
    fun appendDigit_afterOpenString_startsFresh() {
        // «0» no continúa: teclear 5 tras 0 debe dar 5, no 05.
        assertEquals("5", TabKeyboardInput.appendDigit("0", 5, maxFret))
    }

    @Test
    fun appendDigit_acceptsUpToMaxFret() {
        assertEquals("24", TabKeyboardInput.appendDigit("2", 4, maxFret))
        assertNull(TabKeyboardInput.appendDigit("2", 5, maxFret))
    }

    @Test
    fun fretOf_readsTheBuffer() {
        assertEquals(12, TabKeyboardInput.fretOf("12"))
        assertEquals(0, TabKeyboardInput.fretOf("0"))
        assertNull(TabKeyboardInput.fretOf(""))
    }
}
