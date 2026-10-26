package com.jondude.tableforge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jondude.tableforge.AppViewModel
import com.jondude.tableforge.data.BeatStatus
import com.jondude.tableforge.data.Campaign
import com.jondude.tableforge.data.IdeaType
import com.jondude.tableforge.data.PlotBeat
import com.jondude.tableforge.ui.theme.color

@Composable
fun StoryScreen(vm: AppViewModel, wide: Boolean) {
    val campaign by vm.campaign.collectAsStateWithLifecycle()
    val beats by vm.beats.collectAsStateWithLifecycle()
    val suggestions by vm.story.collectAsStateWithLifecycle()
    val anchor by vm.gapAnchor.collectAsStateWithLifecycle()
    var steer by rememberSaveable { mutableStateOf("") }
    var editingBeat by remember { mutableStateOf<PlotBeat?>(null) }
    var editingPlot by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PlotBeat?>(null) }
    var pane by rememberSaveable { mutableIntStateOf(0) }

    val outline: @Composable (Modifier) -> Unit = { mod ->
        LazyColumn(mod, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
            item {
                ScreenHeader("Story", "The overall plot and the beats that get you there") {
                    OutlinedButton(onClick = { editingBeat = PlotBeat(campaignId = 0, position = 0.0, title = "", act = beats.lastOrNull()?.act ?: "Act 1") }) {
                        Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Beat")
                    }
                }
            }
            item { campaign?.let { PlotCard(it) { editingPlot = true } } }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Outline · ${beats.size} beats", Modifier.weight(1f))
                    TextButton(onClick = { vm.storyNextBeats(steer); pane = 1 }) {
                        Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Suggest next beats")
                    }
                }
            }
            if (beats.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No beats yet.", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Add the big moments you already know, then use \"Fill gap\" to have ideas generated for what goes between them.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                        )
                        Button(onClick = { vm.storyFillGap(null, null, steer); pane = 1 }) { Text("Suggest an opening") }
                    }
                }
            }
            itemsIndexed(beats, key = { _, b -> b.id }) { i, b ->
                Column {
                    val prev = beats.getOrNull(i - 1)
                    if (i == 0 || prev?.act != b.act) {
                        if (b.act.isNotBlank()) {
                            Text(b.act, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                        }
                    }
                    GapButton(
                        active = anchor?.let { it.beforeId == prev?.id && it.afterId == b.id } == true,
                        label = if (prev == null) "Fill gap before" else "Fill gap",
                    ) { vm.storyFillGap(prev, b, steer); pane = 1 }
                    BeatRow(
                        index = i + 1,
                        beat = b,
                        isFirst = i == 0,
                        isLast = i == beats.lastIndex,
                        onStatus = {
                            val next = BeatStatus.entries[(BeatStatus.from(b.status).ordinal + 1) % BeatStatus.entries.size]
                            vm.saveBeat(b.copy(status = next.name))
                        },
                        onEdit = { editingBeat = b },
                        onDelete = { deleting = b },
                        onMove = { up -> vm.moveBeat(b, up) },
                    )
                    if (i == beats.lastIndex) {
                        GapButton(active = anchor?.let { it.beforeId == b.id && it.afterId == null } == true, label = "What comes after?") {
                            vm.storyFillGap(b, null, steer); pane = 1
                        }
                    }
                }
            }
        }
    }

    val suggestionPane: @Composable (Modifier) -> Unit = { mod ->
        Column(mod.padding(horizontal = 20.dp)) {
            Text("Suggestions", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp))
            Text(
                when {
                    anchor == null -> "Next-beat ideas based on where the story is now."
                    anchor?.beforeId == null && anchor?.afterId == null -> "Ways the campaign could open."
                    else -> "Options for the gap you picked. Add one and it slots right in."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Field(steer, { steer = it }, "Steer the ideas", placeholder = "e.g. the rival should betray them", singleLine = false)
            if (suggestions.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            suggestions.error?.let { ErrorBanner(it) }
            if (suggestions.cards.isEmpty() && !suggestions.loading) {
                EmptyState(
                    Icons.AutoMirrored.Outlined.MenuBook,
                    "Need a nudge?",
                    "Tap \"Suggest next beats\" or a \"Fill gap\" marker in the outline.",
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                items(suggestions.cards, key = { it.key }) { card ->
                    IdeaCard(
                        card = card,
                        type = IdeaType.STORY_BEAT,
                        kept = false,
                        saved = card.key in suggestions.saved,
                        busy = suggestions.loading,
                        onKeep = null,
                        onSave = { vm.storySaveAsIdea(card) },
                        onReroll = null,
                        onDiscard = { vm.storyClearOne(card) },
                        extraAction = {
                            Button(onClick = { vm.storyAccept(card) }) {
                                Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add")
                            }
                        },
                    )
                }
                if (suggestions.cards.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val a = anchor
                                if (a == null) vm.storyNextBeats(steer)
                                else vm.storyFillGap(beats.firstOrNull { it.id == a.beforeId }, beats.firstOrNull { it.id == a.afterId }, steer)
                            }, enabled = !suggestions.loading) { Text("Regenerate") }
                            TextButton(onClick = vm::storyClear) { Text("Clear") }
                        }
                    }
                }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            outline(Modifier.weight(1.15f).fillMaxHeight())
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.weight(1f).fillMaxHeight()) {
                suggestionPane(Modifier.fillMaxSize())
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(12.dp)) {
                listOf("Outline", "Suggestions").forEachIndexed { i, l ->
                    SegmentedButton(selected = pane == i, onClick = { pane = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
                }
            }
            Box(Modifier.weight(1f)) {
                if (pane == 0) outline(Modifier.fillMaxSize()) else suggestionPane(Modifier.fillMaxSize())
            }
        }
    }

    editingBeat?.let { b -> BeatDialog(b, onDismiss = { editingBeat = null }, onSave = { vm.saveBeat(it); editingBeat = null }) }
    if (editingPlot) campaign?.let { c ->
        PlotDialog(c, onDismiss = { editingPlot = false }, onSave = { vm.updateCampaign(it); editingPlot = false })
    }
    deleting?.let { b ->
        ConfirmDialog("Delete beat?", "\"${b.title}\" will be removed from the outline.", onConfirm = { vm.deleteBeat(b) }, onDismiss = { deleting = null })
    }
}

@Composable
private fun PlotCard(c: Campaign, onEdit: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Overall plot", Modifier.weight(1f))
                Icon(Icons.Outlined.Edit, "Edit plot", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Text(
                c.premise.ifBlank { "Tap to write the big picture: who the villain is, what they want, and how it ends if the party does nothing." },
                style = MaterialTheme.typography.bodyLarge,
                color = if (c.premise.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (c.setting.isNotBlank() || c.tone.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (c.setting.isNotBlank()) Pill(c.setting.take(40), MaterialTheme.colorScheme.tertiary)
                    if (c.tone.isNotBlank()) Pill(c.tone.take(40), MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun GapButton(active: Boolean, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        HorizontalDivider(Modifier.width(24.dp), color = c)
        Icon(Icons.Outlined.AutoAwesome, null, tint = c, modifier = Modifier.padding(horizontal = 6.dp).size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c)
        HorizontalDivider(Modifier.weight(1f).padding(start = 8.dp), color = c.copy(alpha = 0.4f))
    }
}

@Composable
private fun BeatRow(
    index: Int,
    beat: PlotBeat,
    isFirst: Boolean,
    isLast: Boolean,
    onStatus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Boolean) -> Unit,
) {
    val status = BeatStatus.from(beat.status)
    val statusColor = when (status) {
        BeatStatus.PLANNED -> MaterialTheme.colorScheme.onSurfaceVariant
        BeatStatus.IN_PLAY -> MaterialTheme.colorScheme.primary
        BeatStatus.DONE -> MaterialTheme.colorScheme.tertiary
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
            Text(
                "$index",
                style = MaterialTheme.typography.titleLarge,
                color = IdeaType.STORY_BEAT.color().copy(alpha = 0.8f),
                modifier = Modifier.width(32.dp).padding(top = 2.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(beat.title, style = MaterialTheme.typography.titleMedium)
                if (beat.summary.isNotBlank()) {
                    Text(
                        beat.summary.lines().filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(Modifier.clickable(onClick = onStatus).padding(top = 4.dp)) { Pill(status.label, statusColor) }
            }
            Column {
                IconButton(onClick = { onMove(true) }, enabled = !isFirst, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.KeyboardArrowUp, "Move up") }
                IconButton(onClick = { onMove(false) }, enabled = !isLast, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.KeyboardArrowDown, "Move down") }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun BeatDialog(beat: PlotBeat, onDismiss: () -> Unit, onSave: (PlotBeat) -> Unit) {
    var act by remember { mutableStateOf(beat.act) }
    var title by remember { mutableStateOf(beat.title) }
    var summary by remember { mutableStateOf(beat.summary) }
    var status by remember { mutableStateOf(BeatStatus.from(beat.status)) }
    FormDialog(
        title = if (beat.id == 0L) "New beat" else "Edit beat",
        onDismiss = onDismiss,
        onSave = { onSave(beat.copy(act = act.trim(), title = title.trim(), summary = summary.trim(), status = status.name)) },
        saveEnabled = title.isNotBlank(),
    ) {
        Field(act, { act = it }, "Act / chapter", placeholder = "Act 1")
        Field(title, { title = it }, "Title")
        Field(summary, { summary = it }, "What happens", singleLine = false, minLines = 4)
        Dropdown("Status", BeatStatus.entries, status, { it.label }, { status = it })
    }
}

@Composable
private fun PlotDialog(c: Campaign, onDismiss: () -> Unit, onSave: (Campaign) -> Unit) {
    var name by remember { mutableStateOf(c.name) }
    var premise by remember { mutableStateOf(c.premise) }
    var setting by remember { mutableStateOf(c.setting) }
    var tone by remember { mutableStateOf(c.tone) }
    FormDialog(
        title = "Overall plot",
        onDismiss = onDismiss,
        onSave = { onSave(c.copy(name = name.trim(), premise = premise.trim(), setting = setting.trim(), tone = tone.trim())) },
        saveEnabled = name.isNotBlank(),
    ) {
        Field(name, { name = it }, "Campaign name")
        Field(premise, { premise = it }, "Overall plot", singleLine = false, minLines = 6, placeholder = "The villain, their plan, the stakes, how it might end")
        Field(setting, { setting = it }, "Setting", placeholder = "e.g. Forgotten Realms, Sword Coast")
        Field(tone, { tone = it }, "Tone", placeholder = "e.g. grim, political, swashbuckling")
        Text("Everything here is given to the generators so ideas fit your story.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
