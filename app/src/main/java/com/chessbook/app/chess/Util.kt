package com.chessbook.app.chess

/** Converts a UCI principal variation into readable SAN, stopping at the first unreadable move. */
fun pvToSan(start: Position, pv: List<String>, limit: Int = 10): String {
    var p = start
    val out = ArrayList<String>()
    for (u in pv.take(limit)) {
        val m = p.legalMoves.firstOrNull { it.uci() == u } ?: break
        out.add(p.san(m))
        p = p.make(m)
    }
    return out.joinToString(" ")
}

fun gameResultText(pos: Position): String? = when (pos.status()) {
    GameStatus.ONGOING -> null
    GameStatus.CHECKMATE -> if (pos.whiteToMove) "Checkmate — Black wins" else "Checkmate — White wins"
    GameStatus.STALEMATE -> "Draw by stalemate"
    GameStatus.DRAW_FIFTY_MOVE -> "Draw by the fifty-move rule"
    GameStatus.DRAW_INSUFFICIENT_MATERIAL -> "Draw — insufficient material"
}
