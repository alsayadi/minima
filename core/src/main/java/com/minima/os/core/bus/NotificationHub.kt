package com.minima.os.core.bus

import com.minima.os.core.model.NotificationInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-level hub for live notifications.
 *
 * Why here and not in the listener service? The service lives in `:app`, but
 * `:ui` needs to read the list and trigger actions (dismiss, open). `:ui`
 * cannot import from `:app`, so the exchange happens through `:core` — both
 * sides depend on core.
 *
 * The service [bind]s a [Binder] that knows how to call the real platform
 * APIs (`cancelNotification`, `PendingIntent.send`). The UI never holds that
 * reference directly; it just calls [dismiss] / [open] and we fan out.
 */
object NotificationHub {

    private val _notifications = MutableStateFlow<List<NotificationInfo>>(emptyList())
    val notifications: StateFlow<List<NotificationInfo>> = _notifications.asStateFlow()

    @Volatile private var binder: Binder? = null

    /**
     * Platform-side bridge. Implemented by [com.minima.os.service.MinimaNotificationListener]
     * which has the `NotificationListenerService` instance needed to cancel
     * notifications and fire their `contentIntent`.
     */
    interface Binder {
        fun dismiss(key: String)
        fun open(key: String)
        /** Quick-reply via the source notification's RemoteInput action. */
        fun reply(key: String, text: String)
    }

    fun bind(b: Binder) { binder = b }
    fun unbind(b: Binder) { if (binder === b) binder = null }

    /** Called by the listener service whenever a notification is posted. */
    fun upsert(info: NotificationInfo) {
        val current = _notifications.value.toMutableList()
        current.removeAll { it.id == info.id }
        current.add(0, info)
        _notifications.value = current
    }

    /** Called by the listener service whenever a notification is removed. */
    fun remove(key: String) {
        _notifications.value = _notifications.value.filter { it.id != key }
    }

    /** Called by UI when the user taps X on a notification card. */
    fun dismiss(key: String) {
        binder?.dismiss(key)
        // Also remove optimistically so the UI disappears the card immediately.
        // If the platform removal arrives later via onNotificationRemoved, it's a no-op.
        remove(key)
    }

    /** Called by UI when the user taps the notification body to open its source. */
    fun open(key: String) {
        binder?.open(key)
    }

    /**
     * Called by UI to send a quick reply to the source notification. The
     * dispatch happens through the source app's PendingIntent — Minima never
     * holds the message after handing it off. Optimistically removes the card
     * since most messaging apps cancel the notification on reply.
     */
    fun reply(key: String, text: String) {
        binder?.reply(key, text)
        remove(key)
    }
}
