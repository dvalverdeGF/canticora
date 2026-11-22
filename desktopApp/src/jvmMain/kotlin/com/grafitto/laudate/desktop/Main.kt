package com.grafitto.laudate.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.grafitto.laudate.domain.model.LaudateBrand
import com.grafitto.laudate.ui.generated.resources.Res
import com.grafitto.laudate.ui.generated.resources.app_logo
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension
import java.io.File

/**
 * Punto de entrada de la app de escritorio (Windows/JVM).
 *
 * Monta el contenedor real (Room KMP en JVM) y la raíz de UI compartida con Android. Si se abre
 * con un fichero `.canticora` (doble clic / «Abrir con»), se recoge para ofrecer importarlo.
 * Ver ADR-013.
 */
fun main(args: Array<String>) = application {
    val container = remember {
        DesktopContainer().apply {
            // El primer argumento puede ser la ruta del fichero abierto con la app.
            incomingFile = args.firstOrNull()
                ?.let { File(it) }
                ?.takeIf { it.exists() }
        }
    }

    DisposableEffect(container) {
        onDispose { container.close() }
    }

    // Tamaño inicial pensado para que los dos paneles laterales quepan junto al contenido; el
    // usuario puede encoger la ventana y los paneles se plegarán solos (ver `CanticoraScaffold`).
    val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp))

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = LaudateBrand.FULL_NAME,
        // Ventana sin decoración nativa: la barra de título la pinta la app (DesktopTitleBar).
        undecorated = true,
        // Icono de la ventana y de la barra de tareas: la «T» de Canticora (mismo recurso que la app).
        icon = painterResource(Res.drawable.app_logo)
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(720, 480)
            window.fitMaximizedBoundsToWorkArea()
        }
        LaudateDesktopRoot(
            container = container,
            titleBar = { DesktopTitleBar(state = windowState, onClose = ::exitApplication) },
            frame = { content -> WindowFrame(state = windowState, content = content) }
        )
    }
}
