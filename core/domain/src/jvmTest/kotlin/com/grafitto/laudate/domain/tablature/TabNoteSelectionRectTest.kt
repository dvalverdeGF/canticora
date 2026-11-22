package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Selección por rectángulo** (*rubber band*), el gesto de MuseScore de arrastrar sobre un hueco
 * vacío del lienzo para seleccionar las notas que toca.
 *
 * La geometría vive en el Dominio ([TabNoteSelection.rect]) porque la comparten el pintor y los tests:
 * el lienzo solo aporta el rectángulo en coordenadas de **canvas** (los sistemas apilados) y recibe la
 * selección ya resuelta.
 */
class TabNoteSelectionRectTest {

    /** Ajustes estrechos: cada compás mide poco, así que 4 compases caen en varios sistemas. */
    private val narrow = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f)

    /**
     * Tablatura de [measureCount] compases, cada uno con **dos columnas** (dos negras) y **dos
     * cuerdas** por columna (cuerdas 1 y 2, trastes 0 y 3). Da 4 notas por compás.
     */
    private fun tab(measureCount: Int) = Tablature(
        id = "t",
        songId = "song-1",
        title = "T",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.QUARTER,
        measures = List(measureCount) {
            TabMeasure(
                slots = List(2) {
                    TabSlot(
                        strings = listOf(
                            TabPluck(TabPosition(1, 0)),
                            TabPluck(TabPosition(2, 3))
                        ),
                        rhythmicFigure = RhythmFigure.QUARTER
                    )
                }
            )
        }
    )

    /** Un solo sistema: ancho de sobra para los 2 compases. */
    private fun oneSystem(measureCount: Int = 2) =
        TablatureSystemsLayout.layout(tab(measureCount), maxWidth = 4000f, settings = narrow)

    /** Varios sistemas: ancho justo para un compás por línea. */
    private fun manySystems(measureCount: Int = 4) =
        TablatureSystemsLayout.layout(tab(measureCount), maxWidth = 120f, settings = narrow)

    /** `y` de la cuerda [stringNumber] en coordenadas de **canvas**, para el sistema [system]. */
    private fun stringY(system: TabSystem, stringNumber: Int): Float =
        system.topY + system.drawing.gridTop +
            (stringNumber - 1) * system.drawing.stringSpacing

    @Test
    fun unRectanguloSobreTodoElSistemaSeleccionaTodasSusNotas() {
        val systems = oneSystem()
        val system = systems.single()
        val selection = TabNoteSelection.rect(
            systems = systems,
            measures = tab(2).measures,
            left = 0f,
            top = 0f,
            right = 4000f,
            bottom = system.topY + system.height
        )
        // 2 compases × 2 columnas × 2 cuerdas = 8 notas.
        assertEquals("el rectángulo cubre las 8 notas del sistema", 8, selection.size)
    }

    @Test
    fun unRectanguloSobreUnaColumnaSeleccionaLasNotasDeEsaColumna() {
        val systems = oneSystem()
        val system = systems.single()
        val box = system.drawing.columns.getValue(0).first()
        val selection = TabNoteSelection.rect(
            systems = systems,
            measures = tab(2).measures,
            left = box.left + 1f,
            top = 0f,
            right = box.left + box.width - 1f,
            bottom = system.topY + system.height
        )
        assertEquals("solo las 2 cuerdas de la primera columna", 2, selection.size)
        assertTrue(
            "y son las de la columna 0 del compás 0",
            selection.notes.all { it.measureIndex == 0 && it.slotIndex == 0 }
        )
    }

    @Test
    fun unRectanguloSobreUnaCuerdaSeleccionaLasNotasDeEsaCuerda() {
        val systems = oneSystem()
        val system = systems.single()
        val y1 = stringY(system, 1)
        val selection = TabNoteSelection.rect(
            systems = systems,
            measures = tab(2).measures,
            left = 0f,
            top = y1 - 2f,
            right = 4000f,
            bottom = y1 + 2f
        )
        // 2 compases × 2 columnas = 4 notas, todas de la cuerda 1.
        assertEquals("solo las 4 notas de la cuerda 1", 4, selection.size)
        assertTrue("todas de la cuerda 1", selection.notes.all { it.stringNumber == 1 })
    }

    @Test
    fun elRectanguloUsaCoordenadasDelLienzoYNoDelSistema() {
        // Con varios sistemas, el rectángulo se da en coordenadas de **canvas**: un rectángulo sobre
        // el segundo sistema tiene que devolver notas del segundo sistema, no del primero.
        val systems = manySystems()
        assertTrue("el fixture tiene que dar varios sistemas", systems.size >= 2)
        val second = systems[1]
        val selection = TabNoteSelection.rect(
            systems = systems,
            measures = tab(4).measures,
            left = 0f,
            top = second.topY,
            right = 4000f,
            bottom = second.topY + second.height
        )
        assertEquals("las 4 notas del segundo sistema", 4, selection.size)
        assertTrue(
            "y son del compás que empieza ese sistema",
            selection.notes.all { it.measureIndex == second.measureIndices.first() }
        )
    }

    @Test
    fun arrastrarHaciaArribaIzquierdaSeleccionaLoMismo() {
        // El gesto puede empezar en cualquier esquina: el rectángulo se normaliza.
        val systems = oneSystem()
        val system = systems.single()
        val box = system.drawing.columns.getValue(0).first()
        val derechaAbajo = TabNoteSelection.rect(
            systems = systems,
            measures = tab(2).measures,
            left = box.left + 1f,
            top = 0f,
            right = box.left + box.width - 1f,
            bottom = system.topY + system.height
        )
        val izquierdaArriba = TabNoteSelection.rect(
            systems = systems,
            measures = tab(2).measures,
            left = box.left + box.width - 1f,
            top = system.topY + system.height,
            right = box.left + 1f,
            bottom = 0f
        )
        assertEquals("el rectángulo se normaliza", derechaAbajo.notes, izquierdaArriba.notes)
    }

    @Test
    fun unRectanguloVacioNoSeleccionaNada() {
        val systems = oneSystem()
        val system = systems.single()
        val y = stringY(system, 1)
        val selection = TabNoteSelection.rect(
            systems = systems,
            measures = tab(2).measures,
            left = 100f,
            top = y,
            right = 100f,
            bottom = y
        )
        assertTrue("un rectángulo de área cero no selecciona nada", selection.isEmpty)
    }

    @Test
    fun unRectanguloFueraDeLosSistemasNoSeleccionaNada() {
        val systems = oneSystem()
        val selection = TabNoteSelection.rect(
            systems = systems,
            measures = tab(2).measures,
            left = 0f,
            top = 10_000f,
            right = 4000f,
            bottom = 20_000f
        )
        assertTrue("por debajo de todos los sistemas no hay nada", selection.isEmpty)
    }

    @Test
    fun unRectanguloEntreDosCuerdasNoSeleccionaNada() {
        // El hueco vertical entre la cuerda 1 y la 2: el rectángulo cruza todas las columnas pero no
        // toca ninguna cifra, así que no selecciona nada.
        val systems = oneSystem()
        val system = systems.single()
        val y1 = stringY(system, 1)
        val y2 = stringY(system, 2)
        val medio = (y1 + y2) / 2f
        val selection = TabNoteSelection.rect(
            systems = systems,
            measures = tab(2).measures,
            left = 0f,
            top = medio - 1f,
            right = 4000f,
            bottom = medio + 1f
        )
        assertTrue("entre dos cuerdas no hay cifras que seleccionar", selection.isEmpty)
    }

    @Test
    fun elRectanguloNoSeSaleDeLosCompasesQueExisten() {
        // Un `measureIndex` de la geometría que ya no está en la lista de compases se ignora en vez
        // de reventar (p. ej. si el usuario borra un compás mientras arrastra).
        val systems = oneSystem()
        val selection = TabNoteSelection.rect(
            systems = systems,
            measures = emptyList(),
            left = 0f,
            top = 0f,
            right = 4000f,
            bottom = 4000f
        )
        assertTrue("sin compases no hay nada que seleccionar", selection.isEmpty)
    }
}
