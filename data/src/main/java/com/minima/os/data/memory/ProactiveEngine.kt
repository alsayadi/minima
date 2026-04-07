package com.minima.os.data.memory

import com.minima.os.data.dao.MemoryDao
import com.minima.os.data.dao.TaskDao
import com.minima.os.model.provider.CloudModelProvider
import kotlinx.serialization.json.*
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProactiveEngine @Inject constructor(
    private val memoryDao: MemoryDao,
    private val taskDao: TaskDao,
    private val cloudModelProvider: CloudModelProvider
) {

    enum class Sensitivity { QUIET, NORMAL, PROACTIVE }

    data class ProactiveCard(
        val id: String,
        val type: CardType,
        val title: String,
        val body: String,
        val action: String?,
        val priority: Int,
        val dismissable: Boolean = true
    )

    enum class CardType {
        MORNING_BRIEF, TRANSITION_SUMMARY, PATTERN_NUDGE,
        PEOPLE_REMINDER, MEMORY_MILESTONE, TIME_AWARE
    }

    var testHour: Int? = null
    var testDayOfWeek: Int? = null

    // Track dismissed card types so AI learns what user doesn't want
    private val dismissedTypes = mutableSetOf<String>()
    private val tappedTypes = mutableSetOf<String>()

    fun onCardDismissed(cardId: String) {
        dismissedTypes.add(cardId.substringBefore("_"))
    }

    fun onCardTapped(cardId: String) {
        tappedTypes.add(cardId.substringBefore("_"))
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateCards(sensitivity: Sensitivity = Sensitivity.NORMAL): List<ProactiveCard> {
        val cal = Calendar.getInstance()
        val hour = testHour ?: cal.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = testDayOfWeek ?: cal.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

        val maxCards = when (sensitivity) {
            Sensitivity.QUIET -> 1
            Sensitivity.NORMAL -> 3
            Sensitivity.PROACTIVE -> 5
        }

        // Try AI-driven first
        if (cloudModelProvider.isAvailable()) {
            try {
                val aiCards = generateWithAI(hour, dayOfWeek, isWeekend, maxCards, sensitivity)
                if (aiCards.isNotEmpty()) return aiCards
            } catch (e: Exception) {
                android.util.Log.w("ProactiveEngine", "AI generation failed: ${e.message}")
            }
        }

        // Fallback to pattern-based
        return generateFromPatterns(hour, isWeekend, maxCards)
    }

    private suspend fun generateWithAI(
        hour: Int, dayOfWeek: Int, isWeekend: Boolean, maxCards: Int, sensitivity: Sensitivity
    ): List<ProactiveCard> {
        // Gather all context
        val userName = memoryDao.getByKey("user.name")?.value
        val userLocation = memoryDao.getByKey("user.location")?.value
        val userRole = memoryDao.getByKey("user.role")?.value
        val totalMemories = memoryDao.countAll()
        val ltmCount = memoryDao.countByTier("LTM")

        val preferences = memoryDao.getByCategory("preference")
            .filter { !it.key.startsWith("app.usage") && !it.key.startsWith("setting.") }
            .take(5)

        val facts = memoryDao.getByCategory("fact").take(5)
        val patterns = memoryDao.search("routine", 10)
        val recentTasks = taskDao.getRecent(10)

        val dayName = arrayOf("", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")[dayOfWeek]
        val timeLabel = when {
            hour < 5 -> "late night"
            hour < 9 -> "early morning"
            hour < 12 -> "morning"
            hour < 14 -> "around lunch"
            hour < 17 -> "afternoon"
            hour < 20 -> "evening"
            else -> "night"
        }

        val contextBlock = buildString {
            appendLine("Current time: $hour:00 ($timeLabel), $dayName, ${if (isWeekend) "weekend" else "weekday"}")
            if (userName != null) appendLine("User: $userName")
            if (userLocation != null) appendLine("Location: $userLocation")
            if (userRole != null) appendLine("Role: $userRole")
            appendLine("Total memories: $totalMemories ($ltmCount permanent)")

            if (preferences.isNotEmpty()) {
                appendLine("Preferences: ${preferences.joinToString(", ") { "${it.key.substringAfterLast(".")}=${it.value}" }}")
            }
            if (facts.isNotEmpty()) {
                appendLine("Remembered facts: ${facts.joinToString(", ") { it.value }}")
            }
            if (patterns.isNotEmpty()) {
                appendLine("Usage patterns: ${patterns.take(5).joinToString(", ") { it.value }}")
            }
            if (recentTasks.isNotEmpty()) {
                val completed = recentTasks.count { it.state == "COMPLETED" }
                val failed = recentTasks.count { it.state == "FAILED" }
                appendLine("Recent tasks: $completed completed, $failed failed out of ${recentTasks.size}")
                appendLine("Recent commands: ${recentTasks.take(3).joinToString(", ") { it.input }}")
            }
            if (dismissedTypes.isNotEmpty()) {
                appendLine("User dismissed these card types before: ${dismissedTypes.joinToString(", ")} — avoid similar cards")
            }
            if (tappedTypes.isNotEmpty()) {
                appendLine("User engaged with these card types: ${tappedTypes.joinToString(", ")} — show more like these")
            }
            appendLine("Sensitivity: $sensitivity")
        }

        val prompt = """You are an AI assistant deciding what proactive cards to show on a phone home screen.
Based on the user's context below, generate $maxCards proactive notification cards.

Each card should be genuinely useful RIGHT NOW — not generic. Consider:
- What the user likely needs at this time based on their patterns
- Facts they asked to remember (surface them at relevant times)
- People they interact with
- Their role and location
- What they've been doing recently
- DON'T repeat the same card types the user dismissed

$contextBlock

Respond ONLY with a JSON array, no markdown:
[{"type": "morning_brief|transition|nudge|people|milestone|time_aware", "title": "short title", "body": "1-2 sentence insight", "action": "command to run or null", "priority": 0-2}]

Examples of GOOD cards:
- {"type": "nudge", "title": "Coffee time?", "body": "You usually grab coffee around now. There's a cafe nearby.", "action": "search coffee near me", "priority": 1}
- {"type": "people", "title": "Check in with Sarah", "body": "You haven't messaged your sister in a while.", "action": "text Sarah", "priority": 0}
- {"type": "morning_brief", "title": "Good morning, Ahmed", "body": "It's Monday in Dubai. You have a meeting at 3pm. 14 things in memory.", "action": "whats on my calendar", "priority": 2}
- {"type": "time_aware", "title": "Wrap up", "body": "You've completed 5 tasks today. Time to check what's pending.", "action": "check notifications", "priority": 1}

Examples of BAD cards (too generic, not personalized):
- {"type": "time_aware", "title": "Focus time", "body": "Peak productivity hours", ...}  <- says nothing useful
- {"type": "time_aware", "title": "Lunch break", "body": "Take a break", ...}  <- assumes they eat at 12"""

        val response = cloudModelProvider.draft(prompt)

        // Parse the JSON array
        val cleanResponse = response.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        val parsed = json.parseToJsonElement(cleanResponse).jsonArray

        return parsed.mapIndexed { index, element ->
            val obj = element.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: "time_aware"
            val cardType = when (type) {
                "morning_brief" -> CardType.MORNING_BRIEF
                "transition" -> CardType.TRANSITION_SUMMARY
                "nudge" -> CardType.PATTERN_NUDGE
                "people" -> CardType.PEOPLE_REMINDER
                "milestone" -> CardType.MEMORY_MILESTONE
                else -> CardType.TIME_AWARE
            }

            ProactiveCard(
                id = "${type}_${index}_${System.currentTimeMillis()}",
                type = cardType,
                title = obj["title"]?.jsonPrimitive?.content ?: "",
                body = obj["body"]?.jsonPrimitive?.content ?: "",
                action = obj["action"]?.jsonPrimitive?.contentOrNull,
                priority = obj["priority"]?.jsonPrimitive?.intOrNull ?: 1
            )
        }.sortedByDescending { it.priority }.take(maxCards)
    }

    /**
     * Fallback: pattern-based cards when AI is unavailable.
     */
    private suspend fun generateFromPatterns(hour: Int, isWeekend: Boolean, maxCards: Int): List<ProactiveCard> {
        val cards = mutableListOf<ProactiveCard>()
        val userName = memoryDao.getByKey("user.name")?.value
        val userLocation = memoryDao.getByKey("user.location")?.value
        val facts = memoryDao.getByCategory("fact").take(3)
        val totalMemories = memoryDao.countAll()

        // Morning brief
        if (hour in 5..10) {
            val body = buildString {
                if (isWeekend) append("It's the weekend. ")
                else append("Ready for the day. ")
                if (userLocation != null) append("In ${userLocation.replaceFirstChar { it.uppercase() }}. ")
                if (facts.isNotEmpty()) append("Reminder: ${facts.first().value}. ")
                if (totalMemories > 0) append("$totalMemories things in memory.")
            }
            cards.add(ProactiveCard(
                id = "morning_${System.currentTimeMillis()}",
                type = CardType.MORNING_BRIEF,
                title = if (userName != null) "Good morning, ${userName.replaceFirstChar { it.uppercase() }}" else "Good morning",
                body = body, action = "whats on my calendar", priority = 2
            ))
        }

        // Afternoon transition
        if (hour in 14..16) {
            val recent = taskDao.getRecent(10)
            val completed = recent.count { it.state == "COMPLETED" }
            if (completed > 0) {
                cards.add(ProactiveCard(
                    id = "transition_${System.currentTimeMillis()}",
                    type = CardType.TRANSITION_SUMMARY,
                    title = "Afternoon check-in",
                    body = "You've completed $completed tasks. Check if anything needs attention.",
                    action = "check notifications", priority = 1
                ))
            }
        }

        // Night wind down
        if (hour >= 22) {
            cards.add(ProactiveCard(
                id = "night_${System.currentTimeMillis()}",
                type = CardType.TIME_AWARE,
                title = "Wind down",
                body = "Consider DnD for better sleep.",
                action = "turn on do not disturb", priority = 1
            ))
        }

        return cards.take(maxCards)
    }
}
