package com.findev.fintrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.findev.fintrack.data.ThemeMode

/** Resolves the stored preference against the system setting. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun FinTrackTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Material You is off by default.
     *
     * It takes its colours from the wallpaper, so the app cannot promise any particular
     * look - the warm palette in Color.kt would simply be ignored, and on a cool wallpaper
     * every surface comes out grey-blue. Turning it on is a one-line change here if the
     * wallpaper-matching is ever worth more than a consistent identity.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.isDark()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
