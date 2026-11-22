package com.grafitto.laudate.ui.backup

import com.grafitto.laudate.domain.backup.BackupImportSummary
import com.grafitto.laudate.domain.backup.BackupPreview
import com.grafitto.laudate.domain.backup.BackupRestoreMode
import com.grafitto.laudate.domain.backup.SongConflictPolicy

/**
 * Estado de la pantalla de copia de seguridad.
 *
 * [isBusy] se activa mientras se genera o restaura un respaldo; [isImporting] distingue la
 * importación (para mostrar el texto adecuado).
 *
 * [pendingRestore] está activo cuando el usuario ha elegido un archivo para **restaurar** (modo
 * destructivo): se ha analizado el archivo y se muestra su contenido antes de pedir confirmación.
 */
data class BackupUiState(
    val isBusy: Boolean = false,
    val isImporting: Boolean = false,
    val lastMessage: String? = null,
    val songCount: Int = 0,
    val attachmentCount: Int = 0,
    val errorMessage: String? = null,
    val pendingRestore: BackupImportSummary? = null,
    val pendingSync: PendingSync? = null,
    /** `true` si hay una copia automática del estado previo a la última restauración destructiva. */
    val safetyBackupAvailable: Boolean = false
)

/**
 * Sincronización en curso que ha detectado coincidencias con contenido distinto.
 *
 * Solo se usa cuando el usuario elige **preguntar** canto a canto; el flujo normal de
 * sincronización sobrescribe directamente.
 */
data class PendingSync(
    val archiveUri: String,
    val summary: BackupImportSummary,
    val decisions: Map<String, SongConflictPolicy> = emptyMap()
) {
    val conflicts get() = summary.conflicts
}

sealed interface BackupUiIntent {
    /** El usuario ha elegido dónde guardar la copia; se genera el ZIP ahí. */
    data class ExportTo(
        val destinationUri: String,
        /** Si es `true`, además de guardarla se ofrece compartirla al terminar. */
        val shareAfter: Boolean = false
    ) : BackupUiIntent

    /**
     * El usuario ha elegido un archivo de copia para **sincronizar** (fusionar): se añade lo que
     * falta y se **sobrescriben** las canciones que coinciden, sin borrar nada más.
     */
    data class SyncFrom(val archiveUri: String) : BackupUiIntent

    /**
     * El usuario ha elegido un archivo para **restaurar** (destructivo): se analiza y se muestra su
     * contenido antes de pedir confirmación.
     */
    data class RequestRestore(val archiveUri: String) : BackupUiIntent

    /** El usuario confirma la restauración destructiva mostrada en [BackupUiState.pendingRestore]. */
    data object ConfirmRestore : BackupUiIntent

    /** El usuario cancela la restauración pendiente. */
    data object CancelRestore : BackupUiIntent

    /** Recupera la copia automática del estado previo a la última restauración (se añade sin borrar). */
    data object RestoreSafetyBackup : BackupUiIntent

    /** Elección del usuario para un canto en conflicto durante una sincronización con pregunta. */
    data class ResolveConflict(val songId: String, val policy: SongConflictPolicy) : BackupUiIntent

    /** Aplica la misma política a todos los cantos en conflicto. */
    data class ApplyPolicyToAll(val policy: SongConflictPolicy) : BackupUiIntent

    /** Confirma la sincronización con las decisiones tomadas. */
    data object ConfirmSync : BackupUiIntent

    /** Cancela la sincronización pendiente. */
    data object CancelSync : BackupUiIntent

    /** Recarga los contadores que se muestran en la pantalla. */
    data object RefreshStats : BackupUiIntent

    data object ShowExportHelp : BackupUiIntent
}

sealed interface BackupSideEffect {
    data class ShowMessage(val message: String) : BackupSideEffect

    /** Abre el selector del sistema para elegir el destino del archivo (SAF). */
    data class RequestExportDestination(val suggestedFileName: String) : BackupSideEffect

    /** Abre el selector del sistema para elegir el archivo a **sincronizar** (SAF). */
    data object RequestSyncFile : BackupSideEffect

    /** Abre el selector del sistema para elegir el archivo a **restaurar** (SAF). */
    data object RequestRestoreFile : BackupSideEffect

    /** Comparte con la hoja nativa el archivo de copia de seguridad ya generado. */
    data class ShareArchive(val archiveUri: String, val fileName: String) : BackupSideEffect
}
