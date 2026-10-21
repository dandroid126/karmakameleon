package com.karmakameleon.android.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import androidx.core.app.NotificationCompat
import com.karmakameleon.android.MainActivity
import com.karmakameleon.android.testing.testMessage
import com.karmakameleon.shared.domain.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class NotificationHelperTest {
    private val context = RuntimeEnvironment.getApplication()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val notifications get() = shadowOf(notificationManager).allNotifications

    private val Notification.title get() = extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString()
    private val Notification.text get() = extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString()

    @Test
    fun createChannelAddsInboxChannel() {
        NotificationHelper.createChannel(context)

        val channel = notificationManager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        assertEquals("Inbox", channel.name)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
    }

    @Test
    fun noMessagesShowNothing() {
        NotificationHelper.showNotifications(context, emptyList())

        assertTrue(notifications.isEmpty())
    }

    @Test
    fun oneMessageShowsItsAuthorAndText() {
        NotificationHelper.showNotifications(context, listOf(testMessage("a", body = "x".repeat(300))))

        val notification = notifications.single()
        assertEquals("Message from u/sender", notification.title)
        assertEquals("x".repeat(200), notification.text)
        assertEquals("x".repeat(300), notification.extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT).toString())
    }

    @Test
    fun titleSaysWhatKindOfMessageItIs() {
        val expectedTitles =
            mapOf(
                MessageType.COMMENT_REPLY to "u/sender replied to your comment",
                MessageType.POST_REPLY to "u/sender replied to your post",
                MessageType.USERNAME_MENTION to "u/sender mentioned you",
                MessageType.PRIVATE_MESSAGE to "Message from u/sender",
                MessageType.MOD_MESSAGE to "Mod message from u/sender",
            )
        expectedTitles.forEach { (type, title) ->
            notificationManager.cancelAll()
            NotificationHelper.showNotifications(context, listOf(testMessage(type.name, type = type)))
            assertEquals(title, notifications.single().title)
        }
    }

    @Test
    fun messageWithoutAuthorIsFromUnknown() {
        NotificationHelper.showNotifications(context, listOf(testMessage("a", author = null)))

        assertEquals("Message from u/Unknown", notifications.single().title)
    }

    @Test
    fun severalMessagesShowOneSummaryWithAFewLines() {
        val messages = (1..7).map { testMessage("m$it", author = "user$it", body = "Body $it") }

        NotificationHelper.showNotifications(context, messages)

        val summary = notifications.single()
        assertEquals("7 new inbox messages", summary.title)
        assertTrue(summary.flags and Notification.FLAG_GROUP_SUMMARY != 0)
        val lines = summary.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES).orEmpty()
        assertEquals((1..5).map { "Message from u/user$it: Body $it" }, lines.map { it.toString() })
        assertEquals("+2 more", summary.extras.getCharSequence(NotificationCompat.EXTRA_SUMMARY_TEXT).toString())
    }

    @Test
    fun tappingNotificationOpensUnreadInbox() {
        NotificationHelper.showNotifications(context, listOf(testMessage("a")))

        val intent = shadowOf(notifications.single().contentIntent).savedIntent
        assertEquals(ComponentName(context, MainActivity::class.java), intent.component)
        assertTrue(intent.getBooleanExtra(NotificationHelper.EXTRA_OPEN_INBOX_UNREAD, false))
    }
}
