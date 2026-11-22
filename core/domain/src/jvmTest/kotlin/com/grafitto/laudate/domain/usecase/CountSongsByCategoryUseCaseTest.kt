package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.song.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el contador de canciones por categoría que muestra la gestión de categorías (RF-103).
 *
 * El canto guarda sus etiquetas como **texto**, así que la correspondencia se hace por nombre
 * normalizado (minúsculas y sin acentos).
 */
class CountSongsByCategoryUseCaseTest {

    private val useCase = CountSongsByCategoryUseCase()

    private val entrada = category("c1", "Entrada", LiturgicalCategoryKind.MASS_MOMENT)
    private val navidad = category("c2", "Navidad", LiturgicalCategoryKind.LITURGICAL_SEASON)
    private val franciscanas = category("c3", "Franciscanas", LiturgicalCategoryKind.ADDITIONAL)
    private val categories = listOf(entrada, navidad, franciscanas)

    @Test
    fun countsSongsPerCategoryAcrossMomentsAndTimes() {
        val songs = listOf(
            song("s1", moments = listOf("Entrada"), times = listOf("Navidad")),
            song("s2", moments = listOf("Entrada")),
            song("s3", moments = listOf("Franciscanas"))
        )

        val counts = useCase(songs, categories)

        assertEquals(2, counts["c1"])
        assertEquals(1, counts["c2"])
        assertEquals(1, counts["c3"])
    }

    @Test
    fun matchesIgnoringAccentsAndCase() {
        val songs = listOf(song("s1", moments = listOf("navidad")))

        val counts = useCase(songs, categories)

        assertEquals(1, counts["c2"])
    }

    @Test
    fun repeatedLabelCountsOnce() {
        val songs = listOf(song("s1", moments = listOf("Entrada", "Entrada")))

        val counts = useCase(songs, categories)

        assertEquals(1, counts["c1"])
    }

    @Test
    fun unknownLabelsAreIgnored() {
        val songs = listOf(song("s1", moments = listOf("Categoría inexistente")))

        val counts = useCase(songs, categories)

        assertTrue(counts.isEmpty())
    }

    @Test
    fun emptyInputsReturnEmptyMap() {
        assertTrue(useCase(emptyList(), categories).isEmpty())
        assertTrue(useCase(listOf(song("s1", moments = listOf("Entrada"))), emptyList()).isEmpty())
    }

    private fun category(id: String, name: String, kind: LiturgicalCategoryKind) =
        LiturgicalCategory(id = id, name = name, kind = kind)

    private fun song(
        id: String,
        moments: List<String> = emptyList(),
        times: List<String> = emptyList()
    ) = Song(
        id = id,
        title = "Canto $id",
        originalKey = "C",
        defaultLiturgicalMoments = moments,
        defaultLiturgicalTimes = times
    )
}
