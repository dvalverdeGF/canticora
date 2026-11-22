package com.grafitto.laudate.ui.platform

import androidx.compose.runtime.Composable

/** En escritorio no hay gesto/botón "atrás" del sistema: no hace nada. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
