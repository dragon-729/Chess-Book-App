package com.chessbook.app.chess

import kotlin.math.abs

const val PAWN = 1
const val KNIGHT = 2
const val BISHOP = 3
const val ROOK = 4
const val QUEEN = 5
const val KING = 6

/** Square index: 0 = a8, 7 = h8, 56 = a1, 63 = h1. */
fun sqName(s: Int): String = "${'a' + s % 8}${'8' - s / 8}"

fun sqFromName(n: String): Int? {
    if (n.length != 2) return null
    val f = n[0] - 'a'
    val r = '8' - n[1]
    return if (f in 0..7 && r in 0..7) r * 8 + f else null
}

data class Move(val from: Int, val to: Int, val promo: Int = 0) {
    fun uci(): String =
        sqName(from) + sqName(to) + if (promo != 0) "pnbrqk"[promo - 1].toString() else ""
}

enum class GameStatus { ONGOING, CHECKMATE, STALEMATE, DRAW_FIFTY_MOVE, DRAW_INSUFFICIENT_MATERIAL }

private val KNIGHT_D = arrayOf(
    intArrayOf(-2, -1), intArrayOf(-2, 1), intArrayOf(-1, -2), intArrayOf(-1, 2),
    intArrayOf(1, -2), intArrayOf(1, 2), intArrayOf(2, -1), intArrayOf(2, 1),
)
private val DIRS = arrayOf(
    intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1),
    intArrayOf(-1, -1), intArrayOf(-1, 1), intArrayOf(1, -1), intArrayOf(1, 1),
)

/**
 * Immutable chess position. Board squares hold 0 (empty), +type (white) or -type (black).
 * castling bits: 1 = white king side, 2 = white queen side, 4 = black king side, 8 = black queen side.
 */
class Position(
    val board: IntArray,
    val whiteToMove: Boolean,
    val castling: Int,
    val epSquare: Int,
    val halfmove: Int,
    val fullmove: Int,
) {
    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun start(): Position = fromFen(START_FEN)!!

        /** Returns null for anything that is not a legal, well-formed FEN. */
        fun fromFen(fen: String): Position? {
            if (fen.length > 200) return null
            val parts = fen.trim().split(Regex("\\s+"))
            if (parts.size < 4) return null
            val rows = parts[0].split("/")
            if (rows.size != 8) return null
            val b = IntArray(64)
            for ((r, row) in rows.withIndex()) {
                var f = 0
                for (ch in row) {
                    if (ch in '1'..'8') {
                        f += ch - '0'
                    } else {
                        val idx = "pnbrqk".indexOf(ch.lowercaseChar())
                        if (idx < 0 || f > 7) return null
                        b[r * 8 + f] = if (ch.isUpperCase()) idx + 1 else -(idx + 1)
                        f++
                    }
                }
                if (f != 8) return null
            }
            if (b.count { it == KING } != 1 || b.count { it == -KING } != 1) return null
            for (sq in 0 until 8) if (abs(b[sq]) == PAWN || abs(b[56 + sq]) == PAWN) return null
            val white = when (parts[1]) {
                "w" -> true
                "b" -> false
                else -> return null
            }
            var cast = 0
            if (parts[2] != "-") {
                for (c in parts[2]) {
                    when (c) {
                        'K' -> if (b[60] == KING && b[63] == ROOK) cast = cast or 1
                        'Q' -> if (b[60] == KING && b[56] == ROOK) cast = cast or 2
                        'k' -> if (b[4] == -KING && b[7] == -ROOK) cast = cast or 4
                        'q' -> if (b[4] == -KING && b[0] == -ROOK) cast = cast or 8
                        else -> return null
                    }
                }
            }
            var ep = -1
            if (parts[3] != "-") {
                ep = sqFromName(parts[3]) ?: return null
            }
            val half = parts.getOrNull(4)?.toIntOrNull()?.coerceIn(0, 1000) ?: 0
            val full = parts.getOrNull(5)?.toIntOrNull()?.coerceIn(1, 100000) ?: 1
            val pos = Position(b, white, cast, ep, half, full)
            // The side that just moved must not have left its own king capturable.
            val waiting = !white
            if (pos.isAttacked(pos.kingSquare(waiting), white)) return null
            return pos
        }
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 0 until 8) {
            var empty = 0
            for (f in 0 until 8) {
                val v = board[r * 8 + f]
                if (v == 0) {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    val ch = "pnbrqk"[abs(v) - 1]
                    sb.append(if (v > 0) ch.uppercaseChar() else ch)
                }
            }
            if (empty > 0) sb.append(empty)
            if (r < 7) sb.append('/')
        }
        sb.append(if (whiteToMove) " w " else " b ")
        if (castling == 0) sb.append('-') else {
            if (castling and 1 != 0) sb.append('K')
            if (castling and 2 != 0) sb.append('Q')
            if (castling and 4 != 0) sb.append('k')
            if (castling and 8 != 0) sb.append('q')
        }
        sb.append(' ').append(if (epSquare >= 0) sqName(epSquare) else "-")
        sb.append(' ').append(halfmove).append(' ').append(fullmove)
        return sb.toString()
    }

    fun kingSquare(white: Boolean): Int = board.indexOf(if (white) KING else -KING)

    fun isAttacked(sq: Int, byWhite: Boolean): Boolean {
        val r = sq / 8
        val f = sq % 8
        val s = if (byWhite) 1 else -1
        // A white pawn attacks toward lower row numbers, so the attacker sits one row below (r + 1).
        val pr = if (byWhite) r + 1 else r - 1
        if (pr in 0..7) {
            if (f > 0 && board[pr * 8 + f - 1] == s * PAWN) return true
            if (f < 7 && board[pr * 8 + f + 1] == s * PAWN) return true
        }
        for (d in KNIGHT_D) {
            val nr = r + d[0]
            val nf = f + d[1]
            if (nr in 0..7 && nf in 0..7 && board[nr * 8 + nf] == s * KNIGHT) return true
        }
        for (dr in -1..1) for (df in -1..1) {
            if (dr == 0 && df == 0) continue
            val nr = r + dr
            val nf = f + df
            if (nr in 0..7 && nf in 0..7 && board[nr * 8 + nf] == s * KING) return true
        }
        for (d in DIRS) {
            val diag = d[0] != 0 && d[1] != 0
            var nr = r + d[0]
            var nf = f + d[1]
            while (nr in 0..7 && nf in 0..7) {
                val p = board[nr * 8 + nf]
                if (p != 0) {
                    if (p * s > 0) {
                        val t = abs(p)
                        if (t == QUEEN || (diag && t == BISHOP) || (!diag && t == ROOK)) return true
                    }
                    break
                }
                nr += d[0]
                nf += d[1]
            }
        }
        return false
    }

    fun inCheck(): Boolean = isAttacked(kingSquare(whiteToMove), !whiteToMove)

    private fun addPawn(out: MutableList<Move>, from: Int, to: Int, promote: Boolean) {
        if (promote) {
            out.add(Move(from, to, QUEEN))
            out.add(Move(from, to, ROOK))
            out.add(Move(from, to, BISHOP))
            out.add(Move(from, to, KNIGHT))
        } else {
            out.add(Move(from, to))
        }
    }

    fun pseudoMoves(): List<Move> {
        val out = ArrayList<Move>(48)
        val s = if (whiteToMove) 1 else -1
        for (sq in 0 until 64) {
            val p = board[sq]
            if (p * s <= 0) continue
            val r = sq / 8
            val f = sq % 8
            when (abs(p)) {
                PAWN -> {
                    val dr = -s
                    val nr = r + dr
                    val promoRow = if (whiteToMove) 0 else 7
                    val startRow = if (whiteToMove) 6 else 1
                    if (nr in 0..7) {
                        val to = nr * 8 + f
                        if (board[to] == 0) {
                            addPawn(out, sq, to, nr == promoRow)
                            if (r == startRow && board[to + dr * 8] == 0) out.add(Move(sq, to + dr * 8))
                        }
                        for (df in intArrayOf(-1, 1)) {
                            val nf = f + df
                            if (nf !in 0..7) continue
                            val t = nr * 8 + nf
                            if (board[t] * s < 0 || t == epSquare) addPawn(out, sq, t, nr == promoRow)
                        }
                    }
                }
                KNIGHT -> for (d in KNIGHT_D) {
                    val nr = r + d[0]
                    val nf = f + d[1]
                    if (nr in 0..7 && nf in 0..7 && board[nr * 8 + nf] * s <= 0) out.add(Move(sq, nr * 8 + nf))
                }
                BISHOP, ROOK, QUEEN -> {
                    val t = abs(p)
                    for (d in DIRS) {
                        val diag = d[0] != 0 && d[1] != 0
                        if (t == BISHOP && !diag) continue
                        if (t == ROOK && diag) continue
                        var nr = r + d[0]
                        var nf = f + d[1]
                        while (nr in 0..7 && nf in 0..7) {
                            val q = board[nr * 8 + nf]
                            if (q * s > 0) break
                            out.add(Move(sq, nr * 8 + nf))
                            if (q != 0) break
                            nr += d[0]
                            nf += d[1]
                        }
                    }
                }
                KING -> {
                    for (dr in -1..1) for (df in -1..1) {
                        if (dr == 0 && df == 0) continue
                        val nr = r + dr
                        val nf = f + df
                        if (nr in 0..7 && nf in 0..7 && board[nr * 8 + nf] * s <= 0) out.add(Move(sq, nr * 8 + nf))
                    }
                    if (whiteToMove && sq == 60) {
                        if ((castling and 1) != 0 && board[61] == 0 && board[62] == 0 && board[63] == ROOK &&
                            !isAttacked(60, false) && !isAttacked(61, false)
                        ) out.add(Move(60, 62))
                        if ((castling and 2) != 0 && board[59] == 0 && board[58] == 0 && board[57] == 0 && board[56] == ROOK &&
                            !isAttacked(60, false) && !isAttacked(59, false)
                        ) out.add(Move(60, 58))
                    } else if (!whiteToMove && sq == 4) {
                        if ((castling and 4) != 0 && board[5] == 0 && board[6] == 0 && board[7] == -ROOK &&
                            !isAttacked(4, true) && !isAttacked(5, true)
                        ) out.add(Move(4, 6))
                        if ((castling and 8) != 0 && board[3] == 0 && board[2] == 0 && board[1] == 0 && board[0] == -ROOK &&
                            !isAttacked(4, true) && !isAttacked(3, true)
                        ) out.add(Move(4, 2))
                    }
                }
            }
        }
        return out
    }

    /** Applies a (pseudo-)legal move and returns the new position. */
    fun make(m: Move): Position {
        val nb = board.copyOf()
        val p = nb[m.from]
        val s = if (p > 0) 1 else -1
        var cast = castling
        var ep = -1
        val isPawn = abs(p) == PAWN
        val capture = nb[m.to] != 0
        if (isPawn && m.to == epSquare && nb[m.to] == 0 && m.from % 8 != m.to % 8) {
            nb[(m.from / 8) * 8 + m.to % 8] = 0
        }
        nb[m.to] = if (m.promo != 0) s * m.promo else p
        nb[m.from] = 0
        if (abs(p) == KING) {
            if (abs(m.to - m.from) == 2) {
                if (m.to > m.from) {
                    nb[m.to - 1] = nb[m.to + 1]
                    nb[m.to + 1] = 0
                } else {
                    nb[m.to + 1] = nb[m.to - 2]
                    nb[m.to - 2] = 0
                }
            }
            cast = if (s > 0) cast and 3.inv() else cast and 12.inv()
        }
        for (sq in intArrayOf(m.from, m.to)) {
            when (sq) {
                63 -> cast = cast and 1.inv()
                56 -> cast = cast and 2.inv()
                7 -> cast = cast and 4.inv()
                0 -> cast = cast and 8.inv()
            }
        }
        if (isPawn && abs(m.to - m.from) == 16) ep = (m.from + m.to) / 2
        val half = if (isPawn || capture) 0 else halfmove + 1
        val full = fullmove + if (!whiteToMove) 1 else 0
        return Position(nb, !whiteToMove, cast, ep, half, full)
    }

    /** True if the side that just moved did not leave its own king attacked. */
    fun kingSafeAfter(m: Move): Boolean {
        val n = make(m)
        return !n.isAttacked(n.kingSquare(whiteToMove), !whiteToMove)
    }

    val legalMoves: List<Move> by lazy { pseudoMoves().filter { kingSafeAfter(it) } }

    fun status(): GameStatus {
        if (legalMoves.isEmpty()) return if (inCheck()) GameStatus.CHECKMATE else GameStatus.STALEMATE
        if (halfmove >= 100) return GameStatus.DRAW_FIFTY_MOVE
        var minor = 0
        var other = false
        for (v in board) {
            val t = abs(v)
            if (t == 0 || t == KING) continue
            if (t == KNIGHT || t == BISHOP) minor++ else other = true
        }
        if (!other && minor <= 1) return GameStatus.DRAW_INSUFFICIENT_MATERIAL
        return GameStatus.ONGOING
    }

    /** Standard algebraic notation of a legal move in this position. */
    fun san(m: Move): String {
        val p = board[m.from]
        val t = abs(p)
        val sb = StringBuilder()
        if (t == KING && abs(m.to - m.from) == 2) {
            sb.append(if (m.to > m.from) "O-O" else "O-O-O")
        } else {
            val capture = board[m.to] != 0 || (t == PAWN && m.from % 8 != m.to % 8)
            if (t == PAWN) {
                if (capture) sb.append('a' + m.from % 8)
            } else {
                sb.append("PNBRQK"[t - 1])
                val others = legalMoves.filter {
                    it != m && abs(board[it.from]) == t && it.to == m.to
                }
                if (others.isNotEmpty()) {
                    val sameFile = others.any { it.from % 8 == m.from % 8 }
                    val sameRank = others.any { it.from / 8 == m.from / 8 }
                    if (!sameFile) sb.append('a' + m.from % 8)
                    else if (!sameRank) sb.append('8' - m.from / 8)
                    else sb.append(sqName(m.from))
                }
            }
            if (capture) sb.append('x')
            sb.append(sqName(m.to))
            if (m.promo != 0) sb.append('=').append("PNBRQK"[m.promo - 1])
        }
        val n = make(m)
        if (n.inCheck()) sb.append(if (n.legalMoves.isEmpty()) '#' else '+')
        return sb.toString()
    }

    /** Parses SAN (tolerant of check marks, annotations, "0-0" and missing "=" for promotion). */
    fun parseSan(text: String): Move? {
        val clean = normalizeSan(text.replace('0', 'O'))
        if (clean.isEmpty()) return null
        return legalMoves.firstOrNull { normalizeSan(san(it)) == clean }
    }

    private fun normalizeSan(s: String): String = s.trimEnd('+', '#', '!', '?').replace("=", "")
}
