package com.grafitto.laudate.ui.celebration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.celebration.CelebrationType
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.music.NotationFormat
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.ui.common.DeleteActionButton
import com.grafitto.laudate.ui.common.PdfActionButton
import com.grafitto.laudate.ui.common.PdfExportDialog
import com.grafitto.laudate.ui.common.SaveActionButton
import com.grafitto.laudate.ui.common.ShareActionButton
import com.grafitto.laudate.ui.common.LaudateTopAppBar
import com.grafitto.laudate.ui.search.SongSearchSheet
import com.grafitto.laudate.ui.search.SongSearchUiIntent
import com.grafitto.laudate.ui.search.SongSearchUiState

/** Etiqueta usada cuando un canto no se asigna a ningún momento (eventos no litúrgicos). */
private const val NO_MOMENT_LABEL = "Sin asignar"

/**
 * Sugerencias **adicionales** para eventos que no son misa. No son partes de la liturgia;
 * se muestran al final del desplegable, separadas de los momentos litúrgicos reales.
 */
private val ADDITIONAL_MOMENT_SUGGESTIONS = listOf(
    "Ambientación",
    "Inicio",
    "Alabanza",
    "Invocación al Espíritu Santo",
    "Adoración",
    "Meditación",
    "Perdón",
    "Acción de Gracias",
    "Envío / Cierre"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CelebrationEditorScreen(
    uiState: CelebrationEditorUiState,
    onIntent: (CelebrationEditorUiIntent) -> Unit,
    onNavigateBack: () -> Unit,
    searchState: SongSearchUiState = SongSearchUiState(isLoading = false),
    onSearchIntent: (SongSearchUiIntent) -> Unit = {},
    /** Nomenclatura global de acordes para mostrar los tonos (Do-Re-Mi / C-D-E). */
    notationSystem: NotationSystem = NotationSystem.LATIN,
    modifier: Modifier = Modifier
) {
    var showSongSearch by remember { mutableStateOf(false) }
    var pendingSong by remember { mutableStateOf<Song?>(null) }
    var showPdfDialog by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // La barra superior se oculta al bajar y reaparece al subir (comportamiento tipo Gmail).
    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val celebrationType = CelebrationType.fromStoredType(uiState.celebrationType)

    // Antes de generar el PDF se pregunta el modo; la elección se recuerda.
    if (showPdfDialog) {
        PdfExportDialog(
            subject = uiState.name.ifBlank { "este repertorio" },
            initialMode = uiState.pdfDisplayMode,
            onDismiss = { showPdfDialog = false },
            onConfirm = { mode ->
                showPdfDialog = false
                onIntent(CelebrationEditorUiIntent.PrintCelebration(mode))
            }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar Repertorio") },
            text = { Text("¿Deseas eliminar «${uiState.name}»? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onIntent(CelebrationEditorUiIntent.DeleteCelebration)
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

    Scaffold(
        topBar = {
            LaudateTopAppBar(
                title = {
                    Text(celebrationType.editorTitle(isNew = uiState.celebrationId == null))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                scrollBehavior = topBarScrollBehavior
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
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.celebrationId != null) {
                        DeleteActionButton(onClick = { confirmDelete = true })
                    }
                    ShareActionButton(
                        onClick = { onIntent(CelebrationEditorUiIntent.ShareCelebration) },
                        enabled = uiState.items.isNotEmpty()
                    )
                    PdfActionButton(
                        onClick = { showPdfDialog = true },
                        enabled = uiState.items.isNotEmpty()
                    )
                    SaveActionButton(
                        onClick = { onIntent(CelebrationEditorUiIntent.SaveCelebration) },
                        isSaving = uiState.isSaving
                    )
                }
            }
        },
        modifier = modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            uiState.errorMessage?.let { errorMsg ->
                Text(
                    text = errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Selector del Tipo de Celebración / Evento
            var typeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = !typeExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = celebrationType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Tipo de celebración o evento") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    CelebrationType.selectable.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = {
                                onIntent(CelebrationEditorUiIntent.UpdateCelebrationType(type.displayName))
                                typeExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.name,
                onValueChange = { onIntent(CelebrationEditorUiIntent.UpdateName(it)) },
                label = { Text("${celebrationType.nameFieldLabel} *") },
                placeholder = { Text(celebrationType.namePlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.description,
                onValueChange = { onIntent(CelebrationEditorUiIntent.UpdateDescription(it)) },
                label = { Text("Descripción o notas") },
                placeholder = { Text("Ej. Celebrante: P. Juan, lecturas ciclo B...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${celebrationType.songsSectionLabel} (${uiState.items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Button(onClick = { showSongSearch = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar Canto")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Añadir canto")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(uiState.items, key = { _, item -> item.id }) { index, item ->
                    val song = uiState.allSongs.firstOrNull { it.id == item.songId }
                    var momentMenuExpanded by remember { mutableStateOf(false) }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.liturgicalMoment
                                            .takeIf { it.isNotBlank() }
                                            ?.uppercase()
                                            ?: NO_MOMENT_LABEL.uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(onClick = { momentMenuExpanded = true }) {
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Cambiar momento",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                MomentDropdownMenu(
                                    expanded = momentMenuExpanded,
                                    onDismissRequest = { momentMenuExpanded = false },
                                    categories = uiState.availableCategories,
                                    celebrationType = celebrationType,
                                    onMomentSelected = { moment ->
                                        onIntent(CelebrationEditorUiIntent.UpdateItemMoment(item.id, moment))
                                        momentMenuExpanded = false
                                    }
                                )
                                Text(
                                    text = song?.title ?: "Canción no disponible",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                // Iconos de contenido: púa (tablatura) y clip (adjuntos).
                                val tabs = uiState.songIdsWithTablatures
                                val atts = uiState.songIdsWithAttachments
                                if (song != null && (song.id in tabs || song.id in atts)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (song.id in tabs) {
                                            Icon(
                                                imageVector = com.grafitto.laudate.ui.common.LaudateIcons.Guitar,
                                                contentDescription = "Tiene tablatura",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        if (song.id in atts) {
                                            Icon(
                                                imageVector = Icons.Default.AttachFile,
                                                contentDescription = "Tiene adjuntos",
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Tono: ${NotationFormat.key(item.overrideKey ?: song?.originalKey ?: "C", notationSystem)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (index > 0) onIntent(CelebrationEditorUiIntent.MoveItem(index, index - 1))
                                    },
                                    enabled = index > 0
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Subir")
                                }

                                IconButton(
                                    onClick = {
                                        if (index < uiState.items.size - 1) {
                                            onIntent(CelebrationEditorUiIntent.MoveItem(index, index + 1))
                                        }
                                    },
                                    enabled = index < uiState.items.size - 1
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Bajar")
                                }

                                IconButton(onClick = { onIntent(CelebrationEditorUiIntent.RemoveItem(item.id)) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Quitar",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSongSearch) {
        SongSearchSheet(
            state = searchState,
            onIntent = onSearchIntent,
            notationSystem = notationSystem,
            onDismissRequest = { showSongSearch = false },
            onSongClick = { song ->
                pendingSong = song
                showSongSearch = false
            },
            title = "Añadir canto",
            trailingIcon = { Icons.Default.Add },
            trailingIconDescription = "Elegir canto",
            onTrailingClick = { song ->
                pendingSong = song
                showSongSearch = false
            }
        )
    }

    // Al elegir un canto, se pide el momento/sección (sugerido a partir del propio canto).
    pendingSong?.let { song ->
        MomentPickerDialog(
            song = song,
            celebrationType = celebrationType,
            categories = uiState.availableCategories,
            onDismissRequest = { pendingSong = null },
            onMomentSelected = { moment ->
                onIntent(
                    CelebrationEditorUiIntent.AddSongItem(
                        songId = song.id,
                        liturgicalMoment = moment,
                        overrideKey = song.originalKey
                    )
                )
                pendingSong = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MomentPickerDialog(
    song: Song,
    celebrationType: CelebrationType,
    categories: List<LiturgicalCategory>,
    onDismissRequest: () -> Unit,
    onMomentSelected: (String) -> Unit
) {
    // Sugerencia: primer momento por defecto del canto; si no tiene, el primer momento litúrgico
    // (en misas) o «Sin asignar» (en eventos no litúrgicos).
    val suggested = song.defaultLiturgicalMoments.firstOrNull()
        ?: if (celebrationType.isMass) categories.firstOrNull()?.name.orEmpty() else ""
    var momentExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(if (celebrationType.isMass) "Asignar canto a un momento" else "Añadir canto al evento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = song.number?.takeIf { it.isNotBlank() }?.let { "$it · ${song.title}" } ?: song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Box {
                    OutlinedTextField(
                        value = suggested.ifBlank { NO_MOMENT_LABEL },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Momento / sección") },
                        trailingIcon = {
                            IconButton(onClick = { momentExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Elegir momento")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    MomentDropdownMenu(
                        expanded = momentExpanded,
                        onDismissRequest = { momentExpanded = false },
                        categories = categories,
                        celebrationType = celebrationType,
                        onMomentSelected = {
                            momentExpanded = false
                            onMomentSelected(it)
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onMomentSelected(suggested.ifBlank { NO_MOMENT_LABEL }) }) {
                Text(if (celebrationType.isMass) "Asignar" else "Añadir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancelar") }
        }
    )
}

/**
 * Menú con las opciones de momento/sección: primero las partes de la liturgia (orden canónico)
 * y, al final y separadas, las sugerencias adicionales no litúrgicas.
 */
@Composable
private fun MomentDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    categories: List<LiturgicalCategory>,
    celebrationType: CelebrationType,
    onMomentSelected: (String) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        val liturgicalMoments = categories.map { it.name }.distinct()
        if (!celebrationType.isMass) {
            DropdownMenuItem(
                text = { Text(NO_MOMENT_LABEL) },
                onClick = { onMomentSelected(NO_MOMENT_LABEL) }
            )
        }
        liturgicalMoments.forEach { momentName ->
            DropdownMenuItem(
                text = { Text(momentName) },
                onClick = { onMomentSelected(momentName) }
            )
        }

        val additionalSuggestions = ADDITIONAL_MOMENT_SUGGESTIONS.filterNot { it in liturgicalMoments }
        if (additionalSuggestions.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = "Adicionales (no litúrgicos)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            additionalSuggestions.forEach { momentName ->
                DropdownMenuItem(
                    text = { Text(momentName) },
                    onClick = { onMomentSelected(momentName) }
                )
            }
        }
    }
}
