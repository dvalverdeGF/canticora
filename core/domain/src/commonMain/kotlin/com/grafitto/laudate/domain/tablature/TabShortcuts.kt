package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure

/**
 * **Mapa de teclado del editor de tablatura**, paridad con **MuseScore Studio 4** (ADR-020).
 *
 * Kotlin puro y determinista: la UI traduce el `KeyEvent` a un carácter y aquí se decide **qué
 * significa**. Cada entrada cita la fuente del manual de MuseScore 4
 * (`appendix/all-keyboard-shortcuts.md`, sección *Note input ▸ Tablature*).
 *
 * Se **retiran** los atajos de técnica inventados (`x h p s m v t u l`, `,`, `<`, `>`…): MuseScore no
 * los tiene y **chocan** con las letras `A`–`K` (trastes) y con `Q`/`W` (duración). Las técnicas se
 * aplican desde la **paleta** o el **menú contextual**.
 */
object TabShortcuts {

    /** Teclas que escriben un **traste** con una sola pulsación (`0`–`9`). */
    fun fretDigit(code: Char): Int? = when (code) {
        in '0'..'9' -> code - '0'
        else -> null
    }

    /**
     * Teclas **`A`–`K`** (sin la `I`) que escriben los trastes **0–9**, como en la *tablatura francesa*
     * de MuseScore («Fret numbers 0 to 9 can also be entered with the letter keys A to K, skipping
     * I»). Se usa el índice de la letra en el alfabeto sin `I`: `A`=0, `B`=1 … `H`=7, `J`=8, `K`=9.
     */
    private const val LETTER_KEYS = "ABCDEFGHJK"

    /** Traste que escribe la letra [code], o `null` si no es una de las `A`–`K` (sin `I`). */
    fun fretLetterDigit(code: Char): Int? {
        val index = LETTER_KEYS.indexOf(code.uppercaseChar())
        return if (index >= 0) index else null
    }

    /**
     * Figura de duración que fija `Shift`+`0`..`9` en el **pentagrama de tablatura**
     * (*Set duration (TAB)*). En TAB, `0` es la 128ª … y cada dígito sube una figura:
     *
     * | Tecla | Figura |
     * | :--- | :--- |
     * | `Shift`+`0` | 128ª (fuera del modelo de Canticora) |
     * | `Shift`+`4` | **Corchea** |
     * | `Shift`+`5` | **Negra** |
     * | `Shift`+`6` | **Blanca** |
     * | `Shift`+`7` | **Redonda** |
     *
     * Se devuelven **solo** las figuras que el modelo de Canticora soporta; las que no existen en el
     * enum (128ª, 64ª, 32ª, longa, doble redonda) devuelven `null` y **no** cambian la duración.
     */
    fun shiftDigitFigure(code: Char): RhythmFigure? = when (code) {
        '4' -> RhythmFigure.EIGHTH
        '5' -> RhythmFigure.QUARTER
        '6' -> RhythmFigure.HALF
        '7' -> RhythmFigure.WHOLE
        '3' -> RhythmFigure.SIXTEENTH
        else -> null
    }

    /**
     * Orden de las figuras **sonoras**, de la más corta a la más larga. Es el eje de `Q` (acortar) y
     * `W` (alargar) y de las teclas de duración. Se listan solo las simples (sin puntillo): el
     * puntillo se alterna aparte con `.` y al cambiar de figura se conserva.
     */
    private val FIGURE_ORDER: List<RhythmFigure> = listOf(
        RhythmFigure.SIXTEENTH,
        RhythmFigure.EIGHTH,
        RhythmFigure.QUARTER,
        RhythmFigure.HALF,
        RhythmFigure.WHOLE
    )

    /**
     * **`Q`**: acorta la duración de la nota seleccionada a la figura simple anterior. Si ya es la más
     * corta, devuelve `null` (no se puede acortar más). Con una figura **con puntillo** se devuelve su
     * versión simple (negra con puntillo → negra), que es «acortar» sin salir del paso de la rejilla.
     *
     * El **puntillo se quita siempre antes de bajar de figura**: en el modelo de Canticora las
     * figuras se ordenan sobre el eje de las **simples** (16ª, 8ª, negra, blanca, redonda) y el
     * puntillo es un modificador aparte (la tecla `.`), así que `Q` sobre una negra con puntillo da
     * una negra, y solo un `Q` más baja a corchea.
     */
    fun shorter(figure: RhythmFigure): RhythmFigure? {
        val plain = noteOf(figure) ?: return null
        if (plain != figure) return plain
        val index = FIGURE_ORDER.indexOf(plain)
        return FIGURE_ORDER.getOrNull(index - 1)
    }

    /**
     * **`W`**: alarga la duración de la nota seleccionada a la figura simple siguiente. Si ya es la más
     * larga (redonda), devuelve `null`.
     *
     * Como en [`shorter`], el **eje es el de las figuras simples**: al alargar se pierde el puntillo
     * (el puntillo se gestiona aparte con `.`).
     */
    fun longer(figure: RhythmFigure): RhythmFigure? {
        val plain = noteOf(figure) ?: return null
        val index = FIGURE_ORDER.indexOf(plain)
        return FIGURE_ORDER.getOrNull(index + 1)
    }

    /**
     * **`.`**: alterna el **puntillo** de una figura sonora. Como el modelo de Canticora no tiene
     * todas las combinaciones con puntillo (falta la **semicorchea con puntillo** a propósito, ver
     * `RhythmFigure`), una figura que no tiene versión con puntillo devuelve `null` y la tecla no hace
     * nada: es mejor no cambiar nada que inventar una duración que rompa la rejilla.
     */
    fun toggleDot(figure: RhythmFigure): RhythmFigure? = when (figure) {
        RhythmFigure.WHOLE, RhythmFigure.DOTTED_HALF -> RhythmFigure.HALF
        RhythmFigure.HALF -> RhythmFigure.DOTTED_HALF
        RhythmFigure.QUARTER -> RhythmFigure.DOTTED_QUARTER
        RhythmFigure.DOTTED_QUARTER -> RhythmFigure.QUARTER
        RhythmFigure.EIGHTH -> RhythmFigure.DOTTED_EIGHTH
        RhythmFigure.DOTTED_EIGHTH -> RhythmFigure.EIGHTH
        else -> null
    }

    /**
     * Figura **sonora** equivalente a [figure] (le quita el puntillo si lo tiene), o `null` si
     * [figure] es un silencio. La usan `Q`/`W`: cambiar la duración de un **silencio** no tiene el
     * mismo sentido que el de una nota, y en MuseScore `Q`/`W` actúan sobre la selección.
     */
    private fun noteOf(figure: RhythmFigure): RhythmFigure? = when (figure) {
        RhythmFigure.WHOLE, RhythmFigure.HALF, RhythmFigure.QUARTER,
        RhythmFigure.EIGHTH, RhythmFigure.SIXTEENTH -> figure
        RhythmFigure.DOTTED_HALF -> RhythmFigure.HALF
        RhythmFigure.DOTTED_QUARTER -> RhythmFigure.QUARTER
        RhythmFigure.DOTTED_EIGHTH -> RhythmFigure.EIGHTH
        else -> null
    }

    /**
     * **`↑`/`↓` en modo normal**: sube o baja el **traste** de la nota seleccionada **sin cambiar de
     * cuerda** («To increment or decrement, without changing the string: Press Up/Down»). El traste se
     * acota a `0..maxFret`: en los extremos la tecla no hace nada.
     */
    fun incrementFret(pluck: com.grafitto.laudate.domain.model.tablature.TabPluck, delta: Int, maxFret: Int = MAX_FRET): com.grafitto.laudate.domain.model.tablature.TabPluck? {
        if (delta == 0) return null
        val fret = (pluck.fret + delta).coerceIn(0, maxFret)
        if (fret == pluck.fret) return null
        return pluck.copy(position = pluck.position.copy(fret = fret))
    }

    /**
     * **`Alt`+`Shift`+`↑`/`↓`**: sube o baja el **traste** de la nota, **cambiando de cuerda** si hace
     * falta para conseguir el traste más bajo posible («The fret mark automatically changes strings,
     * if necessary, to achieve the lowest possible fret position»).
     *
     * A diferencia de `Ctrl`+`↑`/`↓` (que **conserva la altura** y busca la misma nota en otra cuerda),
     * aquí cambia la **altura** en un semitono y recoloca la nota en la cuerda donde el traste sea
     * **más bajo**, que es lo que hace MuseScore. Devuelve la nueva altura (cuerda y traste) o `null` si
     * la nota no cabe en ningún sitio.
     */
    fun moveFretDiatonic(
        pluck: com.grafitto.laudate.domain.model.tablature.TabPluck,
        tuning: com.grafitto.laudate.domain.model.tablature.Tuning,
        direction: Int,
        maxFret: Int = MAX_FRET
    ): com.grafitto.laudate.domain.model.tablature.TabPluck? {
        if (direction == 0) return null
        val midi = tuning.pitchOf(pluck.stringNumber)?.let { open ->
            if (pluck.fret == 0) open.midi else open.midi + pluck.fret
        } ?: return null
        val target = midi + direction
        if (target !in 0..127) return null
        // Se busca en **todas** las cuerdas la posición con el traste **más bajo** (y, a igualdad, la
        // cuerda más grave, como hace MuseScore al recolocar).
        var best: Pair<Int, Int>? = null
        for (string in 1..tuning.stringCount) {
            val open = tuning.pitchOf(string)?.midi ?: continue
            val fret = target - open
            if (fret !in 0..maxFret) continue
            if (best == null || fret < best!!.second) best = string to fret
        }
        val (string, fret) = best ?: return null
        return pluck.copy(position = pluck.position.copy(stringNumber = string, fret = fret))
    }
}
