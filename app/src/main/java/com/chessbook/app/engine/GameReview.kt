package com.chessbook.app.engine

import com.chessbook.app.chess.GameStatus
import com.chessbook.app.chess.Move
import com.chessbook.app.chess.Position
import kotlin.math.abs
import kotlin.math.exp

/**
 * Brilliant / Great need sacrifice and uniqueness detection that is not implemented yet,
 * so they are intentionally absent rather than assigned at random.
 */
enum class MoveClass(val label: String) {
    BEST("Best"),
    EXCELLENT("Excellent"),
    GOOD("Good"),
    INACCURACY("Inaccuracy"),
    MISTAKE("Mistake"),
    BLUNDER("Blunder"),
}

/** Thresholds are expressed in win-probability points lost by the mover (0..100). */
data class Thresholds(
    val excellent: Double = 2.0,
    val good: Double = 5.0,
    val inaccuracy: Double = 10.0,
    val mistake: Double = 20.0,
)

data class GameReview(
    /** White-perspective centipawns for every position (moves + 1 entries). */
    val evals: List<Int>,
    /** Classification of each played move. */
    val classes: List<MoveClass>,
    /** Engine's preferred move in the position before each played move. */
    val bestMoves: List<Move?>,
    /** Win-percentage points lost by each played move. */
    val losses: List<Double>,
    val accuracyWhite: Double,
    val accuracyBlack: Double,
    val whiteCounts: Map<MoveClass, Int>,
    val blackCounts: Map<MoveClass, Int>,
    val worstMoveIndex: Int?,
    val engineName: String,
    /** Engine's second-best move in the position before each played move (premium reviews). */
    val altMoves: List<Move?> = emptyList(),
)

object ReviewMath {
    fun winPct(cp: Int): Double {
        val c = cp.coerceIn(-1000, 1000)
        return 50.0 + 50.0 * (2.0 / (1.0 + exp(-0.00368208 * c)) - 1.0)
    }

    /** Simplified accuracy curve (shape follows the publicly documented Lichess formula). */
    fun accuracyOf(loss: Double): Double =
        (103.1668 * exp(-0.04354 * loss) - 3.1669).coerceIn(0.0, 100.0)

    fun classify(loss: Double, playedIsBest: Boolean, t: Thresholds = Thresholds()): MoveClass = when {
        playedIsBest || loss <= 0.5 -> MoveClass.BEST
        loss <= t.excellent -> MoveClass.EXCELLENT
        loss <= t.good -> MoveClass.GOOD
        loss <= t.inaccuracy -> MoveClass.INACCURACY
        loss <= t.mistake -> MoveClass.MISTAKE
        else -> MoveClass.BLUNDER
    }

    fun cpOf(r: EngineResult): Int {
        val m = r.mateIn ?: return r.scoreCp
        val sign = if (m > 0) 1 else -1
        return sign * (10000 - minOf(abs(m), 100) * 10)
    }
}

object Reviewer {
    /**
     * Evaluates every position of the line with [engine] and classifies each move from real
     * evaluation changes. Cancellable; reports progress as (done, total).
     */
    suspend fun review(
        engine: ChessEngine,
        positions: List<Position>,
        moves: List<Move>,
        limits: EngineLimits,
        thresholds: Thresholds = Thresholds(),
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): GameReview {
        require(positions.size == moves.size + 1)
        val evals = ArrayList<Int>(positions.size)
        val bests = ArrayList<Move?>(positions.size)
        val alts = ArrayList<Move?>(positions.size)
        var name = engine.name
        for ((i, pos) in positions.withIndex()) {
            val status = pos.status()
            if (status == GameStatus.CHECKMATE) {
                evals.add(if (pos.whiteToMove) -10000 else 10000)
                bests.add(null)
                alts.add(null)
            } else if (status != GameStatus.ONGOING) {
                evals.add(0)
                bests.add(null)
                alts.add(null)
            } else {
                val r = engine.analyze(pos, limits)
                name = r.engineName
                evals.add(ReviewMath.cpOf(r))
                bests.add(r.best)
                alts.add(r.candidates.map { it.move }.firstOrNull { it != r.best })
            }
            onProgress(i + 1, positions.size)
        }

        val classes = ArrayList<MoveClass>(moves.size)
        val losses = ArrayList<Double>(moves.size)
        val accW = ArrayList<Double>()
        val accB = ArrayList<Double>()
        val wc = MoveClass.values().associateWith { 0 }.toMutableMap()
        val bc = MoveClass.values().associateWith { 0 }.toMutableMap()
        var worst: Int? = null
        var worstLoss = 0.0
        for (i in moves.indices) {
            val whiteMoved = positions[i].whiteToMove
            val before = ReviewMath.winPct(evals[i]).let { if (whiteMoved) it else 100 - it }
            val after = ReviewMath.winPct(evals[i + 1]).let { if (whiteMoved) it else 100 - it }
            val loss = (before - after).coerceAtLeast(0.0)
            val cls = ReviewMath.classify(loss, moves[i] == bests[i], thresholds)
            classes.add(cls)
            losses.add(loss)
            if (whiteMoved) { accW.add(ReviewMath.accuracyOf(loss)); wc[cls] = wc.getValue(cls) + 1 }
            else { accB.add(ReviewMath.accuracyOf(loss)); bc[cls] = bc.getValue(cls) + 1 }
            if (loss > worstLoss) { worstLoss = loss; worst = i }
        }
        return GameReview(
            evals = evals,
            classes = classes,
            bestMoves = bests.take(moves.size),
            losses = losses,
            accuracyWhite = if (accW.isEmpty()) 0.0 else accW.average(),
            accuracyBlack = if (accB.isEmpty()) 0.0 else accB.average(),
            whiteCounts = wc,
            blackCounts = bc,
            worstMoveIndex = worst,
            engineName = name,
            altMoves = alts.take(moves.size),
        )
    }
}
