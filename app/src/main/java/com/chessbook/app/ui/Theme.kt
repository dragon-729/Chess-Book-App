package com.chessbook.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2F5D8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E6F7),
    secondary = Color(0xFF4F6A83),
    background = Color(0xFFF6F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6ECF2),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FB3D9),
    onPrimary = Color(0xFF0B2236),
    primaryContainer = Color(0xFF244764),
    secondary = Color(0xFFA9C1D8),
    background = Color(0xFF111A22),
    surface = Color(0xFF17232E),
    surfaceVariant = Color(0xFF22313E),
)

@Composable
fun ChessBookTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
