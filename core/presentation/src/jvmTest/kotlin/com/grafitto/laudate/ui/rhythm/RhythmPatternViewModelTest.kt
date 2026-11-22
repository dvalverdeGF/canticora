package com.grafitto.laudate.ui.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumArticulation
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.StrumRange
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.domain.repository.RhythmPatternRepository
import com.grafitto.laudate.domain.rhythm.GuitarPatternLibrary
import com.grafitto.laudate.domain.rhythm.RhythmGridCell
import com.grafitto.laudate.domain.rhythm.RhythmGridResolution
import com.grafitto.laudate.domain.rhythm.RhythmPlayer
import com.grafitto.laudate.domain.usecase.ExportRhythmPatternsUseCase
import com.grafitto.laudate.domain.usecase.ImportRhythmPatternsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Repositorio de prueba reactivo: mantiene los patrones guardados en memoria. */
private class FakeRhythmRepository : RhythmPatternRepository {
    private val stored = MutableStateFlow<List<RhythmPattern>>(emptyList())
    val saved: List<RhythmPattern> get() = stored.value

    /** Precarga un patrón sin pasar por [savePattern] (simula lo ya existente). */
    fun seed(pattern: RhythmPattern) {
        stored.value = stored.value + pattern
    }

    override fun getAllPatterns(): Flow<List<RhythmPattern>> = stored
    override fun getCustomPatterns(): Flow<List<RhythmPattern>> = stored

    override suspend fun savePattern(pattern: RhythmPattern) {
        // Igual que el DAO real (`REPLACE` por id): guardar un patrón existente lo sustituye.
        stored.value = stored.value.filterNot { it.id == pattern.id } + pattern
    }

    override suspend fun deletePattern(patternId: String) {
        stored.value = stored.value.filterNot { it.id == patternId }
    }

    override suspend fun restorePattern(pattern: RhythmPattern) {
        stored.value = stored.value + pattern
    }
}

/** Reproductor de prueba que registra las solicitudes de detención. */
private class NoopRhythmPlayer : RhythmPlayer {
    override val isPlaying: Boolean = false
    var stopCalls: Int = 0

    override fun play(pattern: RhythmPattern, bpm: Int, onStep: (Int) -> Unit) = Unit
    override fun stop() {
        stopCalls++
    }
}

class RhythmPatternViewModelTest {

    private lateinit var repository: FakeRhythmRepository
    private lateinit var player: NoopRhythmPlayer
    private lateinit var viewModel: RhythmPatternViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository = FakeRhythmRepository()
        player = NoopRhythmPlayer()
        viewModel = RhythmPatternViewModel(
            rhythmPatternRepository = repository,
            rhythmPlayer = player,
            exportRhythmPatternsUseCase = ExportRhythmPatternsUseCase(repository, "test"),
            importRhythmPatternsUseCase = ImportRhythmPatternsUseCase(repository)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun state() = viewModel.uiState.value

    @Test
    fun tapOnEmptySlot_createsStruckEvent() {
        // El patrón arranca vacío: la rejilla está en aire.
        assertTrue(state().gridCells.all { it == RhythmGridCell.Air })

        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(0))

        assertEquals(StrumDirection.DOWN, state().gridCells[0].direction)
        assertTrue(state().editorEvents.isNotEmpty())
    }

    @Test
    fun tappingDifferentSlots_changesEachArrowIndependently() {
        // ↓ en el tiempo 1 (casilla 0) y ↑ en el "&" (casilla 1).
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(0))
        viewModel.processIntent(RhythmPatternUiIntent.SetPaletteCell(RhythmGridCell.Up))
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(1))

        assertEquals(StrumDirection.DOWN, state().gridCells[0].direction)
        assertEquals(StrumDirection.UP, state().gridCells[1].direction)
        // Dos golpes sonoros; el aire restante del compás se representa como silencio.
        assertEquals(2, state().editorEvents.count { it.isStruck })
    }

    @Test
    fun tappingSameSlotAgain_keepsItStruck() {
        // Repetir el mismo trazo no debe borrar la casilla.
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(0))
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(0))

        assertEquals(StrumDirection.DOWN, state().gridCells[0].direction)
    }

    @Test
    fun changingArrowOnExistingSlot_isReflectedInTheModel() {
        // Caso del usuario: crear un golpe y luego cambiar su flecha en la subdivisión.
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(0))
        assertEquals(StrumDirection.DOWN, state().editorEvents.first().direction)

        viewModel.processIntent(RhythmPatternUiIntent.SetPaletteCell(RhythmGridCell.Up))
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(0))

        assertEquals(StrumDirection.UP, state().editorEvents.first().direction)
        assertEquals(StrumDirection.UP, state().gridCells[0].direction)
    }

    @Test
    fun longPress_clearsSlotToAir() {
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(0))
        viewModel.processIntent(RhythmPatternUiIntent.ClearGridCell(0))

        assertEquals(RhythmGridCell.Air, state().gridCells[0])
    }

    @Test
    fun shortArrowFromPalette_isStoredInTheModel() {
        viewModel.processIntent(
            RhythmPatternUiIntent.SetPaletteCell(RhythmGridCell(StrumDirection.DOWN, range = StrumRange.BASS))
        )
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(0))

        assertEquals(StrumRange.BASS, state().editorEvents.first().range)
        assertEquals(StrumRange.BASS, state().gridCells[0].range)
    }

    @Test
    fun shortUpArrowFromPalette_isStoredInTheModel() {
        // La paleta ofrece flechas cortas en las dos direcciones: ↑ sobre las cuerdas agudas.
        viewModel.processIntent(
            RhythmPatternUiIntent.SetPaletteCell(RhythmGridCell(StrumDirection.UP, range = StrumRange.TREBLE))
        )
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(0))

        assertEquals(StrumDirection.UP, state().editorEvents.first().direction)
        assertEquals(StrumRange.TREBLE, state().editorEvents.first().range)
        assertEquals(StrumRange.TREBLE, state().gridCells[0].range)
    }

    @Test
    fun filledPattern_savesTheEditedArrows() = runTest {
        // Rellenar 4 negras y cambiar la segunda a ↑; luego guardar.
        viewModel.processIntent(RhythmPatternUiIntent.SetGridResolution(
            com.grafitto.laudate.domain.rhythm.RhythmGridResolution.QUARTER
        ))
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(0))
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(1))
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(2))
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(3))
        viewModel.processIntent(RhythmPatternUiIntent.SetPaletteCell(RhythmGridCell.Up))
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(1))

        viewModel.processIntent(RhythmPatternUiIntent.UpdateEditorName("Prueba"))
        assertTrue("El patrón debe completar el compás", state().isEditorValid)

        viewModel.processIntent(RhythmPatternUiIntent.SavePattern)
        testScheduler.advanceUntilIdle()

        assertFalse("El editor debe cerrarse tras guardar", state().isEditorOpen)
        assertEquals(1, repository.saved.size)
        val saved = repository.saved.first()
        assertEquals(4, saved.events.size)
        assertEquals(StrumDirection.DOWN, saved.events[0].direction)
        assertEquals(StrumDirection.UP, saved.events[1].direction)
    }

    @Test
    fun gridStaysInSyncWhenEditingInStructuredMode() {
        // Editar en el modo avanzado debe verse en la rejilla (mismo modelo).
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(0))
        viewModel.processIntent(RhythmPatternUiIntent.SetEventRange(0, StrumRange.TREBLE))

        assertEquals(StrumRange.TREBLE, state().gridCells[0].range)
    }

    @Test
    fun stopPreview_resetsPlaybackState() {
        // Al salir de la pantalla se envía StopPreview: el estado de reproducción queda limpio
        // aunque el reproductor sea externo (ámbito de actividad).
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(0))
        viewModel.processIntent(RhythmPatternUiIntent.TogglePreviewPlayback)
        assertTrue(state().isPlaying)

        viewModel.processIntent(RhythmPatternUiIntent.StopPreview)

        assertFalse(state().isPlaying)
        assertEquals(null, state().activeEventIndex)
        assertEquals(1, player.stopCalls)
    }

    @Test
    fun closingEditor_stopsPreview() {
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(0))
        viewModel.processIntent(RhythmPatternUiIntent.TogglePreviewPlayback)
        assertTrue(state().isPlaying)

        viewModel.processIntent(RhythmPatternUiIntent.CloseEditor)

        assertFalse("Cerrar el editor debe detener el audio", state().isPlaying)
        assertEquals(1, player.stopCalls)
    }

    @Test
    fun deletingPattern_offersUndoAndRestoresIt() = runTest {
        val custom = RhythmPattern(
            id = "custom-x",
            name = "Mi ritmo",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN))
        )
        repository.seed(custom)
        testScheduler.advanceUntilIdle()
        assertTrue(state().allPatterns.any { it.id == "custom-x" })

        viewModel.processIntent(RhythmPatternUiIntent.DeletePattern("custom-x"))
        testScheduler.advanceUntilIdle()
        assertTrue("El patrón debe haberse borrado", repository.saved.none { it.id == "custom-x" })

        viewModel.processIntent(RhythmPatternUiIntent.UndoDeletePattern)
        testScheduler.advanceUntilIdle()
        assertTrue("Deshacer debe restaurarlo", repository.saved.any { it.id == "custom-x" })
    }

    // ── Regresión: «edito las flechas y no me guarda los ritmos» ──────────────

    @Test
    fun editingBuiltInPattern_savesUnderTheSameId() = runTest {
        // Editar un patrón predefinido debe MODIFICARLO, no crear una copia con otro id y el
        // mismo nombre: la copia quedaba oculta bajo el original y parecía que no se guardaba.
        val builtIn = GuitarPatternLibrary.builtInPatterns.first { it.id == "builtin_4_4_basico" }
        repository.seed(builtIn)
        testScheduler.advanceUntilIdle()

        viewModel.processIntent(RhythmPatternUiIntent.OpenEditPattern(builtIn.id))
        assertEquals("Editar no debe abrir una copia", builtIn.id, state().editingPatternId)
        assertFalse("Un predefinido editado no es un patrón nuevo", state().editorIsNew)

        viewModel.processIntent(RhythmPatternUiIntent.SetPaletteCell(RhythmGridCell.Up))
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(1))
        viewModel.processIntent(RhythmPatternUiIntent.SavePattern)
        testScheduler.advanceUntilIdle()

        val saved = repository.saved.single { it.id == builtIn.id }
        assertEquals(StrumDirection.UP, saved.events[1].direction)
        assertEquals(
            "No debe quedar un duplicado con el mismo nombre",
            1,
            repository.saved.count { it.name == builtIn.name }
        )
    }

    @Test
    fun editingGrid_keepsArticulationsOfUntouchedSlots() = runTest {
        // Regresión: tocar una casilla reescribía el patrón entero y borraba TODOS los acentos.
        val pattern = RhythmPattern(
            id = "acc",
            name = "Con acentos",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = listOf(
                RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, StrumArticulation.ACCENT),
                RhythmEvent(RhythmFigure.QUARTER, StrumDirection.UP),
                RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
                RhythmEvent(RhythmFigure.QUARTER, StrumDirection.UP)
            )
        )
        repository.seed(pattern)
        testScheduler.advanceUntilIdle()
        viewModel.processIntent(RhythmPatternUiIntent.OpenEditPattern("acc"))
        viewModel.processIntent(RhythmPatternUiIntent.SetGridResolution(RhythmGridResolution.QUARTER))

        viewModel.processIntent(RhythmPatternUiIntent.SetPaletteCell(RhythmGridCell.Up))
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(2))

        assertEquals(StrumArticulation.ACCENT, state().editorEvents[0].articulation)
        assertEquals(StrumDirection.UP, state().editorEvents[2].direction)
        assertEquals(4, state().editorEvents.size)
        assertTrue("El compás debe seguir completo", state().isEditorValid)
    }

    @Test
    fun editingGrid_keepsTheFigureOfTheTouchedSlot() = runTest {
        // Tocar el ataque cambia el trazo, nunca la figura: una negra no se convierte en corcheas.
        val pattern = RhythmPattern(
            id = "fig",
            name = "Negras",
            timeSignature = TimeSignature.FOUR_FOUR,
            events = (1..4).map { RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN) }
        )
        repository.seed(pattern)
        testScheduler.advanceUntilIdle()
        viewModel.processIntent(RhythmPatternUiIntent.OpenEditPattern("fig"))
        viewModel.processIntent(RhythmPatternUiIntent.SetGridResolution(RhythmGridResolution.QUARTER))

        viewModel.processIntent(RhythmPatternUiIntent.SetPaletteCell(RhythmGridCell.Up))
        viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(1))

        assertEquals(4, state().editorEvents.size)
        assertTrue(state().editorEvents.all { it.figure == RhythmFigure.QUARTER })
    }

    @Test
    fun errorMessage_neverShowsNegativeBeats() {
        // Rellenar el compás y pedir una figura más: el aviso dice cuánto sobra, nunca un negativo.
        viewModel.processIntent(
            RhythmPatternUiIntent.SetGridResolution(RhythmGridResolution.QUARTER)
        )
        (0..3).forEach { viewModel.processIntent(RhythmPatternUiIntent.SetGridCell(it)) }
        viewModel.processIntent(RhythmPatternUiIntent.AddEvent(RhythmFigure.QUARTER))

        val message = state().errorMessage
        assertTrue("Debe avisar de que sobra tiempo: $message", message?.contains("Sobran") == true)
        assertFalse("El aviso no puede mostrar negativos: $message", message!!.contains("-"))
    }
}
