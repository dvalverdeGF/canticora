package com.grafitto.laudate.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.music.NotationFormat
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongSortOrder

/**
 * Buscador de cantos reutilizable (RF-107), presentado como *bottom sheet*.
 *
 * Filtra **acumulativamente** por texto libre (título, autor, número y letra), momento
 * litúrgico, tiempo litúrgico, tonalidad y favoritos, con orden configurable. Se usa tanto
 * desde el catálogo (añadir a un evento) como desde el editor de misas.
 *
 * @param onSongClick Acción al pulsar un resultado. El icono de acción ([trailingIcon]) permite
 *   añadirlo a la lista destino sin cerrar el buscador.
 * @param trailingIcon Icono de la acción principal de cada fila (p. ej. añadir). Si es `null`,
 *   la fila solo es pulsable.
 * @param onTrailingClick Acción del icono de [trailingIcon].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SongSearchSheet(
    state: SongSearchUiState,
    onIntent: (SongSearchUiIntent) -> Unit,
    onDismissRequest: () -> Unit,
    onSongClick: (Song) -> Unit,
    title: String = "Buscar canto",
    trailingIcon: ((Song) -> androidx.compose.ui.graphics.vector.ImageVector)? = null,
    trailingIconDescription: String = "Acción",
    onTrailingClick: ((Song) -> Unit)? = null,
    /** Nomenclatura global de acordes para mostrar los tonos (Do-Re-Mi / C-D-E). */
    notationSystem: NotationSystem = NotationSystem.LATIN,
    modifier: Modifier = Modifier
) {
    var filtersExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (state.hasActiveFilters) {
                    TextButton(onClick = { onIntent(SongSearchUiIntent.ClearFilters) }) {
                        Text("Limpiar")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = state.query,
                onValueChange = { onIntent(SongSearchUiIntent.UpdateQuery(it)) },
                placeholder = { Text("Título, autor, número o letra…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onIntent(SongSearchUiIntent.UpdateQuery("")) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar texto")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Barra de filtros y ordenación
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = { filtersExpanded = !filtersExpanded }) {
                    Icon(Icons.Default.Tune, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (state.activeFilterCount > 0) "Filtros (${state.activeFilterCount})" else "Filtros")
                }

                Box {
                    TextButton(onClick = { sortExpanded = true }) {
                        Text(state.sort.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Ordenar")
                    }
                    DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        SongSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.displayName) },
                                onClick = {
                                    onIntent(SongSearchUiIntent.UpdateSort(order))
                                    sortExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = filtersExpanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (state.filterOptions.moments.isNotEmpty()) {
                        FilterSectionLabel("Momento litúrgico")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.filterOptions.moments.forEach { moment ->
                                FilterChip(
                                    selected = moment in state.momentNames,
                                    onClick = { onIntent(SongSearchUiIntent.ToggleMoment(moment)) },
                                    label = { Text(moment) }
                                )
                            }
                        }
                    }

                    if (state.filterOptions.liturgicalTimes.isNotEmpty()) {
                        FilterSectionLabel("Tiempo litúrgico")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.filterOptions.liturgicalTimes.forEach { time ->
                                FilterChip(
                                    selected = time in state.liturgicalTimes,
                                    onClick = { onIntent(SongSearchUiIntent.ToggleLiturgicalTime(time)) },
                                    label = { Text(time) }
                                )
                            }
                        }
                    }

                    if (state.filterOptions.additionalCategories.isNotEmpty()) {
                        FilterSectionLabel("Categorías adicionales")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.filterOptions.additionalCategories.forEach { category ->
                                FilterChip(
                                    selected = category in state.additionalCategories,
                                    onClick = { onIntent(SongSearchUiIntent.ToggleAdditionalCategory(category)) },
                                    label = { Text(category) }
                                )
                            }
                        }
                    }

                    FilterSectionLabel("Sin categoría")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.onlyUncategorized,
                            onClick = { onIntent(SongSearchUiIntent.ToggleUncategorized(!state.onlyUncategorized)) },
                            label = { Text("Solo cantos sin categoría") }
                        )
                    }

                    if (state.filterOptions.keys.isNotEmpty()) {
                        FilterSectionLabel("Tonalidad original")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.filterOptions.keys.forEach { key ->
                                FilterChip(
                                    selected = key in state.keys,
                                    onClick = { onIntent(SongSearchUiIntent.ToggleKey(key)) },
                                    label = { Text(NotationFormat.key(key, notationSystem)) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    FilterChip(
                        selected = state.onlyFavorites,
                        onClick = { onIntent(SongSearchUiIntent.ToggleFavorites(!state.onlyFavorites)) },
                        label = { Text("Solo favoritos") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (state.onlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            HorizontalDivider()

            Text(
                text = "${state.results.size} ${if (state.results.size == 1) "canto" else "cantos"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Cargando catálogo…", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (state.results.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No se encontraron cantos con los filtros actuales.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.results, key = { it.id }) { song ->
                        SongPickerRow(
                            song = song,
                            notationSystem = notationSystem,
                            onClick = { onSongClick(song) },
                            trailingIcon = trailingIcon?.invoke(song),
                            trailingIconDescription = trailingIconDescription,
                            onTrailingClick = onTrailingClick?.let { { it(song) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun SongPickerRow(
    song: Song,
    onClick: () -> Unit,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    trailingIconDescription: String,
    onTrailingClick: (() -> Unit)?,
    notationSystem: NotationSystem = NotationSystem.LATIN
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.number?.takeIf { it.isNotBlank() }
                    ?.let { "$it · ${song.title}" }
                    ?: song.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            song.author?.takeIf { it.isNotBlank() }?.let { author ->
                Text(
                    text = author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Text(
                        text = NotationFormat.key(song.originalKey, notationSystem),
                        modifier = Modifier.padding(2.dp)
                    )
                }
                if (song.capo > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Text(text = "Capo ${song.capo}", modifier = Modifier.padding(2.dp))
                    }
                }
                song.defaultLiturgicalMoments.firstOrNull()?.let { moment ->
                    Text(
                        text = moment,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (trailingIcon != null && onTrailingClick != null) {
            IconButton(onClick = onTrailingClick) {
                Icon(trailingIcon, contentDescription = trailingIconDescription)
            }
        }
    }
}
