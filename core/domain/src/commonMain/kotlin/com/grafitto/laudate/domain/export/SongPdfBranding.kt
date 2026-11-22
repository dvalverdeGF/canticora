package com.grafitto.laudate.domain.export

/**
 * Paleta del PDF (colores ARGB), derivada de los tonos **Material 3 · Static · Baseline** que usa
 * la app, para que el documento impreso mantenga la identidad visual de Canticora.
 *
 * Se expresa como datos planos (`Int` ARGB) para que el dominio no dependa de Android ni de
 * Compose; el pintor (`SongPdfExporter`) los aplica tal cual.
 */
data class SongPdfBranding(
    /** Color de marca (títulos y encabezados) — Primary 40. */
    val primaryArgb: Int = 0xFF6750A4.toInt(),
    /** Color del texto principal — Neutral 10. */
    val textArgb: Int = 0xFF1D1B20.toInt(),
    /** Color del texto secundario (metadatos, comentarios, pie) — Neutral Variant 30. */
    val mutedArgb: Int = 0xFF49454F.toInt(),
    /** Color de las líneas separadoras — Neutral Variant 80. */
    val ruleArgb: Int = 0xFFCAC4D0.toInt(),
    /** Fondo de la banda de marca de la cabecera — Primary 90. */
    val brandBandArgb: Int = 0xFFEADDFF.toInt(),
    /** Color del texto sobre la banda de marca — Primary 10. */
    val onBrandBandArgb: Int = 0xFF21005D.toInt()
)
