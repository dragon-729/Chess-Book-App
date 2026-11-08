package com.chessbook.app.data

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chessbook.app.BuildConfig
import com.razorpay.Checkout
import com.razorpay.PaymentData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to Supabase: anonymous login + the "bright-api" Edge Function.
 * Only PUBLIC values live here (URL, publishable key). No secrets.
 */
private object Backend {
    private val base get() = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key get() = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences("chessbook_auth", Context.MODE_PRIVATE)

    fun hasSession(ctx: Context) = prefs(ctx).getString("refresh", null) != null

    private fun http(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 25_000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            c.disconnect()
        }
    }

    private fun save(ctx: Context, j: JSONObject): String {
        prefs(ctx).edit().putString("refresh", j.getString("refresh_token")).apply()
        return j.getString("access_token")
    }

    /** Returns a valid access token, signing in anonymously the first time. */
    private fun accessToken(ctx: Context): String {
        val hdr = mapOf("apikey" to key, "Content-Type" to "application/json")
        val refresh = prefs(ctx).getString("refresh", null)
        if (refresh != null) {
            val (code, txt) = http(
                "POST", "$base/auth/v1/token?grant_type=refresh_token", hdr,
                JSONObject().put("refresh_token", refresh).toString(),
            )
            if (code == 200) return save(ctx, JSONObject(txt))
        }
        val (code, txt) = http("POST", "$base/auth/v1/signup", hdr, "{}")
        if (code != 200) error("Login failed ($code). Turn on 'Allow anonymous sign-ins' in Supabase Auth settings.")
        return save(ctx, JSONObject(txt))
    }

    /** Calls the Edge Function. Blocking: run on Dispatchers.IO. */
    fun call(ctx: Context, body: JSONObject): JSONObject {
        val token = accessToken(ctx)
        val (code, txt) = http(
            "POST", "$base/functions/v1/bright-api",
            mapOf("apikey" to key, "Authorization" to "Bearer $token", "Content-Type" to "application/json"),
            body.toString(),
        )
        val j = runCatching { JSONObject(txt) }.getOrElse { JSONObject() }
        if (code !in 200..299) error(j.optString("error", "Server error ($code)"))
        return j
    }
}

/**
 * Razorpay checkout + premium status. The app NEVER decides a payment succeeded:
 * it sends the Razorpay result to the server, and only the server's answer turns Premium on.
 */
object Payments {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var lifetime by mutableStateOf(false)
        private set
    var expiresAt by mutableStateOf<String?>(null)
        private set

    val configured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
            BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank() &&
            BuildConfig.RAZORPAY_KEY_ID.isNotBlank()

    private fun applyStatus(j: JSONObject) {
        lifetime = j.optBoolean("lifetime", false)
        expiresAt = if (j.isNull("expires_at")) null else j.optString("expires_at")
        AppSettings.setPremiumFromServer(j.optBoolean("premium", false))
    }

    /** Re-checks Premium with the server (also recovers a payment whose confirmation got lost). */
    suspend fun refresh(ctx: Context) {
        if (!configured || !Backend.hasSession(ctx)) return
        runCatching {
            withContext(Dispatchers.IO) { Backend.call(ctx, JSONObject().put("action", "status")) }
        }.onSuccess { applyStatus(it) }
        // On failure (offline) the app simply stays on the free Lite tier.
    }

    /** Step 1-3: ask the server for an order, then open Razorpay Checkout. */
    fun buy(activity: Activity, planId: String, scope: CoroutineScope) {
        if (busy) return
        if (!configured) {
            message = "Payments are not set up in this build."
            return
        }
        busy = true
        message = null
        scope.launch {
            try {
                val order = withContext(Dispatchers.IO) {
                    Backend.call(activity, JSONObject().put("action", "create_order").put("plan", planId))
                }
                val checkout = Checkout()
                checkout.setKeyID(BuildConfig.RAZORPAY_KEY_ID)
                val options = JSONObject()
                    .put("name", "Chess-Book")
                    .put("description", "Premium")
                    .put("order_id", order.getString("order_id"))
                    .put("currency", "INR")
                    .put("amount", order.getInt("amount"))
                checkout.open(activity, options)
            } catch (e: Exception) {
                busy = false
                message = e.message ?: "Could not start the payment."
            }
        }
    }

    /** Called from MainActivity when Razorpay reports success. */
    fun onSuccess(ctx: Context, data: PaymentData?, scope: CoroutineScope) {
        val orderId = data?.orderId
        val paymentId = data?.paymentId
        val signature = data?.signature
        if (orderId == null || paymentId == null || signature == null) {
            busy = false
            message = "Payment finished but its details are missing. Open Premium again in a minute; it re-checks automatically."
            return
        }
        message = "Verifying payment…"
        scope.launch {
            try {
                val j = withContext(Dispatchers.IO) {
                    Backend.call(
                        ctx,
                        JSONObject().put("action", "verify_payment")
                            .put("order_id", orderId).put("payment_id", paymentId).put("signature", signature),
                    )
                }
                applyStatus(j)
                message = when {
                    j.optBoolean("premium", false) -> "Payment verified. Premium is active."
                    j.optBoolean("pending", false) ->
                        "Payment received. Razorpay is still confirming it; Premium turns on by itself within a minute. Reopen this screen to refresh."
                    else -> "Payment received but Premium is not active yet. Reopen this screen in a minute."
                }
            } catch (e: Exception) {
                message = "Could not confirm yet (${e.message}). If money was deducted, reopen the app: it re-checks and activates Premium."
            }
            busy = false
        }
    }

    /** Called from MainActivity when the payment fails or is cancelled. */
    fun onError(code: Int, response: String?) {
        busy = false
        message = "Payment not completed."
    }
}
