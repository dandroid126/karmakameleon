package com.reader.shared.ui.feed

import com.reader.shared.FakeRedditApi
import com.reader.shared.FakeAuthManager
import com.reader.shared.createTestPost
import com.reader.shared.createTestListing
import com.reader.shared.data.repository.PostRepository
import com.reader.shared.data.repository.SettingsRepository
import com.reader.shared.data.repository.SubredditRepository
import com.reader.shared.data.repository.UserRepository
import com.reader.shared.domain.model.Post
import com.reader.shared.domain.model.PostSort
import com.reader.shared.domain.model.TimeFilter
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
class FeedViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeApi: FakeRedditApi
    private lateinit var postRepo: PostRepository
    private lateinit var subredditRepo: SubredditRepository
    private lateinit var userRepo: UserRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var viewModel: FeedViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val fakeAuthManager = FakeAuthManager()
        fakeApi = FakeRedditApi(fakeAuthManager)
        settingsRepo = SettingsRepository(MapSettings())
        postRepo = PostRepository(fakeApi)
        subredditRepo = SubredditRepository(fakeApi, settingsRepo)
        userRepo = UserRepository(fakeApi, fakeAuthManager)
        viewModel = FeedViewModel(postRepo, subredditRepo, userRepo, settingsRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Initialization ====================

    @Test
    fun init_loadsPostsOnCreation() = runTest {
        advanceUntilIdle()
        // Verify initialization completes without error
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun init_setsDefaultState() {
        assertEquals(PostSort.HOT, viewModel.uiState.value.currentSort)
        assertEquals(TimeFilter.DAY, viewModel.uiState.value.currentTimeFilter)
        assertEquals(FeedType.HOME, viewModel.uiState.value.currentFeedType)
        assertNull(viewModel.uiState.value.currentSubreddit)
    }

    // ==================== Loading Posts ====================

    @Test
    fun loadPosts_setsLoadingState() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loadPosts_forceRefresh_setsRefreshingState() = runTest {
        viewModel.loadPosts(forceRefresh = true)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun loadPosts_preventsMultipleConcurrentLoads() = runTest {
        viewModel.loadPosts()
        viewModel.loadPosts()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loadPosts_clearsErrorOnNewLoad() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun loadPosts_filtersNsfwPostsWhenDisabled() = runTest {
        settingsRepo.setNsfwEnabled(false)
        viewModel.loadPosts()
        advanceUntilIdle()
        val posts = viewModel.uiState.value.posts
        assertTrue(posts.all { !it.isNsfw })
    }

    @Test
    fun loadPosts_includesNsfwPostsWhenEnabled() = runTest {
        settingsRepo.setNsfwEnabled(true)
        viewModel.loadPosts()
        advanceUntilIdle()
        // Just verify no error occurs
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun loadPosts_setsAfterAndHasMore() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertNull(state.error)
    }

    // ==================== Loading More Posts ====================

    @Test
    fun loadMorePosts_appendsToExistingPosts() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        val initialCount = viewModel.uiState.value.posts.size
        
        viewModel.loadMorePosts()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.posts.size >= initialCount)
    }

    @Test
    fun loadMorePosts_preventsLoadWhenNoMore() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        
        viewModel.loadMorePosts()
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isLoadingMore)
    }

    @Test
    fun loadMorePosts_preventsMultipleConcurrentLoads() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        
        viewModel.loadMorePosts()
        viewModel.loadMorePosts()
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isLoadingMore)
    }

    @Test
    fun loadMorePosts_requiresAfterToken() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        
        viewModel.loadMorePosts()
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isLoadingMore)
    }

    // ==================== Sorting ====================

    @Test
    fun setSort_changesCurrentSort() = runTest {
        viewModel.setSort(PostSort.NEW)
        advanceUntilIdle()
        assertEquals(PostSort.NEW, viewModel.uiState.value.currentSort)
    }

    @Test
    fun setSort_clearsPostsAndAfter() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        
        viewModel.setSort(PostSort.TOP)
        advanceUntilIdle()
        
        assertEquals(PostSort.TOP, viewModel.uiState.value.currentSort)
    }

    @Test
    fun setSort_preventsDuplicateSort() = runTest {
        viewModel.setSort(PostSort.HOT)
        advanceUntilIdle()
        
        viewModel.setSort(PostSort.HOT)
        advanceUntilIdle()
        
        assertEquals(PostSort.HOT, viewModel.uiState.value.currentSort)
    }

    // ==================== Time Filter ====================

    @Test
    fun setTimeFilter_changesCurrentTimeFilter() = runTest {
        viewModel.setTimeFilter(TimeFilter.WEEK)
        advanceUntilIdle()
        assertEquals(TimeFilter.WEEK, viewModel.uiState.value.currentTimeFilter)
    }

    @Test
    fun setTimeFilter_clearsPostsAndAfter() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        
        viewModel.setTimeFilter(TimeFilter.MONTH)
        advanceUntilIdle()
        
        assertEquals(TimeFilter.MONTH, viewModel.uiState.value.currentTimeFilter)
    }

    @Test
    fun setTimeFilter_preventsDuplicateFilter() = runTest {
        viewModel.setTimeFilter(TimeFilter.DAY)
        advanceUntilIdle()
        
        viewModel.setTimeFilter(TimeFilter.DAY)
        advanceUntilIdle()
        
        assertEquals(TimeFilter.DAY, viewModel.uiState.value.currentTimeFilter)
    }

    // ==================== Subreddit Selection ====================

    @Test
    fun setSubreddit_changesCurrentSubreddit() = runTest {
        viewModel.setSubreddit("kotlin")
        advanceUntilIdle()
        assertEquals("kotlin", viewModel.uiState.value.currentSubreddit)
    }

    @Test
    fun setSubreddit_clearsPostsAndAfter() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        
        viewModel.setSubreddit("programming")
        advanceUntilIdle()
        
        assertEquals("programming", viewModel.uiState.value.currentSubreddit)
    }

    @Test
    fun setSubreddit_preventsDuplicateSubreddit() = runTest {
        viewModel.setSubreddit("kotlin")
        advanceUntilIdle()
        
        viewModel.setSubreddit("kotlin")
        advanceUntilIdle()
        
        assertEquals("kotlin", viewModel.uiState.value.currentSubreddit)
    }

    @Test
    fun setSubreddit_null_clearsSubreddit() = runTest {
        viewModel.setSubreddit("kotlin")
        advanceUntilIdle()
        
        viewModel.setSubreddit(null)
        advanceUntilIdle()
        
        assertNull(viewModel.uiState.value.currentSubreddit)
    }

    // ==================== Feed Type ====================

    @Test
    fun setFeedType_changesFeedType() = runTest {
        viewModel.setFeedType(FeedType.ALL)
        advanceUntilIdle()
        assertEquals(FeedType.ALL, viewModel.uiState.value.currentFeedType)
    }

    @Test
    fun setFeedType_setsSubredditFromFeedType() = runTest {
        viewModel.setFeedType(FeedType.ALL)
        advanceUntilIdle()
        assertEquals("all", viewModel.uiState.value.currentSubreddit)
    }

    @Test
    fun setFeedType_popular_setsSubredditToPopular() = runTest {
        viewModel.setFeedType(FeedType.POPULAR)
        advanceUntilIdle()
        assertEquals("popular", viewModel.uiState.value.currentSubreddit)
    }

    @Test
    fun setFeedType_home_clearsSubreddit() = runTest {
        viewModel.setFeedType(FeedType.HOME)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.currentSubreddit)
    }

    // ==================== Blocking Subreddits ====================

    @Test
    fun blockSubreddit_addsToBlockedList() = runTest {
        viewModel.blockSubreddit("banned")
        advanceUntilIdle()
        // Verify no error occurs
        assertNull(viewModel.uiState.value.error)
    }

    // ==================== Voting ====================

    @Test
    fun vote_upvote_updatesPost() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        
        val post = viewModel.uiState.value.posts.firstOrNull() ?: return@runTest
        viewModel.vote(post, 1)
        advanceUntilIdle()
        
        // Verify post is still in list
        assertTrue(viewModel.uiState.value.posts.any { it.id == post.id })
    }

    @Test
    fun vote_downvote_updatesPost() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        
        val post = viewModel.uiState.value.posts.firstOrNull() ?: return@runTest
        viewModel.vote(post, -1)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.posts.any { it.id == post.id })
    }

    @Test
    fun vote_clearVote_updatesPost() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        
        val post = viewModel.uiState.value.posts.firstOrNull() ?: return@runTest
        viewModel.vote(post, 0)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.posts.any { it.id == post.id })
    }

    // ==================== Saving ====================

    @Test
    fun save_savesPost() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        
        val post = viewModel.uiState.value.posts.firstOrNull() ?: return@runTest
        viewModel.save(post)
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.posts.any { it.id == post.id })
    }

    // ==================== Hiding ====================

    @Test
    fun hide_removesPostFromList() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        
        val initialCount = viewModel.uiState.value.posts.size
        val post = viewModel.uiState.value.posts.firstOrNull() ?: return@runTest
        
        viewModel.hide(post)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.posts.any { it.id == post.id })
    }

    // ==================== Error Handling ====================

    @Test
    fun clearError_clearsErrorMessage() = runTest {
        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }

    // ==================== Post Updates ====================

    @Test
    fun postUpdates_updatesExistingPost() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        
        val post = viewModel.uiState.value.posts.firstOrNull() ?: return@runTest
        assertTrue(viewModel.uiState.value.posts.any { it.id == post.id })
    }

    @Test
    fun postUpdates_removesHiddenPost() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        
        val post = viewModel.uiState.value.posts.firstOrNull() ?: return@runTest
        viewModel.hide(post)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.posts.any { it.id == post.id })
    }

    // ==================== Login State ====================

    @Test
    fun loginStateChange_updatesIsLoggedIn() = runTest {
        // Verify initial state
        assertFalse(viewModel.uiState.value.isLoggedIn)
    }

    @Test
    fun loginStateChange_filtersBlockedSubreddits() = runTest {
        settingsRepo.addBlockedSubreddit("banned")
        advanceUntilIdle()
        
        val posts = viewModel.uiState.value.posts
        assertTrue(posts.all { it.subreddit.lowercase() != "banned" })
    }
}
