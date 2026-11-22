package com.grafitto.laudate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.FileProvider
import com.grafitto.laudate.domain.model.LaudateBrand
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.backup.FullBackupSerializer
import com.grafitto.laudate.domain.backup.CANTICORA_BACKUP_MIME
import com.grafitto.laudate.domain.transfer.CelebrationBundleSerializer
import com.grafitto.laudate.domain.transfer.CANTICORA_SONG_MIME
import com.grafitto.laudate.domain.transfer.DetectedLaudateFile
import com.grafitto.laudate.domain.transfer.CANTICORA_EVENT_MIME
import com.grafitto.laudate.domain.transfer.LaudateFileDetector
import com.grafitto.laudate.ui.about.AboutDialog
import com.grafitto.laudate.ui.backup.BackupScreen
import com.grafitto.laudate.ui.backup.BackupSideEffect
import com.grafitto.laudate.ui.backup.BackupUiIntent
import com.grafitto.laudate.ui.backup.BackupViewModel
import com.grafitto.laudate.ui.catalog.SongCatalogScreen
import com.grafitto.laudate.ui.catalog.SongCatalogSideEffect
import com.grafitto.laudate.ui.catalog.SongCatalogUiIntent
import com.grafitto.laudate.ui.catalog.SongCatalogViewModel
import com.grafitto.laudate.ui.category.CategoryManagementSheet
import com.grafitto.laudate.ui.common.AppLogo
import com.grafitto.laudate.ui.common.AppMessageController
import com.grafitto.laudate.ui.common.AppMessageEffect
import com.grafitto.laudate.ui.common.CollapsingBottomBar
import com.grafitto.laudate.ui.common.rememberBottomBarCollapseState
import com.grafitto.laudate.ui.common.LaudateLinks
import com.grafitto.laudate.ui.celebration.CelebrationEditorScreen
import com.grafitto.laudate.ui.celebration.CelebrationEditorSideEffect
import com.grafitto.laudate.ui.celebration.CelebrationEditorUiIntent
import com.grafitto.laudate.ui.celebration.CelebrationListScreen
import com.grafitto.laudate.ui.celebration.CelebrationListUiIntent
import com.grafitto.laudate.ui.celebration.CelebrationViewModel
import com.grafitto.laudate.ui.celebration.live.LiveCelebrationScreen
import com.grafitto.laudate.ui.editor.SongEditorScreen
import com.grafitto.laudate.ui.editor.SongEditorSideEffect
import com.grafitto.laudate.ui.editor.SongEditorTab
import com.grafitto.laudate.ui.editor.SongEditorToolHost
import com.grafitto.laudate.ui.editor.SongEditorToolPanel
import com.grafitto.laudate.ui.editor.SongEditorUiIntent
import com.grafitto.laudate.ui.editor.SongEditorViewModel
import com.grafitto.laudate.ui.importer.ImportScreen
import com.grafitto.laudate.ui.importer.ImportSideEffect
import com.grafitto.laudate.ui.importer.ImportUiIntent
import com.grafitto.laudate.ui.importer.ImportViewModel
import com.grafitto.laudate.domain.model.tablature.TabDisplayPosition
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.ui.navigation.CanticoraScaffold
import com.grafitto.laudate.ui.navigation.CatalogToolSettings
import com.grafitto.laudate.ui.navigation.CelebrationListToolSettings
import com.grafitto.laudate.ui.navigation.CommonSettingsPanel
import com.grafitto.laudate.ui.navigation.NavBrandLogoSize
import com.grafitto.laudate.ui.navigation.NavEntry
import com.grafitto.laudate.ui.navigation.RhythmToolSettings
import com.grafitto.laudate.ui.navigation.SongReaderToolSettings
import com.grafitto.laudate.ui.navigation.TablatureToolSettings
import com.grafitto.laudate.ui.manual.ManualScreen
import com.grafitto.laudate.ui.manual.ManualViewModel
import com.grafitto.laudate.ui.rhythm.AudioTrackRhythmPlayer
import com.grafitto.laudate.ui.rhythm.RhythmPatternEditorScreen
import com.grafitto.laudate.ui.rhythm.RhythmPatternSideEffect
import com.grafitto.laudate.ui.rhythm.RhythmPatternUiIntent
import com.grafitto.laudate.ui.rhythm.RhythmPatternViewModel
import com.grafitto.laudate.ui.rhythm.RhythmPatternsScreen
import com.grafitto.laudate.ui.song.SongDetailScreen
import com.grafitto.laudate.ui.song.SongDetailSideEffect
import com.grafitto.laudate.ui.song.SongDetailUiIntent
import com.grafitto.laudate.ui.song.SongDetailUiState
import com.grafitto.laudate.ui.song.SongDetailViewModel
import com.grafitto.laudate.ui.search.SongSearchViewModel
import com.grafitto.laudate.ui.tablature.TablatureEditorScreen
import com.grafitto.laudate.ui.tablature.TablatureScreen
import com.grafitto.laudate.ui.tablature.TablatureSectionRef
import com.grafitto.laudate.ui.tablature.TablatureSideEffect
import com.grafitto.laudate.ui.tablature.TablatureUiIntent
import com.grafitto.laudate.ui.tablature.TablatureViewModel
import com.grafitto.laudate.ui.theme.LaudateTheme
import kotlinx.coroutines.launch
import java.io.File

sealed interface AppScreen {
    data object Catalog : AppScreen

    data class SongDetail(
        val songId: String,
    ) : AppScreen

    data class SongEditor(
        val songId: String?,
    ) : AppScreen

    data object CelebrationList : AppScreen

    data class CelebrationEditor(
        val celebrationId: String?,
    ) : AppScreen

    data class LiveCelebration(
        val celebrationId: String,
    ) : AppScreen

    data object RhythmPatterns : AppScreen

    data object RhythmPatternEditor : AppScreen

    /** Listado de tablaturas de un canto (ADR-017, Feature T). */
    data class Tablatures(val songId: String, val songTitle: String) : AppScreen

    /** Editor de una tablatura (ADR-017). */
    data object TablatureEditor : AppScreen

    /** Copia de seguridad y restauración (incluye guardar en Google Drive vía SAF). */
    data object Backup : AppScreen

    /** Importar contenido: catálogo, repertorio, canciones ChordPro o copia de seguridad. */
    data object Import : AppScreen

    data object Manual : AppScreen
}


/** Ids de las acciones del pie del menú (no son pantallas: abren hojas o enlaces). */
private const val AndroidActionCategories = "action-categories"
private const val AndroidActionSupport = "action-support"
private const val AndroidActionAbout = "action-about"

/** Entradas del **menú de navegación** (panel izquierdo). */
private val androidNavEntries = listOf(
    NavEntry("catalog", "Canciones", Icons.AutoMirrored.Filled.List),
    NavEntry("celebrations", "Misas y Eventos", Icons.Default.DateRange),
    NavEntry("rhythms", "Patrones Rítmicos", Icons.Default.MusicNote),
    NavEntry("import", "Importar", Icons.Default.FileOpen),
    NavEntry("backup", "Copia de seguridad", Icons.Default.CloudSync)
)

/** Acciones al pie del menú (no navegan: abren hojas o enlaces). */
private val androidNavActions = listOf(
    NavEntry(AndroidActionCategories, "Gestión de Categorías", Icons.Default.Settings),
    NavEntry("manual", "Manual de uso", Icons.Default.MenuBook),
    NavEntry(AndroidActionSupport, "Apoyar", Icons.Default.VolunteerActivism),
    NavEntry(AndroidActionAbout, "Acerca de Canticora", Icons.Default.Info)
)

/** Id de menú que corresponde a cada pantalla (para marcar la entrada activa). */
private fun androidScreenId(screen: AppScreen): String = when (screen) {
    is AppScreen.Catalog, is AppScreen.SongDetail, is AppScreen.SongEditor,
    is AppScreen.Tablatures, is AppScreen.TablatureEditor -> "catalog"
    is AppScreen.CelebrationList, is AppScreen.CelebrationEditor,
    is AppScreen.LiveCelebration -> "celebrations"
    is AppScreen.RhythmPatterns, is AppScreen.RhythmPatternEditor -> "rhythms"
    is AppScreen.Backup -> "backup"
    is AppScreen.Import -> "import"
    is AppScreen.Manual -> "manual"
}

/** Pantalla destino de una entrada de menú (conserva el canto abierto si aplica). */
private fun androidScreenFor(id: String, current: AppScreen): AppScreen = when (id) {
    "catalog" -> when (current) {
        // Volver a «Canciones» desde el detalle/editor del canto conserva el canto abierto.
        is AppScreen.SongDetail -> current
        else -> AppScreen.Catalog
    }
    "celebrations" -> AppScreen.CelebrationList
    "rhythms" -> AppScreen.RhythmPatterns
    "backup" -> AppScreen.Backup
    "import" -> AppScreen.Import
    "manual" -> AppScreen.Manual
    else -> current
}

/** Nombre de la herramienta activa, para encabezar su zona de ajustes en el panel derecho. */
/** Nombre de la sección activa, para que el panel de ajustes diga dónde está el usuario. */
private fun androidSectionTitleFor(screen: AppScreen): String = when (screen) {
    is AppScreen.Catalog, is AppScreen.SongDetail, is AppScreen.SongEditor,
    is AppScreen.Tablatures, is AppScreen.TablatureEditor -> "Canciones"
    is AppScreen.CelebrationList, is AppScreen.CelebrationEditor,
    is AppScreen.LiveCelebration -> "Misas y Eventos"
    is AppScreen.RhythmPatterns, is AppScreen.RhythmPatternEditor -> "Patrones rítmicos"
    is AppScreen.Import -> "Importar"
    is AppScreen.Backup -> "Copia de seguridad"
    is AppScreen.Manual -> "Manual de uso"
}

/**
 * Ajustes **de la herramienta activa** para el panel derecho. El panel no es el mismo en todas las
 * pantallas: cambia con la sección, y ofrece lo que de verdad se puede ajustar **donde está** el
 * usuario —orden y filtros del catálogo, tipo de repertorio, notación de los ritmos, instrumento y
 * compás de la tablatura, o compás, tonalidad, tempo y cejilla del canto—. Las pantallas sin
 * ajustes propios devuelven `null` y el panel solo muestra los comunes.
 */
@Composable
private fun androidToolSettingsFor(
    screen: AppScreen,
    catalogViewModel: SongCatalogViewModel,
    celebrationViewModel: CelebrationViewModel,
    rhythmViewModel: RhythmPatternViewModel,
    tablatureViewModel: TablatureViewModel,
    songEditorViewModel: SongEditorViewModel,
    songDetailViewModel: SongDetailViewModel?,
    songEditorToolHost: SongEditorToolHost,
    onAddAttachmentFileClick: (() -> Unit)?,
    onOpenAttachmentClick: ((SongAttachment) -> Unit)?,
    onExportAttachmentClick: ((SongAttachment) -> Unit)?,
    /** Estado del canto que el Modo Celebración está mostrando (para sus ajustes de lectura). */
    celebrationSongState: SongDetailUiState = SongDetailUiState(),
    /** Cómo hablarle a ese canto desde el panel (tamaño de letra, de tablatura y de cifra). */
    celebrationSongIntent: ((SongDetailUiIntent) -> Unit)? = null
): (@Composable () -> Unit)? = when (screen) {
    is AppScreen.Catalog -> {
        val state by catalogViewModel.uiState.collectAsState()
        val panel: @Composable () -> Unit = {
            CatalogToolSettings(
                criteria = state.searchCriteria,
                onCriteriaChange = {
                    catalogViewModel.processIntent(SongCatalogUiIntent.UpdateSearchCriteria(it))
                },
                moments = state.categories
                    .filter { it.kind == LiturgicalCategoryKind.MASS_MOMENT }
                    .map { it.name }
            )
        }
        panel
    }

    is AppScreen.CelebrationList -> {
        val state by celebrationViewModel.listUiState.collectAsState()
        val panel: @Composable () -> Unit = {
            CelebrationListToolSettings(
                typeFilter = state.selectedTypeFilter,
                onTypeFilterChange = {
                    celebrationViewModel.processListIntent(CelebrationListUiIntent.FilterByType(it))
                }
            )
        }
        panel
    }

    is AppScreen.RhythmPatterns, is AppScreen.RhythmPatternEditor -> {
        val state by rhythmViewModel.uiState.collectAsState()
        val panel: @Composable () -> Unit = {
            RhythmToolSettings(
                notationMode = state.notationMode,
                onNotationModeChange = {
                    rhythmViewModel.processIntent(RhythmPatternUiIntent.SetNotationMode(it))
                },
                gridResolution = state.gridResolution,
                onGridResolutionChange = {
                    rhythmViewModel.processIntent(RhythmPatternUiIntent.SetGridResolution(it))
                }
            )
        }
        panel
    }

    is AppScreen.Tablatures, is AppScreen.TablatureEditor -> {
        val state by tablatureViewModel.uiState.collectAsState()
        val panel: @Composable () -> Unit = {
            TablatureToolSettings(
                uiState = state,
                onIntent = tablatureViewModel::processIntent
            )
        }
        panel
    }

    is AppScreen.SongEditor -> {
        val state by songEditorViewModel.uiState.collectAsState()
        // En la pestaña «Tablaturas» el panel derecho pasa a ser el de la tablatura: la herramienta
        // activa manda, como en un editor gráfico.
        val tabState by tablatureViewModel.uiState.collectAsState()
        val panel: @Composable () -> Unit = {
            if (state.editorTab == SongEditorTab.TABLATURES && tabState.isEditorOpen) {
                TablatureToolSettings(
                    uiState = tabState,
                    onIntent = tablatureViewModel::processIntent
                )
            } else {
                SongEditorToolPanel(
                    host = songEditorToolHost,
                    uiState = state,
                    onIntent = songEditorViewModel::processIntent,
                    onAddAttachmentFileClick = onAddAttachmentFileClick,
                    onOpenAttachmentClick = onOpenAttachmentClick,
                    onExportAttachmentClick = onExportAttachmentClick,
                    // (D-82) Tamaño de la tablatura en la vista previa: comparte la preferencia
                    // global con el visor y el modo canto.
                    hasTablature = tabState.tablatures.isNotEmpty() ||
                        (tabState.isEditorOpen && tabState.isEditorValid),
                    tablatureScale = tabState.tablatureScale,
                    onTablatureScaleChange = {
                        tablatureViewModel.processIntent(TablatureUiIntent.SetTablatureScale(it))
                    }
                )
            }
        }
        panel
    }

    is AppScreen.SongDetail -> {
        val detailViewModel = songDetailViewModel
        if (detailViewModel == null) {
            null
        } else {
            val state by detailViewModel.uiState.collectAsState()
            val panel: @Composable () -> Unit = {
                SongReaderToolSettings(
                    hasRhythm = state.rhythmPattern != null || state.sectionRhythmPatterns.isNotEmpty(),
                    rhythmNotationMode = state.rhythmNotationMode,
                    onRhythmNotationChange = {
                        detailViewModel.processIntent(SongDetailUiIntent.ChangeRhythmNotationMode(it))
                    },
                    fontSizeSp = state.fontSizeSp,
                    onFontSizeChange = {
                        detailViewModel.processIntent(SongDetailUiIntent.UpdateFontSize(it))
                    },
                    onFontSizeChangeFinished = {
                        detailViewModel.processIntent(SongDetailUiIntent.SaveFontSize)
                    },
                    // **Lo que se VE en pantalla**, no lo que hay guardado (regla del usuario:
                    // «siempre que aparezca letra o tablatura»). `effectiveDisplayMode` ya cae a
                    // «Letra y acordes» cuando el modo pide tablatura pero el canto no tiene, así
                    // que es la única fuente de verdad de qué se está mostrando.
                    hasTablature = state.effectiveDisplayMode.showsTablature &&
                        state.visibleTablatures.isNotEmpty(),
                    hasLyrics = state.effectiveDisplayMode.showsLyrics,
                    tablatureScale = state.tablatureScale,
                    onTablatureScaleChange = {
                        detailViewModel.processIntent(SongDetailUiIntent.UpdateTablatureScale(it))
                    },
                    onTablatureScaleChangeFinished = {
                        detailViewModel.processIntent(SongDetailUiIntent.SaveTablatureScale)
                    },
                    tablatureNumberScale = state.tablatureNumberScale,
                    onTablatureNumberScaleChange = {
                        detailViewModel.processIntent(SongDetailUiIntent.UpdateTablatureNumberScale(it))
                    },
                    onTablatureNumberScaleChangeFinished = {
                        detailViewModel.processIntent(SongDetailUiIntent.SaveTablatureNumberScale)
                    }
                )
            }
            panel
        }
    }

    // **Modo Celebración**: el lector del atril tenía panel de ajustes **vacío**, así que no se podía
    // cambiar ni el tamaño de la letra ni el de la tablatura mientras se cantaba (lo reportó el
    // usuario, y con razón). El panel es el mismo del visor —mismos mandos, misma preferencia
    // global— y se alimenta del estado del canto **que se está viendo** en ese momento.
    is AppScreen.LiveCelebration -> {
        val state = celebrationSongState
        val panel: @Composable () -> Unit = {
            SongReaderToolSettings(
                hasRhythm = state.rhythmPattern != null || state.sectionRhythmPatterns.isNotEmpty(),
                rhythmNotationMode = state.rhythmNotationMode,
                onRhythmNotationChange = { mode ->
                    celebrationSongIntent?.invoke(SongDetailUiIntent.ChangeRhythmNotationMode(mode))
                },
                fontSizeSp = state.fontSizeSp,
                onFontSizeChange = { size ->
                    celebrationSongIntent?.invoke(SongDetailUiIntent.UpdateFontSize(size))
                },
                onFontSizeChangeFinished = {
                    celebrationSongIntent?.invoke(SongDetailUiIntent.SaveFontSize)
                },
                hasTablature = state.effectiveDisplayMode.showsTablature &&
                    state.visibleTablatures.isNotEmpty(),
                hasLyrics = state.effectiveDisplayMode.showsLyrics,
                tablatureScale = state.tablatureScale,
                onTablatureScaleChange = { scale ->
                    celebrationSongIntent?.invoke(SongDetailUiIntent.UpdateTablatureScale(scale))
                },
                onTablatureScaleChangeFinished = {
                    celebrationSongIntent?.invoke(SongDetailUiIntent.SaveTablatureScale)
                },
                tablatureNumberScale = state.tablatureNumberScale,
                onTablatureNumberScaleChange = { scale ->
                    celebrationSongIntent?.invoke(SongDetailUiIntent.UpdateTablatureNumberScale(scale))
                },
                onTablatureNumberScaleChangeFinished = {
                    celebrationSongIntent?.invoke(SongDetailUiIntent.SaveTablatureNumberScale)
                }
            )
        }
        panel
    }

    else -> null
}

class MainActivity : ComponentActivity() {

    /**
     * Último archivo recibido desde fuera (por WhatsApp, correo, «Compartir» o el gestor de
     * archivos), ya reconocido.
     *
     * Se expone como estado observable porque el archivo puede llegar de dos formas: al **abrir**
     * la app (`onCreate`, arranque en frío) o **con la app ya abierta** (`onNewIntent`). Sin esto,
     * un archivo compartido con Canticora ya en marcha se perdería en silencio.
     */
    private val incomingFile = MutableStateFlow<IncomingLaudateFile?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as CanticoraApp).container

        // Arranque en frío: la app se abre *para* ver el archivo.
        incomingFile.value = readLaudateDeepLink(this)

        setContent {
            LaudateTheme {
                // Archivo recibido, tanto al arrancar como con la app ya abierta.
                val deepLinkPayload by incomingFile.collectAsState()

                // Las huellas de contenido que falten se completan en segundo plano. Es idempotente
                // y no bloquea la UI; el cancionero ya no se embarca en la app (ver ADR-011).
                LaunchedEffect(Unit) {
                    appContainer.backfillSongContentHashesUseCase()
                }

                // **La pantalla sobrevive al giro.** `remember` se pierde cuando Android recrea la
                // Activity al rotar, y la app volvía al catálogo en mitad del Modo Celebración (y en
                // cualquier otra pantalla). Se guarda el **código de ruta** en el estado guardado y
                // se reconstruye la pantalla al volver.
                var currentScreen by rememberSaveable(
                    stateSaver = androidx.compose.runtime.saveable.Saver<AppScreen, String>(
                        save = { appScreenToRouteCode(it) },
                        restore = { appScreenFromRouteCode(it) }
                    )
                ) { mutableStateOf<AppScreen>(AppScreen.Catalog) }
                var showCategorySheet by remember { mutableStateOf(false) }
                var showAboutDialog by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val messages = remember { AppMessageController() }
                // Barra inferior que se oculta al bajar y reaparece al subir (como Gmail): el
                // contenido aprovecha todo el alto. El umbral en dp se convierte a px.
                val density = LocalDensity.current
                val bottomBarCollapse = rememberBottomBarCollapseState(
                    thresholdPx = with(density) { 48.dp.toPx() }
                )
                val showsBottomBar = currentScreen is AppScreen.Catalog ||
                    currentScreen is AppScreen.CelebrationList ||
                    currentScreen is AppScreen.RhythmPatterns
                // Se activa solo en las pantallas con barra inferior; al salir, se restaura.
                LaunchedEffect(showsBottomBar) { bottomBarCollapse.enabled = showsBottomBar }
                val activityContext = LocalContext.current

                // Reproductor de audio del editor de ritmos (RF-306). Vive a nivel de actividad
                // para no instanciar un AudioTrack en cada recomposición; el ViewModel lo detiene
                // al cerrar el editor.
                val rhythmPlayer = remember { AudioTrackRhythmPlayer() }

                // Copia de seguridad pendiente de restaurar al abrirla desde el gestor de archivos.
                var pendingBackupUri by remember { mutableStateOf<String?>(null) }
                // Archivo recibido desde fuera, ya reconocido, pendiente de confirmar el usuario.
                var incomingFile by remember { mutableStateOf<DetectedLaudateFile?>(null) }
                var incomingFileName by remember { mutableStateOf<String?>(null) }
                var incomingArchiveUri by remember { mutableStateOf<String?>(null) }

                // La app se registra como visor de archivos de Canticora: al abrirlos desde el gestor
                // de documentos, recibirlos por WhatsApp o compartirlos, llegan aquí. El contenido se
                // reconoce automáticamente y se ofrece importarlo desde la pantalla «Importar».
                LaunchedEffect(deepLinkPayload) {
                    val payload = deepLinkPayload ?: return@LaunchedEffect
                    incomingFile = payload.detected
                    incomingFileName = payload.fileName
                    incomingArchiveUri = payload.archiveUri
                    currentScreen = AppScreen.Import
                    // Consumido: se limpia para que no vuelva a abrirse al recomponer.
                    this@MainActivity.incomingFile.value = null
                }

                // ViewModels
                val catalogViewModel =
                    viewModel<SongCatalogViewModel> {
                        SongCatalogViewModel(
                            songRepository = appContainer.songRepository,
                            categoryRepository = appContainer.categoryRepository,
                            celebrationRepository = appContainer.celebrationRepository,
                            addSongToCelebrationUseCase = appContainer.addSongToCelebrationUseCase,
                            searchSongsUseCase = appContainer.searchSongsUseCase,
                            countSongsByCategoryUseCase = appContainer.countSongsByCategoryUseCase,
                            ensureCategoriesForImportUseCase = appContainer.ensureCategoriesForImportUseCase,
                            preferencesRepository = appContainer.preferencesRepository,
                            attachmentRepository = appContainer.attachmentRepository,
                            tablatureRepository = appContainer.tablatureRepository
                        )
                    }
                val searchViewModel =
                    viewModel<SongSearchViewModel> {
                        SongSearchViewModel(
                            appContainer.songRepository,
                            appContainer.categoryRepository,
                            appContainer.searchSongsUseCase
                        )
                    }
                val celebrationViewModel =
                    viewModel<CelebrationViewModel> {
                        CelebrationViewModel(
                            appContainer.celebrationRepository,
                            appContainer.songRepository,
                            appContainer.categoryRepository,
                            appContainer.importCelebrationBundleUseCase,
                            appContainer.appVersionName,
                            preferencesRepository = appContainer.preferencesRepository,
                            tablatureRepository = appContainer.tablatureRepository,
                            attachmentRepository = appContainer.attachmentRepository
                        )
                    }

                // El ViewModel de ritmos se comparte con la pantalla del editor: así el patrón en
                // construcción sobrevive a la navegación entre listado y editor. Vive a nivel raíz
                // para que sus efectos (compartir, copiar, deshacer) se atiendan también desde el editor.
                val rhythmViewModel =
                    viewModel<RhythmPatternViewModel> {
                        RhythmPatternViewModel(
                            rhythmPatternRepository = appContainer.rhythmPatternRepository,
                            rhythmPlayer = rhythmPlayer,
                            exportRhythmPatternsUseCase = appContainer.exportRhythmPatternsUseCase,
                            importRhythmPatternsUseCase = appContainer.importRhythmPatternsUseCase
                        )
                    }

                // El repertorio y la copia recibidos se ofrecen en la pantalla «Importar», que es
                // la única que decide qué hacer con el archivo (ver el bloque «is AppScreen.Import»).

                // ViewModel de tablaturas (ADR-017, Feature T): compartido entre el listado del
                // canto y el editor a pantalla completa, de modo que la tablatura en construcción
                // sobrevive a la navegación entre ambos.
                val tablatureViewModel =
                    viewModel<TablatureViewModel> {
                        // `AudioTrackTabNotePlayer`: aviso sonoro al escribir una nota (solo editor).
                        TablatureViewModel(
                            tablatureRepository = appContainer.tablatureRepository,
                            // (D-48) Sin esto los ajustes del pentagrama no se guardarían ni llegarían al PDF.
                            preferencesRepository = appContainer.preferencesRepository,
                            notePlayer = com.grafitto.laudate.ui.tablature.AudioTrackTabNotePlayer(),
                            importTablatureUseCase = appContainer.importTablatureUseCase
                        )
                    }

                // El editor de canto se crea aquí (y no dentro de su pantalla) para que el panel
                // derecho de ajustes pueda leer y cambiar sus metadatos musicales (compás, tonalidad,
                // tempo, cejilla) aunque el panel viva fuera del contenido.
                val songEditorKey = (currentScreen as? AppScreen.SongEditor)?.songId ?: "new"
                val songEditorViewModel =
                    viewModel<SongEditorViewModel>(key = songEditorKey) {
                        SongEditorViewModel(
                            appContainer.songRepository,
                            appContainer.categoryRepository,
                            appContainer.rhythmPatternRepository,
                            detectDuplicateSongUseCase = appContainer.detectDuplicateSongUseCase,
                            attachmentRepository = appContainer.attachmentRepository,
                            addAttachmentToSongUseCase = appContainer.addAttachmentToSongUseCase,
                            deleteSongAttachmentUseCase = appContainer.deleteSongAttachmentUseCase,
                            attachmentStore = appContainer.attachmentStore,
                            preferencesRepository = appContainer.preferencesRepository
                        )
                    }
                // Estado compartido del **panel de herramientas** del editor de canto (estilo
                // Photoshop): la tira de herramientas vive en el panel derecho y cambia su contenido
                // según la activa. El editor registra en él el insertador en el cursor.
                val songEditorToolHost = remember { SongEditorToolHost() }
                // El visor de canto se crea aquí (y no dentro de su pantalla) para que el panel
                // derecho de ajustes pueda leer y cambiar sus preferencias de lectura (notación del
                // ritmo, tamaño de letra) aunque el panel viva fuera del contenido.
                val songDetailKey = (currentScreen as? AppScreen.SongDetail)?.songId
                val songDetailViewModelOrNull = songDetailKey?.let { songId ->
                    viewModel<SongDetailViewModel>(key = songId) {
                        SongDetailViewModel(
                            appContainer.songRepository,
                            appContainer.getTransposedSongUseCase,
                            appContainer.parseChordProUseCase,
                            appContainer.rhythmPatternRepository,
                            appContainer.attachmentRepository,
                            appContainer.addAttachmentToSongUseCase,
                            appContainer.deleteSongAttachmentUseCase,
                            appContainer.attachmentStore,
                            tablatureRepository = appContainer.tablatureRepository,
                            preferencesRepository = appContainer.preferencesRepository
                        )
                    }
                }
                val catalogState by catalogViewModel.uiState.collectAsState()
                val celebrationListState by celebrationViewModel.listUiState.collectAsState()
                val celebrationEditorState by celebrationViewModel.editorUiState.collectAsState()
                // **Modo Celebración**: el panel de ajustes vive fuera de la pantalla, así que el
                // canto que el atril tiene delante (y su procesador de intents) se iza hasta aquí.
                // Sin esto, el panel salía vacío y no se podía tocar el tamaño de letra ni el de la
                // tablatura durante la celebración (lo reportó el usuario).
                var celebrationSongState by remember { mutableStateOf(SongDetailUiState()) }
                var celebrationSongIntent by remember {
                    mutableStateOf<((SongDetailUiIntent) -> Unit)?>(null)
                }

                // Nomenclatura de acordes: preferencia GLOBAL de la app (Do-Re-Mi / C-D-E).
                // Se observa aquí una sola vez y se propaga a las pantallas que solo muestran tonos.
                val notationSystem by appContainer.preferencesRepository.notationSystem
                    .collectAsState(initial = NotationSystem.LATIN)
                // Modo de visualización y posición de la tablatura: preferencias GLOBALES.
                val songDisplayMode by appContainer.preferencesRepository.songDisplayMode
                    .collectAsState(initial = com.grafitto.laudate.domain.model.song.SongDisplayMode.CHORDS_ABOVE_TEXT)
                val tabDisplayPosition by appContainer.preferencesRepository.tabDisplayPosition
                    .collectAsState(initial = com.grafitto.laudate.domain.model.tablature.TabDisplayPosition.AFTER)
                // Instrumento preferido de tablatura: preferencia GLOBAL (qué afinación se muestra).
                val preferredInstrument by appContainer.preferencesRepository.preferredTablatureInstrument
                    .collectAsState(initial = null)
                // (D-48) Ajustes del pentagrama de tablatura: preferencia GLOBAL que el PDF respeta.
                val tablatureStaffOptions by appContainer.preferencesRepository.tablatureStaffOptions
                    .collectAsState(initial = com.grafitto.laudate.domain.tablature.TabStaffOptions())

                // Mientras se **escribe** una tablatura, los ajustes de lectura (cómo se ve la
                // canción y dónde va la tablatura) no tienen efecto visible: son cosas del visor y
                // de la vista previa. Se ocultan para que el panel no ofrezca mandos que no hacen
                // nada en el contexto en que se está.
                val editorStateForReading by songEditorViewModel.uiState.collectAsState()
                val tabStateForReading by tablatureViewModel.uiState.collectAsState()
                val showReadingSettings = !(
                    currentScreen is AppScreen.Tablatures ||
                        currentScreen is AppScreen.TablatureEditor ||
                        (currentScreen is AppScreen.SongEditor &&
                            editorStateForReading.editorTab == SongEditorTab.TABLATURES &&
                            tabStateForReading.isEditorOpen)
                    )

                // Destino elegido con el selector del sistema (SAF) para el PDF de un repertorio.
                var pendingCelebrationPdf by remember {
                    mutableStateOf<Triple<com.grafitto.laudate.domain.model.celebration.Celebration, List<com.grafitto.laudate.domain.export.CelebrationPdfSong>, com.grafitto.laudate.domain.model.song.SongDisplayMode>?>(null)
                }
                val context = LocalContext.current
                val celebrationPdfSaver = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf")
                ) { uri ->
                    val pending = pendingCelebrationPdf
                    pendingCelebrationPdf = null
                    if (uri == null || pending == null) return@rememberLauncherForActivityResult
                    val (celebration, songs, pdfMode) = pending
                    scope.launch {
                        val patterns = appContainer.rhythmPatternRepository.getAllPatterns().first()
                        val resolvedSongs = songs.map {
                            it.copy(song = com.grafitto.laudate.domain.model.rhythm.RhythmPattern.resolveFor(it.song, patterns))
                        }
                        val result = appContainer.songPdfExporter.writeCelebration(
                            uri,
                            celebration,
                            resolvedSongs,
                            com.grafitto.laudate.domain.export.SongPdfOptions(
                                displayMode = pdfMode,
                                notationSystem = notationSystem,
                                tablatureStaff = tablatureStaffOptions
                            )
                        )
                        val message = result.fold(
                            onSuccess = { "Repertorio exportado a PDF" },
                            onFailure = { error ->
                                "No se pudo exportar el PDF: ${error.message ?: "error desconocido"}"
                            }
                        )
                        messages.show(message)
                    }
                }

                // Efectos del módulo de celebraciones (compartir, imprimir, importar).
                LaunchedEffect(celebrationViewModel) {
                    celebrationViewModel.sideEffect.collect { effect ->
                        when (effect) {
                            is CelebrationEditorSideEffect.NavigateBackOnSaved -> {
                                messages.show("Guardado correctamente")
                                currentScreen = AppScreen.CelebrationList
                            }
                            is CelebrationEditorSideEffect.NavigateBackOnDeleted -> {
                                messages.show("Repertorio eliminado")
                                currentScreen = AppScreen.CelebrationList
                            }
                            is CelebrationEditorSideEffect.ShowMessage -> {
                                messages.show(effect.message)
                            }
                            is CelebrationEditorSideEffect.ShareCelebrationFile -> {
                                shareTextFile(
                                    context = context,
                                    title = effect.name,
                                    fileName = effect.fileName,
                                    content = effect.content,
                                    mimeType = CANTICORA_EVENT_MIME
                                )
                            }
                            is CelebrationEditorSideEffect.ExportCelebrationPdf -> {
                                pendingCelebrationPdf = Triple(effect.celebration, effect.songs, effect.mode)
                                celebrationPdfSaver.launch(effect.fileName)
                            }
                        }
                    }
                }

                // Destino elegido con el sistema (SAF) para el PDF de un canto desde el listado.
                var pendingCatalogPdf by remember { mutableStateOf<Pair<Song, com.grafitto.laudate.domain.model.song.SongDisplayMode>?>(null) }
                val catalogPdfSaver = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/pdf")
                ) { uri ->
                    val pending = pendingCatalogPdf
                    pendingCatalogPdf = null
                    if (uri == null || pending == null) return@rememberLauncherForActivityResult
                    val (song, pdfMode) = pending
                    scope.launch {
                        val patterns = appContainer.rhythmPatternRepository.getAllPatterns().first()
                        val tabs = appContainer.tablatureRepository.getTablaturesForSong(song.id)
                        val result = appContainer.songPdfExporter.write(
                            uri,
                            com.grafitto.laudate.domain.model.rhythm.RhythmPattern.resolveFor(song, patterns),
                            com.grafitto.laudate.domain.export.SongPdfOptions(
                                displayMode = pdfMode,
                                notationSystem = notationSystem,
                                tablatureStaff = tablatureStaffOptions
                            ),
                            tablatures = tabs
                        )
                        val message = result.fold(
                            onSuccess = { "PDF guardado correctamente" },
                            onFailure = { error ->
                                "No se pudo guardar el PDF: ${error.message ?: "error desconocido"}"
                            }
                        )
                        messages.show(message)
                    }
                }

                // Efectos del catálogo: mensajes, compartir y exportar a PDF.
                LaunchedEffect(catalogViewModel) {
                    catalogViewModel.sideEffect.collect { effect ->
                        when (effect) {
                            is SongCatalogSideEffect.ShowMessage -> {
                                messages.show(effect.message)
                            }
                            is SongCatalogSideEffect.ShareSong -> {
                                shareChordPro(context, effect.title, effect.chordPro)
                            }
                            is SongCatalogSideEffect.ShareSongPackage -> {
                                val fileName = "${fileSafeName(effect.title)}.canticora"
                                val uri = createExportShareUri(context, fileName)
                                if (uri == null) {
                                    messages.show("No se pudo preparar el canto para compartir")
                                } else {
                                    scope.launch {
                                        val result = runCatching {
                                            appContainer.exportSongPackageUseCase(uri, effect.songId)
                                        }
                                        result.fold(
                                            onSuccess = {
                                                shareContentUri(
                                                    context = context,
                                                    messages = messages,
                                                    uriString = uri,
                                                    title = effect.title,
                                                    mimeType = CANTICORA_SONG_MIME
                                                )
                                            },
                                            onFailure = { error ->
                                                messages.show(
                                                    "No se pudo compartir: ${error.message ?: "error"}"
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                            is SongCatalogSideEffect.ExportSongPdf -> {
                                pendingCatalogPdf = effect.song to effect.mode
                                catalogPdfSaver.launch(effect.suggestedFileName)
                            }
                            else -> Unit
                        }
                    }
                }

                // Confirmación con «Deshacer» tras añadir un canto a una celebración.
                LaunchedEffect(catalogState.pendingAction) {
                    val pending = catalogState.pendingAction ?: return@LaunchedEffect
                    val result = messages.snackbarHostState.showSnackbar(
                        message = "«${pending.song.title}» añadido a ${pending.celebrationName}",
                        actionLabel = "Deshacer",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        catalogViewModel.processIntent(SongCatalogUiIntent.UndoCelebrationAdd)
                    } else {
                        catalogViewModel.processIntent(SongCatalogUiIntent.DismissPendingAction)
                    }
                }

                // Confirmación con «Deshacer» tras eliminar un repertorio.
                LaunchedEffect(celebrationListState.pendingDelete) {
                    val pending = celebrationListState.pendingDelete ?: return@LaunchedEffect
                    val result = messages.snackbarHostState.showSnackbar(
                        message = "«${pending.name}» eliminado",
                        actionLabel = "Deshacer",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        celebrationViewModel.processListIntent(CelebrationListUiIntent.UndoDeleteCelebration)
                    } else {
                        celebrationViewModel.processListIntent(CelebrationListUiIntent.DismissPendingDelete)
                    }
                }

                // Efectos de ritmos (listado y editor): avisos, compartir, copiar y «Deshacer».
                LaunchedEffect(rhythmViewModel) {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    rhythmViewModel.sideEffect.collect { effect ->
                        when (effect) {
                            is RhythmPatternSideEffect.ShowMessage -> messages.show(effect.message)
                            is RhythmPatternSideEffect.ShowUndoMessage -> {
                                val result = messages.snackbarHostState.showSnackbar(
                                    message = effect.message,
                                    actionLabel = "Deshacer",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    rhythmViewModel.processIntent(RhythmPatternUiIntent.UndoDeletePattern)
                                }
                            }
                            is RhythmPatternSideEffect.ShareContent -> {
                                shareTextFile(
                                    context = context,
                                    title = effect.fileName,
                                    fileName = effect.fileName,
                                    content = effect.content,
                                    mimeType = effect.mimeType
                                )
                            }
                            is RhythmPatternSideEffect.CopyToClipboard -> {
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Ritmo de Canticora", effect.text)
                                )
                                messages.show("Ritmo copiado: pégalo donde quieras")
                            }
                        }
                    }
                }

                // Efectos de tablaturas (ADR-017, Feature T): avisos y «Deshacer» al eliminar.
                // Selector de archivo para importar una partitura como tablatura del canto: MusicXML
                // (.musicxml, .xml, .mxl), MuseScore (.mscz), Guitar Pro 7/8 (.gp, .gpx) y TuxGuitar
                // (.tg). El XML interno del ZIP lo extrae la capa de datos.
                val tablatureFilePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    val (name, _) = queryFileMetadata(this@MainActivity, uri)
                    tablatureViewModel.processIntent(
                        TablatureUiIntent.ImportTablatureFromFile(
                            fileUri = uri.toString(),
                            fileName = name ?: "partitura"
                        )
                    )
                }
                LaunchedEffect(tablatureViewModel) {
                    tablatureViewModel.sideEffect.collect { effect ->
                        when (effect) {
                            is TablatureSideEffect.ShowMessage -> messages.show(effect.message)
                            is TablatureSideEffect.ShowUndoMessage -> {
                                val result = messages.snackbarHostState.showSnackbar(
                                    message = effect.message,
                                    actionLabel = "Deshacer",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    tablatureViewModel.undoDelete()
                                }
                            }
                            is TablatureSideEffect.ShareContent -> {
                                shareTextFile(
                                    context = context,
                                    title = effect.fileName,
                                    fileName = effect.fileName,
                                    content = effect.content,
                                    mimeType = effect.mimeType
                                )
                            }
                            is TablatureSideEffect.RequestTablatureFile ->
                                tablatureFilePicker.launch(
                                    com.grafitto.laudate.domain.transfer.TablatureImportFormat
                                        .pickerMimeTypes
                                        .toTypedArray()
                                )
                        }
                    }
                }

                // Mensajes efímeros (toast) de todos los módulos: un snackbar Material 3.
                AppMessageEffect(messages)

                // Adjuntos del editor de canto: los selectores del sistema (SAF) se declaran aquí,
                // por encima del andamiaje, para que el panel derecho de herramientas pueda usarlos
                // igual que la propia pantalla del editor.
                val editorAttachmentPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    val (name, mime) = queryFileMetadata(this@MainActivity, uri)
                    songEditorViewModel.processIntent(
                        SongEditorUiIntent.AddAttachmentFromUri(uri.toString(), name, mime)
                    )
                }
                var pendingEditorExport by remember { mutableStateOf<SongAttachment?>(null) }
                val editorAttachmentExporter = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("*/*")
                ) { uri ->
                    val attachment = pendingEditorExport
                    pendingEditorExport = null
                    if (uri == null || attachment == null) return@rememberLauncherForActivityResult
                    songEditorViewModel.processIntent(
                        SongEditorUiIntent.ExportAttachment(attachment, uri.toString())
                    )
                }
                val addAttachmentFileClick: () -> Unit = { editorAttachmentPicker.launch(arrayOf("*/*")) }
                val openAttachmentClick: (SongAttachment) -> Unit = { attachment ->
                    songEditorViewModel.processIntent(SongEditorUiIntent.OpenAttachment(attachment))
                }
                val exportAttachmentClick: (SongAttachment) -> Unit = { attachment ->
                    pendingEditorExport = attachment
                    editorAttachmentExporter.launch(attachment.displayName)
                }

                CanticoraScaffold(
                    navEntries = androidNavEntries,
                    currentNavId = androidScreenId(currentScreen),
                    onSelectNav = { id -> currentScreen = androidScreenFor(id, currentScreen) },
                    screenKey = currentScreen,
                    brandTitle = LaudateBrand.NAME,
                    brandTagline = LaudateBrand.TAGLINE,
                    brandLogo = {
                        AppLogo(
                            contentDescription = "Logo Canticora",
                            modifier = Modifier.size(NavBrandLogoSize)
                        )
                    },
                    navActions = androidNavActions,
                    onNavAction = { id ->
                        when (id) {
                            AndroidActionCategories -> showCategorySheet = true
                            "manual" -> currentScreen = AppScreen.Manual
                            AndroidActionSupport ->
                                openUrlExternally(activityContext, LaudateLinks.SUPPORT, messages)
                            AndroidActionAbout -> showAboutDialog = true
                        }
                    },
                    sectionTitle = androidSectionTitleFor(currentScreen),
                    commonSettings = {
                        CommonSettingsPanel(
                            notationSystem = notationSystem,
                            onNotationChange = { system ->
                                scope.launch { appContainer.preferencesRepository.setNotationSystem(system) }
                            },
                            preferredInstrument = preferredInstrument,
                            // **Solo los instrumentos que ESTE canto tiene** (petición del usuario):
                            // si el canto trae únicamente una tablatura de guitarra, ofrecer bajo,
                            // ukelele o laúd son opciones que no harían nada. Sin tablaturas, la
                            // lista va vacía y el desplegable entero desaparece. Ojo: se mira lo que
                            // el canto **tiene**, no lo que se está viendo.
                            instrumentOptions = when (val s = currentScreen) {
                                is AppScreen.SongDetail -> {
                                    val detailState by (songDetailViewModelOrNull?.uiState
                                        ?: kotlinx.coroutines.flow.flowOf(
                                            com.grafitto.laudate.ui.song.SongDetailUiState()
                                        )).collectAsState(
                                        initial = songDetailViewModelOrNull?.uiState?.value
                                            ?: com.grafitto.laudate.ui.song.SongDetailUiState()
                                    )
                                    detailState.tablatureInstruments
                                }
                                is AppScreen.SongEditor ->
                                    tabStateForReading.tablatures.map { it.tuning.name }.distinct()
                                is AppScreen.LiveCelebration ->
                                    celebrationSongState.tablatureInstruments
                                else -> emptyList()
                            },
                            onInstrumentChange = { instrument ->
                                scope.launch {
                                    appContainer.preferencesRepository.setPreferredTablatureInstrument(instrument)
                                }
                            },
                            displayMode = songDisplayMode,
                            onDisplayModeChange = { mode ->
                                scope.launch { appContainer.preferencesRepository.setSongDisplayMode(mode) }
                            },
                            tabDisplayPosition = tabDisplayPosition,
                            onTabDisplayPositionChange = { position ->
                                scope.launch { appContainer.preferencesRepository.setTabDisplayPosition(position) }
                            },
                            // **Solo si ESTA canción tiene tablaturas**: elegir «instrumento preferido»
                            // sirve para decidir cuál de ellas se ve, así que sin ninguna no hay nada que
                            // filtrar y el desplegable solo confunde. Se recolecta el estado del visor
                            // (no se lee `.value`, que no dispara recomposición y dejaría el valor
                            // congelado en el primero que llegara).
                            // **¿El canto TIENE tablaturas?**, no «¿se están viendo?». Todo lo que
                            // depende de que exista tablatura (el desplegable de instrumento y las
                            // opciones de vista) se ata a esto. Atarlo a lo que se ve era un círculo
                            // vicioso: en «Letra y acordes» desaparecían las opciones de tablatura y
                            // no había forma de elegirlas nunca (lo reportó el usuario: «en el modo
                            // canto no me sale la opción de mostrar la tablatura»).
                            hasTablature = when (val s = currentScreen) {
                                is AppScreen.SongDetail -> {
                                    val detailState by (songDetailViewModelOrNull?.uiState
                                        ?: kotlinx.coroutines.flow.flowOf(
                                            com.grafitto.laudate.ui.song.SongDetailUiState()
                                        )).collectAsState(
                                        initial = songDetailViewModelOrNull?.uiState?.value
                                            ?: com.grafitto.laudate.ui.song.SongDetailUiState()
                                    )
                                    detailState.tablatures.isNotEmpty()
                                }
                                is AppScreen.SongEditor -> tabStateForReading.tablatures.isNotEmpty()
                                // **Modo Celebración**: el atril enseña el canto igual que el visor,
                                // así que sus ajustes de lectura dependen del canto que se está
                                // viendo (el que la pantalla publica hacia arriba).
                                is AppScreen.LiveCelebration ->
                                    celebrationSongState.tablatures.isNotEmpty()
                                // El resto de pantallas no muestra tablatura en el panel.
                                else -> false
                            },
                            showReadingSettings = showReadingSettings
                        )
                    },
                    toolSettings = androidToolSettingsFor(
                        screen = currentScreen,
                        catalogViewModel = catalogViewModel,
                        celebrationViewModel = celebrationViewModel,
                        rhythmViewModel = rhythmViewModel,
                        tablatureViewModel = tablatureViewModel,
                        songEditorViewModel = songEditorViewModel,
                        songDetailViewModel = songDetailViewModelOrNull,
                        songEditorToolHost = songEditorToolHost,
                        onAddAttachmentFileClick = addAttachmentFileClick,
                        onOpenAttachmentClick = openAttachmentClick,
                        onExportAttachmentClick = exportAttachmentClick,
                        celebrationSongState = celebrationSongState,
                        celebrationSongIntent = celebrationSongIntent
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Scaffold(
                        snackbarHost = { SnackbarHost(messages.snackbarHostState) },
                        bottomBar = {
                            if (currentScreen is AppScreen.Catalog || currentScreen is AppScreen.CelebrationList || currentScreen is AppScreen.RhythmPatterns) {
                                CollapsingBottomBar(visible = bottomBarCollapse.isVisible) {
                                    NavigationBar {
                                        NavigationBarItem(
                                            selected = currentScreen is AppScreen.Catalog,
                                            onClick = { currentScreen = AppScreen.Catalog },
                                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Canciones") },
                                            label = { Text("Canciones") },
                                        )
                                        NavigationBarItem(
                                            selected = currentScreen is AppScreen.CelebrationList,
                                            onClick = { currentScreen = AppScreen.CelebrationList },
                                            icon = { Icon(Icons.Default.DateRange, contentDescription = "Misas y Eventos") },
                                            label = { Text("Misas y Eventos") },
                                        )
                                        NavigationBarItem(
                                            selected = currentScreen is AppScreen.RhythmPatterns,
                                            onClick = { currentScreen = AppScreen.RhythmPatterns },
                                            icon = { Icon(Icons.Default.MusicNote, contentDescription = "Patrones Rítmicos") },
                                            label = { Text("Ritmos") },
                                        )
                                    }
                                }
                            }
                        },
                        // La app dibuja edge-to-edge y cada pantalla aplica el inset de la barra de
                        // estado en su propio TopAppBar. El Scaffold raíz NO debe volver a
                        // consumirlo: era el espacio de más que separaba el menú/logo/título de la
                        // barra de notificaciones. Solo se reserva el inset inferior.
                        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                        modifier = Modifier
                            .fillMaxSize()
                            // Captura el scroll de la pantalla visible para plegar la barra inferior.
                            .nestedScroll(bottomBarCollapse.connection),
                    ) { innerPadding ->
                        when (val screen = currentScreen) {
                            is AppScreen.Catalog -> {
                                SongCatalogScreen(
                                    uiState = catalogState,
                                    onIntent = catalogViewModel::processIntent,
                                    onSongClick = { songId -> currentScreen = AppScreen.SongDetail(songId) },
                                    onCreateSongClick = { currentScreen = AppScreen.SongEditor(null) },
                                    notationSystem = notationSystem,
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }

                            is AppScreen.SongDetail -> {
                                // El visor se creó arriba, junto al panel de ajustes: aquí solo se
                                // recupera. La pantalla es `SongDetail`, así que no puede ser nulo.
                                val songDetailViewModel = checkNotNull(songDetailViewModelOrNull)
                                LaunchedEffect(screen.songId) {
                                    songDetailViewModel.processIntent(SongDetailUiIntent.LoadSong(screen.songId))
                                }
                                LaunchedEffect(songDetailViewModel) {
                                    songDetailViewModel.sideEffect.collect { effect ->
                                        when (effect) {
                                            is SongDetailSideEffect.NavigateBack -> {
                                                currentScreen = AppScreen.Catalog
                                            }
                                            is SongDetailSideEffect.ShowMessage -> {
                                                messages.show(effect.message)
                                            }
                                        }
                                    }
                                }

                                // Selector de archivos para añadir un adjunto (audio, vídeo, PDF, imagen…).
                                val attachmentPicker = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.OpenDocument()
                                ) { uri ->
                                    if (uri == null) return@rememberLauncherForActivityResult
                                    val (name, mime) = queryFileMetadata(context, uri)
                                    songDetailViewModel.processIntent(
                                        SongDetailUiIntent.AddAttachmentFromUri(uri.toString(), name, mime)
                                    )
                                }
                                // Destino elegido con el sistema (SAF) para descargar el adjunto.
                                var pendingExportAttachment by remember { mutableStateOf<SongAttachment?>(null) }
                                val attachmentExporter = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.CreateDocument("*/*")
                                ) { uri ->
                                    val attachment = pendingExportAttachment
                                    pendingExportAttachment = null
                                    if (uri == null || attachment == null) return@rememberLauncherForActivityResult
                                    songDetailViewModel.processIntent(
                                        SongDetailUiIntent.ExportAttachment(attachment, uri.toString())
                                    )
                                }

                                val songUiState by songDetailViewModel.uiState.collectAsState()
                                SongDetailScreen(
                                    uiState = songUiState,
                                    onIntent = songDetailViewModel::processIntent,
                                    onNavigateBack = { currentScreen = AppScreen.Catalog },
                                    onEditClick = { currentScreen = AppScreen.SongEditor(screen.songId) },
                                    onToggleFocus = { songDetailViewModel.processIntent(SongDetailUiIntent.ToggleFocusMode) },
                                    onAddAttachmentFileClick = { attachmentPicker.launch(arrayOf("*/*")) },
                                    onTablaturesClick = {
                                        currentScreen = AppScreen.Tablatures(
                                            screen.songId,
                                            songUiState.song?.title.orEmpty()
                                        )
                                    },
                                    onOpenAttachmentExternally = { attachment ->
                                        openAttachmentExternally(this@MainActivity, attachment, messages)
                                    },
                                    onExportAttachmentClick = { attachment ->
                                        pendingExportAttachment = attachment
                                        attachmentExporter.launch(attachment.displayName)
                                    },
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }

                            is AppScreen.SongEditor -> {
                                val editorViewModel = songEditorViewModel
                                LaunchedEffect(screen.songId) {
                                    editorViewModel.processIntent(SongEditorUiIntent.LoadSongForEditing(screen.songId))
                                }

                                // Selector del sistema (SAF) para añadir un audio, vídeo o documento.
                                // (Declarado por encima del andamiaje: lo comparte el panel derecho.)

                                // Destino elegido con el sistema (SAF) para descargar un adjunto.
                                // (Declarado por encima del andamiaje: lo comparte el panel derecho.)

                                // Destino elegido con el selector del sistema (SAF) para el PDF.
                                var pendingPdfSong by remember { mutableStateOf<Pair<Song, com.grafitto.laudate.domain.model.song.SongDisplayMode>?>(null) }
                                val context = LocalContext.current
                                val pdfSaver = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.CreateDocument("application/pdf")
                                ) { uri ->
                                    val pending = pendingPdfSong
                                    pendingPdfSong = null
                                    if (uri == null || pending == null) return@rememberLauncherForActivityResult
                                    val (song, pdfMode) = pending
                                    scope.launch {
                                        val patterns = appContainer.rhythmPatternRepository.getAllPatterns().first()
                                        val tabs = song.id?.let { appContainer.tablatureRepository.getTablaturesForSong(it) }.orEmpty()
                                        val result = appContainer.songPdfExporter.write(
                                            uri,
                                            com.grafitto.laudate.domain.model.rhythm.RhythmPattern.resolveFor(song, patterns),
                                            com.grafitto.laudate.domain.export.SongPdfOptions(
                                                displayMode = pdfMode,
                                                notationSystem = notationSystem,
                                                tablatureStaff = tablatureStaffOptions
                                            ),
                                            tablatures = tabs
                                        )
                                        val message = result.fold(
                                            onSuccess = { "PDF guardado correctamente" },
                                            onFailure = { error ->
                                                "No se pudo guardar el PDF: ${error.message ?: "error desconocido"}"
                                            }
                                        )
                                        messages.show(message)
                                    }
                                }

                                LaunchedEffect(editorViewModel) {
                                    editorViewModel.sideEffect.collect { effect ->
                                        when (effect) {
                                            is SongEditorSideEffect.NavigateBackOnSaved -> {
                                                messages.show("Canción guardada correctamente")
                                                currentScreen = AppScreen.Catalog
                                            }
                                            is SongEditorSideEffect.NavigateBackOnDeleted -> {
                                                messages.show("Canción eliminada")
                                                currentScreen = AppScreen.Catalog
                                            }
                                            is SongEditorSideEffect.NavigateBack -> {
                                                currentScreen = AppScreen.Catalog
                                            }
                                            is SongEditorSideEffect.ShowMessage -> {
                                                messages.show(effect.message)
                                            }
                                            is SongEditorSideEffect.ShareSong -> {
                                                shareChordPro(context, effect.title, effect.chordPro)
                                            }
                                            is SongEditorSideEffect.ShareSongPackage -> {
                                                val fileName = "${fileSafeName(effect.title)}.canticora"
                                                val uri = createExportShareUri(context, fileName)
                                                if (uri == null) {
                                                    messages.show("No se pudo preparar el canto para compartir")
                                                } else {
                                                    scope.launch {
                                                        val result = runCatching {
                                                            appContainer.exportSongPackageUseCase(uri, effect.songId)
                                                        }
                                                        result.fold(
                                                            onSuccess = {
                                                                shareContentUri(
                                                                    context = context,
                                                                    messages = messages,
                                                                    uriString = uri,
                                                                    title = effect.title,
                                                                    mimeType = CANTICORA_SONG_MIME
                                                                )
                                                            },
                                                            onFailure = { error ->
                                                                messages.show(
                                                                    "No se pudo compartir: ${error.message ?: "error"}"
                                                                )
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                            is SongEditorSideEffect.ExportSongPdf -> {
                                                pendingPdfSong = effect.song to effect.mode
                                                pdfSaver.launch(effect.suggestedFileName)
                                            }
                                            is SongEditorSideEffect.OpenAttachment -> {
                                                openAttachmentExternally(this@MainActivity, effect.attachment, messages)
                                            }
                                        }
                                    }
                                }
                                val editorState by editorViewModel.uiState.collectAsState()
                                // Tablaturas del canto (tercera pestaña del editor): se cargan al
                                // abrir el editor de una canción ya guardada.
                                val tabStateForEditor by tablatureViewModel.uiState.collectAsState()
                                LaunchedEffect(editorState.songId) {
                                    // Un canto nuevo aún sin guardar no tiene id: se suelta el canto
                                    // anterior para que su tablatura no se cuele aquí.
                                    if (editorState.songId == null) {
                                        tablatureViewModel.processIntent(TablatureUiIntent.ClearSong)
                                    }
                                    editorState.songId?.let { songId ->
                                        tablatureViewModel.processIntent(
                                            TablatureUiIntent.LoadForSong(songId, editorState.title, editorState.originalKey)
                                        )
                                        // Secciones del canto: permiten anclar la tablatura a una
                                        // parte (estribillo, estrofa…) desde el panel de herramientas.
                                        val song = appContainer.songRepository.getSongById(songId).first()
                                        tablatureViewModel.setAvailableSections(
                                            song?.sections?.map { sec ->
                                                TablatureSectionRef(
                                                    sec.id,
                                                    sec.headerTitle?.takeIf { l -> l.isNotBlank() }
                                                        ?: com.grafitto.laudate.ui.theme.SectionColors.labelFor(sec.sectionType.name)
                                                )
                                            }.orEmpty()
                                        )
                                    }
                                }
                                SongEditorScreen(
                                    uiState = editorState,
                                    onIntent = editorViewModel::processIntent,
                                    onAddAttachmentFileClick = addAttachmentFileClick,
                                    onOpenAttachmentClick = openAttachmentClick,
                                    onExportAttachmentClick = exportAttachmentClick,
                                    tablatureUiState = tabStateForEditor,
                                    onTablatureIntent = tablatureViewModel::processIntent,
                                    previewDisplayMode = songDisplayMode,
                                    previewTabPosition = tabDisplayPosition,
                                    onPreviewDisplayModeChange = { mode ->
                                        scope.launch { appContainer.preferencesRepository.setSongDisplayMode(mode) }
                                    },
                                    onPreviewTabPositionChange = { position ->
                                        scope.launch { appContainer.preferencesRepository.setTabDisplayPosition(position) }
                                    },
                                    // (D-82) La vista previa dibuja la tablatura con el tamaño global.
                                    previewTablatureScale = tabStateForEditor?.tablatureScale ?: 1f,
                                    previewTablatureStaffOptions = tabStateForEditor?.editorStaffOptions
                                        ?: com.grafitto.laudate.domain.tablature.TabStaffOptions(),
                                    toolHost = songEditorToolHost,
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }

                            is AppScreen.CelebrationList -> {
                                // Selector de archivos (SAF) para importar un repertorio recibido.
                                val importLauncher = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.OpenDocument()
                                ) { uri ->
                                    if (uri == null) return@rememberLauncherForActivityResult
                                    val content = readSharedText(context, uri)
                                    if (content == null) {
                                        messages.show("No se pudo leer el archivo")
                                    } else {
                                        celebrationViewModel.processListIntent(
                                            CelebrationListUiIntent.ImportCelebrationFile(content)
                                        )
                                    }
                                }
                                CelebrationListScreen(
                                    uiState = celebrationListState,
                                    onIntent = celebrationViewModel::processListIntent,
                                    onCelebrationClick = { id ->
                                        celebrationViewModel.processEditorIntent(CelebrationEditorUiIntent.LoadForEdit(id))
                                        currentScreen = AppScreen.CelebrationEditor(id)
                                    },
                                    onLiveModeClick = { id -> currentScreen = AppScreen.LiveCelebration(id) },
                                    onCreateCelebrationClick = {
                                        celebrationViewModel.processEditorIntent(CelebrationEditorUiIntent.LoadForEdit(null))
                                        currentScreen = AppScreen.CelebrationEditor(null)
                                    },
                                    // `*/*` como único tipo: los archivos de Canticora (`.canticora`) no
                                    // tienen un MIME registrado en el sistema, así que restringir la
                                    // lista hacía que el explorador de archivos los ocultara.
                                    onImportClick = { importLauncher.launch(arrayOf("*/*")) },
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }

                            is AppScreen.CelebrationEditor -> {
                                // Aseguramos que el repertorio esté cargado en el editor (al llegar por
                                // un archivo abierto, la pantalla puede mostrarse antes de la carga).
                                LaunchedEffect(celebrationEditorState.celebrationId, screen.celebrationId) {
                                    if (screen.celebrationId != null && celebrationEditorState.celebrationId == null) {
                                        celebrationViewModel.processEditorIntent(
                                            CelebrationEditorUiIntent.LoadForEdit(screen.celebrationId)
                                        )
                                    }
                                }
                                val searchState by searchViewModel.uiState.collectAsState()
                                CelebrationEditorScreen(
                                    uiState = celebrationEditorState,
                                    onIntent = celebrationViewModel::processEditorIntent,
                                    onNavigateBack = { currentScreen = AppScreen.CelebrationList },
                                    searchState = searchState,
                                    onSearchIntent = searchViewModel::processIntent,
                                    notationSystem = notationSystem,
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }

                            is AppScreen.LiveCelebration -> {
                                LiveCelebrationScreen(
                                    celebrationId = screen.celebrationId,
                                    celebrationRepository = appContainer.celebrationRepository,
                                    songRepository = appContainer.songRepository,
                                    getTransposedSongUseCase = appContainer.getTransposedSongUseCase,
                                    parseChordProUseCase = appContainer.parseChordProUseCase,
                                    preferencesRepository = appContainer.preferencesRepository,
                                    tablatureRepository = appContainer.tablatureRepository,
                                    onCloseLiveMode = { currentScreen = AppScreen.CelebrationList },
                                    // El canto activo se publica hacia arriba: el panel de ajustes
                                    // (que vive fuera de esta pantalla) lo necesita para ofrecer el
                                    // tamaño de letra, el de la tablatura y el de la cifra, y para
                                    // que al deslizar de canto los mandos cambien con él.
                                    onActiveSongChange = { state, intent ->
                                        celebrationSongState = state
                                        celebrationSongIntent = intent
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            is AppScreen.Manual -> {
                                val manualViewModel = viewModel<ManualViewModel>()
                                val manualState by manualViewModel.uiState.collectAsState()
                                ManualScreen(
                                    uiState = manualState,
                                    sideEffects = manualViewModel.sideEffect,
                                    onIntent = manualViewModel::processIntent,
                                    onNavigateBack = { currentScreen = AppScreen.Catalog },
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }

                            is AppScreen.RhythmPatterns -> {
                                val rhythmState by rhythmViewModel.uiState.collectAsState()
                                // Al abrir un patrón (nuevo, existente o copia) el VM marca el editor
                                // como abierto y se navega a su pantalla independiente.
                                LaunchedEffect(rhythmState.isEditorOpen) {
                                    if (rhythmState.isEditorOpen) currentScreen = AppScreen.RhythmPatternEditor
                                }
                                RhythmPatternsScreen(
                                    uiState = rhythmState,
                                    onIntent = rhythmViewModel::processIntent,
                                    onNavigateBack = { currentScreen = AppScreen.Catalog },
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }

                            is AppScreen.RhythmPatternEditor -> {
                                val rhythmState by rhythmViewModel.uiState.collectAsState()
                                // Al guardar o cancelar, el VM cierra el editor y se vuelve al listado.
                                LaunchedEffect(rhythmState.isEditorOpen) {
                                    if (!rhythmState.isEditorOpen) currentScreen = AppScreen.RhythmPatterns
                                }
                                // Al SALIR de la pantalla (por cualquier vía) se detiene el audio:
                                // el reproductor es de ámbito de actividad y no se destruye con la vista.
                                DisposableEffect(Unit) {
                                    onDispose {
                                        rhythmViewModel.processIntent(RhythmPatternUiIntent.StopPreview)
                                    }
                                }
                                RhythmPatternEditorScreen(
                                    uiState = rhythmState,
                                    onIntent = rhythmViewModel::processIntent,
                                    onNavigateBack = { rhythmViewModel.processIntent(RhythmPatternUiIntent.CloseEditor) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            is AppScreen.Tablatures -> {
                                val tabState by tablatureViewModel.uiState.collectAsState()
                                // Al abrir el editor (nueva, existente o copia) el VM lo marca como
                                // abierto y se navega a su pantalla independiente.
                                LaunchedEffect(tabState.isEditorOpen) {
                                    if (tabState.isEditorOpen) currentScreen = AppScreen.TablatureEditor
                                }
                                LaunchedEffect(screen.songId) {
                                    // Se trae el canto para conocer sus secciones (asociar la
                                    // tablatura a una parte) y su tonalidad (tonalidad escrita).
                                    val song = appContainer.songRepository.getSongById(screen.songId).first()
                                    tablatureViewModel.processIntent(
                                        TablatureUiIntent.LoadForSong(screen.songId, screen.songTitle, song?.originalKey.orEmpty())
                                    )
                                    tablatureViewModel.setAvailableSections(
                                        song?.sections?.map { s ->
                                            TablatureSectionRef(
                                                s.id,
                                                s.headerTitle?.takeIf { l -> l.isNotBlank() }
                                                    ?: com.grafitto.laudate.ui.theme.SectionColors.labelFor(s.sectionType.name)
                                            )
                                        }.orEmpty()
                                    )
                                }
                                TablatureScreen(
                                    uiState = tabState,
                                    onIntent = tablatureViewModel::processIntent,
                                    onNavigateBack = { currentScreen = AppScreen.SongDetail(screen.songId) },
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }

                            is AppScreen.TablatureEditor -> {
                                val tabState by tablatureViewModel.uiState.collectAsState()
                                // Al guardar o cancelar, el VM cierra el editor y se vuelve al listado.
                                LaunchedEffect(tabState.isEditorOpen) {
                                    if (!tabState.isEditorOpen) currentScreen = AppScreen.Catalog
                                }
                                TablatureEditorScreen(
                                    uiState = tabState,
                                    onIntent = tablatureViewModel::processIntent,
                                    onNavigateBack = { tablatureViewModel.processIntent(TablatureUiIntent.CloseEditor) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            is AppScreen.Backup -> {
                                val backupViewModel = viewModel<BackupViewModel> {
                                    BackupViewModel(
                                        appContainer.songRepository,
                                        appContainer.attachmentRepository,
                                        appContainer.exportBackupUseCase,
                                        appContainer.importBackupUseCase
                                    )
                                }

                                // Destino elegido con el selector del sistema (SAF): incluye Google Drive.
                                val backupExporter = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.CreateDocument(CANTICORA_BACKUP_MIME)
                                ) { uri ->
                                    if (uri == null) return@rememberLauncherForActivityResult
                                    backupViewModel.processIntent(BackupUiIntent.ExportTo(uri.toString()))
                                }
                                // Sincronizar (fusionar): el usuario elige el archivo y se aplica.
                                val backupSyncer = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.OpenDocument()
                                ) { uri ->
                                    if (uri == null) return@rememberLauncherForActivityResult
                                    backupViewModel.processIntent(BackupUiIntent.SyncFrom(uri.toString()))
                                }
                                // Restaurar (destructivo): se analiza el archivo y se muestra su contenido
                                // ANTES de pedir confirmación; hasta entonces no se borra nada.
                                val backupRestorer = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.OpenDocument()
                                ) { uri ->
                                    if (uri == null) return@rememberLauncherForActivityResult
                                    backupViewModel.processIntent(BackupUiIntent.RequestRestore(uri.toString()))
                                }

                                // Una copia recibida desde fuera (WhatsApp, gestor de archivos) se aplica
                                // en modo **sincronizar**: añade sin borrar, que es lo seguro al recibirla.
                                LaunchedEffect(pendingBackupUri, backupViewModel) {
                                    val uri = pendingBackupUri ?: return@LaunchedEffect
                                    backupViewModel.processIntent(BackupUiIntent.SyncFrom(uri))
                                    pendingBackupUri = null
                                }

                                LaunchedEffect(backupViewModel) {
                                    backupViewModel.sideEffect.collect { effect ->
                                        when (effect) {
                                            is BackupSideEffect.ShowMessage -> {
                                                messages.show(effect.message)
                                            }
                                            is BackupSideEffect.RequestExportDestination -> {
                                                backupExporter.launch(effect.suggestedFileName)
                                            }
                                            is BackupSideEffect.RequestSyncFile -> {
                                                backupSyncer.launch(
                                                    arrayOf(CANTICORA_BACKUP_MIME, "application/zip", "application/octet-stream", "*/*")
                                                )
                                            }
                                            is BackupSideEffect.RequestRestoreFile -> {
                                                backupRestorer.launch(
                                                    arrayOf(CANTICORA_BACKUP_MIME, "application/zip", "application/octet-stream", "*/*")
                                                )
                                            }
                                            is BackupSideEffect.ShareArchive -> {
                                                shareContentUri(
                                                    context = context,
                                                    messages = messages,
                                                    uriString = effect.archiveUri,
                                                    title = "Copia de seguridad de Canticora",
                                                    mimeType = CANTICORA_BACKUP_MIME
                                                )
                                            }
                                        }
                                    }
                                }

                                val backupState by backupViewModel.uiState.collectAsState()
                                BackupScreen(
                                    uiState = backupState,
                                    onNavigateBack = { currentScreen = AppScreen.Catalog },
                                    onExportClick = { backupViewModel.requestExport() },
                                    onSyncClick = { backupViewModel.requestSync() },
                                    onRestoreClick = { backupViewModel.requestRestore() },
                                    onShareClick = {
                                        // Se escribe en la caché interna y se comparte por FileProvider:
                                        // así no depende de que el proveedor de destino permita compartir
                                        // su propio URI.
                                        val fileName = backupShareFileName()
                                        val uri = createExportShareUri(context, fileName)
                                        if (uri != null) {
                                            backupViewModel.processIntent(
                                                BackupUiIntent.ExportTo(uri, shareAfter = true)
                                            )
                                        } else {
                                            messages.show("No se pudo preparar la copia para compartir")
                                        }
                                    },
                                    onResolveConflict = { songId, policy ->
                                        backupViewModel.processIntent(BackupUiIntent.ResolveConflict(songId, policy))
                                    },
                                    onApplyPolicyToAll = { policy ->
                                        backupViewModel.processIntent(BackupUiIntent.ApplyPolicyToAll(policy))
                                    },
                                    onConfirmSync = {
                                        backupViewModel.processIntent(BackupUiIntent.ConfirmSync)
                                    },
                                    onCancelSync = {
                                        backupViewModel.processIntent(BackupUiIntent.CancelSync)
                                    },
                                    onConfirmRestore = {
                                        backupViewModel.processIntent(BackupUiIntent.ConfirmRestore)
                                    },
                                    onCancelRestore = {
                                        backupViewModel.processIntent(BackupUiIntent.CancelRestore)
                                    },
                                    onRestoreSafetyBackup = {
                                        backupViewModel.processIntent(BackupUiIntent.RestoreSafetyBackup)
                                    },
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }

                            is AppScreen.Import -> {
                                val importViewModel = viewModel<ImportViewModel> {
                                    ImportViewModel(
                                        appContainer.songRepository,
                                        appContainer.celebrationRepository,
                                        appContainer.categoryRepository,
                                        appContainer.importCatalogBundleUseCase,
                                        appContainer.importCelebrationBundleUseCase,
                                        appContainer.importChordProSongsUseCase,
                                        appContainer.importRhythmPatternsUseCase,
                                        appContainer.importSongPackageUseCase
                                    )
                                }

                                // Contenido pegado (o leído de un archivo): se reconoce al pulsar
                                // «Importar» con el detector de dominio y se pide confirmación.
                                var importContentText by remember { mutableStateOf("") }

                                fun offerContent(text: String, fileName: String?) {
                                    val detected = LaudateFileDetector.detect(
                                        text = text,
                                        mimeType = null,
                                        fileName = fileName
                                    )
                                    importViewModel.processIntent(
                                        ImportUiIntent.OfferContent(detected = detected, content = text)
                                    )
                                }

                                // Un solo botón «Elegir archivo…»: el tipo se decide al leerlo. Antes de
                                // leer el contenido entero se comprueba la cabecera (ZIP o un binario
                                // conocido como vídeo/imagen/PDF), para que un binario nunca se decodifique
                                // como texto (ver bug del MP4 detectado como ChordPro).
                                val contentFileImporter = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.OpenDocument()
                                ) { uri ->
                                    if (uri == null) return@rememberLauncherForActivityResult
                                    val fileName = queryFileMetadata(context, uri).first
                                    val head = readHeadBytes(context, uri)
                                    val isZip = LaudateFileDetector.looksLikeZip(head)
                                    val isMedia = !isZip && LaudateFileDetector.sniffMediaKind(head) != null
                                    val content = if (isZip || isMedia) null else readSharedText(context, uri)
                                    if (!isZip && !isMedia && content == null) {
                                        messages.show("No se pudo leer el archivo")
                                    } else {
                                        val detected = LaudateFileDetector.detect(
                                            text = content,
                                            mimeType = null,
                                            fileName = fileName,
                                            isZip = isZip,
                                            headBytes = head
                                        )
                                        importViewModel.processIntent(
                                            ImportUiIntent.OfferPendingFile(
                                                detected = detected,
                                                fileName = fileName,
                                                // Un ZIP (copia o paquete de canto) se lee con su Uri;
                                                // el resto ya viaja en texto.
                                                archiveUri = if (isZip) uri.toString() else null
                                            )
                                        )
                                    }
                                }

                                // La copia de seguridad se restaura en su propia pantalla (necesita el
                                // Uri con permiso SAF); desde aquí solo se navega a ella.
                                val backupImporter = rememberLauncherForActivityResult(
                                    contract = ActivityResultContracts.OpenDocument()
                                ) { uri ->
                                    if (uri == null) return@rememberLauncherForActivityResult
                                    pendingBackupUri = uri.toString()
                                    currentScreen = AppScreen.Backup
                                }

                                LaunchedEffect(importViewModel) {
                                    importViewModel.sideEffect.collect { effect ->
                                        when (effect) {
                                            is ImportSideEffect.ShowMessage -> {
                                                messages.show(effect.message)
                                            }
                                            is ImportSideEffect.RequestBackupFile -> {
                                                backupImporter.launch(
                                                    arrayOf(CANTICORA_BACKUP_MIME, "application/zip", "application/octet-stream", "*/*")
                                                )
                                            }
                                            // Copia recibida: se abre la pantalla de Copia con su Uri.
                                            is ImportSideEffect.RestoreBackup -> {
                                                pendingBackupUri = effect.uri
                                                currentScreen = AppScreen.Backup
                                            }
                                            // El catálogo y las categorías se releen solos al ser
                                            // reactivos (Flow de Room); no hace falta recargar nada.
                                            is ImportSideEffect.CatalogChanged -> Unit
                                        }
                                    }
                                }

                                // Archivo recibido desde fuera: se registra para que la pantalla
                                // muestre el diálogo «esto es X, ¿lo importo?». Se hace una sola
                                // vez por archivo para no reabrirlo al recomponer.
                                LaunchedEffect(incomingFile) {
                                    val detected = incomingFile ?: return@LaunchedEffect
                                    importViewModel.processIntent(
                                        ImportUiIntent.OfferPendingFile(
                                            detected = detected,
                                            fileName = incomingFileName,
                                            archiveUri = incomingArchiveUri
                                        )
                                    )
                                    incomingFile = null
                                    incomingFileName = null
                                    incomingArchiveUri = null
                                }

                                val importState by importViewModel.uiState.collectAsState()
                                val importStats by importViewModel.stats.collectAsState()
                                ImportScreen(
                                    uiState = importState,
                                    stats = importStats,
                                    onNavigateBack = { currentScreen = AppScreen.Catalog },
                                    contentText = importContentText,
                                    onContentChange = { importContentText = it },
                                    onDetectContent = {
                                        if (importContentText.isBlank()) {
                                            messages.show("Pega el contenido o elige un archivo")
                                        } else {
                                            offerContent(importContentText, null)
                                        }
                                    },
                                    onConfirmContent = {
                                        importViewModel.processIntent(ImportUiIntent.ConfirmPendingContent)
                                        importContentText = ""
                                    },
                                    onDismissContent = {
                                        importViewModel.processIntent(ImportUiIntent.DismissPendingContent)
                                    },
                                    // `*/*` como tipo único: los paquetes de Canticora no tienen MIME
                                    // registrado y restringir la lista ocultaría los ficheros.
                                    onChooseContentFile = { contentFileImporter.launch(arrayOf("*/*")) },
                                    onImportBackupClick = { importViewModel.requestBackupImport() },
                                    onConfirmIncomingFile = { importViewModel.processIntent(ImportUiIntent.ConfirmPendingFile) },
                                    onDismissIncomingFile = { importViewModel.processIntent(ImportUiIntent.DismissPendingFile) },
                                    onResolveImportConflict = { key, policy ->
                                        importViewModel.processIntent(ImportUiIntent.ResolveImportConflict(key, policy))
                                    },
                                    onApplyImportPolicyToAll = { policy ->
                                        importViewModel.processIntent(ImportUiIntent.ApplyImportPolicyToAll(policy))
                                    },
                                    onConfirmImportConflicts = {
                                        importViewModel.processIntent(ImportUiIntent.ConfirmImportConflicts)
                                    },
                                    onCancelImportConflicts = {
                                        importViewModel.processIntent(ImportUiIntent.CancelImportConflicts)
                                    },
                                    modifier = Modifier.padding(innerPadding),
                                )
                            }
                        }
                    }
                }

                if (showAboutDialog) {
                    AboutDialog(onDismiss = { showAboutDialog = false }, appVersionName = appContainer.appVersionName)
                }

                if (showCategorySheet) {
                    CategoryManagementSheet(
                        categories = catalogState.categories,
                        songCounts = catalogState.categorySongCounts,
                        onAddCategory = { newCat ->
                            scope.launch {
                                appContainer.categoryRepository.saveCategory(newCat)
                            }
                        },
                        onEditCategory = { category, newName, newKind ->
                            scope.launch {
                                // 1) Renombra la categoría y propaga el cambio a las canciones y a los
                                //    repertorios que la referencian (el nombre se guarda duplicado).
                                appContainer.updateCategoryUseCase(category.id, newName)
                                // 2) El tipo (momento/tiempo/adicional) se actualiza aparte. CUIDADO:
                                //    hay que usar el nombre NUEVO, porque `category` todavía trae el antiguo.
                                if (category.kind != newKind) {
                                    appContainer.categoryRepository.updateCategory(
                                        category.copy(name = newName.trim(), kind = newKind)
                                    )
                                }
                            }
                        },
                        onDeleteCategory = { catId ->
                            scope.launch {
                                appContainer.categoryRepository.deleteCategory(catId)
                            }
                        },
                        onDismissRequest = { showCategorySheet = false },
                    )
                }
            }
        }
    }

    /**
     * Archivo recibido **con la app ya abierta** (WhatsApp, correo, «Compartir»…).
     *
     * Sin esto, el intent se entregaría a la instancia existente y el archivo se perdería: `onCreate`
     * no vuelve a ejecutarse. Al publicarlo en el estado observable, la UI reacciona igual que en un
     * arranque en frío.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readLaudateDeepLink(this)?.let { incomingFile.value = it }
    }
}

/**
 * Comparte el ChordPro de una canción con la **hoja nativa de Android** (WhatsApp, Telegram,
 * correo, Drive…).
 *
 * Se envía como archivo `.chordpro` a través de un `FileProvider` y, además, como texto plano,
 * de modo que quien lo reciba pueda importarlo como documento o pegarlo directamente en otro
 * dispositivo con Canticora. El archivo temporal se escribe en el almacén interno y se expone con
 * permiso de lectura temporal: no se requiere ningún permiso de almacenamiento.
 */
private fun shareChordPro(context: Context, title: String, chordPro: String) {
    shareTextFile(
        context = context,
        title = title,
        fileName = "${fileSafeName(title)}.chordpro",
        content = chordPro,
        mimeType = "text/plain"
    )
}

/**
 * Comparte un archivo de texto (ChordPro o repertorio `.canticora`) con la hoja nativa del sistema.
 *
 * El contenido viaja **dos veces**: como archivo adjunto (para que llegue como documento y pueda
 * abrirse directamente en Canticora) y como texto plano en el cuerpo del mensaje (cómodo para
 * canales donde los adjuntos se pierden). El archivo se escribe en el almacén interno de caché y
 * se expone con permiso de lectura temporal, sin pedir permisos de almacenamiento.
 */
private fun shareTextFile(
    context: Context,
    title: String,
    fileName: String,
    content: String,
    mimeType: String
) {
    val file = runCatching {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        File(dir, fileName).apply { writeText(content) }
    }.getOrNull()

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_TEXT, content)
    }

    if (file != null) {
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
        if (uri != null) {
            sendIntent.putExtra(Intent.EXTRA_STREAM, uri)
            sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    val chooser = Intent.createChooser(sendIntent, "Compartir").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(chooser)
}

/** Nombre de archivo sugerido para la copia de seguridad, con la fecha del día. */
private fun backupShareFileName(): String {
    val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        .format(java.util.Date())
    return "canticora_copia_$day.canticora"
}

/**
 * Crea un archivo vacío en la caché interna y devuelve su `Uri` de `FileProvider`, listo para
 * escribir en él y luego compartirlo con permiso temporal de lectura.
 */
private fun createExportShareUri(context: Context, fileName: String): String? = runCatching {
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, fileName).apply { if (exists()) delete() }
    file.createNewFile()
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).toString()
}.getOrNull()

/** Comparte un archivo propio (por ejemplo, la copia de seguridad) con la hoja nativa del sistema. */
private fun shareContentUri(
    context: Context,
    messages: AppMessageController,
    uriString: String,
    title: String,
    mimeType: String
) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, Uri.parse(uriString))
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TITLE, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(sendIntent, "Compartir").apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(chooser) }
        .onFailure {
            messages.show("No hay ninguna aplicación para compartir")
        }
}

/** Nombre de archivo seguro a partir de un título libre. */
private fun fileSafeName(raw: String): String = raw
    .lowercase()
    .replace(Regex("[^a-z0-9áéíóúñü ]"), "")
    .trim()
    .replace(Regex("\\s+"), "_")
    .ifBlank { "canto" }

/**
 * Archivo `.canticora` abierto desde el gestor de archivos, WhatsApp u otra app.
 *
 * El mismo contenedor externo sirve para dos cosas distintas: un **repertorio** (JSON) o una
 * **copia de seguridad** (ZIP). Aquí se distingue por el contenido y el tipo MIME para llevar al
 * usuario a la pantalla correcta.
 */
/**
 * Archivo abierto o compartido con Canticora, ya reconocido.
 *
 * @param detected Qué es (cancionero, repertorio, copia de seguridad, canto con adjuntos o canción ChordPro).
 * @param fileName Nombre visible del archivo, para poder citarlo en el diálogo.
 * @param archiveUri `Uri` del archivo cuando su lectura necesita permisos (copia de seguridad o
 *   paquete de canción, ambos ZIP): se entrega a la pantalla que vaya a leerlo.
 */
private data class IncomingLaudateFile(
    val detected: DetectedLaudateFile,
    val fileName: String? = null,
    val archiveUri: String? = null
)

/**
 * Si la actividad se abrió para **ver** o **recibir** un archivo, determina qué contiene.
 *
 * Se atienden dos vías:
 *  - `ACTION_VIEW`: el archivo llega en `intent.data` (tocarlo en el gestor de archivos, WhatsApp…).
 *  - `ACTION_SEND`: el archivo llega en `EXTRA_STREAM` (Compartir → Canticora).
 *
 * El tipo se decide por el **contenido**, no por la extensión ni el MIME: WhatsApp y otras apps
 * entregan los documentos con `application/octet-stream` y con un `content://` que ni siquiera
 * termina en `.canticora`, así que esas pistas no sirven.
 */
private fun readLaudateDeepLink(activity: android.app.Activity): IncomingLaudateFile? {
    val intent = activity.intent ?: return null
    val uri: Uri = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data ?: return null
        Intent.ACTION_SEND -> {
            @Suppress("DEPRECATION")
            (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri) ?: return null
        }
        else -> return null
    }

    val mime = intent.type
    val fileName = displayNameOf(activity, uri)

    // Un binario (ZIP, vídeo, imagen, PDF…) nunca se lee como texto: solo hace falta su cabecera.
    // Esto es lo que evita que, por ejemplo, un MP4 recibido se cuele como ChordPro.
    val head = readHeadBytes(activity, uri)
    val isZip = LaudateFileDetector.looksLikeZip(head)
    val mediaKind = if (isZip) null else LaudateFileDetector.sniffMediaKind(head)

    if (isZip) {
        return IncomingLaudateFile(
            // Se pasa la cabecera para que el detector distinga el paquete de un canto (song.json)
            // de la copia de seguridad (backup.json) leyendo la primera entrada del ZIP.
            detected = LaudateFileDetector.detect(
                text = null, mimeType = mime, fileName = fileName, isZip = true, headBytes = head
            ),
            fileName = fileName,
            archiveUri = uri.toString()
        )
    }
    if (mediaKind != null) {
        return IncomingLaudateFile(
            detected = LaudateFileDetector.detect(text = null, mimeType = mime, fileName = fileName, headBytes = head),
            fileName = fileName,
            archiveUri = null
        )
    }

    val content = readSharedText(activity, uri)
    return IncomingLaudateFile(
        detected = LaudateFileDetector.detect(
            text = content,
            mimeType = mime,
            fileName = fileName,
            isZip = false
        ),
        fileName = fileName,
        archiveUri = null
    )
}

/**
 * Primeros bytes del archivo (una muestra pequeña, no el archivo entero), para reconocer un ZIP o
 * un binario conocido (vídeo, imagen, PDF…) antes de intentar leerlo como texto.
 */
private fun readHeadBytes(context: Context, uri: Uri, limit: Int = 4096): ByteArray = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val head = ByteArray(limit)
        val read = stream.read(head)
        if (read > 0) head.copyOf(read) else ByteArray(0)
    } ?: ByteArray(0)
}.getOrDefault(ByteArray(0))

/** Nombre visible del archivo recibido, para poder citarlo en el diálogo. */
private fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
    }
}.getOrNull()

/**
 * Lee el contenido de un `Uri` recibido por un intent `VIEW` (un repertorio `.canticora` abierto
 * desde el gestor de archivos o compartido desde otra app).
 *
 * Devuelve `null` si el contenido no se puede leer; el llamante muestra el aviso correspondiente.
 */
internal fun readSharedText(context: Context, uri: android.net.Uri): String? =
    runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

/** Nombre visible y tipo MIME de un fichero elegido con el selector del sistema (SAF). */
private fun queryFileMetadata(context: Context, uri: Uri): Pair<String, String?> {
    var name = "adjunto"
    val mime = context.contentResolver.getType(uri)
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { name = it }
            }
        }
    }
    return name to mime
}

/**
 * Abre un enlace web en el navegador del sistema.
 *
 * Se usa para los enlaces institucionales del menú lateral (p. ej. «Apoyar»). Si el dispositivo
 * no tiene ninguna app capaz de resolver el `ACTION_VIEW`, se avisa por snackbar en lugar de
 * dejar caer la excepción.
 */
private fun openUrlExternally(context: Context, url: String, messages: AppMessageController) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { messages.show("No hay ninguna aplicación para abrir el enlace") }
}

/**
 * Abre un adjunto con la app externa correspondiente.
 *
 * Para los enlaces lanza el navegador; para los ficheros locales usa un `FileProvider`, de modo
 * que la app receptora recibe un permiso temporal de lectura sin exponer la ruta real.
 */
private fun openAttachmentExternally(
    context: Context,
    attachment: SongAttachment,
    messages: AppMessageController
) {
    val intent = runCatching {
        if (attachment.isLink) {
            Intent(Intent.ACTION_VIEW, Uri.parse(attachment.uri))
        } else {
            val file = File(attachment.uri)
            require(file.exists()) { "El archivo adjunto ya no está disponible" }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, attachment.mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }.getOrElse { error ->
        messages.show(error.message ?: "No se pudo abrir el adjunto")
        return
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Abrir con")) }
        .onFailure {
            messages.show("No hay ninguna aplicación para abrir este adjunto")
        }
}

/** Nombre visible y tipo MIME de un fichero elegido con el selector del sistema (SAF). */
