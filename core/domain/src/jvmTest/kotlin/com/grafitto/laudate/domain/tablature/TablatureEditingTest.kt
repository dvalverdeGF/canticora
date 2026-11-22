package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabBendAmount
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de los helpers puros de edición de la tablatura por **columnas**. */
class TablatureEditingTest {

    private val time = ScoreTimeSignature("4", 4)

    /** Tablatura de trabajo con `columns` columnas en un solo compás (modelo de columnas). */
    private fun withColumns(columns: Int): List<TabMeasure> =
        listOf(TabMeasure(List(columns) { TabSlot() }))

    @Test
    fun slotsPerMeasure_isOnlyAReadingGuide() {
        // Sigue existiendo como guía de lectura (planilla y PDF), pero NO impone el tamaño de la rejilla.
        assertEquals(4, TablatureEditing.slotsPerMeasure(time, TabResolution.QUARTER))
        assertEquals(8, TablatureEditing.slotsPerMeasure(time, TabResolution.EIGHTH))
        assertEquals(16, TablatureEditing.slotsPerMeasure(time, TabResolution.SIXTEENTH))
        assertEquals(6, TablatureEditing.slotsPerMeasure(ScoreTimeSignature("6", 8), TabResolution.EIGHTH))
    }

    @Test
    fun emptyMeasures_areEmptyByDefault() {
        // Con el modelo de columnas un compás nace VACÍO: la primera nota crea su columna.
        val measures = TablatureEditing.emptyMeasures(time, TabResolution.EIGHTH, measureCount = 2)
        assertEquals(2, measures.size)
        assertTrue(measures.all { it.slots.isEmpty() })
    }

    @Test
    fun emptyMeasures_canBeSeededWithGuideColumns() {
        val measures = TablatureEditing.emptyMeasures(time, TabResolution.EIGHTH, 1, withGuideColumns = true)
        assertEquals(8, measures[0].slots.size)
    }

    @Test
    fun insertColumn_addsAtEnd_andPushesWhenInTheMiddle() {
        var measures = withColumns(2)
        measures = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        measures = TablatureEditing.setFret(measures, 0, 1, 1, 5)
        // Insertar al principio desplaza las dos notas a la derecha.
        measures = TablatureEditing.insertColumn(measures, 0, 0)
        assertEquals(3, measures[0].slots.size)
        assertTrue(measures[0].slots[0].isEmpty)
        assertEquals(3, measures[0].slots[1].strings[0].fret)
        assertEquals(5, measures[0].slots[2].strings[0].fret)
    }

    @Test
    fun insertColumn_beyondTheEnd_padsWithEmptyColumns() {
        var measures = withColumns(1)
        measures = TablatureEditing.insertColumn(measures, 0, 3)
        assertEquals(4, measures[0].slots.size)
    }

    @Test
    fun insertColumn_onEmptyMeasure_createsTheFirstColumn() {
        var measures = listOf(TabMeasure())
        measures = TablatureEditing.insertColumn(measures, 0, 0)
        assertEquals(1, measures[0].slots.size)
    }

    @Test
    fun removeColumn_dropsTheColumn_andKeepsTheMeasure() {
        var measures = withColumns(2)
        measures = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        measures = TablatureEditing.setFret(measures, 0, 1, 1, 5)
        measures = TablatureEditing.removeColumn(measures, 0, 0)
        // Queda la segunda columna (con su nota), no la primera.
        assertEquals(1, measures[0].slots.size)
        assertEquals(5, measures[0].slots[0].strings[0].fret)
        // Quitar la última deja el compás vacío (silencio), pero conserva el compás.
        measures = TablatureEditing.removeColumn(measures, 0, 0)
        assertTrue(measures[0].slots.isEmpty())
        assertEquals(1, measures.size)
    }

    @Test
    fun insertBar_splitsTheMeasureInTwo() {
        var measures = withColumns(4)
        measures = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        measures = TablatureEditing.setFret(measures, 0, 2, 1, 5)
        // Barra tras la columna 1: el compás 0 conserva las columnas 0-1 y el 1 recibe el resto.
        measures = TablatureEditing.insertBar(measures, 0, 1)
        assertEquals(2, measures.size)
        assertEquals(2, measures[0].slots.size)
        assertEquals(2, measures[1].slots.size)
        assertEquals(3, measures[0].slots[0].strings[0].fret)
        assertEquals(5, measures[1].slots[0].strings[0].fret)
    }

    @Test
    fun insertBar_movesEndRepeatToTheNewMeasure() {
        var measures = withColumns(2)
        measures = listOf(measures[0].copy(repeat = TabRepeatMark.END))
        measures = TablatureEditing.insertBar(measures, 0, 0)
        // La repetición de cierre marca el FINAL del compás: viaja al compás nuevo.
        assertEquals(null, measures[0].repeat)
        assertEquals(TabRepeatMark.END, measures[1].repeat)
    }

    @Test
    fun togglePluck_addsThenRemoves() {
        var measures = withColumns(1)
        measures = TablatureEditing.togglePluck(measures, 0, 0, stringNumber = 6, fret = 0)
        assertEquals(1, measures[0].slots[0].strings.size)
        measures = TablatureEditing.togglePluck(measures, 0, 0, stringNumber = 6, fret = 0)
        assertTrue(measures[0].slots[0].isEmpty)
    }

    @Test
    fun togglePluck_changesFretWhenDifferent() {
        var measures = withColumns(1)
        measures = TablatureEditing.togglePluck(measures, 0, 0, stringNumber = 6, fret = 0)
        measures = TablatureEditing.togglePluck(measures, 0, 0, stringNumber = 6, fret = 3)
        assertEquals(1, measures[0].slots[0].strings.size)
        assertEquals(3, measures[0].slots[0].strings[0].fret)
    }

    @Test
    fun multipleStrings_perSlot_areSortedByNumber() {
        var measures = withColumns(1)
        measures = TablatureEditing.togglePluck(measures, 0, 0, 6, 0)
        measures = TablatureEditing.togglePluck(measures, 0, 0, 1, 3)
        measures = TablatureEditing.togglePluck(measures, 0, 0, 3, 2)
        val strings = measures[0].slots[0].strings.map { it.stringNumber }
        assertEquals(listOf(1, 3, 6), strings)
    }

    @Test
    fun setFret_and_removePluck() {
        var measures = withColumns(3)
        measures = TablatureEditing.togglePluck(measures, 0, 2, 1, 0)
        measures = TablatureEditing.setFret(measures, 0, 2, 1, 5)
        assertEquals(5, measures[0].slots[2].strings[0].fret)
        measures = TablatureEditing.removePluck(measures, 0, 2, 1)
        assertTrue(measures[0].slots[2].isEmpty)
    }

    @Test
    fun addAndRemoveMeasure() {
        var measures = withColumns(1)
        measures = TablatureEditing.addMeasure(measures, time, TabResolution.EIGHTH)
        assertEquals(2, measures.size)
        // (Fase B.1) Un compás nuevo nace **completo**: con su silencio de compás, no vacío.
        assertTrue(
            "el compás nuevo nace con su silencio",
            TabMeasureMeter.fillWithRests(measures[1], time, TabResolution.EIGHTH) == measures[1]
        )
        assertTrue("y solo tiene silencios", TabMeasureMeter.isAllRests(measures[1]))
        measures = TablatureEditing.removeMeasure(measures)
        assertEquals(1, measures.size)
        assertEquals(1, TablatureEditing.removeMeasure(measures).size)
    }

    @Test
    fun resize_noLongerShrinksTheGrid() {
        // Cambiar compás o resolución ya NO recorta las columnas escritas (son libres).
        var measures = withColumns(5)
        measures = TablatureEditing.setFret(measures, 0, 4, 1, 7)
        val resized = TablatureEditing.resize(measures, time, TabResolution.SIXTEENTH)
        assertEquals(5, resized[0].slots.size)
        assertEquals(7, resized[0].slots[4].strings[0].fret)
    }

    @Test
    fun toggleHeld_marksProlongation() {
        var measures = withColumns(2)
        measures = TablatureEditing.toggleHeld(measures, 0, 1)
        assertTrue(measures[0].slots[1].isHeld)
        measures = TablatureEditing.toggleHeld(measures, 0, 1)
        assertFalse(measures[0].slots[1].isHeld)
    }

    @Test
    fun hasContent_detectsNotes() {
        assertFalse(TablatureEditing.hasContent(withColumns(2)))
        val withNote = TablatureEditing.togglePluck(withColumns(1), 0, 0, 1, 0)
        assertTrue(TablatureEditing.hasContent(withNote))
    }

    @Test
    fun outOfRangeIndices_areIgnored() {
        val measures = withColumns(1)
        assertEquals(measures, TablatureEditing.setSlot(measures, 5, 0, TabSlot()))
        assertEquals(measures, TablatureEditing.clearSlot(measures, 0, 99))
    }

    @Test
    fun toggleTechnique_combinesTechniques() {
        var measures = withColumns(1)
        measures = TablatureEditing.togglePluck(measures, 0, 0, 1, 3)
        measures = TablatureEditing.toggleTechnique(measures, 0, 0, 1, TabTechnique.HAMMER)
        measures = TablatureEditing.toggleTechnique(measures, 0, 0, 1, TabTechnique.SLIDE)
        val pluck = measures[0].slots[0].strings[0]
        // Las técnicas se COMBINAN (ligado + slide), como en My Guitar Tabs.
        assertTrue(pluck.has(TabTechnique.HAMMER))
        assertTrue(pluck.has(TabTechnique.SLIDE))
        // Volver a pulsar la quita.
        measures = TablatureEditing.toggleTechnique(measures, 0, 0, 1, TabTechnique.HAMMER)
        assertFalse(measures[0].slots[0].strings[0].has(TabTechnique.HAMMER))
        assertTrue(measures[0].slots[0].strings[0].has(TabTechnique.SLIDE))
        // NONE limpia todas.
        measures = TablatureEditing.toggleTechnique(measures, 0, 0, 1, TabTechnique.NONE)
        assertTrue(measures[0].slots[0].strings[0].techniques.isEmpty())
    }

    @Test
    fun toggleTechnique_doesNotCreateNotes() {
        val measures = TablatureEditing.toggleTechnique(withColumns(1), 0, 0, 1, TabTechnique.BEND)
        assertTrue(measures[0].slots[0].isEmpty)
    }

    @Test
    fun changingFret_keepsAllTechniques() {
        var measures = withColumns(1)
        measures = TablatureEditing.togglePluck(measures, 0, 0, 1, 3)
        measures = TablatureEditing.toggleTechnique(measures, 0, 0, 1, TabTechnique.TREMOLO)
        measures = TablatureEditing.toggleTechnique(measures, 0, 0, 1, TabTechnique.VIBRATO)
        measures = TablatureEditing.setFret(measures, 0, 0, 1, 7)
        val pluck = measures[0].slots[0].strings[0]
        assertEquals(7, pluck.fret)
        assertTrue(pluck.has(TabTechnique.TREMOLO))
        assertTrue(pluck.has(TabTechnique.VIBRATO))
    }

    @Test
    fun rhythmicFigureAndTriplet_updateOnlySelectedColumn() {
        val measures = withColumns(2)
        val withFigure = TablatureEditing.setRhythmicFigure(
            measures,
            measureIndex = 0,
            slotIndex = 1,
            figure = RhythmFigure.DOTTED_EIGHTH
        )
        val triplet = TablatureEditing.toggleTripletGroup(withFigure, 0, 1)

        assertEquals(null, triplet[0].slots[0].rhythmicFigure)
        assertEquals(RhythmFigure.DOTTED_EIGHTH, triplet[0].slots[1].rhythmicFigure)
        assertTrue(triplet[0].slots[1].isTriplet)
    }

    @Test
    fun toggleTripletGroup_marksThreeConsecutiveColumns() {
        val measures = withColumns(2)

        val marked = TablatureEditing.toggleTripletGroup(measures, 0, 0)

        // Un tresillo es un **grupo**: se marcan tres columnas seguidas (creando las que falten).
        assertEquals(3, marked[0].slots.size)
        assertTrue(marked[0].slots.all { it.isTriplet })
    }

    @Test
    fun toggleTripletGroup_undoesTheWholeContiguousRun() {
        val measures = withColumns(6)
        val marked = TablatureEditing.toggleTripletGroup(measures, 0, 0)

        // Al volver a pulsar sobre una casilla del grupo, se deshace el grupo **entero**.
        val cleared = TablatureEditing.toggleTripletGroup(marked, 0, 2)

        assertTrue(cleared[0].slots.none { it.isTriplet })
    }

    @Test
    fun repeatCount_isClampedToSupportedRange() {
        assertEquals(2, TablatureEditing.setRepeatCount(withColumns(1), 0, 0).single().repeatCount)
        assertEquals(8, TablatureEditing.setRepeatCount(withColumns(1), 0, 99).single().repeatCount)
    }

    @Test
    fun setRepeat_and_setEnding_useConcreteValues() {
        var measures = TablatureEditing.addMeasure(withColumns(1), time, TabResolution.EIGHTH)
        measures = TablatureEditing.setRepeat(measures, 0, TabRepeatMark.BOTH)
        assertEquals(TabRepeatMark.BOTH, measures[0].repeat)
        measures = TablatureEditing.setEnding(measures, 0, 2)
        assertEquals(2, measures[0].ending)
        measures = TablatureEditing.setRepeat(measures, 0, null)
        assertEquals(null, measures[0].repeat)
    }

    @Test
    fun editingColumns_preservesMeasureMarks() {
        // Las marcas del manuscrito son del DOCUMENTO: editar columnas NUNCA las borra (RF-127).
        var measures = TablatureEditing.addMeasure(withColumns(1), time, TabResolution.EIGHTH)
        measures = TablatureEditing.setRepeat(measures, 0, TabRepeatMark.START)
        measures = TablatureEditing.setEnding(measures, 0, 1)
        measures = TablatureEditing.insertColumn(measures, 0, 0)
        measures = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        assertEquals(TabRepeatMark.START, measures[0].repeat)
        assertEquals(1, measures[0].ending)
    }

    @Test
    fun setBendAmount_storesTheFigure_andClearsItWithNull() {
        var measures = TablatureEditing.togglePluck(withColumns(1), 0, 0, 1, 7)
        measures = TablatureEditing.toggleTechnique(measures, 0, 0, 1, TabTechnique.BEND)
        measures = TablatureEditing.setBendAmount(measures, 0, 0, 1, TabBendAmount.HALF)
        assertEquals(TabBendAmount.HALF, measures[0].slots[0].strings[0].bendAmount)
        assertEquals("1/2", measures[0].slots[0].strings[0].bendAmount?.label)
        measures = TablatureEditing.setBendAmount(measures, 0, 0, 1, null)
        assertEquals(null, measures[0].slots[0].strings[0].bendAmount)
        // La nota y su técnica siguen intactas: la cifra del bend es un matiz, no un marcado.
        assertEquals(7, measures[0].slots[0].strings[0].fret)
        assertTrue(measures[0].slots[0].strings[0].has(TabTechnique.BEND))
    }

    @Test
    fun setPalmMuteSpan_clampsToTheAllowedRange() {
        var measures = TablatureEditing.togglePluck(withColumns(1), 0, 0, 1, 3)
        measures = TablatureEditing.setPalmMuteSpan(measures, 0, 0, 1, 3)
        assertEquals(3, measures[0].slots[0].strings[0].palmMuteSpan)
        measures = TablatureEditing.setPalmMuteSpan(measures, 0, 0, 1, 99)
        assertEquals(MAX_PALM_MUTE_SPAN, measures[0].slots[0].strings[0].palmMuteSpan)
        measures = TablatureEditing.setPalmMuteSpan(measures, 0, 0, 1, 0)
        assertEquals(1, measures[0].slots[0].strings[0].palmMuteSpan)
        measures = TablatureEditing.setPalmMuteSpan(measures, 0, 0, 1, null)
        assertEquals(null, measures[0].slots[0].strings[0].palmMuteSpan)
    }

    @Test
    fun newTechniques_combineWithTheExistingOnes() {
        // Ligado + slide, bend + vibrato: los marcados del catálogo de My Guitar Tabs se suman.
        var measures = TablatureEditing.togglePluck(withColumns(1), 0, 0, 1, 5)
        measures = TablatureEditing.toggleTechnique(measures, 0, 0, 1, TabTechnique.HAMMER)
        measures = TablatureEditing.toggleTechnique(measures, 0, 0, 1, TabTechnique.SLIDE)
        measures = TablatureEditing.toggleTechnique(measures, 0, 0, 1, TabTechnique.BEND)
        measures = TablatureEditing.toggleTechnique(measures, 0, 0, 1, TabTechnique.VIBRATO)
        val pluck = measures[0].slots[0].strings[0]
        assertEquals(
            setOf(TabTechnique.HAMMER, TabTechnique.SLIDE, TabTechnique.BEND, TabTechnique.VIBRATO),
            pluck.techniques
        )
        assertTrue(pluck.hasBend)
        // Volver a pulsar la letra quita solo ese marcado.
        measures = TablatureEditing.toggleTechnique(measures, 0, 0, 1, TabTechnique.SLIDE)
        assertEquals(
            setOf(TabTechnique.HAMMER, TabTechnique.BEND, TabTechnique.VIBRATO),
            measures[0].slots[0].strings[0].techniques
        )
    }

    @Test
    fun mutedString_isNotSounded() {
        // La X del apagado no suena: no debe entrar en el cálculo de alturas ni en la reproducción.
        var measures = TablatureEditing.togglePluck(withColumns(1), 0, 0, 1, 3)
        measures = TablatureEditing.togglePluck(measures, 0, 0, 2, 5)
        measures = TablatureEditing.toggleTechnique(measures, 0, 0, 1, TabTechnique.MUTED)
        val tablature = Tablature(
            id = "t",
            songId = "song-1",
            title = "T",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = time,
            resolution = TabResolution.QUARTER,
            measures = measures
        )
        assertTrue(measures[0].slots[0].strings[0].isMuted)
        // Solo suena la 2ª cuerda (Si3 + 5 trastes = Mi4, MIDI 64); la X de la 1ª no aporta altura.
        val sounded = tablature.soundedPitches()
        assertEquals(1, sounded.size)
        assertEquals(64, sounded.single().midi)
    }

    // ── Reequilibrio del compás (2026-10-15) ───────────────────────────────────

    @Test
    fun rebalance_groupsTheRestBehindTheNoteByBeat() {
        // Rejilla de corcheas (8 columnas) y se escribe una **negra** en la primera: el compás queda
        // en 4 pulsos con la nota en el 1 y el silencio **agrupado por pulso**: negra (pulso 2) +
        // blanca (pulsos 3-4). No tres negras sueltas ni dos negras: eso es lo que hacía que la
        // pantalla se llenara de silencios menudos (2026-10-27).
        val grid = TablatureEditing.emptyCompleteMeasure(time, TabResolution.EIGHTH)
        val written = TablatureEditing.setFret(listOf(grid), 0, 0, 1, 3)
        val withFigure = TablatureEditing.setRhythmicFigure(written, 0, 0, RhythmFigure.QUARTER)
        val balanced = TablatureEditing.rebalance(withFigure[0], time, TabResolution.EIGHTH, RhythmFigure.QUARTER)

        assertEquals(3, balanced.slots.size)
        assertEquals(RhythmFigure.QUARTER, balanced.slots[0].rhythmicFigure)
        assertEquals(RhythmFigure.QUARTER_REST, balanced.slots[1].rhythmicFigure)
        assertEquals(RhythmFigure.HALF_REST, balanced.slots[2].rhythmicFigure)
        assertEquals(4.0, TabMeasureMeter.durationOf(balanced, TabResolution.EIGHTH), 1e-6)
    }

    @Test
    fun rebalance_keepsTheMeasureComplete_whenWritingInTheMiddle() {
        // Se escribe en la 3.ª columna de una rejilla de negras: las dos primeras se conservan y el
        // hueco de detrás se rellena con una negra de silencio.
        val grid = TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER)
        val written = TablatureEditing.setFret(listOf(grid), 0, 2, 1, 7)
        val balanced = TablatureEditing.rebalance(written[0], time, TabResolution.QUARTER, RhythmFigure.QUARTER)

        assertEquals(4, balanced.slots.size)
        assertEquals(7, balanced.slots[2].strings.single().fret)
        assertEquals(RhythmFigure.QUARTER_REST, balanced.slots[3].rhythmicFigure)
        assertEquals(4.0, TabMeasureMeter.durationOf(balanced, TabResolution.QUARTER), 1e-6)
    }

    @Test
    fun rebalance_neverLeavesTheMeasureShort_andGroupsByBeat() {
        // Una **corchea** en el pulso 1 deja 3,5 negras: se rellena con silencios **agrupados por
        // pulso**, no con siete corcheas sueltas. El compás sigue durando 4/4.
        val grid = TablatureEditing.emptyCompleteMeasure(time, TabResolution.EIGHTH)
        val written = TablatureEditing.setFret(listOf(grid), 0, 0, 1, 0)
        val withEighth = TablatureEditing.setRhythmicFigure(written, 0, 0, RhythmFigure.EIGHTH)
        val balanced = TablatureEditing.rebalance(withEighth[0], time, TabResolution.EIGHTH, RhythmFigure.QUARTER)

        assertEquals(RhythmFigure.EIGHTH, balanced.slots[0].rhythmicFigure)
        assertEquals(RhythmFigure.EIGHTH_REST, balanced.slots[1].rhythmicFigure)
        assertEquals(RhythmFigure.QUARTER_REST, balanced.slots[2].rhythmicFigure)
        assertEquals(RhythmFigure.HALF_REST, balanced.slots[3].rhythmicFigure)
        assertEquals(4.0, TabMeasureMeter.durationOf(balanced, TabResolution.EIGHTH), 1e-6)
    }

    @Test
    fun rebalance_groupsTheRestsAlignedToTheBeat() {
        // Una **corchea** en el pulso 1 deja 3,5 negras. Ni la figura activa (negra) ni la
        // subdivisión (negra) dividen ese hueco, así que se parte en varias figuras — pero
        // **alineadas al pulso**, como escribe MuseScore: el silencio de corchea completa el pulso 1,
        // la negra ocupa el pulso 2 y la blanca los pulsos 3-4.
        //
        // (2026-10-27) Antes se elegía siempre la figura **mayor** que cupiera y salía
        // `blanca con puntillo + corchea`: un silencio que **cruza el pulso 2** y que no se escribe
        // así. El usuario lo describió como «salen silencios a lo loco».
        val grid = TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER)
        val written = TablatureEditing.setFret(listOf(grid), 0, 0, 1, 0)
        val withEighth = TablatureEditing.setRhythmicFigure(written, 0, 0, RhythmFigure.EIGHTH)
        val balanced = TablatureEditing.rebalance(withEighth[0], time, TabResolution.QUARTER, RhythmFigure.QUARTER)

        assertEquals(4, balanced.slots.size)
        assertEquals(RhythmFigure.EIGHTH, balanced.slots[0].rhythmicFigure)
        assertEquals(RhythmFigure.EIGHTH_REST, balanced.slots[1].rhythmicFigure)
        assertEquals(RhythmFigure.QUARTER_REST, balanced.slots[2].rhythmicFigure)
        assertEquals(RhythmFigure.HALF_REST, balanced.slots[3].rhythmicFigure)
        assertEquals(4.0, TabMeasureMeter.durationOf(balanced, TabResolution.QUARTER), 1e-6)
    }

    @Test
    fun splitIntoRests_neverCrossesABeatWithAShortFigure() {
        // La regla de agrupación, aislada: un hueco de 3 negras detrás de una negra en el pulso 1
        // **no** se escribe con una blanca con puntillo (cruzaría el pulso 2), sino negra + blanca.
        assertEquals(
            listOf(RhythmFigure.QUARTER_REST, RhythmFigure.HALF_REST),
            TabMeasureMeter.splitIntoRests(3.0, startBeats = 1.0)
        )
        // Y desde el pulso 1 un hueco de compás entero cabe en **una** redonda (4 divide a 0).
        assertEquals(
            listOf(RhythmFigure.WHOLE_REST),
            TabMeasureMeter.splitIntoRests(4.0, startBeats = 0.0)
        )
        // Una blanca sí empieza en el pulso 1 (2 divide a 2) pero **no** en el 2.
        assertEquals(
            listOf(RhythmFigure.HALF_REST),
            TabMeasureMeter.splitIntoRests(2.0, startBeats = 2.0)
        )
    }

    @Test
    fun rebalanceMeasure_onlyTouchesTheGivenMeasure() {
        val measures = listOf(
            TablatureEditing.emptyCompleteMeasure(time, TabResolution.EIGHTH),
            TablatureEditing.emptyCompleteMeasure(time, TabResolution.EIGHTH)
        )
        val written = TablatureEditing.setFret(measures, 1, 0, 1, 5)
        val withFigure = TablatureEditing.setRhythmicFigure(written, 1, 0, RhythmFigure.QUARTER)
        val balanced = TablatureEditing.rebalanceMeasure(
            withFigure, 1, time, TabResolution.EIGHTH, RhythmFigure.QUARTER
        )

        assertEquals(8, balanced[0].slots.size)
        assertEquals(3, balanced[1].slots.size)
    }

    @Test
    fun rebalance_keepsAnExplicitRestFigure() {
        // Pulsar «negra» con el cursor sobre una casilla **vacía** es una elección explícita: la
        // columna se conserva y el compás se rellena a partir de ella.
        val grid = TablatureEditing.emptyCompleteMeasure(time, TabResolution.EIGHTH)
        val withQuarterRest = TablatureEditing.setRhythmicFigure(listOf(grid), 0, 0, RhythmFigure.QUARTER_REST)
        val balanced = TablatureEditing.rebalance(
            withQuarterRest[0], time, TabResolution.EIGHTH, RhythmFigure.QUARTER_REST
        )

        assertEquals(3, balanced.slots.size)
        assertEquals(RhythmFigure.QUARTER_REST, balanced.slots[0].rhythmicFigure)
        assertEquals(RhythmFigure.QUARTER_REST, balanced.slots[1].rhythmicFigure)
        assertEquals(RhythmFigure.HALF_REST, balanced.slots[2].rhythmicFigure)
        assertEquals(4.0, TabMeasureMeter.durationOf(balanced, TabResolution.EIGHTH), 1e-6)
    }

// ── Estructura de compases y voces (Fase 3 de paridad) ─────────────────────

    @Test
    fun clearMeasures_emptiesTheContent_butKeepsTheMarks() {
        // «Vaciar compases» de MuseScore: el contenido desaparece, las marcas del compás no.
        val measures = listOf(
            TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER),
            TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER)
        )
        val written = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        val marked = TablatureEditing.setText(
            TablatureEditing.setTempo(TablatureEditing.setRepeat(written, 0, TabRepeatMark.START), 0, 96),
            0,
            "Solo"
        )
        val cleared = TablatureEditing.clearMeasures(marked, 0..0, time, TabResolution.QUARTER)

        assertTrue(cleared[0].slots.all { it.isEmpty })
        assertEquals(TabRepeatMark.START, cleared[0].repeat)
        assertEquals(96, cleared[0].tempo)
        assertEquals("Solo", cleared[0].text)
        // El compás sigue completo: es un silencio, no un hueco.
        assertEquals(4.0, TabMeasureMeter.durationOf(cleared[0], TabResolution.QUARTER), 1e-6)
    }

    @Test
    fun clearMeasures_onlyTouchesTheRange() {
        val measures = List(3) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        val written = TablatureEditing.setFret(measures, 1, 0, 1, 5)
        val cleared = TablatureEditing.clearMeasures(written, 0..0, time, TabResolution.QUARTER)

        assertTrue(cleared[0].slots.all { it.isEmpty })
        assertEquals(5, cleared[1].slots[0].strings.single().fret)
    }

    @Test
    fun moveMeasures_reordersTheBlock() {
        // `to` es el índice en la lista **ya sin el bloque**: mover 0..0 a 2 lo deja al final.
        val measures = List(3) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        val tagged = measures.mapIndexed { i, m -> m.copy(text = "M$i") }
        val moved = TablatureEditing.moveMeasures(tagged, 0..0, 2)

        assertEquals(listOf("M1", "M2", "M0"), moved.map { it.text })
    }

    @Test
    fun moveMeasures_movesABlockToTheFront() {
        val measures = List(4) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        val tagged = measures.mapIndexed { i, m -> m.copy(text = "M$i") }
        val moved = TablatureEditing.moveMeasures(tagged, 2..3, 0)

        assertEquals(listOf("M2", "M3", "M0", "M1"), moved.map { it.text })
    }

    @Test
    fun moveMeasures_keepsTheDocumentSize() {
        val measures = List(3) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        assertEquals(3, TablatureEditing.moveMeasures(measures, 1..1, 0).size)
    }

    @Test
    fun setSlotVoice_storesTheVoice_andClampsIt() {
        val measures = TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER).let { listOf(it) }
        val voiced = TablatureEditing.setSlotVoice(measures, 0, 1, 3)
        assertEquals(3, voiced[0].slots[1].voice)
        assertEquals(1, voiced[0].slots[0].voice)

        // Fuera de rango se acota al máximo soportado (4 voces, como MuseScore).
        val clamped = TablatureEditing.setSlotVoice(measures, 0, 0, 9)
        assertEquals(TabVoiceColors.VOICE_COUNT, clamped[0].slots[0].voice)
    }

    @Test
    fun setVoiceInRange_voicesEverySlotOfTheRange() {
        val measures = List(2) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        val voiced = TablatureEditing.setVoiceInRange(measures, 0..0, 2)

        assertTrue(voiced[0].slots.all { it.voice == 2 })
        assertTrue(voiced[1].slots.all { it.voice == 1 })
    }

    @Test
    fun rebalance_preservesTheVoiceOfTheMeasure() {
        // El reequilibrio rellena con silencios: deben heredar la voz del compás, no volver a la 1.
        val grid = TablatureEditing.emptyCompleteMeasure(time, TabResolution.EIGHTH)
        val voiced = TablatureEditing.setVoiceInRange(listOf(grid), 0..0, 3)
        val written = TablatureEditing.setFret(voiced, 0, 0, 1, 3)
        val balanced = TablatureEditing.rebalance(written[0], time, TabResolution.EIGHTH, RhythmFigure.QUARTER)

        assertTrue(balanced.slots.all { it.voice == 3 })
    }

    @Test
    fun relayoutRests_preservesTheVoice() {
        val grid = TablatureEditing.emptyCompleteMeasure(time, TabResolution.EIGHTH)
        val voiced = TablatureEditing.setVoiceInRange(listOf(grid), 0..0, 2)
        val written = TablatureEditing.setFret(voiced, 0, 0, 1, 3)
        val relaid = TablatureEditing.relayoutRests(written[0], time, TabResolution.EIGHTH)

        assertTrue(relaid.slots.all { it.voice == 2 })
    }

    @Test
    fun forceSystemBreak_isToggledPerMeasure() {
        val measures = List(2) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        val broken = TablatureEditing.toggleForceSystemBreak(measures, 0)
        assertTrue(broken[0].forceSystemBreak)
        assertFalse(broken[1].forceSystemBreak)

        val undone = TablatureEditing.toggleForceSystemBreak(broken, 0)
        assertFalse(undone[0].forceSystemBreak)
    }

    // ── Salto de página (`Ctrl`+`Return` de MuseScore) ──────────────────────────────────────────

    @Test
    fun elSaltoDePaginaImplicaElDeSistema() {
        val measures = List(2) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        val broken = TablatureEditing.setForcePageBreak(measures, 0, true)

        assertTrue("el salto de pagina abre hoja", broken[0].forcePageBreak)
        assertTrue("y tambien cierra la linea", broken[0].forceSystemBreak)
        assertTrue("el compas arranca sistema", broken[0].startsSystem)
        assertFalse(broken[1].forcePageBreak)
    }

    @Test
    fun quitarElSaltoDePaginaQuitaTambienElDeSistema() {
        val measures = List(2) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        val broken = TablatureEditing.setForcePageBreak(measures, 0, true)
        val undone = TablatureEditing.setForcePageBreak(broken, 0, false)

        assertFalse(undone[0].forcePageBreak)
        assertFalse("no queda un salto de sistema que el usuario no pidio", undone[0].forceSystemBreak)
        assertFalse(undone[0].startsSystem)
    }

    @Test
    fun toggleForcePageBreak_alternaYNoTocaOtrosCompases() {
        val measures = List(3) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        val on = TablatureEditing.toggleForcePageBreak(measures, 1)
        assertTrue(on[1].forcePageBreak)
        assertFalse(on[0].forcePageBreak)
        assertFalse(on[2].forcePageBreak)

        val off = TablatureEditing.toggleForcePageBreak(on, 1)
        assertFalse(off[1].forcePageBreak)
        assertFalse(off[1].forceSystemBreak)
    }

    @Test
    fun toggleForcePageBreak_enUnCompasQueNoExisteNoRompe() {
        val measures = List(1) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        assertEquals(measures, TablatureEditing.toggleForcePageBreak(measures, 7))
    }

    // ── Ligadura de expresión (`S` de MuseScore) ────────────────────────────────────────────────

    @Test
    fun toggleSlur_ligaYDesligaLaCasilla() {
        var measures = withColumns(2)
        measures = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        measures = TablatureEditing.setFret(measures, 0, 1, 1, 5)

        val slurred = TablatureEditing.toggleSlur(measures, 0, 0)
        assertTrue(slurred[0].slots[0].slurToNext)
        // La casilla destino **no** se marca: la ligadura es propiedad de la casilla de origen.
        assertFalse(slurred[0].slots[1].slurToNext)

        assertFalse(TablatureEditing.toggleSlur(slurred, 0, 0)[0].slots[0].slurToNext)
    }

    @Test
    fun toggleSlur_noExigeQueLasAlturasCoincidan() {
        // A diferencia del *tie*, la ligadura de expresión es fraseo: 3 → 5 es válido.
        var measures = withColumns(2)
        measures = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        measures = TablatureEditing.setFret(measures, 0, 1, 1, 5)
        assertTrue(TablatureEditing.toggleSlur(measures, 0, 0)[0].slots[0].slurToNext)
    }

    @Test
    fun toggleSlur_seRechazaSiNoHayCasillaSiguienteConSonido() {
        var measures = withColumns(2)
        measures = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        // La segunda casilla está vacía: no hay a dónde ligar.
        assertEquals(measures, TablatureEditing.toggleSlur(measures, 0, 0))
    }

    @Test
    fun toggleSlur_cruzaDeCompas() {
        var measures = List(2) { TabMeasure(List(2) { TabSlot() }) }
        measures = TablatureEditing.setFret(measures, 0, 1, 2, 7)
        measures = TablatureEditing.setFret(measures, 1, 0, 2, 7)
        assertEquals(1 to 0, TablatureEditing.nextSoundingSlot(measures, 0, 1))
        assertTrue(TablatureEditing.toggleSlur(measures, 0, 1)[0].slots[1].slurToNext)
    }

    @Test
    fun nextSoundingSlot_saltaLasCasillasVacias() {
        var measures = withColumns(3)
        measures = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        measures = TablatureEditing.setFret(measures, 0, 2, 1, 3)
        assertEquals(0 to 2, TablatureEditing.nextSoundingSlot(measures, 0, 0))
    }

    // ── Ligadura de prolongación (B-10) ──────────────────────────────────────────────────────────

    /** Dos columnas con la misma nota (traste 3) en la cuerda 1. */
    private fun tiedPair(): List<TabMeasure> {
        var measures = withColumns(2)
        measures = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        return TablatureEditing.setFret(measures, 0, 1, 1, 3)
    }

    @Test
    fun toggleTie_ligaYDesligaLaNota() {
        val measures = tiedPair()
        val tied = TablatureEditing.toggleTie(measures, 0, 0, 1)
        assertTrue(tied[0].slots[0].strings[0].tiedToNext)
        // La nota destino **no** se marca: la ligadura es una propiedad de la nota de origen.
        assertFalse(tied[0].slots[1].strings[0].tiedToNext)

        val undone = TablatureEditing.toggleTie(tied, 0, 0, 1)
        assertFalse(undone[0].slots[0].strings[0].tiedToNext)
    }

    @Test
    fun toggleTie_creaElDestinoConLaMismaAlturaSiElSiguienteTieneOtra() {
        // (D-90) Si la nota siguiente de esa cuerda tiene **otro traste**, no sirve como destino: se
        // crea una columna con la **misma altura**, que es lo que hace `EditTie::cmdAddTie` en
        // MuseScore cuando `searchTieNote` no encuentra destino. Antes la ligadura «no hacía nada».
        var measures = withColumns(2)
        measures = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        measures = TablatureEditing.setFret(measures, 0, 1, 1, 5)
        val result = TablatureEditing.toggleTie(measures, 0, 0, 1)
        assertTrue(
            "la ligadura se crea con la misma altura",
            result[0].slots[0].strings[0].tiedToNext
        )
    }

    @Test
    fun toggleTie_creaElDestinoSiNoHayNotaSiguiente() {
        // (D-90) Sin nota siguiente, MuseScore **crea** el destino (`cmdAddTie`), no se niega.
        var measures = withColumns(2)
        measures = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        val result = TablatureEditing.toggleTie(measures, 0, 0, 1)
        assertTrue(result[0].slots[0].strings[0].tiedToNext)
        assertTrue(
            "el destino lleva la misma nota",
            result[0].slots[1].strings.any { it.stringNumber == 1 && it.fret == 3 }
        )
    }

    @Test
    fun escribirUnaNegraEnUnCompasDeRedondaParteLaColumna() {
        // (D-93) Un `4/4` nace con **una** columna de redonda. Al escribir una negra, esa columna se
        // queda con la negra y el resto (3 pulsos) pasa a silencios detrás: la nota dura **lo suyo**
        // y el compás sigue durando 4. Antes la nota se metía en la redonda y duraba cuatro pulsos, y
        // el usuario lo describió como «vas desplazando los silencios en lugar de sustituirlos».
        val vacio = listOf(
            TabMeasureMeter.emptyCompleteMeasure(ScoreTimeSignature("4", 4), TabResolution.QUARTER)
        )
        assertEquals(1, vacio[0].slots.size)
        val partido = TablatureEditing.splitSlotForFigure(
            vacio, 0, 0, RhythmFigure.QUARTER
        )!!
        val conNota = TablatureEditing.setFret(partido, 0, 0, 1, 3)
        assertEquals(4.0, TabMeasureMeter.durationOf(conNota[0], TabResolution.QUARTER), 1e-6)
        assertEquals(RhythmFigure.QUARTER, conNota[0].slots[0].rhythmicFigure)
        assertEquals(3, conNota[0].slots[0].strings.single().position.fret)
        // El resto del compás queda como **silencios**, no como hueco.
        assertTrue(
            "el resto del compás son silencios",
            conNota[0].slots.drop(1).all { it.rhythmicFigure?.isRest == true }
        )
        assertEquals(
            "y suman los tres pulsos que faltan",
            3.0,
            conNota[0].slots.drop(1).sumOf { it.rhythmicFigure!!.beatValue },
            1e-6
        )
    }

    @Test
    fun tieTarget_cruzaDeCompas() {
        var measures = List(2) { TabMeasure(List(2) { TabSlot() }) }
        measures = TablatureEditing.setFret(measures, 0, 1, 2, 7)
        measures = TablatureEditing.setFret(measures, 1, 0, 2, 7)
        assertEquals(1 to 0, TablatureEditing.tieTarget(measures, 0, 1, 2))
        // Y la ligadura se puede poner aunque el destino esté en el compás siguiente.
        assertTrue(TablatureEditing.toggleTie(measures, 0, 1, 2)[0].slots[1].strings[0].tiedToNext)
    }

    @Test
    fun tieTarget_ignoraOtrasCuerdas() {
        var measures = withColumns(3)
        measures = TablatureEditing.setFret(measures, 0, 0, 1, 3)
        measures = TablatureEditing.setFret(measures, 0, 1, 2, 3)
        measures = TablatureEditing.setFret(measures, 0, 2, 1, 3)
        // La nota de la cuerda 2 en la columna 1 no cuenta: el destino es la columna 2.
        assertEquals(0 to 2, TablatureEditing.tieTarget(measures, 0, 0, 1))
    }

    @Test
    fun isTiedFromPrevious_detectaLaNotaQueRecibeLaLigadura() {
        val measures = TablatureEditing.toggleTie(tiedPair(), 0, 0, 1)
        assertTrue(TablatureEditing.isTiedFromPrevious(measures, 0, 1, 1))
        assertFalse(TablatureEditing.isTiedFromPrevious(measures, 0, 0, 1))
    }

    @Test
    fun isTiedFromPrevious_cruzaDeCompas() {
        var measures = List(2) { TabMeasure(List(2) { TabSlot() }) }
        measures = TablatureEditing.setFret(measures, 0, 1, 1, 5)
        measures = TablatureEditing.setFret(measures, 1, 0, 1, 5)
        val tied = TablatureEditing.toggleTie(measures, 0, 1, 1)
        assertTrue(TablatureEditing.isTiedFromPrevious(tied, 1, 0, 1))
    }

    @Test
    fun swapMeasures_sustituyeLaSeleccionPorElBloque() {
        val measures = List(4) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        val block = listOf(TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER))
        val swapped = TablatureEditing.swapMeasures(measures, 1..2, block)
        // El bloque (1 compás) sustituye a los dos seleccionados: el documento mengua en uno.
        assertEquals(3, swapped.size)
    }

    @Test
    fun swapMeasures_dosVecesDevuelveElDocumentoOriginal() {
        val measures = List(4) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        val block = List(2) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        val once = TablatureEditing.swapMeasures(measures, 1..2, block)
        val twice = TablatureEditing.swapMeasures(once, 1..2, measures.subList(1, 3))
        assertEquals(measures.size, twice.size)
    }

    @Test
    fun swapMeasures_conBloqueVacioNoCambiaNada() {
        val measures = List(3) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        assertEquals(measures, TablatureEditing.swapMeasures(measures, 0..0, emptyList()))
    }

    @Test
    fun swapMeasures_acotaElRangoAlDocumento() {
        val measures = List(2) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        val block = listOf(TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER))
        // Un rango que se sale por la derecha se acota al último compás: no revienta y el compás 0
        // se conserva, así que el documento mantiene su tamaño (1 bloque por 1 compás).
        assertEquals(2, TablatureEditing.swapMeasures(measures, 1..9, block).size)
    }

    // ── Anacrusa (pickup) ────────────────────────────────────────────────────────────────────────

    @Test
    fun togglePickup_recortaLosSilenciosDelFinal() {
        // Un 4/4 con dos negras sonando y dos de silencio: la anacrusa se queda con las dos negras.
        val measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(strings = listOf(TabPluck(1, 0)), rhythmicFigure = RhythmFigure.QUARTER),
                    TabSlot(strings = listOf(TabPluck(2, 2)), rhythmicFigure = RhythmFigure.QUARTER),
                    TabSlot(rhythmicFigure = RhythmFigure.QUARTER_REST),
                    TabSlot(rhythmicFigure = RhythmFigure.QUARTER_REST)
                )
            ),
            TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER)
        )
        val pickup = TablatureEditing.togglePickup(measures, time, TabResolution.QUARTER)
        assertTrue(pickup[0].isPickup)
        assertEquals("se quedan solo las dos columnas con notas", 2, pickup[0].slots.size)
        assertEquals(2.0, TabMeasureMeter.durationOf(pickup[0], TabResolution.QUARTER), 0.001)
        // El segundo compás no se toca.
        assertEquals(measures[1], pickup[1])
    }

    @Test
    fun togglePickup_esReversible() {
        // Quitarla rellena el compás hasta lo nominal: el documento vuelve a estar completo.
        val measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(strings = listOf(TabPluck(1, 0)), rhythmicFigure = RhythmFigure.QUARTER),
                    TabSlot(rhythmicFigure = RhythmFigure.QUARTER_REST)
                )
            )
        )
        val pickup = TablatureEditing.togglePickup(measures, time, TabResolution.QUARTER)
        assertEquals(1, pickup[0].slots.size)
        val restored = TablatureEditing.togglePickup(pickup, time, TabResolution.QUARTER)
        assertFalse(restored[0].isPickup)
        assertEquals(4.0, TabMeasureMeter.durationOf(restored[0], TabResolution.QUARTER), 0.001)
        assertTrue(TabMeasureMeter.isDurationValid(restored[0], time, TabResolution.QUARTER))
    }

    @Test
    fun togglePickup_noRecortaUnCompasSinNotas() {
        // Un compás vacío no tiene «final de la música» del que tirar: solo se marca.
        val measures = listOf(TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER))
        val pickup = TablatureEditing.togglePickup(measures, time, TabResolution.QUARTER)
        assertTrue(pickup[0].isPickup)
        assertEquals(measures[0].slots.size, pickup[0].slots.size)
    }

    @Test
    fun togglePickup_soloAfectaAlPrimerCompas() {
        val measures = List(3) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        val pickup = TablatureEditing.togglePickup(measures, time, TabResolution.QUARTER)
        assertTrue(pickup[0].isPickup)
        assertFalse(pickup[1].isPickup)
        assertFalse(pickup[2].isPickup)
    }

    @Test
    fun laAnacrusaNoViajaConElBloqueCopiado() {
        // Duplicar el compás 0 (la anacrusa) no puede crear una segunda anacrusa en medio: la marca
        // es una propiedad de la **posición**, como `Measure::isAnacrusis()` de MuseScore.
        val measures = listOf(
            TabMeasure(slots = listOf(TabSlot()), isPickup = true),
            TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER)
        )
        val duplicated = TablatureEditing.duplicateMeasures(measures, 0..0)
        assertEquals(3, duplicated.size)
        assertTrue("la anacrusa sigue siendo la primera", duplicated[0].isPickup)
        assertFalse("la copia no es anacrusa", duplicated[1].isPickup)
    }

    @Test
    fun normalizePickup_soloDejaLaMarcaEnElPrimerCompas() {
        val measures = listOf(
            TabMeasure(isPickup = true),
            TabMeasure(isPickup = true),
            TabMeasure(isPickup = true)
        )
        val normalized = TablatureEditing.normalizePickup(measures)
        assertTrue(normalized[0].isPickup)
        assertFalse(normalized[1].isPickup)
        assertFalse(normalized[2].isPickup)
        // Sin marcas de más no se copia la lista (no se rompe la igualdad estructural).
        val clean = List(3) { TabMeasure() }
        assertEquals(clean, TablatureEditing.normalizePickup(clean))
    }

    @Test
    fun setForceSystemBreak_setsAnExplicitValue() {
        val measures = List(2) { TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER) }
        val broken = TablatureEditing.setForceSystemBreak(measures, 1, true)
        assertTrue(broken[1].forceSystemBreak)
        assertFalse(TablatureEditing.setForceSystemBreak(broken, 1, false)[1].forceSystemBreak)
    }

    @Test
    fun splitSlot_halvesTheFigure_andKeepsTheMeasureDuration() {
        // Un `4/4` en negras: cuatro columnas. Partir la primera deja cinco columnas y la **misma**
        // duración (4,0 negras): el compás no se pasa nunca (D-73).
        val measures = listOf(TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER))
        val before = TabMeasureMeter.durationOf(measures[0], TabResolution.QUARTER)
        val split = TablatureEditing.splitSlot(measures, 0, 0, TabResolution.QUARTER)!!
        assertEquals(5, split[0].slots.size)
        assertEquals(before, TabMeasureMeter.durationOf(split[0], TabResolution.QUARTER), 1e-6)
        assertEquals(RhythmFigure.EIGHTH_REST, split[0].slots[0].rhythmicFigure)
        assertEquals(RhythmFigure.EIGHTH_REST, split[0].slots[1].rhythmicFigure)
    }

    @Test
    fun splitSlot_shortensTheNote_andLeavesARestBehind() {
        // Con una nota, la nota **se acorta** a la mitad y el hueco que deja es un silencio: es lo
        // que hace MuseScore al elegir una figura más corta sobre una nota.
        val measures = TablatureEditing.setFret(
            listOf(TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER)),
            0, 0, 1, 5
        )
        val split = TablatureEditing.splitSlot(measures, 0, 0, TabResolution.QUARTER)!!
        assertEquals(5, split[0].slots[0].strings.first().fret)
        assertEquals(RhythmFigure.EIGHTH_REST, split[0].slots[0].rhythmicFigure)
        assertTrue(split[0].slots[1].strings.isEmpty())
        assertEquals(RhythmFigure.EIGHTH_REST, split[0].slots[1].rhythmicFigure)
    }

    @Test
    fun splitSlot_returnsNull_whenTheFigureCannotBeHalved() {
        // En semicorcheas ya no hay figura de 1/32: la columna no se puede partir.
        val measures = listOf(TablatureEditing.emptyCompleteMeasure(time, TabResolution.SIXTEENTH))
        assertEquals(null, TablatureEditing.splitSlot(measures, 0, 0, TabResolution.SIXTEENTH))
    }
}
