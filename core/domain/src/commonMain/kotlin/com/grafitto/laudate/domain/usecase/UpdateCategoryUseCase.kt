package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.CelebrationRepository
import com.grafitto.laudate.domain.repository.SongRepository

/**
 * Caso de uso: renombrar una categoría/momento y **propagar el cambio a todo el catálogo**.
 *
 * Las categorías no son solo una lista: el nombre de la categoría se guarda **como texto** dentro
 * de cada canción (momentos y tiempos por defecto) y dentro de cada repertorio (el momento litúrgico
 * asignado a un canto). Si al renombrar solo se actualizara la fila de `liturgical_categories`,
 * las canciones y los repertorios seguirían apuntando al nombre antiguo y la categoría quedaría
 * «huérfana» (con cero cantos).
 *
 * Este caso de uso renombra de forma consistente en las tres ubicaciones: la categoría, las
 * canciones que la referencian (por `id`) y los repertorios (por nombre, en minúsculas).
 *
 * Es **Kotlin puro**: coordina repositorios del Dominio, sin conocer Room ni Android.
 *
 * Se permite renombrar **cualquier** categoría, incluidas las canónicas precargadas: la columna
 * `is_system_standard` solo describe su origen, no las hace inmutables. Si el usuario renombra una
 * categoría, se conserva su `kind` y su condición de canónica.
 */
class UpdateCategoryUseCase(
    private val categoryRepository: CategoryRepository,
    private val songRepository: SongRepository,
    private val celebrationRepository: CelebrationRepository
) {

    suspend operator fun invoke(categoryId: String, newName: String) {
        val trimmedName = newName.trim()
        if (trimmedName.isEmpty()) return

        val current = categoryRepository.getCategoryById(categoryId) ?: return
        if (current.name == trimmedName) return

        // 1. La propia categoría (conserva id, kind, orden y condición de canónica).
        categoryRepository.updateCategory(current.copy(name = trimmedName))

        // 2. Las canciones que la referencian: el nombre vive duplicado en sus listas de
        //    momentos y tiempos por defecto. Se renombra en ambas por seguridad.
        songRepository.renameCategoryTag(current.name, trimmedName)

        // 3. Los repertorios que la usan como momento litúrgico de alguno de sus cantos.
        celebrationRepository.renameMoment(current.name, trimmedName)
    }
}
