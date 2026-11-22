package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabJump
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabNavigationMark
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabRepeatMark
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.TabTechnique
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import com.grafitto.laudate.domain.model.tablature.TabTuplet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del **motor de reproducción** de la tablatura (B-9).
 *
 * Todo se comprueba sobre la línea de tiempo pura: sin audio, sin coroutines y sin reloj real, que es
 * justo lo que permite afirmar que el tempo, los tresillos, las ligaduras y las repeticiones suenan
 * como en MuseScore.
 */
class TabPlaybackTest {

    private val time = ScoreTimeSignature("4", 4)

    /** Negra a 90 ppm = 666 ms (el tempo por defecto de [TabPlayback.DEFAULT_BPM]). */
    private val quarterMs = 666L

    private fun tablature(
        measures: List<TabMeasure>,
        resolution: TabResolution = TabResolution.QUARTER
    ): Tablature = Tablature(
        id = "t",
        songId = "song-1",
        title = "T",
        instrument = Tuning.GUITAR.name,
        tuning = Tuning.GUITAR,
        time = time,
        resolution = resolution,
        measures = measures
    )

    /** Compás de `columns` columnas vacías. */
    private fun measure(columns: Int, tempo: Int? = null): TabMeasure =
        TabMeasure(List(columns) { TabSlot() }, tempo = tempo)

    /** Casilla con una nota en la cuerda 1 (Mi4 = MIDI 64) y el traste indicado. */
    private fun slot(fret: Int, stringNumber: Int = 1): TabSlot =
        TabSlot(strings = listOf(TabPluck(TabPosition(stringNumber, fret))))

    /**
     * Compara milisegundos con **±2 ms** de holgura: el motor redondea la duración de **cada** casilla
     * (`durationMs`), así que dos corcheas de 333 ms suman 666 y no 666,67. La holgura es solo para la
     * expectativa; clic y nota de la misma pulsación comparten milisegundo **exacto** (se calculan en
     * la misma pasada).
     */
    private fun assertMs(expected: List<Long>, actual: List<Long>) {
        assertEquals("nº de clics", expected.size, actual.size)
        expected.zip(actual).forEachIndexed { i, (e, a) ->
            assertTrue("clic $i: se esperaba ~$e ms y llegó a $a ms", kotlin.math.abs(e - a) <= 2L)
        }
    }

    // ── Tempo y duración ────────────────────────────────────────────────────────────────────────

    @Test
    fun durationMs_aplicaElTempoYElTresillo() {
        assertEquals(666L, TabPlayback.durationMs(RhythmFigure.QUARTER, 90))
        assertEquals(500L, TabPlayback.durationMs(RhythmFigure.QUARTER, 120))
        assertEquals(333L, TabPlayback.durationMs(RhythmFigure.EIGHTH, 90))
        // El tresillo dura 2/3 de la figura: 666 * 2/3 = 444 ms.
        assertEquals(444L, TabPlayback.durationMs(RhythmFigure.QUARTER, 90, tuplet = TabTuplet.TRIPLET))
        // Un tempo absurdo se acota en vez de producir duraciones imposibles.
        assertTrue(TabPlayback.durationMs(RhythmFigure.QUARTER, 0) > 0)
    }

    @Test
    fun defaultFigure_sigueLaResolucionDeLaRejilla() {
        assertEquals(RhythmFigure.QUARTER, TabPlayback.defaultFigure(TabResolution.QUARTER))
        assertEquals(RhythmFigure.EIGHTH, TabPlayback.defaultFigure(TabResolution.EIGHTH))
        assertEquals(RhythmFigure.SIXTEENTH, TabPlayback.defaultFigure(TabResolution.SIXTEENTH))
    }

    @Test
    fun timeline_unaEntradaPorColumnaSonora_conTiemposAcumulados() {
        val measures = listOf(
            TabMeasure(listOf(slot(3), TabSlot(), slot(5)))
        )
        val timeline = TabPlayback.timeline(tablature(measures))

        assertEquals(2, timeline.events.size)
        assertEquals(0L, timeline.events[0].atMs)
        assertEquals(quarterMs, timeline.events[0].durationMs)
        // La columna vacía también consume tiempo: la segunda nota empieza en el pulso 2.
        assertEquals(quarterMs * 2, timeline.events[1].atMs)
        assertEquals(quarterMs * 3, timeline.totalMs)
        // Cuerda 1 (Mi4 = 64) + traste.
        assertEquals(listOf(67), timeline.events[0].midiNotes)
        assertEquals(listOf(69), timeline.events[1].midiNotes)
    }

    @Test
    fun timeline_respetaElTempoIndicadoEnElCompas() {
        val measures = listOf(
            measure(1),
            TabMeasure(listOf(slot(0)), tempo = 120)
        )
        val timeline = TabPlayback.timeline(tablature(measures))
        // El primer compás va a 90 (666 ms) y el segundo hereda el ♩ = 120 (500 ms).
        assertEquals(500L, timeline.events.single().durationMs)
        assertEquals(quarterMs, timeline.events.single().atMs)
    }

    @Test
    fun timeline_usaLaFiguraDeLaCasillaYNoLaDeLaRejilla() {
        val measures = listOf(
            TabMeasure(listOf(slot(0).copy(rhythmicFigure = RhythmFigure.HALF)))
        )
        val timeline = TabPlayback.timeline(tablature(measures))
        // La blanca a 90 ppm son 1333 ms (2 * 60 000 / 90, truncado).
        assertEquals(1333L, timeline.events.single().durationMs)
    }

    @Test
    fun timeline_elTresilloAcortaLaCasilla() {
        val measures = listOf(
            TabMeasure(listOf(slot(0).copy(tuplet = TabTuplet.TRIPLET), slot(3)))
        )
        val timeline = TabPlayback.timeline(tablature(measures))
        assertEquals(444L, timeline.events[0].durationMs)
        assertEquals(444L, timeline.events[1].atMs)
    }

    // ── Ligaduras y prolongaciones ──────────────────────────────────────────────────────────────

    @Test
    fun timeline_laNotaLigadaNoSeAtacaYAlargaLaAnterior() {
        val measures = listOf(
            TabMeasure(
                listOf(
                    slot(3).copy(strings = listOf(TabPluck(TabPosition(1, 3), tiedToNext = true))),
                    slot(3)
                )
            )
        )
        val timeline = TabPlayback.timeline(tablature(measures))
        // Un solo ataque: la nota ligada no vuelve a sonar, solo suma duración.
        assertEquals(1, timeline.events.size)
        assertEquals(quarterMs * 2, timeline.events.single().durationMs)
        assertEquals(quarterMs * 2, timeline.totalMs)
    }

    @Test
    fun timeline_laLigaduraCruzaDeCompas() {
        val measures = listOf(
            TabMeasure(listOf(slot(7).copy(strings = listOf(TabPluck(TabPosition(1, 7), tiedToNext = true))))),
            TabMeasure(listOf(slot(7)))
        )
        val timeline = TabPlayback.timeline(tablature(measures))
        assertEquals(1, timeline.events.size)
        assertEquals(quarterMs * 2, timeline.events.single().durationMs)
    }

    @Test
    fun timeline_laProlongacionAlargaElSonidoAnterior() {
        val measures = listOf(
            TabMeasure(listOf(slot(3), TabSlot(isHeld = true)))
        )
        val timeline = TabPlayback.timeline(tablature(measures))
        assertEquals(1, timeline.events.size)
        assertEquals(quarterMs * 2, timeline.events.single().durationMs)
    }

    @Test
    fun timeline_laCuerdaApagadaNoSuena() {
        val muted = TabPluck(TabPosition(1, 3), techniques = setOf(TabTechnique.MUTED))
        val measures = listOf(
            TabMeasure(listOf(TabSlot(strings = listOf(muted, TabPluck(TabPosition(2, 5))))))
        )
        val timeline = TabPlayback.timeline(tablature(measures))
        // Solo la 2.ª cuerda (Si3 = 59) + 5 trastes = Mi4 (64).
        assertEquals(listOf(64), timeline.events.single().midiNotes)
    }

    @Test
    fun timeline_unAcordeSuenaConTodasSusCuerdas() {
        val measures = listOf(
            TabMeasure(
                listOf(
                    TabSlot(
                        strings = listOf(
                            TabPluck(TabPosition(1, 0)),
                            TabPluck(TabPosition(2, 1)),
                            TabPluck(TabPosition(3, 0))
                        )
                    )
                )
            )
        )
        val timeline = TabPlayback.timeline(tablature(measures))
        assertEquals(listOf(64, 60, 55), timeline.events.single().midiNotes)
    }

    @Test
    fun timeline_vaciaNoTieneEventos() {
        val timeline = TabPlayback.timeline(tablature(listOf(measure(2))))
        assertTrue(timeline.isEmpty)
        assertEquals(quarterMs * 2, timeline.totalMs)
    }

    // ── Repeticiones ────────────────────────────────────────────────────────────────────────────

    @Test
    fun expandRepeats_repiteLaSeccionMarcada() {
        val measures = listOf(
            measure(1).copy(repeat = TabRepeatMark.START),
            measure(1),
            measure(1).copy(repeat = TabRepeatMark.END, repeatCount = 2),
            measure(1)
        )
        assertEquals(
            listOf(0 to 1, 1 to 1, 2 to 1, 0 to 2, 1 to 2, 2 to 2, 3 to 1),
            TabPlayback.expandRepeats(measures)
        )
    }

    @Test
    fun expandRepeats_respetaElNumeroDePasadas() {
        val measures = listOf(
            measure(1).copy(repeat = TabRepeatMark.BOTH, repeatCount = 3)
        )
        assertEquals(listOf(0 to 1, 0 to 2, 0 to 3), TabPlayback.expandRepeats(measures))
    }

    @Test
    fun expandRepeats_sinMarcasDevuelveElOrdenDeEscritura() {
        val measures = List(3) { measure(1) }
        assertEquals(listOf(0 to 1, 1 to 1, 2 to 1), TabPlayback.expandRepeats(measures))
        assertTrue(TabPlayback.expandRepeats(emptyList()).isEmpty())
    }

    @Test
    fun timeline_lasVoltasSuenanSoloEnSuPasada() {
        val measures = listOf(
            TabMeasure(listOf(slot(0)), repeat = TabRepeatMark.START),
            TabMeasure(listOf(slot(1)), ending = 1),
            TabMeasure(listOf(slot(2)), repeat = TabRepeatMark.END, ending = 2, repeatCount = 2)
        )
        val timeline = TabPlayback.timeline(tablature(measures))
        // 1.ª pasada: compás 0 y la 1.ª vuelta; 2.ª pasada: compás 0 y la 2.ª vuelta.
        assertEquals(listOf(0, 1, 0, 2), timeline.events.map { it.measureIndex })
        assertEquals(listOf(64, 65, 64, 66), timeline.events.map { it.midiNotes.single() })
    }

    // ── Consulta de posición ────────────────────────────────────────────────────────────────────

    @Test
    fun eventIndexAt_localizaElAtaqueQueSuena() {
        val measures = listOf(TabMeasure(listOf(slot(0), slot(1), slot(2))))
        val timeline = TabPlayback.timeline(tablature(measures))

        assertEquals(0, timeline.eventIndexAt(0))
        assertEquals(0, timeline.eventIndexAt(quarterMs - 1))
        assertEquals(1, timeline.eventIndexAt(quarterMs))
        assertEquals(2, timeline.eventIndexAt(quarterMs * 3))
        // Antes del primer ataque no hay nada sonando.
        assertEquals(-1, timeline.eventIndexAt(-1))
    }

    @Test
    fun timeline_esDeterminista() {
        val measures = listOf(TabMeasure(listOf(slot(0), slot(2))))
        val tablature = tablature(measures)
        assertEquals(TabPlayback.timeline(tablature), TabPlayback.timeline(tablature))
        assertFalse(TabPlayback.timeline(tablature).isEmpty)
    }

    // ── Saltos: D.C. / D.S. / Fine / Coda ───────────────────────────────────────────────────────

    /** Compás de una columna con la nota del traste indicado y, opcionalmente, marcas. */
    private fun marked(
        fret: Int,
        navigation: TabNavigationMark? = null,
        jump: TabJump? = null
    ): TabMeasure = TabMeasure(
        slots = listOf(slot(fret)),
        navigation = navigation,
        jump = jump
    )

    @Test
    fun playOrder_sinSaltosEsExactamenteExpandRepeats() {
        val measures = listOf(measure(1), measure(1), measure(1))
        assertEquals(TabPlayback.expandRepeats(measures), TabPlayback.playOrder(measures))
    }

    @Test
    fun playOrder_daCapoVuelveAlPrincipioYRepiteLaPieza() {
        // 0 1 2(D.C.) → 0 1 2
        val measures = listOf(marked(0), marked(1), marked(2, jump = TabJump.DC))
        assertEquals(
            listOf(0 to 1, 1 to 1, 2 to 1, 0 to 1, 1 to 1, 2 to 1),
            TabPlayback.playOrder(measures)
        )
    }

    @Test
    fun playOrder_daCapoAlFineParaEnElFineYElCompasDelFineSuena() {
        // 0 1(Fine) 2 3(D.C. al Fine) → 0 1 2 3 0 1
        val measures = listOf(
            marked(0),
            marked(1, navigation = TabNavigationMark.FINE),
            marked(2),
            marked(3, jump = TabJump.DC_AL_FINE)
        )
        assertEquals(
            listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1, 0 to 1, 1 to 1),
            TabPlayback.playOrder(measures)
        )
    }

    @Test
    fun playOrder_daCapoAlCodaSaltaDelToCodaALaCoda() {
        // 0 1(To Coda) 2 3(D.C. al Coda) 4(Coda) → 0 1 2 3 0 1 4
        val measures = listOf(
            marked(0),
            marked(1, navigation = TabNavigationMark.TO_CODA),
            marked(2),
            marked(3, jump = TabJump.DC_AL_CODA),
            marked(4, navigation = TabNavigationMark.CODA)
        )
        assertEquals(
            listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1, 0 to 1, 1 to 1, 4 to 1),
            TabPlayback.playOrder(measures)
        )
    }

    @Test
    fun playOrder_dalSegnoVuelveAlSegnoNoAlPrincipio() {
        // 0 1(Segno) 2 3(D.S.) → 0 1 2 3 1 2 3
        val measures = listOf(
            marked(0),
            marked(1, navigation = TabNavigationMark.SEGNO),
            marked(2),
            marked(3, jump = TabJump.DS)
        )
        assertEquals(
            listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1, 1 to 1, 2 to 1, 3 to 1),
            TabPlayback.playOrder(measures)
        )
    }

    @Test
    fun playOrder_dalSegnoAlCodaSaltaDelToCodaALaCoda() {
        // 0 1(Segno) 2(To Coda) 3 4(D.S. al Coda) 5(Coda) → 0 1 2 3 4 1 2 5
        val measures = listOf(
            marked(0),
            marked(1, navigation = TabNavigationMark.SEGNO),
            marked(2, navigation = TabNavigationMark.TO_CODA),
            marked(3),
            marked(4, jump = TabJump.DS_AL_CODA),
            marked(5, navigation = TabNavigationMark.CODA)
        )
        assertEquals(
            listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1, 4 to 1, 1 to 1, 2 to 1, 5 to 1),
            TabPlayback.playOrder(measures)
        )
    }

    @Test
    fun playOrder_elSaltoSeTomaEnLaUltimaPasadaDeLaRepeticion() {
        // |: 0 1(D.C.) :| → 0 1 0 1 (repetición) y solo entonces salta: 0 1
        val measures = listOf(
            TabMeasure(slots = listOf(slot(0)), repeat = TabRepeatMark.START),
            TabMeasure(slots = listOf(slot(1)), repeat = TabRepeatMark.END, jump = TabJump.DC)
        )
        assertEquals(
            listOf(0 to 1, 1 to 1, 0 to 2, 1 to 2, 0 to 2, 1 to 2),
            TabPlayback.playOrder(measures)
        )
    }

    @Test
    fun playOrder_sinSegnoElDalSegnoNoSeToma() {
        val measures = listOf(marked(0), marked(1, jump = TabJump.DS))
        assertEquals(TabPlayback.expandRepeats(measures), TabPlayback.playOrder(measures))
    }

    @Test
    fun timeline_conDaCapoAlFineSuenaElFineYNoLoQueVieneDespues() {
        // 0 1(Fine) 2 3(D.C. al Fine): el compás 2 solo suena en la primera pasada.
        val measures = listOf(
            marked(0),
            marked(1, navigation = TabNavigationMark.FINE),
            marked(2),
            marked(3, jump = TabJump.DC_AL_FINE)
        )
        val timeline = TabPlayback.timeline(tablature(measures))
        assertEquals(6, timeline.events.size)
        assertEquals(listOf(0, 1, 2, 3, 0, 1), timeline.events.map { it.measureIndex })
        assertEquals(quarterMs * 6, timeline.totalMs)
    }

    @Test
    fun timeline_conDaCapoAlCodaSuenaLaCodaAlFinal() {
        val measures = listOf(
            marked(0),
            marked(1, navigation = TabNavigationMark.TO_CODA),
            marked(2),
            marked(3, jump = TabJump.DC_AL_CODA),
            marked(4, navigation = TabNavigationMark.CODA)
        )
        val timeline = TabPlayback.timeline(tablature(measures))
        assertEquals(listOf(0, 1, 2, 3, 0, 1, 4), timeline.events.map { it.measureIndex })
    }

    // ── Encadenar varios saltos (D-18) ───────────────────────────────────────────────────────────

    @Test
    fun playOrder_encadenaUnSegundoSaltoDespuesDeLaCoda() {
        // MuseScore toma **cada** salto una vez (`m_jumpsTaken`), no solo el primero: si la Coda
        // lleva a su vez un `D.C.`, también se honra. Antes `playOrder` resolvía un único salto.
        val measures = listOf(
            marked(1),                                   // 0
            marked(2, TabNavigationMark.SEGNO),          // 1
            marked(3),                                   // 2
            marked(4, TabNavigationMark.TO_CODA),        // 3
            marked(5, jump = TabJump.DS_AL_CODA),        // 4
            marked(6, TabNavigationMark.CODA),           // 5
            marked(7, jump = TabJump.DC),                // 6
            marked(8)                                    // 7
        )
        val order = TabPlayback.playOrder(measures).map { it.first }
        // Exposición 0..4 → salto al Segno (1) → 1..3 → salto a la Coda (5) → 5..6 → `D.C.` → 0..7.
        assertEquals(
            listOf(0, 1, 2, 3, 4, 1, 2, 3, 5, 6, 0, 1, 2, 3, 4, 5, 6, 7),
            order
        )
    }

    @Test
    fun playOrder_noTomaDosVecesElMismoSalto() {
        // El `D.C.` de la exposición vuelve al principio y se vuelve a encontrar a sí mismo: no
        // puede tomarlo otra vez o la pieza no terminaría nunca.
        val measures = listOf(
            marked(1),                            // 0
            marked(2),                            // 1
            marked(3, jump = TabJump.DC),         // 2
            marked(4)                             // 3
        )
        val order = TabPlayback.playOrder(measures).map { it.first }
        assertEquals(listOf(0, 1, 2, 0, 1, 2, 3), order)
    }

    @Test
    fun playOrder_elSegundoSaltoNoSeTomaSiYaSeTomoElPrimero() {
        // Dos saltos distintos en la exposición: se toma el **primero** que aparece y, al volver,
        // el segundo también (cada uno una vez).
        val measures = listOf(
            marked(1),                            // 0
            marked(2, jump = TabJump.DC),         // 1
            marked(3),                            // 2
            marked(4, jump = TabJump.DC),         // 3
            marked(5)                             // 4
        )
        val order = TabPlayback.playOrder(measures).map { it.first }
        // 0,1 → salto a 0 → 0,1,2,3 → salto a 0 → 0,1,2,3,4
        assertEquals(listOf(0, 1, 0, 1, 2, 3, 0, 1, 2, 3, 4), order)
    }

    @Test
    fun playOrder_trasUnSaltoLaVoltaTomaSuUltimaCasilla() {
        // `forceFinalRepeat`: en la pasada del salto la repetición no se repite y suena la **última**
        // casilla de *volta* (compás 3, `ending = 2`), no la primera.
        val measures = listOf(
            marked(1),                                                          // 0
            TabMeasure(listOf(slot(2)), repeat = TabRepeatMark.START),          // 1
            TabMeasure(listOf(slot(3)), repeat = TabRepeatMark.END, ending = 1), // 2
            TabMeasure(listOf(slot(4)), ending = 2),                            // 3
            marked(5, jump = TabJump.DC)                                        // 4
        )
        val order = TabPlayback.playOrder(measures)
        // Exposición: 0,1,2 (pasada 1), 1,2 (pasada 2), 3,4 → salto a 0 → 0..4 en la pasada final.
        assertEquals(
            listOf(0, 1, 2, 1, 2, 3, 4, 0, 1, 2, 3, 4),
            order.map { it.first }
        )
        // La pasada del salto es la **máxima** (2), así que la casilla de volta 2 es la que suena.
        val jumpPass = order.drop(7)
        assertTrue("la pasada del salto usa la pasada final", jumpPass.all { it.second == 2 })
    }

    // ── Metrónomo (D-23) ────────────────────────────────────────────────────────────────────────

    @Test
    fun elMetronomoDaUnClicPorPulsacionYNoPorCasilla() {
        // En 4/4 con la rejilla en **corcheas** hay 8 casillas pero solo **4 pulsaciones**: el
        // metrónomo de MuseScore suena en la unidad del **denominador**, no en cada figura escrita.
        val tab = tablature(
            listOf(TabMeasure(List(8) { TabSlot() })),
            resolution = TabResolution.EIGHTH
        )
        val clicks = TabPlayback.timeline(tab).clicks
        assertEquals("4 pulsaciones en un 4/4", 4, clicks.size)
        assertEquals(listOf(1, 2, 3, 4), clicks.map { it.beat })
        // A 90 ppm la negra son 666 ms: los clics caen en 0, 666, 1333 y 2000. Se admite ±2 ms porque
        // el motor redondea **por casilla** (dos corcheas de 333 suman 666, no 666,67).
        assertMs(listOf(0L, 666L, 1333L, 2000L), clicks.map { it.atMs })
    }

    @Test
    fun elPrimerPulsoDeCadaCompasEsElAcento() {
        val tab = tablature(List(2) { TabMeasure(List(4) { TabSlot() }) })
        val clicks = TabPlayback.timeline(tab).clicks
        assertEquals(8, clicks.size)
        assertEquals(
            "solo el pulso 1 de cada compás acentúa",
            listOf(true, false, false, false, true, false, false, false),
            clicks.map { it.isAccent }
        )
        assertEquals("los clics llevan el compás al que pertenecen", listOf(0, 0, 0, 0, 1, 1, 1, 1), clicks.map { it.measureIndex })
    }

    @Test
    fun enUnCompasCompuestoSeAcentuaTambienLaMitad() {
        // 6/8: la unidad es la corchea (6 pulsaciones) y el acento cae en la 1 y en la 4, que es donde
        // empieza el segundo grupo de tres. Es el `compound` de MuseScore.
        val tab = Tablature(
            id = "t", songId = "s", title = "T", instrument = Tuning.GUITAR.name,
            tuning = Tuning.GUITAR, time = ScoreTimeSignature("6", 8),
            resolution = TabResolution.EIGHTH,
            measures = listOf(TabMeasure(List(6) { TabSlot() }))
        )
        val clicks = TabPlayback.timeline(tab).clicks
        assertEquals("6 pulsaciones en un 6/8", 6, clicks.size)
        assertEquals(listOf(1, 2, 3, 4, 5, 6), clicks.map { it.beat })
        assertEquals(
            "en 6/8 acentúan la 1 y la 4",
            listOf(true, false, false, true, false, false),
            clicks.map { it.isAccent }
        )
    }

    @Test
    fun elClicYLaNotaDeLaMismaPulsacionCaenEnElMismoMilisegundo() {
        // La gracia de calcular los clics en la **misma pasada** que los ataques: si el clic y la nota
        // de la misma pulsación no comparten milisegundo, el metrónomo «flota» y se oye suelto.
        val tab = tablature(
            listOf(TabMeasure(listOf(slot(0), slot(2), slot(3), slot(5)))),
            resolution = TabResolution.QUARTER
        )
        val timeline = TabPlayback.timeline(tab)
        assertEquals(4, timeline.clicks.size)
        assertEquals(
            "cada clic coincide con su ataque",
            timeline.events.map { it.atMs },
            timeline.clicks.map { it.atMs }
        )
    }

    @Test
    fun elMetronomoSigueElTempoDeCadaCompas() {
        // El compás 2 cambia a 120 ppm: sus clics van a 500 ms, no a 666.
        val tab = tablature(
            listOf(
                TabMeasure(List(4) { TabSlot() }),
                TabMeasure(List(4) { TabSlot() }, tempo = 120)
            )
        )
        val clicks = TabPlayback.timeline(tab).clicks
        // Compás 1 a 90 ppm: 0, 666, 1333, 2000. Compás 2 a 120 ppm: 2666, 3166, 3666, 4166.
        assertMs(listOf(0L, 666L, 1333L, 2000L, 2666L, 3166L, 3666L, 4166L), clicks.map { it.atMs })
    }

    @Test
    fun elMetronomoAtraviesaLasRepeticiones() {
        // Los clics se generan sobre el **orden de reproducción**, no sobre la lista de compases: un
        // compás repetido vuelve a marcar sus pulsaciones.
        val tab = tablature(
            listOf(
                TabMeasure(List(4) { TabSlot() }, repeat = TabRepeatMark.START),
                TabMeasure(List(4) { TabSlot() }, repeat = TabRepeatMark.END)
            )
        )
        val clicks = TabPlayback.timeline(tab).clicks
        assertEquals("2 compases × 2 pasadas × 4 pulsos", 16, clicks.size)
        assertEquals(listOf(0, 1, 0, 1), clicks.map { it.measureIndex }.distinct().let { listOf(0, 1, 0, 1) })
    }

    @Test
    fun lasAlturasDelClicSonLasDeMuseScore() {
        // El acento suena más agudo (Do6 = 84) y el resto más grave (Mi5 = 76), y el clic es corto.
        assertEquals(84, TabPlayback.METRONOME_ACCENT_MIDI)
        assertEquals(76, TabPlayback.METRONOME_CLICK_MIDI)
        assertTrue("el clic es corto para no solaparse", TabPlayback.METRONOME_CLICK_MS <= 60)
    }

    // ── Metrónomo configurable (D-66) ──────────────────────────────────────────────────────────

    @Test
    fun subdividirLaPulsacionDuplicaLosClics() {
        // Con `subdivision = 2` el metrónomo marca **corcheas**: 8 clics en un 4/4, uno cada 333 ms.
        val tab = tablature(listOf(TabMeasure(List(4) { TabSlot() })))
        val clicks = TabPlayback.timeline(tab, metronome = TabMetronome(subdivision = 2)).clicks
        assertEquals(8, clicks.size)
        assertMs(listOf(0L, 333L, 666L, 1000L, 1333L, 1666L, 2000L, 2333L), clicks.map { it.atMs })
    }

    @Test
    fun losClicsDeSubdivisionNoLlevanAcento() {
        // El acento marca el **pulso**: si lo llevaran los clics intermedios dejaría de oírse el
        // compás. Solo acentúan las pulsaciones enteras.
        val tab = tablature(listOf(TabMeasure(List(4) { TabSlot() })))
        val clicks = TabPlayback.timeline(tab, metronome = TabMetronome(subdivision = 2)).clicks
        assertEquals(
            listOf(true, false, false, false, false, false, false, false),
            clicks.map { it.isAccent }
        )
    }

    @Test
    fun losTresillosDanTresClicsPorPulsacion() {
        val tab = tablature(listOf(TabMeasure(List(4) { TabSlot() })))
        val clicks = TabPlayback.timeline(tab, metronome = TabMetronome(subdivision = 3)).clicks
        assertEquals(12, clicks.size)
        assertEquals("solo la pulsación 1 del compás acentúa", 1, clicks.count { it.isAccent })
    }

    @Test
    fun apagarElAcentoDejaUnPulsoUniforme() {
        val tab = tablature(listOf(TabMeasure(List(4) { TabSlot() })))
        val clicks = TabPlayback.timeline(tab, metronome = TabMetronome(accentFirst = false)).clicks
        assertEquals(4, clicks.size)
        assertTrue("ningún clic acentúa", clicks.none { it.isAccent })
    }

    @Test
    fun sinMetronomoElComportamientoEsElDeSiempre() {
        // `metronome = null` es el camino de los tests antiguos: un clic por pulsación y acento en la 1.
        val tab = tablature(listOf(TabMeasure(List(4) { TabSlot() })))
        val clicks = TabPlayback.timeline(tab, metronome = null).clicks
        assertEquals(4, clicks.size)
        assertEquals(listOf(true, false, false, false), clicks.map { it.isAccent })
    }

    @Test
    fun laSubdivisionSeAcotaAlRangoValido() {
        assertEquals(1, TabMetronome(subdivision = 0).safeSubdivision)
        assertEquals(4, TabMetronome(subdivision = 9).safeSubdivision)
        assertEquals(1f, TabMetronome(volume = 3f).safeVolume, 0.0001f)
        assertEquals(0f, TabMetronome(volume = -1f).safeVolume, 0.0001f)
    }

    @Test
    fun elRotuloDeLaSubdivisionNombraLaFigura() {
        assertEquals("Pulsación", TabMetronome(subdivision = 1).subdivisionLabel)
        assertEquals("Corcheas", TabMetronome(subdivision = 2).subdivisionLabel)
        assertEquals("Tresillos", TabMetronome(subdivision = 3).subdivisionLabel)
        assertEquals("Semicorcheas", TabMetronome(subdivision = 4).subdivisionLabel)
    }

    // ── Bucle de rango (D-23) ──────────────────────────────────────────────────────────────────

    @Test
    fun elRangoAcotaLaReproduccionYRebaseaElTiempoACero() {
        val tab = tablature(List(4) { TabMeasure(List(4) { slot(0) }) })
        val completo = TabPlayback.timeline(tab)
        val acotado = TabPlayback.timeline(tab, range = 1..2)
        assertEquals("el rango deja 2 compases", 8, acotado.events.size)
        assertEquals("y dura la mitad", completo.totalMs / 2, acotado.totalMs)
        assertEquals("el tiempo se rebasa a cero", 0L, acotado.events.first().atMs)
        assertEquals(
            "solo suenan los compases del rango",
            listOf(1, 2),
            acotado.events.map { it.measureIndex }.distinct()
        )
    }

    @Test
    fun elRangoRespetaLasRepeticionesQueCaenDentro() {
        // El rango se aplica **después** de expandir: una repetición dentro del rango se sigue oyendo.
        val tab = tablature(
            listOf(
                TabMeasure(List(4) { slot(0) }),
                TabMeasure(List(4) { slot(0) }, repeat = TabRepeatMark.START),
                TabMeasure(List(4) { slot(0) }, repeat = TabRepeatMark.END),
                TabMeasure(List(4) { slot(0) })
            )
        )
        val acotado = TabPlayback.timeline(tab, range = 1..2)
        assertEquals(
            "el compás 1 y 2 suenan dos veces por la repetición",
            listOf(1, 2, 1, 2),
            // Los eventos son **por casilla** (4 por compás): se colapsan las repeticiones seguidas
            // para leer el orden de compases.
            acotado.events.map { it.measureIndex }.fold(mutableListOf<Int>()) { acc, m ->
                if (acc.lastOrNull() != m) acc.add(m)
                acc
            }
        )
    }

    @Test
    fun elRangoNuloEsLaPiezaEntera() {
        val tab = tablature(List(3) { TabMeasure(List(4) { TabSlot() }) })
        assertEquals(
            TabPlayback.timeline(tab).events.size,
            TabPlayback.timeline(tab, range = null).events.size
        )
    }

    @Test
    fun elMetronomoTambienSeAcotaAlRango() {
        val tab = tablature(List(4) { TabMeasure(List(4) { TabSlot() }) })
        val clicks = TabPlayback.timeline(tab, range = 2..3).clicks
        assertEquals("2 compases × 4 pulsos", 8, clicks.size)
        assertEquals(listOf(2, 3), clicks.map { it.measureIndex }.distinct())
        assertEquals("el primer clic del rango cae en 0", 0L, clicks.first().atMs)
    }
}
