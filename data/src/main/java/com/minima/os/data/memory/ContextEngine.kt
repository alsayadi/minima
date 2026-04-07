package com.minima.os.data.memory

import com.minima.os.data.dao.MemoryDao
import com.minima.os.model.provider.CloudModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextEngine @Inject constructor(
    private val memoryDao: MemoryDao,
    private val cloudModelProvider: CloudModelProvider
) {

    data class ContextData(
        val userName: String?,
        val greeting: String,
        val insightCards: List<InsightCard>,
        val suggestions: List<String>,
        val isNewUser: Boolean,
        val temperature: String? = null
    )

    data class InsightCard(
        val icon: String,
        val title: String,
        val subtitle: String,
        val action: String?
    )

    var testHour: Int? = null
    var testDayOfWeek: Int? = null

    private val json = Json { ignoreUnknownKeys = true }

    // Simple weather cache
    private var cachedTemperature: String? = null
    private var cachedTempAt: Long = 0L
    private val tempTtlMs = 30 * 60 * 1000L // 30 min

    private suspend fun fetchTemperature(city: String?): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedTemperature != null && now - cachedTempAt < tempTtlMs) return@withContext cachedTemperature
        val loc = city?.takeIf { it.isNotBlank() } ?: "Dubai"
        try {
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(loc, "UTF-8")}&count=1"
            val geo = JSONObject(fetch(geoUrl))
            val results = geo.optJSONArray("results") ?: return@withContext null
            if (results.length() == 0) return@withContext null
            val lat = results.getJSONObject(0).getDouble("latitude")
            val lon = results.getJSONObject(0).getDouble("longitude")
            val wxUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m"
            val wx = JSONObject(fetch(wxUrl))
            val t = wx.optJSONObject("current")?.optDouble("temperature_2m", Double.NaN) ?: Double.NaN
            if (t.isNaN()) return@withContext null
            val result = "${t.toInt()}°C"
            cachedTemperature = result
            cachedTempAt = now
            result
        } catch (_: Exception) { null }
    }

    private fun fetch(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000; conn.readTimeout = 5000
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    suspend fun generateContext(): ContextData {
        val cal = Calendar.getInstance()
        val hour = testHour ?: cal.get(Calendar.HOUR_OF_DAY)
        val dayOfWeek = testDayOfWeek ?: cal.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

        val userName = memoryDao.getByKey("user.name")?.value
        val totalMemories = memoryDao.countAll()
        val isNewUser = totalMemories < 3
        val greeting = buildGreeting(hour, userName)

        // Try AI-driven
        if (cloudModelProvider.isAvailable() && !isNewUser) {
            try {
                val aiResult = generateWithAI(hour, dayOfWeek, isWeekend, userName, greeting)
                if (aiResult != null) return aiResult.copy(isNewUser = false)
            } catch (e: Exception) {
                android.util.Log.w("ContextEngine", "AI context failed: ${e.message}")
            }
        }

        // Fallback
        return generateFallback(hour, isWeekend, userName, greeting, isNewUser)
    }

    private suspend fun generateWithAI(
        hour: Int, dayOfWeek: Int, isWeekend: Boolean, userName: String?, greeting: String
    ): ContextData? {
        val userLocation = memoryDao.getByKey("user.location")?.value
        val userRole = memoryDao.getByKey("user.role")?.value
        val totalMemories = memoryDao.countAll()

        val preferences = memoryDao.getByCategory("preference")
            .filter { !it.key.startsWith("app.usage") && !it.key.startsWith("setting.") }
            .take(5)
        val facts = memoryDao.getByCategory("fact").take(5)
        val patterns = memoryDao.search("routine", 5)

        val dayName = arrayOf("", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")[dayOfWeek]
        val timeLabel = when {
            hour < 5 -> "late night"; hour < 9 -> "early morning"; hour < 12 -> "morning"
            hour < 14 -> "midday"; hour < 17 -> "afternoon"; hour < 20 -> "evening"; else -> "night"
        }

        val contextBlock = buildString {
            appendLine("Time: $hour:00 ($timeLabel), $dayName, ${if (isWeekend) "weekend" else "weekday"}")
            if (userName != null) appendLine("Name: $userName")
            if (userLocation != null) appendLine("Location: $userLocation")
            if (userRole != null) appendLine("Role: $userRole")
            if (preferences.isNotEmpty()) appendLine("Preferences: ${preferences.joinToString(", ") { it.value }}")
            if (facts.isNotEmpty()) appendLine("Facts: ${facts.joinToString(", ") { it.value }}")
            if (patterns.isNotEmpty()) appendLine("Patterns: ${patterns.take(3).joinToString(", ") { it.value }}")
            appendLine("Memories: $totalMemories total")
        }

        val prompt = """You are an AI generating context cards for a phone home screen.
Based on this user's context, generate:
1. Exactly 2 insight cards (small, tappable chips for the home screen)
2. Exactly 3 smart suggestions (commands the user might want to run right now)

The insight cards and suggestions must be PERSONALIZED and RELEVANT to this exact moment.
Use the user's actual data — their name, location, preferences, patterns, remembered facts.
Don't be generic. A card about "Focus time" with subtitle "Peak productivity" is useless.
Instead: "Coffee break?" with "You love coffee and it's mid-morning" is personal.

$contextBlock

Respond ONLY with JSON, no markdown:
{"cards": [{"icon": "morning|focus|food|evening|night|heart|pattern|brain|calendar|person", "title": "...", "subtitle": "...", "action": "command or null"}], "suggestions": ["command 1", "command 2", "command 3"]}"""

        val response = cloudModelProvider.draft(prompt)
        val clean = response.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        val parsed = json.parseToJsonElement(clean).jsonObject

        val cards = parsed["cards"]?.jsonArray?.map { el ->
            val obj = el.jsonObject
            InsightCard(
                icon = obj["icon"]?.jsonPrimitive?.content ?: "brain",
                title = obj["title"]?.jsonPrimitive?.content ?: "",
                subtitle = obj["subtitle"]?.jsonPrimitive?.content ?: "",
                action = obj["action"]?.jsonPrimitive?.contentOrNull
            )
        } ?: emptyList()

        val suggestions = parsed["suggestions"]?.jsonArray?.map {
            it.jsonPrimitive.content
        } ?: emptyList()

        return ContextData(
            userName = userName,
            greeting = greeting,
            insightCards = cards.take(3),
            suggestions = suggestions.take(4),
            isNewUser = false,
            temperature = fetchTemperature(userLocation)
        )
    }

    private suspend fun generateFallback(
        hour: Int, isWeekend: Boolean, userName: String?, greeting: String, isNewUser: Boolean
    ): ContextData {
        val userLocation = memoryDao.getByKey("user.location")?.value
        val cards = mutableListOf<InsightCard>()

        // One time-based card
        val timeCard = when {
            hour in 5..9 -> InsightCard(
                "morning",
                if (isWeekend) "Enjoy your weekend" else "Start your day",
                if (userLocation != null) "In ${userLocation.replaceFirstChar { it.uppercase() }}" else "Let's go",
                null
            )
            hour in 10..13 -> InsightCard("focus", "Midday", "Stay on track", null)
            hour in 17..20 -> InsightCard("evening", "Wrapping up", "Check your tasks", "check notifications")
            hour >= 21 || hour < 5 -> InsightCard("night", "Wind down", "Time to relax", "turn on do not disturb")
            else -> InsightCard("brain", "Ready", "What do you need?", null)
        }
        cards.add(timeCard)

        // Preferences card
        val prefs = memoryDao.getByCategory("preference")
            .filter { !it.key.startsWith("user.name") && !it.key.startsWith("user.location") &&
                     !it.key.startsWith("user.role") && !it.key.startsWith("app.") && !it.key.startsWith("setting.") }
        if (prefs.isNotEmpty()) {
            cards.add(InsightCard("heart", "Your likes", prefs.take(3).joinToString(", ") { it.value }, null))
        }

        val suggestions = when {
            userName == null -> listOf("Tell me your name", "What can you do?", "Check notifications")
            hour in 5..9 -> listOf("What's on my calendar?", "Check notifications", "Good morning")
            hour in 10..14 -> listOf("Remind me to stretch", "Order lunch", "Tell me a joke")
            hour in 15..18 -> listOf("What's left today?", "Text someone", "Check notifications")
            else -> listOf("Set alarm for tomorrow", "Turn on do not disturb", "Tell me a joke")
        }

        val temperature = fetchTemperature(userLocation)
        return ContextData(userName, greeting, cards, suggestions, isNewUser, temperature)
    }

    private fun buildGreeting(hour: Int, name: String?): String {
        val time = when { hour < 5 -> "Good night"; hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
        return if (name != null) "$time, ${name.replaceFirstChar { it.uppercase() }}" else time
    }

    fun getOnboardingQuestions(): List<OnboardingQuestion> = listOf(
        OnboardingQuestion("What's your name?", "my name is "),
        OnboardingQuestion("Where do you live?", "i live in "),
        OnboardingQuestion("What do you do?", "i am a "),
        OnboardingQuestion("What do you enjoy?", "i like ")
    )

    data class OnboardingQuestion(val question: String, val prefix: String)
}
