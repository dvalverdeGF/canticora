package com.grafitto.laudate.ui.tablature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.tablature.TablatureTextLayout
import com.grafitto.laudate.ui.common.DeleteActionButton
import com.grafitto.laudate.ui.common.DuplicateActionButton
import com.grafitto.laudate.ui.common.LaudateTopAppBar

/**
 * Listado de **tablaturas de un canto** (ADR-017, *Feature T*). Un canto puede tener varias
 * (guitarra, bajo…). Cada tarjeta previsualiza la tablatura y ofrece editar, duplicar y eliminar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TablatureScreen(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onManageBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            LaudateTopAppBar(
                title = { Text("Tablaturas") },
                navigationIcon = {
                    IconButton(onClick = { onManageBack?.invoke() ?: onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Importar una partitura como tablatura del canto. El selector acepta MusicXML,
                    // MuseScore (.mscz), Guitar Pro 7/8 (.gp, .gpx) y TuxGuitar (.tg). Reemplaza la
                    // tablatura que hubiera: una tablatura pertenece al canto.
                    IconButton(onClick = { onIntent(TablatureUiIntent.RequestImportTablature) }) {
                        Icon(
                            Icons.Default.FileOpen,
                            contentDescription = "Importar tablatura desde un archivo (MusicXML, MuseScore, Guitar Pro, TuxGuitar)"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onIntent(TablatureUiIntent.OpenNewEditor) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nueva tablatura") }
            )
        }
    ) { padding ->
        TablatureListContent(
            uiState = uiState,
            onIntent = onIntent,
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }
}

/**
 * Contenido del **listado de tablaturas** (sin `Scaffold`), reutilizable en la pantalla propia y en
 * la tercera pestaña del editor de canción.
 */
@Composable
fun TablatureListContent(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (uiState.tablatures.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Este canto todavía no tiene tablaturas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Crea una para guitarra, laúd o bajo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Importar un fichero de partitura y convertirlo en tablatura del canto.
                // Es la acción más usada cuando vienes de otro programa, así que va PRIMERO y
                // con el texto completo: antes estaba debajo del FAB, en gris y con un nombre
                // («Importar partitura») que no decía «tablatura» en ninguna parte.
                Button(
                    onClick = { onIntent(TablatureUiIntent.RequestImportTablature) },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Importar tablatura desde un archivo")
                }
                Text(
                    "MusicXML, MuseScore, Guitar Pro o TuxGuitar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                OutlinedButton(
                    onClick = { onIntent(TablatureUiIntent.OpenNewEditor) },
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Escribirla a mano")
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Acción siempre disponible: se pueden tener VARIAS tablaturas por canto (guitarra,
                // bajo, intro, estrofa…). El botón no debe faltar cuando ya existe alguna.
                //
                // **`FlowRow`, no `Row`** (reporte del usuario: «el botón de Nueva Tablatura en el
                // móvil se descuajaringa»). Los dos botones juntos miden más que el ancho de un
                // móvil, y en un `Row` Compose los comprime hasta partir el texto («Nueva
                // tablat / ura») y deformar el icono. Con `FlowRow` el segundo baja a su propia
                // línea cuando no cabe, y en tableta siguen alineados en la misma fila.
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Importar una partitura: sustituye la tablatura del canto. Vive AQUÍ, junto a
                    // «Nueva tablatura», y no solo en la barra superior de la pantalla suelta, porque
                    // la pestaña «Tablaturas» del editor de canto pinta este contenido sin barra.
                    OutlinedButton(
                        onClick = { onIntent(TablatureUiIntent.RequestImportTablature) }
                    ) {
                        Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Importar desde un archivo")
                    }
                    Button(onClick = { onIntent(TablatureUiIntent.OpenNewEditor) }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Nueva tablatura")
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.tablatures, key = { it.id }) { tablature ->
                        TablatureCard(
                            tablature = tablature,
                            // (D-48) La miniatura usa los MISMOS ajustes de pentagrama que el editor.
                            staffOptions = uiState.editorStaffOptions,
                            onEdit = { onIntent(TablatureUiIntent.OpenEditor(tablature.id)) },
                            onDuplicate = { onIntent(TablatureUiIntent.DuplicateTablature(tablature.id)) },
                            onShare = { onIntent(TablatureUiIntent.ShareTablature(tablature.id)) },
                            onDelete = { onIntent(TablatureUiIntent.DeleteTablature(tablature.id)) },
                            onRefingerToTuning = { tuning ->
                                onIntent(TablatureUiIntent.RefingerToTuning(tablature.id, tuning))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TablatureCard(
    tablature: Tablature,
    /** Ajustes del pentagrama (preferencia global, D-48). */
    staffOptions: com.grafitto.laudate.domain.tablature.TabStaffOptions =
        com.grafitto.laudate.domain.tablature.TabStaffOptions(),
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRefingerToTuning: (Tuning) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tablature.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${tablature.instrument} · ${TablatureTextLayout.tuningLabel(tablature)} · ${tablature.time}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DuplicateActionButton(onClick = onDuplicate, label = "Duplicar tablatura")
                // Compartir en MusicXML: es el formato que abren MuseScore, Guitar Pro y TuxGuitar,
                // así que la tablatura sigue siendo editable fuera de Canticora.
                IconButton(onClick = onShare) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Compartir tablatura (MusicXML)"
                    )
                }
                DeleteActionButton(onClick = onDelete, label = "Eliminar tablatura")
            }
            // Deriva una tablatura equivalente para otro instrumento (laúd ↔ guitarra, bajo…) a
            // partir de las MISMAS alturas. Se guarda como tablatura nueva (la original se conserva).
            var tuningMenu by remember { mutableStateOf(false) }
            Box {
                AssistChip(
                    onClick = { tuningMenu = true },
                    label = { Text("Redigitar para otro instrumento…") }
                )
                DropdownMenu(expanded = tuningMenu, onDismissRequest = { tuningMenu = false }) {
                    Tuning.presets.forEach { tuning ->
                        DropdownMenuItem(
                            text = { Text(tuning.name) },
                            onClick = {
                                onRefingerToTuning(tuning)
                                tuningMenu = false
                            }
                        )
                    }
                }
            }
            // Previsualización (solo rejilla, sin cabecera duplicada).
            TablatureView(tablature = tablature, showHeader = false, options = staffOptions)
        }
    }
}
