package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import com.grafitto.laudate.domain.model.tablature.TabTupletBracketType

/**
 * **Geometría de plicas y barras de unión** (roadmap **C.2**, arregla F-03).
 *
 * Hasta ahora la plica y las barras se trazaban con grosores «a ojo» (`unit * 0.17f`,
 * `unit * 0.52f`) que **no** coinciden con la geometría de MuseScore: los trazos delataban la
 * tablatura a simple vista. MuseScore no los inventa: usa los **valores de grabado estándar**
 * (`engravingDefaults` de SMuFL, la especificación que comparten Bravura y el resto de fuentes
 * musicales). Aquí van esos valores y el cálculo de la geometría, en **Kotlin puro**:
 *
 *  - la **plica** nace en el borde del pentagrama/figura y cuelga `3.5` espacios;
 *  - las **barras de unión** miden `0.5` espacios de grosor y `0.25` espacios de separación entre
 *    barras del mismo haz;
 *  - el **lado de anclaje** de la plica sigue la convención: la plica hacia **arriba** se ancla a la
 *    **derecha** de la figura y la que cae hacia **abajo**, a la **izquierda**.
 *
 * `unit` es **un espacio de pentagrama** en px (la maquetación usa `6.5 * scale`, que es la misma
 * unidad de los demás trazos), de modo que los grosores son proporcionales a la escala del pentagrama
 * y la geometría es idéntica en pantalla y en el PDF.
 */
object TabRhythmGeometry {

    /**
     * **Separación entre cuerdas**, en espacios de pentagrama. Lo dice el propio fichero de MuseScore
     * (`<lineDistance>1.5</lineDistance>`): las cuerdas distan **1,5 espacios**. Es la razón por la que
     * la plica y las barras deben medirse en **espacios** (no en la separación de cuerdas), o la fila
     * de ritmo sale deformada.
     */
    const val STRING_SPACING_SPACES: Float = 1.5f

    /**
     * Valores de **`engravingDefaults` de SMuFL**, en **espacios de pentagrama**, **corregidos con
     * las medidas reales del `salmo2.pdf`** (2026-10-12). No son inventados ni «a ojo»: la plica del
     * PDF mide 0,097 espacios de grosor y 3,99 de longitud (medido por píxeles a 300 dpi).
     */
    const val STEM_THICKNESS: Float = 0.10f

    /** Grosor de una **barra de unión** (beam). */
    const val BEAM_THICKNESS: Float = 0.5f

    /** Separación **entre** barras del mismo haz (de la segunda en adelante). */
    const val BEAM_SPACING: Float = 0.25f

    /**
     * Longitud de la **plica** de tablatura, en espacios de pentagrama.
     *
     * **Corregido el 2026-10-15 contra el código fuente de MuseScore** (`StemLayout::tabStemLength`
     * en `src/engraving/rendering/score/stemlayout.cpp`): con las plicas **al lado** del pentagrama
     * (`stemsThrough = 0`, que es el caso del `salmo2`) la longitud es **fija** e igual a
     * `STAFFTYPE_TAB_DEFAULTSTEMLEN_DN = 3.0` espacios (×1,25 si `useWideBeams`). Antes valía 3,99
     * —una medición a ojo que incluía el hueco de arranque— y la plica salía **demasiado larga**.
     *
     * Medido del `salmo2.pdf` (300 dpi, 1 espacio = 20,67 px): las plicas del primer sistema van de
     * y = 712 a y = 773 (M1) y de y = 714 a y = 771 (M3), es decir **2,76-2,95 espacios** de trazo.
     */
    const val STEM_LENGTH: Float = 3.0f

    /**
     * **Hueco entre la última cuerda y el arranque de la plica**, en espacios de pentagrama.
     *
     * **Corregido el 2026-10-15 contra el código fuente de MuseScore** (`StemLayout::tabRestStemPosY`):
     * con las plicas hacia abajo y al lado del pentagrama, el arranque es
     * `(lines - 1) * lineDistance + STAFFTYPE_TAB_DEFAULTSTEMPOSY_DN` = `(6 - 1) * 1,5 + 1,0` =
     * **8,5 espacios desde la primera cuerda**, o sea **1,0 espacio por debajo de la cuerda 6**.
     *
     * Antes la plica nacía **en** la cuerda 6 (offset 0) y se pegaba a la cifra: el usuario lo
     * describió como «la plica está muy cerca de la 6 cuerda». Medido del `salmo2.pdf`: 22-24 px
     * (1,06-1,16 espacios) de hueco.
     */
    const val STEM_TOP_GAP_SPACES: Float = 1.0f

    /**
     * **Proporción de la plica corta** de la blanca, en el estilo [MinimStyle.SHORTER].
     *
     * Es `STAFFTYPE_TAB_SHORTSTEMRATIO = 0.5` (`src/engraving/rendering/score/stemlayout.h:48`): la
     * plica de la blanca mide **la mitad** que la de las demás figuras.
     */
    const val SHORT_STEM_RATIO: Float = 0.5f

    /**
     * **Ancho** de cada barra inclinada de la blanca rayada, en espacios.
     *
     * `STAFFTYPE_TAB_SLASH_WIDTH = 1.2` (`stemlayout.h:52`).
     */
    const val SLASH_WIDTH: Float = 1.2f

    /**
     * **Inclinación** de la barra: cuánto baja su borde **izquierdo** respecto del derecho, en
     * espacios. `STAFFTYPE_TAB_SLASH_SLANTY = 0.8` (`stemlayout.h:53`).
     */
    const val SLASH_SLANT: Float = 0.8f

    /** **Grosor** vertical de la barra, en espacios. `STAFFTYPE_TAB_SLASH_THICK = 0.4` (`stemlayout.h:54`). */
    const val SLASH_THICKNESS: Float = 0.4f

    /**
     * **Separación** entre las dos barras de la blanca rayada, en espacios.
     * `STAFFTYPE_TAB_SLASH_DISPL = 0.8` (`stemlayout.h:55`).
     */
    const val SLASH_DISPLACEMENT: Float = 0.8f

    /**
     * **Altura total** de la pareja de barras, en espacios:
     * `STAFFTYPE_TAB_SLASH_2TOTHEIGHT = THICK + DISPL + SLANTY = 2,0` (`stemlayout.h:58`).
     */
    const val SLASH_PAIR_HEIGHT: Float = SLASH_THICKNESS + SLASH_DISPLACEMENT + SLASH_SLANT

    /**
     * Arranque de la pareja de barras en una plica **hacia arriba**, medido desde el **extremo
     * libre** de la plica: `STAFFTYPE_TAB_SLASH_2STARTY_UP = (3,0 − 2,0) / 2 = 0,5` (`stemlayout.h:61`).
     */
    const val SLASH_PAIR_START_UP: Float = (STEM_LENGTH - SLASH_PAIR_HEIGHT) * 0.5f

    /**
     * Arranque de la pareja de barras en una plica **hacia abajo**, medido desde el **arranque** de
     * la plica: `STAFFTYPE_TAB_SLASH_2STARTY_DN = (3,0 + 2,0) / 2 = 2,5` (`stemlayout.h:63`).
     */
    const val SLASH_PAIR_START_DOWN: Float = (STEM_LENGTH + SLASH_PAIR_HEIGHT) * 0.5f

    /**
     * Una **barra inclinada** rellena: el paralelogramo con el que MuseScore marca la blanca en el
     * estilo [MinimStyle.SLASHED] (`TDraw::draw(const Stem*)`, `tdraw.cpp:2887-2905`).
     *
     * El paralelogramo tiene el borde **derecho** vertical de [y] a `y + thickness` y el **izquierdo**
     * de `y + slant` a `y + thickness + slant`: la barra sube hacia la derecha.
     */
    data class Slash(
        /** Centro horizontal del paralelogramo. */
        val x: Float,
        /** `y` del **borde superior derecho**. */
        val y: Float,
        /** Ancho total, de borde izquierdo a borde derecho. */
        val width: Float,
        /** Grosor vertical. */
        val thickness: Float,
        /** Cuánto baja el borde **izquierdo** respecto del derecho. */
        val slant: Float
    )

    /**
     * **Corchete de tresillo**, medido del `salmo2.pdf` (2026-10-14). En MuseScore el tresillo lleva
     * una **línea** que agrupa sus figuras, con un **gancho** hacia abajo en cada extremo, y la cifra
     * (`3`) **encima de la línea**, que se **interrumpe** a su paso.
     *
     * Medido del PDF (pentagrama de 6 cuerdas, primera línea en y = 535, 1 espacio = 20,67 px):
     * la línea va en y = 508,5 (**1,28 espacios** por encima de la primera cuerda), los ganchos bajan
     * **1,35 espacios** y el corchete vuela **0,63 espacios** más allá de la primera y la última figura.
     */
    data class TupletBracket(
        /** Extremo **izquierdo** de la línea. */
        val x1: Float,
        /** Extremo **derecho** de la línea. */
        val x2: Float,
        /** `y` de la **línea** (los ganchos bajan desde aquí). */
        val y: Float,
        /** Longitud de los **ganchos** hacia abajo. */
        val hook: Float,
        /** Grosor del trazo. */
        val thickness: Float,
        /**
         * **Rótulo** que se pinta sobre la línea, con el hueco que se le abre. Sale de
         * [TabTuplet.label] según su `numberType`: `3` con `SHOW_NUMBER`, `3:2` con `SHOW_RELATION` y
         * **vacío** con `NO_TEXT` (entonces no se abre hueco ni se pinta cifra).
         */
        val label: String = "3",
        /**
         * `false` si el grupo **no lleva corchete** y solo se rotula la cifra. Es el resultado de
         * `Tuplet::calcHasBracket` (`tuplet.cpp:203`): con `AUTO_BRACKET` MuseScore omite el corchete
         * cuando el grupo ocupa **exactamente** una barra de unión y la cifra cae del lado de la
         * plica —el caso normal de un tresillo de corcheas dentro de un pulso—.
         */
        val hasBracket: Boolean = true
    )

    /** Vuelo del corchete de tresillo **por encima de la primera cuerda**, en espacios. */
    const val TUPLET_ABOVE_SPACES: Float = 1.28f

    /**
     * Longitud de los **ganchos** del corchete de tresillo, en espacios.
     *
     * **Medido del `salmo2.pdf`** (2026-10-14, 300 dpi, 1 espacio = 20,67 px): los ganchos bajan de
     * `y = 508` a `y = 536`, es decir **28 px = 1,35 espacios**. El valor anterior (0,73) dejaba los
     * ganchos a media asta y el corchete no se leía como el de MuseScore.
     */
    const val TUPLET_HOOK_SPACES: Float = 1.35f

    /** Vuelo del corchete **más allá** de la primera y la última figura del grupo, en espacios. */
    const val TUPLET_OVERHANG_SPACES: Float = 0.63f

    /**
     * **Media altura de la cifra** del grupo irregular, en espacios de pentagrama.
     *
     * La cifra se centra **en la línea** del corchete (`tupletAlign = Align(HCENTER, VCENTER)` en
     * `styledef.cpp`), así que su tinta sube **media altura** por encima de la línea. Medido del
     * `salmo2.pdf` (300 dpi, 1 espacio = 20,67 px): el `3` del tresillo ocupa y = 498–522, es decir
     * **25 px = 1,21 espacios** de alto → media altura **0,605 espacios**.
     *
     * Sirve para que la franja [TabZone.TOP] reserve el aire que la cifra necesita: sin él la línea
     * del corchete caía en el borde del lienzo y la cifra salía **cortada por la mitad**.
     */
    const val TUPLET_NUMBER_HALF_SPACES: Float = 0.61f

    /** **Dirección** de la plica (`stemsDown` de MuseScore). */
    enum class StemDirection { UP, DOWN }

    /** **Plica** ya resuelta: desde su arranque ([yStart]) hasta su extremo ([yEnd]). */
    data class Stem(
        /** `x` de la plica (el lado de la figura donde se ancla). */
        val x: Float,
        val yStart: Float,
        val yEnd: Float,
        val direction: StemDirection,
        /** Grosor del trazo ([STEM_THICKNESS] · unit). */
        val thickness: Float
    )

    /**
     * Una **barra de unión** (beam): una línea gruesa de [thickness] centrada en la recta
     * (startX,startY)–(endX,endY). Va ligeramente **inclinada** si las notas del grupo están a
     * distinta altura, como en una partitura.
     */
    data class Beam(
        val startX: Float,
        val endX: Float,
        val startY: Float,
        val endY: Float,
        val thickness: Float
    )

    /**
     * Nº de **corchetes** de una figura: `0` sin plica con bandera (redonda, blanca, negra),
     * `1` corchea, `2` semicorchea. Los **silencios** no llevan plica.
     */
    fun flagCount(figure: RhythmFigure): Int = when (figure) {
        RhythmFigure.EIGHTH, RhythmFigure.DOTTED_EIGHTH -> 1
        RhythmFigure.SIXTEENTH -> 2
        else -> 0
    }

    /**
     * `true` si la figura lleva **plica**. La **redonda** no lleva (no tiene plica) y los
     * **silencios** tampoco; la blanca sí (plica sin bandera).
     */
    fun hasStem(figure: RhythmFigure): Boolean =
        !figure.isRest && figure != RhythmFigure.WHOLE

    /**
     * **Línea del pentagrama** donde se ancla un silencio, contada **desde la cuerda superior**
     * (índice `0` = primera cuerda).
     *
     * Es la regla de MuseScore (`RestLayout::computeNaturalLine` + `computeWholeOrBreveRestOffset`):
     * la línea «natural» es `floor(lines/2)` con un número **impar** de líneas y `ceil(lines/2)` con
     * un número **par**; el **silencio de redonda** sube **una línea** más (pende de la línea de
     * arriba en vez de apoyarse en la de abajo).
     *
     * Medido del `salmo2.pdf` (pentagrama de 6 cuerdas, líneas en y = 535/566/597/628/659/690): el
     * silencio de blanca del tresillo apoya su tinta en la **4.ª** línea (índice 3) y el de redonda
     * cuelga de la **3.ª** (índice 2). ✓ Antes se centraban en el pentagrama, que en 6 cuerdas cae
     * **entre** dos líneas y no coincide con ninguna.
     */
    fun restLine(lines: Int, isWholeRest: Boolean): Int {
        if (lines <= 0) return 0
        val natural = if (lines % 2 == 1) lines / 2 else (lines + 1) / 2
        return if (isWholeRest) natural - 1 else natural
    }

    /** Lado de anclaje de la plica: derecha si sube, izquierda si baja (convención de grabado). */
    fun stemSideOffset(direction: StemDirection, unit: Float): Float = when (direction) {
        StemDirection.UP -> unit * 0.30f
        StemDirection.DOWN -> -unit * 0.30f
    }

    /**
     * Geometría de la **plica** de una figura, en coordenadas del `TabRhythmMark` ([headY] es la `y`
     * de la cabeza). `null` si la figura no lleva plica ([hasStem]).
     *
     * Con la plica hacia **abajo** (el caso de la tablatura: `stemsDown`), el trazo **no nace en la
     * cabeza** sino [topGap] espacios más abajo, como en MuseScore (`StemLayout::tabRestStemPosY`:
     * `(lines - 1) * lineDistance + 1,0`). En Canticora [headY] es la **línea de la cuerda 6** (el
     * borde inferior del pentagrama), así que el hueco es exactamente [STEM_TOP_GAP_SPACES].
     *
     * [topGap] se pone a `0` cuando la figura lleva **cabeza dibujada** (`Note symbols`), porque
     * entonces la plica sí debe salir de la cabeza.
     */
    fun stemOf(
        figure: RhythmFigure,
        x: Float,
        headY: Float,
        unit: Float,
        direction: StemDirection,
        topGap: Float = STEM_TOP_GAP_SPACES,
        /**
         * Cómo se dibuja la **blanca** ([MinimStyle]). Con [MinimStyle.NONE] la blanca **no lleva
         * plica** (`Chord::shouldHaveStem()`, `chord.cpp:1074`); con [MinimStyle.SHORTER] la plica
         * mide [SHORT_STEM_RATIO] y su arranque se desplaza lo mismo, de modo que el **extremo libre
         * queda donde el de una plica normal** (`StemLayout::tabStemLength` y `tabRestStemPosY`,
         * `stemlayout.cpp:266-301`).
         */
        minimStyle: MinimStyle = MinimStyle.SHORTER
    ): Stem? {
        if (!hasStem(figure)) return null
        val isHalf = figure == RhythmFigure.HALF || figure == RhythmFigure.DOTTED_HALF
        if (isHalf && minimStyle == MinimStyle.NONE) return null
        val short = isHalf && minimStyle == MinimStyle.SHORTER
        val stemX = x + stemSideOffset(direction, unit)
        val length = unit * STEM_LENGTH * (if (short) SHORT_STEM_RATIO else 1f)
        // El desplazamiento del arranque es exactamente lo que se le quita a la plica: el extremo
        // libre no se mueve, solo se acorta por el lado de la cabeza.
        val shift = unit * STEM_LENGTH * (if (short) SHORT_STEM_RATIO else 0f)
        return when (direction) {
            StemDirection.UP -> Stem(stemX, headY - shift, headY - shift - length, direction, STEM_THICKNESS * unit)
            StemDirection.DOWN -> {
                val start = headY + unit * topGap + shift
                Stem(stemX, start, start + length, direction, STEM_THICKNESS * unit)
            }
        }
    }

    /**
     * Las **dos barras inclinadas** de la blanca en el estilo [MinimStyle.SLASHED], o vacío si la
     * figura no es una blanca o el estilo no es el rayado.
     *
     * Reproduce `TDraw::draw(const Stem*)` (`tdraw.cpp:2887-2905`): la pareja se **centra** en la
     * plica —arranca [SLASH_PAIR_START_UP] espacios por debajo del extremo libre si la plica sube, y
     * [SLASH_PAIR_START_DOWN] por debajo del arranque si baja— y la segunda barra va
     * [SLASH_DISPLACEMENT] espacios más abajo que la primera.
     */
    fun slashesOf(
        figure: RhythmFigure,
        x: Float,
        headY: Float,
        unit: Float,
        direction: StemDirection,
        topGap: Float = STEM_TOP_GAP_SPACES,
        minimStyle: MinimStyle = MinimStyle.SHORTER
    ): List<Slash> {
        val isHalf = figure == RhythmFigure.HALF || figure == RhythmFigure.DOTTED_HALF
        if (!isHalf || minimStyle != MinimStyle.SLASHED) return emptyList()
        val stemX = x + stemSideOffset(direction, unit)
        val first = when (direction) {
            StemDirection.UP -> headY - unit * (STEM_LENGTH - SLASH_PAIR_START_UP)
            StemDirection.DOWN -> headY + unit * topGap + unit * (STEM_LENGTH - SLASH_PAIR_START_DOWN)
        }
        return List(2) { index ->
            Slash(
                x = stemX,
                y = first + unit * SLASH_DISPLACEMENT * index,
                width = unit * SLASH_WIDTH,
                thickness = unit * SLASH_THICKNESS,
                slant = unit * SLASH_SLANT
            )
        }
    }

    /**
     * **Corchetes de tresillo** de un sistema: uno por cada **racha contigua** de figuras marcadas
     * como tresillo dentro del **mismo compás**.
     *
     * [staffTop] es la `y` de la **primera cuerda** (el borde superior del pentagrama): el corchete
     * vuela [TUPLET_ABOVE_SPACES] por encima de ella, como en MuseScore (con las plicas hacia abajo,
     * la cifra del tresillo va **arriba**, al lado contrario de las plicas).
     */
    fun tupletBracketsOf(
        marks: List<TabRhythmMark>,
        unit: Float,
        staffTop: Float,
        /**
         * **Grupos de barras de unión** de [marks] ([TabBeamGrouping.groupsOf]), como índices sobre la
         * propia lista. Solo los necesita la regla `AUTO_BRACKET` de MuseScore, que **omite** el
         * corchete cuando el grupo irregular ocupa exactamente una barra. Si se omite, se asume que
         * ningún grupo coincide con una barra y todos llevan corchete.
         */
        beamGroups: List<List<Int>> = emptyList()
    ): List<TupletBracket> {
        val result = mutableListOf<TupletBracket>()
        // Índice de marca → grupo de barra al que pertenece, para resolver `AUTO_BRACKET` sin recorrer
        // la lista de grupos por cada grupo irregular.
        val beamOf: Map<Int, Int> = buildMap {
            beamGroups.forEachIndexed { groupIndex, group -> group.forEach { put(it, groupIndex) } }
        }
        var i = 0
        while (i < marks.size) {
            val tuplet = marks[i].tuplet
            if (tuplet == null) {
                i++
                continue
            }
            // La racha se corta al cambiar de compás **o de proporción**: un quintillo pegado a un
            // tresillo son dos grupos distintos y llevan dos corchetes, como en MuseScore.
            val measure = marks[i].measureIndex
            var last = i
            while (last + 1 < marks.size &&
                marks[last + 1].tuplet == tuplet &&
                marks[last + 1].measureIndex == measure
            ) {
                last++
            }
            val overhang = unit * TUPLET_OVERHANG_SPACES
            result += TupletBracket(
                x1 = marks[i].x - overhang,
                x2 = marks[last].x + overhang,
                y = staffTop - unit * TUPLET_ABOVE_SPACES,
                hook = unit * TUPLET_HOOK_SPACES,
                thickness = STEM_THICKNESS * unit,
                label = tuplet.label,
                hasBracket = hasBracket(tuplet, marks, i, last, beamOf)
            )
            i = last + 1
        }
        return result
    }

    /**
     * **¿Lleva corchete este grupo irregular?** Es `Tuplet::calcHasBracket`
     * (`src/engraving/dom/tuplet.cpp:203-279`), traducido a la tablatura.
     *
     *  - `SHOW_BRACKET` / `SHOW_NO_BRACKET` mandan sobre todo lo demás.
     *  - Con `AUTO_BRACKET`, un grupo **degenerado** (una sola figura) no lleva corchete.
     *  - Si el grupo **no** ocupa exactamente una barra de unión, lleva corchete: es lo que pasa
     *    cuando cruza un cambio de pulso, un silencio o un compás.
     *  - Si la ocupa **entera**, MuseScore mira el lado de la cifra (`headSide`). En tablatura las
     *    plicas van **hacia abajo** (`stemsDown`), así que la cifra va **arriba**, del lado contrario
     *    a la plica: `headSide` es `false` y la condición `!headSide` se cumple **siempre**. Por eso
     *    aquí la regla se reduce a «el grupo ocupa exactamente una barra → sin corchete», que es
     *    justo lo que se ve en el `salmo2.pdf`: el tresillo de corcheas se rotula con el `3` y **sin**
     *    corchete.
     *
     * La segunda mitad de `calcHasBracket` (los cortes de barra de 32.ª/64.ª, `calcBeamBreaks`) no se
     * reproduce: exige el modelo de barras parciales de MuseScore, que la tablatura no tiene.
     */
    private fun hasBracket(
        tuplet: TabTuplet,
        marks: List<TabRhythmMark>,
        first: Int,
        last: Int,
        beamOf: Map<Int, Int>
    ): Boolean = when (tuplet.bracketType) {
        TabTupletBracketType.SHOW_BRACKET -> true
        TabTupletBracketType.SHOW_NO_BRACKET -> false
        TabTupletBracketType.AUTO_BRACKET -> {
            if (first == last) {
                false
            } else {
                val group = beamOf[first]
                val spansOneBeam = group != null &&
                    group == beamOf[last] &&
                    (first..last).all { beamOf[it] == group }
                !spansOneBeam
            }
        }
    }

    /**
     * **Barras de unión** (beams) de un grupo de marcas contiguas: una por cada corchete de la figura
     * más «densa» del grupo. La primera barra nace en el **extremo de la plica** y las siguientes se
     * apilan con [BEAM_THICKNESS] + [BEAM_SPACING].
     * Devuelve vacío si el grupo no tiene al menos **dos** notas con plica (una sola nota se dibuja
     * con su **bandera**, no con barra).
     */
    fun beamsOf(
        group: List<TabRhythmMark>,
        unit: Float,
        direction: StemDirection,
        topGap: Float = STEM_TOP_GAP_SPACES
    ): List<Beam> {
        val withStem = group.sortedBy { it.x }.filter { hasStem(it.figure) }
        if (withStem.size < 2) return emptyList()
        val beamCount = withStem.maxOf { flagCount(it.figure) }
        if (beamCount == 0) return emptyList()

        val first = withStem.first()
        val last = withStem.last()
        val firstStem = stemOf(first.figure, first.x, first.y, unit, direction, topGap) ?: return emptyList()
        val lastStem = stemOf(last.figure, last.x, last.y, unit, direction, topGap) ?: return emptyList()

        val thickness = BEAM_THICKNESS * unit
        val spacing = BEAM_SPACING * unit
        // El **borde exterior** de la barra coincide con el **extremo de la plica** y las barras
        // sucesivas se apilan **hacia fuera** (hacia el pie si la plica baja, hacia arriba si sube).
        //
        // Medido del `salmo2.pdf` (300 dpi, 1 espacio = 20,67 px, plica de y = 712 a y = 773): en un
        // grupo de **semicorcheas** las dos barras van de y = 764 a 773 y de y = 748 a 757, es decir
        // la primera **por dentro** del extremo de la plica (no por fuera) y la segunda a
        // `beamThickness + beamSpacing` = 0,75 espacios de la primera. Antes la barra se dibujaba
        // **por fuera** del extremo y el grupo entero salía 10 px (media barra) demasiado abajo.
        val sign = if (direction == StemDirection.DOWN) 1f else -1f
        return (0 until beamCount).map { index ->
            val shift = index * (thickness + spacing) + thickness / 2f
            Beam(
                startX = firstStem.x,
                endX = lastStem.x,
                startY = firstStem.yEnd - sign * shift,
                endY = lastStem.yEnd - sign * shift,
                thickness = thickness
            )
        }
    }
}
