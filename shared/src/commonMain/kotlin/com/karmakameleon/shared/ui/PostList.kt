package com.karmakameleon.shared.ui

import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.domain.model.Listing
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.PostSort
import com.karmakameleon.shared.domain.model.TimeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A list of posts in the sort order the user picked, loaded a page at a time. [after] is Reddit's cursor for the next page. */
data class PostListState(
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val currentSort: PostSort = PostSort.HOT,
    val currentTimeFilter: TimeFilter = TimeFilter.DAY,
    val after: String? = null,
    val hasMore: Boolean = true,
) {
    val canLoadMore: Boolean get() = !isLoadingMore && hasMore && after != null
}

/**
 * The posts of the home feed or a subreddit, shared by their view models. Loads them a page at a time from [subreddit]
 * (null for the home feed), handles votes, saves and hides, and keeps the posts in step with changes made on other
 * screens. NSFW posts are left out while NSFW is off, and posts from blocked subreddits when [hideBlockedSubreddits]
 * is set. Coroutines run in [scope], the view model's scope.
 */
class PostList internal constructor(
    private val scope: CoroutineScope,
    private val postRepository: PostRepository,
    private val settingsRepository: SettingsRepository,
    private val hideBlockedSubreddits: Boolean,
    private val subreddit: () -> String?,
) {
    private val _state = MutableStateFlow(PostListState())
    val state: StateFlow<PostListState> = _state.asStateFlow()

    init {
        postRepository.postUpdates
            .onEach { updatedPost -> updatePosts { it.withUpdatedPost(updatedPost) } }
            .launchIn(scope)
    }

    fun load(forceRefresh: Boolean = false) {
        if (_state.value.isLoading && !forceRefresh) return

        scope.launch {
            _state.update { it.copy(isLoading = !forceRefresh, isRefreshing = forceRefresh, error = null) }
            fetchPage(_state.value, after = null).fold(
                onSuccess = { listing ->
                    _state.update {
                        it.copy(
                            posts = listing.items,
                            after = listing.after,
                            hasMore = listing.hasMore,
                            isLoading = false,
                            isRefreshing = false,
                        )
                    }
                    enrich(listing.items)
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, error = error.message ?: "Failed to load posts")
                    }
                },
            )
        }
    }

    fun loadMore() {
        val current = _state.value
        if (!current.canLoadMore) return

        scope.launch {
            _state.update { it.copy(isLoadingMore = true) }
            fetchPage(current, after = current.after).fold(
                onSuccess = { listing ->
                    _state.update {
                        it.copy(
                            posts = it.posts.plusNew(listing.items, Post::id),
                            after = listing.after,
                            hasMore = listing.hasMore,
                            isLoadingMore = false,
                        )
                    }
                    enrich(listing.items)
                },
                onFailure = { _ -> _state.update { it.copy(isLoadingMore = false) } },
            )
        }
    }

    /** Clears the list and loads it again, for when the subreddit it shows has changed. */
    fun reload() {
        _state.update { it.copy(posts = emptyList(), after = null) }
        load()
    }

    fun setSort(sort: PostSort) {
        if (_state.value.currentSort == sort) return
        _state.update { it.copy(currentSort = sort) }
        reload()
    }

    fun setTimeFilter(filter: TimeFilter) {
        if (_state.value.currentTimeFilter == filter) return
        _state.update { it.copy(currentTimeFilter = filter) }
        reload()
    }

    fun vote(
        post: Post,
        direction: Int,
    ) {
        scope.launch {
            postRepository.vote(post, direction).onSuccess { updatedPost -> updatePosts { it.withUpdatedPost(updatedPost) } }
        }
    }

    fun save(post: Post) {
        scope.launch {
            postRepository.save(post).onSuccess { updatedPost -> updatePosts { it.withUpdatedPost(updatedPost) } }
        }
    }

    fun hide(post: Post) {
        scope.launch {
            postRepository.hide(post).onSuccess { _ -> updatePosts { posts -> posts.filter { it.id != post.id } } }
        }
    }

    /** Removes the posts that shouldn't be shown any more, after the NSFW setting or the blocked subreddits changed. */
    fun removeHiddenPosts(
        nsfwEnabled: Boolean,
        blockedSubreddits: Set<String>,
    ) {
        updatePosts { posts -> posts.filter { it.isVisible(nsfwEnabled, blockedSubreddits) } }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    // The time filter only applies to the top and controversial sorts.
    private suspend fun fetchPage(
        state: PostListState,
        after: String?,
    ): Result<Listing<Post>> =
        postRepository
            .getPosts(
                subreddit = subreddit(),
                sort = state.currentSort,
                time = state.currentTimeFilter.takeIf { state.currentSort == PostSort.TOP || state.currentSort == PostSort.CONTROVERSIAL },
                after = after,
            ).map { listing ->
                val nsfwEnabled = settingsRepository.nsfwEnabled.value
                val blockedSubreddits = if (hideBlockedSubreddits) settingsRepository.blockedSubreddits.value else emptySet()
                listing.copy(items = listing.items.filter { it.isVisible(nsfwEnabled, blockedSubreddits) })
            }

    // Fills in the posts that Reddit sent without all their details.
    private suspend fun enrich(posts: List<Post>) {
        val enriched = postRepository.enrichSparsePosts(posts).associateBy { it.id }
        if (enriched.isNotEmpty()) {
            updatePosts { current -> current.map { enriched[it.id] ?: it } }
        }
    }

    private fun updatePosts(transform: (List<Post>) -> List<Post>) {
        _state.update { it.copy(posts = transform(it.posts)) }
    }
}

// Blocked subreddit names are stored in lowercase.
private fun Post.isVisible(
    nsfwEnabled: Boolean,
    blockedSubreddits: Set<String>,
): Boolean = (nsfwEnabled || !isNsfw) && subreddit.lowercase() !in blockedSubreddits

// Puts [updated] in place of the post with its id, or removes that post if it's now hidden.
private fun List<Post>.withUpdatedPost(updated: Post): List<Post> =
    if (updated.isHidden) {
        filter { it.id != updated.id }
    } else {
        map { if (it.id == updated.id) updated else it }
    }
