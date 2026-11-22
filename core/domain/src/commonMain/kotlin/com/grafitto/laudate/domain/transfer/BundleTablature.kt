package com.grafitto.laudate.domain.transfer

import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import kotlinx.serialization.Serializable

/**
 * Tablatura transportable dentro de una copia de seguridad o de un bundle de repertorio
 * (ADR-017, *Feature T*). Campo **aditivo y con valor por defecto** en los contenedores, así que
 * un archivo antiguo (sin tablaturas) se importa sin errores.
 *
 * El contenido viaja ya **serializado a JSON** ([TablatureSerializer]): es exactamente el mismo
 * formato que se guarda en la base de datos y que se comparte como fichero suelto, de modo que la
 * tablatura se reconstruye sin pérdida en el otro dispositivo.
 */
@Serializable
data class BundleTablature(
    val id: String,
    /** Canto al que pertenece. Necesario para reasociarla al importar (se empareja por id). */
    val songId: String,
    val name: String,
    val instrument: String,
    /** Tablatura completa (modelo de dominio) serializada a JSON. */
    val content: String
)

/** Convierte una tablatura de dominio en su forma transportable. */
fun Tablature.toBundleTablature(): BundleTablature = BundleTablature(
    id = id,
    songId = songId,
    name = title,
    instrument = instrument,
    content = TablatureSerializer.encode(this)
)

/** Reconstruye la tablatura desde su forma transportable. `null` si el contenido es ilegible. */
fun BundleTablature.toDomain(): Tablature? = runCatching {
    val decoded = TablatureSerializer.decode(content)
    decoded.copy(id = id, songId = songId, title = name, instrument = instrument)
}.getOrNull()
