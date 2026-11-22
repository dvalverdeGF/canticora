package com.grafitto.laudate.domain.harmonic

import com.grafitto.laudate.domain.model.music.Accidental
import com.grafitto.laudate.domain.model.music.DomainChord
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.music.PitchClass
import com.grafitto.laudate.domain.parser.ChordProParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HarmonicTransposerTest {

    @Test
    fun transpose_12Semitones_returnsIdenticalPitch() {
        val original = DomainChord(rootNote = PitchClass.C, quality = "m7")
        val transposedPlus12 = HarmonicTransposer.transposeChord(original, 12, PitchClass.C)
        val transposedMinus12 = HarmonicTransposer.transposeChord(original, -12, PitchClass.C)

        assertEquals(original.rootNote, transposedPlus12.rootNote)
        assertEquals(original.quality, transposedPlus12.quality)
        assertEquals(original.rootNote, transposedMinus12.rootNote)
        assertEquals(original.quality, transposedMinus12.quality)
    }

    @Test
    fun transpose_enharmonicSelection_followsCircleOfFifths() {
        // C transpuesta a Eb (+3 semitonos). En Eb se prefieren bemoles: A# pasa a ser Bb
        val chordA = DomainChord(rootNote = PitchClass.A)
        val transposedInEb = HarmonicTransposer.transposeChord(chordA, 1, PitchClass.D_SHARP) // A -> Bb (+1) en tonalidad Eb
        assertEquals(PitchClass.A_SHARP, transposedInEb.rootNote)
        assertEquals(Accidental.FLAT, transposedInEb.rootAccidental)
        assertEquals("Bb", transposedInEb.toFormattedString(NotationSystem.ENGLISH))
        assertEquals("Sib", transposedInEb.toFormattedString(NotationSystem.LATIN))

        // C transpuesta a E (+4 semitonos). En E se prefieren sostenidos: F -> F#
        val chordF = DomainChord(rootNote = PitchClass.F)
        val transposedInE = HarmonicTransposer.transposeChord(chordF, 1, PitchClass.E) // F -> F# (+1) en tonalidad E
        assertEquals(PitchClass.F_SHARP, transposedInE.rootNote)
        assertEquals(Accidental.SHARP, transposedInE.rootAccidental)
        assertEquals("F#", transposedInE.toFormattedString(NotationSystem.ENGLISH))
        assertEquals("Fa#", transposedInE.toFormattedString(NotationSystem.LATIN))
    }

    @Test
    fun transpose_complexSlashChords_maintainsBothPitches() {
        // C/B transpuesta +2 semitonos a D
        val slashChord = DomainChord(
            rootNote = PitchClass.C,
            bassNote = PitchClass.B,
            rawChordText = "C/B"
        )
        val transposed = HarmonicTransposer.transposeChord(slashChord, 2, PitchClass.D)

        assertEquals(PitchClass.D, transposed.rootNote)
        assertEquals(PitchClass.C_SHARP, transposed.bassNote)
        assertEquals("D/C#", transposed.toFormattedString(NotationSystem.ENGLISH))
        assertEquals("Re/Do#", transposed.toFormattedString(NotationSystem.LATIN))
    }

    @Test
    fun calculateVisualCapoChord_and_capoPosition() {
        // Canción en C para cantar en E (+4 semitonos).
        // Capo en traste 4 -> digitación visual de C
        val targetKey = PitchClass.E
        val visualKey = PitchClass.C
        val capoPos = HarmonicTransposer.calculateCapoPosition(targetKey, visualKey)
        assertEquals(4, capoPos)

        val chordInE = DomainChord(rootNote = PitchClass.E)
        val visualChord = HarmonicTransposer.calculateVisualCapoChord(chordInE, targetKey, capoPos)
        assertEquals(PitchClass.C, visualChord.rootNote)
        assertEquals("C", visualChord.toFormattedString(NotationSystem.ENGLISH))
    }

    @Test
    fun notationSystem_convertsToNnsDegrees() {
        // En tonalidad de Do:
        // Do = 1, Re = 2, Mi = 3, Fa = 4, Sol = 5, Lam = 6m, Si = 7
        val doChord = DomainChord(rootNote = PitchClass.C)
        val lamChord = DomainChord(rootNote = PitchClass.A, quality = "m")
        val solChord = DomainChord(rootNote = PitchClass.G)

        assertEquals("1", doChord.toFormattedString(NotationSystem.NNS, PitchClass.C))
        assertEquals("6m", lamChord.toFormattedString(NotationSystem.NNS, PitchClass.C))
        assertEquals("5", solChord.toFormattedString(NotationSystem.NNS, PitchClass.C))
    }

    @Test
    fun transposeSong_preservesLyricsAndStructure100Percent() {
        val chordPro = """
            {title: Pescador de Hombres}
            [C]Tú has venido a la [G]orilla,
            [Am]no has buscado ni a sabios ni a [F]ricos.
        """.trimIndent()

        val originalSong = ChordProParser.parse(chordPro)
        val originalLyricsLine0 = originalSong.sections[0].lines[0].lyrics
        val originalCharIdx0 = originalSong.sections[0].lines[0].chords[0].characterIndex

        val transposedSong = HarmonicTransposer.transposeSong(originalSong, 2, PitchClass.D)

        assertEquals("D", transposedSong.originalKey)
        // Letras idénticas
        assertEquals(originalLyricsLine0, transposedSong.sections[0].lines[0].lyrics)
        // Posiciones idénticas
        assertEquals(originalCharIdx0, transposedSong.sections[0].lines[0].chords[0].characterIndex)
        // Acorde transformado C -> D
        assertEquals(PitchClass.D, transposedSong.sections[0].lines[0].chords[0].chord.rootNote)
        // Acorde transformado G -> A (segundo acorde de la línea)
        assertEquals(PitchClass.A, transposedSong.sections[0].lines[0].chords[1].chord.rootNote)
    }
}
