package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Mapa de teclado del editor de tablatura (ADR-020), contra el manual de MuseScore Studio 4
 * (`appendix/all-keyboard-shortcuts.md`, sección *Note input ▸ Tablature*).
 *
 * Cada test cita la entrada del manual de la que sale el comportamiento.
 */
class TabShortcutsTest {

    // ── Entrada de trastes ────────────────────────────────────────────────────────

    @Test
    fun fretDigit_readsTheNumberRow() {
        // Manual: «Enter TAB: fret — 0 – 9».
        for (digit in 0..9) {
            assertEquals(digit, TabShortcuts.fretDigit('0' + digit))
        }
        assertNull(TabShortcuts.fretDigit('a'))
        assertNull(TabShortcuts.fretDigit(' '))
    }

    @Test
    fun fretLetterDigit_maps_A_to_K_skipping_I() {
        // Manual: «Fret numbers 0 to 9 can also be entered with the letter keys A to K, skipping I».
        assertEquals(0, TabShortcuts.fretLetterDigit('A'))
        assertEquals(1, TabShortcuts.fretLetterDigit('B'))
        assertEquals(2, TabShortcuts.fretLetterDigit('C'))
        assertEquals(3, TabShortcuts.fretLetterDigit('D'))
        assertEquals(4, TabShortcuts.fretLetterDigit('E'))
        assertEquals(5, TabShortcuts.fretLetterDigit('F'))
        assertEquals(6, TabShortcuts.fretLetterDigit('G'))
        assertEquals(7, TabShortcuts.fretLetterDigit('H'))
        // La «I» se salta: la «J» es el 8.
        assertNull(TabShortcuts.fretLetterDigit('I'))
        assertEquals(8, TabShortcuts.fretLetterDigit('J'))
        assertEquals(9, TabShortcuts.fretLetterDigit('K'))
        // Fuera del rango A–K no hay traste.
        assertNull(TabShortcuts.fretLetterDigit('L'))
        assertNull(TabShortcuts.fretLetterDigit('Z'))
    }

    @Test
    fun fretLetterDigit_isCaseInsensitive() {
        assertEquals(0, TabShortcuts.fretLetterDigit('a'))
        assertEquals(9, TabShortcuts.fretLetterDigit('k'))
    }

    // ── Duración ──────────────────────────────────────────────────────────────────

    @Test
    fun shiftDigitFigure_mapsTheTabDurationKeys() {
        // Manual: «Set duration (TAB): Shift + 0 – Shift + 9» (128ª en 0, subiendo una figura por
        // dígito). Canticora solo tiene las figuras que modela.
        assertEquals(RhythmFigure.SIXTEENTH, TabShortcuts.shiftDigitFigure('3'))
        assertEquals(RhythmFigure.EIGHTH, TabShortcuts.shiftDigitFigure('4'))
        assertEquals(RhythmFigure.QUARTER, TabShortcuts.shiftDigitFigure('5'))
        assertEquals(RhythmFigure.HALF, TabShortcuts.shiftDigitFigure('6'))
        assertEquals(RhythmFigure.WHOLE, TabShortcuts.shiftDigitFigure('7'))
        // Figuras que el modelo no tiene (128ª, 64ª, 32ª, longa…) no cambian nada.
        assertNull(TabShortcuts.shiftDigitFigure('0'))
        assertNull(TabShortcuts.shiftDigitFigure('1'))
        assertNull(TabShortcuts.shiftDigitFigure('2'))
        assertNull(TabShortcuts.shiftDigitFigure('8'))
        assertNull(TabShortcuts.shiftDigitFigure('9'))
    }

    @Test
    fun shorterAndLonger_walkTheFigureScale() {
        // Manual: «Halve selected duration — Q», «Double selected duration — W».
        assertEquals(RhythmFigure.SIXTEENTH, TabShortcuts.shorter(RhythmFigure.EIGHTH))
        assertEquals(RhythmFigure.EIGHTH, TabShortcuts.shorter(RhythmFigure.QUARTER))
        assertEquals(RhythmFigure.QUARTER, TabShortcuts.shorter(RhythmFigure.HALF))
        assertEquals(RhythmFigure.HALF, TabShortcuts.shorter(RhythmFigure.WHOLE))

        assertEquals(RhythmFigure.EIGHTH, TabShortcuts.longer(RhythmFigure.SIXTEENTH))
        assertEquals(RhythmFigure.QUARTER, TabShortcuts.longer(RhythmFigure.EIGHTH))
        assertEquals(RhythmFigure.HALF, TabShortcuts.longer(RhythmFigure.QUARTER))
        assertEquals(RhythmFigure.WHOLE, TabShortcuts.longer(RhythmFigure.HALF))
    }

    @Test
    fun shorterAndLonger_stopAtTheEnds() {
        assertNull(TabShortcuts.shorter(RhythmFigure.SIXTEENTH))
        assertNull(TabShortcuts.longer(RhythmFigure.WHOLE))
    }

    @Test
    fun shorterAndLonger_stripTheDot() {
        // Una figura con puntillo se «acorta» a su versión simple antes de bajar de figura.
        assertEquals(RhythmFigure.QUARTER, TabShortcuts.shorter(RhythmFigure.DOTTED_QUARTER))
        assertEquals(RhythmFigure.EIGHTH, TabShortcuts.shorter(RhythmFigure.DOTTED_EIGHTH))
        assertEquals(RhythmFigure.HALF, TabShortcuts.shorter(RhythmFigure.DOTTED_HALF))
    }

    @Test
    fun toggleDot_addsAndRemovesTheDot() {
        // Manual: «Toggle duration dot — .».
        assertEquals(RhythmFigure.DOTTED_QUARTER, TabShortcuts.toggleDot(RhythmFigure.QUARTER))
        assertEquals(RhythmFigure.QUARTER, TabShortcuts.toggleDot(RhythmFigure.DOTTED_QUARTER))
        assertEquals(RhythmFigure.DOTTED_HALF, TabShortcuts.toggleDot(RhythmFigure.HALF))
        assertEquals(RhythmFigure.HALF, TabShortcuts.toggleDot(RhythmFigure.DOTTED_HALF))
        assertEquals(RhythmFigure.DOTTED_EIGHTH, TabShortcuts.toggleDot(RhythmFigure.EIGHTH))
        assertEquals(RhythmFigure.EIGHTH, TabShortcuts.toggleDot(RhythmFigure.DOTTED_EIGHTH))
    }

    @Test
    fun toggleDot_doesNothingWhereThereIsNoDottedFigure() {
        // La **semicorchea con puntillo no existe** en el modelo (a propósito): la tecla no hace nada.
        assertNull(TabShortcuts.toggleDot(RhythmFigure.SIXTEENTH))
        // Un silencio tampoco tiene puntillo que alternar aquí.
        assertNull(TabShortcuts.toggleDot(RhythmFigure.QUARTER_REST))
    }

    // ── Edición del traste ────────────────────────────────────────────────────────

    private val guitar = Tuning.GUITAR

    @Test
    fun incrementFret_doesNotChangeTheString() {
        // Manual: «To increment or decrement, without changing the string: Press Up/Down».
        val pluck = TabPluck(3, 5)
        val up = TabShortcuts.incrementFret(pluck, 1)
        assertNotNull(up)
        assertEquals("la cuerda no cambia", 3, up!!.stringNumber)
        assertEquals(6, up.fret)
        val down = TabShortcuts.incrementFret(pluck, -1)
        assertNotNull(down)
        assertEquals(3, down!!.stringNumber)
        assertEquals(4, down.fret)
    }

    @Test
    fun incrementFret_stopsAtTheEnds() {
        assertNull("no hay trastes negativos", TabShortcuts.incrementFret(TabPluck(1, 0), -1))
        assertNull("no pasa del traste máximo", TabShortcuts.incrementFret(TabPluck(1, MAX_FRET), 1))
        assertNull("sin desplazamiento no hay cambio", TabShortcuts.incrementFret(TabPluck(1, 5), 0))
    }

    @Test
    fun moveFretDiatonic_changesTheStringToKeepTheFretLow() {
        // Manual: «Alt+Shift+Up/Down — The fret mark automatically changes strings, if necessary, to
        // achieve the lowest possible fret position».
        val pluck = TabPluck(1, 0) // Mi agudo al aire (MIDI 64, E4)
        val up = TabShortcuts.moveFretDiatonic(pluck, guitar, 1) // sube un semitono (F4)
        assertNotNull(up)
        // F4 está en la 1.ª cuerda en el traste 1, que es la posición más baja posible.
        assertEquals(1, up!!.stringNumber)
        assertEquals(1, up.fret)
    }

    @Test
    fun moveFretDiatonic_prefersTheLowestFretAcrossStrings() {
        // Fa4 (MIDI 65) en la 3.ª cuerda, traste 10. Al **bajar** un semitono sale un Mi4 (MIDI 64),
        // que en la guitarra es la 1.ª cuerda **al aire**: es el traste más bajo posible, así que la
        // nota salta de la 3.ª a la 1.ª cuerda (manual: «changes strings, if necessary, to achieve the
        // lowest possible fret position»).
        val fa = TabPluck(3, 10)
        val moved = TabShortcuts.moveFretDiatonic(fa, guitar, -1)
        assertNotNull(moved)
        assertEquals(1, moved!!.stringNumber)
        assertEquals(0, moved.fret)
    }

    @Test
    fun moveFretDiatonic_withoutDirection_doesNothing() {
        assertNull(TabShortcuts.moveFretDiatonic(TabPluck(1, 5), guitar, 0))
    }

    @Test
    fun moveFretDiatonic_returnsNullWhenItDoesNotFit() {
        // La nota más aguda representable (MIDI 127) no puede subir un semitono más: no cabe en ninguna
        // cuerda y la operación no hace nada.
        val open = guitar.pitchOf(1)?.midi ?: 64
        val top = TabPluck(1, 127 - open)
        assertNull(TabShortcuts.moveFretDiatonic(top, guitar, 1))
    }

    // ── Colores de voz ────────────────────────────────────────────────────────────

    @Test
    fun voiceColors_matchTheMuseScoreSourceValues() {
        // Medidos de `src/engraving/internal/engravingconfiguration.cpp` (DEFAULT_VOICE_COLORS).
        assertEquals(0xFF0065BF.toInt(), TabVoiceColors.VOICE_1)
        assertEquals(0xFF007F00.toInt(), TabVoiceColors.VOICE_2)
        assertEquals(0xFFC53F00.toInt(), TabVoiceColors.VOICE_3)
        assertEquals(0xFFC31989.toInt(), TabVoiceColors.VOICE_4)
        assertEquals(0xFF6038FC.toInt(), TabVoiceColors.ALL_VOICES)
    }

    @Test
    fun voiceColorOf_returnsTheColorOfEachVoice() {
        assertEquals(TabVoiceColors.VOICE_1, TabVoiceColors.of(1))
        assertEquals(TabVoiceColors.VOICE_2, TabVoiceColors.of(2))
        assertEquals(TabVoiceColors.VOICE_3, TabVoiceColors.of(3))
        assertEquals(TabVoiceColors.VOICE_4, TabVoiceColors.of(4))
        // Fuera de rango cae en la voz 1, la de escritura por defecto.
        assertEquals(TabVoiceColors.VOICE_1, TabVoiceColors.of(0))
        assertEquals(TabVoiceColors.VOICE_1, TabVoiceColors.of(9))
    }

    @Test
    fun cursorAlpha_isTheMuseScoreTranslucentValue() {
        // MuseScore pinta el rectángulo del cursor con alfa 80 sobre 255 (`dropRectColor`).
        assertEquals(80f / 255f, TabVoiceColors.CURSOR_ALPHA)
    }
}
