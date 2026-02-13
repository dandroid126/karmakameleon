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
    val replyingTo: String? = null,
    val replyText: String = "",
    val selectedCommentId: String? = null,
    val hiddenCommentIds: Set<String> = emptySet()
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
