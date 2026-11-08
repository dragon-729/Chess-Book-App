package com.chessbook.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chessbook.app.chess.Move
import com.chessbook.app.chess.Pgn
import com.chessbook.app.chess.Position
import com.chessbook.app.chess.gameResultText
import com.chessbook.app.data.AppSettings
import com.chessbook.app.engine.EngineHolder
import com.chessbook.app.engine.EngineLimits
import com.chessbook.app.engine.EngineTier
import com.chessbook.app.engine.HumanTouch
import com.chessbook.app.engine.TierLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Difficulty is real engine configuration: Elo limit (Stockfish) or depth/time/error rate (built-in). */
enum class BotLevel(val label: String, val elo: Int?, val depth: Int, val timeMs: Long, val blunder: Double) {
    BEGINNER("Beginner", 1320, 1, 300, 0.30),
    EASY("Easy", 1500, 2, 400, 0.12),
    INTERMEDIATE("Intermediate", 1800, 3, 700, 0.04),
    ADVANCED("Advanced", 2200, 4, 1000, 0.0),
    EXPERT("Expert", null, 5, 1500, 0.0),
}

class PlayVM(app: Application) : AndroidViewModel(app) {
    private val engine = EngineHolder.get(app)
    private var job: Job? = null

    var level by mutableStateOf(BotLevel.INTERMEDIATE)
    var playerWhite by mutableStateOf(true); private set
    var positions by mutableStateOf(listOf(Position.start())); private set
    var moves by mutableStateOf(listOf<Move>()); private set
    var sans by mutableStateOf(listOf<String>()); private set
    var thinking by mutableStateOf(false); private set
    var over by mutableStateOf<String?>(null); private set
    var hint by mutableStateOf<Move?>(null); private set
    var notice by mutableStateOf<String?>(null)

    /** Name shown for the bot: the custom Elo (Premium) or the chosen level. */
    val botName: String get() = AppSettings.effectiveCustomElo?.let { "Elo $it" } ?: level.label

    val current: Position get() = positions.last()
    val humanTurn: Boolean get() = current.whiteToMove == playerWhite && over == null

    private fun applyMove(m: Move) {
        val cur = current
        sans = sans + cur.san(m)
        positions = positions + cur.make(m)
        moves = moves + m
        hint = null
        over = gameResultText(current)
    }

    fun humanMove(m: Move) {
        if (!humanTurn || thinking) return
        applyMove(m)
        maybeBot()
    }

    /** Elo the bot should play at: the Premium custom value if set, otherwise the level's. */
    private fun botElo(): Int? = AppSettings.effectiveCustomElo ?: level.elo

    /** Built-in fallback engine has no Elo setting, so a custom Elo is mapped to search depth. */
    private fun builtinDepth(custom: Int?): Int = if (custom == null) level.depth else ((custom - 800) / 450).coerceIn(1, 5)

    private fun builtinBlunder(custom: Int?): Double =
        if (custom == null) level.blunder else ((2000 - custom) / 2000.0).coerceIn(0.0, 0.30)

    private fun botLimits(): EngineLimits {
        val custom = AppSettings.effectiveCustomElo
        return EngineLimits(
            maxDepth = if (engine.usingNative) 20 else builtinDepth(custom),
            moveTimeMs = if (custom != null) (500L + (custom - 1320) / 2).coerceIn(500L, 2000L) else level.timeMs,
            // Human touch needs several candidate lines to choose from.
            multiPv = if (AppSettings.effectiveHumanTouch && engine.usingNative) 4 else 1,
            elo = if (engine.supportsElo) botElo() else null,
        )
    }

    private fun maybeBot() {
        if (over != null || current.whiteToMove == playerWhite) return
        val pos = current
        val limits = botLimits()
        val custom = AppSettings.effectiveCustomElo
        val blunder = builtinBlunder(custom)
        val human = AppSettings.effectiveHumanTouch
        val elo = botElo() ?: 3200
        job = viewModelScope.launch {
            thinking = true
            try {
                val started = System.nanoTime()
                val r = engine.analyze(pos, limits)
                var mv = r.best
                if (!engine.supportsElo && blunder > 0 && Random.nextDouble() < blunder) {
                    mv = pos.legalMoves.randomOrNull() ?: mv
                } else if (human && mv != null) {
                    mv = HumanTouch.pick(pos, r, elo) ?: mv
                }
                if (human) {
                    // Take a natural, varying amount of time instead of answering instantly.
                    val spent = (System.nanoTime() - started) / 1_000_000L
                    val target = HumanTouch.thinkTimeMs(pos)
                    if (target > spent) delay(target - spent)
                }
                if (mv != null && current === pos) applyMove(mv)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = "Engine error: ${e.message}"
            } finally {
                thinking = false
            }
        }
    }

    fun newGame(white: Boolean) {
        job?.cancel()
        thinking = false
        playerWhite = white
        positions = listOf(Position.start())
        moves = emptyList()
        sans = emptyList()
        over = null
        hint = null
        maybeBot()
    }

    fun takeback() {
        job?.cancel()
        thinking = false
        hint = null
        var n = moves.size
        if (n == 0) return
        n -= 1
        while (n > 0 && positions[n].whiteToMove != playerWhite) n--
        positions = positions.take(n + 1)
        moves = moves.take(n)
        sans = sans.take(n)
        over = gameResultText(current)
        maybeBot()
    }

    fun resign() {
        if (over != null) return
        job?.cancel()
        thinking = false
        over = "You resigned"
    }

    fun requestHint() {
        if (!humanTurn || thinking) return
        val pos = current
        val limits = TierLimits.forTier(EngineTier.LITE, engine.usingNative, 1, 64)
        job = viewModelScope.launch {
            thinking = true
            try {
                hint = engine.analyze(pos, limits).best
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = "Engine error: ${e.message}"
            } finally {
                thinking = false
            }
        }
    }

    fun pgn(): String {
        val h = mapOf(
            "White" to (if (playerWhite) "You" else "Chess-Book $botName"),
            "Black" to (if (playerWhite) "Chess-Book $botName" else "You"),
        )
        val result = when {
            over?.startsWith("Checkmate — White") == true -> "1-0"
            over?.startsWith("Checkmate — Black") == true -> "0-1"
            over == "You resigned" -> if (playerWhite) "0-1" else "1-0"
            over != null -> "1/2-1/2"
            else -> "*"
        }
        return Pgn.export(h, Position.START_FEN, sans, result)
    }

    override fun onCleared() {
        job?.cancel()
    }
}
