package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import com.grafitto.laudate.domain.model.tablature.TabTupletBracketType
import com.grafitto.laudate.domain.model.tablature.TabTupletNumberType
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.serializer.MsczParser
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **D-38 · Grupos irregulares de cualquier proporción.**
 *
 * MuseScore no conoce «el tresillo»: conoce el `Tuplet`, que es un grupo de `numNotes` figuras en el
 * tiempo de `normalNotes` (`Tuplet::ratio()`). El tresillo es solo el caso `3:2`. Canticora tenía un
 * `TabSlot.isTriplet: Boolean`, así que un quintillo (`5:4`), un seisillo (`6:4`), un septillo (`7:4`)
 * o un nonillo (`9:8`) **no se podían ni escribir ni importar**: se leían como figuras normales y el
 * compás salía con la duración equivocada.
 */
class TabTupletTest {

    private val res = TabResolution.QUARTER

    private fun tab(measures: List<TabMeasure>) = Tablature(
        id = "t",
        songId = "song-1",
        title = "T",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = res,
        measures = measures
    )

    private fun note(figure: RhythmFigure? = null, tuplet: TabTuplet? = null) = TabSlot(
        strings = listOf(TabPluck(1, 3)),
        rhythmicFigure = figure,
        tuplet = tuplet
    )

    // ── El modelo ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun laProporcionEsNormalEntreNumero() {
        // `Tuplet::ratio()` de MuseScore: cuánto dura cada figura respecto de su valor escrito.
        assertEquals(2.0 / 3.0, TabTuplet.TRIPLET.ratio, 1e-9)
        assertEquals(4.0 / 5.0, TabTuplet.QUINTUPLET.ratio, 1e-9)
        assertEquals(4.0 / 6.0, TabTuplet.SEXTUPLET.ratio, 1e-9)
        assertEquals(4.0 / 7.0, TabTuplet.SEPTUPLET.ratio, 1e-9)
        assertEquals(8.0 / 9.0, TabTuplet.NONUPLET.ratio, 1e-9)
    }

    @Test
    fun elRotuloEsElNumeradorYLaRelacionLlevaAmbos() {
        // `TupletNumberType::SHOW_NUMBER` (el valor por defecto de MuseScore) rotula solo el
        // numerador; `SHOW_RELATION` escribiría `3:2`.
        assertEquals("3", TabTuplet.TRIPLET.label)
        assertEquals("5", TabTuplet.QUINTUPLET.label)
        assertEquals("9", TabTuplet.NONUPLET.label)
        assertEquals("3:2", TabTuplet.TRIPLET.relationLabel)
        assertEquals("9:8", TabTuplet.NONUPLET.relationLabel)
    }

    @Test
    fun losPresetsSonLosQueOfreceMuseScore() {
        assertEquals(
            listOf(
                TabTuplet.TRIPLET,
                TabTuplet.QUINTUPLET,
                TabTuplet.SEXTUPLET,
                TabTuplet.SEPTUPLET,
                TabTuplet.NONUPLET
            ),
            TabTuplet.PRESETS
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun unGrupoDeCeroFigurasNoExiste() {
        TabTuplet(numNotes = 0, normalNotes = 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun unGrupoEnElTiempoDeCeroNoExiste() {
        TabTuplet(numNotes = 3, normalNotes = 0)
    }

    @Test
    fun isTripletSoloEsCiertoParaElTresillo() {
        assertTrue(note(tuplet = TabTuplet.TRIPLET).isTriplet)
        assertFalse(note(tuplet = TabTuplet.QUINTUPLET).isTriplet)
        assertFalse(note().isTriplet)
    }

    // ── Duración: espaciado, medidor y reproducción ─────────────────────────────────────────────

    @Test
    fun elQuintilloEncogeCuatroQuintos() {
        val normal = TabSpacing.beatsOf(RhythmFigure.QUARTER, null, res)
        val quintillo = TabSpacing.beatsOf(RhythmFigure.QUARTER, TabTuplet.QUINTUPLET, res)
        assertEquals(1.0, normal, 1e-9)
        assertEquals(0.8, quintillo, 1e-9)
    }

    @Test
    fun elSeptilloEncogeCuatroSeptimos() {
        assertEquals(
            4.0 / 7.0,
            TabSpacing.beatsOf(RhythmFigure.QUARTER, TabTuplet.SEPTUPLET, res),
            1e-9
        )
    }

    @Test
    fun elMedidorCuentaLaProporcionDelGrupo() {
        val m = TabMeasure(
            slots = listOf(TabSlot(rhythmicFigure = RhythmFigure.QUARTER, tuplet = TabTuplet.QUINTUPLET))
        )
        assertEquals(0.8, TabMeasureMeter.durationOf(m, res), 1e-9)
    }

    @Test
    fun laReproduccionAcortaLaCasillaSegunLaProporcion() {
        // 90 bpm → la negra dura 666 ms; un quintillo, 4/5 → 533 ms.
        assertEquals(533L, TabPlayback.durationMs(RhythmFigure.QUARTER, 90, TabTuplet.QUINTUPLET))
        assertEquals(444L, TabPlayback.durationMs(RhythmFigure.QUARTER, 90, TabTuplet.TRIPLET))
        assertEquals(666L, TabPlayback.durationMs(RhythmFigure.QUARTER, 90))
    }

    @Test
    fun laLineaDeTiempoUsaLaProporcionDeCadaCasilla() {
        val measures = listOf(
            TabMeasure(listOf(note(RhythmFigure.QUARTER, TabTuplet.QUINTUPLET), note(RhythmFigure.QUARTER)))
        )
        val timeline = TabPlayback.timeline(tab(measures), defaultBpm = 60)
        // A 60 bpm la negra son 1000 ms: el quintillo 800, la negra suelta 1000.
        assertEquals(800L, timeline.events[0].durationMs)
        assertEquals(1000L, timeline.events[1].durationMs)
    }

    // ── Edición: marcar, deshacer y sustituir ───────────────────────────────────────────────────

    @Test
    fun marcarUnQuintilloMarcaCincoColumnas() {
        val measures = listOf(TabMeasure(slots = List(5) { note(RhythmFigure.SIXTEENTH) }))
        val out = TablatureEditing.toggleTupletGroup(measures, 0, 0, TabTuplet.QUINTUPLET)
        assertEquals(List(5) { TabTuplet.QUINTUPLET }, out[0].slots.map { it.tuplet })
    }

    @Test
    fun marcarUnGrupoCreaLasColumnasQueFalten() {
        val measures = listOf(TabMeasure(slots = listOf(note(RhythmFigure.SIXTEENTH))))
        val out = TablatureEditing.toggleTupletGroup(measures, 0, 0, TabTuplet.SEPTUPLET)
        assertEquals(7, out[0].slots.size)
        assertEquals(List(7) { TabTuplet.SEPTUPLET }, out[0].slots.map { it.tuplet })
    }

    @Test
    fun volverAPulsarDeshaceElGrupoEntero() {
        val measures = listOf(TabMeasure(slots = List(5) { note(RhythmFigure.SIXTEENTH) }))
        val marcado = TablatureEditing.toggleTupletGroup(measures, 0, 0, TabTuplet.QUINTUPLET)
        // Se pulsa en la **tercera** figura del grupo: se deshace el grupo completo, no una casilla.
        val deshecho = TablatureEditing.toggleTupletGroup(marcado, 0, 2, TabTuplet.QUINTUPLET)
        assertEquals(List(5) { null }, deshecho[0].slots.map { it.tuplet })
    }

    @Test
    fun cambiarDeProporcionSustituyeElGrupo() {
        val measures = listOf(TabMeasure(slots = List(5) { note(RhythmFigure.SIXTEENTH) }))
        val tresillo = TablatureEditing.toggleTupletGroup(measures, 0, 0, TabTuplet.TRIPLET)
        val quintillo = TablatureEditing.toggleTupletGroup(tresillo, 0, 0, TabTuplet.QUINTUPLET)
        // Las tres primeras pasan a quintillo y las dos siguientes también: un solo grupo de cinco.
        assertEquals(List(5) { TabTuplet.QUINTUPLET }, quintillo[0].slots.map { it.tuplet })
    }

    @Test
    fun elTresilloSigueFuncionandoPorLaPuertaVieja() {
        val measures = listOf(TabMeasure(slots = List(3) { note(RhythmFigure.EIGHTH) }))
        val out = TablatureEditing.toggleTripletGroup(measures, 0, 0)
        assertEquals(List(3) { TabTuplet.TRIPLET }, out[0].slots.map { it.tuplet })
    }

    // ── Corchetes: uno por grupo y por proporción ───────────────────────────────────────────────

    private fun mark(x: Float, tuplet: TabTuplet?, measure: Int = 0) = TabRhythmMark(
        figure = RhythmFigure.EIGHTH,
        x = x,
        y = 0f,
        tuplet = tuplet,
        measureIndex = measure
    )

    @Test
    fun unCorchetePorRachaYProporcion() {
        val marks = listOf(
            mark(0f, TabTuplet.TRIPLET),
            mark(10f, TabTuplet.TRIPLET),
            mark(20f, TabTuplet.TRIPLET),
            // Cambia la proporción: es **otro** grupo, con su propio corchete.
            mark(30f, TabTuplet.QUINTUPLET),
            mark(40f, TabTuplet.QUINTUPLET),
            mark(50f, null)
        )
        val brackets = TabRhythmGeometry.tupletBracketsOf(marks, unit = 10f, staffTop = 100f)
        assertEquals(2, brackets.size)
        assertEquals("3", brackets[0].label)
        assertEquals("5", brackets[1].label)
    }

    @Test
    fun elCorcheteSeCortaAlCambiarDeCompas() {
        val marks = listOf(
            mark(0f, TabTuplet.TRIPLET, measure = 0),
            mark(10f, TabTuplet.TRIPLET, measure = 1)
        )
        val brackets = TabRhythmGeometry.tupletBracketsOf(marks, unit = 10f, staffTop = 100f)
        assertEquals(2, brackets.size)
    }

    @Test
    fun sinGruposNoHayCorchetes() {
        val marks = listOf(mark(0f, null), mark(10f, null))
        assertTrue(TabRhythmGeometry.tupletBracketsOf(marks, 10f, 100f).isEmpty())
    }

    // ── Serialización ───────────────────────────────────────────────────────────────────────────

    @Test
    fun laProporcionSobreviveAlViajeDeIdaYVuelta() {
        val original = tab(
            listOf(
                TabMeasure(
                    slots = List(5) { note(RhythmFigure.SIXTEENTH, TabTuplet.QUINTUPLET) }
                )
            )
        )
        val vuelta = TablatureSerializer.decode(TablatureSerializer.encode(original))
        assertEquals(
            List(5) { TabTuplet.QUINTUPLET },
            vuelta.measures[0].slots.map { it.tuplet }
        )
    }

    @Test
    fun elJsonLlevaNumeradorYDenominador() {
        val json = TablatureSerializer.encode(
            tab(listOf(TabMeasure(slots = listOf(note(RhythmFigure.EIGHTH, TabTuplet.SEPTUPLET)))))
        )
        assertTrue(json.contains("\"tupletNum\": 7"))
        assertTrue(json.contains("\"tupletNormal\": 4"))
    }

    @Test
    fun unJsonViejoConSoloIsTripletSigueLeyendoseComoTresillo() {
        // Compatibilidad: los documentos guardados antes de D-38 solo traen `isTriplet`.
        val json = TablatureSerializer.encode(
            tab(listOf(TabMeasure(slots = listOf(note(RhythmFigure.EIGHTH)))))
        )
        val viejo = json
            .replace(Regex("\"tupletNum\": null,\\s*"), "")
            .replace(Regex("\"tupletNormal\": null,\\s*"), "")
            .replace("\"isTriplet\": false", "\"isTriplet\": true")
        val vuelta = TablatureSerializer.decode(viejo)
        assertEquals(TabTuplet.TRIPLET, vuelta.measures[0].slots[0].tuplet)
    }

    @Test
    fun unaCasillaSinGrupoNoEscribeProporcion() {
        val json = TablatureSerializer.encode(
            tab(listOf(TabMeasure(slots = listOf(note(RhythmFigure.EIGHTH)))))
        )
        assertTrue(json.contains("\"tupletNum\": null"))
        assertNull(TablatureSerializer.decode(json).measures[0].slots[0].tuplet)
    }

    // ── Importación desde MuseScore ──────────────────────────────────────────────────────────────

    /**
     * `.mscx` mínimo con **un** compás de [n] corcheas dentro de un grupo [num]:[normal]. MuseScore
     * escribe el `<Tuplet>` como **hermano** de las notas (no las envuelve) y lo cierra con
     * `<endTuplet/>` al final del grupo.
     */
    private fun mscxWithTuplet(num: Int, normal: Int, n: Int = num): String {
        val chords = (1..n).joinToString("\n") {
            "<Chord><durationType>eighth</durationType>" +
                "<Note><pitch>60</pitch><fret>$it</fret><string>0</string></Note></Chord>"
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <museScore version="4.20">
              <Score>
                <Staff id="1">
                  <Measure>
                    <voice>
                      <Tuplet><normalNotes>$normal</normalNotes><actualNotes>$num</actualNotes></Tuplet>
                      $chords
                      <endTuplet/>
                    </voice>
                  </Measure>
                </Staff>
              </Score>
            </museScore>
        """.trimIndent()
    }

    @Test
    fun elParserLeeUnQuintilloConSuProporcion() {
        val slots = MsczParser.parse(mscxWithTuplet(5, 4), songId = "x").measures[0].slots
        assertEquals("el quintillo tiene cinco columnas", 5, slots.size)
        slots.forEachIndexed { i, slot ->
            assertEquals("columna $i: quintillo 5:4", TabTuplet.QUINTUPLET, slot.tuplet)
        }
    }

    @Test
    fun elParserLeeUnSeptilloYNoLoTomaPorTresillo() {
        // Antes el parser exigía `actualNotes == 3` y descartaba el resto: un septillo se leía como
        // siete corcheas normales y el compás salía con la duración equivocada.
        val slot = MsczParser.parse(mscxWithTuplet(7, 4), songId = "x").measures[0].slots[0]
        assertEquals(TabTuplet.SEPTUPLET, slot.tuplet)
        assertFalse("un septillo no es un tresillo", slot.isTriplet)
    }

    @Test
    fun elParserAnidaGruposYAsignaElDeDentro() {
        // MuseScore **anida** los grupos: escribe los dos `<Tuplet>` como hermanos antes de las notas
        // y cierra con dos `<endTuplet/>`. El grupo del item es el de la **cima** de la pila.
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <museScore version="4.20">
              <Score>
                <Staff id="1">
                  <Measure>
                    <voice>
                      <Tuplet><normalNotes>2</normalNotes><actualNotes>3</actualNotes></Tuplet>
                      <Tuplet><normalNotes>4</normalNotes><actualNotes>5</actualNotes></Tuplet>
                      <Chord><durationType>eighth</durationType>
                        <Note><pitch>60</pitch><fret>0</fret><string>0</string></Note></Chord>
                      <endTuplet/>
                      <Chord><durationType>eighth</durationType>
                        <Note><pitch>62</pitch><fret>2</fret><string>0</string></Note></Chord>
                      <endTuplet/>
                    </voice>
                  </Measure>
                </Staff>
              </Score>
            </museScore>
        """.trimIndent()
        val slots = MsczParser.parse(xml, songId = "x").measures[0].slots
        assertEquals(2, slots.size)
        assertEquals("dentro de los dos grupos manda el de dentro", TabTuplet.QUINTUPLET, slots[0].tuplet)
        assertEquals("al cerrar el de dentro queda el de fuera", TabTuplet.TRIPLET, slots[1].tuplet)
    }

    // ── D-41 · TupletNumberType y TupletBracketType ─────────────────────────────────────────────

    @Test
    fun elRotuloPorDefectoSoloLlevaElNumero() {
        // `m_numberType = TupletNumberType::SHOW_NUMBER` (tuplet.cpp:70).
        assertEquals(TabTupletNumberType.SHOW_NUMBER, TabTuplet.TRIPLET.numberType)
        assertEquals("3", TabTuplet.TRIPLET.label)
        assertTrue(TabTuplet.TRIPLET.hasLabel)
    }

    @Test
    fun conShowRelationElRotuloEsLaProporcion() {
        val tuplet = TabTuplet(3, 2, numberType = TabTupletNumberType.SHOW_RELATION)
        assertEquals("3:2", tuplet.label)
        assertEquals("3:2", tuplet.relationLabel)
        assertTrue(tuplet.hasLabel)
    }

    @Test
    fun conNoTextElCorcheteQuedaMudo() {
        val tuplet = TabTuplet(5, 4, numberType = TabTupletNumberType.NO_TEXT)
        assertEquals("", tuplet.label)
        assertFalse(tuplet.hasLabel)
    }

    @Test
    fun elCorcheteAutomaticoSeOmiteCuandoElGrupoOcupaUnaSolaBarra() {
        // Tres corcheas en un tresillo, las tres en la **misma** barra de unión: MuseScore rotula el
        // `3` **sin** corchete (`Tuplet::calcHasBracket`, tuplet.cpp:203).
        val marks = listOf(
            mark(0f, TabTuplet.TRIPLET),
            mark(10f, TabTuplet.TRIPLET),
            mark(20f, TabTuplet.TRIPLET)
        )
        val brackets = TabRhythmGeometry.tupletBracketsOf(
            marks, unit = 10f, staffTop = 100f, beamGroups = listOf(listOf(0, 1, 2))
        )
        assertEquals(1, brackets.size)
        assertFalse("el grupo ocupa una sola barra: sin corchete", brackets[0].hasBracket)
        assertEquals("3", brackets[0].label)
    }

    @Test
    fun elCorcheteAutomaticoSiSeDibujaSiElGrupoCruzaDosBarras() {
        val marks = listOf(
            mark(0f, TabTuplet.TRIPLET),
            mark(10f, TabTuplet.TRIPLET),
            mark(20f, TabTuplet.TRIPLET)
        )
        val brackets = TabRhythmGeometry.tupletBracketsOf(
            marks, unit = 10f, staffTop = 100f, beamGroups = listOf(listOf(0, 1), listOf(2))
        )
        assertTrue("el grupo cruza dos barras: con corchete", brackets[0].hasBracket)
    }

    @Test
    fun elCorcheteAutomaticoSiSeDibujaSiElGrupoNoEstaEnNingunaBarra() {
        // Negras: no llevan barra de unión, así que el corchete es obligatorio.
        val marks = listOf(
            mark(0f, TabTuplet.TRIPLET),
            mark(10f, TabTuplet.TRIPLET),
            mark(20f, TabTuplet.TRIPLET)
        )
        val brackets = TabRhythmGeometry.tupletBracketsOf(marks, unit = 10f, staffTop = 100f)
        assertTrue(brackets[0].hasBracket)
    }

    @Test
    fun unGrupoDeUnaSolaFiguraNoLlevaCorchete() {
        // `if (cr1 == cr2) return false;` — grupo degenerado.
        val marks = listOf(mark(0f, TabTuplet.TRIPLET))
        val brackets = TabRhythmGeometry.tupletBracketsOf(marks, unit = 10f, staffTop = 100f)
        assertEquals(1, brackets.size)
        assertFalse(brackets[0].hasBracket)
    }

    @Test
    fun showBracketMandaSobreLaReglaAutomatica() {
        val tuplet = TabTuplet(3, 2, bracketType = TabTupletBracketType.SHOW_BRACKET)
        val marks = listOf(mark(0f, tuplet), mark(10f, tuplet), mark(20f, tuplet))
        val brackets = TabRhythmGeometry.tupletBracketsOf(
            marks, unit = 10f, staffTop = 100f, beamGroups = listOf(listOf(0, 1, 2))
        )
        assertTrue(brackets[0].hasBracket)
    }

    @Test
    fun showNoBracketMandaSobreLaReglaAutomatica() {
        val tuplet = TabTuplet(3, 2, bracketType = TabTupletBracketType.SHOW_NO_BRACKET)
        val marks = listOf(mark(0f, tuplet), mark(10f, tuplet), mark(20f, tuplet))
        val brackets = TabRhythmGeometry.tupletBracketsOf(marks, unit = 10f, staffTop = 100f)
        assertFalse(brackets[0].hasBracket)
    }

    @Test
    fun elRotuloDelCorcheteSigueAlNumberType() {
        val relation = TabTuplet(3, 2, numberType = TabTupletNumberType.SHOW_RELATION)
        val marks = listOf(mark(0f, relation), mark(10f, relation), mark(20f, relation))
        val brackets = TabRhythmGeometry.tupletBracketsOf(marks, unit = 10f, staffTop = 100f)
        assertEquals("3:2", brackets[0].label)
    }

    @Test
    fun cambiarElRotuloAlcanzaATodaLaRachaDelGrupo() {
        val measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(tuplet = TabTuplet.TRIPLET),
                    TabSlot(tuplet = TabTuplet.TRIPLET),
                    TabSlot(tuplet = TabTuplet.TRIPLET),
                    TabSlot()
                )
            )
        )
        val updated = TablatureEditing.setTupletNumberType(
            measures, 0, 1, TabTupletNumberType.SHOW_RELATION
        )
        val slots = updated[0].slots
        assertEquals(TabTupletNumberType.SHOW_RELATION, slots[0].tuplet?.numberType)
        assertEquals(TabTupletNumberType.SHOW_RELATION, slots[1].tuplet?.numberType)
        assertEquals(TabTupletNumberType.SHOW_RELATION, slots[2].tuplet?.numberType)
        assertNull("la casilla fuera del grupo no se toca", slots[3].tuplet)
    }

    @Test
    fun cambiarElCorcheteAlcanzaATodaLaRachaDelGrupo() {
        val measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(tuplet = TabTuplet.TRIPLET),
                    TabSlot(tuplet = TabTuplet.TRIPLET)
                )
            )
        )
        val updated = TablatureEditing.setTupletBracketType(
            measures, 0, 0, TabTupletBracketType.SHOW_NO_BRACKET
        )
        assertEquals(
            TabTupletBracketType.SHOW_NO_BRACKET,
            updated[0].slots[1].tuplet?.bracketType
        )
    }

    @Test
    fun sinGrupoElAjusteNoHaceNada() {
        val measures = listOf(TabMeasure(slots = listOf(TabSlot())))
        val updated = TablatureEditing.setTupletNumberType(
            measures, 0, 0, TabTupletNumberType.NO_TEXT
        )
        assertNull(updated[0].slots[0].tuplet)
    }

    @Test
    fun elRotuloYElCorcheteSobrevivenAlViajeDeIdaYVuelta() {
        val tuplet = TabTuplet(
            3, 2,
            numberType = TabTupletNumberType.SHOW_RELATION,
            bracketType = TabTupletBracketType.SHOW_NO_BRACKET
        )
        val original = tab(
            listOf(
                TabMeasure(
                    slots = listOf(
                        TabSlot(
                            strings = listOf(TabPluck(stringNumber = 1, fret = 0)),
                            rhythmicFigure = RhythmFigure.EIGHTH,
                            tuplet = tuplet
                        )
                    )
                )
            )
        )
        val json = TablatureSerializer.encode(original)
        val back = TablatureSerializer.decode(json)
        val restored = back.measures[0].slots[0].tuplet
        assertEquals(TabTupletNumberType.SHOW_RELATION, restored?.numberType)
        assertEquals(TabTupletBracketType.SHOW_NO_BRACKET, restored?.bracketType)
    }

    @Test
    fun unArchivoSinLosAjustesSeLeeConLosValoresPorDefectoDeMuseScore() {
        val original = tab(
            listOf(
                TabMeasure(
                    slots = listOf(
                        TabSlot(
                            strings = listOf(TabPluck(stringNumber = 1, fret = 0)),
                            rhythmicFigure = RhythmFigure.EIGHTH,
                            tuplet = TabTuplet.TRIPLET
                        )
                    )
                )
            )
        )
        val json = TablatureSerializer.encode(original)
            .replace(Regex("""\s*"tupletNumberType"\s*:\s*"[^"]*",?"""), "")
            .replace(Regex("""\s*"tupletBracketType"\s*:\s*"[^"]*",?"""), "")
        val back = TablatureSerializer.decode(json)
        val restored = back.measures[0].slots[0].tuplet
        assertEquals(TabTupletNumberType.SHOW_NUMBER, restored?.numberType)
        assertEquals(TabTupletBracketType.AUTO_BRACKET, restored?.bracketType)
    }
}
