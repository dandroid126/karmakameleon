package com.karmakameleon.android.notifications

import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.karmakameleon.android.testing.testMessage
import com.karmakameleon.shared.domain.model.InboxFilter
import com.karmakameleon.shared.domain.model.NotificationInterval
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class InboxNotificationWorkerTest {
    @get:Rule
    val dependencies = NotificationTestDependencies()

    private val context = RuntimeEnvironment.getApplication()
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications

    @Before
    fun setUpWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun runWorker(): ListenableWorker.Result =
        runBlocking { TestListenableWorkerBuilder<InboxNotificationWorker>(context).build().doWork() }

    @Test
    fun firstRunRemembersMessagesWithoutNotifying() {
        dependencies.redditApi.unreadMessages = listOf(testMessage("a"))

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(listOf(InboxFilter.UNREAD), dependencies.redditApi.requestedFilters)
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun laterRunNotifiesOnlyNewMessages() {
        dependencies.redditApi.unreadMessages = listOf(testMessage("a"))
        runWorker()

        dependencies.redditApi.unreadMessages = listOf(testMessage("a"), testMessage("b", body = "New one"))
        runWorker()

        val notification = notifications.single()
        assertEquals("New one", notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
    }

    @Test
    fun runWithNoNewMessagesDoesNotNotify() {
        dependencies.redditApi.unreadMessages = listOf(testMessage("a"))
        runWorker()
        runWorker()

        assertTrue(notifications.isEmpty())
    }

    @Test
    fun failedRequestKeepsKnownMessages() {
        dependencies.redditApi.unreadMessages = listOf(testMessage("a"))
        runWorker()

        dependencies.redditApi.unreadMessages = null
        assertEquals(ListenableWorker.Result.success(), runWorker())

        // Had the failure been taken as an empty inbox, "a" would come back as new here.
        dependencies.redditApi.unreadMessages = listOf(testMessage("a"))
        runWorker()
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun doesNothingWhenNotificationsAreOff() {
        dependencies.settingsRepository.setNotificationInterval(NotificationInterval.OFF)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertTrue(dependencies.redditApi.requestedFilters.isEmpty())
    }

    @Test
    fun doesNothingWhenLoggedOut() {
        dependencies.authManager.fakeIsLoggedIn = false

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertTrue(dependencies.redditApi.requestedFilters.isEmpty())
    }

    private fun scheduledWork(): List<WorkInfo> =
        runBlocking {
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(InboxNotificationWorker.WORK_NAME).first()
        }

    @Test
    fun scheduleRunsWorkerAtChosenInterval() {
        dependencies.settingsRepository.setNotificationInterval(NotificationInterval.SIX_HOURS)

        InboxNotificationWorker.schedule(context)

        val work = scheduledWork().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(TimeUnit.HOURS.toMillis(6), work.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun scheduleCancelsWorkWhenNotificationsAreTurnedOff() {
        InboxNotificationWorker.schedule(context)

        dependencies.settingsRepository.setNotificationInterval(NotificationInterval.OFF)
        InboxNotificationWorker.schedule(context)

        assertEquals(WorkInfo.State.CANCELLED, scheduledWork().single().state)
    }

    @Test
    fun scheduleCancelsWorkWhenLoggedOut() {
        InboxNotificationWorker.schedule(context)

        dependencies.authManager.fakeIsLoggedIn = false
        InboxNotificationWorker.schedule(context)

        assertEquals(WorkInfo.State.CANCELLED, scheduledWork().single().state)
    }

    @Test
    fun bootSchedulesWorker() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertEquals(WorkInfo.State.ENQUEUED, scheduledWork().single().state)
    }

    @Test
    fun otherBroadcastsDoNotScheduleWorker() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_TIME_CHANGED))

        assertTrue(scheduledWork().isEmpty())
    }
}
