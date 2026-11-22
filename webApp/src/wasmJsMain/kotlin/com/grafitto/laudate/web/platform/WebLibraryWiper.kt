package com.grafitto.laudate.web.platform

import com.grafitto.laudate.domain.backup.LibraryWiper
import com.grafitto.laudate.web.WebPlatform
import com.grafitto.laudate.web.data.WebStore

/**
 * Vaciado de la biblioteca local en la web (restauración destructiva).
 *
 * Respeta el mismo orden que Android y escritorio: primero los **repertorios** (que referencian
 * cantos) y después el catálogo, cuyos hijos caen en cascada. Se conservan las categorías
 * canónicas y los patrones integrados, que no son contenido del usuario.
 */
class WebLibraryWiper(private val store: WebStore) : LibraryWiper {

    override suspend fun wipeAll() {
        store.celebrations.value.forEach { store.deleteCelebration(it.id) }
        store.songs.value.forEach { store.deleteSong(it.id) }
        store.tablatures.value.forEach { store.deleteTablature(it.id) }
        store.attachments.value.forEach { store.deleteAttachment(it.id) }
        store.categories.value.filterNot { it.isSystemStandard }.forEach { store.deleteCategory(it.id) }
        store.patterns.value.filterNot { it.isBuiltIn }.forEach { store.deletePattern(it.id) }
        WebPlatform.clearFileStore()
    }
}
