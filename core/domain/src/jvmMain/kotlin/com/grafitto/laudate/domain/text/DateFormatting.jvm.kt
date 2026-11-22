package com.grafitto.laudate.domain.text

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SPANISH_LONG_DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es"))

actual fun formatLongDateEs(timestampMillis: Long): String = runCatching {
    Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(SPANISH_LONG_DATE)
}.getOrDefault("")

actual fun formatIsoDate(timestampMillis: Long): String = runCatching {
    Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()
}.getOrDefault("")
