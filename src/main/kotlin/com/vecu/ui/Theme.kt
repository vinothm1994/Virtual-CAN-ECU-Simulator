package com.vecu.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0xFF004C68),
    onPrimaryContainer = Color(0xFFC8E9FF),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE2E6EA),
    surface = Color(0xFF161B21),
    onSurface = Color(0xFFE2E6EA),
    surfaceVariant = Color(0xFF1E252D),
    onSurfaceVariant = Color(0xFFB6C0CA),
    error = Color(0xFFFF6B6B),
    outline = Color(0xFF3A434D),
)

/** Colours for CAN monitor direction tags and status. */
object VecuColors {
    val rx = Color(0xFF4FC3F7)
    val tx = Color(0xFF81C784)
    val warn = Color(0xFFFFB74D)
    val error = Color(0xFFFF6B6B)
    val ok = Color(0xFF81C784)
    val idle = Color(0xFF6B7681)
}

@Composable
fun VecuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
