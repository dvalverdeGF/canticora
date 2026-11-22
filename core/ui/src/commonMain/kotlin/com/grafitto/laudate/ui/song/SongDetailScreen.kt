package com.grafitto.laudate.ui.song

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertLink
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grafitto.laudate.domain.model.attachment.AttachmentKind
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.model.music.NotationFormat
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.chordpro.ChordProSymbol
import com.grafitto.laudate.domain.model.rhythm.RhythmNotationMode
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.song.ChordAnchor
import com.grafitto.laudate.domain.model.song.LineType
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.tablature.TabDisplayPosition
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.ui.attachment.SongAttachmentsSection
import com.grafitto.laudate.domain.model.song.SongSection
import com.grafitto.laudate.ui.chordpro.RichSymbolText
import com.grafitto.laudate.ui.common.Density
import com.grafitto.laudate.ui.common.HairlineDivider
import com.grafitto.laudate.ui.common.MicroChip
import com.grafitto.laudate.ui.common.MicroChipRow
import com.grafitto.laudate.ui.common.MusicFigureGlyph
import com.grafitto.laudate.ui.common.MusicGlyph
import com.grafitto.laudate.ui.common.PillButton
import com.grafitto.laudate.ui.common.StructureBadge
import com.grafitto.laudate.ui.common.StructureMap
import com.grafitto.laudate.ui.common.LaudateTopAppBar
import com.grafitto.laudate.ui.rhythm.StrumSymbol
import com.grafitto.laudate.ui.tablature.TablatureView
import com.grafitto.laudate.ui.navigation.SettingsOption
import com.grafitto.laudate.ui.theme.SectionColors
import com.grafitto.laudate.ui.platform.KeepScreenOn
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailScreen(
    uiState: SongDetailUiState,
    onIntent: (SongDetailUiIntent) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onEditClick: (() -> Unit)? = null,
    /**
     * Fuerza el **modo lectura sin distracciones** al margen del estado.
     *
     * Lo usan contenedores que gestionan su propio foco (p. ej. el Modo Celebración en vivo):
     * `null` deja que mande [SongDetailUiState.isFocusMode]; `true`/`false` lo impone.
     */
    focusModeOverride: Boolean? = null,
    /** Si se pasa, aparece el botón de alternar el modo sin distracciones. */
    onToggleFocus: (() -> Unit)? = null,
    /**
     * Muestra las acciones de la barra superior (editar, eliminar, modo sin distracciones).
     * La vista previa del editor lo pone a `false`: allí el canto aún no está guardado y esas
     * acciones no aplican.
     */
    showActions: Boolean = true,
    /**
     * Muestra la barra superior con el título del canto.
     */
    showTitleBar: Boolean = true,
    /**
     * Acciones extra que se añaden a la derecha de la barra de título, **a la altura del título**.
     * El **Modo Celebración en vivo** las usa para sus iconos de pantalla completa y de cierre.
     */
    topBarActions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
    /** Subtítulo opcional bajo el título (el modo en vivo muestra aquí el progreso litúrgico). */
    subtitle: (@Composable () -> Unit)? = null,
    /** Inset adicional para el contenido (el modo en vivo reserva aquí la barra de estado). */
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(),
    /** Abre el selector de archivos para añadir un adjunto al canto. */
    onAddAttachmentFileClick: (() -> Unit)? = null,
    /** Abre el listado de tablaturas del canto (ADR-017, Feature T). */
    onTablaturesClick: (() -> Unit)? = null,
    /** Abre un adjunto con la app externa correspondiente. */
    onOpenAttachmentExternally: ((SongAttachment) -> Unit)? = null,
    /** Descarga/exporta el fichero de un adjunto al destino elegido. */
    onExportAttachmentClick: ((SongAttachment) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isFocusMode = focusModeOverride ?: uiState.isFocusMode

    // Keep Screen On: durante la lectura no debe apagarse la pantalla (RF-302, RNF-301).
    KeepScreenOn()

    var showDeleteDialog by remember { mutableStateOf(false) }

    // `uiState` viene de otro módulo (KMP), así que el compilador no puede hacer *smart cast*
    // sobre sus propiedades: hay que copiarlas a locales antes de comprobar null. Ver ADR-013.
    val song = uiState.song
    val errorMessage = uiState.errorMessage

    Scaffold(
        topBar = {
            // En modo sin distracciones desaparece la barra completa (título, editar, eliminar)
            // para dejar solo la canción en pantalla.
            if (!isFocusMode && showTitleBar) {
                LaudateTopAppBar(
                    // El título se limita a una línea: en pantallas estrechas la barra no debe
                    // crecer ni partir el texto letra a letra (se recorta con elipsis).
                    title = {
                        Column {
                            Text(
                                text = uiState.song?.title ?: "Canticora",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            subtitle?.invoke()
                        }
                    },
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                            }
                        }
                    },
                    actions = {
                        if (showActions) {
                            if (onToggleFocus != null) {
                                IconButton(onClick = onToggleFocus) {
                                    Icon(
                                        Icons.Default.Fullscreen,
                                        contentDescription = "Modo sin distracciones"
                                    )
                                }
                            }
                            if (onEditClick != null && uiState.song != null) {
                                IconButton(onClick = onEditClick) {
                                    Icon(Icons.Default.Edit, contentDescription = "Editar Canción")
                                }
                            }
                            if (uiState.song != null) {
                                IconButton(onClick = { showDeleteDialog = true }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Eliminar Canción",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        topBarActions?.invoke(this)
                    }
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(contentPadding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                errorMessage != null -> {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                song != null -> {
                    SongContent(
                        song = song,
                        uiState = uiState,
                        onIntent = onIntent,
                        focus = isFocusMode,
                        onAddAttachmentFileClick = onAddAttachmentFileClick,
                        onTablaturesClick = onTablaturesClick,
                        onOpenAttachmentExternally = onOpenAttachmentExternally,
                        onExportAttachmentClick = onExportAttachmentClick
                    )
                }
                else -> {
                    Text(
                        text = "Seleccione o cargue un canto",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // En modo sin distracciones, una acción discreta permite salir sin desorientar al usuario.
            if (isFocusMode && onToggleFocus != null) {
                FilledTonalIconButton(
                    onClick = onToggleFocus,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        // El Scaffold ya reserva el inset de la barra de estado en `innerPadding`:
                        // el botón queda alineado con el título del canto, sin doble separación.
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Default.FullscreenExit,
                        contentDescription = "Mostrar controles",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDeleteDialog && song != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar Canción") },
            text = { Text("¿Estás seguro de que deseas eliminar \"${song.title}\"? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onIntent(SongDetailUiIntent.DeleteSong(song.id))
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun SongContent(
    song: Song,
    uiState: SongDetailUiState,
    onIntent: (SongDetailUiIntent) -> Unit,
    focus: Boolean,
    onAddAttachmentFileClick: (() -> Unit)?,
    onTablaturesClick: (() -> Unit)?,
    onOpenAttachmentExternally: ((SongAttachment) -> Unit)?,
    onExportAttachmentClick: ((SongAttachment) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Density.Compact)
    ) {
        if (focus) {
            // Modo lectura sin distracciones: se conserva solo el título y (si lo hay) el autor.
            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            song.author?.takeIf { it.isNotBlank() }?.let { author ->
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(Density.Compact))
        }

        // Directivas del canto en **micro-badges**: número, tonalidad, capo, compás, tempo y autor.
        // Todo lo que el músico necesita de un vistazo cabe en una línea de 20 dp, sin robarle
        // altura a la letra. Los detalles largos (notas, rasgueo) se despliegan a demanda.
        if (!focus) {
            val hasDetails = !song.notes.isNullOrBlank() || uiState.rhythmPattern != null
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MicroChipRow(modifier = Modifier.weight(1f)) {
                    song.number?.takeIf { it.isNotBlank() }?.let {
                        MicroChip("Nº $it", color = MaterialTheme.colorScheme.primary)
                    }
                    MicroChip(
                        text = "Tonalidad: ${NotationFormat.key(uiState.currentKey, uiState.notationSystem)}",
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (song.capo > 0) {
                        MicroChip("Capo: ${song.capo}", color = MaterialTheme.colorScheme.tertiary)
                    }
                    song.rhythm?.takeIf { it.isNotBlank() }?.let {
                        MicroChip(it, color = MaterialTheme.colorScheme.secondary)
                    }
                    song.tempoBpm?.let {
                        MicroChip(
                            text = "= $it",
                            color = MaterialTheme.colorScheme.tertiary,
                            leading = { MusicGlyph(glyph = "\u2669", size = 11.dp) }
                        )
                    }
                    song.author?.takeIf { it.isNotBlank() }?.let {
                        MicroChip(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (hasDetails) {
                    IconButton(
                        onClick = { onIntent(SongDetailUiIntent.ToggleMetadata) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isMetadataExpanded) {
                                Icons.Default.ExpandLess
                            } else {
                                Icons.Default.ExpandMore
                            },
                            contentDescription = if (uiState.isMetadataExpanded) {
                                "Ocultar detalles del canto"
                            } else {
                                "Ver detalles del canto"
                            },
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.isMetadataExpanded) {
                song.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        text = "Notas: $notes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Density.Tight)
                    )
                }
                uiState.rhythmPattern?.let { pattern ->
                    Text(
                        text = "Rasgueo: ${pattern.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Density.Snug)
                    )
                    RhythmPatternGrid(
                        pattern = pattern,
                        rhythmNotationMode = uiState.rhythmNotationMode
                    )
                }
            }

            Spacer(modifier = Modifier.height(Density.Compact))
        }

        if (!focus) {
            // Barra de controles de transposición, modos y tamaño de fuente (plegada por defecto)
            ReaderControls(
                originalKey = uiState.originalKey,
                currentKey = uiState.currentKey,
                semitones = uiState.transpositionSemitones,
                notationSystem = uiState.notationSystem,
                tablatureInstruments = uiState.tablatureInstruments,
                preferredTablatureInstrument = uiState.preferredTablatureInstrument,
                onSelectTablatureInstrument = { instrument ->
                    onIntent(SongDetailUiIntent.SelectTablatureInstrument(instrument))
                },
                song = song,
                isExpanded = uiState.isControlsExpanded,
                onToggleExpanded = { onIntent(SongDetailUiIntent.ToggleControls) },
                onTranspose = { delta -> onIntent(SongDetailUiIntent.TransposeKey(delta)) },
                onResetTranspose = { onIntent(SongDetailUiIntent.ResetTransposition) }
            )

            Spacer(modifier = Modifier.height(Density.Compact))
        }

        // ── Cuerpo: letra/acordes y/o tablatura según el modo y la posición elegidos ────────────
        val effectiveMode = uiState.effectiveDisplayMode
        val showLyricsLayer = effectiveMode.showsLyrics || effectiveMode.showsChords
        val visibleTab = uiState.visibleTablature?.takeIf { effectiveMode.showsTablature }
        // Secciones del canto para el selector de la barra de colocación: la tablatura se puede
        // atar a la intro, una estrofa, el coro… o quedarse en el bloque general del final.
        val sectionOptions = remember(song.sections) {
            song.sections.map { section ->
                SettingsOption(
                    id = section.id,
                    label = section.headerTitle ?: SectionColors.labelFor(section.sectionType.name)
                )
            }
        }

        when {
            // Solo tablatura: ocupa todo el cuerpo. Muestra las tablaturas **filtradas por el
            // instrumento preferido** (fallo 8: antes salían TODAS, mezclando guitarra y bajo).
            visibleTab != null && !showLyricsLayer -> {
                TablatureSection(
                    tablatures = uiState.visibleTablatures,
                    onIntent = onIntent,
                    modifier = Modifier.weight(1f),
                    scale = uiState.tablatureScale,
                    numberScale = uiState.tablatureNumberScale,
                    staffOptions = uiState.tablatureStaffOptions,
                    sections = sectionOptions,
                    selectedTablatureId = uiState.selectedTablatureId,
                    tabDisplayPosition = uiState.tabDisplayPosition,
                    // Sin letra a la vista, la posición respecto a ella no significa nada.
                    showPositionControl = false
                )
            }

            // Letra + tablatura a la derecha: dos columnas independientes, cada una con su scroll.
            // La columna lateral solo lleva las GENERALES (las de sección ya van intercaladas).
            visibleTab != null && uiState.tabDisplayPosition == TabDisplayPosition.RIGHT -> {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Density.Compact)
                ) {
                    SongLyricsContent(
                        song = song, uiState = uiState, focus = focus,
                        onIntent = onIntent, onTablaturesClick = onTablaturesClick,
                        onAddAttachmentFileClick = onAddAttachmentFileClick,
                        onOpenAttachmentExternally = onOpenAttachmentExternally,
                        onExportAttachmentClick = onExportAttachmentClick,
                        modifier = Modifier.weight(1.1f)
                    )
                    TablatureSection(
                        tablatures = uiState.generalTablatures,
                        onIntent = onIntent,
                        modifier = Modifier.weight(0.9f),
                        scale = uiState.tablatureScale,
                        numberScale = uiState.tablatureNumberScale,
                        staffOptions = uiState.tablatureStaffOptions,
                        sections = sectionOptions,
                        selectedTablatureId = uiState.selectedTablatureId,
                        tabDisplayPosition = uiState.tabDisplayPosition
                    )
                }
            }

            // Letra + tablatura arriba o abajo: una columna; la tablatura se acota a una altura
            // razonable para que ambas capas queden visibles sin desplazar la letra fuera de pantalla.
            visibleTab != null -> {
                Column(modifier = Modifier.weight(1f)) {
                    if (uiState.tabDisplayPosition == TabDisplayPosition.BEFORE) {
                        TablatureSection(
                            tablatures = uiState.generalTablatures,
                            onIntent = onIntent,
                            modifier = Modifier.heightIn(max = 260.dp),
                            scale = uiState.tablatureScale,
                            numberScale = uiState.tablatureNumberScale,
                            staffOptions = uiState.tablatureStaffOptions,
                            sections = sectionOptions,
                            selectedTablatureId = uiState.selectedTablatureId,
                            tabDisplayPosition = uiState.tabDisplayPosition
                        )
                        Spacer(modifier = Modifier.height(Density.Compact))
                        SongLyricsContent(
                            song = song, uiState = uiState, focus = focus,
                            onIntent = onIntent, onTablaturesClick = onTablaturesClick,
                            onAddAttachmentFileClick = onAddAttachmentFileClick,
                            onOpenAttachmentExternally = onOpenAttachmentExternally,
                            onExportAttachmentClick = onExportAttachmentClick,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        SongLyricsContent(
                            song = song, uiState = uiState, focus = focus,
                            onIntent = onIntent, onTablaturesClick = onTablaturesClick,
                            onAddAttachmentFileClick = onAddAttachmentFileClick,
                            onOpenAttachmentExternally = onOpenAttachmentExternally,
                            onExportAttachmentClick = onExportAttachmentClick,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.height(Density.Compact))
                        TablatureSection(
                            tablatures = uiState.generalTablatures,
                            onIntent = onIntent,
                            modifier = Modifier.heightIn(max = 260.dp),
                            scale = uiState.tablatureScale,
                            numberScale = uiState.tablatureNumberScale,
                            staffOptions = uiState.tablatureStaffOptions,
                            sections = sectionOptions,
                            selectedTablatureId = uiState.selectedTablatureId,
                            tabDisplayPosition = uiState.tabDisplayPosition
                        )
                    }
                }
            }

            else -> {
                SongLyricsContent(
                    song = song, uiState = uiState, focus = focus,
                    onIntent = onIntent, onTablaturesClick = onTablaturesClick,
                    onAddAttachmentFileClick = onAddAttachmentFileClick,
                    onOpenAttachmentExternally = onOpenAttachmentExternally,
                    onExportAttachmentClick = onExportAttachmentClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Bloque de la letra (y acordes): mapa de estructura + secciones + tablaturas y adjuntos. */
@Composable
private fun SongLyricsContent(
    song: Song,
    uiState: SongDetailUiState,
    focus: Boolean,
    onIntent: (SongDetailUiIntent) -> Unit,
    onTablaturesClick: (() -> Unit)?,
    onAddAttachmentFileClick: (() -> Unit)?,
    onOpenAttachmentExternally: ((SongAttachment) -> Unit)?,
    onExportAttachmentClick: ((SongAttachment) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Mapa de estructura: la forma del canto en badges diminutos (`Int`, `Est 1`, `Coro`, `Fin`).
    // Se numera cada tipo cuando aparece más de una vez, para distinguir estrofas entre sí.
    val baseLabels = song.sections.map { SectionColors.shortLabelFor(it.sectionType.name) }
    val totals = baseLabels.groupingBy { it }.eachCount()
    val seen = mutableMapOf<String, Int>()
    val badges = song.sections.mapIndexed { index, section ->
        val base = baseLabels[index]
        val occurrence = (seen[base] ?: 0) + 1
        seen[base] = occurrence
        StructureBadge(
            label = if ((totals[base] ?: 0) > 1) "$base $occurrence" else base,
            color = SectionColors.forSectionType(section.sectionType.name)
        )
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(Density.Compact)
    ) {
        // El mapa queda **pegado** arriba mientras se recorre la letra: el músico siempre sabe en
        // qué parte está y puede saltar a otra con un toque.
        if (!focus && badges.isNotEmpty()) {
            stickyHeader(key = "structure") {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        StructureMap(
                            badges = badges,
                            onSelect = { index ->
                                scope.launch { listState.animateScrollToItem(index + 1) }
                            }
                        )
                        HairlineDivider()
                    }
                }
            }
        }

        items(song.sections, key = { it.id }) { section ->
            SectionItem(
                section = section,
                notationSystem = uiState.notationSystem,
                displayMode = uiState.effectiveDisplayMode,
                fontSizeSp = uiState.fontSizeSp,
                rhythmNotationMode = uiState.rhythmNotationMode,
                // Solo se muestra rejilla bajo una sección si esta declara su propio patrón
                // (`{x_rhythm: Nombre}`). El patrón global del canto ya aparece una vez en la
                // tarjeta de metadatos; repetirlo en cada sección sería ruido visual.
                rhythmPattern = uiState.sectionRhythmPatterns[section.sequenceOrder],
                // Tablaturas asociadas a esta sección (intercaladas junto a su parte).
                sectionTablatures = if (uiState.effectiveDisplayMode.showsTablature) {
                    uiState.tablaturesForSection(section.id)
                } else {
                    emptyList()
                },
                tablatureNumberScale = uiState.tablatureNumberScale,
                tablatureStaffOptions = uiState.tablatureStaffOptions
            )
        }

        // Los adjuntos y la gestión de tablaturas se ocultan en el modo sin distracciones.
        if (!focus) {
            if (onTablaturesClick != null) {
                item(key = "tablatures") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTablaturesClick() },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Density.CardPadding),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Density.CardPadding)
                        ) {
                            Icon(
                                imageVector = com.grafitto.laudate.ui.common.LaudateIcons.Guitar,
                                contentDescription = null,
                                // 22 dp: a 18 la silueta de la guitarra se empasta y no se reconoce.
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tablaturas",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Guitarra, laúd, bajo, ukelele…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item(key = "attachments") {
                SongAttachmentsSection(
                    attachments = uiState.attachments,
                    onAddFileClick = onAddAttachmentFileClick,
                    onAddLink = { url, title ->
                        onIntent(SongDetailUiIntent.AddAttachmentLink(url, title))
                    },
                    onOpen = onOpenAttachmentExternally,
                    onExport = onExportAttachmentClick,
                    onDelete = { attachment ->
                        onIntent(SongDetailUiIntent.DeleteAttachment(attachment))
                    }
                )
            }
        }
    }
}

/**
 * Bloque de **tablaturas generales** (ADR-017): las que no están asociadas a una sección concreta.
 * Las asociadas a una sección se muestran intercaladas junto a su parte (ver [SectionItem]).
 *
 * Lleva encima la [TablaturePlacementBar]: elegir cuál se ve, dónde va y en qué orden. Se muestra
 * **solo la tablatura elegida** para no llenar la pantalla de rejillas cuando el canto tiene varias.
 */
@Composable
private fun TablatureSection(
    tablatures: List<Tablature>,
    onIntent: (SongDetailUiIntent) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    numberScale: Float = 1f,
    /** Ajustes del pentagrama de tablatura (preferencia global, D-48). */
    staffOptions: com.grafitto.laudate.domain.tablature.TabStaffOptions =
        com.grafitto.laudate.domain.tablature.TabStaffOptions(),
    sections: List<SettingsOption> = emptyList(),
    selectedTablatureId: String? = null,
    tabDisplayPosition: TabDisplayPosition = TabDisplayPosition.AFTER,
    /** La barra de colocación se oculta en la vista previa de solo lectura. */
    showPlacementBar: Boolean = true,
    /** La posición respecto a la letra solo se ofrece cuando la letra se está viendo. */
    showPositionControl: Boolean = true
) {
    if (tablatures.isEmpty()) return
    val selected = tablatures.firstOrNull { it.id == selectedTablatureId } ?: tablatures.first()
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        if (showPlacementBar) {
            TablaturePlacementBar(
                tablatures = tablatures,
                selectedTablatureId = selected.id,
                sections = sections,
                tabDisplayPosition = tabDisplayPosition,
                onSelectTablature = { onIntent(SongDetailUiIntent.SelectTablature(it)) },
                onSelectPosition = { onIntent(SongDetailUiIntent.ChangeTabDisplayPosition(it)) },
                onSelectSection = { onIntent(SongDetailUiIntent.MoveTablatureToSection(selected.id, it)) },
                onMoveOrder = { onIntent(SongDetailUiIntent.MoveTablatureOrder(selected.id, it)) },
                showPositionControl = showPositionControl
            )
            HairlineDivider()
        }
        TablatureView(
            tablature = selected,
            scale = scale,
            numberScale = numberScale,
            options = staffOptions
        )
    }
}

@Composable
private fun ReaderControls(
    originalKey: String,
    currentKey: String,
    semitones: Int,
    notationSystem: NotationSystem,
    /** Instrumentos con tablatura de este canto (para elegir cuál se toca). */
    tablatureInstruments: List<String> = emptyList(),
    /** Instrumento preferido global (o `null` = se muestran todas las tablaturas). */
    preferredTablatureInstrument: String? = null,
    onSelectTablatureInstrument: (String?) -> Unit = {},
    song: Song,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onTranspose: (Int) -> Unit,
    onResetTranspose: () -> Unit
) {
    // Controles **vivos** de lectura: transposición y elección de la tablatura que se toca. Se usan
    // a media interpretación, así que van pegados a la letra. Las **preferencias** de lectura
    // (notación del ritmo, tamaño de letra) y los ajustes globales viven en el panel de la derecha:
    // duplicarlos aquí era el origen de la «doble botonera» que confundía al usuario.
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(Density.CardPadding)) {
            // Cabecera siempre visible: plegar/desplegar + resumen del tono.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                horizontalArrangement = Arrangement.spacedBy(Density.Snug),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tono: ${NotationFormat.key(currentKey, notationSystem)}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (semitones != 0) {
                    MicroChip(
                        text = "${if (semitones > 0) "+" else ""}$semitones st",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                MicroChip(
                    text = "Orig. ${NotationFormat.key(originalKey, notationSystem)}",
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                song.tempoBpm?.let { bpm ->
                    MicroChip(text = "\u2669 = $bpm", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Contraer controles" else "Expandir controles",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(Density.Snug))

                // Transposición: dos botones (♭/♯) y un reinicio. El número de semitonos se muestra
                // como badge, no como botonera.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Density.Snug)
                ) {
                    PillButton(
                        text = "\u266d",
                        onClick = { onTranspose(-1) },
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    PillButton(
                        text = "\u266f",
                        onClick = { onTranspose(+1) },
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    PillButton(
                        text = "Original",
                        onClick = onResetTranspose,
                        enabled = semitones != 0,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Instrumento que se toca: si el canto tiene tablaturas de VARIOS instrumentos
                // (guitarra, laúd, bajo…), se elige cuál mostrar. La preferencia es **global** y se
                // recuerda para todas las canciones.
                if (tablatureInstruments.size > 1) {
                    Spacer(modifier = Modifier.height(Density.Snug))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Density.Snug)
                    ) {
                        Text(
                            "Tablatura:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        PillButton(
                            text = "Todas",
                            onClick = { onSelectTablatureInstrument(null) },
                            selected = preferredTablatureInstrument == null,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        tablatureInstruments.forEach { instrument ->
                            PillButton(
                                text = instrument,
                                onClick = { onSelectTablatureInstrument(instrument) },
                                selected = preferredTablatureInstrument == instrument,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Representación del patrón rítmico y tamaño de letra viven en el **panel de ajustes**
                // de la derecha (sección «Lectura»): son preferencias de lectura, no controles vivos.
                // Duplicarlos aquí era el origen de la «doble botonera» que confundía al usuario.
            }
        }
    }
}

private fun buildChordsLine(
    chords: List<ChordAnchor>,
    lyricsLength: Int,
    notationSystem: NotationSystem
): String {
    if (chords.isEmpty()) return ""
    val sorted = chords.sortedBy { it.characterIndex }
    val sb = StringBuilder()
    var currentPos = 0
    for (anchor in sorted) {
        val targetPos = anchor.characterIndex
        val spacesNeeded = maxOf(1, targetPos - currentPos)
        if (currentPos > 0) {
            sb.append(" ".repeat(spacesNeeded))
            currentPos += spacesNeeded
        } else if (targetPos > 0) {
            sb.append(" ".repeat(targetPos))
            currentPos += targetPos
        }
        val chordStr = anchor.chord.toFormattedString(notationSystem)
        sb.append(chordStr)
        currentPos += chordStr.length
    }
    return sb.toString()
}

@Composable
private fun SectionItem(
    section: SongSection,
    notationSystem: NotationSystem,
    displayMode: SongDisplayMode,
    fontSizeSp: Float,
    rhythmNotationMode: RhythmNotationMode,
    rhythmPattern: RhythmPattern?,
    /** Tablaturas asociadas a esta sección (intercaladas). */
    sectionTablatures: List<Tablature> = emptyList(),
    /** Escala del dibujo de las tablaturas (preferencia global). */
    tablatureScale: Float = 1f,
    /** Escala solo de las cifras de traste (preferencia global). */
    tablatureNumberScale: Float = tablatureScale,
    /** Ajustes del pentagrama de tablatura (preferencia global, D-48). */
    tablatureStaffOptions: com.grafitto.laudate.domain.tablature.TabStaffOptions =
        com.grafitto.laudate.domain.tablature.TabStaffOptions()
) {
    val accentColor = SectionColors.forSectionType(section.sectionType.name)
    val sectionLabel = section.headerTitle ?: SectionColors.labelFor(section.sectionType.name)

    Column {
        // Encabezado de sección: barra de acento + etiqueta + micro-badges de ejecución. Ocupa una
        // sola línea de 20 dp y el color identifica la parte (coro, estrofa, puente…) de un vistazo.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Density.Tight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Density.Snug)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Text(
                text = sectionLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                // **Aire entre la barra del *anchor* y su etiqueta** (reporte del usuario: «se tapa
                // un poco del inicio de la letra correspondiente al anchor»). El `Row` separa a sus
                // hijos con `Snug` (4 dp), pensado para los chips, no para la barra: con 3 dp de
                // barra y 4 de hueco, la etiqueta empezaba a 7 dp del borde y la barra parecía
                // solaparse con ella. Aquí se abre un hueco propio de `CardPadding` (8 dp), que es el
                // que usa el resto de la pantalla para separar elementos de distinta naturaleza.
                modifier = Modifier.padding(start = Density.CardPadding)
            )
            if (section.isRepeated) {
                MicroChip(
                    text = section.repeatCount?.let { "×$it" } ?: "×2",
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            section.voice?.takeIf { it.isNotBlank() }?.let { voice ->
                MicroChip(
                    text = voice.replaceFirstChar { it.uppercase() },
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Cambio dinámico de patrón rítmico: la rejilla de rasgueo de esta sección, si la declara
        // con `{x_rhythm: ...}` (o si el canto trae un patrón global). Aparece bajo el encabezado
        // para que el músico vea con qué rasgueo empieza cada parte.
        rhythmPattern?.let { pattern ->
            Column(modifier = Modifier.padding(bottom = Density.Snug)) {
                Text(
                    text = "Rasgueo: ${pattern.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                RhythmPatternGrid(pattern = pattern, rhythmNotationMode = rhythmNotationMode)
            }
        }

        section.lines.forEach { line ->
            // Los comentarios (p. ej. `{comment-todos: todos fuerte}`) se muestran como indicación
            // destacada, con su voz destinataria si la llevan. Las filas solo-acordes y la letra
            // siguen el mismo camino de render.
            if (line.type == LineType.COMMENT || line.type == LineType.HEADER) {
                val prefix = line.voice?.let { v -> "[${v.replaceFirstChar { it.uppercase() }}] " }.orEmpty()
                RichSymbolText(
                    text = "$prefix${line.comment ?: line.lyrics}",
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = (fontSizeSp - 1).sp,
                    modifier = Modifier.padding(vertical = Density.Tight)
                )
                return@forEach
            }

            val cleanLyricsRaw = line.lyrics.replace(Regex("\\[[^\\]]+\\]"), "")
            val plan = SongLineRenderPlan.forLine(
                line = line.copy(lyrics = cleanLyricsRaw),
                displayMode = displayMode
            )
            // Las líneas que no aportan nada (p. ej. una fila de acordes en modo «solo letra»)
            // se omiten por completo en lugar de reservar altura vacía.
            if (plan.isEmpty) return@forEach

            if (plan.showChords) {
                // La anchura del marcado se calcula sin símbolos para anclar los acordes.
                // En una fila solo-acordes la anchura de referencia es 0 (no hay letra que alojar).
                val plainLength = if (plan.showLyrics) {
                    ChordProSymbol.renderInline(cleanLyricsRaw).length
                } else {
                    0
                }
                val chordsText = ChordProSymbol.renderInline(
                    buildChordsLine(line.chords, plainLength, notationSystem)
                )
                // La fila de acordes reduce su altura con la letra (lineHeight proporcional) para
                // que al encoger el tamaño no quede un hueco rígido del estilo base.
                val chordsSize = (fontSizeSp - 2).coerceAtLeast(8f)
                Text(
                    text = chordsText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    fontSize = chordsSize.sp,
                    lineHeight = (chordsSize * 1.1f).sp,
                    // Sin letra debajo, la fila de acordes conserva su propio margen.
                    modifier = if (plan.isChordOnly) {
                        Modifier.padding(bottom = chordsSize.dp)
                    } else {
                        Modifier
                    }
                )
            }

            if (plan.showLyrics) {
                RichSymbolText(
                    text = cleanLyricsRaw,
                    fontSize = fontSizeSp.sp,
                    // El interlineado escala con el tamaño de letra: sin él, al reducir la fuente el
                    // `lineHeight` del estilo base quedaba fijo y las líneas se separaban en exceso.
                    lineHeight = (fontSizeSp * 1.25f).sp,
                    modifier = Modifier.padding(bottom = (fontSizeSp * 0.15f).dp)
                )
            }
        }

        // Tablaturas de esta sección, intercaladas justo después de su parte (intro, estrofa, coro…).
        sectionTablatures.forEach { tab ->
            Spacer(modifier = Modifier.height(Density.Snug))
            TablatureView(
                tablature = tab,
                scale = tablatureScale,
                numberScale = tablatureNumberScale,
                options = tablatureStaffOptions
            )
        }
    }
}

/**
 * Rejilla visual de un patrón rítmico: agrupa los eventos por pulso del compás (los separadores
 * `|` se derivan del compás, nunca se almacenan) y pinta cada figura/golpe según el
 * [RhythmNotationMode] elegido (flechas, figuras musicales o símbolos ChordPro).
 *
 * Se comparte entre la ficha del patrón global del canto y el rasgueo declarado por sección
 * (`{x_rhythm: ...}`), de modo que un cambio de patrón a mitad del canto se ve con el mismo estilo.
 */
@Composable
private fun RhythmPatternGrid(
    pattern: RhythmPattern,
    rhythmNotationMode: RhythmNotationMode
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        pattern.metricGroups().forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(34.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                group.forEach { event ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val showFigures = rhythmNotationMode != RhythmNotationMode.ARROWS &&
                            rhythmNotationMode != RhythmNotationMode.CHORDPRO_SYMBOLS
                        val showArrows = rhythmNotationMode != RhythmNotationMode.MUSICAL_SYMBOLS &&
                            rhythmNotationMode != RhythmNotationMode.CHORDPRO_SYMBOLS
                        val showSymbols = rhythmNotationMode == RhythmNotationMode.CHORDPRO_SYMBOLS
                        if (showFigures) {
                            // (D-91) Bravura: el texto normal no tiene glifos musicales en la web.
                            MusicFigureGlyph(figure = event.figure, size = 18.dp)
                        }
                        if (showSymbols) {
                            StrumSymbol(
                                symbolName = event.symbolName,
                                size = 18.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (showArrows) {
                            StrumSymbol(
                                direction = event.effectiveDirection,
                                articulation = event.effectiveArticulation,
                                size = 13.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
