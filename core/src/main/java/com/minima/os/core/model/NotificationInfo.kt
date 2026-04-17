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
    val actions: List<String> = emptyList(),
    /**
     * True if at least one of the notification's actions exposes a RemoteInput
     * (the standard "type a reply without opening the app" mechanism, e.g.
     * Messages, WhatsApp, Telegram). The UI shows an inline reply field for
     * these; the listener service performs the actual send via the source
     * app's PendingIntent.
     */
    val canReply: Boolean = false,
    /**
     * Stable per-conversation key. Notifications sharing a groupKey are
     * collapsed into one card in the strip (e.g. five messages from "Mom"
     * become a single "Mom (5)" card). Falls back to a synthetic
     * "<package>:<title>" so messengers that don't set group still collapse
     * by sender. Null only if neither is available.
     */
    val groupKey: String? = null,
    /**
     * True for the synthetic "summary" notification that some apps post to
     * represent a group. We hide these in the strip — our own grouping is
     * the summary.
     */
    val isGroupSummary: Boolean = false
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
