package com.karmakameleon.shared.ui.profile

import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestComment
import com.karmakameleon.shared.createTestListing
import com.karmakameleon.shared.createTestPost
import com.karmakameleon.shared.createTestUser
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.data.repository.MarkdownRepository
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.testing.FakeAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What the profile does with the posts and comments it lists: votes, saves, edits, deletes and more pages. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelContentTest {
    private val fakeAuthManager = FakeAuthManager()
    private val fakeApi = FakeRedditApi(fakeAuthManager)
    private val viewModel =
        ProfileViewModel(
            UserRepository(fakeApi, fakeAuthManager),
            PostRepository(fakeApi, MarkdownRepository()),
            CommentRepository(fakeApi),
            fakeAuthManager,
        )

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        fakeApi.meResult = createTestUser(name = "testuser")
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun comment(
        id: String,
        isSaved: Boolean = false,
    ): Comment = createTestComment(id = id, name = "t1_$id", body = "Body of $id", isSaved = isSaved)

    // Loads the account's comments and saved comments.
    private fun TestScope.loadComments(
        comments: List<Comment>,
        saved: List<Comment> = emptyList(),
    ) {
        fakeApi.userCommentsResult = createTestListing(items = comments)
        fakeApi.savedCommentsResult = createTestListing(items = saved)
        viewModel.loadOwnProfile()
        advanceUntilIdle()
        viewModel.setSelectedTab(ProfileTab.COMMENTS)
        viewModel.setSelectedTab(ProfileTab.SAVED)
        advanceUntilIdle()
    }

    private fun commentIds(): List<String> =
        viewModel.uiState.value.comments.items
            .map { it.id }

    private fun savedCommentIds(): List<String> =
        viewModel.uiState.value.savedComments.items
            .map { it.id }

    // ==================== Posts ====================

    @Test
    fun unsavingAPost_dropsItFromTheSavedList() =
        runTest {
            val post = createTestPost(id = "p1", isSaved = true)
            fakeApi.userPostsResult = createTestListing(items = listOf(post))
            fakeApi.savedPostsResult = createTestListing(items = listOf(post, createTestPost(id = "p2", isSaved = true)))
            fakeApi.upvotedPostsResult = createTestListing(items = listOf(post))
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            viewModel.setSelectedTab(ProfileTab.SAVED)
            viewModel.setSelectedTab(ProfileTab.UPVOTED)
            advanceUntilIdle()

            viewModel.save(post)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("p2"), state.savedPosts.items.map { it.id })
            assertFalse(
                state.posts.items
                    .single()
                    .isSaved,
            )
            assertFalse(
                state.upvotedPosts.items
                    .single()
                    .isSaved,
            )
        }

    @Test
    fun votingOnAPost_updatesItInEveryList() =
        runTest {
            val post = createTestPost(id = "p1", isSaved = true, score = 5)
            fakeApi.userPostsResult = createTestListing(items = listOf(post))
            fakeApi.savedPostsResult = createTestListing(items = listOf(post))
            fakeApi.downvotedPostsResult = createTestListing(items = listOf(post))
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            viewModel.setSelectedTab(ProfileTab.SAVED)
            viewModel.setSelectedTab(ProfileTab.DOWNVOTED)
            advanceUntilIdle()

            viewModel.vote(post, -1)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            listOf(state.posts, state.savedPosts, state.downvotedPosts).forEach { list ->
                assertEquals(false, list.items.single().likes)
                assertEquals(4, list.items.single().score)
            }
        }

    @Test
    fun voteAndSave_keepThePostWhenTheyFail() =
        runTest {
            val post = createTestPost(id = "p1", score = 5)
            fakeApi.userPostsResult = createTestListing(items = listOf(post))
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            fakeApi.voteResult = false
            fakeApi.saveResult = false

            viewModel.vote(post, 1)
            viewModel.save(post)
            advanceUntilIdle()

            assertEquals(
                post,
                viewModel.uiState.value.posts.items
                    .single(),
            )
        }

    // ==================== Comments ====================

    @Test
    fun saveComment_updatesItAndKeepsItInTheSavedList() =
        runTest {
            loadComments(listOf(comment("c1")))

            viewModel.saveComment(comment("c1"))
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.comments.items
                    .single()
                    .isSaved,
            )
        }

    @Test
    fun updateComment_unsavedDropsItFromTheSavedList() =
        runTest {
            loadComments(
                listOf(comment("c1", isSaved = true)),
                saved = listOf(comment("c1", isSaved = true), comment("c2", isSaved = true)),
            )

            viewModel.updateComment(comment("c1", isSaved = false))

            assertFalse(
                viewModel.uiState.value.comments.items
                    .single()
                    .isSaved,
            )
            assertEquals(listOf("c2"), savedCommentIds())
        }

    @Test
    fun updateComment_savedReplacesItInTheSavedList() =
        runTest {
            loadComments(emptyList(), saved = listOf(comment("c2", isSaved = true)))

            viewModel.updateComment(comment("c2", isSaved = true).copy(body = "Edited"))

            assertEquals(
                "Edited",
                viewModel.uiState.value.savedComments.items
                    .single()
                    .body,
            )
        }

    @Test
    fun submitEdit_updatesTheComment() =
        runTest {
            loadComments(listOf(comment("c1")), saved = listOf(comment("c3", isSaved = true)))
            viewModel.commentViewModel.startEditComment(comment("c3", isSaved = true))
            viewModel.commentViewModel.setReplyText("Edited")

            viewModel.submitEdit()
            advanceUntilIdle()

            assertEquals(listOf("t1_c3" to "Edited"), fakeApi.editedComments)
            assertEquals("Edited", viewModel.findCommentById("c3")?.body)
        }

    @Test
    fun deleteComment_removesItFromBothLists() =
        runTest {
            loadComments(listOf(comment("c1"), comment("c2")), saved = listOf(comment("c1", isSaved = true)))

            viewModel.deleteComment("c1")
            viewModel.deleteComment("gone")
            advanceUntilIdle()

            assertEquals(listOf("t1_c1"), fakeApi.deletedComments)
            assertEquals(listOf("c2"), commentIds())
            assertTrue(savedCommentIds().isEmpty())
        }

    @Test
    fun submitReply_postsIt() =
        runTest {
            fakeApi.submitCommentResult = comment("new1")
            loadComments(listOf(comment("c1")))
            viewModel.commentViewModel.setReplyingTo("t1_c1")
            viewModel.commentViewModel.setReplyText("Hello")

            viewModel.submitReply()
            advanceUntilIdle()

            assertEquals(listOf("t1_c1" to "Hello"), fakeApi.submittedComments)
            assertNull(viewModel.commentViewModel.uiState.value.replyingTo)
        }

    @Test
    fun findCommentById_looksInBothLists() =
        runTest {
            loadComments(listOf(comment("c1")), saved = listOf(comment("c2", isSaved = true)))

            assertEquals("c1", viewModel.findCommentById("c1")?.id)
            assertEquals("c2", viewModel.findCommentById("c2")?.id)
            assertNull(viewModel.findCommentById("gone"))
        }

    // ==================== More pages ====================

    @Test
    fun loadMore_keepsThePageWhenTheNextFailsToLoad() =
        runTest {
            fakeApi.userPostsResult = createTestListing(items = listOf(createTestPost(id = "p1")), after = "t3_p1")
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            fakeApi.shouldThrow = Exception("No connection")

            viewModel.loadMore(ProfileList.POSTS)
            advanceUntilIdle()

            val posts = viewModel.uiState.value.posts
            assertEquals(listOf("p1"), posts.items.map { it.id })
            assertFalse(posts.isLoadingMore)
            assertEquals("t3_p1", fakeApi.userContentRequests.last().after)
        }
}
