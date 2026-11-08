package com.chessbook.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chessbook.app.data.AppSettings
import com.chessbook.app.ui.BotLevel
import com.chessbook.app.ui.ChessBoardView
import com.chessbook.app.ui.Accent
import com.chessbook.app.ui.Handoff
import com.chessbook.app.ui.InfoCard
import com.chessbook.app.ui.Pill
import com.chessbook.app.ui.PlayVM
import kotlin.math.roundToInt

private val ELO_PRESETS = listOf(1320, 1500, 1800, 2000, 2400, 2800, 3190)

private fun eloTitle(elo: Int): String = when {
    elo < 1500 -> "Beginner"
    elo < 1800 -> "Club player"
    elo < 2100 -> "Strong club player"
    elo < 2400 -> "Expert / candidate master"
    elo < 2700 -> "Master"
    else -> "Grandmaster level"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayScreen(onAnalyse: () -> Unit, onGoPremium: () -> Unit, vm: PlayVM = viewModel()) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Play", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        val status = when {
            vm.over != null -> vm.over!!
            vm.thinking -> "${vm.botName} bot is thinking…"
            vm.humanTurn -> "Your move"
            else -> ""
        }
        Text(status, style = MaterialTheme.typography.titleMedium)

        ChessBoardView(
            position = vm.current,
            flipped = !vm.playerWhite,
            theme = AppSettings.boardTheme,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            lastMove = vm.moves.lastOrNull(),
            hint = vm.hint,
            enabled = vm.humanTurn && !vm.thinking,
            onMove = vm::humanMove,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::takeback, enabled = vm.moves.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Takeback") }
            OutlinedButton(onClick = vm::requestHint, enabled = vm.humanTurn && !vm.thinking, modifier = Modifier.weight(1f)) { Text("Hint") }
            OutlinedButton(onClick = vm::resign, enabled = vm.over == null && vm.moves.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Resign") }
        }

        if (vm.sans.isNotEmpty()) {
            Text(
                vm.sans.chunked(2).withIndex().joinToString("  ") { (i, pair) -> "${i + 1}. ${pair.joinToString(" ")}" },
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (vm.over != null && vm.moves.isNotEmpty()) {
            InfoCard {
                Text("Game finished", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Handoff.pendingPgn = vm.pgn(); Handoff.autoReview = true; onAnalyse() }) { Text("Review this game") }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(vm.pgn())) }) { Text("Copy PGN") }
                }
            }
        }

        PremiumPlayOptions(onGoPremium)

        InfoCard {
            Text("New game", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in BotLevel.values()) {
                    FilterChip(
                        selected = vm.level == l && AppSettings.effectiveCustomElo == null,
                        onClick = { vm.level = l; AppSettings.setUseCustomElo(false) },
                        label = { Text(l.label) },
                    )
                }
            }
            AppSettings.effectiveCustomElo?.let {
                Text("Playing at your custom Elo: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.newGame(true) }, modifier = Modifier.weight(1f)) { Text("Play White") }
                Button(onClick = { vm.newGame(false) }, modifier = Modifier.weight(1f)) { Text("Play Black") }
            }
            vm.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** Premium bot options: pick any Elo in a range, and switch on the "Human touch". Locked for free users. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PremiumPlayOptions(onGoPremium: () -> Unit) {
    val premium = AppSettings.premiumActive
    InfoCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Premium play options", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Pill(if (premium) "PREMIUM" else "LOCKED", if (premium) Accent.Gold else Accent.Purple)
        }

        // 1) Custom Elo range
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Custom Elo range", fontWeight = FontWeight.SemiBold)
                Text(
                    "Choose exactly how strong the bot is, from ${AppSettings.MIN_ELO} to ${AppSettings.MAX_ELO}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(4.dp))
            Switch(
                checked = AppSettings.effectiveCustomElo != null,
                onCheckedChange = { if (premium) AppSettings.setUseCustomElo(it) else onGoPremium() },
            )
        }
        if (premium && AppSettings.useCustomElo) {
            Text(
                "${AppSettings.customElo} Elo · ${eloTitle(AppSettings.customElo)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Slider(
                value = AppSettings.customElo.toFloat(),
                onValueChange = { AppSettings.setCustomElo((it / 10f).roundToInt() * 10, persist = false) },
                onValueChangeFinished = { AppSettings.setCustomElo(AppSettings.customElo) },
                valueRange = AppSettings.MIN_ELO.toFloat()..AppSettings.MAX_ELO.toFloat(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (e in ELO_PRESETS) {
                    FilterChip(
                        selected = AppSettings.customElo == e,
                        onClick = { AppSettings.setCustomElo(e) },
                        label = { Text("$e") },
                    )
                }
            }
        }

        // 2) Human touch
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Human touch", fontWeight = FontWeight.SemiBold)
                Text(
                    "The bot varies its moves and thinks for a natural, changing time, like a real opponent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(4.dp))
            Switch(
                checked = AppSettings.effectiveHumanTouch,
                onCheckedChange = { if (premium) AppSettings.setHumanTouch(it) else onGoPremium() },
            )
        }

        // 3) Review (lives in the Analysis tab)
        Text(
            "Move review with suggestion arrows: finish a game and tap “Review this game”.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!premium) {
            Button(onClick = onGoPremium, modifier = Modifier.fillMaxWidth()) { Text("Unlock with Premium") }
        }
    }
}
