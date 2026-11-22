package com.grafitto.laudate.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * **Escala de densidad de Canticora** (diseño *zero waste*).
 *
 * El cancionero se lee y se toca en directo: cada píxel que se lleva el aire es un píxel que no ve
 * la letra. Por eso la interfaz usa una escala de espaciado **muy corta** (2–6 dp) en lugar de los
 * 16 dp habituales de Material, y alturas de fila reducidas pero todavía cómodas al tacto.
 *
 * Se centraliza aquí para que paneles, visor, editores y listados compartan exactamente las mismas
 * medidas y no aparezcan «islas» con más aire que el resto.
 */
object Density {
    /** Línea de separación de 1 px: sustituye al aire entre bloques. */
    val Hairline = 1.dp

    /** Separación mínima entre elementos emparentados. */
    val Tight = 2.dp

    /** Separación corta entre grupos. */
    val Snug = 4.dp

    /** Separación entre bloques distintos. */
    val Compact = 6.dp

    /** Margen interior de una tarjeta o panel. */
    val CardPadding = 8.dp

    /** Altura de una fila de ajuste o de lista. */
    val RowHeight = 40.dp

    /** Altura de una fila con control alto (interruptor, campo de texto). */
    val RowHeightTall = 44.dp

    /** Altura de un micro-badge de directiva. */
    val ChipHeight = 20.dp

    /** Altura de un botón *pill* de acción rápida. */
    val PillHeight = 30.dp

    /** Radio de las tarjetas y paneles. */
    val Corner = 12.dp

    /** Radio de los **paneles laterales** flotantes: más redondeados que una tarjeta normal. */
    val PanelCorner = 16.dp

    /** Radio de los chips y botones pequeños. */
    val CornerSmall = 8.dp

    /** Sombra de un panel lateral flotante: la «altura» de Material que lo separa del contenido. */
    val PanelElevation = 8.dp

    /** Tono de un panel lateral flotante (segunda mitad de la elevación de Material 3). */
    val PanelTone = 2.dp
}

/**
 * **Micro-badge** de directiva: una etiqueta diminuta, redondeada y de alto contraste.
 *
 * Es la unidad de información del diseño compacto: `x2`, `Sopranos`, `f`, `p`, `Tonalidad: Sim`,
 * `Capo: 2`, `4/4`… Todo lo que el músico necesita saber de un vistazo cabe en 20 dp de alto, sin
 * robarle una línea a la letra.
 *
 * @param color acento del badge; se usa al 14 % de fondo y al 100 % en el texto para que contraste
 *   sobre cualquier superficie del tema.
 * @param onClick si se pasa, el badge es pulsable (p. ej. para saltar a una sección).
 */
@Composable
fun MicroChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    icon: DrawableResource? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .height(Density.ChipHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(percent = 50),
        color = color.copy(alpha = 0.14f),
        contentColor = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            icon?.let {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
            }
            // Hueco para un **glifo musical** (p. ej. la negra de `♩ = 80`): no se escribe con el
            // texto porque en la web las fuentes del motor no traen los símbolos musicales.
            leading?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Fila de **micro-badges** que se desplaza en horizontal si no caben. Es la forma canónica de
 * mostrar las directivas de un canto o de una sección sin partir el texto ni reservar altura.
 */
@Composable
fun MicroChipRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Density.Snug),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}

/**
 * **Botón *pill*** de acción rápida: compacto, redondeado y con icono opcional.
 *
 * Sustituye a los botones de ancho completo en las barras de herramientas: ocupa lo justo, se
 * coloca en el margen o al pie y no le roba espacio a la letra.
 *
 * Si [text] va vacío, el botón se queda **solo con el icono** (y usa [iconDescription] para
 * accesibilidad): es la forma de meter acciones evidentes sin gastar ancho en una palabra.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    iconDescription: String? = null
) {
    val container = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest
        selected -> color.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> color
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val showText = text.isNotBlank()
    Surface(
        modifier = modifier
            .height(Density.PillHeight)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(percent = 50),
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (showText) 10.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Density.Snug)
        ) {
            icon?.let {
                Icon(
                    it,
                    contentDescription = if (showText) null else iconDescription,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (showText) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * **Mapa de estructura** del canto: la línea de tiempo de sus partes en badges diminutos
 * (`Int`, `Est 1`, `Coro`, `Puente`, `Fin`).
 *
 * Permite al músico escanear la forma de la pieza en décimas de segundo y saltar a una parte
 * concreta sin recorrer la letra. Va pegado al borde superior del visor, separado del contenido
 * solo por una línea de 1 px.
 */
@Composable
fun StructureMap(
    badges: List<StructureBadge>,
    modifier: Modifier = Modifier,
    onSelect: ((Int) -> Unit)? = null
) {
    if (badges.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Density.Compact, vertical = Density.Snug),
        horizontalArrangement = Arrangement.spacedBy(Density.Tight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        badges.forEachIndexed { index, badge ->
            Surface(
                modifier = Modifier
                    .height(Density.ChipHeight)
                    .then(
                        if (onSelect != null) {
                            Modifier.clickable { onSelect(index) }
                        } else {
                            Modifier
                        }
                    ),
                shape = RoundedCornerShape(Density.CornerSmall),
                color = if (badge.selected) {
                    badge.color.copy(alpha = 0.22f)
                } else {
                    badge.color.copy(alpha = 0.10f)
                },
                contentColor = badge.color
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badge.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (badge.selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Una parte del canto dentro del [StructureMap]: su etiqueta corta y su acento de color. */
data class StructureBadge(
    val label: String,
    val color: Color,
    val selected: Boolean = false
)

/**
 * **Línea de separación** de 1 px entre bloques pegados. Es el recurso del diseño compacto para
 * delimitar sin gastar aire: donde antes había 16 dp de hueco, ahora hay una línea.
 */
@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Density.Hairline)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * **Paleta de acordes frecuentes**: una fila de *pills* con los acordes que más se escriben
 * (`Do`, `Re`, `Mi`, `Lam`, `Sol7`…), para insertarlos en el editor con un toque en lugar de
 * teclear. Va al pie del editor, pegada al borde, sin robarle altura a la letra.
 */
@Composable
fun ChordPalette(
    chords: List<String>,
    onChordClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (chords.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Density.Compact, vertical = Density.Snug),
        horizontalArrangement = Arrangement.spacedBy(Density.Snug),
        verticalAlignment = Alignment.CenterVertically
    ) {
        chords.forEach { chord ->
            Surface(
                modifier = Modifier
                    .height(Density.PillHeight)
                    .clickable { onChordClick(chord) },
                shape = RoundedCornerShape(Density.CornerSmall),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = chord,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * **Rejilla de acordes frecuentes**: los mismos *pills* de [ChordPalette], pero repartidos en
 * **dos filas fijas** que ocupan todo el ancho, sin desplazamiento lateral. Así se ven todos los
 * acordes de un vistazo y no hay que arrastrar la fila para descubrir que faltaban.
 */
@Composable
fun ChordPaletteGrid(
    chords: List<String>,
    onChordClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 8
) {
    if (chords.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Density.Tight)
    ) {
        chords.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Density.Tight)
            ) {
                row.forEach { chord ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(Density.PillHeight)
                            .clickable { onChordClick(chord) },
                        shape = RoundedCornerShape(Density.CornerSmall),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = chord,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
                // Relleno para que la última fila incompleta no estire sus botones.
                repeat(columns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Separador vertical fino para las barras de herramientas compactas. */
@Composable
fun HairlineVerticalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(Density.Hairline)
            .height(20.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** Espaciador vertical de la escala de densidad. */
@Composable
fun DensitySpacer(height: androidx.compose.ui.unit.Dp) {
    Spacer(Modifier.height(height))
}

/** Recorta un modificador a la esquina de densidad estándar. */
fun Modifier.densityClip(): Modifier = clip(RoundedCornerShape(Density.Corner))
