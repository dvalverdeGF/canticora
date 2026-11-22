package com.grafitto.laudate.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val container = WebContainer()
    ComposeViewport(viewportContainerId = "composeTarget") {
        WebApp(container)
    }
}
