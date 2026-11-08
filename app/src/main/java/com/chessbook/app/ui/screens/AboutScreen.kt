package com.chessbook.app.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chessbook.app.BuildConfig
import com.chessbook.app.data.AppSettings
import com.chessbook.app.data.BoardTheme
import com.chessbook.app.ui.ContactButtons
import com.chessbook.app.ui.InfoCard
import com.chessbook.app.ui.Links

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    @Suppress("DEPRECATION")
    val display = (ctx as? Activity)?.windowManager?.defaultDisplay
    val current = display?.refreshRate
    val best = display?.supportedModes?.maxOfOrNull { it.refreshRate }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("About", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        InfoCard {
            Text("Chess-Book ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Text("Made by dragon-729", style = MaterialTheme.typography.bodyMedium)
            Text("GitHub: ${Links.GITHUB}", style = MaterialTheme.typography.bodySmall)
            Text("Telegram: ${Links.TELEGRAM_HANDLE}", style = MaterialTheme.typography.bodySmall)
            ContactButtons()
        }
        InfoCard {
            Text("Board theme", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (t in BoardTheme.values()) {
                    FilterChip(
                        selected = AppSettings.boardTheme == t,
                        onClick = { AppSettings.selectTheme(t) },
                        label = { Text(t.label) },
                    )
                }
            }
        }
        InfoCard {
            Text("Smoothness", style = MaterialTheme.typography.titleSmall)
            Text(
                "Chess-Book asks Android for your screen's highest refresh rate. " +
                    "Current: ${current?.let { "%.0f Hz".format(it) } ?: "unknown"}; " +
                    "highest supported: ${best?.let { "%.0f Hz".format(it) } ?: "unknown"}.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "120 fps needs a 120 Hz screen, and Android can lower it in battery-saver mode or when the phone " +
                    "is hot. This is the same for free and premium users.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
