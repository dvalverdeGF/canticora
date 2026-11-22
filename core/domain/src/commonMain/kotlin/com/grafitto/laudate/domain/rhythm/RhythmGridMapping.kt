package com.grafitto.laudate.domain.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumArticulation
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.StrumRange
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Subdivisión (resolución) de la rejilla rítmica «Beat & Stroke», expresada por el valor
 * en negras de cada casilla.
 *
 * Es la unidad con la que el músico piensa al escribir un ritmo: *«cuatro negras»*,
 * *«corcheas»*, *«semicorcheas»*. Los **tresillos** no se ofrecen porque [RhythmFigure]
 * no tiene figuras ternarias: ofrecerlos rompería la conversión exacta rejilla ↔ modelo.
 * En compases compuestos (6/8, 9/8, 12/8) la subdivisión de **corcheas** ya produce el
 * agrupamiento ternario natural de la métrica.
 *
 * El orden de declaración es de **gruesa a fina**: [ordinal] crece con la finura, lo que permite
 * comparar resoluciones (`requested.ordinal >= best.ordinal`).
 */
enum class RhythmGridResolution(
    val beatValue: Double,
    val label: String,
    val shortLabel: String
) {
    QUARTER(1.0, "Negras", "\u2669"),
    EIGHTH(0.5, "Corcheas", "\u266A"),
    SIXTEENTH(0.25, "Semicorcheas", "\uD834\uDD60");

    /** Número de casillas de **un** compás con esta resolución. */
    fun slotCount(timeSignature: TimeSignature): Int =
        (timeSignature.totalQuarterBeats / beatValue).roundToInt().coerceAtLeast(1)

    /**
     * Indica si el compás admite esta resolución: el compás debe dividirse en un número
     * **entero** de casillas **y** cada pulso del compás en un número entero de ellas.
     *
     * Ejemplos: 9/8 (= 4,5 negras) no admite negras; 6/8 no admite negras (su pulso es una
     * negra con puntillo = 1,5 negras) pero sí corcheas (3 por pulso) y semicorcheas (6).
     */
    fun supports(timeSignature: TimeSignature): Boolean {
        val slots = timeSignature.totalQuarterBeats / beatValue
        if (abs(slots - slots.roundToInt()) > 1e-6) return false
        val perBeat = timeSignature.beatUnitQuarterBeats / beatValue
        return abs(perBeat - perBeat.roundToInt()) < 1e-6
    }

    /**
     * Casillas por pulso. Los compases compuestos (6/8…) tienen 3 corcheas por pulso;
     * los simples, tantas como subdivisiones quepan en una negra.
     */
    fun slotsPerBeat(timeSignature: TimeSignature): Int =
        (timeSignature.beatUnitQuarterBeats / beatValue).roundToInt().coerceAtLeast(1)
}

/**
 * Estado de una casilla de la rejilla «Beat & Stroke».
 *
 *  - [direction] con golpe (`DOWN`/`UP`/`MUTE`): hay un **ataque** en esa subdivisión.
 *  - [direction] `NONE` y [isHeld] `false`: **aire** (silencio) en esa subdivisión.
 *  - [isHeld] `true`: la casilla **prolonga** el golpe anterior (nota más larga).
 *
 * La distinción aire ↔ prolongación es la que permite que la conversión rejilla ↔ modelo
 * sea **exacta**: `↓ — ↓` (negra ligada a negra) es distinto de `↓ · ↓` (negra, silencio, negra).
 *
 * [articulation] y [range] completan el trazo: la casilla guarda **todo** lo que define un
 * golpe, de modo que editar la rejilla nunca pierde acentos, arpegios ni flechas cortas.
 */
data class RhythmGridCell(
    val direction: StrumDirection = StrumDirection.NONE,
    val isHeld: Boolean = false,
    val range: StrumRange = StrumRange.ALL,
    /**
     * Articulación del golpe (acento, arpegio, staccato). La paleta de trazos **no** la expresa:
     * se conserva la que ya tuviera la casilla, para que escribir flechas en la rejilla no borre
     * las articulaciones puestas en el editor avanzado.
     */
    val articulation: StrumArticulation = StrumArticulation.NONE
) {
    /** Indica si la casilla inicia un ataque. */
    val isAttack: Boolean get() = direction.isStruck

    companion object {
        val Down = RhythmGridCell(StrumDirection.DOWN)
        val Up = RhythmGridCell(StrumDirection.UP)
        val Mute = RhythmGridCell(StrumDirection.MUTE)
        val Hold = RhythmGridCell(StrumDirection.NONE, isHeld = true)
        val Air = RhythmGridCell(StrumDirection.NONE, isHeld = false)

        /** Estados por los que cicla un toque sobre la casilla, en orden de frecuencia de uso. */
        val cycle: List<RhythmGridCell> = listOf(Down, Up, Mute, Air, Hold)

        /**
         * Siguiente estado al tocar la casilla.
         *
         * El ciclo se busca por **trazo** (dirección y prolongación), no por igualdad total, para
         * que funcione también cuando la casilla lleva articulación o registro: ambos se conservan.
         */
        fun next(current: RhythmGridCell): RhythmGridCell {
            val index = cycle.indexOfFirst {
                it.direction == current.direction && it.isHeld == current.isHeld
            }
            val base = if (index < 0) Down else cycle[(index + 1) % cycle.size]
            return base.copy(range = current.range, articulation = current.articulation)
        }

        /** Casilla equivalente a un evento del modelo: trazo, registro y articulación. */
        fun of(event: RhythmEvent): RhythmGridCell = RhythmGridCell(
            direction = event.effectiveDirection,
            range = event.effectiveRange,
            articulation = event.effectiveArticulation
        )
    }
}

/**
 * Rejilla rítmica completa: la subdivisión elegida y el estado de cada casilla.
 *
 * La rejilla **siempre representa compases completos**: `cells.size` es un múltiplo exacto de
 * [RhythmGridResolution.slotCount]. Esa es la garantía de que editar la rejilla nunca deja el
 * patrón «a medias» y de que el total de negras no cambia al tocar una casilla.
 */
data class RhythmGrid(
    val resolution: RhythmGridResolution,
    val cells: List<RhythmGridCell>
) {
    /** Número de compases que muestra la rejilla. */
    fun barCount(timeSignature: TimeSignature): Int =
        cells.size / resolution.slotCount(timeSignature)

    /** Índices de casilla que inician un pulso, para dibujar los separadores de tiempo. */
    fun beatBoundaries(timeSignature: TimeSignature): Set<Int> {
        val perBeat = resolution.slotsPerBeat(timeSignature)
        if (perBeat <= 1) return emptySet()
        return cells.indices.filter { it % perBeat == 0 }.toSet()
    }

    /** Índices de casilla que inician un compás, para dibujar los separadores de compás. */
    fun barBoundaries(timeSignature: TimeSignature): Set<Int> {
        val perBar = resolution.slotCount(timeSignature)
        if (perBar <= 0) return emptySet()
        return cells.indices.filter { it % perBar == 0 }.toSet()
    }

    /**
     * Indica si la casilla [slot] puede **prolongar** el golpe anterior.
     *
     * «Prolongar» significa «esta casilla continúa el golpe de la izquierda»: sin un golpe a la
     * izquierda no tiene sentido, y aplicarlo crearía una prolongación huérfana que la conversión
     * al modelo descartaría (el usuario vería que su toque «no hace nada»).
     */
    fun canHoldAt(slot: Int): Boolean {
        if (slot <= 0 || slot >= cells.size) return false
        val previous = cells[slot - 1]
        return previous.isAttack || previous.isHeld
    }

    /** Siguiente estado al tocar la casilla [slot], saltando «prolongar» cuando no aplica. */
    fun nextCell(slot: Int): RhythmGridCell {
        val current = cells.getOrNull(slot) ?: RhythmGridCell.Air
        val candidate = RhythmGridCell.next(current)
        if (candidate.isHeld && !canHoldAt(slot)) return RhythmGridCell.next(candidate)
        return candidate
    }

    /**
     * Devuelve la rejilla con la casilla [slot] cambiada.
     *
     * Es una operación **total**: nunca cambia el número de casillas, así que la duración del
     * patrón se conserva por construcción. Un «prolongar» sin golpe previo se ignora (devuelve
     * la misma rejilla) en lugar de crear una prolongación huérfana.
     */
    fun withCell(slot: Int, cell: RhythmGridCell): RhythmGrid {
        if (slot !in cells.indices) return this
        if (cell.isHeld && !canHoldAt(slot)) return this
        val current = cells[slot]
        // La paleta de trazos no expresa articulación: al escribir una flecha se conserva la
        // articulación que ya tuviera la casilla, para no borrar el trabajo del editor avanzado.
        val merged = if (cell.isAttack && cell.articulation == StrumArticulation.NONE) {
            cell.copy(articulation = current.articulation)
        } else {
            cell
        }
        if (current == merged) return this
        return copy(cells = cells.toMutableList().also { it[slot] = merged })
    }
}

/**
 * Conversión **pura, total y bidireccional** entre el modelo de patrón ([RhythmPattern]) y la
 * rejilla «Beat & Stroke» ([RhythmGrid]).
 *
 * Es la pieza que garantiza que las tres vistas (rejilla, flechas/figuras y símbolos ChordPro)
 * sean **la misma información**: editar en cualquiera de ellas produce un `List<RhythmEvent>`
 * equivalente, y ninguna vista puede desincronizarse de las demás.
 *
 * ## Invariantes (verificados por tests)
 *  1. **Sin pérdida**: `toEvents(toGrid(p))` describe exactamente el mismo ritmo que `p`.
 *  2. **Total**: `toGrid` siempre produce compases completos, así que el total de negras de
 *     `toEvents(toGrid(p))` es `barCount × duración del compás`.
 *  3. **Edición local**: `applyCell` cambia **solo** la casilla tocada; el resto del patrón
 *     (figuras, articulaciones, registros) se conserva.
 *
 * ## Ligaduras
 * Una nota puede durar más que cualquier figura (p. ej. 2,5 negras). En ese caso se guarda como
 * varias figuras **ligadas** ([RhythmEvent.isTied]): la primera lleva el golpe y las siguientes lo
 * continúan. La rejilla las vuelve a ver como un único golpe prolongado, de modo que la ida y
 * vuelta es exacta y el patrón guardado coincide siempre con lo dibujado.
 */
object RhythmGridMapping {

    /** Rejilla vacía (todo aire) para un compás, resolución y número de compases dados. */
    fun emptyGrid(
        timeSignature: TimeSignature,
        resolution: RhythmGridResolution,
        barCount: Int = 1
    ): RhythmGrid = RhythmGrid(
        resolution = resolution,
        cells = List(resolution.slotCount(timeSignature) * barCount.coerceAtLeast(1)) { RhythmGridCell.Air }
    )

    /**
     * Indica si el patrón encaja **exactamente** en la rejilla: cada evento empieza en una
     * casilla y dura un número entero de casillas.
     */
    fun fits(pattern: RhythmPattern, resolution: RhythmGridResolution): Boolean {
        var cursor = 0.0
        return pattern.events.all { event ->
            val startSlots = cursor / resolution.beatValue
            val spanSlots = event.beatValue / resolution.beatValue
            cursor += event.beatValue
            abs(startSlots - startSlots.roundToInt()) < 1e-3 &&
                abs(spanSlots - spanSlots.roundToInt()) < 1e-3
        }
    }

    /**
     * La resolución **más gruesa** que representa el patrón sin pérdida.
     *
     * Nunca devuelve `null`: todas las figuras de [RhythmFigure] son múltiplos de una
     * semicorchea, así que [RhythmGridResolution.SIXTEENTH] siempre encaja.
     */
    fun bestResolution(pattern: RhythmPattern): RhythmGridResolution =
        RhythmGridResolution.entries.firstOrNull { fits(pattern, it) } ?: RhythmGridResolution.SIXTEENTH

    /**
     * Resolución efectiva de la rejilla: la que pidió el usuario, **afinada** si el patrón no
     * cabe en ella.
     *
     * Es lo que evita que abrir un patrón con corcheas con puntillo en una rejilla de negras
     * «invente» duraciones: la rejilla se afina lo justo para representarlo tal cual es.
     */
    fun effectiveResolution(
        pattern: RhythmPattern,
        requested: RhythmGridResolution
    ): RhythmGridResolution {
        val best = bestResolution(pattern)
        return if (requested.ordinal >= best.ordinal) requested else best
    }

    /**
     * Proyecta el patrón sobre la rejilla.
     *
     * @param barCount número de compases a mostrar. Por defecto, los que ocupa el patrón.
     */
    fun toGrid(
        pattern: RhythmPattern,
        resolution: RhythmGridResolution,
        barCount: Int = pattern.barCount
    ): RhythmGrid {
        val perBar = resolution.slotCount(pattern.timeSignature)
        val cells = MutableList(perBar * barCount.coerceAtLeast(1)) { RhythmGridCell.Air }
        val step = resolution.beatValue
        val events = pattern.events

        var cursor = 0.0
        var i = 0
        while (i < events.size) {
            val head = events[i]
            var span = head.beatValue
            var j = i + 1
            while (j < events.size && events[j].isTied) {
                span += events[j].beatValue
                j++
            }

            val startSlot = (cursor / step).roundToInt()
            val spanSlots = (span / step).roundToInt().coerceAtLeast(1)

            if (head.isStruck) {
                if (startSlot in cells.indices) cells[startSlot] = RhythmGridCell.of(head)
                for (k in 1 until spanSlots) {
                    val index = startSlot + k
                    if (index in cells.indices) cells[index] = RhythmGridCell.Hold
                }
            } else {
                // Silencios y posiciones sin golpe: aire (nunca prolongación).
                for (k in 0 until spanSlots) {
                    val index = startSlot + k
                    if (index in cells.indices) cells[index] = RhythmGridCell.Air
                }
            }

            cursor += span
            i = j
        }
        return RhythmGrid(resolution, cells)
    }

    /**
     * Reconstruye la lista de eventos a partir de la rejilla.
     *
     * Cada casilla con golpe inicia un ataque que se extiende por las casillas de prolongación
     * siguientes; cada tramo de casillas sin golpe se agrupa en silencios. Si la duración de un
     * golpe no cabe en una sola figura, se descompone en figuras **ligadas**.
     */
    fun toEvents(grid: RhythmGrid, timeSignature: TimeSignature): List<RhythmEvent> {
        val out = mutableListOf<RhythmEvent>()
        val cells = grid.cells
        val step = grid.resolution.beatValue
        var i = 0

        while (i < cells.size) {
            val cell = cells[i]
            if (cell.isAttack) {
                var span = 1
                while (i + span < cells.size && cells[i + span].isHeld) span++
                val figures = decompose(span * step)
                out += RhythmEvent(figures.first(), cell.direction, cell.articulation, cell.range)
                figures.drop(1).forEach { figure ->
                    out += RhythmEvent(figure, cell.direction, cell.articulation, cell.range, isTied = true)
                }
                i += span
            } else {
                // Aire y prolongaciones huérfanas: silencio del mismo tiempo total.
                var span = 1
                while (i + span < cells.size && !cells[i + span].isAttack) span++
                decompose(span * step).forEach { figure ->
                    out += RhythmEvent(RhythmFigure.restEquivalentOf(figure) ?: RhythmFigure.QUARTER_REST)
                }
                i += span
            }
        }
        return out
    }

    /**
     * Aplica un trazo a **una sola casilla** de la rejilla.
     *
     * Se implementa como `toEvents(toGrid(patrón).withCell(casilla, trazo))`: la rejilla es la
     * fuente de verdad mientras se edita, así que lo que el músico ve es **exactamente** lo que se
     * guarda, y la duración total del patrón no puede cambiar (la rejilla siempre tiene las mismas
     * casillas).
     *
     * Consecuencias, todas predecibles:
     *  - tocar el ataque de una nota cambia el trazo **sin** alterar su duración;
     *  - tocar una prolongación con un golpe **parte** la nota en dos;
     *  - tocar una prolongación con aire **parte** la nota y deja un silencio en medio;
     *  - tocar el ataque con aire convierte la nota (y sus prolongaciones) en silencio;
     *  - tocar con «prolongar» alarga la nota una casilla, absorbiendo la siguiente.
     */
    fun applyCell(
        pattern: RhythmPattern,
        resolution: RhythmGridResolution,
        slot: Int,
        cell: RhythmGridCell
    ): List<RhythmEvent> {
        val grid = toGrid(pattern, resolution)
        val updated = grid.withCell(slot, cell)
        if (updated == grid) return pattern.events
        return toEvents(updated, pattern.timeSignature)
    }

    /**
     * Cambia el número de compases del patrón.
     *
     * Añadir compases rellena con silencios; quitarlos descarta lo que hubiera en los compases
     * sobrantes. El resultado siempre ocupa exactamente [barCount] compases completos.
     */
    fun withBarCount(
        pattern: RhythmPattern,
        resolution: RhythmGridResolution,
        barCount: Int
    ): List<RhythmEvent> {
        val target = barCount.coerceAtLeast(1)
        if (target == pattern.barCount) return pattern.events
        return toEvents(toGrid(pattern, resolution, target), pattern.timeSignature)
    }

    /**
     * Índice de la casilla donde **empieza** cada evento, para poder resaltar en la rejilla
     * la posición que está sonando (RF-307). La lista tiene el mismo tamaño que los eventos.
     */
    fun slotStarts(pattern: RhythmPattern, resolution: RhythmGridResolution): List<Int> {
        var cursor = 0.0
        return pattern.events.map { event ->
            val slot = (cursor / resolution.beatValue).roundToInt()
            cursor += event.beatValue
            slot
        }
    }

    /**
     * Descompone una duración en negras en figuras representables, de mayor a menor.
     *
     * Suma **siempre** el total exacto (todas las figuras son múltiplos de una semicorchea), de
     * modo que la edición nunca altera la duración del compás.
     */
    fun decompose(beats: Double): List<RhythmFigure> {
        if (beats <= 1e-6) return listOf(RhythmFigure.SIXTEENTH)
        val ordered = RhythmFigure.notes.sortedByDescending { it.beatValue }
        val out = mutableListOf<RhythmFigure>()
        var remaining = beats
        while (remaining > 1e-6 && out.size < MAX_DECOMPOSITION) {
            val figure = ordered.firstOrNull { it.beatValue <= remaining + 1e-6 } ?: break
            out += figure
            remaining -= figure.beatValue
        }
        return out.ifEmpty { listOf(RhythmFigure.QUARTER) }
    }

    private const val MAX_DECOMPOSITION = 32
}
