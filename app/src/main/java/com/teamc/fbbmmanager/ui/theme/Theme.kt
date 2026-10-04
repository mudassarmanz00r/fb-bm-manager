package com.teamc.fbbmmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// VIP palette: deep-space navy surfaces, electric indigo accent, warm gold for
// success. Tuned for OLED-friendly dark mode first, light mode second.
// ---------------------------------------------------------------------------

private val Indigo = Color(0xFF6C7BFF)
private val IndigoDeep = Color(0xFF4A56D6)
private val IndigoSoft = Color(0xFF2A3358)

private val Gold = Color(0xFFE8B44A)
private val Mint = Color(0xFF3DDC97)

private val Ink = Color(0xFF0B0E1A)
private val InkRaised = Color(0xFF141A2E)
private val InkLine = Color(0xFF232B45)
private val InkText = Color(0xFFF1F3FA)
private val InkMuted = Color(0xFF9AA3C0)

private val Paper = Color(0xFFF6F7FB)
private val PaperRaised = Color(0xFFFFFFFF)
private val PaperLine = Color(0xFFE3E7F2)
private val PaperText = Color(0xFF101426)
private val PaperMuted = Color(0xFF5A6280)

private val DarkColors = darkColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = IndigoSoft,
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF8E9BFF),
    onSecondary = Color(0xFF0B0E1A),
    tertiary = Gold,
    onTertiary = Color(0xFF0B0E1A),
    background = Ink,
    onBackground = InkText,
    surface = InkRaised,
    onSurface = InkText,
    surfaceVariant = InkLine,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color(0xFF070A14),
    surfaceContainerLow = Ink,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = Color(0xFF1A2138),
    outline = InkLine,
    outlineVariant = Color(0xFF1B2340),
    error = Color(0xFFFF6B7A),
    onError = Color(0xFF1A0508)
)

private val LightColors = lightColorScheme(
    primary = IndigoDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E6FF),
    onPrimaryContainer = IndigoDeep,
    secondary = Color(0xFF5A67E8),
    tertiary = Color(0xFFB07E1F),
    background = Paper,
    onBackground = PaperText,
    surface = PaperRaised,
    onSurface = PaperText,
    surfaceVariant = PaperLine,
    onSurfaceVariant = PaperMuted,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Paper,
    surfaceContainer = Color(0xFFEFF1F8),
    surfaceContainerHigh = Color(0xFFE8EBF5),
    outline = PaperLine,
    error = Color(0xFFD32F2F)
)

/** App theme. Dark-first: defaults to the saved preference (dark on first run). */
@Composable
fun FbBmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

/** Success/positive accent that works on both themes. */
val androidx.compose.material3.ColorScheme.success: Color
    @Composable get() = Mint
