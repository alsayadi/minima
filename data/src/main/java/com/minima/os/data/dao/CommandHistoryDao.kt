package com.minima.os.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minima.os.data.entity.CommandHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandHistoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: CommandHistoryEntity)

    @Query("UPDATE command_history SET useCount = useCount + 1, lastUsedAt = :now, intent = :intent, success = :success WHERE text = :text")
    suspend fun bump(text: String, intent: String?, success: Boolean, now: Long = System.currentTimeMillis())

    suspend fun upsert(text: String, intent: String?, success: Boolean) {
        insert(CommandHistoryEntity(text = text, intent = intent, success = success))
        bump(text, intent, success)
    }

    @Query("SELECT * FROM command_history ORDER BY lastUsedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<CommandHistoryEntity>

    @Query("SELECT * FROM command_history ORDER BY useCount DESC, lastUsedAt DESC LIMIT :limit")
    suspend fun getMostUsed(limit: Int = 20): List<CommandHistoryEntity>

    @Query("SELECT * FROM command_history ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<CommandHistoryEntity>>

    @Query("DELETE FROM command_history WHERE text = :text")
    suspend fun delete(text: String)

    @Query("DELETE FROM command_history")
    suspend fun clear()
}
