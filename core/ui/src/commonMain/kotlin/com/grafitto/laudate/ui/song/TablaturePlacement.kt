package com.grafitto.laudate.ui.song

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.tablature.TabDisplayPosition
import com.grafitto.laudate.domain.model.tablature.Tablature
import com.grafitto.laudate.ui.common.Density
import com.grafitto.laudate.ui.common.HairlineVerticalDivider
import com.grafitto.laudate.ui.common.MicroChip
import com.grafitto.laudate.ui.navigation.CanticoraIcons
import com.grafitto.laudate.ui.navigation.SettingsIcon
import com.grafitto.laudate.ui.navigation.SettingsOption
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * **Barra de colocación de la tablatura** (ADR-017): el mando único para *ver*, *elegir* y *mover*
 * las tablaturas de un canto sin salir del visor ni de la vista previa del editor.
 *
 * Reúne las tres decisiones que el usuario toma sobre una tablatura:
 *  1. **Cuál se ve** — cuando el canto tiene varias, una fila de *chips* con su nombre; se muestra
 *     solo la elegida (las demás quedan a un toque).
 *  2. **Dónde va** — la posición del bloque respecto a la letra (arriba / abajo / a la derecha) y la
 *     **sección** junto a la que se intercala (o el bloque general del final).
 *  3. **En qué orden** — subirla o bajarla dentro de su grupo.
 *
 * Es la misma barra en la vista previa y en el panel de herramientas: un solo sitio donde aprender
 * el gesto. Va pegada al borde, sin caja ni sombra, para no robarle altura a la música.
 */
@Composable
fun TablaturePlacementBar(
    tablatures: List<Tablature>,
    selectedTablatureId: String?,
    sections: List<SettingsOption>,
    tabDisplayPosition: TabDisplayPosition,
    onSelectTablature: (String) -> Unit,
    onSelectPosition: (TabDisplayPosition) -> Unit,
    onSelectSection: (String?) -> Unit,
    onMoveOrder: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** La posición respecto a la letra solo tiene sentido si la letra se está viendo. */
    showPositionControl: Boolean = true
) {
    if (tablatures.isEmpty()) return
    val selected = tablatures.firstOrNull { it.id == selectedTablatureId } ?: tablatures.first()
    val chipLabels = remember(tablatures, sections) { tablatureChipLabels(tablatures, sections) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Density.CardPadding, vertical = Density.Snug),
            horizontalArrangement = Arrangement.spacedBy(Density.Snug),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── 1. Cuál se ve ───────────────────────────────────────────────────────────────────
            if (tablatures.size > 1) {
                tablatures.forEachIndexed { index, tab ->
                    MicroChip(
                        text = chipLabels[index],
                        color = if (tab.id == selected.id) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        onClick = { onSelectTablature(tab.id) }
                    )
                }
                HairlineVerticalDivider()
            }

            // ── 2a. Dónde va: posición respecto a la letra ──────────────────────────────────────
            if (showPositionControl) {
                PlacementIconButton(
                    icon = CanticoraIcons.PositionTop,
                    description = "Tablatura arriba de la letra",
                    selected = tabDisplayPosition == TabDisplayPosition.BEFORE,
                    onClick = { onSelectPosition(TabDisplayPosition.BEFORE) }
                )
                PlacementIconButton(
                    icon = CanticoraIcons.PositionBottom,
                    description = "Tablatura debajo de la letra",
                    selected = tabDisplayPosition == TabDisplayPosition.AFTER,
                    onClick = { onSelectPosition(TabDisplayPosition.AFTER) }
                )
                PlacementIconButton(
                    icon = CanticoraIcons.PositionRight,
                    description = "Tablatura a la derecha de la letra",
                    selected = tabDisplayPosition == TabDisplayPosition.RIGHT,
                    onClick = { onSelectPosition(TabDisplayPosition.RIGHT) }
                )
                HairlineVerticalDivider()
            }

            // ── 2b. Dónde va: sección junto a la que se intercala ───────────────────────────────
            SectionPill(
                sections = sections,
                currentSectionId = selected.sectionId,
                onSelect = onSelectSection
            )

            // ── 3. En qué orden ─────────────────────────────────────────────────────────────────
            HairlineVerticalDivider()
            PlacementIconButton(
                icon = CanticoraIcons.PositionTop,
                description = "Subir la tablatura",
                selected = false,
                onClick = { onMoveOrder(-1) }
            )
            PlacementIconButton(
                icon = CanticoraIcons.PositionBottom,
                description = "Bajar la tablatura",
                selected = false,
                onClick = { onMoveOrder(1) }
            )
        }
    }
}

/**
 * Botón **solo icono** de la barra de colocación. Se tiñe con el color primario cuando representa la
 * opción activa, para que la posición elegida se lea de un vistazo sin texto.
 */
@Composable
private fun PlacementIconButton(
    icon: DrawableResource,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            .size(Density.PillHeight)
            .clip(RoundedCornerShape(Density.CornerSmall))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Density.CornerSmall),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            Color.Transparent
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(icon),
                contentDescription = description,
                modifier = Modifier.size(18.dp),
                tint = tint
            )
        }
    }
}

/**
 * *Pill* con la **sección** a la que se ata la tablatura. Al pulsarla se despliega la lista de
 * secciones del canto más la opción **General** (bloque común del final). Es la forma de «arrastrar»
 * la tablatura a la intro, la estrofa o el coro sin salir del visor.
 */
@Composable
private fun SectionPill(
    sections: List<SettingsOption>,
    currentSectionId: String?,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = sections.firstOrNull { it.id == currentSectionId }?.label ?: "General"

    Box {
        Surface(
            modifier = Modifier
                .height(Density.PillHeight)
                .clip(RoundedCornerShape(Density.CornerSmall))
                .clickable { expanded = true },
            shape = RoundedCornerShape(Density.CornerSmall),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Density.Compact),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Density.Snug)
            ) {
                SettingsIcon(icon = CanticoraIcons.ToolSections, size = 16.dp)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "Elegir la sección de la tablatura",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("General (al final)") },
                onClick = {
                    expanded = false
                    onSelect(null)
                }
            )
            sections.forEach { section ->
                DropdownMenuItem(
                    text = { Text(section.label) },
                    onClick = {
                        expanded = false
                        onSelect(section.id)
                    }
                )
            }
        }
    }
}

/**
 * Variante **de panel** de la barra de colocación: en el panel de herramientas las opciones van
 * apiladas en filas de ajuste (icono + etiqueta + control), no en una tira horizontal.
 */
@Composable
fun TablaturePlacementPanel(
    tablatures: List<Tablature>,
    selectedTablatureId: String?,
    sections: List<SettingsOption>,
    tabDisplayPosition: TabDisplayPosition,
    onSelectTablature: (String) -> Unit,
    onSelectPosition: (TabDisplayPosition) -> Unit,
    onSelectSection: (String?) -> Unit,
    onMoveOrder: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tablatures.isEmpty()) return
    val selected = tablatures.firstOrNull { it.id == selectedTablatureId } ?: tablatures.first()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Density.Snug)
    ) {
        if (tablatures.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Density.CardPadding),
                horizontalArrangement = Arrangement.spacedBy(Density.Snug),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tablatures.forEach { tab ->
                    MicroChip(
                        text = tab.title.ifBlank { tab.instrument },
                        color = if (tab.id == selected.id) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        onClick = { onSelectTablature(tab.id) }
                    )
                }
            }
        }

        PlacementRow(
            icon = CanticoraIcons.TabPosition,
            label = "Posición de la tablatura",
            value = when (tabDisplayPosition) {
                TabDisplayPosition.BEFORE -> "Arriba"
                TabDisplayPosition.AFTER -> "Abajo"
                TabDisplayPosition.RIGHT -> "A la derecha"
            }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Density.Snug)) {
                PlacementIconButton(
                    icon = CanticoraIcons.PositionTop,
                    description = "Tablatura arriba de la letra",
                    selected = tabDisplayPosition == TabDisplayPosition.BEFORE,
                    onClick = { onSelectPosition(TabDisplayPosition.BEFORE) }
                )
                PlacementIconButton(
                    icon = CanticoraIcons.PositionBottom,
                    description = "Tablatura debajo de la letra",
                    selected = tabDisplayPosition == TabDisplayPosition.AFTER,
                    onClick = { onSelectPosition(TabDisplayPosition.AFTER) }
                )
                PlacementIconButton(
                    icon = CanticoraIcons.PositionRight,
                    description = "Tablatura a la derecha de la letra",
                    selected = tabDisplayPosition == TabDisplayPosition.RIGHT,
                    onClick = { onSelectPosition(TabDisplayPosition.RIGHT) }
                )
            }
        }

        PlacementRow(
            icon = CanticoraIcons.ToolSections,
            label = "Sección de la tablatura",
            value = sections.firstOrNull { it.id == selected.sectionId }?.label ?: "General"
        ) {
            SectionPill(
                sections = sections,
                currentSectionId = selected.sectionId,
                onSelect = onSelectSection
            )
        }

        PlacementRow(
            icon = CanticoraIcons.Sort,
            label = "Orden de la tablatura",
            value = "${tablatures.indexOf(selected) + 1} de ${tablatures.size}"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Density.Snug)) {
                PlacementIconButton(
                    icon = CanticoraIcons.PositionTop,
                    description = "Subir la tablatura",
                    selected = false,
                    onClick = { onMoveOrder(-1) }
                )
                PlacementIconButton(
                    icon = CanticoraIcons.PositionBottom,
                    description = "Bajar la tablatura",
                    selected = false,
                    onClick = { onMoveOrder(1) }
                )
            }
        }
    }
}

/** Fila de ajuste compacta: icono + etiqueta + valor a la izquierda, control a la derecha. */
@Composable
private fun PlacementRow(
    icon: DrawableResource,
    label: String,
    value: String,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Density.RowHeight)
            .padding(start = Density.CardPadding, end = Density.Snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Density.Compact)
    ) {
        SettingsIcon(icon = icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        control()
    }
}

/**
 * Etiquetas cortas y **distinguibles** de las tablaturas para los *chips* de selección.
 *
 * El nombre de una tablatura hereda el del canto, así que usarlo aquí pintaba dos pastillas con el
 * mismo texto —una morada (la elegida) y otra gris— sin decir cuál era cuál. Se prefiere el rótulo
 * del copista, luego la sección a la que está asociada y, si no hay nada, su número de orden. Si
 * aun así dos etiquetas coinciden, se les añade el número para que nunca se confundan.
 */
private fun tablatureChipLabels(
    tablatures: List<Tablature>,
    sections: List<SettingsOption>
): List<String> {
    val base = tablatures.mapIndexed { index, tab ->
        tab.label?.takeIf { it.isNotBlank() }
            ?: sections.firstOrNull { it.id == tab.sectionId }?.label
            ?: "Tablatura ${index + 1}"
    }
    val repeated = base.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    return base.mapIndexed { index, label -> if (label in repeated) "$label ${index + 1}" else label }
}
