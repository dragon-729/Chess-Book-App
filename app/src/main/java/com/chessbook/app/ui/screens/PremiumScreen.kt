package com.chessbook.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chessbook.app.BuildConfig
import com.chessbook.app.data.AppSettings
import com.chessbook.app.data.Payments
import com.chessbook.app.engine.EngineHolder
import com.chessbook.app.engine.EngineLimits
import com.chessbook.app.engine.EngineTier
import com.chessbook.app.engine.TierLimits
import com.chessbook.app.ui.InfoCard

/**
 * Plan list shown to the user. [id] must match the plan ids in the Supabase function.
 * The REAL price is decided by the server; the number here is only a label.
 */
private data class Plan(val id: String, val name: String, val priceInr: Int, val access: String)

private val PLANS = listOf(
    Plan("1h", "1 Hour", 2, "60 minutes"),
    Plan("6h", "6 Hours", 5, "6 hours"),
    Plan("12h", "12 Hours", 8, "12 hours"),
    Plan("24h", "24 Hours", 10, "24 hours"),
    Plan("lifetime", "Lifetime", 50, "Lifetime"),
)

private fun describe(l: EngineLimits) =
    "depth ${l.maxDepth} · ${l.moveTimeMs / 1000.0}s per move · ${l.threads} thread(s) · ${l.hashMb} MB hash · ${l.multiPv} line(s)"

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun PremiumScreen() {
    val ctx = LocalContext.current
    val activity = ctx.findActivity()
    val scope = rememberCoroutineScope()
    val engine = EngineHolder.get(ctx)
    var selected by remember { mutableIntStateOf(4) }
    val cores = Runtime.getRuntime().availableProcessors()
    val heap = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
    val lite = TierLimits.forTier(EngineTier.LITE, engine.usingNative, cores, heap)
    val full = TierLimits.forTier(EngineTier.FULL, engine.usingNative, cores, heap)

    // Re-check with the server every time this screen opens.
    LaunchedEffect(Unit) { Payments.refresh(ctx.applicationContext) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Premium", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        InfoCard {
            Text("Your access", style = MaterialTheme.typography.titleSmall)
            Text(
                when {
                    !AppSettings.premiumActive -> "Free: Stockfish Lite. It never expires."
                    Payments.lifetime -> "Premium: Lifetime. Stockfish Full is active."
                    Payments.expiresAt != null -> "Premium active until ${Payments.expiresAt}."
                    else -> "Stockfish Full is active (debug simulation)."
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        InfoCard {
            Text("What Full adds on this device", style = MaterialTheme.typography.titleSmall)
            Text("Lite — ${describe(lite)}", style = MaterialTheme.typography.bodyMedium)
            Text("Full — ${describe(full)}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Limits are capped by this phone's CPU cores and memory. Real speed depends on the device, " +
                    "battery and temperature.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        InfoCard {
            Text("Plans (INR)", style = MaterialTheme.typography.titleSmall)
            PLANS.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == i, onClick = { selected = i })
                    Text("${p.name} — ₹${p.priceInr}  (${p.access})", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Button(
                onClick = { activity?.let { Payments.buy(it, PLANS[selected].id, scope) } },
                enabled = Payments.configured && !Payments.busy && activity != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        !Payments.configured -> "Payments not set up in this build"
                        Payments.busy -> "Please wait…"
                        else -> "Buy ${PLANS[selected].name} — ₹${PLANS[selected].priceInr}"
                    },
                )
            }
            Payments.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "Payment is handled by Razorpay. Premium turns on only after our server verifies the payment; " +
                    "this screen cannot unlock anything by itself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (BuildConfig.DEBUG) {
            InfoCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Simulate Premium", style = MaterialTheme.typography.titleSmall)
                        Text("Debug builds only, for testing the Full engine.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = AppSettings.premiumActive, onCheckedChange = { AppSettings.debugSimulatePremium(it) })
                }
            }
        }
    }
}
