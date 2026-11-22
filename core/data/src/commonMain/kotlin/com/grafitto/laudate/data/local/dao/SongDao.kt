package com.grafitto.laudate.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.grafitto.laudate.data.local.entity.ChordAnchorEntity
import com.grafitto.laudate.data.local.entity.SongEntity
import com.grafitto.laudate.data.local.entity.SongSectionEntity
import com.grafitto.laudate.data.local.relation.SongWithDetailsRelation
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongsFlow(): Flow<List<SongEntity>>

    /**
     * Catálogo completo **con secciones y acordes**.
     *
     * Se usa para búsquedas que necesitan la letra (el buscador de cantos filtra también por
     * `raw_text_content`). Cargar las relaciones de ~1.000 cantos es aceptable porque el
     * resultado se calcula en memoria una sola vez por sesión de búsqueda.
     */
    @Transaction
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongsWithDetailsFlow(): Flow<List<SongWithDetailsRelation>>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCount(): Int

    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY title ASC")
    fun getFavoriteSongsFlow(): Flow<List<SongEntity>>

    @Transaction
    @Query("SELECT * FROM songs WHERE id = :songId")
    fun getSongWithDetailsByIdFlow(songId: String): Flow<SongWithDetailsRelation?>

    @Transaction
    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongWithDetailsById(songId: String): SongWithDetailsRelation?

    /**
     * Cantos concretos **con secciones y acordes**, en una sola consulta.
     *
     * Un repertorio necesita la letra completa de sus cantos para imprimir el PDF y para
     * compartir el archivo `.canticora`. Resolverlos aquí evita cargar el catálogo entero y
     * garantiza que la relación de secciones venga rellena (la consulta de la tabla `songs`
     * sola devuelve `sections` vacío).
     */
    @Transaction
    @Query("SELECT * FROM songs WHERE id IN (:songIds)")
    suspend fun getSongsWithDetailsByIds(songIds: List<String>): List<SongWithDetailsRelation>

    /**
     * Búsqueda por texto sobre título, autor y **número de canto** (citas tipo «el Aleluya 402»).
     * Es la consulta que usa el catálogo; el filtrado por categoría/favoritos se aplica en la UI.
     */
    @Query("""
        SELECT * FROM songs
        WHERE title LIKE '%' || :query || '%'
        OR author LIKE '%' || :query || '%'
        OR number LIKE '%' || :query || '%'
        ORDER BY title ASC
    """)
    fun searchSongs(query: String): Flow<List<SongEntity>>

    /**
     * Búsqueda de texto completo (FTS4). Se une por `rowid`/`docid`, nunca por `title`:
     * varias canciones comparten título en el cancionero y un join por título duplicaría filas.
     */
    @Query("""
        SELECT s.* FROM songs s
        JOIN songs_fts fts ON s.rowid = fts.docid
        WHERE songs_fts MATCH :matchQuery
        ORDER BY s.title ASC
    """)
    fun searchSongsFts(matchQuery: String): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)

    /**
     * Actualiza la cabecera de una canción **sin borrarla y volver a crearla**.
     *
     * Es imprescindible que sea un `UPDATE` y no un `INSERT OR REPLACE`: SQLite implementa el
     * `REPLACE` como `DELETE` + `INSERT`, y ese borrado dispararía las cascadas de `song_sections`
     * (perdiendo los anclajes) y de `song_attachments` (perdiendo los adjuntos del usuario). Además
     * chocaría con `celebration_items.song_id` (`ON DELETE RESTRICT`), que prohíbe borrar un canto
     * que esté en un repertorio y provoca el error «Foreign key constraint failed (1811)».
     */
    @Query(
        """
        UPDATE songs SET
            title = :title,
            original_key = :originalKey,
            number = :number,
            author = :author,
            rhythm = :rhythm,
            rhythm_pattern_id = :rhythmPatternId,
            tempo_bpm = :tempoBpm,
            tempo_marking = :tempoMarking,
            default_liturgical_moments_json = :momentsJson,
            default_liturgical_times_json = :timesJson,
            is_favorite = :isFavorite,
            capo = :capo,
            notes = :notes,
            content_hash = :contentHash,
            updated_at = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateSongHeader(
        id: String,
        title: String,
        originalKey: String,
        number: String?,
        author: String?,
        rhythm: String?,
        rhythmPatternId: String?,
        tempoBpm: Int?,
        tempoMarking: String?,
        momentsJson: String?,
        timesJson: String?,
        isFavorite: Boolean,
        capo: Int,
        notes: String?,
        contentHash: String?,
        updatedAt: Long
    )

    /** Ids de las canciones que ya existen (para decidir entre insertar o actualizar). */
    @Query("SELECT id FROM songs WHERE id IN (:ids)")
    suspend fun existingSongIds(ids: List<String>): List<String>

    /**
     * Cabeceras de las canciones indicadas, **sin sus secciones** (consulta ligera).
     * Sirve para comparar huellas y `updatedAt` antes de restaurar (resolución de conflictos).
     */
    @Query("SELECT * FROM songs WHERE id IN (:ids)")
    suspend fun getSongEntitiesByIds(ids: List<String>): List<SongEntity>

    /** Secciones de una canción; al borrarlas caen sus anclajes por la cascada de la FK. */
    @Query("DELETE FROM song_sections WHERE song_id = :songId")
    suspend fun deleteSectionsForSong(songId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<SongSectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnchors(anchors: List<ChordAnchorEntity>)

    @Transaction
    suspend fun insertFullSong(
        song: SongEntity,
        sections: List<SongSectionEntity>,
        anchors: List<ChordAnchorEntity>
    ) = upsertFullSong(song, sections, anchors)

    /**
     * Inserta o **actualiza** una canción completa preservando su identidad y sus hijos ajenos.
     *
     * Si la canción ya existe se actualiza la cabecera y se reemplazan únicamente sus secciones
     * (los anclajes caen por la cascada de `song_sections`, que es correcta porque pertenecen a la
     * sección). **Nunca** se borra la fila de `songs`, de modo que se conservan los adjuntos y los
     * vínculos con los repertorios.
     */
    @Transaction
    suspend fun upsertFullSong(
        song: SongEntity,
        sections: List<SongSectionEntity>,
        anchors: List<ChordAnchorEntity>
    ) {
        if (existingSongIds(listOf(song.id)).isNotEmpty()) {
            updateSongHeader(
                id = song.id,
                title = song.title,
                originalKey = song.originalKey,
                number = song.number,
                author = song.author,
                rhythm = song.rhythm,
                rhythmPatternId = song.rhythmPatternId,
                tempoBpm = song.tempoBpm,
                tempoMarking = song.tempoMarking,
                momentsJson = song.defaultLiturgicalMomentsJson,
                timesJson = song.defaultLiturgicalTimesJson,
                isFavorite = song.isFavorite,
                capo = song.capo,
                notes = song.notes,
                contentHash = song.contentHash,
                updatedAt = song.updatedAt
            )
            // Solo se reemplazan las secciones propias: los adjuntos y los vínculos con
            // repertorios de esta canción permanecen intactos.
            deleteSectionsForSong(song.id)
        } else {
            insertSong(song)
        }
        if (sections.isNotEmpty()) insertSections(sections)
        if (anchors.isNotEmpty()) insertAnchors(anchors)
    }

    /**
     * Sobrescribe una canción existente con una versión que llega de fuera (restaurar una copia de
     * seguridad o sincronizar con otro dispositivo), **aplicando los metadatos del archivo**,
     * incluido su `updated_at`.
     *
     * A diferencia de [upsertFullSong] —que conserva el `updated_at` local para no alterar el
     * orden «Recientes» al fusionar—, aquí sí se escribe el `updated_at` de la versión entrante:
     * es una decisión explícita del usuario de sustituir el canto.
     *
     * Nunca se borra la fila de `songs` (se conservan los adjuntos y los vínculos con repertorios);
     * solo se reemplazan sus secciones, cuyos anclajes caen por la cascada de la FK.
     */
    @Transaction
    suspend fun updateSongInPlace(
        song: SongEntity,
        sections: List<SongSectionEntity>,
        anchors: List<ChordAnchorEntity>
    ) {
        updateSongHeader(
            id = song.id,
            title = song.title,
            originalKey = song.originalKey,
            number = song.number,
            author = song.author,
            rhythm = song.rhythm,
            rhythmPatternId = song.rhythmPatternId,
            tempoBpm = song.tempoBpm,
            tempoMarking = song.tempoMarking,
            momentsJson = song.defaultLiturgicalMomentsJson,
            timesJson = song.defaultLiturgicalTimesJson,
            isFavorite = song.isFavorite,
            capo = song.capo,
            notes = song.notes,
            contentHash = song.contentHash,
            updatedAt = song.updatedAt
        )
        deleteSectionsForSong(song.id)
        if (sections.isNotEmpty()) insertSections(sections)
        if (anchors.isNotEmpty()) insertAnchors(anchors)
    }


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSections(sections: List<SongSectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllAnchors(anchors: List<ChordAnchorEntity>)

    /**
     * Inserta un lote completo de canciones (cabeceras + secciones + anclajes) en una **única
     * transacción**, para la importación de un cancionero (~1.000 canciones) sin miles de `fsync`.
     *
     * Las canciones que **ya existen** localmente se **omiten por completo**, junto con sus
     * secciones y anclajes: restaurar un respaldo o reimportar un cancionero **nunca** sobrescribe
     * ni duplica lo que el usuario ya tiene. Es la única forma segura de fusionar, porque un
     * `INSERT OR REPLACE` en `songs` borraría la fila y con ella, por cascada, los adjuntos del
     * usuario; además chocaría con `celebration_items.song_id` (`ON DELETE RESTRICT`), que prohíbe
     * borrar un canto vinculado a un repertorio.
     */
    @Transaction
    suspend fun insertFullSongs(
        songs: List<SongEntity>,
        sections: List<SongSectionEntity>,
        anchors: List<ChordAnchorEntity>
    ) {
        if (songs.isEmpty()) return

        val alreadyExisting = existingSongIds(songs.map { it.id }).toSet()
        val newSongs = songs.filterNot { it.id in alreadyExisting }
        if (newSongs.isEmpty()) return

        val newSongIds = newSongs.map { it.id }.toSet()
        val newSections = sections.filter { it.songId in newSongIds }
        val newSectionIds = newSections.map { it.id }.toSet()
        val newAnchors = anchors.filter { it.sectionId in newSectionIds }

        insertSongs(newSongs)
        if (newSections.isNotEmpty()) insertAllSections(newSections)
        if (newAnchors.isNotEmpty()) insertAllAnchors(newAnchors)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Query("UPDATE songs SET is_favorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavoriteStatus(songId: String, isFavorite: Boolean)

    /**
     * Primera canción con una huella de contenido concreta (detección de duplicados).
     * La huella NO es única: puede haber varias filas con el mismo valor.
     */
    @Query("SELECT * FROM songs WHERE content_hash = :hash LIMIT 1")
    suspend fun getSongByContentHash(hash: String): SongEntity?

    /** Canciones con un título exacto (para reconocer la misma canción con contenido distinto). */
    @Query("SELECT * FROM songs WHERE title = :title")
    suspend fun getSongsByTitle(title: String): List<SongEntity>

    /**
     * Canciones cuya huella aún no se ha calculado (altas anteriores a la incorporación del
     * campo o respaldos antiguos). Se usa para rellenarla en segundo plano al arrancar.
     */
    @Transaction
    @Query("SELECT * FROM songs WHERE content_hash IS NULL OR content_hash = '' ORDER BY title ASC")
    suspend fun getSongsMissingContentHash(): List<SongWithDetailsRelation>

    /** Persiste la huella de una canción sin tocar el resto de su contenido. */
    @Query("UPDATE songs SET content_hash = :hash WHERE id = :songId")
    suspend fun updateContentHash(songId: String, hash: String)

    /**
     * Reescribe la lista de momentos litúrgicos por defecto de una canción.
     *
     * Se usa al renombrar una categoría: los nombres viven duplicados dentro de cada canción,
     * y sin esta actualización el filtro dejaría de encontrar los cantos.
     */
    @Query("UPDATE songs SET default_liturgical_moments_json = :json WHERE id = :songId")
    suspend fun updateSongMomentsJson(songId: String, json: String)

    /** Reescribe la lista de tiempos litúrgicos por defecto de una canción (ver [updateSongMomentsJson]). */
    @Query("UPDATE songs SET default_liturgical_times_json = :json WHERE id = :songId")
    suspend fun updateSongTimesJson(songId: String, json: String)

    @Delete
    suspend fun deleteSong(song: SongEntity)

    /**
     * Borra **todas** las canciones (secciones y anclajes caen por cascada).
     *
     * Antes hay que vaciar los repertorios: `celebration_items.song_id` es `ON DELETE RESTRICT` y
     * bloquearía el borrado con el error 1811.
     */
    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()
}
