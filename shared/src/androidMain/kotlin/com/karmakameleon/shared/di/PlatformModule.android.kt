package com.karmakameleon.shared.di

import android.content.Context
import com.karmakameleon.shared.data.api.HttpClientFactory
import com.karmakameleon.shared.data.api.OkHttpClientFactory
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.dsl.module

// androidApp's backup rules exclude this file (res/xml/backup_rules.xml and data_extraction_rules.xml), so keep
// the names in sync.
private const val TOKEN_SETTINGS_FILE = "auth_tokens"

val platformModule =
    module {
        single<HttpClientFactory> { OkHttpClientFactory }
        single<Settings>(TOKEN_SETTINGS) {
            SharedPreferencesSettings(get<Context>().getSharedPreferences(TOKEN_SETTINGS_FILE, Context.MODE_PRIVATE))
        }
    }
