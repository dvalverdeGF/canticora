package com.grafitto.laudate.domain.backup

import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.celebration.CelebrationItem

/** Reconstruye un modelo de Dominio de celebración desde el manifiesto de la copia de seguridad. */
fun BackupCelebration.toDomain(): Celebration = Celebration(
    id = id,
    name = name,
    dateTimestamp = dateTimestamp,
    description = description,
    location = location,
    celebrationType = celebrationType,
    createdAt = createdAt,
    updatedAt = updatedAt,
    items = items.map { it.toDomain() }
)

fun BackupCelebrationItem.toDomain(): CelebrationItem = CelebrationItem(
    id = id,
    celebrationId = celebrationId,
    songId = songId,
    liturgicalMoment = liturgicalMoment,
    displayOrder = displayOrder,
    overrideKey = overrideKey,
    overrideCapo = overrideCapo,
    customNotes = customNotes
)
