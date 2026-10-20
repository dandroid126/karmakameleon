package com.karmakameleon.shared.ui.comment

import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestComment
import com.karmakameleon.shared.createTestMoreComments
import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.data.repository.CommentDraftRepository
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.domain.model.Comment
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Writing, editing, saving and deleting comments, and the drafts kept while writing. */
@OptIn(ExperimentalCoroutinesApi::class)
class CommentViewModelEditingTest {
    private val fakeApi = FakeRedditApi()
    private val draftRepo = CommentDraftRepository(MapSettings())
    private val viewModel = CommentViewModel(CommentRepository(fakeApi), draftRepo)
    private val more = createTestMoreComments(id = "m1", name = "t1_m1", parentId = "t3_abc123", depth = 0)

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        // c1 has the reply c2; a "more" entry follows them.
        val c2 = comment("c2", parentId = "t1_c1", depth = 1)
        viewModel.setComments(listOf(CommentOrMore.CommentItem(comment("c1").copy(replies = listOf(c2))), CommentOrMore.More(more)))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun comment(
        id: String,
        parentId: String = "t3_abc123",
        depth: Int = 0,
    ): Comment = createTestComment(id = id, name = "t1_$id", parentId = parentId, depth = depth, body = "Body of $id")

    private fun commentIds(): List<String> =
        viewModel.getFlattenedComments().filterIsInstance<FlatCommentItem.CommentEntry>().map { it.comment.id }

    // ==================== Replies ====================

    @Test
    fun submitReplyWithError_addsTheReplyUnderItsParent() =
        runTest {
            fakeApi.submitCommentResult = comment("new1", parentId = "t1_c1")
            draftRepo.saveDraft("t1_c1", "Draft")
            viewModel.setReplyingTo("t1_c1")
            viewModel.setReplyText("  Hello  ")

            val error = viewModel.submitReplyWithError(isReplyToPost = false)

            assertNull(error)
            assertEquals(listOf("t1_c1" to "Hello"), fakeApi.submittedComments)
            assertEquals(listOf("c1", "new1", "c2"), commentIds())
            // A new comment starts out upvoted with a score of 1, like Reddit shows it.
            val reply = viewModel.findCommentById("new1")!!
            assertEquals(true, reply.likes)
            assertEquals(1, reply.score)
            assertEquals(1, reply.depth)
            val state = viewModel.uiState.value
            assertNull(state.replyingTo)
            assertEquals("", state.replyText)
            assertNull(draftRepo.loadDraft("t1_c1"))
        }

    @Test
    fun submitReplyWithError_returnsTheErrorAndKeepsTheText() =
        runTest {
            viewModel.setReplyingTo("t1_c1")
            viewModel.setReplyText("Hello")

            val error = viewModel.submitReplyWithError(isReplyToPost = false)

            assertEquals("Failed to submit comment", error)
            assertEquals("Hello", viewModel.uiState.value.replyText)
            assertEquals("t1_c1", viewModel.uiState.value.replyingTo)
        }

    @Test
    fun submitReplyWithError_skipsABlankReply() =
        runTest {
            viewModel.setReplyingTo("t1_c1")
            viewModel.setReplyText("   ")

            assertNull(viewModel.submitReplyWithError(isReplyToPost = false))
            // Nothing is being replied to.
            viewModel.setReplyingTo(null)
            assertNull(viewModel.submitReplyWithError(isReplyToPost = false))
            assertTrue(fakeApi.submittedComments.isEmpty())
        }

    @Test
    fun submitReplyWithCallback_passesOnTheNewComment() =
        runTest {
            fakeApi.submitCommentResult = comment("new1", parentId = "t1_c1")
            viewModel.setReplyingTo("t1_c1")
            viewModel.setReplyText("Hello")
            val posted = mutableListOf<Comment>()

            viewModel.submitReplyWithCallback { posted += it }
            advanceUntilIdle()

            assertEquals(listOf("new1"), posted.map { it.id })
            // The callback is for flat lists, so the tree isn't changed.
            assertEquals(listOf("c1", "c2"), commentIds())
        }

    // ==================== Edits ====================

    @Test
    fun submitEdit_updatesTheComment() =
        runTest {
            viewModel.startEditComment(comment("c2", parentId = "t1_c1", depth = 1))
            viewModel.setReplyText(" Edited ")
            viewModel.saveDraft()

            viewModel.submitEdit()
            advanceUntilIdle()

            assertEquals(listOf("t1_c2" to "Edited"), fakeApi.editedComments)
            assertEquals("Edited", viewModel.findCommentById("c2")?.body)
            assertNull(viewModel.uiState.value.editingCommentId)
            assertEquals("", viewModel.uiState.value.replyText)
            assertNull(draftRepo.loadDraft("c2"))
        }

    @Test
    fun submitEdit_keepsEditingWhenItFails() =
        runTest {
            fakeApi.editCommentResult = false
            viewModel.startEditComment(comment("c1"))
            viewModel.setReplyText("Edited")

            viewModel.submitEdit()
            advanceUntilIdle()

            assertEquals("Body of c1", viewModel.findCommentById("c1")?.body)
            assertEquals("c1", viewModel.uiState.value.editingCommentId)
        }

    @Test
    fun submitEdit_skipsABlankEditOrAMissingComment() =
        runTest {
            viewModel.submitEdit()
            viewModel.startEditComment(comment("c1"))
            viewModel.setReplyText(" ")
            viewModel.submitEdit()
            viewModel.startEditComment(comment("gone"))
            viewModel.setReplyText("Edited")
            viewModel.submitEdit()
            advanceUntilIdle()

            assertTrue(fakeApi.editedComments.isEmpty())
        }

    // ==================== Drafts ====================

    @Test
    fun startingAReplyOrEdit_offersTheSavedDraft() {
        draftRepo.saveDraft("t1_c1", "Reply draft")
        draftRepo.saveDraft("c2", "Edit draft")

        viewModel.setReplyingTo("t1_c1")
        assertEquals("Reply draft", viewModel.uiState.value.savedDraftText)

        viewModel.startReplyWithQuote("t1_c1", "Quoted")
        assertEquals("Reply draft", viewModel.uiState.value.savedDraftText)
        assertEquals(">Quoted\n\n", viewModel.uiState.value.replyText)

        viewModel.startEditComment(comment("c2"))
        assertEquals("Edit draft", viewModel.uiState.value.savedDraftText)
    }

    @Test
    fun saveDraft_savesWhatIsWritten() {
        viewModel.setReplyingTo("t1_c1")
        viewModel.setReplyText("Half a thought")

        viewModel.saveDraft()

        assertEquals("Half a thought", draftRepo.loadDraft("t1_c1"))
        assertEquals("Half a thought", viewModel.uiState.value.savedDraftText)
    }

    // ==================== Saves and deletes ====================

    @Test
    fun saveComment_togglesItInTheTree() =
        runTest {
            viewModel.saveComment(comment("c2", parentId = "t1_c1", depth = 1))
            advanceUntilIdle()

            assertEquals(true, viewModel.findCommentById("c2")?.isSaved)
            // The more entry after the thread is kept.
            assertEquals(
                CommentOrMore.More(more),
                viewModel.uiState.value.comments
                    .last(),
            )
        }

    @Test
    fun saveComment_keepsItWhenItFails() =
        runTest {
            fakeApi.saveResult = false

            viewModel.saveComment(comment("c1"))
            advanceUntilIdle()

            assertEquals(false, viewModel.findCommentById("c1")?.isSaved)
        }

    @Test
    fun deleteComment_removesItFromTheTree() =
        runTest {
            viewModel.deleteComment("c2")
            advanceUntilIdle()

            assertEquals(listOf("t1_c2"), fakeApi.deletedComments)
            assertEquals(listOf("c1"), commentIds())
            assertEquals(
                CommentOrMore.More(more),
                viewModel.uiState.value.comments
                    .last(),
            )
        }

    @Test
    fun deleteComment_keepsItWhenItFails() =
        runTest {
            fakeApi.deleteCommentResult = false

            viewModel.deleteComment("c1")
            viewModel.deleteComment("gone")
            advanceUntilIdle()

            assertEquals(listOf("t1_c1"), fakeApi.deletedComments)
            assertEquals(listOf("c1", "c2"), commentIds())
        }

    // ==================== More replies ====================

    @Test
    fun insertMoreComments_addsTheRepliesOfAComment() {
        val moreReplies = createTestMoreComments(id = "m2", name = "t1_m2", parentId = "t1_c1", depth = 1, children = listOf("c3"))
        viewModel.setComments(listOf(CommentOrMore.CommentItem(comment("c1").copy(moreReplies = moreReplies))))

        viewModel.insertMoreComments(moreReplies, listOf(CommentOrMore.CommentItem(comment("c3", parentId = "t1_c1", depth = 1))))

        assertEquals(listOf("c1", "c3"), commentIds())
        assertNull(viewModel.findCommentById("c1")?.moreReplies)
    }

    // ==================== Navigation ====================

    @Test
    fun findParentAndRoot() {
        assertEquals("c1", viewModel.findParentCommentId("c2"))
        assertNull(viewModel.findParentCommentId("c1"))
        assertEquals("c1", viewModel.findRootCommentId("c2"))
        assertNull(viewModel.findNextRootCommentId("gone"))
    }

    @Test
    fun findRootCommentId_stopsAtTheTopOfWhatIsLoaded() {
        // A thread opened partway down: c5's parent isn't loaded.
        viewModel.setComments(listOf(CommentOrMore.CommentItem(comment("c5", parentId = "t1_c4", depth = 2))))

        assertEquals("c5", viewModel.findRootCommentId("c5"))
        assertNull(viewModel.findParentCommentId("c5"))
    }
}
