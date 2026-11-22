package com.grafitto.laudate.data.export

import android.content.ContentResolver
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
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
import com.grafitto.laudate.domain.tablature.NoteValueStyle
import com.grafitto.laudate.domain.tablature.TabEngraving
import com.grafitto.laudate.domain.tablature.TabGlyphs
import com.grafitto.laudate.domain.tablature.TabLayoutSettings
import com.grafitto.laudate.domain.tablature.TabRhythmGeometry
import com.grafitto.laudate.domain.tablature.TabRhythmPrimitive
import com.grafitto.laudate.domain.tablature.TabRhythmPrimitives
import com.grafitto.laudate.domain.tablature.TabTextFace
import com.grafitto.laudate.domain.tablature.TabTextKind
import com.grafitto.laudate.domain.tablature.TabTextMetrics
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pinta el PDF de una canción y lo escribe en el destino elegido por el usuario.
 *
 * Toda la **maquetación** (reparto de líneas, alineación de acordes, paginación) vive en
 * [SongPdfLayout], en el dominio y sin Android. Esta clase es solo el **pintor**: convierte las
 * páginas ya calculadas en un `PdfDocument` de `android.graphics.pdf` (disponible desde API 19,
 * sin dependencias externas ni aumento del APK).
 *
 * El archivo se escribe en el `Uri` que devuelve el selector del sistema (SAF), por lo que la app
 * no necesita permisos de almacenamiento y funciona igual en Android 8+ y en Android 10+.
 */
class SongPdfExporter(
    private val contentResolver: ContentResolver,
    /**
     * **Bravura**, la fuente musical SMuFL (OFL 1.1) con la que se dibujan las figuras de ritmo, los
     * silencios y las cifras de los grupos irregulares.
     *
     * Se inyecta en vez de cargarse aquí porque el exportador no tiene `Context` (solo el
     * `ContentResolver` del SAF) y porque así se puede probar con una fuente cualquiera. Con `null`
     * la tablatura se imprime **sin** la fila de ritmo: las cuerdas, las barras y las cifras de
     * traste salen igual, de modo que un fallo al cargar la fuente degrada el resultado pero no
     * rompe la exportación.
     */
    private val musicTypeface: Typeface? = null
) {

    /**
     * Genera el PDF y lo escribe en [uri].
     *
     * @return [Result.failure] con la causa si el documento no se pudo escribir (p. ej. el usuario
     *   revocó el acceso al destino entre la elección del archivo y el guardado).
     */
    fun write(
        uri: Uri,
        song: Song,
        options: SongPdfOptions = SongPdfOptions(),
        branding: SongPdfBranding = SongPdfBranding(),
        /** Tablaturas del canto, para imprimirlas cuando el modo las incluye. */
        tablatures: List<com.grafitto.laudate.domain.model.tablature.Tablature> = emptyList()
    ): Result<Unit> = runCatching {
        val document = SongPdfLayout.layout(song, options, tablatures)
        writeDocument(uri, document, options, branding)
    }

    /**
     * Genera y escribe el PDF de una **misa o evento completo**: una portada con el índice y
     * todos sus cantos, uno por página.
     */
    fun writeCelebration(
        uri: Uri,
        celebration: Celebration,
        songs: List<CelebrationPdfSong>,
        options: SongPdfOptions = SongPdfOptions(),
        branding: SongPdfBranding = SongPdfBranding()
    ): Result<Unit> = runCatching {
        val document = CelebrationPdfLayout.layout(celebration, songs, options)
        writeDocument(uri, document, options, branding)
    }

    /** Pinta un documento ya maquetado y lo vuelca en el destino. */
    private fun writeDocument(
        uri: Uri,
        document: SongPdfDocument,
        options: SongPdfOptions,
        branding: SongPdfBranding
    ) {
        val pdf = PdfDocument()
        try {
            val paints = PdfPaints(options, branding)
            document.pages.forEachIndexed { index, page ->
                val pageInfo = PdfDocument.PageInfo
                    .Builder(
                        SongPdfLayout.PAGE_WIDTH_PT.toInt(),
                        SongPdfLayout.PAGE_HEIGHT_PT.toInt(),
                        index + 1
                    )
                    .create()
                val pdfPage = pdf.startPage(pageInfo)
                drawPage(pdfPage.canvas, page, document, options, paints)
                pdf.finishPage(pdfPage)
            }

            val stream = contentResolver.openOutputStream(uri)
                ?: error("No se pudo abrir el destino para escribir el PDF")
            stream.use { output -> pdf.writeTo(output) }
        } finally {
            pdf.close()
        }
    }

    // ── Pintado ───────────────────────────────────────────────────────────────

    private fun drawPage(
        canvas: Canvas,
        page: PdfPage,
        document: SongPdfDocument,
        options: SongPdfOptions,
        paints: PdfPaints
    ) {
        val margin = options.marginPt

        // Cabecera opcional: las páginas sin texto de banda (p. ej. la portada de un repertorio)
        // empiezan directamente en el margen.
        val header = page.header
        val headerHeight = when {
            header == null -> 0f
            page.isTitlePage -> options.titleFontSizePt * 1.9f
            else -> options.runningHeaderHeightPt
        }

        if (header != null) {
            canvas.drawRect(0f, 0f, SongPdfLayout.PAGE_WIDTH_PT, headerHeight, paints.brandBand)
            paints.runningHeader.textSize =
                if (page.isTitlePage) options.titleFontSizePt else options.sectionFontSizePt
            paints.runningHeader.typeface =
                if (page.isTitlePage) paints.boldTypeface else paints.regularTypeface
            val headerBaseline =
                headerHeight / 2f - (paints.runningHeader.descent() + paints.runningHeader.ascent()) / 2f
            canvas.drawText(header, margin, headerBaseline, paints.runningHeader)
        }

        var y = headerHeight + 12f

        // El título de una canción se pinta en la banda; el de una portada (sin banda) es una fila.
        val drawTitleRow = header == null
        // El contenido se desplaza a la derecha para dejar la franja del margen (etiquetas de sección).
        val contentX = margin + options.marginLabelWidthPt

        if (page.columns == 2 && page.secondColumnStartIndex in 1 until page.rows.size) {
            // Cabecera a ancho completo (datos, notas, ritmo global) encima de las columnas: se
            // pinta de lado a lado para que no invada la columna derecha.
            var headerY = headerHeight + 12f
            page.headerRows.forEach { row ->
                headerY += row.leadingGapPt
                headerY = drawRow(
                    canvas, row, headerY, contentX, options, paints, drawTitleRow,
                    options.baseContentWidthPt, margin, labelOnRight = false
                )
            }
            // Página a dos columnas: la izquierda rotula en el margen izquierdo y la derecha en el
            // derecho (márgenes exteriores), de modo que las etiquetas nunca se pisan.
            val columnWidth = options.columnWidthPt(2)
            val rightColumnX = contentX + columnWidth + options.columnGapPt
            val columnsTop = headerY + 4f
            drawRows(
                canvas, page.rows.subList(0, page.secondColumnStartIndex),
                columnsTop, contentX, columnWidth, options, paints, drawTitleRow, margin, labelOnRight = false
            )
            drawRows(
                canvas, page.rows.subList(page.secondColumnStartIndex, page.rows.size),
                columnsTop, rightColumnX, columnWidth,
                options, paints, drawTitleRow, margin, labelOnRight = true
            )
        } else {
            page.rows.forEach { row ->
                y += row.leadingGapPt
                y = drawRow(canvas, row, y, contentX, options, paints, drawTitleRow, options.contentWidthPt, margin, labelOnRight = false)
            }
        }

        drawFooter(canvas, page, options, paints)
    }

    /** Dibuja una lista de filas en una columna de ancho [columnWidth] desde [startY]. */
    private fun drawRows(
        canvas: Canvas,
        rows: List<PdfRow>,
        startY: Float,
        contentX: Float,
        columnWidth: Float,
        options: SongPdfOptions,
        paints: PdfPaints,
        drawTitleRow: Boolean,
        margin: Float,
        labelOnRight: Boolean
    ) {
        var y = startY
        rows.forEach { row ->
            y += row.leadingGapPt
            y = drawRow(canvas, row, y, contentX, options, paints, drawTitleRow, columnWidth, margin, labelOnRight)
        }
    }

    private fun drawRow(
        canvas: Canvas,
        row: PdfRow,
        top: Float,
        contentX: Float,
        options: SongPdfOptions,
        paints: PdfPaints,
        drawTitleRow: Boolean,
        columnWidth: Float,
        margin: Float,
        labelOnRight: Boolean
    ): Float {
        // Etiqueta de sección rotada + regla vertical en el margen exterior de la columna.
        drawSectionMargin(canvas, row, top, contentX, columnWidth, margin, options, paints, labelOnRight)

        return when (row.kind) {
        PdfRowKind.TITLE ->
            // El título se pinta dentro de la banda cuando ésta existe; si no (portada), aquí,
            // centrado en la caja de texto como en los cancioneros impresos.
            if (drawTitleRow) {
                paints.coverTitle.textSize = options.titleFontSizePt
                val baseline = top + options.titleFontSizePt
                canvas.drawText(row.text, contentX + columnWidth / 2f, baseline, paints.coverTitle)
                top + row.heightPt
            } else {
                top + row.heightPt
            }

        PdfRowKind.METADATA -> {
            paints.metadata.textSize = options.metaFontSizePt
            val baseline = top + options.metaFontSizePt
            drawScaledText(canvas, row.text, contentX, baseline, options.textScaleX, paints.metadata)
            top + row.heightPt
        }

        PdfRowKind.SECTION_HEADER -> {
            // Solo se usa en la portada del repertorio (los cantos rotulan la sección en el margen).
            paints.sectionHeader.textSize = options.sectionFontSizePt
            val baseline = top + options.sectionFontSizePt
            canvas.drawText(row.text, contentX, baseline, paints.sectionHeader)
            val ruleY = baseline + 3f
            canvas.drawLine(contentX, ruleY, contentX + columnWidth, ruleY, paints.rule)
            top + row.heightPt
        }

        PdfRowKind.COMMENT -> {
            paints.comment.textSize = options.metaFontSizePt
            val baseline = top + options.metaFontSizePt
            drawAlignedText(canvas, row, baseline, contentX, columnWidth, paints.comment, options.textScaleX)
            top + row.heightPt
        }

        PdfRowKind.RHYTHM -> {
            // Nombre del patrón + rejilla de flechas (muted, sin acordes).
            paints.metadata.textSize = options.metaFontSizePt
            drawScaledText(canvas, row.text, contentX, top + options.metaFontSizePt, options.textScaleX, paints.metadata)
            top + row.heightPt
        }

        PdfRowKind.TABLATURE -> {
            // (D-53) La tablatura ocupa el **ancho de página**: recupera a su izquierda la franja
            // de etiquetas de sección, que en una partitura no existe.
            val tabX = contentX - row.tablatureInsetPt
            // Tablatura vectorial: líneas de cuerda, separadores y cifras de traste.
            val drawing = row.tablature
            if (drawing != null) {
                // (D-39) Los grosores salen de la **hoja de estilos de MuseScore**, en espacios de
                // pentagrama, no de valores fijos en puntos: así el PDF se ve igual que el editor y
                // que el `salmo2.pdf` de referencia a cualquier escala de notación.
                // (D-54) El espacio del **dibujo** manda: el zoom del número separa las cuerdas sin
                // engordar los trazos.
                val staffSpace = drawing.staffSpace.takeIf { it > 0f }
                    ?: TabEngraving.staffSpaceOf(
                        drawing.stringSpacing.takeIf { it > 0f } ?: TabLayoutSettings().stringSpacing
                    )
                val linePaint = Paint().apply {
                    isAntiAlias = true
                    color = 0xFF333333.toInt()
                    strokeWidth = TabEngraving.STAFF_LINE_WIDTH * staffSpace
                }
                // MuseScore imprime las barras con la **misma tinta** que el pentagrama; antes iban
                // en el morado de acento de Material, que no existe en una partitura.
                val barPaint = Paint().apply {
                    isAntiAlias = true
                    color = 0xFF333333.toInt()
                    strokeWidth = TabEngraving.BAR_WIDTH * staffSpace
                }
                val endBarPaint = Paint().apply {
                    isAntiAlias = true
                    color = 0xFF333333.toInt()
                    strokeWidth = TabEngraving.END_BAR_WIDTH * staffSpace
                }
                // (D-55) La familia es la **sans** del texto, no la monoespaciada: el lienzo de
                // Compose pinta la cifra con `FontFamily.SansSerif` y el PDF de escritorio con
                // Helvetica, y la tabla de anchos de `TabSpacing.CHAR_ADVANCE` (0,556 em por dígito)
                // está medida sobre esa familia. Con la monoespaciada el dígito mide 0,6 em y las
                // columnas salían un 8 % más anchas que en pantalla.
                val fretPaint = Paint().apply {
                    isAntiAlias = true
                    color = 0xFF111111.toInt()
                    textSize = TabTextMetrics.FRET_EM * staffSpace
                    typeface = Typeface.SANS_SERIF
                    textAlign = Paint.Align.CENTER
                }
                val labelPaint = Paint().apply {
                    isAntiAlias = true
                    color = 0xFF666666.toInt()
                    textSize = TabTextMetrics.TEXT_EM * staffSpace
                    typeface = Typeface.SANS_SERIF
                    textAlign = Paint.Align.CENTER
                }
                // (D-55) El **tempo** y la **volta** van en negrita y la **dinámica** en negrita
                // cursiva, como en MuseScore (`tempoFontStyle`, `voltaFontStyle`, `dynamicsFontStyle`).
                // Antes todo salía con la monoespaciada normal y el papel no reproducía la pantalla.
                val boldLabelPaint = Paint().apply {
                    isAntiAlias = true
                    color = 0xFF666666.toInt()
                    textSize = TabTextMetrics.TEXT_EM * staffSpace
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                val italicLabelPaint = Paint().apply {
                    isAntiAlias = true
                    color = 0xFF666666.toInt()
                    textSize = TabTextMetrics.TEXT_EM * staffSpace
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)
                    textAlign = Paint.Align.CENTER
                }
                val dy = top + 4f
                val doubleGap = TabEngraving.DOUBLE_BAR_DISTANCE * staffSpace
                val endGap = TabEngraving.END_BAR_DISTANCE * staffSpace
                drawing.lines.forEach { line ->
                    if (!line.isBar) {
                        canvas.drawLine(
                            tabX + line.x1, dy + line.y1,
                            tabX + line.x2, dy + line.y2,
                            linePaint
                        )
                        return@forEach
                    }
                    // La barra **final** y la **doble** llevan dos trazos, como en MuseScore
                    // (`tdraw.cpp`): antes el PDF las imprimía como una barra simple.
                    when (line.barline) {
                        com.grafitto.laudate.domain.model.tablature.TabBarline.FINAL -> {
                            val thinX = tabX + line.x1 -
                                (TabEngraving.END_BAR_WIDTH * staffSpace * 0.5f + endGap +
                                    TabEngraving.BAR_WIDTH * staffSpace * 0.5f)
                            canvas.drawLine(thinX, dy + line.y1, thinX, dy + line.y2, barPaint)
                            canvas.drawLine(
                                tabX + line.x1, dy + line.y1,
                                tabX + line.x1, dy + line.y2, endBarPaint
                            )
                        }
                        com.grafitto.laudate.domain.model.tablature.TabBarline.DOUBLE -> {
                            val leftX = tabX + line.x1 -
                                (TabEngraving.DOUBLE_BAR_WIDTH * staffSpace + doubleGap)
                            canvas.drawLine(leftX, dy + line.y1, leftX, dy + line.y2, barPaint)
                            canvas.drawLine(
                                tabX + line.x1, dy + line.y1,
                                tabX + line.x1, dy + line.y2, barPaint
                            )
                        }
                        else -> canvas.drawLine(
                            tabX + line.x1, dy + line.y1,
                            tabX + line.x1, dy + line.y2, barPaint
                        )
                    }
                }
                // ── Fila de ritmo (D-42) ────────────────────────────────────────────────────────
                // La **geometría** la decide el Dominio ([TabRhythmPrimitives]): aquí solo se traza,
                // exactamente igual que en el lienzo de Compose y en el PDF de escritorio. Antes el
                // PDF de Android no imprimía plicas, barras de unión, corchetes de grupo irregular
                // ni glifos SMuFL, así que la tablatura impresa salía «desnuda» frente a MuseScore.
                if (drawing.rhythmMarks.isNotEmpty()) {
                    drawRhythmPrimitives(
                        canvas = canvas,
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
                        ink = 0xFF333333.toInt()
                    )
                }
                drawing.texts.forEach { t ->
                    // (D-55) La **talla y la familia** salen del Dominio ([TabTextMetrics]), las
                    // mismas que usan el lienzo de Compose y el PDF de escritorio. Antes este
                    // exportador llevaba `9 pt` y `7,5 pt` **fijos** y todo con la monoespaciada: el
                    // rótulo del tempo salía un 37 % más pequeño que en pantalla, el número de compás
                    // un 25 %, y la clave `TAB` y las articulaciones se imprimían como el texto
                    // literal `TAB` o como **signos de interrogación** (los puntos de código SMuFL no
                    // existen en una fuente de texto).
                    val em = TabTextMetrics.emOf(t.kind) * staffSpace
                    if (TabTextMetrics.faceOf(t.kind) == TabTextFace.MUSIC) {
                        val glyph = when (t.kind) {
                            TabTextKind.TIME_NUMERATOR, TabTextKind.TIME_DENOMINATOR ->
                                t.text.toIntOrNull()?.let { TabGlyphs.timeSignatureGlyph(it) } ?: t.text
                            else -> t.text
                        }
                        val music = musicTypeface
                        if (glyph.isNotEmpty() && music != null) {
                            val ink = TabGlyphs.inkOf(glyph)
                            val musicPaint = Paint().apply {
                                isAntiAlias = true
                                color = 0xFF111111.toInt()
                                textSize = em
                                typeface = music
                                textAlign = Paint.Align.CENTER
                            }
                            // `t.y` es el **centro** del símbolo: la línea base queda a
                            // `centro + centroDeTinta`.
                            val baseline = dy + t.y + (ink?.centerFromBaseline ?: 0f) * em
                            canvas.drawText(glyph, tabX + t.x, baseline, musicPaint)
                        }
                        return@forEach
                    }
                    val paint = when (TabTextMetrics.faceOf(t.kind)) {
                        TabTextFace.TEXT_BOLD -> boldLabelPaint
                        TabTextFace.TEXT_ITALIC_BOLD -> italicLabelPaint
                        else -> if (t.kind == TabTextKind.FRET) fretPaint else labelPaint
                    }
                    paint.textSize = em
                    val baseline = dy + t.y - (paint.descent() + paint.ascent()) / 2f
                    canvas.drawText(t.text, tabX + t.x, baseline, paint)
                }
            }
            top + row.heightPt
        }

        PdfRowKind.LYRICS -> {
            val size = row.fontSizePt.takeIf { it > 0f } ?: options.lyricsFontSizePt
            paints.lyrics.textSize = size
            paints.lyrics.typeface = if (row.emphasis) paints.boldSerif else paints.regularSerif
            val baseline = top + size
            drawAlignedText(canvas, row, baseline, contentX, columnWidth, paints.lyrics, options.textScaleX)
            top + row.heightPt
        }

        PdfRowKind.CHORD_ONLY -> {
            // Fila de solo acordes: cada acorde en su desplazamiento exacto (sin letra debajo).
            paints.chord.textSize = options.chordsFontSizePt
            paints.chord.textAlign = Paint.Align.LEFT
            val baseline = top + options.chordsFontSizePt
            row.chords.forEach { mark ->
                drawScaledText(canvas, mark.text, contentX + mark.xOffsetPt, baseline, options.textScaleX, paints.chord)
            }
            top + row.heightPt
        }

        PdfRowKind.CHORDS_OVER_LYRICS -> {
            // Los acordes se apoyan en la letra: cada uno se coloca sobre el carácter que le
            // corresponde. Se mide el texto real con las métricas de la fuente (no una estimación),
            // de modo que la alineación siga siendo exacta cuando la línea va centrada.
            val lyricsSize = row.fontSizePt.takeIf { it > 0f } ?: options.lyricsFontSizePt
            val sx = options.textScaleX
            paints.lyrics.textSize = lyricsSize
            paints.lyrics.typeface = if (row.emphasis) paints.boldSerif else paints.regularSerif
            paints.lyrics.textAlign = Paint.Align.LEFT

            val textWidth = paints.lyrics.measureText(row.text) * sx
            val lineStart = if (row.centered) {
                (contentX + (columnWidth - textWidth) / 2f).coerceAtLeast(contentX)
            } else {
                contentX
            }

            paints.chord.textSize = options.chordsFontSizePt
            // Posición natural de cada acorde (según el carácter que le corresponde) y ancho real
            // medido con la fuente de acordes: `Fam#` es más ancho que los huecos de letra hasta el
            // siguiente acorde y antes se solapaban («Fam#Sim»). Con las métricas reales y
            // `distributeChordGaps` se reparte el hueco para que nunca se pisen.
            val measured = row.chords.map { mark ->
                val prefix = row.text.substring(0, mark.characterIndex.coerceIn(0, row.text.length))
                mark.copy(
                    xOffsetPt = paints.lyrics.measureText(prefix) * sx,
                    widthPt = paints.chord.measureText(mark.text) * sx
                )
            }
            val laidOut = SongPdfLayout.distributeChordGaps(measured, options.minChordGapPt)
            val chordBaseline = top + options.chordsFontSizePt
            laidOut.forEach { mark ->
                drawScaledText(canvas, mark.text, lineStart + mark.xOffsetPt, chordBaseline, sx, paints.chord)
            }

            val lyricsBaseline = chordBaseline + options.chordsFontSizePt * 0.22f + lyricsSize
            drawScaledText(canvas, row.text, lineStart, lyricsBaseline, sx, paints.lyrics)
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
    /**
     * Traza las **primitivas de la fila de ritmo** que calcula el Dominio ([TabRhythmPrimitives]).
     *
     * Es el gemelo de `TabRhythmPainter.drawRhythmPrimitives` (Compose) y de
     * `DesktopPdfExporter.drawRhythmPrimitives` (PDFBox): mismo contrato, distinto motor gráfico.
     * Por eso la geometría —plica, barra de unión, corchete, anclaje de cada glifo— **no** se decide
     * aquí: si se decidiera, la pantalla y el papel se desincronizarían.
     *
     * @param contentX desplazamiento horizontal de la columna de contenido (las primitivas vienen
     *   en coordenadas de la rejilla, que empieza en 0).
     * @param ink color de la tinta de la partitura (el mismo que las cuerdas y las barras).
     */
    private fun drawRhythmPrimitives(
        canvas: Canvas,
        primitives: List<TabRhythmPrimitive>,
        contentX: Float,
        ink: Int
    ) {
        val music = musicTypeface
        val linePaint = Paint().apply {
            isAntiAlias = true
            color = ink
            strokeCap = Paint.Cap.BUTT
        }
        val fillPaint = Paint().apply {
            isAntiAlias = true
            color = ink
            style = Paint.Style.FILL
        }
        val glyphPaint = Paint().apply {
            isAntiAlias = true
            color = ink
            typeface = music
            textAlign = Paint.Align.LEFT
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = ink
            typeface = Typeface.SANS_SERIF
            textAlign = Paint.Align.LEFT
        }
        // Un solo `Path` reutilizado: la blanca rayada puede aparecer muchas veces por sistema y no
        // conviene crear un objeto por barra.
        val slashPath = Path()

        primitives.forEach { primitive ->
            when (primitive) {
                is TabRhythmPrimitive.Line -> {
                    linePaint.strokeWidth = primitive.thickness
                    canvas.drawLine(
                        contentX + primitive.x1, primitive.y1,
                        contentX + primitive.x2, primitive.y2,
                        linePaint
                    )
                }

                is TabRhythmPrimitive.Dot -> canvas.drawCircle(
                    contentX + primitive.x,
                    primitive.y,
                    primitive.radius,
                    fillPaint
                )

                // Paralelogramo **relleno** de la blanca rayada: borde derecho vertical y borde
                // izquierdo más bajo, igual que en el PDF de escritorio.
                is TabRhythmPrimitive.Slash -> {
                    val half = primitive.width / 2f
                    val x = contentX + primitive.x
                    slashPath.reset()
                    slashPath.moveTo(x + half, primitive.y)
                    slashPath.lineTo(x + half, primitive.y + primitive.thickness)
                    slashPath.lineTo(
                        x - half,
                        primitive.y + primitive.thickness + primitive.slant
                    )
                    slashPath.lineTo(x - half, primitive.y + primitive.slant)
                    slashPath.close()
                    canvas.drawPath(slashPath, fillPaint)
                }

                is TabRhythmPrimitive.Glyph -> {
                    if (music == null) return@forEach
                    glyphPaint.textSize = primitive.emPx
                    val width = glyphPaint.measureText(primitive.glyph)
                    // `leftShiftFraction` = 0 → centrado; > 0 → el borde izquierdo cae esa fracción
                    // del ancho a la izquierda de `x` (el corchete se pega a la plica).
                    val left = if (primitive.leftShiftFraction == 0f) {
                        primitive.x - width / 2f
                    } else {
                        primitive.x - width * primitive.leftShiftFraction
                    }
                    canvas.drawText(
                        primitive.glyph,
                        contentX + left,
                        primitive.baselineY,
                        glyphPaint
                    )
                }

                is TabRhythmPrimitive.Tuplet -> drawTupletPrimitive(
                    canvas = canvas,
                    tuplet = primitive,
                    contentX = contentX,
                    linePaint = linePaint,
                    glyphPaint = glyphPaint,
                    textPaint = textPaint,
                    music = music
                )
            }
        }
    }

    /**
     * El **corchete de un grupo irregular**: la línea partida en dos tramos con el hueco de la cifra,
     * los dos ganchos y la cifra.
     *
     * La cifra es el glifo SMuFL `tuplet3`…`tuplet9` cuando el rótulo es un dígito, y texto normal
     * cuando es la proporción completa (`3:2`, `SHOW_RELATION`), que no tiene glifo.
     */
    private fun drawTupletPrimitive(
        canvas: Canvas,
        tuplet: TabRhythmPrimitive.Tuplet,
        contentX: Float,
        linePaint: Paint,
        glyphPaint: Paint,
        textPaint: Paint,
        music: Typeface?
    ) {
        val centerX = (tuplet.x1 + tuplet.x2) / 2f
        val labelGlyph = tuplet.labelGlyph
        val labelText = tuplet.labelText
        // Ancho de la cifra: el del glifo si lo hay; si no, el del texto de la proporción.
        val labelWidth = when {
            labelText == null -> 0f
            labelGlyph != null && music != null -> {
                glyphPaint.textSize = tuplet.labelEmPx
                glyphPaint.measureText(labelGlyph)
            }
            else -> {
                textPaint.textSize = tuplet.labelEmPx * 0.5f
                textPaint.measureText(labelText)
            }
        }

        if (tuplet.hasBracket) {
            val gap = if (labelText == null) 0f else labelWidth / 2f + tuplet.gapPx
            linePaint.strokeWidth = tuplet.thickness
            // La línea, en dos tramos, con el hueco de la cifra en medio.
            canvas.drawLine(
                contentX + tuplet.x1, tuplet.y,
                contentX + centerX - gap, tuplet.y,
                linePaint
            )
            canvas.drawLine(
                contentX + centerX + gap, tuplet.y,
                contentX + tuplet.x2, tuplet.y,
                linePaint
            )
            // Los ganchos, hacia abajo en los dos extremos.
            canvas.drawLine(
                contentX + tuplet.x1, tuplet.y,
                contentX + tuplet.x1, tuplet.y + tuplet.hook,
                linePaint
            )
            canvas.drawLine(
                contentX + tuplet.x2, tuplet.y,
                contentX + tuplet.x2, tuplet.y + tuplet.hook,
                linePaint
            )
        }

        if (labelText == null) return
        if (labelGlyph != null && music != null) {
            // La cifra, con su **tinta** centrada en la línea (o en el vuelo del corchete si no lo
            // hay): `tuplet3` tiene su centro a `+0,184 em` de la línea base.
            val inkBox = TabGlyphs.inkOf(labelGlyph)
            val baseline = tuplet.y + (inkBox?.centerFromBaseline ?: 0f) * tuplet.labelEmPx
            glyphPaint.textSize = tuplet.labelEmPx
            canvas.drawText(
                labelGlyph,
                contentX + centerX - labelWidth / 2f,
                baseline,
                glyphPaint
            )
        } else {
            textPaint.textSize = tuplet.labelEmPx * 0.5f
            canvas.drawText(
                labelText,
                contentX + centerX - labelWidth / 2f,
                tuplet.y + tuplet.labelEmPx * 0.18f,
                textPaint
            )
        }
    }

    private fun drawSectionMargin(
        canvas: Canvas,
        row: PdfRow,
        top: Float,
        contentX: Float,
        columnWidth: Float,
        margin: Float,
        options: SongPdfOptions,
        paints: PdfPaints,
        labelOnRight: Boolean
    ) {
        // La regla va entre el texto y el margen exterior, pegada al contenido.
        if (row.sectionRule) {
            val x = if (labelOnRight) contentX + columnWidth + 2f else contentX - 2f
            canvas.drawLine(x, top, x, top + row.heightPt, paints.rule)
        }
        row.marginLabel?.let { label ->
            paints.sectionLabel.textSize = options.sectionFontSizePt
            // Centro vertical de la sección: es la caja delimitadora real del contenido (sin el
            // aire externo entre secciones), adjunta a esta fila por el motor de maquetación.
            val centerY = top + row.sectionHeightPt / 2f
            val anchorX = if (labelOnRight) {
                // Franja derecha: el cuerpo del texto (rotado) queda a la izquierda del anclaje.
                contentX + columnWidth + options.marginLabelWidthPt - 2f
            } else {
                margin + 2f
            }
            // Se rota 90° alrededor del centro de la sección y se dibuja con `textAlign=CENTER`:
            // el centro de la cadena coincide con el del anclaje en la dirección de lectura, de
            // modo que el rótulo queda centrado verticalmente sea cual sea su longitud. El
            // desplazamiento perpendicular centra la altura de las glifas.
            canvas.save()
            canvas.rotate(90f, anchorX, centerY)
            paints.sectionLabel.textAlign = Paint.Align.CENTER
            val halfTextHeight = (paints.sectionLabel.descent() + paints.sectionLabel.ascent()) / 2f
            canvas.drawText(label, anchorX, centerY - halfTextHeight, paints.sectionLabel)
            paints.sectionLabel.textAlign = Paint.Align.LEFT
            canvas.restore()
        }
        // Marca de repetición (bis / ×N): pequeña y horizontal, al inicio (parte superior) de la
        // sección, en la franja del margen, junto a la etiqueta lateral. No modifica la altura del
        // bloque ni el centro vertical de la etiqueta.
        row.repeatMark?.let { mark ->
            paints.metadata.textSize = options.sectionFontSizePt * 0.8f
            paints.metadata.textAlign = Paint.Align.LEFT
            val x = if (labelOnRight) contentX + columnWidth + 1f else margin + 1f
            canvas.drawText(mark, x, top + paints.metadata.textSize, paints.metadata)
        }
    }

    /** Dibuja el texto de la fila centrado en el ancho útil o alineado a la izquierda. */
    private fun drawAlignedText(
        canvas: Canvas,
        row: PdfRow,
        baseline: Float,
        margin: Float,
        columnWidth: Float,
        paint: Paint,
        scaleX: Float = 1f
    ) {
        val align = if (row.centered) Paint.Align.CENTER else Paint.Align.LEFT
        paint.textAlign = align
        val x = if (row.centered) margin + columnWidth / 2f else margin
        drawScaledText(canvas, row.text, x, baseline, scaleX, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    /** Dibuja texto con compresión horizontal [scaleX] alrededor del anclaje [x]. */
    private fun drawScaledText(canvas: Canvas, text: String, x: Float, y: Float, scaleX: Float, paint: Paint) {
        if (scaleX == 1f) {
            canvas.drawText(text, x, y, paint)
        } else {
            canvas.save()
            canvas.scale(scaleX, 1f, x, y)
            canvas.drawText(text, x, y, paint)
            canvas.restore()
        }
    }

    private fun drawFooter(
        canvas: Canvas,
        page: PdfPage,
        options: SongPdfOptions,
        paints: PdfPaints
    ) {
        val margin = options.marginPt
        val baseline = SongPdfLayout.PAGE_HEIGHT_PT - margin * 0.55f
        val ruleY = baseline - options.metaFontSizePt - 6f
        canvas.drawLine(margin, ruleY, SongPdfLayout.PAGE_WIDTH_PT - margin, ruleY, paints.rule)

        paints.footer.textSize = options.metaFontSizePt
        paints.footer.textAlign = Paint.Align.LEFT
        canvas.drawText(FOOTER_BRAND, margin, baseline, paints.footer)

        paints.footer.textAlign = Paint.Align.RIGHT
        // Sin fecha: la marca de la izquierda ya llena el pie y añadir «· dd/MM/aaaa» hacía que
        // la etiqueta de página se solapara con el último renglón de contenido.
        val pageLabel = "Página ${page.index + 1} de ${page.totalPages}"
        canvas.drawText(pageLabel, SongPdfLayout.PAGE_WIDTH_PT - margin, baseline, paints.footer)
        paints.footer.textAlign = Paint.Align.LEFT
    }

    /** Conjunto de pinceles reutilizados en todas las páginas (se recrean una vez por documento). */
    private class PdfPaints(options: SongPdfOptions, branding: SongPdfBranding) {
        // La letra va en serif (familia «serif» de Android, Noto Serif / Liberation Serif) como en
        // los cancioneros de coro; los encabezados y acordes, en sans, para distinguirse del texto.
        val boldTypeface: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val regularTypeface: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val italicTypeface: Typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        val regularSerif: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val boldSerif: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val boldItalicSerif: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)

        val brandBand: Paint = Paint().apply { color = branding.brandBandArgb }
        val rule: Paint = Paint().apply {
            color = branding.ruleArgb
            strokeWidth = 1f
        }

        val runningHeader: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = branding.onBrandBandArgb
            typeface = boldTypeface
            textSize = options.titleFontSizePt
        }
        val sectionHeader: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = branding.primaryArgb
            typeface = boldTypeface
            letterSpacing = 0.06f
        }
        /** Etiqueta de sección rotada en el margen izquierdo. */
        val sectionLabel: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = branding.primaryArgb
            typeface = boldTypeface
            textAlign = Paint.Align.LEFT
            letterSpacing = 0.02f
        }
        /** Título de portada, cuando la página no lleva banda de marca. */
        val coverTitle: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = branding.primaryArgb
            typeface = boldSerif
            textAlign = Paint.Align.CENTER
        }
        val metadata: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = branding.mutedArgb
            typeface = regularTypeface
        }
        val chord: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = branding.primaryArgb
            typeface = boldTypeface
        }
        val lyrics: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = branding.textArgb
            typeface = regularSerif
        }
        val comment: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = branding.mutedArgb
            typeface = italicTypeface
        }
        val footer: Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = branding.mutedArgb
            typeface = regularTypeface
        }
    }

    private companion object {
        const val FOOTER_BRAND = "Generado con ${LaudateBrand.NAME} · ${LaudateBrand.TAGLINE}"

        /** Interlineado de la rejilla rítmica (nombre + flechas). */
        const val LINE_SPACING = 1.35f

        /** Fecha de generación, fijada una vez por proceso para que todas las páginas coincidan. */
        val TODAY: String = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }
}
