package com.chessbook.app.ui.screens

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chessbook.app.BuildConfig
import com.chessbook.app.data.AppSettings
import com.chessbook.app.engine.EngineHolder
import com.chessbook.app.engine.EngineLimits
import com.chessbook.app.engine.EngineTier
import com.chessbook.app.engine.TierLimits
import com.chessbook.app.ui.InfoCard

/** Default plan list. The backend will own real prices; these mirror the planned launch pricing. */
private data class Plan(val name: String, val priceInr: Int, val access: String)

private val PLANS = listOf(
    Plan("1 Hour", 2, "60 minutes"),
    Plan("6 Hours", 5, "6 hours"),
    Plan("12 Hours", 8, "12 hours"),
    Plan("24 Hours", 10, "24 hours"),
    Plan("Lifetime", 50, "Lifetime"),
)

private fun describe(l: EngineLimits) =
    "depth ${l.maxDepth} · ${l.moveTimeMs / 1000.0}s per move · ${l.threads} thread(s) · ${l.hashMb} MB hash · ${l.multiPv} line(s)"

@Composable
fun PremiumScreen() {
    val ctx = LocalContext.current
    val engine = EngineHolder.get(ctx)
    var selected by remember { mutableIntStateOf(4) }
    val cores = Runtime.getRuntime().availableProcessors()
    val heap = (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
    val lite = TierLimits.forTier(EngineTier.LITE, engine.usingNative, cores, heap)
    val full = TierLimits.forTier(EngineTier.FULL, engine.usingNative, cores, heap)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Premium", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        InfoCard {
            Text("Your access", style = MaterialTheme.typography.titleSmall)
            Text(
                if (AppSettings.premiumActive) "Stockfish Full is active (debug simulation)."
                else "Free: Stockfish Lite. It never expires.",
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
            Text("Planned plans (INR)", style = MaterialTheme.typography.titleSmall)
            PLANS.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == i, onClick = { selected = i })
                    Text("${p.name} — ₹${p.priceInr}  (${p.access})", style = MaterialTheme.typography.bodyLarge)
                }
            }
            Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Purchase ₹${PLANS[selected].priceInr} — not available yet")
            }
            Text(
                "Online payment is not connected in this build. Premium can only be granted after a server " +
                    "verifies a Razorpay payment, so this screen cannot unlock anything by itself.",
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
