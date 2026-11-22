package com.grafitto.laudate.domain.tablature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **D-29 · Mezclador de la reproducción**: volumen, balance, silencio y solo.
 *
 * El mezclador guarda los valores **normalizados** (`0..1` el volumen, `-1..1` el balance) y expone
 * la **ganancia efectiva** que se le pide al motor. Lo que se prueba aquí es esa aritmética: el
 * silencio manda sobre el volumen, los valores fuera de rango se acotan y el rótulo del balance se
 * lee como en una mesa de mezclas.
 */
class TabMixerTest {

    @Test
    fun elMezcladorPorDefectoSuenaAlMaximoYCentrado() {
        val mixer = TabMixer.DEFAULT
        assertEquals(1f, mixer.volume, 0.0001f)
        assertEquals(0f, mixer.pan, 0.0001f)
        assertFalse(mixer.muted)
        assertFalse(mixer.solo)
        assertEquals(1f, mixer.gain, 0.0001f)
        assertFalse(mixer.isSilent)
        assertEquals(100, mixer.volumePercent)
        assertEquals("C", mixer.panLabel)
    }

    @Test
    fun elVolumenSeAcotaAlRangoDelMotor() {
        assertEquals(1f, TabMixer(volume = 3f).safeVolume, 0.0001f)
        assertEquals(0f, TabMixer(volume = -2f).safeVolume, 0.0001f)
        assertEquals(0.5f, TabMixer(volume = 0.5f).safeVolume, 0.0001f)
    }

    @Test
    fun elBalanceSeAcotaAIzquierdaYDerecha() {
        assertEquals(1f, TabMixer(pan = 9f).safePan, 0.0001f)
        assertEquals(-1f, TabMixer(pan = -9f).safePan, 0.0001f)
    }

    @Test
    fun elSilencioMandaSobreElVolumen() {
        // MuseScore: `M` calla la tira aunque el deslizador esté al máximo. Si no, el usuario tendría
        // que bajar el volumen a cero y perder el valor al que lo tenía.
        val mixer = TabMixer(volume = 1f, muted = true)
        assertEquals(0f, mixer.gain, 0.0001f)
        assertTrue(mixer.isSilent)
        // El volumen **no** se pierde: al quitar el silencio vuelve el que había.
        assertEquals(1f, mixer.copy(muted = false).gain, 0.0001f)
    }

    @Test
    fun elVolumenACeroTambienEsSilencio() {
        val mixer = TabMixer(volume = 0f)
        assertTrue(mixer.isSilent)
        assertEquals(0f, mixer.gain, 0.0001f)
    }

    @Test
    fun elRotuloDelBalanceSeLeeComoEnUnaMesa() {
        assertEquals("C", TabMixer(pan = 0f).panLabel)
        assertEquals("I50", TabMixer(pan = -0.5f).panLabel)
        assertEquals("D50", TabMixer(pan = 0.5f).panLabel)
        assertEquals("I100", TabMixer(pan = -1f).panLabel)
        assertEquals("D100", TabMixer(pan = 1f).panLabel)
    }

    @Test
    fun elRotuloDelBalanceRedondeaSinDecimales() {
        // `-0.333` → `I33`, no `I33.3`: el control no enseña decimales.
        assertEquals("I33", TabMixer(pan = -0.333f).panLabel)
        assertEquals("D66", TabMixer(pan = 0.666f).panLabel)
    }

    @Test
    fun elPorcentajeDelVolumenSeRedondeaAlEntero() {
        assertEquals(0, TabMixer(volume = 0f).volumePercent)
        assertEquals(50, TabMixer(volume = 0.5f).volumePercent)
        assertEquals(80, TabMixer(volume = 0.8f).volumePercent)
        assertEquals(100, TabMixer(volume = 1f).volumePercent)
    }

    @Test
    fun conUnaSolaTiraElSoloNoSilenciaNada() {
        // Con una sola tira no hay nada que comparar: `S` se guarda y se pinta, pero la tira suena.
        assertTrue(TabMixer(solo = true).isAudible())
        assertTrue(TabMixer().isAudible())
    }

    @Test
    fun conVariasTirasElSoloSilenciaLasDemas() {
        // La regla que usará el día que haya más de un pentagrama: si alguna lleva `solo`, solo suenan
        // las que lo llevan; si ninguna lo lleva, suenan todas las que no están en silencio.
        val solo = TabMixer(solo = true)
        val otra = TabMixer()
        assertTrue(solo.isAudible(anySolo = true))
        assertFalse(otra.isAudible(anySolo = true))
        assertTrue(otra.isAudible(anySolo = false))
        // El silencio gana al solo: una tira con `M` y `S` no suena.
        assertFalse(TabMixer(muted = true, solo = true).isAudible(anySolo = true))
    }

    @Test
    fun elMezcladorEsUnValorInmutable() {
        // Es un `data class` de Dominio: cada cambio produce una instancia nueva, así que el estado de
        // la UI no se puede mutar por detrás.
        val original = TabMixer(volume = 0.4f)
        val cambiado = original.copy(volume = 0.9f)
        assertEquals(0.4f, original.volume, 0.0001f)
        assertEquals(0.9f, cambiado.volume, 0.0001f)
        assertEquals(original, TabMixer(volume = 0.4f))
    }
}
