package com.chessbook.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chessbook.app.engine.EngineTier

enum class BoardTheme(val label: String, val light: Long, val dark: Long) {
    WALNUT("Classic Wood", 0xFFF0D9B5, 0xFFB58863),
    FOREST("Forest", 0xFFEEEED2, 0xFF6F9A5B),
    SLATE("Slate", 0xFFDDE6EE, 0xFF6C8CAB),
    ROYAL("Royal", 0xFFE9E1F6, 0xFF8466C2),
    CORAL("Coral", 0xFFFBE6D6, 0xFFD9896B),
    MIDNIGHT("Midnight", 0xFFA9B8D0, 0xFF3F5272),
}

/**
 * Premium entitlement holder. Premium is only ever set from the server's answer
 * (Payments.refresh / Payments.onSuccess), never from local storage.
 * The debug-only simulation switch stays for testing the Full engine.
 *
 * The Premium play options (custom Elo, Human touch) are only *preferences* stored locally; they
 * take effect only while [premiumActive] is true (see [effectiveCustomElo], [effectiveHumanTouch]).
 */
object AppSettings {
    const val MIN_ELO = 1320
    const val MAX_ELO = 3190

    private var prefs: android.content.SharedPreferences? = null

    var tier by mutableStateOf(EngineTier.LITE)
        private set
    var boardTheme by mutableStateOf(BoardTheme.WALNUT)
        private set
    var premiumActive by mutableStateOf(false)
        private set

    /** Premium: play against a bot of exactly this Elo instead of one of the five fixed levels. */
    var useCustomElo by mutableStateOf(false)
        private set
    var customElo by mutableIntStateOf(1800)
        private set

    /** Premium: the bot plays and "thinks" more like a person. */
    var humanTouch by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("chessbook", Context.MODE_PRIVATE)
        prefs = p
        tier = runCatching { EngineTier.valueOf(p.getString("tier", "LITE")!!) }.getOrDefault(EngineTier.LITE)
        boardTheme = runCatching { BoardTheme.valueOf(p.getString("theme", "WALNUT")!!) }.getOrDefault(BoardTheme.WALNUT)
        useCustomElo = p.getBoolean("useCustomElo", false)
        customElo = p.getInt("customElo", 1800).coerceIn(MIN_ELO, MAX_ELO)
        humanTouch = p.getBoolean("humanTouch", false)
        // Entitlements are never read from local storage; they must be verified by the server.
        premiumActive = false
        tier = EngineTier.LITE
    }

    /** The tier actually used for analysis: Full only while premium is active. */
    val effectiveTier: EngineTier get() = if (premiumActive) tier else EngineTier.LITE

    /** Custom bot Elo, or null when the person is not Premium or has it switched off. */
    val effectiveCustomElo: Int? get() = if (premiumActive && useCustomElo) customElo else null

    val effectiveHumanTouch: Boolean get() = premiumActive && humanTouch

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

    fun switchCustomElo(on: Boolean) {
        if (on && !premiumActive) return
        useCustomElo = on
        prefs?.edit()?.putBoolean("useCustomElo", on)?.apply()
    }

    /** [persist] is false while a slider is being dragged; save once when the drag ends. */
    fun changeCustomElo(v: Int, persist: Boolean = true) {
        customElo = v.coerceIn(MIN_ELO, MAX_ELO)
        if (persist) prefs?.edit()?.putInt("customElo", customElo)?.apply()
    }

    fun switchHumanTouch(on: Boolean) {
        if (on && !premiumActive) return
        humanTouch = on
        prefs?.edit()?.putBoolean("humanTouch", on)?.apply()
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
