package com.grafitto.laudate.domain.attachment

import com.grafitto.laudate.domain.model.attachment.SongAttachment

/**
 * Abstracción de almacenamiento de ficheros adjuntos.
 *
 * Vive en Dominio (Kotlin puro) y **no conoce Android**: la implementación real copia el fichero
 * elegido por el usuario (a partir de su URI de contenido) al almacén privado de la aplicación,
 * de modo que el adjunto sobreviva a la limpieza del sistema y entre en las copias de seguridad.
 *
 * Las rutas de origen y destino se manejan como texto para no filtrar tipos de Android hacia el
 * Dominio.
 */
interface AttachmentStore {

    /**
     * Copia el fichero indicado por [sourceUri] al almacén privado y devuelve el adjunto listo
     * para persistir (con su nombre visible, tamaño y ruta local definitiva).
     *
     * @throws IllegalArgumentException si el origen no se puede leer.
     */
    suspend fun importFile(
        songId: String,
        sourceUri: String,
        displayName: String,
        mimeType: String?
    ): SongAttachment

    /** Borra el fichero local de un adjunto (no hace nada si es un enlace). */
    suspend fun deleteFile(attachment: SongAttachment)

    /** Borra todos los ficheros de una canción (al eliminar el canto). */
    suspend fun deleteFilesForSong(songId: String)

    /** `true` si el fichero local del adjunto sigue existiendo. */
    suspend fun exists(attachment: SongAttachment): Boolean

    /**
     * Copia el fichero local de un adjunto al destino elegido por el usuario (SAF) para
     * descargarlo o compartirlo.
     */
    suspend fun exportFile(attachment: SongAttachment, destinationUri: String): Result<Unit>
}
