package com.grafitto.laudate.domain.model.attachment

import com.grafitto.laudate.domain.platform.nowMillis
import com.grafitto.laudate.domain.text.formatOneDecimal

/**
 * Tipo de adjunto asociado a un canto.
 *
 * El tipo determina el icono de la interfaz y, sobre todo, **dónde vive el contenido**:
 * los adjuntos de fichero ([AUDIO], [VIDEO], [DOCUMENT], [IMAGE]) se copian al almacén
 * privado de la app para que sobrevivan a la limpieza del sistema y entren en la copia de
 * seguridad; los [LINK] son direcciones de Internet y solo se guarda su URL.
 */
enum class AttachmentKind {
    /** Grabaciones de ensayo, pistas de acompañamiento… */
    AUDIO,

    /** Vídeos de referencia (montaje, coreografía, tutorial…). */
    VIDEO,

    /** Partituras, fichas y documentos (PDF, Word…). */
    DOCUMENT,

    /** Imágenes (fotos del papel, portadas…). */
    IMAGE,

    /** Enlace externo (YouTube, Drive, web del autor…); no se descarga el contenido. */
    LINK;

    companion object {
        /** Deduce el tipo a partir del MIME declarado por el sistema de archivos. */
        fun fromMimeType(mimeType: String?): AttachmentKind {
            val mime = mimeType?.lowercase().orEmpty()
            return when {
                mime.startsWith("audio/") -> AUDIO
                mime.startsWith("video/") -> VIDEO
                mime.startsWith("image/") -> IMAGE
                else -> DOCUMENT
            }
        }

        fun fromName(name: String): AttachmentKind? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * Adjunto de un canto: audio, vídeo, documento, imagen o enlace.
 *
 * Es un modelo **inmutable** de Dominio. Para los adjuntos de fichero, [uri] apunta a una copia
 * guardada en el almacén privado de la aplicación (no a la ubicación original elegida por el
 * usuario, que puede desaparecer); para los enlaces, [uri] es la URL tal cual.
 *
 * [displayName] es el nombre que ve el usuario, y [sizeBytes] el tamaño de la copia cuando se
 * conoce (los enlaces no tienen tamaño).
 */
data class SongAttachment(
    val id: String,
    val songId: String,
    val kind: AttachmentKind,
    val displayName: String,
    val uri: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val createdAt: Long = nowMillis()
) {
    /** `true` si es un enlace externo (no hay fichero local). */
    val isLink: Boolean get() = kind == AttachmentKind.LINK

    /** Tamaño legible ("2,4 MB") o `null` si no se conoce. */
    fun readableSize(): String? {
        val bytes = sizeBytes ?: return null
        if (bytes <= 0) return null
        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        val formatted = if (value >= 10 || unitIndex == 0) {
            value.toInt().toString()
        } else {
            formatOneDecimal(value)
        }
        return "$formatted ${units[unitIndex]}"
    }
}
