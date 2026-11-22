package com.grafitto.laudate.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.grafitto.laudate.domain.model.song.SongSearchCriteria
import com.grafitto.laudate.domain.model.song.SongSortOrder
import com.grafitto.laudate.domain.rhythm.RhythmGridResolution
import com.grafitto.laudate.domain.model.rhythm.RhythmNotationMode

/**
 * Ajustes **propios de cada sección** del panel derecho.
 *
 * El panel de ajustes no es el mismo en todas las pantallas: cambia con la sección activa. Aquí
 * viven los de las secciones que **no** son editores —catálogo, repertorios y patrones rítmicos—,
 * de modo que el usuario ve, en el mismo sitio de siempre, lo que puede ajustar **donde está**.
 *
 * Todos son ajustes **reales**: escriben en el estado de la pantalla (criterios de búsqueda, filtro
 * de tipo, notación de los ritmos), no en interruptores decorativos.
 */

/** Id del desplegable que representa «todos los momentos litúrgicos». */
private const val ALL_MOMENTS = "__all_moments__"

/** Id del desplegable que representa «todas las tonalidades». */
private const val ALL_KEYS = "__all_keys__"

/** Tonalidades ofrecidas como filtro del catálogo. */
private val CATALOG_KEYS = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

/**
 * Ajustes de la sección **Canciones**: cómo se ordena el catálogo y qué se está filtrando.
 *
 * Reproduce en el panel los filtros que el catálogo ya sabe aplicar, para poder acotar la lista sin
 * tener que desplegar la hoja de filtros de la pantalla.
 *
 * @param moments nombres de los momentos litúrgicos disponibles (los del catálogo).
 */
@Composable
fun CatalogToolSettings(
    criteria: SongSearchCriteria,
    onCriteriaChange: (SongSearchCriteria) -> Unit,
    moments: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsButtonGroup(
            label = "Ordenar por",
            options = SongSortOrder.entries.map { SettingsOption(it.name, it.shortName) },
            selectedId = criteria.sort.name,
            onSelect = { id ->
                SongSortOrder.entries.firstOrNull { it.name == id }
                    ?.let { onCriteriaChange(criteria.copy(sort = it)) }
            },
            icons = mapOf(
                SongSortOrder.TITLE.name to CanticoraIcons.SortTitle,
                SongSortOrder.NUMBER.name to CanticoraIcons.SortNumber,
                SongSortOrder.RECENT.name to CanticoraIcons.SortRecent
            )
        )
        SettingsSwitchRow(
            icon = CanticoraIcons.Favorite,
            label = "Solo favoritos",
            checked = criteria.onlyFavorites,
            onCheckedChange = { onCriteriaChange(criteria.copy(onlyFavorites = it)) }
        )
        SettingsSwitchRow(
            icon = CanticoraIcons.Uncategorized,
            label = "Solo sin categoría",
            checked = criteria.onlyUncategorized,
            onCheckedChange = { onCriteriaChange(criteria.copy(onlyUncategorized = it)) }
        )
        if (moments.isNotEmpty()) {
            val current = criteria.momentNames.firstOrNull()
            SettingsDropdownRow(
                icon = CanticoraIcons.Moment,
                label = "Momento litúrgico",
                value = current ?: "Todos",
                selectedId = current ?: ALL_MOMENTS,
                options = listOf(SettingsOption(ALL_MOMENTS, "Todos")) +
                    moments.map { SettingsOption(it, it) },
                onSelect = { id ->
                    onCriteriaChange(
                        criteria.copy(momentNames = if (id == ALL_MOMENTS) emptySet() else setOf(id))
                    )
                }
            )
        }
        val currentKey = criteria.keys.firstOrNull()
        SettingsDropdownRow(
            icon = CanticoraIcons.Key,
            label = "Tonalidad",
            value = currentKey ?: "Todas",
            selectedId = currentKey ?: ALL_KEYS,
            options = listOf(SettingsOption(ALL_KEYS, "Todas")) +
                CATALOG_KEYS.map { SettingsOption(it, it) },
            onSelect = { id ->
                onCriteriaChange(criteria.copy(keys = if (id == ALL_KEYS) emptySet() else setOf(id)))
            }
        )
    }
}

/**
 * Ajustes de la sección **Misas y Eventos**: qué tipo de repertorio se lista y cómo se exporta.
 *
 * @param typeFilter tipo activo (`null` = todos).
 */
@Composable
fun CelebrationListToolSettings(
    typeFilter: String?,
    onTypeFilterChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsButtonGroup(
            label = "Tipo de repertorio",
            options = listOf(SettingsOption(ALL_MOMENTS, "Todos")) +
                CELEBRATION_TYPES.map { SettingsOption(it, it) },
            selectedId = typeFilter ?: ALL_MOMENTS,
            onSelect = { id -> onTypeFilterChange(id.takeIf { it != ALL_MOMENTS }) },
            icons = mapOf(
                ALL_MOMENTS to CanticoraIcons.All,
                "Misa" to CanticoraIcons.Masses,
                "Evento" to CanticoraIcons.Event
            )
        )
    }
}

/** Tipos de repertorio que ofrece la sección de misas y eventos. */
private val CELEBRATION_TYPES = listOf("Misa", "Evento")

/**
 * Ajustes de la sección **Patrones rítmicos**: cómo se dibujan los ritmos (notación) y con qué
 * subdivisión se escribe la rejilla.
 */
@Composable
fun RhythmToolSettings(
    notationMode: RhythmNotationMode,
    onNotationModeChange: (RhythmNotationMode) -> Unit,
    gridResolution: RhythmGridResolution,
    onGridResolutionChange: (RhythmGridResolution) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SettingsButtonGroup(
            label = "Notación",
            options = RhythmNotationMode.entries.map { SettingsOption(it.name, notationShortLabel(it)) },
            selectedId = notationMode.name,
            onSelect = { id ->
                RhythmNotationMode.entries.firstOrNull { it.name == id }?.let(onNotationModeChange)
            },
            icons = mapOf(
                RhythmNotationMode.ARROWS.name to CanticoraIcons.Arrows,
                RhythmNotationMode.MUSICAL_SYMBOLS.name to CanticoraIcons.Key,
                RhythmNotationMode.COMBINED.name to CanticoraIcons.Rhythms,
                RhythmNotationMode.CHORDPRO_SYMBOLS.name to CanticoraIcons.Symbols
            )
        )
        SettingsButtonGroup(
            label = "Subdivisión",
            options = RhythmGridResolution.entries.map { SettingsOption(it.name, it.label) },
            selectedId = gridResolution.name,
            onSelect = { id ->
                RhythmGridResolution.entries.firstOrNull { it.name == id }?.let(onGridResolutionChange)
            }
        )
    }
}

/** Etiqueta corta de un modo de notación rítmica (cabe en una botonera). */
private fun notationShortLabel(mode: RhythmNotationMode): String = when (mode) {
    RhythmNotationMode.ARROWS -> "Flechas"
    RhythmNotationMode.MUSICAL_SYMBOLS -> "Figuras"
    RhythmNotationMode.COMBINED -> "Combinada"
    RhythmNotationMode.CHORDPRO_SYMBOLS -> "ChordPro"
}
