package com.grafitto.laudate.domain.repository

import com.grafitto.laudate.domain.model.celebration.Celebration
import kotlinx.coroutines.flow.Flow

/**
 * Contrato puro de Dominio para la gestión de celebraciones litúrgicas y repertorios.
 */
interface CelebrationRepository {
    fun getAllCelebrations(): Flow<List<Celebration>>
    fun getCelebrationById(id: String): Flow<Celebration?>
    suspend fun saveCelebration(celebration: Celebration)
    suspend fun deleteCelebration(celebrationId: String)

    /**
     * Restaura un repertorio eliminado, con todos sus cantos y su orden (deshacer).
     *
     * La celebración se inserta con el **mismo id** con el que se eliminó (más el id de cada canto
     * del repertorio), de modo que los identificadores internos no cambian. Es la operación inversa
     * de [deleteCelebration].
     */
    suspend fun restoreCelebration(celebration: Celebration)

    /**
     * Renombra el momento litúrgico de los cantos que lo tengan asignado en cualquier repertorio.
     *
     * El momento de un canto dentro de una celebración se guarda como texto libre (columna
     * `celebration_items.liturgical_moment`), no como referencia a una categoría. Al renombrar una
     * categoría hay que actualizar también esos textos o el repertorio seguiría mostrando el nombre
     * antiguo (ver [com.grafitto.laudate.domain.usecase.UpdateCategoryUseCase]).
     *
     * La comparación es por nombre sin distinguir mayúsculas y sin recortar; los espacios sobrantes
     * se normalizan con `trim()`.
     *
     * @return número de cantos de repertorio cuyo momento se ha actualizado.
     */
    suspend fun renameMoment(oldName: String, newName: String): Int
}
