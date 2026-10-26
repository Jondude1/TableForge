package com.jondude.tableforge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jondude.tableforge.AppViewModel
import com.jondude.tableforge.data.PartyItem
import com.jondude.tableforge.data.Player
import com.jondude.tableforge.data.RARITIES
import com.jondude.tableforge.ui.theme.rarityColor

@Composable
fun PartyScreen(vm: AppViewModel, wide: Boolean) {
    val players by vm.players.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    var editingItem by remember { mutableStateOf<PartyItem?>(null) }
    var editingPlayer by remember { mutableStateOf<Player?>(null) }
    var deletingPlayer by remember { mutableStateOf<Player?>(null) }
    var deletingItem by remember { mutableStateOf<PartyItem?>(null) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Party loot", "${items.size} magic items across ${players.size} characters") {
            OutlinedButton(onClick = { editingPlayer = Player(campaignId = 0, playerName = "", characterName = "") }) {
                Icon(Icons.Outlined.PersonAdd, null); Spacer(Modifier.width(6.dp)); Text("Player")
            }
            Button(onClick = { editingItem = PartyItem(campaignId = 0, name = "") }) {
                Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Item")
            }
        }
        if (players.isEmpty() && items.isEmpty()) {
            EmptyState(
                Icons.Outlined.Diamond,
                "Who's carrying what?",
                "Add each player and their character, then log magic items and hand them out. Saved magic items from the Ideas tab can be sent here too.",
            ) {
                Button(onClick = { editingPlayer = Player(campaignId = 0, playerName = "", characterName = "") }) { Text("Add the first player") }
            }
            return@Column
        }
        val stash = items.filter { it.holderId == null || players.none { p -> p.id == it.holderId } }
        LazyRow(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(players, key = { it.id }) { p ->
                val mine = items.filter { it.holderId == p.id }
                HolderColumn(
                    title = p.characterName.ifBlank { p.playerName },
                    subtitle = listOf(p.characterClass, if (p.playerName.isNotBlank()) "played by ${p.playerName}" else "").filter { it.isNotBlank() }.joinToString(" · "),
                    attuned = mine.count { it.attuned },
                    items = mine,
                    width = if (wide) 300 else 280,
                    onEditHolder = { editingPlayer = p },
                    onAdd = { editingItem = PartyItem(campaignId = 0, name = "", holderId = p.id) },
                    onItem = { editingItem = it },
                )
            }
            item(key = "stash") {
                HolderColumn(
                    title = "Party stash",
                    subtitle = "Unclaimed or shared",
                    attuned = null,
                    items = stash,
                    width = if (wide) 300 else 280,
                    onEditHolder = null,
                    onAdd = { editingItem = PartyItem(campaignId = 0, name = "") },
                    onItem = { editingItem = it },
                )
            }
        }
    }

    editingItem?.let { item ->
        ItemDialog(
            item, players,
            onDismiss = { editingItem = null },
            onSave = { vm.saveItem(it); editingItem = null },
            onDelete = if (item.id != 0L) ({ editingItem = null; deletingItem = item }) else null,
        )
    }
    editingPlayer?.let { p ->
        PlayerDialog(
            p,
            onDismiss = { editingPlayer = null },
            onSave = { vm.savePlayer(it); editingPlayer = null },
            onDelete = if (p.id != 0L) ({ editingPlayer = null; deletingPlayer = p }) else null,
        )
    }
    deletingPlayer?.let { p ->
        ConfirmDialog("Remove ${p.characterName.ifBlank { p.playerName }}?", "Their items move to the party stash.", "Remove", { vm.deletePlayer(p) }, { deletingPlayer = null })
    }
    deletingItem?.let { i ->
        ConfirmDialog("Delete ${i.name}?", "It will be removed from the party's items.", onConfirm = { vm.deleteItem(i) }, onDismiss = { deletingItem = null })
    }
}

@Composable
private fun HolderColumn(
    title: String,
    subtitle: String,
    attuned: Int?,
    items: List<PartyItem>,
    width: Int,
    onEditHolder: (() -> Unit)?,
    onAdd: () -> Unit,
    onItem: (PartyItem) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.width(width.dp).fillMaxHeight(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (onEditHolder == null) Icons.Outlined.Inventory2 else Icons.Outlined.Diamond, null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onEditHolder != null) IconButton(onClick = onEditHolder) { Icon(Icons.Outlined.Edit, "Edit player", Modifier.size(18.dp)) }
            }
            if (attuned != null) {
                Text(
                    "Attuned $attuned / 3",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (attuned > 3) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 28.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { ItemCard(it) { onItem(it) } }
                item {
                    TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add item")
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(item: PartyItem, onClick: () -> Unit) {
    val rc = rarityColor(item.rarity)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, color = rc)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(item.rarity, rc)
                if (item.requiresAttunement) {
                    Pill(if (item.attuned) "Attuned" else "Needs attunement", if (item.attuned) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (item.charges.isNotBlank()) Pill("⚡ ${item.charges}", MaterialTheme.colorScheme.primary)
            }
            if (item.notes.isNotBlank()) {
                Text(
                    item.notes.lines().filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.acquiredAt.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Link, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(item.acquiredAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ItemDialog(item: PartyItem, players: List<Player>, onDismiss: () -> Unit, onSave: (PartyItem) -> Unit, onDelete: (() -> Unit)?) {
    var name by remember { mutableStateOf(item.name) }
    var rarity by remember { mutableStateOf(item.rarity) }
    var holder by remember { mutableStateOf(players.firstOrNull { it.id == item.holderId }) }
    var needsAttune by remember { mutableStateOf(item.requiresAttunement) }
    var attuned by remember { mutableStateOf(item.attuned) }
    var charges by remember { mutableStateOf(item.charges) }
    var acquired by remember { mutableStateOf(item.acquiredAt) }
    var notes by remember { mutableStateOf(item.notes) }
    val holders: List<Player?> = listOf(null) + players
    FormDialog(
        title = if (item.id == 0L) "New magic item" else "Edit item",
        onDismiss = onDismiss,
        onSave = {
            onSave(
                item.copy(
                    name = name.trim(), rarity = rarity, holderId = holder?.id,
                    requiresAttunement = needsAttune, attuned = needsAttune && attuned && holder != null,
                    charges = charges.trim(), acquiredAt = acquired.trim(), notes = notes.trim(),
                ),
            )
        },
        saveEnabled = name.isNotBlank(),
        extraAction = if (onDelete != null) {
            { TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) } }
        } else null,
    ) {
        Field(name, { name = it }, "Item name")
        Dropdown("Held by", holders, holder, { it?.let { p -> p.characterName.ifBlank { p.playerName } } ?: "Party stash" }, { holder = it })
        Dropdown("Rarity", RARITIES, rarity, { it }, { rarity = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = needsAttune, onCheckedChange = { needsAttune = it })
            Text("Requires attunement", Modifier.weight(1f))
            if (needsAttune && holder != null) {
                Checkbox(checked = attuned, onCheckedChange = { attuned = it })
                Text("Attuned")
            }
        }
        Field(charges, { charges = it }, "Charges / uses", placeholder = "e.g. 3/7, regains 1d6+1 at dawn")
        Field(acquired, { acquired = it }, "Where it came from", placeholder = "e.g. Session 4, the lich's vault")
        Field(notes, { notes = it }, "Properties & notes", singleLine = false, minLines = 3)
    }
}

@Composable
private fun PlayerDialog(p: Player, onDismiss: () -> Unit, onSave: (Player) -> Unit, onDelete: (() -> Unit)?) {
    var player by remember { mutableStateOf(p.playerName) }
    var character by remember { mutableStateOf(p.characterName) }
    var cls by remember { mutableStateOf(p.characterClass) }
    FormDialog(
        title = if (p.id == 0L) "Add player" else "Edit player",
        onDismiss = onDismiss,
        onSave = { onSave(p.copy(playerName = player.trim(), characterName = character.trim(), characterClass = cls.trim())) },
        saveEnabled = player.isNotBlank() || character.isNotBlank(),
        extraAction = if (onDelete != null) {
            { TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) } }
        } else null,
    ) {
        Field(character, { character = it }, "Character name")
        Field(player, { player = it }, "Player name")
        Field(cls, { cls = it }, "Race & class", placeholder = "e.g. Tiefling Warlock 5")
    }
}
