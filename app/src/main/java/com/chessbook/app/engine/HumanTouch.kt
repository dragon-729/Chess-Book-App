package com.chessbook.app.engine

import com.chessbook.app.chess.Move
import com.chessbook.app.chess.Position
import kotlin.math.exp
import kotlin.random.Random

/**
 * "Human touch" (Premium): makes the bot feel like a person instead of a machine.
 *  - It sometimes plays a different, almost-as-good move instead of always the single best one.
 *  - It never throws away a forced mate and never picks a move that is clearly worse.
 *  - It thinks for a natural, varying time: quick in the opening and in forced replies, longer in
 *    complicated positions.
 * Pure Kotlin, so it runs in plain JVM unit tests.
 */
object HumanTouch {
    /** How many centipawns worse than the best line a "human" choice may be. Weaker bots wobble more. */
    fun toleranceCp(elo: Int, fullmove: Int): Int {
        val base = ((3300 - elo) / 30).coerceIn(10, 70)
        return base + if (fullmove <= 8) 15 else 0
    }

    fun pick(pos: Position, r: EngineResult, elo: Int, rnd: Random = Random.Default): Move? {
        val best = r.best ?: return null
        val sign = if (pos.whiteToMove) 1 else -1
        val scored = r.candidates.map { it.move to it.scoreCp * sign } // mover's point of view
        if (scored.size < 2) return best
        val top = scored.maxOf { it.second }
        if (top >= 9000) return best // never play a forced mate down
        val tol = toleranceCp(elo, pos.fullmove)
        val pool = scored.filter { top - it.second <= tol && it.second > -9000 }
        if (pool.size < 2) return best
        val scale = tol / 2.0
        val weights = pool.map { (m, s) ->
            val w = exp(-(top - s) / scale)
            if (m == best) w * 2.0 else w
        }
        var x = rnd.nextDouble() * weights.sum()
        for ((i, w) in weights.withIndex()) {
            x -= w
            if (x <= 0.0) return pool[i].first
        }
        return pool.last().first
    }

    /** Total time the bot should take over this move, in milliseconds. */
    fun thinkTimeMs(pos: Position, rnd: Random = Random.Default): Long {
        val n = pos.legalMoves.size
        if (n <= 1) return 250L
        var t = 450.0 + n * 18.0 + rnd.nextDouble() * 900.0
        if (pos.inCheck()) t *= 0.6
        if (pos.fullmove <= 6) t *= 0.55
        return t.toLong().coerceIn(300L, 2800L)
    }
}
