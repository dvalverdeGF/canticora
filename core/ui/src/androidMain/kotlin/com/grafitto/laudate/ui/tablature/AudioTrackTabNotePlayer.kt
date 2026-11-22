package com.grafitto.laudate.ui.tablature

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.grafitto.laudate.domain.tablature.TabNotePlayer
import com.grafitto.laudate.domain.tablature.TabToneRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * [TabNotePlayer] para **Android**: reproduce el sonido de una nota al editarla, sintetizado con el
 * motor **puro** compartido ([TabToneRenderer]) sobre un [AudioTrack] en modo *static* (buffer
 * pequeño, se escribe la nota completa y se libera). Mismo enfoque que
 * [AudioTrackRhythmPlayer] (ADR-012): no se empaqueta ningún fichero de audio.
 *
 * Un fallo de audio **nunca** debe tumbar la app: si la pista no se puede crear, simplemente no suena.
 */
class AudioTrackTabNotePlayer : TabNotePlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun playMidi(midiNotes: List<Int>) = playMidi(midiNotes, TabToneRenderer.DEFAULT_NOTE_MS)

    override fun playMidi(midiNotes: List<Int>, durationMs: Int) {
        val pcm = TabToneRenderer.renderChord(midiNotes, durationMs = durationMs)
        if (pcm.isEmpty() || midiNotes.none { it in 0..127 }) return
        scope.launch {
            val sampleRate = resolveSampleRate()
            val track = createTrack(sampleRate, pcm.size) ?: return@launch
            try {
                // `write` en modo static escribe el buffer completo; se espera con `play`+`sleep`.
                track.write(pcm, 0, pcm.size)
                track.play()
                // Duración aproximada de la nota + pequeña cola, para no cortar el decaimiento.
                val millis = (pcm.size * 1000L / sampleRate) + 40
                kotlinx.coroutines.delay(millis)
            } catch (_: Exception) {
                // Sin audio disponible: no suena, sin más.
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
    }

    override fun release() {
        scope.cancel()
    }

    private fun resolveSampleRate(): Int {
        val native = runCatching {
            AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)
        }.getOrDefault(0)
        return if (native > 0) native else TabToneRenderer.DEFAULT_SAMPLE_RATE
    }

    private fun createTrack(sampleRate: Int, samples: Int): AudioTrack? = try {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferBytes = (samples * 2).coerceAtLeast(minBuffer)
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
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
    } catch (_: Exception) {
        null
    }
}
