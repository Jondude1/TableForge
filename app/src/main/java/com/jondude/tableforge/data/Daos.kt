package com.jondude.tableforge.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {
    @Query("SELECT * FROM campaigns ORDER BY createdAt")
    fun all(): Flow<List<Campaign>>

    @Query("SELECT * FROM campaigns WHERE id = :id")
    fun observe(id: Long): Flow<Campaign?>

    @Query("SELECT * FROM campaigns WHERE id = :id")
    suspend fun get(id: Long): Campaign?

    @Query("SELECT COUNT(*) FROM campaigns")
    suspend fun count(): Int

    @Query("SELECT * FROM campaigns ORDER BY createdAt LIMIT 1")
    suspend fun first(): Campaign?

    @Insert
    suspend fun insert(c: Campaign): Long

    @Update
    suspend fun update(c: Campaign)

    @Delete
    suspend fun delete(c: Campaign)
}

@Dao
interface IdeaDao {
    @Query("SELECT * FROM ideas WHERE campaignId = :campaignId ORDER BY favorite DESC, createdAt DESC")
    fun forCampaign(campaignId: Long): Flow<List<Idea>>

    @Query("SELECT * FROM ideas WHERE campaignId = :campaignId ORDER BY createdAt DESC")
    suspend fun listForCampaign(campaignId: Long): List<Idea>

    @Upsert
    suspend fun upsert(i: Idea): Long

    @Delete
    suspend fun delete(i: Idea)
}

@Dao
interface PlotDao {
    @Query("SELECT * FROM plot_beats WHERE campaignId = :campaignId ORDER BY position")
    fun forCampaign(campaignId: Long): Flow<List<PlotBeat>>

    @Query("SELECT * FROM plot_beats WHERE campaignId = :campaignId ORDER BY position")
    suspend fun listForCampaign(campaignId: Long): List<PlotBeat>

    @Query("SELECT MAX(position) FROM plot_beats WHERE campaignId = :campaignId")
    suspend fun maxPosition(campaignId: Long): Double?

    @Upsert
    suspend fun upsert(b: PlotBeat): Long

    @Update
    suspend fun updateAll(beats: List<PlotBeat>)

    @Delete
    suspend fun delete(b: PlotBeat)
}

@Dao
abstract class PartyDao {
    @Query("SELECT * FROM players WHERE campaignId = :campaignId ORDER BY id")
    abstract fun players(campaignId: Long): Flow<List<Player>>

    @Query("SELECT * FROM party_items WHERE campaignId = :campaignId ORDER BY name COLLATE NOCASE")
    abstract fun items(campaignId: Long): Flow<List<PartyItem>>

    @Upsert
    abstract suspend fun upsertPlayer(p: Player): Long

    @Query("UPDATE party_items SET holderId = NULL, attuned = 0 WHERE holderId = :playerId")
    abstract suspend fun moveToStash(playerId: Long)

    @Delete
    abstract suspend fun deletePlayerRow(p: Player)

    @Transaction
    open suspend fun deletePlayer(p: Player) {
        moveToStash(p.id)
        deletePlayerRow(p)
    }

    @Upsert
    abstract suspend fun upsertItem(i: PartyItem): Long

    @Delete
    abstract suspend fun deleteItem(i: PartyItem)
}

@Dao
abstract class TableDao {
    @Query("SELECT * FROM roll_tables WHERE campaignId IS NULL OR campaignId = :campaignId ORDER BY name COLLATE NOCASE")
    abstract fun tables(campaignId: Long): Flow<List<RollTable>>

    @Query("SELECT * FROM table_entries WHERE tableId = :tableId ORDER BY minRoll")
    abstract fun entries(tableId: Long): Flow<List<TableEntry>>

    @Query("SELECT * FROM table_entries WHERE tableId = :tableId ORDER BY minRoll")
    abstract suspend fun entriesNow(tableId: Long): List<TableEntry>

    @Upsert
    abstract suspend fun upsertTable(t: RollTable): Long

    @Delete
    abstract suspend fun deleteTable(t: RollTable)

    @Query("DELETE FROM roll_tables WHERE campaignId = :campaignId")
    abstract suspend fun deleteForCampaign(campaignId: Long)

    @Query("DELETE FROM table_entries WHERE tableId = :tableId")
    abstract suspend fun clearEntries(tableId: Long)

    @Insert
    abstract suspend fun insertEntries(e: List<TableEntry>)

    @Transaction
    open suspend fun replaceEntries(tableId: Long, e: List<TableEntry>) {
        clearEntries(tableId)
        insertEntries(e.map { it.copy(id = 0, tableId = tableId) })
    }
}

@Dao
interface MapDao {
    @Query("SELECT * FROM map_nodes WHERE campaignId = :campaignId")
    fun nodes(campaignId: Long): Flow<List<MapNode>>

    @Query("SELECT * FROM map_edges WHERE campaignId = :campaignId")
    fun edges(campaignId: Long): Flow<List<MapEdge>>

    @Upsert
    suspend fun upsertNode(n: MapNode): Long

    @Query("UPDATE map_nodes SET x = :x, y = :y WHERE id = :id")
    suspend fun moveNode(id: Long, x: Float, y: Float)

    @Delete
    suspend fun deleteNode(n: MapNode)

    @Query("SELECT COUNT(*) FROM map_edges WHERE (fromId = :a AND toId = :b) OR (fromId = :b AND toId = :a)")
    suspend fun edgeCount(a: Long, b: Long): Int

    @Insert
    suspend fun insertEdge(e: MapEdge): Long

    @Upsert
    suspend fun upsertEdge(e: MapEdge): Long

    @Delete
    suspend fun deleteEdge(e: MapEdge)
}

@Dao
interface TileMapDao {
    @Query("SELECT * FROM tile_maps WHERE campaignId = :campaignId ORDER BY createdAt")
    fun maps(campaignId: Long): Flow<List<TileMap>>

    @Query("SELECT * FROM map_features WHERE mapId = :mapId ORDER BY (w * h) DESC, createdAt")
    fun features(mapId: Long): Flow<List<MapFeature>>

    @Query("SELECT * FROM tile_maps WHERE id = :id")
    suspend fun get(id: Long): TileMap?

    @Query("SELECT * FROM map_features WHERE mapId = :mapId")
    suspend fun featuresNow(mapId: Long): List<MapFeature>

    @Upsert
    suspend fun upsertMap(m: TileMap): Long

    @Query("UPDATE tile_maps SET terrain = :terrain WHERE id = :id")
    suspend fun setTerrain(id: Long, terrain: String)

    @Delete
    suspend fun deleteMap(m: TileMap)

    @Upsert
    suspend fun upsertFeature(f: MapFeature): Long

    @Insert
    suspend fun insertFeatures(f: List<MapFeature>)

    @Query("DELETE FROM map_features WHERE id IN (:ids) AND mapId = :mapId")
    suspend fun deleteFeatures(mapId: Long, ids: List<Long>)

    @Delete
    suspend fun deleteFeature(f: MapFeature)
}
