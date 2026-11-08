package com.chessbook.app.engine

import com.chessbook.app.chess.Move
import com.chessbook.app.chess.Position
import com.chessbook.app.chess.QUEEN
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Small alpha-beta engine written in Kotlin. It is NOT Stockfish: it is a fallback that keeps the
 * app fully usable when no Stockfish binary is bundled (see docs/ENGINE_SETUP.md).
 */
class BuiltinEngine : ChessEngine {
    override val name = "Built-in engine"
    override val supportsElo = false

    override suspend fun analyze(pos: Position, limits: EngineLimits): EngineResult =
        withContext(Dispatchers.Default) {
            val deadline = System.nanoTime() + limits.moveTimeMs * 1_000_000L
            val search = Search(deadline) { isActive }
            search.run(pos, limits.maxDepth.coerceIn(1, 8))
        }

    private class Search(val deadline: Long, val active: () -> Boolean) {
        var nodes = 0L
        var stop = false
        val pvTable = Array(72) { arrayOfNulls<Move>(72) }
        val pvLen = IntArray(72)

        companion object {
            const val MATE = 30000
            val VAL = intArrayOf(0, 100, 320, 330, 500, 900, 0)
        }

        fun timeUp(): Boolean {
            if (stop) return true
            if ((nodes and 1023L) == 0L && (System.nanoTime() > deadline || !active())) stop = true
            return stop
        }

        fun eval(p: Position): Int {
            var s = 0
            for (sq in 0 until 64) {
                val v = p.board[sq]
                if (v == 0) continue
                val t = abs(v)
                val r = sq / 8
                val f = sq % 8
                var x = VAL[t]
                val center = (3 - minOf(abs(f - 3), abs(f - 4))) + (3 - minOf(abs(r - 3), abs(r - 4)))
                if (t == 2 || t == 3) x += center * 5
                if (t == 1) {
                    val adv = if (v > 0) 6 - r else r - 1
                    x += adv * 6 + if (f == 3 || f == 4) 5 else 0
                }
                s += if (v > 0) x else -x
            }
            return if (p.whiteToMove) s else -s
        }

        fun moveScore(p: Position, m: Move): Int {
            var s = 0
            val victim = abs(p.board[m.to])
            if (victim != 0) s += 10 * VAL[victim] - VAL[abs(p.board[m.from])] / 10 + 1000
            if (m.promo == QUEEN) s += 800
            return s
        }

        fun qs(p: Position, alphaIn: Int, beta: Int, depth: Int): Int {
            nodes++
            if (timeUp()) return 0
            var alpha = alphaIn
            val stand = eval(p)
            if (depth <= 0 || stand >= beta) return stand
            if (stand > alpha) alpha = stand
            val caps = p.pseudoMoves()
                .filter { p.board[it.to] != 0 || it.promo != 0 }
                .sortedByDescending { moveScore(p, it) }
            for (m in caps) {
                if (!p.kingSafeAfter(m)) continue
                val score = -qs(p.make(m), -beta, -alpha, depth - 1)
                if (stop) return 0
                if (score >= beta) return score
                if (score > alpha) alpha = score
            }
            return alpha
        }

        fun negamax(p: Position, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
            nodes++
            pvLen[ply] = 0
            if (timeUp()) return 0
            val moves = p.legalMoves
            if (moves.isEmpty()) return if (p.inCheck()) -MATE + ply else 0
            if (p.halfmove >= 100) return 0
            if (depth <= 0) return qs(p, alphaIn, beta, 4)
            var alpha = alphaIn
            var best = -MATE * 2
            for (m in moves.sortedByDescending { moveScore(p, it) }) {
                val score = -negamax(p.make(m), depth - 1, -beta, -alpha, ply + 1)
                if (stop) return 0
                if (score > best) best = score
                if (score > alpha) {
                    alpha = score
                    pvTable[ply][0] = m
                    for (i in 0 until pvLen[ply + 1]) pvTable[ply][i + 1] = pvTable[ply + 1][i]
                    pvLen[ply] = pvLen[ply + 1] + 1
                }
                if (alpha >= beta) break
            }
            return best
        }

        fun run(pos: Position, maxDepth: Int): EngineResult {
            val moves = pos.legalMoves
            if (moves.isEmpty()) {
                val mated = pos.inCheck()
                val white = if (mated) (if (pos.whiteToMove) -MATE else MATE) else 0
                return EngineResult(null, white.coerceIn(-10000, 10000), if (mated) 0 else null, 0, 0, emptyList(), "Built-in engine")
            }
            var bestMove: Move = moves.first()
            var bestScore = 0
            var bestPv: List<String> = listOf(bestMove.uci())
            var doneDepth = 0
            for (d in 1..maxDepth) {
                var alpha = -MATE * 2
                var depthBest: Move? = null
                var depthScore = -MATE * 2
                val ordered = moves.sortedByDescending { if (it == bestMove) 100000 else moveScore(pos, it) }
                for (m in ordered) {
                    pvLen[1] = 0
                    val score = -negamax(pos.make(m), d - 1, -MATE * 2, -alpha, 1)
                    if (stop) break
                    if (score > depthScore) {
                        depthScore = score
                        depthBest = m
                        alpha = maxOf(alpha, score)
                        val tail = (0 until pvLen[1]).mapNotNull { pvTable[1][it]?.uci() }
                        bestPv = listOf(m.uci()) + tail
                    }
                }
                if (stop && d > 1) break
                if (depthBest != null) {
                    bestMove = depthBest
                    bestScore = depthScore
                    doneDepth = d
                }
                if (stop) break
                if (abs(bestScore) > MATE - 100) break
            }
            val white = if (pos.whiteToMove) bestScore else -bestScore
            val mateIn = if (abs(bestScore) > MATE - 100) {
                val plies = MATE - abs(bestScore)
                val full = (plies + 1) / 2
                if (white > 0) full else -full
            } else null
            return EngineResult(bestMove, white.coerceIn(-10000, 10000), mateIn, doneDepth, nodes, bestPv, "Built-in engine")
        }
    }
}
