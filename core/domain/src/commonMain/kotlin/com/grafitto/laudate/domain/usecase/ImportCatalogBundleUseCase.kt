package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.backup.BackupSerializer
import com.grafitto.laudate.domain.backup.toDomainCategories
import com.grafitto.laudate.domain.backup.toDomainSongs
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.SongRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resumen de importar un **catálogo o paquete de Canticora**.
 */
data class CatalogImportResult(
    /** Canciones incorporadas (la reimportación no duplica: se guardan por su id). */
    val songsImported: Int = 0,
    /** Categorías nuevas creadas (las que ya existían por nombre se ignoran). */
    val categoriesCreated: Int = 0
) {
    val isEmpty: Boolean get() = songsImported == 0 && categoriesCreated == 0
}

/**
 * Caso de uso: importar un **catálogo o paquete de Canticora** en formato JSON con el esquema
 * `BackupPayload` (el mismo que la semilla del cancionero y que la exportación de catálogo).
 *
 * Es distinto de la **copia de seguridad** (que es un ZIP con manifiesto y adjuntos): aquí llega el
 * texto JSON ya leído por la capa de datos/UI.
 *
 * Garantías:
 *  - **Idempotencia**: las canciones se guardan por su `id` (upsert, no duplica) y las categorías
 *    se insertan con índice único por `name`, de modo que reimportar el mismo paquete no repite nada.
 *  - **Naturaleza de las categorías**: se respeta el `kind` **declarado en el paquete**; nunca se
 *    reclasifica por el nombre.
 *  - **Lote**: canciones y categorías se escriben en una sola transacción cada uno.
 *
 * Kotlin puro: no depende de Android ni de la base de datos.
 */
class ImportCatalogBundleUseCase(
    private val songRepository: SongRepository,
    private val categoryRepository: CategoryRepository,
    /** El deserializado y mapeado de un cancionero completo nunca debe correr en el hilo principal. */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend operator fun invoke(json: String): CatalogImportResult = withContext(computeDispatcher) {
        val payload = BackupSerializer.importFromJson(json)

        val categories = payload.toDomainCategories()
        val songs = payload.toDomainSongs()

        // Las categorías primero: así los cantos importados siempre encuentran su momento litúrgico.
        val categoriesCreated = if (categories.isNotEmpty()) {
            categoryRepository.saveCategories(categories)
        } else {
            0
        }

        if (songs.isNotEmpty()) {
            songRepository.saveSongs(songs)
        }

        CatalogImportResult(songsImported = songs.size, categoriesCreated = categoriesCreated)
    }
}
