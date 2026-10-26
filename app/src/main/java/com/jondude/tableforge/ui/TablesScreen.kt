package com.jondude.tableforge.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jondude.tableforge.AppViewModel
import com.jondude.tableforge.RollRecord
import com.jondude.tableforge.data.DICE
import com.jondude.tableforge.data.RollTable
import com.jondude.tableforge.data.TableEntry
import com.jondude.tableforge.data.TableMath
import com.jondude.tableforge.ui.theme.CinzelFamily
import kotlin.math.cos
import kotlin.math.sin

/** Pointy-top hexagon: the silhouette of a d20, used for roll buttons (after Rathgar Gold's die-shaped buttons). */
val DieShape: Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = minOf(size.width, size.height) / 2f
    for (i in 0 until 6) {
        val a = Math.toRadians((60.0 * i) - 90.0)
        val x = cx + r * cos(a).toFloat()
        val y = cy + r * sin(a).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A table being created or edited. */
private class TableDraft(val original: RollTable?, campaignId: Long, entries: List<String>) {
    var name by mutableStateOf(original?.name ?: "")
    var sides by mutableStateOf(original?.dieSides ?: 12)
    var description by mutableStateOf(original?.description ?: "")
    var shared by mutableStateOf(original?.let { it.campaignId == null } ?: false)
    val campaignId = campaignId
    val entries = mutableStateListOf<String>().apply { addAll(entries) }

    fun toTable() = (original ?: RollTable(name = "", dieSides = 12)).copy(
        name = name.trim(),
        dieSides = sides,
        description = description.trim(),
        campaignId = if (shared) null else campaignId,
    )
}

@Composable
fun TablesScreen(vm: AppViewModel, wide: Boolean) {
    val tables by vm.tables.collectAsStateWithLifecycle()
    val rolls by vm.rolls.collectAsStateWithLifecycle()
    val busy by vm.tableBusy.collectAsStateWithLifecycle()
    val campaignId by vm.campaignId.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var draft by remember { mutableStateOf<TableDraft?>(null) }
    var deleting by remember { mutableStateOf<RollTable?>(null) }
    val selected = tables.firstOrNull { it.id == selectedId }

    LaunchedEffect(wide, tables) {
        if (wide && selectedId == null && tables.isNotEmpty()) selectedId = tables.first().id
    }

    val list: @Composable (Modifier) -> Unit = { mod ->
        Column(mod) {
            ScreenHeader("Tables", "Roll on your own d4–d100 tables") {
                Button(onClick = { draft = TableDraft(null, campaignId, List(12) { "" }); selectedId = null }) {
                    Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Table")
                }
            }
            DiceTray(onRoll = { sides -> vm.roll("d$sides", sides) })
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                item { SectionLabel("Your tables", Modifier.padding(start = 8.dp, bottom = 4.dp)) }
                items(tables, key = { it.id }) { t ->
                    TableRow(t, selected = t.id == selectedId && draft == null) { selectedId = t.id; draft = null }
                }
                if (tables.isEmpty()) {
                    item { Text("No tables yet. Make one — rumors, loot, weather, wild magic…", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                }
                if (rolls.isNotEmpty()) {
                    item {
                        Row(Modifier.padding(start = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("Roll log", Modifier.weight(1f))
                            TextButton(onClick = vm::clearRolls) { Text("Clear") }
                        }
                    }
                    items(rolls.take(12)) { RollLogRow(it) }
                }
            }
        }
    }

    val detail: @Composable (Modifier) -> Unit = { mod ->
        val d = draft
        when {
            d != null -> TableEditor(
                draft = d,
                busy = busy,
                onGenerate = { count ->
                    vm.generateTable(d.toTable(), count) { texts ->
                        d.entries.clear(); d.entries.addAll(texts)
                    }
                },
                onCancel = { draft = null },
                onSave = {
                    vm.saveTable(d.toTable(), d.entries.toList()) { id -> selectedId = id }
                    draft = null
                },
                showBack = !wide,
                modifier = mod,
            )
            selected != null -> TableDetail(
                vm = vm,
                table = selected,
                onEdit = { entries -> draft = TableDraft(selected, campaignId, entries.map { it.text }) },
                onDelete = { deleting = selected },
                onBack = if (wide) null else ({ selectedId = null }),
                modifier = mod,
            )
            else -> EmptyState(Icons.Outlined.Casino, "Pick a table", "Choose a table to roll on, or make a new one.", mod)
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            list(Modifier.width(360.dp).fillMaxHeight())
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.weight(1f).fillMaxHeight()) {
                detail(Modifier.fillMaxSize())
            }
        }
    } else {
        if (draft != null || selected != null) detail(Modifier.fillMaxSize()) else list(Modifier.fillMaxSize())
    }

    deleting?.let { t ->
        ConfirmDialog("Delete \"${t.name}\"?", "The table and all its entries will be removed.", onConfirm = {
            vm.deleteTable(t); selectedId = null
        }, onDismiss = { deleting = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiceTray(onRoll: (Int) -> RollRecord) {
    var last by remember { mutableStateOf<RollRecord?>(null) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Quick roll", Modifier.weight(1f))
                last?.let {
                    Text("d${it.sides} → ", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Text("${it.roll}", fontFamily = CinzelFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DICE.forEach { s -> DieButton("d$s", size = 44) { last = onRoll(s) } }
            }
        }
    }
}

@Composable
fun DieButton(label: String, size: Int, filled: Boolean = false, onClick: () -> Unit) {
    val gold = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(size.dp)
            .background(if (filled) gold else gold.copy(alpha = 0.10f), DieShape)
            .border(1.5.dp, gold, DieShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = CinzelFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (size / 3.6).sp,
            color = if (filled) MaterialTheme.colorScheme.onPrimary else gold,
        )
    }
}

@Composable
private fun TableRow(t: RollTable, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), DieShape), contentAlignment = Alignment.Center) {
                Text("${t.dieSides}", fontFamily = CinzelFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (t.description.isNotBlank()) {
                    Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (t.campaignId == null) Icon(Icons.Outlined.Public, "Shared with all campaigns", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RollLogRow(r: RollRecord) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Text(
            "${r.roll}",
            fontFamily = CinzelFamily, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(40.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(r.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            r.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun TableDetail(
    vm: AppViewModel,
    table: RollTable,
    onEdit: (List<TableEntry>) -> Unit,
    onDelete: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier,
) {
    val flow = remember(table.id) { vm.entries(table.id) }
    val entries by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    var result by remember(table.id) { mutableStateOf<RollRecord?>(null) }
    val spin = remember { Animatable(0f) }
    LaunchedEffect(result) {
        if (result != null) { spin.snapTo(0f); spin.animateTo(360f, tween(450)) }
    }

    LazyColumn(modifier, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(table.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "d${table.dieSides} · ${entries.size} entries" + if (table.campaignId == null) " · shared" else "",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onEdit(entries) }) { Icon(Icons.Outlined.Edit, "Edit table") }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete table") }
            }
            if (table.description.isNotBlank()) {
                Text(table.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.rotate(spin.value)) {
                    DieButton(result?.roll?.toString() ?: "d${table.dieSides}", size = 96, filled = result != null) {
                        result = vm.roll(table.name, table.dieSides, entries)
                    }
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    if (result == null) {
                        Text("Tap the die to roll", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (entries.isEmpty()) Text("This table has no entries yet — tap edit to add some.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        SectionLabel("Rolled ${result?.roll}")
                        Text(result?.text ?: "", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        items(entries, key = { it.id }) { e ->
            val hit = result?.roll?.let { it in e.minRoll..e.maxRoll } == true
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .background(if (hit) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    TableMath.rangeLabel(e, table.dieSides),
                    fontFamily = CinzelFamily, fontWeight = FontWeight.Bold,
                    color = if (hit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp),
                )
                Text(e.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TableEditor(
    draft: TableDraft,
    busy: Boolean,
    onGenerate: (Int) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    showBack: Boolean,
    modifier: Modifier,
) {
    val ranges = TableMath.ranges(draft.sides, draft.entries.size)
    val tooMany = draft.entries.size > draft.sides
    LazyColumn(modifier, contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showBack) IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                Text(if (draft.original == null) "New table" else "Edit table", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = onSave, enabled = draft.name.isNotBlank() && !busy) { Text("Save") }
            }
        }
        item { Field(draft.name, { draft.name = it }, "Table name", placeholder = "e.g. Things found in a goblin's pocket") }
        item { Field(draft.description, { draft.description = it }, "What it's for (helps generation)", singleLine = false) }
        item {
            SectionLabel("Die")
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DICE.forEach { s ->
                    DieButton("d$s", size = 48, filled = draft.sides == s) {
                        val onlyBlanks = draft.entries.all { it.isBlank() }
                        draft.sides = s
                        // Keep the row count matched to the die while the table is still empty.
                        if (onlyBlanks) {
                            draft.entries.clear(); repeat(TableMath.defaultCount(s)) { draft.entries.add("") }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Entries · ${draft.entries.size}", Modifier.weight(1f))
                OutlinedButton(onClick = { onGenerate(draft.entries.size.coerceIn(1, draft.sides).takeIf { draft.entries.isNotEmpty() } ?: TableMath.defaultCount(draft.sides)) }, enabled = !busy && draft.name.isNotBlank()) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Generate entries")
                }
            }
            if (draft.name.isBlank()) Text("Name the table first so generation knows what to write.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (tooMany) Text("A d${draft.sides} only has ${draft.sides} results — extra entries won't be rollable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        itemsIndexed(draft.entries) { i, text ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val r = ranges.getOrNull(i)
                Text(
                    r?.let { if (it.first == it.last) "${it.first}" else "${it.first}–${it.last}" } ?: "—",
                    fontFamily = CinzelFamily, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(56.dp).padding(end = 10.dp),
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { draft.entries[i] = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Entry ${i + 1}") },
                )
                IconButton(onClick = { draft.entries.removeAt(i) }) { Icon(Icons.Outlined.Close, "Remove entry") }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { draft.entries.add("") }, enabled = draft.entries.size < draft.sides) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add entry")
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.clickable { draft.shared = !draft.shared }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = draft.shared, onCheckedChange = { draft.shared = it })
                    Text("Use in all campaigns")
                }
            }
        }
    }
}
