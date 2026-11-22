package com.grafitto.laudate.domain.model.music

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La tonalidad debe mostrarse en la nomenclatura elegida, igual que los acordes.
 * (Antes se colaba en anglosajón: `Tono: C`, `Original: F#`.)
 */
class NotationFormatTest {

    @Test
    fun `latina escribe las notas en Do-Re-Mi`() {
        assertEquals("Do", NotationFormat.key("C", NotationSystem.LATIN))
        assertEquals("Sol", NotationFormat.key("G", NotationSystem.LATIN))
        assertEquals("Si", NotationFormat.key("B", NotationSystem.LATIN))
        assertEquals("Fa#", NotationFormat.key("F#", NotationSystem.LATIN))
        assertEquals("Sib", NotationFormat.key("Bb", NotationSystem.LATIN))
    }

    @Test
    fun `anglosajona escribe las notas en C-D-E`() {
        assertEquals("C", NotationFormat.key("C", NotationSystem.ENGLISH))
        assertEquals("G", NotationFormat.key("G", NotationSystem.ENGLISH))
        assertEquals("F#", NotationFormat.key("F#", NotationSystem.ENGLISH))
        assertEquals("Bb", NotationFormat.key("Bb", NotationSystem.ENGLISH))
    }

    @Test
    fun `convierte entre notaciones en ambos sentidos`() {
        assertEquals("C", NotationFormat.key("Do", NotationSystem.ENGLISH))
        assertEquals("Do", NotationFormat.key("C", NotationSystem.LATIN))
        assertEquals("La", NotationFormat.key("A", NotationSystem.LATIN))
        assertEquals("A", NotationFormat.key("La", NotationSystem.ENGLISH))
    }

    @Test
    fun `conserva la calidad menor de la tonalidad`() {
        assertEquals("Lam", NotationFormat.key("Am", NotationSystem.LATIN))
        assertEquals("Am", NotationFormat.key("Lam", NotationSystem.ENGLISH))
        assertEquals("Rem", NotationFormat.key("Dm", NotationSystem.LATIN))
        assertEquals("Mib", NotationFormat.key("Eb", NotationSystem.LATIN))
    }

    @Test
    fun `convierte el bajo de los acordes con barra`() {
        // Los acordes con barra del constructor de acordes deben traducir ambos lados.
        assertEquals("Do/Sol", NotationFormat.key("C/G", NotationSystem.LATIN))
        assertEquals("C/G", NotationFormat.key("Do/Sol", NotationSystem.ENGLISH))
        assertEquals("Sol/Si", NotationFormat.key("G/B", NotationSystem.LATIN))
        assertEquals("Re/Fa#", NotationFormat.key("D/F#", NotationSystem.LATIN))
    }

    @Test
    fun `texto vacio o desconocido no se altera`() {
        assertEquals("", NotationFormat.key(null, NotationSystem.LATIN))
        assertEquals("", NotationFormat.key("", NotationSystem.LATIN))
        assertEquals("Modo dórico", NotationFormat.key("Modo dórico", NotationSystem.LATIN))
    }

    @Test
    fun `la notacion numerica deja la tonalidad en anglosajon`() {
        assertEquals("C", NotationFormat.key("C", NotationSystem.NNS))
    }
}
