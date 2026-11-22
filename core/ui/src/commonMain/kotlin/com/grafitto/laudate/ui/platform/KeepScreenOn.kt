package com.grafitto.laudate.ui.platform

import androidx.compose.runtime.Composable

/**
 * Mantiene la pantalla encendida mientras el composable está en pantalla (RF-302/RNF-301).
 *
 * Android: marca la `View` con `keepScreenOn`. Escritorio: no hace nada (el sistema no apaga
 * la pantalla como un móvil). Ver ADR-013.
 */
@Composable
expect fun KeepScreenOn()
