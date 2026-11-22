package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning

/**
 * **Tipo de problema** de un acorde de tablatura (F-08).
 *
 * El editor deja escribir **cualquier** combinación de cuerdas y trastes: es lo correcto, porque la
 * tablatura es un lenguaje y hay música que usa posiciones raras. Lo que faltaba es **avisar** de lo
 * que no se puede tocar, sin impedir escribirlo. Es la diferencia entre un editor que valida y uno que
 * corrige: MuseScore tampoco bloquea, marca en rojo.
 */
enum class TabChordIssueKind(val label: String) {
    /** Dos notas en la **misma cuerda** de la misma columna: físicamente imposible. */
    DUPLICATE_STRING("Cuerda repetida"),

    /** La cuerda no existe en la afinación (p. ej. la 7ª de una guitarra de 6). */
    STRING_OUT_OF_RANGE("Cuerda inexistente"),

    /** El traste se sale del mástil (`0..maxFret`). */
    FRET_OUT_OF_RANGE("Traste fuera del mástil"),

    /** La mano no llega: la distancia entre el traste más grave y el más agudo supera el palmo. */
    IMPOSSIBLE_STRETCH("Posición inalcanzable"),

    /** Una cuerda **apagada** (`X`) no puede llevar traste: suena como percusión, sin altura. */
    MUTED_WITH_FRET("Cuerda apagada con traste"),

    /**
     * La cuerda está marcada **siempre al aire** (no pasa por el mástil: la tiorba, la 5.ª del banjo de
     * cinco cuerdas) y se le ha escrito un traste distinto de `0`. Es la regla de
     * `StringData::fret()`: `(fret > 0 && strg.open)` → traste inválido (`stringdata.cpp:524`).
     */
    FRET_ON_ALWAYS_OPEN_STRING("Cuerda siempre al aire")
}

/**
 * Un **aviso** de un acorde concreto: qué pasa, dónde y en qué cuerda.
 *
 * [measureIndex] y [slotIndex] son índices de la **tablatura**, no de la pantalla, para que el aviso
 * sobreviva a cualquier rejilla. [message] está en español y es lo que se le enseña al usuario.
 */
data class TabChordIssue(
    val kind: TabChordIssueKind,
    val measureIndex: Int,
    val slotIndex: Int,
    val stringNumber: Int,
    val message: String
)

/**
 * **Validador de acordes** (F-08): dice qué combinaciones de cuerdas y trastes no se pueden tocar.
 *
 * No modifica nada y no bloquea la escritura: devuelve una **lista de avisos** que la UI pinta (un
 * punto rojo sobre la columna y el texto en la barra de estado). Es puro y sin estado, así que se
 * puede llamar en cada recomposición sin coste perceptible —recorrer una tablatura de 200 compases
 * son unos pocos miles de comparaciones—.
 *
 * **Las reglas** son las de la técnica del instrumento, no las de MuseScore (que no valida nada):
 *
 * 1. **Cuerda repetida**: dos notas en la misma cuerda de la misma columna. En un instrumento de
 *    cuerdas pulsadas cada cuerda suena **una** vez por ataque.
 * 2. **Cuerda inexistente**: el número de cuerda se sale de la afinación.
 * 3. **Traste fuera del mástil**: fuera de `0..maxFret`. El límite es el **número de trastes del
 *    instrumento** ([Tuning.frets], 19 en la guitarra), igual que `m_frets` en MuseScore; solo si se
 *    pasa un `maxFret` explícito se usa ese.
 * 4. **Posición inalcanzable**: entre el traste más grave y el más agudo de un mismo acorde hay más
 *    de [MAX_HAND_SPAN_FRETS] trastes. Cuatro es el palmo cómodo de la mano izquierda y el criterio
 *    que usan los digitadores; se deja como constante pública para poder afinarlo.
 * 5. **Cuerda apagada con traste**: una `X` con una cifra distinta de 0 es contradictoria.
 * 6. **Cuerda siempre al aire con traste**: una cuerda que no pasa por el mástil solo admite el
 *    traste `0` (regla de `StringData::fret()` en MuseScore).
 *
 * Las cuerdas **apagadas** se excluyen de la regla 4: una `X` no se pisa, así que no cuenta para el
 * palmo. Las notas **al aire** (traste 0) tampoco: no se pisan.
 */
object TabChordValidator {

    /**
     * **Palmo máximo** de la mano izquierda, en trastes: la distancia entre el traste más grave y el
     * más agudo que se pueden pisar a la vez. Cuatro trastes (una posición) es el criterio habitual;
     * cinco ya exige una ceja o una mano grande.
     */
    const val MAX_HAND_SPAN_FRETS: Int = 4

    /**
     * Valida **toda** la tablatura y devuelve los avisos en orden de lectura (compás, columna,
     * cuerda). Una tablatura correcta devuelve una lista **vacía**.
     */
    fun validate(tablature: Tablature, maxFret: Int? = null): List<TabChordIssue> {
        val issues = mutableListOf<TabChordIssue>()
        val limit = maxFret ?: tablature.tuning.frets
        tablature.measures.forEachIndexed { measureIndex, measure ->
            measure.slots.forEachIndexed { slotIndex, slot ->
                issues += validateSlot(
                    slot = slot,
                    tuning = tablature.tuning,
                    maxFret = limit,
                    measureIndex = measureIndex,
                    slotIndex = slotIndex
                )
            }
        }
        return issues
    }

    /**
     * Valida **una columna**. Es la unidad de validación porque un acorde es lo que suena a la vez:
     * las reglas de la mano (cuerda repetida, palmo) solo tienen sentido dentro de la columna.
     */
    fun validateSlot(
        slot: TabSlot,
        tuning: Tuning,
        maxFret: Int? = null,
        measureIndex: Int = 0,
        slotIndex: Int = 0
    ): List<TabChordIssue> {
        if (slot.strings.isEmpty()) return emptyList()
        val limit = maxFret ?: tuning.frets
        val issues = mutableListOf<TabChordIssue>()

        // 1 · Cuerda repetida. Se avisa **una sola vez por cuerda**, en la segunda aparición: con tres
        // notas en la misma cuerda el problema es el mismo, no dos problemas.
        val seen = mutableSetOf<Int>()
        val reported = mutableSetOf<Int>()
        slot.strings.forEach { pluck ->
            if (!seen.add(pluck.stringNumber) && reported.add(pluck.stringNumber)) {
                issues += TabChordIssue(
                    kind = TabChordIssueKind.DUPLICATE_STRING,
                    measureIndex = measureIndex,
                    slotIndex = slotIndex,
                    stringNumber = pluck.stringNumber,
                    message = "La cuerda ${pluck.stringNumber} suena dos veces en la misma columna"
                )
            }
        }

        slot.strings.forEach { pluck ->
            // 2 · Cuerda inexistente.
            if (pluck.stringNumber !in 1..tuning.stringCount) {
                issues += TabChordIssue(
                    kind = TabChordIssueKind.STRING_OUT_OF_RANGE,
                    measureIndex = measureIndex,
                    slotIndex = slotIndex,
                    stringNumber = pluck.stringNumber,
                    message = "${tuning.name} no tiene cuerda ${pluck.stringNumber} " +
                        "(tiene ${tuning.stringCount})"
                )
            }
            // 3 · Traste fuera del mástil.
            if (pluck.fret !in 0..limit) {
                issues += TabChordIssue(
                    kind = TabChordIssueKind.FRET_OUT_OF_RANGE,
                    measureIndex = measureIndex,
                    slotIndex = slotIndex,
                    stringNumber = pluck.stringNumber,
                    message = "El traste ${pluck.fret} se sale del mástil (0–$limit)"
                )
            }
            // 6 · Cuerda que no pasa por el mástil: solo admite el traste 0.
            val string = tuning.strings.getOrNull(tuning.strings.size - pluck.stringNumber)
            if (string != null && string.alwaysOpen && pluck.fret > 0) {
                issues += TabChordIssue(
                    kind = TabChordIssueKind.FRET_ON_ALWAYS_OPEN_STRING,
                    measureIndex = measureIndex,
                    slotIndex = slotIndex,
                    stringNumber = pluck.stringNumber,
                    message = "La cuerda ${pluck.stringNumber} de ${tuning.name} va siempre al aire " +
                        "y no admite el traste ${pluck.fret}"
                )
            }
            // 5 · Cuerda apagada con traste.
            if (pluck.isMuted && pluck.fret != 0) {
                issues += TabChordIssue(
                    kind = TabChordIssueKind.MUTED_WITH_FRET,
                    measureIndex = measureIndex,
                    slotIndex = slotIndex,
                    stringNumber = pluck.stringNumber,
                    message = "La cuerda ${pluck.stringNumber} está apagada y no puede llevar " +
                        "el traste ${pluck.fret}"
                )
            }
        }

        // 4 · Posición inalcanzable. Solo cuentan las notas **pisadas** (ni apagadas ni al aire).
        val fretted = slot.strings.filter { !it.isMuted && it.fret > 0 }.map { it.fret }
        if (fretted.size >= 2) {
            val span = fretted.max() - fretted.min()
            if (span > MAX_HAND_SPAN_FRETS) {
                issues += TabChordIssue(
                    kind = TabChordIssueKind.IMPOSSIBLE_STRETCH,
                    measureIndex = measureIndex,
                    slotIndex = slotIndex,
                    stringNumber = slot.strings.minOf { it.stringNumber },
                    message = "La mano no llega: del traste ${fretted.min()} al ${fretted.max()} " +
                        "hay $span trastes (máximo $MAX_HAND_SPAN_FRETS)"
                )
            }
        }

        return issues
    }

    /** `true` si la columna no tiene ningún aviso. Es el atajo que usa la UI para pintar el punto. */
    fun isPlayable(
        slot: TabSlot,
        tuning: Tuning,
        maxFret: Int? = null
    ): Boolean = validateSlot(slot, tuning, maxFret).isEmpty()

    /**
     * Avisos **por columna**, indexados por `measureIndex to slotIndex`. Es la forma que necesita el
     * lienzo para pintar el punto rojo sin recorrer la lista entera por cada columna.
     */
    fun bySlot(tablature: Tablature, maxFret: Int? = null): Map<Pair<Int, Int>, List<TabChordIssue>> =
        validate(tablature, maxFret).groupBy { it.measureIndex to it.slotIndex }

    /** Nº de columnas con algún aviso. Sirve para el resumen de la barra de estado. */
    fun issueCount(tablature: Tablature, maxFret: Int? = null): Int =
        bySlot(tablature, maxFret).size

    /** `true` si **algún** compás tiene avisos. */
    fun hasIssues(measures: List<TabMeasure>, tuning: Tuning, maxFret: Int? = null): Boolean =
        measures.any { measure ->
            measure.slots.any { !isPlayable(it, tuning, maxFret) }
        }
}
