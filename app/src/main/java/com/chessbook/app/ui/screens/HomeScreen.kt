package com.chessbook.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chessbook.app.data.AppSettings
import com.chessbook.app.engine.EngineHolder
import com.chessbook.app.ui.Accent
import com.chessbook.app.ui.ActionCard
import com.chessbook.app.ui.ContactButtons
import com.chessbook.app.ui.HeroBanner
import com.chessbook.app.ui.InfoCard
import com.chessbook.app.ui.Routes

@Composable
fun HomeScreen(onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    val engine = EngineHolder.get(ctx)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HeroBanner(
            title = "Chess-Book",
            subtitle = "Play, analyse and train. The free engine never expires.",
            glyph = "♞",
        ) {
            Button(
                onClick = { onNavigate(Routes.PLAY) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5B95C), contentColor = Color(0xFF2B1D00)),
            ) { Text("Play now") }
        }
        ActionCard("Play a bot", "Five levels, takeback and hints. Premium: any Elo and Human touch", "♞", Accent.Green) { onNavigate(Routes.PLAY) }
        ActionCard("Analyse a game", "Import PGN, FEN, Lichess or Chess.com games. Review with arrows", "♝", Accent.Blue) { onNavigate(Routes.ANALYSIS) }
        ActionCard("Tools", "Chess clock, Elo calculator, tournament generator, FEN viewer", "♜", Accent.Orange) { onNavigate(Routes.TOOLS) }
        ActionCard("Premium", "Stockfish Full, custom Elo, Human touch and move review", "♛", Accent.Gold) { onNavigate(Routes.PREMIUM) }
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
