package com.grafitto.laudate.domain.tablature

/**
 * **Metrónomo configurable** (D-66).
 *
 * Antes el metrónomo era un **interruptor**: sonaba o no sonaba, siempre al volumen del motor y
 * siempre un clic por **pulsación** del compás. En MuseScore el metrónomo es un ajuste con volumen
 * propio, acento del primer tiempo y —en el panel de reproducción— la posibilidad de **subdividir**
 * la pulsación para estudiar pasajes lentos. Eso es lo que se modela aquí.
 *
 * Los valores se guardan **normalizados** (`0..1` el volumen) y no en la escala de la UI (0–100 %),
 * por la misma razón que en [TabMixer]: el motor no debe saber cómo se pinta el control y el redondeo
 * de la pantalla no se acumula en el audio.
 *
 * [subdivision] es **cuántos clics suenan por pulsación**: `1` = la pulsación sola (lo normal), `2` =
 * corcheas, `3` = tresillos, `4` = semicorcheas. Los clics de subdivisión **no** llevan acento: el
 * acento marca el pulso, y si lo llevaran todos dejaría de oírse el compás.
 *
 * [accentFirst] permite **apagar** el acento. No es un capricho: con el metrónomo muy bajo el clic
 * acentuado suena distinto y hay quien prefiere un pulso uniforme para no perder el tempo.
 */
data class TabMetronome(
    /** El metrónomo suena durante la reproducción. */
    val enabled: Boolean = false,
    /** Volumen propio del metrónomo, `0..1`, **independiente** del volumen de la tablatura. */
    val volume: Float = DEFAULT_VOLUME,
    /** Acentúa la primera pulsación del compás (y la mitad en los compases compuestos). */
    val accentFirst: Boolean = true,
    /** Clics por pulsación: `1` pulsación, `2` corcheas, `3` tresillos, `4` semicorcheas. */
    val subdivision: Int = 1
) {
    /** Volumen acotado a `0..1`. */
    val safeVolume: Float get() = volume.coerceIn(MIN_VOLUME, MAX_VOLUME)

    /** Subdivisión acotada a `1..MAX_SUBDIVISION`. */
    val safeSubdivision: Int get() = subdivision.coerceIn(MIN_SUBDIVISION, MAX_SUBDIVISION)

    /** Volumen en **tanto por ciento** (`0..100`), que es lo que enseña la UI. */
    val volumePercent: Int get() = (safeVolume * 100f).toInt()

    /**
     * Rótulo de la subdivisión tal y como se lee en un método: `Pulsación`, `Corcheas`, `Tresillos`,
     * `Semicorcheas`. Es el nombre de la **figura** que se oye, no el número de clics.
     */
    val subdivisionLabel: String
        get() = when (safeSubdivision) {
            1 -> "Pulsación"
            2 -> "Corcheas"
            3 -> "Tresillos"
            else -> "Semicorcheas"
        }

    companion object {
        /** Metrónomo por defecto: apagado, al 80 % y sin subdividir. */
        val DEFAULT = TabMetronome()

        /** Volumen mínimo (0 %). */
        const val MIN_VOLUME: Float = 0f

        /** Volumen máximo (100 %). */
        const val MAX_VOLUME: Float = 1f

        /** Volumen de fábrica: 80 %, para que el clic se oiga sin tapar la guitarra. */
        const val DEFAULT_VOLUME: Float = 0.8f

        /** Sin subdividir: un clic por pulsación. */
        const val MIN_SUBDIVISION: Int = 1

        /** Subdivisión máxima: semicorcheas. */
        const val MAX_SUBDIVISION: Int = 4
    }
}
