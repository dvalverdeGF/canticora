package com.grafitto.laudate.web.data

import kotlin.js.js

/**
 * Almacén clave-valor del navegador (`localStorage`) — ADR-023.
 *
 * La web **no tiene Room**: el catálogo semilla viaja como recurso estático y todo lo que el
 * usuario crea o modifica (favoritos, repertorios, categorías, canciones propias, ajustes) se
 * guarda aquí. El contrato con la UI es idéntico al de las otras plataformas porque el Dominio no
 * cambia: solo cambia el `RepositoryImpl`.
 *
 * Todas las claves van prefijadas por [PREFIX] para no chocar con nada del dominio donde se sirve
 * la aplicación (el dominio donde se sirve puede estar compartido con otros proyectos).
 */
internal object WebStorage {

    /** Prefijo de todas las claves de Canticora en `localStorage`. */
    const val PREFIX: String = "canticora.web."

    /**
     * Lee una clave. Devuelve `null` si no existe o si el navegador la bloquea (modo privado,
     * cuota agotada, `localStorage` deshabilitado): la aplicación funciona igual, sin persistir.
     */
    fun read(key: String): String? = try {
        rawRead(PREFIX + key).takeIf { it.isNotEmpty() }
    } catch (_: Throwable) {
        null
    }

    /** Escribe una clave. Ignora el fallo de cuota: la sesión sigue viva en memoria. */
    fun write(key: String, value: String) {
        try {
            rawWrite(PREFIX + key, value)
        } catch (_: Throwable) {
            // Sin persistencia: los datos viven solo mientras la pestaña esté abierta.
        }
    }

    /** Borra una clave. */
    fun remove(key: String) {
        try {
            rawRemove(PREFIX + key)
        } catch (_: Throwable) {
            // Nada que hacer: si no se puede borrar tampoco se podía leer.
        }
    }

    private fun rawRead(key: String): String = storageGet(key)

    private fun rawWrite(key: String, value: String) = storageSet(key, value)

    private fun rawRemove(key: String) = storageDelete(key)
}

// El interop de Wasm exige que `js(...)` sea el cuerpo completo de una función **de nivel
// superior** que solo use sus propios parámetros; por eso estos tres puentes viven fuera del
// objeto y el manejo de errores se hace alrededor.

private fun storageGet(key: String): String = js("(localStorage.getItem(key) || '')")

private fun storageSet(key: String, value: String) {
    js("(localStorage.setItem(key, value), '')")
}

private fun storageDelete(key: String) {
    js("(localStorage.removeItem(key), '')")
}
