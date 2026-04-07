package com.minima.os.capability.chat

import com.minima.os.capability.registry.CapabilityProvider
import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.StepResult
import com.minima.os.data.memory.MemoryManager
import com.minima.os.model.provider.CloudModelProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatCapability @Inject constructor(
    private val cloudModelProvider: CloudModelProvider,
    private val memoryManager: MemoryManager
) : CapabilityProvider {

    override val id = "chat"

    override fun supportedActions() = listOf("answer")

    override suspend fun execute(step: ActionStep): StepResult {
        val query = step.params["query"] ?: step.params["statement"] ?: return StepResult(false, error = "No question")

        return try {
            // Get memory context
            val memoryContext = try {
                memoryManager.getContextString(query)
            } catch (_: Exception) { "" }

            val prompt = buildString {
                if (memoryContext.isNotBlank()) {
                    appendLine(memoryContext)
                    appendLine()
                }
                append(query)
            }

            if (!cloudModelProvider.isAvailable()) {
                return StepResult(
                    success = true,
                    data = mapOf("answer" to "I need an API key to answer questions. Long-press the clock to open Settings.")
                )
            }

            val answer = cloudModelProvider.draft(prompt)

            StepResult(
                success = true,
                data = mapOf("answer" to answer)
            )
        } catch (e: Exception) {
            StepResult(false, error = "Couldn't get an answer: ${e.message}")
        }
    }
}
