package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.platform.ioDispatcher
import com.grafitto.laudate.domain.repository.RhythmPatternRepository
import com.grafitto.laudate.domain.transfer.RhythmBundleSerializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Construye el paquete de intercambio de uno o varios patrones rítmicos, listo para guardarlo como
 * archivo o compartirlo por texto.
 *
 * Es Dominio puro: no escribe ficheros ni elige destinos (eso corresponde a la UI con SAF).
 */
class ExportRhythmPatternsUseCase(
    private val rhythmPatternRepository: RhythmPatternRepository,
    private val appVersion: String
) {

    /**
     * Exporta **un patrón concreto** por su identificador.
     * Devuelve `null` si el patrón ya no existe.
     */
    suspend operator fun invoke(patternId: String): String? = withContext(ioDispatcher) {
        val pattern = rhythmPatternRepository.getAllPatterns().first().firstOrNull { it.id == patternId }
            ?: return@withContext null
        RhythmBundleSerializer.encode(RhythmBundleSerializer.toBundle(listOf(pattern), appVersion))
    }

    /**
     * Exporta la **colección completa** de patrones.
     *
     * Los integrados no se incluyen: ya viajan con la aplicación y solo llenarían el archivo. Se
     * exporta lo que el usuario ha creado o editado, que es lo que no puede reconstruir de otro modo.
     */
    suspend operator fun invoke(): String = withContext(ioDispatcher) {
        val custom = rhythmPatternRepository.getCustomPatterns().first()
        RhythmBundleSerializer.encode(RhythmBundleSerializer.toBundle(custom, appVersion))
    }
}
