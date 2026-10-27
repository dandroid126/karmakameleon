package com.karmakameleon.shared.di

import com.karmakameleon.shared.data.api.AuthManager
import com.karmakameleon.shared.data.api.HttpClientFactory
import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.data.api.createHttpClientWithConfig
import com.karmakameleon.shared.data.repository.CommentDraftRepository
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.data.repository.InboxPoller
import com.karmakameleon.shared.data.repository.MarkdownRepository
import com.karmakameleon.shared.data.repository.MessageRepository
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.ReadPostsRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.SubredditRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.russhwolf.settings.Settings
import org.koin.core.qualifier.named
import org.koin.dsl.module

// The settings that hold the login tokens, which each platform keeps out of backups.
val TOKEN_SETTINGS = named("tokenSettings")

val sharedModule =
    module {
        single { createHttpClientWithConfig(get()) }
        single { Settings() }
        single { AuthManager(get(), get(), get(TOKEN_SETTINGS)) }
        single { SettingsRepository(get()) }
        single {
            RedditApi(get(), get(), get(), createShareLinkClient = { get<HttpClientFactory>().create(followRedirects = false) })
        }

        // Repositories
        single { PostRepository(get(), get()) }
        single { SubredditRepository(get(), get()) }
        single { UserRepository(get(), get()) }
        single { CommentRepository(get()) }
        single { MessageRepository(get()) }
        single { InboxPoller(get(), get()) }
        single { ReadPostsRepository(get()) }
        single { CommentDraftRepository(get()) }
        single { MarkdownRepository() }
    }
