package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Cabecera del sistema** (D-9): la clave `TAB` y el compás se dibujan **solo en el primer
 * sistema**, como MuseScore en tablatura (`styleDef(hideTabClefAfterFirst, true)` y
 * `MeasureLayout::addHeaderClef()`, que resuelve `hideClef = isTabStaff() && hideTabClefAfterFirst`).
 *
 * El `salmo2.pdf` lo confirma: el primer sistema arranca en x = 281 px (clave + compás) y los otros
 * tres en x = 177 px, sin ninguno de los dos.
 */
class TabSystemHeaderTest {

    private fun tab(measureCount: Int) = Tablature(
        id = "t",
        songId = "song-1",
        title = "T",
        instrument = Tuning.GUITAR.name,
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.QUARTER,
        measures = TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4),
            TabResolution.QUARTER,
            measureCount,
            withGuideColumns = true
        )
    )

    /** Un ancho que obliga a partir en varios sistemas. */
    private val narrow = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f)

    private fun systems(measureCount: Int, settings: TabLayoutSettings = narrow) =
        TablatureSystemsLayout.layout(tab(measureCount), maxWidth = 110f, settings = settings)

    @Test
    fun laClaveSoloSeDibujaEnElPrimerSistema() {
        val systems = systems(measureCount = 4)
        assertTrue("hacen falta varios sistemas para que la prueba tenga sentido", systems.size > 1)
        val clefs = systems.map { s -> s.drawing.texts.count { it.kind == TabTextKind.TAB_CLEF } }
        assertEquals("solo el primer sistema lleva clave", 1, clefs.first())
        assertTrue("ningún otro sistema lleva clave", clefs.drop(1).all { it == 0 })
    }

    @Test
    fun elCompasSoloSeDibujaEnElPrimerSistema() {
        val systems = systems(measureCount = 4)
        val numerators = systems.map { s -> s.drawing.texts.count { it.kind == TabTextKind.TIME_NUMERATOR } }
        assertEquals(1, numerators.first())
        assertTrue(numerators.drop(1).all { it == 0 })
    }

    @Test
    fun laCabeceraSeRepiteSiSePideExplicitamente() {
        val settings = narrow.copy(staff = narrow.staff.copy(headerOnEverySystem = true))
        val systems = systems(measureCount = 4, settings = settings)
        assertTrue(systems.size > 1)
        assertTrue(
            "con `headerOnEverySystem` todos los sistemas llevan clave",
            systems.all { s -> s.drawing.texts.any { it.kind == TabTextKind.TAB_CLEF } }
        )
    }

    @Test
    fun sinCabeceraElPrimerCompasEmpiezaMasALaIzquierda() {
        val withHeader = systems(measureCount = 4)
        val settings = narrow.copy(staff = narrow.staff.copy(headerOnEverySystem = true))
        val repeated = systems(measureCount = 4, settings = settings)
        // El primer sistema de ambos lleva cabecera, así que su primer compás coincide.
        assertEquals(
            withHeader.first().drawing.measureBounds.first().left,
            repeated.first().drawing.measureBounds.first().left,
            0.01f
        )
        // En el segundo sistema, sin cabecera el compás arranca antes.
        assertTrue(
            "sin clave ni compás el pentagrama empieza antes",
            withHeader[1].drawing.measureBounds.first().left <
                repeated[1].drawing.measureBounds.first().left
        )
    }

    @Test
    fun losSistemasSinCabeceraAprovechanElAnchoLibre() {
        val systems = systems(measureCount = 4)
        // El pentagrama de los sistemas 2+ arranca en el margen, no detrás de la clave.
        val firstLeft = systems.first().drawing.measureBounds.first().left
        val secondLeft = systems[1].drawing.measureBounds.first().left
        assertTrue("el segundo sistema arranca más a la izquierda", secondLeft < firstLeft)
    }

    /**
     * **D-22 · la cabecera va dentro del sistema**. En MuseScore la clave y el compás son el
     * `minLeft` del **primer segmento del compás 1**, no un bloque a su izquierda: las cuerdas
     * arrancan **después** de la cabecera y la cabecera se dibuja **encima** del pentagrama. Por eso
     * el compás 1 del `salmo2.pdf` mide **22,5 espacios** (465 px) y los demás 16,5–22,0.
     */
    @Test
    fun laCabeceraOcupaElPrincipioDelPrimerCompasYNoVaAntesDeLaRejilla() {
        // Ancho suficiente para que el primer sistema lleve **dos** compases y se puedan comparar.
        val drawing = TablatureSystemsLayout.layout(
            tab(measureCount = 4),
            maxWidth = 400f,
            settings = narrow
        ).first().drawing
        val caja = drawing.measureBounds.first()
        // Las cuerdas y la caja del compás 1 arrancan en el **mismo** punto: la cabecera está dentro.
        assertEquals(
            "el compás 1 empieza donde arrancan las cuerdas",
            drawing.staffLeft,
            caja.left,
            0.01f
        )
        // Y la clave cae **dentro** de esa caja, no a su izquierda.
        val clef = drawing.texts.first { it.kind == TabTextKind.TAB_CLEF }
        assertTrue(
            "la clave cae dentro del compás 1 (clave=${clef.x}, caja=${caja.left}..${caja.right})",
            clef.x > caja.left && clef.x < caja.right
        )
        // El compás 1 es más ancho que el 2 por la cabecera que arrastra.
        val segundo = drawing.measureBounds[1]
        assertTrue(
            "el compás 1 es más ancho que el 2 por la cabecera",
            (caja.right - caja.left) > (segundo.right - segundo.left)
        )
    }

    @Test
    fun laClaveDeTablaturaEsElGlifoDeLaFuenteMusicalYNoElTexto() {
        // (D-55) El Dominio entrega el **punto de código SMuFL** (`6stringTabClef` / `4stringTabClef`),
        // no la cadena `TAB`. Antes el lienzo adivinaba el glifo por su cuenta y el PDF de escritorio
        // imprimía el texto literal `TAB` en Helvetica.
        val seis = TablatureSystemsLayout.layout(tab(2), maxWidth = 900f).first().drawing
        val clefSeis = seis.texts.first { it.kind == TabTextKind.TAB_CLEF }
        assertEquals("la clave de 6 cuerdas es el glifo SMuFL", TabGlyphs.tabClef(6), clefSeis.text)
        assertTrue("no es el texto literal", clefSeis.text != "TAB")

        val cuatro = TablatureSystemsLayout.layout(
            tab(2).copy(tuning = Tuning.UKULELE),
            maxWidth = 900f
        ).first().drawing
        val clefCuatro = cuatro.texts.first { it.kind == TabTextKind.TAB_CLEF }
        assertEquals("la clave de 4 cuerdas es su glifo", TabGlyphs.tabClef(4), clefCuatro.text)
        assertTrue("las dos claves son glifos distintos", clefSeis.text != clefCuatro.text)
    }
}
