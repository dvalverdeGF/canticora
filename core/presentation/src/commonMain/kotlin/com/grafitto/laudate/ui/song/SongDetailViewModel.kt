package com.grafitto.laudate.ui.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grafitto.laudate.domain.attachment.AttachmentStore
import com.grafitto.laudate.domain.harmonic.HarmonicTransposer
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.model.music.PitchClass
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.repository.AttachmentRepository
import com.grafitto.laudate.domain.repository.PreferencesRepository
import com.grafitto.laudate.domain.repository.RhythmPatternRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.tablature.TablaturePlacement
import com.grafitto.laudate.domain.usecase.AddAttachmentToSongUseCase
import com.grafitto.laudate.domain.usecase.DeleteSongAttachmentUseCase
import com.grafitto.laudate.domain.usecase.GetTransposedSongUseCase
import com.grafitto.laudate.domain.usecase.ParseChordProUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SongDetailViewModel(
    private val songRepository: SongRepository,
    private val getTransposedSongUseCase: GetTransposedSongUseCase,
    private val parseChordProUseCase: ParseChordProUseCase,
    private val rhythmPatternRepository: RhythmPatternRepository? = null,
    private val attachmentRepository: AttachmentRepository? = null,
    private val addAttachmentToSongUseCase: AddAttachmentToSongUseCase? = null,
    private val deleteSongAttachmentUseCase: DeleteSongAttachmentUseCase? = null,
    private val attachmentStore: AttachmentStore? = null,
    /** Tablaturas del canto (ADR-017): se muestran en el visor según el modo de visualización. */
    private val tablatureRepository: TablatureRepository? = null,
    /**
     * Preferencias globales de lectura. La nomenclatura (Do-Re-Mi / C-D-E) se comparte con
     * el resto de la aplicación: cambiarla aquí la cambia en todas partes y se conserva.
     */
    private val preferencesRepository: PreferencesRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SongDetailUiState())
    val uiState: StateFlow<SongDetailUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<SongDetailSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    private var baseSong: Song? = null

    /** Tablatura elegida por canción (preferencia persistente), para recordarla al reabrir. */
    private var selectedTablatureBySong: Map<String, String> = emptyMap()

    /**
     * Colectores **del canto abierto**. El detalle reutiliza el mismo ViewModel al navegar de un
     * canto a otro, así que hay que cancelar los del anterior: si se acumulan, la última emisión en
     * llegar pisa el estado y la tablatura de un canto acaba viéndose en todos.
     */
    private var tablaturesJob: kotlinx.coroutines.Job? = null
    private var attachmentsJob: kotlinx.coroutines.Job? = null
    private var songJob: kotlinx.coroutines.Job? = null

    /**
     * Canto que la pantalla está mostrando ahora mismo. Se fija de forma **síncrona** al cargar y es
     * el filtro de las emisiones: `uiState.song` llega después, así que no sirve para descartar.
     */
    private var currentSongId: String? = null

    init {
        // La nomenclatura es una preferencia de la aplicación, no de esta pantalla.
        preferencesRepository?.let { prefs ->
            viewModelScope.launch {
                prefs.notationSystem.collect { system ->
                    _uiState.update { it.copy(notationSystem = system) }
                }
            }
            viewModelScope.launch {
                prefs.songDisplayMode.collect { mode ->
                    _uiState.update { it.copy(displayMode = mode) }
                }
            }
            viewModelScope.launch {
                prefs.tabDisplayPosition.collect { position ->
                    _uiState.update { it.copy(tabDisplayPosition = position) }
                }
            }
            viewModelScope.launch {
                prefs.tablatureScale.collect { scale ->
                    _uiState.update { it.copy(tablatureScale = scale) }
                }
            }
            viewModelScope.launch {
                prefs.tablatureNumberScale.collect { scale ->
                    _uiState.update { it.copy(tablatureNumberScale = scale) }
                }
            }
            // (D-48) El visor dibuja con los ajustes de pentagrama que el usuario fijó en el editor.
            viewModelScope.launch {
                prefs.tablatureStaffOptions.collect { options ->
                    _uiState.update { it.copy(tablatureStaffOptions = options) }
                }
            }
            viewModelScope.launch {
                prefs.selectedTablatureBySong.collect { map ->
                    selectedTablatureBySong = map
                    // Se restaura la tablatura que se estaba viendo en este canto, si sigue existiendo.
                    _uiState.update { state ->
                        val songId = state.song?.id ?: return@update state
                        state.copy(selectedTablatureId = map[songId] ?: state.selectedTablatureId)
                    }
                }
            }
            viewModelScope.launch {
                prefs.preferredTablatureInstrument.collect { instrument ->
                    _uiState.update { it.copy(preferredTablatureInstrument = instrument) }
                }
            }
            viewModelScope.launch {
                prefs.rhythmNotationMode.collect { mode ->
                    _uiState.update { it.copy(rhythmNotationMode = mode) }
                }
            }
            viewModelScope.launch {
                prefs.songFontSizeSp.collect { sizeSp ->
                    _uiState.update { it.copy(fontSizeSp = normalizeFontSize(sizeSp)) }
                }
            }
        }
    }

    fun processIntent(intent: SongDetailUiIntent) {
        when (intent) {
            is SongDetailUiIntent.LoadSong -> loadSong(intent.songId)
            is SongDetailUiIntent.LoadChordPro -> loadFromChordPro(intent.chordPro)
            is SongDetailUiIntent.TransposeKey -> transpose(intent.semitonesDelta)
            is SongDetailUiIntent.ResetTransposition -> resetTransposition()
            is SongDetailUiIntent.SetCapo -> setCapo(intent.capo)
            is SongDetailUiIntent.ToggleNotationSystem -> {
                _uiState.update { it.copy(notationSystem = intent.system) }
                // Se persiste para toda la app: el cambio sobrevive a la navegación y al reinicio.
                viewModelScope.launch { preferencesRepository?.setNotationSystem(intent.system) }
            }
            is SongDetailUiIntent.ChangeDisplayMode -> {
                _uiState.update { it.copy(displayMode = intent.mode) }
                viewModelScope.launch { preferencesRepository?.setSongDisplayMode(intent.mode) }
            }
            is SongDetailUiIntent.ChangeTabDisplayPosition -> {
                _uiState.update { it.copy(tabDisplayPosition = intent.position) }
                viewModelScope.launch { preferencesRepository?.setTabDisplayPosition(intent.position) }
            }
            is SongDetailUiIntent.SelectTablature -> {
                _uiState.update { it.copy(selectedTablatureId = intent.tablatureId) }
                // Se recuerda por canción: al volver a abrirlo, se muestra la misma tablatura.
                _uiState.value.song?.id?.let { songId ->
                    viewModelScope.launch { preferencesRepository?.setSelectedTablature(songId, intent.tablatureId) }
                }
            }
            is SongDetailUiIntent.SelectTablatureInstrument -> {
                _uiState.update { it.copy(preferredTablatureInstrument = intent.instrument) }
                // Preferencia GLOBAL: el instrumento elegido se usa en todas las canciones.
                viewModelScope.launch { preferencesRepository?.setPreferredTablatureInstrument(intent.instrument) }
            }
            is SongDetailUiIntent.MoveTablatureToSection ->
                moveTablatureToSection(intent.tablatureId, intent.sectionId)
            is SongDetailUiIntent.MoveTablatureOrder ->
                moveTablatureOrder(intent.tablatureId, intent.delta)
            is SongDetailUiIntent.ChangeRhythmNotationMode -> {
                _uiState.update { it.copy(rhythmNotationMode = intent.mode) }
                viewModelScope.launch { preferencesRepository?.setRhythmNotationMode(intent.mode) }
            }
            is SongDetailUiIntent.ToggleControls -> {
                _uiState.update { it.copy(isControlsExpanded = !it.isControlsExpanded) }
            }
            is SongDetailUiIntent.ToggleMetadata -> {
                _uiState.update { it.copy(isMetadataExpanded = !it.isMetadataExpanded) }
            }
            is SongDetailUiIntent.ToggleFocusMode -> {
                _uiState.update { it.copy(isFocusMode = !it.isFocusMode) }
            }
            is SongDetailUiIntent.SetAutoScroll -> {
                _uiState.update { it.copy(isAutoScrolling = intent.active) }
            }
            is SongDetailUiIntent.UpdateFontSize -> {
                val sizeSp = normalizeFontSize(intent.newSizeSp)
                _uiState.update { it.copy(fontSizeSp = sizeSp) }
            }
            is SongDetailUiIntent.SaveFontSize ->
                viewModelScope.launch {
                    preferencesRepository?.setSongFontSizeSp(_uiState.value.fontSizeSp)
                }
            is SongDetailUiIntent.UpdateTablatureScale -> {
                _uiState.update { it.copy(tablatureScale = normalizeTablatureScale(intent.newScale)) }
            }
            is SongDetailUiIntent.SaveTablatureScale ->
                viewModelScope.launch {
                    preferencesRepository?.setTablatureScale(_uiState.value.tablatureScale)
                }
            is SongDetailUiIntent.UpdateTablatureNumberScale -> {
                _uiState.update {
                    it.copy(tablatureNumberScale = normalizeTablatureScale(intent.newScale))
                }
            }
            is SongDetailUiIntent.SaveTablatureNumberScale ->
                viewModelScope.launch {
                    preferencesRepository?.setTablatureNumberScale(_uiState.value.tablatureNumberScale)
                }
            is SongDetailUiIntent.AddAttachmentFromUri -> addAttachmentFromUri(
                sourceUri = intent.sourceUri,
                displayName = intent.displayName,
                mimeType = intent.mimeType
            )
            is SongDetailUiIntent.AddAttachmentLink -> addAttachmentLink(intent.url, intent.title)
            is SongDetailUiIntent.DeleteAttachment -> deleteAttachment(intent.attachment)
            is SongDetailUiIntent.ExportAttachment -> exportAttachment(intent.attachment, intent.destinationUri)
            is SongDetailUiIntent.DeleteSong -> deleteSong(intent.songId)
        }
    }

    private fun normalizeFontSize(sizeSp: Float): Float =
        if (sizeSp.isFinite()) sizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP) else DEFAULT_FONT_SIZE_SP

    private fun normalizeTablatureScale(scale: Float): Float =
        if (scale.isFinite()) scale.coerceIn(MIN_TABLATURE_SCALE, MAX_TABLATURE_SCALE)
        else DEFAULT_TABLATURE_SCALE

    private companion object {
        const val DEFAULT_FONT_SIZE_SP = 16f
        const val MIN_FONT_SIZE_SP = 14f
        const val MAX_FONT_SIZE_SP = 32f
        const val DEFAULT_TABLATURE_SCALE =
            com.grafitto.laudate.domain.tablature.TabNotationScale.DEFAULT_ZOOM
        /** (D-60) El mínimo baja a `0,35`: el 70 % seguía siendo demasiado grande en móvil. */
        const val MIN_TABLATURE_SCALE =
            com.grafitto.laudate.domain.tablature.TabNotationScale.MIN_ZOOM
        const val MAX_TABLATURE_SCALE =
            com.grafitto.laudate.domain.tablature.TabNotationScale.MAX_ZOOM
    }

    /** Importa un fichero elegido con el selector del sistema y lo asocia al canto. */
    private fun addAttachmentFromUri(sourceUri: String, displayName: String, mimeType: String?) {
        val useCase = addAttachmentToSongUseCase
        val songId = _uiState.value.song?.id
        if (useCase == null || songId == null) {
            viewModelScope.launch {
                _sideEffect.send(SongDetailSideEffect.ShowMessage("No se pudo añadir el adjunto"))
            }
            return
        }
        viewModelScope.launch {
            runCatching { useCase(songId, sourceUri, displayName, mimeType) }
                .onSuccess {
                    _sideEffect.send(SongDetailSideEffect.ShowMessage("Adjunto añadido"))
                }
                .onFailure { error ->
                    _sideEffect.send(
                        SongDetailSideEffect.ShowMessage("No se pudo añadir el adjunto: ${error.message ?: "error"}")
                    )
                }
        }
    }

    private fun addAttachmentLink(url: String, title: String?) {
        val useCase = addAttachmentToSongUseCase
        val songId = _uiState.value.song?.id
        if (useCase == null || songId == null) {
            viewModelScope.launch {
                _sideEffect.send(SongDetailSideEffect.ShowMessage("No se pudo añadir el enlace"))
            }
            return
        }
        viewModelScope.launch {
            runCatching { useCase.addLink(songId, url, title) }
                .onSuccess {
                    _sideEffect.send(SongDetailSideEffect.ShowMessage("Enlace añadido"))
                }
                .onFailure { error ->
                    _sideEffect.send(
                        SongDetailSideEffect.ShowMessage("Enlace no válido: ${error.message ?: "error"}")
                    )
                }
        }
    }

    private fun deleteAttachment(attachment: SongAttachment) {
        val useCase = deleteSongAttachmentUseCase ?: return
        viewModelScope.launch {
            runCatching { useCase(attachment) }
                .onSuccess {
                    _sideEffect.send(SongDetailSideEffect.ShowMessage("Adjunto eliminado"))
                }
                .onFailure { error ->
                    _sideEffect.send(
                        SongDetailSideEffect.ShowMessage("No se pudo eliminar: ${error.message ?: "error"}")
                    )
                }
        }
    }

    private fun exportAttachment(attachment: SongAttachment, destinationUri: String) {
        val store = attachmentStore ?: return
        viewModelScope.launch {
            val result = runCatching { store.exportFile(attachment, destinationUri) }
                .getOrElse { Result.failure(it) }
            val message = result.fold(
                onSuccess = { "Adjunto guardado en la carpeta elegida" },
                onFailure = { error -> "No se pudo guardar: ${error.message ?: "error"}" }
            )
            _sideEffect.send(SongDetailSideEffect.ShowMessage(message))
        }
    }

    /** Observa los adjuntos del canto para mantener la lista reactiva. */
    private fun observeAttachments(songId: String) {
        val repository = attachmentRepository ?: return
        attachmentsJob?.cancel()
        attachmentsJob = viewModelScope.launch {
            repository.getAttachments(songId).collect { list ->
                if (currentSongId != songId) return@collect
                _uiState.update { it.copy(attachments = list) }
            }
        }
    }

    /** Observa las tablaturas del canto (ADR-017) para la sección de vista previa/visor. */
    private fun observeTablatures(songId: String) {
        val repository = tablatureRepository ?: return
        tablaturesJob?.cancel()
        tablaturesJob = viewModelScope.launch {
            repository.observeTablaturesForSong(songId).collect { list ->
                // Emisión tardía de un canto que ya no se está viendo: se descarta.
                if (currentSongId != songId) return@collect
                _uiState.update { state ->
                    state.copy(
                        tablatures = list,
                        // Si la tablatura elegida ya no existe, vuelve a la primera automáticamente.
                        selectedTablatureId = state.selectedTablatureId?.takeIf { id ->
                            list.any { it.id == id }
                        }
                    )
                }
            }
        }
    }

    /**
     * **Mueve** una tablatura a otra sección del canto (`null` = bloque general). Se guarda el
     * documento completo: la sección viaja dentro del propio JSON de la tablatura.
     */
    private fun moveTablatureToSection(tablatureId: String, sectionId: String?) {
        val repository = tablatureRepository ?: return
        viewModelScope.launch {
            val tablature = repository.getTablatureById(tablatureId) ?: return@launch
            if (tablature.sectionId == sectionId) return@launch
            val songId = tablature.songId
            val siblings = repository.getTablaturesForSong(songId)
                .filter { it.id != tablatureId && it.sectionId == sectionId }
            repository.saveTablature(TablaturePlacement.moveToSection(tablature, sectionId, siblings))
        }
    }

    /**
     * **Reordena** una tablatura dentro de su grupo (misma sección, o el bloque general). Se
     * reasigna el orden de todo el grupo (0, 1, 2…) para que quede estable y sin huecos.
     */
    private fun moveTablatureOrder(tablatureId: String, delta: Int) {
        val repository = tablatureRepository ?: return
        if (delta == 0) return
        viewModelScope.launch {
            val tablature = repository.getTablatureById(tablatureId) ?: return@launch
            val songId = tablature.songId
            val group = repository.getTablaturesForSong(songId)
                .filter { it.sectionId == tablature.sectionId }
            val reordered = TablaturePlacement.reorder(group, tablatureId, delta)
            // `reorder` devuelve la misma lista si no había nada que mover: no se escribe en disco.
            if (reordered === group) return@launch
            reordered.forEach { repository.saveTablature(it) }
        }
    }

    private fun deleteSong(songId: String) {
        viewModelScope.launch {
            // Primero se borran los ficheros del almacén privado; la fila de la canción cae después
            // y sus adjuntos por la clave foránea en cascada.
            runCatching { attachmentStore?.deleteFilesForSong(songId) }
            songRepository.deleteSong(songId)
            _sideEffect.send(SongDetailSideEffect.ShowMessage("Canción eliminada"))
            _sideEffect.send(SongDetailSideEffect.NavigateBack())
        }
    }

    private fun loadSong(songId: String) {
        currentSongId = songId
        // Al cambiar de canto se vacía lo del anterior antes de suscribirse al nuevo: si no, durante
        // un instante (o para siempre, con un colector rezagado) se vería su tablatura y sus adjuntos.
        _uiState.update { state ->
            if (state.song?.id == songId) {
                state
            } else {
                state.copy(tablatures = emptyList(), attachments = emptyList(), selectedTablatureId = null)
            }
        }
        observeAttachments(songId)
        observeTablatures(songId)
        songJob?.cancel()
        songJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            songRepository.getSongById(songId).collect { song ->
                if (song != null) {
                    baseSong = song
                    val allPatterns = rhythmPatternRepository?.getAllPatterns()?.firstOrNull().orEmpty()
                    val pattern = song.rhythmPatternId?.let { patternId ->
                        allPatterns.firstOrNull { it.id == patternId }
                    }
                    // Patrón por sección declarado con `{x_rhythm: Nombre}`: se resuelve por nombre
                    // (los patrones integrados no tienen fila propia, el nombre es la identidad
                    // estable). Si el nombre no coincide con ningún patrón, la sección no muestra
                    // rejilla, pero el resto del canto se sigue viendo con normalidad.
                    val sectionPatterns = song.sections.mapNotNull { section ->
                        val name = section.rhythmPatternName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        allPatterns.firstOrNull { it.name.equals(name, ignoreCase = true) }
                            ?.let { section.sequenceOrder to it }
                    }.toMap()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            song = song,
                            originalKey = song.originalKey,
                            currentKey = song.originalKey,
                            transpositionSemitones = 0,
                            capoPosition = song.capo,
                            selectedTablatureId = selectedTablatureBySong[song.id] ?: it.selectedTablatureId,
                            rhythmPattern = pattern,
                            sectionRhythmPatterns = sectionPatterns
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Canción no encontrada"
                        )
                    }
                    _sideEffect.send(SongDetailSideEffect.ShowMessage("Canción no encontrada"))
                }
            }
        }
    }

    private fun loadFromChordPro(content: String) {
        val song = parseChordProUseCase(content)
        baseSong = song
        _uiState.update {
            it.copy(
                isLoading = false,
                song = song,
                originalKey = song.originalKey,
                currentKey = song.originalKey,
                transpositionSemitones = 0,
                capoPosition = 0
            )
        }
    }

    private fun transpose(delta: Int) {
        val base = baseSong ?: _uiState.value.song ?: return
        val newSemitones = _uiState.value.transpositionSemitones + delta

        val transposed = getTransposedSongUseCase(base, newSemitones)
        _uiState.update {
            it.copy(
                song = transposed,
                transpositionSemitones = newSemitones,
                currentKey = transposed.originalKey
            )
        }
    }

    private fun resetTransposition() {
        val base = baseSong ?: return
        _uiState.update {
            it.copy(
                song = base,
                transpositionSemitones = 0,
                currentKey = base.originalKey
            )
        }
    }

    private fun setCapo(capo: Int) {
        _uiState.update { it.copy(capoPosition = capo) }
    }
}
