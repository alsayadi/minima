package com.minima.os.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "action_log")
data class ActionRecordEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val stepId: String,
    val capabilityId: String,
    val action: String,
    val paramsJson: String, // JSON
    val outcome: String,
    val approvalLevel: String,
    val timestamp: Long,
    val durationMs: Long? = null,
    val error: String? = null
)
