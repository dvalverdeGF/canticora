package com.grafitto.laudate.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Icono de **ayuda contextual (i)** reutilizable. Al pulsarlo abre un diálogo breve con la
 * explicación de un bloque o de una opción. Mismo patrón que la ayuda del editor de canciones.
 *
 * @param title Título del diálogo.
 * @param entries Lista de pares término → descripción.
 * @param modifier Modificador del botón.
 */
@Composable
fun ContextHelpButton(
    title: String,
    entries: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    var show by remember { mutableStateOf(false) }

    IconButton(onClick = { show = true }, modifier = modifier) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Ayuda sobre $title"
        )
    }

    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    entries.forEach { (term, description) ->
                        Column(modifier = Modifier.padding(bottom = 10.dp)) {
                            Text(
                                text = term,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(text = description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { show = false }) { Text("Entendido") }
            }
        )
    }
}
