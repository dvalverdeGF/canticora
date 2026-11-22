package com.grafitto.laudate.domain.repository

import com.grafitto.laudate.domain.model.song.Song
import kotlinx.coroutines.flow.Flow

/**
 * Contrato puro de Dominio para el acceso al catálogo de canciones.
 * No expone Room ni ningún tipo dependiente de Android.
 */
interface SongRepository {
    fun getAllSongs(): Flow<List<Song>>

    /**
     * Catálogo completo incluyendo secciones (y por tanto letra), para búsquedas que filtran
     * también por el contenido del canto. Más costoso que [getAllSongs], por lo que no debe
     * usarse para listados simples.
     */
    fun getAllSongsWithLyrics(): Flow<List<Song>>

    /** Número de canciones almacenadas (consulta puntual, no reactiva). */
    suspend fun getSongCount(): Int
    fun getFavoriteSongs(): Flow<List<Song>>

    /**
     * Canciones concretas **con su letra**, resueltas en una sola consulta.
     *
     * Es el acceso correcto para exportar (PDF, ChordPro) o compartir un repertorio: leer los
     * cantos uno a uno es más lento y, si se hace contra la tabla `songs` sin sus secciones,
     * devuelve canciones sin letra.
     */
    suspend fun getSongsByIds(ids: List<String>): List<Song>

    /**
     * Cabeceras de varias canciones **sin sus secciones** (consulta ligera).
     *
     * Se usa para saber qué cantos de un archivo ya existen localmente y comparar su huella de
     * contenido antes de restaurar (resolución de conflictos), sin pagar el coste de cargar la
     * letra de todo un cancionero. Las canciones devueltas tienen `sections` vacío.
     */
    suspend fun getSongStubsByIds(ids: List<String>): List<Song>

    /**
     * Canción suelta para el visor. Incluye secciones y acordes, pero no debe usarse en bucle:
     * para varios cantos a la vez, [getSongsByIds].
     */
    fun getSongById(id: String): Flow<Song?>
    fun searchSongs(query: String): Flow<List<Song>>
    suspend fun saveSong(song: Song)

    /**
     * Sobrescribe una canción **existente** con la versión que llega en un archivo (restaurar una
     * copia de seguridad o sincronizar con otro dispositivo), aplicando los metadatos del archivo,
     * incluido su `updatedAt`.
     *
     * Preserva la identidad de la fila para no perder los adjuntos ni los vínculos con repertorios
     * (a diferencia de un borrado + alta). Es la acción que ejecuta la opción «Sobrescribir» de la
     * resolución de conflictos al restaurar.
     */
    suspend fun overwriteSong(song: Song)

    /**
     * Guarda un lote de canciones en una **única transacción**.
     * Pensado para importar un cancionero y restaurar respaldos: con ~1.000 canciones
     * evita miles de `fsync` individuales y mantiene la operación rápida (RNF-101).
     */
    suspend fun saveSongs(songs: List<Song>)

    suspend fun setFavorite(songId: String, isFavorite: Boolean)
    suspend fun deleteSong(songId: String)

    /**
     * Renombra una etiqueta de categoría (momento o tiempo litúrgico) en **todas** las canciones.
     *
     * Los nombres de categoría se guardan duplicados dentro de cada canción (listas JSON de
     * momentos y tiempos por defecto), por lo que renombrar una categoría exige reescribir esas
     * listas para que el filtro siga encontrando los cantos (ver
     * [com.grafitto.laudate.domain.usecase.UpdateCategoryUseCase]).
     *
     * La comparación es por nombre, sin distinguir mayúsculas ni acentos. Devuelve el número de
     * canciones modificadas.
     */
    suspend fun renameCategoryTag(oldName: String, newName: String): Int

    /**
     * Busca una canción por su huella de contenido (detección de duplicados).
     *
     * Devuelve la primera coincidencia o `null`. La huella **no es única** en la base de datos:
     * el usuario puede decidir conservar dos copias de la misma canción.
     */
    suspend fun findSongByContentHash(hash: String): Song?

    /**
     * Busca una canción local por su **identidad musical** (título + autor + número).
     *
     * Es lo que permite detectar que un ChordPro que te han enviado es en realidad un canto que ya
     * tienes pero **editado** (misma canción, contenido distinto), y ofrecer sobrescribirlo con
     * permiso. Compara sin distinguir mayúsculas ni acentos.
     */
    suspend fun findSongByContentSignature(song: Song): Song?

    /** Canciones cuya huella aún no se ha calculado (migración de esquemas anteriores). */
    suspend fun getSongsMissingContentHash(): List<Song>

    /** Persiste la huella de una canción sin tocar el resto de su contenido. */
    suspend fun updateContentHash(songId: String, hash: String)
}
