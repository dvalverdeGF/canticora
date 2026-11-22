package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Agrupamiento de barras de unión por pulso** (punto 6 del usuario, 2026-10-14).
 *
 * El `salmo2.pdf` une las **8 corcheas** de su compás 3 en **dos** barras de 4 (x = 1126..1285 y
 * 1336..1495), no en una sola barra de compás entero. La regla es la tabla `noteGroups` de MuseScore 4
 * (`src/engraving/dom/groups.cpp`).
 */
class TabBeamGroupingTest {

    private fun mark(
        figure: RhythmFigure,
        beatOffset: Double,
        measure: Int = 0
    ) = TabRhythmMark(
        figure = figure,
        x = (beatOffset * 100.0).toFloat(),
        y = 100f,
        measureIndex = measure,
        beatOffset = beatOffset
    )

    /** [count] figuras iguales seguidas, empezando en el pulso 0. */
    private fun run(figure: RhythmFigure, count: Int, measure: Int = 0) =
        (0 until count).map { mark(figure, it * figure.beatValue, measure) }

    // ── Posiciones de corte ───────────────────────────────────────────────────────────────────────

    @Test
    fun enCuatroCuartosLasCorcheasSeCortanEnLaMitadDelCompas() {
        assertEquals(
            listOf(0.0, 2.0),
            TabBeamGrouping.breakPositions(ScoreTimeSignature("4", 4), RhythmFigure.EIGHTH)
        )
    }

    @Test
    fun enCuatroCuartosLasSemicorcheasSeCortanEnCadaNegra() {
        assertEquals(
            listOf(0.0, 1.0, 2.0, 3.0),
            TabBeamGrouping.breakPositions(ScoreTimeSignature("4", 4), RhythmFigure.SIXTEENTH)
        )
    }

    @Test
    fun enTresCuartosLasCorcheasSeCortanEnCadaNegra() {
        assertEquals(
            listOf(0.0, 1.0, 2.0),
            TabBeamGrouping.breakPositions(ScoreTimeSignature("3", 4), RhythmFigure.EIGHTH)
        )
    }

    @Test
    fun enSeisOctavosLasCorcheasSeCortanEnTresMasTres() {
        assertEquals(
            listOf(0.0, 1.5),
            TabBeamGrouping.breakPositions(ScoreTimeSignature("6", 8), RhythmFigure.EIGHTH)
        )
    }

    @Test
    fun enDoceOctavosLasCorcheasSeCortanEnCuatroTresillos() {
        assertEquals(
            listOf(0.0, 1.5, 3.0, 4.5),
            TabBeamGrouping.breakPositions(ScoreTimeSignature("12", 8), RhythmFigure.EIGHTH)
        )
    }

    @Test
    fun unaFiguraSinBarraNoSeCortaNunca() {
        assertEquals(
            listOf(0.0),
            TabBeamGrouping.breakPositions(ScoreTimeSignature("4", 4), RhythmFigure.QUARTER)
        )
    }

    @Test
    fun unCompasNoTabuladoUsaLaReservaDeMuseScore() {
        // 4/8 no está en la tabla `noteGroups`, así que MuseScore recurre a `Groups::endings`
        // (`staff.cpp` → `Staff::group`): un nodo por unidad del denominador, todos con `BEGIN`.
        // Consecuencia fiel pero llamativa: en 4/8 las corcheas **no se unen** entre sí.
        assertEquals(
            listOf(0.0, 0.5, 1.0, 1.5),
            TabBeamGrouping.breakPositions(ScoreTimeSignature("4", 8), RhythmFigure.EIGHTH)
        )
    }

    @Test
    fun unCompasAditivoSeResuelvePorSuNumeroTotalDePulsos() {
        // «3+2»/8 no es una clave de la tabla, pero suma 5 pulsos de 1/8: MuseScore lo trata como 5/8,
        // que sí está tabulado y se corta 3+2.
        assertEquals(
            listOf(0.0, 1.5),
            TabBeamGrouping.breakPositions(ScoreTimeSignature("3+2", 8), RhythmFigure.EIGHTH)
        )
    }

    // ── Arranque de barra ──────────────────────────────────────────────────────────────────────────

    @Test
    fun enCuatroCuartosLaQuintaCorcheaArrancaBarra() {
        val cuatroCuartos = ScoreTimeSignature("4", 4)
        assertTrue(TabBeamGrouping.startsBeam(0.0, RhythmFigure.EIGHTH, cuatroCuartos))
        assertFalse(TabBeamGrouping.startsBeam(0.5, RhythmFigure.EIGHTH, cuatroCuartos))
        assertFalse(TabBeamGrouping.startsBeam(1.0, RhythmFigure.EIGHTH, cuatroCuartos))
        assertFalse(TabBeamGrouping.startsBeam(1.5, RhythmFigure.EIGHTH, cuatroCuartos))
        assertTrue(TabBeamGrouping.startsBeam(2.0, RhythmFigure.EIGHTH, cuatroCuartos))
        assertFalse(TabBeamGrouping.startsBeam(2.5, RhythmFigure.EIGHTH, cuatroCuartos))
    }

    @Test
    fun unaFiguraAContratiempoNoCortaLaBarra() {
        // El tresillo y la síncopa caen **entre** nodos: MuseScore deja la barra seguir.
        assertFalse(TabBeamGrouping.startsBeam(0.3, RhythmFigure.EIGHTH, ScoreTimeSignature("4", 4)))
        assertFalse(TabBeamGrouping.startsBeam(0.25, RhythmFigure.SIXTEENTH, ScoreTimeSignature("4", 4)))
    }

    @Test
    fun unaNegraNoArrancaBarraPorqueNoLleva() {
        assertFalse(TabBeamGrouping.startsBeam(1.0, RhythmFigure.QUARTER, ScoreTimeSignature("4", 4)))
    }

    // ── Grupos de barras ───────────────────────────────────────────────────────────────────────────

    @Test
    fun lasOchoCorcheasDeCuatroCuartosSeUnenDeCuatroEnCuatro() {
        val groups = TabBeamGrouping.groupsOf(run(RhythmFigure.EIGHTH, 8), ScoreTimeSignature("4", 4))
        assertEquals("dos barras, no una de compás entero", 2, groups.size)
        assertEquals(listOf(0, 1, 2, 3), groups[0])
        assertEquals(listOf(4, 5, 6, 7), groups[1])
    }

    @Test
    fun enTresCuartosLasSeisCorcheasSeUnenDeDosEnDos() {
        val groups = TabBeamGrouping.groupsOf(run(RhythmFigure.EIGHTH, 6), ScoreTimeSignature("3", 4))
        assertEquals(3, groups.size)
        assertEquals(listOf(0, 1), groups[0])
        assertEquals(listOf(2, 3), groups[1])
        assertEquals(listOf(4, 5), groups[2])
    }

    @Test
    fun enSeisOctavosLasCorcheasSeUnenDeTresEnTres() {
        val groups = TabBeamGrouping.groupsOf(run(RhythmFigure.EIGHTH, 6), ScoreTimeSignature("6", 8))
        assertEquals(2, groups.size)
        assertEquals(listOf(0, 1, 2), groups[0])
        assertEquals(listOf(3, 4, 5), groups[1])
    }

    @Test
    fun enDoceOctavosLasCorcheasSeUnenDeTresEnTres() {
        val groups = TabBeamGrouping.groupsOf(run(RhythmFigure.EIGHTH, 12), ScoreTimeSignature("12", 8))
        assertEquals(4, groups.size)
        assertEquals(listOf(0, 1, 2), groups[0])
        assertEquals(listOf(9, 10, 11), groups[3])
    }

    @Test
    fun unSilencioCortaLaBarra() {
        val marks = listOf(
            mark(RhythmFigure.EIGHTH, 0.0),
            mark(RhythmFigure.EIGHTH, 0.5),
            mark(RhythmFigure.EIGHTH_REST, 1.0),
            mark(RhythmFigure.EIGHTH, 1.5),
            mark(RhythmFigure.EIGHTH, 2.0),
            mark(RhythmFigure.EIGHTH, 2.5)
        )
        val groups = TabBeamGrouping.groupsOf(marks, ScoreTimeSignature("4", 4))
        assertEquals(2, groups.size)
        assertEquals(listOf(0, 1), groups[0])
        assertEquals(listOf(4, 5), groups[1])
    }

    @Test
    fun unaFiguraSinCorcheteCortaLaBarra() {
        val marks = listOf(
            mark(RhythmFigure.EIGHTH, 0.0),
            mark(RhythmFigure.EIGHTH, 0.5),
            mark(RhythmFigure.QUARTER, 1.0),
            mark(RhythmFigure.EIGHTH, 2.0)
        )
        val groups = TabBeamGrouping.groupsOf(marks, ScoreTimeSignature("4", 4))
        assertEquals("la corchea suelta de después no forma barra", 1, groups.size)
        assertEquals(listOf(0, 1), groups.single())
    }

    @Test
    fun unaCorcheaSolaNoFormaBarra() {
        val marks = listOf(mark(RhythmFigure.EIGHTH, 0.0))
        assertTrue(TabBeamGrouping.groupsOf(marks, ScoreTimeSignature("4", 4)).isEmpty())
    }

    @Test
    fun lasCorcheasDeDosCompasesNoSeUnenEntreSi() {
        val marks = listOf(
            mark(RhythmFigure.EIGHTH, 3.5, measure = 0),
            mark(RhythmFigure.EIGHTH, 0.0, measure = 1)
        )
        assertTrue(TabBeamGrouping.groupsOf(marks, ScoreTimeSignature("4", 4)).isEmpty())
    }

    @Test
    fun lasSemicorcheasDeUnPulsoSeUnenTodas() {
        // Las 3 semicorcheas del compás 4 del `salmo2` (pulsos 0, 0,25 y 0,5) van en **una** barra.
        val marks = (0 until 3).map { mark(RhythmFigure.SIXTEENTH, it * 0.25) }
        val groups = TabBeamGrouping.groupsOf(marks, ScoreTimeSignature("4", 4))
        assertEquals(1, groups.size)
        assertEquals(listOf(0, 1, 2), groups.single())
    }

    @Test
    fun elOrdenDeLecturaSeReconstruyeAunqueLlegueDesordenado() {
        val marks = listOf(
            mark(RhythmFigure.EIGHTH, 0.5),
            mark(RhythmFigure.EIGHTH, 0.0)
        )
        val groups = TabBeamGrouping.groupsOf(marks, ScoreTimeSignature("4", 4))
        assertEquals(listOf(1, 0), groups.single())
    }

    // ── Integración con la maquetación ─────────────────────────────────────────────────────────────

    @Test
    fun laMaquetacionPublicaLosGruposDeBarrasDelCompas() {
        val measure = TabMeasure(
            slots = (0 until 8).map {
                TabSlot(strings = listOf(TabPluck(1, 0)), rhythmicFigure = RhythmFigure.EIGHTH)
            }
        )
        val tablature = Tablature(
            id = "t",
            songId = "song-1",
            title = "T",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH,
            measures = listOf(measure)
        )
        val drawing = TablatureSystemsLayout.layout(
            tablature,
            maxWidth = 2000f,
            settings = TabLayoutSettings(staff = TabStaffOptions.COMMON)
        ).single().drawing

        assertEquals(8, drawing.rhythmMarks.size)
        assertEquals("las 8 corcheas se parten en 4+4", 2, drawing.beamGroups.size)
        assertEquals(listOf(0, 1, 2, 3), drawing.beamGroups[0])
        assertEquals(listOf(4, 5, 6, 7), drawing.beamGroups[1])
        // La posición en negras de cada figura es la que decide el corte.
        assertEquals(
            listOf(0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5),
            drawing.rhythmMarks.map { it.beatOffset }
        )
    }
}
