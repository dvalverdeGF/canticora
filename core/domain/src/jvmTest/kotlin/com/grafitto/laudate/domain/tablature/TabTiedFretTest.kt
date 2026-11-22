package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabMeasure
import com.grafitto.laudate.domain.model.tablature.TabPluck
import com.grafitto.laudate.domain.model.tablature.TabPosition
import com.grafitto.laudate.domain.model.tablature.TabResolution
import com.grafitto.laudate.domain.model.tablature.TabSlot
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.domain.model.tablature.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **La cifra de una nota ligada** (D-47): `ShowTiedFret` y `ParenthesizeTiedFret` de MuseScore.
 *
 * Una ligadura de prolongación une dos notas de la **misma altura**: la segunda no se vuelve a atacar,
 * así que su cifra es redundante. MuseScore deja elegir qué se hace con ella en
 * `Note::shouldHideFret()` (`src/engraving/dom/note.cpp:1527-1556`), y de esa decisión cuelgan los
 * **paréntesis generados** de `ChordLayout::layoutNote2` (`chordlayout.cpp:3166-3169`) y el **arco
 * omitido** de `SlurTieLayout::tieSegmentShouldBeSkipped` (`slurtielayout.cpp:1553-1563`).
 */
class TabTiedFretTest {

    private val cuatroCuartos = ScoreTimeSignature("4", 4)

    private fun tab(vararg measures: TabMeasure) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra",
        tuning = Tuning.GUITAR, time = cuatroCuartos, resolution = TabResolution.QUARTER,
        measures = measures.toList()
    )

    /** Compás de cuatro negras en la cuerda 1; [tied] marca las columnas que dejan la ligadura abierta. */
    private fun negras(vararg tied: Boolean) = TabMeasure(
        slots = tied.map { TabSlot(strings = listOf(TabPluck(TabPosition(1, 0), tiedToNext = it))) }
    )

    private fun dibujo(
        vararg measures: TabMeasure,
        showTiedFret: ShowTiedFret = ShowTiedFret.TIE_AND_FRET,
        parenthesize: ParenthesizeTiedFret = ParenthesizeTiedFret.START_OF_SYSTEM
    ) = TablatureSystemsLayout.layout(
        tab(*measures),
        maxWidth = 2000f,
        settings = TabLayoutSettings(
            staff = TabStaffOptions(
                showTiedFret = showTiedFret,
                parenthesizeTiedFret = parenthesize
            )
        )
    )

    /** Cifras de traste dibujadas, en orden de lectura. */
    private fun cifras(dibujo: List<TabSystem>): List<String> = dibujo
        .flatMap { it.drawing.texts }
        .filter { it.kind == TabTextKind.FRET }
        .map { it.text }

    /** Arcos de ligadura de prolongación dibujados. */
    private fun arcos(dibujo: List<TabSystem>): Int = dibujo
        .flatMap { it.drawing.glyphs }
        .count { it.kind == TabGlyphKind.TIE }

    // --- La decisión pura, contra `Note::shouldHideFret()` -------------------------------------

    @Test
    fun sinLigaduraLaCifraSiempreSeVe() {
        // `if (!tieBack() ...) return false;` (`note.cpp:1528`): sin ligadura no hay nada que ocultar,
        // sea cual sea el ajuste.
        ShowTiedFret.entries.forEach { show ->
            ParenthesizeTiedFret.entries.forEach { parenth ->
                assertFalse(
                    "sin ligadura la cifra se ve ($show/$parenth)",
                    TabTiedFret.shouldHideFret(
                        tiedFromPrevious = false,
                        atMeasureStart = false,
                        opensSystem = false,
                        showTiedFret = show,
                        parenthesize = parenth
                    )
                )
            }
        }
    }

    @Test
    fun conCifraYLigaduraLaCifraSiempreSeVe() {
        // `if (showTiedFret == ShowTiedFret::TIE_AND_FRET) return false;` (`note.cpp:1540-1542`).
        ParenthesizeTiedFret.entries.forEach { parenth ->
            assertFalse(
                "con `TIE_AND_FRET` la cifra se ve siempre ($parenth)",
                TabTiedFret.shouldHideFret(
                    tiedFromPrevious = true,
                    atMeasureStart = false,
                    opensSystem = false,
                    showTiedFret = ShowTiedFret.TIE_AND_FRET,
                    parenthesize = parenth
                )
            )
        }
    }

    @Test
    fun conNuncaLaCifraLigadaNoSeVeJamas() {
        // `if (parenthTiedFret == ParenthesizeTiedFret::NEVER || !rtick().isZero()) return true;`
        // (`note.cpp:1545-1547`): `NEVER` corta antes de mirar el compás.
        listOf(ShowTiedFret.TIE, ShowTiedFret.NONE).forEach { show ->
            assertTrue(
                "con `NEVER` la cifra ligada no se ve ($show)",
                TabTiedFret.shouldHideFret(
                    tiedFromPrevious = true,
                    atMeasureStart = true,
                    opensSystem = true,
                    showTiedFret = show,
                    parenthesize = ParenthesizeTiedFret.NEVER
                )
            )
        }
    }

    @Test
    fun fueraDelArranqueDeCompasLaCifraLigadaNoSeVe() {
        // `!rtick().isZero()` → `true` (`note.cpp:1545`): a mitad de compás no hay recordatorio.
        listOf(ParenthesizeTiedFret.START_OF_SYSTEM, ParenthesizeTiedFret.START_OF_MEASURE)
            .forEach { parenth ->
                assertTrue(
                    "a mitad de compás la cifra ligada no se ve ($parenth)",
                    TabTiedFret.shouldHideFret(
                        tiedFromPrevious = true,
                        atMeasureStart = false,
                        opensSystem = true,
                        showTiedFret = ShowTiedFret.TIE,
                        parenthesize = parenth
                    )
                )
            }
    }

    @Test
    fun alAbrirCompasLaCifraLigadaSeVe() {
        // `if (parenthTiedFret == ParenthesizeTiedFret::START_OF_MEASURE) return false;`
        // (`note.cpp:1549-1551`).
        assertFalse(
            "con `START_OF_MEASURE` la cifra se ve al abrir cualquier compás",
            TabTiedFret.shouldHideFret(
                tiedFromPrevious = true,
                atMeasureStart = true,
                opensSystem = false,
                showTiedFret = ShowTiedFret.TIE,
                parenthesize = ParenthesizeTiedFret.START_OF_MEASURE
            )
        )
    }

    @Test
    fun alAbrirSistemaLaCifraLigadaSeVeSoloSiElCompasAbreElSistema() {
        // `return !isStartOfSystem;` (`note.cpp:1553-1556`).
        assertFalse(
            "con `START_OF_SYSTEM` y el compás abriendo sistema, la cifra se ve",
            TabTiedFret.shouldHideFret(
                tiedFromPrevious = true,
                atMeasureStart = true,
                opensSystem = true,
                showTiedFret = ShowTiedFret.TIE,
                parenthesize = ParenthesizeTiedFret.START_OF_SYSTEM
            )
        )
        assertTrue(
            "con `START_OF_SYSTEM` y el compás a mitad de sistema, no",
            TabTiedFret.shouldHideFret(
                tiedFromPrevious = true,
                atMeasureStart = true,
                opensSystem = false,
                showTiedFret = ShowTiedFret.TIE,
                parenthesize = ParenthesizeTiedFret.START_OF_SYSTEM
            )
        )
    }

    @Test
    fun soloSeParentizaConSoloLaLigaduraYCuandoLaCifraSeVe() {
        // `tieBackParen = isTabStaff && tieBack && showTiedFret != TIE_AND_FRET && !shouldHideFret()`
        // (`chordlayout.cpp:3166-3169`).
        assertFalse(
            "con `TIE_AND_FRET` no hay paréntesis",
            TabTiedFret.shouldParenthesize(
                tiedFromPrevious = true, atMeasureStart = true, opensSystem = true,
                showTiedFret = ShowTiedFret.TIE_AND_FRET,
                parenthesize = ParenthesizeTiedFret.START_OF_SYSTEM
            )
        )
        assertTrue(
            "con `TIE` y la cifra visible, sí",
            TabTiedFret.shouldParenthesize(
                tiedFromPrevious = true, atMeasureStart = true, opensSystem = true,
                showTiedFret = ShowTiedFret.TIE,
                parenthesize = ParenthesizeTiedFret.START_OF_SYSTEM
            )
        )
        assertFalse(
            "si la cifra está oculta no se parentiza nada",
            TabTiedFret.shouldParenthesize(
                tiedFromPrevious = true, atMeasureStart = false, opensSystem = true,
                showTiedFret = ShowTiedFret.TIE,
                parenthesize = ParenthesizeTiedFret.START_OF_SYSTEM
            )
        )
    }

    @Test
    fun elArcoSeOmiteSoloConNada() {
        // `SlurTieLayout::tieSegmentShouldBeSkipped` (`slurtielayout.cpp:1553-1563`).
        assertTrue(TabTiedFret.shouldDrawTie(ShowTiedFret.TIE_AND_FRET))
        assertTrue(TabTiedFret.shouldDrawTie(ShowTiedFret.TIE))
        assertFalse(TabTiedFret.shouldDrawTie(ShowTiedFret.NONE))
    }

    // --- La maquetación completa ---------------------------------------------------------------

    @Test
    fun conCifraYLigaduraSeVenLasCuatroCifrasYLosTresArcos() {
        // `tiedToNext` en las tres primeras columnas: la cuarta **recibe** la ligadura de la tercera.
        val dibujo = dibujo(negras(true, true, true, false))
        assertEquals("las cuatro cifras se ven", listOf("0", "0", "0", "0"), cifras(dibujo))
        assertEquals("tres arcos, uno por ligadura", 3, arcos(dibujo))
    }

    @Test
    fun conNadaDesaparecenLaCifraLigadaYElArco() {
        // Con `NONE` la cifra de la nota que **recibe** la ligadura no se pinta y el arco tampoco: la
        // ligadura desaparece del grabado (`slurtielayout.cpp:1553-1563`).
        val dibujo = dibujo(negras(true, true, true, false), showTiedFret = ShowTiedFret.NONE)
        assertEquals("solo la primera cifra sobrevive", listOf("0"), cifras(dibujo))
        assertEquals("y no queda ningún arco", 0, arcos(dibujo))
    }

    @Test
    fun conSoloLaLigaduraLaCifraRecordadaVaEntreParentesis() {
        // Tres compases encadenados por ligaduras. El compás 2 **abre sistema** (`forceSystemBreak`),
        // así que su cifra ligada se recuerda entre paréntesis; el compás 3 no lo abre, así que la
        // suya desaparece. Es la regla de `Note::shouldHideFret()` (`note.cpp:1544-1556`): con
        // `START_OF_SYSTEM` solo se recuerda al abrir sistema.
        val dibujo = dibujo(
            negras(false, false, false, true),
            negras(false, false, false, true).copy(forceSystemBreak = true),
            negras(false, false, false, false),
            showTiedFret = ShowTiedFret.TIE,
            parenthesize = ParenthesizeTiedFret.START_OF_SYSTEM
        )
        assertEquals("la ligadura parte el dibujo en dos sistemas", 2, dibujo.size)
        assertEquals(
            "solo la cifra que abre sistema se recuerda entre paréntesis",
            listOf("0", "0", "0", "0", "(0)", "0", "0", "0", "0", "0", "0"),
            cifras(dibujo)
        )
        assertEquals("los arcos se siguen dibujando", 2, arcos(dibujo))
    }

    @Test
    fun conSoloLaLigaduraYRecordarAlAbrirCompasSeVeEnCadaCompas() {
        // Dos compases, cada uno con una ligadura que **cruza** al siguiente: con `START_OF_MEASURE`
        // la cifra se recuerda al abrir el compás 2, aunque no abra sistema.
        val dibujo = dibujo(
            negras(false, false, false, true),
            negras(false, false, false, false),
            showTiedFret = ShowTiedFret.TIE,
            parenthesize = ParenthesizeTiedFret.START_OF_MEASURE
        )
        assertEquals(
            "la cifra del compás 2 se recuerda entre paréntesis",
            listOf("0", "0", "0", "0", "(0)", "0", "0", "0"),
            cifras(dibujo)
        )
    }

    @Test
    fun conSoloLaLigaduraYRecordarAlAbrirSistemaNoSeVeAlAbrirCompas() {
        // El mismo caso con `START_OF_SYSTEM`: el compás 2 **no** abre sistema, así que su cifra
        // ligada no se recuerda.
        val dibujo = dibujo(
            negras(false, false, false, true),
            negras(false, false, false, false),
            showTiedFret = ShowTiedFret.TIE,
            parenthesize = ParenthesizeTiedFret.START_OF_SYSTEM
        )
        assertEquals(
            "sin abrir sistema no hay recordatorio",
            listOf("0", "0", "0", "0", "0", "0", "0"),
            cifras(dibujo)
        )
    }

    @Test
    fun laLigaduraQueCruzaDeSistemaSeDetecta() {
        // `forceSystemBreak` en el compás 2: la ligadura del compás 1 al 2 cruza de sistema, y la
        // cifra del compás 2 se recuerda porque **abre** sistema.
        val dibujo = dibujo(
            negras(false, false, false, true),
            negras(false, false, false, false).copy(forceSystemBreak = true),
            showTiedFret = ShowTiedFret.TIE,
            parenthesize = ParenthesizeTiedFret.START_OF_SYSTEM
        )
        assertEquals("la ligadura cruza de sistema", 2, dibujo.size)
        assertEquals(
            "la cifra que abre el sistema 2 se recuerda entre paréntesis",
            listOf("0", "0", "0", "0", "(0)", "0", "0", "0"),
            cifras(dibujo)
        )
    }

    @Test
    fun tiedFromPreviousMarcaLaNotaQueRecibeLaLigadura() {
        // La pasada hacia delante de [TabTiedFret.tiedFromPrevious] tiene que coincidir con
        // [TablatureEditing.isTiedFromPrevious], que es la que usa la reproducción.
        val measures = listOf(
            negras(false, true, false, false),
            negras(false, false, false, false)
        )
        val marcadas = TabTiedFret.tiedFromPrevious(measures)
        assertEquals("solo la columna 2 del compás 1 recibe la ligadura", 1, marcadas.size)
        assertTrue(TabTieKey(0, 2, 1) in marcadas)
        assertTrue(
            "y coincide con la reproducción",
            TablatureEditing.isTiedFromPrevious(measures, 0, 2, 1)
        )
        assertFalse(
            "la columna 1 no la recibe",
            TablatureEditing.isTiedFromPrevious(measures, 0, 1, 1)
        )
    }
}
