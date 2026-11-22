package com.grafitto.laudate.domain.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** En la JVM el trabajo de E/S bloqueante tiene su propio pool. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
