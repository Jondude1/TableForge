package com.jondude.tableforge.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jondude.tableforge.AppViewModel
import com.jondude.tableforge.ai.ImagePart
import com.jondude.tableforge.ai.MonsterForge
import com.jondude.tableforge.ai.NamedText
import com.jondude.tableforge.ai.Seg
import com.jondude.tableforge.ai.StatBlock
import com.jondude.tableforge.ai.rollDice
import com.jondude.tableforge.ai.rollable
import com.jondude.tableforge.data.AiProvider
import com.jondude.tableforge.data.Monster
import com.jondude.tableforge.ui.theme.CinzelFamily
import com.jondude.tableforge.ui.theme.CrimsonFamily
import kotlinx.coroutines.launch
import java.io.File

// Classic 5e stat block colors: parchment, deep red headings and rules, amber borders.
private val Parch = Color(0xFFF7ECD5)
private val SbInk = Color(0xFF1F140C)
private val SbRed = Color(0xFF7A200D)
private val SbRule = Color(0xFF9C2B1B)
private val SbAmber = Color(0xFFE69A28)

private enum class AddMode { MENU, PHOTO, TEXT, MAKE }

@Composable
fun MonstersScreen(vm: AppViewModel, wide: Boolean) {
    val monsters by vm.monsters.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf<AddMode?>(null) }
    var editing by remember { mutableStateOf<Pair<Monster?, StatBlock>?>(null) }
    var deleting by remember { mutableStateOf<Monster?>(null) }
    val shown = monsters.filter { query.isBlank() || it.name.contains(query, true) || it.data.contains(query, true) }
    val selected = monsters.firstOrNull { it.id == selectedId } ?: if (wide) shown.firstOrNull() else null

    val list: @Composable (Modifier) -> Unit = { mod ->
        LazyColumn(mod, contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                ScreenHeader("Monsters", "Stat blocks — scan a book page, paste, or create") {
                    Button(onClick = { adding = AddMode.MENU }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Monster") }
                }
            }
            if (monsters.isNotEmpty()) item {
                OutlinedTextField(
                    query, { query = it }, placeholder = { Text("Search monsters") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            items(shown, key = { it.id }) { m ->
                val b = remember(m.data) { StatBlock.fromJson(m.data) }
                val on = wide && m.id == selected?.id
                Surface(
                    color = if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = if (on) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable { selectedId = m.id },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Pets, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(b.meta.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (m.cr.isNotBlank()) Pill("CR ${m.cr.substringBefore(' ')}", MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (monsters.isEmpty()) item {
                EmptyState(
                    Icons.Outlined.Pets, "No monsters yet",
                    "Snap a photo of a stat block from any book and it becomes a tappable stat block here — roll attacks and damage, and track hit points in combat.",
                    Modifier.height(420.dp),
                ) { Button(onClick = { adding = AddMode.PHOTO }) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Scan a stat block") } }
            }
        }
    }

    val detail: @Composable (Modifier) -> Unit = { mod ->
        val m = selected
        if (m == null) EmptyState(Icons.Outlined.Pets, "Pick a monster", "Choose a stat block, or add a new one.", mod)
        else MonsterDetail(
            vm, m, mod,
            onBack = if (wide) null else ({ selectedId = null }),
            onEdit = { editing = m to StatBlock.fromJson(m.data) },
            onDelete = { deleting = m },
            onCopy = {
                val b = StatBlock.fromJson(m.data).let { it.copy(name = "${it.name} (copy)") }
                vm.saveMonster(null, b) { selectedId = it }
            },
        )
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(380.dp).fillMaxHeight()) { list(Modifier.fillMaxSize()) }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            detail(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        if (selectedId != null && selected != null) detail(Modifier.fillMaxSize()) else list(Modifier.fillMaxSize())
    }

    adding?.let { mode ->
        AddMonsterDialog(
            vm, mode, setMode = { adding = it },
            onSave = { b -> vm.saveMonster(null, b) { selectedId = it; vm.toast("${b.name} added") }; adding = null },
            onBlank = { adding = null; editing = null to StatBlock(name = "") },
        )
    }
    editing?.let { (m, b) ->
        MonsterEditor(b, isNew = m == null, onDismiss = { editing = null }, onSave = { nb -> vm.saveMonster(m, nb) { selectedId = it }; editing = null })
    }
    deleting?.let { m ->
        ConfirmDialog("Delete ${m.name}?", "This stat block will be removed.", onConfirm = { vm.deleteMonster(m); selectedId = null }, onDismiss = { deleting = null })
    }
}

// ---------- Adding ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddMonsterDialog(vm: AppViewModel, mode: AddMode, setMode: (AddMode?) -> Unit, onSave: (StatBlock) -> Unit, onBlank: () -> Unit) {
    val ai by vm.ai.collectAsStateWithLifecycle()
    val req by vm.monsterRequest.collectAsStateWithLifecycle()
    val ready = ai.provider != AiProvider.OFFLINE && ai.isConfigured
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val images = remember { mutableStateListOf<ImagePart>() }
    var text by remember { mutableStateOf("") }
    var idea by remember { mutableStateOf("") }
    var imgError by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val close = { vm.clearMonsterRequest(); setMode(null) }

    LaunchedEffect(Unit) { vm.clearMonsterRequest() }

    fun addUris(uris: List<Uri>) = scope.launch {
        imgError = null
        uris.take(4 - images.size).forEach { u ->
            runCatching { MonsterForge.prepareImage(context, u) }.onSuccess { images += it }.onFailure { imgError = it.message }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { addUris(it) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { addUris(listOf(it)) } }
    fun takePhoto() {
        val dir = File(context.cacheDir, "scans").apply { mkdirs() }
        val f = File(dir, "scan-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", f)
        cameraUri = uri
        camera.launch(uri)
    }

    val result = req.result
    if (result != null) {
        AlertDialog(
            onDismissRequest = close,
            title = { Text("Check it over") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Compare with the original. You can fix anything after saving with Edit.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatBlockView(result, onRoll = null)
                }
            },
            confirmButton = { Button(onClick = { vm.clearMonsterRequest(); onSave(result) }) { Text("Save monster") } },
            dismissButton = { TextButton(onClick = { vm.clearMonsterRequest() }) { Text("Back") } },
        )
        return
    }

    if (mode == AddMode.MENU) {
        AlertDialog(
            onDismissRequest = close,
            title = { Text("Add a monster") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddOption(Icons.Outlined.PhotoCamera, "Scan a stat block", "Take a photo of a book page — Claude reads it", ready) { setMode(AddMode.PHOTO) }
                    AddOption(Icons.Outlined.ContentPaste, "Paste text", "From a PDF, D&D Beyond, or a website", ready) { setMode(AddMode.TEXT) }
                    AddOption(Icons.Outlined.AutoAwesome, "Create with Claude", "Describe it and get a balanced stat block", ready) { setMode(AddMode.MAKE) }
                    AddOption(Icons.Outlined.EditNote, "Start blank", "Type it in yourself", true, onBlank)
                    if (!ready) Text("Scanning, pasting and creating need a Claude API key (Settings). Start blank works offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = close) { Text("Cancel") } },
        )
        return
    }

    val can = when (mode) {
        AddMode.PHOTO -> images.isNotEmpty()
        AddMode.TEXT -> text.trim().length > 20
        else -> idea.trim().length > 2
    }
    AlertDialog(
        onDismissRequest = close,
        title = { Text(when (mode) { AddMode.PHOTO -> "Scan a stat block"; AddMode.TEXT -> "Paste a stat block"; else -> "Create a monster" }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (mode) {
                    AddMode.PHOTO -> {
                        Text(
                            "Lay the page flat in good light and fill the frame with the stat block. If it runs across two columns or pages, add a photo of each part (up to 4).",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            images.forEachIndexed { i, im ->
                                val bmp = remember(im) { Base64.decode(im.base64, Base64.NO_WRAP).let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap() }
                                Box {
                                    if (bmp != null) Image(bmp, "Photo ${i + 1}", contentScale = ContentScale.Crop, modifier = Modifier.size(84.dp, 112.dp).clip(RoundedCornerShape(10.dp)))
                                    IconButton(onClick = { images.removeAt(i) }, modifier = Modifier.align(Alignment.TopEnd).size(30.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(50))) {
                                        Icon(Icons.Outlined.Close, "Remove photo", Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                        if (images.size < 4) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = ::takePhoto) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text(if (images.isEmpty()) "Take photo" else "Add part") }
                            OutlinedButton(onClick = { gallery.launch("image/*") }) { Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Choose") }
                        }
                        imgError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    AddMode.TEXT -> Field(text, { text = it }, "Stat block text", singleLine = false, minLines = 8, placeholder = "Goblin\nSmall humanoid (goblinoid), neutral evil\nArmor Class 15 …")
                    else -> Field(idea, { idea = it }, "Describe the monster", singleLine = false, minLines = 4, placeholder = "e.g. a CR 5 drowned bell-ringer ghost that haunts the harbor")
                }
                if (req.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                req.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = can && !req.loading, onClick = {
                when (mode) {
                    AddMode.PHOTO -> vm.scanMonster(images.toList())
                    AddMode.TEXT -> vm.pasteMonster(text)
                    else -> vm.createMonster(idea)
                }
            }) {
                if (req.loading) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(when { req.loading && mode == AddMode.MAKE -> "Creating…"; req.loading -> "Reading…"; mode == AddMode.MAKE -> "Create"; else -> "Read stat block" })
            }
        },
        dismissButton = { TextButton(onClick = { vm.clearMonsterRequest(); setMode(AddMode.MENU) }) { Text("Back") } },
    )
}

@Composable
private fun AddOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.5f), modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------- Viewing ----------

@Composable
private fun MonsterDetail(vm: AppViewModel, m: Monster, modifier: Modifier, onBack: (() -> Unit)?, onEdit: () -> Unit, onDelete: () -> Unit, onCopy: () -> Unit) {
    val b = remember(m.data) { StatBlock.fromJson(m.data) }
    val max = b.maxHp
    val hp = m.hpNow ?: max
    var amount by remember(m.id) { mutableStateOf("") }
    var last by remember(m.id) { mutableStateOf<String?>(null) }
    val onRoll: (String, String) -> Unit = { label, result -> last = "$label: $result"; vm.toast("$label: $result") }

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp).widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Duplicate") }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete") }
        }

        if (max != null && hp != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Hit points", Modifier.weight(1f))
                        if (hp != max) TextButton(onClick = { vm.setMonsterHp(m, max) }) { Text("Reset") }
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$hp", fontFamily = CinzelFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp,
                            color = when { hp == 0 -> MaterialTheme.colorScheme.error; hp <= max / 2 -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurface },
                        )
                        Text(" / $max" + when { hp == 0 -> " · down"; hp <= max / 2 -> " · bloodied"; else -> "" }, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outlineVariant)) {
                        Box(Modifier.fillMaxWidth(hp.toFloat() / max).height(6.dp).background(if (hp <= max / 2) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            amount, { amount = it.filter(Char::isDigit).take(4) }, placeholder = { Text("Amount") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(120.dp),
                        )
                        OutlinedButton(enabled = amount.isNotEmpty(), onClick = { vm.setMonsterHp(m, (hp - amount.toInt()).coerceAtLeast(0)); amount = "" }) {
                            Text("Damage", color = MaterialTheme.colorScheme.error)
                        }
                        OutlinedButton(enabled = amount.isNotEmpty(), onClick = { vm.setMonsterHp(m, (hp + amount.toInt()).coerceAtMost(max)); amount = "" }) {
                            Text("Heal", color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }

        StatBlockView(b, onRoll)
        last?.let { Text("Last roll — $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text("Tap a gold attack bonus or damage roll to roll it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}

/** The classic 5e stat block: parchment, red rules and headings. */
@Composable
fun StatBlockView(b: StatBlock, onRoll: ((String, String) -> Unit)?) {
    val body = MaterialTheme.typography.bodyLarge.copy(color = SbInk, fontSize = 16.5.sp, lineHeight = 23.sp)
    Surface(color = Parch, shape = RoundedCornerShape(6.dp), shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().height(5.dp).background(SbAmber))
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(b.name.ifBlank { "Unnamed creature" }, fontFamily = CinzelFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = SbRed)
                if (b.meta.isNotBlank()) Text(b.meta, style = body, fontStyle = FontStyle.Italic)
                TaperedRule()
                PropLine("Armor Class", b.ac.ifBlank { "—" }, body)
                PropLine("Hit Points", b.hp.ifBlank { "—" }, body, rollLabel = "Rolled hit points", onRoll = onRoll)
                PropLine("Speed", b.speed.ifBlank { "—" }, body)
                TaperedRule()
                Row(Modifier.fillMaxWidth()) {
                    b.scores.forEach { (k, v) ->
                        val mod = StatBlock.mod(v)
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = onRoll != null) {
                                    val r = rollDice(1, 20, mod)
                                    onRoll?.invoke("$k check", "${r.total} (d20 ${r.rolls[0]} ${StatBlock.fmtMod(mod)})")
                                }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(k, fontWeight = FontWeight.Bold, color = SbRed, style = body)
                            Text("$v (${StatBlock.fmtMod(mod)})", style = body.copy(fontSize = 14.5.sp), textAlign = TextAlign.Center)
                        }
                    }
                }
                TaperedRule()
                val props = listOf(
                    "Saving Throws" to b.saves, "Skills" to b.skills, "Damage Vulnerabilities" to b.vulnerabilities,
                    "Damage Resistances" to b.resistances, "Damage Immunities" to b.immunities, "Condition Immunities" to b.conditionImmunities,
                    "Senses" to b.senses, "Languages" to b.languages, "Challenge" to b.cr,
                ).filter { it.second.isNotBlank() }
                props.forEach { (k, v) -> PropLine(k, v, body) }
                if (props.isNotEmpty()) TaperedRule()
                b.traits.forEach { Entry(it, it.name, body, onRoll) }
                Section("Actions", b.actions, null, body, onRoll)
                Section("Bonus Actions", b.bonusActions, null, body, onRoll)
                Section("Reactions", b.reactions, null, body, onRoll)
                Section("Legendary Actions", b.legendary, b.legendaryIntro, body, onRoll)
                if (b.notes.isNotBlank()) {
                    SectionHeading("Notes")
                    Text(b.notes, style = body)
                }
            }
            Box(Modifier.fillMaxWidth().height(5.dp).background(SbAmber))
        }
    }
}

@Composable
private fun TaperedRule() {
    Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(4.dp).background(
        androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(SbRule, SbRule, SbRule.copy(alpha = 0f))),
        androidx.compose.foundation.shape.GenericShape { size, _ -> moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() },
    ))
}

@Composable
private fun SectionHeading(title: String) {
    Column(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
        Text(title, fontFamily = CinzelFamily, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = SbRed)
        HorizontalDivider(color = SbRule, thickness = 1.5.dp)
    }
}

@Composable
private fun Section(title: String, items: List<NamedText>, intro: String?, body: androidx.compose.ui.text.TextStyle, onRoll: ((String, String) -> Unit)?) {
    if (items.isEmpty() && intro.isNullOrBlank()) return
    SectionHeading(title)
    if (!intro.isNullOrBlank()) Text(intro, style = body, modifier = Modifier.padding(bottom = 8.dp))
    items.forEach { Entry(it, it.name.ifBlank { title }, body, onRoll) }
}

@Composable
private fun PropLine(label: String, value: String, body: androidx.compose.ui.text.TextStyle, rollLabel: String? = null, onRoll: ((String, String) -> Unit)? = null) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = SbRed, fontWeight = FontWeight.Bold)) { append(label) }
            append("  ")
            appendRollable(value, rollLabel ?: label, onRoll, isHp = rollLabel != null)
        },
        style = body,
    )
}

@Composable
private fun Entry(t: NamedText, label: String, body: androidx.compose.ui.text.TextStyle, onRoll: ((String, String) -> Unit)?) {
    Text(
        buildAnnotatedString {
            if (t.name.isNotBlank()) withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) { append("${t.name}. ") }
            appendRollable(t.text, label, onRoll)
        },
        style = body,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/** Appends text where attack bonuses and damage dice become tappable rolls. */
private fun AnnotatedString.Builder.appendRollable(text: String, label: String, onRoll: ((String, String) -> Unit)?, isHp: Boolean = false) {
    if (onRoll == null) { append(text); return }
    val style = TextLinkStyles(SpanStyle(color = Color(0xFF5A1A0A), fontWeight = FontWeight.SemiBold, background = SbAmber.copy(alpha = 0.28f), textDecoration = TextDecoration.Underline))
    rollable(text).forEachIndexed { i, s ->
        when (s) {
            is Seg.Text -> append(s.s)
            is Seg.Hit -> withLink(LinkAnnotation.Clickable("hit$i", style) {
                val r = rollDice(1, 20, s.bonus); val d = r.rolls[0]
                onRoll("$label attack", "${r.total}${if (d == 20) " — NATURAL 20!" else if (d == 1) " — natural 1" else ""} (d20 $d ${StatBlock.fmtMod(s.bonus)})")
            }) { append(s.s) }
            is Seg.Dmg -> withLink(LinkAnnotation.Clickable("dmg$i", style) {
                val r = rollDice(s.n, s.d, s.plus)
                val plus = if (s.plus != 0) " ${if (s.plus > 0) "+" else "−"} ${kotlin.math.abs(s.plus)}" else ""
                onRoll(if (isHp) label else "$label damage", "${r.total} (${r.rolls.joinToString(" + ")}$plus)")
            }) { append(s.s) }
        }
    }
}

// ---------- Editing ----------

private fun joinNamed(l: List<NamedText>) = l.joinToString("\n\n") { if (it.name.isNotBlank()) "${it.name}. ${it.text}" else it.text }
private fun splitList(s: String) = s.split(Regex("""\n\s*\n""")).map { it.trim() }.filter { it.isNotEmpty() }.map { StatBlock.splitNamed(it) }

@Composable
private fun MonsterEditor(b0: StatBlock, isNew: Boolean, onDismiss: () -> Unit, onSave: (StatBlock) -> Unit) {
    var b by remember { mutableStateOf(b0) }
    var traits by remember { mutableStateOf(joinNamed(b0.traits)) }
    var actions by remember { mutableStateOf(joinNamed(b0.actions)) }
    var bonus by remember { mutableStateOf(joinNamed(b0.bonusActions)) }
    var reactions by remember { mutableStateOf(joinNamed(b0.reactions)) }
    var legendary by remember { mutableStateOf(joinNamed(b0.legendary)) }
    val hint = "Name. What it does.\n\nNext one. Separate entries with a blank line."
    FormDialog(
        title = if (isNew) "New monster" else "Edit ${b0.name}",
        onDismiss = onDismiss,
        saveEnabled = b.name.isNotBlank(),
        onSave = {
            onSave(b.copy(name = b.name.trim(), traits = splitList(traits), actions = splitList(actions), bonusActions = splitList(bonus), reactions = splitList(reactions), legendary = splitList(legendary)))
        },
    ) {
        Field(b.name, { b = b.copy(name = it) }, "Name")
        Field(b.meta, { b = b.copy(meta = it) }, "Size, type, alignment", placeholder = "Medium undead, chaotic evil")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field(b.ac, { b = b.copy(ac = it) }, "Armor Class", Modifier.weight(1f))
            Field(b.hp, { b = b.copy(hp = it) }, "Hit Points", Modifier.weight(1f), placeholder = "22 (4d8 + 4)")
        }
        Field(b.speed, { b = b.copy(speed = it) }, "Speed")
        SectionLabel("Ability scores")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            b.scores.forEach { (k, v) ->
                OutlinedTextField(
                    v.toString(),
                    { t ->
                        val n = t.filter(Char::isDigit).take(2).toIntOrNull()?.coerceIn(1, 30) ?: 1
                        b = when (k) { "STR" -> b.copy(str = n); "DEX" -> b.copy(dex = n); "CON" -> b.copy(con = n); "INT" -> b.copy(int = n); "WIS" -> b.copy(wis = n); else -> b.copy(cha = n) }
                    },
                    label = { Text(k, fontSize = 11.sp) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Field(b.saves, { b = b.copy(saves = it) }, "Saving throws", placeholder = "Dex +5, Wis +3")
        Field(b.skills, { b = b.copy(skills = it) }, "Skills")
        Field(b.resistances, { b = b.copy(resistances = it) }, "Damage resistances")
        Field(b.immunities, { b = b.copy(immunities = it) }, "Damage immunities")
        Field(b.vulnerabilities, { b = b.copy(vulnerabilities = it) }, "Damage vulnerabilities")
        Field(b.conditionImmunities, { b = b.copy(conditionImmunities = it) }, "Condition immunities")
        Field(b.senses, { b = b.copy(senses = it) }, "Senses", placeholder = "darkvision 60 ft., passive Perception 12")
        Field(b.languages, { b = b.copy(languages = it) }, "Languages")
        Field(b.cr, { b = b.copy(cr = it) }, "Challenge", placeholder = "2 (450 XP)")
        Field(traits, { traits = it }, "Traits", singleLine = false, minLines = 3, placeholder = hint)
        Field(actions, { actions = it }, "Actions", singleLine = false, minLines = 5, placeholder = hint)
        Field(bonus, { bonus = it }, "Bonus actions", singleLine = false, minLines = 2, placeholder = hint)
        Field(reactions, { reactions = it }, "Reactions", singleLine = false, minLines = 2, placeholder = hint)
        Field(b.legendaryIntro, { b = b.copy(legendaryIntro = it) }, "Legendary actions intro", singleLine = false, minLines = 2)
        Field(legendary, { legendary = it }, "Legendary actions", singleLine = false, minLines = 2, placeholder = hint)
        Field(b.notes, { b = b.copy(notes = it) }, "Notes", singleLine = false, minLines = 3)
    }
}
