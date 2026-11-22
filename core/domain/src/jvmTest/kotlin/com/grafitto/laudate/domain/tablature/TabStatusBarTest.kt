package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la **barra de estado** con el vocabulario de MuseScore 4 (B-1). */
class TabStatusBarTest {

    private val time = ScoreTimeSignature("4", 4)

    private fun measures() = TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER).let { listOf(it) }

    @Test
    fun laNotaDescribeAlturaCuerdaTrasteDuracionVozCompasTiempoYPentagrama() {
        // Mismo orden de campos que MuseScore: Note; Pitch; String; Fret; Duration; Voice; Measure; Beat; Staff.
        val written = TablatureEditing.setFret(measures(), 0, 0, stringNumber = 3, fret = 0)
        val text = TabStatusBar.text(
            measures = written,
            measureIndex = 0,
            slotIndex = 0,
            stringNumber = 3,
            tuning = Tuning.GUITAR,
            resolution = TabResolution.QUARTER,
            voice = 1
        )

        assertEquals(
            "Nota; Altura: Sol3; Cuerda: 3; Traste: 0; Duración: Negra; Voz: 1; Compás: 1; Tiempo: 1; Pentagrama 1",
            text
        )
    }

    @Test
    fun laAlturaIncluyeLaOctavaYElTraste() {
        // Cuerda 1 al aire de una guitarra = Mi4; en el traste 3 = Sol4.
        assertEquals("Mi4", TabStatusBar.pitchLabel(Tuning.GUITAR, 1, 0))
        assertEquals("Sol4", TabStatusBar.pitchLabel(Tuning.GUITAR, 1, 3))
        // Cuerda 6 al aire = Mi2.
        assertEquals("Mi2", TabStatusBar.pitchLabel(Tuning.GUITAR, 6, 0))
    }

    @Test
    fun laAlturaUsaSostenidosCuandoCorresponde() {
        // Traste 1 de la cuerda 2 (Si3) = Do4.
        assertEquals("Do4", TabStatusBar.pitchLabel(Tuning.GUITAR, 2, 1))
        // Traste 2 de la cuerda 2 = Do#4.
        assertEquals("Do#4", TabStatusBar.pitchLabel(Tuning.GUITAR, 2, 2))
    }

    @Test
    fun unaCuerdaInexistenteNoTieneAltura() {
        assertEquals(null, TabStatusBar.pitchLabel(Tuning.GUITAR, 9, 0))
    }

    @Test
    fun elTiempoCuentaEnNegrasDesdeElInicioDelCompas() {
        val measure = TablatureEditing.emptyCompleteMeasure(time, TabResolution.QUARTER)
        assertEquals("1", TabStatusBar.beatLabel(measure, 0, TabResolution.QUARTER))
        assertEquals("2", TabStatusBar.beatLabel(measure, 1, TabResolution.QUARTER))
        assertEquals("4", TabStatusBar.beatLabel(measure, 3, TabResolution.QUARTER))
    }

    @Test
    fun elTiempoAdmiteFraccionesDePulso() {
        // Rejilla de corcheas: la segunda columna cae en el pulso 1,5.
        val measure = TablatureEditing.emptyCompleteMeasure(time, TabResolution.EIGHTH)
        assertEquals("1", TabStatusBar.beatLabel(measure, 0, TabResolution.EIGHTH))
        assertEquals("1,5", TabStatusBar.beatLabel(measure, 1, TabResolution.EIGHTH))
        assertEquals("2", TabStatusBar.beatLabel(measure, 2, TabResolution.EIGHTH))
    }

    @Test
    fun elSilencioSeAnunciaComoSilencio() {
        val text = TabStatusBar.text(
            measures = measures(),
            measureIndex = 0,
            slotIndex = 1,
            stringNumber = 1,
            tuning = Tuning.GUITAR,
            resolution = TabResolution.QUARTER,
            voice = 1
        )
        assertTrue(text.startsWith("Silencio; Cuerda: 1"))
        assertTrue(text.contains("Duración: Silencio de negra"))
    }

    @Test
    fun unaCasillaVaciaSeAnunciaComoCasillaVacia() {
        val text = TabStatusBar.text(
            measures = listOf(com.grafitto.laudate.domain.model.tablature.TabMeasure()),
            measureIndex = 0,
            slotIndex = 0,
            stringNumber = 1,
            tuning = Tuning.GUITAR,
            resolution = TabResolution.QUARTER,
            voice = 2
        )
        assertTrue(text.startsWith("Casilla vacía"))
        assertTrue("la voz mostrada es la activa", text.contains("Voz: 2"))
    }

    @Test
    fun sinCursorNoHaySeleccion() {
        assertEquals(
            TabStatusBar.NO_SELECTION,
            TabStatusBar.text(emptyList(), 0, 0, 1, Tuning.GUITAR, TabResolution.QUARTER, 1)
        )
    }

    @Test
    fun laVozMostradaEsLaDeLaCasilla() {
        val voiced = TablatureEditing.setSlotVoice(measures(), 0, 0, 3)
        val text = TabStatusBar.text(voiced, 0, 0, 1, Tuning.GUITAR, TabResolution.QUARTER, voice = 1)
        assertTrue(text.contains("Voz: 3"))
    }

    @Test
    fun laDuracionDeLaBarraDeEscrituraCaeALaSubdivision() {
        assertEquals("Negra", TabStatusBar.durationLabel(null, TabResolution.QUARTER))
        assertEquals("Corchea", TabStatusBar.durationLabel(null, TabResolution.EIGHTH))
        assertEquals("Blanca", TabStatusBar.durationLabel(RhythmFigure.HALF, TabResolution.QUARTER))
    }
}
