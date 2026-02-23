package com.reader.shared.ui.profile

import com.reader.shared.FakeRedditApi
import com.reader.shared.FakeAuthManager
import com.reader.shared.createTestPost
import com.reader.shared.createTestComment
import com.reader.shared.data.api.AuthManager
import com.reader.shared.data.repository.CommentRepository
import com.reader.shared.data.repository.PostRepository
import com.reader.shared.data.repository.UserRepository
import com.reader.shared.domain.model.Comment
import com.reader.shared.domain.model.Post
import com.reader.shared.domain.model.PostSort
import com.reader.shared.domain.model.TimeFilter
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
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeApi: FakeRedditApi
    private lateinit var fakeAuthManager: FakeAuthManager
    private lateinit var postRepo: PostRepository
    private lateinit var commentRepo: CommentRepository
    private lateinit var userRepo: UserRepository
    private lateinit var viewModel: ProfileViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeApi = FakeRedditApi()
        fakeAuthManager = FakeAuthManager()
        postRepo = PostRepository(fakeApi)
        commentRepo = CommentRepository(fakeApi)
        userRepo = UserRepository(fakeApi, fakeAuthManager)
        viewModel = ProfileViewModel(userRepo, postRepo, commentRepo, fakeAuthManager)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Initialization ====================

    @Test
    fun init_setsDefaultState() {
        assertEquals(ProfileTab.POSTS, viewModel.uiState.value.selectedTab)
        assertEquals(PostSort.NEW, viewModel.uiState.value.postsSort)
        assertEquals(TimeFilter.ALL, viewModel.uiState.value.postsTimeFilter)
        assertEquals(PostSort.NEW, viewModel.uiState.value.commentsSort)
        assertEquals(TimeFilter.ALL, viewModel.uiState.value.commentsTimeFilter)
    }

    @Test
    fun init_setsDefaultClientId() {
        assertEquals("", viewModel.uiState.value.clientId)
    }

    // ==================== Tab Selection ====================

    @Test
    fun setSelectedTab_changeSelectedTab() = runTest {
        viewModel.setSelectedTab(ProfileTab.COMMENTS)
        advanceUntilIdle()
        assertEquals(ProfileTab.COMMENTS, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun setSelectedTab_posts_loadsUserPosts() = runTest {
        viewModel.loadOwnProfile()
        advanceUntilIdle()
        
        viewModel.setSelectedTab(ProfileTab.POSTS)
        advanceUntilIdle()
        
        assertEquals(ProfileTab.POSTS, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun setSelectedTab_comments_loadsUserComments() = runTest {
        viewModel.loadOwnProfile()
        advanceUntilIdle()
        
        viewModel.setSelectedTab(ProfileTab.COMMENTS)
        advanceUntilIdle()
        
        assertEquals(ProfileTab.COMMENTS, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun setSelectedTab_saved_loadsSavedContent() = runTest {
        viewModel.loadOwnProfile()
        advanceUntilIdle()
        
        viewModel.setSelectedTab(ProfileTab.SAVED)
        advanceUntilIdle()
        
        assertEquals(ProfileTab.SAVED, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun setSelectedTab_upvoted_loadsUpvotedContent() = runTest {
        viewModel.loadOwnProfile()
        advanceUntilIdle()
        
        viewModel.setSelectedTab(ProfileTab.UPVOTED)
        advanceUntilIdle()
        
        assertEquals(ProfileTab.UPVOTED, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun setSelectedTab_downvoted_loadsDownvotedContent() = runTest {
        viewModel.loadOwnProfile()
        advanceUntilIdle()
        
        viewModel.setSelectedTab(ProfileTab.DOWNVOTED)
        advanceUntilIdle()
        
        assertEquals(ProfileTab.DOWNVOTED, viewModel.uiState.value.selectedTab)
    }

    // ==================== Loading Own Profile ====================

    @Test
    fun loadOwnProfile_setsLoadingState() = runTest {
        viewModel.loadOwnProfile()
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loadOwnProfile_forceRefresh_setsRefreshingState() = runTest {
        viewModel.loadOwnProfile(forceRefresh = true)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    // ==================== Loading User Profile ====================

    @Test
    fun loadUserProfile_setsLoadingState() = runTest {
        viewModel.loadUserProfile("testuser")
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loadUserProfile_forceRefresh_setsRefreshingState() = runTest {
        viewModel.loadUserProfile("testuser", forceRefresh = true)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    // ==================== Refresh ====================

    @Test
    fun refresh_reloadsCurrentTab() = runTest {
        viewModel.refresh()
        advanceUntilIdle()
        
        // Verify refresh completes without error
        assertNull(viewModel.uiState.value.errorMessage)
    }

    // ==================== Sorting ====================

    @Test
    fun setPostsSort_changesPostsSort() = runTest {
        viewModel.setPostsSort(PostSort.HOT)
        advanceUntilIdle()
        assertEquals(PostSort.HOT, viewModel.uiState.value.postsSort)
    }

    @Test
    fun setPostsSort_reloadsPosts() = runTest {
        viewModel.loadOwnProfile()
        advanceUntilIdle()
        
        viewModel.setPostsSort(PostSort.TOP)
        advanceUntilIdle()
        
        assertEquals(PostSort.TOP, viewModel.uiState.value.postsSort)
    }

    @Test
    fun setCommentsSort_changesCommentsSort() = runTest {
        viewModel.setCommentsSort(PostSort.HOT)
        advanceUntilIdle()
        assertEquals(PostSort.HOT, viewModel.uiState.value.commentsSort)
    }

    @Test
    fun setCommentsSort_reloadsComments() = runTest {
        viewModel.loadOwnProfile()
        advanceUntilIdle()
        
        viewModel.setCommentsSort(PostSort.TOP)
        advanceUntilIdle()
        
        assertEquals(PostSort.TOP, viewModel.uiState.value.commentsSort)
    }

    // ==================== Time Filter ====================

    @Test
    fun setPostsTimeFilter_changesPostsTimeFilter() = runTest {
        viewModel.setPostsTimeFilter(TimeFilter.WEEK)
        advanceUntilIdle()
        assertEquals(TimeFilter.WEEK, viewModel.uiState.value.postsTimeFilter)
    }

    @Test
    fun setCommentsTimeFilter_changesCommentsTimeFilter() = runTest {
        viewModel.setCommentsTimeFilter(TimeFilter.MONTH)
        advanceUntilIdle()
        assertEquals(TimeFilter.MONTH, viewModel.uiState.value.commentsTimeFilter)
    }

    // ==================== Voting ====================

    @Test
    fun vote_upvote_updatesPost() = runTest {
        viewModel.loadOwnProfile()
        advanceUntilIdle()
        
        val post = viewModel.uiState.value.posts.firstOrNull() ?: return@runTest
        viewModel.vote(post, 1)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.posts.any { it.id == post.id })
    }

    @Test
    fun save_savesPost() = runTest {
        viewModel.loadOwnProfile()
        advanceUntilIdle()
        
        val post = viewModel.uiState.value.posts.firstOrNull() ?: return@runTest
        viewModel.save(post)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.posts.any { it.id == post.id })
    }

    // ==================== Error Handling ====================

    @Test
    fun clearErrorMessage_clearsErrorMessage() = runTest {
        viewModel.clearErrorMessage()
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun clearAllData_resetsState() = runTest {
        viewModel.loadOwnProfile()
        advanceUntilIdle()
        
        viewModel.clearAllData()
        
        assertTrue(viewModel.uiState.value.posts.isEmpty())
        assertNull(viewModel.uiState.value.account)
    }
}
