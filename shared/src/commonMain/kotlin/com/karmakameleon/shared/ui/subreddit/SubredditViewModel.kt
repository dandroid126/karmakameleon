package com.karmakameleon.shared.ui.subreddit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.SubredditRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.domain.model.Subreddit
import com.karmakameleon.shared.ui.PostList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubredditUiState(
    val subreddit: Subreddit? = null,
    val isLoggedIn: Boolean = false,
)

class SubredditViewModel(
    private val subredditName: String,
    private val subredditRepository: SubredditRepository,
    postRepository: PostRepository,
    private val userRepository: UserRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SubredditUiState())
    val uiState: StateFlow<SubredditUiState> = _uiState.asStateFlow()

    val postList = PostList(viewModelScope, postRepository, settingsRepository, hideBlockedSubreddits = false) { subredditName }

    init {
        viewModelScope.launch {
            userRepository.isLoggedIn.collect { isLoggedIn ->
                _uiState.update { it.copy(isLoggedIn = isLoggedIn) }
            }
        }
        loadSubreddit()
        postList.load()
    }

    private fun loadSubreddit() {
        viewModelScope.launch {
            val result = subredditRepository.getSubreddit(subredditName)
            result.onSuccess { subreddit ->
                _uiState.update { it.copy(subreddit = subreddit) }
            }
        }
    }

    fun toggleSubscribe() {
        val subreddit = _uiState.value.subreddit ?: return
        viewModelScope.launch {
            val result = subredditRepository.subscribe(subreddit)
            result.onSuccess { updated ->
                _uiState.update { it.copy(subreddit = updated) }
            }
        }
    }
}
