package com.grafitto.laudate.domain.backup

import com.grafitto.laudate.domain.model.attachment.SongAttachment

/**
 * Acceso al **archivo físico** de una copia de seguridad (un ZIP que contiene el manifiesto JSON y
 * los ficheros adjuntos).
 *
 * Vive en Dominio (Kotlin puro): el Dominio describe *qué* se guarda y *qué* se extrae, mientras la
 * implementación concreta conoce Android (`ContentResolver`, `ZipOutputStream`…) y resuelve los
 * `Uri`, que se manejan siempre como texto.
 */
interface BackupArchiveStore {

    /**
     * Crea el archivo de copia de seguridad en [destinationUri] con el manifiesto [manifestJson]
     * y los ficheros adjuntos indicados. Cada adjunto con contenido se guarda en la ruta
     * determinista [backupAttachmentEntryPath].
     */
    suspend fun writeArchive(
        destinationUri: String,
        manifestEntryName: String,
        manifestJson: String,
        attachments: List<SongAttachment>
    )

    /** Lee y devuelve el texto de la entrada [entryName] del ZIP. `null` si no existe. */
    suspend fun readEntry(archiveUri: String, entryName: String): String?

    /**
     * Lee y devuelve el texto de la **primera entrada** cuyo nombre **termine** en [suffix]
     * (comparación sin distinguir mayúsculas). `null` si ninguna coincide.
     *
     * Sirve para formatos ZIP cuyo nombre de entrada **no es fijo**: un `.mscz` de MuseScore, por
     * ejemplo, guarda el XML de la partitura en `<nombre>.mscx`, que cambia con el fichero. Así se
     * reutiliza el **mismo lector de ZIP** que la copia de seguridad (ADR-017), sin un segundo
     * lector que mantener en sincronía.
     */
    suspend fun readEntryEndingWith(archiveUri: String, suffix: String): String?

    /**
     * Extrae un adjunto del ZIP al almacén privado del canto [songId] y devuelve su ruta local.
     *
     * @throws IllegalArgumentException si la entrada no existe en el archivo.
     */
    suspend fun extractAttachment(
        archiveUri: String,
        entryPath: String,
        songId: String,
        displayName: String,
        mimeType: String?
    ): String

    /** Copia un archivo del almacén privado a [destinationUri] (para descargar un adjunto). */
    suspend fun copyLocalFileTo(attachment: SongAttachment, destinationUri: String)
}
