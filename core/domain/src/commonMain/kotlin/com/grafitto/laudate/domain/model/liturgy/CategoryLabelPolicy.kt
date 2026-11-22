package com.grafitto.laudate.domain.model.liturgy

import com.grafitto.laudate.domain.util.diacriticsFold

/**
 * Reglas de correspondencia entre las **etiquetas de texto** que guarda un canto y las
 * categorías del catálogo.
 *
 * El canto no tiene tabla de unión con las categorías: guarda sus etiquetas como texto en
 * `defaultLiturgicalMoments` y `defaultLiturgicalTimes`. Estas reglas concentran en un solo
 * sitio cómo se comparan esos textos (por nombre normalizado) y cómo se reparten por
 * naturaleza al guardar.
 *
 * Es **Kotlin puro** (sin Android ni Room), por lo que es directamente testeable.
 */
object CategoryLabelPolicy {

    /** Normaliza un nombre de categoría: sin espacios sobrantes, sin acentos y en minúsculas. */
    fun normalize(text: String): String = diacriticsFold(text.trim())

    /**
     * Reparte las etiquetas seleccionadas en el editor según la naturaleza de su categoría.
     *
     * Los **tiempos litúrgicos** (Adviento, Navidad, Cuaresma…) van a `times`; los **momentos de
     * la misa** y las **categorías adicionales** van a `moments` (el canto no tiene un campo
     * propio para las adicionales). Sin este reparto, un tiempo litúrgico acabaría guardado como
     * momento y el filtro por tiempo no lo encontraría.
     *
     * Las etiquetas que no correspondan a ninguna categoría conocida se conservan como momentos,
     * para no perder información al guardar.
     */
    fun splitByKind(
        selected: Set<String>,
        categories: List<LiturgicalCategory>
    ): CategoryLabels {
        val kindByName = categories.associate { normalize(it.name) to it.kind }
        val moments = mutableListOf<String>()
        val times = mutableListOf<String>()

        selected.forEach { name ->
            if (kindByName[normalize(name)] == LiturgicalCategoryKind.LITURGICAL_SEASON) {
                times.add(name)
            } else {
                moments.add(name)
            }
        }

        return CategoryLabels(moments = moments, times = times)
    }
}

/** Etiquetas de un canto ya repartidas por naturaleza, listas para persistir. */
data class CategoryLabels(
    val moments: List<String>,
    val times: List<String>
)
