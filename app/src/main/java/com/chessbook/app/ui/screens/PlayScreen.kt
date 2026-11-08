package com.chessbook.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import com.chessbook.app.ui.Handoff
import com.chessbook.app.ui.InfoCard
import com.chessbook.app.ui.PlayVM

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayScreen(onAnalyse: () -> Unit, vm: PlayVM = viewModel()) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Play", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        val status = when {
            vm.over != null -> vm.over!!
            vm.thinking -> "${vm.level.label} bot is thinking…"
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
                    Button(onClick = { Handoff.pendingPgn = vm.pgn(); onAnalyse() }) { Text("Review this game") }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(vm.pgn())) }) { Text("Copy PGN") }
                }
            }
        }

        InfoCard {
            Text("New game", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (l in BotLevel.values()) {
                    FilterChip(selected = vm.level == l, onClick = { vm.level = l }, label = { Text(l.label) })
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.newGame(true) }, modifier = Modifier.weight(1f)) { Text("Play White") }
                Button(onClick = { vm.newGame(false) }, modifier = Modifier.weight(1f)) { Text("Play Black") }
            }
            vm.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
