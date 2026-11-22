package com.grafitto.laudate.ui.platform

import kotlin.js.js

/**
 * ¿La web se está usando desde un dispositivo táctil?
 *
 * Se pregunta al navegador por el tipo de puntero principal (`pointer: coarse`) y por el número de
 * puntos táctiles. Así una tablet o un móvil ven el teclado numérico de traste (ADR-020) y un
 * portátil con ratón no, igual que en escritorio. Ante cualquier fallo de la consulta se asume
 * táctil: es la opción que no esconde funcionalidad.
 */
actual fun isTouchPlatform(): Boolean = queryCoarsePointer()

private fun queryCoarsePointer(): Boolean = js(
    "(typeof window !== 'undefined' && window.matchMedia && " +
        "window.matchMedia('(pointer: coarse)').matches) || " +
        "(typeof navigator !== 'undefined' && navigator.maxTouchPoints > 0)"
)
