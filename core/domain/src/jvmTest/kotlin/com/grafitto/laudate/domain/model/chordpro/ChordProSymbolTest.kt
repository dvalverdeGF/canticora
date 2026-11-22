package com.grafitto.laudate.domain.model.chordpro

import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.StrumArticulation
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordProSymbolTest {

    @Test
    fun symbolsResolveByName() {
        assertEquals(ChordProSymbol.ARROW_DOWN, ChordProSymbol.fromName("arrow-down"))
        assertEquals(ChordProSymbol.SHARP, ChordProSymbol.fromName("sharp"))
        assertEquals(ChordProSymbol.REPEAT_END, ChordProSymbol.fromName("repeat-end"))
        assertNull(ChordProSymbol.fromName("does-not-exist"))
    }

    @Test
    fun renderInline_replacesMarkupWithGlyphs() {
        val input = "Rasgueo: <sym arrow-down/> y <sym arrow-up/>"
        val rendered = ChordProSymbol.renderInline(input)
        assertEquals("Rasgueo: ↓ y ↑", rendered)
    }

    @Test
    fun renderInline_keepsUnknownMarkup() {
        val input = "<sym invented-symbol/>"
        assertEquals(input, ChordProSymbol.renderInline(input))
    }

    @Test
    fun renderInline_handlesCircles() {
        assertEquals("Ⓐ", ChordProSymbol.renderInline("<sym circle-A/>"))
        assertEquals("⑦", ChordProSymbol.renderInline("<sym circle-7/>"))
    }

    @Test
    fun circleSymbolsCoverDigitsAndLetters() {
        assertEquals(10 + 26, ChordProSymbol.circles.size)
        assertNotNull(ChordProSymbol.circleFromName("circle-Z"))
        assertNotNull(ChordProSymbol.circleFromName("circle-0"))
    }

    @Test
    fun markupOf_buildsCanonicalMarkup() {
        assertEquals("<sym arrow-down/>", ChordProSymbol.markupOf("arrow-down"))
    }

    @Test
    fun everyCategoryHasSymbols() {
        val palette = ChordProSymbol.palette()
        ChordProSymbolCategory.entries.forEach { category ->
            assertTrue("La categoría $category debería tener símbolos", palette[category]?.isNotEmpty() == true)
        }
    }

    @Test
    fun rhythmEvent_mapsToChordProSymbolWithArticulation() {
        val accentDown = RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN, StrumArticulation.ACCENT)
        assertEquals("arrow-down-with-accent", accentDown.symbolName)
        assertEquals(ChordProSymbol.ARROW_DOWN_ACCENT.glyph, accentDown.symbolGlyph)

        val arpeggioUp = RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.UP, StrumArticulation.ARPEGGIO)
        assertEquals("arrow-up-with-arpeggio", arpeggioUp.symbolName)

        val plainDown = RhythmEvent(RhythmFigure.QUARTER, StrumDirection.DOWN)
        assertEquals("arrow-down", plainDown.symbolName)

        val mute = RhythmEvent(RhythmFigure.EIGHTH, StrumDirection.MUTE)
        assertEquals("arrow-mute", mute.symbolName)

        val rest = RhythmEvent(RhythmFigure.QUARTER_REST, StrumDirection.DOWN)
        assertEquals("bar", rest.symbolName)
    }

    @Test
    fun strumArticulation_togglesAccentPreservingOtherArticulation() {
        assertEquals(StrumArticulation.ACCENT, StrumArticulation.NONE.toggleAccent())
        assertEquals(StrumArticulation.NONE, StrumArticulation.ACCENT.toggleAccent())
        assertEquals(StrumArticulation.ACCENT_ARPEGGIO, StrumArticulation.ARPEGGIO.toggleAccent())
        assertEquals(StrumArticulation.ARPEGGIO, StrumArticulation.ACCENT_ARPEGGIO.toggleAccent())
    }

    @Test
    fun strumArticulation_cyclesThroughAll() {
        val start = StrumArticulation.NONE
        var current = start
        repeat(StrumArticulation.all.size) { current = StrumArticulation.next(current) }
        assertEquals(start, current)
    }
}
