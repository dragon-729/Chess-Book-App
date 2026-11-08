package com.chessbook.app.engine

import com.chessbook.app.chess.Move
import com.chessbook.app.chess.Position

/** Resource limits for one analysis request. */
data class EngineLimits(
    val maxDepth: Int,
    val moveTimeMs: Long,
    val multiPv: Int = 1,
    val threads: Int = 1,
    val hashMb: Int = 16,
    /** If set, the engine plays at roughly this Elo (UCI_LimitStrength). */
    val elo: Int? = null,
)

/**
 * Result of an analysis. [scoreCp] is always from White's point of view (centipawns).
 * [mateIn] is positive if White delivers mate in that many moves, negative if Black does.
 */
data class EngineResult(
    val best: Move?,
    val scoreCp: Int,
    val mateIn: Int?,
    val depth: Int,
    val nodes: Long,
    val pv: List<String>,
    val engineName: String,
)

interface ChessEngine {
    val name: String

    /** True if the engine honours [EngineLimits.elo]. */
    val supportsElo: Boolean

    /** Runs off the main thread and is cancellable via coroutine cancellation. */
    suspend fun analyze(pos: Position, limits: EngineLimits): EngineResult
}

enum class EngineTier(val label: String) {
    LITE("Stockfish Lite — FREE"),
    FULL("Stockfish Full — PREMIUM"),
}

object TierLimits {
    /**
     * Lite is battery-conscious: one thread, small hash, short search.
     * Full genuinely gets more: more threads, bigger hash, deeper and longer search, 3 lines.
     * Both are capped by what the device can actually provide.
     */
    fun forTier(tier: EngineTier, usingStockfish: Boolean, cores: Int, maxHeapMb: Int): EngineLimits =
        when (tier) {
            EngineTier.LITE -> EngineLimits(
                maxDepth = if (usingStockfish) 14 else 3,
                moveTimeMs = 1500,
                multiPv = 1,
                threads = 1,
                hashMb = 16,
            )
            EngineTier.FULL -> EngineLimits(
                maxDepth = if (usingStockfish) 40 else 5,
                moveTimeMs = 8000,
                multiPv = 3,
                threads = (cores - 1).coerceIn(1, 4),
                hashMb = (maxHeapMb / 4).coerceIn(32, 256),
            )
        }

    /** Shorter limits used when reviewing every move of a game. */
    fun forReview(tier: EngineTier, usingStockfish: Boolean, cores: Int, maxHeapMb: Int): EngineLimits {
        val base = forTier(tier, usingStockfish, cores, maxHeapMb)
        return base.copy(
            maxDepth = if (usingStockfish) (if (tier == EngineTier.LITE) 12 else 18) else base.maxDepth,
            moveTimeMs = if (tier == EngineTier.LITE) 350 else 900,
            multiPv = 1,
        )
    }
}

fun formatScore(r: EngineResult): String {
    r.mateIn?.let { return if (it > 0) "M$it" else "-M${-it}" }
    val v = r.scoreCp / 100.0
    return (if (v > 0) "+" else "") + String.format("%.2f", v)
}
