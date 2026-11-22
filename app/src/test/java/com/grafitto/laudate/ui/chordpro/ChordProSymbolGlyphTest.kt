package com.grafitto.laudate.ui.chordpro

import com.grafitto.laudate.domain.model.chordpro.ChordProSymbol
import com.grafitto.laudate.domain.model.chordpro.ChordProSymbolCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordProSymbolGlyphTest {

    @Test
    fun strumArrowsAreVectorial() {
        listOf(ChordProSymbol.ARROW_DOWN, ChordProSymbol.ARROW_UP, ChordProSymbol.ARROW_MUTE)
            .forEach { assertTrue("${it.symbolName} debería ser vectorial", it.isVectorial()) }
    }

    @Test
    fun nonStrumSymbolsAreNotVectorial() {
        listOf(
            ChordProSymbol.BAR,
            ChordProSymbol.REPEAT_END,
            ChordProSymbol.SHARP,
            ChordProSymbol.FLAT
        ).forEach { assertFalse("${it.symbolName} no debería ser vectorial", it.isVectorial()) }
    }

    @Test
    fun everyStrumCategorySymbolIsVectorial() {
        val strumCategories = listOf(
            ChordProSymbolCategory.STRUM_DOWN,
            ChordProSymbolCategory.STRUM_UP
        )
        ChordProSymbol.palette()
            .filterKeys { it in strumCategories }
            .values.flatten()
            .forEach { assertTrue(it.symbolName, it.isVectorial()) }
    }
}
