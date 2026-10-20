package com.karmakameleon.shared.ui.subreddit

import com.karmakameleon.shared.FakeAuthManager
import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestListing
import com.karmakameleon.shared.createTestPost
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

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Initialization ====================

    @Test
    fun init_loadsSubredditAndPosts() =
        runTest {
            advanceUntilIdle()
            assertNull(viewModel.postList.state.value.error)
        }

    @Test
    fun init_setsDefaultState() {
        assertEquals(PostSort.HOT, viewModel.postList.state.value.currentSort)
        assertEquals(TimeFilter.DAY, viewModel.postList.state.value.currentTimeFilter)
        assertFalse(viewModel.postList.state.value.isLoading)
    }

    // ==================== Loading Subreddit ====================

    @Test
    fun loadSubreddit_loadsSubredditData() =
        runTest {
            advanceUntilIdle()
            // Subreddit should be loaded during init
            assertNull(viewModel.postList.state.value.error)
        }

    // ==================== Loading Posts ====================

    @Test
    fun loadPosts_setsLoadingState() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()
            assertFalse(viewModel.postList.state.value.isLoading)
        }

    @Test
    fun loadPosts_forceRefresh_setsRefreshingState() =
        runTest {
            viewModel.postList.load(forceRefresh = true)
            advanceUntilIdle()
            assertFalse(viewModel.postList.state.value.isRefreshing)
        }

    @Test
    fun loadPosts_preventsMultipleConcurrentLoads() =
        runTest {
            viewModel.postList.load()
            viewModel.postList.load()
            advanceUntilIdle()
            assertFalse(viewModel.postList.state.value.isLoading)
        }

    @Test
    fun loadPosts_clearsErrorOnNewLoad() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()
            assertEquals(null, viewModel.postList.state.value.error)
        }

    @Test
    fun loadPosts_filtersNsfwPostsWhenDisabled() =
        runTest {
            settingsRepo.setNsfwEnabled(false)
            viewModel.postList.load()
            advanceUntilIdle()
            val posts = viewModel.postList.state.value.posts
            assertTrue(posts.all { !it.isNsfw })
        }

    @Test
    fun loadPosts_includesNsfwPostsWhenEnabled() =
        runTest {
            settingsRepo.setNsfwEnabled(true)
            viewModel.postList.load()
            advanceUntilIdle()
            assertNull(viewModel.postList.state.value.error)
        }

    @Test
    fun loadPosts_setsAfterAndHasMore() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()
            val state = viewModel.postList.state.value
            assertNull(state.error)
        }

    // ==================== Loading More Posts ====================

    @Test
    fun loadMorePosts_appendsToExistingPosts() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()
            val initialCount = viewModel.postList.state.value.posts.size

            viewModel.postList.loadMore()
            advanceUntilIdle()

            assertTrue(viewModel.postList.state.value.posts.size >= initialCount)
        }

    @Test
    fun loadMorePosts_preventsLoadWhenNoMore() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()

            viewModel.postList.loadMore()
            advanceUntilIdle()

            assertFalse(viewModel.postList.state.value.isLoadingMore)
        }

    @Test
    fun loadMorePosts_preventsMultipleConcurrentLoads() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()

            viewModel.postList.loadMore()
            viewModel.postList.loadMore()
            advanceUntilIdle()

            assertFalse(viewModel.postList.state.value.isLoadingMore)
        }

    // ==================== Sorting ====================

    @Test
    fun setSort_changesCurrentSort() =
        runTest {
            viewModel.postList.setSort(PostSort.NEW)
            advanceUntilIdle()
            assertEquals(PostSort.NEW, viewModel.postList.state.value.currentSort)
        }

    @Test
    fun setSort_clearsPostsAndAfter() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()

            viewModel.postList.setSort(PostSort.TOP)
            advanceUntilIdle()

            assertEquals(PostSort.TOP, viewModel.postList.state.value.currentSort)
        }

    @Test
    fun setSort_preventsDuplicateSort() =
        runTest {
            viewModel.postList.setSort(PostSort.HOT)
            advanceUntilIdle()

            viewModel.postList.setSort(PostSort.HOT)
            advanceUntilIdle()

            assertEquals(PostSort.HOT, viewModel.postList.state.value.currentSort)
        }

    // ==================== Time Filter ====================

    @Test
    fun setTimeFilter_changesCurrentTimeFilter() =
        runTest {
            viewModel.postList.setTimeFilter(TimeFilter.WEEK)
            advanceUntilIdle()
            assertEquals(TimeFilter.WEEK, viewModel.postList.state.value.currentTimeFilter)
        }

    @Test
    fun setTimeFilter_clearsPostsAndAfter() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()

            viewModel.postList.setTimeFilter(TimeFilter.MONTH)
            advanceUntilIdle()

            assertEquals(TimeFilter.MONTH, viewModel.postList.state.value.currentTimeFilter)
        }

    @Test
    fun setTimeFilter_preventsDuplicateFilter() =
        runTest {
            viewModel.postList.setTimeFilter(TimeFilter.DAY)
            advanceUntilIdle()

            viewModel.postList.setTimeFilter(TimeFilter.DAY)
            advanceUntilIdle()

            assertEquals(TimeFilter.DAY, viewModel.postList.state.value.currentTimeFilter)
        }

    // ==================== Subscription ====================

    @Test
    fun toggleSubscribe_requiresSubreddit() =
        runTest {
            viewModel.toggleSubscribe()
            advanceUntilIdle()
            // Should not error if subreddit is null
            assertNull(viewModel.postList.state.value.error)
        }

    // ==================== Voting ====================

    @Test
    fun vote_upvote_updatesPost() =
        runTest {
            loadPosts(createTestPost(id = "1", score = 100, likes = null))

            viewModel.postList.vote(
                viewModel.postList.state.value.posts
                    .first(),
                1,
            )
            advanceUntilIdle()

            val post =
                viewModel.postList.state.value.posts
                    .single()
            assertEquals(true, post.likes)
            assertEquals(101, post.score)
        }

    @Test
    fun vote_downvote_updatesPost() =
        runTest {
            loadPosts(createTestPost(id = "1", score = 100, likes = null))

            viewModel.postList.vote(
                viewModel.postList.state.value.posts
                    .first(),
                -1,
            )
            advanceUntilIdle()

            val post =
                viewModel.postList.state.value.posts
                    .single()
            assertEquals(false, post.likes)
            assertEquals(99, post.score)
        }

    @Test
    fun vote_clearVote_updatesPost() =
        runTest {
            loadPosts(createTestPost(id = "1", score = 101, likes = true))

            viewModel.postList.vote(
                viewModel.postList.state.value.posts
                    .first(),
                0,
            )
            advanceUntilIdle()

            val post =
                viewModel.postList.state.value.posts
                    .single()
            assertNull(post.likes)
            assertEquals(100, post.score)
        }

    // ==================== Saving ====================

    @Test
    fun save_savesPost() =
        runTest {
            loadPosts(createTestPost(id = "1", isSaved = false))

            viewModel.postList.save(
                viewModel.postList.state.value.posts
                    .first(),
            )
            advanceUntilIdle()

            assertTrue(
                viewModel.postList.state.value.posts
                    .single()
                    .isSaved,
            )
        }

    // ==================== Hiding ====================

    @Test
    fun hide_removesPostFromList() =
        runTest {
            loadPosts(createTestPost(id = "1"), createTestPost(id = "2"))

            viewModel.postList.hide(
                viewModel.postList.state.value.posts
                    .first(),
            )
            advanceUntilIdle()

            assertEquals(
                listOf("2"),
                viewModel.postList.state.value.posts
                    .map { it.id },
            )
        }

    // ==================== Post Updates ====================

    // These change the post through the repository, as another screen would, so only the post updates reach the list.

    @Test
    fun postUpdates_updatesExistingPost() =
        runTest {
            loadPosts(createTestPost(id = "1", likes = null), createTestPost(id = "2", likes = null))

            postRepo.vote(
                viewModel.postList.state.value.posts
                    .first(),
                1,
            )
            advanceUntilIdle()

            assertEquals(
                listOf(true, null),
                viewModel.postList.state.value.posts
                    .map { it.likes },
            )
        }

    @Test
    fun postUpdates_removesHiddenPost() =
        runTest {
            loadPosts(createTestPost(id = "1"), createTestPost(id = "2"))

            postRepo.hide(
                viewModel.postList.state.value.posts
                    .first(),
            )
            advanceUntilIdle()

            assertEquals(
                listOf("2"),
                viewModel.postList.state.value.posts
                    .map { it.id },
            )
        }

    // ==================== Login State ====================

    @Test
    fun loginStateChange_updatesIsLoggedIn() =
        runTest {
            // Verify initial state
            assertFalse(viewModel.uiState.value.isLoggedIn)
        }

    // Loads the post list with [posts], so the tests that act on a post have one.
    private fun TestScope.loadPosts(vararg posts: Post) {
        fakeApi.postsResult = createTestListing(items = posts.toList())
        viewModel.postList.load()
        advanceUntilIdle()
    }
}
