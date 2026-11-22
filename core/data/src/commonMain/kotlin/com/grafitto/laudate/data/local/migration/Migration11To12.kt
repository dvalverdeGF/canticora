package com.grafitto.laudate.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migración `MIGRATION_11_12`: añade al catálogo de ritmos la posibilidad de **borrar patrones
 * integrados**.
 *
 * Los patrones de la biblioteca (`GuitarPatternLibrary`) no tienen fila propia, así que para
 * recordar cuáles ha eliminado el usuario se añaden dos columnas a `rhythm_patterns`; la fila
 * centinela con `is_built_in = 1` guarda la lista de nombres ocultos (`built_in_name` identifica
 * cada patrón integrado si en el futuro se materializan como filas).
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE rhythm_patterns ADD COLUMN built_in_name TEXT DEFAULT NULL")
        connection.execSQL("ALTER TABLE rhythm_patterns ADD COLUMN deleted_built_ins_json TEXT DEFAULT NULL")
    }
}
