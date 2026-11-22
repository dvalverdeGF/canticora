package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.tablature.TabArticulation

/**
 * **Apilado vertical de las articulaciones** de una misma nota (corrección del usuario, 2026-10-12).
 *
 * MuseScore **no** pone las articulaciones **en fila** (a los lados de la cifra) como hacíamos: las
 * **apila en vertical**, una por franja, cada vez más arriba. Es lo que se ve en el `salmo2.pdf`: una
 * nota con `^` + `>` + `𝄢` los muestra **los tres superpuestos verticalmente**, no uno junto a otro.
 *
 * Este objeto fija **el orden de apilado** (de abajo arriba) y reserva una **franja por símbolo**, de
 * modo que su `y` sale de contar cuántos hay debajo. El símbolo va anclado a la nota, así que el eje
 * `x` es siempre el de la cifra (nada de repartos horizontales).
 *
 * Kotlin puro y determinista: la misma nota da siempre el mismo apilado.
 */
object TabArticulationStack {

    /**
     * **Orden de apilado** de abajo arriba (el primero queda pegado a la cuerda). **Medido del
     * `salmo2.pdf`** (D-6, 2026-10-12): la nota del compás 4 apila `𝄢` (arco) abajo, `>` (acento) en
     * medio y `^` (*marcato*) arriba. Es decir, **los arcos van los más próximos a la nota**; después
     * la duración y el matiz de ataque, y por último los adornos y el calderón, que son los que más
     * «vuelan».
     */
    val ORDER: List<TabArticulation> = listOf(
        // 1) Lo más pegado a la nota: los **arcos** de arco (𝄢 ∩ / V). En MuseScore van los primeros.
        TabArticulation.DOWN_BOW,
        TabArticulation.UP_BOW,
        // 2) Duración y matiz de ataque. (D-40) Las **combinadas** van en la franja de su componente
        // **más bajo**, porque ocupan el sitio de las dos que sustituyen y nunca conviven con ellas.
        TabArticulation.STACCATO,
        TabArticulation.ACCENT_STACCATO,
        TabArticulation.MARCATO_STACCATO,
        TabArticulation.STACCATISSIMO,
        TabArticulation.TENUTO,
        TabArticulation.TENUTO_STACCATO,
        TabArticulation.TENUTO_ACCENT,
        TabArticulation.MARCATO_TENUTO,
        TabArticulation.ACCENT,
        TabArticulation.MARCATO,
        // 3) Fuera del todo: adornos y calderón.
        TabArticulation.MORDENT,
        TabArticulation.TURN,
        TabArticulation.FERMATA
    )

    /** Índice de apilado de una articulación (0 = el más próximo a la cuerda). */
    fun tierOf(articulation: TabArticulation): Int =
        ORDER.indexOf(articulation).let { if (it < 0) ORDER.size else it }

    /**
     * Altura de una franja de apilado, en fracciones del **espacio de pentagrama**. MuseScore separa
     * las articulaciones alrededor de **0,6 espacios**; con la altura de los glifos (hasta 1 espacio en
     * el acento) el paso útil ronda **1,0 espacios** para que no se toquen.
     *
     * **Ojo con la unidad**: se multiplica por `stringSpacing`, que son **1,5 espacios** de pentagrama
     * ([TabRhythmGeometry.STRING_SPACING_SPACES]), así que el paso real es `1,5 · 1,1 = 1,65` espacios.
     *
     * **Medido del `salmo2.pdf`** (2026-10-29, corrige la medición del 28): en el compás 3 la pila de
     * x = 1853–1882 tiene el `^` en las filas 425–445, el `>` en 454–472 y el glifo bajo en 495–525
     * (espacio = 20,67 px), o sea un paso de **1,40 a 1,98 espacios**. Nuestro paso de **1,65** cae
     * **dentro de ese rango**, así que la deuda que anotaba §3.11 («~1,5× mayor») era una **mala
     * lectura**: mezclaba filas de glifos de distinta altura. **No hay nada que encoger aquí.**
     */
    const val TIER_SPACES: Float = 1.1f

    /**
     * **Tinta de la cifra de traste** en fracción de su `em` (D-59).
     *
     * Medido del `salmo2.pdf`: la tinta de la cifra mide **0,887 de la separación de cuerdas** y el
     * `em` que la clava es **1,83 espacios** ([TabTextMetrics.FRET_EM]), o sea `0,887 · 1,5 / 1,83 =
     * 0,728` del `em`. Es el dato que dice **cuánto sube la cifra por encima de la línea de la
     * cuerda** (la mitad de su tinta) y, por tanto, dónde tiene que empezar la pila de símbolos.
     */
    const val FRET_INK_RATIO: Float = 0.728f

    /**
     * **Aire** entre la tinta de la cifra y el borde inferior de la primera franja de símbolos, en
     * espacios de pentagrama (D-59).
     */
    const val SYMBOL_AIR_SPACES: Float = 0.09f

    /**
     * **Aire de base de la pila de símbolos**: lo que hay que subir la primera franja para que su
     * contenido **no pise la cifra de la cuerda 1** (D-59).
     *
     * La cifra se centra **en** la línea de la cuerda, así que su tinta sube media altura
     * (`FRET_EM · FRET_INK_RATIO / 2`). Antes la pila arrancaba a media franja de la línea
     * (`symbolsY = bottom(SYMBOLS) − tier/2`) y el glifo, que medía **2,4 espacios** con la franja en
     * **1,65**, bajaba **0,375 espacios por debajo de la línea**: cubría la mitad superior del número.
     * Es el defecto que reportó el usuario con el trémolo, la palanca caída, los armónicos y `l.v.`.
     *
     * @param fretNumberScale zoom del número ([TabLayoutSettings.fretNumberScale]): al agrandar la
     *   cifra su tinta sube más y el aire tiene que crecer con ella.
     */
    fun baseClearanceSpaces(fretNumberScale: Float = 1f): Float =
        TabTextMetrics.FRET_EM * FRET_INK_RATIO * 0.5f * fretNumberScale + SYMBOL_AIR_SPACES

    /**
     * **Apila** las articulaciones de una nota: devuelve cada una con su **índice de franja** (0, 1,
     * 2…), ordenadas de abajo arriba. Dos articulaciones nunca comparten franja (por eso nunca se
     * solapan).
     */
    fun stack(articulations: Set<TabArticulation>): List<StackedArticulation> =
        articulations
            .sortedBy { tierOf(it) }
            .mapIndexed { tier, articulation -> StackedArticulation(articulation, tier) }

    /** Una articulación con su **franja** en la pila vertical (0 = la más baja). */
    data class StackedArticulation(val articulation: TabArticulation, val tier: Int)
}
