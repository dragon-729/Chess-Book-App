package com.chessbook.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.chessbook.app.data.AppSettings
import com.chessbook.app.data.AppUpdate
import com.chessbook.app.data.Payments
import com.chessbook.app.ui.AppRoot
import com.chessbook.app.ui.ChessBookTheme
import com.chessbook.app.ui.UpdateGate
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), PaymentResultWithDataListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(applicationContext)
        requestHighestRefreshRate()
        enableEdgeToEdge()
        if (Payments.configured) Checkout.preload(applicationContext)
        // Ask the server whether Premium is active (never trusted from local storage).
        lifecycleScope.launch { Payments.refresh(applicationContext) }
        setContent {
            ChessBookTheme { UpdateGate { AppRoot() } }
        }
    }

    override fun onResume() {
        super.onResume()
        // Every time the app opens or comes back to the front: is this version still allowed?
        lifecycleScope.launch { AppUpdate.check(applicationContext) }
    }

    // ---- Razorpay results. The app only forwards them to the server. ----

    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        Payments.onSuccess(applicationContext, paymentData, lifecycleScope)
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        Payments.onError(code, response)
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
