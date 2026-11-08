package com.chessbook.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chessbook.app.engine.EngineTier

enum class BoardTheme(val label: String, val light: Long, val dark: Long) {
    SLATE("Slate", 0xFFDDE6EE, 0xFF6C8CAB),
    WALNUT("Walnut", 0xFFF0D9B5, 0xFFB58863),
    FOREST("Forest", 0xFFEEEED2, 0xFF6F9A5B),
}

/**
 * Premium entitlement holder. Premium is only ever set from the server's answer
 * (Payments.refresh / Payments.onSuccess), never from local storage.
 * The debug-only simulation switch stays for testing the Full engine.
 */
object AppSettings {
    private var prefs: android.content.SharedPreferences? = null

    var tier by mutableStateOf(EngineTier.LITE)
        private set
    var boardTheme by mutableStateOf(BoardTheme.SLATE)
        private set
    var premiumActive by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("chessbook", Context.MODE_PRIVATE)
        prefs = p
        tier = runCatching { EngineTier.valueOf(p.getString("tier", "LITE")!!) }.getOrDefault(EngineTier.LITE)
        boardTheme = runCatching { BoardTheme.valueOf(p.getString("theme", "SLATE")!!) }.getOrDefault(BoardTheme.SLATE)
        // Entitlements are never read from local storage; they must be verified by the server.
        premiumActive = false
        tier = EngineTier.LITE
    }

    /** The tier actually used for analysis: Full only while premium is active. */
    val effectiveTier: EngineTier get() = if (premiumActive) tier else EngineTier.LITE

    fun selectTier(t: EngineTier): Boolean {
        if (t == EngineTier.FULL && !premiumActive) return false
        tier = t
        prefs?.edit()?.putString("tier", t.name)?.apply()
        return true
    }

    fun selectTheme(t: BoardTheme) {
        boardTheme = t
        prefs?.edit()?.putString("theme", t.name)?.apply()
    }

    /** Called only with the answer from the server (see Payments.kt). */
    fun setPremiumFromServer(active: Boolean) {
        premiumActive = active
        if (!active) tier = EngineTier.LITE
    }

    /** Debug builds only; the UI hides the switch in release builds. */
    fun debugSimulatePremium(on: Boolean) {
        premiumActive = on
        if (!on) tier = EngineTier.LITE
    }
}
