package com.grafitto.laudate.ui.tablature

import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.tablature.TabMeasureMeter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

private class TraceRepository : TablatureRepository {
    private val stored = MutableStateFlow<List<Tablature>>(emptyList())
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
    override fun observeSongIdsWithTablatures(): Flow<Set<String>> =
        kotlinx.coroutines.flow.flowOf(stored.value.map { it.songId }.toSet())
}

/**
 * Traza **temporal** de una sesión de escritura: imprime el compás tras cada gesto con la
 * duración acumulada de cada columna. No afirma nada; existe para ver con datos qué hace el
 * editor cuando se encadenan gestos como los del uso real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TablatureTraceTest {

    private lateinit var viewModel: TablatureViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        viewModel = TablatureViewModel(TraceRepository())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun dump(title: String) {
        val s = viewModel.uiState.value
        println("── $title ─────────────────────────────")
        println("   figura activa = ${s.selectedFigure}  resolución = ${s.editorResolution}" +
            "  cursor = ${s.selectedSlot}  compases = ${s.editorMeasureCount}")
        s.editorMeasures.take(3).forEachIndexed { m, measure ->
            val dur = TabMeasureMeter.durationOf(measure, s.editorResolution)
            val cells = measure.slots.joinToString(" | ") { slot ->
                val fig = slot.rhythmicFigure?.let { f ->
                    if (f.isRest) "silencio:${f.beatValue}" else "nota:${f.beatValue}"
                } ?: "vacía"
                val frets = slot.strings.joinToString(",") { "${it.stringNumber}:${it.position.fret}" }
                if (frets.isEmpty()) fig else "$frets($fig)"
            }
            println("   compás $m  dur=${dur}  n=${measure.slots.size}  [ $cells ]")
        }
    }

    @Test
    fun trazaEscrituraConTeclado() {
        viewModel.processIntent(TablatureUiIntent.OpenNewEditor)
        dump("abrir editor")
        repeat(8) { i ->
            viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(i))
            dump("teclado en pantalla: $i")
        }
    }

    @Test
    fun trazaEscrituraTocandoCasilla() {
        viewModel.processIntent(TablatureUiIntent.OpenNewEditor)
        dump("abrir editor")
        // Toques en casillas consecutivas de la primera cuerda, como con el ratón.
        repeat(8) { i ->
            viewModel.processIntent(TablatureUiIntent.TapSlot(0, i, 1))
            dump("toque en casilla $i")
        }
    }

    @Test
    fun trazaTecladoFisico() = runTest {
        // El usuario teclea con el teclado del ordenador (TypeFretDigit), no con el teclado en
        // pantalla. Es el camino que dejaba los silencios de la rejilla al final del compas.
        // El cursor avanza tras DIGIT_SOUND_DELAY_MS, asi que hay que dejar correr el reloj.
        viewModel.processIntent(TablatureUiIntent.OpenNewEditor)
        dump("abrir editor")
        listOf(3, 3, 5, 0, 4, 3, 5, 7).forEachIndexed { i, d ->
            viewModel.processIntent(TablatureUiIntent.TypeFretDigit(d))
            advanceTimeBy(400)
            dump("teclado fisico: $d (#$i)")
        }
    }

    @Test
    fun trazaCambioDeFigura() {
        viewModel.processIntent(TablatureUiIntent.OpenNewEditor)
        viewModel.processIntent(TablatureUiIntent.TapSlot(0, 0, 1))
        viewModel.processIntent(TablatureUiIntent.SetRhythmicFigureAtCursor(null))
        dump("figura automática")
        repeat(6) {
            viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(it))
            dump("nota $it con figura automática")
        }
    }

    @Test
    fun trazaCambioDeResolucion() {
        viewModel.processIntent(TablatureUiIntent.OpenNewEditor)
        repeat(2) { viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(it)) }
        dump("dos notas en corcheas")
        viewModel.processIntent(TablatureUiIntent.SetMeasureSubdivision(0, TabResolution.QUARTER))
        dump("cambio a negras")
        repeat(4) { viewModel.processIntent(TablatureUiIntent.WriteFretAndAdvance(it + 2)) }
        dump("más notas tras el cambio")
    }
}
