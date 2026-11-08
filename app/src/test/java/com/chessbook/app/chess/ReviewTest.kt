package com.chessbook.app.chess

import com.chessbook.app.engine.BuiltinEngine
import com.chessbook.app.engine.EngineLimits
import com.chessbook.app.engine.MoveClass
import com.chessbook.app.engine.ReviewMath
import com.chessbook.app.engine.Reviewer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewTest {
    @Test fun classificationThresholds() {
        assertEquals(MoveClass.BEST, ReviewMath.classify(0.0, true))
        assertEquals(MoveClass.EXCELLENT, ReviewMath.classify(1.5, false))
        assertEquals(MoveClass.GOOD, ReviewMath.classify(4.0, false))
        assertEquals(MoveClass.INACCURACY, ReviewMath.classify(8.0, false))
        assertEquals(MoveClass.MISTAKE, ReviewMath.classify(15.0, false))
        assertEquals(MoveClass.BLUNDER, ReviewMath.classify(30.0, false))
    }

    @Test fun accuracyDropsWithLoss() {
        assertTrue(ReviewMath.accuracyOf(0.0) > 99.0)
        assertTrue(ReviewMath.accuracyOf(30.0) < ReviewMath.accuracyOf(5.0))
    }

    @Test fun foolsMateGameReviewFlagsWhiteBlunders() = runBlocking {
        val g = Pgn.parse("1. f3 e5 2. g4 Qh4#").getOrThrow()
        var p = Position.fromFen(g.startFen)!!
        val positions = arrayListOf(p)
        for (m in g.moves) { p = p.make(m); positions.add(p) }
        val review = Reviewer.review(BuiltinEngine(), positions, g.moves, EngineLimits(3, 2000))
        assertEquals(5, review.evals.size)
        assertEquals(4, review.classes.size)
        // 2. g4?? allows mate in one: White's 3rd move (index 2) must be a blunder.
        assertEquals(MoveClass.BLUNDER, review.classes[2])
        assertTrue(review.accuracyWhite < review.accuracyBlack)
    }
}
