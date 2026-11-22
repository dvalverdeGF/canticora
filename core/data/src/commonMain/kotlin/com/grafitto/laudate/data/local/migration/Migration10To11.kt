package com.grafitto.laudate.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migración `MIGRATION_10_11`: añade la tabla de **adjuntos de canción** (audio, vídeo,
 * documentos, imágenes y enlaces).
 *
 * Los ficheros viven en el almacén privado de la app (`files/attachments/`); la base solo guarda
 * sus metadatos. La clave foránea con borrado en cascada hace que al eliminar un canto
 * desaparezcan también sus adjuntos.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `song_attachments` (
                `id` TEXT NOT NULL,
                `song_id` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `display_name` TEXT NOT NULL,
                `uri` TEXT NOT NULL,
                `source_uri` TEXT DEFAULT NULL,
                `mime_type` TEXT DEFAULT NULL,
                `size_bytes` INTEGER DEFAULT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`song_id`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_song_attachments_song_id` ON `song_attachments` (`song_id`)")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_song_attachments_song_id_created_at` " +
                "ON `song_attachments` (`song_id`, `created_at`)"
        )
    }
}
