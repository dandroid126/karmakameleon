package com.reader.shared.ui.subreddit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.shared.data.repository.PostRepository
import com.reader.shared.data.repository.SubredditRepository
import com.reader.shared.data.repository.UserRepository
import com.reader.shared.domain.model.Post
import com.reader.shared.domain.model.PostSort
import com.reader.shared.domain.model.Subreddit
import com.reader.shared.domain.model.TimeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubredditUiState(
    val subreddit: Subreddit? = null,
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val currentSort: PostSort = PostSort.HOT,
    val currentTimeFilter: TimeFilter = TimeFilter.DAY,
    val after: String? = null,
    val hasMore: Boolean = true,
    val isLoggedIn: Boolean = false
)

class SubredditViewModel(
    private val subredditName: String,
    private val subredditRepository: SubredditRepository,
    private val postRepository: PostRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubredditUiState())
    val uiState: StateFlow<SubredditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.isLoggedIn.collect { isLoggedIn ->
                _uiState.update { it.copy(isLoggedIn = isLoggedIn) }
            }
        }
        loadSubreddit()
        loadPosts()
    }

    private fun loadSubreddit() {
        viewModelScope.launch {
            val result = subredditRepository.getSubreddit(subredditName)
            result.onSuccess { subreddit ->
                _uiState.update { it.copy(subreddit = subreddit) }
            }
        }
    }

    fun loadPosts(forceRefresh: Boolean = false) {
        if (_uiState.value.isLoading && !forceRefresh) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = postRepository.getPosts(
                subreddit = subredditName,
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
                        it.copy(isLoading = false, error = error.message)
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
                subreddit = subredditName,
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

    fun toggleSubscribe() {
        val subreddit = _uiState.value.subreddit ?: return
        viewModelScope.launch {
            val result = subredditRepository.subscribe(subreddit)
            result.onSuccess { updated ->
                _uiState.update { it.copy(subreddit = updated) }
            }
        }
    }

    fun vote(post: Post, direction: Int) {
        viewModelScope.launch {
            val result = postRepository.vote(post, direction)
            result.onSuccess { updatedPost ->
                _uiState.update { state ->
                    state.copy(posts = state.posts.map { if (it.id == post.id) updatedPost else it })
                }
            }
        }
    }

    fun save(post: Post) {
        viewModelScope.launch {
            val result = postRepository.save(post)
            result.onSuccess { updatedPost ->
                _uiState.update { state ->
                    state.copy(posts = state.posts.map { if (it.id == post.id) updatedPost else it })
                }
            }
        }
    }

    fun hide(post: Post) {
        viewModelScope.launch {
            val result = postRepository.hide(post)
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(posts = state.posts.filter { it.id != post.id })
                }
            }
        }
    }
}
