package com.grafitto.laudate.domain.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumDirection

/**
 * Un golpe programado dentro de un ciclo del patrón rítmico.
 *
 * Es la unidad que consumen tanto el **audio** (para sintetizar el sonido) como la **UI**
 * (para resaltar la posición activa). Al derivarse de un único plan, sonido y resaltado
 * quedan sincronizados por construcción.
 *
 * @property eventIndex Índice del [com.grafitto.laudate.domain.model.rhythm.RhythmEvent] de origen.
 * @property offsetMillis Instante de inicio del golpe respecto al comienzo del ciclo.
 * @property direction Dirección efectiva del golpe (`DOWN`, `UP` o `MUTE`).
 * @property isAccent Indica si el golpe lleva acento.
 * @property isArpeggio Indica si el golpe se arpegia (se despliega en el tiempo).
 * @property isStaccato Indica si el golpe es staccato (envolvente más corta).
 */
data class RhythmPlaybackStep(
    val eventIndex: Int,
    val offsetMillis: Long,
    val direction: StrumDirection,
    val isAccent: Boolean,
    val isArpeggio: Boolean,
    val isStaccato: Boolean
) {
    /** Indica si el paso representa un golpe sonoro real. */
    val isStruck: Boolean get() = direction.isStruck
}

/**
 * Plan de reproducción de un patrón: la traducción de `(patrón, bpm)` a una secuencia
 * de golpes con su tiempo en milisegundos. Es **Kotlin puro** (sin `android.*`), por lo que
 * se puede testear en la JVM y reutilizar en multiplataforma.
 *
 * @property bpm Pulso por minuto efectivo (ya acotado al rango válido).
 * @property steps Golpes ordenados por [RhythmPlaybackStep.offsetMillis].
 * @property cycleDurationMillis Duración de un compás completo en milisegundos.
 */
data class RhythmPlaybackSchedule(
    val bpm: Int,
    val steps: List<RhythmPlaybackStep>,
    val cycleDurationMillis: Long
) {
    /** Un patrón sin golpes (todo silencios) no se puede reproducir. */
    val isEmpty: Boolean get() = steps.isEmpty()

    /**
     * Índice del evento que está **sonando** en un instante dado del ciclo.
     * Devuelve `null` si aún no ha empezado el primer golpe.
     */
    fun activeEventIndexAt(elapsedMillis: Long): Int? {
        var active: Int? = null
        for (step in steps) {
            if (step.offsetMillis <= elapsedMillis) active = step.eventIndex else break
        }
        return active
    }
}

/**
 * Convierte un [RhythmPattern] y un BPM en un [RhythmPlaybackSchedule].
 *
 * Es lógica **determinista y pura**: misma entrada → misma salida, sin estado ni dependencias
 * de plataforma. La duración de la negra es $60\,000 / \text{bpm}$ ms, y el inicio de cada
 * golpe se obtiene acumulando el `beatValue` de los eventos anteriores.
 */
object RhythmPlaybackScheduler {

    /** BPM por defecto de la previsualización (RF-306). */
    const val DEFAULT_BPM: Int = 80

    /** Rango admitido de BPM (coincide con el rango típico de [com.grafitto.laudate.domain.model.music.TempoMarking]). */
    const val MIN_BPM: Int = 40
    const val MAX_BPM: Int = 208

    /** Milisegundos que dura una negra a un BPM dado, con el valor ya acotado al rango válido. */
    fun quarterNoteMillis(bpm: Int): Double = 60_000.0 / bpm.coerceIn(MIN_BPM, MAX_BPM)

    /** Construye el plan de reproducción de un patrón a un BPM concreto. */
    fun build(pattern: RhythmPattern, bpm: Int = DEFAULT_BPM): RhythmPlaybackSchedule {
        val normalizedBpm = bpm.coerceIn(MIN_BPM, MAX_BPM)
        val millisPerQuarter = quarterNoteMillis(normalizedBpm)

        val steps = mutableListOf<RhythmPlaybackStep>()
        var cursorMillis = 0.0
        pattern.events.forEachIndexed { index, event ->
            val startMillis = cursorMillis
            cursorMillis += event.beatValue * millisPerQuarter
            if (event.isAttack) {
                steps += RhythmPlaybackStep(
                    eventIndex = index,
                    offsetMillis = startMillis.toLong(),
                    direction = event.effectiveDirection,
                    isAccent = event.isAccent,
                    isArpeggio = event.effectiveArticulation.isArpeggio,
                    isStaccato = event.effectiveArticulation.isStaccato
                )
            }
        }

        val cycleMillis = (pattern.targetBeatValue * millisPerQuarter).toLong()
        return RhythmPlaybackSchedule(
            bpm = normalizedBpm,
            steps = steps,
            cycleDurationMillis = cycleMillis.coerceAtLeast(1L)
        )
    }
}
