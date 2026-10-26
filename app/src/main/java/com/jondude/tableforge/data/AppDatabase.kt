package com.jondude.tableforge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Campaign::class, Idea::class, PlotBeat::class, Player::class, PartyItem::class,
        RollTable::class, TableEntry::class, MapNode::class, MapEdge::class,
        TileMap::class, MapFeature::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun campaigns(): CampaignDao
    abstract fun ideas(): IdeaDao
    abstract fun plot(): PlotDao
    abstract fun party(): PartyDao
    abstract fun tables(): TableDao
    abstract fun map(): MapDao
    abstract fun tiles(): TileMapDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tableforge.db")
                .build()
                .also { instance = it }
        }
    }
}
