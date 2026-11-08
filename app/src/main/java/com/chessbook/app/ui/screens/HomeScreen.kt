package com.chessbook.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chessbook.app.data.AppSettings
import com.chessbook.app.engine.EngineHolder
import com.chessbook.app.ui.ActionCard
import com.chessbook.app.ui.ContactButtons
import com.chessbook.app.ui.InfoCard
import com.chessbook.app.ui.Routes

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val engine = EngineHolder.get(ctx)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Chess-Book", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Play, analyse and train. The free engine never expires.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ActionCard("Play a bot", "Five levels, real engine strength settings, takeback and hints") { onNavigate(Routes.PLAY) }
        ActionCard("Analyse a game", "Import PGN, FEN, Lichess or Chess.com games and review every move") { onNavigate(Routes.ANALYSIS) }
        ActionCard("Tools", "Chess clock, Elo calculator, tournament generator, FEN viewer") { onNavigate(Routes.TOOLS) }
        ActionCard("Premium", "See what Stockfish Full adds over the free Lite engine") { onNavigate(Routes.PREMIUM) }
        InfoCard {
            Text("Engine", style = MaterialTheme.typography.titleSmall)
            Text(
                "${AppSettings.effectiveTier.label}\nRunning: ${engine.name}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text("Built by dragon-729", style = MaterialTheme.typography.labelLarge)
        ContactButtons()
    }
}
