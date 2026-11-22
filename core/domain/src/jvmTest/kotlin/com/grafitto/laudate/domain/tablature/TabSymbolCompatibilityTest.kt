package com.grafitto.laudate.domain.tablature

import com.grafitto.laudate.domain.model.tablature.TabArticulation
import com.grafitto.laudate.domain.model.tablature.TabArticulationCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * (D-40) Articulaciones **combinadas**: MuseScore no apila acento + picado como dos signos, sino que
 * los sustituye por un glifo único (`ARTICULATION_ABOVE_JOIN_GROUPS`, `articulation.cpp:749`).
 */
class TabSymbolCompatibilityTest {

    private val parejas = listOf(
        setOf(TabArticulation.ACCENT, TabArticulation.STACCATO) to TabArticulation.ACCENT_STACCATO,
        setOf(TabArticulation.MARCATO, TabArticulation.STACCATO) to TabArticulation.MARCATO_STACCATO,
        setOf(TabArticulation.TENUTO, TabArticulation.STACCATO) to TabArticulation.TENUTO_STACCATO,
        setOf(TabArticulation.TENUTO, TabArticulation.ACCENT) to TabArticulation.TENUTO_ACCENT,
        setOf(TabArticulation.MARCATO, TabArticulation.TENUTO) to TabArticulation.MARCATO_TENUTO
    )

    @Test
    fun lasCincoParejasDeMuseScoreSeFundenEnSuGlifo() {
        parejas.forEach { (sueltas, combinada) ->
            assertEquals(
                "join de $sueltas",
                setOf(combinada),
                TabSymbolCompatibility.join(sueltas)
            )
            assertEquals(
                "split de $combinada",
                sueltas,
                TabSymbolCompatibility.split(setOf(combinada))
            )
        }
    }

    @Test
    fun joinYsplitSonIdempotentes() {
        parejas.forEach { (_, combinada) ->
            val unida = TabSymbolCompatibility.join(setOf(combinada))
            assertEquals(setOf(combinada), unida)
            assertEquals(setOf(combinada), TabSymbolCompatibility.join(unida))
            val desglosada = TabSymbolCompatibility.split(setOf(combinada))
            assertEquals(desglosada, TabSymbolCompatibility.split(desglosada))
        }
    }

    @Test
    fun loQueNoTieneParejaPasaIntacto() {
        val sueltas = setOf(
            TabArticulation.FERMATA,
            TabArticulation.MORDENT,
            TabArticulation.UP_BOW,
            TabArticulation.HARMONIC,
            TabArticulation.ACCENT
        )
        assertEquals(sueltas, TabSymbolCompatibility.join(sueltas))
        assertEquals(sueltas, TabSymbolCompatibility.split(sueltas))
    }

    @Test
    fun conTresCualidadesElEmparejamientoEsVorazYEnOrdenDescendente() {
        // `joinArticulations` (`articulation.cpp:798`) ordena los `SymId` **descendente** y empareja
        // de dos en dos: tenuto > staccato > marcato > accent. Con tenuto + staccato + acento, el
        // tenuto se lleva el staccato (portato) y el acento queda suelto.
        val resultado = TabSymbolCompatibility.join(
            setOf(TabArticulation.TENUTO, TabArticulation.STACCATO, TabArticulation.ACCENT)
        )
        assertEquals(setOf(TabArticulation.TENUTO_STACCATO, TabArticulation.ACCENT), resultado)
    }

    @Test
    fun elAcentoYElMarcatoSeSustituyenNoSeCombinan() {
        // `Chord::updateArticulations` (`chord.cpp:1826`) los trata como alternativas del mismo gesto.
        val conAcento = TabSymbolCompatibility.withArticulation(setOf(TabArticulation.ACCENT), TabArticulation.MARCATO)
        assertEquals(setOf(TabArticulation.MARCATO), conAcento)
        val conMarcato = TabSymbolCompatibility.withArticulation(setOf(TabArticulation.MARCATO), TabArticulation.ACCENT)
        assertEquals(setOf(TabArticulation.ACCENT), conMarcato)
    }

    @Test
    fun anadirUnaCualidadSobreUnaCombinadaConservaLaOtra() {
        // Un sforzato (acento + picado) al que se le añade tenuto: el picado se funde con el tenuto
        // en portato y el acento sobrevive.
        val resultado = TabSymbolCompatibility.withArticulation(
            setOf(TabArticulation.ACCENT_STACCATO),
            TabArticulation.TENUTO
        )
        assertEquals(setOf(TabArticulation.TENUTO_STACCATO, TabArticulation.ACCENT), resultado)
    }

    @Test
    fun alternarPorCualidadDesglosaLaCombinada() {
        // Quitar el **picado** de un sforzato deja el **acento**, no borra el glifo entero.
        val sinPicado = TabSymbolCompatibility.toggleArticulation(
            setOf(TabArticulation.ACCENT_STACCATO),
            TabArticulation.STACCATO
        )
        assertEquals(setOf(TabArticulation.ACCENT), sinPicado)
        // Y volver a ponerlo reconstruye el sforzato.
        assertEquals(
            setOf(TabArticulation.ACCENT_STACCATO),
            TabSymbolCompatibility.toggleArticulation(sinPicado, TabArticulation.STACCATO)
        )
    }

    @Test
    fun alternarLaCombinadaEnteraLaQuitaEntera() {
        val quitada = TabSymbolCompatibility.toggleArticulation(
            setOf(TabArticulation.TENUTO_STACCATO),
            TabArticulation.TENUTO_STACCATO
        )
        assertTrue(quitada.isEmpty())
    }

    @Test
    fun unaCombinadaCuentaComoPresenteParaSusComponentes() {
        val sforzato = setOf(TabArticulation.ACCENT_STACCATO)
        assertTrue(TabSymbolCompatibility.isArticulationPresent(sforzato, TabArticulation.ACCENT))
        assertTrue(TabSymbolCompatibility.isArticulationPresent(sforzato, TabArticulation.STACCATO))
        assertTrue(TabSymbolCompatibility.isArticulationPresent(sforzato, TabArticulation.ACCENT_STACCATO))
        assertFalse(TabSymbolCompatibility.isArticulationPresent(sforzato, TabArticulation.TENUTO))
        assertFalse(TabSymbolCompatibility.isArticulationPresent(sforzato, TabArticulation.MARCATO))
    }

    @Test
    fun elPicadoRetiraLosAdornosPeroElPortatoNo() {
        val conAdorno = setOf(TabArticulation.MORDENT)
        val picado = TabSymbolCompatibility.withArticulation(conAdorno, TabArticulation.STACCATO)
        assertEquals(setOf(TabArticulation.STACCATO), picado)
        // El tenuto no es un corte: el adorno sobrevive y se combina con el picado en portato.
        val portato = TabSymbolCompatibility.withArticulation(conAdorno, TabArticulation.TENUTO)
        assertEquals(setOf(TabArticulation.MORDENT, TabArticulation.TENUTO), portato)
    }

    @Test
    fun lasCombinadasDeclaranSusDosCategorias() {
        assertEquals(
            setOf(TabArticulationCategory.ACCENT, TabArticulationCategory.STACCATO),
            TabArticulation.ACCENT_STACCATO.categories
        )
        assertEquals(
            setOf(TabArticulationCategory.MARCATO, TabArticulationCategory.TENUTO),
            TabArticulation.MARCATO_TENUTO.categories
        )
        // Las sueltas declaran una sola y no son combinadas.
        TabArticulation.entries.filterNot { it.isCombined }.forEach {
            assertEquals("${it.name} debería tener una categoría", 1, it.categories.size)
            assertEquals(setOf(it), it.components)
        }
    }
}
