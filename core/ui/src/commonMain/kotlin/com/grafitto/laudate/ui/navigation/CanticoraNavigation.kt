package com.grafitto.laudate.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.ui.common.Density
import com.grafitto.laudate.ui.common.HairlineDivider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Ancho mínimo a partir del cual los paneles **conviven** con el contenido (no son modales).
 * Por debajo, el menú es un *drawer* modal y los ajustes una **hoja inferior** (RNF-402 / ADR-003).
 */
private val InlinePanelsMinWidth = 600.dp

/**
 * **Altura máxima de la hoja de ajustes en móvil**, como fracción de lo que mide la hoja.
 *
 * Un tercio (petición del usuario): deja dos tercios de la pantalla para la partitura, así que se ve
 * lo que se escribe mientras se cambia de símbolo. Antes la hoja llegaba casi al borde superior y
 * tapaba la rejilla y las barras de herramientas.
 */
private const val SettingsSheetHeightFraction = 1f / 3f

/**
 * Ancho mínimo que debe quedarle al **contenido** para que los dos paneles puedan estar desplegados
 * a la vez sin ahogarlo. Si la ventana no da para `menú + ajustes + contenido`, el menú nace
 * **plegado a iconos** y los ajustes cerrados (el usuario los abre cuando los necesita) en vez de
 * comerse el centro de la pantalla.
 */
private val MinContentWidth = 420.dp

/**
 * Ancho del menú **plegado**: una barra de iconos (*rail*) que **nunca desaparece**.
 *
 * El menú de Canticora no se oculta del todo: al plegarse se queda en iconos, como los paneles de
 * un editor de escritorio. Así el usuario siempre tiene la navegación a un clic y nunca se queda
 * «sin menú».
 */
private val NavRailWidth = 64.dp

/**
 * Una entrada del **panel de navegación** lateral.
 *
 * @param id clave estable para marcarla como seleccionada.
 * @param label nombre visible (en el panel se ven los nombres, no solo el icono).
 * @param icon icono que acompaña al nombre.
 * @param section grupo donde va; `null` = grupo principal (sin encabezado).
 */
data class NavEntry(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val section: String? = null
)

/**
 * Botones de **reapertura** de los paneles, publicados a la barra superior de la pantalla activa.
 *
 * Un valor `null` significa «no hace falta el botón»: el panel ya está desplegado o la pantalla no
 * tiene ajustes propios. Las pantallas lo leen a través de `LaudateTopAppBar`, de modo que **ningún
 * panel puede quedar inaccesible** al plegarse.
 *
 * @param onToggleMenu alterna el menú entre **desplegado** (iconos + texto) y **plegado** (solo
 *   iconos). En ancho amplio el botón está **siempre** presente, porque el menú nunca desaparece.
 * @param menuExpanded `true` si el menú está desplegado; sirve para que el botón refleje el estado.
 */
data class PanelToggles(
    val onToggleMenu: (() -> Unit)? = null,
    val menuExpanded: Boolean = false,
    val onOpenSettings: (() -> Unit)? = null
)

/** Botones de reapertura de los paneles laterales, provistos por [CanticoraScaffold]. */
val LocalPanelToggles = staticCompositionLocalOf { PanelToggles() }

/**
 * Estado de los **dos paneles laterales** (menú izquierdo y ajustes derecho), expuesto al contenido
 * para que la barra superior pueda desplegarlos.
 *
 * En ancho amplio el menú es **inline y permanente**: se pliega a una barra de iconos, nunca
 * desaparece. Los ajustes son inline y se pliegan del todo. En ancho compacto ambos son **modales**
 * (drawer lateral y hoja inferior) y se abren a demanda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Stable
class CanticoraPanelsState internal constructor(
    /** `true` en móvil estrecho: los paneles son modales, no ocupan espacio permanente. */
    val isCompact: Boolean,
    /** `true` si la pantalla activa tiene ajustes propios (editor/visor). */
    val hasToolSettings: Boolean,
    private val drawerState: DrawerState,
    private val sheetState: SheetState,
    private val navExpanded: MutableState<Boolean>,
    private val settingsOpen: MutableState<Boolean>,
    private val scope: CoroutineScope
) {
    /**
     * El menú de navegación está **desplegado** (iconos + texto).
     *
     * En ancho amplio el menú siempre está visible, así que esto solo distingue entre desplegado y
     * plegado a iconos; en compacto indica si el *drawer* modal está abierto.
     */
    val isMenuOpen: Boolean get() = if (isCompact) drawerState.isOpen else navExpanded.value

    /** El panel de ajustes está desplegado. */
    val isSettingsOpen: Boolean get() = if (isCompact) sheetState.isVisible else settingsOpen.value

    /** Despliega el menú de navegación (iconos + texto). */
    fun openMenu() {
        if (isCompact) scope.launch { drawerState.open() } else navExpanded.value = true
    }

    /** Pliega el menú de navegación: en ancho amplio queda la barra de iconos, nunca desaparece. */
    fun closeMenu() {
        if (isCompact) scope.launch { drawerState.close() } else navExpanded.value = false
    }

    /** Alterna el menú entre desplegado y plegado (barra de iconos). */
    fun toggleMenu() {
        if (isCompact) {
            scope.launch { if (drawerState.isOpen) drawerState.close() else drawerState.open() }
        } else {
            navExpanded.value = !navExpanded.value
        }
    }

    /** Despliega el panel de ajustes. */
    fun openSettings() {
        if (isCompact) scope.launch { sheetState.show() } else settingsOpen.value = true
    }

    /** Pliega el panel de ajustes. */
    fun closeSettings() {
        if (isCompact) scope.launch { sheetState.hide() } else settingsOpen.value = false
    }
}

/**
 * **Contenedor de Canticora**: dos paneles laterales plegables alrededor del contenido.
 *
 * - **Izquierda** — el **menú de navegación** ([navEntries] + [navActions]). **Nunca desaparece**:
 *   desplegado muestra iconos **y texto**; plegado se queda en una **barra de iconos** con tooltip.
 *   El botón de **hamburguesa** de la barra superior (vía [LocalPanelToggles]) alterna ambos estados.
 * - **Derecha** — el **panel de ajustes**, con los ajustes **comunes** ([commonSettings]) y los de la
 *   **herramienta activa** ([toolSettings]). Solo existe si la pantalla tiene ajustes propios; en el
 *   resto se abre a demanda desde el botón *tune* de la barra superior.
 *
 * Es **Compose Multiplatform**: la misma implementación en Android y escritorio. El ancho decide la
 * forma (RNF-402 / ADR-003): en ancho amplio los paneles conviven con el contenido; en ancho
 * compacto el menú es un *drawer* modal y los ajustes una **hoja inferior**. Si la ventana no da
 * para `menú + ajustes + contenido`, el menú nace plegado a iconos y los ajustes cerrados.
 *
 * @param screenKey identidad de la pantalla activa; al cambiar, el panel de ajustes se abre solo si
 *   la nueva pantalla tiene ajustes propios (y se cierra si no).
 * @param content el contenido de la aplicación; recibe el [CanticoraPanelsState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanticoraScaffold(
    navEntries: List<NavEntry>,
    currentNavId: String,
    onSelectNav: (String) -> Unit,
    modifier: Modifier = Modifier,
    screenKey: Any? = currentNavId,
    brandTitle: String = "Canticora",
    brandTagline: String? = null,
    brandLogo: (@Composable () -> Unit)? = null,
    navActions: List<NavEntry> = emptyList(),
    onNavAction: (String) -> Unit = {},
    navWidth: Dp = 300.dp,
    settingsWidth: Dp = 320.dp,
    /** Ajustes COMUNES a toda la aplicación (nomenclatura, instrumento preferido, modo de vista…). */
    commonSettings: (@Composable () -> Unit)? = null,
    /** Ajustes propios de la **herramienta activa**; `null` si la pantalla no tiene ajustes. */
    toolSettings: (@Composable () -> Unit)? = null,
        /**
     * Nombre de la sección activa.
     *
     * **Ya no se pinta** (petición del usuario): el subtítulo «Canciones», «Tablaturas»… repetía lo
     * que ya dice la pantalla de debajo y robaba una línea al panel. El parámetro se conserva para no
     * romper a los llamadores, pero [SettingsPanelContent] lo ignora.
     */
    @Suppress("UNUSED_PARAMETER")
    sectionTitle: String? = null,
    content: @Composable (CanticoraPanelsState) -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val isCompact = maxWidth < InlinePanelsMinWidth
        // ¿Caben los dos paneles desplegados sin dejar el contenido en un suspiro?
        val fitsBothPanels = maxWidth >= navWidth + settingsWidth + MinContentWidth
        // Con el menú plegado a iconos basta con el ancho del *rail*: los ajustes caben antes.
        val fitsRailAndSettings = maxWidth >= NavRailWidth + settingsWidth + MinContentWidth
        val hasToolSettings = toolSettings != null
        val scope = rememberCoroutineScope()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val navExpanded = remember { mutableStateOf(!isCompact && fitsBothPanels) }
        val settingsOpen = remember { mutableStateOf(false) }

        // Al cambiar de ancho, el menú se despliega solo si hay sitio; si no, queda en iconos.
        LaunchedEffect(isCompact, fitsBothPanels) { navExpanded.value = !isCompact && fitsBothPanels }
        // Al cambiar de pantalla, los ajustes se abren solos si la pantalla los tiene y hay sitio
        // (en móvil abrir una hoja al entrar sería intrusivo).
        LaunchedEffect(screenKey, hasToolSettings, isCompact, fitsRailAndSettings) {
            settingsOpen.value = hasToolSettings && !isCompact && fitsRailAndSettings
        }

        val state = remember(isCompact, hasToolSettings, drawerState, sheetState) {
            CanticoraPanelsState(
                isCompact = isCompact,
                hasToolSettings = hasToolSettings,
                drawerState = drawerState,
                sheetState = sheetState,
                navExpanded = navExpanded,
                settingsOpen = settingsOpen,
                scope = scope
            )
        }

        val toggles = remember(state, state.isMenuOpen, state.isSettingsOpen) {
            PanelToggles(
                // En ancho amplio el menú nunca desaparece: el botón está siempre y alterna entre
                // desplegado (iconos + texto) y plegado (solo iconos). En compacto abre el drawer.
                onToggleMenu = ({ state.toggleMenu() }),
                menuExpanded = state.isMenuOpen,
                // El panel de ajustes siempre tiene al menos los comunes: su botón nunca falta.
                onOpenSettings = if (state.isSettingsOpen) null else ({ state.openSettings() })
            )
        }

        CompositionLocalProvider(LocalPanelToggles provides toggles) {
            if (isCompact) {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    gesturesEnabled = true,
                    drawerContent = {
                        // Mismo color de *chrome* que el panel inline: en modo oscuro, el color por
                        // defecto del `ModalDrawerSheet` se confunde con el fondo del contenido y el
                        // menú queda sin contraste (ver [InlinePanel]).
                        ModalDrawerSheet(
                            modifier = Modifier.width(navWidth),
                            drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                NavPanelContent(
                                    entries = navEntries,
                                    currentId = currentNavId,
                                    onSelect = { id ->
                                        onSelectNav(id)
                                        state.closeMenu()
                                    },
                                    brandTitle = brandTitle,
                                    brandTagline = brandTagline,
                                    brandLogo = brandLogo,
                                    actions = navActions,
                                    onAction = { id ->
                                        onNavAction(id)
                                        state.closeMenu()
                                    }
                                )
                            }
                        }
                    }
                ) {
                    content(state)
                }

                if (state.isSettingsOpen) {
                    ModalBottomSheet(
                        onDismissRequest = state::closeSettings,
                        sheetState = sheetState,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        // **Tope de altura de la hoja en móvil** (petición del usuario): la hoja se
                        // desplegaba a pantalla casi completa y **tapaba la tablatura**, así que no se
                        // veía lo que se estaba escribiendo. Ahora ocupa como mucho **un tercio** de
                        // la pantalla y el resto se desplaza: se ve la partitura mientras se cambia
                        // de símbolo, que es la razón de tener el panel abierto.
                        //
                        // El tope se calcula con el alto **real que le da la hoja a su contenido**
                        // (`BoxWithConstraints`), no con el de la ventana: `containerSize` incluye las
                        // barras del sistema y además el modificador del `ModalBottomSheet` no sirve
                        // para limitar su altura (la ancla arriba en vez de abajo).
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(
                                        min = 160.dp,
                                        max = maxHeight * SettingsSheetHeightFraction
                                    )
                                    .verticalScroll(rememberScrollState())
                            ) {
                                SettingsPanelContent(
                                    commonSettings = commonSettings,
                                    toolSettings = toolSettings,
                                    sectionTitle = sectionTitle,
                                    onCollapse = state::closeSettings
                                )
                            }
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    // El menú NUNCA desaparece: se pliega a una barra de iconos y se despliega con
                    // el botón de la barra superior. Así la navegación siempre está a un clic.
                    InlinePanel(
                        width = if (state.isMenuOpen) navWidth else NavRailWidth,
                        dividerAtEnd = true
                    ) {
                        NavPanelContent(
                            entries = navEntries,
                            currentId = currentNavId,
                            onSelect = onSelectNav,
                            brandTitle = brandTitle,
                            brandTagline = brandTagline,
                            brandLogo = brandLogo,
                            actions = navActions,
                            onAction = onNavAction,
                            expanded = state.isMenuOpen
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        content(state)
                    }

                    if (state.isSettingsOpen) {
                        InlinePanel(width = settingsWidth, dividerAtEnd = false) {
                            SettingsPanelContent(
                                commonSettings = commonSettings,
                                toolSettings = toolSettings,
                                sectionTitle = sectionTitle,
                                onCollapse = state::closeSettings,
                                scrollable = true
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Panel lateral **inline** (ancho amplio): una zona **plana** a sangre, integrada en el fondo de la
 * aplicación, separada del contenido por un **filete de 1 px**.
 *
 * No es una tarjeta: ni esquinas redondeadas, ni sombra, ni caja. Es el mismo lenguaje que los
 * paneles acoplados de un editor de escritorio (Photoshop, Guitar Pro): el *chrome* de la app va
 * sobre un tono y el documento sobre otro, y el límite lo marca una línea, no una elevación.
 *
 * @param dividerAtEnd `true` para poner el filete en el borde derecho (menú), `false` en el
 *   izquierdo (ajustes).
 */
@Composable
private fun InlinePanel(
    width: Dp,
    dividerAtEnd: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    // El menú se pliega a la barra de iconos con una animación corta: se ve el panel «encogerse»
    // en vez de saltar, y el contenido se recoloca sin parpadeos.
    val animatedWidth by animateDpAsState(
        targetValue = width,
        animationSpec = tween(durationMillis = 180),
        label = "panelWidth"
    )
    Column(
        modifier = Modifier
            .width(animatedWidth)
            .fillMaxHeight()
            // **Un tono por encima del fondo, no uno por debajo** (reporte del usuario: «en el modo
            // oscuro horizontal, el panel lateral no tiene contraste en textos»). Con
            // `surfaceContainerLow` el panel quedaba en `#1D1B20` sobre un fondo `#141218`: una
            // diferencia de luminancia de ~1,1:1, es decir, **invisible**, y los textos parecían
            // flotar sobre el mismo plano. `surfaceContainerHigh` (`#2B2930`) sí separa el *chrome*
            // de la aplicación del contenido, que es el mismo criterio que ya usa el escritorio de la
            // tablatura. En modo claro también mejora: un gris algo más marcado sobre el blanco.
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // **Inset de la barra de navegación del sistema.** En horizontal, Android pone su menú
            // (los tres botones) en el **borde lateral**, y el panel de ajustes llega hasta ese borde:
            // la mitad del panel —y con ella el botón de cerrar— quedaba **detrás del menú** y no se
            // podía pulsar (reporte del usuario: «el panel y el icono cerrar quedan detrás del menú
            // android en modo horizontal, ¡no puedo cerrar!»). Se aplica el inset **en el lado del
            // borde del panel**, que es distinto según sea el menú (`dividerAtEnd = true`, inset
            // izquierdo) o los ajustes (`dividerAtEnd = false`, inset derecho).
            .windowInsetsPadding(
                WindowInsets.navigationBars.only(
                    if (dividerAtEnd) WindowInsetsSides.Start + WindowInsetsSides.Top
                    else WindowInsetsSides.End + WindowInsetsSides.Top
                )
            )
            .drawBehind {
                val stroke = 1.dp.toPx()
                val x = if (dividerAtEnd) size.width - stroke else 0f
                drawRect(
                    color = dividerColor,
                    topLeft = Offset(x, 0f),
                    size = Size(stroke, size.height)
                )
            }
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Bottom)
            )
    ) {
        content()
    }
}

/**
 * Contenido del **menú de navegación**: cabecera de marca, entradas agrupadas y acciones al pie.
 * Lo comparten el *drawer* modal (móvil) y el panel inline (escritorio/tablet).
 *
 * @param expanded `true` → iconos **y texto**; `false` → **solo iconos** (barra plegada). El menú
 *   plegado sigue mostrando todos los iconos: nunca desaparece, solo se queda sin etiquetas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.NavPanelContent(
    entries: List<NavEntry>,
    currentId: String,
    onSelect: (String) -> Unit,
    brandTitle: String,
    brandTagline: String?,
    brandLogo: (@Composable () -> Unit)?,
    actions: List<NavEntry>,
    onAction: (String) -> Unit,
    expanded: Boolean = true
) {
    if (expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Density.CardPadding, end = Density.Tight, top = Density.Compact, bottom = Density.Compact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Density.CardPadding)
        ) {
            brandLogo?.let { it() }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = brandTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                brandTagline?.let { tagline ->
                    Text(
                        text = tagline,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    } else {
        // Plegado: solo la marca, centrada. El botón para desplegar vive en la barra superior.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Density.Compact),
            contentAlignment = Alignment.Center
        ) {
            brandLogo?.let { it() }
        }
    }
    HairlineDivider()

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = Density.Tight)
    ) {
        var lastSection: String? = null
        entries.forEach { entry ->
            if (expanded && entry.section != null && entry.section != lastSection) {
                Text(
                    text = entry.section,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = Density.CardPadding + 26.dp,
                        top = Density.Compact,
                        bottom = Density.Tight
                    )
                )
                lastSection = entry.section
            }
            if (expanded) {
                CompactNavItem(
                    label = entry.label,
                    icon = entry.icon,
                    selected = entry.id == currentId,
                    onClick = { onSelect(entry.id) }
                )
            } else {
                RailNavItem(
                    label = entry.label,
                    icon = entry.icon,
                    selected = entry.id == currentId,
                    onClick = { onSelect(entry.id) }
                )
            }
        }
    }

    // Acciones al pie, siempre visibles: no se pierden al hacer scroll en la lista de entradas.
    if (actions.isNotEmpty()) {
        HairlineDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = Density.Tight)
        ) {
            actions.forEach { entry ->
                if (expanded) {
                    CompactNavItem(
                        label = entry.label,
                        icon = entry.icon,
                        // El pie también puede llevar navegación (p. ej. el manual): se marca igual.
                        selected = entry.id == currentId,
                        onClick = { onAction(entry.id) }
                    )
                } else {
                    RailNavItem(
                        label = entry.label,
                        icon = entry.icon,
                        selected = entry.id == currentId,
                        onClick = { onAction(entry.id) }
                    )
                }
            }
        }
    }
}

/**
 * Entrada del menú **plegado**: solo el icono, centrado, con **tooltip** al pasar el ratón.
 *
 * Es la pieza que hace que el menú «nunca se pliegue del todo»: al plegarse quedan los iconos, así
 * que la navegación sigue a un clic y el usuario no se queda sin menú.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RailNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Density.Snug, vertical = Density.Hairline)
                .height(Density.RowHeight)
                .clip(RoundedCornerShape(Density.CornerSmall))
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        androidx.compose.ui.graphics.Color.Transparent
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * Entrada **compacta** del menú: icono, nombre y, opcionalmente, un chevron.
 *
 * Sustituye al `NavigationDrawerItem` de Material (56 dp de alto y 12 dp de margen lateral) por una
 * fila de [Density.RowHeight] pegada al borde: el menú entero cabe sin scroll y no desperdicia
 * espacio. El estado seleccionado se marca con fondo de acento y texto en color primario.
 */
@Composable
private fun CompactNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Density.Snug)
            .height(Density.RowHeight)
            .clip(RoundedCornerShape(Density.CornerSmall))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Density.CardPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Density.CardPadding)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

/**
 * Contenido del **panel de ajustes** (derecha): los de la herramienta activa y, debajo, los comunes
 * a toda la aplicación. Lo comparten la hoja inferior (móvil) y el panel inline (escritorio).
 *
 * Cada bloque va en una **sección plegable** plana (cabecera con chevron + filete), de modo que el
 * panel se lee por bloques y el usuario pliega lo que no está usando.
 *
 * @param scrollable `true` en el panel acoplado de escritorio (la cabecera queda fija y el cuerpo
 *   se desplaza); `false` dentro de la hoja inferior, que ya aporta su propio scroll.
 */
@Composable
private fun ColumnScope.SettingsPanelContent(
    commonSettings: (@Composable () -> Unit)?,
    toolSettings: (@Composable () -> Unit)?,
    @Suppress("UNUSED_PARAMETER") sectionTitle: String?,
    onCollapse: (() -> Unit)?,
    scrollable: Boolean = false
) {
    // Cabecera **sin icono y sin subtítulo** (petición del usuario): el panel ya se identifica con la
    // palabra «Ajustes», y el icono de la rueda duplicaba el del botón que lo abre, mientras que el
    // subtítulo («Canciones», «Tablaturas»…) repetía lo que ya dice la pantalla de debajo. Se queda
    // solo el título y el botón de cerrar.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Density.CardPadding, end = Density.Tight, top = Density.Compact, bottom = Density.Compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Density.CardPadding)
    ) {
        Text(
            text = "Ajustes",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        if (onCollapse != null) {
            IconButton(onClick = onCollapse, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Plegar los ajustes",
                    modifier = Modifier.size(18.dp),
                    // **Color explícito**: sin él el icono hereda `LocalContentColor`, que dentro de
                    // la cabecera del panel no está atado al fondo del panel. Se quedaba en un gris
                    // casi igual al de `surfaceContainerHigh` y no se veía (reporte del usuario:
                    // «el icono cerrar sigue sin tener contraste»). `onSurfaceVariant` es el tono
                    // secundario del tema y está calculado para leerse sobre la superficie.
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (scrollable) {
                    Modifier.weight(1f).verticalScroll(rememberScrollState())
                } else {
                    Modifier.wrapContentHeight()
                }
            )
            .padding(bottom = Density.Compact)
    ) {
        // Los ajustes de la herramienta van **sin cabecera**: el propio panel ya dice en qué
        // pantalla estamos (subtítulo de arriba) y una cabecera más solo robaba altura útil.
        toolSettings?.invoke()
        if (commonSettings != null) {
            SettingsSection(
                title = "Ajustes generales",
                icon = CanticoraIcons.Notation
            ) {
                commonSettings()
            }
        }
    }
}

/** Tamaño sugerido para el logo de la cabecera del panel. */
val NavBrandLogoSize = 44.dp
