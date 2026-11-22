package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.celebration.CelebrationItem
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.CelebrationRepository
import com.grafitto.laudate.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica que renombrar una categoría propaga el cambio a las canciones y a los repertorios,
 * que es el punto crítico de RF-103: el nombre se guarda duplicado como texto en las tres
 * ubicaciones y, sin propagación, la categoría quedaría «huérfana».
 */
class UpdateCategoryUseCaseTest {

    @Test
    fun renamesCategoryAndPropagatesToSongsAndCelebrations() = runTest {
        val categories = FakeCategoryRepository().apply {
            add(category(id = "c1", name = "Comunión"))
        }
        val songs = FakeSongRepository().apply {
            add(song("s1", moments = listOf("Comunión", "Entrada")))
            add(song("s2", moments = listOf("Gloria")))
        }
        val celebrations = FakeCelebrationRepository().apply {
            add(celebration("cel1", moment = "Comunión"))
            add(celebration("cel2", moment = "Entrada"))
        }

        UpdateCategoryUseCase(categories, songs, celebrations).invoke("c1", "Canto de Comunión")

        assertEquals("Canto de Comunión", categories.categories.single().name)
        assertEquals(listOf("Canto de Comunión", "Entrada"), songs.songs.first { it.id == "s1" }.defaultLiturgicalMoments)
        // Las canciones que no la usaban no se tocan.
        assertEquals(listOf("Gloria"), songs.songs.first { it.id == "s2" }.defaultLiturgicalMoments)
        assertEquals("Canto de Comunión", celebrations.all.first { it.id == "cel1" }.items.single().liturgicalMoment)
        assertEquals("Entrada", celebrations.all.first { it.id == "cel2" }.items.single().liturgicalMoment)
    }

    @Test
    fun propagatesAlsoToLiturgicalTimes() = runTest {
        val categories = FakeCategoryRepository().apply { add(category("c1", "Cuaresma")) }
        val songs = FakeSongRepository().apply {
            add(song("s1", times = listOf("Cuaresma")))
        }

        UpdateCategoryUseCase(categories, songs, FakeCelebrationRepository()).invoke("c1", "Cuaresma y Triduo")

        assertEquals(listOf("Cuaresma y Triduo"), songs.songs.single().defaultLiturgicalTimes)
    }

    @Test
    fun propagationIgnoresCaseAndAccents() = runTest {
        val categories = FakeCategoryRepository().apply { add(category("c1", "Comunión")) }
        val songs = FakeSongRepository().apply { add(song("s1", moments = listOf("COMUNION"))) }

        UpdateCategoryUseCase(categories, songs, FakeCelebrationRepository()).invoke("c1", "Sagrada Comunión")

        assertEquals(listOf("Sagrada Comunión"), songs.songs.single().defaultLiturgicalMoments)
    }

    @Test
    fun renamingCanonicalCategoryIsAllowedAndPreservesItsKind() = runTest {
        val categories = FakeCategoryRepository().apply {
            add(category("c1", "Gloria", isSystemStandard = true, kind = LiturgicalCategoryKind.MASS_MOMENT))
        }

        UpdateCategoryUseCase(categories, FakeSongRepository(), FakeCelebrationRepository())
            .invoke("c1", "Gloria (himno)")

        val renamed = categories.categories.single()
        assertEquals("Gloria (himno)", renamed.name)
        assertTrue("La condición canónica se conserva", renamed.isSystemStandard)
        assertEquals(LiturgicalCategoryKind.MASS_MOMENT, renamed.kind)
    }

    @Test
    fun doesNothingWhenNameIsUnchanged() = runTest {
        val categories = FakeCategoryRepository().apply { add(category("c1", "Comunión")) }
        val songs = FakeSongRepository().apply { add(song("s1", moments = listOf("Comunión"))) }
        val celebrations = FakeCelebrationRepository().apply { add(celebration("cel1", "Comunión")) }

        UpdateCategoryUseCase(categories, songs, celebrations).invoke("c1", "  Comunión  ")

        assertEquals(0, songs.renameCalls)
        assertEquals(0, celebrations.renameCalls)
    }

    @Test
    fun ignoresBlankNameAndUnknownCategory() = runTest {
        val categories = FakeCategoryRepository().apply { add(category("c1", "Comunión")) }
        val songs = FakeSongRepository()

        UpdateCategoryUseCase(categories, songs, FakeCelebrationRepository()).invoke("c1", "   ")
        UpdateCategoryUseCase(categories, songs, FakeCelebrationRepository()).invoke("no-existe", "Otra")

        assertEquals("Comunión", categories.categories.single().name)
        assertEquals(0, songs.renameCalls)
        assertNull(categories.updated)
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────

    private fun category(
        id: String,
        name: String,
        isSystemStandard: Boolean = false,
        kind: LiturgicalCategoryKind = LiturgicalCategoryKind.MASS_MOMENT
    ) = LiturgicalCategory(
        id = id,
        name = name,
        isSystemStandard = isSystemStandard,
        kind = kind
    )
    private fun song(
        id: String,
        moments: List<String> = emptyList(),
        times: List<String> = emptyList()
    ) = Song(
        id = id,
        title = "Canto $id",
        originalKey = "G",
        defaultLiturgicalMoments = moments,
        defaultLiturgicalTimes = times
    )

    private fun celebration(id: String, moment: String) = Celebration(
        id = id,
        name = "Celebración $id",
        dateTimestamp = 0,
        items = listOf(
            CelebrationItem(
                id = "item-$id",
                celebrationId = id,
                songId = "s-$id",
                liturgicalMoment = moment,
                displayOrder = 0
            )
        )
    )

    private class FakeSongRepository : SongRepository {
        val songs = mutableListOf<Song>()
        var renameCalls = 0

        fun add(song: Song) { songs += song }

        override fun getAllSongs(): Flow<List<Song>> = flowOf(songs.toList())
        override fun getAllSongsWithLyrics(): Flow<List<Song>> = flowOf(songs.toList())
        override suspend fun getSongCount(): Int = songs.size
        override fun getFavoriteSongs(): Flow<List<Song>> = flowOf(songs.filter { it.isFavorite })
        override fun getSongById(id: String): Flow<Song?> = flowOf(songs.firstOrNull { it.id == id })
        override suspend fun getSongsByIds(ids: List<String>): List<Song> = songs.filter { it.id in ids }
        override suspend fun getSongStubsByIds(ids: List<String>): List<Song> = getSongsByIds(ids)
        override suspend fun overwriteSong(song: Song) = saveSong(song)
        override fun searchSongs(query: String): Flow<List<Song>> = flowOf(songs)
        override suspend fun saveSong(song: Song) { songs.removeAll { it.id == song.id }; songs += song }
        override suspend fun saveSongs(songs: List<Song>) { songs.forEach { saveSong(it) } }
        override suspend fun setFavorite(songId: String, isFavorite: Boolean) = Unit
        override suspend fun deleteSong(songId: String) { songs.removeAll { it.id == songId } }
        override suspend fun findSongByContentHash(hash: String): Song? = null
        override suspend fun findSongByContentSignature(song: Song): Song? = null
        override suspend fun getSongsMissingContentHash(): List<Song> = emptyList()
        override suspend fun updateContentHash(songId: String, hash: String) = Unit

        override suspend fun renameCategoryTag(oldName: String, newName: String): Int {
            renameCalls++
            val target = normalize(oldName)
            var modified = 0
            songs.replaceAll { song ->
                val moments = song.defaultLiturgicalMoments.map { if (normalize(it) == target) newName else it }
                val times = song.defaultLiturgicalTimes.map { if (normalize(it) == target) newName else it }
                if (moments != song.defaultLiturgicalMoments || times != song.defaultLiturgicalTimes) modified++
                song.copy(defaultLiturgicalMoments = moments, defaultLiturgicalTimes = times)
            }
            return modified
        }
    }

    private class FakeCelebrationRepository : CelebrationRepository {
        val all = mutableListOf<Celebration>()
        var renameCalls = 0

        fun add(celebration: Celebration) { all += celebration }

        override fun getAllCelebrations(): Flow<List<Celebration>> = flowOf(all.toList())
        override fun getCelebrationById(id: String): Flow<Celebration?> = flowOf(all.firstOrNull { it.id == id })
        override suspend fun saveCelebration(celebration: Celebration) { all.removeAll { it.id == celebration.id }; all += celebration }
        override suspend fun deleteCelebration(celebrationId: String) { all.removeAll { it.id == celebrationId } }
        override suspend fun restoreCelebration(celebration: Celebration) { all.removeAll { it.id == celebration.id }; all += celebration }

        override suspend fun renameMoment(oldName: String, newName: String): Int {
            renameCalls++
            val target = normalize(oldName)
            var modified = 0
            all.replaceAll { celebration ->
                val items = celebration.items.map { item ->
                    if (normalize(item.liturgicalMoment) == target && item.liturgicalMoment != newName) {
                        modified++
                        item.copy(liturgicalMoment = newName)
                    } else {
                        item
                    }
                }
                celebration.copy(items = items)
            }
            return modified
        }
    }
}

/**
 * Normaliza una etiqueta como lo hace el Dominio (minúsculas y sin acentos).
 * Debe ser de nivel de archivo para que las clases anidadas puedan usarla.
 */
private fun normalize(text: String): String {
    val decomposed = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
    return decomposed.replace(Regex("\\p{Mn}+"), "").lowercase()
}

private class FakeCategoryRepository : CategoryRepository {
    val categories = mutableListOf<LiturgicalCategory>()
    var updated: LiturgicalCategory? = null

    fun add(category: LiturgicalCategory) { categories += category }

    override fun getAllCategories(): Flow<List<LiturgicalCategory>> = flowOf(categories.toList())
    override suspend fun getAllCategoriesSnapshot(): List<LiturgicalCategory> = categories.toList()
    override suspend fun getCategoryById(id: String): LiturgicalCategory? = categories.firstOrNull { it.id == id }
    override suspend fun saveCategory(category: LiturgicalCategory) { add(category) }
    override suspend fun updateCategory(category: LiturgicalCategory) {
        updated = category
        val index = categories.indexOfFirst { it.id == category.id }
        if (index >= 0) categories[index] = category
    }
    override suspend fun saveCategories(categories: List<LiturgicalCategory>): Int = 0
    override suspend fun deleteCategory(categoryId: String) { categories.removeAll { it.id == categoryId } }
    override suspend fun seedStandardCategoriesIfEmpty() = Unit
    override suspend fun ensureCategories(names: Collection<String>, kind: LiturgicalCategoryKind): List<String> = emptyList()
}
