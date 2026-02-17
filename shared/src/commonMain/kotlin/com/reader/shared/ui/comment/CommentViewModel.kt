package com.reader.shared.ui.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.shared.data.api.CommentOrMore
import com.reader.shared.data.repository.CommentDraftRepository
import com.reader.shared.data.repository.CommentRepository
import com.reader.shared.domain.model.Comment
import com.reader.shared.domain.model.MoreComments
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class FlatCommentItem {
    data class CommentEntry(val comment: Comment) : FlatCommentItem()
    data class MoreEntry(val more: MoreComments) : FlatCommentItem()
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
    val editingOriginalText: String = ""
)

class CommentViewModel(
    private val commentRepository: CommentRepository,
    private val commentDraftRepository: CommentDraftRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentUiState())
    val uiState: StateFlow<CommentUiState> = _uiState.asStateFlow()

    // ==================== Comment State ====================

    fun setComments(comments: List<CommentOrMore>) {
        _uiState.update { it.copy(comments = comments) }
    }

    fun clearComments() {
        _uiState.update { it.copy(
            comments = emptyList(),
            selectedCommentId = null,
            hiddenCommentIds = emptySet()
        ) }
    }

    // ==================== Selection & Hiding ====================

    fun selectComment(commentId: String?) {
        _uiState.update { state ->
            if (commentId != null && state.hiddenCommentIds.contains(commentId)) {
                state.copy(
                    selectedCommentId = commentId,
                    hiddenCommentIds = state.hiddenCommentIds - commentId
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
                selectedCommentId = null
            )
        }
    }

    fun setLastTouchedComment(commentName: String?) {
        _uiState.update { it.copy(lastTouchedCommentName = commentName) }
    }

    // ==================== Flattening ====================

    fun getFlattenedComments(): List<FlatCommentItem> {
        val state = _uiState.value
        return flattenCommentTree(state.comments, state.hiddenCommentIds)
    }

    private fun flattenCommentTree(
        items: List<CommentOrMore>,
        hiddenIds: Set<String>
    ): List<FlatCommentItem> {
        val result = mutableListOf<FlatCommentItem>()
        for (item in items) {
            when (item) {
                is CommentOrMore.CommentItem -> {
                    result.add(FlatCommentItem.CommentEntry(item.comment))
                    if (!hiddenIds.contains(item.comment.id)) {
                        result.addAll(flattenReplies(item.comment.replies, hiddenIds))
                        item.comment.moreReplies?.let { more ->
                            if (more.count > 0 && more.children.isNotEmpty()) {
                                result.add(FlatCommentItem.MoreEntry(more))
                            }
                        }
                    }
                }
                is CommentOrMore.More -> {
                    result.add(FlatCommentItem.MoreEntry(item.more))
                }
            }
        }
        return result
    }

    private fun flattenReplies(
        replies: List<Comment>,
        hiddenIds: Set<String>
    ): List<FlatCommentItem> {
        val result = mutableListOf<FlatCommentItem>()
        for (comment in replies) {
            result.add(FlatCommentItem.CommentEntry(comment))
            if (!hiddenIds.contains(comment.id)) {
                result.addAll(flattenReplies(comment.replies, hiddenIds))
                comment.moreReplies?.let { more ->
                    if (more.count > 0 && more.children.isNotEmpty()) {
                        result.add(FlatCommentItem.MoreEntry(more))
                    }
                }
            }
        }
        return result
    }

    // ==================== Navigation ====================

    fun findRootCommentId(commentId: String): String? {
        val flatComments = getFlattenedComments()
            .filterIsInstance<FlatCommentItem.CommentEntry>()
            .map { it.comment }
        val comment = flatComments.find { it.id == commentId } ?: return null
        if (comment.depth == 0) return comment.id
        var current = comment
        while (current.depth > 0) {
            val parent = flatComments.find { it.name == current.parentId } ?: break
            current = parent
        }
        return current.id
    }

    fun findParentCommentId(commentId: String): String? {
        val flatComments = getFlattenedComments()
            .filterIsInstance<FlatCommentItem.CommentEntry>()
            .map { it.comment }
        val comment = flatComments.find { it.id == commentId } ?: return null
        if (comment.depth == 0) return null
        return flatComments.find { it.name == comment.parentId }?.id
    }

    fun findPrevRootCommentId(commentId: String): String? {
        val state = _uiState.value
        val rootComments = state.comments
            .filterIsInstance<CommentOrMore.CommentItem>()
            .map { it.comment }
        val currentRootId = findRootCommentId(commentId) ?: return null
        val idx = rootComments.indexOfFirst { it.id == currentRootId }
        return if (idx > 0) rootComments[idx - 1].id else null
    }

    fun findNextRootCommentId(commentId: String): String? {
        val state = _uiState.value
        val rootComments = state.comments
            .filterIsInstance<CommentOrMore.CommentItem>()
            .map { it.comment }
        val currentRootId = findRootCommentId(commentId) ?: return null
        val idx = rootComments.indexOfFirst { it.id == currentRootId }
        return if (idx < rootComments.size - 1) rootComments[idx + 1].id else null
    }

    // ==================== Tree Building ====================

    fun buildCommentTree(flatItems: List<CommentOrMore>): List<CommentOrMore> {
        val comments = flatItems.filterIsInstance<CommentOrMore.CommentItem>().map { it.comment }
        val moreEntries = flatItems.filterIsInstance<CommentOrMore.More>()

        val commentByName = mutableMapOf<String, Comment>()
        for (c in comments) commentByName[c.name] = c

        val childNamesOf = mutableMapOf<String, MutableList<String>>()
        for (c in comments) {
            if (c.parentId in commentByName) {
                childNamesOf.getOrPut(c.parentId) { mutableListOf() }.add(c.name)
            }
        }
        val moreOf = mutableMapOf<String, MoreComments>()
        for (m in moreEntries) {
            if (m.more.parentId in commentByName) {
                moreOf[m.more.parentId] = m.more
            }
        }

        for (c in comments.sortedByDescending { it.depth }) {
            val childNames = childNamesOf[c.name]
            val more = moreOf[c.name]
            if (childNames != null || more != null) {
                val children = childNames?.map { commentByName[it]!! } ?: emptyList()
                commentByName[c.name] = commentByName[c.name]!!.copy(
                    replies = children,
                    moreReplies = more
                )
            }
        }

        val result = mutableListOf<CommentOrMore>()
        for (c in comments) {
            if (c.parentId !in commentByName) {
                result.add(CommentOrMore.CommentItem(commentByName[c.name]!!))
            }
        }
        for (m in moreEntries) {
            if (m.more.parentId !in commentByName) {
                result.add(m)
            }
        }
        return result
    }

    // ==================== Tree Modifications ====================

    fun insertMoreComments(more: MoreComments, newComments: List<CommentOrMore>) {
        val treeComments = buildCommentTree(newComments)
        _uiState.update { state ->
            state.copy(
                comments = insertCommentsInTree(state.comments, more, treeComments),
                loadingMoreId = null
            )
        }
    }

    private fun insertCommentsInTree(
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
                    if (item.comment.moreReplies?.id == more.id) {
                        val addedReplies = newComments.filterIsInstance<CommentOrMore.CommentItem>().map { it.comment }
                        val newMore = newComments.filterIsInstance<CommentOrMore.More>().firstOrNull()?.more
                        listOf(
                            CommentOrMore.CommentItem(
                                item.comment.copy(
                                    replies = item.comment.replies + addedReplies,
                                    moreReplies = newMore
                                )
                            )
                        )
                    } else {
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
    }

    private fun insertCommentsInReplies(
        replies: List<Comment>,
        more: MoreComments,
        newComments: List<CommentOrMore>
    ): List<Comment> {
        return replies.map { comment ->
            if (comment.moreReplies?.id == more.id) {
                val addedReplies = newComments.filterIsInstance<CommentOrMore.CommentItem>().map { it.comment }
                val newMore = newComments.filterIsInstance<CommentOrMore.More>().firstOrNull()?.more
                comment.copy(
                    replies = comment.replies + addedReplies,
                    moreReplies = newMore
                )
            } else {
                comment.copy(
                    replies = insertCommentsInReplies(comment.replies, more, newComments)
                )
            }
        }
    }

    fun updateComment(updatedComment: Comment) {
        _uiState.update { state ->
            state.copy(comments = updateCommentInTree(state.comments, updatedComment))
        }
    }

    private fun updateCommentInTree(comments: List<CommentOrMore>, updated: Comment): List<CommentOrMore> {
        return comments.map { item ->
            when (item) {
                is CommentOrMore.CommentItem -> {
                    if (item.comment.id == updated.id) {
                        CommentOrMore.CommentItem(updated.copy(replies = item.comment.replies, moreReplies = item.comment.moreReplies))
                    } else {
                        CommentOrMore.CommentItem(
                            item.comment.copy(replies = updateCommentInReplies(item.comment.replies, updated))
                        )
                    }
                }
                is CommentOrMore.More -> item
            }
        }
    }

    private fun updateCommentInReplies(replies: List<Comment>, updated: Comment): List<Comment> {
        return replies.map { comment ->
            if (comment.id == updated.id) {
                updated.copy(replies = comment.replies, moreReplies = comment.moreReplies)
            } else {
                comment.copy(replies = updateCommentInReplies(comment.replies, updated))
            }
        }
    }

    fun removeComment(commentId: String) {
        _uiState.update { state ->
            state.copy(
                comments = removeCommentFromTree(state.comments, commentId),
                selectedCommentId = null
            )
        }
    }

    private fun removeCommentFromTree(comments: List<CommentOrMore>, commentId: String): List<CommentOrMore> {
        return comments.mapNotNull { item ->
            when (item) {
                is CommentOrMore.CommentItem -> {
                    if (item.comment.id == commentId) {
                        null
                    } else {
                        CommentOrMore.CommentItem(
                            item.comment.copy(replies = removeCommentFromReplies(item.comment.replies, commentId))
                        )
                    }
                }
                is CommentOrMore.More -> item
            }
        }
    }

    private fun removeCommentFromReplies(replies: List<Comment>, commentId: String): List<Comment> {
        return replies.mapNotNull { comment ->
            if (comment.id == commentId) {
                null
            } else {
                comment.copy(replies = removeCommentFromReplies(comment.replies, commentId))
            }
        }
    }

    fun addReplyToTree(parentId: String, newComment: Comment, isReplyToPost: Boolean) {
        _uiState.update { state ->
            state.copy(
                comments = if (isReplyToPost) {
                    listOf(CommentOrMore.CommentItem(newComment.copy(depth = 0))) + state.comments
                } else {
                    addReplyToComment(state.comments, parentId, newComment)
                }
            )
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
                        val adjustedComment = newComment.copy(depth = item.comment.depth + 1)
                        CommentOrMore.CommentItem(
                            item.comment.copy(
                                replies = listOf(adjustedComment) + item.comment.replies
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
                val adjustedComment = newComment.copy(depth = comment.depth + 1)
                comment.copy(replies = listOf(adjustedComment) + comment.replies)
            } else {
                comment.copy(
                    replies = addReplyToReplies(comment.replies, parentId, newComment)
                )
            }
        }
    }

    // ==================== Finding Comments ====================

    fun findCommentById(commentId: String): Comment? {
        for (item in _uiState.value.comments) {
            if (item is CommentOrMore.CommentItem) {
                if (item.comment.id == commentId) return item.comment
                val found = searchReplies(item.comment.replies, commentId)
                if (found != null) return found
            }
        }
        return null
    }

    private fun searchReplies(replies: List<Comment>, commentId: String): Comment? {
        for (comment in replies) {
            if (comment.id == commentId) return comment
            val found = searchReplies(comment.replies, commentId)
            if (found != null) return found
        }
        return null
    }

    fun findCommentByName(commentName: String): Comment? {
        fun searchTree(items: List<CommentOrMore>): Comment? {
            for (item in items) {
                when (item) {
                    is CommentOrMore.CommentItem -> {
                        if (item.comment.name == commentName) return item.comment
                        val found = searchRepliesForName(item.comment.replies, commentName)
                        if (found != null) return found
                    }
                    is CommentOrMore.More -> {}
                }
            }
            return null
        }
        return searchTree(_uiState.value.comments)
    }

    private fun searchRepliesForName(replies: List<Comment>, commentName: String): Comment? {
        for (comment in replies) {
            if (comment.name == commentName) return comment
            val found = searchRepliesForName(comment.replies, commentName)
            if (found != null) return found
        }
        return null
    }

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
                savedDraftText = draft
            )
        }
    }

    fun cancelEdit() {
        _uiState.update { it.copy(editingCommentId = null, editingOriginalText = "", replyText = "", savedDraftText = null) }
    }

    fun startReplyWithQuote(parentId: String, quotedText: String) {
        val formatted = quotedText.lines().joinToString("\n") { ">$it" } + "\n\n"
        val draft = commentDraftRepository?.loadDraft(parentId)
        _uiState.update { it.copy(replyingTo = parentId, editingCommentId = null, replyText = formatted, savedDraftText = draft) }
    }

    fun insertQuotedText(text: String) {
        if (text.isBlank()) return
        val formatted = text.lines().joinToString("\n") { ">$it" } + "\n\n"
        val current = _uiState.value.replyText
        _uiState.update { it.copy(replyText = formatted + current) }
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

    fun submitReply(isReplyToPost: Boolean) {
        val parentId = _uiState.value.replyingTo ?: return
        val text = _uiState.value.replyText.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            val result = commentRepository.submitComment(parentId, text)
            result.onSuccess { newComment ->
                deleteDraft(parentId)
                val adjustedComment = newComment.copy(likes = true, score = 1)
                _uiState.update { it.copy(replyingTo = null, replyText = "", savedDraftText = null) }
                addReplyToTree(parentId, adjustedComment, isReplyToPost)
            }
        }
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
                        savedDraftText = null
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

    fun saveCommentWithCallback(comment: Comment, onSuccess: (Comment) -> Unit) {
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

    fun submitEditWithCallback(findComment: (String) -> Comment?, onSuccess: (Comment) -> Unit) {
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
                        savedDraftText = null
                    )
                }
                onSuccess(updatedComment)
            }
        }
    }

    fun deleteCommentWithCallback(comment: Comment, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = commentRepository.deleteComment(comment)
            result.onSuccess {
                onSuccess()
            }
        }
    }
}
