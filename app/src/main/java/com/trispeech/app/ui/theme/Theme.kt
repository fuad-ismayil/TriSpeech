package com.trispeech.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = TrueBlack, onPrimary = PureWhite, secondary = Ink, onSecondary = PureWhite,
    background = PureWhite, onBackground = TrueBlack, surface = PureWhite, onSurface = TrueBlack,
    surfaceVariant = SurfaceMuted, onSurfaceVariant = Ink, outline = Border, error = Color(0xFF6F0000)
)
private val DarkScheme = darkColorScheme(
    primary = PureWhite, onPrimary = TrueBlack, secondary = Color(0xFFD6D6D6), onSecondary = TrueBlack,
    background = TrueBlack, onBackground = PureWhite, surface = DarkSurface, onSurface = PureWhite,
    surfaceVariant = Color(0xFF2A2A2A), onSurfaceVariant = Color(0xFFD6D6D6), outline = Color(0xFF444444)
)

@Composable
fun TriSpeechTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, typography = TriSpeechTypography, content = content)
}
