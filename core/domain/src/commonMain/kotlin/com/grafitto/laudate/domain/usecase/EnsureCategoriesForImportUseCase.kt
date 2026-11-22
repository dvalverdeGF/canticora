package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.repository.CategoryRepository

/**
 * Resultado de reconciliar las categorías de un conjunto de canciones importadas
 * contra el catálogo local de categorías.
 */
data class EnsureCategoriesResult(
    val createdCategories: List<String> = emptyList(),
    val knownMassMoments: List<String> = emptyList(),
    val knownSeasons: List<String> = emptyList()
) {
    val totalCreated: Int get() = createdCategories.size
}

/**
 * Caso de uso: garantizar que existan en el catálogo local las categorías referenciadas
 * por un lote de canciones importadas (p. ej. un cancionero digitalizado).
 *
 * Distingue, según el nombre de la categoría, si corresponde a:
 *  - una parte de la liturgia (momento de la misa), o
 *  - un tiempo litúrgico (Adviento, Cuaresma…), o
 *  - una categoría adicional del repertorio del coro (franciscanas, juveniles…).
 *
 * Las categorías que falten se crean con la naturaleza adecuada para que **nunca se mezclen**
 * las partes de la liturgia con las clasificaciones propias del repertorio.
 * Las ya existentes no se modifican ni se duplican (comparación por nombre, sin distinguir mayúsculas).
 */
class EnsureCategoriesForImportUseCase(
    private val categoryRepository: CategoryRepository
) {

    /** Momentos canónicos de la misa (se consideran parte de la liturgia). */
    private val massMomentNames = setOf(
        "entrada",
        "piedad / kyrie",
        "piedad/kyrie",
        "kyrie",
        "señor ten piedad",
        "gloria",
        "salmo responsorial",
        "salmo",
        "aleluya / aclamación",
        "aleluya",
        "aclamación",
        "ofertorio",
        "santo",
        "padre nuestro",
        "paz",
        "paz / cordero de dios",
        "cordero de dios",
        "comunión",
        "meditación / acción de gracias",
        "acción de gracias",
        "salida / canto mariano",
        "salida",
        "canto final"
    )

    /** Tiempos del año litúrgico. */
    private val seasonNames = setOf(
        "adviento",
        "navidad",
        "cuaresma",
        "triduo pascual",
        "triduo",
        "pascua",
        "tiempo ordinario",
        "ordinario"
    )

    /**
     * Clasifica un nombre de categoría según su naturaleza.
     * Por defecto se considera categoría adicional (repertorio del coro).
     */
    fun classify(name: String): LiturgicalCategoryKind {
        val normalized = name.trim().lowercase()
        return when {
            normalized in massMomentNames -> LiturgicalCategoryKind.MASS_MOMENT
            normalized in seasonNames -> LiturgicalCategoryKind.LITURGICAL_SEASON
            else -> LiturgicalCategoryKind.ADDITIONAL
        }
    }

    /**
     * Calcula las categorías que hay que crear, agrupándolas por naturaleza.
     *
     * @param referencedCategories nombres referenciados por las canciones importadas.
     * @param existingCategoryNames nombres ya presentes en el catálogo local.
     */
    fun plan(
        referencedCategories: Collection<String>,
        existingCategoryNames: Collection<String>
    ): Map<LiturgicalCategoryKind, List<String>> {
        val existing = existingCategoryNames.map { it.trim().lowercase() }.toSet()
        return referencedCategories
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .filterNot { it.lowercase() in existing }
            .groupBy { classify(it) }
    }

    /**
     * Crea en el catálogo local las categorías referenciadas que aún no existan,
     * clasificándolas por naturaleza (momento de misa, tiempo litúrgico o categoría adicional).
     *
     * @return resultado con las categorías efectivamente creadas.
     */
    suspend operator fun invoke(referencedCategories: Collection<String>): EnsureCategoriesResult {
        val existingNames = categoryRepository.getAllCategoriesSnapshot().map { it.name }
        val plan = plan(referencedCategories, existingNames)

        val created = mutableListOf<String>()
        LiturgicalCategoryKind.entries.forEach { kind ->
            val names = plan[kind].orEmpty()
            if (names.isNotEmpty()) {
                created += categoryRepository.ensureCategories(names, kind)
            }
        }

        return EnsureCategoriesResult(createdCategories = created)
    }
}
