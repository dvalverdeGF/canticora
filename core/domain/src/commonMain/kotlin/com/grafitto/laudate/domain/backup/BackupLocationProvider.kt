package com.grafitto.laudate.domain.backup

/**
 * Abstracción para resolver **dónde** se guardan y se leen las copias de seguridad creadas
 * automáticamente por la app (p. ej. la copia previa a una restauración destructiva).
 *
 * Vive en Dominio (Kotlin puro): el Dominio decide *cuándo* se crea una copia, y esta interfaz
 * resuelve *dónde* vive el archivo, que es lo único que depende de la plataforma.
 *
 * La ubicación debe ser **persistente y propia de la app** (no caché): la copia previa tiene que
 * sobrevivir al cierre de la app, porque es la red de seguridad del usuario.
 */
interface BackupLocationProvider {

    /**
     * Crea (si hace falta) el archivo destino de una copia automática con nombre [fileName] y
     * devuelve una referencia que [BackupArchiveStore] entiende. Se sobrescribe si ya existe.
     */
    suspend fun createAutoBackupTarget(fileName: String): String

    /**
     * Devuelve la **copia automática más reciente** cuyo nombre empiece por [prefix], o `null` si
     * no hay ninguna. Se usa para ofrecer restaurar el estado previo a la última restauración.
     *
     * Los nombres llevan marca de tiempo (no son fijos) para que crear una copia nueva mientras se
     * lee otra no provoque que el archivo que se está leyendo se sobrescriba a sí mismo.
     */
    suspend fun latestAutoBackup(prefix: String): String?
}
