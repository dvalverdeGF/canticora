package com.grafitto.laudate.domain.transfer

import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.celebration.CelebrationItem
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.platform.nowMillis
import com.grafitto.laudate.domain.serializer.ChordProSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Identificador del formato de intercambio de repertorios de Canticora.
 * Va escrito dentro del archivo para poder reconocerlo sin depender del nombre ni del MIME.
 */
const val CANTICORA_EVENT_FORMAT: String = "canticora.event"

/** Extensión de archivo de los repertorios exportados. */
const val CANTICORA_EVENT_EXTENSION: String = "canticora"

/** Tipo MIME propio del formato, para el selector del sistema y el «abrir con». */
const val CANTICORA_EVENT_MIME: String = "application/vnd.canticora.event+json"

/**
 * Formatos aceptados al importar. Solo el identificador actual: la app aún no está en producción,
 * así que no se mantiene compatibilidad con el identificador antiguo `laudate.event`.
 */
val CANTICORA_EVENT_FORMATS: Set<String> = setOf(CANTICORA_EVENT_FORMAT)

/**
 * Repertorio de misa/evento exportado como **archivo único** para compartirlo por WhatsApp,
 * Telegram, correo, etc.
 *
 * A diferencia del respaldo completo (`BackupSerializer`, que vuelca la base entera con anclajes
 * de acorde estructurados), este formato:
 *  - contiene **una sola celebración**;
 *  - lleva cada canto como **texto ChordPro**, que es el idioma franco de la app y de otros
 *    cancioneros electrónicos;
 *  - incluye la **huella de contenido** de cada canto, para que quien lo reciba pueda reconocer
 *    si ya lo tiene sin necesidad de parsear ni comparar la letra.
 *
 * Al importarlo **nunca se sobrescribe** una canción del receptor (ver
 * `ImportCelebrationBundleUseCase`).
 */
@Serializable
data class CelebrationBundle(
    val format: String = CANTICORA_EVENT_FORMAT,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAt: Long = nowMillis(),
    val appVersion: String = "desconocida",
    val celebration: BundleCelebration,
    val songs: List<BundleSong> = emptyList()
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/** Cabecera y orden de cantos de la celebración, sin los datos pesados de cada canción. */
@Serializable
data class BundleCelebration(
    val name: String,
    val type: String,
    val dateTimestamp: Long,
    val description: String? = null,
    val location: String? = null,
    val items: List<BundleItem> = emptyList()
)

/**
 * Canto dentro del repertorio: momento litúrgico, posición (índice en [CelebrationBundle.songs])
 * y los ajustes que el director aplicó solo para esa celebración (tonalidad, capo, notas).
 */
@Serializable
data class BundleItem(
    val songIndex: Int,
    val moment: String,
    val overrideKey: String? = null,
    val overrideCapo: Int? = null,
    val customNotes: String? = null
)

/** Canto con sus datos mínimos de identificación y su texto ChordPro completo. */
@Serializable
data class BundleSong(
    val title: String,
    val number: String? = null,
    val author: String? = null,
    val originalKey: String,
    /** Huella de contenido ([SongContentHasher]), para reconocer cantos ya presentes. */
    val contentHash: String? = null,
    /** Canto completo en formato ChordPro. */
    val chordPro: String,
    /**
     * Tablaturas del canto (ADR-017, *Feature T*), como JSON serializado ([TablatureSerializer]).
     * Campo **aditivo y con valor por defecto**: un repertorio antiguo no trae tablaturas.
     */
    val tablatures: List<String> = emptyList()
)

/**
 * Serializa y deserializa repertorios de Canticora.
 *
 * Kotlin puro: no depende de Android. La lectura/escritura de archivos vive en la capa de datos
 * y la elección del destino en la UI (SAF).
 */
object CelebrationBundleSerializer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Construye el bundle de una celebración.
     *
     * Los cantos se referencian por índice desde los elementos de la celebración, de modo que un
     * canto usado dos veces (p. ej. el mismo Aleluya) se guarda una sola vez.
     */
    fun toBundle(
        celebration: Celebration,
        songsById: Map<String, Song>,
        appVersion: String,
        tablaturesBySongId: Map<String, List<com.grafitto.laudate.domain.model.tablature.Tablature>> = emptyMap()
    ): CelebrationBundle {
        // Índice estable por id de canción para deduplicar el contenido.
        val songIndexById = mutableMapOf<String, Int>()
        val bundleSongs = mutableListOf<BundleSong>()
        val bundleItems = mutableListOf<BundleItem>()

        celebration.items.forEach { item ->
            val song = songsById[item.songId] ?: return@forEach
            val index = songIndexById.getOrPut(song.id) {
                bundleSongs += BundleSong(
                    title = song.title,
                    number = song.number,
                    author = song.author,
                    originalKey = song.originalKey,
                    contentHash = SongContentHasher.hash(song),
                    chordPro = ChordProSerializer.serialize(song),
                    tablatures = tablaturesBySongId[song.id].orEmpty()
                        .map { com.grafitto.laudate.domain.serializer.TablatureSerializer.encode(it) }
                )
                bundleSongs.lastIndex
            }
            bundleItems += BundleItem(
                songIndex = index,
                moment = item.liturgicalMoment,
                overrideKey = item.overrideKey,
                overrideCapo = item.overrideCapo,
                customNotes = item.customNotes
            )
        }

        return CelebrationBundle(
            exportedAt = nowMillis(),
            appVersion = appVersion,
            celebration = BundleCelebration(
                name = celebration.name,
                type = celebration.celebrationType,
                dateTimestamp = celebration.dateTimestamp,
                description = celebration.description,
                location = celebration.location,
                items = bundleItems
            ),
            songs = bundleSongs
        )
    }

    fun serialize(bundle: CelebrationBundle): String = json.encodeToString(bundle)

    /**
     * Deserializa un archivo de repertorio.
     *
     * @throws IllegalArgumentException si el archivo no es un repertorio de Canticora, con un mensaje
     *   apto para mostrar al usuario.
     */
    fun deserialize(text: String): CelebrationBundle {
        val bundle = runCatching { json.decodeFromString<CelebrationBundle>(text) }
            .getOrElse { error ->
                throw IllegalArgumentException(
                    "El archivo no es un repertorio de Canticora válido: " +
                        (error.message ?: "formato desconocido")
                )
            }
        require(bundle.format in CANTICORA_EVENT_FORMATS) {
            "El archivo pertenece a otro formato (${bundle.format})"
        }
        require(bundle.formatVersion <= CelebrationBundle.CURRENT_FORMAT_VERSION) {
            "El archivo se creó con una versión más reciente de Canticora " +
                "(formato v${bundle.formatVersion}); actualiza la aplicación para abrirlo"
        }
        return bundle
    }

    /** Comprueba si un texto parece un repertorio de Canticora, sin lanzar excepción. */
    fun looksLikeBundle(text: String): Boolean =
        runCatching { deserialize(text) }.isSuccess
}

/** Convierte los elementos del bundle en elementos de celebración resueltos a ids locales. */
fun BundleCelebration.toItems(celebrationId: String, songIdsByIndex: Map<Int, String>): List<CelebrationItem> =
    items.mapIndexedNotNull { order, item ->
        val songId = songIdsByIndex[item.songIndex] ?: return@mapIndexedNotNull null
        CelebrationItem(
            id = newId(),
            celebrationId = celebrationId,
            songId = songId,
            liturgicalMoment = item.moment,
            displayOrder = order,
            overrideKey = item.overrideKey,
            overrideCapo = item.overrideCapo,
            customNotes = item.customNotes
        )
    }
