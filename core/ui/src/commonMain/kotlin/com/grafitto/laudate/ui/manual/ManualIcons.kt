package com.grafitto.laudate.ui.manual

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.grafitto.laudate.domain.model.manual.ManualChapterIcon

/**
 * Traduce el icono **semántico** de un capítulo del manual a un icono de Material.
 *
 * La correspondencia vive en la UI porque es un detalle de presentación: el Dominio solo dice qué
 * representa el icono. Se usan vectores y no emojis porque el emoji depende de la fuente del
 * sistema y en la versión web se dibujaba como un hueco vacío.
 */
internal fun ManualChapterIcon.vector(): ImageVector = when (this) {
    ManualChapterIcon.START -> Icons.Default.RocketLaunch
    ManualChapterIcon.CATALOG -> Icons.Default.LibraryMusic
    ManualChapterIcon.SEARCH -> Icons.Default.Search
    ManualChapterIcon.READING -> Icons.Default.MenuBook
    ManualChapterIcon.TRANSPOSE -> Icons.Default.Piano
    ManualChapterIcon.TABLATURE -> Icons.Default.MusicNote
    ManualChapterIcon.RHYTHM -> Icons.Default.GraphicEq
    ManualChapterIcon.CELEBRATION -> Icons.Default.Church
    ManualChapterIcon.CATEGORIES -> Icons.Default.Label
    ManualChapterIcon.EDITOR -> Icons.Default.Edit
    ManualChapterIcon.BACKUP -> Icons.Default.Save
    ManualChapterIcon.IMPORT -> Icons.Default.Download
    ManualChapterIcon.CHORDPRO -> Icons.Default.Article
    ManualChapterIcon.TIPS -> Icons.Default.Lightbulb
}
