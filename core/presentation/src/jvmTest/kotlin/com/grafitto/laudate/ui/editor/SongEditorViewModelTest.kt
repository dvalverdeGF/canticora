package com.grafitto.laudate.ui.editor

import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.model.music.DomainChord
import com.grafitto.laudate.domain.model.music.PitchClass
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.domain.model.song.ChordAnchor
import com.grafitto.laudate.domain.model.song.SectionType
import com.grafitto.laudate.domain.model.song.Song
import com.grafitto.laudate.domain.model.song.SongLine
import com.grafitto.laudate.domain.model.song.SongSection
import com.grafitto.laudate.domain.repository.CategoryRepository
import com.grafitto.laudate.domain.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifica dos comportamientos del editor de cantos:
 *
 * 1. La **vista previa** renderiza un borrador en memoria con el estado actual del editor, no la
 *    canción guardada, y ese borrador nunca se persiste.
 * 2. El **aviso de cambios sin guardar** aparece al intentar salir con ediciones pendientes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SongEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Vista previa ────────────────────────────────────────────────────────

    @Test
    fun previewDraftReflectsEditorFieldsInsteadOfTheSavedSong() = runTest(dispatcher) {
        val repository = FakeSongRepository(listOf(savedSong()))
        val vm = createViewModel(repository)
        vm.processIntent(SongEditorUiIntent.LoadSongForEditing("s1"))
        advanceUntilIdle()

        vm.processIntent(SongEditorUiIntent.UpdateTitle("Aleluya (revisado)"))
        vm.processIntent(SongEditorUiIntent.UpdateAuthor("Anónimo"))
        vm.processIntent(SongEditorUiIntent.UpdateNumber("402"))
        vm.processIntent(SongEditorUiIntent.UpdateCapo(3))
        vm.processIntent(SongEditorUiIntent.UpdateTempoBpm(96))
        vm.processIntent(SongEditorUiIntent.UpdateNotes("Entrar suave"))
        vm.processIntent(SongEditorUiIntent.SelectTimeSignature(TimeSignature.SIX_EIGHT))
        vm.processIntent(SongEditorUiIntent.ToggleCategorySelection("Gloria"))
        vm.processIntent(SongEditorUiIntent.TogglePreviewMode(true))
        advanceUntilIdle()

        val draft = requireNotNull(vm.uiState.value.previewSong)
        assertEquals("Aleluya (revisado)", draft.title)
        assertEquals("Anónimo", draft.author)
        assertEquals("402", draft.number)
        assertEquals(3, draft.capo)
        assertEquals(96, draft.tempoBpm)
        assertEquals("Entrar suave", draft.notes)
        assertEquals("6/8", draft.rhythm)
        assertEquals(listOf("Gloria"), draft.defaultLiturgicalMoments)
        // El borrador no es la canción guardada: los metadatos que no viven en el texto ChordPro
        // también se reflejan.
        assertNotEquals("Aleluya", draft.title)
        assertNotEquals("Anónimo", savedSong().author)
    }

    @Test
    fun previewDraftIsNeverPersisted() = runTest(dispatcher) {
        val repository = FakeSongRepository(listOf(savedSong()))
        val vm = createViewModel(repository)
        vm.processIntent(SongEditorUiIntent.LoadSongForEditing("s1"))
        advanceUntilIdle()

        vm.processIntent(SongEditorUiIntent.UpdateTitle("Borrador sin guardar"))
        vm.processIntent(SongEditorUiIntent.TogglePreviewMode(true))
        advanceUntilIdle()

        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun leavingPreviewDiscardsTheDraft() = runTest(dispatcher) {
        val repository = FakeSongRepository(listOf(savedSong()))
        val vm = createViewModel(repository)
        vm.processIntent(SongEditorUiIntent.LoadSongForEditing("s1"))
        advanceUntilIdle()

        vm.processIntent(SongEditorUiIntent.TogglePreviewMode(true))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isPreviewMode)

        vm.processIntent(SongEditorUiIntent.TogglePreviewMode(false))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isPreviewMode)
        assertNull(vm.uiState.value.previewSong)
    }

    @Test
    fun newSongPreviewUsesTheDraftIdAndCurrentFields() = runTest(dispatcher) {
        val repository = FakeSongRepository(emptyList())
        val vm = createViewModel(repository)
        vm.processIntent(SongEditorUiIntent.LoadSongForEditing(null))
        advanceUntilIdle()

        vm.processIntent(SongEditorUiIntent.UpdateTitle("Canto nuevo"))
        vm.processIntent(SongEditorUiIntent.TogglePreviewMode(true))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNull("Un canto nuevo sigue sin id hasta que se guarde", state.songId)
        val draft = requireNotNull(state.previewSong)
        assertEquals("Canto nuevo", draft.title)
        assertEquals("preview-draft", draft.id)
        assertTrue(repository.saved.isEmpty())
    }

    // ── Pestañas del editor ─────────────────────────────────────────────────

    @Test
    fun selectEditorTab_switchesBetweenEditorPreviewAndTablatures() = runTest(dispatcher) {
        val vm = createViewModel(FakeSongRepository(listOf(savedSong())))
        vm.processIntent(SongEditorUiIntent.LoadSongForEditing("s1"))
        advanceUntilIdle()

        // Por defecto, la pestaña activa es la del editor.
        assertEquals(SongEditorTab.EDITOR, vm.uiState.value.editorTab)

        // La pestaña de vista previa construye el borrador y activa el modo previa.
        vm.processIntent(SongEditorUiIntent.SelectEditorTab(SongEditorTab.PREVIEW))
        advanceUntilIdle()
        assertEquals(SongEditorTab.PREVIEW, vm.uiState.value.editorTab)
        assertTrue(vm.uiState.value.isPreviewMode)
        assertNotNull(vm.uiState.value.previewSong)

        // La pestaña de tablaturas desactiva el modo previa.
        vm.processIntent(SongEditorUiIntent.SelectEditorTab(SongEditorTab.TABLATURES))
        advanceUntilIdle()
        assertEquals(SongEditorTab.TABLATURES, vm.uiState.value.editorTab)
        assertFalse(vm.uiState.value.isPreviewMode)

        // Volver al editor.
        vm.processIntent(SongEditorUiIntent.SelectEditorTab(SongEditorTab.EDITOR))
        advanceUntilIdle()
        assertEquals(SongEditorTab.EDITOR, vm.uiState.value.editorTab)
    }

    // ── Aviso de cambios sin guardar ────────────────────────────────────────

    @Test
    fun noUnsavedChangesRightAfterLoading() = runTest(dispatcher) {
        val vm = createViewModel(FakeSongRepository(listOf(savedSong())))
        vm.processIntent(SongEditorUiIntent.LoadSongForEditing("s1"))
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun editingMarksUnsavedChangesAndUndoingClearsThem() = runTest(dispatcher) {
        val vm = createViewModel(FakeSongRepository(listOf(savedSong())))
        vm.processIntent(SongEditorUiIntent.LoadSongForEditing("s1"))
        advanceUntilIdle()

        vm.processIntent(SongEditorUiIntent.UpdateTitle("Otro título"))
        assertTrue(vm.uiState.value.hasUnsavedChanges)

        // Deshacer la edición deja de contar como cambio pendiente.
        vm.processIntent(SongEditorUiIntent.UpdateTitle("Aleluya"))
        assertFalse(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun savingClearsUnsavedChanges() = runTest(dispatcher) {
        val repository = FakeSongRepository(listOf(savedSong()))
        val vm = createViewModel(repository)
        vm.processIntent(SongEditorUiIntent.LoadSongForEditing("s1"))
        advanceUntilIdle()

        vm.processIntent(SongEditorUiIntent.UpdateTitle("Aleluya (revisado)"))
        vm.processIntent(SongEditorUiIntent.SaveSong)
        advanceUntilIdle()

        assertEquals(1, repository.saved.size)
        assertEquals("Aleluya (revisado)", repository.saved.first().title)
        assertFalse(vm.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun requestExitWithChangesShowsDiscardDialog() = runTest(dispatcher) {
        val vm = createViewModel(FakeSongRepository(listOf(savedSong())))
        val effects = collectEffects(vm)
        vm.processIntent(SongEditorUiIntent.LoadSongForEditing("s1"))
        advanceUntilIdle()

        vm.processIntent(SongEditorUiIntent.UpdateTitle("Otro título"))
        vm.processIntent(SongEditorUiIntent.RequestExit)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.showDiscardDialog)
        assertFalse(effects.contains(SongEditorSideEffect.NavigateBack))
    }

    @Test
    fun requestExitWithoutChangesNavigatesBack() = runTest(dispatcher) {
        val vm = createViewModel(FakeSongRepository(listOf(savedSong())))
        val effects = collectEffects(vm)
        vm.processIntent(SongEditorUiIntent.LoadSongForEditing("s1"))
        advanceUntilIdle()

        vm.processIntent(SongEditorUiIntent.RequestExit)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showDiscardDialog)
        assertTrue(effects.contains(SongEditorSideEffect.NavigateBack))
    }

    @Test
    fun dismissDiscardDialogKeepsEditing() = runTest(dispatcher) {
        val vm = createViewModel(FakeSongRepository(listOf(savedSong())))
        val effects = collectEffects(vm)
        vm.processIntent(SongEditorUiIntent.LoadSongForEditing("s1"))
        advanceUntilIdle()

        vm.processIntent(SongEditorUiIntent.UpdateTitle("Otro título"))
        vm.processIntent(SongEditorUiIntent.RequestExit)
        vm.processIntent(SongEditorUiIntent.DismissDiscardDialog)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showDiscardDialog)
        assertTrue(vm.uiState.value.hasUnsavedChanges)
        assertFalse(effects.contains(SongEditorSideEffect.NavigateBack))
    }

    @Test
    fun confirmDiscardChangesNavigatesBackAndClearsDialog() = runTest(dispatcher) {
        val vm = createViewModel(FakeSongRepository(listOf(savedSong())))
        val effects = collectEffects(vm)
        vm.processIntent(SongEditorUiIntent.LoadSongForEditing("s1"))
        advanceUntilIdle()

        vm.processIntent(SongEditorUiIntent.UpdateTitle("Otro título"))
        vm.processIntent(SongEditorUiIntent.RequestExit)
        vm.processIntent(SongEditorUiIntent.ConfirmDiscardChanges)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.showDiscardDialog)
        assertFalse(vm.uiState.value.hasUnsavedChanges)
        assertTrue(effects.contains(SongEditorSideEffect.NavigateBack))
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    /** Colecta los efectos secundarios del ViewModel en una lista viva. */
    private fun TestScope.collectEffects(vm: SongEditorViewModel): List<SongEditorSideEffect> {
        val effects = mutableListOf<SongEditorSideEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.sideEffect.collect { effects.add(it) }
        }
        return effects
    }

    private fun createViewModel(repository: SongRepository) = SongEditorViewModel(
        songRepository = repository,
        categoryRepository = FakeCategoryRepository()
    )

    /** Canto ya guardado, con contenido ChordPro real para que la vista previa tenga algo que mostrar. */
    private fun savedSong() = Song(
        id = "s1",
        title = "Aleluya",
        originalKey = "C",
        number = "1",
        author = "Taizé",
        rhythm = "4/4",
        tempoBpm = 80,
        capo = 0,
        notes = null,
        defaultLiturgicalMoments = emptyList(),
        sections = listOf(
            SongSection(
                id = "sec1",
                songId = "s1",
                sectionType = SectionType.VERSE,
                sequenceOrder = 0,
                headerTitle = null,
                rawTextContent = "Aleluya, aleluya",
                lines = listOf(
                    SongLine(
                        lineIndex = 0,
                        lyrics = "Aleluya, aleluya",
                        chords = listOf(
                            ChordAnchor(
                                id = "a1",
                                sectionId = "sec1",
                                lineIndex = 0,
                                characterIndex = 0,
                                chord = DomainChord(rootNote = PitchClass.C, rawChordText = "C"),
                                rawChordText = "C"
                            )
                        )
                    )
                )
            )
        )
    )

    private class FakeSongRepository(private val all: List<Song>) : SongRepository {
        val saved = mutableListOf<Song>()

        override fun getAllSongs(): Flow<List<Song>> = flowOf(all)
        override fun getAllSongsWithLyrics(): Flow<List<Song>> = flowOf(all)
        override suspend fun getSongCount(): Int = all.size
        override fun getFavoriteSongs(): Flow<List<Song>> = flowOf(all.filter { it.isFavorite })
        override fun getSongById(id: String): Flow<Song?> = flowOf(all.firstOrNull { it.id == id })
        override suspend fun getSongsByIds(ids: List<String>): List<Song> = all.filter { it.id in ids }
        override suspend fun getSongStubsByIds(ids: List<String>): List<Song> = getSongsByIds(ids)
        override suspend fun overwriteSong(song: Song) = saveSong(song)
        override fun searchSongs(query: String): Flow<List<Song>> = flowOf(all)
        override suspend fun saveSong(song: Song) {
            saved.add(song)
        }
        override suspend fun saveSongs(songs: List<Song>) {
            saved.addAll(songs)
        }
        override suspend fun setFavorite(songId: String, isFavorite: Boolean) = Unit
        override suspend fun deleteSong(songId: String) = Unit
        override suspend fun findSongByContentHash(hash: String): Song? = null
        override suspend fun findSongByContentSignature(song: Song): Song? = null
        override suspend fun getSongsMissingContentHash(): List<Song> = emptyList()
        override suspend fun updateContentHash(songId: String, hash: String) = Unit
        override suspend fun renameCategoryTag(oldName: String, newName: String): Int = 0
    }

    private class FakeCategoryRepository : CategoryRepository {
        private val categories = listOf(
            LiturgicalCategory(id = "c1", name = "Gloria", kind = LiturgicalCategoryKind.MASS_MOMENT)
        )
        override fun getAllCategories(): Flow<List<LiturgicalCategory>> = flowOf(categories)
        override suspend fun getAllCategoriesSnapshot(): List<LiturgicalCategory> = categories
        override suspend fun getCategoryById(id: String): LiturgicalCategory? =
            categories.firstOrNull { it.id == id }
        override suspend fun saveCategory(category: LiturgicalCategory) = Unit
        override suspend fun updateCategory(category: LiturgicalCategory) = Unit
        override suspend fun saveCategories(categories: List<LiturgicalCategory>): Int = 0
        override suspend fun deleteCategory(categoryId: String) = Unit
        override suspend fun seedStandardCategoriesIfEmpty() = Unit
        override suspend fun ensureCategories(
            names: Collection<String>,
            kind: LiturgicalCategoryKind
        ): List<String> = emptyList()
    }
}
