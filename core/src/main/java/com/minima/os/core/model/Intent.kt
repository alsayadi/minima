package com.minima.os.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ClassifiedIntent(
    val type: IntentType,
    val confidence: Confidence,
    val params: Map<String, String> = emptyMap(),
    val rawInput: String = ""
)

@Serializable
enum class IntentType {
    CREATE_EVENT,
    READ_CALENDAR,
    SET_REMINDER,
    SEND_MESSAGE,
    READ_MESSAGES,
    TRIAGE_NOTIFICATIONS,
    DISMISS_NOTIFICATION,
    REPLY_NOTIFICATION,
    ORDER_RIDE,
    ORDER_FOOD,
    SEARCH,
    OPEN_APP,
    DEVICE_SETTING,
    REMEMBER,
    RECALL,
    ANSWER,
    FLASHLIGHT,
    OPEN_CAMERA,
    MUSIC_CONTROL,
    CREATE_CALENDAR_EVENT,
    GET_WEATHER,
    UNKNOWN
}

@Serializable
enum class Confidence {
    HIGH,
    MEDIUM,
    LOW
}
