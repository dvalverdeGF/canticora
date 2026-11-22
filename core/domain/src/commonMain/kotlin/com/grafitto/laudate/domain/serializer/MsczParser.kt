package com.grafitto.laudate.domain.serializer

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabDynamic
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import com.grafitto.laudate.domain.model.tablature.TabTupletBracketType
import com.grafitto.laudate.domain.model.tablature.TabTupletNumberType
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.tablature.TabSymbolCompatibility

/**
 * **Parser de `.mscz`** (MuseScore) → [Tablature] (tarea del usuario 2026-10-12: «parsea y sustituye la
 * tablatura que hay en Canticora por `salmo2`»).
 *
 * El `.mscz` es un ZIP con un `*.mscx` dentro (XML). Aquí se parsea el **XML ya extraído**: la
 * extracción del ZIP la hace la plataforma (jvm/android), de modo que este parser es **Kotlin puro**.
 *
 * Es un parser **mínimo y honesto**: entiende lo que necesita una tablatura monofónica de guitarra
 * (compás, tempo, cuerdas/trastes, duraciones, articulaciones y voces), que es exactamente el caso de
 * `salmo2`. No pretende cubrir todo MuseScore.
 *
 * No usa una biblioteca de XML (no hay en `commonMain` y el formato lo emite MuseScore, estable): se
 * apoya en los **bloques con etiqueta** (`<Measure>`, `<Chord>`, `<Rest>`, `<Articulation>`) y en sus
 * hijos directos, que es determinista.
 */
object MsczParser {

    /**
     * **Símbolos musicales** que MuseScore escribe como `<sym>` dentro de un texto, con su punto de
     * código **SMuFL** (el mismo que usa la fuente Bravura que pinta la tablatura). Solo los que
     * aparecen en las indicaciones de tempo; el resto se descarta.
     */
    private val TEMPO_SYMBOLS: Map<String, String> = mapOf(
        "metNoteWhole" to "\uECA2",
        "metNoteHalfUp" to "\uECA3",
        "metNoteQuarterUp" to "\uECA5",
        "metNote8thUp" to "\uECA7",
        "metNote16thUp" to "\uECA9",
        "metNote32ndUp" to "\uECAB",
        "metNote64thUp" to "\uECAD",
        "metNoteHalfDown" to "\uECA4",
        "metNoteQuarterDown" to "\uECA6",
        "metNote8thDown" to "\uECA8",
        "metAugmentationDot" to "\uE1E7"
    )

    /** Una nota cruda leída del `.mscx`, antes de decidir su figura. */
    private data class ParsedItem(
        val isRest: Boolean,
        val durationType: String,
        val fret: Int?,
        val string: Int?,
        val articulations: List<String>,
        /** `true` si la duración es `measure` (compás entero de silencio). */
        val wholeMeasure: Boolean,
        /** **Grupo irregular** en el que cae el item, o `null` si no está en ninguno. */
        val tuplet: TabTuplet? = null
    )

    /**
     * **Evento de un compás en orden de aparición**: una nota o una frontera de tresillo.
     *
     * MuseScore escribe el `<Tuplet>` como **hermano** de las notas que agrupa (no las envuelve):
     *
     * ```xml
     * <Tuplet><normalNotes>2</normalNotes><actualNotes>3</actualNotes>…</Tuplet>
     * <Rest><durationType>half</durationType></Rest>
     * <Rest><durationType>half</durationType></Rest>
     * <Rest><durationType>half</durationType></Rest>
     * <endTuplet/>
     * ```
     *
     * Por eso hay que **intercalarlo** en el recorrido del compás: un `findAll` de `<Chord>|<Rest>`
     * se salta el tresillo y las notas salían como negras sueltas (sin corchete ni «3»).
     */
    private sealed interface MeasureEvent {
        val pos: Int

        data class Note(override val pos: Int, val match: MatchResult) : MeasureEvent

        /**
         * `<Tuplet>`: [actualNotes] es el numerador (3 en un tresillo) y [normalNotes] el denominador
         * (2 en un tresillo). MuseScore los escribe como `<actualNotes>`/`<normalNotes>`.
         */
        data class TupletBegin(
            override val pos: Int,
            val actualNotes: Int?,
            val normalNotes: Int?,
            /**
             * `TupletNumberType` de MuseScore, tal cual lo escribe el `.mscx` (`<numberType>`, un
             * entero: `0` número, `1` proporción, `2` sin texto). `null` si el fichero no lo trae.
             */
            val numberType: Int? = null,
            /**
             * `TupletBracketType` de MuseScore (`<bracketType>`: `0` automático, `1` con corchete,
             * `2` sin corchete). `null` si el fichero no lo trae.
             */
            val bracketType: Int? = null
        ) : MeasureEvent

        /** `<endTuplet/>`. */
        data class TupletEnd(override val pos: Int) : MeasureEvent
    }

    /**
     * Convierte el **contenido del `*.mscx`** en una [Tablature]. [songId] es el canto al que se
     * asocia (una tablatura **siempre** pertenece a un canto); [title] y [tuning] se reciben aparte
     * (el título suele estar en un `VBox`, y la afinación es un preset del instrumento).
     */
    fun parse(
        mscx: String,
        songId: String,
        title: String = "Salmo",
        tuning: Tuning = Tuning.GUITAR,
        resolution: TabResolution = TabResolution.SIXTEENTH
    ): Tablature {
        val time = parseTimeSignature(mscx) ?: ScoreTimeSignature("4", 4)
        val measures = parseMeasures(mscx, resolution)

        return Tablature(
            id = "mscz",
            songId = songId,
            title = title,
            instrument = tuning.name,
            tuning = tuning,
            time = time,
            resolution = resolution,
            measures = measures,
            origin = com.grafitto.laudate.domain.model.tablature.TabOrigin.IMPORTED_TEXT
        )
    }

    /**
     * El bloque del **`<Staff>` que contiene la música** (`<Staff id="1">`). Hay que buscar por `id`:
     * `<Staff>` a secas es el contenedor del `<Part>` y `<StaffType>` también empieza por `<Staff`, así
     * que un `indexOf("<Staff")` devolvía un bloque sin compases.
     */
    private fun staffBlock(mscx: String): String {
        val regex = Regex("<Staff id=\"\\d+\">")
        val m = regex.find(mscx) ?: return mscx
        val start = m.range.first
        val end = mscx.indexOf("</Staff>", m.range.last)
        return if (end < 0) mscx.substring(start) else mscx.substring(start, end)
    }

    /** Lee `<TimeSig><sigN>4</sigN><sigD>4</sigD>`. */
    private fun parseTimeSignature(mscx: String): ScoreTimeSignature? {
        val block = firstTag(mscx, "TimeSig") ?: return null
        val n = firstChild(block, "sigN")?.toIntOrNull() ?: return null
        val d = firstChild(block, "sigD")?.toIntOrNull() ?: return null
        return ScoreTimeSignature(n.toString(), d)
    }

    /**
     * **Indicación de tempo de un compás** (`<Tempo>`): el BPM y el **rótulo literal**.
     *
     * MuseScore guarda el texto tal cual en `<Tempo><text>`, con los símbolos musicales como
     * `<sym>metNoteQuarterUp</sym>` y la fuente como `<font face="Edwin"/>`. El `salmo2` tiene **dos**
     * indicaciones en compases distintos (`♩ = 80` en el 1 y `Andante` en el 2), así que el tempo se
     * lee **por compás**: antes se tomaba el primero del archivo y se anclaba al compás 1, con lo que
     * la segunda indicación se perdía y la primera se inventaba como `♩ = 80 · Andante`.
     */
    private fun parseMeasureTempo(measureXml: String): Pair<Int?, String?>? {
        val block = firstTag(measureXml, "Tempo") ?: return null
        val bpm = firstChild(block, "tempo")
            ?.toDoubleOrNull()
            ?.let { kotlin.math.round(it * 60.0).toInt() }
            ?.takeIf { it in 20..400 }
        val text = firstTag(block, "text")?.let(::tempoTextOf)?.takeIf { it.isNotBlank() }
        if (bpm == null && text == null) return null
        return bpm to text
    }

    /**
     * Convierte el **texto enriquecido** de MuseScore en texto plano con **SMuFL**.
     *
     * `<sym>metNoteQuarterUp</sym><font face="Edwin"/> = 80` → `\uECA5 = 80`. Los símbolos que no
     * están en la tabla se descartan (mejor perder un adorno que dejar el XML a la vista).
     */
    private fun tempoTextOf(raw: String): String {
        val withSyms = Regex("<sym>([^<]*)</sym>").replace(raw) { m ->
            TEMPO_SYMBOLS[m.groupValues[1]] ?: ""
        }
        return withSyms.replace(Regex("<[^>]*>"), "").trim()
    }

    /**
     * **Dinámica del compás** (`<Dynamic><subtype>mp</subtype>`).
     *
     * MuseScore la guarda como anotación del compás, en el punto del compás donde se escribió. El
     * modelo de Canticora tiene **una** dinámica por compás ([TabMeasure.dynamic]), así que se toma la
     * primera; los subtipos que no están en [TabDynamic] (`rfz`, `sfp`…) se descartan en vez de
     * inventar un equivalente.
     */
    private fun parseMeasureDynamic(measureXml: String): TabDynamic? {
        val subtype = firstTag(measureXml, "Dynamic")
            ?.let { firstChild(it, "subtype") }
            ?.trim()
            ?: return null
        return enumValues<TabDynamic>().firstOrNull { it.glyph == subtype }
    }

    /** Todos los `<Measure>` del `<Staff>`, ya convertidos. */
    private fun parseMeasures(mscx: String, resolution: TabResolution): List<TabMeasure> {
        val staff = staffBlock(mscx)
        return findBlocks(staff, "Measure").map { measureXml ->
            val items = parseItems(measureXml)
            val slots = items.map { toSlot(it) }
            val tempo = parseMeasureTempo(measureXml)
            TabMeasure(
                slots = slots,
                dynamic = parseMeasureDynamic(measureXml),
                tempo = tempo?.first,
                tempoText = tempo?.second
            )
        }
    }

    /**
     * Extrae los `<Chord>` y `<Rest>` de un compás, **en orden**, marcando los que caen dentro de un
     * tresillo. Ver [MeasureEvent] para el porqué del recorrido por eventos.
     */
    private fun parseItems(measureXml: String): List<ParsedItem> {
        val events = mutableListOf<MeasureEvent>()
        Regex("<(Chord|Rest)>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL)
            .findAll(measureXml)
            .forEach { events += MeasureEvent.Note(it.range.first, it) }
        Regex("<Tuplet>(.*?)</Tuplet>", RegexOption.DOT_MATCHES_ALL)
            .findAll(measureXml)
            .forEach {
                events += MeasureEvent.TupletBegin(
                    it.range.first,
                    firstChild(it.groupValues[1], "actualNotes")?.toIntOrNull(),
                    firstChild(it.groupValues[1], "normalNotes")?.toIntOrNull(),
                    firstChild(it.groupValues[1], "numberType")?.toIntOrNull(),
                    firstChild(it.groupValues[1], "bracketType")?.toIntOrNull()
                )
            }
        Regex("<endTuplet\\s*/>")
            .findAll(measureXml)
            .forEach { events += MeasureEvent.TupletEnd(it.range.first) }
        events.sortBy { it.pos }

        val result = mutableListOf<ParsedItem>()
        // **Pila** de grupos irregulares abiertos: MuseScore los **anida** (un tresillo dentro de un
        // quintillo escribe dos `<Tuplet>` antes del primer `<endTuplet/>`), así que el grupo del item
        // es el de la **cima**. Antes solo se contaba la profundidad y se exigía `actualNotes == 3`,
        // de modo que un quintillo o un septillo se leían como figuras normales y el compás se
        // quedaba con la duración equivocada.
        val tupletStack = ArrayDeque<TabTuplet>()
        events.forEach { event ->
            when (event) {
                is MeasureEvent.TupletBegin -> {
                    val num = event.actualNotes
                    val normal = event.normalNotes
                    if (num != null && normal != null && num > 0 && normal > 0) {
                        tupletStack.addLast(
                            TabTuplet(
                                numNotes = num,
                                normalNotes = normal,
                                numberType = when (event.numberType) {
                                    1 -> TabTupletNumberType.SHOW_RELATION
                                    2 -> TabTupletNumberType.NO_TEXT
                                    else -> TabTupletNumberType.SHOW_NUMBER
                                },
                                bracketType = when (event.bracketType) {
                                    1 -> TabTupletBracketType.SHOW_BRACKET
                                    2 -> TabTupletBracketType.SHOW_NO_BRACKET
                                    else -> TabTupletBracketType.AUTO_BRACKET
                                }
                            )
                        )
                    }
                }
                is MeasureEvent.TupletEnd -> tupletStack.removeLastOrNull()
                is MeasureEvent.Note -> {
                    val inner = event.match.groupValues[2]
                    val durationType = firstChild(inner, "durationType") ?: "quarter"
                    val arts = Regex(
                        "<Articulation>.*?<subtype>([^<]+)</subtype>.*?</Articulation>",
                        RegexOption.DOT_MATCHES_ALL
                    ).findAll(inner).map { it.groupValues[1] }.toList()
                    result += ParsedItem(
                        isRest = event.match.groupValues[1] == "Rest",
                        durationType = durationType,
                        fret = firstChild(inner, "fret")?.toIntOrNull(),
                        string = firstChild(inner, "string")?.toIntOrNull(),
                        articulations = arts,
                        wholeMeasure = durationType == "measure",
                        tuplet = tupletStack.lastOrNull()
                    )
                }
            }
        }
        return result
    }

    /** Convierte un item (nota o silencio) en una **columna** ([TabSlot]). */
    private fun toSlot(item: ParsedItem): TabSlot {
        if (item.isRest || item.string == null || item.fret == null) {
            // Silencio: una columna **sin notas**, con su figura (para que la fila rítmica la dibuje).
            // Un **silencio de compás** (`durationType = measure`) es, en MuseScore, un silencio de
            // **redonda** que ocupa el compás entero: antes se devolvía un `TabSlot()` **vacío** y el
            // compás se quedaba en blanco (ni glifo ni ancho), que es el bug que se veía en los
            // compases 6+ del `salmo2`.
            val figure = if (item.wholeMeasure) RhythmFigure.WHOLE_REST else figureOf(item.durationType)
            return TabSlot(
                rhythmicFigure = RhythmFigure.restEquivalentOf(figure ?: RhythmFigure.QUARTER),
                tuplet = item.tuplet
            )
        }
        val techniques = mutableSetOf<TabTechnique>()
        val articulations = mutableSetOf<TabArticulation>()
        item.articulations.forEach { subtype ->
            when (subtype) {
                "stringsUpBow" -> techniques += TabTechnique.PICK_UP
                "stringsDownBow" -> techniques += TabTechnique.PICK_DOWN
                "stringsHarmonic" -> articulations += TabArticulation.HARMONIC
                "articAccentAbove" -> articulations += TabArticulation.ACCENT
                "articTenutoAbove" -> articulations += TabArticulation.TENUTO
                "articMarcatoAbove" -> articulations += TabArticulation.MARCATO
                "articStaccatoAbove" -> articulations += TabArticulation.STACCATO
                "articStaccatissimoAbove" -> articulations += TabArticulation.STACCATISSIMO
                // (D-40) Glifos **combinados** de MuseScore (`ARTICULATION_ABOVE_JOIN_GROUPS`).
                "articAccentStaccatoAbove" -> articulations += TabArticulation.ACCENT_STACCATO
                "articMarcatoStaccatoAbove" -> articulations += TabArticulation.MARCATO_STACCATO
                "articTenutoStaccatoAbove" -> articulations += TabArticulation.TENUTO_STACCATO
                "articTenutoAccentAbove" -> articulations += TabArticulation.TENUTO_ACCENT
                "articMarcatoTenutoAbove" -> articulations += TabArticulation.MARCATO_TENUTO
                // `articUnstressAbove` no tiene símbolo propio en Canticora: se ignora sin romper.
                else -> Unit
            }
        }
        return TabSlot(
            strings = listOf(
                TabPluck(
                    // **Conversión de numeración de cuerda**: MuseScore escribe `<string>` con índice
                    // **0-based desde la cuerda más aguda** (0 = la 1ª, la de arriba), mientras que
                    // [TabPosition.stringNumber] usa **1 = la más aguda** (como se lee en cualquier
                    // tablatura). Sin el `+1` toda la tablatura salía **una cuerda más aguda** de lo
                    // que dice la partitura (verificado contra el PDF: la nota `<string>5</string>`
                    // del `salmo2` va en la 6ª línea, la de abajo, no en la 5ª).
                    position = TabPosition(item.string + 1, item.fret),
                    techniques = techniques,
                    // (D-40) Se **combinan** las parejas que MuseScore dibuja en un solo glifo, para
                    // que el parser y el serializador produzcan la misma forma canónica.
                    articulations = TabSymbolCompatibility.join(articulations)
                )
            ),
            rhythmicFigure = figureOf(item.durationType),
            tuplet = item.tuplet
        )
    }

    /** `durationType` de MuseScore → figura del modelo. */
    private fun figureOf(durationType: String): RhythmFigure? = when (durationType) {
        "whole" -> RhythmFigure.WHOLE
        "half" -> RhythmFigure.HALF
        "quarter" -> RhythmFigure.QUARTER
        "eighth" -> RhythmFigure.EIGHTH
        "16th" -> RhythmFigure.SIXTEENTH
        "32nd" -> RhythmFigure.SIXTEENTH
        "half." -> RhythmFigure.DOTTED_HALF
        "quarter." -> RhythmFigure.DOTTED_QUARTER
        "eighth." -> RhythmFigure.DOTTED_EIGHTH
        else -> null
    }

    // ── Utilidades de XML por bloques (deterministas) ────────────────────────────────────────────

    private fun firstTag(xml: String, tag: String): String? =
        Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1)

    private fun findBlocks(xml: String, tag: String): List<String> =
        Regex("<$tag>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            .map { it.groupValues[1] }.toList()

    /** Primer hijo directo `<tag>valor</tag>` (el primero que aparezca). */
    private fun firstChild(xml: String, tag: String): String? =
        Regex("<$tag>([^<]*)</$tag>").find(xml)?.groupValues?.get(1)?.trim()
}
