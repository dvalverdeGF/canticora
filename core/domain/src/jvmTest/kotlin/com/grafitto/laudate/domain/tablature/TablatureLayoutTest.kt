package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabBendAmount
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de la maquetación vectorial de la tablatura (primitivas). */
class TablatureLayoutTest {

    private fun tab(measures: List<TabMeasure>, tuning: Tuning = Tuning.GUITAR) = Tablature(
        id = "t",
        songId = "song-1",
        title = "T",
        instrument = tuning.name,
        tuning = tuning,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.QUARTER,
        measures = measures
    )

    @Test
    fun drawsOneHorizontalLinePerString() {
        val drawing = TablatureLayout.layout(tab(TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true)))
        val horizontal = drawing.lines.count { it.y1 == it.y2 && !it.isBar }
        assertEquals(6, horizontal)
    }

    @Test
    fun drawsOneBarLinePerMeasureAndNoSystemOpeningBar() {
        // (D-4, medido del `salmo2.pdf`) MuseScore **no** dibuja barra de apertura de sistema: con 3
        // compases hay **3** barras (una al cerrar cada compás), no 4.
        val measures = TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 3, withGuideColumns = true)
        val drawing = TablatureLayout.layout(tab(measures))
        assertEquals(3, drawing.lines.count { it.isBar })
    }

    @Test
    fun fretNumber_isPlacedOnTheRightStringLine() {
        val measures = TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true)
        val withNote = TablatureEditing.togglePluck(measures, 0, 0, stringNumber = 1, fret = 3)
        val drawing = TablatureLayout.layout(tab(withNote))
        val fret = drawing.texts.single { it.kind == TabTextKind.FRET }
        assertEquals("3", fret.text)
        // La cuerda 1 es la **primera línea** (la de arriba): la cifra cae exactamente en su `y`
        // (`On lines`), no encima ni debajo.
        val line = drawing.lines.first { it.y1 == it.y2 && !it.isBar }
        assertEquals(line.y1, fret.y, 0.01f)
    }

    @Test
    fun stringLabels_areOptional_andAgudaFirst() {
        // MuseScore **no** rotula las cuerdas: por defecto el pentagrama va sin etiquetas. Cuando se
        // piden, van ordenadas de aguda (arriba) a grave (abajo).
        val measures = TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true)
        val sinEtiquetas = TablatureLayout.layout(tab(measures))
        assertEquals(0, sinEtiquetas.texts.count { it.kind == TabTextKind.STRING_LABEL })
        val conEtiquetas = TablatureLayout.layout(
            tab(measures),
            TabLayoutSettings(staff = TabStaffOptions(showStringLabels = true))
        )
        val labels = conEtiquetas.texts.filter { it.kind == TabTextKind.STRING_LABEL }
        assertEquals(6, labels.size)
        assertTrue(labels[0].y < labels[5].y)
    }

    @Test
    fun laMismaTablaturaProduceElMismoDibujoEnEditorYVisor() {
        // **S1.2 · test golden de maquetación**: la maquetación es única, así que dos llamadas con los
        // mismos ajustes devuelven exactamente el mismo dibujo. Es la garantía de que el editor y el
        // visor no pueden mostrar dos tablaturas distintas.
        val measures = TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 2, withGuideColumns = true)
        val drawing = tab(measures)
        val system = 900f
        val a = TablatureSystemsLayout.layout(drawing, maxWidth = system)
        val b = TablatureSystemsLayout.layout(drawing, maxWidth = system)
        assertEquals(a, b)
        // Y la maquetación publica la **rejilla de interacción**: bordes del pentagrama y cajas.
        val first = a.first().drawing
        assertTrue(first.gridTop < first.gridBottom)
        assertTrue(first.gridLeft < first.gridRight)
        assertTrue(first.measureBounds.size == 2)
        assertEquals(2, first.columns.size)
        assertTrue(first.columns.values.all { it.isNotEmpty() })
    }

    @Test
    fun elCompasYLaClaveSeDibujanAlInicioDelSistema() {
        val drawing = TablatureSystemsLayout.layout(
            tab(TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true)),
            maxWidth = 900f
        ).single().drawing
        // Clave `TAB` (glifo, S3.1) y compás en fracción: numerador y denominador apilados.
        // (D-55) La clave es el **punto de código SMuFL** `6stringTabClef`, no el literal `TAB`: así
        // el lienzo y los dos PDF dibujan el mismo glifo sin adivinar el número de cuerdas cada uno.
        assertTrue(
            drawing.texts.any { it.kind == TabTextKind.TAB_CLEF && it.text == TabGlyphs.tabClef(6) }
        )
        val num = drawing.texts.single { it.kind == TabTextKind.TIME_NUMERATOR }
        val den = drawing.texts.single { it.kind == TabTextKind.TIME_DENOMINATOR }
        assertEquals("4", num.text)
        assertEquals("4", den.text)
        assertEquals(num.x, den.x, 0.01f)
        assertTrue(num.y < den.y)
    }

    @Test
    fun elNumeroDeCompasVaAlPrincipioDeCadaSistemaDesdeElSegundo() {
        // (U-3) Como MuseScore: solo se numera el **primer compás de cada sistema**, y **solo a
        // partir del segundo** sistema. Con una sola línea, no hay ningún número.
        val measures = TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 6, withGuideColumns = true)
        // El ancho se deriva del **natural** para que el test no dependa de números mágicos: con la
        // cabecera dentro del sistema (F-04.quater) un ancho fijo dejaba de caber en una línea.
        val natural = TablatureSystemsLayout.naturalWidth(tab(measures), TabLayoutSettings())
        val unaLinea = TablatureSystemsLayout.layout(tab(measures), maxWidth = natural * 1.2f).single().drawing
        assertTrue(
            "con un solo sistema no se numera ningún compás",
            unaLinea.texts.none { it.kind == TabTextKind.MEASURE_NUMBER }
        )
        // Con varios sistemas, se numera el **primer compás** de los sistemas **2.º en adelante**.
        val sistemas = TablatureSystemsLayout.layout(tab(measures), maxWidth = natural / 3f)
        assertTrue("debe haber varios sistemas", sistemas.size > 1)
        assertTrue(
            "el primer sistema no lleva número",
            sistemas.first().drawing.texts.none { it.kind == TabTextKind.MEASURE_NUMBER }
        )
        assertTrue(
            "el segundo sí, y uno solo (el de su primer compás)",
            sistemas[1].drawing.texts.count { it.kind == TabTextKind.MEASURE_NUMBER } == 1
        )
    }

    @Test
    fun heldSlot_drawsTildeMarks() {
        val measures = TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true)
        val held = TablatureEditing.toggleHeld(measures, 0, 1)
        val drawing = TablatureLayout.layout(tab(held))
        // Una marca por cuerda (6) en la casilla prolongada.
        assertEquals(6, drawing.texts.count { it.kind == TabTextKind.HELD })
    }

    @Test
    fun size_isPositiveAndGrowsWithMeasures() {
        val one = TablatureLayout.layout(tab(TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true)))
        val two = TablatureLayout.layout(tab(TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 2, withGuideColumns = true)))
        assertTrue(one.width > 0f && one.height > 0f)
        assertTrue(two.width > one.width)
    }

    @Test
    fun bassTuning_drawsFourLines() {
        val drawing = TablatureLayout.layout(
            tab(TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 1, withGuideColumns = true), Tuning.BASS_4)
        )
        assertEquals(4, drawing.lines.count { it.y1 == it.y2 && !it.isBar })
    }

    @Test
    fun pluckOnUnknownString_isIgnored() {
        val measures = listOf(TabMeasure(slots = listOf(TabSlot(strings = listOf(TabPluck(99, 3))))))
        val drawing = TablatureLayout.layout(tab(measures))
        assertEquals(0, drawing.texts.count { it.kind == TabTextKind.FRET })
    }

    @Test
    fun techniqueWithGlyph_emitsAVectorialSymbol() {
        val measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(strings = listOf(TabPluck(1, 3))),
                    TabSlot(
                        strings = listOf(
                            TabPluck(1, 5, com.grafitto.laudate.domain.model.tablature.TabTechnique.BEND)
                        )
                    )
                )
            )
        )
        val drawing = TablatureLayout.layout(tab(measures))
        // El bend se dibuja con símbolo vectorial, no con texto, y su arco enlaza con la nota anterior.
        val bend = drawing.glyphs.single()
        assertEquals(TabGlyphKind.BEND, bend.kind)
        assertTrue(bend.from.x < bend.to.x)
    }

    @Test
    fun ligaduraDeProlongacion_emiteUnArcoEntreLasDosCifras() {
        // B-10: el `tie` se dibuja como un arco propio (`TIE`) que va de la cifra de origen a la de
        // destino, sobre la misma cuerda. No es un `SLUR` (ese une alturas distintas).
        val measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(strings = listOf(TabPluck(TabPosition(1, 3), tiedToNext = true))),
                    TabSlot(strings = listOf(TabPluck(1, 3)))
                )
            )
        )
        val drawing = TablatureLayout.layout(tab(measures))
        val tie = drawing.glyphs.single { it.kind == TabGlyphKind.TIE }
        assertTrue("el arco va de izquierda a derecha", tie.from.x < tie.to.x)
        // El arco está **pegado a la cuerda**: misma `y` en los dos extremos.
        assertEquals(tie.from.y, tie.to.y, 0.01f)
    }

    @Test
    fun sinLigadura_noSeEmiteNingunArcoTIE() {
        val measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(strings = listOf(TabPluck(1, 3))),
                    TabSlot(strings = listOf(TabPluck(1, 3)))
                )
            )
        )
        val drawing = TablatureLayout.layout(tab(measures))
        assertTrue(drawing.glyphs.none { it.kind == TabGlyphKind.TIE })
    }

    @Test
    fun laPuaSeDibujaComoSignoSobreLaCuerda_noDentroDeLaCifra() {
        // La púa es un **arco** (SMuFL `stringsDownBow`), no una flecha pegada al número: la cifra
        // queda limpia (`3`) y el arco va como signo aparte, sobre la cuerda.
        val measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(
                        strings = listOf(
                            TabPluck(1, 3, com.grafitto.laudate.domain.model.tablature.TabTechnique.PICK_DOWN)
                        )
                    )
                )
            )
        )
        val drawing = TablatureLayout.layout(tab(measures))
        assertTrue(drawing.glyphs.isEmpty())
        assertTrue("la cifra queda limpia", drawing.texts.any { it.text == "3" })
        assertTrue(
            "el arco va como signo aparte",
            drawing.texts.any { it.text == TabArticulation.DOWN_BOW.display }
        )
    }

    @Test
    fun fretText_keepsOnlyTheFret_andPickPrefix() {
        // Las técnicas "de adorno" (trémolo, ligadura, bend, vibrato, armónico, P.M., l.v.) se
        // dibujan con **símbolo vectorial**, no dentro del texto: la cifra queda limpia y no se
        // recorta. Solo la púa va como prefijo, porque es parte de cómo se lee el ataque.
        assertEquals("3", fretText(3, com.grafitto.laudate.domain.model.tablature.TabTechnique.NONE))
        assertEquals("3", fretText(3, com.grafitto.laudate.domain.model.tablature.TabTechnique.TREMOLO))
        assertEquals("3", fretText(3, com.grafitto.laudate.domain.model.tablature.TabTechnique.BEND))
        assertEquals("3", fretText(3, com.grafitto.laudate.domain.model.tablature.TabTechnique.PALM_MUTE))
        assertEquals("3", fretText(3, com.grafitto.laudate.domain.model.tablature.TabTechnique.LET_RING))
        // La púa **tampoco** va en el texto: es un arco aparte (como en MuseScore).
        assertEquals("3", fretText(3, com.grafitto.laudate.domain.model.tablature.TabTechnique.PICK_DOWN))
        assertEquals("3", fretText(3, com.grafitto.laudate.domain.model.tablature.TabTechnique.PICK_UP))
    }

    @Test
    fun fretText_withTechniqueSet_writesMutedAndGhostInPlaceOfTheFret() {
        // La X del apagado y el paréntesis de la nota fantasma **sustituyen** a la cifra: son la
        // propia nota, no un adorno encima. El resto de marcados se combinan sin ensuciar el texto.
        assertEquals("X", fretText(3, setOf(TabTechnique.MUTED)))
        assertEquals("(3)", fretText(3, setOf(TabTechnique.GHOST)))
        assertEquals("3", fretText(3, setOf(TabTechnique.TAPPING)))
        assertEquals("3", fretText(3, setOf(TabTechnique.BEND_DOWN)))
        assertEquals("3", fretText(3, setOf(TabTechnique.PRE_BEND)))
        // Combinados: la X manda sobre la cifra; la púa es un arco aparte, no un prefijo.
        assertEquals("X", fretText(3, setOf(TabTechnique.MUTED, TabTechnique.PALM_MUTE)))
        assertEquals("X", fretText(3, setOf(TabTechnique.MUTED, TabTechnique.PICK_DOWN)))
        assertEquals("(3)", fretText(3, setOf(TabTechnique.GHOST, TabTechnique.VIBRATO)))
    }

    @Test
    fun vectorialGlyph_mapsEachAdornmentTechnique_andSkipsTextualOnes() {
        // Cada técnica "de adorno" tiene su símbolo vectorial; las que se escriben con texto
        // (púa) o no son nada (NONE) devuelven null.
        assertEquals(TabGlyphKind.TREMOLO, com.grafitto.laudate.domain.model.tablature.TabTechnique.TREMOLO.vectorialGlyph())
        assertEquals(TabGlyphKind.VIBRATO, com.grafitto.laudate.domain.model.tablature.TabTechnique.VIBRATO.vectorialGlyph())
        assertEquals(TabGlyphKind.BEND, com.grafitto.laudate.domain.model.tablature.TabTechnique.BEND.vectorialGlyph())
        assertEquals(TabGlyphKind.SLIDE, com.grafitto.laudate.domain.model.tablature.TabTechnique.SLIDE.vectorialGlyph())
        assertEquals(TabGlyphKind.SLUR, com.grafitto.laudate.domain.model.tablature.TabTechnique.HAMMER.vectorialGlyph())
        assertEquals(TabGlyphKind.SLUR, com.grafitto.laudate.domain.model.tablature.TabTechnique.PULL.vectorialGlyph())
        assertEquals(TabGlyphKind.HARMONIC, com.grafitto.laudate.domain.model.tablature.TabTechnique.HARMONIC.vectorialGlyph())
        assertEquals(TabGlyphKind.PALM_MUTE, com.grafitto.laudate.domain.model.tablature.TabTechnique.PALM_MUTE.vectorialGlyph())
        assertEquals(TabGlyphKind.LET_RING, com.grafitto.laudate.domain.model.tablature.TabTechnique.LET_RING.vectorialGlyph())
        assertEquals(TabGlyphKind.TAPPING, com.grafitto.laudate.domain.model.tablature.TabTechnique.TAPPING.vectorialGlyph())
        assertEquals(TabGlyphKind.BEND_DOWN, com.grafitto.laudate.domain.model.tablature.TabTechnique.BEND_DOWN.vectorialGlyph())
        assertEquals(TabGlyphKind.PRE_BEND, com.grafitto.laudate.domain.model.tablature.TabTechnique.PRE_BEND.vectorialGlyph())
        assertEquals(null, com.grafitto.laudate.domain.model.tablature.TabTechnique.NONE.vectorialGlyph())
        assertEquals(null, com.grafitto.laudate.domain.model.tablature.TabTechnique.PICK_DOWN.vectorialGlyph())
        assertEquals(null, com.grafitto.laudate.domain.model.tablature.TabTechnique.PICK_UP.vectorialGlyph())
        // Muted y ghost son la propia cifra: no llevan símbolo encima.
        assertEquals(null, com.grafitto.laudate.domain.model.tablature.TabTechnique.MUTED.vectorialGlyph())
        assertEquals(null, com.grafitto.laudate.domain.model.tablature.TabTechnique.GHOST.vectorialGlyph())
    }

    @Test
    fun glyphLabel_and_glyphSpan_carryTheBendFigure_andThePalmMuteReach() {
        val bend = TabPluck(
            position = TabPosition(1, 7),
            techniques = setOf(TabTechnique.BEND),
            bendAmount = TabBendAmount.FULL
        )
        assertEquals("1", bend.glyphLabel(TabGlyphKind.BEND))
        assertEquals(1, bend.glyphSpan(TabGlyphKind.BEND))

        val palmMute = TabPluck(
            position = TabPosition(1, 3),
            techniques = setOf(TabTechnique.PALM_MUTE),
            palmMuteSpan = 4
        )
        assertEquals(4, palmMute.glyphSpan(TabGlyphKind.PALM_MUTE))
        assertEquals(null, palmMute.glyphLabel(TabGlyphKind.PALM_MUTE))
        // Sin cifra de bend, el símbolo va solo.
        val plainBend = TabPluck(position = TabPosition(1, 3), techniques = setOf(TabTechnique.BEND))
        assertEquals(null, plainBend.glyphLabel(TabGlyphKind.BEND))
    }

    @Test
    fun techniqueFamilies_coverEveryTechniqueButNone() {
        val grouped = TabTechniqueFamily.entries.flatMap { it.techniques }
        // Cada técnica (salvo NONE) aparece exactamente en una familia: la paleta no deja ninguna
        // fuera ni la duplica entre secciones.
        com.grafitto.laudate.domain.model.tablature.TabTechnique.entries
            .filter { it != com.grafitto.laudate.domain.model.tablature.TabTechnique.NONE }
            .forEach { technique ->
                assertEquals("$technique debe estar en una sola familia", 1, grouped.count { it == technique })
            }
    }

    @Test
    fun rhythmicFigure_marksTheColumnWithoutChangingTheGridPitch() {
        val measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(strings = listOf(TabPluck(1, 0))),
                    TabSlot(
                        strings = listOf(TabPluck(1, 2)),
                        rhythmicFigure = RhythmFigure.DOTTED_EIGHTH
                    )
                )
            )
        )
        // La fila de figuras es **opcional** (por defecto `NONE`, tipo `Simple` de MuseScore), asi que
        // aqui se activa con el tipo `Common`.
        val drawing = TablatureSystemsLayout.layout(
            tab(measures),
            maxWidth = 600f,
            settings = TabLayoutSettings(staff = TabStaffOptions.COMMON)
        ).single().drawing

        assertEquals(listOf(RhythmFigure.QUARTER, RhythmFigure.DOTTED_EIGHTH), drawing.rhythmMarks.map { it.figure })
        assertEquals(2, drawing.rhythmMarks.size)
        // **Espaciado por duración** (C.1): la 2ª columna lleva **corchea con puntillo** (0,75 negras),
        // así que su centro NO dista un ancho lleno del primero sino 3/4 (con la negra como unidad, la
        // figura más menuda del documento es la corchea con puntillo → la unidad se reescala).
        val cell = TabLayoutSettings().cellWidth
        val distancia = drawing.rhythmMarks[1].x - drawing.rhythmMarks[0].x
        assertTrue("la columna más corta deja menos hueco", distancia < cell * 1.5f)
        assertTrue("pero conserva un ancho mínimo", distancia > cell * 0.05f)
    }

    @Test
    fun unaColumnaConVariosSimbolosNoEnsanchaLaColumna() {
        val measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(strings = listOf(TabPluck(1, 0))),
                    TabSlot(strings = listOf(TabPluck(1, 1))),
                    TabSlot(
                        strings = listOf(
                            TabPluck(
                                position = TabPosition(1, 3),
                                techniques = setOf(
                                    TabTechnique.BEND,
                                    TabTechnique.VIBRATO,
                                    TabTechnique.PALM_MUTE
                                )
                            )
                        )
                    ),
                    TabSlot(strings = listOf(TabPluck(1, 5)))
                )
            )
        )
        val drawing = TablatureSystemsLayout.layout(tab(measures), maxWidth = 900f).single().drawing
        val cell = TabLayoutSettings().cellWidth
        // El ancho real de cada columna lo publica la maquetacion (`columns`), que es lo que usan
        // tanto las cifras como la fila de ritmo.
        val columns = drawing.columns.getValue(0)
        assertEquals(4, columns.size)
        // **Todas** las columnas miden lo mismo: el ancho depende **solo de la duración** (cuatro
        // negras), nunca de cuántos símbolos lleve la nota. Antes la columna cargada se ensanchaba y
        // la tablatura **se movía** al añadir un símbolo (punto 1 del usuario).
        columns.forEach { assertEquals("la columna $it no cambia de ancho", cell, it.width, 0.01f) }
        assertEquals(cell, columns[1].centerX - columns[0].centerX, 0.01f)
        assertEquals(cell, columns[2].centerX - columns[1].centerX, 0.01f)
        assertEquals(cell, columns[3].centerX - columns[2].centerX, 0.01f)
        // (D-89) **Solo los símbolos de articulación van en la pila de arriba.** De los tres de esta
        // nota, la **palm mute** es una `Articulation` de MuseScore y se apila sobre la cuerda; el
        // **bend** y el **vibrato** son `SLine` anclados a la nota y van **junto a la cifra**, a su
        // derecha y a la altura de la cuerda. Antes se apilaban los tres, que es justo lo que el
        // usuario reportó («hay muchos símbolos que van en el dígito y los estás pintando arriba»).
        val stacked = drawing.glyphs.filter { kotlin.math.abs(it.x - columns[2].centerX) < 0.01f }
        assertEquals("solo la palm mute va en la pila superior", 1, stacked.size)
        // (D-89) El **bend** y el **vibrato** van **a la derecha de la cifra** y a la **altura de su
        // cuerda** (`SLine`/`Glissando` anclados a la nota en MuseScore); solo la **palm mute**
        // (una `Articulation`) queda en la pila superior. Medido: bend en x = 223,6 y vibrato en
        // 228,4 con el centro de la columna en 217, y ambos en la fila de la cuerda 1 (y = 93),
        // mientras la palm mute está arriba (y = 72).
        val toTheRight = drawing.glyphs.filter { it.x > columns[2].centerX + 0.01f }
        assertEquals("el bend y el vibrato van junto a la cifra", 2, toTheRight.size)
        assertTrue(
            "y los dos comparten la fila de la cuerda de la nota",
            toTheRight.map { it.y }.distinct().size == 1
        )
        assertTrue(
            "junto a la cifra quedan por debajo de la pila superior",
            toTheRight.first().y > stacked.first().y
        )
    }

    @Test
    fun rhythmicTriplet_isMarkedForRenderer() {
        val measure = TabMeasure(
            slots = listOf(TabSlot(strings = listOf(TabPluck(1, 3)), tuplet = TabTuplet.TRIPLET))
        )
        val drawing = TablatureSystemsLayout.layout(
            tab(listOf(measure)),
            maxWidth = 600f,
            settings = TabLayoutSettings(staff = TabStaffOptions.COMMON)
        ).single().drawing

        assertTrue(drawing.rhythmMarks.single().isTriplet)
    }
}
