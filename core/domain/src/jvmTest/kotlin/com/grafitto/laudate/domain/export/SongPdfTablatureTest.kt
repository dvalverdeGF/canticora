package com.grafitto.laudate.domain.export

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.song.ChordAnchor
import com.grafitto.laudate.domain.model.song.SectionType
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.song.SongLine
import com.grafitto.laudate.domain.model.song.SongSection
import com.grafitto.laudate.domain.model.music.DomainChord
import com.grafitto.laudate.domain.model.music.PitchClass
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la impresión de **tablaturas** en el PDF (modo de visualización, §10). */
class SongPdfTablatureTest {

    private fun section(id: String, type: SectionType = SectionType.VERSE, lyrics: String = "Aleluya") = SongSection(
        id = id,
        songId = "s1",
        sectionType = type,
        sequenceOrder = 0,
        headerTitle = null,
        rawTextContent = lyrics,
        lines = listOf(SongLine(lineIndex = 0, lyrics = lyrics))
    )

    private fun song(vararg sections: SongSection) = Song(
        id = "s1",
        title = "Canto",
        originalKey = "C",
        sections = sections.toList()
    )

    private fun tablature(sectionId: String? = null) = Tablature(
        id = "t1",
        songId = "s1",
        title = "Riff",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.QUARTER,
        sectionId = sectionId,
        measures = listOf(
            TabMeasure(slots = listOf(TabSlot(strings = listOf(TabPluck(1, 3))), TabSlot()))
        )
    )

    @Test
    fun tablatureIsPrintedWhenModeIncludesIt() {
        val options = SongPdfOptions(displayMode = SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE)
        val document = SongPdfLayout.layout(song(section("sec1")), options, tablatures = listOf(tablature()))
        val rows = document.pages.flatMap { it.rows }
        assertTrue(rows.any { it.kind == PdfRowKind.TABLATURE })
    }

    /**
     * (D-85) **La tablatura se alinea a la izquierda con el texto.**
     *
     * Antes la fila recuperaba la franja de etiquetas de sección (`marginLabelWidthPt = 18`) a su
     * izquierda, de modo que el pentagrama arrancaba **18 pt a la izquierda** de la letra y los
     * acordes: el usuario lo veía como que la partitura «no tenía alineación completa a la
     * izquierda». MuseScore imprime la tablatura en el mismo margen que el resto del contenido.
     *
     * El ancho disponible sigue siendo el de la **columna**, así que el sistema no invade el margen
     * derecho.
     */
    @Test
    fun laTablaturaSeAlineaALaIzquierdaConElTexto() {
        val options = SongPdfOptions(displayMode = SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE)
        // Ocho compases de cuatro negras: suficiente para que el sistema **llene** el ancho y se
        // pueda medir su borde derecho.
        val larga = tablature().copy(
            measures = List(8) {
                TabMeasure(slots = List(4) { TabSlot(strings = listOf(TabPluck(1, 3))) })
            }
        )
        val document = SongPdfLayout.layout(song(section("sec1")), options, tablatures = listOf(larga))
        val row = document.pages.flatMap { it.rows }.first { it.kind == PdfRowKind.TABLATURE }
        assertEquals(
            "la tablatura NO se desplaza respecto al texto: se alinea a su izquierda",
            0f,
            row.tablatureInsetPt,
            0.01f
        )
        val drawing = row.tablature!!
        // `staffLeft` es **solo** la cabecera (clave + compás + 1,25 espacios de aire), sin margen:
        // con `staffSpace = 5` y `labelWidth = 9,9` son 10,395 + 9,405 + 6,25 = **26,05 pt**.
        assertEquals(
            "el sistema arranca en la cabecera, sin margen propio",
            26.05f,
            drawing.staffLeft,
            0.05f
        )
        assertEquals(
            "el sistema llena el ancho de la columna",
            options.contentWidthPt,
            drawing.gridRight,
            0.5f
        )
    }

    @Test
    fun tablatureIsNotPrintedWhenModeExcludesIt() {
        val options = SongPdfOptions(displayMode = SongDisplayMode.CHORDS_ABOVE_TEXT)
        val document = SongPdfLayout.layout(song(section("sec1")), options, tablatures = listOf(tablature()))
        val rows = document.pages.flatMap { it.rows }
        assertTrue(rows.none { it.kind == PdfRowKind.TABLATURE })
    }

    @Test
    fun sectionTablatureIsPrintedInsideItsSection() {
        val options = SongPdfOptions(displayMode = SongDisplayMode.LYRICS_AND_TABLATURE)
        val document = SongPdfLayout.layout(
            song(section("sec1")),
            options,
            tablatures = listOf(tablature(sectionId = "sec1"))
        )
        val rows = document.pages.flatMap { it.rows }
        assertTrue(rows.any { it.kind == PdfRowKind.TABLATURE })
    }

    @Test
    fun generalTablatureHasDrawingPrimitives() {
        val options = SongPdfOptions(displayMode = SongDisplayMode.TABLATURE_ONLY)
        val document = SongPdfLayout.layout(song(section("sec1")), options, tablatures = listOf(tablature()))
        val tabRows = document.pages.flatMap { it.rows }.filter { it.kind == PdfRowKind.TABLATURE }
        assertTrue(tabRows.isNotEmpty())
        assertTrue(tabRows.all { it.tablature != null && it.heightPt > 0f })
    }

    @Test
    fun noTablaturesMeansNoTablatureRows() {
        val options = SongPdfOptions(displayMode = SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE)
        val document = SongPdfLayout.layout(song(section("sec1")), options, tablatures = emptyList())
        val rows = document.pages.flatMap { it.rows }
        assertTrue(rows.none { it.kind == PdfRowKind.TABLATURE })
    }

    @Test
    fun tablatureRowHeightIsPositive() {
        val options = SongPdfOptions(displayMode = SongDisplayMode.TABLATURE_ONLY)
        val row = SongPdfLayout.layout(song(section("sec1")), options, tablatures = listOf(tablature()))
            .pages.flatMap { it.rows }.first { it.kind == PdfRowKind.TABLATURE }
        assertTrue(row.heightPt > 0f)
    }

    // ── Justificación vertical de la página impresa (D-17) ───────────────────────────────────────

    /** Tablatura larga: muchos compases para que salgan **varios sistemas** en la misma hoja. */
    @Test
    fun `la tablatura se maqueta al ancho completo aunque el texto vaya a dos columnas`() {
        // (D-99) La música no entra en la columna: va en la banda superior a ancho completo. Lo que
        // se comprueba aquí es el ANCHO de maquetación, no el número de columnas del texto.
        val options = SongPdfOptions(displayMode = SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE)
        val largo = song(
            SongSection(
                id = "sec1",
                songId = "s1",
                sectionType = SectionType.VERSE,
                sequenceOrder = 0,
                headerTitle = "Estrofa",
                rawTextContent = "",
                lines = (0 until 80).map {
                    SongLine(lineIndex = it, lyrics = "Estrofa número $it con su letra completa")
                }
            )
        )
        val document = SongPdfLayout.layout(largo, options, tablatures = listOf(longTablature()))
        // La tablatura puede acabar en las filas de la página o en la banda superior a ancho
        // completo; en los dos casos se maqueta al ancho útil.
        val tab = (document.pages.flatMap { it.rows } + document.pages.flatMap { it.headerRows })
            .first { it.kind == PdfRowKind.TABLATURE }
        // En los dos repartos la música se maqueta al ancho útil completo: es lo que se pide.
        assertTrue(
            "la tablatura usa el ancho útil completo (no el de una columna de texto)",
            kotlin.math.abs(tab.tablature!!.gridRight - options.contentWidthPt) <= 1f
        )
    }

    @Test
    fun `si dos columnas no ahorran hojas se queda en una`() {
        // (D-99) El reparto a dos columnas solo se usa si de verdad reduce el número de hojas.
        // Nunca se mezclan los dos repartos en el mismo documento: no hay página que «vuelva atrás».
        val options = SongPdfOptions(displayMode = SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE)
        val document = SongPdfLayout.layout(
            song(section("sec1")), options, tablatures = listOf(tablature())
        )
        assertTrue("hay páginas", document.pages.isNotEmpty())
        val columnas = document.pages.map { it.columns }.distinct()
        assertEquals("todas las páginas usan el mismo reparto", 1, columnas.size)
    }

    @Test
    fun `la tablatura de una seccion se imprime arriba, antes de la letra`() {
        // (D-97) Toda la tablatura va arriba, aunque esté asociada a una sección: en el papel es un
        // bloque de música que se lee antes de cantar, no un apéndice al final de la estrofa.
        val options = SongPdfOptions(displayMode = SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE)
        val document = SongPdfLayout.layout(
            song(section("sec1")), options, tablatures = listOf(tablature(sectionId = "sec1"))
        )
        val rows = document.pages.first().rows
        val tabIndex = rows.indexOfFirst { it.kind == PdfRowKind.TABLATURE }
        val lyricIndex = rows.indexOfFirst { it.kind == PdfRowKind.LYRICS }
        assertTrue("la tablatura está en la página", tabIndex >= 0)
        assertTrue("hay letra", lyricIndex >= 0)
        assertTrue(
            "la tablatura va ANTES de la letra (tab=$tabIndex, letra=$lyricIndex)",
            tabIndex < lyricIndex
        )
    }

    private fun longTablature(measures: Int = 24) = Tablature(
        id = "t1",
        songId = "s1",
        title = "Riff",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.QUARTER,
        measures = List(measures) {
            TabMeasure(
                slots = List(4) { i -> TabSlot(strings = listOf(TabPluck(1, i))) }
            )
        }
    )

    @Test
    fun losSistemasDeTablaturaSeJustificanHastaElAltoUtil() {
        val options = SongPdfOptions(displayMode = SongDisplayMode.TABLATURE_ONLY)
        val document = SongPdfLayout.layout(
            song(section("sec1")), options, tablatures = listOf(longTablature())
        )
        val page = document.pages.first { p -> p.rows.count { it.kind == PdfRowKind.TABLATURE } >= 2 }
        val systems = page.rows.count { it.kind == PdfRowKind.TABLATURE }
        val used = page.rows.sumOf { (it.leadingGapPt + it.heightPt).toDouble() }.toFloat()
        val maxGap = options.tablatureScale.staffSpacePt *
            com.grafitto.laudate.domain.tablature.TabLayoutSettings.MAX_SYSTEM_DISTANCE_SPACES
        val systemsRows = page.rows.filter { it.kind == PdfRowKind.TABLATURE }
        // (D-57) MuseScore iguala el hueco **de pauta a pauta**: la columna queda llena hasta el alto
        // útil salvo que el hueco de **caja** tope con `maxSystemDistance`, y entonces el hueco de
        // pauta se queda en el máximo que deja ese tope.
        val staffGaps = systemsRows.zipWithNext { a, b ->
            a.heightPt + b.leadingGapPt + b.tablatureGridTopPt - a.tablatureGridBottomPt
        }
        val uniforme = staffGaps.all { kotlin.math.abs(it - staffGaps.first()) <= 0.01f }
        val tope = systemsRows.drop(1).any { it.leadingGapPt >= maxGap - 0.01f }
        assertTrue(
            "la página se justifica hasta el alto útil o el hueco de caja topa ($systems sistemas, $used pt)",
            kotlin.math.abs(used - options.contentHeightPt) <= 1f || (uniforme && tope)
        )
    }

    @Test
    fun laJustificacionIgualaElHuecoDePautaYNoElDeCaja() {
        val options = SongPdfOptions(displayMode = SongDisplayMode.TABLATURE_ONLY)
        val document = SongPdfLayout.layout(
            song(section("sec1")), options, tablatures = listOf(longTablature())
        )
        val page = document.pages.first { p -> p.rows.count { it.kind == PdfRowKind.TABLATURE } >= 2 }
        val systems = page.rows.filter { it.kind == PdfRowKind.TABLATURE }
        // El primer sistema no lleva aire delante; los demás sí.
        assertEquals(0f, systems.first().leadingGapPt, 0.01f)
        // (D-57) Lo que se iguala es el hueco **de pauta a pauta**, no el de caja: el sistema que
        // cuelga algo por debajo de su última cuerda arrastra un hueco de caja más corto. En el
        // `salmo2.pdf` los huecos de pauta son 18,38 / 18,34 / 18,34 espacios y los de caja
        // 9,2 / 15,2 / 15,2.
        val staffGaps = systems.zipWithNext { a, b ->
            a.heightPt + b.leadingGapPt + b.tablatureGridTopPt - a.tablatureGridBottomPt
        }
        assertTrue(
            "el hueco de pauta entre sistemas es uniforme ($staffGaps)",
            staffGaps.all { kotlin.math.abs(it - staffGaps.first()) <= 0.01f }
        )
        // El alto de cada sistema es el de la rejilla: la justificación no lo toca. (D-55) El
        // **primero** es más bajo porque no rotula su compás: solo los sistemas que abren con número
        // reservan el aire de `measureNumberPosAbove` (2 espacios de base + la tinta del dígito).
        assertTrue(
            "los sistemas que rotulan su compás miden todos lo mismo",
            systems.drop(1).all { it.heightPt == systems[1].heightPt }
        )
        assertTrue(
            "el primer sistema no rotula y por eso es más bajo " +
                "(${systems.first().heightPt} vs ${systems[1].heightPt})",
            systems.first().heightPt < systems[1].heightPt
        )
    }

    @Test
    fun laJustificacionNoSuperaElMaximoDeMuseScore() {
        // Con **pocos** sistemas en la hoja el sobrante es enorme, pero MuseScore topa el hueco en
        // `styleDef(maxSystemDistance, 15.0_sp)` (styledef.cpp:59). Se prueban varios largos para
        // cubrir hojas con 2, 3 y 4 sistemas.
        val options = SongPdfOptions(displayMode = SongDisplayMode.TABLATURE_ONLY)
        val maxGap = options.tablatureScale.staffSpacePt *
            com.grafitto.laudate.domain.tablature.TabLayoutSettings.MAX_SYSTEM_DISTANCE_SPACES
        var algunHuecoTope = false
        (4..30).forEach { measures ->
            val document = SongPdfLayout.layout(
                song(section("sec1")), options, tablatures = listOf(longTablature(measures))
            )
            document.pages.forEach { page ->
                val systems = page.rows.filter { it.kind == PdfRowKind.TABLATURE }
                systems.drop(1).forEach { system ->
                    assertTrue(
                        "ningún hueco pasa de 15 espacios ($measures compases)",
                        system.leadingGapPt <= maxGap + 0.01f
                    )
                    if (system.leadingGapPt >= maxGap - 0.01f) algunHuecoTope = true
                }
            }
        }
        assertTrue("alguna hoja tiene que llegar al tope de 15 espacios", algunHuecoTope)
    }

    @Test
    fun laJustificacionSoloEstiraLosHuecosEntreSistemas() {
        // El interlineado de la letra y de los acordes ya viene medido por la tipografía: la
        // justificación vertical solo puede tocar el hueco **entre dos sistemas consecutivos**.
        val options = SongPdfOptions(displayMode = SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE)
        val document = SongPdfLayout.layout(
            song(section("sec1")), options, tablatures = listOf(longTablature())
        )
        val base = options.tablatureScale.systemGapPt
        document.pages.forEach { page ->
            page.rows.forEachIndexed { index, row ->
                if (row.leadingGapPt >= base - 0.01f) {
                    assertTrue(
                        "solo un sistema precedido de otro sistema lleva el aire grande",
                        row.kind == PdfRowKind.TABLATURE &&
                            index > 0 &&
                            page.rows[index - 1].kind == PdfRowKind.TABLATURE
                    )
                }
            }
        }
    }

    // ── Salto de página (`Ctrl`+`Return` de MuseScore) ──────────────────────────────────────────

    /**
     * Tablatura **de sección** de [count] compases de negra, con salto de página en [pageBreakAt].
     *
     * Va dentro de la sección (`sectionId`) y no como tablatura general: la general se emite **antes**
     * de la primera sección, así que en un documento a dos columnas acaba en la banda de cabecera y
     * no se pagina con el resto.
     */
    private fun longTablature(count: Int, pageBreakAt: Int? = null) = Tablature(
        id = "t1",
        songId = "s1",
        title = "Riff",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.QUARTER,
        sectionId = "sec1",
        measures = List(count) { index ->
            TabMeasure(
                slots = listOf(TabSlot(strings = listOf(TabPluck(1, index % 5))), TabSlot()),
                forcePageBreak = index == pageBreakAt,
                forceSystemBreak = index == pageBreakAt
            )
        }
    )

    @Test
    fun elSaltoDePaginaMarcaLaFilaQueAbreHoja() {
        val options = SongPdfOptions(displayMode = SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE)
        val document = SongPdfLayout.layout(
            song(section("sec1")),
            options,
            tablatures = listOf(longTablature(count = 60, pageBreakAt = 5))
        )
        val tabRows = document.pages.flatMap { it.rows }.filter { it.kind == PdfRowKind.TABLATURE }

        assertTrue("deberia haber varios sistemas", tabRows.size >= 2)
        assertFalse("el primer sistema no abre hoja", tabRows.first().pageBreakBefore)
        assertTrue(
            "el sistema que lleva el salto de pagina debe marcarse",
            tabRows.any { it.pageBreakBefore }
        )
    }

    @Test
    fun sinSaltoDePaginaNingunaFilaAbreHoja() {
        val options = SongPdfOptions(displayMode = SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE)
        val document = SongPdfLayout.layout(
            song(section("sec1")),
            options,
            tablatures = listOf(longTablature(count = 60))
        )
        val tabRows = document.pages.flatMap { it.rows }.filter { it.kind == PdfRowKind.TABLATURE }

        assertTrue(tabRows.none { it.pageBreakBefore })
    }

    @Test
    fun elSaltoDePaginaEmpiezaHojaNueva() {
        val options = SongPdfOptions(displayMode = SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE)
        val sinSalto = SongPdfLayout.layout(
            song(section("sec1")),
            options,
            tablatures = listOf(longTablature(count = 60))
        )
        val conSalto = SongPdfLayout.layout(
            song(section("sec1")),
            options,
            tablatures = listOf(longTablature(count = 60, pageBreakAt = 5))
        )

        assertTrue(
            "el salto de pagina debe anadir al menos una hoja",
            conSalto.pages.size > sinSalto.pages.size
        )
        // La hoja nueva **empieza** por el sistema marcado.
        val pageWithBreak = conSalto.pages.first { page ->
            page.rows.any { it.kind == PdfRowKind.TABLATURE && it.pageBreakBefore }
        }
        val firstTab = pageWithBreak.rows.first { it.kind == PdfRowKind.TABLATURE }
        assertTrue("la hoja debe empezar por el sistema del salto", firstTab.pageBreakBefore)
    }
}
