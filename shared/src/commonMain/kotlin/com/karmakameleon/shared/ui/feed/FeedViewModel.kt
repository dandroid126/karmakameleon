package com.karmakameleon.shared.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.ui.PostList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FeedType(
    val displayName: String,
    val subreddit: String?,
) {
    HOME("Home", null),
    ALL("r/all", "all"),
    POPULAR("r/popular", "popular"),
}

data class FeedUiState(
    val currentSubreddit: String? = null,
    val currentFeedType: FeedType = FeedType.HOME,
    val isLoggedIn: Boolean = false,
)

class FeedViewModel(
    postRepository: PostRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    val postList =
        PostList(viewModelScope, postRepository, settingsRepository, hideBlockedSubreddits = true) {
            _uiState.value.currentSubreddit
        }

    init {
        viewModelScope.launch {
            combine(
                userRepository.isLoggedIn,
                settingsRepository.blockedSubreddits,
                settingsRepository.nsfwEnabled,
            ) { isLoggedIn, blockedSubs, nsfwEnabled ->
                Triple(isLoggedIn, blockedSubs, nsfwEnabled)
            }.collect { (isLoggedIn, blockedSubs, nsfwEnabled) ->
                _uiState.update { it.copy(isLoggedIn = isLoggedIn) }
                postList.removeHiddenPosts(nsfwEnabled, blockedSubs)
            }
        }
        postList.load()
    }

    fun setSubreddit(subreddit: String?) {
        if (_uiState.value.currentSubreddit == subreddit) return
        _uiState.update { it.copy(currentSubreddit = subreddit) }
        postList.reload()
    }

    fun setFeedType(feedType: FeedType) {
        if (_uiState.value.currentFeedType == feedType) return
        _uiState.update { it.copy(currentFeedType = feedType, currentSubreddit = feedType.subreddit) }
        postList.reload()
    }

    fun blockSubreddit(subreddit: String) {
        settingsRepository.addBlockedSubreddit(subreddit)
    }
}
