package com.jondude.tableforge.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import java.io.File

/** Shared update state so the banner and Settings show the same thing. */
object UpdateState {
    var available by mutableStateOf<Release?>(null)
    var dismissedBuild by mutableStateOf(0)
    var progress by mutableStateOf<Float?>(null)
    var error by mutableStateOf<String?>(null)
    var downloaded by mutableStateOf<File?>(null)
    var lastCheck by mutableStateOf<String?>(null)
}

/** Checks quietly whenever the app comes to the foreground, at most every 30 minutes. */
@Composable
fun AutoUpdateCheck() {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        var last = 0L
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (System.currentTimeMillis() - last > 30 * 60_000) {
                last = System.currentTimeMillis()
                runCatching { Updater.check() }.onSuccess { UpdateState.available = it }
            }
        }
    }
}

/** Slim banner at the top of the app when a newer build exists. */
@Composable
fun UpdateBanner() {
    val r = UpdateState.available ?: return
    if (UpdateState.dismissedBuild == r.build && UpdateState.progress == null) return
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SystemUpdate, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Update available: build ${r.build}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (UpdateState.progress == null) TextButton(onClick = { UpdateState.dismissedBuild = r.build }) { Text("Later") }
                UpdateButton(r)
            }
            UpdateProgress()
        }
    }
}

@Composable
fun UpdateProgress() {
    UpdateState.progress?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) }
    UpdateState.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
}

/** Downloads (once) and opens the installer; asks for the install permission first if needed. */
@Composable
fun UpdateButton(r: Release) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy = UpdateState.progress != null && UpdateState.downloaded == null
    Button(enabled = !busy, onClick = {
        if (!Updater.canInstall(context)) {
            UpdateState.error = "Allow TableForge to install updates, then tap Update again."
            Updater.openInstallPermission(context)
            return@Button
        }
        val ready = UpdateState.downloaded
        if (ready != null && ready.exists()) { Updater.install(context, ready); return@Button }
        UpdateState.error = null
        UpdateState.progress = 0f
        scope.launch {
            runCatching { Updater.download(context, r) { p -> UpdateState.progress = p } }
                .onSuccess { UpdateState.downloaded = it; UpdateState.progress = null; Updater.install(context, it) }
                .onFailure { UpdateState.progress = null; UpdateState.error = it.message ?: "Download failed." }
        }
    }) {
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Update")
    }
}

/** Card for the Settings screen: current version, manual check, update. */
@Composable
fun UpdateSettings() {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Installed: ${Updater.currentName} (build ${Updater.currentBuild})", style = MaterialTheme.typography.bodyLarge)
        val r = UpdateState.available
        if (r != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Build ${r.build} is ready.", modifier = Modifier.weight(1f))
                UpdateButton(r)
            }
            UpdateProgress()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !checking, onClick = {
                    checking = true
                    scope.launch {
                        runCatching { Updater.check() }
                            .onSuccess { UpdateState.available = it; UpdateState.lastCheck = if (it == null) "You're on the latest version." else null }
                            .onFailure { UpdateState.lastCheck = it.message ?: "Couldn't check for updates." }
                        checking = false
                    }
                }) { Text(if (checking) "Checking…" else "Check for updates") }
                UpdateState.lastCheck?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Text(
            "The app checks automatically when you open it. Updates install over the top and keep your data.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
