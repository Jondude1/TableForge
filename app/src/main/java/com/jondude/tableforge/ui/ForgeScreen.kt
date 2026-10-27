package com.jondude.tableforge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import com.jondude.tableforge.OwnIdea
import androidx.compose.runtime.remember
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Card as M3Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jondude.tableforge.AppViewModel
import com.jondude.tableforge.ai.Card
import com.jondude.tableforge.data.IdeaType
import com.jondude.tableforge.ui.theme.color

private val VIBES = listOf(
    "Darker", "Funnier", "Mysterious", "Tied to the main plot", "Low level (1-4)",
    "High level", "Urban", "Wilderness", "Morally gray", "Weird / fey",
)

@Composable
fun ForgeScreen(vm: AppViewModel, wide: Boolean) {
    val state by vm.forge.collectAsStateWithLifecycle()
    val type by vm.forgeType.collectAsStateWithLifecycle()
    var steer by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableIntStateOf(4) }
    val ownMode by vm.ownMode.collectAsStateWithLifecycle()
    val own by vm.own.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Card?>(null) }

    val controls: @Composable () -> Unit = {
        ForgeControls(
            ownMode = ownMode,
            onOwnMode = vm::setOwnMode,
            own = own,
            onOwn = vm::updateOwn,
            type = type,
            onType = vm::setForgeType,
            count = count,
            onCount = { count = it },
            steer = steer,
            onSteer = { steer = it },
            hasCards = state.cards.isNotEmpty(),
            keptCount = state.kept.size,
            loading = state.loading,
            onGenerate = { if (ownMode) vm.forgeFleshOut() else vm.forgeGenerate(count, steer) },
            onClear = vm::forgeClear,
        )
    }

    val results: @Composable (Modifier) -> Unit = { mod ->
        Column(mod) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { ErrorBanner(it) }
            if (state.cards.isEmpty() && !state.loading) {
                if (ownMode) EmptyState(
                    Icons.Outlined.Edit,
                    "Flesh out your ${type.singular}",
                    "Give it a name and whatever you already know. Claude keeps your details exactly and fills in the rest. Edit anything before you save it.",
                ) else EmptyState(
                    Icons.Outlined.AutoAwesome,
                    "Forge some ${type.label.lowercase()}",
                    "Pick a type, add what you're looking for, and generate. Pin the ones you like, then regenerate the rest until everything fits.",
                )
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(300.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.cards, key = { it.key }) { card ->
                        IdeaCard(
                            card = card,
                            type = type,
                            kept = card.key in state.kept,
                            saved = card.key in state.saved,
                            busy = state.loading,
                            onKeep = { vm.forgeToggleKeep(card) },
                            onSave = { vm.forgeSave(card) },
                            onReroll = { if (ownMode) vm.forgeFleshOut(card) else vm.forgeRegenerateOne(card, steer) },
                            onDiscard = { vm.forgeDiscard(card) },
                            onEdit = { editing = card },
                            saveLabel = if (ownMode && own.ideaId != null) "Update" else "Save",
                        )
                    }
                }
            }
        }
    }

    editing?.let { c ->
        var title by remember(c.key) { mutableStateOf(c.title) }
        var body by remember(c.key) { mutableStateOf(c.body) }
        FormDialog(
            title = "Edit before saving",
            onDismiss = { editing = null },
            onSave = { vm.forgeEditCard(c, title.trim(), body.trim()); editing = null },
            saveEnabled = title.isNotBlank(),
        ) {
            Field(title, { title = it }, "Title")
            Field(body, { body = it }, "Details", singleLine = false, minLines = 8)
            Text("Keep \"Label: text\" lines to get the tidy layout.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(340.dp).fillMaxHeight()) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) { controls() }
            }
            results(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(16.dp)) { controls() }
            results(Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForgeControls(
    ownMode: Boolean,
    onOwnMode: (Boolean) -> Unit,
    own: OwnIdea,
    onOwn: (OwnIdea) -> Unit,
    type: IdeaType,
    onType: (IdeaType) -> Unit,
    count: Int,
    onCount: (Int) -> Unit,
    steer: String,
    onSteer: (String) -> Unit,
    hasCards: Boolean,
    keptCount: Int,
    loading: Boolean,
    onGenerate: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("The Forge", style = MaterialTheme.typography.headlineSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !ownMode, onClick = { onOwnMode(false) }, shape = SegmentedButtonDefaults.itemShape(0, 2),
                icon = { Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(16.dp)) }) { Text("Brainstorm") }
            SegmentedButton(selected = ownMode, onClick = { onOwnMode(true) }, shape = SegmentedButtonDefaults.itemShape(1, 2),
                icon = { Icon(Icons.Outlined.Edit, null, Modifier.size(16.dp)) }) { Text("Flesh out mine") }
        }
        SectionLabel("What to make")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IdeaType.generatable.forEach { t ->
                FilterChip(
                    selected = t == type,
                    onClick = { onType(t) },
                    label = { Text(t.label) },
                    leadingIcon = { Box(Modifier.size(8.dp).background(t.color(), CircleShape)) },
                )
            }
        }
        if (!ownMode) {
            SectionLabel("How many")
            val counts = listOf(1, 3, 4, 6, 8)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                counts.forEachIndexed { i, n ->
                    SegmentedButton(
                        selected = count == n,
                        onClick = { onCount(n) },
                        shape = SegmentedButtonDefaults.itemShape(i, counts.size),
                    ) { Text("$n") }
                }
            }
            SectionLabel("What you're looking for")
            Field(
                steer, onSteer, "Details, constraints, vibe",
                singleLine = false, minLines = 3,
                placeholder = "e.g. a dwarf merchant who secretly works for the cult",
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                VIBES.forEach { v ->
                    val on = steer.contains(v, ignoreCase = true)
                    FilterChip(
                        selected = on,
                        onClick = {
                            onSteer(
                                if (on) steer.replace(v, "", ignoreCase = true).replace(Regex(",\\s*,"), ",").trim(' ', ',')
                                else listOf(steer.trim(), v).filter { it.isNotBlank() }.joinToString(", "),
                            )
                        },
                        label = { Text(v) },
                    )
                }
            }
        } else {
            if (own.ideaId != null) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f), shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Fleshing out a saved idea. Saving updates it.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onOwn(own.copy(ideaId = null)) }) { Text("Save as new") }
                    }
                }
            }
            Field(own.name, { onOwn(own.copy(name = it)) }, "Name", placeholder = if (type == IdeaType.NPC) "e.g. Marta Vey" else "Leave blank to have one invented")
            Field(own.notes, { onOwn(own.copy(notes = it)) }, "What you already know", singleLine = false, minLines = 4, placeholder = ownHint(type))
            Text("Everything you write here is kept exactly. Claude only fills in what's missing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionLabel("Versions to compare")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                (1..3).forEach { n ->
                    SegmentedButton(selected = own.versions == n, onClick = { onOwn(own.copy(versions = n)) }, shape = SegmentedButtonDefaults.itemShape(n - 1, 3)) { Text("$n") }
                }
            }
        }
        Button(
            onClick = onGenerate,
            enabled = !loading && (!ownMode || own.name.isNotBlank() || own.notes.isNotBlank()),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(if (hasCards) Icons.Outlined.Refresh else Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        ownMode && !hasCards -> "Flesh it out"
                        ownMode && keptCount > 0 -> "Try again (keep pinned)"
                        ownMode -> "Try again"
                        !hasCards -> "Generate"
                        keptCount > 0 -> "Regenerate unpinned"
                        else -> "Regenerate all"
                    },
                )
            }
        }
        if (hasCards) {
            TextButton(onClick = onClear, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Clear results") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IdeaCard(
    card: Card,
    type: IdeaType,
    kept: Boolean,
    saved: Boolean,
    busy: Boolean,
    onKeep: (() -> Unit)?,
    onSave: () -> Unit,
    onReroll: (() -> Unit)?,
    onDiscard: () -> Unit,
    extraAction: (@Composable () -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    saveLabel: String = "Save",
) {
    val accent = type.color()
    M3Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = when {
            kept -> BorderStroke(1.5.dp, accent)
            saved -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            else -> null
        },
        shape = RoundedCornerShape(14.dp),
        // Unsaved ideas get a dashed edge: they're still provisional.
        modifier = if (!kept && !saved) Modifier.dashedBorder(accent.copy(alpha = 0.45f), 14.dp) else Modifier,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeTag(type)
                Spacer(Modifier.weight(1f))
                if (kept) Text("PINNED", style = MaterialTheme.typography.labelSmall, color = accent)
            }
            Text(card.title, style = MaterialTheme.typography.titleLarge)
            LabeledBody(card.body)
            if (card.tags.isNotBlank()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    card.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach {
                        Pill("#$it", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onKeep != null) {
                    IconButton(onClick = onKeep) {
                        Icon(if (kept) Icons.Filled.PushPin else Icons.Outlined.PushPin, if (kept) "Unpin" else "Pin (keep when regenerating)", tint = if (kept) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (onReroll != null) {
                    IconButton(onClick = onReroll, enabled = !busy) { Icon(Icons.Outlined.Refresh, "Regenerate this one") }
                }
                if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit before saving") }
                IconButton(onClick = onDiscard) { Icon(Icons.Outlined.Close, "Discard") }
                Spacer(Modifier.weight(1f))
                extraAction?.invoke()
                TextButton(onClick = onSave, enabled = !saved) {
                    Icon(if (saved) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (saved) "Saved" else saveLabel)
                }
            }
        }
    }
}

@Composable
fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
    }
}

private fun ownHint(type: IdeaType) = when (type) {
    IdeaType.NPC -> "e.g. Half-orc, runs the Gilded Eel tavern. Gruff but fair. Secretly owes the thieves' guild."
    IdeaType.EVENT -> "e.g. The harvest festival is interrupted when the bell tower collapses during the opening speech."
    IdeaType.LOCATION -> "e.g. A lighthouse on a sea stack, reachable only at low tide. The keeper vanished a month ago."
    IdeaType.DUNGEON_ROOM -> "e.g. A flooded chapel with a drowned choir that still sings at midnight."
    IdeaType.MAGIC_ITEM -> "e.g. A compass that points to whatever the holder fears most. Rare, needs attunement."
    IdeaType.STORY_BEAT -> "e.g. The party learns the mayor they've been helping is the cult leader."
    IdeaType.NOTE -> ""
}
