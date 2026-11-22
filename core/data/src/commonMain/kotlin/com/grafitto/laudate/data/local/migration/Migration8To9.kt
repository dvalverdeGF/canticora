package com.grafitto.laudate.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migración `MIGRATION_8_9`: añade el número de repeticiones (bises) a las secciones.
 *
 * Complementa a `is_repeated`, que solo registraba que la sección se repite sin indicar cuántas veces.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE song_sections ADD COLUMN repeat_count INTEGER DEFAULT NULL")
    }
}
