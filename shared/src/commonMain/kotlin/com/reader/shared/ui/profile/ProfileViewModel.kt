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
import com.reader.shared.ui.comment.CommentViewModel
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
    val isRefreshing: Boolean = false,
    val isLoadingContent: Boolean = false,
    val isLoadingMorePosts: Boolean = false,
    val isLoadingMoreComments: Boolean = false,
    val isLoadingMoreSavedPosts: Boolean = false,
    val isLoadingMoreSavedComments: Boolean = false,
    val isLoadingMoreUpvoted: Boolean = false,
    val isLoadingMoreDownvoted: Boolean = false,
    val hasMorePosts: Boolean = true,
    val hasMoreComments: Boolean = true,
    val hasMoreSavedPosts: Boolean = true,
    val hasMoreSavedComments: Boolean = true,
    val hasMoreUpvoted: Boolean = true,
    val hasMoreDownvoted: Boolean = true,
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
    val isOwnProfile: Boolean = false,
    val errorMessage: String? = null,
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
    commentRepository: CommentRepository,
    private val authManager: AuthManager
) : ViewModel() {

    val commentViewModel = CommentViewModel(commentRepository)

    private val _uiState = MutableStateFlow(ProfileUiState(clientId = authManager.getClientId()))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun clearAllData() {
        _uiState.value = ProfileUiState(clientId = authManager.getClientId())
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun setClientId(clientId: String) {
        authManager.setClientId(clientId)
        _uiState.update { it.copy(clientId = clientId) }
    }

    fun loadOwnProfile(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !forceRefresh,
                    isRefreshing = forceRefresh,
                    isOwnProfile = true,
                    isLoggedIn = true
                )
            }
            val result = userRepository.loadCurrentUser()
            result.fold(
                onSuccess = { account ->
                    _uiState.update {
                        it.copy(
                            account = account,
                            isLoading = false,
                            isRefreshing = false
                        )
                    }
                    loadUserPosts(account.name)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.message
                        )
                    }
                }
            )
        }
    }

    fun loadUserProfile(username: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !forceRefresh,
                    isRefreshing = forceRefresh,
                    isOwnProfile = false
                )
            }
            val result = userRepository.getUser(username)
            result.fold(
                onSuccess = { user ->
                    _uiState.update {
                        it.copy(
                            user = user,
                            isLoading = false,
                            isRefreshing = false
                        )
                    }
                    loadUserPosts(username)
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.message
                        )
                    }
                }
            )
        }
    }

    private fun getUsername(): String? {
        return if (_uiState.value.isOwnProfile) _uiState.value.account?.name
        else _uiState.value.user?.name
    }

    private fun loadUserPosts(
        username: String,
        sort: PostSort = _uiState.value.postsSort,
        timeFilter: TimeFilter = _uiState.value.postsTimeFilter,
        forceRefresh: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingContent = true,
                    posts = if (forceRefresh) emptyList() else it.posts,
                    hasMorePosts = true
                )
            }
            val result = userRepository.getUserPosts(username, sort, timeFilter)
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            posts = listing.items,
                            postsAfter = listing.after,
                            hasMorePosts = listing.hasMore,
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

    fun loadMoreUserPosts() {
        val currentState = _uiState.value
        if (currentState.isLoadingMorePosts || !currentState.hasMorePosts || currentState.postsAfter == null) return
        val username = getUsername() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMorePosts = true) }
            val result = userRepository.getUserPosts(username, currentState.postsSort, currentState.postsTimeFilter, currentState.postsAfter)
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            posts = it.posts + listing.items,
                            postsAfter = listing.after,
                            hasMorePosts = listing.hasMore,
                            isLoadingMorePosts = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMorePosts = false) }
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
            _uiState.update {
                it.copy(
                    isLoadingContent = true,
                    comments = if (forceRefresh) emptyList() else it.comments,
                    hasMoreComments = true
                )
            }
            val result = userRepository.getUserComments(username, sort, timeFilter)
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            comments = listing.items,
                            commentsAfter = listing.after,
                            hasMoreComments = listing.hasMore,
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

    fun loadMoreUserComments() {
        val currentState = _uiState.value
        if (currentState.isLoadingMoreComments || !currentState.hasMoreComments || currentState.commentsAfter == null) return
        val username = getUsername() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreComments = true) }
            val result = userRepository.getUserComments(username, currentState.commentsSort, currentState.commentsTimeFilter, currentState.commentsAfter)
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            comments = it.comments + listing.items,
                            commentsAfter = listing.after,
                            hasMoreComments = listing.hasMore,
                            isLoadingMoreComments = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMoreComments = false) }
                }
            )
        }
    }

    private fun loadSavedPosts(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingContent = true,
                    savedPosts = if (forceRefresh) emptyList() else it.savedPosts,
                    hasMoreSavedPosts = true
                )
            }
            val result = userRepository.getSavedPosts()
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            savedPosts = listing.items,
                            savedPostsAfter = listing.after,
                            hasMoreSavedPosts = listing.hasMore,
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

    fun loadMoreSavedPosts() {
        val currentState = _uiState.value
        if (currentState.isLoadingMoreSavedPosts || !currentState.hasMoreSavedPosts || currentState.savedPostsAfter == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreSavedPosts = true) }
            val result = userRepository.getSavedPosts(currentState.savedPostsAfter)
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            savedPosts = it.savedPosts + listing.items,
                            savedPostsAfter = listing.after,
                            hasMoreSavedPosts = listing.hasMore,
                            isLoadingMoreSavedPosts = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMoreSavedPosts = false) }
                }
            )
        }
    }

    private fun loadSavedComments(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingContent = true,
                    savedComments = if (forceRefresh) emptyList() else it.savedComments,
                    hasMoreSavedComments = true
                )
            }
            val result = userRepository.getSavedComments()
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            savedComments = listing.items,
                            savedCommentsAfter = listing.after,
                            hasMoreSavedComments = listing.hasMore,
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

    fun loadMoreSavedComments() {
        val currentState = _uiState.value
        if (currentState.isLoadingMoreSavedComments || !currentState.hasMoreSavedComments || currentState.savedCommentsAfter == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreSavedComments = true) }
            val result = userRepository.getSavedComments(currentState.savedCommentsAfter)
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            savedComments = it.savedComments + listing.items,
                            savedCommentsAfter = listing.after,
                            hasMoreSavedComments = listing.hasMore,
                            isLoadingMoreSavedComments = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMoreSavedComments = false) }
                }
            )
        }
    }

    private fun loadUpvotedPosts(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingContent = true,
                    upvotedPosts = if (forceRefresh) emptyList() else it.upvotedPosts,
                    hasMoreUpvoted = true
                )
            }
            val result = userRepository.getUpvotedPosts()
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            upvotedPosts = listing.items,
                            upvotedAfter = listing.after,
                            hasMoreUpvoted = listing.hasMore,
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

    fun loadMoreUpvotedPosts() {
        val currentState = _uiState.value
        if (currentState.isLoadingMoreUpvoted || !currentState.hasMoreUpvoted || currentState.upvotedAfter == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreUpvoted = true) }
            val result = userRepository.getUpvotedPosts(currentState.upvotedAfter)
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            upvotedPosts = it.upvotedPosts + listing.items,
                            upvotedAfter = listing.after,
                            hasMoreUpvoted = listing.hasMore,
                            isLoadingMoreUpvoted = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMoreUpvoted = false) }
                }
            )
        }
    }

    private fun loadDownvotedPosts(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingContent = true,
                    downvotedPosts = if (forceRefresh) emptyList() else it.downvotedPosts,
                    hasMoreDownvoted = true
                )
            }
            val result = userRepository.getDownvotedPosts()
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            downvotedPosts = listing.items,
                            downvotedAfter = listing.after,
                            hasMoreDownvoted = listing.hasMore,
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

    fun loadMoreDownvotedPosts() {
        val currentState = _uiState.value
        if (currentState.isLoadingMoreDownvoted || !currentState.hasMoreDownvoted || currentState.downvotedAfter == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreDownvoted = true) }
            val result = userRepository.getDownvotedPosts(currentState.downvotedAfter)
            result.fold(
                onSuccess = { listing ->
                    _uiState.update {
                        it.copy(
                            downvotedPosts = it.downvotedPosts + listing.items,
                            downvotedAfter = listing.after,
                            hasMoreDownvoted = listing.hasMore,
                            isLoadingMoreDownvoted = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingMoreDownvoted = false) }
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
            ProfileTab.ABOUT -> if (_uiState.value.isOwnProfile) loadOwnProfile(forceRefresh = true) else loadUserProfile(username, forceRefresh = true)
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
        clearAllData()
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
        commentViewModel.saveCommentWithCallback(comment) { updatedComment ->
            updateComment(updatedComment)
        }
    }

    fun submitReply() {
        commentViewModel.submitReplyWithCallback { /* Reply submitted, state already cleared */ }
    }

    fun submitEdit() {
        commentViewModel.submitEditWithCallback(
            findComment = { findCommentById(it) },
            onSuccess = { updatedComment ->
                updateComment(updatedComment)
            }
        )
    }

    fun deleteComment(commentId: String) {
        val comment = findCommentById(commentId) ?: return
        commentViewModel.deleteCommentWithCallback(comment) {
            _uiState.update { state ->
                state.copy(
                    comments = state.comments.filter { it.id != commentId },
                    savedComments = state.savedComments.filter { it.id != commentId }
                )
            }
        }
    }

    fun findCommentById(commentId: String): Comment? {
        return _uiState.value.comments.find { it.id == commentId }
            ?: _uiState.value.savedComments.find { it.id == commentId }
    }

    private fun generateState(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..16).map { chars.random() }.joinToString("")
    }
}
