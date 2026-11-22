package com.grafitto.laudate.domain.serializer

import com.grafitto.laudate.domain.model.song.SectionType
import com.grafitto.laudate.domain.parser.ChordProParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordProSerializerTest {

    @Test
    fun serialize_emitsHeaderDirectives() {
        val song = ChordProParser.parse(
            """
            {title: Juntos como hermanos}
            {key: G}
            {number: 402}
            {artist: Autor Anónimo}
            {time: 4/4}
            {tempo: 92 Andante}
            {capo: 2}
            {comment: Entra el coro al final}
            {soc}
            [G]Juntos como her[C]manos
            {eoc}
            """.trimIndent()
        )

        val output = ChordProSerializer.serialize(song)

        assertTrue(output.contains("{title: Juntos como hermanos}"))
        assertTrue(output.contains("{key: G}"))
        assertTrue(output.contains("{number: 402}"))
        assertTrue(output.contains("{artist: Autor Anónimo}"))
        assertTrue(output.contains("{time: 4/4}"))
        assertTrue(output.contains("{tempo: 92 Andante}"))
        assertTrue(output.contains("{capo: 2}"))
        assertTrue(output.contains("{comment: Entra el coro al final}"))
    }

    @Test
    fun serialize_emitsSectionVoicesAndLabels() {
        val song = ChordProParser.parse(
            """
            {title: Polifonía}
            {key: C}
            {start_of_verse-soprano}
            [C]Canta soprano
            {end_of_verse}
            {start_of_chorus label="Estribillo"}
            [F]Todos juntos
            {end_of_chorus}
            """.trimIndent()
        )

        val output = ChordProSerializer.serialize(song)

        assertTrue(output.contains("{start_of_verse-soprano}"))
        assertTrue(output.contains("{start_of_chorus Estribillo}"))
    }

    @Test
    fun roundTrip_preservesChordsAndStructure() {
        val original = """
            {title: Ronda}
            {key: D}
            {soc}
            [D]Cantad al Se[A]ñor
            {eoc}
            {sov}
            [G]Con alegrí[D]a
            {eov}
        """.trimIndent()

        val song = ChordProParser.parse(original)
        val serialized = ChordProSerializer.serialize(song)
        val reparsed = ChordProParser.parse(serialized)

        assertEquals(song.title, reparsed.title)
        assertEquals(song.originalKey, reparsed.originalKey)
        assertEquals(song.sections.size, reparsed.sections.size)
        assertEquals(
            song.sections.map { it.sectionType },
            reparsed.sections.map { it.sectionType }
        )
        assertEquals(
            song.sections.flatMap { s -> s.lines.map { it.lyrics } },
            reparsed.sections.flatMap { s -> s.lines.map { it.lyrics } }
        )
        assertEquals(
            song.sections.flatMap { s -> s.lines.flatMap { line -> line.chords.map { it.rawChordText } } },
            reparsed.sections.flatMap { s -> s.lines.flatMap { line -> line.chords.map { it.rawChordText } } }
        )
    }

    @Test
    fun serialize_marksRepeatedSections() {
        val song = ChordProParser.parse(
            """
            {title: Bises}
            {key: C}
            {repeat}
            {sov}
            [C]Se repite
            {eov}
            """.trimIndent()
        )

        val output = ChordProSerializer.serialize(song)

        assertTrue(output.contains("{repeat}"))
        assertTrue(output.contains("{repeat_end}"))
        assertTrue(song.sections.any { it.isRepeated })
    }

    @Test
    fun serialize_withRepeatCount_emitsRepeatCount() {
        val song = ChordProParser.parse(
            """
            {title: Bises con conteo}
            {key: C}
            {repeat: 2}
            {soc}
            [C]Ven conmigo
            {eoc}
            {repeat_end}
            """.trimIndent()
        )

        val section = song.sections.single()
        assertTrue(section.isRepeated)
        assertEquals(2, section.repeatCount)

        val output = ChordProSerializer.serialize(song)
        assertTrue(output.contains("{repeat: 2}"))
        assertTrue(output.contains("{repeat_end}"))
    }
}
