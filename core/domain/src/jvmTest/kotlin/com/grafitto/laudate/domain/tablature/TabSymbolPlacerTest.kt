package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests del **apilado de símbolos** de una nota ([TabSymbolPlacer]).
 *
 * (2026-10-14) Los símbolos ya **no** se reparten en fila ni ensanchan la columna: se **apilan en
 * vertical** sobre la cifra, así que el ancho de la columna depende **solo de la duración**.
 */
class TabSymbolPlacerTest {

    private fun pluck(vararg techniques: TabTechnique) = TabPluck(
        position = TabPosition(1, 0),
        techniques = techniques.toSet()
    )

    @Test
    fun laLigaduraNoCuentaComoSimboloEncima() {
        assertEquals(0, TabSymbolPlacer.aboveCount(setOf(TabTechnique.HAMMER)))
        assertEquals(0, TabSymbolPlacer.aboveCount(setOf(TabTechnique.PULL)))
        assertEquals(1, TabSymbolPlacer.aboveCount(setOf(TabTechnique.BEND)))
    }

    @Test
    fun elOrdenColocaLaTecnicaConocidaAntesQueLaDesconocida() {
        val known = TabSymbolPlacer.aboveOrderIndex(TabGlyphKind.BEND)
        val unknown = TabSymbolPlacer.aboveOrderIndex(TabGlyphKind.SLUR)
        assertTrue(known < unknown)
    }

    // ── La pila: arco de púa + articulaciones + glifos de técnica ────────────────────────────────

    @Test
    fun laPilaSumaArcoDePuaArticulacionesYGlifosDeTecnica() {
        val pluck = TabPluck(
            position = TabPosition(1, 0),
            techniques = setOf(TabTechnique.PICK_DOWN, TabTechnique.BEND),
            articulations = setOf(TabArticulation.ACCENT, TabArticulation.MARCATO)
        )
        // 1 arco de púa + 2 articulaciones apiladas + 1 glifo de técnica (bend) = 4 franjas.
        assertEquals(4, TabSymbolPlacer.stackCount(pluck))
    }

    @Test
    fun unaNotaSinSimbolosNoOcupaNingunaFranja() {
        assertEquals(0, TabSymbolPlacer.stackCount(pluck()))
    }

    @Test
    fun maxStackCountTomaLaCuerdaMasCargadaDeLaColumna() {
        val slot = TabSlot(
            strings = listOf(
                pluck(TabTechnique.BEND),
                TabPluck(
                    position = TabPosition(2, 0),
                    techniques = setOf(TabTechnique.PICK_UP, TabTechnique.VIBRATO),
                    articulations = setOf(TabArticulation.ACCENT)
                )
            )
        )
        assertEquals(3, TabSymbolPlacer.maxStackCount(slot))
    }

    @Test
    fun elAnchoDeLaColumnaNoCambiaAlAnadirSimbolos() {
        // El corazón del punto 1 del usuario: añadir técnicas/articulaciones **no** puede mover la
        // tablatura. El ancho de la columna sale **solo** de la duración de la figura.
        val limpio = TabSlot(strings = listOf(pluck()))
        val cargado = TabSlot(
            strings = listOf(
                TabPluck(
                    position = TabPosition(1, 0),
                    techniques = setOf(
                        TabTechnique.PICK_DOWN,
                        TabTechnique.BEND,
                        TabTechnique.VIBRATO,
                        TabTechnique.PALM_MUTE
                    ),
                    articulations = setOf(TabArticulation.ACCENT, TabArticulation.MARCATO)
                )
            )
        )
        val a = TablatureLayout.layout(tablatureCon(limpio)).columns.getValue(0).single()
        val b = TablatureLayout.layout(tablatureCon(cargado)).columns.getValue(0).single()
        assertEquals("el ancho no depende de los símbolos", a.width, b.width, 0.001f)
        assertEquals("el centro tampoco se mueve", a.centerX, b.centerX, 0.001f)
    }

    private fun tablatureCon(slot: TabSlot) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
        measures = listOf(TabMeasure(slots = listOf(slot)))
    )
}
