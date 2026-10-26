package com.jondude.tableforge.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A campaign: everything else hangs off one of these. */
@Entity(tableName = "campaigns")
data class Campaign(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val premise: String = "",
    val tone: String = "",
    val setting: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Kinds of idea the Forge can generate or the DM can write by hand. */
enum class IdeaType(val label: String, val singular: String) {
    NPC("NPCs", "NPC"),
    EVENT("Events", "event"),
    LOCATION("Locations", "location"),
    DUNGEON_ROOM("Dungeon rooms", "dungeon room"),
    MAGIC_ITEM("Magic items", "magic item"),
    STORY_BEAT("Story beats", "story beat"),
    NOTE("Notes", "note");

    companion object {
        fun from(name: String): IdeaType = entries.firstOrNull { it.name == name } ?: NOTE
        val generatable = listOf(NPC, EVENT, LOCATION, DUNGEON_ROOM, MAGIC_ITEM, STORY_BEAT)
    }
}

/** A saved idea — generated or written by the DM. */
@Entity(
    tableName = "ideas",
    foreignKeys = [ForeignKey(Campaign::class, ["id"], ["campaignId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("campaignId")],
)
data class Idea(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val type: String,
    val title: String,
    val body: String,
    val tags: String = "",
    val isOwn: Boolean = false,
    val favorite: Boolean = false,
    val used: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

val Idea.ideaType: IdeaType get() = IdeaType.from(type)

/** One beat in the campaign's overall plot outline. */
@Entity(
    tableName = "plot_beats",
    foreignKeys = [ForeignKey(Campaign::class, ["id"], ["campaignId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("campaignId")],
)
data class PlotBeat(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val position: Double,
    val act: String = "",
    val title: String,
    val summary: String = "",
    val status: String = BeatStatus.PLANNED.name,
)

enum class BeatStatus(val label: String) {
    PLANNED("Planned"), IN_PLAY("In play"), DONE("Done");

    companion object {
        fun from(name: String) = entries.firstOrNull { it.name == name } ?: PLANNED
    }
}

/** A player at the table and their character. */
@Entity(
    tableName = "players",
    foreignKeys = [ForeignKey(Campaign::class, ["id"], ["campaignId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("campaignId")],
)
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val playerName: String,
    val characterName: String,
    val characterClass: String = "",
)

/** A magic item the party owns. holderId == null means it's in the party stash. */
@Entity(
    tableName = "party_items",
    foreignKeys = [ForeignKey(Campaign::class, ["id"], ["campaignId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("campaignId"), Index("holderId")],
)
data class PartyItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val holderId: Long? = null,
    val name: String,
    val rarity: String = "Uncommon",
    val requiresAttunement: Boolean = false,
    val attuned: Boolean = false,
    val charges: String = "",
    val notes: String = "",
    val acquiredAt: String = "",
)

val RARITIES = listOf("Common", "Uncommon", "Rare", "Very rare", "Legendary", "Artifact")

/** A rollable table such as a d12 tavern rumors table. campaignId null = shared across campaigns. */
@Entity(tableName = "roll_tables")
data class RollTable(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long? = null,
    val name: String,
    val dieSides: Int,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "table_entries",
    foreignKeys = [ForeignKey(RollTable::class, ["id"], ["tableId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tableId")],
)
data class TableEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableId: Long,
    val minRoll: Int,
    val maxRoll: Int,
    val text: String,
)

val DICE = listOf(4, 6, 8, 10, 12, 20, 100)

/** A node on the concept map. */
@Entity(
    tableName = "map_nodes",
    foreignKeys = [ForeignKey(Campaign::class, ["id"], ["campaignId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("campaignId")],
)
data class MapNode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val label: String,
    val note: String = "",
    val kind: String = IdeaType.NOTE.name,
    val x: Float,
    val y: Float,
    val ideaId: Long? = null,
)

/** A link between two concept-map nodes. */
@Entity(
    tableName = "map_edges",
    foreignKeys = [
        ForeignKey(MapNode::class, ["id"], ["fromId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(MapNode::class, ["id"], ["toId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("fromId"), Index("toId"), Index("campaignId")],
)
data class MapEdge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val fromId: Long,
    val toId: Long,
    val label: String = "",
)

/** A square-grid map (battle map, town, region). Terrain is one char per tile, row-major. */
@Entity(
    tableName = "tile_maps",
    foreignKeys = [ForeignKey(Campaign::class, ["id"], ["campaignId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("campaignId")],
)
data class TileMap(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val name: String,
    val cols: Int,
    val rows: Int,
    val terrain: String,
    val feetPerSquare: Int = 5,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

fun TileMap.terrainAt(x: Int, y: Int): Terrain =
    if (x in 0 until cols && y in 0 until rows) Terrain.fromCode(terrain.getOrElse(y * cols + x) { 'g' }) else Terrain.GRASS

/** Something placed on a tile map, occupying a w x h block of squares starting at (x, y). */
@Entity(
    tableName = "map_features",
    foreignKeys = [ForeignKey(TileMap::class, ["id"], ["mapId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("mapId")],
)
data class MapFeature(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mapId: Long,
    val x: Int,
    val y: Int,
    val w: Int = 1,
    val h: Int = 1,
    val name: String,
    val kind: String = "",
    val icon: String = "",
    val color: String = "",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** Terrain palette. The code is what's stored in [TileMap.terrain]. */
enum class Terrain(val code: Char, val label: String, val argb: Long) {
    GRASS('g', "Grass", 0xFF3E5230),
    FOREST('f', "Forest", 0xFF27391F),
    DIRT('d', "Dirt / road", 0xFF6B5236),
    SAND('s', "Sand", 0xFFB49A64),
    WATER('w', "Shallow water", 0xFF35617A),
    DEEP('D', "Deep water", 0xFF1E3B52),
    SWAMP('m', "Swamp", 0xFF3D4A2E),
    STONE('r', "Stone floor", 0xFF5A554E),
    COBBLE('c', "Cobblestone", 0xFF6E655A),
    WOOD('o', "Wood floor", 0xFF7A5634),
    WALL('#', "Wall", 0xFF26221E),
    MOUNTAIN('^', "Mountain / rock", 0xFF6F6A64),
    SNOW('n', "Snow / ice", 0xFFC9D3D8),
    LAVA('l', "Lava", 0xFFB5432A),
    VOID('v', "Pit / void", 0xFF0B0907);

    val key: String get() = name.lowercase()

    companion object {
        fun fromCode(c: Char) = entries.firstOrNull { it.code == c } ?: GRASS
        fun fromKey(k: String): Terrain? {
            val s = k.trim().lowercase().replace(' ', '_')
            return entries.firstOrNull { it.key == s || it.label.lowercase() == k.trim().lowercase() }
                ?: when {
                    "tree" in s || "wood" == s -> FOREST
                    "road" in s || "path" in s -> DIRT
                    "river" in s || "pond" in s || "lake" in s -> WATER
                    "ocean" in s || "sea" in s -> DEEP
                    "rock" in s || "cliff" in s || "hill" in s -> MOUNTAIN
                    "floor" in s -> STONE
                    "ice" in s -> SNOW
                    else -> null
                }
        }
    }
}
