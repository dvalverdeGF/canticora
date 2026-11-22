package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabDynamic
import com.grafitto.laudate.domain.model.tablature.TabFrettingFinger
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPickingFinger
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Correcciones del usuario** (U-1…U-6, `ROADMAP_TABLATURES.md` §1.4): las zonas de los símbolos, la
 * compatibilidad, los números de compás y las figuras debajo de la tablatura.
 */
class TabUsuarioCorreccionesTest {

    private fun tab(measures: List<TabMeasure>) = Tablature(
        id = "t",
        songId = "song-1",
        title = "T",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.EIGHTH,
        measures = measures
    )

    private fun unaNota(vararg tech: TabTechnique) = listOf(
        TabMeasure(
            slots = listOf(
                TabSlot(
                    strings = listOf(
                        TabPluck(
                            position = TabPosition(1, 3),
                            techniques = tech.toSet()
                        )
                    )
                )
            )
        )
    )

    // ── U-2 · Compatibilidad ──────────────────────────────────────────────────────────────────

    @Test
    fun losArcosDePuaSonExcluyentes() {
        val conArriba = unaNota(TabTechnique.PICK_UP)
        val conAmbos = TablatureEditing.toggleTechnique(conArriba, 0, 0, 1, TabTechnique.PICK_DOWN)
        val pluck = conAmbos[0].slots[0].strings.single()
        assertFalse("no puede haber arco arriba y abajo a la vez", pluck.has(TabTechnique.PICK_UP))
        assertTrue("queda el que se acaba de marcar", pluck.has(TabTechnique.PICK_DOWN))
    }

    @Test
    fun elArmonicoYLaCuerdaApagadaSeExcluyen() {
        val conArmonico = unaNota(TabTechnique.HARMONIC)
        val conApagada = TablatureEditing.toggleTechnique(conArmonico, 0, 0, 1, TabTechnique.MUTED)
        val pluck = conApagada[0].slots[0].strings.single()
        assertFalse(pluck.has(TabTechnique.HARMONIC))
        assertTrue(pluck.has(TabTechnique.MUTED))
    }

    @Test
    fun unBendNoPuedeSerAscendenteYDescendenteALaVez() {
        val conBend = unaNota(TabTechnique.BEND)
        val conBendDown = TablatureEditing.toggleTechnique(conBend, 0, 0, 1, TabTechnique.BEND_DOWN)
        val pluck = conBendDown[0].slots[0].strings.single()
        assertFalse(pluck.has(TabTechnique.BEND))
        assertTrue(pluck.has(TabTechnique.BEND_DOWN))
    }

    @Test
    fun elPicadoYElTenutoSeCombinanEnPortato() {
        // (D-40) Antes se excluían. MuseScore **no** los excluye: los dibuja con el glifo combinado
        // `articTenutoStaccatoAbove` (U+E4B2), el **portato** (`ARTICULATION_ABOVE_JOIN_GROUPS`).
        val conPicado = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(strings = listOf(TabPluck(1, 3).copy(articulations = setOf(TabArticulation.STACCATO))))
                )
            )
        )
        val conTenuto = TablatureEditing.toggleArticulation(conPicado, 0, 0, 1, TabArticulation.TENUTO)
        val pluck = conTenuto[0].slots[0].strings.single()
        assertEquals(setOf(TabArticulation.TENUTO_STACCATO), pluck.articulations)
    }

    @Test
    fun elPicadisimoYElTenutoSiSeExcluyen() {
        // El picadísimo es un **grado** del picado: no tiene glifo combinado con el tenuto, así que
        // marcar tenuto retira el picadísimo (desviación firmada frente a MuseScore, que los apila).
        val conPicadisimo = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(
                        strings = listOf(
                            TabPluck(1, 3).copy(articulations = setOf(TabArticulation.STACCATISSIMO))
                        )
                    )
                )
            )
        )
        val conTenuto = TablatureEditing.toggleArticulation(conPicadisimo, 0, 0, 1, TabArticulation.TENUTO)
        val pluck = conTenuto[0].slots[0].strings.single()
        assertFalse(pluck.articulations.contains(TabArticulation.STACCATISSIMO))
        assertTrue(pluck.articulations.contains(TabArticulation.TENUTO))
    }

    @Test
    fun lasTecnicasCompatiblesSiSeCombinan() {
        // Un **slide** y un **vibrato** no se contradicen: se combinan (y se dibujan los dos).
        val conSlide = unaNota(TabTechnique.SLIDE)
        val conVibrato = TablatureEditing.toggleTechnique(conSlide, 0, 0, 1, TabTechnique.VIBRATO)
        val pluck = conVibrato[0].slots[0].strings.single()
        assertTrue(pluck.has(TabTechnique.SLIDE))
        assertTrue(pluck.has(TabTechnique.VIBRATO))
    }

    @Test
    fun problemsOfDetectaUnaCombinacionImposible() {
        val pluck = TabPluck(
            position = TabPosition(1, 3),
            techniques = setOf(TabTechnique.PICK_UP, TabTechnique.PICK_DOWN)
        )
        assertTrue(TabSymbolCompatibility.problemsOf(pluck).isNotEmpty())
    }

    // ── U-3 · Números de compás ────────────────────────────────────────────────────────────────

    @Test
    fun elNumeroDeCompasSoloAparaceDesdeElSegundoSistema() {
        // Con **una sola** línea no se numera ningún compás (como MuseScore).
        val uno = TablatureSystemsLayout.layout(tab(unaNota()), maxWidth = 900f)
        assertEquals(1, uno.size)
        assertTrue(
            "con un solo sistema no hay números",
            uno.single().drawing.texts.none { it.kind == TabTextKind.MEASURE_NUMBER }
        )
        // Con **varios**, se numera solo el primer compás de cada sistema **menos el primero**.
        // Con **varios** compases y un ancho corto, el reparto hace varias líneas.
        val muchos = TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, 8, withGuideColumns = true
        )
        val varios = TablatureSystemsLayout.layout(tab(muchos), maxWidth = 220f)
        assertTrue("debe haber varios sistemas", varios.size > 1)
        assertTrue(
            "el primer sistema no lleva número",
            varios.first().drawing.texts.none { it.kind == TabTextKind.MEASURE_NUMBER }
        )
        assertTrue(
            "el segundo sí",
            varios[1].drawing.texts.any { it.kind == TabTextKind.MEASURE_NUMBER }
        )
    }

    // ── D-5 · Dinámicas debajo · U-5/U-6 · figuras y digitación debajo ─────────────────────────

    /**
     * **D-5** corrige la preferencia **U-4** (que pedía las dinámicas encima): MuseScore las coloca
     * **debajo** del pentagrama (`styleDef(dynamicsPlacement, PlacementV::BELOW)` en `styledef.cpp`),
     * y así aparecen también en el cancionero de referencia. La dinámica va por debajo de la rejilla
     * y por encima de las figuras rítmicas, que es el orden de MuseScore.
     */
    @Test
    fun laDinamicaVaDebajoDeLaRejilla() {
        val measures = listOf(
            TabMeasure(
                slots = listOf(TabSlot(strings = listOf(TabPluck(1, 3)))),
                dynamic = TabDynamic.PPP
            )
        )
        val drawing = TablatureSystemsLayout.layout(tab(measures), maxWidth = 900f).single().drawing
        val dinamica = drawing.texts.single { it.kind == TabTextKind.DYNAMIC }
        assertTrue("la dinámica va por DEBAJO de la cuerda inferior", dinamica.y > drawing.gridBottom)
    }

    @Test
    fun laDinamicaVaDebajoDeLasFigurasRitmicas() {
        val measures = listOf(
            TabMeasure(
                slots = listOf(TabSlot(strings = listOf(TabPluck(1, 3)), rhythmicFigure = RhythmFigure.QUARTER)),
                dynamic = TabDynamic.PPP
            )
        )
        val drawing = TablatureSystemsLayout.layout(tab(measures), maxWidth = 900f).single().drawing
        val dinamica = drawing.texts.single { it.kind == TabTextKind.DYNAMIC }
        val figura = drawing.rhythmMarks.single()
        assertTrue("la dinámica va por debajo de la figura rítmica", dinamica.y > figura.y)
    }

    @Test
    fun laDinamicaDejaElAireDeMuseScoreSobreLasFiguras() {
        // (D-20) `styleDef(dynamicsMinDistance, 0.5_sp)` (`styledef.cpp:822`). Medido del
        // `salmo2.pdf`: las plicas y barras acaban en 11,56 espacios y el `mp` empieza en 12,05, o
        // sea 0,49 espacios de aire. Sin él la dinámica quedaba pegada a las plicas.
        val measures = listOf(
            TabMeasure(
                slots = listOf(TabSlot(strings = listOf(TabPluck(1, 3)), rhythmicFigure = RhythmFigure.QUARTER)),
                dynamic = TabDynamic.MP
            )
        )
        val settings = TabLayoutSettings()
        val drawing = TablatureSystemsLayout.layout(tab(measures), maxWidth = 900f, settings = settings)
            .single().drawing
        val dinamica = drawing.texts.single { it.kind == TabTextKind.DYNAMIC }
        val figura = drawing.rhythmMarks.single()
        val aire = (dinamica.y - figura.y) / settings.staffSpace
        assertTrue(
            "la dinámica deja al menos dynamicsMinDistance (0,5 espacios) sobre la figura: $aire",
            aire >= TabStaffZones.DYNAMIC_MIN_DISTANCE_SPACES
        )
    }

    @Test
    fun laDinamicaSeCentraEnLaPrimeraColumnaDelCompas() {
        // (D-5) MuseScore ancla la dinámica al pulso donde se escribió. El modelo guarda una por
        // compás, así que el ancla fiel es la **primera columna**, no el centro del compás: en el
        // `salmo2.pdf` el `mp` cae a 1,16 espacios de la barra, justo sobre la primera nota.
        val measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(strings = listOf(TabPluck(1, 3)), rhythmicFigure = RhythmFigure.QUARTER),
                    TabSlot(strings = listOf(TabPluck(2, 0)), rhythmicFigure = RhythmFigure.QUARTER),
                    TabSlot(strings = listOf(TabPluck(3, 0)), rhythmicFigure = RhythmFigure.QUARTER),
                    TabSlot(strings = listOf(TabPluck(4, 0)), rhythmicFigure = RhythmFigure.QUARTER)
                ),
                dynamic = TabDynamic.MP
            )
        )
        val drawing = TablatureSystemsLayout.layout(tab(measures), maxWidth = 900f).single().drawing
        val dinamica = drawing.texts.single { it.kind == TabTextKind.DYNAMIC }
        val primera = drawing.texts.filter { it.kind == TabTextKind.FRET }.minByOrNull { it.x }!!
        assertEquals("la dinámica se centra en la primera columna", primera.x, dinamica.x, 1.0f)
    }

    @Test
    fun lasFigurasYLaDigitacionVanDebajoDeLaRejilla() {
        val measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(
                        strings = listOf(
                            TabPluck(
                                position = TabPosition(1, 3),
                                frettingFinger = TabFrettingFinger.INDEX,
                                pickingFinger = TabPickingFinger.THUMB
                            )
                        ),
                        rhythmicFigure = RhythmFigure.QUARTER
                    )
                )
            )
        )
        val drawing = TablatureSystemsLayout.layout(tab(measures), maxWidth = 900f).single().drawing
        // (2026-10-12) La figura de duración **cuelga** de la cuerda inferior: su ancla es
        // `gridBottom` (el borde de abajo de la rejilla), con la plica por debajo. Es como MuseScore
        // con `stemsDown=1`: ya no hay cabezas ni margen.
        assertTrue(
            "la figura se ancla en la cuerda inferior",
            drawing.rhythmMarks.all { it.y <= drawing.gridBottom + 0.01f }
        )
        // La digitación sí va **por debajo** de la rejilla.
        val dedos = drawing.glyphs.filter {
            it.kind == TabGlyphKind.FRETTING_FINGER || it.kind == TabGlyphKind.PICKING_FINGER
        }
        assertEquals(2, dedos.size)
        assertTrue("la digitación va DEBAJO", dedos.all { it.y > drawing.gridBottom })
        // (U-6) Las **dos manos** no se pisan: cada una a un lado del centro.
        val izq = dedos.first { it.kind == TabGlyphKind.FRETTING_FINGER }
        val der = dedos.first { it.kind == TabGlyphKind.PICKING_FINGER }
        assertTrue("mano izquierda a la izquierda", izq.x < der.x)
    }

    @Test
    fun laCifraNoLlevaNingunSimboloDentro() {
        // (U-1) Dentro de la rejilla solo va la cifra: ningún texto de adorno compartiendo su `y`.
        val measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(
                        strings = listOf(
                            TabPluck(
                                position = TabPosition(1, 3),
                                techniques = setOf(TabTechnique.PICK_UP),
                                articulations = setOf(TabArticulation.ACCENT),
                                frettingFinger = TabFrettingFinger.INDEX
                            )
                        ),
                        rhythmicFigure = RhythmFigure.QUARTER
                    )
                ),
                dynamic = TabDynamic.MF
            )
        )
        val drawing = TablatureSystemsLayout.layout(tab(measures), maxWidth = 900f).single().drawing
        val cifra = drawing.texts.single { it.kind == TabTextKind.FRET }
        val enLaMismaAltura = drawing.texts.filter {
            it.kind != TabTextKind.FRET && kotlin.math.abs(it.y - cifra.y) < 0.01f
        }
        assertTrue("nada comparte la altura de la cifra", enLaMismaAltura.isEmpty())
    }

    @Test
    fun elTipoCommonLlevaFigurasYElSimpleNo() {
        // El **defecto** es el tipo `Common` (cifras + plicas y barras), que es el del fichero.
        assertEquals(NoteValueStyle.STEMS_AND_BEAMS, TabStaffOptions().noteValues)
        assertEquals(NoteValueStyle.NONE, TabStaffOptions.SIMPLE.noteValues)
        val measures = listOf(
            TabMeasure(slots = listOf(TabSlot(strings = listOf(TabPluck(1, 3)), rhythmicFigure = RhythmFigure.QUARTER)))
        )
        val conCommon = TablatureSystemsLayout.layout(
            tab(measures), maxWidth = 900f, settings = TabLayoutSettings(staff = TabStaffOptions.COMMON)
        ).single().drawing
        assertTrue("Common dibuja figuras", conCommon.rhythmMarks.isNotEmpty())
        val conSimple = TablatureSystemsLayout.layout(
            tab(measures), maxWidth = 900f, settings = TabLayoutSettings(staff = TabStaffOptions.SIMPLE)
        ).single().drawing
        assertTrue("Simple no dibuja figuras", conSimple.rhythmMarks.isEmpty())
    }

    // ── Punto 5 · Los silencios van en su línea, no en la cuerda 6 ─────────────────────────────

    @Test
    fun elSilencioDeBlancaSeApoyaEnLaCuartaCuerdaYElDeRedondaPendeDeLaTercera() {
        // Pentagrama de 6 cuerdas: la línea «natural» es la 4.ª (índice 3) y la redonda sube una.
        // Medido del `salmo2.pdf`: el silencio de blanca del tresillo apoya su tinta en y = 628
        // (4.ª línea) y el de redonda cuelga de y = 597 (3.ª línea).
        val measures = listOf(
            TabMeasure(slots = listOf(TabSlot(rhythmicFigure = RhythmFigure.HALF_REST))),
            TabMeasure(slots = listOf(TabSlot(rhythmicFigure = RhythmFigure.WHOLE_REST)))
        )
        val drawing = TablatureSystemsLayout.layout(tab(measures), maxWidth = 900f).single().drawing
        val spacing = drawing.stringSpacing
        val top = drawing.gridTop
        val blanca = drawing.rhythmMarks.single { it.figure == RhythmFigure.HALF_REST }
        val redonda = drawing.rhythmMarks.single { it.figure == RhythmFigure.WHOLE_REST }
        assertEquals("la blanca se apoya en la 4.ª línea", top + 3f * spacing, blanca.y, 0.01f)
        assertEquals("la redonda pende de la 3.ª línea", top + 2f * spacing, redonda.y, 0.01f)
        // Y **no** en la cuerda 6 (el bug que reportó el usuario: una miniatura pegada abajo).
        assertTrue(
            "ningún silencio cae en la cuerda 6",
            blanca.y < top + 5f * spacing && redonda.y < top + 5f * spacing
        )
    }

    @Test
    fun unCompasVacioSeRellenaConUnSilencioDeRedondaEnSuLinea() {
        val drawing = TablatureSystemsLayout.layout(
            tab(listOf(TabMeasure(slots = emptyList()))),
            maxWidth = 900f
        ).single().drawing
        val redonda = drawing.rhythmMarks.single()
        assertEquals(RhythmFigure.WHOLE_REST, redonda.figure)
        assertEquals(drawing.gridTop + 2f * drawing.stringSpacing, redonda.y, 0.01f)
    }

    /**
     * Un compás con un **silencio de compás explícito** (lo que produce el parser del `.mscz` para
     * `R:measure`) se dibuja igual que uno vacío: con su redonda en la 3.ª línea. Antes el parser
     * devolvía una columna **vacía** y el compás salía en blanco.
     */
    /**
     * (2026-10-27) **La rejilla de trabajo del editor no es musica.** Un `4/4` recien abierto tiene
     * ocho columnas de silencio de corchea (la rejilla, que hace falta para saber donde cae un
     * clic), pero el lector tiene que ver **un unico silencio de compas**, como en MuseScore. Antes
     * se pintaban los ocho y la pantalla se llenaba de silencios al abrir el editor («salen
     * silencios a lo loco»).
     */
    @Test
    fun laRejillaDeTrabajoSeDibujaComoUnUnicoSilencioDeCompas() {
        val rejilla = TablatureEditing.emptyCompleteMeasure(
            ScoreTimeSignature("4", 4),
            TabResolution.EIGHTH
        )
        val drawing = TablatureSystemsLayout.layout(
            tab(listOf(rejilla)),
            maxWidth = 900f
        ).single().drawing
        val marcas = drawing.rhythmMarks
        assertEquals("una rejilla intacta se dibuja con UN silencio", 1, marcas.size)
        assertEquals(RhythmFigure.WHOLE_REST, marcas.single().figure)
    }

    /**
     * En cambio, **cuatro silencios de negra escritos** son musica y se dibujan los cuatro: el
     * usuario los ha puesto ahi.
     */
    @Test
    fun cuatroSilenciosDeNegraEscritosSeDibujanLosCuatro() {
        val escrito = TabMeasure(
            slots = List(4) { TabSlot(rhythmicFigure = RhythmFigure.QUARTER_REST) }
        )
        val drawing = TablatureSystemsLayout.layout(
            tab(listOf(escrito)),
            maxWidth = 900f
        ).single().drawing
        assertEquals(4, drawing.rhythmMarks.size)
    }

    @Test
    fun unCompasConSilencioDeCompasExplicitoDibujaSuRedonda() {
        val drawing = TablatureSystemsLayout.layout(
            tab(listOf(TabMeasure(slots = listOf(TabSlot(rhythmicFigure = RhythmFigure.WHOLE_REST))))),
            maxWidth = 900f
        ).single().drawing
        val redonda = drawing.rhythmMarks.single()
        assertEquals(RhythmFigure.WHOLE_REST, redonda.figure)
        assertEquals(drawing.gridTop + 2f * drawing.stringSpacing, redonda.y, 0.01f)
    }

    /**
     * **Tresillo**: las tres columnas marcadas con `isTriplet` se dibujan con su figura (blanca) y
     * con la marca de tresillo, y su ancho es **2/3** del de la figura (una blanca de tresillo dura
     * como una negra con puntillo). Sin la marca, el tresillo se dibujaba como tres blancas sueltas.
     */
    @Test
    fun unTresilloDeBlancasSeMarcaYSeEstrecha() {
        val tresillo = TabMeasure(
            slots = List(3) { TabSlot(rhythmicFigure = RhythmFigure.HALF_REST, tuplet = TabTuplet.TRIPLET) }
        )
        val suelto = TabMeasure(slots = listOf(TabSlot(rhythmicFigure = RhythmFigure.HALF_REST)))
        val drawing = TablatureSystemsLayout.layout(
            tab(listOf(tresillo, suelto)),
            maxWidth = 900f
        ).single().drawing

        val marcas = drawing.rhythmMarks.filter { it.figure == RhythmFigure.HALF_REST }
        assertEquals("tres columnas del tresillo + una suelta", 4, marcas.size)
        assertTrue("las tres primeras son el tresillo", marcas.take(3).all { it.isTriplet })
        assertTrue("la cuarta no es tresillo", !marcas[3].isTriplet)

        // El tresillo dura 2/3: su columna es más estrecha que la blanca suelta.
        val anchoTresillo = drawing.columns.getValue(0).first().width
        val anchoSuelto = drawing.columns.getValue(1).first().width
        assertTrue(
            "la columna del tresillo ($anchoTresillo) es más estrecha que la suelta ($anchoSuelto)",
            anchoTresillo < anchoSuelto
        )
    }
}
