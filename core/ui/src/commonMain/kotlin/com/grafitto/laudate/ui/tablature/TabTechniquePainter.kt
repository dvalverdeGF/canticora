package com.grafitto.laudate.ui.tablature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.grafitto.laudate.domain.tablature.TabGlyph
import com.grafitto.laudate.domain.tablature.TabGlyphKind
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/**
 * Pintor de los **símbolos vectoriales** de técnica de la tablatura (§13.5.bis).
 *
 * Se dibujan con `Canvas` —líneas, arcos y rombos— **sin empaquetar ninguna fuente**, igual que los
 * símbolos de rasgueo ([com.grafitto.laudate.ui.rhythm.StrumSymbol]). Motivo doble: (a) la fuente
 * oficial de símbolos musicales está licenciada **GPL-3.0-or-later**, lo que contaminaría la app; y
 * (b) los símbolos se dimensionan a la casilla y **no se recortan**, que es justo lo que ocurría
 * cuando el `P.M.` o el `l.v.` se escribían pegados a la cifra.
 *
 * Cada símbolo se ancla a su `(x, y)` del dibujo (encima de la cifra de la nota) y se pinta con un
 * tamaño proporcional a [size].
 */
object TabTechniquePainter {

    /**
     * Pinta un glifo. [size] es el lado del cuadro del símbolo; el resto de medidas se derivan.
     *
     * @param cellWidth ancho de una **columna** de la rejilla, para los símbolos que se **prolongan**
     *   (el `P.M.` con alcance). Por defecto se deriva de [size].
     * @param drawLabel dibuja un texto centrado en `(x, y)`; lo aporta el llamante (que tiene el
     *   `TextMeasurer`), de modo que el pintor no dependa de una fuente concreta.
     */
    fun DrawScope.drawGlyph(
        glyph: TabGlyph,
        color: Color,
        size: Float,
        cellWidth: Float = size * 1.4f,
        drawLabel: (text: String, x: Float, y: Float, bold: Boolean) -> Unit
    ) {
        val stroke = (size * 0.09f).coerceAtLeast(1.1f)
        val cx = glyph.x
        val cy = glyph.y
        when (glyph.kind) {
            // Trémolo: tres barras oblicuas finas.
            TabGlyphKind.TREMOLO -> {
                // (D-28) El número de **trazos** lo decide la nota (`tremolo1`…`tremolo4` de la paleta
                // «Trémolos» de MuseScore): un trazo es una corchea de repetición, cuatro una semifusa.
                // Se reparten **centrados** en el ancho del glifo, así que 1 trazo cae en el eje y 4
                // ocupan todo el hueco sin salirse.
                val half = size * 0.30f
                val slant = size * 0.16f
                val strokes = glyph.strokes.coerceIn(1, 4)
                val step = size * 0.19f
                val first = -(strokes - 1) / 2f
                for (k in 0 until strokes) {
                    val baseX = cx + (first + k) * step
                    drawLine(
                        color,
                        Offset(baseX - slant / 2f, cy + half),
                        Offset(baseX + slant / 2f, cy - half),
                        stroke,
                        StrokeCap.Round
                    )
                }
            }

            // Vibrato: ondulación (dos medias ondas).
            TabGlyphKind.VIBRATO -> {
                val w = size * 0.55f
                val step = w / 4f
                val amp = size * 0.14f
                var x = cx - w / 2f
                var up = true
                repeat(4) {
                    drawLine(
                        color,
                        Offset(x, cy + if (up) amp else -amp),
                        Offset(x + step, cy + if (up) -amp else amp),
                        stroke,
                        StrokeCap.Round
                    )
                    x += step
                    up = !up
                }
            }

            // Bend: arco hacia arriba con punta de flecha.
            TabGlyphKind.BEND -> {
                drawBendArc(cx, cy, size, stroke, color, up = true, preBend = false)
                glyph.label?.let { drawLabel(it, cx + size * 0.62f, cy - size * 0.30f, true) }
            }

            // Bend descendente: arco hacia abajo con punta de flecha.
            TabGlyphKind.BEND_DOWN -> {
                drawBendArc(cx, cy, size, stroke, color, up = false, preBend = false)
                glyph.label?.let { drawLabel(it, cx + size * 0.62f, cy + size * 0.30f, true) }
            }

            // Pre-bend: barra vertical al inicio + arco hacia arriba con flecha.
            TabGlyphKind.PRE_BEND -> {
                drawBendArc(cx, cy, size, stroke, color, up = true, preBend = true)
                glyph.label?.let { drawLabel(it, cx + size * 0.62f, cy - size * 0.30f, true) }
            }

            // Slide: línea oblicua ascendente.
            TabGlyphKind.SLIDE -> {
                val half = size * 0.40f
                drawLine(
                    color,
                    Offset(cx - half, cy + size * 0.16f),
                    Offset(cx + half, cy - size * 0.16f),
                    stroke,
                    StrokeCap.Round
                )
            }

            // Ligadura (hammer-on / pull-off): **arco de grabado** (C.4), fino en las puntas y más
            // grueso en el centro. Se traza desde la geometría pura (dominio) y se rellena como
            // contorno, no como un trazo de grosor constante.
            TabGlyphKind.SLUR -> {
                val fromX = glyph.from.x
                val toX = glyph.to.x
                val width = kotlin.math.abs(toX - fromX)
                if (width < size * 0.2f) {
                    // Un solo anclaje: arco corto sobre la nota (también con curvatura). En la paleta
                    // el glifo no tiene anclajes, así que este es el caso que se ve: se abre a 0,84
                    // del lado para que la ligadura llene la celda como en MuseScore.
                    val from = com.grafitto.laudate.domain.tablature.Offset2(cx - size * 0.42f, cy + size * 0.06f)
                    val to = com.grafitto.laudate.domain.tablature.Offset2(cx + size * 0.42f, cy + size * 0.06f)
                    drawSlurOutline(from, to, size, color)
                } else {
                    val from = com.grafitto.laudate.domain.tablature.Offset2(fromX, cy + size * 0.10f)
                    val to = com.grafitto.laudate.domain.tablature.Offset2(toX, cy + size * 0.10f)
                    drawSlurOutline(from, to, size, color)
                }
            }

            // **Ligadura de prolongación** (B-10): arco de grabado pegado a la cuerda, entre las dos
            // cifras. Va **por debajo** de la línea de la cuerda (como MuseScore, que la cuelga hacia
            // el lado libre) y es más plano que el `SLUR`, porque une dos notas de la misma altura.
            TabGlyphKind.TIE -> {
                // El arco **no** toca las cifras: arranca en el borde derecho de la primera y acaba en
                // el borde izquierdo de la segunda, como el `Tie` de MuseScore, que se ancla al
                // contorno de la nota y no a su centro.
                val inset = size * 0.16f
                val fromX = glyph.from.x + inset
                val toX = glyph.to.x - inset
                val width = toX - fromX
                val y = cy + size * 0.16f
                if (width < size * 0.2f) {
                    // Un solo anclaje (ligadura al borde del sistema): arco corto bajo la cifra.
                    val from = com.grafitto.laudate.domain.tablature.Offset2(cx - size * 0.28f, y)
                    val to = com.grafitto.laudate.domain.tablature.Offset2(cx + size * 0.28f, y)
                    drawTieOutline(from, to, size * 0.7f, color)
                } else {
                    val from = com.grafitto.laudate.domain.tablature.Offset2(fromX, y)
                    val to = com.grafitto.laudate.domain.tablature.Offset2(toX, y)
                    drawTieOutline(from, to, size * 0.7f, color)
                }
            }

            // Armónico: rombo hueco.
            TabGlyphKind.HARMONIC -> {
                val r = size * 0.24f
                val path = Path().apply {
                    moveTo(cx, cy - r)
                    lineTo(cx + r, cy)
                    lineTo(cx, cy + r)
                    lineTo(cx - r, cy)
                    close()
                }
                drawPath(path, color, style = Stroke(width = stroke))
            }

            // Tapping: «T» sobre la nota.
            TabGlyphKind.TAPPING -> drawLabel("T", cx, cy, true)

            // Palm mute: «P.M.» en minúsculas, sobre la nota, con la **línea discontinua** que marca
            // su alcance (cuántas columnas sigue apagado el sonido).
            TabGlyphKind.PALM_MUTE -> {
                drawLabel("P.M.", cx, cy, true)
                val span = glyph.span.coerceAtLeast(1)
                if (span > 1) {
                    val y = cy + size * 0.42f
                    val startX = cx + size * 0.55f
                    val endX = cx + (span - 0.5f) * cellWidth
                    val dash = size * 0.22f
                    val gap = size * 0.16f
                    var x = startX
                    while (x < endX) {
                        drawLine(
                            color,
                            Offset(x, y),
                            Offset(minOf(x + dash, endX), y),
                            stroke * 0.8f,
                            StrokeCap.Round
                        )
                        x += dash + gap
                    }
                }
            }

            // Let ring: «l.v.» en minúsculas, sobre la nota.
            TabGlyphKind.LET_RING -> drawLabel("l.v.", cx, cy, true)

            // Trino: «tr» sobre la nota, con una pequeña ondulación a su derecha.
            TabGlyphKind.TRILL -> {
                drawLabel("tr", cx - size * 0.18f, cy, true)
                val w = size * 0.36f
                val step = w / 2f
                var x = cx + size * 0.10f
                var up = true
                repeat(2) {
                    drawLine(
                        color,
                        Offset(x, cy + if (up) size * 0.12f else -size * 0.12f),
                        Offset(x + step, cy + if (up) -size * 0.12f else size * 0.12f),
                        stroke * 0.9f,
                        StrokeCap.Round
                    )
                    x += step
                    up = !up
                }
            }

            // Arpegio: línea ondulada vertical a la izquierda del acorde.
            TabGlyphKind.ARPEGGIO -> {
                val w = size * 0.11f
                val top = cy - size * 0.55f
                val bottom = cy + size * 0.10f
                drawLine(color, Offset(cx, top), Offset(cx, bottom), stroke * 0.9f, StrokeCap.Round)
                val path = Path().apply {
                    moveTo(cx, top)
                    cubicTo(
                        cx + w * 2f, top + (bottom - top) * 0.33f,
                        cx - w * 2f, top + (bottom - top) * 0.66f,
                        cx, bottom
                    )
                }
                drawPath(path, color, style = Stroke(width = stroke * 0.9f, cap = StrokeCap.Round))
            }

            // Rake: ondulación corta antes de la nota.
            TabGlyphKind.RAKE -> {
                val left = cx - size * 0.5f
                val right = cx + size * 0.1f
                val path = Path().apply {
                    moveTo(left, cy)
                    quadraticTo(left + (right - left) * 0.25f, cy - size * 0.22f, left + (right - left) * 0.5f, cy)
                    quadraticTo(left + (right - left) * 0.75f, cy + size * 0.22f, right, cy)
                }
                drawPath(path, color, style = Stroke(width = stroke * 0.9f, cap = StrokeCap.Round))
            }

            // Armónico de pinza / pulsado: «P.H.» / «T.H.».
            TabGlyphKind.PINCH_HARMONIC -> drawLabel("P.H.", cx, cy, true)
            TabGlyphKind.TAPPED_HARMONIC -> drawLabel("T.H.", cx, cy, true)

            // Palanca: caída (arco hacia abajo), recogida (arco hacia arriba) y trémolo (ondulación).
            TabGlyphKind.WHAMMY_DIVE -> drawBendArc(cx, cy, size, stroke, color, up = false, preBend = false)
            TabGlyphKind.WHAMMY_SCOOP -> drawBendArc(cx, cy, size, stroke, color, up = true, preBend = false)
            TabGlyphKind.TREMOLO_BAR -> {
                val w = size * 0.55f
                val step = w / 4f
                val amp = size * 0.14f
                var x = cx - w / 2f
                var up = true
                repeat(4) {
                    drawLine(
                        color,
                        Offset(x, cy + if (up) amp else -amp),
                        Offset(x + step, cy + if (up) -amp else amp),
                        stroke,
                        StrokeCap.Round
                    )
                    x += step
                    up = !up
                }
            }

            // Efecto violín: «0<>». El rótulo lo dibuja el llamante; aquí solo no hay trazo extra.
            TabGlyphKind.VIOLIN -> drawLabel("0<>", cx, cy, true)

            // Dedos (mano izquierda / derecha): rótulo pequeño debajo de la cifra.
            TabGlyphKind.FRETTING_FINGER, TabGlyphKind.PICKING_FINGER ->
                glyph.label?.let { drawLabel(it, cx, cy, false) }

            // **Regulador** (*hairpin*): dos líneas que se **abren** (crescendo) o se **cierran**
            // (diminuendo) hacia la derecha, como el `HairPin` de MuseScore. La cuña nace y muere en
            // punta: el lado cerrado es un vértice, no un trazo vertical.
            TabGlyphKind.CRESCENDO, TabGlyphKind.DIMINUENDO -> {
                val fromX = glyph.from.x
                val toX = glyph.to.x
                val half = size * 0.26f
                val tip = size * 0.02f
                val opens = glyph.kind == TabGlyphKind.CRESCENDO
                val leftHalf = if (opens) tip else half
                val rightHalf = if (opens) half else tip
                drawLine(
                    color,
                    Offset(fromX, cy - leftHalf),
                    Offset(toX, cy - rightHalf),
                    stroke,
                    StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(fromX, cy + leftHalf),
                    Offset(toX, cy + rightHalf),
                    stroke,
                    StrokeCap.Round
                )
            }
        }
    }

    /**
     * Arco de **ligadura** (C.4): construye el contorno desde [TabSlurGeometry] (dominio) y lo
     * **rellena** —fino en las puntas, grueso en el centro—, como la curvatura de grabado de
     * MuseScore. [unit] es el espacio de pentagrama; aquí se deriva de [size].
     */
    /**
     * Arco de **ligadura de prolongación**: mismo contorno relleno que [drawSlurOutline] pero con la
     * panza **hacia abajo** y la curvatura plana de `TabSlurGeometry.tieArc`.
     */
    private fun DrawScope.drawTieOutline(
        from: com.grafitto.laudate.domain.tablature.Offset2,
        to: com.grafitto.laudate.domain.tablature.Offset2,
        size: Float,
        color: Color
    ) {
        val unit = size / 3.1f
        val outline = com.grafitto.laudate.domain.tablature.TabSlurGeometry.tieArc(from, to, unit)
        val path = Path().apply {
            outline.closed.forEachIndexed { index, p ->
                if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(path, color)
    }

    private fun DrawScope.drawSlurOutline(
        from: com.grafitto.laudate.domain.tablature.Offset2,
        to: com.grafitto.laudate.domain.tablature.Offset2,
        size: Float,
        color: Color
    ) {
        val unit = size / 3.1f
        val outline = com.grafitto.laudate.domain.tablature.TabSlurGeometry.arc(from, to, unit, over = true)
        val path = Path().apply {
            outline.closed.forEachIndexed { index, p ->
                if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(path, color)
    }

    /**
     * Arco de **estiramiento** (*bend*). [up] `true` = hacia arriba (bend / pre-bend), `false` = hacia
     * abajo (*bend down*). [preBend] añade la **barra vertical** del inicio, que distingue el
     * pre-bend: la cuerda ya está estirada **antes** de pulsarla.
     *
     * **Forma** (D-64). El arco de MuseScore no es un cuarto de círculo: es una **subida con
     * aceleración** que termina en una **meseta corta** horizontal, y la flecha sale de la meseta
     * apuntando en la dirección del estiramiento. Un solo `quadraticTo` daba una parábola simétrica
     * que llegaba al extremo **en diagonal**, y a tamaño de cifra eso se lee como una raya torcida en
     * vez de como un estiramiento. Se dibuja con dos tramos:
     *
     *  1. **Subida**: cúbica con el primer punto de control pegado al inicio (arranca casi vertical,
     *     como la cuerda al tensarse) y el segundo ya en la altura final (llega plano).
     *  2. **Meseta**: recta horizontal hasta la punta de la flecha.
     *
     * La flecha es un **triángulo relleno**, no dos rayas: a 1,5 pt de trazo dos rayas se cruzan y
     * forman una mancha. MuseScore usa `SymId::articBendArrow` relleno por la misma razón.
     */
    private fun DrawScope.drawBendArc(
        cx: Float,
        cy: Float,
        size: Float,
        stroke: Float,
        color: Color,
        up: Boolean,
        preBend: Boolean
    ) {
        val sign = if (up) -1f else 1f
        // El arco ocupa el ancho de la cifra y sube ~0,45 em: es la proporción de MuseScore, donde el
        // bend de un semitono y el de dos tonos se dibujan con el **mismo** alto (el intervalo va en
        // el rótulo, no en el tamaño del arco).
        val startX = cx - size * 0.40f
        val startY = cy + sign * size * 0.16f
        val plateauX = cx + size * 0.20f
        val endX = cx + size * 0.40f
        val endY = cy - sign * size * 0.30f
        val path = Path().apply {
            moveTo(startX, startY)
            // Subida: arranca vertical (cuerda tensándose) y llega plana a la meseta.
            cubicTo(
                startX + size * 0.06f, startY + (endY - startY) * 0.45f,
                plateauX - size * 0.10f, endY,
                plateauX, endY
            )
            // Meseta: la flecha sale horizontal.
            lineTo(endX, endY)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        if (preBend) {
            // Barra vertical al inicio: la cuerda ya venía estirada antes de pulsar.
            drawLine(
                color,
                Offset(startX, startY - sign * size * 0.26f),
                Offset(startX, startY + sign * size * 0.10f),
                stroke,
                StrokeCap.Round
            )
        }
        // Punta de flecha **rellena**, alineada con la meseta.
        val head = size * 0.16f
        val arrow = Path().apply {
            moveTo(endX + head * 0.55f, endY)
            lineTo(endX - head * 0.45f, endY - sign * head * 0.62f)
            lineTo(endX - head * 0.45f, endY + sign * head * 0.62f)
            close()
        }
        drawPath(arrow, color)
    }
}

/**
 * **Símbolo de técnica** como pieza Compose, para incrustarlo en la paleta del editor. Dibuja el
 * mismo glifo que el visor, de modo que lo que se ve al elegir un símbolo es lo que se verá al leer.
 *
 * Los símbolos que son **texto** (`P.M.`, `l.v.`, `tr`, `P.H.`…) se rotulan con un [Text] cuyo cuerpo
 * se **deriva del alto disponible**, no de un `sp` fijo: en la paleta la celda mide 48 dp y con los
 * 8-11 sp de antes el rótulo ocupaba 6 px de alto dentro de una caja de 48 —los «simbolitos en cajas
 * enormes»—. En el lienzo se sigue pasando [size] explícito y el rótulo se ajusta a esa caja.
 */
@Composable
fun TabTechniqueGlyph(
    kind: TabGlyphKind,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: Dp? = null
) {
    val boxModifier = if (size != null) modifier.size(size) else modifier
    val label: String? = when (kind) {
        TabGlyphKind.PALM_MUTE -> "P.M."
        TabGlyphKind.LET_RING -> "l.v."
        TabGlyphKind.TAPPING -> "T"
        TabGlyphKind.PINCH_HARMONIC -> "P.H."
        TabGlyphKind.TAPPED_HARMONIC -> "T.H."
        TabGlyphKind.TRILL -> "tr"
        TabGlyphKind.VIOLIN -> "0<>"
        else -> null
    }
    if (label != null) {
        FittedLabel(text = label, color = color, modifier = boxModifier)
    } else {
        Canvas(modifier = boxModifier) {
            val side = this.size.minDimension
            with(TabTechniquePainter) {
                drawGlyph(
                    glyph = TabGlyph(kind = kind, x = this@Canvas.size.width / 2f, y = this@Canvas.size.height / 2f),
                    color = color,
                    size = side
                ) { _: String, _: Float, _: Float, _: Boolean -> /* rótulos aparte */ }
            }
        }
    }
}

/**
 * Rótulo de texto que **se ajusta a la caja disponible**, en vez de usar un `sp` fijo.
 *
 * Se pinta con [TextMeasurer] sobre un `Canvas` —igual que los glifos SMuFL— porque un
 * `BoxWithConstraints` rompería la medición por *intrínsecos* de los paneles que envuelven la paleta
 * (`IllegalStateException: Asking for intrinsic measurements of SubcomposeLayout layouts`).
 */
@Composable
private fun FittedLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val base = TextStyle(color = color, fontWeight = FontWeight.Bold)
        val reference = measurer.measure(text, style = base.copy(fontSize = size.height.toSp()))
        if (reference.size.width <= 0 || reference.size.height <= 0) return@Canvas
        // El rótulo se escala hasta el 95 % del alto y el 92 % del ancho de la caja: **crece con la
        // celda** en vez de quedarse en los 8-11 sp fijos de antes, que en una celda de 48 dp dejaban
        // la tinta en 6 px de alto.
        val byHeight = size.height * 0.95f / reference.size.height
        val byWidth = size.width * 0.92f / reference.size.width
        val scale = minOf(byHeight, byWidth)
        val layout = measurer.measure(
            text,
            style = base.copy(fontSize = (size.height * scale).toSp())
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                x = (size.width - layout.size.width) / 2f,
                y = (size.height - layout.size.height) / 2f
            )
        )
    }
}
