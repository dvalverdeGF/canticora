package com.grafitto.laudate.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grafitto.laudate.domain.backup.BackupRestoreMode
import com.grafitto.laudate.domain.backup.SongConflictPolicy
import com.grafitto.laudate.domain.backup.canticoraBackupFileName
import com.grafitto.laudate.domain.platform.nowMillis
import com.grafitto.laudate.domain.repository.AttachmentRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.text.formatIsoDate
import com.grafitto.laudate.domain.usecase.ExportBackupUseCase
import com.grafitto.laudate.domain.usecase.ImportBackupUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel MVI de la pantalla de copia de seguridad.
 *
 * Orquesta la generación del ZIP y su restauración. Los `Uri` del destino y del archivo de origen
 * llegan resueltos desde la UI (selector del sistema), por lo que aquí no se toca `ContentResolver`
 * ni ninguna API de Android.
 */
class BackupViewModel(
    private val songRepository: SongRepository,
    private val attachmentRepository: AttachmentRepository,
    private val exportBackupUseCase: ExportBackupUseCase,
    private val importBackupUseCase: ImportBackupUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<BackupSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    /** Nombre de archivo propuesto en la última petición de exportación. */
    private var pendingFileName: String = "canticora_copia.canticora"

    /** `true` si, tras generar la copia, debe ofrecerse compartirla. */
    private var shareAfterExport: Boolean = false

    /** Archivo cuya restauración destructiva está pendiente de confirmación. */
    private var pendingRestoreUri: String? = null

    init {
        processIntent(BackupUiIntent.RefreshStats)
    }

    fun processIntent(intent: BackupUiIntent) {
        when (intent) {
            is BackupUiIntent.ExportTo -> exportTo(intent.destinationUri, intent.shareAfter)
            is BackupUiIntent.SyncFrom -> syncFrom(intent.archiveUri)
            is BackupUiIntent.RequestRestore -> requestRestore(intent.archiveUri)
            BackupUiIntent.ConfirmRestore -> confirmRestore()
            BackupUiIntent.CancelRestore -> _uiState.update { it.copy(pendingRestore = null) }
            BackupUiIntent.RestoreSafetyBackup -> restoreSafetyBackup()
            is BackupUiIntent.ResolveConflict -> resolveConflict(intent.songId, intent.policy)
            is BackupUiIntent.ApplyPolicyToAll -> applyPolicyToAll(intent.policy)
            BackupUiIntent.ConfirmSync -> confirmSync()
            BackupUiIntent.CancelSync -> _uiState.update { it.copy(pendingSync = null) }
            is BackupUiIntent.RefreshStats -> refreshStats()
            is BackupUiIntent.ShowExportHelp -> {
                viewModelScope.launch {
                    _sideEffect.send(
                        BackupSideEffect.ShowMessage(
                            "Elige una carpeta de Google Drive (o el almacenamiento del móvil) para " +
                                "guardar la copia. Podrás descargarla o compartirla cuando quieras."
                        )
                    )
                }
            }
        }
    }

    /** Pide a la UI que abra el selector de destino con un nombre de archivo sugerido. */
    fun requestExport(shareAfter: Boolean = false) {
        shareAfterExport = shareAfter
        viewModelScope.launch {
            _sideEffect.send(
                BackupSideEffect.RequestExportDestination(suggestedFileName())
            )
        }
    }

    /** Pide a la UI que abra el selector de archivos para elegir la copia a **sincronizar**. */
    fun requestSync() {
        viewModelScope.launch { _sideEffect.send(BackupSideEffect.RequestSyncFile) }
    }

    /** Pide a la UI que abra el selector de archivos para elegir la copia a **restaurar**. */
    fun requestRestore() {
        viewModelScope.launch { _sideEffect.send(BackupSideEffect.RequestRestoreFile) }
    }

    private fun refreshStats() {
        viewModelScope.launch {
            val songs = songRepository.getAllSongs().first()
            val attachments = if (songs.isEmpty()) {
                emptyList()
            } else {
                attachmentRepository.getAttachmentsForSongs(songs.map { it.id })
            }
            val safetyBackup = runCatching { importBackupUseCase.latestSafetyBackup() }
                .getOrNull() != null
            _uiState.update {
                it.copy(
                    songCount = songs.size,
                    attachmentCount = attachments.size,
                    safetyBackupAvailable = safetyBackup
                )
            }
        }
    }

    private fun exportTo(destinationUri: String, shareAfter: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isImporting = false, errorMessage = null) }
            runCatching { exportBackupUseCase(destinationUri) }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            lastMessage = "Copia creada: ${result.songsExported} cantos y " +
                                "${result.attachmentsExported} adjuntos"
                        )
                    }
                    if (shareAfter) {
                        // El nombre visible en la hoja de compartir se toma del propio archivo generado.
                        val fileName = destinationUri.substringAfterLast('/').ifBlank { pendingFileName }
                        _sideEffect.send(
                            BackupSideEffect.ShareArchive(destinationUri, fileName)
                        )
                    } else {
                        _sideEffect.send(
                            BackupSideEffect.ShowMessage("Copia de seguridad guardada correctamente")
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isBusy = false, errorMessage = error.message) }
                    _sideEffect.send(
                        BackupSideEffect.ShowMessage(
                            "No se pudo crear la copia: ${error.message ?: "error desconocido"}"
                        )
                    )
                }
        }
    }

    /**
     * **Sincronizar** (fusionar): añade lo que falta y **sobrescribe** las canciones que coinciden
     * con el mismo id, sin borrar nada más. Es el flujo para mantener varios dispositivos al día.
     */
    private fun syncFrom(archiveUri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isImporting = true, errorMessage = null) }
            applyImport(archiveUri, emptyMap(), BackupRestoreMode.MERGE)
        }
    }

    /**
     * **Restaurar** (destructivo): primero analiza el archivo para **verificar que es correcto y
     * mostrar su contenido**; solo entonces se pide confirmación. No se borra nada hasta confirmar.
     */
    private fun requestRestore(archiveUri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, isImporting = true, errorMessage = null) }
            runCatching { importBackupUseCase.inspect(archiveUri) }
                .onSuccess { summary ->
                    pendingRestoreUri = archiveUri
                    _uiState.update {
                        it.copy(isBusy = false, isImporting = false, pendingRestore = summary)
                    }
                }
                .onFailure { error -> onImportError(error, "restaurar") }
        }
    }

    private fun confirmRestore() {
        val archiveUri = pendingRestoreUri ?: return
        pendingRestoreUri = null
        viewModelScope.launch {
            _uiState.update { it.copy(pendingRestore = null) }
            applyImport(archiveUri, emptyMap(), BackupRestoreMode.REPLACE)
        }
    }

    /**
     * Recupera la copia automática que se creó justo antes de la última restauración destructiva.
     * Se aplica en modo **sincronizar** (merge), de modo que se recuperan los cantos que había sin
     * destruir los que acaban de llegar.
     */
    private fun restoreSafetyBackup() {
        viewModelScope.launch {
            val uri = runCatching { importBackupUseCase.latestSafetyBackup() }.getOrNull()
            if (uri == null) {
                _sideEffect.send(
                    BackupSideEffect.ShowMessage("No hay ninguna copia automática que recuperar")
                )
                return@launch
            }
            applyImport(uri, emptyMap(), BackupRestoreMode.MERGE)
        }
    }

    private fun resolveConflict(songId: String, policy: SongConflictPolicy) {
        _uiState.update { state ->
            val pending = state.pendingSync ?: return@update state
            state.copy(pendingSync = pending.copy(decisions = pending.decisions + (songId to policy)))
        }
    }

    private fun applyPolicyToAll(policy: SongConflictPolicy) {
        _uiState.update { state ->
            val pending = state.pendingSync ?: return@update state
            val all = pending.conflicts.associate { it.songId to policy }
            state.copy(pendingSync = pending.copy(decisions = all))
        }
    }

    private fun confirmSync() {
        val pending = _uiState.value.pendingSync ?: return
        viewModelScope.launch { applyImport(pending.archiveUri, pending.decisions, BackupRestoreMode.MERGE) }
    }

    /** Ejecuta la operación en el modo indicado y publica el resultado. */
    private suspend fun applyImport(
        archiveUri: String,
        decisions: Map<String, SongConflictPolicy>,
        mode: BackupRestoreMode
    ) {
        _uiState.update {
            it.copy(isBusy = true, isImporting = true, errorMessage = null, pendingSync = null)
        }
        runCatching { importBackupUseCase(archiveUri, decisions, mode) }
            .onSuccess { result ->
                val summary = buildString {
                    val verb = if (result.replaced) "Restaurado" else "Importado"
                    append("$verb: ${result.songsImported} cantos nuevos, ")
                    append("${result.celebrationsImported} repertorios y ")
                    append("${result.attachmentsImported} adjuntos")
                    if (result.songsOverwritten > 0) append(" · ${result.songsOverwritten} cantos actualizados")
                    if (result.safetyBackupFileName != null) {
                        append(". Se guardó una copia automática del estado anterior")
                    }
                }
                _uiState.update {
                    it.copy(isBusy = false, isImporting = false, lastMessage = summary)
                }
                refreshStats()
                _sideEffect.send(BackupSideEffect.ShowMessage(summary))
            }
            .onFailure { error -> onImportError(error, if (mode == BackupRestoreMode.REPLACE) "restaurar" else "sincronizar") }
    }

    private suspend fun onImportError(error: Throwable, action: String) {
        _uiState.update {
            it.copy(
                isBusy = false,
                isImporting = false,
                pendingRestore = null,
                pendingSync = null,
                errorMessage = error.message
            )
        }
        pendingRestoreUri = null
        _sideEffect.send(
            BackupSideEffect.ShowMessage(
                "No se pudo $action: ${error.message ?: "archivo no válido"}"
            )
        )
    }

    private fun suggestedFileName(): String {
        // El formato vive en el **Dominio** ([canticoraBackupFileName]) porque el escritorio también
        // lo necesita: antes ponía su propio literal («laudate_copia.laudate») y los ficheros de
        // Windows salían con la marca y la extensión antiguas.
        return canticoraBackupFileName(todayIsoDate()).also { pendingFileName = it }
    }
}

/** Fecha de hoy en `AAAA-MM-DD`, para el nombre de la copia de seguridad. */
private fun todayIsoDate(): String = formatIsoDate(nowMillis())
