package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabJump
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **C.5 · Colisiones** (roadmap §4): las marcas de una misma franja (repetición, cifra de veces,
 * volta y salto) se reparten para que no se solapen.
 */
class TabMarkCollisionsTest {

    private val cell = 22f

    // ── El resolvedor ───────────────────────────────────────────────────────────────────────────

    @Test
    fun dosMarcasQueSePisanSeSeparan() {
        // `:| x2` y la volta `1.` caen casi en el mismo sitio (entrada **desordenada**, como llega
        // desde el layout: primero el cierre, luego el salto…).
        val items = listOf(100f to ":|", 112f to "x2", 106f to "1.")
        val xs = TabMarkCollisions.resolve(items, cellWidth = cell, leftBound = 80f, rightBound = 180f)
        // Se comparan en el orden por `x`: ninguna marca invade a otra.
        val byX = items.zip(xs).sortedBy { it.first.first }
        for (i in 1 until byX.size) {
            val prevText = byX[i - 1].first.second
            val curText = byX[i].first.second
            val minAllowed = TabMarkCollisions.widthOf(prevText, cell) / 2f +
                TabMarkCollisions.MIN_GAP_FRACTION * cell +
                TabMarkCollisions.widthOf(curText, cell) / 2f
            assertTrue(
                "las marcas '$prevText' y '$curText' no se pisan",
                byX[i].second - byX[i - 1].second >= minAllowed - 0.01f
            )
        }
    }

    @Test
    fun unaSolaMarcaNoSeMueve() {
        val xs = TabMarkCollisions.resolve(listOf(100f to "1."), cell, 80f, 180f)
        assertEquals(listOf(100f), xs)
    }

    @Test
    fun lasMarcasYaSeparadasNoSeMueven() {
        // Con margen suficiente a la izquierda y sin solape, el resolvedor no toca nada.
        val xs = TabMarkCollisions.resolve(
            listOf(80f to "1.", 160f to ":|"),
            cellWidth = cell,
            leftBound = 60f,
            rightBound = 200f
        )
        assertEquals(80f, xs[0], 0.001f)
        assertEquals(160f, xs[1], 0.001f)
    }

    @Test
    fun elGrupoNoSeSalePorLaIzquierda() {
        // Dos marcas anchas muy juntas al borde: al separarlas, la primera no cruza el borde.
        val xs = TabMarkCollisions.resolve(
            listOf(90f to ":|", 95f to "x2"),
            cellWidth = cell,
            leftBound = 80f,
            rightBound = 200f
        )
        val halfFirst = TabMarkCollisions.widthOf(":|", cell) / 2f
        val halfSecond = TabMarkCollisions.widthOf("x2", cell) / 2f
        assertTrue("la primera marca no cruza el borde", xs[0] - halfFirst >= 80f - 0.5f)
        // Y siguen sin pisarse.
        assertTrue(
            "siguen separadas",
            xs[1] - xs[0] >= halfFirst + TabMarkCollisions.MIN_GAP_FRACTION * cell + halfSecond - 0.01f
        )
    }

    // ── Integración con la maquetación ────────────────────────────────────────────────────────────

    private fun tab(measures: List<TabMeasure>) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER, measures = measures
    )

    @Test
    fun elCierreYElSaltoNoSePisanEnElDibujo() {
        // Compás que **cierra repetición** (`:| x2`) **y** lleva un salto (`D.C.`) al final.
        // (D-65) El `:|` ya **no** es una marca de texto: es la propia barra de compás, así que en la
        // franja quedan el `x2` y el salto, y siguen sin pisarse.
        val measure = TabMeasure(
            slots = List(4) { com.grafitto.laudate.domain.model.tablature.TabSlot() },
            repeat = TabRepeatMark.END,
            jump = TabJump.DC
        )
        val drawing = TablatureSystemsLayout.layout(tab(listOf(measure)), maxWidth = 900f).single().drawing
        val marks = drawing.texts.filter { it.kind == TabTextKind.MARK }.sortedBy { it.x }
        assertTrue("hay varias marcas en la franja", marks.size >= 2)
        assertTrue("el cierre de repetición ya no se rotula", marks.none { it.text == ":|" })
        assertTrue("la barra de cierre sí está", drawing.lines.any { it.repeat == TabRepeatMark.END })
        // Ninguna pareja de marcas comparte posición (se han repartido).
        marks.zipWithNext().forEach { (a, b) ->
            assertTrue("marcas separadas: '${a.text}' y '${b.text}'", kotlin.math.abs(b.x - a.x) > 1f)
        }
    }
}
