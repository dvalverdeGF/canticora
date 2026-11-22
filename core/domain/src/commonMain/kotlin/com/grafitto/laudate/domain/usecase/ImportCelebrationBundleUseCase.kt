package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.parser.ChordProParser
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.repository.CelebrationRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import com.grafitto.laudate.domain.transfer.BundleCelebration
import com.grafitto.laudate.domain.transfer.BundleSong
import com.grafitto.laudate.domain.transfer.CelebrationBundle
import com.grafitto.laudate.domain.transfer.ImportConflict
import com.grafitto.laudate.domain.transfer.ImportConflictPolicy
import com.grafitto.laudate.domain.transfer.toItems

/**
 * Resultado de importar un repertorio compartido, con el detalle que se muestra al usuario.
 */
data class CelebrationImportResult(
    val celebrationId: String,
    val celebrationName: String,
    /** Cantos que no estaban en el catálogo y se han creado. */
    val songsCreated: Int,
    /** Cantos que ya estaban en el catálogo y **no** se han tocado. */
    val songsReused: Int,
    /** Cantos que ya existían con contenido distinto y se han **actualizado** con permiso. */
    val songsOverwritten: Int = 0,
    /** Cantos del repertorio (una celebración puede repetir el mismo canto). */
    val celebrationItemCount: Int
) {
    val songCount: Int get() = songsCreated + songsReused + songsOverwritten
}

/**
 * Inspección previa de un repertorio **sin escribir nada**: qué cantos trae, cuántos son nuevos,
 * cuántos idénticos y cuáles ya existen con cambios (pendientes de permiso).
 */
data class CelebrationImportPreview(
    val celebrationName: String,
    val total: Int,
    val newSongs: Int,
    val identical: Int,
    val conflicts: List<ImportConflict>
) {
    val hasConflicts: Boolean get() = conflicts.isNotEmpty()
}

/**
 * Caso de uso: importar en la app un **repertorio de misa/evento** compartido como archivo.
 *
 * Reglas:
 *  - Un canto **idéntico** (misma huella de contenido) se **reutiliza** sin tocarlo.
 *  - Un canto que ya existe **con cambios** (misma canción, contenido distinto) es un conflicto:
 *    con permiso se puede **sobrescribir**, conservar ambos u omitir (por defecto, sobrescribir).
 *    La aplicación llama antes a [inspect] para poder preguntar con permiso.
 *  - El repertorio **siempre** se crea como una celebración nueva.
 *
 * Kotlin puro: el texto del archivo llega ya leído desde la capa de datos/UI.
 */
class ImportCelebrationBundleUseCase(
    private val songRepository: SongRepository,
    private val celebrationRepository: CelebrationRepository,
    /** Repositorio de tablaturas (ADR-017, *Feature T*). Opcional por retrocompatibilidad. */
    private val tablatureRepository: TablatureRepository? = null,
    private val generateId: () -> String = { newId() }
) {

    /** Analiza el repertorio **sin escribir nada**, para poder pedir permiso antes de sobrescribir. */
    suspend fun inspect(bundle: CelebrationBundle): CelebrationImportPreview {
        val songs = bundle.songs.map { parseSong(it) }
        var newSongs = 0
        var identical = 0
        val conflicts = mutableListOf<ImportConflict>()

        songs.forEach { song ->
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

        return CelebrationImportPreview(
            celebrationName = bundle.celebration.name,
            total = songs.size,
            newSongs = newSongs,
            identical = identical,
            conflicts = conflicts
        )
    }

    suspend operator fun invoke(
        bundle: CelebrationBundle,
        policies: Map<String, ImportConflictPolicy> = emptyMap()
    ): CelebrationImportResult {
        val songIdsByIndex = mutableMapOf<Int, String>()
        // Caché por huella: si el repertorio repite un canto, se resuelve una sola vez.
        val resolvedByHash = mutableMapOf<String, String>()
        var created = 0
        var reused = 0
        var overwritten = 0

        bundle.songs.forEachIndexed { index, bundleSong ->
            val song = parseSong(bundleSong)
            val hash = SongContentHasher.hash(song)

            val songId = resolvedByHash[hash] ?: run {
                val identicalSong = songRepository.findSongByContentHash(hash)
                if (identicalSong != null) {
                    reused++
                    identicalSong.id
                } else {
                    val sameSong = songRepository.findSongByContentSignature(song)
                    if (sameSong == null) {
                        val newId = generateId()
                        songRepository.saveSong(song.copy(id = newId))
                        created++
                        newId
                    } else {
                        when (policies[hash] ?: ImportConflictPolicy.OVERWRITE) {
                            ImportConflictPolicy.OVERWRITE -> {
                                songRepository.overwriteSong(song.copy(id = sameSong.id))
                                overwritten++
                                sameSong.id
                            }
                            ImportConflictPolicy.KEEP_BOTH -> {
                                val newId = generateId()
                                songRepository.saveSong(song.copy(id = newId))
                                created++
                                newId
                            }
                            ImportConflictPolicy.SKIP -> {
                                reused++
                                sameSong.id
                            }
                        }
                    }
                }
            }
            resolvedByHash[hash] = songId
            songIdsByIndex[index] = songId

            // Tablaturas del canto (ADR-017, Feature T): se reasocian al canto resuelto.
            importTablatures(bundleSong.tablatures, songId)
        }

        val celebrationId = generateId()
        val celebration = Celebration(
            id = celebrationId,
            name = bundle.celebration.name.ifBlank { "Repertorio importado" },
            dateTimestamp = bundle.celebration.dateTimestamp,
            description = bundle.celebration.description,
            location = bundle.celebration.location,
            celebrationType = bundle.celebration.type.ifBlank { "Misa" },
            items = bundle.celebration.toItems(celebrationId, songIdsByIndex)
        )
        celebrationRepository.saveCelebration(celebration)

        return CelebrationImportResult(
            celebrationId = celebrationId,
            celebrationName = celebration.name,
            songsCreated = created,
            songsReused = reused,
            songsOverwritten = overwritten,
            celebrationItemCount = celebration.items.size
        )
    }

    /** Reconstruye las tablaturas del canto y las reasocia al id resuelto (ADR-017, Feature T). */
    private suspend fun importTablatures(tablatures: List<String>, songId: String) {
        val repo = tablatureRepository ?: return
        tablatures.forEach { json ->
            val decoded = runCatching { TablatureSerializer.decode(json) }.getOrNull() ?: return@forEach
            repo.saveTablature(decoded.copy(songId = songId))
        }
    }

    /** Reconstruye el canto desde su ChordPro, conservando la identificación del archivo. */
    private fun parseSong(bundleSong: BundleSong): Song {
        val parsed = ChordProParser.parse(
            chordProContent = bundleSong.chordPro,
            fallbackTitle = bundleSong.title,
            fallbackKey = bundleSong.originalKey
        )
        return parsed.copy(
            title = bundleSong.title.ifBlank { parsed.title },
            number = bundleSong.number ?: parsed.number,
            author = bundleSong.author ?: parsed.author,
            originalKey = bundleSong.originalKey
        )
    }
}
