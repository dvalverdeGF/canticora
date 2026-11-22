package com.grafitto.laudate.ui.common

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.grafitto.laudate.ui.generated.resources.Res
import com.grafitto.laudate.ui.generated.resources.app_logo
import org.jetbrains.compose.resources.painterResource

/**
 * Logo de Canticora a cualquier tamaño.
 *
 * Es un **vector** (Android Vector Drawable, `app_logo.xml`) generado desde el maestro
 * (`dist/logos/imagotip.png`) con VTracer: se dibuja nítido a 26–72 dp sin el dentado que
 * producía reducir un mapa de bits de 512 px en un solo paso.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(
        painter = painterResource(Res.drawable.app_logo),
        contentDescription = contentDescription,
        modifier = modifier
    )
}
