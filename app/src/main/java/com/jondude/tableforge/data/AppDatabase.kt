package com.jondude.tableforge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * Schema upgrades. Each SQL statement must match what Room generates for the entity exactly;
 * CI checks these strings against the exported schema (scripts/check_migrations.py).
 */
const val MONSTERS_TABLE_SQL =
    "CREATE TABLE IF NOT EXISTS `monsters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `campaignId` INTEGER NOT NULL, " +
        "`name` TEXT NOT NULL, `cr` TEXT NOT NULL, `data` TEXT NOT NULL, `hpNow` INTEGER, `createdAt` INTEGER NOT NULL)"
const val MONSTERS_INDEX_SQL = "CREATE INDEX IF NOT EXISTS `index_monsters_campaignId` ON `monsters` (`campaignId`)"

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(MONSTERS_TABLE_SQL)
        db.execSQL(MONSTERS_INDEX_SQL)
    }
}

@Database(
    entities = [
        Campaign::class, Idea::class, PlotBeat::class, Player::class, PartyItem::class,
        RollTable::class, TableEntry::class, MapNode::class, MapEdge::class,
        TileMap::class, MapFeature::class, Monster::class,
    ],
    version = 2,
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
    abstract fun monsters(): MonsterDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tableforge.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
