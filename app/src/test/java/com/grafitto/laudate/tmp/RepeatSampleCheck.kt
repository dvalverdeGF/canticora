package com.grafitto.laudate.tmp

import com.grafitto.laudate.domain.parser.ValidateChordProUseCase
import com.grafitto.laudate.domain.serializer.ChordProSerializer
import org.junit.Test

class RepeatSampleCheck {

    private val sample = """
        {start_of_verse}
        [G]Primera frase
        [D]segunda frase
        [Em]tercera frase
        [C]cuarta frase
        {end_of_verse}

        {repeat: 2}
        {start_of_chorus}
        [G]Ven conmigo
        [D]camina en paz
        [Em]abre el corazón
        [C]y vuelve a empezar
        {end_of_chorus}
        {repeat_end}

        {comment: Bis}
        [Em]Abre el corazón
        [C]y vuelve a empezar
    """.trimIndent()

    @Test
    fun report() {
        val result = ValidateChordProUseCase()(sample)
        println("isValid=${result.isValid} errores=${result.errors} avisos=${result.warnings}")
        val song = result.song!!
        song.sections.forEachIndexed { i, s ->
            println("[$i] ${s.sectionType} label='${s.label}' isRepeated=${s.isRepeated} repeatCount=${s.repeatCount} lines=${s.lines.size}")
        }
        println("--- RE-SERIALIZADO ---")
        println(ChordProSerializer.serialize(song))
    }
}
