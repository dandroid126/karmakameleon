package com.reader.android.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reader.android.data.CommentDraftRepository
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

sealed class FlatCommentItem {
    data class CommentEntry(val comment: Comment) : FlatCommentItem()
    data class MoreEntry(val more: MoreComments) : FlatCommentItem()
}

data class PostDetailUiState(
    val post: Post? = null,
    val comments: List<CommentOrMore> = emptyList(),
    val isLoading: Boolean = false,
    val loadingMoreId: String? = null,
    val error: String? = null,
    val commentSort: CommentSort = CommentSort.CONFIDENCE,
    val isLoggedIn: Boolean = false,
    val loggedInUsername: String? = null,
    val replyingTo: String? = null,
    val replyText: String = "",
    val savedDraftText: String? = null,
    val editingCommentId: String? = null,
    val editingOriginalText: String = "",
    val selectedCommentId: String? = null,
    val hiddenCommentIds: Set<String> = emptySet(),
    val lastTouchedCommentName: String? = null,
    val focusedCommentId: String? = null
)

class PostDetailViewModel(
    private val subreddit: String,
    private val postId: String,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val userRepository: UserRepository,
    private val commentDraftRepository: CommentDraftRepository,
    private val commentId: String? = null
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
            _uiState.update { it.copy(
                focusedCommentId = newCommentId,
                isLoading = true,
                comments = emptyList(),
                selectedCommentId = null,
                hiddenCommentIds = emptySet()
            ) }
            val result = postRepository.getPostWithComments(
                subreddit = subreddit,
                postId = postId,
                sort = _uiState.value.commentSort,
                commentId = newCommentId
            )
            result.fold(
                onSuccess = { (post, comments) ->
                    _uiState.update { it.copy(post = post, comments = comments, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load comment") }
                }
            )
        }
    }

    fun viewAllComments() {
        viewModelScope.launch {
            _uiState.update { it.copy(
                focusedCommentId = null,
                isLoading = true,
                comments = emptyList(),
                selectedCommentId = null,
                hiddenCommentIds = emptySet()
            ) }
            val result = postRepository.getPostWithComments(
                subreddit = subreddit,
                postId = postId,
                sort = _uiState.value.commentSort
            )
            result.fold(
                onSuccess = { (post, comments) ->
                    _uiState.update { it.copy(post = post, comments = comments, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load comments") }
                }
            )
        }
    }

    fun loadPostWithComments() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val result = postRepository.getPostWithComments(
                subreddit = subreddit,
                postId = postId,
                sort = _uiState.value.commentSort,
                commentId = commentId
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
        _uiState.update { it.copy(commentSort = sort, comments = emptyList()) }
        loadPostWithComments()
    }

    fun loadMoreComments(more: MoreComments) {
        val post = _uiState.value.post ?: return
        if (more.children.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(loadingMoreId = more.id) }
            
            val result = postRepository.getMoreComments(
                linkId = post.name,
                children = more.children.take(100),
                sort = _uiState.value.commentSort
            )
            
            result.fold(
                onSuccess = { newComments ->
                    val treeComments = buildCommentTree(newComments)
                    _uiState.update { state ->
                        val updatedComments = insertComments(
                            state.comments,
                            more,
                            treeComments
                        )
                        state.copy(
                            comments = updatedComments,
                            loadingMoreId = null
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(loadingMoreId = null) }
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

    private fun buildCommentTree(flatItems: List<CommentOrMore>): List<CommentOrMore> {
        val comments = flatItems.filterIsInstance<CommentOrMore.CommentItem>().map { it.comment }
        val moreEntries = flatItems.filterIsInstance<CommentOrMore.More>()

        val commentByName = mutableMapOf<String, Comment>()
        for (c in comments) commentByName[c.name] = c

        // Track child names per parent and more entries per parent (only within this batch)
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

        // Build tree bottom-up (deepest first)
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

        // Roots: items whose parent is not in this batch
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

    fun updateComment(updatedComment: Comment) {
        _uiState.update { state ->
            state.copy(
                comments = updateCommentInTree(state.comments, updatedComment)
            )
        }
    }

    fun saveComment(comment: Comment) {
        viewModelScope.launch {
            val result = commentRepository.save(comment)
            result.onSuccess { updatedComment ->
                _uiState.update { state ->
                    state.copy(
                        comments = updateCommentInTree(state.comments, updatedComment)
                    )
                }
            }
        }
    }


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

    fun navigateToParentRoot(commentId: String) {
        val flatComments = getFlattenedComments()
            .filterIsInstance<FlatCommentItem.CommentEntry>()
            .map { it.comment }
        var current = flatComments.find { it.id == commentId } ?: return
        // Walk up as far as we can in loaded comments
        while (true) {
            val parent = flatComments.find { it.name == current.parentId }
            if (parent != null) {
                current = parent
            } else {
                break
            }
        }
        // current is the topmost loaded comment
        if (!current.parentId.startsWith("t1_")) return // already at root
        // Walk up via API to find the actual root
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, comments = emptyList(), selectedCommentId = null, hiddenCommentIds = emptySet()) }
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
                    // Found the root - navigate to it
                    _uiState.update { it.copy(
                        focusedCommentId = topComment.id,
                        post = result.getOrNull()?.first ?: _uiState.value.post,
                        comments = comments,
                        isLoading = false
                    ) }
                    return@launch
                }
                parentCommentId = topComment.parentId.removePrefix("t1_")
            }
            // Fallback: just show all comments
            _uiState.update { it.copy(isLoading = false) }
            viewAllComments()
        }
    }

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

    fun setReplyingTo(parentId: String?) {
        val draft = if (parentId != null) commentDraftRepository.loadDraft(parentId) else null
        _uiState.update { it.copy(replyingTo = parentId, editingCommentId = null, replyText = "", savedDraftText = draft) }
    }

    fun getReplyParentText(): String {
        val parentId = _uiState.value.replyingTo ?: return ""
        val post = _uiState.value.post
        if (post != null && post.name == parentId) {
            return post.selfText?.takeIf { it.isNotBlank() } ?: post.title
        }
        fun searchComments(items: List<CommentOrMore>): String? {
            for (item in items) {
                when (item) {
                    is CommentOrMore.CommentItem -> {
                        if (item.comment.name == parentId) return item.comment.body
                        val found = searchComments(item.comment.replies.map { CommentOrMore.CommentItem(it) })
                        if (found != null) return found
                    }
                    is CommentOrMore.More -> {}
                }
            }
            return null
        }
        return searchComments(_uiState.value.comments) ?: ""
    }

    fun setLastTouchedComment(commentName: String?) {
        _uiState.update { it.copy(lastTouchedCommentName = commentName) }
    }

    fun startReplyWithQuote(parentId: String, quotedText: String) {
        val formatted = quotedText.lines().joinToString("\n") { ">$it" } + "\n\n"
        val draft = commentDraftRepository.loadDraft(parentId)
        _uiState.update { it.copy(replyingTo = parentId, editingCommentId = null, replyText = formatted, savedDraftText = draft) }
    }

    fun insertQuotedText(text: String) {
        if (text.isBlank()) return
        val formatted = text.lines().joinToString("\n") { ">$it" } + "\n\n"
        val current = _uiState.value.replyText
        _uiState.update { it.copy(replyText = formatted + current) }
    }

    fun insertQuote() {
        val parentText = getReplyParentText()
        if (parentText.isBlank()) return
        val formatted = parentText.lines().joinToString("\n") { ">$it" } + "\n\n"
        val current = _uiState.value.replyText
        _uiState.update { it.copy(replyText = formatted + current) }
    }

    fun saveDraft() {
        val parentId = _uiState.value.replyingTo ?: _uiState.value.editingCommentId ?: return
        val text = _uiState.value.replyText
        if (text.isNotBlank()) {
            commentDraftRepository.saveDraft(parentId, text)
            _uiState.update { it.copy(savedDraftText = text) }
        }
    }

    fun applyDraft(draft: String) {
        _uiState.update { it.copy(replyText = draft) }
    }

    private fun deleteDraft(parentId: String) {
        commentDraftRepository.deleteDraft(parentId)
    }

    fun setReplyText(text: String) {
        _uiState.update { it.copy(replyText = text) }
    }

    fun startEditComment(comment: Comment) {
        val draft = commentDraftRepository.loadDraft(comment.id)
        _uiState.update { it.copy(editingCommentId = comment.id, editingOriginalText = comment.body, replyingTo = null, replyText = comment.body, savedDraftText = draft) }
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
                _uiState.update { state ->
                    state.copy(
                        comments = updateCommentInTree(state.comments, updatedComment),
                        editingCommentId = null,
                        editingOriginalText = "",
                        replyText = "",
                        savedDraftText = null
                    )
                }
            }
        }
    }

    fun deleteComment(commentId: String) {
        val comment = findCommentById(commentId) ?: return
        viewModelScope.launch {
            val result = commentRepository.deleteComment(comment)
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        comments = removeCommentFromTree(state.comments, commentId),
                        selectedCommentId = null
                    )
                }
            }
        }
    }

    private fun findCommentById(commentId: String): Comment? {
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

    fun cancelEdit() {
        _uiState.update { it.copy(editingCommentId = null, editingOriginalText = "", replyText = "", savedDraftText = null) }
    }

    fun submitReply() {
        val parentId = _uiState.value.replyingTo ?: return
        val text = _uiState.value.replyText.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            val result = commentRepository.submitComment(parentId, text)
            result.onSuccess { newComment ->
                deleteDraft(parentId)
                val newComment = newComment.copy(likes = true, score = 1)
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
