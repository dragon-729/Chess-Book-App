package com.chessbook.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chessbook.app.data.AppSettings
import com.chessbook.app.engine.EngineTier
import com.chessbook.app.engine.MoveClass
import com.chessbook.app.engine.ReviewMath
import com.chessbook.app.engine.formatScore
import com.chessbook.app.ui.AnalysisVM
import com.chessbook.app.ui.ChessBoardView
import com.chessbook.app.ui.Handoff
import com.chessbook.app.ui.InfoCard

private fun classColor(c: MoveClass): Color = when (c) {
    MoveClass.BEST -> Color(0xFF2E9E5B)
    MoveClass.EXCELLENT -> Color(0xFF6BB86B)
    MoveClass.GOOD -> Color(0xFF8A97A3)
    MoveClass.INACCURACY -> Color(0xFFD9A400)
    MoveClass.MISTAKE -> Color(0xFFE67E22)
    MoveClass.BLUNDER -> Color(0xFFD64545)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisScreen(onGoPremium: () -> Unit, vm: AnalysisVM = viewModel()) {
    val clipboard = LocalClipboardManager.current
    var showImport by remember { mutableStateOf(false) }

    LaunchedEffect(Handoff.pendingPgn) {
        Handoff.pendingPgn?.let {
            vm.loadPgn(it)
            Handoff.pendingPgn = null
        }
    }

    val pos = vm.current
    val result = vm.result
    val review = vm.review

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Analysis", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        vm.notice?.let { msg ->
            InfoCard {
                Text(msg, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { vm.notice = null }) { Text("OK") }
            }
        }

        EvalBar(
            cp = result?.let { ReviewMath.cpOf(it) },
            text = result?.let { formatScore(it) } ?: "—",
        )

        ChessBoardView(
            position = pos,
            flipped = vm.flipped,
            theme = AppSettings.boardTheme,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
            lastMove = vm.lastMove,
            hint = if (vm.analyzing) null else result?.best,
            onMove = vm::onMove,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val last = vm.positions.size - 1
            FilledTonalButton(onClick = { vm.goTo(0) }, enabled = vm.ply > 0, modifier = Modifier.weight(1f)) { Text("|<") }
            FilledTonalButton(onClick = { vm.goTo(vm.ply - 1) }, enabled = vm.ply > 0, modifier = Modifier.weight(1f)) { Text("<") }
            FilledTonalButton(onClick = { vm.goTo(vm.ply + 1) }, enabled = vm.ply < last, modifier = Modifier.weight(1f)) { Text(">") }
            FilledTonalButton(onClick = { vm.goTo(last) }, enabled = vm.ply < last, modifier = Modifier.weight(1f)) { Text(">|") }
            FilledTonalButton(onClick = { vm.flipped = !vm.flipped }, modifier = Modifier.weight(1f)) { Text("Flip") }
        }

        InfoCard {
            Text("Engine", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = AppSettings.effectiveTier == EngineTier.LITE,
                    onClick = { AppSettings.selectTier(EngineTier.LITE) },
                    label = { Text("Lite — FREE") },
                )
                FilterChip(
                    selected = AppSettings.effectiveTier == EngineTier.FULL,
                    onClick = { if (!AppSettings.selectTier(EngineTier.FULL)) onGoPremium() },
                    label = { Text("Full — PREMIUM") },
                )
            }
            Text(
                "Running: ${vm.engineName}" + if (!vm.usingNative) " (Stockfish binary not bundled in this build)" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (vm.analyzing || vm.reviewing) {
                    Button(onClick = vm::stop) { Text("Stop") }
                } else {
                    Button(onClick = vm::analyze) { Text("Analyse position") }
                    OutlinedButton(onClick = vm::reviewGame, enabled = vm.lines[vm.lineIdx].isNotEmpty()) { Text("Review game") }
                }
            }
            if (vm.analyzing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (vm.reviewing) {
                val (done, total) = vm.reviewProgress
                LinearProgressIndicator(progress = { done.toFloat() / total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                Text("Reviewing position $done of $total", style = MaterialTheme.typography.bodySmall)
            }
            if (result != null) {
                val best = result.best?.let { runCatching { pos.san(it) }.getOrNull() }
                Text(
                    "Eval ${formatScore(result)} · depth ${result.depth} · nodes ${result.nodes}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (best != null) Text("Best move: $best", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (vm.pvText.isNotEmpty()) Text("Line: ${vm.pvText}", style = MaterialTheme.typography.bodyMedium)
                Text("By ${result.engineName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (review != null) {
            InfoCard {
                Text("Game report", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Accuracy — White ${"%.1f".format(review.accuracyWhite)}%  ·  Black ${"%.1f".format(review.accuracyBlack)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                for ((name, counts) in listOf("White" to review.whiteCounts, "Black" to review.blackCounts)) {
                    Text(
                        name + ": " + MoveClass.values().joinToString(" · ") { "${it.label} ${counts[it] ?: 0}" },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                review.worstMoveIndex?.let { i ->
                    val before = vm.positions.getOrNull(i)
                    if (before != null) {
                        val mover = if (before.whiteToMove) "White" else "Black"
                        val num = before.fullmove
                        val bestSan = review.bestMoves.getOrNull(i)?.let { runCatching { before.san(it) }.getOrNull() }
                        Text(
                            "Biggest swing: $mover move $num, ${vm.sans[i]} (${review.classes[i].label})" +
                                (if (bestSan != null) " — engine preferred $bestSan" else ""),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                EvalGraph(review.evals, vm.ply)
                Text(
                    "Based on ${review.engineName} evaluations. Accuracy is a simplified estimate. Brilliant/Great labels are not implemented yet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (vm.lines.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in vm.lines.indices) {
                    FilterChip(
                        selected = vm.lineIdx == i,
                        onClick = { vm.selectLine(i) },
                        label = { Text(if (i == 0) "Main line" else "Variation $i") },
                    )
                }
            }
        }

        InfoCard {
            Text("Moves", style = MaterialTheme.typography.titleSmall)
            if (vm.sans.isEmpty()) {
                Text("Play a move on the board, or import a game.", style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    vm.sans.forEachIndexed { i, san ->
                        val before = vm.positions[i]
                        val prefix = if (before.whiteToMove) "${before.fullmove}. " else if (i == 0) "${before.fullmove}… " else ""
                        val cls = review?.classes?.getOrNull(i)
                        Text(
                            prefix + san,
                            color = cls?.let { classColor(it) } ?: MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (vm.ply == i + 1) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (vm.ply == i + 1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .clickable { vm.goTo(i + 1) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showImport = true }) { Text("Import") }
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(pos.toFen()))
                vm.notice = "FEN copied."
            }) { Text("Copy FEN") }
            OutlinedButton(onClick = {
                clipboard.setText(AnnotatedString(vm.exportPgn()))
                vm.notice = "PGN copied."
            }) { Text("Copy PGN") }
        }
    }

    if (showImport || vm.importResults.isNotEmpty()) {
        ImportDialog(vm = vm, onDismiss = { showImport = false; vm.clearImportResults() })
    }
}

@Composable
private fun EvalBar(cp: Int?, text: String) {
    val frac = cp?.let { (ReviewMath.winPct(it) / 100.0).toFloat() } ?: 0.5f
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color(0xFF14191F)),
        ) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 0.98f)).fillMaxHeight().background(Color.White))
        }
        Text(text, modifier = Modifier.width(56.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EvalGraph(evals: List<Int>, ply: Int) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x22888888))) {
        val w = size.width
        val h = size.height
        drawLine(Color(0x66888888), Offset(0f, h / 2), Offset(w, h / 2), 1f)
        if (evals.size > 1) {
            val path = Path()
            evals.forEachIndexed { i, cp ->
                val x = w * i / (evals.size - 1)
                val y = h * (1f - (ReviewMath.winPct(cp) / 100.0).toFloat())
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, lineColor, style = Stroke(width = 3f))
            val mx = w * ply.coerceIn(0, evals.size - 1) / (evals.size - 1)
            drawLine(Color(0xFFE5484D), Offset(mx, 0f), Offset(mx, h), 2f)
        }
    }
}

@Composable
private fun ImportDialog(vm: AnalysisVM, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var source by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.loadFile(ctx.contentResolver, uri)
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import a game") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = source == 0, onClick = { source = 0 }, label = { Text("PGN / FEN", fontSize = 12.sp) })
                    FilterChip(selected = source == 1, onClick = { source = 1 }, label = { Text("Lichess", fontSize = 12.sp) })
                    FilterChip(selected = source == 2, onClick = { source = 2 }, label = { Text("Chess.com", fontSize = 12.sp) })
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(if (source == 0) "Paste PGN or FEN" else "Username") },
                    minLines = if (source == 0) 4 else 1,
                    maxLines = if (source == 0) 8 else 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (source == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { clipboard.getText()?.text?.let { text = it } }) { Text("Paste clipboard") }
                        TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Open PGN file") }
                    }
                }
                if (vm.importBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (vm.importResults.isNotEmpty()) {
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        for (g in vm.importResults) {
                            TextButton(onClick = { vm.pickRemote(g); onDismiss() }) { Text(g.label, maxLines = 1) }
                        }
                    }
                }
                vm.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !vm.importBusy,
                onClick = {
                    when (source) {
                        0 -> if (vm.loadAny(text)) onDismiss()
                        1 -> vm.fetchRemote(true, text)
                        else -> vm.fetchRemote(false, text)
                    }
                },
            ) { Text(if (source == 0) "Load" else "Fetch games") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
