package com.grafitto.laudate.ui.platform

import androidx.compose.runtime.Composable

/**
 * Maneja el gesto/botón "atrás" de forma multiplataforma.
 *
 * En Android se delega en `androidx.activity.compose.BackHandler`; en escritorio no hace nada
 * (allí no existe un botón "atrás" del sistema). Se usa, por ejemplo, para cerrar el editor de
 * patrones rítmicos con el gesto de retroceso. Ver ADR-013.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
