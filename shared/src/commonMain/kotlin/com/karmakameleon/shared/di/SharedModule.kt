package com.karmakameleon.shared.di

import com.karmakameleon.shared.data.api.AuthManager
import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.data.api.createHttpClientWithConfig
import com.karmakameleon.shared.data.repository.CommentDraftRepository
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.data.repository.InboxPoller
import com.karmakameleon.shared.data.repository.MessageRepository
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.ReadPostsRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.SubredditRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedModule = module {
    single { createHttpClientWithConfig() }
    single { Settings() }
    single { AuthManager(get(), get()) }
    single { SettingsRepository(get()) }
    single { RedditApi(get(), get(), get()) }
    
    // Repositories
    single { PostRepository(get()) }
    single { SubredditRepository(get(), get()) }
    single { UserRepository(get(), get()) }
    single { CommentRepository(get()) }
    single { MessageRepository(get()) }
    single { InboxPoller(get(), get()) }
    single { ReadPostsRepository(get()) }
    single { CommentDraftRepository(get()) }
}

expect fun platformModule(): Module
