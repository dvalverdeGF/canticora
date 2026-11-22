package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabBarline
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **C.6 · Números de compás, repeticiones y navegación** (roadmap §4): el número va **sobre su barra**,
 * **a la derecha del área de clave y compás** (nunca sobre la clave `TAB` ni la fracción), y las
 * repeticiones cierran con la barra de su tipo.
 */
class TabMeasureNumberTest {

    private fun tab(measureCount: Int, resolution: TabResolution = TabResolution.QUARTER) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = resolution,
        measures = List(measureCount) { TabMeasure(slots = List(4) { TabSlot() }) }
    )

    @Test
    fun elNumeroDeCompasVaSobreSuBarraYNoSobreLaClave() {
        // Ancho corto → varios sistemas, para que aparezcan números (desde el 2.º sistema).
        val sistemas = TablatureSystemsLayout.layout(tab(8), maxWidth = 220f)
        assertTrue("debe haber varios sistemas", sistemas.size > 1)
        sistemas.drop(1).forEach { system ->
            val number = system.drawing.texts.firstOrNull { it.kind == TabTextKind.MEASURE_NUMBER }
            assertTrue("cada sistema desde el 2.º lleva su número", number != null)
            // El número está a la derecha de la rejilla (donde arranca el primer compás), no encima
            // de la clave ni de la fracción, que quedan más a la izquierda.
            assertTrue(
                "el número no pisa la clave (gridLeft=${system.drawing.gridLeft}, número=${number!!.x})",
                number.x >= system.drawing.gridLeft - 0.5f
            )
            // Y va por **encima** de la rejilla (franja superior).
            assertTrue("el número va encima de la rejilla", number.y < system.drawing.gridTop)
        }
    }

    @Test
    fun elPrimerSistemaNoLlevaNumero() {
        val sistemas = TablatureSystemsLayout.layout(tab(8), maxWidth = 220f)
        assertTrue(
            "el primer sistema no se numera",
            sistemas.first().drawing.texts.none { it.kind == TabTextKind.MEASURE_NUMBER }
        )
    }

    // ── Anacrusa (pickup) ────────────────────────────────────────────────────────────────────────

    @Test
    fun conAnacrusaElPrimerCompasEsElCeroYNoSeNumera() {
        // MuseScore numera la anacrusa como compás 0: el segundo compás es el 1. Sin el
        // desplazamiento, el director contaría un compás de más en toda la pieza.
        val conAnacrusa = tab(3).copy(
            measures = List(3) { TabMeasure(slots = List(4) { TabSlot() }, isPickup = it == 0) }
        )
        assertTrue(conAnacrusa.startsWithPickup)
        assertEquals(0, conAnacrusa.measureNumber(0))
        assertEquals(1, conAnacrusa.measureNumber(1))
        assertEquals(2, conAnacrusa.measureNumber(2))
        // Sin anacrusa la numeración es la natural.
        val sinAnacrusa = tab(3)
        assertEquals(1, sinAnacrusa.measureNumber(0))
        assertEquals(3, sinAnacrusa.measureNumber(2))
    }

    @Test
    fun laAnacrusaNoSeRotulaYElSiguienteCompasLlevaElUno() {
        // El primer sistema no se numera nunca; el segundo sí, y con anacrusa su primer compás es el
        // que lleva el «1» (no el «2»).
        val measures = List(8) { TabMeasure(slots = List(4) { TabSlot() }, isPickup = it == 0) }
        val sistemas = TablatureSystemsLayout.layout(tab(8).copy(measures = measures), maxWidth = 220f)
        assertTrue("debe haber varios sistemas", sistemas.size > 1)
        sistemas.drop(1).forEach { system ->
            val rotulo = system.drawing.texts.firstOrNull { it.kind == TabTextKind.MEASURE_NUMBER }
            assertTrue("cada sistema desde el 2.º lleva número", rotulo != null)
            // El número rotulado es el del primer compás del sistema, desplazado por la anacrusa.
            val esperado = system.measureIndices.first() // 0-based; con anacrusa el 0 es la anacrusa
            assertEquals("el rótulo del sistema", esperado.toString(), rotulo!!.text)
        }
    }

    @Test
    fun lasRepeticionesCierranConLaBarraDeSuTipo() {
        // Un compás con `:|` cierra con la barra de repetición; el último compás del sistema con la
        // barra **final** (fina + gruesa).
        val measures = listOf(
            TabMeasure(slots = List(4) { TabSlot() }, barline = TabBarline.DOUBLE),
            TabMeasure(slots = List(4) { TabSlot() })
        )
        val drawing = TablatureSystemsLayout.layout(tab(2).copy(measures = measures), maxWidth = 900f)
            .single().drawing
        val bars = drawing.lines.filter { it.isBar }
        assertTrue("el compás intermedio conserva su barra (doble)", bars.any { it.barline == TabBarline.DOUBLE })
        assertTrue("el último compás cierra con la final", bars.any { it.barline == TabBarline.FINAL })
    }

    // ── D-55 · El número cuelga de la pauta, no del borde de la franja ───────────────────────────

    @Test
    fun elNumeroDeCompasCuelgaDosEspaciosSobreLaPrimeraCuerda() {
        // `styleDef(measureNumberPosAbove, PointF(0.0, -2.0))` (`styledef.cpp:1284`): la **línea base**
        // del número va a 2 espacios sobre la primera cuerda. Antes se dibujaba a `tier * 0,25` del
        // borde de la franja `TOP`, que con un espacio de 5 pt lo dejaba a **0,6 pt** de la pauta: el
        // número se montaba sobre la primera cuerda.
        val sistemas = TablatureSystemsLayout.layout(tab(8), maxWidth = 220f)
        sistemas.drop(1).forEach { system ->
            val drawing = system.drawing
            val number = drawing.texts.first { it.kind == TabTextKind.MEASURE_NUMBER }
            val staffSpace = drawing.stringSpacing / TabRhythmGeometry.STRING_SPACING_SPACES
            // El pintor centra la **caja de línea** y deja la base a `0,35 em` del centro.
            val baseline = number.y + 0.35f * TabTextMetrics.MEASURE_NUMBER_EM * staffSpace
            assertEquals(
                "la base del número va a 2 espacios sobre la primera cuerda",
                drawing.gridTop - 2.0f * staffSpace,
                baseline,
                0.05f
            )
        }
    }

    @Test
    fun laFranjaSuperiorReservaLaTintaDelNumeroDeCompas() {
        // Si la franja `TOP` no reserva el aire del número, su tinta se sale por arriba del sistema y
        // el PDF la recorta. MuseScore agranda el bbox del sistema para contenerla.
        val sistemas = TablatureSystemsLayout.layout(tab(8), maxWidth = 220f)
        sistemas.drop(1).forEach { system ->
            val drawing = system.drawing
            val staffSpace = drawing.stringSpacing / TabRhythmGeometry.STRING_SPACING_SPACES
            assertTrue(
                "la franja superior reserva la tinta del número " +
                    "(gridTop=${drawing.gridTop}, mínimo=${TabStaffZones.MEASURE_NUMBER_INK_TOP_SPACES * staffSpace})",
                drawing.gridTop >= TabStaffZones.MEASURE_NUMBER_INK_TOP_SPACES * staffSpace - 0.01f
            )
        }
    }

    @Test
    fun elNumeroDeCompasSeDibujaConLaTallaDeMuseScore() {
        // `styleDef(measureNumberFontSize, 8.0)` con un espacio de 5 pt son 1,6 espacios. Con 2,0 el
        // número salía un 25 % más grande que en el `salmo2.pdf`.
        assertEquals(1.6f, TabTextMetrics.MEASURE_NUMBER_EM, 0.001f)
        assertEquals(1.6f, TabTextMetrics.emOf(TabTextKind.MEASURE_NUMBER), 0.001f)
    }
}
