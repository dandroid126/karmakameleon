package com.karmakameleon.shared.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.SubredditRepository
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.SearchSort
import com.karmakameleon.shared.domain.model.SearchType
import com.karmakameleon.shared.domain.model.Subreddit
import com.karmakameleon.shared.domain.model.TimeFilter
import com.karmakameleon.shared.util.RedditLink
import com.karmakameleon.shared.util.parseRedditLink
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val posts: List<Post> = emptyList(),
    val subreddits: List<Subreddit> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchType: SearchType = SearchType.POST,
    val searchSort: SearchSort = SearchSort.RELEVANCE,
    val timeFilter: TimeFilter = TimeFilter.ALL,
    val after: String? = null,
    val hasMore: Boolean = true,
    val detectedLink: RedditLink? = null
)

class SearchViewModel(
    private val postRepository: PostRepository,
    private val subredditRepository: SubredditRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun setQuery(query: String) {
        val trimmed = query.trim()
        val link = if (trimmed.contains("reddit.com/") || trimmed.startsWith("/r/")) {
            val parsed = parseRedditLink(trimmed)
            if (parsed is RedditLink.External) null else parsed
        } else null

        _uiState.update { it.copy(query = query, detectedLink = link) }

        if (link != null) {
            searchJob?.cancel()
            _uiState.update { it.copy(posts = emptyList(), subreddits = emptyList()) }
            return
        }

        searchJob?.cancel()
        if (query.length >= 2) {
            searchJob = viewModelScope.launch {
                delay(300) // debounce
                search()
            }
        } else {
            _uiState.update { it.copy(posts = emptyList(), subreddits = emptyList()) }
        }
    }

    fun search() {
        val query = _uiState.value.query
        if (query.length < 2) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            when (_uiState.value.searchType) {
                SearchType.POST -> searchPosts(query)
                SearchType.SUBREDDIT -> searchSubreddits(query)
                SearchType.USER -> {} // Not implemented
            }
        }
    }

    private suspend fun searchPosts(query: String) {
        val nsfwEnabled = settingsRepository.nsfwEnabled.value
        val result = postRepository.search(
            query = query,
            sort = _uiState.value.searchSort,
            time = _uiState.value.timeFilter
        )

        result.fold(
            onSuccess = { listing ->
                val posts = if (nsfwEnabled) listing.items else listing.items.filter { !it.isNsfw }
                _uiState.update {
                    it.copy(
                        posts = posts,
                        after = listing.after,
                        hasMore = listing.hasMore,
                        isLoading = false
                    )
                }
            },
            onFailure = { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        )
    }

    private suspend fun searchSubreddits(query: String) {
        val nsfwEnabled = settingsRepository.nsfwEnabled.value
        val nsfwSearchEnabled = settingsRepository.nsfwSearchEnabled.value
        val includeOver18 = nsfwEnabled && nsfwSearchEnabled
        val result = subredditRepository.searchSubreddits(query, includeOver18 = includeOver18)

        result.fold(
            onSuccess = { subreddits ->
                _uiState.update {
                    it.copy(subreddits = subreddits, isLoading = false)
                }
            },
            onFailure = { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message) }
            }
        )
    }

    fun setSearchType(type: SearchType) {
        if (_uiState.value.searchType == type) return
        _uiState.update { 
            it.copy(
                searchType = type, 
                posts = emptyList(), 
                subreddits = emptyList(),
                after = null
            ) 
        }
        if (_uiState.value.query.length >= 2) {
            search()
        }
    }

    fun setSearchSort(sort: SearchSort) {
        if (_uiState.value.searchSort == sort) return
        _uiState.update { it.copy(searchSort = sort, posts = emptyList(), after = null) }
        search()
    }

    fun setTimeFilter(time: TimeFilter) {
        if (_uiState.value.timeFilter == time) return
        _uiState.update { it.copy(timeFilter = time, posts = emptyList(), after = null) }
        search()
    }

    fun loadMore() {
        val currentState = _uiState.value
        if (currentState.isLoading || !currentState.hasMore || currentState.after == null) return

        viewModelScope.launch {
            val nsfwEnabled = settingsRepository.nsfwEnabled.value
            _uiState.update { it.copy(isLoading = true) }

            val result = postRepository.search(
                query = currentState.query,
                sort = currentState.searchSort,
                time = currentState.timeFilter,
                after = currentState.after
            )

            result.fold(
                onSuccess = { listing ->
                    val posts = if (nsfwEnabled) listing.items else listing.items.filter { !it.isNsfw }
                    _uiState.update {
                        it.copy(
                            posts = it.posts + posts,
                            after = listing.after,
                            hasMore = listing.hasMore,
                            isLoading = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoading = false) }
                }
            )
        }
    }
}
