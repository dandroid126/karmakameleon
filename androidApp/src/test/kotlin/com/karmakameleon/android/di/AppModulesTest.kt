package com.karmakameleon.android.di

import android.content.Context
import com.karmakameleon.shared.di.TOKEN_SETTINGS
import com.russhwolf.settings.Settings
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

// The modules the app really starts with, which the other tests replace with fakes. Their settings need a Context, so
// shared's tests can't check them.
@RunWith(RobolectricTestRunner::class)
class AppModulesTest {
    private val context = RuntimeEnvironment.getApplication()
    private val app =
        koinApplication {
            androidContext(context)
            modules(appModules)
        }

    @After
    fun tearDown() {
        app.close()
    }

    @Test
    fun tokensAreSavedInTheFileTheBackupRulesExclude() {
        app.koin.get<Settings>(TOKEN_SETTINGS).putString("token_info", "{}")

        assertTrue(context.getSharedPreferences("auth_tokens", Context.MODE_PRIVATE).contains("token_info"))
    }
}
