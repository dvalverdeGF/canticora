package com.grafitto.laudate.domain.tablature

/**
 * **Familia tipográfica** de un texto de la tablatura.
 *
 * La tablatura mezcla **dos** fuentes, igual que MuseScore: la **musical** (SMuFL, Bravura) para los
 * símbolos —clave `TAB`, compás, articulaciones, marcas— y la de **texto** para las cifras, los
 * rótulos y los números. Elegir mal la familia no es un detalle estético: los puntos de código SMuFL
 * **no existen** en una fuente de texto, así que el símbolo se imprime como un signo de
 * interrogación.
 */
enum class TabTextFace {
    /** Fuente de **texto**, redonda. */
    TEXT,

    /** Fuente de **texto**, negrita (tempo, volta). */
    TEXT_BOLD,

    /** Fuente de **texto**, negrita y cursiva (dinámicas). */
    TEXT_ITALIC_BOLD,

    /** Fuente **musical** (SMuFL): el texto es un punto de código de glifo. */
    MUSIC
}

/**
 * **Talla y familia de cada texto** de la tablatura, en **espacios de pentagrama**.
 *
 * Es la **única fuente de verdad** de la tipografía del grabado: la leen el lienzo de Compose
 * ([com.grafitto.laudate.ui.tablature.TablatureCanvas]) y los dos exportadores a PDF (escritorio y
 * Android). Antes cada uno llevaba sus propias cifras —el lienzo tallas en espacios y los PDF
 * `7,5 pt` y `9 pt` **fijos**—, así que el papel **no** reproducía la pantalla: el rótulo del tempo
 * salía un 37 % más pequeño, el número de compás un 25 %, y la clave `TAB` y las articulaciones se
 * imprimían como el texto literal `TAB` o como **signos de interrogación** (los puntos de código
 * SMuFL no existen en Helvetica).
 *
 * Las tallas están **medidas del `salmo2.pdf`** (espacio de pentagrama = 5,0 pt = 20,67 px a 300 dpi)
 * y son fracciones del espacio, no números sueltos: así el grabado escala con el pentagrama.
 */
object TabTextMetrics {

    /**
     * **Cifra de traste**: `1,83` espacios.
     *
     * Medido del `salmo2.pdf`: la tinta de la cifra mide **0,887 de la separación de cuerdas**
     * (27,5 px con cuerdas a 31 px). En el render la tinta de la sans mide **0,728 em**, así que el em
     * que la clava es `0,887 / 0,728 = 1,22` separaciones de cuerda = **1,83 espacios**.
     */
    const val FRET_EM: Float = 1.83f

    /** **Cifra de adorno**: `graceNoteMag` de MuseScore (`styledef.cpp:524`), **0,7** de la normal. */
    const val GRACE_MAG: Float = 0.7f

    /** **Etiqueta de cuerda** (opcional): `2,0` espacios. */
    const val STRING_LABEL_EM: Float = 2.0f

    /**
     * **Número de compás**: `1,6` espacios.
     *
     * `styleDef(measureNumberFontSize, 8.0)` (`styledef.cpp:1279`) con un espacio de 5 pt son
     * **1,6 espacios**. Medido del `salmo2.pdf`: la tinta del `6` mide 17 × 23 px (0,82 × 1,11
     * espacios), que con la razón de tinta de la sans (0,744 em de alto) da `1,11 / 0,744 = 1,49`;
     * se usa **1,6** porque es el valor de MuseScore y deja la tinta en 24,8 px, a un 8 % del PDF.
     */
    const val MEASURE_NUMBER_EM: Float = 1.6f

    /**
     * **Dinámica** (`mp`, `pp`…): `2,4` espacios.
     *
     * MuseScore 4 la dibuja con **glifos SMuFL** de Leland (`dynamicMP` = U+E52C, comprobado en el
     * `salmo2.pdf`), no con la fuente de texto. Aquí se usa la de texto en negrita cursiva porque
     * Bravura —la musical que sí tenemos— dibuja las dinámicas **1,5× más anchas** que Leland a igual
     * altura de tinta: con `dynamicMP` a la altura medida (1,69 espacios) el `mp` mediría 71 px donde
     * el PDF mide 58. La de texto, a 2,4 espacios, mide 38×63 px frente a los 35×58 del PDF.
     */
    const val DYNAMIC_EM: Float = 2.4f

    /** **Rótulo del tempo**: `tempoFontSize = 12.0` pt de MuseScore = `2,4` espacios. */
    const val TEMPO_EM: Float = 2.4f

    /** **Articulaciones, marcas y `8va`**: glifos SMuFL del tamaño del pentagrama, `3,0` espacios. */
    const val MUSIC_MARK_EM: Float = 3.0f

    /** **Rótulo de la volta**: `voltaFontSize = 11.0` pt de MuseScore = `2,2` espacios, negrita. */
    const val VOLTA_EM: Float = 2.2f

    /** **Cifra prolongada** (nota ligada sin volver a pulsar): `2,4` espacios. */
    const val HELD_EM: Float = 2.4f

    /** **Texto libre** y marcas de la rejilla: `2,0` espacios. */
    const val TEXT_EM: Float = 2.0f

    /** **Rótulo de un glifo** de la rejilla (la cifra de un tresillo, por ejemplo): `1,8` espacios. */
    const val GLYPH_LABEL_EM: Float = 1.8f

    /**
     * Cuerpo del **símbolo SMuFL incrustado** en un texto (el `♩` del tempo), en espacios.
     *
     * Medido del `salmo2.pdf`: el `♩` ocupa **2,52 espacios** y la tinta de `metNoteQuarterUp` en
     * Bravura mide **0,86 em**, así que el cuerpo es `2,52 / 0,86 ≈ 2,9`; se usa **3,2** para que la
     * tinta llegue a 1,82 y quede en la proporción del PDF.
     */
    const val MUSIC_SYMBOL_EM: Float = 3.2f

    /**
     * **Talla** del texto, en espacios de pentagrama.
     *
     * @param numberScale escala propia de las cifras de traste (el zoom del número del visor). Solo
     *   afecta a [TabTextKind.FRET] y [TabTextKind.GRACE].
     */
    fun emOf(kind: TabTextKind, numberScale: Float = 1f): Float = when (kind) {
        TabTextKind.FRET -> FRET_EM * numberScale
        TabTextKind.GRACE -> FRET_EM * GRACE_MAG * numberScale
        TabTextKind.STRING_LABEL -> STRING_LABEL_EM
        TabTextKind.MEASURE_NUMBER -> MEASURE_NUMBER_EM
        TabTextKind.DYNAMIC -> DYNAMIC_EM
        TabTextKind.TEMPO -> TEMPO_EM
        TabTextKind.ARTICULATION, TabTextKind.MARK, TabTextKind.OCTAVE_SHIFT -> MUSIC_MARK_EM
        TabTextKind.VOLTA -> VOLTA_EM
        TabTextKind.HELD -> HELD_EM
        TabTextKind.TEXT -> TEXT_EM
        TabTextKind.TAB_CLEF -> TabGlyphs.TAB_CLEF_EM
        TabTextKind.TIME_NUMERATOR, TabTextKind.TIME_DENOMINATOR -> TabGlyphs.TIME_SIGNATURE_EM
    }

    /** **Familia** con la que se dibuja el texto. */
    fun faceOf(kind: TabTextKind): TabTextFace = when (kind) {
        TabTextKind.TEMPO, TabTextKind.VOLTA -> TabTextFace.TEXT_BOLD
        TabTextKind.DYNAMIC -> TabTextFace.TEXT_ITALIC_BOLD
        TabTextKind.ARTICULATION, TabTextKind.MARK, TabTextKind.OCTAVE_SHIFT,
        TabTextKind.TAB_CLEF, TabTextKind.TIME_NUMERATOR, TabTextKind.TIME_DENOMINATOR ->
            TabTextFace.MUSIC
        else -> TabTextFace.TEXT
    }

    /** `true` si el texto es un **punto de código SMuFL** y hay que dibujarlo con la fuente musical. */
    fun usesMusicFont(kind: TabTextKind): Boolean = faceOf(kind) == TabTextFace.MUSIC
}
