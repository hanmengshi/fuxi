package com.ankireview.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey val path: String,
    val name: String,
    val interval: Int    = 0,
    val ease: Double     = 2.5,
    val reps: Int        = 0,
    val due: String      = "2000-01-01",
    val lapses: Int      = 0
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

    @Query("SELECT COUNT(*) FROM cards WHERE due <= :today")
    fun getDueCount(today: String): Flow<Int>

    @Query("SELECT * FROM heatmap WHERE date >= :from ORDER BY date ASC")
    suspend fun getHeatmapRange(from: String): List<HeatmapEntity>

    @Query("SELECT * FROM heatmap WHERE date = :date")
    suspend fun getHeatmap(date: String): HeatmapEntity?

    @Upsert
    suspend fun upsertCard(card: CardEntity)

    @Upsert
    suspend fun upsertHeatmap(h: HeatmapEntity)
}

@Database(
    entities = [CardEntity::class, HeatmapEntity::class],
    version  = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
}
