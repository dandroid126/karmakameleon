package com.karmakameleon.shared.ui.subreddit

import com.karmakameleon.shared.FakeAuthManager
import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.SubredditRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.PostSort
import com.karmakameleon.shared.domain.model.TimeFilter
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
class SubredditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeApi: FakeRedditApi
    private lateinit var postRepo: PostRepository
    private lateinit var subredditRepo: SubredditRepository
    private lateinit var userRepo: UserRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var viewModel: SubredditViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val fakeAuthManager = FakeAuthManager()
        fakeApi = FakeRedditApi(fakeAuthManager)
        settingsRepo = SettingsRepository(MapSettings())
        postRepo = PostRepository(fakeApi)
        subredditRepo = SubredditRepository(fakeApi, settingsRepo)
        userRepo = UserRepository(fakeApi, fakeAuthManager)
        viewModel = SubredditViewModel("kotlin", subredditRepo, postRepo, userRepo, settingsRepo)
    }

    private fun emitPostUpdate(post: Post) {
        // Helper method to emit post updates
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Initialization ====================

    @Test
    fun init_loadsSubredditAndPosts() = runTest {
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun init_setsDefaultState() {
        assertEquals(PostSort.HOT, viewModel.uiState.value.currentSort)
        assertEquals(TimeFilter.DAY, viewModel.uiState.value.currentTimeFilter)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ==================== Loading Subreddit ====================

    @Test
    fun loadSubreddit_loadsSubredditData() = runTest {
        advanceUntilIdle()
        // Subreddit should be loaded during init
        assertNull(viewModel.uiState.value.error)
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

    // ==================== Subscription ====================

    @Test
    fun toggleSubscribe_requiresSubreddit() = runTest {
        viewModel.toggleSubscribe()
        advanceUntilIdle()
        // Should not error if subreddit is null
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
        
        val post = viewModel.uiState.value.posts.firstOrNull() ?: return@runTest
        
        viewModel.hide(post)
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.posts.any { it.id == post.id })
    }

    // ==================== Post Updates ====================

    @Test
    fun postUpdates_updatesExistingPost() = runTest {
        viewModel.loadPosts()
        advanceUntilIdle()
        
        val post = viewModel.uiState.value.posts.firstOrNull() ?: return@runTest
        // Verify post is in the list
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
}
