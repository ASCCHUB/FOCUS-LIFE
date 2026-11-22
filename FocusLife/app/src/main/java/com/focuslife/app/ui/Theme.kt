package com.focuslife.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6EE7B7),
    onPrimary = Color(0xFF04281F),
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFCCFBEF),
    secondary = Color(0xFF99F6E4),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF1E3A3A),
    onSecondaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EDF7),
    surface = Color(0xFF121B2D),
    onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1A2539),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainer = Color(0xFF121B2D),
    surfaceContainerHigh = Color(0xFF1A2539),
    surfaceContainerHighest = Color(0xFF223049),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF243044),
    error = Color(0xFFFCA5A5),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF047857),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    background = Color(0xFFF3F7F5),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE7F0EC),
    onSurfaceVariant = Color(0xFF5B6B7A),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEFF5F2),
    surfaceContainerHighest = Color(0xFFE7F0EC),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

val Amber = Color(0xFFF59E0B)

@Composable fun FocusLifeTheme(light: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (light) LightColors else DarkColors, shapes = AppShapes, content = content)
}
