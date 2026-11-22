package com.grafitto.laudate.ui.tablature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.grafitto.laudate.domain.tablature.TabGlyphs
import com.grafitto.laudate.ui.generated.resources.Res
import com.grafitto.laudate.ui.generated.resources.bravura
import com.grafitto.laudate.ui.generated.resources.edwin
import org.jetbrains.compose.resources.Font

/**
 * **Tipografía musical** del pentagrama (ADR-017 §D10).
 *
 * MuseScore **no dibuja** los símbolos musicales: los saca de una **fuente** (Bravura, de Steinberg,
 * cuyo diseño es el estándar **SMuFL**). Eso es justo lo que hacía que nuestra tablatura se viera
 * «parecida pero no»: la clave `TAB`, el compás, las figuras de duración y las alteraciones hay que
 * **pintarlas con esa misma fuente**, no imitarlas con trazos.
 *
 * Bravura es **SIL Open Font License 1.1** (`composeResources/font/bravura.otf`, con su texto de
 * licencia en `composeResources/files/OFL-Bravura.txt`).
 *
 * La fuente de **texto** de MuseScore es **Edwin** (`styleDef(defaultFontFace, "Edwin")` en
 * `src/engraving/style/styledef.cpp`), también **OFL** (`composeResources/font/edwin.otf`, licencia en
 * `composeResources/files/OFL-Edwin.txt`). Se embebe para que tempo, dinámicas, textos, números de
 * compás y etiquetas de cuerda tengan **la misma forma** que en MuseScore.
 *
 * **Las cifras de traste son otra cosa**: MuseScore las pinta con **FreeSans**
 * (`styleDef(tabFretNumberFontFace, "FreeSans")`), una **sans** de métrica Helvetica. FreeSans es
 * GPLv3 con excepción de fuente y pesa **1,5 MB**, así que **no** se embebe (RNF-105, APK ≤ 25 MB):
 * se usa la **sans del sistema**, que es la misma familia métrica. Lo que **no** vale es la serif, que
 * es lo que había antes y era la diferencia más visible con MuseScore.
 */
@Immutable
object MusicFont {

    /**
     * **Familia tipográfica** con Bravura cargada desde los recursos de Compose. Es un `@Composable`
     * porque el recurso se resuelve de forma asíncrona (funciona igual en Android y en escritorio).
     */
    @Composable
    fun family(): FontFamily = FontFamily(Font(Res.font.bravura, FontWeight.Normal))

    /**
     * **Familia tipográfica de texto** con **Edwin** cargada desde los recursos de Compose: la misma
     * que MuseScore usa para todo lo que no es símbolo musical (`defaultFontFace`).
     */
    @Composable
    fun textFamily(): FontFamily = FontFamily(Font(Res.font.edwin, FontWeight.Normal))

    /**
     * Los **puntos de código SMuFL** de los símbolos que usa la tablatura.
     *
     * **Ya no viven aquí**: la tabla canónica es [TabGlyphs.Glyph], en el **Dominio**, porque el
     * **PDF** también tiene que emitir los mismos glifos y el exportador de Android (`core:data`) no
     * depende de `core:ui`. Este objeto se conserva como **alias** para no tocar a los consumidores de
     * la UI; los valores son los mismos `const val`, así que no hay copia que se pueda desincronizar.
     */
    object Glyph {
        const val SIX_STRING_TAB_CLEF = TabGlyphs.Glyph.SIX_STRING_TAB_CLEF
        const val FOUR_STRING_TAB_CLEF = TabGlyphs.Glyph.FOUR_STRING_TAB_CLEF
        const val TIME_SIG_0 = TabGlyphs.Glyph.TIME_SIG_0
        const val NOTEHEAD_WHOLE = TabGlyphs.Glyph.NOTEHEAD_WHOLE
        const val NOTEHEAD_HALF = TabGlyphs.Glyph.NOTEHEAD_HALF
        const val NOTEHEAD_BLACK = TabGlyphs.Glyph.NOTEHEAD_BLACK
        const val NOTE_WHOLE = TabGlyphs.Glyph.NOTE_WHOLE
        const val NOTE_HALF = TabGlyphs.Glyph.NOTE_HALF
        const val NOTE_QUARTER = TabGlyphs.Glyph.NOTE_QUARTER
        const val NOTE_8TH = TabGlyphs.Glyph.NOTE_8TH
        const val NOTE_16TH = TabGlyphs.Glyph.NOTE_16TH
        const val FLAG_8TH = TabGlyphs.Glyph.FLAG_8TH
        const val FLAG_16TH = TabGlyphs.Glyph.FLAG_16TH
        const val FLAG_8TH_DOWN = TabGlyphs.Glyph.FLAG_8TH_DOWN
        const val FLAG_16TH_DOWN = TabGlyphs.Glyph.FLAG_16TH_DOWN
        const val AUGMENTATION_DOT = TabGlyphs.Glyph.AUGMENTATION_DOT
        const val TUPLET_3 = TabGlyphs.Glyph.TUPLET_3
        const val REST_QUARTER = TabGlyphs.Glyph.REST_QUARTER
        const val REST_WHOLE = TabGlyphs.Glyph.REST_WHOLE
        const val REST_HALF = TabGlyphs.Glyph.REST_HALF
        const val REST_8TH = TabGlyphs.Glyph.REST_8TH
        const val REST_16TH = TabGlyphs.Glyph.REST_16TH
    }

    /**
     * **Caja de tinta** de un glifo SMuFL, en **em** y con la `y` **hacia arriba** (como la fuente).
     *
     * Es la pieza que permite **anclar por tinta** en vez de por la caja de línea. La caja de línea de
     * Bravura mide `ascent + descent = 2,012 + 2,012 = 4,024 em`, así que centrar un glifo con
     * `size.height / 2` lo deja **fuera de sitio**: era el bug de la clave `TAB`, que salía **0,49 em**
     * (45 px a escala 1,7) demasiado alta. Con la tinta, el centro cae donde tiene que caer.
     *
     * La tabla canónica es [TabGlyphs.Ink], en el Dominio (la comparte el PDF).
     */
    fun inkOf(glyph: String): TabGlyphs.Ink? = TabGlyphs.inkOf(glyph)

    /** Cifra del **compás** (un dígito de `4/4`): el glifo SMuFL `timeSigN`. */
    fun timeSignatureGlyph(digit: Int): String? = TabGlyphs.timeSignatureGlyph(digit)

    /** Cifra de un **grupo irregular** (`tuplet0`…`tuplet9`). */
    fun tupletDigit(digit: Int): String? = TabGlyphs.tupletDigit(digit)

    /** Clave de tablatura según el **número de cuerdas** del instrumento (6 o 4). */
    fun tabClef(stringCount: Int): String = TabGlyphs.tabClef(stringCount)

    /** **Cabeza de nota** de una figura. */
    fun notehead(figure: com.grafitto.laudate.domain.model.rhythm.RhythmFigure): String? =
        TabGlyphs.notehead(figure)

    /** **Corchete** de una figura con plica, en la dirección de la plica. */
    fun flag(
        figure: com.grafitto.laudate.domain.model.rhythm.RhythmFigure,
        stemsDown: Boolean = false
    ): String? = TabGlyphs.flag(figure, stemsDown)

    /** **Silencio** de una figura. */
    fun rest(figure: com.grafitto.laudate.domain.model.rhythm.RhythmFigure): String? =
        TabGlyphs.rest(figure)

    /** **Glifo completo de una figura** (cabeza + plica + corchete). */
    fun figureGlyph(figure: com.grafitto.laudate.domain.model.rhythm.RhythmFigure): String? =
        TabGlyphs.figureGlyph(figure)

    /** **Glifo de icono** de una figura: el completo si es nota, el del silencio si no. */
    fun iconGlyph(figure: com.grafitto.laudate.domain.model.rhythm.RhythmFigure): String? =
        TabGlyphs.iconGlyph(figure)

    /** **Caja de tinta** del icono de una figura, en em. */
    fun iconInk(figure: com.grafitto.laudate.domain.model.rhythm.RhythmFigure): TabGlyphs.Ink? =
        TabGlyphs.iconInk(figure)
}
