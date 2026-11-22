package com.grafitto.laudate.data.tablature

import com.grafitto.laudate.domain.tablature.TabNotePlayer
import com.grafitto.laudate.domain.tablature.TabToneRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/**
 * [TabNotePlayer] para **escritorio (JVM)**: reproduce el sonido de una nota al editarla, sintetizado
 * con el motor **puro** compartido ([TabToneRenderer]) sobre `javax.sound.sampled`. Mismo enfoque que
 * [com.grafitto.laudate.data.rhythm.DesktopRhythmPlayer] (ADR-012/ADR-013): cada pulsación abre una
 * [SourceDataLine] corta y la cierra al terminar, de modo que notas rápidas no se pisan.
 *
 * Un fallo de audio **nunca** debe tumbar la app: si la línea no está disponible, simplemente no suena.
 */
class DesktopTabNotePlayer : TabNotePlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun playMidi(midiNotes: List<Int>) = playMidi(midiNotes, TabToneRenderer.DEFAULT_NOTE_MS)

    override fun playMidi(midiNotes: List<Int>, durationMs: Int) {
        val pcm = TabToneRenderer.renderChord(midiNotes, durationMs = durationMs)
        if (pcm.isEmpty() || midiNotes.none { it in 0..127 }) return
        scope.launch {
            val format = AudioFormat(TabToneRenderer.DEFAULT_SAMPLE_RATE.toFloat(), 16, 1, true, false)
            val line = openLine(format) ?: return@launch
            try {
                val bytes = toLittleEndianBytes(pcm)
                line.open(format, bytes.size)
                line.start()
                line.write(bytes, 0, bytes.size)
                line.drain()
            } catch (_: Exception) {
                // Sin audio disponible: no suena, sin más.
            } finally {
                runCatching { line.stop() }
                runCatching { line.close() }
            }
        }
    }

    override fun release() {
        scope.cancel()
    }

    private fun openLine(format: AudioFormat): SourceDataLine? = try {
        val info = DataLine.Info(SourceDataLine::class.java, format)
        if (!AudioSystem.isLineSupported(info)) null else AudioSystem.getLine(info) as SourceDataLine
    } catch (_: Exception) {
        null
    }

    private fun toLittleEndianBytes(pcm: ShortArray): ByteArray {
        val bytes = ByteArray(pcm.size * 2)
        pcm.forEachIndexed { index, sample ->
            val value = sample.toInt()
            bytes[index * 2] = (value and 0xFF).toByte()
            bytes[index * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
