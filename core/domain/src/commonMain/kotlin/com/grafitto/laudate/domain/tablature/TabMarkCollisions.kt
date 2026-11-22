package com.grafitto.laudate.domain.tablature

/**
 * **Resolución de colisiones de las marcas de un sistema** (roadmap **C.5**).
 *
 * En una **franja** (una fila horizontal del pentagrama) pueden coincidir varias marcas en el mismo
 * tramo: el cierre de repetición (`:|`), la cifra de ejecuciones (`x2`), la **volta** (`1.`) y el
 * **salto** (`D.C.`) del mismo compás. MuseScore las reparte para que no se toquen; aquí se hace igual
 * con un algoritmo **puro y determinista**:
 *
 *  1. pasada **directa** de izquierda a derecha: cada marca se empuja a la derecha hasta dejar
 *     [minGap] respecto a la anterior (nunca se solapan);
 *  2. **reparto simétrico**: se retrocede todo el grupo la **mitad** del desplazamiento acumulado, sin
 *     salirse por la izquierda del compás (así el grupo queda centrado en su sitio natural y no se
 *     corre hacia un lado).
 *
 * El **ancho** de cada marca se **estima** a partir de su texto y del ancho de columna
 * ([CHAR_WIDTH_FACTOR]), igual que hace el motor de PDF: es determinista y deliberadamente
 * conservador, de modo que dos marcas nunca se pisen aunque la fuente real sea algo más ancha.
 *
 * Es la lógica que faltaba para que las marcas de varios compases con repeticiones, voltas y saltos
 * (o con navegación y cambio de octava) no se amontonen.
 */
object TabMarkCollisions {

    /** Ancho medio de carácter como fracción del ancho de una columna. */
    const val CHAR_WIDTH_FACTOR: Float = 0.45f

    /** Separación mínima (px) entre dos marcas de la misma franja. */
    const val MIN_GAP_FRACTION: Float = 0.12f

    /** Ancho estimado de una marca según su texto. */
    fun widthOf(text: String, cellWidth: Float): Float =
        (text.length * cellWidth * CHAR_WIDTH_FACTOR).coerceAtLeast(cellWidth * 0.30f)

    /**
     * Reparte las marcas [items] (posición `x` y texto) de **una misma franja** para que no se
     * solapen. Devuelve las `x` **ajustadas** en el mismo orden.
     *
     * @param leftBound límite izquierdo del compás: el grupo no se desplaza más a la izquierda de él.
     * @param rightBound límite derecho, por simetría (no se fuerza hacia la derecha; solo informa).
     */
    fun resolve(
        items: List<Pair<Float, String>>,
        cellWidth: Float,
        leftBound: Float,
        rightBound: Float
    ): List<Float> {
        if (items.size <= 1) return items.map { it.first }
        val gap = cellWidth * MIN_GAP_FRACTION

        // Las marcas **no** llegan ordenadas por `x` (el salto va al final, la volta al principio…):
        // se ordenan aquí y luego se devuelven en el orden **original** de entrada.
        val order = items.indices.sortedBy { items[it].first }
        val sorted = order.map { items[it] }
        val widths = sorted.map { widthOf(it.second, cellWidth) }
        val xs = sorted.map { it.first }.toMutableList()

        // 1. Pasada directa: sin solapes (empuja a la derecha lo que invada a lo anterior).
        for (i in 1 until xs.size) {
            val minAllowed = xs[i - 1] + widths[i - 1] / 2f + gap + widths[i] / 2f
            if (xs[i] < minAllowed) xs[i] = minAllowed
        }

        // 2. La primera marca no puede cruzar el **borde izquierdo** del compás: si su borde izquierdo
        //    cae fuera, se empuja el grupo entero a la derecha.
        val firstHalf = widths.first() / 2f
        val firstLeft = xs.first() - firstHalf
        if (firstLeft < leftBound) {
            val push = leftBound - firstLeft
            for (i in xs.indices) xs[i] += push
        }

        // 3. Reparto simétrico: se retrocede el grupo la **mitad** del desplazamiento respecto a su
        //    posición natural, sin volver a cruzar el borde izquierdo (así queda centrado y no se
        //    corre hacia un lado).
        val shift = xs.first() - sorted.first().first
        val maxBack = (xs.first() - firstHalf - leftBound).coerceAtLeast(0f)
        val back = (shift / 2f).coerceAtMost(maxBack)
        if (back > 0f) for (i in xs.indices) xs[i] -= back

        // Devolver en el orden original.
        val result = MutableList(items.size) { 0f }
        order.forEachIndexed { position, originalIndex -> result[originalIndex] = xs[position] }
        return result
    }
}
