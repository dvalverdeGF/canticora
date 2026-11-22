package com.grafitto.laudate.domain.parser

import com.grafitto.laudate.domain.model.music.Accidental
import com.grafitto.laudate.domain.model.music.PitchClass
import com.grafitto.laudate.domain.model.song.SectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordProParserTest {

    @Test
    fun parseSingleChords_latinAndEnglish() {
        val cChord = ChordTextParser.parse("C").getOrThrow()
        assertEquals(PitchClass.C, cChord.rootNote)
        assertEquals(Accidental.NATURAL, cChord.rootAccidental)
        assertEquals("", cChord.quality)

        val am7 = ChordTextParser.parse("Am7").getOrThrow()
        assertEquals(PitchClass.A, am7.rootNote)
        assertEquals("m7", am7.quality)

        val solSus4 = ChordTextParser.parse("Sol#sus4").getOrThrow()
        assertEquals(PitchClass.G_SHARP, solSus4.rootNote)
        assertEquals(Accidental.SHARP, solSus4.rootAccidental)
        assertEquals("sus4", solSus4.quality)

        val slashChord = ChordTextParser.parse("Do/Mi").getOrThrow()
        assertEquals(PitchClass.C, slashChord.rootNote)
        assertEquals(PitchClass.E, slashChord.bassNote)

        val englishSlash = ChordTextParser.parse("C/B").getOrThrow()
        assertEquals(PitchClass.C, englishSlash.rootNote)
        assertEquals(PitchClass.B, englishSlash.bassNote)
    }

    @Test
    fun parseLineWithChords_extractsCleanLyricsAndExactIndices() {
        val raw = "[C]Juntos como [G]hermanos, [Am]miembros de una [F]Iglesia"
        val (lyrics, anchors) = ChordProParser.parseLineWithChords(raw, "sec-1", 0)

        assertEquals("Juntos como hermanos, miembros de una Iglesia", lyrics)
        assertEquals(4, anchors.size)

        assertEquals(0, anchors[0].characterIndex)
        assertEquals(PitchClass.C, anchors[0].chord.rootNote)

        assertEquals(12, anchors[1].characterIndex)
        assertEquals(PitchClass.G, anchors[1].chord.rootNote)

        assertEquals(22, anchors[2].characterIndex)
        assertEquals(PitchClass.A, anchors[2].chord.rootNote)
        assertEquals("m", anchors[2].chord.quality)

        assertEquals(38, anchors[3].characterIndex)
        assertEquals(PitchClass.F, anchors[3].chord.rootNote)
    }

    @Test
    fun parseFullChordProContent_generatesCompleteAst() {
        val chordPro = """
            {title: Juntos como Hermanos}
            {author: Cesáreo Gabaráin}
            {key: C}
            {time: 4/4}

            {soc}
            [C]Juntos como [G]hermanos,
            [Am]miembros de una [F]Iglesia,
            {eoc}

            [Verse 1]
            [C]Vamos cami[G]nando
            [F]al encuentro del [G]Señor.
        """.trimIndent()

        val song = ChordProParser.parse(chordPro)

        assertEquals("Juntos como Hermanos", song.title)
        assertEquals("Cesáreo Gabaráin", song.author)
        assertEquals("C", song.originalKey)
        assertEquals("4/4", song.rhythm)
        assertEquals(2, song.sections.size)

        val chorus = song.sections[0]
        assertEquals(SectionType.CHORUS, chorus.sectionType)
        assertEquals(2, chorus.lines.size)
        assertEquals("Juntos como hermanos,", chorus.lines[0].lyrics)
        assertEquals(2, chorus.lines[0].chords.size)

        val verse = song.sections[1]
        assertEquals(SectionType.VERSE, verse.sectionType)
        assertEquals(2, verse.lines.size)
    }

    @Test
    fun parseSectionLabelWithHyphen_doesNotTreatItAsVoice() {
        // Un guion dentro de una etiqueta libre no debe interpretarse como selector de voz.
        val chordPro = """
            {title: Camina}
            {start_of_verse Pre-estribillo}
            [C]Y si la noche llega
            {end_of_verse}
        """.trimIndent()

        val song = ChordProParser.parse(chordPro)
        val section = song.sections.single()
        assertEquals(null, section.voice)
        assertEquals("Pre-estribillo", section.label)
        assertEquals("Pre-estribillo", section.headerTitle)
    }

    @Test
    fun parseSectionVoice_stillWorksAfterLabelFix() {
        val chordPro = """
            {title: Polifonía}
            {start_of_verse-soprano}
            [C]Canta soprano
            {end_of_verse}
        """.trimIndent()

        val song = ChordProParser.parse(chordPro)
        val section = song.sections.single()
        assertEquals("soprano", section.voice)
    }

    @Test
    fun parseChordPro_withNumber_parsesNumber() {
        val chordPro = """
            {title: Aleluya}
            {number: 402}
            {key: G}
            {sov}
            [G]Aleluya, alelu[C]ya
            {eov}
        """.trimIndent()

        val song = ChordProParser.parse(chordPro)
        assertEquals("402", song.number)
    }

    @Test
    fun parseChordPro_withCapoAndNotes_parsesCorrectly() {
        val chordPro = """
            {title: Pescador de Hombres}
            {key: D}
            {capo: 2}
            {comment: Tocar suave con arpegio}
            {time: 3/4}

            {soc}
            [D]Señor, [A]me has mirado a los ojos
            {eoc}
        """.trimIndent()

        val song = ChordProParser.parse(chordPro)
        assertEquals("Pescador de Hombres", song.title)
        assertEquals("D", song.originalKey)
        assertEquals(2, song.capo)
        assertEquals("Tocar suave con arpegio", song.notes)
        assertEquals("3/4", song.rhythm)
        assertEquals("Señor, me has mirado a los ojos", song.sections[0].lines[0].lyrics)
    }

    @Test
    fun parseChordOnlyLine_marksItAsChordOnlyAndKeepsBracketsIntact() {
        // Una intro de acordes puros no debe romper los corchetes ni dejar letra fantasma.
        val chordPro = """
            {title: Princesa}
            {key: C}
            {start_of_intro}
            [Am] [G] [F] [E]
            {end_of_intro}
            {start_of_verse}
            [C]Eres la [G]princesa
            {end_of_verse}
        """.trimIndent()

        val song = ChordProParser.parse(chordPro)
        val intro = song.sections.first { it.sectionType == SectionType.INTRO }
        val chordOnlyLine = intro.lines.single()

        // La "letra" de una fila solo-acordes son los espacios entre corchetes (vacíos en esencia).
        assertTrue(chordOnlyLine.lyrics.isBlank())
        assertEquals(
            listOf("Am", "G", "F", "E"),
            chordOnlyLine.chords.map { it.rawChordText }
        )
        assertTrue(chordOnlyLine.chords.all { !it.rawChordText.startsWith(" ") })
        assertEquals(com.grafitto.laudate.domain.model.song.LineType.CHORD_ONLY, chordOnlyLine.type)

        // La letra con acordes sigue siendo LYRICS.
        val verse = song.sections.first { it.sectionType == SectionType.VERSE }
        assertEquals(com.grafitto.laudate.domain.model.song.LineType.LYRICS, verse.lines.single().type)
    }

    @Test
    fun parseXRhythmDirective_assignsPatternNameToSection() {
        val chordPro = """
            {title: Con cambio de ritmo}
            {key: G}
            {start_of_verse}
            {x_rhythm: Rock rítmico}
            [G]Verso con [D]rock
            {end_of_verse}
            {start_of_chorus}
            {x_rhythm: Semicorcheas funk}
            [C]Coro con [G]funk
            {end_of_chorus}
        """.trimIndent()

        val song = ChordProParser.parse(chordPro)
        assertEquals("Rock rítmico", song.sections[0].rhythmPatternName)
        assertEquals("Semicorcheas funk", song.sections[1].rhythmPatternName)
    }

    @Test
    fun parseXRhythmDirective_survivesRoundTrip() {
        val chordPro = """
            {title: Con cambio de ritmo}
            {key: G}
            {start_of_verse}
            {x_rhythm: Rock}
            [G]Verso
            {end_of_verse}
        """.trimIndent()

        val song = ChordProParser.parse(chordPro)
        val serialized = com.grafitto.laudate.domain.serializer.ChordProSerializer.serialize(song)
        assertTrue(serialized.contains("{x_rhythm: Rock}"))

        val reparsed = ChordProParser.parse(serialized)
        assertEquals("Rock", reparsed.sections.single().rhythmPatternName)
    }
}
