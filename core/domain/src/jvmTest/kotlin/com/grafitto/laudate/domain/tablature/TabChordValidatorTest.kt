package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **F-08 · validación de acordes**: el validador avisa de lo que no se puede tocar sin impedir
 * escribirlo.
 */
class TabChordValidatorTest {

    private fun slot(vararg plucks: TabPluck) = TabSlot(
        strings = plucks.toList(),
        rhythmicFigure = RhythmFigure.QUARTER
    )

    private fun note(string: Int, fret: Int) = TabPluck(TabPosition(string, fret))

    /** Acorde a partir de pares `cuerda, traste, cuerda, traste…`. */
    private fun chord(vararg pairs: Int): TabSlot {
        require(pairs.size % 2 == 0) { "hacen falta pares cuerda/traste" }
        return slot(*pairs.toList().chunked(2).map { note(it[0], it[1]) }.toTypedArray())
    }

    private fun tablature(vararg slots: TabSlot) = Tablature(
        id = "t",
        songId = "s",
        title = "Validación",
        instrument = Tuning.GUITAR.name,
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.QUARTER,
        measures = listOf(TabMeasure(slots = slots.toList())),
        origin = TabOrigin.CREATED
    )

    // ── Casos correctos ─────────────────────────────────────────────────────────────────────────

    @Test
    fun unAcordeNormalNoTieneAvisos() {
        val issues = TabChordValidator.validateSlot(
            chord(1, 0, 2, 1, 3, 0, 4, 2, 5, 3, 6, 0),
            Tuning.GUITAR
        )
        assertTrue("un Do mayor no debería tener avisos: $issues", issues.isEmpty())
    }

    @Test
    fun unaCasillaVaciaNoTieneAvisos() {
        assertTrue(TabChordValidator.validateSlot(slot(), Tuning.GUITAR).isEmpty())
    }

    @Test
    fun unaTablaturaCorrectaDevuelveLaListaVacia() {
        assertTrue(TabChordValidator.validate(tablature(chord(1, 0, 2, 1, 3, 0))).isEmpty())
    }

    // ── Cuerda repetida ─────────────────────────────────────────────────────────────────────────

    @Test
    fun dosNotasEnLaMismaCuerdaSonUnAviso() {
        val issues = TabChordValidator.validateSlot(chord(1, 0, 1, 3), Tuning.GUITAR)
        assertEquals(1, issues.size)
        assertEquals(TabChordIssueKind.DUPLICATE_STRING, issues.single().kind)
        assertEquals(1, issues.single().stringNumber)
    }

    @Test
    fun laCuerdaRepetidaSeAvisaUnaSolaVezPorCuerda() {
        val issues = TabChordValidator.validateSlot(chord(2, 0, 2, 1, 2, 2), Tuning.GUITAR)
        assertEquals(1, issues.count { it.kind == TabChordIssueKind.DUPLICATE_STRING })
    }

    // ── Cuerda inexistente ──────────────────────────────────────────────────────────────────────

    @Test
    fun unaCuerdaQueNoExisteEnLaAfinacionEsUnAviso() {
        val issues = TabChordValidator.validateSlot(chord(7, 0), Tuning.GUITAR)
        assertEquals(1, issues.size)
        assertEquals(TabChordIssueKind.STRING_OUT_OF_RANGE, issues.single().kind)
        assertTrue(issues.single().message.contains("no tiene cuerda 7"))
    }

    @Test
    fun laCuerdaCeroTampocoExiste() {
        val issues = TabChordValidator.validateSlot(chord(0, 0), Tuning.GUITAR)
        assertEquals(TabChordIssueKind.STRING_OUT_OF_RANGE, issues.single().kind)
    }

    @Test
    fun unBajoDeCuatroCuerdasNoAdmiteLaQuinta() {
        val issues = TabChordValidator.validateSlot(chord(5, 0), Tuning.BASS_4)
        assertEquals(TabChordIssueKind.STRING_OUT_OF_RANGE, issues.single().kind)
    }

    // ── Traste fuera del mástil ─────────────────────────────────────────────────────────────────

    @Test
    fun unTrastePorEncimaDelMaximoEsUnAviso() {
        val issues = TabChordValidator.validateSlot(chord(1, 25), Tuning.GUITAR)
        assertEquals(1, issues.size)
        assertEquals(TabChordIssueKind.FRET_OUT_OF_RANGE, issues.single().kind)
    }

    @Test
    fun unTrasteNegativoEsUnAviso() {
        val issues = TabChordValidator.validateSlot(chord(1, -1), Tuning.GUITAR)
        assertEquals(TabChordIssueKind.FRET_OUT_OF_RANGE, issues.single().kind)
    }

    @Test
    fun elTrasteMaximoYElCeroSonValidos() {
        assertTrue(TabChordValidator.validateSlot(chord(1, 0), Tuning.GUITAR).isEmpty())
        // El límite por defecto es el mástil del instrumento (19 trastes en la guitarra), no una
        // constante global: es el `m_frets` de MuseScore.
        assertTrue(TabChordValidator.validateSlot(chord(1, Tuning.GUITAR.frets), Tuning.GUITAR).isEmpty())
        assertEquals(
            TabChordIssueKind.FRET_OUT_OF_RANGE,
            TabChordValidator.validateSlot(chord(1, Tuning.GUITAR.frets + 1), Tuning.GUITAR)
                .single().kind
        )
    }

    @Test
    fun elMaximoDeTrastesSePuedeBajar() {
        val issues = TabChordValidator.validateSlot(chord(1, 12), Tuning.GUITAR, maxFret = 9)
        assertEquals(TabChordIssueKind.FRET_OUT_OF_RANGE, issues.single().kind)
    }

    // ── Posición inalcanzable ───────────────────────────────────────────────────────────────────

    @Test
    fun unPalmoDeCuatroTrastesEsTocable() {
        assertTrue(TabChordValidator.validateSlot(chord(1, 1, 2, 5), Tuning.GUITAR).isEmpty())
    }

    @Test
    fun unPalmoDeCincoTrastesYaNoEsTocable() {
        val issues = TabChordValidator.validateSlot(chord(1, 1, 2, 6), Tuning.GUITAR)
        assertEquals(1, issues.size)
        assertEquals(TabChordIssueKind.IMPOSSIBLE_STRETCH, issues.single().kind)
        assertTrue(issues.single().message.contains("5 trastes"))
    }

    @Test
    fun lasNotasAlAireNoCuentanParaElPalmo() {
        // Traste 0 y traste 12: el 0 no se pisa, así que no hay palmo que medir.
        assertTrue(TabChordValidator.validateSlot(chord(1, 0, 2, 12), Tuning.GUITAR).isEmpty())
    }

    @Test
    fun unaCuerdaApagadaConTrasteNoCuentaParaElPalmo() {
        // El aviso es el de la `X` con traste, **no** el del palmo: una cuerda apagada no se pisa.
        val muted = TabPluck(TabPosition(2, 12), techniques = setOf(TabTechnique.MUTED))
        val issues = TabChordValidator.validateSlot(slot(note(1, 1), muted), Tuning.GUITAR)
        assertEquals(1, issues.size)
        assertEquals(TabChordIssueKind.MUTED_WITH_FRET, issues.single().kind)
    }

    @Test
    fun unaSolaNotaPisadaNuncaEsUnPalmo() {
        assertTrue(TabChordValidator.validateSlot(chord(1, 19), Tuning.GUITAR).isEmpty())
    }

    // ── Cuerda apagada con traste ──────────────────────────────────────────────────────────────

    @Test
    fun unaCuerdaApagadaConTrasteEsUnAviso() {
        val muted = TabPluck(TabPosition(3, 5), techniques = setOf(TabTechnique.MUTED))
        val issues = TabChordValidator.validateSlot(slot(muted), Tuning.GUITAR)
        assertEquals(1, issues.size)
        assertEquals(TabChordIssueKind.MUTED_WITH_FRET, issues.single().kind)
    }

    @Test
    fun unaCuerdaApagadaEnElTrasteCeroEsCorrecta() {
        val muted = TabPluck(TabPosition(3, 0), techniques = setOf(TabTechnique.MUTED))
        assertTrue(TabChordValidator.validateSlot(slot(muted), Tuning.GUITAR).isEmpty())
    }

    // ── Recorrido de la tablatura ──────────────────────────────────────────────────────────────

    @Test
    fun losAvisosLlevanElCompasYLaColumna() {
        val tab = tablature(chord(1, 0), chord(1, 0, 1, 2))
        val issues = TabChordValidator.validate(tab)
        assertEquals(1, issues.size)
        assertEquals(0, issues.single().measureIndex)
        assertEquals(1, issues.single().slotIndex)
    }

    @Test
    fun bySlotAgrupaLosAvisosPorColumna() {
        val tab = tablature(chord(1, 0, 1, 2), chord(1, 0))
        val bySlot = TabChordValidator.bySlot(tab)
        assertEquals(1, bySlot.size)
        assertTrue(bySlot.containsKey(0 to 0))
    }

    @Test
    fun issueCountCuentaColumnasYNoAvisos() {
        // Una columna con dos problemas (cuerda repetida y traste fuera) cuenta como **una**.
        val tab = tablature(chord(1, 0, 1, 30))
        assertEquals(1, TabChordValidator.issueCount(tab))
        assertEquals(2, TabChordValidator.validate(tab).size)
    }

    @Test
    fun hasIssuesResumeLaTablatura() {
        assertFalse(TabChordValidator.hasIssues(tablature(chord(1, 0)).measures, Tuning.GUITAR))
        assertTrue(TabChordValidator.hasIssues(tablature(chord(1, 0, 1, 1)).measures, Tuning.GUITAR))
    }

    @Test
    fun isPlayableEsElAtajoDeLaColumna() {
        assertTrue(TabChordValidator.isPlayable(chord(1, 0, 2, 1), Tuning.GUITAR))
        assertFalse(TabChordValidator.isPlayable(chord(1, 0, 1, 1), Tuning.GUITAR))
    }

    @Test
    fun elPalmoMaximoEsElDeLaMano() {
        assertEquals(4, TabChordValidator.MAX_HAND_SPAN_FRETS)
    }
}
