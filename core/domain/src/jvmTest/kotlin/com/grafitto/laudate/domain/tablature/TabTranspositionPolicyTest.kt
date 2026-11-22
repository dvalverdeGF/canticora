package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreKey
import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la política de transposición (regla de oro del redigitado). */
class TabTranspositionPolicyTest {

    private fun tab(origin: TabOrigin, tuning: Tuning = Tuning.GUITAR): Tablature = Tablature(
        id = "t1",
        songId = "song-1",
        title = "Prueba",
        instrument = tuning.name,
        tuning = tuning,
        time = ScoreTimeSignature("4", 4),
        origin = origin,
        writtenKey = ScoreKey(0, ScoreKey.MAJOR),
        measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(strings = listOf(TabPluck(1, 0))),
                    TabSlot(strings = listOf(TabPluck(1, 3)))
                )
            )
        )
    )

    @Test
    fun generatedTablature_refingersByDefault() {
        assertEquals(
            TabTranspositionMode.REFINGER,
            TabTranspositionPolicy.defaultMode(tab(TabOrigin.GENERATED_FROM_SCORE))
        )
    }

    @Test
    fun handwrittenGuitar_usesCapoByDefault() {
        assertEquals(
            TabTranspositionMode.CAPO,
            TabTranspositionPolicy.defaultMode(tab(TabOrigin.CREATED, Tuning.GUITAR))
        )
    }

    @Test
    fun handwrittenLute_isLockedByDefault() {
        // El laúd no admite cejilla: se bloquea (se ofrece redigitar con aviso).
        assertEquals(
            TabTranspositionMode.LOCKED,
            TabTranspositionPolicy.defaultMode(tab(TabOrigin.CREATED, Tuning.LUTE))
        )
    }

    @Test
    fun refingerOfHandwritten_requiresConfirmation() {
        val handwritten = tab(TabOrigin.CREATED, Tuning.LUTE)
        assertTrue(TabTranspositionPolicy.requiresConfirmation(handwritten, TabTranspositionMode.REFINGER))
        // Un modo que no redistribuye no pide confirmación.
        assertFalse(TabTranspositionPolicy.requiresConfirmation(handwritten, TabTranspositionMode.LOCKED))
        // Una tablatura generada no es decisión del usuario: no hace falta confirmar.
        assertFalse(
            TabTranspositionPolicy.requiresConfirmation(
                tab(TabOrigin.GENERATED_FROM_SCORE), TabTranspositionMode.REFINGER
            )
        )
    }

    @Test
    fun capoMode_leavesPositionsUntouched() {
        val original = tab(TabOrigin.CREATED, Tuning.GUITAR)
        val result = TabTranspositionPolicy.applyCapo(original)
        assertEquals(original.measures, result.measures)
    }

    @Test
    fun refinger_reassignsPositions_forNewPitches() {
        // La misma frase tocada una segunda arriba exige nuevos trastes.
        val original = tab(TabOrigin.GENERATED_FROM_SCORE)
        val newPitches = listOf(ScorePitch('F', 1, 4), ScorePitch('A', 1, 4))
        val result = TabTranspositionPolicy.refinger(original, newPitches, FingeringOptions(tuning = Tuning.GUITAR))
        assertEquals(2, result.measures[0].slots.size)
        // Las posiciones cambian (el La#4 no es el traste 3 de la 1ª).
        val newSecond = result.measures[0].slots[1].strings.first()
        assertTrue(newSecond.fret != 3 || newSecond.stringNumber != 1)
        assertEquals(TabOrigin.GENERATED_FROM_SCORE, result.origin)
    }

    @Test
    fun refinger_preservesTechniqueAndMarks() {
        // El redigitado solo reescribe (cuerda, traste): NUNCA borra el trémolo ni las marcas
        // del manuscrito (repetición/volta), que son del documento (RF-127).
        val base = tab(TabOrigin.CREATED)
        val withMarks = base.copy(
            measures = listOf(
                base.measures[0].copy(
                    slots = listOf(
                        TabSlot(
                            strings = listOf(TabPluck(1, 0, com.grafitto.laudate.domain.model.tablature.TabTechnique.TREMOLO)),
                            rhythmicFigure = RhythmFigure.DOTTED_QUARTER,
                            tuplet = TabTuplet.TRIPLET
                        ),
                        TabSlot(strings = listOf(TabPluck(1, 3, com.grafitto.laudate.domain.model.tablature.TabTechnique.HAMMER)))
                    ),
                    repeat = com.grafitto.laudate.domain.model.tablature.TabRepeatMark.START,
                    ending = 1,
                    repeatCount = 3
                )
            )
        )
        val result = TabTranspositionPolicy.refinger(
            withMarks,
            listOf(ScorePitch('E', 0, 4), ScorePitch('G', 0, 4)),
            FingeringOptions(tuning = Tuning.GUITAR)
        )
        assertEquals(
            com.grafitto.laudate.domain.model.tablature.TabTechnique.TREMOLO,
            result.measures[0].slots[0].strings[0].technique
        )
        assertEquals(
            com.grafitto.laudate.domain.model.tablature.TabTechnique.HAMMER,
            result.measures[0].slots[1].strings[0].technique
        )
        assertEquals(com.grafitto.laudate.domain.model.tablature.TabRepeatMark.START, result.measures[0].repeat)
        assertEquals(1, result.measures[0].ending)
        assertEquals(3, result.measures[0].repeatCount)
        assertEquals(RhythmFigure.DOTTED_QUARTER, result.measures[0].slots[0].rhythmicFigure)
        assertTrue(result.measures[0].slots[0].isTriplet)
    }

    @Test
    fun semitonesFromWrittenKey_measuresSavedKeyChange() {
        // Tablatura escrita en Do (fifths = 0): si el canto se guardó en Re, hay +2 semitonos.
        val inC = tab(TabOrigin.CREATED).copy(writtenKey = ScoreKey(0, ScoreKey.MAJOR))
        assertEquals(2, TabTranspositionPolicy.semitonesFromWrittenKey(inC, "D"))
        assertEquals(0, TabTranspositionPolicy.semitonesFromWrittenKey(inC, "C"))
        // De Do a Si baja medio tono (−1, el intervalo más corto).
        assertEquals(-1, TabTranspositionPolicy.semitonesFromWrittenKey(inC, "B"))
        // Sin tonalidad escrita no se sigue el cambio (compatibilidad con tablaturas antiguas).
        assertEquals(0, TabTranspositionPolicy.semitonesFromWrittenKey(tab(TabOrigin.CREATED).copy(writtenKey = null), "D"))
    }
}
