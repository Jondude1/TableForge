package com.jondude.tableforge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jondude.tableforge.AppViewModel
import com.jondude.tableforge.update.AutoUpdateCheck
import com.jondude.tableforge.update.UpdateBanner
import com.jondude.tableforge.R
import androidx.compose.ui.res.painterResource

enum class AppTab(val label: String, val icon: ImageVector) {
    FORGE("Forge", Icons.Outlined.AutoAwesome),
    STORY("Story", Icons.AutoMirrored.Outlined.MenuBook),
    IDEAS("Ideas", Icons.Outlined.Lightbulb),
    CONCEPTS("Concepts", Icons.Outlined.AccountTree),
    MAPS("Maps", Icons.Outlined.Map),
    MONSTERS("Monsters", Icons.Outlined.Pets),
    LOOT("Loot", Icons.Outlined.Diamond),
    TABLES("Tables", Icons.Outlined.Casino),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

@Composable
fun AppShell(vm: AppViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(AppTab.FORGE) }
    val snackbar = remember { SnackbarHostState() }
    val toast by vm.toast.collectAsStateWithLifecycle()

    LaunchedEffect(toast) {
        toast?.let {
            vm.toastShown()
            snackbar.showSnackbar(it)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { pad ->
            Row(Modifier.fillMaxSize().padding(pad).windowInsetsPadding(WindowInsets.safeDrawing)) {
                if (wide) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        header = {
                            Icon(
                                painterResource(R.drawable.ic_launcher_foreground),
                                "TableForge",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).size(56.dp),
                            )
                        },
                    ) {
                        AppTab.entries.forEach { t ->
                            if (t == AppTab.SETTINGS) Spacer(Modifier.weight(1f))
                            NavigationRailItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, t.label) },
                                label = { Text(t.label) },
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    AutoUpdateCheck()
                    UpdateBanner()
                    CampaignBar(vm, showSettings = !wide, onSettings = { tab = AppTab.SETTINGS })
                    if (!wide) {
                        val tabs = AppTab.entries.filter { it != AppTab.SETTINGS }
                        ScrollableTabRow(
                            selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0),
                            edgePadding = 8.dp,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            tabs.forEach { t ->
                                Tab(selected = tab == t, onClick = { tab = t }, text = { Text(t.label) }, icon = { Icon(t.icon, null) })
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(Modifier.fillMaxSize()) {
                        when (tab) {
                            AppTab.FORGE -> ForgeScreen(vm, wide)
                            AppTab.STORY -> StoryScreen(vm, wide)
                            AppTab.IDEAS -> IdeasScreen(vm, wide)
                            AppTab.CONCEPTS -> ConceptMapScreen(vm, wide)
                            AppTab.MAPS -> TileMapScreen(vm, wide)
                            AppTab.MONSTERS -> MonstersScreen(vm, wide)
                            AppTab.LOOT -> PartyScreen(vm, wide)
                            AppTab.TABLES -> TablesScreen(vm, wide)
                            AppTab.SETTINGS -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }
}

/** Slim bar showing the active campaign, with a menu to switch or create one. */
@Composable
private fun CampaignBar(vm: AppViewModel, showSettings: Boolean, onSettings: () -> Unit) {
    val campaign by vm.campaign.collectAsStateWithLifecycle()
    val campaigns by vm.campaigns.collectAsStateWithLifecycle()
    val ai by vm.ai.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                TextButton(onClick = { open = true }) {
                    Text(
                        campaign?.name ?: "…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(Icons.Outlined.ExpandMore, "Switch campaign", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    campaigns.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.name) },
                            trailingIcon = { if (c.id == campaign?.id) Icon(Icons.Outlined.Check, null) },
                            onClick = { vm.selectCampaign(c.id); open = false },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("New campaign…") }, onClick = { open = false; creating = true })
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                ai.provider.label.substringBefore(" ("),
                style = MaterialTheme.typography.labelMedium,
                color = if (ai.isConfigured) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (showSettings) {
                androidx.compose.material3.IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Settings") }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        var premise by remember { mutableStateOf("") }
        FormDialog(
            title = "New campaign",
            onDismiss = { creating = false },
            onSave = { vm.newCampaign(name, premise); creating = false },
            saveEnabled = name.isNotBlank(),
        ) {
            Field(name, { name = it }, "Name")
            Field(premise, { premise = it }, "Overall plot (optional)", singleLine = false, minLines = 3)
        }
    }
}
