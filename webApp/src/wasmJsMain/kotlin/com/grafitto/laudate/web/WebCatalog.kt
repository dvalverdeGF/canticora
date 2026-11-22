package com.grafitto.laudate.web

import com.grafitto.laudate.domain.backup.BackupSerializer
import com.grafitto.laudate.domain.backup.toDomainCategories
import com.grafitto.laudate.domain.backup.toDomainSongs
import com.grafitto.laudate.web.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Carga del catálogo inicial en la web — ADR-023.
 *
 * **La web se publica con el catálogo de ejemplo.** El cancionero completo es contenido privado
 * del autor y no viaja con la aplicación; lo que sí se sirve es la semillita de 55 cantos
 * tradicionales (`webApp/demo/canticora_semillita.json`), para que la app se vea con contenido real
 * desde el primer arranque. El cancionero propio se añade desde «Importar» (ChordPro, repertorio
 * `.canticora` o copia `.json`).
 *
 * El recurso `files/catalog.json` existe siempre (lo escribe `prepareWebCatalog` en cada
 * compilación). Una compilación **personal**, con `-Pcanticora.catalog=<fichero>`, lo sustituye.
 */
object WebCatalog {

    /** Ruta del recurso dentro de `composeResources` (ver `webApp/build.gradle.kts`). */
    private const val CATALOG_RESOURCE = "files/catalog.json"

    /** Estado del arranque. La UI no pinta nada hasta tener el catálogo. */
    sealed interface Status {
        data object Loading : Status
        data class Ready(val songCount: Int) : Status
        data class Failure(val message: String) : Status
    }

    /**
     * Restaura lo del usuario (`localStorage`) y después hidrata el catálogo inicial.
     *
     * El parseo se hace fuera del hilo de la UI (`Dispatchers.Default`) para que el navegador pueda
     * repintar la pantalla de espera mientras tanto. Un catálogo vacío **no es un error**: es el
     * caso normal de la web publicada, que se entrega sin canciones.
     */
    suspend fun load(container: WebContainer): Status {
        container.restoreUserData()
        return try {
            val text = Res.readBytes(CATALOG_RESOURCE).decodeToString()
            val payload = withContext(Dispatchers.Default) {
                val parsed = BackupSerializer.importFromJson(text)
                parsed.toDomainSongs() to parsed.toDomainCategories()
            }
            val (songs, categories) = payload
            container.hydrate(songs, categories)
            Status.Ready(songs.size)
        } catch (error: Throwable) {
            Status.Failure(
                error.message ?: "Error inesperado al leer el cancionero (${error::class.simpleName})."
            )
        }
    }
}
