package com.minima.os.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.minima.os.core.bus.NotificationHub
import com.minima.os.core.model.NotificationCategory
import com.minima.os.core.model.NotificationInfo
import com.minima.os.core.model.NotificationPriority
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap

@AndroidEntryPoint
class MinimaNotificationListener : NotificationListenerService() {

    // Kept in-process so we can resolve a key → original StatusBarNotification
    // on demand when the UI wants to fire a contentIntent. The hub itself only
    // carries the serializable NotificationInfo, not the platform object.
    private val byKey = ConcurrentHashMap<String, StatusBarNotification>()

    private val binder = object : NotificationHub.Binder {
        override fun dismiss(key: String) {
            runCatching { cancelNotification(key) }
                .onFailure { Log.w(TAG, "dismiss($key) failed: ${it.message}") }
            byKey.remove(key)
        }

        override fun open(key: String) {
            val sbn = byKey[key] ?: return
            val pi: PendingIntent = sbn.notification?.contentIntent ?: return
            runCatching { pi.send() }
                .onFailure { Log.w(TAG, "open($key) failed: ${it.message}") }
        }

        override fun reply(key: String, text: String) {
            val sbn = byKey[key] ?: return
            val replyAction = sbn.notification?.findReplyAction() ?: run {
                Log.w(TAG, "reply($key) — no RemoteInput action on this notification")
                return
            }
            val remoteInput = replyAction.remoteInputs?.firstOrNull() ?: return
            runCatching {
                val fillIn = Intent()
                val results = Bundle().apply { putCharSequence(remoteInput.resultKey, text) }
                RemoteInput.addResultsToIntent(arrayOf(remoteInput), fillIn, results)
                replyAction.actionIntent.send(this@MinimaNotificationListener, 0, fillIn)
            }.onFailure { Log.w(TAG, "reply($key) failed: ${it.message}") }
            byKey.remove(key)
        }
    }

    /** First action whose RemoteInput list is non-empty (canonical "reply" action). */
    private fun Notification.findReplyAction(): Notification.Action? =
        actions?.firstOrNull { it.remoteInputs?.isNotEmpty() == true }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationHub.bind(binder)
        // Seed with whatever's already in the shade so the UI isn't empty on cold boot.
        runCatching {
            activeNotifications?.forEach { handlePosted(it) }
        }
    }

    override fun onListenerDisconnected() {
        NotificationHub.unbind(binder)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handlePosted(sbn)
    }

    private fun handlePosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        val extras = notification.extras

        byKey[sbn.key] = sbn

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
            actions = notification.actions?.map { it.title.toString() } ?: emptyList(),
            canReply = notification.findReplyAction() != null
        )

        NotificationHub.upsert(info)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        byKey.remove(sbn.key)
        NotificationHub.remove(sbn.key)
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
        private const val TAG = "MinimaNotifListener"
    }
}
