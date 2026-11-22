package com.grafitto.laudate.ui.rhythm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumArticulation
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.repository.RhythmPatternRepository
import com.grafitto.laudate.domain.rhythm.RhythmGrid
import com.grafitto.laudate.domain.rhythm.RhythmGridCell
import com.grafitto.laudate.domain.rhythm.RhythmGridMapping
import com.grafitto.laudate.domain.rhythm.RhythmGridResolution
import com.grafitto.laudate.domain.rhythm.RhythmPlaybackScheduler
import com.grafitto.laudate.domain.rhythm.RhythmPlayer
import com.grafitto.laudate.domain.rhythm.RhythmTextNotation
import com.grafitto.laudate.domain.rhythm.StrummingAutomation
import com.grafitto.laudate.domain.transfer.CANTICORA_RHYTHM_MIME
import com.grafitto.laudate.domain.usecase.ExportRhythmPatternsUseCase
import com.grafitto.laudate.domain.usecase.ImportRhythmPatternsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del creador y visualizador de patrones rítmicos.
 *
 * Mantiene **un único modelo de datos** (`List<RhythmEvent>`) que representan todas las
 * vistas (flechas, figuras musicales y combinada). La edición desde cualquier vista
 * muta el mismo modelo, de modo que no hay lógica duplicada ni sincronización manual.
 */
class RhythmPatternViewModel(
    private val rhythmPatternRepository: RhythmPatternRepository,
    private val rhythmPlayer: RhythmPlayer,
    private val exportRhythmPatternsUseCase: ExportRhythmPatternsUseCase,
    private val importRhythmPatternsUseCase: ImportRhythmPatternsUseCase,
    initialTimeSignature: TimeSignature = TimeSignature.FOUR_FOUR
) : ViewModel() {

    private val _uiState = MutableStateFlow(RhythmPatternUiState(selectedTimeSignature = initialTimeSignature))
    val uiState: StateFlow<RhythmPatternUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<RhythmPatternSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    /** Último patrón eliminado, conservado en memoria para poder deshacer el borrado. */
    private var lastDeletedPattern: RhythmPattern? = null

    init {
        loadPatterns()
    }

    fun processIntent(intent: RhythmPatternUiIntent) {
        when (intent) {
            is RhythmPatternUiIntent.LoadPatterns -> loadPatterns()
            is RhythmPatternUiIntent.SetNotationMode -> _uiState.update { it.copy(notationMode = intent.mode) }
            is RhythmPatternUiIntent.SelectTimeSignature -> _uiState.update {
                it.copy(selectedTimeSignature = intent.timeSignature)
            }
            is RhythmPatternUiIntent.OpenNewPatternEditor -> {
                stopPreview()
                _uiState.update {
                    it.copy(
                        isEditorOpen = true,
                        editorIsNew = true,
                        editingPatternId = null,
                        actionPatternId = null,
                        editorName = "",
                        editorEvents = emptyList(),
                        errorMessage = null
                    )
                }
            }
            is RhythmPatternUiIntent.OpenEditPattern -> openPattern(intent.patternId, asCopy = false)
            is RhythmPatternUiIntent.DuplicatePattern -> openPattern(intent.patternId, asCopy = true)
            is RhythmPatternUiIntent.UpdateEditorName -> _uiState.update { it.copy(editorName = intent.name) }
            is RhythmPatternUiIntent.SetEditorTimeSignature ->
                changeTimeSignature(intent.timeSignature)
            is RhythmPatternUiIntent.SetEditorMode -> _uiState.update {
                it.copy(editorMode = intent.mode, selectedEventIndex = null, errorMessage = null)
            }

            // ── Rejilla «Beat & Stroke» ────────────────────────────────────
            is RhythmPatternUiIntent.SetGridResolution -> _uiState.update {
                it.copy(gridResolution = intent.resolution, errorMessage = null)
            }
            is RhythmPatternUiIntent.SetPaletteCell -> _uiState.update { it.copy(paletteCell = intent.cell) }
            is RhythmPatternUiIntent.SetGridCell -> setGridCell(intent.slot)
            is RhythmPatternUiIntent.CycleGridCell -> cycleGridCell(intent.slot)
            is RhythmPatternUiIntent.ClearGridCell -> clearGridCell(intent.slot)
            is RhythmPatternUiIntent.AddBar -> addBar()
            is RhythmPatternUiIntent.RemoveBar -> removeBar()
            is RhythmPatternUiIntent.AutoAssignGridDirections -> autoAssignGridDirections()

            is RhythmPatternUiIntent.AddEvent -> addEvent(intent.figure)
            is RhythmPatternUiIntent.AddMuteEvent -> addEvent(intent.figure, StrumDirection.MUTE)
            is RhythmPatternUiIntent.SetEventFigure -> updateEvent(intent.index) {
                it.copy(figure = intent.figure)
            }
            is RhythmPatternUiIntent.SetEventDirection -> updateEvent(intent.index) {
                it.copy(direction = intent.direction)
            }
            is RhythmPatternUiIntent.CycleEventDirection -> updateEvent(intent.index) {
                it.copy(direction = it.direction.cycled())
            }
            is RhythmPatternUiIntent.ToggleEventAccent -> updateEvent(intent.index) {
                val next = it.articulation.toggleAccent()
                it.copy(articulation = next)
            }
            is RhythmPatternUiIntent.CycleEventArticulation -> updateEvent(intent.index) {
                it.copy(articulation = StrumArticulation.next(it.articulation))
            }
            is RhythmPatternUiIntent.SelectEvent -> _uiState.update {
                it.copy(selectedEventIndex = intent.index)
            }
            is RhythmPatternUiIntent.SetEventArticulation -> updateEvent(intent.index) {
                it.copy(articulation = intent.articulation)
            }
            is RhythmPatternUiIntent.SetEventRange -> updateEvent(intent.index) {
                it.copy(range = intent.range)
            }
            is RhythmPatternUiIntent.ToggleEventRest -> toggleRest(intent.index)
            is RhythmPatternUiIntent.RemoveEvent -> removeEvent(intent.index)
            is RhythmPatternUiIntent.MoveEventLeft -> moveEvent(intent.index, -1)
            is RhythmPatternUiIntent.MoveEventRight -> moveEvent(intent.index, +1)
            is RhythmPatternUiIntent.AutoAssignDirections -> _uiState.update {
                it.copy(
                    editorEvents = StrummingAutomation.apply(it.editorEvents, it.selectedTimeSignature),
                    errorMessage = null
                )
            }
            is RhythmPatternUiIntent.RemoveLastEvent -> _uiState.update {
                if (it.editorEvents.isEmpty()) it
                else it.copy(editorEvents = it.editorEvents.dropLast(1), errorMessage = null)
            }
            is RhythmPatternUiIntent.ClearEditor -> _uiState.update {
                it.copy(editorEvents = emptyList(), errorMessage = null)
            }

            is RhythmPatternUiIntent.SavePattern -> savePattern()
            is RhythmPatternUiIntent.DeletePattern -> deletePattern(intent.patternId)
            is RhythmPatternUiIntent.DeleteEditingPattern -> {
                val patternId = _uiState.value.editingPatternId
                if (patternId != null) {
                    stopPreview()
                    _uiState.update { it.copy(isEditorOpen = false, editingPatternId = null) }
                    deletePattern(patternId)
                }
            }
            is RhythmPatternUiIntent.UndoDeletePattern -> undoDeletePattern()
            is RhythmPatternUiIntent.CloseEditor -> {
                stopPreview()
                _uiState.update { it.copy(isEditorOpen = false) }
            }

            is RhythmPatternUiIntent.SetPreviewBpm -> {
                val clamped = intent.bpm.coerceIn(RhythmPlaybackScheduler.MIN_BPM, RhythmPlaybackScheduler.MAX_BPM)
                _uiState.update { it.copy(previewBpm = clamped) }
                restartPreviewIfPlaying()
            }
            is RhythmPatternUiIntent.AdjustPreviewBpm -> {
                val clamped = (_uiState.value.previewBpm + intent.delta)
                    .coerceIn(RhythmPlaybackScheduler.MIN_BPM, RhythmPlaybackScheduler.MAX_BPM)
                _uiState.update { it.copy(previewBpm = clamped) }
                restartPreviewIfPlaying()
            }
            is RhythmPatternUiIntent.TogglePreviewPlayback -> togglePreviewPlayback()
            is RhythmPatternUiIntent.StopPreview -> stopPreview()

            is RhythmPatternUiIntent.SharePattern -> sharePattern(intent.patternId)
            is RhythmPatternUiIntent.CopyPatternAsText -> copyPatternAsText(intent.patternId)
            is RhythmPatternUiIntent.ShareAllPatterns -> shareAllPatterns()
            is RhythmPatternUiIntent.ImportPatternsFromText -> importFromText(intent.text)

            is RhythmPatternUiIntent.ShowError -> _uiState.update { it.copy(errorMessage = intent.message) }
        }
    }

    // ── Previsualización sonora (RF-306 / RF-307) ──────────────────────────────

    /** Alterna entre reproducir y detener el patrón en construcción. */
    private fun togglePreviewPlayback() {
        if (_uiState.value.isPlaying) {
            stopPreview()
        } else {
            startPreview()
        }
    }

    private fun startPreview() {
        val state = _uiState.value
        if (state.editorEvents.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "El patrón está vacío: añade figuras para escucharlo") }
            return
        }
        val pattern = state.editorAsPattern()
        _uiState.update { it.copy(isPlaying = true, activeEventIndex = null, errorMessage = null) }
        rhythmPlayer.play(pattern, state.previewBpm) { index ->
            _uiState.update { it.copy(activeEventIndex = index) }
        }
    }

    private fun stopPreview() {
        rhythmPlayer.stop()
        _uiState.update { it.copy(isPlaying = false, activeEventIndex = null) }
    }

    // ── Intercambio de ritmos (RF-310) ─────────────────────────────────────────

    /** Comparte un patrón como archivo, listo para WhatsApp, correo o Bluetooth. */
    private fun sharePattern(patternId: String) {
        viewModelScope.launch {
            val json = exportRhythmPatternsUseCase(patternId)
            if (json == null) {
                _sideEffect.send(RhythmPatternSideEffect.ShowMessage("No se encontró el ritmo"))
                return@launch
            }
            _sideEffect.send(
                RhythmPatternSideEffect.ShareContent(
                    content = json,
                    fileName = "ritmo.canticora-rhythm",
                    mimeType = CANTICORA_RHYTHM_MIME
                )
            )
        }
    }

    /** Copia un patrón como **texto** para pegarlo en un mensaje o una nota. */
    private fun copyPatternAsText(patternId: String) {
        val pattern = _uiState.value.allPatterns.firstOrNull { it.id == patternId }
        if (pattern == null) {
            viewModelScope.launch {
                _sideEffect.send(RhythmPatternSideEffect.ShowMessage("No se encontró el ritmo"))
            }
            return
        }
        val text = RhythmTextNotation.encode(pattern)
        viewModelScope.launch { _sideEffect.send(RhythmPatternSideEffect.CopyToClipboard(text)) }
    }

    /** Comparte la colección completa de ritmos propios como un único archivo. */
    private fun shareAllPatterns() {
        viewModelScope.launch {
            val json = exportRhythmPatternsUseCase()
            _sideEffect.send(
                RhythmPatternSideEffect.ShareContent(
                    content = json,
                    fileName = "mis_ritmos.canticora-rhythm",
                    mimeType = CANTICORA_RHYTHM_MIME
                )
            )
        }
    }

    /** Importa un ritmo pegado como texto. */
    private fun importFromText(text: String) {
        viewModelScope.launch {
            val result = importRhythmPatternsUseCase.import(text)
            val message = when {
                result == null -> "El texto no contiene un ritmo reconocible"
                result.created == 0 -> "Ese ritmo ya estaba en tu lista"
                else -> "Ritmo importado: ${result.summary}"
            }
            _sideEffect.send(RhythmPatternSideEffect.ShowMessage(message))
        }
    }

    /** Reinicia la reproducción para aplicar un BPM nuevo mientras se está escuchando. */
    private fun restartPreviewIfPlaying() {
        if (_uiState.value.isPlaying) {
            stopPreview()
            startPreview()
        }
    }

    override fun onCleared() {
        rhythmPlayer.stop()
        super.onCleared()
    }

    private fun loadPatterns() {
        viewModelScope.launch {
            rhythmPatternRepository.getAllPatterns().collect { patterns ->
                _uiState.update { it.copy(isLoading = false, allPatterns = patterns) }
            }
        }
    }

    /**
     * Abre un patrón en el editor. Si [asCopy] es cierto, se guardará como uno nuevo.
     *
     * Los patrones **predefinidos** también se editan en su sitio: al guardar se persiste una
     * fila propia con el mismo id que **sustituye** a la versión de la biblioteca (ver
     * `RhythmPatternRepositoryImpl`). Así «Editar» cumple lo que promete el título de la pantalla
     * y el usuario no acaba con dos ritmos distintos que se llaman igual.
     */
    private fun openPattern(patternId: String, asCopy: Boolean) {
        val pattern = _uiState.value.allPatterns.firstOrNull { it.id == patternId } ?: return
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editorIsNew = asCopy,
                editingPatternId = if (asCopy) null else pattern.id,
                // Las acciones (compartir, copiar, duplicar, eliminar) conocen el id del origen,
                // también cuando es un predefinido o una copia recién creada sin guardar.
                actionPatternId = pattern.id,
                editorName = if (asCopy) "${pattern.name} (copia)" else pattern.name,
                editorEvents = pattern.events,
                selectedTimeSignature = pattern.timeSignature,
                errorMessage = null
            )
        }
    }

    /**
     * Cambia el compás del editor conservando las posiciones.
     *
     * No se bloquea: si el patrón deja de ocupar compases completos, la propia UI avisa de cuánto
     * falta o sobra y el usuario lo ajusta. Bloquear el cambio obligaría a borrar el patrón para
     * poder probar otro compás.
     */
    private fun changeTimeSignature(timeSignature: TimeSignature) {
        _uiState.update {
            it.copy(selectedTimeSignature = timeSignature, errorMessage = null)
        }
    }

    /**
     * Añade una posición al final, respetando el compás y aplicando la dirección automática.
     *
     * Un silencio se añade sin dirección; una figura sonora recibe la dirección que le corresponde
     * por la regla del péndulo, de modo que el editor avanzado produce el mismo resultado que la
     * rejilla (antes se añadía siempre `NONE`, y la posición se veía como aire).
     */
    private fun addEvent(figure: RhythmFigure, direction: StrumDirection? = null) {
        val current = _uiState.value
        val newTotal = current.editorTotalBeats + figure.beatValue
        if (newTotal > current.targetBeats + 1e-6) {
            _uiState.update {
                it.copy(
                    errorMessage = "La figura no cabe en el compás actual. Añade otro compás para " +
                        "continuar. " + beatsGapMessage(newTotal, current.targetBeats)
                )
            }
            return
        }
        _uiState.update { state ->
            val resolved = direction ?: if (figure.isRest) {
                StrumDirection.NONE
            } else {
                StrummingAutomation.nextDirection(state.editorEvents, state.selectedTimeSignature)
            }
            val newEvent = RhythmEvent(figure = figure, direction = resolved)
            state.copy(editorEvents = state.editorEvents + newEvent, errorMessage = null)
        }
    }

    /** Añade un compás al final del patrón, relleno con silencios. */
    private fun addBar() {
        _uiState.update { state ->
            state.copy(
                editorEvents = RhythmGridMapping.withBarCount(
                    pattern = state.editorAsPattern(),
                    resolution = state.effectiveGridResolution,
                    barCount = state.editorBarCount + 1
                ),
                errorMessage = null
            )
        }
    }

    /** Quita el último compás del patrón. Nunca deja el patrón sin compases. */
    private fun removeBar() {
        _uiState.update { state ->
            val bars = state.editorBarCount
            if (bars <= 1) return@update state
            state.copy(
                editorEvents = RhythmGridMapping.withBarCount(
                    pattern = state.editorAsPattern(),
                    resolution = state.effectiveGridResolution,
                    barCount = bars - 1
                ),
                errorMessage = null
            )
        }
    }

    /** Aplica una transformación a la lista de eventos del editor. */
    private inline fun updateEditorEvents(transform: (List<RhythmEvent>) -> List<RhythmEvent>) {
        _uiState.update { it.copy(editorEvents = transform(it.editorEvents), errorMessage = null) }
    }

    /**
     * Aplica el trazo activo de la paleta a una casilla (toque corto en la rejilla).
     *
     * La paleta elige **el trazo** (dirección y registro), no la articulación: si la casilla ya
     * tenía un golpe, se conserva su acento/arpegio/staccato. Así escribir flechas en la rejilla
     * nunca borra las articulaciones puestas en el editor avanzado.
     */
    private fun setGridCell(slot: Int) {
        val state = _uiState.value
        val current = state.gridCells.getOrNull(slot) ?: return
        val palette = state.paletteCell
        val target = if (current.isAttack) palette.copy(articulation = current.articulation) else palette
        if (current == target) return
        applyGridCell(slot, target)
    }

    /** Deja una casilla en aire (silencio), sin tocar el resto del patrón. */
    private fun clearGridCell(slot: Int) {
        val state = _uiState.value
        val current = state.gridCells.getOrNull(slot) ?: return
        if (current == RhythmGridCell.Air) return
        applyGridCell(slot, RhythmGridCell.Air)
    }

    private fun cycleGridCell(slot: Int) {
        val state = _uiState.value
        val current = state.gridCells.getOrNull(slot) ?: return
        applyGridCell(slot, RhythmGridCell.next(current))
    }

    /**
     * Edita **una sola casilla** de la rejilla sobre el modelo, sin reescribir el resto del
     * patrón: las figuras, articulaciones y registros de las demás posiciones se conservan
     * (ver [RhythmGridMapping.applyCell]).
     */
    private fun applyGridCell(slot: Int, cell: RhythmGridCell) {
        _uiState.update { state ->
            state.copy(
                editorEvents = RhythmGridMapping.applyCell(
                    pattern = state.editorAsPattern(),
                    resolution = state.effectiveGridResolution,
                    slot = slot,
                    cell = cell
                ),
                errorMessage = null
            )
        }
    }

    /**
     * Regenera ↓/↑ con la regla del péndulo, conservando silencios, apagados, figuras,
     * articulaciones y registros: solo cambia la **dirección** de los golpes.
     */
    private fun autoAssignGridDirections() {
        val state = _uiState.value
        updateEditorEvents { StrummingAutomation.apply(it, state.selectedTimeSignature) }
    }

    /** Aplica una transformación a una posición concreta, preservando el resto. */
    private inline fun updateEvent(index: Int, transform: (RhythmEvent) -> RhythmEvent) {
        _uiState.update { state ->
            if (index !in state.editorEvents.indices) return@update state
            val updated = state.editorEvents.toMutableList()
            updated[index] = transform(updated[index])
            state.copy(editorEvents = updated, errorMessage = null)
        }
    }

    private fun removeEvent(index: Int) {
        _uiState.update { state ->
            if (index !in state.editorEvents.indices) return@update state
            state.copy(editorEvents = state.editorEvents.filterIndexed { i, _ -> i != index }, errorMessage = null)
        }
    }

    /** Alterna una posición entre golpe y silencio, conservando la dirección previa. */
    private fun toggleRest(index: Int) {
        updateEvent(index) { event ->
            if (event.figure.isRest) {
                val noteFigure = RhythmFigure.noteEquivalentOf(event.figure) ?: RhythmFigure.QUARTER
                val restoredDirection = switchDirection(event.direction)
                event.copy(figure = noteFigure, direction = restoredDirection)
            } else {
                val restFigure = RhythmFigure.restEquivalentOf(event.figure) ?: RhythmFigure.QUARTER_REST
                event.copy(figure = restFigure, direction = StrumDirection.NONE)
            }
        }
    }

    private fun switchDirection(direction: StrumDirection): StrumDirection =
        if (direction.isStruck) direction else StrumDirection.DOWN

    private fun moveEvent(index: Int, delta: Int) {
        _uiState.update { state ->
            val target = index + delta
            if (index !in state.editorEvents.indices || target !in state.editorEvents.indices) return@update state
            val list = state.editorEvents.toMutableList()
            val item = list.removeAt(index)
            list.add(target, item)
            state.copy(editorEvents = list, errorMessage = null)
        }
    }

    private fun savePattern() {
        val state = _uiState.value
        if (state.editorName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Asigna un nombre al patrón") }
            return
        }
        if (!state.isEditorValid) {
            _uiState.update {
                it.copy(
                    errorMessage = "El patrón no ocupa compases completos de ${state.selectedTimeSignature}. " +
                        beatsGapMessage(state.editorTotalBeats, state.targetBeats)
                )
            }
            return
        }

        viewModelScope.launch {
            val pattern = RhythmPattern(
                id = state.editingPatternId ?: newId(),
                name = state.editorName.trim(),
                timeSignature = state.selectedTimeSignature,
                events = state.editorEvents,
                isBuiltIn = false
            )
            rhythmPatternRepository.savePattern(pattern)
            _uiState.update { it.copy(isEditorOpen = false, editingPatternId = null) }
            _sideEffect.send(RhythmPatternSideEffect.ShowMessage("Patrón guardado"))
        }
    }

    private fun deletePattern(patternId: String) {
        viewModelScope.launch {
            // Se conserva el patrón completo para poder restaurarlo con «Deshacer»
            // (también los integrados, que se ocultan por nombre en el repositorio).
            lastDeletedPattern = _uiState.value.allPatterns.firstOrNull { it.id == patternId }
            rhythmPatternRepository.deletePattern(patternId)
            _sideEffect.send(RhythmPatternSideEffect.ShowUndoMessage("Patrón eliminado"))
        }
    }

    /** Restaura el último patrón eliminado (deshacer el borrado). */
    private fun undoDeletePattern() {
        val pattern = lastDeletedPattern ?: return
        lastDeletedPattern = null
        viewModelScope.launch {
            rhythmPatternRepository.restorePattern(pattern)
            _sideEffect.send(RhythmPatternSideEffect.ShowMessage("Patrón restaurado"))
        }
    }

    private fun formatBeats(beats: Double): String {
        val rounded = kotlin.math.round(beats * 100) / 100.0
        return "$rounded negras"
    }

    /**
     * Describe cuánto le falta o le sobra al patrón para completar el compás. Nunca imprime
     * cantidades negativas: si el patrón se pasa, dice cuánto **sobra**.
     */
    private fun beatsGapMessage(total: Double, target: Double): String {
        val diff = target - total
        return if (diff >= 0) "Faltan ${formatBeats(diff)}." else "Sobran ${formatBeats(-diff)}."
    }
}
