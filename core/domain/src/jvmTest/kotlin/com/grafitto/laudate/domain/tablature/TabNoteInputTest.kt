package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Colocación de una nota por su nombre** ([TabNoteInput]): una letra de nota (`C D E F G A B`) se
 * coloca sola en la cuerda y el traste que le corresponden, y `Ctrl+↑/↓` mueve la nota de cuerda sin
 * cambiar su altura.
 *
 * Desde el **ADR-020** esto **ya no es un atajo de teclado** (en MuseScore `A`–`K` escriben **trastes**,
 * en tablatura francesa): [TabNoteInput] se conserva como capacidad de Dominio —el redigitado y el
 * `Ctrl`+`↑`/`↓` la usan— y se prueba aquí.
 */
class TabNoteInputTest {

    private val guitar = Tuning.GUITAR

    @Test
    fun isNoteLetter_aceptaLasSieteNotas() {
        listOf('C', 'D', 'E', 'F', 'G', 'A', 'B', 'c', 'd').forEach {
            assertTrue("«$it» debe ser una nota", TabNoteInput.isNoteLetter(it))
        }
        listOf('H', 'X', 'Z', '1').forEach {
            assertTrue("«$it» NO debe ser una nota", !TabNoteInput.isNoteLetter(it))
        }
    }

    @Test
    fun placeLetter_colocaLaNotaEnUnaCuerdaYTrasteValidos() {
        val pluck = TabNoteInput.placeLetter('E', guitar)
        assertTrue("la nota Mi debe colocarse", pluck != null)
        val string = pluck!!.stringNumber
        val fret = pluck.fret
        assertTrue("cuerda válida", string in 1..guitar.stringCount)
        assertTrue("traste válido", fret in 0..MAX_FRET)
        // Suena como un Mi (índice cromático 4) en alguna octava.
        val open = guitar.pitchOf(string)!!
        val sounded = if (fret == 0) open.midi else open.midi + fret
        assertEquals("debe sonar un Mi", 4, (((sounded % 12) + 12) % 12))
    }

    @Test
    fun placeLetter_cadaNotaSueñaSuAltura() {
        // Índices cromáticos de Do..Si.
        val expected = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)
        expected.forEach { (letter, chromatic) ->
            val pluck = TabNoteInput.placeLetter(letter, guitar)
            assertTrue("la nota $letter debe colocarse", pluck != null)
            val open = guitar.pitchOf(pluck!!.stringNumber)!!
            val sounded = if (pluck.fret == 0) open.midi else open.midi + pluck.fret
            assertEquals("«$letter» debe sonar su altura", chromatic, (((sounded % 12) + 12) % 12))
        }
    }

    @Test
    fun placeLetter_letraInvalidaDevuelveNull() {
        assertNull(TabNoteInput.placeLetter('H', guitar))
        assertNull(TabNoteInput.placeLetter('1', guitar))
    }

    @Test
    fun candidatePitches_caenDentroDelRangoTocable() {
        val range = TabNoteInput.playableRange(guitar)
        val candidates = TabNoteInput.candidatePitches('C', guitar)
        assertTrue("debe haber octavas candidatas", candidates.isNotEmpty())
        candidates.forEach { assertTrue("dentro del rango", it.midi in range) }
        // La primera es la más centrada (cerca de la mitad del mástil).
        val center = (range.first + range.last) / 2
        assertEquals(candidates.minByOrNull { kotlin.math.abs(it.midi - center) }, candidates.first())
    }

    @Test
    fun moveToAdjacentString_conservaLaAltura() {
        // Mi de la 1ª cuerda al aire (traste 0) → al subir a la 2ª cuerda es el traste 5.
        val pluck = TabNoteInput.placeLetter('E', guitar)!!
        val moved = TabNoteInput.moveToAdjacentString(pluck, guitar, direction = 1)
        assertTrue(moved != null)
        assertEquals("cambia de cuerda", pluck.stringNumber + 1, moved!!.stringNumber)
        val before = guitar.pitchOf(pluck.stringNumber)!!.midi + pluck.fret
        val after = guitar.pitchOf(moved.stringNumber)!!.midi + moved.fret
        assertEquals("la altura no cambia", before, after)
    }

    @Test
    fun moveToAdjacentString_sinCuerdaAdyacenteDevuelveNull() {
        // La 6ª cuerda es la más grave: no hay 7ª.
        val pluck = com.grafitto.laudate.domain.model.tablature.TabPluck(6, 0)
        assertNull(TabNoteInput.moveToAdjacentString(pluck, guitar, direction = 1))
    }

    @Test
    fun moveToAdjacentString_saltoGrandeNoCabe() {
        // Una nota muy aguda en la 1ª cuerda no cabe en la 2ª (necesitaría un traste enorme).
        val pluck = com.grafitto.laudate.domain.model.tablature.TabPluck(1, 15)
        assertNull(TabNoteInput.moveToAdjacentString(pluck, guitar, direction = 1, maxFret = 12))
    }

    @Test
    fun placePitch_colocaUnaAlturaConcreta() {
        val pluck = TabNoteInput.placePitch(ScorePitch('G', 0, 3), guitar)
        assertTrue(pluck != null)
        val open = guitar.pitchOf(pluck!!.stringNumber)!!
        val sounded = open.midi + pluck.fret
        assertEquals("debe sonar un Sol3", ScorePitch('G', 0, 3).midi, sounded)
    }
}
