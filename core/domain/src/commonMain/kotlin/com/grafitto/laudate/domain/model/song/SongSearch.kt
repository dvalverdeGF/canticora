package com.grafitto.laudate.domain.model.song

/**
 * Criterios de búsqueda sobre el catálogo (RF-107).
 *
 * Es un modelo de Dominio puro: no depende de Room ni de la UI. Los filtros son
 * **acumulativos** (se aplican todos a la vez): texto **y** momento **y** tiempo **y**
 * tonalidad **y** favoritos.
 *
 * @property query Texto libre. Se tokeniza por espacios y cada token debe aparecer en el
 *   título, el autor, el número, los momentos/tiempos o la letra del canto. Si el texto es
 *   puramente numérico y coincide con un [Song.number], esa canción sube al principio.
 * @property momentNames Momentos litúrgicos seleccionados; se comparan con
 *   [Song.defaultLiturgicalMoments].
 * @property liturgicalTimes Tiempos litúrgicos seleccionados; se comparan con
 *   [Song.defaultLiturgicalTimes].
 * @property additionalCategories Categorías adicionales (ni momento ni tiempo) seleccionadas.
 *   Se comparan contra la **unión** de [Song.defaultLiturgicalMoments] y
 *   [Song.defaultLiturgicalTimes], porque el canto guarda las etiquetas como texto y no
 *   distingue su naturaleza.
 * @property keys Tonalidades originales seleccionadas (p. ej. "Do", "C", "Mim").
 * @property onlyFavorites Si es `true`, solo se devuelven cantos favoritos.
 * @property onlyUncategorized Si es `true`, solo se devuelven cantos **sin ninguna categoría**
 *   (ni momento, ni tiempo, ni categoría adicional).
 * @property sort Criterio de ordenación del resultado.
 */
data class SongSearchCriteria(
    val query: String = "",
    val momentNames: Set<String> = emptySet(),
    val liturgicalTimes: Set<String> = emptySet(),
    val additionalCategories: Set<String> = emptySet(),
    val keys: Set<String> = emptySet(),
    val onlyFavorites: Boolean = false,
    val onlyUncategorized: Boolean = false,
    val sort: SongSortOrder = SongSortOrder.TITLE
) {
    /** Indica si no hay ningún filtro activo (el resultado es el catálogo completo). */
    val isEmpty: Boolean
        get() = query.isBlank() &&
            momentNames.isEmpty() &&
            liturgicalTimes.isEmpty() &&
            additionalCategories.isEmpty() &&
            keys.isEmpty() &&
            !onlyFavorites &&
            !onlyUncategorized

    companion object {
        /**
         * Criterios equivalentes a un filtro de texto simple, útil para adaptar consultas
         * heredadas (p. ej. el buscador del catálogo) sin duplicar la lógica de filtrado.
         */
        fun ofQuery(query: String): SongSearchCriteria = SongSearchCriteria(query = query)
    }
}

/** Criterios de ordenación disponibles para el resultado de búsqueda. */
enum class SongSortOrder(val displayName: String, val shortName: String) {
    /** Alfabético por título. */
    TITLE("Título (A-Z)", "A-Z"),

    /** Por número de cancionero; los cantos sin número van al final. */
    NUMBER("Número de cancionero", "Nº"),

    /** Por última modificación, lo más reciente primero. */
    RECENT("Modificados recientemente", "Recientes")
}
