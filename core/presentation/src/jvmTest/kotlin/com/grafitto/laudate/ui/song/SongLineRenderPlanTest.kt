package com.grafitto.laudate.ui.song

import com.grafitto.laudate.domain.model.music.DomainChord
import com.grafitto.laudate.domain.model.music.PitchClass
import com.grafitto.laudate.domain.model.song.ChordAnchor
import com.grafitto.laudate.domain.model.song.LineType
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.song.SongLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del plan de render de una línea del canto.
 *
 * El cancionero real trae filas de acordes sin letra (intros, riffs, `(bis)` instrumental):
 * deben pintarse y **no** reservar una línea de letra vacía.
 */
class SongLineRenderPlanTest {

    @Test
    fun chordOnlyLineShowsChordsAndNoLyrics() {
        val line = SongLine(
            lineIndex = 0,
            lyrics = "",
            chords = listOf(anchor(0, "G"), anchor(4, "C"))
        )

        val plan = SongLineRenderPlan.forLine(line, SongDisplayMode.CHORDS_ABOVE_TEXT)

        assertTrue(plan.showChords)
        assertFalse(plan.showLyrics)
        assertTrue(plan.isChordOnly)
        assertFalse(plan.isEmpty)
    }

    @Test
    fun chordOnlyLineIsSkippedInLyricsOnlyMode() {
        val line = SongLine(lineIndex = 0, lyrics = "", chords = listOf(anchor(0, "G")))

        val plan = SongLineRenderPlan.forLine(line, SongDisplayMode.LYRICS_ONLY)

        assertFalse(plan.showChords)
        assertFalse(plan.showLyrics)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun lyricsWithChordsShowsBoth() {
        val line = SongLine(lineIndex = 0, lyrics = "Cantad al Señor", chords = listOf(anchor(0, "G")))

        val plan = SongLineRenderPlan.forLine(line, SongDisplayMode.CHORDS_ABOVE_TEXT)

        assertTrue(plan.showChords)
        assertTrue(plan.showLyrics)
        assertFalse(plan.isChordOnly)
    }

    @Test
    fun lyricsWithoutChordsKeepsTheLyricInBothModes() {
        val line = SongLine(lineIndex = 0, lyrics = "Solo letra", chords = emptyList())

        assertEquals(
            SongLineRenderPlan(showChords = false, showLyrics = true),
            SongLineRenderPlan.forLine(line, SongDisplayMode.CHORDS_ABOVE_TEXT)
        )
        assertEquals(
            SongLineRenderPlan(showChords = false, showLyrics = true),
            SongLineRenderPlan.forLine(line, SongDisplayMode.LYRICS_ONLY)
        )
    }

    @Test
    fun blankLineWithoutChordsIsEmpty() {
        val line = SongLine(lineIndex = 0, lyrics = "", chords = emptyList())

        assertTrue(SongLineRenderPlan.forLine(line, SongDisplayMode.CHORDS_ABOVE_TEXT).isEmpty)
        assertTrue(SongLineRenderPlan.forLine(line, SongDisplayMode.LYRICS_ONLY).isEmpty)
    }

    @Test
    fun whitespaceOnlyLyricsCountAsNoLyrics() {
        val line = SongLine(lineIndex = 0, lyrics = "   ", chords = listOf(anchor(0, "G")))

        val plan = SongLineRenderPlan.forLine(line, SongDisplayMode.CHORDS_ABOVE_TEXT)

        assertTrue(plan.isChordOnly)
    }

    @Test
    fun commentsAreNotRenderedByTheLyricsPlan() {
        // Los comentarios se pintan aparte (cursiva destacada), no como letra ni acordes.
        val comment = SongLine(
            lineIndex = 0,
            lyrics = "todos fuerte",
            type = LineType.COMMENT,
            comment = "todos fuerte"
        )

        assertTrue(SongLineRenderPlan.forLine(comment, SongDisplayMode.CHORDS_ABOVE_TEXT).isEmpty)
        assertTrue(SongLineRenderPlan.forLine(comment, SongDisplayMode.LYRICS_ONLY).isEmpty)
    }

    @Test
    fun headerLinesAreNotRenderedByTheLyricsPlan() {
        val header = SongLine(lineIndex = 0, lyrics = "", type = LineType.HEADER)

        assertTrue(SongLineRenderPlan.forLine(header, SongDisplayMode.CHORDS_ABOVE_TEXT).isEmpty)
    }

    @Test
    fun chordOnlyTypedLineIsShownInChordsAboveTextModeEvenWithStaleWhitespace() {
        // Un bloque solo-acordes (intro/riff) declara su tipo y no debe reservar letra vacía
        // aunque su `lyrics` arrastre espacios de una limpieza antigua.
        val line = SongLine(
            lineIndex = 0,
            lyrics = "   ",
            chords = listOf(anchor(0, "Am"), anchor(4, "G")),
            type = LineType.CHORD_ONLY
        )

        val plan = SongLineRenderPlan.forLine(line, SongDisplayMode.CHORDS_ABOVE_TEXT)

        assertTrue(plan.showChords)
        assertFalse(plan.showLyrics)
        assertTrue(plan.isChordOnly)
        assertFalse(plan.isEmpty)
    }

    @Test
    fun lyricsAndTablatureModeHidesChordsButKeepsLyrics() {
        val line = SongLine(lineIndex = 0, lyrics = "Aleluya", chords = listOf(anchor(0, "G")))

        val plan = SongLineRenderPlan.forLine(line, SongDisplayMode.LYRICS_AND_TABLATURE)

        assertFalse(plan.showChords)
        assertTrue(plan.showLyrics)
    }

    @Test
    fun tablatureOnlyModeHidesLyricsAndChords() {
        val line = SongLine(lineIndex = 0, lyrics = "Aleluya", chords = listOf(anchor(0, "G")))

        val plan = SongLineRenderPlan.forLine(line, SongDisplayMode.TABLATURE_ONLY)

        assertFalse(plan.showChords)
        assertFalse(plan.showLyrics)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun lyricsChordsAndTablatureModeShowsBothLayers() {
        val line = SongLine(lineIndex = 0, lyrics = "Aleluya", chords = listOf(anchor(0, "G")))

        val plan = SongLineRenderPlan.forLine(line, SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE)

        assertTrue(plan.showChords)
        assertTrue(plan.showLyrics)
    }

    @Test
    fun displayModeLayerFlagsAreConsistent() {
        assertFalse(SongDisplayMode.LYRICS_ONLY.showsTablature)
        assertFalse(SongDisplayMode.CHORDS_ABOVE_TEXT.showsTablature)
        assertTrue(SongDisplayMode.LYRICS_AND_TABLATURE.showsTablature)
        assertTrue(SongDisplayMode.LYRICS_CHORDS_AND_TABLATURE.showsTablature)
        assertTrue(SongDisplayMode.TABLATURE_ONLY.showsTablature)
        assertFalse(SongDisplayMode.TABLATURE_ONLY.showsLyrics)
        assertTrue(SongDisplayMode.LYRICS_ONLY.showsLyrics)
    }

    private fun anchor(characterIndex: Int, chord: String) = ChordAnchor(
        id = "anc-$characterIndex",
        sectionId = "sec",
        lineIndex = 0,
        characterIndex = characterIndex,
        chord = DomainChord(rootNote = PitchClass.parse(chord) ?: PitchClass.C, rawChordText = chord),
        rawChordText = chord
    )
}
