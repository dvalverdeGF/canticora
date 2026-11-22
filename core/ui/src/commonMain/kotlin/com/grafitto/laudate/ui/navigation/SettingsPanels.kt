package com.grafitto.laudate.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.grafitto.laudate.domain.model.music.NotationSystem
import com.grafitto.laudate.domain.model.song.SongDisplayMode
import com.grafitto.laudate.domain.model.tablature.TabDisplayPosition

/**
 * **Ajustes comunes** del panel lateral: los que valen para toda la aplicación, no solo para la
 * pantalla actual. Hoy: **nomenclatura de acordes**, **instrumento preferido** (la tablatura que se
 * muestra), **modo de vista** de la canción y **posición de la tablatura**.
 *
 * Se mezclan los dos controles del panel según convenga: **botonera** cuando las opciones son pocas
 * y se reconocen de un vistazo (nomenclatura, posición de la tablatura) y **desplegable** cuando la
 * lista es larga (instrumentos, modos de vista). Así el panel se lee rápido y no desperdicia aire.
 *
 * Es una pieza **Compose Multiplatform** (Android + escritorio) y sin lógica: solo pinta y delega.
 */
@Composable
fun CommonSettingsPanel(
    notationSystem: NotationSystem,
    onNotationChange: (NotationSystem) -> Unit,
    preferredInstrument: String?,
    /** Nombres de los instrumentos que **el canto tiene realmente** en sus tablaturas. */
    instrumentOptions: List<String>,
    onInstrumentChange: (String?) -> Unit,
    displayMode: SongDisplayMode,
    onDisplayModeChange: (SongDisplayMode) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * `true` si el canto **tiene** tablaturas guardadas (no si ahora mismo se ven).
     *
     * Esta distinción es importante y costó un fallo real: si aquí se exigiera que la tablatura
     * **ya** estuviera en pantalla, el desplegable de «Cómo se ve la canción» filtraría las opciones
     * de tablatura justo cuando el canto está en «Letra y acordes», y el usuario **no podría
     * elegirlas nunca**: un círculo vicioso. Lo pidió así: *«en el modo canto no me sale la opción
     * de mostrar la tablatura en las canciones que tienen tablatura»*.
     *
     * Quien necesita saber si la tablatura **se está viendo** (los deslizadores de tamaño) lo calcula
     * aparte, con `effectiveDisplayMode`.
     */
    hasTablature: Boolean = false,
    tabDisplayPosition: TabDisplayPosition = TabDisplayPosition.AFTER,
    onTabDisplayPositionChange: (TabDisplayPosition) -> Unit = {},
    /**
     * `true` en las pantallas de **lectura** (visor del canto y vista previa del editor). Mientras
     * se **escribe** una tablatura los ajustes de lectura —cómo se ve la canción y dónde va la
     * tablatura— no tienen efecto visible, así que se ocultan: son cosas de la vista previa.
     */
    showReadingSettings: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // ── Nomenclatura de acordes (global) ────────────────────────────────────────────────
        SettingsButtonGroup(
            label = "Nomenclatura",
            options = NOTATION_OPTIONS.map { SettingsOption(it.name, notationLabel(it)) },
            selectedId = notationSystem.name,
            onSelect = { id ->
                NOTATION_OPTIONS.firstOrNull { it.name == id }?.let(onNotationChange)
            },
            icons = mapOf(
                NotationSystem.LATIN.name to CanticoraIcons.NotationLatin,
                NotationSystem.ENGLISH.name to CanticoraIcons.NotationEnglish
            )
        )

        // ── Instrumento preferido (la tablatura que se muestra) ─────────────────────────────
        // **Solo si el canto tiene tablaturas**, y con **solo sus instrumentos** (petición del
        // usuario, dos veces): el ajuste sirve para elegir cuál de las tablaturas se ve, así que
        // en un canto sin ninguna no ofrece nada que filtrar, y ofrecer los diez instrumentos del
        // catálogo cuando el canto solo trae guitarra es ruido: la mitad de las opciones no harían
        // nada. Se listan **únicamente** las afinaciones que el canto tiene escritas.
        if (hasTablature && instrumentOptions.isNotEmpty()) {
            SettingsDropdownRow(
                icon = CanticoraIcons.Instrument,
                label = "Instrumento preferido",
                value = preferredInstrument ?: "Todas",
                selectedId = preferredInstrument ?: ALL_INSTRUMENTS,
                options = listOf(SettingsOption(ALL_INSTRUMENTS, "Todas")) +
                    instrumentOptions.map { SettingsOption(it, it) },
                onSelect = { id -> onInstrumentChange(id.takeIf { it != ALL_INSTRUMENTS }) }
            )
        }

        // ── Modo de vista de la canción ─────────────────────────────────────────────────────
        // Oculto mientras se **edita** una tablatura: `displayMode` decide cómo se compone la
        // lectura, algo que solo se aprecia en el visor y en la vista previa. En el editor de
        // tablatura sonaría a que hace algo, y no hace nada (es lo que reportó el usuario).
        if (showReadingSettings) {
            // Las opciones que muestran tablatura se ofrecen si el canto **tiene** alguna, aunque
            // ahora mismo no se esté viendo. Filtrarlas por lo que se ve sería un círculo vicioso:
            // con «Letra y acordes» desaparecerían y no habría forma de pasar a la tablatura.
            SettingsDropdownRow(
                icon = CanticoraIcons.DisplayMode,
                label = "Cómo se ve la canción",
                value = displayMode.label,
                selectedId = displayMode.name,
                options = SongDisplayMode.entries
                    .filter { hasTablature || !it.showsTablature }
                    .map { SettingsOption(it.name, it.label) },
                onSelect = { id -> SongDisplayMode.entries.firstOrNull { it.name == id }?.let(onDisplayModeChange) }
            )

            // ── Posición de la tablatura (solo si letra y tablatura conviven) ───────────────
            // Exige que el canto **tenga** tablatura, no solo que el modo la pida: sin ninguna, el
            // selector de posición no mueve nada (lo que se vio en un canto sin tablaturas).
            if (hasTablature && displayMode.showsLyrics && displayMode.showsTablature) {
                SettingsButtonGroup(
                    label = "Posición de la tablatura",
                    options = TabDisplayPosition.entries.map { SettingsOption(it.name, it.label) },
                    selectedId = tabDisplayPosition.name,
                    onSelect = { id ->
                        TabDisplayPosition.entries.firstOrNull { it.name == id }
                            ?.let(onTabDisplayPositionChange)
                    },
                    icons = mapOf(
                        TabDisplayPosition.BEFORE.name to CanticoraIcons.PositionTop,
                        TabDisplayPosition.AFTER.name to CanticoraIcons.PositionBottom,
                        TabDisplayPosition.RIGHT.name to CanticoraIcons.PositionRight
                    )
                )
            }
        }
    }
}

/** Id del desplegable que representa «sin instrumento preferido» (se muestran todas). */
private const val ALL_INSTRUMENTS = "__all__"

/**
 * Nomenclaturas que se ofrecen al usuario. El sistema numérico (`NNS`, «1-4-5») sigue existiendo en
 * el dominio para importar/exportar, pero **no se ofrece en la interfaz**: no aporta nada al músico
 * que lee el canto y solo añade ruido al panel.
 */
private val NOTATION_OPTIONS = listOf(NotationSystem.LATIN, NotationSystem.ENGLISH)

/** Etiqueta corta de una nomenclatura de acordes (cabe en una botonera). */
private fun notationLabel(system: NotationSystem): String = when (system) {
    NotationSystem.LATIN -> "Do-Re-Mi"
    NotationSystem.ENGLISH -> "C-D-E"
    NotationSystem.NNS -> "1-4-5"
}
