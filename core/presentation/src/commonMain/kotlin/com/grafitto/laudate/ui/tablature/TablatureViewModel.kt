package com.grafitto.laudate.ui.tablature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.tablature.FingeringOptions
import com.grafitto.laudate.domain.tablature.MAX_FRET
import com.grafitto.laudate.domain.tablature.TabKeyboardInput
import com.grafitto.laudate.domain.tablature.TabMeasureMeter
import com.grafitto.laudate.domain.tablature.TabMixer
import com.grafitto.laudate.domain.tablature.TabNoteRef
import com.grafitto.laudate.domain.tablature.TabNotationScale
import com.grafitto.laudate.domain.tablature.TabNoteSelection
import com.grafitto.laudate.domain.tablature.TabShortcuts
import com.grafitto.laudate.domain.tablature.TabStringData
import com.grafitto.laudate.domain.tablature.TabTranspositionMode
import com.grafitto.laudate.domain.tablature.TabVoiceColors
import com.grafitto.laudate.domain.tablature.TablatureEditing
import com.grafitto.laudate.domain.tablature.TablaturePlacement
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * ViewModel del **editor de tablaturas** (ADR-017, *Feature T*).
 *
 * Mantiene **un único modelo** ([TablatureUiState.editorMeasures]) que edita la rejilla; guardar
 * persiste la tablatura por el [TablatureRepository]. La edición reutiliza los helpers puros de
 * [TablatureEditing] para no alterar nunca la duración del compás.
 */
@OptIn(ExperimentalUuidApi::class)
class TablatureViewModel(
    private val tablatureRepository: TablatureRepository,
    /** Preferencias globales: la escala del dibujo de las tablaturas se recuerda entre sesiones. */
    private val preferencesRepository: com.grafitto.laudate.domain.repository.PreferencesRepository? = null,
    /**
     * Reproductor del sonido de una nota al **escribir** en el editor (opcional). La síntesis es pura
     * y compartida ([com.grafitto.laudate.domain.tablature.TabToneRenderer]); cada plataforma la
     * reproduce (AudioTrack en Android, SourceDataLine en escritorio, ADR-012/ADR-013).
     */
    private val notePlayer: com.grafitto.laudate.domain.tablature.TabNotePlayer? = null,
    /**
     * Importación de una **partitura** (MusicXML, MuseScore, Guitar Pro, TuxGuitar) como tablatura
     * del canto, **sustituyendo** la que hubiera (opcional: en pruebas puede no inyectarse).
     */
    private val importTablatureUseCase: com.grafitto.laudate.domain.usecase.ImportTablatureFromFileUseCase? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(TablatureUiState())
    val uiState: StateFlow<TablatureUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<TablatureSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    /** Última tablatura eliminada, conservada en memoria para poder deshacer el borrado. */
    private var lastDeleted: Tablature? = null

    /** Job del sonido diferido al **completar** la cifra de dos dígitos (se cancela al teclear más). */
    private var digitSoundJob: Job? = null

    /** Job de la **reproducción de la partitura** (B-9); se cancela al parar o al cerrar el editor. */
    private var playbackJob: Job? = null

    /**
     * Colector **vigente** de las tablaturas del canto abierto.
     *
     * Es un ViewModel compartido por todas las pantallas, así que al pasar del canto A al B hay que
     * cancelar el colector de A antes de abrir el de B. Si no se cancelara, ambos seguirían vivos y
     * la última emisión en llegar pisaría el estado: la tablatura de un canto aparecía en **todos**.
     */
    private var tablaturesJob: Job? = null
    // ── Historial (deshacer/rehacer, E2) y portapapeles (E3) ──────────────────────
    /** Pila de estados **anteriores** del documento (el tope es el estado actual). */
    private val undoStack = ArrayDeque<EditorSnapshot>()
    /** Pila de estados **deshechos** para poder rehacer. */
    private val redoStack = ArrayDeque<EditorSnapshot>()
    /** Bloque de compases copiados/cortados (portapapeles en memoria del editor). */
    private var clipboard: com.grafitto.laudate.domain.tablature.TablatureClipboard? = null
    /** Notas copiadas/cortadas (portapapeles de notas en memoria del editor, Fase 2). */
    private var noteClipboard: com.grafitto.laudate.domain.tablature.TabNoteClipboard? = null
    init {
        preferencesRepository?.let { prefs ->
            viewModelScope.launch {
                prefs.tablatureScale.collect { scale ->
                    _uiState.update { it.copy(tablatureScale = scale) }
                }
            }
            // El **tamaño de las cifras** es preferencia global (la comparte el visor): se recupera
            // al abrir el editor para que el atril se vea como se dejó.
            viewModelScope.launch {
                prefs.tablatureNumberScale.collect { scale ->
                    _uiState.update { it.copy(tablatureNumberScale = scale) }
                }
            }
            viewModelScope.launch {
                prefs.metronomeEnabled.collect { on ->
                    _uiState.update { it.copy(metronomeEnabled = on) }
                }
            }
            // (D-66) El metrónomo configurable es la fuente de verdad del volumen, el acento y la
            // subdivisión; `metronomeEnabled` se sigue escuchando porque es el atajo del interruptor.
            viewModelScope.launch {
                prefs.tabMetronome.collect { metronome ->
                    _uiState.update {
                        it.copy(metronome = metronome, metronomeEnabled = metronome.enabled)
                    }
                }
            }
            viewModelScope.launch {
                prefs.tabMixer.collect { mixer ->
                    _uiState.update { it.copy(mixer = mixer) }
                }
            }
            // (D-48) Los ajustes del pentagrama son una preferencia GLOBAL: se recuperan al abrir el
            // editor para que el usuario no tenga que volver a elegirlos en cada canto.
            viewModelScope.launch {
                prefs.tablatureStaffOptions.collect { options ->
                    _uiState.update { it.copy(editorStaffOptions = options) }
                }
            }
        }
    }

    fun processIntent(intent: TablatureUiIntent) {
        when (intent) {
            is TablatureUiIntent.LoadForSong -> loadForSong(intent.songId, intent.songTitle, intent.songKey)
            TablatureUiIntent.ClearSong -> clearSong()
            is TablatureUiIntent.OpenNewEditor -> openNewEditor()
            is TablatureUiIntent.RequestImportTablature -> requestImportTablature()
            is TablatureUiIntent.ImportTablatureFromFile ->
                importTablature(intent.fileUri, intent.fileName)
            is TablatureUiIntent.ShareTablature -> shareTablature(intent.tablatureId)
            is TablatureUiIntent.OpenEditor -> openEditor(intent.tablatureId, asCopy = false)
            is TablatureUiIntent.DuplicateTablature -> openEditor(intent.tablatureId, asCopy = true)
            is TablatureUiIntent.UpdateEditorTitle ->
                _uiState.update { it.copy(editorTitle = intent.title, errorMessage = null) }
            is TablatureUiIntent.SetEditorSection ->
                _uiState.update { it.copy(editorSectionId = intent.sectionId, errorMessage = null) }
            is TablatureUiIntent.MoveTablatureToSection ->
                moveTablatureToSection(intent.tablatureId, intent.sectionId)
            is TablatureUiIntent.MoveTablatureOrder ->
                moveTablatureOrder(intent.tablatureId, intent.delta)
            is TablatureUiIntent.SelectTablature ->
                _uiState.update { it.copy(selectedTablatureId = intent.tablatureId) }
            is TablatureUiIntent.SetEditorWrittenKey ->
                _uiState.update { it.copy(editorWrittenKey = intent.key, errorMessage = null) }
            is TablatureUiIntent.UpdateEditorLabel ->
                _uiState.update { it.copy(editorLabel = intent.label, errorMessage = null) }
            is TablatureUiIntent.UpdateEditorMoment ->
                _uiState.update { it.copy(editorMoment = intent.moment, errorMessage = null) }
            is TablatureUiIntent.UpdateEditorNote ->
                _uiState.update { it.copy(editorNote = intent.note, errorMessage = null) }
            is TablatureUiIntent.SetTuning -> setTuning(intent.tuning)
            is TablatureUiIntent.OpenStringDataDialog ->
                _uiState.update { it.copy(showStringDataDialog = true) }
            is TablatureUiIntent.CloseStringDataDialog ->
                _uiState.update { it.copy(showStringDataDialog = false) }
            is TablatureUiIntent.SetTuningFrets ->
                setTuning(TabStringData.withFrets(_uiState.value.editorTuning, intent.frets))
            is TablatureUiIntent.SetStringPitch -> setTuning(
                TabStringData.withPitch(
                    _uiState.value.editorTuning,
                    intent.stringNumber,
                    intent.pitch,
                    intent.useFlat
                )
            )
            is TablatureUiIntent.SetStringAlwaysOpen -> setTuning(
                TabStringData.withAlwaysOpen(
                    _uiState.value.editorTuning,
                    intent.stringNumber,
                    intent.alwaysOpen
                )
            )
            is TablatureUiIntent.AddTuningString -> setTuning(
                TabStringData.addString(
                    _uiState.value.editorTuning,
                    intent.afterStringNumber,
                    intent.pitch
                )
            )
            is TablatureUiIntent.RemoveTuningString -> setTuning(
                TabStringData.removeString(_uiState.value.editorTuning, intent.stringNumber)
            )
            is TablatureUiIntent.SetTimeSignature -> setTimeSignature(intent.time)
            is TablatureUiIntent.SetResolution -> setResolution(intent.resolution)
            is TablatureUiIntent.SelectSlot ->
                _uiState.update { it.copy(selectedSlot = intent.measureIndex to intent.slotIndex, fretBuffer = "") }
            is TablatureUiIntent.TypeFretDigit -> typeFretDigit(intent.digit)
            is TablatureUiIntent.WriteFretAndAdvance -> writeFretAndAdvance(intent.fret)
            is TablatureUiIntent.MoveCursor -> moveCursor(intent.measureDelta, intent.slotDelta, intent.stringDelta)
            is TablatureUiIntent.BackspaceCursor -> backspaceCursor()
            is TablatureUiIntent.DeleteAtCursor -> deleteAtCursor()
            is TablatureUiIntent.ClearFretBuffer -> _uiState.update { it.copy(fretBuffer = "") }
            is TablatureUiIntent.ToggleInputMode -> toggleInputMode()
            is TablatureUiIntent.LeaveInputMode -> leaveInputMode()
            is TablatureUiIntent.PlaybackToggle -> togglePlayback()
            is TablatureUiIntent.ToggleMetronome -> updateMetronome {
                it.copy(enabled = !it.enabled)
            }
            is TablatureUiIntent.SetMetronomeVolume -> updateMetronome {
                it.copy(
                    volume = intent.volume.coerceIn(
                        com.grafitto.laudate.domain.tablature.TabMetronome.MIN_VOLUME,
                        com.grafitto.laudate.domain.tablature.TabMetronome.MAX_VOLUME
                    )
                )
            }
            is TablatureUiIntent.ToggleMetronomeAccent -> updateMetronome {
                it.copy(accentFirst = !it.accentFirst)
            }
            is TablatureUiIntent.SetMetronomeSubdivision -> updateMetronome {
                it.copy(
                    subdivision = intent.subdivision.coerceIn(
                        com.grafitto.laudate.domain.tablature.TabMetronome.MIN_SUBDIVISION,
                        com.grafitto.laudate.domain.tablature.TabMetronome.MAX_SUBDIVISION
                    )
                )
            }
            is TablatureUiIntent.SetMixerVolume -> updateMixer {
                it.copy(volume = intent.volume.coerceIn(TabMixer.MIN_VOLUME, TabMixer.MAX_VOLUME))
            }
            is TablatureUiIntent.SetMixerPan -> updateMixer {
                it.copy(pan = intent.pan.coerceIn(TabMixer.MIN_PAN, TabMixer.MAX_PAN))
            }
            is TablatureUiIntent.ToggleMixerMute -> updateMixer { it.copy(muted = !it.muted) }
            is TablatureUiIntent.ToggleMixerSolo -> updateMixer { it.copy(solo = !it.solo) }
            is TablatureUiIntent.ToggleLoopRange -> toggleLoopRange()
            is TablatureUiIntent.SetActiveVoice ->
                // Una voz **fuera de rango cae en la voz 1** (no en la última): es el mismo criterio
                // que `TabVoiceColors.of()`, para que el cursor nunca se quede sin color ni «salte»
                // a una voz que el usuario no ha pedido.
                _uiState.update {
                    it.copy(
                        activeVoice = if (intent.voice in 1..TabVoiceColors.VOICE_COUNT) {
                            intent.voice
                        } else {
                            1
                        }
                    )
                }
            is TablatureUiIntent.SetSlotVoice -> updateMeasures {
                TablatureEditing.setSlotVoice(it, intent.measureIndex, intent.slotIndex, intent.voice)
            }
            is TablatureUiIntent.SetVoiceInRange -> setVoiceInRange(intent.voice)
            is TablatureUiIntent.NudgeFret -> nudgeFret(intent.delta)
            is TablatureUiIntent.NudgeFretAcrossStrings -> nudgeFretAcrossStrings(intent.delta)
            is TablatureUiIntent.SetFigureForCursor -> setFigureForCursor(intent.figure)
            is TablatureUiIntent.AdjustFigure -> adjustFigure(intent.delta)
            is TablatureUiIntent.ToggleDotAtCursor -> toggleDotAtCursor()
            is TablatureUiIntent.InsertRestAtCursorShortcut -> insertRestAtCursorAtCursor()
            is TablatureUiIntent.ToggleGhostNoteAtCursor -> toggleGhostNote()
            is TablatureUiIntent.ApplyArticulationAtCursor ->
                applyArticulationAtCursor(intent.articulation)
            is TablatureUiIntent.ToggleTechnique -> toggleTechniqueAtCursor(intent.technique)
            is TablatureUiIntent.TapSlot ->
                tapSlot(intent.measureIndex, intent.slotIndex, intent.stringNumber)
            is TablatureUiIntent.SetSlotFret -> setSlotFret(intent.measureIndex, intent.slotIndex, intent.stringNumber, intent.fret)
            is TablatureUiIntent.RemovePluck -> updateMeasures {
                TablatureEditing.removePluck(it, intent.measureIndex, intent.slotIndex, intent.stringNumber)
            }
            is TablatureUiIntent.ClearSlot -> updateMeasures {
                TablatureEditing.clearSlot(it, intent.measureIndex, intent.slotIndex)
            }
            is TablatureUiIntent.ToggleHeld -> updateMeasures {
                TablatureEditing.toggleHeld(it, intent.measureIndex, intent.slotIndex)
            }
            is TablatureUiIntent.ToggleTremolo -> updateMeasures {
                TablatureEditing.toggleTremolo(it, intent.measureIndex, intent.slotIndex)
            }
            is TablatureUiIntent.SetTechnique -> updateMeasures {
                TablatureEditing.setTechnique(
                    it, intent.measureIndex, intent.slotIndex, intent.stringNumber, intent.technique
                )
            }
            is TablatureUiIntent.SetTremoloStrokes -> updateMeasures {
                TablatureEditing.setTremoloStrokes(
                    it, intent.measureIndex, intent.slotIndex, intent.stringNumber, intent.strokes
                )
            }
            is TablatureUiIntent.SetBendAmount -> updateMeasures {
                TablatureEditing.setBendAmount(
                    it, intent.measureIndex, intent.slotIndex, intent.stringNumber, intent.amount
                )
            }
            is TablatureUiIntent.SetPalmMuteSpan -> updateMeasures {
                TablatureEditing.setPalmMuteSpan(
                    it, intent.measureIndex, intent.slotIndex, intent.stringNumber, intent.span
                )
            }
            is TablatureUiIntent.SetFrettingFinger -> updateMeasures {
                TablatureEditing.setFrettingFinger(
                    it, intent.measureIndex, intent.slotIndex, intent.stringNumber, intent.finger
                )
            }
            is TablatureUiIntent.SetPickingFinger -> updateMeasures {
                TablatureEditing.setPickingFinger(
                    it, intent.measureIndex, intent.slotIndex, intent.stringNumber, intent.finger
                )
            }
            is TablatureUiIntent.SetRhythmicFigureAtCursor -> {
                val slot = _uiState.value.selectedSlot
                if (slot != null) updateMeasures { measures ->
                    // Sobre una casilla **vacía** la figura elegida es un **silencio** de esa duración
                    // (como en MuseScore: pulsar «negra» sin nota seleccionada inserta un silencio de
                    // negra). Sobre una casilla **con contenido** es la duración de la nota.
                    val current = measures.getOrNull(slot.first)?.slots?.getOrNull(slot.second)
                    val empty = current == null || (current.strings.isEmpty() && !current.isHeld)
                    val figure = if (empty) {
                        intent.figure?.let { RhythmFigure.restEquivalentOf(it) ?: it }
                    } else {
                        intent.figure
                    }
                    val withFigure = TablatureEditing.setRhythmicFigure(measures, slot.first, slot.second, figure)
                    // (D-86) **La figura elegida no puede hacer que el compás se pase.** Antes se
                    // aplicaba y `rebalance` solo rellenaba lo que faltaba, así que alargar una nota
                    // alargaba el compás sin límite —de ahí «¿cuántas blancas caben en un compás de
                    // 4/4?, ¿cuatrocientas mil?»—. `diagnose` lo detectaba (`TOO_LONG`) pero nadie
                    // actuaba. Ahora, si el compás no es anacrusa ni irregular y la figura nueva lo
                    // haría desbordar, se aplica igualmente **pero no se toca el compás**: la figura
                    // se descarta y se deja la anterior, que es lo que hace MuseScore al no caber.
                    val state = _uiState.value
                    val candidate = TablatureEditing.rebalanceMeasure(
                        withFigure, slot.first, state.editorTime, state.editorResolution, intent.figure
                    )
                    val measure = candidate.getOrNull(slot.first)
                        ?: return@updateMeasures candidate
                    val fits = measure.isPickup || measure.isIrregular ||
                        TabMeasureMeter.durationOf(measure, state.editorResolution) <=
                        state.editorTime.totalQuarterBeats + 1e-6
                    if (fits) candidate else measures
                }
                // La figura elegida queda **activa**: es la duración de lo que se escriba después
                // (modo de duración de MuseScore/Guitar Pro), no solo de esta casilla.
                _uiState.update { it.copy(selectedFigure = intent.figure) }
            }
            is TablatureUiIntent.ToggleTripletAtCursor -> {
                val slot = _uiState.value.selectedSlot
                if (slot != null) updateMeasures {
                    TablatureEditing.toggleTripletGroup(it, slot.first, slot.second)
                }
            }
            is TablatureUiIntent.SetTupletGroupAtCursor -> {
                val slot = _uiState.value.selectedSlot
                if (slot != null) updateMeasures {
                    TablatureEditing.toggleTupletGroup(
                        it,
                        slot.first,
                        slot.second,
                        TabTuplet(intent.numNotes, intent.normalNotes)
                    )
                }
            }
            is TablatureUiIntent.SetTupletNumberTypeAtCursor -> {
                val slot = _uiState.value.selectedSlot
                if (slot != null) updateMeasures {
                    TablatureEditing.setTupletNumberType(
                        it,
                        slot.first,
                        slot.second,
                        intent.numberType
                    )
                }
            }
            is TablatureUiIntent.SetTupletBracketTypeAtCursor -> {
                val slot = _uiState.value.selectedSlot
                if (slot != null) updateMeasures {
                    TablatureEditing.setTupletBracketType(
                        it,
                        slot.first,
                        slot.second,
                        intent.bracketType
                    )
                }
            }
            is TablatureUiIntent.CycleRepeat -> updateMeasures {
                TablatureEditing.cycleRepeat(it, intent.measureIndex)
            }
            is TablatureUiIntent.SetRepeatMark -> updateMeasures {
                TablatureEditing.setRepeat(it, intent.measureIndex, intent.mark)
            }
            is TablatureUiIntent.SetRepeatCount -> updateMeasures {
                TablatureEditing.setRepeatCount(it, intent.measureIndex, intent.count)
            }
            is TablatureUiIntent.SetBarline -> updateMeasures {
                TablatureEditing.setBarline(it, intent.measureIndex, intent.barline)
            }
            is TablatureUiIntent.CycleEnding -> updateMeasures {
                TablatureEditing.cycleEnding(it, intent.measureIndex)
            }
            is TablatureUiIntent.SetEnding -> updateMeasures {
                TablatureEditing.setEnding(it, intent.measureIndex, intent.ending)
            }
            is TablatureUiIntent.SetEndingRange -> updateMeasures {
                TablatureEditing.setEndingRange(it, intent.measureIndex, intent.from, intent.to)
            }
            is TablatureUiIntent.SetEndingText -> updateMeasures {
                TablatureEditing.setEndingText(it, intent.measureIndex, intent.text)
            }
            is TablatureUiIntent.SetNavigation -> updateMeasures {
                TablatureEditing.setNavigation(it, intent.measureIndex, intent.mark)
            }
            is TablatureUiIntent.SetJump -> updateMeasures {
                TablatureEditing.setJump(it, intent.measureIndex, intent.jump)
            }
            is TablatureUiIntent.SetDynamic -> updateMeasures {
                TablatureEditing.setDynamic(it, intent.measureIndex, intent.dynamic)
            }
            is TablatureUiIntent.SetHairpin -> updateMeasures {
                TablatureEditing.setHairpin(it, intent.measureIndex, intent.hairpin)
            }
            is TablatureUiIntent.AddGrace -> updateMeasures {
                TablatureEditing.addGrace(
                    it,
                    intent.measureIndex,
                    intent.slotIndex,
                    intent.type,
                    intent.stringNumber
                )
            }
            is TablatureUiIntent.SetGraceFret -> updateMeasures {
                TablatureEditing.setGraceFret(
                    it,
                    intent.measureIndex,
                    intent.slotIndex,
                    intent.graceIndex,
                    intent.fret
                )
            }
            is TablatureUiIntent.SetGraceString -> updateMeasures {
                TablatureEditing.setGraceString(
                    it,
                    intent.measureIndex,
                    intent.slotIndex,
                    intent.graceIndex,
                    intent.stringNumber
                )
            }
            is TablatureUiIntent.RemoveGrace -> updateMeasures {
                TablatureEditing.removeLastGrace(it, intent.measureIndex, intent.slotIndex)
            }
            is TablatureUiIntent.ClearGraces -> updateMeasures {
                TablatureEditing.clearGraces(it, intent.measureIndex, intent.slotIndex)
            }
            is TablatureUiIntent.ToggleArticulation -> updateMeasures {
                TablatureEditing.toggleArticulation(
                    it, intent.measureIndex, intent.slotIndex, intent.stringNumber, intent.articulation
                )
            }
            is TablatureUiIntent.SetAccidental -> updateMeasures {
                TablatureEditing.setAccidental(
                    it, intent.measureIndex, intent.slotIndex, intent.stringNumber, intent.accidental
                )
            }
            is TablatureUiIntent.ToggleTie -> updateMeasures {
                TablatureEditing.toggleTie(
                    it, intent.measureIndex, intent.slotIndex, intent.stringNumber
                )
            }
            is TablatureUiIntent.ToggleSlur -> updateMeasures {
                TablatureEditing.toggleSlur(it, intent.measureIndex, intent.slotIndex)
            }
            is TablatureUiIntent.SetOctaveShift -> updateMeasures {
                TablatureEditing.setOctaveShift(it, intent.measureIndex, intent.shift)
            }
            is TablatureUiIntent.SetTempo -> updateMeasures {
                TablatureEditing.setTempo(it, intent.measureIndex, intent.bpm)
            }
            is TablatureUiIntent.SetMeasureText -> updateMeasures {
                TablatureEditing.setText(it, intent.measureIndex, intent.text)
            }
            is TablatureUiIntent.AddMeasure -> {
                val state = _uiState.value
                val next = TablatureEditing.addMeasure(state.editorMeasures, state.editorTime, state.editorResolution)
                pushHistory(state.editorMeasures)
                _uiState.update {
                    it.copy(
                        editorMeasures = next,
                        selectedSlot = next.lastIndex to 0,
                        canUndo = true,
                        canRedo = false
                    )
                }
            }
            is TablatureUiIntent.AddMeasures -> {
                val state = _uiState.value
                val count = intent.count.coerceIn(1, MAX_APPENDED_MEASURES)
                var next = state.editorMeasures
                repeat(count) {
                    next = TablatureEditing.addMeasure(next, state.editorTime, state.editorResolution)
                }
                pushHistory(state.editorMeasures)
                _uiState.update {
                    it.copy(
                        editorMeasures = next,
                        selectedSlot = next.lastIndex to 0,
                        canUndo = true,
                        canRedo = false
                    )
                }
            }
            is TablatureUiIntent.RemoveMeasure -> {
                val state = _uiState.value
                val next = TablatureEditing.removeMeasure(state.editorMeasures)
                if (next != state.editorMeasures) {
                    pushHistory(state.editorMeasures)
                    _uiState.update {
                        it.copy(
                            editorMeasures = next,
                            selectedSlot = it.selectedSlot?.let { (m, s) ->
                                m.coerceIn(0, next.lastIndex) to s
                            },
                            canUndo = true,
                            canRedo = false
                        )
                    }
                }
            }
            is TablatureUiIntent.InsertMeasureBefore -> insertMeasureAt(intent.measureIndex)
            is TablatureUiIntent.InsertMeasureAfter -> insertMeasureAt(intent.measureIndex + 1)
            is TablatureUiIntent.InsertMeasuresBefore ->
                insertMeasureAt(intent.measureIndex, intent.count)
            is TablatureUiIntent.DuplicateMeasure -> duplicateMeasure(intent.measureIndex)
            is TablatureUiIntent.DeleteMeasure -> deleteMeasure(intent.measureIndex)
            is TablatureUiIntent.DeleteMeasures -> deleteMeasures(intent.range)
            is TablatureUiIntent.SetMeasureSubdivision ->
                setMeasureSubdivision(intent.measureIndex, intent.resolution)
            is TablatureUiIntent.TogglePickup -> togglePickup()
            is TablatureUiIntent.SetMeasureTime -> setMeasureTime(intent.measureIndex, intent.time)
            is TablatureUiIntent.ToggleIrregular -> toggleIrregular(intent.measureIndex)
            is TablatureUiIntent.ClearMeasures -> clearMeasures(intent.range)
            is TablatureUiIntent.MoveMeasures -> moveMeasures(intent.range, intent.to)
            is TablatureUiIntent.ToggleSystemBreak -> toggleSystemBreak(intent.measureIndex)
            is TablatureUiIntent.SetSystemBreak -> updateMeasures {
                TablatureEditing.setForceSystemBreak(it, intent.measureIndex, intent.force)
            }
            is TablatureUiIntent.TogglePageBreak -> togglePageBreak(intent.measureIndex)
            is TablatureUiIntent.SetPageBreak -> updateMeasures {
                TablatureEditing.setForcePageBreak(it, intent.measureIndex, intent.force)
            }
            // ── Selección de rango, portapapeles y deshacer/rehacer (E1-E3) ──────────
            is TablatureUiIntent.SelectMeasureRange -> _uiState.update { state ->
                val selection = com.grafitto.laudate.domain.tablature.TablatureSelection(
                    if (intent.anchor <= intent.to) intent.anchor..intent.to else intent.to..intent.anchor
                ).clamped(state.editorMeasures.size)
                state.copy(selection = selection)
            }
            is TablatureUiIntent.ClearSelection -> _uiState.update { it.copy(selection = null) }
            is TablatureUiIntent.SelectMeasureAt -> selectMeasureAt(intent.measureIndex)
            is TablatureUiIntent.GoToMeasure -> goToMeasure(intent.measureIndex)
            is TablatureUiIntent.ExtendMeasureSelection -> extendMeasureSelection(intent.delta)
            is TablatureUiIntent.ZoomIn -> zoomBy(ZOOM_STEP)
            is TablatureUiIntent.ZoomOut -> zoomBy(1f / ZOOM_STEP)
            is TablatureUiIntent.ZoomReset -> processIntent(TablatureUiIntent.SetTablatureScale(1f))
            // ── Paneles laterales (B-11, B-12, B-14) ──────────────────────────────────
            is TablatureUiIntent.TogglePalettes -> _uiState.update { it.copy(showPalettes = !it.showPalettes) }
            is TablatureUiIntent.ToggleProperties -> _uiState.update { it.copy(showProperties = !it.showProperties) }
            // ── Selección de notas (Fase 2, ADR-020) ──────────────────────────────────
            is TablatureUiIntent.SelectNote -> _uiState.update {
                it.copy(
                    noteSelection = TabNoteSelection().single(intent.ref),
                    noteSelectionAnchor = intent.ref,
                    noteSelectionFocus = intent.ref
                )
            }
            is TablatureUiIntent.ToggleNoteSelection -> _uiState.update { state ->
                val current = state.noteSelection ?: TabNoteSelection()
                state.copy(
                    noteSelection = current.toggled(intent.ref),
                    // El `Ctrl`+clic **reinicia** el ancla: el siguiente `Mayús`+flecha debe crecer
                    // desde la nota que se acaba de tocar, no desde la primera de la selección.
                    noteSelectionAnchor = intent.ref,
                    noteSelectionFocus = intent.ref
                )
            }
            is TablatureUiIntent.ExtendNoteSelection -> _uiState.update { state ->
                val anchor = state.noteSelection
                    ?.notes?.minWithOrNull(compareBy({ it.measureIndex }, { it.slotIndex }))
                val extended = if (anchor == null || anchor.measureIndex != intent.measureIndex) {
                    TabNoteSelection().single(intent.ref)
                } else {
                    TabNoteSelection.range(
                        intent.measureIndex, anchor.slotIndex, intent.slotIndex, intent.stringNumber
                    )
                }
                state.copy(
                    noteSelection = extended,
                    noteSelectionAnchor = anchor ?: intent.ref,
                    noteSelectionFocus = intent.ref
                )
            }
            is TablatureUiIntent.SelectNotesInMeasure ->
                _uiState.update { it.copy(noteSelection = TabNoteSelection.ofMeasure(it.editorMeasures, intent.measureIndex)) }
            is TablatureUiIntent.ExtendNoteSelectionRelative -> extendNoteSelectionRelative(intent.delta)
            is TablatureUiIntent.ExtendNoteSelectionRect ->
                extendNoteSelectionRect(intent.deltaSlot, intent.deltaString)
            is TablatureUiIntent.SelectAllNotes ->
                _uiState.update { it.copy(noteSelection = TabNoteSelection.ofDocument(it.editorMeasures)) }
            is TablatureUiIntent.SetNoteSelection -> _uiState.update {
                val first = intent.notes.minWithOrNull(
                    compareBy({ it.measureIndex }, { it.slotIndex }, { it.stringNumber })
                )
                it.copy(
                    noteSelection = if (intent.notes.isEmpty()) null else TabNoteSelection(intent.notes),
                    noteSelectionAnchor = first,
                    noteSelectionFocus = first
                )
            }
            is TablatureUiIntent.ClearNoteSelection -> _uiState.update {
                it.copy(noteSelection = null, noteSelectionAnchor = null, noteSelectionFocus = null)
            }
            is TablatureUiIntent.DeleteSelectedNotes -> deleteSelectedNotes()
            is TablatureUiIntent.CopySelectedNotes -> copySelectedNotes(cut = false)
            is TablatureUiIntent.CutSelectedNotes -> copySelectedNotes(cut = true)
            is TablatureUiIntent.PasteNotes -> pasteNotes()
            is TablatureUiIntent.DeleteSelection -> deleteSelection()
            is TablatureUiIntent.CopySelection -> copySelection(cut = false)
            is TablatureUiIntent.CutSelection -> copySelection(cut = true)
            is TablatureUiIntent.DuplicateSelection -> duplicateSelection()
            is TablatureUiIntent.PasteClipboard -> pasteClipboard()
            is TablatureUiIntent.SwapSelection -> swapSelection()
            is TablatureUiIntent.Undo -> undo()
            is TablatureUiIntent.Redo -> redo()
            is TablatureUiIntent.InsertColumnAtCursor -> insertColumnAtCursor(intent.measureIndex, intent.slotIndex)
            is TablatureUiIntent.InsertColumnAtCursorNow -> {
                val slot = _uiState.value.selectedSlot ?: (0 to 0)
                insertColumnAtCursor(slot.first, slot.second)
            }
            is TablatureUiIntent.RemoveColumnAt -> removeColumnAt(intent.measureIndex, intent.slotIndex)
            is TablatureUiIntent.RemoveColumnAtCursor -> _uiState.value.selectedSlot
                ?.let { removeColumnAt(it.first, it.second) }
            is TablatureUiIntent.InsertBarAfter -> insertBarAfter(intent.measureIndex, intent.slotIndex)
            is TablatureUiIntent.MoveNoteString -> moveNoteString(intent.direction)
            is TablatureUiIntent.DragNoteToAdjacentString -> dragNoteToAdjacentString(intent.stringDelta)
            is TablatureUiIntent.SaveTablature -> saveTablature()
            is TablatureUiIntent.DeleteTablature -> deleteTablature(intent.tablatureId)
            is TablatureUiIntent.DeleteEditingTablature -> {
                val id = _uiState.value.editingId
                // Cerrar el editor devuelve al **listado** de tablaturas del canto (no deja la
                // pestaña en el editor vacío). `baselineTablature` se limpia para no dejar cambios
                // pendientes colgando.
                _uiState.update {
                    it.copy(isEditorOpen = false, editingId = null, baselineTablature = null, showDiscardDialog = false)
                }
                if (id != null) deleteTablature(id)
            }
            is TablatureUiIntent.CloseEditor -> _uiState.update {
                it.copy(isEditorOpen = false, editingId = null, baselineTablature = null, showDiscardDialog = false)
            }
            is TablatureUiIntent.ApplyTransposition -> applyTransposition(intent.tablatureId, intent.mode)
            is TablatureUiIntent.SetTranspositionMode -> _uiState.update { it.copy(transpositionMode = intent.mode) }
            is TablatureUiIntent.ConfirmRefinger -> confirmRefinger()
            is TablatureUiIntent.CancelRefinger -> _uiState.update { it.copy(pendingRefinger = null) }
            is TablatureUiIntent.RefingerToTuning -> refingerToTuning(intent.tablatureId, intent.tuning)
            is TablatureUiIntent.RefingerToSongKey -> refingerEditorToSongKey()
            is TablatureUiIntent.RequestExit -> {
                if (_uiState.value.hasUnsavedChanges) {
                    _uiState.update { it.copy(showDiscardDialog = true) }
                } else {
                    _uiState.update { it.copy(isEditorOpen = false, editingId = null, baselineTablature = null) }
                }
            }
            is TablatureUiIntent.ConfirmDiscard -> _uiState.update {
                it.copy(isEditorOpen = false, editingId = null, baselineTablature = null, showDiscardDialog = false)
            }
            is TablatureUiIntent.DismissDiscard -> _uiState.update { it.copy(showDiscardDialog = false) }
            is TablatureUiIntent.SetTablatureScale -> {
                _uiState.update { it.copy(tablatureScale = intent.scale) }
                viewModelScope.launch { preferencesRepository?.setTablatureScale(intent.scale) }
            }
            // **Tamaño de las cifras** sin tocar la rejilla. Es la misma preferencia global que usa
            // el visor: si en el editor se agrandara la cifra y al leer el canto volviera a 1,0, el
            // ajuste no serviría para preparar el atril.
            is TablatureUiIntent.SetTablatureNumberScale -> {
                val scale = intent.scale.coerceIn(MIN_NUMBER_SCALE, MAX_NUMBER_SCALE)
                _uiState.update { it.copy(tablatureNumberScale = scale) }
                viewModelScope.launch { preferencesRepository?.setTablatureNumberScale(scale) }
            }
            is TablatureUiIntent.SetStaffOptions -> {
                _uiState.update { it.copy(editorStaffOptions = intent.options) }
                // (D-48) Se persiste: el ajuste vale también para el visor y para el PDF exportado.
                viewModelScope.launch {
                    preferencesRepository?.setTablatureStaffOptions(intent.options)
                }
            }
            // ── Métrica y relleno de compases (Fase B.1, port de TuxGuitar) ──────────
            is TablatureUiIntent.InsertRestAtCursor -> insertRestAtCursor(intent.figure)
            is TablatureUiIntent.CompleteMeasureAt -> completeMeasureAt(intent.measureIndex)
            is TablatureUiIntent.CompleteAllMeasures -> completeAllMeasures()
        }
    }

    /**
     * **Inserta un silencio** de la figura [figure] en la columna del cursor (o al final del compás si
     * no hay cursor). Desplaza lo que venga después, como cualquier otra columna.
     */
    private fun insertRestAtCursor(figure: com.grafitto.laudate.domain.model.rhythm.RhythmFigure) {
        val state = _uiState.value
        val fallbackSize = state.editorMeasures.firstOrNull()?.slots?.size ?: 0
        val target: Pair<Int, Int> = state.selectedSlot ?: (0 to fallbackSize)
        val measureIndex = target.first
        val slotIndex = target.second
        val measure = state.editorMeasures.getOrNull(measureIndex) ?: return
        val updated = com.grafitto.laudate.domain.tablature.TabMeasureMeter
            .insertRest(measure, figure, slotIndex, state.editorResolution)
            .let { withRest ->
                // El silencio pertenece a la **voz activa**, como en MuseScore: un silencio de la voz 2
                // no tapa la voz 1.
                withRest.copy(
                    slots = withRest.slots.mapIndexed { index, slot ->
                        if (index == slotIndex) slot.copy(voice = state.activeVoice) else slot
                    }
                )
            }
        pushHistory(state.editorMeasures)
        _uiState.update {
            it.copy(
                editorMeasures = it.editorMeasures.toMutableList().also { list -> list[measureIndex] = updated },
                canUndo = true, canRedo = false
            )
        }
    }

    /** **Completa el compás** [measureIndex] con los silencios que le falten (Fase B.1). */
    private fun completeMeasureAt(measureIndex: Int) {
        val state = _uiState.value
        val measure = state.editorMeasures.getOrNull(measureIndex) ?: return
        val completed = com.grafitto.laudate.domain.tablature.TabMeasureMeter
            .fillWithRests(measure, state.editorTime, state.editorResolution)
        if (completed == measure) return
        pushHistory(state.editorMeasures)
        _uiState.update {
            it.copy(
                editorMeasures = it.editorMeasures.toMutableList().also { list -> list[measureIndex] = completed },
                canUndo = true, canRedo = false
            )
        }
    }

    /** **Completa con silencios todos los compases** que estén cortos (Fase B.1). */
    private fun completeAllMeasures() {
        val state = _uiState.value
        val completed = com.grafitto.laudate.domain.tablature.TabMeasureMeter
            .fillAll(state.editorMeasures, state.editorTime, state.editorResolution)
        if (completed == state.editorMeasures) return
        pushHistory(state.editorMeasures)
        _uiState.update { it.copy(editorMeasures = completed, canUndo = true, canRedo = false) }
    }

    /** Guarda el estado actual del editor como referencia de «sin cambios pendientes». */
    private fun MutableStateFlow<TablatureUiState>.markEditorBaseline() {
        update { state -> state.copy(baselineTablature = state.editorAsTablature()) }
    }

    /** **Reinicia el historial** (deshacer/rehacer) y la selección al abrir un editor. */
    private fun resetHistory() {
        undoStack.clear()
        redoStack.clear()
        _uiState.update { it.copy(canUndo = false, canRedo = false, selection = null) }
    }

    // ── Carga ──────────────────────────────────────────────────────────────────

    private fun loadForSong(songId: String, songTitle: String, songKey: String) {
        // Al cambiar de canto se vacía la lista de inmediato: si no, durante un instante se verían
        // las tablaturas del canto anterior sobre el nuevo.
        _uiState.update { state ->
            state.copy(
                isLoading = true,
                songId = songId,
                songTitle = songTitle,
                songKey = songKey,
                tablatures = if (state.songId == songId) state.tablatures else emptyList()
            )
        }
        // Un solo colector vivo: el del canto que se está viendo. Sin esto, cada canto abierto dejaba
        // su colector emitiendo y las tablaturas de uno acababan pintándose en los demás.
        tablaturesJob?.cancel()
        tablaturesJob = viewModelScope.launch {
            tablatureRepository.observeTablaturesForSong(songId).collect { list ->
                // Se descarta cualquier emisión que llegue tarde, cuando ya se abrió otro canto.
                if (_uiState.value.songId != songId) return@collect
                _uiState.update { it.copy(tablatures = list, isLoading = false) }
            }
        }
    }

    /**
     * Suelta el canto abierto: se cancela su colector y el estado deja de tener tablaturas y
     * `songId`. Lo usa la pantalla cuando ya no hay canto (un canto nuevo sin guardar), para no
     * seguir arrastrando el del canto anterior.
     */
    private fun clearSong() {
        tablaturesJob?.cancel()
        tablaturesJob = null
        _uiState.update {
            it.copy(
                songId = "",
                songTitle = "",
                songKey = "",
                tablatures = emptyList(),
                isLoading = false,
                selectedTablatureId = null,
                editingId = null,
                isEditorOpen = false,
                editorIsNew = true
            )
        }
    }

    /**
     * Registra las secciones del canto (intro, estrofa, coro…) para poder asociar la tablatura a
     * una de ellas. Lo llama el contenedor que ya tiene el canto cargado.
     */
    fun setAvailableSections(sections: List<TablatureSectionRef>) {
        _uiState.update { it.copy(availableSections = sections) }
    }

    // ── Colocación de las tablaturas guardadas ─────────────────────────────────

    /**
     * **Mueve** una tablatura guardada a otra sección del canto (`null` = bloque general). La
     * sección viaja dentro del propio documento, así que basta con volver a guardarla.
     */
    private fun moveTablatureToSection(tablatureId: String, sectionId: String?) {
        viewModelScope.launch {
            val tablature = tablatureRepository.getTablatureById(tablatureId) ?: return@launch
            if (tablature.sectionId == sectionId) return@launch
            val songId = tablature.songId
            val siblings = tablatureRepository.getTablaturesForSong(songId)
                .filter { it.id != tablatureId && it.sectionId == sectionId }
            tablatureRepository.saveTablature(TablaturePlacement.moveToSection(tablature, sectionId, siblings))
        }
    }

    /**
     * **Reordena** una tablatura guardada dentro de su grupo (misma sección, o el bloque general),
     * renumerando el orden de 0 a n-1 para que quede estable y sin huecos.
     */
    private fun moveTablatureOrder(tablatureId: String, delta: Int) {
        if (delta == 0) return
        viewModelScope.launch {
            val tablature = tablatureRepository.getTablatureById(tablatureId) ?: return@launch
            val songId = tablature.songId
            val group = tablatureRepository.getTablaturesForSong(songId)
                .filter { it.sectionId == tablature.sectionId }
            val reordered = TablaturePlacement.reorder(group, tablatureId, delta)
            // `reorder` devuelve la misma lista si no había nada que mover: no se escribe en disco.
            if (reordered === group) return@launch
            reordered.forEach { tablatureRepository.saveTablature(it) }
        }
    }

    // ── Editor ─────────────────────────────────────────────────────────────────

    private fun openNewEditor() {
        val state = _uiState.value
        // La tonalidad escrita por defecto es la del canto (para que la tablatura siga su tono).
        val defaultKey = com.grafitto.laudate.domain.model.music.PitchClass.parse(state.songKey)
            ?.let { com.grafitto.laudate.domain.model.score.scoreKeyOfTonic(it.chromaticIndex) }
        _uiState.update {
            it.copy(
                isEditorOpen = true,
                editorIsNew = true,
                editingId = null,
                editorTitle = "",
                editorTuning = Tuning.GUITAR,
                editorTime = state.editorTime,
                editorResolution = state.editorResolution,
                editorMeasures = List(NEW_EDITOR_MEASURES) {
                    // Cada compás nace **completo y con rejilla**: una columna por unidad de la
                    // subdivisión, con su silencio. Así se ve dónde va cada pulso y se escribe encima.
                    TablatureEditing.emptyCompleteMeasure(state.editorTime, state.editorResolution)
                },
                selectedString = 1,
                selectedFret = 0,
                selectedSlot = 0 to 0,
                // La **figura activa** arranca en la negra, que es la duración por defecto de
                // MuseScore y Guitar Pro: la barra de escritura nunca aparece «sin duración».
                selectedFigure = RhythmFigure.QUARTER,
                editorWrittenKey = defaultKey,
                editorSectionId = null,
                editorLabel = "",
                editorMoment = "",
                editorNote = "",
                errorMessage = null
            )
        }
        resetHistory()
        _uiState.markEditorBaseline()
    }

    private fun openEditor(tablatureId: String, asCopy: Boolean) {
        viewModelScope.launch {
            val tab = tablatureRepository.getTablatureById(tablatureId)
            if (tab == null) {
                _sideEffect.send(TablatureSideEffect.ShowMessage("No se encontró la tablatura"))
                return@launch
            }
            _uiState.update {
                it.copy(
                    isEditorOpen = true,
                    editorIsNew = asCopy,
                    editingId = if (asCopy) null else tab.id,
                    editorTitle = if (asCopy) "${tab.title} (copia)" else tab.title,
                    editorTuning = tab.tuning,
                    editorTime = tab.time,
                    editorResolution = tab.resolution,
                    editorMeasures = tab.measures.ifEmpty {
                        List(NEW_EDITOR_MEASURES) {
                            TablatureEditing.emptyCompleteMeasure(tab.time, tab.resolution)
                        }
                    },
                    selectedString = 1,
                    selectedFret = 0,
                    selectedSlot = null,
                    selectedFigure = RhythmFigure.QUARTER,
                    editorWrittenKey = tab.writtenKey,
                    editorSectionId = tab.sectionId,
                    editorLabel = tab.label.orEmpty(),
                    editorMoment = tab.moment.orEmpty(),
                    editorNote = tab.note.orEmpty(),
                    transpositionMode = tab.transpositionMode
                        ?: com.grafitto.laudate.domain.tablature.TabTranspositionPolicy.defaultMode(tab),
                    pendingRefinger = null,
                    errorMessage = null
                )
            }
            resetHistory()
            _uiState.markEditorBaseline()
        }
    }

    private fun setTuning(tuning: Tuning) {
        _uiState.update { state ->
            // Si la cuerda seleccionada ya no existe en la nueva afinación, vuelve a la 1ª.
            val string = state.selectedString.coerceIn(1, tuning.stringCount)
            state.copy(editorTuning = tuning, selectedString = string, errorMessage = null)
        }
    }

    /**
     * Cambia el **compás** del editor.
     *
     * El compás forma parte del documento guardado ([Tablature.time]), así que el cambio entra en el
     * historial: antes se aplicaba sin apilar nada y `Ctrl+Z` no lo devolvía. La rejilla **no** se
     * redimensiona a propósito ([TablatureEditing.resize]): con el modelo de columnas las columnas son
     * libres y recortarlas al cambiar de compás borraría notas sin avisar.
     */
    private fun setTimeSignature(time: com.grafitto.laudate.domain.model.score.ScoreTimeSignature) {
        val state = _uiState.value
        if (time == state.editorTime) return
        pushHistory(state.editorMeasures)
        _uiState.update {
            it.copy(
                editorTime = time,
                editorMeasures = TablatureEditing.resize(state.editorMeasures, time, state.editorResolution),
                canUndo = true,
                canRedo = false,
                errorMessage = null
            )
        }
    }

    /**
     * Cambia la **resolución** de la rejilla. Igual que [setTimeSignature]: entra en el historial
     * porque forma parte del documento, y no recorta las columnas existentes.
     */
    private fun setResolution(resolution: TabResolution) {
        val state = _uiState.value
        if (resolution == state.editorResolution) return
        pushHistory(state.editorMeasures)
        _uiState.update {
            it.copy(
                editorResolution = resolution,
                editorMeasures = TablatureEditing.resize(state.editorMeasures, state.editorTime, resolution),
                canUndo = true,
                canRedo = false,
                errorMessage = null
            )
        }
    }

    /**
     * Toca una casilla de la rejilla. La fila tocada pasa a ser la **cuerda activa** y el cursor se
     * coloca en esa casilla.
     *
     * Con el **modo de entrada activo** (ADR-020), un toque sobre una cuerda **vacía** escribe un `0`
     * («cuerda al aire»), que es exactamente lo que hace MuseScore: «Click on a string and MuseScore
     * will enter a 0 (zero), which means "open string"». Con el modo **apagado**, el toque **solo mueve
     * el cursor** y nunca escribe, para que un roce no destruya lo escrito.
     *
     * **Sobre una cifra que ya existe el toque NO escribe** (D-67): solo la selecciona. Antes se
     * pisaba con un `0` y el cursor avanzaba, así que pulsar un `3` lo convertía en `0` y saltaba a la
     * casilla siguiente —el usuario lo describió como «casi siempre se activa la siguiente»—. MuseScore
     * hace lo mismo que se hace aquí: en modo de entrada, pulsar una nota existente la **selecciona**;
     * para cambiarla se teclea la cifra nueva.
     */
    private fun tapSlot(measureIndex: Int, slotIndex: Int, stringNumber: Int?) {
        val state = _uiState.value
        val string = stringNumber ?: state.selectedString
        // ¿Ya hay nota en esa cuerda de esa casilla? Entonces el toque **selecciona**, no escribe.
        val existing = state.editorMeasures.getOrNull(measureIndex)?.slots?.getOrNull(slotIndex)
            ?.strings?.firstOrNull { it.stringNumber == string }
        _uiState.update {
            it.copy(
                selectedSlot = measureIndex to slotIndex,
                selectedString = string,
                fretBuffer = "",
                selectedFret = existing?.fret ?: it.selectedFret,
                selectedFigure = figureAt(it.editorMeasures, measureIndex to slotIndex) ?: it.selectedFigure
            )
        }
        if (existing != null) {
            // Selección pura: se oye la nota y el cursor **no** avanza.
            soundAt(measureIndex, slotIndex, string)
            return
        }
        if (state.isInputMode) {
            // La figura activa se aplica a la nota que nace del toque, como al teclear una cifra.
            updateMeasures { measures ->
                // (D-93) Igual que al teclear: si la casilla es un silencio más largo, se parte.
                val target = state.selectedFigure ?: TablatureEditing.figureForResolution(state.editorResolution)
                val split = TablatureEditing.splitSlotForFigure(measures, measureIndex, slotIndex, target)
                    ?: measures
                val withFret = TablatureEditing.setFret(split, measureIndex, slotIndex, string, 0)
                val withFigure = state.selectedFigure?.let { figure ->
                    TablatureEditing.setRhythmicFigure(withFret, measureIndex, slotIndex, figure)
                } ?: withFret
                // La nota nace en la **voz activa** (Fase 3): el lienzo la pintará con su color.
                val withVoice = withFigure.withActiveVoice(measureIndex, slotIndex)
                TablatureEditing.rebalanceMeasure(
                    withVoice, measureIndex, state.editorTime, state.editorResolution, state.selectedFigure
                )
            }
            soundAt(measureIndex, slotIndex, string)
            _uiState.update { it.copy(selectedFret = 0) }
            advanceCursor()
        }
    }

    /**
     * Escribe un **dígito de traste** con el teclado físico, acumulándolo en el búfer (teclear «1» y
     * «2» da el traste 12). Escribe el resultado en la casilla y cuerda activas y, tras la pausa de
     * la cifra, **avanza** una columna: escribir es siempre escribir y seguir, como en Guitar Pro.
     * (Antes hacía falta activar un «modo de introducción» con la tecla `N` para que avanzara; el
     * modo era invisible y confundía más que ayudaba.)
     */
    private fun typeFretDigit(digit: Int) {
        val state = _uiState.value
        val slot = state.selectedSlot ?: (0 to 0)
        val buffer = TabKeyboardInput.appendDigit(state.fretBuffer, digit, MAX_FRET) ?: digit.toString()
        val fret = TabKeyboardInput.fretOf(buffer) ?: return
        updateMeasures { measures ->
            // (D-94) **El teclado fisico hace lo mismo que el de pantalla.** Antes solo ponia la
            // cifra encima del silencio que hubiera, sin partir la columna ni reequilibrar: en una
            // rejilla de corcheas, una negra metida en media corchea dejaba el compas en **4,5
            // negras** y con los siete silencios de corchea al final, que es lo que el usuario veia
            // como «salen silencios a lo loco» y «hace cosas raras» (2026-10-27). Los dos caminos de
            // escritura tienen que producir **el mismo** documento.
            val target = state.selectedFigure ?: TablatureEditing.figureForResolution(state.editorResolution)
            val split = TablatureEditing.setFigureAbsorbing(measures, slot.first, slot.second, target)
            val withFret = TablatureEditing.setFret(split, slot.first, slot.second, state.selectedString, fret)
            val withFigure = state.selectedFigure?.let { figure ->
                TablatureEditing.setRhythmicFigure(withFret, slot.first, slot.second, figure)
            } ?: withFret
            val withVoice = withFigure.withActiveVoice(slot.first, slot.second)
            TablatureEditing.rebalanceMeasure(
                withVoice, slot.first, state.editorTime, state.editorResolution, state.selectedFigure
            )
        }
        // **Desviación firmada (ADR-020)**: teclear una cifra con el modo **apagado** entra en el modo
        // de entrada y escribe. Ahorra un toque por nota en tableta; en escritorio no pisa ningún atajo
        // real de MuseScore en TAB, porque la duración se fija con `Shift`+`0`..`9`.
        _uiState.update {
            it.copy(isInputMode = true, selectedSlot = slot, selectedFret = fret, fretBuffer = buffer)
        }
        // El sonido se emite al **completar** la cifra: con el búfer de dos dígitos, teclear «1» y
        // «2» debe sonar una sola vez (traste 12), no primero 1 y luego 12. El job se cancela si el
        // usuario sigue tecleando, y es el mismo job el que **avanza el cursor** tras la pausa, de
        // modo que un traste de dos cifras no se corta.
        scheduleDigitSound(slot.first, slot.second, state.selectedString)
    }

    /**
     * **Teclado en pantalla**: acumula el dígito en el **mismo búfer de dos cifras** que el teclado
     * físico ([typeFretDigit]).
     *
     * Antes cada toque escribía **una cifra completa y avanzaba de columna**, así que era
     * materialmente imposible escribir un traste de dos dígitos («12», «17») con el teclado de la
     * barra: el usuario lo reportó («no me da tiempo a meter un número de dos dígitos»). Ahora tocar
     * «1» y luego «2» da el 12, y el cursor avanza cuando pasa el tiempo de la cifra, no antes.
     *
     * Unificar los dos caminos evita además que vuelvan a divergir: los dos tienen que producir el
     * **mismo** documento (lección de `tablature-editor-pitfalls`).
     */
    private fun writeFretAndAdvance(digit: Int) {
        typeFretDigit(digit)
    }

    /**
     * Programa el sonido de la nota escrita con el teclado con un pequeño retardo: si en ese margen
     * llega otro dígito (cifra de dos cifras), se cancela y suena una sola vez la cifra final. Al
     * completarse la cifra, el cursor **avanza** una columna.
     */
    private fun scheduleDigitSound(measureIndex: Int, slotIndex: Int, stringNumber: Int) {
        digitSoundJob?.cancel()
        digitSoundJob = viewModelScope.launch {
            delay(DIGIT_SOUND_DELAY_MS)
            soundAt(measureIndex, slotIndex, stringNumber)
            // Tras la pausa, el cursor **avanza solo** a la siguiente columna. Se hace aquí (y no al
            // teclear) para que un traste de dos cifras («12») no se corte: el segundo dígito cancela
            // el job y vuelve a programarlo.
            advanceCursor()
        }
    }

    // ── Reproducción de la partitura (B-9) ───────────────────────────────────────────────────────

    /**
     * **Reproduce o para** la partitura, como `Espacio` en MuseScore. La lógica musical (tempo,
     * figuras, tresillos, ligaduras, prolongaciones y repeticiones) vive en
     * [com.grafitto.laudate.domain.tablature.TabPlayback], que es puro; aquí solo se recorre la línea
     * de tiempo con `delay()` y se pide el sonido al reproductor de la plataforma.
     */
    private fun togglePlayback() {
        if (_uiState.value.isPlaying) {
            stopPlayback()
            return
        }
        val state = _uiState.value
        if (state.editorMeasures.isEmpty()) return
        val timeline = com.grafitto.laudate.domain.tablature.TabPlayback.timeline(
            state.editorAsTablature(),
            range = state.loopRange,
            metronome = state.metronome
        )
        if (timeline.isEmpty) {
            viewModelScope.launch {
                _sideEffect.send(TablatureSideEffect.ShowMessage("No hay notas que reproducir"))
            }
            return
        }
        playbackJob?.cancel()
        _uiState.update { it.copy(isPlaying = true, playbackSlot = null) }
        playbackJob = viewModelScope.launch {
            // Con **bucle de rango** (D-23) la línea de tiempo se recorre una y otra vez hasta que se
            // pare; sin bucle se recorre una sola vez. El metrónomo se recalcula en cada pasada porque
            // el usuario puede encenderlo o apagarlo mientras suena.
            do {
                playOnce(timeline)
            } while (_uiState.value.isPlaying && _uiState.value.loopRange != null)
            _uiState.update { it.copy(isPlaying = false, playbackSlot = null) }
        }
    }

    /**
     * Recorre **una pasada** de [timeline]: los clics del metrónomo y los ataques, mezclados por
     * milisegundo. Se recorren juntos (y no en dos bucles) para que un clic y una nota de la misma
     * pulsación suenen en el mismo instante.
     */
    /**
     * Aplica un cambio al **mezclador** (D-29) y lo persiste. Es el punto único: cualquier control
     * (deslizador, `M`, `S`) pasa por aquí, así que el estado y la preferencia no se pueden separar.
     */
    /**
     * Aplica un cambio al **metrónomo configurable** (D-66) y lo persiste. Mantiene sincronizado el
     * interruptor histórico [TablatureUiState.metronomeEnabled] para que la barra de escritura y el
     * panel de ajustes no puedan contradecirse.
     */
    private fun updateMetronome(
        transform: (com.grafitto.laudate.domain.tablature.TabMetronome) ->
        com.grafitto.laudate.domain.tablature.TabMetronome
    ) {
        val next = transform(_uiState.value.metronome)
        if (next == _uiState.value.metronome) return
        _uiState.update { it.copy(metronome = next, metronomeEnabled = next.enabled) }
        viewModelScope.launch { preferencesRepository?.setTabMetronome(next) }
    }

    private fun updateMixer(transform: (TabMixer) -> TabMixer) {
        val next = transform(_uiState.value.mixer)
        if (next == _uiState.value.mixer) return
        _uiState.update { it.copy(mixer = next) }
        viewModelScope.launch { preferencesRepository?.setTabMixer(next) }
    }

    private suspend fun playOnce(timeline: com.grafitto.laudate.domain.tablature.TabPlaybackTimeline) {
        val metronome = _uiState.value.metronome
        // El mezclador se lee **una vez** por pasada: si el usuario mueve el volumen a mitad de la
        // reproducción, el cambio entra en la siguiente pasada (o al volver a darle a play), que es
        // lo que hace un mezclador de verdad —no cambia el sonido ya emitido—.
        val mixer = _uiState.value.mixer
        // (D-66) El metrónomo tiene **volumen propio**: se aplica sobre una copia del mezclador para no
        // tocar el de la tablatura. Así se puede bajar el clic sin bajar la guitarra, que es lo que se
        // echa en falta al ensayar con alguien al lado.
        val clickMixer = mixer.copy(volume = mixer.safeVolume * metronome.safeVolume)
        val clicks = if (metronome.enabled) timeline.clicks else emptyList()
        var elapsed = 0L
        var clickIndex = 0
        timeline.events.forEach { event ->
            // Clics que caen **antes** de este ataque.
            while (clickIndex < clicks.size && clicks[clickIndex].atMs <= event.atMs) {
                val click = clicks[clickIndex]
                val wait = click.atMs - elapsed
                if (wait > 0) delay(wait)
                elapsed = click.atMs
                notePlayer?.playMidi(
                    listOf(
                        if (click.isAccent) {
                            com.grafitto.laudate.domain.tablature.TabPlayback.METRONOME_ACCENT_MIDI
                        } else {
                            com.grafitto.laudate.domain.tablature.TabPlayback.METRONOME_CLICK_MIDI
                        }
                    ),
                    com.grafitto.laudate.domain.tablature.TabPlayback.METRONOME_CLICK_MS,
                    clickMixer
                )
                clickIndex++
            }
            val wait = event.atMs - elapsed
            if (wait > 0) delay(wait)
            elapsed = event.atMs
            _uiState.update { it.copy(playbackSlot = event.measureIndex to event.slotIndex) }
            notePlayer?.playMidi(event.midiNotes, event.durationMs.toInt(), mixer)
        }
        // Clics que quedan tras el último ataque (el del último pulso del compás final).
        while (clickIndex < clicks.size) {
            val click = clicks[clickIndex]
            val wait = click.atMs - elapsed
            if (wait > 0) delay(wait)
            elapsed = click.atMs
            notePlayer?.playMidi(
                listOf(
                    if (click.isAccent) {
                        com.grafitto.laudate.domain.tablature.TabPlayback.METRONOME_ACCENT_MIDI
                    } else {
                        com.grafitto.laudate.domain.tablature.TabPlayback.METRONOME_CLICK_MIDI
                    }
                ),
                com.grafitto.laudate.domain.tablature.TabPlayback.METRONOME_CLICK_MS,
                mixer
            )
            clickIndex++
        }
        val tail = timeline.totalMs - elapsed
        if (tail > 0) delay(tail)
    }

    /**
     * **Bucle de rango** (D-23): lo activa o lo quita sobre la **selección de compases**. Sin selección
     * no hay nada que repetir, así que se avisa en vez de inventar un rango de un compás.
     */
    private fun toggleLoopRange() {
        val state = _uiState.value
        if (state.loopRange != null) {
            _uiState.update { it.copy(loopRange = null) }
            return
        }
        val selection = state.selection?.clamped(state.editorMeasures.size)
        if (selection == null) {
            viewModelScope.launch {
                _sideEffect.send(
                    TablatureSideEffect.ShowMessage("Selecciona los compases que quieres repetir")
                )
            }
            return
        }
        _uiState.update { it.copy(loopRange = selection.range) }
    }

    /** Para la reproducción y devuelve el cursor a donde estaba. */
    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        _uiState.update { it.copy(isPlaying = false, playbackSlot = null) }
    }

    /**
     * Hace sonar la nota de la casilla y cuerda indicadas (si tiene altura). Las cuerdas **apagadas**
     * (`X`) no suenan (son percusión, sin altura definida).
     */
    private fun soundAt(measureIndex: Int, slotIndex: Int, stringNumber: Int) {
        val player = notePlayer ?: return
        val state = _uiState.value
        val pluck = state.editorMeasures.getOrNull(measureIndex)?.slots?.getOrNull(slotIndex)
            ?.strings?.firstOrNull { it.stringNumber == stringNumber } ?: return
        if (pluck.isMuted) return
        val midi = state.editorTuning.pitchOf(stringNumber)?.midi?.plus(pluck.fret) ?: return
        player.playMidi(listOf(midi))
    }

    /**
     * Mueve el **cursor** de escritura (compás, casilla y cuerda) con las flechas del teclado, sin
     * escribir. La posición vertical es la cuerda; la horizontal recorre casillas y salta de compás
     * en compás. Vacía el búfer de traste (el búfer pertenece a la casilla anterior).
     */
    private fun moveCursor(measureDelta: Int, slotDelta: Int, stringDelta: Int) {
        val state = _uiState.value
        val current = state.selectedSlot ?: (0 to 0)
        var cursor = TabKeyboardInput.Cursor(current.first, current.second, state.selectedString)
        if (slotDelta != 0 || measureDelta != 0) {
            val counts = state.columnCountPerMeasure
            // Un desplazamiento de compás completo (= `Home`/`End` de compás o `Ctrl+←/→`) avanza
            // todas las columnas del compás actual y una más para «entrar» en el siguiente.
            val step = if (measureDelta != 0) {
                measureDelta * (counts.getOrElse(current.first) { 0 }.coerceAtLeast(1)) + slotDelta
            } else {
                slotDelta
            }
            cursor = TabKeyboardInput.moveHorizontal(cursor, counts, step)
        }
        if (stringDelta != 0) {
            cursor = TabKeyboardInput.moveVertical(cursor, state.editorTuning.stringCount, stringDelta)
        }
        _uiState.update {
            it.copy(
                selectedSlot = cursor.measureIndex to cursor.slotIndex,
                selectedString = cursor.stringNumber,
                fretBuffer = "",
                // La **figura activa** sigue a la casilla: al posarse sobre una nota con figura
                // explícita, la barra de escritura la refleja (como la barra de duración de MuseScore).
                // Sobre una casilla vacía o nueva se **conserva** la que estaba, para no perder el
                // modo de duración al avanzar.
                selectedFigure = figureAt(it.editorMeasures, cursor.measureIndex to cursor.slotIndex)
                    ?: it.selectedFigure
            )
        }
    }

    /**
     * Figura **explícita** de una casilla **con contenido**, o `null` si la casilla está vacía o
     * hereda la subdivisión general.
     *
     * Las casillas vacías llevan la figura del **silencio** de la subdivisión, pero eso no es una
     * elección del usuario: si se devolviera, posarse sobre el compás cambiaría la duración de
     * escritura a «silencio» y teclear una cifra dejaría de escribir la figura elegida.
     */
    private fun figureAt(measures: List<TabMeasure>, slot: Pair<Int, Int>?): RhythmFigure? =
        slot?.let { (measureIndex, slotIndex) ->
            measures.getOrNull(measureIndex)?.slots?.getOrNull(slotIndex)
                ?.takeIf { it.strings.isNotEmpty() || it.isHeld }
                ?.rhythmicFigure
        }

    /**
     * **Retroceso** del teclado físico. Primero corrige el búfer de dígitos (teclear «12» y
     * retroceder deja «1»): es la forma de rectificar un traste de dos cifras. Con el búfer ya vacío,
     * si la cuerda activa tiene nota la borra (sin salir de la casilla); si no, retrocede el cursor.
     */
    private fun backspaceCursor() {
        val state = _uiState.value
        if (state.fretBuffer.isNotEmpty()) {
            val trimmed = state.fretBuffer.dropLast(1)
            if (trimmed.isEmpty()) {
                _uiState.update { it.copy(fretBuffer = "") }
            } else {
                val fret = TabKeyboardInput.fretOf(trimmed) ?: return
                val slot = state.selectedSlot ?: return
                updateMeasures { TablatureEditing.setFret(it, slot.first, slot.second, state.selectedString, fret) }
                _uiState.update { it.copy(selectedFret = fret, fretBuffer = trimmed) }
            }
            return
        }
        val slot = state.selectedSlot
        val hasNote = slot != null && state.editorMeasures.getOrNull(slot.first)?.slots
            ?.getOrNull(slot.second)?.strings?.any { it.stringNumber == state.selectedString } == true
        if (hasNote) {
            updateMeasures { TablatureEditing.removePluck(it, slot.first, slot.second, state.selectedString) }
        } else {
            moveCursor(0, -1, 0)
        }
    }

    /** **Suprimir**: borra la nota de la cuerda activa en la casilla actual, sin mover el cursor. */
    private fun deleteAtCursor() {
        val state = _uiState.value
        val slot = state.selectedSlot ?: return
        updateMeasures { TablatureEditing.removePluck(it, slot.first, slot.second, state.selectedString) }
        _uiState.update { it.copy(fretBuffer = "") }
    }

    private fun setSlotFret(measureIndex: Int, slotIndex: Int, stringNumber: Int, fret: Int) {
        updateMeasures {
            TablatureEditing.setFret(it, measureIndex, slotIndex, stringNumber, fret)
                .withActiveVoice(measureIndex, slotIndex)
        }
        soundAt(measureIndex, slotIndex, stringNumber)
    }

    /**
     * **Inserta una columna** y deja el cursor en ella. La columna nueva nace **después** de la del
     * cursor (es el `Intro` de My Guitar Tabs: «avanzar al comienzo de la siguiente posición»), de
     * modo que el orden de escritura se conserva: `0 ⏎ 2 ⏎ 3` produce `[0, 2, 3]`, no `[3, 2, 0]`.
     * Si el cursor está en la última columna (o más allá), la columna se **añade** al final.
     */
    private fun insertColumnAtCursor(measureIndex: Int, slotIndex: Int) {
        val state = _uiState.value
        val measure = state.editorMeasures.getOrNull(measureIndex)
        val count = measure?.slots?.size ?: 0
        // (D-73) **Un compás no puede pasarse de su capacidad.** Antes «Añadir columna» insertaba
        // siempre, sin mirar la duración, así que se podían meter ocho negras en un `4/4` —el usuario
        // lo reportó tal cual—. MuseScore no deja: cuando el compás está lleno, la nota siguiente
        // **entra en el compás siguiente**. Aquí se hace lo mismo, pero antes se intenta lo que hace
        // MuseScore al elegir una figura más corta: **partir en dos** la columna del cursor (la nota
        // se acorta a la mitad y deja un silencio) para abrir el hueco sin que el compás crezca. Solo
        // si la columna no se puede partir (ya es la figura más corta, es un grupo irregular o una
        // prolongación) el cursor salta al principio del compás siguiente, creándolo si hace falta.
        val complete = measure != null &&
            TabMeasureMeter.durationOf(measure, state.editorResolution) >=
            state.editorTime.totalQuarterBeats - 1e-6
        if (complete) {
            val split = TablatureEditing.splitSlot(
                state.editorMeasures, measureIndex, slotIndex, state.editorResolution
            )
            if (split != null) {
                updateMeasures { split }
                _uiState.update { it.copy(selectedSlot = measureIndex to (slotIndex + 1), fretBuffer = "") }
                return
            }
            val nextMeasure = measureIndex + 1
            if (nextMeasure > state.editorMeasures.lastIndex) insertMeasureAt(state.editorMeasures.size)
            _uiState.update { it.copy(selectedSlot = nextMeasure to 0, fretBuffer = "") }
            return
        }
        val at = (slotIndex + 1).coerceIn(0, count)
        updateMeasures { TablatureEditing.insertColumn(it, measureIndex, at) }
        _uiState.update { it.copy(selectedSlot = measureIndex to at, fretBuffer = "") }
    }

    /** Quita la columna indicada. Si era la última del compás, el cursor retrocede a la anterior. */
    private fun removeColumnAt(measureIndex: Int, slotIndex: Int) {
        updateMeasures { TablatureEditing.removeColumn(it, measureIndex, slotIndex) }
        _uiState.update { state ->
            val previous = (slotIndex - 1).coerceAtLeast(0)
            val count = state.editorMeasures.getOrNull(measureIndex)?.slots?.size ?: 0
            state.copy(
                selectedSlot = measureIndex to previous.coerceAtMost((count - 1).coerceAtLeast(0)),
                fretBuffer = ""
            )
        }
    }

    /**
     * **Inserta una barra de compás** tras la columna indicada, partiendo el compás en dos y dejando
     * el cursor en el compás nuevo. Es el `Shift+Intro` de My Guitar Tabs.
     */
    private fun insertBarAfter(measureIndex: Int, slotIndex: Int) {
        updateMeasures { TablatureEditing.insertBar(it, measureIndex, slotIndex) }
        _uiState.update { it.copy(selectedSlot = (measureIndex + 1) to 0, fretBuffer = "") }
    }

    /**
     * **Inserta un compás vacío** (con su rejilla de silencios) en la posición [at], desplazando el
     * resto. Es el «insertar compás» de MuseScore/Guitar Pro: el compás nuevo nace **completo**, no
     * corto, y el cursor se coloca en él.
     *
     * La **subdivisión** del compás nuevo es la del compás que ocupa esa posición (o la del último si
     * se inserta al final): si se está escribiendo en corcheas, el compás que se añade también va en
     * corcheas, que es lo que se espera.
     */
    private fun insertMeasureAt(at: Int, count: Int = 1) {
        val state = _uiState.value
        val position = at.coerceIn(0, state.editorMeasures.size)
        val neighbour = state.editorMeasures.getOrNull(position)
            ?: state.editorMeasures.lastOrNull()
        val resolution = neighbour
            ?.let { TablatureEditing.subdivisionOf(it, state.editorResolution) }
            ?: state.editorResolution
        val block = List(count.coerceIn(1, MAX_APPENDED_MEASURES)) {
            TablatureEditing.emptyCompleteMeasure(state.editorTime, resolution)
        }
        val next = TablatureEditing.insertMeasures(state.editorMeasures, position, block)
        pushHistory(state.editorMeasures)
        _uiState.update {
            it.copy(
                editorMeasures = next,
                selectedSlot = position to 0,
                fretBuffer = "",
                canUndo = true,
                canRedo = false
            )
        }
    }

    /**
     * **Cambia la subdivisión** (la rejilla de trabajo) del compás [measureIndex] a [resolution].
     *
     * Re-reparte las **columnas de silencio** a la nueva figura y deja **intactas** las columnas con
     * notas, así que se puede cambiar la rejilla de un compás a medio escribir sin perder nada. El
     * cursor se recoloca en la **misma columna** si sigue existiendo, y si no, al principio.
     */
    private fun setMeasureSubdivision(measureIndex: Int, resolution: TabResolution) {
        val state = _uiState.value
        if (measureIndex !in state.editorMeasures.indices) return
        val current = state.editorMeasures[measureIndex]
        if (TablatureEditing.subdivisionOf(current, state.editorResolution) == resolution) return
        val next = state.editorMeasures.toMutableList()
        next[measureIndex] = TablatureEditing.relayoutRests(current, state.editorTime, resolution)
        pushHistory(state.editorMeasures)
        _uiState.update {
            val slot = it.selectedSlot
            it.copy(
                editorMeasures = next,
                selectedSlot = if (slot != null && slot.first == measureIndex) {
                    slot.first to slot.second.coerceAtMost(next[measureIndex].slots.lastIndex.coerceAtLeast(0))
                } else {
                    slot
                },
                fretBuffer = "",
                canUndo = true,
                canRedo = false
            )
        }
    }

    /**
     * **Anacrusa** (*pickup*): marca o desmarca el primer compás como el compás incompleto con el que
     * arranca la pieza. Ver [TablatureEditing.togglePickup] para el recorte y relleno de silencios.
     */
    private fun togglePickup() {
        val state = _uiState.value
        if (state.editorMeasures.isEmpty()) return
        val next = TablatureEditing.togglePickup(
            state.editorMeasures,
            state.editorTime,
            state.editorResolution
        )
        if (next == state.editorMeasures) return
        pushHistory(state.editorMeasures)
        _uiState.update {
            val slot = it.selectedSlot
            it.copy(
                editorMeasures = next,
                selectedSlot = if (slot != null && slot.first == 0) {
                    slot.first to slot.second.coerceAtMost(next[0].slots.lastIndex.coerceAtLeast(0))
                } else {
                    slot
                },
                fretBuffer = "",
                canUndo = true,
                canRedo = false
            )
        }
    }

    /**
     * **Cambio de compás a mitad de pieza**: fija la indicación vigente a partir de [measureIndex], o
     * la quita con `null`. Ver [TablatureEditing.setTime] para el reequilibrio del compás.
     */
    private fun setMeasureTime(
        measureIndex: Int,
        time: com.grafitto.laudate.domain.model.score.ScoreTimeSignature?
    ) {
        val state = _uiState.value
        if (measureIndex !in state.editorMeasures.indices) return
        val next = TablatureEditing.setTime(
            state.editorMeasures,
            measureIndex,
            time,
            state.editorTime,
            state.editorResolution
        )
        if (next == state.editorMeasures) return
        pushHistory(state.editorMeasures)
        _uiState.update {
            val slot = it.selectedSlot
            it.copy(
                editorMeasures = next,
                selectedSlot = if (slot != null && slot.first == measureIndex) {
                    slot.first to slot.second.coerceAtMost(
                        next[measureIndex].slots.lastIndex.coerceAtLeast(0)
                    )
                } else {
                    slot
                },
                fretBuffer = "",
                canUndo = true,
                canRedo = false
            )
        }
    }

    /**
     * **Compás irregular**: marca o desmarca el compás [measureIndex] como de duración libre. Ver
     * [TablatureEditing.toggleIrregular].
     */
    private fun toggleIrregular(measureIndex: Int) {
        val state = _uiState.value
        if (measureIndex !in state.editorMeasures.indices) return
        val next = TablatureEditing.toggleIrregular(
            state.editorMeasures,
            measureIndex,
            state.effectiveTimeAt(measureIndex),
            state.editorResolution
        )
        if (next == state.editorMeasures) return
        pushHistory(state.editorMeasures)
        _uiState.update {
            val slot = it.selectedSlot
            it.copy(
                editorMeasures = next,
                selectedSlot = if (slot != null && slot.first == measureIndex) {
                    slot.first to slot.second.coerceAtMost(
                        next[measureIndex].slots.lastIndex.coerceAtLeast(0)
                    )
                } else {
                    slot
                },
                fretBuffer = "",
                canUndo = true,
                canRedo = false
            )
        }
    }

    /**
     * **Vacía** los compases del rango [range] (`Ctrl+Shift+Del` de MuseScore): quita las notas y deja
     * el compás completo con su silencio, conservando las marcas. El compás **no** desaparece.
     */
    private fun clearMeasures(range: IntRange) {
        val state = _uiState.value
        val next = TablatureEditing.clearMeasures(
            state.editorMeasures, range, state.editorTime, state.editorResolution
        )
        if (next == state.editorMeasures) return
        pushHistory(state.editorMeasures)
        _uiState.update {
            it.copy(
                editorMeasures = next,
                selectedSlot = range.first.coerceIn(0, next.lastIndex.coerceAtLeast(0)) to 0,
                fretBuffer = "",
                canUndo = true,
                canRedo = false
            )
        }
    }

    /**
     * **Mueve** el bloque de compases [range] a la posición [to] (índice en la lista **ya sin el
     * bloque**). El cursor sigue al bloque movido, para no perder de vista lo que se acaba de mover.
     */
    private fun moveMeasures(range: IntRange, to: Int) {
        val state = _uiState.value
        val next = TablatureEditing.moveMeasures(state.editorMeasures, range, to)
        if (next == state.editorMeasures) return
        pushHistory(state.editorMeasures)
        val size = range.last - range.first + 1
        val destination = to.coerceIn(0, (next.size - size).coerceAtLeast(0))
        _uiState.update {
            it.copy(
                editorMeasures = next,
                selection = com.grafitto.laudate.domain.tablature.TablatureSelection(destination until destination + size).clamped(next.size),
                selectedSlot = destination to 0,
                fretBuffer = "",
                canUndo = true,
                canRedo = false
            )
        }
    }

    /**
     * **Salto de sistema** del compás [measureIndex] (`Return` de MuseScore): el compás empieza una
     * fila nueva aunque quepa en la anterior. Alterna, y avisa de lo que ha hecho para que la tecla no
     * parezca no hacer nada cuando el compás ya estaba al principio de su sistema.
     */
    private fun toggleSystemBreak(measureIndex: Int) {
        val state = _uiState.value
        val measure = state.editorMeasures.getOrNull(measureIndex) ?: return
        val force = !measure.forceSystemBreak
        updateMeasures { TablatureEditing.setForceSystemBreak(it, measureIndex, force) }
        viewModelScope.launch {
            _sideEffect.send(
                TablatureSideEffect.ShowMessage(
                    if (force) {
                        "El compás ${measureIndex + 1} empieza un sistema nuevo"
                    } else {
                        "El compás ${measureIndex + 1} vuelve al reparto automático"
                    }
                )
            )
        }
    }

    /**
     * **Salto de página** del compás [measureIndex] (`Ctrl`+`Return` de MuseScore): en el PDF el
     * compás empieza hoja nueva. Implica salto de sistema, así que al quitarlo se quitan los dos.
     */
    private fun togglePageBreak(measureIndex: Int) {
        val state = _uiState.value
        val measure = state.editorMeasures.getOrNull(measureIndex) ?: return
        val force = !measure.forcePageBreak
        updateMeasures { TablatureEditing.setForcePageBreak(it, measureIndex, force) }
        viewModelScope.launch {
            _sideEffect.send(
                TablatureSideEffect.ShowMessage(
                    if (force) {
                        "El compás ${measureIndex + 1} empieza una página nueva en el PDF"
                    } else {
                        "El compás ${measureIndex + 1} ya no fuerza página"
                    }
                )
            )
        }
    }

    /**
     * **Selecciona un compás** entero (clic en su número, `Ctrl+Shift+←/→`). Deja el cursor en su
     * primera casilla, que es lo que hace MuseScore al seleccionar un compás con el teclado.
     */
    /**
     * **Lleva el cursor** a la primera casilla del compás [measureIndex] sin tocar la selección: es el
     * `Ctrl+Inicio` / `Ctrl+Fin` de MuseScore, que navegan la partitura sin seleccionar nada.
     */
    private fun goToMeasure(measureIndex: Int) {
        val state = _uiState.value
        if (measureIndex !in state.editorMeasures.indices) return
        _uiState.update { it.copy(selectedSlot = measureIndex to 0, fretBuffer = "") }
    }

    private fun selectMeasureAt(measureIndex: Int) {
        val state = _uiState.value
        if (measureIndex !in state.editorMeasures.indices) return
        _uiState.update {
            it.copy(
                selection = com.grafitto.laudate.domain.tablature.TablatureSelection(measureIndex..measureIndex).clamped(it.editorMeasures.size),
                selectedSlot = measureIndex to 0,
                fretBuffer = ""
            )
        }
    }

    /**
     * **Extiende la selección de compases** [delta] compases desde el ancla actual (`Shift+clic`,
     * `Shift+←/→`). Sin selección previa se selecciona el compás del cursor, de modo que la primera
     * pulsación siempre hace algo visible.
     */
    private fun extendMeasureSelection(delta: Int) {
        val state = _uiState.value
        if (state.editorMeasures.isEmpty()) return
        val current = state.selection
        if (current == null) {
            val anchor = state.selectedSlot?.first ?: 0
            _uiState.update {
                it.copy(selection = com.grafitto.laudate.domain.tablature.TablatureSelection(anchor..anchor).clamped(it.editorMeasures.size))
            }
            return
        }
        val anchor = current.range.first
        val last = (current.range.last + delta).coerceIn(0, state.editorMeasures.lastIndex)
        val range = if (last >= anchor) anchor..last else last..anchor
        _uiState.update {
            it.copy(selection = com.grafitto.laudate.domain.tablature.TablatureSelection(range).clamped(it.editorMeasures.size))
        }
    }

    /**
     * Asigna la **voz** [voice] a los compases seleccionados (o al compás del cursor si no hay
     * selección) y la deja como voz activa: es el `Ctrl+Alt+1..4` de MuseScore sobre una selección.
     */
    private fun setVoiceInRange(voice: Int) {
        val state = _uiState.value
        val safe = if (voice in 1..TabVoiceColors.VOICE_COUNT) voice else 1
        val range = state.selection?.range
            ?: state.selectedSlot?.first?.let { it..it }
            ?: return
        updateMeasures { TablatureEditing.setVoiceInRange(it, range, safe) }
        _uiState.update { it.copy(activeVoice = safe) }
    }

    /** Aplica un **paso de zoom** multiplicativo, acotado a [MIN_ZOOM]..[MAX_ZOOM]. */
    private fun zoomBy(factor: Float) {
        val next = (_uiState.value.tablatureScale * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        processIntent(TablatureUiIntent.SetTablatureScale(next))
    }

    /**
     * **Duplica** el compás [measureIndex] justo detrás, con sus notas y marcas, y deja el cursor en
     * la copia. Es el `Ctrl+D` de MuseScore.
     */
    private fun duplicateMeasure(measureIndex: Int) {
        val state = _uiState.value
        if (measureIndex !in state.editorMeasures.indices) return
        val next = TablatureEditing.duplicateMeasures(state.editorMeasures, measureIndex..measureIndex)
        if (next == state.editorMeasures) return
        pushHistory(state.editorMeasures)
        _uiState.update {
            it.copy(
                editorMeasures = next,
                selectedSlot = (measureIndex + 1) to 0,
                fretBuffer = "",
                canUndo = true,
                canRedo = false
            )
        }
    }

    /**
     * **Elimina** el compás [measureIndex]. Nunca deja la tablatura sin compases: si era el último
     * que quedaba, se conserva uno vacío (como MuseScore).
     */
    /**
     * **Elimina** los compases del rango [range] de una vez (el `Ctrl+Shift+Supr` de MuseScore sobre
     * una selección de compases). Deja el cursor en el compás que ocupaba el primero del rango.
     */
    private fun deleteMeasures(range: IntRange) {
        val state = _uiState.value
        if (state.editorMeasures.isEmpty()) return
        val next = TablatureEditing.removeMeasures(state.editorMeasures, range)
        if (next == state.editorMeasures) return
        pushHistory(state.editorMeasures)
        _uiState.update {
            it.copy(
                editorMeasures = next,
                selection = null,
                selectedSlot = range.first.coerceIn(0, next.lastIndex) to 0,
                fretBuffer = "",
                canUndo = true,
                canRedo = false
            )
        }
    }

    private fun deleteMeasure(measureIndex: Int) {
        val state = _uiState.value
        if (measureIndex !in state.editorMeasures.indices) return
        val next = TablatureEditing.removeMeasures(state.editorMeasures, measureIndex..measureIndex)
        if (next == state.editorMeasures) return
        pushHistory(state.editorMeasures)
        _uiState.update {
            it.copy(
                editorMeasures = next,
                selectedSlot = measureIndex.coerceIn(0, next.lastIndex) to 0,
                fretBuffer = "",
                canUndo = true,
                canRedo = false
            )
        }
    }

    /**
     * **Alterna una técnica** de la cuerda activa en la casilla del cursor. Las técnicas se combinan
     * (ligado + slide…), así que el mismo botón la pone y la quita; `NONE` limpia todas. Es el gesto de
     * la **paleta de técnicas** (ADR-020: sin atajo de teclado, como MuseScore).
     */
    private fun toggleTechniqueAtCursor(technique: com.grafitto.laudate.domain.model.tablature.TabTechnique) {
        val state = _uiState.value
        val slot = state.selectedSlot ?: return
        updateMeasures {
            TablatureEditing.toggleTechnique(it, slot.first, slot.second, state.selectedString, technique)
        }
        _uiState.update { it.copy(fretBuffer = "") }
    }

    /**
     * **Entra o sale del modo de entrada de notas** (tecla `N` de MuseScore, ADR-020). Al **entrar**,
     * se asegura de que hay un cursor sobre el que escribir; al **salir**, se limpia el búfer de
     * dígitos. El modo es un estado **visible** (cursor azul) y no toca la música.
     */
    private fun toggleInputMode() {
        _uiState.update { state ->
            val entering = !state.isInputMode
            state.copy(
                isInputMode = entering,
                fretBuffer = "",
                // Al entrar sin cursor, se coloca en la primera casilla para poder escribir.
                selectedSlot = state.selectedSlot ?: (0 to 0)
            )
        }
    }

    /** **`Esc`**: sale del modo de entrada y deselecciona. No modifica la música. */
    private fun leaveInputMode() {
        _uiState.update { it.copy(isInputMode = false, fretBuffer = "", selection = null) }
    }

    /**
     * **`↑`/`↓` en modo normal**: sube o baja el **traste** de la nota del cursor **sin cambiar de
     * cuerda** (`TabShortcuts.incrementFret`). Si no hay nota en la cuerda activa, no hace nada: en
     * modo entrada esas flechas mueven el cursor entre cuerdas, pero eso lo resuelve la UI con
     * `MoveCursor`.
     */
    private fun nudgeFret(delta: Int) {
        val state = _uiState.value
        val slot = state.selectedSlot ?: return
        val pluck = state.editorMeasures.getOrNull(slot.first)?.slots?.getOrNull(slot.second)
            ?.strings?.firstOrNull { it.stringNumber == state.selectedString } ?: return
        val moved = TabShortcuts.incrementFret(pluck, delta) ?: return
        updateMeasures { TablatureEditing.setPluck(it, slot.first, slot.second, moved) }
        _uiState.update { it.copy(selectedFret = moved.fret, fretBuffer = "") }
        soundAt(slot.first, slot.second, moved.stringNumber)
    }

    /**
     * **`Alt`+`Shift`+`↑`/`↓`**: sube o baja el traste **cambiando de cuerda** si hace falta para
     * dejar el **traste más bajo posible** (`TabShortcuts.moveFretDiatonic`). La cuerda activa sigue a
     * la nota recolocada.
     */
    private fun nudgeFretAcrossStrings(delta: Int) {
        val state = _uiState.value
        val slot = state.selectedSlot ?: return
        val pluck = state.editorMeasures.getOrNull(slot.first)?.slots?.getOrNull(slot.second)
            ?.strings?.firstOrNull { it.stringNumber == state.selectedString } ?: return
        val moved = TabShortcuts.moveFretDiatonic(pluck, state.editorTuning, delta) ?: return
        updateMeasures { TablatureEditing.setPluck(it, slot.first, slot.second, moved) }
        _uiState.update {
            it.copy(selectedString = moved.stringNumber, selectedFret = moved.fret, fretBuffer = "")
        }
        soundAt(slot.first, slot.second, moved.stringNumber)
    }

    /**
     * **`Q`/`W` (acortar/alargar) y las teclas de duración**: fija la figura de la nota del cursor **y**
     * la deja como **figura activa** de la barra de escritura, como MuseScore: la siguiente nota que se
     * teclee nace con esa duración. Con una casilla **vacía**, la figura es solo la activa (no hay
     * nada que cambiar).
     */
    private fun setFigureForCursor(figure: RhythmFigure?) {
        val state = _uiState.value
        val slot = state.selectedSlot
        // La figura activa cambia **siempre**: es el «modo de duración» de MuseScore.
        _uiState.update { it.copy(selectedFigure = figure, fretBuffer = "") }
        if (slot == null || figure == null) return
        val hasContent = state.editorMeasures.getOrNull(slot.first)?.slots?.getOrNull(slot.second)
            ?.let { !it.isEmpty || it.isHeld } == true
        if (!hasContent) return
        updateMeasures { TablatureEditing.setRhythmicFigure(it, slot.first, slot.second, figure) }
    }

    /**
     * **`Q`/`W`**: acorta (`delta = -1`) o alarga (`delta = +1`) la figura de la nota del cursor,
     * conservando su valor si no se puede (ya es la más corta o la más larga).
     */
    private fun adjustFigure(delta: Int) {
        val state = _uiState.value
        val slot = state.selectedSlot ?: return
        val current = state.selectedFigure
            ?: state.editorMeasures.getOrNull(slot.first)?.slots?.getOrNull(slot.second)?.rhythmicFigure
            ?: RhythmFigure.QUARTER
        val next = if (delta < 0) TabShortcuts.shorter(current) else TabShortcuts.longer(current)
            ?: return
        setFigureForCursor(next)
    }

    /** **`.`**: alterna el **puntillo** de la figura de la nota del cursor (y la deja activa). */
    private fun toggleDotAtCursor() {
        val state = _uiState.value
        val slot = state.selectedSlot ?: return
        val current = state.editorMeasures.getOrNull(slot.first)?.slots?.getOrNull(slot.second)
            ?.rhythmicFigure ?: state.selectedFigure ?: return
        val next = TabShortcuts.toggleDot(current) ?: return
        setFigureForCursor(next)
    }

    /**
     * **`;`**: inserta un **silencio** de la figura activa en la columna del cursor, desplazando lo que
     * venga después. Sin figura activa se usa el silencio de la subdivisión del compás.
     */
    private fun insertRestAtCursorAtCursor() {
        val state = _uiState.value
        val figure = state.selectedFigure
            ?.let { RhythmFigure.restEquivalentOf(it) ?: RhythmFigure.QUARTER_REST }
            ?: TablatureEditing.restFigureForResolution(state.cursorSubdivision)
        processIntent(TablatureUiIntent.InsertRestAtCursor(figure))
    }

    /**
     * **`Shift`+`X`**: alterna la **nota fantasma** (*crosshead/ghost*) de la nota del cursor. Es la
     * técnica [TabTechnique.GHOST]; si la nota es **apagada** (`X`), MuseScore no la ofrece, así que la
     * matriz de compatibilidad decide.
     */
    private fun toggleGhostNote() {
        val state = _uiState.value
        val slot = state.selectedSlot ?: return
        updateMeasures {
            TablatureEditing.toggleTechnique(
                it, slot.first, slot.second, state.selectedString, TabTechnique.GHOST
            )
        }
        _uiState.update { it.copy(fretBuffer = "") }
    }

    /**
     * **`Shift`+`V`/`O`/`S`/`N`**: aplica la **articulación** [articulation] a la nota del cursor. Si ya
     * la tenía, la quita (como un interruptor), respetando la matriz de compatibilidad (U-2).
     */
    private fun applyArticulationAtCursor(articulation: TabArticulation) {
        val state = _uiState.value
        val slot = state.selectedSlot ?: return
        updateMeasures {
            TablatureEditing.toggleArticulation(it, slot.first, slot.second, state.selectedString, articulation)
        }
        _uiState.update { it.copy(fretBuffer = "") }
    }

    /**
     * **Ctrl+↑/↓** (método 2): mueve la nota del cursor a la **cuerda contigua** sin cambiar su
     * altura (recalcula el traste). Si no cabe en esa cuerda, no se mueve.
     */
    private fun moveNoteString(direction: Int) {
        val state = _uiState.value
        val slot = state.selectedSlot ?: return
        val hasNote = state.editorMeasures.getOrNull(slot.first)?.slots?.getOrNull(slot.second)
            ?.strings?.any { it.stringNumber == state.selectedString } == true
        if (!hasNote) {
            // Sin nota, `Ctrl+↑/↓` cambia de cuerda activa (como las flechas en modo normal).
            _uiState.update { it.copy(selectedString = nextStringInDirection(it, direction)) }
            return
        }
        val moved = TablatureEditing.movePluckToAdjacentString(
            state.editorMeasures, slot.first, slot.second, state.selectedString, state.editorTuning, direction
        )
        if (moved != state.editorMeasures) {
            pushHistory(state.editorMeasures)
            // La cuerda activa sigue a la nota movida (la que ya no es la antigua).
            val note = moved[slot.first].slots[slot.second].strings
                .firstOrNull { p -> p.stringNumber != state.selectedString }
            val newString = note?.stringNumber ?: state.selectedString
            val newFret = note?.fret ?: state.selectedFret
            _uiState.update {
                it.copy(
                    editorMeasures = moved,
                    selectedString = newString,
                    selectedFret = newFret,
                    canUndo = true,
                    canRedo = false
                )
            }
            soundAt(slot.first, slot.second, newString)
        }
    }

    /** Cuerda siguiente en la dirección [direction] (+1 grave, −1 agudo), sin dar la vuelta. */
    private fun nextStringInDirection(state: TablatureUiState, direction: Int): Int {
        val count = state.editorTuning.stringCount
        return (state.selectedString + direction).coerceIn(1, count.coerceAtLeast(1))
    }

    // ── Selección de notas (Fase 2, ADR-020) ────────────────────────────────────────

    /**
     * **`Mayús`+`←`/`→`**: extiende la **selección de notas** una columna a la izquierda o a la
     * derecha. Si la selección está vacía, empieza por la casilla del cursor; si ya hay rango, mueve el
     * **borde** del lado por el que se tira, como en un editor de texto.
     */
    private fun extendNoteSelectionRelative(delta: Int) {
        val state = _uiState.value
        val current = state.noteSelection
        val cursor = state.selectedSlot ?: (0 to 0)
        if (current == null || current.isEmpty) {
            _uiState.update {
                it.copy(
                    noteSelection = TabNoteSelection().single(
                        TabNoteRef(cursor.first, cursor.second, it.selectedString)
                    )
                )
            }
            return
        }
        val maxSlot = current.notes.maxOf { it.slotIndex }
        val minSlot = current.notes.minOf { it.slotIndex }
        val stringNumber = current.notes.first().stringNumber
        val measureIndex = current.notes.first().measureIndex
        val (from, to) = if (delta < 0) (minSlot + delta) to maxSlot else minSlot to (maxSlot + delta)
        if (from < 0) return
        val slotCount = state.editorMeasures.getOrNull(measureIndex)?.slots?.size ?: 0
        if (to > slotCount) return
        _uiState.update {
            it.copy(noteSelection = TabNoteSelection.range(measureIndex, from, to, stringNumber))
        }
    }

    /**
     * **`Mayús`+flecha en las dos dimensiones**: mueve el **foco** de la selección y recalcula el
     * rectángulo entre el ancla y el foco.
     *
     * El ancla se fija la primera vez (en la nota del cursor) y **no** se mueve mientras se extiende:
     * es lo que hace que la selección crezca y **decrezca** al volver sobre los pasos, en vez de
     * acumular notas sueltas. El foco se desplaza en columnas —cruzando de compás cuando se acaba el
     * actual— y en cuerdas, acotado al número de cuerdas de la tablatura.
     */
    private fun extendNoteSelectionRect(deltaSlot: Int, deltaString: Int) {
        val state = _uiState.value
        val cursor = state.selectedSlot ?: (0 to 0)
        val anchor = state.noteSelectionAnchor
            ?: TabNoteRef(cursor.first, cursor.second, state.selectedString)
        val focus = state.noteSelectionFocus ?: anchor
        val moved = moveNoteRef(
            state.editorMeasures,
            focus,
            deltaSlot,
            deltaString,
            state.editorTuning.stringCount
        )
        _uiState.update {
            it.copy(
                noteSelectionAnchor = anchor,
                noteSelectionFocus = moved,
                noteSelection = TabNoteSelection.rectBetween(it.editorMeasures, anchor, moved)
            )
        }
    }

    /**
     * Desplaza [ref] [deltaSlot] columnas y [deltaString] cuerdas, cruzando de compás cuando el
     * desplazamiento se sale del actual. Devuelve [ref] sin cambios si el destino no existe: así el
     * foco se **para** en el borde en vez de saltar a un compás arbitrario.
     *
     * [stringCount] sale de la **afinación**, no de las notas escritas: un compás recién creado solo
     * tiene las cuerdas que ya suenan, y el foco debe poder bajar hasta la última del instrumento.
     */
    private fun moveNoteRef(
        measures: List<TabMeasure>,
        ref: TabNoteRef,
        deltaSlot: Int,
        deltaString: Int,
        stringCount: Int
    ): TabNoteRef {
        val string = if (stringCount > 0) {
            (ref.stringNumber + deltaString).coerceIn(1, stringCount)
        } else {
            ref.stringNumber
        }
        if (deltaSlot == 0) return ref.copy(stringNumber = string)
        var measureIndex = ref.measureIndex
        var slotIndex = ref.slotIndex + deltaSlot
        while (slotIndex < 0 && measureIndex > 0) {
            measureIndex -= 1
            slotIndex += measures.getOrNull(measureIndex)?.slots?.size ?: 0
        }
        while (true) {
            val slotCount = measures.getOrNull(measureIndex)?.slots?.size ?: 0
            if (slotIndex < slotCount) break
            if (measureIndex >= measures.lastIndex) {
                slotIndex = (slotCount - 1).coerceAtLeast(0)
                break
            }
            slotIndex -= slotCount
            measureIndex += 1
        }
        if (slotIndex < 0) slotIndex = 0
        return TabNoteRef(measureIndex, slotIndex, string)
    }

    /** **Borra** las notas seleccionadas, dejando las columnas (como el `Supr` de MuseScore). */
    private fun deleteSelectedNotes() {
        val state = _uiState.value
        val selection = state.noteSelection ?: return
        if (selection.isEmpty) return
        val next = TablatureEditing.removeSelectedNotes(state.editorMeasures, selection)
        if (next == state.editorMeasures) return
        pushHistory(state.editorMeasures)
        _uiState.update {
            it.copy(
                editorMeasures = next,
                noteSelection = null,
                canUndo = true,
                canRedo = false,
                errorMessage = null
            )
        }
    }

    /** **Copia** (o **corta**) las notas seleccionadas al portapapeles de notas. */
    private fun copySelectedNotes(cut: Boolean) {
        val state = _uiState.value
        val selection = state.noteSelection ?: return
        if (selection.isEmpty) return
        noteClipboard = TablatureEditing.copySelectedNotes(state.editorMeasures, selection)
        _uiState.update { it.copy(hasNoteClipboard = true) }
        if (cut) deleteSelectedNotes()
    }

    /** **Pega** el portapapeles de notas en la posición del cursor. */
    private fun pasteNotes() {
        val state = _uiState.value
        val clipboard = noteClipboard ?: return
        if (clipboard.isEmpty) return
        val slot = state.selectedSlot ?: return
        val target = TabNoteRef(slot.first, slot.second, state.selectedString)
        val next = TablatureEditing.pasteNotes(state.editorMeasures, clipboard, target)
        if (next == state.editorMeasures) return
        pushHistory(state.editorMeasures)
        _uiState.update {
            it.copy(editorMeasures = next, canUndo = true, canRedo = false, errorMessage = null)
        }
    }

    /**
     * **Arrastrar la cifra a la cuerda adyacente** (MuseScore): mueve la nota del cursor [stringDelta]
     * cuerdas **sin cambiar su altura**, un paso cada vez, hasta donde la nota quepa. Si no cabe en
     * ninguna de las cuerdas intermedias, se queda donde llegó.
     */
    private fun dragNoteToAdjacentString(stringDelta: Int) {
        if (stringDelta == 0) return
        val step = if (stringDelta > 0) 1 else -1
        repeat(kotlin.math.abs(stringDelta)) {
            val state = _uiState.value
            val slot = state.selectedSlot ?: return
            val hasNote = state.editorMeasures.getOrNull(slot.first)?.slots?.getOrNull(slot.second)
                ?.strings?.any { it.stringNumber == state.selectedString } == true
            if (!hasNote) return
            val moved = TablatureEditing.movePluckToAdjacentString(
                state.editorMeasures, slot.first, slot.second, state.selectedString, state.editorTuning, step
            )
            if (moved == state.editorMeasures) return
            pushHistory(state.editorMeasures)
            val note = moved[slot.first].slots[slot.second].strings
                .firstOrNull { p -> p.stringNumber != state.selectedString }
            val newString = note?.stringNumber ?: state.selectedString
            _uiState.update {
                it.copy(
                    editorMeasures = moved,
                    selectedString = newString,
                    selectedFret = note?.fret ?: it.selectedFret,
                    canUndo = true,
                    canRedo = false
                )
            }
        }
        val slot = _uiState.value.selectedSlot
        if (slot != null) soundAt(slot.first, slot.second, _uiState.value.selectedString)
    }

    /** Avanza el cursor una columna (como `Espacio`). */
    private fun advanceCursor() {
        val state = _uiState.value
        val slot = state.selectedSlot ?: (0 to 0)
        val measure = state.editorMeasures.getOrNull(slot.first)
        val count = measure?.slots?.size ?: 0
        if (measure == null) return

        // (D-93) **El cursor avanza según SU posición en el tiempo, no según la duración del
        // compás.** Aquí estaba el error de D-90/D-91: se comparaba la duración **total** del
        // compás (`durationOf`) con su capacidad. La rejilla predefinida nace **llena de silencios**
        // —un `4/4` en negras nace con cuatro columnas que ya suman 4,0—, así que la comparación
        // `4,0 + 1,0 <= 4,0` daba **siempre falsa** y **cada nota creaba un compás nuevo**. El
        // usuario lo describió tal cual.
        //
        // Lo que hace MuseScore es otra cosa: la nota ocupa un **tiempo** que empieza donde está el
        // cursor, y el cursor pasa a la posición `actual + duración`. Solo hay que comprobar que esa
        // **posición** siga dentro del compás, no que quepa una columna más en el total.
        val beatsBefore = measure.slots
            .take(slot.second.coerceIn(0, count))
            .sumOf { TabMeasureMeter.beatsOf(it, state.editorResolution) }
        val writtenBeats = measure.slots.getOrNull(slot.second)
            ?.let { TabMeasureMeter.beatsOf(it, state.editorResolution) }
            ?: (state.selectedFigure?.beatValue
                ?: TablatureEditing.figureForResolution(state.editorResolution).beatValue)
        val capacity = state.editorTime.totalQuarterBeats

        // La **posición del cursor tras escribir** la nota de esta casilla. Si aún queda compás por
        // delante y hay una columna siguiente, se avanza a ella; si no, al compás siguiente.
        val afterWrite = beatsBefore + writtenBeats
        if (slot.second + 1 < count && afterWrite < capacity - 1e-6) {
            _uiState.update { it.copy(selectedSlot = slot.first to slot.second + 1, fretBuffer = "") }
            return
        }
        // Se acabó el compás (o la rejilla): el cursor pasa al **compás siguiente**, creándolo si
        // hace falta, como MuseScore.
        if (afterWrite >= capacity - 1e-6) {
            val nextMeasure = slot.first + 1
            if (nextMeasure > state.editorMeasures.lastIndex) insertMeasureAt(state.editorMeasures.size)
            _uiState.update { it.copy(selectedSlot = nextMeasure to 0, fretBuffer = "") }
            return
        }
        // Queda compás pero no columnas (rejilla corta): se **añade** una columna con la figura
        // activa para poder seguir escribiendo.
        updateMeasures {
            val grown = TablatureEditing.insertColumn(it, slot.first, count)
            TablatureEditing.setRhythmicFigure(
                grown, slot.first, count,
                RhythmFigure.restEquivalentOf(
                    state.selectedFigure ?: TablatureEditing.figureForResolution(state.editorResolution)
                ) ?: RhythmFigure.QUARTER_REST
            ).withActiveVoice(slot.first, count)
        }
        _uiState.update { it.copy(selectedSlot = slot.first to count, fretBuffer = "") }
    }

    /**
     * Aplica una transformación a los compases del editor registrando el estado **anterior** en el
     * historial (E2): cada edición es un paso de *deshacer*. Un cambio que devuelve el mismo valor no
     * ensucia el historial.
     *
     * El historial se apila **fuera** del `update` atómico (que puede reejecutar su lambda) para que
     * cada edición deje exactamente **un** paso.
     */
    private fun updateMeasures(transform: (List<TabMeasure>) -> List<TabMeasure>) {
        val previous = _uiState.value.editorMeasures
        val next = transform(previous)
        if (next == previous) {
            _uiState.update { it.copy(errorMessage = null) }
            return
        }
        pushHistory(previous)
        _uiState.update {
            it.copy(editorMeasures = next, errorMessage = null, canUndo = true, canRedo = false)
        }
    }

    /**
     * Marca la casilla [measureIndex]/[slotIndex] con la **voz activa** del editor (Fase 3 de
     * paridad). Es el punto único donde la voz entra en el modelo: cualquier camino de escritura
     * (toque, teclado físico, teclado en pantalla, arrastre) pasa por aquí, así que no puede haber
     * una nota que se escriba con una voz y se pinte con otra.
     */
    private fun List<TabMeasure>.withActiveVoice(measureIndex: Int, slotIndex: Int): List<TabMeasure> =
        TablatureEditing.setSlotVoice(this, measureIndex, slotIndex, _uiState.value.activeVoice)

    /**
     * Instantánea del documento en edición para el historial. Guarda **también** el compás y la
     * resolución: sin ellos, `Ctrl+Z` devolvía las notas pero dejaba el compás cambiado.
     */
    private data class EditorSnapshot(
        val measures: List<TabMeasure>,
        val time: com.grafitto.laudate.domain.model.score.ScoreTimeSignature,
        val resolution: TabResolution
    )

    /** Apila el estado [previous] en el historial de deshacer y limpia el de rehacer. */
    private fun pushHistory(previous: List<TabMeasure>) {
        val state = _uiState.value
        undoStack.addLast(EditorSnapshot(previous, state.editorTime, state.editorResolution))
        // Tope del historial: no hace falta guardar miles de pasos (memoria del editor).
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
    }

    // ── Selección de rango, portapapeles y deshacer/rehacer (E1-E3) ────────────────

    /** **Elimina** los compases seleccionados y limpia la selección (E1). */
    private fun deleteSelection() {
        val state = _uiState.value
        val selection = state.selection ?: return
        val next = TablatureEditing.removeMeasures(state.editorMeasures, selection.range)
        if (next == state.editorMeasures) {
            _uiState.update { it.copy(selection = null) }
            return
        }
        pushHistory(state.editorMeasures)
        // El cursor cae al compás donde empezaba la selección (acotado al nuevo tamaño).
        val cursorMeasure = selection.range.first.coerceIn(0, next.lastIndex.coerceAtLeast(0))
        _uiState.update {
            it.copy(
                editorMeasures = next,
                selection = null,
                selectedSlot = cursorMeasure to 0,
                canUndo = true,
                canRedo = false,
                errorMessage = null
            )
        }
    }

    /** **Copia** (o **corta**) los compases seleccionados al portapapeles del editor (E3). */
    private fun copySelection(cut: Boolean) {
        val state = _uiState.value
        val selection = state.selection ?: return
        val block = state.editorMeasures.subList(selection.range.first, selection.range.last + 1).toList()
        clipboard = com.grafitto.laudate.domain.tablature.TablatureClipboard(block, isCut = cut)
        if (cut) {
            deleteSelection()
        } else {
            _uiState.update { it.copy(hasClipboard = true) }
        }
        if (cut) _uiState.update { it.copy(hasClipboard = true) }
    }

    /** **Duplica** los compases seleccionados justo detrás y deja el cursor en la copia (E3). */
    private fun duplicateSelection() {
        val state = _uiState.value
        val selection = state.selection ?: return
        val next = TablatureEditing.duplicateMeasures(state.editorMeasures, selection.range)
        if (next == state.editorMeasures) return
        pushHistory(state.editorMeasures)
        val copyStart = selection.range.last + 1
        _uiState.update {
            it.copy(
                editorMeasures = next,
                selection = com.grafitto.laudate.domain.tablature.TablatureSelection(
                    copyStart..(copyStart + selection.size - 1)
                ),
                selectedSlot = copyStart to 0,
                canUndo = true,
                canRedo = false,
                errorMessage = null
            )
        }
    }

    /** **Pega** el portapapeles delante del compás del cursor (E3). */
    private fun pasteClipboard() {
        val state = _uiState.value
        val block = clipboard?.measures ?: return
        val at = (state.selectedSlot?.first ?: state.editorMeasures.size).coerceIn(0, state.editorMeasures.size)
        val next = TablatureEditing.insertMeasures(state.editorMeasures, at, block)
        pushHistory(state.editorMeasures)
        _uiState.update {
            it.copy(
                editorMeasures = next,
                selection = com.grafitto.laudate.domain.tablature.TablatureSelection(
                    at..(at + block.size - 1)
                ),
                selectedSlot = at to 0,
                canUndo = true,
                canRedo = false,
                errorMessage = null
            )
        }
    }

    /**
     * **Intercambia** la selección con el portapapeles (`Ctrl+Mayús+X` de MuseScore, `edit-swap`).
     *
     * El portapapeles ocupa el sitio de la selección y lo que había en ella pasa a ser el contenido
     * nuevo del portapapeles, así que dos `Ctrl+Mayús+X` seguidos devuelven el documento a su estado
     * original. Es la forma de **mover** un pasaje sin cortar y pegar.
     */
    private fun swapSelection() {
        val state = _uiState.value
        val selection = state.selection ?: return
        val block = clipboard?.measures ?: return
        if (block.isEmpty()) return
        val replaced = state.editorMeasures
            .subList(selection.range.first, selection.range.last + 1)
            .toList()
        val next = TablatureEditing.swapMeasures(state.editorMeasures, selection.range, block)
        if (next == state.editorMeasures) return
        pushHistory(state.editorMeasures)
        clipboard = com.grafitto.laudate.domain.tablature.TablatureClipboard(replaced, isCut = false)
        val start = selection.range.first
        _uiState.update {
            it.copy(
                editorMeasures = next,
                selection = com.grafitto.laudate.domain.tablature.TablatureSelection(
                    start..(start + block.size - 1)
                ),
                selectedSlot = start to 0,
                hasClipboard = true,
                canUndo = true,
                canRedo = false,
                errorMessage = null
            )
        }
    }

    /** **Deshace** el último cambio del editor (E2). */
    private fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        _uiState.update { state ->
            redoStack.addLast(EditorSnapshot(state.editorMeasures, state.editorTime, state.editorResolution))
            state.copy(
                editorMeasures = previous.measures,
                editorTime = previous.time,
                editorResolution = previous.resolution,
                selectedSlot = state.selectedSlot?.let { (m, s) ->
                    m.coerceIn(0, previous.measures.lastIndex.coerceAtLeast(0)) to s
                },
                canUndo = undoStack.isNotEmpty(),
                canRedo = true,
                errorMessage = null
            )
        }
    }

    /** **Rehace** el último cambio deshecho (E2). */
    private fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        _uiState.update { state ->
            undoStack.addLast(EditorSnapshot(state.editorMeasures, state.editorTime, state.editorResolution))
            state.copy(
                editorMeasures = next.measures,
                editorTime = next.time,
                editorResolution = next.resolution,
                selectedSlot = state.selectedSlot?.let { (m, s) ->
                    m.coerceIn(0, next.measures.lastIndex.coerceAtLeast(0)) to s
                },
                canUndo = true,
                canRedo = redoStack.isNotEmpty(),
                errorMessage = null
            )
        }
    }

    // ── Importar una partitura como tablatura del canto ──────────────────────────

    /** Pide a la plataforma que **abra el selector de archivo** para elegir una partitura. */
    private fun requestImportTablature() {
        viewModelScope.launch { _sideEffect.send(TablatureSideEffect.RequestTablatureFile) }
    }

    /**
     * Importa la partitura elegida y **reemplaza** la tablatura del canto.
     *
     * La lectura del fichero y el parseo se hacen en `Dispatchers.Default`: abrir un ZIP o recorrer
     * un MusicXML de cientos de compases es trabajo de CPU, y la UI debe seguir a 60 FPS.
     */
    private fun importTablature(fileUri: String, fileName: String) {
        val useCase = importTablatureUseCase ?: run {
            viewModelScope.launch {
                _sideEffect.send(TablatureSideEffect.ShowMessage("La importación de partituras no está disponible"))
            }
            return
        }
        val songId = _uiState.value.songId
        if (songId.isBlank()) {
            viewModelScope.launch {
                _sideEffect.send(TablatureSideEffect.ShowMessage("No se pudo importar: no hay un canto abierto"))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(errorMessage = null) }
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    useCase(
                        fileUri = fileUri,
                        fileName = fileName,
                        songId = songId,
                        title = fileName.substringBeforeLast('.').ifBlank {
                            _uiState.value.songTitle
                        }.ifBlank { "Tablatura" }
                    )
                }
            }.onSuccess { result ->
                _sideEffect.send(
                    TablatureSideEffect.ShowMessage(
                        "Tablatura importada de ${result.format.label}: " +
                            "${result.measureCount} compases, ${result.noteCount} notas" +
                            if (result.replaced > 0) " (se reemplazó la anterior)" else ""
                    )
                )
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        errorMessage = "No se pudo importar la partitura: " +
                            (error.message ?: "formato no reconocido")
                    )
                }
            }
        }
    }

    // ── Compartir una tablatura ──────────────────────────────────────────────────

    /**
     * Comparte la tablatura [tablatureId] como **MusicXML**, el formato que abren MuseScore, Guitar
     * Pro, TuxGuitar y Finale. Compartir un formato propio de Canticora no serviría de nada fuera de
     * la app; MusicXML conserva compases, figuras, técnicas y afinación.
     */
    private fun shareTablature(tablatureId: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val tablature = state.tablatures.firstOrNull { it.id == tablatureId }
                ?: tablatureRepository.getTablatureById(tablatureId)
            if (tablature == null) {
                _sideEffect.send(TablatureSideEffect.ShowMessage("No se encontró la tablatura"))
                return@launch
            }
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    com.grafitto.laudate.domain.serializer.MusicXmlTabWriter.write(tablature)
                }
            }.onSuccess { xml ->
                _sideEffect.send(
                    TablatureSideEffect.ShareContent(
                        content = xml,
                        fileName = "${tablature.title.toFileName()}.musicxml",
                        mimeType = "application/vnd.recordare.musicxml"
                    )
                )
            }.onFailure { error ->
                _sideEffect.send(
                    TablatureSideEffect.ShowMessage(
                        "No se pudo preparar la tablatura: ${error.message ?: "error inesperado"}"
                    )
                )
            }
        }
    }

    /** Nombre de fichero seguro: sin separadores ni caracteres reservados. */
    private fun String.toFileName(): String =
        trim().map { if (it in "/\\:*?\"<>|") '-' else it }
            .joinToString("")
            .trim()
            .ifBlank { "tablatura" }
            .take(80)

    // ── Guardar / borrar ─────────────────────────────────────────────────────────

    private fun saveTablature() {
        val state = _uiState.value
        // Sin canto no hay a quién pertenecer: guardar aquí escribiría la tablatura en el último
        // canto abierto, que es justo lo que hacía que apareciera en canciones donde no era suya.
        if (state.songId.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Guarda el canto antes de añadirle una tablatura")
            }
            return
        }
        if (!state.isEditorValid) {
            _uiState.update { it.copy(errorMessage = "La tablatura está vacía: escribe al menos una nota") }
            return
        }
        val id = state.editingId ?: Uuid.random().toString()
        val title = state.editorTitle.ifBlank { state.songTitle.ifBlank { "Tablatura" } }
        val tablature = state.editorAsTablature().copy(id = id, title = title)
        viewModelScope.launch {
            tablatureRepository.saveTablature(tablature)
            // Guardar **NO** sale del editor: solo se confirma y se deja seguir editando. Se
            // reancla el «baseline» (con el título ya resuelto y el id) para que los cambios dejen
            // de contar como pendientes y las siguientes guardas **actualicen**, no dupliquen.
            _uiState.update {
                it.copy(
                    editingId = id,
                    editorIsNew = false,
                    editorTitle = title,
                    baselineTablature = tablature
                )
            }
            _sideEffect.send(TablatureSideEffect.ShowMessage("Tablatura guardada"))
        }
    }

    private fun deleteTablature(tablatureId: String) {
        viewModelScope.launch {
            val tab = tablatureRepository.getTablatureById(tablatureId)
            if (tab == null) {
                _sideEffect.send(TablatureSideEffect.ShowMessage("No se encontró la tablatura"))
                return@launch
            }
            lastDeleted = tab
            tablatureRepository.deleteTablature(tablatureId)
            _sideEffect.send(TablatureSideEffect.ShowUndoMessage("Tablatura eliminada"))
        }
    }

    /** Restaura la última tablatura eliminada (deshacer desde el snackbar). */
    fun undoDelete() {
        val tab = lastDeleted ?: return
        viewModelScope.launch {
            tablatureRepository.saveTablature(tab)
            lastDeleted = null
        }
    }

    // ── Transposición ─────────────────────────────────────────────────────────────

    /**
     * Aplica un modo de transposición a una tablatura. En [TabTranspositionMode.REFINGER] redigita
     * desde las alturas actuales.
     *
     * **Regla de oro**: si la tablatura fue **escrita a mano** y el modo cambia sus posiciones, se
     * **pide confirmación** ([PendingRefinger]) en lugar de redigitar en silencio.
     */
    private fun applyTransposition(tablatureId: String, mode: TabTranspositionMode) {
        viewModelScope.launch {
            val tab = tablatureRepository.getTablatureById(tablatureId) ?: return@launch
            if (mode == TabTranspositionMode.REFINGER &&
                com.grafitto.laudate.domain.tablature.TabTranspositionPolicy.requiresConfirmation(tab, mode)
            ) {
                _uiState.update { it.copy(pendingRefinger = PendingRefinger(tablatureId)) }
                return@launch
            }
            persistTransposition(tab, mode)
        }
    }

    /** Aplica el redigitado tras la confirmación del usuario. */
    private fun confirmRefinger() {
        val pending = _uiState.value.pendingRefinger ?: return
        _uiState.update { it.copy(pendingRefinger = null) }
        viewModelScope.launch {
            val tab = tablatureRepository.getTablatureById(pending.tablatureId) ?: return@launch
            persistTransposition(tab, TabTranspositionMode.REFINGER)
        }
    }

    private suspend fun persistTransposition(tab: Tablature, mode: TabTranspositionMode) {
        val result = when (mode) {
            TabTranspositionMode.CAPO, TabTranspositionMode.LOCKED -> tab
            TabTranspositionMode.REFINGER -> {
                val pitches = tab.soundedPitches()
                if (pitches.isEmpty()) tab
                else com.grafitto.laudate.domain.tablature.TabTranspositionPolicy.refinger(
                    tab, pitches, FingeringOptions(tuning = tab.tuning)
                )
            }
        }
        tablatureRepository.saveTablature(result)
        _sideEffect.send(TablatureSideEffect.ShowMessage("Tablatura actualizada"))
    }

    /**
     * Deriva una tablatura **equivalente para otro instrumento** (laúd ↔ guitarra, bajo…),
     * redigitando las mismas **alturas** con la nueva afinación. Se guarda como tablatura **nueva**
     * (la original se conserva) para poder elegir cuál se muestra.
     *
     * Si la tablatura de origen tenía posiciones escritas a mano, el redigitado las recalcula; por
     * eso se etiqueta el origen como [TabOrigin.GENERATED_FROM_SCORE] (nadie escribió estas nuevas
     * posiciones) y se conservan las **marcas del documento** (trémolo, repeticiones, voltas).
     */
    private fun refingerToTuning(tablatureId: String, tuning: Tuning) {
        viewModelScope.launch {
            val source = tablatureRepository.getTablatureById(tablatureId) ?: return@launch
            val pitches = source.soundedPitches()
            if (pitches.isEmpty()) {
                _sideEffect.send(TablatureSideEffect.ShowMessage("La tablatura no tiene notas que redigitar"))
                return@launch
            }
            // Si ya existe OTRA tablatura del MISMO canto con esa afinación, no se crea un duplicado
            // ciego: se actualiza la existente. Así, pulsar varias veces «Redigitar para…» no llena
            // el listado de copias del mismo instrumento.
            val existing = tablaturesForSongSnapshot().firstOrNull {
                it.id != source.id && it.tuning.name == tuning.name
            }
            val target = source.copy(
                id = existing?.id ?: Uuid.random().toString(),
                tuning = tuning,
                instrument = tuning.name,
                title = if (existing != null) existing.title else "${source.title} · ${tuning.name}"
            )
            val derived = com.grafitto.laudate.domain.tablature.TabTranspositionPolicy.refinger(
                target,
                pitches,
                FingeringOptions(tuning = tuning)
            )
            tablatureRepository.saveTablature(derived)
            val message = if (existing != null) {
                "Tablatura de ${tuning.name} actualizada"
            } else {
                "Tablatura de ${tuning.name} creada (la original se conserva)"
            }
            _sideEffect.send(TablatureSideEffect.ShowMessage(message))
        }
    }

    /**
     * Redigita la tablatura en edición **al tono del canto**: transporta sus alturas el intervalo
     * entre la tonalidad escrita y la del canto, y recalcula cuerdas y trastes (mismo
     * [FingeringEngine]). Corrige que la tablatura no siguiera el **cambio de tonalidad guardado**
     * del canto cuando se hace desde el propio editor.
     */
    private fun refingerEditorToSongKey() {
        val state = _uiState.value
        val pitches = state.editorAsTablature().soundedPitches()
        if (pitches.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Escribe alguna nota antes de redigitar") }
            return
        }
        val semitones = com.grafitto.laudate.domain.tablature.TabTranspositionPolicy
            .semitonesFromWrittenKey(state.editorAsTablature(), state.songKey)
        val target = pitches.map { com.grafitto.laudate.domain.model.score.ScorePitch.fromMidi(it.midi + semitones) }
        val result = com.grafitto.laudate.domain.tablature.TabTranspositionPolicy.refinger(
            state.editorAsTablature(),
            target,
            FingeringOptions(tuning = state.editorTuning)
        )
        // La tablatura queda escrita en el tono del canto (su tonalidad escrita pasa a ser esa).
        val newWrittenKey = com.grafitto.laudate.domain.model.music.PitchClass.parse(state.songKey)
            ?.let { com.grafitto.laudate.domain.model.score.scoreKeyOfTonic(it.chromaticIndex) }
        // Redigitar reescribe **todas** las cifras: es la operación más destructiva del editor, así
        // que tiene que poder deshacerse.
        if (result.measures != state.editorMeasures) pushHistory(state.editorMeasures)
        _uiState.update {
            it.copy(
                editorMeasures = result.measures,
                editorWrittenKey = newWrittenKey,
                canUndo = it.canUndo || result.measures != state.editorMeasures,
                canRedo = false,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            _sideEffect.send(TablatureSideEffect.ShowMessage("Tablatura redigitada al tono del canto"))
        }
    }

    /** Instantánea de las tablaturas conocidas del canto en edición (para evitar duplicados). */
    private suspend fun tablaturesForSongSnapshot(): List<Tablature> {
        val songId = _uiState.value.songId
        if (songId.isBlank()) return emptyList()
        return tablatureRepository.getTablaturesForSong(songId)
    }

    override fun onCleared() {
        super.onCleared()
        digitSoundJob?.cancel()
        playbackJob?.cancel()
        notePlayer?.release()
    }

    private companion object {
        /** Tope de compases que se pueden añadir de golpe con `Alt+Mayús+B`. */
        const val MAX_APPENDED_MEASURES = 64

        /**
         * Retardo (ms) antes de hacer sonar la nota escrita con el teclado: da margen a que llegue el
         * segundo dígito del traste para no sonar dos veces.
         */
        const val DIGIT_SOUND_DELAY_MS = 300L

        /** Nº máximo de pasos del historial de deshacer/rehacer (E2). */
        const val MAX_HISTORY = 100

        /**
         * **Compases con los que nace un editor nuevo** (punto 6 del usuario: «deberían aparecer dos
         * compases vacíos por defecto para empezar a trabajar»). Con uno solo, en cuanto se escribe
         * la primera nota no hay dónde seguir sin abrir el menú «Compás»; con dos, el segundo compás
         * ya está ahí y el flujo de escritura no se interrumpe.
         */
        const val NEW_EDITOR_MEASURES = 2

        /**
         * Paso del **zoom** del lienzo (`Ctrl` + `+`/`-`): ×1,25 por pulsación, como el zoom de
         * MuseScore, que también avanza en pasos geométricos y no lineales.
         */
        const val ZOOM_STEP = 1.25f

        /**
         * Límites del zoom. **Fuente única**: [TabNotationScale] (D-60). Antes había tres copias del
         * rango —aquí, en el deslizador del panel y en las preferencias— y se desincronizaban: el
         * deslizador no dejaba bajar del 70 % mientras el `Ctrl`+`-` sí, así que el mismo ajuste tenía
         * dos mínimos distintos según por dónde se llegara.
         */
        const val MIN_ZOOM = TabNotationScale.MIN_ZOOM
        const val MAX_ZOOM = TabNotationScale.MAX_ZOOM

        /**
         * Límites del **tamaño de las cifras** en el editor.
         *
         * Son los mismos que usa el visor para el zoom del número, para que el ajuste viaje igual en
         * las dos pantallas (es la misma preferencia global).
         */
        val MIN_NUMBER_SCALE = TabNotationScale.MIN_ZOOM
        val MAX_NUMBER_SCALE = TabNotationScale.MAX_ZOOM
    }
}
