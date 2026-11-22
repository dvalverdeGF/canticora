package com.grafitto.laudate.ui.platform

import androidx.compose.runtime.Composable

/** En escritorio el sistema no apaga la pantalla: no hace falta hacer nada. */
@Composable
actual fun KeepScreenOn() = Unit
