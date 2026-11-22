package com.grafitto.laudate.domain.tablature

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Contrato **puro de Dominio** para sonar una o varias alturas al editar una tablatura.
 *
 * El motor de audio real vive en la capa de plataforma (`AudioTrack` en Android, `SourceDataLine`
 * en la JVM de escritorio, igual que el reproductor de ritmos, ADR-012/ADR-013). La síntesis, en
 * cambio, es **compartida y pura** ([TabToneRenderer]): las dos plataformas suenan igual.
 *
 * Se usa **solo en el editor**, al **confirmar** la nota de una columna (no en el visor de lectura).
 */
interface TabNotePlayer {

    /**
     * Reproduce las alturas [midiNotes] a la vez (un acorde). Ignora notas fuera de `0..127`; si la
     * lista queda vacía, no suena nada.
     */
    fun playMidi(midiNotes: List<Int>)

    /**
     * Reproduce [midiNotes] durante [durationMs] milisegundos. Es la variante que usa la
     * **reproducción de la partitura** (B-9): cada nota dura lo que dice su figura, no un valor fijo.
     *
     * La implementación por defecto **ignora** la duración y delega en [playMidi] (nota corta de
     * edición), de modo que un reproductor que no sepa sostener el sonido sigue funcionando.
     */
    fun playMidi(midiNotes: List<Int>, durationMs: Int) {
        playMidi(midiNotes)
    }

    /**
     * Reproduce [midiNotes] durante [durationMs] con el **mezclador** [mixer] (D-29): volumen,
     * balance, silencio y solo.
     *
     * La implementación por defecto **ignora** el mezclador y delega en la variante sin él, de modo
     * que un reproductor que no sepa aplicar ganancia o balance sigue sonando (a volumen fijo) en vez
     * de romperse. Las implementaciones de plataforma lo sobrescriben para aplicar [TabMixer.gain] y
     * [TabMixer.safePan].
     */
    fun playMidi(midiNotes: List<Int>, durationMs: Int, mixer: TabMixer) {
        playMidi(midiNotes, durationMs)
    }

    /** Libera los recursos de audio. */
    fun release()
}

/**
 * Sintetizador **puro** de una pulsación de cuerda: convierte una altura MIDI en un buffer PCM mono
 * de 16 bits (ataque rápido + decaimiento, con un armónico para dar cuerpo). Es determinista y
 * testeable, y se comparte entre Android y escritorio para que el sonido sea idéntico.
 */
object TabToneRenderer {

    /** Frecuencia de reserva si la plataforma no declara una nativa. */
    const val DEFAULT_SAMPLE_RATE = 48_000

    /** Duración de la nota (ms): suficiente para oírla, corta para no solaparse al teclear rápido. */
    const val DEFAULT_NOTE_MS = 600

    /** Tope de duración de una nota sostenida, para no reservar buffers enormes. */
    private const val MAX_NOTE_MS = 8_000

    private const val DECAY_RATE = 3.2
    private const val AMPLITUDE = 0.40

    /** Ataque muy corto (ms) para evitar el «clic» al empezar la muestra. */
    private const val ATTACK_MS = 3.0

    /** Frecuencia (Hz) de una altura MIDI (La4 = 69 = 440 Hz). */
    fun frequencyOf(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** Renderiza una altura MIDI a PCM mono de 16 bits. */
    fun renderNote(
        midi: Int,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        durationMs: Int = DEFAULT_NOTE_MS
    ): ShortArray = renderChord(listOf(midi), sampleRate, durationMs)

    /**
     * Renderiza varias alturas **mezcladas** (un acorde) a PCM mono de 16 bits, sumando sin saturar.
     * [durationMs] permite sostener la nota lo que dure su figura al reproducir la partitura.
     */
    fun renderChord(
        midiNotes: List<Int>,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        durationMs: Int = DEFAULT_NOTE_MS
    ): ShortArray {
        val notes = midiNotes.filter { it in 0..127 }.distinct()
        val ms = durationMs.coerceIn(1, MAX_NOTE_MS)
        val totalSamples = ((ms / 1000.0) * sampleRate).toInt().coerceAtLeast(1)
        val buffer = ShortArray(totalSamples)
        if (notes.isEmpty()) return buffer
        val attackSamples = (ATTACK_MS / 1000.0 * sampleRate).toInt().coerceAtLeast(1)

        notes.forEach { midi ->
            val f = frequencyOf(midi)
            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate
                val progress = i.toDouble() / totalSamples
                val envelope = exp(-DECAY_RATE * progress)
                // Fundamental + segundo armónico (con más caída) para un timbre de cuerda pulsada.
                val tone = sin(2.0 * PI * f * t) +
                    0.35 * sin(4.0 * PI * f * t) * exp(-DECAY_RATE * 1.4 * progress)
                val attack = if (i < attackSamples) i.toDouble() / attackSamples else 1.0
                val value = tone * envelope * attack * (AMPLITUDE / notes.size) * Short.MAX_VALUE
                val mixed = buffer[i] + value
                buffer[i] = mixed.coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                    .toInt().toShort()
            }
        }
        return buffer
    }
}
