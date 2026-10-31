package com.karmakameleon.shared.ui.post

import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.FakeRedditApi.CommentsRequest
import com.karmakameleon.shared.createTestComment
import com.karmakameleon.shared.createTestMoreComments
import com.karmakameleon.shared.createTestPost
import com.karmakameleon.shared.createTestUser
import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.data.api.RedditApiException
import com.karmakameleon.shared.data.repository.CommentDraftRepository
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.data.repository.MarkdownRepository
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.CommentSort
import com.karmakameleon.shared.testing.FakeAuthManager
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PostDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val authManager = FakeAuthManager()
    private val fakeApi = FakeRedditApi(authManager)
    private val postRepo = PostRepository(fakeApi, MarkdownRepository(testDispatcher))
    private val settingsRepo = SettingsRepository(MapSettings())
    private val draftRepo = CommentDraftRepository(MapSettings())
    private val post = createTestPost(id = "abc123", name = "t3_abc123", title = "A title", selfText = "Some text")

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeApi.postWithCommentsResult = post to listOf(CommentOrMore.CommentItem(comment("c1")))
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun comment(
        id: String,
        parentId: String = "t3_abc123",
        body: String = "Body of $id",
    ): Comment = createTestComment(id = id, name = "t1_$id", parentId = parentId, body = body)

    private fun createViewModel(
        target: PostDetailTarget = PostDetailTarget("kotlin", "abc123"),
        userRepo: UserRepository = UserRepository(fakeApi, authManager, MarkdownRepository(Dispatchers.Main)),
    ): PostDetailViewModel =
        PostDetailViewModel(
            target,
            postRepo,
            CommentRepository(fakeApi, MarkdownRepository(Dispatchers.Main)),
            userRepo,
            draftRepo,
            settingsRepo,
        )

    private fun PostDetailViewModel.commentIds(): List<String> =
        commentViewModel.uiState.value.comments
            .filterIsInstance<CommentOrMore.CommentItem>()
            .map { it.comment.id }

    // ==================== Initial load ====================

    @Test
    fun init_loadsThePostAndItsComments() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(post, viewModel.uiState.value.post)
            assertEquals(listOf("c1"), viewModel.commentIds())
            assertFalse(viewModel.uiState.value.isLoading)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun init_showsTheCachedPostWhileLoading() =
        runTest {
            postRepo.getPostWithComments("kotlin", "abc123")

            val viewModel = createViewModel()

            assertEquals(post, viewModel.uiState.value.post)
        }

    @Test
    fun init_opensTheThreadOfTheTargetComment() =
        runTest {
            val viewModel = createViewModel(PostDetailTarget("kotlin", "abc123", commentId = "c1", context = 3))
            advanceUntilIdle()

            assertEquals(CommentsRequest(sort = null, commentId = "c1", context = 3), fakeApi.postWithCommentsRequests.single())
            assertEquals("c1", viewModel.uiState.value.focusedCommentId)
        }

    @Test
    fun init_usesThePostsSuggestedSort() =
        runTest {
            fakeApi.postWithCommentsResult = post.copy(suggestedSort = CommentSort.QA) to emptyList()

            val viewModel = createViewModel()
            advanceUntilIdle()

            // Without a sort, Reddit uses the post's suggested one.
            assertNull(fakeApi.postWithCommentsRequests.single().sort)
            assertEquals(CommentSort.QA, viewModel.uiState.value.commentSort)
            assertEquals(CommentSort.QA, viewModel.uiState.value.suggestedSort)
        }

    @Test
    fun init_withoutASuggestedSortUsesBest() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(CommentSort.CONFIDENCE, viewModel.uiState.value.commentSort)
            assertNull(viewModel.uiState.value.suggestedSort)
        }

    @Test
    fun init_ignoresTheSuggestedSortWhenTurnedOff() =
        runTest {
            settingsRepo.setUseSuggestedSort(false)
            fakeApi.postWithCommentsResult = post.copy(suggestedSort = CommentSort.QA) to emptyList()

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals(CommentSort.CONFIDENCE, fakeApi.postWithCommentsRequests.single().sort)
            assertEquals(CommentSort.CONFIDENCE, viewModel.uiState.value.commentSort)
            assertFalse(viewModel.uiState.value.useSuggestedSort)
        }

    @Test
    fun init_showsTheError() =
        runTest {
            fakeApi.shouldThrow = Exception("No connection")

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals("No connection", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun init_errorWithoutAMessage() =
        runTest {
            fakeApi.shouldThrow = Exception()

            val viewModel = createViewModel()
            advanceUntilIdle()

            assertEquals("Failed to load post", viewModel.uiState.value.error)
        }

    @Test
    fun init_loadsTheAccountOfALoggedInUser() =
        runTest {
            // Logged in, but the account failed to load then.
            val userRepo = UserRepository(fakeApi, authManager, MarkdownRepository(Dispatchers.Main))
            userRepo.handleAuthCallback("code")
            fakeApi.meResult = createTestUser(name = "me")

            val viewModel = createViewModel(userRepo = userRepo)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.isLoggedIn)
            assertEquals("me", viewModel.uiState.value.loggedInUsername)
        }

    @Test
    fun init_loggedOut() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoggedIn)
            assertNull(viewModel.uiState.value.loggedInUsername)
        }

    // ==================== Refreshing and sorting ====================

    @Test
    fun refresh_keepsTheChosenSort() =
        runTest {
            fakeApi.postWithCommentsResult = post.copy(suggestedSort = CommentSort.QA) to emptyList()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadPostWithComments(forceRefresh = true)
            advanceUntilIdle()

            assertEquals(CommentSort.QA, fakeApi.postWithCommentsRequests.last().sort)
            assertFalse(viewModel.uiState.value.isRefreshing)
        }

    @Test
    fun setCommentSort_reloadsWithTheSort() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setCommentSort(CommentSort.TOP)
            advanceUntilIdle()

            assertEquals(CommentSort.TOP, fakeApi.postWithCommentsRequests.last().sort)
            assertEquals(CommentSort.TOP, viewModel.uiState.value.commentSort)
            assertEquals(listOf("c1"), viewModel.commentIds())
        }

    @Test
    fun setCommentSort_sameSortDoesNotReload() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.setCommentSort(CommentSort.CONFIDENCE)
            advanceUntilIdle()

            assertEquals(1, fakeApi.postWithCommentsRequests.size)
        }

    @Test
    fun setUseSuggestedSort_savesTheSetting() =
        runTest {
            val viewModel = createViewModel()

            viewModel.setUseSuggestedSort(false)

            assertFalse(viewModel.uiState.value.useSuggestedSort)
            assertFalse(settingsRepo.useSuggestedSort.value)
        }

    // ==================== Threads ====================

    @Test
    fun navigateToComment_loadsItsThread() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()
            fakeApi.commentThreadResults["c9"] = post to listOf(CommentOrMore.CommentItem(comment("c9")))

            viewModel.navigateToComment("c9")
            advanceUntilIdle()

            assertEquals(CommentsRequest(CommentSort.CONFIDENCE, "c9", null), fakeApi.postWithCommentsRequests.last())
            assertEquals("c9", viewModel.uiState.value.focusedCommentId)
            assertEquals(listOf("c9"), viewModel.commentIds())
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun navigateToComment_showsTheError() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()
            fakeApi.shouldThrow = Exception()

            viewModel.navigateToComment("c9")
            advanceUntilIdle()

            assertEquals("Failed to load comment", viewModel.uiState.value.error)
            assertTrue(viewModel.commentIds().isEmpty())
        }

    @Test
    fun viewAllComments_loadsEveryComment() =
        runTest {
            val viewModel = createViewModel(PostDetailTarget("kotlin", "abc123", commentId = "c1"))
            advanceUntilIdle()

            viewModel.viewAllComments()
            advanceUntilIdle()

            assertNull(fakeApi.postWithCommentsRequests.last().commentId)
            assertNull(viewModel.uiState.value.focusedCommentId)
        }

    @Test
    fun navigateToParentRoot_loadsUpToTheTopLevelComment() =
        runTest {
            // The thread was opened at c3, two levels below the top-level comment c1.
            fakeApi.commentThreadResults["c3"] = post to listOf(CommentOrMore.CommentItem(comment("c3", parentId = "t1_c2")))
            fakeApi.commentThreadResults["c2"] = post to listOf(CommentOrMore.CommentItem(comment("c2", parentId = "t1_c1")))
            fakeApi.commentThreadResults["c1"] = post to listOf(CommentOrMore.CommentItem(comment("c1")))
            val viewModel = createViewModel(PostDetailTarget("kotlin", "abc123", commentId = "c3"))
            advanceUntilIdle()

            viewModel.navigateToParentRoot("c3")
            advanceUntilIdle()

            assertEquals(listOf("c3", "c2", "c1"), fakeApi.postWithCommentsRequests.map { it.commentId })
            assertEquals("c1", viewModel.uiState.value.focusedCommentId)
            assertEquals(listOf("c1"), viewModel.commentIds())
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun navigateToParentRoot_fallsBackToAllCommentsWhenALoadFails() =
        runTest {
            fakeApi.commentThreadResults["c3"] = post to listOf(CommentOrMore.CommentItem(comment("c3", parentId = "t1_c2")))
            fakeApi.commentThreadResults["c2"] = post to emptyList()
            val viewModel = createViewModel(PostDetailTarget("kotlin", "abc123", commentId = "c3"))
            advanceUntilIdle()

            viewModel.navigateToParentRoot("c3")
            advanceUntilIdle()

            assertEquals(listOf("c3", "c2", null), fakeApi.postWithCommentsRequests.map { it.commentId })
            assertNull(viewModel.uiState.value.focusedCommentId)
            assertEquals(listOf("c1"), viewModel.commentIds())
        }

    @Test
    fun navigateToParentRoot_doesNothingAtATopLevelComment() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.navigateToParentRoot("c1")
            viewModel.navigateToParentRoot("unknown")
            advanceUntilIdle()

            assertEquals(1, fakeApi.postWithCommentsRequests.size)
        }

    // ==================== More comments ====================

    @Test
    fun loadMoreComments_insertsWhatItLoadsAndKeepsTheRest() =
        runTest {
            val more =
                createTestMoreComments(
                    id = "m1",
                    name = "t1_m1",
                    parentId = "t3_abc123",
                    depth = 0,
                    count = 30,
                    children = List(30) { "k$it" },
                )
            fakeApi.postWithCommentsResult = post to listOf(CommentOrMore.CommentItem(comment("c1")), CommentOrMore.More(more))
            fakeApi.commentThreadResults["k0"] = post to listOf(CommentOrMore.CommentItem(comment("k0")))
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.loadMoreComments(more)
            advanceUntilIdle()

            assertEquals(listOf("c1", "k0"), viewModel.commentIds())
            val left =
                viewModel.commentViewModel.uiState.value.comments
                    .last() as CommentOrMore.More
            assertEquals(List(10) { "k${it + 20}" }, left.more.children)
            assertNull(viewModel.commentViewModel.uiState.value.loadingMoreId)
        }

    @Test
    fun loadMoreComments_skipsCommentsAlreadyLoaded() =
        runTest {
            val more = createTestMoreComments(id = "m1", name = "t1_m1", parentId = "t3_abc123", depth = 0, children = listOf("c1", "k1"))
            fakeApi.postWithCommentsResult = post to listOf(CommentOrMore.CommentItem(comment("c1")), CommentOrMore.More(more))
            val viewModel = createViewModel()
            advanceUntilIdle()
            fakeApi.postWithCommentsRequests.clear()
            fakeApi.commentThreadResults["k1"] = post to listOf(CommentOrMore.CommentItem(comment("k1")))

            viewModel.loadMoreComments(more)
            advanceUntilIdle()

            assertEquals(listOf("k1"), fakeApi.postWithCommentsRequests.map { it.commentId })
            assertEquals(listOf("c1", "k1"), viewModel.commentIds())
        }

    @Test
    fun loadMoreComments_stopsLoadingWhenItFails() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()
            fakeApi.shouldThrow = Exception("No connection")

            viewModel.loadMoreComments(createTestMoreComments(id = "m1"))
            advanceUntilIdle()

            assertNull(viewModel.commentViewModel.uiState.value.loadingMoreId)
        }

    @Test
    fun loadMoreComments_skipsAnEmptyMoreAndALoadingPost() =
        runTest {
            val viewModel = createViewModel()
            viewModel.loadMoreComments(createTestMoreComments(id = "m1"))
            advanceUntilIdle()

            viewModel.loadMoreComments(createTestMoreComments(id = "m2", children = emptyList()))
            advanceUntilIdle()

            assertEquals(listOf<String?>(null), fakeApi.postWithCommentsRequests.map { it.commentId })
        }

    // ==================== Votes and saves ====================

    @Test
    fun votePost_updatesThePost() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.votePost(1)
            advanceUntilIdle()

            assertEquals(
                true,
                viewModel.uiState.value.post
                    ?.likes,
            )
            assertEquals(
                post.score + 1,
                viewModel.uiState.value.post
                    ?.score,
            )
        }

    @Test
    fun votePost_showsTheError() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()
            fakeApi.shouldThrow = RedditApiException("THREAD_LOCKED", "you are not allowed to do that")

            viewModel.votePost(1)
            advanceUntilIdle()

            assertEquals("you are not allowed to do that", viewModel.uiState.value.actionError)
            viewModel.clearActionError()
            assertNull(viewModel.uiState.value.actionError)
        }

    @Test
    fun savePost_updatesThePost() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.savePost()
            advanceUntilIdle()

            assertEquals(
                true,
                viewModel.uiState.value.post
                    ?.isSaved,
            )
        }

    @Test
    fun savePost_keepsThePostWhenItFails() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()
            fakeApi.saveResult = false

            viewModel.savePost()
            advanceUntilIdle()

            assertEquals(
                false,
                viewModel.uiState.value.post
                    ?.isSaved,
            )
        }

    @Test
    fun voteAndSave_doNothingBeforeThePostLoads() =
        runTest {
            fakeApi.shouldThrow = Exception("No connection")
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.votePost(1)
            viewModel.savePost()
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.post)
            assertNull(viewModel.uiState.value.actionError)
        }

    // ==================== Replies ====================

    @Test
    fun getReplyParentText() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals("", viewModel.getReplyParentText())

            viewModel.commentViewModel.setReplyingTo("t3_abc123")
            assertEquals("Some text", viewModel.getReplyParentText())

            viewModel.commentViewModel.setReplyingTo("t1_c1")
            assertEquals("Body of c1", viewModel.getReplyParentText())
        }

    @Test
    fun getReplyParentText_linkPostUsesTheTitle() =
        runTest {
            fakeApi.postWithCommentsResult = post.copy(selfText = " ") to emptyList()
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.commentViewModel.setReplyingTo("t3_abc123")

            assertEquals("A title", viewModel.getReplyParentText())
        }

    @Test
    fun insertQuote_quotesTheParent() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.commentViewModel.setReplyingTo("t1_c1")
            viewModel.commentViewModel.setReplyText("I agree")

            viewModel.insertQuote()

            assertEquals(">Body of c1\n\nI agree", viewModel.commentViewModel.uiState.value.replyText)
        }

    @Test
    fun submitReply_toThePostAddsItAtTheTop() =
        runTest {
            fakeApi.submitCommentResult = comment("new1", body = "Hello")
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.commentViewModel.setReplyingTo("t3_abc123")
            viewModel.commentViewModel.setReplyText("Hello")

            viewModel.submitReply()
            advanceUntilIdle()

            assertEquals(listOf("t3_abc123" to "Hello"), fakeApi.submittedComments)
            assertEquals(listOf("new1", "c1"), viewModel.commentIds())
            assertNull(viewModel.uiState.value.actionError)
        }

    @Test
    fun submitReply_showsTheError() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.commentViewModel.setReplyingTo("t1_c1")
            viewModel.commentViewModel.setReplyText("Hello")
            fakeApi.shouldThrow = RedditApiException("RATELIMIT", "Take a break")

            viewModel.submitReply()
            advanceUntilIdle()

            assertEquals("Take a break", viewModel.uiState.value.actionError)
            assertEquals("Hello", viewModel.commentViewModel.uiState.value.replyText)
        }

    @Test
    fun submitReply_withoutAReplyDoesNothing() =
        runTest {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.submitReply()
            advanceUntilIdle()

            assertTrue(fakeApi.submittedComments.isEmpty())
        }
}
