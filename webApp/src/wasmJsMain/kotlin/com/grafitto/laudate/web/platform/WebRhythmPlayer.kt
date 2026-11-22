package com.grafitto.laudate.web.platform

import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.platform.nowMillis
import com.grafitto.laudate.domain.rhythm.RhythmPlaybackScheduler
import com.grafitto.laudate.domain.rhythm.RhythmPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.js.js

/**
 * Reproductor de **patrones rítmicos** con Web Audio.
 *
 * El plan de golpes lo decide el Dominio ([RhythmPlaybackScheduler]); aquí solo se espera hasta el
 * instante de cada golpe y se dispara un clic con un oscilador. La reproducción es cíclica, como en
 * Android y escritorio, y avisa del **índice del evento** para que la rejilla resalte el golpe.
 */
class WebRhythmPlayer : RhythmPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playback: Job? = null

    override var isPlaying: Boolean = false
        private set

    override fun play(pattern: RhythmPattern, bpm: Int, onStep: (Int) -> Unit) {
        stop()
        val schedule = RhythmPlaybackScheduler.build(pattern, bpm)
        if (schedule.isEmpty) return
        isPlaying = true
        playback = scope.launch {
            while (isActive) {
                val cycleStart = nowMillis()
                schedule.steps.forEach { step ->
                    val wait = cycleStart + step.offsetMillis - nowMillis()
                    if (wait > 0) delay(wait)
                    clickJs(step.isAccent, step.isStaccato)
                    onStep(step.eventIndex)
                }
                val rest = cycleStart + schedule.cycleDurationMillis - nowMillis()
                if (rest > 0) delay(rest)
                if (!isActive) break
            }
        }
    }

    override fun stop() {
        playback?.cancel()
        playback = null
        isPlaying = false
    }
}

/**
 * Un clic del metrónomo. El contexto de audio se crea **al primer golpe** (los navegadores exigen
 * un gesto del usuario) y se reutiliza para no abrir un contexto por golpe.
 */
private fun clickJs(accent: Boolean, staccato: Boolean): String = js(
    """(function () {
        var AudioCtor = window.AudioContext || window.webkitAudioContext;
        if (!AudioCtor) return '';
        if (!window.__canticoraAudio) window.__canticoraAudio = new AudioCtor();
        var context = window.__canticoraAudio;
        if (context.state === 'suspended') context.resume();
        var now = context.currentTime;
        var oscillator = context.createOscillator();
        var gain = context.createGain();
        var duration = staccato ? 0.045 : 0.08;
        oscillator.type = 'square';
        oscillator.frequency.value = accent ? 1760 : 1174;
        gain.gain.setValueAtTime(accent ? 0.22 : 0.12, now);
        gain.gain.exponentialRampToValueAtTime(0.0001, now + duration);
        oscillator.connect(gain);
        gain.connect(context.destination);
        oscillator.start(now);
        oscillator.stop(now + duration);
        return '';
    })()"""
)
