package com.grafitto.laudate.ui.tablature

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.grafitto.laudate.domain.tablature.MinimStyle
import com.grafitto.laudate.domain.tablature.TabGlyphs
import com.grafitto.laudate.domain.tablature.TabRhythmGeometry
import com.grafitto.laudate.domain.tablature.TabRhythmGeometry.StemDirection
import com.grafitto.laudate.domain.tablature.TabRhythmMark
import com.grafitto.laudate.domain.tablature.TabRhythmPrimitive
import com.grafitto.laudate.domain.tablature.TabRhythmPrimitives

/**
 * Pintor de las **figuras de ritmo** de la tablatura: la fila que va **debajo de las cuerdas** con las
 * **plicas y barras** de cada columna, como en MuseScore con el tipo `Common` (`stemsDown=1`).
 *
 * **Corrección clave (2026-10-12, medido del `salmo2.pdf`)**: en el tipo `Stems and beams` **no hay
 * cabezas de nota**. La cifra de traste **es** la cabeza; debajo solo van las **plicas** (que cuelgan
 * de la cuerda inferior) y las **barras** de unión. Dibujar óvalos era el «punto sin sentido» que
 * sobraba. Las cabezas solo se pintan en el tipo `Note symbols` ([showNoteheads]).
 *
 * Es un `DrawScope` puro: lo usan **el visor** ([TablatureView]) y **el editor** ([TablatureCanvas]),
 * de modo que la fila de ritmo se dibuja **igual** en los dos.
 *
 * **La geometría NO se decide aquí** (D-42). Desde que el PDF también tiene que dibujar la fila de
 * ritmo, la posición de cada plica, barra, corchete y glifo la calcula el Dominio
 * ([TabRhythmPrimitives]) y este objeto solo **traza** lo que le llega. Así la pantalla y el papel
 * salen del **mismo** cálculo y no hay dos grabados que se puedan desincronizar.
 */
object TabRhythmPainter {

    /**
     * Dibuja todas las figuras de [marks], las **barras de unión** de [beamGroups] y los **corchetes de
     * grupo irregular** de [tuplets].
     *
     * @param staffSpace **un espacio de pentagrama** en px (el paso de las cuerdas / 1,5). Es la
     *   unidad de la plica, las banderas y las barras.
     * @param stemDirection dirección de la plica: **abajo** en el pentagrama (cuelga de la cuerda
     *   inferior); **arriba** en los iconos de la barra de herramientas (para que no se recorten).
     * @param showNoteheads dibuja la **cabeza** de nota. `false` en `Stems and beams` (MuseScore): la
     *   cabeza es la propia cifra y debajo solo van las plicas y barras.
     */
    fun DrawScope.drawRhythmFigures(
        marks: List<TabRhythmMark>,
        offsetY: Float,
        color: Color,
        staffSpace: Float,
        measurer: TextMeasurer,
        labelStyle: TextStyle,
        /** Familia **musical** (Bravura). Si es `null`, no se pinta (p. ej. en un boceto). */
        music: FontFamily? = null,
        stemDirection: StemDirection = StemDirection.DOWN,
        showNoteheads: Boolean = false,
        /**
         * **Grupos de barras de unión**, como índices sobre [marks]. Los decide el Dominio
         * (`TabBeamGrouping` del Dominio) porque dependen del **compás**: en
         * 4/4 las corcheas se unen de cuatro en cuatro. Vacío = ninguna figura se une (todas con
         * bandera), que es lo correcto para una figura suelta.
         */
        beamGroups: List<List<Int>> = emptyList(),
        /**
         * **Corchetes de grupo irregular** ya resueltos por el Dominio ([TabRhythmGeometry.TupletBracket]):
         * la línea que agrupa las figuras del grupo, sus ganchos y su sitio. Se dibujan **una vez
         * por grupo**, no por figura: la cifra va **encima de la línea**, que se interrumpe a su paso.
         */
        tuplets: List<TabRhythmGeometry.TupletBracket> = emptyList(),
        /** Cómo se dibuja la **blanca** ([MinimStyle]): sin plica, con plica corta o rayada. */
        minimStyle: MinimStyle = MinimStyle.SHORTER
    ) {
        drawRhythmPrimitives(
            primitives = TabRhythmPrimitives.of(
                marks = marks,
                staffSpace = staffSpace,
                offsetY = offsetY,
                beamGroups = beamGroups,
                tuplets = tuplets,
                stemDirection = stemDirection,
                showNoteheads = showNoteheads,
                minimStyle = minimStyle
            ),
            color = color,
            measurer = measurer,
            labelStyle = labelStyle,
            music = music
        )
    }

    /**
     * Traza una lista de **primitivas** ya calculadas por el Dominio.
     *
     * Es el único punto de dibujo de la fila de ritmo: si mañana hay que pintarla en otro sitio
     * (una miniatura, un PDF), se reutiliza esto y no se duplica la geometría.
     *
     * @param music familia **musical** (Bravura). Con `null` los glifos se omiten, pero las líneas y
     *   los puntillos se dibujan igual: así un boceto sin fuente sigue mostrando el ritmo.
     */
    fun DrawScope.drawRhythmPrimitives(
        primitives: List<TabRhythmPrimitive>,
        color: Color,
        measurer: TextMeasurer,
        labelStyle: TextStyle,
        music: FontFamily? = null
    ) {
        primitives.forEach { primitive ->
            when (primitive) {
                is TabRhythmPrimitive.Line -> drawLine(
                    color,
                    Offset(primitive.x1, primitive.y1),
                    Offset(primitive.x2, primitive.y2),
                    strokeWidth = primitive.thickness,
                    cap = StrokeCap.Butt
                )

                is TabRhythmPrimitive.Dot -> drawCircle(
                    color,
                    radius = primitive.radius,
                    center = Offset(primitive.x, primitive.y)
                )

                // Paralelogramo **relleno**: borde derecho vertical y borde izquierdo más bajo, que
                // es la inclinación de la barra de la blanca en MuseScore.
                is TabRhythmPrimitive.Slash -> {
                    val half = primitive.width / 2f
                    val path = Path().apply {
                        moveTo(primitive.x + half, primitive.y)
                        lineTo(primitive.x + half, primitive.y + primitive.thickness)
                        lineTo(
                            primitive.x - half,
                            primitive.y + primitive.thickness + primitive.slant
                        )
                        lineTo(primitive.x - half, primitive.y + primitive.slant)
                        close()
                    }
                    drawPath(path, color)
                }

                is TabRhythmPrimitive.Glyph -> {
                    if (music == null) return@forEach
                    val layout = measurer.measure(
                        text = primitive.glyph,
                        style = TextStyle(
                            color = color,
                            // **px → sp con la densidad** (2026-10-30). `emPx` está en **píxeles**, y
                            // `Float.sp` interpreta el número como **sp**: en una tableta de densidad
                            // 3,0 los silencios y los corchetes salían **tres veces** más grandes que la
                            // tablatura. El pintor de textos del lienzo ya hacía esta conversión con
                            // `density`; aquí se había quedado sin ella. `DrawScope` **es** un `Density`,
                            // así que `toSp()` es la conversión correcta.
                            fontSize = primitive.emPx.toSp(),
                            fontFamily = music
                        )
                    )
                    // `leftShiftFraction` = 0 → centrado; > 0 → el borde izquierdo cae esa fracción
                    // del ancho a la izquierda de `x` (el corchete se pega a la plica).
                    val left = if (primitive.leftShiftFraction == 0f) {
                        primitive.x - layout.size.width / 2f
                    } else {
                        primitive.x - layout.size.width * primitive.leftShiftFraction
                    }
                    drawText(
                        layout,
                        topLeft = Offset(left, inkBaselineTopLeftY(layout, primitive.baselineY))
                    )
                }

                is TabRhythmPrimitive.Tuplet -> drawTuplet(
                    primitive,
                    color,
                    measurer,
                    labelStyle,
                    music
                )
            }
        }
    }

    /**
     * El **corchete de grupo irregular**: la **línea** que agrupa las figuras, con un **gancho** hacia
     * abajo en cada extremo, y la cifra **encima**, con el glifo SMuFL `tuplet3` (`\uE883`) — **no** el
     * carácter `3` de una fuente de texto, que tiene otra altura y otro trazo.
     *
     * La línea se **interrumpe** a los lados de la cifra, como en MuseScore: medido del `salmo2.pdf`,
     * el hueco es de 0,5 espacios por lado. La cifra se centra **en la línea** (su tinta, no su caja de
     * línea: `tuplet3` tiene su centro a `+0,184 em` de la línea base).
     */
    private fun DrawScope.drawTuplet(
        tuplet: TabRhythmPrimitive.Tuplet,
        color: Color,
        measurer: TextMeasurer,
        labelStyle: TextStyle,
        music: FontFamily?
    ) {
        val centerX = (tuplet.x1 + tuplet.x2) / 2f

        // ── La cifra ──────────────────────────────────────────────────────────────────────────────
        // Con `SHOW_NUMBER` es el **numerador** de la proporción (3, 5, 6, 7, 9…) y se pinta con el
        // glifo SMuFL `tuplet3`…`tuplet9`. Con `SHOW_RELATION` es la proporción completa (`3:2`), que
        // **no** tiene glifo: se compone con la tipografía de texto. Con `NO_TEXT` no hay cifra.
        val labelGlyph = tuplet.labelGlyph
        val labelText = tuplet.labelText
        val number = when {
            labelText == null -> null
            labelGlyph != null && music != null -> measurer.measure(
                text = labelGlyph,
                style = TextStyle(
                    color = color,
                    // Igual que los glifos de figura: `labelEmPx` está en píxeles y hay que pasarlo a
                    // `sp` con la densidad (antes salía 3× en tableta).
                    fontSize = tuplet.labelEmPx.toSp(),
                    fontFamily = music
                )
            )
            else -> measurer.measure(labelText, labelStyle)
        }

        if (tuplet.hasBracket) {
            // El hueco de la cifra solo se abre si hay cifra que meter.
            val gap = number?.let { it.size.width / 2f + tuplet.gapPx } ?: 0f
            // La línea, en dos tramos, con el hueco de la cifra en medio.
            drawLine(
                color,
                Offset(tuplet.x1, tuplet.y),
                Offset(centerX - gap, tuplet.y),
                strokeWidth = tuplet.thickness,
                cap = StrokeCap.Butt
            )
            drawLine(
                color,
                Offset(centerX + gap, tuplet.y),
                Offset(tuplet.x2, tuplet.y),
                strokeWidth = tuplet.thickness,
                cap = StrokeCap.Butt
            )
            // Los ganchos, hacia abajo en los dos extremos.
            drawLine(
                color,
                Offset(tuplet.x1, tuplet.y),
                Offset(tuplet.x1, tuplet.y + tuplet.hook),
                strokeWidth = tuplet.thickness,
                cap = StrokeCap.Butt
            )
            drawLine(
                color,
                Offset(tuplet.x2, tuplet.y),
                Offset(tuplet.x2, tuplet.y + tuplet.hook),
                strokeWidth = tuplet.thickness,
                cap = StrokeCap.Butt
            )
        }

        if (number != null) {
            // La cifra, con su **tinta** centrada en la línea (o en el vuelo del corchete si no lo hay).
            val ink = tuplet.labelGlyph?.let { TabGlyphs.inkOf(it) }
            val baseline = tuplet.y + (ink?.centerFromBaseline ?: 0f) * tuplet.labelEmPx
            drawText(
                number,
                topLeft = Offset(centerX - number.size.width / 2f, inkBaselineTopLeftY(number, baseline))
            )
        }
    }
}
