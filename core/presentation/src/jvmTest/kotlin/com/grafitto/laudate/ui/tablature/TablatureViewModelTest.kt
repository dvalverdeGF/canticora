package com.grafitto.laudate.ui.tablature

import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabBendAmount
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.TabTupletBracketType
import com.grafitto.laudate.domain.model.tablature.TabTupletNumberType
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.tablature.FretMarkStyle
import com.grafitto.laudate.domain.tablature.MinimStyle
import com.grafitto.laudate.domain.tablature.TabMeasureMeter
import com.grafitto.laudate.domain.tablature.TabNotationScale
import com.grafitto.laudate.domain.tablature.TabNoteRef
import com.grafitto.laudate.domain.tablature.TabStaffOptions
import com.grafitto.laudate.domain.tablature.TabStringData
import com.grafitto.laudate.domain.tablature.TabTranspositionMode
import com.grafitto.laudate.domain.tablature.TablatureEditing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Repositorio de prueba reactivo en memoria. */
private class FakeTablatureRepository : TablatureRepository {
    val stored = MutableStateFlow<List<Tablature>>(emptyList())

    override fun observeTablaturesForSong(songId: String): Flow<List<Tablature>> =
        MutableStateFlow(stored.value.filter { it.songId == songId })

    override suspend fun getTablaturesForSong(songId: String): List<Tablature> =
        stored.value.filter { it.songId == songId }

    override suspend fun getTablatureById(id: String): Tablature? = stored.value.firstOrNull { it.id == id }

    override suspend fun getAllTablatures(): List<Tablature> = stored.value

    override suspend fun saveTablature(tablature: Tablature) {
        stored.value = stored.value.filterNot { it.id == tablature.id } + tablature
    }

    override suspend fun deleteTablature(id: String) {
        stored.value = stored.value.filterNot { it.id == id }
    }

    override suspend fun deleteTablaturesForSong(songId: String) {
        stored.value = stored.value.filterNot { it.songId == songId }
    }

    override fun observeSongIdsWithTablatures(): kotlinx.coroutines.flow.Flow<Set<String>> =
        kotlinx.coroutines.flow.flowOf(stored.value.map { it.songId }.toSet())
}

/**
 * Repositorio de prueba que **emite por canto**: cada `songId` tiene su propio `MutableStateFlow`,
 * para poder simular que llega una emisión de un canto que ya no está abierto.
 */
private class PerSongFakeTablatureRepository : TablatureRepository {
    private val bySong = mutableMapOf<String, MutableStateFlow<List<Tablature>>>()
    val stored = MutableStateFlow<List<Tablature>>(emptyList())

    fun channel(songId: String): MutableStateFlow<List<Tablature>> =
        bySong.getOrPut(songId) { MutableStateFlow(stored.value.filter { it.songId == songId }) }

    override fun observeTablaturesForSong(songId: String): Flow<List<Tablature>> = channel(songId)

    override suspend fun getTablaturesForSong(songId: String): List<Tablature> =
        stored.value.filter { it.songId == songId }

    override suspend fun getTablatureById(id: String): Tablature? = stored.value.firstOrNull { it.id == id }

    override suspend fun getAllTablatures(): List<Tablature> = stored.value

    override suspend fun saveTablature(tablature: Tablature) {
        stored.value = stored.value.filterNot { it.id == tablature.id } + tablature
    }

    override suspend fun deleteTablature(id: String) {
        stored.value = stored.value.filterNot { it.id == id }
    }

    override suspend fun deleteTablaturesForSong(songId: String) {
        stored.value = stored.value.filterNot { it.songId == songId }
    }

    override fun observeSongIdsWithTablatures(): Flow<Set<String>> =
        kotlinx.coroutines.flow.flowOf(stored.value.map { it.songId }.toSet())
}

@OptIn(ExperimentalCoroutinesApi::class)
class TablatureViewModelTest {

    private lateinit var repository: FakeTablatureRepository
    private lateinit var viewModel: TablatureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        repository = FakeTablatureRepository()
        viewModel = TablatureViewModel(repository)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun openNew() {
        viewModel.processIntent(TablatureUiIntent.OpenNewEditor)
    }

    @Test
    fun openNewEditor_startsWithTwoGridMeasures() {
        openNew()
        val state = viewModel.uiState.value
        assertTrue(state.isEditorOpen)
        assertTrue(state.editorIsNew)
        // (Punto 6 del usuario, 2026-10-15) «Deberían aparecer dos compases vacíos por defecto para
        // empezar a trabajar»: con uno solo, en cuanto se escribe la primera nota hay que abrir el
        // menú «Compás» para seguir.
        assertEquals(2, state.editorMeasureCount)
        // (Punto 4) Y cada compás nace con la **rejilla predefinida**: una columna por unidad de la
        // subdivisión (8 corcheas en 4/4), no una sola columna de redonda. Antes el compás tenía una
        // única casilla y el recuadro del cursor abarcaba todo el compás.
        val expected = TablatureEditing.slotsPerMeasure(state.editorTime, state.editorResolution)
        assertEquals(expected, state.editorMeasures[0].slots.size)
        assertEquals(expected, state.editorMeasures[1].slots.size)
        assertTrue(
            "todas las columnas son silencios de la subdivisión",
            state.editorMeasures.all { m ->
                m.slots.all { it.rhythmicFigure == RhythmFigure.EIGHTH_REST }
            }
        )
        // Un silencio **no** es contenido: la tablatura aún no es guardable.
        assertFalse(state.isEditorValid)
    }

    @Test
    fun escribirConElTecladoFisicoAgrupaLosSilenciosIgualQueElDePantalla() = runTest {
        // (D-94) **Los dos caminos de escritura tienen que producir el mismo documento.** El usuario
        // teclea con el teclado del ordenador, y ese camino no partía la columna ni reequilibraba:
        // una negra metida en media corchea dejaba el compás en **4,5 negras** con siete silencios de
        // corchea detrás —«salen silencios a lo loco», «hace cosas raras».
        openNew()
        val iniciales = viewModel.uiState.value.editorMeasureCount
        // Tres negras en un `4/4`: queda un pulso, así que el compás se completa con **un** silencio
        // de negra agrupado, no con las corcheas sueltas de la rejilla.
        listOf(3, 3, 5).forEach { digit ->
            viewModel.processIntent(TablatureUiIntent.TypeFretDigit(digit))
            advanceTimeBy(400)
        }
        val state = viewModel.uiState.value
        val measure = state.editorMeasures[0]
        assertEquals("no se crean compases", iniciales, state.editorMeasureCount)
        assertEquals("las tres notas caen en el primer compás", 3, measure.slots.count { it.strings.isNotEmpty() })
        assertEquals(
            "el compás sigue durando 4/4, no 3,5",
            4.0,
            TabMeasureMeter.durationOf(measure, state.editorResolution),
            1e-6
        )
        assertEquals(
            "el hueco que sobra es UN silencio agrupado, no siete corcheas",
            1,
            measure.slots.count { it.strings.isEmpty() }
        )
        assertEquals(
            "y es de negra (el pulso 4 completo)",
            RhythmFigure.QUARTER_REST,
            measure.slots.last().rhythmicFigure
        )
    }

    @Test
    fun escribirOchoNotasSeguidasNoCreaCompasesNuevos() {
        // (D-93) **El flujo real del usuario**: abrir el editor y escribir ocho notas seguidas sin
        // tocar nada más. Es lo que rompía: cada nota creaba un compás porque el cursor comparaba la
        // duración **total** del compás (que la rejilla ya llenaba de silencios) con su capacidad.
        openNew()
        val iniciales = viewModel.uiState.value.editorMeasureCount
        val columnas = viewModel.uiState.value.editorMeasures[0].slots.size
        // La rejilla nace en **corcheas** (8 casillas) y la figura activa por defecto es la **negra**:
        // cada nota ocupa dos casillas, así que en un `4/4` caben **cuatro**. Se escriben cuatro y se
        // comprueba que NO se ha creado ningún compás.
        repeat(4) { viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(it)) }
        val state = viewModel.uiState.value
        assertEquals(
            "escribir notas NO puede crear compases nuevos",
            iniciales,
            state.editorMeasureCount
        )
        val notas = state.editorMeasures[0].slots.mapNotNull { it.strings.singleOrNull()?.position?.fret }
        assertEquals("las notas caen en el primer compás", listOf(0, 1, 2, 3), notas)
        // Tras la **cuarta** negra el compás está lleno, así que el cursor pasa al segundo: es lo
        // correcto (MuseScore hace lo mismo) y lo que se comprueba es que **no se ha creado un
        // tercero**.
        assertEquals(
            "el cursor pasa al segundo compás cuando el primero se llena",
            1,
            state.selectedSlot?.first
        )
        // Y el compás sigue durando 4/4.
        assertEquals(
            4.0,
            TabMeasureMeter.durationOf(state.editorMeasures[0], state.editorResolution),
            1e-6
        )
        assertTrue("la rejilla no se ha alargado", state.editorMeasures[0].slots.size <= columnas + 1)
    }

    @Test
    fun setMeasureSubdivision_resplitsTheRests_andKeepsTheNotes() {
        openNew()
        // Se escriben dos **corcheas** y se cambia la subdivisión del compás a negras.
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.SetRhythmicFigureAtCursor(RhythmFigure.EIGHTH))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(5))
        // La rejilla de corcheas queda **agrupada por pulso** ya al escribir: las dos corcheas
        // ocupan el pulso 1 y el silencio de detrás se agrupa (corchea + negra… ), no en 8 casillas.
        val before = viewModel.uiState.value.editorMeasures[0].slots.size
        assertTrue("el compás no se queda con 8 casillas", before < 8)

        viewModel.processIntent(
            TablatureUiIntent.SetMeasureSubdivision(0, TabResolution.QUARTER)
        )

        val measure = viewModel.uiState.value.editorMeasures[0]
        // Las notas siguen ahí, intactas y en su orden.
        assertEquals(listOf(3, 5), measure.slots.take(2).map { it.strings.single().fret })
        // Y el compás sigue durando 4/4 tras el re-reparto.
        assertEquals(
            4.0,
            TabMeasureMeter.durationOf(measure, TabResolution.EIGHTH),
            1e-6
        )
        assertEquals(TabResolution.QUARTER, viewModel.uiState.value.cursorSubdivision)
    }

    @Test
    fun setMeasureSubdivision_isUndoable() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetMeasureSubdivision(0, TabResolution.QUARTER))
        assertEquals(4, viewModel.uiState.value.editorMeasures[0].slots.size)
        viewModel.processIntent(TablatureUiIntent.Undo)
        assertEquals(8, viewModel.uiState.value.editorMeasures[0].slots.size)
    }

    // ── Anacrusa (pickup, D-34) ─────────────────────────────────────────────────────

    @Test
    fun togglePickup_marksTheFirstMeasureAndIsUndoable() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        assertFalse(viewModel.uiState.value.editorMeasures[0].isPickup)

        viewModel.processIntent(TablatureUiIntent.TogglePickup)

        val marked = viewModel.uiState.value
        assertTrue("el primer compás pasa a ser anacrusa", marked.editorMeasures[0].isPickup)
        assertFalse("el segundo compás no se toca", marked.editorMeasures[1].isPickup)
        assertTrue(marked.canUndo)

        viewModel.processIntent(TablatureUiIntent.Undo)
        assertFalse(
            "deshacer quita la anacrusa",
            viewModel.uiState.value.editorMeasures[0].isPickup
        )
    }

    @Test
    fun togglePickup_keepsTheCursorInsideTheShortenedMeasure() {
        // Al recortar los silencios del final el compás se acorta: el cursor no puede quedar fuera.
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 7))

        viewModel.processIntent(TablatureUiIntent.TogglePickup)

        val state = viewModel.uiState.value
        val slot = state.selectedSlot
        assertTrue("el cursor sigue en el primer compás", slot != null && slot.first == 0)
        assertTrue(
            "el cursor cabe en el compás recortado",
            slot!!.second <= state.editorMeasures[0].slots.lastIndex
        )
    }

    @Test
    fun setMeasureTime_changesTheMeasureAndIsUndoable() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        assertNull(viewModel.uiState.value.editorMeasures[0].time)

        viewModel.processIntent(
            TablatureUiIntent.SetMeasureTime(0, ScoreTimeSignature("3", 4))
        )

        val changed = viewModel.uiState.value
        assertEquals(ScoreTimeSignature("3", 4), changed.editorMeasures[0].time)
        assertEquals(
            "el compás se reequilibra a 3 negras",
            3,
            changed.editorMeasures[0].slots.size
        )
        assertEquals(ScoreTimeSignature("3", 4), changed.effectiveTimeAt(0))
        assertTrue(changed.canUndo)

        viewModel.processIntent(TablatureUiIntent.Undo)
        assertNull(
            "deshacer quita el cambio de compás",
            viewModel.uiState.value.editorMeasures[0].time
        )
    }

    @Test
    fun setMeasureTime_keepsTheCursorInsideTheShortenedMeasure() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 3))

        viewModel.processIntent(
            TablatureUiIntent.SetMeasureTime(0, ScoreTimeSignature("3", 4))
        )

        val state = viewModel.uiState.value
        val slot = state.selectedSlot
        assertTrue("el cursor sigue en el primer compás", slot != null && slot.first == 0)
        assertTrue(
            "el cursor cabe en el compás recortado",
            slot!!.second <= state.editorMeasures[0].slots.lastIndex
        )
    }

    @Test
    fun setMeasureTime_nullRestoresTheDocumentTime() {
        openNew()
        viewModel.processIntent(
            TablatureUiIntent.SetMeasureTime(0, ScoreTimeSignature("3", 4))
        )
        assertEquals(ScoreTimeSignature("3", 4), viewModel.uiState.value.editorMeasures[0].time)

        viewModel.processIntent(TablatureUiIntent.SetMeasureTime(0, null))

        val state = viewModel.uiState.value
        assertNull("sin override", state.editorMeasures[0].time)
        assertEquals("vuelve a la indicación del documento", state.editorTime, state.effectiveTimeAt(0))
    }

    // ── Compás irregular (D-36) ─────────────────────────────────────────────────────

    @Test
    fun toggleIrregular_marksTheMeasureAndIsUndoable() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        assertFalse(viewModel.uiState.value.editorMeasures[0].isIrregular)

        viewModel.processIntent(TablatureUiIntent.ToggleIrregular(0))

        val marked = viewModel.uiState.value
        assertTrue("el compás pasa a ser irregular", marked.editorMeasures[0].isIrregular)
        assertFalse("el segundo compás no se toca", marked.editorMeasures[1].isIrregular)
        assertTrue(marked.canUndo)

        viewModel.processIntent(TablatureUiIntent.Undo)
        assertFalse(
            "deshacer quita la marca",
            viewModel.uiState.value.editorMeasures[0].isIrregular
        )
    }

    @Test
    fun toggleIrregular_doesNotTouchTheContent() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        val before = viewModel.uiState.value.editorMeasures[0].slots

        viewModel.processIntent(TablatureUiIntent.ToggleIrregular(0))

        assertEquals(
            "marcar irregular no reescribe el compás",
            before,
            viewModel.uiState.value.editorMeasures[0].slots
        )
    }

    @Test
    fun toggleIrregular_offRebalancesToTheNominalDuration() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.ToggleIrregular(0))
        // Con la marca puesta se puede acortar el compás sin que «Completar compases» lo rellene.
        viewModel.processIntent(TablatureUiIntent.SetMeasureSubdivision(0, TabResolution.QUARTER))
        assertEquals(4, viewModel.uiState.value.editorMeasures[0].slots.size)

        viewModel.processIntent(TablatureUiIntent.ToggleIrregular(0))

        val state = viewModel.uiState.value
        assertFalse(state.editorMeasures[0].isIrregular)
        assertEquals(
            "al desmarcar vuelve a la duración nominal",
            4,
            state.editorMeasures[0].slots.size
        )
    }

    @Test
    fun toggleIrregular_ignoresAnIndexOutOfRange() {
        openNew()
        val before = viewModel.uiState.value.editorMeasures
        viewModel.processIntent(TablatureUiIntent.ToggleIrregular(99))
        assertEquals(before, viewModel.uiState.value.editorMeasures)
    }

    @Test
    fun writingAFret_buildsATablature_andBecomesValid() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        // Escribir la cifra en la columna del cursor CREA la nota (y el cursor avanza).
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(0))
        assertTrue(viewModel.uiState.value.isEditorValid)
        // La rejilla sigue **completa**: la negra escrita y el silencio agrupado por pulso
        // —negra (pulso 2) + blanca (pulsos 3-4)—, tres columnas para un 4/4 lleno.
        assertEquals(3, viewModel.uiState.value.editorMeasures[0].slots.size)
        assertEquals(1, viewModel.uiState.value.editorMeasures[0].slots[0].strings.size)
    }

    @Test
    fun tappingASlot_onlyMovesTheCursor_andNeverWrites() {
        openNew()

        // Tocar la partitura **no** escribe: solo señala dónde se va a escribir (como MuseScore y
        // Guitar Pro). Antes, un roce sobre una casilla vacía escribía el traste activo.
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))

        assertEquals(0 to 0, viewModel.uiState.value.selectedSlot)
        assertEquals(1, viewModel.uiState.value.selectedString)
        assertEquals(8, viewModel.uiState.value.editorMeasures[0].slots.size)
        assertTrue(viewModel.uiState.value.editorMeasures[0].slots[0].strings.isEmpty())
        assertFalse(viewModel.uiState.value.isEditorValid)
    }

    @Test
    fun writeFretAndAdvance_writesTheFret_andMovesToTheNextColumn() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))

        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(5))

        val slot = viewModel.uiState.value.editorMeasures[0].slots[0]
        assertEquals(5, slot.strings.first { it.stringNumber == 1 }.fret)
        assertEquals(0 to 1, viewModel.uiState.value.selectedSlot)
    }

    @Test
    fun selectedFigure_isSticky_forTheNotesWrittenAfter() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        // Se elige la **negra** una vez y se teclean tres cifras seguidas: las tres nacen negras.
        viewModel.processIntent(TablatureUiIntent.SetRhythmicFigureAtCursor(RhythmFigure.QUARTER))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(0))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(2))

        val figures = viewModel.uiState.value.editorMeasures[0].slots.map { it.rhythmicFigure }
        assertEquals(
            listOf(RhythmFigure.QUARTER, RhythmFigure.QUARTER, RhythmFigure.QUARTER),
            figures.take(3)
        )
        assertTrue(
            "el resto del compás son silencios de negra",
            figures.drop(3).all { it == RhythmFigure.QUARTER_REST }
        )
    }

    // ── Compases en contexto (insertar / duplicar / eliminar) ──────────────────

    @Test
    fun insertMeasureBefore_shiftsTheFollowingMeasures() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.SelectSlot(1, 0))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(4))

        viewModel.processIntent(TablatureUiIntent.InsertMeasureBefore(1))

        val state = viewModel.uiState.value
        assertEquals(4, state.editorMeasureCount)
        // El compás con la nota se ha desplazado a la posición 2 y el nuevo nace **con rejilla**, en
        // la **misma subdivisión** que su vecino (el compás con la nota quedó en negras al escribir
        // una negra, así que el compás nuevo también va en negras).
        assertEquals(4, state.editorMeasures[2].slots[0].strings.first().fret)
        assertEquals(4, state.editorMeasures[1].slots.size)
        assertTrue(
            "el compás nuevo hereda la subdivisión del vecino",
            state.editorMeasures[1].slots.all { it.rhythmicFigure == RhythmFigure.QUARTER_REST }
        )
        assertEquals(1 to 0, state.selectedSlot)
    }

    @Test
    fun insertMeasureAfter_placesTheNewMeasureRightBehind() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.InsertMeasureAfter(0))

        val state = viewModel.uiState.value
        assertEquals(3, state.editorMeasureCount)
        assertEquals(RhythmFigure.EIGHTH_REST, state.editorMeasures[1].slots[0].rhythmicFigure)
        assertEquals(1 to 0, state.selectedSlot)
    }

    @Test
    fun insertMeasuresBefore_insertsAsManyAsTheSelection() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(1, 0))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(7))

        // `Ctrl+Ins` de MuseScore: tantos compases como abarca la seleccion, delante de ella.
        viewModel.processIntent(TablatureUiIntent.InsertMeasuresBefore(1, 2))

        val state = viewModel.uiState.value
        assertEquals(4, state.editorMeasureCount)
        // El compas con la nota se ha desplazado dos puestos y los dos nuevos nacen con rejilla.
        assertEquals(7, state.editorMeasures[3].slots[0].strings.first().fret)
        assertEquals(1 to 0, state.selectedSlot)
    }

    @Test
    fun insertMeasuresBefore_neverInsertsZeroMeasures() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.InsertMeasuresBefore(0, 0))

        assertEquals(3, viewModel.uiState.value.editorMeasureCount)
    }

    @Test
    fun duplicateMeasure_copiesTheNotesBehindTheOriginal() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(9))

        viewModel.processIntent(TablatureUiIntent.DuplicateMeasure(0))

        val state = viewModel.uiState.value
        assertEquals(3, state.editorMeasureCount)
        assertEquals(9, state.editorMeasures[1].slots[0].strings.first().fret)
        assertEquals(1 to 0, state.selectedSlot)
    }

    @Test
    fun deleteMeasure_neverLeavesTheDocumentEmpty() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)

        viewModel.processIntent(TablatureUiIntent.DeleteMeasure(0))
        assertEquals(2, viewModel.uiState.value.editorMeasureCount)

        viewModel.processIntent(TablatureUiIntent.DeleteMeasure(0))
        assertEquals(1, viewModel.uiState.value.editorMeasureCount)

        // El último compás que queda no se puede borrar: una partitura sin compases no existe.
        viewModel.processIntent(TablatureUiIntent.DeleteMeasure(0))
        assertEquals(1, viewModel.uiState.value.editorMeasureCount)
    }

    @Test
    fun measureEdits_canBeUndone() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.InsertMeasureAfter(0))
        assertEquals(3, viewModel.uiState.value.editorMeasureCount)

        viewModel.processIntent(TablatureUiIntent.Undo)

        assertEquals(2, viewModel.uiState.value.editorMeasureCount)
    }

    @Test
    fun changingTimeSignature_keepsColumns() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(0))
        viewModel.processIntent(TablatureUiIntent.SetTimeSignature(ScoreTimeSignature("3", 4)))
        // Cambiar el compás es **metadato**: no recorta las columnas escritas. Las tres columnas
        // (negra + silencio de negra + blanca) siguen ahí y el compás dura lo que duraba.
        val measure = viewModel.uiState.value.editorMeasures[0]
        assertEquals(3, measure.slots.size)
        assertEquals(4.0, TabMeasureMeter.durationOf(measure, TabResolution.EIGHTH), 1e-6)
    }

    @Test
    fun changingResolution_keepsColumns() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(0))
        viewModel.processIntent(TablatureUiIntent.SetResolution(TabResolution.QUARTER))
        assertEquals(3, viewModel.uiState.value.editorMeasures[0].slots.size)
    }

    @Test
    fun typingFrets_keepsTheTypingOrder() {
        openNew()
        // `0 2 3 4` en las cuatro primeras columnas de la rejilla: el orden de escritura se conserva
        // (no se invierte). La rejilla de un `4/4` en corcheas ya trae las ocho columnas, así que no
        // hace falta insertar ninguna: el compás **no puede crecer** (D-73).
        listOf(0, 2, 3, 4).forEachIndexed { index, digit ->
            viewModel.processIntent(TablatureUiIntent.SelectSlot(0, index))
            viewModel.processIntent(TablatureUiIntent.TypeFretDigit(digit))
        }
        val frets = viewModel.uiState.value.editorMeasures.first().slots
            .map { slot -> slot.strings.firstOrNull()?.fret }
        assertEquals(listOf(0, 2, 3, 4), frets.take(4))
    }

    @Test
    fun insertingAColumnInAFullMeasure_splitsTheSlotInsteadOfGrowingTheMeasure() {
        openNew()
        val before = viewModel.uiState.value.editorMeasures[0]
        val beatsBefore = TabMeasureMeter.durationOf(before, viewModel.uiState.value.editorResolution)
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.InsertColumnAtCursorNow)
        val after = viewModel.uiState.value.editorMeasures[0]
        // La columna se **parte en dos** (8 → 9) y el compás sigue durando lo mismo: nunca se pasa.
        assertEquals(before.slots.size + 1, after.slots.size)
        assertEquals(
            beatsBefore,
            TabMeasureMeter.durationOf(after, viewModel.uiState.value.editorResolution),
            1e-6
        )
        assertEquals(0 to 1, viewModel.uiState.value.selectedSlot)
    }

    @Test
    fun insertingAColumnWhenTheSlotCannotBeSplit_movesToTheNextMeasure() {
        openNew()
        // En semicorcheas la columna ya no se puede partir (no hay figura de 1/32): el cursor salta
        // al compás siguiente en vez de añadir una columna de más.
        viewModel.processIntent(TablatureUiIntent.SetMeasureSubdivision(0, TabResolution.SIXTEENTH))
        val before = viewModel.uiState.value.editorMeasures[0].slots.size
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.InsertColumnAtCursorNow)
        assertEquals(before, viewModel.uiState.value.editorMeasures[0].slots.size)
        assertEquals(1 to 0, viewModel.uiState.value.selectedSlot)
    }

    @Test
    fun insertBarAfterCursor_splitsAtTheEnd_andKeepsTheOrder() {
        openNew()
        listOf(0, 2, 3, 4).forEachIndexed { index, digit ->
            viewModel.processIntent(TablatureUiIntent.SelectSlot(0, index))
            viewModel.processIntent(TablatureUiIntent.TypeFretDigit(digit))
        }
        // El cursor queda sobre la última columna escrita, así que partir el compás justo **detrás**
        // de ella: el primer compás conserva las cuatro notas y el segundo recibe los silencios que
        // quedaban por detrás (el compás nunca se queda corto).
        val cursor = viewModel.uiState.value.selectedSlot!!
        viewModel.processIntent(TablatureUiIntent.InsertBarAfter(cursor.first, cursor.second))
        val measures = viewModel.uiState.value.editorMeasures
        assertEquals(3, measures.size)
        assertEquals(listOf(0, 2, 3, 4), measures[0].slots.map { it.strings.firstOrNull()?.fret })
        assertTrue(
            "el compás nuevo solo lleva silencios",
            measures[1].slots.all { it.strings.isEmpty() && !it.isHeld }
        )
        assertEquals(1 to 0, viewModel.uiState.value.selectedSlot)
    }

    @Test
    fun changingTuning_clampsSelectedString() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 6))
        viewModel.processIntent(TablatureUiIntent.SetTuning(Tuning.BASS_4)) // 4 cuerdas
        assertEquals(4, viewModel.uiState.value.selectedString)
    }

    @Test
    fun addAndRemoveMeasure() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        assertEquals(3, viewModel.uiState.value.editorMeasureCount)
        viewModel.processIntent(TablatureUiIntent.RemoveMeasure)
        assertEquals(2, viewModel.uiState.value.editorMeasureCount)
    }

    @Test
    fun savingEmptyTablature_isRejected() = runTest {
        viewModel.processIntent(TablatureUiIntent.LoadForSong("s1", "Aleluya"))
        openNew()
        viewModel.processIntent(TablatureUiIntent.SaveTablature)
        advanceUntilIdle()
        assertEquals("La tablatura está vacía: escribe al menos una nota", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun savingTablature_persistsIt() = runTest {
        viewModel.processIntent(TablatureUiIntent.LoadForSong("s1", "Aleluya"))
        openNew()
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(0))
        viewModel.processIntent(TablatureUiIntent.UpdateEditorTitle("Riff"))
        viewModel.processIntent(TablatureUiIntent.SaveTablature)
        advanceUntilIdle()

        assertEquals(1, repository.stored.value.size)
        val saved = repository.stored.value.first()
        assertEquals("Riff", saved.title)
        assertEquals("s1", saved.songId)
    }

    @Test
    fun deleteTablature_canBeUndone() = runTest {
        viewModel.processIntent(TablatureUiIntent.LoadForSong("s1", "Aleluya"))
        openNew()
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(0))
        viewModel.processIntent(TablatureUiIntent.SaveTablature)
        advanceUntilIdle()

        val id = repository.stored.value.first().id
        viewModel.processIntent(TablatureUiIntent.DeleteTablature(id))
        advanceUntilIdle()
        assertTrue(repository.stored.value.isEmpty())

        viewModel.undoDelete()
        advanceUntilIdle()
        assertEquals(1, repository.stored.value.size)
    }

    @Test
    fun duplicate_opensEditorAsCopy() = runTest {
        viewModel.processIntent(TablatureUiIntent.LoadForSong("s1", "Aleluya"))
        openNew()
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(0))
        viewModel.processIntent(TablatureUiIntent.UpdateEditorTitle("Original"))
        viewModel.processIntent(TablatureUiIntent.SaveTablature)
        advanceUntilIdle()
        val id = repository.stored.value.first().id

        viewModel.processIntent(TablatureUiIntent.DuplicateTablature(id))
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertTrue(state.editorIsNew)
        assertEquals(null, state.editingId)
        assertTrue(state.editorTitle.contains("(copia)"))
    }

    @Test
    fun refingerOfHandwritten_requiresConfirmation() = runTest {
        // Una tablatura escrita a mano (laúd, sin cejilla) no debe redigitarse en silencio.
        viewModel.processIntent(TablatureUiIntent.LoadForSong("s1", "Aleluya"))
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetTuning(Tuning.LUTE))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(0))
        viewModel.processIntent(TablatureUiIntent.SaveTablature)
        advanceUntilIdle()
        val id = repository.stored.value.first().id

        viewModel.processIntent(TablatureUiIntent.ApplyTransposition(id, TabTranspositionMode.REFINGER))
        advanceUntilIdle()
        // Se pide confirmación en vez de aplicar el cambio.
        assertEquals(id, viewModel.uiState.value.pendingRefinger?.tablatureId)

        viewModel.processIntent(TablatureUiIntent.ConfirmRefinger)
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.pendingRefinger)
    }

    // ── Cambios sin guardar ──────────────────────────────────────────────────

    @Test
    fun openingEditor_hasNoUnsavedChanges() {
        openNew()
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun editing_marksUnsavedChanges() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(0))
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun requestExitWithoutChanges_closesEditor() {
        viewModel.processIntent(TablatureUiIntent.LoadForSong("s1", "Aleluya"))
        openNew()
        // Abrir y salir sin tocar nada no debe pedir confirmación.
        viewModel.processIntent(TablatureUiIntent.RequestExit)
        assertFalse(viewModel.uiState.value.showDiscardDialog)
        assertFalse(viewModel.uiState.value.isEditorOpen)
    }

    @Test
    fun requestExitWithChanges_asksForConfirmation() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(0))
        viewModel.processIntent(TablatureUiIntent.RequestExit)
        assertTrue(viewModel.uiState.value.showDiscardDialog)
        assertTrue(viewModel.uiState.value.isEditorOpen)
    }

    // ── Asociación a secciones ────────────────────────────────────────────────

    @Test
    fun sectionAssignment_isPersistedInTheTablature() = runTest {
        viewModel.processIntent(TablatureUiIntent.LoadForSong("s1", "Aleluya"))
        viewModel.setAvailableSections(listOf(TablatureSectionRef("sec-1", "Intro")))
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetEditorSection("sec-1"))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(0))
        viewModel.processIntent(TablatureUiIntent.SaveTablature)
        advanceUntilIdle()

        assertEquals("sec-1", repository.stored.value.first().sectionId)
    }

    // ── Entrada por teclado físico (Guitar Pro) ────────────────────────────────

    @Test
    fun typingDigits_writesAFretAndAccumulatesTheBuffer() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(1))
        assertEquals(1, viewModel.uiState.value.editorMeasures[0].slots[0].strings.first { it.stringNumber == 1 }.fret)
        assertEquals("1", viewModel.uiState.value.fretBuffer)
        // Segundo dígito: el traste pasa a 12 sobre la misma cuerda.
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(2))
        assertEquals(12, viewModel.uiState.value.editorMeasures[0].slots[0].strings.first { it.stringNumber == 1 }.fret)
        assertEquals("12", viewModel.uiState.value.fretBuffer)
    }

    @Test
    fun typingAnInvalidTwoDigitCombo_startsFreshBuffer() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(2))
        // 25 no es un traste válido: el dígito empieza un búfer nuevo y escribe 5.
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(5))
        assertEquals("5", viewModel.uiState.value.fretBuffer)
        assertEquals(5, viewModel.uiState.value.editorMeasures[0].slots[0].strings.first().fret)
    }

    @Test
    fun arrowKeys_moveTheCursorWithoutWriting() {
        openNew()
        // La rejilla de un `4/4` en corcheas ya trae ocho columnas: el cursor puede recorrerlas sin
        // escribir nada ni insertar columnas (el compás no puede crecer, D-73).
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(3))
        viewModel.processIntent(TablatureUiIntent.MoveCursor(0, 1, 0)) // derecha -> columna 1
        assertEquals(0 to 1, viewModel.uiState.value.selectedSlot)
        viewModel.processIntent(TablatureUiIntent.MoveCursor(0, -1, 0)) // izquierda -> columna 0
        assertEquals(0 to 0, viewModel.uiState.value.selectedSlot)
        viewModel.processIntent(TablatureUiIntent.MoveCursor(0, 0, 1)) // abajo: cuerda 2
        assertEquals(2, viewModel.uiState.value.selectedString)
    }

    @Test
    fun movingTheCursor_clearsTheFretBuffer() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(1))
        assertEquals("1", viewModel.uiState.value.fretBuffer)
        viewModel.processIntent(TablatureUiIntent.MoveCursor(0, 1, 0))
        assertEquals("", viewModel.uiState.value.fretBuffer)
    }

    @Test
    fun backspace_rectifiesTheBufferThenClearsTheNote() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(1))
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(2)) // traste 12
        // Primer retroceso: corrige el búfer a «1» y reescribe el traste 1.
        viewModel.processIntent(TablatureUiIntent.BackspaceCursor)
        assertEquals("1", viewModel.uiState.value.fretBuffer)
        assertEquals(1, viewModel.uiState.value.editorMeasures[0].slots[0].strings.first().fret)
        // Segundo retroceso: vacía el búfer (la nota del traste 1 sigue escrita).
        viewModel.processIntent(TablatureUiIntent.BackspaceCursor)
        assertEquals("", viewModel.uiState.value.fretBuffer)
        assertTrue(viewModel.uiState.value.isEditorValid)
        // Tercer retroceso: sin búfer, borra la nota de la cuerda activa.
        viewModel.processIntent(TablatureUiIntent.BackspaceCursor)
        assertFalse(viewModel.uiState.value.isEditorValid)
    }

    @Test
    fun deleteAtCursor_removesTheNoteWithoutMoving() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(3))
        assertEquals(0 to 0, viewModel.uiState.value.selectedSlot)
        viewModel.processIntent(TablatureUiIntent.DeleteAtCursor)
        assertFalse(viewModel.uiState.value.isEditorValid)
        assertEquals(0 to 0, viewModel.uiState.value.selectedSlot)
    }

    @Test
    fun writingANote_advancesTheCursor() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        // (ADR-020) Escribir **es** avanzar: `WriteFretAndAdvance` deja el cursor una columna más allá.
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        // La negra escrita ocupa la columna 0 y el cursor pasa a la **columna 1**: la siguiente
        // posición de la rejilla. El compás se **reequilibra** al escribir (el dominio reescribe la
        // rejilla: conserva las columnas con contenido y reparte la duración del compás), así que
        // el cursor depende del compás resultante y no de cuántas columnas se insertaron a mano
        // antes de escribir.
        assertEquals(0 to 1, viewModel.uiState.value.selectedSlot)
        assertEquals(
            "la nota queda escrita en la columna 0",
            3,
            viewModel.uiState.value.editorMeasures[0].slots[0].strings.single().fret
        )
    }

    @Test
    fun tappingASlot_clearsTheFretBuffer() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(9))
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 1))
        assertEquals("", viewModel.uiState.value.fretBuffer)
    }

    // ── Modo de entrada de notas (ADR-020, paridad con MuseScore 4) ────────────────

    @Test
    fun toggleInputMode_switchesTheModeOnAndOff() {
        openNew()
        assertFalse(viewModel.uiState.value.isInputMode)
        viewModel.processIntent(TablatureUiIntent.ToggleInputMode)
        assertTrue(viewModel.uiState.value.isInputMode)
        // Al entrar, el cursor se coloca si no había ninguno (para poder escribir).
        assertTrue(viewModel.uiState.value.selectedSlot != null)
        viewModel.processIntent(TablatureUiIntent.ToggleInputMode)
        assertFalse(viewModel.uiState.value.isInputMode)
    }

    @Test
    fun leaveInputMode_clearsTheModeAndTheSelection() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.ToggleInputMode)
        viewModel.processIntent(TablatureUiIntent.SelectMeasureRange(0, 1))
        viewModel.processIntent(TablatureUiIntent.LeaveInputMode)
        val state = viewModel.uiState.value
        assertFalse(state.isInputMode)
        assertNull("Esc también quita la selección", state.selection)
    }

    @Test
    fun tappingAStringInInputMode_writesAnOpenString() {
        // Manual de MuseScore: «Click on a string and MuseScore will enter a 0 (zero), which means
        // "open string"».
        openNew()
        viewModel.processIntent(TablatureUiIntent.ToggleInputMode)
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 3))
        val pluck = viewModel.uiState.value.editorMeasures[0].slots[0].strings
            .firstOrNull { it.stringNumber == 3 }
        assertEquals("el toque en modo entrada escribe un 0 en la cuerda tocada", 0, pluck?.fret)
    }

    @Test
    fun tappingAnExistingFretInInputMode_selectsIt_andDoesNotOverwriteItWithZero() {
        // D-67: pulsar una cifra que ya existe la **selecciona**. Antes se pisaba con un `0` y el
        // cursor avanzaba, así que el usuario veía «el número se convierte en 0» y «se activa el
        // siguiente».
        openNew()
        viewModel.processIntent(TablatureUiIntent.ToggleInputMode)
        // El primer toque escribe un 0 en (0,0) y avanza a (0,1); la cifra 7 se teclea ahí.
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 3))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(7))
        // Se vuelve a pulsar la cifra 7 que ya existe.
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 1, 3))

        val state = viewModel.uiState.value
        val pluck = state.editorMeasures[0].slots[1].strings.first { it.stringNumber == 3 }
        assertEquals("la cifra escrita no se pisa con un 0", 7, pluck.fret)
        assertEquals("el cursor se queda en la casilla pulsada", 0 to 1, state.selectedSlot)
        assertEquals("la cifra activa refleja la nota pulsada", 7, state.selectedFret)
    }

    @Test
    fun tappingAStringWithTheModeOff_doesNotWrite() {
        openNew()
        // Sin modo de entrada, un roce no puede estropear lo escrito: solo mueve el cursor.
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 3))
        assertTrue(
            "la casilla sigue vacía",
            viewModel.uiState.value.editorMeasures[0].slots[0].strings.isEmpty()
        )
        assertEquals(0 to 0, viewModel.uiState.value.selectedSlot)
    }

    @Test
    fun typingADigitWithTheModeOff_entersTheModeAndWrites() {
        // **Desviación firmada del ADR-020**: la primera cifra entra en el modo y escribe (ahorra un
        // toque por nota en tableta).
        openNew()
        assertFalse(viewModel.uiState.value.isInputMode)
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(3))
        assertTrue("la cifra entra en el modo de entrada", viewModel.uiState.value.isInputMode)
        assertEquals(3, viewModel.uiState.value.editorMeasures[0].slots[0].strings.first().fret)
    }

    @Test
    fun setActiveVoice_clampsToTheFourVoices() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetActiveVoice(3))
        assertEquals(3, viewModel.uiState.value.activeVoice)
        viewModel.processIntent(TablatureUiIntent.SetActiveVoice(9))
        assertEquals("fuera de rango cae en la voz 1", 1, viewModel.uiState.value.activeVoice)
        viewModel.processIntent(TablatureUiIntent.SetActiveVoice(0))
        assertEquals(1, viewModel.uiState.value.activeVoice)
    }

    // ── Edición del traste por teclado (ADR-020) ────────────────────────────────────

    @Test
    fun nudgeFret_subesYBajaElTrasteSinCambiarDeCuerda() {
        // Manual: «To increment or decrement, without changing the string: Press Up/Down».
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(5))
        val string = viewModel.uiState.value.selectedString
        viewModel.processIntent(TablatureUiIntent.NudgeFret(1))
        var pluck = viewModel.uiState.value.editorMeasures[0].slots[0].strings.first()
        assertEquals(6, pluck.fret)
        assertEquals("la cuerda no cambia", string, pluck.stringNumber)
        viewModel.processIntent(TablatureUiIntent.NudgeFret(-1))
        pluck = viewModel.uiState.value.editorMeasures[0].slots[0].strings.first()
        assertEquals(5, pluck.fret)
    }

    @Test
    fun nudgeFret_withoutANote_doesNothing() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.NudgeFret(1))
        assertFalse("sin nota no hay nada que subir", viewModel.uiState.value.isEditorValid)
    }

    @Test
    fun adjustFigure_changesTheActiveFigureAndTheNote() {
        // Manual: «Halve selected duration — Q», «Double selected duration — W».
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.SetFigureForCursor(RhythmFigure.QUARTER))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        // Escribir avanza el cursor: se vuelve a la casilla escrita.
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.AdjustFigure(1)) // W: alarga
        assertEquals(RhythmFigure.HALF, viewModel.uiState.value.selectedFigure)
        assertEquals(RhythmFigure.HALF, viewModel.uiState.value.editorMeasures[0].slots[0].rhythmicFigure)
    }

    @Test
    fun toggleDotAtCursor_setsTheDottedFigure() {
        // Manual: «Toggle duration dot — .».
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.SetFigureForCursor(RhythmFigure.QUARTER))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(2))
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.ToggleDotAtCursor)
        assertEquals(RhythmFigure.DOTTED_QUARTER, viewModel.uiState.value.editorMeasures[0].slots[0].rhythmicFigure)
    }

    @Test
    fun insertRestShortcut_insertsARestAtTheCursor() {
        // Manual: «Press ; (semicolon) to enter a rest of the selected duration».
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.SetFigureForCursor(RhythmFigure.QUARTER))
        viewModel.processIntent(TablatureUiIntent.InsertRestAtCursorShortcut)
        assertEquals(
            RhythmFigure.QUARTER_REST,
            viewModel.uiState.value.editorMeasures[0].slots[0].rhythmicFigure
        )
    }

    @Test
    fun toggleGhostNote_marksTheNoteAsGhost() {
        // Manual: «Press Shift+X to toggle ghost noteheads on/off».
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.ToggleGhostNoteAtCursor)
        assertTrue(
            viewModel.uiState.value.editorMeasures[0].slots[0].strings.first().has(TabTechnique.GHOST)
        )
        // Volver a pulsar la quita.
        viewModel.processIntent(TablatureUiIntent.ToggleGhostNoteAtCursor)
        assertFalse(
            viewModel.uiState.value.editorMeasures[0].slots[0].strings.first().has(TabTechnique.GHOST)
        )
    }

    @Test
    fun applyArticulationAtCursor_togglesTheArticulation() {
        // Manual: «Add articulation: accent — Shift+V», «staccato — Shift+S»…
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(
            TablatureUiIntent.ApplyArticulationAtCursor(
                com.grafitto.laudate.domain.model.tablature.TabArticulation.ACCENT
            )
        )
        assertTrue(
            viewModel.uiState.value.editorMeasures[0].slots[0].strings.first()
                .articulations.contains(com.grafitto.laudate.domain.model.tablature.TabArticulation.ACCENT)
        )
    }

    @Test
    fun applyArticulationAtCursor_combinaElAcentoConElPicadoEnSforzato() {
        // (D-40) `Shift+V` es **sforzato** en MuseScore (`shortcuts.json:138`), no acento suelto:
        // acento + picado se dibujan con un solo glifo (`articAccentStaccatoAbove`, U+E4B0).
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(
            TablatureUiIntent.ApplyArticulationAtCursor(
                com.grafitto.laudate.domain.model.tablature.TabArticulation.ACCENT_STACCATO
            )
        )
        assertEquals(
            setOf(com.grafitto.laudate.domain.model.tablature.TabArticulation.ACCENT_STACCATO),
            viewModel.uiState.value.editorMeasures[0].slots[0].strings.first().articulations
        )
    }

    @Test
    fun applyArticulationAtCursor_quitarElPicadoDejaElAcento() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(
            TablatureUiIntent.ApplyArticulationAtCursor(
                com.grafitto.laudate.domain.model.tablature.TabArticulation.ACCENT_STACCATO
            )
        )
        viewModel.processIntent(
            TablatureUiIntent.ApplyArticulationAtCursor(
                com.grafitto.laudate.domain.model.tablature.TabArticulation.STACCATO
            )
        )
        assertEquals(
            setOf(com.grafitto.laudate.domain.model.tablature.TabArticulation.ACCENT),
            viewModel.uiState.value.editorMeasures[0].slots[0].strings.first().articulations
        )
    }

    @Test
    fun dragNoteToAdjacentString_keepsThePitch() {
        // Manual: «Drag a fret mark up or down to an adjacent string» (sin cambiar la altura).
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        // Se escribe un Re5 (traste 10 de la primera cuerda, Mi4 al aire = 64 + 10) para que quepa en
        // la segunda (Si3 = 59, así que Re5 = traste 15).
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 0, 1, 10))
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.DragNoteToAdjacentString(1))
        val moved = viewModel.uiState.value.editorMeasures[0].slots[0].strings.first()
        assertEquals("la nota pasa a la cuerda 2 con el traste recalculado", 2, moved.stringNumber)
        assertEquals(15, moved.fret)
    }

    // ── Selección de notas (Fase 2, ADR-020) ────────────────────────────────────────

    private fun writeTwoNotes() {
        // Las dos notas van en **columnas distintas**: el compás se reequilibra al escribir (el
        // dominio reescribe la rejilla conservando las columnas con contenido), así que dos
        // escrituras seguidas en la **misma** columna acabarían en una sola nota.
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(5))
    }

    @Test
    fun selectNote_replacesTheSelection() {
        openNew()
        writeTwoNotes()
        viewModel.processIntent(TablatureUiIntent.SelectNote(0, 0, 1))
        assertEquals(1, viewModel.uiState.value.noteSelection?.size)
        // Seleccionar otra sustituye la anterior (clic simple).
        viewModel.processIntent(TablatureUiIntent.SelectNote(0, 1, 1))
        assertEquals(1, viewModel.uiState.value.noteSelection?.size)
        assertTrue(
            viewModel.uiState.value.noteSelection!!.contains(
                com.grafitto.laudate.domain.tablature.TabNoteRef(0, 1, 1)
            )
        )
    }

    @Test
    fun toggleNoteSelection_addsAndRemoves() {
        // **Ctrl+clic**: alterna.
        openNew()
        writeTwoNotes()
        viewModel.processIntent(TablatureUiIntent.ToggleNoteSelection(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.ToggleNoteSelection(0, 1, 1))
        assertEquals("dos notas seleccionadas", 2, viewModel.uiState.value.noteSelection?.size)
        viewModel.processIntent(TablatureUiIntent.ToggleNoteSelection(0, 1, 1))
        assertEquals(1, viewModel.uiState.value.noteSelection?.size)
    }

    @Test
    fun extendNoteSelection_coversTheRange() {
        // **Mayús+clic**: extiende a lo largo del tiempo en la misma cuerda.
        openNew()
        writeTwoNotes()
        viewModel.processIntent(TablatureUiIntent.SelectNote(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.ExtendNoteSelection(0, 2, 1))
        assertEquals(3, viewModel.uiState.value.noteSelection?.size)
    }

    @Test
    fun extendNoteSelectionRect_addsStrings() {
        // **Mayús+↓**: el rectángulo abarca también las cuerdas intermedias, no solo el tiempo.
        openNew()
        writeTwoNotes()
        viewModel.processIntent(TablatureUiIntent.SelectNote(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.ExtendNoteSelectionRect(0, 1))
        val state = viewModel.uiState.value
        assertEquals(2, state.noteSelection?.size)
        assertTrue(state.noteSelection!!.contains(TabNoteRef(0, 0, 2)))
        assertEquals("el ancla no se mueve", TabNoteRef(0, 0, 1), state.noteSelectionAnchor)
        assertEquals(TabNoteRef(0, 0, 2), state.noteSelectionFocus)
    }

    @Test
    fun extendNoteSelectionRect_shrinksWhenSteppingBack() {
        // La goma: volver sobre los pasos **encoge** la selección en vez de acumular notas.
        openNew()
        writeTwoNotes()
        viewModel.processIntent(TablatureUiIntent.SelectNote(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.ExtendNoteSelectionRect(1, 0))
        viewModel.processIntent(TablatureUiIntent.ExtendNoteSelectionRect(1, 0))
        assertEquals(3, viewModel.uiState.value.noteSelection?.size)
        viewModel.processIntent(TablatureUiIntent.ExtendNoteSelectionRect(-1, 0))
        assertEquals(2, viewModel.uiState.value.noteSelection?.size)
    }

    @Test
    fun extendNoteSelectionRect_clampsToTheStringsOfTheTablature() {
        // `Mayús+↑` en la primera cuerda no se sale del mástil.
        openNew()
        writeTwoNotes()
        viewModel.processIntent(TablatureUiIntent.SelectNote(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.ExtendNoteSelectionRect(0, -1))
        val state = viewModel.uiState.value
        assertEquals(TabNoteRef(0, 0, 1), state.noteSelectionFocus)
        assertEquals(1, state.noteSelection?.size)
    }

    @Test
    fun extendNoteSelectionRect_crossesMeasures() {
        // Al agotar las columnas del compás el foco pasa al siguiente, como el cursor.
        openNew()
        writeTwoNotes()
        val slots = viewModel.uiState.value.editorMeasures[0].slots.size
        viewModel.processIntent(TablatureUiIntent.SelectNote(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.ExtendNoteSelectionRect(slots, 0))
        val state = viewModel.uiState.value
        assertEquals(TabNoteRef(1, 0, 1), state.noteSelectionFocus)
        assertEquals("del ancla al principio del compás siguiente", slots + 1, state.noteSelection?.size)
    }

    @Test
    fun selectAllNotes_takesTheWholeDocument() {
        // **Ctrl+A** (manual: «Select all»).
        openNew()
        writeTwoNotes()
        viewModel.processIntent(TablatureUiIntent.SelectAllNotes)
        assertEquals(2, viewModel.uiState.value.noteSelection?.size)
    }

    @Test
    fun setNoteSelection_aplicaElRectanguloQueResolvioElLienzo() {
        // **Rubber band**: el lienzo resuelve el rectángulo contra su geometría y emite la selección
        // ya hecha; el ViewModel solo la aplica (no conoce el layout).
        openNew()
        writeTwoNotes()
        val refs = setOf(TabNoteRef(0, 0, 1), TabNoteRef(0, 1, 1))
        viewModel.processIntent(TablatureUiIntent.SetNoteSelection(refs))
        assertEquals(2, viewModel.uiState.value.noteSelection?.size)
        assertEquals(refs, viewModel.uiState.value.noteSelection?.notes)
    }

    @Test
    fun setNoteSelectionConjuntoVacio_limpiaLaSeleccion() {
        // Un rectángulo que no toca ninguna cifra deja la selección vacía, no una selección de cero
        // notas: el estado usa `null` para «no hay selección».
        openNew()
        writeTwoNotes()
        viewModel.processIntent(TablatureUiIntent.SelectAllNotes)
        assertNotNull(viewModel.uiState.value.noteSelection)
        viewModel.processIntent(TablatureUiIntent.SetNoteSelection(emptySet()))
        assertNull("un rectángulo vacío limpia la selección", viewModel.uiState.value.noteSelection)
    }

    @Test
    fun deleteSelectedNotes_keepsTheColumns() {
        // Con notas seleccionadas, `Supr` borra solo lo seleccionado.
        openNew()
        writeTwoNotes()
        viewModel.processIntent(TablatureUiIntent.SelectNote(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.DeleteSelectedNotes)
        val slots = viewModel.uiState.value.editorMeasures[0].slots
        assertTrue("la columna 0 sigue ahí vacía", slots[0].strings.isEmpty())
        assertTrue("la nota de la columna 1 no se toca", slots.isNotEmpty())
        assertNull("la selección se limpia", viewModel.uiState.value.noteSelection)
    }

    @Test
    fun copyAndPasteSelectedNotes_reproduceTheNotes() {
        openNew()
        writeTwoNotes()
        viewModel.processIntent(TablatureUiIntent.SelectAllNotes)
        viewModel.processIntent(TablatureUiIntent.CopySelectedNotes)
        assertTrue(viewModel.uiState.value.hasNoteClipboard)
        // Pegar en el compás 1 (que está vacío).
        viewModel.processIntent(TablatureUiIntent.SelectSlot(1, 0))
        viewModel.processIntent(TablatureUiIntent.PasteNotes)
        assertTrue(
            "las notas pegadas llegan al compás 1",
            viewModel.uiState.value.editorMeasures[1].slots.any { it.strings.isNotEmpty() }
        )
    }

    @Test
    fun cutSelectedNotes_clearsTheSelection() {
        openNew()
        writeTwoNotes()
        viewModel.processIntent(TablatureUiIntent.SelectAllNotes)
        viewModel.processIntent(TablatureUiIntent.CutSelectedNotes)
        assertTrue(viewModel.uiState.value.hasNoteClipboard)
        assertTrue(
            "cortar borra las notas seleccionadas",
            viewModel.uiState.value.editorMeasures[0].slots.all { it.strings.isEmpty() }
        )
    }

    // ── Técnicas y marcas (paleta, un botón por símbolo) ───────────────────────

    @Test
    fun settingATechnique_marksTheActiveString() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(
            TablatureUiIntent.SetTechnique(0, 0, 1, TabTechnique.BEND)
        )
        val pluck = viewModel.uiState.value.editorMeasures[0].slots[0].strings.first { it.stringNumber == 1 }
        assertEquals(TabTechnique.BEND, pluck.technique)
        // Quitar la técnica no toca el traste.
        viewModel.processIntent(TablatureUiIntent.SetTechnique(0, 0, 1, TabTechnique.NONE))
        val cleared = viewModel.uiState.value.editorMeasures[0].slots[0].strings.first { it.stringNumber == 1 }
        assertEquals(TabTechnique.NONE, cleared.technique)
        assertEquals(3, cleared.fret)
    }

    @Test
    fun setTremoloStrokes_cambiaLosTrazosYSeDeshace() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.SetTechnique(0, 0, 1, TabTechnique.TREMOLO))
        viewModel.processIntent(TablatureUiIntent.SetTremoloStrokes(0, 0, 1, 4))

        val pluck = viewModel.uiState.value.editorMeasures[0].slots[0].strings.first { it.stringNumber == 1 }
        assertEquals(4, pluck.strokes)
        assertTrue("el trémolo sigue encendido", pluck.has(TabTechnique.TREMOLO))

        viewModel.processIntent(TablatureUiIntent.Undo)
        val undone = viewModel.uiState.value.editorMeasures[0].slots[0].strings.first { it.stringNumber == 1 }
        assertEquals(1, undone.strokes)
    }

    @Test
    fun setTremoloStrokes_acotaElValorPedido() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.SetTremoloStrokes(0, 0, 1, 99))

        val pluck = viewModel.uiState.value.editorMeasures[0].slots[0].strings.first { it.stringNumber == 1 }
        assertEquals(4, pluck.strokes)
    }

    @Test
    fun toggleTie_ligaLaNotaConLaSiguienteDeLaMismaCuerda() {
        openNew()
        // Dos notas iguales seguidas en la cuerda 1: la segunda columna se escribe a mano.
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 1, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.ToggleTie(0, 0, 1))
        assertTrue(
            viewModel.uiState.value.editorMeasures[0].slots[0].strings
                .first { it.stringNumber == 1 }.tiedToNext
        )
        // El mismo gesto la quita.
        viewModel.processIntent(TablatureUiIntent.ToggleTie(0, 0, 1))
        assertFalse(
            viewModel.uiState.value.editorMeasures[0].slots[0].strings
                .first { it.stringNumber == 1 }.tiedToNext
        )
    }

    @Test
    fun toggleTie_noLigaAlturasDistintas() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 1, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(5))
        viewModel.processIntent(TablatureUiIntent.ToggleTie(0, 0, 1))
        // (D-90) MuseScore **crea** el destino cuando la nota siguiente tiene otra altura
        // (`EditTie::cmdAddTie`), así que la ligadura **sí** se pone: va a una columna nueva con el
        // mismo traste. Antes este test fijaba el rechazo, que es lo que hacía que el botón «no
        // hiciera nada».
        val measure = viewModel.uiState.value.editorMeasures[0]
        assertTrue(
            "la ligadura se crea con la misma altura",
            measure.slots[0].strings.first { it.stringNumber == 1 }.tiedToNext
        )
        val destino = measure.slots.drop(1).firstNotNullOfOrNull { slot ->
            slot.strings.firstOrNull { it.stringNumber == 1 }
        }
        assertEquals("el destino lleva el mismo traste", 3, destino?.position?.fret)
    }

    @Test
    fun toggleSlur_ligaLaCasillaConLaSiguienteConSonido() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 1, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(5))
        viewModel.processIntent(TablatureUiIntent.ToggleSlur(0, 0))
        assertTrue(viewModel.uiState.value.editorMeasures[0].slots[0].slurToNext)
        // El mismo gesto la quita.
        viewModel.processIntent(TablatureUiIntent.ToggleSlur(0, 0))
        assertFalse(viewModel.uiState.value.editorMeasures[0].slots[0].slurToNext)
    }

    @Test
    fun toggleSlur_esDeshacible() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 1, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(5))
        val before = viewModel.uiState.value.editorMeasures
        viewModel.processIntent(TablatureUiIntent.ToggleSlur(0, 0))
        assertTrue(viewModel.uiState.value.editorMeasures[0].slots[0].slurToNext)

        viewModel.processIntent(TablatureUiIntent.Undo)
        assertEquals(before, viewModel.uiState.value.editorMeasures)
    }

    @Test
    fun settingMeasureMarks_usesConcreteValues() {
        openNew()
        viewModel.processIntent(
            TablatureUiIntent.SetRepeatMark(0, com.grafitto.laudate.domain.model.tablature.TabRepeatMark.BOTH)
        )
        assertEquals(
            com.grafitto.laudate.domain.model.tablature.TabRepeatMark.BOTH,
            viewModel.uiState.value.editorMeasures[0].repeat
        )
        viewModel.processIntent(TablatureUiIntent.SetEnding(0, 2))
        assertEquals(2, viewModel.uiState.value.editorMeasures[0].ending)
    }

    @Test
    fun settingTheEndingRange_writesTheMultiPassVolta() {
        // (D-37) La casilla `1, 2.` de MuseScore: un solo corchete que suena en las dos vueltas.
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetEndingRange(0, from = 1, to = 2))
        val measure = viewModel.uiState.value.editorMeasures[0]
        assertEquals(1, measure.ending)
        assertEquals(2, measure.endingTo)
        assertEquals("1, 2.", measure.endingLabel)
    }

    @Test
    fun settingTheEndingRange_isUndoable() {
        openNew()
        val before = viewModel.uiState.value.editorMeasures
        viewModel.processIntent(TablatureUiIntent.SetEndingRange(0, from = 1, to = 2))
        viewModel.processIntent(TablatureUiIntent.Undo)
        assertEquals(before, viewModel.uiState.value.editorMeasures)
    }

    @Test
    fun settingTheVoltaText_writesTheCustomLabel() {
        // (D-43) El rótulo del corchete es texto libre (el `beginText` del `Volta` de MuseScore) y no
        // toca las pasadas, que siguen mandando en la reproducción.
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetEnding(0, 1))
        viewModel.processIntent(TablatureUiIntent.SetEndingText(0, "Todos"))
        val measure = viewModel.uiState.value.editorMeasures[0]
        assertEquals("Todos", measure.endingText)
        assertEquals("Todos", measure.endingLabel)
        assertEquals(listOf(1), measure.endings)
    }

    @Test
    fun settingTheVoltaText_isUndoable() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetEnding(0, 1))
        val before = viewModel.uiState.value.editorMeasures
        viewModel.processIntent(TablatureUiIntent.SetEndingText(0, "Todos"))
        viewModel.processIntent(TablatureUiIntent.Undo)
        assertEquals(before, viewModel.uiState.value.editorMeasures)
    }

    @Test
    fun clearingTheVoltaText_restoresTheDerivedLabel() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetEnding(0, 1))
        viewModel.processIntent(TablatureUiIntent.SetEndingText(0, "Todos"))
        viewModel.processIntent(TablatureUiIntent.SetEndingText(0, null))
        val measure = viewModel.uiState.value.editorMeasures[0]
        assertNull(measure.endingText)
        assertEquals("1.", measure.endingLabel)
    }

    @Test
    fun togglingTechniques_combinesThemOnTheSameNote() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(5))
        // Escribir **avanza** el cursor: se vuelve a la casilla escrita para marcarle las técnicas.
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.ToggleTechnique(TabTechnique.HAMMER))
        viewModel.processIntent(TablatureUiIntent.ToggleTechnique(TabTechnique.SLIDE))
        val pluck = viewModel.uiState.value.editorMeasures[0].slots[0].strings.first { it.stringNumber == 1 }
        assertEquals(setOf(TabTechnique.HAMMER, TabTechnique.SLIDE), pluck.techniques)
        // Volver a pulsar quita solo ese marcado.
        viewModel.processIntent(TablatureUiIntent.ToggleTechnique(TabTechnique.SLIDE))
        val after = viewModel.uiState.value.editorMeasures[0].slots[0].strings.first { it.stringNumber == 1 }
        assertEquals(setOf(TabTechnique.HAMMER), after.techniques)
    }

    @Test
    fun settingBendAmount_and_palmMuteSpan_reachTheNote() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(7))
        // Escribir **avanza** el cursor: se vuelve a la casilla escrita para marcarle el bend.
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.ToggleTechnique(TabTechnique.BEND))
        viewModel.processIntent(TablatureUiIntent.SetBendAmount(0, 0, 1, TabBendAmount.HALF))
        viewModel.processIntent(TablatureUiIntent.SetPalmMuteSpan(0, 0, 1, 3))
        val pluck = viewModel.uiState.value.editorMeasures[0].slots[0].strings.first { it.stringNumber == 1 }
        assertEquals(TabBendAmount.HALF, pluck.bendAmount)
        assertEquals(3, pluck.palmMuteSpan)
        // Quitar la cifra del bend no borra la nota ni el marcado.
        viewModel.processIntent(TablatureUiIntent.SetBendAmount(0, 0, 1, null))
        val cleared = viewModel.uiState.value.editorMeasures[0].slots[0].strings.first { it.stringNumber == 1 }
        assertEquals(null, cleared.bendAmount)
        assertEquals(7, cleared.fret)
        assertTrue(cleared.has(TabTechnique.BEND))
    }

    @Test
    fun settingRhythmAndRepeatCount_updatesTheSelectedMusicalData() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        // Escribir **avanza** el cursor: se vuelve a la casilla escrita para darle figura y tresillo.
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(
            TablatureUiIntent.SetRhythmicFigureAtCursor(RhythmFigure.DOTTED_EIGHTH)
        )
        viewModel.processIntent(TablatureUiIntent.ToggleTripletAtCursor)
        viewModel.processIntent(
            TablatureUiIntent.SetRepeatMark(
                0,
                com.grafitto.laudate.domain.model.tablature.TabRepeatMark.END
            )
        )
        viewModel.processIntent(TablatureUiIntent.SetRepeatCount(0, 3))

        val measure = viewModel.uiState.value.editorMeasures.first()
        // El tresillo es un **grupo** de tres columnas: la primera lleva la figura elegida.
        assertEquals(RhythmFigure.DOTTED_EIGHTH, measure.slots[0].rhythmicFigure)
        assertTrue(measure.slots[0].isTriplet)
        assertEquals(3, measure.repeatCount)
    }

    @Test
    fun setTupletGroupAtCursor_marcaUnQuintilloDeCincoColumnas() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.SetTupletGroupAtCursor(5, 4))

        val measure = viewModel.uiState.value.editorMeasures.first()
        // Un grupo irregular es un **grupo**: se marcan las cinco columnas, creando las que falten.
        assertEquals(5, measure.slots.count { it.tuplet?.numNotes == 5 })
        assertEquals(4, measure.slots.first { it.tuplet != null }.tuplet?.normalNotes)
        // Y no es un tresillo: el rótulo del corchete será «5», no «3».
        assertFalse(measure.slots[0].isTriplet)
    }

    @Test
    fun setTupletGroupAtCursor_volverAPulsarDeshaceElGrupo() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.SetTupletGroupAtCursor(7, 4))
        viewModel.processIntent(TablatureUiIntent.SetTupletGroupAtCursor(7, 4))

        val measure = viewModel.uiState.value.editorMeasures.first()
        assertTrue(measure.slots.none { it.tuplet != null })
    }

    @Test
    fun setTupletNumberTypeAtCursor_cambiaElRotuloDeTodoElGrupo() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.SetTupletGroupAtCursor(3, 2))
        viewModel.processIntent(TablatureUiIntent.SetTupletNumberTypeAtCursor(TabTupletNumberType.SHOW_RELATION))

        val measure = viewModel.uiState.value.editorMeasures.first()
        val group = measure.slots.filter { it.tuplet != null }
        assertTrue(group.isNotEmpty())
        assertTrue(group.all { it.tuplet?.numberType == TabTupletNumberType.SHOW_RELATION })
        assertEquals("3:2", group.first().tuplet?.label)
    }

    @Test
    fun setTupletBracketTypeAtCursor_quitaElCorcheteDelGrupo() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.SetTupletGroupAtCursor(3, 2))
        viewModel.processIntent(TablatureUiIntent.SetTupletBracketTypeAtCursor(TabTupletBracketType.SHOW_NO_BRACKET))

        val measure = viewModel.uiState.value.editorMeasures.first()
        val group = measure.slots.filter { it.tuplet != null }
        assertTrue(group.all { it.tuplet?.bracketType == TabTupletBracketType.SHOW_NO_BRACKET })
    }

    // ── E1-E3: selección de rango, deshacer/rehacer y portapapeles ─────────────────

    @Test
    fun selectMeasureRange_marcaLaSeleccion() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.SelectMeasureRange(0, 1))
        assertEquals(0..1, viewModel.uiState.value.selection?.range)
        assertEquals(2, viewModel.uiState.value.selection?.size)
    }

    @Test
    fun deleteSelection_borraLosCompasesSeleccionadosYSeleccion() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.SelectMeasureRange(0, 1))
        viewModel.processIntent(TablatureUiIntent.DeleteSelection)
        val state = viewModel.uiState.value
        assertEquals(2, state.editorMeasureCount)
        assertNull(state.selection)
    }

    @Test
    fun undoAndRedo_restauranLosCompases() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        assertEquals(4, viewModel.uiState.value.editorMeasureCount)
        assertTrue(viewModel.uiState.value.canUndo)

        viewModel.processIntent(TablatureUiIntent.Undo)
        assertEquals(3, viewModel.uiState.value.editorMeasureCount)
        assertTrue(viewModel.uiState.value.canRedo)

        viewModel.processIntent(TablatureUiIntent.Redo)
        assertEquals(4, viewModel.uiState.value.editorMeasureCount)
    }

    // ── Metrónomo y bucle de rango (D-23) ───────────────────────────────────────────────────────

    @Test
    fun toggleMetronome_enciendeYApaga() {
        openNew()
        assertFalse("el metrónomo nace apagado", viewModel.uiState.value.metronomeEnabled)
        viewModel.processIntent(TablatureUiIntent.ToggleMetronome)
        assertTrue(viewModel.uiState.value.metronomeEnabled)
        viewModel.processIntent(TablatureUiIntent.ToggleMetronome)
        assertFalse(viewModel.uiState.value.metronomeEnabled)
    }

    @Test
    fun toggleLoopRange_sinSeleccionAvisaYNoActivaElBucle() = runTest {
        openNew()
        val messages = mutableListOf<String>()
        val collector = launch {
            viewModel.sideEffect.collect { effect ->
                if (effect is TablatureSideEffect.ShowMessage) messages += effect.message
            }
        }
        viewModel.processIntent(TablatureUiIntent.ToggleLoopRange)
        advanceUntilIdle()
        assertNull("sin selección no hay bucle", viewModel.uiState.value.loopRange)
        assertTrue(
            "pide seleccionar los compases",
            messages.any { it.contains("Selecciona") }
        )
        collector.cancel()
    }

    @Test
    fun toggleLoopRange_tomaLaSeleccionYSeQuitaAlSegundoToque() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectMeasureRange(0, 1))
        viewModel.processIntent(TablatureUiIntent.ToggleLoopRange)
        assertEquals("el bucle es la selección", 0..1, viewModel.uiState.value.loopRange)
        viewModel.processIntent(TablatureUiIntent.ToggleLoopRange)
        assertNull("el segundo toque lo quita", viewModel.uiState.value.loopRange)
    }

    @Test
    fun conBucleLaReproduccionNoTerminaSola() = runTest {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        viewModel.processIntent(TablatureUiIntent.SelectMeasureAt(0))
        viewModel.processIntent(TablatureUiIntent.ToggleLoopRange)
        assertEquals(0..0, viewModel.uiState.value.loopRange)

        viewModel.processIntent(TablatureUiIntent.PlaybackToggle)
        assertTrue(viewModel.uiState.value.isPlaying)
        // Con bucle la línea de tiempo se recorre una y otra vez: sigue sonando.
        advanceTimeBy(5_000)
        assertTrue("el bucle mantiene la reproducción viva", viewModel.uiState.value.isPlaying)

        viewModel.processIntent(TablatureUiIntent.PlaybackToggle)
        assertFalse(viewModel.uiState.value.isPlaying)
    }

    // ── Reguladores (hairpins) ───────────────────────────────────────────────────────────────────

    @Test
    fun setHairpin_fijaLaCunaEnElCompasYSeDeshace() = runTest {
        openNew()
        viewModel.processIntent(
            TablatureUiIntent.SetHairpin(
                0,
                com.grafitto.laudate.domain.model.tablature.TabHairpin(
                    com.grafitto.laudate.domain.model.tablature.TabHairpinType.CRESCENDO,
                    2
                )
            )
        )
        val conCuna = viewModel.uiState.value.editorMeasures[0].hairpin
        assertEquals(
            com.grafitto.laudate.domain.model.tablature.TabHairpinType.CRESCENDO,
            conCuna?.type
        )
        assertEquals(2, conCuna?.measures)
        assertTrue("el cambio entra en el historial", viewModel.uiState.value.canUndo)

        viewModel.processIntent(TablatureUiIntent.Undo)
        assertNull("Ctrl+Z quita la cuña", viewModel.uiState.value.editorMeasures[0].hairpin)
    }

    /**
     * El **compás** forma parte del documento guardado, así que cambiarlo tiene que poder deshacerse.
     * Antes se aplicaba sin apilar nada y `Ctrl+Z` no lo devolvía.
     */
    @Test
    fun setTimeSignature_esDeshacible() {
        openNew()
        val timeBefore = viewModel.uiState.value.editorTime
        assertFalse(viewModel.uiState.value.canUndo)

        viewModel.processIntent(TablatureUiIntent.SetTimeSignature(ScoreTimeSignature("3", 4)))
        assertEquals(ScoreTimeSignature("3", 4), viewModel.uiState.value.editorTime)
        assertTrue(viewModel.uiState.value.canUndo)

        viewModel.processIntent(TablatureUiIntent.Undo)
        assertEquals(timeBefore, viewModel.uiState.value.editorTime)
    }

    /** La **resolución** de la rejilla también forma parte del documento: mismo contrato. */
    @Test
    fun setResolution_esDeshacible() {
        openNew()
        val resolutionBefore = viewModel.uiState.value.editorResolution
        assertFalse(viewModel.uiState.value.canUndo)

        viewModel.processIntent(TablatureUiIntent.SetResolution(TabResolution.SIXTEENTH))
        assertEquals(TabResolution.SIXTEENTH, viewModel.uiState.value.editorResolution)
        assertTrue(viewModel.uiState.value.canUndo)

        viewModel.processIntent(TablatureUiIntent.Undo)
        assertEquals(resolutionBefore, viewModel.uiState.value.editorResolution)
    }

    /**
     * Cambiar de compás **no** recorta las columnas ya escritas: con el modelo de columnas son libres
     * y recortarlas borraría notas sin avisar.
     */
    @Test
    fun setTimeSignature_noRecortaLasColumnasEscritas() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        val slotsBefore = viewModel.uiState.value.editorMeasures[0].slots.size

        viewModel.processIntent(TablatureUiIntent.SetTimeSignature(ScoreTimeSignature("3", 4)))
        assertEquals(slotsBefore, viewModel.uiState.value.editorMeasures[0].slots.size)
        assertEquals(3, viewModel.uiState.value.editorMeasures[0].slots[0].strings.first().position.fret)
    }

    /**
     * **Redigitar al tono del canto** reescribe todas las cifras: es la operación más destructiva del
     * editor y también tiene que poder deshacerse.
     */
    @Test
    fun refingerToSongKey_esDeshacible() {
        viewModel.processIntent(TablatureUiIntent.LoadForSong("song-1", "Salmo 2", "G"))
        openNew()
        // La tablatura se escribe en Do pero el canto está en Sol: redigitar la transporta 7 semitonos.
        viewModel.processIntent(
            TablatureUiIntent.SetEditorWrittenKey(
                com.grafitto.laudate.domain.model.score.scoreKeyOfTonic(0)
            )
        )
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        val before = viewModel.uiState.value.editorMeasures
        assertTrue(viewModel.uiState.value.canRefingerToSongKey)

        viewModel.processIntent(TablatureUiIntent.RefingerToSongKey)
        val after = viewModel.uiState.value.editorMeasures
        assertTrue("redigitar tiene que cambiar las cifras", after != before)
        assertTrue(viewModel.uiState.value.canUndo)

        viewModel.processIntent(TablatureUiIntent.Undo)
        assertEquals(before, viewModel.uiState.value.editorMeasures)
    }

    @Test
    fun undo_vuelveHastaElEstadoInicial() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(0))
        // Se deshace TODO hasta que la pila se agota: el editor vuelve a su estado inicial.
        while (viewModel.uiState.value.canUndo) {
            viewModel.processIntent(TablatureUiIntent.Undo)
        }
        // El estado inicial son los **dos compases con rejilla** con los que nace el editor.
        assertEquals(2, viewModel.uiState.value.editorMeasureCount)
        val expected = TablatureEditing.slotsPerMeasure(
            viewModel.uiState.value.editorTime,
            viewModel.uiState.value.editorResolution
        )
        assertEquals(expected, viewModel.uiState.value.editorMeasures[0].slots.size)
        assertEquals(
            RhythmFigure.EIGHTH_REST,
            viewModel.uiState.value.editorMeasures[0].slots[0].rhythmicFigure
        )
    }

    @Test
    fun copyAndPaste_duplicaElBloqueSeleccionado() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.SelectMeasureRange(0, 0))
        viewModel.processIntent(TablatureUiIntent.CopySelection)
        assertTrue(viewModel.uiState.value.hasClipboard)
        viewModel.processIntent(TablatureUiIntent.PasteClipboard)
        assertEquals(4, viewModel.uiState.value.editorMeasureCount)
    }

    @Test
    fun cutSelection_eliminaYPegaEnOtroSitio() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.SelectMeasureRange(1, 2))
        viewModel.processIntent(TablatureUiIntent.CutSelection)
        // Se queda solo el primer compás y el portapapeles tiene 2.
        assertEquals(2, viewModel.uiState.value.editorMeasureCount)
        assertTrue(viewModel.uiState.value.hasClipboard)
        // Al pegar, vuelven a estar los 4.
        viewModel.processIntent(TablatureUiIntent.PasteClipboard)
        assertEquals(4, viewModel.uiState.value.editorMeasureCount)
    }

    @Test
    fun duplicateSelection_clonaElBloqueDetras() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.SelectMeasureRange(0, 1))
        viewModel.processIntent(TablatureUiIntent.DuplicateSelection)
        assertEquals(5, viewModel.uiState.value.editorMeasureCount)
    }

    // ── Fase 3 de paridad: compases, voces, sistemas y zoom ─────────────────────────

    @Test
    fun clearMeasures_borraElContenidoYConservaElCompas() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(5))
        viewModel.processIntent(TablatureUiIntent.SetTempo(0, 96))
        val before = viewModel.uiState.value.editorMeasureCount
        viewModel.processIntent(TablatureUiIntent.ClearMeasures(0..0))
        val state = viewModel.uiState.value
        assertEquals("el compás sigue ahí", before, state.editorMeasureCount)
        assertTrue(
            "no queda ninguna nota",
            state.editorMeasures[0].slots.all { it.strings.isEmpty() }
        )
        assertEquals(
            "las marcas del compás se conservan",
            96,
            state.editorMeasures[0].tempo
        )
    }

    @Test
    fun deleteMeasures_quitaElRangoYDejaElCursorDentro() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        assertEquals(4, viewModel.uiState.value.editorMeasureCount)
        viewModel.processIntent(TablatureUiIntent.DeleteMeasures(1..2))
        val state = viewModel.uiState.value
        assertEquals(2, state.editorMeasureCount)
        assertNull("la selección se limpia", state.selection)
        assertTrue("el cursor cae dentro", state.selectedSlot!!.first in state.editorMeasures.indices)
    }

    @Test
    fun deleteMeasures_nuncaDejaLaTablaturaVacia() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.DeleteMeasures(0..1))
        assertEquals(1, viewModel.uiState.value.editorMeasureCount)
    }

    @Test
    fun moveMeasures_reordenaElBloque() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        // Marca el compás 0 para poder seguirlo tras el movimiento.
        viewModel.processIntent(TablatureUiIntent.SetTempo(0, 120))
        // `to` es el índice en la lista **ya sin el bloque**: 3 = al final.
        viewModel.processIntent(TablatureUiIntent.MoveMeasures(0..0, 3))
        val state = viewModel.uiState.value
        assertEquals(4, state.editorMeasureCount)
        assertEquals("el compás marcado acabó al final", 120, state.editorMeasures.last().tempo)
    }

    @Test
    fun toggleSystemBreak_alternaElSaltoDeSistema() {
        openNew()
        assertFalse(viewModel.uiState.value.editorMeasures[0].forceSystemBreak)
        viewModel.processIntent(TablatureUiIntent.ToggleSystemBreak(0))
        assertTrue(viewModel.uiState.value.editorMeasures[0].forceSystemBreak)
        viewModel.processIntent(TablatureUiIntent.ToggleSystemBreak(0))
        assertFalse(viewModel.uiState.value.editorMeasures[0].forceSystemBreak)
    }

    @Test
    fun setSystemBreak_fijaElValorSinAlternar() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetSystemBreak(1, true))
        assertTrue(viewModel.uiState.value.editorMeasures[1].forceSystemBreak)
        viewModel.processIntent(TablatureUiIntent.SetSystemBreak(1, true))
        assertTrue("repetir el mismo valor no lo apaga", viewModel.uiState.value.editorMeasures[1].forceSystemBreak)
    }

    @Test
    fun togglePageBreak_marcaPaginaYSistemaYSeDeshace() {
        openNew()
        assertFalse(viewModel.uiState.value.editorMeasures[0].forcePageBreak)

        viewModel.processIntent(TablatureUiIntent.TogglePageBreak(0))
        val marcado = viewModel.uiState.value.editorMeasures[0]
        assertTrue("el compas abre hoja", marcado.forcePageBreak)
        assertTrue("y tambien cierra la linea", marcado.forceSystemBreak)

        viewModel.processIntent(TablatureUiIntent.TogglePageBreak(0))
        val limpio = viewModel.uiState.value.editorMeasures[0]
        assertFalse(limpio.forcePageBreak)
        assertFalse("no queda un salto de sistema huerfano", limpio.forceSystemBreak)
    }

    @Test
    fun setPageBreak_fijaElValorSinAlternar() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetPageBreak(1, true))
        assertTrue(viewModel.uiState.value.editorMeasures[1].forcePageBreak)
        viewModel.processIntent(TablatureUiIntent.SetPageBreak(1, true))
        assertTrue(
            "repetir el mismo valor no lo apaga",
            viewModel.uiState.value.editorMeasures[1].forcePageBreak
        )
    }

    @Test
    fun selectMeasureAt_seleccionaElCompasYDejaElCursorEnSuPrimeraCasilla() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.SelectMeasureAt(2))
        val state = viewModel.uiState.value
        assertEquals(2..2, state.selection?.range)
        assertEquals(2 to 0, state.selectedSlot)
    }

    @Test
    fun selectMeasureAt_ignoraUnIndiceFueraDeRango() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectMeasureAt(99))
        assertNull(viewModel.uiState.value.selection)
    }

    @Test
    fun extendMeasureSelection_creceDesdeElAncla() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.SelectMeasureAt(0))
        viewModel.processIntent(TablatureUiIntent.ExtendMeasureSelection(2))
        assertEquals(0..2, viewModel.uiState.value.selection?.range)
        viewModel.processIntent(TablatureUiIntent.ExtendMeasureSelection(-1))
        assertEquals(0..1, viewModel.uiState.value.selection?.range)
    }

    @Test
    fun extendMeasureSelection_sinSeleccionPreviaSeleccionaElCompasDelCursor() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(1, 0))
        viewModel.processIntent(TablatureUiIntent.ExtendMeasureSelection(1))
        assertEquals(1..1, viewModel.uiState.value.selection?.range)
    }

    @Test
    fun zoomInOutReset_respetanLosLimites() {
        openNew()
        val base = viewModel.uiState.value.tablatureScale
        viewModel.processIntent(TablatureUiIntent.ZoomIn)
        assertTrue("acerca", viewModel.uiState.value.tablatureScale > base)
        viewModel.processIntent(TablatureUiIntent.ZoomReset)
        assertEquals(base, viewModel.uiState.value.tablatureScale, 0.0001f)
        repeat(20) { viewModel.processIntent(TablatureUiIntent.ZoomOut) }
        // (D-60) El rango vive en `TabNotationScale`: el 70 % de antes es el nuevo 100 %, así que el
        // mínimo baja a 35 % para que la tablatura quepa de verdad en un móvil.
        assertEquals(
            "no baja del mínimo",
            TabNotationScale.MIN_ZOOM,
            viewModel.uiState.value.tablatureScale,
            0.0001f
        )
        repeat(40) { viewModel.processIntent(TablatureUiIntent.ZoomIn) }
        assertEquals(
            "no pasa del máximo",
            TabNotationScale.MAX_ZOOM,
            viewModel.uiState.value.tablatureScale,
            0.0001f
        )
    }

    @Test
    fun togglePalettes_alternaElPanelLateral() {
        openNew()
        assertTrue("las paletas se ven al abrir", viewModel.uiState.value.showPalettes)
        viewModel.processIntent(TablatureUiIntent.TogglePalettes)
        assertFalse("F9 las oculta", viewModel.uiState.value.showPalettes)
        viewModel.processIntent(TablatureUiIntent.TogglePalettes)
        assertTrue("F9 las vuelve a mostrar", viewModel.uiState.value.showPalettes)
    }

    @Test
    fun toggleProperties_alternaElPanelDePropiedades() {
        openNew()
        assertTrue("las propiedades se ven al abrir", viewModel.uiState.value.showProperties)
        viewModel.processIntent(TablatureUiIntent.ToggleProperties)
        assertFalse("F8 las oculta", viewModel.uiState.value.showProperties)
        viewModel.processIntent(TablatureUiIntent.ToggleProperties)
        assertTrue("F8 las vuelve a mostrar", viewModel.uiState.value.showProperties)
    }

    @Test
    fun setSlotVoice_guardaLaVozEnLaCasilla() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetSlotVoice(0, 0, 2))
        assertEquals(2, viewModel.uiState.value.editorMeasures[0].slots[0].voice)
    }

    @Test
    fun setVoiceInRange_aplicaLaVozYLaDejaActiva() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectMeasureRange(0, 1))
        viewModel.processIntent(TablatureUiIntent.SetVoiceInRange(3))
        val state = viewModel.uiState.value
        assertEquals(3, state.activeVoice)
        assertTrue(
            "todas las casillas del rango son de la voz 3",
            state.editorMeasures.take(2).all { m -> m.slots.all { it.voice == 3 } }
        )
    }

    @Test
    fun escribirUnaNota_lePoneLaVozActiva() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetActiveVoice(2))
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(3))
        assertEquals(2, viewModel.uiState.value.editorMeasures[0].slots[0].voice)
    }

    @Test
    fun statusBarText_describeElCursorConElVocabularioDeMuseScore() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(0))
        val text = viewModel.uiState.value.statusBarText
        assertTrue("empieza por el tipo de elemento: $text", text.startsWith("Nota;"))
        assertTrue("lleva la altura: $text", text.contains("Altura:"))
        assertTrue("lleva la cuerda: $text", text.contains("Cuerda:"))
        assertTrue("lleva el traste: $text", text.contains("Traste: 0"))
        assertTrue("lleva la duración: $text", text.contains("Duración:"))
        assertTrue("lleva la voz: $text", text.contains("Voz:"))
        assertTrue("lleva el compás: $text", text.contains("Compás: 1"))
        assertTrue("lleva el tiempo: $text", text.contains("Tiempo:"))
        assertTrue("lleva el pentagrama: $text", text.endsWith("Pentagrama 1"))
    }

    @Test
    fun activeFigure_enUnaCasillaVaciaEsLaFiguraElegida() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.SetRhythmicFigureAtCursor(RhythmFigure.EIGHTH))
        assertEquals(
            "en una casilla vacía manda la figura elegida",
            RhythmFigure.EIGHTH,
            viewModel.uiState.value.activeFigure
        )
    }

    @Test
    fun activeFigure_conUnaNotaBajoElCursorMandaLaDuracionDeLaNota() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        viewModel.processIntent(TablatureUiIntent.TypeFretDigit(3))
        viewModel.processIntent(TablatureUiIntent.SetRhythmicFigureAtCursor(RhythmFigure.HALF))
        // Se mueve el cursor a otra casilla y se elige otra figura: la nota escrita conserva la suya.
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 1))
        viewModel.processIntent(TablatureUiIntent.SetRhythmicFigureAtCursor(RhythmFigure.SIXTEENTH))
        viewModel.processIntent(TablatureUiIntent.SelectSlot(0, 0))
        assertEquals(
            "la barra describe la nota seleccionada, no la última figura elegida",
            RhythmFigure.HALF,
            viewModel.uiState.value.activeFigure
        )
    }

    @Test
    fun goToMeasure_mueveElCursorSinTocarLaSeleccion() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.SelectMeasureAt(0))
        viewModel.processIntent(TablatureUiIntent.GoToMeasure(2))
        val state = viewModel.uiState.value
        assertEquals(2 to 0, state.selectedSlot)
        assertEquals("la selección de compases no cambia", 0..0, state.selection?.range)
    }

    @Test
    fun goToMeasure_ignoraUnIndiceFueraDeRango() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectSlot(1, 0))
        viewModel.processIntent(TablatureUiIntent.GoToMeasure(99))
        assertEquals(1 to 0, viewModel.uiState.value.selectedSlot)
    }

    // ── Reproducción (B-9) ──────────────────────────────────────────────────────────────────────

    @Test
    fun playbackToggle_arrancaYTerminaSolo() = runTest {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))

        viewModel.processIntent(TablatureUiIntent.PlaybackToggle)
        // El estado se enciende de inmediato, sin esperar al primer ataque.
        assertTrue(viewModel.uiState.value.isPlaying)

        advanceUntilIdle()
        // Al agotarse la línea de tiempo la reproducción se apaga sola y el cursor se retira.
        assertFalse(viewModel.uiState.value.isPlaying)
        assertNull(viewModel.uiState.value.playbackSlot)
    }

    @Test
    fun playbackToggle_paraAlSegundoToque() = runTest {
        openNew()
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))

        viewModel.processIntent(TablatureUiIntent.PlaybackToggle)
        assertTrue(viewModel.uiState.value.isPlaying)

        viewModel.processIntent(TablatureUiIntent.PlaybackToggle)
        assertFalse(viewModel.uiState.value.isPlaying)
        assertNull(viewModel.uiState.value.playbackSlot)
    }

    @Test
    fun playbackToggle_sinNotasAvisaYNoArranca() = runTest {
        openNew()
        val messages = mutableListOf<String>()
        val collector = launch {
            viewModel.sideEffect.collect { effect ->
                if (effect is TablatureSideEffect.ShowMessage) messages += effect.message
            }
        }
        viewModel.processIntent(TablatureUiIntent.PlaybackToggle)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isPlaying)
        assertTrue(
            "avisa de que no hay nada que sonar",
            messages.any { it.contains("No hay notas") }
        )
        collector.cancel()
    }

    // ── Reguladores (hairpins) ───────────────────────────────────────────────────────────────────

    @Test
    fun setHairpin_escribeLaCunaYSeDeshace() {
        openNew()
        viewModel.processIntent(
            TablatureUiIntent.SetHairpin(
                0,
                com.grafitto.laudate.domain.model.tablature.TabHairpin(
                    com.grafitto.laudate.domain.model.tablature.TabHairpinType.CRESCENDO,
                    lengthMeasures = 2
                )
            )
        )
        val hairpin = viewModel.uiState.value.editorMeasures[0].hairpin
        assertEquals(
            com.grafitto.laudate.domain.model.tablature.TabHairpinType.CRESCENDO,
            hairpin?.type
        )
        assertEquals(2, hairpin?.measures)

        viewModel.processIntent(TablatureUiIntent.Undo)
        assertEquals(null, viewModel.uiState.value.editorMeasures[0].hairpin)
    }

    @Test
    fun setHairpinConNull_quitaElRegulador() {
        openNew()
        viewModel.processIntent(
            TablatureUiIntent.SetHairpin(
                0,
                com.grafitto.laudate.domain.model.tablature.TabHairpin(
                    com.grafitto.laudate.domain.model.tablature.TabHairpinType.DIMINUENDO
                )
            )
        )
        assertTrue(viewModel.uiState.value.editorMeasures[0].hairpin != null)
        viewModel.processIntent(TablatureUiIntent.SetHairpin(0, null))
        assertEquals(null, viewModel.uiState.value.editorMeasures[0].hairpin)
    }

    // ── Notas de adorno (*grace notes*) ─────────────────────────────────────────────────────────

    @Test
    fun addGrace_cuelgaUnaCifraPequenaDeLaCasillaDelCursor() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddGrace(0, 0))
        val graces = viewModel.uiState.value.editorMeasures[0].slots[0].graces
        assertEquals(1, graces.size)
        assertEquals(
            com.grafitto.laudate.domain.model.tablature.TabGraceType.ACCIACCATURA,
            graces.single().type
        )
    }

    @Test
    fun addGrace_conApoyaturaGuardaElTipo() {
        openNew()
        viewModel.processIntent(
            TablatureUiIntent.AddGrace(
                0,
                0,
                com.grafitto.laudate.domain.model.tablature.TabGraceType.APPOGGIATURA
            )
        )
        assertEquals(
            com.grafitto.laudate.domain.model.tablature.TabGraceType.APPOGGIATURA,
            viewModel.uiState.value.editorMeasures[0].slots[0].graces.single().type
        )
    }

    @Test
    fun removeGrace_quitaElUltimoYSeDeshaceConUndo() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddGrace(0, 0))
        viewModel.processIntent(TablatureUiIntent.AddGrace(0, 0))
        assertEquals(2, viewModel.uiState.value.editorMeasures[0].slots[0].graces.size)

        viewModel.processIntent(TablatureUiIntent.RemoveGrace(0, 0))
        assertEquals(1, viewModel.uiState.value.editorMeasures[0].slots[0].graces.size)

        viewModel.processIntent(TablatureUiIntent.Undo)
        assertEquals(2, viewModel.uiState.value.editorMeasures[0].slots[0].graces.size)
    }

    @Test
    fun clearGraces_losQuitaTodos() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddGrace(0, 0))
        viewModel.processIntent(TablatureUiIntent.AddGrace(0, 0))
        viewModel.processIntent(TablatureUiIntent.ClearGraces(0, 0))
        assertTrue(viewModel.uiState.value.editorMeasures[0].slots[0].graces.isEmpty())
    }

    // ── F-08 · avisos de acorde ────────────────────────────────────────────────────────────────

    @Test
    fun unaTablaturaTocableNoTieneAvisos() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 0, 1, 0))
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 0, 2, 1))
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 0, 3, 0))
        val state = viewModel.uiState.value
        assertFalse(state.hasChordIssues)
        assertEquals(0, state.chordIssueCount)
        assertNull(state.chordIssueSummary)
    }

    @Test
    fun unPalmoInalcanzableSeAvisaEnLaColumna() {
        openNew()
        // Del traste 1 al 9 en la misma columna: ocho trastes, la mano no llega.
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 0, 1, 1))
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 0, 2, 9))
        val state = viewModel.uiState.value
        assertTrue(state.hasChordIssues)
        assertEquals(1, state.chordIssueCount)
        assertTrue(state.chordIssues.containsKey(0 to 0))
        assertNotNull(state.chordIssueSummary)
        assertTrue(
            "el resumen dice cuántos trastes hay",
            state.chordIssueSummary!!.contains("8 trastes")
        )
    }

    @Test
    fun unaCuerdaApagadaConTrasteSeAvisa() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 0, 1, 5))
        viewModel.processIntent(
            TablatureUiIntent.SetTechnique(0, 0, 1, TabTechnique.MUTED)
        )
        val state = viewModel.uiState.value
        assertTrue(state.hasChordIssues)
        assertTrue(
            "el resumen habla de la cuerda apagada: ${state.chordIssueSummary}",
            state.chordIssueSummary!!.contains("apagada")
        )
    }

    @Test
    fun elAvisoDesapareceAlCorregirLaPosicion() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 0, 1, 1))
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 0, 2, 9))
        assertTrue(viewModel.uiState.value.hasChordIssues)

        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 0, 2, 4))
        assertFalse(viewModel.uiState.value.hasChordIssues)
        assertNull(viewModel.uiState.value.chordIssueSummary)
    }

    @Test
    fun elResumenCuentaColumnasYNoAvisos() {
        openNew()
        // Dos columnas imposibles: el resumen dice «2 acordes».
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 0, 1, 1))
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 0, 2, 9))
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 1, 1, 1))
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 1, 2, 9))
        val state = viewModel.uiState.value
        assertEquals(2, state.chordIssueCount)
        assertTrue(state.chordIssueSummary!!.startsWith("2 acordes"))
    }

    // ── Intercambio con el portapapeles y compases añadidos en bloque ──────────────

    @Test
    fun swapSelection_intercambiaLaSeleccionConElPortapapeles() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        // El compás 0 lleva un traste 5; se copia y se intercambia con el compás 2, que está vacío.
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 0, 1, 5))
        viewModel.processIntent(TablatureUiIntent.SelectMeasureRange(0, 0))
        viewModel.processIntent(TablatureUiIntent.CopySelection)
        viewModel.processIntent(TablatureUiIntent.SelectMeasureRange(2, 2))
        viewModel.processIntent(TablatureUiIntent.SwapSelection)
        val state = viewModel.uiState.value
        // El compás 2 recibe el traste 5. El compás 0 **conserva** el suyo: el «intercambiar con el
        // portapapeles» de MuseScore (`Ctrl+Mayús+X`, `edit-swap`) **pega** el portapapeles en la
        // selección y **captura** lo que había allí; no vacía el origen (para mover hay que cortar).
        assertEquals(5, state.editorMeasures[2].slots[0].strings.first().position.fret)
        assertEquals(5, state.editorMeasures[0].slots[0].strings.first().position.fret)
        assertTrue(state.hasClipboard)
    }

    @Test
    fun swapSelection_dosVecesDevuelveElDocumentoOriginal() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.AddMeasure)
        viewModel.processIntent(TablatureUiIntent.SetSlotFret(0, 0, 1, 5))
        viewModel.processIntent(TablatureUiIntent.SelectMeasureRange(0, 0))
        viewModel.processIntent(TablatureUiIntent.CopySelection)
        viewModel.processIntent(TablatureUiIntent.SelectMeasureRange(1, 1))
        viewModel.processIntent(TablatureUiIntent.SwapSelection)
        viewModel.processIntent(TablatureUiIntent.SwapSelection)
        val state = viewModel.uiState.value
        assertEquals(5, state.editorMeasures[0].slots[0].strings.first().position.fret)
        assertTrue(state.editorMeasures[1].slots[0].strings.isEmpty())
    }

    @Test
    fun swapSelection_sinPortapapelesNoHaceNada() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SelectMeasureRange(0, 0))
        val before = viewModel.uiState.value.editorMeasures
        viewModel.processIntent(TablatureUiIntent.SwapSelection)
        assertEquals(before, viewModel.uiState.value.editorMeasures)
    }

    @Test
    fun addMeasures_anadeVariosCompasesDeGolpe() {
        openNew()
        val before = viewModel.uiState.value.editorMeasureCount
        viewModel.processIntent(TablatureUiIntent.AddMeasures(4))
        assertEquals(before + 4, viewModel.uiState.value.editorMeasureCount)
        assertTrue(viewModel.uiState.value.canUndo)
    }

    @Test
    fun addMeasures_acotaLaCantidad() {
        openNew()
        val before = viewModel.uiState.value.editorMeasureCount
        // Un cero o un negativo añade **uno**: el gesto nunca deja de hacer nada.
        viewModel.processIntent(TablatureUiIntent.AddMeasures(0))
        assertEquals(before + 1, viewModel.uiState.value.editorMeasureCount)
    }

    // ── Mezclador de la reproducción (D-29) ───────────────────────────────────────

    @Test
    fun elMezcladorNaceAlMaximoYCentrado() {
        openNew()
        val mixer = viewModel.uiState.value.mixer
        assertEquals(1f, mixer.volume, 0.0001f)
        assertEquals(0f, mixer.pan, 0.0001f)
        assertFalse(mixer.muted)
        assertFalse(mixer.solo)
    }

    @Test
    fun setMixerVolume_fijaElVolumenYLoAcota() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetMixerVolume(0.4f))
        assertEquals(0.4f, viewModel.uiState.value.mixer.volume, 0.0001f)
        // Fuera de rango se acota: el motor nunca recibe una ganancia negativa ni mayor que uno.
        viewModel.processIntent(TablatureUiIntent.SetMixerVolume(5f))
        assertEquals(1f, viewModel.uiState.value.mixer.volume, 0.0001f)
        viewModel.processIntent(TablatureUiIntent.SetMixerVolume(-3f))
        assertEquals(0f, viewModel.uiState.value.mixer.volume, 0.0001f)
    }

    @Test
    fun setMixerPan_fijaElBalanceYLoAcota() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetMixerPan(-0.5f))
        assertEquals(-0.5f, viewModel.uiState.value.mixer.pan, 0.0001f)
        assertEquals("I50", viewModel.uiState.value.mixer.panLabel)
        viewModel.processIntent(TablatureUiIntent.SetMixerPan(9f))
        assertEquals(1f, viewModel.uiState.value.mixer.pan, 0.0001f)
    }

    @Test
    fun toggleMixerMute_callaYDevuelveElVolumen() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetMixerVolume(0.7f))
        viewModel.processIntent(TablatureUiIntent.ToggleMixerMute)
        val muted = viewModel.uiState.value.mixer
        assertTrue(muted.muted)
        assertEquals(0f, muted.gain, 0.0001f)
        // El volumen **no** se pierde: al quitar el silencio vuelve el que había.
        viewModel.processIntent(TablatureUiIntent.ToggleMixerMute)
        assertFalse(viewModel.uiState.value.mixer.muted)
        assertEquals(0.7f, viewModel.uiState.value.mixer.gain, 0.0001f)
    }

    @Test
    fun toggleMixerSolo_seEnciendeYSeApaga() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.ToggleMixerSolo)
        assertTrue(viewModel.uiState.value.mixer.solo)
        viewModel.processIntent(TablatureUiIntent.ToggleMixerSolo)
        assertFalse(viewModel.uiState.value.mixer.solo)
    }

    // ── D-48: los ajustes del pentagrama son una preferencia GLOBAL ──────────────────────────

    @Test
    fun losAjustesDelPentagramaSeGuardanAlCambiarlos() = runTest {
        val preferences = FakePreferencesRepository()
        val vm = TablatureViewModel(repository, preferences)
        val options = TabStaffOptions(fretMarks = FretMarkStyle.LETTERS, minimStyle = MinimStyle.SLASHED)

        vm.processIntent(TablatureUiIntent.SetStaffOptions(options))
        advanceUntilIdle()

        assertEquals(options, vm.uiState.value.editorStaffOptions)
        // Y quedan guardados: el visor y el PDF los leerán de la misma preferencia.
        assertEquals(options, preferences.savedStaffOptions)
    }

    @Test
    fun alAbrirElEditorSeRecuperanLosAjustesGuardados() = runTest {
        val preferences = FakePreferencesRepository()
        val saved = TabStaffOptions(showClef = false, showMeasureNumbers = false)
        preferences.setTablatureStaffOptions(saved)

        val vm = TablatureViewModel(repository, preferences)
        advanceUntilIdle()

        assertEquals(saved, vm.uiState.value.editorStaffOptions)
    }

    @Test
    fun cambiarLosAjustesNoMarcaElCantoComoModificado() = runTest {
        // Son preferencias de **aspecto**, no una edición del documento: no deben ensuciar el
        // historial ni pedir guardar al cerrar.
        val preferences = FakePreferencesRepository()
        val vm = TablatureViewModel(repository, preferences)
        vm.processIntent(TablatureUiIntent.OpenNewEditor)
        advanceUntilIdle()
        val before = vm.uiState.value

        vm.processIntent(TablatureUiIntent.SetStaffOptions(TabStaffOptions(showClef = false)))
        advanceUntilIdle()

        val after = vm.uiState.value
        assertEquals(before.editorMeasures, after.editorMeasures)
        assertEquals(before.canUndo, after.canUndo)
        assertEquals(before.hasUnsavedChanges, after.hasUnsavedChanges)
    }

    // ── D-49: los datos de cuerda (el «String data» de MuseScore) ────────────────────────────

    @Test
    fun elDialogoDeDatosDeCuerdaSeAbreYSeCierra() {
        openNew()
        assertFalse(viewModel.uiState.value.showStringDataDialog)
        viewModel.processIntent(TablatureUiIntent.OpenStringDataDialog)
        assertTrue(viewModel.uiState.value.showStringDataDialog)
        viewModel.processIntent(TablatureUiIntent.CloseStringDataDialog)
        assertFalse(viewModel.uiState.value.showStringDataDialog)
    }

    @Test
    fun elNumeroDeTrastesSeCambiaYSeAcota() {
        openNew()
        assertEquals(TabStringData.DEFAULT_FRETS, viewModel.uiState.value.editorTuning.frets)

        viewModel.processIntent(TablatureUiIntent.SetTuningFrets(24))
        assertEquals(24, viewModel.uiState.value.editorTuning.frets)

        // El diálogo de MuseScore no deja bajar de 0 ni subir de 99.
        viewModel.processIntent(TablatureUiIntent.SetTuningFrets(-3))
        assertEquals(TabStringData.MIN_FRETS, viewModel.uiState.value.editorTuning.frets)
        viewModel.processIntent(TablatureUiIntent.SetTuningFrets(500))
        assertEquals(TabStringData.MAX_FRETS, viewModel.uiState.value.editorTuning.frets)
    }

    @Test
    fun laAlturaDeUnaCuerdaSeCambiaSinTocarLasDemas() {
        openNew()
        val before = viewModel.uiState.value.editorTuning

        viewModel.processIntent(
            TablatureUiIntent.SetStringPitch(6, ScorePitch('D', 0, 2), useFlat = true)
        )

        val after = viewModel.uiState.value.editorTuning
        assertEquals(ScorePitch('D', 0, 2), after.strings.first().pitch)
        assertTrue(after.strings.first().useFlat)
        assertEquals(before.strings.drop(1), after.strings.drop(1))
    }

    @Test
    fun marcarUnaCuerdaSiempreAlAireLlegaAlEstado() {
        openNew()
        viewModel.processIntent(TablatureUiIntent.SetStringAlwaysOpen(1, true))
        assertTrue(viewModel.uiState.value.editorTuning.strings.last().alwaysOpen)
        assertEquals(listOf(1), TabStringData.alwaysOpenStrings(viewModel.uiState.value.editorTuning))
    }

    @Test
    fun anadirYQuitarCuerdasRenumeraLaAfinacion() {
        openNew()
        val before = viewModel.uiState.value.editorTuning.stringCount

        viewModel.processIntent(
            TablatureUiIntent.AddTuningString(1, ScorePitch('A', 0, 4))
        )
        assertEquals(before + 1, viewModel.uiState.value.editorTuning.stringCount)
        // La nueva queda DEBAJO de la 1.ª, como «New string…» de MuseScore: en el listado
        // grave→aguda eso es la penúltima posición, no el final.
        assertEquals(ScorePitch('A', 0, 4), viewModel.uiState.value.editorTuning.strings[before - 1].pitch)

        viewModel.processIntent(TablatureUiIntent.RemoveTuningString(1))
        assertEquals(before, viewModel.uiState.value.editorTuning.stringCount)
    }

    @Test
    fun quitarCuerdasNoDejaLaSeleccionFueraDeLaAfinacion() {
        openNew()
        // Con la 6.ª seleccionada, quitar cuerdas hasta dejar una no puede dejar la selección fuera.
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, stringNumber = 6))
        assertEquals(6, viewModel.uiState.value.selectedString)

        repeat(5) {
            val last = viewModel.uiState.value.editorTuning.stringCount
            viewModel.processIntent(TablatureUiIntent.RemoveTuningString(last))
        }

        val state = viewModel.uiState.value
        assertEquals(1, state.editorTuning.stringCount)
        assertTrue(state.selectedString in 1..state.editorTuning.stringCount)
    }

    @Test
    fun elMezcladorNoTocaElDocumentoNiElHistorial() {
        // Es una preferencia de **reproducción**, no una edición: mover el volumen no debe marcar el
        // canto como modificado ni llenar la pila de deshacer.
        openNew()
        val before = viewModel.uiState.value
        viewModel.processIntent(TablatureUiIntent.SetMixerVolume(0.3f))
        viewModel.processIntent(TablatureUiIntent.ToggleMixerMute)
        val after = viewModel.uiState.value
        assertEquals(before.editorMeasures, after.editorMeasures)
        assertEquals(before.canUndo, after.canUndo)
        assertEquals(before.hasUnsavedChanges, after.hasUnsavedChanges)
    }

    @Test
    fun cambiarDeCantoNoDejaLasTablaturasDelAnterior() = runTest {
        // Regresión: el ViewModel es compartido por todas las pantallas y cada canto abría un
        // colector que nunca se cancelaba, así que la tablatura de un canto acababa viéndose en
        // todos. Al abrir otro canto, su lista tiene que quedar limpia.
        val repo = PerSongFakeTablatureRepository()
        repo.stored.value = listOf(tabOf("t1", "s1"))
        repo.channel("s1").value = listOf(tabOf("t1", "s1"))
        val vm = TablatureViewModel(repo)

        vm.processIntent(TablatureUiIntent.LoadForSong("s1", "Canto uno", "C"))
        advanceUntilIdle()
        assertEquals(listOf("t1"), vm.uiState.value.tablatures.map { it.id })

        vm.processIntent(TablatureUiIntent.LoadForSong("s2", "Canto dos", "D"))
        advanceUntilIdle()
        assertEquals("s2", vm.uiState.value.songId)
        assertTrue(vm.uiState.value.tablatures.isEmpty())
    }

    @Test
    fun unaEmisionTardiaDelCantoAnteriorNoPisaElEstado() = runTest {
        // El colector del canto viejo puede emitir después de haber abierto otro (Room tarda en
        // entregar la consulta). Esa emisión no puede pintar sus tablaturas sobre el canto nuevo.
        val repo = PerSongFakeTablatureRepository()
        repo.stored.value = listOf(tabOf("t1", "s1"), tabOf("t2", "s2"))
        repo.channel("s1").value = listOf(tabOf("t1", "s1"))
        repo.channel("s2").value = listOf(tabOf("t2", "s2"))
        val vm = TablatureViewModel(repo)

        vm.processIntent(TablatureUiIntent.LoadForSong("s1", "Canto uno", "C"))
        advanceUntilIdle()
        vm.processIntent(TablatureUiIntent.LoadForSong("s2", "Canto dos", "D"))
        advanceUntilIdle()

        // Llega tarde un cambio del canto viejo.
        repo.channel("s1").value = listOf(tabOf("t1", "s1"), tabOf("t3", "s1"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("s2", state.songId)
        assertEquals(listOf("t2"), state.tablatures.map { it.id })
    }

    private fun tabOf(id: String, songId: String): Tablature = Tablature(
        id = id,
        songId = songId,
        title = "Tabla $id",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.EIGHTH
    )

    @Test
    fun soltarElCantoDejaElEstadoSinTablaturas() = runTest {
        val repo = PerSongFakeTablatureRepository()
        repo.stored.value = listOf(tabOf("t1", "s1"))
        repo.channel("s1").value = listOf(tabOf("t1", "s1"))
        val vm = TablatureViewModel(repo)

        vm.processIntent(TablatureUiIntent.LoadForSong("s1", "Canto uno", "C"))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.tablatures.size)

        vm.processIntent(TablatureUiIntent.ClearSong)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.songId.isBlank())
        assertTrue(state.tablatures.isEmpty())
    }

    @Test
    fun sinCantoNoSeGuardaLaTablatura() = runTest {
        // Regresión: con el `songId` del canto anterior todavía en el estado, una tablatura escrita
        // en un canto nuevo sin guardar se persistía en el canto viejo.
        val repo = PerSongFakeTablatureRepository()
        val vm = TablatureViewModel(repo)
        vm.processIntent(TablatureUiIntent.LoadForSong("s1", "Canto uno", "C"))
        advanceUntilIdle()
        vm.processIntent(TablatureUiIntent.ClearSong)
        advanceUntilIdle()

        vm.processIntent(TablatureUiIntent.OpenNewEditor)
        vm.processIntent(TablatureUiIntent.TapSlot(0, 0, 6))
        vm.processIntent(TablatureUiIntent.WriteFretAndAdvance(3))
        vm.processIntent(TablatureUiIntent.SaveTablature)
        advanceUntilIdle()

        assertTrue(repo.stored.value.isEmpty())
        assertNotNull(vm.uiState.value.errorMessage)
    }
}
