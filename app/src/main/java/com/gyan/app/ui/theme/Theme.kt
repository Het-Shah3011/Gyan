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

// ── Light Mode: Clean Academic (Notion / Linear feel) ───────────────────────
// Canvas: warm off-white stone  •  Accent: Royal Cobalt Blue + Sage Green + Warm Orange
private val LightColors = lightColorScheme(
    primary            = Color(0xFF2563EB),   // Royal Cobalt Blue
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFDBEAFE),   // light blue tint
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary          = Color(0xFF10B981),   // Botanical Sage Green
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF065F46),
    tertiary           = Color(0xFFF59E0B),   // Warm Honey Amber
    onTertiary         = Color.White,
    tertiaryContainer  = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background         = Color(0xFFF5F5F0),   // Warm stone / oatmeal
    onBackground       = Color(0xFF111827),
    surface            = Color(0xFFFFFFFF),
    onSurface          = Color(0xFF111827),
    surfaceVariant     = Color(0xFFEBEBE6),   // warm grey
    onSurfaceVariant   = Color(0xFF4B5563),
    error              = Color(0xFFDC2626),
    onError            = Color.White,
    outline            = Color(0xFFD1D5DB),
    outlineVariant     = Color(0xFFE5E7EB)
)

// ── Dark Mode: Charcoal Slate & Electric Indigo ───────────────────────────────
// Canvas: warm charcoal (NOT pitch black)  •  Accent: Electric Indigo + Nordic Teal + Tangerine
// Premium blue-purple feel — no amber/yellow overload.
private val DarkColors = darkColorScheme(
    primary            = Color(0xFF818CF8),   // Electric Indigo (bright for dark bg)
    onPrimary          = Color(0xFF1E1B4B),
    primaryContainer   = Color(0xFF312E81),   // deep indigo container
    onPrimaryContainer = Color(0xFFC7D2FE),
    secondary          = Color(0xFF2DD4BF),   // Nordic Teal
    onSecondary        = Color(0xFF00312C),
    secondaryContainer = Color(0xFF0D4840),
    onSecondaryContainer = Color(0xFF99F6E4),
    tertiary           = Color(0xFFFB923C),   // Tangerine / warm orange
    onTertiary         = Color(0xFF1F0A00),
    tertiaryContainer  = Color(0xFF3A1800),
    onTertiaryContainer = Color(0xFFFFD9B8),
    background         = Color(0xFF181C20),   // Warm charcoal — not OLED black
    onBackground       = Color(0xFFF1F0EE),
    surface            = Color(0xFF21262D),   // Elevated zinc slate
    onSurface          = Color(0xFFF1F0EE),
    surfaceVariant     = Color(0xFF2D333B),   // Slightly lighter slate
    onSurfaceVariant   = Color(0xFFB0B8C4),
    error              = Color(0xFFF87171),
    onError            = Color(0xFF4B0000),
    outline            = Color(0xFF444C56),
    outlineVariant     = Color(0xFF30363D)
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
            small      = RoundedCornerShape(12.dp),
            medium     = RoundedCornerShape(18.dp),
            large      = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(30.dp)
        ),
        content = content
    )
}
