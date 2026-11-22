package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Apilado vertical de articulaciones** (corrección del usuario, 2026-10-12): una nota con varias
 * articulaciones compatibles las **apila en vertical** (`^` encima de `>` encima de `𝄢`), como
 * MuseScore, y **no** las reparte en fila a los lados de la cifra.
 */
class TabArticulationStackTest {

    private fun tab(pluck: TabPluck) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
        measures = listOf(TabMeasure(slots = listOf(TabSlot(strings = listOf(pluck)))))
    )

    // ── El orden de apilado ─────────────────────────────────────────────────────────────────────

    @Test
    fun laPilaOrdenaDeAbajoArribaSinRepetirFranja() {
        val stack = TabArticulationStack.stack(
            setOf(TabArticulation.MARCATO, TabArticulation.ACCENT, TabArticulation.TENUTO)
        )
        // Tres franjas distintas (0,1,2), todas con `x` = centro de la cifra.
        assertEquals(listOf(0, 1, 2), stack.map { it.tier })
        // El tenuto (duración) va más cerca de la nota que el acento, y el marcato el más alto.
        assertEquals(TabArticulation.TENUTO, stack[0].articulation)
        assertEquals(TabArticulation.ACCENT, stack[1].articulation)
        assertEquals(TabArticulation.MARCATO, stack[2].articulation)
    }

    @Test
    fun losArcosDeArcoVanLosMasCercanosALaNota() {
        // (D-6, medido del `salmo2.pdf`) La nota del compás 4 apila `𝄢` abajo, `>` en medio y `^`
        // arriba: **los arcos son los más próximos a la cuerda**.
        val stack = TabArticulationStack.stack(
            setOf(TabArticulation.MARCATO, TabArticulation.ACCENT, TabArticulation.DOWN_BOW)
        )
        assertEquals(TabArticulation.DOWN_BOW, stack[0].articulation)
        assertEquals(TabArticulation.ACCENT, stack[1].articulation)
        assertEquals(TabArticulation.MARCATO, stack[2].articulation)
        // Y el arco supera en franja a cualquier articulación de duración/ataque.
        assertTrue(
            TabArticulationStack.tierOf(TabArticulation.DOWN_BOW) <
                TabArticulationStack.tierOf(TabArticulation.TENUTO)
        )
    }

    // ── Integración con la maquetación: apilado VERTICAL, no en fila ────────────────────────────

    @Test
    fun lasArticulacionesSeApilanEnLaMismaXySubenEnY() {
        val pluck = TabPluck(
            position = TabPosition(1, 3),
            articulations = setOf(
                TabArticulation.MARCATO,
                TabArticulation.ACCENT,
                TabArticulation.TENUTO
            )
        )
        val drawing = TablatureSystemsLayout.layout(tab(pluck), maxWidth = 900f).single().drawing
        val marks = drawing.texts.filter { it.kind == TabTextKind.ARTICULATION }
        assertEquals("las tres articulaciones se dibujan", 3, marks.size)
        // (1) Todas comparten la MISMA `x` (centro de la cifra): no se reparten en fila.
        assertEquals(1, marks.map { it.x }.distinct().size)
        // (2) Distinta `y`: cada una en su franja (apiladas).
        assertEquals("cada una en su franja", 3, marks.map { it.y }.distinct().size)
        // (3) Todas por encima de la rejilla (sobre la cuerda), como MuseScore.
        assertTrue(marks.all { it.y < drawing.gridTop })
    }

    @Test
    fun elArmonicoNoEntraEnLaPila() {
        // El armónico se pinta **en** la cifra (diamante), no como articulación apilada.
        assertFalse(TabArticulation.HARMONIC.stacks)
        val pluck = TabPluck(
            position = TabPosition(1, 3),
            articulations = setOf(TabArticulation.HARMONIC, TabArticulation.ACCENT)
        )
        val drawing = TablatureSystemsLayout.layout(tab(pluck), maxWidth = 900f).single().drawing
        val marks = drawing.texts.filter { it.kind == TabTextKind.ARTICULATION }
        assertEquals("solo el acento se apila", 1, marks.size)
    }

    @Test
    fun elArcoDePuaEsLaTecnicaYNoUnaArticulacionApilada() {
        // El arco de púa es la **técnica** `PICK_DOWN`: se dibuja (fuente única), pero NO como
        // articulación de la pila. Y no puede haber arco arriba y abajo a la vez.
        assertFalse(TabArticulation.UP_BOW.stacks)
        assertFalse(TabArticulation.DOWN_BOW.stacks)
        val pluck = TabPluck(position = TabPosition(1, 3), techniques = setOf(TabTechnique.PICK_DOWN))
        val drawing = TablatureSystemsLayout.layout(tab(pluck), maxWidth = 900f).single().drawing
        val pick = drawing.texts.filter {
            it.kind == TabTextKind.ARTICULATION && it.text == TabArticulation.DOWN_BOW.display
        }
        assertEquals("el arco de púa se dibuja una vez", 1, pick.size)
    }

    // ── Compatibilidad: no se pueden marcar los dos arcos ───────────────────────────────────────

    @Test
    fun noSePuedenMarcarArcoArribaYAbajoALaVez() {
        assertFalse(
            TabSymbolCompatibility.isArticulationAllowed(setOf(TabArticulation.DOWN_BOW), TabArticulation.UP_BOW)
        )
        assertTrue(
            TabSymbolCompatibility.isArticulationAllowed(emptySet(), TabArticulation.UP_BOW)
        )
        // Al añadir uno, se retira el otro.
        assertEquals(
            setOf(TabArticulation.UP_BOW),
            TabSymbolCompatibility.withArticulation(setOf(TabArticulation.DOWN_BOW), TabArticulation.UP_BOW)
        )
    }
}
