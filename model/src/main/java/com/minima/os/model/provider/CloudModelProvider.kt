package com.minima.os.model.provider

import com.minima.os.core.model.ClassifiedIntent
import com.minima.os.core.model.Confidence
import com.minima.os.core.model.IntentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudModelProvider @Inject constructor() : ModelProvider {

    override val name = "openai-gpt4.5"
    override val isLocal = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()

    private var apiKey: String? = null
    private var baseUrl: String = "https://api.openai.com/v1/chat/completions"
    private var model: String = "gpt-4o"

    fun configure(apiKey: String, baseUrl: String? = null, model: String? = null) {
        this.apiKey = apiKey
        baseUrl?.let { this.baseUrl = it }
        model?.let { this.model = it }
    }

    override suspend fun classify(input: String): ClassifiedIntent {
        // Separate memory context from user request if present
        val parts = input.split("\n\nUser request: ", limit = 2)
        val memoryContext = if (parts.size == 2) parts[0] else ""
        val userRequest = if (parts.size == 2) parts[1] else input

        val systemPrompt = buildString {
            appendLine("""You are an intent classifier for a mobile phone launcher AI assistant.
Classify the user's input into exactly one of these intent types:
CREATE_EVENT, READ_CALENDAR, SET_REMINDER, SEND_MESSAGE, READ_MESSAGES,
TRIAGE_NOTIFICATIONS, DISMISS_NOTIFICATION, REPLY_NOTIFICATION,
ORDER_RIDE, ORDER_FOOD, SEARCH, OPEN_APP, DEVICE_SETTING,
REMEMBER, RECALL, ANSWER, UNKNOWN

Rules:
- Use ANSWER for ANY question, conversation, greeting, joke, math, translation, explanation, or general knowledge request.
- Use SEARCH only when the user explicitly wants to search the web or needs real-time info (weather, news, stock prices).
- Use REMEMBER when the user states personal facts ("my name is...", "I like...", "I live in...").
- Use RECALL when the user asks what you know/remember about them.
- Use OPEN_APP when user wants to open or list apps. If no specific app name, set appName to empty string.
- NEVER return UNKNOWN if the input is a valid question or request. Use ANSWER as the default for anything conversational.

Respond ONLY with valid JSON, no markdown, no explanation:
{"type": "INTENT_TYPE", "confidence": "HIGH|MEDIUM|LOW", "params": {"key": "value"}}

Examples:
- "remind me to buy milk at 5pm" -> {"type": "SET_REMINDER", "confidence": "HIGH", "params": {"description": "buy milk", "time": "5pm"}}
- "what's the weather" -> {"type": "SEARCH", "confidence": "HIGH", "params": {"query": "what's the weather"}}
- "tell me a joke" -> {"type": "ANSWER", "confidence": "HIGH", "params": {"query": "tell me a joke"}}
- "what's 2 plus 2" -> {"type": "ANSWER", "confidence": "HIGH", "params": {"query": "what's 2 plus 2"}}
- "hello" -> {"type": "ANSWER", "confidence": "HIGH", "params": {"query": "hello"}}
- "good morning" -> {"type": "ANSWER", "confidence": "HIGH", "params": {"query": "good morning"}}
- "translate hello to arabic" -> {"type": "ANSWER", "confidence": "HIGH", "params": {"query": "translate hello to arabic"}}
- "order an uber to the airport" -> {"type": "ORDER_RIDE", "confidence": "HIGH", "params": {"destination": "airport"}}
- "turn off do not disturb" -> {"type": "DEVICE_SETTING", "confidence": "HIGH", "params": {"setting": "dnd", "action": "off"}}
- "my name is Ahmed" -> {"type": "REMEMBER", "confidence": "HIGH", "params": {"statement": "my name is Ahmed"}}
- "what do you know about me" -> {"type": "RECALL", "confidence": "HIGH", "params": {"query": "me"}}
- "what apps do I have" -> {"type": "OPEN_APP", "confidence": "HIGH", "params": {"appName": ""}}""")

            if (memoryContext.isNotBlank()) {
                appendLine()
                appendLine("Context about the user (use this to personalize, NOT to classify):")
                appendLine(memoryContext)
            }
        }

        val response = chatComplete(systemPrompt, userRequest)

        return try {
            val parsed = json.parseToJsonElement(response.trim()).jsonObject
            val typeStr = parsed["type"]?.jsonPrimitive?.content ?: "UNKNOWN"
            val type = try { IntentType.valueOf(typeStr) } catch (_: Exception) { IntentType.UNKNOWN }
            val confStr = parsed["confidence"]?.jsonPrimitive?.content ?: "MEDIUM"
            val confidence = try { Confidence.valueOf(confStr) } catch (_: Exception) { Confidence.MEDIUM }

            val params = mutableMapOf<String, String>()
            parsed["params"]?.jsonObject?.forEach { (k, v) ->
                params[k] = v.jsonPrimitive.content
            }

            ClassifiedIntent(type = type, confidence = confidence, params = params, rawInput = input)
        } catch (e: Exception) {
            ClassifiedIntent(type = IntentType.SEARCH, confidence = Confidence.LOW,
                params = mapOf("query" to input), rawInput = input)
        }
    }

    override suspend fun draft(prompt: String): String {
        return chatComplete(
            "You are a helpful mobile assistant. Write concise, natural responses.",
            prompt
        )
    }

    override suspend fun plan(context: String): String {
        return chatComplete(
            "You are a task planner for a mobile AI assistant. Break down the user request into actionable steps.",
            context
        )
    }

    override suspend fun isAvailable(): Boolean {
        val available = apiKey != null
        android.util.Log.d("CloudModelProvider", "isAvailable=$available, model=$model")
        return available
    }

    private suspend fun chatComplete(systemPrompt: String, userMessage: String): String =
        withContext(Dispatchers.IO) {
            val key = apiKey ?: throw IllegalStateException("OpenAI API key not configured")
            android.util.Log.d("CloudModelProvider", "Calling $model with key=${key.take(10)}...")

            val escapedSystem = systemPrompt.replace("\"", "\\\"").replace("\n", "\\n")
            val escapedUser = userMessage.replace("\"", "\\\"").replace("\n", "\\n")

            val body = """
            {
                "model": "$model",
                "messages": [
                    {"role": "system", "content": "$escapedSystem"},
                    {"role": "user", "content": "$escapedUser"}
                ],
                "max_tokens": 1024,
                "temperature": 0.3
            }
            """.trimIndent()

            val request = Request.Builder()
                .url(baseUrl)
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .post(body.toRequestBody(mediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                throw Exception("OpenAI API error ${response.code}: $responseBody")
            }

            // Parse OpenAI response: { "choices": [{ "message": { "content": "..." } }] }
            val parsed = json.parseToJsonElement(responseBody).jsonObject
            val choices = parsed["choices"]?.jsonArray
                ?: throw Exception("No choices in response")
            val firstChoice = choices[0].jsonObject
            val message = firstChoice["message"]?.jsonObject
                ?: throw Exception("No message in choice")
            message["content"]?.jsonPrimitive?.content
                ?: throw Exception("No content in message")
        }
}
