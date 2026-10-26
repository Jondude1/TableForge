package com.jondude.tableforge.ui

import com.jondude.tableforge.data.ideaType
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jondude.tableforge.AppViewModel
import com.jondude.tableforge.data.IdeaType
import com.jondude.tableforge.data.MapEdge
import com.jondude.tableforge.data.MapNode
import com.jondude.tableforge.ui.theme.color
import kotlin.math.roundToInt

private const val NODE_W = 180f
private const val NODE_H = 68f
private const val BOARD_DP = 4000f

private fun clampToBoard(p: Offset) = Offset(p.x.coerceIn(0f, BOARD_DP - NODE_W), p.y.coerceIn(0f, BOARD_DP - NODE_H))

@Composable
fun ConceptMapScreen(vm: AppViewModel, wide: Boolean) {
    val nodes by vm.nodes.collectAsStateWithLifecycle()
    val edges by vm.edges.collectAsStateWithLifecycle()
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var linkingFrom by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf<Offset?>(null) }
    var pickingIdea by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MapNode?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset(40f, 40f)) }
    // Positions while dragging, so the node follows the finger before it's saved.
    val live = remember { mutableStateMapOf<Long, Offset>() }
    val selected = nodes.firstOrNull { it.id == selectedId }
    val density = LocalDensity.current

    LaunchedEffect(nodes) { if (selectedId != null && nodes.none { it.id == selectedId }) selectedId = null }

    fun posOf(n: MapNode): Offset = live[n.id] ?: Offset(n.x, n.y)

    /** Screen point -> map coordinates (dp). */
    fun toMap(p: Offset): Offset = clampToBoard(Offset((p.x - pan.x) / scale / density.density - NODE_W / 2, (p.y - pan.y) / scale / density.density - NODE_H / 2))

    val canvas: @Composable (Modifier) -> Unit = { mod ->
        val nodesNow by rememberUpdatedState(nodes)
        Box(
            mod
                .clipToBounds()
                .background(Color(0xFF17110C))
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, p, zoom, _ ->
                        val ns = (scale * zoom).coerceIn(0.35f, 2.5f)
                        val f = ns / scale
                        pan = Offset(centroid.x - (centroid.x - pan.x) * f, centroid.y - (centroid.y - pan.y) * f) + p
                        scale = ns
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { selectedId = null; linkingFrom = null },
                        onDoubleTap = { p -> adding = toMap(p) },
                    )
                },
        ) {
            // Gold dot grid that moves with the map (Rathgar Gold canvas).
            Canvas(Modifier.fillMaxSize()) {
                val step = 32.dp.toPx() * scale
                val dot = Color(0xFFD4A84B).copy(alpha = 0.16f)
                var x = ((pan.x % step) + step) % step
                while (x < size.width) {
                    var y = ((pan.y % step) + step) % step
                    while (y < size.height) { drawCircle(dot, 1.5f, Offset(x, y)); y += step }
                    x += step
                }
            }
            Box(
                Modifier
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = scale; scaleY = scale
                        translationX = pan.x; translationY = pan.y
                    }
                    // A large fixed board so every concept stays inside the hit-testable area.
                    .requiredSize(BOARD_DP.dp, BOARD_DP.dp),
            ) {
                // Links
                Canvas(Modifier.fillMaxSize()) {
                    val d = density.density
                    edges.forEach { e ->
                        val a = nodesNow.firstOrNull { it.id == e.fromId } ?: return@forEach
                        val b = nodesNow.firstOrNull { it.id == e.toId } ?: return@forEach
                        val pa = posOf(a); val pb = posOf(b)
                        val ca = Offset((pa.x + NODE_W / 2) * d, (pa.y + NODE_H / 2) * d)
                        val cb = Offset((pb.x + NODE_W / 2) * d, (pb.y + NODE_H / 2) * d)
                        val hot = selectedId == a.id || selectedId == b.id
                        drawLine(
                            color = if (hot) Color(0xFFF0C766) else Color(0xFFD4A84B).copy(alpha = 0.45f),
                            start = ca, end = cb, strokeWidth = if (hot) 3.5f else 2.5f,
                            pathEffect = if (e.label.isBlank()) null else PathEffect.dashPathEffect(floatArrayOf(14f, 8f)),
                        )
                    }
                }
                // Link labels at midpoints
                edges.filter { it.label.isNotBlank() }.forEach { e ->
                    val a = nodes.firstOrNull { it.id == e.fromId } ?: return@forEach
                    val b = nodes.firstOrNull { it.id == e.toId } ?: return@forEach
                    val pa = posOf(a); val pb = posOf(b)
                    val mx = (pa.x + pb.x) / 2 + NODE_W / 2 - 50
                    val my = (pa.y + pb.y) / 2 + NODE_H / 2 - 10
                    Text(
                        e.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .offset { IntOffset((mx * density.density).roundToInt(), (my * density.density).roundToInt()) }
                            .width(100.dp)
                            .background(Color(0xFF17110C).copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp),
                    )
                }
                // Nodes
                nodes.forEach { n ->
                    val p = posOf(n)
                    NodeCard(
                        n,
                        selected = n.id == selectedId,
                        linkTarget = linkingFrom != null && linkingFrom != n.id,
                        modifier = Modifier
                            .offset { IntOffset((p.x * density.density).roundToInt(), (p.y * density.density).roundToInt()) }
                            .size(NODE_W.dp, NODE_H.dp)
                            .pointerInput(n) {
                                detectTapGestures(
                                    onTap = {
                                        val from = linkingFrom
                                        if (from != null && from != n.id) {
                                            vm.connect(from, n.id); linkingFrom = null; selectedId = n.id
                                        } else selectedId = n.id
                                    },
                                    onDoubleTap = { editing = n },
                                )
                            }
                            .pointerInput(n) {
                                detectDragGestures(
                                    onDragStart = { selectedId = n.id; live[n.id] = Offset(n.x, n.y) },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        val cur = live[n.id] ?: Offset(n.x, n.y)
                                        live[n.id] = clampToBoard(cur + Offset(delta.x / density.density, delta.y / density.density))
                                    },
                                    onDragEnd = {
                                        live[n.id]?.let { vm.moveNode(n.id, it.x, it.y) }
                                        // Keep the live position until the database echoes the move back.
                                    },
                                    onDragCancel = { live.remove(n.id) },
                                )
                            },
                    )
                }
            }
            if (nodes.isEmpty()) {
                EmptyState(
                    Icons.Outlined.AccountTree,
                    "Map out your ideas",
                    "Double-tap anywhere to add a concept, drag to arrange, and link related ideas — factions, NPCs, secrets, places. You can also pull in saved ideas.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { adding = toMap(Offset(200f, 200f)) }) { Text("Add a concept") }
                        OutlinedButton(onClick = { pickingIdea = true }, enabled = ideas.isNotEmpty()) { Text("From Ideas") }
                    }
                }
            }
            if (linkingFrom != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Tap another concept to link it", style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { linkingFrom = null }) { Icon(Icons.Outlined.Close, "Cancel linking") }
                    }
                }
            }
        }
    }

    // Drop live positions once the stored node catches up.
    LaunchedEffect(nodes) {
        nodes.forEach { n -> live[n.id]?.let { p -> if (kotlin.math.abs(p.x - n.x) < 0.5f && kotlin.math.abs(p.y - n.y) < 0.5f) live.remove(n.id) } }
    }

    val panel: @Composable (Modifier) -> Unit = { mod ->
        Column(mod.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Concepts", style = MaterialTheme.typography.headlineSmall)
            Text("${nodes.size} concepts · ${edges.size} links", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { adding = toMap(Offset(240f, 200f)) }) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Concept") }
                OutlinedButton(onClick = { pickingIdea = true }, enabled = ideas.isNotEmpty()) { Icon(Icons.Outlined.Lightbulb, null); Spacer(Modifier.width(6.dp)); Text("From Ideas") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (selected == null) {
                Text(
                    "Double-tap the canvas to add a concept. Tap one to select it, drag to move, double-tap to edit. Pinch to zoom.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val kind = IdeaType.from(selected.kind)
                TypeTag(kind)
                Text(selected.label, style = MaterialTheme.typography.titleLarge)
                if (selected.note.isNotBlank()) LabeledBody(selected.note)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = { linkingFrom = selected.id }) { Icon(Icons.Outlined.Link, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Link to…") }
                    TextButton(onClick = { editing = selected }) { Text("Edit") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.deleteNode(selected); selectedId = null }) { Icon(Icons.Outlined.Delete, "Delete concept", tint = MaterialTheme.colorScheme.error) }
                }
                val links = edges.filter { it.fromId == selected.id || it.toId == selected.id }
                if (links.isNotEmpty()) {
                    SectionLabel("Links")
                    links.forEach { e -> LinkRow(e, nodes, selected.id, onSave = vm::saveEdge, onDelete = { vm.deleteEdge(e) }) }
                }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            canvas(Modifier.weight(1f).fillMaxHeight())
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(340.dp).fillMaxHeight()) { panel(Modifier.fillMaxSize()) }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            canvas(Modifier.weight(1.4f).fillMaxWidth())
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.weight(1f).fillMaxWidth()) { panel(Modifier.fillMaxSize()) }
        }
    }

    adding?.let { at ->
        NodeDialog(null, onDismiss = { adding = null }) { label, note, kind ->
            vm.addNode(label, note, kind, at.x, at.y); adding = null
        }
    }
    editing?.let { n ->
        NodeDialog(n, onDismiss = { editing = null }) { label, note, kind ->
            vm.updateNode(n.copy(label = label, note = note, kind = kind.name)); editing = null
        }
    }
    if (pickingIdea) {
        AlertDialog(
            onDismissRequest = { pickingIdea = false },
            title = { Text("Add from Ideas") },
            text = {
                LazyColumn(Modifier.height(420.dp)) {
                    items(ideas, key = { it.id }) { i ->
                        val onMap = nodes.any { it.ideaId == i.id }
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !onMap) { vm.ideaToMap(i); pickingIdea = false }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(10.dp).background(i.ideaType.color(), CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(i.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (onMap) Text("on map", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingIdea = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun NodeCard(n: MapNode, selected: Boolean, linkTarget: Boolean, modifier: Modifier) {
    val kind = IdeaType.from(n.kind)
    val c = kind.color()
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            when {
                selected -> MaterialTheme.colorScheme.primary
                linkTarget -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else -> c.copy(alpha = 0.55f)
            },
        ),
        shadowElevation = if (selected) 8.dp else 2.dp,
        modifier = modifier,
    ) {
        Row {
            Box(Modifier.width(5.dp).fillMaxHeight().background(c))
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(kind.singular.uppercase(), style = MaterialTheme.typography.labelSmall, color = c, maxLines = 1)
                Text(n.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LinkRow(e: MapEdge, nodes: List<MapNode>, selfId: Long, onSave: (MapEdge) -> Unit, onDelete: () -> Unit) {
    val otherId = if (e.fromId == selfId) e.toId else e.fromId
    val other = nodes.firstOrNull { it.id == otherId } ?: return
    var editing by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { editing = true }.padding(vertical = 2.dp)) {
        Column(Modifier.weight(1f)) {
            Text(other.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(e.label.ifBlank { "Tap to describe the link" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Close, "Remove link") }
    }
    if (editing) {
        var label by remember { mutableStateOf(e.label) }
        FormDialog("Link to ${other.label}", onDismiss = { editing = false }, onSave = { onSave(e.copy(label = label.trim())); editing = false }) {
            Field(label, { label = it }, "How are they connected?", placeholder = "e.g. secretly funds, rivals, owes a debt to")
        }
    }
}

@Composable
private fun NodeDialog(n: MapNode?, onDismiss: () -> Unit, onSave: (String, String, IdeaType) -> Unit) {
    var label by remember { mutableStateOf(n?.label ?: "") }
    var note by remember { mutableStateOf(n?.note ?: "") }
    var kind by remember { mutableStateOf(n?.let { IdeaType.from(it.kind) } ?: IdeaType.NOTE) }
    FormDialog(if (n == null) "New concept" else "Edit concept", onDismiss = onDismiss, onSave = { onSave(label.trim(), note.trim(), kind) }, saveEnabled = label.isNotBlank()) {
        Field(label, { label = it }, "Name", placeholder = "e.g. The Ashen Court")
        Dropdown("Kind", IdeaType.entries, kind, { it.singular.replaceFirstChar { c -> c.uppercase() } }, { kind = it })
        Field(note, { note = it }, "Notes", singleLine = false, minLines = 4)
    }
}
