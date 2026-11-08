package com.chessbook.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Classic navy + gold, with green and warm accents. Ivory in light mode, deep midnight blue in dark mode.
private val Light = lightColorScheme(
    primary = Color(0xFF1F3B73),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5F8),
    onPrimaryContainer = Color(0xFF0E2149),
    secondary = Color(0xFF2E7D5B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EEDF),
    onSecondaryContainer = Color(0xFF0B3A25),
    tertiary = Color(0xFFB7791F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBE8BE),
    onTertiaryContainer = Color(0xFF4A3000),
    background = Color(0xFFFAF6EC),
    onBackground = Color(0xFF1B2233),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B2233),
    surfaceVariant = Color(0xFFEFE8D6),
    onSurfaceVariant = Color(0xFF5A6070),
    outline = Color(0xFF8A8F9E),
    outlineVariant = Color(0xFFE4DBC4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFE5B95C),
    onPrimary = Color(0xFF2B1D00),
    primaryContainer = Color(0xFF33497E),
    onPrimaryContainer = Color(0xFFDCE5F8),
    secondary = Color(0xFF6FD3A0),
    onSecondary = Color(0xFF00391F),
    secondaryContainer = Color(0xFF1F4D38),
    onSecondaryContainer = Color(0xFFCDEFDD),
    tertiary = Color(0xFF9DB8F5),
    onTertiary = Color(0xFF0B1F4A),
    tertiaryContainer = Color(0xFF3A3220),
    onTertiaryContainer = Color(0xFFF5DFA4),
    background = Color(0xFF0E1526),
    onBackground = Color(0xFFEDEFF6),
    surface = Color(0xFF172036),
    onSurface = Color(0xFFEDEFF6),
    surfaceVariant = Color(0xFF222D4A),
    onSurfaceVariant = Color(0xFFAAB4CC),
    outline = Color(0xFF6F7A95),
    outlineVariant = Color(0xFF2C3858),
)

private val Base = Typography()

// Serif headings give the app a classic, book-like feel; body text stays clean and readable.
private val AppTypography = Typography(
    headlineMedium = Base.headlineMedium.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    titleLarge = Base.titleLarge.copy(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.Bold),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun ChessBookTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
