package com.grafitto.laudate.web.data

import com.grafitto.laudate.domain.backup.BackupSerializer
import com.grafitto.laudate.domain.backup.toDomain
import com.grafitto.laudate.domain.backup.toDomainCategories
import com.grafitto.laudate.domain.backup.toDomainSongs
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.attachment.AttachmentKind
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.platform.nowMillis
import com.grafitto.laudate.domain.rhythm.GuitarPatternLibrary
import com.grafitto.laudate.domain.transfer.BundleRhythmPattern
import com.grafitto.laudate.domain.transfer.bundlePatternOf
import com.grafitto.laudate.domain.transfer.toBundleTablature
import com.grafitto.laudate.domain.transfer.toDomain as rhythmToDomain
import com.grafitto.laudate.domain.transfer.toDomain as tablatureToDomain
import com.grafitto.laudate.domain.util.diacriticsFold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Estado compartido de la web, en memoria y persistido en `localStorage` — ADR-023.
 *
 * Hace de «base de datos» de la versión web: el catálogo semilla es **inmutable** (viaja con la
 * aplicación y se versiona con ella) y encima se aplican las capas del usuario:
 *
 * - `userSongs`: canciones propias y **sobrescrituras** de canciones del catálogo (mismo `id`).
 * - `favorites`: ids marcados como favoritos (no se reescribe el catálogo entero).
 * - `deleted`: ids del catálogo que el usuario ha borrado (no se pueden quitar del fichero).
 * - `celebrations`, `categories`: repertorios y categorías tal cual, en JSON de respaldo.
 * - `tablatures`: las tablaturas de cada canto (ADR-017), también como JSON de respaldo.
 * - `attachments`: los **metadatos** de los adjuntos; los bytes de los ficheros viven en
 *   IndexedDB (ver `WebAttachmentStore`).
 *
 * Todas las escrituras son síncronas a propósito: en el navegador `localStorage` es síncrono y la
 * alternativa (IndexedDB) obligaría a poner `suspend` en todas partes sin ganar nada, porque el
 * volumen de datos propios de un usuario es de kilobytes.
 */
class WebStore {

    private val _songs = MutableStateFlow<List<Song>>(emptyList())

    /** Catálogo vivo: semilla + capas del usuario ya aplicadas. */
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _categories = MutableStateFlow<List<LiturgicalCategory>>(emptyList())
    val categories: StateFlow<List<LiturgicalCategory>> = _categories.asStateFlow()

    private val _celebrations = MutableStateFlow<List<Celebration>>(emptyList())
    val celebrations: StateFlow<List<Celebration>> = _celebrations.asStateFlow()

    private val _tablatures = MutableStateFlow<List<Tablature>>(emptyList())

    /** Tablaturas guardadas en este navegador (ADR-017). */
    val tablatures: StateFlow<List<Tablature>> = _tablatures.asStateFlow()

    private val _attachments = MutableStateFlow<List<SongAttachment>>(emptyList())

    /** Adjuntos de todos los cantos (los ficheros, en IndexedDB). */
    val attachments: StateFlow<List<SongAttachment>> = _attachments.asStateFlow()

    private val _patterns = MutableStateFlow(GuitarPatternLibrary.builtInPatterns)

    /** Patrones rítmicos: los integrados del Dominio más los que ha creado el usuario. */
    val patterns: StateFlow<List<RhythmPattern>> = _patterns.asStateFlow()

    private var seedSongs: List<Song> = emptyList()
    private var seedIds: Set<String> = emptySet()
    private val userSongs = LinkedHashMap<String, Song>()
    private val favorites = LinkedHashSet<String>()
    private val deleted = LinkedHashSet<String>()
    private val customPatterns = LinkedHashMap<String, RhythmPattern>()

    // ---------------------------------------------------------------------------------------
    // Arranque
    // ---------------------------------------------------------------------------------------

    /**
     * Carga lo que el usuario tenía guardado. Se llama **antes** de [hydrate] y es la única parte
     * del arranque que es síncrona (kilobytes de JSON).
     */
    fun restore() {
        WebStorage.read(KEY_FAVORITES)?.split(',')?.filter { it.isNotBlank() }?.forEach(favorites::add)
        WebStorage.read(KEY_DELETED)?.split(',')?.filter { it.isNotBlank() }?.forEach(deleted::add)
        readSongs(KEY_SONGS)?.forEach { userSongs[it.id] = it }

        val restoredCelebrations = readCelebrations(KEY_CELEBRATIONS).orEmpty()
        if (restoredCelebrations.isNotEmpty()) _celebrations.value = restoredCelebrations

        val restoredCategories = readCategories(KEY_CATEGORIES).orEmpty()
        if (restoredCategories.isNotEmpty()) _categories.value = restoredCategories

        val restoredTablatures = readTablatures(KEY_TABLATURES).orEmpty()
        if (restoredTablatures.isNotEmpty()) _tablatures.value = restoredTablatures

        val restoredAttachments = readAttachments(KEY_ATTACHMENTS).orEmpty()
        if (restoredAttachments.isNotEmpty()) _attachments.value = restoredAttachments

        readPatterns(KEY_PATTERNS)?.forEach { customPatterns[it.id] = it }
        if (customPatterns.isNotEmpty()) recomputePatterns()
    }

    /**
     * Incorpora el catálogo semilla una vez descargado y parseado, y recalcula lo que ve la UI.
     *
     * El catálogo semilla se lee del recurso de la aplicación, no de `localStorage`: son ~18 MB y
     * no caben en la cuota del navegador.
     */
    fun hydrate(seedSongs: List<Song>, seedCategories: List<LiturgicalCategory>) {
        this.seedSongs = seedSongs
        this.seedIds = seedSongs.mapTo(HashSet(seedSongs.size)) { it.id }

        // Categorías: las del usuario mandan (puede haberlas renombrado), pero un catálogo más
        // nuevo puede traer categorías que aquí todavía no existen: se añaden las que falten.
        val byId = _categories.value.associateBy { it.id }.toMutableMap()
        seedCategories.forEach { seed -> if (!byId.containsKey(seed.id)) byId[seed.id] = seed }
        _categories.value = byId.values.sortedWith(
            compareBy<LiturgicalCategory> { it.displayOrder }.thenBy { diacriticsFold(it.name) }
        )

        recomputeSongs()
    }

    // ---------------------------------------------------------------------------------------
    // Canciones
    // ---------------------------------------------------------------------------------------

    /** Guarda (crea o sobrescribe) una canción. Devuelve `true` si es nueva. */
    fun saveSong(song: Song): Boolean {
        val isNew = !seedIds.contains(song.id) && !userSongs.containsKey(song.id)
        userSongs[song.id] = song.copy(isFavorite = song.isFavorite || favorites.contains(song.id))
        deleted.remove(song.id)
        persistSongs()
        recomputeSongs()
        return isNew
    }

    fun saveSongs(songs: List<Song>) {
        songs.forEach { userSongs[it.id] = it }
        persistSongs()
        recomputeSongs()
    }

    fun setFavorite(songId: String, isFavorite: Boolean) {
        if (isFavorite) favorites.add(songId) else favorites.remove(songId)
        WebStorage.write(KEY_FAVORITES, favorites.joinToString(","))
        recomputeSongs()
    }

    fun isFavorite(songId: String): Boolean = favorites.contains(songId)

    fun deleteSong(songId: String) {
        userSongs.remove(songId)
        if (seedIds.contains(songId)) deleted.add(songId)
        persistSongs()
        WebStorage.write(KEY_DELETED, deleted.joinToString(","))
        recomputeSongs()
    }

    fun updateContentHash(songId: String, hash: String) {
        val current = _songs.value.firstOrNull { it.id == songId } ?: return
        if (current.contentHash == hash) return
        userSongs[songId] = current.copy(contentHash = hash)
        persistSongs()
        recomputeSongs()
    }

    /**
     * Renombra una etiqueta litúrgica en todas las canciones (momentos y tiempos por defecto).
     *
     * @return número de canciones modificadas.
     */
    fun renameCategoryTag(oldName: String, newName: String): Int {
        val old = diacriticsFold(oldName)
        val new = diacriticsFold(newName)
        if (old == new) return 0
        var changed = 0
        _songs.value.forEach { song ->
            val moments = song.defaultLiturgicalMoments
            val times = song.defaultLiturgicalTimes
            val renamedMoments = moments.map { if (diacriticsFold(it) == old) newName else it }
            val renamedTimes = times.map { if (diacriticsFold(it) == old) newName else it }
            if (renamedMoments != moments || renamedTimes != times) {
                userSongs[song.id] = song.copy(
                    defaultLiturgicalMoments = renamedMoments,
                    defaultLiturgicalTimes = renamedTimes,
                    updatedAt = nowMillis()
                )
                changed++
            }
        }
        if (changed > 0) {
            persistSongs()
            recomputeSongs()
        }
        return changed
    }

    /** Canción del catálogo vivo, o `null`. */
    fun findSong(songId: String): Song? = _songs.value.firstOrNull { it.id == songId }

    // ---------------------------------------------------------------------------------------
    // Categorías
    // ---------------------------------------------------------------------------------------

    fun saveCategory(category: LiturgicalCategory) {
        _categories.value = _categories.value.filterNot { it.id == category.id } + category
        persistCategories()
    }

    fun saveCategories(categories: List<LiturgicalCategory>): Int {
        if (categories.isEmpty()) return 0
        val ids = categories.mapTo(HashSet(categories.size)) { it.id }
        val kept = _categories.value.filterNot { ids.contains(it.id) }
        _categories.value = kept + categories
        persistCategories()
        return categories.size
    }

    fun deleteCategory(categoryId: String) {
        _categories.value = _categories.value.filterNot { it.id == categoryId }
        persistCategories()
    }

    /** Crea las categorías que falten de la lista indicada. Devuelve los nombres creados. */
    fun ensureCategories(
        names: Collection<String>,
        kind: LiturgicalCategoryKind
    ): List<String> {
        val existing = _categories.value.mapTo(HashSet()) { diacriticsFold(it.name) }
        val created = names.mapNotNull { name ->
            val trimmed = name.trim()
            if (trimmed.isEmpty() || existing.contains(diacriticsFold(trimmed))) {
                null
            } else {
                LiturgicalCategory(
                    id = newId(),
                    name = trimmed,
                    displayOrder = _categories.value.size,
                    kind = kind
                )
            }
        }
        if (created.isNotEmpty()) {
            _categories.value = _categories.value + created
            persistCategories()
        }
        return created.map { it.name }
    }

    // ---------------------------------------------------------------------------------------
    // Repertorios
    // ---------------------------------------------------------------------------------------

    fun saveCelebration(celebration: Celebration) {
        val others = _celebrations.value.filterNot { it.id == celebration.id }
        _celebrations.value = (others + celebration).sortedByDescending { it.dateTimestamp }
        persistCelebrations()
    }

    fun deleteCelebration(celebrationId: String) {
        _celebrations.value = _celebrations.value.filterNot { it.id == celebrationId }
        persistCelebrations()
    }

    fun renameMomentInCelebrations(oldName: String, newName: String): Int {
        var changed = 0
        val updated = _celebrations.value.map { celebration ->
            val renamed = celebration.copy(
                items = celebration.items.map {
                    if (diacriticsFold(it.liturgicalMoment) == diacriticsFold(oldName)) {
                        it.copy(liturgicalMoment = newName)
                    } else {
                        it
                    }
                }
            )
            if (renamed != celebration) changed++
            renamed
        }
        if (changed > 0) {
            _celebrations.value = updated
            persistCelebrations()
        }
        return changed
    }

    /**
     * Copia completa de la biblioteca en el mismo JSON que las copias de seguridad de Android y
     * escritorio (`BackupPayload`): reimportable aquí o en la app.
     */
    fun exportLibraryJson(): String = BackupSerializer.exportToJson(
        songs = _songs.value,
        celebrations = _celebrations.value,
        categories = _categories.value,
        rhythms = _patterns.value,
        tablatures = _tablatures.value
    )

    // ---------------------------------------------------------------------------------------
    // Patrones rítmicos propios (RF-306)
    // ---------------------------------------------------------------------------------------

    fun savePattern(pattern: RhythmPattern) {
        customPatterns[pattern.id] = pattern.copy(isBuiltIn = false)
        persistPatterns()
        recomputePatterns()
    }

    fun deletePattern(patternId: String) {
        if (customPatterns.remove(patternId) == null) return
        persistPatterns()
        recomputePatterns()
    }

    private fun recomputePatterns() {
        _patterns.value = GuitarPatternLibrary.builtInPatterns + customPatterns.values
    }

    // ---------------------------------------------------------------------------------------
    // Adjuntos de un canto
    // ---------------------------------------------------------------------------------------

    fun saveAttachment(attachment: SongAttachment) {
        _attachments.value = _attachments.value.filterNot { it.id == attachment.id } + attachment
        persistAttachments()
    }

    fun deleteAttachment(attachmentId: String) {
        _attachments.value = _attachments.value.filterNot { it.id == attachmentId }
        persistAttachments()
    }

    fun deleteAttachmentsForSong(songId: String) {
        val remaining = _attachments.value.filterNot { it.songId == songId }
        if (remaining.size == _attachments.value.size) return
        _attachments.value = remaining
        persistAttachments()
    }

    // ---------------------------------------------------------------------------------------
    // Tablaturas (ADR-017)
    // ---------------------------------------------------------------------------------------

    fun saveTablature(tablature: Tablature) {
        _tablatures.value = _tablatures.value.filterNot { it.id == tablature.id } + tablature
        persistTablatures()
    }

    fun deleteTablature(tablatureId: String) {
        _tablatures.value = _tablatures.value.filterNot { it.id == tablatureId }
        persistTablatures()
    }

    fun deleteTablaturesForSong(songId: String) {
        val remaining = _tablatures.value.filterNot { it.songId == songId }
        if (remaining.size == _tablatures.value.size) return
        _tablatures.value = remaining
        persistTablatures()
    }

    // ---------------------------------------------------------------------------------------
    // Persistencia
    // ---------------------------------------------------------------------------------------

    private fun recomputeSongs() {
        val merged = ArrayList<Song>(seedSongs.size + userSongs.size)
        seedSongs.forEach { song ->
            if (deleted.contains(song.id)) return@forEach
            val own = userSongs[song.id]
            val favorite = favorites.contains(song.id)
            merged += when {
                own != null -> own.copy(isFavorite = favorite)
                favorite && !song.isFavorite -> song.copy(isFavorite = true)
                else -> song
            }
        }
        userSongs.values.forEach { song -> if (!seedIds.contains(song.id)) merged += song }
        _songs.value = merged
    }

    private fun persistSongs() {
        // Las canciones propias sobrescriben las del catálogo inicial: se guardan con el mismo
        // serializador que los respaldos, así que un `.json` exportado aquí se restaura en Android
        // sin conversión alguna.
        WebStorage.write(
            KEY_SONGS,
            BackupSerializer.exportToJson(
                songs = userSongs.values.toList(),
                celebrations = emptyList(),
                categories = emptyList()
            )
        )
    }

    private fun persistCelebrations() {
        WebStorage.write(
            KEY_CELEBRATIONS,
            BackupSerializer.exportToJson(
                songs = emptyList(),
                celebrations = _celebrations.value,
                categories = emptyList()
            )
        )
    }

    private fun persistCategories() {
        WebStorage.write(
            KEY_CATEGORIES,
            BackupSerializer.exportToJson(
                songs = emptyList(),
                celebrations = emptyList(),
                categories = _categories.value
            )
        )
    }

    private fun persistAttachments() {
        val stored = _attachments.value.map { attachment ->
            StoredAttachment(
                id = attachment.id,
                songId = attachment.songId,
                kind = attachment.kind.name,
                displayName = attachment.displayName,
                uri = attachment.uri,
                mimeType = attachment.mimeType,
                sizeBytes = attachment.sizeBytes,
                createdAt = attachment.createdAt
            )
        }
        WebStorage.write(
            KEY_ATTACHMENTS,
            attachmentsJson.encodeToString(storedAttachmentsSerializer, stored)
        )
    }

    private fun persistTablatures() {
        WebStorage.write(
            KEY_TABLATURES,
            BackupSerializer.exportToJson(
                songs = emptyList(),
                celebrations = emptyList(),
                categories = emptyList(),
                tablatures = _tablatures.value
            )
        )
    }

    private fun persistPatterns() {
        val stored = customPatterns.map { (id, pattern) -> StoredRhythm(id, pattern.bundlePatternOf()) }
        WebStorage.write(KEY_PATTERNS, rhythmsJson.encodeToString(storedRhythmsSerializer, stored))
    }

    private fun readPatterns(key: String): List<RhythmPattern>? = WebStorage.read(key)?.let { text ->
        runCatching<List<StoredRhythm>> {
            rhythmsJson.decodeFromString(storedRhythmsSerializer, text)
        }.getOrNull()?.map { stored -> stored.pattern.rhythmToDomain(stored.id) }
    }

    private fun readSongs(key: String): List<Song>? = WebStorage.read(key)?.let { text ->
        runCatching { BackupSerializer.importFromJson(text).toDomainSongs() }.getOrNull()
    }

    private fun readCelebrations(key: String): List<Celebration>? = WebStorage.read(key)?.let { text ->
        runCatching { BackupSerializer.importFromJson(text).celebrations.map { it.toDomain() } }
            .getOrNull()
    }

    private fun readAttachments(key: String): List<SongAttachment>? = WebStorage.read(key)?.let { text ->
        runCatching<List<StoredAttachment>> {
            attachmentsJson.decodeFromString(storedAttachmentsSerializer, text)
        }.getOrNull()?.map { stored ->
            SongAttachment(
                id = stored.id,
                songId = stored.songId,
                kind = runCatching { AttachmentKind.valueOf(stored.kind) }
                    .getOrDefault(AttachmentKind.fromMimeType(stored.mimeType)),
                displayName = stored.displayName,
                uri = stored.uri,
                mimeType = stored.mimeType,
                sizeBytes = stored.sizeBytes,
                createdAt = stored.createdAt
            )
        }
    }

    private fun readTablatures(key: String): List<Tablature>? = WebStorage.read(key)?.let { text ->
        runCatching<List<Tablature>> {
            BackupSerializer.importFromJson(text).tablatures.mapNotNull { it.tablatureToDomain() }
        }.getOrNull()
    }

    private fun readCategories(key: String): List<LiturgicalCategory>? =
        WebStorage.read(key)?.let { text ->
            runCatching { BackupSerializer.importFromJson(text).toDomainCategories() }.getOrNull()
        }

    private companion object {
        const val KEY_SONGS = "songs.user"
        const val KEY_FAVORITES = "songs.favorites"
        const val KEY_DELETED = "songs.deleted"
        const val KEY_CELEBRATIONS = "celebrations"
        const val KEY_CATEGORIES = "categories"
        const val KEY_TABLATURES = "tablatures"
        const val KEY_ATTACHMENTS = "attachments"
        const val KEY_PATTERNS = "rhythms.custom"
    }
}

/** Adjunto tal cual se guarda en `localStorage` (los bytes del fichero están en IndexedDB). */
@Serializable
private data class StoredAttachment(
    val id: String,
    val songId: String,
    val kind: String,
    val displayName: String,
    val uri: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val createdAt: Long = 0L
)

private val storedAttachmentsSerializer = ListSerializer(StoredAttachment.serializer())

private val attachmentsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Patrón propio tal cual se guarda en `localStorage`: conserva su id estable. */
@Serializable
private data class StoredRhythm(
    val id: String,
    val pattern: BundleRhythmPattern
)

private val storedRhythmsSerializer = ListSerializer(StoredRhythm.serializer())

private val rhythmsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
