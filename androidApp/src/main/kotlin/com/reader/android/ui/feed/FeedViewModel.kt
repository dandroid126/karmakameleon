package com.reader.android.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.android.data.SettingsRepository
import com.reader.shared.data.repository.PostRepository
import com.reader.shared.data.repository.SubredditRepository
import com.reader.shared.data.repository.UserRepository
import com.reader.shared.domain.model.Post
import com.reader.shared.domain.model.PostSort
import com.reader.shared.domain.model.TimeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FeedType(val displayName: String, val subreddit: String?) {
    HOME("Home", null),
    ALL("r/all", "all"),
    POPULAR("r/popular", "popular")
}

data class FeedUiState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val currentSort: PostSort = PostSort.HOT,
    val currentTimeFilter: TimeFilter = TimeFilter.DAY,
    val currentSubreddit: String? = null,
    val currentFeedType: FeedType = FeedType.HOME,
    val after: String? = null,
    val hasMore: Boolean = true,
    val isLoggedIn: Boolean = false
)

class FeedViewModel(
    private val postRepository: PostRepository,
    private val subredditRepository: SubredditRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                userRepository.isLoggedIn,
                settingsRepository.blockedSubreddits
            ) { isLoggedIn, blockedSubs ->
                isLoggedIn to blockedSubs
            }.collect { (isLoggedIn, blockedSubs) ->
                _uiState.update { state ->
                    state.copy(
                        isLoggedIn = isLoggedIn,
                        posts = state.posts.filter { post ->
                            post.subreddit.lowercase() !in blockedSubs.map { it.lowercase() }
                        }
                    )
                }
            }
        }
        loadPosts()
    }

    fun loadPosts(forceRefresh: Boolean = false) {
        if (_uiState.value.isLoading && !forceRefresh) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val result = postRepository.getPosts(
                subreddit = _uiState.value.currentSubreddit,
                sort = _uiState.value.currentSort,
                time = if (_uiState.value.currentSort == PostSort.TOP || 
                          _uiState.value.currentSort == PostSort.CONTROVERSIAL) 
                    _uiState.value.currentTimeFilter else null
            )
            
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            posts = listing.items,
                            after = listing.after,
                            hasMore = listing.hasMore,
                            isLoading = false
                        )
                    }
                    enrichPosts(listing.items)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load posts"
                        )
                    }
                }
            )
        }
    }

    fun loadMorePosts() {
        val currentState = _uiState.value
        if (currentState.isLoadingMore || !currentState.hasMore || currentState.after == null) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            
            val result = postRepository.getPosts(
                subreddit = currentState.currentSubreddit,
                sort = currentState.currentSort,
                time = if (currentState.currentSort == PostSort.TOP || 
                          currentState.currentSort == PostSort.CONTROVERSIAL) 
                    currentState.currentTimeFilter else null,
                after = currentState.after
            )
            
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            posts = it.posts + listing.items,
                            after = listing.after,
                            hasMore = listing.hasMore,
                            isLoadingMore = false
                        )
                    }
                    enrichPosts(listing.items)
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            )
        }
    }

    private suspend fun enrichPosts(posts: List<Post>) {
        val enriched = postRepository.enrichSparsePosts(posts)
        if (enriched.isNotEmpty()) {
            val enrichedMap = enriched.associateBy { it.id }
            _uiState.update { state ->
                state.copy(posts = state.posts.map { enrichedMap[it.id] ?: it })
            }
        }
    }

    fun setSort(sort: PostSort) {
        if (_uiState.value.currentSort == sort) return
        _uiState.update { it.copy(currentSort = sort, posts = emptyList(), after = null) }
        loadPosts()
    }

    fun setTimeFilter(filter: TimeFilter) {
        if (_uiState.value.currentTimeFilter == filter) return
        _uiState.update { it.copy(currentTimeFilter = filter, posts = emptyList(), after = null) }
        loadPosts()
    }

    fun setSubreddit(subreddit: String?) {
        if (_uiState.value.currentSubreddit == subreddit) return
        _uiState.update { it.copy(currentSubreddit = subreddit, posts = emptyList(), after = null) }
        loadPosts()
    }

    fun setFeedType(feedType: FeedType) {
        if (_uiState.value.currentFeedType == feedType) return
        _uiState.update { it.copy(currentFeedType = feedType, currentSubreddit = feedType.subreddit, posts = emptyList(), after = null) }
        loadPosts()
    }

    fun blockSubreddit(subreddit: String) {
        settingsRepository.addBlockedSubreddit(subreddit)
    }

    fun vote(post: Post, direction: Int) {
        viewModelScope.launch {
            val result = postRepository.vote(post, direction)
            result.onSuccess { updatedPost ->
                _uiState.update { state ->
                    state.copy(
                        posts = state.posts.map { if (it.id == post.id) updatedPost else it }
                    )
                }
            }
        }
    }

    fun save(post: Post) {
        viewModelScope.launch {
            val result = postRepository.save(post)
            result.onSuccess { updatedPost ->
                _uiState.update { state ->
                    state.copy(
                        posts = state.posts.map { if (it.id == post.id) updatedPost else it }
                    )
                }
            }
        }
    }

    fun hide(post: Post) {
        viewModelScope.launch {
            val result = postRepository.hide(post)
            result.onSuccess { updatedPost ->
                _uiState.update { state ->
                    state.copy(
                        posts = state.posts.filter { it.id != post.id }
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
