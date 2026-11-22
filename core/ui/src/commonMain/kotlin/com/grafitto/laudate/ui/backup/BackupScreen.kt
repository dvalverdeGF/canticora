package com.grafitto.laudate.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.ui.common.LaudateTopAppBar
import com.grafitto.laudate.domain.backup.BackupImportSummary
import com.grafitto.laudate.domain.backup.SongConflict
import com.grafitto.laudate.domain.backup.SongConflictPolicy
import com.grafitto.laudate.ui.backup.PendingSync

/**
 * Pantalla de **copia de seguridad**: crear un archivo con toda la biblioteca (canciones,
 * repertorios, categorías y adjuntos) y restaurarlo.
 *
 * El destino y el origen se eligen con el selector del sistema, que incluye Google Drive: así la
 * copia puede guardarse en la nube, descargarse de nuevo o compartirse con otro miembro del coro
 * sin necesidad de iniciar sesión en ninguna cuenta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    uiState: BackupUiState,
    onNavigateBack: () -> Unit,
    onExportClick: () -> Unit,
    onSyncClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onShareClick: (() -> Unit)? = null,
    onResolveConflict: (String, SongConflictPolicy) -> Unit = { _, _ -> },
    onApplyPolicyToAll: (SongConflictPolicy) -> Unit = {},
    onConfirmSync: () -> Unit = {},
    onCancelSync: () -> Unit = {},
    onConfirmRestore: () -> Unit = {},
    onCancelRestore: () -> Unit = {},
    onRestoreSafetyBackup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // La barra superior se oculta al bajar y reaparece al subir (comportamiento tipo Gmail).
    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        topBar = {
            LaudateTopAppBar(
                title = { Text("Copia de seguridad") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                scrollBehavior = topBarScrollBehavior
            )
        },
        modifier = modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tu cancionero, a salvo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Se guarda un único archivo con tus ${uiState.songCount} cantos, " +
                            "tus repertorios y sus ${uiState.attachmentCount} audios, vídeos y " +
                            "documentos adjuntos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onExportClick,
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crear copia de seguridad")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Al guardarla puedes elegir tu carpeta de Google Drive. Después podrás " +
                    "descargarla o compartirla con quien quieras.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (onShareClick != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onShareClick,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compartir copia")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Enviala por WhatsApp o correo a otro miembro del coro para que la " +
                        "restaure en su móvil.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Usar un archivo en este dispositivo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Sincronizar: la opción recomendada para mantener varios dispositivos al día.
            Button(
                onClick = onSyncClick,
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sincronizar con el archivo")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Añade lo que falte y **sobrescribe** los cantos que ya existan con los del " +
                    "archivo. No borra nada más. Es lo que usarás para poner al día el móvil o el " +
                    "ordenador tras trabajar en el otro.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Restaurar: destructivo. Antes de borrar nada se analiza y se muestra el contenido.
            OutlinedButton(
                onClick = onRestoreClick,
                enabled = !uiState.isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restaurar copia (borra y reemplaza todo)")
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Deja este dispositivo **exactamente** como el archivo: borra el cancionero " +
                    "actual y lo sustituye por el de la copia. Antes de confirmar verás qué contiene " +
                    "el archivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Antes de restaurar se guarda sola una copia del estado actual; desde aquí se recupera.
            if (uiState.safetyBackupAvailable) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedButton(
                    onClick = onRestoreSafetyBackup,
                    enabled = !uiState.isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recuperar lo que había antes de restaurar")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Antes de importar una biblioteca o de restaurar una copia, Canticora " +
                        "guarda sola una copia del estado anterior. Desde aquí puedes recuperarla (se " +
                        "añade a lo que tengas ahora, sin borrar nada).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.isBusy) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (uiState.isImporting) "Procesando…" else "Creando copia…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            uiState.lastMessage?.let { message ->
                Spacer(modifier = Modifier.height(20.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = message, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    // Confirmación de una restauración destructiva: primero se muestra qué contiene el archivo.
    uiState.pendingRestore?.let { summary ->
        RestorePreviewDialog(
            summary = summary,
            localSongCount = uiState.songCount,
            onConfirm = onConfirmRestore,
            onDismiss = onCancelRestore
        )
    }

    // Diálogo de resolución de conflictos de una sincronización con pregunta.
    uiState.pendingSync?.let { pending ->
        ConflictResolutionDialog(
            pending = pending,
            onResolveConflict = onResolveConflict,
            onApplyPolicyToAll = onApplyPolicyToAll,
            onConfirm = onConfirmSync,
            onDismiss = onCancelSync
        )
    }
}

/**
 * Diálogo de confirmación de una **restauración destructiva**: muestra el contenido del archivo
 * verificado y advierte de que el cancionero actual se borrará y se sustituirá por el de la copia.
 */
@Composable
private fun RestorePreviewDialog(
    summary: BackupImportSummary,
    localSongCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val preview = summary.preview
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restaurar copia") },
        text = {
            Column {
                Text(
                    text = "El archivo es válido y contiene:",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                PreviewRow("Cantos", preview.songs)
                PreviewRow("Repertorios", preview.celebrations)
                PreviewRow("Categorías", preview.categories)
                PreviewRow("Ritmos", preview.rhythms)
                PreviewRow("Adjuntos", preview.attachments)
                preview.appVersion?.let { version ->
                    PreviewRow("Creado con", null, textValue = version)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Se borrará este cancionero",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tus $localSongCount cantos actuales, tus repertorios y tus " +
                                "adjuntos se borrarán y se sustituirán por los del archivo. Esta " +
                                "acción no se puede deshacer, pero se guardará sola una copia del " +
                                "estado actual antes de borrar.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Borrar y restaurar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/** Fila «Etiqueta …… valor» del resumen de contenido de una copia. */
@Composable
private fun PreviewRow(
    label: String,
    count: Int?,
    textValue: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = textValue ?: count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Diálogo tipo «Windows» al restaurar un archivo sobre canciones que ya existen: permite decidir,
 * canto a canto, entre **sobrescribir** con la versión del archivo, **conservar ambas** (importa la
 * del archivo como un canto nuevo) u **omitir** (mantener la local).
 */
@Composable
private fun ConflictResolutionDialog(
    pending: PendingSync,
    onResolveConflict: (String, SongConflictPolicy) -> Unit,
    onApplyPolicyToAll: (SongConflictPolicy) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val conflicts = pending.conflicts
    val pendingCount = conflicts.count { pending.decisions[it.songId] == null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cantos que ya existen") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                Text(
                    text = if (conflicts.size == 1) {
                        "La copia trae un canto que ya tienes pero con cambios. Elige qué hacer con él."
                    } else {
                        "La copia trae ${conflicts.size} cantos que ya tienes pero con cambios. " +
                            "Elige qué hacer con cada uno."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onApplyPolicyToAll(SongConflictPolicy.OVERWRITE) }) {
                        Text("Sobrescribir todo")
                    }
                    TextButton(onClick = { onApplyPolicyToAll(SongConflictPolicy.KEEP_BOTH) }) {
                        Text("Conservar todo")
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider()

                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(conflicts, key = { it.songId }) { conflict ->
                        ConflictRow(
                            conflict = conflict,
                            selected = pending.decisions[conflict.songId],
                            onSelect = { onResolveConflict(conflict.songId, it) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(if (pendingCount == 0) "Restaurar" else "Restaurar ($pendingCount sin decidir)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/** Fila de un canto en conflicto con sus tres opciones. */
@Composable
private fun ConflictRow(
    conflict: SongConflict,
    selected: SongConflictPolicy?,
    onSelect: (SongConflictPolicy) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = if (conflict.number != null) "${conflict.number} · ${conflict.title}" else conflict.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = if (conflict.incomingIsNewer) {
                "El archivo parece más reciente"
            } else {
                "Tu versión parece más reciente"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = selected == SongConflictPolicy.OVERWRITE,
                onClick = { onSelect(SongConflictPolicy.OVERWRITE) },
                label = { Text("Sobrescribir") }
            )
            FilterChip(
                selected = selected == SongConflictPolicy.KEEP_BOTH,
                onClick = { onSelect(SongConflictPolicy.KEEP_BOTH) },
                label = { Text("Conservar ambas") }
            )
            FilterChip(
                selected = selected == SongConflictPolicy.SKIP,
                onClick = { onSelect(SongConflictPolicy.SKIP) },
                label = { Text("Omitir") }
            )
        }
    }
}
