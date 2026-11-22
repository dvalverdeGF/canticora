package com.grafitto.laudate.domain.harmonic

import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.music.PitchClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresiones del motor de transposición sobre textos ChordPro reales.
 *
 * El cancionero usa `[Estrofa 1]`, `[Coro]`… como rótulos de sección y acordes como
 * `lam`, `fam#`, `SOL/SI` o `SIB`. Antes, un rótulo que empezaba por A-G (`[Estrofa]`)
 * se colaba como acorde y al transponer se corrompía a `[A#strofa]`.
 */
class ChordProTranspositionRegressionTest {

    @Test
    fun `las etiquetas de seccion no se transponen`() {
        val content = "[Estrofa 1]\n[C]Hola\n[Coro]\n[G]Aleluya"
        val result = HarmonicTransposer.transposeChordProContent(
            content = content,
            semitones = 2,
            targetKeyPitch = PitchClass.D
        )
        assertTrue("La etiqueta de sección debe quedar intacta", result.contains("[Estrofa 1]"))
        assertTrue(result.contains("[Coro]"))
        assertFalse("No debe aparecer una etiqueta corrompida", result.contains("[A#strofa]"))
        assertFalse(result.contains("[E#strofa]"))
        assertTrue(result.contains("[D]Hola"))
        assertTrue(result.contains("[A]Aleluya"))
    }

    @Test
    fun `una etiqueta sin numero como Estrofa tampoco se corrompe`() {
        val content = "[Estrofa]\n[C]Texto"
        val result = HarmonicTransposer.transposeChordProContent(content, 3, PitchClass.D_SHARP)
        assertTrue(result.contains("[Estrofa]"))
        assertFalse(result.contains("#strofa"))
    }

    @Test
    fun `las voces y secciones instrumentales se preservan`() {
        val content = "[Asamblea]\n[C]Todos\n[Instrumental]\n[G]Solo\n[Sopranos]\n[F]Voz"
        val result = HarmonicTransposer.transposeChordProContent(content, 1, PitchClass.C_SHARP)
        assertTrue(result.contains("[Asamblea]"))
        assertTrue(result.contains("[Instrumental]"))
        assertTrue(result.contains("[Sopranos]"))
    }

    @Test
    fun `los acordes reales en mayusculas si se transpone`() {
        // Cifrado latino en mayúsculas: son acordes, no etiquetas. La salida normaliza la
        // grafía de la nota (Fa/Sol), que es lo correcto musicalmente.
        val content = "[MI]Texto [SOL]más [LA]fin"
        val result = HarmonicTransposer.transposeChordProContent(content, 1, PitchClass.F)
        assertTrue("MI sube a Fa", result.contains("[Fa]Texto"))
        assertTrue("SOL sube a Lab en una tonalidad con bemoles", result.contains("[Lab]más"))
        assertTrue(result.contains("[La#]fin") || result.contains("[Sib]fin"))
    }

    @Test
    fun `los acordes del cancionero real siguen parseando`() {
        // Muestras tomadas del bundle real: calidades y bemoles tal cual.
        val realChords = listOf(
            "SOL", "DO", "RE", "LA", "lam", "mim", "MI", "FA", "rem", "sim",
            "SI7", "fam#", "LA7", "SOL7", "dom#", "SI", "RE7", "solm", "SIb",
            "D7", "A7", "G7", "C7", "DO7", "mim7", "lam7", "Dm7", "SOL/SI", "SIB"
        )
        realChords.forEach { chord ->
            val parsed = com.grafitto.laudate.domain.parser.ChordTextParser.parse(chord)
            assertTrue("Debe parsear el acorde real [$chord]", parsed.isSuccess)
        }
    }

    @Test
    fun `el texto libre entre corchetes no se toma por acorde`() {
        // Palabras que empiezan por A-G pero no son acordes.
        listOf("Estrofa", "Estribillo", "Amén", "Aleluya", "Gloria", "Final").forEach { word ->
            val parsed = com.grafitto.laudate.domain.parser.ChordTextParser.parse(word)
            assertTrue("[$word] no debería ser un acorde", parsed.isFailure)
        }
    }

    @Test
    fun `convertir notacion no transpone y cambia Do por C`() {
        val content = "{key: Do}\n[Do]Hola [Sol]mundo"
        val result = HarmonicTransposer.convertChordProNotation(
            content = content,
            system = NotationSystem.ENGLISH,
            referenceKey = PitchClass.C
        )
        assertTrue(result.contains("[C]Hola [G]mundo"))
        assertTrue(result.contains("{key: C}"))
    }

    @Test
    fun `convertir notacion respeta las etiquetas de seccion`() {
        val content = "[Estrofa 1]\n[C]Hola\n[Coro]\n[G]Aleluya"
        val result = HarmonicTransposer.convertChordProNotation(
            content = content,
            system = NotationSystem.LATIN,
            referenceKey = PitchClass.C
        )
        assertTrue(result.contains("[Estrofa 1]"))
        assertTrue(result.contains("[Coro]"))
        assertEquals("[Do]Hola", result.lines()[1])
        assertEquals("[Sol]Aleluya", result.lines()[3])
    }

    @Test
    fun `convertir ida y vuelta devuelve el texto original`() {
        val original = "{key: C}\n[C]Hola [Am]mundo [F]fin"
        val latin = HarmonicTransposer.convertChordProNotation(original, NotationSystem.LATIN, PitchClass.C)
        val back = HarmonicTransposer.convertChordProNotation(latin, NotationSystem.ENGLISH, PitchClass.C)
        assertEquals(original, back)
    }
}
