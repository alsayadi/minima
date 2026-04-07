package com.minima.os.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.minima.os.data.entity.ActionRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Append-only DAO. No @Update, no @Delete.
 * Every action that passes through the system gets an immutable record.
 */
@Dao
interface ActionDao {

    @Insert
    suspend fun insert(record: ActionRecordEntity)

    @Query("SELECT * FROM action_log WHERE taskId = :taskId ORDER BY timestamp ASC")
    fun observeByTask(taskId: String): Flow<List<ActionRecordEntity>>

    @Query("SELECT * FROM action_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<ActionRecordEntity>>

    @Query("SELECT COUNT(*) FROM action_log")
    suspend fun count(): Int
}
