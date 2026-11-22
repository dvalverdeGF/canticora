package com.grafitto.laudate.domain.rhythm

import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumArticulation
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.StrumRange
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmGridMappingTest {

    private fun patternOf(vararg events: RhythmEvent, ts: TimeSignature = TimeSignature.FOUR_FOUR) =
        RhythmPattern(id = "t", name = "T", timeSignature = ts, events = events.toList())

    // ── Rejilla ↔ modelo: ida y vuelta ────────────────────────────────────────

    @Test
    fun quarterPattern_roundTripsThroughGrid() {
        val pattern = patternOf(
            *(1..4).map { RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN) }.toTypedArray()
        )
        val grid = RhythmGridMapping.toGrid(pattern, RhythmGridResolution.QUARTER)
        val back = RhythmGridMapping.toEvents(grid, pattern.timeSignature)

        assertEquals(4, grid.cells.size)
        assertTrue(grid.cells.all { it.isAttack })
        assertEquals(4, back.size)
        assertTrue(back.all { it.figure == RhythmFigure.QUARTER && it.direction == StrumDirection.DOWN })
    }

    @Test
    fun eighthPattern_fillsEightSlots() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.UP),
            RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )
        val grid = RhythmGridMapping.toGrid(pattern, RhythmGridResolution.EIGHTH)

        assertEquals(8, grid.cells.size)
        assertEquals(StrumDirection.DOWN, grid.cells[0].direction)
        assertEquals(StrumDirection.UP, grid.cells[1].direction)
        // La negra ocupa dos casillas: la segunda es prolongación.
        assertTrue(grid.cells[4].isAttack)
        assertEquals(RhythmGridCell.Hold, grid.cells[5])
        assertTrue(grid.cells[6].isAttack)
        assertEquals(RhythmGridCell.Hold, grid.cells[7])
    }

    @Test
    fun halfNote_becomesOneAttackPlusHold() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.HALF, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.HALF, StrumDirection.DOWN)
        )
        val grid = RhythmGridMapping.toGrid(pattern, RhythmGridResolution.QUARTER)

        assertEquals(4, grid.cells.size)
        assertTrue(grid.cells[0].isAttack)
        assertEquals(RhythmGridCell.Hold, grid.cells[1])
        assertTrue(grid.cells[2].isAttack)
        assertEquals(RhythmGridCell.Hold, grid.cells[3])

        val back = RhythmGridMapping.toEvents(grid, pattern.timeSignature)
        assertEquals(2, back.size)
        assertTrue(back.all { it.figure == RhythmFigure.HALF })
    }

    @Test
    fun rest_isAirNotHold() {
        // ↓ · ↓ ↓ en negras: el silencio es "aire", no una prolongación.
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER_REST, StrumDirection.NONE),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )
        val grid = RhythmGridMapping.toGrid(pattern, RhythmGridResolution.QUARTER)

        assertTrue(grid.cells[0].isAttack)
        assertEquals(RhythmGridCell.Air, grid.cells[1])
        assertTrue(grid.cells[2].isAttack)

        val back = RhythmGridMapping.toEvents(grid, pattern.timeSignature)
        assertTrue(back[1].figure.isRest)
    }

    // ── Registro de cuerdas (flechas largas / cortas) ─────────────────────────

    @Test
    fun range_survivesRoundTrip() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, range = StrumRange.ALL),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, range = StrumRange.BASS),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, range = StrumRange.TREBLE),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.UP, range = StrumRange.BASS)
        )
        val grid = RhythmGridMapping.toGrid(pattern, RhythmGridResolution.QUARTER)
        val back = RhythmGridMapping.toEvents(grid, pattern.timeSignature)

        assertEquals(StrumRange.ALL, grid.cells[0].range)
        assertEquals(StrumRange.BASS, grid.cells[1].range)
        assertEquals(StrumRange.TREBLE, grid.cells[2].range)
        assertEquals(StrumRange.BASS, back[3].range)
    }

    @Test
    fun rest_hasAllRangeEffective() {
        val event = RhythmEvent(RhythmFigure.QUARTER_REST, StrumDirection.NONE, range = StrumRange.BASS)
        assertEquals(StrumRange.ALL, event.effectiveRange)
    }

    // ── Articulaciones: la rejilla nunca las pierde ───────────────────────────

    @Test
    fun articulation_survivesRoundTrip() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, StrumArticulation.ACCENT),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.UP, StrumArticulation.ARPEGGIO),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, StrumArticulation.STACCATO),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.UP, StrumArticulation.ACCENT_STACCATO)
        )
        val grid = RhythmGridMapping.toGrid(pattern, RhythmGridResolution.QUARTER)
        val back = RhythmGridMapping.toEvents(grid, pattern.timeSignature)

        assertEquals(StrumArticulation.ACCENT, grid.cells[0].articulation)
        assertEquals(StrumArticulation.ARPEGGIO, grid.cells[1].articulation)
        assertEquals(
            pattern.events.map { it.effectiveArticulation },
            back.map { it.effectiveArticulation }
        )
    }

    @Test
    fun builtInPatterns_roundTripWithoutLosingArticulations() {
        // Regresión: la rejilla no tenía campo de articulación, así que abrir un patrón
        // integrado y tocar una sola casilla borraba TODOS sus acentos.
        GuitarPatternLibrary.builtInPatterns.forEach { pattern ->
            val resolution = RhythmGridMapping.bestResolution(pattern) ?: RhythmGridResolution.SIXTEENTH
            val back = RhythmGridMapping.toEvents(
                RhythmGridMapping.toGrid(pattern, resolution),
                pattern.timeSignature
            )
            assertEquals(
                "${pattern.name}: duración alterada",
                pattern.events.sumOf { it.beatValue },
                back.sumOf { it.beatValue },
                1e-6
            )
            assertEquals(
                "${pattern.name}: articulaciones perdidas",
                pattern.events.map { it.effectiveArticulation },
                back.map { it.effectiveArticulation }
            )
            assertEquals(
                "${pattern.name}: registros perdidos",
                pattern.events.map { it.effectiveRange },
                back.map { it.effectiveRange }
            )
        }
    }

    // ── Edición quirúrgica de una casilla (applyCell) ─────────────────────────

    @Test
    fun applyCell_onAttack_keepsFigureAndArticulation() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, StrumArticulation.ACCENT),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )
        val events = RhythmGridMapping.applyCell(
            pattern,
            RhythmGridResolution.QUARTER,
            slot = 1,
            cell = RhythmGridCell.Up
        )

        assertEquals(4, events.size)
        assertEquals(RhythmFigure.QUARTER, events[1].figure)
        assertEquals(StrumDirection.UP, events[1].direction)
        // El acento de la primera posición no se toca.
        assertEquals(StrumArticulation.ACCENT, events[0].articulation)
        assertEquals(4.0, events.sumOf { it.beatValue }, 1e-6)
    }

    @Test
    fun applyCell_onHold_splitsTheNoteInTwo() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.HALF, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.HALF, StrumDirection.DOWN)
        )
        // La blanca ocupa las casillas 0 y 1; tocar la 1 con un golpe la parte en dos negras.
        val events = RhythmGridMapping.applyCell(
            pattern,
            RhythmGridResolution.QUARTER,
            slot = 1,
            cell = RhythmGridCell.Up
        )

        assertEquals(3, events.size)
        assertEquals(RhythmFigure.QUARTER, events[0].figure)
        assertEquals(StrumDirection.DOWN, events[0].direction)
        assertEquals(RhythmFigure.QUARTER, events[1].figure)
        assertEquals(StrumDirection.UP, events[1].direction)
        assertEquals(4.0, events.sumOf { it.beatValue }, 1e-6)
    }

    @Test
    fun applyCell_withAirOnHold_leavesARestInTheMiddle() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.HALF, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.HALF, StrumDirection.DOWN)
        )
        val events = RhythmGridMapping.applyCell(
            pattern,
            RhythmGridResolution.QUARTER,
            slot = 1,
            cell = RhythmGridCell.Air
        )

        assertEquals(3, events.size)
        assertTrue(events[1].figure.isRest)
        assertEquals(4.0, events.sumOf { it.beatValue }, 1e-6)
    }

    @Test
    fun applyCell_withHoldOnAttack_extendsTheNote() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )
        // «Prolonga» significa «esta casilla continúa el golpe anterior», así que se aplica
        // sobre la casilla 1 para alargar la negra de la casilla 0.
        val events = RhythmGridMapping.applyCell(
            pattern,
            RhythmGridResolution.QUARTER,
            slot = 1,
            cell = RhythmGridCell.Hold
        )

        assertEquals(RhythmFigure.HALF, events[0].figure)
        assertEquals(4.0, events.sumOf { it.beatValue }, 1e-6)
    }

    @Test
    fun applyCell_withHoldOnFirstSlot_isANoOp() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )
        // No hay golpe previo que prolongar: la casilla 0 no puede ser una prolongación.
        val events = RhythmGridMapping.applyCell(
            pattern,
            RhythmGridResolution.QUARTER,
            slot = 0,
            cell = RhythmGridCell.Hold
        )

        assertEquals(2, events.size)
        assertEquals(RhythmFigure.QUARTER, events[0].figure)
        assertEquals(2.0, events.sumOf { it.beatValue }, 1e-6)
    }

    @Test
    fun applyCell_beyondTheEnd_appendsTheStrokeWhereTapped() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )
        val events = RhythmGridMapping.applyCell(
            pattern,
            RhythmGridResolution.QUARTER,
            slot = 3,
            cell = RhythmGridCell.Up
        )

        assertEquals(4.0, events.sumOf { it.beatValue }, 1e-6)
        assertEquals(StrumDirection.UP, events.last().direction)
        assertEquals(3, RhythmGridMapping.slotStarts(patternOf(*events.toTypedArray()), RhythmGridResolution.QUARTER).last())
    }

    @Test
    fun applyCell_withAirOnAttack_turnsTheStrokeIntoARest() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )
        val events = RhythmGridMapping.applyCell(
            pattern,
            RhythmGridResolution.QUARTER,
            slot = 2,
            cell = RhythmGridCell.Air
        )

        assertEquals(4, events.size)
        assertTrue(events[2].figure.isRest)
        assertEquals(4.0, events.sumOf { it.beatValue }, 1e-6)
    }

    @Test
    fun cellNext_keepsRangeAndArticulation() {
        val cell = RhythmGridCell(
            direction = StrumDirection.DOWN,
            range = StrumRange.TREBLE,
            articulation = StrumArticulation.ACCENT
        )
        val next = RhythmGridCell.next(cell)

        assertEquals(StrumDirection.UP, next.direction)
        assertEquals(StrumRange.TREBLE, next.range)
        assertEquals(StrumArticulation.ACCENT, next.articulation)
    }

    // ── Compatibilidad de la rejilla con el patrón ────────────────────────────

    @Test
    fun fits_isTrueForRepresentablePattern() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.HALF, StrumDirection.DOWN)
        )
        assertTrue(RhythmGridMapping.fits(pattern, RhythmGridResolution.EIGHTH))
    }

    @Test
    fun fits_isFalseWhenAnEventSpansHalfSlot() {
        // Una corchea con puntillo (0.75) no encaja en una rejilla de corcheas (0.5).
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.DOTTED_EIGHTH, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.SIXTEENTH, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.HALF, StrumDirection.DOWN)
        )
        assertFalse(RhythmGridMapping.fits(pattern, RhythmGridResolution.EIGHTH))
        assertTrue(RhythmGridMapping.fits(pattern, RhythmGridResolution.SIXTEENTH))
    }

    @Test
    fun bestResolution_picksCoarsestThatFits() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )
        assertEquals(RhythmGridResolution.QUARTER, RhythmGridMapping.bestResolution(pattern))
    }

    @Test
    fun bestResolution_fallsBackToSixteenthWhenOnlyFineGridFits() {
        // Toda figura es múltiplo de una semicorchea, así que la rejilla de semicorcheas
        // siempre representa cualquier patrón válido; esta es la red de seguridad.
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.DOTTED_EIGHTH, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.DOTTED_EIGHTH, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.DOTTED_QUARTER, StrumDirection.DOWN)
        )
        assertEquals(RhythmGridResolution.SIXTEENTH, RhythmGridMapping.bestResolution(pattern))
    }

    // ── Invariante temporal: la edición nunca altera la duración del compás ───

    @Test
    fun toEvents_preservesTotalDuration_forHoldsOfAwkwardLength() {
        // 5 semicorcheas prolongadas (1,25 negras) no tienen figura única: se descompone en
        // ♩ + 𝅘𝅥𝅯 ligada para no perder tiempo ni corromper el patrón.
        val cells = listOf(
            RhythmGridCell.Down, RhythmGridCell.Hold, RhythmGridCell.Hold,
            RhythmGridCell.Hold, RhythmGridCell.Hold,
            RhythmGridCell.Up, RhythmGridCell.Hold, RhythmGridCell.Hold
        )
        val grid = RhythmGrid(cells.size.let { RhythmGridResolution.SIXTEENTH }, cells)
        val events = RhythmGridMapping.toEvents(grid, TimeSignature.FOUR_FOUR)

        // 5 + 3 semicorcheas = 2 negras, exactamente.
        assertEquals(2.0, events.sumOf { it.beatValue }, 1e-6)
        assertEquals(RhythmFigure.QUARTER, events.first().figure)
        // La parte sobrante (0,25) se liga a la negra en vez de convertirse en silencio.
        assertEquals(RhythmFigure.SIXTEENTH, events[1].figure)
        assertTrue(events[1].isTied)
        assertFalse(events.any { it.figure.isRest })
    }

    @Test
    fun resolutionSupport_excludesImpossibleCombinations() {
        // 9/8 = 4,5 negras: no cabe en casillas de negra, pero sí de corchea o semicorchea.
        assertFalse(RhythmGridResolution.QUARTER.supports(TimeSignature.NINE_EIGHT))
        assertTrue(RhythmGridResolution.EIGHTH.supports(TimeSignature.NINE_EIGHT))
        assertTrue(RhythmGridResolution.SIXTEENTH.supports(TimeSignature.NINE_EIGHT))
        assertTrue(RhythmGridResolution.QUARTER.supports(TimeSignature.FOUR_FOUR))

        // 6/8 tiene pulso de negra con puntillo (1,5 negras): no admite rejilla de negras
        // (no cuadraría con el pulso), pero sí de corcheas (3 por pulso) y semicorcheas (6).
        assertFalse(RhythmGridResolution.QUARTER.supports(TimeSignature.SIX_EIGHT))
        assertTrue(RhythmGridResolution.EIGHTH.supports(TimeSignature.SIX_EIGHT))
        assertTrue(RhythmGridResolution.SIXTEENTH.supports(TimeSignature.SIX_EIGHT))
    }

    @Test
    fun gridAlwaysCoversFullMeasure() {
        TimeSignature.common.forEach { ts ->
            RhythmGridResolution.entries.filter { it.supports(ts) }.forEach { res ->
                val grid = RhythmGridMapping.emptyGrid(ts, res)
                assertEquals(
                    "$ts / $res: la rejilla debe cubrir el compás completo",
                    ts.totalQuarterBeats,
                    grid.cells.size * res.beatValue,
                    1e-6
                )
            }
        }
    }

    @Test
    fun beatBoundaries_alignWithBeatUnit() {
        // 4/4 con corcheas: cada 2 casillas empieza un pulso.
        val grid = RhythmGridMapping.emptyGrid(TimeSignature.FOUR_FOUR, RhythmGridResolution.EIGHTH)
        assertEquals(setOf(0, 2, 4, 6), grid.beatBoundaries(TimeSignature.FOUR_FOUR))

        // 6/8 con corcheas: cada 3 casillas empieza un pulso (negra con puntillo).
        val compound = RhythmGridMapping.emptyGrid(TimeSignature.SIX_EIGHT, RhythmGridResolution.EIGHTH)
        assertEquals(setOf(0, 3), compound.beatBoundaries(TimeSignature.SIX_EIGHT))
    }

    @Test
    fun cellCycle_visitsEveryState() {
        var cell = RhythmGridCell.Down
        val visited = mutableListOf(cell)
        repeat(RhythmGridCell.cycle.size - 1) {
            cell = RhythmGridCell.next(cell)
            visited += cell
        }
        assertEquals(RhythmGridCell.cycle.toSet(), visited.toSet())
        // Un ciclo completo vuelve al punto de partida.
        assertEquals(RhythmGridCell.Down, RhythmGridCell.next(cell))
    }

    // ── Regresión: la rejilla es la fuente de verdad mientras se edita ────────

    private val palette = listOf(
        RhythmGridCell.Down,
        RhythmGridCell.Up,
        RhythmGridCell.Mute,
        RhythmGridCell.Air,
        RhythmGridCell.Hold,
        RhythmGridCell(StrumDirection.DOWN, range = StrumRange.BASS),
        RhythmGridCell(StrumDirection.UP, range = StrumRange.TREBLE)
    )

    @Test
    fun applyCell_onAirCell_placesTheStroke() {
        // Regresión: tocar una casilla de aire (silencio) debe colocar el trazo, no perderse.
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER_REST),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )
        val events = RhythmGridMapping.applyCell(
            pattern,
            RhythmGridResolution.QUARTER,
            slot = 1,
            cell = RhythmGridCell.Up
        )

        assertEquals(4, events.size)
        assertEquals(StrumDirection.UP, events[1].effectiveDirection)
        assertTrue(events[1].isAttack)
        assertEquals(4.0, events.sumOf { it.beatValue }, 1e-6)
    }

    @Test
    fun applyCell_neverChangesTotalDuration_forEveryBuiltInPattern() {
        GuitarPatternLibrary.builtInPatterns.forEach { pattern ->
            RhythmGridResolution.entries
                .filter { it.supports(pattern.timeSignature) }
                .forEach { res ->
                    val expected = pattern.barCount * pattern.barBeatValue
                    val slots = res.slotCount(pattern.timeSignature) * pattern.barCount
                    for (slot in 0 until slots) {
                        palette.forEach { cell ->
                            val events = RhythmGridMapping.applyCell(pattern, res, slot, cell)
                            assertEquals(
                                "${pattern.name} / $res / casilla $slot / $cell: el total no puede cambiar",
                                expected,
                                events.sumOf { it.beatValue },
                                1e-6
                            )
                        }
                    }
                }
        }
    }

    @Test
    fun applyCell_showsExactlyTheTappedCell() {
        // Regresión del bug «pongo unas flechas y me pone otras»: tras tocar una casilla, la
        // rejilla reconstruida desde el modelo debe mostrar exactamente esa casilla.
        GuitarPatternLibrary.builtInPatterns.forEach { pattern ->
            RhythmGridResolution.entries
                .filter { it.supports(pattern.timeSignature) }
                .forEach { res ->
                    val before = RhythmGridMapping.toGrid(pattern, res, pattern.barCount)
                    val slots = before.cells.size
                    for (slot in 0 until slots) {
                        palette.forEach { cell ->
                            val expected = before.withCell(slot, cell).cells[slot]
                            val events = RhythmGridMapping.applyCell(pattern, res, slot, cell)
                            val after = RhythmGridMapping.toGrid(
                                patternOf(*events.toTypedArray(), ts = pattern.timeSignature),
                                res,
                                pattern.barCount
                            )
                            assertEquals(
                                "${pattern.name} / $res / casilla $slot: se tocó $cell",
                                expected,
                                after.cells[slot]
                            )
                        }
                    }
                }
        }
    }

    @Test
    fun applyCell_preservesArticulationOfTheCell() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, StrumArticulation.ACCENT),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )
        // Cambiar la flecha no debe borrar el acento puesto en el editor avanzado.
        val events = RhythmGridMapping.applyCell(
            pattern,
            RhythmGridResolution.QUARTER,
            slot = 0,
            cell = RhythmGridCell.Up
        )

        assertEquals(StrumDirection.UP, events[0].direction)
        assertEquals(StrumArticulation.ACCENT, events[0].articulation)
    }

    @Test
    fun roundTrip_isExactForEveryBuiltInPattern() {
        GuitarPatternLibrary.builtInPatterns.forEach { pattern ->
            val res = RhythmGridMapping.bestResolution(pattern)
            val grid = RhythmGridMapping.toGrid(pattern, res)
            val back = RhythmGridMapping.toEvents(grid, pattern.timeSignature)
            val again = RhythmGridMapping.toGrid(
                patternOf(*back.toTypedArray(), ts = pattern.timeSignature),
                res
            )

            assertEquals("${pattern.name}: la rejilla debe ser estable", grid, again)
            assertEquals(
                "${pattern.name}: el total debe conservarse",
                pattern.barCount * pattern.barBeatValue,
                back.sumOf { it.beatValue },
                1e-6
            )
        }
    }

    @Test
    fun bestResolution_isNeverNullAndAlwaysFits() {
        GuitarPatternLibrary.builtInPatterns.forEach { pattern ->
            val res = RhythmGridMapping.bestResolution(pattern)
            assertTrue("${pattern.name} no cabe en $res", RhythmGridMapping.fits(pattern, res))
        }
    }

    @Test
    fun ties_roundTripForDurationsWithoutASingleFigure() {
        // 2,5 negras no tienen figura única: blanca + corchea ligada.
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.HALF, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.DOWN, isTied = true),
            RhythmEvent(RhythmFigure.DOTTED_QUARTER, StrumDirection.UP)
        )
        val grid = RhythmGridMapping.toGrid(pattern, RhythmGridResolution.EIGHTH)
        val back = RhythmGridMapping.toEvents(grid, pattern.timeSignature)

        assertEquals(4.0, back.sumOf { it.beatValue }, 1e-6)
        assertEquals(RhythmFigure.HALF, back[0].figure)
        assertEquals(RhythmFigure.EIGHTH, back[1].figure)
        assertTrue(back[1].isTied)
        assertFalse(back[1].isAttack)
        // La ligadura no inicia un ataque nuevo: la rejilla la ve como prolongación.
        assertEquals(RhythmGridCell.Hold, grid.cells[4])
    }

    // ── Multi-compás ─────────────────────────────────────────────────────────

    @Test
    fun barCount_isDerivedFromTheTotalDuration() {
        val oneBar = patternOf(
            *(1..4).map { RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN) }.toTypedArray()
        )
        assertEquals(1, oneBar.barCount)
        assertEquals(4.0, oneBar.targetBeatValue, 1e-6)

        val twoBars = patternOf(
            *(1..5).map { RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN) }.toTypedArray()
        )
        assertEquals(2, twoBars.barCount)
        assertEquals(8.0, twoBars.targetBeatValue, 1e-6)
        assertEquals(3.0, twoBars.remainingBeatValue, 1e-6)
    }

    @Test
    fun withBarCount_extendsWithRestsAndTrims() {
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        )

        val twoBars = RhythmGridMapping.withBarCount(pattern, RhythmGridResolution.QUARTER, 2)
        assertEquals(8.0, twoBars.sumOf { it.beatValue }, 1e-6)
        assertEquals(2, patternOf(*twoBars.toTypedArray()).barCount)
        // Los compases añadidos se rellenan con silencios.
        assertTrue(twoBars.drop(2).all { it.figure.isRest })

        val backToOne = RhythmGridMapping.withBarCount(
            patternOf(*twoBars.toTypedArray()),
            RhythmGridResolution.QUARTER,
            1
        )
        assertEquals(4.0, backToOne.sumOf { it.beatValue }, 1e-6)
    }

    @Test
    fun withBarCount_neverGoesBelowOneBar() {
        val pattern = patternOf(
            *(1..5).map { RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN) }.toTypedArray()
        )
        assertEquals(2, pattern.barCount)
        val events = RhythmGridMapping.withBarCount(pattern, RhythmGridResolution.QUARTER, 0)
        assertEquals(4.0, events.sumOf { it.beatValue }, 1e-6)
    }

    @Test
    fun barBoundaries_markEveryMeasure() {
        val grid = RhythmGridMapping.emptyGrid(
            TimeSignature.FOUR_FOUR,
            RhythmGridResolution.EIGHTH,
            barCount = 2
        )
        assertEquals(2, grid.barCount(TimeSignature.FOUR_FOUR))
        assertEquals(setOf(0, 8), grid.barBoundaries(TimeSignature.FOUR_FOUR))
        assertEquals(setOf(0, 2, 4, 6, 8, 10, 12, 14), grid.beatBoundaries(TimeSignature.FOUR_FOUR))
    }

    @Test
    fun multiBarPattern_roundTripsThroughTheGrid() {
        // Patrón de dos compases: 4/4 con ♩♩♩♩ + ♩♩𝄽𝄽.
        val pattern = patternOf(
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.UP),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.UP),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN),
            RhythmEvent(RhythmFigure.QUARTER, StrumDirection.UP),
            RhythmEvent(RhythmFigure.HALF_REST)
        )
        assertEquals(2, pattern.barCount)

        val grid = RhythmGridMapping.toGrid(pattern, RhythmGridResolution.QUARTER)
        assertEquals(8, grid.cells.size)
        assertEquals(2, grid.barCount(TimeSignature.FOUR_FOUR))

        val back = RhythmGridMapping.toEvents(grid, pattern.timeSignature)
        assertEquals(8.0, back.sumOf { it.beatValue }, 1e-6)
        assertEquals(6, back.count { it.isAttack })
    }
}
