package com.grafitto.laudate.domain.transfer

import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumArticulation
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.StrumRange
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.domain.platform.nowMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Identificador del formato de intercambio de patrones rítmicos de Canticora.
 * Va escrito dentro del archivo para reconocerlo sin depender del nombre ni del MIME.
 */
const val CANTICORA_RHYTHM_FORMAT: String = "canticora.rhythm"

/** Extensión de los ficheros de ritmos exportados. */
const val CANTICORA_RHYTHM_EXTENSION: String = "canticora-rhythm"

/** Tipo MIME propio del formato, para el selector del sistema y el «abrir con». */
const val CANTICORA_RHYTHM_MIME: String = "application/vnd.canticora.rhythm+json"

/**
 * Formatos aceptados al importar. Solo el identificador actual: la app aún no está en producción,
 * así que no se mantiene compatibilidad con el identificador antiguo `laudate.rhythm`.
 */
val CANTICORA_RHYTHM_FORMATS: Set<String> = setOf(CANTICORA_RHYTHM_FORMAT)

/**
 * Patrón rítmico exportado para **compartirlo con otro músico** (por WhatsApp, correo, etc.).
 *
 * Igual que el repertorio (`.canticora`), el patrón viaja como **datos estructurados**, no como
 * dibujo: quien lo recibe obtiene un patrón completo que puede editar, escuchar y reutilizar.
 *
 * La serialización es un reflejo directo del modelo de Dominio (`figure`, `direction`,
 * `articulation`, `range`), de modo que el receptor conserva **toda** la información del golpe:
 * no se pierde ni la articulación ni el registro de cuerdas.
 *
 * Al importarlo **nunca se sobrescribe** un patrón del receptor (ver
 * [com.grafitto.laudate.domain.usecase.ImportRhythmPatternsUseCase]).
 */
@Serializable
data class RhythmBundle(
    val format: String = CANTICORA_RHYTHM_FORMAT,
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAt: Long = nowMillis(),
    val appVersion: String = "desconocida",
    val patterns: List<BundleRhythmPattern> = emptyList()
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/** Un patrón de rasgueo con su compás y sus golpes. */
@Serializable
data class BundleRhythmPattern(
    val name: String,
    val numerator: Int,
    val denominator: Int,
    val events: List<BundleRhythmEvent> = emptyList()
)

/**
 * Un golpe del patrón.
 *
 * Los campos con valor por defecto (`DOWN`, `NONE`, `ALL`) se omiten al escribir, de modo que un
 * patrón sencillo queda compacto y legible.
 */
@Serializable
data class BundleRhythmEvent(
    val figure: String,
    val direction: String = StrumDirection.DOWN.name,
    val articulation: String = StrumArticulation.NONE.name,
    val range: String = StrumRange.ALL.name,
    /** Ligadura: la figura continúa la nota anterior. */
    val tied: Boolean = false
)

/**
 * Serializa y deserializa patrones rítmicos de Canticora.
 *
 * Kotlin puro: no depende de Android. La escritura del archivo y la elección del destino viven en
 * la UI (SAF / hoja de compartir).
 */
object RhythmBundleSerializer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Construye el bundle de una lista de patrones. */
    fun toBundle(patterns: List<RhythmPattern>, appVersion: String): RhythmBundle =
        RhythmBundle(
            exportedAt = nowMillis(),
            appVersion = appVersion,
            patterns = patterns.map { it.toBundlePattern() }
        )

    fun encode(bundle: RhythmBundle): String = json.encodeToString(bundle)

    /**
     * Deserializa un archivo de ritmos.
     *
     * Tolera nombres de enum desconocidos (un archivo de una versión más nueva no debe romper la
     * importación): los valores no reconocidos caen a sus valores por defecto.
     */
    fun decode(text: String): RhythmBundle = json.decodeFromString(text)

    /**
     * Indica si el texto declara ser un archivo de ritmos de Canticora.
     * Se usa para reconocer el archivo al recibirlo, sin necesidad de deserializarlo entero.
     */
    fun looksLikeRhythms(text: String): Boolean = runCatching {
        val trimmed = text.trim()
        trimmed.startsWith("{") && CANTICORA_RHYTHM_FORMATS.any { trimmed.contains("\"$it\"") }
    }.getOrDefault(false)

    private fun RhythmPattern.toBundlePattern(): BundleRhythmPattern = bundlePatternOf()
}

/**
 * Convierte un patrón de Dominio en su forma serializable.
 *
 * Es pública porque la **copia de seguridad** la reutiliza: así el formato de un ritmo es el mismo
 * tanto si viaja en un archivo de ritmos como dentro de un `.canticora` de copia completa.
 */
fun RhythmPattern.bundlePatternOf(): BundleRhythmPattern =
    BundleRhythmPattern(
        name = name,
        numerator = timeSignature.numerator,
        denominator = timeSignature.denominator,
        events = events.map { event ->
            BundleRhythmEvent(
                figure = event.figure.name,
                direction = event.direction.name,
                articulation = event.articulation.name,
                range = event.range.name,
                tied = event.isTied
            )
        }
    )

/** Convierte un patrón del bundle al modelo de Dominio. */
fun BundleRhythmPattern.toDomain(id: String): RhythmPattern = RhythmPattern(
    id = id,
    name = name.trim().ifEmpty { "Patrón importado" },
    timeSignature = TimeSignature(numerator, denominator),
    events = events.map { it.toDomainEvent() },
    isBuiltIn = false
)

/**
 * Convierte un golpe del bundle al modelo.
 *
 * Los campos de dirección, articulación y registro son tolerantes: un valor desconocido cae a su
 * valor por defecto en lugar de descartar el golpe. Una figura desconocida (por ejemplo una
 * retirada del modelo) se aproxima a la semicorchea, para no acortar el patrón.
 */
private fun BundleRhythmEvent.toDomainEvent(): RhythmEvent {
    val figure = RhythmFigure.entries.firstOrNull { it.name == this.figure }
        ?: legacyFigureOf(this.figure)
        ?: RhythmFigure.SIXTEENTH
    val direction = StrumDirection.entries.firstOrNull { it.name == this.direction } ?: StrumDirection.DOWN
    val articulation = StrumArticulation.entries.firstOrNull { it.name == this.articulation }
        ?: StrumArticulation.NONE
    val range = StrumRange.entries.firstOrNull { it.name == this.range } ?: StrumRange.ALL
    return RhythmEvent(
        figure = figure,
        direction = direction,
        articulation = articulation,
        range = range,
        isTied = tied
    )
}

/** Figuras retiradas del modelo, para poder seguir leyendo bundles antiguos. */
private fun legacyFigureOf(name: String): RhythmFigure? = when (name) {
    "DOTTED_SIXTEENTH" -> RhythmFigure.SIXTEENTH
    "DOTTED_SIXTEENTH_REST" -> RhythmFigure.SIXTEENTH_REST
    else -> null
}
