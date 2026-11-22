package com.grafitto.laudate.domain.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** En la web no hay E/S de ficheros bloqueante: el pool por defecto es suficiente. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
