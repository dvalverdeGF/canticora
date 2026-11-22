package com.grafitto.laudate.ui.rhythm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmNotationMode
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.ui.common.Density
import com.grafitto.laudate.ui.common.ActionMenuItem
import com.grafitto.laudate.ui.common.ActionTint
import com.grafitto.laudate.ui.common.LaudateTopAppBar
import com.grafitto.laudate.ui.common.MusicFigureGlyph

/**
 * Listado de patrones rítmicos. La creación y la edición viven en una **pantalla aparte**
 * ([RhythmPatternEditorScreen]): aquí solo se explora, se duplica y se elimina.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RhythmPatternsScreen(
    uiState: RhythmPatternUiState,
    onIntent: (RhythmPatternUiIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var patternToDelete by remember { mutableStateOf<RhythmPattern?>(null) }
    // La barra superior se oculta al bajar y reaparece al subir (comportamiento tipo Gmail).
    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            LaudateTopAppBar(
                title = { Text("Patrones Rítmicos") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                actions = {
                    // Intercambio de la colección completa de ritmos propios (RF-310).
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Compartir todos mis ritmos") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onIntent(RhythmPatternUiIntent.ShareAllPatterns)
                            }
                        )
                    }
                },
                scrollBehavior = topBarScrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onIntent(RhythmPatternUiIntent.OpenNewPatternEditor) }) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo Patrón")
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
            // Selector global de representación (mismo modelo, distintas vistas).
            // Se usan chips desplazables en lugar de un segmented button: con 4 opciones las
            // etiquetas no caben y el control se descuadraba.
            Text(
                text = "Representación:",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Density.Snug, bottom = Density.Snug)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Density.Snug)
            ) {
                // (D-91) Los iconos son **vectores**, no caracteres Unicode: en la web el texto se
                // compone sin *fallback* de fuentes y `↓`, `♪` y `⇅` salían como cajas vacías.
                FilterChip(
                    selected = uiState.notationMode == RhythmNotationMode.ARROWS,
                    onClick = { onIntent(RhythmPatternUiIntent.SetNotationMode(RhythmNotationMode.ARROWS)) },
                    label = { Text("Flechas") },
                    leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) }
                )
                FilterChip(
                    selected = uiState.notationMode == RhythmNotationMode.MUSICAL_SYMBOLS,
                    onClick = { onIntent(RhythmPatternUiIntent.SetNotationMode(RhythmNotationMode.MUSICAL_SYMBOLS)) },
                    label = { Text("Figuras") },
                    leadingIcon = { MusicFigureGlyph(figure = RhythmFigure.QUARTER, size = 18.dp) }
                )
                FilterChip(
                    selected = uiState.notationMode == RhythmNotationMode.COMBINED,
                    onClick = { onIntent(RhythmPatternUiIntent.SetNotationMode(RhythmNotationMode.COMBINED)) },
                    label = { Text("Combinada") },
                    leadingIcon = { Icon(Icons.Default.SwapVert, contentDescription = null) }
                )
                FilterChip(
                    selected = uiState.notationMode == RhythmNotationMode.CHORDPRO_SYMBOLS,
                    onClick = { onIntent(RhythmPatternUiIntent.SetNotationMode(RhythmNotationMode.CHORDPRO_SYMBOLS)) },
                    label = { Text("ChordPro") },
                    leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selector de compás
            Text("Compás:", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeSignature.common.forEach { timeSignature ->
                    FilterChip(
                        selected = uiState.selectedTimeSignature == timeSignature,
                        onClick = { onIntent(RhythmPatternUiIntent.SelectTimeSignature(timeSignature)) },
                        label = { Text(timeSignature.toString()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.visiblePatterns, key = { it.id }) { pattern ->
                    PatternCard(
                        pattern = pattern,
                        notationMode = uiState.notationMode,
                        onEdit = { onIntent(RhythmPatternUiIntent.OpenEditPattern(pattern.id)) },
                        onDuplicate = { onIntent(RhythmPatternUiIntent.DuplicatePattern(pattern.id)) },
                        onDelete = { patternToDelete = pattern },
                        onShare = { onIntent(RhythmPatternUiIntent.SharePattern(pattern.id)) },
                        onCopyText = { onIntent(RhythmPatternUiIntent.CopyPatternAsText(pattern.id)) }
                    )
                }
                if (uiState.visiblePatterns.isEmpty()) {
                    item {
                        Text(
                            text = "No hay patrones para ${uiState.selectedTimeSignature}. Crea uno con el botón '+'.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }
                }
            }
        }
    }

    patternToDelete?.let { pattern ->
        AlertDialog(
            onDismissRequest = { patternToDelete = null },
            title = { Text("Eliminar Patrón") },
            text = { Text("¿Deseas eliminar el patrón \"${pattern.name}\"? Podrás deshacerlo desde el aviso inferior.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIntent(RhythmPatternUiIntent.DeletePattern(pattern.id))
                        patternToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { patternToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PatternCard(
    pattern: RhythmPattern,
    notationMode: RhythmNotationMode,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onCopyText: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(
            containerColor = if (pattern.isBuiltIn) MaterialTheme.colorScheme.surfaceContainer
            else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(Density.CardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pattern.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Compás ${pattern.timeSignature}" +
                            if (pattern.isBuiltIn) " · predefinido" else " · personalizado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Acciones agrupadas en el menú de tres puntos, con el mismo código de color que
                // los editores: editar en primario y eliminar en error.
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        ActionMenuItem(
                            text = "Editar",
                            icon = Icons.Default.Edit,
                            tint = ActionTint.PRIMARY,
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            }
                        )
                        ActionMenuItem(
                            text = "Duplicar",
                            icon = Icons.Default.FileCopy,
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            }
                        )
                        ActionMenuItem(
                            text = "Compartir como archivo",
                            icon = Icons.Default.Share,
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        ActionMenuItem(
                            text = "Copiar como texto",
                            icon = Icons.Default.ContentCopy,
                            onClick = {
                                menuExpanded = false
                                onCopyText()
                            }
                        )
                        // Los patrones predefinidos también se pueden eliminar: se ocultan de la
                        // biblioteca (borrado persistente y reversible con «Deshacer»).
                        ActionMenuItem(
                            text = "Eliminar",
                            icon = Icons.Default.Delete,
                            tint = ActionTint.ERROR,
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            PatternVisualization(pattern = pattern, notationMode = notationMode)

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Total: ${formatBeats(pattern.totalBeatValue)} de ${formatBeats(pattern.timeSignature.totalQuarterBeats)}" +
                    if (pattern.isValid) "" else " · incompleto",
                style = MaterialTheme.typography.labelSmall,
                color = if (pattern.isValid) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}
