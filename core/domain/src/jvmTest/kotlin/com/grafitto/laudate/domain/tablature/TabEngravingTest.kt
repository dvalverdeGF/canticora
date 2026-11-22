package com.grafitto.laudate.domain.tablature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los grosores de grabado (D-39) se expresan en **espacios de pentagrama**, como en MuseScore, y no
 * en píxeles fijos: así escalan con el zoom y coinciden con el PDF de referencia.
 */
class TabEngravingTest {

    @Test
    fun losGrosoresSonLosDeLaHojaDeEstilosDeMuseScore() {
        assertEquals(0.11f, TabEngraving.STAFF_LINE_WIDTH, 0.0001f)
        assertEquals(0.18f, TabEngraving.BAR_WIDTH, 0.0001f)
        assertEquals(0.18f, TabEngraving.DOUBLE_BAR_WIDTH, 0.0001f)
        assertEquals(0.37f, TabEngraving.DOUBLE_BAR_DISTANCE, 0.0001f)
        assertEquals(0.55f, TabEngraving.END_BAR_WIDTH, 0.0001f)
        assertEquals(0.37f, TabEngraving.END_BAR_DISTANCE, 0.0001f)
    }

    @Test
    fun elEspacioDePentagramaEsElPasoDeCuerdasEntreUnoComaCinco() {
        // El `.mscx` de MuseScore declara <lineDistance>1.5</lineDistance> para la tablatura.
        assertEquals(20f, TabEngraving.staffSpaceOf(30f), 0.0001f)
        assertEquals(13.3333f, TabEngraving.staffSpaceOf(20f), 0.001f)
    }

    @Test
    fun losGrosoresEscalanConElTamanoDelPentagrama() {
        val small = TabEngraving.staffSpaceOf(15f)
        val large = TabEngraving.staffSpaceOf(30f)
        assertEquals(2f, large / small, 0.0001f)
        // El doble de pentagrama, el doble de grosor: eso es lo que no hacía el valor fijo en px.
        assertEquals(
            TabEngraving.BAR_WIDTH * small * 2f,
            TabEngraving.BAR_WIDTH * large,
            0.0001f
        )
    }

    @Test
    fun laBarraFinalEsMasGruesaQueLaNormalYLaDobleIgualQueLaNormal() {
        assertTrue(TabEngraving.END_BAR_WIDTH > TabEngraving.BAR_WIDTH)
        assertEquals(TabEngraving.BAR_WIDTH, TabEngraving.DOUBLE_BAR_WIDTH, 0.0001f)
    }

    @Test
    fun losGrosoresReproducenElPdfDeReferencia() {
        // salmo2.pdf a 300 dpi: 1 espacio = 20,67 px. Las cuerdas miden 2 px y las barras 4 px.
        val space = 20.67f
        assertEquals(2.27f, TabEngraving.STAFF_LINE_WIDTH * space, 0.01f)
        assertEquals(3.72f, TabEngraving.BAR_WIDTH * space, 0.01f)
        // Rasterizados a píxeles enteros dan exactamente los 2 px y 4 px medidos.
        assertEquals(2, (TabEngraving.STAFF_LINE_WIDTH * space).toInt())
        assertEquals(3, (TabEngraving.BAR_WIDTH * space).toInt())
    }
}
