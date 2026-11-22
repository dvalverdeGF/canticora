package com.grafitto.laudate.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migración `MIGRATION_9_10`: añade la **huella de contenido** de la canción.
 *
 * La huella es un SHA-256 del ChordPro canónico del canto, que no se puede calcular en SQL puro.
 * Por eso la columna se crea vacía y la app la rellena en segundo plano al arrancar
 * (`BackfillSongContentHashesUseCase`), sin bloquear ni reescribir los datos del usuario.
 *
 * El índice no es único: varias canciones pueden compartir contenido (dos versiones que el
 * usuario decide conservar); solo acelera la consulta de detección de duplicados.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE songs ADD COLUMN content_hash TEXT DEFAULT NULL")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_content_hash` ON `songs` (`content_hash`)")
    }
}
