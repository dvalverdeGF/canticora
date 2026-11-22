package com.grafitto.laudate.domain.model

/**
 * Identidad nominal del producto (marca visible).
 *
 * Vive en el Dominio porque lo consumen las tres capas: el manual embebido (`:core:domain`),
 * los textos de UI (`:core:ui`) y los cascarones de plataforma (`:app` y `:desktopApp`).
 * Centralizarlo evita que el nombre de mercado se desincronice entre pantallas.
 *
 * ⚠️ Esto es **marca**, no identidad técnica: el paquete Kotlin, el `applicationId`, el
 * `Name`/`Publisher` del MSIX y la extensión `.canticora` **no** se derivan de aquí. Ver ADR-014.
 */
object LaudateBrand {

    /** Nombre corto: identificador de marca, el que cabe en menús y títulos. */
    const val NAME = "Canticora"

    /** Lema del producto, sin el nombre. */
    const val TAGLINE = "Crea tu repertorio. Interpreta tu música"

    /** Nombre de mercado completo, tal como se publica en Google Play y Microsoft Store. */
    const val FULL_NAME = "$NAME — $TAGLINE"
}
