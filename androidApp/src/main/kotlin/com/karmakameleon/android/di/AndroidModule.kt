package com.karmakameleon.android.di

import com.karmakameleon.android.navigation.NavigationHandler
import com.karmakameleon.android.ui.menu.GlobalMenuManager
import com.karmakameleon.shared.ui.feed.FeedViewModel
import com.karmakameleon.shared.ui.inbox.InboxViewModel
import com.karmakameleon.shared.ui.post.PostDetailViewModel
import com.karmakameleon.shared.ui.profile.ProfileViewModel
import com.karmakameleon.shared.ui.search.SearchViewModel
import com.karmakameleon.shared.ui.subreddit.SubredditViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val androidModule =
    module {
        single { NavigationHandler() }
        single { GlobalMenuManager() }
        // ViewModels (Android-specific registration using viewModel DSL)
        viewModel { FeedViewModel(get(), get(), get(), get()) }
        viewModel { params -> PostDetailViewModel(params.get(), get(), get(), get(), get(), get()) }
        viewModel { params -> SubredditViewModel(params.get(), get(), get(), get(), get()) }
        viewModel { ProfileViewModel(get(), get(), get(), get()) }
        viewModel { InboxViewModel(get(), get(), get()) }
        viewModel { SearchViewModel(get(), get(), get()) }
    }
