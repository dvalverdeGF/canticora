package com.grafitto.laudate.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import com.grafitto.laudate.domain.model.LaudateBrand
import com.grafitto.laudate.ui.common.AppLogo
import java.awt.Cursor
import java.awt.Frame
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit

/** Altura de la barra de título propia (mayor que los 32 dp de la barra nativa de Windows). */
val TitleBarHeight: Dp = 44.dp

private val CloseHoverColor = Color(0xFFC42B1C)

/**
 * Barra de título integrada en la app (ventana sin decoración nativa).
 *
 * Arrastrar la zona libre mueve la ventana; el doble clic alterna maximizar/restaurar. A la
 * derecha van los tres botones de ventana con el aspecto de Windows 11.
 */
/**
 * Una ventana sin decoración se maximiza sobre toda la pantalla y tapa la barra de tareas de
 * Windows; con estos límites se maximiza solo sobre el área de trabajo del monitor actual.
 */
fun java.awt.Window.fitMaximizedBoundsToWorkArea() {
    val frame = this as? Frame ?: return
    val gc = graphicsConfiguration ?: return
    val insets = Toolkit.getDefaultToolkit().getScreenInsets(gc)
    val b = gc.bounds
    frame.maximizedBounds = Rectangle(
        insets.left,
        insets.top,
        b.width - insets.left - insets.right,
        b.height - insets.top - insets.bottom
    )
}

@Composable
fun WindowScope.DesktopTitleBar(
    state: WindowState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maximized = state.placement == WindowPlacement.Maximized
    val toggleMaximize = {
        if (!maximized) window.fitMaximizedBoundsToWorkArea()
        state.placement = if (maximized) WindowPlacement.Floating else WindowPlacement.Maximized
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TitleBarHeight)
            // Mismo tono claro que el menú: barra de título "elevada" sobre el contenido
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dragOrigin = remember { arrayOfNulls<Point>(2) }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .pointerInput(maximized) {
                    detectTapGestures(onDoubleTap = { toggleMaximize() })
                }
                .pointerInput(maximized) {
                    if (maximized) return@pointerInput
                    // Se mueve con el puntero en coordenadas de pantalla: el delta local
                    // cambia al moverse la propia ventana y provocaría temblor.
                    detectDragGestures(
                        onDragStart = {
                            dragOrigin[0] = window.location
                            dragOrigin[1] = MouseInfo.getPointerInfo().location
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val w0 = dragOrigin[0] ?: return@detectDragGestures
                            val m0 = dragOrigin[1] ?: return@detectDragGestures
                            val m = MouseInfo.getPointerInfo().location
                            window.setLocation(w0.x + m.x - m0.x, w0.y + m.y - m0.y)
                        }
                    )
                }
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppLogo(modifier = Modifier.size(26.dp))
                Text(
                    text = LaudateBrand.NAME,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }

        CaptionButton(onClick = { state.isMinimized = true }) { color ->
            Canvas(Modifier.size(10.dp)) {
                drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
            }
        }
        CaptionButton(onClick = toggleMaximize) { color ->
            Canvas(Modifier.size(10.dp)) {
                val w = 1.dp.toPx()
                if (maximized) {
                    val s = size.width - 2.5.dp.toPx()
                    val o = size.width - s
                    drawRect(color, Offset(0f, o), Size(s, s), style = Stroke(w, join = StrokeJoin.Miter))
                    drawLine(color, Offset(o, o), Offset(o, 0f), strokeWidth = w)
                    drawLine(color, Offset(o, 0f), Offset(size.width, 0f), strokeWidth = w)
                    drawLine(color, Offset(size.width, 0f), Offset(size.width, s), strokeWidth = w)
                    drawLine(color, Offset(size.width, s), Offset(s, s), strokeWidth = w)
                } else {
                    drawRect(color, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, join = StrokeJoin.Miter))
                }
            }
        }
        CaptionButton(onClick = onClose, hoverColor = CloseHoverColor, hoverContentColor = Color.White) { color ->
            Canvas(Modifier.size(10.dp)) {
                val w = 1.dp.toPx()
                drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = w)
                drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = w)
            }
        }
    }
}

@Composable
private fun CaptionButton(
    onClick: () -> Unit,
    hoverColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
    hoverContentColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: @Composable (Color) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .width(52.dp)
            .fillMaxHeight()
            .background(if (hovered) hoverColor else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        icon(if (hovered) hoverContentColor else MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Borde fino de la ventana (las ventanas sin decoración no traen ninguno) y tiradores de
 * redimensionado en bordes y esquinas, que AWT no ofrece en ventanas sin decoración.
 */
@Composable
fun WindowScope.WindowFrame(
    state: WindowState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val maximized = state.placement == WindowPlacement.Maximized
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (maximized) Modifier
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            )
    ) {
        content()
        if (!maximized) ResizeHandles()
    }
}

private enum class Edge(val cursor: Int, val left: Boolean, val right: Boolean, val top: Boolean, val bottom: Boolean) {
    N(Cursor.N_RESIZE_CURSOR, false, false, true, false),
    S(Cursor.S_RESIZE_CURSOR, false, false, false, true),
    W(Cursor.W_RESIZE_CURSOR, true, false, false, false),
    E(Cursor.E_RESIZE_CURSOR, false, true, false, false),
    NW(Cursor.NW_RESIZE_CURSOR, true, false, true, false),
    NE(Cursor.NE_RESIZE_CURSOR, false, true, true, false),
    SW(Cursor.SW_RESIZE_CURSOR, true, false, false, true),
    SE(Cursor.SE_RESIZE_CURSOR, false, true, false, true)
}

@Composable
private fun WindowScope.ResizeHandles() {
    val thickness = 5.dp
    val corner = 12.dp
    Box(Modifier.fillMaxSize()) {
        Handle(Edge.N, Modifier.align(Alignment.TopCenter).fillMaxWidth().height(thickness).padding(horizontal = corner))
        Handle(Edge.S, Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(thickness).padding(horizontal = corner))
        Handle(Edge.W, Modifier.align(Alignment.CenterStart).fillMaxHeight().width(thickness).padding(vertical = corner))
        Handle(Edge.E, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(thickness).padding(vertical = corner))
        Handle(Edge.NW, Modifier.align(Alignment.TopStart).size(corner))
        Handle(Edge.NE, Modifier.align(Alignment.TopEnd).size(corner))
        Handle(Edge.SW, Modifier.align(Alignment.BottomStart).size(corner))
        Handle(Edge.SE, Modifier.align(Alignment.BottomEnd).size(corner))
    }
}

@Composable
private fun WindowScope.Handle(edge: Edge, modifier: Modifier) {
    val startBounds = remember { arrayOfNulls<Rectangle>(1) }
    val startMouse = remember { arrayOfNulls<java.awt.Point>(1) }
    Box(
        modifier = modifier
            .pointerHoverIcon(PointerIcon(Cursor(edge.cursor)))
            .pointerInput(edge) {
                detectDragGestures(
                    onDragStart = {
                        startBounds[0] = window.bounds
                        startMouse[0] = MouseInfo.getPointerInfo().location
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val b = startBounds[0] ?: return@detectDragGestures
                        val m0 = startMouse[0] ?: return@detectDragGestures
                        val m = MouseInfo.getPointerInfo().location
                        val dx = m.x - m0.x
                        val dy = m.y - m0.y
                        val min = window.minimumSize
                        var x = b.x
                        var y = b.y
                        var w = b.width
                        var h = b.height
                        if (edge.right) w = (b.width + dx).coerceAtLeast(min.width)
                        if (edge.bottom) h = (b.height + dy).coerceAtLeast(min.height)
                        if (edge.left) {
                            w = (b.width - dx).coerceAtLeast(min.width)
                            x = b.x + b.width - w
                        }
                        if (edge.top) {
                            h = (b.height - dy).coerceAtLeast(min.height)
                            y = b.y + b.height - h
                        }
                        window.setBounds(x, y, w, h)
                    }
                )
            }
    )
}
