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
 * **Indicación de cortesía al final del sistema** (D-44).
 *
 * Es el comportamiento real de MuseScore 4: `MeasureLayout::addSystemTrailer`
 * (`src/engraving/rendering/score/measurelayout.cpp`, línea 1981) llama a `setCourtesyTimeSig` con
 * `SegmentType::TimeSigAnnounce` **solo para el último compás del sistema**
 * (`SystemLayout::layoutSystem`, `systemlayout.cpp`, línea 434). La cortesía se dibuja a **tamaño
 * completo** y **después** de la barra final, para que el lector sepa que el sistema siguiente
 * cambia de compás antes de llegar a él.
 *
 * MuseScore **no** repite la indicación al **abrir** sistema: `addSystemHeader` (línea 1887) solo
 * añade barline de inicio, clave y armadura.
 */
class TabCourtesyTimeSignatureTest {

    private val cuatroCuartos = ScoreTimeSignature("4", 4)
    private val tresCuartos = ScoreTimeSignature("3", 4)

    private fun tab(vararg measures: TabMeasure) = Tablature(
        id = "t", songId = "song-1", title = "T", instrument = "Guitarra",
        tuning = Tuning.GUITAR, time = cuatroCuartos, resolution = TabResolution.QUARTER,
        measures = measures.toList()
    )

    private fun lleno(time: ScoreTimeSignature = cuatroCuartos) = TabMeasure(
        slots = List(time.totalQuarterBeats.toInt()) { TabSlot(rhythmicFigure = RhythmFigure.QUARTER) }
    )

    /** Numeradores de cortesía: los que caen **más allá** del borde derecho de su sistema. */
    private fun cortesias(sistemas: List<TabSystem>): List<TabText> = sistemas.flatMap { sistema ->
        val right = sistema.drawing.measureBounds.maxOfOrNull { it.right } ?: 0f
        sistema.drawing.texts.filter {
            it.kind == TabTextKind.TIME_NUMERATOR && it.x > right
        }
    }

    @Test
    fun elSistemaAnteriorAnunciaElCompasDelSiguiente() {
        // El compás 2 cambia a 3/4 y **abre** sistema: el sistema 1 debe anunciarlo al final.
        val t = tab(lleno(), lleno(), lleno(tresCuartos).copy(time = tresCuartos, forceSystemBreak = true))
        val sistemas = TablatureSystemsLayout.layout(t, maxWidth = 2000f)
        assertEquals("debe haber dos sistemas", 2, sistemas.size)
        val anuncios = cortesias(sistemas)
        assertEquals("el primer sistema anuncia una sola fracción", 1, anuncios.size)
        assertEquals("y anuncia el compás nuevo", "3", anuncios.single().text)
        val denominadores = sistemas.first().drawing.texts.filter {
            it.kind == TabTextKind.TIME_DENOMINATOR && it.x > anuncios.single().x - 1f
        }
        assertEquals("con su denominador", 1, denominadores.size)
        assertEquals("4", denominadores.single().text)
    }

    @Test
    fun elUltimoSistemaNoAnunciaNada() {
        val t = tab(lleno(), lleno(), lleno(tresCuartos).copy(time = tresCuartos, forceSystemBreak = true))
        val sistemas = TablatureSystemsLayout.layout(t, maxWidth = 2000f)
        assertTrue(
            "el último sistema no tiene compás siguiente que anunciar",
            cortesias(listOf(sistemas.last())).isEmpty()
        )
    }

    @Test
    fun unCambioDentroDelSistemaNoGeneraCortesia() {
        // El compás 1 cambia a 3/4 pero **no** abre sistema: la fracción se rotula dentro.
        val t = tab(lleno(), lleno(tresCuartos).copy(time = tresCuartos))
        val sistemas = TablatureSystemsLayout.layout(t, maxWidth = 2000f)
        assertEquals("todo cabe en un sistema", 1, sistemas.size)
        assertTrue("no hay cortesía", cortesias(sistemas).isEmpty())
    }

    @Test
    fun sinCambioDeCompasNoHayCortesia() {
        val t = tab(lleno(), lleno(), lleno())
        val sistemas = TablatureSystemsLayout.layout(t, maxWidth = 2000f)
        assertTrue(cortesias(sistemas).isEmpty())
    }

    @Test
    fun conLasIndicacionesOcultasNoHayCortesia() {
        val t = tab(lleno(), lleno(), lleno(tresCuartos).copy(time = tresCuartos, forceSystemBreak = true))
        val sistemas = TablatureSystemsLayout.layout(
            t,
            maxWidth = 2000f,
            settings = TabLayoutSettings(staff = TabStaffOptions(showTimeSignature = false))
        )
        assertTrue("sin indicaciones no se anuncia nada", cortesias(sistemas).isEmpty())
    }

    @Test
    fun laCortesiaNoSeSaleDelAnchoDisponible() {
        val t = tab(lleno(), lleno(), lleno(tresCuartos).copy(time = tresCuartos, forceSystemBreak = true))
        val maxWidth = 2000f
        val sistemas = TablatureSystemsLayout.layout(t, maxWidth = maxWidth)
        sistemas.forEachIndexed { index, sistema ->
            assertTrue(
                "el sistema $index mide ${sistema.drawing.width} y no debe pasar de $maxWidth",
                sistema.drawing.width <= maxWidth + 0.5f
            )
        }
    }

    @Test
    fun laCortesiaVaDespuesDeLaBarraFinalDelSistema() {
        val t = tab(lleno(), lleno(), lleno(tresCuartos).copy(time = tresCuartos, forceSystemBreak = true))
        val sistemas = TablatureSystemsLayout.layout(t, maxWidth = 2000f)
        val primero = sistemas.first().drawing
        val barraFinal = primero.lines.filter { it.isBar }.maxOf { it.x1 }
        val anuncio = cortesias(listOf(sistemas.first())).single()
        assertTrue(
            "la cortesía (x=${anuncio.x}) va después de la barra final (x=$barraFinal)",
            anuncio.x > barraFinal
        )
    }

    @Test
    fun laCortesiaSeCentraEnLaAlturaDelPentagrama() {
        val t = tab(lleno(), lleno(), lleno(tresCuartos).copy(time = tresCuartos, forceSystemBreak = true))
        val sistemas = TablatureSystemsLayout.layout(t, maxWidth = 2000f)
        val primero = sistemas.first().drawing
        val centro = (primero.gridTop + primero.gridBottom) / 2f
        val numerador = cortesias(listOf(sistemas.first())).single()
        val denominador = primero.texts.single {
            it.kind == TabTextKind.TIME_DENOMINATOR && it.x > numerador.x - 1f
        }
        assertEquals(
            "numerador y denominador equidistan del centro del pentagrama",
            centro - numerador.y,
            denominador.y - centro,
            0.01f
        )
    }
}
