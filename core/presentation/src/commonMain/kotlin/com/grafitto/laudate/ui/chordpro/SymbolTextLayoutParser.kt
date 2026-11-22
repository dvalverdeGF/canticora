package com.grafitto.laudate.ui.chordpro

import com.grafitto.laudate.domain.model.chordpro.ChordProSymbol

/** Carácter de reemplazo usado como marcador temporal del símbolo vectorial. */
const val SYMBOL_MARKER = '\uFFFC'

/**
 * Resultado de preparar un texto con símbolos para su renderizado.
 */
data class SymbolTextLayout(
    /** Texto con los glifos no vectoriales ya resueltos y marcadores `\uFFFC` en los vectoriales. */
    val renderedText: String,
    /** Nombre canónico de cada símbolo vectorial, en orden de aparición. */
    val vectorialNames: List<String>
)

private val SYM_REGEX = Regex("""<sym\s+([^/>\s]+)(?:\s[^/>]*)?/>""", RegexOption.IGNORE_CASE)

/**
 * Lógica pura de preparación del texto con símbolos ChordPro (sin dependencias de Compose).
 *
 * - Los símbolos de rasgueo (vectoriales) se sustituyen por un marcador, para insertarlos como
 *   contenido inline.
 * - Los demás se resuelven a su glifo Unicode dentro del texto.
 * - Un marcado desconocido se conserva tal cual (no se pierde información).
 */
object SymbolTextLayoutParser {

    fun layout(text: String): SymbolTextLayout {
        if (!ChordProSymbol.containsMarkup(text)) {
            return SymbolTextLayout(renderedText = text, vectorialNames = emptyList())
        }

        val names = mutableListOf<String>()
        val rendered = SYM_REGEX.replace(text) { match ->
            val name = match.groupValues[1]
            val symbol = ChordProSymbol.fromName(name)
            if (symbol != null && symbol.isVectorial()) {
                names.add(symbol.symbolName)
                SYMBOL_MARKER.toString()
            } else {
                symbol?.glyph
                    ?: ChordProSymbol.circleFromName(name)?.glyph
                    ?: match.value
            }
        }
        return SymbolTextLayout(renderedText = rendered, vectorialNames = names)
    }
}
