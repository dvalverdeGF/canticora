package com.grafitto.laudate.domain.text


/**
 * Fecha larga en español para los documentos exportados: «21 de noviembre de 2026».
 *
 * Es `expect` porque la JVM lo resuelve con `java.time` (zona horaria del sistema) y la web, donde
 * no hay `java.time`, con aritmética de calendario en UTC.
 */
expect fun formatLongDateEs(timestampMillis: Long): String

/**
 * Fecha en formato `AAAA-MM-DD`, para nombres de fichero (copias de seguridad, exportaciones).
 *
 * Igual que [formatLongDateEs], es `expect`: la JVM usa el calendario del sistema y la web calcula
 * en UTC.
 */
expect fun formatIsoDate(timestampMillis: Long): String

/** Nombres de los meses en español (índice 0 = enero). */
internal val SPANISH_MONTHS = arrayOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"
)

/** Compone «D de MES de AAAA» a partir de sus partes. */
internal fun longDateEs(year: Int, month: Int, day: Int): String =
    "$day de ${SPANISH_MONTHS[month - 1]} de $year"
