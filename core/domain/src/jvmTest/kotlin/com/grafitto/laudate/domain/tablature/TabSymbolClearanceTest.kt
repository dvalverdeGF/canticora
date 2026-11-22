package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * (D-59) **Los símbolos de técnica no pisan la cifra de la cuerda 1.**
 *
 * El usuario reportó que el trémolo, la palanca caída, los armónicos y `l.v.` se dibujaban **encima
 * del número de traste**. La causa era geométrica: la cifra se centra **en** la línea de la cuerda
 * (su tinta sube media altura) y la pila de símbolos arrancaba a media franja de esa misma línea, con
 * un glifo de caja **más alta que la franja**, así que su mitad inferior caía sobre el número.
 *
 * Estas pruebas fijan la regla: **la caja del símbolo más bajo queda por encima de la tinta de la
 * cifra**, y la franja `SYMBOLS` reserva el aire necesario para que el símbolo más alto no se salga
 * por arriba del sistema.
 */
class TabSymbolClearanceTest {

    private val settings = TabLayoutSettings()

    private fun tablature(vararg techniques: TabTechnique): Tablature = Tablature(
        id = "t",
        songId = "song-1",
        title = "T",
        instrument = Tuning.GUITAR.name,
        tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.QUARTER,
        measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(
                        strings = listOf(
                            TabPluck(TabPosition(1, 3), techniques = techniques.toSet())
                        ),
                        rhythmicFigure = RhythmFigure.QUARTER
                    )
                )
            )
        )
    )

    private fun drawing(
        vararg techniques: TabTechnique,
        scale: Float = 1f
    ): TablatureDrawing = TablatureSystemsLayout.layout(
        tablature(*techniques),
        maxWidth = 1200f,
        settings = settings.copy(fretNumberScale = scale)
    ).first().drawing

    /** Un **espacio de pentagrama** en px, con la separación de cuerdas ya corregida (D-54). */
    private fun staffSpace(scale: Float): Float =
        settings.copy(fretNumberScale = scale).withFretNumberSpacing().staffSpace

    /** Lado de la caja de un glifo: el pintor lo dibuja a **una franja** de la pila (D-59). */
    private fun tierPx(scale: Float): Float =
        settings.copy(fretNumberScale = scale).withFretNumberSpacing().stringSpacing *
            TabArticulationStack.TIER_SPACES

    /**
     * Borde **superior** de la tinta de la cifra de la cuerda 1: la cifra se centra en la línea, así
     * que sube media tinta (`FRET_EM · FRET_INK_RATIO / 2`) y el aire de base deja además
     * [TabArticulationStack.SYMBOL_AIR_SPACES] de separación.
     */
    private fun fretInkTop(scale: Float, vararg techniques: TabTechnique): Float =
        drawing(*techniques, scale = scale).gridTop -
            TabArticulationStack.baseClearanceSpaces(scale) * staffSpace(scale) +
            TabArticulationStack.SYMBOL_AIR_SPACES * staffSpace(scale)

    private fun glyphOf(technique: TabTechnique, scale: Float = 1f): TabGlyph =
        drawing(technique, scale = scale).glyphs.first { it.kind == technique.vectorialGlyph() }

    // ── El defecto reportado ──────────────────────────────────────────────────────────────────────

    @Test
    fun elTremoloNoSeSuperponeSobreLaCifraDeLaCuerda1() {
        val glyph = glyphOf(TabTechnique.TREMOLO)
        val bottom = glyph.y + tierPx(1f) / 2f
        val inkTop = fretInkTop(1f, TabTechnique.TREMOLO)
        assertTrue("el trémolo baja hasta $bottom y la cifra empieza en $inkTop", bottom <= inkTop)
    }

    @Test
    fun laPalancaCaidaNoSeSuperponeSobreLaCifra() {
        val glyph = glyphOf(TabTechnique.WHAMMY_DIVE)
        assertTrue(glyph.y + tierPx(1f) / 2f <= fretInkTop(1f, TabTechnique.WHAMMY_DIVE))
    }

    @Test
    fun elArmonicoNoSeSuperponeSobreLaCifra() {
        val glyph = glyphOf(TabTechnique.HARMONIC)
        assertTrue(glyph.y + tierPx(1f) / 2f <= fretInkTop(1f, TabTechnique.HARMONIC))
    }

    @Test
    fun elLetRingNoSeSuperponeSobreLaCifra() {
        val glyph = glyphOf(TabTechnique.LET_RING)
        assertTrue(glyph.y + tierPx(1f) / 2f <= fretInkTop(1f, TabTechnique.LET_RING))
    }

    // ── La franja reserva el aire ─────────────────────────────────────────────────────────────────

    @Test
    fun elSimboloMasAltoCabeDentroDelSistema() {
        // Con la pila llena (trémolo + armónico) el glifo de arriba no se sale por el borde superior.
        val drawing = drawing(TabTechnique.TREMOLO, TabTechnique.HARMONIC)
        val top = drawing.glyphs.minOf { it.y } - tierPx(1f) / 2f
        assertTrue("el símbolo más alto empieza en $top, fuera del sistema", top >= 0f)
    }

    @Test
    fun elAireDeBaseCreceConElZoomDelNumero() {
        val base = TabArticulationStack.baseClearanceSpaces(1f)
        val doble = TabArticulationStack.baseClearanceSpaces(2f)
        assertTrue("el aire tiene que crecer con la cifra", doble > base)
        // La parte que crece es exactamente la media tinta de la cifra agrandada.
        assertEquals(
            TabTextMetrics.FRET_EM * TabArticulationStack.FRET_INK_RATIO * 0.5f,
            doble - base,
            0.001f
        )
    }

    @Test
    fun elAireDeBaseCubreLaMediaTintaDeLaCifra() {
        val mediaTinta = TabTextMetrics.FRET_EM * TabArticulationStack.FRET_INK_RATIO * 0.5f
        assertTrue(
            "el aire (${TabArticulationStack.baseClearanceSpaces(1f)}) tiene que pasar de la " +
                "media tinta de la cifra ($mediaTinta)",
            TabArticulationStack.baseClearanceSpaces(1f) > mediaTinta
        )
    }

    @Test
    fun conElNumeroAgrandadoElSimboloTampocoPisaLaCifra() {
        val scale = 2f
        val glyph = glyphOf(TabTechnique.TREMOLO, scale)
        assertTrue(glyph.y + tierPx(scale) / 2f <= fretInkTop(scale, TabTechnique.TREMOLO))
    }

    // ── El apilado sigue sin solaparse ────────────────────────────────────────────────────────────

    @Test
    fun dosSimbolosApiladosNoSeSolapanEntreSi() {
        val glyphs = drawing(TabTechnique.TREMOLO, TabTechnique.HARMONIC).glyphs
            .filter { it.kind == TabGlyphKind.TREMOLO || it.kind == TabGlyphKind.HARMONIC }
            .sortedByDescending { it.y }
        assertEquals(2, glyphs.size)
        assertEquals(tierPx(1f), glyphs[0].y - glyphs[1].y, 0.01f)
    }
}
