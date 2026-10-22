package com.karmakameleon.android.notifications

import com.karmakameleon.android.testing.FakeRedditApi
import com.karmakameleon.shared.data.api.AuthManager
import com.karmakameleon.shared.data.repository.MessageRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.domain.model.NotificationInterval
import com.karmakameleon.shared.testing.FakeAuthManager
import com.russhwolf.settings.MapSettings
import org.junit.rules.ExternalResource
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

// Starts Koin with the fakes the worker and its scheduling look up, and stops it after each test.
class NotificationTestDependencies : ExternalResource() {
    val authManager = FakeAuthManager().apply { fakeIsLoggedIn = true }
    val redditApi = FakeRedditApi(authManager)
    val settingsRepository = SettingsRepository(MapSettings())

    override fun before() {
        settingsRepository.setNotificationInterval(NotificationInterval.ONE_HOUR)
        startKoin {
            modules(
                module {
                    single<AuthManager> { authManager }
                    single { MessageRepository(redditApi) }
                    single { settingsRepository }
                },
            )
        }
    }

    override fun after() {
        stopKoin()
    }
}
