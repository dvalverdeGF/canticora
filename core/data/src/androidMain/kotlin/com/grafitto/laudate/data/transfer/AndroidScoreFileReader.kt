package com.grafitto.laudate.data.transfer

import android.content.Context
import android.net.Uri
import com.grafitto.laudate.domain.backup.BackupArchiveStore
import com.grafitto.laudate.domain.transfer.ScoreFileReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lector de ficheros de partitura en **Android**.
 *
 * Solo aporta la lectura de **texto plano** ([readText]); para los contenedores ZIP **delega en
 * [BackupArchiveStore]**, que ya sabe abrir entradas con `ZipInputStream` sobre un `content://`.
 * Así no hay dos lectores de ZIP que mantener en sincronía (ADR-017).
 */
class AndroidScoreFileReader(
    context: Context,
    private val archiveStore: BackupArchiveStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ScoreFileReader {

    private val resolver = context.applicationContext.contentResolver

    override suspend fun readText(fileUri: String): String? = withContext(ioDispatcher) {
        runCatching {
            resolver.openInputStream(Uri.parse(fileUri))?.use { input ->
                input.readBytes().toString(Charsets.UTF_8).removePrefix("\uFEFF")
            }
        }.getOrNull()
    }

    override suspend fun readArchiveEntry(fileUri: String, entryName: String): String? =
        runCatching { archiveStore.readEntry(fileUri, entryName) }.getOrNull()

    override suspend fun readArchiveEntryEndingWith(fileUri: String, suffix: String): String? =
        runCatching { archiveStore.readEntryEndingWith(fileUri, suffix) }.getOrNull()
}
