package com.minima.os.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per command submitted. De-duplicated by text (latest timestamp wins). */
@Entity(tableName = "command_history")
data class CommandHistoryEntity(
    @PrimaryKey val text: String,
    val intent: String? = null,          // IntentType name at classification
    val success: Boolean = true,
    val useCount: Int = 1,
    val lastUsedAt: Long = System.currentTimeMillis()
)
