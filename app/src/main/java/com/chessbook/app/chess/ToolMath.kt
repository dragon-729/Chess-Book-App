package com.chessbook.app.chess

import kotlin.math.pow
import kotlin.math.roundToInt

/** Standard Elo model: expected score = 1 / (1 + 10^((opponent - own) / 400)). */
object EloMath {
    fun expected(own: Int, opponent: Int): Double = 1.0 / (1.0 + 10.0.pow((opponent - own) / 400.0))

    /** [score] is 1.0 for a win, 0.5 for a draw, 0.0 for a loss. Returns the rating change. */
    fun change(own: Int, opponent: Int, score: Double, k: Int): Double = k * (score - expected(own, opponent))

    fun newRating(own: Int, opponent: Int, score: Double, k: Int): Int =
        (own + change(own, opponent, score, k)).roundToInt()
}

object Tournament {
    /** Circle-method round robin. Odd player counts get a "BYE" opponent. */
    fun roundRobin(players: List<String>): List<List<Pair<String, String>>> {
        if (players.size < 2) return emptyList()
        val arr = players.toMutableList()
        if (arr.size % 2 == 1) arr.add("BYE")
        val n = arr.size
        val rounds = ArrayList<List<Pair<String, String>>>()
        repeat(n - 1) { r ->
            val pairs = (0 until n / 2).map { i ->
                val a = arr[i]
                val b = arr[n - 1 - i]
                // Alternate colours for the fixed player so they are not always White.
                if (i == 0 && r % 2 == 1) b to a else a to b
            }
            rounds.add(pairs)
            arr.add(1, arr.removeAt(n - 1))
        }
        return rounds
    }
}
