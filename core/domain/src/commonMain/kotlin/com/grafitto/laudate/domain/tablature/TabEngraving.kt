package com.grafitto.laudate.domain.tablature

/**
 * **Grosores de grabado** de las líneas del pentagrama, en **espacios de pentagrama**.
 *
 * MuseScore no dibuja las líneas con un grosor en píxeles: usa los valores de grabado de su hoja de
 * estilos (`src/engraving/style/styledef.cpp`), que son **múltiplos del `spatium`**. Por eso una
 * partitura de MuseScore se ve igual a cualquier zoom y en el PDF, mientras que un grosor fijo en
 * píxeles se ve **demasiado fino** al ampliar y **demasiado grueso** al reducir.
 *
 * Valores de MuseScore Studio 4 (`styledef.cpp`):
 *
 * | Estilo | Valor | Constante |
 * | :--- | :--- | :--- |
 * | `staffLineWidth` | 0,11 sp | [STAFF_LINE_WIDTH] |
 * | `barWidth` | 0,18 sp | [BAR_WIDTH] |
 * | `doubleBarWidth` | 0,18 sp | [DOUBLE_BAR_WIDTH] |
 * | `doubleBarDistance` | 0,37 sp | [DOUBLE_BAR_DISTANCE] |
 * | `endBarWidth` | 0,55 sp | [END_BAR_WIDTH] |
 * | `endBarDistance` | 0,37 sp | [END_BAR_DISTANCE] |
 *
 * **Comprobado contra el `salmo2.pdf`** (300 dpi, 1 espacio = 20,67 px): las líneas de cuerda miden
 * **2 px** (0,097 sp, que es 0,11 sp rasterizado) y las barras de compás **4 px** (0,194 sp, que es
 * 0,18 sp rasterizado). Antes el lienzo usaba `1f` y `1,6f` **en píxeles fijos**, es decir un 32 %
 * más finas de lo que pide MuseScore y, peor, **sin escalar** con el zoom.
 */
object TabEngraving {

    /** Grosor de una **línea de cuerda** (`staffLineWidth`), en espacios. */
    const val STAFF_LINE_WIDTH: Float = 0.11f

    /** Grosor de una **barra de compás** normal (`barWidth`), en espacios. */
    const val BAR_WIDTH: Float = 0.18f

    /** Grosor de cada trazo de una **barra doble** (`doubleBarWidth`), en espacios. */
    const val DOUBLE_BAR_WIDTH: Float = 0.18f

    /** Separación **entre** los dos trazos de una barra doble (`doubleBarDistance`), en espacios. */
    const val DOUBLE_BAR_DISTANCE: Float = 0.37f

    /** Grosor del trazo **grueso** de la barra final (`endBarWidth`), en espacios. */
    const val END_BAR_WIDTH: Float = 0.55f

    /** Separación entre el trazo fino y el grueso de la barra final (`endBarDistance`), en espacios. */
    const val END_BAR_DISTANCE: Float = 0.37f

    /**
     * **Un espacio de pentagrama** en la unidad del lienzo, a partir del paso de las cuerdas.
     *
     * Las cuerdas distan [TabRhythmGeometry.STRING_SPACING_SPACES] espacios (lo dice el propio
     * `.mscx`: `<lineDistance>1.5</lineDistance>`), así que el espacio es el paso dividido por 1,5.
     */
    fun staffSpaceOf(stringSpacing: Float): Float =
        stringSpacing / TabRhythmGeometry.STRING_SPACING_SPACES
}
