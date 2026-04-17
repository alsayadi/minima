package com.minima.os.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Debug-only: posts a self-notification with a RemoteInput action so the
 * quick-reply path can be exercised without installing a real messenger app.
 *
 * Trigger:
 *   adb shell am broadcast -a com.minima.os.debug.POST_REPLY_NOTIF
 *
 * Tap "Reply" on the resulting card in Minima — the inline send fires the
 * PendingIntent, which is captured by [ReplyConsumerReceiver] and logged.
 */
class PostReplyNotifReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Mom"
        val text = intent.getStringExtra("text") ?: "hey — are we still on for dinner sunday?"
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val channelId = "minima_debug_reply"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Minima Debug Reply", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
        }

        val replyKey = "minima_debug_reply_key"
        val remoteInput = RemoteInput.Builder(replyKey).setLabel("Reply").build()

        val replyConsumerIntent = Intent(context, ReplyConsumerReceiver::class.java).apply {
            action = "com.minima.os.debug.REPLY_CONSUMED"
        }
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val replyPi = PendingIntent.getBroadcast(context, 0, replyConsumerIntent, piFlags)

        val replyAction = Notification.Action.Builder(
            android.R.drawable.ic_menu_send, "Reply", replyPi
        ).addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .build()

        // Tap-the-card target — opens Minima so we have somewhere to land.
        val openMinima = Intent(context, com.minima.os.LauncherActivity::class.java)
        val openPi = PendingIntent.getActivity(context, 1, openMinima, PendingIntent.FLAG_IMMUTABLE)

        val notif = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(openPi)
            .addAction(replyAction)
            .setAutoCancel(true)
            .build()

        nm.notify(MinimaDebugIds.REPLY_TEST_NOTIFICATION_ID, notif)
    }
}

/**
 * Captures the user's reply when they hit Send in Minima's inline reply UI.
 * Logs the text via Logcat so we can verify in tests.
 */
class ReplyConsumerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val results = RemoteInput.getResultsFromIntent(intent) ?: return
        val text = results.getCharSequence("minima_debug_reply_key")?.toString() ?: return
        android.util.Log.i("MinimaReplyConsumer", "Got reply: $text")
        // Cancel the source notification (real messengers do this automatically).
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.cancel(MinimaDebugIds.REPLY_TEST_NOTIFICATION_ID)
    }
}

object MinimaDebugIds {
    const val REPLY_TEST_NOTIFICATION_ID = 90210
}
