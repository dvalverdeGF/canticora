package com.grafitto.laudate.domain.serializer

import com.grafitto.laudate.domain.model.score.ScoreKey
import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabBendAmount
import com.grafitto.laudate.domain.model.tablature.TabHairpin
import com.grafitto.laudate.domain.model.tablature.TabHairpinType
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.model.tablature.TabExecution
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de ida y vuelta del serializador de tablaturas. */
class TablatureSerializerTest {

    private fun sample(): Tablature = Tablature(
        id = "tab-1",
        songId = "song-1",
        title = "Aleluya",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR_DROP_D,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.SIXTEENTH,
        origin = TabOrigin.CREATED,
        writtenKey = ScoreKey(-1, ScoreKey.MAJOR),
        label = "Por los niños",
        moment = "Ofertorio",
        note = "Arreglo de 1974, laúd",
        measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(
                        strings = listOf(
                            TabPluck(6, 0),
                            TabPluck(5, 2, TabTechnique.TREMOLO)
                        )
                    ),
                    TabSlot(
                        strings = listOf(TabPluck(1, 3, execution = TabExecution(velocity = 80, dynamics = "mf", midiNote = 60))),
                        isHeld = true,
                        rhythmicFigure = RhythmFigure.DOTTED_EIGHTH,
                        tuplet = TabTuplet.TRIPLET
                    ),
                    TabSlot() // pausa
                ),
                repeat = TabRepeatMark.BOTH,
                ending = 2,
                endingTo = 3,
                endingText = "Todos",
                repeatCount = 3
            ),
            TabMeasure(slots = listOf(TabSlot(strings = listOf(TabPluck(2, 10)))))
        )
    )

    @Test
    fun roundTrip_preservesEveryField() {
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(sample()))

        assertEquals("tab-1", restored.id)
        assertEquals("song-1", restored.songId)
        assertEquals("Aleluya", restored.title)
        assertEquals("Guitarra", restored.instrument)
        assertEquals(ScoreTimeSignature("4", 4), restored.time)
        assertEquals(TabResolution.SIXTEENTH, restored.resolution)
        assertEquals(TabOrigin.CREATED, restored.origin)
        assertEquals(ScoreKey(-1, ScoreKey.MAJOR), restored.writtenKey)
        assertEquals(2, restored.measures.size)

        // Metadatos del copista (RF-127).
        assertEquals("Por los niños", restored.label)
        assertEquals("Ofertorio", restored.moment)
        assertEquals("Arreglo de 1974, laúd", restored.note)

        // Afinación (drop D: la 6ª es Re2).
        assertEquals(ScorePitch('D', 0, 2), restored.tuning.strings.first().pitch)
        assertEquals(Tuning.GUITAR_DROP_D, restored.tuning)

        // Casilla con dos cuerdas a la vez y técnica (trémolo).
        val firstSlot = restored.measures[0].slots[0]
        assertEquals(2, firstSlot.strings.size)
        assertEquals(TabTechnique.TREMOLO, firstSlot.strings[1].technique)

        // Ejecución (eje 3): velocidad, dinámica y MIDI.
        val execution = restored.measures[0].slots[1].strings[0].execution
        assertEquals(80, execution?.velocity)
        assertEquals("mf", execution?.dynamics)
        assertEquals(60, execution?.midiNote)

        // Marcas del manuscrito del compás: repetición y volta.
        assertEquals(TabRepeatMark.BOTH, restored.measures[0].repeat)
        assertEquals(2, restored.measures[0].ending)
        // (D-37) Casilla de varias pasadas (`2, 3.`): el rango viaja en el JSON.
        assertEquals(3, restored.measures[0].endingTo)
        assertEquals(listOf(2, 3), restored.measures[0].endings)
        // (D-43) Rótulo propio de la casilla (el `beginText` del `Volta` de MuseScore).
        assertEquals("Todos", restored.measures[0].endingText)
        assertEquals("Todos", restored.measures[0].endingLabel)
        assertEquals(3, restored.measures[0].repeatCount)

        // Prolongación (isHeld) y pausa.
        assertTrue(restored.measures[0].slots[1].isHeld)
        assertEquals(RhythmFigure.DOTTED_EIGHTH, restored.measures[0].slots[1].rhythmicFigure)
        assertTrue(restored.measures[0].slots[1].isTriplet)
        assertTrue(restored.measures[0].slots[2].isEmpty)

        // Traste alto del segundo compás.
        assertEquals(10, restored.measures[1].slots[0].strings[0].fret)
    }

    @Test
    fun roundTrip_isIdempotent() {
        val once = TablatureSerializer.encode(sample())
        val twice = TablatureSerializer.encode(TablatureSerializer.decode(once))
        assertEquals(once, twice)
    }

    @Test
    fun looksLikeTablature_detectsOwnFormat() {
        val encoded = TablatureSerializer.encode(sample())
        assertTrue(TablatureSerializer.looksLikeTablature(encoded))
        assertFalse(TablatureSerializer.looksLikeTablature("{\"format\":\"canticora.event\"}"))
        assertFalse(TablatureSerializer.looksLikeTablature("no soy json"))
    }

    @Test
    fun unknownEnumValues_fallBackInsteadOfFailing() {
        // Un archivo de una versión futura no debe romper la importación.
        val json = """
            {
              "format": "canticora.tablature",
              "id": "x",
              "title": "Futuro",
              "instrument": "Guitarra",
              "tuning": { "name": "G", "strings": [ { "step": "E", "octave": 2 } ] },
              "time": { "beats": "4", "beatType": 4 },
              "resolution": "TRIPLET",
              "origin": "TELEPORTED",
              "measures": [ { "slots": [ { "strings": [ { "stringNumber": 1, "fret": 0, "technique": "SQUEAL" } ] } ] } ]
            }
        """.trimIndent()
        val restored = TablatureSerializer.decode(json)
        assertEquals(TabResolution.EIGHTH, restored.resolution)
        assertEquals(TabOrigin.CREATED, restored.origin)
        assertEquals(TabTechnique.NONE, restored.measures[0].slots[0].strings[0].technique)
        assertEquals(null, restored.measures[0].slots[0].rhythmicFigure)
        assertFalse(restored.measures[0].slots[0].isTriplet)
        assertEquals(2, restored.measures[0].repeatCount)
    }

    @Test
    fun bendAmount_and_palmMuteSpan_surviveTheRoundTrip() {
        val tablature = Tablature(
            id = "tab-bend",
            songId = "song-1",
            title = "Estiramientos",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH,
            measures = listOf(
                TabMeasure(
                    slots = listOf(
                        TabSlot(
                            strings = listOf(
                                TabPluck(
                                    position = TabPosition(1, 7),
                                    techniques = setOf(TabTechnique.BEND, TabTechnique.VIBRATO),
                                    bendAmount = TabBendAmount.HALF
                                ),
                                TabPluck(
                                    position = TabPosition(2, 3),
                                    techniques = setOf(TabTechnique.PALM_MUTE),
                                    palmMuteSpan = 4
                                )
                            )
                        )
                    )
                )
            )
        )
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tablature))
        val first = restored.measures[0].slots[0].strings[0]
        assertEquals(TabBendAmount.HALF, first.bendAmount)
        assertEquals(setOf(TabTechnique.BEND, TabTechnique.VIBRATO), first.techniques)
        val second = restored.measures[0].slots[0].strings[1]
        assertEquals(4, second.palmMuteSpan)
        assertEquals(setOf(TabTechnique.PALM_MUTE), second.techniques)
    }

    @Test
    fun legacyFiles_withoutTheNewFields_stillLoad() {
        // Retrocompatibilidad: un archivo escrito antes de las cifras de bend y del alcance del
        // P.M. no trae esos campos y debe cargarse sin fallar.
        val json = """
            {
              "format": "canticora.tablature",
              "id": "x",
              "title": "Antiguo",
              "instrument": "Guitarra",
              "tuning": { "name": "G", "strings": [ { "step": "E", "octave": 2 } ] },
              "time": { "beats": "4", "beatType": 4 },
              "resolution": "EIGHTH",
              "measures": [ { "slots": [ { "strings": [ { "stringNumber": 1, "fret": 3, "technique": "BEND" } ] } ] } ]
            }
        """.trimIndent()
        val restored = TablatureSerializer.decode(json)
        val pluck = restored.measures[0].slots[0].strings[0]
        assertEquals(TabTechnique.BEND, pluck.technique)
        assertEquals(null, pluck.bendAmount)
        assertEquals(null, pluck.palmMuteSpan)
    }

    @Test
    fun newTechniques_surviveTheRoundTrip() {
        val techniques = setOf(
            TabTechnique.MUTED,
            TabTechnique.GHOST,
            TabTechnique.TAPPING,
            TabTechnique.BEND_DOWN,
            TabTechnique.PRE_BEND
        )
        val tablature = Tablature(
            id = "tab-new",
            songId = "song-1",
            title = "Catálogo",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH,
            measures = listOf(
                TabMeasure(
                    slots = listOf(
                        TabSlot(strings = listOf(TabPluck(position = TabPosition(1, 3), techniques = techniques)))
                    )
                )
            )
        )
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tablature))
        assertEquals(techniques, restored.measures[0].slots[0].strings[0].techniques)
    }

    @Test
    fun tiedToNext_sobreviveALaIdaYVuelta() {
        val tablature = Tablature(
            id = "tab-tie",
            songId = "song-1",
            title = "Ligadura",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH,
            measures = listOf(
                TabMeasure(
                    slots = listOf(
                        TabSlot(strings = listOf(TabPluck(TabPosition(1, 3), tiedToNext = true))),
                        TabSlot(strings = listOf(TabPluck(1, 3)))
                    )
                )
            )
        )
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tablature))
        assertTrue(restored.measures[0].slots[0].strings[0].tiedToNext)
        assertFalse(restored.measures[0].slots[1].strings[0].tiedToNext)
    }

    @Test
    fun slurToNext_sobreviveALaIdaYVuelta() {
        val tablature = Tablature(
            id = "tab-slur",
            songId = "song-1",
            title = "Ligadura de expresión",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH,
            measures = listOf(
                TabMeasure(
                    slots = listOf(
                        TabSlot(
                            strings = listOf(TabPluck(TabPosition(1, 3))),
                            slurToNext = true
                        ),
                        TabSlot(strings = listOf(TabPluck(1, 5)))
                    )
                )
            )
        )
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tablature))
        assertTrue(restored.measures[0].slots[0].slurToNext)
        assertFalse(restored.measures[0].slots[1].slurToNext)
    }

    @Test
    fun elReguladorSobreviveALaIdaYVuelta() {
        val original = sample().let { tab ->
            tab.copy(
                measures = tab.measures.mapIndexed { index, measure ->
                    if (index == 0) {
                        measure.copy(hairpin = TabHairpin(TabHairpinType.CRESCENDO, lengthMeasures = 3))
                    } else {
                        measure
                    }
                }
            )
        }
        val restored = TablatureSerializer.decode(TablatureSerializer.encode(original))
        assertEquals(TabHairpinType.CRESCENDO, restored.measures[0].hairpin?.type)
        assertEquals(3, restored.measures[0].hairpin?.measures)
        // Los compases sin regulador siguen sin él.
        assertEquals(null, restored.measures[1].hairpin)
    }

    @Test
    fun unReguladorConAlcanceInvalidoSeCorrigeAlLeer() {
        // Un archivo manipulado con `hairpinLength: 0` no puede dejar una cuña de cero compases.
        val json = TablatureSerializer.encode(sample()).replace(
            "\"hairpinLength\": 1",
            "\"hairpinLength\": 0"
        )
        val restored = TablatureSerializer.decode(json)
        assertTrue(restored.measures.all { (it.hairpin?.measures ?: 1) >= 1 })
    }

    // ── Saltos de sistema y de página ───────────────────────────────────────────────────────────

    @Test
    fun losSaltosDeSistemaYPaginaSobrevivenALaIdaYVuelta() {
        // Antes `forceSystemBreak` **no se guardaba**: los saltos se perdian al reabrir la cancion.
        val original = sample().let { tab ->
            tab.copy(
                measures = tab.measures.mapIndexed { index, measure ->
                    when (index) {
                        0 -> measure.copy(forceSystemBreak = true)
                        1 -> measure.copy(forcePageBreak = true, forceSystemBreak = true)
                        else -> measure
                    }
                }
            )
        }

        val restored = TablatureSerializer.decode(TablatureSerializer.encode(original))

        assertTrue("el salto de sistema se pierde", restored.measures[0].forceSystemBreak)
        assertFalse(restored.measures[0].forcePageBreak)
        assertTrue("el salto de pagina se pierde", restored.measures[1].forcePageBreak)
        assertTrue(restored.measures[1].forceSystemBreak)
    }

    @Test
    fun elNumeroDeTrazosDelTremoloSobreviveAlViaje() {
        // D-28: los trazos son una **magnitud** de la técnica, no un adorno del dibujo, así que
        // tienen que guardarse con la nota.
        val original = sample().let { tab ->
            tab.copy(
                measures = tab.measures.mapIndexed { index, measure ->
                    if (index != 0) {
                        measure
                    } else {
                        measure.copy(
                            slots = measure.slots.map { slot ->
                                slot.copy(
                                    strings = slot.strings.map {
                                        if (it.has(TabTechnique.TREMOLO)) it.copy(tremoloStrokes = 4) else it
                                    }
                                )
                            }
                        )
                    }
                }
            )
        }

        val restored = TablatureSerializer.decode(TablatureSerializer.encode(original))

        val pluck = restored.measures[0].slots[0].strings.first { it.has(TabTechnique.TREMOLO) }
        assertEquals(4, pluck.tremoloStrokes)
        assertEquals(4, pluck.strokes)
    }

    @Test
    fun unJsonSinElCampoDeTrazosSigueLeyendose() {
        // Compatibilidad: los archivos guardados antes de D-28 no traen `tremoloStrokes`.
        val json = TablatureSerializer.encode(sample())
            .replace(",\"tremoloStrokes\":1", "")

        val restored = TablatureSerializer.decode(json)

        val pluck = restored.measures[0].slots[0].strings.first { it.has(TabTechnique.TREMOLO) }
        assertEquals(1, pluck.strokes)
    }

    @Test
    fun laAnacrusaSobreviveALaIdaYVuelta() {
        // D-34: sin la marca, al reabrir la canción «Completar compases» rellenaría la anacrusa y la
        // pieza perdería su compás de entrada.
        val original = sample().let { tab ->
            tab.copy(
                measures = tab.measures.mapIndexed { index, measure ->
                    if (index == 0) measure.copy(isPickup = true) else measure
                }
            )
        }

        val restored = TablatureSerializer.decode(TablatureSerializer.encode(original))

        assertTrue("la anacrusa se pierde", restored.measures[0].isPickup)
        assertTrue(restored.startsWithPickup)
        assertTrue(restored.measures.drop(1).none { it.isPickup })
    }

    @Test
    fun unJsonSinElCampoDeAnacrusaSigueLeyendose() {
        // Compatibilidad: los archivos guardados antes de D-34 no traen `isPickup`.
        val json = TablatureSerializer.encode(sample()).replace(",\"isPickup\":false", "")

        val restored = TablatureSerializer.decode(json)

        assertTrue(restored.measures.none { it.isPickup })
    }

    @Test
    fun elCambioDeCompasSobreviveAlViajeDeIdaYVuelta() {
        // D-35: la indicación de compás es una propiedad **del compás**. Si no se guarda, al reabrir
        // la pieza el compás de 3/4 vuelve a medirse en 4/4 y «Completar compases» le añade un
        // silencio de más.
        val original = sample().let { tab ->
            tab.copy(
                measures = tab.measures.mapIndexed { index, measure ->
                    if (index == 1) measure.copy(time = ScoreTimeSignature("3", 4)) else measure
                }
            )
        }

        val restored = TablatureSerializer.decode(TablatureSerializer.encode(original))

        assertEquals(ScoreTimeSignature("3", 4), restored.measures[1].time)
        assertEquals(ScoreTimeSignature("3", 4), restored.timeAt(1))
        assertTrue(restored.changesTimeAt(1))
    }

    @Test
    fun unJsonSinElCampoDeCompasSigueLeyendose() {
        // Compatibilidad: los archivos guardados antes de D-35 no traen `time` en el compás.
        val json = TablatureSerializer.encode(sample()).replace(",\"time\":null", "")

        val restored = TablatureSerializer.decode(json)

        assertTrue(restored.measures.none { it.time != null })
    }

    @Test
    fun elCompasIrregularSobreviveAlViajeDeIdaYVuelta() {
        // D-36: sin la marca, al reabrir la pieza «Completar compases» rellenaría el compás que el
        // usuario dejó a propósito con otra duración.
        val original = sample().let { tab ->
            tab.copy(
                measures = tab.measures.mapIndexed { index, measure ->
                    if (index == 1) measure.copy(isIrregular = true) else measure
                }
            )
        }

        val restored = TablatureSerializer.decode(TablatureSerializer.encode(original))

        assertTrue("el compás irregular se pierde", restored.measures[1].isIrregular)
        assertTrue(restored.measures[1].hasFreeDuration)
        assertTrue(restored.measures.filterIndexed { i, _ -> i != 1 }.none { it.isIrregular })
    }

    @Test
    fun unJsonSinElCampoDeFinDeCasillaSigueLeyendose() {
        // Compatibilidad: los archivos guardados antes de D-37 no traen `endingTo`; la casilla queda
        // como casilla simple, que es lo que eran todas antes.
        val json = TablatureSerializer.encode(sample())
            .replace(Regex(""",\s*"endingTo":\s*3"""), "")

        val restored = TablatureSerializer.decode(json)

        assertEquals(2, restored.measures[0].ending)
        assertNull(restored.measures[0].endingTo)
        assertEquals(listOf(2), restored.measures[0].endings)
    }

    @Test
    fun unJsonSinElRotuloDeLaCasillaSigueLeyendose() {
        // Compatibilidad: los archivos guardados antes de D-43 no traen `endingText`; el rótulo se
        // deriva de las pasadas, que es lo que hacían todas las casillas antes.
        val json = TablatureSerializer.encode(sample())
            .replace(Regex(""",\s*"endingText":\s*"Todos""""), "")

        val restored = TablatureSerializer.decode(json)

        assertNull(restored.measures[0].endingText)
        assertEquals("2, 3.", restored.measures[0].endingLabel)
    }

    @Test
    fun unJsonSinElCampoDeCompasIrregularSigueLeyendose() {
        // Compatibilidad: los archivos guardados antes de D-36 no traen `isIrregular`.
        val json = TablatureSerializer.encode(sample()).replace(",\"isIrregular\":false", "")

        val restored = TablatureSerializer.decode(json)

        assertTrue(restored.measures.none { it.isIrregular })
    }

    @Test
    fun unCompasConTextoIlegibleNoRompeLaCarga() {
        // Un JSON corrupto no puede impedir abrir la pieza: el compás se queda con la indicación del
        // documento en vez de lanzar.
        val json = TablatureSerializer.encode(sample()).replaceFirst(
            "\"slots\":[",
            "\"time\":\"basura\",\"slots\":["
        )

        val restored = TablatureSerializer.decode(json)

        assertNull(restored.measures.first().time)
    }

    @Test
    fun unJsonSinLosCamposDeSaltoSigueLeyendose() {
        // Compatibilidad: los archivos guardados antes de serializar los saltos no los traen.
        val json = TablatureSerializer.encode(sample())
            .replace(",\"forceSystemBreak\":false", "")
            .replace(",\"forcePageBreak\":false", "")

        val restored = TablatureSerializer.decode(json)

        assertTrue(restored.measures.none { it.forceSystemBreak || it.forcePageBreak })
    }

    @Test
    fun unArchivoConArticulacionesSueltasSeLeeComoSuGlifoCombinado() {
        // (D-40) Un archivo guardado antes de las articulaciones combinadas trae `ACCENT` y
        // `STACCATO` por separado. Al leerlo se **funden** en el sforzato, que es como lo dibuja
        // MuseScore, para que la forma canónica sea la misma que la de un archivo nuevo.
        val json = """
            {
              "format": "canticora.tablature",
              "id": "x",
              "title": "Antiguo",
              "instrument": "Guitarra",
              "tuning": { "name": "G", "strings": [ { "step": "E", "octave": 2 } ] },
              "time": { "beats": "4", "beatType": 4 },
              "resolution": "EIGHTH",
              "measures": [ { "slots": [ { "strings": [ {
                "stringNumber": 1, "fret": 3,
                "articulations": ["ACCENT", "STACCATO"]
              } ] } ] } ]
            }
        """.trimIndent()

        val restored = TablatureSerializer.decode(json)

        assertEquals(
            setOf(TabArticulation.ACCENT_STACCATO),
            restored.measures[0].slots[0].strings[0].articulations
        )
    }

    @Test
    fun lasArticulacionesCombinadasSobrevivenALaIdaYVuelta() {
        val combinadas = setOf(
            TabArticulation.ACCENT_STACCATO,
            TabArticulation.MARCATO_TENUTO,
            TabArticulation.FERMATA
        )
        val tab = sample().let { t ->
            t.copy(
                measures = listOf(
                    TabMeasure(
                        slots = listOf(
                            TabSlot(strings = listOf(TabPluck(1, 3).copy(articulations = combinadas)))
                        )
                    )
                )
            )
        }

        val restored = TablatureSerializer.decode(TablatureSerializer.encode(tab))

        assertEquals(combinadas, restored.measures[0].slots[0].strings[0].articulations)
    }
}
