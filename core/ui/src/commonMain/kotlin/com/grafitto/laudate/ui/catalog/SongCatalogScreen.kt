package com.grafitto.laudate.ui.catalog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LabelOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.music.NotationFormat
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongSortOrder
import com.grafitto.laudate.ui.common.ActionMenuItem
import com.grafitto.laudate.ui.common.ActionTint
import com.grafitto.laudate.ui.common.LaudateIcons
import com.grafitto.laudate.ui.common.PdfExportDialog
import com.grafitto.laudate.ui.common.ShareSongDialog
import com.grafitto.laudate.ui.common.rememberCollapsingHeaderState
import com.grafitto.laudate.ui.platform.PlatformBackHandler
import com.grafitto.laudate.ui.theme.SectionColors
import com.grafitto.laudate.ui.common.AppLogo
import com.grafitto.laudate.ui.common.Density
import com.grafitto.laudate.ui.common.LaudateTopAppBar
import com.grafitto.laudate.ui.common.MicroChip

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SongCatalogScreen(
    uiState: SongCatalogUiState,
    onIntent: (SongCatalogUiIntent) -> Unit,
    onSongClick: (String) -> Unit,
    onCreateSongClick: () -> Unit,
    /** Nomenclatura global de acordes para mostrar los tonos (Do-Re-Mi / C-D-E). */
    notationSystem: NotationSystem = NotationSystem.LATIN,
    modifier: Modifier = Modifier
) {
    var celebrationPickerSong by remember { mutableStateOf<Song?>(null) }
    var showNoCelebrationDialog by remember { mutableStateOf(false) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    var songToPdf by remember { mutableStateOf<Song?>(null) }
    // Canto pendiente de compartir: al pulsarlo se pregunta si deben viajar también los adjuntos.
    var songToShare by remember { mutableStateOf<Song?>(null) }

    // La barra superior se oculta al bajar y reaparece al subir (comportamiento tipo Gmail).
    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val gridState = rememberLazyGridState()
    val headerState = rememberCollapsingHeaderState(gridState)

    Scaffold(
        topBar = {
            LaudateTopAppBar(
                title = {
                    Text("Cancionero")
                },
                scrollBehavior = topBarScrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateSongClick) {
                Icon(Icons.Default.Add, contentDescription = "Nueva Canción")
            }
        },
        modifier = modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        val activeFilterLabels =
            (
                uiState.searchCriteria.momentNames +
                    uiState.searchCriteria.liturgicalTimes +
                    uiState.searchCriteria.additionalCategories
                ).toList()
        val moments = uiState.categories.filter { it.kind == LiturgicalCategoryKind.MASS_MOMENT }
        val times = uiState.categories.filter { it.kind == LiturgicalCategoryKind.LITURGICAL_SEASON }
        val additional = uiState.categories.filter { it.kind == LiturgicalCategoryKind.ADDITIONAL }
        var filtersPanelExpanded by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Lista de resultados
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.songs.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppLogo(modifier = Modifier.size(72.dp).alpha(0.6f))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (uiState.totalSongCount == 0) "No hay canciones guardadas aún.\n¡Crea una nueva con el botón '+'!"
                                   else "No se encontraron cantos con los filtros actuales.",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    // Rejilla adaptativa: en pantalla ancha los cantos se reparten en varias
                    // columnas en vez de dejar una tarjeta larguísima con medio panel vacío.
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(Density.Snug),
                        horizontalArrangement = Arrangement.spacedBy(Density.Snug),
                        contentPadding = PaddingValues(
                            start = Density.CardPadding,
                            end = Density.CardPadding,
                            top = headerState.contentTopPadding() + Density.Snug,
                            bottom = 96.dp
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Cabecera de resultados: total de coincidencias y aviso de muestra recortada.
                        item(key = "results-header", span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Text(
                                    text = if (uiState.isShowingSample) {
                                        "Mostrando ${uiState.songs.size} de ${uiState.resultCount} cantos"
                                    } else {
                                        "${uiState.resultCount} ${if (uiState.resultCount == 1) "canto" else "cantos"}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (uiState.isShowingSample) {
                                    Text(
                                        text = "Escribe al menos 3 letras o elige un filtro para ver todos los resultados.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        items(uiState.songs, key = { it.id }) { song ->
                            SongCardItem(
                                song = song,
                                notationSystem = notationSystem,
                                hasAttachments = song.id in uiState.songIdsWithAttachments,
                                hasTablatures = song.id in uiState.songIdsWithTablatures,
                                onSongClick = { onSongClick(song.id) },
                                onToggleFavorite = {
                                    onIntent(SongCatalogUiIntent.ToggleSongFavorite(song.id, song.isFavorite))
                                },
                                onShare = { songToShare = song },
                                onExportPdf = { songToPdf = song },
                                onDelete = { songToDelete = song },
                                onAddToCelebration = {
                                    when {
                                        uiState.celebrations.isEmpty() -> showNoCelebrationDialog = true
                                        uiState.celebrations.size == 1 -> onIntent(
                                            SongCatalogUiIntent.AddSongToCelebration(
                                                uiState.celebrations.first().id,
                                                song.id
                                            )
                                        )
                                        else -> celebrationPickerSong = song
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Cabecera colapsable: buscador + filtros. Flota sobre la lista al reaparecer con
            // scroll hacia arriba. Su fondo (surface) tapa los cantos que pasan por debajo.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, headerState.offsetY.toInt()) }
                    .onSizeChanged { headerState.onHeightChanged(it.height) }
                    // Flotando sobre la lista el fondo se aclara un punto: pista de que aún se
                    // puede seguir subiendo (como en Gmail).
                    .background(
                        if (headerState.isFloating) MaterialTheme.colorScheme.surfaceContainerLow
                        else MaterialTheme.colorScheme.surface
                    )
                    .padding(horizontal = Density.CardPadding, vertical = Density.Snug)
            ) {
                CatalogHeader(
                    uiState = uiState,
                    onIntent = onIntent,
                    activeFilterLabels = activeFilterLabels,
                    moments = moments,
                    times = times,
                    additional = additional,
                    filtersPanelExpanded = filtersPanelExpanded,
                    onToggleFiltersPanel = { filtersPanelExpanded = !filtersPanelExpanded }
                )
            }
        }
    }

    // Si hay varias celebraciones, se pregunta a cuál añadir el canto.
    celebrationPickerSong?.let { song ->
        CelebrationPickerDialog(
            song = song,
            celebrations = uiState.celebrations,
            onDismissRequest = { celebrationPickerSong = null },
            onCelebrationSelected = { celebrationId ->
                onIntent(SongCatalogUiIntent.AddSongToCelebration(celebrationId, song.id))
                celebrationPickerSong = null
            }
        )
    }

    if (showNoCelebrationDialog) {
        AlertDialog(
            onDismissRequest = { showNoCelebrationDialog = false },
            title = { Text("Añadir a misa/evento") },
            text = {
                Text("Todavía no has creado ninguna misa o evento. Créalo desde «Misas y Eventos» y vuelve a intentarlo.")
            },
            confirmButton = {
                TextButton(onClick = { showNoCelebrationDialog = false }) { Text("Entendido") }
            }
        )
    }

    // Antes de compartir se pregunta si el canto debe viajar con sus adjuntos.
    songToShare?.let { song ->
        ShareSongDialog(
            subject = song.title,
            attachmentCount = null,
            onDismiss = { songToShare = null },
            onConfirm = { withAttachments ->
                if (withAttachments) {
                    onIntent(SongCatalogUiIntent.ShareSongWithAttachments(song.id))
                } else {
                    onIntent(SongCatalogUiIntent.ShareSong(song.id))
                }
                songToShare = null
            }
        )
    }

    // Antes de generar el PDF se pregunta el modo; la elección se recuerda.
    songToPdf?.let { song ->
        PdfExportDialog(
            subject = song.title,
            initialMode = uiState.pdfDisplayMode,
            hasTablature = song.id in uiState.songIdsWithTablatures,
            onDismiss = { songToPdf = null },
            onConfirm = { mode ->
                onIntent(SongCatalogUiIntent.ExportSongPdf(song.id, mode))
                songToPdf = null
            }
        )
    }

    songToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { songToDelete = null },
            title = { Text("Eliminar Canción") },
            text = { Text("¿Deseas eliminar «${song.title}»? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIntent(SongCatalogUiIntent.DeleteSong(song.id))
                        songToDelete = null
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { songToDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

/**
 * Contenido de la cabecera pegajosa del catálogo: el buscador (colapsado por defecto, solo
 * «Buscar») y, tras pulsarlo, la fila de filtros. Los filtros quedan ocultos hasta que el usuario
 * pulsa el campo de búsqueda, como en la app de Gmail.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogHeader(
    uiState: SongCatalogUiState,
    onIntent: (SongCatalogUiIntent) -> Unit,
    activeFilterLabels: List<String>,
    moments: List<LiturgicalCategory>,
    times: List<LiturgicalCategory>,
    additional: List<LiturgicalCategory>,
    filtersPanelExpanded: Boolean,
    onToggleFiltersPanel: () -> Unit,
) {
    var searchActive by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // La fila de filtros se despliega al activar la búsqueda o al abrir el panel de filtros.
    val showFiltersRow = searchActive || filtersPanelExpanded

    // Al desplegar la búsqueda, el campo recibe el foco automáticamente (como en Gmail).
    LaunchedEffect(searchActive) {
        if (searchActive) runCatching { focusRequester.requestFocus() }
    }
    // El gesto/botón «atrás» pliega la búsqueda antes de salir de la pantalla.
    PlatformBackHandler(enabled = searchActive) {
        searchActive = false
        focusManager.clearFocus()
    }

    // Gmail: una píldora «Buscar» colapsada que, al pulsarla, se convierte con una animación
    // cruzada en el campo de búsqueda; el icono de la flecha (y el botón atrás del sistema)
    // vuelven a plegarla.
    AnimatedContent(
        targetState = searchActive,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "catalogSearchToggle"
    ) { active ->
        if (!active) {
            Surface(
                onClick = { searchActive = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Buscar",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            OutlinedTextField(
                value = uiState.searchCriteria.query,
                onValueChange = {
                    onIntent(SongCatalogUiIntent.UpdateSearchCriteria(uiState.searchCriteria.copy(query = it)))
                },
                placeholder = { Text("Buscar") },
                leadingIcon = {
                    IconButton(onClick = {
                        searchActive = false
                        focusManager.clearFocus()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar búsqueda")
                    }
                },
                trailingIcon = {
                    if (uiState.searchCriteria.query.isNotEmpty()) {
                        IconButton(onClick = {
                            onIntent(SongCatalogUiIntent.UpdateSearchCriteria(uiState.searchCriteria.copy(query = "")))
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true
            )
        }
    }

    AnimatedVisibility(visible = showFiltersRow) {
        Column {
            Spacer(modifier = Modifier.height(Density.Snug))
            // Chips en una sola fila con scroll horizontal, como los filtros de Gmail: nunca
            // saltan a una segunda línea (en móvil el ancho es escaso).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Density.Snug),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = filtersPanelExpanded,
                    onClick = onToggleFiltersPanel,
                    label = {
                        Text(
                            if (activeFilterLabels.isNotEmpty()) "Filtros (${activeFilterLabels.size})" else "Filtros"
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.Tune, contentDescription = "Filtros") }
                )

                FilterChip(
                    selected = uiState.searchCriteria.onlyFavorites,
                    onClick = {
                        onIntent(
                            SongCatalogUiIntent.UpdateSearchCriteria(
                                uiState.searchCriteria.copy(
                                    onlyFavorites = !uiState.searchCriteria.onlyFavorites
                                )
                            )
                        )
                    },
                    label = { Text("Favoritos") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (uiState.searchCriteria.onlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favoritos"
                        )
                    }
                )

                FilterChip(
                    selected = uiState.searchCriteria.onlyUncategorized,
                    onClick = {
                        onIntent(
                            SongCatalogUiIntent.UpdateSearchCriteria(
                                uiState.searchCriteria.copy(
                                    onlyUncategorized = !uiState.searchCriteria.onlyUncategorized
                                )
                            )
                        )
                    },
                    label = { Text("Sin categoría") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.LabelOff,
                            contentDescription = "Sin categoría"
                        )
                    }
                )

                // Chips de filtros activos, quitables uno a uno.
                activeFilterLabels.take(2).forEach { label ->
                    FilterChip(
                        selected = true,
                        onClick = {
                            val criteria = uiState.searchCriteria
                            onIntent(
                                SongCatalogUiIntent.UpdateSearchCriteria(
                                    criteria.copy(
                                        momentNames = criteria.momentNames - label,
                                        liturgicalTimes = criteria.liturgicalTimes - label,
                                        additionalCategories = criteria.additionalCategories - label
                                    )
                                )
                            )
                        },
                        label = { Text(label) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Quitar filtro") }
                    )
                }

                if (activeFilterLabels.size > 2) {
                    Text(
                        text = "+${activeFilterLabels.size - 2}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Ordenación del resultado: alfabética o por número de cancionero.
                Box {
                    FilterChip(
                        selected = false,
                        onClick = { sortMenuExpanded = true },
                        label = { Text(uiState.searchCriteria.sort.shortName, maxLines = 1) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar") }
                    )
                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SongSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.displayName) },
                                onClick = {
                                    onIntent(
                                        SongCatalogUiIntent.UpdateSearchCriteria(
                                            uiState.searchCriteria.copy(sort = order)
                                        )
                                    )
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = filtersPanelExpanded) {
                // Se reserva SIEMPRE un alto concreto y el scroll vive DENTRO. Así el panel
                // desplaza de forma fiable aunque haya decenas de categorías.
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(end = 4.dp)
                        ) {
                            if (moments.isNotEmpty()) {
                                CatalogFilterLabel("Momento litúrgico")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    moments.forEach { category ->
                                        FilterChip(
                                            selected = category.name in uiState.searchCriteria.momentNames,
                                            onClick = {
                                                val current = uiState.searchCriteria.momentNames
                                                val updated = if (category.name in current) current - category.name else current + category.name
                                                onIntent(
                                                    SongCatalogUiIntent.UpdateSearchCriteria(
                                                        uiState.searchCriteria.copy(momentNames = updated)
                                                    )
                                                )
                                            },
                                            label = { Text(category.name) }
                                        )
                                    }
                                }
                            }

                            if (times.isNotEmpty()) {
                                CatalogFilterLabel("Tiempo litúrgico")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    times.forEach { category ->
                                        FilterChip(
                                            selected = category.name in uiState.searchCriteria.liturgicalTimes,
                                            onClick = {
                                                val current = uiState.searchCriteria.liturgicalTimes
                                                val updated = if (category.name in current) current - category.name else current + category.name
                                                onIntent(
                                                    SongCatalogUiIntent.UpdateSearchCriteria(
                                                        uiState.searchCriteria.copy(liturgicalTimes = updated)
                                                    )
                                                )
                                            },
                                            label = { Text(category.name) }
                                        )
                                    }
                                }
                            }
                            if (additional.isNotEmpty()) {
                                CatalogFilterLabel("Categorías adicionales")
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    additional.forEach { category ->
                                        FilterChip(
                                            selected = category.name in uiState.searchCriteria.additionalCategories,
                                            onClick = {
                                                val current = uiState.searchCriteria.additionalCategories
                                                val updated = if (category.name in current) current - category.name else current + category.name
                                                onIntent(
                                                    SongCatalogUiIntent.UpdateSearchCriteria(
                                                        uiState.searchCriteria.copy(additionalCategories = updated)
                                                    )
                                                )
                                            },
                                            label = { Text(category.name) }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Degradado inferior: pista visual de que hay más filtros debajo.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(12.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                onIntent(
                                    SongCatalogUiIntent.UpdateSearchCriteria(
                                        uiState.searchCriteria.copy(
                                            momentNames = emptySet(),
                                            liturgicalTimes = emptySet(),
                                            additionalCategories = emptySet(),
                                            onlyUncategorized = false
                                        )
                                    )
                                )
                            },
                            enabled = activeFilterLabels.isNotEmpty() || uiState.searchCriteria.onlyUncategorized
                        ) {
                            Text("Limpiar filtros")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongCardItem(
    song: Song,
    onSongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToCelebration: () -> Unit,
    onShare: () -> Unit = {},
    onExportPdf: () -> Unit = {},
    onDelete: () -> Unit = {},
    /** Nomenclatura global de acordes para mostrar el tono. */
    notationSystem: NotationSystem = NotationSystem.LATIN,
    /** Indica si el canto tiene adjuntos (icono en la tarjeta). */
    hasAttachments: Boolean = false,
    /** Indica si el canto tiene tablaturas (icono en la tarjeta). */
    hasTablatures: Boolean = false,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    // Color de acento según el primer momento/etiqueta del canto
    val accentColor = song.defaultLiturgicalMoments.firstOrNull()?.let { moment ->
        SectionColors.forLabel(moment)
    } ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSongClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Barra de acento de color a la izquierda de la tarjeta, a toda la altura de la fila.
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Density.CardPadding, vertical = Density.Compact),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = song.number?.takeIf { it.isNotBlank() }
                                ?.let { "$it · ${song.title}" }
                                ?: song.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        // Iconos de contenido: el canto tiene tablatura y/o adjuntos.
                        if (hasTablatures) {
                            Icon(
                                imageVector = com.grafitto.laudate.ui.common.LaudateIcons.Guitar,
                                contentDescription = "Tiene tablatura",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = Density.Snug).size(14.dp)
                            )
                        }
                        if (hasAttachments) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Tiene adjuntos",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(start = Density.Snug).size(14.dp)
                            )
                        }
                    }

                    song.author?.takeIf { it.isNotBlank() }?.let { author ->
                        Text(
                            text = author,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Directivas del canto en micro-badges: tono, capo y primer momento litúrgico.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Density.Tight),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MicroChip(
                            text = "Tono: ${NotationFormat.key(song.originalKey, notationSystem)}",
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (song.capo > 0) {
                            MicroChip(
                                text = "Capo ${song.capo}",
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        song.defaultLiturgicalMoments.firstOrNull()?.let { moment ->
                            MicroChip(text = moment, color = accentColor)
                        }
                    }
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorito",
                        tint = if (song.isFavorite) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        ActionMenuItem(
                            text = "Abrir",
                            icon = Icons.Default.Edit,
                            tint = ActionTint.PRIMARY,
                            onClick = {
                                menuExpanded = false
                                onSongClick()
                            }
                        )
                        ActionMenuItem(
                            text = "Añadir a misa/evento",
                            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                            onClick = {
                                menuExpanded = false
                                onAddToCelebration()
                            }
                        )
                        ActionMenuItem(
                            text = "Compartir",
                            icon = Icons.Default.Share,
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        ActionMenuItem(
                            text = "Exportar a PDF",
                            icon = Icons.Default.PictureAsPdf,
                            onClick = {
                                menuExpanded = false
                                onExportPdf()
                            }
                        )
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
        }
    }
}

@Composable
private fun CatalogFilterLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun CelebrationPickerDialog(
    song: Song,
    celebrations: List<Celebration>,
    onDismissRequest: () -> Unit,
    onCelebrationSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Añadir a misa/evento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "¿A qué celebración quieres añadir «${song.title}»?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                celebrations.forEach { celebration ->
                    Text(
                        text = celebration.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCelebrationSelected(celebration.id) }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) { Text("Cancelar") }
        }
    )
}
