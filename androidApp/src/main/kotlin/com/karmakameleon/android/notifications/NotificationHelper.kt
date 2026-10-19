package com.karmakameleon.android.notifications

import android.Manifest
import android.R.drawable
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.karmakameleon.android.MainActivity
import com.karmakameleon.shared.domain.model.Message
import com.karmakameleon.shared.domain.model.MessageType

object NotificationHelper {
    const val CHANNEL_ID = "inbox_notifications"
    const val EXTRA_OPEN_INBOX_UNREAD = "open_inbox_unread"
    private const val CHANNEL_NAME = "Inbox"
    private const val CHANNEL_DESCRIPTION = "Notifications for new inbox messages"
    private const val SUMMARY_NOTIFICATION_ID = 0

    // How much of a message to show: one message's text and expanded text, and per line when several are grouped.
    private const val CONTENT_TEXT_MAX_CHARS = 200
    private const val BIG_TEXT_MAX_CHARS = 500
    private const val INBOX_LINE_MAX_CHARS = 80
    private const val MAX_INBOX_LINES = 5

    @RequiresApi(Build.VERSION_CODES.O)
    fun createChannel(context: Context) {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = CHANNEL_DESCRIPTION
            }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showNotifications(
        context: Context,
        newMessages: List<Message>,
    ) {
        if (newMessages.isEmpty()) return

        val notificationManager = NotificationManagerCompat.from(context)
        val tapIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(EXTRA_OPEN_INBOX_UNREAD, true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        if (newMessages.size == 1) {
            val message = newMessages.first()
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(drawable.ic_dialog_email)
                    .setContentTitle(buildTitle(message))
                    .setContentText(message.body.take(CONTENT_TEXT_MAX_CHARS))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message.body.take(BIG_TEXT_MAX_CHARS)))
                    .setContentIntent(tapIntent)
                    .setAutoCancel(true)
                    .build()
            notificationManager.notify(message.id.hashCode(), notification)
        } else {
            val inboxStyle = NotificationCompat.InboxStyle()
            newMessages.take(MAX_INBOX_LINES).forEach { message ->
                inboxStyle.addLine("${buildTitle(message)}: ${message.body.take(INBOX_LINE_MAX_CHARS)}")
            }
            if (newMessages.size > MAX_INBOX_LINES) {
                inboxStyle.setSummaryText("+${newMessages.size - MAX_INBOX_LINES} more")
            }
            val summary =
                NotificationCompat
                    .Builder(context, CHANNEL_ID)
                    .setSmallIcon(drawable.ic_dialog_email)
                    .setContentTitle("${newMessages.size} new inbox messages")
                    .setContentText(newMessages.joinToString(", ") { buildTitle(it) }.take(200))
                    .setStyle(inboxStyle)
                    .setContentIntent(tapIntent)
                    .setAutoCancel(true)
                    .setGroupSummary(true)
                    .setGroup(CHANNEL_ID)
                    .build()
            notificationManager.notify(SUMMARY_NOTIFICATION_ID, summary)
        }
    }

    private fun buildTitle(message: Message): String {
        val author = message.author ?: "Unknown"
        return when (message.type) {
            MessageType.COMMENT_REPLY -> "u/$author replied to your comment"
            MessageType.POST_REPLY -> "u/$author replied to your post"
            MessageType.USERNAME_MENTION -> "u/$author mentioned you"
            MessageType.PRIVATE_MESSAGE -> "Message from u/$author"
            MessageType.MOD_MESSAGE -> "Mod message from u/$author"
        }
    }
}
