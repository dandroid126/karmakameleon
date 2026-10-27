package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.AuthManager
import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.Listing
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.PostSort
import com.karmakameleon.shared.domain.model.TimeFilter
import com.karmakameleon.shared.domain.model.User
import com.karmakameleon.shared.util.runSuspendCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserRepository(
    private val redditApi: RedditApi,
    private val authManager: AuthManager,
    private val markdownRepository: MarkdownRepository,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    private val _currentAccount = MutableStateFlow<User?>(null)
    val currentAccount: StateFlow<User?> = _currentAccount.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        _isLoggedIn.value = authManager.isLoggedIn()
        // Undispatched, so the current login state is applied before init returns. Otherwise that first update could
        // land after loadCurrentUser() and overwrite what it set.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            authManager.isLoggedInFlow.collect { isLoggedIn ->
                _isLoggedIn.value = isLoggedIn
            }
        }
    }

    suspend fun loadCurrentUser(): Result<User> =
        runSuspendCatching {
            val account = redditApi.getMe() ?: error("Failed to load user")
            _currentAccount.value = account
            _isLoggedIn.value = true
            account
        }

    suspend fun getUser(username: String): Result<User> = runSuspendCatching { redditApi.getUser(username) ?: error("User not found") }

    suspend fun getUserPosts(
        username: String,
        sort: PostSort = PostSort.NEW,
        timeFilter: TimeFilter? = null,
        after: String? = null,
    ): Result<Listing<Post>> = runSuspendCatching { redditApi.getUserPosts(username, sort, timeFilter, after) }

    suspend fun getUserComments(
        username: String,
        sort: PostSort = PostSort.NEW,
        timeFilter: TimeFilter? = null,
        after: String? = null,
    ): Result<Listing<Comment>> = runSuspendCatching { redditApi.getUserComments(username, sort, timeFilter, after).withParsedBodies() }

    suspend fun getSavedPosts(after: String? = null): Result<Listing<Post>> {
        val username = _currentAccount.value?.name ?: return Result.failure(Exception("Not logged in"))
        return runSuspendCatching { redditApi.getSavedPosts(username, after) }
    }

    suspend fun getSavedComments(after: String? = null): Result<Listing<Comment>> {
        val username = _currentAccount.value?.name ?: return Result.failure(Exception("Not logged in"))
        return runSuspendCatching { redditApi.getSavedComments(username, after).withParsedBodies() }
    }

    // Parses the comments' text before they're shown, so the screen shows it on its first frame without parsing it
    // while drawing.
    private suspend fun Listing<Comment>.withParsedBodies(): Listing<Comment> =
        also { markdownRepository.parseAll(items.map(Comment::body)) }

    suspend fun getUpvotedPosts(after: String? = null): Result<Listing<Post>> {
        val username = _currentAccount.value?.name ?: return Result.failure(Exception("Not logged in"))
        return runSuspendCatching { redditApi.getUpvotedPosts(username, after) }
    }

    suspend fun getDownvotedPosts(after: String? = null): Result<Listing<Post>> {
        val username = _currentAccount.value?.name ?: return Result.failure(Exception("Not logged in"))
        return runSuspendCatching { redditApi.getDownvotedPosts(username, after) }
    }

    fun getAuthorizationUrl(state: String): String = authManager.getAuthorizationUrl(state)

    suspend fun handleAuthCallback(code: String): Boolean {
        val success = authManager.exchangeCodeForToken(code)
        if (success) {
            _isLoggedIn.value = true
            loadCurrentUser()
        }
        return success
    }

    fun logout() {
        authManager.clearToken()
        _currentAccount.value = null
        _isLoggedIn.value = false
    }

    fun isLoggedInSync(): Boolean = authManager.isLoggedIn()
}
