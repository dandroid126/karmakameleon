package com.reader.android.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.shared.data.api.CommentOrMore
import com.reader.shared.data.repository.CommentRepository
import com.reader.shared.data.repository.PostRepository
import com.reader.shared.data.repository.UserRepository
import com.reader.shared.domain.model.Comment
import com.reader.shared.domain.model.CommentSort
import com.reader.shared.domain.model.MoreComments
import com.reader.shared.domain.model.Post
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostDetailUiState(
    val post: Post? = null,
    val comments: List<CommentOrMore> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingComments: Boolean = false,
    val error: String? = null,
    val commentSort: CommentSort = CommentSort.CONFIDENCE,
    val isLoggedIn: Boolean = false,
    val replyingTo: String? = null,
    val replyText: String = ""
)

class PostDetailViewModel(
    private val subreddit: String,
    private val postId: String,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostDetailUiState())
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    init {
        // Show cached post immediately so the header renders while comments load
        postRepository.getCachedPost(postId)?.let { cachedPost ->
            _uiState.update { it.copy(post = cachedPost) }
        }
        viewModelScope.launch {
            userRepository.isLoggedIn.collect { isLoggedIn ->
                _uiState.update { it.copy(isLoggedIn = isLoggedIn) }
            }
        }
        loadPostWithComments()
    }

    fun loadPostWithComments() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val result = postRepository.getPostWithComments(
                subreddit = subreddit,
                postId = postId,
                sort = _uiState.value.commentSort
            )
            
            result.fold(
                onSuccess = { (post, comments) ->
                    _uiState.update {
                        it.copy(
                            post = post,
                            comments = comments,
                            isLoading = false
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
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
        loadPostWithComments()
    }

    fun loadMoreComments(more: MoreComments) {
        val post = _uiState.value.post ?: return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingComments = true) }
            
            val result = postRepository.getMoreComments(
                linkId = post.name,
                children = more.children.take(100),
                sort = _uiState.value.commentSort
            )
            
            result.fold(
                onSuccess = { newComments ->
                    _uiState.update { state ->
                        val updatedComments = insertComments(
                            state.comments,
                            more,
                            newComments
                        )
                        state.copy(
                            comments = updatedComments,
                            isLoadingComments = false
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingComments = false) }
                }
            )
        }
    }

    private fun insertComments(
        existing: List<CommentOrMore>,
        more: MoreComments,
        newComments: List<CommentOrMore>
    ): List<CommentOrMore> {
        return existing.flatMap { item ->
            when (item) {
                is CommentOrMore.More -> {
                    if (item.more.id == more.id) {
                        newComments
                    } else {
                        listOf(item)
                    }
                }
                is CommentOrMore.CommentItem -> {
                    listOf(
                        CommentOrMore.CommentItem(
                            item.comment.copy(
                                replies = insertCommentsInReplies(item.comment.replies, more, newComments)
                            )
                        )
                    )
                }
            }
        }
    }

    private fun insertCommentsInReplies(
        replies: List<Comment>,
        more: MoreComments,
        newComments: List<CommentOrMore>
    ): List<Comment> {
        return replies.map { comment ->
            comment.copy(
                replies = insertCommentsInReplies(comment.replies, more, newComments)
            )
        }
    }

    fun votePost(direction: Int) {
        val post = _uiState.value.post ?: return
        viewModelScope.launch {
            val result = postRepository.vote(post, direction)
            result.onSuccess { updatedPost ->
                _uiState.update { it.copy(post = updatedPost) }
            }
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

    fun voteComment(comment: Comment, direction: Int) {
        viewModelScope.launch {
            val result = commentRepository.vote(comment, direction)
            result.onSuccess { updatedComment ->
                _uiState.update { state ->
                    state.copy(
                        comments = updateCommentInList(state.comments, updatedComment)
                    )
                }
            }
        }
    }

    fun saveComment(comment: Comment) {
        viewModelScope.launch {
            val result = commentRepository.save(comment)
            result.onSuccess { updatedComment ->
                _uiState.update { state ->
                    state.copy(
                        comments = updateCommentInList(state.comments, updatedComment)
                    )
                }
            }
        }
    }

    private fun updateCommentInList(
        comments: List<CommentOrMore>,
        updated: Comment
    ): List<CommentOrMore> {
        return comments.map { item ->
            when (item) {
                is CommentOrMore.CommentItem -> {
                    if (item.comment.id == updated.id) {
                        CommentOrMore.CommentItem(updated)
                    } else {
                        CommentOrMore.CommentItem(
                            item.comment.copy(
                                replies = updateCommentInReplies(item.comment.replies, updated)
                            )
                        )
                    }
                }
                is CommentOrMore.More -> item
            }
        }
    }

    private fun updateCommentInReplies(
        replies: List<Comment>,
        updated: Comment
    ): List<Comment> {
        return replies.map { comment ->
            if (comment.id == updated.id) {
                updated
            } else {
                comment.copy(
                    replies = updateCommentInReplies(comment.replies, updated)
                )
            }
        }
    }

    fun setReplyingTo(parentId: String?) {
        _uiState.update { it.copy(replyingTo = parentId, replyText = "") }
    }

    fun setReplyText(text: String) {
        _uiState.update { it.copy(replyText = text) }
    }

    fun submitReply() {
        val parentId = _uiState.value.replyingTo ?: return
        val text = _uiState.value.replyText.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            val result = commentRepository.submitComment(parentId, text)
            result.onSuccess { newComment ->
                _uiState.update { state ->
                    state.copy(
                        comments = if (parentId == state.post?.name) {
                            listOf(CommentOrMore.CommentItem(newComment)) + state.comments
                        } else {
                            addReplyToComment(state.comments, parentId, newComment)
                        },
                        replyingTo = null,
                        replyText = ""
                    )
                }
            }
        }
    }

    private fun addReplyToComment(
        comments: List<CommentOrMore>,
        parentId: String,
        newComment: Comment
    ): List<CommentOrMore> {
        return comments.map { item ->
            when (item) {
                is CommentOrMore.CommentItem -> {
                    if (item.comment.name == parentId) {
                        CommentOrMore.CommentItem(
                            item.comment.copy(
                                replies = listOf(newComment) + item.comment.replies
                            )
                        )
                    } else {
                        CommentOrMore.CommentItem(
                            item.comment.copy(
                                replies = addReplyToReplies(item.comment.replies, parentId, newComment)
                            )
                        )
                    }
                }
                is CommentOrMore.More -> item
            }
        }
    }

    private fun addReplyToReplies(
        replies: List<Comment>,
        parentId: String,
        newComment: Comment
    ): List<Comment> {
        return replies.map { comment ->
            if (comment.name == parentId) {
                comment.copy(replies = listOf(newComment) + comment.replies)
            } else {
                comment.copy(
                    replies = addReplyToReplies(comment.replies, parentId, newComment)
                )
            }
        }
    }
}
