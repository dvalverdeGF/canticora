package com.grafitto.laudate.ui.celebration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.ui.common.Density
import com.grafitto.laudate.ui.common.ActionMenuItem
import com.grafitto.laudate.ui.common.ActionTint
import com.grafitto.laudate.ui.common.PdfExportDialog
import com.grafitto.laudate.ui.common.rememberCollapsingHeaderState
import com.grafitto.laudate.ui.common.AppLogo
import com.grafitto.laudate.ui.common.LaudateTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CelebrationListScreen(
    uiState: CelebrationListUiState,
    onIntent: (CelebrationListUiIntent) -> Unit,
    onCelebrationClick: (String) -> Unit,
    onLiveModeClick: (String) -> Unit,
    onCreateCelebrationClick: () -> Unit,
    /** Abre el selector de archivos para importar un repertorio recibido. */
    onImportClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var celebrationToDelete by remember { mutableStateOf<Celebration?>(null) }
    var celebrationToShare by remember { mutableStateOf<Celebration?>(null) }
    var celebrationToPdf by remember { mutableStateOf<Celebration?>(null) }

    val filteredCelebrations = remember(uiState.celebrations, uiState.selectedTypeFilter) {
        when (uiState.selectedTypeFilter) {
            "Misa" -> uiState.celebrations.filter { it.celebrationType.equals("Misa", ignoreCase = true) }
            "Evento" -> uiState.celebrations.filter { !it.celebrationType.equals("Misa", ignoreCase = true) }
            else -> uiState.celebrations
        }
    }

    // La barra superior se oculta al bajar y reaparece al subir (comportamiento tipo Gmail).
    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberLazyListState()
    val headerState = rememberCollapsingHeaderState(listState)

    Scaffold(
        topBar = {
            LaudateTopAppBar(
                title = {
                    Text("Misas y Eventos")
                },
                actions = {
                    if (onImportClick != null) {
                        IconButton(onClick = onImportClick) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = "Importar misa o evento"
                            )
                        }
                    }
                },
                scrollBehavior = topBarScrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateCelebrationClick) {
                Icon(Icons.Default.Add, contentDescription = "Nueva Celebración o Evento")
            }
        },
        modifier = modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Density.CardPadding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    filteredCelebrations.isEmpty() -> {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            AppLogo(modifier = Modifier.size(72.dp).alpha(0.6f))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (uiState.celebrations.isEmpty())
                                    "No hay repertorios creados aún.\nCrea una lista para preparar tu misa o evento con el botón '+'."
                                else "No hay elementos en esta categoría.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(Density.Snug),
                            contentPadding = PaddingValues(
                                top = headerState.contentTopPadding() + 8.dp,
                                bottom = 96.dp
                            ),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredCelebrations, key = { it.id }) { celebration ->
                                CelebrationCardItem(
                                    celebration = celebration,
                                    onClick = { onCelebrationClick(celebration.id) },
                                    onLiveClick = { onLiveModeClick(celebration.id) },
                                    onDeleteClick = { celebrationToDelete = celebration },
                                    onShareClick = { celebrationToShare = celebration },
                                    onPdfClick = { celebrationToPdf = celebration }
                                )
                            }
                        }
                    }
                }

                // Cabecera colapsable: filtros rápidos por tipo. Flota al reaparecer con scroll
                // hacia arriba (como en Gmail).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, headerState.offsetY.toInt()) }
                        .onSizeChanged { headerState.onHeightChanged(it.height) }
                        .background(
                            if (headerState.isFloating) MaterialTheme.colorScheme.surfaceContainerLow
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(vertical = Density.Snug),
                    horizontalArrangement = Arrangement.spacedBy(Density.Snug)
                ) {
                    FilterChip(
                        selected = uiState.selectedTypeFilter == null,
                        onClick = { onIntent(CelebrationListUiIntent.FilterByType(null)) },
                        label = { Text("Todos") }
                    )
                    FilterChip(
                        selected = uiState.selectedTypeFilter == "Misa",
                        onClick = { onIntent(CelebrationListUiIntent.FilterByType("Misa")) },
                        label = { Text("Misas") }
                    )
                    FilterChip(
                        selected = uiState.selectedTypeFilter == "Evento",
                        onClick = { onIntent(CelebrationListUiIntent.FilterByType("Evento")) },
                        label = { Text("Eventos") }
                    )
                }
            }
        }
    }

    celebrationToDelete?.let { cel ->
        AlertDialog(
            onDismissRequest = { celebrationToDelete = null },
            title = { Text("Eliminar Celebración / Evento") },
            text = { Text("¿Deseas eliminar \"${cel.name}\"? Podrás deshacerlo desde el aviso inferior.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIntent(CelebrationListUiIntent.DeleteCelebration(cel.id))
                        celebrationToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { celebrationToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Compartir el repertorio completo como archivo de Canticora.
    celebrationToShare?.let { cel ->
        AlertDialog(
            onDismissRequest = { celebrationToShare = null },
            title = { Text("Compartir repertorio") },
            text = {
                Text(
                    "Puedes enviar «${cel.name}» a los miembros del coro como archivo de Canticora: " +
                        "lo abrirán directamente en su app."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIntent(CelebrationListUiIntent.ShareCelebration(cel.id))
                        celebrationToShare = null
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Compartir archivo")
                }
            },
            dismissButton = {
                TextButton(onClick = { celebrationToShare = null }) { Text("Cancelar") }
            }
        )
    }

    // Antes de generar el PDF se pregunta el modo; la elección se recuerda.
    celebrationToPdf?.let { cel ->
        PdfExportDialog(
            subject = cel.name,
            initialMode = uiState.pdfDisplayMode,
            onDismiss = { celebrationToPdf = null },
            onConfirm = { mode ->
                onIntent(CelebrationListUiIntent.PrintCelebration(cel.id, mode))
                celebrationToPdf = null
            }
        )
    }
}

@Composable
fun CelebrationCardItem(
    celebration: Celebration,
    onClick: () -> Unit,
    onLiveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: () -> Unit = {},
    onPdfClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isMass = celebration.celebrationType.equals("Misa", ignoreCase = true)
    val accentColor = if (isMass) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Barra de acento lateral según tipo de celebración
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(96.dp)
                    .background(accentColor)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Density.CardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Density.Snug)
                    ) {
                        Text(
                            text = celebration.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Badge(
                            containerColor = accentColor.copy(alpha = 0.25f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Text(
                                text = celebration.celebrationType,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    celebration.description?.takeIf { it.isNotBlank() }?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            text = "${celebration.items.size} cantos asignados",
                            modifier = Modifier.padding(2.dp)
                        )
                    }
                }

                // Acciones agrupadas en el menú de tres puntos; el clic sobre la tarjeta sigue
                // abriendo el editor del repertorio.
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        ActionMenuItem(
                            text = "Abrir en vivo",
                            icon = Icons.Default.PlayArrow,
                            tint = ActionTint.PRIMARY,
                            onClick = {
                                menuExpanded = false
                                onLiveClick()
                            }
                        )
                        ActionMenuItem(
                            text = "Compartir",
                            icon = Icons.Default.Share,
                            onClick = {
                                menuExpanded = false
                                onShareClick()
                            }
                        )
                        ActionMenuItem(
                            text = "Exportar a PDF",
                            icon = Icons.Default.PictureAsPdf,
                            onClick = {
                                menuExpanded = false
                                onPdfClick()
                            }
                        )
                        ActionMenuItem(
                            text = "Eliminar",
                            icon = Icons.Default.Delete,
                            tint = ActionTint.ERROR,
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
        }
    }
}
