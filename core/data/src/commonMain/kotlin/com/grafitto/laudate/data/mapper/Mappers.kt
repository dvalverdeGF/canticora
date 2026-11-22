package com.grafitto.laudate.data.mapper

import com.grafitto.laudate.data.local.converters.RoomConverters
import com.grafitto.laudate.data.local.entity.CelebrationEntity
import com.grafitto.laudate.data.local.entity.CelebrationItemEntity
import com.grafitto.laudate.data.local.entity.ChordAnchorEntity
import com.grafitto.laudate.data.local.entity.SongEntity
import com.grafitto.laudate.data.local.entity.SongSectionEntity
import com.grafitto.laudate.data.local.relation.CelebrationWithItemsRelation
import com.grafitto.laudate.data.local.relation.SongWithDetailsRelation
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.celebration.CelebrationItem
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

object SongMapper {

    private val converters = RoomConverters()

    fun toDomain(entity: SongEntity): Song {
        return Song(
            id = entity.id,
            title = entity.title,
            originalKey = entity.originalKey,
            number = entity.number,
            author = entity.author,
            rhythm = entity.rhythm,
            rhythmPatternId = entity.rhythmPatternId,
            tempoBpm = entity.tempoBpm,
            tempoMarking = entity.tempoMarking,
            defaultLiturgicalMoments = converters.toListString(entity.defaultLiturgicalMomentsJson),
            defaultLiturgicalTimes = converters.toListString(entity.defaultLiturgicalTimesJson),
            isFavorite = entity.isFavorite,
            capo = entity.capo,
            notes = entity.notes,
            contentHash = entity.contentHash,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            sections = emptyList()
        )
    }

    fun toDomain(relation: SongWithDetailsRelation): Song {
        val baseSong = toDomain(relation.song)

        val sections = relation.sections.sortedBy { it.section.sequenceOrder }.map { sectionRel ->
            val sectionEntity = sectionRel.section
            val sectionType = runCatching { SectionType.valueOf(sectionEntity.sectionType) }.getOrDefault(SectionType.VERSE)

            // Reconstruir SongLines agrupando los anchors por lineIndex
            val anchorsByLine = sectionRel.anchors.groupBy { it.lineIndex }
            val lines = sectionEntity.rawTextContent.lines().mapIndexed { lineIdx, lineText ->
                val trimmedLine = lineText.trim()
                // Los comentarios se reconstruyen desde el texto original (no se almacenan aparte).
                if (trimmedLine.startsWith("{") && trimmedLine.endsWith("}")) {
                    val directive = ChordProParser.parseDirective(trimmedLine)
                    if (directive.tag in setOf("comment", "c", "comment_italic", "ci", "comment_box", "cb")) {
                        return@mapIndexed SongLine(
                            lineIndex = lineIdx,
                            lyrics = directive.value,
                            type = LineType.COMMENT,
                            comment = directive.value,
                            voice = directive.voice
                        )
                    }
                }

                val lineAnchors = anchorsByLine[lineIdx].orEmpty().map { anchorEntity ->
                    ChordAnchor(
                        id = anchorEntity.id,
                        sectionId = anchorEntity.sectionId,
                        lineIndex = anchorEntity.lineIndex,
                        characterIndex = anchorEntity.characterIndex,
                        chord = DomainChord(
                            rootNote = PitchClass.parse(anchorEntity.chordRoot) ?: PitchClass.C,
                            rootAccidental = when {
                                anchorEntity.chordRoot.endsWith("#") -> Accidental.SHARP
                                anchorEntity.chordRoot.endsWith("b") -> Accidental.FLAT
                                else -> Accidental.NATURAL
                            },
                            quality = anchorEntity.chordQuality,
                            bassNote = anchorEntity.chordBass?.let { PitchClass.parse(it) },
                            rawChordText = anchorEntity.rawChordText
                        ),
                        rawChordText = anchorEntity.rawChordText
                    )
                }
                // `parseLineWithChords` ya devuelve la letra limpia (sin `[Acorde]`). NO volver a
                // limpiarla con una regex sobre `lineText` crudo: en una fila de solo acordes
                // (`[Am] [G] [F] [E]`) dejaría espacios colgantes ("  ") en vez de "", y esos
                // espacios fantasma desalineaban los acordes al reimprimir/exportar a PDF.
                val (cleanLyrics, _) = ChordProParser.parseLineWithChords(lineText, sectionEntity.id, lineIdx)
                SongLine(
                    lineIndex = lineIdx,
                    lyrics = cleanLyrics,
                    chords = lineAnchors,
                    // Fila sin letra pero con acordes = bloque solo-acordes (intro/riff/outro).
                    type = if (cleanLyrics.isBlank() && lineAnchors.isNotEmpty()) {
                        LineType.CHORD_ONLY
                    } else {
                        LineType.LYRICS
                    }
                )
            }

            SongSection(
                id = sectionEntity.id,
                songId = sectionEntity.songId,
                sectionType = sectionType,
                sequenceOrder = sectionEntity.sequenceOrder,
                headerTitle = sectionEntity.headerTitle,
                rawTextContent = sectionEntity.rawTextContent,
                label = sectionEntity.label,
                voice = sectionEntity.voice,
                isRepeated = sectionEntity.isRepeated,
                repeatCount = sectionEntity.repeatCount,
                rhythmPatternName = sectionEntity.rhythmPatternName,
                lines = lines
            )
        }

        return baseSong.copy(sections = sections)
    }

    fun toEntity(domain: Song): Triple<SongEntity, List<SongSectionEntity>, List<ChordAnchorEntity>> {
        val songEntity = SongEntity(
            id = domain.id,
            title = domain.title,
            originalKey = domain.originalKey,
            number = domain.number,
            author = domain.author,
            rhythm = domain.rhythm,
            rhythmPatternId = domain.rhythmPatternId,
            tempoBpm = domain.tempoBpm,
            tempoMarking = domain.tempoMarking,
            defaultLiturgicalMomentsJson = converters.fromListString(domain.defaultLiturgicalMoments),
            defaultLiturgicalTimesJson = converters.fromListString(domain.defaultLiturgicalTimes),
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            isFavorite = domain.isFavorite,
            capo = domain.capo,
            notes = domain.notes,
            contentHash = domain.contentHash
        )

        val sectionEntities = mutableListOf<SongSectionEntity>()
        val anchorEntities = mutableListOf<ChordAnchorEntity>()

        domain.sections.forEachIndexed { sIdx, section ->
            sectionEntities.add(
                SongSectionEntity(
                    id = section.id,
                    songId = domain.id,
                    sectionType = section.sectionType.name,
                    sequenceOrder = sIdx,
                    rawTextContent = section.rawTextContent,
                    headerTitle = section.headerTitle,
                    label = section.label,
                    voice = section.voice,
                    isRepeated = section.isRepeated,
                    repeatCount = section.repeatCount,
                    rhythmPatternName = section.rhythmPatternName
                )
            )

            section.lines.forEach { line ->
                line.chords.forEach { anchor ->
                    anchorEntities.add(
                        ChordAnchorEntity(
                            id = anchor.id,
                            sectionId = section.id,
                            lineIndex = line.lineIndex,
                            characterIndex = anchor.characterIndex,
                            chordRoot = anchor.chord.rootNote.preferredEnglishName,
                            chordQuality = anchor.chord.quality,
                            chordBass = anchor.chord.bassNote?.preferredEnglishName,
                            rawChordText = anchor.rawChordText
                        )
                    )
                }
            }
        }

        return Triple(songEntity, sectionEntities, anchorEntities)
    }
}

object CelebrationMapper {
    fun toDomain(relation: CelebrationWithItemsRelation): Celebration {
        val items = relation.items.sortedBy { it.item.displayOrder }.map { itemWithSong ->
            CelebrationItem(
                id = itemWithSong.item.id,
                celebrationId = itemWithSong.item.celebrationId,
                songId = itemWithSong.item.songId,
                liturgicalMoment = itemWithSong.item.liturgicalMoment,
                displayOrder = itemWithSong.item.displayOrder,
                overrideKey = itemWithSong.item.overrideKey,
                overrideCapo = itemWithSong.item.overrideCapo,
                customNotes = itemWithSong.item.customNotes
            )
        }

        return Celebration(
            id = relation.celebration.id,
            name = relation.celebration.name,
            dateTimestamp = relation.celebration.dateTimestamp,
            description = relation.celebration.description,
            location = relation.celebration.location,
            celebrationType = relation.celebration.celebrationType,
            createdAt = relation.celebration.createdAt,
            updatedAt = relation.celebration.updatedAt,
            items = items
        )
    }

    fun toEntity(domain: Celebration): Pair<CelebrationEntity, List<CelebrationItemEntity>> {
        val celebrationEntity = CelebrationEntity(
            id = domain.id,
            name = domain.name,
            dateTimestamp = domain.dateTimestamp,
            description = domain.description,
            location = domain.location,
            celebrationType = domain.celebrationType,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )

        val itemEntities = domain.items.mapIndexed { idx, item ->
            CelebrationItemEntity(
                id = item.id,
                celebrationId = domain.id,
                songId = item.songId,
                liturgicalMoment = item.liturgicalMoment,
                displayOrder = idx,
                overrideKey = item.overrideKey,
                overrideCapo = item.overrideCapo,
                customNotes = item.customNotes
            )
        }

        return celebrationEntity to itemEntities
    }
}
