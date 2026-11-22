package com.grafitto.laudate.data.local

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

// -----------------------------------------------------------------------------
// Equivalentes KMP de las utilidades JVM (`java.util.UUID`, `System.currentTimeMillis`).
//
// `java.*` NO existe en `commonMain`, así que el esquema (entidades) y los repositorios
// compartidos no pueden usarlos. Estas funciones viven en el módulo compartido y son
// la ÚNICA dependencia de bajo nivel para generar ids y marcas de tiempo.
// -----------------------------------------------------------------------------

/** Identificador único (equivalente KMP de `UUID.randomUUID().toString()`). */
@OptIn(ExperimentalUuidApi::class)
fun newId(): String = Uuid.random().toString()

/** Marca de tiempo en milisegundos (equivalente KMP de `System.currentTimeMillis()`). */
@OptIn(ExperimentalTime::class)
fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
