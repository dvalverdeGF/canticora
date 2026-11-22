package com.grafitto.laudate.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.ui.common.Density
import com.grafitto.laudate.ui.common.HairlineDivider
import com.grafitto.laudate.ui.generated.resources.Res
import com.grafitto.laudate.ui.generated.resources.abc
import com.grafitto.laudate.ui.generated.resources.align_horizontal_right
import com.grafitto.laudate.ui.generated.resources.apps
import com.grafitto.laudate.ui.generated.resources.attachment
import com.grafitto.laudate.ui.generated.resources.category
import com.grafitto.laudate.ui.generated.resources.church
import com.grafitto.laudate.ui.generated.resources.cloud_sync
import com.grafitto.laudate.ui.generated.resources.code
import com.grafitto.laudate.ui.generated.resources.edit_note
import com.grafitto.laudate.ui.generated.resources.equalizer
import com.grafitto.laudate.ui.generated.resources.event
import com.grafitto.laudate.ui.generated.resources.favorite
import com.grafitto.laudate.ui.generated.resources.format_list_numbered
import com.grafitto.laudate.ui.generated.resources.format_size
import com.grafitto.laudate.ui.generated.resources.graphic_eq
import com.grafitto.laudate.ui.generated.resources.grid_on
import com.grafitto.laudate.ui.generated.resources.history
import com.grafitto.laudate.ui.generated.resources.info
import com.grafitto.laudate.ui.generated.resources.label_off
import com.grafitto.laudate.ui.generated.resources.library_music
import com.grafitto.laudate.ui.generated.resources.lock
import com.grafitto.laudate.ui.generated.resources.lyrics
import com.grafitto.laudate.ui.generated.resources.menu_book
import com.grafitto.laudate.ui.generated.resources.music_note
import com.grafitto.laudate.ui.generated.resources.numbers
import com.grafitto.laudate.ui.generated.resources.piano
import com.grafitto.laudate.ui.generated.resources.queue_music
import com.grafitto.laudate.ui.generated.resources.record_voice_over
import com.grafitto.laudate.ui.generated.resources.schedule
import com.grafitto.laudate.ui.generated.resources.sort
import com.grafitto.laudate.ui.generated.resources.sort_by_alpha
import com.grafitto.laudate.ui.generated.resources.speed
import com.grafitto.laudate.ui.generated.resources.straighten
import com.grafitto.laudate.ui.generated.resources.swap_horiz
import com.grafitto.laudate.ui.generated.resources.swap_vert
import com.grafitto.laudate.ui.generated.resources.touch_app
import com.grafitto.laudate.ui.generated.resources.translate
import com.grafitto.laudate.ui.generated.resources.tune
import com.grafitto.laudate.ui.generated.resources.upload
import com.grafitto.laudate.ui.generated.resources.vertical_align_bottom
import com.grafitto.laudate.ui.generated.resources.vertical_align_top
import com.grafitto.laudate.ui.generated.resources.view_column
import com.grafitto.laudate.ui.generated.resources.visibility
import com.grafitto.laudate.ui.generated.resources.volunteer_activism
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * **Iconos de Canticora**, tomados de **Material Symbols** (Google, licencia Apache-2.0) y
 * descargados a `composeResources/drawable` como SVG. Se usan en los paneles laterales para que
 * cada ajuste se reconozca de un vistazo sin leer la etiqueta.
 *
 * Son vectores: se dibujan nítidos a cualquier tamaño y se tiñen con el color del tema.
 */
object CanticoraIcons {
    // ── Ajustes comunes ─────────────────────────────────────────────────────────────────────
    val Notation: DrawableResource = Res.drawable.translate
    val Instrument: DrawableResource = Res.drawable.piano
    val DisplayMode: DrawableResource = Res.drawable.visibility
    val TabPosition: DrawableResource = Res.drawable.view_column

    // ── Ajustes de la herramienta ───────────────────────────────────────────────────────────
    val TimeSignature: DrawableResource = Res.drawable.grid_on
    val Subdivision: DrawableResource = Res.drawable.graphic_eq
    val Transposition: DrawableResource = Res.drawable.swap_horiz
    val Key: DrawableResource = Res.drawable.music_note
    val Tempo: DrawableResource = Res.drawable.speed
    /** Metrónomo: el pulso que se oye al estudiar (D-66). */
    val Metronome: DrawableResource = Res.drawable.equalizer
    /** Volumen propio del metrónomo, independiente del de la tablatura (D-66). */
    val MetronomeVolume: DrawableResource = Res.drawable.graphic_eq
    val Capo: DrawableResource = Res.drawable.straighten
    val TabScale: DrawableResource = Res.drawable.format_size
    val Voice: DrawableResource = Res.drawable.record_voice_over
    /** Dedo que pisa o pulsa la nota (Material Symbols `touch_app`). */
    val Fingering: DrawableResource = Res.drawable.touch_app

    // ── Ajustes por sección ─────────────────────────────────────────────────────────────────
    val Sort: DrawableResource = Res.drawable.sort
    val Favorite: DrawableResource = Res.drawable.favorite
    val Uncategorized: DrawableResource = Res.drawable.label_off
    val Moment: DrawableResource = Res.drawable.schedule

    // ── Opciones de las botoneras ───────────────────────────────────────────────────────────
    val NotationLatin: DrawableResource = Res.drawable.abc
    val NotationEnglish: DrawableResource = Res.drawable.translate
    val NotationNumbers: DrawableResource = Res.drawable.numbers
    val LyricsOnly: DrawableResource = Res.drawable.lyrics
    val ChordsAbove: DrawableResource = Res.drawable.music_note
    val WithTablature: DrawableResource = Res.drawable.grid_on
    val Everything: DrawableResource = Res.drawable.view_column
    val TabOnly: DrawableResource = Res.drawable.straighten
    val PositionTop: DrawableResource = Res.drawable.vertical_align_top
    val PositionBottom: DrawableResource = Res.drawable.vertical_align_bottom
    val PositionRight: DrawableResource = Res.drawable.align_horizontal_right
    val Locked: DrawableResource = Res.drawable.lock
    val Arrows: DrawableResource = Res.drawable.swap_vert
    val Symbols: DrawableResource = Res.drawable.code
    val All: DrawableResource = Res.drawable.apps
    val Event: DrawableResource = Res.drawable.event
    val SortTitle: DrawableResource = Res.drawable.sort_by_alpha
    val SortNumber: DrawableResource = Res.drawable.format_list_numbered
    val SortRecent: DrawableResource = Res.drawable.history

    // ── Navegación ──────────────────────────────────────────────────────────────────────────
    val Songs: DrawableResource = Res.drawable.library_music
    val Masses: DrawableResource = Res.drawable.church
    val Rhythms: DrawableResource = Res.drawable.equalizer
    val Import: DrawableResource = Res.drawable.upload
    val Backup: DrawableResource = Res.drawable.cloud_sync
    val Manual: DrawableResource = Res.drawable.menu_book
    val Categories: DrawableResource = Res.drawable.category
    val Support: DrawableResource = Res.drawable.volunteer_activism
    val About: DrawableResource = Res.drawable.info
    val Settings: DrawableResource = Res.drawable.tune
    val Playlist: DrawableResource = Res.drawable.queue_music

    // ── Herramientas del editor de canto (panel derecho estilo Photoshop) ───────────────────
    val ToolSong: DrawableResource = Res.drawable.edit_note
    val ToolChords: DrawableResource = Res.drawable.piano
    val ToolSections: DrawableResource = Res.drawable.view_column
    val ToolRhythm: DrawableResource = Res.drawable.equalizer
    val ToolDirectives: DrawableResource = Res.drawable.code
    val ToolSymbols: DrawableResource = Res.drawable.music_note
    val ToolAttachments: DrawableResource = Res.drawable.attachment
}

/** Una opción de un desplegable de ajustes: [id] es el valor estable, [label] lo que se lee. */
data class SettingsOption(val id: String, val label: String)

/** Icono de una fila de ajustes, ya teñido con el color del tema. */
@Composable
fun SettingsIcon(
    icon: DrawableResource,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: androidx.compose.ui.unit.Dp = 20.dp
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint
    )
}

/**
 * Fila de ajuste **desplegable**: icono, etiqueta y valor actual; al pulsarla se abre el menú con
 * las opciones.
 *
 * Es una fila **compacta** de [Density.RowHeight] (40 dp), no el `ListItem` de 56 dp: el panel de
 * ajustes tiene que caber entero sin scroll en la mayoría de pantallas, y el aire sobrante se
 * cambia por más ajustes a la vista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdownRow(
    icon: DrawableResource,
    label: String,
    value: String,
    options: List<SettingsOption>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Density.RowHeight)
                .clickable(enabled = enabled) { expanded = true }
                .padding(start = Density.CardPadding, end = Density.Snug),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Density.CardPadding)
        ) {
            SettingsIcon(icon, size = 20.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 108.dp)
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelect(option.id)
                    },
                    leadingIcon = {
                        if (option.id == selectedId) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Fila de ajuste **desplegable de selección múltiple**: misma altura y aspecto que
 * [SettingsDropdownRow], pero cada opción se marca o desmarca sin cerrar el menú.
 *
 * Se usa para las categorías y etiquetas de un canto, donde el usuario suele marcar varias seguidas.
 */
@Composable
fun SettingsMultiSelectRow(
    icon: DrawableResource,
    label: String,
    value: String,
    options: List<SettingsOption>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Density.RowHeight)
                // La fila alterna: si el desplegable está abierto, volver a pulsarla lo cierra.
                .clickable { expanded = !expanded }
                .padding(start = Density.CardPadding, end = Density.Snug),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Density.CardPadding)
        ) {
            SettingsIcon(icon, size = 20.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 108.dp)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ArrowDropDown,
                contentDescription = if (expanded) "Cerrar la lista" else "Abrir la lista",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // La lista puede tener decenas de opciones: se acota la altura para que el menú no
            // tape el panel entero y el botón «Listo» quede siempre a la vista.
            modifier = Modifier.heightIn(max = 420.dp)
        ) {
            // «Listo» va arriba a propósito: con listas largas el pie del menú queda fuera de
            // pantalla y el usuario se quedaría sin forma de cerrar el desplegable.
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Listo",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = { expanded = false },
                leadingIcon = {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            )
            HairlineDivider()
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    // Se queda abierto a propósito: es una selección múltiple y cerrar a cada
                    // marca obligaría a reabrirlo. Para salir está el botón «Listo» de arriba.
                    onClick = { onToggle(option.id) },
                    leadingIcon = {
                        if (option.id in selectedIds) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * **Botonera de opciones** del panel: una fila de botones compactos, con icono y etiqueta corta,
 * donde el activo se resalta con el color de acento.
 *
 * Es el patrón de los paneles de herramientas de los editores musicales: cuando las opciones son
 * pocas y se reconocen de un vistazo, una botonera es más rápida que un desplegable. Se usa **solo**
 * con conjuntos cortos y excluyentes; para listas largas (instrumentos, tonalidades) sigue habiendo
 * desplegable, y para los números, campo de texto.
 *
 * @param icons icono de cada opción, por `id`; las que no lo tengan se muestran solo con texto.
 * @param onHelpClick si no es `null`, añade un botón **(i)** pegado a la etiqueta que explica el
 *   ajuste. Va **en la misma línea** del título para no comerse ni un píxel de alto.
 */
@Composable
fun SettingsButtonGroup(
    label: String,
    options: List<SettingsOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    icons: Map<String, DrawableResource> = emptyMap(),
    onHelpClick: (() -> Unit)? = null
) {
    val hasIcons = options.any { icons.containsKey(it.id) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Density.CardPadding, end = Density.CardPadding, top = Density.Snug, bottom = Density.Tight)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            onHelpClick?.let { onClick ->
                IconButton(onClick = onClick, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Qué hace «$label»",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(Density.Tight))
        Row(horizontalArrangement = Arrangement.spacedBy(Density.Snug)) {
            options.forEach { option ->
                val selected = option.id == selectedId
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (hasIcons) 42.dp else 32.dp)
                        .clickable { onSelect(option.id) },
                    shape = RoundedCornerShape(Density.CornerSmall),
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = Density.Tight),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        icons[option.id]?.let { icon ->
                            Icon(
                                painter = painterResource(icon),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.height(1.dp))
                        }
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fila de ajuste **numérica**: icono, etiqueta y un campo donde se escribe el valor a mano. Se usa
 * para el **tempo** y la **cejilla**, que son números: no necesitan una botonera de presets.
 */
@Composable
fun SettingsNumberRow(
    icon: DrawableResource,
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 0,
    max: Int = 999
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Density.RowHeightTall)
            .padding(start = Density.CardPadding, end = Density.CardPadding, top = Density.Tight, bottom = Density.Tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Density.CardPadding)
    ) {
        SettingsIcon(icon)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        SettingsNumberField(value = value, onValueChange = onValueChange, min = min, max = max)
    }
}

/** Campo numérico compacto (ancho fijo) para las filas de ajustes. */
@Composable
private fun SettingsNumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Surface(
        modifier = Modifier.width(56.dp).height(Density.PillHeight),
        shape = RoundedCornerShape(Density.CornerSmall),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(4)
                    text = digits
                    digits.toIntOrNull()?.let { onValueChange(it.coerceIn(min, max)) }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Fila de ajuste **de texto**: icono, etiqueta y un campo compacto de una línea.
 *
 * Sustituye a los `OutlinedTextField` de altura completa, que en el panel derecho robaban media
 * pantalla para escribir cuatro letras.
 */
@Composable
fun SettingsTextFieldRow(
    icon: DrawableResource,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Density.RowHeightTall)
            .padding(
                start = Density.CardPadding,
                end = Density.CardPadding,
                top = Density.Tight,
                bottom = Density.Tight
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Density.CardPadding)
    ) {
        SettingsIcon(icon)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Surface(
            modifier = Modifier.width(150.dp).height(Density.PillHeight),
            shape = RoundedCornerShape(Density.CornerSmall),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier.fillMaxSize().padding(horizontal = Density.Compact)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty() && placeholder.isNotEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            inner()
                        }
                    }
                )
            }
        }
    }
}

/**
 * Fila de ajuste **booleana**: icono, etiqueta y un interruptor. Se usa para los filtros que se
 * activan o desactivan (solo favoritos, solo sin categoría…).
 */
@Composable
fun SettingsSwitchRow(
    icon: DrawableResource,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Density.RowHeightTall)
            .clickable { onCheckedChange(!checked) }
            .padding(start = Density.CardPadding, end = Density.Snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Density.CardPadding)
    ) {
        SettingsIcon(icon)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Fila de ajuste **continua** (deslizador) para los valores que se recorren mejor con el dedo que
 * escribiéndolos: tamaño de letra, escala de la tablatura… Muestra el icono, la etiqueta y el valor
 * actual en porcentaje o puntos, para que el ajuste se lea sin abrir nada.
 */
@Composable
fun SettingsSliderRow(
    icon: DrawableResource,
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    valueLabel: (Float) -> String = { "${(it * 100).toInt()}%" },
    onValueChangeFinished: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Density.CardPadding, end = Density.CardPadding, top = Density.Tight)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Density.CardPadding)
        ) {
            SettingsIcon(icon, size = 18.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel(value),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.fillMaxWidth().height(Density.RowHeight)
        )
    }
}

/**
 * **Sección plegable** del panel de ajustes: una cabecera plana (icono, título y chevron) que se
 * abre y se cierra, separada del bloque siguiente por un **filete de 1 px**.
 *
 * No es una tarjeta: el panel de ajustes es una superficie continua y las secciones se separan con
 * líneas, no con cajas ni sombras. Es el mismo patrón que usan los paneles de herramientas de los
 * editores musicales: el usuario pliega lo que no está usando y el panel deja de ocupar espacio.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    icon: DrawableResource? = null,
    initiallyExpanded: Boolean = true,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = Density.CardPadding, end = Density.Snug, top = Density.Compact, bottom = Density.Compact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Density.Compact)
        ) {
            icon?.let { SettingsIcon(it, tint = MaterialTheme.colorScheme.primary, size = 18.dp) }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Plegar $title" else "Desplegar $title",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // El filete va **pegado a la cabecera**, igual que en el panel de herramientas: así el
        // bloque se lee como un título con su contenido debajo y no como una lista suelta.
        HairlineDivider()
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Density.Snug, bottom = Density.Snug)
            ) { content() }
        }
        HairlineDivider()
    }
}

/** Contenedor vertical de filas de ajustes, sin aire de más entre ellas. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) { content() }
}
