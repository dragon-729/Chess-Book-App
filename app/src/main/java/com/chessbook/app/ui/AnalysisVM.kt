package com.chessbook.app.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chessbook.app.chess.Move
import com.chessbook.app.chess.ParsedGame
import com.chessbook.app.chess.Pgn
import com.chessbook.app.chess.Position
import com.chessbook.app.chess.pvToSan
import com.chessbook.app.data.AppSettings
import com.chessbook.app.data.Importers
import com.chessbook.app.data.RemoteGame
import com.chessbook.app.engine.EngineResult
import com.chessbook.app.engine.EngineHolder
import com.chessbook.app.engine.GameReview
import com.chessbook.app.engine.Reviewer
import com.chessbook.app.engine.TierLimits
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Lets other screens (e.g. Play) hand a PGN to the Analysis tab. */
object Handoff {
    var pendingPgn by mutableStateOf<String?>(null)
}

class AnalysisVM(app: Application) : AndroidViewModel(app) {
    private val engine = EngineHolder.get(app)
    private var job: Job? = null

    var startFen by mutableStateOf(Position.START_FEN); private set
    var headers by mutableStateOf<Map<String, String>>(emptyMap()); private set

    /** Every line is a full move list from the start. Line 0 is the imported/main line. */
    var lines by mutableStateOf(listOf<List<Move>>(emptyList())); private set
    var lineIdx by mutableIntStateOf(0); private set
    var ply by mutableIntStateOf(0); private set
    var positions by mutableStateOf(listOf(Position.start())); private set
    var sans by mutableStateOf(listOf<String>()); private set
    var flipped by mutableStateOf(false)

    var result by mutableStateOf<EngineResult?>(null); private set
    var pvText by mutableStateOf(""); private set
    var analyzing by mutableStateOf(false); private set

    var review by mutableStateOf<GameReview?>(null); private set
    var reviewing by mutableStateOf(false); private set
    var reviewProgress by mutableStateOf(0 to 1); private set

    var notice by mutableStateOf<String?>(null)

    var importBusy by mutableStateOf(false); private set
    var importResults by mutableStateOf<List<RemoteGame>>(emptyList()); private set

    val current: Position get() = positions[ply.coerceIn(0, positions.size - 1)]
    val lastMove: Move? get() = lines[lineIdx].getOrNull(ply - 1)
    val engineName: String get() = engine.name
    val usingNative: Boolean get() = engine.usingNative

    private fun cores() = Runtime.getRuntime().availableProcessors()
    private fun heapMb() = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()

    private fun rebuild() {
        val start = Position.fromFen(startFen) ?: Position.start()
        val ps = ArrayList<Position>()
        val ss = ArrayList<String>()
        var cur = start
        ps.add(cur)
        for (m in lines[lineIdx]) {
            ss.add(cur.san(m))
            cur = cur.make(m)
            ps.add(cur)
        }
        positions = ps
        sans = ss
    }

    private fun stopWork() {
        job?.cancel()
        analyzing = false
        reviewing = false
    }

    fun goTo(p: Int) {
        stopWork()
        ply = p.coerceIn(0, positions.size - 1)
        result = null
        pvText = ""
    }

    fun onMove(m: Move) {
        stopWork()
        val line = lines[lineIdx]
        if (ply < line.size) {
            if (line[ply] == m) {
                goTo(ply + 1)
                return
            }
            // A different move creates a new branch; the original line stays untouched.
            lines = lines + listOf(line.take(ply) + m)
            lineIdx = lines.size - 1
        } else {
            lines = lines.toMutableList().also { it[lineIdx] = line + m }
        }
        review = null
        rebuild()
        goTo(ply + 1)
    }

    fun selectLine(i: Int) {
        stopWork()
        lineIdx = i.coerceIn(0, lines.size - 1)
        rebuild()
        review = null
        goTo(ply.coerceAtMost(lines[lineIdx].size))
    }

    private fun loadParsed(g: ParsedGame) {
        stopWork()
        startFen = g.startFen
        headers = g.headers
        lines = listOf(g.moves)
        lineIdx = 0
        review = null
        rebuild()
        goTo(0)
    }

    fun loadFen(text: String): Boolean {
        val pos = Position.fromFen(text.trim())
        if (pos == null) {
            notice = "That is not a valid FEN position."
            return false
        }
        stopWork()
        startFen = pos.toFen()
        headers = emptyMap()
        lines = listOf(emptyList())
        lineIdx = 0
        review = null
        rebuild()
        goTo(0)
        notice = "Position loaded."
        return true
    }

    fun loadPgn(text: String): Boolean {
        val r = Pgn.parse(text)
        r.onSuccess {
            loadParsed(it)
            notice = "Loaded ${it.moves.size} moves."
        }.onFailure { notice = it.message ?: "Could not read that PGN." }
        return r.isSuccess
    }

    /** Accepts either a FEN or a PGN, whichever it turns out to be. */
    fun loadAny(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) { notice = "Nothing to import."; return false }
        return if (!t.contains('[') && t.count { it == '/' } == 7 && !t.contains("1.")) loadFen(t) else loadPgn(t)
    }

    fun loadFile(resolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            try {
                loadPgn(Importers.readText(resolver, uri))
            } catch (e: Exception) {
                notice = "Could not read the file: ${e.message}"
            }
        }
    }

    fun fetchRemote(lichess: Boolean, username: String) {
        viewModelScope.launch {
            importBusy = true
            importResults = emptyList()
            try {
                importResults = if (lichess) Importers.lichess(username) else Importers.chessCom(username)
                notice = "Fetched ${importResults.size} games. Pick one to load."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = e.message ?: "Could not fetch games."
            } finally {
                importBusy = false
            }
        }
    }

    fun pickRemote(g: RemoteGame) {
        importResults = emptyList()
        loadPgn(g.pgn)
    }

    fun clearImportResults() { importResults = emptyList() }

    fun exportPgn(): String = Pgn.export(headers, startFen, sans)

    fun analyze() {
        stopWork()
        val pos = current
        val limits = TierLimits.forTier(AppSettings.effectiveTier, engine.usingNative, cores(), heapMb())
        job = viewModelScope.launch {
            analyzing = true
            try {
                val r = engine.analyze(pos, limits)
                if (current === pos) {
                    result = r
                    pvText = pvToSan(pos, r.pv)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = "Engine error: ${e.message}"
            } finally {
                analyzing = false
            }
        }
    }

    fun stop() = stopWork()

    fun reviewGame() {
        val moves = lines[lineIdx]
        if (moves.isEmpty()) { notice = "Load or play some moves first."; return }
        stopWork()
        val ps = positions
        val limits = TierLimits.forReview(AppSettings.effectiveTier, engine.usingNative, cores(), heapMb())
        job = viewModelScope.launch {
            reviewing = true
            reviewProgress = 0 to ps.size
            try {
                review = Reviewer.review(engine, ps, moves, limits) { d, t -> reviewProgress = d to t }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice = "Review failed: ${e.message}"
            } finally {
                reviewing = false
            }
        }
    }

    override fun onCleared() {
        job?.cancel()
    }
}
