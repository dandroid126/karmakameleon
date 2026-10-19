package com.karmakameleon.shared.ui.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.data.repository.CommentDraftRepository
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.MoreComments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FlatCommentItem {
    data class CommentEntry(
        val comment: Comment,
    ) : FlatCommentItem

    data class MoreEntry(
        val more: MoreComments,
    ) : FlatCommentItem
}

data class CommentUiState(
    val comments: List<CommentOrMore> = emptyList(),
    val loadingMoreId: String? = null,
    val selectedCommentId: String? = null,
    val hiddenCommentIds: Set<String> = emptySet(),
    val lastTouchedCommentName: String? = null,
    val replyingTo: String? = null,
    val replyText: String = "",
    val savedDraftText: String? = null,
    val editingCommentId: String? = null,
    val editingOriginalText: String = "",
)

class CommentViewModel(
    private val commentRepository: CommentRepository,
    private val commentDraftRepository: CommentDraftRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CommentUiState())
    val uiState: StateFlow<CommentUiState> = _uiState.asStateFlow()

    // ==================== Comment State ====================

    fun setComments(comments: List<CommentOrMore>) {
        _uiState.update { it.copy(comments = comments) }
    }

    fun clearComments() {
        _uiState.update {
            it.copy(
                comments = emptyList(),
                selectedCommentId = null,
                hiddenCommentIds = emptySet(),
            )
        }
    }

    // ==================== Selection & Hiding ====================

    fun selectComment(commentId: String?) {
        _uiState.update { state ->
            if (commentId != null && state.hiddenCommentIds.contains(commentId)) {
                state.copy(
                    selectedCommentId = commentId,
                    hiddenCommentIds = state.hiddenCommentIds - commentId,
                )
            } else {
                state.copy(selectedCommentId = commentId)
            }
        }
    }

    fun hideComment(commentId: String) {
        _uiState.update { state ->
            state.copy(
                hiddenCommentIds = state.hiddenCommentIds + commentId,
                selectedCommentId = null,
            )
        }
    }

    fun setLastTouchedComment(commentName: String?) {
        _uiState.update { it.copy(lastTouchedCommentName = commentName) }
    }

    // ==================== Flattening ====================

    fun getFlattenedComments(): List<FlatCommentItem> {
        val state = _uiState.value
        return state.comments.flatten(state.hiddenCommentIds)
    }

    // ==================== Navigation ====================

    fun findRootCommentId(commentId: String): String? {
        val visible = visibleComments()
        var current = visible.find { it.id == commentId } ?: return null
        while (current.depth > 0) {
            current = visible.find { it.name == current.parentId } ?: break
        }
        return current.id
    }

    fun findParentCommentId(commentId: String): String? {
        val visible = visibleComments()
        val comment = visible.find { it.id == commentId }?.takeIf { it.depth > 0 } ?: return null
        return visible.find { it.name == comment.parentId }?.id
    }

    fun findPrevRootCommentId(commentId: String): String? = neighborRootCommentId(commentId, -1)

    fun findNextRootCommentId(commentId: String): String? = neighborRootCommentId(commentId, 1)

    private fun visibleComments(): List<Comment> =
        getFlattenedComments().filterIsInstance<FlatCommentItem.CommentEntry>().map { it.comment }

    // The top-level comment offset places before or after the one whose thread contains commentId.
    private fun neighborRootCommentId(
        commentId: String,
        offset: Int,
    ): String? {
        val rootIds =
            _uiState.value.comments
                .filterIsInstance<CommentOrMore.CommentItem>()
                .map { it.comment.id }
        val currentRootId = findRootCommentId(commentId) ?: return null
        return rootIds.getOrNull(rootIds.indexOf(currentRootId) + offset)
    }

    // ==================== Tree Modifications ====================

    fun insertMoreComments(
        more: MoreComments,
        newComments: List<CommentOrMore>,
    ) {
        val treeComments = buildCommentTree(newComments)
        _uiState.update { state ->
            state.copy(
                comments = state.comments.withMoreComments(more, treeComments),
                loadingMoreId = null,
            )
        }
    }

    fun updateComment(updatedComment: Comment) {
        _uiState.update { state -> state.copy(comments = state.comments.withUpdatedComment(updatedComment)) }
    }

    fun removeComment(commentId: String) {
        _uiState.update { state ->
            state.copy(
                comments = state.comments.withoutComment(commentId),
                selectedCommentId = null,
            )
        }
    }

    fun addReplyToTree(
        parentId: String,
        newComment: Comment,
        isReplyToPost: Boolean,
    ) {
        _uiState.update { state ->
            state.copy(
                comments =
                    if (isReplyToPost) {
                        listOf(CommentOrMore.CommentItem(newComment.copy(depth = 0))) + state.comments
                    } else {
                        state.comments.withReply(parentId, newComment)
                    },
            )
        }
    }

    // ==================== Finding Comments ====================

    fun findCommentById(commentId: String): Comment? = _uiState.value.comments.findComment { it.id == commentId }

    fun findCommentByName(commentName: String): Comment? = _uiState.value.comments.findComment { it.name == commentName }

    // ==================== Loading More ====================

    fun setLoadingMore(moreId: String?) {
        _uiState.update { it.copy(loadingMoreId = moreId) }
    }

    // ==================== Reply/Edit State ====================

    fun setReplyingTo(parentId: String?) {
        val draft = if (parentId != null) commentDraftRepository?.loadDraft(parentId) else null
        _uiState.update { it.copy(replyingTo = parentId, editingCommentId = null, replyText = "", savedDraftText = draft) }
    }

    fun setReplyText(text: String) {
        _uiState.update { it.copy(replyText = text) }
    }

    fun startEditComment(comment: Comment) {
        val draft = commentDraftRepository?.loadDraft(comment.id)
        _uiState.update {
            it.copy(
                editingCommentId = comment.id,
                editingOriginalText = comment.body,
                replyingTo = null,
                replyText = comment.body,
                savedDraftText = draft,
            )
        }
    }

    fun cancelEdit() {
        _uiState.update { it.copy(editingCommentId = null, editingOriginalText = "", replyText = "", savedDraftText = null) }
    }

    fun startReplyWithQuote(
        parentId: String,
        quotedText: String,
    ) {
        val formatted = quotedText.lines().joinToString("\n") { ">$it" } + "\n\n"
        val draft = commentDraftRepository?.loadDraft(parentId)
        _uiState.update { it.copy(replyingTo = parentId, editingCommentId = null, replyText = formatted, savedDraftText = draft) }
    }

    fun insertQuote(parentText: String) {
        if (parentText.isBlank()) return
        val formatted = parentText.lines().joinToString("\n") { ">$it" } + "\n\n"
        val current = _uiState.value.replyText
        _uiState.update { it.copy(replyText = formatted + current) }
    }

    fun saveDraft() {
        val parentId = _uiState.value.replyingTo ?: _uiState.value.editingCommentId ?: return
        val text = _uiState.value.replyText
        if (text.isNotBlank()) {
            commentDraftRepository?.saveDraft(parentId, text)
            _uiState.update { it.copy(savedDraftText = text) }
        }
    }

    fun applyDraft(draft: String) {
        _uiState.update { it.copy(replyText = draft) }
    }

    private fun deleteDraft(parentId: String) {
        commentDraftRepository?.deleteDraft(parentId)
    }

    // ==================== CRUD Operations (Tree-based) ====================

    fun saveComment(comment: Comment) {
        viewModelScope.launch {
            val result = commentRepository.save(comment)
            result.onSuccess { updatedComment ->
                updateComment(updatedComment)
            }
        }
    }

    suspend fun submitReplyWithError(isReplyToPost: Boolean): String? {
        val parentId = _uiState.value.replyingTo ?: return null
        val text = _uiState.value.replyText.trim()
        if (text.isEmpty()) return null

        val result = commentRepository.submitComment(parentId, text)
        return result.fold(
            onSuccess = { newComment ->
                deleteDraft(parentId)
                val adjustedComment = newComment.copy(likes = true, score = 1)
                _uiState.update { it.copy(replyingTo = null, replyText = "", savedDraftText = null) }
                addReplyToTree(parentId, adjustedComment, isReplyToPost)
                null
            },
            onFailure = { error -> error.message },
        )
    }

    fun submitEdit() {
        val editingId = _uiState.value.editingCommentId ?: return
        val text = _uiState.value.replyText.trim()
        if (text.isEmpty()) return

        val comment = findCommentById(editingId) ?: return
        viewModelScope.launch {
            val result = commentRepository.editComment(comment, text)
            result.onSuccess { updatedComment ->
                deleteDraft(editingId)
                _uiState.update {
                    it.copy(
                        editingCommentId = null,
                        editingOriginalText = "",
                        replyText = "",
                        savedDraftText = null,
                    )
                }
                updateComment(updatedComment)
            }
        }
    }

    fun deleteComment(commentId: String) {
        val comment = findCommentById(commentId) ?: return
        viewModelScope.launch {
            val result = commentRepository.deleteComment(comment)
            result.onSuccess {
                removeComment(commentId)
            }
        }
    }

    // ==================== CRUD Operations (Callback-based for flat lists) ====================

    fun saveCommentWithCallback(
        comment: Comment,
        onSuccess: (Comment) -> Unit,
    ) {
        viewModelScope.launch {
            val result = commentRepository.save(comment)
            result.onSuccess { updatedComment ->
                onSuccess(updatedComment)
            }
        }
    }

    fun submitReplyWithCallback(onSuccess: (Comment) -> Unit) {
        val parentId = _uiState.value.replyingTo ?: return
        val text = _uiState.value.replyText.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            val result = commentRepository.submitComment(parentId, text)
            result.onSuccess { newComment ->
                deleteDraft(parentId)
                val adjustedComment = newComment.copy(likes = true, score = 1)
                _uiState.update { it.copy(replyingTo = null, replyText = "", savedDraftText = null) }
                onSuccess(adjustedComment)
            }
        }
    }

    fun submitEditWithCallback(
        findComment: (String) -> Comment?,
        onSuccess: (Comment) -> Unit,
    ) {
        val editingId = _uiState.value.editingCommentId ?: return
        val text = _uiState.value.replyText.trim()
        if (text.isEmpty()) return

        val comment = findComment(editingId) ?: return
        viewModelScope.launch {
            val result = commentRepository.editComment(comment, text)
            result.onSuccess { updatedComment ->
                deleteDraft(editingId)
                _uiState.update {
                    it.copy(
                        editingCommentId = null,
                        editingOriginalText = "",
                        replyText = "",
                        savedDraftText = null,
                    )
                }
                onSuccess(updatedComment)
            }
        }
    }

    fun deleteCommentWithCallback(
        comment: Comment,
        onSuccess: () -> Unit,
    ) {
        viewModelScope.launch {
            val result = commentRepository.deleteComment(comment)
            result.onSuccess {
                onSuccess()
            }
        }
    }
}
