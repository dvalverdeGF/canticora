package com.grafitto.laudate.domain.serializer

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabBarline
import com.grafitto.laudate.domain.model.tablature.TabFrettingFinger
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de los **parsers de partituras importadas** (MusicXML, GPIF de Guitar Pro y TuxGuitar) y
 * del **escritor de MusicXML** que se usa al compartir una tablatura.
 *
 * Todas comprueban lo mismo desde ángulos distintos: la música se lee, y lo que Canticora no
 * modela —adornos, marcas, propiedades de Guitar Pro— **se ignora en silencio** en vez de romper.
 */
class TablatureImportParsersTest {

    // ── MusicXML ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `musicxml lee compases, acordes, silencios, tecnicas y afinacion`() {
        val tab = MusicXmlTabParser.parse(MUSICXML, songId = "song-1", title = "Adeste fideles")

        assertEquals("Adeste fideles", tab.title)
        assertEquals("song-1", tab.songId)
        assertEquals(2, tab.measures.size)
        assertEquals(ScoreTimeSignature("3", 4), tab.time)

        // La afinación se guarda de grave a aguda, aunque el fichero la escriba al revés.
        assertEquals(6, tab.tuning.stringCount)
        assertEquals(40, tab.tuning.pitchOf(6)?.midi)
        assertEquals(64, tab.tuning.pitchOf(1)?.midi)

        val first = tab.measures[0]
        assertEquals("Andante", first.text)
        assertEquals(TabRepeatMark.START, first.repeat)

        // Acorde de dos cuerdas en una sola casilla + silencio de corchea.
        assertEquals(2, first.slots.size)
        val chord = first.slots[0]
        assertEquals(RhythmFigure.QUARTER, chord.rhythmicFigure)
        // Orden canónico del modelo: **ascendente** por número de cuerda (1 = la más aguda).
        assertEquals(listOf(TabPosition(5, 2), TabPosition(6, 3)), chord.strings.map { it.position })
        assertEquals(RhythmFigure.EIGHTH_REST, first.slots[1].rhythmicFigure)
        assertTrue("el silencio no lleva pulsaciones", first.slots[1].strings.isEmpty())
    }

    @Test
    fun `musicxml lee las tecnicas de technical y la barra final`() {
        val tab = MusicXmlTabParser.parse(MUSICXML, songId = "song-1", title = "X")

        val pluck = tab.measures[1].slots[0].strings.single()
        assertEquals(TabPosition(1, 10), pluck.position)
        assertTrue("hammer-on", TabTechnique.HAMMER in pluck.techniques)
        assertEquals(TabFrettingFinger.INDEX, pluck.frettingFinger)
        assertEquals(TabBarline.FINAL, tab.measures[1].barline)
    }

    @Test
    fun `musicxml ignora los simbolos que no modela sin lanzar`() {
        // El fixture trae <print>, <segno>, <ornaments><turn/></ornaments>, <lyric> y una
        // <direction> con texto: ninguno tiene equivalente y el parseo debe seguir.
        val tab = MusicXmlTabParser.parse(MUSICXML, songId = "song-1", title = "X")
        assertEquals(2, tab.measures.size)
    }

    @Test
    fun `musicxml sin notas no produce compases`() {
        val vacio = MusicXmlTabParser.parse(
            """<?xml version="1.0"?><score-partwise><part id="P1"></part></score-partwise>""",
            songId = "song-1",
            title = "Vacío"
        )
        assertTrue("no debe inventar compases", vacio.measures.isEmpty())
    }

    @Test
    fun `musicxml reconoce la afinacion estandar como guitarra, no como afinacion propia`() {
        // El fixture declara la afinación estándar de 6 cuerdas (EADGBE). Debe resolverse al
        // preset `Guitarra`: antes el parser la llamaba siempre «Afinación de la partitura» y el
        // usuario veía ese texto en vez del instrumento real.
        val tab = MusicXmlTabParser.parse(MUSICXML, songId = "song-1", title = "X")
        assertEquals("Guitarra", tab.tuning.name)
        assertEquals(Tuning.GUITAR.strings.map { it.pitch }, tab.tuning.strings.map { it.pitch })
    }

    @Test
    fun `musicxml con una afinacion que no es preset conserva las cuerdas declaradas`() {
        // Drop D no es lo que buscamos aquí (sí es preset): se baja la 5.ª a Sol, que no
        // corresponde a ningún preset, para comprobar que se conserva lo declarado.
        val abierta = MUSICXML.replace(
            "<tuning-step>A</tuning-step><tuning-octave>2</tuning-octave>",
            "<tuning-step>G</tuning-step><tuning-octave>2</tuning-octave>"
        )
        val tab = MusicXmlTabParser.parse(abierta, songId = "song-1", title = "X")
        assertEquals("Afinación de la partitura", tab.tuning.name)
        assertEquals(43, tab.tuning.pitchOf(5)?.midi)
    }

    // ── Guitar Pro 7/8 (GPIF) ────────────────────────────────────────────────────────────────────

    @Test
    fun `gpif lee afinacion, compas, tempo, notas y tecnicas`() {
        val tab = GpifTabParser.parse(GPIF, songId = "song-1", title = "Salmo 2")

        assertEquals("Guitarra", tab.tuning.name)
        assertEquals(6, tab.tuning.stringCount)
        assertEquals(40, tab.tuning.pitchOf(6)?.midi)
        assertEquals(64, tab.tuning.pitchOf(1)?.midi)
        assertEquals(ScoreTimeSignature("4", 4), tab.time)

        assertEquals(1, tab.measures.size)
        assertEquals(90, tab.measures[0].tempo)

        val slots = tab.measures[0].slots
        assertEquals(2, slots.size)
        assertEquals(RhythmFigure.QUARTER, slots[0].rhythmicFigure)
        val pluck = slots[0].strings.single()
        assertEquals(TabPosition(6, 3), pluck.position)
        assertTrue("HopoOrigin es martillo", TabTechnique.HAMMER in pluck.techniques)
        assertTrue("el beat sin notas es un silencio", slots[1].strings.isEmpty())
    }

    @Test
    fun `gpif ignora las propiedades de Guitar Pro que no modela`() {
        // El fixture incluye Slap, LetRing ausente, percusión y automatizaciones: se ignoran.
        val tab = GpifTabParser.parse(GPIF, songId = "song-1", title = "X")
        assertEquals(TabPosition(6, 3), tab.measures[0].slots[0].strings.single().position)
        assertTrue("el beat sin notas sigue siendo silencio", tab.measures[0].slots[1].strings.isEmpty())
    }

    // ── TuxGuitar ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `tuxguitar lee cuerdas, duraciones y tecnicas`() {
        val tab = TuxGuitarTabParser.parse(TUXGUITAR, songId = "song-1", title = "Magnificat")

        assertEquals(6, tab.tuning.stringCount)
        assertEquals(40, tab.tuning.pitchOf(6)?.midi)
        assertEquals(ScoreTimeSignature("4", 4), tab.time)
        assertEquals(1, tab.measures.size)
        assertEquals(100, tab.measures[0].tempo)

        val slots = tab.measures[0].slots
        assertEquals(2, slots.size)
        assertEquals(RhythmFigure.QUARTER, slots[0].rhythmicFigure)
        val pluck = slots[0].strings.single()
        assertEquals(TabPosition(6, 3), pluck.position)
        assertTrue("hammer", TabTechnique.HAMMER in pluck.techniques)
        assertTrue("el beat vacío es un silencio", slots[1].strings.isEmpty())
    }

    @Test
    fun `tuxguitar entiende el puntillo como figura con puntillo`() {
        val xml = TUXGUITAR.replace(
            "<duration>4</duration>",
            "<duration>4</duration><dotted>true</dotted>"
        )
        val tab = TuxGuitarTabParser.parse(xml, songId = "song-1", title = "X")
        assertEquals(RhythmFigure.DOTTED_QUARTER, tab.measures[0].slots[0].rhythmicFigure)
    }

    // ── Ida y vuelta MusicXML ────────────────────────────────────────────────────────────────────

    @Test
    fun `escribir y volver a leer musicxml conserva la musica`() {
        val original = tablatura()

        val xml = MusicXmlTabWriter.write(original)
        assertTrue("debe ser un score-partwise", xml.contains("<score-partwise"))
        assertTrue("debe declarar la tablatura", xml.contains("<fret>3</fret>"))

        val releida = MusicXmlTabParser.parse(xml, songId = "song-1", title = "Ida y vuelta")

        assertEquals(original.time, releida.time)
        assertEquals(original.tuning.stringCount, releida.tuning.stringCount)
        assertEquals(original.measures.size, releida.measures.size)

        original.measures.forEachIndexed { index, measure ->
            val copia = releida.measures[index]
            assertEquals("compás ${index + 1}: columnas", measure.slots.size, copia.slots.size)
            measure.slots.forEachIndexed { slotIndex, slot ->
                val slotCopia = copia.slots[slotIndex]
                assertEquals(
                    "compás ${index + 1}, columna ${slotIndex + 1}: figura",
                    slot.rhythmicFigure,
                    slotCopia.rhythmicFigure
                )
                assertEquals(
                    "compás ${index + 1}, columna ${slotIndex + 1}: cuerdas",
                    slot.strings.map { it.position },
                    slotCopia.strings.map { it.position }
                )
            }
        }

        // Las técnicas con equivalente en MusicXML sobreviven al viaje.
        assertTrue(
            "el martillo se conserva",
            TabTechnique.HAMMER in
                releida.measures[0].slots[0].strings.single { it.stringNumber == 6 }.techniques
        )
        assertEquals(TabRepeatMark.START, releida.measures[0].repeat)
        assertEquals(76, releida.measures[0].tempo)
    }

    @Test
    fun `el escritor escapa el titulo para no romper el xml`() {
        val xml = MusicXmlTabWriter.write(tablatura().copy(title = "Roca & <fuego>"))
        assertTrue(xml.contains("Roca &amp; &lt;fuego&gt;"))
        // Y aun así se vuelve a leer.
        val releida = MusicXmlTabParser.parse(xml, songId = "song-1", title = "X")
        assertEquals(2, releida.measures.size)
    }

    @Test
    fun `un silencio se escribe como rest y se lee como silencio`() {
        val conSilencio = tablatura().copy(
            measures = listOf(TabMeasure(slots = listOf(TabSlot(rhythmicFigure = RhythmFigure.QUARTER_REST))))
        )
        val xml = MusicXmlTabWriter.write(conSilencio)
        assertTrue(xml.contains("<rest/>"))

        val releida = MusicXmlTabParser.parse(xml, songId = "song-1", title = "X")
        assertEquals(RhythmFigure.QUARTER_REST, releida.measures.single().slots.single().rhythmicFigure)
        assertTrue(releida.measures.single().slots.single().strings.isEmpty())
        assertNull(releida.measures.single().repeat)
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    /** Tablatura de trabajo: dos compases con acorde, martillo, silencio y repetición. */
    private fun tablatura() = Tablature(
        id = "tab-1",
        songId = "song-1",
        title = "Adeste fideles",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(
                        strings = listOf(
                            TabPluck(position = TabPosition(5, 2)),
                            TabPluck(
                                position = TabPosition(6, 3),
                                techniques = setOf(TabTechnique.HAMMER)
                            )
                        ),
                        rhythmicFigure = RhythmFigure.QUARTER
                    ),
                    TabSlot(rhythmicFigure = RhythmFigure.EIGHTH_REST)
                ),
                repeat = TabRepeatMark.START,
                tempo = 76
            ),
            TabMeasure(
                slots = listOf(
                    TabSlot(
                        strings = listOf(TabPluck(position = TabPosition(1, 10))),
                        rhythmicFigure = RhythmFigure.EIGHTH
                    )
                )
            )
        )
    )

    private companion object {

        /** MusicXML 4.0 mínimo con dos compases, tablatura de 6 cuerdas y símbolos sin equivalente. */
        val MUSICXML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="4.0">
              <part-list>
                <score-part id="P1"><part-name>Guitarra</part-name></score-part>
              </part-list>
              <part id="P1">
                <measure number="1">
                  <print new-system="yes"/>
                  <attributes>
                    <divisions>4</divisions>
                    <key><fifths>0</fifths></key>
                    <time><beats>3</beats><beat-type>4</beat-type></time>
                    <clef><sign>TAB</sign><line>5</line></clef>
                    <staff-details>
                      <staff-lines>6</staff-lines>
                      <staff-tuning line="1">
                        <tuning-step>E</tuning-step><tuning-octave>4</tuning-octave>
                      </staff-tuning>
                      <staff-tuning line="2">
                        <tuning-step>B</tuning-step><tuning-octave>3</tuning-octave>
                      </staff-tuning>
                      <staff-tuning line="3">
                        <tuning-step>G</tuning-step><tuning-octave>3</tuning-octave>
                      </staff-tuning>
                      <staff-tuning line="4">
                        <tuning-step>D</tuning-step><tuning-octave>3</tuning-octave>
                      </staff-tuning>
                      <staff-tuning line="5">
                        <tuning-step>A</tuning-step><tuning-octave>2</tuning-octave>
                      </staff-tuning>
                      <staff-tuning line="6">
                        <tuning-step>E</tuning-step><tuning-octave>2</tuning-octave>
                      </staff-tuning>
                    </staff-details>
                  </attributes>
                  <direction placement="above">
                    <direction-type><words>Andante</words></direction-type>
                  </direction>
                  <barline location="left">
                    <bar-style>heavy-light</bar-style>
                    <repeat direction="forward"/>
                  </barline>
                  <note>
                    <pitch><step>G</step><octave>2</octave></pitch>
                    <duration>4</duration>
                    <voice>1</voice>
                    <type>quarter</type>
                    <notations>
                      <technical><string>6</string><fret>3</fret></technical>
                    </notations>
                  </note>
                  <note>
                    <chord/>
                    <pitch><step>B</step><octave>2</octave></pitch>
                    <duration>4</duration>
                    <voice>1</voice>
                    <type>quarter</type>
                    <notations>
                      <technical><string>5</string><fret>2</fret></technical>
                    </notations>
                  </note>
                  <note>
                    <rest/>
                    <duration>2</duration>
                    <voice>1</voice>
                    <type>eighth</type>
                    <lyric><text>Chris</text></lyric>
                  </note>
                </measure>
                <measure number="2">
                  <direction placement="above">
                    <direction-type><segno/></direction-type>
                  </direction>
                  <note>
                    <pitch><step>D</step><octave>5</octave></pitch>
                    <duration>2</duration>
                    <voice>1</voice>
                    <type>eighth</type>
                    <notations>
                      <ornaments><turn/></ornaments>
                      <technical>
                        <string>1</string>
                        <fret>10</fret>
                        <hammer-on type="start">H</hammer-on>
                        <fingering>1</fingering>
                      </technical>
                    </notations>
                  </note>
                  <barline location="right"><bar-style>light-heavy</bar-style></barline>
                </measure>
              </part>
            </score-partwise>
        """.trimIndent()

        /** `score.gpif` de Guitar Pro 7/8 con una pista de guitarra y dos tiempos. */
        val GPIF = """
            <?xml version="1.0" encoding="UTF-8"?>
            <GPIF>
              <GPFileVersion>7.0</GPFileVersion>
              <Score>
                <Title>Salmo 2</Title>
                <MasterBars>
                  <MasterBar>
                    <Time>4/4</Time>
                    <Tempo>90</Tempo>
                  </MasterBar>
                </MasterBars>
                <Tracks>
                  <Track id="0">
                    <Name>Guitarra</Name>
                    <Properties>
                      <Property name="Tuning"><Pitches>40 45 50 55 59 64</Pitches></Property>
                    </Properties>
                    <Bars>
                      <Bar id="0">
                        <Voices>
                          <Voice id="0">
                            <Beats>
                              <Beat id="0">
                                <Rhythm ref="0"/>
                                <Notes>
                                  <Note id="0">
                                    <Properties>
                                      <Property name="String"><Number>6</Number></Property>
                                      <Property name="Fret"><Number>3</Number></Property>
                                      <Property name="HopoOrigin"/>
                                      <Property name="Slap"/>
                                    </Properties>
                                  </Note>
                                </Notes>
                              </Beat>
                              <Beat id="1">
                                <Rhythm ref="1"/>
                                <Notes/>
                              </Beat>
                            </Beats>
                          </Voice>
                        </Voices>
                      </Bar>
                    </Bars>
                  </Track>
                </Tracks>
                <Rhythms>
                  <Rhythm id="0"><NoteValue>Quarter</NoteValue></Rhythm>
                  <Rhythm id="1"><NoteValue>Eighth</NoteValue></Rhythm>
                </Rhythms>
              </Score>
            </GPIF>
        """.trimIndent()

        /** `song.tg` de TuxGuitar: una pista de seis cuerdas y un compás de dos tiempos. */
        val TUXGUITAR = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tuxguitar>
              <song>
                <tracks>
                  <track id="0">
                    <name>Guitarra</name>
                    <strings>
                      <string><number>1</number><value>64</value></string>
                      <string><number>2</number><value>59</value></string>
                      <string><number>3</number><value>55</value></string>
                      <string><number>4</number><value>50</value></string>
                      <string><number>5</number><value>45</value></string>
                      <string><number>6</number><value>40</value></string>
                    </strings>
                    <measures>
                      <measure id="0">
                        <header>
                          <tempo>100</tempo>
                          <time-signature>
                            <numerator>4</numerator>
                            <denominator>4</denominator>
                          </time-signature>
                        </header>
                        <voices>
                          <voice id="0">
                            <beats>
                              <beat id="0">
                                <duration>4</duration>
                                <notes>
                                  <note>
                                    <string>6</string>
                                    <value>3</value>
                                    <hammer>true</hammer>
                                    <trill>true</trill>
                                  </note>
                                </notes>
                              </beat>
                              <beat id="1">
                                <duration>8</duration>
                                <empty>true</empty>
                              </beat>
                            </beats>
                          </voice>
                        </voices>
                      </measure>
                    </measures>
                  </track>
                </tracks>
              </song>
            </tuxguitar>
        """.trimIndent()
    }
}
