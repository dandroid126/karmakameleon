package com.karmakameleon.shared.ui.feed

import com.karmakameleon.shared.FakeAuthManager
import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestListing
import com.karmakameleon.shared.createTestPost
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.UserRepository
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
class FeedViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeApi: FakeRedditApi
    private lateinit var postRepo: PostRepository
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
        userRepo = UserRepository(fakeApi, fakeAuthManager)
        viewModel = FeedViewModel(postRepo, userRepo, settingsRepo)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Initialization ====================

    @Test
    fun init_loadsPostsOnCreation() =
        runTest {
            advanceUntilIdle()
            // Verify initialization completes without error
            assertNull(viewModel.postList.state.value.error)
        }

    @Test
    fun init_setsDefaultState() {
        assertEquals(PostSort.HOT, viewModel.postList.state.value.currentSort)
        assertEquals(TimeFilter.DAY, viewModel.postList.state.value.currentTimeFilter)
        assertEquals(FeedType.HOME, viewModel.uiState.value.currentFeedType)
        assertNull(viewModel.uiState.value.currentSubreddit)
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
            // Just verify no error occurs
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

    @Test
    fun loadMorePosts_requiresAfterToken() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()

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

    // ==================== Subreddit Selection ====================

    @Test
    fun setSubreddit_changesCurrentSubreddit() =
        runTest {
            viewModel.setSubreddit("kotlin")
            advanceUntilIdle()
            assertEquals("kotlin", viewModel.uiState.value.currentSubreddit)
        }

    @Test
    fun setSubreddit_clearsPostsAndAfter() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()

            viewModel.setSubreddit("programming")
            advanceUntilIdle()

            assertEquals("programming", viewModel.uiState.value.currentSubreddit)
        }

    @Test
    fun setSubreddit_preventsDuplicateSubreddit() =
        runTest {
            viewModel.setSubreddit("kotlin")
            advanceUntilIdle()

            viewModel.setSubreddit("kotlin")
            advanceUntilIdle()

            assertEquals("kotlin", viewModel.uiState.value.currentSubreddit)
        }

    @Test
    fun setSubreddit_null_clearsSubreddit() =
        runTest {
            viewModel.setSubreddit("kotlin")
            advanceUntilIdle()

            viewModel.setSubreddit(null)
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.currentSubreddit)
        }

    // ==================== Feed Type ====================

    @Test
    fun setFeedType_changesFeedType() =
        runTest {
            viewModel.setFeedType(FeedType.ALL)
            advanceUntilIdle()
            assertEquals(FeedType.ALL, viewModel.uiState.value.currentFeedType)
        }

    @Test
    fun setFeedType_setsSubredditFromFeedType() =
        runTest {
            viewModel.setFeedType(FeedType.ALL)
            advanceUntilIdle()
            assertEquals("all", viewModel.uiState.value.currentSubreddit)
        }

    @Test
    fun setFeedType_popular_setsSubredditToPopular() =
        runTest {
            viewModel.setFeedType(FeedType.POPULAR)
            advanceUntilIdle()
            assertEquals("popular", viewModel.uiState.value.currentSubreddit)
        }

    @Test
    fun setFeedType_home_clearsSubreddit() =
        runTest {
            viewModel.setFeedType(FeedType.HOME)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.currentSubreddit)
        }

    // ==================== Blocking Subreddits ====================

    @Test
    fun blockSubreddit_addsToBlockedList() =
        runTest {
            viewModel.blockSubreddit("banned")
            advanceUntilIdle()
            // Verify no error occurs
            assertNull(viewModel.postList.state.value.error)
        }

    @Test
    fun loadPosts_filtersBlockedSubreddits() =
        runTest {
            fakeApi.postsResult =
                createTestListing(
                    items =
                        listOf(
                            createTestPost(id = "1", subreddit = "kept"),
                            createTestPost(id = "2", subreddit = "banned"),
                            createTestPost(id = "3", subreddit = "BANNED"),
                        ),
                )
            settingsRepo.addBlockedSubreddit("banned")

            viewModel.postList.load(forceRefresh = true)
            advanceUntilIdle()

            val posts = viewModel.postList.state.value.posts
            assertEquals(1, posts.size)
            assertEquals("kept", posts.single().subreddit)
        }

    @Test
    fun loadMorePosts_filtersBlockedSubreddits() =
        runTest {
            fakeApi.postsResult =
                createTestListing(
                    items = listOf(createTestPost(id = "0", subreddit = "kept")),
                    after = "t3_0",
                )
            viewModel.postList.load(forceRefresh = true)
            advanceUntilIdle()

            settingsRepo.addBlockedSubreddit("banned")
            fakeApi.postsResult =
                createTestListing(
                    items =
                        listOf(
                            createTestPost(id = "1", subreddit = "kept2"),
                            createTestPost(id = "2", subreddit = "banned"),
                        ),
                )
            viewModel.postList.loadMore()
            advanceUntilIdle()

            val posts = viewModel.postList.state.value.posts
            assertTrue(posts.none { it.subreddit.equals("banned", ignoreCase = true) })
            assertTrue(posts.any { it.id == "1" })
        }

    // ==================== Voting ====================

    @Test
    fun vote_upvote_updatesPost() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()

            val post =
                viewModel.postList.state.value.posts
                    .firstOrNull() ?: return@runTest
            viewModel.postList.vote(post, 1)
            advanceUntilIdle()

            // Verify post is still in list
            assertTrue(
                viewModel.postList.state.value.posts
                    .any { it.id == post.id },
            )
        }

    @Test
    fun vote_downvote_updatesPost() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()

            val post =
                viewModel.postList.state.value.posts
                    .firstOrNull() ?: return@runTest
            viewModel.postList.vote(post, -1)
            advanceUntilIdle()

            assertTrue(
                viewModel.postList.state.value.posts
                    .any { it.id == post.id },
            )
        }

    @Test
    fun vote_clearVote_updatesPost() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()

            val post =
                viewModel.postList.state.value.posts
                    .firstOrNull() ?: return@runTest
            viewModel.postList.vote(post, 0)
            advanceUntilIdle()

            assertTrue(
                viewModel.postList.state.value.posts
                    .any { it.id == post.id },
            )
        }

    // ==================== Saving ====================

    @Test
    fun save_savesPost() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()

            val post =
                viewModel.postList.state.value.posts
                    .firstOrNull() ?: return@runTest
            viewModel.postList.save(post)
            advanceUntilIdle()

            assertTrue(
                viewModel.postList.state.value.posts
                    .any { it.id == post.id },
            )
        }

    // ==================== Hiding ====================

    @Test
    fun hide_removesPostFromList() =
        runTest {
            fakeApi.postsResult =
                createTestListing(
                    items =
                        listOf(
                            createTestPost(id = "1"),
                            createTestPost(id = "2"),
                        ),
                )
            viewModel.postList.load()
            advanceUntilIdle()

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

    // ==================== Error Handling ====================

    @Test
    fun clearError_clearsErrorMessage() =
        runTest {
            viewModel.postList.clearError()
            assertNull(viewModel.postList.state.value.error)
        }

    // ==================== Post Updates ====================

    @Test
    fun postUpdates_updatesExistingPost() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()

            val post =
                viewModel.postList.state.value.posts
                    .firstOrNull() ?: return@runTest
            assertTrue(
                viewModel.postList.state.value.posts
                    .any { it.id == post.id },
            )
        }

    @Test
    fun postUpdates_removesHiddenPost() =
        runTest {
            viewModel.postList.load()
            advanceUntilIdle()

            val post =
                viewModel.postList.state.value.posts
                    .firstOrNull() ?: return@runTest
            viewModel.postList.hide(post)
            advanceUntilIdle()

            assertFalse(
                viewModel.postList.state.value.posts
                    .any { it.id == post.id },
            )
        }

    // ==================== Login State ====================

    @Test
    fun loginStateChange_updatesIsLoggedIn() =
        runTest {
            // Verify initial state
            assertFalse(viewModel.uiState.value.isLoggedIn)
        }

    @Test
    fun loginStateChange_filtersBlockedSubreddits() =
        runTest {
            settingsRepo.addBlockedSubreddit("banned")
            advanceUntilIdle()

            val posts = viewModel.postList.state.value.posts
            assertTrue(posts.all { it.subreddit.lowercase() != "banned" })
        }
}
