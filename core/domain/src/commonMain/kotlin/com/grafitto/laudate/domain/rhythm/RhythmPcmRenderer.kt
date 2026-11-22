package com.grafitto.laudate.domain.rhythm

import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import kotlin.math.exp
import kotlin.random.Random

/**
 * Sintetizador **puro de Dominio** que convierte un [RhythmPlaybackSchedule] en un ciclo de
 * audio PCM mono de 16 bits.
 *
 * Vive en `commonMain` (sin `android.*` ni `javax.sound.*`) para que **Android y escritorio
 * compartan exactamente el mismo sonido**: la plataforma solo se encarga de volcar el buffer
 * a su salida de audio (`AudioTrack` en Android, `SourceDataLine` en la JVM). Ver ADR-012/ADR-013.
 *
 * Cada golpe es una envolvente corta de **ruido filtrado** cuyo timbre depende de la dirección
 * y la articulación del evento:
 *
 * | Golpe | Timbre |
 * | :--- | :--- |
 * | `DOWN` (↓) | Grave y sostenido (filtro más cerrado, cola más larga) |
 * | `UP` (↑) | Más agudo y breve |
 * | `MUTE` (×) | Percusivo y seco (ruido sin filtrar) |
 * | *acento* | Sube el pico de amplitud |
 * | *staccato* | Acorta la envolvente |
 * | *arpegio* | Despliega el golpe en varios ataques ligeramente retardados |
 */
object RhythmPcmRenderer {

    /** Frecuencia de reserva si la plataforma no declara una nativa. */
    const val DEFAULT_SAMPLE_RATE = 48_000

    private const val SEED = 20260930L
    private const val DECAY_RATE = 5.0
    private const val ACCENT_GAIN = 1.6
    private const val STACCATO_FACTOR = 0.5
    private const val ARPEGGIO_ATTACKS = 3
    private const val ARPEGGIO_SPREAD_MS = 14.0

    /** Parámetros de timbre de un golpe según su dirección. */
    private data class StrumVoice(val durationMs: Int, val amplitude: Double, val cutoff: Double)

    private fun voiceFor(direction: StrumDirection): StrumVoice = when (direction) {
        StrumDirection.DOWN -> StrumVoice(durationMs = 90, amplitude = 0.55, cutoff = 0.10)
        StrumDirection.UP -> StrumVoice(durationMs = 60, amplitude = 0.45, cutoff = 0.32)
        StrumDirection.MUTE -> StrumVoice(durationMs = 32, amplitude = 0.60, cutoff = 0.90)
        StrumDirection.NONE -> StrumVoice(durationMs = 40, amplitude = 0.30, cutoff = 0.50)
    }

    /**
     * Renderiza un compás completo del patrón a PCM mono de 16 bits.
     *
     * @param schedule Plan de reproducción (golpes + duración del ciclo).
     * @param sampleRate Frecuencia de muestreo de la salida.
     * @param seed Semilla del ruido, para que el resultado sea determinista y testeable.
     */
    fun renderCycle(
        schedule: RhythmPlaybackSchedule,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        seed: Long = SEED
    ): ShortArray {
        val totalSamples = ((schedule.cycleDurationMillis / 1000.0) * sampleRate).toInt().coerceAtLeast(1)
        val buffer = ShortArray(totalSamples)
        val random = Random(seed)
        schedule.steps.forEach { step ->
            renderStep(buffer, step, sampleRate, random)
        }
        return buffer
    }

    private fun renderStep(buffer: ShortArray, step: RhythmPlaybackStep, sampleRate: Int, random: Random) {
        val voice = voiceFor(step.direction)
        val amplitude = if (step.isAccent) voice.amplitude * ACCENT_GAIN else voice.amplitude
        val durationMs = if (step.isStaccato) (voice.durationMs * STACCATO_FACTOR).toInt() else voice.durationMs
        val startSample = ((step.offsetMillis / 1000.0) * sampleRate).toInt()

        if (step.isArpeggio) {
            // Arpegio: tres ataques breves y decrecientes, ligeramente retardados.
            val spread = ((ARPEGGIO_SPREAD_MS / 1000.0) * sampleRate).toInt()
            repeat(ARPEGGIO_ATTACKS) { i ->
                renderPluck(
                    buffer = buffer,
                    startSample = startSample + i * spread,
                    durationMs = durationMs,
                    amplitude = amplitude * (1.0 - i * 0.25),
                    cutoff = voice.cutoff,
                    sampleRate = sampleRate,
                    random = random
                )
            }
        } else {
            renderPluck(buffer, startSample, durationMs, amplitude, voice.cutoff, sampleRate, random)
        }
    }

    /**
     * Escribe un golpe (ruido filtrado con decaimiento exponencial) en [buffer] sumando sin saturar.
     * [cutoff] es el coeficiente del filtro paso-bajo de un polo: menor = más grave/oscuro.
     */
    private fun renderPluck(
        buffer: ShortArray,
        startSample: Int,
        durationMs: Int,
        amplitude: Double,
        cutoff: Double,
        sampleRate: Int,
        random: Random
    ) {
        val durationSamples = ((durationMs / 1000.0) * sampleRate).toInt().coerceAtLeast(1)
        var filtered = 0.0
        for (i in 0 until durationSamples) {
            val index = startSample + i
            if (index >= buffer.size) break
            if (index < 0) continue
            val progress = i.toDouble() / durationSamples
            val envelope = exp(-DECAY_RATE * progress)
            val noise = random.nextDouble() * 2.0 - 1.0
            filtered += cutoff * (noise - filtered)
            val value = filtered * envelope * amplitude * Short.MAX_VALUE
            val mixed = buffer[index] + value
            buffer[index] = mixed.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble()).toInt().toShort()
        }
    }
}
