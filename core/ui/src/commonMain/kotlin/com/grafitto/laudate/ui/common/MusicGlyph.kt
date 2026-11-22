package com.grafitto.laudate.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.tablature.TabGlyphs
import com.grafitto.laudate.ui.tablature.MusicFont

/**
 * (D-91) **Proporción de la caja que ocupa la tinta** del glifo. No se rellena hasta el borde: a
 * tamaño pequeño (16–22 dp) el glifo pegado a los lados se ve «gordo» y pierde el aire que sí tiene
 * en la partitura.
 */
private const val INK_FILL = 0.74f

/**
 * **Glifo musical suelto** pintado con **Bravura** ([MusicFont]).
 *
 * Existe porque el texto normal **no puede** pintar símbolos musicales en todas las plataformas: en
 * la web (Kotlin/Wasm + CanvasKit) el texto se compone con las fuentes que trae el propio motor, sin
 * *fallback* del sistema ni descarga de fuentes, así que `♩`, `♪`, `𝅝`… salían como **cajas vacías**
 * (glifo `.notdef`). En Android y escritorio el sistema sí los cubría, y por eso el defecto solo se
 * veía en la PWA.
 *
 * Se dibuja en un `Canvas` y no con `Text` por dos motivos: la caja de línea de Bravura mide
 * `2,012 + 2,012 = 4,024 em` (más de cuatro veces el cuerpo), lo que **infla la altura de línea** del
 * texto que la rodea; y así se puede **centrar por tinta** ([MusicFont.inkOf]) en lugar de por línea
 * base, que es lo que deja los símbolos planos (redonda, bemoles) diminutos y descentrados.
 *
 * @param glyph glifo SMuFL (uso privado `U+E000…U+F8FF`) o símbolo musical Unicode (`♩`, `♪`…).
 * @param size lado de la caja cuadrada donde se dibuja.
 */
@Composable
fun MusicGlyph(
    glyph: String,
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) {
    val measurer = rememberTextMeasurer()
    val family = MusicFont.family()
    Canvas(modifier.size(size)) {
        drawMusicGlyph(measurer, glyph, family, color, dotted = false)
    }
}

/**
 * **Figura rítmica** (negra, corchea, silencio…) dibujada con **Bravura**, con su **puntillo**.
 *
 * Usa el **glifo completo** de [MusicFont.iconGlyph] (cabeza + plica + corchete ya dibujados por la
 * fuente) y, si la figura es con puntillo, superpone el `augmentationDot`, exactamente igual que
 * monta TuxGuitar su icono de duración. Componer el símbolo con el carácter Unicode que lleva
 * [RhythmFigure.symbol] (`♩` + `·`) no servía: el puntillo tipográfico no existe en Bravura y la
 * figura con puntillo se veía **igual** que la figura base.
 */
@Composable
fun MusicFigureGlyph(
    figure: RhythmFigure,
    size: Dp,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current
) {
    val measurer = rememberTextMeasurer()
    val family = MusicFont.family()
    val glyph = MusicFont.iconGlyph(figure) ?: figure.symbol
    val dotted = figure.isDotted
    Canvas(modifier.size(size)) {
        drawMusicGlyph(measurer, glyph, family, color, dotted = dotted)
    }
}

/**
 * Pinta [glyph] con la fuente musical, **centrado por tinta** y **acotado por alto y por ancho**.
 *
 * El cuerpo se elige para que la tinta ocupe [INK_FILL] del lado de la caja, sin que el avance del
 * glifo (más el puntillo, si lo lleva) se salga del ancho disponible.
 */
private fun DrawScope.drawMusicGlyph(
    measurer: TextMeasurer,
    glyph: String,
    family: FontFamily,
    color: Color,
    dotted: Boolean
) {
    val side = size.minDimension
    if (side <= 0f || glyph.isEmpty()) return

    val ink = MusicFont.inkOf(glyph)
    // Medida de referencia con el cuerpo igual al lado: sirve para acotar por ancho y centrar en
    // horizontal. El cuerpo definitivo se vuelve a medir ya escalado, para no deformar el glifo.
    val reference = measurer.measure(
        text = glyph,
        style = TextStyle(color = color, fontFamily = family, fontSize = side.toSp()),
        maxLines = 1,
        softWrap = false
    )
    val dotReference = if (dotted) {
        measurer.measure(
            text = TabGlyphs.Glyph.AUGMENTATION_DOT,
            style = TextStyle(color = color, fontFamily = family, fontSize = side.toSp()),
            maxLines = 1,
            softWrap = false
        )
    } else {
        null
    }
    val naturalWidth = reference.size.width.toFloat() +
        (dotReference?.size?.width?.toFloat() ?: 0f)
    val inkHeight = (ink?.height ?: 1f) * side
    val scale = minOf(
        if (inkHeight > 0f) INK_FILL * side / inkHeight else Float.MAX_VALUE,
        if (naturalWidth > 0f) side / naturalWidth else Float.MAX_VALUE
    )

    val em = side * scale
    val layout = measurer.measure(
        text = glyph,
        style = TextStyle(color = color, fontFamily = family, fontSize = em.toSp()),
        maxLines = 1,
        softWrap = false
    )
    val totalWidth = layout.size.width.toFloat() +
        (dotReference?.size?.width?.toFloat() ?: 0f) * scale
    // La tinta se centra en el eje vertical; la línea base se desplaza para que el centro de la tinta
    // caiga en el centro de la caja.
    val baseline = size.height / 2f + (ink?.centerFromBaseline ?: 0f) * em
    val left = (size.width - totalWidth) / 2f
    drawText(layout, topLeft = Offset(left, baseline - layout.firstBaseline))

    if (dotted) {
        // El puntillo se dibuja **en la misma línea base** que la figura: su tinta está centrada en
        // ella, que es justo la altura de la cabeza de nota, así que no hay que colocarlo aparte.
        val dot = measurer.measure(
            text = TabGlyphs.Glyph.AUGMENTATION_DOT,
            style = TextStyle(color = color, fontFamily = family, fontSize = em.toSp()),
            maxLines = 1,
            softWrap = false
        )
        drawText(
            dot,
            topLeft = Offset(left + layout.size.width.toFloat(), baseline - dot.firstBaseline)
        )
    }
}
