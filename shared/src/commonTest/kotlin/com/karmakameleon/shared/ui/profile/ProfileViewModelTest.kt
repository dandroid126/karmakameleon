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
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.PostSort
import com.karmakameleon.shared.domain.model.TimeFilter
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
        postRepo = PostRepository(fakeApi, MarkdownRepository(testDispatcher))
        commentRepo = CommentRepository(fakeApi)
        userRepo = UserRepository(fakeApi, fakeAuthManager)
        viewModel = ProfileViewModel(userRepo, postRepo, commentRepo, fakeAuthManager)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Tab Selection ====================

    @Test
    fun setSelectedTab_loadsTheListsTheTabShows() =
        runTest {
            loadOwnPosts()
            val tabs =
                mapOf(
                    ProfileTab.COMMENTS to listOf("comments"),
                    ProfileTab.SAVED to listOf("saved_posts", "saved_comments"),
                    ProfileTab.UPVOTED to listOf("upvoted"),
                    ProfileTab.DOWNVOTED to listOf("downvoted"),
                    ProfileTab.ABOUT to emptyList(),
                )

            tabs.forEach { (tab, lists) ->
                fakeApi.userContentRequests.clear()
                viewModel.setSelectedTab(tab)
                advanceUntilIdle()

                assertEquals(tab, viewModel.uiState.value.selectedTab)
                assertEquals(lists, fakeApi.userContentRequests.map { it.list }, tab.name)
            }
        }

    @Test
    fun setSelectedTab_doesNotReloadAListThatHasItems() =
        runTest {
            loadOwnPosts(createTestPost(id = "p1"))
            fakeApi.userContentRequests.clear()

            viewModel.setSelectedTab(ProfileTab.COMMENTS)
            viewModel.setSelectedTab(ProfileTab.POSTS)
            advanceUntilIdle()

            assertEquals(listOf("comments"), fakeApi.userContentRequests.map { it.list })
        }

    @Test
    fun setSelectedTab_beforeAProfileLoadsOnlyChangesTheTab() =
        runTest {
            viewModel.setSelectedTab(ProfileTab.COMMENTS)
            advanceUntilIdle()

            assertEquals(ProfileTab.COMMENTS, viewModel.uiState.value.selectedTab)
            assertTrue(fakeApi.userContentRequests.isEmpty())
        }

    // ==================== Loading Own Profile ====================

    @Test
    fun loadOwnProfile_loadsTheAccountAndItsPosts() =
        runTest {
            loadOwnPosts(createTestPost(id = "p1"))

            val state = viewModel.uiState.value
            assertEquals("testuser", state.account?.name)
            assertTrue(state.isOwnProfile)
            assertTrue(state.isLoggedIn)
            assertFalse(state.isLoading)
            assertEquals(listOf("p1"), state.posts.items.map { it.id })
            assertEquals(
                FakeRedditApi.UserContentRequest("submitted", "testuser", PostSort.NEW, TimeFilter.ALL),
                fakeApi.userContentRequests.single(),
            )
        }

    @Test
    fun loadOwnProfile_showsTheError() =
        runTest {
            viewModel.loadOwnProfile(forceRefresh = true)
            advanceUntilIdle()

            assertEquals("Failed to load user", viewModel.uiState.value.errorMessage)
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertNull(viewModel.uiState.value.account)
        }

    // ==================== Loading User Profile ====================

    @Test
    fun loadUserProfile_loadsTheUserAndTheirPosts() =
        runTest {
            fakeApi.userResult = createTestUser(name = "someone")
            fakeApi.userPostsResult = createTestListing(items = listOf(createTestPost(id = "p1")), after = "t3_p1")

            viewModel.loadUserProfile("someone")
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("someone", state.user?.name)
            assertFalse(state.isOwnProfile)
            assertFalse(state.isLoading)
            assertEquals(listOf("p1"), state.posts.items.map { it.id })
            assertEquals("t3_p1", state.posts.after)
            assertEquals("someone", fakeApi.userContentRequests.single().username)
        }

    @Test
    fun loadUserProfile_showsTheError() =
        runTest {
            viewModel.loadUserProfile("nobody")
            advanceUntilIdle()

            assertEquals("User not found", viewModel.uiState.value.errorMessage)
            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun loadUserProfile_keepsThePostsWhenTheyFailToLoad() =
        runTest {
            fakeApi.userResult = createTestUser(name = "someone")
            viewModel.loadUserProfile("someone")
            advanceUntilIdle()
            fakeApi.userPostsResult = createTestListing(items = listOf(createTestPost(id = "p1")))
            viewModel.refresh()
            advanceUntilIdle()
            fakeApi.shouldThrow = Exception("No connection")

            viewModel.setPostsSort(PostSort.TOP)
            advanceUntilIdle()

            assertEquals(
                listOf("p1"),
                viewModel.uiState.value.posts.items
                    .map { it.id },
            )
            assertFalse(viewModel.uiState.value.isLoadingContent)
        }

    // ==================== Refresh ====================

    @Test
    fun refresh_reloadsTheListsOfTheTab() =
        runTest {
            loadOwnPosts(createTestPost(id = "p1"))
            viewModel.setSelectedTab(ProfileTab.SAVED)
            advanceUntilIdle()
            fakeApi.userContentRequests.clear()
            fakeApi.savedPostsResult = createTestListing(items = listOf(createTestPost(id = "s1")))

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(listOf("saved_posts", "saved_comments"), fakeApi.userContentRequests.map { it.list })
            assertEquals(
                listOf("s1"),
                viewModel.uiState.value.savedPosts.items
                    .map { it.id },
            )
        }

    @Test
    fun refresh_onTheAboutTabReloadsTheProfile() =
        runTest {
            loadOwnPosts()
            viewModel.setSelectedTab(ProfileTab.ABOUT)
            fakeApi.meResult = createTestUser(name = "renamed")

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(
                "renamed",
                viewModel.uiState.value.account
                    ?.name,
            )
        }

    @Test
    fun refresh_onSomeoneElsesAboutTabReloadsTheirProfile() =
        runTest {
            fakeApi.userResult = createTestUser(name = "someone", linkKarma = 1)
            viewModel.loadUserProfile("someone")
            advanceUntilIdle()
            viewModel.setSelectedTab(ProfileTab.ABOUT)
            fakeApi.userResult = createTestUser(name = "someone", linkKarma = 2)

            viewModel.refresh()
            advanceUntilIdle()

            assertEquals(
                2,
                viewModel.uiState.value.user
                    ?.linkKarma,
            )
        }

    @Test
    fun refresh_beforeAProfileLoadsDoesNothing() =
        runTest {
            viewModel.refresh()
            advanceUntilIdle()

            assertTrue(fakeApi.userContentRequests.isEmpty())
        }

    // ==================== Sorting and time filters ====================

    @Test
    fun sortsAndTimeFilters_reloadTheirListWithThem() =
        runTest {
            loadOwnPosts()
            fakeApi.userContentRequests.clear()

            viewModel.setPostsSort(PostSort.TOP)
            advanceUntilIdle()
            viewModel.setPostsTimeFilter(TimeFilter.WEEK)
            advanceUntilIdle()
            viewModel.setCommentsSort(PostSort.CONTROVERSIAL)
            advanceUntilIdle()
            viewModel.setCommentsTimeFilter(TimeFilter.MONTH)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    FakeRedditApi.UserContentRequest("submitted", "testuser", PostSort.TOP, TimeFilter.ALL),
                    FakeRedditApi.UserContentRequest("submitted", "testuser", PostSort.TOP, TimeFilter.WEEK),
                    FakeRedditApi.UserContentRequest("comments", "testuser", PostSort.CONTROVERSIAL, TimeFilter.ALL),
                    FakeRedditApi.UserContentRequest("comments", "testuser", PostSort.CONTROVERSIAL, TimeFilter.MONTH),
                ),
                fakeApi.userContentRequests,
            )
        }

    @Test
    fun sortsAndTimeFilters_beforeAProfileLoadsOnlyChangeTheSetting() =
        runTest {
            viewModel.setPostsSort(PostSort.HOT)
            viewModel.setPostsTimeFilter(TimeFilter.WEEK)
            viewModel.setCommentsSort(PostSort.HOT)
            viewModel.setCommentsTimeFilter(TimeFilter.MONTH)
            viewModel.setSavedContentType(SavedContentType.COMMENTS)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(PostSort.HOT, state.postsSort)
            assertEquals(TimeFilter.WEEK, state.postsTimeFilter)
            assertEquals(PostSort.HOT, state.commentsSort)
            assertEquals(TimeFilter.MONTH, state.commentsTimeFilter)
            assertEquals(SavedContentType.COMMENTS, state.savedContentType)
            assertTrue(fakeApi.userContentRequests.isEmpty())
        }

    // ==================== Voting ====================

    @Test
    fun vote_upvote_updatesPost() =
        runTest {
            loadOwnPosts(createTestPost(id = "p1", score = 100, likes = null))

            viewModel.vote(
                viewModel.uiState.value.posts.items
                    .first(),
                1,
            )
            advanceUntilIdle()

            val post =
                viewModel.uiState.value.posts.items
                    .single()
            assertEquals(true, post.likes)
            assertEquals(101, post.score)
        }

    @Test
    fun save_savesPost() =
        runTest {
            loadOwnPosts(createTestPost(id = "p1", isSaved = false))

            viewModel.save(
                viewModel.uiState.value.posts.items
                    .first(),
            )
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.posts.items
                    .single()
                    .isSaved,
            )
        }

    // ==================== Load More: Posts ====================

    @Test
    fun loadMoreUserPosts_appendsToExistingPosts() =
        runTest {
            fakeApi.meResult = createTestUser(name = "testuser")
            fakeApi.userPostsResult = createTestListing(items = listOf(createTestPost(id = "p1")), after = "token1")
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            val initialCount = viewModel.uiState.value.posts.items.size

            fakeApi.userPostsResult = createTestListing(items = listOf(createTestPost(id = "p2")))
            viewModel.loadMore(ProfileList.POSTS)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.posts.items.size > initialCount)
            assertFalse(viewModel.uiState.value.posts.isLoadingMore)
        }

    @Test
    fun loadMoreUserPosts_preventsLoadWhenNoMore() =
        runTest {
            fakeApi.meResult = createTestUser(name = "testuser")
            fakeApi.userPostsResult = createTestListing(items = listOf(createTestPost(id = "p1")), after = null)
            viewModel.loadOwnProfile()
            advanceUntilIdle()

            viewModel.loadMore(ProfileList.POSTS)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.posts.isLoadingMore)
            assertEquals(1, viewModel.uiState.value.posts.items.size)
        }

    @Test
    fun loadMoreUserPosts_updatesAfterToken() =
        runTest {
            fakeApi.meResult = createTestUser(name = "testuser")
            fakeApi.userPostsResult = createTestListing(items = listOf(createTestPost(id = "p1")), after = "token1")
            viewModel.loadOwnProfile()
            advanceUntilIdle()

            fakeApi.userPostsResult = createTestListing(items = listOf(createTestPost(id = "p2")), after = "token2")
            viewModel.loadMore(ProfileList.POSTS)
            advanceUntilIdle()

            assertEquals("token2", viewModel.uiState.value.posts.after)
            assertTrue(viewModel.uiState.value.posts.hasMore)
        }

    // ==================== Load More: Comments ====================

    @Test
    fun loadMoreUserComments_appendsToExistingComments() =
        runTest {
            fakeApi.meResult = createTestUser(name = "testuser")
            fakeApi.userCommentsResult = createTestListing(items = listOf(createTestComment(id = "c1")), after = "token1")
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            viewModel.setSelectedTab(ProfileTab.COMMENTS)
            advanceUntilIdle()
            val initialCount = viewModel.uiState.value.comments.items.size

            fakeApi.userCommentsResult = createTestListing(items = listOf(createTestComment(id = "c2")))
            viewModel.loadMore(ProfileList.COMMENTS)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.comments.items.size > initialCount)
            assertFalse(viewModel.uiState.value.comments.isLoadingMore)
        }

    @Test
    fun loadMoreUserComments_preventsLoadWhenNoMore() =
        runTest {
            fakeApi.meResult = createTestUser(name = "testuser")
            fakeApi.userCommentsResult = createTestListing(items = listOf(createTestComment(id = "c1")), after = null)
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            viewModel.setSelectedTab(ProfileTab.COMMENTS)
            advanceUntilIdle()

            viewModel.loadMore(ProfileList.COMMENTS)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.comments.isLoadingMore)
            assertEquals(1, viewModel.uiState.value.comments.items.size)
        }

    // ==================== Load More: Saved Posts ====================

    @Test
    fun loadMoreSavedPosts_appendsToExistingSavedPosts() =
        runTest {
            fakeApi.meResult = createTestUser(name = "testuser")
            fakeApi.savedPostsResult = createTestListing(items = listOf(createTestPost(id = "s1")), after = "token1")
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            viewModel.setSelectedTab(ProfileTab.SAVED)
            advanceUntilIdle()
            val initialCount = viewModel.uiState.value.savedPosts.items.size

            fakeApi.savedPostsResult = createTestListing(items = listOf(createTestPost(id = "s2")))
            viewModel.loadMore(ProfileList.SAVED_POSTS)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.savedPosts.items.size > initialCount)
            assertFalse(viewModel.uiState.value.savedPosts.isLoadingMore)
        }

    @Test
    fun loadMoreSavedPosts_preventsLoadWhenNoMore() =
        runTest {
            fakeApi.meResult = createTestUser(name = "testuser")
            fakeApi.savedPostsResult = createTestListing(items = listOf(createTestPost(id = "s1")), after = null)
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            viewModel.setSelectedTab(ProfileTab.SAVED)
            advanceUntilIdle()

            viewModel.loadMore(ProfileList.SAVED_POSTS)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.savedPosts.isLoadingMore)
            assertEquals(1, viewModel.uiState.value.savedPosts.items.size)
        }

    // ==================== Load More: Saved Comments ====================

    @Test
    fun loadMoreSavedComments_appendsToExistingSavedComments() =
        runTest {
            fakeApi.meResult = createTestUser(name = "testuser")
            fakeApi.savedCommentsResult = createTestListing(items = listOf(createTestComment(id = "sc1")), after = "token1")
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            viewModel.setSelectedTab(ProfileTab.SAVED)
            advanceUntilIdle()
            val initialCount = viewModel.uiState.value.savedComments.items.size

            fakeApi.savedCommentsResult = createTestListing(items = listOf(createTestComment(id = "sc2")))
            viewModel.loadMore(ProfileList.SAVED_COMMENTS)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.savedComments.items.size > initialCount)
            assertFalse(viewModel.uiState.value.savedComments.isLoadingMore)
        }

    @Test
    fun loadMoreSavedComments_preventsLoadWhenNoMore() =
        runTest {
            fakeApi.meResult = createTestUser(name = "testuser")
            fakeApi.savedCommentsResult = createTestListing(items = listOf(createTestComment(id = "sc1")), after = null)
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            viewModel.setSelectedTab(ProfileTab.SAVED)
            advanceUntilIdle()

            viewModel.loadMore(ProfileList.SAVED_COMMENTS)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.savedComments.isLoadingMore)
            assertEquals(1, viewModel.uiState.value.savedComments.items.size)
        }

    // ==================== Load More: Upvoted ====================

    @Test
    fun loadMoreUpvotedPosts_appendsToExistingUpvotedPosts() =
        runTest {
            fakeApi.meResult = createTestUser(name = "testuser")
            fakeApi.upvotedPostsResult = createTestListing(items = listOf(createTestPost(id = "u1")), after = "token1")
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            viewModel.setSelectedTab(ProfileTab.UPVOTED)
            advanceUntilIdle()
            val initialCount = viewModel.uiState.value.upvotedPosts.items.size

            fakeApi.upvotedPostsResult = createTestListing(items = listOf(createTestPost(id = "u2")))
            viewModel.loadMore(ProfileList.UPVOTED)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.upvotedPosts.items.size > initialCount)
            assertFalse(viewModel.uiState.value.upvotedPosts.isLoadingMore)
        }

    @Test
    fun loadMoreUpvotedPosts_preventsLoadWhenNoMore() =
        runTest {
            fakeApi.meResult = createTestUser(name = "testuser")
            fakeApi.upvotedPostsResult = createTestListing(items = listOf(createTestPost(id = "u1")), after = null)
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            viewModel.setSelectedTab(ProfileTab.UPVOTED)
            advanceUntilIdle()

            viewModel.loadMore(ProfileList.UPVOTED)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.upvotedPosts.isLoadingMore)
            assertEquals(1, viewModel.uiState.value.upvotedPosts.items.size)
        }

    // ==================== Load More: Downvoted ====================

    @Test
    fun loadMoreDownvotedPosts_appendsToExistingDownvotedPosts() =
        runTest {
            fakeApi.meResult = createTestUser(name = "testuser")
            fakeApi.downvotedPostsResult = createTestListing(items = listOf(createTestPost(id = "d1")), after = "token1")
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            viewModel.setSelectedTab(ProfileTab.DOWNVOTED)
            advanceUntilIdle()
            val initialCount = viewModel.uiState.value.downvotedPosts.items.size

            fakeApi.downvotedPostsResult = createTestListing(items = listOf(createTestPost(id = "d2")))
            viewModel.loadMore(ProfileList.DOWNVOTED)
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.downvotedPosts.items.size > initialCount)
            assertFalse(viewModel.uiState.value.downvotedPosts.isLoadingMore)
        }

    @Test
    fun loadMoreDownvotedPosts_preventsLoadWhenNoMore() =
        runTest {
            fakeApi.meResult = createTestUser(name = "testuser")
            fakeApi.downvotedPostsResult = createTestListing(items = listOf(createTestPost(id = "d1")), after = null)
            viewModel.loadOwnProfile()
            advanceUntilIdle()
            viewModel.setSelectedTab(ProfileTab.DOWNVOTED)
            advanceUntilIdle()

            viewModel.loadMore(ProfileList.DOWNVOTED)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.downvotedPosts.isLoadingMore)
            assertEquals(1, viewModel.uiState.value.downvotedPosts.items.size)
        }

    // ==================== Error Handling ====================

    @Test
    fun clearErrorMessage_clearsErrorMessage() =
        runTest {
            viewModel.clearErrorMessage()
            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun clearAllData_resetsState() =
        runTest {
            viewModel.loadOwnProfile()
            advanceUntilIdle()

            viewModel.clearAllData()

            assertTrue(
                viewModel.uiState.value.posts.items
                    .isEmpty(),
            )
            assertNull(viewModel.uiState.value.account)
        }

    // Loads the logged-in user's profile with [posts] on its posts tab, so the tests that act on a post have one.
    private fun TestScope.loadOwnPosts(vararg posts: Post) {
        fakeApi.meResult = createTestUser(name = "testuser")
        fakeApi.userPostsResult = createTestListing(items = posts.toList())
        viewModel.loadOwnProfile()
        advanceUntilIdle()
    }
}
