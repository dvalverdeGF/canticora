package com.grafitto.laudate.data.mapper

import com.grafitto.laudate.data.local.entity.RhythmPatternEntity
import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumArticulation
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.StrumRange
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class RhythmEventDto(
    val figure: String,
    val direction: String,
    val accent: Boolean = false,
    val articulation: String? = null,
    /** Registro de cuerdas (ALL/BASS/TREBLE). Ausente en datos previos: equivale a ALL. */
    val range: String? = null,
    /** Ligadura: la figura continúa la nota anterior. Ausente en datos previos: sin ligadura. */
    val tied: Boolean = false
)

/**
 * Mapeador Entity ↔ Domain para patrones rítmicos.
 * La lista de eventos se serializa a JSON en la columna `events_json`.
 */
object RhythmPatternMapper {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Figuras retiradas del modelo. Se siguen leyendo para no perder patrones guardados con
     * versiones anteriores: se aproximan a la figura representable más cercana.
     */
    private val legacyFigures = mapOf(
        "DOTTED_SIXTEENTH" to RhythmFigure.SIXTEENTH,
        "DOTTED_SIXTEENTH_REST" to RhythmFigure.SIXTEENTH_REST
    )

    /**
     * Resuelve una figura por nombre. Si el nombre es desconocido se recurre a la semicorchea en
     * lugar de descartar el evento: descartarlo acortaría el patrón y lo dejaría inválido.
     */
    private fun figureOf(name: String): RhythmFigure =
        RhythmFigure.entries.firstOrNull { it.name == name }
            ?: legacyFigures[name]
            ?: RhythmFigure.SIXTEENTH

    fun toDomain(entity: RhythmPatternEntity): RhythmPattern {
        val events = try {
            json.decodeFromString<List<RhythmEventDto>>(entity.eventsJson).mapNotNull { dto ->
                val figure = figureOf(dto.figure)
                val direction = StrumDirection.entries.firstOrNull { it.name == dto.direction } ?: StrumDirection.DOWN
                // Compatibilidad con datos previos: `accent = true` equivale a ACCENT.
                val articulation = dto.articulation
                    ?.let { name -> StrumArticulation.entries.firstOrNull { it.name == name } }
                    ?: if (dto.accent) StrumArticulation.ACCENT else StrumArticulation.NONE
                // Compatibilidad con datos previos: sin registro explícito, todas las cuerdas.
                val range = dto.range
                    ?.let { name -> StrumRange.entries.firstOrNull { it.name == name } }
                    ?: StrumRange.ALL
                RhythmEvent(
                    figure = figure,
                    direction = direction,
                    articulation = articulation,
                    range = range,
                    isTied = dto.tied
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

        return RhythmPattern(
            id = entity.id,
            name = entity.name,
            timeSignature = TimeSignature(entity.numerator, entity.denominator),
            events = events,
            isBuiltIn = entity.isBuiltIn,
            createdAt = entity.createdAt
        )
    }

    fun toEntity(domain: RhythmPattern): RhythmPatternEntity {
        val eventsDto = domain.events.map { event ->
            RhythmEventDto(
                figure = event.figure.name,
                direction = event.direction.name,
                accent = event.isAccent,
                articulation = event.articulation.name,
                range = event.range.name,
                tied = event.isTied
            )
        }
        return RhythmPatternEntity(
            id = domain.id,
            name = domain.name,
            numerator = domain.timeSignature.numerator,
            denominator = domain.timeSignature.denominator,
            eventsJson = json.encodeToString(eventsDto),
            isBuiltIn = domain.isBuiltIn,
            timeSignature = domain.timeSignature.toString(),
            createdAt = domain.createdAt
        )
    }
}
