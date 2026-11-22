package com.grafitto.laudate.domain.model.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TempoMarkingTest {

    @Test
    fun fromBpmMapsToCorrectMarking() {
        assertEquals(TempoMarking.LARGO, TempoMarking.fromBpm(50))
        assertEquals(TempoMarking.ADAGIO, TempoMarking.fromBpm(70))
        assertEquals(TempoMarking.ANDANTE, TempoMarking.fromBpm(92))
        assertEquals(TempoMarking.ALLEGRO, TempoMarking.fromBpm(140))
        assertEquals(TempoMarking.PRESTO, TempoMarking.fromBpm(220))
    }

    @Test
    fun fromBpmClampsOutOfRangeValues() {
        // Muy por debajo del mínimo -> la más lenta
        assertEquals(TempoMarking.LARGO, TempoMarking.fromBpm(10))
        // Muy por encima del máximo -> la más rápida
        assertEquals(TempoMarking.PRESTO, TempoMarking.fromBpm(400))
    }

    @Test
    fun fromNameIsCaseInsensitive() {
        assertEquals(TempoMarking.ANDANTE, TempoMarking.fromName("andante"))
        assertEquals(TempoMarking.ALLEGRO, TempoMarking.fromName("Allegro"))
        assertNull(TempoMarking.fromName("inexistente"))
    }

    @Test
    fun suggestedBpmIsWithinRange() {
        TempoMarking.all.forEach { marking ->
            assertEquals((marking.minBpm + marking.maxBpm) / 2, marking.suggestedBpm)
            org.junit.Assert.assertTrue(marking.suggestedBpm in marking.minBpm..marking.maxBpm)
        }
    }

    @Test
    fun allMarkingsAreOrderedBySpeed() {
        val sorted = TempoMarking.all.sortedBy { it.minBpm }
        assertEquals(sorted, TempoMarking.all)
    }
}
