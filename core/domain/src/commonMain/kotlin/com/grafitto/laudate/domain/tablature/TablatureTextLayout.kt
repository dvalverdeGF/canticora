package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.Tablature

/**
 * Maquetación **pura** de una tablatura como rejilla monoespaciada (ADR-017 §4.5).
 *
 * No necesita fuente musical: es texto. La cuerda más **aguda** se dibuja **arriba** (orden de
 * dibujo invertido respecto al modelo). Los separadores `|` se **derivan** del compás, no se
 * almacenan.
 *
 * Es puro y testeable al 100 %: la UI solo pinta el resultado con una fuente monoespaciada.
 */
object TablatureTextLayout {

    /** Ancho de cada casilla (nº de caracteres para la cifra del traste + separación). */
    private const val CELL_WIDTH = 3

    /** Nombre de la cuerda para la etiqueta izquierda, según la octava (p. ej. `E2` → `E`). */
    fun stringLabel(pitchName: String, octave: Int): String = pitchName

    /**
     * Genera las líneas de la tablatura, una por cuerda ya en **orden de dibujo** (aguda arriba).
     * Cada línea termina sin `|` final; los compases se separan con `|`.
     */
    fun render(tablature: Tablature): List<TablatureLine> {
        val drawn = tablature.tuning.drawnStrings() // (1..n) aguda primero
        return drawn.map { (stringNumber, string) ->
            val body = buildString {
                tablature.measures.forEach { measure ->
                    // Barras de repetición (RF-127): `|:` al abrir el compás, `:` antes del cierre.
                    append(if (measure.repeat == TabRepeatMark.START || measure.repeat == TabRepeatMark.BOTH) "|:" else "|")
                    append(renderMeasure(measure, stringNumber))
                    if (measure.repeat == TabRepeatMark.END || measure.repeat == TabRepeatMark.BOTH) append(':')
                }
                append('|')
            }
            TablatureLine(
                stringNumber = stringNumber,
                label = stringLabel(string.pitch.step.uppercaseChar().toString(), string.pitch.octave),
                body = body
            )
        }
    }

    /** Etiqueta de la afinación comprimida, p. ej. `EADGBE` (grave → aguda, convención estándar). */
    fun tuningLabel(tablature: Tablature): String =
        tablature.tuning.strings.joinToString("") { it.pitch.step.uppercaseChar().toString() }

    private fun renderMeasure(measure: TabMeasure, stringNumber: Int): String =
        measure.slots.joinToString("") { slot ->
            val pluck = slot.strings.firstOrNull { it.stringNumber == stringNumber }
            when {
                slot.isHeld && pluck == null -> center("~")
                pluck == null -> center("-")
                else -> center(fretText(pluck.fret, pluck.techniques))
            }
        }

    /** Centra el contenido en una casilla de [CELL_WIDTH] caracteres. */
    private fun center(text: String): String {
        if (text.length >= CELL_WIDTH) return text
        val totalPad = CELL_WIDTH - text.length
        val left = totalPad / 2
        val right = totalPad - left
        return " ".repeat(left) + text + " ".repeat(right)
    }
}

/** Una línea de la tablatura: una cuerda con su etiqueta y el cuerpo de casillas. */
data class TablatureLine(
    val stringNumber: Int,
    val label: String,
    val body: String
)
