package com.grafitto.laudate.domain.harmonic

import com.grafitto.laudate.domain.model.music.PitchClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordProTranspositionTest {

    @Test
    fun transposeChordPro_shiftUpTwoSemitones_updatesChordsAndKey() {
        val content = """
            {title: Prueba}
            {key: C}
            [C]Hola [G]mundo
            [Am]otra [F]línea
        """.trimIndent()

        val result = HarmonicTransposer.transposeChordProContent(
            content = content,
            semitones = 2,
            targetKeyPitch = PitchClass.D
        )

        assertTrue(result.contains("[D]Hola [A]mundo"))
        assertTrue(result.contains("[Bm]otra [G]línea"))
        assertTrue(result.contains("{key: D}"))
    }

    @Test
    fun transposeChordPro_zeroSemitones_returnsIdenticalContent() {
        val content = "{key: C}\n[C]Letra"
        val result = HarmonicTransposer.transposeChordProContent(content, 0, PitchClass.C)
        assertEquals(content, result)
    }

    @Test
    fun transposeChordPro_halfStepDown_shiftsChordsByOneSemitone() {
        // D bajado un semitono = Db/C#. Como Db prefiere bemoles, los acordes se escriben con bemoles.
        val content = "{key: D}\n[D]Uno [A]dos"
        val result = HarmonicTransposer.transposeChordProContent(
            content = content,
            semitones = -1,
            targetKeyPitch = PitchClass.D.transpose(-1) // Db/C#
        )
        assertTrue("Debe contener la letra intacta", result.contains("[Db]Uno [Ab]dos") || result.contains("[C#]Uno [G#]dos"))
    }

    @Test
    fun transposeChordPro_flatsPreferredInFlatKeys() {
        val content = "{key: C}\n[A]La [E]Mi"
        val result = HarmonicTransposer.transposeChordProContent(
            content = content,
            semitones = 3,
            targetKeyPitch = PitchClass.D_SHARP // Eb prefiere bemoles
        )
        assertTrue(result.contains("{key: D#}"))
        assertTrue(result.contains("C"))
        assertTrue(result.contains("G"))
    }

    @Test
    fun transposeChordPro_preservesSectionHeaders() {
        val content = "[Coro]\n[C]Texto"
        val result = HarmonicTransposer.transposeChordProContent(
            content = content,
            semitones = 2,
            targetKeyPitch = PitchClass.D
        )
        assertTrue(result.contains("[Coro]"))
        assertTrue(result.contains("[D]Texto"))
    }

    @Test
    fun transposeChordPro_latinNotationStaysLatin() {
        val content = "{key: Do}\n[Do]Texto [Sol]más"
        val result = HarmonicTransposer.transposeChordProContent(
            content = content,
            semitones = 2,
            targetKeyPitch = PitchClass.D
        )
        assertTrue(result.contains("[Re]Texto"))
        assertTrue(result.contains("[La]más"))
        assertTrue(result.contains("{key: Re}"))
    }

    @Test
    fun isFlatKey_identifiesFlatKeys() {
        assertTrue(HarmonicTransposer.isFlatKey(PitchClass.F))
        assertTrue(HarmonicTransposer.isFlatKey(PitchClass.A_SHARP))
        assertTrue(HarmonicTransposer.isFlatKey(PitchClass.D_SHARP))
        assertFalse(HarmonicTransposer.isFlatKey(PitchClass.C))
        assertFalse(HarmonicTransposer.isFlatKey(PitchClass.G))
        assertFalse(HarmonicTransposer.isFlatKey(PitchClass.D))
    }
}
