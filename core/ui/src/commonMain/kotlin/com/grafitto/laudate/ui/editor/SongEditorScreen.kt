package com.grafitto.laudate.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grafitto.laudate.domain.model.chordpro.ChordProSymbol
import com.grafitto.laudate.domain.model.chordpro.ChordProSymbolCategory
import com.grafitto.laudate.domain.model.music.NotationFormat
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.music.TempoMarking
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.tablature.TabDisplayPosition
import com.grafitto.laudate.domain.parser.ChordProValidationSeverity
import com.grafitto.laudate.domain.usecase.DuplicateSongMatch
import com.grafitto.laudate.ui.attachment.SongAttachmentsSection
import com.grafitto.laudate.ui.common.ChordPalette
import com.grafitto.laudate.ui.common.MicroChip
import com.grafitto.laudate.ui.common.MusicFigureGlyph
import com.grafitto.laudate.ui.common.DeleteActionButton
import com.grafitto.laudate.ui.common.Density
import com.grafitto.laudate.ui.common.PillButton
import com.grafitto.laudate.ui.navigation.CanticoraIcons
import com.grafitto.laudate.ui.rhythm.StrumSymbol
import com.grafitto.laudate.ui.navigation.SettingsDropdownRow
import com.grafitto.laudate.ui.navigation.SettingsOption
import com.grafitto.laudate.ui.common.PdfActionButton
import com.grafitto.laudate.ui.common.PdfExportDialog
import com.grafitto.laudate.ui.common.ShareSongDialog
import com.grafitto.laudate.ui.common.SaveActionButton
import com.grafitto.laudate.ui.common.ShareActionButton
import com.grafitto.laudate.ui.common.LaudateTopAppBar
import com.grafitto.laudate.ui.platform.PlatformBackHandler
import com.grafitto.laudate.ui.song.SongDetailScreen
import com.grafitto.laudate.ui.song.SongDetailUiIntent
import com.grafitto.laudate.ui.song.SongDetailUiState
import com.grafitto.laudate.ui.tablature.TablatureEditorPanel
import com.grafitto.laudate.ui.tablature.TablatureListContent
import com.grafitto.laudate.ui.tablature.TablatureUiIntent
import com.grafitto.laudate.ui.tablature.TablatureUiState

internal val KEYS = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

/**
 * Notas raíz del constructor de acordes. Se guardan en cifrado anglosajón (la forma
 * canónica interna) y se **renderizan** según la nomenclatura activa; así los botones
 * cambian a Do-Re-Mi al elegir notación latina.
 */
internal val ROOT_NOTES = listOf("C", "D", "E", "F", "G", "A", "B")
internal val MODIFIERS = listOf("#", "b", "m", "7", "m7", "maj7", "9", "sus4", "sus2", "dim", "dis", "aug", "/G")

/** Acordes frecuentes, en cifrado anglosajón; se renderizan en la notación activa. */
internal val FREQUENT_CHORDS = listOf("C", "D", "E", "F", "G", "A", "B", "Dm", "Em", "Am", "G7", "C7", "D7", "B7", "F#m")

/**
 * Alturas (en dp) del área de texto ChordPro del editor. El usuario puede **redimensionarla**
 * arrastrando el asa inferior —como un `textarea` de HTML— o maximizarla con un botón, porque
 * el valor fijo anterior (260 dp) se quedaba corto incluso en escritorio.
 */
private const val DEFAULT_EDITOR_HEIGHT_DP = 360f
private const val MIN_EDITOR_HEIGHT_DP = 200f
private const val MAX_EDITOR_HEIGHT_DP = 1000f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SongEditorScreen(
    uiState: SongEditorUiState,
    onIntent: (SongEditorUiIntent) -> Unit,
    onAddAttachmentFileClick: (() -> Unit)? = null,
    onOpenAttachmentClick: ((com.grafitto.laudate.domain.model.attachment.SongAttachment) -> Unit)? = null,
    onExportAttachmentClick: ((com.grafitto.laudate.domain.model.attachment.SongAttachment) -> Unit)? = null,
    /** Estado del editor de tablaturas del canto (tercera pestaña). `null` lo deshabilita. */
    tablatureUiState: TablatureUiState? = null,
    onTablatureIntent: ((TablatureUiIntent) -> Unit)? = null,
    /** Modo de visualización de la vista previa (comparte las preferencias del visor). */
    previewDisplayMode: SongDisplayMode = SongDisplayMode.CHORDS_ABOVE_TEXT,
    /** Posición de la tablatura en la vista previa. */
    previewTabPosition: TabDisplayPosition = TabDisplayPosition.AFTER,
    onPreviewDisplayModeChange: (SongDisplayMode) -> Unit = {},
    onPreviewTabPositionChange: (TabDisplayPosition) -> Unit = {},
    /**
     * **Tamaño del dibujo de la tablatura** en la vista previa: el de la rejilla y el de las cifras,
     * por separado. Los comparte con el visor y el modo canto porque son **la misma preferencia
     * global**: si en la vista previa se agrandara a 1,5 y al leer la canción volviera a 1,0, el
     * ajuste no serviría para preparar el atril.
     */
    previewTablatureScale: Float = 1f,
    /** Ajustes del pentagrama de tablatura (preferencia global, D-48). */
    previewTablatureStaffOptions: com.grafitto.laudate.domain.tablature.TabStaffOptions =
        com.grafitto.laudate.domain.tablature.TabStaffOptions(),
    /**
     * Estado compartido con el **panel de herramientas** del editor (tira de herramientas +
     * opciones de la activa). Si es `null`, el editor funciona igual pero sin panel.
     */
    toolHost: SongEditorToolHost? = null,
    modifier: Modifier = Modifier
) {
    // El gesto/botón «atrás» del sistema también pasa por el aviso de cambios sin guardar.
    PlatformBackHandler(enabled = true) { onIntent(SongEditorUiIntent.RequestExit) }

    // ── Área de texto ChordPro ──────────────────────────────────────────────────────────────
    // El `TextFieldValue` (texto + cursor) vive aquí, en la pantalla, y no dentro del formulario:
    // así el panel de herramientas puede insertar acordes, directivas y símbolos **en el cursor**
    // aunque el usuario esté en otra pestaña (vista previa, tablaturas).
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(uiState.chordProContent, TextRange(uiState.chordProContent.length)))
    }

    // Mantener sincronizado si cambia externamente (ej. carga inicial)
    if (textFieldValue.text != uiState.chordProContent && !textFieldValue.text.contains(uiState.chordProContent)) {
        textFieldValue = TextFieldValue(uiState.chordProContent, TextRange(uiState.chordProContent.length))
    }

    fun insertTextAtCursor(textToInsert: String) {
        val currentText = textFieldValue.text
        val selection = textFieldValue.selection
        val start = selection.min.coerceIn(0, currentText.length)
        val end = selection.max.coerceIn(0, currentText.length)
        val newText = currentText.substring(0, start) + textToInsert + currentText.substring(end)
        val newCursor = start + textToInsert.length
        textFieldValue = TextFieldValue(newText, TextRange(newCursor))
        onIntent(SongEditorUiIntent.UpdateChordProContent(newText))
    }

    // El panel de herramientas inserta a través del host; se registra mientras la pantalla vive.
    if (toolHost != null) {
        DisposableEffect(toolHost) {
            toolHost.registerInserter(::insertTextAtCursor)
            onDispose { toolHost.unregisterInserter() }
        }
    }

    var confirmDelete by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }

    // Diálogo inicial al crear una canción: ¿importar desde ChordPro?
    if (uiState.showImportPrompt) {
        ImportChordProDialog(
            importText = uiState.importText,
            issues = uiState.importIssues,
            onTextChange = { onIntent(SongEditorUiIntent.UpdateImportText(it)) },
            onValidate = { onIntent(SongEditorUiIntent.ValidateAndImport) },
            onSkip = { onIntent(SongEditorUiIntent.SetImportPromptVisible(false)) }
        )
    }

    // Diálogo de exportación con el texto ChordPro generado.
    uiState.exportedChordPro?.let { exported ->
        ExportChordProDialog(
            chordPro = exported,
            onDismiss = { onIntent(SongEditorUiIntent.DismissExport) }
        )
    }

    // Coincidencia de contenido: la canción ya existe; el usuario decide qué hacer.
    uiState.duplicateMatch?.let { match ->
        DuplicateSongDialog(
            match = match,
            onOverwrite = { onIntent(SongEditorUiIntent.ConfirmOverwrite) },
            onCreateNew = { onIntent(SongEditorUiIntent.CreateAsNewSong) },
            onDismiss = { onIntent(SongEditorUiIntent.DismissDuplicatePrompt) }
        )
    }

    // Antes de compartir se pregunta si el canto debe viajar con sus adjuntos.
    if (showShareDialog) {
        ShareSongDialog(
            subject = uiState.title.ifBlank { "esta canción" },
            attachmentCount = uiState.attachments.size,
            onDismiss = { showShareDialog = false },
            onConfirm = { withAttachments ->
                showShareDialog = false
                if (withAttachments) {
                    onIntent(SongEditorUiIntent.ShareChordProWithAttachments)
                } else {
                    onIntent(SongEditorUiIntent.ShareChordPro)
                }
            }
        )
    }

    // Antes de generar el PDF se pregunta el modo; la elección se recuerda. Las opciones con
    // tablatura solo se ofrecen si el canto tiene alguna (antes faltaba `hasTablature` y nunca
    // aparecían, por eso el PDF salía sin tablaturas).
    if (uiState.showPdfDialog) {
        PdfExportDialog(
            subject = uiState.title.ifBlank { "esta canción" },
            initialMode = uiState.pdfDisplayMode,
            hasTablature = tablatureUiState?.tablatures?.isNotEmpty() == true,
            onDismiss = { onIntent(SongEditorUiIntent.DismissPdfDialog) },
            onConfirm = { onIntent(SongEditorUiIntent.ConfirmExportPdf(it)) }
        )
    }

    // La barra superior se oculta al bajar y reaparece al subir (comportamiento tipo Gmail).
    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    if (confirmDelete) {        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar Canción") },
            text = { Text("¿Deseas eliminar «${uiState.title}»? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onIntent(SongEditorUiIntent.DeleteSong)
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

    // Aviso de cambios sin guardar: evita perder el trabajo al salir del editor por descuido.
    if (uiState.showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { onIntent(SongEditorUiIntent.DismissDiscardDialog) },
            title = { Text("Cambios sin guardar") },
            text = {
                Text(
                    "«${uiState.title.ifBlank { "Este canto" }}» tiene cambios que todavía no se " +
                        "han guardado. Si sales ahora se perderán."
                )
            },
            confirmButton = {
                TextButton(onClick = { onIntent(SongEditorUiIntent.ConfirmDiscardChanges) }) {
                    Text("Salir sin guardar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(SongEditorUiIntent.DismissDiscardDialog) }) {
                    Text("Seguir editando")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            LaudateTopAppBar(
                title = { Text(if (uiState.songId == null) "Nueva Canción" else "Editar Canción") },
                navigationIcon = {
                    IconButton(onClick = { onIntent(SongEditorUiIntent.RequestExit) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                },
                scrollBehavior = topBarScrollBehavior
            )
        },
        bottomBar = {
            // Con el editor de tablatura abierto manda SU barra de acciones: si se dejaran las dos,
            // habría dos botoneras a la vez y se pulsaría la equivocada.
            val tablatureEditorOpen = uiState.editorTab == SongEditorTab.TABLATURES &&
                tablatureUiState?.isEditorOpen == true
            if (!tablatureEditorOpen) {
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
                        if (uiState.songId != null) {
                            DeleteActionButton(onClick = { confirmDelete = true })
                        }
                        ShareActionButton(onClick = { showShareDialog = true })
                        PdfActionButton(onClick = { onIntent(SongEditorUiIntent.ExportPdf) })
                        SaveActionButton(
                            onClick = { onIntent(SongEditorUiIntent.SaveSong) },
                            isSaving = uiState.isSaving
                        )
                    }
                }
            }
        },
        modifier = modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = uiState.editorTab.ordinal) {
                SongEditorTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.editorTab == tab,
                        onClick = { onIntent(SongEditorUiIntent.SelectEditorTab(tab)) },
                        // La pestaña de tablaturas solo está disponible para un canto ya guardado.
                        enabled = tab != SongEditorTab.TABLATURES || uiState.songId != null,
                        text = { Text(tab.label) }
                    )
                }
            }

            when (uiState.editorTab) {
                SongEditorTab.PREVIEW -> {
                    // El borrador lo construye el ViewModel con el estado completo del editor, así que
                    // la vista previa muestra exactamente lo que se guardará (no la versión persistida).
                    // Los controles de lectura (plegables) se gestionan con estado local: la vista
                    // previa del editor no tiene su propio ViewModel de canción.
                    var previewControlsExpanded by remember { mutableStateOf(false) }
                    // La vista previa muestra las tablaturas **guardadas** y, además, el **borrador**
                    // que se está editando: si no, al crear una tablatura nueva la vista previa
                    // seguía vacía hasta guardarla y parecía que la tablatura no existía.
                    val previewTablatures = remember(tablatureUiState) {
                        val saved = tablatureUiState?.tablatures.orEmpty()
                        val draft = tablatureUiState
                            ?.takeIf { it.isEditorOpen && it.isEditorValid }
                            ?.editorAsTablature()
                        if (draft == null) saved else saved.filterNot { it.id == draft.id } + draft
                    }
                    // Si el canto tiene tablaturas, la vista previa arranca mostrándolas (antes
                    // heredaba el modo global, que por defecto no las incluye → «no se veían»).
                    var previewMode by remember(previewTablatures.isNotEmpty()) {
                        mutableStateOf(
                            if (previewTablatures.isNotEmpty()) SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE
                            else previewDisplayMode
                        )
                    }
                    // El modo que muestra la vista previa se persiste: así el panel de ajustes
                    // (que lee la preferencia global) ofrece «Posición de la tablatura» y no se
                    // contradice con lo que se ve en pantalla.
                    LaunchedEffect(previewMode) {
                        if (previewMode != previewDisplayMode) onPreviewDisplayModeChange(previewMode)
                    }
                    uiState.previewSong?.let { previewSong ->
                        SongDetailScreen(
                            uiState = SongDetailUiState(
                                song = previewSong,
                                originalKey = previewSong.originalKey,
                                currentKey = previewSong.originalKey,
                                // La vista previa debe usar la misma nomenclatura que el editor.
                                notationSystem = uiState.notationSystem,
                                rhythmPattern = uiState.selectedPattern,
                                isControlsExpanded = previewControlsExpanded,
                                // La vista previa comparte las tablaturas del canto.
                                tablatures = previewTablatures,
                                // La tablatura elegida la recuerda el ViewModel de tablaturas, que
                                // es el mismo que alimenta el panel de herramientas: así la barra de
                                // la vista previa y el panel derecho nunca se contradicen.
                                selectedTablatureId = tablatureUiState?.selectedTablatureId
                                    ?: previewTablatures.firstOrNull()?.id,
                                displayMode = previewMode,
                                tabDisplayPosition = previewTabPosition,
                                // (D-82) La vista previa dibuja la tablatura con el **mismo tamaño**
                                // que el visor y el modo canto: son la misma preferencia global. Antes
                                // no se pasaba y la rejilla salía siempre a 1,0, así que ajustar el
                                // tamaño para leer en el atril no tenía efecto al previsualizar.
                                tablatureScale = previewTablatureScale,
                                tablatureStaffOptions = previewTablatureStaffOptions
                            ),
                            onIntent = { detailIntent ->
                                // La vista previa ofrece las MISMAS opciones que el visor; los
                                // cambios de modo y posición se persisten como preferencia global.
                                when (detailIntent) {
                                    is SongDetailUiIntent.ChangeDisplayMode -> {
                                        previewMode = detailIntent.mode
                                        onPreviewDisplayModeChange(detailIntent.mode)
                                    }
                                    is SongDetailUiIntent.ChangeTabDisplayPosition ->
                                        onPreviewTabPositionChange(detailIntent.position)
                                    is SongDetailUiIntent.ToggleControls ->
                                        previewControlsExpanded = !previewControlsExpanded
                                    is SongDetailUiIntent.SelectTablature ->
                                        onTablatureIntent?.invoke(
                                            TablatureUiIntent.SelectTablature(detailIntent.tablatureId)
                                        )
                                    // Mover y reordenar sí escriben en disco: los ejecuta el
                                    // ViewModel de tablaturas, que es quien tiene el repositorio.
                                    is SongDetailUiIntent.MoveTablatureToSection ->
                                        onTablatureIntent?.invoke(
                                            TablatureUiIntent.MoveTablatureToSection(
                                                detailIntent.tablatureId,
                                                detailIntent.sectionId
                                            )
                                        )
                                    is SongDetailUiIntent.MoveTablatureOrder ->
                                        onTablatureIntent?.invoke(
                                            TablatureUiIntent.MoveTablatureOrder(
                                                detailIntent.tablatureId,
                                                detailIntent.delta
                                            )
                                        )
                                    else -> Unit
                                }
                            },
                            showActions = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                SongEditorTab.TABLATURES -> {
                    if (tablatureUiState != null && onTablatureIntent != null) {
                        if (tablatureUiState.isEditorOpen) {
                            // La vuelta al listado **no ocupa fila propia**: viaja al **cromo** del
                            // editor de tablatura y se pinta como un icono más, a la izquierda,
                            // delante de deshacer/rehacer. Antes era un `TextButton` con texto
                            // («Volver al listado») en su propia fila, y se comía una línea entera de
                            // la partitura en el móvil.
                            TablatureEditorPanel(
                                uiState = tablatureUiState,
                                onIntent = onTablatureIntent,
                                showActions = true,
                                onBackToList = {
                                    onTablatureIntent(TablatureUiIntent.CloseEditor)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            TablatureListContent(
                                uiState = tablatureUiState,
                                onIntent = onTablatureIntent,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Guarda el canto antes de crear sus tablaturas.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SongEditorTab.EDITOR -> {
                    EditorForm(
                        uiState = uiState,
                        onIntent = onIntent,
                        textFieldValue = textFieldValue,
                        onTextFieldValueChange = { newValue ->
                            textFieldValue = newValue
                            onIntent(SongEditorUiIntent.UpdateChordProContent(newValue.text))
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorForm(
    uiState: SongEditorUiState,
    onIntent: (SongEditorUiIntent) -> Unit,
    textFieldValue: TextFieldValue,
    onTextFieldValueChange: (TextFieldValue) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Density.CardPadding)
    ) {
        uiState.errorMessage?.let { msg ->
            Text(
                text = msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        OutlinedTextField(
            value = uiState.title,
            onValueChange = { onIntent(SongEditorUiIntent.UpdateTitle(it)) },
            label = { Text("Título de la canción *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(Density.Snug))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Density.Snug)
        ) {
            OutlinedTextField(
                value = uiState.number,
                onValueChange = { onIntent(SongEditorUiIntent.UpdateNumber(it)) },
                label = { Text("Número") },
                placeholder = { Text("402") },
                modifier = Modifier.width(120.dp),
                singleLine = true
            )
            OutlinedTextField(
                value = uiState.author,
                onValueChange = { onIntent(SongEditorUiIntent.UpdateAuthor(it)) },
                label = { Text("Autor o Fuente") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
        Text(
            text = "El número puede repetirse entre cancioneros; sirve para citar el canto en los " +
                "ensayos («el Aleluya 402»).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Density.Tight)
        )

        Spacer(modifier = Modifier.height(Density.Snug))

        // ── Área de texto ChordPro: altura ajustable, como un textarea ──────────────
        // Se puede arrastrar el asa inferior para hacerla más alta (útil sobre todo en
        // escritorio) o pulsar el botón para maximizarla/restaurarla.
        val density = LocalDensity.current
        var editorHeight by rememberSaveable { mutableStateOf(DEFAULT_EDITOR_HEIGHT_DP) }
        val isMaximized = editorHeight >= MAX_EDITOR_HEIGHT_DP

        OutlinedTextField(
            value = textFieldValue,
            onValueChange = onTextFieldValueChange,
            label = { Text("Letra y Acordes en ChordPro ([C]Palabra)") },
            modifier = Modifier
                .fillMaxWidth()
                .height(editorHeight.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
        )

        // Asa de redimensionado: arrastrar arriba/abajo cambia la altura del área de texto.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(30.dp)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            val delta = with(density) { dragAmount.toDp().value }
                            editorHeight = (editorHeight + delta)
                                .coerceIn(MIN_EDITOR_HEIGHT_DP, MAX_EDITOR_HEIGHT_DP)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                )
            }
            IconButton(
                onClick = {
                    editorHeight = if (isMaximized) DEFAULT_EDITOR_HEIGHT_DP else MAX_EDITOR_HEIGHT_DP
                }
            ) {
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = if (isMaximized) {
                        "Reducir el área de texto"
                    } else {
                        "Ampliar el área de texto"
                    }
                )
            }
        }
    }
}

/**
 * Diálogo que se muestra al crear una canción nueva: permite pegar un texto
 * ChordPro, validarlo y, si es correcto, importarlo al editor.
 */
@Composable
private fun ImportChordProDialog(
    importText: String,
    issues: List<com.grafitto.laudate.domain.parser.ChordProValidationIssue>,
    onTextChange: (String) -> Unit,
    onValidate: () -> Unit,
    onSkip: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("¿Importar desde ChordPro?") },
        text = {
            Column {
                Text(
                    text = "Pega el texto ChordPro de la canción. Si el archivo es válido " +
                        "se cargarán título, autor, tonalidad, tempo, capo y acordes. " +
                        "Si prefieres escribirla desde cero, pulsa «Empezar de cero».",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(Density.Compact))
                OutlinedTextField(
                    value = importText,
                    onValueChange = onTextChange,
                    label = { Text("Texto ChordPro") },
                    placeholder = { Text("{title: Mi canción}\n{key: G}\n{soc}\n[G]Letra...\n{eoc}") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
                if (issues.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(Density.Snug))
                    issues.forEach { issue ->
                        val isError = issue.severity == ChordProValidationSeverity.ERROR
                        val tint = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Density.Tight)
                        ) {
                            Icon(
                                imageVector = if (isError) Icons.Default.Close else Icons.Default.Warning,
                                contentDescription = if (isError) "Error" else "Aviso",
                                tint = tint,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = buildString {
                                    issue.lineNumber?.let { append("Línea $it: ") }
                                    append(issue.message)
                                },
                                color = tint,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onValidate,
                enabled = importText.isNotBlank()
            ) {
                Text("Validar e importar")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) { Text("Empezar de cero") }
        }
    )
}

/**
 * Diálogo que muestra el texto ChordPro generado y permite copiarlo al portapapeles
 * para exportarlo o compartirlo.
 */
@Composable
private fun ExportChordProDialog(
    chordPro: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar a ChordPro") },
        text = {
            OutlinedTextField(
                value = chordPro,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(chordPro))
                    onDismiss()
                }
            ) {
                Text("Copiar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

/**
 * Diálogo que aparece cuando el canto que se va a guardar ya existe en el catálogo con el mismo
 * contenido (misma huella ChordPro) pero distinto identificador: por ejemplo, un archivo que nos
 * han reenviado tras haberlo editado alguien.
 *
 * Ofrece las dos decisiones posibles sin perder datos: **sobrescribir** la copia existente
 * (conservando su identidad y su fecha de alta) o **crear una nueva** y dejar la anterior intacta.
 */
@Composable
private fun DuplicateSongDialog(
    match: DuplicateSongMatch,
    onOverwrite: () -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Esta canción ya existe") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Ya tienes una canción idéntica en el catálogo (mismo contenido). " +
                        "¿Quieres reemplazarla o conservar ambas?"
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = match.existing.title,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        match.existing.number?.takeIf { it.isNotBlank() }?.let { number ->
                            Text(
                                text = "Nº $number",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        match.existing.author?.takeIf { it.isNotBlank() }?.let { author ->
                            Text(
                                text = author,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Text(
                    text = "• Sobrescribir: actualiza la canción existente con esta versión.\n" +
                        "• Crear nueva: guarda una copia aparte y conserva la actual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onOverwrite) { Text("Sobrescribir") }
        },
        dismissButton = {
            TextButton(onClick = onCreateNew) { Text("Crear nueva") }
        }
    )
}

/**
 * Selector de nomenclatura de acordes (**preferencia global de la aplicación**).
 *
 * Se coloca justo debajo de «Categorías y etiquetas» para poder cambiarla sin salir del editor.
 * El cambio afecta a toda la app (visor, modo celebración, buscador…) y se conserva hasta que
 * el usuario la vuelva a cambiar.
 */
@Composable
private fun NotationSelector(
    notationSystem: NotationSystem,
    onSelect: (NotationSystem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Notación de acordes:", style = MaterialTheme.typography.titleSmall)
        Text(
            text = "Se aplica a toda la aplicación y se recuerda hasta que la cambies.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Density.Snug))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = notationSystem == NotationSystem.LATIN,
                onClick = { onSelect(NotationSystem.LATIN) },
                label = { Text("Latina (Do-Re-Mi)") }
            )
            FilterChip(
                selected = notationSystem == NotationSystem.ENGLISH,
                onClick = { onSelect(NotationSystem.ENGLISH) },
                label = { Text("Anglosajona (C-D-E)") }
            )
        }
    }
}

/**
 * Lightbox de ayuda del editor: explica en detalle un bloque del editor sin salir de la página.
 * El contenido se desplaza verticalmente para que quepa en pantallas pequeñas.
 */
@Composable
internal fun EditorHelpDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                content()
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendido") }
        }
    )
}

/** Entrada del lightbox de ayuda: un término y su explicación. */
@Composable
internal fun HelpEntry(term: String, description: String) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(
            text = term,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(text = description, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Ayuda del bloque «Directivas avanzadas». Se abre desde el icono (i) de la cabecera del panel de
 * herramientas, de modo que la explicación no ocupa ni un píxel del panel cuando no se usa.
 */
@Composable
internal fun DirectivesHelpDialog(onDismiss: () -> Unit) {
    EditorHelpDialog(title = "Directivas avanzadas", onDismiss = onDismiss) {
        HelpEntry(
            term = "¿Qué son?",
            description = "Son órdenes que se escriben entre llaves {} dentro del texto del canto. " +
                "No se cantan: le dicen a Canticora (y a cualquier programa que lea ChordPro) cómo " +
                "está organizado el canto. Al pulsar un botón, la directiva se inserta en el punto " +
                "donde tengas el cursor."
        )
        HelpEntry(
            term = "Sección con voz — {start_of_verse-soprano} … {end_of_verse}",
            description = "Abre y cierra un bloque cantado por una voz concreta. El sufijo -soprano " +
                "(o -alto, -tenor, -bajo, -asamblea, -solista) es una extensión de Canticora: marca " +
                "quién canta ese fragmento. Sirve para escribir polifonía: cada voz tiene su propio " +
                "bloque y la app los muestra etiquetados. En ChordPro estándar se lee como una estrofa " +
                "normal, así que el canto sigue siendo compatible."
        )
        HelpEntry(
            term = "Bises (se repite) — {repeat}",
            description = "Marca el comienzo de un fragmento que se repite. La app lo señala con la " +
                "etiqueta «Bises»."
        )
        HelpEntry(
            term = "Se repite ×2 — {repeat: 2} … {repeat_end}",
            description = "Indica que el fragmento se repite 2 veces; {repeat_end} cierra el bloque. " +
                "Cambia el número para otras repeticiones."
        )
        HelpEntry(
            term = "Coro etiquetado — {start_of_chorus label=\"Estribillo\"}",
            description = "Abre el coro y le pone un nombre propio. Esa etiqueta sustituye a «Coro» en " +
                "la vista previa y en el PDF."
        )
        HelpEntry(
            term = "Estrofa numerada — {start_of_verse Verse 1}",
            description = "Estrofa con nombre libre. El texto que sigue a la directiva es la etiqueta " +
                "que se muestra en lugar de «Estrofa»."
        )
        HelpEntry(
            term = "Ritmo por sección — {x_rhythm: Nombre}",
            description = "Asigna a la sección el patrón de rasgueo con ese nombre, para cambiar de " +
                "ritmo al pasar de la estrofa al coro. Escríbela DENTRO de la sección (después de su " +
                "{start_of_...}). El nombre debe coincidir con uno de tus patrones de «Ritmos de rasgueo»."
        )
        HelpEntry(
            term = "Comentario por voz — {comment-alto: ...}",
            description = "Comentario dirigido a una voz concreta: indicaciones de ensayo, entradas, " +
                "dinámica… Se muestra en cursiva y no se canta. El sufijo -alto es una extensión de " +
                "Canticora."
        )
        HelpEntry(
            term = "Relación con ChordPro",
            description = "Todo esto es texto plano: puedes escribir las directivas a mano en la " +
                "pestaña ChordPro y se guardan tal cual al exportar. En el manual tienes la referencia " +
                "completa de directivas."
        )
    }
}

/** Voces de la polifonía: inicial visible y sufijo ChordPro que se inserta. */
private val VOICE_SHORTCUTS = listOf(
    "S" to "soprano",
    "A" to "alto",
    "T" to "tenor",
    "B" to "bajo",
    "As" to "asamblea",
    "Sol" to "solista"
)

/** Etiquetas y repeticiones: rótulo corto y marcado que se inserta. */
private val LABEL_SHORTCUTS = listOf(
    "Bises" to "\n{repeat}\n",
    "×2" to "\n{repeat: 2}\n\n{repeat_end}\n",
    "Coro" to "\n{start_of_chorus label=\"Estribillo\"}\n\n{end_of_chorus}\n",
    "Estrofa" to "\n{start_of_verse Verse 1}\n\n{end_of_verse}\n"
)

/** Voces disponibles para los comentarios dirigidos (`{comment-voz: ...}`). */
private val COMMENT_VOICES = listOf("soprano", "alto", "tenor", "bajo", "asamblea", "todos")

/**
 * Panel para insertar directivas avanzadas de ChordPro de forma cómoda: secciones con voz
 * (`{sov-soprano}`), bises/repetición (`{repeat}`), ritmo por sección y comentarios por voz.
 *
 * Todo se pulsa con **iniciales o rótulos cortos** en filas de igual peso: nada de botones largos
 * que obliguen a desplazar lateralmente para descubrir que faltan voces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdvancedDirectivesPanel(
    onInsert: (String) -> Unit,
    patterns: List<RhythmPattern>,
    timeSignatureLabel: String?
) {
    var commentVoice by remember { mutableStateOf("alto") }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Density.CardPadding)) {
        // Secciones con voz concreta (polifonía): iniciales, todas visibles de una vez.
        Text("Sección con voz", style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Density.Tight),
            horizontalArrangement = Arrangement.spacedBy(Density.Tight)
        ) {
            VOICE_SHORTCUTS.forEach { (initial, voice) ->
                PillButton(
                    text = initial,
                    onClick = { onInsert("\n{start_of_verse-$voice}\n\n{end_of_verse}\n") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            text = VOICE_SHORTCUTS.joinToString(" · ") { "${it.first} = ${it.second}" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Density.Tight)
        )

        Spacer(modifier = Modifier.height(Density.Compact))

        // Etiqueta y repetición: rótulos cortos, también en una sola fila.
        Text("Etiqueta y repetición", style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Density.Tight),
            horizontalArrangement = Arrangement.spacedBy(Density.Tight)
        ) {
            LABEL_SHORTCUTS.forEach { (label, markup) ->
                PillButton(
                    text = label,
                    onClick = { onInsert(markup) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(Density.Compact))

        // Cambio de patrón rítmico por sección (`{x_rhythm: Nombre}`). Solo se ofrecen los
        // patrones del compás elegido para la canción: un patrón en 4/4 no encaja en un canto
        // en 3/4. La directiva se inserta con el nombre exacto del patrón.
        when {
            timeSignatureLabel == null -> Hint("Elige el compás del canto para ver sus ritmos.")
            patterns.isEmpty() -> Hint(
                "No hay ritmos para el compás $timeSignatureLabel. Créalos en «Ritmos de rasgueo»."
            )
            else -> SettingsDropdownRow(
                icon = CanticoraIcons.Rhythms,
                label = "Ritmo de la sección (compás $timeSignatureLabel)",
                value = "Insertar ritmo…",
                selectedId = null,
                options = patterns.map { SettingsOption(it.name, it.name) },
                onSelect = { name -> onInsert("\n{x_rhythm: $name}\n") }
            )
        }

        Spacer(modifier = Modifier.height(Density.Compact))

        // Comentarios por voz: la voz se elige en un desplegable compacto y se inserta con un
        // botón de icono (el texto «Añadir comentario» no cabía sin robar altura).
        SettingsDropdownRow(
            icon = CanticoraIcons.Voice,
            label = "Comentario por voz",
            value = commentVoice,
            selectedId = commentVoice,
            options = COMMENT_VOICES.map { SettingsOption(it, it) },
            onSelect = { commentVoice = it }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Density.Tight),
            horizontalArrangement = Arrangement.End
        ) {
            PillButton(
                text = "",
                icon = Icons.Default.AddComment,
                iconDescription = "Insertar comentario",
                onClick = { onInsert("\n{comment-$commentVoice: }\n") }
            )
        }
    }
}

/**
 * Ayuda del bloque «Símbolos musicales». Se abre desde el icono (i) de la cabecera del panel de
 * herramientas.
 */
@Composable
internal fun SymbolsHelpDialog(onDismiss: () -> Unit) {
    EditorHelpDialog(title = "Símbolos musicales", onDismiss = onDismiss) {
        HelpEntry(
            term = "¿Qué son?",
            description = "Son dibujos musicales (rasgueos, barras de compás, repeticiones, " +
                "alteraciones, letras y cifras en círculo) que se colocan en un punto exacto de la " +
                "letra. Se escriben con la etiqueta <sym nombre/>, por ejemplo <sym arrow-down/>. " +
                "Canticora los dibuja en pantalla y los conserva tal cual al exportar a ChordPro."
        )
        HelpEntry(
            term = "Rasgueo (abajo / arriba)",
            description = "Flechas de mano derecha: hacia abajo es un rasgueo descendente y hacia " +
                "arriba ascendente. Se combinan con acento ('), arpegio (~), staccato (·) y " +
                "apagado (×)."
        )
        HelpEntry(
            term = "Barras de compás",
            description = "Separadores de compás: simple, doble, gruesa, gruesa doble, inicio de " +
                "repetición y fin de repetición."
        )
        HelpEntry(
            term = "Repeticiones",
            description = "Signos de repetición: |: comienza, :| termina y :|: termina y vuelve a " +
                "comenzar."
        )
        HelpEntry(
            term = "Alteraciones",
            description = "Bemol, becuadro y sostenido, para escribir notas alteradas dentro de la " +
                "letra."
        )
        HelpEntry(
            term = "Letras y cifras en círculo",
            description = "Números y letras dentro de un círculo (①, Ⓐ…), útiles para marcar estrofas, " +
                "voces o llamadas."
        )
        HelpEntry(
            term = "Relación con ChordPro",
            description = "No hace falta memorizar los nombres: la paleta los inserta por ti en la " +
                "posición del cursor y el texto exportado sigue siendo ChordPro válido."
        )
    }
}

/**
 * Paleta de símbolos musicales de ChordPro (`<sym name/>`): rasgueo, barras, repeticiones,
 * alteraciones y letras/cifras en círculo. Al pulsar uno se inserta su marcado en el texto.
 *
 * Los botones muestran **solo el símbolo**: el nombre viaja en un *tooltip*, porque en una paleta
 * de símbolos la etiqueta de texto solo añade ruido y ancho. La paleta está **siempre desplegada**:
 * vive dentro de la herramienta «Símbolos», así que un botón «Mostrar/Ocultar» solo añadía un clic.
 */
@Composable
internal fun SymbolsPanel(onInsert: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Density.CardPadding)) {
        ChordProSymbolCategory.entries.forEach { category ->
            val symbols = ChordProSymbol.palette()[category].orEmpty()
            if (symbols.isEmpty()) return@forEach

            Text(category.displayName, style = MaterialTheme.typography.labelSmall)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = Density.Tight, bottom = Density.Compact),
                horizontalArrangement = Arrangement.spacedBy(Density.Tight)
            ) {
                symbols.forEach { symbol ->
                    SymbolGlyphButton(
                        glyph = symbol.glyph,
                        label = symbol.label,
                        onClick = { onInsert(ChordProSymbol.markupOf(symbol.symbolName)) }
                    )
                }
            }
        }

        // Letras y cifras en círculo (Ⓐ, ①, …)
        Text("En círculo", style = MaterialTheme.typography.labelSmall)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = Density.Tight),
            horizontalArrangement = Arrangement.spacedBy(Density.Tight)
        ) {
            ChordProSymbol.circles.forEach { circled ->
                SymbolGlyphButton(
                    glyph = circled.glyph,
                    label = "En círculo: ${circled.character}",
                    onClick = { onInsert(ChordProSymbol.markupOf(circled.symbolName)) }
                )
            }
        }

        Text(
            text = "Se insertan como marcado ChordPro estándar, p. ej. <sym arrow-down/>.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Density.Compact)
        )
    }
}

/**
 * Botón de un **símbolo musical**: solo el glifo, sin texto.
 *
 * El nombre del símbolo se muestra en un *tooltip* al pasar el ratón (escritorio) o al mantener
 * pulsado (táctil), de modo que la paleta queda compacta sin perder la pista de qué es cada signo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SymbolGlyphButton(
    glyph: String,
    label: String,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        Surface(
            modifier = Modifier
                .height(Density.PillHeight)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(Density.CornerSmall),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Box(
                modifier = Modifier.padding(horizontal = Density.Compact),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = glyph,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Pequeña vista previa del patrón rítmico usando el modelo del dominio:
 * figuras y flechas agrupadas por compás.
 */
@Composable
internal fun RhythmPatternPreview(pattern: RhythmPattern, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
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
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                group.forEach { event ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // (D-91) Figura con Bravura y dirección con el símbolo vectorial: en la web
                        // los símbolos musicales y las flechas del texto salían como cajas vacías.
                        MusicFigureGlyph(figure = event.figure, size = 18.dp)
                        StrumSymbol(
                            direction = event.effectiveDirection,
                            articulation = event.effectiveArticulation,
                            size = 14.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
