package com.minima.os.capability.memory

import com.minima.os.data.memory.MemoryExtractor
import com.minima.os.data.memory.MemoryManager
import com.minima.os.capability.registry.CapabilityProvider
import com.minima.os.core.model.ActionStep
import com.minima.os.core.model.StepResult
import com.minima.os.core.model.Task
import com.minima.os.core.model.TaskState
import com.minima.os.core.model.ClassifiedIntent
import com.minima.os.core.model.IntentType
import com.minima.os.core.model.Confidence
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryCapability @Inject constructor(
    private val memoryManager: MemoryManager,
    private val memoryExtractor: MemoryExtractor
) : CapabilityProvider {

    override val id = "memory"

    override fun supportedActions() = listOf("remember", "recall")

    override suspend fun execute(step: ActionStep): StepResult {
        return when (step.action) {
            "remember" -> handleRemember(step)
            "recall" -> handleRecall(step)
            else -> StepResult(success = false, error = "Unknown memory action: ${step.action}")
        }
    }

    private suspend fun handleRemember(step: ActionStep): StepResult {
        val statement = step.params["statement"] ?: return StepResult(false, error = "Nothing to remember")

        // Create a fake completed task so MemoryExtractor can parse it
        val task = Task(
            id = step.taskId,
            input = statement,
            state = TaskState.COMPLETED,
            intent = ClassifiedIntent(IntentType.REMEMBER, Confidence.HIGH, step.params, statement),
            completedAt = System.currentTimeMillis()
        )
        memoryExtractor.extractFromTask(task)

        return StepResult(
            success = true,
            data = mapOf("message" to "Got it, I'll remember that.")
        )
    }

    private suspend fun handleRecall(step: ActionStep): StepResult {
        val query = step.params["query"]?.trim() ?: ""

        // Check specific queries
        if (query.isEmpty() || query.contains("name") || query.contains("who am i")) {
            val name = memoryManager.recall("user.name", 1).firstOrNull()
            if (name != null) {
                return StepResult(
                    success = true,
                    data = mapOf("answer" to "Your name is ${name.value}")
                )
            }
        }

        // General recall
        val memories = memoryManager.recall(query, 5)
        val people = if (query.isNotBlank()) {
            try {
                val ctx = memoryManager.getContextForAgent(query)
                ctx.relevantPeople
            } catch (_: Exception) { emptyList() }
        } else emptyList()

        val stats = memoryManager.getStats()

        if (memories.isEmpty() && people.isEmpty()) {
            return StepResult(
                success = true,
                data = mapOf("answer" to "I don't have any memories about that yet. I have ${stats.totalMemories} memories total.")
            )
        }

        val sb = StringBuilder()
        if (memories.isNotEmpty()) {
            sb.appendLine("I know:")
            memories.forEach { sb.appendLine("- ${it.key}: ${it.value}") }
        }
        if (people.isNotEmpty()) {
            sb.appendLine("People:")
            people.forEach {
                sb.appendLine("- ${it.name}${it.relationship?.let { r -> " ($r)" } ?: ""}")
            }
        }
        sb.append("(${stats.totalMemories} total memories)")

        return StepResult(
            success = true,
            data = mapOf("answer" to sb.toString().trim())
        )
    }
}
