package com.minima.os.core.model

import kotlinx.serialization.Serializable

@Serializable
data class NotificationInfo(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val category: NotificationCategory = NotificationCategory.OTHER,
    val priority: NotificationPriority = NotificationPriority.DEFAULT,
    val timestamp: Long = System.currentTimeMillis(),
    val isOngoing: Boolean = false,
    val actions: List<String> = emptyList()
)

@Serializable
enum class NotificationCategory {
    MESSAGE,
    EMAIL,
    SOCIAL,
    CALENDAR,
    TRANSPORT,
    FINANCE,
    NEWS,
    PROMO,
    SYSTEM,
    OTHER
}

@Serializable
enum class NotificationPriority {
    URGENT,
    HIGH,
    DEFAULT,
    LOW
}
