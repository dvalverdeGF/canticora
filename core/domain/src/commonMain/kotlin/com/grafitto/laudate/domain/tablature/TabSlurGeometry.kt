package com.grafitto.laudate.domain.tablature

/**
 * **Geometría de las ligaduras** (roadmap **C.4**): la curva del arco de ligadura (*hammer-on* /
 * *pull-off*), con la **curvatura de grabado** de MuseScore —**fina en los extremos y más gruesa en el
 * centro**— y no un trazo de grosor constante.
 *
 * MuseScore no dibuja un arco de grosor uniforme: traza un contorno **relleno** cuya altura crece
 * hacia el centro, con los valores de grabado estándar de SMuFL:
 *
 *  - altura del arco respecto a la cuerda (`slurHeight` ≈ `0.33` espacios);
 *  - grosor en la **punta** (`slurEndpointThickness` ≈ `0.10` espacios);
 *  - grosor en el **centro** (`slurMidpointThickness` ≈ `0.22` espacios).
 *
 * Aquí se calcula el **contorno** (dos curvas: exterior e interior) muestreando una **Bézier cúbica**
 * de la línea central, de modo que el pintor solo tiene que construir el `Path`, cerrarlo y rellenarlo.
 * Kotlin puro y determinista: lo comparten el editor, el visor y el PDF.
 */
object TabSlurGeometry {

    /** Altura del arco respecto a la línea de la cuerda, en fracciones del ancho de la ligadura. */
    const val SLUR_HEIGHT: Float = 0.33f

    /** Grosor del arco en las **puntas**, en espacios. */
    const val ENDPOINT_THICKNESS: Float = 0.10f

    /** Grosor del arco en el **centro**, en espacios. */
    const val MIDPOINT_THICKNESS: Float = 0.22f

    /** Contorno de la ligadura: el borde **exterior** (hacia la panza) y el **interior**. */
    data class Outline(val outer: List<Offset2>, val inner: List<Offset2>) {
        /** Puntos del contorno cerrado: el exterior y el interior en orden inverso (para un `Path`). */
        val closed: List<Offset2> get() = outer + inner.reversed()
    }

    /**
     * Altura (px) del arco según su **ancho** ([span], la distancia horizontal entre las dos notas) y
     * la unidad del pentagrama ([unit], el espacio de pentagrama). Se acota para que una ligadura
     * corta no salga plana ni una muy larga se dispare.
     */
    fun height(span: Float, unit: Float): Float =
        (span * SLUR_HEIGHT).coerceIn(unit * 0.5f, unit * 1.8f)

    /**
     * **Contorno** de la ligadura entre dos puntos `from` y `to` (los centros de las dos notas).
     *
     * [over] `true` traza la panza **hacia arriba** (hacia las cuerdas agudas, que es como se leen las
     * ligaduras de guitarra); `false`, hacia abajo. [samples] es el número de segmentos del muestreo
     * (más = curva más suave).
     */
    fun arc(
        from: Offset2,
        to: Offset2,
        unit: Float,
        over: Boolean = true,
        samples: Int = 12
    ): Outline {
        val span = spanOf(from, to)
        return outline(from, to, unit, over, height(span, unit), samples)
    }

    /**
     * Altura del arco de **ligadura de prolongación** (*tie*), en fracciones del ancho.
     *
     * Es **más plano** que el de expresión: en MuseScore la ligadura de prolongación de una tablatura
     * une dos cifras de la **misma** altura, así que no necesita panza; solo un arco que se lea como
     * «esto sigue sonando» sin invadir la cuerda de al lado.
     */
    const val TIE_HEIGHT: Float = 0.12f

    /** Altura (px) del arco de ligadura de prolongación, acotada como el de expresión. */
    fun tieHeight(span: Float, unit: Float): Float =
        (span * TIE_HEIGHT).coerceIn(unit * 0.35f, unit * 0.9f)

    /**
     * **Contorno de la ligadura de prolongación** (*tie*): igual que [arc] pero con la panza **hacia
     * abajo** (debajo de la línea de la cuerda, que es el lado libre de la cifra) y con la curvatura
     * plana de [tieHeight].
     */
    fun tieArc(
        from: Offset2,
        to: Offset2,
        unit: Float,
        samples: Int = 12
    ): Outline {
        val span = spanOf(from, to)
        return outline(from, to, unit, over = false, h = tieHeight(span, unit), samples = samples)
    }

    /** Distancia horizontal entre los dos extremos, nunca cero (evita divisiones por cero). */
    private fun spanOf(from: Offset2, to: Offset2): Float =
        kotlin.math.abs(to.x - from.x).coerceAtLeast(0.001f)

    /** Contorno del arco con una altura [h] ya resuelta y la panza hacia [over]. */
    private fun outline(
        from: Offset2,
        to: Offset2,
        unit: Float,
        over: Boolean,
        h: Float,
        samples: Int
    ): Outline {
        val left = if (from.x <= to.x) from else to
        val right = if (from.x <= to.x) to else from
        val span = spanOf(from, to)
        // `sign` apunta hacia la panza: -1 = arriba (menor y), +1 = abajo.
        val sign = if (over) -1f else 1f
        val baseY = (left.y + right.y) / 2f

        // Bézier cúbica de la **línea central**: arranca en la primera nota, termina en la segunda y
        // se abomba en el centro (puntos de control al 25 % y al 75 % del ancho, separados `h`).
        val c1 = Offset2(left.x + span * 0.25f, baseY + sign * h)
        val c2 = Offset2(right.x - span * 0.25f, baseY + sign * h)

        val start = Offset2(left.x, baseY)
        val end = Offset2(right.x, baseY)
        val outer = ArrayList<Offset2>(samples + 1)
        val inner = ArrayList<Offset2>(samples + 1)
        for (i in 0..samples) {
            val t = i.toFloat() / samples
            val p = cubic(start, c1, c2, end, t)
            val half = thicknessAt(t, unit) / 2f
            // El grosor se aplica **en vertical**: la ligadura es casi horizontal y así el borde de la
            // panza queda por fuera (arriba si `over`, abajo si no).
            outer += Offset2(p.x, p.y + sign * half)
            inner += Offset2(p.x, p.y - sign * half)
        }
        return Outline(outer, inner)
    }

    /** Grosor (px) del arco en el parámetro [t] (`0` punta, `0.5` centro, `1` punta). */
    fun thicknessAt(t: Float, unit: Float): Float {
        val endpoint = ENDPOINT_THICKNESS * unit
        val midpoint = MIDPOINT_THICKNESS * unit
        val bulge = kotlin.math.sin(kotlin.math.PI.toFloat() * t.coerceIn(0f, 1f))
        return endpoint + (midpoint - endpoint) * bulge
    }

    /** Punto de una **Bézier cúbica** en el parámetro `t`. */
    fun cubic(p0: Offset2, p1: Offset2, p2: Offset2, p3: Offset2, t: Float): Offset2 {
        val u = 1f - t
        val b0 = u * u * u
        val b1 = 3f * u * u * t
        val b2 = 3f * u * t * t
        val b3 = t * t * t
        return Offset2(
            x = b0 * p0.x + b1 * p1.x + b2 * p2.x + b3 * p3.x,
            y = b0 * p0.y + b1 * p1.y + b2 * p2.y + b3 * p3.y
        )
    }
}
