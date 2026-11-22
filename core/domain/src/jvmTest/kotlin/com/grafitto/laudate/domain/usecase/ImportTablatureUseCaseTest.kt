package com.grafitto.laudate.domain.usecase

import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.repository.TablatureRepository
import com.grafitto.laudate.domain.transfer.ScoreFileReader
import com.grafitto.laudate.domain.transfer.TablatureImportFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la **importación de partituras** como tablatura del canto.
 *
 * Cubren las dos mitades del camino: el caso de uso que **parsea y reemplaza** (`ImportTablatureUseCase`)
 * y el que **resuelve el fichero** —extensión, ZIP interno y aviso de formatos binarios—
 * (`ImportTablatureFromFileUseCase`), con un lector de ficheros falso para no depender del sistema.
 */
class ImportTablatureUseCaseTest {

    private val repository = FakeTablatureRepository()
    private val importTablature = ImportTablatureUseCase(repository, generateId = { "importada" })

    // ── ImportTablatureUseCase ───────────────────────────────────────────────────────────────────

    @Test
    fun `importa musicxml y reemplaza la tablatura previa del canto`() = runBlocking {
        repository.saveTablature(previo())

        val result = importTablature(
            format = TablatureImportFormat.MUSICXML,
            content = MUSICXML,
            songId = "song-1",
            title = "Adeste fideles"
        )

        assertEquals(TablatureImportFormat.MUSICXML, result.format)
        assertEquals("Adeste fideles", result.title)
        assertEquals(1, result.measureCount)
        assertEquals(2, result.noteCount)
        assertEquals("debe reemplazar la anterior", 1, result.replaced)

        val stored = repository.stored.value
        assertEquals("solo queda la importada", 1, stored.size)
        assertEquals("importada", stored.single().id)
        assertEquals("song-1", stored.single().songId)
        assertEquals(TabOrigin.IMPORTED_TEXT, stored.single().origin)
    }

    @Test
    fun `ignora los simbolos desconocidos del formato en vez de fallar`() = runBlocking {
        // El fixture trae <print>, <segno>, <ornaments> y una letra: se importa igual.
        val result = importTablature(
            format = TablatureImportFormat.MUSICXML,
            content = MUSICXML,
            songId = "song-1",
            title = "Con adornos"
        )
        assertEquals(1, result.measureCount)
    }

    @Test
    fun `el titulo vacio cae al del documento`() = runBlocking {
        val result = importTablature(
            format = TablatureImportFormat.MUSICXML,
            content = MUSICXML,
            songId = "song-1",
            title = "   "
        )
        assertEquals("Adeste fideles", result.title)
    }

    @Test
    fun `guitar pro 3 4 y 5 se rechaza con un mensaje que explica como convertirlo`() = runBlocking {
        val error = runCatching {
            importTablature(
                format = TablatureImportFormat.GP_LEGACY,
                content = "GP5\u0000basura",
                songId = "song-1",
                title = "Viejo"
            )
        }.exceptionOrNull()

        assertTrue("debe ser un error de uso", error is IllegalArgumentException)
        val mensaje = error?.message.orEmpty()
        assertTrue("debe nombrar el formato", mensaje.contains("Guitar Pro 3/4/5"))
        assertTrue("debe decir cómo convertirlo", mensaje.contains("MusicXML"))
    }

    @Test
    fun `un contenido vacio no se puede importar`() = runBlocking {
        val error = runCatching {
            importTablature(TablatureImportFormat.MUSICXML, "   ", songId = "song-1", title = "X")
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `una tablatura siempre pertenece a un canto`() = runBlocking {
        val error = runCatching {
            importTablature(TablatureImportFormat.MUSICXML, MUSICXML, songId = "", title = "X")
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    // ── ImportTablatureFromFileUseCase ───────────────────────────────────────────────────────────

    @Test
    fun `lee un musicxml de texto plano`() = runBlocking {
        val fromFile = fromFile(FakeScoreFileReader(text = MUSICXML))

        val result = fromFile(
            fileUri = "content://doc/1",
            fileName = "adeste.musicxml",
            songId = "song-1",
            title = "Adeste fideles"
        )

        assertEquals(TablatureImportFormat.MUSICXML, result.format)
        assertEquals(1, result.measureCount)
    }

    @Test
    fun `deduce el formato por el contenido cuando el nombre no ayuda`() = runBlocking {
        val fromFile = fromFile(FakeScoreFileReader(text = MUSICXML))

        val result = fromFile(
            fileUri = "content://doc/2",
            fileName = "partitura",
            songId = "song-1",
            title = "Sin extensión"
        )

        assertEquals(TablatureImportFormat.MUSICXML, result.format)
    }

    @Test
    fun `abre el zip de un mscz y lee su mscx`() = runBlocking {
        val reader = FakeScoreFileReader(archive = mapOf("salmo.mscx" to MSCX))
        val fromFile = fromFile(reader)

        val result = fromFile(
            fileUri = "content://doc/3",
            fileName = "salmo.mscz",
            songId = "song-1",
            title = "Salmo"
        )

        assertEquals(TablatureImportFormat.MSCZ, result.format)
        assertTrue("debe tener compases", result.measureCount > 0)
        assertTrue("debe pedir la entrada .mscx", reader.suffixReads.contains(".mscx"))
    }

    @Test
    fun `abre el score gpif de un gp`() = runBlocking {
        val reader = FakeScoreFileReader(archive = mapOf("Content/score.gpif" to GPIF))
        val fromFile = fromFile(reader)

        val result = fromFile(
            fileUri = "content://doc/4",
            fileName = "tema.gp",
            songId = "song-1",
            title = "Tema"
        )

        assertEquals(TablatureImportFormat.GPIF, result.format)
        assertEquals(1, result.measureCount)
        assertTrue("debe abrir la entrada conocida", "Content/score.gpif" in reader.exactReads)
    }

    @Test
    fun `abre el song tg de un tuxguitar`() = runBlocking {
        val reader = FakeScoreFileReader(archive = mapOf("song.tg" to TUXGUITAR))
        val fromFile = fromFile(reader)

        val result = fromFile(
            fileUri = "content://doc/5",
            fileName = "magnificat.tg",
            songId = "song-1",
            title = "Magnificat"
        )

        assertEquals(TablatureImportFormat.TUXGUITAR, result.format)
        assertEquals(1, result.measureCount)
    }

    @Test
    fun `el mxl se resuelve por el manifiesto container xml`() = runBlocking {
        val reader = FakeScoreFileReader(
            archive = mapOf(
                "META-INF/container.xml" to
                    """<?xml version="1.0"?><container><rootfiles><rootfile """ +
                    """full-path="score/adeste.xml"/></rootfiles></container>""",
                "score/adeste.xml" to MUSICXML
            )
        )
        val fromFile = fromFile(reader)

        val result = fromFile(
            fileUri = "content://doc/6",
            fileName = "adeste.mxl",
            songId = "song-1",
            title = "Adeste fideles"
        )

        assertEquals(TablatureImportFormat.MUSICXML, result.format)
        assertEquals(1, result.measureCount)
        assertTrue("debe leer el fichero que declara el manifiesto", "score/adeste.xml" in reader.exactReads)
    }

    @Test
    fun `un gp3 se rechaza antes de intentar leerlo`() = runBlocking {
        val reader = FakeScoreFileReader(text = "no debería leerse")
        val fromFile = fromFile(reader)

        val error = runCatching {
            fromFile("content://doc/7", "viejo.gp3", songId = "song-1", title = "Viejo")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("Guitar Pro 3/4/5"))
        assertTrue("no debe abrir el fichero", reader.textReads.isEmpty())
    }

    @Test
    fun `un fichero ilegible da un mensaje amable`() = runBlocking {
        val fromFile = fromFile(FakeScoreFileReader())

        val error = runCatching {
            fromFile("content://doc/8", "roto.mscz", songId = "song-1", title = "Roto")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("No se pudo leer"))
    }

    // ── Utilidades ───────────────────────────────────────────────────────────────────────────────

    private fun fromFile(reader: ScoreFileReader) =
        ImportTablatureFromFileUseCase(reader, importTablature)

    private fun previo() = Tablature(
        id = "vieja",
        songId = "song-1",
        title = "Antigua",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4)
    )

    /** Lector de ficheros en memoria: texto plano y entradas de ZIP ya «descomprimidas». */
    private class FakeScoreFileReader(
        private val text: String? = null,
        private val archive: Map<String, String> = emptyMap()
    ) : ScoreFileReader {

        val textReads = mutableListOf<String>()
        val exactReads = mutableListOf<String>()
        val suffixReads = mutableListOf<String>()

        override suspend fun readText(fileUri: String): String? {
            textReads += fileUri
            return text
        }

        override suspend fun readArchiveEntry(fileUri: String, entryName: String): String? {
            exactReads += entryName
            return archive[entryName]
        }

        override suspend fun readArchiveEntryEndingWith(fileUri: String, suffix: String): String? {
            suffixReads += suffix
            return archive.entries
                .firstOrNull { it.key.endsWith(suffix, ignoreCase = true) }
                ?.value
        }
    }

    /** Repositorio de tablaturas en memoria. */
    private class FakeTablatureRepository : TablatureRepository {
        val stored = MutableStateFlow<List<Tablature>>(emptyList())

        override fun observeTablaturesForSong(songId: String): Flow<List<Tablature>> =
            flowOf(stored.value.filter { it.songId == songId })

        override suspend fun getTablaturesForSong(songId: String): List<Tablature> =
            stored.value.filter { it.songId == songId }

        override suspend fun getTablatureById(id: String): Tablature? =
            stored.value.firstOrNull { it.id == id }

        override suspend fun getAllTablatures(): List<Tablature> = stored.value

        override suspend fun saveTablature(tablature: Tablature) {
            stored.value = stored.value.filterNot { it.id == tablature.id } + tablature
        }

        override suspend fun deleteTablature(id: String) {
            stored.value = stored.value.filterNot { it.id == id }
        }

        override suspend fun deleteTablaturesForSong(songId: String) {
            stored.value = stored.value.filterNot { it.songId == songId }
        }

        override fun observeSongIdsWithTablatures(): Flow<Set<String>> =
            flowOf(stored.value.map { it.songId }.toSet())
    }

    private companion object {

        /** MusicXML de un compás con un acorde de dos notas y símbolos sin equivalente. */
        val MUSICXML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="4.0">
              <work><work-title>Adeste fideles</work-title></work>
              <part-list>
                <score-part id="P1"><part-name>Guitarra</part-name></score-part>
              </part-list>
              <part id="P1">
                <measure number="1">
                  <print new-system="yes"/>
                  <attributes>
                    <divisions>4</divisions>
                    <time><beats>4</beats><beat-type>4</beat-type></time>
                    <staff-details>
                      <staff-lines>6</staff-lines>
                      <staff-tuning line="1"><tuning-step>E</tuning-step><tuning-octave>4</tuning-octave></staff-tuning>
                      <staff-tuning line="2"><tuning-step>B</tuning-step><tuning-octave>3</tuning-octave></staff-tuning>
                      <staff-tuning line="3"><tuning-step>G</tuning-step><tuning-octave>3</tuning-octave></staff-tuning>
                      <staff-tuning line="4"><tuning-step>D</tuning-step><tuning-octave>3</tuning-octave></staff-tuning>
                      <staff-tuning line="5"><tuning-step>A</tuning-step><tuning-octave>2</tuning-octave></staff-tuning>
                      <staff-tuning line="6"><tuning-step>E</tuning-step><tuning-octave>2</tuning-octave></staff-tuning>
                    </staff-details>
                  </attributes>
                  <direction placement="above">
                    <direction-type><segno/></direction-type>
                  </direction>
                  <note>
                    <pitch><step>G</step><octave>2</octave></pitch>
                    <duration>4</duration>
                    <voice>1</voice>
                    <type>quarter</type>
                    <notations>
                      <ornaments><turn/></ornaments>
                      <technical><string>6</string><fret>3</fret></technical>
                    </notations>
                    <lyric><text>Chris</text></lyric>
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
                </measure>
              </part>
            </score-partwise>
        """.trimIndent()

        /** `.mscx` de MuseScore: una parte con una cuerda y una nota. */
        val MSCX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <museScore version="4.10">
              <Score>
                <Part>
                  <Staff id="1">
                    <Measure>
                      <voice>
                        <TimeSig><sigN>4</sigN><sigD>4</sigD></TimeSig>
                        <Chord>
                          <durationType>quarter</durationType>
                          <Note>
                            <pitch>55</pitch>
                            <string>5</string>
                            <fret>3</fret>
                          </Note>
                        </Chord>
                      </voice>
                    </Measure>
                  </Staff>
                </Part>
              </Score>
            </museScore>
        """.trimIndent()

        /** `score.gpif` mínimo: una pista de guitarra y un tiempo con una nota. */
        val GPIF = """
            <?xml version="1.0" encoding="UTF-8"?>
            <GPIF>
              <Score>
                <MasterBars><MasterBar><Time>4/4</Time><Tempo>80</Tempo></MasterBar></MasterBars>
                <Tracks>
                  <Track id="0">
                    <Name>Guitarra</Name>
                    <Properties>
                      <Property name="Tuning"><Pitches>40 45 50 55 59 64</Pitches></Property>
                    </Properties>
                    <Bars>
                      <Bar id="0">
                        <Voices><Voice id="0"><Beats>
                          <Beat id="0">
                            <Rhythm ref="0"/>
                            <Notes><Note id="0"><Properties>
                              <Property name="String"><Number>6</Number></Property>
                              <Property name="Fret"><Number>3</Number></Property>
                            </Properties></Note></Notes>
                          </Beat>
                        </Beats></Voice></Voices>
                      </Bar>
                    </Bars>
                  </Track>
                </Tracks>
                <Rhythms><Rhythm id="0"><NoteValue>Quarter</NoteValue></Rhythm></Rhythms>
              </Score>
            </GPIF>
        """.trimIndent()

        /** `song.tg` mínimo: una pista de seis cuerdas y un tiempo con una nota. */
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
                            <numerator>4</numerator><denominator>4</denominator>
                          </time-signature>
                        </header>
                        <voices><voice id="0"><beats>
                          <beat id="0">
                            <duration>4</duration>
                            <notes><note><string>6</string><value>3</value></note></notes>
                          </beat>
                        </beats></voice></voices>
                      </measure>
                    </measures>
                  </track>
                </tracks>
              </song>
            </tuxguitar>
        """.trimIndent()
    }
}
