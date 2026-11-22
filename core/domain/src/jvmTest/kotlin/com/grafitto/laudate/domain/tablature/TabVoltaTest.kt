package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la **volta** (casilla de 1.ª/2.ª vez) como *spanner* de MuseScore (D-37): el rango de
 * pasadas (`1, 2.`), el corchete con su gancho, el rótulo único por casilla y la reserva de franja.
 */
class TabVoltaTest {

    private fun slot(fret: Int) = TabSlot(
        strings = listOf(TabPluck(TabPosition(1, fret))),
        rhythmicFigure = RhythmFigure.QUARTER
    )

    private fun tab(measures: List<TabMeasure>) = Tablature(
        id = "t",
        songId = "song-1",
        title = "T",
        instrument = Tuning.GUITAR.name,
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.QUARTER,
        measures = measures
    )

    private fun measure(fret: Int, ending: Int? = null, endingTo: Int? = null) =
        TabMeasure(slots = listOf(slot(fret)), ending = ending, endingTo = endingTo)

    // ── Modelo: rango de pasadas ────────────────────────────────────────────────

    @Test
    fun endings_expandsTheRange() {
        assertEquals(listOf(1), measure(0, ending = 1).endings)
        assertEquals(listOf(1, 2), measure(0, ending = 1, endingTo = 2).endings)
        assertEquals(listOf(2, 3, 4), measure(0, ending = 2, endingTo = 4).endings)
        assertTrue(measure(0).endings.isEmpty())
    }

    @Test
    fun endings_collapsesAnInvertedRange() {
        // `2, 1.` no existe: si el rango viene al revés se queda en la primera pasada.
        assertEquals(listOf(2), measure(0, ending = 2, endingTo = 1).endings)
    }

    @Test
    fun hasEnding_answersPerPass() {
        val volta = measure(0, ending = 1, endingTo = 2)
        assertTrue(volta.hasEnding(1))
        assertTrue(volta.hasEnding(2))
        assertFalse(volta.hasEnding(3))
    }

    @Test
    fun endingLabel_writesThePassesWithTrailingDot() {
        assertEquals("1.", measure(0, ending = 1).endingLabel)
        assertEquals("1, 2.", measure(0, ending = 1, endingTo = 2).endingLabel)
        assertEquals("2, 3.", measure(0, ending = 2, endingTo = 3).endingLabel)
        assertNull(measure(0).endingLabel)
    }

    // ── Edición: setEndingRange ─────────────────────────────────────────────────

    @Test
    fun setEndingRange_storesTheRange() {
        val measures = listOf(measure(0), measure(1))
        val edited = TablatureEditing.setEndingRange(measures, 1, from = 1, to = 2)
        assertEquals(1, edited[1].ending)
        assertEquals(2, edited[1].endingTo)
        assertEquals("1, 2.", edited[1].endingLabel)
    }

    @Test
    fun setEndingRange_withoutTo_isASinglePass() {
        val edited = TablatureEditing.setEndingRange(listOf(measure(0)), 0, from = 2)
        assertEquals(2, edited[0].ending)
        assertNull(edited[0].endingTo)
    }

    @Test
    fun setEndingRange_withToNotGreaterThanFrom_isASinglePass() {
        // `1, 1.` no existe: se guarda la casilla simple.
        val edited = TablatureEditing.setEndingRange(listOf(measure(0)), 0, from = 1, to = 1)
        assertEquals(1, edited[0].ending)
        assertNull(edited[0].endingTo)
    }

    @Test
    fun setEndingRange_withNullFrom_removesTheVolta() {
        val edited = TablatureEditing.setEndingRange(listOf(measure(0, ending = 1, endingTo = 2)), 0, from = null)
        assertNull(edited[0].ending)
        assertNull(edited[0].endingTo)
    }

    @Test
    fun setEndingRange_clampsToTheSupportedPasses() {
        val low = TablatureEditing.setEndingRange(listOf(measure(0)), 0, from = 0, to = 0)
        assertEquals(1, low[0].ending)
        assertNull(low[0].endingTo)
        val high = TablatureEditing.setEndingRange(listOf(measure(0)), 0, from = 20, to = 30)
        assertEquals(16, high[0].ending)
        assertNull(high[0].endingTo)
    }

    @Test
    fun setEnding_clearsTheRange() {
        // El botón de una sola pasada deja la casilla simple, aunque antes fuera `1, 2.`.
        val edited = TablatureEditing.setEnding(listOf(measure(0, ending = 1, endingTo = 2)), 0, ending = 2)
        assertEquals(2, edited[0].ending)
        assertNull(edited[0].endingTo)
    }

    // ── Reproducción: la casilla `1, 2.` suena en las dos vueltas ───────────────

    @Test
    fun playback_playsTheMultiPassVoltaOnEveryPass() {
        // |: 1 |: 2 (1, 2.) :| 3 |  → la casilla `1, 2.` suena en la 1.ª y en la 2.ª vuelta.
        val measures = listOf(
            measure(0, ending = 1, endingTo = 2).copy(repeat = TabRepeatMark.START),
            measure(1, ending = 1, endingTo = 2).copy(repeat = TabRepeatMark.END, repeatCount = 2),
            measure(2, ending = 3)
        )
        val order = TabPlayback.playOrder(measures)
        val passesOfVolta = order.filter { it.first == 1 }.map { it.second }
        assertEquals(listOf(1, 2), passesOfVolta)
        // La casilla `3.` no suena: la repetición solo da dos vueltas.
        val played = TabPlayback.timeline(tab(measures)).events.map { it.measureIndex }.distinct()
        assertFalse(played.contains(2))
    }

    @Test
    fun playback_skipsTheVoltaOnPassesOutsideItsRange() {
        val measures = listOf(
            measure(0).copy(repeat = TabRepeatMark.START),
            measure(1, ending = 1).copy(repeat = TabRepeatMark.END, repeatCount = 2),
            measure(2, ending = 2)
        )
        val timeline = TabPlayback.timeline(tab(measures))
        val played = timeline.events.map { it.measureIndex }.distinct()
        // El compás 1 (casilla `1.`) suena; el 2 (casilla `2.`) no, porque la repetición acaba antes.
        assertTrue(played.contains(1))
        assertFalse(played.contains(2))
    }

    // ── Maquetación: corchete, gancho y rótulo único ────────────────────────────

    private fun layoutOf(measures: List<TabMeasure>, maxWidth: Float = 4000f) =
        TablatureSystemsLayout.layout(tab(measures), maxWidth = maxWidth)

    @Test
    fun layout_drawsOneBracketPerVoltaSpan() {
        val systems = layoutOf(
            listOf(measure(0), measure(1, ending = 1, endingTo = 2), measure(2, ending = 1, endingTo = 2), measure(3, ending = 3))
        )
        val drawing = systems.single().drawing
        val labels = drawing.texts.filter { it.kind == TabTextKind.VOLTA }
        // Un rótulo por **casilla**, no uno por compás: `1, 2.` cubre dos compases y se rotula una vez.
        assertEquals(listOf("1, 2.", "3."), labels.map { it.text })
    }

    @Test
    fun layout_bracketLineSitsThreeSpacesAboveTheStaff() {
        val systems = layoutOf(listOf(measure(0), measure(1, ending = 1)))
        val drawing = systems.single().drawing
        val staffSpace = TabRhythmGeometry.STRING_SPACING_SPACES.let { 20f / it }
        val gridTop = drawing.gridTop
        val bracket = drawing.lines.first { it.y1 == it.y2 && !it.isBar && it.y1 < gridTop }
        // `styleDef(voltaPosAbove, PointF(0, -3.0))`: la línea va 3 espacios sobre la primera cuerda.
        assertEquals(3.0f, (gridTop - bracket.y1) / staffSpace, 0.05f)
    }

    @Test
    fun layout_opensWithADownwardHookOfVoltaHook() {
        val systems = layoutOf(listOf(measure(0), measure(1, ending = 1)))
        val drawing = systems.single().drawing
        val staffSpace = 20f / TabRhythmGeometry.STRING_SPACING_SPACES
        val bracket = drawing.lines.first { it.y1 == it.y2 && !it.isBar && it.y1 < drawing.gridTop }
        val hook = drawing.lines.first { it.x1 == it.x2 && it.y1 == bracket.y1 }
        // `styleDef(voltaHook, 2.2_sp)`: el gancho de apertura baja 2,2 espacios.
        assertEquals(2.2f, (hook.y2 - hook.y1) / staffSpace, 0.05f)
        // Y nace en el **borde izquierdo** del primer compás de la casilla.
        assertEquals(drawing.measureBounds[1].left, hook.x1, 0.5f)
    }

    @Test
    fun layout_hasNoClosingHook() {
        // `Volta::propertyDefault(END_HOOK_TYPE) = HookType::NONE`: la casilla cierra sin gancho.
        val systems = layoutOf(listOf(measure(0), measure(1, ending = 1)))
        val drawing = systems.single().drawing
        val bracket = drawing.lines.first { it.y1 == it.y2 && !it.isBar && it.y1 < drawing.gridTop }
        val verticals = drawing.lines.filter { it.x1 == it.x2 && it.y1 == bracket.y1 }
        assertEquals(1, verticals.size)
    }

    @Test
    fun layout_labelStartsRightOfTheHook() {
        val systems = layoutOf(listOf(measure(0), measure(1, ending = 1)))
        val drawing = systems.single().drawing
        val label = drawing.texts.single { it.kind == TabTextKind.VOLTA }
        val hook = drawing.lines.first { it.x1 == it.x2 && it.y1 < drawing.gridTop }
        // `styleDef(voltaOffset, PointF(0.6, 2.2))`: el rótulo nace 0,6 espacios a la derecha.
        assertTrue("el rótulo debe nacer a la derecha del gancho", label.x > hook.x1)
        assertEquals(TabTextAlign.LEFT, label.align)
    }

    @Test
    fun layout_reservesTheVoltaBandOnlyWhenThereIsAVolta() {
        val without = layoutOf(listOf(measure(0), measure(1))).single().drawing
        val with = layoutOf(listOf(measure(0), measure(1, ending = 1))).single().drawing
        // Sin volta no se reserva franja: la primera cuerda queda más arriba.
        assertTrue(with.gridTop > without.gridTop)
    }

    @Test
    fun layout_splitsTheVoltaAcrossSystems() {
        // Con un ancho estrecho la casilla de tres compases se parte: cada trozo se abre con gancho y
        // el rótulo se escribe **solo** en el primero (`continueText` está vacío en MuseScore).
        val measures = listOf(
            measure(0, ending = 1, endingTo = 2),
            measure(1, ending = 1, endingTo = 2),
            measure(2, ending = 1, endingTo = 2)
        )
        val systems = layoutOf(measures, maxWidth = 320f)
        assertTrue("debe haber más de un sistema", systems.size > 1)
        val labels = systems.flatMap { it.drawing.texts.filter { t -> t.kind == TabTextKind.VOLTA } }
        assertEquals(1, labels.size)
        // Cada sistema con parte de la casilla trae su corchete, pero el **gancho** y el rótulo solo
        // van en el primero: `layoutTextLineBaseSegment` los dibuja con `isSingleOrBegin`.
        systems.forEachIndexed { index, system ->
            val bracket = system.drawing.lines.filter { it.y1 == it.y2 && !it.isBar && it.y1 < system.drawing.gridTop }
            assertEquals(1, bracket.size)
            val hooks = system.drawing.lines.filter { it.x1 == it.x2 && it.y1 == bracket.first().y1 }
            assertEquals(if (index == 0) 1 else 0, hooks.size)
        }
    }

    @Test
    fun voltaSpansOf_groupsConsecutiveMeasuresWithTheSameEnding() {
        val measures = listOf(
            measure(0),
            measure(1, ending = 1, endingTo = 2),
            measure(2, ending = 1, endingTo = 2),
            measure(3, ending = 3)
        )
        val spans = voltaSpansOf(measures, listOf(0, 1, 2, 3))
        assertEquals(2, spans.size)
        assertEquals(1, spans[0].start)
        assertEquals(2, spans[0].end)
        assertEquals("1, 2.", spans[0].label)
        assertTrue(spans[0].opens)
        assertTrue(spans[0].closes)
        assertEquals(3, spans[1].start)
        assertEquals("3.", spans[1].label)
    }

    @Test
    fun voltaSpansOf_marksTheContinuationAsNotOpening() {
        // El sistema empieza **dentro** de la casilla: el tramo no abre (no repite rótulo ni gancho).
        val measures = listOf(
            measure(0, ending = 1),
            measure(1, ending = 1),
            measure(2, ending = 1)
        )
        val spans = voltaSpansOf(measures, listOf(1, 2))
        assertEquals(1, spans.size)
        assertFalse(spans[0].opens)
        assertTrue(spans[0].closes)
    }

    // ── D-43: rótulo propio de la casilla (`beginText` del `Volta` de MuseScore) ─────────────────

    @Test
    fun endingLabel_usesTheCustomTextWhenPresent() {
        // El rótulo es **texto libre** en MuseScore (`TextLineBase::beginText`), no algo derivado de
        // las pasadas: «Todos» sobre una casilla de 1ª vez es una casilla de 1ª vez que se rotula
        // «Todos».
        val m = measure(0, ending = 1).copy(endingText = "Todos")
        assertEquals("Todos", m.endingLabel)
        // Las pasadas **no** cambian: el rótulo es presentación, la casilla es semántica.
        assertEquals(listOf(1), m.endings)
        assertTrue(m.hasEnding(1))
        assertFalse(m.hasEnding(2))
    }

    @Test
    fun endingLabel_ignoresABlankCustomText() {
        // Un texto en blanco dejaría el corchete mudo sin que el usuario lo pretendiera.
        assertEquals("1.", measure(0, ending = 1).copy(endingText = "   ").endingLabel)
        assertEquals("1.", measure(0, ending = 1).copy(endingText = "").endingLabel)
    }

    @Test
    fun endingLabel_isNullWithoutAVoltaEvenWithCustomText() {
        // Sin casilla no hay corchete: el texto no se dibuja.
        assertNull(measure(0).copy(endingText = "Todos").endingLabel)
    }

    @Test
    fun setEndingText_storesTheTextOnAVolta() {
        val measures = listOf(measure(0, ending = 1), measure(1))
        val edited = TablatureEditing.setEndingText(measures, 0, "Al final")
        assertEquals("Al final", edited[0].endingText)
        assertEquals("Al final", edited[0].endingLabel)
        // El compás sin casilla no se toca.
        assertNull(edited[1].endingText)
    }

    @Test
    fun setEndingText_isIgnoredWithoutAVolta() {
        // Sin volta no hay corchete donde escribir: el texto se descarta en vez de quedar invisible.
        val measures = listOf(measure(0))
        val edited = TablatureEditing.setEndingText(measures, 0, "Todos")
        assertNull(edited[0].endingText)
        assertNull(edited[0].endingLabel)
    }

    @Test
    fun setEndingText_withNullOrBlank_restoresTheDerivedLabel() {
        val measures = listOf(measure(0, ending = 1).copy(endingText = "Todos"))
        assertNull(TablatureEditing.setEndingText(measures, 0, null)[0].endingText)
        assertNull(TablatureEditing.setEndingText(measures, 0, "  ")[0].endingText)
        assertEquals("1.", TablatureEditing.setEndingText(measures, 0, null)[0].endingLabel)
    }

    @Test
    fun voltaSpansOf_usesTheCustomTextOfAnyMeasureInTheSpan() {
        // El texto vive en el *spanner* de MuseScore, no en el compás: escribirlo en cualquier compás
        // de la casilla tiene que rotular el tramo entero.
        val measures = listOf(
            measure(0),
            measure(1, ending = 1),
            measure(2, ending = 1).copy(endingText = "Todos"),
            measure(3)
        )
        val spans = voltaSpansOf(measures, listOf(0, 1, 2, 3))
        assertEquals(1, spans.size)
        assertEquals("Todos", spans[0].label)
    }

    @Test
    fun layout_drawsTheCustomVoltaText() {
        val measures = listOf(
            measure(0, ending = 1).copy(endingText = "Todos"),
            measure(1)
        )
        val drawing = TablatureLayout.layout(tab(measures))
        val volta = drawing.texts.filter { it.kind == TabTextKind.VOLTA }
        assertEquals(1, volta.size)
        assertEquals("Todos", volta[0].text)
    }
}
