package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.tablature.TabRhythmGeometry.StemDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **C.2 · Geometría de plicas y barras** (roadmap §4, arregla F-03): los trazos salen de los
 * **`engravingDefaults` de SMuFL** (`stemThickness`, `beamThickness`, `beamSpacing`), no de grosores
 * a ojo, y la dirección de la plica sigue la convención de grabado (como el `stemsDown`).
 */
class TabRhythmGeometryTest {

    private val unit = 6.5f

    private fun mark(figure: RhythmFigure, x: Float, measure: Int = 0) =
        TabRhythmMark(figure = figure, x = x, y = 100f, measureIndex = measure)

    // ── Corchetes y plica ───────────────────────────────────────────────────────────────────────

    @Test
    fun elNumeroDeCorchetesPorFiguraEsElDeLaNotacion() {
        assertEquals(0, TabRhythmGeometry.flagCount(RhythmFigure.WHOLE))
        assertEquals(0, TabRhythmGeometry.flagCount(RhythmFigure.HALF))
        assertEquals(0, TabRhythmGeometry.flagCount(RhythmFigure.QUARTER))
        assertEquals(1, TabRhythmGeometry.flagCount(RhythmFigure.EIGHTH))
        assertEquals(1, TabRhythmGeometry.flagCount(RhythmFigure.DOTTED_EIGHTH))
        assertEquals(2, TabRhythmGeometry.flagCount(RhythmFigure.SIXTEENTH))
        // Los silencios no llevan plica ni bandera.
        assertEquals(0, TabRhythmGeometry.flagCount(RhythmFigure.EIGHTH_REST))
    }

    @Test
    fun laRedondaYLosSilenciosNoLlevanPlica() {
        assertNull(TabRhythmGeometry.stemOf(RhythmFigure.WHOLE, 10f, 100f, unit, StemDirection.DOWN))
        assertNull(TabRhythmGeometry.stemOf(RhythmFigure.QUARTER_REST, 10f, 100f, unit, StemDirection.DOWN))
        assertNotNull(TabRhythmGeometry.stemOf(RhythmFigure.HALF, 10f, 100f, unit, StemDirection.DOWN))
        assertNotNull(TabRhythmGeometry.stemOf(RhythmFigure.QUARTER, 10f, 100f, unit, StemDirection.DOWN))
    }

    @Test
    fun laPlicaHaciaAbajoCuelgaDelPieYSeAnclaALaIzquierda() {
        val stem = TabRhythmGeometry.stemOf(RhythmFigure.QUARTER, 10f, 100f, unit, StemDirection.DOWN)!!
        assertTrue("la plica baja", stem.yEnd > stem.yStart)
        assertEquals("longitud de MuseScore (3,0 espacios)", unit * 3.0f, stem.yEnd - stem.yStart, 0.001f)
        assertTrue("hacia abajo se ancla a la izquierda de la figura", stem.x < 10f)
        assertEquals("grosor stemThickness", unit * 0.10f, stem.thickness, 0.001f)
    }

    @Test
    fun laPlicaHaciaArribaSubeYSeAnclaALaDerecha() {
        val stem = TabRhythmGeometry.stemOf(RhythmFigure.QUARTER, 10f, 100f, unit, StemDirection.UP)!!
        assertTrue("la plica sube", stem.yEnd < stem.yStart)
        assertTrue("hacia arriba se ancla a la derecha", stem.x > 10f)
    }

    // ── Barras de unión ─────────────────────────────────────────────────────────────────────────

    @Test
    fun unaSolaCorcheaNoFormaBarra() {
        val beams = TabRhythmGeometry.beamsOf(listOf(mark(RhythmFigure.EIGHTH, 0f)), unit, StemDirection.DOWN)
        assertTrue("una nota sola lleva bandera, no barra", beams.isEmpty())
    }

    @Test
    fun dosCorcheasSeguidasFormanUnaBarra() {
        val beams = TabRhythmGeometry.beamsOf(
            listOf(mark(RhythmFigure.EIGHTH, 0f), mark(RhythmFigure.EIGHTH, 20f)),
            unit,
            StemDirection.DOWN
        )
        assertEquals(1, beams.size)
        val beam = beams.single()
        assertEquals("grosor beamThickness", unit * 0.5f, beam.thickness, 0.001f)
        assertTrue("la barra une las dos plicas", beam.endX > beam.startX)
    }

    @Test
    fun dosSemicorcheasFormanDosBarrasSeparadas() {
        val beams = TabRhythmGeometry.beamsOf(
            listOf(mark(RhythmFigure.SIXTEENTH, 0f), mark(RhythmFigure.SIXTEENTH, 20f)),
            unit,
            StemDirection.DOWN
        )
        assertEquals("la semicorchea tiene dos corchetes", 2, beams.size)
        // La separación entre barras es `beamThickness + beamSpacing`.
        val gap = kotlin.math.abs(beams[1].startY - beams[0].startY)
        assertEquals(unit * (0.5f + 0.25f), gap, 0.001f)
    }

    @Test
    fun lasBarrasVanAlPieDeLaPlicaCuandoMiraHaciaAbajo() {
        val beams = TabRhythmGeometry.beamsOf(
            listOf(mark(RhythmFigure.EIGHTH, 0f), mark(RhythmFigure.EIGHTH, 20f)),
            unit,
            StemDirection.DOWN
        ).single()
        // Con la plica hacia abajo, la barra queda por **debajo** del arranque de la plica (y > 100).
        assertTrue("la barra va al pie de la plica", beams.startY > 100f)
    }

    @Test
    fun elBordeExteriorDeLaBarraTocaElExtremoDeLaPlica() {
        // (2026-10-15) Medido del `salmo2.pdf`: la plica va de y = 712 a y = 773 y la barra de un
        // grupo de corcheas de y = 764 a 773, o sea el **borde exterior** de la barra (el más
        // alejado del pentagrama) coincide con el extremo de la plica, y la barra se dibuja **por
        // dentro**. Antes se dibujaba por fuera y el grupo salía media barra demasiado abajo.
        val headY = 100f
        val stem = TabRhythmGeometry.stemOf(RhythmFigure.EIGHTH, 0f, headY, unit, StemDirection.DOWN)!!
        val beam = TabRhythmGeometry.beamsOf(
            listOf(mark(RhythmFigure.EIGHTH, 0f), mark(RhythmFigure.EIGHTH, 20f)),
            unit,
            StemDirection.DOWN
        ).single()
        val outerEdge = beam.startY + beam.thickness / 2f
        assertEquals("el borde exterior toca el extremo de la plica", stem.yEnd, outerEdge, 0.001f)
    }

    @Test
    fun lasBarrasDeSemicorcheaSeApilanHaciaLasNotas() {
        // Medido del `salmo2.pdf` (M4, 300 dpi): en un grupo de semicorcheas con plica hacia abajo
        // la barra primaria va de y = 764 a 773 (al pie de la plica) y la secundaria de y = 748 a
        // 757, es decir **por encima** de la primaria, del lado de las cabezas, a
        // `beamThickness + beamSpacing` = 0,75 espacios. Nunca se apilan hacia fuera.
        val beams = TabRhythmGeometry.beamsOf(
            listOf(mark(RhythmFigure.SIXTEENTH, 0f), mark(RhythmFigure.SIXTEENTH, 20f)),
            unit,
            StemDirection.DOWN
        )
        val first = beams[0].startY + beams[0].thickness / 2f
        val second = beams[1].startY + beams[1].thickness / 2f
        assertEquals("la segunda barra sube hacia las cabezas", unit * (0.5f + 0.25f), first - second, 0.001f)
    }

    @Test
    fun laBarraSigueLaInclinacionDeLasNotas() {
        // Dos corcheas a distinta altura: la barra se inclina (startY ≠ endY), como en una partitura.
        val low = mark(RhythmFigure.EIGHTH, 0f)
        val high = mark(RhythmFigure.EIGHTH, 20f).copy(y = 90f)
        val beam = TabRhythmGeometry.beamsOf(listOf(low, high), unit, StemDirection.DOWN).single()
        assertTrue("la barra se inclina", kotlin.math.abs(beam.startY - beam.endY) > 0.1f)
    }

    // ── Valores de grabado (SMuFL) ──────────────────────────────────────────────────────────────

    @Test
    fun losGrosoresSonLosDeSMuFL() {
        assertEquals(0.10f, TabRhythmGeometry.STEM_THICKNESS, 0.0001f)
        assertEquals(0.5f, TabRhythmGeometry.BEAM_THICKNESS, 0.0001f)
        assertEquals(0.25f, TabRhythmGeometry.BEAM_SPACING, 0.0001f)
        // (2026-10-15) Corregido contra `StemLayout::tabStemLength` de MuseScore: con las plicas al
        // lado del pentagrama la longitud es `STAFFTYPE_TAB_DEFAULTSTEMLEN_DN = 3,0` espacios.
        assertEquals(3.0f, TabRhythmGeometry.STEM_LENGTH, 0.0001f)
    }

    @Test
    fun laPlicaArrancaUnEspacioPorDebajoDeLaUltimaCuerda() {
        // (2026-10-15) `StemLayout::tabRestStemPosY` con plicas hacia abajo y al lado del pentagrama:
        // `(lines - 1) * lineDistance + 1,0` = 8,5 espacios desde la primera cuerda, o sea **1,0
        // espacio por debajo de la cuerda 6**. Antes la plica nacía **en** la cuerda 6 y se pegaba a
        // la cifra («la plica está muy cerca de la 6 cuerda»).
        assertEquals(1.0f, TabRhythmGeometry.STEM_TOP_GAP_SPACES, 0.0001f)
        val unit = 10f
        val headY = 100f
        val stem = TabRhythmGeometry.stemOf(
            RhythmFigure.QUARTER, 0f, headY, unit, StemDirection.DOWN
        )!!
        assertEquals("arranca 1 espacio por debajo", headY + unit, stem.yStart, 0.0001f)
        assertEquals("mide 3 espacios", headY + unit * 4f, stem.yEnd, 0.0001f)
    }

    @Test
    fun conCabezaDibujadaLaPlicaSiSaleDeLaCabeza() {
        // En el modo `Note symbols` la figura lleva cabeza, así que la plica no puede despegarse.
        val stem = TabRhythmGeometry.stemOf(
            RhythmFigure.QUARTER, 0f, 100f, 10f, StemDirection.DOWN, topGap = 0f
        )!!
        assertEquals(100f, stem.yStart, 0.0001f)
    }

    // ── Línea de los silencios (regla de MuseScore) ─────────────────────────────────────────────

    @Test
    fun laLineaNaturalDelSilencioEsLaDeMuseScore() {
        // `RestLayout::computeNaturalLine`: `floor(lines/2)` con líneas impares, `ceil(lines/2)` con
        // pares. En un pentagrama de 5 líneas el silencio se apoya en la **3.ª** (índice 2, la central);
        // en uno de tablatura de 6 cuerdas, en la **4.ª** (índice 3).
        assertEquals(2, TabRhythmGeometry.restLine(lines = 5, isWholeRest = false))
        assertEquals(3, TabRhythmGeometry.restLine(lines = 6, isWholeRest = false))
        assertEquals(1, TabRhythmGeometry.restLine(lines = 3, isWholeRest = false))
        assertEquals(2, TabRhythmGeometry.restLine(lines = 4, isWholeRest = false))
    }

    @Test
    fun elSilencioDeRedondaPendeDeLaLineaDeArriba() {
        // `computeWholeOrBreveRestOffset`: la redonda sube **una línea** (pende en vez de apoyarse).
        assertEquals(2, TabRhythmGeometry.restLine(lines = 6, isWholeRest = true))
        assertEquals(1, TabRhythmGeometry.restLine(lines = 5, isWholeRest = true))
    }

    @Test
    fun unPentagramaSinLineasNoRompeLaRegla() {
        assertEquals(0, TabRhythmGeometry.restLine(lines = 0, isWholeRest = false))
        assertEquals(0, TabRhythmGeometry.restLine(lines = 0, isWholeRest = true))
    }
}
