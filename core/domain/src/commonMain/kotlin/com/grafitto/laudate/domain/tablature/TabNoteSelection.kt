package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.tablature.TabMeasure

/**
 * **Selección de notas** del editor (Fase 2 del encargo de paridad, ADR-020).
 *
 * El editor tenía **solo** [TablatureSelection] (un rango de **compases**), suficiente para borrar o
 * copiar bloques, pero no para lo que MuseScore hace con las **notas**: seleccionar una cifra, varias
 * con `Ctrl`, o un rango con `Mayús`. Esta clase es esa selección, y es **la misma pieza de Dominio**
 * que comparten el pintor (para colorear lo seleccionado) y los tests.
 *
 * Las notas se identifican por **[TabNoteRef]** (compás, columna y cuerda), que es la misma terna con
 * la que el editor escribe: así el resaltado cae **exactamente** sobre la cifra, sin volver a medir.
 *
 * Kotlin puro: no conoce la UI ni Compose, así que el pintor y los tests comparten la misma lógica.
 */
data class TabNoteSelection(
    /** Notas seleccionadas, sin orden ni repeticiones. */
    val notes: Set<TabNoteRef> = emptySet()
) {
    /** Indica si la nota [ref] está seleccionada. */
    fun contains(ref: TabNoteRef): Boolean = ref in notes

    /** Nº de notas seleccionadas. */
    val size: Int get() = notes.size

    /** `true` si no hay nada seleccionado. */
    val isEmpty: Boolean get() = notes.isEmpty()

    /** Añade o quita la nota [ref] (el `Ctrl`+clic de MuseScore). */
    fun toggled(ref: TabNoteRef): TabNoteSelection =
        if (ref in notes) TabNoteSelection(notes - ref) else TabNoteSelection(notes + ref)

    /** Sustituye la selección por la nota [ref] (el clic simple). */
    fun single(ref: TabNoteRef): TabNoteSelection = TabNoteSelection(setOf(ref))

    /** Añade [ref] a la selección sin quitar nada (el `Mayús`+clic incremental). */
    fun plus(ref: TabNoteRef): TabNoteSelection = TabNoteSelection(notes + ref)

    companion object {
        /**
         * Selección de un **rango** de columnas dentro de un compás, en la misma cuerda: es el gesto de
         * `Mayús`+flecha, que extiende la selección a lo largo del tiempo sin cambiar de cuerda.
         */
        fun range(measureIndex: Int, fromSlot: Int, toSlot: Int, stringNumber: Int): TabNoteSelection {
            val first = minOf(fromSlot, toSlot)
            val last = maxOf(fromSlot, toSlot)
            return TabNoteSelection(
                (first..last).map { TabNoteRef(measureIndex, it, stringNumber) }.toSet()
            )
        }

        /**
         * **Rectángulo entre dos notas** (*rubber band* con el teclado): todas las notas que caen
         * entre [anchor] y [focus], en las **dos** dimensiones —columnas y cuerdas— y cruzando
         * compases si hace falta.
         *
         * Es el gesto de `Mayús`+flecha de MuseScore: la primera nota fija el **ancla** y cada
         * pulsación mueve el **foco**, de modo que la selección crece y decrece como una goma. La
         * diferencia con [range] es que [range] solo recorre el **tiempo** en una cuerda, mientras
         * que esto abarca también las cuerdas intermedias: es lo que hace falta para borrar un
         * acorde entero o para copiar un pasaje de varias cuerdas.
         *
         * El rectángulo se resuelve sobre el **modelo** y no sobre la geometría del lienzo (a
         * diferencia de [rect], que sí necesita el dibujo): con el teclado no hay píxeles, hay
         * compases, columnas y cuerdas.
         */
        fun rectBetween(
            measures: List<TabMeasure>,
            anchor: TabNoteRef,
            focus: TabNoteRef
        ): TabNoteSelection {
            val firstMeasure = minOf(anchor.measureIndex, focus.measureIndex)
            val lastMeasure = maxOf(anchor.measureIndex, focus.measureIndex)
            val firstString = minOf(anchor.stringNumber, focus.stringNumber)
            val lastString = maxOf(anchor.stringNumber, focus.stringNumber)
            // Qué extremo es el principio del barrido: si el foco está **antes** que el ancla, el
            // rectángulo se lee al revés y hay que intercambiarlos.
            val forward = anchor.measureIndex < focus.measureIndex ||
                (anchor.measureIndex == focus.measureIndex && anchor.slotIndex <= focus.slotIndex)
            val start = if (forward) anchor else focus
            val end = if (forward) focus else anchor
            val found = mutableSetOf<TabNoteRef>()
            for (measureIndex in firstMeasure..lastMeasure) {
                val measure = measures.getOrNull(measureIndex) ?: continue
                val lastSlot = measure.slots.lastIndex
                if (lastSlot < 0) continue
                val slotRange = when {
                    measureIndex == start.measureIndex && measureIndex == end.measureIndex ->
                        start.slotIndex..end.slotIndex
                    measureIndex == start.measureIndex -> start.slotIndex..lastSlot
                    measureIndex == end.measureIndex -> 0..end.slotIndex
                    else -> 0..lastSlot
                }
                for (slotIndex in slotRange) {
                    if (slotIndex !in 0..lastSlot) continue
                    for (stringNumber in firstString..lastString) {
                        found += TabNoteRef(measureIndex, slotIndex, stringNumber)
                    }
                }
            }
            return TabNoteSelection(found)
        }

        /**
         * Todas las notas del compás [measureIndex]. Es lo que selecciona MuseScore al pulsar sobre el
         * compás (o `Ctrl`+`A` sobre él).
         */
        fun ofMeasure(measures: List<TabMeasure>, measureIndex: Int): TabNoteSelection {
            val measure = measures.getOrNull(measureIndex) ?: return TabNoteSelection()
            return TabNoteSelection(
                measure.slots.flatMapIndexed { slotIndex, slot ->
                    slot.strings.map { TabNoteRef(measureIndex, slotIndex, it.stringNumber) }
                }.toSet()
            )
        }

        /** Todas las notas del documento (`Ctrl`+`A`), útil para transponer o borrar en bloque. */
        fun ofDocument(measures: List<TabMeasure>): TabNoteSelection =
            TabNoteSelection(
                measures.flatMapIndexed { measureIndex, measure ->
                    measure.slots.flatMapIndexed { slotIndex, slot ->
                        slot.strings.map { TabNoteRef(measureIndex, slotIndex, it.stringNumber) }
                    }
                }.toSet()
            )

        /**
         * **Selección por rectángulo** (*rubber band*): todas las notas cuya cifra cae dentro del
         * rectángulo `[left, right] × [top, bottom]`, en coordenadas del **lienzo** (los sistemas
         * apilados, no de un sistema suelto).
         *
         * Es el gesto de MuseScore de **arrastrar sobre un hueco vacío**: el rectángulo selecciona
         * las notas que toca, y arrastrar **sobre una cifra** mueve la nota de cuerda en vez de
         * seleccionar. La decisión de qué gesto es cuál la toma la UI con el *hit testing*; aquí solo
         * se resuelve la geometría, que es lo que comparten el pintor y los tests.
         *
         * Una nota cuenta como «dentro» si su **columna** se solapa horizontalmente con el rectángulo
         * y su **cuerda** cae entre los bordes verticales: la cifra está centrada en la línea de la
         * cuerda, así que su `y` es exactamente esa línea.
         */
        fun rect(
            systems: List<TabSystem>,
            measures: List<TabMeasure>,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float
        ): TabNoteSelection {
            val l = minOf(left, right)
            val r = maxOf(left, right)
            val t = minOf(top, bottom)
            val b = maxOf(top, bottom)
            val found = mutableSetOf<TabNoteRef>()
            systems.forEach { system ->
                val drawing = system.drawing
                // El rectángulo viene en coordenadas del lienzo; cada sistema tiene su propio origen.
                val localTop = t - system.topY
                val localBottom = b - system.topY
                if (localBottom >= 0f && localTop <= system.height) {
                    drawing.columns.forEach { (measureIndex, columns) ->
                        val measure = measures.getOrNull(measureIndex)
                        if (measure != null) {
                            columns.forEach { box ->
                                val overlapsX = box.left < r && box.left + box.width > l
                                val slot = measure.slots.getOrNull(box.slotIndex)
                                if (overlapsX && slot != null) {
                                    slot.strings.forEach { pluck ->
                                        val y = drawing.gridTop +
                                            (pluck.stringNumber - 1) * drawing.stringSpacing
                                        if (y >= localTop && y <= localBottom) {
                                            found += TabNoteRef(
                                                measureIndex, box.slotIndex, pluck.stringNumber
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return TabNoteSelection(found)
        }
    }
}

/**
 * Referencia a **una nota** de la tablatura: compás, columna y cuerda. Es la identidad estable de una
 * cifra para la selección, el portapapeles de notas y el resaltado del pintor.
 */
data class TabNoteRef(val measureIndex: Int, val slotIndex: Int, val stringNumber: Int)

/**
 * **Portapapeles de notas**: las notas seleccionadas y sus datos, listas para pegar en otra posición.
 * Se guarda fuera del estado persistente (no viaja al JSON ni a la base de datos).
 */
data class TabNoteClipboard(
    val notes: List<TabNoteClipboardEntry>,
    /** Compás y columna del primer elemento copiado: la referencia para pegar con desplazamiento. */
    val anchor: TabNoteRef
) {
    val isEmpty: Boolean get() = notes.isEmpty()
}

/** Una nota copiada: dónde estaba y qué era (la pulsación completa, con técnicas y duración). */
data class TabNoteClipboardEntry(
    val measureOffset: Int,
    val slotOffset: Int,
    val pluck: com.grafitto.laudate.domain.model.tablature.TabPluck,
    val rhythmicFigure: com.grafitto.laudate.domain.model.rhythm.RhythmFigure?
)
