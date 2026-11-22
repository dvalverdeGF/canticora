package com.grafitto.laudate.domain.export

import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.song.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la maquetación del **PDF del repertorio completo**.
 *
 * Requisitos que se validan aquí:
 *  - Portada con el índice de cantos.
 *  - **Un canto por página** (nunca se encadenan dos cantos en la misma página).
 *  - Se respeta el tono propio de la celebración (override) para cada canto.
 */
class CelebrationPdfLayoutTest {

    private fun song(id: String, title: String, key: String = "C"): Song {
        val parsed = com.grafitto.laudate.domain.parser.ChordProParser.parse(
            chordProContent = "{title: $title}\n[C]Letra del canto $title",
            songId = id,
            fallbackTitle = title,
            fallbackKey = key
        )
        return parsed
    }

    private fun celebration(itemCount: Int): Celebration = Celebration(
        id = "c1",
        name = "Misa Dominical",
        celebrationType = "Misa",
        dateTimestamp = 1_700_000_000_000L,
        location = "Parroquia San Juan",
        description = "Con el coro parroquial",
        items = (0 until itemCount).map { index ->
            com.grafitto.laudate.domain.model.celebration.CelebrationItem(
                id = "item$index",
                celebrationId = "c1",
                songId = "s$index",
                liturgicalMoment = if (index == 0) "Entrada" else "Comunión",
                displayOrder = index
            )
        }
    )

    private fun songs(count: Int): List<CelebrationPdfSong> = (0 until count).map { index ->
        CelebrationPdfSong(
            song = song("s$index", "Canto $index"),
            moment = if (index == 0) "Entrada" else "Comunión"
        )
    }

    @Test
    fun `la portada lleva el nombre y el indice de cantos`() {
        val document = CelebrationPdfLayout.layout(celebration(3), songs(3))
        val cover = document.pages.first()
        assertTrue(cover.rows.any { it.kind == PdfRowKind.TITLE && it.text == "Misa Dominical" })
        assertTrue(cover.rows.any { it.text == "ÍNDICE DE CANTOS" })
        assertTrue(cover.rows.count { it.text.startsWith("1.") || it.text.startsWith("2.") || it.text.startsWith("3.") } == 3)
    }

    @Test
    fun `la portada incluye tipo fecha y lugar`() {
        val cover = CelebrationPdfLayout.layout(celebration(1), songs(1)).pages.first()
        assertTrue(cover.rows.any { it.text == "MISA" })
        assertTrue(cover.rows.any { it.text == "Parroquia San Juan" })
    }

    @Test
    fun `cada canto empieza en una pagina nueva`() {
        val document = CelebrationPdfLayout.layout(celebration(4), songs(4))
        // 1 portada + 4 cantos = 5 páginas (cada canto cabe holgadamente en una).
        assertEquals(5, document.pages.size)
    }

    @Test
    fun `el indice de cada canto aparece en la pagina del canto`() {
        val document = CelebrationPdfLayout.layout(celebration(2), songs(2))
        // La página 2 corresponde al primer canto y lleva su momento.
        assertTrue(document.pages[1].rows.any { it.kind == PdfRowKind.SECTION_HEADER && it.text == "ENTRADA" })
        assertTrue(document.pages[2].rows.any { it.kind == PdfRowKind.SECTION_HEADER && it.text == "COMUNIÓN" })
    }

    @Test
    fun `ningun canto comparte pagina con otro`() {
        val document = CelebrationPdfLayout.layout(celebration(3), songs(3))
        // Se reconoce el canto de cada página por su título.
        val pagesWithTitles = document.pages.drop(1).map { page ->
            page.rows.firstOrNull { it.kind == PdfRowKind.TITLE }?.text
        }
        assertEquals(pagesWithTitles.size, pagesWithTitles.filterNotNull().size)
    }

    @Test
    fun `el tono del repertorio prevalece sobre el original`() {
        val entry = CelebrationPdfSong(
            song = song("s1", "Aleluya", key = "C"),
            moment = "Aleluya",
            overrideKey = "D"
        )
        val document = CelebrationPdfLayout.layout(celebration(1), listOf(entry))
        // El canto se maqueta con el tono de la celebración.
        val meta = document.pages.drop(1).flatMap { it.rows }.first { it.kind == PdfRowKind.METADATA }
        assertTrue(meta.text.contains("Tono: D"))
    }

    @Test
    fun `un repertorio vacio genera solo la portada`() {
        val document = CelebrationPdfLayout.layout(celebration(0), emptyList())
        assertEquals(1, document.pages.size)
        assertTrue(document.pages.first().rows.any { it.text.contains("no tiene cantos asignados") })
    }

    @Test
    fun `la portada no lleva banda y las demas si`() {
        val document = CelebrationPdfLayout.layout(celebration(2), songs(2))
        assertTrue(document.pages.first().isTitlePage)
        assertEquals(null, document.pages.first().header)
        assertTrue(document.pages.drop(1).all { it.header != null })
    }

    @Test
    fun `cada pagina de canto lleva el titulo del canto en la banda`() {
        val document = CelebrationPdfLayout.layout(celebration(2), songs(2))
        assertEquals("Canto 0", document.pages[1].header)
        assertEquals("Canto 1", document.pages[2].header)
    }

    @Test
    fun `cada canto imprime su letra y no solo el titulo`() {
        // Regresion: el PDF salia con los titulos pero sin la letra.
        val document = CelebrationPdfLayout.layout(celebration(2), songs(2))
        document.pages.drop(1).forEachIndexed { index, page ->
            val lyrics = page.rows.filter { it.kind == PdfRowKind.CHORDS_OVER_LYRICS }
            assertTrue("el canto $index debe traer letra", lyrics.isNotEmpty())
            assertTrue(lyrics.first().text.contains("Letra del canto"))
        }
    }

    @Test
    fun `la letra de cada canto va centrada`() {
        val document = CelebrationPdfLayout.layout(celebration(1), songs(1))
        val row = document.pages[1].rows.first { it.kind == PdfRowKind.CHORDS_OVER_LYRICS }
        assertTrue(row.centered)
    }

    @Test
    fun `un estribillo se destaca con mas cuerpo`() {
        val parsed = com.grafitto.laudate.domain.parser.ChordProParser.parse(
            chordProContent = "{title: Gloria}\n{start_of_chorus}\nGloria a Dios\n{end_of_chorus}",
            songId = "s1",
            fallbackTitle = "Gloria"
        )
        val document = CelebrationPdfLayout.layout(
            celebration(1),
            listOf(CelebrationPdfSong(song = parsed, moment = "Gloria"))
        )
        val chorusRow = document.pages.drop(1).flatMap { it.rows }
            .first { it.kind == PdfRowKind.LYRICS && it.emphasis }
        assertTrue(chorusRow.fontSizePt > SongPdfOptions().lyricsFontSizePt)
    }

    @Test
    fun `la numeracion de paginas es coherente`() {
        val document = CelebrationPdfLayout.layout(celebration(3), songs(3))
        assertTrue(document.pages.all { it.totalPages == document.pages.size })
    }
}
