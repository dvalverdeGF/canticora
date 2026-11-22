package com.grafitto.laudate.domain.tablature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Ley de espaciado de MuseScore**. Fija los valores **reales** para que no se puedan romper: una
 * negra mide **3,5 espacios** (`DEFAULT_QUARTER_NOTE_SPACE`, `horizontalspacing.cpp:744`) y el ancho
 * sigue `ancho ∝ duración^0,585` (sub-lineal), no una proporción lineal.
 *
 * **Corrección (F-04.quater)**: antes se fijaba en **3,815** «medido del `salmo2.pdf`» (18,92 pt /
 * 4,96 pt). Ese valor es el ancho **justificado**, no el natural: el sistema 1 del PDF se estira para
 * llenar el ancho imprimible con un factor de `102,89 / 95,48 = 1,078`, y `3,5 · 1,078 = 3,77`
 * espacios = 18,7 pt, que es el 18,92 pt medido. Tomar el justificado como natural ensanchaba todos
 * los compases un 9 % y descuadraba el reparto en líneas.
 *
 * Datos de origen del PDF (`pdftotext -bbox`, 1 espacio = 4,96 pt): negra 18,92 pt · corchea
 * 12,62 pt · semicorchea 8,42 pt — **justificados**.
 */
class TabSpacingPdfLawTest {

    private val space = 5.0f          // espacio de pentagrama medido (4,96 pt)
    private val quarter = TabSpacing.quarterWidth(space)

    private fun w(beats: Double) = TabSpacing.columnWidth(beats, quarter)

    @Test
    fun laNegraMideElAnchoNaturalDeMuseScore() {
        // `DEFAULT_QUARTER_NOTE_SPACE = 3.5_sp` (`horizontalspacing.cpp:744`).
        assertEquals(3.5f, TabSpacing.QUARTER_WIDTH_SPACES, 0.001f)
        assertEquals(3.5f * space, quarter, 0.01f)
        // El ancho de una negra coincide con el cellWidth (por construcción).
        assertEquals(quarter, w(1.0), 0.01f)
        // Y en espacios: 3,5.
        assertEquals(3.5f, w(1.0) / space, 0.02f)
    }

    @Test
    fun laLeyEsSublinealNoProporcional() {
        // La razón negra/corchea es 1,499 (no 2,0): `ancho ∝ duración^0,585`.
        assertEquals(1.499f, w(1.0) / w(0.5), 0.01f)
        assertEquals(1.499f, w(0.5) / w(0.25), 0.01f)
        // Un compás de 8 corcheas es ~1,33× el de 4 negras, NO 2×.
        val ochoCorcheas = w(0.5) * 8
        val cuatroNegras = w(1.0) * 4
        assertEquals(1.335f, ochoCorcheas / cuatroNegras, 0.02f)
    }

    @Test
    fun elAnchoMinimoNoRecortaLasFigurasDelPdf() {
        // La semicorchea del PDF (0,444·negra) tiene que caber SIN que la recorte el mínimo.
        assertEquals(0.444f, w(0.25) / w(1.0), 0.01f)
    }

    @Test
    fun losAnchosDelPdfSonLosNaturalesJustificados() {
        // El PDF está **justificado**: su sistema 1 se estira `18,92 / 17,36 = 1,09`. Los anchos
        // naturales (espacio 4,96 pt) son 17,36 / 11,57 / 7,72 pt; al multiplicarlos por el factor de
        // justificación dan los 18,92 / 12,62 / 8,42 pt medidos del PDF.
        val pdfSpace = 4.96f
        val naturalQuarter = TabSpacing.quarterWidth(pdfSpace)
        assertEquals(17.36f, naturalQuarter, 0.05f)
        // El factor que explica el PDF tiene que caer en la banda de justificación real (1,05–1,12):
        // si alguien vuelve a meter el ancho justificado como natural, esto lo delata.
        val factor = 18.92f / naturalQuarter
        assertTrue("el factor de justificación del PDF es ~1,09, no $factor", factor in 1.05f..1.12f)
        // Y con ese factor se reproducen las tres cifras medidas del PDF.
        assertEquals(18.92f, naturalQuarter * factor, 0.01f)
        assertEquals(12.62f, TabSpacing.columnWidth(0.5, naturalQuarter) * factor, 0.02f)
        assertEquals(8.42f, TabSpacing.columnWidth(0.25, naturalQuarter) * factor, 0.02f)
    }

    @Test
    fun elPuntilloUsaSuDuracionConLaMismaLey() {
        // Una negra con puntillo (1,5 negras) es más ancha que una negra pero menos que el doble.
        val conPuntillo = w(1.5)
        assertEquals(1.269f, conPuntillo / w(1.0), 0.01f)
    }
}
