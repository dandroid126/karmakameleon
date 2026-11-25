package com.karmakameleon.shared.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karmakameleon.shared.data.api.AuthManager
import com.karmakameleon.shared.data.api.OAuthRedirect
import com.karmakameleon.shared.data.repository.ActionRepository
import com.karmakameleon.shared.data.repository.ItemChanges
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.data.repository.withChanges
import com.karmakameleon.shared.data.repository.withCommentChanges
import com.karmakameleon.shared.data.repository.withPostChanges
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.Listing
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.PostSort
import com.karmakameleon.shared.domain.model.TimeFilter
import com.karmakameleon.shared.domain.model.User
import com.karmakameleon.shared.ui.PagedList
import com.karmakameleon.shared.ui.comment.CommentViewModel
import com.karmakameleon.shared.ui.plusNew
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val account: User? = null,
    val user: User? = null,
    val posts: PagedList<Post> = PagedList(),
    val comments: PagedList<Comment> = PagedList(),
    val savedPosts: PagedList<Post> = PagedList(),
    val savedComments: PagedList<Comment> = PagedList(),
    val upvotedPosts: PagedList<Post> = PagedList(),
    val downvotedPosts: PagedList<Post> = PagedList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingContent: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val selectedTab: ProfileTab = ProfileTab.POSTS,
    val postsSort: PostSort = PostSort.NEW,
    val postsTimeFilter: TimeFilter = TimeFilter.ALL,
    val commentsSort: PostSort = PostSort.NEW,
    val commentsTimeFilter: TimeFilter = TimeFilter.ALL,
    val authUrl: String? = null,
    val isOwnProfile: Boolean = false,
    val errorMessage: String? = null,
    val clientId: String = "",
    val redirectUri: String = AuthManager.DEFAULT_REDIRECT_URI,
    val isSigningIn: Boolean = false,
    // True once a sign-in succeeds, until the screen has acted on it (see consumeSignedIn).
    val signedIn: Boolean = false,
    val savedContentType: SavedContentType = SavedContentType.POSTS,
)

enum class ProfileTab(
    val displayName: String,
) {
    POSTS("Posts"),
    COMMENTS("Comments"),
    SAVED("Saved"),
    UPVOTED("Upvoted"),
    DOWNVOTED("Downvoted"),
    ABOUT("About"),
}

/** The profile's paged lists. */
enum class ProfileList {
    POSTS,
    COMMENTS,
    SAVED_POSTS,
    SAVED_COMMENTS,
    UPVOTED,
    DOWNVOTED,
}

enum class SavedContentType(
    val displayName: String,
) {
    POSTS("Posts"),
    COMMENTS("Comments"),
}

// Shows the posts and comments with the user's votes and saves from [ActionRepository] on top. One the user unsaves
// stays in the saved lists until they're reloaded, so tapping save again brings it back.
class ProfileViewModel(
    private val userRepository: UserRepository,
    private val actionRepository: ActionRepository,
    private val authManager: AuthManager,
) : ViewModel() {
    val commentViewModel = CommentViewModel(actionRepository)

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private var pendingLoginState: String? = null

    // Follows the account, like the other screens, so another user's profile can be voted on and replied to too (#87).
    init {
        viewModelScope.launch {
            userRepository.isLoggedIn.collect { isLoggedIn -> _uiState.update { it.copy(isLoggedIn = isLoggedIn) } }
        }
        viewModelScope.launch {
            actionRepository.changes.collect { changes ->
                _uiState.update { it.withChanges(changes) }
                commentViewModel.showChanges(changes)
            }
        }
    }

    // Clearing the data keeps whether you're logged in, which is about the account, not the profile shown.
    private fun initialState() =
        ProfileUiState(
            clientId = authManager.getClientId(),
            redirectUri = authManager.getRedirectUri(),
            isLoggedIn = userRepository.isLoggedIn.value,
        )

    fun clearAllData() {
        _uiState.value = initialState()
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun consumeSignedIn() {
        _uiState.update { it.copy(signedIn = false) }
    }

    fun setClientId(clientId: String) {
        authManager.setClientId(clientId)
        _uiState.update { it.copy(clientId = authManager.getClientId()) }
    }

    fun setRedirectUri(redirectUri: String) {
        authManager.setRedirectUri(redirectUri)
        _uiState.update { it.copy(redirectUri = authManager.getRedirectUri()) }
    }

    fun loadOwnProfile(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !forceRefresh,
                    isRefreshing = forceRefresh,
                    isOwnProfile = true,
                )
            }
            val result = userRepository.loadCurrentUser()
            result.fold(
                onSuccess = { account ->
                    _uiState.update {
                        it.copy(
                            account = account,
                            isLoading = false,
                            isRefreshing = false,
                        )
                    }
                    load(userPosts, account.name)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.message,
                        )
                    }
                },
            )
        }
    }

    fun loadUserProfile(
        username: String,
        forceRefresh: Boolean = false,
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !forceRefresh,
                    isRefreshing = forceRefresh,
                    isOwnProfile = false,
                )
            }
            val result = userRepository.getUser(username)
            result.fold(
                onSuccess = { user ->
                    _uiState.update {
                        it.copy(
                            user = user,
                            isLoading = false,
                            isRefreshing = false,
                        )
                    }
                    load(userPosts, username)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.message,
                        )
                    }
                },
            )
        }
    }

    private fun getUsername(): String? =
        if (_uiState.value.isOwnProfile) {
            _uiState.value.account?.name
        } else {
            _uiState.value.user?.name
        }

    // How to read one paged list from the state, write it back, and fetch a page of it for a user.
    private class ListSource<T>(
        val get: (ProfileUiState) -> PagedList<T>,
        val set: (ProfileUiState, PagedList<T>) -> ProfileUiState,
        val id: (T) -> String,
        val fetchPage: suspend (username: String, after: String?) -> Result<Listing<T>>,
    ) {
        fun update(
            state: ProfileUiState,
            change: (PagedList<T>) -> PagedList<T>,
        ): ProfileUiState = set(state, change(get(state)))
    }

    private val userPosts =
        ListSource({ it.posts }, { state, list -> state.copy(posts = list) }, Post::id) { username, after ->
            userRepository.getUserPosts(username, _uiState.value.postsSort, _uiState.value.postsTimeFilter, after)
        }
    private val userComments =
        ListSource({ it.comments }, { state, list -> state.copy(comments = list) }, Comment::id) { username, after ->
            userRepository.getUserComments(username, _uiState.value.commentsSort, _uiState.value.commentsTimeFilter, after)
        }

    // The saved, upvoted and downvoted lists are the logged-in account's, so they don't need the username.
    private val savedPosts =
        ListSource({
            it.savedPosts
        }, { state, list -> state.copy(savedPosts = list) }, Post::id) { _, after -> userRepository.getSavedPosts(after) }
    private val savedComments =
        ListSource({ it.savedComments }, { state, list -> state.copy(savedComments = list) }, Comment::id) { _, after ->
            userRepository.getSavedComments(after)
        }
    private val upvotedPosts =
        ListSource({ it.upvotedPosts }, { state, list -> state.copy(upvotedPosts = list) }, Post::id) { _, after ->
            userRepository.getUpvotedPosts(after)
        }
    private val downvotedPosts =
        ListSource({ it.downvotedPosts }, { state, list -> state.copy(downvotedPosts = list) }, Post::id) { _, after ->
            userRepository.getDownvotedPosts(after)
        }

    // Loads the first page of a list, replacing what it held.
    private fun <T> load(
        source: ListSource<T>,
        username: String,
        forceRefresh: Boolean = false,
    ) {
        viewModelScope.launch {
            _uiState.update { state ->
                source
                    .update(
                        state,
                    ) { it.copy(items = if (forceRefresh) emptyList() else it.items, hasMore = true) }
                    .copy(isLoadingContent = true)
            }
            val result = source.fetchPage(username, null)
            _uiState.update { state ->
                val loaded =
                    result.fold(
                        onSuccess = { listing ->
                            source.update(state) {
                                it.copy(items = listing.items, after = listing.after, hasMore = listing.hasMore, itemsId = it.itemsId + 1)
                            }
                        },
                        onFailure = { state },
                    )
                loaded.copy(isLoadingContent = false).withChanges(actionRepository.changes.value)
            }
        }
    }

    fun loadMore(list: ProfileList) {
        when (list) {
            ProfileList.POSTS -> loadMore(userPosts)
            ProfileList.COMMENTS -> loadMore(userComments)
            ProfileList.SAVED_POSTS -> loadMore(savedPosts)
            ProfileList.SAVED_COMMENTS -> loadMore(savedComments)
            ProfileList.UPVOTED -> loadMore(upvotedPosts)
            ProfileList.DOWNVOTED -> loadMore(downvotedPosts)
        }
    }

    // Appends the next page of a list.
    private fun <T> loadMore(source: ListSource<T>) {
        val current = source.get(_uiState.value)
        if (!current.canLoadMore) return
        val username = getUsername() ?: return
        viewModelScope.launch {
            _uiState.update { state -> source.update(state) { it.copy(isLoadingMore = true) } }
            val result = source.fetchPage(username, current.after)
            _uiState.update { state ->
                source
                    .update(state) { list ->
                        result.fold(
                            onSuccess = { listing ->
                                list.copy(
                                    items = list.items.plusNew(listing.items, source.id),
                                    after = listing.after,
                                    hasMore = listing.hasMore,
                                    isLoadingMore = false,
                                )
                            },
                            onFailure = { list.copy(isLoadingMore = false) },
                        )
                    }.withChanges(actionRepository.changes.value)
            }
        }
    }

    fun setSelectedTab(tab: ProfileTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        val username = getUsername() ?: return
        when (tab) {
            ProfileTab.ABOUT -> { /* No loading needed */ }

            else -> {
                sourcesFor(tab).filter { it.get(_uiState.value).items.isEmpty() }.forEach { load(it, username) }
            }
        }
    }

    // The lists a tab shows.
    private fun sourcesFor(tab: ProfileTab): List<ListSource<*>> =
        when (tab) {
            ProfileTab.POSTS -> listOf(userPosts)
            ProfileTab.COMMENTS -> listOf(userComments)
            ProfileTab.SAVED -> listOf(savedPosts, savedComments)
            ProfileTab.UPVOTED -> listOf(upvotedPosts)
            ProfileTab.DOWNVOTED -> listOf(downvotedPosts)
            ProfileTab.ABOUT -> emptyList()
        }

    fun setPostsSort(sort: PostSort) {
        _uiState.update { it.copy(postsSort = sort) }
        val username = getUsername() ?: return
        load(userPosts, username)
    }

    fun setPostsTimeFilter(timeFilter: TimeFilter) {
        _uiState.update { it.copy(postsTimeFilter = timeFilter) }
        val username = getUsername() ?: return
        load(userPosts, username)
    }

    fun setCommentsSort(sort: PostSort) {
        _uiState.update { it.copy(commentsSort = sort) }
        val username = getUsername() ?: return
        load(userComments, username)
    }

    fun setCommentsTimeFilter(timeFilter: TimeFilter) {
        _uiState.update { it.copy(commentsTimeFilter = timeFilter) }
        val username = getUsername() ?: return
        load(userComments, username)
    }

    fun setSavedContentType(type: SavedContentType) {
        _uiState.update { it.copy(savedContentType = type) }
    }

    fun refresh() {
        val username = getUsername() ?: return
        when (val tab = _uiState.value.selectedTab) {
            ProfileTab.ABOUT -> {
                if (_uiState.value.isOwnProfile) loadOwnProfile(forceRefresh = true) else loadUserProfile(username, forceRefresh = true)
            }

            else -> {
                sourcesFor(tab).forEach { load(it, username, forceRefresh = true) }
            }
        }
    }

    fun initiateLogin() {
        val state = generateState()
        pendingLoginState = state
        val authUrl = userRepository.getAuthorizationUrl(state)
        _uiState.update { it.copy(authUrl = authUrl) }
    }

    fun cancelLogin() {
        pendingLoginState = null
        _uiState.update { it.copy(authUrl = null) }
    }

    /**
     * Called for each page the login WebView is about to load. Returns true if [url] is Reddit's
     * redirect back to the app, in which case the WebView must not load it.
     */
    fun handleLoginRedirect(url: String): Boolean {
        val redirect = OAuthRedirect.parse(url, authManager.getRedirectUri()) ?: return false
        // The WebView can report the same redirect more than once; only the first one counts.
        val expectedState = pendingLoginState ?: return true
        pendingLoginState = null
        _uiState.update { it.copy(authUrl = null) }

        val code = redirect.code
        when {
            redirect.state != expectedState -> {
                showLoginError("Sign in failed. Please try again.")
            }

            redirect.error == "access_denied" -> {
                // The user declined on Reddit's page.
            }

            redirect.error != null -> {
                showLoginError("Sign in failed: ${redirect.error}")
            }

            code == null -> {
                showLoginError("Sign in failed. Please try again.")
            }

            else -> {
                _uiState.update { it.copy(isSigningIn = true) }
                viewModelScope.launch {
                    val success = userRepository.handleAuthCallback(code)
                    _uiState.update { it.copy(isSigningIn = false, signedIn = success) }
                    if (!success) showLoginError("Sign in failed. Check your client ID and redirect URI.")
                }
            }
        }
        return true
    }

    private fun showLoginError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun logout() {
        userRepository.logout()
        clearAllData()
    }

    private fun ProfileUiState.withChanges(changes: Map<String, ItemChanges>): ProfileUiState {
        fun PagedList<Post>.changed() = copy(items = items.withPostChanges(changes))

        fun PagedList<Comment>.changed() = copy(items = items.withCommentChanges(changes))
        return copy(
            posts = posts.changed(),
            comments = comments.changed(),
            savedPosts = savedPosts.changed(),
            savedComments = savedComments.changed(),
            upvotedPosts = upvotedPosts.changed(),
            downvotedPosts = downvotedPosts.changed(),
        )
    }

    fun submitReply() = commentViewModel.submitReply()

    fun submitEdit() = commentViewModel.submitEdit(::findCommentById)

    fun deleteComment(commentId: String) = commentViewModel.deleteComment(commentId, ::findCommentById)

    fun findCommentById(commentId: String): Comment? =
        _uiState.value.comments.items
            .find { it.id == commentId }
            ?: _uiState.value.savedComments.items
                .find { it.id == commentId }

    private fun generateState(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..OAUTH_STATE_LENGTH).map { chars.random() }.joinToString("")
    }

    companion object {
        private const val OAUTH_STATE_LENGTH = 16
    }
}
