package com.grafitto.laudate.domain.model.celebration

import com.grafitto.laudate.domain.platform.nowMillis

/**
 * Elemento de canto asignado a una celebración litúrgica.
 */
data class CelebrationItem(
    val id: String,
    val celebrationId: String,
    val songId: String,
    val liturgicalMoment: String,
    val displayOrder: Int,
    val overrideKey: String? = null,
    val overrideCapo: Int? = null,
    val customNotes: String? = null
)

/**
 * Modelo inmutable de Celebración / Lista de Misa en el Dominio.
 */
data class Celebration(
    val id: String,
    val name: String,
    val dateTimestamp: Long,
    val description: String? = null,
    val location: String? = null,
    val celebrationType: String = "Misa",
    val createdAt: Long = nowMillis(),
    val updatedAt: Long = nowMillis(),
    val items: List<CelebrationItem> = emptyList()
)
