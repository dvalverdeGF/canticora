package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **C.1 · Espaciado por duración** (roadmap §4, algoritmo de TuxGuitar `getSpacingForQuarter`): el
 * ancho de cada columna sale de su **duración**, no de una rejilla uniforme (arregla F-04).
 */
class TabSpacingTest {

    private val cell = TabLayoutSettings().cellWidth

    private fun tab(measures: List<TabMeasure>, resolution: TabResolution = TabResolution.QUARTER) = Tablature(
        id = "t",
        songId = "song-1",
        title = "T",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = resolution,
        measures = measures
    )

    private fun note(figure: RhythmFigure? = null, triplet: Boolean = false) = TabSlot(
        strings = listOf(TabPluck(1, 3)),
        rhythmicFigure = figure,
        tuplet = if (triplet) TabTuplet.TRIPLET else null
    )

    // ── La ley de espaciado ─────────────────────────────────────────────────────────────────────

    @Test
    fun columnWidthCreceConLaDuracionDeFormaSublineal() {
        // Ley REAL de MuseScore (medida del PDF): `ancho ∝ duración^0,585`. La corchea mide
        // 1/1,499 de la negra, NO la mitad.
        val negra = TabSpacing.columnWidth(TabSpacing.beatsOf(RhythmFigure.QUARTER), cell)
        val corchea = TabSpacing.columnWidth(TabSpacing.beatsOf(RhythmFigure.EIGHTH), cell)
        assertEquals(cell, negra, 0.001f)
        assertEquals(cell / 1.499f, corchea, 0.02f)
    }

    @Test
    fun elPuntilloUsaSuDuracionConLaMismaLey() {
        val negra = TabSpacing.columnWidth(TabSpacing.beatsOf(RhythmFigure.QUARTER), cell)
        val conPuntillo = TabSpacing.columnWidth(
            TabSpacing.beatsOf(RhythmFigure.DOTTED_QUARTER),
            cell
        )
        assertTrue("la negra con puntillo es más ancha", conPuntillo > negra)
        assertEquals("1,5 negras → 1,5^0,585 = 1,269×", 1.269f, conPuntillo / negra, 0.01f)
    }

    @Test
    fun elTresilloEncogeDosTercios() {
        val normal = TabSpacing.beatsOf(RhythmFigure.QUARTER, null, TabResolution.QUARTER)
        val tresillo = TabSpacing.beatsOf(RhythmFigure.QUARTER, TabTuplet.TRIPLET, TabResolution.QUARTER)
        assertEquals(1.0, normal, 0.001)
        assertEquals(2.0 / 3.0, tresillo, 0.001)
    }

    @Test
    fun hayUnAnchoMinimoGeometrico() {
        // Aunque la duración sea mínima, la columna no baja de `MIN_FRACTION · cell`.
        val tiny = TabSpacing.columnWidth(0.01, cell)
        assertEquals(cell * TabSpacing.MIN_FRACTION, tiny, 0.001f)
    }

    @Test
    fun elSilencioUsaLaDuracionDeSuFiguraEquivalente() {
        assertEquals(
            TabSpacing.beatsOf(RhythmFigure.EIGHTH),
            TabSpacing.beatsOf(RhythmFigure.EIGHTH_REST),
            0.001
        )
    }

    // ── Integración con la maquetación ───────────────────────────────────────────────────────────

    @Test
    fun unCompasDeOchoCorcheasEsMasAnchoQueUnoDeDosNegras() {
        // Ley sublineal (medida del PDF): 8 corcheas ≈ 1,33× lo de 4 negras, no el doble. Se comparan
        // las **columnas**, no la caja del compás: la caja incluye el aire constante de las barras
        // (`barNoteDistance` + `noteBarDistance`), que diluiría la razón sin cambiar la ley.
        val measures = listOf(
            TabMeasure(slots = List(4) { note(RhythmFigure.QUARTER) }),   // 4 negras
            TabMeasure(slots = List(8) { note(RhythmFigure.EIGHTH) })     // 8 corcheas
        )
        // (D-50) Con la **escala de MuseScore** (`cellWidth = 3,5 espacios`), no con el `cellWidth`
        // suelto de 22 px: con 22 px la corchea mide 14,7 px y el **ancho de la cifra** (20,2 px) la
        // domina, así que la ley de duración quedaría tapada por el mínimo del texto. Aquí se mide la
        // ley, no el mínimo.
        val settings = TabLayoutSettings(
            cellWidth = TabSpacing.quarterWidth(20f / TabRhythmGeometry.STRING_SPACING_SPACES),
            stringSpacing = 20f
        )
        val drawing = TablatureSystemsLayout.layout(
            tab(measures, TabResolution.EIGHTH),
            maxWidth = 5000f,
            settings = settings
        ).single().drawing
        val anchoNegras = drawing.columns[0].orEmpty().sumOf { it.width.toDouble() }.toFloat()
        val anchoCorcheas = drawing.columns[1].orEmpty().sumOf { it.width.toDouble() }.toFloat()
        assertEquals("las 8 corcheas son ~1,33× las 4 negras", 1.335f, anchoCorcheas / anchoNegras, 0.03f)
        assertTrue("y desde luego no ocupan lo mismo (F-04)", anchoCorcheas > anchoNegras)
    }

    @Test
    fun lasColumnasMidenSegunSuDuracionEnElDibujo() {
        val measure = TabMeasure(
            slots = listOf(
                note(RhythmFigure.HALF),    // blanca = 2 negras
                note(RhythmFigure.QUARTER)  // negra = 1 negra
            )
        )
        val drawing = TablatureSystemsLayout.layout(tab(listOf(measure)), maxWidth = 5000f).single().drawing
        val boxes = drawing.columns.getValue(0)
        assertEquals(2, boxes.size)
        // 2^0,585 = 1,5× (sublineal).
        assertEquals("la blanca es ~1,5× la negra", 1.5f, boxes[0].width / boxes[1].width, 0.03f)
    }

    @Test
    fun unaTablaturaUniformeEnNegrasMideLoQueUnaNegra() {
        // Si todas las columnas son negras, cada una mide el ancho base (una negra).
        val measure = TabMeasure(slots = List(4) { note(RhythmFigure.QUARTER) })
        val drawing = TablatureSystemsLayout.layout(tab(listOf(measure)), maxWidth = 5000f).single().drawing
        val boxes = drawing.columns.getValue(0)
        assertEquals(cell, boxes[1].centerX - boxes[0].centerX, 0.05f)
    }

    // ── D-50 · El ancho de la cifra manda ────────────────────────────────────────────────────────

    @Test
    fun laCifraDeDosDigitosEsMasAnchaQueLaDeUno() {
        // MuseScore mide la **forma** del texto (`HorizontalSpacing::minHorizontalDistance`,
        // `horizontalspacing.cpp:1222-1290`), no un ancho fijo. Un «12» ocupa casi el doble que un «5».
        val uno = TabSpacing.fretTextWidthSpaces("5")
        val dos = TabSpacing.fretTextWidthSpaces("12")
        assertTrue("«12» es más ancho que «5»", dos > uno)
        // El bounding rect descuenta los dos side bearings (0,05 em cada uno), así que la razón no es
        // exactamente 2: `(2 · 0,556 − 0,1) / (0,556 − 0,1) = 2,22`.
        assertEquals("dos dígitos ≈ 2,22 × un dígito", 2.22f, dos / uno, 0.02f)
        // Y el valor absoluto cuadra con el `salmo2.pdf`: 0,834 espacios = 17,2 px a 300 dpi.
        assertEquals("un dígito mide 0,834 espacios", 0.834f, uno, 0.01f)
    }

    @Test
    fun laCifraConAlteracionEsMasAnchaQueLaNatural() {
        // El «♯» (0,5 em) es más estrecho que un dígito (0,556 em), pero más que un paréntesis.
        assertTrue(TabSpacing.fretTextWidthSpaces("5♯") > TabSpacing.fretTextWidthSpaces("5"))
        assertTrue(TabSpacing.fretTextWidthSpaces("(5)") > TabSpacing.fretTextWidthSpaces("5"))
    }

    @Test
    fun elAnchoDeLaCifraEsElMinimoDeLaColumna() {
        val staffSpace = 20f / TabRhythmGeometry.STRING_SPACING_SPACES
        val minimo = TabSpacing.fretColumnMinWidth("12", staffSpace)
        // Una semicorchea sola mide mucho menos que su cifra: el mínimo la ensancha.
        val natural = TabSpacing.columnWidth(TabSpacing.beatsOf(RhythmFigure.SIXTEENTH), cell)
        assertTrue("la cifra de dos dígitos supera a la semicorchea", minimo > natural)
        assertEquals(minimo, TabSpacing.columnWidth(TabSpacing.beatsOf(RhythmFigure.SIXTEENTH), cell, minimo), 0.001f)
    }

    @Test
    fun unaSemicorcheaConTrasteDeDosDigitosNoSePisa() {
        // El defecto que arregla D-50: con la rejilla de duración pura, una semicorchea medía ~9,8 px
        // y una cifra de dos dígitos necesita ~20 px → los números se solapaban.
        val measure = TabMeasure(
            slots = List(4) { TabSlot(strings = listOf(TabPluck(1, 12)), rhythmicFigure = RhythmFigure.SIXTEENTH) }
        )
        val settings = TabLayoutSettings(
            cellWidth = TabSpacing.quarterWidth(20f / TabRhythmGeometry.STRING_SPACING_SPACES),
            stringSpacing = 20f
        )
        val drawing = TablatureSystemsLayout.layout(tab(listOf(measure), TabResolution.SIXTEENTH), maxWidth = 5000f, settings = settings)
            .single().drawing
        val boxes = drawing.columns.getValue(0)
        val minimo = TabSpacing.fretColumnMinWidth("12", settings.staffSpace)
        boxes.forEach { assertEquals("la columna cabe su cifra", minimo, it.width, 0.5f) }
    }

    @Test
    fun ningunaCifraSePisaEnUnaTablaturaDensa() {
        // El invariante que D-50 tiene que garantizar **siempre**, no solo en el caso del test
        // anterior: entre dos cifras contiguas hay al menos `(w₁ + w₂)/2 + 2 · relleno` de distancia
        // entre centros, que es la separación de bordes de `minHorizontalDistance`
        // (`r1.right() - r2.left() + padding`). Se mezclan trastes de uno y de dos dígitos para que
        // los anchos sean distintos.
        val frets = listOf(1, 12, 5, 19, 3, 10, 7, 15)
        val measure = TabMeasure(
            slots = frets.map {
                TabSlot(strings = listOf(TabPluck(1, it)), rhythmicFigure = RhythmFigure.SIXTEENTH)
            }
        )
        val settings = TabLayoutSettings(
            cellWidth = TabSpacing.quarterWidth(20f / TabRhythmGeometry.STRING_SPACING_SPACES),
            stringSpacing = 20f
        )
        val boxes = TablatureSystemsLayout
            .layout(tab(listOf(measure), TabResolution.SIXTEENTH), maxWidth = 100_000f, settings = settings)
            .single().drawing.columns.getValue(0)
        assertEquals(frets.size, boxes.size)
        val anchos = frets.map { TabSpacing.fretTextWidthSpaces(it.toString()) * settings.staffSpace }
        val relleno = 2f * TabSpacing.FRET_TEXT_PADDING_SPACES * settings.staffSpace
        for (i in 1 until boxes.size) {
            val distancia = boxes[i].centerX - boxes[i - 1].centerX
            val necesaria = (anchos[i] + anchos[i - 1]) / 2f + relleno
            assertTrue(
                "las cifras ${frets[i - 1]} y ${frets[i]} se pisan: $distancia < $necesaria",
                distancia >= necesaria - 0.01f
            )
        }
    }

    // ── D-52: el zoom del número entra en la maquetación ─────────────────────────────────────────

    @Test
    fun elZoomDelNumeroEnsanchaLaColumna() {
        // El visor deja agrandar la cifra hasta el 200 % para leer de lejos en el atril
        // (`TablatureCanvas.numberScale`). Hasta D-52 ese factor solo llegaba al **pintor**: la cifra
        // se dibujaba al doble sobre una columna del tamaño normal y los números se pisaban.
        val measure = TabMeasure(
            slots = listOf(TabSlot(strings = listOf(TabPluck(1, 12)), rhythmicFigure = RhythmFigure.SIXTEENTH))
        )
        val base = TabLayoutSettings(
            cellWidth = TabSpacing.quarterWidth(20f / TabRhythmGeometry.STRING_SPACING_SPACES),
            stringSpacing = 20f
        )
        fun anchoDeColumna(escala: Float): Float = TablatureSystemsLayout
            .layout(
                tab(listOf(measure), TabResolution.SIXTEENTH),
                maxWidth = 100_000f,
                settings = base.copy(fretNumberScale = escala)
            )
            .single().drawing.columns.getValue(0).single().width

        val normal = anchoDeColumna(1f)
        val doble = anchoDeColumna(2f)
        // La cifra «12» mide 1,852 espacios de tinta; al 200 % son 3,704. La columna tiene que
        // reservarlos, no quedarse en el ancho de la cifra normal.
        val tintaDoble = TabSpacing.fretTextWidthSpaces("12") * 2f * base.staffSpace
        assertTrue(
            "la columna al 200 % ($doble) no reserva la cifra escalada ($tintaDoble)",
            doble >= tintaDoble
        )
        assertTrue("el zoom no ensancha la columna: $normal → $doble", doble > normal)
    }

    @Test
    fun elZoomDelNumeroNoCambiaNadaAlCienPorCien() {
        // El PDF y el editor usan `fretNumberScale = 1`: el valor por defecto tiene que dar
        // **exactamente** el mismo ancho que antes de D-52, o el grabado del `salmo2` se movería.
        val measure = TabMeasure(
            slots = listOf(TabSlot(strings = listOf(TabPluck(1, 5)), rhythmicFigure = RhythmFigure.QUARTER))
        )
        val settings = TabLayoutSettings(
            cellWidth = TabSpacing.quarterWidth(20f / TabRhythmGeometry.STRING_SPACING_SPACES),
            stringSpacing = 20f
        )
        fun ancho(escala: Float): Float = TablatureSystemsLayout
            .layout(
                tab(listOf(measure)),
                maxWidth = 100_000f,
                settings = settings.copy(fretNumberScale = escala)
            )
            .single().drawing.columns.getValue(0).single().width

        assertEquals(ancho(1f), ancho(1f), 0.001f)
        assertEquals(
            "el valor por defecto no puede diferir del explícito",
            ancho(1f),
            TablatureSystemsLayout
                .layout(tab(listOf(measure)), maxWidth = 100_000f, settings = settings)
                .single().drawing.columns.getValue(0).single().width,
            0.001f
        )
    }

    @Test
    fun conElNumeroAlDoscientosPorCientoLasCifrasSiguenSinPisarse() {
        // El invariante de D-50 tiene que seguir cumpliéndose con el zoom del número al máximo.
        val frets = listOf(1, 12, 5, 19, 3, 10, 7, 15)
        val measure = TabMeasure(
            slots = frets.map {
                TabSlot(strings = listOf(TabPluck(1, it)), rhythmicFigure = RhythmFigure.SIXTEENTH)
            }
        )
        val settings = TabLayoutSettings(
            cellWidth = TabSpacing.quarterWidth(20f / TabRhythmGeometry.STRING_SPACING_SPACES),
            stringSpacing = 20f,
            fretNumberScale = 2f
        )
        val boxes = TablatureSystemsLayout
            .layout(tab(listOf(measure), TabResolution.SIXTEENTH), maxWidth = 100_000f, settings = settings)
            .single().drawing.columns.getValue(0)
        assertEquals(frets.size, boxes.size)
        val anchos = frets.map {
            TabSpacing.fretTextWidthSpaces(it.toString()) * settings.fretNumberScale * settings.staffSpace
        }
        val relleno = 2f * TabSpacing.FRET_TEXT_PADDING_SPACES * settings.staffSpace
        for (i in 1 until boxes.size) {
            val distancia = boxes[i].centerX - boxes[i - 1].centerX
            val necesaria = (anchos[i] + anchos[i - 1]) / 2f + relleno
            assertTrue(
                "al 200 % las cifras ${frets[i - 1]} y ${frets[i]} se pisan: $distancia < $necesaria",
                distancia >= necesaria - 0.01f
            )
        }
    }

    // ---------------------------------------------------------------------------------------------
    // (D-54) El zoom del número abre las cuerdas sin engordar el resto del grabado.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun elZoomDelNumeroNoAbreLasCuerdasHastaElCientoSetentaYUno() {
        // Con las cuerdas a 1,5 espacios y la tinta de la cifra a 0,82, el aire se agota al 171 %:
        // por debajo, la separación no se toca ni un píxel.
        val base = 20f
        val staffSpace = base / TabRhythmGeometry.STRING_SPACING_SPACES
        listOf(1f, 1.2f, 1.5f, 1.7f).forEach { zoom ->
            assertEquals(
                "al ${(zoom * 100).toInt()} % la separación no cambia",
                base,
                TabSpacing.stringSpacingFor(base, staffSpace, zoom),
                0.001f
            )
        }
    }

    @Test
    fun alDoscientosPorCientoLasCuerdasSeAbrenLoJusto() {
        val base = 20f
        val staffSpace = base / TabRhythmGeometry.STRING_SPACING_SPACES
        val abierta = TabSpacing.stringSpacingFor(base, staffSpace, 2f)
        // La tinta al doble (1,64 espacios) más el aire mínimo (0,10) = 1,74 espacios.
        val esperada = (TabSpacing.FRET_INK_HEIGHT_SPACES * 2f + TabSpacing.FRET_INK_MIN_GAP_SPACES) * staffSpace
        assertEquals(esperada, abierta, 0.001f)
        assertTrue("las cuerdas se abren", abierta > base)
    }

    @Test
    fun abrirLasCuerdasNoEngordaElGrabado() {
        // El espacio de pentagrama (grosores, plicas, barras) se queda **fijo**: solo se mueven las
        // cuerdas. Sin esto, al 200 % la plica y la barra crecían con la separación.
        val settings = TabLayoutSettings(stringSpacing = 20f, fretNumberScale = 2f)
        val antes = settings.staffSpace
        val despues = settings.withFretNumberSpacing()
        assertTrue("las cuerdas se abren", despues.stringSpacing > settings.stringSpacing)
        assertEquals("el espacio de pentagrama no cambia", antes, despues.staffSpace, 0.001f)
    }

    @Test
    fun sinZoomLosAjustesNoSeTocan() {
        val settings = TabLayoutSettings(stringSpacing = 20f, fretNumberScale = 1f)
        assertSame("al 100 % devuelve el mismo objeto", settings, settings.withFretNumberSpacing())
    }

    @Test
    fun conCifrasEnLasSeisCuerdasAlDoscientosPorCientoNoSePisan() {
        // El caso que el zoom podía romper: una cifra en **cada** cuerda. La tinta de la cifra mide
        // 0,82 espacios al 100 %, así que al 200 % ocupa 1,64 y la separación base (1,5) ya no basta.
        val settings = TabLayoutSettings(stringSpacing = 20f, fretNumberScale = 2f)
        val drawing = TablatureSystemsLayout
            .layout(
                tab(
                    listOf(
                        TabMeasure(
                            slots = listOf(
                                TabSlot(strings = (1..6).map { TabPluck(it, 12) })
                            )
                        )
                    )
                ),
                maxWidth = 5000f,
                settings = settings
            )
            .single()
            .drawing
        val tinta = TabSpacing.FRET_INK_HEIGHT_SPACES * 2f * drawing.staffSpace
        assertTrue(
            "la cifra al 200 % mide ${tinta}px y las cuerdas van a ${drawing.stringSpacing}px",
            drawing.stringSpacing > tinta
        )
        // Y el aire que queda es, como mínimo, el declarado.
        assertTrue(
            "el aire entre cifras es ${drawing.stringSpacing - tinta}px",
            drawing.stringSpacing - tinta >= TabSpacing.FRET_INK_MIN_GAP_SPACES * drawing.staffSpace - 0.001f
        )
    }

    @Test
    fun elEspacioDelPentagramaViajaEnElDibujo() {
        // El pintor ya no puede derivar el espacio de la separación de cuerdas: el dibujo lo lleva.
        val settings = TabLayoutSettings(stringSpacing = 20f, fretNumberScale = 2f)
        val drawing = TablatureSystemsLayout
            .layout(tab(listOf(TabMeasure(slots = listOf(TabSlot(strings = listOf(TabPluck(1, 5))))))),
                maxWidth = 5000f, settings = settings)
            .single().drawing
        assertEquals("el espacio del grabado, no el de las cuerdas", 20f / 1.5f, drawing.staffSpace, 0.001f)
        assertTrue("las cuerdas sí se abren", drawing.stringSpacing > 20f)
    }
}
