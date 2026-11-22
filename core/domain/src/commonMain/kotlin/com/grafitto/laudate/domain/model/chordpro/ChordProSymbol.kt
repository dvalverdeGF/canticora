package com.grafitto.laudate.domain.model.chordpro

/**
 * Categoría de un símbolo ChordPro, usada para agrupar la paleta del editor.
 */
enum class ChordProSymbolCategory(val displayName: String) {
    STRUM_DOWN("Rasgueo ↓"),
    STRUM_UP("Rasgueo ↑"),
    BAR("Barras de compás"),
    REPEAT("Repeticiones"),
    ACCIDENTAL("Alteraciones"),
    OTHER("Otros")
}

/**
 * Símbolos musicales de la fuente oficial de ChordPro, invocables con el marcado `<sym name/>`.
 *
 * El Dominio guarda el **nombre canónico** ([symbolName]) para poder exportar/importar y
 * round-trip-ear con cualquier programa ChordPro. [glyph] es una aproximación Unicode que usa
 * Canticora para dibujarlos en pantalla (la fuente real de ChordPro no está embarcada).
 *
 * Referencia: <https://www.chordpro.org/chordpro/chordpro-symbols/>
 */
enum class ChordProSymbol(
    val symbolName: String,
    val glyph: String,
    val category: ChordProSymbolCategory,
    val label: String
) {
    // ── Rasgueo hacia abajo ────────────────────────────────────────────────
    ARROW_DOWN("arrow-down", "↓", ChordProSymbolCategory.STRUM_DOWN, "Abajo"),
    ARROW_DOWN_ACCENT("arrow-down-with-accent", "↓'", ChordProSymbolCategory.STRUM_DOWN, "Abajo acentuado"),
    ARROW_DOWN_ARPEGGIO("arrow-down-with-arpeggio", "↓~", ChordProSymbolCategory.STRUM_DOWN, "Abajo arpegiado"),
    ARROW_DOWN_STACCATO("arrow-down-with-staccato", "↓·", ChordProSymbolCategory.STRUM_DOWN, "Abajo staccato"),
    ARROW_DOWN_ACCENT_ARPEGGIO(
        "arrow-down-with-accent-and-arpeggio", "↓'~", ChordProSymbolCategory.STRUM_DOWN, "Abajo acentuado y arpegiado"
    ),
    ARROW_DOWN_ACCENT_STACCATO(
        "arrow-down-with-accent-and-staccato", "↓'·", ChordProSymbolCategory.STRUM_DOWN, "Abajo acentuado y staccato"
    ),
    ARROW_DOWN_MUTED("arrow-down-muted", "↓×", ChordProSymbolCategory.STRUM_DOWN, "Abajo apagado"),
    ARROW_DOWN_MUTED_ACCENT("arrow-down-muted-with-accent", "↓×'", ChordProSymbolCategory.STRUM_DOWN, "Abajo apagado acentuado"),
    ARROW_DOWN_MUTED_ARPEGGIO("arrow-down-muted-with-arpeggio", "↓×~", ChordProSymbolCategory.STRUM_DOWN, "Abajo apagado arpegiado"),
    ARROW_DOWN_MUTED_ACCENT_ARPEGGIO(
        "arrow-down-muted-with-accent-and-arpeggio", "↓×'~", ChordProSymbolCategory.STRUM_DOWN, "Abajo apagado acentuado y arpegiado"
    ),
    ARROW_MUTE("arrow-mute", "×", ChordProSymbolCategory.STRUM_DOWN, "Apagado (mute)"),

    // ── Rasgueo hacia arriba ───────────────────────────────────────────────
    ARROW_UP("arrow-up", "↑", ChordProSymbolCategory.STRUM_UP, "Arriba"),
    ARROW_UP_ACCENT("arrow-up-with-accent", "↑'", ChordProSymbolCategory.STRUM_UP, "Arriba acentuado"),
    ARROW_UP_ARPEGGIO("arrow-up-with-arpeggio", "↑~", ChordProSymbolCategory.STRUM_UP, "Arriba arpegiado"),
    ARROW_UP_STACCATO("arrow-up-with-staccato", "↑·", ChordProSymbolCategory.STRUM_UP, "Arriba staccato"),
    ARROW_UP_ACCENT_ARPEGGIO(
        "arrow-up-with-accent-and-arpeggio", "↑'~", ChordProSymbolCategory.STRUM_UP, "Arriba acentuado y arpegiado"
    ),
    ARROW_UP_ACCENT_STACCATO(
        "arrow-up-with-accent-and-staccato", "↑'·", ChordProSymbolCategory.STRUM_UP, "Arriba acentuado y staccato"
    ),
    ARROW_UP_MUTED("arrow-up-muted", "↑×", ChordProSymbolCategory.STRUM_UP, "Arriba apagado"),
    ARROW_UP_MUTED_ACCENT("arrow-up-muted-with-accent", "↑×'", ChordProSymbolCategory.STRUM_UP, "Arriba apagado acentuado"),
    ARROW_UP_MUTED_ARPEGGIO("arrow-up-muted-with-arpeggio", "↑×~", ChordProSymbolCategory.STRUM_UP, "Arriba apagado arpegiado"),
    ARROW_UP_MUTED_ACCENT_ARPEGGIO(
        "arrow-up-muted-with-accent-and-arpeggio", "↑×'~", ChordProSymbolCategory.STRUM_UP, "Arriba apagado acentuado y arpegiado"
    ),

    // ── Barras de compás ───────────────────────────────────────────────────
    BAR("bar", "|", ChordProSymbolCategory.BAR, "Barra simple"),
    DOUBLE_BAR("double-bar", "‖", ChordProSymbolCategory.BAR, "Barra doble"),
    DOUBLE_THICK_BAR("double-thick-bar", "┃", ChordProSymbolCategory.BAR, "Barra gruesa doble"),
    THICK_BAR("thick-bar", "▌", ChordProSymbolCategory.BAR, "Barra gruesa"),
    START_BAR("start-bar", "|:", ChordProSymbolCategory.BAR, "Inicio"),
    END_BAR("end-bar", ":|", ChordProSymbolCategory.BAR, "Fin"),

    // ── Repeticiones ───────────────────────────────────────────────────────
    REPEAT_COLON("repeat-colon", ":|:", ChordProSymbolCategory.REPEAT, "Repetición (inicio)"),
    REPEAT_START("repeat-start", "|:", ChordProSymbolCategory.REPEAT, "Comenzar repetición"),
    REPEAT_END("repeat-end", ":|", ChordProSymbolCategory.REPEAT, "Terminar repetición"),
    REPEAT_END_START("repeat-end-start", ":|:", ChordProSymbolCategory.REPEAT, "Terminar y comenzar"),
    REPEAT1("repeat1", "¹", ChordProSymbolCategory.REPEAT, "Primera vez"),
    REPEAT2("repeat2", "²", ChordProSymbolCategory.REPEAT, "Segunda vez"),

    // ── Alteraciones ───────────────────────────────────────────────────────
    FLAT("flat", "♭", ChordProSymbolCategory.ACCIDENTAL, "Bemol"),
    NATURAL("natural", "♮", ChordProSymbolCategory.ACCIDENTAL, "Becuadro"),
    SHARP("sharp", "♯", ChordProSymbolCategory.ACCIDENTAL, "Sostenido"),

    // ── Otros ──────────────────────────────────────────────────────────────
    DELTA("delta", "△", ChordProSymbolCategory.OTHER, "Delta");

    /**
     * `true` si el símbolo se dibuja **vectorialmente** (flechas de rasgueo) en vez de
     * representarse con su glifo Unicode. Es lógica pura: la UI decide cómo dibujarlo, pero
     * qué símbolos son vectoriales lo determina el modelo, no la plataforma. Ver ADR-013
     * (lo consumen tanto `:app` como `:core:presentation`).
     */
    fun isVectorial(): Boolean =
        category == ChordProSymbolCategory.STRUM_DOWN || category == ChordProSymbolCategory.STRUM_UP

    companion object {
        private val byName: Map<String, ChordProSymbol> =
            entries.associateBy { it.symbolName.lowercase() }

        private val SYM_REGEX =
            Regex("""<sym\s+([^/>\s]+)(?:\s[^/>]*)?/>""", RegexOption.IGNORE_CASE)

        /** Símbolos con cifras o letras en círculo, generados por su carácter sistemático. */
        val circles: List<CircledSymbol> = buildList {
            ('0'..'9').forEach { c -> add(CircledSymbol("circle-$c", circledDigit(c), c)) }
            ('A'..'Z').forEach { c -> add(CircledSymbol("circle-$c", circledLetter(c), c)) }
        }

        /** Opciones de la paleta del editor agrupadas por categoría. */
        fun palette(): Map<ChordProSymbolCategory, List<ChordProSymbol>> = entries.groupBy { it.category }

        /** Busca un símbolo por su nombre canónico (`arrow-down`, `sharp`, …). */
        fun fromName(name: String): ChordProSymbol? = byName[name.trim().lowercase()]

        /** Busca una letra o cifra en círculo (`circle-A`, `circle-7`, …). */
        fun circleFromName(name: String): CircledSymbol? =
            circles.firstOrNull { it.symbolName.equals(name.trim(), ignoreCase = true) }

        /** Marcado `<sym name/>` canónico para un símbolo. */
        fun markupOf(symbolName: String): String = "<sym $symbolName/>"

        /**
         * Sustituye cada marcado `<sym name/>` de un texto por su glifo Unicode.
         * Si el nombre no se reconoce, se deja el marcado tal cual (no se pierde información).
         */
        fun renderInline(text: String): String {
            if (!containsMarkup(text)) return text
            return SYM_REGEX.replace(text) { match ->
                val name = match.groupValues[1]
                fromName(name)?.glyph
                    ?: circleFromName(name)?.glyph
                    ?: match.value
            }
        }

        /** Indica si un texto contiene al menos un marcado `<sym .../>`. */
        fun containsMarkup(text: String): Boolean = SYM_REGEX.containsMatchIn(text)

        // ⓪ = U+24EA; ①..⑨ = U+2460..U+2468
        private fun circledDigit(c: Char): String =
            if (c == '0') "\u24EA" else Char(0x2460 + (c - '1')).toString()

        // Ⓐ..Ⓩ = U+24B6..U+24CF (contiguos)
        private fun circledLetter(c: Char): String = Char(0x24B6 + (c - 'A')).toString()
    }
}

/**
 * Letra o cifra en círculo de la fuente ChordPro (`circle-A`, `circle-0`, …).
 */
data class CircledSymbol(
    val symbolName: String,
    val glyph: String,
    val character: Char
)
