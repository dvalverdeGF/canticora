package com.grafitto.laudate.ui.catalog

import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.song.SearchQueryPolicy
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifica el comportamiento de muestra acotada del catálogo: con un catálogo grande se muestra
 * solo una muestra hasta que el usuario concreta la búsqueda (texto significativo o filtro).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SongCatalogViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun largeCatalogShowsOnlyASampleUntilQueryIsConcrete() = runTest(dispatcher) {
        val vm = createViewModel(songCount = 500)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(SearchQueryPolicy.MAX_RESULTS, state.songs.size)
        assertEquals(500, state.resultCount)
        assertTrue("Debe indicar que muestra una muestra", state.isShowingSample)
    }

    @Test
    fun significantQueryShowsFullResult() = runTest(dispatcher) {
        val vm = createViewModel(songCount = 500)
        advanceUntilIdle()

        vm.processIntent(SongCatalogUiIntent.UpdateSearchCriteria(vm.uiState.value.searchCriteria.copy(query = "canto")))
        advanceTimeBy(300) // supera el debounce de 200 ms
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(500, state.songs.size)
        assertEquals(500, state.resultCount)
        assertFalse(state.isShowingSample)
    }

    @Test
    fun activeFilterShowsFullResultAndLimitsToIt() = runTest(dispatcher) {
        val vm = createViewModel(songCount = 500)
        advanceUntilIdle()

        // 40 cantos marcados con el momento "Gloria".
        vm.processIntent(SongCatalogUiIntent.UpdateSearchCriteria(vm.uiState.value.searchCriteria.copy(momentNames = setOf("Gloria"))))
        advanceTimeBy(300)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(40, state.songs.size)
        assertEquals(40, state.resultCount)
        assertFalse(state.isShowingSample)
    }

    private fun createViewModel(songCount: Int): SongCatalogViewModel {
        val songs = (1..songCount).map { index ->
            Song(
                id = "s$index",
                title = "Canto $index",
                originalKey = "G",
                defaultLiturgicalMoments = if (index <= 40) listOf("Gloria") else emptyList()
            )
        }
        return SongCatalogViewModel(
            songRepository = FakeSongRepository(songs),
            categoryRepository = FakeCategoryRepository(),
            computeDispatcher = dispatcher
        )
    }

    private class FakeSongRepository(private val all: List<Song>) : SongRepository {
        override fun getAllSongs(): Flow<List<Song>> = flowOf(all)
        override fun getAllSongsWithLyrics(): Flow<List<Song>> = flowOf(all)
        override suspend fun getSongCount(): Int = all.size
        override fun getFavoriteSongs(): Flow<List<Song>> = flowOf(all.filter { it.isFavorite })
        override fun getSongById(id: String): Flow<Song?> = flowOf(all.firstOrNull { it.id == id })
        override suspend fun getSongsByIds(ids: List<String>): List<Song> = all.filter { it.id in ids }
        override suspend fun getSongStubsByIds(ids: List<String>): List<Song> = getSongsByIds(ids)
        override suspend fun overwriteSong(song: Song) = saveSong(song)
        override fun searchSongs(query: String): Flow<List<Song>> = flowOf(all)
        override suspend fun saveSong(song: Song) = Unit
        override suspend fun saveSongs(songs: List<Song>) = Unit
        override suspend fun setFavorite(songId: String, isFavorite: Boolean) = Unit
        override suspend fun deleteSong(songId: String) = Unit
        override suspend fun findSongByContentHash(hash: String): Song? = null
        override suspend fun findSongByContentSignature(song: Song): Song? = null
        override suspend fun getSongsMissingContentHash(): List<Song> = emptyList()
        override suspend fun updateContentHash(songId: String, hash: String) = Unit
        override suspend fun renameCategoryTag(oldName: String, newName: String): Int = 0
    }

    private class FakeCategoryRepository : CategoryRepository {
        private val categories = listOf(
            LiturgicalCategory(id = "c1", name = "Gloria", kind = LiturgicalCategoryKind.MASS_MOMENT)
        )
        override fun getAllCategories(): Flow<List<LiturgicalCategory>> = flowOf(categories)
        override suspend fun getAllCategoriesSnapshot(): List<LiturgicalCategory> = categories
        override suspend fun getCategoryById(id: String): LiturgicalCategory? = categories.firstOrNull { it.id == id }
        override suspend fun saveCategory(category: LiturgicalCategory) = Unit
        override suspend fun updateCategory(category: LiturgicalCategory) = Unit
        override suspend fun saveCategories(categories: List<LiturgicalCategory>): Int = 0
        override suspend fun deleteCategory(categoryId: String) = Unit
        override suspend fun seedStandardCategoriesIfEmpty() = Unit
        override suspend fun ensureCategories(names: Collection<String>, kind: LiturgicalCategoryKind): List<String> = emptyList()
    }
}
