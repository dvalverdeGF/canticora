package com.grafitto.laudate.ui.rhythm

import com.grafitto.laudate.domain.model.rhythm.StrumArticulation
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class StrumSymbolSpecTest {

    @Test
    fun parsesDirections() {
        assertEquals(StrumDirection.DOWN, StrumSymbolSpec.parse("arrow-down").direction)
        assertEquals(StrumDirection.UP, StrumSymbolSpec.parse("arrow-up").direction)
        assertEquals(StrumDirection.MUTE, StrumSymbolSpec.parse("arrow-mute").direction)
        assertEquals(StrumDirection.MUTE, StrumSymbolSpec.parse("arrow-down-muted").direction)
        assertEquals(StrumDirection.NONE, StrumSymbolSpec.parse("bar").direction)
    }

    @Test
    fun parsesArticulations() {
        assertEquals(StrumArticulation.NONE, StrumSymbolSpec.parse("arrow-down").articulation)
        assertEquals(StrumArticulation.ACCENT, StrumSymbolSpec.parse("arrow-down-with-accent").articulation)
        assertEquals(StrumArticulation.ARPEGGIO, StrumSymbolSpec.parse("arrow-up-with-arpeggio").articulation)
        assertEquals(StrumArticulation.STACCATO, StrumSymbolSpec.parse("arrow-down-with-staccato").articulation)
        assertEquals(
            StrumArticulation.ACCENT_ARPEGGIO,
            StrumSymbolSpec.parse("arrow-down-with-accent-and-arpeggio").articulation
        )
        assertEquals(
            StrumArticulation.ACCENT_STACCATO,
            StrumSymbolSpec.parse("arrow-up-with-accent-and-staccato").articulation
        )
    }

    @Test
    fun roundTripsEveryEventSymbolName() {
        // Todo símbolo generado por el dominio debe poder interpretarse de vuelta para dibujarse.
        StrumDirection.strikable.forEach { direction ->
            StrumArticulation.all.forEach { articulation ->
                val name = articulation.symbolNameFor(direction)
                val spec = StrumSymbolSpec.parse(name)
                assertEquals(direction, spec.direction)
                assertEquals(articulation, spec.articulation)
            }
        }
    }
}
