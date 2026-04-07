package com.minima.os.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.minima.os.core.model.TaskState

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val input: String,
    val state: String,
    val intentType: String? = null,
    val intentParams: String? = null, // JSON
    val stepsJson: String? = null, // JSON array
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val error: String? = null
)
