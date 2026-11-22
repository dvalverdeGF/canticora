package com.grafitto.laudate.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Mensajes efímeros («toast») **compartidos** entre Android y escritorio.
 *
 * Los ViewModels ya emiten sus avisos como `SideEffect.ShowMessage` («Guardado correctamente»,
 * ««X» ya estaba en Misa Dominical»…). En vez de mostrarlos con el `Toast` del sistema —que solo
 * existe en Android— o con un `Text` fijo que no desaparece —lo que hacía el escritorio—, la app
 * los canaliza por aquí y los pinta con un **snackbar Material 3** en el borde inferior.
 *
 * Uso:
 * - Se crea un `AppMessageController` a nivel de pantalla raíz y se hace `show(mensaje)` en los
 *   manejadores de efectos.
 * - [AppMessageEffect] conecta los mensajes al [SnackbarHostState] del controlador; se usa cuando
 *   el host lo pone la pantalla (p. ej. el `snackbarHost` del `Scaffold` de Android).
 * - [AppMessageHost] hace lo mismo y además superpone el snackbar sobre su contenido (escritorio,
 *   que no tiene `Scaffold`).
 *
 * El «Deshacer» del catálogo usa directamente `controller.snackbarHostState`, de modo que las
 * acciones con botón y los avisos simples comparten un único host.
 */
class AppMessageController {
    /** Host compartido: los mensajes simples y los de acción (con «Deshacer») salen por aquí. */
    val snackbarHostState = SnackbarHostState()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    internal val messages: SharedFlow<String> = _messages

    /** Encola un mensaje; la duración la decide quien lo muestra. */
    fun show(message: String) {
        _messages.tryEmit(message)
    }
}

/** Conecta los mensajes del controlador a su [SnackbarHostState]. */
@Composable
fun AppMessageEffect(controller: AppMessageController) {
    LaunchedEffect(controller) {
        controller.messages.collect { message ->
            controller.snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }
}

/**
 * Superpone el snackbar de mensajes sobre [content].
 *
 * Se usa cuando la pantalla no tiene un `Scaffold` con ranura `snackbarHost` (escritorio). En
 * Android basta con [AppMessageEffect] + `SnackbarHost(controller.snackbarHostState)`.
 */
@Composable
fun AppMessageHost(
    controller: AppMessageController,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AppMessageEffect(controller)
    Box(modifier = modifier) {
        content()
        SnackbarHost(
            hostState = controller.snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }
}
