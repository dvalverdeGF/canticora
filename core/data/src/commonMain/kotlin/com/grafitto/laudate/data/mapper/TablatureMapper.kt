package com.grafitto.laudate.data.mapper

import com.grafitto.laudate.data.local.entity.NumericTablatureEntity
import com.grafitto.laudate.data.local.nowMillis
import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.serializer.TablatureSerializer

/**
 * Mapeador Entity ↔ Domain para **tablaturas**.
 *
 * La tablatura completa se serializa a JSON en la columna `pattern_json` reutilizando
 * [TablatureSerializer] (el mismo formato que viaja en el intercambio). El resto de columnas
 * (`name`, `instrument`, `time_signature`, `resolution`, `written_key`, `origin`, marcas de tiempo)
 * se guardan desnormalizadas para poder listar y ordenar sin deserializar.
 */
object TablatureMapper {

    /**
     * Reconstruye la tablatura desde la fila. El `songId` de la entidad manda sobre el del JSON:
     * es la clave foránea real y evita inconsistencias si el JSON viniera de otro canto.
     */
    fun toDomain(entity: NumericTablatureEntity): Tablature {
        val decoded = try {
            TablatureSerializer.decode(entity.patternJson)
        } catch (_: Exception) {
            // Fila corrupta: se construye una tablatura mínima para no romper la pantalla.
            Tablature(
                id = entity.id,
                songId = entity.songId,
                title = entity.name,
                instrument = entity.instrument,
                tuning = com.grafitto.laudate.domain.model.tablature.Tuning.GUITAR,
                time = com.grafitto.laudate.domain.model.score.ScoreTimeSignature("4", 4)
            )
        }
        return decoded.copy(
            id = entity.id,
            songId = entity.songId,
            title = entity.name.ifBlank { decoded.title },
            instrument = entity.instrument.ifBlank { decoded.instrument }
        )
    }

    fun toEntity(domain: Tablature, createdAt: Long? = null): NumericTablatureEntity = NumericTablatureEntity(
        id = domain.id,
        songId = domain.songId,
        patternJson = TablatureSerializer.encode(domain),
        tuning = tuningText(domain),
        name = domain.title,
        instrument = domain.instrument,
        timeSignature = domain.time.toString(),
        resolution = domain.resolution.name,
        writtenKey = domain.writtenKey?.let { "${it.fifths};${it.mode}" },
        origin = domain.origin.name,
        createdAt = createdAt ?: nowMillis(),
        updatedAt = nowMillis()
    )

    /**
     * Afinación en texto para lectura rápida/debug (p. ej. `EADGBE`). Se escribe de la cuerda más
     * **grave** a la más aguda, que es la convención habitual (EADGBE = guitarra estándar).
     */
    private fun tuningText(tablature: Tablature): String =
        tablature.tuning.strings.joinToString("") { it.pitch.step.uppercaseChar().toString() }

    /** Nombre del origen tal y como viaja en la columna `origin`. */
    fun originColumn(origin: TabOrigin): String = origin.name
}
