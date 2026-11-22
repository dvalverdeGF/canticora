package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.repository.SongRepository

/**
 * Coincidencia entre una canción candidata y otra ya existente en el catálogo,
 * con la misma huella de contenido.
 */
data class DuplicateSongMatch(
    /** La canción que ya está guardada. */
    val existing: Song,
    /** La canción que se pretendía guardar/importar. */
    val candidate: Song,
    /** Huella compartida por ambas. */
    val contentHash: String
)

/**
 * Caso de uso: comprobar si una canción ya existe en el catálogo comparando su **huella de
 * contenido** (no su `id`).
 *
 * Escenario típico: alguien edita un canto y lo comparte en ChordPro (WhatsApp, Telegram,
 * correo); al recibirlo e importarlo, la app detecta que ya lo tiene y puede ofrecer
 * **sobrescribir** la versión local o **crear una nueva** sin perder la existente.
 *
 * La decisión es siempre del usuario: este caso de uso solo informa.
 */
class DetectDuplicateSongUseCase(
    private val songRepository: SongRepository
) {

    /**
     * Devuelve la coincidencia si el catálogo ya contiene una canción con el mismo contenido,
     * o `null` si es nueva.
     *
     * Si la [candidate] ya existe con el **mismo `id`** (es decir, se está editando la propia
     * canción), no se considera duplicado: no tiene sentido preguntar por sobrescribirse a sí misma.
     */
    suspend operator fun invoke(candidate: Song): DuplicateSongMatch? {
        val hash = SongContentHasher.hash(candidate)
        val existing = songRepository.findSongByContentHash(hash) ?: return null
        if (existing.id == candidate.id) return null
        return DuplicateSongMatch(existing = existing, candidate = candidate, contentHash = hash)
    }

    /** Comprueba una huella ya calculada (p. ej. la del texto ChordPro pegado). */
    suspend fun byHash(hash: String, candidate: Song): DuplicateSongMatch? {
        val existing = songRepository.findSongByContentHash(hash) ?: return null
        if (existing.id == candidate.id) return null
        return DuplicateSongMatch(existing = existing, candidate = candidate, contentHash = hash)
    }
}
