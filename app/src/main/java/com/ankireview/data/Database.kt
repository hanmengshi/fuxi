package com.ankireview.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val path: String,        // WebDAV path as unique ID
    val name: String,
    val interval: Int    = 0,
    val ease: Double     = 2.5,
    val reps: Int        = 0,
    val due: String      = "2000-01-01"
)

@Entity(tableName = "heatmap")
data class HeatmapEntity(
    @PrimaryKey val date: String,
    val count: Int = 0
)

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE path = :path")
    suspend fun getCard(path: String): CardEntity?

    @Query("SELECT * FROM cards WHERE due <= :today")
    suspend fun getDueCards(today: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE due > :today ORDER BY RANDOM() LIMIT :limit")
    suspend fun getFutureCards(today: String, limit: Int): List<CardEntity>

    @Query("SELECT * FROM cards WHERE reps = 0 ORDER BY RANDOM() LIMIT :limit")
    suspend fun getNewCards(limit: Int): List<CardEntity>

    @Query("SELECT COUNT(*) FROM cards WHERE due <= :today")
    fun getDueCount(today: String): Flow<Int>

    @Upsert
    suspend fun upsertCard(card: CardEntity)

    @Query("SELECT * FROM heatmap WHERE date >= :from ORDER BY date ASC")
    suspend fun getHeatmapRange(from: String): List<HeatmapEntity>

    @Query("SELECT * FROM heatmap WHERE date = :date")
    suspend fun getHeatmap(date: String): HeatmapEntity?

    @Upsert
    suspend fun upsertHeatmap(h: HeatmapEntity)
}

@Database(entities = [CardEntity::class, HeatmapEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
}
