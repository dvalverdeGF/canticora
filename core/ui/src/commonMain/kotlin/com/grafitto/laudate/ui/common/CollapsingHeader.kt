package com.grafitto.laudate.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Estado de una **cabecera pegajosa** que se esconde al bajar y reaparece al subir.
 *
 * Reproduce el comportamiento de la app de Gmail: el bloque superior (título o buscador/filtros)
 * sube con el contenido al hacer scroll hacia abajo, vuelve a aparecer en cuanto se hace scroll
 * hacia arriba y, cuando ya no está pegado al principio, se dibuja **flotando** sobre la lista con
 * una elevación y un fondo algo más claro. Esa pista visual indica al usuario que todavía puede
 * seguir subiendo.
 *
 * La cabecera se **traslada** con [offsetY] (de 0 a -alto) y el contenido reserva ese hueco con
 * [contentTopPadding]; ambos se derivan del mismo valor animado, así que no queda ningún hueco.
 *
 * Uso típico junto con [CollapsingHeaderBox].
 */
@Stable
class CollapsingHeaderState internal constructor() {

    internal var measuredHeight by mutableIntStateOf(0)
    internal var animatedOffset by mutableFloatStateOf(0f)
    internal var revealedState by mutableStateOf(true)
    internal var floatingState by mutableStateOf(false)

    /** Alto natural de la cabecera, en píxeles (0 hasta la primera medición). */
    val height: Int get() = measuredHeight

    /** `true` cuando la cabecera flota sobre el contenido (no está pegada al principio). */
    val isFloating: Boolean get() = floatingState

    /** Desplazamiento vertical actual: `0` visible, negativo oculta. */
    val offsetY: Float get() = animatedOffset

    /** Notifica el alto medido (llamar desde `Modifier.onSizeChanged`). */
    fun onHeightChanged(heightPx: Int) {
        measuredHeight = heightPx
    }

    /**
     * Alto que debe reservar el contenido como *padding* superior para quedar justo debajo de la
     * cabecera, también mientras esta se anima.
     */
    @Composable
    fun contentTopPadding(): Dp = with(LocalDensity.current) { (measuredHeight + animatedOffset).toDp() }

    /** Elevación a aplicar a la cabecera: 0 pegada al principio, 3 dp cuando flota. */
    @Composable
    fun headerElevation(): Dp = if (floatingState) 3.dp else 0.dp
}

/**
 * Recuerda un [CollapsingHeaderState] a partir del scroll de una `LazyColumn`.
 *
 * Solo hay dos posiciones estables (visible / oculta) y la transición se anima; así no hay
 * cálculos frágiles de deltas entre fotogramas.
 */
@Composable
fun rememberCollapsingHeaderState(
    listState: LazyListState,
    initiallyRevealed: Boolean = true,
): CollapsingHeaderState {
    val state = remember { CollapsingHeaderState().apply { revealedState = initiallyRevealed } }

    LaunchedEffect(listState) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val atTop = index == 0 && offset <= 0
                val scrollingUp = index < lastIndex || (index == lastIndex && offset < lastOffset)
                val scrollingDown = index > lastIndex || (index == lastIndex && offset > lastOffset)

                when {
                    atTop -> {
                        state.revealedState = true
                        state.floatingState = false
                    }
                    scrollingUp -> {
                        state.revealedState = true
                        state.floatingState = true
                    }
                    scrollingDown -> {
                        state.revealedState = false
                        state.floatingState = false
                    }
                }

                lastIndex = index
                lastOffset = offset
            }
    }

    return state.also { it.animate() }
}

/** Variante para rejillas (`LazyVerticalGrid`). */
@Composable
fun rememberCollapsingHeaderState(
    gridState: LazyGridState,
    initiallyRevealed: Boolean = true,
): CollapsingHeaderState {
    val state = remember { CollapsingHeaderState().apply { revealedState = initiallyRevealed } }

    LaunchedEffect(gridState) {
        var lastIndex = gridState.firstVisibleItemIndex
        var lastOffset = gridState.firstVisibleItemScrollOffset
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val atTop = index == 0 && offset <= 0
                val scrollingUp = index < lastIndex || (index == lastIndex && offset < lastOffset)
                val scrollingDown = index > lastIndex || (index == lastIndex && offset > lastOffset)

                when {
                    atTop -> {
                        state.revealedState = true
                        state.floatingState = false
                    }
                    scrollingUp -> {
                        state.revealedState = true
                        state.floatingState = true
                    }
                    scrollingDown -> {
                        state.revealedState = false
                        state.floatingState = false
                    }
                }

                lastIndex = index
                lastOffset = offset
            }
    }

    return state.also { it.animate() }
}

/** Variante para contenedores con `Modifier.verticalScroll(rememberScrollState())`. */
@Composable
fun rememberCollapsingHeaderState(
    scrollState: ScrollState,
    initiallyRevealed: Boolean = true,
): CollapsingHeaderState {
    val state = remember { CollapsingHeaderState().apply { revealedState = initiallyRevealed } }

    LaunchedEffect(scrollState) {
        var last = scrollState.value
        snapshotFlow { scrollState.value }.collect { value ->
            when {
                value <= 0 -> {
                    state.revealedState = true
                    state.floatingState = false
                }
                value < last -> {
                    state.revealedState = true
                    state.floatingState = true
                }
                value > last -> {
                    state.revealedState = false
                    state.floatingState = false
                }
            }
            last = value
        }
    }

    return state.also { it.animate() }
}

/**
 * Anima el desplazamiento de la cabecera entre visible (0) y oculta (-alto) y lo guarda en el
 * estado. Se ejecuta al final de [rememberCollapsingHeaderState].
 */
@Composable
private fun CollapsingHeaderState.animate() {
    val target = if (revealedState) 0f else -measuredHeight.toFloat()
    val value by animateFloatAsState(targetValue = target, label = "collapsingHeaderOffset")
    animatedOffset = value
}

/**
 * Estado de la **barra inferior** (menú de navegación) que se oculta al bajar y reaparece al subir,
 * igual que la cabecera superior. Así el contenido aprovecha todo el alto de la pantalla.
 *
 * A diferencia de la cabecera, la barra inferior vive en la raíz (fuera de las pantallas), por lo
 * que se alimenta de un [NestedScrollConnection] que captura el scroll de la pantalla visible.
 */
@Stable
class BottomBarCollapseState internal constructor() {

    internal var visibleState by mutableStateOf(true)
    internal var accumulated by mutableFloatStateOf(0f)
    internal var enabledState by mutableStateOf(true)

    /** Conexión de scroll que debe colgarse de la raíz (por encima de las pantallas). */
    lateinit var connection: NestedScrollConnection
        internal set

    /** `true` si la barra inferior debe verse. */
    val isVisible: Boolean get() = visibleState

    /** Solo se pliega en las pantallas que muestran la barra (catálogo, misas, ritmos). */
    var enabled: Boolean
        get() = enabledState
        set(value) {
            enabledState = value
            if (!value) reset()
        }

    /** Vuelve a mostrar la barra (al cambiar de pantalla o al llegar arriba). */
    fun reset() {
        visibleState = true
        accumulated = 0f
    }
}

/**
 * Recuerda el estado de la barra inferior. El [NestedScrollConnection] que devuelve el estado debe
 * aplicarse con `Modifier.nestedScroll(...)` en un contenedor **ancestro** de las pantallas.
 */
@Composable
fun rememberBottomBarCollapseState(thresholdPx: Float = 64f): BottomBarCollapseState {
    val state = remember { BottomBarCollapseState() }
    state.connection = remember(state, thresholdPx) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!state.enabledState) return Offset.Zero
                val dy = consumed.y
                if (dy == 0f) return Offset.Zero
                // Al cambiar de dirección se reinicia el acumulador: hacen falta ~64 px seguidos.
                if (dy * state.accumulated < 0f) state.accumulated = 0f
                state.accumulated += dy
                when {
                    state.accumulated <= -thresholdPx -> state.visibleState = false
                    state.accumulated >= thresholdPx -> state.visibleState = true
                }
                return Offset.Zero
            }
        }
    }
    return state
}

/**
 * Envuelve la barra inferior para que se **deslice hacia abajo** al ocultarse (el alto reservado se
 * reduce en la misma medida, así el contenido gana ese espacio). Misma matemática que la barra
 * superior de Material 3.
 */
@Composable
fun CollapsingBottomBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val hidden by animateFloatAsState(if (visible) 0f else 1f, label = "bottomBarCollapse")
    Box(
        modifier = modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val height = (placeable.height * (1f - hidden)).roundToInt().coerceAtLeast(0)
                layout(placeable.width, height) { placeable.place(0, 0) }
            }
    ) {
        content()
    }
}
