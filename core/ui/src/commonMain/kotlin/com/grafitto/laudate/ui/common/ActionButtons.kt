package com.grafitto.laudate.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Acciones de pantalla unificadas para canciones, misas y ritmos.
 *
 * Todas las pantallas de listado y de edición comparten el mismo vocabulario visual:
 *
 *  - **Guardar / Editar** → color primario (acción principal, destacada).
 *  - **Eliminar** → color de error (acción destructiva).
 *  - **Compartir** → color secundario.
 *  - **Duplicar / Copiar texto** → neutro.
 *  - **PDF** → color terciario.
 *
 * Los botones son **solo icono** (sin texto): así caben siempre en una fila horizontal sin
 * apilarse en vertical cuando la pantalla es estrecha (móvil). El nombre de cada acción viaja en
 * la `contentDescription` (accesibilidad) y en el `label` (tooltip nativo en escritorio).
 */
@Composable
fun ActionButtonGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Botón principal de guardado: relleno con el color primario del tema. */
@Composable
fun SaveActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSaving: Boolean = false,
    label: String = "Guardar"
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled && !isSaving,
        modifier = modifier,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(Icons.Default.Check, contentDescription = label)
        }
    }
}

/** Botón destructivo: tonal con el color de error del tema. */
@Composable
fun DeleteActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "Eliminar"
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Icon(Icons.Default.Delete, contentDescription = label)
    }
}

/** Botón de compartición: tonal con el color secundario del tema. */
@Composable
fun ShareActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "Compartir"
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Icon(Icons.Default.Share, contentDescription = label)
    }
}

/** Botón de duplicado: tonal neutro, con el icono de copia de fichero (distinto de «Copiar texto»). */
@Composable
fun DuplicateActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "Duplicar"
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(Icons.Default.FileCopy, contentDescription = label)
    }
}

/** Botón «Copiar como texto»: tonal neutro con el icono de portapapeles. */
@Composable
fun CopyTextActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "Copiar texto"
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(Icons.Default.ContentCopy, contentDescription = label)
    }
}

/** Botón de exportación a PDF: tonal con el color terciario del tema. */
@Composable
fun PdfActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "PDF"
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        )
    ) {
        Icon(Icons.Default.PictureAsPdf, contentDescription = label)
    }
}

/**
 * Entrada de un menú de acciones de tarjeta (los «tres puntos» de los listados).
 *
 * Usa el mismo código de color que los botones de los editores: [ActionTint.PRIMARY] para la
 * acción principal (abrir/editar), [ActionTint.ERROR] para la destructiva y
 * [ActionTint.NEUTRAL] para el resto.
 */
enum class ActionTint { PRIMARY, ERROR, NEUTRAL }

@Composable
fun ActionMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: ActionTint = ActionTint.NEUTRAL
) {
    val color: Color = when (tint) {
        ActionTint.PRIMARY -> MaterialTheme.colorScheme.primary
        ActionTint.ERROR -> MaterialTheme.colorScheme.error
        ActionTint.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    DropdownMenuItem(
        text = { Text(text, color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    )
}
