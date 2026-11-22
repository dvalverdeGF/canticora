package com.grafitto.laudate.ui.platform

/**
 * En escritorio (JVM) la plataforma **no** es táctil: el teclado numérico de traste no se ofrece en la
 * barra, porque en MuseScore lo pone el sistema operativo (ADR-020).
 */
actual fun isTouchPlatform(): Boolean = false
