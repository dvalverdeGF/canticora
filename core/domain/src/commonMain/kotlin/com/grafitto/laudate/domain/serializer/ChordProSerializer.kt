package com.grafitto.laudate.domain.serializer

import com.grafitto.laudate.domain.model.song.LineType
import com.grafitto.laudate.domain.model.song.SectionType
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongSection

/**
 * Serializador de Dominio: convierte un modelo [Song] a texto ChordPro.
 *
 * Es la operación inversa de [com.grafitto.laudate.domain.parser.ChordProParser].
 * No depende de Android y es determinista (mismo `Song` → mismo texto).
 */
object ChordProSerializer {

    /** Serializa una canción completa a texto ChordPro legible. */
    fun serialize(song: Song): String {
        val sb = StringBuilder()

        sb.appendLine("{title: ${song.title}}")
        sb.appendLine("{key: ${song.originalKey}}")
        song.number?.takeIf { it.isNotBlank() }?.let { sb.appendLine("{number: $it}") }
        song.author?.takeIf { it.isNotBlank() }?.let { sb.appendLine("{artist: $it}") }
        song.rhythm?.takeIf { it.isNotBlank() }?.let { sb.appendLine("{time: $it}") }
        song.tempoBpm?.let { bpm ->
            val suffix = song.tempoMarking?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
            sb.appendLine("{tempo: $bpm$suffix}")
        }
        if (song.capo > 0) sb.appendLine("{capo: ${song.capo}}")
        song.notes?.takeIf { it.isNotBlank() }?.lines()?.forEach { line ->
            sb.appendLine("{comment: $line}")
        }

        song.sections.forEach { section ->
            sb.appendLine()
            appendSection(sb, section)
        }

        return sb.toString().trimEnd() + "\n"
    }

    private fun appendSection(sb: StringBuilder, section: SongSection) {
        val voiceSuffix = section.voice?.takeIf { it.isNotBlank() }
            ?.let { "-${it.lowercase().replace(" ", "_")}" }
            .orEmpty()
        val labelSuffix = section.label?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()

        // Entorno de repetición (bises) alrededor de la sección.
        if (section.isRepeated) {
            section.repeatCount?.let { sb.appendLine("{repeat: $it}") } ?: sb.appendLine("{repeat}")
        }

        when (section.sectionType) {
            SectionType.CHORUS -> sb.appendLine("{start_of_chorus$voiceSuffix$labelSuffix}")
            SectionType.BRIDGE -> sb.appendLine("{start_of_bridge$voiceSuffix$labelSuffix}")
            SectionType.INTRO -> sb.appendLine("{start_of_intro$voiceSuffix$labelSuffix}")
            SectionType.OUTRO -> sb.appendLine("{start_of_outro$voiceSuffix$labelSuffix}")
            SectionType.INTERLUDE -> sb.appendLine("{start_of_interlude$voiceSuffix$labelSuffix}")
            SectionType.ACCLAMATION -> sb.appendLine("{start_of_chorus$voiceSuffix$labelSuffix}")
            SectionType.PRE_CHORUS -> sb.appendLine("{start_of_verse$voiceSuffix$labelSuffix}")
            SectionType.VERSE -> sb.appendLine("{start_of_verse$voiceSuffix$labelSuffix}")
        }

        // Cambio de patrón rítmico de esta sección (`{x_rhythm: Rock}`). Va DENTRO de la sección,
        // tras su `{start_of_*}`: así al reparsear se asigna a ESTA sección y no a la anterior.
        section.rhythmPatternName?.takeIf { it.isNotBlank() }?.let {
            sb.appendLine("{x_rhythm: $it}")
        }

        section.lines.forEach { line ->
            when (line.type) {
                LineType.COMMENT -> {
                    val suffix = line.voice?.takeIf { it.isNotBlank() }?.let { "-$it" }.orEmpty()
                    sb.appendLine("{comment$suffix: ${line.comment ?: line.lyrics}}")
                }
                // Las filas solo-acordes (intro/riff/outro) y la letra se reconstruyen igual:
                // `renderLine` inserta cada `[Acorde]` en su posición de carácter exacta.
                LineType.LYRICS, LineType.CHORD_ONLY ->
                    sb.appendLine(renderLine(line.lyrics, line.chords.sortedBy { it.characterIndex }))
                else -> sb.appendLine(renderLine(line.lyrics, line.chords.sortedBy { it.characterIndex }))
            }
        }

        val endTag = when (section.sectionType) {
            SectionType.CHORUS, SectionType.ACCLAMATION -> "end_of_chorus"
            SectionType.BRIDGE -> "end_of_bridge"
            SectionType.INTRO -> "end_of_intro"
            SectionType.OUTRO -> "end_of_outro"
            SectionType.INTERLUDE -> "end_of_interlude"
            SectionType.VERSE, SectionType.PRE_CHORUS -> "end_of_verse"
        }
        sb.appendLine("{$endTag}")
        if (section.isRepeated) sb.appendLine("{repeat_end}")
    }

    /**
     * Reconstruye una línea con sus etiquetas de acorde [Acorde] insertadas en la posición exacta.
     * Es la inversa exacta de `ChordProParser.parseLineWithChords`.
     */
    fun renderLine(
        lyrics: String,
        chords: List<com.grafitto.laudate.domain.model.song.ChordAnchor>
    ): String {
        if (chords.isEmpty()) return lyrics
        val sb = StringBuilder()
        var lastIdx = 0
        for (anchor in chords) {
            val idx = anchor.characterIndex.coerceIn(0, lyrics.length)
            if (idx < lastIdx) continue
            sb.append(lyrics, lastIdx, idx)
            sb.append('[').append(anchor.rawChordText).append(']')
            lastIdx = idx
        }
        if (lastIdx < lyrics.length) {
            sb.append(lyrics, lastIdx, lyrics.length)
        }
        return sb.toString()
    }
}
