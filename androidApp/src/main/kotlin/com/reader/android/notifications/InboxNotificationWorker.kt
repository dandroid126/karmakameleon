package com.reader.android.notifications

import android.content.Context
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reader.shared.data.api.AuthManager
import com.reader.shared.data.repository.MessageRepository
import com.reader.shared.data.repository.SettingsRepository
import com.reader.shared.domain.model.InboxFilter
import com.reader.shared.domain.model.NotificationInterval
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

class InboxNotificationWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val messageRepository: MessageRepository by inject()
    private val authManager: AuthManager by inject()
    private val settingsRepository: SettingsRepository by inject()

    override suspend fun doWork(): Result {
        if (settingsRepository.notificationInterval.value == NotificationInterval.OFF) return Result.success()
        if (!authManager.isLoggedIn()) return Result.success()

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val knownIdsRaw = prefs.getString(KEY_KNOWN_IDS, "") ?: ""
        val isFirstRun = prefs.getBoolean(KEY_FIRST_RUN, true)

        val knownIds = if (knownIdsRaw.isEmpty()) emptySet()
                       else knownIdsRaw.split(",").toSet()

        val result = messageRepository.getInbox(InboxFilter.UNREAD)
        val messages = result.getOrNull()?.items ?: return Result.success()

        val allCurrentIds = messages.map { it.id }.toSet()
        prefs.edit {
            putString(KEY_KNOWN_IDS, allCurrentIds.joinToString(","))
                .putBoolean(KEY_FIRST_RUN, false)
        }

        if (!isFirstRun) {
            val newMessages = messages.filter { it.id !in knownIds }
            if (newMessages.isNotEmpty()) {
                NotificationHelper.showNotifications(context, newMessages)
            }
        }

        return Result.success()
    }

    companion object {
        private const val PREFS_NAME = "inbox_notification_prefs"
        private const val KEY_KNOWN_IDS = "known_message_ids"
        private const val KEY_FIRST_RUN = "first_run"
        const val WORK_NAME = "inbox_notification_worker"
        private const val MIN_INTERVAL_MINUTES = 15L

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            val koin = GlobalContext.get()
            val authManager = koin.get<AuthManager>()
            val settingsRepository = koin.get<SettingsRepository>()
            val interval = settingsRepository.notificationInterval.value

            if (!authManager.isLoggedIn() || interval == NotificationInterval.OFF) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val request = PeriodicWorkRequestBuilder<InboxNotificationWorker>(
                interval.minutes!!, TimeUnit.MINUTES
            ).build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request
            )
        }
    }
}
