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

    val controls: @Composable () -> Unit = {
        ForgeControls(
            type = type,
            onType = vm::setForgeType,
            count = count,
            onCount = { count = it },
            steer = steer,
            onSteer = { steer = it },
            hasCards = state.cards.isNotEmpty(),
            keptCount = state.kept.size,
            loading = state.loading,
            onGenerate = { vm.forgeGenerate(count, steer) },
            onClear = vm::forgeClear,
        )
    }

    val results: @Composable (Modifier) -> Unit = { mod ->
        Column(mod) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { ErrorBanner(it) }
            if (state.cards.isEmpty() && !state.loading) {
                EmptyState(
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
                            onReroll = { vm.forgeRegenerateOne(card, steer) },
                            onDiscard = { vm.forgeDiscard(card) },
                        )
                    }
                }
            }
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
        Button(onClick = onGenerate, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(if (hasCards) Icons.Outlined.Refresh else Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
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
                IconButton(onClick = onDiscard) { Icon(Icons.Outlined.Close, "Discard") }
                Spacer(Modifier.weight(1f))
                extraAction?.invoke()
                TextButton(onClick = onSave, enabled = !saved) {
                    Icon(if (saved) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (saved) "Saved" else "Save")
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
