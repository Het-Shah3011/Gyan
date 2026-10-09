package com.gyan.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0D9488),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF134E4A),
    tertiary = Color(0xFFDB2777),
    background = Color(0xFFF6F7FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6E8F0),
    onSurfaceVariant = Color(0xFF4B5563),
    error = Color(0xFFDC2626)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF625DEB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF353184),
    onPrimaryContainer = Color(0xFFE7E5FF),
    secondary = Color(0xFF38D9BA),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF124A43),
    onSecondaryContainer = Color(0xFF9CF4DE),
    tertiary = Color(0xFFFFAB4C),
    background = Color(0xFF0A0E1C),
    surface = Color(0xFF151D38),
    surfaceVariant = Color(0xFF242E53),
    onSurfaceVariant = Color(0xFF9AA7D0),
    error = Color(0xFFF87171)
)

@Composable
fun GyanTheme(themeMode: String = "dark", content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(30.dp)
        ),
        content = content
    )
}
