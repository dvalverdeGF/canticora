package com.grafitto.laudate.ui.chordpro

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grafitto.laudate.domain.model.chordpro.ChordProSymbol
import com.grafitto.laudate.domain.model.chordpro.ChordProSymbolCategory
import com.grafitto.laudate.ui.rhythm.StrumSymbol

/**
 * Dibuja un símbolo ChordPro con la mejor representación disponible:
 *
 * - Las **flechas de rasgueo** se dibujan vectorialmente ([StrumSymbol]), de modo que la articulación
 *   (acento, arpegio, staccato) se compone sin solaparse y nítida a cualquier tamaño.
 * - El resto (barras, repeticiones, alteraciones, círculos) usa su **glifo Unicode**.
 */
@Composable
fun ChordProSymbolGlyph(
    symbolName: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 18.dp,
    fontSize: TextUnit = 16.sp
) {
    val symbol = ChordProSymbol.fromName(symbolName)
    if (symbol != null && symbol.isVectorial()) {
        StrumSymbol(symbolName = symbol.symbolName, modifier = modifier, color = color, size = size)
    } else {
        Text(
            text = symbol?.glyph ?: symbolName,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Glifo de un símbolo ya resuelto del dominio, para usar en la paleta.
 */
@Composable
fun ChordProSymbolGlyph(
    symbol: ChordProSymbol,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 18.dp,
    fontSize: TextUnit = 16.sp
) {
    if (symbol.isVectorial()) {
        StrumSymbol(symbolName = symbol.symbolName, modifier = modifier, color = color, size = size)
    } else {
        Text(
            text = symbol.glyph,
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Glifo de una letra o cifra en círculo. Estos símbolos siempre usan su carácter Unicode.
 */
@Composable
fun CircledSymbolGlyph(
    symbolName: String,
    color: Color = MaterialTheme.colorScheme.primary,
    fontSize: TextUnit = 16.sp
) {
    val circled = ChordProSymbol.circleFromName(symbolName)
    Text(
        text = circled?.glyph ?: symbolName,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.Medium
    )
}

/** Tamaño por defecto de los glifos vectoriales en la paleta del editor. */
val SYMBOL_PALETTE_GLYPH_SIZE: Dp = 18.dp
