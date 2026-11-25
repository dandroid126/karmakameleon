package com.karmakameleon.shared.ui

import com.karmakameleon.shared.data.repository.ActionRepository
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.withPostChanges
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
    /**
     * Counts the first pages that replaced [posts], so a screen can tell new posts, which start at the top, from more of
     * the same ones, which don't.
     */
    val itemsId: Int = 0,
) {
    val canLoadMore: Boolean get() = !isLoadingMore && hasMore && after != null
}

/**
 * The posts of the home feed or a subreddit, shared by their view models. Loads them a page at a time from [subreddit]
 * (null for the home feed), and shows them with the user's votes, saves and hides from [ActionRepository] on top, made
 * here or on other screens. Hidden posts are left out of the list, as are NSFW posts while NSFW is off, and posts from
 * blocked subreddits when [hideBlockedSubreddits] is set. Coroutines run in [scope], the view model's scope.
 */
class PostList internal constructor(
    private val scope: CoroutineScope,
    private val postRepository: PostRepository,
    private val actionRepository: ActionRepository,
    private val settingsRepository: SettingsRepository,
    private val hideBlockedSubreddits: Boolean,
    private val subreddit: () -> String?,
) {
    private val _state = MutableStateFlow(PostListState())
    val state: StateFlow<PostListState> = _state.asStateFlow()

    // The posts as loaded, hidden ones included, so a hide that Reddit refuses can bring its post back.
    private var loadedPosts: List<Post> = emptyList()

    init {
        actionRepository.changes
            .onEach { _state.update { state -> state.copy(posts = shownPosts()) } }
            .launchIn(scope)
    }

    fun load(forceRefresh: Boolean = false) {
        if (_state.value.isLoading && !forceRefresh) return

        scope.launch {
            _state.update { it.copy(isLoading = !forceRefresh, isRefreshing = forceRefresh, error = null) }
            fetchPage(_state.value, after = null).fold(
                onSuccess = { listing ->
                    loadedPosts = listing.items
                    _state.update {
                        it.copy(
                            posts = shownPosts(),
                            after = listing.after,
                            hasMore = listing.hasMore,
                            isLoading = false,
                            isRefreshing = false,
                            itemsId = it.itemsId + 1,
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
                    loadedPosts = loadedPosts.plusNew(listing.items, Post::id)
                    _state.update {
                        it.copy(
                            posts = shownPosts(),
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
        loadedPosts = emptyList()
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

    // Changes the loaded posts and shows them.
    private fun updatePosts(transform: (List<Post>) -> List<Post>) {
        loadedPosts = transform(loadedPosts)
        _state.update { it.copy(posts = shownPosts()) }
    }

    // A hidden post leaves the feed, since the user asked not to see it: that's what hiding is for.
    private fun shownPosts(): List<Post> = loadedPosts.withPostChanges(actionRepository.changes.value).filterNot { it.isHidden }
}

// Blocked subreddit names are stored in lowercase.
private fun Post.isVisible(
    nsfwEnabled: Boolean,
    blockedSubreddits: Set<String>,
): Boolean = (nsfwEnabled || !isNsfw) && subreddit.lowercase() !in blockedSubreddits
