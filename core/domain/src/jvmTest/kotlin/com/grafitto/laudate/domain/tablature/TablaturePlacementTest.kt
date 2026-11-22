package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Pruebas de la colocación pura de tablaturas: reordenar dentro del grupo y mover de sección. */
class TablaturePlacementTest {

    private fun tab(id: String, order: Int = 0, sectionId: String? = null) = Tablature(
        id = id,
        songId = "song-1",
        title = id,
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        sectionId = sectionId,
        order = order
    )

    @Test
    fun reorder_movesDownAndRenumbers() {
        val group = listOf(tab("a", 0), tab("b", 1), tab("c", 2))
        val result = TablaturePlacement.reorder(group, "a", 1)
        assertEquals(listOf("b", "a", "c"), result.map { it.id })
        assertEquals(listOf(0, 1, 2), result.map { it.order })
    }

    @Test
    fun reorder_movesUpAndRenumbers() {
        val group = listOf(tab("a", 0), tab("b", 1), tab("c", 2))
        val result = TablaturePlacement.reorder(group, "c", -1)
        assertEquals(listOf("a", "c", "b"), result.map { it.id })
        assertEquals(listOf(0, 1, 2), result.map { it.order })
    }

    @Test
    fun reorder_usesOrderNotListOrder() {
        // La lista llega desordenada: manda el campo `order`.
        val group = listOf(tab("c", 2), tab("a", 0), tab("b", 1))
        val result = TablaturePlacement.reorder(group, "a", 2)
        assertEquals(listOf("b", "c", "a"), result.map { it.id })
    }

    @Test
    fun reorder_clampsAtTheExtremes() {
        val group = listOf(tab("a", 0), tab("b", 1), tab("c", 2))
        // Ya está arriba: subir no hace nada.
        assertSame(group, TablaturePlacement.reorder(group, "a", -1))
        // Ya está abajo: bajar no hace nada.
        assertSame(group, TablaturePlacement.reorder(group, "c", 1))
    }

    @Test
    fun reorder_isNoOpWhenNothingMoves() {
        val group = listOf(tab("a", 0), tab("b", 1))
        assertSame(group, TablaturePlacement.reorder(group, "a", 0))
        assertSame(group, TablaturePlacement.reorder(group, "desconocida", 1))
        val single = listOf(tab("a"))
        assertSame(single, TablaturePlacement.reorder(single, "a", 1))
        val empty = emptyList<Tablature>()
        assertSame(empty, TablaturePlacement.reorder(empty, "a", 1))
    }

    @Test
    fun reorder_renumbersGapsLeftByDeletedTablatures() {
        // Huecos en el orden (0 y 7): al mover se renumera de 0 a n-1.
        val group = listOf(tab("a", 0), tab("b", 7))
        val result = TablaturePlacement.reorder(group, "b", -1)
        assertEquals(listOf("b", "a"), result.map { it.id })
        assertEquals(listOf(0, 1), result.map { it.order })
    }

    @Test
    fun moveToSection_appendsAtTheEndOfTheTargetGroup() {
        val moving = tab("a", order = 0, sectionId = "coro")
        val result = TablaturePlacement.moveToSection(
            tablature = moving,
            sectionId = "estrofa",
            siblingsInTarget = listOf(tab("x", 0, "estrofa"), tab("y", 1, "estrofa"))
        )
        assertEquals("estrofa", result.sectionId)
        assertEquals(2, result.order)
        // El resto del documento no se toca.
        assertEquals(moving.id, result.id)
        assertEquals(moving.songId, result.songId)
    }

    @Test
    fun moveToSection_toGeneralBlockUsesNull() {
        val result = TablaturePlacement.moveToSection(
            tablature = tab("a", order = 3, sectionId = "coro"),
            sectionId = null,
            siblingsInTarget = emptyList()
        )
        assertEquals(null, result.sectionId)
        assertEquals(0, result.order)
    }
}
