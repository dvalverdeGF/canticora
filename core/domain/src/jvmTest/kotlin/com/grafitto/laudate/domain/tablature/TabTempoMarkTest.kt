package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **T5 · Tempo**: el BPM de un compás se fija, se rotula (`♩ = N · Andante`) sobre el sistema y
 * sobrevive al guardado. Se acota a un rango razonable.
 */
class TabTempoMarkTest {

    private fun base(measureCount: Int = 2): List<TabMeasure> =
        TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, measureCount, withGuideColumns = true
        )

    private fun tablature(measures: List<TabMeasure>) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.EIGHTH, measures = measures
    )

    @Test
    fun setTempo_fijaYQuitaElBpm() {
        val conTempo = TablatureEditing.setTempo(base(), 0, 92)
        assertEquals(92, conTempo[0].tempo)
        assertNull(conTempo[1].tempo)
        assertNull(TablatureEditing.setTempo(conTempo, 0, null)[0].tempo)
    }

    @Test
    fun elTempoSeAcotaAlRangoValido() {
        assertEquals(TablatureEditing.MAX_TEMPO_BPM, TablatureEditing.setTempo(base(), 0, 9999)[0].tempo)
        assertEquals(TablatureEditing.MIN_TEMPO_BPM, TablatureEditing.setTempo(base(), 0, 1)[0].tempo)
    }

    @Test
    fun elTempoSobreviveAlGuardado() {
        val measures = TablatureEditing.setTempo(base(), 1, 132)
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tablature(measures)))
        assertEquals(132, restored.measures[1].tempo)
    }

    @Test
    fun laMaquetacionRotulaElTempoConSuIndicacion() {
        val measures = TablatureEditing.setTempo(base(), 0, 90)
        val drawing = TablatureLayout.layout(tablature(measures))
        val tempo = drawing.texts.firstOrNull { it.kind == TabTextKind.TEMPO }
        assertTrue("debe rotular el tempo", tempo != null)
        assertTrue("incluye el BPM y la indicación", tempo!!.text.contains("90") && tempo.text.contains("Andante"))
    }

    @Test
    fun elRotuloDelTempoVaAlineadoALaIzquierda() {
        // (D-8) MuseScore ancla la indicación al **inicio** del compás y el texto crece hacia la
        // derecha; centrarlo la metía dentro del pentagrama y la desalineaba con el PDF.
        val measures = TablatureEditing.setTempo(base(), 0, 90)
        val drawing = TablatureLayout.layout(tablature(measures))
        val tempo = drawing.texts.first { it.kind == TabTextKind.TEMPO }
        assertEquals(TabTextAlign.LEFT, tempo.align)
        val resto = drawing.texts.filter { it.kind != TabTextKind.TEMPO }
        assertTrue("el resto de textos sigue centrado", resto.all { it.align == TabTextAlign.CENTER })
    }

    @Test
    fun elRotuloDelTempoEmpiezaEnLaFraccionDeCompas() {
        // (D-26) MuseScore alinea el tempo sobre el **compás** cuando cae en el primer tiempo de un
        // compás que abre sistema (`TLayout::layoutTempoText`, `tlayout.cpp:5766-5775`): retrocede
        // hasta el borde izquierdo de la fracción. En el PDF el `4/4` ocupa x 352–387 y el `♩` empieza
        // en 351, un píxel a su izquierda.
        val measures = TablatureEditing.setTempo(base(), 0, 80)
        val settings = TabLayoutSettings(cellWidth = 22f, stringSpacing = 20f, labelWidth = 26f, padding = 8f)
        val drawing = TablatureLayout.layout(tablature(measures), settings)
        val tempo = drawing.texts.first { it.kind == TabTextKind.TEMPO }
        val clef = drawing.texts.firstOrNull { it.kind == TabTextKind.TAB_CLEF }
        val time = drawing.texts.firstOrNull { it.kind == TabTextKind.TIME_NUMERATOR }
        assertTrue("el sistema 1 lleva cabecera", clef != null && time != null)
        // El rótulo arranca en el **borde izquierdo** de la fracción, no en su centro ni en el de la
        // columna. La fracción se dibuja centrada en `time.x`, así que su borde izquierdo está a
        // `timeSpace / 2` = `labelWidth * 0,95 / 2` a la izquierda.
        val timeLeftEdge = time!!.x - settings.labelWidth * 0.95f / 2f
        assertEquals(
            "el tempo debe empezar en el borde izquierdo de la fracción",
            timeLeftEdge,
            tempo.x,
            0.01f
        )
        // Y sigue a la derecha de la clave: no se sale del pentagrama por la izquierda.
        assertTrue("el tempo (x=${tempo.x}) no puede ir antes de la clave (x=${clef!!.x})", tempo.x >= clef.x)
    }

    @Test
    fun elRotuloDelTempoDeUnCompasInteriorSigueEnSuColumna() {
        // (D-26) La regla de MuseScore solo se aplica al **primer** tiempo de un compás que abre
        // sistema: en un compás interior el rótulo se queda en el origen de sus columnas.
        val measures = TablatureEditing.setTempo(base(measureCount = 3), 1, 92)
        val settings = TabLayoutSettings(cellWidth = 22f, stringSpacing = 20f, labelWidth = 26f, padding = 8f)
        val drawing = TablatureLayout.layout(tablature(measures), settings)
        val tempo = drawing.texts.first { it.kind == TabTextKind.TEMPO }
        val box = drawing.measureBounds.first { it.measureIndex == 1 }
        assertTrue(
            "el tempo (x=${tempo.x}) debe caer dentro de su compás (${box.left}–${box.right})",
            tempo.x >= box.left && tempo.x <= box.right
        )
    }

    @Test
    fun elRotuloLiteralSobreviveAlGuardado() {
        val measures = TablatureEditing.setTempo(base(), 0, 80).mapIndexed { i, m ->
            if (i == 0) m.copy(tempoText = "\uECA5 = 80") else m
        }
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tablature(measures)))
        assertEquals("\uECA5 = 80", restored.measures[0].tempoText)
        assertNull(restored.measures[1].tempoText)
    }

    @Test
    fun elRotuloLiteralDelCompasMandaSobreElInventado() {
        // (D-8) Si el `.mscx` trae su propio rótulo (`♩ = 80`, `Andante`), se usa **tal cual**; el
        // `♩ = N · Andante` de [TablatureLayout] es solo el respaldo cuando no hay texto de origen.
        val measures = TablatureEditing.setTempo(base(), 0, 80).mapIndexed { i, m ->
            if (i == 0) m.copy(tempoText = "\uECA5 = 80") else m
        }
        val drawing = TablatureLayout.layout(tablature(measures))
        val tempo = drawing.texts.first { it.kind == TabTextKind.TEMPO }
        assertEquals("\uECA5 = 80", tempo.text)
    }

    @Test
    fun elRotuloDelTempoCuelgaAUnaAlturaFijaSobreLaPauta() {
        // (D-8) MuseScore **no** apila el tempo encima de la pila de articulaciones: lo cuelga a una
        // altura fija sobre la primera cuerda. Medido del `salmo2.pdf` (pauta en y = 535, espacio =
        // 20,67 px): el signo `=` queda a **2,98 espacios** de la primera cuerda. Antes la franja
        // `TEXT` se sumaba por encima de `SYMBOLS` y el tempo se iba a **5,8 espacios**, con el
        // sistema reservando un aire que el PDF no tiene.
        val measures = TablatureEditing.setTempo(base(), 0, 90)
        val drawing = TablatureLayout.layout(tablature(measures))
        val tempo = drawing.texts.first { it.kind == TabTextKind.TEMPO }
        val staffSpace = TabLayoutSettings().stringSpacing / TabRhythmGeometry.STRING_SPACING_SPACES
        val altura = (drawing.gridTop - tempo.y) / staffSpace
        assertEquals(TabStaffZones.TEXT_ANCHOR_SPACES, altura, 0.01f)
    }

    @Test
    fun losDosRotulosDeTempoCompartenLaMismaLineaBase() {
        // (D-11) MuseScore ancla el `<Tempo>` por su **línea base** (`tempoPosAbove`), así que dos
        // indicaciones en compases distintos caen a la **misma** altura aunque una lleve el `♩` de la
        // fuente musical y la otra sea solo texto. Si la maquetación les diera `y` distintas, el
        // pintor no podría alinearlas y `Andante` saldría 0,6 espacios por debajo de `= 80`.
        val measures = TablatureEditing.setTempo(base(measureCount = 3), 0, 80)
            .let { TablatureEditing.setTempo(it, 1, 92) }
        val drawing = TablatureLayout.layout(tablature(measures))
        val tempos = drawing.texts.filter { it.kind == TabTextKind.TEMPO }
        assertEquals("los dos compases llevan tempo", 2, tempos.size)
        assertEquals("comparten la línea base", tempos[0].y, tempos[1].y, 0.01f)
        assertTrue("el rótulo va por encima de la pauta", tempos[0].y < drawing.gridTop)
    }

    @Test
    fun laFranjaDeTextoNoSeApilaEncimaDeLaDeSimbolos() {
        // (D-8) La franja `TEXT` **no** se suma por encima de `SYMBOLS`: el tempo vive a una altura
        // fija sobre la pauta y solo reserva el aire que le **falte** para llegar a
        // [TabStaffZones.TEXT_ANCHOR_SPACES]. Con una pila de articulaciones lo bastante alta no
        // reserva nada; con una corta, solo el hueco que queda.
        val settings = TabLayoutSettings()
        val strings = tablature(base()).tuning.drawnStrings().size
        fun zonas(vararg articulaciones: TabArticulation) = TabStaffZones.of(
            settings,
            strings,
            listOf(
                TabMeasure(
                    slots = listOf(
                        TabSlot(
                            strings = listOf(
                                TabPluck(TabPosition(1, 0), articulations = articulaciones.toSet())
                            )
                        )
                    )
                )
            ),
            showFigures = true
        )
        // Pila **alta** (4 franjas): el tempo cabe dentro de `SYMBOLS` y `TEXT` no reserva nada.
        val alta = zonas(
            TabArticulation.ACCENT,
            TabArticulation.MARCATO,
            TabArticulation.STACCATO,
            TabArticulation.TENUTO
        )
        assertEquals(0f, alta.heightOf(TabZone.TEXT), 0.01f)
        assertTrue(
            "el tempo queda dentro de la franja de símbolos",
            alta.anchor(TabZone.TEXT) > alta.top(TabZone.SYMBOLS)
        )
        // Pila **corta** (2 franjas): falta aire y `TEXT` reserva solo la diferencia.
        val corta = zonas(TabArticulation.ACCENT, TabArticulation.MARCATO)
        val staffSpace = settings.stringSpacing / TabRhythmGeometry.STRING_SPACING_SPACES
        // (D-59) `heightOf(SYMBOLS)` incluye el **aire de base** de la pila, así que con una pila corta
        // el tempo ya cabe dentro y `TEXT` no reserva nada (el déficit se acota a cero).
        val falta = (TabStaffZones.TEXT_ANCHOR_SPACES * staffSpace + staffSpace -
            corta.heightOf(TabZone.SYMBOLS)).coerceAtLeast(0f)
        assertEquals(falta, corta.heightOf(TabZone.TEXT), 0.01f)
        // En ambos casos el tempo cuelga **siempre** a la misma altura sobre la pauta.
        assertEquals(
            alta.gridTop - TabStaffZones.TEXT_ANCHOR_SPACES * staffSpace,
            alta.anchor(TabZone.TEXT),
            0.01f
        )
        assertEquals(
            corta.gridTop - TabStaffZones.TEXT_ANCHOR_SPACES * staffSpace,
            corta.anchor(TabZone.TEXT),
            0.01f
        )
    }
}
