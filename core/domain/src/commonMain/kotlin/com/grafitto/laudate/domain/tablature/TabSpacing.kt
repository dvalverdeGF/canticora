package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import kotlin.math.pow

/**
 * **Espaciado horizontal por duración** (roadmap C.1, arregla F-04). Parte de la idea de
 * `TGLayout.getSpacingForQuarter` de TuxGuitar (§0.3.1 del roadmap) —el ancho de una columna sale de
 * **cuánto dura**, no de una rejilla uniforme—, pero con la **ley real de MuseScore** medida del
 * `salmo2.pdf`, que es **sub-lineal** y no la lineal de TuxGuitar:
 *
 * ```java
 * // TuxGuitar (ley lineal, descartada)
 * getSpacingForQuarter(d) = (QUARTER_TIME / d.getTime()) * getDurationWidth(d)
 * ```
 *
 * Hasta ahora **todas** las columnas medían lo mismo ([TabLayoutSettings.cellWidth]); un compás de 8
 * corcheas ocupaba igual que uno de 2 negras, que es lo que MuseScore **no** hace. Aquí el ancho de
 * cada columna es `cellWidth × duración^0,585`, con la negra como ancla: una negra mide [cellWidth],
 * una corchea `1/1,499` de eso y una redonda ~2,25 (no 4). Ver [columnWidth].
 *
 * Kotlin puro y determinista: lo comparten el editor, el visor y el PDF.
 */
object TabSpacing {

    /**
     * **Tiempo de una negra** en las unidades de duración del motor (el `QUARTER_TIME = 960` de
     * TuxGuitar). Aquí la duración se mide en **negras**, así que vale `1.0`.
     */
    const val QUARTER_TIME: Double = 1.0

    // ── La ley de espaciado REAL (medida del `salmo2.pdf`, 2026-10-12) ────────────────────────────
    //
    // MuseScore **no** reparte el ancho de forma lineal con la duración. Medido del PDF (cifras a la
    // misma escala): negra = 18,92 pt · corchea = 12,62 pt · semicorchea = 8,42 pt. Las razones son
    // 18,92/12,62 = 1,499 y 12,62/8,42 = 1,499. Es decir:
    //
    //     ancho ∝ duración^0,585
    //
    // Un compás de 8 corcheas **no** ocupa el doble que uno de 4 negras: ocupa 8·12,62 = 100,96 pt
    // frente a los 4·18,92 = 75,68 pt (1,33×, no 2×). Ese es el error de fondo que hacía que la
    // tablatura «no se viera igual», y se corrige aquí.

    /** **Espacio de pentagrama** de referencia (pt): 1,50 · `lineDistance`. Es la unidad de medida. */
    const val SPACE_PT: Float = 5.0f

    /**
     * **Ancho NATURAL de una negra**, en espacios de pentagrama: `styleDef` de MuseScore
     * `DEFAULT_QUARTER_NOTE_SPACE = 3.5_sp` (`src/engraving/rendering/score/horizontalspacing.cpp`,
     * línea 744, dentro de `chordRestSegmentNaturalWidth`).
     *
     * **Corrección (F-04.quater)**: hasta ahora valía **3,815**, «medido del `salmo2.pdf`» (18,92 pt
     * / 4,96 pt). Ese 3,815 **no** es el ancho natural: es el ancho **justificado**. El sistema 1 del
     * PDF se estira para llenar el ancho imprimible (`SystemLayout::shouldBeJustified`,
     * `systemlayout.cpp:487`), y su factor es `102,89 / 95,48 = 1,078`; `3,5 · 1,078 = 3,77`
     * espacios = 18,7 pt, que es el 18,92 pt medido (1 % de error de medición). Tomar el valor
     * justificado como natural hacía **todos** los compases un **9 % más anchos** de lo que son y por
     * eso el reparto en líneas no coincidía con el del referente (4/10/11/7 compases por sistema en
     * vez de 5/11/11/5).
     *
     * El valor real se **reescala** con el espacio configurado; este es el de referencia.
     */
    const val QUARTER_WIDTH_SPACES: Float = 3.5f

    /**
     * **Exponente** de la ley de espaciado (`ancho ∝ duración^`[SPACING_EXPONENT]), medido del PDF:
     * `ln(8,42/18,92) / ln(0,25) = 0,585`.
     */
    const val SPACING_EXPONENT: Float = 0.585f

    /**
     * **Ancho mínimo** de una columna, como fracción del ancho de una negra. Medido del PDF, la
     * **semicorchea** mide `0,25^0,585 = 0,444` negra, así que el tope tiene que estar **por debajo**
     * de eso (antes `0,5` recortaba la semicorchea y descuadraba el compás).
     */
    const val MIN_FRACTION: Float = 0.42f

    // ── El ancho de la cifra (D-50) ──────────────────────────────────────────────────────────────
    //
    // MuseScore **no** reparte solo por duración: `HorizontalSpacing::minHorizontalDistance`
    // (`src/engraving/rendering/score/horizontalspacing.cpp:1222-1290`) mide la **forma** de cada
    // elemento —`r1.right() - r2.left() + padding`— y `spaceAgainstPreviousSegments` (`:346-410`)
    // toma `x = max(x, xPrevSeg + minHorDist)`. La forma de una cifra de tablatura es su **texto**,
    // así que una columna con `12` es más ancha que una con `5` aunque duren lo mismo. Aquí no había
    // nada de eso: `columnWidth` solo miraba la duración y una semicorchea (1,554 espacios) se comía
    // una cifra de dos dígitos (2,035 espacios).

    /**
     * **Alto de la cifra de traste**, en espacios de pentagrama: el `em` de
     * `TabCanvasTextStyles.fret` (`staffSpace × 1,83`), medido del `salmo2.pdf` (la tinta de la cifra
     * mide 0,887 de la separación de cuerdas y la sans rinde 0,728 em).
     */
    const val FRET_TEXT_HEIGHT_SPACES: Float = 1.83f

    /**
     * **Aire a cada lado de la cifra**, en espacios: la mitad del `minNoteDistance` de MuseScore
     * (`styleDef(minNoteDistance, 0.35_sp)`, `styledef.cpp:271`), que es el `padding` que
     * `minHorizontalDistance` suma a la distancia entre dos formas. Se reparte a los dos lados porque
     * la cifra se dibuja **centrada** en la columna (`cellCenterX`).
     */
    const val FRET_TEXT_PADDING_SPACES: Float = 0.175f

    /**
     * **Side bearing** de un carácter, en fracción del `em`: la mitad del hueco que la fuente deja a
     * cada lado de la tinta. `TextBase::width()` devuelve el **bounding rect** de la tinta, no la suma
     * de avances, así que hay que descontarlo. Con `0,05 em` el modelo cuadra con el `salmo2.pdf`: una
     * cifra de un dígito mide allí **17-18 px** a 300 dpi (`0,84 espacios`), y
     * `(0,556 − 2 · 0,05) · 1,83 = 0,834 espacios` = 17,2 px.
     */
    private const val SIDE_BEARING_EM: Float = 0.05f

    /**
     * **Avance de cada carácter** de la cifra, en fracción del `em`. Son las métricas de una sans de
     * familia Helvetica —la que MuseScore pide para la tablatura (`styleDef(tabFretNumberFontFace,
     * "FreeSans")`)—: los dígitos avanzan `0,556 em`, la `X` de la cuerda apagada `0,667`, los
     * paréntesis de la nota fantasma `0,333` y las alteraciones `0,5`.
     */
    private val CHAR_ADVANCE: Map<Char, Float> = buildMap {
        for (c in '0'..'9') put(c, 0.556f)
        put('X', 0.667f)
        put('x', 0.556f)
        put('(', 0.333f)
        put(')', 0.333f)
        put('\u266F', 0.5f) // ♯
        put('\u266D', 0.5f) // ♭
        put('\u266E', 0.5f) // ♮
        put('.', 0.278f)
        put('/', 0.278f)
        put('-', 0.333f)
        for (c in 'a'..'z') put(c, 0.556f)
    }

    /** Avance por defecto de un carácter que no está en [CHAR_ADVANCE]. */
    private const val DEFAULT_ADVANCE: Float = 0.556f

    /**
     * **Ancho del texto de una cifra**, en espacios de pentagrama: la suma de los avances de sus
     * caracteres por [FRET_TEXT_HEIGHT_SPACES]. Es el `TextBase::width()` que MuseScore mete en la
     * forma del acorde.
     */
    fun fretTextWidthSpaces(text: String): Float {
        if (text.isEmpty()) return 0f
        var advance = 0f
        for (c in text) advance += CHAR_ADVANCE[c] ?: DEFAULT_ADVANCE
        // El bounding rect de la tinta, no la suma de avances: se descuentan los dos side bearings.
        val ink = (advance - 2f * SIDE_BEARING_EM).coerceAtLeast(advance * 0.5f)
        return ink * FRET_TEXT_HEIGHT_SPACES
    }

    /**
     * **Ancho mínimo (px) de una columna** que lleva la cifra [text]: el ancho del texto más el aire
     * de los dos lados ([FRET_TEXT_PADDING_SPACES]). Es el `minWidth` de la columna.
     */
    fun fretColumnMinWidth(text: String, staffSpace: Float): Float {
        if (text.isEmpty()) return 0f
        return (fretTextWidthSpaces(text) + 2f * FRET_TEXT_PADDING_SPACES) * staffSpace
    }

    /** Duración en **negras** de una figura (el puntillo ya va dentro del `beatValue`). */
    fun beatsOf(figure: RhythmFigure): Double = figure.beatValue * QUARTER_TIME

    /**
     * Figura por defecto de una resolución: la que representa una columna **sin** figura explícita
     * (`TabSlot.rhythmicFigure == null`).
     */
    fun defaultFigure(resolution: TabResolution): RhythmFigure = when (resolution) {
        TabResolution.QUARTER -> RhythmFigure.QUARTER
        TabResolution.EIGHTH -> RhythmFigure.EIGHTH
        TabResolution.SIXTEENTH -> RhythmFigure.SIXTEENTH
    }

    /** Duración en negras de una figura por defecto de la [resolution]. */
    fun beatsOf(resolution: TabResolution): Double = beatsOf(defaultFigure(resolution))

    /**
     * Duración en **negras** de una **columna**: la de su figura (o la de su resolución si no tiene) y,
     * si forma parte de un **grupo irregular**, la proporción de ese grupo (`normalNotes / numNotes`:
     * 2/3 en un tresillo, 4/5 en un quintillo). Es el `TGDuration.getTime()` de TuxGuitar con el
     * `Tuplet::ratio()` de MuseScore.
     */
    fun beatsOf(figure: RhythmFigure?, tuplet: TabTuplet?, resolution: TabResolution): Double {
        val base = figure?.let { beatsOf(it) } ?: beatsOf(resolution)
        return base * (tuplet?.ratio ?: 1.0)
    }

    /**
     * **Ancho (px) de una columna** de duración [beats] negras, con la **ley real de MuseScore**
     * (`ancho ∝ duración^[SPACING_EXPONENT]`) y el **mínimo geométrico** [MIN_FRACTION] × [cellWidth].
     *
     * [cellWidth] es el ancho de una **negra** en la unidad de la plataforma: se reescala desde el
     * valor medido ([QUARTER_WIDTH_SPACES] · espacio) para que la proporción con las cuerdas coincida
     * con el PDF, en vez de depender de un `cellWidth` arbitrario.
     *
     * La ley está **anclada en la negra**, así que no necesita saber cuál es la figura más menuda del
     * documento: una negra mide siempre [cellWidth] y el resto se deduce de su duración. (La ley
     * **lineal** de TuxGuitar sí necesitaba esa figura para fijar la unidad; se descartó por no
     * coincidir con el PDF.)
     */
    fun columnWidth(beats: Double, cellWidth: Float, minContentWidth: Float = 0f): Float {
        val exponent = SPACING_EXPONENT
        val quarter = cellWidth.coerceAtLeast(0.001f)
        // `kotlin.math.pow` es KMP (no `java.lang.Math`).
        val width = quarter * beats.coerceAtLeast(0.001).pow(exponent.toDouble()).toFloat()
        // (D-50) El **ancho de la cifra** manda cuando la duración pide menos de lo que el texto
        // ocupa: es el `x = max(x, xPrevSeg + minHorDist)` de `spaceAgainstPreviousSegments`.
        return width.coerceAtLeast(quarter * MIN_FRACTION).coerceAtLeast(minContentWidth)
    }

    /**
     * **Alto de la tinta** de una cifra de traste, en espacios de pentagrama.
     *
     * Medido del `salmo2.pdf` a 300 dpi: **17 px** de alto con el espacio a 20,67 px ⇒ **0,82**. Es
     * menor que [FRET_TEXT_HEIGHT_SPACES] (1,83) porque esa es la **caja de la fuente**, no lo que
     * mancha el dígito.
     */
    const val FRET_INK_HEIGHT_SPACES: Float = 0.82f

    /**
     * **Aire mínimo** entre la tinta de dos cifras de **cuerdas adyacentes**, en espacios.
     *
     * Con las cuerdas a 1,5 espacios y la cifra a 0,82, sobran 0,68: el aire de sobra. Solo se agota
     * cuando el visor agranda el número ([TabLayoutSettings.fretNumberScale]) por encima del **171 %**.
     */
    const val FRET_INK_MIN_GAP_SPACES: Float = 0.10f

    /**
     * **Separación de cuerdas** que hace falta para que la cifra agrandada **no se pise** con la de la
     * cuerda vecina.
     *
     * El visor deja agrandar la cifra hasta el **200 %** para leer de lejos en el atril, pero ese
     * factor solo movía el **texto**: con las cuerdas a 1,5 espacios y la tinta al doble (1,64), las
     * cifras de cuerdas contiguas se tocaban. MuseScore no tiene este problema porque su
     * `tabFretFontSize` no se toca en tiempo de ejecución; aquí la separación tiene que **seguir** al
     * zoom.
     *
     * La separación **no** arrastra al resto del grabado: [TabLayoutSettings.staffSpace] se queda
     * fijo (grosores, plicas, barras y huecos no cambian), solo se abren las cuerdas. Por debajo del
     * 171 % devuelve `baseStringSpacing` sin tocar nada.
     */
    fun stringSpacingFor(
        baseStringSpacing: Float,
        staffSpace: Float,
        fretNumberScale: Float
    ): Float {
        if (fretNumberScale <= 1f || staffSpace <= 0f) return baseStringSpacing
        val needed = (FRET_INK_HEIGHT_SPACES * fretNumberScale + FRET_INK_MIN_GAP_SPACES) * staffSpace
        return needed.coerceAtLeast(baseStringSpacing)
    }

    /**
     * **Ancho de una negra** (en la unidad que sea) a partir del **espacio de pentagrama**: `3,5`
     * espacios, el `DEFAULT_QUARTER_NOTE_SPACE` de MuseScore. Es el `cellWidth` que hace que el
     * reparto coincida con MuseScore.
     */
    fun quarterWidth(staffSpace: Float): Float = staffSpace * QUARTER_WIDTH_SPACES
}
