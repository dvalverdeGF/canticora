package com.grafitto.laudate.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.grafitto.laudate.data.local.newId
import com.grafitto.laudate.data.local.nowMillis

/**
 * Adjunto de un canto (audio, vídeo, documento, imagen o enlace externo).
 *
 * Para los adjuntos de fichero, [localPath] apunta a la **copia en el almacén privado** de la
 * aplicación (`files/attachments/<songId>/…`); para los enlaces guarda la URL. El campo
 * [sourceUri] conserva la ubicación original por si se quiere volver a importar.
 */
@Entity(
    tableName = "song_attachments",
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
        Index(value = ["song_id", "created_at"])
    ]
)
data class SongAttachmentEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String = newId(),
    @ColumnInfo(name = "song_id") val songId: String,
    /** AUDIO, VIDEO, DOCUMENT, IMAGE o LINK. */
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    /** Ruta local de la copia (ficheros) o URL (enlaces). */
    @ColumnInfo(name = "uri") val uri: String,
    /** Ubicación original elegida por el usuario, solo informativa. */
    @ColumnInfo(name = "source_uri") val sourceUri: String? = null,
    @ColumnInfo(name = "mime_type") val mimeType: String? = null,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = nowMillis()
)
