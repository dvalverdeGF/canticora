package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabBarline
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **R · Render**: acercar la notación a MuseScore. Comprueba que las primitivas llevan los datos que
 * el pintor necesita para la **clave `TAB`** (R1) y la **barra gruesa de inicio de sistema** (R6).
 */
class TablatureRenderTest {

    private fun tablature(measureCount: Int = 4): Tablature = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.EIGHTH,
        measures = TablatureEditing.emptyMeasures(
            ScoreTimeSignature("4", 4), TabResolution.EIGHTH, measureCount, withGuideColumns = true
        )
    )

    @Test
    fun laClaveTabVaEnElBordeIzquierdoDelPentagramaComoGlifo() {
        val drawing = TablatureLayout.layout(tablature())
        val clef = drawing.texts.firstOrNull { it.kind == TabTextKind.TAB_CLEF }
        assertTrue("debe existir la clave TAB", clef != null)
        // (D-55) La clave es el **punto de código SMuFL** `6stringTabClef`, no el literal `TAB`: así
        // el lienzo y los dos PDF dibujan el mismo glifo sin adivinar el número de cuerdas cada uno.
        assertEquals(TabGlyphs.tabClef(6), clef!!.text)
        // La clave ya **no** es un texto girado: el pintor la traza como glifo vectorial (S3.1).
        assertTrue("la clave se traza como glifo, no como texto girado", !clef.rotated)
        // (F-04.quater) En MuseScore la cabecera va **dentro** del sistema: las cuerdas arrancan
        // **después** de ella y la clave se dibuja **encima** del pentagrama, al principio del
        // **primer compás**. Medido del `salmo2.pdf`: cuerdas en x = 281 px (13,6 espacios), clave
        // `TAB` en 296–331 (centro 15,2) y primera cifra en 433 (20,9).
        assertTrue(
            "la clave arranca en el borde izquierdo del pentagrama (staffLeft=${drawing.staffLeft})",
            clef.x >= drawing.staffLeft
        )
        assertTrue(
            "la clave cae dentro del primer compás, en su cabecera (gridLeft=${drawing.gridLeft})",
            clef.x > drawing.gridLeft
        )
        val primeraCifra = drawing.texts.filter { it.kind == TabTextKind.FRET }.minOfOrNull { it.x }
        assertTrue(
            "la clave va antes de la primera cifra (cifra=$primeraCifra)",
            primeraCifra == null || clef.x < primeraCifra
        )
    }

    @Test
    fun elSistemaNoAbreConBarraPropia() {
        // (D-4, medido del `salmo2.pdf`) MuseScore **no** dibuja barra de apertura de sistema: las
        // cuerdas arrancan directamente. La primera barra vertical debe ser el cierre del **primer
        // compás**, nunca una a la izquierda de la rejilla.
        val drawing = TablatureLayout.layout(tablature())
        val clef = drawing.texts.first { it.kind == TabTextKind.TAB_CLEF }
        val barsLeftOfClef = drawing.lines.filter { it.isBar && it.x1 < clef.x }
        assertTrue(
            "no debe haber ninguna barra a la izquierda de la clave (fueron ${barsLeftOfClef.size})",
            barsLeftOfClef.isEmpty()
        )
    }

    @Test
    fun cadaCompasCierraConSuBarraYElSistemaConLaFinal() {
        // Ancho pequeño para forzar varios sistemas.
        val systems = TablatureSystemsLayout.layout(tablature(6), maxWidth = 260f)
        assertTrue("debe haber más de un sistema", systems.size > 1)
        // (D-53) La barra **fina + gruesa** cierra la **pieza**, no cada línea: en MuseScore solo el
        // último compás del último sistema lleva `endBarType = END`; los sistemas intermedios cierran
        // con la barra **simple** de su compás. Antes se forzaba `FINAL` en todos y el PDF salía con
        // cuatro barras finales donde el `salmo2.pdf` tiene una.
        systems.forEachIndexed { index, system ->
            val finals = system.drawing.lines.count { it.isBar && it.barline == TabBarline.FINAL }
            if (index == systems.lastIndex) {
                assertEquals("el último sistema cierra con barra final", 1, finals)
            } else {
                assertEquals("el sistema ${index + 1} no cierra la pieza", 0, finals)
            }
            // Todos los sistemas cierran con **alguna** barra al final.
            val lastBar = system.drawing.lines.lastOrNull { it.isBar }
            assertNotNull("cada sistema cierra con una barra", lastBar)
        }
    }

    @Test
    fun unaBarraFinalExplicitaAMitadDeLaPiezaSeRespeta() {
        // Un `FINAL` puesto a mano en un compás intermedio manda sobre la regla del cierre de pieza.
        val tab = tablature(6).let { t ->
            t.copy(measures = t.measures.mapIndexed { i, m ->
                if (i == 2) m.copy(barline = TabBarline.FINAL) else m
            })
        }
        val systems = TablatureSystemsLayout.layout(tab, maxWidth = 260f)
        val finals = systems.flatMap { s -> s.drawing.lines.filter { it.isBar && it.barline == TabBarline.FINAL } }
        assertEquals("el FINAL explícito y el cierre de pieza", 2, finals.size)
    }
}
