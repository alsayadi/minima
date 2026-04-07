package com.minima.os.agent.planner

import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.ApprovalLevel
import com.minima.os.core.model.ClassifiedIntent
import com.minima.os.core.model.IntentType
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeterministicPlanner @Inject constructor() : Planner {

    override suspend fun plan(taskId: String, intent: ClassifiedIntent): List<ActionStep> {
        return when (intent.type) {
            IntentType.CREATE_EVENT -> listOf(
                step(taskId, "calendar", "create_event", intent.params, ApprovalLevel.CONFIRM)
            )

            IntentType.READ_CALENDAR -> listOf(
                step(taskId, "calendar", "read_events", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.SET_REMINDER -> listOf(
                step(taskId, "calendar", "set_reminder", intent.params, ApprovalLevel.NOTIFY)
            )

            IntentType.SEND_MESSAGE -> listOf(
                step(taskId, "messaging", "draft_message", intent.params, ApprovalLevel.AUTO),
                step(taskId, "messaging", "send_message", intent.params, ApprovalLevel.CONFIRM)
            )

            IntentType.READ_MESSAGES -> listOf(
                step(taskId, "messaging", "read_messages", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.TRIAGE_NOTIFICATIONS -> listOf(
                step(taskId, "notification", "triage", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.DISMISS_NOTIFICATION -> listOf(
                step(taskId, "notification", "dismiss", intent.params, ApprovalLevel.NOTIFY)
            )

            IntentType.REPLY_NOTIFICATION -> listOf(
                step(taskId, "notification", "draft_reply", intent.params, ApprovalLevel.AUTO),
                step(taskId, "notification", "send_reply", intent.params, ApprovalLevel.CONFIRM)
            )

            IntentType.ORDER_RIDE -> listOf(
                step(taskId, "commerce", "open_ride_app", intent.params, ApprovalLevel.CONFIRM)
            )

            IntentType.ORDER_FOOD -> listOf(
                step(taskId, "commerce", "open_food_app", intent.params, ApprovalLevel.CONFIRM)
            )

            IntentType.OPEN_APP -> listOf(
                step(taskId, "system", "open_app", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.SEARCH -> listOf(
                step(taskId, "system", "search", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.DEVICE_SETTING -> listOf(
                step(taskId, "system", "change_setting", intent.params, ApprovalLevel.NOTIFY)
            )

            IntentType.REMEMBER -> listOf(
                step(taskId, "memory", "remember", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.RECALL -> listOf(
                step(taskId, "memory", "recall", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.ANSWER -> listOf(
                step(taskId, "chat", "answer", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.FLASHLIGHT -> listOf(
                step(taskId, "system", "flashlight", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.OPEN_CAMERA -> listOf(
                step(taskId, "system", "open_camera", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.MUSIC_CONTROL -> listOf(
                step(taskId, "system", "music_control", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.CREATE_CALENDAR_EVENT -> listOf(
                step(taskId, "calendar", "create_event", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.GET_WEATHER -> listOf(
                step(taskId, "weather", "get_weather", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.SET_ALARM -> listOf(
                step(taskId, "alarm",
                    if (intent.params.containsKey("seconds") || intent.params.containsKey("minutes")) "set_timer" else "set_alarm",
                    intent.params, ApprovalLevel.AUTO)
            )

            IntentType.CALL_CONTACT -> listOf(
                step(taskId, "contacts", "call", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.CONVERT -> listOf(
                step(taskId, "convert", "convert", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.SUMMARIZE_NOTIFICATIONS -> listOf(
                step(taskId, "notification", "summarize", intent.params, ApprovalLevel.AUTO)
            )

            IntentType.UNKNOWN -> emptyList()
        }
    }

    private fun step(
        taskId: String,
        capabilityId: String,
        action: String,
        params: Map<String, String>,
        approval: ApprovalLevel
    ) = ActionStep(
        id = UUID.randomUUID().toString(),
        taskId = taskId,
        capabilityId = capabilityId,
        action = action,
        params = params,
        approvalLevel = approval
    )
}
