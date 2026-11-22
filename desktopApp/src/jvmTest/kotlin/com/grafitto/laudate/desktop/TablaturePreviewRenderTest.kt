package com.grafitto.laudate.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreKey
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabAccidental
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabBendAmount
import com.grafitto.laudate.domain.model.tablature.TabBarline
import com.grafitto.laudate.domain.tablature.FretMarkStyle
import com.grafitto.laudate.domain.tablature.MinimStyle
import com.grafitto.laudate.domain.tablature.ParenthesizeTiedFret
import com.grafitto.laudate.domain.tablature.RhythmRepeat
import com.grafitto.laudate.domain.tablature.ShowTiedFret
import com.grafitto.laudate.domain.tablature.TabChordValidator
import com.grafitto.laudate.domain.tablature.TabMetronome
import com.grafitto.laudate.domain.tablature.TabMixer
import com.grafitto.laudate.domain.tablature.TabMeasureMeter
import com.grafitto.laudate.domain.tablature.TabNotationScale
import com.grafitto.laudate.domain.tablature.TabRhythmGeometry
import com.grafitto.laudate.domain.tablature.TablatureSystemsLayout
import com.grafitto.laudate.domain.tablature.TabStaffOptions
import com.grafitto.laudate.domain.tablature.TabTextKind
import com.grafitto.laudate.domain.tablature.TablatureEditing
import com.grafitto.laudate.domain.tablature.TablatureLayout
import com.grafitto.laudate.domain.model.tablature.TabDynamic
import com.grafitto.laudate.domain.model.tablature.TabFrettingFinger
import com.grafitto.laudate.domain.model.tablature.TabGrace
import com.grafitto.laudate.domain.model.tablature.TabGraceType
import com.grafitto.laudate.domain.model.tablature.TabHairpin
import com.grafitto.laudate.domain.model.tablature.TabHairpinType
import com.grafitto.laudate.domain.model.tablature.TabJump
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabNavigationMark
import com.grafitto.laudate.domain.model.tablature.TabOctaveShift
import com.grafitto.laudate.domain.model.tablature.TabPickingFinger
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import com.grafitto.laudate.domain.model.tablature.TabTupletBracketType
import com.grafitto.laudate.domain.model.tablature.TabTupletNumberType
import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.serializer.MsczParser
import com.grafitto.laudate.ui.tablature.TabCanvasHitTester
import com.grafitto.laudate.ui.tablature.TabCanvasOverlay
import com.grafitto.laudate.ui.tablature.TablatureCanvas
import com.grafitto.laudate.ui.tablature.TablatureEditorPanel
import com.grafitto.laudate.ui.navigation.TablatureToolSettings
import com.grafitto.laudate.ui.tablature.TablatureUiIntent
import com.grafitto.laudate.ui.tablature.TablatureUiState
import com.grafitto.laudate.ui.tablature.TablatureView
import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.ui.theme.LaudateTheme
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.grafitto.laudate.ui.tablature.TAB_CANVAS_TEST_TAG
import com.grafitto.laudate.ui.tablature.TAB_PALETTE_CELL_TEST_TAG
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isToggleable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.geometry.Size
import kotlin.time.Duration.Companion.minutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO

/**
 * **Verificación visual** de la tablatura: renderiza la **vista previa real** (`TablatureView`)
 * con una tablatura que ejerce TODOS los símbolos del modelo y vuelca el resultado a un PNG, para
 * poder inspeccionarlo a ojo (no sustituye a los tests de dominio, los complementa).
 *
 * El PNG se escribe en `desktopApp/build/tablature-preview/`. Es una prueba **manual**: el test solo
 * comprueba que el render termina sin fallos y produce una imagen no vacía.
 */
@OptIn(ExperimentalTestApi::class)
class TablaturePreviewRenderTest {

    /**
     * **Fracción mínima del lado de la celda** que tiene que ocupar la tinta de un símbolo (D-81).
     *
     * No es una meta estética sino un **detector de regresión**: cuando el glifo no encuentra la fuente
     * musical y cae a la de respaldo se dibuja a ~1 px (0,02 del lado), que es el defecto real que hay
     * que impedir. Las marcas pequeñas por naturaleza —el picado es un punto, el portato una línea con
     * un punto, el grupeto un glifo bajo— quedan por encima del 4 %.
     */
    private val MIN_INK_RATIO = 0.04f

    private fun pluck(
        string: Int,
        fret: Int,
        vararg techniques: TabTechnique,
        bend: TabBendAmount? = null,
        span: Int? = null
    ) = TabPluck(
        position = com.grafitto.laudate.domain.model.tablature.TabPosition(string, fret),
        techniques = techniques.toSet(),
        bendAmount = bend,
        palmMuteSpan = span
    )

    /**
     * **Tablatura de referencia REAL**: el `salmo2.mscz` de MuseScore, parseado con [MsczParser].
     *
     * (Punto 0 del usuario, 2026-10-14) Antes el PNG de comparación pintaba un **fixture escrito a
     * mano** ([symbolCoverageTablature]) que se *parecía* al salmo2 pero no era el salmo2: cualquier
     * diferencia con el PDF podía venir del fixture, no del pintor. El arnés no era **honesto**. Ahora
     * el PNG de comparación pinta el fichero real, el mismo que usa `MsczParserSalmo2Test`.
     *
     * El `.mscz` es un ZIP: se extrae el `*.mscx` y lo parsea [MsczParser] (Kotlin puro). Si el fichero
     * no está, el test se **salta** (`assumeTrue`), como el resto del banco del salmo2.
     */
    private fun salmo2Tablature(): Tablature? {
        val f = listOf(
            File("../core/domain/src/jvmTest/resources/salmo2.mscz"),
            File("core/domain/src/jvmTest/resources/salmo2.mscz"),
            File("src/jvmTest/resources/salmo2.mscz")
        ).firstOrNull { it.exists() } ?: return null
        val mscx = ZipInputStream(f.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name.endsWith(".mscx")) return@use zip.readBytes().toString(Charsets.UTF_8)
                e = zip.nextEntry
            }
            null
        } ?: return null
        return MsczParser.parse(mscx, songId = "salmo2")
    }

    /**
     * **Tablatura de cobertura de símbolos**: un fixture escrito a mano que ejerce TODOS los símbolos
     * del modelo (técnicas, articulaciones, digitaciones, dinámicas, repeticiones, tempo…). No es el
     * salmo2: sirve para que el render de la vista previa y de la paleta toquen cada rama del pintor.
     * Para comparar con el PDF se usa [salmo2Tablature].
     */
    private fun symbolCoverageTablature(): Tablature {
        fun pluck(string: Int, fret: Int, vararg tech: TabTechnique) = TabPluck(
            position = TabPosition(string, fret),
            techniques = tech.toSet()
        )
        fun slot(figure: RhythmFigure, vararg plucks: TabPluck) =
            TabSlot(strings = plucks.toList(), rhythmicFigure = figure)
        fun rest(figure: RhythmFigure) = TabSlot(rhythmicFigure = figure)

        return Tablature(
            id = "salmo",
            songId = "song-1",
            title = "Salmo",
            instrument = "Guitarra clásica",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH,
            writtenKey = ScoreKey(0),
            origin = TabOrigin.IMPORTED_TEXT,
            note = "Volcado literal de salmo.mscz",
            measures = listOf(
                // c1: 1(5ª)↑  2(4ª)↓  3(3ª)↑  0(2ª)   ♩=80
                TabMeasure(
                    slots = listOf(
                        slot(RhythmFigure.QUARTER, pluck(5, 1, TabTechnique.PICK_UP)),
                        slot(RhythmFigure.QUARTER, pluck(4, 2, TabTechnique.PICK_DOWN)),
                        slot(RhythmFigure.QUARTER, pluck(3, 3, TabTechnique.PICK_UP)),
                        slot(RhythmFigure.QUARTER, pluck(2, 0))
                    ),
                    tempo = 80
                ),
                // c2: 0 0 0 0 (2ª-5ª) con acento en la 1ª; ♩=80 «Andante» (el rótulo es DEL TEMPO, no un
                // texto libre: en el `.mscz` viene en `<Tempo><text>Andante</text>`, y MuseScore lo
                // dibuja como «♩ = 80 · Andante». Por eso aquí NO se pone `text`, solo `tempo`).
                TabMeasure(
                    slots = listOf(
                        slot(RhythmFigure.QUARTER, pluck(2, 0).copy(articulations = setOf(TabArticulation.ACCENT))),
                        slot(RhythmFigure.QUARTER, pluck(3, 0)),
                        slot(RhythmFigure.QUARTER, pluck(4, 0)),
                        slot(RhythmFigure.QUARTER, pluck(5, 0))
                    ),
                    dynamic = TabDynamic.MP
                ),
                // c3: ocho corcheas 0 (5ª-4ª-3ª-2ª-2ª-2ª-3ª-4ª), acento en la 1ª; pp
                TabMeasure(
                    slots = listOf(
                        slot(RhythmFigure.EIGHTH, pluck(5, 0).copy(articulations = setOf(TabArticulation.ACCENT))),
                        slot(RhythmFigure.EIGHTH, pluck(4, 0)),
                        slot(RhythmFigure.EIGHTH, pluck(3, 0)),
                        slot(RhythmFigure.EIGHTH, pluck(2, 0)),
                        slot(RhythmFigure.EIGHTH, pluck(2, 0)),
                        slot(RhythmFigure.EIGHTH, pluck(2, 0)),
                        slot(RhythmFigure.EIGHTH, pluck(3, 0)),
                        slot(RhythmFigure.EIGHTH, pluck(4, 0))
                    ),
                    dynamic = TabDynamic.PP,
                    // **Regulador** de crescendo que abarca dos compases: la cuña arranca **después**
                    // del `pp` y llega al final del compás siguiente.
                    hairpin = TabHairpin(TabHairpinType.CRESCENDO, lengthMeasures = 2)
                ),
                // c4: semicorcheas 0(5ª) 0(4ª) 0(3ª) sil. 0(2ª) sil. | 0(2ª) 0(3ª) 0(4ª) corchea
                TabMeasure(
                    slots = listOf(
                        slot(RhythmFigure.SIXTEENTH, pluck(5, 0, TabTechnique.HARMONIC)),
                        slot(RhythmFigure.SIXTEENTH, pluck(4, 0)),
                        slot(RhythmFigure.SIXTEENTH, pluck(3, 0)),
                        rest(RhythmFigure.SIXTEENTH_REST),
                        slot(RhythmFigure.SIXTEENTH, pluck(2, 0)),
                        rest(RhythmFigure.SIXTEENTH_REST),
                        slot(RhythmFigure.QUARTER, pluck(2, 0)),
                        slot(RhythmFigure.QUARTER, pluck(3, 0)),
                        slot(RhythmFigure.EIGHTH, pluck(4, 0))
                    )
                )
            )
        )
    }

    /**
     * **Ligaduras de prolongación y reproducción** (B-10 y B-9): dos compases con notas ligadas dentro
     * del compás y **cruzando** la barra, más una nota con prolongación (`isHeld`). Es el fixture que
     * usa [renderTiesAndPlaybackToPng] para comprobar a ojo que el arco de ligadura se pinta **debajo**
     * de la línea de la cuerda y que la banda de reproducción marca la columna que suena.
     */
    private fun tiesTablature(): Tablature {
        fun tied(string: Int, fret: Int, tie: Boolean = false) = TabSlot(
            strings = listOf(TabPluck(TabPosition(string, fret), tiedToNext = tie)),
            rhythmicFigure = RhythmFigure.QUARTER
        )
        fun plain(string: Int, fret: Int) = TabSlot(
            strings = listOf(TabPluck(TabPosition(string, fret))),
            rhythmicFigure = RhythmFigure.QUARTER
        )
        val measures = listOf(
            TabMeasure(
                listOf(
                    tied(1, 3, tie = true),
                    tied(1, 3, tie = true),
                    tied(1, 3),
                    plain(2, 1)
                )
            ),
            TabMeasure(
                listOf(
                    plain(3, 0),
                    TabSlot(isHeld = true, rhythmicFigure = RhythmFigure.QUARTER),
                    plain(1, 5),
                    plain(1, 5)
                )
            )
        )
        return Tablature(
            id = "ties",
            songId = "s1",
            title = "Ligaduras",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = measures,
            origin = TabOrigin.CREATED
        )
    }

    /**
     * (D-94) **La rejilla de trabajo no es musica.** Un `4/4` recien abierto tiene ocho columnas de
     * silencio de corchea en el modelo, pero el lector tiene que ver **un unico silencio de compas**
     * centrado, como en MuseScore. Antes se pintaban los ocho y la pantalla se llenaba de silencios
     * al abrir el editor («salen silencios a lo loco», 2026-10-27).
     *
     * Se comprueba en el **modelo de maquetacion** (que es lo que pinta el lienzo) y ademas se
     * escribe el PNG para revision visual.
     */
    /**
     * (D-94) **Los silencios se agrupan al pulso.** Dos negras en un `4/4` dejan **una** blanca
     * (pulsos 3-4), no dos silencios de negra ni cuatro. Es lo que hace MuseScore y lo que el
     * usuario espera al escribir seguido.
     */
    @Test
    fun dosNegrasDejanUnaBlancaYNoDosSilenciosDeNegra() {
        val rejilla = TablatureEditing.emptyCompleteMeasure(
            ScoreTimeSignature("4", 4),
            TabResolution.EIGHTH
        )
        var medidas = listOf(rejilla)
        // Dos negras en el pulso 1 y el 2, como al teclear seguidas con la negra activa.
        medidas = TablatureEditing.setFigureAbsorbing(medidas, 0, 0, RhythmFigure.QUARTER)
        medidas = TablatureEditing.setFret(medidas, 0, 0, 1, 3)
        medidas = TablatureEditing.rebalanceMeasure(
            medidas, 0, ScoreTimeSignature("4", 4), TabResolution.EIGHTH, RhythmFigure.QUARTER
        )
        val tras1 = medidas[0]
        assertEquals("negra + silencio de negra + blanca", 3, tras1.slots.size)
        assertEquals(RhythmFigure.QUARTER_REST, tras1.slots[1].rhythmicFigure)
        assertEquals(RhythmFigure.HALF_REST, tras1.slots[2].rhythmicFigure)
        assertEquals(4.0, TabMeasureMeter.durationOf(tras1, TabResolution.EIGHTH), 1e-6)

        val tablature = Tablature(
            id = "t", songId = "s1", title = "Dos negras", instrument = "Guitarra",
            tuning = Tuning.GUITAR, time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH, measures = medidas, origin = TabOrigin.CREATED
        )
        val marcas = TablatureSystemsLayout.layout(tablature, maxWidth = 900f)
            .flatMap { it.drawing.rhythmMarks }
        // La nota (negra) + el silencio de negra + la blanca: tres simbolos, no cuatro.
        assertEquals(3, marcas.size)
    }

    @Test
    fun laRejillaVaciaSeDibujaConUnUnicoSilencio() {
        val rejilla = TablatureEditing.emptyCompleteMeasure(
            ScoreTimeSignature("4", 4),
            TabResolution.EIGHTH
        )
        val tablature = Tablature(
            id = "t",
            songId = "s1",
            title = "Vacio",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH,
            measures = listOf(rejilla, rejilla),
            origin = TabOrigin.CREATED
        )
        val sistemas = TablatureSystemsLayout.layout(tablature, maxWidth = 900f)
        val marcas = sistemas.flatMap { it.drawing.rhythmMarks }
        assertEquals(
            "dos compases vacios se dibujan con DOS silencios de compas, no con dieciseis",
            2,
            marcas.size
        )
        assertTrue("y todos son silencio de compas", marcas.all { it.figure == RhythmFigure.WHOLE_REST })

        runDesktopComposeUiTest(width = 900, height = 420) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(860.dp)
                            .background(androidx.compose.ui.graphics.Color.White)
                    ) {
                        TablatureCanvas(tablature = tablature, scale = 1.4f)
                    }
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-empty-grid.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue("no se genero el PNG del compas vacio", out.exists() && out.length() > 0)
            println("PNG del compas vacio: ${out.absolutePath} (${out.length()} bytes)")
        }
    }

    /**
     * **Verificación visual** de las ligaduras (B-10) y de la banda de reproducción (B-9). Escribe
     * `tablature-ties.png` con el cursor de reproducción sobre la segunda columna del primer compás.
     */
    @Test
    fun renderTiesAndPlaybackToPng() {
        val tablature = tiesTablature()
        runDesktopComposeUiTest(width = 900, height = 420) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(860.dp)
                            .background(androidx.compose.ui.graphics.Color.White)
                    ) {
                        TablatureCanvas(
                            tablature = tablature,
                            scale = 1.4f,
                            overlay = TabCanvasOverlay(playbackCursor = 0 to 1)
                        )
                    }
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-ties.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue("no se genero el PNG de ligaduras", out.exists() && out.length() > 0)
            println("PNG de ligaduras: ${out.absolutePath} (${out.length()} bytes)")
        }
    }

    /**
     * **Verificación visual** de la ligadura de expresión (`S` de MuseScore). Escribe
     * `tablature-slurs.png` con una cadena de tres casillas ligadas (un solo arco) y una ligadura
     * suelta, para comprobar a ojo que el arco va **por encima** de la primera cuerda y que la cadena
     * se dibuja **una vez**, no una por par.
     */
    @Test
    fun renderSlursToPng() {
        fun note(string: Int, fret: Int, slur: Boolean = false) = TabSlot(
            strings = listOf(TabPluck(TabPosition(string, fret))),
            rhythmicFigure = RhythmFigure.QUARTER,
            slurToNext = slur
        )
        val tablature = Tablature(
            id = "slurs",
            songId = "s1",
            title = "Ligaduras de expresión",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(
                    listOf(
                        note(1, 3, slur = true),
                        note(1, 5, slur = true),
                        note(1, 7),
                        note(2, 1, slur = true)
                    )
                ),
                // Salto de sistema forzado: la cadena de ligaduras del compás 1 queda **abierta** al
                // final del primer sistema, así que el segundo tiene que **reanudar** el arco.
                TabMeasure(
                    listOf(note(2, 3), note(3, 0), note(3, 2), note(1, 0)),
                    forceSystemBreak = true
                )
            ),
            origin = TabOrigin.CREATED
        )
        runDesktopComposeUiTest(width = 900, height = 700) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(860.dp)
                            .background(androidx.compose.ui.graphics.Color.White)
                    ) {
                        TablatureCanvas(tablature = tablature, scale = 1.4f)
                    }
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-slurs.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue("no se genero el PNG de ligaduras de expresion", out.exists() && out.length() > 0)
            println("PNG de ligaduras de expresion: ${out.absolutePath} (${out.length()} bytes)")
        }
    }

    /**
     * **Reguladores** (*hairpins*): la cuña de crescendo y la de diminuendo se dibujan bajo la
     * cuerda inferior, en la franja de las dinámicas, y abarcan los compases que declaran. Se
     * renderiza a PNG para poder comprobarlo a ojo y por píxeles.
     */
    @Test
    fun renderHairpinsToPng() {
        fun note(string: Int, fret: Int) = TabSlot(
            strings = listOf(TabPluck(TabPosition(string, fret))),
            rhythmicFigure = RhythmFigure.QUARTER
        )
        val tablature = Tablature(
            id = "hairpins",
            songId = "s1",
            title = "Reguladores",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(
                    slots = listOf(note(1, 3), note(2, 1), note(3, 0), note(4, 2)),
                    dynamic = TabDynamic.MP,
                    hairpin = TabHairpin(TabHairpinType.CRESCENDO, lengthMeasures = 2)
                ),
                TabMeasure(slots = listOf(note(1, 5), note(2, 3), note(3, 2), note(4, 0))),
                TabMeasure(
                    slots = listOf(note(1, 7), note(2, 5), note(3, 4), note(4, 2)),
                    hairpin = TabHairpin(TabHairpinType.DIMINUENDO, lengthMeasures = 1)
                )
            ),
            origin = TabOrigin.CREATED
        )
        // A `scale = 1.0` los tres compases caben en **un solo sistema**, que es lo que hace falta
        // para ver las dos cuñas seguidas y comprobar que la de crescendo **abre** y la de
        // diminuendo **cierra**.
        runDesktopComposeUiTest(width = 1200, height = 400) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(1160.dp)
                            .height(320.dp)
                            .background(androidx.compose.ui.graphics.Color.White)
                    ) {
                        TablatureCanvas(tablature = tablature, scale = 1.0f)
                    }
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-hairpins.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue("no se genero el PNG de reguladores", out.exists() && out.length() > 0)
            println("PNG de reguladores: ${out.absolutePath} (${out.length()} bytes)")
        }
    }

    /**
     * **Trazos del trémolo** (D-28): la paleta «Trémolos» de MuseScore ofrece `tremolo1`…`tremolo4`,
     * y cada trazo es una figura de repetición (corchea, semicorchea, fusa, semifusa). Se renderiza
     * una fila con los cuatro números para poder **contar los trazos por píxeles**: el de un trazo
     * tiene que dibujar **una** barra oblicua y el de cuatro, **cuatro**.
     */
    @Test
    fun renderTremoloStrokesToPng() {
        fun note(string: Int, fret: Int, strokes: Int) = TabSlot(
            strings = listOf(
                TabPluck(
                    TabPosition(string, fret),
                    techniques = setOf(TabTechnique.TREMOLO),
                    tremoloStrokes = strokes
                )
            ),
            rhythmicFigure = RhythmFigure.QUARTER
        )
        val tablature = Tablature(
            id = "tremolos",
            songId = "s1",
            title = "Tremolos",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(
                    slots = listOf(
                        note(1, 3, 1),
                        note(2, 3, 2),
                        note(3, 3, 3),
                        note(4, 3, 4)
                    )
                )
            ),
            origin = TabOrigin.CREATED
        )
        runDesktopComposeUiTest(width = 1200, height = 400) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(1160.dp)
                            .height(320.dp)
                            .background(androidx.compose.ui.graphics.Color.White)
                    ) {
                        TablatureCanvas(tablature = tablature, scale = 1.0f)
                    }
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-tremolos.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue("no se genero el PNG de tremolos", out.exists() && out.length() > 0)
            println("PNG de tremolos: ${out.absolutePath} (${out.length()} bytes)")
        }
    }

    /**
     * **Alineación del tempo** (D-26): el rótulo `♩ = 80` empieza en el **borde izquierdo de la
     * fracción de compás**, no en el centro de la primera columna.
     *
     * Es la regla de MuseScore (`TLayout::layoutTempoText`, `tlayout.cpp:5766-5775`): cuando el tempo
     * cae en el primer tiempo de un compás que abre sistema, el rótulo **retrocede** hasta el borde
     * izquierdo de la fracción. Medido del `salmo2.pdf`: el `4/4` ocupa x 352–387 y el `♩` empieza en
     * **351**, un píxel a su izquierda.
     *
     * Se renderiza a PNG para poder comprobarlo por píxeles: el `♩` tiene que caer **a la izquierda**
     * del centro de la fracción y **a la derecha** de la clave `TAB`.
     */
    @Test
    fun renderTempoAlignmentToPng() {
        fun note(string: Int, fret: Int) = TabSlot(
            strings = listOf(TabPluck(TabPosition(string, fret))),
            rhythmicFigure = RhythmFigure.QUARTER
        )
        val tablature = Tablature(
            id = "tempo",
            songId = "s1",
            title = "Tempo",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(
                    slots = listOf(note(1, 0), note(2, 1), note(3, 0), note(4, 2)),
                    tempo = 80,
                    tempoText = "\uECA5 = 80"
                ),
                TabMeasure(slots = listOf(note(1, 3), note(2, 1), note(3, 0), note(4, 2)))
            ),
            origin = TabOrigin.CREATED
        )
        runDesktopComposeUiTest(width = 1200, height = 400) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(1160.dp)
                            .height(320.dp)
                            .background(androidx.compose.ui.graphics.Color.White)
                    ) {
                        TablatureCanvas(tablature = tablature, scale = 1.0f)
                    }
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-tempo.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue("no se genero el PNG de tempo", out.exists() && out.length() > 0)
            println("PNG de tempo: ${out.absolutePath} (${out.length()} bytes)")
        }
    }

    /**
     * **Notas de adorno** (*grace notes*): la cifra **pequeña** a la izquierda de la principal.
     *
     * Se comprueba midiendo los píxeles del PNG: la cifra de adorno tiene que ser **más baja** que la
     * principal (0,7 del tamaño, `graceNoteMag` de MuseScore) y estar **a su izquierda**, en la misma
     * banda vertical (misma cuerda).
     */
    @Test
    fun renderGraceNotesToPng() {
        fun note(string: Int, fret: Int) = TabSlot(
            strings = listOf(TabPluck(TabPosition(string, fret))),
            rhythmicFigure = RhythmFigure.QUARTER
        )
        val tablature = Tablature(
            id = "graces",
            songId = "s1",
            title = "Adornos",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(
                    slots = listOf(
                        // Sin adorno: la referencia de tamaño.
                        note(1, 3),
                        // Un adorno: cifra pequeña a la izquierda.
                        note(2, 5).copy(
                            graces = listOf(TabGrace(2, 7, TabGraceType.ACCIACCATURA))
                        ),
                        // Dos adornos: dos cifras pequeñas escalonadas.
                        note(3, 2).copy(
                            graces = listOf(
                                TabGrace(3, 4, TabGraceType.ACCIACCATURA),
                                TabGrace(3, 0, TabGraceType.APPOGGIATURA)
                            )
                        ),
                        note(4, 1)
                    )
                )
            ),
            origin = TabOrigin.CREATED
        )
        runDesktopComposeUiTest(width = 1200, height = 400) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(1160.dp)
                            .height(320.dp)
                            .background(androidx.compose.ui.graphics.Color.White)
                    ) {
                        TablatureCanvas(tablature = tablature, scale = 1.0f)
                    }
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-graces.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue("no se genero el PNG de adornos", out.exists() && out.length() > 0)
            println("PNG de adornos: ${out.absolutePath} (${out.length()} bytes)")
        }
    }

    /**
     * **Avisos de acorde** (F-08): la columna que no se puede tocar lleva un **punto rojo** por encima
     * del pentagrama. Se comprueba midiendo los píxeles del PNG: el punto tiene que estar **dentro** de
     * la franja superior (por encima de la primera cuerda) y **centrado** en la columna imposible, y
     * **no** aparecer en la columna correcta.
     */
    @Test
    fun renderChordIssuesToPng() {
        fun note(string: Int, fret: Int) = TabSlot(
            strings = listOf(TabPluck(TabPosition(string, fret))),
            rhythmicFigure = RhythmFigure.QUARTER
        )
        val tablature = Tablature(
            id = "issues",
            songId = "s1",
            title = "Avisos",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(
                    slots = listOf(
                        // Correcta: no debe llevar punto.
                        note(1, 0),
                        // Imposible: del traste 1 al 9 en la misma columna.
                        TabSlot(
                            strings = listOf(
                                TabPluck(TabPosition(1, 1)),
                                TabPluck(TabPosition(2, 9))
                            ),
                            rhythmicFigure = RhythmFigure.QUARTER
                        ),
                        // Correcta: no debe llevar punto.
                        note(3, 2)
                    )
                )
            ),
            origin = TabOrigin.CREATED
        )
        val issues = TabChordValidator.bySlot(tablature).keys
        assertEquals("solo la columna 1 tiene avisos", setOf(0 to 1), issues)
        runDesktopComposeUiTest(width = 1200, height = 400) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(1160.dp)
                            .height(320.dp)
                            .background(androidx.compose.ui.graphics.Color.White)
                    ) {
                        TablatureCanvas(
                            tablature = tablature,
                            scale = 1.0f,
                            overlay = TabCanvasOverlay(chordIssues = issues)
                        )
                    }
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-chord-issues.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue("no se genero el PNG de avisos", out.exists() && out.length() > 0)
            println("PNG de avisos: ${out.absolutePath} (${out.length()} bytes)")
        }
    }

    /**
     * **Bucle de rango** (D-23): el corchete se dibuja **por encima** del pentagrama y abarca
     * exactamente los compases del rango. Se comprueba midiendo los píxeles del PNG: la línea
     * horizontal tiene que empezar en el borde izquierdo del primer compás del bucle y acabar en el
     * borde derecho del último, y no puede salirse de ellos.
     */
    @Test
    fun renderLoopBracketToPng() {
        fun note(string: Int, fret: Int) = TabSlot(
            strings = listOf(TabPluck(TabPosition(string, fret))),
            rhythmicFigure = RhythmFigure.QUARTER
        )
        val tablature = Tablature(
            id = "loop",
            songId = "s1",
            title = "Bucle",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = List(4) { m ->
                TabMeasure(slots = listOf(note(1, m), note(2, m + 1), note(3, m + 2), note(4, m + 3)))
            },
            origin = TabOrigin.CREATED
        )
        // Lienzo ancho a propósito: los **cuatro** compases tienen que caber en un solo sistema para
        // que el corchete se vea entero y se pueda medir de un vistazo.
        runDesktopComposeUiTest(width = 1500, height = 400) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(1460.dp)
                            .height(320.dp)
                            .background(androidx.compose.ui.graphics.Color.White)
                    ) {
                        TablatureCanvas(
                            tablature = tablature,
                            scale = 1.0f,
                            overlay = TabCanvasOverlay(loopRange = 1..2)
                        )
                    }
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-loop.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue("no se genero el PNG del bucle", out.exists() && out.length() > 0)
            println("PNG del bucle: ${out.absolutePath} (${out.length()} bytes)")
        }
    }

    /**
     * **Transporte** (B-9): el botón de reproducir existe en el árbol de accesibilidad y **cambia de
     * etiqueta** cuando la partitura está sonando, para que un lector de pantalla anuncie la acción
     * real (`Reproducir` → `Parar`) y no un botón ambiguo.
     */
    @Test
    fun editorShowsTransportButton() {
        fun state(playing: Boolean) = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = tiesTablature().measures,
            selectedSlot = 0 to 0,
            isPlaying = playing
        )
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = state(playing = false), onIntent = {})
                }
            }
            waitForIdle()
            onNodeWithContentDescription("Reproducir la partitura (Espacio)").assertExists()
        }
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = state(playing = true), onIntent = {})
                }
            }
            waitForIdle()
            onNodeWithContentDescription("Parar la reproducción (Espacio)").assertExists()
        }
    }

    /**
     * **Metrónomo y bucle de rango** (D-23): los dos interruptores están en la barra de escritura y
     * cambian de estado. El bucle, además, se anuncia con los compases que abarca.
     */
    @Test
    fun editorShowsMetronomeAndLoopButtons() {
        fun state(metronome: Boolean, loop: IntRange?) = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = tiesTablature().measures,
            selectedSlot = 0 to 0,
            metronomeEnabled = metronome,
            loopRange = loop
        )
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = state(metronome = false, loop = null), onIntent = {})
                }
            }
            waitForIdle()
            onNodeWithContentDescription("Encender el metrónomo").assertExists()
            // (D-89) El botón de **bucle se ha retirado** del cromo: el usuario lo pidió porque «no
            // funciona bien y es complejo». Se comprueba que ya no está, para que nadie lo reponga
            // sin querer.
            onAllNodesWithContentDescription("Repetir los compases seleccionados").assertCountEquals(0)
        }
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = state(metronome = true, loop = 1..3), onIntent = {})
                }
            }
            waitForIdle()
            onNodeWithContentDescription("Metrónomo encendido. Pulsa para apagarlo").assertExists()
            onAllNodesWithContentDescription("Bucle activo en los compases 2 a 4. Pulsa para quitarlo")
                .assertCountEquals(0)
        }
    }

    /**
     * **Mezclador de la reproducción** (D-29): el botón enseña el estado de un vistazo y `M`/`S` están
     * siempre a la vista, sin desplegar nada. Es la diferencia con MuseScore, que lo esconde en un
     * panel (`F10`): aquí el volumen se toca mientras se ensaya.
     */
    @Test
    fun editorShowsMixerControls() {
        fun state(mixer: TabMixer) = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = tiesTablature().measures,
            selectedSlot = 0 to 0,
            mixer = mixer
        )
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = state(TabMixer.DEFAULT), onIntent = {})
                }
            }
            waitForIdle()
            onNodeWithContentDescription("Abrir el mezclador de la reproducción").assertExists()
            onNodeWithContentDescription("Silenciar la reproducción").assertExists()
            onNodeWithContentDescription("Escuchar solo esta pista").assertExists()
        }
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(
                        uiState = state(TabMixer(volume = 0.4f, muted = true, solo = true)),
                        onIntent = {}
                    )
                }
            }
            waitForIdle()
            // Con el silencio puesto, el botón lo dice y el interruptor cambia de rótulo.
            onNodeWithContentDescription("Quitar el silencio").assertExists()
            onNodeWithContentDescription("Quitar el solo").assertExists()
        }
    }

    /**
     * **Propiedades de la nota** (D-32): la pestaña «Nota» del panel de propiedades.
     *
     * Se comprueba lo que resuelve el hueco: con el cursor sobre una nota aparecen su **traste**, su
     * **voz** y su **digitación**, y el traste se puede subir sin borrar la nota. Sin nota en el
     * cursor la sección **no** se pinta (un panel de propiedades vacío es ruido).
     */
    @Test
    fun elPanelDePropiedadesMuestraYEditaLaNotaDelCursor() {
        val base = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = tiesTablature().measures,
            selectedSlot = 0 to 0,
            selectedString = 1
        )
        val intents = mutableListOf<TablatureUiIntent>()
        runDesktopComposeUiTest(width = 1500, height = 4000) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureToolSettings(uiState = base, onIntent = { intents += it })
                }
            }
            waitForIdle()
            // La sección existe y enseña el traste de la nota del cursor.
            onNodeWithText("Nota").assertExists()
            // (D-84) Todos los bloques nacen **plegados**: se abre «Nota» para poder editar la nota.
            onNodeWithContentDescription("Expandir Nota").performClick()
            waitForIdle()
            onNodeWithContentDescription("Subir el traste de la nota").performClick()
            waitForIdle()
            assertTrue(
                "Subir el traste debe emitir SetSlotFret: $intents",
                intents.any { it is TablatureUiIntent.SetSlotFret }
            )
            onNodeWithContentDescription("Poner la nota en la voz 2").performClick()
            waitForIdle()
            assertTrue(
                "La voz debe emitir SetVoiceInRange(2): $intents",
                intents.any { it is TablatureUiIntent.SetVoiceInRange && it.voice == 2 }
            )
        }
        // Sin nota en la casilla del cursor, la sección no se pinta.
        val sinNota = base.copy(
            editorMeasures = tiesTablature().measures.map { it.copy(slots = emptyList()) }
        )
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureToolSettings(uiState = sinNota, onIntent = {})
                }
            }
            waitForIdle()
            onNodeWithText("Nota").assertDoesNotExist()
        }
    }

    /**
     * **Metrónomo configurable** (D-66): el botón de la barra de escritura solo lo enciende; lo que se
     * configura una vez —volumen propio, acento y subdivisión— vive en el panel de herramientas.
     *
     * Se comprueba lo que el usuario pidió: que el metrónomo **se pueda configurar**, no solo encender.
     */
    @Test
    fun elPanelDeHerramientasConfiguraElMetronomo() {
        val base = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = tiesTablature().measures,
            selectedSlot = 0 to 0,
            selectedString = 1,
            metronome = TabMetronome(enabled = true, volume = 0.5f, accentFirst = true, subdivision = 2)
        )
        val intents = mutableListOf<TablatureUiIntent>()
        runDesktopComposeUiTest(width = 1500, height = 4000) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureToolSettings(uiState = base, onIntent = { intents += it })
                }
            }
            waitForIdle()
            // La sección nace plegada, como las paletas: el panel no se llena de ajustes que se tocan
            // una vez.
            onNodeWithText("Metrónomo").performClick()
            waitForIdle()
            onNodeWithText("Suena al reproducir").assertExists()
            onNodeWithText("Volumen del clic").assertExists()
            onNodeWithText("Acentuar el primer tiempo").assertExists()
            onNodeWithText("Subdivisión").assertExists()
            // El valor del volumen se lee en la propia fila.
            onNodeWithText("50 %").assertExists()
            // Y la subdivisión activa es la que dice el modelo.
            onNodeWithText("Corcheas").performClick()
            waitForIdle()
            assertTrue(
                "Elegir corcheas debe emitir SetMetronomeSubdivision(2): $intents",
                intents.any { it is TablatureUiIntent.SetMetronomeSubdivision && it.subdivision == 2 }
            )
            onNodeWithText("Acentuar el primer tiempo").performClick()
            waitForIdle()
            assertTrue(
                "El acento debe emitir ToggleMetronomeAccent: $intents",
                intents.any { it is TablatureUiIntent.ToggleMetronomeAccent }
            )
        }
    }

    /**
     * **Botón «+» de la barra de escritura** (D-31): abre los símbolos de la nota del cursor **sin**
     * salir del modo de entrada.
     *
     * Se comprueba lo que de verdad importa: que sin nota en el cursor el botón está **deshabilitado**
     * (una articulación sin nota no existe) y que con nota se abre el menú y sigue estándolo el modo
     * de entrada, que es justo lo que MuseScore garantiza con su `+`.
     */
    @Test
    fun elBotonMasSimbolosAbreLosSimbolosDeLaNotaSinSalirDelModoDeEntrada() {
        val base = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = tiesTablature().measures,
            selectedSlot = 0 to 0,
            selectedString = 1,
            isInputMode = true
        )
        // Sin nota en la casilla del cursor: el botón no se puede usar.
        val sinNota = base.copy(editorMeasures = tiesTablature().measures.map { it.copy(slots = emptyList()) })
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = sinNota, onIntent = {})
                }
            }
            waitForIdle()
            onNodeWithContentDescription("Más símbolos: coloca el cursor sobre una nota").assertExists()
        }
        // Con nota: el botón se habilita y el menú se abre.
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = base, onIntent = {})
                }
            }
            waitForIdle()
            onNodeWithContentDescription("Más símbolos para la nota del cursor").performClick()
            waitForIdle()
            onNodeWithText("Símbolos de la nota").assertExists()
            onNodeWithText("Ver todas las paletas (F9)").assertExists()
        }
    }

    /**
     * **RePág/AvPág** (D-30): desplazan la **vista** una pantalla sin mover el cursor. Se comprueba
     * midiendo la posición del lienzo en la raíz: si la hoja sube, la vista se ha desplazado.
     */
    @Test
    fun pageDownAndPageUpScrollTheView() {
        // Muchos compases para que la hoja sea más alta que la ventana y haya algo que desplazar.
        val long = tiesTablature().let { tab ->
            tab.copy(measures = List(24) { tab.measures[it % tab.measures.size] })
        }
        val state = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = long.measures,
            selectedSlot = 0 to 0
        )
        runDesktopComposeUiTest(width = 1200, height = 700) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = state, onIntent = {})
                }
            }
            waitForIdle()
            val canvas = onNodeWithTag(TAB_CANVAS_TEST_TAG, useUnmergedTree = true)
            // El lienzo se enfoca al pulsarlo (es lo que hace el usuario antes de usar el teclado).
            // Se mide **después** del clic: enfocar puede desplazar la vista por sí solo, y entonces
            // la medida de antes no serviría de referencia.
            canvas.performClick()
            waitForIdle()
            // Se mide `positionInRoot` y **no** `boundsInRoot`: el lienzo es más alto que la
            // ventana, así que sus límites recortados se quedan pegados al borde superior de la
            // vista y no se moverían aunque la hoja subiera.
            val before = canvas.fetchSemanticsNode().positionInRoot.y
            canvas.performKeyInput { pressKey(Key.PageDown) }
            waitForIdle()
            val afterDown = canvas.fetchSemanticsNode().positionInRoot.y
            assertTrue(
                "AvPág debe subir la hoja: antes $before, después $afterDown",
                afterDown < before
            )
            canvas.performKeyInput { pressKey(Key.PageUp) }
            waitForIdle()
            val afterUp = canvas.fetchSemanticsNode().positionInRoot.y
            assertTrue(
                "RePág debe devolver la hoja hacia abajo: antes $afterDown, después $afterUp",
                afterUp > afterDown
            )
        }
    }

    @Composable
    private fun Preview() {
        LaudateTheme(darkTheme = false) {
            Box(modifier = Modifier.width(820.dp).padding(16.dp).fillMaxWidth()) {
                TablatureView(tablature = symbolCoverageTablature())
            }
        }
    }

    @Test
    fun renderPreviewToPng() {
        runDesktopComposeUiTest(width = 900, height = 900) {
            setContent { Preview() }
            waitForIdle()
            val image = onRoot().captureToImage()
            val bmp = image.toAwtImage()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-preview.png")
            ImageIO.write(bmp, "png", out)
            assertTrue("no se generó el PNG", out.exists() && out.length() > 0)
            println("PNG de la vista previa: ${out.absolutePath} (${out.length()} bytes)")
        }
    }

    /** S1.5: el **mismo pintor** (`TablatureCanvas`) sin capas de interaccion, para comparar con la
     * rejilla del editor (`tablature-editor.png`): al compartir la maquetacion, son identicas.
     *
     * (Punto 0 del usuario) Pinta el **`salmo2.mscz` real**, no un fixture: es el PNG que consume
     * `tools/salmo2_compare.py` para el lado a lado con el PDF.
     *
     * El lienzo mide **2560 dp**: el primer sistema del PDF ocupa 2300 px a 300 dpi con las cuerdas a
     * 31 px, y aquí las cuerdas van a 34 px, así que el ancho equivalente es `2300 · 34/31 ≈ 2523`.
     * Con 1460 dp solo entraban **3 compases** y no se podían comparar los silencios (compases 4 y 5)
     * ni el tresillo.
     *
     * El alto (**2300 dp**) cubre los **cuatro sistemas** del `salmo2` (32 compases). Con 1700 dp
     * entraban los cuatro mientras la separación entre sistemas era de **1,05 espacios**; al subirla
     * al mínimo de MuseScore (8,5 espacios, D-15) el paso de sistema pasa a ~515 px y el cuarto
     * sistema se salía por abajo (`último.topY + último.height ≈ 2072` px). Los compases de
     * silencio de compás miden lo que manda la ley de duración (una redonda, ~195 px), así que el
     * `salmo2` reparte 7 + 12 + 9 + 4 compases (el greedy de MuseScore daría 7 + 12 + 12 + 1, pero el
     * **reparto equilibrado** le pasa tres compases al último sistema para que no quede suelto).
     * `tools/salmo2_compare.py` recorta el primer sistema de este PNG antes de compararlo con el PDF. */
    @Test
    fun renderGridWithoutOverlay() {
        val tablature = salmo2Tablature()
        assumeTrue("falta core/domain/src/jvmTest/resources/salmo2.mscz", tablature != null)
        // **`runSkikoComposeUiTest`, no `runDesktopComposeUiTest`**: el segundo envuelve el bloque en
        // un `runTest` **externo** con el timeout por defecto de 60 s, y su parámetro `testTimeout`
        // solo afecta al `runTest` **interno**, así que un lienzo de 2600×1700 (4,4 Mpx, los cuatro
        // sistemas del `salmo2`) se pasaba de los 60 s y el test fallaba **después** de haber escrito
        // el PNG. El arnés skiko no tiene ese `runTest` externo y sí respeta `testTimeout`.
        //
        // **`scale = 1.6` da la geometría de página de MuseScore**: `staffSpacePt = 20 · 1,6 / 1,5 =
        // 21,33 pt`, y `2560 / 21,33 = 120 espacios` de ancho, que es el A4 de MuseScore (210 mm /
        // 1,75 mm de `spatium`). Con 1,7 la página medía 112,9 espacios y el reparto en líneas no se
        // podía comparar con el del PDF (5 / 11 / 11 / 5).
        runSkikoComposeUiTest(
            size = Size(2600f, 2300f),
            testTimeout = 5.minutes
        ) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(2560.dp)
                            .background(androidx.compose.ui.graphics.Color.White)
                    ) {
                        TablatureCanvas(tablature = tablature!!, scale = 1.6f, numberScale = 1f)
                    }
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-grid.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue("no se genero el PNG de la rejilla", out.exists() && out.length() > 0)
        }
    }

    /** (D-50) El **ancho de la cifra** manda en el espaciado también en el **lienzo real**: con la
     * escala de `TabNotationScale` —la que usan `TablatureCanvas` y el PDF—, una tablatura de
     * semicorcheas con trastes de **dos dígitos** ocupa más que la misma con trastes de **uno**. Antes
     * de D-50 las dos medían exactamente lo mismo (solo duración) y las cifras se pisaban: una
     * semicorchea medía 20,7 px y un «12» necesita 33,8 px. */
    @Test
    fun elAnchoDeLaCifraEnsanchaElSistemaEnElLienzo() {
        fun tablatureOf(fret: Int) = Tablature(
            id = "t",
            songId = "s",
            title = "T",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.SIXTEENTH,
            measures = listOf(
                TabMeasure(
                    slots = List(16) {
                        TabSlot(
                            strings = listOf(TabPluck(1, fret)),
                            rhythmicFigure = RhythmFigure.SIXTEENTH
                        )
                    }
                )
            )
        )
        val settings = TabNotationScale(
            staffSpacePt = 20f / TabRhythmGeometry.STRING_SPACING_SPACES
        ).toLayoutSettings()
        fun widthOf(fret: Int): Float = TablatureSystemsLayout
            .layout(tablatureOf(fret), maxWidth = 100_000f, settings = settings)
            .single()
            .drawing
            .columns.getValue(0)
            .sumOf { it.width.toDouble() }
            .toFloat()
        val uno = widthOf(5)
        val dos = widthOf(12)
        assertTrue(
            "la cifra de dos dígitos ensancha el compás ($dos > $uno)",
            dos > uno * 1.4f
        )
    }

    /**
     * **D-52**: el zoom del número llega al **lienzo**, no solo al pintor.
     *
     * El visor deja agrandar la cifra hasta el 200 % para leer de lejos en el atril. Hasta D-52 ese
     * factor solo lo veía `TabCanvasTextStyles.fret`: la cifra se dibujaba al doble sobre columnas
     * del tamaño normal y los números se pisaban. Se comprueba por la **escala de ajuste al ancho**
     * (`onFitScale`), que sale del ancho natural de la tablatura: si el zoom no llegara a la
     * maquetación, el ancho natural no cambiaría y la escala tampoco.
     */
    @Test
    fun elZoomDelNumeroLlegaAlLienzo() {
        val tablature = Tablature(
            id = "t",
            songId = "s",
            title = "T",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.SIXTEENTH,
            measures = listOf(
                TabMeasure(
                    slots = List(16) {
                        TabSlot(
                            strings = listOf(TabPluck(1, 12)),
                            rhythmicFigure = RhythmFigure.SIXTEENTH
                        )
                    }
                )
            )
        )
        val fits = mutableMapOf<Float, Float>()
        runDesktopComposeUiTest(width = 1200, height = 400) {
            val numberScale = mutableStateOf(1f)
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureCanvas(
                        tablature = tablature,
                        numberScale = numberScale.value,
                        onFitScale = { fits[numberScale.value] = it }
                    )
                }
            }
            waitForIdle()
            numberScale.value = 2f
            waitForIdle()
        }
        val normal = fits[1f]
        val doble = fits[2f]
        assertTrue("el lienzo no comunico la escala normal", normal != null && normal > 0f)
        assertTrue("el lienzo no comunico la escala al 200 %", doble != null && doble > 0f)
        assertTrue(
            "el zoom del numero no llego a la maquetacion: $normal -> $doble",
            doble!! < normal!! * 0.95f
        )
    }

    /**
     * **D-54**: al 200 % las cuerdas se **abren** y las cifras dejan de pisarse, pero el **grabado**
     * (grosores, plicas, barras) no engorda.
     *
     * Se mide sobre el PNG: la distancia entre la 1.ª y la 6.ª cuerda crece, y el grosor de la línea
     * de cuerda se queda igual. Sin `staffSpaceOverride` las dos cosas crecerían juntas.
     */
    @Test
    fun elZoomDelNumeroAbreLasCuerdasSinEngordarElGrabado() {
        val tablature = Tablature(
            id = "t",
            songId = "s",
            title = "T",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.SIXTEENTH,
            measures = listOf(
                TabMeasure(
                    slots = List(16) {
                        TabSlot(
                            // Una sola cuerda: así la línea de cuerda queda **continua** y se puede
                            // medir el paso entre cuerdas por píxeles. El no-solape de las cifras de
                            // cuerdas contiguas se comprueba en `TabSpacingTest`, donde la geometría
                            // se lee sin pasar por el rasterizado.
                            strings = listOf(TabPluck(1, 12)),
                            rhythmicFigure = RhythmFigure.SIXTEENTH
                        )
                    }
                )
            )
        )
        fun medir(zoom: Float): Pair<Int, Int> {
            var png: java.awt.image.BufferedImage? = null
            runDesktopComposeUiTest(width = 1200, height = 500) {
                setContent {
                    LaudateTheme(darkTheme = false) {
                        TablatureCanvas(tablature = tablature, numberScale = zoom)
                    }
                }
                waitForIdle()
                png = onRoot().captureToImage().toAwtImage()
            }
            val bmp = png!!
            // Referencia visual: el PNG se queda en `build/tablature-preview/` para poder mirarlo.
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            ImageIO.write(bmp, "png", File(outDir, "tablature-zoom-${(zoom * 100).toInt()}.png"))
            // El PNG del lienzo lleva **fondo transparente**: hay que componerlo sobre blanco antes
            // de medir tinta, o el alfa 0 se lee como negro y todas las filas parecen cuerdas.
            fun tinta(x: Int, y: Int): Boolean {
                val argb = bmp.getRGB(x, y)
                val a = (argb ushr 24) and 0xFF
                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                val lum = (r * a + 255 * (255 - a)) / 255 * 299 / 1000 +
                    (g * a + 255 * (255 - a)) / 255 * 587 / 1000 +
                    (b * a + 255 * (255 - a)) / 255 * 114 / 1000
                return lum < 200
            }
            // Filas de **cuerda**: las que más tinta tienen de todo el dibujo. No vale exigir un
            // tramo continuo, porque con `BROKEN_ON_MARKS` la línea se corta en cada cifra y con 16
            // cifras por cuerda no queda ningún tramo largo.
            val tintaPorFila = IntArray(bmp.height) { y ->
                (0 until bmp.width).count { x -> tinta(x, y) }
            }
            val maxTinta = tintaPorFila.max()
            val filas = (0 until bmp.height).filter { y -> tintaPorFila[y] > maxTinta * 0.6 }
            assertTrue("no se encontraron cuerdas al ${(zoom * 100).toInt()} %", filas.size >= 6)
            val paso = (filas.last() - filas.first()) / 5
            // Grosor: cuántas filas seguidas tienen tinta de cuerda.
            var grosor = 1
            var mejor = 1
            for (i in 1 until filas.size) {
                if (filas[i] == filas[i - 1] + 1) { grosor++; if (grosor > mejor) mejor = grosor } else grosor = 1
            }
            return paso to mejor
        }
        val (pasoNormal, grosorNormal) = medir(1f)
        val (pasoDoble, grosorDoble) = medir(2f)
        assertTrue(
            "al 200 % las cuerdas no se abren: $pasoNormal -> $pasoDoble",
            pasoDoble > pasoNormal
        )
        assertTrue(
            "el grabado engordo con el zoom: grosor $grosorNormal -> $grosorDoble",
            grosorDoble <= grosorNormal + 1
        )
    }

    @Test
    fun renderEditorToPng() {
        val uiState = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = symbolCoverageTablature().measures,
            selectedString = 2,
            selectedFret = 3,
            selectedSlot = 0 to 1
        )
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = uiState, onIntent = {})
                }
            }
            waitForIdle()
            val image = onRoot().captureToImage()
            val bmp = image.toAwtImage()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-editor.png")
            ImageIO.write(bmp, "png", out)
            assertTrue("no se generó el PNG del editor", out.exists() && out.length() > 0)
            println("PNG del editor: ${out.absolutePath} (${out.length()} bytes)")
        }
    }

    /**
     * **Barra de estado y selector de voces** (B-1 y B-2): comprueba por **semántica** que el editor
     * pinta la descripción del cursor con el vocabulario de MuseScore y las cuatro voces, y que la
     * voz activa es la que se anuncia como activa. Es la verificación de que los controles existen de
     * verdad en el árbol de accesibilidad, no solo en el código.
     */
    @Test
    fun editorShowsStatusBarAndVoiceSelector() {
        val uiState = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = symbolCoverageTablature().measures,
            selectedString = 2,
            selectedFret = 3,
            selectedSlot = 0 to 1,
            activeVoice = 2
        )
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = uiState, onIntent = {})
                }
            }
            waitForIdle()
            // La barra de estado describe el cursor con los campos de MuseScore.
            val status = uiState.statusBarText
            assertTrue("la barra de estado no está vacía: $status", status.contains("Cuerda:"))
            onNodeWithContentDescription("Barra de estado: $status").assertExists()
            // Las cuatro voces están, y la 2 se anuncia como la activa.
            onNodeWithContentDescription("Voz 2 activa").assertExists()
            onNodeWithContentDescription("Escribir en la voz 1").assertExists()
            onNodeWithContentDescription("Escribir en la voz 3").assertExists()
            onNodeWithContentDescription("Escribir en la voz 4").assertExists()
        }
    }

    /**
     * **Zoom de la barra de estado** (D-14): el porcentaje es un **desplegable** con los presets de
     * MuseScore, no un texto de solo lectura. Antes se podía cambiar el zoom con `Ctrl`+rueda o con el
     * pellizco, pero no **elegir un valor** ni volver al 100 % sin adivinar.
     */
    @Test
    fun elZoomSeEligeDesdeLaBarraDeEstado() {
        val uiState = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = symbolCoverageTablature().measures,
            tablatureScale = 1f
        )
        val intents = mutableListOf<TablatureUiIntent>()
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = uiState, onIntent = { intents += it })
                }
            }
            waitForIdle()
            onNodeWithContentDescription("Zoom: 100 por ciento").assertExists()
            onNodeWithContentDescription("Zoom: 100 por ciento").performClick()
            waitForIdle()
            // Los presets de MuseScore están todos, y el vigente va marcado.
            onNodeWithText("✓ 100 %").assertExists()
            onNodeWithText("25 %").assertExists()
            onNodeWithText("400 %").assertExists()
            onNodeWithText("50 %").performClick()
            waitForIdle()
            assertEquals(
                "elegir un preset emite el zoom",
                TablatureUiIntent.SetTablatureScale(0.5f),
                intents.last()
            )
        }
    }

    /**
     * **Ajustar al ancho** (D-14): el lienzo mide su ancho real y ofrece la escala que llena el ancho
     * imprimible. Es el comando de MuseScore que evita adivinar el porcentaje.
     */
    @Test
    fun ajustarAlAnchoEmiteLaEscalaQueLlenaElLienzo() {
        val uiState = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = symbolCoverageTablature().measures,
            tablatureScale = 1f
        )
        val intents = mutableListOf<TablatureUiIntent>()
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = uiState, onIntent = { intents += it })
                }
            }
            waitForIdle()
            onNodeWithContentDescription("Zoom: 100 por ciento").performClick()
            waitForIdle()
            onNodeWithText("Ajustar al ancho").performClick()
            waitForIdle()
            val scale = (intents.last() as TablatureUiIntent.SetTablatureScale).scale
            assertTrue("la escala ajustada es positiva: $scale", scale > 0f)
            assertTrue("y está dentro del rango de MuseScore: $scale", scale in 0.25f..4f)
        }
    }

    /**
     * **Propiedades del pentagrama** (D-45): el `Staff/Part properties` de MuseScore. Hasta D-45 los
     * doce ajustes de `TabStaffOptions` **no tenían ninguna interfaz** —`SetStaffOptions` existía en el
     * contrato pero nadie lo despachaba—, así que el usuario solo podía usar los valores por defecto.
     * El menú «Pentagrama» del cromo abre el diálogo y cada control emite el estado **completo**.
     */
    @Test
    fun elMenuDelPentagramaAbreSusPropiedadesYEmiteElAjuste() {
        val uiState = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = symbolCoverageTablature().measures,
            tablatureScale = 1f
        )
        val intents = mutableListOf<TablatureUiIntent>()
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = uiState, onIntent = { intents += it })
                }
            }
            waitForIdle()
            onNodeWithContentDescription(
                "Propiedades del pentagrama: cifras, ritmo, líneas y cabecera"
            ).performClick()
            waitForIdle()
            // (D-46) Los **tres presets** de MuseScore (`stafftype.cpp:946-967`) están en el menú.
            // El de `Common` sale marcado con «✓  » porque es el ajuste por defecto, así que se busca
            // por subcadena.
            onNodeWithText("Simple (solo cifras)").assertExists()
            onNodeWithText("Common (cifras, silencios y plicas)", substring = true).assertExists()
            onNodeWithText("Full (blanca rayada)").assertExists()
            onNodeWithText("Propiedades del pentagrama…").performClick()
            waitForIdle()
            // El diálogo aparece con su título y los cuatro modos del símbolo de duración.
            onNodeWithText("Propiedades del pentagrama").assertExists()
            onNodeWithText("Nunca").assertExists()
            onNodeWithText("Al abrir sistema").assertExists()
            onNodeWithText("Al abrir compás").assertExists()
            onNodeWithText("Siempre").assertExists()
            // Elegir «Siempre» emite el estado completo con `rhythmRepeat = ALWAYS`.
            onNodeWithText("Siempre").performClick()
            waitForIdle()
            val emitido = intents.filterIsInstance<TablatureUiIntent.SetStaffOptions>().last()
            assertEquals(
                "el modo del símbolo pasa a ALWAYS",
                RhythmRepeat.ALWAYS,
                emitido.options.rhythmRepeat
            )
            assertEquals(
                "y el resto de ajustes se conserva",
                uiState.editorStaffOptions.copy(rhythmRepeat = RhythmRepeat.ALWAYS),
                emitido.options
            )
            // (D-46) El mismo panel elige **cómo se dibuja la blanca** (`TablatureMinimStyle`).
            onNodeWithText("Rayada").performClick()
            waitForIdle()
            val conRayada = intents.filterIsInstance<TablatureUiIntent.SetStaffOptions>().last()
            assertEquals(
                "la blanca pasa a rayada",
                MinimStyle.SLASHED,
                conRayada.options.minimStyle
            )
            // El arnés no recompone con el estado emitido (el `uiState` es fijo), así que el resto de
            // ajustes sale del estado de partida: lo que importa es que **solo** cambie `minimStyle`.
            assertEquals(
                "y el resto de ajustes se conserva",
                uiState.editorStaffOptions.copy(minimStyle = MinimStyle.SLASHED),
                conRayada.options
            )
            // (D-47) La cifra de la nota ligada: `ShowTiedFret` (`stafftype.h:116-120`) y
            // `ParenthesizeTiedFret` (`stafftype.h:122-126`), con sus valores por defecto
            // (`styledef.cpp:2096-2097`).
            onNodeWithText("Cifra de la nota ligada").assertExists()
            onNodeWithText("Cifra y ligadura").assertExists()
            onNodeWithText("Solo la ligadura").performClick()
            waitForIdle()
            val soloLigadura = intents.filterIsInstance<TablatureUiIntent.SetStaffOptions>().last()
            assertEquals(
                "la cifra ligada pasa a ocultarse",
                ShowTiedFret.TIE,
                soloLigadura.options.showTiedFret
            )
            assertEquals(
                "y el resto de ajustes se conserva",
                uiState.editorStaffOptions.copy(showTiedFret = ShowTiedFret.TIE),
                soloLigadura.options
            )
            onNodeWithText("Dónde se recuerda").assertExists()
            onNodeWithText("Al abrir el sistema").assertExists()
            onNodeWithText("Al abrir el compás").performClick()
            waitForIdle()
            val alAbrirCompas = intents.filterIsInstance<TablatureUiIntent.SetStaffOptions>().last()
            assertEquals(
                "la cifra recordada se parentiza al abrir compás",
                ParenthesizeTiedFret.START_OF_MEASURE,
                alAbrirCompas.options.parenthesizeTiedFret
            )
            assertEquals(
                "y el resto de ajustes se conserva",
                uiState.editorStaffOptions.copy(
                    parenthesizeTiedFret = ParenthesizeTiedFret.START_OF_MEASURE
                ),
                alAbrirCompas.options
            )
        }
    }

    /**
     * (D-48) El diálogo del pentagrama **arranca de los ajustes guardados**, no de los valores por
     * defecto: la preferencia es global y el usuario no debe reconfigurarla en cada canto. Se parte
     * de un estado con ajustes no triviales y se comprueba que al tocar **un** control se emite el
     * estado completo con los demás ajustes **intactos**.
     */
    @Test
    fun elDialogoDelPentagramaArrancaDeLosAjustesGuardados() {
        val guardados = TabStaffOptions(
            fretMarks = FretMarkStyle.LETTERS,
            minimStyle = MinimStyle.SLASHED,
            showTiedFret = ShowTiedFret.TIE,
            parenthesizeTiedFret = ParenthesizeTiedFret.NEVER,
            showClef = false,
            showMeasureNumbers = false
        )
        val uiState = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = symbolCoverageTablature().measures,
            tablatureScale = 1f,
            editorStaffOptions = guardados
        )
        val intents = mutableListOf<TablatureUiIntent>()
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = uiState, onIntent = { intents += it })
                }
            }
            waitForIdle()
            onNodeWithContentDescription(
                "Propiedades del pentagrama: cifras, ritmo, líneas y cabecera"
            ).performClick()
            waitForIdle()
            onNodeWithText("Propiedades del pentagrama…").performClick()
            waitForIdle()
            // Los chips de los ajustes guardados están ahí (el de letras solo aparece con LETTERS).
            onNodeWithText("Letras").assertExists()
            onNodeWithText("Rayada").assertExists()
            onNodeWithText("No se recuerda").assertExists()
            // Al cambiar **solo** el símbolo de duración, el resto de lo guardado se conserva.
            onNodeWithText("Siempre").performClick()
            waitForIdle()
            val emitido = intents.filterIsInstance<TablatureUiIntent.SetStaffOptions>().last()
            assertEquals(
                "el estado emitido parte de los ajustes guardados",
                guardados.copy(rhythmRepeat = RhythmRepeat.ALWAYS),
                emitido.options
            )
        }
    }

    /**
     * **Datos de cuerda** (D-49): el `String data` de MuseScore (`editstringdata.ui`). Hasta D-49 la
     * afinación era de solo lectura: no se podía añadir una cuerda, cambiar su altura ni decir que
     * una cuerda no pasa por el mástil. El menú «Pentagrama» abre el diálogo y cada control emite su
     * intent.
     */
    @Test
    fun elMenuDelPentagramaAbreLosDatosDeCuerdaYEmiteSusAjustes() {
        val uiState = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = symbolCoverageTablature().measures,
            tablatureScale = 1f
        )
        val intents = mutableListOf<TablatureUiIntent>()
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = uiState, onIntent = { intents += it })
                }
            }
            waitForIdle()
            onNodeWithContentDescription(
                "Propiedades del pentagrama: cifras, ritmo, líneas y cabecera"
            ).performClick()
            waitForIdle()
            onNodeWithText("Datos de cuerda…").performClick()
            waitForIdle()
            assertEquals(
                "el menú pide abrir el diálogo",
                TablatureUiIntent.OpenStringDataDialog,
                intents.filterIsInstance<TablatureUiIntent.OpenStringDataDialog>().last()
            )
        }
    }

    /**
     * El diálogo de **Datos de cuerda** (D-49) pinta la tabla de MuseScore —«Siempre al aire» y
     * «Altura» por cuerda, de la 1.ª a la última— y sus tres acciones: nueva cuerda, quitar cuerda y
     * el número de trastes del mástil.
     */
    @Test
    fun elDialogoDeDatosDeCuerdaPintaLaTablaYEmiteSusAcciones() {
        val uiState = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = symbolCoverageTablature().measures,
            showStringDataDialog = true,
            tablatureScale = 1f
        )
        val intents = mutableListOf<TablatureUiIntent>()
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(uiState = uiState, onIntent = { intents += it })
                }
            }
            waitForIdle()
            onNodeWithText("Datos de cuerda").assertExists()
            onNodeWithText("Siempre al aire").assertExists()
            // La columna «Altura» se comprueba por su contenido: la 1.ª cuerda de la guitarra es mi4,
            // en minúscula como `pitch2string` de MuseScore. (Buscar la palabra «Altura» colisiona
            // con la paleta del mismo nombre.)
            onNodeWithText("e4").assertExists()
            onNodeWithText("Número de trastes").assertExists()
            onNodeWithText("Nueva cuerda…").assertExists()
            onNodeWithText("Quitar cuerda").assertExists()
            // La guitarra tiene 19 trastes: el `+` pide uno más.
            onNodeWithContentDescription("Un traste más").performClick()
            waitForIdle()
            assertEquals(
                "el `+` sube el mástil un traste",
                TablatureUiIntent.SetTuningFrets(Tuning.GUITAR.frets + 1),
                intents.filterIsInstance<TablatureUiIntent.SetTuningFrets>().last()
            )
            // La 1.ª fila de la tabla es la 1.ª cuerda (la más aguda): marcarla «siempre al aire».
            onAllNodes(isToggleable())[0].performClick()
            waitForIdle()
            assertEquals(
                "la casilla marca la 1.ª cuerda",
                TablatureUiIntent.SetStringAlwaysOpen(1, true),
                intents.filterIsInstance<TablatureUiIntent.SetStringAlwaysOpen>().last()
            )
            // Y el `+` de altura de esa misma cuerda sube medio tono.
            onNodeWithContentDescription("Subir medio tono la cuerda 1").performClick()
            waitForIdle()
            val altura = intents.filterIsInstance<TablatureUiIntent.SetStringPitch>().last()
            assertEquals("la cuerda es la 1.ª", 1, altura.stringNumber)
            assertEquals(
                "y sube un semitono desde Mi4",
                ScorePitch.fromMidi(Tuning.GUITAR.strings.last().pitch.midi + 1),
                altura.pitch
            )
        }
    }

    /**
     * **Un solo panel** (D-58): las paletas viven **dentro del panel de herramientas**, encima de los
     * ajustes, y se ocultan con `F9`; las propiedades con `F8` (B-14). Se comprueba que el editor ya
     * **no** tiene un panel de paletas propio, que las paletas están en el panel de herramientas y
     * que el botón del cromo las anuncia.
     */
    @Test
    fun editorShowsPalettesInsideTheToolPanelAndPanelToggles() {
        val uiState = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = symbolCoverageTablature().measures,
            selectedString = 2,
            selectedFret = 3,
            selectedSlot = 0 to 1
        )
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    // La disposición real: el editor ocupa el contenido y el panel de herramientas
                    // va a la derecha, con el ancho que tiene en la aplicación.
                    androidx.compose.foundation.layout.Row {
                        Box(modifier = Modifier.weight(1f)) {
                            TablatureEditorPanel(uiState = uiState, onIntent = {})
                        }
                        Box(modifier = Modifier.width(320.dp)) {
                            TablatureToolSettings(uiState = uiState, onIntent = {})
                        }
                    }
                }
            }
            waitForIdle()
            // El editor ya **no** tiene un panel de paletas propio: no queda ninguna cabecera
            // «Paletas» suelta en la pantalla.
            onAllNodesWithContentDescription("Paletas").assertCountEquals(0)
            // Las paletas están en el panel de herramientas, encima de los ajustes.
            // (D-82) «Alteraciones» ya **no** está: en una tablatura numérica el traste y la
            // afinación determinan la nota, así que una alteración delante de la cifra era
            // redundante. Se comprueba que la paleta desapareció y que las demás siguen.
            onAllNodesWithText("Alteraciones").assertCountEquals(0)
            onNodeWithText("Dinámicas").assertExists()
            // Un solo disparador de `F9`: el botón del cromo (el panel ya no tiene botón de cierre).
            onAllNodesWithContentDescription("Ocultar las paletas (F9)").assertCountEquals(1)
            // Y el de propiedades (`F8`).
            onNodeWithContentDescription("Ocultar las propiedades (F8)").assertExists()
        }
    }

    /**
     * **Las celdas de una paleta son uniformes y de tamaño único en todo el panel** (D-77, D-81).
     *
     * Es la regresión de «simbolitos de mierda en cajas enormes descuadradas». Primero cada celda era
     * un cuadrado de 44 dp con `defaultMinSize`, así que el ancho lo decidía el texto del símbolo y en
     * una misma fila convivían celdas de 44, 61 y 86 dp —y una ligadura salía en una caja de 44×91 dp
     * con un glifo de 8×9 px dentro—. Después se repartió el ancho **de cada fila** entre sus celdas,
     * y el resultado fue otro descuadre: las paletas de 13 símbolos daban celdas de 60 dp y las de 6
     * daban de 50 dp, así que las columnas de una paleta no caían donde las de la siguiente.
     *
     * (D-81) Ahora hay **una sola medida** para todo el panel y la celda es un **cuadrado**: se
     * comprueba de ancho, de alto, cuadrada, alineada por la izquierda y con columnas equidistantes.
     *
     * Se renderiza **solo el panel**, a 320 dp de ancho y 4000 dp de alto, para que ninguna paleta
     * quede recortada por el scroll: un nodo recortado devuelve límites sujetos al viewport y dos
     * paletas distintas acabarían compartiendo `top`.
     */
    @Test
    fun lasCeldasDeUnaPaletaSonUniformesYLlenanElPanel() {
        val uiState = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = symbolCoverageTablature().measures,
            selectedString = 2,
            selectedFret = 3,
            // La columna **tiene** que tener nota: si no, `canEditTechnique` es `false` y todas las
            // paletas salen deshabilitadas, que es lo que hacia parecer «lavados» los glifos.
            selectedSlot = 0 to 0
        )
        runDesktopComposeUiTest(width = 320, height = 4000) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TablatureToolSettings(uiState = uiState, onIntent = {})
                    }
                }
            }
            waitForIdle()
            // Se abren todas las paletas para poder medir sus celdas. (D-79) Ya vienen abiertas: solo
            // se pulsa la cabecera si sigue plegada, porque pulsarla la **cerraría**.
            listOf(
                "Dinámicas", "Reguladores", "Adornos", "Articulaciones",
                "Ligaduras", "Técnicas", "Trémolos", "Digitación", "Repeticiones",
                "Barras de compás", "Indicaciones de compás", "Tempo", "Repeticiones y saltos"
            ).forEach { titulo ->
                val plegada = onAllNodesWithContentDescription("Expandir $titulo")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
                if (plegada) {
                    runCatching { onNodeWithContentDescription("Expandir $titulo").performClick() }
                    // (D-84) Con las paletas plegadas por defecto hace falta dejar asentar la
                    // animación de despliegue antes de pulsar la siguiente cabecera: si no, la
                    // lista se mueve bajo el dedo y `performClick` falla o pulsa otra.
                    waitForIdle()
                    mainClock.advanceTimeBy(400)
                    waitForIdle()
                }
            }
            waitForIdle()
            // (D-79) Se filtran por la **etiqueta de celda de paleta**, no por «tiene descripción
            // accesible y mide entre 40 y 90 dp»: con las paletas desplegadas ese filtro amplio
            // metía en el saco botones de 40 dp que no son celdas y la comprobación de altura
            // fallaba con [48, 40].
            val celdas = onAllNodesWithTag(TAB_PALETTE_CELL_TEST_TAG)
                .fetchSemanticsNodes()
                .map { it.boundsInRoot }
            assertTrue("el panel no expone celdas de paleta", celdas.size > 20)
            // (D-81) **Una sola medida para todas las celdas de todas las paletas**, y cuadradas.
            // El usuario pidió literalmente «toda la superficie ocupada por cajas de igual tamaño».
            // Antes cada fila repartía *su* ancho entre *su* número de celdas: una paleta de 13 daba
            // celdas de 60 dp y otra de 6 daba celdas de 50 dp, así que las columnas de una paleta no
            // caían donde las de la siguiente.
            val anchos = celdas.map { it.width }.distinct()
            val altos = celdas.map { it.height }.distinct()
            assertEquals("todas las celdas tienen que medir lo mismo de ancho: $anchos", 1, anchos.size)
            assertEquals("todas las celdas tienen que medir lo mismo de alto: $altos", 1, altos.size)
            assertEquals(
                "la celda de paleta tiene que ser un CUADRADO: ${anchos.first()} x ${altos.first()}",
                anchos.first(),
                altos.first(),
                1f
            )
            // Todas las filas arrancan en el mismo borde izquierdo, así que las columnas se alinean
            // verticalmente entre paletas distintas. **Por fila**: celdas de columnas distintas tienen,
            // por definición, `left` distintos.
            val izquierdas = celdas.groupBy { it.top }.map { (_, deLaFila) -> deLaFila.minOf { c -> c.left } }
            val desalineado = izquierdas.max() - izquierdas.min()
            assertTrue(
                "todas las filas tienen que arrancar en el mismo borde izquierdo (desviación ${desalineado}px): $izquierdas",
                desalineado <= 1f
            )
            // Y las columnas caen en el mismo sitio: el conjunto de `x` es el de una rejilla regular.
            val columnas = celdas.map { it.left }.distinct().sorted()
            val pasos = columnas.zipWithNext { a, b -> b - a }
            assertTrue(
                "las columnas tienen que ser equidistantes; pasos medidos: $pasos",
                pasos.all { kotlin.math.abs(it - pasos.first()) <= 1f }
            )
        }
    }

    /**
     * **El símbolo cabe dentro de su caja y la ocupa casi entera** (D-81).
     *
     * Es la regresión del defecto que el usuario describió como «iconos gigantes y cortados» y, a la
     * vez, «el icono ocupando el máximo posible de la caja sin salirse y centrado».
     *
     * El fallo estaba en el cálculo del cuerpo: para los glifos **sin caja de tinta tabulada** se
     * asumía `FALLBACK_INK` = 0,5 em, así que el cuerpo salía `alto / 0,5` = **el doble** del alto de
     * la celda; con una tinta real de 0,7 em el símbolo acababa en 1,4 veces la celda y el borde del
     * panel lo recortaba. Aquí se comprueba sobre el **render real**: se captura cada celda a imagen y
     * se mide la **tinta** (píxeles distintos del fondo blanco de la celda), exigiendo que
     *   - no toque el borde de la celda (nada de símbolos cortados), y
     *   - ocupe al menos el **35 %** del lado, para que no vuelvan los «simbolitos de mierda».
     *
     * El 35 % es deliberadamente laxo: los símbolos planos de la paleta (el guion de «sin alteración»,
     * los puntos de articulación) ocupan poco por naturaleza y no son un defecto. La cota superior es
     * la que importa y es estricta: **ni un píxel en el marco de 2 px del borde**.
     */
    @Test
    fun elSimboloDeCadaCeldaCabeEnSuCaja() {
        val uiState = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = symbolCoverageTablature().measures,
            selectedString = 2,
            selectedFret = 3,
            selectedSlot = 0 to 0
        )
        runDesktopComposeUiTest(width = 320, height = 4000) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TablatureToolSettings(uiState = uiState, onIntent = {})
                    }
                }
            }
            waitForIdle()
            // (D-84) Las paletas nacen **plegadas**: se abren todas para poder medir sus celdas.
            listOf(
                "Dinámicas", "Reguladores", "Adornos", "Articulaciones",
                "Ligaduras", "Técnicas", "Trémolos", "Digitación", "Repeticiones",
                "Barras de compás", "Indicaciones de compás", "Tempo", "Repeticiones y saltos"
            ).forEach { titulo ->
                if (onAllNodesWithContentDescription("Expandir $titulo").fetchSemanticsNodes().isNotEmpty()) {
                    runCatching { onNodeWithContentDescription("Expandir $titulo").performClick() }
                    waitForIdle()
                    mainClock.advanceTimeBy(400)
                    waitForIdle()
                }
            }
            val celdas = onAllNodesWithTag(TAB_PALETTE_CELL_TEST_TAG).fetchSemanticsNodes()
            assertTrue("el panel no expone celdas de paleta", celdas.size > 20)
            // Se muestrea una celda de cada paleta visible: medir las 100+ celdas haría el test lento
            // sin aportar nada, porque el defecto era **sistemático** (afectaba a todo glifo que no
            // estuviera en la tabla de tinta).
            val imagen = onRoot().captureToImage().toAwtImage()
            // Se vuelca el panel completo para poder inspeccionarlo a ojo: es la única forma de
            // comprobar la maquetación de las paletas sin abrir la app.
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            ImageIO.write(imagen, "png", File(outDir, "tablature-palette-cells.png"))
            var medidas = 0
            var recortadas = 0
            val pequenas = mutableListOf<String>()
            val ratios = mutableListOf<Pair<Float, Float>>()
            celdas.forEachIndexed { indice, nodo ->
                if (indice % 3 != 0) return@forEachIndexed
                val b = nodo.boundsInRoot
                // (D-81) Se muestrea **todo el interior real** de la celda: se descarta solo el píxel
                // del borde dibujado (`1 px`) y su antialiasing inmediato, es decir 2 px por lado. La
                // ventana anterior de 3 px era más estrecha que el propio símbolo (que ocupa el ~90 %
                // del lado, que es justo lo que se pide), así que marcaba como «cortado» cualquier
                // glifo bien dimensionado.
                val x0 = b.left.toInt() + 2
                val y0 = b.top.toInt() + 2
                val x1 = (b.right.toInt() - 2).coerceAtMost(imagen.width - 1)
                val y1 = (b.bottom.toInt() - 2).coerceAtMost(imagen.height - 1)
                if (x1 <= x0 || y1 <= y0) return@forEachIndexed
                // (D-81) El fondo de la celda **no siempre es blanco**: la celda activa va teñida de
                // `primaryContainer` y las deshabilitadas llevan un gris translúcido. Se toma el color
                // **modal** del área interior como referencia de fondo (no la esquina, que cae sobre el
                // arco del radio de 2 dp y está mezclada con el antialiasing).
                val frecuencias = HashMap<Int, Int>()
                for (y in y0..y1) {
                    for (x in x0..x1) {
                        val rgb = imagen.getRGB(x, y) and 0xFFFFFF
                        frecuencias[rgb] = (frecuencias[rgb] ?: 0) + 1
                    }
                }
                val fondo = frecuencias.maxByOrNull { it.value }?.key ?: 0xFFFFFF
                val fr = (fondo shr 16) and 0xFF
                val fg = (fondo shr 8) and 0xFF
                val fb = fondo and 0xFF
                var minX = Int.MAX_VALUE
                var maxX = Int.MIN_VALUE
                var minY = Int.MAX_VALUE
                var maxY = Int.MIN_VALUE
                for (y in y0..y1) {
                    for (x in x0..x1) {
                        val rgb = imagen.getRGB(x, y) and 0xFFFFFF
                        val r = (rgb shr 16) and 0xFF
                        val g = (rgb shr 8) and 0xFF
                        val bl = rgb and 0xFF
                        // Tinta = todo lo que se separe del fondo de la celda.
                        val distancia = kotlin.math.abs(r - fr) + kotlin.math.abs(g - fg) + kotlin.math.abs(bl - fb)
                        if (distancia > 90) {
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        }
                    }
                }
                medidas++
                if (minX == Int.MAX_VALUE) return@forEachIndexed
                // **Cortada**: la tinta toca el borde dibujado de la celda. Se exige ≥ 2 px de aire
                // respecto a los límites de la celda, que es el margen del marco.
                val margen = 2
                val tocaBorde = minX < b.left.toInt() + margen || maxX > b.right.toInt() - margen ||
                    minY < b.top.toInt() + margen || maxY > b.bottom.toInt() - margen
                val lado = (b.right - b.left)
                val anchoTinta = (maxX - minX + 1) / lado
                val altoTinta = (maxY - minY + 1) / lado
                ratios += anchoTinta to altoTinta
                if (tocaBorde) {
                    recortadas++
                    println("celda cortada: ${nodo.config} en $b tinta ${anchoTinta}x${altoTinta}")
                }
                // (D-81) Umbral del **12 %**: hay marcas que por naturaleza ocupan poquísimo —el picado
                // es un punto, el portato una línea corta con un punto, el grupeto un glifo bajo—, y
                // en MuseScore también se ven pequeñas dentro de su caja. El defecto que hay que
                // detectar no es «ocupa poco» sino «se dibujó a 1/20 del lado», que es lo que pasaba
                // cuando el glifo no encontraba la fuente y caía a la de respaldo (0,02 = 1 px).
                if (anchoTinta < MIN_INK_RATIO && altoTinta < MIN_INK_RATIO) {
                    pequenas += "${nodo.config} w=$anchoTinta h=$altoTinta"
                }
            }
            assertTrue("no se midió ninguna celda", medidas > 10)
            if (ratios.isNotEmpty()) {
                println(
                    "tinta medida (fracción del lado de la celda): " +
                        "ancho ${ratios.minOf { it.first }}..${ratios.maxOf { it.first }} · " +
                        "alto ${ratios.minOf { it.second }}..${ratios.maxOf { it.second }}"
                )
            }
            assertEquals(
                "hay $recortadas celdas con la tinta tocando el marco (símbolo cortado)",
                0,
                recortadas
            )
            assertTrue(
                "hay símbolos por debajo del ${(MIN_INK_RATIO * 100).toInt()} % del lado de la celda " +
                    "(vuelven los «simbolitos»): $pequenas",
                pequenas.isEmpty()
            )
        }
    }

    /**
     * El lienzo **instala** su resolvedor de celdas (B-13), que es lo que permite soltar una celda de
     * paleta sobre una nota concreta. Se barre una rejilla de puntos y se exige que al menos uno
     * resuelva a una celda con cuerda: si el resolvedor no se instalara, todos serían `null`.
     */
    @Test
    fun canvasInstallsHitTesterForPaletteDrops() {
        val tablature = symbolCoverageTablature()
        val hitTester = TabCanvasHitTester()
        runDesktopComposeUiTest(width = 1200, height = 800) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureCanvas(tablature = tablature, hitTester = hitTester)
                }
            }
            waitForIdle()
            val hits = buildList {
                for (x in 20..1100 step 20) {
                    for (y in 20..700 step 10) {
                        hitTester(Offset(x.toFloat(), y.toFloat()))?.let(::add)
                    }
                }
            }
            assertTrue("el lienzo no instaló el resolvedor de celdas", hits.isNotEmpty())
            assertTrue(
                "ninguna posición resolvió a una cuerda concreta: ${hits.take(5)}",
                hits.any { it.stringNumber > 0 }
            )
        }
    }

    /**
     * Renderiza **solo el panel de paletas** del editor (B-12), para poder compararlo a ojo con el
     * panel de paletas de MuseScore Studio 4. Es una prueba visual: captura la **paleta**, no la
     * rejilla.
     */
    @Test
    fun renderEditorOptionsPanelToPng() {
        val uiState = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = symbolCoverageTablature().measures,
            selectedString = 2,
            selectedFret = 3,
            selectedSlot = 0 to 1
        )
        runDesktopComposeUiTest(width = 380, height = 1000) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    com.grafitto.laudate.ui.tablature.TablatureMarksPalette(
                        uiState = uiState,
                        onIntent = {}
                    )
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-palette.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue("no se generó el PNG de la paleta", out.exists() && out.length() > 0)
            println("PNG de la paleta: ${out.absolutePath} (${out.length()} bytes)")
        }
    }
    /**
     * **Rectángulo de selección** (*rubber band*): arrastrar sobre un **hueco vacío** del lienzo
     * selecciona las notas que toca, como en MuseScore. Es el gesto que faltaba del punto 5 de la
     * lista de paridad; aquí se comprueba **de extremo a extremo** — ratón sobre el lienzo real,
     * `detectDragGestures`, resolución contra la geometría y emisión de la selección.
     *
     * Las coordenadas **no se adivinan**: se localizan con el [TabCanvasHitTester] del propio lienzo,
     * que es la misma función que usa el editor para saber qué hay bajo el puntero.
     */
    @Test
    fun arrastrarSobreUnHuecoVacioSeleccionaLasNotasQueToca() {
        val tablature = tiesTablature()
        var seleccion: com.grafitto.laudate.domain.tablature.TabNoteSelection? = null
        val tester = TabCanvasHitTester()
        runDesktopComposeUiTest(width = 1200, height = 500) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(modifier = Modifier.width(1200.dp).background(Color.White)) {
                        TablatureCanvas(
                            tablature = tablature,
                            scale = 1.0f,
                            numberScale = 1f,
                            onHit = { _, _ -> },
                            hitTester = tester,
                            onRubberBand = { seleccion = it }
                        )
                    }
                }
            }
            waitForIdle()
            // Hueco vacío = a la **izquierda** del primer compás, dentro de la franja del sistema
            // (`hitTest` devuelve `null` porque no hay ninguna caja de compás que contenga la x).
            var dentro: Float? = null
            for (y in 0 until 500) {
                if (tester(Offset(600f, y.toFloat())) != null) { dentro = y.toFloat(); break }
            }
            assertTrue("tiene que haber un sistema bajo el puntero", dentro != null)
            var vacio: Offset? = null
            for (x in 0 until 600) {
                val p = Offset(x.toFloat(), dentro!!)
                if (tester(p) == null) { vacio = p; break }
            }
            assertTrue("tiene que haber un hueco vacío a la izquierda del primer compás", vacio != null)
            // Y una cifra real, para saber hasta dónde arrastrar.
            var cifra: Offset? = null
            for (y in 0 until 500) {
                val p = Offset(200f, y.toFloat())
                val hit = tester(p)
                if (hit != null && hit.stringNumber > 0) { cifra = p; break }
            }
            assertTrue("tiene que haber una cifra que arrastrar", cifra != null)
            onRoot().performMouseInput {
                moveTo(vacio!!)
                press()
                moveTo(Offset(600f, vacio.y))
                moveTo(Offset(1100f, cifra!!.y + 40f))
                release()
            }
            waitForIdle()
        }
        val s = seleccion
        assertTrue("el arrastre sobre un hueco vacío tiene que emitir una selección", s != null)
        assertTrue("y la selección no puede quedar vacía", s!!.notes.isNotEmpty())
        println("rubber band: ${s.size} notas seleccionadas")
    }

    /**
     * Arrastrar **sobre una cifra** sigue moviendo la nota de cuerda y **no** abre el rectángulo: los
     * dos gestos son excluyentes y los decide el punto donde empieza el arrastre.
     */
    @Test
    fun arrastrarSobreUnaCifraMueveLaCuerdaYNoAbreElRectangulo() {
        val tablature = tiesTablature()
        var seleccion: com.grafitto.laudate.domain.tablature.TabNoteSelection? = null
        var movida: Pair<com.grafitto.laudate.ui.tablature.TabCanvasHit, Int>? = null
        val tester = TabCanvasHitTester()
        runDesktopComposeUiTest(width = 1200, height = 500) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(modifier = Modifier.width(1200.dp).background(Color.White)) {
                        TablatureCanvas(
                            tablature = tablature,
                            scale = 1.0f,
                            numberScale = 1f,
                            onHit = { _, _ -> },
                            hitTester = tester,
                            onDragStrings = { hit, delta -> movida = hit to delta },
                            onRubberBand = { seleccion = it }
                        )
                    }
                }
            }
            waitForIdle()
            // Una cifra de la **cuerda 1** (la de arriba), para poder arrastrarla hacia abajo.
            var cifra: Offset? = null
            for (y in 0 until 500) {
                val p = Offset(200f, y.toFloat())
                if (tester(p)?.stringNumber == 1) { cifra = p; break }
            }
            assertTrue("tiene que haber una cifra en la cuerda 1", cifra != null)
            onRoot().performMouseInput {
                moveTo(cifra!!)
                press()
                moveTo(Offset(cifra.x, cifra.y + 20f))
                moveTo(Offset(cifra.x, cifra.y + 40f))
                release()
            }
            waitForIdle()
        }
        assertTrue("arrastrar sobre una cifra no abre el rectángulo", seleccion == null)
        assertTrue("y sí mueve la nota de cuerda", movida != null)
        println("arrastre de cuerda: $movida")
    }

    /**
     * **Un clic simple mueve el cursor** (D-78). Es la regresion de la queja «la pulsacion no hace
     * nada»: se podia escribir en la primera casilla —la que deja el cursor al abrir— pero pulsar
     * otra cifra no movia el cursor, porque `awaitLongPressOrCancellation` **consume** el evento de
     * levantamiento y la guarda `up.isConsumed` descartaba todos los clics.
     *
     * Se pulsa una cifra de la **segunda** columna y se comprueba que `onHit` llega con esa columna.
     */
    @Test
    fun unClicSimpleMueveElCursorAOtraColumna() {
        val tablature = tiesTablature()
        val tester = TabCanvasHitTester()
        val pulsaciones = mutableListOf<com.grafitto.laudate.ui.tablature.TabCanvasHit>()
        runDesktopComposeUiTest(width = 1200, height = 500) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(modifier = Modifier.width(1200.dp).background(Color.White)) {
                        TablatureCanvas(
                            tablature = tablature,
                            scale = 1.0f,
                            numberScale = 1f,
                            onHit = { hit, _ -> pulsaciones += hit },
                            hitTester = tester
                        )
                    }
                }
            }
            waitForIdle()
            // Se busca una cifra de la **segunda** columna (`slotIndex == 1`), que es la que el cursor
            // no ocupa al abrir el editor.
            var objetivo: Offset? = null
            for (x in 0 until 1200 step 4) {
                for (y in 0 until 500) {
                    val p = Offset(x.toFloat(), y.toFloat())
                    val hit = tester(p)
                    if (hit != null && hit.slotIndex == 1) { objetivo = p; break }
                }
                if (objetivo != null) break
            }
            assertTrue("tiene que haber una cifra en la segunda columna", objetivo != null)
            onRoot().performMouseInput {
                moveTo(objetivo!!)
                press()
                release()
            }
            waitForIdle()
        }
        assertTrue(
            "un clic simple tiene que emitir una pulsacion (si no, el cursor no se mueve)",
            pulsaciones.isNotEmpty()
        )
        assertEquals(
            "y la pulsacion tiene que ser la de la columna pulsada",
            1,
            pulsaciones.last().slotIndex
        )
    }

    /**
     * **Corchete de la volta** (D-37): una casilla de **dos compases** (`1.`) y otra de **uno** (`2.`)
     * tienen que dibujar **un solo** corchete cada una, con su gancho de apertura y su rótulo **una
     * sola vez** —no un rótulo por compás, que era el defecto anterior—. Se comprueba midiendo los
     * píxeles del PNG: la línea horizontal del corchete abarca exactamente los compases de la casilla.
     */
    @Test
    fun renderVoltaBracketsToPng() {
        fun note(string: Int, fret: Int) = TabSlot(
            strings = listOf(TabPluck(TabPosition(string, fret))),
            rhythmicFigure = RhythmFigure.QUARTER
        )
        val tablature = Tablature(
            id = "volta",
            songId = "s1",
            title = "Voltas",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(slots = listOf(note(1, 0), note(2, 2), note(3, 3), note(4, 5))),
                TabMeasure(
                    slots = listOf(note(1, 1), note(2, 3), note(3, 5), note(4, 7)),
                    ending = 1,
                    endingTo = 2
                ),
                TabMeasure(
                    slots = listOf(note(1, 2), note(2, 4), note(3, 6), note(4, 8)),
                    ending = 1,
                    endingTo = 2
                ),
                TabMeasure(
                    slots = listOf(note(1, 3), note(2, 5), note(3, 7), note(4, 9)),
                    ending = 3
                )
            ),
            origin = TabOrigin.CREATED
        )
        runDesktopComposeUiTest(width = 1500, height = 500) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(1460.dp)
                            .height(420.dp)
                            .background(Color.White)
                    ) {
                        TablatureCanvas(tablature = tablature, scale = 1.0f)
                    }
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-volta.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue("no se genero el PNG de la volta", out.exists() && out.length() > 0)
            println("PNG de la volta: ${out.absolutePath} (${out.length()} bytes)")
        }
    }

    /**
     * **D-38 · Grupos irregulares de cualquier proporción.** Un compás por proporción, con su
     * corchete y su cifra: `3` (tresillo), `5` (quintillo), `6` (seisillo), `7` (septillo) y `9`
     * (nonillo). Antes el pintor tenía el glifo `tuplet3` **hardcodeado**, así que todos los grupos
     * se rotulaban «3».
     */
    @Test
    fun renderTupletGroupsToPng() {
        fun group(tuplet: TabTuplet, count: Int) = TabMeasure(
            slots = List(count) { i ->
                TabSlot(
                    strings = listOf(TabPluck(TabPosition(1 + i % 4, i))),
                    rhythmicFigure = RhythmFigure.EIGHTH,
                    tuplet = tuplet
                )
            }
        )
        val tablature = Tablature(
            id = "tuplets",
            songId = "s1",
            title = "Grupos irregulares",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH,
            measures = listOf(
                group(TabTuplet.TRIPLET, 3),
                group(TabTuplet.QUINTUPLET, 5),
                group(TabTuplet.SEXTUPLET, 6),
                group(TabTuplet.SEPTUPLET, 7),
                group(TabTuplet.NONUPLET, 9)
            ),
            origin = TabOrigin.CREATED
        )
        runDesktopComposeUiTest(width = 1500, height = 500) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(1460.dp)
                            .height(420.dp)
                            .background(Color.White)
                    ) {
                        TablatureCanvas(tablature = tablature, scale = 1.0f)
                    }
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-tuplets.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue("no se genero el PNG de los grupos", out.exists() && out.length() > 0)
            println("PNG de los grupos: ${out.absolutePath} (${out.length()} bytes)")
        }
    }

    /**
     * **D-46 · Los tres estilos de la blanca** (`TablatureMinimStyle` de MuseScore). Un compás de
     * blancas con cada estilo, y se mide la **tinta por debajo de la última cuerda**, que es donde
     * cuelgan las plicas (`stemsDown = true`):
     *
     *  - `NONE` → la blanca **no lleva plica**: cero tinta;
     *  - `SHORTER` → media plica: algo de tinta;
     *  - `SLASHED` → plica entera **más** dos barras rellenas: la que más tinta deja.
     *
     * Es la comprobación de que el ajuste llega de verdad al lienzo y no se queda en el Dominio.
     */
    @Test
    fun renderMinimStylesToPng() {
        val tablature = Tablature(
            id = "blancas",
            songId = "s1",
            title = "Blancas",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(
                    slots = List(4) { i ->
                        TabSlot(
                            strings = listOf(TabPluck(TabPosition(1 + i % 4, i))),
                            rhythmicFigure = RhythmFigure.HALF
                        )
                    }
                )
            ),
            origin = TabOrigin.CREATED
        )
        val outDir = File("build/tablature-preview").apply { mkdirs() }

        fun inkBelowStaff(style: MinimStyle, name: String): Int {
            var count = 0
            runDesktopComposeUiTest(width = 1200, height = 500) {
                setContent {
                    LaudateTheme(darkTheme = false) {
                        Box(
                            modifier = Modifier
                                .width(1160.dp)
                                .height(420.dp)
                                .background(Color.White)
                        ) {
                            TablatureCanvas(
                                tablature = tablature,
                                scale = 1.0f,
                                options = TabStaffOptions(minimStyle = style)
                            )
                        }
                    }
                }
                waitForIdle()
                val out = File(outDir, "tablature-blanca-$name.png")
                val bmp = onRoot().captureToImage().toAwtImage()
                ImageIO.write(bmp, "png", out)
                assertTrue("no se genero el PNG $name", out.exists() && out.length() > 0)

                fun ink(x: Int, y: Int): Boolean {
                    val rgb = bmp.getRGB(x, y)
                    val r = (rgb shr 16) and 0xFF
                    val g = (rgb shr 8) and 0xFF
                    val b = rgb and 0xFF
                    return (r + g + b) / 3 < 200
                }
                // La **última cuerda** es la fila con más tinta de la mitad inferior del PNG: una
                // línea de cuerda cruza toda la rejilla y ninguna plica llega a esa anchura.
                val rowInk = IntArray(bmp.height) { y ->
                    (0 until bmp.width).count { x -> ink(x, y) }
                }
                val maxInk = rowInk.max()
                var lastStringY = -1
                for (y in bmp.height - 1 downTo 0) {
                    if (rowInk[y] * 2 >= maxInk) {
                        lastStringY = y
                        break
                    }
                }
                assertTrue("no se encontro la ultima cuerda en $name", lastStringY > 0)
                // Las cifras se centran en la cuerda y bajan ~10 px: por debajo de `lastStringY + 10`
                // solo queda la tinta de la plica y de las barras de la blanca.
                for (y in lastStringY + 10 until bmp.height) {
                    for (x in 0 until bmp.width) if (ink(x, y)) count++
                }
                println("tinta bajo la ultima cuerda ($name): $count px")
            }
            return count
        }

        val sinPlica = inkBelowStaff(MinimStyle.NONE, "sin-plica")
        val corta = inkBelowStaff(MinimStyle.SHORTER, "corta")
        val rayada = inkBelowStaff(MinimStyle.SLASHED, "rayada")

        assertEquals("con `NONE` la blanca no deja plica", 0, sinPlica)
        assertTrue("con `SHORTER` la plica corta deja tinta ($corta px)", corta > 0)
        assertTrue(
            "con `SLASHED` la plica entera y sus barras dejan mas tinta ($rayada px > $corta px)",
            rayada > corta
        )
    }

    /**
     * **D-47 · La cifra de la nota ligada** (`ShowTiedFret` de MuseScore). Un compás de cuatro negras
     * en la misma cuerda, las tres primeras ligadas a la siguiente, dibujado con los tres modos:
     *
     *  - `TIE_AND_FRET` → cuatro cifras y tres arcos: la que más tinta deja;
     *  - `TIE` → solo la primera cifra (las demás reciben ligadura y no abren compás, así que se
     *    ocultan) y los tres arcos: menos tinta;
     *  - `NONE` → una cifra y ningún arco: la que menos.
     *
     * Comprueba que el ajuste llega al lienzo y no se queda en el Dominio.
     */
    @Test
    fun renderTiedFretModesToPng() {
        val tablature = Tablature(
            id = "ligaduras",
            songId = "s1",
            title = "Ligaduras",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(
                    slots = List(4) { i ->
                        TabSlot(
                            strings = listOf(
                                TabPluck(TabPosition(1, 0), tiedToNext = i < 3)
                            ),
                            rhythmicFigure = RhythmFigure.QUARTER
                        )
                    }
                )
            ),
            origin = TabOrigin.CREATED
        )
        val outDir = File("build/tablature-preview").apply { mkdirs() }

        fun inkOf(mode: ShowTiedFret, name: String): Int {
            var count = 0
            runDesktopComposeUiTest(width = 1200, height = 500) {
                setContent {
                    LaudateTheme(darkTheme = false) {
                        Box(
                            modifier = Modifier
                                .width(1160.dp)
                                .height(420.dp)
                                .background(Color.White)
                        ) {
                            TablatureCanvas(
                                tablature = tablature,
                                scale = 1.0f,
                                options = TabStaffOptions(showTiedFret = mode)
                            )
                        }
                    }
                }
                waitForIdle()
                val out = File(outDir, "tablature-ligadura-$name.png")
                val bmp = onRoot().captureToImage().toAwtImage()
                ImageIO.write(bmp, "png", out)
                assertTrue("no se genero el PNG $name", out.exists() && out.length() > 0)
                for (y in 0 until bmp.height) {
                    for (x in 0 until bmp.width) {
                        val rgb = bmp.getRGB(x, y)
                        val r = (rgb shr 16) and 0xFF
                        val g = (rgb shr 8) and 0xFF
                        val b = rgb and 0xFF
                        if ((r + g + b) / 3 < 200) count++
                    }
                }
                println("tinta con $name: $count px")
            }
            return count
        }

        val conCifra = inkOf(ShowTiedFret.TIE_AND_FRET, "cifra-y-ligadura")
        val soloLigadura = inkOf(ShowTiedFret.TIE, "solo-ligadura")
        val nada = inkOf(ShowTiedFret.NONE, "nada")

        assertTrue(
            "con «Solo la ligadura» se ocultan cifras y queda menos tinta ($soloLigadura < $conCifra)",
            soloLigadura < conCifra
        )
        assertTrue(
            "con «Nada» desaparecen tambien los arcos ($nada < $soloLigadura)",
            nada < soloLigadura
        )
    }

    @Test
    fun renderLineWeightsToPng() {
        fun measure(barline: TabBarline) = TabMeasure(
            slots = List(4) { i ->
                TabSlot(
                    strings = listOf(TabPluck(TabPosition(1 + i % 4, i))),
                    rhythmicFigure = RhythmFigure.QUARTER
                )
            },
            barline = barline
        )
        val tablature = Tablature(
            id = "weights",
            songId = "s1",
            title = "Grosores de linea",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH,
            measures = listOf(
                measure(TabBarline.SINGLE),
                measure(TabBarline.DOUBLE),
                measure(TabBarline.FINAL)
            ),
            origin = TabOrigin.CREATED
        )
        runDesktopComposeUiTest(width = 1200, height = 500) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(1160.dp)
                            .height(420.dp)
                            .background(Color.White)
                    ) {
                        TablatureCanvas(tablature = tablature, scale = 1.0f)
                    }
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-line-weights.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            assertTrue("no se genero el PNG de grosores", out.exists() && out.length() > 0)
            println("PNG de grosores: ${out.absolutePath} (${out.length()} bytes)")
        }
    }


    /**
     * (D-40) **Articulaciones combinadas**: el glifo combinado ocupa **una** franja de la pila, no dos.
     *
     * Se pinta un compás con cuatro notas y se comprueba sobre la **maquetación** (determinista y
     * exacta) cuántas franjas de articulación reserva cada una:
     *
     * - nota 1: `ACCENT` suelto → **una** franja.
     * - nota 2: `ACCENT_STACCATO` → **una** franja (si se apilara acento + picado serían dos).
     * - nota 3: `ACCENT` + `FERMATA` → **dos** franjas (referencia de dos glifos de verdad).
     * - nota 4: `TENUTO_STACCATO` → **una** franja.
     *
     * El PNG queda en `build/tablature-preview/tablature-combined-articulations.png` para revisarlo a
     * ojo, y se comprueba por píxeles que la zona de articulaciones tiene tinta (el glifo se pinta).
     */
    @Test
    fun renderCombinedArticulationsToPng() {
        fun note(fret: Int, articulations: Set<TabArticulation>) = TabSlot(
            strings = listOf(TabPluck(TabPosition(1, fret)).copy(articulations = articulations)),
            rhythmicFigure = RhythmFigure.QUARTER
        )
        val tablature = Tablature(
            id = "combined",
            songId = "s1",
            title = "Articulaciones combinadas",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH,
            measures = listOf(
                TabMeasure(
                    slots = listOf(
                        note(0, setOf(TabArticulation.ACCENT)),
                        note(1, setOf(TabArticulation.ACCENT_STACCATO)),
                        note(2, setOf(TabArticulation.ACCENT, TabArticulation.FERMATA)),
                        note(3, setOf(TabArticulation.TENUTO_STACCATO))
                    )
                )
            ),
            origin = TabOrigin.CREATED
        )

        // ── Maquetación: cuántas franjas reserva cada nota ────────────────────────────────────────
        val drawing = TablatureLayout.layout(tablature)
        val fretX = drawing.texts.filter { it.kind == TabTextKind.FRET }.map { it.x }
        assertEquals("cuatro cifras", 4, fretX.size)
        val tiersOf = fretX.map { x ->
            drawing.texts
                .filter { it.kind == TabTextKind.ARTICULATION && kotlin.math.abs(it.x - x) < 0.5f }
                .map { it.y }
                .distinct()
                .sorted()
        }
        assertEquals("acento suelto: una franja", 1, tiersOf[0].size)
        assertEquals("sforzato: una franja, no dos", 1, tiersOf[1].size)
        assertEquals("acento + calderon: dos franjas", 2, tiersOf[2].size)
        assertEquals("portato: una franja", 1, tiersOf[3].size)
        // Y la franja de la combinada es la **misma** que la del acento suelto: ocupa el sitio de un
        // solo glifo, pegado a la cuerda.
        assertEquals(tiersOf[0], tiersOf[1])
        assertEquals(tiersOf[0], tiersOf[3])
        // Las dos franjas del acento + calderón están separadas exactamente **un paso de pila**
        // (`TIER_SPACES · stringSpacing` = 1,1 · 20 = 22 px): es la prueba de que son dos glifos.
        assertEquals(22f, tiersOf[2][1] - tiersOf[2][0], 0.5f)

        // ── Render: el glifo se pinta de verdad ──────────────────────────────────────────────────
        runDesktopComposeUiTest(width = 1200, height = 500) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(
                        modifier = Modifier
                            .width(1160.dp)
                            .height(420.dp)
                            .background(Color.White)
                    ) {
                        TablatureCanvas(tablature = tablature, scale = 1.0f)
                    }
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "tablature-combined-articulations.png")
            val bmp = onRoot().captureToImage().toAwtImage()
            ImageIO.write(bmp, "png", out)
            assertTrue("no se genero el PNG de articulaciones combinadas", out.exists() && out.length() > 0)

            // La tinta no es negra pura (`onSurface` ≈ #1D1B20) y el antialiasing deja grises claros.
            fun ink(x: Int, y: Int): Boolean {
                val rgb = bmp.getRGB(x, y)
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                return (r + g + b) / 3 < 200
            }
            // La **primera cuerda** es la **primera** fila (de arriba abajo) con una tirada larga de
            // tinta. Ojo: no vale «la fila con más tinta», porque las cifras parten la primera línea
            // y la segunda queda entera, más larga.
            var stringY = -1
            for (y in 0 until bmp.height / 2) {
                var run = 0
                var longest = 0
                for (x in 0 until bmp.width) {
                    if (ink(x, y)) run++ else run = 0
                    if (run > longest) longest = run
                }
                if (longest > 100) {
                    stringY = y
                    break
                }
            }
            assertTrue("no se encontro la primera cuerda", stringY > 0)
            // Las cifras se centran en la cuerda y miden ~20 px, así que por encima de
            // `stringY - 10` solo queda la tinta de las articulaciones.
            val top = stringY - 10
            val columns = (0 until bmp.width).filter { x -> (0 until top).any { y -> ink(x, y) } }
            val clusters = mutableListOf<IntRange>()
            var start = -1
            var prev = -2
            columns.forEach { x ->
                if (x != prev + 1) {
                    if (start >= 0) clusters += start..prev
                    start = x
                }
                prev = x
            }
            if (start >= 0) clusters += start..prev
            assertEquals("una pila de articulaciones por nota", 4, clusters.size)

            val extents = clusters.map { range ->
                val rows = (0 until top).filter { y -> (range.first..range.last).any { x -> ink(x, y) } }
                if (rows.isEmpty()) 0 else rows.last() - rows.first() + 1
            }
            println("alturas de tinta: acento=${extents[0]} sforzato=${extents[1]} " +
                "acento+calderon=${extents[2]} portato=${extents[3]}")

            // El **sforzato** y el **portato** miden como **un** glifo: bastante menos que la pila de
            // dos glifos de verdad (acento + calderón). Si se apilaran acento y picado, su altura se
            // acercaría a la de la nota 3.
            assertTrue(
                "el sforzato (${extents[1]} px) debe medir como un glifo, no como dos (${extents[2]} px)",
                extents[1] < extents[2] * 0.8f
            )
            assertTrue(
                "el portato (${extents[3]} px) debe medir como un glifo, no como dos (${extents[2]} px)",
                extents[3] < extents[2] * 0.8f
            )
            // Y el glifo combinado es **más alto** que el acento suelto: lleva el picado debajo.
            assertTrue(
                "el sforzato (${extents[1]} px) debe ser mas alto que el acento suelto (${extents[0]} px)",
                extents[1] > extents[0]
            )
            println("PNG de articulaciones combinadas: ${out.absolutePath}")
        }
    }


    /**
     * **D-41 · `TupletNumberType` y `TupletBracketType` del grupo irregular.**
     *
     * MuseScore no dibuja siempre el corchete: con `AUTO_BRACKET` lo **omite** cuando el grupo ocupa
     * exactamente una barra de unión y la cifra cae del lado contrario a la plica
     * (`Tuplet::calcHasBracket`, `tuplet.cpp:203`). En tablatura las plicas van hacia abajo, así que
     * la regla se reduce a «el grupo ocupa una sola barra → sin corchete», que es justo lo que se ve
     * en el `salmo2.pdf`: el tresillo de corcheas se rotula con el `3` y **sin** línea.
     *
     * Se renderizan **tres** tablaturas de un compás (un tresillo de tres corcheas, que ocupa una
     * sola barra) y se mide la **tinta por encima de la primera cuerda**, que es donde vive el
     * corchete:
     *
     *  - `AUTO_BRACKET` → solo la cifra: poca tinta;
     *  - `SHOW_BRACKET` → cifra **y** línea: mucha más tinta;
     *  - `NO_TEXT` → ni cifra ni línea: **cero** tinta.
     */
    @Test
    fun renderTupletBracketTypesToPng() {
        fun triplet(bracketType: TabTupletBracketType, numberType: TabTupletNumberType) =
            TabTuplet(3, 2, numberType = numberType, bracketType = bracketType)

        fun tablatureOf(tuplet: TabTuplet) = Tablature(
            id = "tuplet",
            songId = "s1",
            title = "Corchete del grupo irregular",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH,
            measures = listOf(
                TabMeasure(
                    slots = (0..2).map { i ->
                        TabSlot(
                            strings = listOf(TabPluck(TabPosition(1, i))),
                            rhythmicFigure = RhythmFigure.EIGHTH,
                            tuplet = tuplet
                        )
                    }
                )
            ),
            origin = TabOrigin.CREATED
        )

        // ── Maquetación: la regla de MuseScore, sin dibujar nada ─────────────────────────────────
        val auto = TablatureLayout.layout(tablatureOf(triplet(TabTupletBracketType.AUTO_BRACKET, TabTupletNumberType.SHOW_NUMBER)))
        assertEquals("un solo grupo irregular", 1, auto.tuplets.size)
        assertFalse(
            "el tresillo de corcheas ocupa una sola barra: sin corchete",
            auto.tuplets[0].hasBracket
        )
        assertEquals("pero si lleva la cifra", "3", auto.tuplets[0].label)

        val shown = TablatureLayout.layout(tablatureOf(triplet(TabTupletBracketType.SHOW_BRACKET, TabTupletNumberType.SHOW_NUMBER)))
        assertTrue("con SHOW_BRACKET el corchete se dibuja", shown.tuplets[0].hasBracket)

        val muted = TablatureLayout.layout(tablatureOf(triplet(TabTupletBracketType.AUTO_BRACKET, TabTupletNumberType.NO_TEXT)))
        assertEquals("con NO_TEXT no hay cifra", "", muted.tuplets[0].label)
        assertFalse("y tampoco corchete", muted.tuplets[0].hasBracket)

        // ── Render: se mide la tinta por encima de la primera cuerda ─────────────────────────────
        val outDir = File("build/tablature-preview").apply { mkdirs() }

        fun inkAboveStaff(tablature: Tablature, name: String): Int {
            var count = 0
            var topInkRow = -1
            runDesktopComposeUiTest(width = 1200, height = 500) {
                setContent {
                    LaudateTheme(darkTheme = false) {
                        Box(
                            modifier = Modifier
                                .width(1160.dp)
                                .height(420.dp)
                                .background(Color.White)
                        ) {
                            TablatureCanvas(tablature = tablature, scale = 1.0f)
                        }
                    }
                }
                waitForIdle()
                val out = File(outDir, "tablature-tuplet-$name.png")
                val bmp = onRoot().captureToImage().toAwtImage()
                ImageIO.write(bmp, "png", out)
                assertTrue("no se genero el PNG $name", out.exists() && out.length() > 0)

                fun ink(x: Int, y: Int): Boolean {
                    val rgb = bmp.getRGB(x, y)
                    val r = (rgb shr 16) and 0xFF
                    val g = (rgb shr 8) and 0xFF
                    val b = rgb and 0xFF
                    return (r + g + b) / 3 < 200
                }
                // La **primera cuerda** es la primera fila con **tinta de línea de pentagrama**. Una
                // línea de cuerda cruza toda la rejilla, así que es la fila con más tinta del PNG; el
                // corchete del grupo es más corto (solo abarca el grupo) y la cifra mide ~20 px, así
                // que ninguno llega a la **mitad** de la tinta de una cuerda. Antes se buscaba «la
                // primera tirada de tinta > 100 px» y fallaba: las cifras de traste parten la primera
                // línea en trozos cortos y la heurística devolvía la **segunda** cuerda.
                val rowInk = IntArray(bmp.height) { y ->
                    (0 until bmp.width).count { x -> ink(x, y) }
                }
                val maxInk = rowInk.max()
                var stringY = -1
                for (y in 0 until bmp.height / 2) {
                    if (rowInk[y] * 2 >= maxInk) {
                        stringY = y
                        break
                    }
                }
                assertTrue("no se encontro la primera cuerda en $name", stringY > 0)
                // Las cifras se centran en la cuerda y miden ~20 px: por encima de `stringY - 10`
                // solo queda la tinta del corchete y de la cifra del grupo.
                for (y in 0 until stringY - 10) {
                    for (x in 0 until bmp.width) if (ink(x, y)) count++
                }
                topInkRow = rowInk.indexOfFirst { it > 0 }
                println("tinta sobre la cuerda ($name): $count px, primera fila con tinta: $topInkRow")
            }
            // (D-41) La cifra del grupo se centra **en la línea** del corchete, así que su tinta sube
            // media altura por encima. Si la franja `TOP` no reserva ese aire, la línea cae en el
            // borde del lienzo y la cifra sale **cortada por la mitad**: la primera fila con tinta
            // sería la 0. Es la regresión que arregla D-41.
            if (count > 0) {
                assertTrue(
                    "la cifra del grupo se corta en el borde superior ($name): primera fila con tinta = $topInkRow",
                    topInkRow > 0
                )
            }
            return count
        }

        val autoInk = inkAboveStaff(
            tablatureOf(triplet(TabTupletBracketType.AUTO_BRACKET, TabTupletNumberType.SHOW_NUMBER)),
            "auto"
        )
        val shownInk = inkAboveStaff(
            tablatureOf(triplet(TabTupletBracketType.SHOW_BRACKET, TabTupletNumberType.SHOW_NUMBER)),
            "bracket"
        )
        val mutedInk = inkAboveStaff(
            tablatureOf(triplet(TabTupletBracketType.AUTO_BRACKET, TabTupletNumberType.NO_TEXT)),
            "notext"
        )

        assertTrue("con AUTO_BRACKET se ve la cifra ($autoInk px)", autoInk > 0)
        assertEquals("con NO_TEXT no queda nada sobre la cuerda", 0, mutedInk)
        // El corchete añade la **línea** (con su hueco para la cifra) y los **dos ganchos**: más del
        // doble de la tinta que deja la cifra sola. No se pide un múltiplo mayor porque la banda que se
        // mide corta la cifra por abajo (el `3` se centra en la línea y baja por debajo de ella).
        assertTrue(
            "con SHOW_BRACKET la linea anade mucha tinta ($shownInk px contra $autoInk px)",
            shownInk > autoInk * 2
        )
    }

    /**
     * **D-43 · Rótulo propio de la volta.** En MuseScore el texto del corchete es el `beginText` de un
     * `TextLineBase` y se escribe libremente; aquí se comprueba que el rótulo propio **sustituye** al
     * derivado de las pasadas y que se pinta de verdad, midiendo la tinta de la franja de la volta.
     */
    @Test
    fun renderVoltaCustomTextToPng() {
        fun note(string: Int, fret: Int) = TabSlot(
            strings = listOf(TabPluck(TabPosition(string, fret))),
            rhythmicFigure = RhythmFigure.QUARTER
        )

        fun tablatureOf(endingText: String?) = Tablature(
            id = "volta-text",
            songId = "s1",
            title = "Rotulo de la casilla",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(slots = listOf(note(1, 0), note(2, 2), note(3, 3), note(4, 5))),
                TabMeasure(
                    slots = listOf(note(1, 1), note(2, 3), note(3, 5), note(4, 7)),
                    ending = 1,
                    endingTo = 2,
                    endingText = endingText
                ),
                TabMeasure(slots = listOf(note(1, 2), note(2, 4), note(3, 6), note(4, 8)))
            ),
            origin = TabOrigin.CREATED
        )

        // ── Maquetación: el rótulo propio manda sobre el derivado ────────────────────────────────
        val derived = TablatureLayout.layout(tablatureOf(null))
        val custom = TablatureLayout.layout(tablatureOf("Todos los fieles"))
        val derivedLabel = derived.texts.single { it.kind == TabTextKind.VOLTA }.text
        val customLabel = custom.texts.single { it.kind == TabTextKind.VOLTA }.text
        assertEquals("sin texto propio el rotulo se deriva de las pasadas", "1, 2.", derivedLabel)
        assertEquals("con texto propio manda el texto", "Todos los fieles", customLabel)

        // ── Render: la tinta de la franja de la volta crece con el rótulo ────────────────────────
        val outDir = File("build/tablature-preview").apply { mkdirs() }

        fun voltaInkWidth(tablature: Tablature, name: String): Int {
            var minX = Int.MAX_VALUE
            var maxX = -1
            runDesktopComposeUiTest(width = 1200, height = 500) {
                setContent {
                    LaudateTheme(darkTheme = false) {
                        Box(
                            modifier = Modifier
                                .width(1160.dp)
                                .height(420.dp)
                                .background(Color.White)
                        ) {
                            TablatureCanvas(tablature = tablature, scale = 1.0f)
                        }
                    }
                }
                waitForIdle()
                val out = File(outDir, "tablature-volta-text-$name.png")
                val bmp = onRoot().captureToImage().toAwtImage()
                ImageIO.write(bmp, "png", out)
                assertTrue("no se genero el PNG $name", out.exists() && out.length() > 0)

                fun ink(x: Int, y: Int): Boolean {
                    val rgb = bmp.getRGB(x, y)
                    val r = (rgb shr 16) and 0xFF
                    val g = (rgb shr 8) and 0xFF
                    val b = rgb and 0xFF
                    return (r + g + b) / 3 < 200
                }
                // La **primera cuerda** es la primera fila con casi toda la tinta del PNG (una línea de
                // pentagrama cruza la rejilla entera). Se busca la **primera** que llega al 90 % del
                // máximo, no la del máximo: el antialiasing hace que las seis líneas no midan igual y
                // la del máximo puede ser la segunda.
                val rowInk = (0 until bmp.height).map { y -> (0 until bmp.width).count { x -> ink(x, y) } }
                val peak = rowInk.max()
                val firstString = rowInk.indexOfFirst { it >= peak * 9 / 10 }
                // El **corchete de la volta** es la primera fila ancha por encima del pentagrama:
                // cruza el compás entero, así que gana a cualquier rótulo o gancho. Se busca la
                // **primera** fila ancha (no la del máximo) porque el corchete ocupa 3 px y todas
                // ellas miden lo mismo.
                val bracketRow = (0 until firstString).firstOrNull { rowInk[it] >= 100 } ?: 0
                // El rótulo se apoya **encima** del corchete (como el `beginText` de MuseScore, que
                // va sobre la línea) y crece hacia la derecha. Se mide la banda que queda **por
                // encima** de la línea: por debajo solo está el gancho vertical de 2 px, que es igual
                // en los dos casos y no distingue nada.
                for (y in 0 until bracketRow) {
                    for (x in 0 until bmp.width) {
                        if (ink(x, y)) {
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                        }
                    }
                }
            }
            return if (maxX < 0) 0 else maxX - minX + 1
        }

        val derivedWidth = voltaInkWidth(tablatureOf(null), "derivado")
        val customWidth = voltaInkWidth(tablatureOf("Todos los fieles"), "propio")
        println("Ancho de tinta de la volta: derivado=$derivedWidth px, propio=$customWidth px")
        assertTrue(
            "el rotulo propio tiene que ser mas ancho que el derivado ($customWidth px contra $derivedWidth px)",
            customWidth > derivedWidth
        )
    }

    /**
     * **La paleta actúa sobre una nota real** (D-58). La cuerda elegida en la barra de escritura puede
     * no tener nota en la casilla del cursor; antes eso dejaba **toda** la paleta de nota
     * deshabilitada aunque hubiera notas a la vista, y el usuario concluía —con razón— que los botones
     * no hacían nada. Ahora se cae a la primera nota de la casilla.
     */
    @Test
    fun laPaletaActuaSobreLaPrimeraNotaDeLaCasillaSiLaCuerdaActivaEstaVacia() {
        val tablature = Tablature(
            id = "paleta",
            songId = "s1",
            title = "Paleta",
            instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(
                    slots = listOf(
                        TabSlot(
                            strings = listOf(TabPluck(TabPosition(1, 3))),
                            rhythmicFigure = RhythmFigure.QUARTER
                        )
                    )
                )
            ),
            origin = TabOrigin.CREATED
        )
        val uiState = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Paleta",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.QUARTER,
            editorMeasures = tablature.measures,
            // El cursor está en la casilla 0, pero la cuerda activa (2) **no** tiene nota: la única
            // nota de la casilla es de la cuerda 1.
            selectedSlot = 0 to 0,
            selectedString = 2,
            showPalettes = true,
            showProperties = false,
            tablatureScale = 1f
        )
        val intents = mutableListOf<TablatureUiIntent>()
        // (D-58) Las paletas viven en el **panel de herramientas**, encima de los ajustes: se
        // renderiza ese panel con el ancho que tiene en la aplicación (320 dp), no el editor entero.
        runDesktopComposeUiTest(width = 360, height = 4000) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(modifier = Modifier.width(320.dp)) {
                        TablatureToolSettings(uiState = uiState, onIntent = { intents += it })
                    }
                }
            }
            waitForIdle()
            // (D-84) Las paletas nacen **plegadas**: se abre «Articulaciones» antes de pulsar.
            runCatching {
                onNodeWithContentDescription("Expandir Articulaciones").performClick()
            }
            waitForIdle()
            // (D-82) Se usa «Picado» (articulación) en vez del «Sostenido», que ya no existe: lo que
            // este test fija es que la paleta cae a la **primera nota de la casilla** cuando la
            // cuerda activa no tiene ninguna, no una paleta concreta.
            onNodeWithContentDescription("Picado (staccato)").performClick()
            waitForIdle()
            assertTrue(
                "la paleta tiene que actuar sobre la nota de la cuerda 1",
                intents.isNotEmpty()
            )
        }
    }

    /**
     * (D-95) **La ayuda contextual del editor se abre desde el cromo.** En táctil no hay *hover*,
     * así que los tooltips de los iconos no se ven nunca: sin este botón, las cuatro zonas del
     * editor (barra de escritura, cromo, paletas y panel) solo se explican en el manual, fuera de la
     * pantalla donde se usan.
     */
    @Test
    fun elEditorAbreLaAyudaContextual() {
        runDesktopComposeUiTest(width = 1500, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureEditorPanel(
                        uiState = TablatureUiState(
                            isLoading = false,
                            songId = "s1",
                            songTitle = "Estudio",
                            songKey = "C",
                            isEditorOpen = true,
                            editorIsNew = true,
                            editorTuning = Tuning.GUITAR,
                            editorTime = ScoreTimeSignature("4", 4),
                            editorResolution = TabResolution.EIGHTH,
                            editorMeasures = tiesTablature().measures,
                            selectedSlot = 0 to 0
                        ),
                        onIntent = {}
                    )
                }
            }
            waitForIdle()
            onNodeWithContentDescription("Ayuda del editor de tablatura").performClick()
            waitForIdle()
            // El primer bloque **lee el estado**: compás, casilla, cuerda y traste.
            onNodeWithText("Dónde estás").assertExists()
            onNodeWithText("Atajos de teclado").assertExists()
            onNodeWithText("Guardar").assertExists()
        }
    }
}
