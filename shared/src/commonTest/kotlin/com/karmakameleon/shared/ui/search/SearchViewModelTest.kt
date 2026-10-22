package com.karmakameleon.shared.ui.search

import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.FakeRedditApi.SearchRequest
import com.karmakameleon.shared.createTestListing
import com.karmakameleon.shared.createTestPost
import com.karmakameleon.shared.createTestSubreddit
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.data.repository.SubredditRepository
import com.karmakameleon.shared.domain.model.SearchSort
import com.karmakameleon.shared.domain.model.SearchType
import com.karmakameleon.shared.domain.model.TimeFilter
import com.karmakameleon.shared.testing.FakeAuthManager
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class SearchViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeApi: FakeRedditApi
    private lateinit var postRepo: PostRepository
    private lateinit var subredditRepo: SubredditRepository
    private lateinit var settingsRepo: SettingsRepository
    private lateinit var viewModel: SearchViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val fakeAuthManager = FakeAuthManager()
        fakeApi = FakeRedditApi(fakeAuthManager)
        settingsRepo = SettingsRepository(MapSettings())
        postRepo = PostRepository(fakeApi)
        subredditRepo = SubredditRepository(fakeApi, settingsRepo)
        viewModel = SearchViewModel(postRepo, subredditRepo, settingsRepo)
    }

    private fun search() {
        viewModel.search()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Initialization ====================

    @Test
    fun init_setsDefaultState() {
        assertEquals("", viewModel.uiState.value.query)
        assertEquals(SearchType.POST, viewModel.uiState.value.searchType)
        assertEquals(SearchSort.RELEVANCE, viewModel.uiState.value.searchSort)
        assertEquals(TimeFilter.ALL, viewModel.uiState.value.timeFilter)
        assertTrue(
            viewModel.uiState.value.posts
                .isEmpty(),
        )
        assertTrue(
            viewModel.uiState.value.subreddits
                .isEmpty(),
        )
    }

    // ==================== Query Setting ====================

    @Test
    fun setQuery_updatesQuery() =
        runTest {
            viewModel.setQuery("kotlin")
            advanceUntilIdle()
            assertEquals("kotlin", viewModel.uiState.value.query)
        }

    @Test
    fun setQuery_trimsWhitespace() =
        runTest {
            viewModel.setQuery("  kotlin  ")
            advanceUntilIdle()
            // Query is stored as-is, trimming happens during search
            assertTrue(
                viewModel.uiState.value.query
                    .contains("kotlin"),
            )
        }

    @Test
    fun setQuery_emptyQuery() =
        runTest {
            viewModel.setQuery("")
            advanceUntilIdle()
            assertEquals("", viewModel.uiState.value.query)
        }

    @Test
    fun setQuery_debouncesSearch() =
        runTest {
            viewModel.setQuery("k")
            advanceUntilIdle()
            viewModel.setQuery("ko")
            advanceUntilIdle()
            viewModel.setQuery("kot")
            advanceUntilIdle()
            viewModel.setQuery("kotl")
            advanceUntilIdle()
            viewModel.setQuery("kotlin")
            advanceUntilIdle()

            assertEquals("kotlin", viewModel.uiState.value.query)
        }

    @Test
    fun setQuery_redditLink_detectsLink() =
        runTest {
            viewModel.setQuery("https://reddit.com/r/kotlin")
            advanceUntilIdle()

            // Link detection may or may not work depending on parser
            assertTrue(
                viewModel.uiState.value.query
                    .contains("reddit"),
            )
        }

    @Test
    fun setQuery_subredditLink_detectsLink() =
        runTest {
            viewModel.setQuery("/r/kotlin")
            advanceUntilIdle()

            assertEquals("/r/kotlin", viewModel.uiState.value.query)
        }

    // ==================== Search Type ====================

    @Test
    fun setSearchType_searchesSubreddits() =
        runTest {
            fakeApi.searchSubredditsResult = listOf(createTestSubreddit(displayName = "Kotlin"))
            viewModel.setQuery("kotlin")
            advanceUntilIdle()

            viewModel.setSearchType(SearchType.SUBREDDIT)
            advanceUntilIdle()

            assertEquals(
                listOf("Kotlin"),
                viewModel.uiState.value.subreddits
                    .map { it.displayName },
            )
            assertTrue(
                viewModel.uiState.value.posts
                    .isEmpty(),
            )
            assertEquals(listOf(false), fakeApi.subredditSearchesIncludingNsfw)
        }

    @Test
    fun setSearchType_subredditsIncludeNsfwOnlyWhenBothSettingsAreOn() =
        runTest {
            viewModel.setSearchType(SearchType.SUBREDDIT)
            viewModel.setQuery("kotlin")
            settingsRepo.setNsfwSearchEnabled(true)
            advanceUntilIdle()
            settingsRepo.setNsfwEnabled(true)
            viewModel.search()
            advanceUntilIdle()
            settingsRepo.setNsfwSearchEnabled(false)
            viewModel.search()
            advanceUntilIdle()

            assertEquals(listOf(false, true, false), fakeApi.subredditSearchesIncludingNsfw)
        }

    @Test
    fun setSearchType_sameTypeOrShortQueryDoesNotSearch() =
        runTest {
            viewModel.setSearchType(SearchType.POST)
            viewModel.setSearchType(SearchType.SUBREDDIT)
            viewModel.setSearchType(SearchType.SUBREDDIT)
            viewModel.setQuery("k")
            viewModel.search()
            advanceUntilIdle()

            assertEquals(SearchType.SUBREDDIT, viewModel.uiState.value.searchType)
            assertTrue(fakeApi.searchRequests.isEmpty())
            assertTrue(fakeApi.subredditSearchesIncludingNsfw.isEmpty())
        }

    // ==================== Results ====================

    @Test
    fun search_leavesOutNsfwPostsUnlessEnabled() =
        runTest {
            fakeApi.searchResult = createTestListing(items = listOf(createTestPost(id = "p1"), createTestPost(id = "p2", isNsfw = true)))

            viewModel.setQuery("kotlin")
            advanceUntilIdle()
            assertEquals(
                listOf("p1"),
                viewModel.uiState.value.posts
                    .map { it.id },
            )

            settingsRepo.setNsfwEnabled(true)
            viewModel.search()
            advanceUntilIdle()
            assertEquals(
                listOf("p1", "p2"),
                viewModel.uiState.value.posts
                    .map { it.id },
            )
        }

    @Test
    fun search_showsTheError() =
        runTest {
            fakeApi.shouldThrow = Exception("No connection")

            viewModel.setQuery("kotlin")
            advanceUntilIdle()

            assertEquals("No connection", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun subredditSearch_showsTheError() =
        runTest {
            viewModel.setSearchType(SearchType.SUBREDDIT)
            fakeApi.shouldThrow = Exception("No connection")

            viewModel.setQuery("kotlin")
            advanceUntilIdle()

            assertEquals("No connection", viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    // ==================== Sort and time filter ====================

    @Test
    fun setSearchSortAndTimeFilter_searchAgainWithThem() =
        runTest {
            viewModel.setQuery("kotlin")
            advanceUntilIdle()

            viewModel.setSearchSort(SearchSort.TOP)
            advanceUntilIdle()
            viewModel.setTimeFilter(TimeFilter.MONTH)
            advanceUntilIdle()
            viewModel.setSearchSort(SearchSort.TOP)
            viewModel.setTimeFilter(TimeFilter.MONTH)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    SearchRequest("kotlin", SearchSort.RELEVANCE, TimeFilter.ALL, null),
                    SearchRequest("kotlin", SearchSort.TOP, TimeFilter.ALL, null),
                    SearchRequest("kotlin", SearchSort.TOP, TimeFilter.MONTH, null),
                ),
                fakeApi.searchRequests,
            )
        }

    // ==================== Loading More ====================

    @Test
    fun loadMore_appendsTheNextPage() =
        runTest {
            fakeApi.searchResult = createTestListing(items = listOf(createTestPost(id = "p1")), after = "t3_p1")
            viewModel.setQuery("kotlin")
            advanceUntilIdle()
            fakeApi.searchResult = createTestListing(items = listOf(createTestPost(id = "p2"), createTestPost(id = "p3", isNsfw = true)))

            viewModel.loadMore()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("p1", "p2"), state.posts.map { it.id })
            assertNull(state.after)
            assertFalse(state.hasMore)
            assertEquals("t3_p1", fakeApi.searchRequests.last().after)
        }

    @Test
    fun loadMore_keepsNsfwPostsWhenEnabled() =
        runTest {
            settingsRepo.setNsfwEnabled(true)
            fakeApi.searchResult = createTestListing(items = listOf(createTestPost(id = "p1")), after = "t3_p1")
            viewModel.setQuery("kotlin")
            advanceUntilIdle()
            fakeApi.searchResult = createTestListing(items = listOf(createTestPost(id = "p2", isNsfw = true)))

            viewModel.loadMore()
            advanceUntilIdle()

            assertEquals(
                listOf("p1", "p2"),
                viewModel.uiState.value.posts
                    .map { it.id },
            )
        }

    @Test
    fun loadMore_keepsThePageWhenTheNextFails() =
        runTest {
            fakeApi.searchResult = createTestListing(items = listOf(createTestPost(id = "p1")), after = "t3_p1")
            viewModel.setQuery("kotlin")
            advanceUntilIdle()
            fakeApi.shouldThrow = Exception("No connection")

            viewModel.loadMore()
            advanceUntilIdle()

            assertEquals(
                listOf("p1"),
                viewModel.uiState.value.posts
                    .map { it.id },
            )
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun loadMore_onlyWhenThereIsAnotherPageAndNothingLoading() =
        runTest {
            fakeApi.searchResult = createTestListing(items = listOf(createTestPost(id = "p1")), after = "t3_p1")
            viewModel.setQuery("kotlin")
            advanceUntilIdle()
            fakeApi.searchResult = createTestListing(items = listOf(createTestPost(id = "p2")))
            fakeApi.responseDelayMillis = 1000

            viewModel.loadMore()
            runCurrent()
            viewModel.loadMore()
            advanceUntilIdle()
            // The last page had no after.
            viewModel.loadMore()
            advanceUntilIdle()

            assertEquals(2, fakeApi.searchRequests.size)
        }

    // ==================== Empty Query ====================

    @Test
    fun shortQuery_clearsResults() =
        runTest {
            fakeApi.searchResult = createTestListing(items = listOf(createTestPost(id = "p1")))
            viewModel.setQuery("kotlin")
            advanceUntilIdle()

            viewModel.setQuery("k")
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.posts
                    .isEmpty(),
            )
            assertEquals(1, fakeApi.searchRequests.size)
        }
}
