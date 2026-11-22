package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.tablature.TabRhythmGeometry.StemDirection

/**
 * Una **primitiva de dibujo** de la fila de ritmo de la tablatura, ya en **píxeles del sistema**.
 *
 * Existe porque la fila de ritmo se pinta en **tres** sitios que no comparten motor gráfico: el
 * lienzo de Compose ([TabRhythmPainter] en `core:ui`), el PDF de escritorio (PDFBox) y el PDF de
 * Android (`android.graphics.pdf`). Si cada uno midiera la plica, la barra de unión y el corchete
 * del grupo por su cuenta, tendríamos **tres** grabados distintos del mismo compás y se
 * desincronizarían al primer cambio. Con las primitivas, la **geometría se decide una vez** en el
 * Dominio y cada consumidor solo sabe trazar líneas, puntos y glifos.
 *
 * Es la misma división de responsabilidades que ya usan los grupos de barras
 * ([TabBeamGrouping]) y los corchetes de grupo ([TabRhythmGeometry.tupletBracketsOf]): el Dominio
 * decide **dónde**, el pintor decide **con qué**.
 */
sealed interface TabRhythmPrimitive {

    /** Un **trazo recto** de grosor [thickness] (plica, barra de unión, línea del corchete, gancho). */
    data class Line(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val thickness: Float
    ) : TabRhythmPrimitive

    /** El **puntillo** de aumento: un círculo relleno de radio [radius] centrado en `(x, y)`. */
    data class Dot(val x: Float, val y: Float, val radius: Float) : TabRhythmPrimitive

    /**
     * Una **barra inclinada** rellena: el paralelogramo con el que se marca la blanca en el estilo
     * `SLASHED` ([MinimStyle.SLASHED]).
     *
     * No es un [Line] porque MuseScore no traza un segmento: rellena un **paralelogramo** con el
     * borde izquierdo más bajo que el derecho (`TDraw::draw(const Stem*)`, `tdraw.cpp:2887-2905`).
     * Trazarlo como línea gruesa daría un rectángulo sin inclinación y la blanca no se leería.
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
    ) : TabRhythmPrimitive

    /**
     * Un **glifo de la fuente musical** (SMuFL), con su **línea base** ya resuelta.
     *
     * El consumidor solo tiene que medirlo con su propia fuente, ponerlo a [emPx] y colocar su
     * **línea base** en [baselineY]. Anclar por línea base —y no por el centro de la caja de
     * línea— es lo que hace que el silencio caiga en su cuerda y el corchete quede pegado a la
     * plica: la caja de línea de Bravura mide `ascent + descent = 4,024 em`, así que centrar por
     * caja deja el glifo fuera de sitio.
     */
    data class Glyph(
        val glyph: String,
        /** Centro horizontal del glifo, salvo que [leftShiftFraction] diga otra cosa. */
        val x: Float,
        val baselineY: Float,
        val emPx: Float,
        /**
         * Desplazamiento del **borde izquierdo** respecto de [x], como **fracción del ancho** del
         * glifo. `0` = el glifo va **centrado** en [x] (silencios, cabezas, cifras de grupo);
         * `0,06` = el borde izquierdo cae un 6 % del ancho a la izquierda de [x], que es cómo el
         * corchete se **pega** a la plica en vez de quedar centrado en ella.
         */
        val leftShiftFraction: Float = 0f
    ) : TabRhythmPrimitive

    /**
     * El **corchete de un grupo irregular**: la línea que agrupa las figuras, sus dos ganchos y la
     * cifra.
     *
     * La línea se **interrumpe** a los lados de la cifra, como en MuseScore: el hueco es
     * `anchoDeLaCifra / 2 + gapPx` por cada lado. Como el ancho depende de la fuente del
     * consumidor, el hueco **no** se puede cerrar aquí: la primitiva trae [gapPx] (la parte fija,
     * en píxeles) y el consumidor suma media anchura de la cifra que haya medido.
     */
    data class Tuplet(
        val x1: Float,
        val x2: Float,
        val y: Float,
        val thickness: Float,
        /** Largo de los **ganchos** hacia abajo en los dos extremos. */
        val hook: Float,
        /** `false` con `AUTO_BRACKET` cuando el grupo ocupa una sola barra, o con `SHOW_NO_BRACKET`. */
        val hasBracket: Boolean,
        /** Glifo SMuFL de la cifra (`tuplet3`…`tuplet9`); `null` si no es un dígito o no hay cifra. */
        val labelGlyph: String?,
        /** Texto de la cifra cuando no tiene glifo (la proporción `3:2`); `null` si no toca. */
        val labelText: String?,
        /** Tamaño de la cifra, en píxeles (em de la fuente musical). */
        val labelEmPx: Float,
        /** Hueco **fijo** a cada lado de la cifra, en píxeles. */
        val gapPx: Float
    ) : TabRhythmPrimitive
}

/**
 * Traduce la fila de ritmo de un sistema a **primitivas de dibujo**.
 *
 * Es la **única** fuente de verdad de la geometría de la fila de ritmo: la usan el lienzo de
 * Compose y los dos exportadores de PDF, de modo que lo que se ve en pantalla y lo que se imprime
 * salen del mismo cálculo.
 *
 * **Lo que decide el Dominio** (y por eso está aquí, no en el pintor):
 *  - la **plica** y su dirección ([TabRhythmGeometry.stemOf]);
 *  - las **barras de unión** ([TabRhythmGeometry.beamsOf]), que dependen del compás;
 *  - el **corchete del grupo irregular** ([TabRhythmGeometry.tupletBracketsOf]), que depende de la
 *    barra de unión y de la primera cuerda;
 *  - el **anclaje** de cada glifo (por línea base, con la caja de tinta de [TabGlyphs]).
 *
 * **Lo que decide el consumidor**: con qué fuente mide, cuánto mide de ancho cada glifo y, por
 * tanto, dónde cae exactamente el hueco de la cifra del grupo.
 */
object TabRhythmPrimitives {

    /**
     * **Tamaño de los silencios** en espacios de pentagrama (em de la fuente musical).
     *
     * SMuFL dibuja los silencios a **em = 4 espacios** (el «staff space» de la fuente es 1/4 em).
     * Medido del `salmo2.pdf`: la tinta del silencio de semicorchea mide 2,8 esp de alto, que es
     * `rest16th` (0,679 em) a em = 4.
     */
    const val REST_EM: Float = 4.0f

    /**
     * **Tamaño de los corchetes** en espacios de pentagrama.
     *
     * Medido del `salmo2.pdf`: la tinta del corchete mide 3,264 esp de alto y `flag16thDown` mide
     * 0,821 em ⇒ **em = 3,97 ≈ 4**.
     */
    const val FLAG_EM: Float = 4.0f

    /**
     * **Tamaño de la cifra del grupo irregular** en espacios de pentagrama.
     *
     * Medido del `salmo2.pdf`: la cifra del grupo mide **1,35 espacios** de alto, exactamente lo
     * mismo que una cifra de traste. El glifo `tuplet3` mide 0,383 em de alto, así que el em tiene
     * que ser `1,35 / 0,383 = 3,2` espacios.
     */
    const val TUPLET_EM: Float = 3.2f

    /**
     * **Hueco** que se deja en la línea del corchete de grupo a cada lado de la cifra, en espacios.
     * Medido del `salmo2.pdf`: la línea se interrumpe 0,5 espacios antes y después del `3`.
     */
    const val TUPLET_GAP_SPACES: Float = 0.5f

    /** **Tamaño de la cabeza de nota** en espacios (solo con el tipo `Note symbols`). */
    const val NOTEHEAD_EM: Float = 1.2f

    /** **Radio del puntillo** de aumento, en espacios. */
    const val DOT_RADIUS_SPACES: Float = 0.16f

    /**
     * Construye las primitivas de la fila de ritmo.
     *
     * @param marks figuras del sistema, con su `x` y su anclaje vertical ya resueltos.
     * @param staffSpace **un espacio de pentagrama** en px (el paso de las cuerdas / 1,5). Es la
     *   unidad de la plica, los corchetes y las barras.
     * @param offsetY desplazamiento vertical que aplica el consumidor (el sistema no empieza en 0).
     * @param beamGroups **grupos de barras de unión**, como índices sobre [marks]. Vacío = ninguna
     *   figura se une (todas con corchete propio), que es lo correcto para una figura suelta.
     * @param tuplets **corchetes de grupo irregular** ya resueltos por el Dominio.
     * @param stemDirection dirección de la plica: **abajo** en el pentagrama (cuelga de la cuerda
     *   inferior); **arriba** en los iconos de la barra de herramientas (para que no se recorten).
     * @param showNoteheads dibuja la **cabeza** de nota. `false` en `Stems and beams` (MuseScore):
     *   la cabeza es la propia cifra de traste y debajo solo van las plicas y las barras.
     * @param topGap aire entre la última cuerda y el arranque de la plica, en espacios.
     */
    fun of(
        marks: List<TabRhythmMark>,
        staffSpace: Float,
        offsetY: Float = 0f,
        beamGroups: List<List<Int>> = emptyList(),
        tuplets: List<TabRhythmGeometry.TupletBracket> = emptyList(),
        stemDirection: StemDirection = StemDirection.DOWN,
        showNoteheads: Boolean = false,
        topGap: Float = TabRhythmGeometry.STEM_TOP_GAP_SPACES,
        /** Cómo se dibuja la **blanca** ([MinimStyle]): sin plica, con plica corta o rayada. */
        minimStyle: MinimStyle = MinimStyle.SHORTER
    ): List<TabRhythmPrimitive> {
        if (marks.isEmpty()) return emptyList()
        val out = ArrayList<TabRhythmPrimitive>(marks.size * 2 + tuplets.size * 5)

        // Con la cabeza dibujada (`Note symbols`) no hay barras de unión: cada figura lleva su
        // corchete propio, como en MuseScore.
        val groups = if (showNoteheads) emptyList() else beamGroups
        // Índices de las figuras que van **unidas por una barra**: esas no llevan corchete propio.
        val beamable = groups.flatten().toSet()
        // Con la cabeza dibujada la plica sale **de la cabeza**; sin ella, la plica es la «línea de
        // ritmo» de MuseScore y arranca [topGap] espacios por debajo de la última cuerda.
        val gap = if (showNoteheads) 0f else topGap

        marks.forEachIndexed { index, mark ->
            figurePrimitives(
                out = out,
                x = mark.x,
                y = mark.y + offsetY,
                figure = mark.figure,
                staffSpace = staffSpace,
                drawFlags = showNoteheads || index !in beamable,
                stemDirection = stemDirection,
                showNotehead = showNoteheads,
                topGap = gap,
                minimStyle = minimStyle
            )
        }

        groups.forEach { group ->
            val shifted = group.mapNotNull { marks.getOrNull(it)?.copy(y = marks[it].y + offsetY) }
            TabRhythmGeometry.beamsOf(shifted, staffSpace, stemDirection, gap).forEach { beam ->
                out += TabRhythmPrimitive.Line(
                    x1 = beam.startX,
                    y1 = beam.startY,
                    x2 = beam.endX,
                    y2 = beam.endY,
                    thickness = beam.thickness
                )
            }
        }

        tuplets.forEach { bracket -> out += tupletPrimitive(bracket, offsetY, staffSpace) }

        return out
    }

    /** Las primitivas de **una** figura: silencio o cabeza + plica + corchete + puntillo. */
    private fun figurePrimitives(
        out: MutableList<TabRhythmPrimitive>,
        x: Float,
        y: Float,
        figure: RhythmFigure,
        staffSpace: Float,
        drawFlags: Boolean,
        stemDirection: StemDirection,
        showNotehead: Boolean,
        topGap: Float,
        minimStyle: MinimStyle
    ) {
        // ── Silencio: glifo de la fuente, anclado a **su línea** del pentagrama ────────────────────
        // El **origen** de los glifos de silencio de SMuFL no es el mismo en todos: `restWhole`
        // cuelga **por debajo** de su origen, `restHalf` se apoya **encima** y `restQuarter`/
        // `rest8th`/`rest16th` lo llevan dentro. Por eso se ancla por **línea base** y no por tinta:
        // es lo que hace MuseScore (`RestLayout::layout` → `setPosY(finalLine * lineDist * spatium)`).
        val rest = TabGlyphs.rest(figure)
        if (rest != null) {
            out += TabRhythmPrimitive.Glyph(
                glyph = rest,
                x = x,
                baselineY = y,
                emPx = staffSpace * REST_EM
            )
            if (figure.isDotted) {
                out += TabRhythmPrimitive.Dot(
                    x = x + staffSpace * 1.2f,
                    y = y - staffSpace * 0.4f,
                    radius = staffSpace * DOT_RADIUS_SPACES
                )
            }
            return
        }

        // ── Cabeza (solo en `Note symbols`): la cifra **es** la cabeza en `Stems and beams` ───────
        if (showNotehead) {
            val head = TabGlyphs.notehead(figure) ?: return
            // La caja de línea de Bravura es simétrica (`ascent = descent = 2,012 em`), así que su
            // centro **es** la línea base: anclar la base en `y` centra la cabeza en `y`.
            out += TabRhythmPrimitive.Glyph(
                glyph = head,
                x = x,
                baselineY = y,
                emPx = staffSpace * NOTEHEAD_EM
            )
        }

        // ── Plica + corchete (geometría SMuFL, anclada a la cabeza/cuerda) ─────────────────────────
        val stem = TabRhythmGeometry.stemOf(
            figure, x, y, staffSpace, stemDirection, topGap, minimStyle
        )
        if (stem != null) {
            out += TabRhythmPrimitive.Line(
                x1 = stem.x,
                y1 = stem.yStart,
                x2 = stem.x,
                y2 = stem.yEnd,
                thickness = stem.thickness
            )
            // **Un solo corchete por figura**, y el de la **dirección correcta**: SMuFL tiene dos
            // glifos espejo por corchete (`flag8thUp`/`flag8thDown`), y el de semicorchea
            // (`flag16thDown`) **ya trae las dos banderas dibujadas**, así que se pinta una vez.
            val flagGlyph = if (drawFlags) {
                TabGlyphs.flag(figure, stemsDown = stemDirection == StemDirection.DOWN)
            } else {
                null
            }
            if (flagGlyph != null) {
                // El origen del glifo está en el **punto de unión con la plica**: basta con poner la
                // línea base en el extremo libre de la plica y el corchete queda **pegado** a ella.
                val attachY = if (stemDirection == StemDirection.DOWN) stem.yEnd else stem.yStart
                out += TabRhythmPrimitive.Glyph(
                    glyph = flagGlyph,
                    x = stem.x,
                    baselineY = attachY,
                    emPx = staffSpace * FLAG_EM,
                    leftShiftFraction = 0.06f
                )
            }
        }
        // ── Barras inclinadas de la blanca (`SLASHED`) ────────────────────────────────────────────
        TabRhythmGeometry.slashesOf(
            figure, x, y, staffSpace, stemDirection, topGap, minimStyle
        ).forEach { slash ->
            out += TabRhythmPrimitive.Slash(
                x = slash.x,
                y = slash.y,
                width = slash.width,
                thickness = slash.thickness,
                slant = slash.slant
            )
        }
        if (figure.isDotted) {
            out += TabRhythmPrimitive.Dot(
                x = x + staffSpace * 1.5f,
                y = y,
                radius = staffSpace * DOT_RADIUS_SPACES
            )
        }
    }

    /** Las primitivas del **corchete de un grupo irregular**: línea, ganchos y cifra. */
    private fun tupletPrimitive(
        bracket: TabRhythmGeometry.TupletBracket,
        offsetY: Float,
        staffSpace: Float
    ): TabRhythmPrimitive.Tuplet {
        // Con `SHOW_NUMBER` la cifra es el **numerador** de la proporción (3, 5, 6, 7, 9…) y se
        // pinta con el glifo SMuFL `tuplet3`…`tuplet9`. Con `SHOW_RELATION` es la proporción
        // completa (`3:2`), que **no** tiene glifo: se compone con la tipografía de texto. Con
        // `NO_TEXT` no hay cifra.
        val label = bracket.label
        val isDigit = label.length == 1 && label[0].isDigit()
        return TabRhythmPrimitive.Tuplet(
            x1 = bracket.x1,
            x2 = bracket.x2,
            y = bracket.y + offsetY,
            thickness = bracket.thickness,
            hook = bracket.hook,
            hasBracket = bracket.hasBracket,
            labelGlyph = if (isDigit) TabGlyphs.tupletDigit(label[0] - '0') else null,
            labelText = label.ifEmpty { null },
            labelEmPx = staffSpace * TUPLET_EM,
            gapPx = staffSpace * TUPLET_GAP_SPACES
        )
    }
}
