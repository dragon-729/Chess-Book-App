package com.chessbook.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chessbook.app.data.AppUpdate

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** Shows [content] normally, or a screen that cannot be skipped when an update is required. */
@Composable
fun UpdateGate(content: @Composable () -> Unit) {
    if (!AppUpdate.required) {
        content()
        return
    }
    val ctx = LocalContext.current
    BackHandler { ctx.activity()?.finish() } // Back closes the app; it never skips the screen
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("♞", style = MaterialTheme.typography.displayLarge)
            Text("Update required", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                AppUpdate.message.ifBlank { "This version of Chess-Book is no longer supported. Please install the latest version to continue." },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                "Your version code: ${AppUpdate.currentVersionCode}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            val canOpen = AppUpdate.url.startsWith("https://")
            Button(
                onClick = {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppUpdate.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
                enabled = canOpen,
                modifier = Modifier.padding(top = 24.dp),
            ) { Text(if (canOpen) "Update now" else "Ask the developer for the new version") }
        }
    }
}
