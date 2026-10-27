package com.jondude.tableforge.ui

import com.jondude.tableforge.data.ideaType
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.jondude.tableforge.data.Idea
import com.jondude.tableforge.data.IdeaType
import com.jondude.tableforge.ui.theme.color

private sealed interface IdeaFilter {
    data object All : IdeaFilter
    data object Own : IdeaFilter
    data object Favorites : IdeaFilter
    data object Unused : IdeaFilter
    data class Type(val t: IdeaType) : IdeaFilter
}

@Composable
fun IdeasScreen(vm: AppViewModel, wide: Boolean) {
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf<IdeaFilter>(IdeaFilter.All) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Idea?>(null) }
    var deleting by remember { mutableStateOf<Idea?>(null) }

    val shown = ideas.filter { i ->
        when (val f = filter) {
            IdeaFilter.All -> true
            IdeaFilter.Own -> i.isOwn
            IdeaFilter.Favorites -> i.favorite
            IdeaFilter.Unused -> !i.used
            is IdeaFilter.Type -> i.ideaType == f.t
        } && (query.isBlank() || i.title.contains(query, true) || i.body.contains(query, true) || i.tags.contains(query, true))
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Ideas", "Everything you've saved from the Forge, plus your own") {
            Button(onClick = { editing = Idea(campaignId = 0, type = IdeaType.NOTE.name, title = "", body = "", isOwn = true) }) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Add my own")
            }
        }
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search ideas") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(if (wide) 0.4f else 1f),
            )
        }
        val filters = listOf(IdeaFilter.All, IdeaFilter.Own, IdeaFilter.Favorites, IdeaFilter.Unused) + IdeaType.entries.map { IdeaFilter.Type(it) }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filters) { f ->
                val label = when (f) {
                    IdeaFilter.All -> "All (${ideas.size})"
                    IdeaFilter.Own -> "My own"
                    IdeaFilter.Favorites -> "Favorites"
                    IdeaFilter.Unused -> "Not used yet"
                    is IdeaFilter.Type -> f.t.label
                }
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(label) })
            }
        }
        if (shown.isEmpty()) {
            EmptyState(
                Icons.Outlined.Lightbulb,
                if (ideas.isEmpty()) "No ideas yet" else "Nothing matches",
                if (ideas.isEmpty()) "Save ideas from the Forge or jot down your own. They feed back into the generators so new ideas fit with them." else "Try another filter or search.",
            )
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(280.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.id }) { idea ->
                    IdeaTile(idea, onOpen = { editing = idea }, onFavorite = { vm.saveIdea(idea.copy(favorite = !idea.favorite)) })
                }
            }
        }
    }

    editing?.let { i ->
        IdeaDialog(
            idea = i,
            onDismiss = { editing = null },
            onSave = { vm.saveIdea(it); editing = null },
            onDelete = if (i.id != 0L) ({ editing = null; deleting = i }) else null,
            onFleshOut = if (i.id != 0L && i.ideaType in IdeaType.generatable) ({ editing = null; vm.fleshOutIdea(i) }) else null,
            onToOutline = if (i.id != 0L) ({ vm.ideaToOutline(i); editing = null }) else null,
            onToMap = if (i.id != 0L) ({ vm.ideaToMap(i); editing = null }) else null,
            onToLoot = if (i.id != 0L && i.ideaType == IdeaType.MAGIC_ITEM) ({ vm.ideaToLoot(i); editing = null }) else null,
        )
    }
    deleting?.let { i ->
        ConfirmDialog("Delete idea?", "\"${i.title}\" will be gone for good.", onConfirm = { vm.deleteIdea(i) }, onDismiss = { deleting = null })
    }
}

@Composable
private fun IdeaTile(idea: Idea, onOpen: () -> Unit, onFavorite: () -> Unit) {
    val accent = idea.ideaType.color()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TypeTag(idea.ideaType)
                if (idea.isOwn) { Spacer(Modifier.width(6.dp)); Pill("Mine", MaterialTheme.colorScheme.tertiary) }
                if (idea.used) { Spacer(Modifier.width(6.dp)); Icon(Icons.Outlined.CheckCircle, "Used", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onFavorite) {
                    Icon(if (idea.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline, "Favorite", tint = if (idea.favorite) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(idea.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 12.dp))
            Text(
                idea.body.lines().filter { it.isNotBlank() }.joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}

@Composable
private fun IdeaDialog(
    idea: Idea,
    onDismiss: () -> Unit,
    onSave: (Idea) -> Unit,
    onDelete: (() -> Unit)?,
    onFleshOut: (() -> Unit)?,
    onToOutline: (() -> Unit)?,
    onToMap: (() -> Unit)?,
    onToLoot: (() -> Unit)?,
) {
    var type by remember { mutableStateOf(idea.ideaType) }
    var title by remember { mutableStateOf(idea.title) }
    var body by remember { mutableStateOf(idea.body) }
    var tags by remember { mutableStateOf(idea.tags) }
    var used by remember { mutableStateOf(idea.used) }
    FormDialog(
        title = if (idea.id == 0L) "My own idea" else "Edit idea",
        onDismiss = onDismiss,
        onSave = { onSave(idea.copy(type = type.name, title = title.trim(), body = body.trim(), tags = tags.trim(), used = used)) },
        saveEnabled = title.isNotBlank(),
        extraAction = if (onDelete != null) {
            { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
        } else null,
    ) {
        Dropdown("Type", IdeaType.entries, type, { it.singular.replaceFirstChar { c -> c.uppercase() } }, { type = it })
        Field(title, { title = it }, "Title")
        Field(body, { body = it }, "Details", singleLine = false, minLines = 6, placeholder = "Use \"Label: text\" lines if you like, e.g.\nWants: revenge")
        Field(tags, { tags = it }, "Tags (comma separated)")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { used = !used }) {
            Checkbox(checked = used, onCheckedChange = { used = it })
            Text("Already used in a session")
        }
        if (onFleshOut != null) {
            OutlinedButton(onClick = onFleshOut, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Flesh out in the Forge")
            }
        }
        if (onToOutline != null || onToMap != null || onToLoot != null) {
            SectionLabel("Send to")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                onToOutline?.let { TextButton(onClick = it) { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Outline") } }
                onToMap?.let { TextButton(onClick = it) { Icon(Icons.Outlined.AccountTree, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Map") } }
                onToLoot?.let { TextButton(onClick = it) { Icon(Icons.Outlined.Diamond, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Loot") } }
            }
        }
    }
}
