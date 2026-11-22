package com.grafitto.laudate.ui.song

import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.song.LineType
import com.grafitto.laudate.domain.model.song.SongLine

/**
 * Decide qué se pinta de cada línea del canto.
 *
 * El cancionero real trae **filas de acordes sin letra** (intros, `(bis)` de solo acordes): son
 * líneas con `lyrics` vacía y `chords` poblados. Sin esta decisión, esas líneas pintaban una fila
 * de acordes seguida de una **línea de letra vacía** que reservaba altura y descuadraba el bloque.
 *
 * La lógica se extrae aquí, en Kotlin puro, para poder probarla sin instrumentación.
 */
data class SongLineRenderPlan(
    val showChords: Boolean,
    val showLyrics: Boolean
) {
    /** `true` si la línea no aporta nada visible y debe omitirse por completo. */
    val isEmpty: Boolean get() = !showChords && !showLyrics

    /** `true` si la línea solo lleva acordes (intro, riff, `(bis)` instrumental). */
    val isChordOnly: Boolean get() = showChords && !showLyrics

    companion object {
        /**
         * Calcula el plan de render de una línea.
         *
         * Los comentarios y encabezados ([LineType.COMMENT] / [LineType.HEADER]) se pintan aparte,
         * así que devuelven un plan vacío.
         */
        fun forLine(line: SongLine, displayMode: SongDisplayMode): SongLineRenderPlan {
            if (line.type == LineType.COMMENT || line.type == LineType.HEADER) {
                return SongLineRenderPlan(showChords = false, showLyrics = false)
            }

            val hasChords = line.chords.isNotEmpty()
            // Una fila solo-acordes declara su tipo explícitamente; en cualquier caso, una línea
            // sin letra pero con acordes se trata como bloque instrumental.
            val hasLyrics = line.lyrics.isNotBlank()
            val isChordOnlyLine = line.type == LineType.CHORD_ONLY || (hasChords && !hasLyrics)

            // Las tres capas (letra, acordes, tablatura) se activan de forma independiente según el
            // modo. La tablatura NO se decide aquí: se pinta aparte; este plan solo gobierna la
            // columna de letra/acordes.
            return SongLineRenderPlan(
                showChords = displayMode.showsChords && hasChords,
                showLyrics = displayMode.showsLyrics && hasLyrics && !isChordOnlyLine
            )
        }
    }
}
