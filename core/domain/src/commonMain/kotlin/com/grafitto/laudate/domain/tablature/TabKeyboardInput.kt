package com.grafitto.laudate.domain.tablature

/**
 * Entrada por **teclado físico** del editor de tablatura (ADR-017, *Feature T*).
 *
 * Reproduce el modelo de los editores de referencia (**Guitar Pro**, **My Guitar Tabs**): el
 * **cursor** es una posición lógica (compás, casilla y cuerda) que se mueve con las flechas, y el
 * **traste se teclea**. Kotlin puro y determinista: la UI sólo traduce las teclas a estas
 * operaciones y el ViewModel las aplica.
 *
 * Los dígitos se acumulan en un **búfer** para poder teclear trastes de dos cifras (0–[MAX_FRET]):
 * «1» y «2» → 12; «2» y «5» → el 25 no es un traste válido, así que se reinterpreta como 5. La UI (o
 * el ViewModel) decide cuándo se vacía el búfer por inactividad.
 */
object TabKeyboardInput {

    /** Posición lógica del cursor de escritura: compás, casilla y cuerda (1 = la más aguda). */
    data class Cursor(val measureIndex: Int, val slotIndex: Int, val stringNumber: Int)

    /**
     * Desplaza el cursor **horizontalmente** (←/→) [delta] columnas, saltando de compás en compás y
     * quedándose en los topes (nunca sale de la tablatura). Cada compás tiene sus **columnas reales**
     * (modelo de columnas), así que el salto usa [columnCountPerMeasure] en vez de un nº fijo.
     *
     * Si un compás está **vacío** (0 columnas), el cursor puede situarse en su única posición
     * virtual (la 0) para poder escribir la primera nota.
     */
    fun moveHorizontal(
        cursor: Cursor,
        columnCountPerMeasure: List<Int>,
        delta: Int
    ): Cursor {
        val measureCount = columnCountPerMeasure.size
        if (measureCount <= 0) return cursor
        var measure = cursor.measureIndex.coerceIn(0, measureCount - 1)
        // Posiciones válidas del cursor en un compás: 0..columnas (ambas inclusive). La posición
        // «columnas» es la **fantasma**: está justo tras la última y es donde se escribe lo nuevo.
        fun positionsOf(m: Int): Int = columnCountPerMeasure[m] + 1
        var slot = cursor.slotIndex.coerceIn(0, positionsOf(measure) - 1) + delta
        while (slot < 0) {
            if (measure == 0) {
                slot = 0
                break
            }
            measure -= 1
            slot += positionsOf(measure)
        }
        while (slot >= positionsOf(measure)) {
            if (measure == measureCount - 1) {
                slot = positionsOf(measure) - 1
                break
            }
            slot -= positionsOf(measure)
            measure += 1
        }
        return cursor.copy(measureIndex = measure, slotIndex = slot)
    }

    /**
     * Desplaza el cursor **verticalmente** (↑/↓) [delta] cuerdas. El signo positivo baja de cuerda
     * (número mayor); la cuerda 1 es la más aguda y se dibuja arriba. No da la vuelta: se queda en
     * los extremos.
     */
    fun moveVertical(cursor: Cursor, stringCount: Int, delta: Int): Cursor {
        if (stringCount <= 0) return cursor
        val string = (cursor.stringNumber + delta).coerceIn(1, stringCount)
        return cursor.copy(stringNumber = string)
    }

    /** Avanza una columna (Espacio/Intro): es el gesto de «siguiente nota» en el tiempo. */
    fun advance(cursor: Cursor, columnCountPerMeasure: List<Int>): Cursor =
        moveHorizontal(cursor, columnCountPerMeasure, 1)

    /**
     * Acumula un [digit] en el [buffer] de traste. Devuelve el **nuevo búfer** o `null` si la
     * combinación no es un traste válido (`0..maxFret`), en cuyo caso el dígito debe empezar un
     * búfer nuevo. Un búfer «0» seguido de otro dígito empieza de cero (0 · 5 → 5), porque la
     * cuerda al aire no tiene continuación.
     */
    fun appendDigit(buffer: String, digit: Int, maxFret: Int): String? {
        val base = if (buffer == "0") "" else buffer
        val candidate = base + digit
        val value = candidate.toIntOrNull() ?: return null
        return if (value in 0..maxFret) candidate else null
    }

    /** Traste que representa un búfer de dígitos (`null` si está vacío o no es un número). */
    fun fretOf(buffer: String): Int? = buffer.toIntOrNull()
}
