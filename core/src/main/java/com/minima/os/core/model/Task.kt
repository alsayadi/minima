package com.minima.os.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    val input: String,
    val state: TaskState = TaskState.CREATED,
    val intent: ClassifiedIntent? = null,
    val steps: List<ActionStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val error: String? = null
)

@Serializable
enum class TaskState {
    CREATED,
    CLASSIFYING,
    PLANNING,
    EXECUTING,
    AWAITING_APPROVAL,
    COMPLETED,
    FAILED,
    CANCELLED
}
