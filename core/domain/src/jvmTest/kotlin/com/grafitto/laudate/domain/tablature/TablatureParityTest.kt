package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabAccidental
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabBendAmount
import com.grafitto.laudate.domain.model.tablature.TabDynamic
import com.grafitto.laudate.domain.model.tablature.TabJump
import com.grafitto.laudate.domain.model.tablature.TabNavigationMark
import com.grafitto.laudate.domain.model.tablature.TabOctaveShift
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Q1 · Paridad (golden)**: transcribe una **tablatura de referencia** con la riqueza notacional de
 * MuseScore (técnicas, repeticiones, dinámicas, articulaciones, alteraciones, 8va, tempo, texto) y
 * comprueba que el **modelo** resultante coincide con el esperado y sobrevive a un ciclo completo de
 * guardado/reabierto. Es el test de «misma tablatura ⇒ mismo modelo» que cierra la paridad.
 */
class TablatureParityTest {

    /**
     * Construye por las **operaciones puras** de edición (como haría el editor) la tablatura de
     * referencia, para no depender de constructores a mano y ejercitar la ruta real de edición.
     */
    private fun buildReference(): Tablature {
        var measures = TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, measureCount = 3
        )
        // Compás 1 · cuerda 1: 0 (al aire con púa abajo) → 2 (ligado) → 4 (slide) con acento.
        measures = TablatureEditing.setFret(measures, 0, 0, 1, 0)
        measures = TablatureEditing.toggleTechnique(measures, 0, 0, 1, TabTechnique.PICK_DOWN)
        measures = TablatureEditing.setFret(measures, 0, 1, 1, 2)
        measures = TablatureEditing.toggleTechnique(measures, 0, 1, 1, TabTechnique.HAMMER)
        measures = TablatureEditing.setFret(measures, 0, 2, 1, 4)
        measures = TablatureEditing.toggleTechnique(measures, 0, 2, 1, TabTechnique.SLIDE)
        measures = TablatureEditing.toggleArticulation(measures, 0, 2, 1, TabArticulation.ACCENT)
        measures = TablatureEditing.setAccidental(measures, 0, 2, 1, TabAccidental.SHARP)
        // Compás 1 · cuerda 6: apagada (X) y fantasma.
        measures = TablatureEditing.setFret(measures, 0, 3, 6, 0)
        measures = TablatureEditing.toggleTechnique(measures, 0, 3, 6, TabTechnique.MUTED)

        // Compás 2 · bend con cantidad, vibrato y palm mute con alcance.
        measures = TablatureEditing.setFret(measures, 1, 0, 1, 7)
        measures = TablatureEditing.toggleTechnique(measures, 1, 0, 1, TabTechnique.BEND)
        measures = TablatureEditing.setBendAmount(measures, 1, 0, 1, TabBendAmount.HALF)
        measures = TablatureEditing.setFret(measures, 1, 1, 2, 5)
        measures = TablatureEditing.toggleTechnique(measures, 1, 1, 2, TabTechnique.PALM_MUTE)
        measures = TablatureEditing.setPalmMuteSpan(measures, 1, 1, 2, 3)
        measures = TablatureEditing.setRepeat(measures, 1, TabRepeatMark.END)

        // Compás 3 · armónico artificial, calderón, dinámica, navegación, salto, 8va, tempo y texto.
        measures = TablatureEditing.setFret(measures, 2, 0, 4, 12)
        measures = TablatureEditing.toggleTechnique(measures, 2, 0, 4, TabTechnique.ARTIFICIAL_HARMONIC)
        measures = TablatureEditing.toggleArticulation(measures, 2, 0, 4, TabArticulation.FERMATA)
        measures = TablatureEditing.setDynamic(measures, 2, TabDynamic.MF)
        measures = TablatureEditing.setNavigation(measures, 2, TabNavigationMark.CODA)
        measures = TablatureEditing.setJump(measures, 2, TabJump.DS_AL_CODA)
        measures = TablatureEditing.setOctaveShift(measures, 2, TabOctaveShift.OTTAVA)
        measures = TablatureEditing.setTempo(measures, 2, 96)
        measures = TablatureEditing.setText(measures, 2, "a tempo")

        return Tablature(
            id = "ref", songId = "song-1", title = "Referencia", instrument = "Guitarra", tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4), resolution = TabResolution.EIGHTH,
            measures = measures
        )
    }

    @Test
    fun laTablaturaDeReferenciaConservaTodoElModelo() {
        val tab = buildReference()
        val m1 = tab.measures[0]
        val m2 = tab.measures[1]
        val m3 = tab.measures[2]

        // Compás 1: la primera nota es una cuerda al aire con púa; la ligadura y el slide se conservan.
        assertEquals(0, m1.slots[0].strings.single().fret)
        assertTrue(m1.slots[0].strings.single().has(TabTechnique.PICK_DOWN))
        assertTrue(m1.slots[1].strings.single().has(TabTechnique.HAMMER))
        assertTrue(m1.slots[2].strings.single().has(TabTechnique.SLIDE))
        assertTrue(m1.slots[2].strings.single().articulations.contains(TabArticulation.ACCENT))
        assertEquals(TabAccidental.SHARP, m1.slots[2].strings.single().accidental)
        assertTrue(m1.slots[3].strings.single().isMuted)

        // Compás 2: bend de medio tono, palm mute de 3 columnas y cierre de repetición.
        assertEquals(TabBendAmount.HALF, m2.slots[0].strings.single().bendAmount)
        assertEquals(3, m2.slots[1].strings.single().palmMuteSpan)
        assertEquals(TabRepeatMark.END, m2.repeat)

        // Compás 3: armónico + calderón, y todas las marcas de compás.
        assertTrue(m3.slots[0].strings.single().has(TabTechnique.ARTIFICIAL_HARMONIC))
        assertTrue(m3.slots[0].strings.single().articulations.contains(TabArticulation.FERMATA))
        assertEquals(TabDynamic.MF, m3.dynamic)
        assertEquals(TabNavigationMark.CODA, m3.navigation)
        assertEquals(TabJump.DS_AL_CODA, m3.jump)
        assertEquals(TabOctaveShift.OTTAVA, m3.octaveShift)
        assertEquals(96, m3.tempo)
        assertEquals("a tempo", m3.text)
    }

    @Test
    fun laTablaturaDeReferenciaSobreviveAlCicloGuardarReabrir() {
        val tab = buildReference()
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tab))
        // Paridad TOTAL del modelo: el ciclo de ida y vuelta devuelve exactamente el mismo documento.
        assertEquals(tab, restored)
    }

    @Test
    fun laMaquetacionDeLaReferenciaProduceTodasLasFranjas() {
        val tab = buildReference()
        val drawing = TablatureLayout.layout(tab)
        // La clave TAB siempre está.
        assertTrue(drawing.texts.any { it.kind == TabTextKind.TAB_CLEF })
        // Y cada tipo de marca de la referencia aparece rotulado.
        assertTrue(drawing.texts.any { it.kind == TabTextKind.ARTICULATION })
        assertTrue(drawing.texts.any { it.kind == TabTextKind.DYNAMIC })
        assertTrue(drawing.texts.any { it.kind == TabTextKind.OCTAVE_SHIFT })
        assertTrue(drawing.texts.any { it.kind == TabTextKind.TEMPO })
        assertTrue(drawing.texts.any { it.kind == TabTextKind.TEXT })
    }

    @Test
    fun laTabSlotVaciaEsUnaPausaValida() {
        // Un compás nacido vacío es un silencio: el modelo no exige casillas de relleno.
        val empty = TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.EIGHTH, 1)
        assertTrue(empty.single().slots.isEmpty())
        assertTrue(TabSlot().isEmpty)
    }
}
