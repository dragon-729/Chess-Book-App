package com.chessbook.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Links {
    const val GITHUB = "https://github.com/dragon-729"
    const val TELEGRAM = "https://t.me/YogeshBhai729"
    const val TELEGRAM_HANDLE = "@YogeshBhai729"
}

object Routes {
    const val HOME = "home"
    const val PLAY = "play"
    const val ANALYSIS = "analysis"
    const val TOOLS = "tools"
    const val PREMIUM = "premium"
    const val ABOUT = "about"
}

/** Accent colours used across the app so every section has its own identity. */
object Accent {
    val Green = Color(0xFF2E9E5B)
    val Blue = Color(0xFF2F6FDE)
    val Orange = Color(0xFFE67E22)
    val Gold = Color(0xFFC8962E)
    val Purple = Color(0xFF7B4FD0)
    val Rose = Color(0xFFD64F79)
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** Tappable card with a coloured icon tile, used for the main menu. */
@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    glyph: String = "♞",
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GlyphTile(glyph, accent)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Rounded gradient tile with a chess-piece glyph in it. */
@Composable
fun GlyphTile(glyph: String, accent: Color, size: Int = 52) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.65f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = (size * 0.54f).sp, color = Color.White)
    }
}

/** Big gradient banner at the top of a screen. */
@Composable
fun HeroBanner(
    title: String,
    subtitle: String,
    glyph: String,
    colors: List<Color> = listOf(Color(0xFF1B2B5B), Color(0xFF45309A), Color(0xFFB5651D)),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(colors)),
    ) {
        Text(
            glyph,
            fontSize = 150.sp,
            color = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
        )
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.88f))
            content()
        }
    }
}

/** One line of a feature list: coloured tile, bold title, short description. */
@Composable
fun FeatureRow(glyph: String, title: String, text: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlyphTile(glyph, accent, size = 42)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Small coloured pill, for example "PREMIUM". */
@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** GitHub and Telegram links requested for the app. */
@Composable
fun ContactButtons(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { uri.openUri(Links.GITHUB) }, modifier = Modifier.weight(1f)) { Text("GitHub") }
        OutlinedButton(onClick = { uri.openUri(Links.TELEGRAM) }, modifier = Modifier.weight(1f)) {
            Text("Telegram ${Links.TELEGRAM_HANDLE}", maxLines = 1)
        }
    }
}
