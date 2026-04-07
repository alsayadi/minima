package com.minima.os.capability.notification

import com.minima.os.capability.registry.CapabilityProvider
import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.NotificationCategory
import com.minima.os.core.model.NotificationInfo
import com.minima.os.core.model.StepResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationCapability @Inject constructor() : CapabilityProvider {

    override val id = "notification"

    override fun supportedActions() = listOf("triage", "dismiss", "draft_reply", "send_reply")

    // Injected at runtime by the notification listener service
    var notificationSource: (() -> List<NotificationInfo>)? = null

    override suspend fun execute(step: ActionStep): StepResult {
        return when (step.action) {
            "triage" -> triageNotifications()
            "dismiss" -> dismissNotification(step.params)
            "draft_reply" -> draftReply(step.params)
            "send_reply" -> sendReply(step.params)
            else -> StepResult(success = false, error = "Unknown action: ${step.action}")
        }
    }

    private fun triageNotifications(): StepResult {
        val notifications = notificationSource?.invoke() ?: emptyList()

        if (notifications.isEmpty()) {
            return StepResult(success = true, data = mapOf("summary" to "No notifications"))
        }

        val grouped = notifications.groupBy { it.category }
        val summary = buildString {
            append("${notifications.size} notifications:\n")
            grouped.forEach { (category, items) ->
                append("  ${category.name}: ${items.size}")
                if (items.size <= 3) {
                    items.forEach { append("\n    - ${it.appName}: ${it.title}") }
                }
                append("\n")
            }
        }

        val urgent = notifications.filter {
            it.category == NotificationCategory.MESSAGE ||
                it.category == NotificationCategory.CALENDAR
        }

        return StepResult(
            success = true,
            data = mapOf(
                "summary" to summary,
                "total" to notifications.size.toString(),
                "urgentCount" to urgent.size.toString()
            )
        )
    }

    private fun dismissNotification(params: Map<String, String>): StepResult {
        // Would integrate with NotificationListenerService.cancelNotification()
        return StepResult(
            success = true,
            data = mapOf("action" to "dismissed", "target" to (params["target"] ?: "all"))
        )
    }

    private fun draftReply(params: Map<String, String>): StepResult {
        val message = params["message"] ?: return StepResult(
            success = false, error = "No reply message provided"
        )
        return StepResult(
            success = true,
            data = mapOf("draft" to message, "status" to "ready_to_send")
        )
    }

    private fun sendReply(params: Map<String, String>): StepResult {
        // Would use notification's direct reply action
        return StepResult(
            success = true,
            data = mapOf("status" to "sent", "message" to (params["message"] ?: ""))
        )
    }
}
