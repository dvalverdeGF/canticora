package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.music.PitchClass
import com.grafitto.laudate.domain.model.score.ScoreKey
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature

/**
 * Modo de transposición de una tablatura (ADR-017, *Feature T*).
 *
 *  - [CAPO]: los dígitos **no cambian**; sube la cejilla. Requiere un instrumento que la admita.
 *  - [REFINGER]: recalcula **cuerda y traste** para la nueva tonalidad (el tono no cambia).
 *  - [LOCKED]: no sigue la transposición; se muestra en su tonalidad original.
 */
enum class TabTranspositionMode { CAPO, REFINGER, LOCKED }

/**
 * Reglas de transposición de tablaturas.
 *
 * **Regla de oro** (`TABLATURES_PLAN.md` §4.2): una tablatura **escrita a mano** es una decisión del
 * usuario. Redigitarla **cambia sus posiciones**, así que **nunca se hace en silencio**.
 */
object TabTranspositionPolicy {

    /**
     * Aplica la transposición **para visualización** (sin guardar): devuelve la tablatura tal y
     * como debe verse cuando el canto se ha transportado [semitones]. Solo el modo **redigitado**
     * cambia la digitación (se recalcula desde las alturas ya transportadas); cejilla y bloqueado
     * dejan la tablatura intacta.
     *
     * El modo lo manda [Tablature.transpositionMode] si está fijado; si no, se decide con
     * [defaultMode]. Es **temporal**: nunca se persiste el resultado de una transposición de lectura.
     */
    fun transposeForDisplay(
        tablature: Tablature,
        semitones: Int,
        options: FingeringOptions? = null
    ): Tablature {
        if (semitones == 0) return tablature
        val mode = tablature.transpositionMode ?: defaultMode(tablature)
        return when (mode) {
            TabTranspositionMode.CAPO, TabTranspositionMode.LOCKED -> tablature
            TabTranspositionMode.REFINGER -> {
                val target = tablature.soundedPitches().map { pitch ->
                    com.grafitto.laudate.domain.model.score.ScorePitch.fromMidi(pitch.midi + semitones)
                }
                if (target.isEmpty()) tablature
                else refinger(
                    tablature,
                    target,
                    options ?: FingeringOptions(tuning = tablature.tuning)
                )
            }
        }
    }

    /**
     * Semitonos entre la tonalidad **escrita** de la tablatura ([Tablature.writtenKey]) y la
     * **actual** del canto.
     *
     * Es lo que permite que la tablatura **siga el cambio de tonalidad guardado** en el canto (no
     * solo la transposición en vivo): si el canto se guardó en otra tonalidad, la tablatura se
     * redigita (o se etiqueta como cejilla) según su modo. Devuelve `0` si la tablatura no declara
     * tonalidad escrita (compatibilidad con tablaturas antiguas).
     */
    fun semitonesFromWrittenKey(tablature: Tablature, songKeyName: String): Int {
        val written = tablature.writtenKey ?: return 0
        val target = PitchClass.parse(songKeyName) ?: return 0
        return shortestDelta(written.tonicChromaticIndex(), target.chromaticIndex)
    }

    /** Intervalo más corto (−6..+6 semitonos) entre dos índices cromáticos. */
    private fun shortestDelta(from: Int, to: Int): Int {
        var delta = (to - from) % 12
        if (delta > 6) delta -= 12
        if (delta < -6) delta += 12
        return delta
    }

    /**
     * Índice cromático de la **tónica** de una tonalidad. La armadura avanza de quinta en quinta
     * (+7 semitonos por cada alteración); en modo menor la tónica está una tercera menor por debajo.
     */
    private fun ScoreKey.tonicChromaticIndex(): Int {
        val majorTonic = (((fifths * 7) % 12) + 12) % 12
        return if (isMinor) (((majorTonic - 3) % 12) + 12) % 12 else majorTonic
    }

    /**
     * Decide el modo por defecto **sin tocar nada**:
     *  - generada/redigitada → [TabTranspositionMode.REFINGER] (nadie escribió esas posiciones);
     *  - escrita a mano + instrumento con cejilla → [TabTranspositionMode.CAPO];
     *  - escrita a mano + instrumento sin cejilla (laúd) → [TabTranspositionMode.LOCKED]
     *    (con la opción de redigitar **con aviso**).
     */
    fun defaultMode(tablature: Tablature, supportsCapo: Boolean = tablature.tuning.supportsCapo): TabTranspositionMode =
        when (tablature.origin) {
            TabOrigin.GENERATED_FROM_SCORE -> TabTranspositionMode.REFINGER
            TabOrigin.IMPORTED_TEXT -> if (supportsCapo) TabTranspositionMode.CAPO else TabTranspositionMode.LOCKED
            TabOrigin.CREATED -> if (supportsCapo) TabTranspositionMode.CAPO else TabTranspositionMode.LOCKED
        }

    /**
     * Aplica [TabTranspositionMode.REFINGER]: redigita la tablatura desde sus **alturas** para la
     * nueva tonalidad, usando el [FingeringEngine]. El tono de las notas NO cambia (la nueva
     * tonalidad la fija quien la pide); aquí se recalcula la **digitación** para esas alturas.
     *
     * @param pitches alturas destino (ya transpuestas por el motor armónico).
     * @return la tablatura con las nuevas posiciones, o `null` si el redigitado lo pide el usuario
     *   sobre una tablatura escrita a mano (hay que **avisar** antes; ver [requiresConfirmation]).
     */
    fun refinger(
        tablature: Tablature,
        pitches: List<com.grafitto.laudate.domain.model.score.ScorePitch>,
        options: FingeringOptions
    ): Tablature {
        val result = FingeringEngine.assign(pitches, options)
        // Reparte las pulsaciones calculadas por las casillas existentes (mismo nº de notas sonoras).
        val measures = distribute(tablature.measures, result.plucks)
        return tablature.copy(measures = measures, origin = TabOrigin.GENERATED_FROM_SCORE)
    }

    /**
     * Indica si hay que **pedir confirmación** antes de redigitar: es una tablatura escrita a mano
     * (origen [TabOrigin.CREATED]) y el modo elegido es [TabTranspositionMode.REFINGER].
     */
    fun requiresConfirmation(tablature: Tablature, mode: TabTranspositionMode): Boolean =
        mode == TabTranspositionMode.REFINGER && tablature.origin == TabOrigin.CREATED

    /**
     * Aplica [TabTranspositionMode.CAPO]: los dígitos se conservan y se indica el traste de cejilla
     * que sube el tono. Devuelve la tablatura sin cambios en las posiciones (el capo lo aplica la
     * canción); esta función existe para dejar explícito el contrato del modo.
     */
    fun applyCapo(tablature: Tablature): Tablature = tablature

    /**
     * Reparte una lista plana de pulsaciones (una por nota sonora) en la estructura de casillas de
     * la tablatura, respetando el nº de pulsaciones de cada casilla (las pausas se conservan).
     */
    private fun distribute(measures: List<TabMeasure>, plucks: List<TabPluck>): List<TabMeasure> {
        var cursor = 0
        return measures.map { measure ->
            // Se conservan las marcas del manuscrito (repetición/volta): el redigitado solo
            // reescribe (cuerda, traste) y **nunca** borra marcas del documento (RF-127).
            measure.copy(
                slots = measure.slots.map { slot ->
                    if (slot.isEmpty) {
                        slot
                    } else {
                        val take = slot.strings.size
                        val slice = plucks.subList(cursor, minOf(cursor + take, plucks.size))
                        cursor += slice.size
                        // La técnica de cada nota del usuario se **conserva** al redigitar (las
                        // técnicas son un conjunto y pueden combinarse).
                        val merged = slice.mapIndexed { i, pluck ->
                            val original = slot.strings.getOrNull(i)
                            if (original != null) pluck.copy(techniques = original.techniques) else pluck
                        }
                        slot.copy(strings = merged.toList())
                    }
                }
            )
        }
    }
}
