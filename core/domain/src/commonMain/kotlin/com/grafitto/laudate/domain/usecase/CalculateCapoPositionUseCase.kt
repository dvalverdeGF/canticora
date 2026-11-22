package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.harmonic.HarmonicTransposer
import com.grafitto.laudate.domain.model.music.PitchClass

data class CapoCalculationResult(
    val capoPosition: Int,
    val visualKey: PitchClass,
    val targetKey: PitchClass
)

/**
 * Caso de uso: Calcular la posición de capo óptima para adaptar una canción a digitaciones cómodas.
 */
class CalculateCapoPositionUseCase {

    operator fun invoke(targetKey: PitchClass, desiredVisualKey: PitchClass): CapoCalculationResult {
        val capo = HarmonicTransposer.calculateCapoPosition(targetKey, desiredVisualKey)
        return CapoCalculationResult(
            capoPosition = capo,
            visualKey = desiredVisualKey,
            targetKey = targetKey
        )
    }
}
