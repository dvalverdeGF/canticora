package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongSearchCriteria
import com.grafitto.laudate.domain.model.song.SongSortOrder
import com.grafitto.laudate.domain.util.diacriticsFold

/**
 * Filtra y ordena una colección de canciones según [SongSearchCriteria] (RF-107).
 *
 * Es **Kotlin puro** (sin Android, sin Room): recibe la lista ya cargada y devuelve el
 * subconjunto que cumple todos los filtros. Se mantiene como lógica pura y testeable para
 * poder validar la semántica de la búsqueda sin instrumentación.
 *
 * El filtrado se hace en memoria sobre listas pequeñas/medianas (~1.000 cantos): suficiente
 * para mantener la latencia muy por debajo de los 50 ms de RNF-102. Si el catálogo creciera
 * mucho, la misma semántica puede delegarse a la consulta SQL sin cambiar la UI.
 */
class SearchSongsUseCase {

    operator fun invoke(songs: List<Song>, criteria: SongSearchCriteria): List<Song> {
        if (criteria.isEmpty) return sortSongs(songs, criteria.sort)

        val queryTokens = tokenize(criteria.query)
        val numberQuery = criteria.query.trim().takeIf { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }

        val matched = songs.filter { song ->
            matchesFilters(song, criteria) && matchesQuery(song, queryTokens)
        }

        // El número exacto manda: si se busca "402", el canto 402 va primero aunque otro
        // contenga 402 en la letra. El resto respeta el criterio de orden elegido.
        val (exactNumber, others) = matched.partition { song ->
            numberQuery != null && song.number?.trim() == numberQuery
        }
        return exactNumber.sortedBy { it.number } + sortSongs(others, criteria.sort)
    }

    private fun matchesFilters(song: Song, criteria: SongSearchCriteria): Boolean {
        if (criteria.onlyFavorites && !song.isFavorite) return false

        // «Sin categoría» = el canto no tiene ninguna etiqueta (ni momento, ni tiempo).
        if (criteria.onlyUncategorized &&
            (song.defaultLiturgicalMoments.isNotEmpty() || song.defaultLiturgicalTimes.isNotEmpty())
        ) {
            return false
        }

        if (criteria.momentNames.isNotEmpty()) {
            val moments = song.defaultLiturgicalMoments.map { normalize(it) }.toSet()
            if (criteria.momentNames.none { normalize(it) in moments }) return false
        }

        if (criteria.liturgicalTimes.isNotEmpty()) {
            // Se busca en la **unión** de momentos y tiempos: el canto guarda las etiquetas como
            // texto y no distingue su naturaleza. Las versiones antiguas del editor guardaban los
            // tiempos litúrgicos en el campo de momentos, así que sin esta unión un canto con
            // «Navidad» no aparecería al filtrar por ese tiempo.
            val all = (song.defaultLiturgicalMoments + song.defaultLiturgicalTimes)
                .map { normalize(it) }
                .toSet()
            if (criteria.liturgicalTimes.none { normalize(it) in all }) return false
        }

        // Las categorías adicionales se buscan en la unión de momentos y tiempos: el canto
        // guarda las etiquetas como texto y no distingue de qué tipo son.
        if (criteria.additionalCategories.isNotEmpty()) {
            val all = (song.defaultLiturgicalMoments + song.defaultLiturgicalTimes)
                .map { normalize(it) }
                .toSet()
            if (criteria.additionalCategories.none { normalize(it) in all }) return false
        }

        if (criteria.keys.isNotEmpty()) {
            val key = normalize(song.originalKey)
            if (criteria.keys.none { normalize(it) == key }) return false
        }

        return true
    }

    private fun matchesQuery(song: Song, tokens: List<String>): Boolean {
        if (tokens.isEmpty()) return true
        val haystack = buildHaystack(song)
        return tokens.all { it in haystack }
    }

    /**
     * Construye el texto indexable del canto: título, autor, número, momentos/tiempos y letra.
     * Todo normalizado (minúsculas y sin acentos) para que "corazon" encuentre "Corazón".
     */
    private fun buildHaystack(song: Song): String {
        val builder = StringBuilder()
        builder.append(song.title).append('\n')
        song.number?.let { builder.append(it).append('\n') }
        song.author?.let { builder.append(it).append('\n') }
        song.rhythm?.let { builder.append(it).append('\n') }
        song.defaultLiturgicalMoments.forEach { builder.append(it).append('\n') }
        song.defaultLiturgicalTimes.forEach { builder.append(it).append('\n') }
        song.sections.forEach { section ->
            builder.append(section.rawTextContent).append('\n')
            section.label?.let { builder.append(it).append('\n') }
        }
        return normalize(builder.toString())
    }

    private fun sortSongs(
        songs: List<Song>,
        sort: SongSortOrder
    ): List<Song> = when (sort) {
        SongSortOrder.TITLE -> songs.sortedBy { normalize(it.title) }
        SongSortOrder.NUMBER -> songs.sortedWith(
            compareBy<Song> { it.number == null }.thenBy { it.number?.padStart(6, '0') ?: it.title }
        )
        SongSortOrder.RECENT -> songs.sortedByDescending { it.updatedAt }
    }

    private fun tokenize(query: String): List<String> =
        normalize(query).split(' ', '\n', '\t', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun normalize(text: String): String = diacriticsFold(text)
}
