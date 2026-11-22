package com.grafitto.laudate.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.model.music.NotationFormat
import com.grafitto.laudate.domain.model.music.TempoMarking
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.ui.attachment.SongAttachmentsSection
import com.grafitto.laudate.ui.common.ChordPaletteGrid
import com.grafitto.laudate.ui.common.Density
import com.grafitto.laudate.ui.common.HairlineDivider
import com.grafitto.laudate.ui.common.MicroChip
import com.grafitto.laudate.ui.common.PillButton
import com.grafitto.laudate.ui.navigation.CanticoraIcons
import com.grafitto.laudate.ui.navigation.SettingsButtonGroup
import com.grafitto.laudate.ui.navigation.SettingsDropdownRow
import com.grafitto.laudate.ui.navigation.SettingsIcon
import com.grafitto.laudate.ui.navigation.SettingsNumberRow
import com.grafitto.laudate.ui.navigation.SettingsMultiSelectRow
import com.grafitto.laudate.ui.navigation.SettingsSliderRow
import com.grafitto.laudate.domain.tablature.TabNotationScale
import com.grafitto.laudate.ui.navigation.SettingsOption
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Herramientas del **editor de canto**. Cada una es un «pincel» del panel derecho: al elegirla, el
 * panel cambia por completo y muestra **solo** las opciones avanzadas de esa herramienta.
 *
 * Es el modelo de los editores profesionales (Photoshop, Illustrator, Guitar Pro): en lugar de
 * apilar cajas plegables —«Directivas avanzadas», «Símbolos musicales», «Adjuntos»…— que alargan el
 * formulario y obligan a bajar y subir, hay una **tira de herramientas** arriba y un panel que
 * cambia de contenido. El formulario se queda con lo esencial (título, autor y el texto ChordPro) y
 * todo lo demás vive aquí, a un clic.
 */
enum class SongEditorTool(
    val label: String,
    val icon: DrawableResource,
    /**
     * `true` si la herramienta **escribe texto** en el área ChordPro (acordes, secciones,
     * directivas, símbolos). Estas van juntas en la tira y solo se ofrecen en la pestaña
     * «Editor»: en la vista previa o en las tablaturas no hay cursor donde insertar nada.
     */
    val insertsText: Boolean
) {
    SONG("Canción", CanticoraIcons.ToolSong, insertsText = false),
    CHORDS("Acordes", CanticoraIcons.ToolChords, insertsText = true),
    SECTIONS("Secciones", CanticoraIcons.ToolSections, insertsText = true),
    DIRECTIVES("Directivas", CanticoraIcons.ToolDirectives, insertsText = true),
    SYMBOLS("Símbolos", CanticoraIcons.ToolSymbols, insertsText = true),
    RHYTHM("Ritmo", CanticoraIcons.ToolRhythm, insertsText = false),
    ATTACHMENTS("Adjuntos", CanticoraIcons.ToolAttachments, insertsText = false);

    companion object {
        /** Herramientas que se ofrecen en una pestaña concreta del editor. */
        fun forTab(tab: SongEditorTab): List<SongEditorTool> =
            if (tab == SongEditorTab.EDITOR) entries else entries.filterNot { it.insertsText }
    }
}

/**
 * Estado compartido entre el editor y su panel de herramientas.
 *
 * Guarda **qué herramienta está activa** y el **insertador de texto** del área ChordPro. El
 * insertador se registra desde el editor (que es quien tiene el cursor) y lo usan los paneles para
 * meter acordes, directivas y símbolos en la posición exacta del cursor, sin que el panel tenga que
 * conocer el `TextFieldValue`.
 */
@Stable
class SongEditorToolHost {
    /** Herramienta activa del panel derecho. */
    var tool by mutableStateOf(SongEditorTool.SONG)

    private var inserter: ((String) -> Unit)? = null

    /** El editor registra aquí cómo insertar texto en el cursor. */
    fun registerInserter(block: (String) -> Unit) {
        inserter = block
    }

    /** El editor se da de baja al salir para no dejar una lambda colgando. */
    fun unregisterInserter() {
        inserter = null
    }

    /** Inserta [text] en la posición del cursor del área ChordPro. */
    fun insert(text: String) {
        inserter?.invoke(text)
    }
}

/**
 * **Tira de herramientas** del panel derecho: una fila de botones de icono, uno por herramienta, con
 * la activa resaltada. Es la pieza que sustituye a las cajas plegables del formulario.
 */
@Composable
fun SongEditorToolStrip(
    host: SongEditorToolHost,
    tools: List<SongEditorTool> = SongEditorTool.entries,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Density.Snug, vertical = Density.Tight),
        horizontalArrangement = Arrangement.spacedBy(Density.Tight)
    ) {
        tools.forEach { tool ->
            val selected = host.tool == tool
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(Density.CornerSmall))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                    .clickable { host.tool = tool },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(tool.icon),
                    contentDescription = tool.label,
                    modifier = Modifier.size(18.dp),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * **Panel de la herramienta activa**: la cabecera plegable, la tira de herramientas y las opciones
 * avanzadas de la que está en uso. Se coloca en el panel derecho de la aplicación, así que no dibuja
 * fondo ni bordes propios: es una zona plana más del panel.
 *
 * La **tira de herramientas va primero** (es el selector: se elige herramienta y, debajo, se ven sus
 * opciones). La cabecera de la herramienta en uso (icono + nombre + ayuda) hace de **tirador** para
 * plegar el bloque de opciones, de modo que el músico pueda quedarse solo con la tira y recuperar esa
 * franja de alto.
 */
@Composable
fun SongEditorToolPanel(
    host: SongEditorToolHost,
    uiState: SongEditorUiState,
    onIntent: (SongEditorUiIntent) -> Unit,
    onAddAttachmentFileClick: (() -> Unit)? = null,
    onOpenAttachmentClick: ((SongAttachment) -> Unit)? = null,
    onExportAttachmentClick: ((SongAttachment) -> Unit)? = null,
    /**
     * **Tamaño de la tablatura** en la vista previa (preferencia global, compartida con el visor y
     * el modo canto). Solo se ofrece cuando el canto tiene tablaturas y se está en la vista previa:
     * en el editor de ChordPro no hay tablatura a la vista que ajustar.
     */
    hasTablature: Boolean = false,
    tablatureScale: Float = 1f,
    onTablatureScaleChange: ((Float) -> Unit)? = null
) {
    val tools = SongEditorTool.forTab(uiState.editorTab)
    // Si la herramienta activa deja de ofrecerse (p. ej. se pasa de «Editor» a «Vista Previa»
    // con «Acordes» seleccionado), se vuelve a la primera disponible en vez de dejar el panel
    // mostrando algo que ya no está en la tira.
    LaunchedEffect(tools) {
        if (host.tool !in tools) host.tool = tools.first()
    }
    var helpTool by remember { mutableStateOf<SongEditorTool?>(null) }
    var bodyExpanded by remember { mutableStateOf(true) }
    // Al cambiar de herramienta se despliegan sus opciones: si el usuario plegó las de la
    // anterior, no tiene sentido que la nueva aparezca vacía.
    LaunchedEffect(host.tool) { bodyExpanded = true }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 1. Selector de herramienta: siempre arriba y siempre visible.
        SongEditorToolStrip(host, tools)

        // 2. Cabecera de la herramienta activa: tirador del bloque de opciones.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { bodyExpanded = !bodyExpanded }
                .padding(
                    start = Density.CardPadding,
                    end = Density.Tight,
                    top = Density.Tight,
                    bottom = Density.Tight
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Density.Compact)
        ) {
            SettingsIcon(host.tool.icon, tint = MaterialTheme.colorScheme.primary, size = 18.dp)
            Text(
                text = host.tool.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            // El icono (i) vive **en la misma fila** que el título: no consume ni una línea de
            // altura propia. Solo aparece en las herramientas que tienen algo que explicar.
            if (host.tool.hasHelp) {
                IconButton(
                    onClick = { helpTool = host.tool },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Ayuda sobre ${host.tool.label}",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = if (bodyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (bodyExpanded) {
                    "Ocultar las opciones de ${host.tool.label}"
                } else {
                    "Mostrar las opciones de ${host.tool.label}"
                },
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = bodyExpanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                HairlineDivider()

                // (D-82) **Tamaño de la tablatura en la vista previa.** Se ofrece arriba del todo de
                // la herramienta «Canción» —la que queda activa al entrar en la pestaña Vista
                // previa— porque es lo que se viene a ajustar: agrandar la rejilla para verla desde
                // lejos, o las cifras sin tocar la rejilla. Comparte la preferencia con el visor y el
                // modo canto, así que lo que se ve aquí es lo que se verá leyendo.
                if (
                    hasTablature &&
                    host.tool == SongEditorTool.SONG &&
                    onTablatureScaleChange != null
                ) {
                    SettingsSliderRow(
                        icon = CanticoraIcons.TabScale,
                        label = "Tamaño de la tablatura",
                        value = tablatureScale,
                        onValueChange = onTablatureScaleChange,
                        valueRange = TabNotationScale.MIN_ZOOM..TabNotationScale.MAX_ZOOM
                    )
                    HairlineDivider()
                }

                when (host.tool) {
                    SongEditorTool.SONG -> SongToolPanel(uiState, onIntent)
                    SongEditorTool.CHORDS -> ChordsToolPanel(uiState, host::insert)
                    SongEditorTool.SECTIONS -> SectionsToolPanel(host::insert)
                    SongEditorTool.RHYTHM -> RhythmToolPanel(uiState, onIntent)
                    SongEditorTool.DIRECTIVES -> DirectivesToolPanel(uiState, host::insert)
                    SongEditorTool.SYMBOLS -> SymbolsToolPanel(host::insert)
                    SongEditorTool.ATTACHMENTS -> AttachmentsToolPanel(
                        uiState = uiState,
                        onIntent = onIntent,
                        onAddAttachmentFileClick = onAddAttachmentFileClick,
                        onOpenAttachmentClick = onOpenAttachmentClick,
                        onExportAttachmentClick = onExportAttachmentClick
                    )
                }
            }
        }
    }

    when (helpTool) {
        SongEditorTool.DIRECTIVES -> DirectivesHelpDialog(onDismiss = { helpTool = null })
        SongEditorTool.SYMBOLS -> SymbolsHelpDialog(onDismiss = { helpTool = null })
        else -> Unit
    }
}

/** `true` si la herramienta tiene un lightbox de ayuda que explicar. */
private val SongEditorTool.hasHelp: Boolean
    get() = this == SongEditorTool.DIRECTIVES || this == SongEditorTool.SYMBOLS

// ── Herramienta «Canción» ───────────────────────────────────────────────────────────────────

/** Tono, transposición, cejilla, notas de ensayo y categorías del canto. */
@Composable
private fun SongToolPanel(uiState: SongEditorUiState, onIntent: (SongEditorUiIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Density.CardPadding, end = Density.CardPadding, top = Density.Snug),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Density.Snug)
        ) {
            SettingsIcon(CanticoraIcons.Transposition, size = 18.dp)
            Text(
                text = "Transponer",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            MicroChip(
                text = NotationFormat.key(uiState.originalKey, uiState.notationSystem),
                color = MaterialTheme.colorScheme.primary
            )
            PillButton(text = "\u266d", onClick = { onIntent(SongEditorUiIntent.TransposeSong(-1)) })
            PillButton(text = "\u266f", onClick = { onIntent(SongEditorUiIntent.TransposeSong(+1)) })
            PillButton(
                text = "Orig.",
                onClick = { onIntent(SongEditorUiIntent.ResetTransposition) },
                enabled = uiState.originalKey != uiState.baseKey
            )
        }

        SettingsDropdownRow(
            icon = CanticoraIcons.Key,
            label = "Tono de la canción",
            value = NotationFormat.key(uiState.originalKey, uiState.notationSystem),
            options = KEYS.map { SettingsOption(it, NotationFormat.key(it, uiState.notationSystem)) },
            onSelect = { onIntent(SongEditorUiIntent.UpdateOriginalKey(it)) },
            selectedId = uiState.originalKey
        )

        // La cejilla es un número de traste: se escribe, no se elige de una botonera.
        SettingsNumberRow(
            icon = CanticoraIcons.Capo,
            label = "Cejilla (traste)",
            value = uiState.capo,
            onValueChange = { onIntent(SongEditorUiIntent.UpdateCapo(it)) },
            min = 0,
            max = 12
        )

        Spacer(modifier = Modifier.height(Density.Snug))

        OutlinedTextField(
            value = uiState.notes,
            onValueChange = { onIntent(SongEditorUiIntent.UpdateNotes(it)) },
            label = { Text("Notas de interpretación / Ensayo") },
            placeholder = { Text("Ej. El coro entra en la segunda estrofa…") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Density.CardPadding),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(Density.Snug))

        CategoryPicker(uiState, onIntent)
    }
}

/** Desplegable de categorías y etiquetas con los chips de las seleccionadas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPicker(uiState: SongEditorUiState, onIntent: (SongEditorUiIntent) -> Unit) {
    val selected = uiState.selectedCategories
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsMultiSelectRow(
            icon = CanticoraIcons.Categories,
            label = "Categorías y etiquetas",
            value = if (selected.isEmpty()) "Ninguna" else "${selected.size} marcadas",
            options = uiState.availableCategories.map { SettingsOption(it.name, it.name) },
            selectedIds = selected.toSet(),
            onToggle = { onIntent(SongEditorUiIntent.ToggleCategorySelection(it)) }
        )

        if (selected.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Density.CardPadding, end = Density.CardPadding, bottom = Density.Snug)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Density.Snug)
            ) {
                selected.forEach { catName ->
                    PillButton(
                        text = catName,
                        onClick = { onIntent(SongEditorUiIntent.ToggleCategorySelection(catName)) },
                        icon = Icons.Default.Close
                    )
                }
            }
        }
    }
}

// ── Herramienta «Acordes» ───────────────────────────────────────────────────────────────────

/**
 * Paleta de **acordes frecuentes**: los que de verdad se tocan, en dos filas fijas y sin scroll
 * lateral. Todo lo que se pulsa se inserta **en el cursor** del área ChordPro.
 *
 * No hay nota raíz ni modificadores: construir un acorde pieza a pieza obligaba a tres pulsaciones
 * y a desplazar la fila para descubrir que faltaban opciones. La nomenclatura tampoco se repite
 * aquí: ya está siempre al final del panel, en los ajustes comunes.
 */
@Composable
private fun ChordsToolPanel(
    uiState: SongEditorUiState,
    onInsert: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Density.CardPadding)) {
        Text(
            text = "Acordes frecuentes",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Density.Tight))
        ChordPaletteGrid(
            chords = FREQUENT_CHORDS.map { NotationFormat.key(it, uiState.notationSystem) },
            onChordClick = { chord -> onInsert("[$chord]") }
        )
        Spacer(modifier = Modifier.height(Density.Compact))
        Text(
            text = "Se insertan en el cursor como [Acorde]. Para otros acordes, escríbelos a mano " +
                "en el texto.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Herramienta «Secciones» ─────────────────────────────────────────────────────────────────

/** Atajos de estructura: insertan el par de directivas de una sección con un toque. */
@Composable
private fun SectionsToolPanel(onInsert: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Density.CardPadding)) {
        Text(
            text = "Inserta el par de directivas de la sección en el cursor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Density.Snug))
        SECTION_SHORTCUTS.forEach { (label, markup) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Density.Tight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Density.Snug)
            ) {
                PillButton(text = label, onClick = { onInsert(markup) })
                Text(
                    text = markup.replace("\n", " ").trim(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Herramienta «Ritmo» ─────────────────────────────────────────────────────────────────────

/** Compás, patrón de rasgueo y tempo del canto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RhythmToolPanel(uiState: SongEditorUiState, onIntent: (SongEditorUiIntent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Compás",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Density.CardPadding, bottom = Density.Tight)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Density.CardPadding),
            horizontalArrangement = Arrangement.spacedBy(Density.Tight)
        ) {
            PillButton(
                text = "—",
                onClick = { onIntent(SongEditorUiIntent.SelectTimeSignature(null)) },
                selected = uiState.timeSignature == null
            )
            TimeSignature.common.forEach { bar ->
                PillButton(
                    text = bar.toString(),
                    onClick = { onIntent(SongEditorUiIntent.SelectTimeSignature(bar)) },
                    selected = uiState.timeSignature == bar
                )
            }
        }

        Spacer(modifier = Modifier.height(Density.Snug))

        when {
            uiState.timeSignature == null -> Hint("Elige un compás para poder asignar un patrón rítmico.")
            uiState.patternsForSelectedBar.isEmpty() -> Hint(
                "No hay patrones guardados para ${uiState.timeSignature}. " +
                    "Créalos en la sección «Patrones rítmicos»."
            )
            else -> {
                SettingsDropdownRow(
                    icon = CanticoraIcons.Rhythms,
                    label = "Patrón de rasgueo",
                    value = uiState.selectedPattern?.name ?: "Sin patrón",
                    selectedId = uiState.selectedPattern?.id,
                    options = listOf(SettingsOption("", "Sin patrón")) +
                        uiState.patternsForSelectedBar.map { SettingsOption(it.id, it.name) },
                    onSelect = { id ->
                        onIntent(SongEditorUiIntent.SelectRhythmPattern(id.ifEmpty { null }))
                    }
                )
                uiState.selectedPattern?.let { pattern ->
                    Spacer(modifier = Modifier.height(Density.Snug))
                    RhythmPatternPreview(
                        pattern = pattern,
                        modifier = Modifier.padding(horizontal = Density.CardPadding)
                    )
                }
            }
        }

        HairlineDivider()

        // El tempo es un número: se escribe a mano. La indicación (Allegro, Andante…) se deduce
        // del BPM, así que solo se muestra como dato, sin botonera.
        SettingsNumberRow(
            icon = CanticoraIcons.Tempo,
            label = "Tempo (BPM)",
            value = uiState.tempoBpm,
            onValueChange = { onIntent(SongEditorUiIntent.UpdateTempoBpm(it)) },
            min = 0,
            max = 240
        )
        Text(
            text = uiState.tempoMarking.ifBlank { "Sin indicación" } +
                " · se deduce del BPM",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = Density.CardPadding, bottom = Density.Snug)
        )
    }
}

// ── Herramienta «Directivas» ────────────────────────────────────────────────────────────────

/** Directivas avanzadas de ChordPro: voces, bises, comentarios y ritmo por sección. */
@Composable
private fun DirectivesToolPanel(uiState: SongEditorUiState, onInsert: (String) -> Unit) {
    AdvancedDirectivesPanel(
        onInsert = onInsert,
        patterns = uiState.patternsForSelectedBar,
        timeSignatureLabel = uiState.timeSignature?.toString()
    )
}

// ── Herramienta «Símbolos» ──────────────────────────────────────────────────────────────────

/** Paleta de símbolos musicales de ChordPro (`<sym name/>`). */
@Composable
private fun SymbolsToolPanel(onInsert: (String) -> Unit) {
    SymbolsPanel(onInsert = onInsert)
}

// ── Herramienta «Adjuntos» ──────────────────────────────────────────────────────────────────

/** Audios, vídeos, partituras, imágenes y enlaces de ensayo del canto. */
@Composable
private fun AttachmentsToolPanel(
    uiState: SongEditorUiState,
    onIntent: (SongEditorUiIntent) -> Unit,
    onAddAttachmentFileClick: (() -> Unit)?,
    onOpenAttachmentClick: ((SongAttachment) -> Unit)?,
    onExportAttachmentClick: ((SongAttachment) -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Density.CardPadding)) {
        if (uiState.songId == null) {
            Text(
                text = "Guarda el canto para poder añadirle audios, vídeos o partituras.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            SongAttachmentsSection(
                showTitle = false,
                attachments = uiState.attachments,
                onAddFileClick = onAddAttachmentFileClick,
                onAddLink = { url, title -> onIntent(SongEditorUiIntent.AddAttachmentLink(url, title)) },
                onOpen = onOpenAttachmentClick,
                onExport = onExportAttachmentClick,
                onDelete = { attachment -> onIntent(SongEditorUiIntent.DeleteAttachment(attachment)) }
            )
        }
    }
}

/** Nota breve de ayuda dentro de un panel de herramienta. */
@Composable
internal fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Density.CardPadding)
    )
}

/** Atajos de sección: etiqueta visible y marcado ChordPro que insertan. */
private val SECTION_SHORTCUTS = listOf(
    "[Coro]" to "\n{soc}\n{eoc}\n",
    "[Estrofa]" to "\n{sov}\n{eov}\n",
    "[Puente]" to "\n{sob}\n{eob}\n",
    "[Intro]" to "\n{soi}\n{eoi}\n",
    "[Solo]" to "\n{sos}\n{eos}\n",
    "[Final]" to "\n{sof}\n{eof}\n"
)
