package com.grafitto.laudate.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migración `MIGRATION_12_13`: añade el **patrón rítmico por sección** al esquema.
 *
 * Una sección de canto puede declarar su propio rasgueo con la directiva ChordPro
 * `{x_rhythm: Nombre}`; el patrón se guarda como **nombre** (no como id) porque los patrones
 * integrados no tienen fila propia y el nombre es único en la biblioteca. Al mostrar la canción
 * se resuelve contra `RhythmPatternRepository`. Permite ritmos distintos por sección
 * (verso en «Rock», coro en «Semicorcheas funk»).
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE song_sections ADD COLUMN rhythm_pattern_name TEXT DEFAULT NULL")
    }
}
