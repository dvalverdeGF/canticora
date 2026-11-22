package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique

/**
 * **Apilado de los símbolos de una nota** (parte de la maquetación pura, ADR-017).
 *
 * Una nota puede llevar **varias técnicas a la vez** (liga + slide, bend + vibrato…) y cada una tiene
 * su símbolo. Antes se **repartían en fila** a los lados de la cifra, y eso obligaba a **ensanchar la
 * columna** para que no se pisaran: el ancho de la tablatura **cambiaba** al añadir un símbolo, que es
 * justo lo que no puede pasar (el pentagrama se «movía» al editar).
 *
 * Ahora **todos** los símbolos que van sobre la cuerda —el arco de púa, las articulaciones y los glifos
 * de técnica— forman **una sola pila vertical**, una franja por símbolo, igual que hace MuseScore. El
 * eje `x` es **siempre** el de la cifra, así que el ancho de la columna depende **solo de la duración**
 * de la figura y **nunca** de cuántos símbolos lleve la nota.
 *
 * Kotlin puro y determinista: la misma nota da siempre el mismo apilado.
 */
object TabSymbolPlacer {

    /** Símbolos que se dibujan **encima** de la cifra, de abajo arriba. */
    private val ABOVE_ORDER: List<TabGlyphKind> = listOf(
        TabGlyphKind.PALM_MUTE,
        TabGlyphKind.TREMOLO,
        TabGlyphKind.VIBRATO,
        TabGlyphKind.BEND,
        TabGlyphKind.BEND_DOWN,
        TabGlyphKind.PRE_BEND,
        TabGlyphKind.SLIDE,
        TabGlyphKind.HARMONIC,
        TabGlyphKind.PINCH_HARMONIC,
        TabGlyphKind.TAPPED_HARMONIC,
        TabGlyphKind.TAPPING,
        TabGlyphKind.TRILL,
        TabGlyphKind.ARPEGGIO,
        TabGlyphKind.RAKE,
        TabGlyphKind.WHAMMY_DIVE,
        TabGlyphKind.WHAMMY_SCOOP,
        TabGlyphKind.TREMOLO_BAR,
        TabGlyphKind.VIOLIN,
        TabGlyphKind.LET_RING
    )

    /** Orden de los símbolos **encima** de la cifra según su índice en [ABOVE_ORDER] (los demás, al
     *  final para que nunca queden fuera). */
    fun aboveOrderIndex(kind: TabGlyphKind): Int =
        ABOVE_ORDER.indexOf(kind).let { if (it < 0) ABOVE_ORDER.size else it }

    /** Nº de símbolos **encima** que llevaría una nota con esas técnicas (sin contar la ligadura). */
    fun aboveCount(techniques: Set<TabTechnique>): Int =
        techniques.count { technique ->
            val kind = technique.vectorialGlyph()
            kind != null && kind != TabGlyphKind.SLUR
        }

    /**
     * Nº de **franjas** que una nota ocupa **por encima de la cuerda**, de abajo arriba:
     *  1. el **arco de púa** (la técnica `PICK_*`), pegado a la cuerda;
     *  2. las **articulaciones** que se apilan (`>` `^` `𝄢`…), una por franja;
     *  3. los **glifos de técnica** (bend, vibrato, `P.M.`…), uno por franja, encima de todo.
     *
     * Es la única fuente de verdad del alto de la pila: la franja `SYMBOLS` se dimensiona con el
     * **máximo** del sistema, de modo que ninguna nota se sale por arriba.
     */
    fun stackCount(pluck: TabPluck): Int {
        val pick = if (pluck.has(TabTechnique.PICK_DOWN) || pluck.has(TabTechnique.PICK_UP)) 1 else 0
        val articulations = pluck.articulations.count { it.stacks }
        return pick + articulations + aboveCount(pluck.techniques)
    }

    /** Nº máximo de franjas apiladas de una columna (la cuerda más cargada manda). */
    fun maxStackCount(slot: TabSlot): Int =
        slot.strings.maxOfOrNull { stackCount(it) } ?: 0
}
