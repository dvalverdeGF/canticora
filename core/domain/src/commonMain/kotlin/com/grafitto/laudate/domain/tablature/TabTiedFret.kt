package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.tablature.TabMeasure

/**
 * **Cifra de una nota ligada**: qué se pinta, dónde y con qué paréntesis.
 *
 * Reproduce `Note::shouldHideFret()` de MuseScore (`src/engraving/dom/note.cpp:1527-1556`) y las dos
 * decisiones que cuelgan de ella: los **paréntesis generados** de `ChordLayout::layoutNote2`
 * (`chordlayout.cpp:3166-3169`) y el **arco omitido** de `SlurTieLayout::tieSegmentShouldBeSkipped`
 * (`slurtielayout.cpp:1553-1563`).
 *
 * El original, traducido línea a línea:
 *
 * ```cpp
 * if (!tieBack() || shouldForceShowFret() || !staffType()->isTabStaff()) return false;
 * if (isContinuationOfBend() && !rtick().isZero()) return true;
 * ShowTiedFret showTiedFret = style().value(Sid::tabShowTiedFret).value<ShowTiedFret>();
 * if (showTiedFret == ShowTiedFret::TIE_AND_FRET) return false;
 * ParenthesizeTiedFret parenthTiedFret = style().value(Sid::tabParenthesizeTiedFret).value<ParenthesizeTiedFret>();
 * if (parenthTiedFret == ParenthesizeTiedFret::NEVER || !rtick().isZero()) return true;
 * if (parenthTiedFret == ParenthesizeTiedFret::START_OF_MEASURE) return false;
 * const Measure* measure = findMeasure();
 * bool isStartOfSystem = measure && measure->system() && measure->isFirstInSystem();
 * return !isStartOfSystem;
 * ```
 *
 * Las tres condiciones que aquí **no** aparecen son deliberadas:
 *
 *  - `!staffType()->isTabStaff()`: este código solo corre en un pentagrama de tablatura.
 *  - `shouldForceShowFret()`: depende de `parenthesizeTiedFretIfArticulation` y de que el acorde
 *    lleve articulaciones, línea de acorde, `Spanner` que no sea bend, bend suelto, trémolo de barra,
 *    vibrato o bend de tipo *dip*. Canticora **no** tiene `Spanner` ni bends encadenados, y las
 *    articulaciones que sí tiene (púa, *hammer*, *pull*, armónico…) no son las que MuseScore mira, así
 *    que la condición nunca se cumpliría. Se documenta en vez de fingir que existe.
 *  - `isContinuationOfBend()`: no hay bends encadenados en el modelo.
 */
object TabTiedFret {

    /**
     * `true` si la cifra de la nota ligada **no** debe pintarse.
     *
     * @param tiedFromPrevious la nota **recibe** una ligadura (el `tieBack()` de MuseScore).
     * @param atMeasureStart la nota cae en la **primera** columna de su compás (`rtick().isZero()`).
     * @param opensSystem el compás de la nota es el **primero** de su sistema
     *   (`measure->isFirstInSystem()`).
     */
    fun shouldHideFret(
        tiedFromPrevious: Boolean,
        atMeasureStart: Boolean,
        opensSystem: Boolean,
        showTiedFret: ShowTiedFret,
        parenthesize: ParenthesizeTiedFret
    ): Boolean {
        if (!tiedFromPrevious) return false
        if (showTiedFret == ShowTiedFret.TIE_AND_FRET) return false
        if (parenthesize == ParenthesizeTiedFret.NEVER || !atMeasureStart) return true
        if (parenthesize == ParenthesizeTiedFret.START_OF_MEASURE) return false
        return !opensSystem
    }

    /**
     * `true` si la cifra, además de verse, va **entre paréntesis**: el `tieBackParen` de
     * `ChordLayout::layoutNote2` (`chordlayout.cpp:3166-3169`). Solo pasa con [ShowTiedFret.TIE] y
     * solo cuando la cifra se ve.
     */
    fun shouldParenthesize(
        tiedFromPrevious: Boolean,
        atMeasureStart: Boolean,
        opensSystem: Boolean,
        showTiedFret: ShowTiedFret,
        parenthesize: ParenthesizeTiedFret
    ): Boolean = tiedFromPrevious &&
        showTiedFret != ShowTiedFret.TIE_AND_FRET &&
        !shouldHideFret(tiedFromPrevious, atMeasureStart, opensSystem, showTiedFret, parenthesize)

    /**
     * `true` si el **arco** de la ligadura se dibuja. `SlurTieLayout::tieSegmentShouldBeSkipped`
     * (`slurtielayout.cpp:1553-1563`) lo omite con [ShowTiedFret.NONE]: si la cifra no se ve y el arco
     * tampoco, la ligadura desaparece del grabado.
     */
    fun shouldDrawTie(showTiedFret: ShowTiedFret): Boolean = showTiedFret != ShowTiedFret.NONE

    /**
     * Posiciones que **reciben** una ligadura de prolongación, en **una sola pasada hacia delante**.
     *
     * Es el equivalente barato de [TablatureEditing.isTiedFromPrevious] para la maquetación: en vez de
     * mirar hacia atrás desde cada nota —lo que sería cuadrático— se recuerda, por número de cuerda, si
     * la **última** nota vista dejó la ligadura abierta. El resultado es el mismo, incluido el caso de
     * una ligadura que **cruza de compás** y el de una que **cruza de sistema**.
     */
    fun tiedFromPrevious(measures: List<TabMeasure>): Set<TabTieKey> {
        val result = mutableSetOf<TabTieKey>()
        val pending = mutableMapOf<Int, Boolean>()
        measures.forEachIndexed { measureIndex, measure ->
            measure.slots.forEachIndexed { slotIndex, slot ->
                slot.strings.forEach { pluck ->
                    if (pending[pluck.stringNumber] == true) {
                        result += TabTieKey(measureIndex, slotIndex, pluck.stringNumber)
                    }
                    pending[pluck.stringNumber] = pluck.tiedToNext
                }
            }
        }
        return result
    }
}

/**
 * **Posición de una nota** dentro de la tablatura: compás, columna y cuerda. Es la clave con la que
 * [TabTiedFret.tiedFromPrevious] marca las notas que reciben una ligadura.
 */
data class TabTieKey(val measureIndex: Int, val slotIndex: Int, val stringNumber: Int)
