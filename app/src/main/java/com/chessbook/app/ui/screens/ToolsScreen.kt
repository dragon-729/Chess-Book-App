package com.chessbook.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessbook.app.chess.EloMath
import com.chessbook.app.chess.Position
import com.chessbook.app.chess.Tournament
import com.chessbook.app.data.AppSettings
import com.chessbook.app.ui.ActionCard
import com.chessbook.app.ui.ChessBoardView
import com.chessbook.app.ui.InfoCard
import kotlinx.coroutines.delay

@Composable
fun ToolsScreen() {
    var tool by rememberSaveable { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Tools", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (tool == null) {
            ActionCard("Chess clock", "Two-player clock with increment") { tool = "clock" }
            ActionCard("Elo rating calculator", "Standard Elo formula with FIDE-style K-factor presets") { tool = "elo" }
            ActionCard("Round-robin generator", "Pairings for every player, with byes for odd numbers") { tool = "rr" }
            ActionCard("FEN viewer", "Paste a FEN to see the position") { tool = "fen" }
        } else {
            TextButton(onClick = { tool = null }) { Text("← All tools") }
            when (tool) {
                "clock" -> ClockTool()
                "elo" -> EloTool()
                "rr" -> RoundRobinTool()
                "fen" -> FenTool()
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val total = ms.coerceAtLeast(0)
    val m = total / 60_000
    val s = (total / 1000) % 60
    return if (total < 20_000) "%d:%02d.%d".format(m, s, (total / 100) % 10) else "%d:%02d".format(m, s)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClockTool() {
    val controls = listOf(1 to 0, 3 to 2, 5 to 0, 10 to 0, 15 to 10)
    var control by remember { mutableIntStateOf(2) }
    val startMs = controls[control].first * 60_000L
    val incMs = controls[control].second * 1000L
    var whiteMs by remember { mutableLongStateOf(startMs) }
    var blackMs by remember { mutableLongStateOf(startMs) }
    var active by remember { mutableIntStateOf(0) } // 0 idle, 1 white running, 2 black running
    var flagged by remember { mutableIntStateOf(0) }

    LaunchedEffect(active) {
        if (active == 0) return@LaunchedEffect
        var last = System.nanoTime()
        while (true) {
            delay(50)
            val now = System.nanoTime()
            val dt = (now - last) / 1_000_000
            last = now
            if (active == 1) {
                whiteMs -= dt
                if (whiteMs <= 0) { whiteMs = 0; flagged = 1; active = 0; break }
            } else {
                blackMs -= dt
                if (blackMs <= 0) { blackMs = 0; flagged = 2; active = 0; break }
            }
        }
    }

    fun reset(c: Int) {
        control = c
        whiteMs = controls[c].first * 60_000L
        blackMs = whiteMs
        active = 0
        flagged = 0
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        controls.forEachIndexed { i, (m, inc) ->
            FilterChip(selected = control == i, onClick = { reset(i) }, label = { Text("$m|$inc") })
        }
    }

    ClockPanel("Black", blackMs, active == 2, flagged == 2) {
        if (active == 2) { blackMs += incMs; active = 1 }
    }
    ClockPanel("White", whiteMs, active == 1, flagged == 1) {
        if (active == 1) { whiteMs += incMs; active = 2 }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (active == 0 && flagged == 0) Button(onClick = { active = 1 }) { Text("Start (White)") }
        if (active != 0) OutlinedButton(onClick = { active = 0 }) { Text("Pause") }
        OutlinedButton(onClick = { reset(control) }) { Text("Reset") }
    }
    Text(
        "Tap your own panel after you move. Increment is added when you tap.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ClockPanel(name: String, ms: Long, running: Boolean, flagged: Boolean, onTap: () -> Unit) {
    val bg = when {
        flagged -> Color(0xFFD64545)
        running -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (running || flagged) Color.White else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(enabled = running, onClick = onTap)
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, color = fg, style = MaterialTheme.typography.labelLarge)
            Text(fmt(ms), color = fg, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            if (flagged) Text("Time's up", color = fg)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EloTool() {
    var own by rememberSaveable { mutableStateOf("1500") }
    var opp by rememberSaveable { mutableStateOf("1600") }
    var k by rememberSaveable { mutableStateOf("20") }
    var score by rememberSaveable { mutableIntStateOf(0) } // 0 win, 1 draw, 2 loss
    val a = own.toIntOrNull()
    val b = opp.toIntOrNull()
    val kk = k.toIntOrNull()
    val s = when (score) { 0 -> 1.0; 1 -> 0.5; else -> 0.0 }

    InfoCard {
        Text("Elo rating calculator", style = MaterialTheme.typography.titleMedium)
        Text(
            "Standard Elo: expected = 1 / (1 + 10^((opponent − own) / 400)); new = own + K × (score − expected).",
            style = MaterialTheme.typography.bodySmall,
        )
        val num = KeyboardOptions(keyboardType = KeyboardType.Number)
        OutlinedTextField(own, { own = it.filter(Char::isDigit).take(4) }, label = { Text("Your rating") }, keyboardOptions = num, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(opp, { opp = it.filter(Char::isDigit).take(4) }, label = { Text("Opponent rating") }, keyboardOptions = num, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(k, { k = it.filter(Char::isDigit).take(2) }, label = { Text("K-factor") }, keyboardOptions = num, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (preset in listOf(40, 20, 10)) {
                FilterChip(selected = k == preset.toString(), onClick = { k = preset.toString() }, label = { Text("K=$preset") })
            }
        }
        Text("FIDE K presets: 40 for new players, 20 below 2400, 10 at 2400+.", style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Win", "Draw", "Loss").forEachIndexed { i, label ->
                FilterChip(selected = score == i, onClick = { score = i }, label = { Text(label) })
            }
        }
        if (a != null && b != null && kk != null && kk > 0) {
            val exp = EloMath.expected(a, b)
            val change = EloMath.change(a, b, s, kk)
            Text("Expected score: ${"%.1f".format(exp * 100)}%", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Rating change: ${if (change >= 0) "+" else ""}${"%.1f".format(change)}  →  new rating ${EloMath.newRating(a, b, s, kk)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Text("Enter both ratings and a K-factor.", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            "USCF uses a different formula and is not included yet.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RoundRobinTool() {
    var names by rememberSaveable { mutableStateOf("Asha\nBilal\nChen\nDiego") }
    var generated by remember { mutableStateOf<List<List<Pair<String, String>>>?>(null) }
    InfoCard {
        Text("Round-robin generator", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            names,
            { names = it },
            label = { Text("One player per line (2–24)") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        val list = names.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        Button(onClick = { generated = if (list.size in 2..24) Tournament.roundRobin(list) else emptyList() }) { Text("Generate pairings") }
        generated?.let { rounds ->
            if (rounds.isEmpty()) {
                Text("Enter between 2 and 24 different names.", color = MaterialTheme.colorScheme.error)
            } else {
                rounds.forEachIndexed { i, pairs ->
                    Text("Round ${i + 1}", fontWeight = FontWeight.SemiBold)
                    pairs.forEach { (w, bl) -> Text("  $w (White) vs $bl (Black)", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun FenTool() {
    var fen by rememberSaveable { mutableStateOf(Position.START_FEN) }
    val pos = remember(fen) { Position.fromFen(fen) }
    InfoCard {
        Text("FEN viewer", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(fen, { fen = it }, label = { Text("FEN") }, modifier = Modifier.fillMaxWidth())
        if (pos == null) {
            Text("That is not a valid FEN position.", color = MaterialTheme.colorScheme.error)
        } else {
            Text(
                (if (pos.whiteToMove) "White" else "Black") + " to move" + if (pos.inCheck()) " (in check)" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            ChessBoardView(
                position = pos,
                flipped = false,
                theme = AppSettings.boardTheme,
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
                enabled = false,
                onMove = {},
            )
        }
    }
}
