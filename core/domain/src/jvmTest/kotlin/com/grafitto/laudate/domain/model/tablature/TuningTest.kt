package com.grafitto.laudate.domain.model.tablature

import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas de las afinaciones de fábrica y del orden de dibujo invertido. */
class TuningTest {

    @Test
    fun guitar_hasSixStrings_graveToAguda() {
        val guitar = Tuning.GUITAR
        assertEquals(6, guitar.stringCount)
        // La lista va de grave a aguda: la 6ª es Mi2 y la 1ª es Mi4.
        assertEquals(ScorePitch('E', 0, 2), guitar.strings.first().pitch)
        assertEquals(ScorePitch('E', 0, 4), guitar.strings.last().pitch)
    }

    @Test
    fun pitchOf_usesReadingNumber_thinnestStringIsOne() {
        val guitar = Tuning.GUITAR
        // 1 = la cuerda más AGUDA (como se lee en una tablatura).
        assertEquals(ScorePitch('E', 0, 4), guitar.pitchOf(1))
        assertEquals(ScorePitch('E', 0, 2), guitar.pitchOf(6))
        assertNull(guitar.pitchOf(7))
    }

    @Test
    fun drawnStrings_areOrderedAgudaFirst() {
        val drawn = Tuning.GUITAR.drawnStrings()
        assertEquals(6, drawn.size)
        // Primera fila dibujada = cuerda 1 (la más aguda).
        assertEquals(1 to Tuning.GUITAR.strings.last(), drawn.first())
        assertEquals(6 to Tuning.GUITAR.strings.first(), drawn.last())
    }

    @Test
    fun bassVariants_haveCorrectStringCounts_andLowestString() {
        assertEquals(4, Tuning.BASS_4.stringCount)
        assertEquals(5, Tuning.BASS_5.stringCount)
        assertEquals(6, Tuning.BASS_6.stringCount)
        // El bajo de 5 y 6 añaden la cuerda grave Si0.
        assertEquals(ScorePitch('B', 0, 0), Tuning.BASS_5.strings.first().pitch)
        assertEquals(ScorePitch('B', 0, 0), Tuning.BASS_6.strings.first().pitch)
        // El de 6 añade además un Do3 agudo.
        assertEquals(ScorePitch('C', 0, 3), Tuning.BASS_6.strings.last().pitch)
    }

    @Test
    fun ukulele_isReentrant_firstStringIsNotTheGravest() {
        val uke = Tuning.UKULELE
        assertEquals(4, uke.stringCount)
        // En orden interno (grave→aguda) la 1ª guardada es Sol4, pero la cuerda 1 de lectura es La4.
        assertEquals(ScorePitch('G', 0, 4), uke.strings.first().pitch)
        assertEquals(ScorePitch('A', 0, 4), uke.pitchOf(1))
        // La cuerda 4 (de lectura) es la más aguda real del instrumento tras el reentrante.
        assertEquals(ScorePitch('G', 0, 4), uke.pitchOf(4))
    }

    @Test
    fun lute_usesDoubleCourses_andDoesNotSupportCapo() {
        val lute = Tuning.LUTE
        assertTrue(lute.isCourseInstrument())
        assertTrue(lute.strings.all { it.isCourse })
        assertEquals(false, lute.supportsCapo)
        // La guitarra sí admite cejilla.
        assertTrue(Tuning.GUITAR.supportsCapo)
    }

    @Test
    fun custom_clampsStringCount() {
        assertEquals(4, Tuning.custom(4).stringCount)
        assertEquals(8, Tuning.custom(20).stringCount)
        assertEquals(1, Tuning.custom(0).stringCount)
    }

    @Test
    fun presets_coverAllFactoryTunings() {
        assertEquals(Tuning.presets.size, Tuning.presets.distinct().size)
        assertTrue(Tuning.presets.contains(Tuning.LUTE))
        assertTrue(Tuning.presets.contains(Tuning.BASS_6))
    }

    @Test
    fun tablature_slotsPerMeasure_isDerived_notStored() {
        val tab = Tablature(
            id = "t1",
            songId = "song-1",
            title = "Prueba",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            resolution = TabResolution.EIGHTH
        )
        assertEquals(8, tab.slotsPerMeasure)
        // Cambiar la resolución cambia el nº de casillas sin tocar el modelo guardado.
        assertEquals(4, tab.copy(resolution = TabResolution.QUARTER).slotsPerMeasure)
        assertEquals(16, tab.copy(resolution = TabResolution.SIXTEENTH).slotsPerMeasure)
        // Compás compuesto 6/8 en corcheas = 6 casillas.
        assertEquals(6, tab.copy(time = ScoreTimeSignature("6", 8)).slotsPerMeasure)
    }

    @Test
    fun soundedPitches_computesFretOffsets() {
        val tab = Tablature(
            id = "t1",
            songId = "song-1",
            title = "Prueba",
            instrument = "Guitarra",
            tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4),
            measures = listOf(
                TabMeasure(
                    slots = listOf(
                        // Cuerda 1 (Mi4) al aire -> Mi4.
                        TabSlot(strings = listOf(TabPluck(1, 0))),
                        // Cuerda 1 (Mi4) traste 3 -> Sol4.
                        TabSlot(strings = listOf(TabPluck(1, 3))),
                        // Pausa.
                        TabSlot()
                    )
                )
            )
        )
        val pitches = tab.soundedPitches()
        assertEquals(2, pitches.size)
        assertEquals(ScorePitch('E', 0, 4), pitches[0])
        assertEquals(ScorePitch('G', 0, 4), pitches[1])
    }
}
