package com.grafitto.laudate.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.song.SongDisplayMode

/**
 * Diálogo previo a la exportación a PDF de un cancionero o de un repertorio.
 *
 * Ofrece las **mismas opciones que el visor** ([SongDisplayMode]): solo letra, letra y acordes,
 * letra y tablatura, letra + acordes + tablatura o solo tablatura. Los modos con tablatura solo se
 * ofrecen si el contenido tiene alguna. La elección se recuerda como preferencia global.
 *
 * @param subject nombre del canto o del repertorio que se va a exportar.
 * @param initialMode modo recordado; preselecciona la opción correspondiente.
 * @param onConfirm recibe el [SongDisplayMode] elegido.
 * @param hasTablature `true` si el contenido incluye al menos una tablatura.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfExportDialog(
    subject: String,
    initialMode: SongDisplayMode,
    onDismiss: () -> Unit,
    onConfirm: (SongDisplayMode) -> Unit,
    modifier: Modifier = Modifier,
    hasTablature: Boolean = false
) {
    var mode by remember(initialMode) { mutableStateOf(initialMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("Exportar a PDF") },
        text = {
            Column {
                Text(
                    text = "¿Cómo quieres el PDF de «$subject»?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SongDisplayMode.entries.forEach { option ->
                        // Los modos con tablatura solo se ofrecen si el contenido tiene alguna.
                        if (hasTablature || !option.showsTablature) {
                            FilterChip(
                                selected = mode == option,
                                onClick = { mode = option },
                                label = { Text(option.label) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Se recordará tu elección para la próxima vez.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(mode) }) { Text("Exportar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
