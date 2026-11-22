package com.grafitto.laudate.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.backup.BackupSerializer
import com.grafitto.laudate.domain.backup.canticoraBackupFileName
import com.grafitto.laudate.domain.backup.toDomainCategories
import com.grafitto.laudate.domain.backup.toDomainSongs
import com.grafitto.laudate.domain.export.CelebrationPdfLayout
import com.grafitto.laudate.domain.export.SongPdfLayout
import com.grafitto.laudate.domain.export.SongPdfOptions
import com.grafitto.laudate.domain.model.LaudateBrand
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.tablature.TabDisplayPosition
import com.grafitto.laudate.domain.platform.nowMillis
import com.grafitto.laudate.domain.text.formatIsoDate
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
import com.grafitto.laudate.ui.celebration.CelebrationEditorScreen
import com.grafitto.laudate.ui.celebration.CelebrationEditorSideEffect
import com.grafitto.laudate.ui.celebration.CelebrationEditorUiIntent
import com.grafitto.laudate.ui.celebration.CelebrationListScreen
import com.grafitto.laudate.ui.celebration.CelebrationListUiIntent
import com.grafitto.laudate.ui.celebration.CelebrationViewModel
import com.grafitto.laudate.ui.celebration.live.LiveCelebrationScreen
import com.grafitto.laudate.ui.category.CategoryManagementSheet
import com.grafitto.laudate.ui.common.AppLogo
import com.grafitto.laudate.ui.common.AppMessageController
import com.grafitto.laudate.ui.common.AppMessageHost
import com.grafitto.laudate.ui.common.LaudateLinks
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.ui.editor.SongEditorScreen
import com.grafitto.laudate.ui.editor.SongEditorTab
import com.grafitto.laudate.ui.editor.SongEditorToolHost
import com.grafitto.laudate.ui.editor.SongEditorToolPanel
import com.grafitto.laudate.ui.editor.SongEditorSideEffect
import com.grafitto.laudate.ui.editor.SongEditorUiIntent
import com.grafitto.laudate.ui.editor.SongEditorViewModel
import com.grafitto.laudate.ui.importer.ImportScreen
import com.grafitto.laudate.ui.importer.ImportSideEffect
import com.grafitto.laudate.ui.importer.ImportUiIntent
import com.grafitto.laudate.ui.importer.ImportViewModel
import com.grafitto.laudate.ui.manual.ManualScreen
import com.grafitto.laudate.ui.manual.ManualViewModel
import com.grafitto.laudate.ui.navigation.CanticoraScaffold
import com.grafitto.laudate.ui.navigation.CommonSettingsPanel
import com.grafitto.laudate.ui.navigation.NavBrandLogoSize
import com.grafitto.laudate.ui.navigation.NavEntry
import com.grafitto.laudate.ui.navigation.CatalogToolSettings
import com.grafitto.laudate.ui.navigation.CelebrationListToolSettings
import com.grafitto.laudate.ui.navigation.RhythmToolSettings
import com.grafitto.laudate.ui.navigation.SongReaderToolSettings
import com.grafitto.laudate.ui.navigation.TablatureToolSettings
import com.grafitto.laudate.ui.rhythm.RhythmPatternEditorScreen
import com.grafitto.laudate.ui.rhythm.RhythmPatternSideEffect
import com.grafitto.laudate.ui.rhythm.RhythmPatternUiIntent
import com.grafitto.laudate.ui.rhythm.RhythmPatternViewModel
import com.grafitto.laudate.ui.rhythm.RhythmPatternsScreen
import com.grafitto.laudate.ui.search.SongSearchViewModel
import com.grafitto.laudate.ui.tablature.TablatureEditorScreen
import com.grafitto.laudate.ui.tablature.TablatureScreen
import com.grafitto.laudate.ui.tablature.TablatureSectionRef
import com.grafitto.laudate.ui.tablature.TablatureSideEffect
import com.grafitto.laudate.ui.tablature.TablatureUiIntent
import com.grafitto.laudate.ui.tablature.TablatureViewModel
import com.grafitto.laudate.ui.song.SongDetailScreen
import com.grafitto.laudate.ui.song.SongDetailSideEffect
import com.grafitto.laudate.ui.song.SongDetailUiIntent
import com.grafitto.laudate.ui.song.SongDetailViewModel
import com.grafitto.laudate.ui.theme.LaudateTheme
import com.grafitto.laudate.ui.theme.SectionColors
import com.grafitto.laudate.web.WebCatalog
import com.grafitto.laudate.web.export.WebPdfWriter
import com.grafitto.laudate.web.io.WebBytes
import com.grafitto.laudate.web.platform.WebArchiveRegistry
import com.grafitto.laudate.web.platform.downloadUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Raíz de la aplicación web — ADR-023.
 *
 * Mismo patrón que Android y escritorio: un contenedor de dependencias propio del «cascarón», los
 * ViewModels compartidos de `:core:presentation` y las pantallas compartidas de `:core:ui`. La única
 * diferencia es el arranque: aquí hay que **descargar y parsear el cancionero** antes de pintar el
 * catálogo, así que hay un estado de carga explícito con su pantalla de espera.
 */
@Composable
fun WebApp(container: WebContainer) {
    var status by remember { mutableStateOf<WebCatalog.Status>(WebCatalog.Status.Loading) }

    LaunchedEffect(Unit) {
        status = WebCatalog.load(container)
    }

    when (val current = status) {
        is WebCatalog.Status.Loading -> WebSplash()
        is WebCatalog.Status.Failure -> WebLoadError(message = current.message)
        is WebCatalog.Status.Ready -> WebRoot(container)
    }
}

/** Pantalla de espera mientras llega el cancionero (sustituye al «splash» del HTML). */
@Composable
private fun WebSplash() {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AppLogo(contentDescription = "Logotipo de Canticora", modifier = Modifier.padding(8.dp))
            Text(
                text = LaudateBrand.FULL_NAME,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Cargando el cancionero…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }
    }
}

/** El cancionero no se pudo leer: se dice qué pasó en vez de dejar la pantalla en blanco. */
@Composable
private fun WebLoadError(message: String) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "No se pudo cargar el cancionero",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Recarga la página para volver a intentarlo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Pantallas de la web: las mismas que Android y escritorio, con el mismo id de navegación. */
private sealed interface WebScreen {
    data object Catalog : WebScreen
    data class SongDetail(val songId: String) : WebScreen
    data class SongEditor(val songId: String?) : WebScreen
    data object CelebrationList : WebScreen
    data class CelebrationEditor(val celebrationId: String?) : WebScreen
    data class LiveCelebration(val celebrationId: String) : WebScreen
    data object RhythmPatterns : WebScreen
    data object RhythmPatternEditor : WebScreen
    data class Tablatures(val songId: String, val songTitle: String) : WebScreen
    data object TablatureEditor : WebScreen
    data object Backup : WebScreen
    data object Manual : WebScreen
    data object Import : WebScreen
}

private const val ActionCategories = "action-categories"
private const val ActionSupport = "action-support"
private const val ActionAbout = "action-about"

private val webNavEntries = listOf(
    NavEntry("catalog", "Canciones", Icons.Default.MusicNote),
    NavEntry("celebrations", "Misas y Eventos", Icons.Default.PlaylistPlay),
    NavEntry("rhythms", "Patrones rítmicos", Icons.Default.Settings),
    NavEntry("import", "Importar", Icons.Default.Upload),
    NavEntry("backup", "Copia de seguridad", Icons.Default.CloudSync)
)

private val webNavActions = listOf(
    NavEntry(ActionCategories, "Gestión de categorías", Icons.Default.Settings),
    NavEntry("manual", "Manual de uso", Icons.Default.MenuBook),
    NavEntry(ActionSupport, "Apoyar", Icons.Default.VolunteerActivism),
    NavEntry(ActionAbout, "Acerca de Canticora", Icons.Default.Info)
)

private fun webScreenId(screen: WebScreen): String = when (screen) {
    is WebScreen.Catalog, is WebScreen.SongDetail, is WebScreen.SongEditor,
    is WebScreen.Tablatures, is WebScreen.TablatureEditor -> "catalog"
    is WebScreen.CelebrationList, is WebScreen.CelebrationEditor,
    is WebScreen.LiveCelebration -> "celebrations"
    is WebScreen.RhythmPatterns, is WebScreen.RhythmPatternEditor -> "rhythms"
    is WebScreen.Backup -> "backup"
    is WebScreen.Manual -> "manual"
    is WebScreen.Import -> "import"
}

/** Pantalla a la que lleva cada entrada del panel, conservando la que ya está abierta. */
private fun webScreenFor(id: String, current: WebScreen): WebScreen = when (id) {
    "catalog" -> when (current) {
        is WebScreen.SongDetail, is WebScreen.SongEditor -> current
        else -> WebScreen.Catalog
    }
    "celebrations" -> WebScreen.CelebrationList
    "rhythms" -> WebScreen.RhythmPatterns
    "backup" -> WebScreen.Backup
    "import" -> WebScreen.Import
    else -> WebScreen.Catalog
}

/** Fecha de hoy en `AAAA-MM-DD` para el nombre de la copia (el Dominio solo formatea). */
private fun todayIsoDate(): String = formatIsoDate(nowMillis())

@Composable
private fun WebRoot(container: WebContainer) {
    val messages = remember { AppMessageController() }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<WebScreen>(WebScreen.Catalog) }
    var showAbout by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    // Copia de seguridad recibida desde la pantalla de importación: se abre aquí.
    var pendingBackupUri by remember { mutableStateOf<String?>(null) }

    val notation by container.preferencesRepository.notationSystem
        .collectAsState(initial = NotationSystem.LATIN)
    val songDisplayMode by container.preferencesRepository.songDisplayMode
        .collectAsState(initial = SongDisplayMode.CHORDS_ABOVE_TEXT)
    val tabDisplayPosition by container.preferencesRepository.tabDisplayPosition
        .collectAsState(initial = TabDisplayPosition.AFTER)
    val preferredInstrument by container.preferencesRepository.preferredTablatureInstrument
        .collectAsState(initial = null)

    // ── ViewModels compartidos (los MISMOS que Android y escritorio) ──────────
    val catalogViewModel = remember(container) {
        SongCatalogViewModel(
            songRepository = container.songRepository,
            categoryRepository = container.categoryRepository,
            celebrationRepository = container.celebrationRepository,
            addSongToCelebrationUseCase = container.addSongToCelebrationUseCase,
            ensureCategoriesForImportUseCase = container.ensureCategoriesForImportUseCase,
            preferencesRepository = container.preferencesRepository,
            attachmentRepository = container.attachmentRepository,
            tablatureRepository = container.tablatureRepository
        )
    }
    val celebrationViewModel = remember(container) {
        CelebrationViewModel(
            container.celebrationRepository,
            container.songRepository,
            container.categoryRepository,
            container.importCelebrationBundleUseCase,
            container.appVersionName,
            preferencesRepository = container.preferencesRepository,
            tablatureRepository = container.tablatureRepository,
            attachmentRepository = container.attachmentRepository
        )
    }
    val searchViewModel = remember(container) {
        SongSearchViewModel(
            container.songRepository,
            container.categoryRepository,
            container.searchSongsUseCase
        )
    }
    val songDetailKey = (screen as? WebScreen.SongDetail)?.songId
    val songDetailViewModel = remember(container, songDetailKey) {
        SongDetailViewModel(
            songRepository = container.songRepository,
            getTransposedSongUseCase = container.getTransposedSongUseCase,
            parseChordProUseCase = container.parseChordProUseCase,
            rhythmPatternRepository = container.rhythmPatternRepository,
            attachmentRepository = container.attachmentRepository,
            addAttachmentToSongUseCase = container.addAttachmentToSongUseCase,
            deleteSongAttachmentUseCase = container.deleteSongAttachmentUseCase,
            attachmentStore = container.attachmentStore,
            tablatureRepository = container.tablatureRepository,
            preferencesRepository = container.preferencesRepository
        )
    }
    val songEditorKey = (screen as? WebScreen.SongEditor)?.songId
    val songEditorViewModel = remember(container, songEditorKey) {
        SongEditorViewModel(
            songRepository = container.songRepository,
            categoryRepository = container.categoryRepository,
            rhythmPatternRepository = container.rhythmPatternRepository,
            detectDuplicateSongUseCase = container.detectDuplicateSongUseCase,
            attachmentRepository = container.attachmentRepository,
            addAttachmentToSongUseCase = container.addAttachmentToSongUseCase,
            deleteSongAttachmentUseCase = container.deleteSongAttachmentUseCase,
            attachmentStore = container.attachmentStore,
            preferencesRepository = container.preferencesRepository
        )
    }
    // Tablaturas del canto (ADR-017): el editor las lista y edita en su propia pestaña, igual que
    // Android y escritorio. Se guardan en `localStorage` (ver `WebTablatureRepository`).
    val tablatureViewModel = remember(container) {
        TablatureViewModel(
            tablatureRepository = container.tablatureRepository,
            preferencesRepository = container.preferencesRepository,
            importTablatureUseCase = container.importTablatureUseCase
        )
    }
    // Ritmos (patrones propios y predefinidos) y copia de seguridad: los mismos ViewModels.
    val rhythmViewModel = remember(container) {
        RhythmPatternViewModel(
            rhythmPatternRepository = container.rhythmPatternRepository,
            rhythmPlayer = container.rhythmPlayer,
            exportRhythmPatternsUseCase = container.exportRhythmPatternsUseCase,
            importRhythmPatternsUseCase = container.importRhythmPatternsUseCase
        )
    }
    val backupViewModel = remember(container) {
        BackupViewModel(
            container.songRepository,
            container.attachmentRepository,
            container.exportBackupUseCase,
            container.importBackupUseCase
        )
    }
    val manualViewModel = remember { ManualViewModel() }
    val importViewModel = remember(container) {
        ImportViewModel(
            songRepository = container.songRepository,
            celebrationRepository = container.celebrationRepository,
            categoryRepository = container.categoryRepository,
            importCatalogBundleUseCase = container.importCatalogBundleUseCase,
            importCelebrationBundleUseCase = container.importCelebrationBundleUseCase,
            importChordProSongsUseCase = container.importChordProSongsUseCase,
            importRhythmPatternsUseCase = container.importRhythmPatternsUseCase
        )
    }

    // Panel de herramientas del editor de canto (pestañas y herramientas tipo «Editor»): el editor
    // registra en el host cómo insertar texto en el cursor, así que el host vive por encima de la
    // pantalla y se pasa tanto al editor como al panel del `CanticoraScaffold`.
    val songEditorToolHost = remember { SongEditorToolHost() }
    val songEditorStateForPanel by songEditorViewModel.uiState.collectAsState()
    val tablatureStateForPanel by tablatureViewModel.uiState.collectAsState()
    // El panel de lectura solo enseña los mandos de tablatura que **esa canción** necesita.
    val songDetailStateForPanel by songDetailViewModel.uiState.collectAsState()

    // Mientras se **escribe** una tablatura, los ajustes de lectura no tienen efecto: se ocultan.
    val showReadingSettings = !(
        screen is WebScreen.Tablatures ||
            screen is WebScreen.TablatureEditor ||
            (screen is WebScreen.SongEditor &&
                songEditorStateForPanel.editorTab == SongEditorTab.TABLATURES &&
                tablatureStateForPanel.isEditorOpen)
        )

    /**
     * Genera el **PDF real** del canto y lo pone a descargar.
     *
     * La maquetación la decide el Dominio ([SongPdfLayout]); aquí solo se convierte en los bytes
     * del fichero ([WebPdfWriter]) y se entrega al navegador.
     */
    suspend fun exportSongPdf(songId: String, mode: SongDisplayMode) {
        val song = container.songRepository.getSongById(songId).first()
        if (song == null) {
            messages.show("El canto ya no existe.")
            return
        }
        val tablatures = container.tablatureRepository.getTablaturesForSong(songId)
        val document = withContext(Dispatchers.Default) {
            SongPdfLayout.layout(
                song = song,
                options = SongPdfOptions(
                    notationSystem = notation,
                    displayMode = mode
                ),
                tablatures = tablatures
            )
        }
        val bytes = withContext(Dispatchers.Default) { WebPdfWriter.write(document) }
        val fileName = safeFileName(song.title.ifBlank { "canto" }) + ".pdf"
        WebPlatform.downloadBytes(fileName, "application/pdf", bytes)
        messages.show("PDF descargado: $fileName")
    }

    /**
     * Adjuntar un **fichero** desde el navegador: el selector deja los bytes en IndexedDB y aquí solo
     * se registra el adjunto con su `idb://…`. [songId] se resuelve al pulsar, porque el canto puede
     * acabar de guardarse y estrenar id.
     */
    fun pickAttachmentForEditor() {
        val songId = songEditorStateForPanel.songId
        if (songId == null) {
            messages.show("Guarda el canto antes de añadirle adjuntos.")
            return
        }
        WebPlatform.pickAttachmentFile(accept = "*/*", songId = songId) { uri, name, mime, _ ->
            if (uri.isEmpty()) {
                messages.show("No se pudo leer «$name».")
                return@pickAttachmentFile
            }
            scope.launch {
                songEditorViewModel.processIntent(
                    SongEditorUiIntent.AddAttachmentFromUri(uri, name, mime.takeIf { it.isNotBlank() })
                )
            }
        }
    }

    /** Descarga un adjunto de fichero (los enlaces se abren en otra pestaña). */
    fun exportAttachment(attachment: SongAttachment) {
        if (attachment.isLink) {
            WebPlatform.openUrl(attachment.uri)
            return
        }
        scope.launch {
            container.attachmentStore.exportFile(attachment, destinationUri = "")
                .onFailure { messages.show("No se pudo descargar «${attachment.displayName}».") }
        }
    }

    /**
     * Empaqueta el canto con sus adjuntos (`canticora.song`) y lo entrega al usuario.
     *
     * En móvil se usa la **hoja de compartir** del sistema (Web Share API, nivel 2); si el
     * navegador no sabe compartir ficheros o el usuario cancela, se descarga el `.canticora.song`.
     */
    suspend fun shareSongPackage(songId: String, title: String, suggestedFileName: String) {
        val uri = "mem://package-${nowMillis()}"
        val fileName = suggestedFileName.ifBlank { safeFileName(title) + ".canticora.song" }
        runCatching { container.exportSongPackageUseCase(uri, songId) }
            .onFailure {
                messages.show("No se pudo preparar el canto: ${it.message ?: "error"}")
                return
            }
        val bytes = WebArchiveRegistry.find(uri)?.bytes
        if (bytes == null) {
            messages.show("No se pudo preparar el paquete del canto.")
            return
        }
        val shared = WebPlatform.shareFile(fileName, "application/zip", bytes, title)
        if (!shared) {
            WebPlatform.downloadBytes(fileName, "application/zip", bytes)
            messages.show("Canto guardado: $fileName")
        }
    }

    // ── Efectos de la barra de mensajes ─────────────────────────────────────
    LaunchedEffect(catalogViewModel) {
        catalogViewModel.sideEffect.collect { effect ->
            when (effect) {
                is SongCatalogSideEffect.NavigateToSongDetail ->
                    screen = WebScreen.SongDetail(effect.songId)
                is SongCatalogSideEffect.NavigateToCreateSong ->
                    screen = WebScreen.SongEditor(null)
                is SongCatalogSideEffect.ShowMessage -> messages.show(effect.message)
                is SongCatalogSideEffect.ShareSong -> {
                    val copied = WebPlatform.copyToClipboard(effect.chordPro)
                    messages.show(
                        if (copied) "«${effect.title}» copiado al portapapeles"
                        else "No se pudo copiar al portapapeles"
                    )
                }
                is SongCatalogSideEffect.ShareSongPackage -> scope.launch {
                    shareSongPackage(
                        songId = effect.songId,
                        title = effect.title,
                        suggestedFileName = effect.suggestedFileName
                    )
                }
                is SongCatalogSideEffect.ExportSongPdf -> scope.launch {
                    exportSongPdf(effect.song.id, effect.mode)
                }
            }
        }
    }

    LaunchedEffect(songDetailViewModel) {
        songDetailViewModel.sideEffect.collect { effect ->
            when (effect) {
                is SongDetailSideEffect.ShowMessage -> messages.show(effect.message)
                is SongDetailSideEffect.NavigateBack -> screen = WebScreen.Catalog
            }
        }
    }

    LaunchedEffect(songEditorViewModel) {
        songEditorViewModel.sideEffect.collect { effect ->
            when (effect) {
                SongEditorSideEffect.NavigateBackOnSaved -> screen = WebScreen.Catalog
                SongEditorSideEffect.NavigateBack -> screen = WebScreen.Catalog
                SongEditorSideEffect.NavigateBackOnDeleted -> screen = WebScreen.Catalog
                is SongEditorSideEffect.ShowMessage -> messages.show(effect.message)
                is SongEditorSideEffect.ShareSong -> {
                    val copied = WebPlatform.copyToClipboard(effect.chordPro)
                    messages.show(
                        if (copied) "«${effect.title}» copiado al portapapeles"
                        else "No se pudo copiar al portapapeles"
                    )
                }
                is SongEditorSideEffect.ShareSongPackage -> scope.launch {
                    shareSongPackage(
                        songId = effect.songId,
                        title = effect.title,
                        suggestedFileName = effect.suggestedFileName
                    )
                }
                is SongEditorSideEffect.ExportSongPdf -> scope.launch {
                    exportSongPdf(effect.song.id, effect.mode)
                }
                is SongEditorSideEffect.OpenAttachment -> exportAttachment(effect.attachment)
            }
        }
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
                    if (result == SnackbarResult.ActionPerformed) tablatureViewModel.undoDelete()
                }
                is TablatureSideEffect.ShareContent ->
                    WebPlatform.downloadText(effect.fileName, effect.content)
                TablatureSideEffect.RequestTablatureFile -> WebPlatform.pickBinaryFile(accept = "*/*") { fileName, base64 ->
                    val uri = WebArchiveRegistry.register(WebBytes.base64Decode(base64), fileName)
                    tablatureViewModel.processIntent(
                        TablatureUiIntent.ImportTablatureFromFile(uri, fileName)
                    )
                }
            }
        }
    }

    LaunchedEffect(importViewModel) {
        importViewModel.sideEffect.collect { effect ->
            when (effect) {
                is ImportSideEffect.ShowMessage -> messages.show(effect.message)
                // El catálogo cambia con lo importado: se recarga la lista de canciones.
                ImportSideEffect.CatalogChanged ->
                    catalogViewModel.processIntent(SongCatalogUiIntent.LoadCatalog)
                // Las copias de seguridad son ZIP: se eligen con el selector del navegador y se
                // registran en memoria para que el caso de uso pueda abrirlas por su `mem://`.
                ImportSideEffect.RequestBackupFile -> WebPlatform.pickBinaryFile(accept = "*/*") { fileName, base64 ->
                    val bytes = WebBytes.base64Decode(base64)
                    val uri = WebArchiveRegistry.register(bytes, fileName)
                    val archive = WebArchiveRegistry.find(uri)
                    val detected = LaudateFileDetector.detect(
                        text = archive?.text,
                        fileName = fileName,
                        isZip = archive?.isArchive == true,
                        headBytes = bytes.copyOfRange(0, minOf(bytes.size, 4096))
                    )
                    importViewModel.processIntent(
                        ImportUiIntent.OfferPendingFile(
                            detected = detected,
                            fileName = fileName,
                            archiveUri = if (detected.isZip) uri else null
                        )
                    )
                }
                // Copia recibida: se abre la pantalla de Copia con su archivo ya registrado.
                is ImportSideEffect.RestoreBackup -> {
                    pendingBackupUri = effect.uri
                    screen = WebScreen.Backup
                }
            }
        }
    }

    LaunchedEffect(rhythmViewModel) {
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
                    WebPlatform.downloadText(effect.fileName, effect.content)
                    messages.show("Patrones descargados: ${effect.fileName}")
                }
                is RhythmPatternSideEffect.CopyToClipboard -> {
                    val copied = WebPlatform.copyToClipboard(effect.text)
                    messages.show(
                        if (copied) "Patrones copiados al portapapeles"
                        else "No se pudo copiar al portapapeles"
                    )
                }
            }
        }
    }

    LaunchedEffect(backupViewModel) {
        backupViewModel.sideEffect.collect { effect ->
            when (effect) {
                is BackupSideEffect.ShowMessage -> messages.show(effect.message)
                // En la web no hay selector de destino: el archivo se descarga con su nombre.
                is BackupSideEffect.RequestExportDestination ->
                    backupViewModel.processIntent(
                        BackupUiIntent.ExportTo(downloadUri(effect.suggestedFileName))
                    )
                BackupSideEffect.RequestSyncFile -> WebPlatform.pickBinaryFile(accept = "*/*") { fileName, base64 ->
                    val uri = WebArchiveRegistry.register(WebBytes.base64Decode(base64), fileName)
                    backupViewModel.processIntent(BackupUiIntent.SyncFrom(uri))
                }
                BackupSideEffect.RequestRestoreFile -> WebPlatform.pickBinaryFile(accept = "*/*") { fileName, base64 ->
                    val uri = WebArchiveRegistry.register(WebBytes.base64Decode(base64), fileName)
                    backupViewModel.processIntent(BackupUiIntent.RequestRestore(uri))
                }
                // Compartir la copia con la hoja del sistema; si el navegador no puede, se descarga.
                is BackupSideEffect.ShareArchive -> scope.launch {
                    val bytes = WebArchiveRegistry.find(effect.archiveUri)?.bytes
                    if (bytes == null) {
                        messages.show("No se pudo preparar la copia para compartir.")
                    } else if (!WebPlatform.shareFile(
                            effect.fileName,
                            "application/zip",
                            bytes,
                            "Copia de seguridad de Canticora"
                        )
                    ) {
                        WebPlatform.downloadBytes(effect.fileName, "application/zip", bytes)
                        messages.show("Copia descargada: ${effect.fileName}")
                    }
                }
            }
        }
    }

    // Copia recibida desde «Importar»: la pantalla de Copia la analiza y pide confirmación.
    LaunchedEffect(pendingBackupUri, screen) {
        val uri = pendingBackupUri ?: return@LaunchedEffect
        if (screen !is WebScreen.Backup) return@LaunchedEffect
        pendingBackupUri = null
        backupViewModel.processIntent(BackupUiIntent.RequestRestore(uri))
    }

    LaunchedEffect(celebrationViewModel) {
        celebrationViewModel.sideEffect.collect { effect ->
            when (effect) {
                CelebrationEditorSideEffect.NavigateBackOnSaved -> screen = WebScreen.CelebrationList
                CelebrationEditorSideEffect.NavigateBackOnDeleted -> screen = WebScreen.CelebrationList
                is CelebrationEditorSideEffect.ShowMessage -> messages.show(effect.message)
                is CelebrationEditorSideEffect.ShareCelebrationFile -> {
                    WebPlatform.downloadText(effect.fileName, effect.content)
                    messages.show("Repertorio descargado: ${effect.fileName}")
                }
                is CelebrationEditorSideEffect.ExportCelebrationPdf -> scope.launch {
                    val document = withContext(Dispatchers.Default) {
                        CelebrationPdfLayout.layout(
                            celebration = effect.celebration,
                            songs = effect.songs,
                            options = SongPdfOptions(
                                notationSystem = notation,
                                displayMode = effect.mode
                            )
                        )
                    }
                    val bytes = withContext(Dispatchers.Default) { WebPdfWriter.write(document) }
                    val fileName = effect.fileName.ifBlank {
                        safeFileName(effect.celebration.name) + ".pdf"
                    }
                    WebPlatform.downloadBytes(fileName, "application/pdf", bytes)
                    messages.show("PDF descargado: $fileName")
                }
            }
        }
    }

    // «Deshacer» al añadir un canto a un repertorio: mismo contrato que Android y escritorio.
    LaunchedEffect(catalogViewModel) {
        catalogViewModel.uiState
            .map { it.pendingAction }
            .distinctUntilChanged()
            .collect { pending ->
                val action = pending ?: return@collect
                val result = messages.snackbarHostState.showSnackbar(
                    message = "«${action.song.title}» añadido a ${action.celebrationName}",
                    actionLabel = "Deshacer",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    catalogViewModel.processIntent(SongCatalogUiIntent.UndoCelebrationAdd)
                } else {
                    catalogViewModel.processIntent(SongCatalogUiIntent.DismissPendingAction)
                }
            }
    }

    // «Deshacer» al eliminar un repertorio.
    LaunchedEffect(celebrationViewModel) {
        celebrationViewModel.listUiState
            .map { it.pendingDelete }
            .distinctUntilChanged()
            .collect { pending ->
                val celebration = pending ?: return@collect
                val result = messages.snackbarHostState.showSnackbar(
                    message = "«${celebration.name}» eliminado",
                    actionLabel = "Deshacer",
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    celebrationViewModel.processListIntent(
                        CelebrationListUiIntent.UndoDeleteCelebration
                    )
                } else {
                    celebrationViewModel.processListIntent(
                        CelebrationListUiIntent.DismissPendingDelete
                    )
                }
            }
    }

    /**
     * Un fichero elegido en el navegador se **olfatea por sus bytes** igual que en escritorio: un
     * ZIP (copia o paquete de canto) viaja por su `mem://` y el resto por su texto.
     */
    fun offerImportFile(fileName: String, base64: String) {
        val bytes = WebBytes.base64Decode(base64)
        if (bytes.isEmpty()) {
            messages.show("«$fileName» no se pudo leer.")
            return
        }
        val head = bytes.copyOfRange(0, minOf(bytes.size, 4096))
        val isZip = LaudateFileDetector.looksLikeZip(head)
        val media = LaudateFileDetector.sniffMediaKind(head)
        val text = if (isZip || media != null) null else bytes.decodeToString()
        val detected = LaudateFileDetector.detect(
            text = text,
            fileName = fileName,
            isZip = isZip,
            headBytes = head
        )
        if (detected.isZip) {
            val uri = WebArchiveRegistry.register(bytes, fileName)
            importViewModel.processIntent(
                ImportUiIntent.OfferPendingFile(detected, fileName, archiveUri = uri)
            )
        } else if (text != null) {
            importViewModel.processIntent(ImportUiIntent.OfferContent(detected, text))
        } else {
            importViewModel.processIntent(
                ImportUiIntent.OfferPendingFile(detected, fileName, archiveUri = null)
            )
        }
    }

    LaudateTheme {
        AppMessageHost(controller = messages, modifier = Modifier.fillMaxSize()) {
            CanticoraScaffold(
                navEntries = webNavEntries,
                currentNavId = webScreenId(screen),
                onSelectNav = { id -> screen = webScreenFor(id, screen) },
                screenKey = screen,
                brandTitle = LaudateBrand.NAME,
                brandTagline = LaudateBrand.TAGLINE,
                brandLogo = {
                    AppLogo(
                        contentDescription = "Logotipo de Canticora",
                        modifier = Modifier.padding(4.dp)
                    )
                },
                // Panel de herramientas del editor: en escritorio se acopla a la derecha y en móvil
                // se abre como hoja inferior. Solo existe mientras se edita un canto.
                toolSettings = webToolSettings(
                    screen = screen,
                    catalogViewModel = catalogViewModel,
                    celebrationViewModel = celebrationViewModel,
                    rhythmViewModel = rhythmViewModel,
                    tablatureViewModel = tablatureViewModel,
                    songEditorViewModel = songEditorViewModel,
                    songDetailViewModel = songDetailViewModel,
                    songEditorToolHost = songEditorToolHost,
                    onAddAttachmentFileClick = ::pickAttachmentForEditor,
                    onOpenAttachmentClick = ::exportAttachment
                ),
                navActions = webNavActions,
                onNavAction = { id ->
                    when (id) {
                        ActionCategories -> showCategorySheet = true
                        "manual" -> screen = WebScreen.Manual
                        ActionSupport -> WebPlatform.openUrl(LaudateLinks.SUPPORT)
                        ActionAbout -> showAbout = true
                    }
                },
                commonSettings = {
                    CommonSettingsPanel(
                        notationSystem = notation,
                        onNotationChange = {
                            scope.launch { container.preferencesRepository.setNotationSystem(it) }
                        },
                        preferredInstrument = preferredInstrument,
                        // Solo los instrumentos que ESTA canción tiene en sus tablaturas.
                        instrumentOptions = when (val s = screen) {
                            is WebScreen.SongDetail -> songDetailStateForPanel.tablatureInstruments
                            is WebScreen.SongEditor ->
                                tablatureStateForPanel.tablatures.map { it.tuning.name }.distinct()
                            else -> emptyList()
                        },
                        onInstrumentChange = {
                            scope.launch {
                                container.preferencesRepository.setPreferredTablatureInstrument(it)
                            }
                        },
                        displayMode = songDisplayMode,
                        onDisplayModeChange = {
                            scope.launch { container.preferencesRepository.setSongDisplayMode(it) }
                        },
                        tabDisplayPosition = tabDisplayPosition,
                        onTabDisplayPositionChange = {
                            scope.launch { container.preferencesRepository.setTabDisplayPosition(it) }
                        },
                        // Se mira si el canto TIENE tablaturas, no si ahora se ven.
                        hasTablature = when (val s = screen) {
                            is WebScreen.SongDetail -> songDetailStateForPanel.tablatures.isNotEmpty()
                            is WebScreen.SongEditor -> tablatureStateForPanel.tablatures.isNotEmpty()
                            else -> false
                        },
                        showReadingSettings = showReadingSettings
                    )
                },
                modifier = Modifier.fillMaxSize()
            ) { _ ->
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    when (val current = screen) {
                        is WebScreen.Catalog -> {
                            val state by catalogViewModel.uiState.collectAsState()
                            SongCatalogScreen(
                                uiState = state,
                                onIntent = catalogViewModel::processIntent,
                                onSongClick = { screen = WebScreen.SongDetail(it) },
                                onCreateSongClick = { screen = WebScreen.SongEditor(null) },
                                notationSystem = notation
                            )
                        }

                        is WebScreen.SongDetail -> {
                            LaunchedEffect(current.songId) {
                                songDetailViewModel.processIntent(
                                    SongDetailUiIntent.LoadSong(current.songId)
                                )
                            }
                            val state by songDetailViewModel.uiState.collectAsState()
                            SongDetailScreen(
                                uiState = state,
                                onIntent = songDetailViewModel::processIntent,
                                onNavigateBack = { screen = WebScreen.Catalog },
                                onEditClick = { screen = WebScreen.SongEditor(current.songId) },
                                onToggleFocus = {
                                    songDetailViewModel.processIntent(SongDetailUiIntent.ToggleFocusMode)
                                },
                                // Los ficheros elegidos en el navegador se guardan en IndexedDB.
                                onAddAttachmentFileClick = {
                                    val songId = current.songId
                                    WebPlatform.pickAttachmentFile("*/*", songId) { uri, name, mime, _ ->
                                        if (uri.isEmpty()) return@pickAttachmentFile
                                        scope.launch {
                                            songDetailViewModel.processIntent(
                                                SongDetailUiIntent.AddAttachmentFromUri(
                                                    uri,
                                                    name,
                                                    mime.takeIf { it.isNotBlank() }
                                                )
                                            )
                                        }
                                    }
                                },
                                onTablaturesClick = {
                                    screen = WebScreen.Tablatures(
                                        current.songId,
                                        state.song?.title.orEmpty()
                                    )
                                },
                                // Un adjunto de fichero vive en IndexedDB: se descarga; un enlace se abre.
                                onOpenAttachmentExternally = ::exportAttachment
                            )
                        }

                        is WebScreen.SongEditor -> {
                            LaunchedEffect(current.songId) {
                                songEditorViewModel.processIntent(
                                    SongEditorUiIntent.LoadSongForEditing(current.songId)
                                )
                            }
                            val state by songEditorViewModel.uiState.collectAsState()
                            val tablatureState by tablatureViewModel.uiState.collectAsState()
                            // El editor de tablaturas necesita el canto **ya guardado**; cuando el
                            // canto existe se carga aquí (antes la web no lo hacía y el editor
                            // pedía guardar el canto incluso en uno que ya existía).
                            LaunchedEffect(state.songId) {
                                val songId = state.songId
                                if (songId == null) {
                                    tablatureViewModel.processIntent(TablatureUiIntent.ClearSong)
                                } else {
                                    tablatureViewModel.processIntent(
                                        TablatureUiIntent.LoadForSong(
                                            songId,
                                            state.title,
                                            state.originalKey
                                        )
                                    )
                                    val song = container.songRepository.getSongById(songId).first()
                                    tablatureViewModel.setAvailableSections(
                                        song?.sections?.map { section ->
                                            TablatureSectionRef(
                                                section.id,
                                                section.headerTitle?.takeIf { it.isNotBlank() }
                                                    ?: SectionColors.labelFor(section.sectionType.name)
                                            )
                                        }.orEmpty()
                                    )
                                }
                            }
                            SongEditorScreen(
                                uiState = state,
                                onIntent = songEditorViewModel::processIntent,
                                onAddAttachmentFileClick = ::pickAttachmentForEditor,
                                onOpenAttachmentClick = { WebPlatform.openUrl(it.uri) },
                                onExportAttachmentClick = ::exportAttachment,
                                toolHost = songEditorToolHost,
                                tablatureUiState = tablatureState,
                                onTablatureIntent = tablatureViewModel::processIntent,
                                previewDisplayMode = songDisplayMode,
                                previewTabPosition = tabDisplayPosition,
                                onPreviewDisplayModeChange = {
                                    scope.launch { container.preferencesRepository.setSongDisplayMode(it) }
                                },
                                onPreviewTabPositionChange = {
                                    scope.launch { container.preferencesRepository.setTabDisplayPosition(it) }
                                }
                            )
                        }

                        is WebScreen.CelebrationList -> {
                            val state by celebrationViewModel.listUiState.collectAsState()
                            CelebrationListScreen(
                                uiState = state,
                                onIntent = celebrationViewModel::processListIntent,
                                onCelebrationClick = { screen = WebScreen.CelebrationEditor(it) },
                                onLiveModeClick = { screen = WebScreen.LiveCelebration(it) },
                                onCreateCelebrationClick = {
                                    celebrationViewModel.processEditorIntent(
                                        CelebrationEditorUiIntent.LoadForEdit(null)
                                    )
                                    screen = WebScreen.CelebrationEditor(null)
                                }
                            )
                        }

                        is WebScreen.CelebrationEditor -> {
                            LaunchedEffect(current.celebrationId) {
                                if (current.celebrationId != null) {
                                    celebrationViewModel.processEditorIntent(
                                        CelebrationEditorUiIntent.LoadForEdit(current.celebrationId)
                                    )
                                }
                            }
                            val state by celebrationViewModel.editorUiState.collectAsState()
                            val searchState by searchViewModel.uiState.collectAsState()
                            CelebrationEditorScreen(
                                uiState = state,
                                onIntent = celebrationViewModel::processEditorIntent,
                                onNavigateBack = { screen = WebScreen.CelebrationList },
                                searchState = searchState,
                                onSearchIntent = searchViewModel::processIntent,
                                notationSystem = notation
                            )
                        }

                        is WebScreen.LiveCelebration -> {
                            LiveCelebrationScreen(
                                celebrationId = current.celebrationId,
                                celebrationRepository = container.celebrationRepository,
                                songRepository = container.songRepository,
                                getTransposedSongUseCase = container.getTransposedSongUseCase,
                                parseChordProUseCase = container.parseChordProUseCase,
                                onCloseLiveMode = { screen = WebScreen.CelebrationList },
                                preferencesRepository = container.preferencesRepository,
                                tablatureRepository = container.tablatureRepository
                            )
                        }

                        is WebScreen.RhythmPatterns -> {
                            val state by rhythmViewModel.uiState.collectAsState()
                            LaunchedEffect(state.isEditorOpen) {
                                if (state.isEditorOpen) screen = WebScreen.RhythmPatternEditor
                            }
                            RhythmPatternsScreen(
                                uiState = state,
                                onIntent = rhythmViewModel::processIntent,
                                onNavigateBack = { screen = WebScreen.Catalog }
                            )
                        }

                        is WebScreen.RhythmPatternEditor -> {
                            val state by rhythmViewModel.uiState.collectAsState()
                            LaunchedEffect(state.isEditorOpen) {
                                if (!state.isEditorOpen) screen = WebScreen.RhythmPatterns
                            }
                            DisposableEffect(Unit) {
                                onDispose {
                                    rhythmViewModel.processIntent(RhythmPatternUiIntent.StopPreview)
                                }
                            }
                            RhythmPatternEditorScreen(
                                uiState = state,
                                onIntent = rhythmViewModel::processIntent,
                                onNavigateBack = {
                                    rhythmViewModel.processIntent(
                                        RhythmPatternUiIntent.CloseEditor
                                    )
                                }
                            )
                        }

                        is WebScreen.Tablatures -> {
                            val state by tablatureViewModel.uiState.collectAsState()
                            LaunchedEffect(state.isEditorOpen) {
                                if (state.isEditorOpen) screen = WebScreen.TablatureEditor
                            }
                            LaunchedEffect(current.songId) {
                                val song = container.songRepository
                                    .getSongById(current.songId)
                                    .first()
                                tablatureViewModel.processIntent(
                                    TablatureUiIntent.LoadForSong(
                                        current.songId,
                                        current.songTitle,
                                        song?.originalKey.orEmpty()
                                    )
                                )
                                tablatureViewModel.setAvailableSections(
                                    song?.sections?.map { section ->
                                        TablatureSectionRef(
                                            section.id,
                                            section.headerTitle?.takeIf { it.isNotBlank() }
                                                ?: SectionColors.labelFor(section.sectionType.name)
                                        )
                                    }.orEmpty()
                                )
                            }
                            TablatureScreen(
                                uiState = state,
                                onIntent = tablatureViewModel::processIntent,
                                onNavigateBack = {
                                    screen = WebScreen.SongDetail(current.songId)
                                }
                            )
                        }

                        is WebScreen.TablatureEditor -> {
                            val state by tablatureViewModel.uiState.collectAsState()
                            LaunchedEffect(state.isEditorOpen) {
                                if (!state.isEditorOpen) screen = WebScreen.Catalog
                            }
                            TablatureEditorScreen(
                                uiState = state,
                                onIntent = tablatureViewModel::processIntent,
                                onNavigateBack = {
                                    tablatureViewModel.processIntent(TablatureUiIntent.CloseEditor)
                                }
                            )
                        }

                        is WebScreen.Backup -> {
                            val state by backupViewModel.uiState.collectAsState()
                            BackupScreen(
                                uiState = state,
                                onNavigateBack = { screen = WebScreen.Catalog },
                                onExportClick = {
                                    backupViewModel.processIntent(
                                        BackupUiIntent.ExportTo(
                                            downloadUri(canticoraBackupFileName(todayIsoDate()))
                                        )
                                    )
                                },
                                onSyncClick = {
                                    WebPlatform.pickBinaryFile(accept = "*/*") { _, base64 ->
                                        val uri = WebArchiveRegistry.register(
                                            WebBytes.base64Decode(base64),
                                            "sync.canticora"
                                        )
                                        backupViewModel.processIntent(
                                            BackupUiIntent.SyncFrom(uri)
                                        )
                                    }
                                },
                                onRestoreClick = {
                                    WebPlatform.pickBinaryFile(accept = "*/*") { _, base64 ->
                                        val uri = WebArchiveRegistry.register(
                                            WebBytes.base64Decode(base64),
                                            "restore.canticora"
                                        )
                                        backupViewModel.processIntent(
                                            BackupUiIntent.RequestRestore(uri)
                                        )
                                    }
                                },
                                onShareClick = {
                                    backupViewModel.processIntent(
                                        BackupUiIntent.ExportTo(
                                            downloadUri(canticoraBackupFileName(todayIsoDate())),
                                            shareAfter = true
                                        )
                                    )
                                },
                                onResolveConflict = { songId, policy ->
                                    backupViewModel.processIntent(
                                        BackupUiIntent.ResolveConflict(songId, policy)
                                    )
                                },
                                onApplyPolicyToAll = {
                                    backupViewModel.processIntent(
                                        BackupUiIntent.ApplyPolicyToAll(it)
                                    )
                                },
                                onConfirmSync = {
                                    backupViewModel.processIntent(BackupUiIntent.ConfirmSync)
                                },
                                onCancelSync = {
                                    backupViewModel.processIntent(BackupUiIntent.CancelSync)
                                },
                                onCancelRestore = {
                                    backupViewModel.processIntent(BackupUiIntent.CancelRestore)
                                },
                                onConfirmRestore = {
                                    backupViewModel.processIntent(BackupUiIntent.ConfirmRestore)
                                },
                                onRestoreSafetyBackup = {
                                    backupViewModel.processIntent(
                                        BackupUiIntent.RestoreSafetyBackup
                                    )
                                }
                            )
                        }

                        is WebScreen.Import -> {
                            val state by importViewModel.uiState.collectAsState()
                            val stats by importViewModel.stats.collectAsState()
                            var importContentText by remember { mutableStateOf("") }
                            ImportScreen(
                                uiState = state,
                                stats = stats,
                                onNavigateBack = { screen = WebScreen.Catalog },
                                contentText = importContentText,
                                onContentChange = { importContentText = it },
                                onDetectContent = {
                                    if (importContentText.isBlank()) {
                                        messages.show("Pega el contenido o elige un archivo")
                                    } else {
                                        val detected = LaudateFileDetector.detect(
                                            text = importContentText,
                                            fileName = null
                                        )
                                        importViewModel.processIntent(
                                            ImportUiIntent.OfferContent(detected, importContentText)
                                        )
                                    }
                                },
                                onConfirmContent = {
                                    importViewModel.processIntent(ImportUiIntent.ConfirmPendingContent)
                                    importContentText = ""
                                },
                                onDismissContent = {
                                    importViewModel.processIntent(ImportUiIntent.DismissPendingContent)
                                },
                                onChooseContentFile = {
                                    WebPlatform.pickBinaryFile(
                                        accept = ".chordpro,.cho,.pro,.crd,.json,.txt,.zip," +
                                            ".canticora,.laudate,application/zip,text/plain"
                                    ) { fileName, base64 -> offerImportFile(fileName, base64) }
                                },
                                // Una copia de seguridad es un ZIP: se abre con el selector binario.
                                onConfirmIncomingFile = {
                                    importViewModel.processIntent(ImportUiIntent.ConfirmPendingFile)
                                },
                                onDismissIncomingFile = {
                                    importViewModel.processIntent(ImportUiIntent.DismissPendingFile)
                                },
                                onResolveImportConflict = { key, policy ->
                                    importViewModel.processIntent(
                                        ImportUiIntent.ResolveImportConflict(key, policy)
                                    )
                                },
                                onApplyImportPolicyToAll = { policy ->
                                    importViewModel.processIntent(
                                        ImportUiIntent.ApplyImportPolicyToAll(policy)
                                    )
                                },
                                onConfirmImportConflicts = {
                                    importViewModel.processIntent(
                                        ImportUiIntent.ConfirmImportConflicts
                                    )
                                },
                                onCancelImportConflicts = {
                                    importViewModel.processIntent(
                                        ImportUiIntent.CancelImportConflicts
                                    )
                                },
                                onImportBackupClick = {
                                    WebPlatform.pickBinaryFile(accept = "*/*") { fileName, base64 ->
                                        val bytes = WebBytes.base64Decode(base64)
                                        val uri = WebArchiveRegistry.register(bytes, fileName)
                                        val detected = LaudateFileDetector.detect(
                                            text = null,
                                            fileName = fileName,
                                            isZip = true,
                                            headBytes = bytes.copyOfRange(
                                                0,
                                                minOf(bytes.size, 4096)
                                            )
                                        )
                                        importViewModel.processIntent(
                                            ImportUiIntent.OfferPendingFile(
                                                detected,
                                                fileName,
                                                archiveUri = uri
                                            )
                                        )
                                    }
                                }
                            )
                        }

                        is WebScreen.Manual -> {
                            val state by manualViewModel.uiState.collectAsState()
                            ManualScreen(
                                uiState = state,
                                sideEffects = manualViewModel.sideEffect,
                                onIntent = manualViewModel::processIntent,
                                onNavigateBack = { screen = WebScreen.Catalog }
                            )
                        }
                    }
                }
            }
        }
    }

    // Gestión de categorías: la misma hoja que Android y escritorio.
    if (showCategorySheet) {
        val catalogState by catalogViewModel.uiState.collectAsState()
        CategoryManagementSheet(
            categories = catalogState.categories,
            songCounts = catalogState.categorySongCounts,
            onAddCategory = { newCategory ->
                scope.launch { container.categoryRepository.saveCategory(newCategory) }
            },
            onEditCategory = { category, newName, newKind ->
                scope.launch {
                    // 1) Renombra y propaga el nombre a canciones y repertorios.
                    container.updateCategoryUseCase(category.id, newName)
                    // 2) El tipo se actualiza aparte, con el nombre NUEVO (el modelo trae el viejo).
                    if (category.kind != newKind) {
                        container.categoryRepository.updateCategory(
                            category.copy(name = newName.trim(), kind = newKind)
                        )
                    }
                }
            },
            onDeleteCategory = { categoryId ->
                scope.launch { container.categoryRepository.deleteCategory(categoryId) }
            },
            onDismissRequest = { showCategorySheet = false }
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false }, appVersionName = container.appVersionName)
    }
}

/** Nombre de archivo seguro (sin caracteres prohibidos por el sistema de archivos). */
internal fun safeFileName(raw: String): String {
    val cleaned = raw.map { ch ->
        if (ch.isLetterOrDigit() || ch == ' ' || ch == '-' || ch == '_' || ch == '.') ch else '_'
    }.joinToString("").trim().trim('.').replace(Regex("\\s+"), "_")
    return cleaned.ifBlank { "canticora" }.take(80)
}

/**
 * Panel de herramientas del `CanticoraScaffold` según la pantalla: la misma regla que en
 * escritorio (`toolSettingsFor`) — la herramienta activa manda.
 */
@Composable
private fun webToolSettings(
    screen: WebScreen,
    catalogViewModel: SongCatalogViewModel,
    celebrationViewModel: CelebrationViewModel,
    rhythmViewModel: RhythmPatternViewModel,
    tablatureViewModel: TablatureViewModel,
    songEditorViewModel: SongEditorViewModel,
    songDetailViewModel: SongDetailViewModel,
    songEditorToolHost: SongEditorToolHost,
    onAddAttachmentFileClick: () -> Unit,
    onOpenAttachmentClick: (SongAttachment) -> Unit
): (@Composable () -> Unit)? = when (screen) {
    is WebScreen.Catalog -> {
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

    is WebScreen.CelebrationList -> {
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

    is WebScreen.RhythmPatterns, is WebScreen.RhythmPatternEditor -> {
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

    is WebScreen.Tablatures, is WebScreen.TablatureEditor -> {
        val state by tablatureViewModel.uiState.collectAsState()
        val panel: @Composable () -> Unit = {
            TablatureToolSettings(uiState = state, onIntent = tablatureViewModel::processIntent)
        }
        panel
    }

    is WebScreen.SongEditor -> {
        val state by songEditorViewModel.uiState.collectAsState()
        val tabState by tablatureViewModel.uiState.collectAsState()
        val panel: @Composable () -> Unit = {
            // En la pestaña «Tablaturas» el panel derecho pasa a ser el de la tablatura.
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
                    onExportAttachmentClick = null,
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

    is WebScreen.SongDetail -> {
        val state by songDetailViewModel.uiState.collectAsState()
        val panel: @Composable () -> Unit = {
            SongReaderToolSettings(
                hasRhythm = state.rhythmPattern != null || state.sectionRhythmPatterns.isNotEmpty(),
                rhythmNotationMode = state.rhythmNotationMode,
                onRhythmNotationChange = {
                    songDetailViewModel.processIntent(SongDetailUiIntent.ChangeRhythmNotationMode(it))
                },
                fontSizeSp = state.fontSizeSp,
                onFontSizeChange = {
                    songDetailViewModel.processIntent(SongDetailUiIntent.UpdateFontSize(it))
                },
                onFontSizeChangeFinished = {
                    songDetailViewModel.processIntent(SongDetailUiIntent.SaveFontSize)
                },
                hasTablature = state.visibleTablatures.isNotEmpty(),
                tablatureScale = state.tablatureScale,
                onTablatureScaleChange = {
                    songDetailViewModel.processIntent(SongDetailUiIntent.UpdateTablatureScale(it))
                },
                onTablatureScaleChangeFinished = {
                    songDetailViewModel.processIntent(SongDetailUiIntent.SaveTablatureScale)
                },
                tablatureNumberScale = state.tablatureNumberScale,
                onTablatureNumberScaleChange = {
                    songDetailViewModel.processIntent(SongDetailUiIntent.UpdateTablatureNumberScale(it))
                },
                onTablatureNumberScaleChangeFinished = {
                    songDetailViewModel.processIntent(SongDetailUiIntent.SaveTablatureNumberScale)
                }
            )
        }
        panel
    }

    else -> null
}
