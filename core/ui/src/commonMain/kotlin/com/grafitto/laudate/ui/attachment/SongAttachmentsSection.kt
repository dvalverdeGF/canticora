package com.grafitto.laudate.ui.attachment

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertLink
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.attachment.AttachmentKind
import com.grafitto.laudate.domain.model.attachment.SongAttachment

/**
 * Sección de **adjuntos de un canto** (audios, vídeos, documentos, imágenes y enlaces).
 *
 * Vive en `ui/attachment/` porque la usan dos pantallas —el visor (`SongDetailScreen`) y el
 * editor (`SongEditorScreen`)— y ambas deben comportarse igual. Los adjuntos se guardan en la
 * biblioteca local del usuario y **no viajan** al compartir un canto o un repertorio; sí se
 * incluyen en la copia de seguridad completa.
 *
 * @param onAddFileClick `null` deshabilita el botón «Archivo» (p. ej. mientras el canto no se ha
 *   guardado todavía o si la pantalla no puede abrir el selector del sistema).
 * @param showTitle `false` cuando quien la usa ya titula la sección (p. ej. el panel de la
 *   herramienta «Adjuntos» del editor), para no repetir «Adjuntos» dos veces seguidas.
 */
@Composable
fun SongAttachmentsSection(
    attachments: List<SongAttachment>,
    onAddFileClick: (() -> Unit)?,
    onAddLink: (String, String?) -> Unit,
    onOpen: ((SongAttachment) -> Unit)?,
    onExport: ((SongAttachment) -> Unit)?,
    onDelete: (SongAttachment) -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = true
) {
    var showLinkDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showTitle) {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showTitle) {
                Text(
                    text = "Adjuntos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row {
                if (onAddFileClick != null) {
                    TextButton(onClick = onAddFileClick) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = null,
                            modifier = Modifier.width(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Archivo")
                    }
                }
                TextButton(onClick = { showLinkDialog = true }) {
                    Icon(
                        Icons.Default.InsertLink,
                        contentDescription = null,
                        modifier = Modifier.width(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enlace")
                }
            }
        }

        if (attachments.isEmpty()) {
            Text(
                text = "Añade grabaciones, vídeos, partituras o enlaces de ensayo. Se guardan en " +
                    "tu dispositivo y en tus copias de seguridad (no se comparten con el canto).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                attachments.forEach { attachment ->
                    AttachmentRow(
                        attachment = attachment,
                        onOpen = onOpen,
                        onExport = onExport,
                        onDelete = onDelete
                    )
                }
            }
        }
    }

    if (showLinkDialog) {
        AddLinkDialog(
            onDismiss = { showLinkDialog = false },
            onConfirm = { url, title ->
                onAddLink(url, title)
                showLinkDialog = false
            }
        )
    }
}

@Composable
private fun AttachmentRow(
    attachment: SongAttachment,
    onOpen: ((SongAttachment) -> Unit)?,
    onExport: ((SongAttachment) -> Unit)?,
    onDelete: (SongAttachment) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onOpen != null) { onOpen?.invoke(attachment) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (attachment.kind) {
                    AttachmentKind.AUDIO -> Icons.Default.AudioFile
                    AttachmentKind.VIDEO -> Icons.Default.Videocam
                    AttachmentKind.IMAGE -> Icons.Default.Image
                    AttachmentKind.LINK -> Icons.Default.InsertLink
                    AttachmentKind.DOCUMENT -> Icons.Default.Description
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2
                )
                attachment.readableSize()?.let { size ->
                    Text(
                        text = size,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!attachment.isLink && onExport != null) {
                IconButton(onClick = { onExport(attachment) }) {
                    Icon(Icons.Default.Download, contentDescription = "Descargar adjunto")
                }
            }
            IconButton(onClick = { onOpen?.invoke(attachment) }, enabled = onOpen != null) {
                Icon(Icons.Default.OpenInNew, contentDescription = "Abrir adjunto")
            }
            IconButton(onClick = { onDelete(attachment) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Eliminar adjunto",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AddLinkDialog(
    onDismiss: () -> Unit,
    onConfirm: (url: String, title: String?) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir enlace") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Dirección (https://…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim(), title.trim().ifEmpty { null }) },
                enabled = url.trim().startsWith("http")
            ) {
                Text("Añadir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
