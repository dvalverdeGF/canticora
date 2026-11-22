package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tuning

/**
 * Pesos del **motor de digitación** (ADR-017, *Feature T*). No son constantes mágicas: son
 * preferencias del músico (zona cómoda del mástil, tolerancia al salto…). Se exponen para poder
 * ofrecer alternativas en lugar de imponer una única solución.
 *
 * Taxonomía de coste (Bontempi et al., 2024):
 *  - **PC** *position change* (tiempo): salto de la mano a lo largo del mástil.
 *  - **SC** *string change* (tiempo): movimiento a través de las cuerdas.
 *  - **HS** *hand spread* (molestia): abertura entre dedos (cuerdas no adyacentes).
 *  - **Zona** (molestia): tocar cerca de la cejuela o muy lejos.
 *  - **Aire** (timbre): preferencia por cuerdas al aire.
 */
data class FingeringWeights(
    /** Coste por cada traste de salto de la mano. */
    val positionChange: Double = 1.0,
    /** Coste por cambiar de cuerda. */
    val stringChange: Double = 1.5,
    /** Coste por cada cuerda de más en una abertura de mano no adyacente. */
    val handSpread: Double = 2.0,
    /** Bonificación (se resta) por tocar una cuerda al aire. */
    val openStringBonus: Double = 1.0,
    /** Penalización por tocar en la zona incómoda del mástil (trastes altos). */
    val zonePenalty: Double = 0.6,
    /** A partir de este traste empieza la zona incómoda. */
    val zoneStartFret: Int = 9
)

/** Criterio de optimización del camino. */
enum class FingeringCriterion {
    /** Viterbi clásico: minimiza la dificultad **total** de la frase (Sayegh, 1989). */
    MINIMUM,
    /** Minimax: minimiza **el peor salto** (Hori & Sagayama, 2016); evita tramos injugables. */
    MINIMAX
}

/** Opciones del cálculo de digitación. */
data class FingeringOptions(
    val tuning: Tuning,
    /** Traste máximo considerado tocable. */
    val maxFret: Int = 12,
    /** Preferir cuerdas al aire (afecta al peso del aire). */
    val preferOpenStrings: Boolean = true,
    /** Salto máximo de la mano (en trastes) admitido sin tiempo para recolocar. */
    val maxHandShift: Int = 5,
    val weights: FingeringWeights = FingeringWeights(),
    val criterion: FingeringCriterion = FingeringCriterion.MINIMUM,
    /** Nº de digitaciones alternativas a devolver además de la principal (0 = solo la principal). */
    val alternatives: Int = 0,
    /** Milisegundos necesarios para cubrir un traste de desplazamiento (para el descarte por IOI). */
    val millisPerFret: Long = 90L
)

/**
 * Resultado del cálculo de digitación.
 *
 * [assignment] tiene **una entrada por nota** de la entrada; `null` cuando la nota no cabe en el
 * mástil (ver [unplayable]). Así el llamante puede alinear el resultado con la melodía sin perder
 * la posición de las notas intocables.
 */
data class FingeringResult(
    val assignment: List<TabPluck?>,
    /** Índices (sobre la entrada) de las notas que NO caben en el mástil. */
    val unplayable: List<Int>,
    /** Índice de dificultad 1..10 (orientativo). */
    val difficulty: Int,
    /** Digitaciones alternativas, alineadas con la entrada. La principal es [assignment]. */
    val alternatives: List<List<TabPluck?>> = emptyList()
) {
    /** Solo las pulsaciones calculadas, en orden, sin las notas intocables. */
    val plucks: List<TabPluck> get() = assignment.filterNotNull()
}

/**
 * **Motor de digitación** (ADR-017). Melodía (alturas) → cuerdas y trastes, por **programación
 * dinámica** (red de Viterbi / *Shortest Path Problem*, Sayegh 1989).
 *
 * Kotlin puro: determinista y testeable al 100 %. Sin estado mutable compartido.
 *
 * Cada nota es una capa; cada candidato `(cuerda, traste)` válido es un nodo. El coste de una
 * transición combina PC + SC + HS − aire + zona. Las transiciones **imposibles** se descartan por
 * **IOI** (no hay tiempo físico para el salto).
 */
object FingeringEngine {

    /** Coste "infinito" para transiciones prohibidas. */
    private const val INF = Double.MAX_VALUE / 4

    /** Penalización aplicada a los candidatos ya usados al buscar alternativas. */
    private const val ALTERNATIVE_PENALTY = 100.0

    /** Un candidato de digitación para una nota concreta. */
    private data class Candidate(val stringNumber: Int, val fret: Int)

    /** Clave de un nodo, para el mapa de penalización de alternativas. */
    private data class NodeKey(val noteIndex: Int, val stringNumber: Int, val fret: Int)

    // ===========================================================================================
    // API pública
    // ===========================================================================================

    /** Asigna cuerdas y trastes a una melodía sin información de tiempo (no aplica descarte por IOI). */
    fun assign(pitches: List<ScorePitch>, options: FingeringOptions): FingeringResult =
        assign(pitches, onsetsMillis = null, options = options)

    /**
     * Asigna cuerdas y trastes a una melodía.
     *
     * @param onsetsMillis tiempo (ms) de cada nota, para descartar transiciones imposibles por IOI.
     *   Si es `null` o su tamaño no coincide con `pitches`, no se aplica el descarte por tiempo.
     */
    fun assign(
        pitches: List<ScorePitch>,
        onsetsMillis: List<Long>?,
        options: FingeringOptions
    ): FingeringResult {
        if (pitches.isEmpty()) {
            return FingeringResult(assignment = emptyList(), unplayable = emptyList(), difficulty = 1)
        }

        val timing = onsetsMillis?.takeIf { it.size == pitches.size }
        val candidates = pitches.map { candidatesFor(it, options) }
        val unplayable = candidates.indices.filter { candidates[it].isEmpty() }
        val runs = playableRuns(candidates)

        val assignment = arrayOfNulls<TabPluck>(pitches.size)
        val metrics = Metrics()
        runs.forEach { layerIndices ->
            val plucks = viterbi(layerIndices, candidates, timing, options, emptyMap())
            for (i in layerIndices.indices) {
                assignment[layerIndices[i]] = plucks[i]
            }
            metrics.add(evaluate(layerIndices, plucks, timing, options))
        }

        val alternatives = alternativesFor(pitches, candidates, timing, options, assignment.toList())

        return FingeringResult(
            assignment = assignment.toList(),
            unplayable = unplayable,
            difficulty = difficultyOf(metrics.totalCost, pitches.size - unplayable.size, metrics.maxTransition),
            alternatives = alternatives
        )
    }

    // ===========================================================================================
    // Candidatos y tramos
    // ===========================================================================================

    private fun candidatesFor(pitch: ScorePitch, options: FingeringOptions): List<Candidate> {
        val targetMidi = pitch.midi
        val tuning = options.tuning
        val result = mutableListOf<Candidate>()
        // stringNumber de lectura: 1 = la más aguda.
        for (stringNumber in 1..tuning.stringCount) {
            val open = tuning.pitchOf(stringNumber) ?: continue
            val fret = targetMidi - open.midi
            if (fret in 0..options.maxFret) {
                result += Candidate(stringNumber, fret)
            }
        }
        return result
    }

    /** Tramos de índices de notas tocables (con al menos un candidato), separados por intocables. */
    private fun playableRuns(candidates: List<List<Candidate>>): List<List<Int>> {
        val runs = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        candidates.forEachIndexed { index, list ->
            if (list.isEmpty()) {
                if (current.isNotEmpty()) { runs += current; current = mutableListOf() }
            } else {
                current += index
            }
        }
        if (current.isNotEmpty()) runs += current
        return runs
    }

    // ===========================================================================================
    // Viterbi
    // ===========================================================================================

    /**
     * Resuelve un tramo por programación dinámica. Devuelve un [TabPluck] por capa (nota).
     *
     * @param penaltyByKey penaliza nodos concretos (se usa al buscar alternativas).
     */
    private fun viterbi(
        layers: List<Int>,
        candidates: List<List<Candidate>>,
        timing: List<Long>?,
        options: FingeringOptions,
        penaltyByKey: Map<NodeKey, Double>
    ): List<TabPluck> {
        val size = layers.size
        require(size > 0)
        val backPointers = Array(size) { IntArray(0) }
        var dpPrev = DoubleArray(0)
        var prevCaps: List<Candidate> = emptyList()

        layers.forEachIndexed { layerPos, noteIndex ->
            val caps = candidates[noteIndex]
            val dpCur = DoubleArray(caps.size)
            val bp = IntArray(caps.size) { -1 }

            caps.forEachIndexed { ci, c ->
                val penalty = penaltyByKey[NodeKey(noteIndex, c.stringNumber, c.fret)] ?: 0.0
                if (layerPos == 0) {
                    dpCur[ci] = entryCost(c, options) + penalty
                } else {
                    val prevNoteIndex = layers[layerPos - 1]
                    var best = INF
                    var bestPrev = -1
                    prevCaps.forEachIndexed { pi, pc ->
                        val t = transitionCost(pc, c, noteIndex, prevNoteIndex, timing, options)
                        if (t >= INF) return@forEachIndexed
                        val acc = if (options.criterion == FingeringCriterion.MINIMAX) {
                            maxOf(dpPrev[pi], t)
                        } else {
                            dpPrev[pi] + t
                        }
                        if (acc < best) { best = acc; bestPrev = pi }
                    }
                    dpCur[ci] = if (best >= INF) INF else best + penalty
                    bp[ci] = bestPrev
                }
            }
            backPointers[layerPos] = bp
            dpPrev = dpCur
            prevCaps = caps
        }

        var bestIdx = 0
        var bestVal = INF
        dpPrev.forEachIndexed { i, v -> if (v < bestVal) { bestVal = v; bestIdx = i } }

        val chosen = arrayOfNulls<Candidate>(size)
        var idx = bestIdx
        for (layerPos in (size - 1) downTo 0) {
            val noteIndex = layers[layerPos]
            chosen[layerPos] = candidates[noteIndex].getOrNull(idx)
            idx = backPointers[layerPos].getOrNull(idx) ?: -1
            if (idx < 0 && layerPos > 0) idx = 0
        }
        return chosen.map { c -> TabPluck(c!!.stringNumber, c.fret, TabTechnique.NONE) }
    }

    // ===========================================================================================
    // Costes
    // ===========================================================================================

    /** Coste de entrada (primera nota de un tramo): zona − aire. */
    private fun entryCost(c: Candidate, options: FingeringOptions): Double {
        val w = options.weights
        var cost = zonePenalty(c.fret, options)
        if (c.fret == 0 && options.preferOpenStrings) cost -= w.openStringBonus
        return cost
    }

    /**
     * Coste de pasar de [prev] a [cur].
     * Devuelve [INF] (transición eliminada) cuando el salto no cabe en el tiempo disponible (IOI).
     */
    private fun transitionCost(
        prev: Candidate,
        cur: Candidate,
        noteIndex: Int,
        prevIndex: Int,
        timing: List<Long>?,
        options: FingeringOptions
    ): Double {
        val w = options.weights
        val fretJump = kotlin.math.abs(cur.fret - prev.fret)

        // Descarte por IOI: con más tiempo, el salto admite más trastes.
        if (timing != null) {
            val dt = (timing[noteIndex] - timing[prevIndex]).coerceAtLeast(0)
            val coverable = options.maxHandShift + (dt / options.millisPerFret).toInt()
            if (fretJump > coverable) return INF
        }

        var cost = 0.0
        // PC · position change.
        cost += w.positionChange * fretJump
        // SC · string change.
        if (cur.stringNumber != prev.stringNumber) cost += w.stringChange
        // HS · hand spread (cuerdas no adyacentes).
        val stringGap = kotlin.math.abs(cur.stringNumber - prev.stringNumber)
        if (stringGap > 1) cost += w.handSpread * (stringGap - 1)
        // Aire (timbre).
        if (cur.fret == 0 && options.preferOpenStrings) cost -= w.openStringBonus
        // Zona (molestia).
        cost += zonePenalty(cur.fret, options)
        return cost
    }

    /** Penalización por tocar en la zona incómoda del mástil (trastes altos). */
    private fun zonePenalty(fret: Int, options: FingeringOptions): Double {
        val w = options.weights
        if (fret <= w.zoneStartFret) return 0.0
        return w.zonePenalty * (fret - w.zoneStartFret)
    }

    // ===========================================================================================
    // Métricas y dificultad
    // ===========================================================================================

    private class Metrics {
        var totalCost = 0.0
        var maxTransition = 0.0

        fun add(other: Metrics) {
            totalCost += other.totalCost
            maxTransition = maxOf(maxTransition, other.maxTransition)
        }
    }

    private fun evaluate(
        layers: List<Int>,
        plucks: List<TabPluck>,
        timing: List<Long>?,
        options: FingeringOptions
    ): Metrics {
        val metrics = Metrics()
        plucks.forEachIndexed { i, pluck ->
            val cur = Candidate(pluck.stringNumber, pluck.fret)
            if (i == 0) {
                metrics.totalCost += entryCost(cur, options)
            } else {
                val prev = Candidate(plucks[i - 1].stringNumber, plucks[i - 1].fret)
                val t = transitionCost(prev, cur, layers[i], layers[i - 1], timing, options)
                if (t < INF) {
                    metrics.totalCost += t
                    metrics.maxTransition = maxOf(metrics.maxTransition, t)
                }
            }
        }
        return metrics
    }

    /** Convierte el coste acumulado en un índice de dificultad 1..10 (orientativo). */
    private fun difficultyOf(totalCost: Double, playableNotes: Int, maxTransition: Double): Int {
        if (playableNotes <= 1) return 1
        val avg = (totalCost / playableNotes).coerceAtLeast(0.0)
        val byAvg = 1 + (avg * 9.0 / 8.0)
        val byWorst = 1 + (maxTransition.coerceAtLeast(0.0) * 9.0 / 14.0)
        return maxOf(byAvg, byWorst).toInt().coerceIn(1, 10)
    }

    // ===========================================================================================
    // Alternativas
    // ===========================================================================================

    /**
     * Genera hasta `options.alternatives` digitaciones distintas de la principal. Se obtiene
     * repitiendo el cálculo con los candidatos ya usados **penalizados**, hasta reunir caminos
     * distintos (técnica de Lawler simplificada).
     */
    private fun alternativesFor(
        pitches: List<ScorePitch>,
        candidates: List<List<Candidate>>,
        timing: List<Long>?,
        options: FingeringOptions,
        principal: List<TabPluck?>
    ): List<List<TabPluck?>> {
        if (options.alternatives <= 0) return emptyList()
        val results = mutableListOf<List<TabPluck?>>()
        val usedKeys = mutableSetOf<NodeKey>()
        principal.forEachIndexed { i, p -> if (p != null) usedKeys += NodeKey(i, p.stringNumber, p.fret) }

        val runs = playableRuns(candidates)
        var attempts = 0
        val maxAttempts = options.alternatives * 4
        while (results.size < options.alternatives && attempts < maxAttempts) {
            attempts++
            val penaltyMap = usedKeys.associateWith { ALTERNATIVE_PENALTY }
            val assignment = arrayOfNulls<TabPluck>(pitches.size)
            runs.forEach { layerIndices ->
                val plucks = viterbi(layerIndices, candidates, timing, options, penaltyMap)
                for (i in layerIndices.indices) assignment[layerIndices[i]] = plucks[i]
            }
            val list = assignment.toList()
            val isDifferent = list.any { it != null } && list != principal && results.none { it == list }
            if (isDifferent) {
                results += list
                list.forEachIndexed { i, p -> if (p != null) usedKeys += NodeKey(i, p.stringNumber, p.fret) }
            } else {
                // No quedan soluciones distintas con esta penalización: detener.
                break
            }
        }
        return results
    }
}
