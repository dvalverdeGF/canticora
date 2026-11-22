package com.grafitto.laudate.domain.harmonic

import com.grafitto.laudate.domain.model.music.Accidental
import com.grafitto.laudate.domain.model.music.DomainChord
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.music.PitchClass
import com.grafitto.laudate.domain.model.song.ChordAnchor
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongLine
import com.grafitto.laudate.domain.model.song.SongSection
import com.grafitto.laudate.domain.parser.ChordProParser
import com.grafitto.laudate.domain.parser.ChordTextParser

/**
 * Motor funcional puro de transposición armónica.
 * Garantiza la invariante: sólo se modifican los acordes, mientras que la letra,
 * los títulos de sección y las posiciones/offsets de caracteres permanecen 100% idénticos.
 */
object HarmonicTransposer {

    /**
     * Transpone un acorde individual aplicando las reglas de enarmonía según la tonalidad destino.
     */
    fun transposeChord(
        chord: DomainChord,
        semitones: Int,
        targetKey: PitchClass
    ): DomainChord {
        val newRoot = chord.rootNote.transpose(semitones)
        val newBass = chord.bassNote?.transpose(semitones)

        val prefersFlats = isFlatKey(targetKey)

        return chord.copy(
            rootNote = newRoot,
            rootAccidental = calculateAccidental(newRoot, prefersFlats),
            bassNote = newBass,
            bassAccidental = newBass?.let { calculateAccidental(it, prefersFlats) }
        )
    }

    /**
     * Calcula la posición de acordes a visualizar teniendo en cuenta la cejilla (Capo).
     * Fórmula: visualKey = targetKey - capoPosition
     */
    fun calculateVisualCapoChord(
        chord: DomainChord,
        targetKey: PitchClass,
        capoPosition: Int
    ): DomainChord {
        val effectiveShift = -capoPosition
        val visualTargetKey = targetKey.transpose(effectiveShift)
        return transposeChord(chord, effectiveShift, visualTargetKey)
    }

    /**
     * Calcula la posición de capo óptima entre targetKey y una digitación deseada (visualKey).
     */
    fun calculateCapoPosition(targetKey: PitchClass, visualKey: PitchClass): Int {
        val diff = (targetKey.chromaticIndex - visualKey.chromaticIndex) % 12
        return if (diff < 0) diff + 12 else diff
    }

    /**
     * Transpone una canción completa preservando íntegramente la letra y las estructuras.
     */
    fun transposeSong(
        song: Song,
        semitones: Int,
        targetKeyPitch: PitchClass
    ): Song {
        val newSections = song.sections.map { section ->
            val newLines = section.lines.map { line ->
                val newChords = line.chords.map { anchor ->
                    anchor.copy(
                        chord = transposeChord(anchor.chord, semitones, targetKeyPitch)
                    )
                }
                line.copy(chords = newChords)
            }
            section.copy(lines = newLines)
        }

        return song.copy(
            originalKey = targetKeyPitch.preferredEnglishName,
            sections = newSections
        )
    }

    /**
     * Determina si una tonalidad prefiere bemoles según el Círculo de Quintas.
     * Tonalidades con bemoles: F (1b), Bb (2b), Eb (3b), Ab (4b), Db (5b), Gb (6b).
     */
    fun isFlatKey(key: PitchClass): Boolean {
        return when (key) {
            PitchClass.F,
            PitchClass.A_SHARP, // Bb
            PitchClass.D_SHARP, // Eb
            PitchClass.G_SHARP, // Ab
            PitchClass.C_SHARP, // Db
            PitchClass.F_SHARP  // Gb
            -> true
            else -> false
        }
    }

    private fun calculateAccidental(pitchClass: PitchClass, prefersFlats: Boolean): Accidental {
        return when (pitchClass) {
            PitchClass.C_SHARP,
            PitchClass.D_SHARP,
            PitchClass.F_SHARP,
            PitchClass.G_SHARP,
            PitchClass.A_SHARP -> {
                if (prefersFlats) Accidental.FLAT else Accidental.SHARP
            }
            else -> Accidental.NATURAL
        }
    }

    /**
     * Transpone el texto en formato ChordPro actualizando todas las etiquetas de acordes [Acorde]
     * y directivas de tonalidad {key: ...} respetando los semitonos indicados.
     */
    fun transposeChordProContent(
        content: String,
        semitones: Int,
        targetKeyPitch: PitchClass
    ): String {
        if (semitones == 0) return content

        val tagRegex = Regex("\\[([^\\]]+)\\]")
        val keyDirectiveRegex = Regex("^\\s*\\{(key|k)\\s*:\\s*([^}]+)\\}", RegexOption.IGNORE_CASE)

        val lines = content.lines()
        val resultLines = lines.map { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val match = keyDirectiveRegex.matchEntire(trimmed)
                if (match != null) {
                    val tag = match.groupValues[1]
                    val isLatinKey = match.groupValues[2].trim().let {
                        it.startsWith("Do", ignoreCase = true) || it.startsWith("Re", ignoreCase = true) ||
                        it.startsWith("Mi", ignoreCase = true) || it.startsWith("Fa", ignoreCase = true) ||
                        it.startsWith("Sol", ignoreCase = true) || it.startsWith("La", ignoreCase = true) ||
                        it.startsWith("Si", ignoreCase = true)
                    }
                    val formattedKey = if (isLatinKey) targetKeyPitch.preferredLatinName else targetKeyPitch.preferredEnglishName
                    "{$tag: $formattedKey}"
                } else {
                    line
                }
            } else {
                tagRegex.replace(line) { matchResult ->
                    val rawTag = matchResult.groupValues[1]
                    if (ChordProParser.isSectionHeader(rawTag)) {
                        matchResult.value
                    } else {
                        val parsed = ChordTextParser.parse(rawTag).getOrNull()
                        if (parsed != null) {
                            val transposed = transposeChord(parsed, semitones, targetKeyPitch)
                            val isLatin = rawTag.startsWith("Do", ignoreCase = true) ||
                                          rawTag.startsWith("Re", ignoreCase = true) ||
                                          rawTag.startsWith("Mi", ignoreCase = true) ||
                                          rawTag.startsWith("Fa", ignoreCase = true) ||
                                          rawTag.startsWith("Sol", ignoreCase = true) ||
                                          rawTag.startsWith("La", ignoreCase = true) ||
                                          rawTag.startsWith("Si", ignoreCase = true)
                            val notation = if (isLatin) NotationSystem.LATIN else NotationSystem.ENGLISH
                            "[${transposed.toFormattedString(notation, targetKeyPitch)}]"
                        } else {
                            matchResult.value
                        }
                    }
                }
            }
        }
        return resultLines.joinToString("\n")
    }

    /**
     * Reescribe las etiquetas de acordes (`[Acorde]`) y la directiva `{key: …}` de un texto
     * ChordPro en el sistema de notación pedido, **sin transponer** (0 semitonos).
     *
     * Es lo que permite que, al cambiar entre notación latina y anglosajona, el **texto** del
     * editor se actualice (no solo el visor): `[Do]` ↔ `[C]`. Las etiquetas de sección
     * (`[Estrofa]`, `[Coro]`…) y el resto del texto quedan intactos.
     */
    fun convertChordProNotation(
        content: String,
        system: NotationSystem,
        referenceKey: PitchClass
    ): String {
        val tagRegex = Regex("\\[([^\\]]+)\\]")
        val keyDirectiveRegex = Regex("^\\s*\\{(key|k)\\s*:\\s*([^}]+)\\}", RegexOption.IGNORE_CASE)

        return content.lines().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val match = keyDirectiveRegex.matchEntire(trimmed)
                if (match != null) {
                    val tag = match.groupValues[1]
                    val pitch = PitchClass.parse(match.groupValues[2].trim())
                    if (pitch == null) line else "{$tag: ${formatKey(pitch, system)}}"
                } else {
                    line
                }
            } else {
                tagRegex.replace(line) { matchResult ->
                    val rawTag = matchResult.groupValues[1]
                    if (ChordProParser.isSectionHeader(rawTag)) {
                        matchResult.value
                    } else {
                        val parsed = ChordTextParser.parse(rawTag).getOrNull()
                        if (parsed == null) {
                            matchResult.value
                        } else {
                            "[${parsed.toFormattedString(system, referenceKey)}]"
                        }
                    }
                }
            }
        }
    }

    /** Formatea una tonalidad según el sistema (la NNS no aplica a rótulos de tonalidad). */
    private fun formatKey(pitch: PitchClass, system: NotationSystem): String = when (system) {
        NotationSystem.LATIN -> pitch.preferredLatinName
        else -> pitch.preferredEnglishName
    }
}
