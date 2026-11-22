package com.grafitto.laudate.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.grafitto.laudate.domain.model.rhythm.RhythmNotationMode
import com.grafitto.laudate.domain.tablature.TabMetronome
import com.grafitto.laudate.domain.tablature.TabNotationScale
import com.grafitto.laudate.domain.tablature.TabTranspositionMode
import com.grafitto.laudate.ui.common.Density
import com.grafitto.laudate.ui.tablature.TablatureEditorOptions
import com.grafitto.laudate.ui.tablature.TablatureMarksPalette
import com.grafitto.laudate.ui.tablature.TablaturePaletteDrag
import com.grafitto.laudate.ui.tablature.TablatureUiIntent
import com.grafitto.laudate.ui.tablature.TablatureUiState

/**
 * **Ajustes de la herramienta «Tablatura»** para el panel derecho.
 *
 * Reúne **todo lo que no es la rejilla**, en este orden y de arriba abajo:
 *
 * 1. Las **propiedades de la nota del cursor** (dentro de [TablatureEditorOptions]): traste, voz,
 *    dedo y púa de lo que se acaba de escribir. (D-84) Va **lo primero** porque es lo que se toca a
 *    cada compás mientras se escribe, y arranca **plegada**.
 * 2. Las **paletas de marcas** ([TablatureMarksPalette]): dinámicas, reguladores, adornos,
 *    articulaciones, ligaduras, técnicas, trémolos, digitación, repeticiones y saltos, barras de
 *    compás, tempo y texto. Se ocultan con `F9`.
 * 3. El resto de **opciones de la tablatura** ([TablatureEditorOptions]): tonalidad y transposición,
 *    tamaño y metadatos. Se ocultan con `F8`.
 *
 * (D-58) Las paletas **no** tienen panel propio: viven aquí, porque la aplicación ya tiene **un**
 * panel de herramientas y dos paneles laterales con símbolos era exactamente el problema. La
 * pantalla se queda con el contenido —la rejilla y la barra de escritura—, así que no hay dos
 * controles que digan lo mismo ni ajustes robándole sitio a la escritura.
 *
 * (D-84) **Todos los bloques arrancan plegados**, por petición del usuario: el panel es un índice de
 * secciones, no un formulario que se despliega entero al abrirlo.
 *
 * Es una pieza **Compose Multiplatform** y sin lógica: pinta y delega en quien la usa.
 */
@Composable
fun TablatureToolSettings(
    uiState: TablatureUiState,
    onIntent: (TablatureUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    var showHelp by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Density.Snug)
    ) {
        // ── Propiedades de la nota del cursor (D-84): **lo primero** del panel ──────────────────
        // Es lo que se toca a cada compás mientras se escribe —traste, voz, dedo, púa—, así que va
        // por delante de las paletas de símbolos. Arranca plegada, como el resto de los bloques.
        if (uiState.showProperties) {
            TablatureEditorOptions(
                uiState = uiState,
                onIntent = onIntent,
                contextualOnly = true
            )
        }
        // ── Paletas de marcas (D-58) ─────────────────────────────────────────────────────────────
        // Van **antes de los ajustes del documento**: se usan a cada compás mientras se escribe,
        // mientras que tonalidad, tamaño e instrumento se tocan una vez. `F9` las oculta, como en
        // MuseScore.
        // El arrastre de una celda a la partitura se resuelve con la instancia compartida: la
        // rejilla instala su resolvedor de celdas y aquí solo se lanza el intent al soltar.
        if (uiState.showPalettes) {
            TablatureMarksPalette(
                uiState = uiState,
                onIntent = onIntent,
                dragState = TablaturePaletteDrag,
                onDrop = {
                    TablaturePaletteDrag.resolveDrop()?.let(onIntent)
                    TablaturePaletteDrag.cancel()
                }
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = Density.Tight)
            )
        }
        TablatureEditorOptions(uiState = uiState, onIntent = onIntent)
        // ── Metrónomo configurable (D-66) ──────────────────────────────────────────────────────
        // El botón de la barra de escritura solo lo enciende y lo apaga. Lo que se configura una vez
        // —volumen propio, acento del primer tiempo y subdivisión para estudiar despacio— vive
        // aquí, junto al resto de ajustes de la herramienta, y se guarda en las preferencias.
        SettingsSection(
            title = "Metrónomo",
            icon = CanticoraIcons.Metronome,
            initiallyExpanded = false
        ) {
            SettingsSwitchRow(
                icon = CanticoraIcons.Metronome,
                label = "Suena al reproducir",
                checked = uiState.metronome.enabled,
                onCheckedChange = { onIntent(TablatureUiIntent.ToggleMetronome) }
            )
            SettingsSliderRow(
                icon = CanticoraIcons.MetronomeVolume,
                label = "Volumen del clic",
                value = uiState.metronome.safeVolume,
                onValueChange = { onIntent(TablatureUiIntent.SetMetronomeVolume(it)) },
                valueRange = TabMetronome.MIN_VOLUME..TabMetronome.MAX_VOLUME,
                valueLabel = { "${(it * 100).toInt()} %" }
            )
            SettingsSwitchRow(
                icon = CanticoraIcons.Tempo,
                label = "Acentuar el primer tiempo",
                checked = uiState.metronome.accentFirst,
                onCheckedChange = { onIntent(TablatureUiIntent.ToggleMetronomeAccent) }
            )
            SettingsButtonGroup(
                label = "Subdivisión",
                options = (TabMetronome.MIN_SUBDIVISION..TabMetronome.MAX_SUBDIVISION).map {
                    SettingsOption(it.toString(), metronomeSubdivisionLabel(it))
                },
                selectedId = uiState.metronome.safeSubdivision.toString(),
                onSelect = { id ->
                    id.toIntOrNull()?.let { onIntent(TablatureUiIntent.SetMetronomeSubdivision(it)) }
                }
            )
        }
        SettingsButtonGroup(
            label = "Al transportar",
            options = TabTranspositionMode.entries.map {
                SettingsOption(it.name, transpositionModeLabel(it))
            },
            selectedId = uiState.transpositionMode.name,
            onSelect = { id ->
                TabTranspositionMode.entries.firstOrNull { it.name == id }
                    ?.let { onIntent(TablatureUiIntent.SetTranspositionMode(it)) }
            },
            icons = mapOf(
                TabTranspositionMode.CAPO.name to CanticoraIcons.Capo,
                TabTranspositionMode.REFINGER.name to CanticoraIcons.Transposition,
                TabTranspositionMode.LOCKED.name to CanticoraIcons.Locked
            ),
            onHelpClick = { showHelp = true }
        )
        Text(
            text = transpositionModeHint(uiState.transpositionMode),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                start = Density.CardPadding,
                end = Density.CardPadding,
                bottom = Density.Snug
            )
        )
    }
    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Al transportar la canción") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Density.Compact)) {
                    TabTranspositionMode.entries.forEach { mode ->
                        Text(
                            text = "${transpositionModeLabel(mode)}: ${transpositionModeHint(mode)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Entendido") }
            }
        )
    }
}

/**
 * **Ajustes de la herramienta «Lectura»** para el panel derecho del visor de canto: cómo se dibuja
 * el patrón rítmico, con qué tamaño se lee la letra y con qué tamaño se dibuja la tablatura (rejilla
 * y cifras de traste, por separado).
 *
 * Son ajustes **de lectura**, no de la canción: no se guardan en el canto, sino en las preferencias
 * del usuario. Los controles **vivos** (transponer, elegir la tablatura que se toca) siguen en la
 * propia pantalla, pegados a la letra, porque se usan a media interpretación; aquí solo vive lo que
 * se configura una vez. Así no hay ninguna botonera duplicada.
 */
@Composable
fun SongReaderToolSettings(
    hasRhythm: Boolean,
    rhythmNotationMode: RhythmNotationMode,
    onRhythmNotationChange: (RhythmNotationMode) -> Unit,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    onFontSizeChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * `true` si la pantalla **está mostrando** tablatura. No basta con que el canto **tenga**
     * tablaturas guardadas: el usuario quiere el control **siempre que aparezca** tablatura en la
     * vista, y no quiere ver controles de algo que no está en pantalla.
     */
    hasTablature: Boolean = false,
    /**
     * `true` si la pantalla **está mostrando** letra. Con «Solo tablatura» no hay letra que
     * agrandar, así que el control de tamaño de texto sobra.
     */
    hasLyrics: Boolean = true,
    tablatureScale: Float = 1f,
    onTablatureScaleChange: (Float) -> Unit = {},
    onTablatureScaleChangeFinished: () -> Unit = {},
    tablatureNumberScale: Float = 1f,
    onTablatureNumberScaleChange: (Float) -> Unit = {},
    onTablatureNumberScaleChangeFinished: () -> Unit = {}
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (hasRhythm) {
            SettingsButtonGroup(
                label = "Notación del ritmo",
                options = RhythmNotationMode.entries.map {
                    SettingsOption(it.name, rhythmNotationLabel(it))
                },
                selectedId = rhythmNotationMode.name,
                onSelect = { id ->
                    RhythmNotationMode.entries.firstOrNull { it.name == id }?.let(onRhythmNotationChange)
                },
                icons = mapOf(
                    RhythmNotationMode.ARROWS.name to CanticoraIcons.Arrows,
                    RhythmNotationMode.MUSICAL_SYMBOLS.name to CanticoraIcons.Symbols,
                    RhythmNotationMode.COMBINED.name to CanticoraIcons.Subdivision,
                    RhythmNotationMode.CHORDPRO_SYMBOLS.name to CanticoraIcons.Notation
                )
            )
        }
        // **Tamaño de la letra**: solo si hay letra en pantalla. En «Solo tablatura» no hay texto que
        // agrandar y el deslizador no haría nada visible.
        if (hasLyrics) {
            SettingsSliderRow(
                icon = CanticoraIcons.TabScale,
                label = "Tamaño de la letra",
                value = fontSizeSp,
                onValueChange = onFontSizeChange,
                valueRange = 14f..32f,
                valueLabel = { "${it.toInt()} pt" },
                onValueChangeFinished = onFontSizeChangeFinished
            )
        }
        // **Tamaño de la tablatura y de sus cifras**: solo si hay tablatura en pantalla. Es la regla
        // que pidió el usuario: el control aparece **siempre que se vea** tablatura, sin depender de
        // cuántas tenga guardadas el canto ni de la base de datos.
        if (hasTablature) {
            SettingsSliderRow(
                icon = CanticoraIcons.TabScale,
                label = "Tamaño de la tablatura",
                value = tablatureScale,
                onValueChange = onTablatureScaleChange,
                valueRange = TabNotationScale.MIN_ZOOM..TabNotationScale.MAX_ZOOM,
                onValueChangeFinished = onTablatureScaleChangeFinished
            )
            SettingsSliderRow(
                icon = CanticoraIcons.NotationNumbers,
                label = "Tamaño del número",
                value = tablatureNumberScale,
                onValueChange = onTablatureNumberScaleChange,
                valueRange = TabNotationScale.MIN_ZOOM..TabNotationScale.MAX_ZOOM,
                onValueChangeFinished = onTablatureNumberScaleChangeFinished
            )
        }
    }
}

/** Etiqueta legible del modo de notación rítmica del visor. */
private fun rhythmNotationLabel(mode: RhythmNotationMode): String = when (mode) {
    RhythmNotationMode.ARROWS -> "Flechas"
    RhythmNotationMode.MUSICAL_SYMBOLS -> "Figuras"
    RhythmNotationMode.COMBINED -> "Combinada"
    RhythmNotationMode.CHORDPRO_SYMBOLS -> "ChordPro"
}

/** Id del desplegable que representa «sin compás». */

/** Tonalidades ofrecidas en el panel (las mismas que el selector del editor de canto). */

/** Etiqueta legible del modo de transposición de una tablatura. */
private fun transpositionModeLabel(mode: TabTranspositionMode): String = when (mode) {
    TabTranspositionMode.CAPO -> "Cejilla"
    TabTranspositionMode.REFINGER -> "Redigitada"
    TabTranspositionMode.LOCKED -> "Fija"
}

/** Qué hace cada modo de transposición, en una línea (se muestra bajo la botonera y en la ayuda). */
private fun transpositionModeHint(mode: TabTranspositionMode): String = when (mode) {
    TabTranspositionMode.CAPO -> "Los trastes no cambian: sube la cejilla con la canción."
    TabTranspositionMode.REFINGER -> "Recalcula cuerdas y trastes para la nueva tonalidad."
    TabTranspositionMode.LOCKED -> "Se muestra en su tonalidad original, sin seguir la canción."
}

/**
 * Nombre de la **figura** que se oye con cada subdivisión del metrónomo (D-66): `1` pulsación,
 * `2` corcheas, `3` tresillos, `4` semicorcheas. Se lee como en un método, no como un número de
 * clics, porque es lo que el músico reconoce.
 */
private fun metronomeSubdivisionLabel(subdivision: Int): String = when (subdivision) {
    1 -> "Pulsación"
    2 -> "Corcheas"
    3 -> "Tresillos"
    else -> "Semicorcheas"
}
