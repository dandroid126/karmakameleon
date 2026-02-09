package com.reader.android.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.shared.data.repository.PostRepository
import com.reader.shared.data.repository.SubredditRepository
import com.reader.shared.data.repository.UserRepository
import com.reader.shared.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FeedUiState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val currentSort: PostSort = PostSort.HOT,
    val currentTimeFilter: TimeFilter = TimeFilter.DAY,
    val currentSubreddit: String? = null,
    val after: String? = null,
    val hasMore: Boolean = true,
    val isLoggedIn: Boolean = false
)

class FeedViewModel(
    private val postRepository: PostRepository,
    private val subredditRepository: SubredditRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.isLoggedIn.collect { isLoggedIn ->
                _uiState.update { it.copy(isLoggedIn = isLoggedIn) }
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
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMore = false) }
                }
            )
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
