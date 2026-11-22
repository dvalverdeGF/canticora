package com.grafitto.laudate.web.export

import com.grafitto.laudate.domain.export.PdfPage
import com.grafitto.laudate.domain.export.PdfRow
import com.grafitto.laudate.domain.export.PdfRowKind
import com.grafitto.laudate.domain.export.SongPdfBranding
import com.grafitto.laudate.domain.export.SongPdfLayout
import com.grafitto.laudate.domain.export.SongPdfOptions
import com.grafitto.laudate.domain.export.SongPdfDocument
import com.grafitto.laudate.web.io.ByteSink
import kotlin.math.max

/**
 * Pintor de **PDF real** para la web.
 *
 * Android usa `android.graphics.pdf` y escritorio una biblioteca de PDF; ninguna existe en Wasm.
 * Este escritor genera el fichero a mano (PDF 1.4, sin comprimir el flujo de contenido) con las
 * fuentes **base-14** que todo lector trae: Helvetica, Helvetica-Bold y Courier.
 *
 * La maquetación no se decide aquí: llega ya hecha en un [SongPdfDocument] desde el Dominio
 * ([com.grafitto.laudate.domain.export.SongPdfLayout] y
 * [com.grafitto.laudate.domain.export.CelebrationPdfLayout]), que es lo que se prueba en la JVM.
 */
object WebPdfWriter {

    private const val PAGE_WIDTH = 595.276f
    private const val PAGE_HEIGHT = 841.89f

    fun write(
        document: SongPdfDocument,
        branding: SongPdfBranding = SongPdfBranding()
    ): ByteArray {
        val options = document.options
        val pages = document.pages.ifEmpty {
            listOf(PdfPage(rows = emptyList(), index = 0, totalPages = 1))
        }
        val contents = pages.map { page ->
            paintPage(page, pages.size, document.title, branding, options)
        }

        val sink = ByteSink(4096 + contents.sumOf { it.length })
        sink.writeAscii("%PDF-1.4\n")
        val offsets = IntArray(5 + contents.size * 2 + 1)

        fun beginObject(id: Int) {
            offsets[id] = sink.length
            sink.writeAscii("$id 0 obj\n")
        }
        fun endObject() {
            sink.writeAscii("endobj\n")
        }

        val pageIdOf = { index: Int -> 6 + index * 2 }

        beginObject(1)
        sink.writeAscii("<< /Type /Catalog /Pages 2 0 R >>\n")
        endObject()

        beginObject(2)
        val kids = pages.indices.joinToString(" ") { "${pageIdOf(it)} 0 R" }
        sink.writeAscii("<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>\n")
        endObject()

        beginObject(3)
        sink.writeAscii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\n")
        endObject()
        beginObject(4)
        sink.writeAscii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\n")
        endObject()
        beginObject(5)
        sink.writeAscii("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>\n")
        endObject()

        contents.forEachIndexed { index, content ->
            val pageId = pageIdOf(index)
            beginObject(pageId)
            sink.writeAscii(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >> >> " +
                    "/Contents ${pageId + 1} 0 R >>\n"
            )
            endObject()

            beginObject(pageId + 1)
            val bytes = content.encodeLatin1()
            sink.writeAscii("<< /Length ${bytes.size} >>\nstream\n")
            sink.writeBytes(bytes)
            sink.writeAscii("\nendstream\n")
            endObject()
        }

        val xrefOffset = sink.length
        val objectCount = 5 + contents.size * 2 + 1
        sink.writeAscii("xref\n0 $objectCount\n")
        sink.writeAscii("0000000000 65535 f \n")
        for (id in 1 until objectCount) {
            sink.writeAscii(offsets[id].toString().padStart(10, '0') + " 00000 n \n")
        }
        sink.writeAscii(
            "trailer\n<< /Size $objectCount /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n"
        )
        return sink.toByteArray()
    }

    // ---------------------------------------------------------------------------------------
    // Pintado de una página
    // ---------------------------------------------------------------------------------------

    private fun paintPage(
        page: PdfPage,
        totalPages: Int,
        title: String,
        branding: SongPdfBranding,
        options: SongPdfOptions
    ): String {
        val out = StringBuilder(8192)
        val margin = 42f
        val labelWidth = 18f
        val contentLeft = margin + labelWidth
        val contentWidth = PAGE_WIDTH - margin * 2 - labelWidth
        val gap = 18f
        val columns = if (page.columns > 1 && page.secondColumnStartIndex > 0) 2 else 1
        val columnWidth = if (columns == 2) (contentWidth - gap) / 2 else contentWidth

        var top = PAGE_HEIGHT - margin

        // Banda superior con el título vivo del canto.
        page.header?.let { header ->
            val bandHeight = if (page.isTitlePage) 30f else 18f
            out.filledRect(margin, top - bandHeight, PAGE_WIDTH - margin * 2, bandHeight, branding.brandBandArgb)
            val size = if (page.isTitlePage) 13f else 9.5f
            out.text(
                font = FONT_BOLD,
                size = size,
                color = branding.onBrandBandArgb,
                x = margin + 8f,
                y = top - bandHeight / 2f - size * 0.35f,
                text = header
            )
            top -= bandHeight + 8f
        }

        page.headerRows.forEach { row ->
            val consumed = paintRow(
                out = out,
                row = row,
                top = top,
                left = contentLeft,
                width = contentWidth - 0f,
                branding = branding,
                options = options,
                scaleX = 0.95f
            )
            top -= consumed
        }
        if (page.headerRows.isNotEmpty()) top -= 4f

        val rows = page.rows
        val split = if (columns == 2) page.secondColumnStartIndex else -1
        val first = if (split > 0) rows.subList(0, split) else rows
        val second = if (split > 0) rows.subList(split, rows.size) else emptyList()

        paintColumn(out, first, top, contentLeft, columnWidth, branding, options)
        if (second.isNotEmpty()) {
            paintColumn(out, second, top, contentLeft + columnWidth + gap, columnWidth, branding, options)
        }

        // Pie: marca y paginación.
        val footerY = margin * 0.62f
        out.line(
            x1 = margin,
            y1 = footerY + 10f,
            x2 = PAGE_WIDTH - margin,
            y2 = footerY + 10f,
            width = 0.5f,
            color = branding.ruleArgb
        )
        out.text(FONT_NORMAL, 7.5f, branding.mutedArgb, margin, footerY, "Canticora")
        val label = "Página ${page.index + 1} de $totalPages"
        out.text(
            FONT_NORMAL,
            7.5f,
            branding.mutedArgb,
            PAGE_WIDTH - margin - out.widthOf(FONT_NORMAL, label, 7.5f),
            footerY,
            label
        )
        if (title.isNotBlank()) {
            val centerLabel = title
            out.text(
                FONT_NORMAL,
                7.5f,
                branding.mutedArgb,
                (PAGE_WIDTH - out.widthOf(FONT_NORMAL, centerLabel, 7.5f)) / 2f,
                footerY,
                centerLabel
            )
        }
        return out.toString()
    }

    private fun paintColumn(
        out: StringBuilder,
        rows: List<PdfRow>,
        top: Float,
        left: Float,
        width: Float,
        branding: SongPdfBranding,
        options: SongPdfOptions
    ) {
        var cursor = top
        rows.forEach { row ->
            cursor -= paintRow(out, row, cursor, left, width, branding, options, scaleX = 1f)
        }
    }

    /** Pinta una fila y devuelve la altura consumida. */
    private fun paintRow(
        out: StringBuilder,
        row: PdfRow,
        top: Float,
        left: Float,
        width: Float,
        branding: SongPdfBranding,
        options: SongPdfOptions,
        scaleX: Float
    ): Float {
        if (row.kind == PdfRowKind.SPACER) return row.heightPt + row.leadingGapPt

        var consumed = row.leadingGapPt
        val bodyTop = top - row.leadingGapPt
        val height = max(row.heightPt, 4f)

        row.marginLabel?.let { label ->
            val sectionHeight = if (row.sectionHeightPt > 0f) row.sectionHeightPt else height
            out.rotatedText(
                font = FONT_NORMAL,
                size = 6.5f,
                color = branding.mutedArgb,
                x = left - 12f,
                y = bodyTop - sectionHeight / 2f - out.widthOf(FONT_NORMAL, label, 6.5f) / 2f,
                text = label
            )
        }
        if (row.sectionRule) {
            out.line(
                x1 = left - 5f,
                y1 = bodyTop,
                x2 = left - 5f,
                y2 = bodyTop - height,
                width = 0.5f,
                color = branding.ruleArgb
            )
        }
        row.repeatMark?.let { mark ->
            out.text(FONT_NORMAL, 6.5f, branding.mutedArgb, left - 4f, bodyTop - 7f, mark)
        }

        when (row.kind) {
            PdfRowKind.TITLE -> {
                val size = if (row.fontSizePt > 0f) row.fontSizePt else 20f
                out.alignedText(
                    font = FONT_BOLD,
                    size = size,
                    color = branding.primaryArgb,
                    x = left,
                    baseline = bodyTop - size,
                    width = width,
                    text = row.text,
                    centered = row.centered
                )
            }

            PdfRowKind.METADATA -> {
                val size = if (row.fontSizePt > 0f) row.fontSizePt else 8f
                out.alignedText(
                    font = FONT_NORMAL,
                    size = size,
                    color = branding.mutedArgb,
                    x = left,
                    baseline = bodyTop - size,
                    width = width,
                    text = row.text,
                    centered = row.centered
                )
            }

            PdfRowKind.SECTION_HEADER -> {
                val size = if (row.fontSizePt > 0f) row.fontSizePt else 9.5f
                out.alignedText(
                    font = FONT_BOLD,
                    size = size,
                    color = branding.primaryArgb,
                    x = left,
                    baseline = bodyTop - size,
                    width = width,
                    text = row.text,
                    centered = row.centered
                )
            }

            PdfRowKind.LYRICS -> {
                val size = row.fontSizePt.takeIf { it > 0f } ?: options.lyricsFontSizePt
                val font = if (row.emphasis) FONT_BOLD else FONT_NORMAL
                out.alignedText(
                    font = font,
                    size = size,
                    color = branding.textArgb,
                    x = left,
                    baseline = bodyTop - size,
                    width = width,
                    text = row.text,
                    centered = row.centered
                )
            }

            PdfRowKind.COMMENT -> {
                val size = if (row.fontSizePt > 0f) row.fontSizePt else 8.5f
                out.alignedText(
                    font = FONT_NORMAL,
                    size = size,
                    color = branding.mutedArgb,
                    x = left,
                    baseline = bodyTop - size,
                    width = width,
                    text = row.text,
                    centered = row.centered
                )
            }

            PdfRowKind.RHYTHM -> {
                val size = if (row.fontSizePt > 0f) row.fontSizePt else 10f
                out.alignedText(
                    font = FONT_MONO,
                    size = size,
                    color = branding.textArgb,
                    x = left,
                    baseline = bodyTop - size,
                    width = width,
                    text = row.text,
                    centered = row.centered
                )
            }

            PdfRowKind.CHORDS_OVER_LYRICS -> {
                // Misma maqueta que el exportador de escritorio: si la fila va **centrada** los
                // acordes arrancan donde arranca la letra (no en el margen), y cada acorde se
                // coloca midiendo el prefijo de letra que tiene delante.
                val size = row.fontSizePt.takeIf { it > 0f } ?: options.lyricsFontSizePt
                val chordSize = options.chordsFontSizePt
                val lyricsFont = if (row.emphasis) FONT_BOLD else FONT_NORMAL
                val lineStart = if (row.centered && row.text.isNotBlank()) {
                    (left + (width - out.widthOf(lyricsFont, row.text, size)) / 2f).coerceAtLeast(left)
                } else {
                    left
                }
                val measured = row.chords.map { mark ->
                    val prefix = row.text.substring(
                        0,
                        mark.characterIndex.coerceIn(0, row.text.length)
                    )
                    mark.copy(
                        xOffsetPt = out.widthOf(lyricsFont, prefix, size),
                        widthPt = out.widthOf(FONT_BOLD, mark.text, chordSize)
                    )
                }
                val chordBaseline = bodyTop - chordSize
                SongPdfLayout.distributeChordGaps(measured, options.minChordGapPt).forEach { mark ->
                    out.text(
                        FONT_BOLD,
                        chordSize,
                        branding.primaryArgb,
                        lineStart + mark.xOffsetPt,
                        chordBaseline,
                        mark.text
                    )
                }
                out.text(
                    lyricsFont,
                    size,
                    branding.textArgb,
                    lineStart,
                    chordBaseline - chordSize * 0.22f - size,
                    row.text
                )
            }

            PdfRowKind.CHORD_ONLY -> {
                val chordSize = row.fontSizePt.takeIf { it > 0f } ?: options.chordsFontSizePt
                row.chords.forEach { chord ->
                    out.text(
                        FONT_BOLD,
                        chordSize,
                        branding.primaryArgb,
                        left + chord.xOffsetPt.coerceAtLeast(0f),
                        bodyTop - chordSize,
                        chord.text
                    )
                }
            }

            PdfRowKind.TABLATURE -> {
                val drawing = row.tablature
                if (drawing == null || drawing.width <= 0f) {
                    consumed += height
                } else {
                    val available = width + row.tablatureInsetPt
                    var scale = available / drawing.width
                    if (drawing.height * scale > height) scale = height / drawing.height
                    val originX = left - row.tablatureInsetPt
                    val originY = bodyTop - height
                    paintTablature(out, drawing, originX, originY, scale, branding)
                    consumed += height
                }
                return consumed
            }

            PdfRowKind.SPACER -> Unit
        }

        consumed += height
        return consumed
    }

    private fun paintTablature(
        out: StringBuilder,
        drawing: com.grafitto.laudate.domain.tablature.TablatureDrawing,
        originX: Float,
        originY: Float,
        scale: Float,
        branding: SongPdfBranding
    ) {
        val ink = branding.textArgb
        drawing.lines.forEach { line ->
            out.line(
                x1 = originX + line.x1 * scale,
                y1 = originY + (drawing.height - line.y1) * scale,
                x2 = originX + line.x2 * scale,
                y2 = originY + (drawing.height - line.y2) * scale,
                width = if (line.isBar) 0.8f else 0.4f,
                color = ink
            )
        }
        val size = (8.5f * scale).coerceIn(4f, 10f)
        drawing.texts.forEach { text ->
            val x = originX + text.x * scale
            val y = originY + (drawing.height - text.y) * scale
            if (text.rotated) {
                out.rotatedText(FONT_MONO, size, ink, x, y, text.text)
            } else {
                val offset = when (text.align) {
                    com.grafitto.laudate.domain.tablature.TabTextAlign.LEFT -> 0f
                    com.grafitto.laudate.domain.tablature.TabTextAlign.CENTER ->
                        -out.widthOf(FONT_MONO, text.text, size) / 2f
                }
                out.text(FONT_MONO, size, ink, x + offset, y, text.text)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Operadores de contenido
    // ---------------------------------------------------------------------------------------

    private fun StringBuilder.text(
        font: String,
        size: Float,
        color: Int,
        x: Float,
        y: Float,
        text: String
    ) {
        if (text.isEmpty()) return
        val (r, g, b) = rgb(color)
        append("BT /").append(font).append(' ').append(fmt(size)).append(" Tf ")
        append(fmt(r)).append(' ').append(fmt(g)).append(' ').append(fmt(b)).append(" rg ")
        append("1 0 0 1 ").append(fmt(x)).append(' ').append(fmt(y)).append(" Tm (")
        append(escape(text)).append(") Tj ET\n")
    }

    private fun StringBuilder.rotatedText(
        font: String,
        size: Float,
        color: Int,
        x: Float,
        y: Float,
        text: String
    ) {
        if (text.isEmpty()) return
        val (r, g, b) = rgb(color)
        append("BT /").append(font).append(' ').append(fmt(size)).append(" Tf ")
        append(fmt(r)).append(' ').append(fmt(g)).append(' ').append(fmt(b)).append(" rg ")
        append("0 1 -1 0 ").append(fmt(x)).append(' ').append(fmt(y)).append(" Tm (")
        append(escape(text)).append(") Tj ET\n")
    }

    private fun StringBuilder.alignedText(
        font: String,
        size: Float,
        color: Int,
        x: Float,
        baseline: Float,
        width: Float,
        text: String,
        centered: Boolean
    ) {
        val start = if (centered && text.isNotBlank()) {
            x + (width - widthOf(font, text, size)) / 2f
        } else {
            x
        }
        text(font, size, color, start.coerceAtLeast(x - 2f), baseline, text)
    }

    private fun StringBuilder.line(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        width: Float,
        color: Int
    ) {
        val (r, g, b) = rgb(color)
        append("q ").append(fmt(width)).append(" w ")
        append(fmt(r)).append(' ').append(fmt(g)).append(' ').append(fmt(b)).append(" RG ")
        append(fmt(x1)).append(' ').append(fmt(y1)).append(" m ")
        append(fmt(x2)).append(' ').append(fmt(y2)).append(" l S Q\n")
    }

    private fun StringBuilder.filledRect(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        color: Int
    ) {
        val (r, g, b) = rgb(color)
        append("q ").append(fmt(r)).append(' ').append(fmt(g)).append(' ').append(fmt(b)).append(" rg ")
        append(fmt(x)).append(' ').append(fmt(y)).append(' ')
        append(fmt(width)).append(' ').append(fmt(height)).append(" re f Q\n")
    }

    /**
     * Anchuras **reales** de las fuentes base-14 en milésimas de em, para los códigos WinAnsi
     * 32..255 (índice 0 = espacio).
     *
     * Son las métricas con las que el lector compone el texto, así que son las que hay que usar
     * al medir: con una aproximación por carácter los acordes acababan hasta un centímetro a la
     * derecha de su sílaba. Helvetica y Helvetica-Bold vienen del AFM de Adobe; Courier es
     * monoespaciada (600 para todos los glifos).
     */
    private val HELVETICA_WIDTHS = intArrayOf(
        278, 278, 355, 556, 556, 889, 667, 191, 333, 333, 389, 584, 278, 333, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 278, 278, 584, 584, 584, 556,
        1015, 667, 667, 722, 722, 667, 611, 778, 722, 278, 500, 667, 556, 833, 722, 778,
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 278, 278, 278, 469, 556,
        333, 556, 556, 500, 556, 556, 278, 556, 556, 222, 222, 500, 222, 833, 556, 556,
        556, 556, 333, 500, 278, 556, 500, 722, 500, 500, 500, 334, 260, 334, 584, 0,
        556, 0, 222, 556, 333, 1000, 556, 556, 333, 1000, 667, 333, 1000, 0, 611, 0,
        0, 222, 222, 333, 333, 350, 556, 1000, 333, 1000, 500, 333, 944, 0, 500, 667,
        278, 333, 556, 556, 556, 556, 260, 556, 333, 737, 370, 556, 584, 333, 737, 333,
        400, 584, 333, 333, 333, 556, 537, 278, 333, 333, 365, 556, 834, 834, 834, 611,
        667, 667, 667, 667, 667, 667, 1000, 722, 667, 667, 667, 667, 278, 278, 278, 278,
        722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611,
        556, 556, 556, 556, 556, 556, 889, 500, 556, 556, 556, 556, 278, 278, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 584, 611, 556, 556, 556, 556, 500, 556, 500,
    )

    private val HELVETICA_BOLD_WIDTHS = intArrayOf(
        278, 333, 474, 556, 556, 889, 722, 238, 333, 333, 389, 584, 278, 333, 278, 278,
        556, 556, 556, 556, 556, 556, 556, 556, 556, 556, 333, 333, 584, 584, 584, 611,
        975, 722, 722, 722, 722, 667, 611, 778, 722, 278, 556, 722, 611, 833, 722, 778,
        667, 778, 722, 667, 611, 722, 667, 944, 667, 667, 611, 333, 278, 333, 584, 556,
        333, 556, 611, 556, 611, 556, 333, 611, 611, 278, 278, 556, 278, 889, 611, 611,
        611, 611, 389, 556, 333, 611, 556, 778, 556, 556, 500, 389, 280, 389, 584, 0,
        556, 0, 278, 556, 500, 1000, 556, 556, 333, 1000, 667, 333, 1000, 0, 611, 0,
        0, 278, 278, 500, 500, 350, 556, 1000, 333, 1000, 556, 333, 944, 0, 500, 667,
        278, 333, 556, 556, 556, 556, 280, 556, 333, 737, 370, 556, 584, 333, 737, 333,
        400, 584, 333, 333, 333, 611, 556, 278, 333, 333, 365, 556, 834, 834, 834, 611,
        722, 722, 722, 722, 722, 722, 1000, 722, 667, 667, 667, 667, 278, 278, 278, 278,
        722, 722, 778, 778, 778, 778, 778, 584, 778, 722, 722, 722, 722, 667, 667, 611,
        556, 556, 556, 556, 556, 556, 889, 556, 556, 556, 556, 556, 278, 278, 278, 278,
        611, 611, 611, 611, 611, 611, 611, 584, 611, 611, 611, 611, 611, 556, 611, 556,
    )

    private const val MONO_WIDTH = 600

    /** Ancho en milésimas de em del glifo [character], con respaldo para lo no listado. */
    private fun widthUnits(character: Char, font: String): Int {
        if (font == FONT_MONO) return MONO_WIDTH
        val table = if (font == FONT_BOLD) HELVETICA_BOLD_WIDTHS else HELVETICA_WIDTHS
        val index = character.code - 32
        val units = if (index in 0..223) table[index] else 0
        return if (units == 0) 556 else units
    }

    /** Ancho del texto en puntos con las métricas reales de [font]. */
    private fun StringBuilder.widthOf(font: String, text: String, size: Float): Float {
        var units = 0
        text.forEach { character -> units += widthUnits(character, font) }
        return units * size / 1000f
    }

    private fun rgb(argb: Int): Triple<Float, Float, Float> = Triple(
        ((argb shr 16) and 0xFF) / 255f,
        ((argb shr 8) and 0xFF) / 255f,
        (argb and 0xFF) / 255f
    )

    private fun fmt(value: Float): String {
        val scaled = (value * 100f).toInt()
        val whole = scaled / 100
        val cents = scaled % 100
        if (cents == 0) return whole.toString()
        val sign = if (scaled < 0 && whole == 0) "-" else ""
        return "$sign$whole.${if (cents < 0) -cents else cents}".let { text ->
            val dot = text.indexOf('.')
            text.substring(0, dot + 1) + text.substring(dot + 1).padStart(2, '0')
        }
    }

    private fun escape(text: String): String {
        val out = StringBuilder(text.length + 8)
        text.forEach { character ->
            when (character) {
                '(' -> out.append("\\(")
                ')' -> out.append("\\)")
                '\\' -> out.append("\\\\")
                '\n', '\r' -> out.append(' ')
                else -> out.append(if (character.code in 32..255) character else '?')
            }
        }
        return out.toString()
    }

    private fun String.encodeLatin1(): ByteArray {
        val bytes = ByteArray(length)
        forEachIndexed { index, character ->
            val code = character.code
            bytes[index] = when {
                code in 32..255 -> code.toByte()
                // Los saltos de línea son parte de la estructura del PDF: nunca se sustituyen.
                code == '\n'.code || code == '\r'.code || code == '\t'.code -> code.toByte()
                else -> '?'.code.toByte()
            }
        }
        return bytes
    }

    private fun ByteSink.writeAscii(text: String) {
        writeBytes(text.encodeLatin1())
    }

    private const val FONT_NORMAL = "F1"
    private const val FONT_BOLD = "F2"
    private const val FONT_MONO = "F3"
}
