package com.grafitto.laudate.desktop

import com.grafitto.laudate.domain.export.CelebrationPdfLayout
import com.grafitto.laudate.domain.export.CelebrationPdfSong
import com.grafitto.laudate.domain.export.PdfPage
import com.grafitto.laudate.domain.export.PdfRow
import com.grafitto.laudate.domain.export.PdfRowKind
import com.grafitto.laudate.domain.model.LaudateBrand
import com.grafitto.laudate.domain.export.SongPdfBranding
import com.grafitto.laudate.domain.export.SongPdfDocument
import com.grafitto.laudate.domain.export.SongPdfLayout
import com.grafitto.laudate.domain.export.SongPdfOptions
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.tablature.TabEngraving
import com.grafitto.laudate.domain.tablature.TabGlyphs
import com.grafitto.laudate.domain.tablature.TabLayoutSettings
import com.grafitto.laudate.domain.tablature.TabRhythmGeometry
import com.grafitto.laudate.domain.tablature.TabRhythmPrimitive
import com.grafitto.laudate.domain.tablature.TabRhythmPrimitives
import com.grafitto.laudate.domain.tablature.TabTextFace
import com.grafitto.laudate.domain.tablature.TabTextKind
import com.grafitto.laudate.domain.tablature.TabTextMetrics
import com.grafitto.laudate.domain.tablature.NoteValueStyle
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.apache.pdfbox.pdmodel.interactive.viewerpreferences.PDViewerPreferences
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pinto del PDF de Canticora para **escritorio (JVM)**, con **Apache PDFBox**.
 *
 * Es el equivalente de `SongPdfExporter` (Android, `android.graphics.pdf`): **toda la maquetación
 * vive en el dominio** ([SongPdfLayout] / [CelebrationPdfLayout]) y esta clase solo pinta las
 * páginas ya calculadas. Reproduce el mismo estilo: banda de marca, acordes sobre la letra alineados
 * por carácter, secciones con línea, letra en serif y pie de página. Ver ADR-013.
 */
class DesktopPdfExporter {

    /** Escribe el PDF de una canción en [destination]. */
    fun write(
        destination: File,
        song: Song,
        options: SongPdfOptions = SongPdfOptions(),
        branding: SongPdfBranding = SongPdfBranding(),
        /** Tablaturas del canto, para imprimirlas cuando el modo las incluye. */
        tablatures: List<com.grafitto.laudate.domain.model.tablature.Tablature> = emptyList()
    ): Result<Unit> = runCatching {
        writeDocument(destination, SongPdfLayout.layout(song, options, tablatures), options, branding)
    }

    /** Escribe el PDF de una misa/evento completo (portada + índice + cantos). */
    fun writeCelebration(
        destination: File,
        celebration: Celebration,
        songs: List<CelebrationPdfSong>,
        options: SongPdfOptions = SongPdfOptions(),
        branding: SongPdfBranding = SongPdfBranding()
    ): Result<Unit> = runCatching {
        writeDocument(destination, CelebrationPdfLayout.layout(celebration, songs, options), options, branding)
    }

    private fun writeDocument(
        destination: File,
        document: SongPdfDocument,
        options: SongPdfOptions,
        branding: SongPdfBranding
    ) {
        PDDocument().use { pdf ->
            val fonts = PdfFonts()
            document.pages.forEach { page ->
                val pdPage = PDPage(PDRectangle(SongPdfLayout.PAGE_WIDTH_PT, SongPdfLayout.PAGE_HEIGHT_PT))
                pdf.addPage(pdPage)
                PDPageContentStream(pdf, pdPage).use { cs ->
                    drawPage(cs, page, document, options, branding, fonts)
                }
            }
            declarePrintPreferences(pdf)
            // Al reexportar sobre un fichero existente, PDFBox avisa por el logger que «se está
            // sobrescribiendo». El documento se escribe de una vez (no se lee de ese fichero), así
            // que el aviso es inofensivo y solo ensucia la salida: se silencia el logger de guardado.
            java.util.logging.Logger.getLogger("org.apache.pdfbox.pdmodel.PDDocument")
                .apply { level = java.util.logging.Level.SEVERE }
            pdf.save(destination)
        }
    }

    /**
     * Declara las **preferencias de impresión** del documento.
     *
     * Sin ellas, el visor/impresora de Windows usa el **papel por defecto de la impresora** al
     * abrir el diálogo de imprimir (en muchas instalaciones, «Letter»), en lugar del tamaño real de
     * las páginas (A4 vertical), y el diálogo aparece con un tamaño y una orientación que no
     * corresponden al documento. Con `PickTrayByPDFSize`, el visor respeta el tamaño de página del
     * PDF; `PrintScaling = AppDefault` deja que la impresora escale al área imprimible (evita que la
     * opción de escalado oculte la elección de páginas por hoja).
     */
    private fun declarePrintPreferences(pdf: PDDocument) {
        val catalog = pdf.documentCatalog
        val prefs = catalog.viewerPreferences ?: PDViewerPreferences().also {
            catalog.viewerPreferences = it
        }
        // Getter (`String`) y setter (`PRINT_SCALING`) no coinciden: hay que llamar al setter.
        prefs.setPrintScaling(PDViewerPreferences.PRINT_SCALING.AppDefault)
        // `PickTrayByPDFSize` no se expone en la API de PDFBox 3, se escribe directamente en el COS.
        prefs.cosObject.setBoolean(COSName.getPDFName("PickTrayByPDFSize"), true)
    }

    // ── Pintado (misma lógica que el pintor de Android, en coordenadas PDFBox desde arriba) ──

    private fun drawPage(
        cs: PDPageContentStream,
        page: PdfPage,
        document: SongPdfDocument,
        options: SongPdfOptions,
        branding: SongPdfBranding,
        fonts: PdfFonts
    ) {
        val margin = options.marginPt
        val header = page.header
        val headerHeight = when {
            header == null -> 0f
            page.isTitlePage -> options.titleFontSizePt * 1.9f
            else -> options.runningHeaderHeightPt
        }

        if (header != null) {
            cs.setNonStrokingColor(branding.brandBandArgb.toArgbColor())
            cs.addRect(0f, SongPdfLayout.PAGE_HEIGHT_PT - headerHeight, SongPdfLayout.PAGE_WIDTH_PT, headerHeight)
            cs.fill()

            val size = if (page.isTitlePage) options.titleFontSizePt else options.sectionFontSizePt
            val font = if (page.isTitlePage) fonts.sansBold else fonts.sansBold
            cs.setNonStrokingColor(branding.onBrandBandArgb.toArgbColor())
            textAt(cs, font, size, margin, header, headerHeight / 2f + size * 0.35f)
        }

        var y = headerHeight + 12f
        val drawTitleRow = header == null
        // El contenido arranca tras la franja de etiquetas del margen izquierdo.
        val contentX = margin + options.marginLabelWidthPt

        if (page.columns == 2 && page.secondColumnStartIndex in 1 until page.rows.size) {
            // Cabecera a ancho completo (datos, notas, ritmo global) encima de las columnas: se
            // pinta de lado a lado para que no invada la columna derecha.
            var headerY = headerHeight + 12f
            page.headerRows.forEach { row ->
                headerY += row.leadingGapPt
                headerY = drawRow(
                    cs, row, headerY, contentX, options, branding, fonts, drawTitleRow,
                    options.baseContentWidthPt, margin, labelOnRight = false
                )
            }
            // Página a dos columnas: la izquierda rotula en el margen izquierdo y la derecha en el
            // derecho (márgenes exteriores), de modo que las etiquetas nunca se pisan.
            val columnWidth = options.columnWidthPt(2)
            val leftRows = page.rows.subList(0, page.secondColumnStartIndex)
            val rightRows = page.rows.subList(page.secondColumnStartIndex, page.rows.size)
            val rightColumnX = contentX + columnWidth + options.columnGapPt
            val columnsTop = headerY + 4f
            drawRows(
                cs, leftRows, columnsTop, contentX, columnWidth, options, branding, fonts,
                drawTitleRow, margin, labelOnRight = false
            )
            drawRows(
                cs, rightRows, columnsTop, rightColumnX, columnWidth, options, branding, fonts,
                drawTitleRow, margin, labelOnRight = true
            )
        } else {
            page.rows.forEach { row ->
                y += row.leadingGapPt
                y = drawRow(cs, row, y, contentX, options, branding, fonts, drawTitleRow, options.contentWidthPt, margin, labelOnRight = false)
            }
        }

        drawFooter(cs, page, options, branding, fonts)
    }

    /** Dibuja una lista de filas en una columna de ancho [columnWidth] desde [startY]. */
    private fun drawRows(
        cs: PDPageContentStream,
        rows: List<PdfRow>,
        startY: Float,
        contentX: Float,
        columnWidth: Float,
        options: SongPdfOptions,
        branding: SongPdfBranding,
        fonts: PdfFonts,
        drawTitleRow: Boolean,
        margin: Float,
        labelOnRight: Boolean
    ) {
        var y = startY
        rows.forEach { row ->
            y += row.leadingGapPt
            y = drawRow(cs, row, y, contentX, options, branding, fonts, drawTitleRow, columnWidth, margin, labelOnRight)
        }
    }

    private fun drawRow(
        cs: PDPageContentStream,
        row: PdfRow,
        top: Float,
        contentX: Float,
        options: SongPdfOptions,
        branding: SongPdfBranding,
        fonts: PdfFonts,
        drawTitleRow: Boolean,
        columnWidth: Float,
        margin: Float,
        labelOnRight: Boolean
    ): Float {
        // Etiqueta de sección rotada + regla vertical en el margen exterior de la columna.
        drawSectionMargin(cs, row, top, contentX, columnWidth, margin, options, branding, fonts, labelOnRight)

        return when (row.kind) {
        PdfRowKind.TITLE ->
            if (drawTitleRow) {
                cs.setNonStrokingColor(branding.primaryArgb.toArgbColor())
                centerText(cs, fonts.serifBold, options.titleFontSizePt, row.text, contentX, columnWidth, top + options.titleFontSizePt)
                top + row.heightPt
            } else {
                top + row.heightPt
            }

        PdfRowKind.METADATA -> {
            cs.setNonStrokingColor(branding.mutedArgb.toArgbColor())
            textAt(cs, fonts.sansRegular, options.metaFontSizePt, contentX, row.text, top + options.metaFontSizePt, options.textScaleX)
            top + row.heightPt
        }

        PdfRowKind.SECTION_HEADER -> {
            // Solo se usa en la portada del repertorio (los cantos rotulan la sección en el margen).
            cs.setNonStrokingColor(branding.primaryArgb.toArgbColor())
            val baseline = top + options.sectionFontSizePt
            textAt(cs, fonts.sansBold, options.sectionFontSizePt, contentX, row.text, baseline)
            val ruleY = SongPdfLayout.PAGE_HEIGHT_PT - (baseline + 3f)
            cs.setStrokingColor(branding.ruleArgb.toArgbColor())
            cs.setLineWidth(1f)
            cs.moveTo(contentX, ruleY)
            cs.lineTo(contentX + columnWidth, ruleY)
            cs.stroke()
            top + row.heightPt
        }

        PdfRowKind.COMMENT -> {
            cs.setNonStrokingColor(branding.mutedArgb.toArgbColor())
            val baseline = top + options.metaFontSizePt
            alignedText(cs, fonts.sansItalic, options.metaFontSizePt, row, contentX, options, baseline, columnWidth)
            top + row.heightPt
        }

        PdfRowKind.RHYTHM -> {
            // Nombre del patrón + rejilla de flechas (muted, sin acordes).
            cs.setNonStrokingColor(branding.mutedArgb.toArgbColor())
            textAt(cs, fonts.sansRegular, options.metaFontSizePt, contentX, row.text, top + options.metaFontSizePt, options.textScaleX)
            top + row.heightPt
        }

        PdfRowKind.TABLATURE -> {
            // (D-53) La tablatura ocupa el **ancho de página**: recupera a su izquierda la franja
            // de etiquetas de sección, que en una partitura no existe.
            val tabX = contentX - row.tablatureInsetPt
            // Tablatura vectorial: líneas de cuerda, separadores y cifras de traste.
            val drawing = row.tablature
            if (drawing != null) {
                val dy = top + 4f
                // (D-39) Grosores de la **hoja de estilos de MuseScore**, en espacios de pentagrama:
                // antes eran 0,8/1,2 pt fijos y las barras iban en el morado de acento de Material,
                // que no existe en una partitura impresa.
                // (D-54) El espacio del **dibujo** manda: el zoom del número separa las cuerdas sin
                // engordar los trazos.
                val staffSpace = drawing.staffSpace.takeIf { it > 0f }
                    ?: TabEngraving.staffSpaceOf(
                        drawing.stringSpacing.takeIf { it > 0f } ?: TabLayoutSettings().stringSpacing
                    )
                val staffLineWidth = TabEngraving.STAFF_LINE_WIDTH * staffSpace
                val barWidth = TabEngraving.BAR_WIDTH * staffSpace
                val endBarWidth = TabEngraving.END_BAR_WIDTH * staffSpace
                val doubleGap = TabEngraving.DOUBLE_BAR_DISTANCE * staffSpace
                val endGap = TabEngraving.END_BAR_DISTANCE * staffSpace
                fun vertical(x: Float, y1: Float, y2: Float, width: Float) {
                    cs.setLineWidth(width)
                    cs.moveTo(x, SongPdfLayout.PAGE_HEIGHT_PT - (dy + y1))
                    cs.lineTo(x, SongPdfLayout.PAGE_HEIGHT_PT - (dy + y2))
                    cs.stroke()
                }
                drawing.lines.forEach { line ->
                    if (!line.isBar) {
                        cs.setStrokingColor(branding.mutedArgb.toArgbColor())
                        cs.setLineWidth(staffLineWidth)
                        cs.moveTo(tabX + line.x1, SongPdfLayout.PAGE_HEIGHT_PT - (dy + line.y1))
                        cs.lineTo(tabX + line.x2, SongPdfLayout.PAGE_HEIGHT_PT - (dy + line.y2))
                        cs.stroke()
                        return@forEach
                    }
                    cs.setStrokingColor(branding.textArgb.toArgbColor())
                    // La barra **final** y la **doble** llevan dos trazos, como en MuseScore
                    // (`tdraw.cpp`): antes el PDF las imprimía como una barra simple.
                    when (line.barline) {
                        com.grafitto.laudate.domain.model.tablature.TabBarline.FINAL -> {
                            val thinX = tabX + line.x1 - (endBarWidth * 0.5f + endGap + barWidth * 0.5f)
                            vertical(thinX, line.y1, line.y2, barWidth)
                            vertical(tabX + line.x1, line.y1, line.y2, endBarWidth)
                        }
                        com.grafitto.laudate.domain.model.tablature.TabBarline.DOUBLE -> {
                            val leftX = tabX + line.x1 - (barWidth + doubleGap)
                            vertical(leftX, line.y1, line.y2, barWidth)
                            vertical(tabX + line.x1, line.y1, line.y2, barWidth)
                        }
                        else -> vertical(tabX + line.x1, line.y1, line.y2, barWidth)
                    }
                }
                // ── Fila de ritmo (D-42) ────────────────────────────────────────────────────────
                // La **geometría** la decide el Dominio ([TabRhythmPrimitives]): aquí solo se traza,
                // exactamente igual que en el lienzo de Compose. Antes el PDF no imprimía plicas,
                // barras de unión, corchetes de grupo irregular ni glifos SMuFL, así que la
                // tablatura impresa salía «desnuda» frente a la de MuseScore.
                if (drawing.rhythmMarks.isNotEmpty()) {
                    drawRhythmPrimitives(
                        cs = cs,
                        primitives = TabRhythmPrimitives.of(
                            marks = drawing.rhythmMarks,
                            staffSpace = staffSpace,
                            offsetY = dy,
                            beamGroups = drawing.beamGroups,
                            tuplets = drawing.tuplets,
                            stemDirection = if (drawing.stemsDown) {
                                TabRhythmGeometry.StemDirection.DOWN
                            } else {
                                TabRhythmGeometry.StemDirection.UP
                            },
                            showNoteheads = drawing.noteValues == NoteValueStyle.SYMBOLS_ABOVE,
                            minimStyle = drawing.minimStyle
                        ),
                        contentX = tabX,
                        branding = branding,
                        fonts = fonts
                    )
                }
                drawing.texts.forEach { t ->
                    // (D-55) La **talla y la familia** salen del Dominio ([TabTextMetrics]), las
                    // mismas que usa el lienzo de Compose. Antes el PDF llevaba `7,5 pt` y `9 pt`
                    // **fijos** y todo con la sans: el rótulo del tempo salía un 37 % más pequeño
                    // que en pantalla, el número de compás un 25 %, y la clave `TAB` y las
                    // articulaciones se imprimían como el texto literal `TAB` o como **signos de
                    // interrogación** (los puntos de código SMuFL no existen en Helvetica).
                    val em = TabTextMetrics.emOf(t.kind) * staffSpace
                    when (TabTextMetrics.faceOf(t.kind)) {
                        TabTextFace.MUSIC -> drawMusicText(cs, t, tabX, dy, em, branding, fonts)
                        else -> {
                            val font = when (TabTextMetrics.faceOf(t.kind)) {
                                TabTextFace.TEXT_BOLD -> fonts.sansBold
                                TabTextFace.TEXT_ITALIC_BOLD -> fonts.sansItalic
                                else -> fonts.sansRegular
                            }
                            cs.setNonStrokingColor(
                                if (t.kind == TabTextKind.FRET) {
                                    branding.textArgb.toArgbColor()
                                } else {
                                    branding.mutedArgb.toArgbColor()
                                }
                            )
                            // Centrado horizontal: se aproxima el ancho del texto.
                            val approxWidth = t.text.length * em * 0.6f
                            textAt(
                                cs = cs,
                                font = font,
                                size = em,
                                x = tabX + t.x - approxWidth / 2f,
                                text = t.text,
                                topBaseline = dy + t.y + em * 0.35f
                            )
                        }
                    }
                }
            }
            top + row.heightPt
        }

        PdfRowKind.LYRICS -> {
            val size = row.fontSizePt.takeIf { it > 0f } ?: options.lyricsFontSizePt
            val font = if (row.emphasis) fonts.serifBold else fonts.serifRegular
            cs.setNonStrokingColor(branding.textArgb.toArgbColor())
            alignedText(cs, font, size, row, contentX, options, top + size, columnWidth)
            top + row.heightPt
        }

        PdfRowKind.CHORD_ONLY -> {
            // Fila de solo acordes: se pinta uno junto a otro en su desplazamiento exacto.
            cs.setNonStrokingColor(branding.primaryArgb.toArgbColor())
            val baseline = top + options.chordsFontSizePt
            row.chords.forEach { mark ->
                textAt(cs, fonts.sansBold, options.chordsFontSizePt, contentX + mark.xOffsetPt, mark.text, baseline, options.textScaleX)
            }
            top + row.heightPt
        }

        PdfRowKind.CHORDS_OVER_LYRICS -> {
            val lyricsSize = row.fontSizePt.takeIf { it > 0f } ?: options.lyricsFontSizePt
            val lyricsFont = if (row.emphasis) fonts.serifBold else fonts.serifRegular
            val sx = options.textScaleX
            val textWidth = widthOf(lyricsFont, lyricsSize, row.text) * sx
            val lineStart = if (row.centered) {
                (contentX + (columnWidth - textWidth) / 2f).coerceAtLeast(contentX)
            } else {
                contentX
            }

            cs.setNonStrokingColor(branding.primaryArgb.toArgbColor())
            // Posición natural de cada acorde (según el carácter que le corresponde) y ancho real
            // medido con la fuente de acordes: `Fam#` es más ancho que los huecos de letra hasta el
            // siguiente acorde y antes se solapaban («Fam#Sim»). Con las métricas reales y
            // `distributeChordGaps` se reparte el hueco para que nunca se pisen.
            val measured = row.chords.map { mark ->
                val prefix = row.text.substring(0, mark.characterIndex.coerceIn(0, row.text.length))
                mark.copy(
                    xOffsetPt = widthOf(lyricsFont, lyricsSize, prefix) * sx,
                    widthPt = widthOf(fonts.sansBold, options.chordsFontSizePt, mark.text) * sx
                )
            }
            val laidOut = com.grafitto.laudate.domain.export.SongPdfLayout
                .distributeChordGaps(measured, options.minChordGapPt)
            val chordBaseline = top + options.chordsFontSizePt
            laidOut.forEach { mark ->
                textAt(cs, fonts.sansBold, options.chordsFontSizePt, lineStart + mark.xOffsetPt, mark.text, chordBaseline, sx)
            }

            cs.setNonStrokingColor(branding.textArgb.toArgbColor())
            val lyricsBaseline = chordBaseline + options.chordsFontSizePt * 0.22f + lyricsSize
            textAt(cs, lyricsFont, lyricsSize, lineStart, row.text, lyricsBaseline, sx)
            top + row.heightPt
        }

        PdfRowKind.SPACER -> top + row.heightPt
        }
    }

    /**
     * Dibuja la etiqueta de sección (rotada 90°) en el **margen exterior** de la columna y el
     * tramo de regla vertical de esta fila. La etiqueta se centra verticalmente dentro de su
     * sección (nunca se sale ni pisa el título ni las secciones vecinas).
     */
    private fun drawSectionMargin(
        cs: PDPageContentStream,
        row: PdfRow,
        top: Float,
        contentX: Float,
        columnWidth: Float,
        margin: Float,
        options: SongPdfOptions,
        branding: SongPdfBranding,
        fonts: PdfFonts,
        labelOnRight: Boolean
    ) {
        // La regla va entre el texto y el margen exterior, pegada al contenido.
        if (row.sectionRule) {
            cs.setStrokingColor(branding.ruleArgb.toArgbColor())
            cs.setLineWidth(0.8f)
            val x = if (labelOnRight) contentX + columnWidth + 2f else contentX - 2f
            cs.moveTo(x, SongPdfLayout.PAGE_HEIGHT_PT - top)
            cs.lineTo(x, SongPdfLayout.PAGE_HEIGHT_PT - (top + row.heightPt))
            cs.stroke()
        }
        row.marginLabel?.let { label ->
            cs.setNonStrokingColor(branding.primaryArgb.toArgbColor())
            val size = options.sectionFontSizePt
            // Centro vertical de la sección: es la caja delimitadora real del contenido (sin el
            // aire externo entre secciones), adjunta a esta fila por el motor de maquetación.
            val centerY = top + row.sectionHeightPt / 2f
            val anchorX = if (labelOnRight) {
                // Franja derecha: el cuerpo del texto (rotado) queda a la izquierda del anclaje,
                // así que se ancla en el borde exterior de la franja, lejos de la línea.
                contentX + columnWidth + options.marginLabelWidthPt - 2f
            } else {
                margin + 2f
            }
            // Rotación 90° (lectura de abajo arriba) con el **centro de la cadena** en el centro de
            // la sección: se traslada media anchura sobre la base, de modo que el rótulo quede
            // centrado verticalmente sea cual sea su longitud. En coordenadas PDFBox (origen abajo)
            // el centro de la sección es `PAGE_HEIGHT - centerY`.
            val textWidth = widthOf(fonts.sansBold, size, label)
            val anchorYpdf = SongPdfLayout.PAGE_HEIGHT_PT - centerY
            val matrix = org.apache.pdfbox.util.Matrix(0f, 1f, -1f, 0f, anchorX, anchorYpdf - textWidth / 2f)
            cs.beginText()
            cs.setFont(fonts.sansBold, size)
            cs.setTextMatrix(matrix)
            cs.showText(sanitize(label))
            cs.endText()
        }
        // Marca de repetición (bis / ×N): pequeña y horizontal, al inicio (parte superior) de la
        // sección, en la franja del margen. No modifica la altura del bloque ni el centro vertical
        // de la etiqueta.
        row.repeatMark?.let { mark ->
            cs.setNonStrokingColor(branding.mutedArgb.toArgbColor())
            val size = options.sectionFontSizePt * 0.75f
            val x = if (labelOnRight) contentX + columnWidth + 1f else margin + 2f
            textAt(cs, fonts.sansRegular, size, x, mark, top + size)
        }
    }

    private fun alignedText(
        cs: PDPageContentStream,
        font: PDFont,
        size: Float,
        row: PdfRow,
        margin: Float,
        options: SongPdfOptions,
        baseline: Float,
        columnWidth: Float = options.contentWidthPt
    ) {
        if (row.centered) {
            centerText(cs, font, size, row.text, margin, columnWidth, baseline, options.textScaleX)
        } else {
            textAt(cs, font, size, margin, row.text, baseline, options.textScaleX)
        }
    }

    private fun drawFooter(
        cs: PDPageContentStream,
        page: PdfPage,
        options: SongPdfOptions,
        branding: SongPdfBranding,
        fonts: PdfFonts
    ) {
        val margin = options.marginPt
        val baseline = SongPdfLayout.PAGE_HEIGHT_PT - margin * 0.55f
        val ruleY = SongPdfLayout.PAGE_HEIGHT_PT - (baseline - options.metaFontSizePt - 6f)
        cs.setStrokingColor(branding.ruleArgb.toArgbColor())
        cs.setLineWidth(1f)
        cs.moveTo(margin, ruleY)
        cs.lineTo(SongPdfLayout.PAGE_WIDTH_PT - margin, ruleY)
        cs.stroke()

        cs.setNonStrokingColor(branding.mutedArgb.toArgbColor())
        textAt(cs, fonts.sansRegular, options.metaFontSizePt, margin, FOOTER_BRAND, baseline)

        // Sin fecha: la marca de la izquierda ya llena el pie y añadir «· 29/10/2026» hacía que
        // la etiqueta de página empezara a media hoja y se comiera parte del último renglón.
        val pageLabel = "Página ${page.index + 1} de ${page.totalPages}"
        val labelWidth = widthOf(fonts.sansRegular, options.metaFontSizePt, pageLabel)
        textAt(cs, fonts.sansRegular, options.metaFontSizePt, SongPdfLayout.PAGE_WIDTH_PT - margin - labelWidth, pageLabel, baseline)
    }

    // ── Utilidades de texto ────────────────────────────────────────────────────

    /** Dibuja texto con el origen en la parte SUPERIOR (como Canvas en Android), no en la base. */
    /**
     * Dibuja texto con el origen en la parte SUPERIOR (como Canvas en Android), no en la base.
     * [scaleX] comprime horizontalmente el texto (tipografía «condensada»): cabe más por línea sin
     * reducir el cuerpo.
     */
    /**
     * Traza las **primitivas de la fila de ritmo** que calcula el Dominio ([TabRhythmPrimitives]).
     *
     * Es el gemelo de `TabRhythmPainter.drawRhythmPrimitives` (Compose): mismo contrato, distinto
     * motor gráfico. Por eso la geometría —plica, barra de unión, corchete, anclaje de cada
     * glifo— **no** se decide aquí: si se decidiera, la pantalla y el papel se desincronizarían.
     *
     * @param contentX desplazamiento horizontal de la columna de contenido (las primitivas vienen
     *   en coordenadas de la rejilla, que empieza en 0).
     */
    private fun drawRhythmPrimitives(
        cs: PDPageContentStream,
        primitives: List<TabRhythmPrimitive>,
        contentX: Float,
        branding: SongPdfBranding,
        fonts: PdfFonts
    ) {
        val music = fonts.music ?: return
        val ink = branding.textArgb.toArgbColor()
        primitives.forEach { primitive ->
            when (primitive) {
                is TabRhythmPrimitive.Line -> {
                    cs.setStrokingColor(ink)
                    cs.setLineWidth(primitive.thickness)
                    cs.moveTo(contentX + primitive.x1, flipY(primitive.y1))
                    cs.lineTo(contentX + primitive.x2, flipY(primitive.y2))
                    cs.stroke()
                }

                is TabRhythmPrimitive.Dot -> {
                    cs.setNonStrokingColor(ink)
                    circle(cs, contentX + primitive.x, flipY(primitive.y), primitive.radius)
                    cs.fill()
                }

                // Paralelogramo **relleno** de la blanca rayada: borde derecho vertical y borde
                // izquierdo más bajo. PDF no tiene `quad`, así que se traza con cuatro `l` y se
                // rellena con la regla **non-zero** (`f`), la misma de los glifos.
                is TabRhythmPrimitive.Slash -> {
                    val half = primitive.width / 2f
                    val x = contentX + primitive.x
                    cs.setNonStrokingColor(ink)
                    cs.moveTo(x + half, flipY(primitive.y))
                    cs.lineTo(x + half, flipY(primitive.y + primitive.thickness))
                    cs.lineTo(
                        x - half,
                        flipY(primitive.y + primitive.thickness + primitive.slant)
                    )
                    cs.lineTo(x - half, flipY(primitive.y + primitive.slant))
                    cs.closePath()
                    cs.fill()
                }

                is TabRhythmPrimitive.Glyph -> {
                    val size = primitive.emPx
                    val width = music.advanceOf(primitive.glyph) / MusicGlyphOutlines.FONT_UNITS * size
                    // `leftShiftFraction` = 0 → centrado; > 0 → el borde izquierdo cae esa fracción
                    // del ancho a la izquierda de `x` (el corchete se pega a la plica).
                    val left = if (primitive.leftShiftFraction == 0f) {
                        primitive.x - width / 2f
                    } else {
                        primitive.x - width * primitive.leftShiftFraction
                    }
                    cs.setNonStrokingColor(ink)
                    music.draw(cs, primitive.glyph, contentX + left, primitive.baselineY, size)
                }

                is TabRhythmPrimitive.Tuplet -> drawTupletPrimitive(
                    cs = cs,
                    tuplet = primitive,
                    contentX = contentX,
                    ink = ink,
                    branding = branding,
                    fonts = fonts,
                    music = music
                )
            }
        }
    }

    /**
     * El **corchete de un grupo irregular**: la línea partida en dos tramos con el hueco de la
     * cifra, los dos ganchos y la cifra.
     *
     * La cifra es el glifo SMuFL `tuplet3`…`tuplet9` cuando el rótulo es un dígito, y texto normal
     * cuando es la proporción completa (`3:2`, `SHOW_RELATION`), que no tiene glifo.
     */
    private fun drawTupletPrimitive(
        cs: PDPageContentStream,
        tuplet: TabRhythmPrimitive.Tuplet,
        contentX: Float,
        ink: java.awt.Color,
        branding: SongPdfBranding,
        fonts: PdfFonts,
        music: MusicGlyphOutlines
    ) {
        val centerX = (tuplet.x1 + tuplet.x2) / 2f
        val labelGlyph = tuplet.labelGlyph
        val labelText = tuplet.labelText
        // Ancho de la cifra: el del glifo si lo hay; si no, el del texto de la proporción.
        val labelWidth = when {
            labelText == null -> 0f
            labelGlyph != null ->
                music.advanceOf(labelGlyph) / MusicGlyphOutlines.FONT_UNITS * tuplet.labelEmPx
            else -> fonts.sansRegular.getStringWidth(labelText) / 1000f * tuplet.labelEmPx * 0.5f
        }

        if (tuplet.hasBracket) {
            val gap = if (labelText == null) 0f else labelWidth / 2f + tuplet.gapPx
            cs.setStrokingColor(ink)
            cs.setLineWidth(tuplet.thickness)
            // La línea, en dos tramos, con el hueco de la cifra en medio.
            cs.moveTo(contentX + tuplet.x1, flipY(tuplet.y))
            cs.lineTo(contentX + centerX - gap, flipY(tuplet.y))
            cs.stroke()
            cs.moveTo(contentX + centerX + gap, flipY(tuplet.y))
            cs.lineTo(contentX + tuplet.x2, flipY(tuplet.y))
            cs.stroke()
            // Los ganchos, hacia abajo en los dos extremos.
            cs.moveTo(contentX + tuplet.x1, flipY(tuplet.y))
            cs.lineTo(contentX + tuplet.x1, flipY(tuplet.y + tuplet.hook))
            cs.stroke()
            cs.moveTo(contentX + tuplet.x2, flipY(tuplet.y))
            cs.lineTo(contentX + tuplet.x2, flipY(tuplet.y + tuplet.hook))
            cs.stroke()
        }

        if (labelText == null) return
        if (labelGlyph != null) {
            // La cifra, con su **tinta** centrada en la línea (o en el vuelo del corchete si no lo
            // hay): `tuplet3` tiene su centro a `+0,184 em` de la línea base.
            val inkBox = TabGlyphs.inkOf(labelGlyph)
            val baseline = tuplet.y + (inkBox?.centerFromBaseline ?: 0f) * tuplet.labelEmPx
            cs.setNonStrokingColor(ink)
            music.draw(
                cs = cs,
                glyph = labelGlyph,
                x = contentX + centerX - labelWidth / 2f,
                baselineY = baseline,
                size = tuplet.labelEmPx
            )
        } else {
            cs.setNonStrokingColor(branding.textArgb.toArgbColor())
            textAt(
                cs = cs,
                font = fonts.sansRegular,
                size = tuplet.labelEmPx * 0.5f,
                x = contentX + centerX - labelWidth / 2f,
                text = labelText,
                topBaseline = tuplet.y + tuplet.labelEmPx * 0.18f
            )
        }
    }

    /**
     * Dibuja un texto de la tablatura que es un **glifo SMuFL** (clave `TAB`, indicación de compás,
     * articulaciones, marcas, `8va`).
     *
     * Es el gemelo de las ramas `TAB_CLEF` / `TIME_NUMERATOR` / `TIME_DENOMINATOR` de
     * `TablatureCanvas`: el anclaje es por **tinta** ([TabGlyphs.inkOf]), no por caja de línea, porque
     * la caja de línea de Bravura mide 4,024 em y centrar por ella dejaría el símbolo fuera de su
     * franja.
     *
     * (D-55) Antes solo se trataba así la indicación de compás; la clave `TAB` se imprimía como el
     * texto literal y las articulaciones como **signos de interrogación**, porque sus puntos de código
     * no existen en Helvetica.
     *
     * Si el glifo no se puede extraer (fuente ausente) no se dibuja nada: es preferible un hueco a un
     * carácter basura en el papel.
     */
    private fun drawMusicText(
        cs: PDPageContentStream,
        text: com.grafitto.laudate.domain.tablature.TabText,
        contentX: Float,
        dy: Float,
        emPx: Float,
        branding: SongPdfBranding,
        fonts: PdfFonts
    ) {
        val music = fonts.music ?: return
        val glyph = when (text.kind) {
            TabTextKind.TIME_NUMERATOR, TabTextKind.TIME_DENOMINATOR ->
                text.text.toIntOrNull()?.let { TabGlyphs.timeSignatureGlyph(it) } ?: text.text
            else -> text.text
        }
        if (glyph.isEmpty()) return
        val width = music.advanceOf(glyph) / MusicGlyphOutlines.FONT_UNITS * emPx
        val ink = TabGlyphs.inkOf(glyph)
        // `text.y` es el **centro** del símbolo: la línea base queda a `centro + centroDeTinta`.
        val baseline = dy + text.y + (ink?.centerFromBaseline ?: 0f) * emPx
        cs.setNonStrokingColor(branding.textArgb.toArgbColor())
        music.draw(
            cs = cs,
            glyph = glyph,
            x = contentX + text.x - width / 2f,
            baselineY = baseline,
            size = emPx
        )
    }

    /** Círculo relleno por cuatro curvas de Bézier (PDFBox no trae primitiva de círculo). */
    private fun circle(cs: PDPageContentStream, cx: Float, cy: Float, r: Float) {
        val k = r * 0.5523f
        cs.moveTo(cx + r, cy)
        cs.curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        cs.curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        cs.curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        cs.curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        cs.closePath()
    }

    /** Pasa una coordenada **desde arriba** (la del Dominio) a la del PDF, que cuenta desde abajo. */
    private fun flipY(y: Float): Float = SongPdfLayout.PAGE_HEIGHT_PT - y

    private fun textAt(
        cs: PDPageContentStream,
        font: PDFont,
        size: Float,
        x: Float,
        text: String,
        topBaseline: Float,
        scaleX: Float = 1f
    ) {
        if (text.isEmpty()) return
        val y = SongPdfLayout.PAGE_HEIGHT_PT - topBaseline
        cs.beginText()
        cs.setFont(font, size)
        // La matriz de texto lleva la escala horizontal y la traslación de una sola vez: fijar solo
        // la escala perdería la posición (PDFBox la reemplaza, no la multiplica).
        cs.setTextMatrix(org.apache.pdfbox.util.Matrix(scaleX, 0f, 0f, 1f, x, y))
        cs.showText(sanitize(text))
        cs.endText()
    }

    private fun centerText(
        cs: PDPageContentStream,
        font: PDFont,
        size: Float,
        text: String,
        margin: Float,
        contentWidth: Float,
        topBaseline: Float,
        scaleX: Float = 1f
    ) {
        val x = margin + (contentWidth - widthOf(font, size, text) * scaleX) / 2f
        textAt(cs, font, size, x.coerceAtLeast(margin), text, topBaseline, scaleX)
    }

    private fun widthOf(font: PDFont, size: Float, text: String): Float =
        runCatching { font.getStringWidth(sanitize(text)) / 1000f * size }.getOrDefault(0f)

    /**
     * PDFBox con fuentes Type1 (WinAnsi) no admite ciertos caracteres Unicode (p. ej. los símbolos
     * musicales de la barra superior). Se sustituyen por un equivalente ASCII para no romper el
     * documento. Una fuente TrueType embebida daría soporte completo.
     */
    private fun sanitize(text: String): String {
        // Las **figuras musicales** se deletrean antes de nada: el Dominio las escribe con Unicode
        // (♩, ♪, 𝅘𝅥𝅯) porque en pantalla las pinta SMuFL, pero las fuentes estándar del PDF no las
        // tienen y todo lo que caía en ese rango se imprimía como `?`. El resultado era un rótulo
        // de tempo «? = 112 · Moderato» junto al metadato, ya correcto, «Negra = 112»: el mismo
        // dato dos veces, una de ellas rota.
        val spelled = text
            .replace("\u2669\u00B7", "Negra con puntillo")
            .replace("\u2669", "Negra")
            .replace("\u266A", "Corchea")
            .replace("\u266B", "Corcheas")
            .replace("\uD834\uDD5F\uD834\uDD65\uD834\uDD6F", "Semicorcheas")
            .replace("\uD834\uDD5F\uD834\uDD65\uD834\uDD64", "Semicorcheas")
            .replace("\uD834\uDD5F", "Semicorcheas")
        return spelled.map { ch ->
            if (ch.code in 32..255) ch else ch.toAsciiFallback()
        }.joinToString("")
    }

    private fun Char.toAsciiFallback(): Char = when (this) {
        '↻' -> 'R'
        '×' -> 'x'
        '↑' -> '^'
        '↓' -> 'v'
        '·' -> '.'
        '–' -> '-'
        '—', '–' -> '-'
        '“', '”' -> '"'
        '‘', '’' -> '\''
        else -> '?'
    }

    /** Convierte un ARGB de 32 bits al color RGB que entiende PDFBox (ignora el alfa). */
    private fun Int.toArgbColor(): java.awt.Color =
        java.awt.Color((this shr 16) and 0xFF, (this shr 8) and 0xFF, this and 0xFF)

    private class PdfFonts {
        val sansRegular: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        val sansBold: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD)
        val sansItalic: PDFont = PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE)
        val serifRegular: PDFont = PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN)
        val serifBold: PDFont = PDType1Font(Standard14Fonts.FontName.TIMES_BOLD)

        /**
         * **Bravura**, la fuente musical SMuFL (OFL 1.1) con la que se dibujan las figuras de ritmo,
         * los corchetes y las cifras de grupo irregular.
         *
         * No se embebe como fuente (PDFBox rechaza los OpenType con contornos CFF): se trazan sus
         * **contornos** como caminos vectoriales ([MusicGlyphOutlines]). El resultado impreso es el
         * mismo y el PDF no depende de que el equipo tenga una fuente musical instalada.
         *
         * `null` si el recurso no está en el classpath (una instalación incompleta): en ese caso la
         * tablatura se imprime sin la fila de ritmo, pero **no** falla la exportación.
         */
        val music: MusicGlyphOutlines? = MusicGlyphOutlines.load()
    }

    private companion object {
        const val FOOTER_BRAND = "Generado con ${LaudateBrand.NAME} · ${LaudateBrand.TAGLINE}"
        const val LINE_SPACING = 1.35f
        val TODAY: String = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    }
}
