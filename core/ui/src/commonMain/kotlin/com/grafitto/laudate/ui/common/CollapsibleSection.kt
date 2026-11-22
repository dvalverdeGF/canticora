package com.grafitto.laudate.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * **Sección plegable** de un formulario: una cabecera tocable con el título y un chevron, y su
 * contenido, que se muestra u oculta. Es la pieza que permite convertir los editores verticales
 * (todo apilado, con scroll interminable) en un **panel de ajustes plegado por defecto**.
 *
 * Se pliega/despliega con estado local (`remember`) o **controlado** por el llamante
 * ([expanded] + [onExpandedChange]), para poder recordar el estado globalmente si interesa.
 *
 * @param title rótulo de la sección.
 * @param subtitle texto secundario opcional (p. ej. el valor actual: «Cejilla», «Guitarra»).
 * @param initiallyExpanded si se abre al aparecer (por defecto **plegada**: prioriza el contenido).
 */
@Composable
fun CollapsibleSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    initiallyExpanded: Boolean = false,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    /**
     * Color del titulo. `null` usa el acento del tema (formularios de la app); las **paletas** del
     * editor de tablatura pasan el color neutro de texto, porque en MuseScore las paletas no van
     * coloreadas (P-5.4).
     */
    titleColor: androidx.compose.ui.graphics.Color? = null,
    /**
     * **Cabecera compacta** (D-79): chevron pequeño a la **izquierda** del título, tipografía de
     * etiqueta y poco relleno vertical. Es el modo de las **paletas** del editor de tablatura, donde
     * la cabecera es solo un rótulo de grupo y no debe competir con los símbolos: con la cabecera
     * grande, una paleta plegada se leía como «una caja enorme con un simbolito dentro».
     */
    dense: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var localExpanded by remember { mutableStateOf(initiallyExpanded) }
    val isExpanded = expanded ?: localExpanded
    val toggle: (Boolean) -> Unit = { next ->
        if (expanded == null) localExpanded = next
        onExpandedChange?.invoke(next)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { toggle(!isExpanded) }
                // El título no puede quedar a ras del borde del panel: se alinea con el resto de
                // filas de ajustes (mismo `start` que `SettingsDropdownRow`).
                .padding(
                    start = if (dense) 0.dp else Density.CardPadding,
                    end = Density.Snug,
                    top = if (dense) 2.dp else 6.dp,
                    bottom = if (dense) 2.dp else 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 8.dp)
        ) {
            // (D-79) En modo compacto el chevron va **delante** del título, como en las paletas de
            // MuseScore: así el rótulo empieza en el borde y la fila no parece un botón vacío.
            if (dense) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Contraer $title" else "Expandir $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = title,
                style = if (dense) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.titleSmall
                },
                fontWeight = FontWeight.SemiBold,
                color = titleColor ?: MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f, fill = false)
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
            if (!dense) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Contraer $title" else "Expandir $title",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // Filete pegado a la cabecera: el bloque se lee como un título con su contenido debajo.
        HairlineDivider()
        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (dense) 0.dp else Density.CardPadding,
                        top = if (dense) 2.dp else 4.dp,
                        bottom = if (dense) 2.dp else 4.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (dense) 2.dp else 10.dp),
                content = content
            )
        }
    }
}

/**
 * Sección plegable **secundaria** (ajustes avanzados): mismo comportamiento, título más discreto.
 * Se usa para agrupar las herramientas que se tocan una vez por canción y no deben competir con el
 * contenido principal.
 */
@Composable
fun AdvancedSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    CollapsibleSection(
        title = title,
        subtitle = subtitle,
        initiallyExpanded = initiallyExpanded,
        modifier = modifier
    ) { content() }
}
