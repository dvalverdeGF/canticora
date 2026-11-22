package com.grafitto.laudate.ui.chordpro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolTextLayoutParserTest {

    @Test
    fun plainTextHasNoVectorialSymbols() {
        val layout = SymbolTextLayoutParser.layout("Cuando comienza el día")
        assertEquals("Cuando comienza el día", layout.renderedText)
        assertTrue(layout.vectorialNames.isEmpty())
    }

    @Test
    fun strumArrowBecomesMarker() {
        val layout = SymbolTextLayoutParser.layout("<sym arrow-down/>")
        assertEquals(SYMBOL_MARKER.toString(), layout.renderedText)
        assertEquals(listOf("arrow-down"), layout.vectorialNames)
    }

    @Test
    fun preservesOrderOfMultipleVectorialSymbols() {
        val layout = SymbolTextLayoutParser.layout("<sym arrow-down/> y <sym arrow-up-with-accent/>")
        assertEquals(listOf("arrow-down", "arrow-up-with-accent"), layout.vectorialNames)
        // Texto + separador entre los dos marcadores.
        assertEquals(2, layout.renderedText.count { it == SYMBOL_MARKER })
        assertTrue(layout.renderedText.contains(" y "))
    }

    @Test
    fun nonVectorialSymbolsBecomeGlyphs() {
        val layout = SymbolTextLayoutParser.layout("Inicio <sym repeat-start/> fin")
        assertEquals("Inicio |: fin", layout.renderedText)
        assertTrue(layout.vectorialNames.isEmpty())
    }

    @Test
    fun circlesBecomeGlyphs() {
        val layout = SymbolTextLayoutParser.layout("<sym circle-A/> y <sym circle-3/>")
        assertEquals("Ⓐ y ③", layout.renderedText)
        assertTrue(layout.vectorialNames.isEmpty())
    }

    @Test
    fun unknownMarkupIsPreserved() {
        val layout = SymbolTextLayoutParser.layout("<sym invented/>")
        assertEquals("<sym invented/>", layout.renderedText)
        assertTrue(layout.vectorialNames.isEmpty())
    }

    @Test
    fun mixedContentSplitsCorrectly() {
        val layout = SymbolTextLayoutParser.layout("Rasgueo <sym arrow-down-with-accent/> luego <sym sharp/>")
        assertEquals(listOf("arrow-down-with-accent"), layout.vectorialNames)
        assertEquals("Rasgueo \uFFFC luego ♯", layout.renderedText)
    }
}
