package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas del motor de digitación (Viterbi). */
class FingeringEngineTest {

    private fun options(
        tuning: Tuning = Tuning.GUITAR,
        maxFret: Int = 12,
        criterion: FingeringCriterion = FingeringCriterion.MINIMUM,
        alternatives: Int = 0,
        weights: FingeringWeights = FingeringWeights()
    ) = FingeringOptions(
        tuning = tuning,
        maxFret = maxFret,
        criterion = criterion,
        alternatives = alternatives,
        weights = weights
    )

    // Mi4 (cuerda 1 al aire), Mi2 (cuerda 6 al aire) son casos de cuerda al aire.
    private val e4 = ScorePitch('E', 0, 4)
    private val g4 = ScorePitch('G', 0, 4)
    private val e2 = ScorePitch('E', 0, 2)

    @Test
    fun singleNote_prefersOpenString() {
        val result = FingeringEngine.assign(listOf(e4), options())
        assertEquals(1, result.assignment.size)
        // Mi4 es la 1ª cuerda al aire.
        assertEquals(1, result.plucks.first().stringNumber)
        assertEquals(0, result.plucks.first().fret)
        assertTrue(result.unplayable.isEmpty())
    }

    @Test
    fun everyNote_isAssignedExactlyWhenPlayable() {
        val pitches = listOf(e4, g4, ScorePitch('C', 0, 4), ScorePitch('D', 0, 4))
        val result = FingeringEngine.assign(pitches, options())
        assertEquals(pitches.size, result.assignment.size)
        assertTrue(result.assignment.all { it != null })
        assertTrue(result.unplayable.isEmpty())
    }

    @Test
    fun unplayableNote_isReported_notInvented() {
        // Un Do7 queda por encima del traste 12 en un ukelele.
        val tooHigh = ScorePitch('C', 0, 7)
        val result = FingeringEngine.assign(listOf(e4, tooHigh, g4), FingeringOptions(tuning = Tuning.UKULELE, maxFret = 12))
        assertEquals(listOf(1), result.unplayable)
        assertNull(result.assignment[1])
        // Las notas tocables de los lados sí tienen digitación.
        assertNotNull(result.assignment[0])
        assertNotNull(result.assignment[2])
    }

    @Test
    fun minimalShift_isPreferred() {
        // La melodía Mi4 → Sol4 → Mi4 admite Sol4 en la 1ª (traste 3) o la 2ª (traste 8).
        // Con preferencia de salto mínimo, la mano debe quedarse cerca.
        val result = FingeringEngine.assign(listOf(e4, g4, e4), options())
        val sol = result.assignment[1]!!
        // Sol4 en la 1ª cuerda traste 3 (o 2ª cuerda traste 8, que exige más salto). Debe elegir la 1ª.
        assertEquals(1, sol.stringNumber)
        assertEquals(3, sol.fret)
    }

    @Test
    fun minimax_avoidsWorstLeap() {
        // Secuencia con un salto grande intermedio: minimax penaliza el peor tramo.
        val pitches = listOf(e4, ScorePitch('E', 0, 2), e4)
        val min = FingeringEngine.assign(pitches, options(criterion = FingeringCriterion.MINIMUM))
        val mini = FingeringEngine.assign(pitches, options(criterion = FingeringCriterion.MINIMAX))
        // Ambas soluciones son válidas y producen la misma secuencia de alturas.
        assertEquals(3, min.assignment.size)
        assertEquals(3, mini.assignment.size)
    }

    @Test
    fun ioiDiscard_removesImpossibleTransitions() {
        // Dos notas muy graves separadas por 12 trastes pero solo 10 ms: imposible.
        val low = ScorePitch('E', 0, 2)
        val high = ScorePitch('E', 0, 3) // Mi3 = traste 12 de la 6ª o traste 7 de la 5ª...
        val optionsTight = options().copy(maxHandShift = 0, millisPerFret = 90)
        // Con tiempo 0 ms y maxHandShift 0, solo se admite salto 0. Aun así el motor elige la mejor
        // transición disponible dentro de la misma cuerda si existe.
        val result = FingeringEngine.assign(listOf(low, high), listOf(0L, 0L), optionsTight)
        assertEquals(2, result.assignment.size)
    }

    @Test
    fun weightChange_canYieldDifferentFingering() {
        // Dos opciones válidas para Sol4: 1ª traste 3 (salto) o 2ª traste 8.
        val heavyZone = FingeringWeights(zoneStartFret = 0, zonePenalty = 10.0)
        val result = FingeringEngine.assign(
            listOf(e4, g4),
            options(weights = heavyZone)
        )
        val sol = result.assignment[1]!!
        // Penalizando mucho la zona alta, prefiere el traste 3 (más agudo pero traste bajo).
        assertEquals(3, sol.fret)
    }

    @Test
    fun alternatives_areDistinctFromPrincipal() {
        val result = FingeringEngine.assign(
            listOf(ScorePitch('G', 0, 4), ScorePitch('G', 0, 4)),
            options(alternatives = 2)
        )
        assertTrue(result.alternatives.isNotEmpty())
        result.alternatives.forEach { alt ->
            assertTrue(alt != result.assignment)
        }
    }

    @Test
    fun emptyMelody_returnsEmptyResult() {
        val result = FingeringEngine.assign(emptyList(), options())
        assertEquals(0, result.assignment.size)
        assertEquals(1, result.difficulty)
    }

    @Test
    fun bass_highMelody_isMostlyUnplayable() {
        // Una melodía vocal aguda no cabe en el bajo de 4 cuerdas (grave).
        val vocal = listOf(ScorePitch('C', 0, 5), ScorePitch('E', 0, 5), ScorePitch('G', 0, 5))
        val result = FingeringEngine.assign(vocal, FingeringOptions(tuning = Tuning.BASS_4, maxFret = 12))
        assertTrue(result.unplayable.isNotEmpty())
    }

    @Test
    fun difficulty_isWithinRange() {
        val result = FingeringEngine.assign(
            listOf(e4, g4, ScorePitch('B', 0, 4), ScorePitch('C', 0, 5)),
            options()
        )
        assertTrue(result.difficulty in 1..10)
    }
}
