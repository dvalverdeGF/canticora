package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.score.ScoreTimeSignature
import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabHairpin
import com.grafitto.laudate.domain.model.tablature.TabHairpinType
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

/** Pruebas del reparto en sistemas (filas de compases) de la tablatura. */
class TablatureSystemsLayoutTest {

    private fun tab(measureCount: Int, resolution: TabResolution = TabResolution.QUARTER, tuning: Tuning = Tuning.GUITAR) =
        Tablature(
            id = "t",
            songId = "song-1",
            title = "T",
            instrument = tuning.name,
            tuning = tuning,
            time = ScoreTimeSignature("4", 4),
            resolution = resolution,
            measures = TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), resolution, measureCount, withGuideColumns = true)
        )

    @Test
    fun allMeasuresFitInOneSystem_whenWidthIsLarge() {
        val systems = TablatureSystemsLayout.layout(tab(measureCount = 2), maxWidth = 5000f)
        assertEquals(1, systems.size)
        assertEquals(listOf(0, 1), systems[0].measureIndices)
    }

    @Test
    fun measuresWrapToNextSystem_whenTheyDoNotFit() {
        // 4 compases, cada uno de 4 casillas a 20px = 80px; disponible ~ 1 compás por fila.
        val settings = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f)
        val systems = TablatureSystemsLayout.layout(tab(measureCount = 4), maxWidth = 110f, settings = settings)
        // Cada sistema lleva 1 compás; los índices van en orden y completos.
        assertEquals(4, systems.size)
        assertEquals(listOf(0), systems[0].measureIndices)
        assertEquals(listOf(3), systems[3].measureIndices)
    }

    @Test
    fun systemsAreStackedVerticallyWithoutOverlap() {
        val settings = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f)
        val systems = TablatureSystemsLayout.layout(tab(measureCount = 3), maxWidth = 110f, settings = settings)
        // El segundo sistema empieza por debajo del primero.
        assertTrue(systems[1].topY >= systems[0].topY + systems[0].height)
    }

    @Test
    fun everyMeasureAppearsExactlyOnce() {
        val settings = TabLayoutSettings(cellWidth = 18f, labelWidth = 20f, padding = 8f)
        val systems = TablatureSystemsLayout.layout(tab(measureCount = 7), maxWidth = 200f, settings = settings)
        val covered = systems.flatMap { it.measureIndices }
        assertEquals((0 until 7).toList(), covered)
    }

    @Test
    fun atLeastOneMeasurePerSystem_evenIfItOverflows() {
        // Ancho ridículo: aun así cada sistema lleva al menos 1 compás (nunca 0).
        val settings = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f)
        val systems = TablatureSystemsLayout.layout(tab(measureCount = 3), maxWidth = 10f, settings = settings)
        assertEquals(3, systems.size)
        assertTrue(systems.all { it.measureIndices.size == 1 })
    }

    // ── Justificación y reparto equilibrado ─────────────────────────────────────────────────────
    //
    // MuseScore **justifica** cada sistema hasta llenar el ancho disponible
    // (`SystemLayout::shouldBeJustified`), salvo el **último** si no llega a `lastSystemFillLimit`
    // (0,3). MuseScore es **greedy** y **no** reequilibra; aquí sí, pero atado a ese mismo umbral.

    /** Ajustes estrechos: la cabecera reserva 48 px (8 de margen + 21 de clave + 19 de compás). */
    private val narrow = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f)

    /**
     * Ancho de la **cabecera** del primer sistema con [narrow]: los **dos** márgenes de página (D-13,
     * el margen va a izquierda y a derecha) más la clave, el compás y el **aire de arranque** del
     * sistema (`systemHeaderMinStartOfSystemDistance`, 1,25 espacios, F-04.quater).
     */
    private val headerPx: Float =
        narrow.pageMargin * 2f + narrow.labelWidth * 1.05f + narrow.labelWidth * 0.95f +
            narrow.staffSpace * TablatureSystemsLayout.SYSTEM_HEADER_MIN_START_SPACES

    /**
     * **Aire de la cabecera** (clave + compás + aire de arranque) con [narrow]. En MuseScore la
     * cabecera va **dentro** del sistema (es el `minLeft` del primer segmento del compás 1), así que
     * ocupa el principio del compás 1 y **no** se estira: el ancho disponible para las columnas es
     * `maxWidth − headerPx − headerSpacePx`.
     */
    private val headerSpacePx: Float =
        narrow.labelWidth * 1.05f + narrow.labelWidth * 0.95f +
            narrow.staffSpace * TablatureSystemsLayout.SYSTEM_HEADER_MIN_START_SPACES

    /**
     * Ancho de un compás de [quarters] negras con [narrow]: sus columnas **más el aire de las dos
     * barras** (`barNoteDistance` + `noteBarDistance` de MuseScore, 2,75 espacios).
     */
    private fun measurePx(quarters: Int): Float = quarters * narrow.cellWidth +
        narrow.staffSpace * (TablatureSystemsLayout.BAR_NOTE_SPACES + TablatureSystemsLayout.NOTE_BAR_SPACES)

    /** Tablatura de compases con el nº de negras indicado (una negra = 20 px con [narrow]). */
    private fun tabOf(vararg quarterCounts: Int) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
        measures = quarterCounts.map { n -> TabMeasure(slots = List(n) { TabSlot() }) }
    )

    @Test
    fun elSistemaSeJustificaHastaLlenarElAnchoDisponible() {
        // (D-9) El **primer** sistema lleva clave y compás (48 px de cabecera); los demás no, así que
        // disponen de más ancho y se estiran más. MuseScore justifica cada sistema a **su** ancho.
        val m = measurePx(4)
        val maxWidth = headerPx + headerSpacePx + 2.5f * m
        val systems = TablatureSystemsLayout.layout(tab(measureCount = 4), maxWidth = maxWidth, settings = narrow)
        assertEquals(2, systems.size)
        assertEquals(listOf(0, 1), systems[0].measureIndices)
        assertEquals(listOf(2, 3), systems[1].measureIndices)
        assertEquals("el primer sistema se estira ×1,25", 1.25f, systems[0].stretch, 0.001f)
        assertEquals(
            "la rejilla del primer sistema llena su ancho (con cabecera)",
            2.5f * m + headerSpacePx,
            systems[0].drawing.gridRight - systems[0].drawing.gridLeft,
            0.01f
        )
        // (D-76) El segundo sistema se estira **más** que el primero, pero con el **tope** de
        // [TablatureSystemsLayout.MAX_JUSTIFY_STRETCH]: sin él se estiraba ×2,16 y la tablatura se
        // veía más ancha al reducir el zoom.
        assertEquals(
            "el segundo sistema se estira hasta el tope",
            TablatureSystemsLayout.MAX_JUSTIFY_STRETCH,
            systems[1].stretch,
            0.001f
        )
        assertEquals(
            "la rejilla del segundo sistema mide su ancho natural por el tope",
            2f * m * TablatureSystemsLayout.MAX_JUSTIFY_STRETCH,
            systems[1].drawing.gridRight - systems[1].drawing.gridLeft,
            0.01f
        )
        assertTrue("el segundo sistema se estira más", systems[1].stretch > systems[0].stretch)
    }

    @Test
    fun laClaveYElCompasSoloSeDibujanEnElPrimerSistema() {
        // (D-9) `hideTabClefAfterFirst = true` en MuseScore: en tablatura la clave `TAB` y el compás
        // van **solo** en el primer sistema. El `salmo2.pdf` lo confirma (sistema 1 arranca en
        // x = 281 px con clave y compás; los otros tres en x = 177 px, sin ninguno de los dos).
        val systems = TablatureSystemsLayout.layout(tab(measureCount = 4), maxWidth = 248f, settings = narrow)
        val primero = systems[0].drawing.texts
        assertTrue("el primer sistema lleva la clave", primero.any { it.kind == TabTextKind.TAB_CLEF })
        assertTrue(
            "el primer sistema lleva el compás",
            primero.any { it.kind == TabTextKind.TIME_NUMERATOR }
        )
        systems.drop(1).forEachIndexed { i, system ->
            val textos = system.drawing.texts
            assertTrue(
                "el sistema ${i + 2} no repite la clave",
                textos.none { it.kind == TabTextKind.TAB_CLEF }
            )
            assertTrue(
                "el sistema ${i + 2} no repite el compás",
                textos.none { it.kind == TabTextKind.TIME_NUMERATOR }
            )
        }
    }

    @Test
    fun laCabeceraSePuedeRepetirEnTodosLosSistemas() {
        // La opción existe para quien la quiera (`headerOnEverySystem`), pero **no** es el defecto:
        // MuseScore no la usa en tablatura.
        val conCabecera = narrow.copy(staff = narrow.staff.copy(headerOnEverySystem = true))
        val systems = TablatureSystemsLayout.layout(tab(measureCount = 4), maxWidth = 248f, settings = conCabecera)
        systems.forEach { system ->
            assertTrue(
                "con la opción activa todos llevan clave",
                system.drawing.texts.any { it.kind == TabTextKind.TAB_CLEF }
            )
        }
    }

    @Test
    fun elUmbralDeLlenadoDelUltimoSistemaEsElDeMuseScore() {
        assertEquals("el umbral es el de MuseScore", 0.3f, TablatureSystemsLayout.LAST_SYSTEM_FILL_LIMIT, 0.0001f)
        // Un compás de 4 negras mide `m` px. Se eligen dos anchos a cada lado del umbral: uno en el
        // que el compás llena menos de 0,3 (no se estira) y otro en el que llena más (sí se estira).
        val m = measurePx(4)
        val porDebajo = TablatureSystemsLayout.layout(
            tab(measureCount = 1),
            maxWidth = headerPx + headerSpacePx + m / 0.25f,
            settings = narrow
        )
        assertEquals("llena 0,25 < 0,3 → sin estirar", 1f, porDebajo.single().stretch, 0.001f)
        val porEncima = TablatureSystemsLayout.layout(
            tab(measureCount = 1),
            maxWidth = headerPx + headerSpacePx + m / 0.4f,
            settings = narrow
        )
        // (D-76) El estiramiento tiene **tope** ×1,5: sin él, un compás suelto en un ancho grande se
        // estiraba ×2,5 y las cifras se separaban mientras las cuerdas seguían juntas.
        assertEquals(
            "llena 0,4 ≥ 0,3 → estirado, pero con el tope de ×1,5",
            TablatureSystemsLayout.MAX_JUSTIFY_STRETCH,
            porEncima.single().stretch,
            0.001f
        )
    }

    @Test
    fun reducirElZoomNoEnsanchaLaTablatura() {
        // (D-76) El mismo compás en un ancho **el doble** de su tamaño natural: sin tope se estiraría
        // ×2 y la tablatura se vería más ancha al encogerla. Con el tope, el sistema se queda a su
        // tamaño natural y deja aire a la derecha.
        val m = measurePx(4)
        val systems = TablatureSystemsLayout.layout(
            tab(measureCount = 1),
            maxWidth = headerPx + headerSpacePx + m * 2f,
            settings = narrow
        )
        assertEquals(
            "el sistema no se estira más de ×1,5",
            TablatureSystemsLayout.MAX_JUSTIFY_STRETCH,
            systems.single().stretch,
            0.001f
        )
        assertTrue(
            "la rejilla no llena el ancho disponible",
            systems.single().drawing.gridRight - systems.single().drawing.gridLeft <
                headerPx + headerSpacePx + m * 2f
        )
    }

    @Test
    fun elUltimoSistemaNoSeJustificaSiNoLlegaAlUmbral() {
        // Un compás de 4 negras y otro de 1 en un ancho que solo deja sitio al primero: el último
        // llena menos de 0,3 y se queda como está, mientras el primero sí se estira.
        val cuatro = measurePx(4)
        val una = measurePx(1)
        val maxWidth = headerPx + headerSpacePx + 1.2f * cuatro
        val systems = TablatureSystemsLayout.layout(tabOf(4, 1), maxWidth = maxWidth, settings = narrow)
        assertEquals(2, systems.size)
        assertEquals(listOf(0), systems[0].measureIndices)
        assertEquals(listOf(1), systems[1].measureIndices)
        assertEquals(1.2f, systems[0].stretch, 0.001f)
        assertEquals("el último sistema no se estira", 1f, systems[1].stretch, 0.001f)
        assertEquals(una, systems[1].drawing.gridRight - systems[1].drawing.gridLeft, 0.01f)
    }

    @Test
    fun elRepartoEquilibradoEvitaElCompasSueltoEnElUltimoSistema() {
        // El greedy de MuseScore daría 3+3+1 (último sistema con un compás suelto, por debajo del
        // umbral); el reequilibrio le pasa un compás del anterior y deja 3+2+2. Los sistemas 2+ no
        // llevan cabecera (D-9), así que su ancho es `maxWidth − 2·pageMargin`.
        val m = measurePx(4)
        // El ancho se elige para que el greedy deje **3 + 3 + 1**: el primer sistema cabe 3 compases
        // (3,02·m de hueco para columnas) y los siguientes, sin cabecera, también 3 pero no 4.
        val systems = TablatureSystemsLayout.layout(
            tab(measureCount = 7),
            maxWidth = headerPx + headerSpacePx + 3.02f * m,
            settings = narrow
        )
        assertEquals(3, systems.size)
        assertEquals(listOf(0, 1, 2), systems[0].measureIndices)
        assertEquals(listOf(3, 4), systems[1].measureIndices)
        assertEquals(listOf(5, 6), systems[2].measureIndices)
        assertTrue("ningún sistema se queda con un compás suelto", systems.all { it.measureIndices.size > 1 })
    }

    @Test
    fun laJustificacionNuncaEncogeUnCompasMasAnchoQueElDisponible() {
        // Un compás más ancho que el hueco disponible: se sale del sistema, como en MuseScore, en vez
        // de comprimirse y solapar las cifras.
        val m = measurePx(4)
        val systems = TablatureSystemsLayout.layout(
            tab(measureCount = 1),
            maxWidth = headerPx + headerSpacePx + m * 0.2f,
            settings = narrow
        )
        assertEquals(1, systems.size)
        assertEquals(1f, systems.single().stretch, 0.001f)
        assertEquals(
            m + headerSpacePx,
            systems.single().drawing.gridRight - systems.single().drawing.gridLeft,
            0.01f
        )
    }

    @Test
    fun notesArePlacedPerSystem() {
        val measures = TablatureEditing.emptyMeasures(ScoreTimeSignature("4", 4), TabResolution.QUARTER, 4, withGuideColumns = true)
        val withNotes = TablatureEditing.togglePluck(measures, 0, 0, stringNumber = 1, fret = 3)
            .let { TablatureEditing.togglePluck(it, 3, 0, stringNumber = 1, fret = 5) }
        val tab = tab(4).copy(measures = withNotes)
        val settings = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f)
        val systems = TablatureSystemsLayout.layout(tab, maxWidth = 110f, settings = settings)
        // El traste 3 va en el primer sistema y el 5 en el cuarto.
        assertTrue(systems[0].drawing.texts.any { it.kind == TabTextKind.FRET && it.text == "3" })
        assertTrue(systems[3].drawing.texts.any { it.kind == TabTextKind.FRET && it.text == "5" })
    }

// ── Saltos de sistema forzados (Fase 3 de paridad) ─────────────────────────

    @Test
    fun unCompasConSaltoForzadoEmpiezaUnSistemaNuevo() {
        // Con ancho de sobra los 4 compases cabrían en un sistema; el salto forzado del compás 2
        // obliga a partir ahí, como el «Crear sistema» de MuseScore.
        val base = tab(measureCount = 4)
        val conSalto = base.copy(
            measures = TablatureEditing.setForceSystemBreak(base.measures, 2, true)
        )
        val systems = TablatureSystemsLayout.layout(conSalto, maxWidth = 5000f)

        assertEquals(2, systems.size)
        assertEquals(listOf(0, 1), systems[0].measureIndices)
        assertEquals(listOf(2, 3), systems[1].measureIndices)
    }

    @Test
    fun elSaltoForzadoNoSeDeshaceConElRepartoEquilibrado() {
        // El reequilibrio del último sistema no puede mover compases **antes** de un salto forzado.
        val base = tab(measureCount = 7)
        val conSalto = base.copy(
            measures = TablatureEditing.setForceSystemBreak(base.measures, 5, true)
        )
        val systems = TablatureSystemsLayout.layout(conSalto, maxWidth = 348f, settings = narrow)

        val inicioDelUltimo = systems.last().measureIndices.first()
        assertEquals("el último sistema empieza en el compás marcado", 5, inicioDelUltimo)
    }

    @Test
    fun sinSaltosForzadosElRepartoNoCambia() {
        val systems = TablatureSystemsLayout.layout(tab(measureCount = 4), maxWidth = 5000f)
        assertEquals(1, systems.size)
    }

    // ── Aire vertical de las franjas de símbolos (D-15) ───────────────────────

    @Test
    fun laFranjaDeSimbolosSeReservaPorSistemaYNoPorDocumento() {
        // Un sistema con tres articulaciones apiladas y otro sin ninguna, en el **mismo** documento.
        // Si la franja `SYMBOLS` se reservara por documento, el sistema vacío arrastraría el aire del
        // cargado; MuseScore la reserva por sistema (`TabStaffZones.of` recibe los compases del
        // sistema, no los del documento).
        val cargado = TabMeasure(
            slots = listOf(
                TabSlot(
                    strings = listOf(
                        TabPluck(
                            TabPosition(1, 3),
                            articulations = setOf(
                                TabArticulation.ACCENT,
                                TabArticulation.MARCATO,
                                TabArticulation.STACCATO
                            )
                        )
                    )
                )
            )
        )
        // El salto forzado va en el compás que **empieza** el sistema nuevo (el `Return` de MuseScore).
        val vacio = TabMeasure(slots = listOf(TabSlot()), forceSystemBreak = true)
        val tablature = Tablature(
            id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
            measures = listOf(cargado, vacio)
        )

        val systems = TablatureSystemsLayout.layout(tablature, maxWidth = 5000f, settings = narrow)
        assertEquals("el salto de sistema debe partir el documento en dos", 2, systems.size)

        val conSimbolos = systems[0]
        val sinSimbolos = systems[1]
        assertTrue(
            "el sistema con tres articulaciones es más alto que el vacío: " +
                "${conSimbolos.height} vs ${sinSimbolos.height}",
            conSimbolos.height > sinSimbolos.height
        )
        // La reserva **exacta** de la pila se mide en
        // `laFranjaDeSimbolosNoSeContagiaDelNumeroDeCompas`, con los números de compás apagados y
        // los dos sistemas con notas: aquí el sistema vacío no gasta franja de figuras y sí reserva
        // el aire de su número, así que su alto no es comparable con el del primero.
    }

    @Test
    fun laFranjaDeSimbolosNoSeContagiaDelNumeroDeCompas() {
        // (D-55) El sistema que abre con número reserva el aire de `measureNumberPosAbove` en la
        // franja `TOP`, así que su alto **no** es comparable con el del primer sistema, que no lo
        // rotula. Con los números apagados y **notas en los dos** sistemas, la única diferencia es la
        // pila de articulaciones, y ahí se ve que la reserva es **exacta**: tres franjas de 1,1
        // espacios, ni una más.
        val sinNumeros = narrow.copy(staff = narrow.staff.copy(showMeasureNumbers = false))
        val cargado = TabMeasure(
            slots = listOf(
                TabSlot(
                    strings = listOf(
                        TabPluck(
                            TabPosition(1, 3),
                            articulations = setOf(
                                TabArticulation.ACCENT,
                                TabArticulation.MARCATO,
                                TabArticulation.STACCATO
                            )
                        )
                    )
                )
            )
        )
        val simple = TabMeasure(
            slots = listOf(TabSlot(strings = listOf(TabPluck(1, 3)))),
            forceSystemBreak = true
        )
        val tablature = Tablature(
            id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
            measures = listOf(cargado, simple)
        )

        val systems = TablatureSystemsLayout.layout(tablature, maxWidth = 5000f, settings = sinNumeros)
        assertEquals(2, systems.size)
        // (D-59) La franja de símbolos reserva además el **aire de base** de la pila (lo que la cifra
        // sube sobre la línea de la cuerda 1), que solo gasta el sistema que tiene articulaciones.
        val staffSpace = sinNumeros.stringSpacing / TabRhythmGeometry.STRING_SPACING_SPACES
        val tresFranjas = sinNumeros.stringSpacing * TabArticulationStack.TIER_SPACES * 3f +
            TabArticulationStack.baseClearanceSpaces() * staffSpace
        val diferencia = systems[0].height - systems[1].height
        assertEquals("reserva exactamente tres franjas", tresFranjas, diferencia, 0.01f)
    }

    // ── Ancho imprimible y «ajustar al ancho» (D-13/D-14) ─────────────────────

    @Test
    fun elMargenDePaginaDejaAireSimetricoALosDosLados() {
        // MuseScore no dibuja de borde a borde: el `salmo2.pdf` deja 7,1 % a cada lado. El margen
        // tiene que entrar **por los dos** lados, no solo por la izquierda.
        val settings = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f, pageMargin = 100f)
        val system = TablatureSystemsLayout.layout(tab(measureCount = 6), maxWidth = 1200f, settings = settings).single()

        val izquierda = system.drawing.lines.minOf { it.x1 }
        val derecha = system.drawing.lines.maxOf { it.x2 }
        // (F-04.quater) Las cuerdas arrancan **después** de la cabecera (clave + compás + aire de
        // arranque), que va **encima** del pentagrama, como en MuseScore.
        val cabecera = settings.labelWidth * 1.05f + settings.labelWidth * 0.95f +
            settings.staffSpace * TablatureSystemsLayout.SYSTEM_HEADER_MIN_START_SPACES
        assertEquals("el pentagrama arranca tras la cabecera", 100f + cabecera, izquierda, 0.01f)
        assertEquals("y acaba en el margen contrario", 1100f, derecha, 0.01f)
    }

    @Test
    fun elMargenDePaginaNoEsElAireVerticalDelSistema() {
        // `padding` es el aire de arriba de la primera franja; `pageMargin` es el de la hoja. Si se
        // confunden, cada pentagrama se lleva 100 px de aire encima.
        val base = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f)
        val conMargen = base.copy(pageMargin = 100f)
        val a = TablatureSystemsLayout.layout(tab(measureCount = 2), maxWidth = 1000f, settings = base).single()
        val b = TablatureSystemsLayout.layout(tab(measureCount = 2), maxWidth = 1000f, settings = conMargen).single()

        assertEquals("el alto del sistema no cambia", a.height, b.height, 0.01f)
        assertEquals("la primera cuerda sigue a la misma altura", a.drawing.lines.first().y1, b.drawing.lines.first().y1, 0.01f)
    }

    @Test
    fun unSistemaDeSoloSilenciosNoReservaLaFranjaDeFiguras() {
        // (D-55) Los **silencios** se dibujan **dentro** del pentagrama (`TabRhythmGeometry.restLine`),
        // no debajo: un sistema de compases vacíos no gasta plica. Antes la franja `FIGURE` se
        // reservaba con solo `showFigures` y dejaba **20 pt de aire muerto** por sistema. Medido del
        // `salmo2`: la fila de los sistemas 2-4 medía 67,6 pt donde MuseScore gasta 52,8.
        val settings = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f)
        val vacio = TablatureSystemsLayout.layout(tab(measureCount = 2), maxWidth = 1000f, settings = settings).single()
        val conNotas = TablatureSystemsLayout.layout(
            tab(measureCount = 2).copy(
                measures = List(2) { m ->
                    TabMeasure(
                        slots = List(4) { s ->
                            if (m == 0 && s == 0) TabSlot(strings = listOf(TabPluck(1, 0))) else TabSlot()
                        }
                    )
                }
            ),
            maxWidth = 1000f,
            settings = settings
        ).single()

        // (D-91) La franja mide **solo** lo que cuelga (la plica con su bandera); el hueco de
        // arranque es aire dentro de ese trazo, no sitio extra.
        val franja = settings.stringSpacing * TabRhythmGeometry.STEM_LENGTH /
            TabRhythmGeometry.STRING_SPACING_SPACES
        assertEquals(
            "el sistema sin notas ahorra la franja de figuras",
            franja,
            conNotas.height - vacio.height,
            0.5f
        )
    }

    @Test
    fun elAnchoNaturalEsLaSumaDeLosCompasesSinJustificar() {
        val settings = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f)
        val uno = TablatureSystemsLayout.naturalWidth(tab(measureCount = 1), settings)
        val dos = TablatureSystemsLayout.naturalWidth(tab(measureCount = 2), settings)
        val tres = TablatureSystemsLayout.naturalWidth(tab(measureCount = 3), settings)

        // El ancho natural crece con los compases y lo hace de forma **lineal**: cada compás añade
        // exactamente lo mismo (la cabecera es constante).
        assertTrue("dos compases ocupan más que uno", dos > uno)
        assertEquals("el segundo compás añade su ancho", dos - uno, tres - dos, 0.01f)
    }

    @Test
    fun ajustarAlAnchoLlenaElAnchoImprimible() {
        val settings = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f)
        val tablature = tab(measureCount = 4)
        val viewport = 1000f
        val escala = TablatureSystemsLayout.fitToWidthScale(tablature, viewport, settings)

        // A esa escala, el ancho natural iguala el ancho imprimible del lienzo.
        val imprimible = viewport * (1f - 2f * TabLayoutSettings.PAGE_MARGIN_RATIO)
        assertEquals(imprimible, TablatureSystemsLayout.naturalWidth(tablature, settings) * escala, 0.01f)
    }

    @Test
    fun ajustarAlAnchoConMasCompasesDaMenosZoom() {
        val settings = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f)
        val pocos = TablatureSystemsLayout.fitToWidthScale(tab(measureCount = 2), 1000f, settings)
        val muchos = TablatureSystemsLayout.fitToWidthScale(tab(measureCount = 8), 1000f, settings)

        assertTrue("más compases ⇒ menos zoom", muchos < pocos)
    }

    @Test
    fun ajustarAlAnchoSinAnchoMedibleDevuelveCienPorCien() {
        val settings = TabLayoutSettings(cellWidth = 20f, labelWidth = 20f, padding = 8f)
        assertEquals(1f, TablatureSystemsLayout.fitToWidthScale(tab(measureCount = 2), 0f, settings), 0.0001f)
    }

    // ── Ligadura de expresión (`S` de MuseScore) ────────────────────────────────────────────────

    /** Tablatura de un compás con [count] columnas, todas con una nota en la cuerda 1. */
    private fun tabWithNotes(count: Int) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
        measures = listOf(
            TabMeasure(slots = List(count) { TabSlot(strings = listOf(TabPluck(TabPosition(1, 3)))) })
        )
    )

    @Test
    fun laLigaduraDeExpresionSeDibujaUnaVezPorCadena() {
        // Tres casillas ligadas seguidas = **una** ligadura larga, no dos arcos (MuseScore).
        val tablature = tabWithNotes(3).let { t ->
            t.copy(
                measures = listOf(
                    t.measures[0].copy(
                        slots = t.measures[0].slots.mapIndexed { i, slot ->
                            if (i < 2) slot.copy(slurToNext = true) else slot
                        }
                    )
                )
            )
        }
        val systems = TablatureSystemsLayout.layout(tablature, maxWidth = 2000f, settings = narrow)
        val slurs = systems[0].drawing.glyphs.filter { it.kind == TabGlyphKind.SLUR }
        assertEquals("una sola ligadura para toda la cadena", 1, slurs.size)
    }

    @Test
    fun laLigaduraDeExpresionUneLaCasillaInicialConLaSiguienteAlFinalDeLaCadena() {
        val tablature = tabWithNotes(3).let { t ->
            t.copy(
                measures = listOf(
                    t.measures[0].copy(
                        slots = t.measures[0].slots.mapIndexed { i, slot ->
                            if (i < 2) slot.copy(slurToNext = true) else slot
                        }
                    )
                )
            )
        }
        val systems = TablatureSystemsLayout.layout(tablature, maxWidth = 2000f, settings = narrow)
        val slur = systems[0].drawing.glyphs.single { it.kind == TabGlyphKind.SLUR }
        val boxes = systems[0].drawing.columns.getValue(0)
        assertEquals("arranca en el centro de la primera casilla", boxes[0].centerX, slur.from.x, 0.5f)
        assertEquals("acaba en el centro de la casilla que cierra la cadena", boxes[2].centerX, slur.to.x, 0.5f)
    }

    @Test
    fun laLigaduraDeExpresionVaPorEncimaDeLaCuerdaSuperior() {
        val tablature = tabWithNotes(2).let { t ->
            t.copy(
                measures = listOf(
                    t.measures[0].copy(
                        slots = t.measures[0].slots.mapIndexed { i, slot ->
                            if (i == 0) slot.copy(slurToNext = true) else slot
                        }
                    )
                )
            )
        }
        val systems = TablatureSystemsLayout.layout(tablature, maxWidth = 2000f, settings = narrow)
        val slur = systems[0].drawing.glyphs.single { it.kind == TabGlyphKind.SLUR }
        val topString = systems[0].drawing.gridTop
        assertTrue("el arco queda por encima de la primera cuerda", slur.y < topString)
    }

    @Test
    fun laLigaduraDeExpresionReservaSuFranjaYNoSeSaleDelSistema() {
        // El arco se dibuja **por encima** de la pila de símbolos: si la franja no reserva su aire, la
        // panza se sale por arriba del sistema y el arco aparece **cortado** en el borde del lienzo.
        val sinLigadura = tabWithNotes(3)
        val conLigadura = sinLigadura.copy(
            measures = listOf(
                sinLigadura.measures[0].copy(
                    slots = sinLigadura.measures[0].slots.mapIndexed { i, slot ->
                        if (i < 2) slot.copy(slurToNext = true) else slot
                    }
                )
            )
        )
        val altoSin = TablatureSystemsLayout.layout(sinLigadura, maxWidth = 2000f, settings = narrow)
            .single().height
        val sistema = TablatureSystemsLayout.layout(conLigadura, maxWidth = 2000f, settings = narrow)
            .single()
        assertTrue(
            "la ligadura tiene que reservar aire: el sistema crece",
            sistema.height > altoSin
        )
        val slur = sistema.drawing.glyphs.single { it.kind == TabGlyphKind.SLUR }
        // La panza sube `0,75 · alto` sobre la línea base del arco; con el alto máximo posible
        // (`1,8 · unidad`) el punto más alto del arco sigue **dentro** del sistema.
        val unidad = narrow.staffSpace * 2.4f / 3.1f
        val panza = slur.y - 0.75f * (unidad * 1.8f)
        assertTrue(
            "la panza del arco no puede salirse por arriba del sistema (y=$panza, top=${sistema.topY})",
            panza >= sistema.topY
        )
    }

    @Test
    fun laLigaduraDeExpresionSeReanudaAlPrincipioDelSistemaSiguiente() {
        // Cadena que cruza el salto de sistema: el arco se corta en el borde derecho del primer
        // sistema y **vuelve a aparecer** al principio del segundo, como el `startOfSystem` de
        // MuseScore. Antes se cortaba y no se reanudaba.
        fun note(fret: Int, slur: Boolean = false) = TabSlot(
            strings = listOf(TabPluck(TabPosition(1, fret))),
            rhythmicFigure = RhythmFigure.QUARTER,
            slurToNext = slur
        )
        val tablature = Tablature(
            id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(listOf(note(3), note(5, slur = true))),
                TabMeasure(listOf(note(7), note(8)), forceSystemBreak = true)
            )
        )
        val systems = TablatureSystemsLayout.layout(tablature, maxWidth = 2000f, settings = narrow)
        assertEquals("el salto forzado tiene que dar dos sistemas", 2, systems.size)
        val primero = systems[0].drawing.glyphs.filter { it.kind == TabGlyphKind.SLUR }
        val segundo = systems[1].drawing.glyphs.filter { it.kind == TabGlyphKind.SLUR }
        assertEquals("el primer sistema dibuja el arco que se corta", 1, primero.size)
        assertEquals("el segundo sistema reanuda la ligadura", 1, segundo.size)
        assertEquals(
            "la continuación arranca en el borde izquierdo de la rejilla",
            systems[1].drawing.gridLeft,
            segundo.single().from.x,
            0.5f
        )
        assertEquals(
            "y acaba en la casilla que cierra la cadena",
            systems[1].drawing.columns.getValue(systems[1].measureIndices[0])[0].centerX,
            segundo.single().to.x,
            0.5f
        )
    }

    // ── Silencio de compás completo: `minMeasureWidth` de MuseScore ───────────────────────────────

    /**
     * Cabecera del primer sistema **sin** los márgenes de página (que [naturalWidth] no incluye):
     * clave, compás y el **aire de arranque** del sistema (F-04.quater).
     */
    private val headerOnlyPx: Float = narrow.labelWidth * 1.05f + narrow.labelWidth * 0.95f +
        narrow.staffSpace * TablatureSystemsLayout.SYSTEM_HEADER_MIN_START_SPACES

    /** Tablatura de [count] compases **vacíos**, cada uno con un solo silencio de compás completo. */
    private fun tabOfWholeRests(count: Int) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
        time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
        measures = List(count) {
            TabMeasure(slots = listOf(TabSlot(rhythmicFigure = RhythmFigure.WHOLE_REST)))
        }
    )

    @Test
    fun elMinimoDeMuseScoreEsUnSueloYNoElAnchoDelCompasVacio() {
        // `styleDef(minMeasureWidth, 8.0_sp)` es un **suelo**, no el ancho: MuseScore mide el
        // silencio de compás **por duración** y solo después aplica el mínimo. Con [narrow] el
        // `cellWidth` (20 px) es pequeño frente al espacio (13,33 px), así que la duración da
        // 45 px y el suelo de 8 espacios (106,67 px) es el que manda. Ver
        // [unCompasVacioSeMidePorDuracionYNoPorElMinimo] para el caso con la proporción real.
        val ancho = TablatureSystemsLayout.naturalWidth(tabOfWholeRests(1), narrow) - headerOnlyPx
        assertEquals(
            "el suelo de minMeasureWidth manda cuando la duración da menos",
            narrow.staffSpace * TablatureSystemsLayout.MIN_MEASURE_SPACES,
            ancho,
            0.01f
        )
    }

    /** Ajustes con la **proporción real** de MuseScore: `cellWidth` = 3,5 · espacio. */
    private fun museScoreSettings(staffSpace: Float = 20f) = TabLayoutSettings(
        cellWidth = TabSpacing.quarterWidth(staffSpace),
        stringSpacing = staffSpace * TabRhythmGeometry.STRING_SPACING_SPACES,
        labelWidth = staffSpace * 3.6f * 0.55f,
        padding = staffSpace * 3.6f * 0.18f
    )

    @Test
    fun unCompasVacioSeMidePorDuracionYNoPorElMinimo() {
        // (F-04.quater) El ancho natural de un compás vacío es `barNoteDistance` (1,25) **más** la
        // columna de la **redonda** (`3,5 · 4^0,585` = 7,875), o sea **9,125 espacios**: el silencio
        // se centra en ese hueco (`measurelayout.cpp:2212`) y no se suma el aire de la barra derecha.
        // Con 9,125 caben `102,89 / 9,125 = 11,28` → **11 compases** por sistema, como el PDF.
        val settings = museScoreSettings()
        val ancho = TablatureSystemsLayout.naturalWidth(tabOfWholeRests(1), settings) -
            (settings.labelWidth * 1.05f + settings.labelWidth * 0.95f +
                settings.staffSpace * TablatureSystemsLayout.SYSTEM_HEADER_MIN_START_SPACES)
        val espacios = ancho / settings.staffSpace
        assertEquals(
            "un compás vacío mide barNoteDistance + la columna de la redonda (9,125 espacios)",
            9.125f,
            espacios,
            0.01f
        )
        assertTrue(
            "y no el suelo de 8,0 espacios",
            espacios > TablatureSystemsLayout.MIN_MEASURE_SPACES
        )
    }

    @Test
    fun elSueloDeMuseScoreSigueMandandoEnUnCompasCorto() {
        // En 3/4 la columna de la blanca con puntillo mide `3,5 · 3^0,585` = 6,65 espacios, más
        // `barNoteDistance` (1,25) = 7,90, por debajo del suelo: ahí sí manda `minMeasureWidth`, como
        // en MuseScore.
        val settings = museScoreSettings()
        val tablatura = Tablature(
            id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("3", 4), resolution = TabResolution.QUARTER,
            measures = listOf(TabMeasure(slots = listOf(TabSlot(rhythmicFigure = RhythmFigure.WHOLE_REST))))
        )
        val ancho = TablatureSystemsLayout.naturalWidth(tablatura, settings) -
            (settings.labelWidth * 1.05f + settings.labelWidth * 0.95f +
                settings.staffSpace * TablatureSystemsLayout.SYSTEM_HEADER_MIN_START_SPACES)
        assertEquals(
            "en 3/4 el suelo de 8,0 espacios manda",
            settings.staffSpace * TablatureSystemsLayout.MIN_MEASURE_SPACES,
            ancho,
            0.01f
        )
    }

    @Test
    fun cabenOnceCompasesVaciosPorSistemaComoEnElPdf() {
        // El `salmo2.pdf` reparte los compases vacíos (6–32) en **11 / 11 / 5**. Se reproduce con el
        // ancho de página de MuseScore (120 espacios: 210 mm / 1,75 mm de spatium) y el margen del
        // 7,1 % (D-13). Sin cabecera, para que los tres sistemas tengan el mismo ancho disponible: en
        // el PDF el primero lleva los compases **con contenido** y por eso solo caben 5.
        val settings = museScoreSettings()
        val pageWidth = settings.staffSpace * 120f
        val systems = TablatureSystemsLayout.layout(
            tabOfWholeRests(27),
            maxWidth = pageWidth,
            settings = settings.copy(
                pageMargin = pageWidth * TabLayoutSettings.PAGE_MARGIN_RATIO,
                staff = TabStaffOptions(showClef = false, showTimeSignature = false)
            )
        )
        assertEquals(
            "el reparto en líneas del PDF es 11 / 11 / 5",
            listOf(11, 11, 5),
            systems.map { it.measureIndices.size }
        )
    }

    @Test
    fun unCompasConCuatroSilenciosDeNegraNoEsUnCompasVacio() {
        // Cuatro silencios de negra son cuatro columnas reales: se espacian por duración, como
        // MuseScore (solo el **silencio de compás** se centra y no ocupa ancho).
        val ancho = TablatureSystemsLayout.naturalWidth(tabOf(4), narrow) - headerOnlyPx
        assertEquals(measurePx(4), ancho, 0.01f)
    }

    @Test
    fun elSilencioDeCompasSeCentraEnElCompas() {
        val systems = TablatureSystemsLayout.layout(tabOfWholeRests(1), maxWidth = 1000f, settings = narrow)
        val caja = systems[0].drawing.measureBounds.single()
        val silencio = systems[0].drawing.rhythmMarks.single()
        // (F-04.quater) La caja del compás 1 incluye la cabecera del sistema, pero el silencio se
        // centra en el **hueco libre**, o sea a la derecha de la cabecera.
        assertEquals(
            "el silencio de compás va centrado en el hueco libre",
            (caja.left + headerSpacePx + caja.right) / 2f,
            silencio.x,
            1f
        )
    }

    @Test
    fun cabenMasCompasesVaciosPorSistemaQueLlenos() {
        // Es la razón de que la tablatura saliera más ancha que la de MuseScore: al espaciar los
        // compases vacíos como redondas cabían 8 por sistema en vez de 11.
        // Con el espaciado **real** de MuseScore (`DEFAULT_QUARTER_NOTE_SPACE = 3,5` espacios): con
        // los ajustes `narrow` la negra mide 1,5 espacios y el compás vacío sale **más ancho** que
        // uno de cuatro negras, que es un artefacto del test y no del modelo.
        val real = TabLayoutSettings(
            cellWidth = narrow.staffSpace * TabSpacing.QUARTER_WIDTH_SPACES,
            labelWidth = 20f,
            padding = 8f
        )
        val ancho = 1400f
        val vacios = TablatureSystemsLayout.layout(tabOfWholeRests(12), maxWidth = ancho, settings = real)
        val llenos = TablatureSystemsLayout.layout(
            Tablature(
                id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
                time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
                measures = List(12) { TabMeasure(slots = List(4) { TabSlot() }) }
            ),
            maxWidth = ancho,
            settings = real
        )
        assertTrue(
            "los compases vacíos ocupan menos y caben más por sistema (${vacios.size} < ${llenos.size})",
            vacios.size < llenos.size
        )
    }

    // ── Reguladores (hairpins): la cuña de crescendo / diminuendo ────────────────────────────────

    @Test
    fun elReguladorSeDibujaComoCunaEnLaFranjaDeLasDinamicas() {
        val tablature = Tablature(
            id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(
                    slots = listOf(TabSlot(strings = listOf(TabPluck(TabPosition(1, 3))))),
                    hairpin = TabHairpin(TabHairpinType.CRESCENDO, lengthMeasures = 1)
                )
            )
        )
        val system = TablatureSystemsLayout.layout(tablature, maxWidth = 2000f, settings = narrow).single()
        val wedge = system.drawing.glyphs.single { it.kind == TabGlyphKind.CRESCENDO }
        // La cuña **abre hacia la derecha**: nace en la primera columna y acaba en el borde del compás.
        assertTrue("la cuña tiene que tener ancho", wedge.to.x > wedge.from.x)
        assertTrue(
            "la cuña vive en la franja de las dinámicas, bajo la cuerda inferior",
            wedge.y > system.drawing.gridBottom
        )
    }

    @Test
    fun elReguladorAbarcaLosCompasesQueDeclara() {
        fun measure(hairpin: TabHairpin?) = TabMeasure(
            slots = listOf(TabSlot(strings = listOf(TabPluck(TabPosition(1, 3))))),
            hairpin = hairpin
        )
        val tablature = Tablature(
            id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
            measures = listOf(
                measure(TabHairpin(TabHairpinType.DIMINUENDO, lengthMeasures = 3)),
                measure(null),
                measure(null)
            )
        )
        val system = TablatureSystemsLayout.layout(tablature, maxWidth = 2000f, settings = narrow).single()
        val wedge = system.drawing.glyphs.single { it.kind == TabGlyphKind.DIMINUENDO }
        val columns = system.drawing.columns.getValue(0)
        val lastMeasureEnd = system.drawing.columns.getValue(2).last().centerX
        // Tres compases: la cuña llega al final del tercero, no al del primero.
        assertTrue("la cuña abarca los tres compases", wedge.to.x > lastMeasureEnd - columns.first().width)
    }

    @Test
    fun elReguladorNoSeSaleDelSistema() {
        fun measure(hairpin: TabHairpin?) = TabMeasure(
            slots = listOf(TabSlot(strings = listOf(TabPluck(TabPosition(1, 3))))),
            hairpin = hairpin
        )
        // La cuña declara 8 compases pero el sistema solo tiene 2: se recorta al borde.
        val tablature = Tablature(
            id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
            measures = listOf(
                measure(TabHairpin(TabHairpinType.CRESCENDO, lengthMeasures = 8)),
                measure(null)
            )
        )
        val system = TablatureSystemsLayout.layout(tablature, maxWidth = 2000f, settings = narrow).single()
        val wedge = system.drawing.glyphs.single { it.kind == TabGlyphKind.CRESCENDO }
        assertTrue(
            "la cuña no puede pasarse del borde derecho de la rejilla",
            wedge.to.x <= system.drawing.gridLeft + system.drawing.width + 0.5f
        )
    }

    @Test
    fun elReguladorReservaLaFranjaAunqueNoHayaDinamicaEscrita() {
        fun measure(hairpin: TabHairpin?) = TabMeasure(
            slots = listOf(TabSlot(strings = listOf(TabPluck(TabPosition(1, 3))))),
            hairpin = hairpin
        )
        val conCuña = Tablature(
            id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
            measures = listOf(measure(TabHairpin(TabHairpinType.CRESCENDO, 1)))
        )
        val sinCuña = conCuña.copy(measures = listOf(measure(null)))
        val altoCon = TablatureSystemsLayout.layout(conCuña, maxWidth = 2000f, settings = narrow).single().height
        val altoSin = TablatureSystemsLayout.layout(sinCuña, maxWidth = 2000f, settings = narrow).single().height
        assertTrue("la cuña necesita su franja bajo la pauta", altoCon > altoSin)
    }

    // ── Salto de página (`Ctrl`+`Return` de MuseScore) ──────────────────────────────────────────

    @Test
    fun elSaltoDePaginaTambienParteElSistemaYLoMarca() {
        fun note(fret: Int) = TabSlot(
            strings = listOf(TabPluck(TabPosition(1, fret))),
            rhythmicFigure = RhythmFigure.QUARTER
        )
        val tablature = Tablature(
            id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(listOf(note(3), note(5))),
                // El salto de página va en el compás que **abre** la hoja.
                TabMeasure(listOf(note(7), note(8)), forcePageBreak = true)
            )
        )

        val systems = TablatureSystemsLayout.layout(tablature, maxWidth = 5000f, settings = narrow)

        assertEquals("el salto de pagina debe partir el documento en dos", 2, systems.size)
        assertFalse("el primer sistema no abre pagina", systems[0].startsPage)
        assertTrue("el segundo sistema abre pagina", systems[1].startsPage)
        assertEquals(listOf(1), systems[1].measureIndices)
    }

    @Test
    fun sinSaltoDePaginaNingunSistemaAbreHoja() {
        fun note(fret: Int) = TabSlot(
            strings = listOf(TabPluck(TabPosition(1, fret))),
            rhythmicFigure = RhythmFigure.QUARTER
        )
        val tablature = Tablature(
            id = "t", songId = "song-1", title = "T", instrument = "Guitarra", tuning = Tuning.GUITAR,
            time = ScoreTimeSignature("4", 4), resolution = TabResolution.QUARTER,
            measures = listOf(
                TabMeasure(listOf(note(3), note(5))),
                TabMeasure(listOf(note(7), note(8)), forceSystemBreak = true)
            )
        )

        val systems = TablatureSystemsLayout.layout(tablature, maxWidth = 5000f, settings = narrow)

        assertEquals(2, systems.size)
        assertTrue("un salto de sistema no abre hoja", systems.none { it.startsPage })
    }
}
