package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

fun AppThemePalette.toColorScheme(): ColorScheme {
    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = surfaceVariant,
            onPrimaryContainer = text,
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = text,
            tertiary = primary,
            onTertiary = onPrimary,
            background = bg,
            surface = surface,
            surfaceVariant = surfaceVariant,
            onBackground = text,
            onSurface = text,
            onSurfaceVariant = textMuted,
            outline = border,
            outlineVariant = border.copy(alpha = 0.5f)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = surfaceVariant,
            onPrimaryContainer = text,
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = text,
            tertiary = primary,
            onTertiary = onPrimary,
            background = bg,
            surface = surface,
            surfaceVariant = surfaceVariant,
            onBackground = text,
            onSurface = text,
            onSurfaceVariant = textMuted,
            outline = border,
            outlineVariant = border.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun MyApplicationTheme(
    selectedTheme: AppThemePalette = AppThemePalette.TOKYO_NIGHT,
    darkTheme: Boolean = selectedTheme.isDark,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val activePalette = if (!darkTheme && selectedTheme.isDark) {
        AppThemePalette.CLEAN_LIGHT
    } else {
        selectedTheme
    }

    MaterialTheme(
        colorScheme = activePalette.toColorScheme(),
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

