package com.chessbook.app.chess

import com.chessbook.app.engine.BuiltinEngine
import com.chessbook.app.engine.EngineLimits
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val limits = EngineLimits(maxDepth = 4, moveTimeMs = 5000)

    @Test fun findsMateInOne() = runBlocking {
        // Back-rank mate: Ra8#.
        val p = Position.fromFen("6k1/5ppp/8/8/8/8/8/R3K3 w - - 0 1")!!
        val r = BuiltinEngine().analyze(p, limits)
        assertEquals("a1a8", r.best!!.uci())
        assertEquals(1, r.mateIn)
    }

    @Test fun takesFreeQueen() = runBlocking {
        val p = Position.fromFen("4k3/8/8/3q4/8/8/8/3RK3 w - - 0 1")!!
        val r = BuiltinEngine().analyze(p, limits)
        assertEquals("d1d5", r.best!!.uci())
    }

    @Test fun blackPerspectiveScoreIsConvertedToWhite() = runBlocking {
        // Black has a queen against a rook: score from White's side must be clearly negative.
        val p = Position.fromFen("4k3/8/8/8/3q4/8/8/4K2R b - - 0 1")!!
        val r = BuiltinEngine().analyze(p, limits)
        assertNotNull(r.best)
        assertTrue("score was ${r.scoreCp}", r.scoreCp < -200)
    }

    @Test fun checkmatedPositionHasNoMove() = runBlocking {
        val p = Position.fromFen("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3")!!
        val r = BuiltinEngine().analyze(p, limits)
        assertNull(r.best)
        assertEquals(0, r.mateIn?.let { kotlin.math.abs(it) })
    }
}
