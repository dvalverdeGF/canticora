package com.grafitto.laudate.domain.manual

import com.grafitto.laudate.domain.model.manual.ManualBlock
import com.grafitto.laudate.domain.model.manual.ManualChapter
import com.grafitto.laudate.domain.model.manual.ManualChapterIcon
import com.grafitto.laudate.domain.util.diacriticsFold

/**
 * Coincidencia encontrada por el buscador interno del manual.
 *
 * Apunta a una **sección** concreta (no a un capítulo completo) para poder saltar directamente
 * a ella desde los resultados.
 */
data class ManualSearchHit(
    val chapterId: String,
    val chapterTitle: String,
    val chapterIcon: ManualChapterIcon,
    val sectionId: String,
    val sectionTitle: String,
    /** Fragmento de texto donde apareció la coincidencia, para dar contexto. */
    val snippet: String
)

/**
 * Búsqueda de texto sobre el manual de usuario (RF-107 aplicado a la propia ayuda).
 *
 * Es **Kotlin puro**: recibe los capítulos ya cargados y devuelve las secciones que cumplen
 * todos los términos buscados. La comparación es **sin acentos y sin distinción de mayúsculas**
 * (buscar «tonalidad» encuentra «Tonalidad», y «acorde» encuentra «Acorde»), y los términos se
 * combinan con **Y lógico** (todos deben aparecer en la misma sección).
 */
object ManualSearch {

    /** Longitud máxima del fragmento mostrado como contexto de la coincidencia. */
    private const val SNIPPET_LENGTH = 160

    /**
     * Devuelve las secciones que contienen **todos** los términos de [query].
     * Si [query] está en blanco, no devuelve resultados (el llamador muestra el índice).
     */
    fun search(chapters: List<ManualChapter>, query: String): List<ManualSearchHit> {
        val tokens = tokenize(query)
        if (tokens.isEmpty()) return emptyList()

        val hits = mutableListOf<ManualSearchHit>()
        chapters.forEach { chapter ->
            chapter.sections.forEach { section ->
                val candidates = sectionTexts(section)
                val haystack = candidates.joinToString("\n") { normalize(it) }
                if (tokens.all { it in haystack }) {
                    hits += ManualSearchHit(
                        chapterId = chapter.id,
                        chapterTitle = chapter.title,
                        chapterIcon = chapter.icon,
                        sectionId = section.id,
                        sectionTitle = section.title,
                        snippet = snippetFor(candidates, tokens.first())
                    )
                }
            }
        }
        return hits
    }

    /** Textos individuales de una sección (título y contenido), sin normalizar. */
    private fun sectionTexts(section: com.grafitto.laudate.domain.model.manual.ManualSection): List<String> =
        buildList {
            add(section.title)
            section.blocks.forEach { block ->
                when (block) {
                    is ManualBlock.Paragraph -> add(block.text)
                    is ManualBlock.Steps -> addAll(block.items)
                    is ManualBlock.Note -> add(block.text)
                    is ManualBlock.Reference -> {
                        block.caption?.let { add(it) }
                        block.rows.forEach { row -> add(row.joinToString(" · ")) }
                    }
                }
            }
        }

    /** Elige como fragmento el primer texto que contiene el término, recortado a [SNIPPET_LENGTH]. */
    private fun snippetFor(candidates: List<String>, token: String): String {
        val match = candidates.firstOrNull { token in normalize(it) } ?: candidates.firstOrNull().orEmpty()
        val clean = match.replace('\n', ' ').trim()
        return if (clean.length <= SNIPPET_LENGTH) clean else clean.take(SNIPPET_LENGTH).trimEnd() + "…"
    }

    internal fun tokenize(query: String): List<String> =
        normalize(query)
            .split(' ', '\n', '\t', ',', ';', '.')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** Minúsculas y sin diacríticos, para que la búsqueda no dependa de tildes ni mayúsculas. */
    internal fun normalize(text: String): String = diacriticsFold(text)
}
