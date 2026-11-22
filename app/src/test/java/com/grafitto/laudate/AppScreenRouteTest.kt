package com.grafitto.laudate

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Comprueba que la pantalla actual **sobrevive como texto** al guardado de estado.
 *
 * Es lo que se rompía al girar la tableta: la pantalla se perdía y la app volvía al catálogo. El
 * ida y vuelta tiene que ser **exacto** para todas las pantallas; si alguna deja de serlo, al rotar
 * el usuario aterrizará en otro sitio aunque el estado sí se haya conservado.
 */
class AppScreenRouteTest {

    private fun roundTrip(screen: AppScreen): AppScreen =
        appScreenFromRouteCode(appScreenToRouteCode(screen))

    @Test
    fun `todas las pantallas sobreviven al ida y vuelta`() {
        val screens = listOf(
            AppScreen.Catalog,
            AppScreen.SongDetail("2187190f-ec9b-489d-b54c-cc0004a6cbaf"),
            AppScreen.SongEditor(null),
            AppScreen.SongEditor("abc-123"),
            AppScreen.CelebrationList,
            AppScreen.CelebrationEditor(null),
            AppScreen.CelebrationEditor("cel-1"),
            AppScreen.LiveCelebration("cel-1"),
            AppScreen.RhythmPatterns,
            AppScreen.RhythmPatternEditor,
            AppScreen.Tablatures("sng-1", "Adeste fideles"),
            AppScreen.TablatureEditor,
            AppScreen.Backup,
            AppScreen.Import,
            AppScreen.Manual
        )
        screens.forEach { screen ->
            assertEquals("ida y vuelta de $screen", screen, roundTrip(screen))
        }
    }

    @Test
    fun `el codigo es estable y no depende del orden de llamada`() {
        assertEquals("catalog", appScreenToRouteCode(AppScreen.Catalog))
        assertEquals("live:cel-1", appScreenToRouteCode(AppScreen.LiveCelebration("cel-1")))
        assertEquals("editor:", appScreenToRouteCode(AppScreen.SongEditor(null)))
        assertEquals(
            "tablatures:sng-1:Adeste fideles",
            appScreenToRouteCode(AppScreen.Tablatures("sng-1", "Adeste fideles"))
        )
    }

    @Test
    fun `un titulo con dos puntos no rompe la reconstruccion`() {
        val screen = AppScreen.Tablatures("sng-1", "Salmo 66: A Dios den gracias")
        assertEquals(screen, roundTrip(screen))
    }

    @Test
    fun `un codigo desconocido cae al catalogo en vez de fallar`() {
        assertEquals(AppScreen.Catalog, appScreenFromRouteCode(null))
        assertEquals(AppScreen.Catalog, appScreenFromRouteCode(""))
        assertEquals(AppScreen.Catalog, appScreenFromRouteCode("pantalla-que-ya-no-existe"))
        assertEquals(AppScreen.Catalog, appScreenFromRouteCode("song:"))
        assertEquals(AppScreen.Catalog, appScreenFromRouteCode("tablatures:"))
    }

    @Test
    fun `una pantalla sin identificador se degrada a una lista valida`() {
        // `LiveCelebration("")` dejaría el atril sin nada que mostrar: mejor la lista de misas, que
        // es la pantalla desde la que se entra al modo en vivo.
        assertEquals(AppScreen.CelebrationList, appScreenFromRouteCode("live:"))
        // `Tablatures("", ...)` no tendría canto del que listar tablaturas.
        assertEquals(AppScreen.Catalog, appScreenFromRouteCode("tablatures::sin id"))
    }
}
