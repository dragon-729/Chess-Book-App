package com.chessbook.app.chess

data class ParsedGame(
    val headers: Map<String, String>,
    val startFen: String,
    val moves: List<Move>,
    val sans: List<String>,
)

object Pgn {
    private const val MAX_CHARS = 2_000_000
    private val HEADER = Regex("\\[(\\w+)\\s+\"(.*?)\"\\]")
    private val RESULTS = setOf("1-0", "0-1", "1/2-1/2", "*")

    /** Parses the first game in [text]. Fails with a readable message instead of guessing. */
    fun parse(text: String): Result<ParsedGame> {
        if (text.isBlank()) return Result.failure(IllegalArgumentException("Nothing to import."))
        if (text.length > MAX_CHARS) return Result.failure(IllegalArgumentException("PGN is too large."))

        val headers = LinkedHashMap<String, String>()
        for (m in HEADER.findAll(text)) headers.putIfAbsent(m.groupValues[1], m.groupValues[2])

        val startFen = if (headers["SetUp"] == "1" && headers.containsKey("FEN")) headers["FEN"]!! else Position.START_FEN
        var pos = Position.fromFen(startFen)
            ?: return Result.failure(IllegalArgumentException("The FEN in this PGN is not a valid position."))

        var body = text.replace(Regex("\\[[^\\]]*\\]"), " ")
        body = body.replace(Regex("\\{[^}]*\\}"), " ")
        body = body.replace(Regex(";[^\\n]*"), " ")
        var previous: String
        do {
            previous = body
            body = body.replace(Regex("\\([^()]*\\)"), " ")
        } while (body != previous)
        body = body.replace(Regex("\\$\\d+"), " ")

        val moves = ArrayList<Move>()
        val sans = ArrayList<String>()
        for (raw in body.split(Regex("\\s+"))) {
            var token = raw.replace(Regex("^\\d+\\.+"), "")
            if (token.isEmpty() || token.all { it == '.' }) continue
            if (token in RESULTS) break
            if (raw in RESULTS) break
            token = token.trim()
            val move = pos.parseSan(token)
                ?: return Result.failure(
                    IllegalArgumentException("Could not read move \"$token\" after ${moves.size} moves. Check the PGN."),
                )
            sans.add(pos.san(move))
            moves.add(move)
            pos = pos.make(move)
        }
        if (moves.isEmpty()) return Result.failure(IllegalArgumentException("No moves found in this PGN."))
        return Result.success(ParsedGame(headers, startFen, moves, sans))
    }

    /** Builds PGN text from a start position and its SAN list. */
    fun export(headers: Map<String, String>, startFen: String, sans: List<String>, result: String = "*"): String {
        val sb = StringBuilder()
        val h = LinkedHashMap<String, String>()
        h["Event"] = headers["Event"] ?: "Chess-Book analysis"
        h["Site"] = headers["Site"] ?: "Chess-Book"
        h["White"] = headers["White"] ?: "?"
        h["Black"] = headers["Black"] ?: "?"
        h["Result"] = headers["Result"] ?: result
        if (startFen != Position.START_FEN) {
            h["SetUp"] = "1"
            h["FEN"] = startFen
        }
        for ((k, v) in h) sb.append("[$k \"$v\"]\n")
        sb.append('\n')
        val start = Position.fromFen(startFen)
        var moveNo = start?.fullmove ?: 1
        var white = start?.whiteToMove ?: true
        for ((i, san) in sans.withIndex()) {
            if (white) sb.append(moveNo).append(". ")
            else if (i == 0) sb.append(moveNo).append("... ")
            sb.append(san).append(' ')
            if (!white) moveNo++
            white = !white
        }
        sb.append(h["Result"])
        return sb.toString()
    }
}
