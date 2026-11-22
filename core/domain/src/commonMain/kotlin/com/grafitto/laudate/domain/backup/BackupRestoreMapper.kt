package com.grafitto.laudate.domain.backup

import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.music.Accidental
import com.grafitto.laudate.domain.model.music.DomainChord
import com.grafitto.laudate.domain.model.music.PitchClass
import com.grafitto.laudate.domain.model.song.ChordAnchor
import com.grafitto.laudate.domain.model.song.LineType
import com.grafitto.laudate.domain.model.song.SectionType
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongLine
import com.grafitto.laudate.domain.model.song.SongSection
import com.grafitto.laudate.domain.parser.ChordProParser

/**
 * Mapeo inverso de [BackupSerializer]: reconstruye modelos de Dominio desde el payload
 * de un bundle `.canticora` (copia de seguridad **o** cancionero a importar).
 *
 * Es Kotlin puro y no depende de Android, así que sirve tanto para restaurar un respaldo
 * como para incorporar el cancionero digitalizado.
 */

/** Reconstruye todas las canciones del bundle, con sus secciones ya ordenadas. */
fun BackupPayload.toDomainSongs(): List<Song> = songs.map { it.toDomain() }

/** Reconstruye todas las categorías del bundle preservando el `kind` declarado. */
fun BackupPayload.toDomainCategories(): List<LiturgicalCategory> = categories.map { it.toDomain() }

fun BackupCategory.toDomain(): LiturgicalCategory = LiturgicalCategory(
    id = id,
    name = name,
    description = description,
    displayOrder = displayOrder,
    isSystemStandard = isSystemStandard,
    // La naturaleza declarada en el bundle manda: nunca se reclasifica por el nombre.
    kind = LiturgicalCategoryKind.fromName(kind),
    createdAt = createdAt
)

fun BackupSong.toDomain(): Song = Song(
    id = id,
    title = title,
    originalKey = originalKey,
    // El número es independiente del título: no se embebe ni se deduplica.
    number = number?.takeIf { it.isNotBlank() },
    author = author,
    rhythm = rhythm,
    rhythmPatternId = rhythmPatternId,
    tempoBpm = tempoBpm,
    tempoMarking = tempoMarking,
    defaultLiturgicalMoments = defaultLiturgicalMoments,
    defaultLiturgicalTimes = defaultLiturgicalTimes,
    isFavorite = isFavorite,
    capo = capo,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sections = sections
        .sortedBy { it.sequenceOrder }
        .mapIndexed { index, section -> section.toDomain(sequenceOrder = index) }
)

fun BackupSection.toDomain(sequenceOrder: Int): SongSection = SongSection(
    id = id,
    songId = songId,
    sectionType = runCatching { SectionType.valueOf(sectionType.uppercase()) }
        .getOrDefault(SectionType.VERSE),
    sequenceOrder = sequenceOrder,
    headerTitle = headerTitle,
    rawTextContent = rawTextContent,
    label = label,
    voice = voice,
    isRepeated = isRepeated,
    repeatCount = repeatCount,
    rhythmPatternName = rhythmPatternName,
    lines = resolveLines()
)

/**
 * Resuelve las líneas de la sección a partir del bundle.
 *
 * `BackupLine` no tiene campo de tipo ni de voz: los comentarios y las indicaciones de voz
 * (`{comment-todos: ...}`, `[TODOS]`) viajan dentro de `rawTextContent`. Por eso:
 *  - si el bundle trae las líneas ya resueltas, se usan tal cual (reconociendo directivas de comentario);
 *  - si no las trae, se reconstruyen desde el contenido ChordPro, igual que la recarga desde Room.
 */
private fun BackupSection.resolveLines(): List<SongLine> {
    if (lines.isNotEmpty()) {
        return lines.sortedBy { it.lineIndex }.map { it.toDomain(sectionId = id) }
    }
    return reconstructLinesFromChordPro(rawTextContent, sectionId = id)
}

/** Reconstruye las líneas de una sección a partir de su contenido ChordPro original. */
private fun reconstructLinesFromChordPro(rawTextContent: String, sectionId: String): List<SongLine> =
    rawTextContent.lines().mapIndexed { lineIdx, lineText ->
        parseCommentLine(lineText, lineIdx) ?: run {
            // La letra ya llega limpia de `parseLineWithChords`: no re-limpiar con regex (dejaría
            // espacios colgantes en las filas de solo acordes y desalinearía los acordes).
            val (cleanLyrics, anchors) = ChordProParser.parseLineWithChords(lineText, sectionId, lineIdx)
            SongLine(
                lineIndex = lineIdx,
                lyrics = cleanLyrics,
                chords = anchors,
                type = if (cleanLyrics.isBlank() && anchors.isNotEmpty()) {
                    LineType.CHORD_ONLY
                } else {
                    LineType.LYRICS
                }
            )
        }
    }

/** Devuelve una línea de comentario si el texto es una directiva `{comment-voz: ...}`. */
private fun parseCommentLine(text: String, lineIndex: Int): SongLine? {
    val trimmed = text.trim()
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null
    val directive = ChordProParser.parseDirective(trimmed)
    if (directive.tag !in COMMENT_TAGS) return null
    return SongLine(
        lineIndex = lineIndex,
        lyrics = directive.value,
        type = LineType.COMMENT,
        comment = directive.value,
        voice = directive.voice
    )
}

private val COMMENT_TAGS = setOf("comment", "c", "comment_italic", "ci", "comment_box", "cb")

fun BackupLine.toDomain(sectionId: String): SongLine {
    // Una línea que llega como directiva de comentario se conserva como tal (no como letra).
    parseCommentLine(lyrics, lineIndex)?.let { return it }
    return SongLine(
        lineIndex = lineIndex,
        lyrics = lyrics,
        chords = chords.map { it.toDomain(sectionId = sectionId) },
        // Fila sin letra pero con acordes = bloque solo-acordes (intro/riff/outro).
        type = if (lyrics.isBlank() && chords.isNotEmpty()) LineType.CHORD_ONLY else LineType.LYRICS
    )
}

fun BackupAnchor.toDomain(sectionId: String): ChordAnchor = ChordAnchor(
    id = id,
    sectionId = sectionId,
    lineIndex = lineIndex,
    characterIndex = characterIndex,
    chord = DomainChord(
        rootNote = PitchClass.parse(chordRoot) ?: PitchClass.C,
        rootAccidental = chordRoot.toAccidental(),
        quality = chordQuality,
        bassNote = chordBass?.let { PitchClass.parse(it) },
        rawChordText = rawChordText
    ),
    rawChordText = rawChordText
)

private fun String.toAccidental(): Accidental = when {
    endsWith("#") -> Accidental.SHARP
    length > 1 && endsWith("b") -> Accidental.FLAT
    else -> Accidental.NATURAL
}
