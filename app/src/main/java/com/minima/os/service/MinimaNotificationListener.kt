package com.minima.os.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.minima.os.core.model.NotificationCategory
import com.minima.os.core.model.NotificationInfo
import com.minima.os.core.model.NotificationPriority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class MinimaNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras

        val info = NotificationInfo(
            id = sbn.key,
            packageName = sbn.packageName,
            appName = extras.getString("android.title.app", sbn.packageName),
            title = extras.getCharSequence("android.title")?.toString() ?: "",
            text = extras.getCharSequence("android.text")?.toString() ?: "",
            category = categorize(notification.category, sbn.packageName),
            priority = mapPriority(notification.priority),
            timestamp = sbn.postTime,
            isOngoing = sbn.isOngoing,
            actions = notification.actions?.map { it.title.toString() } ?: emptyList()
        )

        val current = _notifications.value.toMutableList()
        current.removeAll { it.id == info.id }
        current.add(0, info)
        _notifications.value = current
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        _notifications.value = _notifications.value.filter { it.id != sbn.key }
    }

    private fun categorize(category: String?, packageName: String): NotificationCategory {
        return when {
            category == "msg" -> NotificationCategory.MESSAGE
            category == "email" -> NotificationCategory.EMAIL
            category == "social" -> NotificationCategory.SOCIAL
            category == "event" -> NotificationCategory.CALENDAR
            category == "transport" -> NotificationCategory.TRANSPORT
            category == "promo" -> NotificationCategory.PROMO
            category == "sys" -> NotificationCategory.SYSTEM
            packageName.contains("messaging") || packageName.contains("whatsapp") ||
                packageName.contains("telegram") -> NotificationCategory.MESSAGE
            packageName.contains("gmail") || packageName.contains("mail") -> NotificationCategory.EMAIL
            packageName.contains("twitter") || packageName.contains("instagram") ||
                packageName.contains("facebook") -> NotificationCategory.SOCIAL
            else -> NotificationCategory.OTHER
        }
    }

    private fun mapPriority(priority: Int): NotificationPriority {
        return when {
            priority >= 2 -> NotificationPriority.URGENT
            priority == 1 -> NotificationPriority.HIGH
            priority == 0 -> NotificationPriority.DEFAULT
            else -> NotificationPriority.LOW
        }
    }

    companion object {
        private val _notifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
        val notifications: StateFlow<List<NotificationInfo>> = _notifications.asStateFlow()

        fun getActiveNotifications(): List<NotificationInfo> = _notifications.value
    }
}
