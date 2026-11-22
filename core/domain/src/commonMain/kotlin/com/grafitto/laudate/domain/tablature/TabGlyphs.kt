package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure

/**
 * **Datos de la fuente musical** (SMuFL) que necesita el grabado de la tablatura: los **puntos de
 * código** de cada símbolo y su **caja de tinta**.
 *
 * Vive en el **Dominio** y no en la UI por una razón concreta: el **PDF** también tiene que dibujar
 * los mismos glifos, y el exportador de Android (`core:data`) **no depende** de `core:ui`. Si los
 * puntos de código vivieran solo en la capa de UI, el PDF no podría emitir ni un silencio ni un
 * corchete y habría que **duplicarlos** — que es justo lo que se quiere evitar: dos tablas de
 * codepoints se desincronizan el día que alguien toque una.
 *
 * Los valores son los de **Bravura** (Steinberg, SIL OFL 1.1), que es la fuente que usa MuseScore
 * (`styleDef(musicalSymbolFont, "Bravura")` en `src/engraving/style/styledef.cpp`). No son
 * aproximaciones: son los mismos glifos, letra por letra.
 *
 * Las **cajas de tinta** están medidas de `bravura.otf` con `fontTools` (upm = 1000) y vienen en
 * **em** con la `y` **hacia arriba**, como la fuente. Sirven para **anclar por tinta** en vez de por
 * la caja de línea: la caja de línea de Bravura mide `ascent + descent = 2,012 + 2,012 = 4,024 em`,
 * así que centrar un glifo con `size.height / 2` lo deja **fuera de sitio** (era el bug de la clave
 * `TAB`, que salía 0,49 em demasiado alta).
 */
object TabGlyphs {

    /**
     * Los **puntos de código SMuFL** de los símbolos que usa la tablatura. Son los **mismos** que
     * MuseScore, así que el glifo coincide letra por letra (no hay aproximación).
     */
    object Glyph {
        /** Clave de **tablatura de 6 cuerdas** de SMuFL (`6stringTabClef`): la misma que MuseScore. */
        const val SIX_STRING_TAB_CLEF = "\uE06D"

        /** Clave de **tablatura de 4 cuerdas** (bajo, ukelele). */
        const val FOUR_STRING_TAB_CLEF = "\uE06E"

        /** Primera de las cifras del **compás** (`timeSig0`); las demás suman el dígito. */
        const val TIME_SIG_0 = "\uE080"

        /** **Redonda** (semibreve). */
        const val NOTEHEAD_WHOLE = "\uE0A2"

        /** **Blanca** (minim). */
        const val NOTEHEAD_HALF = "\uE0A3"

        /** **Negra** y menores (cabeza rellena). */
        const val NOTEHEAD_BLACK = "\uE0A4"

        // ── Glifos **completos de una pieza** (cabeza + plica + corchete), como los iconos de
        //    TuxGuitar: es lo que hay que usar para un **símbolo** (botón de paleta, icono), en vez
        //    de componer cabeza + plica + bandera por separado, que a tamaño pequeño sale deforme.

        /** **Redonda** completa (`noteWhole`). */
        const val NOTE_WHOLE = "\uE1D2"

        /** **Blanca** completa, plica arriba (`noteHalfUp`). */
        const val NOTE_HALF = "\uE1D3"

        /** **Negra** completa, plica arriba (`noteQuarterUp`). */
        const val NOTE_QUARTER = "\uE1D5"

        /** **Corchea** completa, plica arriba (`note8thUp`). */
        const val NOTE_8TH = "\uE1D7"

        /** **Semicorchea** completa, plica arriba (`note16thUp`). */
        const val NOTE_16TH = "\uE1D9"

        /** Corchete de **corchea** con la plica **hacia arriba** (`flag8thUp`). */
        const val FLAG_8TH = "\uE240"

        /** Corchete de **semicorchea** con la plica **hacia arriba** (`flag16thUp`). */
        const val FLAG_16TH = "\uE242"

        /**
         * Corchete de **corchea** con la plica **hacia abajo** (`flag8thDown`).
         *
         * SMuFL tiene **dos** glifos por corchete: el de plica arriba y el de plica abajo, que son
         * **espejos** (no el mismo girado). La tablatura lleva las plicas **hacia abajo**
         * (`stemsDown=1`), así que hay que usar estos: con los de arriba el corchete salía **al revés**
         * (abriéndose hacia la cuerda en vez de hacia el pie).
         */
        const val FLAG_8TH_DOWN = "\uE241"

        /** Corchete de **semicorchea** con la plica **hacia abajo** (`flag16thDown`). */
        const val FLAG_16TH_DOWN = "\uE243"

        /** **Puntillo** de aumento. */
        const val AUGMENTATION_DOT = "\uE1E7"

        /**
         * **Cifra del tresillo** (`tuplet3`): el `3` de la fuente musical, no el carácter de texto.
         * El `3` de una fuente de texto tiene otra altura y otro trazo; el de SMuFL está dibujado para
         * ir **sobre** el grupo, con la misma tinta que el resto de la notación.
         */
        const val TUPLET_3 = "\uE883"

        /** Silencio de **negra**. */
        const val REST_QUARTER = "\uE4E5"

        /** Silencio de **redonda** (`restWhole`): pende **bajo** la 4.ª línea. */
        const val REST_WHOLE = "\uE4E3"

        /** Silencio de **blanca** (`restHalf`): se apoya **sobre** la 3.ª línea. */
        const val REST_HALF = "\uE4E4"

        /** Silencio de **corchea**. */
        const val REST_8TH = "\uE4E6"

        /** Silencio de **semicorchea**. */
        const val REST_16TH = "\uE4E7"
    }

    /**
     * **Caja de tinta** de un glifo SMuFL, en **em** y con la `y` **hacia arriba** (como la fuente).
     */
    data class Ink(val y0: Float, val y1: Float) {
        /** Alto de la **tinta** del glifo, en em. */
        val height: Float get() = y1 - y0

        /** Centro de la tinta respecto a la **línea base**, en em (positivo = por encima). */
        val centerFromBaseline: Float get() = (y0 + y1) / 2f
    }

    /**
     * Cajas de tinta **medidas de `bravura.otf`** con `fontTools` (upm = 1000). No son estimaciones:
     * son los `yMin`/`yMax` reales del contorno de cada glifo.
     */
    private val INK: Map<String, Ink> = buildMap {
        put(Glyph.SIX_STRING_TAB_CLEF, Ink(-0.748f, 0.764f))
        put(Glyph.FOUR_STRING_TAB_CLEF, Ink(-0.508f, 0.504f))
        // Los diez dígitos del compás comparten caja (están diseñados para apilarse).
        (0..9).forEach { d -> put((Glyph.TIME_SIG_0[0] + d).toString(), Ink(-0.250f, 0.251f)) }
        put(Glyph.NOTE_WHOLE, Ink(-0.137f, 0.136f))
        put(Glyph.NOTE_HALF, Ink(-0.145f, 0.875f))
        put(Glyph.NOTE_QUARTER, Ink(-0.141f, 0.875f))
        put(Glyph.NOTE_8TH, Ink(-0.138f, 0.873f))
        put(Glyph.NOTE_16TH, Ink(-0.138f, 0.873f))
        put(Glyph.NOTEHEAD_WHOLE, Ink(-0.125f, 0.125f))
        put(Glyph.NOTEHEAD_HALF, Ink(-0.125f, 0.125f))
        put(Glyph.NOTEHEAD_BLACK, Ink(-0.125f, 0.125f))
        put(Glyph.AUGMENTATION_DOT, Ink(-0.050f, 0.050f))
        put(Glyph.FLAG_8TH, Ink(-0.810f, 0.009f))
        put(Glyph.FLAG_16TH, Ink(-0.813f, 0.002f))
        put(Glyph.FLAG_8TH_DOWN, Ink(-0.014f, 0.808f))
        put(Glyph.FLAG_16TH_DOWN, Ink(-0.009f, 0.812f))
        put(Glyph.TUPLET_3, Ink(-0.008f, 0.375f))
        // Los diez dígitos de grupo irregular comparten caja (están diseñados para ir sobre el grupo).
        (0..9).forEach { d -> put((Glyph.TUPLET_3[0] - 3 + d).toString(), Ink(-0.008f, 0.375f)) }
        put(Glyph.REST_WHOLE, Ink(-0.135f, 0.009f))
        put(Glyph.REST_HALF, Ink(-0.002f, 0.142f))
        put(Glyph.REST_QUARTER, Ink(-0.375f, 0.373f))
        put(Glyph.REST_8TH, Ink(-0.251f, 0.174f))
        put(Glyph.REST_16TH, Ink(-0.500f, 0.179f))
        // (D-55) **Articulaciones** (`TabArticulation.display`): acento, staccato, marcato, tenuto,
        // calderón, mordente, grupeto y los arcos de púa. Sin su caja, el PDF las anclaba por el
        // centro de la caja de línea de Bravura (4,024 em) y salían **fuera** de su franja.
        put("\uE4A0", Ink(0.001f, 0.245f)) // articAccentAbove
        put("\uE4A2", Ink(0.000f, 0.084f)) // articStaccatoAbove
        put("\uE4AC", Ink(-0.001f, 0.253f)) // articMarcatoAbove
        put("\uE4A4", Ink(0.000f, 0.048f)) // articTenutoAbove
        put("\uE4C0", Ink(-0.003f, 0.329f)) // fermataAbove
        put("\uE56D", Ink(-0.073f, 0.319f)) // ornamentMordent
        put("\uE567", Ink(0.000f, 0.218f)) // ornamentTurn
        put("\uE612", Ink(0.001f, 0.495f)) // stringsUpBow
        put("\uE610", Ink(0.000f, 0.318f)) // stringsDownBow
        put("\uE614", Ink(0.000f, 0.200f)) // stringsHarmonic
        // (D-77) **Articulaciones combinadas** y *staccatissimo*. Faltaban en la tabla, así que la
        // paleta caía al centrado por **caja de línea** —4,024 em en Bravura— y el glifo se dibujaba
        // a **1/4** de su tamaño: el sforzato `𝄰` salía de 2×2 px dentro de una celda de 48 dp.
        // **Alteraciones** SMuFL. La paleta usa estos glifos para las **dobles**: `𝄪`/`𝄫` son pares
        // sustitutos que la fuente del sistema no tiene y la celda salía con un cuadradito de 8 px.
        put("\uE260", Ink(-0.175f, 0.439f)) // accidentalFlat
        put("\uE261", Ink(-0.335f, 0.341f)) // accidentalNatural
        put("\uE262", Ink(-0.348f, 0.350f)) // accidentalSharp
        put("\uE263", Ink(-0.125f, 0.127f)) // accidentalDoubleSharp
        put("\uE264", Ink(-0.175f, 0.437f)) // accidentalDoubleFlat
        put("\uE4A6", Ink(-0.002f, 0.293f)) // articStaccatissimoAbove
        put("\uE4AE", Ink(0.000f, 0.443f)) // articMarcatoStaccatoAbove
        put("\uE4B0", Ink(0.000f, 0.420f)) // articAccentStaccatoAbove
        put("\uE4B2", Ink(0.000f, 0.240f)) // articTenutoStaccatoAbove
        put("\uE4B4", Ink(0.000f, 0.345f)) // articTenutoAccentAbove
        put("\uE4BC", Ink(0.000f, 0.427f)) // articMarcatoTenutoAbove
        // **Signos de navegación**: segno y coda. Sin su caja salían de 5×7 y 9×11 px.
        put("\uE047", Ink(-0.027f, 0.759f)) // segno
        put("\uE048", Ink(-0.158f, 0.898f)) // coda
        // **Trémolos** de una, dos y tres barras (comparten caja).
        put("\uE510", Ink(-0.010f, 0.463f)) // tremolo1
        put("\uE511", Ink(-0.010f, 0.463f)) // tremolo2
        put("\uE512", Ink(-0.010f, 0.463f)) // tremolo3
        // **Cabezas de nota** de guitarra (`notehead*` de la zona ECAx): abierta, cerrada, con X,
        // con paréntesis, con rombo… Cada una tiene su propia caja.
        put("\uECA2", Ink(-0.125f, 0.148f)) // noteheadXBlack
        put("\uECA3", Ink(-0.141f, 0.688f)) // noteheadXHalf
        put("\uECA4", Ink(-0.688f, 0.141f)) // noteheadXWhole
        put("\uECA5", Ink(-0.141f, 0.688f)) // noteheadCircleXHalf
        put("\uECA6", Ink(-0.688f, 0.141f)) // noteheadCircleXWhole
        put("\uECA7", Ink(-0.141f, 0.696f)) // noteheadParenthesisBlack
        put("\uECA8", Ink(-0.706f, 0.141f)) // noteheadParenthesisWhole
        put("\uECA9", Ink(-0.141f, 0.700f)) // noteheadDiamondBlack
        put("\uECAB", Ink(-0.141f, 0.923f)) // noteheadDiamondHalf
        put("\uECAD", Ink(-0.141f, 1.098f)) // noteheadDiamondWhole
        // (D-91) **Símbolos musicales Unicode** (`♩ ♪ ♫ ♬ ♭ ♮ ♯`), que es lo que llevan escritos
        // `RhythmFigure.symbol` y los chips de tempo. Bravura **sí** los tiene, pero su caja no
        // estaba tabulada y al centrarlos por línea base (4,024 em) el glifo se salía de su caja.
        put("\u2669", Ink(-0.141f, 0.875f)) // ♩ quarterNote
        put("\u266A", Ink(-0.138f, 0.873f)) // ♪ eighthNote
        put("\u266B", Ink(-0.141f, 0.688f)) // ♫ beamedEighthNotes
        put("\u266C", Ink(-0.141f, 0.688f)) // ♬ beamedSixteenthNotes
        put("\u266D", Ink(-0.175f, 0.439f)) // ♭ flat
        put("\u266E", Ink(-0.335f, 0.341f)) // ♮ natural
        put("\u266F", Ink(-0.348f, 0.350f)) // ♯ sharp
    }

    /**
     * Caja de tinta **asumida** para un glifo SMuFL que no esté en la tabla: media em sobre la línea
     * base. Es una aproximación deliberadamente **pequeña**: si se usara la caja de línea de Bravura
     * (4,024 em) el glifo se dibujaría a la cuarta parte de su tamaño, que es exactamente el defecto
     * que se corrigió en D-77.
     */
    val FALLBACK_INK: Ink = Ink(0f, 0.5f)

    /** Caja de tinta de un glifo; `null` si no está tabulada (el pintor cae al centrado por caja). */
    fun inkOf(glyph: String): Ink? = INK[glyph]

    /**
     * **Cuerpo** (em de la fuente musical) con el que se dibuja la **clave de tablatura** `TAB`:
     * `3,90` espacios de pentagrama.
     *
     * Medido del `salmo2.pdf`: la tinta de la clave mide **5,86 esp** de alto y el glifo
     * `6stringTabClef` de Bravura mide 1,512 em ⇒ **em = 3,87**. Se usa 3,90 (el valor histórico, ya
     * correcto).
     *
     * Vive aquí, y no en la UI, porque el **PDF** dibuja la misma clave: si cada motor tuviera su
     * copia, la pantalla y el papel acabarían con tamaños distintos.
     */
    const val TAB_CLEF_EM: Float = 3.90f

    /**
     * **Cuerpo** (em) con el que se dibuja la **indicación de compás** (`4/4`): `3,87` espacios.
     *
     * Medido del `salmo2.pdf`: el par de dígitos mide **3,94 esp** de alto y `timeSigN` de Bravura
     * mide 0,501 em ⇒ **em = 3,87**.
     */
    const val TIME_SIGNATURE_EM: Float = 3.87f

    /**
     * Cifra del **compás** (un dígito de `4/4`): el glifo SMuFL `timeSigN`, que es `timeSig0` más el
     * valor del dígito. Devuelve `null` si no es un dígito.
     */
    fun timeSignatureGlyph(digit: Int): String? =
        if (digit in 0..9) (Glyph.TIME_SIG_0[0] + digit).toString() else null

    /**
     * Cifra de un **grupo irregular** (`tuplet0`…`tuplet9`): el glifo SMuFL `tupletN`, que es
     * `tuplet0` más el valor del dígito. Devuelve `null` si no es un dígito.
     *
     * MuseScore rotula el grupo con el **numerador** de la proporción (`TupletNumberType::SHOW_NUMBER`,
     * el valor por defecto), así que un quintillo lleva `5` y un nonillo `9`.
     */
    fun tupletDigit(digit: Int): String? =
        if (digit in 0..9) (Glyph.TUPLET_3[0] - 3 + digit).toString() else null

    /** Clave de tablatura según el **número de cuerdas** del instrumento (6 o 4). */
    fun tabClef(stringCount: Int): String =
        if (stringCount <= 4) Glyph.FOUR_STRING_TAB_CLEF else Glyph.SIX_STRING_TAB_CLEF

    /**
     * **Cabeza de nota** de una figura: redonda (hueca), blanca (hueca con plica) o negra y menores
     * (rellena). Son los glifos `noteheadWhole` / `noteheadHalf` / `noteheadBlack` de SMuFL.
     */
    fun notehead(figure: RhythmFigure): String? = when (figure) {
        RhythmFigure.WHOLE -> Glyph.NOTEHEAD_WHOLE
        RhythmFigure.HALF, RhythmFigure.DOTTED_HALF -> Glyph.NOTEHEAD_HALF
        else -> Glyph.NOTEHEAD_BLACK
    }

    /**
     * **Corchete** de una figura con plica. SMuFL tiene **dos** glifos por corchete (plica arriba y
     * plica abajo, que son **espejos**): hay que elegir el que toca o el corchete sale invertido.
     *
     * El glifo de semicorchea (`flag16thDown`) ya trae **las dos** banderas dibujadas, así que se pinta
     * **una sola vez** por figura (no una bandera por corchete, que las duplicaría).
     *
     * @param stemsDown `true` si la plica cuelga hacia abajo (el caso de la tablatura).
     */
    fun flag(figure: RhythmFigure, stemsDown: Boolean = false): String? = when (figure) {
        RhythmFigure.EIGHTH, RhythmFigure.DOTTED_EIGHTH ->
            if (stemsDown) Glyph.FLAG_8TH_DOWN else Glyph.FLAG_8TH
        RhythmFigure.SIXTEENTH ->
            if (stemsDown) Glyph.FLAG_16TH_DOWN else Glyph.FLAG_16TH
        else -> null
    }

    /**
     * **Silencio** de una figura (`restWhole` / `restHalf` / `restQuarter` / `rest8th` / `rest16th`);
     * `null` si no es silencio.
     */
    fun rest(figure: RhythmFigure): String? = when (figure) {
        RhythmFigure.WHOLE_REST -> Glyph.REST_WHOLE
        RhythmFigure.HALF_REST, RhythmFigure.DOTTED_HALF_REST -> Glyph.REST_HALF
        RhythmFigure.QUARTER_REST, RhythmFigure.DOTTED_QUARTER_REST -> Glyph.REST_QUARTER
        RhythmFigure.EIGHTH_REST, RhythmFigure.DOTTED_EIGHTH_REST -> Glyph.REST_8TH
        RhythmFigure.SIXTEENTH_REST -> Glyph.REST_16TH
        else -> null
    }

    /**
     * **Glifo completo de una figura** (`noteWhole`, `noteHalfUp`, `noteQuarterUp`, `note8thUp`,
     * `note16thUp`): la nota **entera de una pieza**, con su plica y su corchete ya dibujados por la
     * fuente.
     *
     * Es lo que hay que usar para un **símbolo** (el icono de un botón, una miniatura), igual que
     * TuxGuitar usa sus iconos de 22×22: componer cabeza + plica + bandera a mano a tamaño pequeño se
     * deforma (esa era la causa de los «puntos» en la paleta de duración).
     *
     * Las variantes **con puntillo** devuelven el glifo de la figura **base** (la que tiene la misma
     * cabeza): el puntillo se **superpone** aparte, igual que TuxGuitar monta `dotted.png` sobre
     * `4.png`. Devuelve `null` para los **silencios** (usan [rest]).
     */
    fun figureGlyph(figure: RhythmFigure): String? = when (figure) {
        RhythmFigure.WHOLE -> Glyph.NOTE_WHOLE
        RhythmFigure.DOTTED_HALF, RhythmFigure.HALF -> Glyph.NOTE_HALF
        RhythmFigure.DOTTED_QUARTER, RhythmFigure.QUARTER -> Glyph.NOTE_QUARTER
        RhythmFigure.DOTTED_EIGHTH, RhythmFigure.EIGHTH -> Glyph.NOTE_8TH
        RhythmFigure.SIXTEENTH -> Glyph.NOTE_16TH
        else -> null
    }

    /**
     * **Glifo de icono** de una figura: el completo si es nota ([figureGlyph]) o el del silencio
     * ([rest]). Es lo que pinta un botón de la paleta de duración.
     */
    fun iconGlyph(figure: RhythmFigure): String? = figureGlyph(figure) ?: rest(figure)

    /**
     * **Caja de tinta** del icono de una figura, en em. La usa la paleta para **escalar cada figura a
     * su caja**: sin esto, todas se pintan al mismo `em` y la redonda (0,273 em de alto) sale diminuta
     * mientras la semicorchea (1,011 em) se sale del botón.
     */
    fun iconInk(figure: RhythmFigure): Ink? = iconGlyph(figure)?.let { inkOf(it) }
}
