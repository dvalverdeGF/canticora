package com.grafitto.laudate.data.local.entity

import com.grafitto.laudate.data.local.newId
import com.grafitto.laudate.data.local.nowMillis
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["title"]),
        Index(value = ["original_key"]),
        Index(value = ["is_favorite"]),
        // No es UNIQUE: dos versiones de la misma canción pueden convivir si el usuario lo decide.
        Index(value = ["content_hash"])
    ]
)
data class SongEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = newId(),
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "original_key") val originalKey: String,
    @ColumnInfo(name = "number") val number: String? = null,
    @ColumnInfo(name = "author") val author: String?,
    @ColumnInfo(name = "rhythm") val rhythm: String?,
    @ColumnInfo(name = "default_liturgical_moments_json") val defaultLiturgicalMomentsJson: String = "[]",
    @ColumnInfo(name = "default_liturgical_times_json") val defaultLiturgicalTimesJson: String = "[]",
    @ColumnInfo(name = "created_at") val createdAt: Long = nowMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = nowMillis(),
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "capo") val capo: Int = 0,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "tempo_bpm") val tempoBpm: Int? = null,
    @ColumnInfo(name = "tempo_marking") val tempoMarking: String? = null,
    @ColumnInfo(name = "rhythm_pattern_id") val rhythmPatternId: String? = null,
    /**
     * Huella de contenido (SHA-256 del ChordPro canónico) para detectar duplicados.
     * Valor derivado: lo recalcula el repositorio al guardar; puede ser `null` en filas antiguas
     * hasta que el relleno en segundo plano las complete.
     */
    @ColumnInfo(name = "content_hash") val contentHash: String? = null
)

@Fts4(contentEntity = SongEntity::class)
@Entity(tableName = "songs_fts")
data class SongFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Int,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "author") val author: String?
)

@Entity(
    tableName = "song_sections",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["song_id"]),
        Index(value = ["song_id", "sequence_order"])
    ]
)
data class SongSectionEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = newId(),
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "section_type") val sectionType: String,
    @ColumnInfo(name = "sequence_order") val sequenceOrder: Int,
    @ColumnInfo(name = "raw_text_content") val rawTextContent: String,
    @ColumnInfo(name = "header_title") val headerTitle: String?,
    @ColumnInfo(name = "label") val label: String? = null,
    @ColumnInfo(name = "voice") val voice: String? = null,
    @ColumnInfo(name = "is_repeated") val isRepeated: Boolean = false,
    @ColumnInfo(name = "repeat_count") val repeatCount: Int? = null,
    /**
     * Nombre del patrón rítmico declarado con `{x_rhythm: Nombre}` para esta sección (v13).
     * Permite que el canto cambie de rasgueo por sección (verso, coro, intro…).
     */
    @ColumnInfo(name = "rhythm_pattern_name") val rhythmPatternName: String? = null
)

@Entity(
    tableName = "chords_anchors",
    foreignKeys = [
        ForeignKey(
            entity = SongSectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["section_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["section_id"]),
        Index(value = ["section_id", "line_index", "character_index"])
    ]
)
data class ChordAnchorEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = newId(),
    @ColumnInfo(name = "section_id") val sectionId: String,
    @ColumnInfo(name = "line_index") val lineIndex: Int,
    @ColumnInfo(name = "character_index") val characterIndex: Int,
    @ColumnInfo(name = "chord_root") val chordRoot: String,
    @ColumnInfo(name = "chord_quality") val chordQuality: String,
    @ColumnInfo(name = "chord_bass") val chordBass: String?,
    @ColumnInfo(name = "raw_chord_text") val rawChordText: String
)

@Entity(
    tableName = "arrangements",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["song_id"])]
)
data class ArrangementEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = newId(),
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "target_key") val targetKey: String,
    @ColumnInfo(name = "target_bpm") val targetBpm: Int = 120,
    @ColumnInfo(name = "capo_position") val capoPosition: Int = 0,
    @ColumnInfo(name = "section_sequence_json") val sectionSequenceJson: String = "[]",
    @ColumnInfo(name = "is_default") val isDefault: Boolean = false
)

@Entity(
    tableName = "celebrations",
    indices = [Index(value = ["date_timestamp"])]
)
data class CelebrationEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = newId(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "date_timestamp") val dateTimestamp: Long,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "location") val location: String?,
    @ColumnInfo(name = "celebration_type") val celebrationType: String = "Misa",
    @ColumnInfo(name = "created_at") val createdAt: Long = nowMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = nowMillis()
)

@Entity(
    tableName = "celebration_items",
    foreignKeys = [
        ForeignKey(
            entity = CelebrationEntity::class,
            parentColumns = ["id"],
            childColumns = ["celebration_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["celebration_id"]),
        Index(value = ["song_id"]),
        Index(value = ["celebration_id", "display_order"])
    ]
)
data class CelebrationItemEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = newId(),
    @ColumnInfo(name = "celebration_id") val celebrationId: String,
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "liturgical_moment") val liturgicalMoment: String,
    @ColumnInfo(name = "display_order") val displayOrder: Int,
    @ColumnInfo(name = "override_key") val overrideKey: String?,
    @ColumnInfo(name = "override_capo") val overrideCapo: Int?,
    @ColumnInfo(name = "custom_notes") val customNotes: String?
)

@Entity(
    tableName = "user_annotations",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["song_id"])]
)
data class UserAnnotationEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = newId(),
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "line_index") val lineIndex: Int,
    @ColumnInfo(name = "character_index") val characterIndex: Int,
    @ColumnInfo(name = "text_content") val textContent: String,
    @ColumnInfo(name = "color_hex") val colorHex: String = "#FFD700",
    @ColumnInfo(name = "created_at") val createdAt: Long = nowMillis()
)

@Entity(
    tableName = "custom_symbols",
    foreignKeys = [
        ForeignKey(
            entity = UserAnnotationEntity::class,
            parentColumns = ["id"],
            childColumns = ["annotation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["annotation_id"])]
)
data class CustomSymbolEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = newId(),
    @ColumnInfo(name = "annotation_id") val annotationId: String,
    @ColumnInfo(name = "symbol_type") val symbolType: String,
    @ColumnInfo(name = "icon_identifier") val iconIdentifier: String,
    @ColumnInfo(name = "position_x") val positionX: Int,
    @ColumnInfo(name = "position_y") val positionY: Int
)

@Entity(
    tableName = "scores",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["song_id"])]
)
data class ScoreEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = newId(),
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "score_type") val scoreType: String,
    @ColumnInfo(name = "file_path_or_uri") val filePathOrUri: String,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long
)

@Entity(
    tableName = "numeric_tablatures",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["song_id"])]
)
data class NumericTablatureEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = newId(),
    @ColumnInfo(name = "song_id") val songId: String,
    /** Tablatura completa serializada a JSON (modelo de dominio `Tablature`). */
    @ColumnInfo(name = "pattern_json") val patternJson: String,
    /** Afinación en texto para lectura rápida (p. ej. `EADGBE`). */
    @ColumnInfo(name = "tuning") val tuning: String = "EADGBE",
    @ColumnInfo(name = "name") val name: String = "",
    @ColumnInfo(name = "instrument") val instrument: String = "",
    @ColumnInfo(name = "time_signature") val timeSignature: String = "4/4",
    @ColumnInfo(name = "resolution") val resolution: String = "EIGHTH",
    @ColumnInfo(name = "written_key") val writtenKey: String? = null,
    @ColumnInfo(name = "origin") val origin: String = "CREATED",
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L
)

@Entity(
    tableName = "liturgical_categories",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["display_order"])
    ]
)
data class LiturgicalCategoryEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = newId(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "display_order") val displayOrder: Int = 0,
    @ColumnInfo(name = "is_system_standard") val isSystemStandard: Boolean = false,
    @ColumnInfo(name = "kind") val kind: String = "ADDITIONAL",
    @ColumnInfo(name = "created_at") val createdAt: Long = nowMillis()
)

@Entity(
    tableName = "rhythm_patterns",
    indices = [Index(value = ["time_signature"]), Index(value = ["name"])]
)
data class RhythmPatternEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = newId(),
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "numerator") val numerator: Int,
    @ColumnInfo(name = "denominator") val denominator: Int,
    @ColumnInfo(name = "events_json") val eventsJson: String = "[]",
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "time_signature") val timeSignature: String = "$numerator/$denominator",
    @ColumnInfo(name = "created_at") val createdAt: Long = nowMillis(),
    /** Marca de los patrones integrados de la biblioteca (permite ocultarlos al borrarlos). */
    @ColumnInfo(name = "built_in_name") val builtInName: String? = null,
    /** Nombres de los patrones integrados que el usuario ha eliminado (lista JSON). */
    @ColumnInfo(name = "deleted_built_ins_json") val deletedBuiltInsJson: String? = null
)
