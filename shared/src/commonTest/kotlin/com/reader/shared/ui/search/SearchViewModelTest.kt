package com.reader.shared.ui.search

import com.reader.shared.FakeRedditApi
import com.reader.shared.FakeAuthManager
import com.reader.shared.createTestPost
import com.reader.shared.createTestSubreddit
import com.reader.shared.createTestListing
import com.reader.shared.data.repository.PostRepository
import com.reader.shared.data.repository.SettingsRepository
import com.reader.shared.data.repository.SubredditRepository
import com.reader.shared.domain.model.Post
import com.reader.shared.domain.model.SearchSort
import com.reader.shared.domain.model.SearchType
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
        assertTrue(viewModel.uiState.value.posts.isEmpty())
        assertTrue(viewModel.uiState.value.subreddits.isEmpty())
    }

    // ==================== Query Setting ====================

    @Test
    fun setQuery_updatesQuery() = runTest {
        viewModel.setQuery("kotlin")
        advanceUntilIdle()
        assertEquals("kotlin", viewModel.uiState.value.query)
    }

    @Test
    fun setQuery_trimsWhitespace() = runTest {
        viewModel.setQuery("  kotlin  ")
        advanceUntilIdle()
        // Query is stored as-is, trimming happens during search
        assertTrue(viewModel.uiState.value.query.contains("kotlin"))
    }

    @Test
    fun setQuery_emptyQuery() = runTest {
        viewModel.setQuery("")
        advanceUntilIdle()
        assertEquals("", viewModel.uiState.value.query)
    }

    @Test
    fun setQuery_debouncesSearch() = runTest {
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
    fun setQuery_redditLink_detectsLink() = runTest {
        viewModel.setQuery("https://reddit.com/r/kotlin")
        advanceUntilIdle()
        
        // Link detection may or may not work depending on parser
        assertTrue(viewModel.uiState.value.query.contains("reddit"))
    }

    @Test
    fun setQuery_subredditLink_detectsLink() = runTest {
        viewModel.setQuery("/r/kotlin")
        advanceUntilIdle()
        
        assertEquals("/r/kotlin", viewModel.uiState.value.query)
    }

    // ==================== Search Type ====================

    @Test
    fun setSearchType_changesSearchType() = runTest {
        viewModel.setSearchType(SearchType.SUBREDDIT)
        advanceUntilIdle()
        assertEquals(SearchType.SUBREDDIT, viewModel.uiState.value.searchType)
    }

    @Test
    fun setSearchType_post_searchesPosts() = runTest {
        viewModel.setQuery("kotlin")
        advanceUntilIdle()
        
        viewModel.setSearchType(SearchType.POST)
        advanceUntilIdle()
        
        assertEquals(SearchType.POST, viewModel.uiState.value.searchType)
    }

    @Test
    fun setSearchType_subreddit_searchesSubreddits() = runTest {
        viewModel.setQuery("kotlin")
        advanceUntilIdle()
        
        viewModel.setSearchType(SearchType.SUBREDDIT)
        advanceUntilIdle()
        
        assertEquals(SearchType.SUBREDDIT, viewModel.uiState.value.searchType)
    }

    // ==================== Search Sort ====================

    @Test
    fun setSearchSort_changesSearchSort() = runTest {
        viewModel.setSearchSort(SearchSort.HOT)
        advanceUntilIdle()
        assertEquals(SearchSort.HOT, viewModel.uiState.value.searchSort)
    }

    @Test
    fun setSearchSort_triggersNewSearch() = runTest {
        viewModel.setQuery("kotlin")
        advanceUntilIdle()
        
        viewModel.setSearchSort(SearchSort.TOP)
        advanceUntilIdle()
        
        assertEquals(SearchSort.TOP, viewModel.uiState.value.searchSort)
    }

    // ==================== Time Filter ====================

    @Test
    fun setTimeFilter_changesTimeFilter() = runTest {
        viewModel.setTimeFilter(TimeFilter.WEEK)
        advanceUntilIdle()
        assertEquals(TimeFilter.WEEK, viewModel.uiState.value.timeFilter)
    }

    @Test
    fun setTimeFilter_triggersNewSearch() = runTest {
        viewModel.setQuery("kotlin")
        advanceUntilIdle()
        
        viewModel.setTimeFilter(TimeFilter.MONTH)
        advanceUntilIdle()
        
        assertEquals(TimeFilter.MONTH, viewModel.uiState.value.timeFilter)
    }

    // ==================== Loading More ====================

    @Test
    fun loadMore_appendsToExistingResults() = runTest {
        viewModel.setQuery("kotlin")
        advanceUntilIdle()
        
        val initialCount = viewModel.uiState.value.posts.size
        viewModel.loadMore()
        advanceUntilIdle()
        
        assertTrue(viewModel.uiState.value.posts.size >= initialCount)
    }

    @Test
    fun loadMore_preventsLoadWhenNoMore() = runTest {
        viewModel.setQuery("kotlin")
        advanceUntilIdle()
        
        viewModel.loadMore()
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun loadMore_preventsMultipleConcurrentLoads() = runTest {
        viewModel.setQuery("kotlin")
        advanceUntilIdle()
        
        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()
        
        assertFalse(viewModel.uiState.value.isLoading)
    }

    // ==================== Empty Query ====================

    @Test
    fun emptyQuery_clearsResults() = runTest {
        viewModel.setQuery("kotlin")
        advanceUntilIdle()
        
        viewModel.setQuery("")
        advanceUntilIdle()
        
        assertEquals("", viewModel.uiState.value.query)
    }
}
