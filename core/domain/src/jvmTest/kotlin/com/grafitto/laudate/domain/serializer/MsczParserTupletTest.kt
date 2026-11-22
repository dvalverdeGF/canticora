package com.grafitto.laudate.domain.serializer

import com.grafitto.laudate.domain.model.tablature.TabTupletBracketType
import com.grafitto.laudate.domain.model.tablature.TabTupletNumberType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **D-41 · `TupletNumberType` y `TupletBracketType` del `.mscx`.**
 *
 * MuseScore guarda los dos ajustes del grupo irregular como **enteros** dentro del `<Tuplet>`
 * (`tuplet.h:34` y `tuplet.h:60`, serializados por `TWrite::write(const Tuplet*)`):
 *
 * ```xml
 * <Tuplet>
 *   <normalNotes>2</normalNotes>
 *   <actualNotes>3</actualNotes>
 *   <numberType>1</numberType>   <!-- 0 número · 1 proporción · 2 sin texto -->
 *   <bracketType>2</bracketType> <!-- 0 automático · 1 con corchete · 2 sin corchete -->
 * </Tuplet>
 * ```
 *
 * Antes se ignoraban los dos y todo grupo se leía como «número + corchete automático», así que un
 * `3:2` rotulado como proporción o un grupo sin corchete se perdían al importar.
 */
class MsczParserTupletTest {

    private fun mscx(tupletExtras: String): String = """
        <Score>
          <Staff id="1">
            <Measure>
              <voice>
                <Tuplet>
                  <normalNotes>2</normalNotes>
                  <actualNotes>3</actualNotes>
                  $tupletExtras
                </Tuplet>
                <Chord><durationType>eighth</durationType><Note><fret>0</fret><string>0</string></Note></Chord>
                <Chord><durationType>eighth</durationType><Note><fret>2</fret><string>1</string></Note></Chord>
                <Chord><durationType>eighth</durationType><Note><fret>3</fret><string>2</string></Note></Chord>
                <endTuplet/>
              </voice>
            </Measure>
          </Staff>
        </Score>
    """.trimIndent()

    @Test
    fun sinAjustesSeLeeComoNumeroYCorcheteAutomatico() {
        val tab = MsczParser.parse(mscx(""), songId = "t")
        val tuplet = tab.measures[0].slots[0].tuplet
        assertEquals(TabTupletNumberType.SHOW_NUMBER, tuplet?.numberType)
        assertEquals(TabTupletBracketType.AUTO_BRACKET, tuplet?.bracketType)
        assertEquals("3", tuplet?.label)
    }

    @Test
    fun numberTypeUnoSeLeeComoProporcion() {
        val tab = MsczParser.parse(mscx("<numberType>1</numberType>"), songId = "t")
        val tuplet = tab.measures[0].slots[0].tuplet
        assertEquals(TabTupletNumberType.SHOW_RELATION, tuplet?.numberType)
        assertEquals("3:2", tuplet?.label)
    }

    @Test
    fun numberTypeDosSeLeeComoSinTexto() {
        val tab = MsczParser.parse(mscx("<numberType>2</numberType>"), songId = "t")
        val tuplet = tab.measures[0].slots[0].tuplet
        assertEquals(TabTupletNumberType.NO_TEXT, tuplet?.numberType)
        assertEquals("", tuplet?.label)
    }

    @Test
    fun bracketTypeUnoSeLeeComoConCorchete() {
        val tab = MsczParser.parse(mscx("<bracketType>1</bracketType>"), songId = "t")
        assertEquals(
            TabTupletBracketType.SHOW_BRACKET,
            tab.measures[0].slots[0].tuplet?.bracketType
        )
    }

    @Test
    fun bracketTypeDosSeLeeComoSinCorchete() {
        val tab = MsczParser.parse(mscx("<bracketType>2</bracketType>"), songId = "t")
        assertEquals(
            TabTupletBracketType.SHOW_NO_BRACKET,
            tab.measures[0].slots[0].tuplet?.bracketType
        )
    }

    @Test
    fun losDosAjustesLleganATodasLasColumnasDelGrupo() {
        val tab = MsczParser.parse(
            mscx("<numberType>1</numberType><bracketType>2</bracketType>"),
            songId = "t"
        )
        val slots = tab.measures[0].slots
        assertEquals(3, slots.size)
        slots.forEach { slot ->
            assertEquals(TabTupletNumberType.SHOW_RELATION, slot.tuplet?.numberType)
            assertEquals(TabTupletBracketType.SHOW_NO_BRACKET, slot.tuplet?.bracketType)
        }
    }
}
