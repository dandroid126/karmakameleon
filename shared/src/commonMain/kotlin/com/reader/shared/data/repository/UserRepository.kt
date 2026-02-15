package com.reader.shared.data.repository

import com.reader.shared.data.api.AuthManager
import com.reader.shared.data.api.RedditApi
import com.reader.shared.domain.model.Account
import com.reader.shared.domain.model.Comment
import com.reader.shared.domain.model.Listing
import com.reader.shared.domain.model.Post
import com.reader.shared.domain.model.PostSort
import com.reader.shared.domain.model.TimeFilter
import com.reader.shared.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserRepository(
    private val redditApi: RedditApi,
    private val authManager: AuthManager,
) {
    private val _currentAccount = MutableStateFlow<Account?>(null)
    val currentAccount: StateFlow<Account?> = _currentAccount.asStateFlow()
    
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        _isLoggedIn.value = authManager.isLoggedIn()
    }

    suspend fun loadCurrentUser(): Result<Account> {
        return try {
            val account = redditApi.getMe()
            if (account != null) {
                _currentAccount.value = account
                _isLoggedIn.value = true
                Result.success(account)
            } else {
                Result.failure(Exception("Failed to load user"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUser(username: String): Result<User> {
        return try {
            val user = redditApi.getUser(username)
            if (user != null) {
                Result.success(user)
            } else {
                Result.failure(Exception("User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserPosts(
        username: String,
        sort: PostSort = PostSort.NEW,
        timeFilter: TimeFilter? = null,
        after: String? = null
    ): Result<Listing<Post>> {
        return try {
            val listing = redditApi.getUserPosts(username, sort, timeFilter, after)
            Result.success(listing)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserComments(
        username: String,
        sort: PostSort = PostSort.NEW,
        timeFilter: TimeFilter? = null,
        after: String? = null
    ): Result<Listing<Comment>> {
        return try {
            val listing = redditApi.getUserComments(username, sort, timeFilter, after)
            Result.success(listing)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSavedPosts(after: String? = null): Result<Listing<Post>> {
        val username = _currentAccount.value?.name ?: return Result.failure(Exception("Not logged in"))
        return try {
            val listing = redditApi.getSavedPosts(username, after)
            Result.success(listing)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSavedComments(after: String? = null): Result<Listing<Comment>> {
        val username = _currentAccount.value?.name ?: return Result.failure(Exception("Not logged in"))
        return try {
            val listing = redditApi.getSavedComments(username, after)
            Result.success(listing)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUpvotedPosts(after: String? = null): Result<Listing<Post>> {
        val username = _currentAccount.value?.name ?: return Result.failure(Exception("Not logged in"))
        return try {
            val listing = redditApi.getUpvotedPosts(username, after)
            Result.success(listing)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDownvotedPosts(after: String? = null): Result<Listing<Post>> {
        val username = _currentAccount.value?.name ?: return Result.failure(Exception("Not logged in"))
        return try {
            val listing = redditApi.getDownvotedPosts(username, after)
            Result.success(listing)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAuthorizationUrl(state: String): String {
        return authManager.getAuthorizationUrl(state)
    }

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
