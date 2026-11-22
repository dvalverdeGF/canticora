package com.grafitto.laudate.desktop

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreKey
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.serializer.TablatureSerializer
import com.grafitto.laudate.ui.tablature.TablatureEditorPanel
import com.grafitto.laudate.ui.tablature.TablatureUiState
import com.grafitto.laudate.ui.tablature.TablatureView
import com.grafitto.laudate.ui.theme.LaudateTheme
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Reproduce la **tablatura de referencia** del arreglo de «A Dios den gracias (Sal. 66)» para poder
 * compararla visualmente y volcar el JSON que consume la app.
 */
@OptIn(ExperimentalTestApi::class)
class ReferenceTabTest {

    private fun p(string: Int, fret: Int, vararg t: TabTechnique) = TabPluck(
        position = TabPosition(string, fret),
        techniques = t.toSet()
    )

    private fun slot(figure: RhythmFigure, vararg plucks: TabPluck) =
        TabSlot(strings = plucks.toList(), rhythmicFigure = figure)

    private fun chord(figure: RhythmFigure, vararg stringFrets: Pair<Int, Int>) =
        TabSlot(
            strings = stringFrets.map { (s, f) -> TabPluck(position = TabPosition(s, f)) },
            rhythmicFigure = figure
        )

    /** Tablatura de referencia, 14 compases en 4/4 con corcheas/dos corcheas por pulso. */
    private fun referenceTablature(): Tablature = Tablature(
        id = "ref-sal66",
        songId = "song-1",
        title = "A Dios den gracias (Sal. 66)",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.EIGHTH,
        writtenKey = ScoreKey(0),
        origin = TabOrigin.CREATED,
        note = "Transcripción de referencia",
        measures = listOf(
            // m1: 3 2 | 3-4 4 (slide en el 3→4) | 1 0 | 2-0
            TabMeasure(
                slots = listOf(
                    slot(RhythmFigure.EIGHTH, p(2, 3), p(3, 4, TabTechnique.SLIDE)),
                    slot(RhythmFigure.EIGHTH, p(2, 2), p(3, 4)),
                    slot(RhythmFigure.EIGHTH, p(2, 1), p(3, 0)),
                    slot(RhythmFigure.EIGHTH, p(2, 2), p(3, 0))
                )
            ),
            // m2: 1 0 | 2-0 | 1 0 | 3
            TabMeasure(
                slots = listOf(
                    slot(RhythmFigure.EIGHTH, p(2, 1), p(3, 0)),
                    slot(RhythmFigure.EIGHTH, p(3, 2), p(4, 0)),
                    slot(RhythmFigure.EIGHTH, p(2, 1), p(3, 0)),
                    slot(RhythmFigure.EIGHTH, p(5, 3))
                )
            ),
            // m3: 3 2 | 3-4 4 | 4 4 | (0)
            TabMeasure(
                slots = listOf(
                    slot(RhythmFigure.EIGHTH, p(2, 3), p(3, 4, TabTechnique.SLIDE)),
                    slot(RhythmFigure.EIGHTH, p(2, 2), p(3, 4)),
                    slot(RhythmFigure.EIGHTH, p(3, 4)),
                    slot(RhythmFigure.EIGHTH, p(3, 4))
                )
            ),
            // m4: 0 3 2 2 (cuerda 3)
            TabMeasure(
                slots = listOf(
                    slot(RhythmFigure.EIGHTH, p(3, 0)),
                    slot(RhythmFigure.EIGHTH, p(3, 3)),
                    slot(RhythmFigure.EIGHTH, p(3, 2)),
                    slot(RhythmFigure.EIGHTH, p(3, 2))
                )
            ),
            // m5: 0 | 0 2 | 0 2 | 0 1 2
            TabMeasure(
                slots = listOf(
                    slot(RhythmFigure.EIGHTH, p(3, 0)),
                    slot(RhythmFigure.EIGHTH, p(3, 0), p(4, 2)),
                    slot(RhythmFigure.EIGHTH, p(3, 0), p(4, 2)),
                    chord(RhythmFigure.EIGHTH, 3 to 0, 4 to 1, 5 to 2)
                )
            ),
            // m6: 3 2 | 3-4 4 | 4 4 | 3
            TabMeasure(
                slots = listOf(
                    slot(RhythmFigure.EIGHTH, p(2, 3), p(3, 4, TabTechnique.SLIDE)),
                    slot(RhythmFigure.EIGHTH, p(2, 2), p(3, 4)),
                    slot(RhythmFigure.EIGHTH, p(3, 4)),
                    slot(RhythmFigure.EIGHTH, p(3, 3))
                )
            ),
            // m7: 3 0 | 1 0 | 1 0 | 4
            TabMeasure(
                slots = listOf(
                    slot(RhythmFigure.EIGHTH, p(2, 3), p(3, 0)),
                    slot(RhythmFigure.EIGHTH, p(2, 1), p(3, 0)),
                    slot(RhythmFigure.EIGHTH, p(2, 1), p(3, 0)),
                    slot(RhythmFigure.EIGHTH, p(4, 4))
                )
            ),
            // m8: 0 1 2 (3cuerda) | 1 1 3 (cuerda 2) | 2 0 0 (3cuerda) | 3 (5cuerda)
            TabMeasure(
                slots = listOf(
                    chord(RhythmFigure.EIGHTH, 2 to 0, 3 to 1, 4 to 2),
                    slot(RhythmFigure.EIGHTH, p(2, 1)),
                    slot(RhythmFigure.EIGHTH, p(2, 1)),
                    slot(RhythmFigure.EIGHTH, p(2, 3))
                )
            ),
            // m9 (final del bloque): 0 0 0 en cuerdas 1-2-3
            TabMeasure(
                slots = listOf(
                    chord(RhythmFigure.EIGHTH, 1 to 0, 2 to 0, 3 to 0),
                    chord(RhythmFigure.EIGHTH, 1 to 0, 2 to 0, 3 to 0),
                    chord(RhythmFigure.EIGHTH, 1 to 0, 2 to 0, 3 to 0),
                    slot(RhythmFigure.EIGHTH, p(1, 0))
                )
            )
        )
    )

    @Test
    fun dumpJson() {
        val json = TablatureSerializer.encode(referenceTablature())
        val outDir = File("build/tablature-preview").apply { mkdirs() }
        File(outDir, "reference-sal66.json").writeText(json)
        println("JSON: ${File(outDir, "reference-sal66.json").absolutePath}")
    }

    @Test
    fun renderReference() {
        runDesktopComposeUiTest(width = 1400, height = 400) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    TablatureView(tablature = referenceTablature())
                }
            }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "reference-sal66.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            println("PNG: ${out.absolutePath}")
        }
    }

    @Test
    fun renderEditorNarrow() {
        val uiState = TablatureUiState(
            isLoading = false, songId = "s1", songTitle = "A Dios den gracias",
            isEditorOpen = true, editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = referenceTablature().measures,
            selectedSlot = 0 to 0
        )
        runDesktopComposeUiTest(width = 420, height = 1200) {
            setContent { LaudateTheme(darkTheme = false) { TablatureEditorPanel(uiState = uiState, onIntent = {}) } }
            waitForIdle()
            val outDir = File("build/tablature-preview").apply { mkdirs() }
            val out = File(outDir, "editor-narrow2.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
            println("PNG: " + out.absolutePath)
        }
    }
}
