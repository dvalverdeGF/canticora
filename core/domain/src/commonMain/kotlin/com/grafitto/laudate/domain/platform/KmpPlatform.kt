package com.grafitto.laudate.domain.platform

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineDispatcher

// -----------------------------------------------------------------------------
// Equivalentes multiplataforma de lo que en la JVM viene de `java.*`.
//
// El dominio debe compilar en **todos** los targets (JVM y Wasm): `nowMillis()`,
// `java.util.UUID` y `ioDispatcher` no existen fuera de la JVM. Estas tres funciones son el
// ÚNICO punto de contacto con el reloj, los identificadores y el hilo de entrada/salida, de modo
// que portar el dominio a una plataforma nueva no obliga a tocar los casos de uso.
// -----------------------------------------------------------------------------

/** Marca de tiempo en milisegundos desde la época (equivalente KMP de `System.currentTimeMillis()`). */
@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Identificador único en texto (equivalente KMP de `newId()`). */
@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

/**
 * Dispatcher para trabajo de entrada/salida.
 *
 * En la JVM es `ioDispatcher` (pool de hilos bloqueantes). En la web no existe: no hay ficheros
 * bloqueantes y se usa `Dispatchers.Default`.
 */
expect val ioDispatcher: CoroutineDispatcher
