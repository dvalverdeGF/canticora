package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScorePitch
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabOrigin
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.model.tablature.TuningString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los datos de cuerda (D-49): el puente altura ↔ (cuerda, traste) y las operaciones del diálogo
 * «String data» de MuseScore.
 *
 * Los valores esperados están calculados a mano sobre la guitarra estándar (Mi2 La2 Re3 Sol3 Si3
 * Mi4, MIDI 40/45/50/55/59/64) y sobre la tiorba (13 trastes, todas las cuerdas «siempre al aire»),
 * que es el caso que MuseScore documenta en el tooltip del diálogo.
 */
class TabStringDataTest {

    private val guitar = Tuning.GUITAR

    /** Tablatura de un solo ataque, para las pruebas del validador. */
    private fun singlePluck(stringNumber: Int, fret: Int) = Tablature(
        id = "t",
        songId = "s",
        title = "Datos de cuerda",
        instrument = guitar.name,
        tuning = guitar,
        time = ScoreTimeSignature("4", 4),
        resolution = TabResolution.QUARTER,
        measures = listOf(
            TabMeasure(
                slots = listOf(
                    TabSlot(
                        strings = listOf(TabPluck(TabPosition(stringNumber, fret))),
                        rhythmicFigure = RhythmFigure.QUARTER
                    )
                )
            )
        ),
        origin = TabOrigin.CREATED
    )

    // ---------------------------------------------------------------------------------------------
    // Altura ↔ (cuerda, traste)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun laCuerdaUnoEsLaMasAgudaYLaSeisLaMasGrave() {
        // El orden de lectura va al revés que la lista interna (grave → aguda), como en MuseScore.
        assertEquals(ScorePitch('E', 0, 4), TabStringData.pitchAt(guitar, 1, 0))
        assertEquals(ScorePitch('B', 0, 3), TabStringData.pitchAt(guitar, 2, 0))
        assertEquals(ScorePitch('G', 0, 3), TabStringData.pitchAt(guitar, 3, 0))
        assertEquals(ScorePitch('D', 0, 3), TabStringData.pitchAt(guitar, 4, 0))
        assertEquals(ScorePitch('A', 0, 2), TabStringData.pitchAt(guitar, 5, 0))
        assertEquals(ScorePitch('E', 0, 2), TabStringData.pitchAt(guitar, 6, 0))
    }

    @Test
    fun pisarUnTrasteSubeLaAlturaEnSemitonos() {
        // 5.ª cuerda (La2 = MIDI 45) en el traste 3 → Do3 (MIDI 48).
        assertEquals(ScorePitch('C', 0, 3), TabStringData.pitchAt(guitar, 5, 3))
        // 6.ª cuerda (Mi2 = MIDI 40) en el traste 12 → Mi3 (MIDI 52).
        assertEquals(ScorePitch('E', 0, 3), TabStringData.pitchAt(guitar, 6, 12))
        // 1.ª cuerda (Mi4 = MIDI 64) en el traste 1 → Fa4 (MIDI 65).
        assertEquals(ScorePitch('F', 0, 4), TabStringData.pitchAt(guitar, 1, 1))
    }

    @Test
    fun unaCuerdaInexistenteNoTieneAltura() {
        assertNull(TabStringData.pitchAt(guitar, 0, 0))
        assertNull(TabStringData.pitchAt(guitar, 7, 0))
        assertNull(TabStringData.pitchAt(guitar, 1, -1))
    }

    @Test
    fun elTrasteDeUnaAlturaEsLaDiferenciaConLaCuerdaAlAire() {
        assertEquals(0, TabStringData.fretAt(guitar, 1, ScorePitch('E', 0, 4)))
        assertEquals(3, TabStringData.fretAt(guitar, 5, ScorePitch('C', 0, 3)))
        assertEquals(12, TabStringData.fretAt(guitar, 6, ScorePitch('E', 0, 3)))
    }

    @Test
    fun unaAlturaPorDebajoDeLaCuerdaAlAireNoTieneTraste() {
        // La 1.ª cuerda al aire es Mi4: nada por debajo cabe en ella.
        assertNull(TabStringData.fretAt(guitar, 1, ScorePitch('D', 0, 4)))
    }

    @Test
    fun elTrasteSeAcotaAlNumeroDeTrastesDelInstrumento() {
        // La guitarra tiene 19 trastes: el 19 cabe, el 20 no.
        assertEquals(19, TabStringData.fretAt(guitar, 6, TabStringData.pitchAt(guitar, 6, 19)!!))
        assertNull(TabStringData.fretAt(guitar, 6, TabStringData.pitchAt(guitar, 6, 20)!!))
        // Con 24 trastes el mismo traste 20 sí cabe: el límite es del instrumento, no global.
        val guitar24 = TabStringData.withFrets(guitar, 24)
        assertEquals(20, TabStringData.fretAt(guitar24, 6, TabStringData.pitchAt(guitar24, 6, 20)!!))
    }

    @Test
    fun unaCuerdaSiempreAlAireSoloAdmiteElTrasteCero() {
        val theorbo = Tuning(
            name = "Tiorba",
            strings = guitar.strings.map { it.copy(alwaysOpen = true) },
            frets = 13
        )
        assertEquals(0, TabStringData.fretAt(theorbo, 1, ScorePitch('E', 0, 4)))
        assertNull(TabStringData.fretAt(theorbo, 1, ScorePitch('F', 0, 4)))
        // Y su altura no cambia al pedir un traste: la cuerda no pasa por el mástil.
        assertEquals(ScorePitch('E', 0, 4), TabStringData.pitchAt(theorbo, 1, 5))
    }

    @Test
    fun convertirUnaAlturaEligeLaCuerdaMasAgudaPosible() {
        // Mi4 cabe en la 1.ª al aire y en la 2.ª en el traste 5: MuseScore elige la 1.ª.
        assertEquals(TabPosition(1, 0), TabStringData.convertPitch(guitar, ScorePitch('E', 0, 4)))
        // Do3 solo cabe en la 5.ª (traste 3) y en la 6.ª (traste 8): gana la 5.ª, más aguda.
        assertEquals(TabPosition(5, 3), TabStringData.convertPitch(guitar, ScorePitch('C', 0, 3)))
        // Mi2 es la 6.ª al aire.
        assertEquals(TabPosition(6, 0), TabStringData.convertPitch(guitar, ScorePitch('E', 0, 2)))
    }

    @Test
    fun unaAlturaPorDebajoDeLaCuerdaMasGraveNoSePuedeConvertir() {
        assertNull(TabStringData.convertPitch(guitar, ScorePitch('D', 0, 2)))
    }

    @Test
    fun unaAlturaPorEncimaDelUltimoTrasteNoSePuedeConvertir() {
        // El techo del mástil es la 1.ª cuerda en el traste 19: Mi4 (64) + 19 = Si5 (83).
        val techo = TabStringData.pitchAt(guitar, 1, 19)!!
        assertEquals(83, techo.midi)
        assertEquals(TabPosition(1, 19), TabStringData.convertPitch(guitar, techo))
        // Un semitono más ya no cabe en ninguna cuerda.
        assertNull(TabStringData.convertPitch(guitar, ScorePitch.fromMidi(techo.midi + 1)))
    }

    @Test
    fun soloSePuedeEscribirUnTrasteQueExistaYQueLaCuerdaAdmita() {
        assertTrue(TabStringData.canEnterFret(guitar, 1, 0))
        assertTrue(TabStringData.canEnterFret(guitar, 1, 19))
        assertFalse(TabStringData.canEnterFret(guitar, 1, 20))
        assertFalse(TabStringData.canEnterFret(guitar, 1, -1))
        assertFalse(TabStringData.canEnterFret(guitar, 7, 0))

        val theorbo = TabStringData.withAlwaysOpen(guitar, 1, true)
        assertTrue(TabStringData.canEnterFret(theorbo, 1, 0))
        assertFalse(TabStringData.canEnterFret(theorbo, 1, 1))
        // Las demás cuerdas siguen admitiendo trastes.
        assertTrue(TabStringData.canEnterFret(theorbo, 2, 5))
    }

    // ---------------------------------------------------------------------------------------------
    // Nombres de altura
    // ---------------------------------------------------------------------------------------------

    @Test
    fun elNombreDeLaAlturaSigueLaGrafiaDeMuseScore() {
        assertEquals("e4", TabStringData.pitchLabel(ScorePitch('E', 0, 4)))
        assertEquals("c♯4", TabStringData.pitchLabel(ScorePitch('C', 1, 4)))
        assertEquals("d♭4", TabStringData.pitchLabel(ScorePitch('C', 1, 4), useFlat = true))
        assertEquals("a2", TabStringData.pitchLabel(ScorePitch('A', 0, 2)))
    }

    @Test
    fun lasOctavasNegativasVanEnMayuscula() {
        // pitch2string() pone la letra en mayúscula cuando la octava es negativa (utils.cpp:437).
        assertEquals("C-1", TabStringData.pitchLabel(ScorePitch('C', 0, -1)))
    }

    @Test
    fun laAlturaDesdeMidiRespetaLaGrafiaPedida() {
        assertEquals(ScorePitch('C', 1, 4), TabStringData.midiToPitch(61))
        assertEquals(ScorePitch('D', -1, 4), TabStringData.midiToPitch(61, useFlat = true))
        assertEquals(ScorePitch('E', 0, 4), TabStringData.midiToPitch(64))
        assertEquals(ScorePitch('C', 0, 4), TabStringData.midiToPitch(60))
        // Y coincide con ScorePitch.fromMidi en la grafía con sostenidos.
        for (midi in 40..80) {
            assertEquals(ScorePitch.fromMidi(midi), TabStringData.midiToPitch(midi))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Operaciones del diálogo
    // ---------------------------------------------------------------------------------------------

    @Test
    fun elNumeroDeTrastesSeAcotaAlRangoDelDialogo() {
        assertEquals(24, TabStringData.withFrets(guitar, 24).frets)
        assertEquals(TabStringData.MIN_FRETS, TabStringData.withFrets(guitar, -5).frets)
        assertEquals(TabStringData.MAX_FRETS, TabStringData.withFrets(guitar, 500).frets)
    }

    @Test
    fun cambiarLaAlturaDeUnaCuerdaNoTocaLasDemas() {
        val changed = TabStringData.withPitch(guitar, 6, ScorePitch('D', 0, 2), useFlat = true)
        assertEquals(ScorePitch('D', 0, 2), changed.strings.first().pitch)
        assertTrue(changed.strings.first().useFlat)
        assertEquals(guitar.strings.drop(1), changed.strings.drop(1))
        // Un número de cuerda inexistente no cambia nada.
        assertEquals(guitar, TabStringData.withPitch(guitar, 9, ScorePitch('C', 0, 4)))
    }

    @Test
    fun marcarSiempreAlAireNoTocaLasDemasCuerdas() {
        val changed = TabStringData.withAlwaysOpen(guitar, 1, true)
        assertTrue(changed.strings.last().alwaysOpen)
        assertEquals(listOf(1), TabStringData.alwaysOpenStrings(changed))
        assertEquals(emptyList<Int>(), TabStringData.alwaysOpenStrings(guitar))
    }

    @Test
    fun unaCuerdaNuevaSeInsertaDebajoDeLaSeleccionada() {
        // «New string…» inserta DEBAJO de la fila seleccionada (editstringdata.cpp:196-200): con la
        // 1.ª seleccionada, la nueva pasa a ser la 2.ª y la 1.ª no se mueve.
        val added = TabStringData.addString(guitar, afterStringNumber = 1, pitch = ScorePitch('A', 0, 4))
        assertEquals(7, added.stringCount)
        assertEquals(ScorePitch('E', 0, 4), TabStringData.pitchAt(added, 1, 0))
        assertEquals(ScorePitch('A', 0, 4), TabStringData.pitchAt(added, 2, 0))
        // Y la que era 2.ª (Si3) pasa a ser la 3.ª.
        assertEquals(ScorePitch('B', 0, 3), TabStringData.pitchAt(added, 3, 0))
    }

    @Test
    fun unaCuerdaNuevaAlFinalQuedaComoLaMasGrave() {
        val added = TabStringData.addString(guitar, afterStringNumber = 0, pitch = ScorePitch('B', 0, 1))
        assertEquals(7, added.stringCount)
        assertEquals(ScorePitch('B', 0, 1), added.strings.first().pitch)
        assertEquals(ScorePitch('B', 0, 1), TabStringData.pitchAt(added, 7, 0))
    }

    @Test
    fun noSePuedenApilarCuerdasSinLimite() {
        var tuning = guitar
        repeat(20) { tuning = TabStringData.addString(tuning, 1, ScorePitch('E', 0, 4)) }
        assertEquals(TabStringData.MAX_STRINGS, tuning.stringCount)
    }

    @Test
    fun quitarUnaCuerdaRenumeraLasDemas() {
        val removed = TabStringData.removeString(guitar, 1)
        assertEquals(5, removed.stringCount)
        // La que era 2.ª (Si3) pasa a ser la 1.ª.
        assertEquals(ScorePitch('B', 0, 3), TabStringData.pitchAt(removed, 1, 0))
    }

    @Test
    fun noSePuedeDejarElInstrumentoSinCuerdas() {
        val one = Tuning(name = "Monocordio", strings = listOf(TuningString(ScorePitch('C', 0, 3))))
        assertEquals(1, TabStringData.removeString(one, 1).stringCount)
    }

    @Test
    fun unaAfinacionEsValidaSiTieneCuerdasYTrastesEnRango() {
        assertTrue(TabStringData.isValid(guitar))
        assertFalse(TabStringData.isValid(guitar.copy(strings = emptyList())))
        assertFalse(TabStringData.isValid(guitar.copy(frets = -1)))
        assertFalse(TabStringData.isValid(guitar.copy(frets = 200)))
    }

    // ---------------------------------------------------------------------------------------------
    // El validador usa el mástil del instrumento
    // ---------------------------------------------------------------------------------------------

    @Test
    fun elValidadorAvisaDeUnTrasteQueNoExisteEnElInstrumento() {
        val tablature = singlePluck(stringNumber = 6, fret = 20)
        // La guitarra tiene 19 trastes: el 20 se sale del mástil.
        assertTrue(
            TabChordValidator.validate(tablature).any { it.kind == TabChordIssueKind.FRET_OUT_OF_RANGE }
        )
        // Con un mástil de 24 trastes el mismo traste es correcto.
        val wide = tablature.copy(tuning = TabStringData.withFrets(guitar, 24))
        assertTrue(TabChordValidator.validate(wide).isEmpty())
    }

    @Test
    fun elValidadorAvisaDeUnTrasteEnUnaCuerdaSiempreAlAire() {
        val theorbo = TabStringData.withAlwaysOpen(guitar, 1, true)
        val tablature = singlePluck(stringNumber = 1, fret = 3).copy(tuning = theorbo)
        val issue = TabChordValidator.validate(tablature)
            .single { it.kind == TabChordIssueKind.FRET_ON_ALWAYS_OPEN_STRING }
        assertEquals(1, issue.stringNumber)
        // Al aire sí se puede.
        val open = singlePluck(stringNumber = 1, fret = 0).copy(tuning = theorbo)
        assertTrue(TabChordValidator.validate(open).isEmpty())
    }
}
