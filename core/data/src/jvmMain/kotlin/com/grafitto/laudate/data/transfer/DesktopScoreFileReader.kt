package com.grafitto.laudate.data.transfer

import com.grafitto.laudate.domain.backup.BackupArchiveStore
import com.grafitto.laudate.domain.transfer.ScoreFileReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lector de ficheros de partitura en **escritorio (JVM)**.
 *
 * Como en Android, la lectura de **texto plano** es lo único propio; los contenedores ZIP se
 * delegan en [BackupArchiveStore] para no duplicar el lector (ADR-013, ADR-017).
 */
class DesktopScoreFileReader(
    private val archiveStore: BackupArchiveStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ScoreFileReader {

    override suspend fun readText(fileUri: String): String? = withContext(ioDispatcher) {
        runCatching {
            File(fileUri).readText(Charsets.UTF_8).removePrefix("\uFEFF")
        }.getOrNull()
    }

    override suspend fun readArchiveEntry(fileUri: String, entryName: String): String? =
        runCatching { archiveStore.readEntry(fileUri, entryName) }.getOrNull()

    override suspend fun readArchiveEntryEndingWith(fileUri: String, suffix: String): String? =
        runCatching { archiveStore.readEntryEndingWith(fileUri, suffix) }.getOrNull()
}
