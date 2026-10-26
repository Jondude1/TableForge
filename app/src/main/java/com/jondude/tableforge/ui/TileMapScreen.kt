package com.jondude.tableforge.ui

import com.jondude.tableforge.data.terrainAt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jondude.tableforge.AppViewModel
import com.jondude.tableforge.ai.GridRect
import com.jondude.tableforge.ai.MapForge
import com.jondude.tableforge.data.AiProvider
import com.jondude.tableforge.data.MapFeature
import com.jondude.tableforge.data.Terrain
import com.jondude.tableforge.data.TileMap
import com.jondude.tableforge.ui.theme.CinzelFamily
import kotlin.math.floor
import kotlin.math.roundToInt

private const val CELL_DP = 44f
private const val MARGIN_DP = 26f

private val SUGGESTIONS = listOf(
    "A lighthouse that takes up 4 squares",
    "A river running north to south through here",
    "A goblin camp with 3 tents around a campfire",
    "Make this whole area a swamp",
    "A ruined watchtower with a hidden cellar",
    "A small fishing dock with two rowboats",
)

private enum class MapMode { ASK, PAINT }

fun Terrain.color(): Color = Color(argb)

private fun featureColor(f: MapFeature): Color =
    runCatching { Color(android.graphics.Color.parseColor(f.color)) }.getOrNull() ?: Color(0xFFD4A84B)

@Composable
fun TileMapScreen(vm: AppViewModel, wide: Boolean) {
    val maps by vm.tileMaps.collectAsStateWithLifecycle()
    val req by vm.mapRequest.collectAsStateWithLifecycle()
    val ai by vm.ai.collectAsStateWithLifecycle()
    var mapId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by remember { mutableStateOf(false) }
    var editingInfo by remember { mutableStateOf<TileMap?>(null) }
    var deletingMap by remember { mutableStateOf<TileMap?>(null) }

    LaunchedEffect(maps) {
        if (maps.isNotEmpty() && maps.none { it.id == mapId }) mapId = maps.first().id
        if (maps.isEmpty()) mapId = null
    }
    val map = maps.firstOrNull { it.id == mapId }

    if (map == null) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("Maps", "Grid maps you can build by tapping and asking")
            EmptyState(
                Icons.Outlined.Map,
                "Start a map",
                "Make a grid, tap any square (or long-press and drag over an area), and describe what goes there — \"a lighthouse that takes up 4 squares\". It gets placed, sized and described for you.",
            ) { Button(onClick = { creating = true }) { Text("New map") } }
        }
    } else {
        MapWorkspace(
            vm = vm,
            map = map,
            maps = maps,
            wide = wide,
            aiReady = ai.provider != AiProvider.OFFLINE && ai.isConfigured,
            req = req,
            onSwitch = { mapId = it },
            onNew = { creating = true },
            onEditInfo = { editingInfo = map },
            onDelete = { deletingMap = map },
        )
    }

    if (creating) NewMapDialog(onDismiss = { creating = false }) { name, cols, rows, base, feet ->
        vm.createMap(name, cols, rows, base, feet) { id -> mapId = id }
        creating = false
    }
    editingInfo?.let { m ->
        var name by remember { mutableStateOf(m.name) }
        var notes by remember { mutableStateOf(m.notes) }
        var feet by remember { mutableStateOf(m.feetPerSquare.toString()) }
        FormDialog("Map details", onDismiss = { editingInfo = null }, onSave = {
            vm.updateMapInfo(m.copy(name = name.trim(), notes = notes.trim(), feetPerSquare = feet.toIntOrNull()?.coerceIn(1, 5280) ?: m.feetPerSquare))
            editingInfo = null
        }, saveEnabled = name.isNotBlank()) {
            Field(name, { name = it }, "Name")
            Field(feet, { feet = it.filter(Char::isDigit) }, "Feet per square")
            Field(notes, { notes = it }, "What this map is (helps Claude)", singleLine = false, minLines = 3, placeholder = "e.g. A storm-battered fishing village on a rocky coast")
        }
    }
    deletingMap?.let { m ->
        ConfirmDialog("Delete \"${m.name}\"?", "The map and everything on it will be removed.", onConfirm = { vm.deleteMap(m) }, onDismiss = { deletingMap = null })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MapWorkspace(
    vm: AppViewModel,
    map: TileMap,
    maps: List<TileMap>,
    wide: Boolean,
    aiReady: Boolean,
    req: com.jondude.tableforge.MapRequestState,
    onSwitch: (Long) -> Unit,
    onNew: () -> Unit,
    onEditInfo: () -> Unit,
    onDelete: () -> Unit,
) {
    val featuresFlow = remember(map.id) { vm.features(map.id) }
    val features by featuresFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var selection by remember(map.id) { mutableStateOf<GridRect?>(null) }
    var selectedFeatureId by remember(map.id) { mutableStateOf<Long?>(null) }
    var mode by rememberSaveable { mutableStateOf(MapMode.ASK) }
    var brush by rememberSaveable { mutableStateOf(Terrain.WATER) }
    var request by rememberSaveable { mutableStateOf("") }
    var editingFeature by remember { mutableStateOf<MapFeature?>(null) }
    var recenter by remember { mutableIntStateOf(0) }
    val selectedFeature = features.firstOrNull { it.id == selectedFeatureId }

    val onCellTap: (Int, Int) -> Unit = { x, y ->
        val hit = features.lastOrNull { x in it.x until it.x + it.w && y in it.y until it.y + it.h }
        if (mode == MapMode.PAINT) {
            vm.paintCells(map.id, brush, GridRect(x, y, 1, 1))
        } else if (hit != null && selectedFeatureId != hit.id) {
            selectedFeatureId = hit.id
            selection = GridRect(hit.x, hit.y, hit.w, hit.h)
        } else {
            selectedFeatureId = null
            selection = GridRect(x, y, 1, 1)
        }
    }

    val canvas: @Composable (Modifier) -> Unit = { mod ->
        Box(mod) {
            MapCanvas(
                map = map,
                features = features,
                selection = selection,
                selectedFeatureId = selectedFeatureId,
                recenterKey = recenter,
                allowMove = mode == MapMode.ASK,
                onCellTap = onCellTap,
                onAreaSelected = { r ->
                    selectedFeatureId = null
                    if (mode == MapMode.PAINT) vm.paintCells(map.id, brush, r) else selection = r
                },
                onFeatureMoved = { f, x, y -> vm.saveFeature(f.copy(x = x, y = y)); selection = GridRect(x, y, f.w, f.h) },
            )
            // Floating hint + recenter
            Row(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f), shape = RoundedCornerShape(50)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.TouchApp, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (mode == MapMode.PAINT) "Tap or long-press + drag to paint · pinch to zoom"
                            else "Tap a square · long-press + drag for an area · long-press a feature to move it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { recenter++ }) { Icon(Icons.Outlined.CenterFocusStrong, "Fit map to screen") }
            }
        }
    }

    val panel: @Composable (Modifier) -> Unit = { mod ->
        Column(mod.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MapPicker(map, maps, onSwitch, onNew, onEditInfo, onDelete)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = mode == MapMode.ASK, onClick = { mode = MapMode.ASK }, shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(16.dp)) }) { Text("Build") }
                SegmentedButton(selected = mode == MapMode.PAINT, onClick = { mode = MapMode.PAINT; selectedFeatureId = null }, shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Outlined.Brush, null, Modifier.size(16.dp)) }) { Text("Paint") }
            }

            if (req.loading || req.error != null || req.summary != null) {
                StatusCard(req, onUndo = vm::undoMapChange, onDismiss = vm::dismissMapMessage)
            }

            if (mode == MapMode.PAINT) {
                SectionLabel("Terrain brush")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Terrain.entries.forEach { t ->
                        val on = t == brush
                        Row(
                            Modifier
                                .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { brush = t }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(18.dp).background(t.color(), RoundedCornerShape(4.dp)).border(1.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(6.dp))
                            Text(t.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Text("Painting is instant and works offline. Use Build to describe bigger terrain changes in words.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (selectedFeature != null) {
                FeatureCard(
                    selectedFeature,
                    onEdit = { editingFeature = selectedFeature },
                    onDelete = { vm.deleteFeature(selectedFeature); selectedFeatureId = null },
                    onToIdea = { vm.featureToIdea(selectedFeature) },
                    onAskAbout = { request = "Change ${selectedFeature.name}: " },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AskBox(selection, request, { request = it }, req.loading, aiReady, onSend = {
                    selection?.let { s -> vm.requestOnMap(map.id, s, request) { request = "" } }
                }, onAddPlain = null)
            } else {
                AskBox(
                    selection, request, { request = it }, req.loading, aiReady,
                    onSend = { selection?.let { s -> vm.requestOnMap(map.id, s, request) { request = "" } } },
                    onAddPlain = { selection?.let { s -> vm.addFeatureManually(map.id, s, request); request = "" } },
                )
            }

            if (features.isNotEmpty()) {
                SectionLabel("On this map · ${features.size}")
                features.sortedBy { it.y * map.cols + it.x }.forEach { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { mode = MapMode.ASK; selectedFeatureId = f.id; selection = GridRect(f.x, f.y, f.w, f.h) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(f.icon, fontSize = 18.sp, modifier = Modifier.width(30.dp))
                        Text(f.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(GridRect(f.x, f.y, f.w, f.h).label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            canvas(Modifier.weight(1f).fillMaxHeight())
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.width(380.dp).fillMaxHeight()) {
                panel(Modifier.fillMaxSize())
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            canvas(Modifier.weight(1.2f).fillMaxWidth())
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.weight(1f).fillMaxWidth()) {
                panel(Modifier.fillMaxSize())
            }
        }
    }

    editingFeature?.let { f ->
        FeatureDialog(f, map, onDismiss = { editingFeature = null }, onSave = {
            vm.saveFeature(it); editingFeature = null; selection = GridRect(it.x, it.y, it.w, it.h)
        })
    }
}

/** The zoomable, pannable grid. Gestures are converted to grid squares here. */
@Composable
private fun MapCanvas(
    map: TileMap,
    features: List<MapFeature>,
    selection: GridRect?,
    selectedFeatureId: Long?,
    recenterKey: Int,
    allowMove: Boolean,
    onCellTap: (Int, Int) -> Unit,
    onAreaSelected: (GridRect) -> Unit,
    onFeatureMoved: (MapFeature, Int, Int) -> Unit,
) {
    val density = LocalDensity.current
    val cellPx = with(density) { CELL_DP.dp.toPx() }
    val marginPx = with(density) { MARGIN_DP.dp.toPx() }
    val contentW = marginPx + map.cols * cellPx
    val contentH = marginPx + map.rows * cellPx

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(map.id) { mutableFloatStateOf(1f) }
    var offset by remember(map.id) { mutableStateOf(Offset.Zero) }

    // Fit the whole map in view when opened or when asked.
    LaunchedEffect(map.id, viewport, recenterKey) {
        if (viewport.width > 0 && viewport.height > 0) {
            val s = minOf(viewport.width / (contentW + marginPx), viewport.height / (contentH + marginPx)).coerceIn(0.3f, 2.5f)
            scale = s
            offset = Offset((viewport.width - contentW * s) / 2f, (viewport.height - contentH * s) / 2f)
        }
    }

    // Drag state for long-press gestures (area select or moving a feature).
    var dragStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragNow by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var moving by remember { mutableStateOf<MapFeature?>(null) }
    val currentFeatures by rememberUpdatedState(features)
    val tap by rememberUpdatedState(onCellTap)
    val area by rememberUpdatedState(onAreaSelected)
    val moved by rememberUpdatedState(onFeatureMoved)
    val canMove by rememberUpdatedState(allowMove)

    fun cellAt(p: Offset): Pair<Int, Int>? {
        val x = floor((p.x - marginPx) / cellPx).toInt()
        val y = floor((p.y - marginPx) / cellPx).toInt()
        return if (x in 0 until map.cols && y in 0 until map.rows) x to y else null
    }
    fun clampCell(p: Offset): Pair<Int, Int> = (
        floor((p.x - marginPx) / cellPx).toInt().coerceIn(0, map.cols - 1) to
            floor((p.y - marginPx) / cellPx).toInt().coerceIn(0, map.rows - 1)
        )

    val gold = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF110D09))
            .onSizeChanged { viewport = it }
            .pointerInputTransform(map.id) { centroid, pan, zoom ->
                val newScale = (scale * zoom).coerceIn(0.25f, 4f)
                val f = newScale / scale
                offset = Offset(centroid.x - (centroid.x - offset.x) * f, centroid.y - (centroid.y - offset.y) * f) + pan
                scale = newScale
            },
    ) {
        Box(
            Modifier
                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                // The zoom layer must wrap the full-size content so hit testing happens
                // in map coordinates; otherwise zoomed-in taps near the edges are dropped.
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                }
                .requiredSize(with(density) { contentW.toDp() }, with(density) { contentH.toDp() })
                .pointerInputTaps(map.id) { p -> cellAt(p)?.let { (x, y) -> tap(x, y) } }
                .pointerInputLongDrag(
                    key = map.id,
                    onStart = { p ->
                        val c = cellAt(p)
                        if (c != null) {
                            val hit = if (!canMove) null
                            else currentFeatures.lastOrNull { c.first in it.x until it.x + it.w && c.second in it.y until it.y + it.h }
                            moving = hit
                            dragStart = c; dragNow = c
                        }
                    },
                    onDrag = { p -> if (dragStart != null) dragNow = clampCell(p) },
                    onEnd = {
                        val s = dragStart; val n = dragNow; val m = moving
                        if (s != null && n != null) {
                            if (m != null) {
                                val nx = (m.x + n.first - s.first).coerceIn(0, map.cols - m.w)
                                val ny = (m.y + n.second - s.second).coerceIn(0, map.rows - m.h)
                                if (nx != m.x || ny != m.y) moved(m, nx, ny)
                            } else {
                                area(GridRect.spanning(s.first, s.second, n.first, n.second))
                            }
                        }
                        dragStart = null; dragNow = null; moving = null
                    },
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawTerrain(map, cellPx, marginPx)
                drawGrid(map, cellPx, marginPx)
                drawLabels(map, cellPx, marginPx, labelColor)
            }

            // Features as small tokens: tinted footprint, emoji, name.
            features.forEach { f ->
                val m = moving
                val s = dragStart
                val n = dragNow
                val (fx, fy) = if (m != null && m.id == f.id && s != null && n != null) {
                    (f.x + n.first - s.first).coerceIn(0, map.cols - f.w) to (f.y + n.second - s.second).coerceIn(0, map.rows - f.h)
                } else f.x to f.y
                FeatureToken(
                    f,
                    selected = f.id == selectedFeatureId,
                    lifted = m?.id == f.id,
                    modifier = Modifier
                        .offset { IntOffset((marginPx + fx * cellPx).roundToInt(), (marginPx + fy * cellPx).roundToInt()) }
                        .size(with(density) { (f.w * cellPx).toDp() }, with(density) { (f.h * cellPx).toDp() }),
                )
            }

            // Selection / drag rectangle on top.
            val rect = run {
                val s = dragStart; val n = dragNow
                if (s != null && n != null && moving == null) GridRect.spanning(s.first, s.second, n.first, n.second) else selection
            }
            if (rect != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val tl = Offset(marginPx + rect.x * cellPx, marginPx + rect.y * cellPx)
                    val sz = Size(rect.w * cellPx, rect.h * cellPx)
                    drawRect(gold.copy(alpha = 0.16f), tl, sz)
                    drawRect(gold, tl, sz, style = Stroke(width = 3f))
                }
            }
        }
    }
}

@Composable
private fun FeatureToken(f: MapFeature, selected: Boolean, lifted: Boolean, modifier: Modifier) {
    val c = featureColor(f)
    val small = f.w == 1 && f.h == 1
    Box(
        modifier
            .padding(2.dp)
            .graphicsLayer { if (lifted) { scaleX = 1.06f; scaleY = 1.06f; alpha = 0.85f } }
            .background(c.copy(alpha = 0.30f), RoundedCornerShape(6.dp))
            .border(if (selected) 2.5.dp else 1.5.dp, if (selected) MaterialTheme.colorScheme.primary else c, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(2.dp)) {
            Text(f.icon.ifBlank { "⭐" }, fontSize = if (small) 20.sp else 26.sp, textAlign = TextAlign.Center)
            if (!small) {
                Text(
                    f.name,
                    fontFamily = CinzelFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp, lineHeight = 10.sp,
                    color = Color(0xFFF4EBDA), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(3.dp)).padding(horizontal = 3.dp),
                )
            }
        }
    }
}

private fun DrawScope.drawTerrain(map: TileMap, cell: Float, margin: Float) {
    for (y in 0 until map.rows) for (x in 0 until map.cols) {
        val t = map.terrainAt(x, y)
        val base = t.color()
        // Slight checker variation so large areas don't look flat.
        val c = if ((x + y) % 2 == 0) base else base.copy(red = base.red * 0.94f, green = base.green * 0.94f, blue = base.blue * 0.94f)
        val tl = Offset(margin + x * cell, margin + y * cell)
        drawRect(c, tl, Size(cell + 0.5f, cell + 0.5f))
        when (t) {
            Terrain.FOREST -> {
                drawCircle(Color(0xFF1A2915), cell * 0.18f, tl + Offset(cell * 0.32f, cell * 0.36f))
                drawCircle(Color(0xFF1A2915), cell * 0.15f, tl + Offset(cell * 0.66f, cell * 0.62f))
            }
            Terrain.WATER, Terrain.DEEP -> {
                val wave = Color.White.copy(alpha = 0.13f)
                drawLine(wave, tl + Offset(cell * 0.2f, cell * 0.4f), tl + Offset(cell * 0.45f, cell * 0.4f), 2f)
                drawLine(wave, tl + Offset(cell * 0.55f, cell * 0.68f), tl + Offset(cell * 0.8f, cell * 0.68f), 2f)
            }
            Terrain.WALL -> {
                val h = Color.White.copy(alpha = 0.06f)
                drawLine(h, tl, tl + Offset(cell, cell), 2f)
                drawLine(h, tl + Offset(cell / 2, 0f), tl + Offset(cell, cell / 2), 2f)
                drawLine(h, tl + Offset(0f, cell / 2), tl + Offset(cell / 2, cell), 2f)
            }
            Terrain.MOUNTAIN -> {
                val p = Color.Black.copy(alpha = 0.22f)
                drawLine(p, tl + Offset(cell * 0.2f, cell * 0.75f), tl + Offset(cell * 0.5f, cell * 0.3f), 3f)
                drawLine(p, tl + Offset(cell * 0.5f, cell * 0.3f), tl + Offset(cell * 0.8f, cell * 0.75f), 3f)
            }
            Terrain.SWAMP -> drawCircle(Color(0xFF2B3A40).copy(alpha = 0.6f), cell * 0.14f, tl + Offset(cell * 0.6f, cell * 0.45f))
            Terrain.LAVA -> drawCircle(Color(0xFFF2A33A).copy(alpha = 0.45f), cell * 0.12f, tl + Offset(cell * 0.4f, cell * 0.55f))
            else -> Unit
        }
    }
}

private fun DrawScope.drawGrid(map: TileMap, cell: Float, margin: Float) {
    val line = Color.Black.copy(alpha = 0.28f)
    val w = map.cols * cell
    val h = map.rows * cell
    for (x in 0..map.cols) drawLine(line, Offset(margin + x * cell, margin), Offset(margin + x * cell, margin + h), 1f)
    for (y in 0..map.rows) drawLine(line, Offset(margin, margin + y * cell), Offset(margin + w, margin + y * cell), 1f)
    // Faint gold dots at intersections, after the Rathgar Gold canvas grid.
    val dot = Color(0xFFD4A84B).copy(alpha = 0.22f)
    for (x in 0..map.cols step 1) for (y in 0..map.rows step 1) drawCircle(dot, 1.6f, Offset(margin + x * cell, margin + y * cell))
    drawRect(Color(0xFFD4A84B).copy(alpha = 0.55f), Offset(margin, margin), Size(w, h), style = Stroke(2f))
}

private fun DrawScope.drawLabels(map: TileMap, cell: Float, margin: Float, color: Color) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = margin * 0.5f
        this.color = android.graphics.Color.argb((color.alpha * 255).toInt(), (color.red * 255).toInt(), (color.green * 255).toInt(), (color.blue * 255).toInt())
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
    }
    val nc = drawContext.canvas.nativeCanvas
    for (x in 0 until map.cols) nc.drawText(MapForge.colName(x), margin + x * cell + cell / 2, margin * 0.7f, paint)
    for (y in 0 until map.rows) nc.drawText("${y + 1}", margin / 2, margin + y * cell + cell / 2 + paint.textSize / 3, paint)
}

@Composable
private fun MapPicker(map: TileMap, maps: List<TileMap>, onSwitch: (Long) -> Unit, onNew: () -> Unit, onEditInfo: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Row(Modifier.clickable { open = true }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f, fill = false)) {
                    Text(map.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${map.cols}×${map.rows} squares · ${map.feetPerSquare} ft each", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ExpandMore, "Switch map")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                maps.forEach { m -> DropdownMenuItem(text = { Text(m.name) }, onClick = { onSwitch(m.id); open = false }) }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("New map…") }, leadingIcon = { Icon(Icons.Outlined.Add, null) }, onClick = { open = false; onNew() })
            }
        }
        IconButton(onClick = onEditInfo) { Icon(Icons.Outlined.Edit, "Map details") }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Delete map") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskBox(
    selection: GridRect?,
    request: String,
    onRequest: (String) -> Unit,
    loading: Boolean,
    aiReady: Boolean,
    onSend: () -> Unit,
    onAddPlain: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel(if (selection == null) "Pick a spot" else "Add at ${selection.label}")
        if (selection == null) {
            Text("Tap a square on the map, or long-press and drag to mark an area.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = request,
            onValueChange = onRequest,
            placeholder = { Text("What should go here? Anything — a lighthouse, a river, a dragon's hoard…") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SUGGESTIONS.forEach { s -> SuggestionChip(onClick = { onRequest(s) }, label = { Text(s, maxLines = 1) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onSend, enabled = selection != null && request.isNotBlank() && !loading, modifier = Modifier.weight(1f).height(48.dp)) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else { Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(if (aiReady) "Ask Claude" else "Add") }
            }
            if (onAddPlain != null && aiReady) {
                OutlinedButton(onClick = onAddPlain, enabled = selection != null && request.isNotBlank() && !loading, modifier = Modifier.height(48.dp)) { Text("Just label it") }
            }
        }
        if (!aiReady) {
            Text(
                "Offline mode understands sizes like \"4 squares\" or \"3x2\" and \"make this water\". Add a Claude API key in Settings to ask for anything.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(req: com.jondude.tableforge.MapRequestState, onUndo: () -> Unit, onDismiss: () -> Unit) {
    val err = req.error != null
    Surface(
        color = if (err) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (err) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (req.loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Building it…", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            } else {
                Text(req.error ?: req.summary ?: "", style = MaterialTheme.typography.bodyMedium, color = if (err) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                if (req.canUndo) TextButton(onClick = onUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Undo") }
                TextButton(onClick = onDismiss) { Text("OK") }
            }
        }
    }
}

@Composable
private fun FeatureCard(f: MapFeature, onEdit: () -> Unit, onDelete: () -> Unit, onToIdea: () -> Unit, onAskAbout: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, featureColor(f).copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.icon, fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOf(f.kind, GridRect(f.x, f.y, f.w, f.h).label).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (f.description.isNotBlank()) LabeledBody(f.description)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onAskAbout) { Text("Change…") }
                TextButton(onClick = onToIdea) { Text("Save idea") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Remove from map", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun FeatureDialog(f: MapFeature, map: TileMap, onDismiss: () -> Unit, onSave: (MapFeature) -> Unit) {
    var name by remember { mutableStateOf(f.name) }
    var icon by remember { mutableStateOf(f.icon) }
    var kind by remember { mutableStateOf(f.kind) }
    var w by remember { mutableFloatStateOf(f.w.toFloat()) }
    var h by remember { mutableFloatStateOf(f.h.toFloat()) }
    var desc by remember { mutableStateOf(f.description) }
    FormDialog("Edit ${f.name}", onDismiss = onDismiss, onSave = {
        val ww = w.roundToInt().coerceIn(1, map.cols)
        val hh = h.roundToInt().coerceIn(1, map.rows)
        onSave(
            f.copy(
                name = name.trim(), icon = icon.trim(), kind = kind.trim(), w = ww, h = hh,
                x = f.x.coerceIn(0, map.cols - ww), y = f.y.coerceIn(0, map.rows - hh), description = desc.trim(),
            ),
        )
    }, saveEnabled = name.isNotBlank()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(icon, { icon = it.take(8) }, label = { Text("Icon") }, singleLine = true, modifier = Modifier.width(90.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Field(kind, { kind = it }, "Kind")
        Text("Width: ${w.roundToInt()} squares", style = MaterialTheme.typography.bodyMedium)
        Slider(w, { w = it }, valueRange = 1f..minOf(20, map.cols).toFloat(), steps = (minOf(20, map.cols) - 2).coerceAtLeast(0))
        Text("Height: ${h.roundToInt()} squares", style = MaterialTheme.typography.bodyMedium)
        Slider(h, { h = it }, valueRange = 1f..minOf(20, map.rows).toFloat(), steps = (minOf(20, map.rows) - 2).coerceAtLeast(0))
        Field(desc, { desc = it }, "Description", singleLine = false, minLines = 5)
    }
}

@Composable
private fun NewMapDialog(onDismiss: () -> Unit, onCreate: (String, Int, Int, Terrain, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var preset by remember { mutableIntStateOf(1) }
    var base by remember { mutableStateOf(Terrain.GRASS) }
    val presets = listOf(Triple("Small", 16, 12), Triple("Medium", 24, 16), Triple("Large", 36, 24), Triple("Huge", 48, 32))
    var feet by remember { mutableStateOf("5") }
    FormDialog("New map", onDismiss = onDismiss, onSave = {
        val p = presets[preset]
        onCreate(name.trim(), p.second, p.third, base, feet.toIntOrNull()?.coerceIn(1, 5280) ?: 5)
    }, saveEnabled = name.isNotBlank()) {
        Field(name, { name = it }, "Name", placeholder = "e.g. Saltmere Harbor")
        SectionLabel("Size")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            presets.forEachIndexed { i, p ->
                SegmentedButton(selected = preset == i, onClick = { preset = i }, shape = SegmentedButtonDefaults.itemShape(i, presets.size)) {
                    Text("${p.second}×${p.third}", fontSize = 12.sp)
                }
            }
        }
        Text(presets[preset].first + " grid", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Dropdown("Starting terrain", Terrain.entries, base, { it.label }, { base = it })
        Field(feet, { feet = it.filter(Char::isDigit) }, "Feet per square", placeholder = "5 for battle maps, 100+ for towns")
    }
}

// ---------- Gesture helpers ----------

private fun Modifier.pointerInputTransform(key: Any, onGesture: (Offset, Offset, Float) -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(key) {
            detectTransformGestures { centroid, pan, zoom, _ -> onGesture(centroid, pan, zoom) }
        },
    )

private fun Modifier.pointerInputTaps(key: Any, onTap: (Offset) -> Unit): Modifier =
    this.then(Modifier.pointerInput(key) { detectTapGestures(onTap = onTap) })

private fun Modifier.pointerInputLongDrag(key: Any, onStart: (Offset) -> Unit, onDrag: (Offset) -> Unit, onEnd: () -> Unit): Modifier =
    this.then(
        Modifier.pointerInput(key) {
            detectDragGesturesAfterLongPress(
                onDragStart = onStart,
                onDrag = { change, _ -> change.consume(); onDrag(change.position) },
                onDragEnd = onEnd,
                onDragCancel = onEnd,
            )
        },
    )
