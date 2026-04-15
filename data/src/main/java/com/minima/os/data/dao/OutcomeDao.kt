package com.minima.os.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.minima.os.data.entity.TaskOutcomeEntity
import com.minima.os.data.entity.TuningChangeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutcomeDao {
    @Insert
    suspend fun insert(outcome: TaskOutcomeEntity): Long

    @Query("SELECT * FROM task_outcomes WHERE timestamp > :since ORDER BY timestamp ASC")
    suspend fun getSince(since: Long): List<TaskOutcomeEntity>

    @Query("SELECT * FROM task_outcomes ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 200): List<TaskOutcomeEntity>

    @Query("SELECT COUNT(*) FROM task_outcomes WHERE timestamp > :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM task_outcomes")
    suspend fun countAll(): Int

    @Query("SELECT * FROM task_outcomes ORDER BY timestamp DESC LIMIT 50")
    fun observeRecent(): Flow<List<TaskOutcomeEntity>>
}

@Dao
interface TuningChangeDao {
    @Insert
    suspend fun insert(change: TuningChangeEntity): Long

    @androidx.room.Update
    suspend fun update(change: TuningChangeEntity)

    @Query("SELECT * FROM tuning_changes ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<TuningChangeEntity>

    @Query("SELECT * FROM tuning_changes ORDER BY timestamp DESC LIMIT 20")
    fun observeRecent(): Flow<List<TuningChangeEntity>>

    /** Count un-applied proposals (for HUMAN_QUEUE badge). */
    @Query("SELECT COUNT(*) FROM tuning_changes WHERE applied = 0")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM tuning_changes WHERE applied = 0")
    fun observePendingCount(): Flow<Int>

    /** Mark a single proposal applied in place (no duplicate row). */
    @Query("UPDATE tuning_changes SET applied = 1, timestamp = :now WHERE id = :id")
    suspend fun markApplied(id: Long, now: Long = System.currentTimeMillis()): Int
}
