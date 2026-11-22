package com.grafitto.laudate.ui.rhythm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.rhythm.StrumArticulation
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.StrumRange

/**
 * Dirección + articulación + registro extraídos de un símbolo de rasgueo.
 * (`arrow-down-with-accent` → [StrumDirection.DOWN] + [StrumArticulation.ACCENT]).
 */
data class StrumSymbolSpec(
    val direction: StrumDirection,
    val articulation: StrumArticulation,
    val range: StrumRange = StrumRange.ALL
) {
    companion object {
        fun parse(symbolName: String): StrumSymbolSpec {
            // "muted" (apagado) se comprueba antes que "down"/"up": ChordPro lo codifica como sufijo
            // (`arrow-down-muted`) y en Canticora equivale a la dirección MUTE.
            val base = when {
                symbolName.contains("muted") || symbolName.startsWith("arrow-mute") -> StrumDirection.MUTE
                symbolName.startsWith("arrow-down") -> StrumDirection.DOWN
                symbolName.startsWith("arrow-up") -> StrumDirection.UP
                else -> StrumDirection.NONE
            }
            val articulation = when {
                symbolName.contains("accent-and-arpeggio") -> StrumArticulation.ACCENT_ARPEGGIO
                symbolName.contains("accent-and-staccato") -> StrumArticulation.ACCENT_STACCATO
                symbolName.contains("with-accent") -> StrumArticulation.ACCENT
                symbolName.contains("with-arpeggio") -> StrumArticulation.ARPEGGIO
                symbolName.contains("with-staccato") -> StrumArticulation.STACCATO
                else -> StrumArticulation.NONE
            }
            return StrumSymbolSpec(base, articulation)
        }
    }
}

/**
 * Dibuja un símbolo de rasgueo ChordPro de forma **vectorial**, sin empaquetar ninguna fuente.
 *
 * Motivo: la fuente oficial `ChordProSymbols.ttf` está licenciada **GPL-3.0-or-later**, lo que
 * impondría esa licencia a toda la aplicación. Estos glifos son propios de Canticora y libres de
 * ataduras, y escalan a cualquier tamaño sin pérdida.
 *
 * Representa la flecha de dirección y, a su derecha, las marcas de articulación apiladas:
 * acento (`>`), arpegio (`~`) y staccato (`·`).
 */
@Composable
fun StrumSymbol(
    symbolName: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 20.dp
) {
    val spec = remember(symbolName) { StrumSymbolSpec.parse(symbolName) }
    Canvas(modifier = modifier.size(size)) {
        drawStrumSymbol(spec, color)
    }
}

/** Variante para [StrumDirection] + [StrumArticulation] ya resueltos. */
@Composable
fun StrumSymbol(
    direction: StrumDirection,
    articulation: StrumArticulation,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 20.dp,
    range: StrumRange = StrumRange.ALL
) {
    Canvas(modifier = modifier.size(size)) {
        drawStrumSymbol(StrumSymbolSpec(direction, articulation, range), color)
    }
}

private fun DrawScope.drawStrumSymbol(spec: StrumSymbolSpec, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = (w * 0.09f).coerceAtLeast(1.2f)
    val cx = w * 0.34f
    val marksX = w * 0.68f

    when (spec.direction) {
        StrumDirection.DOWN, StrumDirection.UP -> {
            val down = spec.direction == StrumDirection.DOWN
            // La flecha LARGA barre todas las cuerdas; la CORTA (registro parcial) ocupa ~60 %
            // del alto. Es la convención del cancionero: la longitud indica cuántas cuerdas suenan.
            //
            // La BANDA vertical donde se dibuja la flecha corta indica el registro, con la caja
            // orientada como se ve la guitarra al tocar: las **graves** (6.ª–4.ª) arriba y las
            // **agudas** (3.ª–1.ª) abajo. Así, la corta de graves ocupa la mitad superior y la de
            // agudos la mitad inferior. Sin esa banda, «graves» y «agudos» se dibujaban idénticos
            // y era imposible distinguirlos en la paleta.
            val bandTop: Float
            val bandBottom: Float
            when (spec.range) {
                StrumRange.BASS -> { bandTop = h * 0.06f; bandBottom = h * 0.50f }
                StrumRange.TREBLE -> { bandTop = h * 0.50f; bandBottom = h * 0.94f }
                StrumRange.ALL -> { bandTop = h * 0.06f; bandBottom = h * 0.94f }
            }
            val bandHeight = bandBottom - bandTop
            val shaftTop: Float
            val shaftBottom: Float
            val tipY: Float
            val armY: Float
            if (down) {
                shaftTop = bandTop
                shaftBottom = bandTop + bandHeight * 0.62f
                tipY = bandTop + bandHeight * 0.86f
                armY = bandTop + bandHeight * 0.60f
            } else {
                shaftTop = bandBottom
                shaftBottom = bandBottom - bandHeight * 0.62f
                tipY = bandBottom - bandHeight * 0.86f
                armY = bandBottom - bandHeight * 0.60f
            }
            drawLine(
                color = color,
                start = Offset(cx, shaftTop),
                end = Offset(cx, shaftBottom),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            val armW = w * 0.15f
            drawLine(color, Offset(cx, tipY), Offset(cx - armW, armY), stroke, StrokeCap.Round)
            drawLine(color, Offset(cx, tipY), Offset(cx + armW, armY), stroke, StrokeCap.Round)
        }

        StrumDirection.MUTE -> {
            // Flecha hacia abajo con el apagado (×) superpuesto.
            drawLine(color, Offset(cx, h * 0.08f), Offset(cx, h * 0.60f), stroke, StrokeCap.Round)
            drawLine(color, Offset(cx, h * 0.84f), Offset(cx - w * 0.15f, h * 0.58f), stroke, StrokeCap.Round)
            drawLine(color, Offset(cx, h * 0.84f), Offset(cx + w * 0.15f, h * 0.58f), stroke, StrokeCap.Round)
            val xr = w * 0.13f
            drawLine(color, Offset(cx - xr, h * 0.56f), Offset(cx + xr, h * 0.82f), stroke, StrokeCap.Round)
            drawLine(color, Offset(cx - xr, h * 0.82f), Offset(cx + xr, h * 0.56f), stroke, StrokeCap.Round)
        }

        StrumDirection.NONE -> {
            // Silencio: barra vertical.
            drawLine(color, Offset(cx, h * 0.12f), Offset(cx, h * 0.88f), stroke, StrokeCap.Round)
        }
    }

    // Marcas de articulación, apiladas a la derecha para no solaparse entre sí.
    if (spec.articulation.isAccent) {
        // Acento: chevron ">".
        drawLine(color, Offset(marksX, h * 0.30f), Offset(marksX + w * 0.12f, h * 0.40f), stroke, StrokeCap.Round)
        drawLine(color, Offset(marksX + w * 0.12f, h * 0.40f), Offset(marksX, h * 0.50f), stroke, StrokeCap.Round)
    }
    if (spec.articulation.isArpeggio) {
        // Arpegio: pequeña onda "~" dibujada como zigzag.
        val waveY = h * 0.62f
        val stepW = w * 0.06f
        drawLine(color, Offset(marksX - stepW, waveY + h * 0.04f), Offset(marksX, waveY), stroke, StrokeCap.Round)
        drawLine(color, Offset(marksX, waveY), Offset(marksX + stepW, waveY + h * 0.04f), stroke, StrokeCap.Round)
        drawLine(
            color,
            Offset(marksX + stepW, waveY + h * 0.04f),
            Offset(marksX + stepW * 2, waveY),
            stroke,
            StrokeCap.Round
        )
    }
    if (spec.articulation.isStaccato) {
        // Staccato: punto.
        drawCircle(color, radius = w * 0.055f, center = Offset(marksX + w * 0.04f, h * 0.78f))
    }
}

/**
 * Etiqueta corta de una articulación, para los controles del editor de patrones.
 */
fun StrumArticulation.shortGlyph(): String = when (this) {
    StrumArticulation.NONE -> "—"
    StrumArticulation.ACCENT -> ">"
    StrumArticulation.ARPEGGIO -> "~"
    StrumArticulation.STACCATO -> "·"
    StrumArticulation.ACCENT_ARPEGGIO -> ">~"
    StrumArticulation.ACCENT_STACCATO -> ">·"
}

/**
 * Nombre legible de una articulación.
 */
fun StrumArticulation.displayNameEs(): String = when (this) {
    StrumArticulation.NONE -> "Normal"
    StrumArticulation.ACCENT -> "Acento"
    StrumArticulation.ARPEGGIO -> "Arpegio"
    StrumArticulation.STACCATO -> "Staccato"
    StrumArticulation.ACCENT_ARPEGGIO -> "Acento + arpegio"
    StrumArticulation.ACCENT_STACCATO -> "Acento + staccato"
}
