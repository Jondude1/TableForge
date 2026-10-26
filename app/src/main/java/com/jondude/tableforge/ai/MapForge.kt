package com.jondude.tableforge.ai

import com.jondude.tableforge.data.AiProvider
import com.jondude.tableforge.data.AiSettings
import com.jondude.tableforge.data.MapFeature
import com.jondude.tableforge.data.Terrain
import com.jondude.tableforge.data.TileMap
import org.json.JSONObject
import kotlin.math.sqrt

/** A rectangle of squares on a tile map, in grid coordinates. */
data class GridRect(val x: Int, val y: Int, val w: Int, val h: Int) {
    val label: String
        get() = if (w == 1 && h == 1) MapForge.cellName(x, y)
        else "${MapForge.cellName(x, y)}–${MapForge.cellName(x + w - 1, y + h - 1)} (${w}×$h)"

    companion object {
        fun spanning(ax: Int, ay: Int, bx: Int, by: Int) =
            GridRect(minOf(ax, bx), minOf(ay, by), kotlin.math.abs(ax - bx) + 1, kotlin.math.abs(ay - by) + 1)
    }
}

data class TerrainPaint(val terrain: Terrain, val cells: List<Pair<Int, Int>>)

/** Everything one request changes on a map. */
data class MapEdit(
    val summary: String,
    val features: List<MapFeature>,
    val terrain: List<TerrainPaint>,
    val removeIds: List<Long>,
)

/**
 * Turns a free-form request about a spot on the map ("a lighthouse that takes up 4 squares")
 * into concrete placements and terrain changes.
 */
object MapForge {

    fun colName(x: Int): String {
        var n = x
        val sb = StringBuilder()
        do {
            sb.insert(0, ('A' + n % 26))
            n = n / 26 - 1
        } while (n >= 0)
        return sb.toString()
    }

    fun cellName(x: Int, y: Int) = "${colName(x)}${y + 1}"

    private val SYSTEM = """You are a map-building assistant for a Dungeons & Dragons Dungeon Master. You edit a square-grid map.
Coordinates: x is the column (0 = left edge), y is the row (0 = top edge). Squares are labeled with a column letter and a 1-based row number, so "C4" means x=2, y=3. Each square is a fixed number of feet.
You can do anything the DM asks by combining three operations:
1. "features": things placed on the map. Each occupies a w-by-h block of whole squares with its top-left at (x, y). Size things realistically for the square size unless the DM gives a size. "4 squares" means a 2x2 block. Large or irregular things (a ship, a wall, a camp) can be split into several features.
2. "terrain": repaint squares with one of these terrain types: ${Terrain.entries.joinToString(", ") { it.key }}. Use this for rivers, roads, forests, floors, walls, lava, swamps and so on. Give squares as "rects" [[x,y,w,h],...] and/or "cells" [[x,y],...].
3. "remove": ids of existing features to delete when the DM asks to remove or replace something.
Place things at or around the square(s) the DM selected unless they say otherwise. Keep everything inside the map bounds. Avoid covering existing features unless asked.
Each feature needs: name, kind (short noun), icon (a single emoji that best depicts it), color (a hex like #A0522D that suits it), x, y, w, h, and description.
The description is table-ready plain text with short labeled lines separated by \n: "Read-aloud:" (1-2 vivid sentences), "Details:", and when it fits "Secret:" or "Encounter:".
Reply with JSON only: {"summary":"one short sentence of what you did","features":[...],"terrain":[{"terrain":"water","rects":[[x,y,w,h]],"cells":[[x,y]]}],"remove":[ids]}"""

    suspend fun build(
        settings: AiSettings,
        ctx: CampaignContext?,
        map: TileMap,
        features: List<MapFeature>,
        selection: GridRect,
        request: String,
    ): MapEdit {
        if (settings.provider == AiProvider.OFFLINE) return offline(map, selection, request)
        val user = buildString {
            if (ctx != null) {
                appendLine("Campaign: ${ctx.campaign.name}")
                if (ctx.campaign.premise.isNotBlank()) appendLine("Plot: ${ctx.campaign.premise.take(600)}")
                if (ctx.campaign.setting.isNotBlank()) appendLine("Setting: ${ctx.campaign.setting}")
                if (ctx.campaign.tone.isNotBlank()) appendLine("Tone: ${ctx.campaign.tone}")
                appendLine()
            }
            appendLine("MAP \"${map.name}\": ${map.cols} columns (x 0-${map.cols - 1}, A-${colName(map.cols - 1)}) by ${map.rows} rows (y 0-${map.rows - 1}, 1-${map.rows}). Each square is ${map.feetPerSquare} ft.")
            if (map.notes.isNotBlank()) appendLine("Map notes: ${map.notes}")
            appendLine("Terrain legend: " + Terrain.entries.joinToString(", ") { "${it.code}=${it.key}" })
            appendLine("Current terrain, one line per row from y=0:")
            for (y in 0 until map.rows) appendLine(map.terrain.substring(y * map.cols, (y + 1) * map.cols))
            if (features.isEmpty()) appendLine("Existing features: none")
            else {
                appendLine("Existing features:")
                features.forEach { appendLine("- id ${it.id}: ${it.name} (${it.kind}) at x=${it.x}, y=${it.y}, size ${it.w}x${it.h}") }
            }
            appendLine()
            appendLine("The DM selected ${if (selection.w == 1 && selection.h == 1) "square" else "the area"} ${selection.label}: x=${selection.x}, y=${selection.y}, w=${selection.w}, h=${selection.h}.")
            appendLine("DM's request: $request")
        }
        val raw = AiClient.complete(settings, SYSTEM, user, maxTokens = 4000)
        return parse(raw, map, features)
    }

    private fun parse(raw: String, map: TileMap, existing: List<MapFeature>): MapEdit {
        val text = raw.replace("```json", "").replace("```", "").trim()
        val json = runCatching { JSONObject(text.substring(text.indexOf('{'), text.lastIndexOf('}') + 1)) }.getOrNull()
            ?: throw AiException("The map answer wasn't in the expected format. Try rephrasing or tap again.")

        val feats = mutableListOf<MapFeature>()
        json.optJSONArray("features")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val w = o.optInt("w", 1).coerceIn(1, map.cols)
                val h = o.optInt("h", 1).coerceIn(1, map.rows)
                val x = o.optInt("x", 0).coerceIn(0, map.cols - w)
                val y = o.optInt("y", 0).coerceIn(0, map.rows - h)
                val name = o.optString("name").trim().ifBlank { o.optString("kind").trim() }.ifBlank { "Feature" }
                feats += MapFeature(
                    mapId = map.id, x = x, y = y, w = w, h = h,
                    name = name,
                    kind = o.optString("kind").trim(),
                    icon = o.optString("icon").trim().takeIf { it.isNotEmpty() && it.length <= 12 && !it.any(Char::isLetterOrDigit) } ?: iconFor(name + " " + o.optString("kind")),
                    color = o.optString("color").trim().takeIf { Regex("#[0-9a-fA-F]{6}").matches(it) } ?: "",
                    description = o.optString("description").trim().replace("\\n", "\n"),
                )
            }
        }

        val paints = mutableListOf<TerrainPaint>()
        json.optJSONArray("terrain")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val t = Terrain.fromKey(o.optString("terrain")) ?: continue
                val cells = mutableListOf<Pair<Int, Int>>()
                o.optJSONArray("rects")?.let { rs ->
                    for (j in 0 until rs.length()) {
                        val r = rs.optJSONArray(j) ?: continue
                        val rx = r.optInt(0); val ry = r.optInt(1); val rw = r.optInt(2, 1); val rh = r.optInt(3, 1)
                        for (yy in ry until ry + rh) for (xx in rx until rx + rw) cells += xx to yy
                    }
                }
                o.optJSONArray("cells")?.let { cs ->
                    for (j in 0 until cs.length()) {
                        val c = cs.optJSONArray(j) ?: continue
                        cells += c.optInt(0) to c.optInt(1)
                    }
                }
                val inBounds = cells.filter { (cx, cy) -> cx in 0 until map.cols && cy in 0 until map.rows }.distinct()
                if (inBounds.isNotEmpty()) paints += TerrainPaint(t, inBounds)
            }
        }

        val validIds = existing.map { it.id }.toSet()
        val removes = mutableListOf<Long>()
        json.optJSONArray("remove")?.let { arr -> for (i in 0 until arr.length()) arr.optLong(i, -1L).takeIf { it in validIds }?.let { removes += it } }

        if (feats.isEmpty() && paints.isEmpty() && removes.isEmpty()) {
            throw AiException(json.optString("summary").ifBlank { "Nothing to add for that request. Try describing it differently." })
        }
        return MapEdit(json.optString("summary").trim(), feats, paints, removes)
    }

    /** Applies terrain paints to a terrain string. */
    fun paint(map: TileMap, paints: List<TerrainPaint>): String {
        val chars = map.terrain.toCharArray()
        paints.forEach { p -> p.cells.forEach { (x, y) -> chars[y * map.cols + x] = p.terrain.code } }
        return String(chars)
    }

    fun blankTerrain(cols: Int, rows: Int, base: Terrain) = base.code.toString().repeat(cols * rows)

    // ---------- Offline fallback: simple size + keyword parsing ----------

    private val sizeWxH = Regex("(\\d+)\\s*[x×by]+\\s*(\\d+)", RegexOption.IGNORE_CASE)
    private val sizeN = Regex("(\\d+)\\s*(squares|tiles|spaces|cells)", RegexOption.IGNORE_CASE)
    private val words = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "eight" to 8, "nine" to 9, "ten" to 10, "twelve" to 12, "sixteen" to 16,
    )

    private fun offline(map: TileMap, sel: GridRect, request: String): MapEdit {
        var req = request.trim()
        words.forEach { (k, v) -> req = req.replace(Regex("\\b$k\\b(?=\\s*(squares|tiles|spaces|cells))", RegexOption.IGNORE_CASE), v.toString()) }
        val lower = req.lowercase()

        // "make this water", "fill with forest", "paint it lava"
        val terrainVerb = Regex("^(make|turn|fill|paint|cover|change)\\b").containsMatchIn(lower)
        if (terrainVerb) {
            val t = Terrain.entries.firstOrNull { lower.contains(it.key) || lower.contains(it.label.lowercase()) }
                ?: lower.split(" ").firstNotNullOfOrNull { Terrain.fromKey(it) }
            if (t != null) {
                val cells = buildList { for (y in sel.y until sel.y + sel.h) for (x in sel.x until sel.x + sel.w) add(x to y) }
                return MapEdit("Painted ${sel.label} as ${t.label.lowercase()}.", emptyList(), listOf(TerrainPaint(t, cells)), emptyList())
            }
        }

        var w = sel.w
        var h = sel.h
        sizeWxH.find(req)?.let { m ->
            w = m.groupValues[1].toInt(); h = m.groupValues[2].toInt()
        } ?: sizeN.find(req)?.let { m ->
            val n = m.groupValues[1].toInt().coerceAtLeast(1)
            val (a, b) = blockFor(n); w = a; h = b
        }
        w = w.coerceIn(1, map.cols); h = h.coerceIn(1, map.rows)
        val x = sel.x.coerceIn(0, map.cols - w)
        val y = sel.y.coerceIn(0, map.rows - h)

        val name = req
            .replace(sizeWxH, "").replace(sizeN, "")
            .replace(Regex("\\b(that|which)\\s+(takes|take|is|covers|fills)\\s+(up)?\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(add|place|put|build|draw|make|create)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^(a|an|the|some)\\s+", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(here|there)$", RegexOption.IGNORE_CASE), "")
            .trim(' ', ',', '.')
            .replaceFirstChar { it.uppercase() }
            .ifBlank { "Feature" }
        val feature = MapFeature(
            mapId = map.id, x = x, y = y, w = w, h = h, name = name, kind = name.lowercase(),
            icon = iconFor(lower), description = "Details: $request",
        )
        return MapEdit("Placed $name at ${GridRect(x, y, w, h).label}.", listOf(feature), emptyList(), emptyList())
    }

    /** Most square-ish w x h block with exactly n squares (4 -> 2x2, 6 -> 3x2). */
    fun blockFor(n: Int): Pair<Int, Int> {
        var best = n to 1
        for (a in 1..sqrt(n.toDouble()).toInt()) if (n % a == 0) best = (n / a) to a
        return best
    }

    /** Emoji token for a feature, by keyword. Used offline and when the model gives no icon. */
    fun iconFor(text: String): String {
        val t = text.lowercase()
        val table = listOf(
            "lighthouse" to "\uD83D\uDDFC", "tower" to "\uD83D\uDDFC", "castle" to "\uD83C\uDFF0", "keep" to "\uD83C\uDFF0", "fort" to "\uD83C\uDFF0",
            "temple" to "\u26EA", "shrine" to "\u26E9\uFE0F", "church" to "\u26EA", "chapel" to "\u26EA",
            "tavern" to "\uD83C\uDF7A", "inn" to "\uD83C\uDF7A", "shop" to "\uD83D\uDED2", "market" to "\uD83D\uDED2", "stall" to "\uD83D\uDED2",
            "smith" to "\u2692\uFE0F", "forge" to "\u2692\uFE0F",
            "ship" to "\u26F5", "boat" to "\u26F5", "dock" to "\u2693", "pier" to "\u2693", "bridge" to "\uD83C\uDF09",
            "tree" to "\uD83C\uDF32", "grove" to "\uD83C\uDF32", "forest" to "\uD83C\uDF32", "rock" to "\uD83E\uDEA8", "boulder" to "\uD83E\uDEA8",
            "mountain" to "\u26F0\uFE0F", "pond" to "\uD83D\uDCA7", "lake" to "\uD83D\uDCA7", "fountain" to "\u26F2",
            "camp" to "\u26FA", "tent" to "\u26FA", "fire" to "\uD83D\uDD25", "cave" to "\uD83D\uDD73\uFE0F", "ruin" to "\uD83C\uDFDA\uFE0F",
            "dragon" to "\uD83D\uDC09", "goblin" to "\uD83D\uDC7A", "orc" to "\uD83D\uDC79", "lair" to "\uD83D\uDC09", "spider" to "\uD83D\uDD77\uFE0F",
            "grave" to "\uD83E\uDEA6", "crypt" to "\uD83D\uDC80", "skull" to "\uD83D\uDC80", "chest" to "\uD83D\uDCB0", "treasure" to "\uD83D\uDCB0",
            "door" to "\uD83D\uDEAA", "gate" to "\uD83D\uDEAA", "stair" to "\uD83E\uDE9C", "ladder" to "\uD83E\uDE9C", "statue" to "\uD83D\uDDFF",
            "well" to "\uD83E\uDEA3", "trap" to "\u26A0\uFE0F", "flag" to "\uD83D\uDEA9", "altar" to "\uD83D\uDD6F\uFE0F", "library" to "\uD83D\uDCDA",
            "house" to "\uD83C\uDFE0", "hut" to "\uD83D\uDED6", "cottage" to "\uD83C\uDFE1", "shack" to "\uD83D\uDED6", "farm" to "\uD83C\uDF3E", "barn" to "\uD83C\uDF3E",
            "mill" to "\uD83C\uDF2C\uFE0F", "wizard" to "\uD83E\uDDD9", "guard" to "\uD83D\uDEE1\uFE0F",
        )
        return table.firstOrNull { (k, _) -> k in t }?.second ?: "\uD83C\uDFE0"
    }
}
