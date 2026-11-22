package com.grafitto.laudate.data.backup

import com.grafitto.laudate.data.local.dao.AttachmentDao
import com.grafitto.laudate.data.local.dao.CategoryDao
import com.grafitto.laudate.data.local.dao.CelebrationDao
import com.grafitto.laudate.data.local.dao.RhythmPatternDao
import com.grafitto.laudate.data.local.dao.SongDao
import com.grafitto.laudate.data.mapper.AttachmentMapper
import com.grafitto.laudate.domain.attachment.AttachmentStore
import com.grafitto.laudate.domain.backup.LibraryWiper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementación de [LibraryWiper]: vacía la biblioteca local para dejar el dispositivo como el
 * archivo de una copia de seguridad (restauración destructiva).
 *
 * El **orden del borrado es obligatorio**:
 *  1. **Repertorios**: sus `celebration_items.song_id` son `ON DELETE RESTRICT`, así que mientras
 *     existan bloquearían el borrado de las canciones (error 1811).
 *  2. **Canciones**: sus secciones, anclajes y adjuntos de BD caen por cascada.
 *  3. **Ficheros de adjuntos** en el almacén privado: si no se borran, quedarían huérfanos en disco.
 *  4. **Patrones rítmicos personalizados** y **categorías personalizadas** (nunca las canónicas ni
 *     los patrones integrados, que viajan en la app).
 */
class RoomLibraryWiper(
    private val songDao: SongDao,
    private val celebrationDao: CelebrationDao,
    private val categoryDao: CategoryDao,
    private val rhythmPatternDao: RhythmPatternDao,
    private val attachmentDao: AttachmentDao,
    private val attachmentStore: AttachmentStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : LibraryWiper {

    override suspend fun wipeAll() = withContext(ioDispatcher) {
        // 1) Repertorios primero: libera la restricción de clave foránea sobre las canciones.
        celebrationDao.deleteAllCelebrations()

        // 2) Ficheros de adjuntos en disco, antes de perder las filas que apuntan a ellos.
        val attachments = attachmentDao.getAllAttachments()
        attachments.forEach { entity ->
            runCatching { attachmentStore.deleteFile(AttachmentMapper.toDomain(entity)) }
        }

        // 3) Catálogo: secciones, anclajes y adjuntos de BD caen por cascada.
        songDao.deleteAllSongs()
        attachmentDao.deleteAllAttachments()

        // 4) Contenido propio del usuario (se conservan canónicas e integrados).
        rhythmPatternDao.deleteAllCustomPatterns()
        categoryDao.deleteCustomCategories()
    }
}
