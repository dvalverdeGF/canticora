package com.grafitto.laudate.data.backup

import com.grafitto.laudate.domain.backup.BackupLocationProvider
import java.io.File

/**
 * Resuelve la ubicación de las **copias automáticas** en escritorio: un archivo dentro de la
 * carpeta de datos (`~/.canticora/auto_backups/`).
 *
 * En escritorio las rutas son normales (no `Uri`), así que la referencia es la propia ruta.
 */
class DesktopBackupLocationProvider(
    private val dataDirectory: File,
    private val relativeDirectory: String = "auto_backups"
) : BackupLocationProvider {

    override suspend fun createAutoBackupTarget(fileName: String): String {
        val dir = File(dataDirectory, relativeDirectory).apply { mkdirs() }
        val file = File(dir, fileName)
        file.createNewFile()
        pruneOldBackups(dir, fileName)
        return file.absolutePath
    }

    override suspend fun latestAutoBackup(prefix: String): String? {
        val dir = File(dataDirectory, relativeDirectory)
        return dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(".canticora") }
            ?.maxByOrNull { it.lastModified() }
            ?.absolutePath
    }

    /**
     * Conserva solo las [MAX_AUTO_BACKUPS] copias automáticas más recientes (incluida [justCreated]),
     * para que importar muchas veces no acumule archivos sin límite.
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
