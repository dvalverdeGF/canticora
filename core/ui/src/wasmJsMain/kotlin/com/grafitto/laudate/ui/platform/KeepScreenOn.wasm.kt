package com.grafitto.laudate.ui.platform

import androidx.compose.runtime.Composable

/**
 * En la web no se hace nada todavía.
 *
 * Existe la *Screen Wake Lock API*, pero requiere permiso explícito, es asíncrona y no está
 * disponible en todos los navegadores; dejarla sin efecto degrada a «la pantalla se apaga según el
 * sistema», que es el comportamiento por defecto del navegador y no rompe nada. Ver ADR-023.
 */
@Composable
actual fun KeepScreenOn() = Unit
