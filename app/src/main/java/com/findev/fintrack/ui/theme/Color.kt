package com.findev.fintrack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/*
 * Cool, near-neutral palette with a light-blue accent, matching the app icon.
 *
 * The neutrals are genuinely neutral. An earlier version tinted every surface towards the
 * accent hue to make the app feel "warm", and the dark theme stopped reading as dark at
 * all - it read as brown. Tinted neutrals are a trap at low lightness: what looks like a
 * subtle cast on a swatch becomes the dominant impression once it covers the whole screen.
 * Warmth, or coolness, belongs in the accents.
 *
 * Every role is spelled out rather than derived from a couple of seeds - passing two or
 * three colours to lightColorScheme() leaves everything else on the violet Material
 * baseline, so half the components come out the wrong colour.
 */

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00658A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC3E8FF),
    onPrimaryContainer = Color(0xFF001E2C),

    secondary = Color(0xFF4D616C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E6F2),
    onSecondaryContainer = Color(0xFF081E27),

    tertiary = Color(0xFF2C6B45),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB0F1C2),
    onTertiaryContainer = Color(0xFF002110),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFF7F9FB),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFDCE3E9),
    onSurfaceVariant = Color(0xFF40484D),

    outline = Color(0xFF70787D),
    outlineVariant = Color(0xFFC0C8CD),

    // The tonal steps panels and fields are built from. Far enough apart to tell apart on
    // a phone in daylight - this is the whole depth system, there are no shadows.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4F6),
    surfaceContainer = Color(0xFFEBEEF1),
    surfaceContainerHigh = Color(0xFFE4E9EC),
    surfaceContainerHighest = Color(0xFFDDE3E7),
)

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7FD3F7),
    onPrimary = Color(0xFF003546),
    primaryContainer = Color(0xFF004E66),
    onPrimaryContainer = Color(0xFFBFE9FF),

    secondary = Color(0xFFB4CAD6),
    onSecondary = Color(0xFF1F333D),
    secondaryContainer = Color(0xFF354A54),
    onSecondaryContainer = Color(0xFFD0E6F2),

    tertiary = Color(0xFF9CD5A8),
    onTertiary = Color(0xFF00391A),
    tertiaryContainer = Color(0xFF1F5133),
    onTertiaryContainer = Color(0xFFB8F2C4),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    // Very dark and neutral, but not #000: pure black smears on OLED while scrolling and
    // leaves the tonal steps above it nowhere to go.
    background = Color(0xFF0F1113),
    onBackground = Color(0xFFE2E2E5),
    surface = Color(0xFF0F1113),
    onSurface = Color(0xFFE2E2E5),
    surfaceVariant = Color(0xFF40484D),
    onSurfaceVariant = Color(0xFFC0C8CD),

    outline = Color(0xFF8A9297),
    outlineVariant = Color(0xFF40484D),

    surfaceContainerLowest = Color(0xFF0A0C0E),
    surfaceContainerLow = Color(0xFF17191C),
    surfaceContainer = Color(0xFF1B1E21),
    surfaceContainerHigh = Color(0xFF262A2D),
    surfaceContainerHighest = Color(0xFF313538),
)

/*
 * Money colours.
 *
 * Kept out of the scheme roles deliberately. "Income is green, expense is red" is a
 * meaning, not a brand accent - it must not drift when the accent colour changes, and it
 * must stay the same in both themes so a glance at a number never has to be re-learned.
 * Only the lightness differs, so both stay legible on their own background.
 */
object MoneyColors {
    private val IncomeLight = Color(0xFF1E7A45)
    private val IncomeDark = Color(0xFF7BD9A0)
    private val ExpenseLight = Color(0xFFB3261E)
    private val ExpenseDark = Color(0xFFFFB4AB)

    val income: Color @Composable get() = if (onDarkSurface()) IncomeDark else IncomeLight
    val expense: Color @Composable get() = if (onDarkSurface()) ExpenseDark else ExpenseLight
}

/**
 * Whether the theme currently in force is a dark one.
 *
 * Read off the surface colour rather than from isSystemInDarkTheme(): the app has its own
 * light/dark setting that can disagree with the system, and asking the system would give
 * the wrong answer whenever it does.
 */
@Composable
private fun onDarkSurface(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f
