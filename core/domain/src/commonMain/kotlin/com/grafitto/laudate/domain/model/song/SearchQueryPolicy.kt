package com.grafitto.laudate.domain.model.song

/**
 * Política de consulta del catálogo (RNF-102/RNF-103).
 *
 * Con un cancionero de ~1.000 cantos, aplicar la búsqueda de texto completa —que recorre
 * también la **letra** de cada canto— en cada pulsación bloquea la escritura. Esta política
 * define cuándo el catálogo puede mostrar **todos** los resultados y cuándo debe mostrar
 * únicamente una **muestra**.
 *
 * Regla: el catálogo muestra una muestra acotada mientras el usuario **no ha concretado** la
 * consulta. Se considera que la consulta está concretada si:
 *  - el texto tiene al menos [MIN_QUERY_LENGTH] caracteres (búsqueda significativa), **o**
 *  - hay algún filtro activo (momento, tiempo, tonalidad o favoritos).
 *
 * Es **Kotlin puro** (sin Android ni Compose), por lo que es directamente testeable.
 */
object SearchQueryPolicy {

    /**
     * Longitud mínima del texto para considerarlo una búsqueda «real».
     *
     * Por debajo de este umbral solo se busca por título/autor/número (metadatos ligeros) y el
     * resultado se limita a una muestra, para que la escritura no se bloquee en teclas
     * intermedias.
     */
    const val MIN_QUERY_LENGTH: Int = 3

    /** Número máximo de cantos que se muestran cuando el resultado **no** está acotado. */
    const val MAX_RESULTS: Int = 200

    /** ¿El texto es una búsqueda significativa (al menos [MIN_QUERY_LENGTH] caracteres, sin contar espacios)? */
    fun isSearchQueryValid(query: String): Boolean = query.trim().length >= MIN_QUERY_LENGTH

    /** ¿Hay algún filtro activo (favoritos, momento, tiempo, categoría adicional o tonalidad)? El texto no cuenta. */
    fun hasActiveFilters(criteria: SongSearchCriteria): Boolean =
        criteria.onlyFavorites ||
            criteria.onlyUncategorized ||
            criteria.momentNames.isNotEmpty() ||
            criteria.liturgicalTimes.isNotEmpty() ||
            criteria.additionalCategories.isNotEmpty() ||
            criteria.keys.isNotEmpty()

    /** ¿El usuario ha concretado la consulta (texto significativo o algún filtro)? */
    fun canShowAllResults(criteria: SongSearchCriteria): Boolean =
        isSearchQueryValid(criteria.query) || hasActiveFilters(criteria)

    /**
     * ¿Hay **alguna** entrada del usuario (aunque no sea suficiente para mostrar todo)?
     *
     * Sirve para decidir si el mensaje de «muestra» debe animar a concretar la búsqueda o
     * simplemente indicar que aún no hay cantos.
     */
    fun hasUsableInput(criteria: SongSearchCriteria): Boolean =
        criteria.query.isNotBlank() || hasActiveFilters(criteria)

    /**
     * Aplica el tope de muestra a una lista ya filtrada.
     *
     * Si el usuario ha concretado la consulta, devuelve el resultado completo; si no, solo los
     * primeros [MAX_RESULTS].
     */
    fun <T> limited(results: List<T>, criteria: SongSearchCriteria): List<T> =
        if (canShowAllResults(criteria)) results else results.take(MAX_RESULTS)
}
