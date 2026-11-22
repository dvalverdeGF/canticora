package com.grafitto.laudate.domain.parser

import com.grafitto.laudate.domain.model.music.TempoMarking
import com.grafitto.laudate.domain.model.song.ChordAnchor
import com.grafitto.laudate.domain.model.song.LineType
import com.grafitto.laudate.domain.model.song.SectionType
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongLine
import com.grafitto.laudate.domain.model.song.SongSection
import com.grafitto.laudate.domain.platform.newId

/**
 * Parser de texto con sintaxis ChordPro a modelos estructurados de Dominio (AST).
 *
 * Sintaxis soportada:
 *  - Acordes inline: `[Do]Alabaré` / `[C]Alabaré`.
 *  - Directivas simples: `{title: ...}`, `{key: G}`, `{tempo: 92 Andante}`, `{capo: 2}`.
 *  - Secciones: `{start_of_verse}` / `{end_of_verse}`, `{soc}` / `{eoc}`, `{sob}` / `{eob}`,
 *    `{start_of_intro}`, `{start_of_outro}`, `{start_of_interlude}`.
 *  - Voz/rol de sección: `{start_of_verse-soprano}` (sufijo tras guion).
 *  - Etiqueta de sección: `{start_of_verse Verse 1}` o `{start_of_verse label="Verse 1"}`.
 *  - Comentarios por voz: `{comment-alto: Very softly!}`.
 *  - Encabezados de sección entre corchetes: `[Estrofa 1]`, `[Coro]`.
 *  - Cambio de patrón rítmico: `{x_rhythm: Rock}` aplica el patrón nombrado a la **siguiente**
 *    sección; permite ritmos distintos por sección (verso/coro/intro).
 */
object ChordProParser {

    private val CHORD_TAG_REGEX = Regex("\\[([^\\]]+)\\]")
    private val LABEL_ATTR_REGEX = Regex("""label\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE)

    /** Directivas que representan un comentario renderizable. */
    private val COMMENT_TAGS = setOf("comment", "c", "comment_italic", "ci", "comment_box", "cb")

    fun parse(
        chordProContent: String,
        songId: String = newId(),
        fallbackTitle: String = "Sin título",
        fallbackKey: String = "C"
    ): Song {
        var title = fallbackTitle
        var originalKey = fallbackKey
        var number: String? = null
        var author: String? = null
        var rhythm: String? = null
        var capo = 0
        var notes: String? = null
        var tempoBpm: Int? = null
        var tempoMarking: String? = null
        var repeatedNext = false
        var repeatCountNext: Int? = null

        val sections = mutableListOf<SongSection>()
        var currentSectionType = SectionType.VERSE
        var currentHeaderTitle: String? = null
        var currentLabel: String? = null
        var currentVoice: String? = null
        val currentLinesRaw = mutableListOf<String>()
        var sectionOrder = 0
        // Patrón rítmico pendiente declarado con `{x_rhythm: ...}`: se asigna a la próxima sección
        // que se abra o que se cierre (si la directiva va justo dentro de una sección abierta).
        var pendingRhythmName: String? = null

        fun commitCurrentSection() {
            // Una sección vacía se descarta… salvo que declare un patrón rítmico propio
            // (`{x_rhythm: ...}`), que es contenido intencional y no debe perderse.
            if (currentLinesRaw.isEmpty() && currentLabel == null && currentVoice == null &&
                pendingRhythmName == null
            ) return
            val sectionId = newId()
            val parsedLines = mutableListOf<SongLine>()

            currentLinesRaw.forEachIndexed { lineIdx, rawLine ->
                val trimmedLine = rawLine.trim()
                val commentDirective = if (trimmedLine.startsWith("{") && trimmedLine.endsWith("}")) {
                    parseDirective(trimmedLine)
                } else {
                    null
                }

                if (commentDirective != null && commentDirective.tag in COMMENT_TAGS) {
                    // Los comentarios son contenido renderizable: se conservan como línea propia,
                    // guardando el texto y la voz destinataria (`{comment-alto: ...}` → voice = "alto").
                    parsedLines.add(
                        SongLine(
                            lineIndex = lineIdx,
                            lyrics = commentDirective.value,
                            type = LineType.COMMENT,
                            comment = commentDirective.value,
                            voice = commentDirective.voice
                        )
                    )
                } else {
                    val (cleanLyrics, anchors) = parseLineWithChords(rawLine, sectionId, lineIdx)
                    parsedLines.add(
                        SongLine(
                            lineIndex = lineIdx,
                            lyrics = cleanLyrics,
                            chords = anchors,
                            // Una fila sin letra pero con acordes (intro, riff, outro) es un
                            // bloque solo-acordes: se marca para que el visor y el PDF no
                            // reserven un renglón de letra vacío debajo.
                            type = if (cleanLyrics.isBlank() && anchors.isNotEmpty()) {
                                LineType.CHORD_ONLY
                            } else {
                                LineType.LYRICS
                            }
                        )
                    )
                }
            }

            sections.add(
                SongSection(
                    id = sectionId,
                    songId = songId,
                    sectionType = currentSectionType,
                    sequenceOrder = sectionOrder++,
                    headerTitle = currentHeaderTitle,
                    rawTextContent = currentLinesRaw.joinToString("\n"),
                    label = currentLabel,
                    voice = currentVoice,
                    isRepeated = repeatedNext || repeatCountNext != null,
                    repeatCount = repeatCountNext,
                    rhythmPatternName = pendingRhythmName,
                    lines = parsedLines
                )
            )
            currentLinesRaw.clear()
            currentHeaderTitle = null
            currentLabel = null
            currentVoice = null
            repeatedNext = false
            repeatCountNext = null
            pendingRhythmName = null
        }

        chordProContent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val directive = parseDirective(trimmed)

                when (directive.tag) {
                    "title", "t" -> title = directive.value
                    "key", "k" -> originalKey = directive.value
                    "number", "n" -> number = directive.value.trim().ifEmpty { null }
                    "artist", "author", "a" -> author = directive.value
                    "time", "rhythm" -> rhythm = directive.value
                    "tempo", "bpm" -> {
                        val parsed = parseTempo(directive.value)
                        tempoBpm = parsed.first
                        tempoMarking = parsed.second
                    }
                    "capo" -> capo = directive.value.toIntOrNull() ?: 0

                    "start_of_chorus", "soc" -> {
                        commitCurrentSection()
                        currentSectionType = SectionType.CHORUS
                        currentVoice = directive.voice
                        currentLabel = directive.label.ifEmpty { null }
                        currentHeaderTitle = directive.label.ifEmpty { "Coro" }
                    }
                    "end_of_chorus", "eoc" -> {
                        commitCurrentSection()
                        currentSectionType = SectionType.VERSE
                    }
                    "start_of_verse", "sov" -> {
                        commitCurrentSection()
                        currentSectionType = SectionType.VERSE
                        currentVoice = directive.voice
                        currentLabel = directive.label.ifEmpty { null }
                        currentHeaderTitle = directive.label.ifEmpty { null }
                    }
                    "end_of_verse", "eov" -> {
                        commitCurrentSection()
                        currentSectionType = SectionType.VERSE
                    }
                    "start_of_bridge", "sob" -> {
                        commitCurrentSection()
                        currentSectionType = SectionType.BRIDGE
                        currentVoice = directive.voice
                        currentLabel = directive.label.ifEmpty { null }
                        currentHeaderTitle = directive.label.ifEmpty { "Puente" }
                    }
                    "end_of_bridge", "eob" -> {
                        commitCurrentSection()
                        currentSectionType = SectionType.VERSE
                    }
                    "start_of_intro" -> {
                        commitCurrentSection()
                        currentSectionType = SectionType.INTRO
                        currentVoice = directive.voice
                        currentLabel = directive.label.ifEmpty { null }
                        currentHeaderTitle = directive.label.ifEmpty { "Intro" }
                    }
                    "end_of_intro" -> {
                        commitCurrentSection()
                        currentSectionType = SectionType.VERSE
                    }
                    "start_of_outro" -> {
                        commitCurrentSection()
                        currentSectionType = SectionType.OUTRO
                        currentVoice = directive.voice
                        currentLabel = directive.label.ifEmpty { null }
                        currentHeaderTitle = directive.label.ifEmpty { "Final" }
                    }
                    "end_of_outro" -> {
                        commitCurrentSection()
                        currentSectionType = SectionType.VERSE
                    }
                    "start_of_interlude" -> {
                        commitCurrentSection()
                        currentSectionType = SectionType.INTERLUDE
                        currentVoice = directive.voice
                        currentLabel = directive.label.ifEmpty { null }
                        currentHeaderTitle = directive.label.ifEmpty { "Interludio" }
                    }
                    "end_of_interlude" -> {
                        commitCurrentSection()
                        currentSectionType = SectionType.VERSE
                    }

                    "repeat", "x" -> {
                        // `{repeat}` abre un entorno de repetición (se cierra con `{repeat_end}`);
                        // `{repeat: N}` o `{x: N}` indican además el número de repeticiones. Si aparecen
                        // justo antes de una sección, el conteo se le asigna a esa sección.
                        repeatedNext = true
                        repeatCountNext = directive.value.trim().toIntOrNull()
                            ?.takeIf { it > 0 }
                    }

                    "repeat_end" -> {
                        // Cierra el entorno de repetición marcando la última sección cerrada.
                        // Es idempotente: si la sección ya quedó marcada por un `{repeat}` previo, no cambia nada.
                        sections.lastOrNull()?.takeIf { !it.isRepeated }?.let { last ->
                            sections[sections.size - 1] = last.copy(isRepeated = true)
                        }
                    }

                    "comment", "c", "notes", "note" -> {
                        if (directive.voice != null) {
                            // Comentario dirigido a una voz concreta: se conserva como línea de anotación.
                            currentLinesRaw.add("{comment-${directive.voice}: ${directive.value}}")
                        } else if (currentLinesRaw.isEmpty() && currentHeaderTitle == null) {
                            // Comentario de cabecera, antes de cualquier sección: se trata como nota del canto.
                            notes = if (notes == null) directive.value else "$notes\n${directive.value}"
                        } else {
                            // Comentario dentro de una sección: se conserva como línea renderizable.
                            // Se normaliza para garantizar un round-trip estable.
                            currentLinesRaw.add(if (directive.value.isBlank()) "{comment: }" else "{comment: ${directive.value}}")
                        }
                    }

                    // Cambio de patrón rítmico por sección: `{x_rhythm: Rock}` asigna el patrón
                    // nombrado a la sección que se está cerrando (colócalo dentro de la sección,
                    // tras su `{start_of_*}`, para que afecte a esa sección y a las siguientes).
                    "x_rhythm", "x_rhythm_pattern", "rhythm_pattern" -> {
                        pendingRhythmName = directive.value.trim().ifEmpty { null }
                    }

                    else -> {
                        // Directiva no reconocida: se ignora sin romper el parseo.
                    }
                }
            } else if (trimmed.startsWith("[") && trimmed.endsWith("]") && isSectionHeaderLine(trimmed)) {
                commitCurrentSection()
                val headerName = trimmed.substring(1, trimmed.length - 1)
                currentSectionType = inferSectionType(headerName)
                currentHeaderTitle = headerName
                currentLabel = headerName
            } else if (trimmed.isNotEmpty()) {
                currentLinesRaw.add(line.trim())
            }
        }

        commitCurrentSection()

        return Song(
            id = songId,
            title = title,
            originalKey = originalKey,
            number = number,
            author = author,
            rhythm = rhythm,
            tempoBpm = tempoBpm,
            tempoMarking = tempoMarking,
            capo = capo,
            notes = notes,
            sections = sections
        )
    }

    /**
     * Directiva ChordPro descompuesta en sus partes.
     * Permite `{tag-voz}`, `{tag label}`, `{tag label="..."}`, `{tag: valor}`.
     */
    data class Directive(
        val tag: String,
        val value: String,
        val voice: String? = null,
        val label: String = ""
    )

    /**
     * Descompone una directiva ChordPro en `tag`, `voice`, `label` y `value`.
     * Acepta las variantes de etiqueta y voz pedidas:
     * `{start_of_verse-soprano}`, `{start_of_verse Verse 1}`, `{start_of_verse label="Verse 1"}`.
     */
    fun parseDirective(raw: String): Directive {
        val inner = raw.trim().removePrefix("{").removeSuffix("}").trim()
        val colonIdx = inner.indexOf(':')
        val namePart = (if (colonIdx != -1) inner.substring(0, colonIdx) else inner).trim()
        val rawValue = if (colonIdx != -1) inner.substring(colonIdx + 1).trim() else ""

        // El selector de voz (`{start_of_verse-soprano}`) solo puede aparecer en el token del tag:
        // nunca debe interpretarse un guion que forme parte de una etiqueta libre
        // (`{start_of_verse Pre-estribillo}` → label = "Pre-estribillo", no voz).
        val tagToken = namePart.substringBefore(' ').trim()
        val restOfName = namePart.substringAfter(' ', "").trim()
        val dashIdx = tagToken.indexOf('-')
        val tag = (if (dashIdx != -1) tagToken.substring(0, dashIdx) else tagToken).trim().lowercase()
        val voice = if (dashIdx != -1) tagToken.substring(dashIdx + 1).trim().ifEmpty { null } else null

        // Etiqueta: puede venir como `label="..."` o como texto libre tras el tag.
        val labelAttr = LABEL_ATTR_REGEX.find(namePart)?.groupValues?.getOrNull(1)?.trim()
        val freeLabel = restOfName.removeSurrounding("\"")
        val label = when {
            !labelAttr.isNullOrBlank() -> labelAttr
            colonIdx != -1 -> rawValue
            freeLabel.isNotBlank() -> freeLabel
            else -> ""
        }

        val value = if (colonIdx != -1) rawValue else ""

        return Directive(tag = tag, value = value, voice = voice, label = label)
    }

    /** Interpreta el valor de `{tempo:}` aceptando `92`, `92 Andante` o `Andante`. */
    private fun parseTempo(value: String): Pair<Int?, String?> {
        val bpm = value.substringBefore(' ').trim().toIntOrNull()
        return if (bpm != null) {
            val explicit = value.dropWhile { !it.isLetter() }.trim().ifEmpty { null }
            bpm to (explicit ?: TempoMarking.fromBpm(bpm).italianName)
        } else {
            val marking = TempoMarking.fromName(value)
            if (marking != null) marking.suggestedBpm to marking.italianName else null to null
        }
    }

    /**
     * Indica si un texto entre corchetes (`[Texto]`) es una **etiqueta de sección** y no un acorde.
     *
     * Es clave para que al transponer no se confunda `[Estrofa]` con un acorde de Mi
     * (`[E]` + calidad «strofa»), ni `[Asamblea]` con un La. Se reconocen las convenciones
     * del cancionero litúrgico hispano (partes, voces y secciones instrumentales).
     *
     * OJO: no se usa una heurística de mayúsculas, porque en cifrado latino `[MI]`, `[SOL]`
     * o `[LA]` son acordes reales. La defensa principal contra falsos acordes es que
     * [ChordTextParser] solo acepta calidades conocidas; aquí basta con la lista de palabras.
     */
    fun isSectionHeader(tag: String): Boolean {
        val lower = tag.trim().lowercase()
        // Una etiqueta con espacios («Coro 1», «Estrofa del pueblo») nunca es un acorde.
        if (lower.contains(" ")) return true
        return SECTION_HEADER_WORDS.any { lower.contains(it) }
    }

    /**
     * Indica si una línea completa es una **etiqueta de sección de una sola pieza** (`[Coro]`,
     * `[Estrofa 1]`), no una fila de acordes.
     *
     * Es la guarda que evita el bug histórico de las intros: una fila de solo acordes como
     * `[Am] [G] [F] [E]` empieza por `[`, termina por `]` y contiene un espacio, así que
     * [isSectionHeader] la tomaba por un encabezado y `substring(1, length-1)` **se comía el
     * primer corchete de apertura**, dejando `Am] [G] [F] [E`. Aquí se exige un **único** grupo
     * `[...]` que abarque toda la línea: si hay más de uno, es una fila de acordes.
     */
    fun isSectionHeaderLine(trimmedLine: String): Boolean =
        BRACKET_ONLY_REGEX.matches(trimmedLine) && isSectionHeader(trimmedLine.substring(1, trimmedLine.length - 1))

    /** Un único grupo `[...]` que abarca toda la línea, sin `[`/`]` internos. */
    private val BRACKET_ONLY_REGEX = Regex("\\[[^\\[\\]]+\\]")

    /** Raíces de las etiquetas de sección admitidas (en minúsculas, sin tildes). */
    private val SECTION_HEADER_WORDS = listOf(
        "verso", "verse", "estrofa", "copla",
        "coro", "chorus", "estribillo",
        "intro", "introducc",
        "puente", "bridge",
        "outro", "final", "coda", "cierre",
        "interludio", "interlude", "instrumental", "solo",
        "aclamaci", "aleluya",
        "asamblea", "todos", "pueblo", "respuesta",
        "sopran", "contralt", "tenor", "baritono", "bajo"
    )

    private fun inferSectionType(header: String): SectionType {
        val lower = header.lowercase()
        return when {
            lower.contains("coro") || lower.contains("chorus") || lower.contains("estribillo") -> SectionType.CHORUS
            lower.contains("intro") -> SectionType.INTRO
            lower.contains("puente") || lower.contains("bridge") -> SectionType.BRIDGE
            lower.contains("outro") || lower.contains("final") -> SectionType.OUTRO
            lower.contains("interludio") || lower.contains("interlude") -> SectionType.INTERLUDE
            lower.contains("aclamaci") -> SectionType.ACCLAMATION
            else -> SectionType.VERSE
        }
    }

    /**
     * Parsea una línea en formato ChordPro extrayendo la letra limpia y los ChordAnchors con su characterIndex exacto.
     */
    fun parseLineWithChords(
        rawLine: String,
        sectionId: String,
        lineIndex: Int
    ): Pair<String, List<ChordAnchor>> {
        val cleanLyrics = StringBuilder()
        val anchors = mutableListOf<ChordAnchor>()

        var lastIdx = 0
        val matches = CHORD_TAG_REGEX.findAll(rawLine)

        for (match in matches) {
            val textBefore = rawLine.substring(lastIdx, match.range.first)
            cleanLyrics.append(textBefore)
            val charIndex = cleanLyrics.length
            val rawChord = match.groupValues[1]

            val parsedChord = ChordTextParser.parse(rawChord).getOrNull()
            if (parsedChord != null) {
                anchors.add(
                    ChordAnchor(
                        id = newId(),
                        sectionId = sectionId,
                        lineIndex = lineIndex,
                        characterIndex = charIndex,
                        chord = parsedChord,
                        rawChordText = rawChord
                    )
                )
            }

            lastIdx = match.range.last + 1
        }

        cleanLyrics.append(rawLine.substring(lastIdx))
        return cleanLyrics.toString() to anchors
    }
}
