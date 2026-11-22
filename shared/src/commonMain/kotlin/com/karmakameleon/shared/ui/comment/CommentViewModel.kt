package com.karmakameleon.shared.ui.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.data.media.ImageFile
import com.karmakameleon.shared.data.repository.CommentDraftRepository
import com.karmakameleon.shared.data.repository.CommentImageRepository
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.MoreComments
import com.karmakameleon.shared.domain.model.Subreddit
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
    val replyImages: List<ReplyImage> = emptyList(),
    // The subreddit replies go to, which says what media its comments allow. Null while it isn't known.
    val commentSubreddit: Subreddit? = null,
    // Something to tell the user about an image they tried to add, like the subreddit not allowing it.
    val imageMessage: String? = null,
    // Whether images can be uploaded from this screen at all.
    val supportsImages: Boolean = false,
) {
    /**
     * Whether to show the button that adds an image: when replying, not editing, once it's known what the subreddit
     * allows. Where it allows no uploads, the button looks disabled; see [canAddImages].
     */
    val showsImageButton: Boolean
        get() = supportsImages && replyingTo != null && editingCommentId == null && commentSubreddit != null

    /** Whether the image button adds an image: where the subreddit allows some kind of upload. */
    val canAddImages: Boolean
        get() =
            showsImageButton &&
                commentSubreddit?.let { it.allowsCommentImage("image/jpeg") || it.allowsCommentImage("image/gif") } == true
}

class CommentViewModel(
    private val commentRepository: CommentRepository,
    private val commentDraftRepository: CommentDraftRepository? = null,
    private val commentImageRepository: CommentImageRepository? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CommentUiState(supportsImages = commentImageRepository != null))

    private var nextImageNumber = 1
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
        _uiState.update { state ->
            state.copy(
                comments = state.comments.withMoreComments(more, newComments.withoutDuplicates(state.comments)),
                loadingMoreId = null,
            )
        }
    }

    /** [more] without the comments an earlier thread brought along, as replies or in its own "more" entries. */
    fun withoutLoadedChildren(more: MoreComments): MoreComments {
        val covered = _uiState.value.comments.commentIdsCoveredOutside(more)
        return more.copy(children = more.children.filterNot { it in covered })
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
        _uiState.update {
            it.copy(replyingTo = parentId, editingCommentId = null, replyText = "", savedDraftText = draft, replyImages = emptyList())
        }
    }

    fun setReplyText(text: String) {
        _uiState.update { it.copy(replyText = text) }
    }

    fun startEditComment(comment: Comment) {
        val draft = commentDraftRepository?.loadDraft(comment.id)
        _uiState.update {
            it.copy(
                editingCommentId = comment.id,
                editingOriginalText = comment.sourceBody,
                replyingTo = null,
                replyText = comment.sourceBody,
                savedDraftText = draft,
                // Images can't be added to an edit, so the reply's are dropped.
                replyImages = emptyList(),
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
        _uiState.update {
            it.copy(
                replyingTo = parentId,
                editingCommentId = null,
                replyText = formatted,
                savedDraftText = draft,
                replyImages = emptyList(),
            )
        }
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

    // Images aren't kept in drafts, so a draft's tokens for images that are gone are taken out.
    fun applyDraft(draft: String) {
        _uiState.update { state -> state.copy(replyText = draft.keepingImageTokens(state.replyImages.map { it.key }.toSet())) }
    }

    private fun deleteDraft(parentId: String) {
        commentDraftRepository?.deleteDraft(parentId)
    }

    // ==================== CRUD Operations (Tree-based) ====================

    fun saveComment(comment: Comment) = saveCommentWithCallback(comment, ::updateComment)

    suspend fun submitReplyWithError(isReplyToPost: Boolean): String? {
        val (parentId, result) = submitReply() ?: return null
        return result.fold(
            onSuccess = { newComment ->
                addReplyToTree(parentId, newComment, isReplyToPost)
                null
            },
            onFailure = { error -> error.message },
        )
    }

    fun submitEdit() = submitEditWithCallback(::findCommentById, ::updateComment)

    fun deleteComment(commentId: String) {
        val comment = findCommentById(commentId) ?: return
        deleteCommentWithCallback(comment) { removeComment(commentId) }
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
        viewModelScope.launch {
            submitReply()?.second?.onSuccess(onSuccess)
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

    // Posts the reply being written and clears it once it's posted. Returns the parent's ID with the new comment, which
    // starts out upvoted with a score of 1 like Reddit shows it, or null when there's no reply to post.
    private suspend fun submitReply(): Pair<String, Result<Comment>>? {
        val parentId = _uiState.value.replyingTo ?: return null
        // A token with no image behind it, like a typed one, isn't posted as text.
        val replyImages = _uiState.value.replyImages
        val text =
            _uiState.value.replyText
                .keepingImageTokens(replyImages.map { it.key }.toSet())
                .trim()
        if (text.isEmpty()) return null

        // Only the images still in the text are sent; ones the user deleted the token of are dropped.
        val keys = text.imageKeys()
        val images = replyImages.filter { it.key in keys }
        val notReady = images.firstOrNull { it.status !is ReplyImageStatus.Uploaded }
        if (notReady != null) {
            val message =
                if (notReady.status is ReplyImageStatus.Failed) {
                    "An image didn't upload. Retry or remove it."
                } else {
                    "Wait for the images to upload"
                }
            return parentId to Result.failure(IllegalStateException(message))
        }
        val assetIds = images.associate { it.key to (it.status as ReplyImageStatus.Uploaded).assetId }

        val result =
            commentRepository
                .submitComment(parentId, text.withAssetIds(assetIds), assetIds.values.toSet())
                .map { it.copy(likes = true, score = 1) }
        if (result.isSuccess) {
            deleteDraft(parentId)
            _uiState.update { it.copy(replyingTo = null, replyText = "", savedDraftText = null, replyImages = emptyList()) }
        }
        return parentId to result
    }

    // ==================== Images ====================

    fun setCommentSubreddit(subreddit: Subreddit?) {
        _uiState.update { it.copy(commentSubreddit = subreddit) }
    }

    /** Tells the user why the image button is disabled. */
    fun explainImagesDisabled() {
        val subreddit = _uiState.value.commentSubreddit ?: return
        _uiState.update { it.copy(imageMessage = "Inline images are disabled in ${subreddit.displayNamePrefixed}") }
    }

    fun clearImageMessage() {
        _uiState.update { it.copy(imageMessage = null) }
    }

    /**
     * Adds an image the user picked, or the keyboard or clipboard sent, and returns the text to put at the cursor: the
     * image's token, or, where the image can't go, a link to it when [linkUrl] gives one. Returns null when nothing should
     * be inserted, after setting [CommentUiState.imageMessage] to say why. [mimeType] lets an image the subreddit doesn't
     * allow be turned away before it's read; without it, that happens once it's read.
     */
    fun attachImage(
        location: String,
        mimeType: String? = null,
        linkUrl: String? = null,
    ): String? {
        val refusal = imageRefusal(mimeType)
        if (refusal != null) return refuseImage(refusal, mimeType, linkUrl)
        val repository = commentImageRepository ?: return null
        return startImage { repository.read(location) }
    }

    /** Adds the image at a web address, downloading it first, and returns its token to put at the cursor. */
    fun attachImageFromUrl(url: String): String? {
        val repository = commentImageRepository ?: return null
        if (imageRefusal(null) != null) return null
        return startImage { repository.download(url) }
    }

    /** Uploads an image again after its upload failed. */
    fun retryImage(key: String) {
        val image =
            _uiState.value.replyImages
                .find { it.key == key }
                ?.image ?: return
        updateImage(key) { it.copy(status = ReplyImageStatus.Uploading) }
        viewModelScope.launch { upload(key, image) }
    }

    /** Removes an image and its token from the text. */
    fun removeImage(key: String) {
        _uiState.update { state ->
            state.copy(replyImages = state.replyImages.filterNot { it.key == key }, replyText = state.replyText.replacingImageToken(key))
        }
    }

    // Why an image of this type can't be added, if it can't. A type that isn't known yet is checked once it's read.
    private fun imageRefusal(mimeType: String?): String? {
        val state = _uiState.value
        val subreddit = state.commentSubreddit
        val kind = if (isGif(mimeType)) "GIFs" else "images"
        return when {
            state.editingCommentId != null -> {
                "Images can't be added while editing a comment"
            }

            !state.supportsImages || state.replyingTo == null -> {
                "Images can't be added here"
            }

            subreddit != null && mimeType != null && !subreddit.allowsCommentImage(mimeType) -> {
                "${subreddit.displayNamePrefixed} doesn't allow $kind in comments"
            }

            else -> {
                null
            }
        }
    }

    private fun refuseImage(
        refusal: String,
        mimeType: String?,
        linkUrl: String?,
    ): String? {
        if (linkUrl == null) {
            _uiState.update { it.copy(imageMessage = refusal) }
            return null
        }
        _uiState.update { it.copy(imageMessage = "$refusal, so it was added as a link") }
        return imageLink(mimeType, linkUrl)
    }

    // Reads an image, then uploads it if the subreddit allows its type, and returns the token for it.
    private fun startImage(read: suspend () -> Result<ImageFile>): String {
        // Skips the keys of tokens already in the text, like ones a draft brought in.
        val textKeys = _uiState.value.replyText.imageKeys()
        var key: String
        do {
            key = "img-${nextImageNumber++}"
        } while (key in textKeys)
        _uiState.update { it.copy(replyImages = it.replyImages + ReplyImage(key, ReplyImageStatus.Reading)) }
        viewModelScope.launch {
            read().fold(
                onSuccess = { image ->
                    val refusal = imageRefusal(image.mimeType)
                    if (refusal != null) {
                        removeImage(key)
                        _uiState.update { it.copy(imageMessage = refusal) }
                    } else {
                        updateImage(key) { it.copy(image = image, status = ReplyImageStatus.Uploading) }
                        upload(key, image)
                    }
                },
                onFailure = { error -> failImage(key, error.message ?: "Couldn't read the image") },
            )
        }
        return imageToken(key)
    }

    private suspend fun upload(
        key: String,
        image: ImageFile,
    ) {
        val result = commentImageRepository?.upload(image) ?: return
        result.fold(
            onSuccess = { assetId -> updateImage(key) { it.copy(status = ReplyImageStatus.Uploaded(assetId)) } },
            onFailure = { error -> failImage(key, error.message ?: "Couldn't upload the image") },
        )
    }

    // Marks an image as failed, and tells the user why, since its thumbnail has no room for that.
    private fun failImage(
        key: String,
        message: String,
    ) {
        if (_uiState.value.replyImages.none { it.key == key }) return
        updateImage(key) { it.copy(status = ReplyImageStatus.Failed(message)) }
        _uiState.update { it.copy(imageMessage = message) }
    }

    // Changes an image that's still in the reply. One removed meanwhile stays removed.
    private fun updateImage(
        key: String,
        change: (ReplyImage) -> ReplyImage,
    ) {
        _uiState.update { state -> state.copy(replyImages = state.replyImages.map { if (it.key == key) change(it) else it }) }
    }
}
