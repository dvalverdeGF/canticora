package com.grafitto.laudate.ui.celebration

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grafitto.laudate.domain.export.CelebrationPdfSong
import com.grafitto.laudate.domain.model.celebration.Celebration
import com.grafitto.laudate.domain.model.celebration.CelebrationItem
import com.grafitto.laudate.domain.model.celebration.CelebrationType
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.CelebrationRepository
import com.grafitto.laudate.domain.repository.PreferencesRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.transfer.CANTICORA_EVENT_EXTENSION
import com.grafitto.laudate.domain.transfer.CelebrationBundleSerializer
import com.grafitto.laudate.domain.usecase.ImportCelebrationBundleUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CelebrationViewModel(
    private val celebrationRepository: CelebrationRepository,
    private val songRepository: SongRepository,
    private val categoryRepository: CategoryRepository,
    private val importCelebrationBundleUseCase: ImportCelebrationBundleUseCase? = null,
    /** Versión de la app que se anota en los repertorios exportados. */
    private val appVersion: String = "",
    /** Preferencias globales: recuerda si el PDF se exporta con acordes o solo con la letra. */
    private val preferencesRepository: PreferencesRepository? = null,
    /** Repositorio de tablaturas (ADR-017, *Feature T*): viajan en los repertorios compartidos. */
    private val tablatureRepository: TablatureRepository? = null,
    /** Adjuntos: se usan para marcar qué cantos tienen ficheros (icono del listado). */
    private val attachmentRepository: com.grafitto.laudate.domain.repository.AttachmentRepository? = null
) : ViewModel() {

    private val _listUiState = MutableStateFlow(CelebrationListUiState())
    val listUiState: StateFlow<CelebrationListUiState> = _listUiState.asStateFlow()

    private val _editorUiState = MutableStateFlow(CelebrationEditorUiState())
    val editorUiState: StateFlow<CelebrationEditorUiState> = _editorUiState.asStateFlow()

    private val _sideEffect = Channel<CelebrationEditorSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    init {
        loadCelebrations()
        observePdfPreference()
        observeContentBadges()
    }

    /** Observa qué cantos tienen tablatura o adjuntos, para los iconos del editor de misa. */
    private fun observeContentBadges() {
        val tabs = tablatureRepository?.observeSongIdsWithTablatures()
            ?: kotlinx.coroutines.flow.flowOf(emptySet())
        val atts = attachmentRepository?.observeSongIdsWithAttachments()
            ?: kotlinx.coroutines.flow.flowOf(emptySet())
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(tabs, atts) { withTab, withAtt -> withTab to withAtt }
                .collect { (withTab, withAtt) ->
                    _editorUiState.update {
                        it.copy(songIdsWithTablatures = withTab, songIdsWithAttachments = withAtt)
                    }
                }
        }
    }

    /** La preferencia «PDF con acordes» es global y se recuerda entre sesiones. */
    private fun observePdfPreference() {
        val prefs = preferencesRepository ?: return
        viewModelScope.launch {
            prefs.songDisplayMode.collect { mode ->
                _listUiState.update { it.copy(pdfDisplayMode = mode) }
                _editorUiState.update { it.copy(pdfDisplayMode = mode) }
            }
        }
    }

    fun processListIntent(intent: CelebrationListUiIntent) {
        when (intent) {
            is CelebrationListUiIntent.LoadCelebrations -> loadCelebrations()
            is CelebrationListUiIntent.FilterByType -> _listUiState.update { it.copy(selectedTypeFilter = intent.type) }
            is CelebrationListUiIntent.DeleteCelebration -> deleteCelebration(intent.id)
            is CelebrationListUiIntent.UndoDeleteCelebration -> undoDeleteCelebration()
            is CelebrationListUiIntent.DismissPendingDelete -> _listUiState.update { it.copy(pendingDelete = null) }
            is CelebrationListUiIntent.ShareCelebration -> shareCelebration(intent.id)
            is CelebrationListUiIntent.PrintCelebration -> printCelebration(intent.id, intent.mode)
            is CelebrationListUiIntent.ImportCelebrationFile -> importCelebrationFile(intent.content)
        }
    }

    fun processEditorIntent(intent: CelebrationEditorUiIntent) {
        when (intent) {
            is CelebrationEditorUiIntent.LoadForEdit -> loadForEdit(intent.celebrationId)
            is CelebrationEditorUiIntent.UpdateName -> _editorUiState.update { it.copy(name = intent.name) }
            is CelebrationEditorUiIntent.UpdateCelebrationType -> updateCelebrationType(intent.type)
            is CelebrationEditorUiIntent.UpdateDescription -> _editorUiState.update { it.copy(description = intent.description) }
            is CelebrationEditorUiIntent.UpdateLocation -> _editorUiState.update { it.copy(location = intent.location) }
            is CelebrationEditorUiIntent.AddSongItem -> {
                _editorUiState.update { current ->
                    val normalizedMoment = intent.liturgicalMoment
                        .takeIf { it.isNotBlank() && !it.equals("Sin asignar", ignoreCase = true) }
                        .orEmpty()
                    val newItem = CelebrationItem(
                        id = newId(),
                        celebrationId = current.celebrationId ?: "",
                        songId = intent.songId,
                        liturgicalMoment = normalizedMoment,
                        displayOrder = current.items.size,
                        overrideKey = intent.overrideKey
                    )
                    current.copy(items = current.items + newItem)
                }
            }
            is CelebrationEditorUiIntent.RemoveItem -> {
                _editorUiState.update { current ->
                    current.copy(items = current.items.filterNot { it.id == intent.itemId })
                }
            }
            is CelebrationEditorUiIntent.MoveItem -> {
                _editorUiState.update { current ->
                    val mutable = current.items.toMutableList()
                    if (intent.fromIndex in mutable.indices && intent.toIndex in mutable.indices) {
                        val item = mutable.removeAt(intent.fromIndex)
                        mutable.add(intent.toIndex, item)
                    }
                    current.copy(items = mutable)
                }
            }
            is CelebrationEditorUiIntent.UpdateItemOverrideKey -> {
                _editorUiState.update { current ->
                    val updated = current.items.map { item ->
                        if (item.id == intent.itemId) item.copy(overrideKey = intent.key) else item
                    }
                    current.copy(items = updated)
                }
            }
            is CelebrationEditorUiIntent.UpdateItemMoment -> {
                _editorUiState.update { current ->
                    val normalized = intent.moment
                        .takeIf { it.isNotBlank() && !it.equals("Sin asignar", ignoreCase = true) }
                        .orEmpty()
                    val updated = current.items.map { item ->
                        if (item.id == intent.itemId) item.copy(liturgicalMoment = normalized) else item
                    }
                    current.copy(items = updated)
                }
            }
            is CelebrationEditorUiIntent.SaveCelebration -> saveCelebration()
            is CelebrationEditorUiIntent.ShareCelebration -> shareCurrentCelebration()
            is CelebrationEditorUiIntent.PrintCelebration -> printCurrentCelebration(intent.mode)
            is CelebrationEditorUiIntent.DeleteCelebration -> deleteCurrentCelebration()
            is CelebrationEditorUiIntent.ClearPendingAction -> Unit
        }
    }

    // ── Compartir / importar / imprimir repertorios ──────────────────────────────

    /**
     * Comparte el repertorio como archivo `.canticora` (ChordPro + estructura) por la hoja nativa.
     * Se leen los cantos para volcar su texto completo en el archivo.
     */
    private fun shareCelebration(celebrationId: String) {
        viewModelScope.launch {
            val celebration = celebrationRepository.getCelebrationById(celebrationId).firstOrNull()
            if (celebration == null) {
                _sideEffect.send(CelebrationEditorSideEffect.ShowMessage("No se encontró el repertorio"))
                return@launch
            }
            if (celebration.items.isEmpty()) {
                _sideEffect.send(CelebrationEditorSideEffect.ShowMessage("El repertorio no tiene cantos que compartir"))
                return@launch
            }
            val songsById = resolveSongsById(celebration)
            val bundle = CelebrationBundleSerializer.toBundle(
                celebration, songsById, appVersion, loadTablaturesBySongId(songsById.keys)
            )
            _sideEffect.send(
                CelebrationEditorSideEffect.ShareCelebrationFile(
                    name = celebration.name,
                    fileName = "${fileSafeName(celebration.name)}.$CANTICORA_EVENT_EXTENSION",
                    content = CelebrationBundleSerializer.serialize(bundle)
                )
            )
        }
    }

    /** Prepara el PDF del repertorio completo (un canto por página) y pide el destino (SAF). */
    private fun printCelebration(celebrationId: String, mode: com.grafitto.laudate.domain.model.song.SongDisplayMode) {
        viewModelScope.launch {
            preferencesRepository?.setSongDisplayMode(mode)
            val celebration = celebrationRepository.getCelebrationById(celebrationId).firstOrNull()
            if (celebration == null) {
                _sideEffect.send(CelebrationEditorSideEffect.ShowMessage("No se encontró el repertorio"))
                return@launch
            }
            if (celebration.items.isEmpty()) {
                _sideEffect.send(CelebrationEditorSideEffect.ShowMessage("El repertorio no tiene cantos que imprimir"))
                return@launch
            }
            val songsById = resolveSongsById(celebration)
            val pdfSongs = celebration.items.mapNotNull { item ->
                songsById[item.songId]?.let { song ->
                    CelebrationPdfSong(
                        song = song,
                        moment = item.liturgicalMoment,
                        overrideKey = item.overrideKey
                    )
                }
            }
            _sideEffect.send(
                CelebrationEditorSideEffect.ExportCelebrationPdf(
                    celebration = celebration,
                    songs = pdfSongs,
                    fileName = "${fileSafeName(celebration.name)}.pdf",
                    mode = mode
                )
            )
        }
    }

    /** Comparte/imprime el repertorio que está abierto en el editor, guardándolo antes si hace falta. */
    private fun shareCurrentCelebration() {
        val state = _editorUiState.value
        if (state.items.isEmpty()) {
            viewModelScope.launch {
                _sideEffect.send(CelebrationEditorSideEffect.ShowMessage("Añade algún canto antes de compartir"))
            }
            return
        }
        viewModelScope.launch {
            val bundle = CelebrationBundleSerializer.toBundle(
                celebration = state.toCelebration(),
                songsById = resolveEditorSongsById(state),
                appVersion = appVersion,
                tablaturesBySongId = loadTablaturesBySongId(resolveEditorSongsById(state).keys)
            )
            _sideEffect.send(
                CelebrationEditorSideEffect.ShareCelebrationFile(
                    name = state.name,
                    fileName = "${fileSafeName(state.name)}.$CANTICORA_EVENT_EXTENSION",
                    content = CelebrationBundleSerializer.serialize(bundle)
                )
            )
        }
    }

    private fun printCurrentCelebration(mode: com.grafitto.laudate.domain.model.song.SongDisplayMode) {
        val state = _editorUiState.value
        if (state.items.isEmpty()) {
            viewModelScope.launch {
                _sideEffect.send(CelebrationEditorSideEffect.ShowMessage("Añade algún canto antes de imprimir"))
            }
            return
        }
        viewModelScope.launch {
            preferencesRepository?.setSongDisplayMode(mode)
            val songsById = resolveEditorSongsById(state)
            val pdfSongs = state.items.mapNotNull { item ->
                songsById[item.songId]?.let { song ->
                    CelebrationPdfSong(song = song, moment = item.liturgicalMoment, overrideKey = item.overrideKey)
                }
            }
            _sideEffect.send(
                CelebrationEditorSideEffect.ExportCelebrationPdf(
                    celebration = state.toCelebration(),
                    songs = pdfSongs,
                    fileName = "${fileSafeName(state.name)}.pdf",
                    mode = mode
                )
            )
        }
    }

    /** Elimina el repertorio abierto en el editor y vuelve al listado. */
    private fun deleteCurrentCelebration() {
        val celebrationId = _editorUiState.value.celebrationId ?: return
        viewModelScope.launch {
            celebrationRepository.deleteCelebration(celebrationId)
            _sideEffect.send(CelebrationEditorSideEffect.NavigateBackOnDeleted)
        }
    }

    /**
     * Elimina un repertorio del listado conservando una copia completa (con sus cantos) para
     * ofrecer «Deshacer» desde el snackbar.
     */
    private fun deleteCelebration(celebrationId: String) {
        viewModelScope.launch {
            // Se lee antes de borrar para poder restaurarlo idéntico (mismos ids y orden).
            val snapshot = celebrationRepository.getCelebrationById(celebrationId).firstOrNull()
            celebrationRepository.deleteCelebration(celebrationId)
            _listUiState.update { it.copy(pendingDelete = snapshot) }
        }
    }

    /** Restaura el repertorio eliminado pendiente (deshacer). */
    private fun undoDeleteCelebration() {
        val pending = _listUiState.value.pendingDelete ?: return
        viewModelScope.launch {
            celebrationRepository.restoreCelebration(pending)
            _listUiState.update { it.copy(pendingDelete = null) }
        }
    }

    /**
     * Importa un repertorio recibido: crea los cantos que falten y **reutiliza sin modificar** los
     * que ya existan, y añade siempre un repertorio nuevo. Nunca sobrescribe canciones.
     */
    private fun importCelebrationFile(content: String) {
        val useCase = importCelebrationBundleUseCase
        if (useCase == null) {
            viewModelScope.launch {
                _sideEffect.send(CelebrationEditorSideEffect.ShowMessage("La importación no está disponible"))
            }
            return
        }
        viewModelScope.launch {
            _listUiState.update { it.copy(isLoading = true) }
            val result = runCatching {
                useCase(CelebrationBundleSerializer.deserialize(content))
            }
            _listUiState.update { it.copy(isLoading = false) }
            result.fold(
                onSuccess = { imported ->
                    _sideEffect.send(
                        CelebrationEditorSideEffect.ShowMessage(
                            "«${imported.celebrationName}» importado: " +
                                "${imported.songsCreated} cantos nuevos y ${imported.songsReused} ya existentes"
                        )
                    )
                },
                onFailure = { error ->
                    _sideEffect.send(
                        CelebrationEditorSideEffect.ShowMessage(
                            "No se pudo importar: ${error.message ?: "archivo no válido"}"
                        )
                    )
                }
            )
        }
    }

    /** Resuelve los cantos del repertorio, **con su letra**, desde el catálogo. */
    private suspend fun resolveSongsById(celebration: Celebration): Map<String, Song> =
        songRepository.getSongsByIds(celebration.items.map { it.songId }).associateBy { it.id }

    /**
     * Tablaturas de los cantos indicados (ADR-017, *Feature T*), indexadas por id de canto, para
     * que viajen dentro del repertorio compartido. Vacío si no hay repositorio de tablaturas.
     */
    private suspend fun loadTablaturesBySongId(
        songIds: Set<String>
    ): Map<String, List<com.grafitto.laudate.domain.model.tablature.Tablature>> {
        val repo = tablatureRepository ?: return emptyMap()
        return songIds.associateWith { repo.getTablaturesForSong(it) }.filterValues { it.isNotEmpty() }
    }

    /**
     * Resuelve los cantos del editor a partir de los que ya tiene cargados.
     *
     * El editor guarda `allSongs` con la letra incluida; si por cualquier motivo un canto no
     * estuviera en esa lista, se recupera del repositorio para que el PDF y el archivo compartido
     * nunca salgan en blanco.
     */
    private suspend fun resolveEditorSongsById(state: CelebrationEditorUiState): Map<String, Song> {
        val wanted = state.items.map { it.songId }.distinct()
        val byId = state.allSongs.filter { it.id in wanted }.associateBy { it.id }
        val missing = wanted.filterNot { byId.containsKey(it) }
        if (missing.isEmpty()) return byId
        return byId + songRepository.getSongsByIds(missing).associateBy { it.id }
    }

    /** Convierte el estado del editor a un modelo de celebración listo para exportar. */
    private fun CelebrationEditorUiState.toCelebration(): Celebration = Celebration(
        id = celebrationId ?: newId(),
        name = name.trim(),
        celebrationType = celebrationType,
        description = description.trim().ifEmpty { null },
        location = location.trim().ifEmpty { null },
        dateTimestamp = dateTimestamp,
        items = items
    )

    /** Nombre de archivo seguro (sin caracteres prohibidos). */
    private fun fileSafeName(raw: String): String = raw
        .lowercase()
        .replace(Regex("[^a-z0-9áéíóúñü ]"), "")
        .trim()
        .replace(Regex("\\s+"), "_")
        .ifBlank { "repertorio" }

    private fun loadCelebrations() {
        viewModelScope.launch {
            celebrationRepository.getAllCelebrations().collect { list ->
                _listUiState.update { it.copy(isLoading = false, celebrations = list) }
            }
        }
    }

    private fun loadForEdit(celebrationId: String?) {
        viewModelScope.launch {
            // El editor necesita la letra para previsualizar los cantos y para compartir o imprimir
            // el repertorio, de modo que se carga el catálogo con sus secciones.
            val allSongs = songRepository.getAllSongsWithLyrics().firstOrNull() ?: emptyList()
            val categories = categoryRepository.getAllCategories().firstOrNull() ?: emptyList()

            if (celebrationId != null) {
                val existing = celebrationRepository.getCelebrationById(celebrationId).firstOrNull()
                if (existing != null) {
                    _editorUiState.update {
                        it.copy(
                            celebrationId = existing.id,
                            name = existing.name,
                            celebrationType = existing.celebrationType,
                            description = existing.description.orEmpty(),
                            location = existing.location.orEmpty(),
                            dateTimestamp = existing.dateTimestamp,
                            items = existing.items,
                            allSongs = allSongs,
                            availableCategories = categories
                        )
                    }
                    return@launch
                }
            }
            val defaultType = CelebrationType.MISA
            _editorUiState.update {
                it.copy(
                    celebrationId = null,
                    name = defaultType.defaultName,
                    celebrationType = defaultType.displayName,
                    description = "",
                    location = "",
                    items = emptyList(),
                    allSongs = allSongs,
                    availableCategories = categories
                )
            }
        }
    }

    /**
     * Cambia el tipo de celebración. Si el nombre actual era el nombre por defecto del
     * tipo anterior (o estaba vacío), se sustituye por el nombre por defecto del nuevo tipo,
     * evitando que un evento conserve "Misa Dominical".
     */
    private fun updateCelebrationType(typeName: String) {
        val newType = CelebrationType.fromDisplayName(typeName)
        _editorUiState.update { current ->
            val previousType = CelebrationType.fromStoredType(current.celebrationType)
            val wasUntouchedDefault =
                current.name.isBlank() || current.name.trim() == previousType.defaultName
            val newName = if (wasUntouchedDefault) newType.defaultName else current.name
            current.copy(celebrationType = newType.displayName, name = newName)
        }
    }

    private fun saveCelebration() {
        val state = _editorUiState.value
        if (state.isSaving) return
        if (state.name.isBlank()) {
            _editorUiState.update { it.copy(errorMessage = "El nombre de la celebración es obligatorio") }
            return
        }

        viewModelScope.launch {
            _editorUiState.update { it.copy(isSaving = true) }
            val id = state.celebrationId ?: newId()

            val celebration = Celebration(
                id = id,
                name = state.name.trim(),
                celebrationType = state.celebrationType,
                description = state.description.trim().ifEmpty { null },
                location = state.location.trim().ifEmpty { null },
                dateTimestamp = state.dateTimestamp,
                items = state.items.mapIndexed { idx, it -> it.copy(celebrationId = id, displayOrder = idx) }
            )

            celebrationRepository.saveCelebration(celebration)
            _editorUiState.update { it.copy(isSaving = false) }
            _sideEffect.send(CelebrationEditorSideEffect.ShowMessage("Guardado correctamente"))
            _sideEffect.send(CelebrationEditorSideEffect.NavigateBackOnSaved)
        }
    }
}
