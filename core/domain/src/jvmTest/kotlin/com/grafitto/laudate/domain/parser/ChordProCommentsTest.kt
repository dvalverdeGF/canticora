package com.grafitto.laudate.domain.parser

import com.grafitto.laudate.domain.model.song.LineType
import com.grafitto.laudate.domain.serializer.ChordProSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordProCommentsTest {

    @Test
    fun commentInsideSection_becomesRenderableLine() {
        val song = ChordProParser.parse(
            """
            {title: Camina}
            {sov}
            [G]Cuando comienza el día
            {comment: Entra la guitarra sola}
            {eov}
            """.trimIndent()
        )

        val section = song.sections.single()
        assertEquals(2, section.lines.size)
        val commentLine = section.lines[1]
        assertEquals(LineType.COMMENT, commentLine.type)
        assertEquals("Entra la guitarra sola", commentLine.comment)
        assertEquals(null, commentLine.voice)
    }

    @Test
    fun voicedComment_keepsVoiceAndText() {
        val song = ChordProParser.parse(
            """
            {title: Polifonía}
            {sov}
            {comment-todos: todos fuerte}
            [C]Cantamos
            {eov}
            """.trimIndent()
        )

        val commentLine = song.sections.single().lines.first()
        assertEquals(LineType.COMMENT, commentLine.type)
        assertEquals("todos fuerte", commentLine.comment)
        assertEquals("todos", commentLine.voice)
    }

    @Test
    fun commentRoundTripsThroughSerializer() {
        val original = """
            {title: Camina}
            {key: G}
            {sov}
            {comment-todos: todos fuerte}
            [G]Cuando comienza el día
            {eov}
        """.trimIndent()

        val song = ChordProParser.parse(original)
        val serialized = ChordProSerializer.serialize(song)
        assertTrue(serialized.contains("{comment-todos: todos fuerte}"))

        val reparsed = ChordProParser.parse(serialized)
        val commentLine = reparsed.sections.flatMap { it.lines }.first { it.type == LineType.COMMENT }
        assertEquals("todos fuerte", commentLine.comment)
        assertEquals("todos", commentLine.voice)
    }

    @Test
    fun headerComment_beforeAnySection_isSongNote() {
        // Antes de la primera sección, un comentario es nota del canto, no línea de sección.
        val song = ChordProParser.parse(
            """
            {title: Camina}
            {comment: Tocar suave con arpegio}
            {sov}
            [C]Letra
            {eov}
            """.trimIndent()
        )

        assertEquals("Tocar suave con arpegio", song.notes)
        assertTrue(song.sections.single().lines.none { it.type == LineType.COMMENT })
    }
}
