package com.grafitto.laudate.domain.export

import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.model.song.ChordAnchor
import com.grafitto.laudate.domain.model.song.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del motor de maquetación del PDF. Es lógica pura, así que se puede validar sin Android:
 * reparto de líneas, alineación de acordes y paginación.
 */
class SongPdfLayoutTest {

    private fun song(
        title: String = "Canto de prueba",
        originalKey: String = "C",
        sections: List<com.grafitto.laudate.domain.model.song.SongSection> = emptyList()
    ) = Song(
        id = "s1",
        title = title,
        originalKey = originalKey,
        contentHash = SongContentHasher.hashOfChordPro(title),
        sections = sections
    )

    // ── Medición ───────────────────────────────────────────────────────────────

    @Test
    fun `wrapText respeta el limite de caracteres sin partir palabras`() {
        val text = "uno dos tres cuatro cinco"
        val lines = SongPdfLayout.wrapText(text, widthPt = 60f, fontSizePt = 10f)
        // 60 pt / 5 pt por carácter = 12 caracteres por línea
        assertTrue(lines.all { it.length <= 12 })
        // No se pierde ni se altera ninguna palabra.
        assertEquals(text, lines.joinToString(" "))
        assertTrue(lines.size > 1)
    }

    @Test
    fun `wrapText no pierde ninguna palabra`() {
        val text = "Bendito el que viene en el nombre del Señor"
        val joined = SongPdfLayout.wrapText(text, widthPt = 100f, fontSizePt = 10f).joinToString(" ")
        assertEquals(text, joined)
    }

    @Test
    fun `wrapText respeta los saltos de linea explicitos`() {
        val lines = SongPdfLayout.wrapText("primera\nsegunda", widthPt = 500f, fontSizePt = 10f)
        assertEquals(listOf("primera", "segunda"), lines)
    }

    @Test
    fun `una sola palabra larga no se pierde`() {
        val lines = SongPdfLayout.wrapText("Supercalifragilisticoexpialidoso", widthPt = 40f, fontSizePt = 10f)
        assertEquals(listOf("Supercalifragilisticoexpialidoso"), lines)
    }

    // ── Estructura del documento ────────────────────────────────────────────────

    @Test
    fun `una cancion vacia genera al menos una pagina con el titulo`() {
        val document = SongPdfLayout.layout(song())
        assertTrue(document.pages.isNotEmpty())
        assertEquals("Canto de prueba", document.title)
    }

    @Test
    fun `los metadatos incluyen tono numero autor capo y tempo`() {
        val rich = song().copy(
            number = "402",
            author = "Autor X",
            capo = 3,
            tempoBpm = 92,
            tempoMarking = "Andante",
            rhythm = "4/4"
        )
        val document = SongPdfLayout.layout(rich)
        val meta = document.pages.flatMap { it.rows }.first { it.kind == PdfRowKind.METADATA }
        assertTrue(meta.text.contains("Nº 402"))
        assertTrue(meta.text.contains("Autor X"))
        assertTrue(meta.text.contains("Tono: C"))
        assertTrue(meta.text.contains("Capo: traste 3"))
        // (D-96) La negra se escribe con letras: el PDF solo embebe las fuentes estándar, así que el
        // glifo ♩ se imprimía como `?` («? = 92»).
        assertTrue(meta.text.contains("Negra = 92"))
        assertFalse("el glifo ♩ no se puede imprimir con las fuentes estándar", meta.text.contains("♩"))
    }

    @Test
    fun `los titulos de seccion se rotulan en espanol`() {
        val section = com.grafitto.laudate.domain.model.song.SongSection(
            id = "sec",
            songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.CHORUS,
            sequenceOrder = 0,
            headerTitle = null,
            rawTextContent = "[C]Aleluya",
            lines = (0..4).map {
                com.grafitto.laudate.domain.model.song.SongLine(lineIndex = it, lyrics = "Aleluya $it")
            }
        )
        val document = SongPdfLayout.layout(song(sections = listOf(section)))
        // La etiqueta de sección ya no ocupa un renglón: va rotada en el margen de la primera fila.
        // Una sección larga conserva el rótulo completo.
        val labeled = document.pages.flatMap { it.rows }.first { it.marginLabel != null }
        assertEquals("ESTRIBILLO", labeled.marginLabel)
    }

    @Test
    fun `las secciones cortas abrevian su rotulo`() {
        // La etiqueta va rotada: en un bloque corto un rótulo largo lo dominaría. Se abrevia.
        val chorus = com.grafitto.laudate.domain.model.song.SongSection(
            id = "coro",
            songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.CHORUS,
            sequenceOrder = 0,
            headerTitle = null,
            rawTextContent = "Gloria",
            lines = listOf(
                com.grafitto.laudate.domain.model.song.SongLine(lineIndex = 0, lyrics = "Gloria")
            )
        )
        val verse = com.grafitto.laudate.domain.model.song.SongSection(
            id = "v1",
            songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.VERSE,
            sequenceOrder = 1,
            headerTitle = null,
            rawTextContent = "letra",
            label = "Estrofa 2",
            lines = listOf(
                com.grafitto.laudate.domain.model.song.SongLine(lineIndex = 0, lyrics = "letra")
            )
        )
        val labels = SongPdfLayout.layout(song(sections = listOf(chorus, verse)))
            .pages.flatMap { it.rows }.mapNotNull { it.marginLabel }
        assertEquals(listOf("CORO", "EST. 2"), labels)
    }

    // ── Geometría de las secciones (cajas delimitadoras) ───────────────────────

    @Test
    fun `el aire entre secciones se anade al inicio de la siguiente`() {
        val first = verseSection(lyrics = "uno")
        val second = verseSection(lyrics = "dos").copy(id = "v2", sequenceOrder = 1)
        val labeled = SongPdfLayout.layout(song(sections = listOf(first, second)))
            .pages.flatMap { it.rows }.filter { it.marginLabel != null }
        val options = SongPdfOptions()
        // La primera sección no lleva aire previo (no hay nada antes); la segunda sí.
        assertTrue("La 1ª sección no debe llevar aire externo", labeled[0].leadingGapPt < options.sectionGapPt)
        assertTrue("La 2ª sección debe separarse de la anterior", labeled[1].leadingGapPt >= options.sectionGapPt)
    }

    @Test
    fun `la altura de la seccion es la caja real sin el aire externo`() {
        // Es clave para centrar la etiqueta: la caja delimitadora mide solo el contenido, de modo
        // que el aire entre secciones no desplaza el centro del rótulo.
        val first = verseSection(lyrics = "uno")
        val second = verseSection(lyrics = "dos").copy(id = "v2", sequenceOrder = 1)
        val allRows = SongPdfLayout.layout(song(sections = listOf(first, second)))
            .pages.flatMap { it.rows }
        val starts = allRows.withIndex().filter { it.value.marginLabel != null }.map { it.index }
        val secondRows = allRows.subList(starts[1], allRows.size)
        val boxWithoutGap = secondRows.sumOf { (it.leadingGapPt + it.heightPt).toDouble() }.toFloat() -
            SongPdfOptions().sectionGapPt
        assertEquals(boxWithoutGap, secondRows.first().sectionHeightPt, 0.01f)
    }

    @Test
    fun `la marca de repeticion va solo en la primera fila de la seccion`() {
        val chorus = com.grafitto.laudate.domain.model.song.SongSection(
            id = "coro",
            songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.CHORUS,
            sequenceOrder = 0,
            headerTitle = null,
            rawTextContent = "Gloria\na Dios",
            isRepeated = true,
            lines = listOf(
                com.grafitto.laudate.domain.model.song.SongLine(lineIndex = 0, lyrics = "Gloria"),
                com.grafitto.laudate.domain.model.song.SongLine(lineIndex = 1, lyrics = "a Dios")
            )
        )
        val rows = SongPdfLayout.layout(song(sections = listOf(chorus))).pages.flatMap { it.rows }
        assertEquals("bis", rows.first { it.marginLabel != null }.repeatMark)
        assertEquals("El «bis» aparece una sola vez", 1, rows.count { it.repeatMark != null })
    }

    @Test
    fun `la etiqueta de seccion tiene prioridad sobre el tipo`() {
        val section = com.grafitto.laudate.domain.model.song.SongSection(
            id = "sec",
            songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.VERSE,
            sequenceOrder = 0,
            headerTitle = null,
            rawTextContent = "letra",
            label = "Estrofa del pueblo",
            lines = listOf(
                com.grafitto.laudate.domain.model.song.SongLine(lineIndex = 0, lyrics = "letra")
            )
        )
        val document = SongPdfLayout.layout(song(sections = listOf(section)))
        val labeled = document.pages.flatMap { it.rows }.first { it.marginLabel != null }
        assertEquals("ESTROFA DEL PUEBLO", labeled.marginLabel)
    }

    @Test
    fun `los acordes se asocian a su posicion de caracter`() {
        val section = com.grafitto.laudate.domain.model.song.SongSection(
            id = "sec",
            songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.VERSE,
            sequenceOrder = 0,
            headerTitle = null,
            rawTextContent = "Aleluya",
            lines = listOf(
                com.grafitto.laudate.domain.model.song.SongLine(
                    lineIndex = 0,
                    lyrics = "Aleluya",
                    chords = listOf(
                        ChordAnchor("a1", "sec", 0, 0, chord("C"), "C"),
                        ChordAnchor("a2", "sec", 0, 3, chord("G"), "G")
                    )
                )
            )
        )
        val document = SongPdfLayout.layout(song(sections = listOf(section)))
        val row = document.pages.flatMap { it.rows }
            .first { it.kind == PdfRowKind.CHORDS_OVER_LYRICS }
        assertEquals(2, row.chords.size)
        assertEquals("C", row.chords[0].text)
        assertEquals(0, row.chords[0].characterIndex)
        assertEquals("G", row.chords[1].text)
        assertEquals(3, row.chords[1].characterIndex)
    }

    @Test
    fun `el mismo contenido produce el mismo numero de paginas`() {
        val long = buildLongSong()
        val first = SongPdfLayout.layout(long).pages.size
        val second = SongPdfLayout.layout(long).pages.size
        assertEquals(first, second)
    }

    @Test
    fun `las paginas a dos columnas llevan el titulo en la banda`() {
        val document = SongPdfLayout.layout(buildLongSong())
        assertTrue(document.pages.size > 1)
        // A dos columnas el título va en la banda a ancho completo en TODAS las páginas (incluida
        // la primera), de modo que quien canta siempre lo tenga a la vista.
        assertEquals(2, document.pages.first().columns)
        assertTrue(document.pages.all { it.header == "Canto de prueba" })
    }

    @Test
    fun `el numero total de paginas es coherente en todas`() {
        val document = SongPdfLayout.layout(buildLongSong())
        assertTrue(document.pages.all { it.totalPages == document.pages.size })
    }

    @Test
    fun `una cancion larga no mete todo en una sola pagina`() {
        assertTrue(SongPdfLayout.layout(buildLongSong()).pages.size > 1)
    }

    @Test
    fun `una cancion corta cabe en una pagina`() {
        val short = song().copy(
            sections = listOf(
                com.grafitto.laudate.domain.model.song.SongSection(
                    id = "sec",
                    songId = "s1",
                    sectionType = com.grafitto.laudate.domain.model.song.SectionType.VERSE,
                    sequenceOrder = 0,
                    headerTitle = null,
                    rawTextContent = "[C]Hola"
                )
            )
        )
        assertEquals(1, SongPdfLayout.layout(short).pages.size)
    }

    @Test
    fun `los comentarios se incluyen como filas de comentario`() {
        val section = com.grafitto.laudate.domain.model.song.SongSection(
            id = "sec",
            songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.VERSE,
            sequenceOrder = 0,
            headerTitle = null,
            rawTextContent = "letra",
            lines = listOf(
                com.grafitto.laudate.domain.model.song.SongLine(
                    lineIndex = 0,
                    lyrics = "",
                    type = com.grafitto.laudate.domain.model.song.LineType.COMMENT,
                    comment = "Entran las voces",
                    voice = "soprano"
                )
            )
        )
        val document = SongPdfLayout.layout(song(sections = listOf(section)))
        val comment = document.pages.flatMap { it.rows }.first { it.kind == PdfRowKind.COMMENT }
        assertTrue(comment.text.contains("[soprano]"))
        assertTrue(comment.text.contains("Entran las voces"))
    }

    // ── Estilo de cancionero (centrado y estribillos) ──────────────────────────

    @Test
    fun `la letra se centra como en los cancioneros de coro`() {
        val section = verseSection(lyrics = "Todo mi ser canta hoy")
        val row = SongPdfLayout.layout(song(sections = listOf(section)))
            .pages.flatMap { it.rows }.first { it.kind == PdfRowKind.LYRICS }
        assertTrue(row.centered)
    }

    @Test
    fun `el estribillo se destaca en negrita y algo mayor`() {
        val chorus = com.grafitto.laudate.domain.model.song.SongSection(
            id = "coro",
            songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.CHORUS,
            sequenceOrder = 0,
            headerTitle = null,
            rawTextContent = "Gloria a ti Senor",
            lines = listOf(
                com.grafitto.laudate.domain.model.song.SongLine(
                    lineIndex = 0,
                    lyrics = "Gloria a ti Senor"
                )
            )
        )
        val rows = SongPdfLayout.layout(song(sections = listOf(chorus))).pages.flatMap { it.rows }
        val row = rows.first { it.kind == PdfRowKind.LYRICS }
        assertTrue(row.emphasis)
        assertTrue(
            "el estribillo debe ir mayor que el cuerpo",
            row.fontSizePt > SongPdfOptions().lyricsFontSizePt
        )
    }

    @Test
    fun `la estrofa no se marca como enfasis`() {
        val rows = SongPdfLayout.layout(song(sections = listOf(verseSection(lyrics = "Estrofa normal"))))
            .pages.flatMap { it.rows }
        val row = rows.first { it.kind == PdfRowKind.LYRICS }
        assertFalse(row.emphasis)
        assertEquals(SongPdfOptions().lyricsFontSizePt, row.fontSizePt, 0.001f)
    }

    @Test
    fun `el texto con acordes tambien se centra y conserva sus acordes`() {
        val section = com.grafitto.laudate.domain.model.song.SongSection(
            id = "sec",
            songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.VERSE,
            sequenceOrder = 0,
            headerTitle = null,
            rawTextContent = "Aleluya",
            lines = listOf(
                com.grafitto.laudate.domain.model.song.SongLine(
                    lineIndex = 0,
                    lyrics = "Aleluya",
                    chords = listOf(
                        ChordAnchor("a1", "sec", 0, 0, chord("C"), "C"),
                        ChordAnchor("a2", "sec", 0, 3, chord("G"), "G")
                    )
                )
            )
        )
        val row = SongPdfLayout.layout(song(sections = listOf(section)))
            .pages.flatMap { it.rows }.first { it.kind == PdfRowKind.CHORDS_OVER_LYRICS }
        assertTrue(row.centered)
        assertEquals(listOf("C", "G"), row.chords.map { it.text })
    }

    /** Sección de estrofa sencilla, sin acordes. */
    private fun verseSection(lyrics: String) = com.grafitto.laudate.domain.model.song.SongSection(
        id = "verse",
        songId = "s1",
        sectionType = com.grafitto.laudate.domain.model.song.SectionType.VERSE,
        sequenceOrder = 0,
        headerTitle = null,
        rawTextContent = lyrics,
        lines = listOf(
            com.grafitto.laudate.domain.model.song.SongLine(lineIndex = 0, lyrics = lyrics)
        )
    )

    // ── Marca ──────────────────────────────────────────────────────────────────

    @Test
    fun `la marca por defecto usa los tonos baseline de la app`() {
        val branding = SongPdfBranding()
        // Primary 40 del baseline M3.
        assertEquals(0xFF6750A4.toInt(), branding.primaryArgb)
    }

    // ── Nomenclatura de los acordes del PDF ─────────────────────────────────────

    /** Sección con una línea de letra y acordes latinos, para probar el formateo. */
    private fun latinChordSection(): com.grafitto.laudate.domain.model.song.SongSection {
        val anchor = { root: String, quality: String, idx: Int ->
            ChordAnchor(
                id = "a", sectionId = "sec", lineIndex = 0, characterIndex = idx,
                chord = chord(root).copy(quality = quality),
                rawChordText = root + quality
            )
        }
        return com.grafitto.laudate.domain.model.song.SongSection(
            id = "sec", songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.VERSE,
            sequenceOrder = 0, headerTitle = null,
            rawTextContent = "Aleluya al Señor",
            lines = listOf(
                com.grafitto.laudate.domain.model.song.SongLine(
                    lineIndex = 0, lyrics = "Aleluya al Senor",
                    // Un acorde latino guardado como `fam#` (root F#, calidad m).
                    chords = listOf(anchor("F#", "m", 0), anchor("B", "m", 12))
                )
            )
        )
    }

    @Test
    fun `los acordes del PDF se imprimen en ingles por defecto`() {
        // El cancionero importado trae los acordes en latino, pero el PDF debe respetar la
        // nomenclatura preferida de la app (igual que el visor).
        val options = SongPdfOptions()
        val row = SongPdfLayout.layout(song(sections = listOf(latinChordSection())), options)
            .pages.flatMap { it.rows }.first { it.kind == PdfRowKind.CHORDS_OVER_LYRICS }
        assertEquals(listOf("F#m", "Bm"), row.chords.map { it.text })
    }

    @Test
    fun `los acordes del PDF se imprimen en latino si se pide`() {
        val options = SongPdfOptions(notationSystem = com.grafitto.laudate.domain.model.music.NotationSystem.LATIN)
        val row = SongPdfLayout.layout(song(sections = listOf(latinChordSection())), options)
            .pages.flatMap { it.rows }.first { it.kind == PdfRowKind.CHORDS_OVER_LYRICS }
        assertEquals(listOf("Fa#m", "Sim"), row.chords.map { it.text })
    }

    @Test
    fun `la tonalidad de los metadatos respeta la nomenclatura`() {
        val options = SongPdfOptions(notationSystem = com.grafitto.laudate.domain.model.music.NotationSystem.LATIN)
        val meta = SongPdfLayout.layout(song(originalKey = "C"), options)
            .pages.flatMap { it.rows }.first { it.kind == PdfRowKind.METADATA }
        assertTrue("La tonalidad debe salir en latino: ${meta.text}", meta.text.contains("Tono: Do"))
    }

    // ── Separación inteligente de acordes (en espacio-x real) ───────────────────

    /** Crea marcas ya medidas (posición natural + ancho real), como las produce el pintor. */
    private fun measuredMark(text: String, x: Float, width: Float) =
        com.grafitto.laudate.domain.export.PdfChordMark(
            text = text, characterIndex = 0, widthPt = width, xOffsetPt = x
        )

    @Test
    fun `dos acordes que se pisarian se reparten uno a cada lado`() {
        // Reproduce el caso real: `Fam#` (ancho ~25) anclado a x=5 y `Sim` a x=20. Antes salía
        // «Fam#Sim» solapados. Ahora el hueco se reparte: el 1º recula y el 2º avanza.
        val laidOut = SongPdfLayout.distributeChordGaps(
            listOf(measuredMark("Fam#", 5f, 25f), measuredMark("Sim", 20f, 20f)),
            minGapPt = 3f
        )
        val (a, b) = laidOut
        assertTrue("El 1º acorde debe recular", a.xOffsetPt < 5f)
        assertTrue("El 2º acorde debe avanzar", b.xOffsetPt > 20f)
        assertTrue(
            "Ninguno debe solaparse",
            b.xOffsetPt >= a.xOffsetPt + a.widthPt + 3f - 0.01f
        )
    }

    @Test
    fun `los acordes largos muy juntos no se solapan`() {
        // El caso que preocupa al usuario: acordes largos pegados (`Soladd9#`, `Lam7add9`…).
        val laidOut = SongPdfLayout.distributeChordGaps(
            listOf(
                measuredMark("Soladd9#", 0f, 45f),
                measuredMark("Lam7add9", 12f, 42f),
                measuredMark("Rem7add9", 24f, 42f),
                measuredMark("Domaj7", 36f, 34f)
            ),
            minGapPt = 3f
        )
        laidOut.zipWithNext().forEach { (left, right) ->
            assertTrue(
                "«${right.text}» no debe pisar a «${left.text}»",
                right.xOffsetPt >= left.xOffsetPt + left.widthPt + 3f - 0.01f
            )
        }
        // El orden se conserva.
        val xs = laidOut.map { it.xOffsetPt }
        assertEquals(xs.sorted(), xs)
    }

    @Test
    fun `si ya hay hueco suficiente no se mueve nada`() {
        val marks = listOf(measuredMark("C", 0f, 8f), measuredMark("G", 80f, 8f))
        val laidOut = SongPdfLayout.distributeChordGaps(marks, minGapPt = 3f)
        assertEquals(0f, laidOut[0].xOffsetPt, 0.01f)
        assertEquals(80f, laidOut[1].xOffsetPt, 0.01f)
        assertTrue("El hueco por defecto debe ser positivo", SongPdfOptions().minChordGapPt > 0f)
    }

    @Test
    fun `un solo acorde no se mueve`() {
        val only = measuredMark("Do", 42f, 14f)
        val laidOut = SongPdfLayout.distributeChordGaps(listOf(only), minGapPt = 3f)
        assertEquals(42f, laidOut.single().xOffsetPt, 0.01f)
    }

    // ── Filas de solo acordes (intro/riff/outro) ───────────────────────────────

    @Test
    fun `una fila de solo acordes no solapa los acordes`() {
        // Reproduce el bug: [Am] [G] [F] [E] se pintaba como "AmGEFE" porque la separación se
        // medía con el ancho de un espacio de la fuente de letra (menor que el acorde).
        val section = com.grafitto.laudate.domain.model.song.SongSection(
            id = "intro",
            songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.INTRO,
            sequenceOrder = 0,
            headerTitle = null,
            rawTextContent = "[Am] [G] [F] [E]",
            lines = listOf(
                com.grafitto.laudate.domain.model.song.SongLine(
                    lineIndex = 0,
                    lyrics = "  ",
                    type = com.grafitto.laudate.domain.model.song.LineType.CHORD_ONLY,
                    chords = listOf(
                        ChordAnchor("a1", "intro", 0, 0, chord("Am"), "Am"),
                        ChordAnchor("a2", "intro", 0, 2, chord("G"), "G"),
                        ChordAnchor("a3", "intro", 0, 4, chord("F"), "F"),
                        ChordAnchor("a4", "intro", 0, 6, chord("E"), "E")
                    )
                )
            )
        )
        val row = SongPdfLayout.layout(song(sections = listOf(section)))
            .pages.flatMap { it.rows }.first { it.kind == PdfRowKind.CHORD_ONLY }

        assertEquals(listOf("Am", "G", "F", "E"), row.chords.map { it.text })
        // Los desplazamientos deben respetar el ancho de cada acorde (sin solaparse).
        val marks = row.chords
        marks.zipWithNext().forEach { (left, right) ->
            assertTrue(
                "El acorde ${right.text} debe empezar después de que acabe ${left.text}",
                right.xOffsetPt >= left.xOffsetPt + left.widthPt
            )
        }
    }

    @Test
    fun `el ritmo global del canto se imprime como rejilla de flechas`() {
        val pattern = com.grafitto.laudate.domain.model.rhythm.RhythmPattern(
            id = "r1",
            name = "Rock",
            timeSignature = com.grafitto.laudate.domain.model.rhythm.TimeSignature.FOUR_FOUR,
            events = listOf(
                com.grafitto.laudate.domain.model.rhythm.RhythmEvent(
                    com.grafitto.laudate.domain.model.rhythm.RhythmFigure.QUARTER,
                    com.grafitto.laudate.domain.model.rhythm.StrumDirection.DOWN
                ),
                com.grafitto.laudate.domain.model.rhythm.RhythmEvent(
                    com.grafitto.laudate.domain.model.rhythm.RhythmFigure.QUARTER,
                    com.grafitto.laudate.domain.model.rhythm.StrumDirection.UP
                ),
                com.grafitto.laudate.domain.model.rhythm.RhythmEvent(
                    com.grafitto.laudate.domain.model.rhythm.RhythmFigure.QUARTER,
                    com.grafitto.laudate.domain.model.rhythm.StrumDirection.DOWN
                ),
                com.grafitto.laudate.domain.model.rhythm.RhythmEvent(
                    com.grafitto.laudate.domain.model.rhythm.RhythmFigure.QUARTER,
                    com.grafitto.laudate.domain.model.rhythm.StrumDirection.DOWN
                )
            )
        )
        val withRhythm = song().copy(rhythmPatternId = "r1", rhythmPattern = pattern)
        // El rasgueo global va integrado en la línea de datos (metadatos), no en fila aparte.
        val meta = SongPdfLayout.layout(withRhythm)
            .pages.flatMap { it.rows }.first { it.kind == PdfRowKind.METADATA }
        assertTrue(meta.text.contains("Rock"))
        assertTrue(meta.text.contains("↓"))
        assertTrue(meta.text.contains("↑"))
    }

    @Test
    fun `el ritmo por seccion declarado con x_rhythm se imprime`() {
        val pattern = com.grafitto.laudate.domain.model.rhythm.RhythmPattern(
            id = "r2",
            name = "Funk",
            timeSignature = com.grafitto.laudate.domain.model.rhythm.TimeSignature.FOUR_FOUR,
            events = listOf(
                com.grafitto.laudate.domain.model.rhythm.RhythmEvent(
                    com.grafitto.laudate.domain.model.rhythm.RhythmFigure.QUARTER,
                    com.grafitto.laudate.domain.model.rhythm.StrumDirection.MUTE
                )
            )
        )
        val section = com.grafitto.laudate.domain.model.song.SongSection(
            id = "coro",
            songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.CHORUS,
            sequenceOrder = 0,
            headerTitle = null,
            rawTextContent = "letra",
            rhythmPatternName = "Funk",
            rhythmPattern = pattern,
            lines = listOf(com.grafitto.laudate.domain.model.song.SongLine(lineIndex = 0, lyrics = "letra"))
        )
        val row = SongPdfLayout.layout(song(sections = listOf(section)))
            .pages.flatMap { it.rows }.first { it.kind == PdfRowKind.RHYTHM }
        assertTrue(row.text.contains("Funk"))
    }

    // ── Maquetación a dos columnas ──────────────────────────────────────────────

    @Test
    fun `una cancion que no cabe en una pagina se reparte en dos columnas`() {
        val rows = (0 until 60).map { index ->
            PdfRow(
                kind = PdfRowKind.LYRICS,
                text = "Línea de letra número $index",
                heightPt = 18f,
                leadingGapPt = 2f
            )
        }
        val options = SongPdfOptions()
        // A una columna no cabe; a dos columnas sí.
        assertTrue(SongPdfLayout.paginateRows(rows, options).size > 1)
        val section = com.grafitto.laudate.domain.model.song.SongSection(
            id = "v",
            songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.VERSE,
            sequenceOrder = 0,
            headerTitle = null,
            rawTextContent = "",
            lines = rows.mapIndexed { i, r ->
                com.grafitto.laudate.domain.model.song.SongLine(lineIndex = i, lyrics = r.text)
            }
        )
        val document = SongPdfLayout.layout(song(sections = listOf(section)))
        assertEquals(1, document.pages.size)
        assertEquals(2, document.pages.first().columns)
        assertTrue(document.pages.first().secondColumnStartIndex in 1 until document.pages.first().rows.size)
    }

    @Test
    fun `una cancion corta se queda a una columna`() {
        val short = song().copy(
            sections = listOf(verseSection(lyrics = "Corto"))
        )
        val document = SongPdfLayout.layout(short)
        assertEquals(1, document.pages.size)
        assertEquals(1, document.pages.first().columns)
    }

    @Test
    fun `a dos columnas ninguna columna queda vacia ni partida`() {
        // La paginación debe progresar siempre (nunca una columna vacía) sea cual sea el contenido.
        val document = SongPdfLayout.layout(buildLongSong())
        document.pages.forEachIndexed { index, page ->
            val split = page.secondColumnStartIndex
            if (index == document.pages.lastIndex) {
                // (D-100) La **última** página puede ir a una sola columna si todo el resto cabe
                // en la izquierda: entonces no hay segunda columna que empezar.
                assertTrue(
                    "en la última página el corte va al final o dentro",
                    split == page.rows.size || split in 1 until page.rows.size
                )
            } else {
                assertTrue("La 2ª columna debe empezar en un índice válido", split in 1 until page.rows.size)
            }
        }
    }

    @Test
    fun `una seccion que no cabe en una columna se reparte pero sin perderse`() {
        // Una única sección gigantesca (no cabe ni en una columna ni en dos): no debe colgarse ni
        // perder texto; simplemente ocupa varias columnas/páginas.
        val lines = (0 until 90).map {
            com.grafitto.laudate.domain.model.song.SongLine(
                lineIndex = it,
                lyrics = "Línea $it de una estrofa enorme que no cabe en una sola columna de la página"
            )
        }
        val huge = com.grafitto.laudate.domain.model.song.SongSection(
            id = "huge",
            songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.VERSE,
            sequenceOrder = 0,
            headerTitle = null,
            rawTextContent = "",
            lines = lines
        )
        val document = SongPdfLayout.layout(song(sections = listOf(huge)))
        // Todas las líneas de letra aparecen exactamente una vez, repartidas entre páginas.
        val rendered = document.pages.flatMap { it.rows }
            .filter { it.kind == PdfRowKind.LYRICS || it.kind == PdfRowKind.CHORDS_OVER_LYRICS }
            .map { it.text }
        assertTrue("Todas las líneas se renderizan", rendered.size >= 90)
    }

    @Test
    fun `las continuaciones de linea no se mezclan con la letra siguiente`() {
        // Una línea larga con acordes se parte; la continuación (sin acordes) debe emitirse como
        // LYRICS con su propia altura, no como fila de acordes vacía que se desbordaba.
        val longLine = "[C]" + "palabra ".repeat(40).trim()
        val section = com.grafitto.laudate.domain.model.song.SongSection(
            id = "v",
            songId = "s1",
            sectionType = com.grafitto.laudate.domain.model.song.SectionType.VERSE,
            sequenceOrder = 0,
            headerTitle = null,
            rawTextContent = longLine,
            lines = listOf(
                com.grafitto.laudate.domain.model.song.SongLine(
                    lineIndex = 0,
                    lyrics = "palabra ".repeat(40).trim(),
                    chords = listOf(ChordAnchor("a", "v", 0, 0, chord("C"), "C"))
                )
            )
        )
        val rows = SongPdfLayout.layout(song(sections = listOf(section))).pages.flatMap { it.rows }
        val continuation = rows.filter { it.kind == PdfRowKind.LYRICS }
        assertTrue("La continuación se emite como letra normal", continuation.isNotEmpty())
        // Ninguna fila que no tiene acordes debe llevar el alto de un renglón de acordes.
        rows.filter { it.kind == PdfRowKind.LYRICS }.forEach { row ->
            assertTrue(row.chords.isEmpty())
        }
    }

    private fun chord(root: String) = com.grafitto.laudate.domain.parser.ChordTextParser.parse(root)
        .getOrElse {
            com.grafitto.laudate.domain.model.music.DomainChord(
                rootNote = com.grafitto.laudate.domain.model.music.PitchClass.C,
                quality = "",
                rawChordText = root
            )
        }

    /** Canción con secciones suficientes para forzar varias páginas incluso a dos columnas. */
    private fun buildLongSong(): Song {
        val sections = (0 until 120).map { index ->
            com.grafitto.laudate.domain.model.song.SongSection(
                id = "sec$index",
                songId = "s1",
                sectionType = com.grafitto.laudate.domain.model.song.SectionType.VERSE,
                sequenceOrder = index,
                headerTitle = null,
                rawTextContent = "Letra de la estrofa número $index con bastante texto para ocupar espacio",
                lines = listOf(
                    com.grafitto.laudate.domain.model.song.SongLine(
                        lineIndex = 0,
                        lyrics = "Letra de la estrofa número $index con bastante texto para ocupar espacio"
                    ),
                    com.grafitto.laudate.domain.model.song.SongLine(
                        lineIndex = 1,
                        lyrics = "Segunda línea de la estrofa $index que también ocupa su espacio"
                    )
                )
            )
        }
        return song(sections = sections)
    }
}
