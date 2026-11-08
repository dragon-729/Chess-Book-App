package com.chessbook.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
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
import com.chessbook.app.ui.Accent
import com.chessbook.app.ui.FeatureRow
import com.chessbook.app.ui.HeroBanner
import com.chessbook.app.ui.InfoCard
import com.chessbook.app.ui.Pill

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
        HeroBanner(
            title = "Premium",
            subtitle = "Stronger analysis, a bot at any Elo, and a real game review.",
            glyph = "♛",
            colors = listOf(Color(0xFF2A1A5E), Color(0xFF6A3FB8), Color(0xFFC8962E)),
        )

        InfoCard {
            Text("What you get", style = MaterialTheme.typography.titleSmall)
            FeatureRow("♛", "Stockfish Full", "Deeper, longer analysis with 3 lines at once.", Accent.Gold)
            FeatureRow("♞", "Custom Elo range", "Pick exactly how strong the bot is, from ${AppSettings.MIN_ELO} to ${AppSettings.MAX_ELO}.", Accent.Green)
            FeatureRow("♙", "Human touch", "The bot varies its moves and thinks like a real opponent.", Accent.Rose)
            FeatureRow("♗", "Move review with arrows", "Each move rated on the board, plus arrows for the better moves.", Accent.Blue)
        }

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
                val sel = selected == i
                Card(
                    onClick = { selected = i },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ),
                    border = BorderStroke(
                        if (sel) 2.dp else 1.dp,
                        if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(p.access, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (p.id == "lifetime") {
                            Pill("BEST VALUE", Accent.Gold)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text("₹${p.priceInr}", style = MaterialTheme.typography.titleLarge)
                    }
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
