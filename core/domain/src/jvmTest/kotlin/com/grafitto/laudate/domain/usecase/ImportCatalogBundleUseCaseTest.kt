package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la **importación de catálogos/paquetes de Canticora** (esquema `BackupPayload`) desde
 * la pantalla «Importar».
 *
 * Es la vía que sustituye a la siembra automática del APK: la semilla ya no viaja en la app, sino
 * que el usuario la importa cuando quiere. Se comprueba que:
 *  - se incorporan canciones y categorías;
 *  - la **naturaleza** (`kind`) de las categorías se respeta tal cual la declara el paquete;
 *  - reimportar **no duplica** (upsert por id + índice único por nombre).
 */
class ImportCatalogBundleUseCaseTest {

    @Test
    fun importsSongsAndCategoriesFromBundle() = runTest {
        val songs = FakeSongRepository()
        val categories = FakeCategoryRepository()

        val result = ImportCatalogBundleUseCase(songs, categories, Dispatchers.Unconfined).invoke(bundleOf())

        assertEquals(2, result.songsImported)
        assertEquals(2, result.categoriesCreated)
        assertEquals(2, songs.songs.size)
        assertEquals(2, categories.categories.size)
    }

    @Test
    fun preservesDeclaredCategoryKindWithoutReclassifying() = runTest {
        // "Ofertorio" suena a momento de la misa, pero el paquete lo declara tiempo litúrgico:
        // manda lo declarado, no el nombre.
        val json = """
            {
              "version": 1,
              "categories": [
                { "id": "c1", "name": "Ofertorio", "kind": "LITURGICAL_SEASON",
                  "displayOrder": 3, "isSystemStandard": false, "createdAt": 1 }
              ],
              "songs": [],
              "celebrations": []
            }
        """.trimIndent()
        val categories = FakeCategoryRepository()

        ImportCatalogBundleUseCase(FakeSongRepository(), categories, Dispatchers.Unconfined).invoke(json)

        assertEquals(LiturgicalCategoryKind.LITURGICAL_SEASON, categories.categories.single().kind)
    }

    @Test
    fun reimportingDoesNotDuplicateCategoriesNorSongs() = runTest {
        val songs = FakeSongRepository()
        val categories = FakeCategoryRepository()
        val useCase = ImportCatalogBundleUseCase(songs, categories, Dispatchers.Unconfined)

        useCase(bundleOf())
        val second = useCase(bundleOf())

        // Las categorías ya existen por nombre y las canciones se sobrescriben por id.
        assertEquals(0, second.categoriesCreated)
        assertEquals(2, categories.categories.size)
        assertEquals(2, songs.songs.size)
    }

    @Test
    fun emptyBundleImportsNothing() = runTest {
        val songs = FakeSongRepository()
        val result = ImportCatalogBundleUseCase(songs, FakeCategoryRepository(), Dispatchers.Unconfined)
            .invoke("""{"version":1,"songs":[],"celebrations":[],"categories":[]}""")

        assertTrue(result.isEmpty)
        assertTrue(songs.songs.isEmpty())
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────────

    private fun bundleOf() = """
        {
          "version": 1,
          "exportedAt": 0,
          "categories": [
            { "id": "c1", "name": "Entrada", "kind": "MASS_MOMENT", "displayOrder": 0,
              "isSystemStandard": false, "createdAt": 1 },
            { "id": "c2", "name": "Adviento", "kind": "LITURGICAL_SEASON", "displayOrder": 1,
              "isSystemStandard": false, "createdAt": 1 }
          ],
          "songs": [
            { "id": "s1", "title": "Canto uno", "originalKey": "G", "createdAt": 1, "updatedAt": 1,
              "defaultLiturgicalMoments": ["Entrada"], "sections": [] },
            { "id": "s2", "title": "Canto dos", "originalKey": "D", "createdAt": 1, "updatedAt": 1,
              "sections": [] }
          ],
          "celebrations": []
        }
    """.trimIndent()

    private class FakeSongRepository : SongRepository {
        val songs = mutableListOf<Song>()
        override fun getAllSongs(): Flow<List<Song>> = flowOf(songs.toList())
        override fun getAllSongsWithLyrics(): Flow<List<Song>> = flowOf(songs.toList())
        override suspend fun getSongCount(): Int = songs.size
        override fun getFavoriteSongs(): Flow<List<Song>> = flowOf(songs.filter { it.isFavorite })
        override fun getSongById(id: String): Flow<Song?> = flowOf(songs.firstOrNull { it.id == id })
        override suspend fun getSongsByIds(ids: List<String>): List<Song> = songs.filter { it.id in ids }
        override suspend fun getSongStubsByIds(ids: List<String>): List<Song> = getSongsByIds(ids)
        override suspend fun overwriteSong(song: Song) = saveSong(song)
        override fun searchSongs(query: String): Flow<List<Song>> =
            flowOf(songs.filter { it.title.contains(query, ignoreCase = true) })

        override suspend fun saveSong(song: Song) {
            songs.removeAll { it.id == song.id }
            songs.add(song)
        }

        override suspend fun saveSongs(songs: List<Song>) {
            songs.forEach { saveSong(it) }
        }

        override suspend fun setFavorite(songId: String, isFavorite: Boolean) {
            val index = songs.indexOfFirst { it.id == songId }
            if (index >= 0) songs[index] = songs[index].copy(isFavorite = isFavorite)
        }

        override suspend fun deleteSong(songId: String) = songs.removeAll { it.id == songId }.let { }
        override suspend fun findSongByContentHash(hash: String): Song? =
            songs.firstOrNull { it.contentHash == hash }

        override suspend fun findSongByContentSignature(song: Song): Song? = null

        override suspend fun getSongsMissingContentHash(): List<Song> =
            songs.filter { it.contentHash.isNullOrBlank() }

        override suspend fun updateContentHash(songId: String, hash: String) {
            val index = songs.indexOfFirst { it.id == songId }
            if (index >= 0) songs[index] = songs[index].copy(contentHash = hash)
        }

        override suspend fun renameCategoryTag(oldName: String, newName: String): Int = 0
    }

    private class FakeCategoryRepository : CategoryRepository {
        val categories = mutableListOf<LiturgicalCategory>()

        override fun getAllCategories(): Flow<List<LiturgicalCategory>> = flowOf(categories.toList())
        override suspend fun getAllCategoriesSnapshot() = categories.toList()
        override suspend fun getCategoryById(id: String) = categories.firstOrNull { it.id == id }
        override suspend fun saveCategory(category: LiturgicalCategory) {
            saveCategories(listOf(category))
        }

        override suspend fun updateCategory(category: LiturgicalCategory) = Unit

        override suspend fun saveCategories(categories: List<LiturgicalCategory>): Int {
            val existing = this.categories.map { it.name.trim().lowercase() }.toMutableSet()
            var created = 0
            categories.forEach { category ->
                if (existing.add(category.name.trim().lowercase())) {
                    this.categories.add(category)
                    created++
                }
            }
            return created
        }

        override suspend fun deleteCategory(categoryId: String) {
            categories.removeAll { it.id == categoryId }
        }

        override suspend fun seedStandardCategoriesIfEmpty() = Unit

        override suspend fun ensureCategories(
            names: Collection<String>,
            kind: LiturgicalCategoryKind
        ): List<String> = names.toList()
    }
}
