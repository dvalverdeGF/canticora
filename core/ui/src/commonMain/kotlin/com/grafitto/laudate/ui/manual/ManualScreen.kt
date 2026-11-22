package com.grafitto.laudate.ui.manual

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.ui.common.LaudateTopAppBar
import com.grafitto.laudate.domain.manual.ManualSearchHit
import com.grafitto.laudate.domain.model.LaudateBrand
import com.grafitto.laudate.domain.model.manual.ManualBlock
import com.grafitto.laudate.domain.model.manual.ManualChapter
import com.grafitto.laudate.domain.model.manual.ManualSection
import com.grafitto.laudate.domain.model.manual.UserManualContent
import kotlinx.coroutines.flow.Flow

/** Número máximo de fotogramas que se espera a que la sección destino aparezca en el índice. */
private const val MAX_SCROLL_ATTEMPTS = 10

/**
 * Pantalla del **Manual de uso** (accesible desde el menú lateral).
 *
 * Ofrece tres capacidades: **índice navegable** por capítulos y secciones, **buscador interno**
 * (sin acentos ni mayúsculas) y **secciones plegables** para consultar rápido en el ensayo.
 * Todo el contenido es estático y offline ([UserManualContent]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScreen(
    uiState: ManualUiState,
    sideEffects: Flow<ManualSideEffect>,
    onIntent: (ManualUiIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val items = buildManualItems(uiState)
    val currentItems by rememberUpdatedState(items)
    // La barra superior se oculta al bajar y reaparece al subir (comportamiento tipo Gmail).
    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Al abrir una coincidencia, se desplaza hasta su sección (ya desplegada por el ViewModel).
    // Se reintenta unos fotogramas porque el efecto puede llegar antes de que el índice incluya
    // la sección recién desplegada.
    LaunchedEffect(Unit) {
        sideEffects.collect { effect ->
            when (effect) {
                is ManualSideEffect.ScrollToSection -> {
                    val targetKey = "section-${effect.sectionId}"
                    repeat(MAX_SCROLL_ATTEMPTS) {
                        val index = currentItems.indexOfFirst { it.key == targetKey }
                        if (index >= 0) {
                            listState.animateScrollToItem(index)
                            return@collect
                        }
                        withFrameNanos { }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            LaudateTopAppBar(
                title = { Text("Manual de uso") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                scrollBehavior = topBarScrollBehavior
            )
        },
        modifier = modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { onIntent(ManualUiIntent.UpdateQuery(it)) },
                placeholder = { Text("Buscar en el manual…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                trailingIcon = {
                    if (uiState.query.isNotEmpty()) {
                        IconButton(onClick = { onIntent(ManualUiIntent.UpdateQuery("")) }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = true
            )

            if (uiState.isSearching) {
                SearchResults(
                    results = uiState.results,
                    hasNoResults = uiState.hasNoResults,
                    onResultClick = { onIntent(ManualUiIntent.OpenSearchResult(it)) },
                    modifier = Modifier.weight(1f)
                )
            } else if (uiState.chapters.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("El manual no está disponible.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is ManualItem.ChapterHeader -> ChapterHeaderCard(
                                chapter = item.chapter,
                                expanded = item.chapter.id in uiState.expandedChapterIds,
                                onToggle = { onIntent(ManualUiIntent.ToggleChapter(item.chapter.id)) }
                            )

                            is ManualItem.SectionItem -> SectionCard(
                                section = item.section,
                                expanded = item.section.id in uiState.expandedSectionIds,
                                onToggle = { onIntent(ManualUiIntent.ToggleSection(item.section.id)) }
                            )
                        }
                    }

                    item(key = "manual-footer") {
                        ManualFooter()
                    }
                }
            }
        }
    }
}

/** Pie con la versión y fecha del manual (trazabilidad del contenido). */
@Composable
private fun ManualFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
        Text(
            text = "Manual v${UserManualContent.VERSION} · Actualizado el ${UserManualContent.LAST_UPDATED}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = LaudateBrand.FULL_NAME,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Cabecera de capítulo, plegable, con su icono temático. */
@Composable
private fun ChapterHeaderCard(
    chapter: ManualChapter,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        onClick = onToggle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = chapter.icon.vector(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Contraer capítulo" else "Expandir capítulo",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** Sección plegable: su cabecera es un botón y su contenido aparece al desplegar. */
@Composable
private fun SectionCard(
    section: ManualSection,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Contraer sección" else "Expandir sección",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    section.blocks.forEach { block -> ManualBlockView(block) }
                }
            }
        }
    }
}

/** Renderiza un bloque de contenido del manual. */
@Composable
private fun ManualBlockView(block: ManualBlock) {
    when (block) {
        is ManualBlock.Paragraph -> Text(
            text = manualRichText(block.text),
            style = MaterialTheme.typography.bodyMedium
        )

        is ManualBlock.Steps -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            block.items.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = manualRichText(step),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        is ManualBlock.Note -> Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(12.dp)) {
                Text(text = "\uD83D\uDCA1")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = manualRichText(block.text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        is ManualBlock.Reference -> ReferenceTable(block)
    }
}

/** Tabla de referencia simple (cabecera opcional + filas de dos o más columnas). */
@Composable
private fun ReferenceTable(block: ManualBlock.Reference) {
    Column(modifier = Modifier.fillMaxWidth()) {
        block.caption?.let { caption ->
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (block.headers.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                block.headers.forEach { header ->
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        block.rows.forEachIndexed { rowIndex, row ->
            if (rowIndex % 2 == 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                row.forEach { cell ->
                    Text(
                        text = manualRichText(cell),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Lista de coincidencias del buscador interno. */
@Composable
private fun SearchResults(
    results: List<ManualSearchHit>,
    hasNoResults: Boolean,
    onResultClick: (ManualSearchHit) -> Unit,
    modifier: Modifier = Modifier
) {
    if (hasNoResults) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No se encontró nada en el manual.\nPrueba con otra palabra.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "results-count") {
            Text(
                text = "${results.size} ${if (results.size == 1) "sección" else "secciones"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        items(results, key = { "${it.chapterId}/${it.sectionId}" }) { hit ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                onClick = { onResultClick(hit) }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = hit.chapterIcon.vector(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = hit.chapterTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = hit.sectionTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = hit.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Índice plano (para LazyColumn y el salto a sección)
// ────────────────────────────────────────────────────────────────────────────

private sealed interface ManualItem {
    val key: String

    data class ChapterHeader(val chapter: ManualChapter) : ManualItem {
        override val key: String get() = "chapter-${chapter.id}"
    }

    data class SectionItem(val section: ManualSection) : ManualItem {
        override val key: String get() = "section-${section.id}"
    }
}

/** Aplana capítulos y secciones según lo que esté desplegado en el índice. */
private fun buildManualItems(uiState: ManualUiState): List<ManualItem> = buildList {
    uiState.chapters.forEach { chapter ->
        add(ManualItem.ChapterHeader(chapter))
        if (chapter.id in uiState.expandedChapterIds) {
            chapter.sections.forEach { section -> add(ManualItem.SectionItem(section)) }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Formato enriquecido del texto (**negrita** y `código`)
// ────────────────────────────────────────────────────────────────────────────

private val INLINE_MARKUP = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`)")

/**
 * Convierte el marcado ligero del manual en texto enriquecido:
 * `**texto**` → negrita y `` `texto` `` → monoespaciado (para directivas y acordes).
 */
private fun manualRichText(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    INLINE_MARKUP.findAll(text).forEach { match ->
        if (match.range.first > last) append(text.substring(last, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.removeSurrounding("**"))
            }

            token.startsWith("`") -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                append(token.removeSurrounding("`"))
            }

            else -> append(token)
        }
        last = match.range.last + 1
    }
    if (last < text.length) append(text.substring(last))
}
