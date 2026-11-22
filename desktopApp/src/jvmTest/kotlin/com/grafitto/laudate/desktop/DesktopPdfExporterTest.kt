package com.grafitto.laudate.desktop

import com.grafitto.laudate.domain.export.SongPdfOptions
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.tablature.TabBarline
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import com.grafitto.laudate.domain.model.tablature.TabTupletBracketType
import com.grafitto.laudate.domain.model.tablature.TabTupletNumberType
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.parser.ChordProParser
import com.grafitto.laudate.domain.tablature.MinimStyle
import com.grafitto.laudate.domain.tablature.ShowTiedFret
import com.grafitto.laudate.domain.tablature.TabStaffOptions
import org.apache.pdfbox.Loader
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Verifica que el PDF de escritorio (PDFBox) se genera de verdad a partir de los layouts puros de
 * Canticora y que el documento abre y tiene páginas. Ver ADR-013.
 */
class DesktopPdfExporterTest {

    @Test
    fun `genera un PDF de una cancion con al menos una pagina`() {
        val song = ChordProParser.parse(
            chordProContent = """
                {title: Aleluya}
                {key: D}
                {soc}
                [D]Aleluya, ale[A]luya
                {eoc}
            """.trimIndent(),
            songId = "test-1",
            fallbackTitle = "Aleluya"
        )

        val file = Files.createTempFile("laudate-song", ".pdf").toFile()
        file.delete()

        val result = DesktopPdfExporter().write(file, song)
        assertTrue("El PDF se generó", result.isSuccess)
        assertTrue("El fichero existe y no está vacío", file.exists() && file.length() > 0)

        Loader.loadPDF(file).use { pdf ->
            assertTrue("Tiene al menos una página", pdf.numberOfPages >= 1)
        }
    }

    /**
     * Reexportar sobre un fichero **ya existente** (el aviso «overwriting …» de PDFBox) debe
     * sustituir el PDF por uno **válido y completo**, no corromperlo: al final vuelve a abrir y
     * tiene el contenido nuevo. Cierra el pendiente del aviso de PDFBox al reexportar.
     */
    @Test
    fun `reexportar sobre un PDF existente lo reemplaza por uno valido`() {
        val exporter = DesktopPdfExporter()
        val file = Files.createTempFile("laudate-reexport", ".pdf").toFile()

        val first = ChordProParser.parse(
            chordProContent = "{title: Primera}\n{key: C}\n{start_of_verse}\nUno\n{end_of_verse}",
            songId = "r1",
            fallbackTitle = "Primera"
        )
        assertTrue("La primera exportación funciona", exporter.write(file, first).isSuccess)
        val firstSize = file.length()
        assertTrue("El primer PDF no está vacío", firstSize > 0)

        // Segunda exportación sobre EL MISMO fichero: aquí es donde PDFBox avisa «overwriting».
        val second = ChordProParser.parse(
            chordProContent = ("{title: Segunda}\n{key: D}\n{start_of_verse}\n" + "Dos\n".repeat(60)) + "{end_of_verse}",
            songId = "r2",
            fallbackTitle = "Segunda"
        )
        assertTrue("La reexportación funciona", exporter.write(file, second).isSuccess)

        Loader.loadPDF(file).use { pdf ->
            assertTrue("El PDF reexportado tiene páginas", pdf.numberOfPages >= 1)
        }
    }

    @Test
    fun `el pdf es A4 vertical y declara las preferencias de impresion`() {
        val song = ChordProParser.parse(
            chordProContent = "{title: A4}\n{key: C}\n{start_of_verse}\nHola\n{end_of_verse}",
            songId = "a4",
            fallbackTitle = "A4"
        )
        val file = Files.createTempFile("laudate-a4", ".pdf").toFile()
        file.delete()
        assertTrue(DesktopPdfExporter().write(file, song).isSuccess)

        Loader.loadPDF(file).use { pdf ->
            val page = pdf.getPage(0)
            // 595 × 842 pt = A4 vertical. Si estuviera apaisada, el diálogo de imprimir mostraría
            // un tamaño y una orientación que no corresponden al documento.
            assertTrue("Ancho A4 (595 pt)", Math.abs(page.mediaBox.width - 595f) < 1f)
            assertTrue("Alto A4 (842 pt)", Math.abs(page.mediaBox.height - 842f) < 1f)
            // El visor debe elegir el papel por el tamaño del PDF (A4) y no forzar escalado.
            val prefs = pdf.documentCatalog.viewerPreferences
            assertTrue("Debe declarar preferencias del visor", prefs != null)
            assertTrue(
                "PickTrayByPDFSize debe estar activo",
                prefs.cosObject.getBoolean(org.apache.pdfbox.cos.COSName.getPDFName("PickTrayByPDFSize"), false)
            )
        }
    }

    @Test
    fun `la tablatura impresa usa los grosores de MuseScore`() {
        val song = ChordProParser.parse(
            chordProContent = "{title: Grosores}\n{key: C}\n{start_of_verse}\nHola\n{end_of_verse}",
            songId = "grosores",
            fallbackTitle = "Grosores"
        )
        val tablature = Tablature(
            id = "tab-grosores",
            songId = "grosores",
            title = "Grosores",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            measures = listOf(
                TabMeasure(slots = listOf(TabSlot(listOf(TabPluck(1, 0))))),
                TabMeasure(
                    slots = listOf(TabSlot(listOf(TabPluck(1, 2)))),
                    barline = TabBarline.DOUBLE
                ),
                TabMeasure(
                    slots = listOf(TabSlot(listOf(TabPluck(1, 3)))),
                    barline = TabBarline.FINAL
                )
            )
        )

        val file = Files.createTempFile("laudate-grosores", ".pdf").toFile()
        file.delete()
        val result = DesktopPdfExporter().write(
            file,
            song,
            SongPdfOptions(displayMode = SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE),
            tablatures = listOf(tablature)
        )
        assertTrue("El PDF se generó", result.isSuccess)

        Loader.loadPDF(file).use { pdf ->
            // Se comprueba sobre el **flujo de contenido** (los operadores `w` de grosor de trazo):
            // es exacto y no depende del rasterizador. Los valores son los de la hoja de estilos de
            // MuseScore (`styledef.cpp`) multiplicados por el espacio de pentagrama del documento.
            val stream = String(pdf.getPage(0).contents.readAllBytes(), Charsets.ISO_8859_1)
            val widths = Regex("""([0-9.]+) w""")
                .findAll(stream)
                .map { it.groupValues[1].toFloat() }
                .toList()
            assertTrue("el PDF no tiene trazos", widths.size >= 6)

            // El grosor más repetido es el de las **cuerdas** (una por cuerda y sistema).
            val staffWidth = widths.groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
            val staffSpace = staffWidth / 0.11f
            val barWidth = 0.18f * staffSpace
            val endBarWidth = 0.55f * staffSpace
            println("staffSpace=$staffSpace cuerda=$staffWidth trazos=$widths")

            assertTrue(
                "la cuerda debe medir 0,11 espacios (fue ${staffWidth / staffSpace})",
                kotlin.math.abs(staffWidth / staffSpace - 0.11f) < 0.005f
            )
            // Barras simples y dobles: 0,18 espacios. Debe haber al menos cuatro trazos así
            // (la simple, los dos de la doble y el fino de la final).
            val barStrokes = widths.count { kotlin.math.abs(it - barWidth) < 0.01f }
            assertTrue("faltan barras de 0,18 espacios (había $barStrokes)", barStrokes >= 4)
            // La barra **final** lleva un trazo grueso de 0,55 espacios.
            assertTrue(
                "falta el trazo grueso de la barra final (0,55 espacios)",
                widths.any { kotlin.math.abs(it - endBarWidth) < 0.02f }
            )
        }
    }

    /**
     * (D-42) La tablatura impresa **dibuja la fila de ritmo**: plicas, barras de unión, corchetes de
     * grupo irregular y glifos SMuFL, con **Bravura embebida**.
     *
     * Antes el PDF solo trazaba cuerdas, barras y cifras de traste, así que la tablatura impresa
     * salía «desnuda» frente a la de MuseScore. La geometría la decide el Dominio
     * (`TabRhythmPrimitives`), de modo que el papel y la pantalla salen del mismo cálculo.
     */
    @Test
    fun `la tablatura impresa dibuja la fila de ritmo con Bravura`() {
        fun tablatureOf(figure: RhythmFigure, tuplet: TabTuplet? = null) = Tablature(
            id = "tab-ritmo",
            songId = "ritmo",
            title = "Ritmo",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH,
            measures = listOf(
                TabMeasure(
                    slots = (0..3).map { i ->
                        TabSlot(
                            strings = listOf(TabPluck(1, i)),
                            rhythmicFigure = figure,
                            tuplet = tuplet
                        )
                    }
                )
            )
        )

        /** Operadores del flujo de contenido del PDF de una tablatura. */
        data class PdfProbe(val strokes: Int, val texts: Int, val curves: Int, val fills: Int)

        fun pdfOf(tablature: Tablature): PdfProbe {
            val song = ChordProParser.parse(
                chordProContent = "{title: Ritmo}\n{key: C}\n{start_of_verse}\nHola\n{end_of_verse}",
                songId = "ritmo",
                fallbackTitle = "Ritmo"
            )
            val file = Files.createTempFile("laudate-ritmo", ".pdf").toFile()
            file.delete()
            val result = DesktopPdfExporter().write(
                file,
                song,
                SongPdfOptions(displayMode = SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE),
                tablatures = listOf(tablature)
            )
            assertTrue("El PDF se generó", result.isSuccess)
            Loader.loadPDF(file).use { pdf ->
                val page = pdf.getPage(0)
                val stream = String(page.contents.readAllBytes(), Charsets.ISO_8859_1)
                // Operadores que añade la fila de ritmo: trazos (`S`), textos (`Tj`), curvas (`c`,
                // los contornos de los glifos SMuFL) y rellenos (`f`).
                fun count(op: String) = Regex("""\b$op\b""").findAll(stream).count()
                return PdfProbe(count("S"), count("Tj"), count("c"), count("f"))
            }
        }

        // Una **blanca** no lleva plica ni corchete; una **corchea** sí. La diferencia de trazos es
        // exactamente la fila de ritmo (plicas + barra de unión + corchetes).
        val half = pdfOf(tablatureOf(RhythmFigure.HALF))
        val eighth = pdfOf(tablatureOf(RhythmFigure.EIGHTH))
        println("trazos: blanca=${half.strokes} corchea=${eighth.strokes}")
        assertTrue(
            "las corcheas deben añadir plicas y barras (blanca=${half.strokes}, corchea=${eighth.strokes})",
            eighth.strokes > half.strokes
        )
        // Los **silencios** son glifos SMuFL: se trazan con los contornos de Bravura, así que
        // aparecen curvas. Una negra no lleva ningún glifo (solo la plica) y no tiene ninguna.
        // Se comparan negra y silencio de negra —y no corchea y blanca— porque las corcheas de un
        // compás completo van **unidas por una barra** y entonces no llevan corchete propio, que es
        // justo lo que hace MuseScore.
        val quarter = pdfOf(tablatureOf(RhythmFigure.QUARTER))
        val quarterRest = pdfOf(tablatureOf(RhythmFigure.QUARTER_REST))
        println("curvas: negra=${quarter.curves} silencio=${quarterRest.curves}")
        assertTrue(
            "el silencio debe trazar los contornos de Bravura " +
                "(negra=${quarter.curves}, silencio=${quarterRest.curves})",
            quarterRest.curves > quarter.curves
        )

        // El **grupo irregular** añade su cifra SMuFL: un `Tj` más que el mismo grupo sin rótulo.
        val withLabel = pdfOf(
            tablatureOf(
                RhythmFigure.EIGHTH,
                TabTuplet(3, 2, bracketType = TabTupletBracketType.SHOW_BRACKET)
            )
        )
        val withoutLabel = pdfOf(
            tablatureOf(
                RhythmFigure.EIGHTH,
                TabTuplet(
                    3,
                    2,
                    numberType = TabTupletNumberType.NO_TEXT,
                    bracketType = TabTupletBracketType.SHOW_BRACKET
                )
            )
        )
        // La cifra `3` es un **glifo relleno** (un camino más), no un texto: se compara el número de
        // rellenos, no el de `Tj`.
        println("rellenos: con cifra=${withLabel.fills} sin cifra=${withoutLabel.fills}")
        assertTrue(
            "la cifra del grupo irregular debe imprimirse " +
                "(con=${withLabel.fills}, sin=${withoutLabel.fills})",
            withLabel.fills > withoutLabel.fills
        )
    }

    /**
     * (D-44) La **indicación de compás** se imprime con los **glifos SMuFL** `timeSigN`, no con la
     * tipografía de texto: en Bravura los dígitos del compás están diseñados para apilarse y su tinta
     * mide 0,501 em. Antes el PDF los sacaba con la sans del texto, así que la fracción tenía otro
     * dibujo y otro tamaño que en la pantalla.
     *
     * Se compara una tablatura **con** cambio de compás (que añade dos dígitos) contra la misma
     * **sin** él: los dígitos nuevos suman **rellenos** (contornos de Bravura) y **no** suman `Tj`.
     */
    @Test
    fun `la indicacion de compas se imprime con glifos SMuFL y no como texto`() {
        fun tab(conCambio: Boolean) = Tablature(
            id = "tab-compas",
            songId = "compas",
            title = "Compás",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(
                    slots = (0..3).map { TabSlot(rhythmicFigure = RhythmFigure.QUARTER) }
                ),
                TabMeasure(
                    slots = (0..2).map { TabSlot(rhythmicFigure = RhythmFigure.QUARTER) },
                    time = if (conCambio) ScoreTimeSignature("3", 4) else null
                )
            )
        )
        val con = pdfProbeOf(tab(conCambio = true))
        val sin = pdfProbeOf(tab(conCambio = false))
        println("rellenos: con cambio=${con.fills} sin cambio=${sin.fills}")
        println("textos: con cambio=${con.texts} sin cambio=${sin.texts}")
        assertTrue(
            "los dígitos del compás deben trazarse como glifos " +
                "(con=${con.fills}, sin=${sin.fills})",
            con.fills > sin.fills
        )
        assertTrue(
            "y no deben imprimirse como texto (con=${con.texts}, sin=${sin.texts})",
            con.texts <= sin.texts
        )
    }

    /**
     * (D-46) La **blanca rayada** (`TablatureMinimStyle::SLASHED`) se imprime como dos
     * paralelogramos **rellenos**, no como texto ni como trazo: el PDF gana rellenos (`f`) y no
     * gana `Tj`. Se compara contra la misma tablatura con la plica corta (`SHORTER`).
     */
    @Test
    fun `la blanca rayada se imprime como paralelogramos rellenos`() {
        fun tab(minimStyle: MinimStyle) = Tablature(
            id = "tab-blanca",
            songId = "blanca",
            title = "Blanca",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(
                    slots = (0..3).map { TabSlot(rhythmicFigure = RhythmFigure.HALF) }
                )
            )
        )
        val rayada = pdfProbeOf(tab(MinimStyle.SLASHED), TabStaffOptions(minimStyle = MinimStyle.SLASHED))
        val corta = pdfProbeOf(tab(MinimStyle.SHORTER), TabStaffOptions(minimStyle = MinimStyle.SHORTER))
        println("rellenos: rayada=${rayada.fills} corta=${corta.fills}")
        assertTrue(
            "las barras de la blanca deben rellenarse (rayada=${rayada.fills}, corta=${corta.fills})",
            rayada.fills > corta.fills
        )
        assertTrue(
            "y no deben imprimirse como texto (rayada=${rayada.texts}, corta=${corta.texts})",
            rayada.texts <= corta.texts
        )
    }

    /**
     * (D-47) La **cifra de la nota ligada** llega al papel: con `ShowTiedFret::NONE`
     * (`stafftype.h:116-120`) la cifra de la nota que recibe la ligadura no se imprime, así que el
     * PDF tiene menos texto que con el valor por defecto `TIE_AND_FRET`.
     */
    @Test
    fun `la cifra de la nota ligada desaparece del papel con Nada`() {
        val tablature = Tablature(
            id = "tab-ligadura",
            songId = "ligadura",
            title = "Ligadura",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(
                    slots = listOf(
                        TabSlot(strings = listOf(TabPluck(TabPosition(1, 0), tiedToNext = true))),
                        TabSlot(strings = listOf(TabPluck(TabPosition(1, 0), tiedToNext = true))),
                        TabSlot(strings = listOf(TabPluck(TabPosition(1, 0), tiedToNext = true))),
                        TabSlot(strings = listOf(TabPluck(TabPosition(1, 0))))
                    )
                )
            )
        )
        val conCifra = pdfProbeOf(tablature, TabStaffOptions(showTiedFret = ShowTiedFret.TIE_AND_FRET))
        val sinCifra = pdfProbeOf(tablature, TabStaffOptions(showTiedFret = ShowTiedFret.NONE))
        println("cifras: con=${conCifra.texts} sin=${sinCifra.texts}")
        assertTrue(
            "con «Nada» se imprimen menos cifras (con=${conCifra.texts}, sin=${sinCifra.texts})",
            sinCifra.texts < conCifra.texts
        )
    }

    /**
     * (D-48) Los **ajustes del pentagrama** llegan al papel: con `showClef = false` y
     * `showMeasureNumbers = false` el PDF pierde la clave `TAB` y los números de compás. Es la prueba
     * de que la preferencia global no se queda en el editor.
     *
     * (D-55) La clave `TAB` ya **no** es texto: se imprime como el **glifo SMuFL** `6stringTabClef`
     * (contornos rellenos con `f`), igual que en el lienzo. Por eso la prueba mira los **rellenos** y
     * no los `Tj`: antes el PDF de escritorio escribía el literal `TAB` en Helvetica.
     */
    @Test
    fun `los ajustes del pentagrama llegan al PDF`() {
        val tablature = Tablature(
            id = "tab-ajustes",
            songId = "ajustes",
            title = "Ajustes",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = (0..1).map {
                TabMeasure(
                    slots = (0..3).map {
                        TabSlot(strings = listOf(TabPluck(TabPosition(1, 0))))
                    }
                )
            }
        )
        val completo = pdfProbeOf(tablature, TabStaffOptions())
        val sinCabecera = pdfProbeOf(
            tablature,
            TabStaffOptions(showClef = false, showMeasureNumbers = false)
        )
        println("textos: completo=${completo.texts} sinCabecera=${sinCabecera.texts}")
        println("rellenos: completo=${completo.fills} sinCabecera=${sinCabecera.fills}")
        assertTrue(
            "sin clave se imprimen menos glifos rellenos " +
                "(completo=${completo.fills}, sinCabecera=${sinCabecera.fills})",
            sinCabecera.fills < completo.fills
        )
        assertTrue(
            "sin números de compás no se imprimen más textos " +
                "(completo=${completo.texts}, sinCabecera=${sinCabecera.texts})",
            sinCabecera.texts <= completo.texts
        )
    }

    /** Operadores del flujo de contenido del PDF de una tablatura suelta. */
    private data class PdfProbe(val strokes: Int, val texts: Int, val curves: Int, val fills: Int)

    private fun pdfProbeOf(
        tablature: Tablature,
        staff: TabStaffOptions = TabStaffOptions()
    ): PdfProbe {
        val song = ChordProParser.parse(
            chordProContent = "{title: Ritmo}\n{key: C}\n{start_of_verse}\nHola\n{end_of_verse}",
            songId = "ritmo",
            fallbackTitle = "Ritmo"
        )
        val file = Files.createTempFile("laudate-probe", ".pdf").toFile()
        file.delete()
        val result = DesktopPdfExporter().write(
            file,
            song,
            SongPdfOptions(
                displayMode = SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE,
                tablatureStaff = staff
            ),
            tablatures = listOf(tablature)
        )
        assertTrue("El PDF se generó", result.isSuccess)
        Loader.loadPDF(file).use { pdf ->
            val page = pdf.getPage(0)
            val stream = String(page.contents.readAllBytes(), Charsets.ISO_8859_1)
            fun count(op: String) = Regex("""\b$op\b""").findAll(stream).count()
            return PdfProbe(count("S"), count("Tj"), count("c"), count("f"))
        }
    }

    /**
     * **Artefacto de comparación**: exporta el `salmo2.mscz` real a
     * `build/tablature-preview/salmo2-canticora.pdf` para medirlo contra el PDF de MuseScore con
     * `pdftoppm` + `tools/salmo2_measure.py`.
     *
     * No asevera geometría —de eso se encargan los tests de `TabEngraving` y del flujo de
     * contenido—: solo deja el fichero en disco para la inspección visual y numérica.
     */
    @Test
    fun `exporta el salmo2 a PDF para compararlo con MuseScore`() {
        val mscz = listOf(
            File("../core/domain/src/jvmTest/resources/salmo2.mscz"),
            File("core/domain/src/jvmTest/resources/salmo2.mscz"),
            File("src/jvmTest/resources/salmo2.mscz")
        ).firstOrNull { it.exists() }
        org.junit.Assume.assumeTrue("falta core/domain/src/jvmTest/resources/salmo2.mscz", mscz != null)
        val mscx = java.util.zip.ZipInputStream(mscz!!.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name.endsWith(".mscx")) return@use zip.readBytes().toString(Charsets.UTF_8)
                e = zip.nextEntry
            }
            null
        } ?: return
        val tablature = com.grafitto.laudate.domain.serializer.MsczParser.parse(mscx, songId = "salmo2")
        org.junit.Assume.assumeTrue("el mscz no se pudo parsear", tablature != null)

        val song = ChordProParser.parse(
            chordProContent = "{title: Salmo 2}\n{key: D}\n[D]Texto",
            songId = "salmo2",
            fallbackTitle = "Salmo 2"
        )
        val outDir = File("build/tablature-preview").apply { mkdirs() }
        val file = File(outDir, "salmo2-canticora.pdf")
        file.delete()
        val result = DesktopPdfExporter().write(
            file,
            song,
            SongPdfOptions(displayMode = SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE),
            tablatures = listOf(tablature!!)
        )
        assertTrue("El PDF se generó", result.isSuccess)
        assertTrue("el PDF no está vacío", file.length() > 0)
    }
}
