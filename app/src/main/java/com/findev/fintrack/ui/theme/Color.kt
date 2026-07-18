package com.findev.fintrack.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Fallback palettes, used only when dynamic color is turned off (previews, tests).
// Material 3 derives the remaining roles from these.
internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00696E),
    secondary = Color(0xFF4A6363),
    tertiary = Color(0xFF4B607C),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CDADF),
    secondary = Color(0xFFB1CCCC),
    tertiary = Color(0xFFB3C8E8),
)
