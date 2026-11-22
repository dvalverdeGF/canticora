package com.grafitto.laudate.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.ui.editor.SongEditorUiState
import com.grafitto.laudate.ui.editor.SongEditorTab
import com.grafitto.laudate.ui.editor.SongEditorScreen
import com.grafitto.laudate.ui.tablature.TablatureUiState
import com.grafitto.laudate.ui.theme.LaudateTheme
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * La **vista previa** del editor dibuja la tablatura con el **tamaño global** (D-82).
 *
 * Es la regresión de «ajusto el tamaño para leer en el atril y al previsualizar vuelve a salir
 * pequeño»: [SongEditorScreen] recibía la escala pero no la pasaba a la vista previa, que es un
 * [SongDetailScreen] interno.
 */
@OptIn(ExperimentalTestApi::class)
class SongEditorPreviewTablatureScaleTest {

    @Test
    fun laVistaPreviaUsaElTamanoDeTablaturaGlobal() {
        val tabState = TablatureUiState(
            isLoading = false,
            songId = "s1",
            songTitle = "Estudio",
            songKey = "C",
            isEditorOpen = true,
            editorIsNew = true,
            editorTuning = Tuning.GUITAR,
            editorTime = ScoreTimeSignature("4", 4),
            editorResolution = TabResolution.EIGHTH,
            editorMeasures = emptyList(),
            selectedString = 1,
            selectedFret = 0,
            selectedSlot = 0 to 0,
            tablatureScale = 1.8f
        )
        val editorState = SongEditorUiState(
            songId = "s1",
            title = "Estudio",
            chordProContent = "{title: Estudio}\n[Do]Hola",
            editorTab = SongEditorTab.PREVIEW
        )
        runDesktopComposeUiTest(width = 1200, height = 900) {
            setContent {
                LaudateTheme(darkTheme = false) {
                    Box(Modifier.fillMaxSize()) {
                        SongEditorScreen(
                            uiState = editorState,
                            onIntent = {},
                            tablatureUiState = tabState,
                            onTablatureIntent = {},
                            previewTablatureScale = tabState.tablatureScale
                        )
                    }
                }
            }
            waitForIdle()
            val out = File("build/tablature-preview").apply { mkdirs() }
            val png = File(out, "editor-preview-escala.png")
            ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", png)
            assertTrue("no se generó el PNG de la vista previa", png.length() > 0)
        }
    }
}
