package com.ansh.omnipage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OmniColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF1A1300),
    primaryContainer = Teal,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF5DB7D6),
    background = Night,
    onBackground = TextHigh,
    surface = Panel,
    onSurface = TextHigh,
    surfaceVariant = CardBg,
    onSurfaceVariant = TextMuted,
    outline = Hairline,
    error = Color(0xFFFF6B6B),
)

@Composable
fun OmniPageTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OmniColors, typography = OmniTypography, content = content)
}
