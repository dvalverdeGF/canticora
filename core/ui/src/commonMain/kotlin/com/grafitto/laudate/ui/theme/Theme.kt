package com.grafitto.laudate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.grafitto.laudate.ui.theme.M3Baseline as C

/**
 * Esquema de color claro · Material 3 · Static color · Baseline.
 * Mapeo 1:1 de los roles oficiales del esquema baseline a los tonos canónicos.
 */
private val BaselineLightColorScheme = lightColorScheme(
    primary = C.Primary40,
    onPrimary = C.Primary100,
    primaryContainer = C.Primary90,
    onPrimaryContainer = C.Primary10,

    secondary = C.Secondary40,
    onSecondary = C.Primary100,
    secondaryContainer = C.Secondary90,
    onSecondaryContainer = C.Secondary10,

    tertiary = C.Tertiary40,
    onTertiary = C.Primary100,
    tertiaryContainer = C.Tertiary90,
    onTertiaryContainer = C.Tertiary10,

    error = C.Error40,
    onError = C.Error100,
    errorContainer = C.Error90,
    onErrorContainer = C.Error10,

    background = C.Neutral99,
    onBackground = C.Neutral10,
    surface = C.Neutral99,
    onSurface = C.Neutral10,
    surfaceVariant = C.NeutralVariant90,
    onSurfaceVariant = C.NeutralVariant30,

    surfaceTint = C.Primary40,
    inverseSurface = C.Neutral20,
    inverseOnSurface = C.Neutral95,
    inversePrimary = C.Primary80,

    outline = C.NeutralVariant50,
    outlineVariant = C.NeutralVariant80,

    scrim = Color(0xFF000000),

    surfaceBright = C.Neutral98,
    surfaceDim = C.Neutral87,
    surfaceContainer = C.Neutral94,
    surfaceContainerHigh = C.Neutral92,
    surfaceContainerHighest = C.Neutral90,
    surfaceContainerLow = C.Neutral96,
    surfaceContainerLowest = C.Primary100
)

/**
 * Esquema de color oscuro · Material 3 · Static color · Baseline.
 * Apto para ensayos y uso en atril con poca luz.
 */
private val BaselineDarkColorScheme = darkColorScheme(
    primary = C.Primary80,
    onPrimary = C.Primary20,
    primaryContainer = C.Primary30,
    onPrimaryContainer = C.Primary90,

    secondary = C.Secondary80,
    onSecondary = C.Secondary20,
    secondaryContainer = C.Secondary30,
    onSecondaryContainer = C.Secondary90,

    tertiary = C.Tertiary80,
    onTertiary = C.Tertiary20,
    tertiaryContainer = C.Tertiary30,
    onTertiaryContainer = C.Tertiary90,

    error = C.Error80,
    onError = C.Error20,
    errorContainer = C.Error30,
    onErrorContainer = C.Error90,

    background = C.Neutral6,
    onBackground = C.Neutral90,
    surface = C.Neutral6,
    onSurface = C.Neutral90,
    surfaceVariant = C.NeutralVariant30,
    onSurfaceVariant = C.NeutralVariant80,

    surfaceTint = C.Primary80,
    inverseSurface = C.Neutral90,
    inverseOnSurface = C.Neutral20,
    inversePrimary = C.Primary40,

    outline = C.NeutralVariant60,
    outlineVariant = C.NeutralVariant30,

    scrim = Color(0xFF000000),

    surfaceBright = C.Neutral24,
    surfaceDim = C.Neutral6,
    surfaceContainer = C.Neutral12,
    surfaceContainerHigh = C.Neutral17,
    surfaceContainerHighest = C.Neutral22,
    surfaceContainerLow = C.Neutral10,
    surfaceContainerLowest = C.Neutral4
)

@Composable
fun LaudateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BaselineDarkColorScheme else BaselineLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
