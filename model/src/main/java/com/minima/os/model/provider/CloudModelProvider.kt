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

enum class Provider(
    val displayName: String,
    val baseUrl: String,
    val defaultModel: String,
    val format: Format
) {
    OPENAI("OpenAI", "https://api.openai.com/v1/chat/completions", "gpt-4o", Format.OPENAI),
    GROQ("Groq (free)", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile", Format.OPENAI),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1/chat/completions", "deepseek-chat", Format.OPENAI),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "openai/gpt-4o-mini", Format.OPENAI),
    ANTHROPIC("Anthropic Claude", "https://api.anthropic.com/v1/messages", "claude-3-5-sonnet-20241022", Format.ANTHROPIC),
    GEMINI("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/models", "gemini-2.0-flash", Format.GEMINI);

    enum class Format { OPENAI, ANTHROPIC, GEMINI }
}

@Singleton
class CloudModelProvider @Inject constructor() : ModelProvider {

    override val name get() = "${provider.displayName}/$model"
    override val isLocal = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json".toMediaType()

    private var apiKey: String? = null
    private var provider: Provider = Provider.OPENAI
    private var baseUrl: String = Provider.OPENAI.baseUrl
    private var model: String = Provider.OPENAI.defaultModel

    fun configure(apiKey: String, baseUrl: String? = null, model: String? = null) {
        this.apiKey = apiKey
        baseUrl?.let { this.baseUrl = it }
        model?.let { this.model = it }
    }

    /** Select provider preset. Model defaults to provider's default unless overridden. */
    fun configureProvider(provider: Provider, apiKey: String, model: String? = null) {
        this.provider = provider
        this.apiKey = apiKey
        this.baseUrl = provider.baseUrl
        this.model = model?.takeIf { it.isNotBlank() } ?: provider.defaultModel
    }

    fun currentProvider(): Provider = provider
    fun currentModel(): String = model

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
FLASHLIGHT, OPEN_CAMERA, MUSIC_CONTROL, CREATE_CALENDAR_EVENT, GET_WEATHER,
SET_ALARM, CALL_CONTACT, CONVERT, SUMMARIZE_NOTIFICATIONS,
REMEMBER, RECALL, ANSWER, UNKNOWN

Rules:
- Use ANSWER for ANY question, conversation, greeting, joke, math, translation, explanation, or general knowledge request.
- Use GET_WEATHER for real-time weather queries ("weather", "is it hot", "will it rain", "forecast"). Pass location in params if mentioned.
- Use FLASHLIGHT for torch/light commands. params.mode = "on" | "off" | "toggle".
- Use OPEN_CAMERA for "take photo", "camera", "selfie".
- Use MUSIC_CONTROL for music playback. params.action = "play" | "pause" | "next" | "prev" | "toggle".
- Use CREATE_CALENDAR_EVENT for "schedule a meeting", "add event to calendar". params.title = event title.
- Use SEARCH only when the user explicitly wants to search the web for something other than weather.
- Use REMEMBER when the user states personal facts ("my name is...", "I like...", "I live in...").
- Use RECALL when the user asks what you know/remember about them.
- Use OPEN_APP when user wants to open or list apps. If no specific app name, set appName to empty string.
- NEVER return UNKNOWN if the input is a valid question or request. Use ANSWER as the default for anything conversational.

Respond ONLY with valid JSON, no markdown, no explanation:
{"type": "INTENT_TYPE", "confidence": "HIGH|MEDIUM|LOW", "params": {"key": "value"}}

Examples:
- "remind me to buy milk at 5pm" -> {"type": "SET_REMINDER", "confidence": "HIGH", "params": {"description": "buy milk", "time": "5pm"}}
- "what's the weather" -> {"type": "GET_WEATHER", "confidence": "HIGH", "params": {"location": ""}}
- "weather in London" -> {"type": "GET_WEATHER", "confidence": "HIGH", "params": {"location": "London"}}
- "turn on the flashlight" -> {"type": "FLASHLIGHT", "confidence": "HIGH", "params": {"mode": "on"}}
- "take a photo" -> {"type": "OPEN_CAMERA", "confidence": "HIGH", "params": {}}
- "pause the music" -> {"type": "MUSIC_CONTROL", "confidence": "HIGH", "params": {"action": "pause"}}
- "next song" -> {"type": "MUSIC_CONTROL", "confidence": "HIGH", "params": {"action": "next"}}
- "schedule a meeting with John tomorrow" -> {"type": "CREATE_CALENDAR_EVENT", "confidence": "HIGH", "params": {"title": "meeting with John"}}
- "wake me up at 7am" -> {"type": "SET_ALARM", "confidence": "HIGH", "params": {"time": "7am"}}
- "set a timer for 10 minutes" -> {"type": "SET_ALARM", "confidence": "HIGH", "params": {"minutes": "10"}}
- "call Mom" -> {"type": "CALL_CONTACT", "confidence": "HIGH", "params": {"name": "Mom"}}
- "50 USD in EUR" -> {"type": "CONVERT", "confidence": "HIGH", "params": {"value": "50", "from": "USD", "to": "EUR"}}
- "10 miles in km" -> {"type": "CONVERT", "confidence": "HIGH", "params": {"value": "10", "from": "mi", "to": "km"}}
- "what did I miss" -> {"type": "SUMMARIZE_NOTIFICATIONS", "confidence": "HIGH", "params": {}}
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
            val key = apiKey ?: throw IllegalStateException("${provider.displayName} API key not configured")
            android.util.Log.d("CloudModelProvider", "Calling ${provider.name}/$model")

            val escapedSystem = systemPrompt.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            val escapedUser = userMessage.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

            val (url, body, headers) = when (provider.format) {
                Provider.Format.OPENAI -> {
                    val b = """{"model":"$model","messages":[{"role":"system","content":"$escapedSystem"},{"role":"user","content":"$escapedUser"}],"max_tokens":1024,"temperature":0.3}"""
                    Triple(baseUrl, b, mapOf("Authorization" to "Bearer $key", "Content-Type" to "application/json"))
                }
                Provider.Format.ANTHROPIC -> {
                    val b = """{"model":"$model","max_tokens":1024,"system":"$escapedSystem","messages":[{"role":"user","content":"$escapedUser"}]}"""
                    Triple(baseUrl, b, mapOf(
                        "x-api-key" to key,
                        "anthropic-version" to "2023-06-01",
                        "Content-Type" to "application/json"
                    ))
                }
                Provider.Format.GEMINI -> {
                    val u = "$baseUrl/$model:generateContent?key=$key"
                    val b = """{"system_instruction":{"parts":[{"text":"$escapedSystem"}]},"contents":[{"parts":[{"text":"$escapedUser"}]}],"generationConfig":{"temperature":0.3,"maxOutputTokens":1024}}"""
                    Triple(u, b, mapOf("Content-Type" to "application/json"))
                }
            }

            val reqBuilder = Request.Builder().url(url).post(body.toRequestBody(mediaType))
            headers.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
            val response = client.newCall(reqBuilder.build()).execute()
            val responseBody = response.body?.string() ?: throw Exception("Empty response")

            if (!response.isSuccessful) {
                throw Exception("${provider.displayName} API error ${response.code}: $responseBody")
            }

            val parsed = json.parseToJsonElement(responseBody).jsonObject
            when (provider.format) {
                Provider.Format.OPENAI -> {
                    val choices = parsed["choices"]?.jsonArray ?: throw Exception("No choices")
                    choices[0].jsonObject["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content
                        ?: throw Exception("No content")
                }
                Provider.Format.ANTHROPIC -> {
                    val content = parsed["content"]?.jsonArray ?: throw Exception("No content array")
                    content[0].jsonObject["text"]?.jsonPrimitive?.content
                        ?: throw Exception("No text")
                }
                Provider.Format.GEMINI -> {
                    val candidates = parsed["candidates"]?.jsonArray ?: throw Exception("No candidates")
                    val content = candidates[0].jsonObject["content"]?.jsonObject ?: throw Exception("No content")
                    val parts = content["parts"]?.jsonArray ?: throw Exception("No parts")
                    parts[0].jsonObject["text"]?.jsonPrimitive?.content
                        ?: throw Exception("No text")
                }
            }
        }
}
