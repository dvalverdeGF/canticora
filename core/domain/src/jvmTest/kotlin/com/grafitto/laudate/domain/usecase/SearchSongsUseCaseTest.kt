package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.song.SectionType
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongSearchCriteria
import com.grafitto.laudate.domain.model.song.SongSection
import com.grafitto.laudate.domain.model.song.SongSortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica la semántica del buscador de cantos (RF-107): texto libre tolerante a acentos,
 * número, letra, filtros acumulativos y ordenación.
 */
class SearchSongsUseCaseTest {

    private val useCase = SearchSongsUseCase()

    private val aleluya = song(
        id = "s1",
        title = "Aleluya",
        number = "402",
        moments = listOf("Aleluya"),
        times = listOf("Tiempo Ordinario"),
        key = "Do",
        lyrics = "Aleluya, aleluya, cantad al Señor"
    )
    private val gloria = song(
        id = "s2",
        title = "Gloria",
        number = "12",
        moments = listOf("Gloria"),
        times = listOf("Pascua"),
        key = "C",
        lyrics = "Gloria a Dios en las alturas"
    )
    private val corazon = song(
        id = "s3",
        title = "Corazón Santo",
        number = null,
        moments = listOf("Comunión"),
        times = listOf("Tiempo Ordinario"),
        key = "Mim",
        lyrics = "Corazón santo, en ti confío",
        favorite = true
    )
    private val songs = listOf(aleluya, gloria, corazon)

    @Test
    fun emptyCriteriaReturnsAllSortedByTitle() {
        val result = useCase(songs, SongSearchCriteria())
        assertEquals(listOf("Aleluya", "Corazón Santo", "Gloria"), result.map { it.title })
    }

    @Test
    fun queryMatchesTitleIgnoringCaseAndAccents() {
        val result = useCase(songs, SongSearchCriteria(query = "corazon"))
        assertEquals(listOf("Corazón Santo"), result.map { it.title })
    }

    @Test
    fun queryMatchesNumber() {
        val result = useCase(songs, SongSearchCriteria(query = "402"))
        assertEquals(listOf("Aleluya"), result.map { it.title })
    }

    @Test
    fun numericQueryPutsExactNumberMatchFirst() {
        // "12" coincide con el número de Gloria y con el 402 (contiene "402"→ no; contiene... no).
        // Se añade un canto cuya letra contiene "12" para comprobar la prioridad.
        val decimosegundo = song(id = "s4", title = "Canto 12 veces", number = null, lyrics = "repítelo 12 veces")
        val result = useCase(songs + decimosegundo, SongSearchCriteria(query = "12"))
        assertEquals("Gloria", result.first().title)
    }

    @Test
    fun queryMatchesLyrics() {
        val result = useCase(songs, SongSearchCriteria(query = "alturas"))
        assertEquals(listOf("Gloria"), result.map { it.title })
    }

    @Test
    fun multipleTokensMustAllMatch() {
        val result = useCase(songs, SongSearchCriteria(query = "senor aleluya"))
        assertEquals(listOf("Aleluya"), result.map { it.title })
    }

    @Test
    fun filtersAreCumulativeWithQuery() {
        val result = useCase(
            songs,
            SongSearchCriteria(query = "tiempo ordinario")
        )
        assertEquals(listOf("Aleluya", "Corazón Santo"), result.map { it.title })
    }

    @Test
    fun filterByMoment() {
        val result = useCase(songs, SongSearchCriteria(momentNames = setOf("Gloria")))
        assertEquals(listOf("Gloria"), result.map { it.title })
    }

    @Test
    fun filterByLiturgicalTime() {
        val result = useCase(songs, SongSearchCriteria(liturgicalTimes = setOf("Pascua")))
        assertEquals(listOf("Gloria"), result.map { it.title })
    }

    @Test
    fun filterByLiturgicalTimeAlsoFindsLegacyLabelsStoredAsMoments() {
        // Las versiones antiguas del editor guardaban los tiempos litúrgicos en el campo de
        // momentos; el filtro por tiempo debe seguir encontrándolos.
        val legacy = song(id = "s6", title = "Noche de paz", moments = listOf("Navidad"))
        val result = useCase(songs + legacy, SongSearchCriteria(liturgicalTimes = setOf("Navidad")))
        assertEquals(listOf("Noche de paz"), result.map { it.title })
    }

    @Test
    fun filterByKey() {
        val result = useCase(songs, SongSearchCriteria(keys = setOf("Mim")))
        assertEquals(listOf("Corazón Santo"), result.map { it.title })
    }

    @Test
    fun filterByFavorites() {
        val result = useCase(songs, SongSearchCriteria(onlyFavorites = true))
        assertEquals(listOf("Corazón Santo"), result.map { it.title })
    }

    @Test
    fun filterByUncategorizedReturnsOnlySongsWithoutAnyLabel() {
        val sinEtiquetas = song(id = "s5", title = "Sin etiquetas")
        val result = useCase(songs + sinEtiquetas, SongSearchCriteria(onlyUncategorized = true))
        assertEquals(listOf("Sin etiquetas"), result.map { it.title })
    }

    @Test
    fun filterByAdditionalCategoryMatchesMomentsAndTimes() {
        // El canto guarda las etiquetas como texto: una categoría adicional puede estar en
        // cualquiera de los dos campos.
        val result = useCase(songs, SongSearchCriteria(additionalCategories = setOf("Pascua")))
        assertEquals(listOf("Gloria"), result.map { it.title })
    }

    @Test
    fun filterByAdditionalCategoryIgnoresAccentsAndCase() {
        val result = useCase(songs, SongSearchCriteria(additionalCategories = setOf("comunion")))
        assertEquals(listOf("Corazón Santo"), result.map { it.title })
    }

    @Test
    fun filtersCombineWithAnd() {
        val result = useCase(
            songs,
            SongSearchCriteria(keys = setOf("Do"), momentNames = setOf("Aleluya"))
        )
        assertEquals(listOf("Aleluya"), result.map { it.title })
    }

    @Test
    fun sortByNumberPlacesMissingNumbersLast() {
        val result = useCase(songs, SongSearchCriteria(sort = SongSortOrder.NUMBER))
        assertEquals(listOf("Gloria", "Aleluya", "Corazón Santo"), result.map { it.title })
    }

    @Test
    fun sortByRecentUsesUpdatedAtDescending() {
        val newer = gloria.copy(updatedAt = 5_000)
        val result = useCase(listOf(aleluya, newer, corazon), SongSearchCriteria(sort = SongSortOrder.RECENT))
        assertEquals("Gloria", result.first().title)
    }

    @Test
    fun noMatchReturnsEmptyList() {
        assertTrue(useCase(songs, SongSearchCriteria(query = "inexistente")).isEmpty())
    }

    private fun song(
        id: String,
        title: String,
        number: String? = null,
        moments: List<String> = emptyList(),
        times: List<String> = emptyList(),
        key: String = "C",
        lyrics: String = "",
        favorite: Boolean = false,
        updatedAt: Long = 1_000
    ) = Song(
        id = id,
        title = title,
        originalKey = key,
        number = number,
        defaultLiturgicalMoments = moments,
        defaultLiturgicalTimes = times,
        isFavorite = favorite,
        updatedAt = updatedAt,
        sections = if (lyrics.isEmpty()) emptyList() else listOf(
            SongSection(
                id = "$id-sec",
                songId = id,
                sectionType = SectionType.VERSE,
                sequenceOrder = 0,
                headerTitle = null,
                rawTextContent = lyrics
            )
        )
    )
}
