package com.chessbook.app.chess

import com.chessbook.app.engine.Candidate
import com.chessbook.app.engine.EngineResult
import com.chessbook.app.engine.HumanTouch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HumanTouchTest {
    private fun mv(a: String, b: String) = Move(sqFromName(a)!!, sqFromName(b)!!)

    private fun result(vararg c: Pair<Move, Int>) = EngineResult(
        best = c.first().first, scoreCp = c.first().second, mateIn = null, depth = 10, nodes = 1,
        pv = emptyList(), engineName = "test", candidates = c.map { Candidate(it.first, it.second) },
    )

    @Test fun singleCandidateReturnsBest() {
        val r = result(mv("e2", "e4") to 30)
        assertEquals(mv("e2", "e4"), HumanTouch.pick(Position.start(), r, 1800, Random(1)))
    }

    @Test fun variesAmongCloseMovesButNeverPlaysABadOne() {
        val e4 = mv("e2", "e4"); val d4 = mv("d2", "d4"); val a3 = mv("a2", "a3")
        val r = result(e4 to 30, d4 to 20, a3 to -200)
        val rnd = Random(7)
        val seen = HashSet<Move>()
        repeat(300) { HumanTouch.pick(Position.start(), r, 1800, rnd)?.let(seen::add) }
        assertTrue(e4 in seen)
        assertTrue(d4 in seen)
        assertFalse(a3 in seen)
    }

    @Test fun neverGivesUpAForcedMate() {
        val mate = mv("e2", "e4"); val other = mv("d2", "d4")
        val r = result(mate to 10000, other to 9990)
        repeat(50) { assertEquals(mate, HumanTouch.pick(Position.start(), r, 1400, Random(it))) }
    }

    @Test fun weakerBotsWobbleMore() {
        assertTrue(HumanTouch.toleranceCp(1400, 20) > HumanTouch.toleranceCp(3000, 20))
    }

    @Test fun thinkTimeStaysNatural() {
        repeat(50) {
            val t = HumanTouch.thinkTimeMs(Position.start(), Random(it))
            assertTrue(t in 300L..2800L)
        }
    }
}
