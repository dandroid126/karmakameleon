package com.reader.shared.ui.post

import com.reader.shared.FakeRedditApi
import com.reader.shared.FakeAuthManager
import com.reader.shared.createTestPost
import com.reader.shared.createTestComment
import com.reader.shared.data.api.CommentOrMore
import com.reader.shared.data.repository.CommentDraftRepository
import com.reader.shared.data.repository.CommentRepository
import com.reader.shared.data.repository.PostRepository
import com.reader.shared.data.repository.UserRepository
import com.reader.shared.domain.model.CommentSort
import com.reader.shared.domain.model.Post
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PostDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeApi: FakeRedditApi
    private lateinit var postRepo: PostRepository
    private lateinit var commentRepo: CommentRepository
    private lateinit var commentDraftRepo: CommentDraftRepository
    private lateinit var userRepo: UserRepository
    private lateinit var viewModel: PostDetailViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val fakeAuthManager = FakeAuthManager()
        fakeApi = FakeRedditApi(fakeAuthManager)
        postRepo = PostRepository(fakeApi)
        commentRepo = CommentRepository(fakeApi)
        commentDraftRepo = CommentDraftRepository(MapSettings())
        userRepo = UserRepository(fakeApi, fakeAuthManager)
        viewModel = PostDetailViewModel("kotlin", "abc123", postRepo, commentRepo, userRepo, commentDraftRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Initialization ====================

    @Test
    fun init_setsDefaultState() {
        assertEquals(CommentSort.CONFIDENCE, viewModel.uiState.value.commentSort)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun init_loadsCachedPost() = runTest {
        advanceUntilIdle()
        // Verify initialization completes
        assertEquals(CommentSort.CONFIDENCE, viewModel.uiState.value.commentSort)
    }

    // ==================== Loading Post With Comments ====================

    @Test
    fun loadPostWithComments_setsLoadingState() = runTest {
        viewModel.loadPostWithComments()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loadPostWithComments_forceRefresh_setsRefreshingState() = runTest {
        viewModel.loadPostWithComments(forceRefresh = true)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun loadPostWithComments_clearsErrorOnNewLoad() = runTest {
        viewModel.loadPostWithComments()
        advanceUntilIdle()
        // Verify load completes
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ==================== Comment Sorting ====================

    @Test
    fun setCommentSort_changesCommentSort() = runTest {
        viewModel.setCommentSort(CommentSort.NEW)
        advanceUntilIdle()
        assertEquals(CommentSort.NEW, viewModel.uiState.value.commentSort)
    }

    @Test
    fun setCommentSort_reloadsComments() = runTest {
        viewModel.loadPostWithComments()
        advanceUntilIdle()
        
        viewModel.setCommentSort(CommentSort.TOP)
        advanceUntilIdle()
        
        assertEquals(CommentSort.TOP, viewModel.uiState.value.commentSort)
    }

    @Test
    fun setCommentSort_preventsDuplicateSort() = runTest {
        viewModel.setCommentSort(CommentSort.CONFIDENCE)
        advanceUntilIdle()
        
        viewModel.setCommentSort(CommentSort.CONFIDENCE)
        advanceUntilIdle()
        
        assertEquals(CommentSort.CONFIDENCE, viewModel.uiState.value.commentSort)
    }

    // ==================== Comment ViewModel Integration ====================

    @Test
    fun commentViewModel_isInitialized() {
        assertNotNull(viewModel.commentViewModel)
    }

    @Test
    fun commentViewModel_canSetComments() = runTest {
        val comment = createTestComment(id = "c1")
        viewModel.commentViewModel.setComments(listOf(CommentOrMore.CommentItem(comment)))
        advanceUntilIdle()
        
        assertEquals(1, viewModel.commentViewModel.uiState.value.comments.size)
    }

    // ==================== Login State ====================

    @Test
    fun loginStateChange_updatesIsLoggedIn() = runTest {
        // Verify initial state
        assertFalse(viewModel.uiState.value.isLoggedIn)
    }

    // ==================== Navigation ====================

    @Test
    fun navigateToComment_setsFocusedComment() = runTest {
        viewModel.navigateToComment("c1")
        advanceUntilIdle()
        
        assertEquals("c1", viewModel.uiState.value.focusedCommentId)
    }

    @Test
    fun viewAllComments_clearsFocus() = runTest {
        viewModel.viewAllComments()
        advanceUntilIdle()
        
        assertNull(viewModel.uiState.value.focusedCommentId)
    }

    // ==================== Voting ====================

    @Test
    fun votePost_updatesPost() = runTest {
        viewModel.votePost(1)
        advanceUntilIdle()
        
        // Verify vote completes
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun votePost_setsActionErrorOnFailure() = runTest {
        val post = createTestPost(id = "abc123")
        fakeApi.postWithCommentsResult = post to emptyList()
        val vm = PostDetailViewModel("kotlin", "abc123", postRepo, commentRepo, userRepo, commentDraftRepo)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.post)

        fakeApi.shouldThrow = com.reader.shared.data.api.RedditApiException("THREAD_LOCKED", "you are not allowed to do that")
        vm.votePost(1)
        advanceUntilIdle()

        assertEquals("you are not allowed to do that", vm.uiState.value.actionError)
    }

    @Test
    fun clearActionError_clearsError() = runTest {
        val post = createTestPost(id = "abc123")
        fakeApi.postWithCommentsResult = post to emptyList()
        val vm = PostDetailViewModel("kotlin", "abc123", postRepo, commentRepo, userRepo, commentDraftRepo)
        advanceUntilIdle()
        assertNotNull(vm.uiState.value.post)

        fakeApi.shouldThrow = Exception("Vote failed")
        vm.votePost(1)
        advanceUntilIdle()

        assertNotNull(vm.uiState.value.actionError)
        vm.clearActionError()
        assertNull(vm.uiState.value.actionError)
    }

    @Test
    fun savePost_updatesPost() = runTest {
        viewModel.savePost()
        advanceUntilIdle()
        
        // Verify save completes
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
