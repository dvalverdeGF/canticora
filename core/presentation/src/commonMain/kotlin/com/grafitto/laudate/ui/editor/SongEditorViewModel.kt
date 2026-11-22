package com.grafitto.laudate.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grafitto.laudate.domain.harmonic.HarmonicTransposer
import com.grafitto.laudate.domain.model.attachment.SongAttachment
import com.grafitto.laudate.domain.model.liturgy.CategoryLabelPolicy
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.music.PitchClass
import com.grafitto.laudate.domain.model.music.TempoMarking
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.parser.ChordProParser
import com.grafitto.laudate.domain.parser.ValidateChordProUseCase
import com.grafitto.laudate.domain.platform.newId
import com.grafitto.laudate.domain.platform.nowMillis
import com.grafitto.laudate.domain.repository.AttachmentRepository
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.PreferencesRepository
import com.grafitto.laudate.domain.repository.RhythmPatternRepository
import com.grafitto.laudate.domain.repository.SongRepository
import com.grafitto.laudate.domain.serializer.ChordProSerializer
import com.grafitto.laudate.domain.usecase.AddAttachmentToSongUseCase
import com.grafitto.laudate.domain.usecase.DeleteSongAttachmentUseCase
import com.grafitto.laudate.domain.usecase.DetectDuplicateSongUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SongEditorViewModel(
    private val songRepository: SongRepository,
    private val categoryRepository: CategoryRepository,
    private val rhythmPatternRepository: RhythmPatternRepository? = null,
    private val validateChordProUseCase: ValidateChordProUseCase = ValidateChordProUseCase(),
    private val detectDuplicateSongUseCase: DetectDuplicateSongUseCase? = null,
    private val attachmentRepository: AttachmentRepository? = null,
    private val addAttachmentToSongUseCase: AddAttachmentToSongUseCase? = null,
    private val deleteSongAttachmentUseCase: DeleteSongAttachmentUseCase? = null,
    private val attachmentStore: com.grafitto.laudate.domain.attachment.AttachmentStore? = null,
    /** Preferencias globales (nomenclatura de acordes) compartidas con el resto de la app. */
    private val preferencesRepository: PreferencesRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SongEditorUiState())
    val uiState: StateFlow<SongEditorUiState> = _uiState.asStateFlow()

    private val _sideEffect = Channel<SongEditorSideEffect>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    /** Trabajo en curso para los adjuntos (su ciclo de vida sigue mientras la pantalla esté abierta). */
    private var attachmentsJob: kotlinx.coroutines.Job? = null

    init {
        loadRhythmPatterns()
        observeNotationSystem()
        observePdfPreference()
    }

    /** La preferencia «modo de vista del PDF» es global: se recuerda entre sesiones. */
    private fun observePdfPreference() {
        val prefs = preferencesRepository ?: return
        viewModelScope.launch {
            prefs.songDisplayMode.collect { mode ->
                _uiState.update { it.copy(pdfDisplayMode = mode) }
            }
        }
    }

    /** La nomenclatura es global: se refleja aquí y se persiste al cambiarla. */
    private fun observeNotationSystem() {
        val prefs = preferencesRepository ?: return
        viewModelScope.launch {
            prefs.notationSystem.collect { system ->
                syncContentToNotation(system)
            }
        }
    }

    private fun loadRhythmPatterns() {
        val repository = rhythmPatternRepository ?: return
        viewModelScope.launch {
            repository.getAllPatterns().collect { patterns ->
                _uiState.update { it.copy(availablePatterns = patterns) }
            }
        }
    }

    fun processIntent(intent: SongEditorUiIntent) {
        when (intent) {
            is SongEditorUiIntent.LoadSongForEditing -> loadSong(intent.songId)
            is SongEditorUiIntent.UpdateTitle -> _uiState.update { it.copy(title = intent.title) }
            is SongEditorUiIntent.UpdateNumber -> _uiState.update { it.copy(number = intent.number) }
            is SongEditorUiIntent.UpdateAuthor -> _uiState.update { it.copy(author = intent.author) }
            is SongEditorUiIntent.UpdateOriginalKey -> setOriginalKey(intent.key)
            is SongEditorUiIntent.SetNotationSystem -> {
                applyNotationSystem(intent.system)
                viewModelScope.launch { preferencesRepository?.setNotationSystem(intent.system) }
            }
            is SongEditorUiIntent.SelectTimeSignature -> _uiState.update { current ->
                // Al cambiar de compás se limpia el patrón si ya no encaja.
                val patternStillValid = current.selectedPattern?.timeSignature == intent.timeSignature
                current.copy(
                    timeSignature = intent.timeSignature,
                    selectedPatternId = if (patternStillValid) current.selectedPatternId else null
                )
            }
            is SongEditorUiIntent.SelectRhythmPattern -> _uiState.update {
                it.copy(selectedPatternId = intent.patternId)
            }
            is SongEditorUiIntent.UpdateTempoBpm -> _uiState.update {
                val marking = TempoMarking.fromBpm(intent.bpm).italianName
                it.copy(tempoBpm = intent.bpm, tempoMarking = marking)
            }
            is SongEditorUiIntent.UpdateTempoMarking -> _uiState.update {
                val marking = TempoMarking.fromName(intent.marking)
                it.copy(
                    tempoMarking = intent.marking,
                    tempoBpm = marking?.suggestedBpm ?: it.tempoBpm
                )
            }
            is SongEditorUiIntent.UpdateCapo -> _uiState.update { it.copy(capo = intent.capo) }
            is SongEditorUiIntent.UpdateNotes -> _uiState.update { it.copy(notes = intent.notes) }
            is SongEditorUiIntent.UpdateChordProContent -> _uiState.update { it.copy(chordProContent = intent.content) }
            is SongEditorUiIntent.ToggleCategorySelection -> {
                _uiState.update { current ->
                    val newSet = current.selectedCategories.toMutableSet()
                    if (newSet.contains(intent.categoryName)) {
                        newSet.remove(intent.categoryName)
                    } else {
                        newSet.add(intent.categoryName)
                    }
                    current.copy(selectedCategories = newSet)
                }
            }
            is SongEditorUiIntent.TogglePreviewMode -> togglePreviewMode(intent.isPreview)
            is SongEditorUiIntent.SelectEditorTab -> selectEditorTab(intent.tab)
            is SongEditorUiIntent.InsertChordShortcut -> {
                _uiState.update { current ->
                    val newContent = current.chordProContent + "[${intent.chord}]"
                    current.copy(chordProContent = newContent)
                }
            }
            is SongEditorUiIntent.InsertDirectiveShortcut -> {
                _uiState.update { current ->
                    val newContent = current.chordProContent + "\n" + intent.directive + "\n"
                    current.copy(chordProContent = newContent)
                }
            }
            is SongEditorUiIntent.TransposeSong -> transposeSong(intent.semitonesDelta)
            is SongEditorUiIntent.ResetTransposition -> resetTransposition()
            is SongEditorUiIntent.SaveSong -> saveSong()

            // ── Importación / exportación ChordPro ──────────────────────────────
            is SongEditorUiIntent.SetImportPromptVisible ->
                _uiState.update { it.copy(showImportPrompt = intent.visible) }
            is SongEditorUiIntent.UpdateImportText ->
                _uiState.update { it.copy(importText = intent.text) }
            is SongEditorUiIntent.ValidateAndImport -> validateAndImport()
            is SongEditorUiIntent.ExportChordPro -> exportChordPro()
            is SongEditorUiIntent.DismissExport -> _uiState.update { it.copy(exportedChordPro = null) }

            // ── Compartir / exportar a PDF ──────────────────────────────────────────
            is SongEditorUiIntent.ShareChordPro -> shareChordPro()
            is SongEditorUiIntent.ShareChordProWithAttachments -> shareChordProWithAttachments()
            is SongEditorUiIntent.ExportPdf -> _uiState.update { it.copy(showPdfDialog = true) }
            is SongEditorUiIntent.ConfirmExportPdf -> confirmExportPdf(intent.mode)
            is SongEditorUiIntent.DismissPdfDialog -> _uiState.update { it.copy(showPdfDialog = false) }
            is SongEditorUiIntent.DeleteSong -> deleteSong()

            // ── Salida del editor ───────────────────────────────────────────────
            is SongEditorUiIntent.RequestExit -> requestExit()
            is SongEditorUiIntent.ConfirmDiscardChanges -> confirmDiscardChanges()
            is SongEditorUiIntent.DismissDiscardDialog ->
                _uiState.update { it.copy(showDiscardDialog = false) }

            // ── Duplicados ──────────────────────────────────────────────────────
            is SongEditorUiIntent.ConfirmOverwrite -> resolveDuplicate(overwrite = true)
            is SongEditorUiIntent.CreateAsNewSong -> resolveDuplicate(overwrite = false)
            is SongEditorUiIntent.DismissDuplicatePrompt ->
                _uiState.update { it.copy(duplicateMatch = null) }

            // ── Adjuntos ────────────────────────────────────────────────────────
            is SongEditorUiIntent.AddAttachmentFromUri -> addAttachmentFromUri(
                sourceUri = intent.sourceUri,
                displayName = intent.displayName,
                mimeType = intent.mimeType
            )
            is SongEditorUiIntent.AddAttachmentLink -> addAttachmentLink(intent.url, intent.title)
            is SongEditorUiIntent.DeleteAttachment -> deleteAttachment(intent.attachment)
            is SongEditorUiIntent.OpenAttachment -> viewModelScope.launch {
                _sideEffect.send(SongEditorSideEffect.OpenAttachment(intent.attachment))
            }
            is SongEditorUiIntent.ExportAttachment -> exportAttachment(
                intent.attachment,
                intent.destinationUri
            )
        }
    }

    private fun exportAttachment(attachment: SongAttachment, destinationUri: String) {
        val store = attachmentStore ?: return
        viewModelScope.launch {
            val result = runCatching { store.exportFile(attachment, destinationUri) }
                .getOrElse { Result.failure(it) }
            signal(
                result.fold(
                    onSuccess = { "Adjunto guardado en la carpeta elegida" },
                    onFailure = { error -> "No se pudo guardar: ${error.message ?: "error"}" }
                )
            )
        }
    }

    /**
     * Observa los adjuntos del canto en curso.
     *
     * En una canción **nueva** todavía no hay `id`, así que la sección de adjuntos permanece oculta
     * hasta que se guarde por primera vez (un adjunto necesita un canto al que pertenecer).
     */
    private fun observeAttachments(songId: String) {
        val repository = attachmentRepository ?: return
        attachmentsJob?.cancel()
        attachmentsJob = viewModelScope.launch {
            repository.getAttachments(songId).collect { attachments ->
                _uiState.update { it.copy(attachments = attachments) }
            }
        }
    }

    /** Importa un fichero elegido con el selector del sistema y lo asocia al canto. */
    private fun addAttachmentFromUri(sourceUri: String, displayName: String, mimeType: String?) {
        val useCase = addAttachmentToSongUseCase
        val songId = _uiState.value.songId
        if (useCase == null || songId == null) {
            signal("Guarda el canto antes de añadir adjuntos")
            return
        }
        viewModelScope.launch {
            runCatching { useCase(songId, sourceUri, displayName, mimeType) }
                .onSuccess { signal("Adjunto añadido") }
                .onFailure { signal("No se pudo añadir el adjunto: ${it.message ?: "error"}") }
        }
    }

    private fun addAttachmentLink(url: String, title: String?) {
        val useCase = addAttachmentToSongUseCase
        val songId = _uiState.value.songId
        if (useCase == null || songId == null) {
            signal("Guarda el canto antes de añadir adjuntos")
            return
        }
        viewModelScope.launch {
            runCatching { useCase.addLink(songId, url, title) }
                .onSuccess { signal("Enlace añadido") }
                .onFailure { signal("Enlace no válido: ${it.message ?: "error"}") }
        }
    }

    private fun deleteAttachment(attachment: SongAttachment) {
        val useCase = deleteSongAttachmentUseCase ?: return
        viewModelScope.launch {
            runCatching { useCase(attachment) }
                .onSuccess { signal("Adjunto eliminado") }
                .onFailure { signal("No se pudo eliminar el adjunto: ${it.message ?: "error"}") }
        }
    }

    private fun signal(message: String) {
        viewModelScope.launch { _sideEffect.send(SongEditorSideEffect.ShowMessage(message)) }
    }

    /**
     * Valida el texto ChordPro pegado por el usuario y, si es correcto, carga todos
     * los campos del editor (título, autor, tonalidad, tempo, capo y acordes).
     */
    private fun validateAndImport() {
        viewModelScope.launch {
            applyImport(_uiState.value.importText)
        }
    }

    private suspend fun applyImport(rawText: String) {
        val result = validateChordProUseCase(rawText)
        val song = result.song

        if (result.isValid && song != null) {
            _uiState.update {
                it.copy(
                    title = song.title,
                    number = song.number.orEmpty(),
                    author = song.author.orEmpty(),
                    originalKey = song.originalKey,
                    baseKey = song.originalKey,
                    timeSignature = song.rhythm?.let { r -> TimeSignature.parse(r) },
                    selectedPatternId = song.rhythmPatternId,
                    tempoBpm = song.tempoBpm ?: 0,
                    tempoMarking = song.tempoMarking.orEmpty(),
                    capo = song.capo,
                    notes = song.notes.orEmpty(),
                    chordProContent = removeHeaderDirectives(rawText),
                    // Momentos y tiempos se unen: el editor los presenta como una sola lista de etiquetas.
                    selectedCategories = (song.defaultLiturgicalMoments + song.defaultLiturgicalTimes).toSet(),
                    importIssues = result.issues,
                    lastImportValid = true,
                    showImportPrompt = false,
                    errorMessage = null
                )
            }
            _sideEffect.send(SongEditorSideEffect.ShowMessage("Canción importada correctamente"))
        } else {
            _uiState.update {
                it.copy(
                    importIssues = result.issues,
                    lastImportValid = false,
                    errorMessage = "No se pudo importar: revisa los problemas detectados."
                )
            }
        }
    }

    /**
     * Elimina del editor las directivas de cabecera ({title}, {key}, …) porque ya
     * se editan en los campos del formulario y se vuelven a emitir al exportar.
     */
    private fun removeHeaderDirectives(rawContent: String): String {
        val headerTags = setOf(
            "title", "t", "key", "k", "number", "n", "artist", "author", "a",
            "time", "rhythm", "tempo", "bpm", "capo"
        )
        return rawContent.lineSequence()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.startsWith("{") && trimmed.endsWith("}") &&
                    ChordProParser.parseDirective(trimmed).tag in headerTags
            }
            .joinToString("\n")
            .trim()
    }

    /** Serializa la canción actual (estado del editor) a texto ChordPro. */
    private fun exportChordPro() {
        val preview = buildSongFromState(id = _uiState.value.songId ?: newId())
        _uiState.update { it.copy(exportedChordPro = ChordProSerializer.serialize(preview)) }
    }

    /** Comparte el ChordPro por la hoja nativa de Android (WhatsApp, Telegram, correo…). */
    private fun shareChordPro() {
        val state = _uiState.value
        val song = buildSongFromState(id = state.songId ?: newId())
        viewModelScope.launch {
            _sideEffect.send(
                SongEditorSideEffect.ShareSong(
                    title = song.title.ifBlank { "Canto" },
                    chordPro = ChordProSerializer.serialize(song)
                )
            )
        }
    }

    /**
     * Empaqueta el canto con sus adjuntos (`canticora.song`) y lo comparte.
     *
     * Solo tiene sentido con un canto **ya guardado** (un paquete necesita el id para localizar sus
     * adjuntos); en una canción nueva se avisa de que primero hay que guardarla.
     */
    private fun shareChordProWithAttachments() {
        val state = _uiState.value
        val songId = state.songId
        if (songId == null) {
            signal("Guarda el canto antes de compartirlo con sus adjuntos")
            return
        }
        viewModelScope.launch {
            _sideEffect.send(
                SongEditorSideEffect.ShareSongPackage(
                    songId = songId,
                    title = state.title.ifBlank { "Canto" },
                    suggestedFileName = buildPackageFileName(state.title.ifBlank { "Canto" })
                )
            )
        }
    }

    /** Nombre de archivo seguro (sin caracteres prohibidos) para el paquete del canto. */
    private fun buildPackageFileName(rawTitle: String): String {
        val safeTitle = rawTitle
            .lowercase()
            .replace(Regex("[^a-z0-9áéíóúñü ]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .ifBlank { "canto" }
        return "$safeTitle.canticora"
    }

    /**
     * Prepara el PDF del canto. La escritura real la hace la capa de UI tras elegir destino con
     * el selector del sistema (SAF): el ViewModel no conoce `Uri` ni el filesystem.
     */
    private fun confirmExportPdf(mode: com.grafitto.laudate.domain.model.song.SongDisplayMode) {
        val state = _uiState.value
        val song = buildSongFromState(id = state.songId ?: newId())
        _uiState.update { it.copy(showPdfDialog = false, pdfDisplayMode = mode) }
        // La elección se recuerda: la próxima exportación ya vendrá preseleccionada.
        viewModelScope.launch {
            preferencesRepository?.setSongDisplayMode(mode)
            _sideEffect.send(
                SongEditorSideEffect.ExportSongPdf(
                    song = song,
                    suggestedFileName = buildPdfFileName(song),
                    mode = mode
                )
            )
        }
    }

    /** Elimina la canción en edición y vuelve al listado. */
    private fun deleteSong() {
        val songId = _uiState.value.songId ?: return
        viewModelScope.launch {
            songRepository.deleteSong(songId)
            _sideEffect.send(SongEditorSideEffect.NavigateBackOnDeleted)
        }
    }

    /** Nombre de archivo seguro (sin caracteres prohibidos) para el PDF exportado. */
    private fun buildPdfFileName(song: Song): String {
        val safeTitle = song.title
            .lowercase()
            .replace(Regex("[^a-z0-9áéíóúñü ]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .ifBlank { "canto" }
        return "$safeTitle.pdf"
    }

    /**
     * Construye el modelo de canción a partir del estado del editor, sin persistirlo.
     * Un único punto de verdad para guardar, compartir y exportar: así las tres operaciones
     * producen exactamente la misma canción.
     */
    /**
     * El usuario quiere salir del editor. Si hay cambios sin guardar se pide confirmación para no
     * perder el trabajo; si no los hay, se sale directamente.
     */
    private fun requestExit() {
        if (_uiState.value.hasUnsavedChanges) {
            _uiState.update { it.copy(showDiscardDialog = true) }
        } else {
            viewModelScope.launch { _sideEffect.send(SongEditorSideEffect.NavigateBack) }
        }
    }

    /** Descarta los cambios pendientes y sale del editor. */
    private fun confirmDiscardChanges() {
        _uiState.update { it.copy(showDiscardDialog = false).markAsSaved() }
        viewModelScope.launch { _sideEffect.send(SongEditorSideEffect.NavigateBack) }
    }

    /**
     * Cambia entre el editor y la vista previa.
     *
     * Al entrar en la vista previa se construye un **borrador en memoria** con el estado actual
     * del editor, usando el mismo camino que el guardado ([buildSongFromState]). Así la vista
     * previa muestra el canto tal y como quedará al persistirlo, y no la versión guardada: los
     * metadatos que no viven en el texto ChordPro (autor, número, capo, tempo, notas, compás,
     * patrón rítmico y categorías) también se reflejan. El borrador nunca se escribe en disco.
     */
    private fun togglePreviewMode(isPreview: Boolean) {
        if (!isPreview) {
            _uiState.update { it.copy(isPreviewMode = false, previewSong = null) }
            return
        }
        val draft = buildSongFromState(_uiState.value.songId ?: PREVIEW_DRAFT_ID)
        _uiState.update { it.copy(isPreviewMode = true, previewSong = draft) }
    }

    /** Cambia de pestaña. La vista previa reconstruye el borrador en memoria al entrar. */
    private fun selectEditorTab(tab: SongEditorTab) {
        when (tab) {
            SongEditorTab.PREVIEW -> {
                val draft = buildSongFromState(_uiState.value.songId ?: PREVIEW_DRAFT_ID)
                _uiState.update { it.copy(editorTab = tab, isPreviewMode = true, previewSong = draft) }
            }
            SongEditorTab.EDITOR, SongEditorTab.TABLATURES ->
                _uiState.update { it.copy(editorTab = tab, isPreviewMode = false) }
        }
    }

    private fun buildSongFromState(id: String): Song {
        val state = _uiState.value
        val now = nowMillis()
        val parsed = ChordProParser.parse(
            chordProContent = state.chordProContent,
            songId = id,
            fallbackTitle = state.title,
            fallbackKey = state.originalKey
        )
        // Los tiempos litúrgicos (Navidad, Cuaresma…) se guardan en su propia lista: si se
        // mezclaran con los momentos, el filtro por tiempo no los encontraría.
        val labels = CategoryLabelPolicy.splitByKind(state.selectedCategories, state.availableCategories)
        return parsed.copy(
            id = id,
            title = state.title.trim().ifBlank { "Sin título" },
            number = state.number.trim().ifEmpty { null },
            author = state.author.trim().ifEmpty { null },
            originalKey = state.originalKey,
            rhythm = state.timeSignature?.toString(),
            rhythmPatternId = state.selectedPatternId,
            tempoBpm = state.tempoBpm.takeIf { it > 0 },
            tempoMarking = state.tempoMarking.trim().ifEmpty { null },
            capo = state.capo,
            notes = state.notes.trim().ifEmpty { null },
            defaultLiturgicalMoments = labels.moments,
            defaultLiturgicalTimes = labels.times,
            createdAt = now,
            updatedAt = now
        )
    }

    /**
     * Cambia la tonalidad de la canción a [key] reescribiendo los acordes del ChordPro.
     *
     * A diferencia de elegir el tono en el visor (transposición **en vivo**), aquí la canción
     * queda **permanentemente en el nuevo tono** al guardar: la directiva `{key}` y todas las
     * etiquetas `[Acorde]` se transportan por el intervalo entre el tono actual y el elegido.
     */
    private fun setOriginalKey(key: String) {
        val state = _uiState.value
        val currentPitch = PitchClass.parse(state.originalKey) ?: PitchClass.C
        val targetPitch = PitchClass.parse(key) ?: return
        if (currentPitch == targetPitch) {
            _uiState.update { it.copy(originalKey = key, errorMessage = null) }
            return
        }
        // Intervalo más corto entre ambos tonos (−6..+6 semitonos).
        val delta = (targetPitch.chromaticIndex - currentPitch.chromaticIndex).let {
            if (it > 6) it - 12 else if (it < -6) it + 12 else it
        }
        val newContent = HarmonicTransposer.transposeChordProContent(
            content = state.chordProContent,
            semitones = delta,
            targetKeyPitch = targetPitch
        )
        _uiState.update {
            it.copy(
                originalKey = targetPitch.preferredEnglishName,
                chordProContent = newContent,
                errorMessage = null
            )
        }
    }

    /**
     * Transpone el contenido ChordPro y la tonalidad de la canción en edición.
     * Actualiza tanto las etiquetas de acordes como la directiva {key: ...}.
     */
    private fun transposeSong(delta: Int) {
        val state = _uiState.value
        val currentPitch = PitchClass.parse(state.originalKey) ?: PitchClass.C
        val newPitch = currentPitch.transpose(delta)

        val newContent = HarmonicTransposer.transposeChordProContent(
            content = state.chordProContent,
            semitones = delta,
            targetKeyPitch = newPitch
        )

        _uiState.update {
            it.copy(
                originalKey = newPitch.preferredEnglishName,
                chordProContent = newContent,
                errorMessage = null
            )
        }
    }

    private fun resetTransposition() {
        val state = _uiState.value
        val basePitch = PitchClass.parse(state.baseKey) ?: PitchClass.C
        val currentPitch = PitchClass.parse(state.originalKey) ?: PitchClass.C
        // Semitonos necesarios para volver de currentPitch a basePitch
        val delta = (basePitch.chromaticIndex - currentPitch.chromaticIndex).let {
            if (it > 6) it - 12 else if (it < -6) it + 12 else it
        }
        if (delta == 0) return

        val newContent = HarmonicTransposer.transposeChordProContent(
            content = state.chordProContent,
            semitones = delta,
            targetKeyPitch = basePitch
        )
        _uiState.update {
            it.copy(originalKey = basePitch.preferredEnglishName, chordProContent = newContent, errorMessage = null)
        }
    }

    /**
     * Cambia la notación de acordes **de toda la aplicación** y reescribe el texto ChordPro
     * del editor para que los acordes se vean en el nuevo sistema (`[Do]` ↔ `[C]`).
     *
     * OJO: no basta con cambiar el UiState; el ChordPro guardado es texto y debe convertirse.
     * La preferencia se persiste aparte (`preferencesRepository`), así que al observarla de
     * nuevo (p. ej. al abrir el editor) el texto ya está en el sistema correcto y no se
     * vuelve a convertir.
     */
    private fun applyNotationSystem(system: NotationSystem) {
        val state = _uiState.value
        if (state.notationSystem == system) return
        val referenceKey = PitchClass.parse(state.originalKey) ?: PitchClass.C
        val converted = HarmonicTransposer.convertChordProNotation(
            content = state.chordProContent,
            system = system,
            referenceKey = referenceKey
        )
        _uiState.update {
            it.copy(
                notationSystem = system,
                chordProContent = converted,
                lastAutoNotationSystem = system
            )
        }
    }

    /**
     * Convierte el texto a la notación activa cuando la preferencia global cambia desde otra
     * pantalla o al abrir el editor (una sola vez por sistema para no machacar la edición).
     */
    private fun syncContentToNotation(system: NotationSystem) {
        val state = _uiState.value
        if (state.notationSystem == system && state.lastAutoNotationSystem == system) return
        val referenceKey = PitchClass.parse(state.originalKey) ?: PitchClass.C
        val converted = HarmonicTransposer.convertChordProNotation(
            content = state.chordProContent,
            system = system,
            referenceKey = referenceKey
        )
        _uiState.update {
            it.copy(
                notationSystem = system,
                chordProContent = converted,
                lastAutoNotationSystem = system
            )
        }
    }

    private fun loadSong(songId: String?) {
        viewModelScope.launch {
            val categories = categoryRepository.getAllCategories().firstOrNull() ?: emptyList()
            if (songId != null) {
                val song = songRepository.getSongById(songId).firstOrNull()
                if (song != null) {
                    val rawContent = buildChordProFromSections(song.sections)
                    _uiState.update {
                        it.copy(
                            songId = song.id,
                            title = song.title,
                            number = song.number.orEmpty(),
                            author = song.author.orEmpty(),
                            originalKey = song.originalKey,
                            baseKey = song.originalKey,
                            timeSignature = song.rhythm?.let { TimeSignature.parse(it) },
                            selectedPatternId = song.rhythmPatternId,
                            tempoBpm = song.tempoBpm ?: 0,
                            tempoMarking = song.tempoMarking.orEmpty(),
                            capo = song.capo,
                            notes = song.notes.orEmpty(),
                            chordProContent = rawContent,
                            availableCategories = categories,
                            // Momentos y tiempos se unen: el editor los presenta como una sola lista de etiquetas.
                            selectedCategories = (song.defaultLiturgicalMoments + song.defaultLiturgicalTimes).toSet(),
                            // El texto recién cargado viene en la notación en que se guardó:
                            // obliga a reconvertirlo a la preferencia activa.
                            lastAutoNotationSystem = null
                        )
                    }
                    // Se muestra la canción en la nomenclatura que el usuario tiene activa.
                    syncContentToNotation(_uiState.value.notationSystem)
                    // Lo recién cargado es la referencia: todavía no hay cambios pendientes.
                    _uiState.update { it.markAsSaved() }
                    // Los adjuntos solo existen en cantos ya guardados.
                    observeAttachments(song.id)
                    return@launch
                }
            }
            _uiState.update {
                it.copy(
                    songId = null,
                    availableCategories = categories,
                    // Al crear una canción nueva se pregunta primero si desea importarla.
                    showImportPrompt = true,
                    chordProContent = """
                        {soc}
                        [C]Señor, ten pie[G]dad
                        [Am]Cristo, ten pie[F]dad
                        {eoc}
                    """.trimIndent()
                ).markAsSaved()
            }
        }
    }

    private fun buildChordProFromSections(sections: List<com.grafitto.laudate.domain.model.song.SongSection>): String {
        val sb = StringBuilder()
        sections.forEach { section ->
            when (section.sectionType) {
                com.grafitto.laudate.domain.model.song.SectionType.CHORUS -> {
                    sb.appendLine("{soc}")
                    sb.appendLine(rebuildSectionChordPro(section))
                    sb.appendLine("{eoc}")
                }
                com.grafitto.laudate.domain.model.song.SectionType.BRIDGE -> {
                    sb.appendLine("{sob}")
                    sb.appendLine(rebuildSectionChordPro(section))
                    sb.appendLine("{eob}")
                }
                else -> {
                    section.headerTitle?.let { sb.appendLine("[$it]") }
                    sb.appendLine(rebuildSectionChordPro(section))
                }
            }
        }
        return sb.toString().trim()
    }

    private fun rebuildSectionChordPro(section: com.grafitto.laudate.domain.model.song.SongSection): String {
        val sb = StringBuilder()
        section.lines.forEach { line ->
            val anchors = line.chords.sortedBy { it.characterIndex }
            var lastIdx = 0
            val lineSb = StringBuilder()
            for (anchor in anchors) {
                if (anchor.characterIndex >= lastIdx && anchor.characterIndex <= line.lyrics.length) {
                    lineSb.append(line.lyrics.substring(lastIdx, anchor.characterIndex))
                    lineSb.append("[${anchor.rawChordText}]")
                    lastIdx = anchor.characterIndex
                }
            }
            if (lastIdx < line.lyrics.length) {
                lineSb.append(line.lyrics.substring(lastIdx))
            }
            sb.appendLine(lineSb.toString())
        }
        return sb.toString().trimEnd()
    }

    private fun saveSong() {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.title.isBlank()) {
            _uiState.update { it.copy(errorMessage = "El título de la canción no puede estar vacío") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            val id = state.songId ?: newId()
            val song = buildSongFromState(id)

            // Antes de escribir se comprueba si el catálogo ya tiene ese mismo contenido con otro
            // id (p. ej. un ChordPro que nos han reenviado). La decisión es del usuario.
            val duplicate = detectDuplicateSongUseCase?.invoke(song)
            if (duplicate != null) {
                _uiState.update { it.copy(isSaving = false, duplicateMatch = duplicate) }
                return@launch
            }

            persistSong(song, createdAt = null)
        }
    }

    /**
     * Resuelve la coincidencia detectada.
     *
     * @param overwrite `true` reemplaza la copia existente conservando su identidad (id y fecha
     *   de alta); `false` guarda el canto como uno nuevo y deja intacta la copia que ya había.
     */
    private fun resolveDuplicate(overwrite: Boolean) {
        val match = _uiState.value.duplicateMatch ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(duplicateMatch = null, isSaving = true) }
            val candidate = if (overwrite) {
                match.candidate.copy(id = match.existing.id)
            } else {
                match.candidate.copy(id = newId())
            }
            persistSong(candidate, createdAt = if (overwrite) match.existing.createdAt else null)
        }
    }

    /** Escribe la canción y notifica el resultado. Un único camino de escritura para el editor. */
    private suspend fun persistSong(song: Song, createdAt: Long?) {
        val now = nowMillis()
        val toSave = song.copy(
            createdAt = createdAt ?: song.createdAt,
            updatedAt = now
        )
        // Se conserva el id que tenía la pantalla: un canto nuevo mantiene su identidad al
        // guardarse, de modo que la sección de adjuntos aparece sin recargar el editor.
        songRepository.saveSong(toSave)
        _uiState.update { it.copy(songId = toSave.id, isSaving = false).markAsSaved() }
        observeAttachments(toSave.id)
        _sideEffect.send(SongEditorSideEffect.ShowMessage("Canción guardada correctamente"))
        _sideEffect.send(SongEditorSideEffect.NavigateBackOnSaved)
    }

    private companion object {
        /** Identificador del borrador de vista previa de un canto aún sin guardar. */
        const val PREVIEW_DRAFT_ID = "preview-draft"
    }
}
