package com.grafitto.laudate.web.data

import com.grafitto.laudate.domain.attachment.AttachmentStore
import com.grafitto.laudate.domain.model.attachment.AttachmentKind
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.repository.AttachmentRepository
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.CelebrationRepository
import com.grafitto.laudate.domain.repository.RhythmPatternRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.util.diacriticsFold
import com.grafitto.laudate.web.WebPlatform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Implementaciones web de los repositorios de Dominio — ADR-023.
 *
 * La web **no** usa `:core:data` (Room + ficheros): estas clases cumplen exactamente las mismas
 * interfaces sobre [WebStore] (`localStorage`), de modo que los casos de uso y los ViewModels son
 * los mismos que en Android y escritorio. Nada de la UI sabe que aquí no hay base de datos.
 */

/** Catálogo de canciones sobre el estado en memoria + `localStorage`. */
class WebSongRepository(private val store: WebStore) : SongRepository {

    override fun getAllSongs(): Flow<List<Song>> = store.songs

    override fun getAllSongsWithLyrics(): Flow<List<Song>> = store.songs

    override suspend fun getSongCount(): Int = store.songs.value.size

    override fun getFavoriteSongs(): Flow<List<Song>> =
        store.songs.map { songs -> songs.filter { it.isFavorite } }

    override suspend fun getSongsByIds(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val byId = store.songs.value.associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    override suspend fun getSongStubsByIds(ids: List<String>): List<Song> =
        getSongsByIds(ids).map { it.copy(sections = emptyList()) }

    override fun getSongById(id: String): Flow<Song?> =
        store.songs.map { songs -> songs.firstOrNull { it.id == id } }

    override fun searchSongs(query: String): Flow<List<Song>> =
        store.songs.map { songs -> songs.filterMatching(query) }

    override suspend fun saveSong(song: Song) {
        store.saveSong(song)
    }

    override suspend fun overwriteSong(song: Song) {
        store.saveSong(song)
    }

    override suspend fun saveSongs(songs: List<Song>) {
        store.saveSongs(songs)
    }

    override suspend fun setFavorite(songId: String, isFavorite: Boolean) {
        store.setFavorite(songId, isFavorite)
    }

    override suspend fun deleteSong(songId: String) {
        // El canto se lleva consigo lo que colgaba de él: en `localStorage` los metadatos y, en
        // IndexedDB, los bytes de sus adjuntos (ahí no hay borrado en cascada).
        store.deleteSong(songId)
        store.deleteAttachmentsForSong(songId)
        store.deleteTablaturesForSong(songId)
        WebPlatform.deleteAttachmentFilesForSong(songId)
    }

    override suspend fun renameCategoryTag(oldName: String, newName: String): Int =
        store.renameCategoryTag(oldName, newName)

    override suspend fun findSongByContentHash(hash: String): Song? =
        store.songs.value.firstOrNull { it.contentHash == hash }

    override suspend fun findSongByContentSignature(song: Song): Song? {
        val title = diacriticsFold(song.title).trim()
        if (title.isEmpty()) return null
        val author = song.author?.let { diacriticsFold(it).trim() }
        val number = song.number?.trim()?.takeIf { it.isNotEmpty() }
        return store.songs.value.firstOrNull { other ->
            other.id != song.id &&
                diacriticsFold(other.title).trim() == title &&
                other.author?.let { diacriticsFold(it).trim() } == author &&
                other.number?.trim()?.takeIf { it.isNotEmpty() } == number
        }
    }

    override suspend fun getSongsMissingContentHash(): List<Song> =
        store.songs.value.filter { it.contentHash.isNullOrBlank() }

    override suspend fun updateContentHash(songId: String, hash: String) {
        store.updateContentHash(songId, hash)
    }

    /**
     * Búsqueda del catálogo sin SQL: se pliega el texto (sin tildes ni mayúsculas) y se exige que
     * **todas** las palabras de la consulta aparezcan en el título, el autor, el número o las
     * etiquetas litúrgicas. Es la misma semántica que la consulta FTS5 de Android.
     */
    private fun List<Song>.filterMatching(query: String): List<Song> {
        val terms = diacriticsFold(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return this
        return filter { song ->
            val haystack = diacriticsFold(
                buildString {
                    append(song.title).append(' ')
                    song.author?.let { append(it).append(' ') }
                    song.number?.let { append(it).append(' ') }
                    song.defaultLiturgicalMoments.forEach { append(it).append(' ') }
                    song.defaultLiturgicalTimes.forEach { append(it).append(' ') }
                }
            )
            terms.all { haystack.contains(it) }
        }
    }
}

/** Categorías litúrgicas (momentos, tiempos, colecciones, etiquetas). */
class WebCategoryRepository(private val store: WebStore) : CategoryRepository {

    override fun getAllCategories(): Flow<List<LiturgicalCategory>> = store.categories

    override suspend fun getAllCategoriesSnapshot(): List<LiturgicalCategory> =
        store.categories.value

    override suspend fun getCategoryById(id: String): LiturgicalCategory? =
        store.categories.value.firstOrNull { it.id == id }

    override suspend fun saveCategory(category: LiturgicalCategory) {
        store.saveCategory(category)
    }

    override suspend fun updateCategory(category: LiturgicalCategory) {
        store.saveCategory(category)
    }

    override suspend fun saveCategories(categories: List<LiturgicalCategory>): Int =
        store.saveCategories(categories)

    override suspend fun deleteCategory(categoryId: String) {
        store.deleteCategory(categoryId)
    }

    /** El catálogo semilla ya trae las categorías estándar: no hay nada que sembrar. */
    override suspend fun seedStandardCategoriesIfEmpty() = Unit

    override suspend fun ensureCategories(
        names: Collection<String>,
        kind: LiturgicalCategoryKind
    ): List<String> = store.ensureCategories(names, kind)
}

/** Repertorios de misa y eventos. */
class WebCelebrationRepository(private val store: WebStore) : CelebrationRepository {

    override fun getAllCelebrations(): Flow<List<Celebration>> = store.celebrations

    override fun getCelebrationById(id: String): Flow<Celebration?> =
        store.celebrations.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun saveCelebration(celebration: Celebration) {
        store.saveCelebration(celebration)
    }

    override suspend fun deleteCelebration(celebrationId: String) {
        store.deleteCelebration(celebrationId)
    }

    override suspend fun restoreCelebration(celebration: Celebration) {
        store.saveCelebration(celebration)
    }

    override suspend fun renameMoment(oldName: String, newName: String): Int =
        store.renameMomentInCelebrations(oldName, newName)
}

/**
 * Tablaturas guardadas en el navegador.
 *
 * Una tablatura es un documento propio del canto (ADR-017) y no necesita ningún servicio del
 * sistema: se guardan como JSON en `localStorage` junto al resto de datos del usuario, así que el
 * editor de tablaturas funciona igual que en Android y escritorio (menos la importación de
 * partituras MusicXML, que necesita leer un fichero binario).
 */
class WebTablatureRepository(private val store: WebStore) : TablatureRepository {

    override fun observeTablaturesForSong(songId: String): Flow<List<Tablature>> =
        store.tablatures.map { list -> list.filter { it.songId == songId } }

    override suspend fun getTablaturesForSong(songId: String): List<Tablature> =
        store.tablatures.value.filter { it.songId == songId }

    override suspend fun getTablatureById(id: String): Tablature? =
        store.tablatures.value.firstOrNull { it.id == id }

    override suspend fun getAllTablatures(): List<Tablature> = store.tablatures.value

    override suspend fun saveTablature(tablature: Tablature) = store.saveTablature(tablature)

    override suspend fun deleteTablature(id: String) = store.deleteTablature(id)

    override suspend fun deleteTablaturesForSong(songId: String) =
        store.deleteTablaturesForSong(songId)

    override fun observeSongIdsWithTablatures(): Flow<Set<String>> =
        store.tablatures.map { list -> list.mapTo(HashSet()) { it.songId } }
}

/**
 * Adjuntos de un canto.
 *
 * Los **metadatos** (nombre, tipo, tamaño, URL o clave del fichero) se guardan con el resto de datos
 * del usuario; los **bytes** de los ficheros viven en IndexedDB ([WebAttachmentStore]). Los enlaces
 * externos solo necesitan la URL, así que funcionan igual que en Android.
 */
class WebAttachmentRepository(private val store: WebStore) : AttachmentRepository {

    override fun getAttachments(songId: String): Flow<List<SongAttachment>> =
        store.attachments.map { list -> list.filter { it.songId == songId } }

    override suspend fun getAttachmentsForSongs(songIds: List<String>): List<SongAttachment> {
        if (songIds.isEmpty()) return emptyList()
        val ids = songIds.toSet()
        return store.attachments.value.filter { it.songId in ids }
    }

    override suspend fun addAttachment(attachment: SongAttachment) =
        store.saveAttachment(attachment)

    override suspend fun deleteAttachment(attachmentId: String) =
        store.deleteAttachment(attachmentId)

    override suspend fun deleteAttachmentsForSong(songId: String) =
        store.deleteAttachmentsForSong(songId)

    override suspend fun countForSong(songId: String): Int =
        store.attachments.value.count { it.songId == songId }

    override fun observeSongIdsWithAttachments(): Flow<Set<String>> =
        store.attachments.map { list -> list.mapTo(HashSet()) { it.songId } }
}

/** Patrones rítmicos: los integrados del Dominio más los que crea el usuario. */
class WebRhythmPatternRepository(private val store: WebStore) : RhythmPatternRepository {

    override fun getAllPatterns(): Flow<List<RhythmPattern>> = store.patterns

    override fun getCustomPatterns(): Flow<List<RhythmPattern>> =
        store.patterns.map { patterns -> patterns.filterNot { it.isBuiltIn } }

    override suspend fun savePattern(pattern: RhythmPattern) = store.savePattern(pattern)

    override suspend fun deletePattern(patternId: String) = store.deletePattern(patternId)

    override suspend fun restorePattern(pattern: RhythmPattern) = store.savePattern(pattern)
}

/**
 * Almacén de ficheros adjuntos sobre **IndexedDB**.
 *
 * El fichero se elige con `WebPlatform.pickAttachmentFile`, que ya lo deja guardado y entrega un
 * `idb://<clave>`; aquí solo se crea el modelo de Dominio con ese `uri` y su tamaño. La clave lleva
 * delante el `songId`, de modo que borrar un canto borra sus ficheros sin recorrer nada más.
 */
class WebAttachmentStore : AttachmentStore {

    override suspend fun importFile(
        songId: String,
        sourceUri: String,
        displayName: String,
        mimeType: String?
    ): SongAttachment {
        require(sourceUri.startsWith("idb://")) {
            "El fichero no está en el almacén del navegador."
        }
        val stored = WebPlatform.readAttachment(WebPlatform.keyOf(sourceUri))
            ?: error("El fichero se perdió al guardarlo.")
        return SongAttachment(
            id = newId(),
            songId = songId,
            kind = AttachmentKind.fromMimeType(mimeType ?: stored.mimeType),
            displayName = displayName.ifBlank { stored.fileName },
            uri = sourceUri,
            mimeType = mimeType ?: stored.mimeType.takeIf { it.isNotBlank() },
            sizeBytes = stored.sizeBytes
        )
    }

    override suspend fun deleteFile(attachment: SongAttachment) {
        if (attachment.isLink) return
        WebPlatform.deleteAttachmentFile(attachment.uri)
    }

    override suspend fun deleteFilesForSong(songId: String) {
        WebPlatform.deleteAttachmentFilesForSong(songId)
    }

    override suspend fun exists(attachment: SongAttachment): Boolean =
        if (attachment.isLink) true else WebPlatform.attachmentFileExists(attachment.uri)

    override suspend fun exportFile(
        attachment: SongAttachment,
        destinationUri: String
    ): Result<Unit> = runCatching {
        if (attachment.isLink) {
            WebPlatform.openUrl(attachment.uri)
            return@runCatching
        }
        val stored = WebPlatform.readAttachment(WebPlatform.keyOf(attachment.uri))
            ?: error("El fichero ya no está en el almacén del navegador.")
        WebPlatform.downloadBase64(
            fileName = attachment.displayName,
            mimeType = attachment.mimeType ?: stored.mimeType,
            base64 = stored.base64
        )
    }
}
