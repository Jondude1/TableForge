package com.jondude.tableforge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jondude.tableforge.AppViewModel
import com.jondude.tableforge.data.AiProvider
import com.jondude.tableforge.data.AiSettings

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val saved by vm.ai.collectAsStateWithLifecycle()
    val campaign by vm.campaign.collectAsStateWithLifecycle()
    val campaigns by vm.campaigns.collectAsStateWithLifecycle()
    val test by vm.aiTest.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(saved) }
    var showKey by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { draft = saved }
    val dirty = draft != saved
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Settings")
        Column(Modifier.padding(horizontal = 24.dp).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsCard("AI generation") {
                Text(
                    "The Forge, story suggestions, table generation and map building all use this. Offline mode uses built-in tables and needs no internet.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AiProvider.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = draft.provider == p,
                            onClick = { draft = draft.copy(provider = p) },
                            shape = SegmentedButtonDefaults.itemShape(i, AiProvider.entries.size),
                        ) { Text(p.label.substringBefore(" (").removePrefix("Local ")) }
                    }
                }
                when (draft.provider) {
                    AiProvider.CLAUDE -> {
                        OutlinedTextField(
                            value = draft.claudeKey,
                            onValueChange = { draft = draft.copy(claudeKey = it.trim()) },
                            label = { Text("Claude API key") },
                            placeholder = { Text("sk-ant-…") },
                            singleLine = true,
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showKey) "Hide key" else "Show key")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Field(draft.claudeModel, { draft = draft.copy(claudeModel = it) }, "Model", placeholder = AiSettings.DEFAULT_CLAUDE_MODEL)
                        Text(
                            "Get a key at console.anthropic.com → API Keys. It's stored only on this tablet and sent only to Anthropic.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AiProvider.OLLAMA -> {
                        Field(draft.ollamaUrl, { draft = draft.copy(ollamaUrl = it) }, "Server address", placeholder = "http://192.168.1.10:11434")
                        Field(draft.ollamaModel, { draft = draft.copy(ollamaModel = it) }, "Model", placeholder = "llama3.1:8b")
                        Text(
                            "Runs on your own computer. The tablet must be on the same network (or Tailscale), and Ollama must listen on the network (OLLAMA_HOST=0.0.0.0).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AiProvider.OFFLINE -> Unit
                }
                if (draft.provider == AiProvider.OLLAMA) {
                    Text("Creativity: ${"%.1f".format(draft.creativity)}", style = MaterialTheme.typography.bodyMedium)
                    Slider(draft.creativity, { draft = draft.copy(creativity = it) }, valueRange = 0.2f..1f)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { vm.saveAi(draft) }, enabled = dirty) { Text(if (dirty) "Save" else "Saved") }
                    if (draft.provider != AiProvider.OFFLINE) {
                        OutlinedButton(onClick = { vm.testAi(draft) }, enabled = draft.isConfigured) { Text("Test connection") }
                    }
                }
                test?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (it.startsWith("Failed")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                    )
                }
            }

            campaign?.let { c ->
                SettingsCard("Campaign") {
                    Text(c.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Edit the name, overall plot, setting and tone from the Story tab. Switch campaigns from the bar at the top.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { deleting = true }) { Text("Delete this campaign…", color = MaterialTheme.colorScheme.error) }
                }
            }

            SettingsCard("About") {
                Text("TableForge $version", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Session prep for Dungeon Masters. Visual style inspired by Jack Lockwood's Quest Journal Obsidian theme, the Rathgar Gold TTRPG theme, and Samantha Hersom's Pocket Squire concept. Fonts: Cinzel (Natanael Gama) and Crimson Text (Sebastian Kosch), both SIL Open Font License. Full credits in CREDITS.md on GitHub.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(1.dp).padding(bottom = 24.dp))
        }
    }

    if (deleting) campaign?.let { c ->
        ConfirmDialog(
            "Delete ${c.name}?",
            "Its ideas, outline, party, concept map and grid maps will be deleted. Shared dice tables are kept." +
                if (campaigns.size <= 1) " A fresh empty campaign will be created." else "",
            onConfirm = { vm.deleteCampaign(c) },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel(title)
            content()
        }
    }
}
