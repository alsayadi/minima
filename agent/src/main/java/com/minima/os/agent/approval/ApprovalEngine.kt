package com.minima.os.agent.approval

import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.ApprovalLevel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class ApprovalRequest(
    val step: ActionStep,
    val description: String
)

data class ApprovalDecision(
    val approved: Boolean,
    val level: ApprovalLevel,
    val reason: String? = null
)

@Singleton
class ApprovalEngine @Inject constructor() {

    private val _pendingApprovals = MutableSharedFlow<ApprovalRequest>(extraBufferCapacity = 1)
    val pendingApprovals: SharedFlow<ApprovalRequest> = _pendingApprovals.asSharedFlow()

    private val _decisions = MutableSharedFlow<ApprovalDecision>(extraBufferCapacity = 1)

    suspend fun evaluate(step: ActionStep): ApprovalDecision {
        return when (step.approvalLevel) {
            ApprovalLevel.AUTO -> {
                ApprovalDecision(approved = true, level = ApprovalLevel.AUTO)
            }

            ApprovalLevel.NOTIFY -> {
                ApprovalDecision(approved = true, level = ApprovalLevel.NOTIFY)
            }

            ApprovalLevel.CONFIRM -> {
                _pendingApprovals.emit(
                    ApprovalRequest(
                        step = step,
                        description = formatDescription(step)
                    )
                )
                _decisions.first()
            }

            ApprovalLevel.BLOCK -> {
                ApprovalDecision(
                    approved = false,
                    level = ApprovalLevel.BLOCK,
                    reason = "This action is blocked for safety"
                )
            }
        }
    }

    suspend fun submitDecision(approved: Boolean, reason: String? = null) {
        _decisions.emit(
            ApprovalDecision(
                approved = approved,
                level = ApprovalLevel.CONFIRM,
                reason = reason
            )
        )
    }

    private fun formatDescription(step: ActionStep): String {
        return when (step.action) {
            "send_message" -> "Send message to ${step.params["recipient"]}: \"${step.params["message"]}\""
            "send_reply" -> "Reply with: \"${step.params["message"]}\""
            "create_event" -> "Create calendar event: ${step.params["description"]}"
            "open_ride_app" -> "Open ride app${step.params["destination"]?.let { " to $it" } ?: ""}"
            "open_food_app" -> "Order food${step.params["restaurant"]?.let { " from $it" } ?: ""}"
            else -> "${step.action} via ${step.capabilityId}"
        }
    }
}
