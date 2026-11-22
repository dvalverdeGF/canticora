package com.grafitto.laudate.ui.rhythm

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
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

/**
 * Implementación de [RhythmPlayer] que **sintetiza** el sonido de los rasgueos en tiempo real
 * con [AudioTrack], sin empaquetar ningún fichero de audio (ver ADR-012).
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
 *
 * Un ciclo completo del patrón se renderiza a PCM y se escribe **en bucle**; en paralelo, una
 * corrutina emite el índice del evento activo para que la UI lo resalte (RF-307), sincronizada
 * con el mismo [RhythmPlaybackScheduler] que genera el audio.
 *
 * **Robustez del flujo de audio** (pistas de fallos tipo «el sonido va y viene»):
 *  - Se usa la **frecuencia de muestreo nativa de la salida** ([AudioTrack.getNativeOutputSampleRate]):
 *    pedir 44.1 kHz cuando el dispositivo reproduce a 48 kHz obliga a remuestrear y produce artefactos.
 *  - No se pide `PERFORMANCE_MODE_LOW_LATENCY`, que con buffers de streaming grandes y en emuladores
 *    provoca *underruns* (cortes periódicos).
 *  - El **buffer interno es holgado** (varias decenas de ms) para absorber los tirones del planificador.
 *  - La escritura maneja **escrituras parciales**: si `write` no acepta todo el bloque, se continúa
 *    desde donde se quedó en lugar de reiniciar el ciclo (lo que perdía la cola y provocaba huecos).
 *  - El hilo que escribe es bloqueante por naturaleza, así que vive en [Dispatchers.IO] para no
 *    acaparar hilos del planificador por defecto.
 */
class AudioTrackRhythmPlayer : RhythmPlayer {

    /** Ámbito propio del reproductor; la escritura de audio se hace fuera del planificador por defecto. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var track: AudioTrack? = null

    private var job: Job? = null

    override val isPlaying: Boolean
        get() = job?.isActive == true

    override fun play(
        pattern: RhythmPattern,
        bpm: Int,
        onStep: (Int) -> Unit
    ) {
        stop()
        val schedule = RhythmPlaybackScheduler.build(pattern, bpm)
        if (schedule.isEmpty) return

        job = scope.launch {
            val sampleRate = resolveSampleRate()
            val audio = createTrack(sampleRate) ?: return@launch
            track = audio
            try {
                val pcm = RhythmPcmRenderer.renderCycle(schedule, sampleRate)
                audio.setVolume(AudioTrack.getMaxVolume())
                audio.play()

                // `coroutineScope` liga el resaltador al job de reproducción: al cancelar la
                // reproducción se cancelan también sus hijos, sin depender de un `finally`.
                coroutineScope {
                    launch(Dispatchers.Default) { runHighlighter(schedule, onStep) }

                    var running = true
                    while (isActive && running) {
                        var offset = 0
                        // Escritura bloqueante; puede devolver menos de lo pedido: se reanuda donde quedó.
                        while (isActive && offset < pcm.size) {
                            val written = audio.write(pcm, offset, pcm.size - offset)
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
                if (track === audio) track = null
                runCatching { audio.stop() }
                runCatching { audio.release() }
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        // Desbloquea el `write` en curso; el `release` lo hace el propio job en su `finally`.
        track?.let { runCatching { it.stop() } }
    }

    /** Detiene la reproducción incluso si aún no se había creado la pista de audio. */
    fun release() {
        stop()
        scope.cancel()
    }

    /** Frecuencia nativa de la salida; si no está disponible, cae a [DEFAULT_SAMPLE_RATE]. */
    private fun resolveSampleRate(): Int {
        val native = runCatching {
            AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        }.getOrDefault(0)
        return if (native > 0) native else DEFAULT_SAMPLE_RATE
    }

    /** Configura el [AudioTrack] en modo streaming con un buffer holgado pero de baja latencia. */
    private fun createTrack(sampleRate: Int): AudioTrack? = try {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            null
        } else {
            // Buffer holgado (~decenas de ms) para absorber tirones sin cortes audibles.
            val bufferBytes = maxOf(minBuffer, FRAME_BYTES_MONO_16BIT * MIN_FRAMES) * BUFFER_HEADROOM
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }
    } catch (_: Exception) {
        null
    }

    /** Emite el índice del evento activo en cada golpe, ciclo tras ciclo. */
    private suspend fun runHighlighter(
        schedule: RhythmPlaybackSchedule,
        onStep: (Int) -> Unit
    ) {
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

    companion object {
        /** Frecuencia de reserva si el dispositivo no declara una nativa. */
        const val DEFAULT_SAMPLE_RATE = RhythmPcmRenderer.DEFAULT_SAMPLE_RATE

        /** Tamaño de un frame en mono PCM de 16 bits. */
        private const val FRAME_BYTES_MONO_16BIT = 2

        /** Mínimo de frames del buffer (~43 ms a 48 kHz) para evitar cortes por inanición. */
        private const val MIN_FRAMES = 2048

        /** Margen sobre el buffer mínimo del sistema. */
        private const val BUFFER_HEADROOM = 2
    }
}
