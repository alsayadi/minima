package com.minima.os.capability.messaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.minima.os.capability.registry.CapabilityProvider
import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.StepResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessagingCapability @Inject constructor(
    @ApplicationContext private val context: Context
) : CapabilityProvider {

    override val id = "messaging"

    override fun supportedActions() = listOf("draft_message", "send_message", "read_messages")

    override suspend fun execute(step: ActionStep): StepResult {
        return when (step.action) {
            "draft_message" -> draftMessage(step.params)
            "send_message" -> sendMessage(step.params)
            "read_messages" -> readMessages(step.params)
            else -> StepResult(success = false, error = "Unknown action: ${step.action}")
        }
    }

    private fun draftMessage(params: Map<String, String>): StepResult {
        val recipient = params["recipient"] ?: return StepResult(
            success = false, error = "No recipient specified"
        )
        val message = params["message"] ?: return StepResult(
            success = false, error = "No message content"
        )

        return StepResult(
            success = true,
            data = mapOf(
                "draft" to message,
                "recipient" to recipient,
                "status" to "drafted"
            )
        )
    }

    private fun sendMessage(params: Map<String, String>): StepResult {
        val recipient = params["recipient"] ?: return StepResult(
            success = false, error = "No recipient"
        )
        val message = params["message"] ?: return StepResult(
            success = false, error = "No message"
        )

        return try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$recipient")
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            StepResult(
                success = true,
                data = mapOf("status" to "opened_sms_app", "recipient" to recipient)
            )
        } catch (e: Exception) {
            StepResult(success = false, error = "Failed to open messaging: ${e.message}")
        }
    }

    private fun readMessages(params: Map<String, String>): StepResult {
        // Opens the default messaging app
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_MESSAGING)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            StepResult(success = true, data = mapOf("status" to "opened_messages"))
        } catch (e: Exception) {
            StepResult(success = false, error = "Failed to open messages: ${e.message}")
        }
    }
}
