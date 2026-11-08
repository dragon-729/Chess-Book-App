package com.chessbook.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chessbook.app.BuildConfig

/**
 * Forced update. The server says the oldest allowed versionCode; if this build is older,
 * [required] becomes true and the app shows a screen that blocks everything else.
 * If the check cannot be done (offline, server down) the app is NOT blocked.
 */
object AppUpdate {
    var required by mutableStateOf(false)
        private set
    var url by mutableStateOf("")
        private set
    var message by mutableStateOf("")
        private set

    val currentVersionCode: Int get() = BuildConfig.VERSION_CODE

    suspend fun check(ctx: Context) {
        val cfg = Payments.appConfig(ctx.applicationContext) ?: return
        val min = cfg.optInt("min_version_code", 0)
        url = cfg.optString("update_url", "")
        message = cfg.optString("message", "")
        required = min > BuildConfig.VERSION_CODE
    }
}
