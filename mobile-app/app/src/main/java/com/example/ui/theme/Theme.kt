package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    secondary = GreenAccent,
    background = Black,
    surface = DarkCardBg,
    surfaceVariant = DarkBubbleBg,
    onBackground = White,
    onSurface = White,
    onSurfaceVariant = DarkMuted
)

private val LightColorScheme = lightColorScheme(
    primary = Black,
    onPrimary = White,
    secondary = Accent,
    background = LightBackground,
    surface = LightCardBg,
    surfaceVariant = LightBubbleBg,
    onBackground = Black,
    onSurface = Black,
    onSurfaceVariant = LightMuted
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // Fallback to system theme if not explicitly set
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
