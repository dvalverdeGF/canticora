package com.grafitto.laudate.ui.rhythm

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grafitto.laudate.domain.model.rhythm.RhythmEvent
import com.grafitto.laudate.domain.model.rhythm.RhythmFigure
import com.grafitto.laudate.domain.model.rhythm.RhythmNotationMode
import com.grafitto.laudate.domain.model.rhythm.RhythmPattern
import com.grafitto.laudate.domain.model.rhythm.StrumArticulation
import com.grafitto.laudate.domain.model.rhythm.StrumDirection
import com.grafitto.laudate.domain.model.rhythm.StrumRange
import com.grafitto.laudate.domain.model.rhythm.TimeSignature
import com.grafitto.laudate.domain.rhythm.RhythmGridCell
import com.grafitto.laudate.domain.rhythm.RhythmGridResolution
import com.grafitto.laudate.ui.common.MusicFigureGlyph

/**
 * Componentes compartidos entre el **listado de patrones** y el **editor a pantalla completa**.
 *
 * Se extraen aquí para que ambas pantallas representen el mismo modelo (`RhythmPattern.events`)
 * con exactamente la misma vista, sin duplicar código ni divergir en la representación.
 */

/**
 * Representación visual de un patrón. Todas las vistas parten del MISMO modelo
 * ([RhythmPattern.events]); las separaciones `|` se derivan del compás mediante
 * [RhythmPattern.metricGroups].
 */
@Composable
internal fun PatternVisualization(
    pattern: RhythmPattern,
    notationMode: RhythmNotationMode,
    activeEventIndex: Int? = null,
    modifier: Modifier = Modifier
) {
    var flatIndex = 0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        pattern.metricGroups().forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                group.forEach { event ->
                    val currentIndex = flatIndex++
                    RhythmEventCell(
                        event = event,
                        notationMode = notationMode,
                        isActive = currentIndex == activeEventIndex
                    )
                }
            }
        }
    }
}

/** Celda de una posición rítmica: puede mostrar la figura, la flecha o ambas. */
@Composable
internal fun RhythmEventCell(
    event: RhythmEvent,
    notationMode: RhythmNotationMode,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val accentColor = when {
        event.isAccent -> MaterialTheme.colorScheme.tertiary
        event.figure.isRest -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isActive -> MaterialTheme.colorScheme.primaryContainer
                    event.isAccent -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val showFigures = notationMode == RhythmNotationMode.MUSICAL_SYMBOLS ||
                notationMode == RhythmNotationMode.COMBINED
            val showArrows = notationMode == RhythmNotationMode.ARROWS ||
                notationMode == RhythmNotationMode.COMBINED
            val showSymbols = notationMode == RhythmNotationMode.CHORDPRO_SYMBOLS

            if (showFigures) {
                // (D-91) La figura se pinta con **Bravura**: en la web el texto normal no tiene
                // glifos musicales y salía como caja vacía.
                MusicFigureGlyph(figure = event.figure, size = 22.dp, color = accentColor)
            }
            if (showSymbols) {
                // Símbolo de rasgueo vectorial con su articulación y su registro de cuerdas.
                StrumSymbol(
                    direction = event.effectiveDirection,
                    articulation = event.effectiveArticulation,
                    range = event.effectiveRange,
                    size = 22.dp,
                    color = accentColor
                )
            }
            if (showArrows) {
                // Se dibuja el MISMO símbolo vectorial que la rejilla «Beat & Stroke»: así la
                // previsualización y la rejilla muestran exactamente lo mismo, incluida la
                // longitud de la flecha (registro de cuerdas) y su articulación.
                StrumSymbol(
                    direction = event.effectiveDirection,
                    articulation = event.effectiveArticulation,
                    range = event.effectiveRange,
                    size = 18.dp,
                    color = accentColor
                )
                if (event.effectiveRange.isShort) {
                    Text(
                        text = if (event.effectiveRange == StrumRange.BASS) "gr" else "ag",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Tira de posiciones del editor avanzado.
 *
 * Tocar una posición la **selecciona**; no la modifica. Toda la edición se hace en el inspector de
 * abajo, de modo que nunca hay dos flujos a medias ni cambios «invisibles» al tocar una celda.
 */
@Composable
internal fun PositionStrip(
    events: List<RhythmEvent>,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        events.forEachIndexed { index, event ->
            val isSelected = index == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            event.isAccent -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                MusicFigureGlyph(figure = event.figure, size = 18.dp)
                StrumSymbol(
                    direction = event.effectiveDirection,
                    articulation = event.effectiveArticulation,
                    range = event.effectiveRange,
                    size = 22.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Inspector de la posición seleccionada en el editor avanzado.
 *
 * Reúne en **un solo panel** todo lo que define una posición —figura, dirección del golpe,
 * registro de cuerdas, articulación y borrado—, en lugar de repartirlo en dos flujos de dos pasos
 * («selecciona arriba, edita abajo»). Así el usuario ve siempre el estado completo de lo que está
 * editando y no hay pasos a medias.
 */
@Composable
internal fun PositionInspector(
    event: RhythmEvent?,
    index: Int?,
    onSetFigure: (RhythmFigure) -> Unit,
    onSetDirection: (StrumDirection) -> Unit,
    onSetArticulation: (StrumArticulation) -> Unit,
    onSetRange: (StrumRange) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp)
    ) {
        if (event == null || index == null) {
            Text(
                text = "Toca una posición de arriba para editarla.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Posición ${index + 1}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            StrumSymbol(
                direction = event.effectiveDirection,
                articulation = event.effectiveArticulation,
                range = event.effectiveRange,
                size = 24.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        InspectorRow(label = "Figura") {
            RhythmFigure.notes.forEach { figure ->
                InspectorChip(
                    selected = !event.figure.isRest && event.figure == figure,
                    onClick = { onSetFigure(figure) }
                ) {
                    MusicFigureGlyph(figure = figure, size = 16.dp)
                    Text(text = figure.noteName, style = MaterialTheme.typography.labelSmall)
                }
            }
            RhythmFigure.rests.forEach { figure ->
                InspectorChip(
                    selected = event.figure.isRest && event.figure == figure,
                    onClick = { onSetFigure(figure) }
                ) {
                    MusicFigureGlyph(figure = figure, size = 16.dp)
                    Text(text = figure.noteName, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        InspectorRow(label = "Golpe") {
            StrumDirection.strikable.forEach { direction ->
                InspectorChip(
                    selected = event.direction == direction,
                    onClick = { onSetDirection(direction) }
                ) {
                    StrumSymbol(
                        direction = direction,
                        articulation = StrumArticulation.NONE,
                        size = 18.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        InspectorRow(label = "Cuerdas") {
            StrumRange.palette.forEach { range ->
                InspectorChip(
                    selected = event.range == range,
                    onClick = { onSetRange(range) }
                ) {
                    StrumSymbol(
                        direction = StrumDirection.DOWN,
                        articulation = StrumArticulation.NONE,
                        range = range,
                        size = 18.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = range.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        InspectorRow(label = "Articulación") {
            StrumArticulation.all.forEach { articulation ->
                InspectorChip(
                    selected = event.articulation == articulation,
                    onClick = { onSetArticulation(articulation) }
                ) {
                    Text(text = articulation.displayNameEs(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Fila del inspector: etiqueta a la izquierda y opciones desplazables en horizontal. */
@Composable
private fun InspectorRow(
    label: String,
    content: @Composable RowScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** Opción del inspector: se marca la activa, para que el estado de la posición sea siempre visible. */
@Composable
private fun InspectorChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { label() }
        }
    )
}

/**
 * Rejilla «Beat & Stroke»: la vía **rápida** de crear un ritmo, tocando casillas.
 *
 * Cada columna es una subdivisión del compás. Tocar una casilla aplica el **trazo activo** de la
 * paleta; mantener pulsado la deja en aire. La fila superior muestra el número de pulso en las
 * líneas de tiempo y «&» en las subdivisiones átonas, como en la notación de cancionero. En
 * patrones de varios compases, el inicio de cada compás se marca con una barra doble `‖` y la
 * numeración de pulsos se reinicia.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BeatStrokeGrid(
    cells: List<RhythmGridCell>,
    resolution: RhythmGridResolution,
    timeSignature: TimeSignature,
    beatBoundaries: Set<Int>,
    barBoundaries: Set<Int> = emptySet(),
    activeSlot: Int?,
    onTapCell: (Int) -> Unit,
    onLongPressCell: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val slotsPerBeat = resolution.slotsPerBeat(timeSignature)
    val alignsWithBeatUnit =
        slotsPerBeat * resolution.beatValue >= timeSignature.beatUnitQuarterBeats - 1e-6

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cells.forEachIndexed { slot, cell ->
            val onBeatLine = slot % slotsPerBeat == 0

            // El separador `|` se dibuja ANTES de la casilla que inicia la línea de tiempo, para
            // que quede entre el «&» y la cifra del pulso: `1 & | 2 & | 3 & | 4 &`.
            // En patrones de varios compases, el inicio de compás se marca con una barra más
            // gruesa y alta (`‖`), para que se distingan los compases de un vistazo.
            if (slot in beatBoundaries && slot != 0) {
                val isBarStart = slot in barBoundaries
                Box(
                    modifier = Modifier
                        .width(if (isBarStart) 3.dp else 2.dp)
                        .height(if (isBarStart) 60.dp else 48.dp)
                        .background(
                            if (isBarStart) {
                                MaterialTheme.colorScheme.outline
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Etiqueta de tiempo: cifra del pulso o «&» en la subdivisión átona. La cifra se
                // reinicia en cada compás, para que un patrón de dos compases se lea `1 2 3 4 ‖ 1 2 3 4`.
                val slotsPerBar = resolution.slotCount(timeSignature)
                val beatNumber = (slot % slotsPerBar) / slotsPerBeat + 1
                Text(
                    text = if (onBeatLine && alignsWithBeatUnit) beatNumber.toString() else "\u0026",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (onBeatLine) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(gridCellBackground(cell, onBeatLine, isActive = slot == activeSlot))
                        .combinedClickable(
                            onClick = { onTapCell(slot) },
                            onLongClick = { onLongPressCell(slot) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        cell.isAttack -> StrumSymbol(
                            direction = cell.direction,
                            articulation = cell.articulation,
                            range = cell.range,
                            size = 26.dp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        cell.isHeld -> Text(
                            text = "\u2013", // guion: prolonga el golpe anterior
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> Text(
                            text = "\u00B7", // punto: aire (silencio)
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Registro de cuerdas de la casilla (solo cuando hay golpe): ayuda a leer de un vistazo
                // si el trazo barre todas las cuerdas o solo un registro. Ancho FIJO para que todas
                // las columnas midan lo mismo y los separadores de tiempo queden alineados.
                Text(
                    text = when {
                        cell.isAttack && cell.range == StrumRange.BASS -> "gr"
                        cell.isAttack && cell.range == StrumRange.TREBLE -> "ag"
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.width(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Color de la casilla según su estado y si inicia pulso. */
@Composable
private fun gridCellBackground(
    cell: RhythmGridCell,
    onBeatLine: Boolean,
    isActive: Boolean
) = when {
    isActive -> MaterialTheme.colorScheme.primaryContainer
    cell.isAttack && onBeatLine -> MaterialTheme.colorScheme.secondaryContainer
    cell.isAttack -> MaterialTheme.colorScheme.surfaceVariant
    cell.isHeld -> MaterialTheme.colorScheme.surfaceContainerHighest
    else -> MaterialTheme.colorScheme.surface
}

/** Leyenda de los estados de la casilla, para que la rejilla se explique sola. */
@Composable
internal fun GridLegend(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Toca una casilla para aplicar el trazo; mantén pulsado para dejarla en aire.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StrumSymbol(StrumDirection.DOWN, StrumArticulation.NONE, size = 18.dp, range = StrumRange.ALL)
            Text("larga (todas)", style = MaterialTheme.typography.labelSmall)
            StrumSymbol(StrumDirection.DOWN, StrumArticulation.NONE, size = 18.dp, range = StrumRange.BASS)
            Text("corta (graves)", style = MaterialTheme.typography.labelSmall)
            StrumSymbol(StrumDirection.DOWN, StrumArticulation.NONE, size = 18.dp, range = StrumRange.TREBLE)
            Text("corta (agudos)", style = MaterialTheme.typography.labelSmall)
            Text("\u2013 prolonga", style = MaterialTheme.typography.labelSmall)
            Text("\u00B7 aire", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "En las flechas cortas, la banda del glifo indica el registro: arriba graves, abajo agudos.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Paleta de trazos: elige el tipo de flecha que se colocará al tocar las casillas de la rejilla.
 * Es la forma **fácil** de indicar flechas largas (todas las cuerdas) y cortas (graves o agudos),
 * **en las dos direcciones**.
 *
 * Los trazos se agrupan por familia —largos, cortos, apagado y sin golpe— separados por una línea
 * fina, para que la paleta se lea de un vistazo aunque tenga nueve opciones.
 */
@Composable
internal fun StrokePalette(
    active: RhythmGridCell,
    onSelect: (RhythmGridCell) -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = listOf(
        // Golpes largos: barren todas las cuerdas.
        listOf(RhythmGridCell.Down, RhythmGridCell.Up),
        // Golpes cortos: solo un registro. La **banda** del glifo (arriba graves / abajo agudos) y la
        // **punta** de la flecha (abajo / arriba) identifican cada una de las cuatro combinaciones.
        listOf(
            RhythmGridCell(StrumDirection.DOWN, range = StrumRange.BASS),
            RhythmGridCell(StrumDirection.DOWN, range = StrumRange.TREBLE),
            RhythmGridCell(StrumDirection.UP, range = StrumRange.TREBLE),
            RhythmGridCell(StrumDirection.UP, range = StrumRange.BASS)
        ),
        // Apagado.
        listOf(RhythmGridCell.Mute),
        // Sin golpe: aire y prolongación.
        listOf(RhythmGridCell.Air, RhythmGridCell.Hold)
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        groups.forEachIndexed { groupIndex, options ->
            if (groupIndex > 0) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            options.forEach { option ->
                val selected = option == active
                AssistChip(
                    onClick = { onSelect(option) },
                    label = {
                        if (option.isAttack) {
                            StrumSymbol(
                                direction = option.direction,
                                articulation = StrumArticulation.NONE,
                                range = option.range,
                                size = 22.dp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.semantics {
                                    contentDescription = option.paletteDescription()
                                }
                            )
                        } else {
                            Text(
                                text = if (option.isHeld) "\u2013" else "\u00B7",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Seleccionado",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

/** Descripción accesible de un trazo de la paleta: dirección + registro de cuerdas. */
private fun RhythmGridCell.paletteDescription(): String = when {
    isHeld -> "Prolongar el golpe anterior"
    direction == StrumDirection.NONE -> "Aire (silencio)"
    direction == StrumDirection.MUTE -> "Golpe apagado"
    else -> buildString {
        append(if (direction == StrumDirection.DOWN) "Golpe hacia abajo" else "Golpe hacia arriba")
        append(
            when (range) {
                StrumRange.ALL -> ", todas las cuerdas"
                StrumRange.BASS -> ", solo cuerdas graves"
                StrumRange.TREBLE -> ", solo cuerdas agudas"
            }
        )
    }
}

/**
 * Control del número de compases del patrón.
 *
 * Un ritmo puede ocupar **más de un compás** (por ejemplo un patrón de dos compases que se repite
 * en bucle). Este control es el que lo declara: añadir compases rellena con silencios y quitarlos
 * descarta el último.
 */
@Composable
internal fun BarCountControl(
    barCount: Int,
    onAddBar: () -> Unit,
    onRemoveBar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "Compases:", style = MaterialTheme.typography.labelMedium)
        FilledTonalIconButton(onClick = onRemoveBar, enabled = barCount > 1) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "Quitar compás",
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = barCount.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        FilledTonalIconButton(onClick = onAddBar) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Añadir compás",
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** Chip para añadir una figura rítmica (negra, corchea…) o un silencio al patrón. */
@Composable
internal fun FigureChip(
    figure: RhythmFigure,
    onAdd: () -> Unit
) {
    AssistChip(
        onClick = onAdd,
        label = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MusicFigureGlyph(figure = figure, size = 18.dp)
                Text(text = figure.noteName, style = MaterialTheme.typography.labelSmall)
            }
        }
    )
}

/**
 * Barra de **previsualización sonora** del patrón (RF-306): selector de BPM (± / valor exacto)
 * y botón de reproducir/detener. El BPM por defecto es 80.
 */
@Composable
internal fun RhythmPreviewBar(
    bpm: Int,
    isPlaying: Boolean,
    enabled: Boolean,
    onAdjustBpm: (Int) -> Unit,
    onTogglePlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Escuchar el patrón",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(
                onClick = { onAdjustBpm(-5) },
                enabled = !isPlaying
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Bajar tempo")
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$bpm BPM",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = tempoLabel(bpm),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            FilledTonalIconButton(
                onClick = { onAdjustBpm(+5) },
                enabled = !isPlaying
            ) {
                Icon(Icons.Default.Add, contentDescription = "Subir tempo")
            }
            IconButton(
                onClick = onTogglePlayback,
                enabled = enabled
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Detener" else "Reproducir",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/** Indica el tempo de forma aproximada a partir del BPM (grave, moderado, rápido…). */
private fun tempoLabel(bpm: Int): String = when {
    bpm < 60 -> "Lento"
    bpm < 96 -> "Moderado"
    bpm < 140 -> "Animado"
    else -> "Rápido"
}

/** Formatea una duración en negras con un decimal como máximo. */
internal fun formatBeats(beats: Double): String {
    val rounded = kotlin.math.round(beats * 100) / 100.0
    val text = if (kotlin.math.abs(rounded - rounded.toInt()) < 0.001) rounded.toInt().toString()
    else rounded.toString()
    return "$text negras"
}
