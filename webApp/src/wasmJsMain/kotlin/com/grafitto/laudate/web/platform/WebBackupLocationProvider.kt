package com.grafitto.laudate.web.platform

import com.grafitto.laudate.domain.backup.BackupLocationProvider
import com.grafitto.laudate.web.WebPlatform

/**
 * Dónde viven las **copias automáticas** en la web.
 *
 * En Android y escritorio son ficheros en el almacenamiento privado de la app. Aquí se guardan en
 * **IndexedDB** (acepta megabytes, a diferencia de `localStorage`) y se recuerda su nombre en un
 * índice de `localStorage`. La copia previa a una restauración tiene que sobrevivir al cierre del
 * navegador: por eso no se usa memoria ni caché.
 */
class WebBackupLocationProvider : BackupLocationProvider {

    override suspend fun createAutoBackupTarget(fileName: String): String {
        val index = (names().filterNot { it == fileName } + fileName)
        val stale = if (index.size > MAX_BACKUPS) index.drop(index.size - MAX_BACKUPS) else emptyList()
        val kept = if (index.size > MAX_BACKUPS) index.takeLast(MAX_BACKUPS) else index
        WebPlatform.writeLocalValue(KEY_INDEX, kept.joinToString("\n"))
        stale.forEach { name ->
            WebPlatform.deleteAttachmentFile(WebPlatform.IDB_PREFIX + KEY_PREFIX + name)
        }
        return WebPlatform.IDB_PREFIX + KEY_PREFIX + fileName
    }

    override suspend fun latestAutoBackup(prefix: String): String? {
        for (name in names().filter { it.startsWith(prefix) }.sortedDescending()) {
            val uri = WebPlatform.IDB_PREFIX + KEY_PREFIX + name
            if (WebPlatform.attachmentFileExists(uri)) return uri
        }
        return null
    }

    private fun names(): List<String> =
        WebPlatform.readLocalValue(KEY_INDEX).split('\n').filter { it.isNotBlank() }

    private companion object {
        const val KEY_INDEX = "backups.index"
        const val KEY_PREFIX = "backups/"
        const val MAX_BACKUPS = 6
    }
}
