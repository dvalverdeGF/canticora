package com.grafitto.laudate.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreKey
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabGrace
import com.grafitto.laudate.domain.model.tablature.TabGraceType
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.ui.tablature.TablatureEditorHelpContent
import com.grafitto.laudate.ui.tablature.TablatureEditorPanel
import com.grafitto.laudate.ui.tablature.TablatureUiState
import com.grafitto.laudate.ui.tablature.TablatureView
import com.grafitto.laudate.ui.theme.LaudateTheme
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Genera las capturas de la tablatura para la web y las fichas de Google Play y Microsoft Store.
 *
 * No hay emulador ni dispositivo en el entorno de integración, así que las capturas se dibujan con
 * Compose Desktop sobre un lienzo del tamaño exacto que pide cada canal. Para simular una pantalla de
 * móvil se fija la densidad (1080 px / 2,75 = 393 dp de ancho útil); el escritorio usa 1,5.
 *
 * Salidas en `desktopApp/build/store-screenshots/`:
 * - `mobile-*.png` — 1080×1920, la medida de Google Play (9:16).
 * - `desktop-*.png` — 1920×1080, la medida que exige Partner Center (mínimo 1920×1080).
 *
 * `store/prepare_store_screenshots.py` recorta y reescala estas salidas a 720×1280 (web) y
 * 1280×665 (web de escritorio).
 */
@OptIn(ExperimentalTestApi::class)
class StoreScreenshotsTest {

    private fun p(string: Int, fret: Int, vararg t: TabTechnique) = TabPluck(
        position = TabPosition(string, fret),
        techniques = t.toSet()
    )

    private fun slot(vararg plucks: TabPluck) = TabSlot(strings = plucks.toList())

    private fun slot(vararg plucks: TabPluck, graces: List<TabGrace>) =
        TabSlot(strings = plucks.toList(), graces = graces)

    /**
     * Arreglo de «Adeste fideles» (John Francis Wade, 1743, dominio público) para guitarra: melodía
     * en las cuerdas 1 y 2 con bajo pulsado en 4, 5 y 6. Sirve de muestra porque enseña cifras,
     * acordes, un *slide*, un *hammer-on* y un adorno.
     */
    private fun sampleTablature(): Tablature = Tablature(
        id = "shot-adeste",
        songId = "song-1",
        title = "Adeste fideles",
        instrument = "Guitarra",
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.QUARTER,
        writtenKey = ScoreKey(0),
        origin = TabOrigin.CREATED,
        note = null,
        measures = phrase() + phrase()
    )

    /** Ocho compases: el tema, la dominante y la cadencia. Se repiten para llenar la pantalla. */
    private fun phrase(): List<TabMeasure> = listOf(
        // G: bajo en Sol, melodía Sol–Sol–Re–Sol
        TabMeasure(
            slots = listOf(
                slot(p(6, 3), p(4, 0)),
                slot(p(1, 3)),
                slot(p(2, 3)),
                slot(p(1, 3))
            )
        ),
        // G: La–Sol–Fa♯–Sol, con un *hammer-on* en la última casilla
        TabMeasure(
            slots = listOf(
                slot(p(5, 2), p(1, 5)),
                slot(p(1, 3)),
                slot(p(1, 2)),
                slot(p(1, 3), p(3, 0))
            )
        ),
        // D: Re–Fa♯–La con un *slide* 3→5 en la segunda cuerda
        TabMeasure(
            slots = listOf(
                slot(p(4, 0), p(3, 2)),
                slot(p(2, 3, TabTechnique.SLIDE)),
                slot(p(1, 2)),
                slot(p(1, 5))
            )
        ),
        // D7 → G: cadencia con un adorno sobre la dominante
        TabMeasure(
            slots = listOf(
                slot(
                    p(1, 2),
                    graces = listOf(
                        TabGrace(stringNumber = 2, fret = 1, type = TabGraceType.ACCIACCATURA)
                    )
                ),
                slot(p(4, 0), p(2, 1)),
                slot(p(1, 0)),
                slot(p(1, 2, TabTechnique.HAMMER), p(5, 2))
            )
        ),
        // C: bajo en Do, melodía Mi–Sol–Mi
        TabMeasure(
            slots = listOf(
                slot(p(5, 3), p(3, 0)),
                slot(p(1, 0)),
                slot(p(1, 3)),
                slot(p(1, 0))
            )
        ),
        // G: vuelta al tema, con acorde de tres cuerdas
        TabMeasure(
            slots = listOf(
                slot(p(6, 3), p(4, 0), p(3, 0)),
                slot(p(2, 0)),
                slot(p(1, 3)),
                slot(p(1, 3), p(2, 3), p(3, 0))
            )
        ),
        // D: dominante con la melodía arriba
        TabMeasure(
            slots = listOf(
                slot(p(4, 0), p(3, 2)),
                slot(p(1, 2)),
                slot(p(1, 5)),
                slot(p(2, 3))
            )
        ),
        // G: cierre en tónica con acento en la nota más aguda
        TabMeasure(
            slots = listOf(
                slot(p(6, 3), p(4, 0)),
                slot(
                    TabPluck(
                        position = TabPosition(1, 3),
                        articulations = setOf(TabArticulation.ACCENT)
                    )
                ),
                slot(p(2, 3)),
                slot(p(1, 3), p(2, 0), p(3, 0), p(4, 0))
            )
        )
    )

    private fun editorState() = TablatureUiState(
        isLoading = false,
        songId = "song-1",
        songTitle = "Adeste fideles",
        songKey = "Sol",
        isEditorOpen = true,
        editorIsNew = false,
        editorTitle = "Adeste fideles",
        editorTuning = Tuning.GUITAR,
        editorTime = ScoreTimeSignature("4", 4),
        editorResolution = TabResolution.QUARTER,
        editorMeasures = sampleTablature().measures,
        selectedSlot = 1 to 1
    )

    /** Lienzo del tamaño exacto de la captura, con la densidad del dispositivo que se simula. */
    @Composable
    private fun Canvas(density: Float, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(density)) {
            LaudateTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    content()
                }
            }
        }
    }

    private fun ComposeUiTest.save(name: String) {
        val outDir = File("build/store-screenshots").apply { mkdirs() }
        val out = File(outDir, name)
        ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", out)
        println("PNG: ${out.absolutePath}")
    }

    @Test
    fun capturasDeMovil() {
        runDesktopComposeUiTest(width = 1080, height = 1920) {
            setContent { Canvas(density = 2.75f) { TablatureEditorPanel(editorState(), onIntent = {}) } }
            save("mobile-editor.png")
        }
        runDesktopComposeUiTest(width = 1080, height = 1920) {
            setContent {
                Canvas(density = 2.75f) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                    ) {
                        Text(
                            text = "Adeste fideles",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Sol mayor · Guitarra · 16 compases",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        TablatureView(tablature = sampleTablature(), scale = 1.25f)
                    }
                }
            }
            save("mobile-lectura.png")
        }
        // El diálogo de ayuda es una **ventana aparte** en escritorio y `captureToImage` solo ve la
        // ventana principal, así que se dibuja su contenido —la misma composable que abre el botón—
        // dentro de un lienzo normal.
        runDesktopComposeUiTest(width = 1080, height = 1920) {
            setContent {
                Canvas(density = 2.75f) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 28.dp)
                    ) {
                        Text(
                            text = "Ayuda del editor de tablatura",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(16.dp))
                        TablatureEditorHelpContent(editorState())
                    }
                }
            }
            save("mobile-ayuda.png")
        }
    }

    @Test
    fun capturasDeEscritorio() {
        runDesktopComposeUiTest(width = 1920, height = 1080) {
            setContent { Canvas(density = 1.5f) { TablatureEditorPanel(editorState(), onIntent = {}) } }
            save("desktop-editor.png")
        }
        runDesktopComposeUiTest(width = 1920, height = 1080) {
            setContent {
                Canvas(density = 1.5f) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                    ) {
                        Text(
                            text = "Adeste fideles",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Sol mayor · Guitarra · 16 compases",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        TablatureView(tablature = sampleTablature(), scale = 1.42f)
                    }
                }
            }
            save("desktop-lectura.png")
        }
    }
}
