package com.grafitto.laudate.domain.backup

/**
 * Abstracción para **vaciar la biblioteca local** (restauración destructiva).
 *
 * Vive en Dominio y no conoce Android ni Room: la implementación concreta orquesta los DAOs.
 * Se mantiene como una única operación —en lugar de repartir un `deleteAll…` por cada repositorio—
 * porque el borrado tiene un **orden obligatorio**: primero los repertorios (cuyos
 * `celebration_items.song_id` son `ON DELETE RESTRICT` y bloquearían el borrado de las canciones) y
 * después el catálogo, cuyos hijos caen por cascada.
 */
interface LibraryWiper {

    /**
     * Borra canciones, repertorios, adjuntos, patrones personalizados y categorías personalizadas.
     *
     * Conserva lo que **no** es contenido del usuario: las categorías canónicas (`isSystemStandard`)
     * y los patrones integrados (que viajan en la app).
     */
    suspend fun wipeAll()
}
