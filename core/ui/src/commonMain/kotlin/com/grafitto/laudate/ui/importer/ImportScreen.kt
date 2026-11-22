package com.grafitto.laudate.ui.importer

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
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.ui.common.LaudateTopAppBar
import com.grafitto.laudate.domain.transfer.ImportConflict
import com.grafitto.laudate.domain.transfer.ImportConflictPolicy

/**
 * Pantalla **Importar**: punto de entrada explícito para traer contenido a Canticora.
 *
 * Canticora no publica canciones: el usuario trae su propio cancionero. Para no obligarle a
 * elegir la vía correcta, la pantalla tiene **una sola forma de importar**: un campo de texto (o
 * un archivo) y un único botón **Importar**. El contenido se reconoce automáticamente
 * ([com.grafitto.laudate.domain.transfer.LaudateFileDetector]) y, antes de tocar nada, se dice qué
 * es y se pide confirmación. Arriba se explican los tipos que se pueden traer.
 *
 * La **copia de seguridad** (`.zip`) se queda aparte: es binaria, no se puede pegar y necesita el
 * selector de archivos del sistema, así que conserva su propio botón.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    uiState: ImportUiState,
    stats: ImportLibraryStats,
    onNavigateBack: () -> Unit,
    contentText: String,
    onContentChange: (String) -> Unit,
    onDetectContent: () -> Unit,
    onConfirmContent: () -> Unit,
    onDismissContent: () -> Unit,
    onChooseContentFile: () -> Unit,
    onImportBackupClick: () -> Unit,
    onConfirmIncomingFile: () -> Unit = {},
    onDismissIncomingFile: () -> Unit = {},
    onResolveImportConflict: (String, ImportConflictPolicy) -> Unit = { _, _ -> },
    onApplyImportPolicyToAll: (ImportConflictPolicy) -> Unit = {},
    onConfirmImportConflicts: () -> Unit = {},
    onCancelImportConflicts: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // La barra superior se oculta al bajar y reaparece al subir (comportamiento tipo Gmail).
    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // Archivo recibido desde fuera o contenido pegado/elegido: se dice qué es y se ofrece
    // importarlo directamente. El camino de confirmación depende del origen.
    uiState.pendingFile?.let { detected ->
        IncomingFileDialog(
            detected = detected,
            fileName = uiState.pendingFileName,
            isBusy = uiState.isBusy,
            onConfirm = if (uiState.pendingIsIncomingFile) onConfirmIncomingFile else onConfirmContent,
            onDismiss = if (uiState.pendingIsIncomingFile) onDismissIncomingFile else onDismissContent
        )
    }

    // Conflicto de importación: lo que llega ya existe con cambios. Se pregunta antes de sobrescribir.
    uiState.pendingConflicts?.let { pending ->
        ImportConflictDialog(
            pending = pending,
            onResolveConflict = onResolveImportConflict,
            onApplyPolicyToAll = onApplyImportPolicyToAll,
            onConfirm = onConfirmImportConflicts,
            onDismiss = onCancelImportConflicts
        )
    }

    Scaffold(
        topBar = {
            LaudateTopAppBar(
                title = { Text("Importar") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Canticora no incluye canciones: trae las tuyas. Puedes importar un " +
                            "cancionero completo, un repertorio de misa, canciones en ChordPro o " +
                            "la biblioteca de un compañero.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Resumen de la biblioteca actual (antes de importar).
            Text(
                text = "Tu biblioteca ahora mismo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Card {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Canciones", style = MaterialTheme.typography.bodyMedium)
                        Text("${stats.songCount}", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Repertorios", style = MaterialTheme.typography.bodyMedium)
                        Text("${stats.celebrationCount}", fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Categorías", style = MaterialTheme.typography.bodyMedium)
                        Text("${stats.categoryCount}", fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "Importar contenido",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Pega el contenido o elige un archivo y pulsa **Importar**. Canticora " +
                            "reconoce solo de qué se trata y te lo confirma antes de tocar nada.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    OutlinedTextField(
                        value = contentText,
                        onValueChange = onContentChange,
                        label = { Text("Pega aquí el contenido (ChordPro, JSON…)") },
                        minLines = 4,
                        maxLines = 10,
                        enabled = !uiState.isBusy,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onDetectContent,
                            enabled = !uiState.isBusy && contentText.isNotBlank()
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Importar")
                        }
                        OutlinedButton(
                            onClick = onChooseContentFile,
                            enabled = !uiState.isBusy
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Elegir archivo…")
                        }
                    }
                }
            }

            Text(
                text = "Qué puedes importar",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            ImportTypeInfo(
                icon = Icons.Default.MenuBook,
                title = "Canciones de Canticora",
                description = "Un cancionero completo en formato JSON. Incorpora todas sus canciones " +
                    "y sus categorías de una vez."
            )

            ImportTypeInfo(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                title = "Repertorio de una misa o evento",
                description = "Un repertorio compartido por otro miembro del coro. Los cantos que ya " +
                    "tengas no se sobrescriben."
            )

            ImportTypeInfo(
                icon = Icons.Default.MusicNote,
                title = "Canciones en ChordPro",
                description = "Una o varias canciones en formato ChordPro, pegadas como texto o en un " +
                    "archivo. El título, el autor y el tono se toman de sus propias directivas."
            )

            ImportTypeInfo(
                icon = Icons.Default.AttachFile,
                title = "Un canto con sus adjuntos",
                description = "Un canto compartido junto a sus audios, vídeos, documentos o imágenes. " +
                    "Se añade al cancionero con sus ficheros."
            )

            ImportTypeInfo(
                icon = Icons.Default.GraphicEq,
                title = "Ritmos de rasgueo",
                description = "Patrones rítmicos compartidos o guardados: un archivo de ritmos o un " +
                    "ritmo pegado como texto. Los que ya tengas no se duplican."
            )

            Text(
                text = "¿Te pasan una biblioteca?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Card {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Importar una biblioteca",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Trae la biblioteca de un compañero (su copia de seguridad) a este " +
                            "dispositivo: se añade todo lo que no tengas y se actualizan las canciones, " +
                            "misas y ritmos que coincidan. No se borra nada. Antes de empezar se guarda " +
                            "una copia de seguridad automática, por si quieres volver atrás.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onImportBackupClick, enabled = !uiState.isBusy) {
                        Icon(Icons.Default.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Importar biblioteca")
                    }
                }
            }

            if (uiState.isBusy) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("Importando…", style = MaterialTheme.typography.bodyMedium)
                }
            }

            uiState.lastResult?.let { result ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(result, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Text(
                text = "¿Tienes tus cantos en ChordPro? Puedes exportar una canción abriéndola y " +
                    "usando el botón de compartir del editor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Tarjeta informativa de un tipo de contenido que se puede importar (sin botón: se importa con el
 * botón único de arriba). */
@Composable
private fun ImportTypeInfo(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Diálogo de resolución de conflictos de importación: lo que llega **ya existe** con cambios.
 *
 * Permite decidir, elemento a elemento, entre **sobrescribir** con lo que llega, **conservar
 * ambos** (añade lo nuevo sin tocar lo local) u **omitir**.
 */
@Composable
private fun ImportConflictDialog(
    pending: PendingImportConflicts,
    onResolveConflict: (String, ImportConflictPolicy) -> Unit,
    onApplyPolicyToAll: (ImportConflictPolicy) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val conflicts = pending.conflicts
    val undecided = conflicts.count { pending.decisions[it.key] == null }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pending.title) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                Text(
                    text = if (conflicts.size == 1) {
                        "Esto ya lo tienes, pero con cambios. Elige qué hacer."
                    } else {
                        "Estos ${conflicts.size} elementos ya los tienes, pero con cambios. " +
                            "Elige qué hacer con cada uno."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onApplyPolicyToAll(ImportConflictPolicy.OVERWRITE) }) {
                        Text("Sobrescribir todo")
                    }
                    TextButton(onClick = { onApplyPolicyToAll(ImportConflictPolicy.KEEP_BOTH) }) {
                        Text("Conservar todo")
                    }
                }
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(conflicts, key = { it.key }) { conflict ->
                        ImportConflictRow(
                            conflict = conflict,
                            selected = pending.decisions[conflict.key],
                            onSelect = { onResolveConflict(conflict.key, it) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(if (undecided == 0) "Importar" else "Importar ($undecided sin decidir)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/** Fila de un conflicto con sus tres opciones. */
@Composable
private fun ImportConflictRow(
    conflict: ImportConflict,
    selected: ImportConflictPolicy?,
    onSelect: (ImportConflictPolicy) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = conflict.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
        conflict.detail?.let { detail ->
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = selected == ImportConflictPolicy.OVERWRITE,
                onClick = { onSelect(ImportConflictPolicy.OVERWRITE) },
                label = { Text("Sobrescribir") }
            )
            FilterChip(
                selected = selected == ImportConflictPolicy.KEEP_BOTH,
                onClick = { onSelect(ImportConflictPolicy.KEEP_BOTH) },
                label = { Text("Conservar ambos") }
            )
            FilterChip(
                selected = selected == ImportConflictPolicy.SKIP,
                onClick = { onSelect(ImportConflictPolicy.SKIP) },
                label = { Text("Omitir") }
            )
        }
    }
}
