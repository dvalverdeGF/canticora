package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.celebration.CelebrationItem
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.repository.CelebrationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica las reglas de inserción de un canto en un repertorio (RF-202/RF-204):
 * sin duplicados, momento sugerido y orden litúrgico.
 */
class AddSongToCelebrationUseCaseTest {

    private val repository = FakeCelebrationRepository()
    private val useCase = AddSongToCelebrationUseCase(repository) { "generated-id" }

    private val entradaId = "item-entrada"
    private val gloriaId = "item-gloria"

    private val celebration = Celebration(
        id = "c1",
        name = "Misa Dominical",
        dateTimestamp = 0,
        items = listOf(
            CelebrationItem(id = entradaId, celebrationId = "c1", songId = "s-entrada", liturgicalMoment = "Entrada", displayOrder = 0),
            CelebrationItem(id = gloriaId, celebrationId = "c1", songId = "s-gloria", liturgicalMoment = "Gloria", displayOrder = 1)
        )
    )

    private val song = Song(id = "s-aleluya", title = "Aleluya", originalKey = "Re", defaultLiturgicalMoments = listOf("Aleluya"))

    @Test
    fun returnsNullWhenCelebrationDoesNotExist() = runBlocking {
        repository.stored = null
        assertNull(useCase("missing", song))
    }

    @Test
    fun appendsAtEndWhenMomentIsNotYetInTheList() = runBlocking {
        repository.stored = celebration
        val result = useCase("c1", song)!!

        assertEquals(2, result.insertIndex)
        assertFalse(result.alreadyPresent)
        assertEquals(3, result.celebration.items.size)
        assertEquals("Aleluya", result.celebration.items.last().liturgicalMoment)
    }

    @Test
    fun insertsAfterLastItemOfTheSameMoment() = runBlocking {
        repository.stored = celebration.copy(
            items = celebration.items.map { it.copy() } + CelebrationItem(
                id = "item-gloria-2", celebrationId = "c1", songId = "s-gloria2", liturgicalMoment = "Gloria", displayOrder = 2
            )
        )
        val gloriaSong = song.copy(id = "s-gloria3", defaultLiturgicalMoments = listOf("Gloria"))

        val result = useCase("c1", gloriaSong)!!

        // Debe insertarse justo después del último Gloria (índice 3), no al final tras "Entrada".
        assertEquals(3, result.insertIndex)
        assertEquals("Gloria", result.celebration.items[3].liturgicalMoment)
        assertEquals("s-gloria3", result.celebration.items[3].songId)
    }

    @Test
    fun doesNotDuplicateWhenSongAlreadyPresent() = runBlocking {
        repository.stored = celebration
        val existing = song.copy(id = "s-gloria")

        val result = useCase("c1", existing)!!

        assertTrue(result.alreadyPresent)
        assertEquals(2, result.celebration.items.size)
        // Si el canto ya estaba, no debe volver a guardarse.
        assertEquals(0, repository.saveCount)
        assertEquals(gloriaId, result.item.id)
    }

    @Test
    fun suggestsMomentFromSongWhenNotProvided() = runBlocking {
        repository.stored = celebration
        val result = useCase("c1", song, moment = null)!!
        assertEquals("Aleluya", result.item.liturgicalMoment)
    }

    @Test
    fun explicitMomentWinsOverSuggestion() = runBlocking {
        repository.stored = celebration
        val result = useCase("c1", song, moment = "Comunión")!!
        assertEquals("Comunión", result.item.liturgicalMoment)
    }

    @Test
    fun sinAsignarFallsBackToSongSuggestion() = runBlocking {
        repository.stored = celebration
        val result = useCase("c1", song, moment = AddSongToCelebrationUseCase.SIN_ASIGNAR)!!
        assertEquals("Aleluya", result.item.liturgicalMoment)
    }

    @Test
    fun songWithoutMomentsEndsUpUnassigned() = runBlocking {
        repository.stored = celebration
        val orphan = song.copy(id = "s-x", defaultLiturgicalMoments = emptyList())
        val result = useCase("c1", orphan)!!
        assertEquals("", result.item.liturgicalMoment)
    }

    @Test
    fun keepsKeyOverrideFromSong() = runBlocking {
        repository.stored = celebration
        val result = useCase("c1", song)!!
        assertEquals("Re", result.item.overrideKey)
    }

    @Test
    fun normalizesDisplayOrderAfterInsert() = runBlocking {
        repository.stored = celebration
        val result = useCase("c1", song)!!
        assertEquals(listOf(0, 1, 2), result.celebration.items.map { it.displayOrder })
    }

    private class FakeCelebrationRepository : CelebrationRepository {
        var stored: Celebration? = null
        var saveCount = 0

        override fun getAllCelebrations(): Flow<List<Celebration>> =
            flowOf(listOfNotNull(stored))

        override fun getCelebrationById(id: String): Flow<Celebration?> =
            flowOf(stored?.takeIf { it.id == id })

        override suspend fun saveCelebration(celebration: Celebration) {
            saveCount++
            stored = celebration
        }

        override suspend fun deleteCelebration(celebrationId: String) {
            stored = null
        }

        override suspend fun restoreCelebration(celebration: Celebration) {
            stored = celebration
        }

        override suspend fun renameMoment(oldName: String, newName: String): Int = 0
    }
}
