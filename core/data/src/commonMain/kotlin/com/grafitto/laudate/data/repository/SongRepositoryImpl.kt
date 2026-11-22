package com.grafitto.laudate.data.repository

import com.grafitto.laudate.data.local.converters.RoomConverters
import com.grafitto.laudate.data.local.dao.SongDao
import com.grafitto.laudate.data.local.entity.ChordAnchorEntity
import com.grafitto.laudate.data.local.entity.SongEntity
import com.grafitto.laudate.data.local.entity.SongSectionEntity
import com.grafitto.laudate.data.mapper.SongMapper
import com.grafitto.laudate.domain.hashing.SongContentHasher
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.util.diacriticsFold
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implementación de SongRepository asegurando Local-First (Room como SSOT)
 * y concurrencia sin bloquear el hilo principal.
 */
class SongRepositoryImpl(
    private val songDao: SongDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : SongRepository {

    private val converters = RoomConverters()

    override fun getAllSongs(): Flow<List<Song>> {
        return songDao.getAllSongsFlow()
            .map { list -> list.map { SongMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override fun getAllSongsWithLyrics(): Flow<List<Song>> {
        return songDao.getAllSongsWithDetailsFlow()
            .map { list -> list.map { SongMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override fun getFavoriteSongs(): Flow<List<Song>> {
        return songDao.getFavoriteSongsFlow()
            .map { list -> list.map { SongMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getSongCount(): Int = withContext(ioDispatcher) {
        songDao.getSongCount()
    }

    override suspend fun getSongsByIds(ids: List<String>): List<Song> = withContext(ioDispatcher) {
        if (ids.isEmpty()) return@withContext emptyList()
        songDao.getSongsWithDetailsByIds(ids.distinct())
            .map { relation -> SongMapper.toDomain(relation) }
    }

    override suspend fun getSongStubsByIds(ids: List<String>): List<Song> = withContext(ioDispatcher) {
        if (ids.isEmpty()) return@withContext emptyList()
        songDao.getSongEntitiesByIds(ids.distinct()).map { SongMapper.toDomain(it) }
    }

    override fun getSongById(id: String): Flow<Song?> {
        return songDao.getSongWithDetailsByIdFlow(id)
            .map { relation -> relation?.let { SongMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override fun searchSongs(query: String): Flow<List<Song>> {
        return songDao.searchSongs(query)
            .map { list -> list.map { SongMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override suspend fun saveSong(song: Song) = withContext(ioDispatcher) {
        // La huella es un valor derivado del contenido: se recalcula al guardar para que un canto
        // editado deje de coincidir con su versión anterior y sí con cualquier copia de su texto actual.
        val hashed = song.copy(contentHash = SongContentHasher.hash(song))
        val (songEntity, sections, anchors) = SongMapper.toEntity(hashed)
        songDao.insertFullSong(songEntity, sections, anchors)
    }

    override suspend fun saveSongs(songs: List<Song>) = withContext(ioDispatcher) {
        if (songs.isEmpty()) return@withContext

        val songEntities = ArrayList<SongEntity>(songs.size)
        val sectionEntities = mutableListOf<SongSectionEntity>()
        val anchorEntities = mutableListOf<ChordAnchorEntity>()

        songs.forEach { song ->
            // La huella es un valor derivado: se recalcula SIEMPRE a partir del contenido actual
            // (el llamante no tiene por qué conocerla ni fijarla a mano).
            val hashed = song.copy(contentHash = SongContentHasher.hash(song))
            val (songEntity, sections, anchors) = SongMapper.toEntity(hashed)
            songEntities.add(songEntity)
            sectionEntities.addAll(sections)
            anchorEntities.addAll(anchors)
        }

        // Una sola transacción para todo el lote (importación de cancionero/respaldo, no edición interactiva).
        songDao.insertFullSongs(songEntities, sectionEntities, anchorEntities)
    }

    override suspend fun overwriteSong(song: Song) = withContext(ioDispatcher) {
        // La huella se recalcula igual que al guardar: es un valor derivado del contenido.
        val hashed = song.copy(contentHash = SongContentHasher.hash(song))
        val (songEntity, sections, anchors) = SongMapper.toEntity(hashed)
        songDao.updateSongInPlace(songEntity, sections, anchors)
    }

    override suspend fun setFavorite(songId: String, isFavorite: Boolean) = withContext(ioDispatcher) {
        songDao.updateFavoriteStatus(songId, isFavorite)
    }

    override suspend fun findSongByContentHash(hash: String): Song? = withContext(ioDispatcher) {
        songDao.getSongByContentHash(hash)?.let { SongMapper.toDomain(it) }
    }

    override suspend fun findSongByContentSignature(song: Song): Song? = withContext(ioDispatcher) {
        // Identidad musical: título + autor + número, normalizados (minúsculas y sin acentos).
        val byTitle = songDao.getSongsByTitle(song.title)
        byTitle.firstOrNull { candidate ->
            diacriticsFold(candidate.author.orEmpty()) == diacriticsFold(song.author.orEmpty()) &&
                diacriticsFold(candidate.number.orEmpty()) == diacriticsFold(song.number.orEmpty())
        }?.let { SongMapper.toDomain(it) }
    }

    override suspend fun getSongsMissingContentHash(): List<Song> = withContext(ioDispatcher) {
        songDao.getSongsMissingContentHash().map { SongMapper.toDomain(it) }
    }

    override suspend fun updateContentHash(songId: String, hash: String) = withContext(ioDispatcher) {
        songDao.updateContentHash(songId, hash)
    }

    override suspend fun renameCategoryTag(oldName: String, newName: String): Int = withContext(ioDispatcher) {
        val normalizedOld = normalizeTag(oldName)
        if (normalizedOld.isEmpty()) return@withContext 0

        val songs = songDao.getAllSongsFlow().firstOrNull().orEmpty()
        var modified = 0
        songs.forEach { entity ->
            val moments = converters.toListString(entity.defaultLiturgicalMomentsJson)
            val times = converters.toListString(entity.defaultLiturgicalTimesJson)
            val newMoments = moments.map { if (normalizeTag(it) == normalizedOld) newName else it }
            val newTimes = times.map { if (normalizeTag(it) == normalizedOld) newName else it }

            if (newMoments != moments) {
                songDao.updateSongMomentsJson(entity.id, converters.fromListString(newMoments))
            }
            if (newTimes != times) {
                songDao.updateSongTimesJson(entity.id, converters.fromListString(newTimes))
            }
            if (newMoments != moments || newTimes != times) modified++
        }
        modified
    }

    /** Normaliza una etiqueta igual que la búsqueda (minúsculas y sin acentos) para poder comparar. */
    private fun normalizeTag(text: String): String {
        return diacriticsFold(text.trim())
    }

    override suspend fun deleteSong(songId: String) = withContext(ioDispatcher) {
        val songWithDetails = songDao.getSongWithDetailsById(songId)
        if (songWithDetails != null) {
            songDao.deleteSong(songWithDetails.song)
        }
    }
}
