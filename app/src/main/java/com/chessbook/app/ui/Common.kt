package com.chessbook.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

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

@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun ActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
