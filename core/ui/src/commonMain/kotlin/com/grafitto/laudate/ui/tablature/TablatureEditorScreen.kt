package com.grafitto.laudate.ui.tablature

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.painterResource
import com.grafitto.laudate.ui.generated.resources.Res
import com.grafitto.laudate.ui.generated.resources.edit_note
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.score.toScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabAccidental
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabBendAmount
import com.grafitto.laudate.domain.model.tablature.TabDynamic
import com.grafitto.laudate.domain.model.tablature.TabGraceType
import com.grafitto.laudate.domain.model.tablature.TabHairpin
import com.grafitto.laudate.domain.model.tablature.TabHairpinType
import com.grafitto.laudate.domain.model.tablature.TabJump
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabFrettingFinger
import com.grafitto.laudate.domain.model.tablature.TabPickingFinger
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import com.grafitto.laudate.domain.model.tablature.TabTupletBracketType
import com.grafitto.laudate.domain.model.tablature.TabTupletNumberType
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.tablature.FretMarkStyle
import com.grafitto.laudate.domain.tablature.LineStyle
import com.grafitto.laudate.domain.tablature.MarksPlacement
import com.grafitto.laudate.domain.tablature.MinimStyle
import com.grafitto.laudate.domain.tablature.ParenthesizeTiedFret
import com.grafitto.laudate.domain.tablature.ShowTiedFret
import com.grafitto.laudate.domain.tablature.MAX_FRET
import com.grafitto.laudate.domain.tablature.MAX_PALM_MUTE_SPAN
import com.grafitto.laudate.domain.tablature.TabGlyphs
import com.grafitto.laudate.domain.tablature.TabGlyphKind
import com.grafitto.laudate.domain.tablature.TabMeasureMeter
import com.grafitto.laudate.domain.tablature.TabMixer
import com.grafitto.laudate.domain.tablature.TabNotationScale
import com.grafitto.laudate.domain.tablature.NoteValueStyle
import com.grafitto.laudate.domain.tablature.RhythmRepeat
import com.grafitto.laudate.domain.tablature.TabShortcuts
import com.grafitto.laudate.domain.tablature.TabStaffOptions
import com.grafitto.laudate.domain.tablature.TabStringData
import com.grafitto.laudate.domain.tablature.TabSymbolCompatibility
import com.grafitto.laudate.domain.tablature.TabTechniqueFamily
import com.grafitto.laudate.domain.tablature.TabTranspositionMode
import com.grafitto.laudate.domain.tablature.TabVoiceColors
import com.grafitto.laudate.domain.tablature.TablatureEditing
import com.grafitto.laudate.domain.tablature.fretText
import com.grafitto.laudate.domain.tablature.techniqueLabel
import com.grafitto.laudate.domain.tablature.vectorialGlyph
import com.grafitto.laudate.ui.rhythm.formatBeats
import com.grafitto.laudate.ui.common.AdvancedSection
import com.grafitto.laudate.ui.common.CollapsibleSection
import com.grafitto.laudate.ui.common.Density
import com.grafitto.laudate.ui.common.DeleteActionButton
import com.grafitto.laudate.ui.common.DuplicateActionButton
import com.grafitto.laudate.ui.common.SaveActionButton
import com.grafitto.laudate.ui.common.LaudateTopAppBar
import com.grafitto.laudate.ui.common.MicroChip
import com.grafitto.laudate.ui.common.MusicGlyph
import com.grafitto.laudate.ui.navigation.CanticoraIcons
import com.grafitto.laudate.ui.editor.EditorHelpDialog
import com.grafitto.laudate.ui.editor.HelpEntry
import com.grafitto.laudate.ui.navigation.SettingsButtonGroup
import com.grafitto.laudate.ui.navigation.SettingsDropdownRow
import com.grafitto.laudate.ui.navigation.SettingsOption
import com.grafitto.laudate.ui.navigation.SettingsSliderRow
import com.grafitto.laudate.ui.navigation.SettingsTextFieldRow
import com.grafitto.laudate.ui.platform.PlatformBackHandler
import com.grafitto.laudate.ui.platform.isTouchPlatform
import kotlin.math.roundToInt

/**
 * Pantalla **independiente** de creación/edición de una tablatura (ADR-017, *Feature T*).
 *
 * Envuelve [TablatureEditorPanel] (el formulario real, sin `Scaffold`) en una pantalla propia con
 * barra superior y acciones. El mismo panel se incrusta en la tercera pestaña del editor de canción.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TablatureEditorScreen(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlatformBackHandler(enabled = true) { onIntent(TablatureUiIntent.RequestExit) }

    Scaffold(
        modifier = modifier,
        topBar = {
            LaudateTopAppBar(
                title = { Text(if (uiState.editorIsNew) "Nueva tablatura" else "Editar tablatura") },
                navigationIcon = {
                    IconButton(onClick = { onIntent(TablatureUiIntent.RequestExit) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        bottomBar = {
            TablatureActionBar(uiState, onIntent, onBack = onNavigateBack)
        }
    ) { padding ->
        TablatureEditorPanel(
            uiState = uiState,
            onIntent = onIntent,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        )
    }

    TablaturePendingDialogs(uiState, onIntent)
}

/**
 * **Formulario del editor de tablatura** (sin `Scaffold`), reutilizable tanto en la pantalla propia
 * como incrustado en la tercera pestaña del editor de canción.
 *
 * La pantalla se queda con el **contenido**: la rejilla, los compases y la paleta de escritura
 * (traste y cuerda). Todo lo demás —marcas del manuscrito, tonalidad, tamaño y metadatos— vive en
 * [TablatureEditorOptions], que el **panel de herramientas** (derecha) pinta. Así no hay dos
 * botoneras que digan lo mismo ni ajustes robándole sitio a la rejilla.
 *
 * @param showActions si `true`, incluye la barra de acciones (duplicar / eliminar / guardar). La
 *   pantalla propia la pone en el `bottomBar` del `Scaffold` y la deja en `false`.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TablatureEditorPanel(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    modifier: Modifier = Modifier,
    showActions: Boolean = false,
    /**
     * Vuelta al **listado** de tablaturas del canto sin salir de la pestaña. Si no es `null`, el cromo
     * pinta un icono de **lista** a la izquierda del todo, en la **misma fila** que deshacer y el
     * resto de herramientas. Antes era un botón con texto en su propia fila y se comía una línea de
     * la partitura en el móvil.
     */
    onBackToList: (() -> Unit)? = null
) {
    // El panel pinta su **propio fondo**: así es autocontenido y se puede incrustar en cualquier
    // contenedor (la pestaña del editor de canción, el arnés de render) sin que el área de scroll
    // quede sin pintar.
    // **Arrastre de celdas de paleta** (B-13): las paletas viven en el **panel de herramientas**
    // (D-58), que es otra rama del árbol de composición, así que el estado no puede ser un
    // `remember` local: se comparte por [TablaturePaletteDrag]. Al soltar un símbolo sobre la
    // partitura se aplica a la nota que hay debajo.
    val paletteDrag = TablaturePaletteDrag
    // (D-14) **Escala de «ajustar al ancho»**: la calcula la rejilla (es quien conoce su ancho real)
    // y la consume el desplegable de zoom de la barra de estado. `null` mientras no se haya medido.
    var fitScale by remember { mutableStateOf<Float?>(null) }
    // (D-45) **Propiedades del pentagrama**: el `Staff/Part properties` de MuseScore. Se abre desde el
    // menú «Pentagrama» del cromo y se pinta aquí, fuera de la rejilla, para que el diálogo no se
    // desmonte al desplazar la partitura.
    var staffPropertiesOpen by remember { mutableStateOf(false) }
    // **Ayuda contextual del editor** (D-95): un solo diálogo que explica lo que se puede hacer
    // **desde donde está el cursor** —compás, columna, cuerda, selección—, porque en táctil no hay
    // hover y los tooltips de los botones nunca llegan a verse.
    var helpOpen by remember { mutableStateOf(false) }
    // **Teclado numérico** (solo táctil). Vive **aquí**, en el panel, y no dentro de la barra de
    // escritura: así lo puede abrir tanto su botón como **tocar la rejilla**. En una tableta sin
    // teclado físico, tocar una cifra y que no aparezca nada para escribir el traste deja al usuario
    // sin salida —solo movía el cursor—, que es justo lo que reportó.
    val isTouchDevice = remember { isTouchPlatform() }
    var showKeypad by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // La rejilla es la superficie de escritura: se lleva el **foco del teclado físico**. Se pide
        // al abrir el editor para que las flechas y los dígitos actúen sin tener que tocar nada.
        val gridFocus = remember { FocusRequester() }
        LaunchedEffect(uiState.editingId, uiState.isEditorOpen, uiState.editorIsNew) {
            if (uiState.isEditorOpen) runCatching { gridFocus.requestFocus() }
        }
        // La **barra de escritura** (duración, silencios y teclado de traste) y el cromo van **fijos**
        // (no se van con el scroll), pegados a la partitura, como la barra de herramientas de
        // MuseScore: siempre a mano mientras se escribe. Antes la duración vivía en el panel de
        // herramientas —en móvil, en un `ModalBottomSheet` que tapaba la partitura— y había que ir y
        // volver para cada nota.
        Column(
            modifier = Modifier.padding(horizontal = Density.Compact),
            verticalArrangement = Arrangement.spacedBy(Density.Tight)
        ) {
            TablatureWritingBar(
                uiState = uiState,
                onIntent = onIntent,
                gridFocus = gridFocus,
                showKeypad = showKeypad,
                onShowKeypad = { showKeypad = true }
            )
            TablatureEditorChrome(
                uiState = uiState,
                onIntent = onIntent,
                gridFocus = gridFocus,
                onOpenStaffProperties = { staffPropertiesOpen = true },
                onOpenHelp = { helpOpen = true },
                onBackToList = onBackToList
            )
        }
        if (helpOpen) {
            TablatureEditorHelpDialog(uiState = uiState, onDismiss = { helpOpen = false })
        }
        if (staffPropertiesOpen) {
            StaffPropertiesDialog(
                uiState = uiState,
                onIntent = onIntent,
                onDismiss = { staffPropertiesOpen = false }
            )
        }
        // (D-49) **Datos de cuerda**: el «String data» de MuseScore. El estado vive en el ViewModel
        // (no en un `remember`) para que el diálogo sobreviva a la recomposición del panel.
        if (uiState.showStringDataDialog) {
            StringDataDialog(
                uiState = uiState,
                onIntent = onIntent,
                onDismiss = { onIntent(TablatureUiIntent.CloseStringDataDialog) }
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // **La rejilla ocupa todo el ancho** (D-58): las paletas ya **no** son una columna aparte.
        // Viven dentro del **panel de herramientas** de la derecha —el que la aplicación ya tenía—,
        // encima de los ajustes. Así hay un solo panel lateral, la partitura no queda encajonada
        // entre dos columnas y no hay dos sitios donde buscar el mismo símbolo.
        // **RePág/AvPág** (D-30): el estado de scroll de la rejilla se **iza** aquí para que el
        // manejador de teclas pueda moverlo. Se desplaza el 90 % de la ventana y no el 100 % para
        // que quede un renglón de solape: sin él, el lector pierde el hilo entre pantallas.
        val gridScroll = rememberScrollState()
        val gridScope = rememberCoroutineScope()
        val pageScroll: (Int) -> Unit = { direction ->
            gridScope.launch {
                gridScroll.scrollBy(direction * gridScroll.viewportSize * PAGE_SCROLL_FRACTION)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(gridScroll)
                .padding(horizontal = Density.Compact),
            verticalArrangement = Arrangement.spacedBy(Density.Snug)
        ) {
            TablatureEditorGrid(
                uiState = uiState,
                onIntent = onIntent,
                focusRequester = gridFocus,
                hitTester = paletteDrag.hitTester,
                onFitScale = { fitScale = it },
                onPageScroll = pageScroll,
                // En táctil, tocar la partitura abre el teclado numérico: es el gesto que espera
                // cualquiera en una tableta, y sin él tocar una cifra solo movía el cursor.
                onGridTap = if (isTouchDevice) {
                    { showKeypad = true }
                } else {
                    null
                }
            )
            Spacer(Modifier.height(Density.CardPadding))
        }

        // **Barra de estado** (B-1): describe el elemento del cursor con el vocabulario de MuseScore
        // (`Nota; Altura: Sol3; Cuerda: 3; Traste: 0; Duración: Negra; Voz: 1; Compás: 2; Tiempo: 1;
        // Pentagrama 1`). Va **abajo**, como en MuseScore, y no se va con el scroll.
        TablatureStatusBar(uiState, onIntent, fitScale)

        if (showActions) {
            TablatureActionBar(uiState, onIntent, onBack = { onIntent(TablatureUiIntent.CloseEditor) })
        }
    }
    PaletteDragGhost(paletteDrag)
    TablaturePendingDialogs(uiState, onIntent)

    // **Hoja del teclado numérico**: la abre el botón **Teclado** de la barra y, en táctil, **tocar la
    // rejilla**. Cada tecla acumula dígitos igual que el teclado físico («1» y luego «2» dan el traste
    // 12) y el cursor avanza al pasar el tiempo de la cifra.
    if (isTouchDevice && showKeypad) {
        TablatureKeypadSheet(
            onDigit = { digit ->
                onIntent(TablatureUiIntent.WriteFretAndAdvance(digit))
            },
            onBackspace = {
                onIntent(TablatureUiIntent.BackspaceCursor)
            },
            onDismiss = { showKeypad = false }
        )
    }
}

/**
 * Fracción de la ventana que avanza **RePág/AvPág** (D-30). No es `1.0`: se deja un renglón de
 * solape para que el lector no pierda el hilo entre pantallas, que es lo que hace cualquier visor de
 * documentos y lo que hace MuseScore al desplazar la vista.
 */
private const val PAGE_SCROLL_FRACTION = 0.9f

/** Etiqueta de prueba del lienzo de la tablatura (D-30). */
const val TAB_CANVAS_TEST_TAG = "tablature-canvas"

/**
 * Etiqueta de prueba de una **celda de paleta** (D-79). Las celdas no tienen nodo de texto —el
 * símbolo se pinta en un `Canvas`—, así que sin una etiqueta propia los tests no pueden distinguir
 * una celda de paleta de cualquier otro nodo con descripción accesible y alto parecido.
 */
const val TAB_PALETTE_CELL_TEST_TAG = "tablature-palette-cell"

/**
 * **Escritorio** alrededor de la hoja (D-10): el aire gris que separa la página del borde del panel.
 * MuseScore deja un marco ancho; con 8 dp la hoja parecía pegada y no se leía como papel.
 */
private val TabPaperDeskPadding = 20.dp

/** **Margen de página** dentro de la hoja: el pentagrama nunca toca el borde del papel. */
private val TabPaperMargin = 12.dp

/**
 * Color del **papel** de la partitura.
 *
 * **Sigue el tema**: en modo claro es claro y en modo oscuro es **oscuro de verdad** (el fondo oscuro
 * del tema, no un blanco encajado). Antes se fijaba en blanco con el argumento de que «una partitura
 * sobre negro no se lee»: era un criterio equivocado, porque el usuario que elige modo oscuro quiere
 * modo oscuro, no un rectángulo blanco deslumbrando en mitad de la pantalla. Lo pidió así: *«¿por qué
 * has puesto el fondo blanco en el modo edición en modo oscuro? ¿por qué no lo dejas como modo oscuro
 * de verdad: fondo negro y líneas, números y símbolos blancos?»*.
 *
 * El PDF exportado sigue siendo blanco (es un documento para imprimir), y eso es independiente del
 * color del lienzo en pantalla.
 */
@Composable
private fun tabPaperColor(): androidx.compose.ui.graphics.Color =
    MaterialTheme.colorScheme.surfaceContainerLowest

/**
 * Color de la **tinta** de la música (líneas, cifras y símbolos).
 *
 * **Sigue el tema**, emparejado con [tabPaperColor]: en claro es casi negro y en oscuro casi blanco.
 * Es lo correcto porque el papel también sigue el tema; el error anterior fue fijar el papel en
 * blanco y, para compensar, fijar la tinta en negro. Con los dos atados al tema, la partitura se lee
 * en los dos modos y en oscuro se ve como lo que es: una partitura en modo oscuro.
 */
@Composable
private fun tabPaperInk(): androidx.compose.ui.graphics.Color =
    MaterialTheme.colorScheme.onSurface

/**
 * **Opciones de la tablatura** que se está escribiendo, para el **panel de herramientas** (derecha).
 *
 * Todo lo que no es la rejilla ni la paleta de escritura (traste y cuerda) vive aquí: las marcas del
 * manuscrito del compás seleccionado, la tonalidad y la transposición, el tamaño y los metadatos
 * propios de la tablatura (instrumento, compás, subdivisión, sección y nota del copista). El título,
 * el rótulo y el momento **no** se repiten aquí: son datos del canto y viven en la herramienta
 * «Canción». Así la pantalla se queda con el **contenido** —la rejilla— y el panel con **las opciones
 * de la herramienta**, como en un editor gráfico: al elegir otra herramienta, el panel cambia.
 *
 * Es una pieza **Compose Multiplatform** y sin lógica: pinta y delega en quien la usa.
 */
@Composable
fun TablatureEditorOptions(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * `true` = solo el bloque **contextual** (propiedades de la nota del cursor y sus adornos), que
     * el panel pinta **arriba**, antes de las paletas (D-84). `false` = los ajustes del documento
     * (tonalidad, tamaño, instrumento…), que van **debajo**.
     */
    contextualOnly: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Density.Snug)
    ) {
        // ── Propiedades **contextuales** (B-11) ────────────────────────────────────────────
        // En MuseScore el panel de propiedades (`F8`) solo enseña lo que se puede cambiar en la
        // **selección actual**. Aquí igual: las paletas de marcas viven **arriba**, en este mismo
        // panel (D-58), y esta sección se queda con lo que es propiedad de la tablatura.
        // `F8` la oculta entera.
        if (!uiState.showProperties) {
            if (contextualOnly) return@Column
            Text(
                text = "Propiedades ocultas · F8 para mostrarlas",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Density.CardPadding, top = Density.Tight)
            )
            return@Column
        }

        // (D-84) «Nota» y «Adornos» son lo que se toca **a cada compás** mientras se escribe, así que
        // el panel los pinta **lo primero**, antes de las paletas de símbolos.
        if (contextualOnly) {
            NotePropertiesSection(uiState, onIntent)
            GraceNotesSection(uiState, onIntent)
            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = Density.CardPadding)
                )
            }
            return@Column
        }

        TablatureKeyboardActionsPanel(uiState, onIntent)

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Density.CardPadding)
            )
        }

        // ── Tonalidad y transposición ──────────────────────────────────────────────────────
        CollapsibleSection(
            title = "Tonalidad y transposición",
            subtitle = transpositionModeLabel(uiState.transpositionMode)
        ) {
            // Tonalidad DEL CANTO: la tablatura puede redigitarse a ese tono (fallo 3, §13).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Tono del canto: ${uiState.songKey.ifBlank { "—" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { onIntent(TablatureUiIntent.RefingerToSongKey) },
                    enabled = uiState.canRefingerToSongKey
                ) { Text("Redigitar al tono del canto") }
            }
            // Tonalidad ESCRITA de la tablatura: con qué tono se transpondrá al seguir al canto.
            WrittenKeyPicker(uiState, onIntent)
            Text(
                text = transpositionModeHint(uiState.transpositionMode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ── Tamaño de la tablatura (cifras y rejilla) ──────────────────────────────────────
        SettingsSliderRow(
            icon = CanticoraIcons.TabScale,
            label = "Tamaño de la tablatura",
            value = uiState.tablatureScale,
            onValueChange = { onIntent(TablatureUiIntent.SetTablatureScale(it)) },
            valueRange = TabNotationScale.MIN_ZOOM..TabNotationScale.MAX_ZOOM,
            valueLabel = { "${(it * 100).toInt()}%" }
        )

        // ── Tamaño de las CIFRAS, sin tocar la rejilla ────────────────────────────────────
        // Faltaba en el editor (petición del usuario): el visor ya tenía este control y el editor no,
        // así que en modo edición era imposible agrandar los números para leer de lejos en el atril sin
        // agrandar también las cuerdas y los compases. Es la **misma preferencia global** que usa el
        // visor, así que lo que se ajusta aquí se ve igual al leer el canto.
        SettingsSliderRow(
            icon = CanticoraIcons.TabScale,
            label = "Tamaño de los números",
            value = uiState.tablatureNumberScale,
            onValueChange = { onIntent(TablatureUiIntent.SetTablatureNumberScale(it)) },
            valueRange = TabNotationScale.MIN_ZOOM..TabNotationScale.MAX_ZOOM,
            valueLabel = { "${(it * 100).toInt()}%" }
        )

        // ── Avanzado: instrumento, compás, subdivisión y nota del copista ──────────────────
        AdvancedSection(title = "Más opciones (instrumento, compás…)") {
            SettingsDropdownRow(
                icon = CanticoraIcons.Instrument,
                label = "Instrumento",
                value = uiState.editorTuning.name,
                selectedId = uiState.editorTuning.name,
                options = Tuning.presets.map { SettingsOption(it.name, it.name) },
                onSelect = { id ->
                    Tuning.presets.firstOrNull { it.name == id }
                        ?.let { onIntent(TablatureUiIntent.SetTuning(it)) }
                }
            )
            SettingsDropdownRow(
                icon = CanticoraIcons.TimeSignature,
                label = "Compás",
                value = uiState.editorTime.toString(),
                selectedId = uiState.editorTime.toString(),
                options = TimeSignature.common.map { SettingsOption(it.toString(), it.toString()) },
                onSelect = { id ->
                    TimeSignature.common.firstOrNull { it.toString() == id }
                        ?.let { onIntent(TablatureUiIntent.SetTimeSignature(it.toScoreTimeSignature())) }
                }
            )
            SettingsButtonGroup(
                label = "Subdivisión",
                options = TabResolution.entries.map { SettingsOption(it.name, resolutionLabel(it)) },
                selectedId = uiState.editorResolution.name,
                onSelect = { id ->
                    TabResolution.entries.firstOrNull { it.name == id }
                        ?.let { onIntent(TablatureUiIntent.SetResolution(it)) }
                }
            )
            if (uiState.availableSections.isNotEmpty()) {
                SectionPicker(uiState, onIntent)
            }
            SettingsTextFieldRow(
                icon = CanticoraIcons.Voice,
                label = "Nota",
                value = uiState.editorNote,
                onValueChange = { onIntent(TablatureUiIntent.UpdateEditorNote(it)) },
                placeholder = "Ej. Arreglo de 1974, laúd"
            )
        }
    }
}

/**
 * **Ancho y alto fijos** de cada botón de atajo. Sin ellos el `FlowRow` daba a cada botón el ancho de
 * su texto —`←` quedaba diminuto y `Alt+Mayús+↑` enorme— y la rejilla salía descuadrada.
 */
private val ShortcutCellWidth = 176.dp
private val ShortcutCellHeight = 36.dp

/** Cuerpo del glifo de un atajo: más que el texto porque los símbolos SMuFL tienen menos tinta. */
private val ShortcutGlyphSize = 20.sp

/**
 * Botones equivalentes a todas las teclas que el editor interpreta. Los atajos aceleran la edición,
 * pero las acciones esenciales también deben ser descubribles y utilizables con ratón o tacto.
 *
 * **Cada botón se apaga cuando no puede actuar** (D-68). Antes todos parecían disponibles y la mitad
 * no hacía nada: los que trabajan sobre la nota del cursor (`Q`/`W`, `.`, `Supr`, las articulaciones…)
 * necesitan una cifra en la cuerda activa, y sin ella el caso de uso salía por el `return` sin tocar
 * nada. Un botón que se puede pulsar y no hace nada es peor que un botón apagado.
 *
 * Los rótulos de tecla están **verificados contra [handleTabKeyEvent]**, no inventados: `Alt+Supr`
 * quita la columna (no `Ctrl+Supr`, que borra compases) y «Añadir columna» no tiene tecla —vive en el
 * menú contextual del compás—, así que su botón lo dice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TablatureKeyboardActionsPanel(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit
) {
    val selected = uiState.selectedSlot
    val measureIndex = selected?.first?.coerceIn(0, uiState.editorMeasures.lastIndex.coerceAtLeast(0)) ?: 0
    val pluck = selected?.second
        ?.let { uiState.editorMeasures.getOrNull(measureIndex)?.slots?.getOrNull(it) }
        ?.strings?.firstOrNull { it.stringNumber == uiState.selectedString }
    // Qué puede hacer cada botón **ahora mismo**.
    val hasSlot = selected != null
    val hasNote = pluck != null

    CollapsibleSection(
        title = "Atajos de teclado",
        subtitle = null,
        initiallyExpanded = false
    ) {
        Text(
            text = if (hasNote) {
                "Actuando sobre la nota del compás ${measureIndex + 1}, cuerda " +
                    "${uiState.selectedString} (traste ${pluck?.position?.fret})."
            } else {
                "Pulsa una cifra de la tablatura para seleccionarla: los botones que actúan sobre " +
                    "la nota se activan entonces."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        ShortcutGroup("Mover y editar") {
            ShortcutActionButton("←", "Anterior", "Flecha izquierda: casilla anterior") {
                onIntent(TablatureUiIntent.MoveCursor(0, -1, 0))
            }
            ShortcutActionButton("→", "Siguiente", "Flecha derecha: casilla siguiente") {
                onIntent(TablatureUiIntent.MoveCursor(0, 1, 0))
            }
            // **↑/↓**: cambian según el modo (como MuseScore): en **modo entrada** mueven el cursor
            // entre cuerdas; en **modo normal** suben/bajan el traste de la nota.
            ShortcutActionButton(
                keyLabel = "↑",
                actionLabel = if (uiState.isInputMode) "Cuerda arriba" else "Subir traste",
                accessibilityLabel = if (uiState.isInputMode) {
                    "Flecha arriba: cuerda anterior"
                } else {
                    "Flecha arriba: subir el traste de la nota"
                },
                enabled = uiState.isInputMode || hasNote
            ) {
                onIntent(
                    if (uiState.isInputMode) TablatureUiIntent.MoveCursor(0, 0, -1)
                    else TablatureUiIntent.NudgeFret(-1)
                )
            }
            ShortcutActionButton(
                keyLabel = "↓",
                actionLabel = if (uiState.isInputMode) "Cuerda abajo" else "Bajar traste",
                accessibilityLabel = if (uiState.isInputMode) {
                    "Flecha abajo: cuerda siguiente"
                } else {
                    "Flecha abajo: bajar el traste de la nota"
                },
                enabled = uiState.isInputMode || hasNote
            ) {
                onIntent(
                    if (uiState.isInputMode) TablatureUiIntent.MoveCursor(0, 0, 1)
                    else TablatureUiIntent.NudgeFret(1)
                )
            }
            ShortcutActionButton(
                keyLabel = "Alt+Mayús+↑",
                actionLabel = "Subir traste y cuerda",
                accessibilityLabel = "Alt más Mayús más flecha arriba: subir el traste cambiando de cuerda",
                enabled = hasNote
            ) {
                onIntent(TablatureUiIntent.NudgeFretAcrossStrings(-1))
            }
            ShortcutActionButton(
                keyLabel = "Alt+Mayús+↓",
                actionLabel = "Bajar traste y cuerda",
                accessibilityLabel = "Alt más Mayús más flecha abajo: bajar el traste cambiando de cuerda",
                enabled = hasNote
            ) {
                onIntent(TablatureUiIntent.NudgeFretAcrossStrings(1))
            }
            ShortcutActionButton(
                keyLabel = "Ctrl+↑",
                actionLabel = "Mover cuerda arriba",
                accessibilityLabel = "Ctrl más flecha arriba: mover la nota a la cuerda de arriba"
            ) {
                onIntent(TablatureUiIntent.MoveNoteString(-1))
            }
            ShortcutActionButton(
                keyLabel = "Ctrl+↓",
                actionLabel = "Mover cuerda abajo",
                accessibilityLabel = "Ctrl más flecha abajo: mover la nota a la cuerda de abajo"
            ) {
                onIntent(TablatureUiIntent.MoveNoteString(1))
            }
            ShortcutActionButton(
                keyLabel = "Menú",
                actionLabel = "Añadir columna",
                accessibilityLabel = "Añadir una columna en el cursor (menú del compás)"
            ) {
                onIntent(TablatureUiIntent.InsertColumnAtCursorNow)
            }
            ShortcutActionButton(
                keyLabel = "Alt+Supr",
                actionLabel = "Quitar columna",
                accessibilityLabel = "Alt más Suprimir: quitar la columna del cursor",
                enabled = hasSlot
            ) {
                onIntent(TablatureUiIntent.RemoveColumnAtCursor)
            }
            ShortcutActionButton(
                keyLabel = "N",
                actionLabel = "Modo entrada",
                accessibilityLabel = "Tecla N: modo de entrada de notas",
                selected = uiState.isInputMode
            ) {
                onIntent(TablatureUiIntent.ToggleInputMode)
            }
            ShortcutActionButton(";", "Silencio", "Punto y coma: insertar un silencio") {
                onIntent(TablatureUiIntent.InsertRestAtCursorShortcut)
            }
            ShortcutActionButton(
                keyLabel = "Q",
                actionLabel = "Acortar duración",
                accessibilityLabel = "Tecla Q: acortar la duración de la nota",
                enabled = hasSlot
            ) {
                onIntent(TablatureUiIntent.AdjustFigure(-1))
            }
            ShortcutActionButton(
                keyLabel = "W",
                actionLabel = "Alargar duración",
                accessibilityLabel = "Tecla W: alargar la duración de la nota",
                enabled = hasSlot
            ) {
                onIntent(TablatureUiIntent.AdjustFigure(1))
            }
            ShortcutActionButton(
                keyLabel = ".",
                actionLabel = "Puntillo",
                accessibilityLabel = "Punto: alternar el puntillo de la nota",
                enabled = hasSlot
            ) {
                onIntent(TablatureUiIntent.ToggleDotAtCursor)
            }
            ShortcutActionButton(
                keyLabel = "Mayús+X",
                actionLabel = "Nota fantasma",
                accessibilityLabel = "Mayús más X: alternar la nota fantasma",
                enabled = hasNote
            ) {
                onIntent(TablatureUiIntent.ToggleGhostNoteAtCursor)
            }
            ShortcutActionButton("⌫", "Borrar y volver", "Retroceso: borrar y volver") {
                onIntent(TablatureUiIntent.BackspaceCursor)
            }
            ShortcutActionButton(
                keyLabel = "Supr",
                actionLabel = "Borrar nota",
                accessibilityLabel = "Suprimir: borrar la nota del cursor",
                enabled = hasNote
            ) {
                onIntent(TablatureUiIntent.DeleteAtCursor)
            }
        }

        // ── Articulaciones de MuseScore (`Shift`+`V`/`O`/`S`/`N`) ────────────────────────────────────
        // MuseScore **no tiene atajos de técnica**: las técnicas se aplican desde la **paleta** (arriba).
        // Solo las cuatro articulaciones del manual tienen tecla, y aquí se ofrecen como botones.
        ShortcutGroup("Articulaciones") {
            SHORTCUT_ARTICULATIONS.forEach { (keyLabel, articulation) ->
                ShortcutActionButton(
                    keyLabel = keyLabel,
                    actionLabel = articulation.label,
                    accessibilityLabel = "Atajo $keyLabel: ${articulation.label}",
                    selected = pluck?.articulations?.contains(articulation) == true,
                    enabled = hasNote,
                    glyph = articulation.display
                ) {
                    onIntent(TablatureUiIntent.ApplyArticulationAtCursor(articulation))
                }
            }
        }
    }
}

/** Título de un grupo de atajos y su rejilla de botones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutGroup(title: String, content: @Composable () -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = Density.Snug)
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Density.Tight),
        verticalArrangement = Arrangement.spacedBy(Density.Tight)
    ) {
        content()
    }
}

/**
 * Las **cuatro** articulaciones con atajo en MuseScore Studio 4 (`Shift`+`V`/`O`/`S`/`N`). Se ofrecen
 * como botones para que sean descubribles; **no** incluye técnicas, porque MuseScore no les da tecla.
 *
 * `Shift`+`V` es el **sforzato** (acento **y** picado en un solo glifo), no el acento suelto: así lo
 * dice `shortcuts.json:138` (`toggle-sforzato`) y así lo rotula la barra de entrada por defecto
 * (`noteinputbarmodel.cpp:141-144`: marcato, sforzato, tenuto, staccato — el acento **no** está).
 */
private val SHORTCUT_ARTICULATIONS = listOf(
    "Mayús+V" to TabArticulation.ACCENT_STACCATO,
    "Mayús+O" to TabArticulation.MARCATO,
    "Mayús+S" to TabArticulation.STACCATO,
    "Mayús+N" to TabArticulation.TENUTO
)

/**
 * Indicaciones de compás que ofrece el menú del compás. Cubre las **simples** (2/4…8/4, 2/2, 4/2) y
 * las **compuestas** (6/8, 9/8, 12/8) habituales en el canto litúrgico.
 *
 * `8/4` está a propósito: sin ella no había forma de escribir **ocho negras** en un compás —en 4/4
 * solo caben cuatro— y el usuario acababa con compases sobrepasados. `ScoreTimeSignature` admite
 * cualquier numerador, así que la lista es solo la puerta de entrada, no un límite del modelo.
 */
private val MEASURE_TIME_CHOICES = listOf(
    ScoreTimeSignature("2", 4),
    ScoreTimeSignature("3", 4),
    ScoreTimeSignature("4", 4),
    ScoreTimeSignature("5", 4),
    ScoreTimeSignature("6", 4),
    ScoreTimeSignature("7", 4),
    ScoreTimeSignature("8", 4),
    ScoreTimeSignature("2", 2),
    ScoreTimeSignature("3", 2),
    ScoreTimeSignature("4", 2),
    ScoreTimeSignature("3", 8),
    ScoreTimeSignature("6", 8),
    ScoreTimeSignature("7", 8),
    ScoreTimeSignature("9", 8),
    ScoreTimeSignature("12", 8)
)

/** `true` si [text] es un glifo del área de uso privado de SMuFL (necesita la fuente musical). */
private fun isMusicGlyph(text: String): Boolean =
    text.length == 1 && (text[0].code in 0xE000..0xF8FF || text[0].code in 0x2669..0x266F)

/** Flechas de teclado (`← ↑ → ↓`), que solo tiene la fuente de texto musical empaquetada. */
private const val ARROW_CHARS = "\u2190\u2191\u2192\u2193"

/**
 * (D-91) En la web no hay *fallback* de fuentes: un `←` o un `♯` se pinta como una caja. Estos
 * rótulos se dibujan con la fuente musical de texto, que sí los contiene.
 */
private fun hasArrow(text: String): Boolean = text.any { it in ARROW_CHARS }

/**
 * Un botón de atajo, **siempre del mismo tamaño** para que la rejilla quede alineada.
 *
 * (D-70) Es una **fila** `[tecla] [glifo] [nombre]`, no una celda alta con tres pisos. La versión
 * anterior medía 92 × 80 dp para meter una tecla, un símbolo y un nombre apilados: el usuario lo
 * describió como «botones enormes para un textito y un icono que necesitas microscopio». En fila, el
 * mismo contenido ocupa la mitad de alto, el glifo sube a [ShortcutGlyphSize] y el nombre cabe en una
 * línea sin elipsis.
 *
 * El glifo se pinta con la fuente musical cuando es un codepoint SMuFL: sin ella las articulaciones
 * salían como cajas o como puntos.
 */
@Composable
private fun ShortcutActionButton(
    keyLabel: String,
    actionLabel: String,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    glyph: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .width(ShortcutCellWidth)
            .height(ShortcutCellHeight)
            .semantics { contentDescription = accessibilityLabel },
        shape = RoundedCornerShape(Density.CornerSmall),
        color = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Density.Compact, vertical = Density.Tight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Density.Compact)
        ) {
            // La **tecla**, en un chip: es lo que se busca cuando se viene a esta sección.
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    text = keyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = if (hasArrow(keyLabel)) MusicFont.textFamily() else FontFamily.Default,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = Density.Snug, vertical = 1.dp)
                )
            }
            if (glyph != null) {
                Text(
                    text = glyph,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = ShortcutGlyphSize,
                    fontFamily = if (isMusicGlyph(glyph)) MusicFont.family() else FontFamily.Default,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Barra de acciones del editor (duplicar / eliminar / guardar). */
@Composable
private fun TablatureActionBar(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    onBack: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    // Mismo estilo que el resto de botoneras de la app: superficie con altura tonal, para que el
    // pie del editor no parezca otra pantalla distinta.
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Portapapeles de compases (E3): copiar / cortar / pegar el bloque seleccionado.
            val hasSelection = uiState.selection != null
            IconButton(
                onClick = { onIntent(TablatureUiIntent.CopySelection) },
                enabled = hasSelection
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copiar los compases seleccionados")
            }
            IconButton(
                onClick = { onIntent(TablatureUiIntent.CutSelection) },
                enabled = hasSelection
            ) {
                Icon(Icons.Default.ContentCut, contentDescription = "Cortar los compases seleccionados")
            }
            IconButton(
                onClick = { onIntent(TablatureUiIntent.PasteClipboard) },
                enabled = uiState.hasClipboard
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = "Pegar los compases copiados")
            }
            DuplicateActionButton(
                onClick = { uiState.editingId?.let { onIntent(TablatureUiIntent.DuplicateTablature(it)) } },
                enabled = uiState.editingId != null
            )
            DeleteActionButton(
                onClick = { confirmDelete = true },
                enabled = uiState.editingId != null
            )
            SaveActionButton(
                onClick = { onIntent(TablatureUiIntent.SaveTablature) },
                enabled = uiState.isEditorValid
            )
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Eliminar tablatura",
            message = "¿Seguro que quieres eliminar esta tablatura? Podrás deshacerlo desde el aviso.",
            confirmLabel = "Eliminar",
            onConfirm = {
                confirmDelete = false
                onIntent(TablatureUiIntent.DeleteEditingTablature)
                onBack()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
/** Diálogos de confirmación contextuales del editor (independientes del `Scaffold`). */
@Composable
fun TablaturePendingDialogs(uiState: TablatureUiState, onIntent: (TablatureUiIntent) -> Unit) {
    // Aviso de la regla de oro: redigitar cambia las posiciones escritas a mano.
    if (uiState.pendingRefinger != null) {
        ConfirmDialog(
            title = "Redigitar la tablatura",
            message = "Este modo recalcula las cuerdas y trastes y cambiará las posiciones que " +
                "escribiste. ¿Quieres redigitar según la nueva tonalidad?",
            confirmLabel = "Redigitar",
            onConfirm = { onIntent(TablatureUiIntent.ConfirmRefinger) },
            onDismiss = { onIntent(TablatureUiIntent.CancelRefinger) }
        )
    }
    // Aviso de cambios sin guardar: solo aparece si REALMENTE hay cambios.
    if (uiState.showDiscardDialog) {
        ConfirmDialog(
            title = "Descartar cambios",
            message = "Hay cambios sin guardar en esta tablatura. ¿Quieres salir sin guardar?",
            confirmLabel = "Descartar",
            onConfirm = { onIntent(TablatureUiIntent.ConfirmDiscard) },
            onDismiss = { onIntent(TablatureUiIntent.DismissDiscard) }
        )
    }
}

private fun transpositionModeLabel(mode: TabTranspositionMode): String = when (mode) {
    TabTranspositionMode.CAPO -> "Cejilla"
    TabTranspositionMode.REFINGER -> "Redigitar"
    TabTranspositionMode.LOCKED -> "Bloqueada"
}

private fun transpositionModeHint(mode: TabTranspositionMode): String = when (mode) {
    TabTranspositionMode.CAPO -> "Los trastes no cambian: sube la cejilla con la canción."
    TabTranspositionMode.REFINGER -> "Recalcula cuerdas y trastes para la nueva tonalidad."
    TabTranspositionMode.LOCKED -> "Se muestra en su tonalidad original, sin seguir la canción."
}

/** Etiqueta legible de una subdivisión de la rejilla de tablatura. */
private fun resolutionLabel(resolution: TabResolution): String = when (resolution) {
    TabResolution.QUARTER -> "Negras"
    TabResolution.EIGHTH -> "Corcheas"
    TabResolution.SIXTEENTH -> "Semicorcheas"
}

/**
 * Selector de la **tonalidad escrita** de la tablatura. Se usa para que la tablatura **siga el
 * cambio de tonalidad** del canto (redigitado/cejilla según su modo); si se deja «Sin tonalidad»,
 * la tablatura no sigue el tono (se mantiene en su digitación original).
 */
@Composable
private fun WrittenKeyPicker(uiState: TablatureUiState, onIntent: (TablatureUiIntent) -> Unit) {
    val currentLabel = uiState.editorWrittenKey?.tonicEnglishName() ?: "Sin tonalidad"
    Column(verticalArrangement = Arrangement.spacedBy(Density.Tight)) {
        SettingsDropdownRow(
            icon = CanticoraIcons.Key,
            label = "Tonalidad escrita",
            value = currentLabel,
            options = listOf(SettingsOption(NO_WRITTEN_KEY, "Sin tonalidad")) +
                TAB_KEYS.map { SettingsOption(it, it) },
            selectedId = uiState.editorWrittenKey?.tonicEnglishName() ?: NO_WRITTEN_KEY,
            onSelect = { id ->
                onIntent(
                    TablatureUiIntent.SetEditorWrittenKey(
                        if (id == NO_WRITTEN_KEY) null else scoreKeyOfName(id)
                    )
                )
            }
        )
        Text(
            "Si coincide con el tono del canto, la tablatura se transpondrá con él.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Density.CardPadding)
        )
    }
}

/** Id reservado de la opción «Sin tonalidad escrita» del desplegable. */
private const val NO_WRITTEN_KEY = "__none__"

/**
 * **Paletas de marcas** del editor de tablatura, al estilo de las *paletas* de MuseScore: una
 * colección de **secciones plegables**, cada una con los botones de un grupo (alteraciones,
 * dinámicas, reguladores, adornos, articulaciones, ligaduras, técnicas, trémolos, digitación,
 * repeticiones y saltos, barras de compás, tempo y texto).
 *
 * Las marcas de **compás** (repetición, volta, navegación, salto, dinámica, tempo, texto) actúan
 * sobre el compás seleccionado; las de **nota** (técnicas, articulaciones, alteración, dedos) sobre la
 * casilla y cuerda activas. La casilla y el compás a los que se aplica se muestran en la cabecera, así
 * que siempre se sabe sobre qué se está escribiendo.
 *
 * (D-58) Se pinta **dentro del panel de herramientas** de la derecha, encima de los ajustes: no hay
 * un panel de paletas aparte. Por eso **no** lleva scroll propio —el panel ya scrollea— y por eso el
 * arrastre se comparte por [TablaturePaletteDrag] en vez de por un `remember` local.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TablatureMarksPalette(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    modifier: Modifier = Modifier,
    dragState: PaletteDragState? = null,
    onDrop: (() -> Unit)? = null
) {
    if (uiState.editorMeasures.isEmpty()) return
    // (D-95) Ayuda de las paletas, junto al interruptor «?/Nombres».
    var showPaletteHelp by remember { mutableStateOf(false) }
    val selected = uiState.selectedSlot
    val measureIndex = (selected?.first ?: 0).coerceIn(0, uiState.editorMeasures.lastIndex)
    val measure = uiState.editorMeasures.getOrNull(measureIndex) ?: return
    val slotIndex = selected?.second
    val activeSlot = slotIndex?.let(measure.slots::getOrNull)
    // Técnica que tiene ahora la cuerda activa en la casilla elegida (la última pulsación de esa
    // cuerda). Con eso se resalta el botón del símbolo activo.
    // **Caída a la primera nota de la casilla** (D-58): la cuerda elegida en la barra de escritura
    // puede no tener nota en esta casilla aunque la casilla tenga notas. Antes eso dejaba **toda** la
    // paleta deshabilitada con notas a la vista, y el usuario concluía —con razón— que los botones no
    // hacían nada. Ahora se actúa sobre la primera nota de la casilla.
    val activePluck = slotIndex?.let { index ->
        val slot = measure.slots.getOrNull(index)
        slot?.strings?.lastOrNull { it.stringNumber == uiState.selectedString }
            ?: slot?.strings?.firstOrNull()
    }
    val stringNumber = activePluck?.stringNumber ?: uiState.selectedString
    val hasNote = activePluck != null
    val canEditTechnique = slotIndex != null && hasNote

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Density.Tight)
    ) {
        // Cabecera: sobre qué compás y cuerda actúan las paletas de abajo. Con **anacrusa** el primer
        // compás es el `0` y no se numera, así que se nombra por lo que es: decir «Compás 1» cuando
        // el compás 1 es el siguiente desorienta al que cuenta.
        val measureLabel = if (measure.isPickup) "Anacrusa" else "Compás ${measureIndex + 1}"
        // Con **compás irregular** la duración real es una decisión del usuario, así que se muestra:
        // es la única forma de saber cuánto dura sin contar las figuras a ojo.
        val irregularLabel = if (measure.isIrregular) {
            val beats = TabMeasureMeter.durationOf(measure, uiState.editorResolution)
            " · irregular (${formatBeats(beats)})"
        } else {
            ""
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Density.CardPadding, end = Density.Snug),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Density.Tight)
        ) {
            Text(
                text = measureLabel + irregularLabel +
                    if (slotIndex == null) " · sin casilla" else " · cuerda $stringNumber",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            // (D-62) Ayuda contextual: enciende el **nombre de cada símbolo** debajo de su celda.
            // Es la única forma de que un glifo SMuFL se explique en un móvil, donde no hay hover.
            val showLabels = TablaturePaletteLabels.show
            Surface(
                onClick = { TablaturePaletteLabels.show = !showLabels },
                shape = RoundedCornerShape(Density.CornerSmall),
                color = if (showLabels) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                },
                border = BorderStroke(
                    1.dp,
                    if (showLabels) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                contentColor = if (showLabels) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.semantics {
                    contentDescription = if (showLabels) {
                        "Ocultar los nombres de los símbolos de las paletas"
                    } else {
                        "Mostrar el nombre de cada símbolo de las paletas"
                    }
                }
            ) {
                Text(
                    text = if (showLabels) "Nombres" else "?",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
                )
            }
            // (D-95) Ayuda de las paletas: el botón «?» solo enciende los nombres; este otro explica
            // qué hace cada familia de símbolos y cómo se combinan sobre la misma nota.
            GridIconButton(
                icon = Icons.Default.HelpOutline,
                description = "Ayuda de las paletas de la tablatura"
            ) { showPaletteHelp = true }
        }
        if (showPaletteHelp) {
            TablatureEditorHelpDialog(uiState = uiState, onDismiss = { showPaletteHelp = false })
        }
        // (D-58) Si la casilla no tiene **ninguna** nota, las paletas no tienen a qué aplicarse y sus
        // botones salen deshabilitados. Sin decirlo, parece que la paleta está rota: se explica qué
        // falta y cómo resolverlo.
        if (!canEditTechnique) {
            Text(
                text = if (slotIndex == null) {
                    "Elige una casilla de la tablatura para aplicar las paletas."
                } else {
                    "Esta casilla no tiene notas: escribe una nota para poder aplicarle marcas."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Density.CardPadding, end = Density.CardPadding)
            )
        }

        // ── La paleta «Duración» **ya no vive aquí** ─────────────────────────────────────────────
        // (2026-10-15) Las figuras, el puntillo, el tresillo y los silencios se han mudado a la
        // **barra de escritura** ([TablatureWritingBar]), pegada a la partitura y siempre visible.
        // Aquí, en el panel de herramientas, obligaban a ir y volver —y en móvil el panel es un
        // `ModalBottomSheet` que **tapaba** la partitura—: era la causa de que insertar notas
        // fuera «caótico». Este panel se queda con lo que **no** es escribir: las marcas del
        // compás, la tonalidad, el tamaño y los metadatos.

        // ── Paleta «Alteraciones»: **no se ofrece** (D-83) ────────────────────────────────────────
        // En una tablatura numérica la **nota ya está determinada** por el traste y la afinación, así
        // que un `♯`/`♭` delante de la cifra es redundante: no hay dos enarmónicos posibles en el
        // mismo traste. MuseScore la tiene porque su paleta es la **compartida con el pentagrama**:
        // cuando la tablatura va emparejada con notación estándar, la alteración se escribe en el
        // pentagrama y se refleja en las dos. Canticora solo tiene tablatura, así que aquí era ruido.
        //
        // El **modelo** conserva `TabPluck.accidental`, el dibujado y la serialización: una tablatura
        // importada de MuseScore con alteraciones las sigue mostrando tal cual, y el PDF también. Lo
        // único que desaparece es el mando para ponerlas a mano.

        // ── Paleta «Dinámicas» (matices, acentos y reguladores del compás) ─────────────────────────
        Palette(title = "Dinámicas") {
            PaletteFlow {
                PaletteChip(
                    glyph = "—",
                    label = "Sin dinámica",
                    selected = measure.dynamic == null,
                    dragIntent = { hit -> TablatureUiIntent.SetDynamic(hit.measureIndex, null) },
                    dragState = dragState,
                    onDrop = onDrop,
                    onClick = { onIntent(TablatureUiIntent.SetDynamic(measureIndex, null)) }
                )
                TabDynamic.entries.forEach { dynamic ->
                    PaletteChip(
                        glyph = dynamic.glyph,
                        label = "Dinámica ${dynamic.glyph}",
                        selected = measure.dynamic == dynamic,
                        // Las dinámicas van en **cursiva negrita**, como en MuseScore.
                        italic = true,
                        dragIntent = { hit -> TablatureUiIntent.SetDynamic(hit.measureIndex, dynamic) },
                        dragState = dragState,
                        onDrop = onDrop,
                        onClick = { onIntent(TablatureUiIntent.SetDynamic(measureIndex, dynamic)) }
                    )
                }
            }
        }

        // ── Paleta «Reguladores» (cuñas de crescendo y diminuendo) ─────────────────────────────────
        // En MuseScore el regulador **no** es el texto `cresc.`/`dim.` de la paleta de dinámicas: es
        // una **cuña** (`<HairPin>`) que abarca varios compases. Se ofrece aparte, con su alcance.
        Palette(title = "Reguladores") {
            PaletteFlow {
                PaletteChip(
                    glyph = "\u2014",
                    label = "Sin regulador",
                    selected = measure.hairpin == null,
                    dragIntent = { hit -> TablatureUiIntent.SetHairpin(hit.measureIndex, null) },
                    dragState = dragState,
                    onDrop = onDrop,
                    onClick = { onIntent(TablatureUiIntent.SetHairpin(measureIndex, null)) }
                )
                TabHairpinType.entries.forEach { type ->
                    val length = measure.hairpin?.measures ?: 1
                    PaletteChip(
                        // La cuña se insinúa con el signo de desigualdad: `>` abre hacia la derecha
                        // (crescendo) y `<` cierra (diminuendo), igual que en la paleta de MuseScore.
                        glyph = if (type == TabHairpinType.CRESCENDO) "<" else ">",
                        label = type.label,
                        selected = measure.hairpin?.type == type,
                        dragIntent = { hit ->
                            TablatureUiIntent.SetHairpin(hit.measureIndex, TabHairpin(type, length))
                        },
                        dragState = dragState,
                        onDrop = onDrop,
                        onClick = {
                            onIntent(TablatureUiIntent.SetHairpin(measureIndex, TabHairpin(type, length)))
                        }
                    )
                }
            }
            // **Alcance** de la cuña en compases: sin esto el regulador duraría un solo compás, que
            // no sirve para nada musicalmente.
            if (measure.hairpin != null) {
                PaletteFlow {
                    listOf(1, 2, 4, 8).forEach { length ->
                        PaletteChip(
                            glyph = length.toString(),
                            label = "Abarca $length compás(es)",
                            selected = measure.hairpin?.measures == length,
                            onClick = {
                                onIntent(
                                    TablatureUiIntent.SetHairpin(
                                        measureIndex,
                                        TabHairpin(measure.hairpin!!.type, length)
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }

        // ── Paleta «Adornos» (notas de adorno, *grace notes*) ──────────────────────────────────────
        // En MuseScore la tecla `/` cuelga un acorde **pequeño** (0,7 del tamaño) delante del
        // principal. En tablatura se ve como una cifra pequeña a la izquierda, sin barra de
        // acciaccatura (la tablatura es *stemless* y MuseScore no dibuja el `StemSlash` sin asta).
        Palette(title = "Adornos") {
            PaletteFlow {
                PaletteChip(
                    glyph = "\u266F",
                    label = "Acciaccatura",
                    selected = activeSlot?.graces?.any { it.type == TabGraceType.ACCIACCATURA } == true,
                    dragIntent = { hit ->
                        TablatureUiIntent.AddGrace(
                            hit.measureIndex,
                            hit.slotIndex,
                            TabGraceType.ACCIACCATURA,
                            uiState.selectedString
                        )
                    },
                    dragState = dragState,
                    onDrop = onDrop,
                    onClick = {
                        val slot = uiState.selectedSlot
                        if (slot != null) {
                            onIntent(
                                TablatureUiIntent.AddGrace(
                                    slot.first,
                                    slot.second,
                                    TabGraceType.ACCIACCATURA,
                                    uiState.selectedString
                                )
                            )
                        }
                    }
                )
                PaletteChip(
                    glyph = "\u266E",
                    label = "Apoyatura",
                    selected = activeSlot?.graces?.any { it.type == TabGraceType.APPOGGIATURA } == true,
                    dragIntent = { hit ->
                        TablatureUiIntent.AddGrace(
                            hit.measureIndex,
                            hit.slotIndex,
                            TabGraceType.APPOGGIATURA,
                            uiState.selectedString
                        )
                    },
                    dragState = dragState,
                    onDrop = onDrop,
                    onClick = {
                        val slot = uiState.selectedSlot
                        if (slot != null) {
                            onIntent(
                                TablatureUiIntent.AddGrace(
                                    slot.first,
                                    slot.second,
                                    TabGraceType.APPOGGIATURA,
                                    uiState.selectedString
                                )
                            )
                        }
                    }
                )
                PaletteChip(
                    // (D-77) `⌫` (U+232B) no existe en la fuente del sistema y la celda salía vacía.
                    glyph = "\u00D7",
                    label = "Quitar adornos",
                    selected = false,
                    enabled = activeSlot?.hasGraces == true,
                    onClick = {
                        val slot = uiState.selectedSlot
                        if (slot != null) {
                            onIntent(TablatureUiIntent.ClearGraces(slot.first, slot.second))
                        }
                    }
                )
            }
        }

        // ── Paleta «Articulaciones» (se apilan en vertical sobre la cifra) ──────────────────────────
        // Solo las que **se apilan** (el **armónico** se elige en «Técnicas»: va **en** la cifra, no
        // en la pila) y **deshabilitadas** las que chocan con lo que ya lleva la nota, igual que
        // MuseScore: no se ofrece lo imposible. Un arco arriba y otro abajo no se pueden marcar juntos.
        Palette(title = "Articulaciones") {
            PaletteFlow {
                TabArticulation.entries.filter { it.stacks }.forEach { articulation ->
                    // (D-40) La marca del chip es por **cualidad**: una nota con sforzato `𝄰` ya lleva
                    // acento y picado, así que los dos chips salen marcados aunque el glifo guardado
                    // sea el combinado.
                    val present = TabSymbolCompatibility.isArticulationPresent(
                        activePluck?.articulations ?: emptySet(),
                        articulation
                    )
                    val allowed = !present && TabSymbolCompatibility.isArticulationAllowed(
                        activePluck?.articulations ?: emptySet(),
                        articulation
                    )
                    PaletteChip(
                        glyph = articulation.display,
                        label = articulation.label,
                        selected = canEditTechnique && present,
                        enabled = canEditTechnique && (present || allowed),
                        dragIntent = { hit ->
                            if (hit.stringNumber <= 0) {
                                null
                            } else {
                                TablatureUiIntent.ToggleArticulation(
                                    hit.measureIndex, hit.slotIndex, hit.stringNumber, articulation
                                )
                            }
                        },
                        dragState = dragState,
                        onDrop = onDrop,
                        onClick = {
                            if (slotIndex != null) {
                                onIntent(
                                    TablatureUiIntent.ToggleArticulation(
                                        measureIndex, slotIndex, stringNumber, articulation
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }

        // ── Paleta «Ligaduras» (B-10) ─────────────────────────────────────────────────────────────
        // El `tie` de MuseScore: une la nota con la **siguiente de la misma cuerda** para que suenen
        // como una sola. Se distingue del *slur* (hammer-on/pull-off), que va en «Técnicas».
        Palette(title = "Ligaduras", subtitle = "de la nota") {
            PaletteRow {
                PaletteChip(
                    glyph = "\u2040",
                    label = "Ligadura de prolongación (T)",
                    selected = canEditTechnique && activePluck?.tiedToNext == true,
                    enabled = canEditTechnique,
                    dragIntent = { hit ->
                        if (hit.stringNumber <= 0) {
                            null
                        } else {
                            TablatureUiIntent.ToggleTie(hit.measureIndex, hit.slotIndex, hit.stringNumber)
                        }
                    },
                    dragState = dragState,
                    onDrop = onDrop,
                    onClick = {
                        if (slotIndex != null) {
                            onIntent(TablatureUiIntent.ToggleTie(measureIndex, slotIndex, stringNumber))
                        }
                    }
                )
            }
        }

        // ── Paleta «Técnicas» (por familias, como el catálogo de MuseScore) ────────────────────────
        Palette(title = "Técnicas", subtitle = "de la nota") {
            TabTechniqueFamily.entries.forEach { family ->
                Text(
                    text = family.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PaletteFlow {
                    PaletteChip(
                        glyph = "—",
                        label = "Sin técnica",
                        selected = canEditTechnique && activePluck?.techniques?.isEmpty() == true,
                        enabled = canEditTechnique,
                        dragIntent = { hit ->
                            if (hit.stringNumber <= 0) {
                                null
                            } else {
                                TablatureUiIntent.SetTechnique(
                                    hit.measureIndex, hit.slotIndex, hit.stringNumber, TabTechnique.NONE
                                )
                            }
                        },
                        dragState = dragState,
                        onDrop = onDrop,
                        onClick = {
                            if (slotIndex != null) {
                                onIntent(
                                    TablatureUiIntent.SetTechnique(
                                        measureIndex, slotIndex, stringNumber, TabTechnique.NONE
                                    )
                                )
                            }
                        }
                    )
                    family.techniques.forEach { technique ->
                        TechniquePaletteChip(
                            technique = technique,
                            selected = canEditTechnique && activePluck?.has(technique) == true,
                            enabled = canEditTechnique,
                            dragIntent = { hit ->
                                if (hit.stringNumber <= 0) {
                                    null
                                } else {
                                    TablatureUiIntent.SetTechnique(
                                        hit.measureIndex, hit.slotIndex, hit.stringNumber, technique
                                    )
                                }
                            },
                            dragState = dragState,
                            onDrop = onDrop,
                            onClick = {
                                if (slotIndex != null) {
                                    onIntent(
                                        TablatureUiIntent.SetTechnique(
                                            measureIndex, slotIndex, stringNumber, technique
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // ── Paleta «Trémolos» (número de trazos) ──────────────────────────────────────────────────
        // MuseScore tiene una paleta propia con `tremolo1`…`tremolo4`: un trazo es una **corchea** de
        // repetición, dos una **semicorchea**, tres una **fusa** y cuatro una **semifusa**. Aquí el
        // trémolo se activa desde «Técnicas» y esta paleta decide **cuántos trazos** dibuja (D-28).
        Palette(title = "Trémolos", subtitle = "trazos de la nota") {
            PaletteFlow {
                (1..TabPluck.MAX_TREMOLO_STROKES).forEach { strokes ->
                    PaletteChip(
                        // El glifo del trémolo son barras oblicuas: se insinúan con `/` repetido, que
                        // es lo que se ve sobre la cifra.
                        glyph = "/".repeat(strokes),
                        label = tremoloStrokeLabel(strokes),
                        selected = canEditTechnique && activePluck?.strokes == strokes,
                        enabled = canEditTechnique,
                        dragIntent = { hit ->
                            if (hit.stringNumber <= 0) {
                                null
                            } else {
                                TablatureUiIntent.SetTremoloStrokes(
                                    hit.measureIndex, hit.slotIndex, hit.stringNumber, strokes
                                )
                            }
                        },
                        dragState = dragState,
                        onDrop = onDrop,
                        onClick = {
                            if (slotIndex != null) {
                                onIntent(
                                    TablatureUiIntent.SetTremoloStrokes(
                                        measureIndex, slotIndex, stringNumber, strokes
                                    )
                                )
                            }
                        }
                    )
                }
            }
            if (canEditTechnique && activePluck?.has(TabTechnique.TREMOLO) != true) {
                Text(
                    text = "El trémolo se enciende en «Técnicas»; aquí solo se eligen los trazos.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Paleta «Digitación» (dedos izquierda/derecha) + magnitudes de técnica ──────────────────
        Palette(title = "Digitación", subtitle = "de la nota") {
            if (activePluck?.hasBend == true) {
                Text(
                    text = "Cantidad del estiramiento",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PaletteRow {
                    PaletteChip(
                        glyph = "—",
                        label = "Estiramiento sin indicar",
                        selected = activePluck.bendAmount == null,
                        onClick = {
                            onIntent(TablatureUiIntent.SetBendAmount(measureIndex, slotIndex, stringNumber, null))
                        }
                    )
                    TabBendAmount.entries.forEach { amount ->
                        PaletteChip(
                            glyph = amount.label,
                            label = "Estiramiento de ${amount.label} de tono",
                            selected = activePluck.bendAmount == amount,
                            onClick = {
                                onIntent(
                                    TablatureUiIntent.SetBendAmount(measureIndex, slotIndex, stringNumber, amount)
                                )
                            }
                        )
                    }
                }
            }
            if (activePluck?.has(TabTechnique.PALM_MUTE) == true) {
                Text(
                    text = "Alcance del apagado de palma",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PaletteRow {
                    PaletteChip(
                        glyph = "—",
                        label = "Solo esta columna",
                        selected = activePluck.palmMuteSpan == null,
                        onClick = {
                            onIntent(TablatureUiIntent.SetPalmMuteSpan(measureIndex, slotIndex, stringNumber, null))
                        }
                    )
                    (2..MAX_PALM_MUTE_SPAN).forEach { span ->
                        PaletteChip(
                            glyph = "$span",
                            label = "Apagado durante $span columnas",
                            selected = activePluck.palmMuteSpan == span,
                            onClick = {
                                onIntent(
                                    TablatureUiIntent.SetPalmMuteSpan(measureIndex, slotIndex, stringNumber, span)
                                )
                            }
                        )
                    }
                }
            }
            Text(
                text = "Dedos de la mano izquierda",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PaletteRow {
                PaletteChip(
                    glyph = "—",
                    label = "Sin dedo indicado",
                    selected = canEditTechnique && activePluck?.frettingFinger == null,
                    enabled = canEditTechnique,
                    onClick = {
                        if (slotIndex != null) {
                            onIntent(TablatureUiIntent.SetFrettingFinger(measureIndex, slotIndex, stringNumber, null))
                        }
                    }
                )
                com.grafitto.laudate.domain.model.tablature.TabFrettingFinger.entries.forEach { finger ->
                    PaletteChip(
                        glyph = finger.number.toString(),
                        label = "Dedo ${finger.number}",
                        selected = activePluck?.frettingFinger == finger,
                        enabled = canEditTechnique,
                        onClick = {
                            if (slotIndex != null) {
                                onIntent(
                                    TablatureUiIntent.SetFrettingFinger(
                                        measureIndex, slotIndex, stringNumber, finger
                                    )
                                )
                            }
                        }
                    )
                }
            }
            Text(
                text = "Dedos de la mano derecha",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PaletteRow {
                PaletteChip(
                    glyph = "—",
                    label = "Sin púa/dedo indicado",
                    selected = canEditTechnique && activePluck?.pickingFinger == null,
                    enabled = canEditTechnique,
                    onClick = {
                        if (slotIndex != null) {
                            onIntent(TablatureUiIntent.SetPickingFinger(measureIndex, slotIndex, stringNumber, null))
                        }
                    }
                )
                com.grafitto.laudate.domain.model.tablature.TabPickingFinger.entries.forEach { finger ->
                    PaletteChip(
                        glyph = finger.label,
                        label = "Dedo $finger",
                        selected = activePluck?.pickingFinger == finger,
                        enabled = canEditTechnique,
                        onClick = {
                            if (slotIndex != null) {
                                onIntent(
                                    TablatureUiIntent.SetPickingFinger(
                                        measureIndex, slotIndex, stringNumber, finger
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }

        // ── Paleta «Repeticiones y saltos» (repetición, volta, navegación y saltos del compás) ─────
        Palette(title = "Repeticiones y saltos") {
            Text("Repetición", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PaletteRow {
                PaletteChip("—", "Sin repetición", measure.repeat == null) {
                    onIntent(TablatureUiIntent.SetRepeatMark(measureIndex, null))
                }
                REPEAT_MARKS.forEach { mark ->
                    PaletteChip(repeatGlyph(mark), repeatMarkLabel(mark), measure.repeat == mark) {
                        onIntent(TablatureUiIntent.SetRepeatMark(measureIndex, mark))
                    }
                }
            }
            if (measure.repeat == com.grafitto.laudate.domain.model.tablature.TabRepeatMark.END ||
                measure.repeat == com.grafitto.laudate.domain.model.tablature.TabRepeatMark.BOTH
            ) {
                Text("Número de ejecuciones", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PaletteFlow {
                    (2..8).forEach { count ->
                        PaletteChip("×$count", "Repetir $count veces", measure.repeatCount == count) {
                            onIntent(TablatureUiIntent.SetRepeatCount(measureIndex, count))
                        }
                    }
                }
            }
            Text("Volta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PaletteRow {
                PaletteChip("—", "Sin volta", measure.ending == null) {
                    onIntent(TablatureUiIntent.SetEnding(measureIndex, null))
                }
                ENDINGS.forEach { ending ->
                    PaletteChip(
                        "${ending}ª",
                        "Volta: ${ending}ª vez",
                        measure.ending == ending && measure.endingTo == null
                    ) {
                        onIntent(TablatureUiIntent.SetEnding(measureIndex, ending))
                    }
                }
            }
            // (D-37) **Casilla de varias pasadas**: en MuseScore una volta puede sonar en más de una
            // pasada (`1, 2.`), y el rótulo del corchete lo refleja. Solo se ofrece cuando el compás
            // ya tiene volta, porque la casilla necesita un punto de partida.
            measure.ending?.let { from ->
                Text(
                    "Pasadas de la casilla",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PaletteFlow {
                    ENDINGS.filter { it >= from }.forEach { to ->
                        val label = if (to == from) "$from." else "$from, $to."
                        PaletteChip(
                            label,
                            if (to == from) {
                                "Casilla de la ${from}ª vez"
                            } else {
                                "Casilla de la ${from}ª a la ${to}ª vez"
                            },
                            measure.endingTo == to || (to == from && measure.endingTo == null)
                        ) {
                            onIntent(TablatureUiIntent.SetEndingRange(measureIndex, from, to))
                        }
                    }
                }
                // (D-43) **Rótulo propio** de la volta: en MuseScore el texto del corchete es el
                // `beginText` de un `TextLineBase` y se escribe libremente («1ª vez», «Todos», «Al
                // final»). No toca las pasadas, que siguen mandando en la reproducción.
                var endingDraft by remember(measureIndex, measure.endingText) {
                    mutableStateOf(measure.endingText.orEmpty())
                }
                Text(
                    "Rótulo de la casilla",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                PaletteTextEntry(
                    value = endingDraft,
                    onValueChange = { endingDraft = it },
                    label = "Texto sobre el corchete",
                    applyLabel = "Fijar",
                    applyDescription = "Fijar el rótulo de la casilla",
                    applied = measure.endingText == endingDraft.trim().ifEmpty { null },
                    onApply = {
                        onIntent(TablatureUiIntent.SetEndingText(measureIndex, endingDraft))
                    },
                    clearDescription = "Volver al rótulo de las pasadas",
                    cleared = measure.endingText == null,
                    onClear = {
                        endingDraft = ""
                        onIntent(TablatureUiIntent.SetEndingText(measureIndex, null))
                    }
                )
            }
            Text("Navegación", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PaletteFlow {
                PaletteChip("—", "Sin signo de navegación", measure.navigation == null) {
                    onIntent(TablatureUiIntent.SetNavigation(measureIndex, null))
                }
                NAVIGATION_MARKS.forEach { navigation ->
                    PaletteChip(navigation.display, navigation.label, measure.navigation == navigation) {
                        onIntent(TablatureUiIntent.SetNavigation(measureIndex, navigation))
                    }
                }
            }
            Text("Saltos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PaletteFlow {
                PaletteChip("—", "Sin salto", measure.jump == null) {
                    onIntent(TablatureUiIntent.SetJump(measureIndex, null))
                }
                TabJump.entries.forEach { jump ->
                    PaletteChip(jump.label, "Salto: ${jump.label}", measure.jump == jump) {
                        onIntent(TablatureUiIntent.SetJump(measureIndex, jump))
                    }
                }
            }
        }

        // ── Paleta «Barras de compás» ───────────────────────────────────────────────────────────────
        // (D-72) La paleta de MuseScore (*Barlines*) ofrece **todas** las barras en un solo sitio: la
        // simple, la doble, la final y las de **repetición** `|:` `:|` `:||:`. Aquí faltaban las de
        // repetición —vivían solo en «Repeticiones y saltos»—, así que el usuario que iba a buscar
        // una barra de compás no las encontraba: «me faltan barras de compás».
        Palette(title = "Barras de compás") {
            PaletteRow {
                com.grafitto.laudate.domain.model.tablature.TabBarline.entries.forEach { barline ->
                    PaletteChip(barlineGlyph(barline), barlineLabel(barline), measure.barline == barline) {
                        onIntent(TablatureUiIntent.SetBarline(measureIndex, barline))
                    }
                }
            }
            PaletteRow {
                PaletteChip("—", "Sin repetición", measure.repeat == null) {
                    onIntent(TablatureUiIntent.SetRepeatMark(measureIndex, null))
                }
                REPEAT_MARKS.forEach { mark ->
                    PaletteChip(repeatGlyph(mark), repeatMarkLabel(mark), measure.repeat == mark) {
                        onIntent(TablatureUiIntent.SetRepeatMark(measureIndex, mark))
                    }
                }
            }
        }

        // ── Paleta «Indicaciones de compás» ─────────────────────────────────────────────────────────
        // (D-72) En MuseScore la indicación de compás se **inserta desde la paleta** (*Time
        // signatures*) y queda anclada al compás, rigiendo hasta el siguiente cambio. Aquí solo se
        // podía cambiar desde el menú contextual del compás, así que no había paleta: «me faltan
        // indicaciones de compás». Se ofrece la lista completa de [MEASURE_TIME_CHOICES] más la
        // opción de **volver a la del documento**.
        Palette(title = "Indicaciones de compás") {
            PaletteFlow {
                PaletteChip(
                    glyph = "—",
                    label = "Volver a la indicación del documento",
                    selected = measure.time == null
                ) {
                    onIntent(TablatureUiIntent.SetMeasureTime(measureIndex, null))
                }
                MEASURE_TIME_CHOICES.forEach { time ->
                    val label = "${time.beats}/${time.beatType}"
                    PaletteChip(
                        glyph = label,
                        label = "Compás de $label",
                        selected = measure.time == time
                    ) {
                        onIntent(TablatureUiIntent.SetMeasureTime(measureIndex, time))
                    }
                }
            }
        }

        // ── Paleta «Altura» **retirada** (D-58) ──────────────────────────────────────────────────────
        // El 8va/15ma/8vb no cambia **ninguna** cifra de la tablatura: solo añade un rótulo sobre el
        // pentagrama. En una partitura mueve las notas de sitio; aquí no hay notas que mover, así que
        // el usuario pulsaba y no veía nada. Se queda fuera de la paleta (el modelo y el pintor
        // siguen soportándolo para no romper la serialización de tablaturas ya guardadas).

        // ── Paleta «Tempo» (BPM del compás con su indicación italiana) ──────────────────────────────
        // (D-61) El tempo **se escribe**, no solo se elige de tres presets. Antes la paleta ofrecía
        // 60/90/120 y nada más: quien necesitaba `♩ = 72` (el *Andante* de un salmo) no podía, y el
        // valor que traía la tablatura importada tampoco se podía corregir. Ahora hay un **paso**
        // `−`/`+` de 5 BPM —el mismo control que el traste de la nota, sin teclado ni validación— y
        // los presets quedan como atajos.
        Palette(title = "Tempo") {
            val currentBpm = measure.tempo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Density.Tight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Density.Tight)
                ) {
                    // El carácter `♩` no existe en la fuente de la web: se dibuja con Bravura.
                    MusicGlyph("\u2669", 12.dp)
                    Text(text = "=", style = MaterialTheme.typography.bodyMedium)
                }
                FretStepButton(
                    glyph = "\u2212",
                    description = "Bajar el tempo 5 pulsaciones por minuto",
                    enabled = currentBpm != null && currentBpm > MIN_TEMPO_BPM
                ) {
                    onIntent(
                        TablatureUiIntent.SetTempo(
                            measureIndex,
                            ((currentBpm ?: DEFAULT_TEMPO_BPM) - TEMPO_STEP_BPM)
                                .coerceAtLeast(MIN_TEMPO_BPM)
                        )
                    )
                }
                Text(
                    text = currentBpm?.toString() ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.widthIn(min = 40.dp),
                    textAlign = TextAlign.Center
                )
                FretStepButton(
                    glyph = "+",
                    description = "Subir el tempo 5 pulsaciones por minuto",
                    enabled = (currentBpm ?: DEFAULT_TEMPO_BPM) < MAX_TEMPO_BPM
                ) {
                    onIntent(
                        TablatureUiIntent.SetTempo(
                            measureIndex,
                            ((currentBpm ?: DEFAULT_TEMPO_BPM) + TEMPO_STEP_BPM)
                                .coerceAtMost(MAX_TEMPO_BPM)
                        )
                    )
                }
            }
            PaletteRow {
                PaletteChip("—", "Sin indicación de tempo", measure.tempo == null) {
                    onIntent(TablatureUiIntent.SetTempo(measureIndex, null))
                }
                TEMPO_PRESETS.forEach { bpm ->
                    PaletteChip("$bpm", "Tempo $bpm (pulsaciones por minuto)", measure.tempo == bpm) {
                        onIntent(TablatureUiIntent.SetTempo(measureIndex, bpm))
                    }
                }
            }
        }

        // ── Paleta «Texto» (indicación libre del compás) ────────────────────────────────────────────
        Palette(title = "Texto") {
            var textDraft by remember(measureIndex) { mutableStateOf(measure.text.orEmpty()) }
            PaletteTextEntry(
                value = textDraft,
                onValueChange = { textDraft = it },
                label = "Indicación del compás",
                applyLabel = "Fijar",
                applyDescription = "Fijar el texto del compás",
                applied = measure.text == textDraft.trim().ifEmpty { null },
                onApply = { onIntent(TablatureUiIntent.SetMeasureText(measureIndex, textDraft)) },
                clearDescription = "Quitar el texto del compás",
                cleared = measure.text == null,
                onClear = {
                    textDraft = ""
                    onIntent(TablatureUiIntent.SetMeasureText(measureIndex, null))
                }
            )
        }
    }
}

/**
 * Nombre en español del **número de trazos** de un trémolo (D-28), con la figura de repetición que
 * representa cada uno: un trazo es una corchea, dos una semicorchea, tres una fusa y cuatro una
 * semifusa. Es el vocabulario de la paleta «Trémolos» de MuseScore.
 */
private fun tremoloStrokeLabel(strokes: Int): String = when (strokes) {
    1 -> "1 trazo (corchea)"
    2 -> "2 trazos (semicorchea)"
    3 -> "3 trazos (fusa)"
    else -> "4 trazos (semifusa)"
}

/**
 * **Campo de texto de una paleta** con sus dos acciones, en una fila que no se descuadra (D-74).
 *
 * Antes cada paleta montaba su propia fila con `OutlinedTextField(Modifier.weight(1f))` y un
 * [PaletteChip] de glifo `"OK"`: el campo se comía **todo** el ancho del panel —«cabría el Quijote»,
 * en palabras del usuario— y la acción quedaba reducida a un cuadradito de 44 dp con dos letras
 * dentro. Aquí el campo tiene un ancho **acotado** (el de un rótulo de compás, no el del panel) y las
 * acciones son botones de verdad, con su texto y su altura táctil.
 */
@Composable
private fun PaletteTextEntry(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    applyLabel: String,
    applyDescription: String,
    applied: Boolean,
    onApply: () -> Unit,
    clearDescription: String,
    cleared: Boolean,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Density.Compact),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.widthIn(min = 150.dp, max = 240.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
        )
        PaletteActionButton(
            text = applyLabel,
            description = applyDescription,
            selected = applied,
            onClick = onApply
        )
        PaletteActionButton(
            text = "Quitar",
            description = clearDescription,
            selected = cleared,
            onClick = onClear
        )
    }
}

/**
 * **Botón de acción de una paleta**: texto, altura táctil y ancho mínimo, en vez de un cuadrado con
 * un glifo de dos letras. Se resalta cuando la acción ya está aplicada.
 */
@Composable
private fun PaletteActionButton(
    text: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = Density.RowHeight)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(Density.CornerSmall),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = Density.CardPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/**
 * **Paleta**: un grupo de símbolos de MuseScore (Alteraciones, Dinámicas, Articulaciones…) con su
 * rótulo y, debajo, la rejilla de símbolos.
 *
 * (D-79) Arrancan **plegadas**, como todas las secciones del panel (D-84). En la 26.ª tanda se
 * abrieron por defecto porque el panel entero eran quince filas vacías y no se veía un solo símbolo;
 * el usuario ha pedido después que **ningún bloque aparezca abierto de entrada**, así que vuelven a
 * nacer plegadas. La diferencia con entonces es que ahora las cabeceras son compactas y el usuario
 * sabe que hay símbolos detrás: el problema era la cabecera de 32 dp con un chevron de 20, no el
 * hecho de estar plegadas.
 */
@Composable
private fun Palette(
    title: String,
    subtitle: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    // En MuseScore las paletas son una lista de **celdas** con borde fino, sin color de acento ni
    // recuadros de Material (P-5.1, P-5.4). **Sin fondo propio**: el usuario no quiere ver la caja
    // de la herramienta detrás de las celdas — las celdas van directamente sobre el panel.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Density.Snug)
            .padding(vertical = 1.dp)
    ) {
        CollapsibleSection(
            title = title,
            subtitle = subtitle,
            initiallyExpanded = initiallyExpanded,
            titleColor = MaterialTheme.colorScheme.onSurface,
            dense = true,
            content = content
        )
    }
}

/**
 * **Rejilla de una paleta** (D-81): **celdas cuadradas de tamaño idéntico** en todas las paletas.
 *
 * Medido contra la paleta de MuseScore (captura de 320 dp: caja exterior de **46 dp**, hueco de
 * **4 dp**, seis columnas, margen de 12 dp), la rejilla anterior estaba mal por dos motivos:
 *
 * 1. **Cada fila se estiraba hasta el borde** repartiendo el ancho entre *su* número de celdas. Una
 *    paleta con 13 símbolos daba filas de 5, 4 y 4 → celdas de **60 dp**; «Alteraciones», con 6, daba
 *    celdas de **50 dp**. En el mismo panel convivían cajas de 50 y de 60 dp y las columnas de una
 *    paleta **no caían** donde las de la siguiente: medido en `laudate-window.png`, las filas de
 *    símbolos arrancaban en x=965, 971 y 977. Eso es exactamente lo que el usuario ve como
 *    «descuadre».
 * 2. El tope de 64 dp no arreglaba el problema, solo lo cambiaba de sitio.
 *
 * Ahora el **lado de la celda se calcula una sola vez** a partir del ancho del panel y del número
 * máximo de columnas, y **todas** las celdas —de todas las paletas— miden exactamente lo mismo. Las
 * filas incompletas se quedan a la izquierda con su hueco a la derecha, que es lo que hace MuseScore.
 *
 * Las celdas se miden con restricciones **fijas** (`Constraints.fixed`), así que su alto es igual a su
 * ancho: son cuadrados perfectos, no rectángulos que crecen con el texto.
 */
@Composable
private fun PaletteGrid(content: @Composable () -> Unit) {
    val gap = PaletteCellGap
    SubcomposeLayout { constraints ->
        val measurables = subcompose(PaletteGridSlot, content)
        val count = measurables.size
        if (count == 0) {
            layout(0, 0) {}
        } else {
            val gapPx = gap.roundToPx()
            val available = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
            // (D-81) **Un solo tamaño para todo el panel**: se reparte el ancho entre las columnas,
            // no entre las celdas de cada fila. Asi una paleta de 6 celdas y otra de 13 comparten
            // rejilla y las columnas caen en la misma x.
            //
            // **Numero de columnas adaptativo** (peticion del usuario: «la cuadricula con los
            // botones de las herramientas es enorme para android»). Antes eran **6 columnas
            // fijas**: en el movil, donde la hoja ocupa los ~400 dp de ancho completo, cada celda
            // salia de 63 dp —mas grande que en el escritorio, cuyo panel mide 320 dp—, y la paleta
            // se comia la pantalla. Ahora se meten todas las columnas que quepan a
            // [PaletteTargetCellWidth] de lado, con un tope para no dejar celdas minusculas.
            val columns = if (available > 0) {
                (available / PaletteTargetCellWidth.roundToPx())
                    .coerceIn(PaletteMaxColumns, PaletteMaxColumnsWide)
            } else {
                PaletteMaxColumns
            }
            val maxCellPx = PaletteMaxCellWidth.roundToPx()
            val cellWidth = if (available > 0) {
                val byColumns = (available - gapPx * (columns - 1)) / columns
                byColumns.coerceIn(1, maxCellPx)
            } else {
                PaletteCellSize.roundToPx()
            }
            val cellHeight = if (TablaturePaletteLabels.show) {
                cellWidth + PaletteLabelHeight.roundToPx()
            } else {
                cellWidth
            }
            val placeables = ArrayList<Placeable>(count)
            val positions = ArrayList<IntOffset>(count)
            var index = 0
            var y = 0
            paletteRowCounts(count, columns).forEach { inRow ->
                var x = 0
                var rowHeight = 0
                repeat(inRow) {
                    val placeable = measurables[index].measure(
                        Constraints.fixed(cellWidth, cellHeight)
                    )
                    placeables += placeable
                    positions += IntOffset(x, y)
                    x += cellWidth + gapPx
                    rowHeight = maxOf(rowHeight, placeable.height)
                    index++
                }
                y += rowHeight + gapPx
            }
            val width = if (available > 0) available else cellWidth
            layout(width, y - gapPx) {
                placeables.forEachIndexed { i, placeable -> placeable.place(positions[i]) }
            }
        }
    }
}

/** Ranura de subcomposición de [PaletteGrid]: una sola, porque la rejilla no tiene huecos. */
private const val PaletteGridSlot = "palette-grid"

/**
 * Celdas de cada fila de una paleta con [count] celdas: como mucho [columns] por fila y
 * **repartidas a partes iguales** (13 → 5, 4, 4), para que ninguna fila quede medio vacía.
 */
private fun paletteRowCounts(count: Int, columns: Int = PaletteMaxColumns): List<Int> {
    val rows = (count + columns - 1) / columns
    val base = count / rows
    val extra = count % rows
    return List(rows) { row -> if (row < extra) base + 1 else base }
}

/** Fila de botones de una paleta. Se conserva el nombre por compatibilidad: ahora es una rejilla. */
@Composable
private fun PaletteRow(content: @Composable () -> Unit) {
    PaletteGrid(content)
}

/** Rejilla de botones de una paleta (para grupos con muchos símbolos). */
@Composable
private fun PaletteFlow(content: @Composable () -> Unit) {
    PaletteGrid(content)
}

/**
 * **Lado del botón de figura** de la paleta «Duración».
 *
 * (Punto 7 del usuario) 40 dp en vez de los 30 dp de [Density.PillHeight]: a 30 dp las figuras salían
 * apretadas y la redonda y la semicorchea no se distinguían.
 *
 * En **móvil** baja a [FigureButtonSizeCompact]: con 40 dp la barra de escritura se llevaba casi un
 * tercio de la pantalla antes de empezar a escribir, y el usuario lo señaló como «enorme». En
 * escritorio, donde sobra sitio y el ratón es preciso, se queda en 40 dp.
 */
private val FigureButtonSize = 40.dp

/** Lado del botón de figura **en táctil**, en dp: la barra cabe sin comerse la partitura. */
private val FigureButtonSizeCompact = 30.dp

/**
 * Lado del botón de figura según la plataforma y el ancho disponible.
 *
 * Se decide por **ancho**, no solo por plataforma: una tableta en horizontal aguanta 40 dp igual que
 * un ordenador, y un móvil en vertical necesita 30 dp para que quepan las figuras y la partitura
 * siga viéndose.
 */
@Composable
private fun figureButtonSize(): androidx.compose.ui.unit.Dp = if (isCompactWidth()) {
    FigureButtonSizeCompact
} else {
    FigureButtonSize
}

/**
 * `true` cuando la ventana es **estrecha** (móvil en vertical), que es el caso en el que la barra de
 * escritura tiene que encogerse para no tapar la partitura.
 *
 * El umbral (**600 dp**) es el mismo que usa la navegación para pasar a paneles inline
 * (`InlinePanelsMinWidth`): una sola definición de «estrecho» en toda la app, así la barra y el menú
 * cambian de modo a la vez.
 */
@Composable
private fun isCompactWidth(): Boolean {
    val info = LocalWindowInfo.current
    val widthDp = with(LocalDensity.current) { info.containerSize.width.toDp() }
    return widthDp < 600.dp
}

/**
 * **Cuerpo de los silencios planos** en el botón de figura, como fracción de su altura (D-84).
 *
 * La redonda y la blanca de Bravura son un rectángulo de 144 unidades de alto sobre una em de 1000,
 * es decir **0,144 em**. Si se les deja crecer hasta llenar el botón, se estiran a 32 px de alto y
 * pierden la proporción con la que se leen en una partitura (que es **ancho y plano**, 1,96:1). Con
 * este cuerpo el silencio ocupa el ancho del botón y conserva esa proporción.
 */
private const val PALETTE_REST_EM = 1.6f

/**
 * Botón de **figura de duración** de la paleta «Duración», calcado de la barra de duración de
 * **TuxGuitar**: un **icono por figura** (`1.png`/`4.png`/`8.png`/`dotted.png`…), **sin caja** (solo se
 * resalta el activo), y el puntillo **superpuesto** al icono base, que es como lo monta TuxGuitar.
 *
 * El icono es el glifo SMuFL **completo** de la figura (`noteQuarterUp`…), el equivalente exacto a su
 * PNG: la nota entera de una pieza, no cabeza + plica + corchete compuestos a mano (esa era la causa
 * de los «puntos» que se veían antes a tamaño diminuto).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FigureButton(
    figure: RhythmFigure,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    // Tamaño **adaptativo**: 40 dp en escritorio, 30 dp en móvil (ver [figureButtonSize]).
    val buttonSize = figureButtonSize()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(figure.label) } },
        state = rememberTooltipState()
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            // **Sin borde**: como TuxGuitar, que no dibuja caja en los botones de duración; el activo
            // se resalta con fondo. (Antes cada botón llevaba un borde de 1 dp: era la «mierda» de
            // cajas que se veía.)
            // (Punto 7 del usuario) El botón se **agranda** de 30 a 40 dp: a 30 dp las figuras salían
            // apretadas y no se distinguían unas de otras. Y vuelve a 30 dp en móvil, donde 40 dp
            // hacían que la barra de escritura tapara la partitura.
            modifier = Modifier.size(buttonSize),
            shape = RoundedCornerShape(3.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Transparent
            },
            border = null,
            contentColor = contentColor
        ) {
            val measurer = rememberTextMeasurer()
            val music = MusicFont.family()
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                // Icono base: el glifo **completo** de la figura (o el silencio, que también existe
                // como glifo de una pieza: `restWhole`, `restQuarter`…).
                val glyph = MusicFont.iconGlyph(figure) ?: return@Canvas
                // (D-84) **La redonda y la blanca son el MISMO rectángulo en Bravura.**
                //
                // Medido con `fontTools`: `restWhole` (E4E3) y `restHalf` (E4E4) tienen la misma
                // tinta, 282 × 144 unidades, con los mismos 10 contornos; lo único que cambia es
                // su altura respecto a la **línea base** (la redonda va de −135 a 9, colgando por
                // debajo; la blanca de −2 a 142, apoyada por encima). La diferencia musical no está
                // en el dibujo del signo, sino en **de qué lado de la línea** cuelga.
                //
                // Centrando ambos por su tinta —lo que se hacía— los dos se veían idénticos, y con
                // razón parecía que eran el mismo símbolo. Ahora se ancla el glifo por su **línea
                // base** y se dibuja un **trozo de línea de pentagrama** de referencia: la redonda
                // cuelga por debajo y la blanca se apoya por encima. Es la única forma de que se
                // distingan, y es exactamente cómo se leen en una partitura.
                val isRest = figure.isRest
                val ink = MusicFont.iconInk(figure)
                val ref = measurer.measure(
                    text = glyph,
                    style = TextStyle(color = contentColor, fontSize = size.height.toSp(), fontFamily = music)
                )
                val advanceEm = if (size.height > 0f) ref.size.width / size.height else 1f
                val emByHeight = if (ink != null && ink.height > 0f) size.height / ink.height else size.height
                val emByWidth = if (advanceEm > 0f) size.width / advanceEm else size.height
                // En los silencios planos (redonda y blanca) el tope por alto no debe actuar: si se
                // les deja crecer hasta llenar el botón, el rectángulo de 144 unidades se estira a
                // 32 px de alto y pierde su proporción. Se acotan por **ancho** y se les da un cuerpo
                // fijo, que es como los dibuja MuseScore en su paleta.
                val emPx = if (isRest && (ink == null || ink.height < 0.4f)) {
                    minOf(size.height * PALETTE_REST_EM, emByWidth)
                } else {
                    minOf(emByHeight, emByWidth)
                }
                val layout = measurer.measure(
                    text = glyph,
                    style = TextStyle(color = contentColor, fontSize = emPx.toSp(), fontFamily = music)
                )
                val originX = (size.width - layout.size.width) / 2f
                // Centrado por **tinta**: la caja de línea de Bravura (4,024 em) no coincide con la
                // tinta, así que centrar por `size.height` dejaba el icono descentrado.
                val originY = if (isRest) {
                    // Anclaje por **línea base** con la línea de referencia en el centro del botón.
                    size.height / 2f - layout.getLineBaseline(0)
                } else {
                    inkTopLeftY(layout, size.height / 2f, emPx, ink)
                }
                // Línea de pentagrama de referencia: es lo que hace visible de qué lado cuelga el
                // silencio. Solo para redonda y blanca, que son los dos que se confunden.
                if (isRest && (ink == null || ink.height < 0.4f)) {
                    drawLine(
                        color = contentColor.copy(alpha = 0.45f),
                        start = Offset(originX - size.width * 0.06f, size.height / 2f),
                        end = Offset(
                            originX + layout.size.width + size.width * 0.06f,
                            size.height / 2f
                        ),
                        strokeWidth = (size.height * 0.045f).coerceAtLeast(1f),
                        cap = StrokeCap.Butt
                    )
                }
                drawText(textLayoutResult = layout, topLeft = Offset(originX, originY))
                // **Puntillo superpuesto** (como `dotted.png` sobre `4.png` en TuxGuitar): a la derecha
                // del icono, a la altura de la **cabeza** (que en los glifos `note*Up` está en la línea
                // base).
                if (figure.isDotted) {
                    val dot = measurer.measure(
                        text = MusicFont.Glyph.AUGMENTATION_DOT,
                        style = TextStyle(color = contentColor, fontSize = emPx.toSp(), fontFamily = music)
                    )
                    drawText(
                        textLayoutResult = dot,
                        topLeft = Offset(
                            originX + layout.size.width * 0.78f,
                            originY + layout.getLineBaseline(0) - dot.size.height / 2f
                        )
                    )
                }
            }
        }
    }
}

/**
 * **Símbolo de una celda de paleta**, con **cuerpo uniforme** para todos los glifos (D-81).
 *
 * Antes el glifo se pintaba a un cuerpo fijo de 22 sp dentro de una celda de 44 dp: la tinta medía
 * entre 5×5 y 12×15 px, es decir un tercio de la celda, y el usuario lo describió como «simbolitos de
 * mierda en cajas enormes».
 *
 * (D-81) **Por qué no basta con rellenar la celda.** Después se pasó al extremo contrario: el cuerpo se
 * elegía como `min(alto / altoDeTinta, ancho / avance)`, que **rellena cada glifo hasta el borde**. Eso
 * hace que los glifos planos y anchos (`—` de «sin alteración», el picado, el portato) se dibujen
 * gigantes y los altos (coda, segno) salgan pequeños, porque su tinta tiene otra proporción.
 *
 * Medido en la paleta de MuseScore (cajas de 82 px), la coda (tinta 1,056 em) ocupa 40 px y el
 * sostenido (0,698 em) 26 px: el mismo em implícito de **37-38 px ≈ 0,46 del lado**. Es decir,
 * MuseScore usa **un solo cuerpo para todos los símbolos** y deja que la tinta varíe. Eso es lo que se
 * hace ahora con [PALETTE_GLYPH_EM], acotando después por la caja de tinta de cada glifo.
 *
 * Los glifos SMuFL se centran por **tinta** ([MusicFont.inkOf]) porque su caja de línea es de 4 em y
 * centrar por ella los dejaba diminutos y descentrados; el texto se centra por **caja de línea**. El
 * `Canvas` recorta con `clipToBounds()` por si una métrica futura se desviara.
 */
@Composable
private fun PaletteGlyph(
    glyph: String,
    color: Color,
    fontFamily: FontFamily,
    italic: Boolean,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val isMusicGlyph = glyph.length == 1 && glyph[0].code in 0xE000..0xF8FF
    val family = if (isMusicGlyph) MusicFont.family() else fontFamily
    val ink = if (isMusicGlyph) MusicFont.inkOf(glyph) else null
    Canvas(modifier = modifier.clipToBounds()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        // (D-77) La **ligadura de prolongación** no es un glifo: Bravura no tiene `tie`/`slur` (son
        // curvas, no caracteres) y el `‿` tipográfico no existe en la fuente del sistema, así que la
        // celda salía **completamente vacía**. Se dibuja la curva a mano.
        if (glyph == TIE_PALETTE_GLYPH) {
            drawTieCurve(color)
            return@Canvas
        }
        val emRef = size.height
        val base = TextStyle(
            color = color,
            fontSize = emRef.toSp(),
            fontFamily = family,
            fontWeight = if (italic) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal
        )
        val reference = measurer.measure(glyph, style = base)
        // (D-81) **Avance del glifo a 1 em**, que acota el ancho. Para los glifos **musicales** se
        // acota a un rango plausible: cuando la fuente aún no está resuelta (es un recurso asíncrono y
        // el primer `measure` puede caer antes de que cargue) el carácter PUA se mide con la fuente de
        // respaldo y devuelve un avance de 3-5 em. Para el **texto** (`cresc.`, `P.M.`) el avance es
        // legítimamente grande —«cresc.» ocupa 2,5 em—, así que se usa tal cual: acotarlo a 2 em hacía
        // que el rótulo se saliera de la celda y se leyera «cresc.» recortado como «cres».
        val rawAdvanceEm = if (emRef > 0f) reference.size.width / emRef else 1f
        val advanceEm = if (isMusicGlyph) {
            if (rawAdvanceEm in 0.05f..2f) rawAdvanceEm else 1f
        } else {
            rawAdvanceEm.coerceAtLeast(0.05f)
        }
        // (D-81) **Un solo cuerpo (em) para todos los símbolos**, como en MuseScore.
        //
        // Medido de su paleta: con cajas de 82 px, la coda (tinta 1,056 em) ocupa 40 px y el sostenido
        // (0,698 em) 26 px, de donde sale un em implícito de **37-38 px ≈ 0,46 del lado de la caja**
        // para los dos. Es decir, MuseScore **no** escala cada símbolo para que llene su celda: usa el
        // mismo cuerpo y deja que la tinta varíe (49-74 % del lado en los símbolos llenos, 17 % en las
        // marcas pequeñas como el picado). Rellenar cada glifo hasta el borde —lo que se hacía antes—
        // hace que el guion de «sin alteración» y la coma del picado salgan gigantes y la coda pequeña,
        // que es justo el descuadre que se veía.
        //
        // Se toma un cuerpo algo mayor que el de MuseScore ([PALETTE_GLYPH_EM] = 0,62 del lado) para
        // que los símbolos tengan presencia en la celda, y se acota después por la **caja de tinta**
        // real de cada glifo, de modo que ninguno se salga.
        var emPx = size.minDimension * PALETTE_GLYPH_EM
        // Tope por altura de tinta (los símbolos muy altos no pueden invadir la celda vecina).
        if (ink != null && ink.height > 0f) {
            emPx = minOf(emPx, (size.height * PALETTE_GLYPH_FILL) / ink.height)
        }
        // Tope por ancho: afecta sobre todo a los rótulos de texto (`cresc.`, `P.M.`).
        if (advanceEm > 0f) {
            emPx = minOf(emPx, (size.width * PALETTE_GLYPH_FILL) / advanceEm)
        }
        var layout = measurer.measure(glyph, style = base.copy(fontSize = emPx.toSp()))
        // (D-81) **Solo el texto** se reajusta por caja de línea. Aplicarlo también a los glifos
        // musicales es el error que dejaba los símbolos a 1/4 de tamaño: la caja de línea de Bravura
        // mide **4,024 em** (tiene ascendentes y descendentes reservados para el pentagrama completo),
        // así que `alto / cajaDeLínea` encogía el glifo cuatro veces. Los glifos SMuFL se acotan por su
        // **tinta**, que es lo que se pinta de verdad, y el `Canvas` los recorta si hiciera falta.
        if (!isMusicGlyph && layout.size.height > size.height * PALETTE_GLYPH_FILL && layout.size.height > 0) {
            emPx *= (size.height * PALETTE_GLYPH_FILL) / layout.size.height
            layout = measurer.measure(glyph, style = base.copy(fontSize = emPx.toSp()))
        }
        val topLeft = if (isMusicGlyph) {
            val center = (ink ?: com.grafitto.laudate.domain.tablature.TabGlyphs.FALLBACK_INK)
                .centerFromBaseline
            Offset(
                x = (size.width - layout.size.width) / 2f,
                y = size.height / 2f - layout.getLineBaseline(0) + center * emPx
            )
        } else {
            // El texto se centra por **caja de línea**, que ya incluye el espacio de ascendentes y
            // descendentes: así «ppp» y «cresc.» quedan a la misma altura y no tocan el borde.
            Offset(
                x = (size.width - layout.size.width) / 2f,
                y = (size.height - layout.size.height) / 2f
            )
        }
        drawText(textLayoutResult = layout, topLeft = topLeft)
    }
}

/**
 * **Cuerpo uniforme** del símbolo dentro de la celda de paleta (D-81), como fracción del lado.
 *
 * MuseScore dibuja **todos** los símbolos de una paleta con el **mismo cuerpo** y deja que la tinta
 * varíe según el glifo: medido en su paleta (cajas de 82 px), la coda (tinta 1,056 em) ocupa 40 px y el
 * sostenido (0,698 em) 26 px, es decir un em implícito de **37-38 px ≈ 0,46 del lado** en los dos
 * casos. Aquí se usa 0,62 para que el símbolo tenga algo más de presencia en la celda, y después se
 * acota por la caja de tinta real para que ninguno se salga.
 */
private const val PALETTE_GLYPH_EM = 0.62f

/**
 * **Margen de seguridad** de la celda de paleta (D-81).
 *
 * El usuario lo pidió como «el icono ocupando el **máximo posible** de la caja **sin salirse**», y esas
 * dos condiciones se tocan: escalar al 100 % exacto hace que los glifos planos y anchos (`—` de «sin
 * alteración», `rake`) lleguen al píxel del borde, y con el antialiasing de la fuente eso se lee como
 * un símbolo pegado al marco.
 */
private const val PALETTE_GLYPH_FILL = 0.88f

/**
 * Glifo de paleta de una **alteración**, siempre en **SMuFL** (D-81).
 *
 * Antes solo las dobles usaban SMuFL y bemol, becuadro y sostenido se quedaban con su carácter
 * Unicode (`♭`, `♮`, `♯`). Esos caracteres los resuelve la fuente **del sistema**, no Bravura, y su
 * tinta es mucho menor que la del carácter homónimo de la fuente musical: en el PNG de verificación
 * el bemol medía **7×29 px** dentro de una celda de 48 dp mientras los símbolos SMuFL de la misma
 * fila ocupaban 40×40. Medido en la paleta de MuseScore, la alteración ocupa entre el **49 % y el
 * 74 %** del lado de la caja. Con el glifo SMuFL medido en [TabGlyphs.INK] el tamaño sale solo.
 */
private fun paletteAccidentalGlyph(accidental: TabAccidental): String = when (accidental) {
    TabAccidental.FLAT -> "\uE260"
    TabAccidental.NATURAL -> "\uE261"
    TabAccidental.SHARP -> "\uE262"
    TabAccidental.DOUBLE_SHARP -> "\uE263"
    TabAccidental.DOUBLE_FLAT -> "\uE264"
}

/** Glifo centinela de la **ligadura de prolongación** en la paleta: se pinta como curva, no como texto. */
private const val TIE_PALETTE_GLYPH = "\u2040"

/**
 * Curva de **ligadura de prolongación** para la celda de paleta: un arco plano, como el `tie` de
 * MuseScore. Se dibuja vectorialmente porque la fuente musical no trae el glifo.
 */
private fun DrawScope.drawTieCurve(color: Color) {
    val w = size.width * 0.86f
    val h = size.height * 0.30f
    val left = (size.width - w) / 2f
    val top = (size.height - h) / 2f
    val path = Path().apply {
        moveTo(left, top + h)
        quadraticTo(left + w / 2f, top - h * 0.55f, left + w, top + h)
    }
    drawPath(path, color, style = Stroke(width = (size.height * 0.075f).coerceAtLeast(1.5f), cap = StrokeCap.Round))
}

/**
 * Coloca el **fantasma** del arrastre justo bajo el puntero, en coordenadas de ventana (B-13). Es lo
 * que hace MuseScore: el símbolo viaja con el dedo hasta que se suelta sobre la nota.
 */
private class DragGhostPositionProvider(private val position: Offset) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset = IntOffset(
        (position.x - popupContentSize.width / 2f).roundToInt(),
        (position.y - popupContentSize.height / 2f).roundToInt()
    )
}

/** Símbolo que **sigue al puntero** mientras se arrastra una celda de paleta (B-13). */
@Composable
private fun PaletteDragGhost(dragState: PaletteDragState) {
    if (!dragState.isDragging) return
    val glyph = dragState.glyph ?: return
    val position = dragState.position
    Popup(
        popupPositionProvider = remember(position) { DragGhostPositionProvider(position) },
        properties = PopupProperties(focusable = false, clippingEnabled = false)
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            shadowElevation = 4.dp
        ) {
            Text(
                text = glyph,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = if (glyph.length == 1 && glyph[0].code in 0xE000..0xF8FF) {
                    MusicFont.family()
                } else {
                    FontFamily.Monospace
                },
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Estado del **arrastre de celdas de paleta** (B-13), compartido entre el panel de paletas y la
 * rejilla.
 *
 * En MuseScore una celda de paleta se puede **arrastrar sobre una nota** para aplicarle el símbolo;
 * el clic la aplica a la selección. Aquí se replica igual: al empezar el arrastre se guarda la
 * **fábrica del intent** (que necesita saber sobre qué celda cae) y la posición del puntero en
 * coordenadas raíz; al soltar, la rejilla resuelve la celda con [hitTester] y lanza el intent.
 */
@Stable
class PaletteDragState {
    /** Fábrica del intent a aplicar sobre la celda donde se suelte. `null` = sin arrastre. */
    var payload: ((TabCanvasHit) -> TablatureUiIntent?)? by mutableStateOf(null)

    /** Posición actual del puntero en **coordenadas raíz**. */
    var position: Offset by mutableStateOf(Offset.Zero)

    /** Glifo que se está arrastrando, para el **fantasma** que sigue al puntero. */
    var glyph: String? by mutableStateOf(null)

    /** Resolvedor de celda del lienzo; lo instala la rejilla. */
    var hitTester: TabCanvasHitTester? = null

    /** `true` mientras hay una celda de paleta en el aire. */
    val isDragging: Boolean get() = payload != null

    fun begin(factory: (TabCanvasHit) -> TablatureUiIntent?, at: Offset, glyph: String? = null) {
        payload = factory
        position = at
        this.glyph = glyph
    }

    fun move(delta: Offset) {
        position += delta
    }

    fun cancel() {
        payload = null
        glyph = null
    }

    /** Resuelve la celda bajo el puntero y devuelve el intent a lanzar (o `null`). */
    fun resolveDrop(): TablatureUiIntent? {
        val factory = payload ?: return null
        val hit = hitTester?.invoke(position) ?: return null
        return factory(hit)
    }
}

/**
 * **Estado de arrastre compartido** entre las paletas y la rejilla (D-58).
 *
 * Las paletas viven en el **panel de herramientas** de la derecha y la rejilla en el contenido: son
 * dos ramas distintas del árbol de composición, así que no pueden compartir un `remember`. Como solo
 * hay **un** editor de tablatura abierto a la vez, una instancia única basta y evita izar el estado
 * hasta la raíz de la aplicación.
 */
val TablaturePaletteDrag = PaletteDragState()

/**
 * **Nombres en las celdas de paleta** (D-62).
 *
 * El usuario pidió «una ayuda contextual para saber qué hace cada botón». El *tooltip* ya existía,
 * pero en **móvil no hay hover**: el nombre de un símbolo SMuFL (`𝄐`, `𝄋`, `♮`) solo se descubría
 * manteniendo el dedo pulsado, y en escritorio había que pasar el ratón por cada celda. MuseScore
 * resuelve esto enseñando **el nombre debajo del símbolo** en cada celda de la paleta.
 *
 * Aquí se ofrece como **interruptor** —y no siempre encendido— porque con los nombres las celdas
 * ocupan el doble de alto y la paleta entera deja de caber en el panel: quien ya conoce los símbolos
 * los quiere compactos. El estado es **global** por la misma razón que [TablaturePaletteDrag]: solo
 * hay un editor de tablatura abierto a la vez.
 */
/**
 * **Alto de la celda de paleta**, en dp (D-77).
 *
 * El **ancho** no es fijo: lo calcula [PaletteGrid] para que cada fila **llene** el panel. Antes la
 * celda era un cuadrado de 44 dp con `defaultMinSize`, así que cada símbolo tomaba el tamaño de su
 * texto: en la misma fila convivían celdas de 44, 61 y 86 dp de ancho —y una de 44×91 dp con un glifo
 * de 8×9 px dentro—. Eso era exactamente lo que el usuario veía como «cajas enormes descuadradas».
 */
private val PaletteCellSize = 48.dp

/**
 * **Hueco entre celdas** de una paleta (D-81).
 *
 * Medido de la paleta de MuseScore: **4 dp** (8 px en la captura de 320 dp). Antes eran 2 dp y las
 * cajas quedaban pegadas, de modo que la rejilla se leía como una masa continua en vez de como cajas
 * separadas.
 */
private val PaletteCellGap = 4.dp

/**
 * **Margen interior de la celda**: la tinta del símbolo nunca toca el borde.
 *
 * Es lo que el usuario describe como «el icono ocupando el máximo posible de la caja **sin salirse**»:
 * con 4 dp de margen por lado, el icono puede ocupar hasta `lado − 8 dp`. MuseScore deja más aire
 * (su tinta típica ocupa entre el **49 % y el 74 %** del lado de la caja, medido en la captura), así
 * que este margen da un poco más de presencia al símbolo sin que llegue a tocar el borde.
 */
private val PaletteGlyphInset = 4.dp

/** Alto extra de la celda cuando se encienden los nombres ([TablaturePaletteLabels]). */
private val PaletteLabelHeight = 30.dp

/**
 * **Columnas mínimas** de una paleta. Con menos, las celdas se hacen enormes: en el móvil la hoja de
 * herramientas ocupa el ancho completo, así que repartir entre pocas columnas infla cada celda por
 * encima de la del escritorio, que es justo lo contrario de lo que hace falta.
 */
private const val PaletteMaxColumns = 6

/**
 * **Columnas máximas** de una paleta: el tope de la cuadrícula cuando el ancho da para más.
 *
 * Existe para que las celdas no se queden en migajas en una tableta en vertical o en una hoja ancha
 * de móvil: por debajo de ~40 dp un glifo SMuFL deja de leerse.
 */
private const val PaletteMaxColumnsWide = 9

/**
 * **Lado objetivo** de una celda de paleta, en dp: el que se busca al elegir cuántas columnas caben.
 *
 * Es la guía de los ~40 dp del panel de MuseScore, y el número que hace que en un móvil de 400 dp la
 * paleta salga a 8 columnas (46 dp por celda) en vez de a 6 (63 dp).
 */
private val PaletteTargetCellWidth = 44.dp

/**
 * **Ancho máximo de una celda de paleta** (D-80, revisado en D-81).
 *
 * Con el reparto por columnas de [PaletteGrid] el ancho sale de dividir el panel entre
 * [PaletteMaxColumns], así que en el panel de 320 dp las celdas miden **~50 dp** y el tope no llega a
 * actuar. Se conserva como red de seguridad para paneles muy anchos (ventana maximizada en
 * escritorio): sin él, seis columnas en 900 dp darían celdas de 148 dp con el símbolo perdido dentro.
 */
private val PaletteMaxCellWidth = 64.dp

/** Radio de la celda de paleta (D-81): el de MuseScore, 2 dp. Casi recto, pero sin esquina viva. */
private val PaletteCellCorner = 2.dp

/** Grosor del borde de la celda de paleta (D-81): 1 px, como el filete de MuseScore. */
private val PaletteCellBorderWidth = 1.dp

object TablaturePaletteLabels {
    /** `true` = cada celda enseña su nombre debajo del símbolo. */
    var show: Boolean by mutableStateOf(false)
}

/**
 * Celda de una **paleta** al estilo MuseScore: fondo claro y **borde fino** (no un chip Material
 * relleno), que cambia a un tinte azul al estar activa. El nombre va en el *tooltip*.
 *
 * [italic] pone el símbolo en **cursiva negrita**, que es como MuseScore escribe las dinámicas
 * (`mp`, `cresc.`…); [serifFamily] usa una serif para los símbolos que en una partitura van en serif.
 *
 * [dragIntent] hace la celda **arrastrable** (B-13): al soltarla sobre la partitura, el símbolo se
 * aplica a la nota que hay debajo del dedo, como en MuseScore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteChip(
    glyph: String,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    italic: Boolean = false,
    /**
     * Fuente del glifo. Por defecto la de texto; los signos **musicales** (glifos SMuFL: acentos,
     * arcos, segno…) pasan la fuente del pentagrama, o saldrian como cajas.
     */
    fontFamily: FontFamily = FontFamily.Monospace,
    dragIntent: ((TabCanvasHit) -> TablatureUiIntent?)? = null,
    dragState: PaletteDragState? = null,
    onDrop: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    val draggable = dragIntent != null && dragState != null && enabled
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                // (D-81) La celda **obedece el tamaño que le impone** [PaletteGrid]: ancho y alto
                // llegan fijos por `Constraints.fixed`, así que la celda es un **cuadrado** del mismo
                // lado que las de todas las demás paletas. Ya no fija su propia altura ni su ancho:
                // si lo hiciera, cada paleta volvería a tener su propio tamaño.
                .fillMaxWidth()
                .onGloballyPositioned { rootPosition = it.positionInRoot() }
                // (D-77) El símbolo se pinta en un `Canvas`, así que la celda ya no tiene nodo de
                // texto: sin esto ni TalkBack ni los tests sabrían qué símbolo es. El nombre de la
                // celda pasa a ser su descripción accesible.
                .semantics { contentDescription = label }
                .testTag(TAB_PALETTE_CELL_TEST_TAG)
                .then(
                    if (!draggable) {
                        Modifier
                    } else {
                        Modifier.pointerInput(dragIntent, dragState) {
                            // **Pulsacion larga para arrastrar** (peticion del usuario: «en horizontal
                            // no puedo hacer scroll»). Antes cualquier deslizamiento que empezara sobre
                            // una celda se lo quedaba el arrastre de simbolos -`detectDragGestures`
                            // consume el gesto en cuanto pasa el umbral-, asi que la hoja de herramientas
                            // no se podia desplazar en ninguna direccion. Ahora el arrastre arranca
                            // **solo tras mantener pulsado** `viewConfiguration.longPressTimeoutMillis`:
                            // un deslizamiento rapido (que es como se hace scroll) se queda para el
                            // contenedor, y arrastrar un simbolo sigue funcionando manteniendolo un
                            // instante. Mismo patron que usa cualquier lista reordenable.
                            val longPress = viewConfiguration.longPressTimeoutMillis
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Si el dedo se mueve antes del umbral de pulsacion larga, es scroll:
                                // se abandona sin consumir y el contenedor se queda el gesto.
                                val held = withTimeoutOrNull(longPress) {
                                    var moved = false
                                    while (!moved) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: return@withTimeoutOrNull false
                                        if (change.changedToUp()) return@withTimeoutOrNull false
                                        moved = change.positionChange().getDistance() > viewConfiguration.touchSlop
                                    }
                                    false
                                } ?: true
                                if (!held) return@awaitEachGesture
                                down.consume()
                                dragState?.begin(dragIntent!!, rootPosition + down.position, glyph)
                                // Seguir el dedo hasta que se suelte.
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (change.changedToUp()) break
                                    val delta = change.positionChange()
                                    change.consume()
                                    dragState?.move(delta)
                                }
                                onDrop?.invoke()
                            }
                        }
                    }
                ),
            shape = RoundedCornerShape(PaletteCellCorner),
            // Celda de paleta **con caja** (D-81). En la iteración anterior se quitaron las cajas y el
            // resultado fue peor: símbolos sueltos flotando sobre el lavanda del panel, sin retícula.
            // MuseScore **sí** dibuja caja —blanca pura (`#FFFFFF`) con borde fino (`#E5E1E9`, medido
            // en la captura)— y el usuario la identifica como «las cajitas bonitas». Se replica: fondo
            // `surfaceContainerLowest` (blanco puro en el tema claro) y borde de 1 px en
            // `outlineVariant`, que sobre el lavanda del panel dibuja exactamente esa retícula.
            color = when {
                selected -> MaterialTheme.colorScheme.primaryContainer
                !enabled -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.surfaceContainerLowest
            },
            border = BorderStroke(
                PaletteCellBorderWidth,
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            ),
            contentColor = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
        ) {
            val glyphColor = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
            Box(
                modifier = Modifier.fillMaxSize().padding(PaletteGlyphInset),
                contentAlignment = Alignment.Center
            ) {
                if (TablaturePaletteLabels.show) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PaletteGlyph(
                            glyph = glyph,
                            color = glyphColor,
                            fontFamily = fontFamily,
                            italic = italic,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    PaletteGlyph(
                        glyph = glyph,
                        color = glyphColor,
                        fontFamily = fontFamily,
                        italic = italic,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/** Botón de una **técnica** de la paleta: dibuja su símbolo vectorial o su glifo de texto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TechniquePaletteChip(
    technique: TabTechnique,
    selected: Boolean,
    enabled: Boolean,
    dragIntent: ((TabCanvasHit) -> TablatureUiIntent?)? = null,
    dragState: PaletteDragState? = null,
    onDrop: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val glyph = technique.vectorialGlyph()
    var rootPosition by remember { mutableStateOf(Offset.Zero) }
    val draggable = dragIntent != null && dragState != null && enabled
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(techniqueLabel(technique)) } },
        state = rememberTooltipState()
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                // (D-81) La **misma** celda que [PaletteChip]: el tamaño lo impone [PaletteGrid] con
                // `Constraints.fixed`, así que es un cuadrado idéntico al de las demás paletas. Si la
                // técnica midiera distinto, la rejilla de «Técnicas» —que mezcla «Sin técnica» con las
                // técnicas— quedaría descuadrada, que es justo lo que el usuario reportó.
                .fillMaxWidth()
                .onGloballyPositioned { rootPosition = it.positionInRoot() }
                // (D-77) Igual que en [PaletteChip]: el símbolo va en un `Canvas`, así que el nombre
                // de la técnica es su descripción accesible.
                .semantics { contentDescription = techniqueLabel(technique) }
                .testTag(TAB_PALETTE_CELL_TEST_TAG)
                .then(
                    if (!draggable) {
                        Modifier
                    } else {
                        Modifier.pointerInput(dragIntent, dragState) {
                            // **Pulsacion larga para arrastrar**, igual que en [PaletteChip]: sin esto
                            // la celda se queda todos los deslizamientos y la hoja de herramientas no
                            // se puede desplazar.
                            val longPress = viewConfiguration.longPressTimeoutMillis
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val held = withTimeoutOrNull(longPress) {
                                    var moved = false
                                    while (!moved) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                            ?: return@withTimeoutOrNull false
                                        if (change.changedToUp()) return@withTimeoutOrNull false
                                        moved = change.positionChange().getDistance() > viewConfiguration.touchSlop
                                    }
                                    false
                                } ?: true
                                if (!held) return@awaitEachGesture
                                down.consume()
                                dragState?.begin(
                                    dragIntent!!,
                                    rootPosition + down.position,
                                    techniqueGlyph(technique)
                                )
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    if (change.changedToUp()) break
                                    val delta = change.positionChange()
                                    change.consume()
                                    dragState?.move(delta)
                                }
                                onDrop?.invoke()
                            }
                        }
                    }
                ),
            shape = RoundedCornerShape(PaletteCellCorner),
            // (D-81) Misma celda **con caja** que [PaletteChip]: fondo blanco y borde fino, para que
            // «Técnicas» —que mezcla «Sin técnica» con las técnicas— tenga exactamente la misma
            // retícula que el resto de las paletas.
            color = when {
                selected -> MaterialTheme.colorScheme.primaryContainer
                !enabled -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.surfaceContainerLowest
            },
            border = BorderStroke(
                PaletteCellBorderWidth,
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            ),
            contentColor = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
        ) {
            // (D-77) El símbolo ocupa la celda y, con los nombres encendidos, el nombre va debajo
            // **igual que en [PaletteChip]**: si una celda creciera y la otra no, la rejilla de
            // «Técnicas» volvería a descuadrarse.
            val glyphColor = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(PaletteGlyphInset),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val symbol: @Composable () -> Unit = {
                    if (glyph != null) {
                        TabTechniqueGlyph(
                            kind = glyph,
                            color = glyphColor,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        PaletteGlyph(
                            glyph = techniqueGlyph(technique),
                            color = glyphColor,
                            fontFamily = FontFamily.Monospace,
                            italic = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (TablaturePaletteLabels.show) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) { symbol() }
                    Text(
                        text = techniqueLabel(technique),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    symbol()
                }
            }
        }
    }
}

/** Valores de tempo (BPM) más frecuentes que ofrece la paleta, uno por botón (T5). */
private val TEMPO_PRESETS = listOf(60, 90, 120)

/** Paso del control `−`/`+` del tempo (D-61): 5 BPM, como el `+`/`−` del inspector de MuseScore. */
private const val TEMPO_STEP_BPM = 5

/**
 * Límites del tempo escribible: los **mismos** que aplica [TablatureEditing.setTempo], para que el
 * control no ofrezca un valor que el modelo vaya a recortar en silencio.
 */
private val MIN_TEMPO_BPM = TablatureEditing.MIN_TEMPO_BPM
private val MAX_TEMPO_BPM = TablatureEditing.MAX_TEMPO_BPM

/** Tempo con el que arranca el control cuando el compás aún no tiene indicación. */
private const val DEFAULT_TEMPO_BPM = 90

/** Signos de navegación que ofrece la paleta, uno por botón (T1). */
private val NAVIGATION_MARKS = com.grafitto.laudate.domain.model.tablature.TabNavigationMark.entries

/** Glifo corto de las técnicas que se escriben con **texto** (`H`/`P` ligado, `↓`/`↑` púa). */
private fun techniqueGlyph(technique: TabTechnique): String = when (technique) {
    TabTechnique.NONE -> "—"
    TabTechnique.HAMMER -> "H"
    TabTechnique.PULL -> "P"
    // La púa es un **arco** de la fuente musical (SMuFL `stringsDownBow`/`stringsUpBow`), no una
    // flecha: en MuseScore es un símbolo del pentagrama.
    TabTechnique.PICK_DOWN -> TabArticulation.DOWN_BOW.display
    TabTechnique.PICK_UP -> TabArticulation.UP_BOW.display
    // El resto tiene símbolo vectorial (`vectorialGlyph()`), así que su glifo de texto es irrelevante:
    // se usa el nombre corto por si algún día se pinta como texto.
    TabTechnique.TREMOLO -> "﹡"
    TabTechnique.SLIDE -> "/"
    TabTechnique.BEND -> "b"
    TabTechnique.BEND_DOWN -> "b↓"
    TabTechnique.PRE_BEND -> "b↑"
    TabTechnique.HARMONIC -> "◇"
    TabTechnique.PALM_MUTE -> "P.M."
    TabTechnique.VIBRATO -> "~"
    TabTechnique.LET_RING -> "l.v."
    TabTechnique.MUTED -> "X"
    TabTechnique.GHOST -> "( )"
    TabTechnique.TAPPING -> "T"
    TabTechnique.TR -> "tr"
    TabTechnique.ARPEGGIO -> "⤓"
    TabTechnique.RAKE -> "rake"
    TabTechnique.ARTIFICIAL_HARMONIC -> "A.H."
    TabTechnique.PINCH_HARMONIC -> "P.H."
    TabTechnique.TAPPED_HARMONIC -> "T.H."
    TabTechnique.WHAMMY_DIVE -> "dive"
    TabTechnique.WHAMMY_SCOOP -> "scoop"
    TabTechnique.TREMOLO_BAR -> "~bar"
    TabTechnique.VIOLIN -> "0<>"
}

/** Marcas de repetición que ofrece la paleta, una por botón. */
private val REPEAT_MARKS = listOf(
    com.grafitto.laudate.domain.model.tablature.TabRepeatMark.START,
    com.grafitto.laudate.domain.model.tablature.TabRepeatMark.END,
    com.grafitto.laudate.domain.model.tablature.TabRepeatMark.BOTH
)

/** Voltas (casillas de vez) que ofrece la paleta, una por botón. */
private val ENDINGS = listOf(1, 2, 3)

/** Nombre del símbolo de repetición, para el *tooltip* del botón. */
private fun repeatMarkLabel(
    mark: com.grafitto.laudate.domain.model.tablature.TabRepeatMark
): String = when (mark) {
    com.grafitto.laudate.domain.model.tablature.TabRepeatMark.START -> "Repetición: comienza (|:)"
    com.grafitto.laudate.domain.model.tablature.TabRepeatMark.END -> "Repetición: termina (:|)"
    com.grafitto.laudate.domain.model.tablature.TabRepeatMark.BOTH -> "Repetición: comienza y termina (|: :|)"
}

/** Glifo de un tipo de **barra de compás** (`|`, `||`, final), para el botón de la paleta. */
private fun barlineGlyph(barline: com.grafitto.laudate.domain.model.tablature.TabBarline): String =
    when (barline) {
        com.grafitto.laudate.domain.model.tablature.TabBarline.SINGLE -> "|"
        com.grafitto.laudate.domain.model.tablature.TabBarline.DOUBLE -> "||"
        com.grafitto.laudate.domain.model.tablature.TabBarline.FINAL -> "|‖"
    }

/** Nombre legible de un tipo de **barra de compás**. */
private fun barlineLabel(barline: com.grafitto.laudate.domain.model.tablature.TabBarline): String =
    when (barline) {
        com.grafitto.laudate.domain.model.tablature.TabBarline.SINGLE -> "Barra simple"
        com.grafitto.laudate.domain.model.tablature.TabBarline.DOUBLE -> "Barra doble"
        com.grafitto.laudate.domain.model.tablature.TabBarline.FINAL -> "Barra final"
    }

/**
 * Botón de una **marca del manuscrito**: solo el glifo, sin texto. El nombre va en el *tooltip*, y el
 * color avisa de si la marca está activa. Es el mismo gesto que los símbolos musicales del editor.
 *
 * [enabled] `false` lo deja visible pero atenuado y sin responder: se usa para el trémolo cuando no
 * hay casilla seleccionada (es una marca **de casilla**), de modo que el botón no desaparece y el
 * usuario entiende que existe y qué le falta para usarlo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkGlyphButton(
    glyph: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.height(Density.PillHeight),
            shape = RoundedCornerShape(Density.CornerSmall),
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        ) {
            Box(
                modifier = Modifier.padding(horizontal = Density.Compact),
                contentAlignment = Alignment.Center
            ) {
                val isMusicGlyph = glyph.length == 1 && glyph[0].code in 0xE000..0xF8FF
                Text(
                    text = glyph,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = if (isMusicGlyph) {
                        com.grafitto.laudate.ui.tablature.MusicFont.family()
                    } else {
                        FontFamily.Monospace
                    },
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Botón de una **técnica** de la paleta: dibuja su **símbolo vectorial** (ligadura, bend, vibrato,
 * armónico…) o, si la técnica es puramente textual (`H`/`P`, `↓`/`↑`), su glifo corto. El nombre va en
 * el *tooltip*. Se resalta cuando es la técnica activa de la cuerda.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TechniqueGlyphButton(
    technique: TabTechnique,
    glyph: TabGlyphKind?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(techniqueLabel(technique)) } },
        state = rememberTooltipState()
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .width(34.dp)
                .height(Density.PillHeight),
            shape = RoundedCornerShape(Density.CornerSmall),
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (glyph != null) {
                    TabTechniqueGlyph(
                        kind = glyph,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        size = 15.dp
                    )
                } else {
                    Text(
                        text = techniqueGlyph(technique),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = if (hasArrow(techniqueGlyph(technique))) {
                            MusicFont.textFamily()
                        } else {
                            FontFamily.Monospace
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Glifo de la marca de **repetición** según su estado (`—` = sin marca). */
private fun repeatGlyph(repeat: com.grafitto.laudate.domain.model.tablature.TabRepeatMark?): String = when (repeat) {
    null -> "—"
    com.grafitto.laudate.domain.model.tablature.TabRepeatMark.START -> "|:"
    com.grafitto.laudate.domain.model.tablature.TabRepeatMark.END -> ":|"
    com.grafitto.laudate.domain.model.tablature.TabRepeatMark.BOTH -> "|: :|"
}

/** Tonalidades ofrecidas en el selector de tonalidad escrita de la tablatura. */
private val TAB_KEYS = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

/** Tonalidad mayor cuya tónica coincide con el nombre dado (notación anglosajona). */
private fun scoreKeyOfName(name: String): com.grafitto.laudate.domain.model.score.ScoreKey =
    com.grafitto.laudate.domain.model.music.PitchClass.parse(name)
        ?.let { com.grafitto.laudate.domain.model.score.scoreKeyOfTonic(it.chromaticIndex) }
        ?: com.grafitto.laudate.domain.model.score.ScoreKey(0)

/** Nombre anglosajón de la tónica de la tonalidad escrita (con bemoles para las armaduras negativas). */
private fun com.grafitto.laudate.domain.model.score.ScoreKey.tonicEnglishName(): String {
    val majorIndex = (((fifths * 7) % 12) + 12) % 12
    val tonicIndex = if (isMinor) ((majorIndex - 3) % 12 + 12) % 12 else majorIndex
    return when (tonicIndex) {
        0 -> "C"; 1 -> "C#"; 2 -> "D"; 3 -> "Eb"; 4 -> "E"; 5 -> "F"
        6 -> "F#"; 7 -> "G"; 8 -> "Ab"; 9 -> "A"; 10 -> "Bb"; else -> "B"
    }
}

@Composable
private fun GridIconButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(28.dp),
        shape = RoundedCornerShape(Density.CornerSmall),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = description, modifier = Modifier.size(16.dp))
        }
    }
}

/**
 * **Mapa de teclado del editor**, paridad con MuseScore Studio 4 (ADR-020).
 *
 * Cada tecla está **verificada** contra el manual (`appendix/all-keyboard-shortcuts.md`, sección
 * *Note input ▸ Tablature*) y **no se inventa nada**: los 13 atajos de técnica por letra que había
 * (`x h p s m v t u l`, `,`, `<`, `>`) se **retiraron** porque MuseScore no los tiene y porque chocaban
 * con `A`–`K` (trastes), `Q`/`W` (duración), `S` (ligadura de expresión) y `X` (voltear).
 *
 * Resumen del mapa:
 *
 *  - **`N`** entra/sale del **modo de entrada**; **`Esc`** sale y deselecciona.
 *  - **`←`/`→`** recorren la partitura; **`↑`/`↓`** mueven el cursor **entre cuerdas** (modo entrada) o
 *    **suben/bajan el traste** de la nota (modo normal), como MuseScore.
 *  - **`0`–`9`** y **`A`–`K`** (sin `I`) escriben el traste.
 *  - **`;`** inserta un silencio; **`Q`/`W`** acortan/alarga la duración; **`Shift`+`0`..`9`** fijan la
 *    duración; **`.`** alterna el puntillo; **`Shift`+`X`** alterna la nota fantasma.
 *  - **`Ctrl`+`↑`/`↓`** mueve la nota a la cuerda contigua **sin cambiar la altura**;
 *    **`Alt`+`Shift`+`↑`/`↓`** sube/baja el traste **cambiando de cuerda**.
 *  - **`Ctrl`+`Z`/`Y`** deshacen/rehacen; **`Ctrl`+`C`/`X`/`V`** copian/cortan/pegan.
 *  - **`Espacio`** reproduce/pausa (la reproducción real llega en la Fase 5; hoy no hace nada).
 *  - **`Return`** ya **no** inserta columnas (en MuseScore es un **salto de sistema**).
 */
private fun handleTabKeyEvent(
    event: KeyEvent,
    onIntent: (TablatureUiIntent) -> Unit,
    uiState: TablatureUiState,
    onPageScroll: ((Int) -> Unit)? = null
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    val shift = event.isShiftPressed
    val ctrl = event.isCtrlPressed
    val alt = event.isAltPressed
    val isInputMode = uiState.isInputMode
    val hasNoteSelection = uiState.noteSelection?.isEmpty == false
    val cursorMeasure = uiState.selectedSlot?.first ?: 0
    val measureCount = uiState.editorMeasures.size
    // Rango sobre el que actúan los comandos de **estructura**: la selección de compases o, si no
    // hay selección, el compás del cursor. Es el mismo criterio que MuseScore, donde los comandos de
    // compás actúan sobre la selección y, sin ella, sobre el compás actual.
    val targetRange = uiState.selection?.range ?: (cursorMeasure..cursorMeasure)
    // Cuántos compases abarca la selección: es lo que usa `Alt+Mayús+B` para saber cuántos añadir.
    val selectionMeasureCount = targetRange.last - targetRange.first + 1

    // ── Ctrl+Mayús: selección de compases y vaciado (MuseScore) ────────────────────────────────────
    if (ctrl && shift) {
        when (event.key) {
            // `select-prev-measure` / `select-next-measure` de MuseScore.
            Key.DirectionLeft -> { onIntent(TablatureUiIntent.ExtendMeasureSelection(-1)); return true }
            Key.DirectionRight -> { onIntent(TablatureUiIntent.ExtendMeasureSelection(1)); return true }
            // `select-begin-score` / `select-end-score`.
            Key.MoveHome -> { onIntent(TablatureUiIntent.SelectMeasureAt(0)); return true }
            Key.MoveEnd -> {
                if (measureCount > 0) onIntent(TablatureUiIntent.SelectMeasureAt(measureCount - 1))
                return true
            }
            // `full-measure-rest`: deja el compás completo con su silencio, sin borrarlo.
            Key.Delete, Key.Backspace -> {
                onIntent(TablatureUiIntent.ClearMeasures(targetRange)); return true
            }
            else -> Unit
        }
    }

    // ── Alt+↑/↓: mueve el compás (o el bloque) un puesto atrás/adelante (MuseScore) ────────────────
    if (alt && !shift) {
        when (event.key) {
            // **Alt+Supr**: quita la **columna** del cursor. Es una operación que MuseScore **no**
            // tiene (allí la unidad de edición es la nota, no la columna), así que se queda en un
            // atajo propio y `Ctrl+Supr` vuelve a ser el de MuseScore (eliminar compases).
            Key.Delete -> { onIntent(TablatureUiIntent.RemoveColumnAtCursor); return true }
            Key.DirectionUp -> {
                onIntent(TablatureUiIntent.MoveMeasures(targetRange, targetRange.first - 1))
                return true
            }
            Key.DirectionDown -> {
                onIntent(TablatureUiIntent.MoveMeasures(targetRange, targetRange.first + 1))
                return true
            }
            else -> Unit
        }
    }

    // ── Atajos con Ctrl (E2/E3, portapapeles, mover cuerda) ────────────────────────────────────────
    if (ctrl) {
        when (event.key) {
            Key.Z -> {
                onIntent(if (shift) TablatureUiIntent.Redo else TablatureUiIntent.Undo)
                return true
            }
            Key.Y -> { onIntent(TablatureUiIntent.Redo); return true }
            // **Ctrl+A**: selecciona **todo** (MuseScore). Si hay seleccion de compases se extiende a
            // las notas del documento; si no, se seleccionan las notas enteras.
            Key.A -> { onIntent(TablatureUiIntent.SelectAllNotes); return true }
            Key.C -> {
                // Con **notas** seleccionadas se copian ellas; si no, los **compases**.
                onIntent(
                    if (hasNoteSelection) TablatureUiIntent.CopySelectedNotes
                    else TablatureUiIntent.CopySelection
                )
                return true
            }
            Key.X -> {
                onIntent(
                    if (hasNoteSelection) TablatureUiIntent.CutSelectedNotes
                    else TablatureUiIntent.CutSelection
                )
                return true
            }
            Key.V -> {
                onIntent(
                    if (hasNoteSelection) TablatureUiIntent.PasteNotes
                    else TablatureUiIntent.PasteClipboard
                )
                return true
            }
            Key.D -> { onIntent(TablatureUiIntent.DuplicateSelection); return true }
            // **Ctrl+S**: guarda la tablatura (`file-save` de MuseScore).
            Key.S -> { onIntent(TablatureUiIntent.SaveTablature); return true }
            // **Ctrl+B**: añade un compás al final (`append-measure` de MuseScore).
            Key.B -> { onIntent(TablatureUiIntent.AddMeasure); return true }
            // **Ctrl+3/5/6/7/9**: grupo irregular de esa proporción (`tuplet3`…`tuplet9` de
            // MuseScore). Antes solo existía el tresillo y ni siquiera estaba enlazado.
            Key.Three, Key.Five, Key.Six, Key.Seven, Key.Nine -> {
                val preset = when (event.key) {
                    Key.Three -> TabTuplet.TRIPLET
                    Key.Five -> TabTuplet.QUINTUPLET
                    Key.Six -> TabTuplet.SEXTUPLET
                    Key.Seven -> TabTuplet.SEPTUPLET
                    else -> TabTuplet.NONUPLET
                }
                onIntent(
                    TablatureUiIntent.SetTupletGroupAtCursor(preset.numNotes, preset.normalNotes)
                )
                return true
            }
            // **Ctrl+Ins**: inserta **tantos compases como haya en la selección**, delante de ella
            // (`insert-measures` de MuseScore, `shortcuts.json:197`). Antes insertaba **uno detrás**
            // del cursor, que no es lo que hace MuseScore.
            Key.Insert -> {
                onIntent(
                    TablatureUiIntent.InsertMeasuresBefore(targetRange.first, selectionMeasureCount)
                )
                return true
            }
            // **Ctrl+`+`/`-`/`0`**: zoom del lienzo (`zoomin`/`zoomout`/`zoom100` de MuseScore).
            Key.Equals, Key.Plus, Key.NumPadAdd -> { onIntent(TablatureUiIntent.ZoomIn); return true }
            Key.Minus, Key.NumPadSubtract -> { onIntent(TablatureUiIntent.ZoomOut); return true }
            Key.Zero, Key.NumPad0 -> { onIntent(TablatureUiIntent.ZoomReset); return true }
            // **Ctrl+←/→**: compás anterior / siguiente (`notation-move-left-quickly` /
            // `notation-move-right-quickly` de MuseScore, que avanzan de compás en compás).
            Key.DirectionLeft -> { onIntent(TablatureUiIntent.MoveCursor(-1, 0, 0)); return true }
            Key.DirectionRight -> { onIntent(TablatureUiIntent.MoveCursor(1, 0, 0)); return true }
            // **Ctrl+Inicio/Fin**: principio / final de la partitura. Mueven el **cursor**, no la
            // selección (la selección de compases es `Ctrl+Mayús+Inicio/Fin`).
            Key.MoveHome -> { onIntent(TablatureUiIntent.GoToMeasure(0)); return true }
            Key.MoveEnd -> {
                if (measureCount > 0) onIntent(TablatureUiIntent.GoToMeasure(measureCount - 1))
                return true
            }
            // **Ctrl+↑/↓** (TAB): mueve la nota a la **cuerda** contigua sin cambiar su altura.
            Key.DirectionUp -> { onIntent(TablatureUiIntent.MoveNoteString(-1)); return true }
            Key.DirectionDown -> { onIntent(TablatureUiIntent.MoveNoteString(1)); return true }
            // **Ctrl+Supr**: **elimina los compases** seleccionados (`delete-measures` de MuseScore).
            // Antes quitaba la **columna** del cursor, que es otra cosa: el usuario que pulsa
            // `Ctrl+Supr` espera que el compás desaparezca, no que se le vacíe un pulso.
            Key.Delete -> { onIntent(TablatureUiIntent.DeleteMeasures(targetRange)); return true }
            // **Ctrl+Mayús+X**: intercambia la selección con el portapapeles (`edit-swap`).
            Key.X -> { onIntent(TablatureUiIntent.SwapSelection); return true }
            // **Ctrl+Alt+1..4**: voz activa (MuseScore). Cambia el color del cursor.
            Key.One, Key.Two, Key.Three, Key.Four -> {
                if (alt) {
                    val voice = when (event.key) {
                        Key.One -> 1; Key.Two -> 2; Key.Three -> 3; else -> 4
                    }
                    onIntent(TablatureUiIntent.SetActiveVoice(voice)); return true
                }
            }
            else -> Unit
        }
    }

    // ── Alt+Shift+↑/↓: sube/baja el traste **cambiando de cuerda** si hace falta (MuseScore) ───────
    if (alt && shift) {
        when (event.key) {
            Key.DirectionUp -> { onIntent(TablatureUiIntent.NudgeFretAcrossStrings(-1)); return true }
            Key.DirectionDown -> { onIntent(TablatureUiIntent.NudgeFretAcrossStrings(1)); return true }
            // **Alt+Mayús+B**: añade compases al final (`append-measures` de MuseScore). MuseScore
            // abre un diálogo para pedir cuántos; aquí se añaden tantos como compases tenga la
            // selección —así el gesto sirve para «añadir otra frase como esta»— y uno si no hay
            // selección de varios compases.
            Key.B -> {
                onIntent(TablatureUiIntent.AddMeasures(selectionMeasureCount.coerceAtLeast(1)))
                return true
            }
            else -> Unit
        }
    }

    // ── Shift+0..9: fijan la **duración** de la nota (TAB), como MuseScore ─────────────────────────
    if (shift) {
        val code = event.utf16CodePoint
        val figure = TabShortcuts.shiftDigitFigure(code.toChar())
        if (figure != null && code in '0'.code..'9'.code) {
            onIntent(TablatureUiIntent.SetFigureForCursor(figure)); return true
        }
    }

    when (event.key) {
        // **RePág/AvPág**: desplazan la **vista** una pantalla, sin mover el cursor. Es lo que hacen
        // en MuseScore (`notation-move-up`/`-down` con `PgUp`/`PgDn`): el cursor se queda donde está
        // y lo que cambia es lo que se ve. Van **sin modificador**, como en MuseScore; `Home`/`End`
        // sí mueven el cursor, así que las dos parejas no se pisan.
        Key.PageUp -> { onPageScroll?.invoke(-1); return true }
        Key.PageDown -> { onPageScroll?.invoke(1); return true }
        // **Mayús+flecha**: extiende la selección de notas en **las dos dimensiones**, como el
        // *rubber band* de MuseScore. `←`/`→` recorren el tiempo y `↑`/`↓` añaden o quitan cuerdas,
        // así que se puede seleccionar un acorde entero o un pasaje de varias cuerdas sin ratón. La
        // selección **decrece** al volver sobre los pasos porque el rectángulo se recalcula entre el
        // ancla y el foco, no se acumula.
        Key.DirectionLeft -> {
            onIntent(
                if (shift) TablatureUiIntent.ExtendNoteSelectionRect(-1, 0)
                else TablatureUiIntent.MoveCursor(0, -1, 0)
            )
            return true
        }
        Key.DirectionRight -> {
            onIntent(
                if (shift) TablatureUiIntent.ExtendNoteSelectionRect(1, 0)
                else TablatureUiIntent.MoveCursor(0, 1, 0)
            )
            return true
        }
        // **↑/↓**: con **Mayús** extienden la selección a la cuerda contigua; en **modo entrada**
        // mueven el cursor **entre cuerdas**; en **modo normal** suben o bajan el **traste** de la
        // nota sin cambiar de cuerda (MuseScore).
        Key.DirectionUp -> {
            onIntent(
                when {
                    shift -> TablatureUiIntent.ExtendNoteSelectionRect(0, -1)
                    isInputMode -> TablatureUiIntent.MoveCursor(0, 0, -1)
                    else -> TablatureUiIntent.NudgeFret(-1)
                }
            )
            return true
        }
        Key.DirectionDown -> {
            onIntent(
                when {
                    shift -> TablatureUiIntent.ExtendNoteSelectionRect(0, 1)
                    isInputMode -> TablatureUiIntent.MoveCursor(0, 0, 1)
                    else -> TablatureUiIntent.NudgeFret(1)
                }
            )
            return true
        }
        // **Esc**: sale del modo de entrada y deselecciona.
        Key.Escape -> { onIntent(TablatureUiIntent.LeaveInputMode); return true }
        // **Espacio**: reproducir/pausar (MuseScore). Hoy no hay reproducción (Fase 5).
        Key.Spacebar -> { onIntent(TablatureUiIntent.PlaybackToggle); return true }
        Key.Backspace -> { onIntent(TablatureUiIntent.BackspaceCursor); return true }
        // **Intro**: salto de sistema (`system-break` de MuseScore). El compás del cursor empieza una
        // fila nueva aunque quepa en la anterior; se alterna con el mismo atajo.
        // **Ctrl+Intro**: salto de **página** (`page-break` de MuseScore), que en el PDF obliga a
        // empezar hoja. Implica el de sistema.
        Key.Enter, Key.NumPadEnter -> {
            onIntent(
                if (ctrl) TablatureUiIntent.TogglePageBreak(cursorMeasure)
                else TablatureUiIntent.ToggleSystemBreak(cursorMeasure)
            )
            return true
        }
        // **Ins**: inserta un compás **delante** del cursor (`insert-measure` de MuseScore).
        Key.Insert -> {
            onIntent(TablatureUiIntent.InsertMeasureBefore(cursorMeasure)); return true
        }
        // **Supr**: con **notas** seleccionadas borra la seleccion; sin ella, la nota del cursor.
        Key.Delete -> {
            onIntent(
                if (hasNoteSelection) TablatureUiIntent.DeleteSelectedNotes
                else TablatureUiIntent.DeleteAtCursor
            )
            return true
        }
        // **F8 / F9**: muestran u ocultan el panel de **propiedades** y el de **paletas**, como en
        // MuseScore Studio 4 (`toggle-properties` / `toggle-palette`).
        Key.F8 -> { onIntent(TablatureUiIntent.ToggleProperties); return true }
        Key.F9 -> { onIntent(TablatureUiIntent.TogglePalettes); return true }
        else -> Unit
    }

    val code = event.utf16CodePoint
    // **`N`**: entra/sale del modo de entrada de notas (ADR-020).
    if (!shift && !ctrl && !alt && (code == 'n'.code || code == 'N'.code)) {
        onIntent(TablatureUiIntent.ToggleInputMode); return true
    }
    return when {
        // **`0`–`9`**: escriben el traste. Con el modo apagado, la primera cifra entra en el modo.
        code in '0'.code..'9'.code -> {
            onIntent(TablatureUiIntent.TypeFretDigit(code - '0'.code)); true
        }
        // **`A`–`K`** (sin `I`): trastes 0–9, como la tablatura francesa de MuseScore.
        !shift && !ctrl && !alt && TabShortcuts.fretLetterDigit(code.toChar()) != null -> {
            onIntent(TablatureUiIntent.TypeFretDigit(TabShortcuts.fretLetterDigit(code.toChar())!!)); true
        }
        // **`;`**: inserta un silencio de la duración activa.
        code == ';'.code -> { onIntent(TablatureUiIntent.InsertRestAtCursorShortcut); true }
        // **`Q`/`W`**: acorta/alarga la duración de la nota del cursor.
        code == 'q'.code || code == 'Q'.code -> { onIntent(TablatureUiIntent.AdjustFigure(-1)); true }
        code == 'w'.code || code == 'W'.code -> { onIntent(TablatureUiIntent.AdjustFigure(1)); true }
        // **`R`**: repite la selección (`repeat-sel` de MuseScore): duplica lo seleccionado detrás.
        !shift && !ctrl && !alt && (code == 'r'.code || code == 'R'.code) -> {
            onIntent(TablatureUiIntent.DuplicateSelection); true
        }
        // **`T`**: ligadura de prolongación (`tie` de MuseScore). Une la nota del cursor con la
        // siguiente de la misma cuerda; solo si tienen el mismo traste.
        !ctrl && !alt && (code == 't'.code || code == 'T'.code) -> {
            val slot = uiState.selectedSlot
            if (slot != null) {
                onIntent(TablatureUiIntent.ToggleTie(slot.first, slot.second, uiState.selectedString))
                true
            } else {
                false
            }
        }
        // **`S`**: ligadura de expresión (`slur` de MuseScore). Une la casilla del cursor con la
        // siguiente con sonido; no exige que las alturas coincidan (eso es `T`).
        !shift && !ctrl && !alt && (code == 's'.code || code == 'S'.code) -> {
            val slot = uiState.selectedSlot
            if (slot != null) {
                onIntent(TablatureUiIntent.ToggleSlur(slot.first, slot.second))
                true
            } else {
                false
            }
        }
        // **`/`**: nota de adorno (*grace note*, `acciaccatura` de MuseScore). Clona la nota más
        // aguda de la casilla del cursor como cifra **pequeña** a su izquierda. `Shift`+`/` quita el
        // último adorno, que es el deshacer natural del mismo gesto.
        code == '/'.code -> {
            val slot = uiState.selectedSlot
            if (slot != null) {
                onIntent(
                    if (shift) {
                        TablatureUiIntent.RemoveGrace(slot.first, slot.second)
                    } else {
                        TablatureUiIntent.AddGrace(
                            slot.first,
                            slot.second,
                            stringNumber = uiState.selectedString
                        )
                    }
                )
                true
            } else {
                false
            }
        }
        // **`.`**: alterna el puntillo de la figura del cursor.
        code == '.'.code -> { onIntent(TablatureUiIntent.ToggleDotAtCursor); true }
        // **`Shift`+`X`**: alterna la nota fantasma (*crosshead*).
        shift && (code == 'x'.code || code == 'X'.code) -> {
            onIntent(TablatureUiIntent.ToggleGhostNoteAtCursor); true
        }
        // **`Shift`+`V`/`O`/`S`/`N`**: articulaciones (sforzato, marcato, picado, tenuto), como
        // MuseScore (`shortcuts.json:135-138`). `Shift`+`V` es el **sforzato** —acento y picado en un
        // solo glifo—, no el acento suelto: el acento se elige en la paleta, igual que allí.
        shift && (code == 'v'.code || code == 'V'.code) -> {
            onIntent(TablatureUiIntent.ApplyArticulationAtCursor(TabArticulation.ACCENT_STACCATO)); true
        }
        shift && (code == 'o'.code || code == 'O'.code) -> {
            onIntent(TablatureUiIntent.ApplyArticulationAtCursor(TabArticulation.MARCATO)); true
        }
        shift && (code == 's'.code || code == 'S'.code) -> {
            onIntent(TablatureUiIntent.ApplyArticulationAtCursor(TabArticulation.STACCATO)); true
        }
        shift && (code == 'n'.code || code == 'N'.code) -> {
            onIntent(TablatureUiIntent.ApplyArticulationAtCursor(TabArticulation.TENUTO)); true
        }
        else -> false
    }
}

@Composable
private fun SectionPicker(uiState: TablatureUiState, onIntent: (TablatureUiIntent) -> Unit) {
    val current = uiState.availableSections.firstOrNull { it.id == uiState.editorSectionId }
    SettingsDropdownRow(
        icon = CanticoraIcons.ToolSections,
        label = "Sección",
        value = current?.label ?: "General (al final)",
        options = listOf(SettingsOption(NO_SECTION, "General (al final)")) +
            uiState.availableSections.map { SettingsOption(it.id, it.label) },
        selectedId = uiState.editorSectionId ?: NO_SECTION,
        onSelect = { id ->
            onIntent(TablatureUiIntent.SetEditorSection(if (id == NO_SECTION) null else id))
        }
    )
}

/** Id reservado de la opción «General (al final)» del desplegable de sección. */
private const val NO_SECTION = "__general__"

/**
 * **Rejilla del editor**: el **mismo pentagrama** que el visor ([TablatureCanvas]) más la capa de
 * interacción (cursor, selección y columna fantasma). Al compartir el pintor, lo que se escribe y lo
 * que se lee **no pueden diferir** (S1 del plan de paridad visual): antes había dos
 * implementaciones —una a mano con filas de Compose y otra en `Canvas`— y por eso nunca coincidían.
 *
 * La rejilla recibe el **foco del teclado físico**, para que las flechas, los dígitos y los atajos de
 * técnica actúen sin tener que tocar antes una casilla.
 */
@Composable
private fun TablatureEditorGrid(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    focusRequester: FocusRequester,
    hitTester: TabCanvasHitTester? = null,
    onFitScale: ((Float) -> Unit)? = null,
    onPageScroll: ((Int) -> Unit)? = null,
    /**
     * Aviso de que se ha **tocado la rejilla**. En táctil sirve para abrir el teclado numérico: sin
     * él, tocar una cifra solo movía el cursor y en una tableta sin teclado físico no había forma
     * de escribir el traste.
     */
    onGridTap: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    // Celda sobre la que se ha pedido el **menu contextual** (pulsacion larga o clic derecho): al
    // soltar se abre el `DropdownMenu` de esa celda. `null` = sin menu.
    var contextMenuHit by remember { mutableStateOf<TabCanvasHit?>(null) }
    // Compás cuyas **propiedades** se están editando (`null` = diálogo cerrado). Es el
    // `Propiedades de compás…` de MuseScore, reducido a lo que el modelo sabe de verdad.
    var propertiesMeasure by remember { mutableStateOf<Int?>(null) }
    // **Capas de interacción**: cursor (marco), selección de compases y la columna fantasma del
    // compás donde se escribe. Son guías de interfaz, no notación: la música va con la tinta única.
    val overlay = remember(
        uiState.selectedSlot,
        uiState.selectedString,
        uiState.selection,
        uiState.editorMeasures,
        uiState.isInputMode,
        uiState.activeVoice,
        uiState.noteSelection,
        uiState.playbackSlot,
        uiState.loopRange
    ) {
        TabCanvasOverlay(
            selection = uiState.selection?.range?.toSet() ?: emptySet(),
            cursor = uiState.selectedSlot,
            cursorString = uiState.selectedString,
            ghostMeasure = uiState.selectedSlot?.first,
            inputMode = uiState.isInputMode,
            voice = uiState.activeVoice,
            selectedNotes = uiState.noteSelection?.notes ?: emptySet(),
            playbackCursor = uiState.playbackSlot,
            loopRange = uiState.loopRange,
            chordIssues = uiState.chordIssues.keys
        )
    }
    // **Hoja sobre el escritorio** (D-10): MuseScore no pinta la partitura sobre el fondo de la
    // ventana, sino sobre una **página blanca** con sombra, flotando en un escritorio gris. Es la
    // diferencia visual más grande que quedaba: sin ella la tablatura parecía un formulario, no una
    // partitura. El escritorio va en `surfaceContainerHigh` y la hoja un tono por encima del fondo de
    // la app (`surfaceContainerLowest`), con los **márgenes de página** dentro (el pentagrama nunca
    // toca el borde del papel). El papel **sigue el tema**: en claro es claro y en oscuro es oscuro,
    // no un rectángulo blanco encajado en mitad de una pantalla oscura.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Density.Corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = TabPaperDeskPadding, vertical = Density.CardPadding)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(2.dp))
                // **El papel sigue el tema.** En oscuro es oscuro y la tinta es clara, para que el
                // editor sea de verdad modo oscuro (lo pidió el usuario). Antes estaba fijado en
                // blanco, que en modo oscuro era un rectángulo deslumbrante en mitad de la pantalla.
                .background(tabPaperColor())
                .padding(horizontal = TabPaperMargin, vertical = Density.Compact)
                .clickable(interactionSource = interactionSource, indication = null) {
                    runCatching { focusRequester.requestFocus() }
                }
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    handleTabKeyEvent(event, onIntent, uiState, onPageScroll)
                }
                .focusable()
        ) {
            TablatureCanvas(
                tablature = uiState.editorAsTablature(),
                // Etiqueta de prueba: permite medir cuánto se ha desplazado la hoja con RePág/AvPág
                // (D-30) sin depender de la geometría interna del lienzo.
                modifier = Modifier.testTag(TAB_CANVAS_TEST_TAG),
                scale = uiState.tablatureScale,
                // **Tamaño de las cifras** (el ajuste que faltaba): sin esto el editor dibujaba
                // siempre el número a `1f` y el control no tenía efecto.
                numberScale = uiState.tablatureNumberScale,
                options = uiState.editorStaffOptions,
                // **La tinta sigue el tema**, emparejada con el papel: en oscuro, símbolos claros
                // sobre fondo oscuro. Ver [tabPaperInk].
                inkColor = tabPaperInk(),
                overlay = overlay,
                onHit = { hit, modifiers ->
                    onGridTap?.invoke()
                    handleCanvasHit(hit, modifiers, onIntent, focusRequester, uiState)
                },
                onLongPress = { hit ->
                    // **Pulsacion larga / clic derecho**: abre el menu contextual de la celda, como MuseScore.
                    runCatching { focusRequester.requestFocus() }
                    contextMenuHit = hit
                },
                onDragStrings = { hit, delta ->
                    // **Arrastrar una cifra a otra cuerda** (MuseScore): mueve la nota [delta] cuerdas
                    // sin cambiar su altura y deja el cursor sobre ella.
                    onIntent(TablatureUiIntent.TapSlot(hit.measureIndex, hit.slotIndex, hit.stringNumber))
                    onIntent(TablatureUiIntent.DragNoteToAdjacentString(delta))
                },
                onRubberBand = { selection ->
                    // **Rectangulo de seleccion** (*rubber band*): arrastrar sobre un hueco vacio
                    // selecciona las notas que toca, como en MuseScore. El lienzo ya lo ha resuelto
                    // contra su geometria, asi que aqui solo se aplica.
                    runCatching { focusRequester.requestFocus() }
                    onIntent(TablatureUiIntent.SetNoteSelection(selection.notes))
                },
                // **Zoom** con `Ctrl`+rueda (escritorio) o pellizco (táctil), aplicado a la escala del
                // dibujo, como MuseScore (`Ctrl`+`+`/`-` y `Ctrl`+rueda). Se acota con las constantes
                // del Dominio ([TabNotationScale.MIN_ZOOM]/[TabNotationScale.MAX_ZOOM]): antes había
                // aquí un `0.25f..4f` escrito a mano que contradecía el rango real y dejaba al
                // deslizador y a la rueda en desacuerdo (D-60).
                onZoom = { factor ->
                    onIntent(
                        TablatureUiIntent.SetTablatureScale(
                            (uiState.tablatureScale * factor)
                                .coerceIn(TabNotationScale.MIN_ZOOM, TabNotationScale.MAX_ZOOM)
                        )
                    )
                },
                hitTester = hitTester,
                onFitScale = onFitScale
            )

            // **Menu contextual** de la celda (clic derecho o pulsacion larga), como el de MuseScore:
            // `Cortar`, `Copiar`, `Pegar`, `Vaciar la celda`, `Borrar la nota`, `Insertar columna`,
            // `Insertar compas`. Nada de botones de mentira: solo lo que se puede aplicar de verdad.
            contextMenuHit?.let { hit ->
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { contextMenuHit = null }
                ) {
                    val measureIndex = hit.measureIndex
                    val slotIndex = hit.slotIndex
                    val stringNumber = hit.stringNumber
                    val hasNote = uiState.editorMeasures.getOrNull(measureIndex)?.slots
                        ?.getOrNull(slotIndex)?.strings?.any { it.stringNumber == stringNumber } == true
                    DropdownMenuItem(
                        text = { Text("Cortar los compases seleccionados") },
                        enabled = uiState.selection != null,
                        onClick = { contextMenuHit = null; onIntent(TablatureUiIntent.CutSelection) }
                    )
                    DropdownMenuItem(
                        text = { Text("Copiar los compases seleccionados") },
                        enabled = uiState.selection != null,
                        onClick = { contextMenuHit = null; onIntent(TablatureUiIntent.CopySelection) }
                    )
                    DropdownMenuItem(
                        text = { Text("Pegar") },
                        enabled = uiState.hasClipboard,
                        onClick = { contextMenuHit = null; onIntent(TablatureUiIntent.PasteClipboard) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Borrar la nota de esta cuerda") },
                        enabled = hasNote,
                        onClick = {
                            contextMenuHit = null
                            onIntent(TablatureUiIntent.RemovePluck(measureIndex, slotIndex, stringNumber))
                        }
                    )
                    // **Ligadura de prolongación** (B-10): el `tie` de MuseScore, también con `T`.
                    val tied = uiState.editorMeasures.getOrNull(measureIndex)?.slots
                        ?.getOrNull(slotIndex)?.strings
                        ?.firstOrNull { it.stringNumber == stringNumber }?.tiedToNext == true
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (tied) "Quitar la ligadura de prolongación" else "Ligar con la nota siguiente"
                            )
                        },
                        enabled = hasNote,
                        onClick = {
                            contextMenuHit = null
                            onIntent(TablatureUiIntent.ToggleTie(measureIndex, slotIndex, stringNumber))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Vaciar la celda") },
                        enabled = uiState.editorMeasures.getOrNull(measureIndex)?.slots
                            ?.getOrNull(slotIndex)?.isEmpty == false,
                        onClick = {
                            contextMenuHit = null
                            onIntent(TablatureUiIntent.ClearSlot(measureIndex, slotIndex))
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Insertar columna antes") },
                        onClick = {
                            contextMenuHit = null
                            onIntent(TablatureUiIntent.InsertColumnAtCursor(measureIndex, (slotIndex - 1).coerceAtLeast(0)))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Insertar compas despues") },
                        onClick = {
                            contextMenuHit = null
                            onIntent(TablatureUiIntent.InsertMeasureAfter(measureIndex))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar el compas ${measureIndex + 1}") },
                        enabled = uiState.editorMeasureCount > 1,
                        onClick = {
                            contextMenuHit = null
                            onIntent(TablatureUiIntent.DeleteMeasure(measureIndex))
                        }
                    )
                    HorizontalDivider()
                    // ── **Comandos de compás** (B-8), los mismos que el menú `Compás` de MuseScore ──────
                    // Actúan sobre la **selección de compases** si la hay; si no, sobre el compás pulsado.
                    val target = uiState.selection?.range ?: (measureIndex..measureIndex)
                    val targetLabel = if (target.first == target.last) {
                        "el compás ${target.first + 1}"
                    } else {
                        "los compases ${target.first + 1}–${target.last + 1}"
                    }
                    DropdownMenuItem(
                        text = { Text("Vaciar $targetLabel") },
                        onClick = {
                            contextMenuHit = null
                            onIntent(TablatureUiIntent.ClearMeasures(target))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar $targetLabel") },
                        enabled = uiState.editorMeasureCount > target.count(),
                        onClick = {
                            contextMenuHit = null
                            onIntent(TablatureUiIntent.DeleteMeasures(target))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Insertar un compás antes del ${measureIndex + 1}") },
                        onClick = {
                            contextMenuHit = null
                            onIntent(TablatureUiIntent.InsertMeasureBefore(measureIndex))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Mover $targetLabel al sistema anterior") },
                        enabled = target.first > 0,
                        onClick = {
                            contextMenuHit = null
                            onIntent(TablatureUiIntent.MoveMeasures(target, (target.first - 1).coerceAtLeast(0)))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Mover $targetLabel al sistema siguiente") },
                        enabled = target.last < uiState.editorMeasures.lastIndex,
                        onClick = {
                            contextMenuHit = null
                            onIntent(TablatureUiIntent.MoveMeasures(target, (target.last + 1).coerceAtMost(uiState.editorMeasures.lastIndex)))
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (uiState.editorMeasures.getOrNull(measureIndex)?.forceSystemBreak == true) {
                                    "Quitar el salto de sistema del compás ${measureIndex + 1}"
                                } else {
                                    "Empezar un sistema nuevo en el compás ${measureIndex + 1}"
                                }
                            )
                        },
                        onClick = {
                            contextMenuHit = null
                            onIntent(TablatureUiIntent.ToggleSystemBreak(measureIndex))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Propiedades del compás ${measureIndex + 1}…") },
                        onClick = {
                            contextMenuHit = null
                            propertiesMeasure = measureIndex
                        }
                    )
                }
            }

            // **Propiedades de compás** (B-8): subdivisión de la rejilla y salto de sistema, que es lo que
            // el modelo guarda por compás. Se abre desde el menú contextual, como en MuseScore.
            propertiesMeasure?.let { index ->
                MeasurePropertiesDialog(
                    measureIndex = index,
                    uiState = uiState,
                    onIntent = onIntent,
                    onDismiss = { propertiesMeasure = null }
                )
            }
        }
    }
}

/**
 * Traduce una **pulsación del pentagrama** a los intents del editor. El pintor ya resolvió la celda
 * ([TabCanvasHit]) y los **modificadores** con los que se ha pulsado ([TabCanvasModifiers]), así que
 * aquí solo hay que decidir qué se hace:
 *
 *  - **Clic simple**: mueve el cursor (y en modo entrada escribe un `0`), como MuseScore.
 *  - **`Ctrl`+clic**: alterna la nota en la **selección de notas**.
 *  - **`Mayús`+clic**: **extiende** la selección de notas hasta esa celda.
 *  - **Clic en el margen del compás** (fuera de las cuerdas): **selecciona el compás entero**; con
 *    `Mayús`, **extiende la selección de compases** hasta él (B-7). Es el gesto de MuseScore para
 *    seleccionar compases con el ratón, y el que habilita los comandos de compás (vaciar, mover,
 *    eliminar) sin tocar el teclado.
 */
private fun handleCanvasHit(
    hit: TabCanvasHit,
    modifiers: TabCanvasModifiers,
    onIntent: (TablatureUiIntent) -> Unit,
    focusRequester: FocusRequester,
    uiState: TablatureUiState
) {
    runCatching { focusRequester.requestFocus() }
    // **Selección de compases** (B-7): pulsar en el margen del compás, fuera de las cuerdas.
    if (hit.isMeasureLevel) {
        val anchor = uiState.selection?.range?.first ?: uiState.selectedSlot?.first ?: hit.measureIndex
        if (modifiers.shift) {
            onIntent(TablatureUiIntent.SelectMeasureRange(anchor, hit.measureIndex))
        } else {
            onIntent(TablatureUiIntent.SelectMeasureAt(hit.measureIndex))
        }
        return
    }
    when {
        modifiers.ctrl -> onIntent(
            TablatureUiIntent.ToggleNoteSelection(hit.measureIndex, hit.slotIndex, hit.stringNumber)
        )
        modifiers.shift -> onIntent(
            TablatureUiIntent.ExtendNoteSelection(hit.measureIndex, hit.slotIndex, hit.stringNumber)
        )
        else -> {
            // Un clic simple **limpia** la selección de notas y mueve el cursor (modo entrada: escribe).
            if (uiState.noteSelection != null) onIntent(TablatureUiIntent.ClearNoteSelection)
            onIntent(TablatureUiIntent.TapSlot(hit.measureIndex, hit.slotIndex, hit.stringNumber))
        }
    }
}

/**
 * **Cromo de edición** de la tablatura: deshacer/rehacer y las operaciones de **estructura**
 * (compases y columnas). Vive **fuera** de la caja del pentagrama (S6), sin recuadros de color: la
 * partitura se queda solo con la música.
 *
 * **(2026-10-15)** Antes había aquí un botón `N` (modo de introducción de notas) y cuatro iconos
 * sueltos —`+`, `−`, `PlaylistAdd`, `PlaylistRemove`— que no decían qué hacían: dos de ellos
 * «añadían» cosas distintas y no había forma de **insertar un compás en medio**. Ahora el modo `N`
 * **no existe** (teclear siempre escribe y avanza) y las operaciones viven en dos menús
 * **etiquetados** que actúan sobre el compás y la columna del cursor: **Compás** y **Columna**.
 */
@Composable
private fun TablatureEditorChrome(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    gridFocus: FocusRequester,
    /** (D-45) Abre el diálogo de **propiedades del pentagrama** (`Staff/Part properties`). */
    onOpenStaffProperties: () -> Unit = {},
    /** (D-95) Abre la **ayuda contextual** del editor. */
    onOpenHelp: () -> Unit = {},
    /**
     * Vuelta al **listado** de tablaturas del canto. Se pinta como un icono de **lista** al principio
     * de la fila del cromo, compartiendo fila con deshacer y rehacer: no roba altura a la partitura.
     */
    onBackToList: (() -> Unit)? = null
) {
    fun focusGrid() = runCatching { gridFocus.requestFocus() }
    val measureIndex = (uiState.selectedSlot?.first ?: 0).coerceIn(0, uiState.editorMeasures.lastIndex.coerceAtLeast(0))
    val slotIndex = uiState.selectedSlot?.second

    // **Cromo con scroll horizontal** (petición del usuario: «en horizontal no puedo hacer scroll»).
    // La fila tiene más herramientas de las que caben en un móvil —lista, deshacer, rehacer, Compás,
    // Columna, selección, Pentágono, paletas, ayuda—, así que se desplaza en horizontal en vez de
    // recortarse por la derecha. En escritorio no cambia nada: cabe entera y no aparece la barra.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(end = Density.Snug),
        horizontalArrangement = Arrangement.spacedBy(Density.Tight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Vuelta al **listado** del canto: comparte fila con el resto del cromo. Es el primer icono
        // porque es la salida, y se separa con un filete para no confundirlo con deshacer.
        if (onBackToList != null) {
            GridIconButton(Icons.AutoMirrored.Filled.List, "Volver al listado de tablaturas", true) {
                onBackToList()
            }
            ToolbarSeparator()
        }
        GridIconButton(Icons.AutoMirrored.Filled.Undo, "Deshacer (Ctrl+Z)", uiState.canUndo) {
            onIntent(TablatureUiIntent.Undo)
        }
        GridIconButton(Icons.AutoMirrored.Filled.Redo, "Rehacer (Ctrl+Mayús+Z)", uiState.canRedo) {
            onIntent(TablatureUiIntent.Redo)
        }
        ToolbarSeparator()
        ChromeMenuButton(
            icon = Icons.Default.Add,
            label = "Compás",
            description = "Añadir, insertar, duplicar o eliminar compases"
        ) { dismiss ->
            ChromeMenuItem("Añadir compás al final") {
                onIntent(TablatureUiIntent.AddMeasure); dismiss(); focusGrid()
            }
            ChromeMenuItem("Insertar compás antes del ${measureIndex + 1}") {
                onIntent(TablatureUiIntent.InsertMeasureBefore(measureIndex)); dismiss(); focusGrid()
            }
            ChromeMenuItem("Insertar compás después del ${measureIndex + 1}") {
                onIntent(TablatureUiIntent.InsertMeasureAfter(measureIndex)); dismiss(); focusGrid()
            }
            ChromeMenuItem("Duplicar el compás ${measureIndex + 1}") {
                onIntent(TablatureUiIntent.DuplicateMeasure(measureIndex)); dismiss(); focusGrid()
            }
            ChromeMenuItem(
                text = "Eliminar el compás ${measureIndex + 1}",
                enabled = uiState.editorMeasureCount > 1
            ) {
                onIntent(TablatureUiIntent.DeleteMeasure(measureIndex)); dismiss(); focusGrid()
            }
            ChromeMenuDivider()
            // **Anacrusa** (*pickup*): el compás incompleto con el que arranca la pieza. Es una
            // propiedad del **primer** compás, como el `Measure::isAnacrusis()` de MuseScore, así que
            // la entrada solo aparece cuando el cursor está en él.
            if (measureIndex == 0) {
                val isPickup = uiState.editorMeasures.firstOrNull()?.isPickup == true
                ChromeMenuItem(
                    text = if (isPickup) "Quitar la anacrusa" else "Marcar el compás 1 como anacrusa",
                    selected = isPickup
                ) {
                    onIntent(TablatureUiIntent.TogglePickup); dismiss(); focusGrid()
                }
            }
            // **Indicación de compás** anclada al compás del cursor: la paleta *Indicaciones de
            // compás* de MuseScore. La indicación rige **desde** este compás hasta el siguiente
            // cambio, y el compás se reequilibra a su nueva capacidad (un 3/4 dentro de una pieza 4/4
            // dura 3 negras). Sin esta entrada, un compás de otra medida salía marcado como «corto» y
            // «Completar compases» le añadía un silencio de más.
            val currentTime = uiState.effectiveTimeAt(measureIndex)
            val hasTimeChange = uiState.editorMeasures.getOrNull(measureIndex)?.time != null
            Text(
                text = "Indicación de compás desde el ${measureIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
            )
            MEASURE_TIME_CHOICES.forEach { candidate ->
                ChromeMenuItem(
                    text = "${candidate.beats}/${candidate.beatType}",
                    selected = currentTime == candidate
                ) {
                    onIntent(TablatureUiIntent.SetMeasureTime(measureIndex, candidate))
                    dismiss(); focusGrid()
                }
            }
            if (hasTimeChange) {
                ChromeMenuItem(text = "Quitar el cambio de compás") {
                    onIntent(TablatureUiIntent.SetMeasureTime(measureIndex, null))
                    dismiss(); focusGrid()
                }
            }
            ChromeMenuDivider()
            // **Compás irregular** (`Measure::isIrregular()` de MuseScore): la duración **real** del
            // compás difiere a propósito de la nominal. Es lo que permite un compás de 3 negras en
            // medio de una pieza 4/4 sin que «Completar compases» lo rellene y sin que la barra de
            // estado lo marque en rojo para siempre.
            val isIrregular = uiState.editorMeasures.getOrNull(measureIndex)?.isIrregular == true
            ChromeMenuItem(
                text = if (isIrregular) {
                    "Volver a la duración normal del compás"
                } else {
                    "Compás irregular (duración libre)"
                },
                selected = isIrregular
            ) {
                onIntent(TablatureUiIntent.ToggleIrregular(measureIndex)); dismiss(); focusGrid()
            }
            ChromeMenuDivider()
            // **Subdivisión del compás** (punto 5 del usuario: «no tengo como cambiar si el compás va
            // a negras o a corcheas»). Es la **rejilla de trabajo**: cuántas casillas tiene el compás
            // y con qué figura se rellenan los silencios. Cambiarla **no** toca las notas escritas.
            Text(
                text = "Subdivisión del compás ${measureIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
            )
            ChromeMenuItem(
                text = "Negras (${TablatureEditing.slotsPerMeasure(uiState.editorTime, TabResolution.QUARTER)} casillas)",
                selected = uiState.cursorSubdivision == TabResolution.QUARTER
            ) {
                onIntent(TablatureUiIntent.SetMeasureSubdivision(measureIndex, TabResolution.QUARTER))
                dismiss(); focusGrid()
            }
            ChromeMenuItem(
                text = "Corcheas (${TablatureEditing.slotsPerMeasure(uiState.editorTime, TabResolution.EIGHTH)} casillas)",
                selected = uiState.cursorSubdivision == TabResolution.EIGHTH
            ) {
                onIntent(TablatureUiIntent.SetMeasureSubdivision(measureIndex, TabResolution.EIGHTH))
                dismiss(); focusGrid()
            }
            ChromeMenuItem(
                text = "Semicorcheas (${TablatureEditing.slotsPerMeasure(uiState.editorTime, TabResolution.SIXTEENTH)} casillas)",
                selected = uiState.cursorSubdivision == TabResolution.SIXTEENTH
            ) {
                onIntent(TablatureUiIntent.SetMeasureSubdivision(measureIndex, TabResolution.SIXTEENTH))
                dismiss(); focusGrid()
            }
        }
        ChromeMenuButton(
            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
            label = "Columna",
            description = "Añadir o quitar la columna del cursor"
        ) { dismiss ->
            ChromeMenuItem("Añadir columna") {
                onIntent(TablatureUiIntent.InsertColumnAtCursorNow); dismiss(); focusGrid()
            }
            ChromeMenuItem("Quitar la columna del cursor", enabled = slotIndex != null) {
                slotIndex?.let { onIntent(TablatureUiIntent.RemoveColumnAt(measureIndex, it)) }
                dismiss(); focusGrid()
            }
        }
        // **Notas** (Fase 2, ADR-020): las mismas acciones que MuseScore sobre una selección de notas.
        // Solo aparecen cuando hay notas seleccionadas: nada de botones de mentira.
        val selectedNotes = uiState.noteSelection?.size ?: 0
        if (selectedNotes > 0) {
            ToolbarSeparator()
            Text(
                text = "$selectedNotes nota${if (selectedNotes == 1) "" else "s"} seleccionada${if (selectedNotes == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            GridIconButton(Icons.Default.ContentCopy, "Copiar las notas seleccionadas") {
                onIntent(TablatureUiIntent.CopySelectedNotes); focusGrid()
            }
            GridIconButton(Icons.Default.ContentCut, "Cortar las notas seleccionadas") {
                onIntent(TablatureUiIntent.CutSelectedNotes); focusGrid()
            }
            GridIconButton(Icons.Default.Delete, "Borrar las notas seleccionadas") {
                onIntent(TablatureUiIntent.DeleteSelectedNotes); focusGrid()
            }
            GridIconButton(
                Icons.Default.Close,
                "Quitar la selección de notas"
            ) { onIntent(TablatureUiIntent.ClearNoteSelection); focusGrid() }
        }
        ToolbarSeparator()
        GridIconButton(
            icon = Icons.Default.CheckBox,
            description = if (uiState.selection == null) {
                "Seleccionar todo el documento"
            } else {
                "Quitar la selección de compases"
            }
        ) {
            if (uiState.selection == null) {
                onIntent(
                    TablatureUiIntent.SelectMeasureRange(
                        0,
                        uiState.editorMeasures.lastIndex.coerceAtLeast(0)
                    )
                )
            } else {
                onIntent(TablatureUiIntent.ClearSelection)
            }
        }
        GridIconButton(
            icon = Icons.Default.Delete,
            description = "Eliminar los compases seleccionados",
            enabled = uiState.selection != null
        ) { onIntent(TablatureUiIntent.DeleteSelection) }
        ToolbarSeparator()
        // (D-45) **Propiedades del pentagrama**: el `Staff/Part properties` de MuseScore. Es donde
        // viven los ajustes que hasta ahora **no tenían ninguna interfaz** —`noteValues`,
        // `rhythmRepeat`, `showRests`, `fretMarks`, `marksPlacement`, `lines`, la clave, el compás, la
        // cabecera y la dirección de la plica—, así que el usuario solo podía usar los valores por
        // defecto. En MuseScore se abre con clic derecho sobre el pentagrama; aquí además hay un menú
        // visible, porque en táctil no hay clic derecho.
        ChromeMenuButton(
            icon = Icons.Default.Settings,
            label = "Pentagrama",
            description = "Propiedades del pentagrama: cifras, ritmo, líneas y cabecera"
        ) { dismiss ->
            ChromeMenuItem("Propiedades del pentagrama…") {
                dismiss(); onOpenStaffProperties()
            }
            ChromeMenuItem("Datos de cuerda…") {
                dismiss(); onIntent(TablatureUiIntent.OpenStringDataDialog)
            }
            ChromeMenuDivider()
            Text(
                text = "Tipo de pentagrama",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
            )
            // Los tres presets de MuseScore (`stafftype.cpp:946-967`): `Simple`, `Common` y `Full`.
            // `Full` es `Common` con la blanca dibujada con barra inclinada (`TablatureMinimStyle`).
            ChromeMenuItem(
                text = "Simple (solo cifras)",
                selected = uiState.editorStaffOptions == TabStaffOptions.SIMPLE
            ) {
                onIntent(TablatureUiIntent.SetStaffOptions(TabStaffOptions.SIMPLE))
                dismiss(); focusGrid()
            }
            ChromeMenuItem(
                text = "Common (cifras, silencios y plicas)",
                selected = uiState.editorStaffOptions == TabStaffOptions.COMMON
            ) {
                onIntent(TablatureUiIntent.SetStaffOptions(TabStaffOptions.COMMON))
                dismiss(); focusGrid()
            }
            ChromeMenuItem(
                text = "Full (blanca rayada)",
                selected = uiState.editorStaffOptions == TabStaffOptions.FULL
            ) {
                onIntent(TablatureUiIntent.SetStaffOptions(TabStaffOptions.FULL))
                dismiss(); focusGrid()
            }
        }
        // **Sin interruptores de paletas ni de propiedades** (petición del usuario): el icono de
        // hamburguesa (mostrar/ocultar paletas, `F9`) y el **(i)** (propiedades de la nota, `F8`) no
        // decían qué hacían y el usuario los retiró. Las paletas y las propiedades se siguen
        // abriendo desde el **panel de herramientas** y desde el menú **Pentagrama**, así que no se
        // pierde ninguna función: lo que desaparece es el atajo duplicado.
        // (D-95) **Ayuda contextual**: en táctil no hay *hover*, así que los tooltips de los iconos
        // no se ven nunca. Este botón abre la explicación de lo que se puede hacer **desde el
        // estado actual** (compás, columna, cuerda, selección), no un manual genérico.
        ToolbarSeparator()
        GridIconButton(
            icon = Icons.Default.HelpOutline,
            description = "Ayuda del editor de tablatura"
        ) { onOpenHelp() }
    }
}

/**
 * **Ayuda contextual del editor de tablatura** (D-95).
 *
 * No es el manual: es la explicación de **lo que se puede hacer ahora mismo**, con el vocabulario de
 * la pantalla. Se abre desde el botón **?** del cromo y desde el botón de ayuda de la paleta, porque
 * en táctil no existe el *hover*: los tooltips de los iconos no llegan a verse nunca (D-62).
 *
 * El primer bloque **lee el estado** —compás, casilla, cuerda, traste y selección— para que el texto
 * no hable en abstracto; los demás explican las cuatro zonas donde se trabaja: la barra de
 * escritura, el cromo (compás y columna), las paletas de marcas y el panel de herramientas.
 */
@Composable
internal fun TablatureEditorHelpDialog(
    uiState: TablatureUiState,
    onDismiss: () -> Unit
) {
    EditorHelpDialog(title = "Ayuda del editor de tablatura", onDismiss = onDismiss) {
        TablatureEditorHelpContent(uiState)
    }
}

/**
 * Contenido de la ayuda del editor de tablatura, **sin** el diálogo que lo envuelve.
 *
 * Está separado para poder dibujarlo fuera de una ventana modal: en escritorio el diálogo es una
 * ventana aparte y las capturas de las tiendas solo pueden fotografiar la ventana principal.
 */
@Composable
fun TablatureEditorHelpContent(uiState: TablatureUiState) {
    val measureIndex = (uiState.selectedSlot?.first ?: 0)
        .coerceIn(0, uiState.editorMeasures.lastIndex.coerceAtLeast(0))
    val slotIndex = uiState.selectedSlot?.second
    val selectedNotes = uiState.noteSelection?.size ?: 0
    val selectedRange = uiState.selection?.range
    val where = buildString {
        append("Estás en el compás ").append(measureIndex + 1)
        if (slotIndex != null) append(", casilla ").append(slotIndex + 1)
        append(", cuerda ").append(uiState.selectedString)
        append(" (la 1 es la más aguda) y traste ").append(uiState.selectedFret).append(".")
        when {
            selectedNotes > 0 -> append(
                " Hay $selectedNotes nota${if (selectedNotes == 1) "" else "s"} seleccionada" +
                    "${if (selectedNotes == 1) "" else "s"}: los botones de nota actúan solo sobre " +
                    "ella${if (selectedNotes == 1) "" else "s"}."
            )
            selectedRange != null -> append(
                " Hay compases seleccionados (del ${selectedRange.first + 1} al " +
                    "${selectedRange.last + 1}): las operaciones de compás actúan sobre el bloque."
            )
            else -> append(
                " Pulsa una cifra de la rejilla para elegir la nota; mientras no haya ninguna, los " +
                    "botones que escriben sobre una nota están apagados a propósito."
            )
        }
    }
    HelpEntry(term = "Dónde estás", description = where)
    HelpEntry(
        term = "Escribir una nota",
        description = "Toca una casilla de la rejilla y teclea el traste: el número aparece en esa " +
            "cuerda y el cursor avanza. La rejilla es una cuadrícula de cuerdas (filas) por " +
            "columnas (eventos); las cuerdas se dibujan punteadas mientras editas y continuas en " +
            "la vista final. Con **Entrada de notas** encendida (botón o tecla `N`) tocar una " +
            "cuerda escribe un 0; con el modo apagado, tocar solo mueve el cursor, pero teclear " +
            "una cifra entra en el modo y escribe."
    )
    HelpEntry(
        term = "La barra de escritura (pegada a la partitura)",
        description = "**Reproducir** escucha la tablatura y **Metrónomo** enciende el clic. " +
            "Las **figuras** (redonda a semicorchea) fijan la duración de lo que escribas, con " +
            "**puntillo** y **tresillo** al lado; los **silencios** insertan una pausa de esa " +
            "figura; **Teclado** (solo táctil) abre el teclado numérico; **1–4** eligen la voz, " +
            "que se pinta de su color; y **+** abre los símbolos que se aplican a la nota del " +
            "cursor sin salir del modo de entrada."
    )
    HelpEntry(
        term = "Cromo: Compás y Columna",
        description = "**Compás** añade, inserta, duplica o elimina compases, marca la **anacrusa**, " +
            "cambia la **indicación de compás**, declara un compás **irregular** y elige la " +
            "**subdivisión** (negras, corcheas o semicorcheas). **Columna** añade o quita la " +
            "columna del cursor. Las flechas curvadas son **deshacer** y **rehacer**."
    )
    HelpEntry(
        term = "Marcas de la nota y del compás",
        description = "Con una cifra elegida, el panel de la derecha ofrece las **paletas**: " +
            "expresión (trémolo, vibrato, estiramiento, slide), ligados, mano derecha, duración, " +
            "armónicos, palanca, alteraciones, articulaciones, dinámicas, digitación, navegación, " +
            "saltos, tempo y texto. Los símbolos se **combinan** sobre la misma nota y se quitan " +
            "volviendo a pulsarlos. El interruptor **?/Nombres** de la cabecera escribe el nombre " +
            "debajo de cada símbolo; también puedes **arrastrar** un símbolo hasta una nota de la " +
            "rejilla."
    )
    HelpEntry(
        term = "Avisos de la rejilla",
        description = "Canticora **avisa, no corrige**: si una columna tiene dos notas en la misma " +
            "cuerda, una cuerda que el instrumento no tiene, un traste fuera del mástil, un palmo " +
            "de más de cuatro trastes o una cuerda apagada con traste, la cifra se marca en rojo y " +
            "el aviso dice qué pasa. La música que usa posiciones raras sigue siendo posible."
    )
    HelpEntry(
        term = "Propiedades del pentagrama y datos de cuerda",
        description = "El menú **Pentagrama** abre las propiedades (cifras con números o letras, " +
            "dónde se coloca la cifra, líneas continuas o cortadas, silencios, plica, clave TAB, " +
            "indicación de compás, cabecera en todos los sistemas, nombres de cuerda, números de " +
            "compás y el tipo de pentagrama Simple/Common/Full) y los **datos de cuerda** " +
            "(afinación y número de cuerdas)."
    )
    HelpEntry(
        term = "Atajos de teclado",
        description = "`0`–`9` y `A`–`K` (sin la `I`) escriben el traste; las flechas " +
            "izquierda y derecha recorren la partitura y las flechas arriba y abajo cambian de " +
            "cuerda; `Espacio` reproduce; `Return` abre un " +
            "**sistema** nuevo y `Ctrl+Return` una **página** nueva; `Mayús+Intro` inserta una " +
            "barra de compás; `Supr` borra y `Retroceso` corrige lo tecleado; `Q`/`W` alargan o " +
            "acortan la duración y `Mayús`+cifra la fija; `Ctrl+Z`/`Y` deshacen y rehacen; " +
            "`Ctrl+C`/`X`/`V` copian, cortan y pegan; `Esc` sale del modo de entrada. La lista " +
            "completa, con botones, está en **Atajos de teclado**, dentro del panel."
    )
    HelpEntry(
        term = "Escuchar y metrónomo",
        description = "La reproducción respeta tempo, repeticiones, casillas y saltos. El " +
            "metrónomo suena en cada **pulsación** del compás (la del denominador: negra en " +
            "4/4, corchea en 6/8) y acentúa la primera; su volumen, el acento y la subdivisión se " +
            "configuran en **Metrónomo**, en el panel de herramientas, y se recuerdan entre " +
            "sesiones."
    )
    HelpEntry(
        term = "Zoom y lectura",
        description = "La barra de estado tiene el desplegable de **zoom** (35 % a 200 %) y " +
            "**Ajustar al ancho**. En un ordenador, `Ctrl`+rueda acerca y aleja; en tableta, el " +
            "pellizco. Nada de esto cambia la tablatura: solo cómo se ve."
    )
    HelpEntry(
        term = "Guardar",
        description = "El botón **Guardar** guarda la tablatura en el canto. Puedes tener varias por canto y " +
            "asignar cada una a una **sección** (intro, estrofa, coro…), que se intercala junto a " +
            "su parte; las de «General» van al final. Si sales con cambios sin guardar, Canticora " +
            "te lo pregunta."
    )
}

/**
 * **Barra de escritura**: la botonera que hace falta para escribir, **pegada a la partitura** y
 * siempre visible (móvil y escritorio). Es la respuesta al «es caótica la forma de insertar compases
 * y notas»: antes la paleta de duración vivía en el panel de herramientas —en móvil, dentro de un
 * `ModalBottomSheet` que **tapaba la partitura**— y había que ir y volver entre el panel y la rejilla
 * para cada nota.
 *
 * Fila 1: figuras (la activa resaltada), puntillos, tresillo y silencios, como la barra de **entrada
 * de notas** de MuseScore. Fila 2: el botón del **modo de entrada** (`N`) y el estado de la escritura.
 *
 * **Sin** teclado `0`–`9` en la barra: MuseScore **no** lo tiene (el teclado numérico es del sistema).
 * En **táctil** se ofrece aparte, en una **hoja flotante** ([TablatureKeypadSheet]), que es una
 * desviación deliberada del ADR-020.
 */
@Composable
private fun TablatureWritingBar(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    gridFocus: FocusRequester,
    /** Estado del teclado numérico: lo controla el **panel** para que también lo abra la rejilla. */
    showKeypad: Boolean = false,
    onShowKeypad: () -> Unit = {}
) {
    fun focusGrid() = runCatching { gridFocus.requestFocus() }
    // **Figura activa efectiva** (B-15): la elegida o, si no hay, la de la casilla del cursor. Es la
    // única que se resalta, como la barra de duraciones de MuseScore.
    val figure = uiState.activeFigure
    val hasCursor = uiState.selectedSlot != null
    val isTouch = remember { isTouchPlatform() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(Density.CornerSmall)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight),
            verticalArrangement = Arrangement.spacedBy(Density.Tight)
        ) {
            // ── Fila 1: duración (figuras, puntillo, tresillo) y silencios ────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Density.Tight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FigureButton(RhythmFigure.WHOLE, figure == RhythmFigure.WHOLE, true) {
                    onIntent(TablatureUiIntent.SetRhythmicFigureAtCursor(RhythmFigure.WHOLE)); focusGrid()
                }
                FigureButton(RhythmFigure.HALF, figure == RhythmFigure.HALF, true) {
                    onIntent(TablatureUiIntent.SetRhythmicFigureAtCursor(RhythmFigure.HALF)); focusGrid()
                }
                FigureButton(RhythmFigure.QUARTER, figure == RhythmFigure.QUARTER, true) {
                    onIntent(TablatureUiIntent.SetRhythmicFigureAtCursor(RhythmFigure.QUARTER)); focusGrid()
                }
                FigureButton(RhythmFigure.EIGHTH, figure == RhythmFigure.EIGHTH, true) {
                    onIntent(TablatureUiIntent.SetRhythmicFigureAtCursor(RhythmFigure.EIGHTH)); focusGrid()
                }
                FigureButton(RhythmFigure.SIXTEENTH, figure == RhythmFigure.SIXTEENTH, true) {
                    onIntent(TablatureUiIntent.SetRhythmicFigureAtCursor(RhythmFigure.SIXTEENTH)); focusGrid()
                }
                FigureButton(RhythmFigure.DOTTED_HALF, figure == RhythmFigure.DOTTED_HALF, true) {
                    onIntent(TablatureUiIntent.SetRhythmicFigureAtCursor(RhythmFigure.DOTTED_HALF)); focusGrid()
                }
                FigureButton(RhythmFigure.DOTTED_QUARTER, figure == RhythmFigure.DOTTED_QUARTER, true) {
                    onIntent(TablatureUiIntent.SetRhythmicFigureAtCursor(RhythmFigure.DOTTED_QUARTER)); focusGrid()
                }
                FigureButton(RhythmFigure.DOTTED_EIGHTH, figure == RhythmFigure.DOTTED_EIGHTH, true) {
                    onIntent(TablatureUiIntent.SetRhythmicFigureAtCursor(RhythmFigure.DOTTED_EIGHTH)); focusGrid()
                }
                // **Grupos irregulares** (D-38): un chip por proporción, como el submenú *Crear
                // grupo irregular* de MuseScore. Antes solo existía el tresillo, así que un quintillo
                // o un septillo no se podían ni escribir. El chip se resalta si la casilla del cursor
                // pertenece a un grupo de **esa** proporción.
                val cursorTuplet = uiState.selectedSlot?.let { (m, s) ->
                    uiState.editorMeasures.getOrNull(m)?.slots?.getOrNull(s)?.tuplet
                }
                TabTuplet.PRESETS.forEach { preset ->
                    TupletButton(
                        selected = cursorTuplet == preset,
                        enabled = hasCursor,
                        label = preset.label,
                        tooltip = tupletTooltip(preset)
                    ) {
                        onIntent(
                            TablatureUiIntent.SetTupletGroupAtCursor(
                                preset.numNotes,
                                preset.normalNotes
                            )
                        )
                        focusGrid()
                    }
                }
                // **Ajustes del grupo irregular** (D-41): qué se rotula y si lleva corchete. Son
                // las dos propiedades que MuseScore expone en *Propiedades ▸ Grupo irregular*
                // (`TupletNumberType` y `TupletBracketType`). Solo aparecen cuando el cursor está
                // dentro de un grupo, porque fuera de él no hay nada que ajustar.
                if (cursorTuplet != null) {
                    TupletAppearanceButton(
                        tuplet = cursorTuplet,
                        onIntent = onIntent,
                        focusGrid = { focusGrid() }
                    )
                }
                ToolbarSeparator()
                // **Silencios**: insertan un silencio explícito en la columna del cursor. Un compás
                // se rellena con silencios; la cifra no puede dejar huecos.
                FigureButton(RhythmFigure.WHOLE_REST, figure == RhythmFigure.WHOLE_REST, true) {
                    onIntent(TablatureUiIntent.InsertRestAtCursor(RhythmFigure.WHOLE_REST)); focusGrid()
                }
                FigureButton(RhythmFigure.HALF_REST, figure == RhythmFigure.HALF_REST, true) {
                    onIntent(TablatureUiIntent.InsertRestAtCursor(RhythmFigure.HALF_REST)); focusGrid()
                }
                FigureButton(RhythmFigure.QUARTER_REST, figure == RhythmFigure.QUARTER_REST, true) {
                    onIntent(TablatureUiIntent.InsertRestAtCursor(RhythmFigure.QUARTER_REST)); focusGrid()
                }
                FigureButton(RhythmFigure.EIGHTH_REST, figure == RhythmFigure.EIGHTH_REST, true) {
                    onIntent(TablatureUiIntent.InsertRestAtCursor(RhythmFigure.EIGHTH_REST)); focusGrid()
                }
                FigureButton(RhythmFigure.SIXTEENTH_REST, figure == RhythmFigure.SIXTEENTH_REST, true) {
                    onIntent(TablatureUiIntent.InsertRestAtCursor(RhythmFigure.SIXTEENTH_REST)); focusGrid()
                }
            }

            // ── Fila 2: **modo de entrada** y estado, como la barra de MuseScore ──────────────────
            // MuseScore **no** tiene un teclado `0`–`9` en la barra (el teclado numérico es del
            // sistema). Aquí se ofrece el botón del **modo de entrada** (el `N` de MuseScore) y el
            // estado de la escritura; el teclado numérico se reserva para la **hoja flotante táctil**
            // de la pantalla de edición (desviación deliberada, ver TABLATURE_UX_PARITY.md).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Density.Tight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // **Transporte** (B-9): reproduce o para la partitura, como `Espacio` en MuseScore.
                // Mientras suena, el botón se enciende y el lienzo sigue la columna que va sonando.
                Surface(
                    onClick = { onIntent(TablatureUiIntent.PlaybackToggle); focusGrid() },
                    shape = RoundedCornerShape(Density.CornerSmall),
                    color = if (uiState.isPlaying) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    },
                    border = BorderStroke(
                        1.dp,
                        if (uiState.isPlaying) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    contentColor = if (uiState.isPlaying) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (uiState.isPlaying) {
                            "Parar la reproducción (Espacio)"
                        } else {
                            "Reproducir la partitura (Espacio)"
                        }
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Density.Tight),
                        modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
                    ) {
                        Text(
                            text = if (uiState.isPlaying) "\u25A0" else "\u25B6",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.isPlaying) "Parar" else "Reproducir",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                // **Metrónomo** (D-23): un clic por pulsación, con acento en la primera. Es un
                // interruptor independiente del transporte, como el botón de metrónomo de MuseScore.
                Surface(
                    onClick = { onIntent(TablatureUiIntent.ToggleMetronome); focusGrid() },
                    shape = RoundedCornerShape(Density.CornerSmall),
                    color = if (uiState.metronomeEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    },
                    border = BorderStroke(
                        1.dp,
                        if (uiState.metronomeEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    contentColor = if (uiState.metronomeEnabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (uiState.metronomeEnabled) {
                            "Metrónomo encendido. Pulsa para apagarlo"
                        } else {
                            "Encender el metrónomo"
                        }
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Density.Tight),
                        modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
                    ) {
                        Text(
                            text = "\u2669",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Metrónomo", style = MaterialTheme.typography.labelSmall)
                    }
                }
                // (D-89) **El botón de bucle se retira.** El usuario lo pidió: «no funciona bien y es
                // complejo». Repetía los compases seleccionados, pero la reproducción del editor es
                // un metrónomo de paso y el rango no se respetaba al cambiar de compás, así que el
                // botón prometía algo que no cumplía. Se quita del cromo; `ToggleLoopRange` y el
                // estado `loopRange` **se conservan** en el contrato porque la reproducción los
                // consulta, y volver a exponerlos es solo pintar de nuevo este botón.
                ToolbarSeparator()
                Surface(
                    onClick = { onIntent(TablatureUiIntent.ToggleInputMode); focusGrid() },
                    shape = RoundedCornerShape(Density.CornerSmall),
                    color = if (uiState.isInputMode) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    },
                    border = BorderStroke(
                        1.dp,
                        if (uiState.isInputMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    ),
                    contentColor = if (uiState.isInputMode) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.semantics {
                        contentDescription = if (uiState.isInputMode) {
                            "Modo de entrada activo. Pulsa para salir (tecla N)"
                        } else {
                            "Entrar en el modo de entrada de notas (tecla N)"
                        }
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Density.Tight),
                        modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
                    ) {
                        // (D-21) MuseScore 4 rotula el botón del modo de entrada con el icono
                        // `IconCode::Code::EDIT` (0xEF63, un **lápiz**), no con la letra `N`:
                        // `notationcommandsregister.cpp` registra `TOGGLE_NOTE_INPUT_BY_NOTE_NAME_COMMAND`
                        // con `Decoration(IconCode::Code::EDIT, Checkable::Yes)`. `0xEF63` es de la
                        // fuente de iconos de MuseScore, no de SMuFL, así que se usa el lápiz de
                        // Material (`edit_note`), que es el mismo símbolo.
                        Icon(
                            painter = painterResource(Res.drawable.edit_note),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (uiState.isInputMode) "Entrada" else "Entrada de notas",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                ToolbarSeparator()
                // En **táctil**, el teclado numérico de traste vive en una **hoja flotante** (MuseScore
                // no lo pone en la barra: es el teclado del sistema). Es una desviación deliberada.
                if (isTouch) {
                    Surface(
                        onClick = onShowKeypad,
                        shape = RoundedCornerShape(Density.CornerSmall),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics {
                            contentDescription = "Abrir el teclado numérico para escribir el traste"
                        }
                    ) {
                        Text(
                            text = "Teclado",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
                        )
                    }
                    ToolbarSeparator()
                }
                // **Sin botones de voces** (petición del usuario): se retiran de la barra. El modelo de
                // polifonía de 4 voces sigue en el estado y cada casilla conserva la suya, pero el
                // control visible desaparece mientras se escribe con una sola voz, que es el caso
                // normal. Con esto la barra de entrada se queda en: figuras, modo de entrada, teclado
                // y «+» de símbolos.
                ToolbarSeparator()
                // **Botón «+»** (D-31): el resto de símbolos **sin salir del modo de entrada**. En
                // MuseScore el `+` de la barra de entrada abre el desbordamiento de la botonera; aquí
                // abre un menú con los símbolos que se aplican a la **nota del cursor**, que es lo que
                // de verdad se echa en falta mientras se escribe: poner un armónico o un acento no
                // debería obligar a abrir el panel de paletas y perder el punto de escritura.
                MoreSymbolsButton(uiState, onIntent, ::focusGrid)
            }

            // **Sin fila de mezclador** (petición del usuario): ocupaba una tercera fila de la barra
            // de escritura —altura que se le quita a la partitura— y el usuario no la quiere. El
            // estado `mixer` y su intent siguen en el contrato porque la reproducción los consulta
            // (volumen y balance); lo que desaparece es el control visible.
        }
    }

    // **Hoja del teclado numérico**: la abre el botón **Teclado** y, en táctil, **tocar la rejilla**.
    // Se pinta en el [TablatureEditorPanel] para que sobreviva al scroll de la partitura.
}

/**
 * Botón `−`/`+` del **traste** en el panel de propiedades de la nota (D-32).
 *
 * Es un botón de texto y no un icono porque el signo **es** la información: `−` y `+` se leen sin
 * aprender un símbolo, y a este tamaño un icono de resta y uno de suma se confunden.
 */
@Composable
private fun FretStepButton(
    glyph: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Density.CornerSmall),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
        )
    }
}

/**
 * **Propiedades de la nota del cursor** (D-32): la pestaña «Nota» del panel de propiedades.
 *
 * El `F8` de MuseScore es **contextual** —enseña lo que se puede cambiar en la selección actual— y
 * tiene una pestaña por **nota** con cuerda, traste, digitación, voz y cabeza. Aquí el panel era del
 * **documento** (tonalidad, tamaño, instrumento), así que corregir una nota concreta obligaba a
 * borrarla y volver a escribirla: el traste mal escrito, el dedo equivocado o la voz cambiada no
 * tenían arreglo sin destruir la nota.
 *
 * Se edita lo que el modelo sabe de una nota y tiene un intent propio: **traste** (con `−`/`+`, que
 * respeta el rango `0..MAX_FRET`), **voz** (1–4, con el color de la voz), **dedo izquierdo** y
 * **púa/dedo derecho**. La **cuerda** y la **duración** se muestran pero no se editan aquí: mover una
 * nota de cuerda cambia su altura y su traste a la vez, y eso es una operación de la rejilla
 * (`Ctrl+↑/↓`), no un campo de texto.
 *
 * Sin nota en el cursor la sección **no se pinta**: un panel de propiedades vacío es ruido, y
 * MuseScore tampoco enseña la pestaña «Nota» cuando no hay nota seleccionada.
 */
@Composable
private fun NotePropertiesSection(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit
) {
    val selected = uiState.selectedSlot ?: return
    val measureIndex = selected.first
    val slotIndex = selected.second
    val measure = uiState.editorMeasures.getOrNull(measureIndex) ?: return
    val slot = measure.slots.getOrNull(slotIndex) ?: return
    val stringNumber = uiState.selectedString
    val pluck = slot.strings.lastOrNull { it.stringNumber == stringNumber } ?: return

    CollapsibleSection(
        title = "Nota",
        subtitle = "compás ${measureIndex + 1} · cuerda $stringNumber · traste ${pluck.fret}",
        // (D-84) **Plegada por defecto**, como todas las secciones del panel: el usuario pidió que
        // ningún bloque aparezca abierto de entrada. El subtítulo ya resume la nota del cursor, así
        // que plegada sigue siendo útil; desplegarla es un toque y entonces se edita el traste.
        initiallyExpanded = false
    ) {
        // ── Traste: `−`/`+` en vez de un campo de texto ───────────────────────────────────────
        // Un traste es un entero pequeño y acotado; con dos botones no hay que abrir el teclado ni
        // validar lo que se escribe, y el cambio se ve en la partitura al instante.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Density.Tight)
        ) {
            Text(
                text = "Traste",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            FretStepButton(
                glyph = "\u2212",
                description = "Bajar el traste de la nota",
                enabled = pluck.fret > 0
            ) {
                onIntent(
                    TablatureUiIntent.SetSlotFret(
                        measureIndex, slotIndex, stringNumber, pluck.fret - 1
                    )
                )
            }
            Text(
                text = pluck.fret.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(min = 28.dp),
                textAlign = TextAlign.Center
            )
            FretStepButton(
                glyph = "+",
                description = "Subir el traste de la nota",
                enabled = pluck.fret < MAX_FRET
            ) {
                onIntent(
                    TablatureUiIntent.SetSlotFret(
                        measureIndex, slotIndex, stringNumber, pluck.fret + 1
                    )
                )
            }
        }

        // ── Voz: el color es el mismo que lleva la nota en la partitura ───────────────────────
        // (D-75) El rótulo **no** lleva `weight(1f)`: lo empujaba todo a la derecha y dejaba los
        // botones de voz flotando en el extremo opuesto del panel, lejísimos de su etiqueta. Ahora
        // van pegados a ella y miden lo mismo que en la barra de escritura.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Density.Snug)
        ) {
            Text(
                text = "Voz",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = Density.Snug)
            )
            (1..TabVoiceColors.VOICE_COUNT).forEach { voice ->
                val active = slot.voice == voice
                val voiceColor = Color(TabVoiceColors.of(voice))
                Surface(
                    onClick = { onIntent(TablatureUiIntent.SetVoiceInRange(voice)) },
                    shape = RoundedCornerShape(Density.CornerSmall),
                    color = if (active) {
                        voiceColor.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest
                    },
                    border = BorderStroke(
                        1.dp,
                        if (active) voiceColor else MaterialTheme.colorScheme.outlineVariant
                    ),
                    contentColor = if (active) voiceColor else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .size(VoiceButtonSize)
                        .semantics {
                            contentDescription = "Poner la nota en la voz $voice"
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = voice.toString(),
                            // (D-77) `titleMedium` (16 sp) dejaba la cifra en 11 px dentro de una
                            // caja de 40 dp: el «simbolito en caja enorme» también aquí.
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // ── Digitación: los mismos dedos que la paleta, en un desplegable ─────────────────────
        SettingsDropdownRow(
            icon = CanticoraIcons.Fingering,
            label = "Dedo izquierdo",
            value = pluck.frettingFinger?.let { "Dedo ${it.number}" } ?: "Sin indicar",
            selectedId = pluck.frettingFinger?.name ?: NOTE_FINGER_NONE,
            options = listOf(SettingsOption(NOTE_FINGER_NONE, "Sin indicar")) +
                TabFrettingFinger.entries.map { SettingsOption(it.name, "Dedo ${it.number}") },
            onSelect = { id ->
                onIntent(
                    TablatureUiIntent.SetFrettingFinger(
                        measureIndex,
                        slotIndex,
                        stringNumber,
                        TabFrettingFinger.entries.firstOrNull { it.name == id }
                    )
                )
            }
        )
        SettingsDropdownRow(
            icon = CanticoraIcons.Fingering,
            label = "Púa / dedo derecho",
            value = pluck.pickingFinger?.let { "Dedo ${it.label}" } ?: "Sin indicar",
            selectedId = pluck.pickingFinger?.name ?: NOTE_FINGER_NONE,
            options = listOf(SettingsOption(NOTE_FINGER_NONE, "Sin indicar")) +
                TabPickingFinger.entries.map { SettingsOption(it.name, "Dedo ${it.label}") },
            onSelect = { id ->
                onIntent(
                    TablatureUiIntent.SetPickingFinger(
                        measureIndex,
                        slotIndex,
                        stringNumber,
                        TabPickingFinger.entries.firstOrNull { it.name == id }
                    )
                )
            }
        )

        // ── Lo que se **lee** pero no se edita aquí ───────────────────────────────────────────
        Text(
            text = buildString {
                append("Duración: ")
                append(slot.rhythmicFigure?.label ?: "la del compás")
                slot.tuplet?.let { append(" · "); append(tupletName(it)) }
                if (pluck.techniques.isNotEmpty()) {
                    append(" · ")
                    append(pluck.techniques.joinToString(", ") { techniqueLabel(it) })
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * **Adornos de la casilla del cursor** (D-63): la pestaña que faltaba para que un adorno deje de ser
 * un `0` diminuto e intocable.
 *
 * El problema no era el dibujo —el grabado de la acciaccatura y de la apoyatura ya era el de
 * MuseScore— sino que **no había forma de decir qué nota es el adorno**. `addGrace` lo creaba
 * clonando la nota más aguda de la casilla y, si la casilla estaba vacía, con `fret = 0`; a partir de
 * ahí el control de traste del panel editaba la nota **principal**, no el adorno, así que el `0` se
 * quedaba para siempre. En MuseScore un adorno es una nota más: se selecciona y se le cambia el
 * traste y la cuerda como a cualquier otra.
 *
 * Se edita **traste** (`−`/`+`, acotado a `0..MAX_FRET` en el Dominio) y **cuerda** (desplegable),
 * que son las dos cosas que sitúan el adorno en la rejilla. El **tipo** (acciaccatura/apoyatura) se
 * cambia desde la paleta, que es donde vive el símbolo.
 *
 * Sin adornos la sección **no se pinta**: igual que la de la nota, un panel vacío es ruido.
 */
@Composable
private fun GraceNotesSection(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit
) {
    val selected = uiState.selectedSlot ?: return
    val measureIndex = selected.first
    val slotIndex = selected.second
    val measure = uiState.editorMeasures.getOrNull(measureIndex) ?: return
    val slot = measure.slots.getOrNull(slotIndex) ?: return
    if (slot.graces.isEmpty()) return

    CollapsibleSection(
        title = "Adornos",
        subtitle = if (slot.graces.size == 1) {
            "1 adorno en el compás ${measureIndex + 1}"
        } else {
            "${slot.graces.size} adornos en el compás ${measureIndex + 1}"
        },
        // (D-84) Plegada por defecto, como el resto del panel.
        initiallyExpanded = false
    ) {
        slot.graces.forEachIndexed { graceIndex, grace ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Density.Tight)
            ) {
                Text(
                    text = "${graceIndex + 1}. ${grace.type.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                FretStepButton(
                    glyph = "\u2212",
                    description = "Bajar el traste del adorno ${graceIndex + 1}",
                    enabled = grace.fret > 0
                ) {
                    onIntent(
                        TablatureUiIntent.SetGraceFret(
                            measureIndex, slotIndex, graceIndex, grace.fret - 1
                        )
                    )
                }
                Text(
                    text = grace.fret.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.widthIn(min = 28.dp),
                    textAlign = TextAlign.Center
                )
                FretStepButton(
                    glyph = "+",
                    description = "Subir el traste del adorno ${graceIndex + 1}",
                    enabled = grace.fret < MAX_FRET
                ) {
                    onIntent(
                        TablatureUiIntent.SetGraceFret(
                            measureIndex, slotIndex, graceIndex, grace.fret + 1
                        )
                    )
                }
            }
            SettingsDropdownRow(
                icon = CanticoraIcons.Fingering,
                label = "Cuerda del adorno ${graceIndex + 1}",
                value = "Cuerda ${grace.stringNumber}",
                selectedId = grace.stringNumber.toString(),
                options = (1..uiState.editorTuning.stringCount).map {
                    SettingsOption(it.toString(), "Cuerda $it")
                },
                onSelect = { id ->
                    id.toIntOrNull()?.let { stringNumber ->
                        onIntent(
                            TablatureUiIntent.SetGraceString(
                                measureIndex, slotIndex, graceIndex, stringNumber
                            )
                        )
                    }
                }
            )
        }
    }
}

/** Id reservado de la opción «sin dedo indicado» de los desplegables de digitación. */
private const val NOTE_FINGER_NONE = "__none__"

/**
 * **Botón «+» de la barra de escritura** (D-31): el resto de símbolos **sin salir del modo de entrada**.
 *
 * En MuseScore 4 el `+` de la barra de entrada de notas abre el **desbordamiento** de la botonera: los
 * botones que no caben siguen a un toque y el modo de entrada **no** se abandona. Aquí el problema es
 * el mismo pero el contenido es otro: la botonera ya cabe (se desplaza), así que lo que falta a mano
 * mientras se escribe son los **símbolos de la nota** —articulaciones, técnicas, ligadura, trémolos,
 * dinámicas—, que hoy viven en el panel de paletas. Abrir ese panel para poner un armónico obliga a
 * soltar el punto de escritura y, en móvil, a tapar la partitura con una hoja.
 *
 * El menú es un `DropdownMenu` y **no** un `ModalBottomSheet` a propósito: el modal crea una raíz de
 * semántica nueva y rompe el arnés de render (`onRoot().captureToImage()`), además de tapar la
 * partitura. El menú se ancla al botón y deja ver la nota sobre la que se escribe.
 *
 * Los símbolos se agrupan por familia y **solo** se ofrecen los que se aplican a una nota existente:
 * sin nota en el cursor el botón está deshabilitado, porque una articulación sin nota no existe (es
 * la misma regla que `canEditTechnique` en el panel de paletas).
 */
@Composable
private fun MoreSymbolsButton(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    focusGrid: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = uiState.selectedSlot
    val measureIndex = selected?.first
    val slotIndex = selected?.second
    val stringNumber = uiState.selectedString
    val measure = measureIndex?.let { uiState.editorMeasures.getOrNull(it) }
    val activePluck = slotIndex
        ?.let { measure?.slots?.getOrNull(it) }
        ?.strings?.lastOrNull { it.stringNumber == stringNumber }
    val canEdit = measureIndex != null && slotIndex != null && activePluck != null

    Box {
        Surface(
            onClick = { expanded = true },
            enabled = canEdit,
            shape = RoundedCornerShape(Density.CornerSmall),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            contentColor = if (canEdit) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.semantics {
                contentDescription = if (canEdit) {
                    "Más símbolos para la nota del cursor"
                } else {
                    "Más símbolos: coloca el cursor sobre una nota"
                }
            }
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (canEdit && measureIndex != null && slotIndex != null) {
                MoreSymbolsMenu(
                    measureIndex = measureIndex,
                    slotIndex = slotIndex,
                    stringNumber = stringNumber,
                    activePluck = activePluck,
                    onIntent = onIntent,
                    onDismiss = { expanded = false },
                    focusGrid = focusGrid
                )
            }
        }
    }
}

/**
 * Contenido del menú del botón «+»: los símbolos de la **nota del cursor**, agrupados por familia.
 *
 * Se ofrecen las familias que se aplican a una nota suelta —articulaciones, ligadura, técnicas,
 * trémolos y dinámicas— y **no** las de compás (repeticiones, barras, tempo, texto): esas no son
 * «símbolos de la nota» y en MuseScore tampoco están en la barra de entrada.
 */
@Composable
private fun MoreSymbolsMenu(
    measureIndex: Int,
    slotIndex: Int,
    stringNumber: Int,
    activePluck: TabPluck,
    onIntent: (TablatureUiIntent) -> Unit,
    onDismiss: () -> Unit,
    focusGrid: () -> Unit
) {
    fun apply(intent: TablatureUiIntent) {
        onIntent(intent)
        onDismiss()
        focusGrid()
    }

    Text(
        text = "Símbolos de la nota",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Density.Compact, vertical = Density.Tight)
    )

    // ── Articulaciones ────────────────────────────────────────────────────────────────────────────
    Text(
        text = "Articulaciones",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Density.Compact)
    )
    Row(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Density.Compact, vertical = Density.Tight),
        horizontalArrangement = Arrangement.spacedBy(Density.Tight)
    ) {
        TabArticulation.entries.filter { it.stacks }.forEach { articulation ->
            val present = TabSymbolCompatibility.isArticulationPresent(
                activePluck.articulations,
                articulation
            )
            val allowed = !present && TabSymbolCompatibility.isArticulationAllowed(
                activePluck.articulations,
                articulation
            )
            PaletteChip(
                glyph = articulation.display,
                label = articulation.label,
                selected = present,
                enabled = present || allowed,
                onClick = {
                    apply(
                        TablatureUiIntent.ToggleArticulation(
                            measureIndex, slotIndex, stringNumber, articulation
                        )
                    )
                }
            )
        }
    }

    // ── Ligadura de prolongación ──────────────────────────────────────────────────────────────────
    Text(
        text = "Ligaduras",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Density.Compact)
    )
    Row(
        modifier = Modifier.padding(horizontal = Density.Compact, vertical = Density.Tight),
        horizontalArrangement = Arrangement.spacedBy(Density.Tight)
    ) {
        PaletteChip(
            glyph = "\u2040",
            label = "Ligadura de prolongación (T)",
            selected = activePluck.tiedToNext,
            onClick = {
                apply(TablatureUiIntent.ToggleTie(measureIndex, slotIndex, stringNumber))
            }
        )
    }

    // ── Técnicas más usadas ───────────────────────────────────────────────────────────────────────
    // No van las veinte: el menú es un atajo, no el catálogo. Las que se tocan al escribir son el
    // ligado, el estiramiento, el armónico, el apagado de palma y el dejar sonar; el resto sigue en
    // la paleta «Técnicas», que se abre desde la última entrada de este menú.
    Text(
        text = "Técnicas",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Density.Compact)
    )
    Row(
        modifier = Modifier
            .widthIn(max = 320.dp)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Density.Compact, vertical = Density.Tight),
        horizontalArrangement = Arrangement.spacedBy(Density.Tight)
    ) {
        MORE_SYMBOLS_TECHNIQUES.forEach { technique ->
            TechniquePaletteChip(
                technique = technique,
                selected = activePluck.has(technique),
                enabled = true,
                onClick = { apply(TablatureUiIntent.ToggleTechnique(technique)) }
            )
        }
    }

    // ── Trémolos ─────────────────────────────────────────────────────────────────────────────────
    Text(
        text = "Trémolos",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Density.Compact)
    )
    Row(
        modifier = Modifier.padding(horizontal = Density.Compact, vertical = Density.Tight),
        horizontalArrangement = Arrangement.spacedBy(Density.Tight)
    ) {
        (1..4).forEach { strokes ->
            PaletteChip(
                glyph = "\u00B7".repeat(strokes),
                label = "Trémolo de $strokes trazos",
                selected = activePluck.techniques.contains(TabTechnique.TREMOLO) &&
                    activePluck.tremoloStrokes == strokes,
                onClick = {
                    apply(
                        TablatureUiIntent.SetTremoloStrokes(
                            measureIndex, slotIndex, stringNumber, strokes
                        )
                    )
                }
            )
        }
    }

    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("Ver todas las paletas (F9)") },
        onClick = {
            onIntent(TablatureUiIntent.TogglePalettes)
            onDismiss()
        }
    )
}

/**
 * Técnicas que se ofrecen en el menú del botón «+»: las que se aplican **mientras se escribe**. El
 * catálogo completo sigue en la paleta «Técnicas»; aquí solo están las de uso diario.
 */
private val MORE_SYMBOLS_TECHNIQUES = listOf(
    TabTechnique.HAMMER,
    TabTechnique.PULL,
    TabTechnique.BEND,
    TabTechnique.HARMONIC,
    TabTechnique.PALM_MUTE,
    TabTechnique.LET_RING,
    TabTechnique.VIBRATO
)

/**
 * **Mezclador de la reproducción** (D-29): volumen, balance, silencio y solo.
 *
 * MuseScore lo tiene en un panel propio (`F10`, `MixerPanel`) con una **tira por pentagrama**. En
 * Canticora la tablatura es un solo pentagrama, así que la tira es una y se despliega **dentro de la
 * barra de escritura** en vez de en un panel flotante: el volumen se toca mientras se ensaya, y un
 * modal crearía una raíz de semántica nueva que rompe el arnés de render.
 *
 * El botón enseña el estado de un vistazo (`Mezclador 80 %`, `Mezclador · M`), así que no hace falta
 * desplegarlo para saber si está en silencio.
 */
@Composable
private fun TablatureMixerBar(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    focusGrid: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val mixer = uiState.mixer
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Density.Tight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Density.Tight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { expanded = !expanded },
                shape = RoundedCornerShape(Density.CornerSmall),
                color = if (expanded || mixer.muted) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                },
                border = BorderStroke(
                    1.dp,
                    if (expanded || mixer.muted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                ),
                contentColor = if (expanded || mixer.muted) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.semantics {
                    contentDescription = if (expanded) {
                        "Ocultar el mezclador de la reproducción"
                    } else {
                        "Abrir el mezclador de la reproducción"
                    }
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Density.Tight),
                    modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
                ) {
                    Text(
                        text = "\u266B",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = buildString {
                            append("Mezclador ")
                            append(mixer.volumePercent)
                            append(" %")
                            if (mixer.muted) append(" \u00B7 M")
                            if (mixer.solo) append(" \u00B7 S")
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            // `M` y `S` viven **fuera** del desplegable: son los dos gestos de urgencia (callar el
            // atril, aislar la pista) y no deberían costar dos toques.
            MixerToggleChip(
                label = "M",
                description = if (mixer.muted) "Quitar el silencio" else "Silenciar la reproducción",
                active = mixer.muted,
                onClick = { onIntent(TablatureUiIntent.ToggleMixerMute); focusGrid() }
            )
            MixerToggleChip(
                label = "S",
                description = if (mixer.solo) "Quitar el solo" else "Escuchar solo esta pista",
                active = mixer.solo,
                onClick = { onIntent(TablatureUiIntent.ToggleMixerSolo); focusGrid() }
            )
        }
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Density.Snug),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MixerSlider(
                    label = "Volumen",
                    value = mixer.safeVolume,
                    valueLabel = "${mixer.volumePercent} %",
                    range = TabMixer.MIN_VOLUME..TabMixer.MAX_VOLUME,
                    onChange = { onIntent(TablatureUiIntent.SetMixerVolume(it)) }
                )
                MixerSlider(
                    label = "Balance",
                    value = mixer.safePan,
                    valueLabel = mixer.panLabel,
                    range = TabMixer.MIN_PAN..TabMixer.MAX_PAN,
                    onChange = { onIntent(TablatureUiIntent.SetMixerPan(it)) }
                )
            }
        }
    }
}

/** Deslizador de una tira del mezclador, con su rótulo y su valor a la vista. */
@Composable
private fun MixerSlider(
    label: String,
    value: Float,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Density.Tight),
        modifier = Modifier.width(220.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "$label: $valueLabel" }
        )
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(44.dp)
        )
    }
}

/** Interruptor compacto del mezclador (`M` / `S`), del tamaño de un chip de la barra. */
@Composable
private fun MixerToggleChip(
    label: String,
    description: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Density.CornerSmall),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
        )
    }
}

/**
 * **Teclado numérico de traste** en una **hoja flotante**, solo para **plataformas táctiles**.
 *
 * MuseScore de escritorio **no** dibuja un teclado `0`–`9` (el número lo pone el teclado del sistema),
 * así que en escritorio no se ofrece. En tableta, en cambio, hace falta para poder escribir sin un
 * teclado físico: se mantiene como **desviación deliberada** del ADR-020, documentada en
 * `docs/TABLATURE_UX_PARITY.md`.
 *
 * Cada tecla **acumula** el dígito en el mismo búfer de dos cifras que el teclado físico: «1» y luego
 * «2» dan el traste 12, y el cursor avanza cuando pasa la pausa de la cifra, no al primer toque.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TablatureKeypadSheet(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Density.Compact, vertical = Density.Snug),
            verticalArrangement = Arrangement.spacedBy(Density.Tight)
        ) {
            Text(
                text = "Traste",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Rejilla de 5 columnas con las 10 cifras y el retroceso, a tamaño táctil (>= 48 dp).
            Column(verticalArrangement = Arrangement.spacedBy(Density.Tight)) {
                listOf(0..4, 5..9).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Density.Tight)
                    ) {
                        row.forEach { digit ->
                            FretKey(digit, Modifier.weight(1f)) { onDigit(digit) }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Density.Tight)
                ) {
                    FretKey(
                        digit = null,
                        modifier = Modifier.weight(1f),
                        label = "⌫",
                        description = "Borrar la cifra y retroceder",
                        onClick = onBackspace
                    )
                }
            }
            Spacer(Modifier.height(Density.Tight))
        }
    }
}

/**
 * **Barra de estado** del editor (B-1), con el **vocabulario de MuseScore 4**.
 *
 * MuseScore describe el elemento seleccionado con una línea de campos separados por `;`
 * (`Note::accessibleInfo` + `formatBarsAndBeats` + `Staff N`). Aquí se usa la misma estructura en
 * español, con la **octava** añadida a la altura (`Sol3`), que es lo que hace falta para saber qué
 * va a sonar sin contar trastes. El texto lo construye el Dominio
 * ([com.grafitto.laudate.domain.tablature.TabStatusBar]), así que es puro y está probado.
 *
 * A la derecha se muestra el **zoom** (B-6), como el indicador de zoom de MuseScore.
 */
@Composable
private fun TablatureStatusBar(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    fitScale: Float? = null
) {
    val text = uiState.statusBarText
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Density.Compact, vertical = Density.Tight)
                .semantics { contentDescription = "Barra de estado: $text" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Density.Snug)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // **Aviso de acorde** (F-08): el editor no impide escribir una posición imposible, pero la
            // barra de estado lo dice con el primer aviso y cuántas columnas lo tienen. Va antes del
            // zoom para que el mensaje tenga sitio y el zoom no se mueva de su extremo.
            uiState.chordIssueSummary?.let { summary ->
                Text(
                    text = "\u26A0 $summary",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Aviso de acorde: $summary" }
                )
            }
            ZoomControl(uiState.tablatureScale, fitScale, onIntent)
        }
    }
}

/**
 * **Zoom de la barra de estado** (D-14): el porcentaje es un **desplegable**, como en MuseScore 4,
 * donde el zoom vive en la barra de estado y no en un menú. Antes era un `Text` de solo lectura: se
 * podía cambiar el zoom con `Ctrl`+rueda o con el pellizco, pero **no se podía elegir un valor** ni
 * volver al 100 % sin adivinar.
 *
 * Ofrece los **presets de MuseScore** (25, 50, 75, 100, 150, 200, 400 %) y **«Ajustar al ancho»**,
 * que es el comando que en MuseScore 4 está en el propio control de zoom. El valor vigente se marca
 * con una **cuña** (`✓`) porque el desplegable no lleva iconos de estado.
 */
@Composable
private fun ZoomControl(
    scale: Float,
    fitScale: Float?,
    onIntent: (TablatureUiIntent) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val percent = (scale * 100f).roundToInt()
    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = Density.Snug, vertical = 0.dp),
            modifier = Modifier.semantics { contentDescription = "Zoom: $percent por ciento" }
        ) {
            Text(
                text = "$percent %",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (preset in ZOOM_PRESETS) {
                val presetPercent = (preset * 100f).roundToInt()
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Density.Snug)
                        ) {
                            if (presetPercent == percent) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Zoom actual",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text("$presetPercent %")
                        }
                    },
                    onClick = {
                        expanded = false
                        onIntent(TablatureUiIntent.SetTablatureScale(preset))
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Ajustar al ancho") },
                enabled = fitScale != null,
                onClick = {
                    expanded = false
                    fitScale?.let {
                        onIntent(
                            TablatureUiIntent.SetTablatureScale(
                                it.coerceIn(TabNotationScale.MIN_ZOOM, TabNotationScale.MAX_ZOOM)
                            )
                        )
                    }
                }
            )
        }
    }
}

/** Presets de zoom de MuseScore 4 (`Ctrl`+`+`/`-` y el desplegable de la barra de estado). */
private val ZOOM_PRESETS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 4f)

/**
 * Lado del botón de **voz** (D-75): mínimo táctil cómodo, no los 28 dp que tenía.
 *
 * Sigue en uso en el **selector de voz del panel de propiedades** de la nota (el `F8` de MuseScore),
 * aunque los botones de voz ya no estén en la barra de escritura.
 */
private val VoiceButtonSize = 40.dp

/** Tecla del **teclado de traste** de la hoja táctil (0-9, o el retroceso). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FretKey(
    digit: Int?,
    modifier: Modifier = Modifier,
    label: String = digit?.toString().orEmpty(),
    description: String = "Escribir el traste ${digit ?: 0}",
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            // Tamaño táctil mínimo (RNF de accesibilidad: >= 48 dp).
            .height(FRET_KEY)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(3.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(contentAlignment = Alignment.Center) {
            // (Punto 3 del usuario, 2026-10-15) «El recuadro de introducción de números es más grande
            // que un número»: el número **llena** la tecla y se lee de un vistazo.
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Alto de una tecla del teclado de traste (dp): el mínimo táctil cómodo. */
private val FRET_KEY = 48.dp

/** Nombre en español de un **grupo irregular**, como se lee en un ensayo. */
private fun tupletName(tuplet: TabTuplet): String = when (tuplet) {
    TabTuplet.TRIPLET -> "tresillo"
    TabTuplet.QUINTUPLET -> "quintillo"
    TabTuplet.SEXTUPLET -> "seisillo"
    TabTuplet.SEPTUPLET -> "septillo"
    TabTuplet.NONUPLET -> "nonillo"
    else -> "grupo de ${tuplet.numNotes}:${tuplet.normalNotes}"
}

/** Texto de ayuda de un chip de grupo irregular: nombre y proporción. */
private fun tupletTooltip(tuplet: TabTuplet): String =
    "${tupletName(tuplet).replaceFirstChar { it.uppercase() }}: " +
        "${tuplet.numNotes} en el tiempo de ${tuplet.normalNotes} " +
        "(${tuplet.numNotes}:${tuplet.normalNotes})"

/**
 * Botón de **ajustes del grupo irregular** del cursor (D-41): despliega las dos propiedades que
 * MuseScore expone en *Propiedades ▸ Grupo irregular*.
 *
 *  - **Número** (`TupletNumberType`): `3` (solo el número, el valor por defecto), `3:2` (la
 *    proporción) o nada.
 *  - **Corchete** (`TupletBracketType`): automático (MuseScore lo omite cuando el grupo ocupa
 *    exactamente una barra de unión), siempre o nunca.
 *
 * El icono del botón resume el estado: la cifra que se rotula y, si no hay corchete, sin las líneas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TupletAppearanceButton(
    tuplet: TabTuplet,
    onIntent: (TablatureUiIntent) -> Unit,
    focusGrid: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val contentColor = MaterialTheme.colorScheme.onSurface
    MouseTooltip(
        "Grupo irregular: ${tuplet.numberType.label.lowercase()} · " +
            tuplet.bracketType.label.lowercase()
    ) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier
                .size(figureButtonSize())
                .semantics { contentDescription = "Ajustes del grupo irregular del cursor" },
            shape = RoundedCornerShape(3.dp),
            color = Color.Transparent,
            border = null,
            contentColor = contentColor
        ) {
            val measurer = rememberTextMeasurer()
            val music = MusicFont.family()
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                // La cifra que se rotula: `3`, `3:2` o nada.
                val text = tuplet.label
                if (text.isNotEmpty()) {
                    val isDigit = text.length == 1 && text[0].isDigit()
                    val glyph = if (isDigit) MusicFont.tupletDigit(text[0] - '0') else null
                    val layout = if (glyph != null) {
                        val ink = MusicFont.inkOf(glyph) ?: return@Canvas
                        val emPx = size.height * 0.5f / ink.height.coerceAtLeast(0.01f)
                        measurer.measure(
                            text = glyph,
                            style = TextStyle(
                                color = contentColor,
                                fontSize = emPx.toSp(),
                                fontFamily = music
                            )
                        )
                    } else {
                        measurer.measure(
                            text = text,
                            style = TextStyle(
                                color = contentColor,
                                fontSize = (size.height * 0.34f).toSp()
                            )
                        )
                    }
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            (size.width - layout.size.width) / 2f,
                            (size.height - layout.size.height) / 2f
                        )
                    )
                }
                // El corchete, solo si el grupo lo lleva.
                if (tuplet.bracketType != TabTupletBracketType.SHOW_NO_BRACKET) {
                    val stroke = 1.2f
                    val top = size.height * 0.16f
                    val hook = size.height * 0.16f
                    val left = size.width * 0.08f
                    val right = size.width * 0.92f
                    drawLine(contentColor, Offset(left, top), Offset(right, top), stroke, StrokeCap.Round)
                    drawLine(contentColor, Offset(left, top), Offset(left, top + hook), stroke, StrokeCap.Round)
                    drawLine(contentColor, Offset(right, top), Offset(right, top + hook), stroke, StrokeCap.Round)
                }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Text(
                text = "Número",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
            )
            TabTupletNumberType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    leadingIcon = {
                        if (tuplet.numberType == type) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        onIntent(TablatureUiIntent.SetTupletNumberTypeAtCursor(type))
                        expanded = false
                        focusGrid()
                    }
                )
            }
            HorizontalDivider()
            Text(
                text = "Corchete",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
            )
            TabTupletBracketType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.label) },
                    leadingIcon = {
                        if (tuplet.bracketType == type) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        onIntent(TablatureUiIntent.SetTupletBracketTypeAtCursor(type))
                        expanded = false
                        focusGrid()
                    }
                )
            }
        }
    }
}

/**
 * Botón del **tresillo** de la barra de escritura.
 *
 * (Punto 8 del usuario) El símbolo es el **glifo SMuFL** `tuplet3` (`\uE883`) —el que está dibujado
 * para ir sobre el grupo, con la misma tinta que el resto de la notación—, **no** el carácter «3» de
 * una fuente de texto. Y se dibuja **con su corchete**: el glifo solo es el «3»; el corchete que
 * abraza las tres figuras se traza a los lados, que es como se ve en la partitura. Antes el chip
 * pintaba el glifo con la tipografía de texto a un tamaño diminuto (0,383 em de tinta) y salía un
 * «3» perdido en una caja.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TupletButton(
    selected: Boolean,
    enabled: Boolean,
    label: String = "3",
    tooltip: String = "Tresillo: la figura dura dos tercios (Ctrl+3)",
    onClick: () -> Unit
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState()
    ) {
        Surface(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(figureButtonSize()),
            shape = RoundedCornerShape(3.dp),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            border = null,
            contentColor = contentColor
        ) {
            val measurer = rememberTextMeasurer()
            val music = MusicFont.family()
            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val glyph = MusicFont.tupletDigit(label.toIntOrNull() ?: 3)
                    ?: MusicFont.Glyph.TUPLET_3
                val ink = MusicFont.inkOf(glyph) ?: return@Canvas
                // La cifra se escala a la **mitad** de la caja: el corchete ocupa el resto.
                val emPx = size.height * 0.5f / ink.height.coerceAtLeast(0.01f)
                val layout = measurer.measure(
                    text = glyph,
                    style = TextStyle(color = contentColor, fontSize = emPx.toSp(), fontFamily = music)
                )
                // **Corchete** del tresillo, trazado **igual que en la partitura**
                // (`TabRhythmPainter.drawTupletBracket`): la línea va arriba, se **interrumpe** para
                // dejar sitio al «3» —que se centra sobre ella, no encima— y baja en **gancho** por
                // los dos extremos. Antes la línea cruzaba el «3» de lado a lado y el símbolo salía
                // tachado.
                val stroke = 1.2f
                val top = size.height * 0.34f
                val hook = size.height * 0.30f
                val left = size.width * 0.08f
                val right = size.width * 0.92f
                val centerX = size.width / 2f
                val gap = layout.size.width / 2f + size.width * 0.04f
                drawLine(contentColor, Offset(left, top), Offset(centerX - gap, top), stroke, StrokeCap.Round)
                drawLine(contentColor, Offset(centerX + gap, top), Offset(right, top), stroke, StrokeCap.Round)
                drawLine(contentColor, Offset(left, top), Offset(left, top + hook), stroke, StrokeCap.Round)
                drawLine(contentColor, Offset(right, top), Offset(right, top + hook), stroke, StrokeCap.Round)
                // La cifra, con su **tinta** centrada en la línea (no su caja de línea).
                val originX = centerX - layout.size.width / 2f
                val originY = inkTopLeftY(layout, top, emPx, ink)
                drawText(textLayoutResult = layout, topLeft = Offset(originX, originY))
            }
        }
    }
}

/**
 * Botón de **menú** del cromo: un icono con su **nombre** y una flecha, que despliega las acciones.
 * Sustituye a los iconos sueltos que no decían qué hacían.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChromeMenuButton(
    icon: ImageVector,
    label: String,
    description: String,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.height(Density.PillHeight),
            shape = RoundedCornerShape(3.dp),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Density.Snug),
                horizontalArrangement = Arrangement.spacedBy(Density.Tight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = description, modifier = Modifier.size(16.dp))
                Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            content { expanded = false }
        }
    }
}

/** Fila de un menú del cromo. Con [selected] lleva un **check** delante (opción activa). */
@Composable
private fun ChromeMenuItem(
    text: String,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            // El check va como icono: el carácter `✓` no existe en la fuente de la web.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Density.Snug)
            ) {
                if (selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Opción activa",
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
        },
        enabled = enabled,
        onClick = onClick
    )
}

/** **Separador** dentro de un menú del cromo (agrupa opciones). */
@Composable
private fun ChromeMenuDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(vertical = Density.Tight)
    )
}

/** Separación fina entre grupos de la barra de herramientas (como el aire entre secciones). */
@Composable
private fun ToolbarSeparator() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * **Tooltip solo con ratón.** En táctil, mantener pulsado un botón dispara el globo del tooltip: un
 * recuadro diminuto que aparece **encima del propio botón** y, con los glifos musicales dentro,
 * parece una **réplica borrosa del botón**. El usuario lo describió así («sale una réplica en
 * pequeñita sobre el botón pulsado») al pulsar los botones del panel de herramientas.
 *
 * En una pantalla táctil el tooltip tampoco aporta nada —no hay hover—, así que se pinta el contenido
 * **sin él**. En escritorio se mantiene, que es donde sí sirve.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MouseTooltip(
    text: String,
    content: @Composable () -> Unit
) {
    if (isTouchPlatform()) {
        content()
        return
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text) } },
        state = rememberTooltipState()
    ) {
        content()
    }
}

/**
 * **Propiedades de compás** (B-8), el diálogo `Propiedades de compás…` de MuseScore reducido a lo que
 * el modelo guarda **de verdad** por compás: la **subdivisión** de la rejilla (negras, corcheas,
 * semicorcheas) y el **salto de sistema**. No se inventan campos que no se puedan guardar.
 *
 * Se abre desde el menú contextual del compás (pulsación larga o clic derecho), que es donde MuseScore
 * lo tiene.
 */
@Composable
private fun MeasurePropertiesDialog(
    measureIndex: Int,
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    onDismiss: () -> Unit
) {
    val measure = uiState.editorMeasures.getOrNull(measureIndex) ?: return
    val slots = measure.slots.size
    val notes = measure.slots.sumOf { slot -> slot.strings.size }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Propiedades del compás ${measureIndex + 1}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Density.Snug)) {
                Text(
                    text = "$slots casillas · $notes notas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Subdivisión de la rejilla",
                    style = MaterialTheme.typography.labelMedium
                )
                // Cambiar la subdivisión **no** toca las notas escritas: solo rehace las columnas de
                // silencio, así que se puede cambiar a medio escribir sin perder nada.
                Row(horizontalArrangement = Arrangement.spacedBy(Density.Tight)) {
                    for (resolution in listOf(
                        TabResolution.QUARTER,
                        TabResolution.EIGHTH,
                        TabResolution.SIXTEENTH
                    )) {
                        val selected = uiState.cursorSubdivision == resolution
                        Surface(
                            onClick = {
                                onIntent(TablatureUiIntent.SetMeasureSubdivision(measureIndex, resolution))
                            },
                            shape = RoundedCornerShape(Density.CornerSmall),
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier.semantics {
                                contentDescription = "Subdivisión ${resolutionLabel(resolution)}"
                            }
                        ) {
                            Text(
                                text = resolutionLabel(resolution),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(
                                    horizontal = Density.Snug,
                                    vertical = Density.Tight
                                )
                            )
                        }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Empezar un sistema nuevo",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "El compás empieza una fila aunque quepa en la anterior (Return).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = measure.forceSystemBreak,
                        onCheckedChange = { force ->
                            onIntent(TablatureUiIntent.SetSystemBreak(measureIndex, force))
                        }
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Density.Snug),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Empezar una página nueva",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "En el PDF el compás abre hoja (Ctrl+Return). Implica el salto de sistema.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = measure.forcePageBreak,
                        onCheckedChange = { force ->
                            onIntent(TablatureUiIntent.SetPageBreak(measureIndex, force))
                        }
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

/**
 * **Propiedades del pentagrama**: el `Staff/Part properties` de MuseScore, con los ajustes de
 * `TabStaffOptions` que hasta D-45 **no tenían ninguna interfaz**.
 *
 * MuseScore los reparte en dos diálogos —*Staff/Part properties* (tipo de pentagrama, cifras, ritmo,
 * líneas) y *Format ▸ Style ▸ Tablature*—, pero todos acaban en el mismo `StaffType`
 * (`src/engraving/dom/stafftype.h`). Aquí van juntos porque son **doce** ajustes y partirlos en dos
 * pantallas solo añadiría un viaje.
 *
 * Cada ajuste se aplica con [TablatureUiIntent.SetStaffOptions] sobre el estado **completo**, no con
 * un intent por campo: `TabStaffOptions` es un `data class` inmutable y `copy` deja el resto intacto,
 * así que un solo camino mantiene el contrato MVI pequeño y el `when` exhaustivo.
 */
@Composable
private fun StaffPropertiesDialog(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    onDismiss: () -> Unit
) {
    val options = uiState.editorStaffOptions
    fun apply(updated: TabStaffOptions) = onIntent(TablatureUiIntent.SetStaffOptions(updated))
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Propiedades del pentagrama") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Density.Snug)
            ) {
                Text(
                    text = "Los ajustes del pentagrama de tablatura, como el «Staff/Part properties» " +
                        "de MuseScore. Se aplican a la vista y al PDF.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()

                // `Note values` (`StaffType::genDurations` + el estilo del pentagrama rítmico).
                StaffOptionRow(
                    title = "Valores de nota",
                    hint = "Qué se dibuja del ritmo: nada, símbolos encima o plicas y barras debajo."
                ) {
                    StaffOptionChip("Ninguno", options.noteValues == NoteValueStyle.NONE) {
                        apply(options.copy(noteValues = NoteValueStyle.NONE))
                    }
                    StaffOptionChip("Símbolos", options.noteValues == NoteValueStyle.SYMBOLS_ABOVE) {
                        apply(options.copy(noteValues = NoteValueStyle.SYMBOLS_ABOVE))
                    }
                    StaffOptionChip("Plicas y barras", options.noteValues == NoteValueStyle.STEMS_AND_BEAMS) {
                        apply(options.copy(noteValues = NoteValueStyle.STEMS_AND_BEAMS))
                    }
                }

                // `TablatureSymbolRepeat` (`stafftype.h:93-98`), con sus **cuatro** valores.
                StaffOptionRow(
                    title = "Repetir el símbolo de duración",
                    hint = "Cuándo se vuelve a rotular la figura: solo al cambiar, al abrir compás, " +
                        "al abrir sistema o siempre."
                ) {
                    StaffOptionChip("Nunca", options.rhythmRepeat == RhythmRepeat.NEVER) {
                        apply(options.copy(rhythmRepeat = RhythmRepeat.NEVER))
                    }
                    StaffOptionChip("Al abrir sistema", options.rhythmRepeat == RhythmRepeat.SYSTEM) {
                        apply(options.copy(rhythmRepeat = RhythmRepeat.SYSTEM))
                    }
                    StaffOptionChip("Al abrir compás", options.rhythmRepeat == RhythmRepeat.MEASURE) {
                        apply(options.copy(rhythmRepeat = RhythmRepeat.MEASURE))
                    }
                    StaffOptionChip("Siempre", options.rhythmRepeat == RhythmRepeat.ALWAYS) {
                        apply(options.copy(rhythmRepeat = RhythmRepeat.ALWAYS))
                    }
                }

                // `TablatureMinimStyle` (`stafftype.h:87-91`): cómo se distingue la blanca de la
                // negra cuando la plica no nace de una cabeza de nota.
                StaffOptionRow(
                    title = "La blanca",
                    hint = "Sin plica, con la plica a la mitad o con dos barras inclinadas. Es el " +
                        "`TablatureMinimStyle` de MuseScore."
                ) {
                    StaffOptionChip("Sin plica", options.minimStyle == MinimStyle.NONE) {
                        apply(options.copy(minimStyle = MinimStyle.NONE))
                    }
                    StaffOptionChip("Plica corta", options.minimStyle == MinimStyle.SHORTER) {
                        apply(options.copy(minimStyle = MinimStyle.SHORTER))
                    }
                    StaffOptionChip("Rayada", options.minimStyle == MinimStyle.SLASHED) {
                        apply(options.copy(minimStyle = MinimStyle.SLASHED))
                    }
                }

                // `ShowTiedFret` (`stafftype.h:116-120`): qué se hace con la cifra de una nota que
                // **recibe** una ligadura de prolongación.
                StaffOptionRow(
                    title = "Cifra de la nota ligada",
                    hint = "La segunda nota de una ligadura dice lo mismo que la primera: se puede " +
                        "repetir, recordar solo al abrir compás o sistema, o quitar."
                ) {
                    StaffOptionChip("Cifra y ligadura", options.showTiedFret == ShowTiedFret.TIE_AND_FRET) {
                        apply(options.copy(showTiedFret = ShowTiedFret.TIE_AND_FRET))
                    }
                    StaffOptionChip("Solo la ligadura", options.showTiedFret == ShowTiedFret.TIE) {
                        apply(options.copy(showTiedFret = ShowTiedFret.TIE))
                    }
                    StaffOptionChip("Nada", options.showTiedFret == ShowTiedFret.NONE) {
                        apply(options.copy(showTiedFret = ShowTiedFret.NONE))
                    }
                }

                // `ParenthesizeTiedFret` (`stafftype.h:122-126`): dónde se recuerda la cifra ligada.
                // Solo tiene efecto con «Solo la ligadura».
                StaffOptionRow(
                    title = "Dónde se recuerda",
                    hint = "Con «Solo la ligadura»: al abrir sistema, al abrir compás o nunca. La " +
                        "cifra recordada va entre paréntesis."
                ) {
                    StaffOptionChip(
                        "Al abrir el sistema",
                        options.parenthesizeTiedFret == ParenthesizeTiedFret.START_OF_SYSTEM
                    ) { apply(options.copy(parenthesizeTiedFret = ParenthesizeTiedFret.START_OF_SYSTEM)) }
                    StaffOptionChip(
                        "Al abrir el compás",
                        options.parenthesizeTiedFret == ParenthesizeTiedFret.START_OF_MEASURE
                    ) { apply(options.copy(parenthesizeTiedFret = ParenthesizeTiedFret.START_OF_MEASURE)) }
                    StaffOptionChip(
                        "No se recuerda",
                        options.parenthesizeTiedFret == ParenthesizeTiedFret.NEVER
                    ) { apply(options.copy(parenthesizeTiedFret = ParenthesizeTiedFret.NEVER)) }
                }

                StaffOptionRow(
                    title = "Cifras",
                    hint = "Números (0-9) o letras (a-k, sin la i), como el `FretNumberType` de MuseScore."
                ) {
                    StaffOptionChip("Números", options.fretMarks == FretMarkStyle.NUMBERS) {
                        apply(options.copy(fretMarks = FretMarkStyle.NUMBERS))
                    }
                    StaffOptionChip("Letras", options.fretMarks == FretMarkStyle.LETTERS) {
                        apply(options.copy(fretMarks = FretMarkStyle.LETTERS))
                    }
                }

                StaffOptionRow(
                    title = "Colocación de la cifra",
                    hint = "Sobre la línea de la cuerda o encima de ella."
                ) {
                    StaffOptionChip("Sobre la línea", options.marksPlacement == MarksPlacement.ON_LINES) {
                        apply(options.copy(marksPlacement = MarksPlacement.ON_LINES))
                    }
                    StaffOptionChip("Encima", options.marksPlacement == MarksPlacement.ABOVE_LINES) {
                        apply(options.copy(marksPlacement = MarksPlacement.ABOVE_LINES))
                    }
                }

                StaffOptionRow(
                    title = "Líneas de cuerda",
                    hint = "Continuas o interrumpidas en cada cifra (`linesThrough` de MuseScore)."
                ) {
                    StaffOptionChip("Continuas", options.lines == LineStyle.CONTINUOUS) {
                        apply(options.copy(lines = LineStyle.CONTINUOUS))
                    }
                    StaffOptionChip("Cortadas en la cifra", options.lines == LineStyle.BROKEN_ON_MARKS) {
                        apply(options.copy(lines = LineStyle.BROKEN_ON_MARKS))
                    }
                }

                HorizontalDivider()
                StaffOptionSwitch(
                    title = "Silencios",
                    hint = "Dibujar los silencios del pentagrama rítmico (`showRests`).",
                    checked = options.showRests
                ) { apply(options.copy(showRests = it)) }
                StaffOptionSwitch(
                    title = "Plica hacia abajo",
                    hint = "Dirección de la plica del pentagrama rítmico (`stemsDown`).",
                    checked = options.stemsDown
                ) { apply(options.copy(stemsDown = it)) }
                StaffOptionSwitch(
                    title = "Clave TAB",
                    hint = "Dibujar la clave de tablatura al abrir el primer sistema.",
                    checked = options.showClef
                ) { apply(options.copy(showClef = it)) }
                StaffOptionSwitch(
                    title = "Indicación de compás",
                    hint = "Dibujar la fracción al abrir el primer sistema.",
                    checked = options.showTimeSignature
                ) { apply(options.copy(showTimeSignature = it)) }
                StaffOptionSwitch(
                    title = "Cabecera en todos los sistemas",
                    hint = "MuseScore no lo hace en tablatura: la clave y el compás van solo en el primero.",
                    checked = options.headerOnEverySystem
                ) { apply(options.copy(headerOnEverySystem = it)) }
                StaffOptionSwitch(
                    title = "Nombres de cuerda",
                    hint = "Rotular a la izquierda la nota de cada cuerda (E B G D A E).",
                    checked = options.showStringLabels
                ) { apply(options.copy(showStringLabels = it)) }
                StaffOptionSwitch(
                    title = "Números de compás",
                    hint = "Numerar los compases al abrir cada uno.",
                    checked = options.showMeasureNumbers
                ) { apply(options.copy(showMeasureNumbers = it)) }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

/**
 * **Datos de cuerda** (D-49): el diálogo «String data» de MuseScore
 * (`src/notationscene/widgets/editstringdata.ui`).
 *
 * Es la única puerta a tres cosas que hasta ahora no se podían tocar:
 *
 *  - la **altura al aire** de cada cuerda (afinaciones que no están en los presets: scordaturas,
 *    laúd renacentista, tiorba…);
 *  - la marca **«Siempre al aire»** (`instrString::open`): la cuerda no pasa por el mástil, así que
 *    solo admite el traste `0`. Es el caso de la tiorba y de la 5.ª del banjo de cinco cuerdas;
 *  - el **número de trastes** del instrumento (`m_frets`), que es el límite real del mástil y no una
 *    constante global.
 *
 * **Desviaciones conscientes** respecto al diálogo de MuseScore, todas por ser táctil:
 *
 *  - MuseScore tiene un botón «Edit string…» que abre un subdiálogo con un selector de altura. Aquí
 *    la altura se cambia con `−`/`+` en la propia fila (un semitono por toque, como
 *    `StringTuningsSettingsModel::increaseStringValue`), que en un móvil es un gesto en vez de tres.
 *  - «New string…» en MuseScore abre un subdiálogo para elegir la altura; aquí la cuerda nueva nace
 *    con la altura de la cuerda seleccionada y se ajusta con `−`/`+`.
 *  - El `startFret` de la 5.ª cuerda del banjo no se expone: MuseScore lo calcula solo
 *    (`StringData::configBanjo5thString()`) y Canticora no tiene banjo de cinco cuerdas.
 *
 * La tabla se pinta **al revés que la lista interna** (la 1.ª, la más aguda, arriba), igual que
 * `EditStringData::init()` (`editstringdata.cpp:222-290`).
 */
@Composable
private fun StringDataDialog(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    onDismiss: () -> Unit
) {
    val tuning = uiState.editorTuning
    // Fila seleccionada, en **número de cuerda** (1 = la más aguda). Es donde actúan «Nueva cuerda…»
    // y «Quitar cuerda», como la fila actual de la tabla de MuseScore.
    var selected by remember(tuning.stringCount) {
        mutableStateOf(uiState.selectedString.coerceIn(1, tuning.stringCount.coerceAtLeast(1)))
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Datos de cuerda") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Density.Snug)
            ) {
                Text(
                    text = "Las cuerdas de ${tuning.name}, de la 1.ª (la más aguda) a la última. " +
                        "Una cuerda «siempre al aire» no pasa por el mástil y solo admite el traste 0.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()

                // Cabecera de la tabla.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cuerda",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp)
                    )
                    Text(
                        text = "Siempre al aire",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Altura",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Filas en orden de lectura: la 1.ª (más aguda) arriba.
                for (index in tuning.strings.indices.reversed()) {
                    val number = tuning.strings.size - index
                    val string = tuning.strings[index]
                    StringDataRow(
                        number = number,
                        pitchLabel = TabStringData.pitchLabel(string.pitch, string.useFlat),
                        alwaysOpen = string.alwaysOpen,
                        selected = number == selected,
                        onSelect = { selected = number },
                        onAlwaysOpenChange = {
                            onIntent(TablatureUiIntent.SetStringAlwaysOpen(number, it))
                        },
                        onSemitone = { delta ->
                            val midi = (string.pitch.midi + delta).coerceIn(0, 127)
                            onIntent(
                                TablatureUiIntent.SetStringPitch(
                                    stringNumber = number,
                                    pitch = TabStringData.midiToPitch(midi, string.useFlat),
                                    useFlat = string.useFlat
                                )
                            )
                        },
                        onToggleFlat = {
                            onIntent(
                                TablatureUiIntent.SetStringPitch(
                                    stringNumber = number,
                                    pitch = string.pitch,
                                    useFlat = !string.useFlat
                                )
                            )
                        }
                    )
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Density.Tight)
                ) {
                    TextButton(
                        onClick = {
                            val pitch = tuning.strings.getOrNull(tuning.strings.size - selected)?.pitch
                                ?: TabStringData.midiToPitch(64)
                            onIntent(TablatureUiIntent.AddTuningString(selected, pitch))
                        },
                        enabled = tuning.stringCount < TabStringData.MAX_STRINGS
                    ) { Text("Nueva cuerda…") }
                    TextButton(
                        onClick = { onIntent(TablatureUiIntent.RemoveTuningString(selected)) },
                        enabled = tuning.stringCount > TabStringData.MIN_STRINGS
                    ) { Text("Quitar cuerda") }
                }

                HorizontalDivider()
                // `Number of frets:` (`editstringdata.ui:89-99`), con el tope de 99 de Qt.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Número de trastes", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = "El largo del mástil: no se puede escribir un traste que no exista.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            onIntent(TablatureUiIntent.SetTuningFrets(tuning.frets - 1))
                        },
                        enabled = tuning.frets > TabStringData.MIN_FRETS
                    ) { Icon(Icons.Default.Remove, contentDescription = "Un traste menos") }
                    Text(
                        text = tuning.frets.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        onClick = {
                            onIntent(TablatureUiIntent.SetTuningFrets(tuning.frets + 1))
                        },
                        enabled = tuning.frets < TabStringData.MAX_FRETS
                    ) { Icon(Icons.Default.Add, contentDescription = "Un traste más") }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

/** Una fila de la tabla de **Datos de cuerda**: número, «siempre al aire» y altura con `−`/`+`. */
@Composable
private fun StringDataRow(
    number: Int,
    pitchLabel: String,
    alwaysOpen: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onAlwaysOpenChange: (Boolean) -> Unit,
    onSemitone: (Int) -> Unit,
    onToggleFlat: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(Density.CornerSmall),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.width(56.dp)
            )
            androidx.compose.material3.Checkbox(
                checked = alwaysOpen,
                onCheckedChange = onAlwaysOpenChange,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onSemitone(-1) }) {
                Icon(Icons.Default.Remove, contentDescription = "Bajar medio tono la cuerda $number")
            }
            TextButton(onClick = onToggleFlat) {
                Text(text = pitchLabel, style = MaterialTheme.typography.labelMedium)
            }
            IconButton(onClick = { onSemitone(1) }) {
                Icon(Icons.Default.Add, contentDescription = "Subir medio tono la cuerda $number")
            }
        }
    }
}

/** Fila de un ajuste con **varias opciones excluyentes** (chips) en el diálogo del pentagrama. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffOptionRow(
    title: String,
    hint: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Density.Tight)) {
        Text(text = title, style = MaterialTheme.typography.labelMedium)
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // **FlowRow, no Row** (visto en el móvil): con tres chips etiquetados —«Cifra y ligadura»,
        // «Solo la ligadura», «Nada» o «Al abrir el sistema», «Al abrir el compás», «No se
        // recuerda»— un `Row` no los hace caber, así que comprimía el último hasta dejarlo en una
        // columna de letras sueltas, ilegible. Un `FlowRow` los reparte en tantas líneas como haga
        // falta y cada chip conserva su ancho natural.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Density.Tight),
            verticalArrangement = Arrangement.spacedBy(Density.Tight)
        ) { content() }
    }
}

/** Chip de una opción del diálogo del pentagrama: resaltado cuando es la vigente. */
@Composable
private fun StaffOptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Density.CornerSmall),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier.semantics { contentDescription = label }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Density.Snug, vertical = Density.Tight)
        )
    }
}

/** Interruptor de un ajuste booleano del diálogo del pentagrama. */
@Composable
private fun StaffOptionSwitch(
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium)
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
