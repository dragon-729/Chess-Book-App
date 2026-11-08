package com.chessbook.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.chessbook.app.data.AppSettings
import com.chessbook.app.ui.AppRoot
import com.chessbook.app.ui.ChessBookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(applicationContext)
        requestHighestRefreshRate()
        enableEdgeToEdge()
        setContent {
            ChessBookTheme { AppRoot() }
        }
    }

    /**
     * Asks Android to run this window at the screen's highest refresh rate (e.g. 120 Hz) at the
     * current resolution. This is a request: the system may still lower it (battery saver,
     * thermal limits, or a screen that only supports 60 Hz).
     */
    @Suppress("DEPRECATION")
    private fun requestHighestRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val display = windowManager.defaultDisplay ?: return
        val mode = display.mode
        val best = display.supportedModes
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        val params = window.attributes
        params.preferredDisplayModeId = best.modeId
        params.preferredRefreshRate = best.refreshRate
        window.attributes = params
    }
}
