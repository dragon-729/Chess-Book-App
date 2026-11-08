package com.chessbook.app.chess

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolMathTest {
    @Test fun equalRatingsExpectHalf() {
        assertEquals(0.5, EloMath.expected(1500, 1500), 1e-9)
    }

    @Test fun eloUpdateMatchesFormula() {
        // 1600 beats 1400 with K=20: expected ~0.7597, gain ~4.8 -> 1605
        assertEquals(1605, EloMath.newRating(1600, 1400, 1.0, 20))
        // 1400 loses to 1600 with K=20: loses ~4.8 more precisely 20*(0-0.2403)= -4.8 -> 1395
        assertEquals(1395, EloMath.newRating(1400, 1600, 0.0, 20))
        // Draw between equal players changes nothing.
        assertEquals(1500, EloMath.newRating(1500, 1500, 0.5, 20))
    }

    @Test fun roundRobinPairsEveryoneExactlyOnce() {
        val players = listOf("A", "B", "C", "D", "E", "F")
        val rounds = Tournament.roundRobin(players)
        assertEquals(5, rounds.size)
        val seen = HashSet<Set<String>>()
        for (r in rounds) {
            assertEquals(3, r.size)
            val inRound = r.flatMap { listOf(it.first, it.second) }
            assertEquals(6, inRound.toSet().size) // nobody plays twice in a round
            for (p in r) assertTrue(seen.add(setOf(p.first, p.second)))
        }
        assertEquals(15, seen.size)
    }

    @Test fun oddPlayersGetByes() {
        val rounds = Tournament.roundRobin(listOf("A", "B", "C"))
        assertEquals(3, rounds.size)
        assertEquals(3, rounds.count { r -> r.any { it.first == "BYE" || it.second == "BYE" } })
    }
}
