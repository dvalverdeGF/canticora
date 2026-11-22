package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tuning

/**
 * **Barra de estado** del editor de tablaturas, con el **vocabulario de MuseScore 4** (B-1 del plan
 * de paridad).
 *
 * MuseScore describe el elemento seleccionado con una cadena de campos separados por `;`
 * (`Note::accessibleInfo` + `EngravingItem::formatBarsAndBeats` + `Staff N`):
 *
 * ```
 * Note; Pitch: G; String: 3; Fret: 0; Duration: Quarter; Voice: 1; Measure: 2; Beat: 1; Staff 1
 * ```
 *
 * Aquí se traduce al español y se **añade la octava** a la altura (`Sol3`), que es lo que el usuario
 * necesita para saber **qué va a sonar** sin contar trastes. El orden de los campos es el de
 * MuseScore, para que quien venga de allí lea la misma línea.
 *
 * Es una función **pura** de Dominio: la UI solo la pinta.
 */
object TabStatusBar {

    /** Nombre del elemento cuando el cursor está sobre una nota. */
    const val NOTE = "Nota"

    /** Nombre del elemento cuando el cursor está sobre un silencio. */
    const val REST = "Silencio"

    /** Nombre del elemento cuando el cursor está sobre una casilla vacía. */
    const val EMPTY = "Casilla vacía"

    /** Texto que se muestra cuando no hay cursor (nada seleccionado). */
    const val NO_SELECTION = "Sin selección"

    /**
     * Línea de estado del cursor de escritura.
     *
     * @param measures compases del documento (para leer la casilla y su figura).
     * @param measureIndex compás del cursor (0-based).
     * @param slotIndex columna del cursor (0-based).
     * @param stringNumber cuerda activa (1 = la más aguda).
     * @param tuning afinación, para calcular la **altura** que suena.
     * @param resolution subdivisión del documento, para el **tiempo** cuando la casilla no tiene figura.
     * @param voice voz activa (1–4), la que se usaría al escribir.
     */
    fun text(
        measures: List<TabMeasure>,
        measureIndex: Int,
        slotIndex: Int,
        stringNumber: Int,
        tuning: Tuning,
        resolution: TabResolution,
        voice: Int
    ): String {
        val measure = measures.getOrNull(measureIndex) ?: return NO_SELECTION
        val slot = measure.slots.getOrNull(slotIndex)
        val pluck = slot?.strings?.firstOrNull { it.stringNumber == stringNumber }
        val figure = slot?.rhythmicFigure
        val isRest = slot != null && slot.strings.isEmpty() && !slot.isHeld
        val kind = when {
            pluck != null -> NOTE
            slot == null -> EMPTY
            isRest -> REST
            else -> NOTE
        }
        val fields = mutableListOf(kind)
        // **Altura**: solo las notas con traste la tienen (una cuerda apagada `X` no suena).
        if (pluck != null && !pluck.isMuted) {
            pitchLabel(tuning, stringNumber, pluck.fret)?.let { fields += "Altura: $it" }
        }
        fields += "Cuerda: $stringNumber"
        if (pluck != null) fields += "Traste: ${pluck.fret}"
        figure?.let { fields += "Duración: ${durationOf(it, isRest)}" }
        fields += "Voz: ${slot?.voice ?: voice}"
        fields += "Compás: ${measureIndex + 1}"
        fields += "Tiempo: ${beatLabel(measure, slotIndex, resolution)}"
        fields += "Pentagrama 1"
        return fields.joinToString("; ")
    }

    /**
     * **Altura** que suena al pisar el traste [fret] de la cuerda [stringNumber], en nomenclatura
     * latina con octava (`Sol3`, `Do#4`). `null` si la cuerda no existe en la afinación.
     */
    fun pitchLabel(tuning: Tuning, stringNumber: Int, fret: Int): String? {
        val open = tuning.pitchOf(stringNumber) ?: return null
        val pitch = ScorePitch.fromMidi(open.midi + fret)
        return pitch.toPitchClass().preferredLatinName + pitch.octave
    }

    /**
     * **Tiempo** (pulso) del compás en el que cae la columna [slotIndex], en la notación de MuseScore:
     * `1` para el primer pulso, `1,5` para la mitad del primero, `2` para el segundo… Se cuenta en
     * **negras** desde el inicio del compás, sumando la duración de las columnas anteriores.
     */
    fun beatLabel(measure: TabMeasure, slotIndex: Int, resolution: TabResolution): String {
        val unit = TablatureEditing.figureForResolution(resolution).beatValue
        val beats = measure.slots.take(slotIndex.coerceAtLeast(0)).sumOf { slot ->
            slot.rhythmicFigure?.beatValue ?: unit
        }
        val beat = beats + 1.0
        // Sin decimales cuando es un pulso entero (`1`, `2`), con una cifra cuando no (`1,5`).
        val rounded = kotlin.math.round(beat * 100.0) / 100.0
        return if (rounded == kotlin.math.floor(rounded)) {
            rounded.toInt().toString()
        } else {
            rounded.toString().replace('.', ',')
        }
    }

    /**
     * **Duración** de la figura activa, para la barra de escritura: el nombre de la figura o, si no hay
     * ninguna elegida, la de la subdivisión del compás.
     */
    fun durationLabel(figure: RhythmFigure?, resolution: TabResolution): String {
        val resolved = figure ?: TablatureEditing.figureForResolution(resolution)
        return durationOf(resolved, resolved.isRest)
    }

    /**
     * Nombre de la figura tal y como lo escribe MuseScore: la nota se nombra sola (`Negra`) y el
     * silencio lleva su prefijo (`Silencio de negra`), para que la barra de estado no confunda una
     * nota con un silencio de la misma duración.
     */
    private fun durationOf(figure: RhythmFigure, isRest: Boolean): String =
        if (isRest) figure.label else figure.noteName.replaceFirstChar { it.uppercase() }
}
