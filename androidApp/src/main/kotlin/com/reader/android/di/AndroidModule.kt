package com.reader.android.di

import com.reader.shared.ui.feed.FeedViewModel
import com.reader.shared.ui.inbox.InboxViewModel
import com.reader.shared.ui.post.PostDetailViewModel
import com.reader.shared.ui.profile.ProfileViewModel
import com.reader.shared.ui.search.SearchViewModel
import com.reader.shared.ui.subreddit.SubredditViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val androidModule = module {
    // ViewModels (Android-specific registration using viewModel DSL)
    viewModel { FeedViewModel(get(), get(), get(), get()) }
    viewModel { params -> PostDetailViewModel(params[0], params[1], get(), get(), get(), get(), params.values.getOrNull(2) as? String) }
    viewModel { params -> SubredditViewModel(params.get(), get(), get(), get()) }
    viewModel { ProfileViewModel(get(), get(), get()) }
    viewModel { InboxViewModel(get(), get()) }
    viewModel { SearchViewModel(get(), get()) }
}
