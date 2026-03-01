package com.karmakameleon.shared.ui.comment

import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestComment
import com.karmakameleon.shared.createTestMoreComments
import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.data.repository.CommentDraftRepository
import com.karmakameleon.shared.data.repository.CommentRepository
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CommentViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeApi: FakeRedditApi
    private lateinit var commentRepo: CommentRepository
    private lateinit var draftRepo: CommentDraftRepository
    private lateinit var viewModel: CommentViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeApi = FakeRedditApi()
        commentRepo = CommentRepository(fakeApi)
        draftRepo = CommentDraftRepository(MapSettings())
        viewModel = CommentViewModel(commentRepo, draftRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== setComments / clearComments ====================

    @Test
    fun setComments_updatesState() {
        val comment = createTestComment(id = "c1")
        viewModel.setComments(listOf(CommentOrMore.CommentItem(comment)))
        assertEquals(1, viewModel.uiState.value.comments.size)
    }

    @Test
    fun clearComments_clearsState() {
        val comment = createTestComment(id = "c1")
        viewModel.setComments(listOf(CommentOrMore.CommentItem(comment)))
        viewModel.clearComments()
        assertTrue(viewModel.uiState.value.comments.isEmpty())
        assertNull(viewModel.uiState.value.selectedCommentId)
        assertTrue(viewModel.uiState.value.hiddenCommentIds.isEmpty())
    }

    // ==================== Selection & Hiding ====================

    @Test
    fun selectComment_setsSelectedId() {
        viewModel.selectComment("c1")
        assertEquals("c1", viewModel.uiState.value.selectedCommentId)
    }

    @Test
    fun selectComment_null_deselects() {
        viewModel.selectComment("c1")
        viewModel.selectComment(null)
        assertNull(viewModel.uiState.value.selectedCommentId)
    }

    @Test
    fun selectComment_unhidesHiddenComment() {
        viewModel.hideComment("c1")
        assertTrue(viewModel.uiState.value.hiddenCommentIds.contains("c1"))
        viewModel.selectComment("c1")
        assertFalse(viewModel.uiState.value.hiddenCommentIds.contains("c1"))
        assertEquals("c1", viewModel.uiState.value.selectedCommentId)
    }

    @Test
    fun hideComment_addsToHiddenSet() {
        viewModel.hideComment("c1")
        assertTrue(viewModel.uiState.value.hiddenCommentIds.contains("c1"))
    }

    @Test
    fun hideComment_deselectsComment() {
        viewModel.selectComment("c1")
        viewModel.hideComment("c1")
        assertNull(viewModel.uiState.value.selectedCommentId)
    }

    @Test
    fun setLastTouchedComment_updatesState() {
        viewModel.setLastTouchedComment("t1_c1")
        assertEquals("t1_c1", viewModel.uiState.value.lastTouchedCommentName)
    }

    // ==================== Flattening ====================

    @Test
    fun getFlattenedComments_emptyList() {
        viewModel.setComments(emptyList())
        assertTrue(viewModel.getFlattenedComments().isEmpty())
    }

    @Test
    fun getFlattenedComments_flattensSingleComment() {
        val comment = createTestComment(id = "c1")
        viewModel.setComments(listOf(CommentOrMore.CommentItem(comment)))
        val flat = viewModel.getFlattenedComments()
        assertEquals(1, flat.size)
        assertTrue(flat[0] is FlatCommentItem.CommentEntry)
    }

    @Test
    fun getFlattenedComments_flattensNestedReplies() {
        val child = createTestComment(id = "c2", parentId = "t1_c1", depth = 1)
        val parent = createTestComment(id = "c1", replies = listOf(child))
        viewModel.setComments(listOf(CommentOrMore.CommentItem(parent)))
        val flat = viewModel.getFlattenedComments()
        assertEquals(2, flat.size)
    }

    @Test
    fun getFlattenedComments_excludesChildrenOfHiddenComments() {
        val child = createTestComment(id = "c2", parentId = "t1_c1", depth = 1)
        val parent = createTestComment(id = "c1", replies = listOf(child))
        viewModel.setComments(listOf(CommentOrMore.CommentItem(parent)))
        viewModel.hideComment("c1")
        val flat = viewModel.getFlattenedComments()
        assertEquals(1, flat.size) // Only parent, child is hidden
    }

    @Test
    fun getFlattenedComments_includesMoreEntries() {
        val more = createTestMoreComments(id = "more1", parentId = "t1_c1")
        val comment = createTestComment(id = "c1", moreReplies = more)
        viewModel.setComments(listOf(CommentOrMore.CommentItem(comment)))
        val flat = viewModel.getFlattenedComments()
        assertEquals(2, flat.size)
        assertTrue(flat[1] is FlatCommentItem.MoreEntry)
    }

    @Test
    fun getFlattenedComments_excludesMoreEntriesOfHiddenComments() {
        val more = createTestMoreComments(id = "more1", parentId = "t1_c1")
        val comment = createTestComment(id = "c1", moreReplies = more)
        viewModel.setComments(listOf(CommentOrMore.CommentItem(comment)))
        viewModel.hideComment("c1")
        val flat = viewModel.getFlattenedComments()
        assertEquals(1, flat.size) // Only parent
    }

    @Test
    fun getFlattenedComments_topLevelMoreEntry() {
        val more = createTestMoreComments(id = "more1", parentId = "t3_post")
        viewModel.setComments(listOf(CommentOrMore.More(more)))
        val flat = viewModel.getFlattenedComments()
        assertEquals(1, flat.size)
        assertTrue(flat[0] is FlatCommentItem.MoreEntry)
    }

    // ==================== Navigation ====================

    @Test
    fun findRootCommentId_rootComment() {
        val comment = createTestComment(id = "c1", depth = 0)
        viewModel.setComments(listOf(CommentOrMore.CommentItem(comment)))
        assertEquals("c1", viewModel.findRootCommentId("c1"))
    }

    @Test
    fun findRootCommentId_childComment() {
        val child = createTestComment(id = "c2", name = "t1_c2", parentId = "t1_c1", depth = 1)
        val parent = createTestComment(id = "c1", name = "t1_c1", depth = 0, replies = listOf(child))
        viewModel.setComments(listOf(CommentOrMore.CommentItem(parent)))
        assertEquals("c1", viewModel.findRootCommentId("c2"))
    }

    @Test
    fun findRootCommentId_nonexistentComment() {
        assertNull(viewModel.findRootCommentId("nonexistent"))
    }

    @Test
    fun findParentCommentId_rootComment() {
        val comment = createTestComment(id = "c1", depth = 0)
        viewModel.setComments(listOf(CommentOrMore.CommentItem(comment)))
        assertNull(viewModel.findParentCommentId("c1"))
    }

    @Test
    fun findParentCommentId_childComment() {
        val child = createTestComment(id = "c2", name = "t1_c2", parentId = "t1_c1", depth = 1)
        val parent = createTestComment(id = "c1", name = "t1_c1", depth = 0, replies = listOf(child))
        viewModel.setComments(listOf(CommentOrMore.CommentItem(parent)))
        assertEquals("c1", viewModel.findParentCommentId("c2"))
    }

    @Test
    fun findPrevRootCommentId_firstRoot() {
        val c1 = createTestComment(id = "c1", depth = 0)
        viewModel.setComments(listOf(CommentOrMore.CommentItem(c1)))
        assertNull(viewModel.findPrevRootCommentId("c1"))
    }

    @Test
    fun findPrevRootCommentId_secondRoot() {
        val c1 = createTestComment(id = "c1", depth = 0)
        val c2 = createTestComment(id = "c2", depth = 0)
        viewModel.setComments(listOf(
            CommentOrMore.CommentItem(c1),
            CommentOrMore.CommentItem(c2)
        ))
        assertEquals("c1", viewModel.findPrevRootCommentId("c2"))
    }

    @Test
    fun findNextRootCommentId_lastRoot() {
        val c1 = createTestComment(id = "c1", depth = 0)
        viewModel.setComments(listOf(CommentOrMore.CommentItem(c1)))
        assertNull(viewModel.findNextRootCommentId("c1"))
    }

    @Test
    fun findNextRootCommentId_firstRoot() {
        val c1 = createTestComment(id = "c1", depth = 0)
        val c2 = createTestComment(id = "c2", depth = 0)
        viewModel.setComments(listOf(
            CommentOrMore.CommentItem(c1),
            CommentOrMore.CommentItem(c2)
        ))
        assertEquals("c2", viewModel.findNextRootCommentId("c1"))
    }

    // ==================== Tree Building ====================

    @Test
    fun buildCommentTree_flatListToTree() {
        val parent = createTestComment(id = "c1", name = "t1_c1", parentId = "t3_post", depth = 0)
        val child = createTestComment(id = "c2", name = "t1_c2", parentId = "t1_c1", depth = 1)
        val items = listOf(
            CommentOrMore.CommentItem(parent),
            CommentOrMore.CommentItem(child)
        )
        val tree = viewModel.buildCommentTree(items)
        assertEquals(1, tree.size)
        val rootComment = (tree[0] as CommentOrMore.CommentItem).comment
        assertEquals(1, rootComment.replies.size)
        assertEquals("c2", rootComment.replies[0].id)
    }

    @Test
    fun buildCommentTree_withMoreComments() {
        val parent = createTestComment(id = "c1", name = "t1_c1", parentId = "t3_post", depth = 0)
        val more = createTestMoreComments(id = "more1", parentId = "t1_c1")
        val items = listOf(
            CommentOrMore.CommentItem(parent),
            CommentOrMore.More(more)
        )
        val tree = viewModel.buildCommentTree(items)
        assertEquals(1, tree.size)
        val rootComment = (tree[0] as CommentOrMore.CommentItem).comment
        assertEquals("more1", rootComment.moreReplies?.id)
    }

    // ==================== Tree Modifications ====================

    @Test
    fun insertMoreComments_replacesMoreEntry() {
        val more = createTestMoreComments(id = "more1", parentId = "t3_post")
        viewModel.setComments(listOf(CommentOrMore.More(more)))

        val newComment = createTestComment(id = "c1", parentId = "t3_post", depth = 0)
        viewModel.insertMoreComments(more, listOf(CommentOrMore.CommentItem(newComment)))

        val comments = viewModel.uiState.value.comments
        assertEquals(1, comments.size)
        assertTrue(comments[0] is CommentOrMore.CommentItem)
    }

    @Test
    fun updateComment_updatesInTree() {
        val comment = createTestComment(id = "c1", body = "original")
        viewModel.setComments(listOf(CommentOrMore.CommentItem(comment)))

        val updated = comment.copy(body = "updated")
        viewModel.updateComment(updated)

        val result = (viewModel.uiState.value.comments[0] as CommentOrMore.CommentItem).comment
        assertEquals("updated", result.body)
    }

    @Test
    fun removeComment_removesFromTree() {
        val c1 = createTestComment(id = "c1")
        val c2 = createTestComment(id = "c2")
        viewModel.setComments(listOf(
            CommentOrMore.CommentItem(c1),
            CommentOrMore.CommentItem(c2)
        ))

        viewModel.removeComment("c1")
        assertEquals(1, viewModel.uiState.value.comments.size)
        assertEquals("c2", (viewModel.uiState.value.comments[0] as CommentOrMore.CommentItem).comment.id)
    }

    @Test
    fun removeComment_removesNestedComment() {
        val child = createTestComment(id = "c2", parentId = "t1_c1", depth = 1)
        val parent = createTestComment(id = "c1", replies = listOf(child))
        viewModel.setComments(listOf(CommentOrMore.CommentItem(parent)))

        viewModel.removeComment("c2")
        val result = (viewModel.uiState.value.comments[0] as CommentOrMore.CommentItem).comment
        assertTrue(result.replies.isEmpty())
    }

    @Test
    fun addReplyToTree_addsToPost() {
        val existing = createTestComment(id = "c1")
        viewModel.setComments(listOf(CommentOrMore.CommentItem(existing)))

        val newComment = createTestComment(id = "new1")
        viewModel.addReplyToTree("t3_post", newComment, isReplyToPost = true)

        assertEquals(2, viewModel.uiState.value.comments.size)
        // New comment should be first
        assertEquals("new1", (viewModel.uiState.value.comments[0] as CommentOrMore.CommentItem).comment.id)
    }

    @Test
    fun addReplyToTree_addsToComment() {
        val parent = createTestComment(id = "c1", name = "t1_c1", depth = 0)
        viewModel.setComments(listOf(CommentOrMore.CommentItem(parent)))

        val newComment = createTestComment(id = "new1")
        viewModel.addReplyToTree("t1_c1", newComment, isReplyToPost = false)

        val result = (viewModel.uiState.value.comments[0] as CommentOrMore.CommentItem).comment
        assertEquals(1, result.replies.size)
        assertEquals("new1", result.replies[0].id)
        assertEquals(1, result.replies[0].depth) // depth adjusted
    }

    // ==================== Finding Comments ====================

    @Test
    fun findCommentById_findsRootComment() {
        val comment = createTestComment(id = "c1")
        viewModel.setComments(listOf(CommentOrMore.CommentItem(comment)))
        assertEquals("c1", viewModel.findCommentById("c1")?.id)
    }

    @Test
    fun findCommentById_findsNestedComment() {
        val child = createTestComment(id = "c2", depth = 1)
        val parent = createTestComment(id = "c1", replies = listOf(child))
        viewModel.setComments(listOf(CommentOrMore.CommentItem(parent)))
        assertEquals("c2", viewModel.findCommentById("c2")?.id)
    }

    @Test
    fun findCommentById_returnsNullForMissing() {
        assertNull(viewModel.findCommentById("nonexistent"))
    }

    @Test
    fun findCommentByName_findsComment() {
        val comment = createTestComment(id = "c1", name = "t1_c1")
        viewModel.setComments(listOf(CommentOrMore.CommentItem(comment)))
        assertEquals("c1", viewModel.findCommentByName("t1_c1")?.id)
    }

    @Test
    fun findCommentByName_findsNestedComment() {
        val child = createTestComment(id = "c2", name = "t1_c2", depth = 1)
        val parent = createTestComment(id = "c1", name = "t1_c1", replies = listOf(child))
        viewModel.setComments(listOf(CommentOrMore.CommentItem(parent)))
        assertEquals("c2", viewModel.findCommentByName("t1_c2")?.id)
    }

    @Test
    fun findCommentByName_returnsNullForMissing() {
        assertNull(viewModel.findCommentByName("t1_nonexistent"))
    }

    // ==================== Loading More ====================

    @Test
    fun setLoadingMore_updatesState() {
        viewModel.setLoadingMore("more1")
        assertEquals("more1", viewModel.uiState.value.loadingMoreId)
    }

    @Test
    fun setLoadingMore_null_clearsState() {
        viewModel.setLoadingMore("more1")
        viewModel.setLoadingMore(null)
        assertNull(viewModel.uiState.value.loadingMoreId)
    }

    // ==================== Reply/Edit State ====================

    @Test
    fun setReplyingTo_setsState() {
        viewModel.setReplyingTo("t1_c1")
        assertEquals("t1_c1", viewModel.uiState.value.replyingTo)
        assertNull(viewModel.uiState.value.editingCommentId)
    }

    @Test
    fun setReplyingTo_null_clearsState() {
        viewModel.setReplyingTo("t1_c1")
        viewModel.setReplyingTo(null)
        assertNull(viewModel.uiState.value.replyingTo)
    }

    @Test
    fun setReplyText_updatesState() {
        viewModel.setReplyText("Hello world")
        assertEquals("Hello world", viewModel.uiState.value.replyText)
    }

    @Test
    fun startEditComment_setsEditState() {
        val comment = createTestComment(id = "c1", body = "original text")
        viewModel.startEditComment(comment)
        assertEquals("c1", viewModel.uiState.value.editingCommentId)
        assertEquals("original text", viewModel.uiState.value.editingOriginalText)
        assertEquals("original text", viewModel.uiState.value.replyText)
        assertNull(viewModel.uiState.value.replyingTo)
    }

    @Test
    fun cancelEdit_clearsEditState() {
        val comment = createTestComment(id = "c1", body = "text")
        viewModel.startEditComment(comment)
        viewModel.cancelEdit()
        assertNull(viewModel.uiState.value.editingCommentId)
        assertEquals("", viewModel.uiState.value.editingOriginalText)
        assertEquals("", viewModel.uiState.value.replyText)
    }

    @Test
    fun startReplyWithQuote_formatsQuotedText() {
        viewModel.startReplyWithQuote("t1_c1", "Line 1\nLine 2")
        assertEquals("t1_c1", viewModel.uiState.value.replyingTo)
        assertTrue(viewModel.uiState.value.replyText.startsWith(">Line 1\n>Line 2"))
    }

    @Test
    fun insertQuotedText_prependsToExistingText() {
        viewModel.setReplyText("existing text")
        viewModel.insertQuotedText("quoted line")
        assertTrue(viewModel.uiState.value.replyText.startsWith(">quoted line"))
        assertTrue(viewModel.uiState.value.replyText.contains("existing text"))
    }

    @Test
    fun insertQuotedText_ignoresBlankText() {
        viewModel.setReplyText("existing")
        viewModel.insertQuotedText("  ")
        assertEquals("existing", viewModel.uiState.value.replyText)
    }

    @Test
    fun insertQuote_formatsParentText() {
        viewModel.setReplyText("existing")
        viewModel.insertQuote("parent text")
        assertTrue(viewModel.uiState.value.replyText.startsWith(">parent text"))
    }

    @Test
    fun insertQuote_ignoresBlankText() {
        viewModel.setReplyText("existing")
        viewModel.insertQuote("")
        assertEquals("existing", viewModel.uiState.value.replyText)
    }

    // ==================== Draft ====================

    @Test
    fun saveDraft_savesDraftToRepository() {
        viewModel.setReplyingTo("t1_c1")
        viewModel.setReplyText("draft content")
        viewModel.saveDraft()
        assertEquals("draft content", draftRepo.loadDraft("t1_c1"))
    }

    @Test
    fun saveDraft_doesNothingWhenNoParent() {
        viewModel.setReplyText("draft content")
        viewModel.saveDraft() // No replyingTo or editingCommentId set
    }

    @Test
    fun saveDraft_doesNothingWhenBlank() {
        viewModel.setReplyingTo("t1_c1")
        viewModel.setReplyText("")
        viewModel.saveDraft()
        assertNull(draftRepo.loadDraft("t1_c1"))
    }

    @Test
    fun applyDraft_setsReplyText() {
        viewModel.applyDraft("saved draft")
        assertEquals("saved draft", viewModel.uiState.value.replyText)
    }

    @Test
    fun setReplyingTo_loadsDraft() {
        draftRepo.saveDraft("t1_c1", "saved draft")
        viewModel.setReplyingTo("t1_c1")
        assertEquals("saved draft", viewModel.uiState.value.savedDraftText)
    }
}
