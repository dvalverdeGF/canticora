package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.liturgy.CategoryLabelPolicy
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.song.Song

/**
 * Cuenta cuántas canciones tiene etiquetada cada categoría (RF-103).
 *
 * La gestión de categorías muestra el número de cantos entre paréntesis, para que el usuario
 * sepa qué está borrando antes de hacerlo.
 *
 * El canto guarda sus etiquetas como **texto** (no hay tabla de unión), así que la
 * correspondencia se hace por **nombre normalizado** (minúsculas y sin acentos): «Navidad» y
 * «navidad» cuentan como la misma categoría.
 *
 * Es **Kotlin puro** (sin Android ni Room): recibe las listas ya cargadas y devuelve un mapa
 * `id de categoría → nº de canciones`. Un canto que repita la misma etiqueta cuenta una sola vez.
 */
class CountSongsByCategoryUseCase {

    operator fun invoke(
        songs: List<Song>,
        categories: List<LiturgicalCategory>
    ): Map<String, Int> {
        if (songs.isEmpty() || categories.isEmpty()) return emptyMap()

        val idByName = categories.associate { CategoryLabelPolicy.normalize(it.name) to it.id }
        val counts = mutableMapOf<String, Int>()

        songs.forEach { song ->
            val labels = (song.defaultLiturgicalMoments + song.defaultLiturgicalTimes)
                .map { CategoryLabelPolicy.normalize(it) }
                .filter { it.isNotEmpty() }
                .toSet()

            labels.forEach { label ->
                idByName[label]?.let { id -> counts[id] = (counts[id] ?: 0) + 1 }
            }
        }

        return counts
    }
}
