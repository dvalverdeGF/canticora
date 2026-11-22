package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.export.SongPdfLayout
import com.grafitto.laudate.domain.export.SongPdfOptions
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.song.SongLine
import com.grafitto.laudate.domain.model.song.SongSection
import com.grafitto.laudate.domain.model.song.SectionType
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **C.3 · Escala de notación y paginación** (roadmap §4, arregla F-05). La tablatura impresa usa una
 * **escala de notación** ([TabNotationScale], en espacios de pentagrama como SMuFL) y el paginador de
 * filas reparte los sistemas en páginas **sin partir ninguno**.
 */
class TabNotationScaleTest {

    private fun tab(measureCount: Int): Tablature = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
        measures = List(measureCount) {
            TabMeasure(slots = List(4) { TabSlot(strings = listOf(TabPluck(1, it))) })
        }
    )

    // ── Escala de notación ──────────────────────────────────────────────────────────────────────

    @Test
    fun laEscalaDerivaTodasLasMedidasDeUnaSolaUnidad() {
        val scale = TabNotationScale(staffSpacePt = 8f)
        // La separación de cuerdas es 1,5 espacios (como el `lineDistance 1.5` del fichero).
        assertEquals("la separación de cuerdas es 1,5 espacios", 8f * 1.5f, scale.stringSpacingPt, 0.001f)
        // El ancho de una negra es 3,5 espacios (`DEFAULT_QUARTER_NOTE_SPACE` de MuseScore).
        assertEquals("el ancho de negra es 3,5 espacios", 8f * 3.5f, scale.cellWidthPt, 0.02f)
        assertEquals("la cabecera también", 8f * 3.6f, scale.headerWidthPt, 0.001f)
    }

    @Test
    fun unaEscalaMayorProduceUnaTablaturaMasAlta() {
        val pequeña = TablatureSystemsLayout.layout(
            tab(2), maxWidth = 500f, settings = settingsFor(TabNotationScale(staffSpacePt = 5f))
        ).sumOf { it.height.toDouble() }.toFloat()
        val grande = TablatureSystemsLayout.layout(
            tab(2), maxWidth = 500f, settings = settingsFor(TabNotationScale(staffSpacePt = 9f))
        ).sumOf { it.height.toDouble() }.toFloat()
        assertTrue("una notación mayor ocupa más alto", grande > pequeña)
    }

    private fun settingsFor(scale: TabNotationScale) = TabLayoutSettings(
        cellWidth = scale.cellWidthPt,
        stringSpacing = scale.stringSpacingPt,
        labelWidth = scale.headerWidthPt * 0.55f,
        padding = scale.headerWidthPt * 0.18f,
        systemGap = scale.systemGapPt
    )

    // ── D-7 · Proporciones del PDF en el pintor ───────────────────────────────────────────────────

    @Test
    fun laProporcionDeColumnaEsLaDelPdf() {
        // La razón `ancho de negra / separación de cuerdas` de MuseScore es 3,5 / 1,5 = 2,333.
        // Antes el canvas usaba cellWidth=22 contra stringSpacing=20 (razón 1,1): 2,1× más estrecho.
        val scale = TabNotationScale(staffSpacePt = 20f / 1.5f) // separación de cuerdas = 20 px
        val s = scale.toLayoutSettings()
        assertEquals("separación de cuerdas", 20f, s.stringSpacing, 0.001f)
        assertEquals("la razón de columna es la de MuseScore (2,333)", 2.333f, s.cellWidth / s.stringSpacing, 0.01f)
    }

    @Test
    fun toLayoutSettingsConservaElPentagramaYLaEscalaDeLasCuerdas() {
        val scale = TabNotationScale(staffSpacePt = 8f)
        val s = scale.toLayoutSettings(staff = com.grafitto.laudate.domain.tablature.TabStaffOptions(showClef = false))
        assertEquals("la separación de cuerdas es la de la escala", scale.stringSpacingPt, s.stringSpacing, 0.001f)
        assertEquals("el ancho de negra es el de la escala", scale.cellWidthPt, s.cellWidth, 0.001f)
        assertTrue("se conserva la configuración del pentagrama", !s.staff.showClef)
    }

    // ── Paginación (nunca parte un sistema) ─────────────────────────────────────────────────────

    @Test
    fun cadaSistemaDeTablaturaEsUnaFilaDelPdf() {
        val song = Song(
            id = "s1", title = "Canto", originalKey = "C",
            sections = listOf(
                SongSection(
                    id = "sec1", songId = "s1", sectionType = SectionType.VERSE, sequenceOrder = 0,
                    headerTitle = null, rawTextContent = "Aleluya",
                    lines = listOf(SongLine(lineIndex = 0, lyrics = "Aleluya"))
                )
            )
        )
        val options = SongPdfOptions(displayMode = SongDisplayMode.TABLATURE_ONLY)
        val doc = SongPdfLayout.layout(song, options, tablatures = listOf(tab(24)))
        val rows = doc.pages.flatMap { it.rows }.filter { it.kind == com.grafitto.laudate.domain.export.PdfRowKind.TABLATURE }
        // La tablatura larga se reparte en **varios sistemas** (filas), uno por sistema: el paginador
        // de filas nunca puede partir uno por la mitad.
        assertTrue("la tablatura larga ocupa varios sistemas (fueron ${rows.size})", rows.size > 1)
        assertTrue("todas las filas de tablatura llevan su dibujo (el sistema completo)",
            rows.all { it.tablature != null })
    }

    @Test
    fun unSistemaMasAltoQueLaPaginaNoSeParte() {
        // Aunque un sistema pase de la altura útil, sigue siendo **una** fila: no se corta.
        val song = Song(
            id = "s1", title = "Canto", originalKey = "C",
            sections = listOf(
                SongSection(
                    id = "sec1", songId = "s1", sectionType = SectionType.VERSE, sequenceOrder = 0,
                    headerTitle = null, rawTextContent = "Aleluya",
                    lines = listOf(SongLine(lineIndex = 0, lyrics = "Aleluya"))
                )
            )
        )
        val grande = SongPdfOptions(
            displayMode = SongDisplayMode.TABLATURE_ONLY,
            tablatureScale = TabNotationScale(staffSpacePt = 40f)
        )
        val doc = SongPdfLayout.layout(song, grande, tablatures = listOf(tab(1)))
        val rows = doc.pages.flatMap { it.rows }.filter { it.kind == com.grafitto.laudate.domain.export.PdfRowKind.TABLATURE }
        assertTrue(rows.isNotEmpty())
        assertTrue("cada sistema va entero en una fila", rows.all { it.tablature != null })
    }
}
