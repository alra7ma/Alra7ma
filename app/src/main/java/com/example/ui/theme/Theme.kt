package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = CrimsonBright,
    onPrimary = Color.White,
    primaryContainer = CrimsonDarkContainer,
    onPrimaryContainer = CrimsonSoft,
    secondary = LiveEmerald,
    onSecondary = Color.Black,
    secondaryContainer = LiveEmeraldDarkBg,
    onSecondaryContainer = LiveEmerald,
    tertiary = WarningAmber,
    onTertiary = Color.Black,
    tertiaryContainer = WarningAmberDarkBg,
    onTertiaryContainer = WarningAmber,
    background = ObsidianBg,
    onBackground = TextLightPrimary,
    surface = CharcoalSurface,
    onSurface = TextLightPrimary,
    surfaceVariant = CharcoalElevated,
    onSurfaceVariant = TextLightSecondary,
    outline = CharcoalBorder,
    outlineVariant = Color(0xFF202634)
)

private val LightColorScheme = lightColorScheme(
    primary = CrimsonRed,
    onPrimary = Color.White,
    primaryContainer = CrimsonSoft,
    onPrimaryContainer = CrimsonDarkContainer,
    secondary = Color(0xFF047857),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),
    background = AlabasterBg,
    onBackground = TextDarkPrimary,
    surface = CrispWhite,
    onSurface = TextDarkPrimary,
    surfaceVariant = MistSurface,
    onSurfaceVariant = TextDarkSecondary,
    outline = MistBorder,
    outlineVariant = Color(0xFFE5EAF0)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RedCounterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
