package com.minima.os.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ActionStep(
    val id: String,
    val taskId: String,
    val capabilityId: String,
    val action: String,
    val params: Map<String, String> = emptyMap(),
    val approvalLevel: ApprovalLevel = ApprovalLevel.CONFIRM,
    val status: StepStatus = StepStatus.PENDING,
    val result: StepResult? = null
)

@Serializable
enum class StepStatus {
    PENDING,
    AWAITING_APPROVAL,
    APPROVED,
    EXECUTING,
    COMPLETED,
    FAILED,
    REJECTED
}

@Serializable
data class StepResult(
    val success: Boolean,
    val data: Map<String, String> = emptyMap(),
    val error: String? = null
)
