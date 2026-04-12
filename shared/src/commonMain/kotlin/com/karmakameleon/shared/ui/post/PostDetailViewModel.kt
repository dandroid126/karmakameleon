package com.karmakameleon.shared.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.data.repository.CommentDraftRepository
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.domain.model.CommentSort
import com.karmakameleon.shared.domain.model.MoreComments
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.ui.comment.CommentViewModel
import com.karmakameleon.shared.ui.comment.FlatCommentItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostDetailUiState(
    val post: Post? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val actionError: String? = null,
    val commentSort: CommentSort = CommentSort.CONFIDENCE,
    val suggestedSort: CommentSort? = null,
    val useSuggestedSort: Boolean = true,
    val isLoggedIn: Boolean = false,
    val loggedInUsername: String? = null,
    val focusedCommentId: String? = null
)

class PostDetailViewModel(
    private val subreddit: String,
    private val postId: String,
    private val postRepository: PostRepository,
    commentRepository: CommentRepository,
    private val userRepository: UserRepository,
    commentDraftRepository: CommentDraftRepository,
    private val settingsRepository: SettingsRepository,
    private val commentId: String? = null,
    private val context: Int? = null
) : ViewModel() {

    val commentViewModel = CommentViewModel(commentRepository, commentDraftRepository)

    private val _uiState = MutableStateFlow(PostDetailUiState(
        useSuggestedSort = settingsRepository.useSuggestedSort.value
    ))
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    private var isInitialLoad = true

    init {
        postRepository.getCachedPost(postId)?.let { cachedPost ->
            _uiState.update { it.copy(post = cachedPost) }
        }
        viewModelScope.launch {
            userRepository.isLoggedIn.collect { isLoggedIn ->
                _uiState.update { it.copy(isLoggedIn = isLoggedIn) }
            }
        }
        viewModelScope.launch {
            userRepository.currentAccount.collect { account ->
                _uiState.update { it.copy(loggedInUsername = account?.name) }
            }
        }
        if (userRepository.isLoggedIn.value && userRepository.currentAccount.value == null) {
            viewModelScope.launch { userRepository.loadCurrentUser() }
        }
        _uiState.update { it.copy(focusedCommentId = commentId) }
        loadPostWithComments()
    }

    fun navigateToComment(newCommentId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(focusedCommentId = newCommentId, isLoading = true) }
            commentViewModel.clearComments()
            val result = postRepository.getPostWithComments(
                subreddit = subreddit,
                postId = postId,
                sort = _uiState.value.commentSort,
                commentId = newCommentId
            )
            result.fold(
                onSuccess = { (post, comments) ->
                    _uiState.update { it.copy(post = post, isLoading = false) }
                    commentViewModel.setComments(comments)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load comment") }
                }
            )
        }
    }

    fun viewAllComments() {
        viewModelScope.launch {
            _uiState.update { it.copy(focusedCommentId = null, isLoading = true) }
            commentViewModel.clearComments()
            val result = postRepository.getPostWithComments(
                subreddit = subreddit,
                postId = postId,
                sort = _uiState.value.commentSort
            )
            result.fold(
                onSuccess = { (post, comments) ->
                    _uiState.update { it.copy(post = post, isLoading = false) }
                    commentViewModel.setComments(comments)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load comments") }
                }
            )
        }
    }

    private fun getEffectiveSort(): CommentSort? {
        val state = _uiState.value
        if (isInitialLoad && state.useSuggestedSort) return null
        return state.commentSort
    }

    fun loadPostWithComments(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isLoading = !forceRefresh, 
                    isRefreshing = forceRefresh,
                    error = null
                )
            }
            
            val result = postRepository.getPostWithComments(
                subreddit = subreddit,
                postId = postId,
                sort = getEffectiveSort(),
                commentId = commentId,
                context = context
            )
            
            result.fold(
                onSuccess = { (post, comments) ->
                    val suggestedSort = post.suggestedSort
                    val effectiveSort = if (isInitialLoad && _uiState.value.useSuggestedSort) {
                        suggestedSort ?: CommentSort.CONFIDENCE
                    } else {
                        _uiState.value.commentSort
                    }
                    isInitialLoad = false
                    _uiState.update { 
                        it.copy(
                            post = post, 
                            isLoading = false,
                            isRefreshing = false,
                            suggestedSort = suggestedSort,
                            commentSort = effectiveSort
                        )
                    }
                    commentViewModel.setComments(comments)
                },
                onFailure = { error ->
                    isInitialLoad = false
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            isRefreshing = false,
                            error = error.message ?: "Failed to load post"
                        )
                    }
                }
            )
        }
    }

    fun setCommentSort(sort: CommentSort) {
        if (_uiState.value.commentSort == sort) return
        _uiState.update { it.copy(commentSort = sort) }
        commentViewModel.clearComments()
        loadPostWithComments()
    }

    fun setUseSuggestedSort(enabled: Boolean) {
        settingsRepository.setUseSuggestedSort(enabled)
        _uiState.update { it.copy(useSuggestedSort = enabled) }
    }

    fun loadMoreComments(more: MoreComments) {
        val post = _uiState.value.post ?: return
        if (more.children.isEmpty()) return
        
        viewModelScope.launch {
            commentViewModel.setLoadingMore(more.id)
            
            val result = postRepository.getMoreComments(
                linkId = post.name,
                children = more.children.take(100),
                sort = _uiState.value.commentSort
            )
            
            result.fold(
                onSuccess = { newComments ->
                    commentViewModel.insertMoreComments(more, newComments)
                },
                onFailure = {
                    commentViewModel.setLoadingMore(null)
                }
            )
        }
    }

    fun votePost(direction: Int) {
        val post = _uiState.value.post ?: return
        viewModelScope.launch {
            val result = postRepository.vote(post, direction)
            result.fold(
                onSuccess = { updatedPost ->
                    _uiState.update { it.copy(post = updatedPost) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(actionError = error.message) }
                }
            )
        }
    }

    fun savePost() {
        val post = _uiState.value.post ?: return
        viewModelScope.launch {
            val result = postRepository.save(post)
            result.onSuccess { updatedPost ->
                _uiState.update { it.copy(post = updatedPost) }
            }
        }
    }

    fun navigateToParentRoot(commentId: String) {
        val flatComments = commentViewModel.getFlattenedComments()
            .filterIsInstance<FlatCommentItem.CommentEntry>()
            .map { it.comment }
        var current = flatComments.find { it.id == commentId } ?: return
        while (true) {
            val parent = flatComments.find { it.name == current.parentId }
            if (parent != null) {
                current = parent
            } else {
                break
            }
        }
        if (!current.parentId.startsWith("t1_")) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            commentViewModel.clearComments()
            var parentCommentId = current.parentId.removePrefix("t1_")
            while (true) {
                val result = postRepository.getPostWithComments(
                    subreddit = subreddit,
                    postId = postId,
                    sort = _uiState.value.commentSort,
                    commentId = parentCommentId
                )
                val comments = result.getOrNull()?.second ?: break
                val topComment = comments.filterIsInstance<CommentOrMore.CommentItem>().firstOrNull()?.comment ?: break
                if (!topComment.parentId.startsWith("t1_")) {
                    _uiState.update { it.copy(
                        focusedCommentId = topComment.id,
                        post = result.getOrNull()?.first ?: _uiState.value.post,
                        isLoading = false
                    ) }
                    commentViewModel.setComments(comments)
                    return@launch
                }
                parentCommentId = topComment.parentId.removePrefix("t1_")
            }
            _uiState.update { it.copy(isLoading = false) }
            viewAllComments()
        }
    }

    fun getReplyParentText(): String {
        val parentId = commentViewModel.uiState.value.replyingTo ?: return ""
        val post = _uiState.value.post
        if (post != null && post.name == parentId) {
            return post.selfText?.takeIf { it.isNotBlank() } ?: post.title
        }
        return commentViewModel.findCommentByName(parentId)?.body ?: ""
    }

    fun insertQuote() {
        commentViewModel.insertQuote(getReplyParentText())
    }

    fun submitReply() {
        val parentId = commentViewModel.uiState.value.replyingTo ?: return
        val post = _uiState.value.post
        val isReplyToPost = post != null && parentId == post.name
        viewModelScope.launch {
            val error = commentViewModel.submitReplyWithError(isReplyToPost)
            if (error != null) {
                _uiState.update { it.copy(actionError = error) }
            }
        }
    }

    fun clearActionError() {
        _uiState.update { it.copy(actionError = null) }
    }
}
