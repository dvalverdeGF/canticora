package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabHairpin
import com.grafitto.laudate.domain.model.tablature.TabHairpinType
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Reguladores** (*hairpins*): la **cuña** de crescendo/diminuendo. Es un elemento distinto de la
 * dinámica escrita (`cresc.`/`dim.`): la dinámica es el matiz, el regulador es la cuña que lo
 * desarrolla a lo largo de varios compases, como el `<HairPin>` de MuseScore.
 */
class TabHairpinTest {

    private fun base(measureCount: Int = 3): List<TabMeasure> =
        TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, measureCount, withGuideColumns = true
        )

    private fun tablature(measures: List<TabMeasure>) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.EIGHTH,
        measures = measures
    )

    @Test
    fun setHairpin_fijaLaCunaEnElCompasDondeNace() {
        val updated = TablatureEditing.setHairpin(base(), 1, TabHairpin(TabHairpinType.CRESCENDO, 2))
        assertEquals(TabHairpinType.CRESCENDO, updated[1].hairpin?.type)
        assertEquals(2, updated[1].hairpin?.measures)
        assertNull(updated[0].hairpin)
        assertNull(updated[2].hairpin)
    }

    @Test
    fun setHairpin_nullQuitaLaCuna() {
        val withWedge = TablatureEditing.setHairpin(base(), 0, TabHairpin(TabHairpinType.DIMINUENDO))
        val cleared = TablatureEditing.setHairpin(withWedge, 0, null)
        assertNull(cleared[0].hairpin)
    }

    @Test
    fun elAlcanceNuncaEsCero() {
        // Un alcance de 0 compases no significa nada: la cuña siempre abarca al menos uno.
        assertEquals(1, TabHairpin(TabHairpinType.CRESCENDO, 0).measures)
        assertEquals(1, TabHairpin(TabHairpinType.CRESCENDO, -5).measures)
        assertEquals(4, TabHairpin(TabHairpinType.CRESCENDO, 4).measures)
    }

    @Test
    fun elReguladorSobreviveAlGuardado() {
        val measures = TablatureEditing.setHairpin(
            base(2), 1, TabHairpin(TabHairpinType.DIMINUENDO, 3)
        )
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tablature(measures)))
        assertEquals(TabHairpinType.DIMINUENDO, restored.measures[1].hairpin?.type)
        assertEquals(3, restored.measures[1].hairpin?.measures)
    }

    @Test
    fun elReguladorYLaDinamicaSonElementosDistintos() {
        // MuseScore los guarda por separado: un `mp` con una cuña de crescendo a continuación.
        val measures = TablatureEditing.setDynamic(base(1), 0, com.grafitto.laudate.domain.model.tablature.TabDynamic.MP)
        val conCuna = TablatureEditing.setHairpin(measures, 0, TabHairpin(TabHairpinType.CRESCENDO, 2))
        assertEquals(com.grafitto.laudate.domain.model.tablature.TabDynamic.MP, conCuna[0].dynamic)
        assertEquals(TabHairpinType.CRESCENDO, conCuna[0].hairpin?.type)
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tablature(conCuna)))
        assertEquals(com.grafitto.laudate.domain.model.tablature.TabDynamic.MP, restored.measures[0].dynamic)
        assertEquals(TabHairpinType.CRESCENDO, restored.measures[0].hairpin?.type)
    }

    @Test
    fun laCunaSeDibujaConSuTipoYNoComoTexto() {
        val measures = TablatureEditing.setHairpin(base(1), 0, TabHairpin(TabHairpinType.CRESCENDO, 1))
        val system = TablatureSystemsLayout.layout(
            tablature(measures), maxWidth = 2000f,
            settings = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f)
        ).single()
        assertTrue(
            "el crescendo tiene que salir como cuña, no como texto",
            system.drawing.glyphs.any { it.kind == TabGlyphKind.CRESCENDO }
        )
        assertTrue(
            "y no tiene que aparecer ningún rótulo de dinámica",
            system.drawing.texts.none { it.kind == TabTextKind.DYNAMIC }
        )
    }
}
