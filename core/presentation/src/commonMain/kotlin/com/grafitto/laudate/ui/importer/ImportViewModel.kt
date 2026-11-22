package com.grafitto.laudate.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.CelebrationRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.transfer.CelebrationBundle
import com.grafitto.laudate.domain.transfer.CelebrationBundleSerializer
import com.grafitto.laudate.domain.transfer.DetectedLaudateFile
import com.grafitto.laudate.domain.transfer.ImportConflict
import com.grafitto.laudate.domain.transfer.ImportConflictPolicy
import com.grafitto.laudate.domain.transfer.LaudateFileKind
import com.grafitto.laudate.domain.usecase.ChordProFile
import com.grafitto.laudate.domain.usecase.ImportCatalogBundleUseCase
import com.grafitto.laudate.domain.usecase.ImportCelebrationBundleUseCase
import com.grafitto.laudate.domain.usecase.ImportChordProSongsUseCase
import com.grafitto.laudate.domain.usecase.ImportRhythmPatternsUseCase
import com.grafitto.laudate.domain.usecase.ImportSongPackageUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel MVI de la pantalla **Importar**.
 *
 * Orquesta las cuatro vías de entrada de datos de Canticora y publica un resumen de cada una:
 *  1. **Catálogo/paquete** (`.json` con esquema `BackupPayload`).
 *  2. **Repertorio de misa** (`.canticora`).
 *  3. **Canciones sueltas** en ChordPro (uno o varios archivos).
 *  4. **Copia de seguridad** (`.zip`): se delega en la pantalla de Copia de seguridad, que sí
 *     dispone del `Uri` con permiso SAF.
 *
 * Además, cuando la app recibe un archivo **desde fuera** (WhatsApp, correo, «Compartir»…), esta
 * pantalla lo **reconoce** y ofrece importarlo directamente, sin que el usuario tenga que elegir la
 * vía ni buscar el archivo.
 *
 * Los `Uri` y el contenido de los ficheros llegan ya resueltos desde la UI, por lo que este
 * ViewModel no toca `ContentResolver` ni ninguna API de Android.
 */
class ImportViewModel(
    private val songRepository: SongRepository,
    private val celebrationRepository: CelebrationRepository,
    private val categoryRepository: CategoryRepository,
    private val importCatalogBundleUseCase: ImportCatalogBundleUseCase,
    private val importCelebrationBundleUseCase: ImportCelebrationBundleUseCase,
    private val importChordProSongsUseCase: ImportChordProSongsUseCase,
    private val importRhythmPatternsUseCase: ImportRhythmPatternsUseCase,
    /** Importa un canto suelto con sus adjuntos (`canticora.song`, ADR-017). */
    private val importSongPackageUseCase: ImportSongPackageUseCase? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _stats = MutableStateFlow(ImportLibraryStats())
    val stats: StateFlow<ImportLibraryStats> = _stats.asStateFlow()

    private val _sideEffect = Channel<ImportSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    init {
        refreshStats()
    }

    fun processIntent(intent: ImportUiIntent) {
        when (intent) {
            is ImportUiIntent.ImportCatalog -> importCatalog(intent.json)
            is ImportUiIntent.ImportCelebration -> inspectCelebrationThen(intent.content)
            is ImportUiIntent.ImportRhythms -> inspectRhythmsThen(intent.content)
            is ImportUiIntent.ImportChordProFiles -> inspectChordProThen(intent.files)
            is ImportUiIntent.ResolveImportConflict -> resolveConflict(intent.key, intent.policy)
            is ImportUiIntent.ApplyImportPolicyToAll -> applyPolicyToAll(intent.policy)
            ImportUiIntent.ConfirmImportConflicts -> confirmConflicts()
            ImportUiIntent.CancelImportConflicts -> {
                pendingAction = null
                _uiState.update { it.copy(pendingConflicts = null) }
            }
            is ImportUiIntent.OfferPendingFile -> offerContent(
                detected = intent.detected,
                preview = intent.detected.rawText,
                fileName = intent.fileName,
                archiveUri = intent.archiveUri,
                isIncomingFile = true
            )
            is ImportUiIntent.OfferContent -> offerContent(
                detected = intent.detected,
                preview = intent.content,
                fileName = intent.detected.title,
                isIncomingFile = false
            )
            ImportUiIntent.ConfirmPendingFile -> confirmPendingFile()
            ImportUiIntent.ConfirmPendingContent -> confirmPendingContent()
            ImportUiIntent.DismissPendingFile -> dismissPendingContent()
            ImportUiIntent.DismissPendingContent -> dismissPendingContent()
            ImportUiIntent.DismissResult -> _uiState.update { it.copy(lastResult = null) }
        }
    }

    // ── Resolución de conflictos (lo que llega ya existe con cambios) ─────────────

    /** Acción pendiente que se ejecutará al confirmar el diálogo de conflictos. */
    private var pendingAction: (suspend (Map<String, ImportConflictPolicy>) -> Unit)? = null

    private fun resolveConflict(key: String, policy: ImportConflictPolicy) {
        _uiState.update { state ->
            val pending = state.pendingConflicts ?: return@update state
            state.copy(pendingConflicts = pending.copy(decisions = pending.decisions + (key to policy)))
        }
    }

    private fun applyPolicyToAll(policy: ImportConflictPolicy) {
        _uiState.update { state ->
            val pending = state.pendingConflicts ?: return@update state
            state.copy(
                pendingConflicts = pending.copy(
                    decisions = pending.conflicts.associate { it.key to policy }
                )
            )
        }
    }

    private fun confirmConflicts() {
        val decisions = _uiState.value.pendingConflicts?.decisions.orEmpty()
        val action = pendingAction
        pendingAction = null
        _uiState.update { it.copy(pendingConflicts = null) }
        action?.let { run -> viewModelScope.launch { run(decisions) } }
    }

    /**
     * Muestra el diálogo de conflictos si hay coincidencias con cambios; si no, ejecuta [perform]
     * directamente (por defecto el usuario no tiene que decidir nada).
     */
    private fun offerConflicts(
        title: String,
        conflicts: List<ImportConflict>,
        perform: suspend (Map<String, ImportConflictPolicy>) -> Unit
    ) {
        if (conflicts.isEmpty()) {
            viewModelScope.launch { perform(emptyMap()) }
        } else {
            pendingAction = perform
            _uiState.update {
                it.copy(isBusy = false, pendingConflicts = PendingImportConflicts(title, conflicts))
            }
        }
    }

    // ── Inspección previa + importación ──────────────────────────────────────────

    private fun inspectRhythmsThen(content: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, lastResult = null) }
            val preview = runCatching { importRhythmPatternsUseCase.inspect(content) }.getOrNull()
            when {
                preview == null -> _uiState.update {
                    it.copy(isBusy = false, errorMessage = "El texto no contiene un ritmo reconocible")
                }
                preview.total == 0 -> _uiState.update {
                    it.copy(isBusy = false, errorMessage = "El archivo no contiene patrones rítmicos")
                }
                else -> offerConflicts("Ritmos", preview.conflicts) { policies ->
                    importRhythms(content, policies)
                }
            }
        }
    }

    private fun inspectCelebrationThen(content: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, lastResult = null) }
            val bundle = runCatching { CelebrationBundleSerializer.deserialize(content) }.getOrNull()
            if (bundle == null) {
                _uiState.update {
                    it.copy(isBusy = false, errorMessage = "No se pudo importar el repertorio: formato no reconocido")
                }
                return@launch
            }
            val preview = runCatching { importCelebrationBundleUseCase.inspect(bundle) }.getOrNull()
            if (preview == null || !preview.hasConflicts) {
                importCelebration(bundle, emptyMap())
            } else {
                offerConflicts("Repertorio «${preview.celebrationName}»", preview.conflicts) { policies ->
                    importCelebration(bundle, policies)
                }
            }
        }
    }

    private fun inspectChordProThen(inputs: List<ChordProFileInput>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, lastResult = null) }
            val files = inputs.map { ChordProFile(content = it.content, fileName = it.fileName) }
            val preview = runCatching { importChordProSongsUseCase.inspect(files) }.getOrNull()
            if (preview == null || !preview.hasConflicts) {
                importChordPro(files, emptyMap())
            } else {
                offerConflicts("Canciones", preview.conflicts) { policies ->
                    importChordPro(files, policies)
                }
            }
        }
    }

    /**
     * Muestra el diálogo de confirmación para un contenido ya analizado.
     *
     * Unifica las dos entradas: el archivo recibido desde fuera ([ImportUiIntent.OfferPendingFile],
     * que además trae un `Uri` de backup) y lo que la UI ha **reconocido al leer un archivo o al
     * pegar texto** ([ImportUiIntent.OfferContent]). En ambos casos se anuncia qué es y se espera
     * una única confirmación; el texto a importar viaja en [ImportUiState.pendingContentPreview].
     */
    private fun offerContent(
        detected: DetectedLaudateFile,
        preview: String?,
        fileName: String?,
        archiveUri: String? = null,
        isIncomingFile: Boolean = false
    ) {
        _uiState.update {
            it.copy(
                pendingFile = detected,
                pendingFileName = fileName,
                pendingArchiveUri = archiveUri,
                pendingContentPreview = preview,
                pendingIsIncomingFile = isIncomingFile
            )
        }
    }

    /** Descarta el contenido pendiente de confirmar (no se escribe nada). */
    private fun dismissPendingContent() {
        _uiState.update {
            it.copy(
                pendingFile = null,
                pendingFileName = null,
                pendingArchiveUri = null,
                pendingContentPreview = null,
                pendingIsIncomingFile = false
            )
        }
    }

    /**
     * Importa el archivo recibido según el tipo que se haya detectado.
     *
     * Así el usuario solo tiene que confirmar: la vía correcta se elige sola. Un cancionero entra
     * por el importador de catálogo, un repertorio por el de repertorios y una canción suelta por
     * el de ChordPro; una copia de seguridad se delega en su pantalla, que necesita el `Uri`.
     */
    private fun confirmPendingFile() {
        val detected = _uiState.value.pendingFile ?: return
        val text = _uiState.value.pendingContentPreview ?: detected.rawText
        val archiveUri = _uiState.value.pendingArchiveUri
        dismissPendingContent()

        when (detected.kind) {
            LaudateFileKind.CATALOG -> text?.let { importCatalog(it) }
            LaudateFileKind.CELEBRATION -> text?.let { inspectCelebrationThen(it) }
            LaudateFileKind.RHYTHMS -> text?.let { inspectRhythmsThen(it) }
            LaudateFileKind.CHORDPRO -> text?.let {
                inspectChordProThen(listOf(ChordProFileInput(content = it, fileName = detected.title)))
            }
            LaudateFileKind.SONG -> {
                val uri = archiveUri
                if (uri == null) {
                    _uiState.update { it.copy(errorMessage = "No se pudo leer el paquete del canto") }
                } else {
                    importSongPackage(uri)
                }
            }
            LaudateFileKind.BACKUP -> viewModelScope.launch {
                // La restauración la ejecuta la pantalla de Copia de seguridad (tiene el permiso SAF),
                // así que se le pasa el Uri del archivo recibido.
                if (archiveUri != null) {
                    _sideEffect.send(ImportSideEffect.RestoreBackup(archiveUri))
                } else {
                    _sideEffect.send(ImportSideEffect.RequestBackupFile)
                }
            }
            LaudateFileKind.MEDIA -> _uiState.update {
                // No debería llegar aquí (el diálogo no ofrece «Importar» para MEDIA), pero por si
                // acaso se confirma desde otra vía, nunca se crea nada con un binario.
                val description = detected.mediaDescription ?: "archivo"
                it.copy(errorMessage = "No se puede importar como canción: es un archivo de tipo «$description»")
            }
            LaudateFileKind.UNKNOWN -> _uiState.update {
                it.copy(errorMessage = "No se ha reconocido el contenido del archivo")
            }
        }
    }

    /** Confirma la importación del contenido reconocido por la UI (pegado o leído de un archivo). */
    private fun confirmPendingContent() {
        val detected = _uiState.value.pendingFile ?: return
        val content = _uiState.value.pendingContentPreview ?: detected.rawText
        // Los ZIP (copia de seguridad y paquete de canto) no viajan en texto: su lectura la hace la
        // vía de archivo, no la de contenido.
        val isArchive = detected.kind == LaudateFileKind.BACKUP || detected.kind == LaudateFileKind.SONG
        if (!isArchive && content.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "No hay contenido que importar") }
            return
        }
        confirmPendingFile()
    }

    /** Abre el selector de copia de seguridad; la restauración real la hace la pantalla de Backup. */
    fun requestBackupImport() {
        viewModelScope.launch { _sideEffect.send(ImportSideEffect.RequestBackupFile) }
    }

    private fun importCatalog(json: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, lastResult = null) }
            runCatching { importCatalogBundleUseCase(json) }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            lastResult = buildString {
                                append("Canciones importadas: ${result.songsImported}")
                                if (result.categoriesCreated > 0) {
                                    append(" · ${result.categoriesCreated} categorías nuevas")
                                }
                            }
                        )
                    }
                    refreshStats()
                    _sideEffect.send(ImportSideEffect.CatalogChanged)
                    _sideEffect.send(ImportSideEffect.ShowMessage("Canciones importadas correctamente"))
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = "No se pudo importar el catálogo: ${error.message ?: "formato no reconocido"}"
                        )
                    }
                }
        }
    }

    private suspend fun importRhythms(content: String, policies: Map<String, ImportConflictPolicy>) {
        runCatching {
            importRhythmPatternsUseCase.import(content, policies)
        }
            .onSuccess { result ->
                // `null` = el texto no contenía un ritmo reconocible.
                if (result == null) {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = "El texto no contiene un ritmo reconocible"
                        )
                    }
                    return@onSuccess
                }
                val empty = result.created == 0 && result.skipped == 0 && result.overwritten == 0
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        lastResult = if (empty) null else "Ritmos importados: ${result.summary}",
                        errorMessage = if (empty) "El archivo no contiene patrones rítmicos" else null
                    )
                }
                _sideEffect.send(ImportSideEffect.ShowMessage("Importación de ritmos finalizada"))
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = "No se pudieron importar los ritmos: " +
                            "${error.message ?: "formato no reconocido"}"
                    )
                }
            }
    }

    private suspend fun importCelebration(
        bundle: CelebrationBundle,
        policies: Map<String, ImportConflictPolicy>
    ) {
        runCatching { importCelebrationBundleUseCase(bundle, policies) }
            .onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        lastResult = "Repertorio «${result.celebrationName}» importado: " +
                            "${result.songCount} cantos " +
                            "(${result.songsCreated} nuevos, ${result.songsReused} ya en tu biblioteca" +
                            if (result.songsOverwritten > 0) ", ${result.songsOverwritten} actualizados)" else ")"
                    )
                }
                refreshStats()
                _sideEffect.send(ImportSideEffect.CatalogChanged)
                _sideEffect.send(ImportSideEffect.ShowMessage("Repertorio importado correctamente"))
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = "No se pudo importar el repertorio: ${error.message ?: "formato no reconocido"}"
                    )
                }
            }
    }

    /**
     * Importa un **canto suelto con sus adjuntos** (`canticora.song`). Se analiza primero para
     * saber si ya existe con cambios y, si es así, se pregunta antes de sobrescribir.
     */
    private fun importSongPackage(archiveUri: String) {
        val useCase = importSongPackageUseCase
        if (useCase == null) {
            _uiState.update { it.copy(errorMessage = "La importación de cantos no está disponible") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, errorMessage = null, lastResult = null) }
            val preview = runCatching { useCase.inspect(archiveUri) }.getOrNull()
            if (preview == null) {
                _uiState.update {
                    it.copy(isBusy = false, errorMessage = "No se pudo leer el canto: formato no reconocido")
                }
                return@launch
            }
            val conflict = preview.conflict
            if (conflict == null) {
                performSongImport(archiveUri, ImportConflictPolicy.OVERWRITE)
            } else {
                offerConflicts("Canto «${preview.title}»", listOf(conflict)) { policies ->
                    performSongImport(archiveUri, policies[conflict.key] ?: ImportConflictPolicy.OVERWRITE)
                }
            }
        }
    }

    /** Ejecuta la importación del paquete ya analizado y publica el resultado. */
    private suspend fun performSongImport(archiveUri: String, policy: ImportConflictPolicy) {
        val useCase = importSongPackageUseCase ?: return
        runCatching { useCase(archiveUri, policy) }
            .onSuccess { result ->
                val details = buildList {
                    add(
                        when {
                            result.identical -> "ya estaba en tu biblioteca"
                            result.created -> "añadido"
                            else -> "actualizado"
                        }
                    )
                    if (result.attachmentsImported > 0) add("${result.attachmentsImported} adjuntos")
                    if (result.categoriesCreated > 0) add("${result.categoriesCreated} categorías nuevas")
                }
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        lastResult = "«${result.title}»: ${details.joinToString(" · ")}"
                    )
                }
                refreshStats()
                _sideEffect.send(ImportSideEffect.CatalogChanged)
                _sideEffect.send(ImportSideEffect.ShowMessage("Canto importado correctamente"))
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = "No se pudo importar el canto: ${error.message ?: "formato no reconocido"}"
                    )
                }
            }
    }

    private suspend fun importChordPro(
        files: List<ChordProFile>,
        policies: Map<String, ImportConflictPolicy>
    ) {        runCatching { importChordProSongsUseCase(files, policies) }
            .onSuccess { result ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        lastResult = buildString {
                            append("ChordPro importado: ${result.total} canciones")
                            val details = buildList {
                                add("${result.songsCreated} nuevas")
                                if (result.songsReused > 0) add("${result.songsReused} ya existían")
                                if (result.songsOverwritten > 0) add("${result.songsOverwritten} actualizadas")
                            }
                            append(" (${details.joinToString(", ")})")
                            if (result.categoriesCreated > 0) {
                                append(" · ${result.categoriesCreated} categorías nuevas")
                            }
                        }
                    )
                }
                refreshStats()
                _sideEffect.send(ImportSideEffect.CatalogChanged)
                _sideEffect.send(ImportSideEffect.ShowMessage("Canciones importadas correctamente"))
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = "No se pudieron importar las canciones: ${error.message ?: "formato no reconocido"}"
                    )
                }
            }
    }

    private fun refreshStats() {
        viewModelScope.launch {
            val songs = songRepository.getSongCount()
            val celebrations = celebrationRepository.getAllCelebrations().first().size
            val categories = categoryRepository.getAllCategoriesSnapshot().size
            _stats.update {
                it.copy(songCount = songs, celebrationCount = celebrations, categoryCount = categories)
            }
        }
    }
}
