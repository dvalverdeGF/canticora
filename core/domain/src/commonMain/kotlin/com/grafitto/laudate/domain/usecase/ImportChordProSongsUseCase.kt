package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.parser.ChordProParser
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.transfer.ImportConflict
import com.grafitto.laudate.domain.transfer.ImportConflictPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Un archivo ChordPro a importar: su contenido ya leído y, opcionalmente, su nombre de fichero.
 */
data class ChordProFile(
    val content: String,
    /** Nombre del archivo, usado como título de reserva si el ChordPro no trae `{title:}`. */
    val fileName: String? = null
)

/**
 * Resumen de importar un lote de canciones sueltas en ChordPro.
 */
data class ChordProImportResult(
    /** Canciones que no estaban en el catálogo y se han creado. */
    val songsCreated: Int = 0,
    /** Canciones que ya existían (misma huella de contenido) y **no** se han duplicado. */
    val songsReused: Int = 0,
    /** Canciones que ya existían con contenido distinto y se han **actualizado** con la versión que llegó. */
    val songsOverwritten: Int = 0,
    /** Categorías nuevas creadas a partir de los momentos/tiempos que citan los cantos. */
    val categoriesCreated: Int = 0
) {
    val total: Int get() = songsCreated + songsReused + songsOverwritten
}

/**
 * Inspección previa de un lote de ChordPro **sin escribir nada**: permite saber si alguno de los
 * cantos ya existe con contenido distinto (y pedir permiso antes de sobrescribir).
 */
data class ChordProImportPreview(
    val total: Int,
    val newSongs: Int,
    /** Cantos que ya existen **idénticos** (no requieren decisión). */
    val identical: Int,
    /** Cantos que ya existen con cambios, pendientes de decisión. */
    val conflicts: List<ImportConflict>
) {
    val hasConflicts: Boolean get() = conflicts.isNotEmpty()
}

/**
 * Caso de uso: importar **canciones sueltas en formato ChordPro** (uno o varios archivos).
 *
 * Los metadatos se deducen de las propias directivas del archivo (`{title:}`, `{artist:}`,
 * `{key:}`, `{tempo:}`, `{capo:}`, `{number:}`); si falta el título, se usa el **nombre del
 * fichero** como reserva. No se inventa ninguna atribución de autoría.
 *
 * Reglas:
 *  - Un canto **idéntico** (misma huella de contenido) nunca se duplica: se reconoce como reutilizado.
 *  - Un canto que ya existe **con cambios** (te lo han reenviado tras editarlo) se decide con
 *    [ImportConflictPolicy]: sobrescribir (por defecto), conservar ambos u omitir. La aplicación
 *    llama antes a [inspect] para poder preguntar con permiso.
 *  - Las categorías citadas por los cantos se reconcilian con el catálogo local mediante
 *    [EnsureCategoriesForImportUseCase].
 *
 * Kotlin puro: no accede a ficheros ni a Android; los contenidos llegan ya leídos.
 */
class ImportChordProSongsUseCase(
    private val songRepository: SongRepository,
    private val ensureCategoriesForImportUseCase: EnsureCategoriesForImportUseCase,
    private val generateId: () -> String = { newId() },
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    /**
     * Analiza el lote **sin escribir nada**. Devuelve cuántos cantos son nuevos, cuántos idénticos y
     * qué conflictos hay (mismos cantos con contenido distinto), para poder preguntar al usuario.
     */
    suspend fun inspect(files: List<ChordProFile>): ChordProImportPreview =
        withContext(computeDispatcher) {
            var newSongs = 0
            var identical = 0
            val conflicts = mutableListOf<ImportConflict>()

            files.forEach { file ->
                val song = parseFile(file) ?: return@forEach
                val hash = SongContentHasher.hash(song)
                when {
                    songRepository.findSongByContentHash(hash) != null -> identical++
                    songRepository.findSongByContentSignature(song) != null -> {
                        conflicts += ImportConflict(
                            key = hash,
                            title = song.title,
                            detail = song.author
                        )
                    }
                    else -> newSongs++
                }
            }

            ChordProImportPreview(
                total = files.size,
                newSongs = newSongs,
                identical = identical,
                conflicts = conflicts
            )
        }

    suspend operator fun invoke(
        files: List<ChordProFile>,
        policies: Map<String, ImportConflictPolicy> = emptyMap()
    ): ChordProImportResult = withContext(computeDispatcher) {
        var created = 0
        var reused = 0
        var overwritten = 0
        val referencedCategories = linkedSetOf<String>()

        files.forEach { file ->
            val song = parseFile(file) ?: return@forEach

            val hash = SongContentHasher.hash(song)
            val identical = songRepository.findSongByContentHash(hash)
            if (identical != null) {
                reused++
            } else {
                // ¿Es el mismo canto (título/autor/número) con otro contenido?
                val sameSong = songRepository.findSongByContentSignature(song)
                if (sameSong == null) {
                    songRepository.saveSong(song)
                    created++
                } else {
                    when (policies[hash] ?: ImportConflictPolicy.OVERWRITE) {
                        ImportConflictPolicy.OVERWRITE -> {
                            songRepository.overwriteSong(song.copy(id = sameSong.id))
                            overwritten++
                        }
                        ImportConflictPolicy.KEEP_BOTH -> {
                            songRepository.saveSong(song)
                            created++
                        }
                        ImportConflictPolicy.SKIP -> reused++
                    }
                }
            }

            referencedCategories += song.defaultLiturgicalMoments
            referencedCategories += song.defaultLiturgicalTimes
        }

        val categoriesCreated = if (referencedCategories.isNotEmpty()) {
            ensureCategoriesForImportUseCase(referencedCategories).totalCreated
        } else {
            0
        }

        ChordProImportResult(
            songsCreated = created,
            songsReused = reused,
            songsOverwritten = overwritten,
            categoriesCreated = categoriesCreated
        )
    }

    /** Parsea un fichero ChordPro; devuelve `null` si está vacío. */
    private fun parseFile(file: ChordProFile): Song? {
        if (file.content.isBlank()) return null
        val fallbackTitle = file.fileName
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: "Sin título"
        return ChordProParser.parse(
            chordProContent = file.content,
            songId = generateId(),
            fallbackTitle = fallbackTitle
        )
    }
}
