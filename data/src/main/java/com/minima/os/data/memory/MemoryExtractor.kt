package com.minima.os.data.memory

import com.minima.os.core.model.IntentType
import com.minima.os.core.model.Task
import com.minima.os.core.model.TaskState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts memories from completed tasks. Runs after every task execution.
 * Learns:
 * - People mentioned in messages/events
 * - Places from ride/navigation requests
 * - Preferences from repeated patterns
 * - Facts from explicit "remember" commands
 * - Usage patterns from command history
 */
@Singleton
class MemoryExtractor @Inject constructor(
    private val memoryManager: MemoryManager
) {

    /**
     * Extract and store memories from a completed task.
     */
    suspend fun extractFromTask(task: Task) {
        if (task.state != TaskState.COMPLETED && task.state != TaskState.FAILED) return

        val intent = task.intent ?: return
        val params = intent.params
        val input = task.input.lowercase()

        // Record the command pattern
        recordCommandPattern(intent.type)

        // Check for explicit "remember" commands
        if (isRememberCommand(input)) {
            handleRememberCommand(input, task.id)
            return
        }

        // Extract based on intent type
        when (intent.type) {
            IntentType.SEND_MESSAGE, IntentType.REPLY_NOTIFICATION -> {
                extractPerson(params, task.id)
            }

            IntentType.CREATE_EVENT, IntentType.SET_REMINDER -> {
                extractEventMemory(params, task.id)
                // Extract people from event descriptions
                extractPerson(params, task.id)
            }

            IntentType.ORDER_RIDE -> {
                extractPlace(params, "destination", task.id)
            }

            IntentType.ORDER_FOOD -> {
                extractFoodPreference(params, task.id)
            }

            IntentType.SEARCH -> {
                extractSearchPattern(params, task.id)
            }

            IntentType.OPEN_APP -> {
                extractAppPreference(params, task.id)
            }

            IntentType.DEVICE_SETTING -> {
                extractSettingPreference(params, task.id)
            }

            else -> {}
        }
    }

    /**
     * Handle explicit "remember that..." or "my name is..." commands.
     */
    private suspend fun handleRememberCommand(input: String, taskId: String) {
        val text = input.trim()

        // "my name is X"
        val nameMatch = Regex("my name is (\\w+)", RegexOption.IGNORE_CASE).find(text)
        if (nameMatch != null) {
            val name = nameMatch.groupValues[1]
            memoryManager.remember(
                key = "user.name",
                value = name,
                category = "preference",
                source = "explicit",
                tier = MemoryManager.LTM
            )
            return
        }

        // "i am a X" / "i'm a X"
        val roleMatch = Regex("i(?:'m| am) (?:a |an )?(\\w[\\w\\s]+)", RegexOption.IGNORE_CASE).find(text)
        if (roleMatch != null) {
            memoryManager.remember(
                key = "user.role",
                value = roleMatch.groupValues[1].trim(),
                category = "preference",
                source = "explicit",
                tier = MemoryManager.LTM
            )
            return
        }

        // "i live in X" / "i'm from X"
        val locationMatch = Regex("i (?:live in|'m from|am from) (.+)", RegexOption.IGNORE_CASE).find(text)
        if (locationMatch != null) {
            val location = locationMatch.groupValues[1].trim()
            memoryManager.remember(
                key = "user.location",
                value = location,
                category = "preference",
                source = "explicit",
                tier = MemoryManager.LTM
            )
            memoryManager.rememberPlace(name = location, type = "home")
            return
        }

        // "i like X" / "i prefer X" / "i love X"
        val prefMatch = Regex("i (?:like|prefer|love|enjoy) (.+)", RegexOption.IGNORE_CASE).find(text)
        if (prefMatch != null) {
            memoryManager.remember(
                key = "user.preference.${prefMatch.groupValues[1].take(20).replace(" ", "_")}",
                value = prefMatch.groupValues[1].trim(),
                category = "preference",
                source = "explicit",
                tier = MemoryManager.LTM
            )
            return
        }

        // "remember that X" / "remember X"
        val rememberMatch = Regex("remember (?:that )?(.+)", RegexOption.IGNORE_CASE).find(text)
        if (rememberMatch != null) {
            memoryManager.remember(
                key = "fact.${System.currentTimeMillis()}",
                value = rememberMatch.groupValues[1].trim(),
                category = "fact",
                source = "explicit",
                tier = MemoryManager.LTM
            )
            return
        }

        // "X is my Y" (e.g. "Sarah is my sister")
        val relMatch = Regex("(\\w+) is my (\\w+)", RegexOption.IGNORE_CASE).find(text)
        if (relMatch != null) {
            val name = relMatch.groupValues[1]
            val relationship = relMatch.groupValues[2]
            memoryManager.rememberPerson(name = name, relationship = relationship)
            return
        }
    }

    private fun isRememberCommand(input: String): Boolean {
        return input.startsWith("remember ") ||
                input.startsWith("my name is ") ||
                input.startsWith("i am ") ||
                input.startsWith("i'm ") ||
                input.startsWith("i live ") ||
                input.startsWith("i like ") ||
                input.startsWith("i prefer ") ||
                input.startsWith("i love ") ||
                input.startsWith("i enjoy ") ||
                input.contains(" is my ")
    }

    private suspend fun extractPerson(params: Map<String, String>, taskId: String) {
        val recipient = params["recipient"] ?: params["contact"] ?: params["to"] ?: return
        if (recipient.length < 2) return
        memoryManager.rememberPerson(
            name = recipient.replaceFirstChar { it.uppercase() },
            notes = "Contacted via task"
        )
    }

    private suspend fun extractEventMemory(params: Map<String, String>, taskId: String) {
        val description = params["description"] ?: params["title"] ?: return
        memoryManager.remember(
            key = "event.recent.${System.currentTimeMillis()}",
            value = description,
            category = "context",
            source = "task:$taskId"
        )
    }

    private suspend fun extractPlace(params: Map<String, String>, key: String, taskId: String) {
        val place = params[key] ?: params["location"] ?: return
        memoryManager.rememberPlace(
            name = place,
            notes = "Mentioned in task"
        )
    }

    private suspend fun extractFoodPreference(params: Map<String, String>, taskId: String) {
        val restaurant = params["restaurant"] ?: params["from"] ?: return
        memoryManager.remember(
            key = "food.restaurant.$restaurant",
            value = restaurant,
            category = "preference",
            source = "task:$taskId"
        )
    }

    private suspend fun extractSearchPattern(params: Map<String, String>, taskId: String) {
        // Don't store every search as STM noise. Only remember a single "last_search"
        // slot, overwritten each time.
        val query = params["query"]?.trim() ?: return
        if (query.isBlank()) return
        memoryManager.remember(
            key = "search.last",
            value = query,
            category = "context",
            source = "task:$taskId"
        )
    }

    private suspend fun extractAppPreference(params: Map<String, String>, taskId: String) {
        val app = params["app"] ?: params["app_name"] ?: return
        memoryManager.remember(
            key = "app.usage.$app",
            value = app,
            category = "preference",
            source = "task:$taskId"
        )
    }

    private suspend fun extractSettingPreference(params: Map<String, String>, taskId: String) {
        val setting = params["setting"] ?: return
        val action = params["action"] ?: "toggle"
        memoryManager.remember(
            key = "setting.preference.$setting",
            value = "$action $setting",
            category = "preference",
            source = "task:$taskId"
        )
    }

    private suspend fun recordCommandPattern(intentType: IntentType) {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timeOfDay = when (hour) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }

        memoryManager.recordPattern(
            type = "routine",
            description = "Uses ${intentType.name.lowercase().replace("_", " ")} in the $timeOfDay"
        )
    }
}
