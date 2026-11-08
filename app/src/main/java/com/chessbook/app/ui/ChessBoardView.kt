package com.chessbook.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.chessbook.app.chess.BISHOP
import com.chessbook.app.chess.KNIGHT
import com.chessbook.app.chess.Move
import com.chessbook.app.chess.Position
import com.chessbook.app.chess.QUEEN
import com.chessbook.app.chess.ROOK
import com.chessbook.app.data.BoardTheme
import kotlin.math.abs
import kotlin.math.sqrt

// U+FE0E forces text presentation so Android never swaps the pawn for a colour emoji.
private val GLYPH = arrayOf("", "♟︎", "♞︎", "♝︎", "♜︎", "♛︎", "♚︎")

/** An arrow drawn over the board from one square to another (square index 0..63, a8 = 0). */
data class BoardArrow(val from: Int, val to: Int, val color: Color)

/** A small round badge (for example "??") shown on the top-right corner of a square. */
data class BoardBadge(val square: Int, val color: Color, val text: String)

/**
 * Interactive board drawn on a single Canvas (no per-square composables), which keeps drawing
 * cheap enough for 120 fps. Selection state lives here; the position is the single source of truth.
 */
@Composable
fun ChessBoardView(
    position: Position,
    flipped: Boolean,
    theme: BoardTheme,
    modifier: Modifier = Modifier,
    lastMove: Move? = null,
    hint: Move? = null,
    arrows: List<BoardArrow> = emptyList(),
    badge: BoardBadge? = null,
    enabled: Boolean = true,
    onMove: (Move) -> Unit,
) {
    var selected by remember(position) { mutableIntStateOf(-1) }
    var promoChoices by remember(position) { mutableStateOf<List<Move>?>(null) }
    val legal = remember(position) { position.legalMoves }
    val checkSq = remember(position) { if (position.inCheck()) position.kingSquare(position.whiteToMove) else -1 }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val fillWhite = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT } }
    val strokeWhite = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E2A35.toInt(); textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; typeface = Typeface.DEFAULT } }
    val fillBlack = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF14191F.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT } }
    val strokeBlack = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE9EEF3.toInt(); textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE; typeface = Typeface.DEFAULT } }
    val label = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD } }
    val badgeText = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER }
    }

    Canvas(
        modifier
            .aspectRatio(1f)
            .pointerInput(position, flipped, enabled, selected) {
                detectTapGestures { off ->
                    if (!enabled) return@detectTapGestures
                    val cell = size.width / 8f
                    var col = (off.x / cell).toInt().coerceIn(0, 7)
                    var row = (off.y / cell).toInt().coerceIn(0, 7)
                    if (flipped) { col = 7 - col; row = 7 - row }
                    val sq = row * 8 + col
                    val targets = if (selected >= 0) legal.filter { it.from == selected && it.to == sq } else emptyList()
                    val own = position.board[sq] * (if (position.whiteToMove) 1 else -1) > 0
                    when {
                        targets.size > 1 -> promoChoices = targets
                        targets.size == 1 -> {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val mv = targets[0]
                            selected = -1
                            onMove(mv)
                        }
                        own -> selected = if (selected == sq) -1 else sq
                        else -> selected = -1
                    }
                }
            },
    ) {
        val cell = size.width / 8f
        fun cx(sq: Int): Float { val c = sq % 8; return (if (flipped) 7 - c else c) * cell }
        fun cy(sq: Int): Float { val r = sq / 8; return (if (flipped) 7 - r else r) * cell }

        for (sq in 0 until 64) {
            val light = (sq / 8 + sq % 8) % 2 == 0
            drawRect(Color(if (light) theme.light else theme.dark), Offset(cx(sq), cy(sq)), Size(cell, cell))
        }
        if (lastMove != null) {
            for (sq in intArrayOf(lastMove.from, lastMove.to))
                drawRect(Color(0x66F2D544), Offset(cx(sq), cy(sq)), Size(cell, cell))
        }
        if (hint != null) {
            for (sq in intArrayOf(hint.from, hint.to))
                drawRect(Color(0x6643B581), Offset(cx(sq), cy(sq)), Size(cell, cell))
        }
        if (checkSq >= 0) drawRect(Color(0x88E5484D), Offset(cx(checkSq), cy(checkSq)), Size(cell, cell))
        if (selected >= 0) drawRect(Color(0x8843B581), Offset(cx(selected), cy(selected)), Size(cell, cell))

        // Coordinates
        label.textSize = cell * 0.17f
        drawIntoNative { c ->
            for (i in 0 until 8) {
                val col = if (flipped) 7 - i else i
                label.color = (if ((7 + col) % 2 == 0) theme.dark else theme.light).toInt()
                c.drawText(('a' + i).toString(), col * cell + cell * 0.80f, 8 * cell - cell * 0.05f, label)
                val row = if (flipped) 7 - i else i
                label.color = (if (row % 2 == 0) theme.dark else theme.light).toInt()
                c.drawText(('8' - i).toString(), cell * 0.05f, row * cell + cell * 0.20f, label)
            }
        }

        // Move targets
        for (m in legal) {
            if (m.from != selected) continue
            val center = Offset(cx(m.to) + cell / 2, cy(m.to) + cell / 2)
            if (position.board[m.to] != 0 || m.to == position.epSquare && abs(position.board[m.from]) == 1) {
                drawCircle(Color(0x9943B581), cell * 0.46f, center, style = Stroke(width = cell * 0.08f))
            } else {
                drawCircle(Color(0x9943B581), cell * 0.16f, center)
            }
        }

        // Pieces
        val textSize = cell * 0.78f
        for (p in arrayOf(fillWhite, strokeWhite, fillBlack, strokeBlack)) p.textSize = textSize
        strokeWhite.strokeWidth = cell * 0.035f
        strokeBlack.strokeWidth = cell * 0.025f
        drawIntoNative { c ->
            val metrics = fillWhite.fontMetrics
            val baseOffset = -(metrics.ascent + metrics.descent) / 2f
            for (sq in 0 until 64) {
                val v = position.board[sq]
                if (v == 0) continue
                val g = GLYPH[abs(v)]
                val x = cx(sq) + cell / 2
                val y = cy(sq) + cell / 2 + baseOffset
                if (v > 0) { c.drawText(g, x, y, strokeWhite); c.drawText(g, x, y, fillWhite) }
                else { c.drawText(g, x, y, strokeBlack); c.drawText(g, x, y, fillBlack) }
            }
        }

        // Review arrows sit on top of the pieces, like on chess.com.
        for (a in arrows) {
            drawArrow(
                Offset(cx(a.from) + cell / 2, cy(a.from) + cell / 2),
                Offset(cx(a.to) + cell / 2, cy(a.to) + cell / 2),
                a.color,
                cell,
            )
        }

        // Move-quality badge (Best, Mistake, Blunder ...).
        if (badge != null) {
            val center = Offset(cx(badge.square) + cell * 0.80f, cy(badge.square) + cell * 0.20f)
            val radius = cell * 0.19f
            drawCircle(Color.White, radius + cell * 0.025f, center)
            drawCircle(badge.color, radius, center)
            badgeText.textSize = cell * (if (badge.text.length > 1) 0.17f else 0.22f)
            drawIntoNative { c -> c.drawText(badge.text, center.x, center.y + badgeText.textSize * 0.35f, badgeText) }
        }
    }

    promoChoices?.let { choices ->
        AlertDialog(
            onDismissRequest = { promoChoices = null },
            title = { Text("Promote to") },
            text = {
                Row {
                    for (pc in intArrayOf(QUEEN, ROOK, BISHOP, KNIGHT)) {
                        val mv = choices.firstOrNull { it.promo == pc } ?: continue
                        TextButton(onClick = {
                            promoChoices = null
                            selected = -1
                            onMove(mv)
                        }) { Text(GLYPH[pc].dropLast(1), fontSize = with(density) { 30.dp.toSp() }) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { promoChoices = null }) { Text("Cancel") } },
        )
    }
}

/** Straight arrow with a triangular head; the shaft starts slightly off the centre of the source square. */
private fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color, cell: Float) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < cell * 0.5f) return
    val ux = dx / len
    val uy = dy / len
    val px = -uy
    val py = ux
    val headLen = cell * 0.38f
    val headHalf = cell * 0.24f
    val start = Offset(from.x + ux * cell * 0.20f, from.y + uy * cell * 0.20f)
    val tip = Offset(to.x - ux * cell * 0.10f, to.y - uy * cell * 0.10f)
    val neck = Offset(tip.x - ux * headLen, tip.y - uy * headLen)
    drawLine(color, start, neck, strokeWidth = cell * 0.15f)
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(neck.x + px * headHalf, neck.y + py * headHalf)
        lineTo(neck.x - px * headHalf, neck.y - py * headHalf)
        close()
    }
    drawPath(head, color)
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoNative(block: (android.graphics.Canvas) -> Unit) {
    block(drawContext.canvas.nativeCanvas)
}
