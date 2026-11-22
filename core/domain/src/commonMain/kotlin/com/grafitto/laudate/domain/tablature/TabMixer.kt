package com.grafitto.laudate.domain.tablature

/**
 * **Mezclador de la reproducción** (D-29): volumen, balance, silencio y solo.
 *
 * MuseScore tiene un panel de mezclador con una tira por pentagrama (volumen, *pan*, `M` y `S`). En
 * Canticora la tablatura es **un solo pentagrama**, así que la tira es una: lo que sí se conserva es
 * el **modelo** —volumen, balance, silencio y solo— para que el día que haya más de un pentagrama
 * (una tablatura con acompañamiento, o una partitura con varias voces) no haya que rehacerlo.
 *
 * Los valores se guardan **normalizados** (`0..1` el volumen, `-1..1` el balance) y no en la escala
 * de la UI (0–100 %, L100–R100): así el motor no tiene que saber cómo se pinta el control y el
 * redondeo de la pantalla no se acumula en el audio.
 *
 * [solo] **no** silencia nada por sí mismo cuando hay una sola tira —no hay nada que comparar—, pero
 * se guarda y se expone porque es parte del estado del mezclador y la UI lo pinta como un interruptor
 * con memoria. Con varias tiras, [isAudible] es la regla: suena la que tiene `solo` y, si ninguna lo
 * tiene, todas las que no están en silencio.
 */
data class TabMixer(
    /** Volumen de `0` (nada) a `1` (el máximo del motor). */
    val volume: Float = 1f,
    /** Balance: `-1` todo a la izquierda, `0` al centro, `1` todo a la derecha. */
    val pan: Float = 0f,
    /** Silencio (`M` de MuseScore). Manda sobre [volume]. */
    val muted: Boolean = false,
    /** Solo (`S` de MuseScore): con varias tiras, silencia las demás. */
    val solo: Boolean = false
) {
    /** Volumen acotado a `0..1`. */
    val safeVolume: Float get() = volume.coerceIn(0f, 1f)

    /** Balance acotado a `-1..1`. */
    val safePan: Float get() = pan.coerceIn(-1f, 1f)

    /**
     * **Ganancia efectiva** que se le pide al motor: `0` si está en silencio, el volumen si no.
     * Es el único número que el reproductor necesita.
     */
    val gain: Float get() = if (muted) 0f else safeVolume

    /** `true` si no debe sonar nada. */
    val isSilent: Boolean get() = gain <= 0f

    /** `true` si esta tira suena, dado si **alguna** lleva `solo`. */
    fun isAudible(anySolo: Boolean = solo): Boolean =
        if (anySolo) solo && !muted else !muted

    /** Volumen en **tanto por ciento** (`0..100`), que es lo que enseña la UI. */
    val volumePercent: Int get() = (safeVolume * 100f).toInt()

    /**
     * Rótulo del balance como en una mesa de mezclas: `C` (centro), `I25`/`D25` (25 % a la izquierda
     * o a la derecha). Se redondea al entero para que el control no enseñe decimales.
     */
    val panLabel: String
        get() {
            val percent = (safePan * 100f).toInt()
            return when {
                percent == 0 -> "C"
                percent < 0 -> "I${-percent}"
                else -> "D$percent"
            }
        }

    companion object {
        /** Mezclador por defecto: volumen máximo, centrado, sin silencio ni solo. */
        val DEFAULT = TabMixer()

        /** Volumen mínimo audible que se puede fijar desde la UI (0 %). */
        const val MIN_VOLUME: Float = 0f

        /** Volumen máximo (100 %). */
        const val MAX_VOLUME: Float = 1f

        /** Balance extremo a la izquierda. */
        const val MIN_PAN: Float = -1f

        /** Balance extremo a la derecha. */
        const val MAX_PAN: Float = 1f
    }
}
