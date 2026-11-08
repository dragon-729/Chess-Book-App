package com.chessbook.app.engine

import com.chessbook.app.chess.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

/**
 * Talks to a real UCI engine (Stockfish) running as a native executable bundled as
 * `libstockfish.so` in jniLibs. One short-lived process per request keeps memory use low and
 * makes cancellation simple: cancelling the coroutine kills the process.
 */
class UciEngine(private val binary: File, private val label: String = "Stockfish") : ChessEngine {
    override val name: String get() = label
    override val supportsElo = true

    override suspend fun analyze(pos: Position, limits: EngineLimits): EngineResult =
        withContext(Dispatchers.IO) {
            val proc = ProcessBuilder(binary.absolutePath).redirectErrorStream(true).start()
            val job = coroutineContext[Job]
            val handle = job?.invokeOnCompletion { proc.destroyForcibly() }
            val writer = proc.outputStream.bufferedWriter()
            val reader = proc.inputStream.bufferedReader()

            fun send(s: String) {
                writer.write(s)
                writer.write("\n")
                writer.flush()
            }

            try {
                send("uci")
                waitFor(reader) { it == "uciok" }
                send("setoption name Threads value ${limits.threads}")
                send("setoption name Hash value ${limits.hashMb}")
                send("setoption name MultiPV value ${limits.multiPv}")
                if (limits.elo != null) {
                    send("setoption name UCI_LimitStrength value true")
                    send("setoption name UCI_Elo value ${limits.elo}")
                }
                send("isready")
                waitFor(reader) { it == "readyok" }
                send("ucinewgame")
                send("position fen ${pos.toFen()}")
                send("go depth ${limits.maxDepth} movetime ${limits.moveTimeMs}")

                var depth = 0
                var nodes = 0L
                var cp = 0
                var mate: Int? = null
                var pv: List<String> = emptyList()
                var bestToken: String? = null
                // multipv index -> (first move in UCI form, white-perspective centipawns)
                val lines = HashMap<Int, Pair<String, Int>>()
                while (true) {
                    coroutineContext.ensureActive()
                    val line = reader.readLine() ?: throw IOException("Engine closed unexpectedly")
                    if (line.startsWith("bestmove")) {
                        bestToken = line.split(" ").getOrNull(1)
                        break
                    }
                    if (!line.startsWith("info ") || !line.contains(" pv ")) continue
                    val t = line.split(" ")
                    val mpv = t.indexOf("multipv").let { if (it >= 0) t[it + 1].toIntOrNull() ?: 1 else 1 }
                    run {
                        val s = t.indexOf("score")
                        val first = t.getOrNull(t.indexOf("pv") + 1)
                        if (s >= 0 && first != null) {
                            val v = t.getOrNull(s + 2)?.toIntOrNull()
                            if (v != null) {
                                val sg = if (pos.whiteToMove) 1 else -1
                                val lineCp = if (t.getOrNull(s + 1) == "mate") (if (v * sg > 0) 10000 else -10000) else v * sg
                                lines[mpv] = first to lineCp
                            }
                        }
                    }
                    if (mpv != 1) continue
                    t.indexOf("depth").takeIf { it >= 0 }?.let { depth = t[it + 1].toIntOrNull() ?: depth }
                    t.indexOf("nodes").takeIf { it >= 0 }?.let { nodes = t[it + 1].toLongOrNull() ?: nodes }
                    val si = t.indexOf("score")
                    if (si >= 0) {
                        val kind = t[si + 1]
                        val value = t[si + 2].toIntOrNull() ?: 0
                        val sign = if (pos.whiteToMove) 1 else -1
                        if (kind == "cp") { cp = value * sign; mate = null }
                        else if (kind == "mate") { mate = value * sign; cp = if (value * sign > 0) 10000 else -10000 }
                    }
                    pv = t.drop(t.indexOf("pv") + 1)
                }
                val best = pos.legalMoves.firstOrNull { it.uci() == bestToken }
                val legal = pos.legalMoves
                val candidates = lines.entries.sortedBy { it.key }.mapNotNull { e ->
                    legal.firstOrNull { it.uci() == e.value.first }?.let { Candidate(it, e.value.second) }
                }
                EngineResult(best, cp, mate, depth, nodes, pv, label, candidates)
            } finally {
                runCatching { send("quit") }
                handle?.dispose()
                proc.destroy()
            }
        }

    private fun waitFor(reader: BufferedReader, done: (String) -> Boolean) {
        while (true) {
            val line = reader.readLine() ?: throw IOException("Engine closed unexpectedly")
            if (done(line.trim())) return
        }
    }
}

/** Uses [primary] and silently falls back to [fallback] if the native engine can't start. */
class ResilientEngine(private val primary: ChessEngine, private val fallback: ChessEngine) : ChessEngine {
    @Volatile private var primaryBroken = false
    @Volatile private var lastUsed: ChessEngine = primary

    override val name: String get() = if (primaryBroken) fallback.name else lastUsed.name
    override val supportsElo: Boolean get() = !primaryBroken && primary.supportsElo

    override suspend fun analyze(pos: Position, limits: EngineLimits): EngineResult {
        if (!primaryBroken) {
            try {
                lastUsed = primary
                return primary.analyze(pos, limits)
            } catch (e: IOException) {
                primaryBroken = true
            } catch (e: SecurityException) {
                primaryBroken = true
            }
        }
        lastUsed = fallback
        return fallback.analyze(pos, limits)
    }

    val usingNative: Boolean get() = !primaryBroken && primary is UciEngine
}
