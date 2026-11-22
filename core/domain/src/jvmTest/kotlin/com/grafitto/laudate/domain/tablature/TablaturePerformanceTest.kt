package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Q3 · RNF de rendimiento**: la maquetación de una tablatura real y su ciclo de guardado/lectura
 * deben cumplir los objetivos del proyecto (transposición/maquetación **≤ 50 ms**, RNF-110; **0 MB
 * añadido**, RNF-112, porque no se embeben fuentes de audio/imagen).
 *
 * Los tiempos son **holgados** (se ejecuta en CI): el objetivo es detectar regresiones de orden de
 * magnitud (algoritmos cuadráticos), no medir con precisión de milisegundos.
 */
class TablaturePerformanceTest {

    /** Una tablatura «grande»: 60 compases × 8 columnas × 2 cuerdas. */
    private fun bigTablature(): Tablature {
        var measures = TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, measureCount = 60
        )
        for (m in 0 until 60) {
            for (c in 0 until 8) {
                measures = TablatureEditing.setFret(measures, m, c, 1, (m + c) % 12)
                measures = TablatureEditing.setFret(measures, m, c, 2, (m * c) % 12)
            }
        }
        return Tablature(
            id = "big", songId = "song-1", title = "Grande", instrument = "Guitarra", tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4), resolution = TabResolution.EIGHTH,
            measures = measures
        )
    }

    @Test
    fun laMaquetacionDeUnaTablaturaGrandeEsRapida() {
        val tab = bigTablature()
        // Calentamiento (JIT) para no medir la primera pasada.
        TablatureSystemsLayout.layout(tab, maxWidth = 900f)
        val start = System.nanoTime()
        repeat(5) { TablatureSystemsLayout.layout(tab, maxWidth = 900f) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0 / 5
        assertTrue("la maquetación debe ser ≤ 50 ms (fue ${elapsedMs} ms)", elapsedMs <= 50.0)
    }

    @Test
    fun elCicloGuardarReabrirEsRapido() {
        val tab = bigTablature()
        val encoded = TablatureSerializer.encode(tab)
        val start = System.nanoTime()
        repeat(5) { TablatureSerializer.decode(encoded) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0 / 5
        assertTrue("el ciclo guardar/reabrir debe ser ≤ 50 ms (fue ${elapsedMs} ms)", elapsedMs <= 50.0)
    }
}
