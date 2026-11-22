package com.grafitto.laudate.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migración `MIGRATION_7_8`: añade el número de canción del cancionero de origen.
 *
 * El número es opcional y **no único**: varios cancioneros pueden reutilizar el mismo número
 * (p. ej. varios «Gloria» con números distintos), por lo que no se crea índice único.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE songs ADD COLUMN number TEXT DEFAULT NULL")
    }
}
