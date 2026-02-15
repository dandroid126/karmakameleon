package com.reader.shared.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.shared.data.api.AuthManager
import com.reader.shared.data.repository.CommentRepository
import com.reader.shared.data.repository.PostRepository
import com.reader.shared.data.repository.UserRepository
import com.reader.shared.domain.model.Account
import com.reader.shared.domain.model.Comment
import com.reader.shared.domain.model.Post
import com.reader.shared.domain.model.PostSort
import com.reader.shared.domain.model.TimeFilter
import com.reader.shared.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val account: Account? = null,
    val user: User? = null,
    val posts: List<Post> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val savedPosts: List<Post> = emptyList(),
    val savedComments: List<Comment> = emptyList(),
    val upvotedPosts: List<Post> = emptyList(),
    val downvotedPosts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingContent: Boolean = false,
    val error: String? = null,
    val isLoggedIn: Boolean = false,
    val selectedTab: ProfileTab = ProfileTab.POSTS,
    val postsSort: PostSort = PostSort.NEW,
    val postsTimeFilter: TimeFilter = TimeFilter.ALL,
    val commentsSort: PostSort = PostSort.NEW,
    val commentsTimeFilter: TimeFilter = TimeFilter.ALL,
    val postsAfter: String? = null,
    val commentsAfter: String? = null,
    val savedPostsAfter: String? = null,
    val savedCommentsAfter: String? = null,
    val upvotedAfter: String? = null,
    val downvotedAfter: String? = null,
    val authUrl: String? = null,
    val isOwnProfile: Boolean = true,
    val clientId: String = "",
    val savedContentType: SavedContentType = SavedContentType.POSTS
)

enum class ProfileTab(val displayName: String) {
    POSTS("Posts"),
    COMMENTS("Comments"),
    SAVED("Saved"),
    UPVOTED("Upvoted"),
    DOWNVOTED("Downvoted"),
    ABOUT("About")
}

enum class SavedContentType(val displayName: String) {
    POSTS("Posts"),
    COMMENTS("Comments")
}

class ProfileViewModel(
    private val userRepository: UserRepository,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState(clientId = authManager.getClientId()))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userRepository.isLoggedIn.collect { isLoggedIn ->
                _uiState.update { it.copy(isLoggedIn = isLoggedIn) }
                if (isLoggedIn) {
                    loadCurrentUser()
                }
            }
        }
        
        viewModelScope.launch {
            userRepository.currentAccount.collect { account ->
                _uiState.update { it.copy(account = account) }
                if (account != null) {
                    loadUserPosts(account.name)
                }
            }
        }
    }

    fun setClientId(clientId: String) {
        authManager.setClientId(clientId)
        _uiState.update { it.copy(clientId = clientId) }
    }

    fun loadCurrentUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = userRepository.loadCurrentUser()
            result.fold(
                onSuccess = { account ->
                    _uiState.update { it.copy(account = account, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }

    fun loadUser(username: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isOwnProfile = false) }
            val result = userRepository.getUser(username)
            result.fold(
                onSuccess = { user ->
                    _uiState.update { it.copy(user = user, isLoading = false) }
                    loadUserPosts(username)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message) }
                }
            )
        }
    }

    private fun getUsername(): String? {
        return if (_uiState.value.isOwnProfile) {
            _uiState.value.account?.name
        } else {
            _uiState.value.user?.name
        }
    }

    private fun loadUserPosts(
        username: String,
        sort: PostSort = _uiState.value.postsSort,
        timeFilter: TimeFilter = _uiState.value.postsTimeFilter,
        forceRefresh: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContent = true, posts = if (forceRefresh) emptyList() else it.posts) }
            val result = userRepository.getUserPosts(username, sort, timeFilter)
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            posts = listing.items,
                            postsAfter = listing.after,
                            isLoadingContent = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingContent = false) }
                }
            )
        }
    }

    private fun loadUserComments(
        username: String,
        sort: PostSort = _uiState.value.commentsSort,
        timeFilter: TimeFilter = _uiState.value.commentsTimeFilter,
        forceRefresh: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContent = true, comments = if (forceRefresh) emptyList() else it.comments) }
            val result = userRepository.getUserComments(username, sort, timeFilter)
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            comments = listing.items,
                            commentsAfter = listing.after,
                            isLoadingContent = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingContent = false) }
                }
            )
        }
    }

    private fun loadSavedPosts(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContent = true, savedPosts = if (forceRefresh) emptyList() else it.savedPosts) }
            val result = userRepository.getSavedPosts()
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            savedPosts = listing.items,
                            savedPostsAfter = listing.after,
                            isLoadingContent = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingContent = false) }
                }
            )
        }
    }

    private fun loadSavedComments(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContent = true, savedComments = if (forceRefresh) emptyList() else it.savedComments) }
            val result = userRepository.getSavedComments()
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            savedComments = listing.items,
                            savedCommentsAfter = listing.after,
                            isLoadingContent = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingContent = false) }
                }
            )
        }
    }

    private fun loadUpvotedPosts(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContent = true, upvotedPosts = if (forceRefresh) emptyList() else it.upvotedPosts) }
            val result = userRepository.getUpvotedPosts()
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            upvotedPosts = listing.items,
                            upvotedAfter = listing.after,
                            isLoadingContent = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingContent = false) }
                }
            )
        }
    }

    private fun loadDownvotedPosts(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingContent = true, downvotedPosts = if (forceRefresh) emptyList() else it.downvotedPosts) }
            val result = userRepository.getDownvotedPosts()
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            downvotedPosts = listing.items,
                            downvotedAfter = listing.after,
                            isLoadingContent = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingContent = false) }
                }
            )
        }
    }

    fun setSelectedTab(tab: ProfileTab) {
        _uiState.update { it.copy(selectedTab = tab) }
        val username = getUsername() ?: return
        when (tab) {
            ProfileTab.POSTS -> if (_uiState.value.posts.isEmpty()) loadUserPosts(username)
            ProfileTab.COMMENTS -> if (_uiState.value.comments.isEmpty()) loadUserComments(username)
            ProfileTab.SAVED -> {
                if (_uiState.value.savedPosts.isEmpty()) loadSavedPosts()
                if (_uiState.value.savedComments.isEmpty()) loadSavedComments()
            }
            ProfileTab.UPVOTED -> if (_uiState.value.upvotedPosts.isEmpty()) loadUpvotedPosts()
            ProfileTab.DOWNVOTED -> if (_uiState.value.downvotedPosts.isEmpty()) loadDownvotedPosts()
            ProfileTab.ABOUT -> { /* No loading needed */ }
        }
    }

    fun setPostsSort(sort: PostSort) {
        _uiState.update { it.copy(postsSort = sort) }
        val username = getUsername() ?: return
        loadUserPosts(username, sort, _uiState.value.postsTimeFilter)
    }

    fun setPostsTimeFilter(timeFilter: TimeFilter) {
        _uiState.update { it.copy(postsTimeFilter = timeFilter) }
        val username = getUsername() ?: return
        loadUserPosts(username, _uiState.value.postsSort, timeFilter)
    }

    fun setCommentsSort(sort: PostSort) {
        _uiState.update { it.copy(commentsSort = sort) }
        val username = getUsername() ?: return
        loadUserComments(username, sort, _uiState.value.commentsTimeFilter)
    }

    fun setCommentsTimeFilter(timeFilter: TimeFilter) {
        _uiState.update { it.copy(commentsTimeFilter = timeFilter) }
        val username = getUsername() ?: return
        loadUserComments(username, _uiState.value.commentsSort, timeFilter)
    }

    fun setSavedContentType(type: SavedContentType) {
        _uiState.update { it.copy(savedContentType = type) }
    }

    fun refresh() {
        val username = getUsername() ?: return
        when (_uiState.value.selectedTab) {
            ProfileTab.POSTS -> loadUserPosts(username, forceRefresh = true)
            ProfileTab.COMMENTS -> loadUserComments(username, forceRefresh = true)
            ProfileTab.SAVED -> {
                loadSavedPosts(forceRefresh = true)
                loadSavedComments(forceRefresh = true)
            }
            ProfileTab.UPVOTED -> loadUpvotedPosts(forceRefresh = true)
            ProfileTab.DOWNVOTED -> loadDownvotedPosts(forceRefresh = true)
            ProfileTab.ABOUT -> if (_uiState.value.isOwnProfile) loadCurrentUser() else loadUser(username)
        }
    }

    fun initiateLogin() {
        val state = generateState()
        val authUrl = userRepository.getAuthorizationUrl(state)
        _uiState.update { it.copy(authUrl = authUrl) }
    }

    fun clearAuthUrl() {
        _uiState.update { it.copy(authUrl = null) }
    }

    fun logout() {
        userRepository.logout()
        _uiState.update {
            ProfileUiState(isLoggedIn = false)
        }
    }

    fun vote(post: Post, direction: Int) {
        viewModelScope.launch {
            val result = postRepository.vote(post, direction)
            result.onSuccess { updatedPost ->
                _uiState.update { state ->
                    state.copy(
                        posts = state.posts.map { if (it.id == post.id) updatedPost else it },
                        savedPosts = state.savedPosts.map { if (it.id == post.id) updatedPost else it },
                        upvotedPosts = state.upvotedPosts.map { if (it.id == post.id) updatedPost else it },
                        downvotedPosts = state.downvotedPosts.map { if (it.id == post.id) updatedPost else it }
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
                        posts = state.posts.map { if (it.id == post.id) updatedPost else it },
                        savedPosts = if (updatedPost.isSaved) {
                            state.savedPosts.map { if (it.id == post.id) updatedPost else it }
                        } else {
                            state.savedPosts.filter { it.id != post.id }
                        },
                        upvotedPosts = state.upvotedPosts.map { if (it.id == post.id) updatedPost else it },
                        downvotedPosts = state.downvotedPosts.map { if (it.id == post.id) updatedPost else it }
                    )
                }
            }
        }
    }

    fun updateComment(updatedComment: Comment) {
        _uiState.update { state ->
            state.copy(
                comments = state.comments.map { if (it.id == updatedComment.id) updatedComment else it },
                savedComments = if (updatedComment.isSaved) {
                    state.savedComments.map { if (it.id == updatedComment.id) updatedComment else it }
                } else {
                    state.savedComments.filter { it.id != updatedComment.id }
                }
            )
        }
    }

    fun saveComment(comment: Comment) {
        viewModelScope.launch {
            val result = commentRepository.save(comment)
            result.onSuccess { updatedComment ->
                updateComment(updatedComment)
            }
        }
    }

    private fun generateState(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }
}
