package com.grafitto.laudate.domain.manual

import com.grafitto.laudate.domain.model.manual.ManualBlock
import com.grafitto.laudate.domain.model.manual.ManualChapter
import com.grafitto.laudate.domain.model.manual.ManualChapterIcon
import com.grafitto.laudate.domain.model.manual.ManualSection
import com.grafitto.laudate.domain.model.manual.UserManualContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del manual de usuario: integridad del contenido y motor de búsqueda interno.
 *
 * El manual es contenido embarcado y navegable, así que un `id` duplicado o una búsqueda que
 * no encuentra lo evidente son fallos de producto, no solo de código.
 */
class ManualSearchTest {

    // ── Integridad del contenido ────────────────────────────────────────────

    @Test
    fun `el manual tiene capitulos y secciones`() {
        assertTrue(UserManualContent.chapters.size >= 10)
        UserManualContent.chapters.forEach { chapter ->
            assertTrue("Capítulo sin secciones: ${chapter.id}", chapter.sections.isNotEmpty())
        }
    }

    @Test
    fun `los identificadores de capitulos y secciones son unicos`() {
        val chapterIds = UserManualContent.chapters.map { it.id }
        assertEquals(chapterIds.size, chapterIds.toSet().size)

        val sectionIds = UserManualContent.chapters.flatMap { it.sections }.map { it.id }
        assertEquals(sectionIds.size, sectionIds.toSet().size)
    }

    @Test
    fun `cada seccion tiene titulo y al menos un bloque`() {
        UserManualContent.chapters.flatMap { it.sections }.forEach { section ->
            assertTrue("Sección sin título: ${section.id}", section.title.isNotBlank())
            assertTrue("Sección sin contenido: ${section.id}", section.blocks.isNotEmpty())
        }
    }

    @Test
    fun `la version y la fecha del manual estan declaradas`() {
        assertTrue(UserManualContent.VERSION.isNotBlank())
        assertTrue(Regex("""\d{4}-\d{2}-\d{2}""").matches(UserManualContent.LAST_UPDATED))
    }

    // ── Búsqueda ────────────────────────────────────────────────────────────

    private val chapters = UserManualContent.chapters

    @Test
    fun `buscar sin texto no devuelve resultados`() {
        assertTrue(ManualSearch.search(chapters, "").isEmpty())
        assertTrue(ManualSearch.search(chapters, "   ").isEmpty())
    }

    @Test
    fun `encuentra la seccion de transposicion por palabra clave`() {
        val hits = ManualSearch.search(chapters, "transposición")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.any { it.chapterId == "transposicion" })
    }

    @Test
    fun `la busqueda ignora mayusculas y tildes`() {
        val withAccent = ManualSearch.search(chapters, "tonalidad")
        val upper = ManualSearch.search(chapters, "TONALIDAD")
        assertEquals(withAccent.map { it.sectionId }, upper.map { it.sectionId })
        assertTrue(withAccent.isNotEmpty())
    }

    @Test
    fun `los terminos se combinan con Y logico`() {
        val single = ManualSearch.search(chapters, "capo")
        val combined = ManualSearch.search(chapters, "capo cejilla")
        // El término combinado no puede devolver más secciones que el más restrictivo.
        assertTrue(combined.size <= single.size)
        assertTrue(combined.all { hit -> hit.sectionId in single.map { it.sectionId } })
    }

    @Test
    fun `encuentra el apendice ChordPro por una directiva`() {
        val hits = ManualSearch.search(chapters, "start_of_chorus")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.any { it.chapterId == "apendice-chordpro" })
    }

    @Test
    fun `una busqueda sin coincidencias devuelve lista vacia`() {
        assertTrue(ManualSearch.search(chapters, "xyzzyplugh").isEmpty())
    }

    @Test
    fun `el fragmento de contexto no es vacio y se recorta`() {
        val hits = ManualSearch.search(chapters, "alabaré")
        assertTrue(hits.isNotEmpty())
        hits.forEach { hit ->
            assertTrue(hit.snippet.isNotBlank())
            assertTrue(hit.snippet.length <= 200)
        }
    }

    @Test
    fun `el texto buscable de una seccion incluye pasos y tablas`() {
        val section = ManualSection(
            id = "test/seccion",
            title = "Título de prueba",
            blocks = listOf(
                ManualBlock.Paragraph("Un párrafo de prueba."),
                ManualBlock.Steps(listOf("Primer paso", "Segundo paso")),
                ManualBlock.Note("Un consejo."),
                ManualBlock.Reference(
                    caption = "Tabla",
                    headers = listOf("A", "B"),
                    rows = listOf(listOf("uno", "dos"))
                )
            )
        )

        val searchable = section.searchableText
        listOf("Título de prueba", "Un párrafo", "Primer paso", "Un consejo", "uno dos").forEach {
            assertTrue("Falta '$it' en searchableText", searchable.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun `busca dentro de tablas de referencia`() {
        val chapters = listOf(
            ManualChapter(
                id = "cap",
                title = "Capítulo",
                icon = ManualChapterIcon.START,
                sections = listOf(
                    ManualSection(
                        id = "cap/tabla",
                        title = "Tabla de referencia",
                        blocks = listOf(
                            ManualBlock.Reference(
                                headers = listOf("Directiva"),
                                rows = listOf(listOf("{tempo: 92 Andante}"))
                            )
                        )
                    )
                )
            )
        )

        val hits = ManualSearch.search(chapters, "andante")
        assertTrue(hits.isNotEmpty())
        assertEquals("cap/tabla", hits.first().sectionId)
    }

    @Test
    fun `el normalizador elimina diacriticos y pone minusculas`() {
        assertEquals("corazon", ManualSearch.normalize("Corazón"))
        assertEquals("cancion", ManualSearch.normalize("Canción"))
        assertEquals("adviento", ManualSearch.normalize("Adviento"))
    }

    @Test
    fun `tokeniza separando por espacios y signos`() {
        assertEquals(listOf("do", "re", "mi"), ManualSearch.tokenize("Do, Re; Mi."))
        assertTrue(ManualSearch.tokenize("   ").isEmpty())
    }
}
