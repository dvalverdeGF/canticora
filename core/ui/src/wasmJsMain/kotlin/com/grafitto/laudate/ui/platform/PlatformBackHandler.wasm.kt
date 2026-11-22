package com.grafitto.laudate.ui.platform

import androidx.compose.runtime.Composable

/**
 * En la web el «atrás» pertenece al historial del navegador: interceptarlo y llamar a `onBack`
 * dejaría al usuario sin poder salir de la app con el gesto del sistema, y rompería el botón
 * «atrás» del propio navegador. La navegación interna de Canticora se resuelve con la barra de
 * navegación de la interfaz. Ver ADR-023.
 */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
