package com.example.habit.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "coach_messages", foreignKeys = [ForeignKey(entity = HabitEntity::class,
    parentColumns = ["id"], childColumns = ["habit_id"], onDelete = ForeignKey.CASCADE)], indices = [Index("habit_id")])
data class CoachMessageEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "habit_id") val habitId: Long, val role: String, val text: String,
    @ColumnInfo(name = "created_at") val createdAt: Long, @ColumnInfo(name = "exchange_id") val exchangeId: String)

@Entity(tableName = "coach_caches", foreignKeys = [ForeignKey(entity = HabitEntity::class,
    parentColumns = ["id"], childColumns = ["habit_id"], onDelete = ForeignKey.CASCADE)], indices = [Index("habit_id")])
data class CoachCacheEntity(@PrimaryKey val id: String, @ColumnInfo(name = "habit_id") val habitId: Long,
    val request: String, val response: String, val baseline: String, @ColumnInfo(name = "catalog_sha256") val catalogSha256: String,
    @ColumnInfo(name = "created_at") val createdAt: Long)

@Entity(tableName = "coach_actions", foreignKeys = [ForeignKey(entity = HabitEntity::class,
    parentColumns = ["id"], childColumns = ["habit_id"], onDelete = ForeignKey.CASCADE)], indices = [Index("habit_id")])
data class CoachActionEntity(@PrimaryKey val id: String, @ColumnInfo(name = "habit_id") val habitId: Long,
    @ColumnInfo(name = "strategy_id") val strategyId: String, val inverse: String,
    @ColumnInfo(name = "applied_at") val appliedAt: Long, val status: String, val confirmation: String)

/** Local revision identities are operational metadata, never outbound fields. */
@Entity(tableName = "habit_field_versions", primaryKeys = ["habit_id", "field"], foreignKeys = [ForeignKey(
    entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habit_id"], onDelete = ForeignKey.CASCADE)])
data class HabitFieldVersion(@ColumnInfo(name = "habit_id") val habitId: Long, val field: String, val revision: Long)

@Dao
interface CoachDao {
    @Insert suspend fun message(value: CoachMessageEntity)
    @Insert suspend fun cache(value: CoachCacheEntity)
    @Update suspend fun updateCache(value: CoachCacheEntity)
    @Upsert suspend fun action(value: CoachActionEntity)
    @Query("SELECT * FROM coach_messages WHERE habit_id = :id ORDER BY id") fun messages(id: Long): Flow<List<CoachMessageEntity>>
    @Query("SELECT * FROM coach_caches WHERE habit_id = :id AND response != '' ORDER BY created_at DESC, id DESC LIMIT 1") fun latestCache(id: Long): Flow<CoachCacheEntity?>
    @Query("SELECT * FROM coach_caches WHERE id = :id") suspend fun cacheById(id: String): CoachCacheEntity?
    @Query("SELECT * FROM coach_actions WHERE id = :id") suspend fun actionById(id: String): CoachActionEntity?
    @Query("SELECT * FROM coach_actions WHERE habit_id = :id ORDER BY applied_at, id") fun actions(id: Long): Flow<List<CoachActionEntity>>
    @Query("DELETE FROM coach_messages WHERE habit_id = :id AND id NOT IN (SELECT id FROM coach_messages WHERE habit_id = :id ORDER BY id DESC LIMIT 50)") suspend fun trim(id: Long)
    @Query("SELECT * FROM coach_messages ORDER BY id") suspend fun allMessages(): List<CoachMessageEntity>
    @Query("SELECT * FROM coach_caches ORDER BY created_at, id") suspend fun allCaches(): List<CoachCacheEntity>
    @Query("SELECT * FROM coach_actions ORDER BY applied_at, id") suspend fun allActions(): List<CoachActionEntity>
    @Query("DELETE FROM coach_messages") suspend fun clearMessages()
    @Query("DELETE FROM coach_caches") suspend fun clearCaches()
    @Query("DELETE FROM coach_actions") suspend fun clearActions()
    @Transaction suspend fun clear() { clearMessages(); clearCaches(); clearActions() }
}
