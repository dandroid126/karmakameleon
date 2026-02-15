package com.reader.android.di

import com.reader.android.data.CommentDraftRepository
import com.reader.android.data.ReadPostsRepository
import com.reader.android.data.SettingsRepository
import com.reader.android.ui.feed.FeedViewModel
import com.reader.android.ui.inbox.InboxViewModel
import com.reader.android.ui.post.PostDetailViewModel
import com.reader.android.ui.profile.ProfileViewModel
import com.reader.android.ui.search.SearchViewModel
import com.reader.android.ui.subreddit.SubredditViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val androidModule = module {
    single { ReadPostsRepository(androidContext()) }
    single { CommentDraftRepository(androidContext()) }
    single { SettingsRepository(androidContext()) }
    viewModel { FeedViewModel(get(), get(), get(), get()) }
    viewModel { params -> PostDetailViewModel(params[0], params[1], get(), get(), get(), get(), params.values.getOrNull(2) as? String) }
    viewModel { params -> SubredditViewModel(params.get(), get(), get(), get()) }
    viewModel { ProfileViewModel(get(), get()) }
    viewModel { InboxViewModel(get(), get()) }
    viewModel { SearchViewModel(get(), get()) }
}
