package com.grafitto.laudate.domain.tablature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **C.4 · Ligaduras y curvas** (roadmap §4): el arco de ligadura tiene la **curvatura de grabado** de
 * MuseScore —fino en las puntas, más grueso en el centro— y no un trazo de grosor constante.
 */
class TabSlurGeometryTest {

    private val unit = 6.5f
    private val from = Offset2(0f, 100f)
    private val to = Offset2(60f, 100f)

    @Test
    fun elArcoEsMasGruesoEnElCentroQueEnLasPuntas() {
        val endpoint = TabSlurGeometry.thicknessAt(0f, unit)
        val midpoint = TabSlurGeometry.thicknessAt(0.5f, unit)
        assertEquals("la punta usa slurEndpointThickness", TabSlurGeometry.ENDPOINT_THICKNESS * unit, endpoint, 0.001f)
        assertEquals("el centro usa slurMidpointThickness", TabSlurGeometry.MIDPOINT_THICKNESS * unit, midpoint, 0.001f)
        assertTrue("el centro es más grueso", midpoint > endpoint)
    }

    @Test
    fun elContornoSeAbombaHaciaLaPanza() {
        val outline = TabSlurGeometry.arc(from, to, unit, over = true)
        val outer = outline.outer
        assertEquals("tantos puntos como muestras + 1", 13, outer.size)
        // El punto medio del borde exterior está **por encima** de las puntas (y menor = arriba).
        val midOuter = outer[outer.size / 2]
        assertTrue("la panza sube por encima de la línea de las notas", midOuter.y < from.y)
        assertEquals("las puntas sí tocan la línea", from.y, outer.first().y, unit * 0.3f)
    }

    @Test
    fun haciaAbajoElArcoCuelga() {
        val outline = TabSlurGeometry.arc(from, to, unit, over = false)
        val midOuter = outline.outer[outline.outer.size / 2]
        assertTrue("con `over = false` la panza baja", midOuter.y > from.y)
    }

    @Test
    fun laAlturaDelArcoSeAcota() {
        // Arco muy corto: no queda plano (altura mínima de medio espacio).
        assertEquals(unit * 0.5f, TabSlurGeometry.height(1f, unit), 0.001f)
        // Arco muy largo: no se dispara (altura máxima de 1,8 espacios).
        assertEquals(unit * 1.8f, TabSlurGeometry.height(1000f, unit), 0.001f)
    }

    // --- Ligadura de prolongación (B-10) -------------------------------------------------------

    @Test
    fun laLigaduraDeProlongacionCuelgaPorDebajoDeLaCuerda() {
        val outline = TabSlurGeometry.tieArc(from, to, unit)
        val midOuter = outline.outer[outline.outer.size / 2]
        val midInner = outline.inner[outline.inner.size / 2]
        // MuseScore dibuja el `Tie` de tablatura con la panza **hacia abajo** (`setUp(!chord->up())`),
        // que es el lado libre de la cifra: nunca debe subir por encima de la línea de la cuerda.
        assertTrue("la panza baja", midOuter.y > from.y)
        assertTrue("el borde interior tampoco sube", midInner.y > from.y)
        assertEquals("las puntas sí tocan la línea", from.y, outline.outer.first().y, unit * 0.3f)
    }

    @Test
    fun laLigaduraDeProlongacionEsMasPlanaQueLaDeExpresion() {
        val tie = TabSlurGeometry.tieHeight(60f, unit)
        val slur = TabSlurGeometry.height(60f, unit)
        assertTrue("la ligadura de prolongación es más plana", tie < slur)
        // Y el contorno lo refleja: la panza del `tie` queda más cerca de la cuerda.
        val tieBelly = TabSlurGeometry.tieArc(from, to, unit).outer[6].y
        val slurBelly = TabSlurGeometry.arc(from, to, unit, over = false).outer[6].y
        assertTrue("el `tie` no invade la cuerda de al lado", tieBelly < slurBelly)
    }

    @Test
    fun laAlturaDeLaLigaduraDeProlongacionSeAcota() {
        assertEquals(unit * 0.35f, TabSlurGeometry.tieHeight(1f, unit), 0.001f)
        assertEquals(unit * 0.9f, TabSlurGeometry.tieHeight(1000f, unit), 0.001f)
        // Dentro de los topes manda la proporción del ancho.
        assertEquals(30f * TabSlurGeometry.TIE_HEIGHT, TabSlurGeometry.tieHeight(30f, unit), 0.001f)
    }

    @Test
    fun elContornoSeCierraConLosDosBordes() {
        val outline = TabSlurGeometry.arc(from, to, unit)
        assertEquals(26, outline.closed.size)
        // El primer punto y el último están en la misma punta (el contorno se cierra).
        val first = outline.closed.first()
        val last = outline.closed.last()
        assertTrue("el contorno cierra en la punta", kotlin.math.abs(first.x - last.x) < unit)
    }
}
