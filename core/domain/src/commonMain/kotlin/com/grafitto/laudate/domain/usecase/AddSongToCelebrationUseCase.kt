package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.celebration.CelebrationItem
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.repository.CelebrationRepository
import kotlinx.coroutines.flow.firstOrNull

/**
 * Resultado de añadir un canto a una celebración.
 *
 * @property celebration Celebración con el canto ya integrado (o la original si ya estaba).
 * @property item Ítem resultante (el nuevo, o el existente si el canto ya estaba presente).
 * @property alreadyPresent `true` si el canto ya formaba parte de la celebración y no se
 *   ha duplicado.
 * @property insertIndex Posición (0-based) en la que quedó el canto dentro del repertorio.
 */
data class AddSongToCelebrationResult(
    val celebration: Celebration,
    val item: CelebrationItem,
    val alreadyPresent: Boolean,
    val insertIndex: Int
)

/**
 * Añade un canto a un repertorio de misa/evento sin duplicarlo (RF-202, RF-204).
 *
 * Reglas de negocio (Kotlin puro, sin Android ni Room):
 * 1. **Sin duplicados**: si el canto ya está en la celebración, no se vuelve a insertar y se
 *    informa con [AddSongToCelebrationResult.alreadyPresent].
 * 2. **Momento sugerido**: si no se indica un momento explícito, se usa el primer
 *    `defaultLiturgicalMoment` del propio canto; si el canto no tiene ninguno, queda
 *    «Sin asignar» (útil en eventos no litúrgicos).
 * 3. **Orden litúrgico**: el canto se inserta justo **después del último canto del mismo
 *    momento** para no romper el orden del repertorio; si su momento aún no existe en la
 *    lista, se añade al final.
 *
 * Devuelve `null` si la celebración indicada no existe.
 */
class AddSongToCelebrationUseCase(
    private val celebrationRepository: CelebrationRepository,
    private val idGenerator: () -> String = { newId() }
) {

    suspend operator fun invoke(
        celebrationId: String,
        song: Song,
        moment: String? = null
    ): AddSongToCelebrationResult? {
        val celebration = celebrationRepository.getCelebrationById(celebrationId).firstOrNull()
            ?: return null

        val existing = celebration.items.firstOrNull { it.songId == song.id }
        if (existing != null) {
            return AddSongToCelebrationResult(
                celebration = celebration,
                item = existing,
                alreadyPresent = true,
                insertIndex = celebration.items.indexOf(existing)
            )
        }

        val resolvedMoment = resolveMoment(moment, song)
        val insertIndex = computeInsertIndex(celebration.items, resolvedMoment)

        val newItem = CelebrationItem(
            id = idGenerator(),
            celebrationId = celebration.id,
            songId = song.id,
            liturgicalMoment = resolvedMoment,
            displayOrder = insertIndex,
            overrideKey = song.originalKey
        )

        val reordered = celebration.items.toMutableList().apply { add(insertIndex, newItem) }
            .mapIndexed { index, item -> item.copy(displayOrder = index) }

        val updated = celebration.copy(items = reordered)
        celebrationRepository.saveCelebration(updated)

        return AddSongToCelebrationResult(
            celebration = updated,
            item = newItem,
            alreadyPresent = false,
            insertIndex = insertIndex
        )
    }

    private fun resolveMoment(explicitMoment: String?, song: Song): String {
        val explicit = explicitMoment?.trim().orEmpty()
        if (explicit.isNotEmpty() && !explicit.equals(SIN_ASIGNAR, ignoreCase = true)) return explicit
        return song.defaultLiturgicalMoments.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    private fun computeInsertIndex(items: List<CelebrationItem>, moment: String): Int {
        if (moment.isBlank()) return items.size
        val target = moment.trim().lowercase()
        val lastOfSameMoment = items.indexOfLast { it.liturgicalMoment.trim().lowercase() == target }
        return if (lastOfSameMoment >= 0) lastOfSameMoment + 1 else items.size
    }

    companion object {
        /** Etiqueta usada por la UI cuando un canto no se asigna a ningún momento. */
        const val SIN_ASIGNAR = "Sin asignar"
    }
}
