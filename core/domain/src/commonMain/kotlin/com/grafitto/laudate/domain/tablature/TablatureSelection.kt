package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.tablature.TabMeasure

/**
 * **Selección de compases** del editor (E1, ADR-017 §13). Es un **rango de índices de compás**
 * (`IntRange`), como en MuseScore: con él se borran compases en bloque, se copian y se duplican.
 *
 * Kotlin puro: no conoce la UI, así que el pintor y los tests comparten exactamente la misma lógica.
 */
data class TablatureSelection(val range: IntRange) {
    /** Nº de compases seleccionados. */
    val size: Int get() = (range.last - range.first + 1).coerceAtLeast(0)

    /** Indica si el compás [measureIndex] está dentro de la selección. */
    fun contains(measureIndex: Int): Boolean = measureIndex in range

    /** Normaliza los índices a un rango válido `0..lastIndex` (una selección fuera de rango se vacía). */
    fun clamped(measureCount: Int): TablatureSelection? {
        if (measureCount <= 0) return null
        val from = range.first.coerceIn(0, measureCount - 1)
        val to = range.last.coerceIn(0, measureCount - 1)
        return TablatureSelection(if (from <= to) from..to else to..from)
    }
}

/**
 * **Portapapeles** en memoria del editor (E3): un bloque de compases copiados o cortados, listo para
 * pegar. Se guarda fuera del estado persistente (no viaja al JSON ni a la base de datos).
 */
data class TablatureClipboard(val measures: List<TabMeasure>, val isCut: Boolean = false) {
    val isEmpty: Boolean get() = measures.isEmpty()
    val measureCount: Int get() = measures.size
}
