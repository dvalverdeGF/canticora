package com.grafitto.laudate.domain.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmPattern

/**
 * Contrato **puro de Dominio** para reproducir sonoramente un patrón rítmico.
 *
 * El motor de audio real vive en la capa de plataforma (implementación con `AudioTrack`,
 * ver ADR-012); esta interfaz mantiene el Dominio libre de `android.*` y deja lista la
 * puerta para Kotlin Multiplatform (`expect/actual`).
 *
 * La reproducción es **cíclica**: repite el patrón indefinidamente hasta que se invoca
 * [stop]. En cada golpe se notifica su índice de evento para que la UI pueda resaltarlo.
 */
interface RhythmPlayer {

    /** Indica si hay una reproducción en curso. */
    val isPlaying: Boolean

    /**
     * Comienza a reproducir [pattern] a [bpm].
     *
     * Si ya había una reproducción activa, se detiene antes de empezar la nueva.
     *
     * @param onStep Se invoca en cada golpe con el **índice del evento** que suena en ese
     *   instante. Puede invocarse desde un hilo de fondo: el consumidor debe actualizar el
     *   estado de forma segura (p. ej. `StateFlow.update`).
     */
    fun play(
        pattern: RhythmPattern,
        bpm: Int = RhythmPlaybackScheduler.DEFAULT_BPM,
        onStep: (Int) -> Unit = {}
    )

    /** Detiene la reproducción y libera los recursos de audio asociados. */
    fun stop()
}
