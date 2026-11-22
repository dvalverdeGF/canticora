package com.grafitto.laudate.domain.transfer

import com.grafitto.laudate.domain.backup.BackupAttachment
import com.grafitto.laudate.domain.platform.nowMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Identificador del formato de **paquete de canción** de Canticora: un ZIP con un solo canto, su
 * manifiesto (`song.json`) y, si los lleva, sus adjuntos.
 *
 * Va escrito dentro del archivo para poder reconocerlo sin depender del nombre ni del MIME.
 * Ver ADR-017.
 */
const val CANTICORA_SONG_FORMAT: String = "canticora.song"

/**
 * Extensión de los paquetes de canción.
 *
 * Es la misma que la de los repertorios y la de las copias de seguridad (`.canticora`): las tres
 * se distinguen por el **contenido** (el campo `format` del manifiesto y la primera entrada del
 * ZIP), no por la extensión, igual que ya ocurría con `.canticora` de repertorio y de copia.
 */
const val CANTICORA_SONG_EXTENSION: String = "canticora"

/** Tipo MIME del paquete de canción (ZIP con el manifiesto del canto y sus adjuntos). */
const val CANTICORA_SONG_MIME: String = "application/vnd.canticora.song+zip"

/**
 * Formatos aceptados al importar. Solo el identificador actual: la app no mantiene compatibilidad
 * con identificadores de versiones que nunca se publicaron.
 */
val CANTICORA_SONG_FORMATS: Set<String> = setOf(CANTICORA_SONG_FORMAT)

/**
 * Nombre de la entrada del ZIP con el manifiesto del paquete.
 *
 * Se escribe **siempre la primera**: el detector de archivos reconoce el paquete por la primera
 * entrada del ZIP sin necesidad de descomprimirlo (ver `LaudateFileDetector.zipFirstEntryName`).
 */
const val SONG_DATA_ENTRY: String = "song.json"

/**
 * Paquete de **una sola canción** con sus adjuntos, listo para compartir por WhatsApp, correo o
 * cualquier otra vía.
 *
 * Es el hermano pequeño de la copia de seguridad (`FullBackup`, ADR-010): donde aquella vuelca la
 * biblioteca entera, este lleva **un canto** y **solo sus adjuntos**. Se apoya en la misma
 * infraestructura de ZIP (`BackupArchiveStore`) y en el mismo índice de adjuntos
 * (`BackupAttachment`), de modo que no hay dos implementaciones de «empaquetar ficheros» que
 * mantener en sincronía.
 *
 * A diferencia del repertorio `.canticora` (`CelebrationBundle`), que lleva los cantos como texto
 * ChordPro pero **nunca** sus adjuntos, el paquete de canción **sí viaja con los ficheros**.
 */
@Serializable
data class SongPackage(
    val format: String = CANTICORA_SONG_FORMAT,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAt: Long = nowMillis(),
    val appVersion: String = "desconocida",
    /** Cabecera del canto y su texto ChordPro completo. */
    val song: SongPackageSong,
    /** Índice de los adjuntos incluidos en el ZIP. */
    val attachments: List<BackupAttachment> = emptyList(),
    /**
     * **Tablaturas del canto**, ya serializadas ([BundleTablature]).
     *
     * Campo **aditivo y con valor por defecto**: un paquete creado antes de que existiera se
     * importa igual, solo que sin tablaturas. Van aquí y no como adjuntos porque son **datos
     * musicales estructurados** del canto (ADR-017), no ficheros sueltos: al importar se
     * reasocian al canto de destino y se pueden volver a editar.
     */
    val tablatures: List<BundleTablature> = emptyList()
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/**
 * El canto que viaja en el paquete.
 *
 * No lleva la letra en estructurado (secciones y anclajes): viaja como **texto ChordPro**, que es
 * el idioma franco de Canticora y de otros cancioneros electrónicos. Los metadatos que **no** viven
 * en el texto (momentos y tiempos litúrgicos) sí se declaran explícitamente, porque de lo contrario
 * se perderían al compartir.
 */
@Serializable
data class SongPackageSong(
    val title: String,
    val number: String? = null,
    val author: String? = null,
    val originalKey: String,
    /** Huella de contenido ([com.grafitto.laudate.domain.hashing.SongContentHasher]), ADR-009. */
    val contentHash: String? = null,
    /** Momentos litúrgicos con los que viaja el canto (no forman parte del texto ChordPro). */
    val defaultLiturgicalMoments: List<String> = emptyList(),
    /** Tiempos litúrgicos con los que viaja el canto. */
    val defaultLiturgicalTimes: List<String> = emptyList(),
    /** El canto completo en formato ChordPro. */
    val chordPro: String
)

/**
 * Serializa y deserializa el manifiesto de un paquete de canción.
 *
 * Kotlin puro: la lectura/escritura del ZIP vive en la capa de datos y la elección del destino en
 * la UI (SAF en Android, diálogo nativo en escritorio).
 */
object SongPackageSerializer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(packageFile: SongPackage): String = json.encodeToString(packageFile)

    /**
     * Deserializa el manifiesto de un paquete de canción.
     *
     * @throws IllegalArgumentException si el archivo no es un paquete de Canticora, con un mensaje
     *   apto para mostrar al usuario.
     */
    fun decode(text: String): SongPackage {
        val packageFile = runCatching { json.decodeFromString<SongPackage>(text) }
            .getOrElse { error ->
                throw IllegalArgumentException(
                    "El archivo no es un canto de Canticora válido: " +
                        (error.message ?: "formato desconocido")
                )
            }
        require(packageFile.format in CANTICORA_SONG_FORMATS) {
            "El archivo pertenece a otro formato (${packageFile.format}); no es un canto suelto"
        }
        require(packageFile.formatVersion <= SongPackage.CURRENT_FORMAT_VERSION) {
            "El archivo se creó con una versión más reciente de Canticora " +
                "(formato v${packageFile.formatVersion}); actualiza la aplicación para abrirlo"
        }
        return packageFile
    }

    /** Comprueba si un texto parece el manifiesto de un paquete de canción, sin lanzar excepción. */
    fun looksLikeSongPackage(text: String): Boolean =
        runCatching { decode(text) }.isSuccess
}
