package com.grafitto.laudate.ui.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategory
import com.grafitto.laudate.domain.model.liturgy.LiturgicalCategoryKind
import com.grafitto.laudate.domain.platform.newId

/**
 * Hoja de gestión de categorías y momentos litúrgicos.
 *
 * Permite crear, **editar (renombrar y cambiar el tipo)** y **eliminar** cualquier categoría,
 * incluidas las canónicas precargadas: su condición de «canónica» solo describe su origen, no las
 * hace inmutables (RF-103). Al borrar una categoría las canciones que la usaran se quedan sin esa
 * etiqueta, pero **no se borra ningún canto**; las canónicas se pueden recrear con «Restaurar
 * categorías estándar».
 *
 * Es una pantalla sin ViewModel propio: recibe los callbacks del llamante (`MainActivity`), que
 * delega en `CategoryRepository` / `UpdateCategoryUseCase`.
 *
 * @param songCounts número de canciones etiquetadas con cada categoría (por `id`), para mostrarlo
 *   entre paréntesis junto al nombre.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementSheet(
    categories: List<LiturgicalCategory>,
    onAddCategory: (LiturgicalCategory) -> Unit,
    onEditCategory: (LiturgicalCategory, String, LiturgicalCategoryKind) -> Unit,
    onDeleteCategory: (String) -> Unit,
    onDismissRequest: () -> Unit,
    songCounts: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier
) {
    var dialogTarget by remember { mutableStateOf<CategoryDialogTarget?>(null) }
    var categoryToDelete by remember { mutableStateOf<LiturgicalCategory?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Categorías Litúrgicas",
                    style = MaterialTheme.typography.titleLarge
                )

                Button(onClick = { dialogTarget = CategoryDialogTarget() }) {
                    Icon(Icons.Default.Add, contentDescription = "Nueva")
                    Spacer(modifier = Modifier.padding(2.dp))
                    Text("Nueva")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories, key = { it.id }) { cat ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${cat.name} (${songCounts[cat.id] ?: 0})",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = buildString {
                                        append(cat.kind.displayName)
                                        append(" · ")
                                        append(if (cat.isSystemStandard) "canónica" else "personalizada")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Editar está disponible para TODAS las categorías (incluidas las canónicas).
                            IconButton(onClick = { dialogTarget = CategoryDialogTarget(cat) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Editar",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Eliminar también está disponible para TODAS: borrar una categoría
                            // nunca borra canciones, solo las deja sin esa etiqueta.
                            IconButton(onClick = { categoryToDelete = cat }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Eliminar",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    categoryToDelete?.let { cat ->
        val count = songCounts[cat.id] ?: 0
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Eliminar categoría") },
            text = {
                Text(
                    if (count > 0) {
                        "¿Eliminar «${cat.name}»? $count canción(es) dejarán de tener esta " +
                            "categoría, pero no se borrará ninguna canción."
                    } else {
                        "¿Eliminar «${cat.name}»? Esta acción no se puede deshacer."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        categoryToDelete = null
                        onDeleteCategory(cat.id)
                    }
                ) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    dialogTarget?.let { target ->
        CategoryFormDialog(
            target = target,
            onConfirm = { name, kind ->
                if (target.category == null) {
                    onAddCategory(
                        LiturgicalCategory(
                            id = newId(),
                            name = name,
                            isSystemStandard = false,
                            kind = kind
                        )
                    )
                } else {
                    onEditCategory(target.category, name, kind)
                }
                dialogTarget = null
            },
            onDismiss = { dialogTarget = null }
        )
    }
}

/** Estado del diálogo de categoría: sin categoría para crear, con categoría para editar. */
private data class CategoryDialogTarget(val category: LiturgicalCategory? = null)

/**
 * Diálogo único de alta y edición de categoría. Al editar precarga el nombre y el tipo actuales.
 * La clave `remember(target.category?.id)` garantiza que al cambiar de categoría se reinicie el formulario.
 */
@Composable
private fun CategoryFormDialog(
    target: CategoryDialogTarget,
    onConfirm: (String, LiturgicalCategoryKind) -> Unit,
    onDismiss: () -> Unit
) {
    val isEditing = target.category != null
    var name by remember(target.category?.id) { mutableStateOf(target.category?.name.orEmpty()) }
    var selectedKind by remember(target.category?.id) {
        mutableStateOf(target.category?.kind ?: LiturgicalCategoryKind.ADDITIONAL)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Editar Categoría" else "Nueva Categoría") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del momento o categoría") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Tipo de categoría:",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                LiturgicalCategoryKind.entries.forEach { kind ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedKind = kind }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedKind == kind,
                            onClick = { selectedKind = kind }
                        )
                        Text(text = kind.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) onConfirm(trimmed, selectedKind)
                }
            ) {
                Text(if (isEditing) "Guardar" else "Crear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
