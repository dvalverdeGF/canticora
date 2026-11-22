package com.grafitto.laudate.domain.backup

import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.celebration.CelebrationItem
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.song.ChordAnchor
import com.grafitto.laudate.domain.model.song.SectionType
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongLine
import com.grafitto.laudate.domain.model.song.SongSection
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.platform.nowMillis
import com.grafitto.laudate.domain.transfer.BundleRhythmPattern
import com.grafitto.laudate.domain.transfer.BundleTablature
import com.grafitto.laudate.domain.transfer.bundlePatternOf
import com.grafitto.laudate.domain.transfer.toBundleTablature
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long = nowMillis(),
    val songs: List<BackupSong>,
    val celebrations: List<BackupCelebration>,
    val categories: List<BackupCategory>,
    /**
     * Patrones rítmicos personalizados del usuario.
     *
     * Campo **aditivo y con valor por defecto**: una copia antigua (sin esta clave) se restaura
     * sin errores y simplemente no aporta ritmos. Los integrados no se incluyen (viajan en la app).
     */
    val rhythms: List<BundleRhythmPattern> = emptyList(),
    /**
     * Tablaturas del usuario (ADR-017, *Feature T*).
     *
     * Campo **aditivo y con valor por defecto**: una copia antigua (sin esta clave) se restaura
     * sin errores y simplemente no aporta tablaturas.
     */
    val tablatures: List<BundleTablature> = emptyList()
)

@Serializable
data class BackupSong(
    val id: String,
    val title: String,
    val originalKey: String,
    val number: String? = null,
    val author: String? = null,
    val rhythm: String? = null,
    val rhythmPatternId: String? = null,
    val tempoBpm: Int? = null,
    val tempoMarking: String? = null,
    val capo: Int = 0,
    val notes: String? = null,
    val defaultLiturgicalMoments: List<String> = emptyList(),
    val defaultLiturgicalTimes: List<String> = emptyList(),
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val sections: List<BackupSection> = emptyList()
)

@Serializable
data class BackupSection(
    val id: String,
    val songId: String,
    val sectionType: String,
    val sequenceOrder: Int,
    val headerTitle: String? = null,
    val rawTextContent: String,
    val label: String? = null,
    val voice: String? = null,
    val isRepeated: Boolean = false,
    val repeatCount: Int? = null,
    /** Nombre del patrón rítmico de la sección (`{x_rhythm: Nombre}`); campo aditivo y opcional. */
    val rhythmPatternName: String? = null,
    val lines: List<BackupLine> = emptyList()
)

@Serializable
data class BackupLine(
    val lineIndex: Int,
    val lyrics: String,
    val chords: List<BackupAnchor> = emptyList()
)

@Serializable
data class BackupAnchor(
    val id: String,
    val sectionId: String,
    val lineIndex: Int,
    val characterIndex: Int,
    val chordRoot: String,
    val chordQuality: String,
    val chordBass: String? = null,
    val rawChordText: String
)

@Serializable
data class BackupCelebration(
    val id: String,
    val name: String,
    val dateTimestamp: Long,
    val description: String? = null,
    val location: String? = null,
    /** Tipo de celebración: "Misa", "Hora Santa", "Evento general"… */
    val celebrationType: String = "Misa",
    val createdAt: Long,
    val updatedAt: Long,
    val items: List<BackupCelebrationItem> = emptyList()
)

@Serializable
data class BackupCelebrationItem(
    val id: String,
    val celebrationId: String,
    val songId: String,
    val liturgicalMoment: String,
    val displayOrder: Int,
    val overrideKey: String? = null,
    val overrideCapo: Int? = null,
    val customNotes: String? = null
)

@Serializable
data class BackupCategory(
    val id: String,
    val name: String,
    val description: String? = null,
    val displayOrder: Int = 0,
    val isSystemStandard: Boolean = false,
    val kind: String = "ADDITIONAL",
    val createdAt: Long
)

object BackupSerializer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun exportToJson(
        songs: List<Song>,
        celebrations: List<Celebration>,
        categories: List<LiturgicalCategory>,
        rhythms: List<RhythmPattern> = emptyList(),
        tablatures: List<Tablature> = emptyList()
    ): String = json.encodeToString(
        toPayload(songs, celebrations, categories, rhythms, tablatures)
    )

    /** Construye el payload estructurado (canciones, repertorios, categorías y ritmos). */
    fun toPayload(
        songs: List<Song>,
        celebrations: List<Celebration>,
        categories: List<LiturgicalCategory>,
        rhythms: List<RhythmPattern> = emptyList(),
        tablatures: List<Tablature> = emptyList()
    ): BackupPayload = BackupPayload(
        songs = songs.map { it.toBackup() },
        celebrations = celebrations.map { it.toBackup() },
        categories = categories.map { it.toBackup() },
        rhythms = rhythms.filter { !it.isBuiltIn }.map { it.bundlePatternOf() },
        tablatures = tablatures.map { it.toBundleTablature() }
    )

    fun importFromJson(jsonString: String): BackupPayload {
        return json.decodeFromString(jsonString)
    }

    private fun Song.toBackup() = BackupSong(
        id = id,
        title = title,
        originalKey = originalKey,
        number = number,
        author = author,
        rhythm = rhythm,
        rhythmPatternId = rhythmPatternId,
        tempoBpm = tempoBpm,
        tempoMarking = tempoMarking,
        capo = capo,
        notes = notes,
        defaultLiturgicalMoments = defaultLiturgicalMoments,
        defaultLiturgicalTimes = defaultLiturgicalTimes,
        isFavorite = isFavorite,
        createdAt = createdAt,
        updatedAt = updatedAt,
        sections = sections.map { sec ->
            BackupSection(
                id = sec.id,
                songId = sec.songId,
                sectionType = sec.sectionType.name,
                sequenceOrder = sec.sequenceOrder,
                headerTitle = sec.headerTitle,
                rawTextContent = sec.rawTextContent,
                label = sec.label,
                voice = sec.voice,
                isRepeated = sec.isRepeated,
                repeatCount = sec.repeatCount,
                rhythmPatternName = sec.rhythmPatternName,
                lines = sec.lines.map { l ->
                    BackupLine(
                        lineIndex = l.lineIndex,
                        lyrics = l.lyrics,
                        chords = l.chords.map { c ->
                            BackupAnchor(
                                id = c.id,
                                sectionId = c.sectionId,
                                lineIndex = c.lineIndex,
                                characterIndex = c.characterIndex,
                                chordRoot = c.chord.rootNote.preferredEnglishName,
                                chordQuality = c.chord.quality,
                                chordBass = c.chord.bassNote?.preferredEnglishName,
                                rawChordText = c.rawChordText
                            )
                        }
                    )
                }
            )
        }
    )

    private fun Celebration.toBackup() = BackupCelebration(
        id = id,
        name = name,
        dateTimestamp = dateTimestamp,
        description = description,
        location = location,
        celebrationType = celebrationType,
        createdAt = createdAt,
        updatedAt = updatedAt,
        items = items.map { item ->
            BackupCelebrationItem(
                id = item.id,
                celebrationId = item.celebrationId,
                songId = item.songId,
                liturgicalMoment = item.liturgicalMoment,
                displayOrder = item.displayOrder,
                overrideKey = item.overrideKey,
                overrideCapo = item.overrideCapo,
                customNotes = item.customNotes
            )
        }
    )

    private fun LiturgicalCategory.toBackup() = BackupCategory(
        id = id,
        name = name,
        description = description,
        displayOrder = displayOrder,
        isSystemStandard = isSystemStandard,
        kind = kind.name,
        createdAt = createdAt
    )
}
