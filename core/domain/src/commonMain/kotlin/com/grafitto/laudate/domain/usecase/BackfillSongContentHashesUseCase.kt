package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.repository.SongRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Caso de uso: calcular y persistir la huella de contenido de las canciones que aún no la tienen.
 *
 * Las canciones creadas en versiones anteriores a la incorporación de la huella (o restauradas
 * desde un respaldo antiguo) no la traen. En lugar de reescribir el esquema con un valor
 * calculado en la migración —imposible en SQL puro: la huella es un SHA-256 del ChordPro, no un
 * simple hash de columnas—, la app la **rellena en segundo plano** al arrancar.
 *
 * Es **idempotente y barato de comprobar**: sin canciones pendientes no hace ningún trabajo.
 * El cálculo corre en [computeDispatcher] porque serializar y hashear en serie no debe tocar el
 * hilo principal.
 */
class BackfillSongContentHashesUseCase(
    private val songRepository: SongRepository,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    /**
     * Rellena las huellas pendientes.
     *
     * @return cuántas canciones se actualizaron (0 si ya estaban todas).
     */
    suspend operator fun invoke(): Int {
        val pending = songRepository.getSongsMissingContentHash()
        if (pending.isEmpty()) return 0

        var updated = 0
        pending.forEach { song ->
            val hash = SongContentHasher.hash(song)
            songRepository.updateContentHash(song.id, hash)
            updated++
        }
        return updated
    }
}
