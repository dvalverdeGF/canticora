package com.grafitto.laudate.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.grafitto.laudate.data.local.converters.RoomConverters
import com.grafitto.laudate.data.local.dao.AttachmentDao
import com.grafitto.laudate.data.local.dao.CategoryDao
import com.grafitto.laudate.data.local.dao.CelebrationDao
import com.grafitto.laudate.data.local.dao.RhythmPatternDao
import com.grafitto.laudate.data.local.dao.SongDao
import com.grafitto.laudate.data.local.dao.TablatureDao
import com.grafitto.laudate.data.local.entity.ArrangementEntity
import com.grafitto.laudate.data.local.entity.CelebrationEntity
import com.grafitto.laudate.data.local.entity.CelebrationItemEntity
import com.grafitto.laudate.data.local.entity.ChordAnchorEntity
import com.grafitto.laudate.data.local.entity.CustomSymbolEntity
import com.grafitto.laudate.data.local.entity.LiturgicalCategoryEntity
import com.grafitto.laudate.data.local.entity.NumericTablatureEntity
import com.grafitto.laudate.data.local.entity.RhythmPatternEntity
import com.grafitto.laudate.data.local.entity.ScoreEntity
import com.grafitto.laudate.data.local.entity.SongAttachmentEntity
import com.grafitto.laudate.data.local.entity.SongEntity
import com.grafitto.laudate.data.local.entity.SongFtsEntity
import com.grafitto.laudate.data.local.entity.SongSectionEntity
import com.grafitto.laudate.data.local.entity.UserAnnotationEntity
import com.grafitto.laudate.data.local.migration.MIGRATION_6_7 as MIGRATION_6_7_IMPL
import com.grafitto.laudate.data.local.migration.MIGRATION_7_8 as MIGRATION_7_8_IMPL
import com.grafitto.laudate.data.local.migration.MIGRATION_8_9 as MIGRATION_8_9_IMPL
import com.grafitto.laudate.data.local.migration.MIGRATION_9_10 as MIGRATION_9_10_IMPL
import com.grafitto.laudate.data.local.migration.MIGRATION_10_11 as MIGRATION_10_11_IMPL
import com.grafitto.laudate.data.local.migration.MIGRATION_11_12 as MIGRATION_11_12_IMPL
import com.grafitto.laudate.data.local.migration.MIGRATION_12_13 as MIGRATION_12_13_IMPL
import com.grafitto.laudate.data.local.migration.MIGRATION_13_14 as MIGRATION_13_14_IMPL

@Database(
    entities = [
        SongEntity::class,
        SongFtsEntity::class,
        SongSectionEntity::class,
        ChordAnchorEntity::class,
        ArrangementEntity::class,
        CelebrationEntity::class,
        CelebrationItemEntity::class,
        UserAnnotationEntity::class,
        CustomSymbolEntity::class,
        ScoreEntity::class,
        NumericTablatureEntity::class,
        LiturgicalCategoryEntity::class,
        RhythmPatternEntity::class,
        SongAttachmentEntity::class
    ],
    version = 14,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class LaudateDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun celebrationDao(): CelebrationDao
    abstract fun categoryDao(): CategoryDao
    abstract fun rhythmPatternDao(): RhythmPatternDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun tablatureDao(): TablatureDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE songs ADD COLUMN capo INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE songs ADD COLUMN notes TEXT DEFAULT NULL")
                connection.execSQL("ALTER TABLE celebrations ADD COLUMN celebration_type TEXT NOT NULL DEFAULT 'Misa'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `rhythm_patterns` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `numerator` INTEGER NOT NULL,
                        `denominator` INTEGER NOT NULL,
                        `events_json` TEXT NOT NULL DEFAULT '[]',
                        `is_built_in` INTEGER NOT NULL DEFAULT 0,
                        `time_signature` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_rhythm_patterns_time_signature` ON `rhythm_patterns` (`time_signature`)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_rhythm_patterns_name` ON `rhythm_patterns` (`name`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE songs ADD COLUMN tempo_bpm INTEGER DEFAULT NULL")
                connection.execSQL("ALTER TABLE songs ADD COLUMN tempo_marking TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE songs ADD COLUMN rhythm_pattern_id TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE liturgical_categories ADD COLUMN kind TEXT NOT NULL DEFAULT 'ADDITIONAL'")
                // Las categorías canónicas marcadas como estándar ya existentes corresponden a momentos de la misa.
                connection.execSQL("UPDATE liturgical_categories SET kind = 'MASS_MOMENT' WHERE is_system_standard = 1")
            }
        }

        val MIGRATION_6_7 = MIGRATION_6_7_IMPL

        val MIGRATION_7_8 = MIGRATION_7_8_IMPL

        val MIGRATION_8_9 = MIGRATION_8_9_IMPL

        val MIGRATION_9_10 = MIGRATION_9_10_IMPL

        val MIGRATION_10_11 = MIGRATION_10_11_IMPL

        val MIGRATION_11_12 = MIGRATION_11_12_IMPL

        val MIGRATION_12_13 = MIGRATION_12_13_IMPL

        val MIGRATION_13_14 = MIGRATION_13_14_IMPL
    }
}
