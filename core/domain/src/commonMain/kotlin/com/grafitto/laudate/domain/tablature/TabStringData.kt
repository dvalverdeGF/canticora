package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.model.tablature.TuningString

/**
 * **Los datos de cuerda de un instrumento** (D-49): el puente entre una **altura** y el par
 * **(cuerda, traste)**, y las operaciones del diálogo «String data» de MuseScore.
 *
 * MuseScore guarda esto en `StringData` (`src/engraving/dom/stringdata.h`), que tiene dos piezas:
 *
 *  - `m_frets`: el **número de trastes** del instrumento. No es decorativo: `StringData::fret()`
 *    devuelve `INVALID_FRET_INDEX` si el traste se sale de `0..m_frets`, y `convertPitch()` lo usa
 *    para decidir si una altura cabe en el mástil.
 *  - `m_stringTable`: una lista de `instrString { pitch, open, startFret, useFlat }`
 *    (`stringdata.h:42-53`), **ordenada de grave a aguda** —el mismo orden que [Tuning.strings]—.
 *
 * De los cuatro campos de `instrString` Canticora implementa **tres**:
 *
 *  - `pitch`: la altura al aire. Ya existía en [TuningString.pitch].
 *  - `open` («Always open»): la cuerda **no está sobre el mástil** (tiorba, la 5.ª del banjo). En una
 *    tablatura solo se puede escribir el traste `0` en ella; `StringData::fret()` lo comprueba con
 *    `(fret > 0 && strg.open)`. Es [TuningString.alwaysOpen].
 *  - `useFlat`: escribir la altura con bemoles en vez de sostenidos. Es [TuningString.useFlat].
 *  - `startFret`: **solo** para la 5.ª cuerda del banjo de cinco cuerdas, que arranca en el traste 5.
 *    MuseScore lo **calcula solo** en `StringData::configBanjo5thString()` (`stringdata.cpp:807-823`)
 *    y **no** lo expone en el diálogo; Canticora no tiene banjo de cinco cuerdas, así que se
 *    documenta como desviación en vez de fingir que existe.
 *
 * **El orden de las cuerdas es el de MuseScore**: [Tuning.strings] va de **grave a aguda** y el
 * número de lectura va al revés (1 = la más aguda). Todas las funciones de este objeto hablan en
 * **número de lectura**, que es lo que ve el usuario.
 */
object TabStringData {

    /** Trastes mínimos: un instrumento sin trastes sigue teniendo cuerdas al aire. */
    const val MIN_FRETS = 0

    /**
     * Trastes máximos. MuseScore no fija un máximo explícito en el `QSpinBox` del diálogo
     * (`editstringdata.ui:89-99`), así que Qt aplica su tope por defecto, **99**.
     */
    const val MAX_FRETS = 99

    /** Cuerdas mínimas: un instrumento sin cuerdas no se puede tabular. */
    const val MIN_STRINGS = 1

    /** Cuerdas máximas: por encima de doce la rejilla deja de ser legible. */
    const val MAX_STRINGS = 12

    /** Trastes por defecto de una guitarra, el de `instruments.xml` (`<frets>19</frets>`). */
    const val DEFAULT_FRETS = 19

    // ---------------------------------------------------------------------------------------------
    // Altura ↔ (cuerda, traste)
    // ---------------------------------------------------------------------------------------------

    /**
     * Altura que suena al pulsar [fret] en la cuerda [stringNumber], o `null` si no existe.
     *
     * Transcripción de `StringData::getPitch()` (`stringdata.cpp:484-496`):
     * `pitch = strg.pitch - pitchOffset + (strg.open ? 0 : fret)`. Canticora no tiene instrumentos
     * transpositores en la tablatura, así que `pitchOffset` es siempre `0`.
     */
    fun pitchAt(tuning: Tuning, stringNumber: Int, fret: Int): ScorePitch? {
        val string = tuning.strings.getOrNull(tuning.strings.size - stringNumber) ?: return null
        if (fret < 0) return null
        val offset = if (string.alwaysOpen) 0 else fret
        return if (offset == 0) string.pitch else ScorePitch.fromMidi(string.pitch.midi + offset)
    }

    /**
     * Traste en el que se toca [pitch] en la cuerda [stringNumber], o `null` si no se puede.
     *
     * Transcripción de `StringData::fret()` (`stringdata.cpp:504-530`). Las tres guardas finales son
     * las de MuseScore: traste negativo, traste por encima del mástil y **traste `> 0` en una cuerda
     * «siempre al aire»**.
     */
    fun fretAt(tuning: Tuning, stringNumber: Int, pitch: ScorePitch): Int? {
        val string = tuning.strings.getOrNull(tuning.strings.size - stringNumber) ?: return null
        val fret = pitch.midi - string.pitch.midi
        if (fret < 0 || fret > tuning.frets) return null
        if (fret > 0 && string.alwaysOpen) return null
        return fret
    }

    /**
     * La **cuerda más aguda posible** para [pitch], como `StringData::convertPitch()`
     * (`stringdata.cpp:423-470`): recorre la tabla de la aguda a la grave y se queda con la primera
     * cuerda cuya altura al aire no supere la pedida.
     *
     * Devuelve `null` si la altura queda por debajo de la cuerda más grave.
     */
    fun convertPitch(tuning: Tuning, pitch: ScorePitch): TabPosition? {
        if (tuning.strings.isEmpty()) return null
        for (index in tuning.strings.indices.reversed()) {
            val string = tuning.strings[index]
            if (pitch.midi < string.pitch.midi) continue
            val stringNumber = tuning.strings.size - index
            val fret = fretAt(tuning, stringNumber, pitch) ?: continue
            return TabPosition(stringNumber, fret)
        }
        return null
    }

    /**
     * Si se puede **escribir** [fret] en la cuerda [stringNumber]. Es la regla del tooltip del
     * diálogo de MuseScore: *«On tablature staves, fret positions other than '0' cannot be entered
     * on strings marked 'always open'»*.
     */
    fun canEnterFret(tuning: Tuning, stringNumber: Int, fret: Int): Boolean {
        val string = tuning.strings.getOrNull(tuning.strings.size - stringNumber) ?: return false
        if (fret < 0 || fret > tuning.frets) return false
        return fret == 0 || !string.alwaysOpen
    }

    // ---------------------------------------------------------------------------------------------
    // Nombres de altura
    // ---------------------------------------------------------------------------------------------

    private val SHARP_NAMES = listOf("c", "c♯", "d", "d♯", "e", "f", "f♯", "g", "g♯", "a", "a♯", "b")
    private val FLAT_NAMES = listOf("c", "d♭", "d", "e♭", "e", "f", "g♭", "g", "a♭", "a", "b♭", "b")

    /**
     * Nombre de la altura como en `pitch2string()` (`utils.cpp:427-443`): letra minúscula, alteración
     * y **octava científica** (`C4` = Do central). Con [useFlat] se escribe con bemoles.
     *
     * MuseScore pone la letra en **mayúscula** para las octavas negativas; aquí se hace igual.
     */
    fun pitchLabel(pitch: ScorePitch, useFlat: Boolean = false): String {
        val names = if (useFlat) FLAT_NAMES else SHARP_NAMES
        val name = names[pitch.chromaticIndex]
        val letter = if (pitch.octave < 0) name.uppercase() else name
        return "$letter${pitch.octave}"
    }

    /** Altura a partir de un número MIDI, con la grafía que pida [useFlat]. */
    fun midiToPitch(midi: Int, useFlat: Boolean = false): ScorePitch {
        val octave = midi / 12 - 1
        val index = ((midi % 12) + 12) % 12
        val (step, alter) = if (useFlat) {
            FLAT_STEPS[index]
        } else {
            SHARP_STEPS[index]
        }
        return ScorePitch(step, alter, octave)
    }

    private val SHARP_STEPS = listOf(
        'C' to 0, 'C' to 1, 'D' to 0, 'D' to 1, 'E' to 0, 'F' to 0,
        'F' to 1, 'G' to 0, 'G' to 1, 'A' to 0, 'A' to 1, 'B' to 0
    )
    private val FLAT_STEPS = listOf(
        'C' to 0, 'D' to -1, 'D' to 0, 'E' to -1, 'E' to 0, 'F' to 0,
        'G' to -1, 'G' to 0, 'A' to -1, 'A' to 0, 'B' to -1, 'B' to 0
    )

    // ---------------------------------------------------------------------------------------------
    // Operaciones del diálogo «String data»
    // ---------------------------------------------------------------------------------------------

    /** Cambia el número de trastes, acotado a `MIN_FRETS..MAX_FRETS`. */
    fun withFrets(tuning: Tuning, frets: Int): Tuning =
        tuning.copy(frets = frets.coerceIn(MIN_FRETS, MAX_FRETS))

    /** Cambia la altura al aire de una cuerda. */
    fun withPitch(tuning: Tuning, stringNumber: Int, pitch: ScorePitch, useFlat: Boolean = false): Tuning {
        val index = tuning.strings.size - stringNumber
        if (index !in tuning.strings.indices) return tuning
        return tuning.copy(
            strings = tuning.strings.toMutableList().also {
                it[index] = it[index].copy(pitch = pitch, useFlat = useFlat)
            }
        )
    }

    /** Marca o desmarca una cuerda como «siempre al aire». */
    fun withAlwaysOpen(tuning: Tuning, stringNumber: Int, alwaysOpen: Boolean): Tuning {
        val index = tuning.strings.size - stringNumber
        if (index !in tuning.strings.indices) return tuning
        return tuning.copy(
            strings = tuning.strings.toMutableList().also {
                it[index] = it[index].copy(alwaysOpen = alwaysOpen)
            }
        )
    }

    /**
     * Añade una cuerda **debajo** de [afterStringNumber], como el botón «New string…» de MuseScore:
     * `i = stringList->currentRow() + 1; if (i <= 0) i = stringList->rowCount();`
     * (`editstringdata.cpp:196-200`).
     *
     * La tabla se pinta al revés que la lista interna (la 1.ª, la más aguda, arriba), así que
     * insertar en la fila `r` de la tabla es insertar en el índice `size - r` de la lista. Con
     * [afterStringNumber] `0` —nada seleccionado— MuseScore manda la cuerda **al final de la tabla**,
     * que es la más grave: índice `0`.
     */
    fun addString(tuning: Tuning, afterStringNumber: Int, pitch: ScorePitch): Tuning {
        if (tuning.strings.size >= MAX_STRINGS) return tuning
        val index = if (afterStringNumber <= 0) {
            0
        } else {
            (tuning.strings.size - afterStringNumber).coerceIn(0, tuning.strings.size)
        }
        return tuning.copy(
            strings = tuning.strings.toMutableList().also { it.add(index, TuningString(pitch)) }
        )
    }

    /** Quita una cuerda. No deja el instrumento sin ninguna. */
    fun removeString(tuning: Tuning, stringNumber: Int): Tuning {
        if (tuning.strings.size <= MIN_STRINGS) return tuning
        val index = tuning.strings.size - stringNumber
        if (index !in tuning.strings.indices) return tuning
        return tuning.copy(strings = tuning.strings.toMutableList().also { it.removeAt(index) })
    }

    /** Si la afinación es utilizable: al menos una cuerda y los trastes en rango. */
    fun isValid(tuning: Tuning): Boolean =
        tuning.strings.size in MIN_STRINGS..MAX_STRINGS && tuning.frets in MIN_FRETS..MAX_FRETS

    /** Cuerdas que **no** están sobre el mástil, como `StringData::frettedStrings()` al revés. */
    fun alwaysOpenStrings(tuning: Tuning): List<Int> =
        tuning.strings.indices
            .filter { tuning.strings[it].alwaysOpen }
            .map { tuning.strings.size - it }
}
