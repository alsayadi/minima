package com.minima.os.agent.classifier

import com.minima.os.core.model.ClassifiedIntent
import com.minima.os.core.model.Confidence
import com.minima.os.core.model.IntentType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeterministicClassifier @Inject constructor() : IntentClassifier {

    override suspend fun classify(input: String): ClassifiedIntent {
        val text = input.trim().lowercase()
        if (text.isBlank()) {
            return ClassifiedIntent(IntentType.UNKNOWN, Confidence.LOW, rawInput = input)
        }

        // Calendar — create
        if (text.matchesAny("schedule", "create event", "add event", "new event", "book meeting",
                "set up meeting", "create meeting", "add meeting", "new meeting", "create appointment")) {
            val desc = text.removeLeading("schedule", "create event", "create meeting",
                "add event", "new event", "book meeting", "set up meeting", "add meeting",
                "new meeting", "create appointment", "create a meeting", "schedule a meeting",
                "book a meeting", "set up a meeting")
            return ClassifiedIntent(IntentType.CREATE_EVENT, Confidence.HIGH,
                mapOf("description" to desc), input)
        }

        // Calendar — read
        if (text.matchesAny("what's on my calendar", "show calendar", "check calendar",
                "my schedule", "show my schedule", "what's my schedule", "my agenda",
                "what do i have today", "calendar", "events today", "upcoming events",
                "what's today", "any meetings", "show events")) {
            return ClassifiedIntent(IntentType.READ_CALENDAR, Confidence.HIGH,
                mapOf("timeRange" to "today"), input)
        }

        // Reminders & alarms
        if (text.matchesAny("remind", "reminder", "alarm", "set alarm", "set a reminder",
                "set reminder", "don't forget", "don't let me forget")) {
            val desc = text.removeLeading("remind me to", "remind me", "set a reminder to",
                "set a reminder for", "set reminder to", "set reminder for", "set reminder",
                "set a reminder", "set alarm for", "set an alarm for", "set alarm", "set an alarm",
                "reminder to", "reminder", "remind", "don't forget to", "don't let me forget to")
            val parts = desc.split(Regex("\\s+(at|in|on|by|for)\\s+"), limit = 2)
            val params = mutableMapOf("description" to parts[0].trim())
            if (parts.size > 1) params["time"] = parts[1].trim()
            return ClassifiedIntent(IntentType.SET_REMINDER, Confidence.HIGH, params, input)
        }

        // Messaging — send
        if (text.matchesAny("text ", "message ", "send ", "tell ", "write to ")) {
            val msgMatch = Regex("(?:text|message|send|send a message to|tell|write to)\\s+(.+?)\\s+(?:that|saying|with|:)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
            return if (msgMatch != null) {
                ClassifiedIntent(IntentType.SEND_MESSAGE, Confidence.HIGH,
                    mapOf("recipient" to msgMatch.groupValues[1], "message" to msgMatch.groupValues[2]), input)
            } else {
                val recipient = text.removeLeading("text", "message", "send a message to",
                    "send message to", "send", "tell", "write to")
                ClassifiedIntent(IntentType.SEND_MESSAGE, Confidence.MEDIUM,
                    mapOf("recipient" to recipient), input)
            }
        }

        // Messaging — read
        if (text.matchesAny("read messages", "show messages", "check messages", "my messages",
                "open messages", "read texts", "show texts", "check texts", "inbox", "read sms")) {
            return ClassifiedIntent(IntentType.READ_MESSAGES, Confidence.HIGH, emptyMap(), input)
        }

        // Notifications
        if (text.matchesAny("notifications", "show notifications", "check notifications",
                "my notifications", "triage notifications", "clear notifications",
                "notification", "what notifications", "any notifications")) {
            return ClassifiedIntent(IntentType.TRIAGE_NOTIFICATIONS, Confidence.HIGH, emptyMap(), input)
        }

        if (text.startsWith("dismiss") || (text.startsWith("clear") && text.contains("notification"))) {
            return ClassifiedIntent(IntentType.DISMISS_NOTIFICATION, Confidence.HIGH,
                mapOf("target" to text.removeLeading("dismiss", "clear", "dismiss notification from",
                    "dismiss notification", "clear notification from", "clear notification")), input)
        }

        if (text.matchesAny("reply to", "respond to")) {
            val replyMatch = Regex("(?:reply|respond)\\s+(?:to)?\\s*(.+?)\\s+(?:with|saying|:)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
            return if (replyMatch != null) {
                ClassifiedIntent(IntentType.REPLY_NOTIFICATION, Confidence.HIGH,
                    mapOf("target" to replyMatch.groupValues[1], "message" to replyMatch.groupValues[2]), input)
            } else {
                ClassifiedIntent(IntentType.REPLY_NOTIFICATION, Confidence.MEDIUM,
                    mapOf("target" to text.removeLeading("reply to", "respond to")), input)
            }
        }

        // Commerce — rides
        if (text.matchesAny("get a ride", "get me a ride", "order a ride", "book a ride",
                "call a ride", "call an uber", "get an uber", "uber", "lyft", "taxi", "cab",
                "request a ride", "i need a ride", "ride to", "take me to")) {
            val dest = text.removeLeading("get a ride to", "get me a ride to", "order a ride to",
                "book a ride to", "call an uber to", "get an uber to", "take me to",
                "ride to", "uber to", "lyft to",
                "get a ride", "get me a ride", "order a ride", "book a ride",
                "call a ride", "call an uber", "get an uber", "request a ride",
                "i need a ride", "uber", "lyft", "taxi", "cab")
            return ClassifiedIntent(IntentType.ORDER_RIDE, Confidence.HIGH,
                mapOf("destination" to dest), input)
        }

        // Commerce — food
        if (text.matchesAny("order food", "get food", "delivery", "order from",
                "doordash", "ubereats", "uber eats", "get me food", "hungry",
                "order dinner", "order lunch", "order breakfast")) {
            val restaurant = text.removeLeading("order food from", "get food from", "order from",
                "order food", "get food", "get me food from", "get me food",
                "order dinner from", "order lunch from", "order breakfast from",
                "order dinner", "order lunch", "order breakfast")
            return ClassifiedIntent(IntentType.ORDER_FOOD, Confidence.HIGH,
                mapOf("restaurant" to restaurant), input)
        }

        // Device settings
        if (text.matchesAny("turn on", "turn off", "enable", "disable", "toggle",
                "wifi", "bluetooth", "flashlight", "dark mode", "airplane mode",
                "brightness", "volume", "dnd", "do not disturb")) {
            val setting = when {
                text.contains("wifi") -> "wifi"
                text.contains("bluetooth") -> "bluetooth"
                text.contains("flashlight") || text.contains("torch") -> "flashlight"
                text.contains("dark mode") -> "dark mode"
                text.contains("airplane") -> "airplane mode"
                text.contains("brightness") -> "brightness"
                text.contains("volume") -> "volume"
                text.contains("dnd") || text.contains("do not disturb") -> "dnd"
                else -> text.removeLeading("turn on", "turn off", "enable", "disable", "toggle")
            }
            val value = when {
                text.contains("turn on") || text.contains("enable") -> "on"
                text.contains("turn off") || text.contains("disable") -> "off"
                text.contains("toggle") -> "toggle"
                else -> ""
            }
            return ClassifiedIntent(IntentType.DEVICE_SETTING, Confidence.HIGH,
                mapOf("setting" to setting, "value" to value), input)
        }

        // Search
        if (text.matchesAny("search", "look up", "google", "what is", "who is",
                "how to", "how do", "what are", "where is", "when is", "why is", "find info")) {
            val query = text.removeLeading("search for", "search", "look up", "google",
                "what is", "who is", "how to", "how do i", "how do", "what are",
                "where is", "when is", "why is", "find info about", "find info on", "find info")
            return ClassifiedIntent(IntentType.SEARCH, Confidence.HIGH,
                mapOf("query" to query), input)
        }

        // Memory — remember
        if (text.matchesAny("remember ", "my name is ", "i am ", "i'm ", "i live ",
                "i like ", "i prefer ", "i love ", "i enjoy ") ||
            text.contains(" is my ")) {
            return ClassifiedIntent(IntentType.REMEMBER, Confidence.HIGH,
                mapOf("statement" to text), input)
        }

        // Memory — recall
        if (text.matchesAny("what do you know", "what do you remember", "do you know my",
                "what's my name", "who am i", "what do i like", "recall ",
                "what do you know about")) {
            val query = text.removeLeading("what do you know about", "what do you remember about",
                "recall", "what do you know", "what do you remember")
            return ClassifiedIntent(IntentType.RECALL, Confidence.HIGH,
                mapOf("query" to query), input)
        }

        // List apps
        if (text.matchesAny("what apps", "list apps", "show apps", "my apps",
                "installed apps", "which apps", "apps on this phone", "what apps do i have")) {
            return ClassifiedIntent(IntentType.OPEN_APP, Confidence.HIGH,
                mapOf("appName" to ""), input)
        }

        // Open app — must be after more specific intents
        if (text.matchesAny("open ", "launch ", "start ", "go to ")) {
            val appName = text.removeLeading("open", "launch", "start", "go to")
            return ClassifiedIntent(IntentType.OPEN_APP, Confidence.HIGH,
                mapOf("appName" to appName), input)
        }

        // Fallback: try to be helpful
        return ClassifiedIntent(IntentType.UNKNOWN, Confidence.LOW, rawInput = input)
    }

    private fun String.matchesAny(vararg keywords: String): Boolean {
        return keywords.any { kw ->
            this == kw || this.startsWith("$kw ") || this.startsWith(kw) || this.contains(kw)
        }
    }

    private fun String.removeLeading(vararg prefixes: String): String {
        // Sort by length descending so longer prefixes match first
        for (prefix in prefixes.sortedByDescending { it.length }) {
            if (this.startsWith(prefix, ignoreCase = true)) {
                return this.substring(prefix.length).trim()
            }
        }
        return this.trim()
    }
}
