package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.Tuning

/**
 * **Métodos de entrada de notas** de la tablatura (paridad con MuseScore, ADR-017), en Kotlin puro.
 *
 * MuseScore tiene dos formas de escribir la música y Canticora las reproduce:
 *
 *  1. **Modo introducción de notas** (`N`): se elige la **duración** y se teclean los **trastes**; el
 *     cursor avanza solo. Es el gesto secuencial para escribir una tirada de notas.
 *  2. **Selección directa por nombre**: con el modo `N` desactivado, se escribe la **letra** de la nota
 *     (`C D E F G A B`, cifrado anglosajón) y el motor de digitación ([FingeringEngine]) coloca la
 *     nota en la **cuerda y traste** más lógicos. Es el gesto «pienso en notas, no en trastes».
 *
 * Aquí vive la **aritmética** del método 2: qué altura corresponde a una letra (deduciendo la octava
 * que encaja en el instrumento) y en qué cuerda/traste se toca. Todo es determinista y testeable.
 */
object TabNoteInput {

    /** Letras del cifrado anglosajón aceptadas como entrada de nota (Do, Re, Mi, Fa, Sol, La, Si). */
    val NOTE_LETTERS: Set<Char> = setOf('C', 'D', 'E', 'F', 'G', 'A', 'B')

    /** Indica si [char] es una letra de nota (insensible a mayúsculas). */
    fun isNoteLetter(char: Char): Boolean = char.uppercaseChar() in NOTE_LETTERS

    /**
     * **Rango tocable** de una afinación en números MIDI: de la cuerda más grave al aire hasta la más
     * aguda en el traste máximo. Es el intervalo en el que tiene sentido buscar la octava de una nota.
     */
    fun playableRange(tuning: Tuning, maxFret: Int = MAX_FRET): IntRange {
        val open = tuning.strings.map { it.pitch.midi }
        if (open.isEmpty()) return 0..127
        return open.min()..(open.max() + maxFret).coerceAtMost(127)
    }

    /**
     * Alturas candidatas (una por **octava** posible) para una **letra** de nota dentro del rango
     * tocable del instrumento. Se ordenan de la **más centrada** a la más extrema, porque la nota
     * «natural» de una tablatura suele caer en la zona media del mástil.
     */
    fun candidatePitches(letter: Char, tuning: Tuning, maxFret: Int = MAX_FRET): List<ScorePitch> {
        val step = letter.uppercaseChar()
        if (!isNoteLetter(step)) return emptyList()
        val range = playableRange(tuning, maxFret)
        val center = (range.first + range.last) / 2
        return (0..9)
            .map { octave -> ScorePitch(step, 0, octave) }
            .filter { it.midi in range }
            .sortedBy { kotlin.math.abs(it.midi - center) }
    }

    /**
     * Coloca una **altura** en la tablatura: devuelve la pulsación (cuerda y traste) que el motor de
     * digitación considera mejor para [pitch], o `null` si no cabe en el mástil.
     */
    fun placePitch(pitch: ScorePitch, tuning: Tuning, options: FingeringOptions = FingeringOptions(tuning = tuning)): TabPluck? =
        FingeringEngine.assign(listOf(pitch), options.copy(tuning = tuning))
            .assignment
            .firstOrNull()

    /**
     * **Método 2**: para una **letra** de nota, elige la altura que mejor encaja en el instrumento y
     * la posición (cuerda y traste) donde tocarla. `null` si la letra no es una nota o no cabe.
     *
     * Se prueban las octavas de la más centrada a la más extrema y se devuelve la **primera** que el
     * motor de digitación puede colocar, que es la que suena en la zona natural del mástil.
     */
    fun placeLetter(letter: Char, tuning: Tuning, options: FingeringOptions = FingeringOptions(tuning = tuning)): TabPluck? {
        for (pitch in candidatePitches(letter, tuning, options.maxFret)) {
            val placed = placePitch(pitch, tuning, options)
            if (placed != null) return placed
        }
        return null
    }

    /**
     * **Corrige la cuerda** de una nota sin cambiar su altura (el `Ctrl+↑`/`Ctrl+↓` de MuseScore):
     * busca, entre las cuerdas [direction] más aguda/grave, la primera en la que la **misma altura**
     * sea un traste válido. Devuelve `null` si no hay ninguna (la nota no cabe en otra cuerda).
     */
    fun moveToAdjacentString(
        pluck: TabPluck,
        tuning: Tuning,
        direction: Int,
        maxFret: Int = MAX_FRET
    ): TabPluck? {
        if (direction == 0) return null
        val pitchMidi = tuning.pitchOf(pluck.stringNumber)?.let { open ->
            if (pluck.fret == 0) open.midi else open.midi + pluck.fret
        } ?: return null
        val step = if (direction > 0) 1 else -1
        var string = pluck.stringNumber + step
        while (string in 1..tuning.stringCount) {
            val open = tuning.pitchOf(string) ?: break
            val fret = pitchMidi - open.midi
            if (fret in 0..maxFret) return pluck.copy(position = pluck.position.copy(stringNumber = string, fret = fret))
            string += step
        }
        return null
    }
}
