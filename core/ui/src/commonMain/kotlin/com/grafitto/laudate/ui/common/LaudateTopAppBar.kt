package com.grafitto.laudate.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.grafitto.laudate.ui.navigation.LocalPanelToggles

/**
 * Barra superior de Canticora: un `TopAppBar` de Material 3 que, además de lo que le pase cada
 * pantalla, añade los **botones de reapertura de los paneles laterales**.
 *
 * Los botones no se cablean pantalla a pantalla: se leen de [LocalPanelToggles], que publica
 * `CanticoraScaffold`. Así, cuando el **menú** o los **ajustes** se pliegan, su botón aparece en la
 * barra superior de *cualquier* pantalla y **ningún panel queda inaccesible**.
 *
 * - **Hamburguesa** (esquina izquierda, **siempre el primero**): despliega el menú de navegación.
 *   Va antes que el botón «atrás» de la pantalla para que su sitio no cambie nunca: el usuario lo
 *   encuentra siempre en el mismo píxel, sin depender de si la pantalla tiene flecha o no.
 * - **Ajustes** (esquina derecha, icono *tune*): despliega el panel de ajustes; solo aparece en
 *   pantallas con ajustes propios.
 *
 * Fuera de un `CanticoraScaffold` (previsualizaciones, tests) no publica nada y se comporta como un
 * `TopAppBar` normal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LaudateTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    val toggles = LocalPanelToggles.current
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // El hamburguesa va SIEMPRE primero: esquina izquierda, misma posición en todas las
                // pantallas, tenga o no botón «atrás». En ancho amplio alterna el menú entre
                // desplegado (iconos + texto) y plegado (solo iconos): el menú nunca desaparece.
                toggles.onToggleMenu?.let { onToggle ->
                    IconButton(onClick = onToggle) {
                        Icon(
                            imageVector = if (toggles.menuExpanded) {
                                Icons.AutoMirrored.Filled.MenuOpen
                            } else {
                                Icons.Default.Menu
                            },
                            contentDescription = if (toggles.menuExpanded) {
                                "Plegar el menú"
                            } else {
                                "Desplegar el menú"
                            }
                        )
                    }
                }
                navigationIcon()
            }
        },
        actions = {
            actions()
            toggles.onOpenSettings?.let { onOpen ->
                IconButton(onClick = onOpen) {
                    Icon(Icons.Default.Tune, contentDescription = "Abrir los ajustes")
                }
            }
        },
        colors = colors,
        scrollBehavior = scrollBehavior
    )
}
