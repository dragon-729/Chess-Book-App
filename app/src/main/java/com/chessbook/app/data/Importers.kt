package com.chessbook.app.data

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class RemoteGame(val label: String, val pgn: String)

/**
 * Fetches games from public Lichess / Chess.com APIs. Every call either returns real data or
 * throws an IOException with a readable message; nothing is ever reported as imported unless
 * the HTTP request actually succeeded and returned games.
 */
object Importers {
    private val USERNAME = Regex("^[A-Za-z0-9_-]{2,40}$")
    private const val UA = "Chess-Book-Android/0.1 (github.com/dragon-729)"
    private const val MAX_BYTES = 4_000_000

    private fun checkUser(name: String): String {
        val n = name.trim()
        if (!USERNAME.matches(n)) throw IOException("That does not look like a valid username.")
        return n
    }

    private fun get(url: String, accept: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", accept)
        try {
            when (val code = c.responseCode) {
                200 -> {
                    val bytes = c.inputStream.use { it.readNBytesLimited(MAX_BYTES) }
                    return String(bytes, Charsets.UTF_8)
                }
                404 -> throw IOException("User not found.")
                429 -> throw IOException("Rate limited by the server. Please wait a minute and try again.")
                else -> throw IOException("Server returned HTTP $code.")
            }
        } finally {
            c.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > limit) throw IOException("Response too large.")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun label(pgn: String): String {
        fun tag(t: String) = Regex("\\[$t \"(.*?)\"\\]").find(pgn)?.groupValues?.get(1) ?: "?"
        return "${tag("White")} vs ${tag("Black")}  ${tag("Result")}  ${tag("Date")}"
    }

    suspend fun lichess(username: String, max: Int = 10): List<RemoteGame> = withContext(Dispatchers.IO) {
        val u = checkUser(username)
        val text = get(
            "https://lichess.org/api/games/user/$u?max=$max&tags=true&opening=true",
            "application/x-chess-pgn",
        )
        val games = text.split(Regex("\\n\\s*\\n(?=\\[Event )")).map { it.trim() }.filter { it.startsWith("[") }
        if (games.isEmpty()) throw IOException("No games found for $u on Lichess.")
        games.map { RemoteGame(label(it), it) }
    }

    suspend fun chessCom(username: String, max: Int = 10): List<RemoteGame> = withContext(Dispatchers.IO) {
        val u = checkUser(username).lowercase()
        val archives = JSONObject(get("https://api.chess.com/pub/player/$u/games/archives", "application/json"))
            .getJSONArray("archives")
        if (archives.length() == 0) throw IOException("No games found for $u on Chess.com.")
        val out = ArrayList<RemoteGame>()
        var i = archives.length() - 1
        while (i >= 0 && out.size < max) {
            val url = archives.getString(i)
            if (!url.startsWith("https://api.chess.com/")) throw IOException("Unexpected archive address.")
            val games = JSONObject(get(url, "application/json")).getJSONArray("games")
            var g = games.length() - 1
            while (g >= 0 && out.size < max) {
                val pgn = games.getJSONObject(g).optString("pgn", "")
                if (pgn.isNotBlank()) out.add(RemoteGame(label(pgn), pgn))
                g--
            }
            i--
        }
        if (out.isEmpty()) throw IOException("No games found for $u on Chess.com.")
        out
    }

    suspend fun readText(resolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(uri) ?: throw IOException("Could not open the file.")
        val bytes = stream.use { it.readNBytesLimited(2_000_000) }
        String(bytes, Charsets.UTF_8)
    }
}
