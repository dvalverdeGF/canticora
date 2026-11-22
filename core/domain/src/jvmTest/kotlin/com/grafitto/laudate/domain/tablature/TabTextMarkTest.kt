package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
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
 * **T6 · Texto**: el texto libre anclado a un compás (una indicación del copista) se fija, se maqueta
 * sobre el sistema y sobrevive al guardado. El rótulo, el momento y la nota de la tablatura (RF-127)
 * ya existían en el modelo.
 */
class TabTextMarkTest {

    private fun base(measureCount: Int = 2): List<TabMeasure> =
        TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, measureCount, withGuideColumns = true
        )

    private fun tablature(measures: List<TabMeasure>) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.EIGHTH, measures = measures,
        label = "Por los niños", moment = "Ofertorio", note = "Copia de 1998"
    )

    @Test
    fun setText_fijaYQuitaElTexto() {
        val conTexto = TablatureEditing.setText(base(), 0, "ritardando")
        assertEquals("ritardando", conTexto[0].text)
        assertNull(conTexto[1].text)
        assertNull(TablatureEditing.setText(conTexto, 0, null)[0].text)
    }

    @Test
    fun elTextoEnBlancoSeNormalizaANulo() {
        assertNull(TablatureEditing.setText(base(), 0, "   ")[0].text)
    }

    @Test
    fun elTextoSobreviveAlGuardado() {
        val measures = TablatureEditing.setText(base(), 1, "solo guitarra")
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tablature(measures)))
        assertEquals("solo guitarra", restored.measures[1].text)
        // El rótulo, el momento y la nota también se conservan (RF-127).
        assertEquals("Por los niños", restored.label)
        assertEquals("Ofertorio", restored.moment)
        assertEquals("Copia de 1998", restored.note)
    }

    @Test
    fun laMaquetacionRotulaElTextoDelCompas() {
        val measures = TablatureEditing.setText(base(), 0, "D.C. al Fine")
        val drawing = TablatureLayout.layout(tablature(measures))
        assertTrue(
            "debe rotular el texto libre",
            drawing.texts.any { it.text == "D.C. al Fine" && it.kind == TabTextKind.TEXT }
        )
    }
}
