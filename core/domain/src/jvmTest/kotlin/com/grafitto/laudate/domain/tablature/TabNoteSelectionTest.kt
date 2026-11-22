package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Selección y portapapeles de **notas** del editor (Fase 2 del encargo de paridad, ADR-020).
 *
 * MuseScore selecciona notas (no solo compases): con un clic, con `Ctrl`, con `Mayús` y con `Ctrl+A`.
 * Aquí se comprueba la **aritmética** de esa selección y las operaciones de borrar/copiar/pegar.
 */
class TabNoteSelectionTest {

    private fun measuresWithNotes(): List<TabMeasure> = listOf(
        TabMeasure(
            slots = listOf(
                TabSlot(strings = listOf(TabPluck(1, 0), TabPluck(3, 2))),
                TabSlot(strings = listOf(TabPluck(2, 3))),
                TabSlot(strings = listOf(TabPluck(1, 5)))
            )
        ),
        TabMeasure(slots = listOf(TabSlot(strings = listOf(TabPluck(1, 7)))))
    )

    @Test
    fun singleAndToggle_buildTheSelection() {
        val ref = TabNoteRef(0, 0, 1)
        val other = TabNoteRef(0, 0, 3)
        val single = TabNoteSelection().single(ref)
        assertEquals(1, single.size)
        assertTrue(single.contains(ref))
        assertFalse(single.contains(other))

        // **Ctrl+clic**: alterna (añade si no estaba, quita si estaba).
        val both = single.toggled(other)
        assertEquals(2, both.size)
        val back = both.toggled(other)
        assertEquals(1, back.size)
        assertFalse(back.contains(other))
    }

    @Test
    fun range_selectsConsecutiveColumnsOnTheSameString() {
        // **Mayús+clic/flecha**: extiende a lo largo del tiempo, sin cambiar de cuerda.
        val selection = TabNoteSelection.range(measureIndex = 0, fromSlot = 0, toSlot = 2, stringNumber = 1)
        assertEquals(3, selection.size)
        assertTrue(selection.contains(TabNoteRef(0, 0, 1)))
        assertTrue(selection.contains(TabNoteRef(0, 1, 1)))
        assertTrue(selection.contains(TabNoteRef(0, 2, 1)))
    }

    @Test
    fun range_isSymmetrical() {
        // Extender hacia atrás da el mismo rango que hacia delante.
        assertEquals(
            TabNoteSelection.range(0, 0, 2, 1),
            TabNoteSelection.range(0, 2, 0, 1)
        )
    }

    @Test
    fun ofMeasure_selectsEveryNoteOfTheMeasure() {
        val selection = TabNoteSelection.ofMeasure(measuresWithNotes(), 0)
        assertEquals("el compás tiene 2 + 1 + 1 notas", 4, selection.size)
        assertTrue(selection.contains(TabNoteRef(0, 1, 2)))
        // El compás 1 solo tiene una nota.
        assertEquals(1, TabNoteSelection.ofMeasure(measuresWithNotes(), 1).size)
    }

    @Test
    fun ofDocument_selectsEverything() {
        // **Ctrl+A**.
        assertEquals(5, TabNoteSelection.ofDocument(measuresWithNotes()).size)
    }

    @Test
    fun removeSelectedNotes_deletesOnlyTheSelectedStrings() {
        val measures = measuresWithNotes()
        val selection = TabNoteSelection().single(TabNoteRef(0, 0, 3))
        val result = TablatureEditing.removeSelectedNotes(measures, selection)
        val slot = result[0].slots[0]
        // La cuerda 3 pierde su nota; la cuerda 1 del mismo acorde se queda.
        assertTrue(slot.strings.none { it.stringNumber == 3 })
        assertEquals(1, slot.strings.size)
        assertEquals(1, slot.strings.first().stringNumber)
    }

    @Test
    fun removeSelectedNotes_keepsTheColumns() {
        // Borrar todas las notas de una columna deja la columna (un silencio), no la elimina.
        val measures = measuresWithNotes()
        val selection = TabNoteSelection.ofMeasure(measures, 0)
        val result = TablatureEditing.removeSelectedNotes(measures, selection)
        assertEquals("las columnas se conservan", 3, result[0].slots.size)
        assertTrue(result[0].slots.all { it.strings.isEmpty() })
        // Y el compás 1 no se toca.
        assertEquals(1, result[1].slots[0].strings.size)
    }

    @Test
    fun copyAndPaste_reproduceTheNotesAtTheTarget() {
        val measures = measuresWithNotes()
        val selection = TabNoteSelection.ofMeasure(measures, 0)
        val clipboard = TablatureEditing.copySelectedNotes(measures, selection)
        assertEquals(4, clipboard.notes.size)

        // Se pega **dos compases más adelante**, conservando la forma.
        val target = TabNoteRef(1, 0, 1)
        val pasted = TablatureEditing.pasteNotes(measures, clipboard, target)
        // El compás 1 (que ya tenía una nota en la cuerda 1) gana además las notas de las columnas 1 y 2.
        assertEquals(3, pasted[1].slots.size)
        assertTrue(pasted[1].slots[1].strings.any { it.stringNumber == 2 })
        assertTrue(pasted[1].slots[2].strings.any { it.stringNumber == 1 })
    }

    @Test
    fun copy_preservesTechniquesAndFingerings() {
        val measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(
                        strings = listOf(
                            TabPluck(
                                position = com.grafitto.laudate.domain.model.tablature.TabPosition(2, 5),
                                techniques = setOf(com.grafitto.laudate.domain.model.tablature.TabTechnique.HAMMER,
                                    com.grafitto.laudate.domain.model.tablature.TabTechnique.VIBRATO),
                                frettingFinger = com.grafitto.laudate.domain.model.tablature.TabFrettingFinger.INDEX
                            )
                        )
                    )
                )
            )
        )
        val clipboard = TablatureEditing.copySelectedNotes(
            measures,
            TabNoteSelection().single(TabNoteRef(0, 0, 2))
        )
        assertEquals(1, clipboard.notes.size)
        val copied = clipboard.notes.first().pluck
        assertTrue(copied.has(com.grafitto.laudate.domain.model.tablature.TabTechnique.HAMMER))
        assertTrue(copied.has(com.grafitto.laudate.domain.model.tablature.TabTechnique.VIBRATO))
        assertEquals(com.grafitto.laudate.domain.model.tablature.TabFrettingFinger.INDEX, copied.frettingFinger)
    }

    @Test
    fun copy_isEmptyWithoutSelection() {
        assertTrue(
            TablatureEditing.copySelectedNotes(measuresWithNotes(), TabNoteSelection()).isEmpty
        )
    }

    @Test
    fun paste_neverWritesOutsideTheDocument() {
        val measures = measuresWithNotes()
        val clipboard = TablatureEditing.copySelectedNotes(
            measures,
            TabNoteSelection.ofMeasure(measures, 0)
        )
        // Se pega en el **último** compás: lo que se saldría del documento se descarta sin fallar.
        val pasted = TablatureEditing.pasteNotes(measures, clipboard, TabNoteRef(1, 2, 1))
        assertEquals("no se crean compases nuevos", 2, pasted.size)
    }

    // ── Rectángulo con el teclado (`Mayús`+flecha en dos dimensiones) ────────────────────────────

    @Test
    fun rectBetween_coversEveryStringBetweenTheTwoNotes() {
        // `Mayús`+`↓` desde la cuerda 1 hasta la 3: el rectángulo abarca también la **2**, que es lo
        // que `range` no hace (solo recorre el tiempo en una cuerda).
        val selection = TabNoteSelection.rectBetween(
            measuresWithNotes(),
            anchor = TabNoteRef(0, 0, 1),
            focus = TabNoteRef(0, 0, 3)
        )
        assertEquals(3, selection.size)
        assertTrue(selection.contains(TabNoteRef(0, 0, 1)))
        assertTrue(selection.contains(TabNoteRef(0, 0, 2)))
        assertTrue(selection.contains(TabNoteRef(0, 0, 3)))
    }

    @Test
    fun rectBetween_growsAndShrinksWithTheFocus() {
        val measures = measuresWithNotes()
        val anchor = TabNoteRef(0, 0, 1)
        val wide = TabNoteSelection.rectBetween(measures, anchor, TabNoteRef(0, 2, 1))
        val narrow = TabNoteSelection.rectBetween(measures, anchor, TabNoteRef(0, 1, 1))
        assertEquals(3, wide.size)
        assertEquals("volver sobre los pasos encoge la selección", 2, narrow.size)
        assertTrue(narrow.notes.all { wide.contains(it) })
    }

    @Test
    fun rectBetween_isSymmetrical() {
        // El foco puede quedar **antes** que el ancla: el rectángulo es el mismo.
        val measures = measuresWithNotes()
        val forward = TabNoteSelection.rectBetween(measures, TabNoteRef(0, 0, 1), TabNoteRef(0, 2, 2))
        val backward = TabNoteSelection.rectBetween(measures, TabNoteRef(0, 2, 2), TabNoteRef(0, 0, 1))
        assertEquals(forward, backward)
    }

    @Test
    fun rectBetween_crossesMeasures() {
        // Del compás 0 al 1: el primero se toma desde el ancla hasta su final y el segundo desde su
        // principio hasta el foco.
        val selection = TabNoteSelection.rectBetween(
            measuresWithNotes(),
            anchor = TabNoteRef(0, 1, 1),
            focus = TabNoteRef(1, 0, 1)
        )
        assertEquals(3, selection.size)
        assertTrue(selection.contains(TabNoteRef(0, 1, 1)))
        assertTrue(selection.contains(TabNoteRef(0, 2, 1)))
        assertTrue(selection.contains(TabNoteRef(1, 0, 1)))
    }

    @Test
    fun rectBetween_ignoresMeasuresThatDoNotExist() {
        // Un foco fuera del documento no revienta: se recorta a lo que hay.
        val selection = TabNoteSelection.rectBetween(
            measuresWithNotes(),
            anchor = TabNoteRef(0, 0, 1),
            focus = TabNoteRef(9, 9, 1)
        )
        assertEquals("del ancla al final del documento", 4, selection.size)
        assertTrue(selection.contains(TabNoteRef(1, 0, 1)))
    }
}
