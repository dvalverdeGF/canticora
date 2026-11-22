package com.grafitto.laudate.domain.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import kotlin.math.abs

/**
 * Reglas de digitación automática de la mano derecha para patrones de rasgueo.
 *
 * Invariante métrica (regla del péndulo):
 *  - Sobre la subdivisión más fina usada en el patrón se alterna ↓ ↑ ↓ ↑.
 *  - El primer golpe de **cada pulso** del compás vuelve siempre a ↓.
 *  - Un **silencio** reinicia el péndulo: el golpe inmediatamente posterior vuelve a ↓.
 *  - Los golpes percusivos (`MUTE`, `×`) se respetan tal cual: son una decisión del usuario.
 *
 * La rejilla se calcula por el máximo común divisor de las duraciones (en subdivisiones de
 * semicorchea) para soportar correctamente figuras con puntillo.
 *
 * Ejemplos en 4/4:
 *  - Negras:        ↓ ↓ ↓ ↓
 *  - Corcheas:      ↓ ↑ ↓ ↑ ↓ ↑ ↓ ↑
 *  - Semicorcheas:  ↓ ↑ ↓ ↑ ↓ ↑ ↓ ↑ (reinicia ↓ en cada pulso)
 *  - Con silencio:  ↓ ↑ · ↓ ↑  → tras el silencio el péndulo vuelve a bajar
 */
object StrummingAutomation {

    /** Epsilon para comparaciones de coma flotante. */
    private const val EPSILON = 1e-6

    /** Subdivisión de referencia en negras (unidad mínima: semicorchea). */
    private const val BASE_SLOT = 0.25

    /**
     * Subdivisión de referencia del patrón: el MCD de las duraciones usadas,
     * acotado inferiormente por la semicorchea (0.25 negras).
     */
    private fun gridSlot(events: List<RhythmEvent>): Double {
        if (events.isEmpty()) return 1.0
        val units = events.map { (it.beatValue / BASE_SLOT).toLong() }.filter { it > 0 }
        if (units.isEmpty()) return 1.0
        var gcd = units.first()
        units.drop(1).forEach { value -> gcd = gcdOf(gcd, value) }
        return (gcd * BASE_SLOT).coerceAtLeast(BASE_SLOT)
    }

    private tailrec fun gcdOf(a: Long, b: Long): Long =
        if (b == 0L) abs(a) else gcdOf(b, a % b)

    /**
     * Recalcula la dirección de todos los golpes del patrón aplicando la regla del péndulo.
     * Los silencios y los golpes percusivos se conservan, pero los silencios reinician la alternancia.
     */
    fun apply(events: List<RhythmEvent>, timeSignature: TimeSignature = TimeSignature.FOUR_FOUR): List<RhythmEvent> {        if (events.isEmpty()) return events

        val slot = gridSlot(events)
        val beatUnit = timeSignature.beatUnitQuarterBeats
        var position = 0.0
        var forceDown = true // el primer golpe y el posterior a un silencio son ↓

        return events.map { event ->
            when {
                event.figure.isRest -> {
                    forceDown = true
                    position += event.beatValue
                    event.copy(direction = StrumDirection.NONE)
                }

                // Una ligadura continúa la nota anterior: conserva su dirección y no reinicia el péndulo.
                event.isTied -> {
                    position += event.beatValue
                    event
                }

                event.direction == StrumDirection.MUTE -> {
                    // El apagado percusivo es una decisión del usuario: se respeta.
                    forceDown = false
                    position += event.beatValue
                    event
                }

                else -> {
                    // Posición dentro del pulso actual del compás.
                    val offsetInBeat = position - (kotlin.math.floor(position / beatUnit) * beatUnit)
                    val slotIndex = (offsetInBeat / slot + EPSILON).toInt()
                    val direction = if (forceDown || slotIndex % 2 == 0) {
                        StrumDirection.DOWN
                    } else {
                        StrumDirection.UP
                    }
                    forceDown = false
                    position += event.beatValue
                    event.copy(direction = direction)
                }
            }
        }
    }

    /**
     * Calcula la dirección que le correspondería al siguiente golpe si se añadiera
     * al final del patrón, respetando la regla del péndulo.
     */
    fun nextDirection(
        events: List<RhythmEvent>,
        timeSignature: TimeSignature = TimeSignature.FOUR_FOUR
    ): StrumDirection {
        if (events.isEmpty()) return StrumDirection.DOWN

        val slot = gridSlot(events)
        val beatUnit = timeSignature.beatUnitQuarterBeats
        val position = events.sumOf { it.beatValue }
        val afterRest = events.last().figure.isRest
        val offsetInBeat = position - (kotlin.math.floor(position / beatUnit) * beatUnit)
        val slotIndex = (offsetInBeat / slot + EPSILON).toInt()

        return if (afterRest || slotIndex % 2 == 0) StrumDirection.DOWN else StrumDirection.UP
    }

    /**
     * Crea un golpe percusivo (apagado) que el usuario puede intercalar.
     */
    fun muteStroke(figure: RhythmFigure = RhythmFigure.QUARTER): RhythmEvent =
        RhythmEvent(figure = figure, direction = StrumDirection.MUTE)
}
