package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **T3 · Articulaciones**: los acentos (`>`, `^`), el picado (`.`), el *tenuto*, el calderón y los
 * adornos se marcan **sobre la cifra**, se **combinan** y sobreviven al guardado.
 */
class TabArticulationTest {

    private fun oneNote(): List<TabMeasure> {
        val base = TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, 1
        )
        return TablatureEditing.setFret(base, 0, 0, 1, 3)
    }

    private fun tablature(measures: List<TabMeasure>) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.EIGHTH,
        measures = measures
    )

    @Test
    fun toggleArticulation_anadeYQuitaElMarcado() {
        val marcada = TablatureEditing.toggleArticulation(oneNote(), 0, 0, 1, TabArticulation.ACCENT)
        assertTrue(marcada[0].slots[0].strings[0].articulations.contains(TabArticulation.ACCENT))
        val quitada = TablatureEditing.toggleArticulation(marcada, 0, 0, 1, TabArticulation.ACCENT)
        assertTrue(quitada[0].slots[0].strings[0].articulations.isEmpty())
    }

    @Test
    fun lasArticulacionesSeCombinan() {
        var measures = oneNote()
        measures = TablatureEditing.toggleArticulation(measures, 0, 0, 1, TabArticulation.ACCENT)
        measures = TablatureEditing.toggleArticulation(measures, 0, 0, 1, TabArticulation.STACCATO)
        val articulations = measures[0].slots[0].strings[0].articulations
        // (D-40) Acento + picado **no** se apilan como dos signos: MuseScore los dibuja con el glifo
        // combinado `articAccentStaccatoAbove` (U+E4B0), el **sforzato**.
        assertEquals(setOf(TabArticulation.ACCENT_STACCATO), articulations)
    }

    @Test
    fun elSforzatoSeDesglosaAlQuitarleElPicado() {
        var measures = oneNote()
        measures = TablatureEditing.toggleArticulation(measures, 0, 0, 1, TabArticulation.ACCENT_STACCATO)
        assertEquals(
            setOf(TabArticulation.ACCENT_STACCATO),
            measures[0].slots[0].strings[0].articulations
        )
        // Quitar el **picado** deja el **acento** suelto, no borra el glifo entero: la alternancia es
        // por **cualidad** (`Chord::updateArticulations`, `chord.cpp:1768`).
        measures = TablatureEditing.toggleArticulation(measures, 0, 0, 1, TabArticulation.STACCATO)
        assertEquals(setOf(TabArticulation.ACCENT), measures[0].slots[0].strings[0].articulations)
    }

    @Test
    fun elPortatoEsTenutoYPicadoEnUnSoloGlifo() {
        var measures = oneNote()
        measures = TablatureEditing.toggleArticulation(measures, 0, 0, 1, TabArticulation.TENUTO)
        measures = TablatureEditing.toggleArticulation(measures, 0, 0, 1, TabArticulation.STACCATO)
        assertEquals(
            setOf(TabArticulation.TENUTO_STACCATO),
            measures[0].slots[0].strings[0].articulations
        )
    }

    @Test
    fun lasCincoCombinadasDeMuseScoreTienenSuGlifo() {
        // `ARTICULATION_ABOVE_JOIN_GROUPS` (`articulation.cpp:749`) y los códigos de SMuFL.
        assertEquals("\uE4B0", TabArticulation.ACCENT_STACCATO.glyph)
        assertEquals("\uE4AE", TabArticulation.MARCATO_STACCATO.glyph)
        assertEquals("\uE4B2", TabArticulation.TENUTO_STACCATO.glyph)
        assertEquals("\uE4B4", TabArticulation.TENUTO_ACCENT.glyph)
        assertEquals("\uE4BC", TabArticulation.MARCATO_TENUTO.glyph)
        TabArticulation.entries.filter { it.isCombined }.forEach { combined ->
            assertEquals(2, combined.categories.size)
            assertEquals(2, combined.components.size)
            assertTrue(combined.stacks)
        }
    }

    @Test
    fun lasArticulacionesSobrevivenAlGuardado() {
        var measures = oneNote()
        measures = TablatureEditing.toggleArticulation(measures, 0, 0, 1, TabArticulation.FERMATA)
        measures = TablatureEditing.toggleArticulation(measures, 0, 0, 1, TabArticulation.TENUTO)
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tablature(measures)))
        assertEquals(
            setOf(TabArticulation.FERMATA, TabArticulation.TENUTO),
            restored.measures[0].slots[0].strings[0].articulations
        )
    }

    @Test
    fun laMaquetacionRotulaLaArticulacionSobreLaCifra() {
        val measures = TablatureEditing.toggleArticulation(oneNote(), 0, 0, 1, TabArticulation.MARCATO)
        val drawing = TablatureLayout.layout(tablature(measures))
        val mark = drawing.texts.firstOrNull { it.kind == TabTextKind.ARTICULATION }
        assertTrue("debe rotular el marcato", mark != null && mark.text == TabArticulation.MARCATO.display)
        // La articulación va por DEBAJO (y menor) del centro de la cifra, no encima de la línea.
        val fret = drawing.texts.first { it.kind == TabTextKind.FRET }
        assertTrue("la articulación va sobre la cifra", mark!!.y < fret.y)
    }
}
