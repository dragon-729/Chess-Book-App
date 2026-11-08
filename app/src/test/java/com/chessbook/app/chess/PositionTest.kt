package com.chessbook.app.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionTest {

    private fun perft(p: Position, depth: Int): Long {
        if (depth == 0) return 1
        var n = 0L
        for (m in p.legalMoves) n += perft(p.make(m), depth - 1)
        return n
    }

    private fun fen(s: String) = Position.fromFen(s)!!

    @Test fun perftStartPosition() {
        val p = Position.start()
        assertEquals(20, perft(p, 1))
        assertEquals(400, perft(p, 2))
        assertEquals(8902, perft(p, 3))
    }

    @Test fun perftKiwipeteCoversCastlingAndEnPassant() {
        val p = fen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48, perft(p, 1))
        assertEquals(2039, perft(p, 2))
        assertEquals(97862, perft(p, 3))
    }

    @Test fun perftEndgameWithEnPassantPins() {
        val p = fen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(14, perft(p, 1))
        assertEquals(191, perft(p, 2))
        assertEquals(2812, perft(p, 3))
    }

    @Test fun perftPromotionPosition() {
        val p = fen("n1n5/PPPk4/8/8/8/8/4Kppp/5N1N b - - 0 1")
        assertEquals(24, perft(p, 1))
        assertEquals(496, perft(p, 2))
        assertEquals(9483, perft(p, 3))
    }

    @Test fun fenRoundTrip() {
        val s = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        assertEquals(s, fen(s).toFen())
    }

    @Test fun rejectsBadFen() {
        assertNull(Position.fromFen("not a fen"))
        assertNull(Position.fromFen("8/8/8/8/8/8/8/8 w - - 0 1")) // no kings
        assertNull(Position.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBN w KQkq - 0 1")) // short rank
    }

    @Test fun illegalMovesAreRejected() {
        val p = Position.start()
        assertNull(p.parseSan("e5"))        // pawn cannot jump 3 squares
        assertNull(p.parseSan("Nf6"))       // wrong side
        assertNotNull(p.parseSan("Nf3"))
    }

    @Test fun foolsMateIsCheckmate() {
        var p = Position.start()
        for (san in listOf("f3", "e5", "g4", "Qh4#")) p = p.make(p.parseSan(san)!!)
        assertEquals(GameStatus.CHECKMATE, p.status())
    }

    @Test fun stalemateDetected() {
        val p = fen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1")
        assertEquals(GameStatus.STALEMATE, p.status())
    }

    @Test fun castlingEnPassantAndPromotionViaPgn() {
        val pgn = """
            [Event "Test"]
            1. e4 d5 2. e5 f5 3. exf6 Nxf6 4. Nf3 e6 5. Be2 Bd6 6. O-O O-O 7. d4 Nc6 *
        """.trimIndent()
        val g = Pgn.parse(pgn).getOrThrow()
        assertEquals(14, g.moves.size)
        assertTrue(g.sans.contains("exf6"))
        assertEquals(2, g.sans.count { it == "O-O" })

        val promo = fen("8/P6k/8/8/8/8/8/K7 w - - 0 1")
        val m = promo.parseSan("a8=Q+")
        assertNotNull(m)
        assertEquals(QUEEN, m!!.promo)
    }

    @Test fun pgnWithCommentsAndVariationsParses() {
        val pgn = "1. e4 {best by test} e5 (1... c5 2. Nf3) 2. Nf3 \$1 Nc6 1-0"
        assertEquals(4, Pgn.parse(pgn).getOrThrow().moves.size)
    }

    @Test fun badPgnReportsError() {
        assertTrue(Pgn.parse("1. e4 e5 2. Ke3").isFailure) // illegal king move
        assertTrue(Pgn.parse("").isFailure)
    }

    @Test fun pgnExportRoundTrips() {
        val g = Pgn.parse("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6").getOrThrow()
        val text = Pgn.export(g.headers, g.startFen, g.sans)
        assertEquals(g.moves, Pgn.parse(text).getOrThrow().moves)
    }
}
