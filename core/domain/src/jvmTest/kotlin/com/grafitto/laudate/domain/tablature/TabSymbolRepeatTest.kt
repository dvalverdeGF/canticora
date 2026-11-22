package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Los cuatro modos del símbolo de duración** (D-45): `TablatureSymbolRepeat` de MuseScore.
 *
 * `StaffType::TablatureSymbolRepeat` (`src/engraving/dom/stafftype.h`, líneas 93-98) tiene cuatro
 * valores —`NEVER`, `SYSTEM`, `MEASURE`, `ALWAYS`— y se consulta en **dos** sitios:
 *
 * 1. `ChordLayout::layoutChords` (`src/engraving/rendering/score/chordlayout.cpp`, líneas 530-537)
 *    decide si el acorde **necesita** símbolo de duración: siempre que no haya acorde anterior, que
 *    cambie la figura, los puntillos o el grupo irregular, o que el anterior sea un silencio; y
 *    **además** si el modo es `ALWAYS`, o si es `MEASURE`/`SYSTEM` y el compás ha cambiado.
 * 2. `TDraw::draw(const TabDurationSymbol*)` (`src/engraving/rendering/score/tdraw.cpp`, líneas
 *    3067-3074) descarta el símbolo de `SYSTEM` cuando el acorde anterior está en el **mismo**
 *    sistema. Por eso `SYSTEM` y `MEASURE` se comportan **igual** al maquetar y solo se distinguen al
 *    pintar: aquí el sistema ya está partido, así que «el compás cambió de sistema» equivale a «este
 *    compás abre el sistema».
 *
 * El ajuste solo afecta a `NoteValueStyle.SYMBOLS_ABOVE`: con `STEMS_AND_BEAMS` la duración ya va en
 * las plicas y las barras, y MuseScore dibuja **todas** las notas (el `beamMode` de una nota unida
 * nunca es `AUTO`, así que `needTabDur` es siempre `true`).
 */
class TabSymbolRepeatTest {

    private val cuatroCuartos = ScoreTimeSignature("4", 4)

    private fun tab(vararg measures: TabMeasure) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra",
        tuning = Tuning.GUITAR, time = cuatroCuartos, resolution = TabResolution.QUARTER,
        measures = measures.toList()
    )

    /** Compás de cuatro negras: la figura **no** cambia dentro del compás. */
    private fun negras(breakBefore: Boolean = false) = TabMeasure(
        slots = List(4) { TabSlot(rhythmicFigure = RhythmFigure.QUARTER) },
        forceSystemBreak = breakBefore
    )

    private fun marcas(
        repeat: RhythmRepeat,
        vararg measures: TabMeasure,
        noteValues: NoteValueStyle = NoteValueStyle.SYMBOLS_ABOVE
    ): List<TabRhythmMark> = TablatureSystemsLayout
        .layout(
            tab(*measures),
            maxWidth = 2000f,
            settings = TabLayoutSettings(staff = TabStaffOptions(noteValues = noteValues, rhythmRepeat = repeat))
        )
        .flatMap { it.drawing.rhythmMarks }

    @Test
    fun nuncaSoloRotulaAlCambiarLaFigura() {
        // Cuatro compases de negras: la figura nunca cambia, así que solo la **primera** columna del
        // documento lleva símbolo. Es el valor por defecto de MuseScore (`stafftype.cpp:79`).
        val marcas = marcas(RhythmRepeat.NEVER, negras(), negras(), negras(), negras())
        assertEquals("solo la primera columna del documento", 1, marcas.size)
        assertEquals("y es la del primer compás", 0, marcas.single().measureIndex)
    }

    @Test
    fun alAbrirCompasRotulaLaPrimeraColumnaDeCadaCompas() {
        val marcas = marcas(RhythmRepeat.MEASURE, negras(), negras(), negras(), negras())
        assertEquals("una por compás", 4, marcas.size)
        assertEquals(
            "en la primera columna de cada uno",
            listOf(0, 1, 2, 3),
            marcas.map { it.measureIndex }
        )
        assertTrue(
            "todas en el pulso 0",
            marcas.all { it.beatOffset == 0.0 }
        )
    }

    @Test
    fun alAbrirSistemaRotulaSoloElPrimerCompasDeCadaSistema() {
        // El compás 3 (índice 2) fuerza salto: sistemas [0,1] y [2,3].
        val marcas = marcas(
            RhythmRepeat.SYSTEM,
            negras(), negras(), negras(breakBefore = true), negras()
        )
        assertEquals("uno por sistema", 2, marcas.size)
        assertEquals(
            "en el compás que abre cada sistema",
            listOf(0, 2),
            marcas.map { it.measureIndex }
        )
    }

    @Test
    fun siempreRotulaTodasLasColumnas() {
        val marcas = marcas(RhythmRepeat.ALWAYS, negras(), negras(), negras(), negras())
        assertEquals("cuatro columnas por compás", 16, marcas.size)
        assertEquals(
            "repartidas por compás",
            listOf(0, 1, 2, 3),
            marcas.map { it.measureIndex }.distinct()
        )
    }

    @Test
    fun alAbrirSistemaYAlAbrirCompasCoincidenSiCadaCompasAbreSistema() {
        // Con un salto en cada compás, «abrir compás» y «abrir sistema» son lo mismo: es la
        // consecuencia de que `TDraw` solo distinga los dos modos al pintar.
        val porCompas = marcas(
            RhythmRepeat.MEASURE,
            negras(), negras(breakBefore = true), negras(breakBefore = true)
        )
        val porSistema = marcas(
            RhythmRepeat.SYSTEM,
            negras(), negras(breakBefore = true), negras(breakBefore = true)
        )
        assertEquals(porCompas, porSistema)
        assertEquals("tres compases, tres sistemas", 3, porCompas.size)
    }

    @Test
    fun conPlicasYBarrasElAjusteNoFiltraNingunaNota() {
        // `Stems and beams`: la duración va en la plica, así que se dibujan todas las notas aunque el
        // modo sea `NEVER` (en MuseScore el `beamMode` de una nota unida nunca es `AUTO`).
        val marcas = marcas(
            RhythmRepeat.NEVER,
            negras(), negras(),
            noteValues = NoteValueStyle.STEMS_AND_BEAMS
        )
        assertEquals("todas las columnas", 8, marcas.size)
    }

    @Test
    fun conLosValoresDeNotaApagadosNoHayNingunSimbolo() {
        val marcas = marcas(
            RhythmRepeat.ALWAYS,
            negras(), negras(),
            noteValues = NoteValueStyle.NONE
        )
        assertTrue("`Note values: None` apaga el pentagrama rítmico", marcas.isEmpty())
    }

    @Test
    fun unCambioDeFiguraRotulaAunqueElModoSeaNunca() {
        // `NEVER` no significa «nunca jamás»: `needTabDur` sigue siendo `true` cuando la figura
        // cambia, porque si no el lector no sabría cuánto dura la nota.
        val mixto = TabMeasure(
            slots = listOf(
                TabSlot(rhythmicFigure = RhythmFigure.QUARTER),
                TabSlot(rhythmicFigure = RhythmFigure.EIGHTH),
                TabSlot(rhythmicFigure = RhythmFigure.EIGHTH),
                TabSlot(rhythmicFigure = RhythmFigure.QUARTER)
            )
        )
        val marcas = marcas(RhythmRepeat.NEVER, mixto)
        assertEquals("negra, corchea y negra: tres cambios", 3, marcas.size)
        assertEquals(
            "con las figuras en orden",
            listOf(RhythmFigure.QUARTER, RhythmFigure.EIGHTH, RhythmFigure.QUARTER),
            marcas.map { it.figure }
        )
    }
}
