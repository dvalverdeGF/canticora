package com.grafitto.laudate.domain.text

import kotlin.math.abs
import kotlin.math.round

/**
 * Formatea un número con **un decimal** y punto como separador (`1.5`, `12.0`).
 *
 * Sustituye a `String.format(Locale, "%.1f", value)`, que no existe fuera de la JVM. El redondeo
 * es hacia arriba en la mitad (el mismo que `%.1f`): 2.25 → «2.3».
 */
internal fun formatOneDecimal(value: Double): String {
    val scaled = round(value * 10.0).toLong()
    val sign = if (scaled < 0) "-" else ""
    val absolute = abs(scaled)
    return "$sign${absolute / 10}.${absolute % 10}"
}
