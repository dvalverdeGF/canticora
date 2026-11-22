package com.grafitto.laudate.web.platform

import com.grafitto.laudate.web.WebPlatform
import com.grafitto.laudate.web.io.WebBytes
import com.grafitto.laudate.web.io.WebZip
import com.grafitto.laudate.web.io.WebZipEntry

/**
 * Un archivo (ZIP o texto) que vive en la memoria del navegador mientras dura una importación.
 *
 * En Android y escritorio los casos de uso reciben un `Uri`/ruta que pueden volver a abrir cuando
 * quieran. En la web el fichero elegido por el usuario solo existe como bytes en memoria, así que
 * se le da un **URI ficticio** (`mem://3`) y se resuelve contra este registro. Así los mismos casos
 * de uso de Dominio (copia de seguridad, paquetes de canto, partituras) funcionan sin cambios.
 */
internal class WebArchive(val fileName: String, val bytes: ByteArray, private val isZip: Boolean) {

    private val entries: List<WebZipEntry> by lazy {
        runCatching { WebZip.entries(bytes) }.getOrDefault(emptyList())
    }

    /** Texto del fichero tal cual (MusicXML sin comprimir, ChordPro, JSON…). */
    val text: String? get() = runCatching { bytes.decodeToString() }.getOrNull()

    fun textOf(entryName: String): String? =
        entries.firstOrNull { it.name == entryName }?.let { it.bytes.decodeToString() }

    fun textEndingWith(suffix: String): String? = entries
        .firstOrNull { it.name.endsWith(suffix, ignoreCase = true) }
        ?.let { it.bytes.decodeToString() }

    fun bytesOf(entryName: String): ByteArray? =
        entries.firstOrNull { it.name == entryName }?.bytes

    /** `true` si el contenido es realmente un ZIP (firma `PK\x03\x04`) y no un fichero de texto. */
    val isArchive: Boolean
        get() = isZip || entries.isNotEmpty()
}

/** Registro de los ficheros que la UI ha elegido y que los casos de uso leerán por su `mem://`. */
internal object WebArchiveRegistry {

    private val archives = LinkedHashMap<String, WebArchive>()
    private var counter = 0

    /** Registra unos bytes elegidos por el usuario y devuelve el URI con el que se leerán. */
    fun register(bytes: ByteArray, fileName: String): String {
        val uri = "$SCHEME${counter++}"
        val looksZip = bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() &&
            bytes[3] == 4.toByte()
        archives[uri] = WebArchive(fileName, bytes, looksZip)
        // Se guardan como mucho los últimos 8: un fichero elegido se consume enseguida.
        while (archives.size > MAX_ARCHIVES) {
            archives.remove(archives.keys.first())
        }
        return uri
    }

    fun find(uri: String): WebArchive? = archives[uri]

    /** Guarda unos bytes bajo un URI **elegido por quien llama** (paquetes y copias efímeras). */
    fun write(uri: String, bytes: ByteArray, fileName: String) {
        val looksZip = bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() &&
            bytes[3] == 4.toByte()
        archives[uri] = WebArchive(fileName, bytes, looksZip)
    }

    /** Resuelve un `mem://` (memoria) o un `idb://` (almacén del navegador) a sus bytes. */
    suspend fun read(uri: String): WebArchive? {
        archives[uri]?.let { return it }
        if (!uri.startsWith(WebPlatform.IDB_PREFIX)) return null
        val stored = WebPlatform.readStoredFile(WebPlatform.keyOf(uri)) ?: return null
        val bytes = runCatching { WebBytes.base64Decode(stored.base64) }.getOrNull() ?: return null
        val archive = WebArchive(stored.fileName, bytes, isZip = true)
        archives[uri] = archive
        return archive
    }

    private const val SCHEME = "mem://"
    private const val MAX_ARCHIVES = 8
}

/** Esquema con el que la UI pide «descargar esto» en lugar de guardarlo en la memoria. */
internal const val DOWNLOAD_SCHEME = "download:"

/** Construye el URI de descarga de un fichero con nombre [fileName]. */
internal fun downloadUri(fileName: String): String = DOWNLOAD_SCHEME + fileName

/** Nombre del fichero que lleva un URI de descarga o de archivo en memoria. */
internal fun fileNameOf(uri: String): String = when {
    uri.startsWith(DOWNLOAD_SCHEME) -> uri.removePrefix(DOWNLOAD_SCHEME)
    uri.startsWith("mem://") -> "respaldo.canticora"
    else -> uri.substringAfterLast('/').ifBlank { "respaldo.canticora" }
}
