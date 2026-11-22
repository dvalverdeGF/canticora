package com.grafitto.laudate.domain.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.StrumRange
import com.grafitto.laudate.domain.model.rhythm.TimeSignature

/**
 * Notación de **texto** de un patrón rítmico: la forma más rápida de pasar un ritmo a otra
 * persona, porque cabe en un mensaje y se puede pegar en cualquier parte.
 *
 * ```text
 * Canticora · Ritmo «Balada suave»
 * Compás 4/4
 * ↓ ↑ · ↓ | ↓ ↑ · ↑
 * ```
 *
 * La línea de golpes es la **misma notación de la rejilla** (`↓` `↑` `×` `·` `–`), con `|` en cada
 * pulso. Solo se usa cuando el patrón encaja en una subdivisión fina; si tiene figuras con puntillo
 * se recurre al archivo JSON, que conserva el modelo exacto.
 *
 * Es Dominio puro y hace **ida y vuelta** con el modelo: lo que se copia y se pega reconstruye el
 * mismo patrón, con sus registros de cuerdas (`g` = graves, `a` = agudos).
 */
object RhythmTextNotation {

    private const val NAME_PREFIX = "Canticora · Ritmo «"
    private const val MEASURE_PREFIX = "Compás"
    private const val BARS_PREFIX = "Compases"

    /** Marcas de registro de cuerdas anexadas al símbolo del golpe. */
    private const val BASS_MARK = 'g'
    private const val TREBLE_MARK = 'a'

    /** Primera línea del texto, usada como cabecera. */
    private fun header(name: String): String = "$NAME_PREFIX$name»"

    /** Caracteres admitidos en la línea de golpes. */
    private val notationArrows = listOf(StrumDirection.DOWN.arrow, StrumDirection.UP.arrow, StrumDirection.MUTE.arrow)
    private val notationExtras = " ·–|"

    /** Comprueba si un texto contiene un ritmo pegable. */
    fun looksLikeRhythmText(text: String): Boolean {
        val lines = text.lines()
        return lines.any { it.trim().startsWith(MEASURE_PREFIX) } &&
            lines.any { isNotationLine(it) }
    }

    /** True si la línea es exclusivamente notación de golpes (no texto corriente). */
    private fun isNotationLine(line: String): Boolean {
        val body = line.trim()
        if (body.isEmpty()) return false
        if (notationArrows.none { body.contains(it) }) return false
        return body.all { char ->
            char.isWhitespace() || notationExtras.contains(char) ||
                notationArrows.any { it.length == 1 && it[0] == char } ||
                char == BASS_MARK || char == TREBLE_MARK
        }
    }

    /**
     * Convierte un patrón en texto pegable.
     *
     * Usa la subdivisión **más gruesa** que represente el patrón sin pérdida, para que la línea
     * salga lo más corta y legible posible.
     */
    fun encode(pattern: RhythmPattern): String {
        val resolution = RhythmGridMapping.bestResolution(pattern)
        val barCount = pattern.barCount
        val cells = RhythmGridMapping.toGrid(pattern, resolution, barCount).cells
        val perBeat = resolution.slotsPerBeat(pattern.timeSignature)

        val body = cells.mapIndexed { index, cell ->
            val token = cellToken(cell)
            val endsBeat = (index + 1) % perBeat == 0 && index != cells.lastIndex
            if (endsBeat) "$token |" else token
        }.joinToString(" ")

        return buildString {
            appendLine(header(pattern.name))
            appendLine("$MEASURE_PREFIX ${pattern.timeSignature}")
            if (barCount > 1) appendLine("$BARS_PREFIX $barCount")
            append(body)
        }
    }

    private fun cellToken(cell: RhythmGridCell): String = when {
        cell.isAttack -> buildString {
            append(cell.direction.arrow)
            when (cell.range) {
                StrumRange.BASS -> append(BASS_MARK)
                StrumRange.TREBLE -> append(TREBLE_MARK)
                StrumRange.ALL -> Unit
            }
        }
        cell.isHeld -> "\u2013"
        else -> "\u00B7"
    }

    /** Resultado de interpretar un texto: el patrón o el motivo del fallo. */
    sealed interface DecodeResult {
        data class Success(val pattern: RhythmPattern) : DecodeResult
        data class Failure(val reason: String) : DecodeResult
    }

    /**
     * Reconstruye un patrón a partir de un texto copiado.
     *
     * Es tolerante: ignora líneas vacías y espacios sobrantes, y admite que falten la cabecera o el
     * nombre. Lo único imprescindible es el compás y la línea de golpes.
     */
    fun decode(text: String): DecodeResult {
        val lines = text.lines()

        val measure = lines.firstOrNull { it.trim().startsWith(MEASURE_PREFIX) }
            ?.substringAfter(MEASURE_PREFIX)?.trim()
            ?.let { TimeSignature.parse(it) }
            ?: return DecodeResult.Failure("No se encontró el compás (por ejemplo «Compás 4/4»).")

        val notationLine = lines.firstOrNull { isNotationLine(it) }
            ?: return DecodeResult.Failure("No se encontró la línea de rasgueo (↓ ↑ · …).")

        val tokens = notationLine.trim().split(Regex("\\s+")).filter { it != "|" }
        val cells = tokens.map { token ->
            when (token) {
                "\u2013" -> RhythmGridCell.Hold
                "\u00B7" -> RhythmGridCell.Air
                else -> parseAttack(token) ?: RhythmGridCell.Air
            }
        }

        // La resolución se deduce del NÚMERO de casillas del texto, no al revés: si se eligiera la
        // más gruesa primero, un ritmo de corcheas se truncaría a la mitad de sus golpes.
        val barCount = lines.firstOrNull { it.trim().startsWith(BARS_PREFIX) }
            ?.substringAfter(BARS_PREFIX)?.trim()?.toIntOrNull()
            ?.coerceAtLeast(1)
            ?: 1

        val resolution = RhythmGridResolution.entries.firstOrNull {
            it.supports(measure) && it.slotCount(measure) * barCount == cells.size
        } ?: RhythmGridResolution.entries.firstOrNull {
            it.supports(measure) && it.slotCount(measure) == cells.size
        } ?: RhythmGridResolution.entries.firstOrNull {
            it.supports(measure) && it.slotCount(measure) >= cells.size
        } ?: RhythmGridResolution.SIXTEENTH
        val expected = resolution.slotCount(measure) * barCount
        val fitted = when {
            cells.size == expected -> cells
            cells.size < expected -> cells + List(expected - cells.size) { RhythmGridCell.Air }
            else -> cells.take(expected)
        }

        val name = lines.firstOrNull { it.contains(NAME_PREFIX) }
            ?.substringAfter(NAME_PREFIX)?.substringBefore('»')?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "Ritmo importado"

        val events = RhythmGridMapping.toEvents(RhythmGrid(resolution, fitted), measure)
        if (events.isEmpty()) return DecodeResult.Failure("El ritmo no contiene ningún golpe.")

        return DecodeResult.Success(
            RhythmPattern(
                id = "imported",
                name = name,
                timeSignature = measure,
                events = events,
                isBuiltIn = false
            )
        )
    }

    /** Interpreta un golpe (`↓`, `↑g`, `×`…) con su registro de cuerdas, si lo lleva. */
    private fun parseAttack(token: String): RhythmGridCell? {
        val head = token.firstOrNull()?.toString() ?: return null
        val direction = when (head) {
            StrumDirection.DOWN.arrow -> StrumDirection.DOWN
            StrumDirection.UP.arrow -> StrumDirection.UP
            StrumDirection.MUTE.arrow -> StrumDirection.MUTE
            else -> return null
        }
        val range = when {
            token.contains(TREBLE_MARK) -> StrumRange.TREBLE
            token.contains(BASS_MARK) -> StrumRange.BASS
            else -> StrumRange.ALL
        }
        return RhythmGridCell(direction, isHeld = false, range = range)
    }
}
