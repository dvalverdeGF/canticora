package com.grafitto.laudate.data.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.rhythm.RhythmPcmRenderer
import com.grafitto.laudate.domain.rhythm.RhythmPlaybackSchedule
import com.grafitto.laudate.domain.rhythm.RhythmPlaybackScheduler
import com.grafitto.laudate.domain.rhythm.RhythmPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/**
 * Reproductor de patrones rítmicos para **escritorio (JVM)**.
 *
 * Sintetiza el sonido con `javax.sound.sampled` reutilizando el **mismo** sintetizador puro de
 * Dominio que Android ([RhythmPcmRenderer]): un ciclo completo del patrón se renderiza a PCM y se
 * escribe en bucle en una [SourceDataLine]. En paralelo, una corrutina emite el índice del evento
 * activo para que la UI lo resalte (RF-307), sincronizada con el mismo [RhythmPlaybackScheduler].
 *
 * Así el escritorio suena **exactamente igual** que el móvil, sin duplicar la lógica de síntesis.
 * Ver ADR-012/ADR-013.
 */
class DesktopRhythmPlayer : RhythmPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var line: SourceDataLine? = null

    private var job: Job? = null

    override val isPlaying: Boolean
        get() = job?.isActive == true

    override fun play(pattern: RhythmPattern, bpm: Int, onStep: (Int) -> Unit) {
        stop()
        val schedule = RhythmPlaybackScheduler.build(pattern, bpm)
        if (schedule.isEmpty) return

        job = scope.launch {
            val format = AudioFormat(
                RhythmPcmRenderer.DEFAULT_SAMPLE_RATE.toFloat(),
                16,
                1,
                true,
                false
            )
            val audio = openLine(format) ?: return@launch
            line = audio
            try {
                val pcm = RhythmPcmRenderer.renderCycle(schedule, RhythmPcmRenderer.DEFAULT_SAMPLE_RATE)
                val bytes = toLittleEndianBytes(pcm)
                audio.open(format, bytes.size * BUFFER_CYCLES)
                audio.start()

                coroutineScope {
                    launch(Dispatchers.Default) { runHighlighter(schedule, onStep) }

                    var running = true
                    while (isActive && running) {
                        var offset = 0
                        while (isActive && offset < bytes.size) {
                            val written = audio.write(bytes, offset, bytes.size - offset)
                            if (written <= 0) {
                                running = false
                                break
                            }
                            offset += written
                        }
                    }
                }
            } catch (_: Exception) {
                // Un fallo de audio no debe tumbar la app: la UI simplemente no suena.
            } finally {
                if (line === audio) line = null
                runCatching { audio.stop() }
                runCatching { audio.close() }
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        // Desbloquea el `write` en curso; el cierre lo hace el propio job en su `finally`.
        line?.let { runCatching { it.stop() } }
    }

    /** Detiene la reproducción y libera el ámbito de corrutinas del reproductor. */
    fun release() {
        stop()
        scope.cancel()
    }

    private fun openLine(format: AudioFormat): SourceDataLine? = try {
        val info = DataLine.Info(SourceDataLine::class.java, format)
        if (!AudioSystem.isLineSupported(info)) {
            null
        } else {
            AudioSystem.getLine(info) as SourceDataLine
        }
    } catch (_: Exception) {
        null
    }

    /** Convierte PCM mono de 16 bits a bytes en orden little-endian (formato de `SourceDataLine`). */
    private fun toLittleEndianBytes(pcm: ShortArray): ByteArray {
        val bytes = ByteArray(pcm.size * 2)
        pcm.forEachIndexed { index, sample ->
            val value = sample.toInt()
            bytes[index * 2] = (value and 0xFF).toByte()
            bytes[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    /** Emite el índice del evento activo en cada golpe, ciclo tras ciclo. */
    private suspend fun runHighlighter(schedule: RhythmPlaybackSchedule, onStep: (Int) -> Unit) {
        while (true) {
            val cycleStart = System.nanoTime()
            schedule.steps.forEach { step ->
                val targetNanos = step.offsetMillis * 1_000_000L
                val remaining = targetNanos - (System.nanoTime() - cycleStart)
                if (remaining > 0) delay(remaining / 1_000_000L)
                onStep(step.eventIndex)
            }
            val cycleNanos = schedule.cycleDurationMillis * 1_000_000L
            val remaining = cycleNanos - (System.nanoTime() - cycleStart)
            if (remaining > 0) delay(remaining / 1_000_000L)
        }
    }

    private companion object {
        /** Número de ciclos que caben en el buffer de la línea de audio. */
        const val BUFFER_CYCLES = 2
    }
}
