package com.grafitto.laudate.ui.rhythm

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmNotationMode
import com.grafitto.laudate.domain.model.rhythm.StrumArticulation
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.domain.rhythm.RhythmGridResolution
import com.grafitto.laudate.ui.common.CopyTextActionButton
import com.grafitto.laudate.ui.common.DeleteActionButton
import com.grafitto.laudate.ui.common.DuplicateActionButton
import com.grafitto.laudate.ui.common.SaveActionButton
import com.grafitto.laudate.ui.common.ShareActionButton
import com.grafitto.laudate.ui.common.LaudateTopAppBar
import com.grafitto.laudate.ui.platform.PlatformBackHandler

/**
 * Pantalla **independiente** de creación/edición de un patrón rítmico.
 *
 * Antes vivía dentro de un `AlertDialog`, que estrechaba el contenido y obligaba a hacer scroll
 * horizontal continuo para trabajar con patrones de 4–12 posiciones. Ahora es una pantalla propia,
 * con toda la amplitud disponible: la barra de acciones va **fija abajo** y el contenido se
 * desplaza verticalmente sin que los botones de Guardar/Cancelar se pierdan.
 *
 * El estado y los intents son los mismos que usaba el diálogo (`RhythmPatternUiState` /
 * `RhythmPatternUiIntent`): el editor sigue siendo una vista del mismo ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RhythmPatternEditorScreen(
    uiState: RhythmPatternUiState,
    onIntent: (RhythmPatternUiIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // El botón «atrás» cierra el editor (vuelve al listado) con el mismo efecto que Cancelar.
    PlatformBackHandler(enabled = true) { onIntent(RhythmPatternUiIntent.CloseEditor) }

    val editablePattern = uiState.editorAsPattern()
    val isNew = uiState.editorIsNew
    // Id del patrón sobre el que actúan los botones (incluye los predefinidos y las copias):
    // no se usa `editingPatternId` porque en un predefinido es null (se guardaría como copia).
    val actionPatternId = uiState.actionPatternId
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LaudateTopAppBar(
                title = { Text(if (isNew) "Nuevo Patrón Rítmico" else "Editar Patrón Rítmico") },
                navigationIcon = {
                    IconButton(onClick = { onIntent(RhythmPatternUiIntent.CloseEditor) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 3.dp
            ) {
                // Fila horizontal de **iconos** (nunca se apila en vertical), alineada a la derecha.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // `navigationBarsPadding` evita que los botones queden bajo la barra de
                        // gestos/navegación del sistema (la app dibuja edge-to-edge).
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (actionPatternId != null) {
                        DeleteActionButton(onClick = { confirmDelete = true })
                        ShareActionButton(
                            onClick = { onIntent(RhythmPatternUiIntent.SharePattern(actionPatternId)) }
                        )
                        CopyTextActionButton(
                            onClick = { onIntent(RhythmPatternUiIntent.CopyPatternAsText(actionPatternId)) }
                        )
                        DuplicateActionButton(
                            onClick = { onIntent(RhythmPatternUiIntent.DuplicatePattern(actionPatternId)) }
                        )
                    }
                    SaveActionButton(
                        onClick = { onIntent(RhythmPatternUiIntent.SavePattern) },
                        enabled = uiState.isEditorValid && uiState.editorName.isNotBlank()
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Nombre y compás
            OutlinedTextField(
                value = uiState.editorName,
                onValueChange = { onIntent(RhythmPatternUiIntent.UpdateEditorName(it)) },
                label = { Text("Nombre del patrón") },
                placeholder = { Text("Ej. Balada suave 4/4") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text("Compás:", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimeSignature.common.forEach { timeSignature ->
                    FilterChip(
                        selected = uiState.selectedTimeSignature == timeSignature,
                        onClick = { onIntent(RhythmPatternUiIntent.SetEditorTimeSignature(timeSignature)) },
                        label = { Text(timeSignature.toString()) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Modo de edición: la rejilla rápida «Beat & Stroke» o el editor avanzado por posiciones.
            // Ambos son vistas del MISMO modelo: lo que se edite en uno aparece en el otro.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RhythmEditorMode.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.editorMode == mode,
                        onClick = { onIntent(RhythmPatternUiIntent.SetEditorMode(mode)) },
                        label = { Text(if (mode == RhythmEditorMode.GRID) "Rejilla (fácil)" else "Avanzado") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Duración y validez del patrón
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Duración",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${formatBeats(uiState.editorTotalBeats)} / ${formatBeats(uiState.targetBeats)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (uiState.isEditorValid) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Representación del patrón: siempre visible, en cualquier modo de edición.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp)
            ) {
                if (uiState.editorEvents.isEmpty()) {
                    Text(
                        text = if (uiState.editorMode == RhythmEditorMode.GRID)
                            "Toca las casillas de abajo para escribir el ritmo"
                        else
                            "Añade figuras rítmicas para construir el patrón",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    PatternVisualization(
                        pattern = editablePattern,
                        notationMode = RhythmNotationMode.COMBINED,
                        activeEventIndex = uiState.activeEventIndex
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Previsualización sonora: BPM (80 por defecto) y Play/Stop (RF-306/RF-307).
            // Va SIEMPRE aquí, justo debajo de la representación del patrón, en cualquier modo
            // de edición: el reproductor no cambia de sitio al alternar Rejilla ↔ Avanzado.
            RhythmPreviewBar(
                bpm = uiState.previewBpm,
                isPlaying = uiState.isPlaying,
                enabled = uiState.editorEvents.isNotEmpty(),
                onAdjustBpm = { onIntent(RhythmPatternUiIntent.AdjustPreviewBpm(it)) },
                onTogglePlayback = { onIntent(RhythmPatternUiIntent.TogglePreviewPlayback) }
            )

            uiState.errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Número de compases del patrón: un ritmo puede ocupar más de un compás.
            BarCountControl(
                barCount = uiState.editorBarCount,
                onAddBar = { onIntent(RhythmPatternUiIntent.AddBar) },
                onRemoveBar = { onIntent(RhythmPatternUiIntent.RemoveBar) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // ── Editor por rejilla «Beat & Stroke» (vía rápida) ──────────────────────
            if (uiState.editorMode == RhythmEditorMode.GRID) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "1. Elige el trazo:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        StrokePalette(
                            active = uiState.paletteCell,
                            onSelect = { onIntent(RhythmPatternUiIntent.SetPaletteCell(it)) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "2. Toca las subdivisiones donde suena:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        BeatStrokeGrid(
                            cells = uiState.gridCells,
                            resolution = uiState.effectiveGridResolution,
                            timeSignature = uiState.selectedTimeSignature,
                            beatBoundaries = uiState.gridBeatBoundaries,
                            barBoundaries = uiState.gridBarBoundaries,
                            activeSlot = uiState.activeGridSlot,
                            onTapCell = { onIntent(RhythmPatternUiIntent.SetGridCell(it)) },
                            onLongPressCell = { onIntent(RhythmPatternUiIntent.ClearGridCell(it)) }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        GridLegend()

                        Spacer(modifier = Modifier.height(10.dp))

                        // Subdivisión de la rejilla (negras, corcheas, semicorcheas).
                        Text("Subdivisión:", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RhythmGridResolution.entries
                                .filter { it.supports(uiState.selectedTimeSignature) }
                                .forEach { resolution ->
                                    FilterChip(
                                        // Se marca la resolución **efectiva**: si el patrón no cabe
                                        // en la elegida, la rejilla usa otra y el chip debe reflejarlo.
                                        selected = uiState.effectiveGridResolution == resolution,
                                        onClick = {
                                            onIntent(RhythmPatternUiIntent.SetGridResolution(resolution))
                                        },
                                        label = {
                                            Text("${resolution.shortLabel} ${resolution.label}")
                                        }
                                    )
                                }
                        }

                        if (uiState.effectiveGridResolution != uiState.gridResolution) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Este patrón necesita subdivisiones de " +
                                    "${uiState.effectiveGridResolution.label.lowercase()} " +
                                    "para representarse sin perder figuras.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AssistChip(
                                onClick = { onIntent(RhythmPatternUiIntent.AutoAssignGridDirections) },
                                label = { Text("Auto") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            AssistChip(
                                onClick = { onIntent(RhythmPatternUiIntent.ClearEditor) },
                                label = { Text("Limpiar") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── Editor avanzado por posiciones (figuras, articulaciones y símbolos) ──
            if (uiState.editorMode == RhythmEditorMode.STRUCTURED) {
                // Indicador de la dirección automática del próximo golpe
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StrumSymbol(
                        direction = uiState.nextStrumDirection,
                        articulation = StrumArticulation.NONE,
                        size = 22.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Próximo golpe automático (alterna arriba y abajo; un silencio reinicia " +
                            "en abajo)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Posiciones del patrón: tocar una la selecciona para editarla abajo.
                if (uiState.editorEvents.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Posiciones (toca una para editarla):",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            PositionStrip(
                                events = uiState.editorEvents,
                                selectedIndex = uiState.selectedEventIndex,
                                onSelect = { onIntent(RhythmPatternUiIntent.SelectEvent(it)) }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Inspector único: figura, golpe, cuerdas, articulación y borrado.
                            val selectedIndex = uiState.selectedEventIndex
                                ?.takeIf { it in uiState.editorEvents.indices }
                            PositionInspector(
                                event = selectedIndex?.let { uiState.editorEvents[it] },
                                index = selectedIndex,
                                onSetFigure = { figure ->
                                    selectedIndex?.let {
                                        onIntent(RhythmPatternUiIntent.SetEventFigure(it, figure))
                                    }
                                },
                                onSetDirection = { direction ->
                                    selectedIndex?.let {
                                        onIntent(RhythmPatternUiIntent.SetEventDirection(it, direction))
                                    }
                                },
                                onSetArticulation = { articulation ->
                                    selectedIndex?.let {
                                        onIntent(RhythmPatternUiIntent.SetEventArticulation(it, articulation))
                                    }
                                },
                                onSetRange = { range ->
                                    selectedIndex?.let {
                                        onIntent(RhythmPatternUiIntent.SetEventRange(it, range))
                                    }
                                },
                                onRemove = {
                                    selectedIndex?.let {
                                        onIntent(RhythmPatternUiIntent.RemoveEvent(it))
                                        onIntent(RhythmPatternUiIntent.SelectEvent(null))
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Paleta de figuras
                Text("Añadir figura:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RhythmFigure.notes.forEach { figure ->
                        FigureChip(figure = figure, onAdd = {
                            onIntent(RhythmPatternUiIntent.AddEvent(figure))
                        })
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Añadir silencio:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RhythmFigure.rests.forEach { figure ->
                        FigureChip(figure = figure, onAdd = {
                            onIntent(RhythmPatternUiIntent.AddEvent(figure))
                        })
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (confirmDelete && actionPatternId != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar Patrón") },
            text = {
                Text(
                    "¿Deseas eliminar el patrón \"${uiState.editorName}\"? " +
                        "Podrás deshacerlo desde el aviso inferior."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        // Cierra el editor y borra por id (vale para propios y predefinidos).
                        onIntent(RhythmPatternUiIntent.CloseEditor)
                        onIntent(RhythmPatternUiIntent.DeletePattern(actionPatternId))
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            }
        )
    }
}
