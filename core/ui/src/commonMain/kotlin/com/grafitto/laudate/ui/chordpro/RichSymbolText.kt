package com.grafitto.laudate.ui.chordpro

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import com.grafitto.laudate.domain.model.chordpro.ChordProSymbol
import com.grafitto.laudate.ui.rhythm.StrumSymbol

/** Tamaño por defecto del símbolo vectorial embebido en el texto. */
private val DEFAULT_SYMBOL_SIZE: Dp = 18.dp

/**
 * Renderiza un texto que puede contener marcados ChordPro `<sym name/>`.
 *
 * Los símbolos de rasgueo se dibujan **vectorialmente** y se insertan como **contenido inline**, de modo
 * que el texto conserva su comportamiento normal: **envuelve líneas**, respeta `fontSize`, `color`,
 * `fontWeight` y `fontStyle`, y se alinea correctamente. Los símbolos restantes (barras, repeticiones,
 * alteraciones y círculos) se sustituyen por su glifo Unicode dentro del propio texto.
 *
 * Se implementa con `AnnotatedString` + `InlineTextContent`: **no** usa `Row`/`horizontalScroll`,
 * que rompería el salto de línea de la letra.
 */
@Composable
fun RichSymbolText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    symbolSize: Dp = DEFAULT_SYMBOL_SIZE
) {
    val effectiveColor = if (color.isUnspecified) MaterialTheme.colorScheme.onSurface else color

    if (!ChordProSymbol.containsMarkup(text)) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            lineHeight = lineHeight
        )
        return
    }

    // Nombre canónico de cada símbolo vectorial que aparece, en orden de aparición.
    val layout = SymbolTextLayoutParser.layout(text)
    val vectorialNames = layout.vectorialNames
    val renderedText = layout.renderedText

    // El hueco reservado al símbolo sigue el tamaño de la letra para que la línea no se descuadre.
    val placeholderSize: TextUnit = if (fontSize.isUnspecified) 18.sp else fontSize * 1.15f

    // Cada marcador recibe su propio id y su propio contenido vectorial.
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        var occurrence = 0
        renderedText.forEach { ch ->
            if (ch == SYMBOL_MARKER) {
                val index = occurrence++
                val id = "sym$index"
                val name = vectorialNames.getOrNull(index).orEmpty()
                inlineContent[id] = InlineTextContent(
                    placeholder = Placeholder(
                        width = placeholderSize,
                        height = placeholderSize,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    StrumSymbol(
                        symbolName = name,
                        color = effectiveColor,
                        size = symbolSize,
                        modifier = Modifier.size(symbolSize)
                    )
                }
                appendInlineContent(id, "[sym]")
            } else {
                append(ch)
            }
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        inlineContent = inlineContent,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        lineHeight = lineHeight
    )
}
