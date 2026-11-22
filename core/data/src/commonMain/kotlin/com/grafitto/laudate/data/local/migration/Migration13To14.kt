package com.grafitto.laudate.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migración `MIGRATION_13_14`: convierte la tabla `numeric_tablatures` (declarada y **vacía** desde
 * el primer día) en el almacén de **tablaturas** de la *Feature T* (ADR-017).
 *
 * La tabla existía con `id`, `song_id`, `pattern_json` y `tuning`; se le añaden los campos del
 * modelo: nombre, instrumento, compás, resolución, tonalidad escrita, origen y marcas de tiempo.
 * El índice `index_numeric_tablatures_song_id` ya existe y **no es único**, de modo que un canto
 * puede tener varias tablaturas (p. ej. guitarra y bajo).
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE numeric_tablatures ADD COLUMN name TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE numeric_tablatures ADD COLUMN instrument TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE numeric_tablatures ADD COLUMN time_signature TEXT NOT NULL DEFAULT '4/4'")
        connection.execSQL("ALTER TABLE numeric_tablatures ADD COLUMN resolution TEXT NOT NULL DEFAULT 'EIGHTH'")
        connection.execSQL("ALTER TABLE numeric_tablatures ADD COLUMN written_key TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE numeric_tablatures ADD COLUMN origin TEXT NOT NULL DEFAULT 'CREATED'")
        connection.execSQL("ALTER TABLE numeric_tablatures ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE numeric_tablatures ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
    }
}
