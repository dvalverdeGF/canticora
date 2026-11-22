package com.grafitto.laudate.domain.backup

import com.grafitto.laudate.domain.platform.nowMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Identificador del formato de copia de seguridad completa de Canticora. */
const val CANTICORA_BACKUP_FORMAT: String = "canticora.backup"

/** Extensión de los ficheros de copia de seguridad. */
const val CANTICORA_BACKUP_EXTENSION: String = "canticora"

/** Tipo MIME de la copia de seguridad (ZIP con el manifiesto JSON y los adjuntos dentro). */
const val CANTICORA_BACKUP_MIME: String = "application/vnd.canticora.backup+zip"

/**
 * Formatos aceptados al importar. Solo el identificador actual: la app aún no está en producción,
 * así que no se mantiene compatibilidad con el identificador antiguo `laudate.backup`.
 */
val CANTICORA_BACKUP_FORMATS: Set<String> = setOf(CANTICORA_BACKUP_FORMAT)

/** Nombre de la entrada del ZIP con el manifiesto completo (datos + índice de adjuntos). */
const val BACKUP_DATA_ENTRY: String = "backup.json"

/** Carpeta, dentro del ZIP, donde viven los ficheros adjuntos. */
const val BACKUP_ATTACHMENTS_DIR: String = "attachments"

/** Construye la ruta determinista de un adjunto dentro del ZIP a partir de su identificador. */
fun backupAttachmentEntryPath(attachmentId: String): String = "$BACKUP_ATTACHMENTS_DIR/$attachmentId"

/**
 * **Nombre de archivo** de una copia de seguridad recién creada, con la fecha del día.
 *
 * Vive en el Dominio —y no en cada plataforma— porque Android y escritorio lo generaban por su
 * cuenta: Android pedía `canticora_copia_AAAA-MM-DD.canticora` y el escritorio seguía con el literal
 * `laudate_copia.laudate`, con la **marca y la extensión antiguas**. De ahí que los ficheros de
 * Windows se llamaran «laudate» mientras el resto de la app ya decía «Canticora».
 *
 * @param isoDate fecha en formato `AAAA-MM-DD` (se pasa desde fuera para que la función sea pura y
 *   se pueda probar sin depender del reloj).
 */
fun canticoraBackupFileName(isoDate: String): String = "canticora_copia_$isoDate.$CANTICORA_BACKUP_EXTENSION"

/**
 * Copia de seguridad **completa** de la biblioteca local: canciones, repertorios, categorías y
 * adjuntos.
 *
 * Se guarda dentro de un ZIP ([BACKUP_DATA_ENTRY] lleva este objeto serializado en JSON y
 * [BACKUP_ATTACHMENTS_DIR] los ficheros adjuntos). El ZIP es a la vez el archivo que se descarga,
 * se comparte o se sube a Drive, y el que se reimporta en una instalación nueva. Toda la parte
 * serializada es Kotlin puro y testeable.
 */
@Serializable
data class FullBackup(
    val format: String = CANTICORA_BACKUP_FORMAT,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAt: Long = nowMillis(),
    val appVersion: String = "desconocida",
    /** Contenido estructurado (canciones, repertorios y categorías) con el esquema ya existente. */
    val payload: BackupPayload,
    /** Índice de los adjuntos incluidos en el ZIP. */
    val attachments: List<BackupAttachment> = emptyList()
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/**
 * Metadatos de un adjunto dentro de la copia de seguridad.
 *
 * El fichero físico no va aquí: esta entrada apunta a su ruta dentro del ZIP ([entryPath]).
 * Cuando el adjunto es un enlace no hay fichero y [entryPath] es `null`.
 */
@Serializable
data class BackupAttachment(
    val id: String,
    val songId: String,
    /** AUDIO, VIDEO, DOCUMENT, IMAGE o LINK. */
    val kind: String,
    val displayName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val createdAt: Long = nowMillis(),
    /** URL del enlace, solo para `kind = LINK`. */
    val linkUrl: String? = null,
    /** Ruta del fichero dentro del ZIP, solo para adjuntos con contenido. */
    val entryPath: String? = null
)

/** Serialización (Kotlin puro) del manifiesto de una copia de seguridad. */
object FullBackupSerializer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: FullBackup): String = json.encodeToString(backup)

    fun decode(text: String): FullBackup = json.decodeFromString(text)

    /** Comprueba si un texto parece una copia de seguridad completa de Canticora. */
    fun looksLikeBackup(text: String): Boolean = runCatching {
        require(decode(text).format in CANTICORA_BACKUP_FORMATS)
    }.isSuccess
}
