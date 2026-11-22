package com.grafitto.laudate.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.grafitto.laudate.domain.backup.BackupLocationProvider
import java.io.File

/**
 * Resuelve la ubicación de las **copias automáticas** en Android: un archivo dentro del almacén
 * privado de la app (`files/auto_backups/`).
 *
 * Es persistente (no caché): la copia previa a una restauración destructiva debe sobrevivir al
 * cierre de la app. Se expone por `FileProvider` (se añade la ruta `auto_backups/` a
 * `file_paths.xml`) para poder compartirla o restaurarla.
 */
class AndroidBackupLocationProvider(
    context: Context,
    private val relativeDirectory: String = "auto_backups"
) : BackupLocationProvider {

    private val appContext = context.applicationContext

    override suspend fun createAutoBackupTarget(fileName: String): String {
        val dir = File(appContext.filesDir, relativeDirectory).apply { mkdirs() }
        val file = File(dir, fileName)
        file.createNewFile()
        pruneOldBackups(dir, fileName)
        return Uri.fromFile(file).toString()
    }

    override suspend fun latestAutoBackup(prefix: String): String? {
        val dir = File(appContext.filesDir, relativeDirectory)
        val latest = dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(".canticora") }
            ?.maxByOrNull { it.lastModified() }
            ?: return null
        return FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            latest
        ).toString()
    }

    /**
     * Conserva solo las [MAX_AUTO_BACKUPS] copias automáticas más recientes (incluida [justCreated]),
     * para que importar muchas veces no llene el almacén del móvil sin límite.
     */
    private fun pruneOldBackups(dir: File, justCreated: String) {
        val backups = dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(SAFETY_PREFIX) && it.name.endsWith(".canticora") }
            ?.sortedByDescending { it.lastModified() }
            ?: return
        backups.drop(MAX_AUTO_BACKUPS).forEach { old ->
            if (old.name != justCreated) old.delete()
        }
    }

    private companion object {
        const val SAFETY_PREFIX = "canticora_copia_automatica_"
        const val MAX_AUTO_BACKUPS = 3
    }
}
