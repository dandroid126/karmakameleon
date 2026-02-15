package com.reader.shared.di

import com.reader.shared.data.api.AuthManager
import com.reader.shared.data.api.RedditApi
import com.reader.shared.data.api.createHttpClientWithConfig
import com.reader.shared.data.repository.CommentDraftRepository
import com.reader.shared.data.repository.CommentRepository
import com.reader.shared.data.repository.MessageRepository
import com.reader.shared.data.repository.PostRepository
import com.reader.shared.data.repository.ReadPostsRepository
import com.reader.shared.data.repository.SettingsRepository
import com.reader.shared.data.repository.SubredditRepository
import com.reader.shared.data.repository.UserRepository
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedModule = module {
    single { createHttpClientWithConfig() }
    single { Settings() }
    single { AuthManager(get(), get()) }
    single { RedditApi(get(), get()) }
    
    // Repositories
    single { PostRepository(get()) }
    single { SubredditRepository(get(), get()) }
    single { UserRepository(get(), get()) }
    single { CommentRepository(get()) }
    single { MessageRepository(get()) }
    single { SettingsRepository(get()) }
    single { ReadPostsRepository(get()) }
    single { CommentDraftRepository(get()) }
}

expect fun platformModule(): Module
