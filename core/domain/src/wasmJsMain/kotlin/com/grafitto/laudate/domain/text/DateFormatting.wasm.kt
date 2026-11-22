package com.grafitto.laudate.domain.text

/**
 * Fecha larga calculada con aritmética de calendario (algoritmo civil-from-days de Hinnant) sobre la
 * época Unix en **UTC**. La web no tiene `java.time`; el matiz de zona horaria solo puede desplazar
 * un día el encabezado de un documento exportado.
 */
actual fun formatLongDateEs(timestampMillis: Long): String = runCatching {
    val epochDay = floorDiv(timestampMillis, 86_400_000L)
    val (year, month, day) = civilFromDays(epochDay)
    longDateEs(year, month, day)
}.getOrDefault("")

actual fun formatIsoDate(timestampMillis: Long): String = runCatching {
    val (year, month, day) = civilFromDays(floorDiv(timestampMillis, 86_400_000L))
    buildString {
        append(year)
        append('-')
        if (month < 10) append('0')
        append(month)
        append('-')
        if (day < 10) append('0')
        append(day)
    }
}.getOrDefault("")

private fun floorDiv(value: Long, divisor: Long): Long {
    val quotient = value / divisor
    return if (value % divisor != 0L && (value xor divisor) < 0) quotient - 1 else quotient
}

/** Convierte un día desde 1970-01-01 en (año, mes, día) del calendario gregoriano proléptico. */
private fun civilFromDays(epochDay: Long): Triple<Int, Int, Int> {
    val z = epochDay + 719_468
    val era = (if (z >= 0) z else z - 146_096) / 146_097
    val dayOfEra = z - era * 146_097
    val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val year = yearOfEra + era * 400
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val monthPrime = (5 * dayOfYear + 2) / 153
    val day = dayOfYear - (153 * monthPrime + 2) / 5 + 1
    val month = if (monthPrime < 10) monthPrime + 3 else monthPrime - 9
    val gregorianYear = if (month <= 2) year + 1 else year
    return Triple(gregorianYear.toInt(), month.toInt(), day.toInt())
}
