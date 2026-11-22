package com.grafitto.laudate.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.graphics.Color
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.domain.model.score.toScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabDisplayPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.backup.CANTICORA_BACKUP_EXTENSION
import com.grafitto.laudate.domain.backup.canticoraBackupFileName
import com.grafitto.laudate.domain.transfer.LaudateFileDetector
import com.grafitto.laudate.domain.usecase.SearchSongsUseCase
import com.grafitto.laudate.ui.about.AboutDialog
import com.grafitto.laudate.ui.backup.BackupScreen
import com.grafitto.laudate.ui.backup.BackupUiIntent
import com.grafitto.laudate.ui.backup.BackupUiState
import com.grafitto.laudate.ui.backup.BackupViewModel
import com.grafitto.laudate.ui.catalog.SongCatalogScreen
import com.grafitto.laudate.ui.catalog.SongCatalogSideEffect
import com.grafitto.laudate.ui.catalog.SongCatalogUiIntent
import com.grafitto.laudate.ui.catalog.SongCatalogViewModel
import com.grafitto.laudate.ui.category.CategoryManagementSheet
import com.grafitto.laudate.ui.celebration.CelebrationEditorScreen
import com.grafitto.laudate.ui.celebration.CelebrationEditorSideEffect
import com.grafitto.laudate.ui.celebration.CelebrationEditorUiIntent
import com.grafitto.laudate.ui.celebration.CelebrationListScreen
import com.grafitto.laudate.ui.celebration.CelebrationListUiIntent
import com.grafitto.laudate.ui.celebration.CelebrationViewModel
import com.grafitto.laudate.ui.celebration.live.LiveCelebrationScreen
import com.grafitto.laudate.ui.common.AppMessageController
import com.grafitto.laudate.ui.common.AppMessageHost
import com.grafitto.laudate.ui.common.LaudateLinks
import com.grafitto.laudate.ui.navigation.CanticoraScaffold
import com.grafitto.laudate.ui.navigation.CatalogToolSettings
import com.grafitto.laudate.ui.navigation.CelebrationListToolSettings
import com.grafitto.laudate.ui.navigation.CommonSettingsPanel
import com.grafitto.laudate.ui.navigation.NavBrandLogoSize
import com.grafitto.laudate.ui.navigation.NavEntry
import com.grafitto.laudate.ui.navigation.RhythmToolSettings
import com.grafitto.laudate.ui.navigation.SongReaderToolSettings
import com.grafitto.laudate.ui.navigation.TablatureToolSettings
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
import com.grafitto.laudate.ui.manual.ManualScreen
import com.grafitto.laudate.ui.manual.ManualViewModel
import com.grafitto.laudate.ui.rhythm.RhythmPatternEditorScreen
import com.grafitto.laudate.ui.rhythm.RhythmPatternsScreen
import com.grafitto.laudate.ui.rhythm.RhythmPatternSideEffect
import com.grafitto.laudate.ui.rhythm.RhythmPatternUiIntent
import com.grafitto.laudate.ui.rhythm.RhythmPatternViewModel
import com.grafitto.laudate.ui.tablature.TablatureEditorScreen
import com.grafitto.laudate.ui.tablature.TablatureScreen
import com.grafitto.laudate.ui.tablature.TablatureSectionRef
import com.grafitto.laudate.ui.tablature.TablatureSideEffect
import com.grafitto.laudate.ui.tablature.TablatureUiIntent
import com.grafitto.laudate.ui.tablature.TablatureViewModel
import com.grafitto.laudate.ui.search.SongSearchViewModel
import com.grafitto.laudate.ui.song.SongDetailScreen
import com.grafitto.laudate.ui.song.SongDetailUiIntent
import com.grafitto.laudate.ui.song.SongDetailViewModel
import com.grafitto.laudate.domain.model.LaudateBrand
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.ui.theme.LaudateTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Pantallas de escritorio (mismo contrato conceptual que `AppScreen` en Android). */
sealed interface DesktopScreen {
    data object Catalog : DesktopScreen
    data class SongDetail(val songId: String) : DesktopScreen
    data class SongEditor(val songId: String?) : DesktopScreen
    data object CelebrationList : DesktopScreen
    data class CelebrationEditor(val celebrationId: String?) : DesktopScreen
    data class LiveCelebration(val celebrationId: String) : DesktopScreen
    data object RhythmPatterns : DesktopScreen
    data object RhythmPatternEditor : DesktopScreen
    data class Tablatures(val songId: String, val songTitle: String) : DesktopScreen
    data object TablatureEditor : DesktopScreen
    data object Backup : DesktopScreen
    data object Import : DesktopScreen
    data object Manual : DesktopScreen
}

/**
 * Raíz de la UI de escritorio.
 *
 * Reutiliza **tal cual** los ViewModels y las pantallas compartidos con Android
 * (`:core:presentation` + `:core:ui`). Lo único específico de escritorio es el "cascarón": diálogos
 * de fichero AWT ([DesktopFileDialogs]), apertura externa (`java.awt.Desktop`) y un contenedor sin
 * `Context`. Ver ADR-013.
 */
@Composable
fun WindowScope.LaudateDesktopRoot(
    container: DesktopContainer,
    titleBar: @Composable WindowScope.() -> Unit,
    frame: @Composable WindowScope.(content: @Composable () -> Unit) -> Unit
) {
    LaunchedEffect(Unit) { container.seedCategories() }

    var screen by remember { mutableStateOf<DesktopScreen>(DesktopScreen.Catalog) }
    // Mensajes efímeros (toast) compartidos con Android; se pintan como snackbar Material 3.
    val messages = remember { AppMessageController() }
    var showAbout by remember { mutableStateOf(false) }
    var showCategorySheet by remember { mutableStateOf(false) }
    val pdfExporter = remember { DesktopPdfExporter() }
    val scope = rememberCoroutineScope()

    // Modo de visualización y posición de tablatura: preferencias GLOBALES (misma que Android).
    val songDisplayMode by container.preferencesRepository.songDisplayMode
        .collectAsState(initial = com.grafitto.laudate.domain.model.song.SongDisplayMode.CHORDS_ABOVE_TEXT)
    val tabDisplayPosition by container.preferencesRepository.tabDisplayPosition
        .collectAsState(initial = TabDisplayPosition.AFTER)

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
        SongSearchViewModel(container.songRepository, container.categoryRepository, SearchSongsUseCase())
    }
    // Un único VM de ritmos para listado y editor: el patrón en construcción sobrevive a la navegación.
    val rhythmViewModel = remember(container) {
        RhythmPatternViewModel(
            rhythmPatternRepository = container.rhythmPatternRepository,
            rhythmPlayer = container.rhythmPlayer,
            exportRhythmPatternsUseCase = container.exportRhythmPatternsUseCase,
            importRhythmPatternsUseCase = container.importRhythmPatternsUseCase
        )
    }
    // Un único VM de tablaturas para listado y editor (ADR-017, Feature T).
    val tablatureViewModel = remember(container) {
        // `DesktopTabNotePlayer`: aviso sonoro al escribir una nota (solo editor).
        TablatureViewModel(
            tablatureRepository = container.tablatureRepository,
            // (D-48) Sin esto los ajustes del pentagrama no se guardarían ni llegarían al PDF.
            preferencesRepository = container.preferencesRepository,
            notePlayer = com.grafitto.laudate.data.tablature.DesktopTabNotePlayer(),
            importTablatureUseCase = container.importTablatureUseCase
        )
    }
    // El editor de canto se crea aquí (y no dentro de su pantalla) para que el panel derecho de
    // ajustes pueda leer y cambiar sus metadatos musicales (compás, tonalidad, tempo, cejilla)
    // aunque el panel viva fuera del contenido.
    val songEditorKey = (screen as? DesktopScreen.SongEditor)?.songId
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
    // Estado compartido del **panel de herramientas** del editor de canto (estilo Photoshop): la
    // tira de herramientas vive en el panel derecho y cambia su contenido según la activa. El editor
    // registra en él el insertador en el cursor, así que el host debe vivir por encima de ambos.
    val songEditorToolHost = remember { SongEditorToolHost() }
    // Mientras se **escribe** una tablatura, los ajustes de lectura (cómo se ve la canción y dónde va
    // la tablatura) no tienen efecto visible: son cosas del visor y de la vista previa. Se ocultan
    // para no ofrecer mandos que no hacen nada en ese contexto.
    val editorStateForReading by songEditorViewModel.uiState.collectAsState()
    val tabStateForReading by tablatureViewModel.uiState.collectAsState()
    val showReadingSettings = !(
        screen is DesktopScreen.Tablatures ||
            screen is DesktopScreen.TablatureEditor ||
            (screen is DesktopScreen.SongEditor &&
                editorStateForReading.editorTab == SongEditorTab.TABLATURES &&
                tabStateForReading.isEditorOpen)
        )
    // Callbacks de adjuntos del editor, izados aquí para que el **panel de herramientas** (que vive
    // fuera del contenido) pueda ofrecer «Archivo» y abrir los adjuntos igual que la pantalla. Sin
    // esto el panel solo podía añadir enlaces.
    val addSongAttachmentFileClick: () -> Unit = {
        scope.launch {
            DesktopFileDialogs.openFile("Añadir adjunto")?.let { file ->
                songEditorViewModel.processIntent(
                    SongEditorUiIntent.AddAttachmentFromUri(file.absolutePath, file.name, null)
                )
            }
        }
    }
    val openSongAttachmentClick: (SongAttachment) -> Unit = { openExternally(it.uri) }
    // El visor de canto se crea aquí (y no dentro de su pantalla) para que el panel derecho de
    // ajustes pueda leer y cambiar sus preferencias de lectura (notación del ritmo, tamaño de letra)
    // aunque el panel viva fuera del contenido.
    val songDetailKey = (screen as? DesktopScreen.SongDetail)?.songId
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
    val backupViewModel = remember(container) {
        BackupViewModel(
            container.songRepository, container.attachmentRepository,
            container.exportBackupUseCase, container.importBackupUseCase
        )
    }
    val importViewModel = remember(container) {
        ImportViewModel(
            container.songRepository, container.celebrationRepository, container.categoryRepository,
            container.importCatalogBundleUseCase, container.importCelebrationBundleUseCase,
            container.importChordProSongsUseCase, container.importRhythmPatternsUseCase,
            container.importSongPackageUseCase
        )
    }
    val manualViewModel = remember { ManualViewModel() }

    // Fichero `.laudate` abierto con la app (doble clic / «Abrir con»): se detecta y se ofrece
    // importarlo en la pantalla «Importar» (equivalente de escritorio del archivo recibido).
    val incomingHandler = remember(container) { DesktopIncomingFileHandler(container) }
    LaunchedEffect(Unit) {
        val file = container.incomingFile ?: return@LaunchedEffect
        val detected = withContext(Dispatchers.IO) { incomingHandler.detect(file) }
        if (detected != null) {
            importViewModel.processIntent(
                ImportUiIntent.OfferPendingFile(
                    detected = detected,
                    fileName = file.name,
                    // Un ZIP (copia o paquete de canto) se lee por su ruta; el resto ya viaja en texto.
                    archiveUri = if (detected.isZip) file.absolutePath else null
                )
            )
            screen = DesktopScreen.Import
        } else {
            messages.show("El archivo no es de Laudate: ${file.name}")
        }
    }

    // Al confirmar en «Importar», si hay un fichero entrante se importa de verdad.
    LaunchedEffect(importViewModel) {
        importViewModel.sideEffect.collect { effect ->
            when (effect) {
                is com.grafitto.laudate.ui.importer.ImportSideEffect.ShowMessage -> messages.show(effect.message)
                is com.grafitto.laudate.ui.importer.ImportSideEffect.CatalogChanged -> Unit
                else -> Unit
            }
        }
    }

    val notation by container.preferencesRepository.notationSystem
        .collectAsState(initial = NotationSystem.LATIN)

    // Instrumento preferido de tablatura (global) y estado del editor de tablaturas: alimentan los
    // ajustes del panel lateral (comunes y de la herramienta activa).
    val preferredInstrument by container.preferencesRepository.preferredTablatureInstrument
        .collectAsState(initial = null)
    // (D-48) Ajustes del pentagrama de tablatura: preferencia GLOBAL que el PDF respeta.
    val tablatureStaffOptions by container.preferencesRepository.tablatureStaffOptions
        .collectAsState(initial = com.grafitto.laudate.domain.tablature.TabStaffOptions())
    val tablatureState by tablatureViewModel.uiState.collectAsState()
    // Estado del visor: el panel de ajustes solo enseña los mandos de tablatura que **esa canción**
    // necesita (instrumentos que tiene, posición de la tablatura…), así que hay que observarlo aquí,
    // fuera del contenido, igual que en Android.
    val songDetailStateForPanel by songDetailViewModel.uiState.collectAsState()

    LaunchedEffect(catalogViewModel) {
        catalogViewModel.sideEffect.collect { effect ->
            when (effect) {
                is SongCatalogSideEffect.ShowMessage -> messages.show(effect.message)
                // En escritorio no hay hoja de compartir: se guarda el ChordPro como archivo.
                is SongCatalogSideEffect.ShareSong -> {
                    DesktopFileDialogs.saveFile("Guardar canción", "${effect.title}.chordpro")?.let { file ->
                        runCatching { file.writeText(effect.chordPro) }.fold(
                            onSuccess = { messages.show("Canción guardada: ${file.name}") },
                            onFailure = { messages.show("No se pudo guardar: ${it.message ?: "error"}") }
                        )
                    }
                }
                is SongCatalogSideEffect.ShareSongPackage -> {
                    DesktopFileDialogs.saveFile("Guardar canto con adjuntos", effect.suggestedFileName)?.let { file ->
                        scope.launch {
                            runCatching {
                                container.exportSongPackageUseCase(file.absolutePath, effect.songId)
                            }.fold(
                                onSuccess = { messages.show("Canto guardado: ${file.name}") },
                                onFailure = { messages.show("No se pudo guardar: ${it.message ?: "error"}") }
                            )
                        }
                    }
                }
                is SongCatalogSideEffect.ExportSongPdf -> {
                    DesktopFileDialogs.saveFile("Guardar PDF", effect.suggestedFileName)?.let { file ->
                        val patterns = container.rhythmPatternRepository.getAllPatterns().first()
                        val tabs = container.tablatureRepository.getTablaturesForSong(effect.song.id)
                        pdfExporter.write(
                            file,
                            com.grafitto.laudate.domain.model.rhythm.RhythmPattern.resolveFor(effect.song, patterns),
                            com.grafitto.laudate.domain.export.SongPdfOptions(
                                displayMode = effect.mode,
                                notationSystem = notation,
                                tablatureStaff = tablatureStaffOptions
                            ),
                            tablatures = tabs
                        ).fold(
                            onSuccess = { messages.show("PDF guardado: ${file.name}") },
                            onFailure = { messages.show("No se pudo generar el PDF: ${it.message ?: "error"}") }
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    // Confirmación con «Deshacer» tras añadir un canto a un repertorio (mismo contrato que en
    // Android). El mensaje de éxito NO viaja como SideEffect: la acción vive en `pendingAction`.
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

    // PDF de un repertorio (botón Imprimir del editor de misas): guardar con diálogo nativo.
    LaunchedEffect(celebrationViewModel) {
        celebrationViewModel.sideEffect.collect { effect ->
            when (effect) {
                is CelebrationEditorSideEffect.ExportCelebrationPdf -> {
                    DesktopFileDialogs.saveFile("Guardar PDF del repertorio", effect.fileName)?.let { file ->
                        val patterns = container.rhythmPatternRepository.getAllPatterns().first()
                        val songs = effect.songs.map { it.copy(song = com.grafitto.laudate.domain.model.rhythm.RhythmPattern.resolveFor(it.song, patterns)) }
                        pdfExporter.writeCelebration(
                            file,
                            effect.celebration,
                            songs,
                            com.grafitto.laudate.domain.export.SongPdfOptions(
                                displayMode = effect.mode,
                                notationSystem = notation,
                                tablatureStaff = tablatureStaffOptions
                            )
                        ).fold(
                            onSuccess = { messages.show("PDF guardado: ${file.name}") },
                            onFailure = { messages.show("No se pudo generar el PDF: ${it.message ?: "error"}") }
                        )
                    }
                }
                is CelebrationEditorSideEffect.NavigateBackOnDeleted -> {
                    messages.show("Repertorio eliminado")
                    screen = DesktopScreen.CelebrationList
                }
                is CelebrationEditorSideEffect.ShareCelebrationFile -> {
                    DesktopFileDialogs.saveFile("Guardar repertorio", effect.fileName)?.let { file ->
                        runCatching { file.writeText(effect.content) }.fold(
                            onSuccess = { messages.show("Repertorio guardado: ${file.name}") },
                            onFailure = { messages.show("No se pudo guardar: ${it.message ?: "error"}") }
                        )
                    }
                }
                is CelebrationEditorSideEffect.ShowMessage -> messages.show(effect.message)
                else -> Unit
            }
        }
    }

    // Confirmación con «Deshacer» tras eliminar un repertorio.
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
                    celebrationViewModel.processListIntent(CelebrationListUiIntent.UndoDeleteCelebration)
                } else {
                    celebrationViewModel.processListIntent(CelebrationListUiIntent.DismissPendingDelete)
                }
            }
    }

    // Efectos de ritmos: compartir, copiar y avisos con «Deshacer».
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
                    DesktopFileDialogs.saveFile("Guardar ritmo", effect.fileName)?.let { file ->
                        runCatching { file.writeText(effect.content) }.fold(
                            onSuccess = { messages.show("Ritmo guardado: ${file.name}") },
                            onFailure = { messages.show("No se pudo guardar: ${it.message ?: "error"}") }
                        )
                    }
                }
                is RhythmPatternSideEffect.CopyToClipboard -> {
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard
                        .setContents(java.awt.datatransfer.StringSelection(effect.text), null)
                    messages.show("Ritmo copiado: pégalo donde quieras")
                }
            }
        }
    }

    // Efectos de tablaturas (ADR-017, Feature T): avisos, «Deshacer» y compartir como fichero.
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
                    DesktopFileDialogs.saveFile("Guardar tablatura", effect.fileName)?.let { file ->
                        runCatching { file.writeText(effect.content) }.fold(
                            onSuccess = { messages.show("Tablatura guardada: ${file.name}") },
                            onFailure = { messages.show("No se pudo guardar: ${it.message ?: "error"}") }
                        )
                    }
                }
                is TablatureSideEffect.RequestTablatureFile -> {
                    // Selector de archivo para elegir la partitura: MusicXML, MuseScore (.mscz),
                    // Guitar Pro 7/8 (.gp, .gpx) o TuxGuitar (.tg).
                    DesktopFileDialogs.openFile("Importar partitura")?.let { file ->
                        tablatureViewModel.processIntent(
                            com.grafitto.laudate.ui.tablature.TablatureUiIntent
                                .ImportTablatureFromFile(file.absolutePath, file.name)
                        )
                    }
                }
            }
        }
    }

    LaudateTheme {
        frame {
            // Estilo WhatsApp Desktop: el menú y la barra de título van sobre un fondo
            // algo más oscuro; el contenido es una tarjeta clara con la esquina superior
            // izquierda redondeada, como si el menú quedara "por debajo".
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                titleBar()
                AppMessageHost(
                    controller = messages,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    CanticoraScaffold(
                        navEntries = desktopNavEntries,
                        currentNavId = desktopScreenId(screen),
                        onSelectNav = { id -> screen = desktopScreenFor(id, screen) },
                        screenKey = screen,
                        brandTitle = LaudateBrand.NAME,
                        brandTagline = LaudateBrand.TAGLINE,
                        brandLogo = {
                            // La «T» de la marca, a tamaño de cabecera de panel.
                            com.grafitto.laudate.ui.common.AppLogo(
                                contentDescription = "Logotipo de Canticora",
                                modifier = Modifier.size(NavBrandLogoSize)
                            )
                        },
                        navActions = desktopNavActions,
                        onNavAction = { id ->
                            when (id) {
                                ActionCategories -> showCategorySheet = true
                                "manual" -> screen = DesktopScreen.Manual
                                ActionSupport -> openUrlExternally(LaudateLinks.SUPPORT)
                                ActionAbout -> showAbout = true
                            }
                        },
                        sectionTitle = desktopSectionTitleFor(screen),
                        commonSettings = {
                            CommonSettingsPanel(
                                notationSystem = notation,
                                onNotationChange = { scope.launch { container.preferencesRepository.setNotationSystem(it) } },
                                preferredInstrument = preferredInstrument,
                                // **Solo los instrumentos que ESTA canción tiene** en sus
                                // tablaturas: ofrecer las diez afinaciones del catálogo para un
                                // canto que solo trae guitarra llena el desplegable de opciones que
                                // no hacen nada. Sin tablaturas, la lista va vacía y el desplegable
                                // desaparece.
                                instrumentOptions = when (val s = screen) {
                                    is DesktopScreen.SongDetail ->
                                        songDetailStateForPanel.tablatureInstruments
                                    is DesktopScreen.SongEditor ->
                                        tabStateForReading.tablatures.map { it.tuning.name }.distinct()
                                    else -> emptyList()
                                },
                                onInstrumentChange = { scope.launch { container.preferencesRepository.setPreferredTablatureInstrument(it) } },
                                displayMode = songDisplayMode,
                                onDisplayModeChange = { scope.launch { container.preferencesRepository.setSongDisplayMode(it) } },
                                tabDisplayPosition = tabDisplayPosition,
                                onTabDisplayPositionChange = { scope.launch { container.preferencesRepository.setTabDisplayPosition(it) } },
                                // La misma regla que en Android: sin tablatura en pantalla no hay
                                // «Instrumento preferido» ni «Posición de la tablatura».
                                // La misma regla que en Android: se mira si el canto **tiene**
                                // tablaturas, no si ahora mismo se ven. Atarlo a lo que se ve
                                // escondía las opciones de tablatura del desplegable de vista y no
                                // había forma de elegirlas.
                                hasTablature = when (val s = screen) {
                                    is DesktopScreen.SongDetail ->
                                        songDetailStateForPanel.tablatures.isNotEmpty()
                                    is DesktopScreen.SongEditor ->
                                        tabStateForReading.tablatures.isNotEmpty()
                                    else -> false
                                },
                                // Al escribir una tablatura, los ajustes de lectura no tienen efecto
                                // visible: se ocultan (son cosas del visor y la vista previa).
                                showReadingSettings = showReadingSettings
                            )
                        },
                        toolSettings = toolSettingsFor(
                            screen = screen,
                            catalogViewModel = catalogViewModel,
                            celebrationViewModel = celebrationViewModel,
                            rhythmViewModel = rhythmViewModel,
                            tablatureViewModel = tablatureViewModel,
                            songEditorViewModel = songEditorViewModel,
                            songDetailViewModel = songDetailViewModel,
                            songEditorToolHost = songEditorToolHost,
                            onAddAttachmentFileClick = addSongAttachmentFileClick,
                            onOpenAttachmentClick = openSongAttachmentClick
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) { _ ->
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                        when (val s = screen) {
                        is DesktopScreen.Catalog -> {
                            val state by catalogViewModel.uiState.collectAsState()
                            SongCatalogScreen(
                                uiState = state,
                                onIntent = catalogViewModel::processIntent,
                                onSongClick = { screen = DesktopScreen.SongDetail(it) },
                                onCreateSongClick = { screen = DesktopScreen.SongEditor(null) },
                                notationSystem = notation
                            )
                        }

                        is DesktopScreen.SongDetail -> {
                            val detailViewModel = songDetailViewModel
                            LaunchedEffect(s.songId) {
                                detailViewModel.processIntent(SongDetailUiIntent.LoadSong(s.songId))
                            }
                            val state by detailViewModel.uiState.collectAsState()
                            SongDetailScreen(
                                uiState = state,
                                onIntent = detailViewModel::processIntent,
                                onNavigateBack = { screen = DesktopScreen.Catalog },
                                onEditClick = { screen = DesktopScreen.SongEditor(s.songId) },
                                onToggleFocus = {
                                    detailViewModel.processIntent(SongDetailUiIntent.ToggleFocusMode)
                                },
                                onAddAttachmentFileClick = {
                                    scope.launch {
                                        DesktopFileDialogs.openFile("Añadir adjunto")?.let { file ->
                                            detailViewModel.processIntent(
                                                SongDetailUiIntent.AddAttachmentFromUri(file.absolutePath, file.name, null)
                                            )
                                        }
                                    }
                                },
                                onTablaturesClick = {
                                    screen = DesktopScreen.Tablatures(s.songId, state.song?.title.orEmpty())
                                },
                                onOpenAttachmentExternally = { openExternally(it.uri) }
                            )
                        }

                        is DesktopScreen.SongEditor -> {
                            val editorViewModel = songEditorViewModel
                            LaunchedEffect(s.songId) {
                                editorViewModel.processIntent(SongEditorUiIntent.LoadSongForEditing(s.songId))
                            }
                            // PDF de la canción (botón PDF del editor): guardar con diálogo nativo.
                            LaunchedEffect(editorViewModel) {
                                editorViewModel.sideEffect.collect { effect ->
                                    when (effect) {
                                        is SongEditorSideEffect.ExportSongPdf -> {
                                            DesktopFileDialogs.saveFile("Guardar PDF", effect.suggestedFileName)?.let { file ->
                                                val patterns = container.rhythmPatternRepository.getAllPatterns().first()
                                                val tabs = effect.song.id?.let { container.tablatureRepository.getTablaturesForSong(it) }.orEmpty()
                                                pdfExporter.write(
                                                    file,
                                                    com.grafitto.laudate.domain.model.rhythm.RhythmPattern.resolveFor(effect.song, patterns),
                                                    com.grafitto.laudate.domain.export.SongPdfOptions(
                                                        displayMode = effect.mode,
                                                        notationSystem = notation,
                                                        tablatureStaff = tablatureStaffOptions
                                                    ),
                                                    tablatures = tabs
                                                ).fold(
                                                    onSuccess = { messages.show("PDF guardado: ${file.name}") },
                                                    onFailure = { messages.show("No se pudo generar el PDF: ${it.message ?: "error"}") }
                                                )
                                            }
                                        }
                                        is SongEditorSideEffect.NavigateBackOnSaved -> {
                                            messages.show("Canción guardada correctamente")
                                            screen = DesktopScreen.Catalog
                                        }
                                        is SongEditorSideEffect.NavigateBackOnDeleted -> {
                                            messages.show("Canción eliminada")
                                            screen = DesktopScreen.Catalog
                                        }
                                        is SongEditorSideEffect.NavigateBack -> screen = DesktopScreen.Catalog
                                        is SongEditorSideEffect.ShareSong -> {
                                            DesktopFileDialogs.saveFile("Guardar canción", "${effect.title}.chordpro")?.let { file ->
                                                runCatching { file.writeText(effect.chordPro) }.fold(
                                                    onSuccess = { messages.show("Canción guardada: ${file.name}") },
                                                    onFailure = { messages.show("No se pudo guardar: ${it.message ?: "error"}") }
                                                )
                                            }
                                        }
                                        is SongEditorSideEffect.ShareSongPackage -> {
                                            DesktopFileDialogs.saveFile("Guardar canto con adjuntos", effect.suggestedFileName)?.let { file ->
                                                runCatching {
                                                    container.exportSongPackageUseCase(file.absolutePath, effect.songId)
                                                }.fold(
                                                    onSuccess = { messages.show("Canto guardado: ${file.name}") },
                                                    onFailure = { messages.show("No se pudo guardar: ${it.message ?: "error"}") }
                                                )
                                            }
                                        }
                                        is SongEditorSideEffect.ShowMessage -> messages.show(effect.message)
                                        else -> Unit
                                    }
                                }
                            }
                            val state by editorViewModel.uiState.collectAsState()
                            // Tablaturas del canto (tercera pestaña del editor).
                            val editorTabState by tablatureViewModel.uiState.collectAsState()
                            LaunchedEffect(state.songId) {
                                // Un canto nuevo aún sin guardar no tiene id: se suelta el canto
                                // anterior para que su tablatura no se cuele aquí.
                                if (state.songId == null) {
                                    tablatureViewModel.processIntent(TablatureUiIntent.ClearSong)
                                }
                                state.songId?.let { songId ->
                                    tablatureViewModel.processIntent(
                                        TablatureUiIntent.LoadForSong(songId, state.title, state.originalKey)
                                    )
                                    // Secciones del canto: permiten anclar la tablatura a una parte
                                    // (estribillo, estrofa…) desde el panel de herramientas.
                                    val song = container.songRepository.getSongById(songId).first()
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
                                uiState = state,
                                onIntent = editorViewModel::processIntent,
                                onAddAttachmentFileClick = addSongAttachmentFileClick,
                                onOpenAttachmentClick = openSongAttachmentClick,
                                tablatureUiState = editorTabState,
                                onTablatureIntent = tablatureViewModel::processIntent,
                                previewDisplayMode = songDisplayMode,
                                previewTabPosition = tabDisplayPosition,
                                onPreviewDisplayModeChange = { mode ->
                                    scope.launch { container.preferencesRepository.setSongDisplayMode(mode) }
                                },
                                onPreviewTabPositionChange = { position ->
                                    scope.launch { container.preferencesRepository.setTabDisplayPosition(position) }
                                },
                                // (D-82) La vista previa dibuja la tablatura con el tamaño global.
                                previewTablatureScale = editorTabState?.tablatureScale ?: 1f,
                                previewTablatureStaffOptions = editorTabState?.editorStaffOptions
                                    ?: com.grafitto.laudate.domain.tablature.TabStaffOptions(),
                                toolHost = songEditorToolHost
                            )
                        }

                        is DesktopScreen.CelebrationList -> {
                            val state by celebrationViewModel.listUiState.collectAsState()
                            CelebrationListScreen(
                                uiState = state,
                                onIntent = celebrationViewModel::processListIntent,
                                onCelebrationClick = { id ->
                                    celebrationViewModel.processEditorIntent(
                                        CelebrationEditorUiIntent.LoadForEdit(id)
                                    )
                                    screen = DesktopScreen.CelebrationEditor(id)
                                },
                                onLiveModeClick = { screen = DesktopScreen.LiveCelebration(it) },
                                onCreateCelebrationClick = {
                                    celebrationViewModel.processEditorIntent(
                                        CelebrationEditorUiIntent.LoadForEdit(null)
                                    )
                                    screen = DesktopScreen.CelebrationEditor(null)
                                },
                                onImportClick = {
                                    scope.launch {
                                        DesktopFileDialogs.openFile("Importar repertorio")?.let { file ->
                                            celebrationViewModel.processListIntent(
                                                CelebrationListUiIntent.ImportCelebrationFile(file.readText())
                                            )
                                        }
                                    }
                                }
                            )
                        }

                        is DesktopScreen.CelebrationEditor -> {
                            LaunchedEffect(s.celebrationId) {
                                if (s.celebrationId != null) {
                                    celebrationViewModel.processEditorIntent(
                                        CelebrationEditorUiIntent.LoadForEdit(s.celebrationId)
                                    )
                                }
                            }
                            val state by celebrationViewModel.editorUiState.collectAsState()
                            val searchState by searchViewModel.uiState.collectAsState()
                            CelebrationEditorScreen(
                                uiState = state,
                                onIntent = celebrationViewModel::processEditorIntent,
                                onNavigateBack = { screen = DesktopScreen.CelebrationList },
                                searchState = searchState,
                                onSearchIntent = searchViewModel::processIntent,
                                notationSystem = notation
                            )
                        }

                        is DesktopScreen.LiveCelebration -> {
                            LiveCelebrationScreen(
                                celebrationId = s.celebrationId,
                                celebrationRepository = container.celebrationRepository,
                                songRepository = container.songRepository,
                                getTransposedSongUseCase = container.getTransposedSongUseCase,
                                parseChordProUseCase = container.parseChordProUseCase,
                                preferencesRepository = container.preferencesRepository,
                                tablatureRepository = container.tablatureRepository,
                                onCloseLiveMode = { screen = DesktopScreen.CelebrationList }
                            )
                        }

                        is DesktopScreen.RhythmPatterns -> {
                            val state by rhythmViewModel.uiState.collectAsState()
                            LaunchedEffect(state.isEditorOpen) {
                                if (state.isEditorOpen) screen = DesktopScreen.RhythmPatternEditor
                            }
                            RhythmPatternsScreen(
                                uiState = state,
                                onIntent = rhythmViewModel::processIntent,
                                onNavigateBack = { screen = DesktopScreen.Catalog }
                            )
                        }

                        is DesktopScreen.RhythmPatternEditor -> {
                            val state by rhythmViewModel.uiState.collectAsState()
                            LaunchedEffect(state.isEditorOpen) {
                                if (!state.isEditorOpen) screen = DesktopScreen.RhythmPatterns
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
                                    rhythmViewModel.processIntent(RhythmPatternUiIntent.CloseEditor)
                                }
                            )
                        }

                        is DesktopScreen.Tablatures -> {
                            val state by tablatureViewModel.uiState.collectAsState()
                            LaunchedEffect(state.isEditorOpen) {
                                if (state.isEditorOpen) screen = DesktopScreen.TablatureEditor
                            }
                            LaunchedEffect(s.songId) {
                                // Se trae el canto para sus secciones y su tonalidad (tonalidad escrita).
                                val song = container.songRepository.getSongById(s.songId).first()
                                tablatureViewModel.processIntent(
                                    TablatureUiIntent.LoadForSong(s.songId, s.songTitle, song?.originalKey.orEmpty())
                                )
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
                            TablatureScreen(
                                uiState = state,
                                onIntent = tablatureViewModel::processIntent,
                                onNavigateBack = { screen = DesktopScreen.SongDetail(s.songId) }
                            )
                        }

                        is DesktopScreen.TablatureEditor -> {
                            val state by tablatureViewModel.uiState.collectAsState()
                            LaunchedEffect(state.isEditorOpen) {
                                if (!state.isEditorOpen) screen = DesktopScreen.Catalog
                            }
                            TablatureEditorScreen(
                                uiState = state,
                                onIntent = tablatureViewModel::processIntent,
                                onNavigateBack = {
                                    tablatureViewModel.processIntent(TablatureUiIntent.CloseEditor)
                                }
                            )
                        }

                        is DesktopScreen.Backup -> {
                            val state by backupViewModel.uiState.collectAsState()
                            BackupHost(container, backupViewModel, state) { screen = DesktopScreen.Catalog }
                        }

                        is DesktopScreen.Import -> {
                            val state by importViewModel.uiState.collectAsState()
                            val stats by importViewModel.stats.collectAsState()
                            var importContentText by remember { mutableStateOf("") }
                            // Reconoce el contenido (pegado o leído de un archivo) y pide confirmación.
                            fun offerContent(text: String, fileName: String?) {
                                val detected = LaudateFileDetector.detect(text = text, fileName = fileName)
                                importViewModel.processIntent(
                                    ImportUiIntent.OfferContent(detected = detected, content = text)
                                )
                            }
                            // Un archivo elegido se sniffea por sus bytes antes de leerlo entero como
                            // texto: un binario (vídeo, imagen, PDF…) nunca debe decodificarse como
                            // ChordPro. Ver LaudateFileDetector.sniffMediaKind.
                            fun offerFile(file: File) {
                                val head = runCatching {
                                    file.inputStream().use { it.readNBytes(4096) }
                                }.getOrDefault(ByteArray(0))
                                val isZip = LaudateFileDetector.looksLikeZip(head)
                                val text = if (isZip || LaudateFileDetector.sniffMediaKind(head) != null) {
                                    null
                                } else {
                                    runCatching { LaudateFileDetector.stripBom(file.readText()) }.getOrNull()
                                }
                                val detected = LaudateFileDetector.detect(
                                    text = text,
                                    fileName = file.name,
                                    isZip = isZip,
                                    headBytes = head
                                )
                                importViewModel.processIntent(
                                    ImportUiIntent.OfferPendingFile(
                                        detected = detected,
                                        fileName = file.name,
                                        archiveUri = if (isZip) file.absolutePath else null
                                    )
                                )
                            }
                            ImportScreen(
                                uiState = state,
                                stats = stats,
                                onNavigateBack = { screen = DesktopScreen.Catalog },
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
                                onChooseContentFile = {
                                    scope.launch {
                                        DesktopFileDialogs.openFile("Importar contenido")?.let { file ->
                                            offerFile(file)
                                        }
                                    }
                                },
                                onImportBackupClick = {
                                    scope.launch {
                                        DesktopFileDialogs.openFile("Importar una biblioteca (.laudate)")?.let {
                                            backupViewModel.processIntent(BackupUiIntent.SyncFrom(it.absolutePath))
                                            screen = DesktopScreen.Backup
                                        }
                                    }
                                },
                                onConfirmIncomingFile = {
                                    importViewModel.processIntent(ImportUiIntent.DismissPendingFile)
                                    val file = container.incomingFile
                                    if (file != null) {
                                        scope.launch {
                                            container.incomingFile = null
                                            messages.show(withContext(Dispatchers.IO) { incomingHandler.import(file) })
                                        }
                                    }
                                },
                                onDismissIncomingFile = {
                                    importViewModel.processIntent(ImportUiIntent.DismissPendingFile)
                                    container.incomingFile = null
                                },
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
                                }
                            )
                        }

                        is DesktopScreen.Manual -> {
                            val state by manualViewModel.uiState.collectAsState()
                            ManualScreen(
                                uiState = state,
                                sideEffects = manualViewModel.sideEffect,
                                onIntent = manualViewModel::processIntent,
                                onNavigateBack = { screen = DesktopScreen.Catalog }
                            )
                        }
                    }
                }
            }

            if (showAbout) {
                AboutDialog(
                    onDismiss = { showAbout = false },
                    appVersionName = container.appVersionName
                )
            }

            // Gestión de categorías (paridad con el menú lateral de Android).
            if (showCategorySheet) {
                val catalogState by catalogViewModel.uiState.collectAsState()
                CategoryManagementSheet(
                    categories = catalogState.categories,
                    songCounts = catalogState.categorySongCounts,
                    onAddCategory = { newCat ->
                        scope.launch { container.categoryRepository.saveCategory(newCat) }
                    },
                    onEditCategory = { category, newName, newKind ->
                        scope.launch {
                            // 1) Renombra y propaga el cambio a canciones y repertorios.
                            container.updateCategoryUseCase(category.id, newName)
                            // 2) El tipo se actualiza aparte, con el nombre NUEVO.
                            if (category.kind != newKind) {
                                container.categoryRepository.updateCategory(
                                    category.copy(name = newName.trim(), kind = newKind)
                                )
                            }
                        }
                    },
                    onDeleteCategory = { catId ->
                        scope.launch { container.categoryRepository.deleteCategory(catId) }
                    },
                    onDismissRequest = { showCategorySheet = false }
                )
            }
            }
                    }
            }
        }
    }
}

/** Pantalla de copia de seguridad con sus diálogos de fichero de escritorio. */
@Composable
private fun BackupHost(
    container: DesktopContainer,
    viewModel: BackupViewModel,
    state: BackupUiState,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    BackupScreen(
        uiState = state,
        onNavigateBack = onBack,
        onExportClick = {
            scope.launch {
                // El nombre sale de la **misma** función que usa Android
                // ([canticoraBackupFileName]), no de un literal: antes el escritorio pedía
                // `laudate_copia.laudate`, con la extensión y la marca **antiguas**, mientras la app
                // ya generaba `canticora_copia_<fecha>.canticora`. De ahí que los ficheros de Windows
                // siguieran llamándose «laudate…».
                DesktopFileDialogs.saveFile(
                    "Guardar copia de seguridad",
                    canticoraBackupFileName(todayIsoDate())
                )?.let {
                    viewModel.processIntent(BackupUiIntent.ExportTo(it.absolutePath))
                }
            }
        },
        onSyncClick = {
            scope.launch {
                DesktopFileDialogs.openFile("Sincronizar con un archivo (.$CANTICORA_BACKUP_EXTENSION)")?.let {
                    viewModel.processIntent(BackupUiIntent.SyncFrom(it.absolutePath))
                }
            }
        },
        onRestoreClick = {
            scope.launch {
                DesktopFileDialogs.openFile("Restaurar copia (borra y reemplaza)")?.let {
                    viewModel.processIntent(BackupUiIntent.RequestRestore(it.absolutePath))
                }
            }
        },
        onShareClick = {
            scope.launch {
                DesktopFileDialogs.saveFile(
                    "Compartir copia de seguridad",
                    canticoraBackupFileName(todayIsoDate())
                )?.let {
                    viewModel.processIntent(BackupUiIntent.ExportTo(it.absolutePath, shareAfter = true))
                }
            }
        },
        onResolveConflict = { songId, policy ->
            viewModel.processIntent(BackupUiIntent.ResolveConflict(songId, policy))
        },
        onApplyPolicyToAll = { policy ->
            viewModel.processIntent(BackupUiIntent.ApplyPolicyToAll(policy))
        },
        onConfirmSync = { viewModel.processIntent(BackupUiIntent.ConfirmSync) },
        onCancelSync = { viewModel.processIntent(BackupUiIntent.CancelSync) },
        onConfirmRestore = { viewModel.processIntent(BackupUiIntent.ConfirmRestore) },
        onCancelRestore = { viewModel.processIntent(BackupUiIntent.CancelRestore) },
        onRestoreSafetyBackup = { viewModel.processIntent(BackupUiIntent.RestoreSafetyBackup) }
    )
}

/** Ids de las acciones del pie del panel (no son pantallas: abren hojas o enlaces). */
private const val ActionCategories = "action-categories"
private const val ActionSupport = "action-support"
private const val ActionAbout = "action-about"

/** Entradas del panel de navegación (con nombres; el panel las muestra y las agrupa). */
private val desktopNavEntries = listOf(
    NavEntry("catalog", "Canciones", Icons.Default.MusicNote),
    NavEntry("celebrations", "Misas y Eventos", Icons.Default.PlaylistPlay),
    NavEntry("rhythms", "Patrones rítmicos", Icons.Default.Settings),
    NavEntry("import", "Importar", Icons.Default.Upload),
    NavEntry("backup", "Copia de seguridad", Icons.Default.CloudSync)
)

/** Acciones al pie del panel (no navegan: abren hojas/enlaces). */
private val desktopNavActions = listOf(
    NavEntry(ActionCategories, "Gestión de categorías", Icons.Default.Settings),
    NavEntry("manual", "Manual de uso", Icons.Default.MenuBook),
    NavEntry(ActionSupport, "Apoyar", Icons.Default.VolunteerActivism),
    NavEntry(ActionAbout, "Acerca de Canticora", Icons.Default.Info)
)

/** Id de menú que corresponde a cada pantalla (para marcar la entrada activa). */
private fun desktopScreenId(screen: DesktopScreen): String = when (screen) {
    is DesktopScreen.Catalog -> "catalog"
    is DesktopScreen.CelebrationList, is DesktopScreen.CelebrationEditor, is DesktopScreen.LiveCelebration -> "celebrations"
    is DesktopScreen.RhythmPatterns, is DesktopScreen.RhythmPatternEditor -> "rhythms"
    is DesktopScreen.Backup -> "backup"
    is DesktopScreen.Import -> "import"
    is DesktopScreen.Manual -> "manual"
    is DesktopScreen.SongDetail, is DesktopScreen.SongEditor -> "catalog"
    is DesktopScreen.Tablatures, is DesktopScreen.TablatureEditor -> "catalog"
}

/** Pantalla destino de una entrada de menú (conserva el canto actual si aplica). */
private fun desktopScreenFor(id: String, current: DesktopScreen): DesktopScreen = when (id) {
    "catalog" -> when (current) {
        // Volver a «Canciones» desde el detalle/editor del canto conserva el canto abierto.
        is DesktopScreen.SongDetail -> current
        else -> DesktopScreen.Catalog
    }
    "celebrations" -> DesktopScreen.CelebrationList
    "rhythms" -> DesktopScreen.RhythmPatterns
    "backup" -> DesktopScreen.Backup
    "import" -> DesktopScreen.Import
    "manual" -> DesktopScreen.Manual
    else -> current
}

/** Nombre de la sección activa, para que el panel de ajustes diga dónde está el usuario. */
private fun desktopSectionTitleFor(screen: DesktopScreen): String = when (screen) {
    is DesktopScreen.Catalog, is DesktopScreen.SongDetail, is DesktopScreen.SongEditor,
    is DesktopScreen.Tablatures, is DesktopScreen.TablatureEditor -> "Canciones"
    is DesktopScreen.CelebrationList, is DesktopScreen.CelebrationEditor,
    is DesktopScreen.LiveCelebration -> "Misas y Eventos"
    is DesktopScreen.RhythmPatterns, is DesktopScreen.RhythmPatternEditor -> "Patrones rítmicos"
    is DesktopScreen.Import -> "Importar"
    is DesktopScreen.Backup -> "Copia de seguridad"
    is DesktopScreen.Manual -> "Manual de uso"
}

/**
 * Ajustes **de la herramienta activa** para el panel derecho. El panel no es el mismo en todas las
 * pantallas: cambia con la sección, y ofrece lo que de verdad se puede ajustar **donde está** el
 * usuario —orden y filtros del catálogo, tipo de repertorio, notación de los ritmos, instrumento y
 * compás de la tablatura, o compás, tonalidad, tempo y cejilla del canto—. Las pantallas sin
 * ajustes propios devuelven `null` y el panel solo muestra los comunes.
 */
@Composable
private fun toolSettingsFor(
    screen: DesktopScreen,
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
    is DesktopScreen.Catalog -> {
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

    is DesktopScreen.CelebrationList -> {
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

    is DesktopScreen.RhythmPatterns, is DesktopScreen.RhythmPatternEditor -> {
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

    is DesktopScreen.Tablatures, is DesktopScreen.TablatureEditor -> {
        val state by tablatureViewModel.uiState.collectAsState()
        val panel: @Composable () -> Unit = {
            TablatureToolSettings(
                uiState = state,
                onIntent = tablatureViewModel::processIntent
            )
        }
        panel
    }

    is DesktopScreen.SongEditor -> {
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
                    onExportAttachmentClick = null,
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

    is DesktopScreen.SongDetail -> {
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

/** Abre un fichero (adjunto) con la aplicación asociada del sistema operativo. */
private fun openExternally(path: String) {
    runCatching { java.awt.Desktop.getDesktop().open(java.io.File(path)) }
}

/** Abre un enlace web en el navegador predeterminado del sistema. */
private fun openUrlExternally(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
}

/**
 * Fecha de hoy en `AAAA-MM-DD`, para el nombre de la copia de seguridad.
 *
 * Se calcula aquí, en el borde de la aplicación, y se pasa a [canticoraBackupFileName]: así la
 * función del Dominio sigue siendo **pura** y comparte formato con Android.
 */
private fun todayIsoDate(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        .format(java.util.Date())
