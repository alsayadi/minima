package com.minima.os.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ActionRecord(
    val id: String,
    val taskId: String,
    val stepId: String,
    val capabilityId: String,
    val action: String,
    val params: Map<String, String> = emptyMap(),
    val outcome: ActionOutcome,
    val approvalLevel: ApprovalLevel,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long? = null,
    val error: String? = null
)

@Serializable
enum class ActionOutcome {
    SUCCESS,
    FAILED,
    REJECTED,
    BLOCKED,
    CANCELLED
}
