package com.grafitto.laudate.domain.serializer

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure

/**
 * Utilidades **mínimas de XML por bloques** compartidas por los parsers de partituras.
 *
 * El Dominio no puede usar un parser XML de la plataforma (Kotlin puro, `commonMain`), y los
 * formatos que se leen aquí —MusicXML, GPIF de Guitar Pro y XML de TuxGuitar— son **planos y
 * estables**: cada elemento aparece una vez y los hijos cuelgan directamente del padre. Por eso
 * basta con localizar bloques por etiqueta y leer sus hijos directos.
 *
 * Todas las funciones devuelven `null`/lista vacía si no encuentran nada: los parsers son
 * **tolerantes** por contrato (lo que no se entiende se ignora, no se lanza).
 */
internal object TabXml {

    /** Bloques `<tag ...>…</tag>`, en orden de aparición. Admite atributos en la apertura. */
    fun blocks(xml: String, tag: String): List<String> =
        Regex("<$tag(?:\\s[^>]*)?>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()

    /** Primer bloque `<tag ...>…</tag>`, o `null`. */
    fun firstBlock(xml: String, tag: String): String? =
        Regex("<$tag(?:\\s[^>]*)?>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)
            ?.groupValues
            ?.get(1)

    /** Texto del **primer hijo directo** `<tag>valor</tag>` (sin anidar), recortado. */
    fun child(xml: String, tag: String): String? =
        Regex("<$tag(?:\\s[^>]*)?>([^<]*)</$tag>")
            .find(xml)
            ?.groupValues
            ?.get(1)
            ?.trim()

    /** Entero del primer hijo `<tag>`, o `null` si falta o no es un número. */
    fun intChild(xml: String, tag: String): Int? = child(xml, tag)?.toIntOrNull()

    /** `true` si el XML contiene la etiqueta (abierta, cerrada o vacía). */
    fun has(xml: String, tag: String): Boolean =
        Regex("<$tag(?:\\s[^>]*)?(?:/>|>)").containsMatchIn(xml)

    /** Valor del atributo [name] de la **primera** etiqueta `<tag ...>` del XML. */
    fun attribute(xml: String, tag: String, name: String): String? {
        val opening = Regex("<$tag(?:\\s[^>]*)?>").find(xml)?.value ?: return null
        return Regex("$name\\s*=\\s*\"([^\"]*)\"").find(opening)?.groupValues?.get(1)
    }

    /** Todos los valores de [name] de las etiquetas `<tag ...>` presentes en [xml]. */
    fun attributes(xml: String, tag: String, name: String): List<String> =
        Regex("<$tag(?:\\s[^>]*)?>").findAll(xml).mapNotNull { match ->
            Regex("$name\\s*=\\s*\"([^\"]*)\"").find(match.value)?.groupValues?.get(1)
        }.toList()

    /** Quita las etiquetas de un fragmento XML y devuelve el texto que queda, recortado. */
    fun textOf(xml: String): String =
        xml.replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim()

    /**
     * Figura rítmica a partir del **nombre** de la duración que usan MusicXML (`<type>`) y Guitar
     * Pro (`<NoteValue>`), con los puntillos indicados en [dots].
     *
     * Los nombres que no están en la tabla (una garrapatea, una semifusa…) devuelven `null`: el
     * llamante conserva la figura de la casilla anterior en vez de inventarse una duración.
     */
    fun figureOf(type: String?, dots: Int = 0): RhythmFigure? {
        val base = when (type?.trim()?.lowercase()) {
            "whole", "breve" -> RhythmFigure.WHOLE
            "half" -> RhythmFigure.HALF
            "quarter" -> RhythmFigure.QUARTER
            "eighth" -> RhythmFigure.EIGHTH
            "16th", "sixteenth" -> RhythmFigure.SIXTEENTH
            else -> null
        } ?: return null
        if (dots <= 0) return base
        return when (base) {
            RhythmFigure.HALF -> RhythmFigure.DOTTED_HALF
            RhythmFigure.QUARTER -> RhythmFigure.DOTTED_QUARTER
            RhythmFigure.EIGHTH -> RhythmFigure.DOTTED_EIGHTH
            else -> base
        }
    }

    /** Silencio equivalente a una figura sonora (para las casillas de pausa). */
    fun restOf(figure: RhythmFigure): RhythmFigure =
        RhythmFigure.restEquivalentOf(figure) ?: RhythmFigure.QUARTER_REST
}
