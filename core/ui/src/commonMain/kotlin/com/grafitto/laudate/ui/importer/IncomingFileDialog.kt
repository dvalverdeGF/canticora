package com.grafitto.laudate.ui.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.transfer.DetectedLaudateFile
import com.grafitto.laudate.domain.transfer.LaudateFileKind

/**
 * Diálogo que aparece al **abrir o compartir un archivo con Canticora**.
 *
 * Dice qué es el archivo (reconocido automáticamente) y ofrece importarlo de una vez, para que el
 * usuario no tenga que elegir la vía ni volver a buscar el archivo. Solo hay que confirmar.
 */
@Composable
fun IncomingFileDialog(
    detected: DetectedLaudateFile,
    fileName: String?,
    isBusy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val recognized = detected.kind != LaudateFileKind.UNKNOWN && detected.kind != LaudateFileKind.MEDIA

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(iconFor(detected.kind), contentDescription = null) },
        title = {
            Text(
                if (recognized) "Importar ${detected.kindLabel}" else titleFor(detected),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detected.title?.takeIf { it.isNotBlank() }?.let { title ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                fileName?.takeIf { it != detected.title }?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = when (detected.kind) {
                        LaudateFileKind.CELEBRATION -> "Repertorio de misa o evento. Se añadirá como " +
                            "una lista nueva, reutilizando los cantos que ya tengas."
                        LaudateFileKind.BACKUP -> "Biblioteca de otro dispositivo. Se añadirá todo " +
                            "lo que no tengas y se actualizarán las canciones, misas y ritmos que " +
                            "coincidan; no se borra nada."
                        LaudateFileKind.SONG -> "Un canto con sus adjuntos. Se añadirá a tu " +
                            "cancionero con sus audios, vídeos, documentos o imágenes."
                        LaudateFileKind.CATALOG -> "Cancionero completo. Se incorporarán sus canciones y " +
                            "sus categorías."
                        LaudateFileKind.RHYTHMS -> "Patrones rítmicos de rasgueo. Se añadirán a tus ritmos; " +
                            "los que ya tengas idénticos no se duplican."
                        LaudateFileKind.CHORDPRO -> "Una canción en formato ChordPro."
                        LaudateFileKind.MEDIA -> mediaExplanation(detected)
                        LaudateFileKind.UNKNOWN -> "Este archivo no parece un cancionero, un repertorio " +
                            "ni una copia de seguridad de Canticora."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )

                val details = buildList {
                    detected.songCount?.let { add("$it cantos") }
                    detected.celebrationItemCount?.let { add("$it elementos") }
                    detected.categoryCount?.let { add("$it categorías") }
                    detected.rhythmCount?.let { add("$it ritmos") }
                    detected.attachmentCount?.takeIf { it > 0 }?.let { add("$it adjuntos") }
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            if (recognized) {
                TextButton(onClick = onConfirm, enabled = !isBusy) {
                    Spacer(Modifier.width(0.dp))
                    Text("Importar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (recognized) "Ahora no" else "Cerrar")
            }
        }
    )
}

private fun iconFor(kind: LaudateFileKind): ImageVector = when (kind) {
    LaudateFileKind.CELEBRATION -> Icons.AutoMirrored.Filled.PlaylistAdd
    LaudateFileKind.BACKUP -> Icons.Default.CloudSync
    LaudateFileKind.SONG -> Icons.Default.AttachFile
    LaudateFileKind.CATALOG -> Icons.Default.MenuBook
    LaudateFileKind.RHYTHMS -> Icons.Default.GraphicEq
    LaudateFileKind.CHORDPRO -> Icons.Default.MusicNote
    LaudateFileKind.MEDIA -> Icons.Default.HelpOutline
    LaudateFileKind.UNKNOWN -> Icons.Default.HelpOutline
}

/** Título del diálogo cuando el archivo no se puede importar como canción. */
private fun titleFor(detected: DetectedLaudateFile): String =
    if (detected.kind == LaudateFileKind.MEDIA) {
        "Esto no es una canción"
    } else {
        "Archivo no reconocido"
    }

/**
 * Explicación en lenguaje llano de por qué un [LaudateFileKind.MEDIA] no se puede importar, y qué
 * hacer en su lugar: añadirlo como adjunto a una canción desde el editor, no crear una canción con
 * su contenido.
 */
private fun mediaExplanation(detected: DetectedLaudateFile): String {
    val description = detected.mediaDescription ?: "archivo"
    return "Esto es un $description, no una canción. Canticora no puede abrirlo como tal. Si " +
        "quieres guardarlo junto a una canción (por ejemplo un vídeo o un audio de ensayo), " +
        "añádelo como adjunto desde la pantalla de esa canción, con el botón «Añadir adjunto»."
}
