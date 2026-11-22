package com.grafitto.laudate.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migración `MIGRATION_6_7`: añade la etiqueta, la voz y la marca de repetición (bises) a las secciones.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE song_sections ADD COLUMN label TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE song_sections ADD COLUMN voice TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE song_sections ADD COLUMN is_repeated INTEGER NOT NULL DEFAULT 0")
    }
}
