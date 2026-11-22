package com.grafitto.laudate.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Diálogo que se muestra al **compartir un canto**: pregunta si debe viajar también con sus
 * adjuntos (audio, vídeo, documentos, imágenes y enlaces).
 *
 * La pregunta tiene **un solo botón claro** para el «sí» y un botón secundario para el «no», de
 * modo que compartir rápido no deje al usuario con una decisión a medias:
 *
 *  - **Solo el canto**: envía el ChordPro como texto y como archivo `.chordpro` (comportamiento de
 *    siempre; el más ligero y el que entienden todos los cancioneros).
 *  - **Con los adjuntos**: empaqueta el canto y sus ficheros en un archivo `canticora.song`
 *    (ADR-017). Útil para pasar a un compañero el canto con su pista de ensayo o su partitura.
 *
 * @param subject título del canto que se va a compartir.
 * @param attachmentCount número de adjuntos que viajarían si se elige «con los adjuntos»; `null`
 *   cuando la pantalla no los conoce (se usa un texto genérico).
 * @param onConfirm recibe `true` para compartir con adjuntos y `false` para compartir solo el canto.
 */
@Composable
fun ShareSongDialog(
    subject: String,
    attachmentCount: Int?,
    onDismiss: () -> Unit,
    onConfirm: (withAttachments: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Compartir canto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "¿Quieres compartir también los adjuntos de «$subject»?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = when (attachmentCount) {
                        null -> "Puede tener audios, vídeos, documentos, imágenes o enlaces."
                        1 -> "Este canto tiene 1 adjunto (audio, vídeo, documento, imagen o enlace)."
                        else -> "Este canto tiene $attachmentCount adjuntos (audios, vídeos, " +
                            "documentos, imágenes o enlaces)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Sin adjuntos el canto se envía como texto y se abre al instante; con " +
                        "adjuntos viaja en un archivo más pesado que incluye los ficheros.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(true) }) { Text("Sí, con los adjuntos") }
        },
        dismissButton = {
            TextButton(onClick = { onConfirm(false) }) { Text("Solo el canto") }
        }
    )
}
