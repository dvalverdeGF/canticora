package com.grafitto.laudate.domain.tablature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests del sintetizador puro de la nota de tablatura ([TabToneRenderer]). */
class TabToneRendererTest {

    @Test
    fun laFrecuenciaDeLa4Es440() {
        assertEquals(440.0, TabToneRenderer.frequencyOf(69), 0.001)
    }

    @Test
    fun laFrecuenciaSubeUnaOctavaCadaDoceSemitonos() {
        assertEquals(880.0, TabToneRenderer.frequencyOf(81), 0.001)
    }

    @Test
    fun unaNotaGeneraUnBufferNoVacioConSonido() {
        val pcm = TabToneRenderer.renderNote(64)
        assertTrue("el buffer tiene muestras", pcm.isNotEmpty())
        assertTrue("hay señal (no todo ceros)", pcm.any { it.toInt() != 0 })
    }

    @Test
    fun unAcordeSuena_mezlcaVariasCuerdas() {
        val pcm = TabToneRenderer.renderChord(listOf(40, 45, 50))
        assertTrue(pcm.isNotEmpty())
        assertTrue(pcm.any { it.toInt() != 0 })
    }

    @Test
    fun unaListaVaciaNoProduceSonido() {
        val pcm = TabToneRenderer.renderChord(emptyList())
        assertTrue("sin alturas no hay señal", pcm.all { it.toInt() == 0 })
    }

    @Test
    fun alturasFueraDeRangoSeIgnoran() {
        val pcm = TabToneRenderer.renderChord(listOf(-1, 200))
        assertTrue("nada suena", pcm.all { it.toInt() == 0 })
    }
}
