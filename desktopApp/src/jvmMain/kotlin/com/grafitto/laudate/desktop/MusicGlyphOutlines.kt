package com.grafitto.laudate.desktop

import com.grafitto.laudate.domain.export.SongPdfLayout
import org.apache.pdfbox.pdmodel.PDPageContentStream
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.geom.PathIterator

/**
 * Contornos vectoriales de los glifos **SMuFL** de Bravura, para el PDF de escritorio.
 *
 * **Por qué no se embebe la fuente.** Bravura es un OpenType con contornos **CFF** y PDFBox 3
 * rechaza embeberlos (`True Type fonts using CFF outlines are not supported`). En vez de convertir
 * la fuente —lo que obligaría a versionar un binario derivado y a renombrarla por la licencia
 * OFL—, se extraen los **contornos** con AWT y se trazan como **caminos vectoriales** en el PDF.
 * El resultado impreso es idéntico (misma forma, misma métrica), el PDF no depende de que el equipo
 * tenga una fuente musical instalada y no se añade ningún recurso nuevo al repositorio.
 *
 * Las coordenadas se guardan en **unidades de fuente** (em = 1000, `y` hacia **abajo**, con el
 * origen en la línea base), así que un mismo contorno sirve para cualquier tamaño: se escala por
 * `tamaño / 1000` al trazarlo.
 */
internal class MusicGlyphOutlines private constructor(private val font: Font) {

    /** Una orden de trazado, en unidades de fuente. */
    private sealed interface Op {
        data class Move(val x: Float, val y: Float) : Op
        data class Line(val x: Float, val y: Float) : Op
        data class Cubic(
            val x1: Float,
            val y1: Float,
            val x2: Float,
            val y2: Float,
            val x: Float,
            val y: Float
        ) : Op

        data object Close : Op
    }

    /** Contorno de un glifo: su **avance** y sus órdenes de trazado. */
    private class Outline(val advance: Float, val ops: List<Op>)

    private val cache = HashMap<String, Outline?>()

    /** **Avance** del glifo en unidades de fuente (`0` si no se pudo extraer). */
    fun advanceOf(glyph: String): Float = outlineOf(glyph)?.advance ?: 0f

    /**
     * Traza el glifo relleno, con su **borde izquierdo** en [x] y su **línea base** en [baselineY]
     * (coordenada **desde arriba**, como todo el Dominio).
     */
    fun draw(
        cs: PDPageContentStream,
        glyph: String,
        x: Float,
        baselineY: Float,
        size: Float
    ) {
        val outline = outlineOf(glyph) ?: return
        val k = size / FONT_UNITS
        outline.ops.forEach { op ->
            when (op) {
                is Op.Move -> cs.moveTo(x + op.x * k, flipY(baselineY + op.y * k))
                is Op.Line -> cs.lineTo(x + op.x * k, flipY(baselineY + op.y * k))
                is Op.Cubic -> cs.curveTo(
                    x + op.x1 * k, flipY(baselineY + op.y1 * k),
                    x + op.x2 * k, flipY(baselineY + op.y2 * k),
                    x + op.x * k, flipY(baselineY + op.y * k)
                )
                Op.Close -> cs.closePath()
            }
        }
        // `f` = relleno con la regla **non-zero**, la misma que usan TrueType y CFF: los huecos de
        // la `o` (y del `3` del grupo irregular) quedan huecos.
        cs.fill()
    }

    private fun outlineOf(glyph: String): Outline? = cache.getOrPut(glyph) { extract(glyph) }

    private fun extract(glyph: String): Outline? = runCatching {
        val em = font.deriveFont(FONT_UNITS)
        val vector = em.createGlyphVector(FontRenderContext(null, true, true), glyph)
        val advance = vector.getGlyphMetrics(0).advanceX
        val ops = ArrayList<Op>(32)
        val iterator = vector.getOutline().getPathIterator(null)
        val c = DoubleArray(6)
        var startX = 0f
        var startY = 0f
        var lastX = 0f
        var lastY = 0f
        while (!iterator.isDone) {
            when (iterator.currentSegment(c)) {
                PathIterator.SEG_MOVETO -> {
                    startX = c[0].toFloat()
                    startY = c[1].toFloat()
                    lastX = startX
                    lastY = startY
                    ops += Op.Move(startX, startY)
                }
                PathIterator.SEG_LINETO -> {
                    lastX = c[0].toFloat()
                    lastY = c[1].toFloat()
                    ops += Op.Line(lastX, lastY)
                }
                PathIterator.SEG_QUADTO -> {
                    // PDF no tiene curvas **cuadráticas**: se elevan a cúbicas con los puntos de
                    // control a 2/3 del camino hacia el de la cuadrática.
                    val qx = c[0].toFloat()
                    val qy = c[1].toFloat()
                    val ex = c[2].toFloat()
                    val ey = c[3].toFloat()
                    ops += Op.Cubic(
                        lastX + 2f / 3f * (qx - lastX),
                        lastY + 2f / 3f * (qy - lastY),
                        ex + 2f / 3f * (qx - ex),
                        ey + 2f / 3f * (qy - ey),
                        ex,
                        ey
                    )
                    lastX = ex
                    lastY = ey
                }
                PathIterator.SEG_CUBICTO -> {
                    lastX = c[4].toFloat()
                    lastY = c[5].toFloat()
                    ops += Op.Cubic(
                        c[0].toFloat(), c[1].toFloat(),
                        c[2].toFloat(), c[3].toFloat(),
                        lastX, lastY
                    )
                }
                PathIterator.SEG_CLOSE -> {
                    ops += Op.Close
                    lastX = startX
                    lastY = startY
                }
            }
            iterator.next()
        }
        Outline(advance, ops)
    }.getOrNull()

    companion object {
        /** Unidades por em de Bravura (`head.unitsPerEm`). */
        const val FONT_UNITS: Float = 1000f

        /**
         * Ruta de **Bravura** dentro del classpath. Los recursos de Compose Multiplatform se
         * empaquetan bajo `composeResources/<paquete de la clase Res>/…`, y `core:ui` declara
         * `packageOfResClass = "com.grafitto.laudate.ui.generated.resources"`.
         */
        const val BRAVURA_RESOURCE =
            "/composeResources/com.grafitto.laudate.ui.generated.resources/font/bravura.otf"

        /** Carga Bravura del classpath; `null` si el recurso no está (instalación incompleta). */
        fun load(): MusicGlyphOutlines? = runCatching {
            val stream = MusicGlyphOutlines::class.java.getResourceAsStream(BRAVURA_RESOURCE)
                ?: return@runCatching null
            MusicGlyphOutlines(stream.use { Font.createFont(Font.TRUETYPE_FONT, it) })
        }.getOrNull()
    }
}

/** Pasa una coordenada **desde arriba** (la del Dominio) a la del PDF, que cuenta desde abajo. */
private fun flipY(y: Float): Float = SongPdfLayout.PAGE_HEIGHT_PT - y
