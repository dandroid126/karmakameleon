package com.reader.android.di

import com.reader.android.data.ReadPostsRepository
import com.reader.android.ui.feed.FeedViewModel
import com.reader.android.ui.post.PostDetailViewModel
import com.reader.android.ui.subreddit.SubredditViewModel
import com.reader.android.ui.profile.ProfileViewModel
import com.reader.android.ui.inbox.InboxViewModel
import com.reader.android.ui.search.SearchViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val androidModule = module {
    single { ReadPostsRepository(androidContext()) }
    viewModel { FeedViewModel(get(), get(), get()) }
    viewModel { params -> PostDetailViewModel(params.get(), params.get(), get(), get(), get()) }
    viewModel { params -> SubredditViewModel(params.get(), get(), get(), get()) }
    viewModel { ProfileViewModel(get(), get()) }
    viewModel { InboxViewModel(get(), get()) }
    viewModel { SearchViewModel(get(), get()) }
}
