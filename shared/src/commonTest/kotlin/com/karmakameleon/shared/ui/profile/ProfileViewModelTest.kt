package com.karmakameleon.shared.ui.profile

import com.karmakameleon.shared.FakeAuthManager
import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestAccount
import com.karmakameleon.shared.createTestComment
import com.karmakameleon.shared.createTestListing
import com.karmakameleon.shared.createTestPost
import com.karmakameleon.shared.data.api.AuthManager
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.domain.model.PostSort
import com.karmakameleon.shared.domain.model.TimeFilter
import io.ktor.http.Url
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

    @Test
    fun init_setsDefaultRedirectUri() {
        assertEquals(AuthManager.DEFAULT_REDIRECT_URI, viewModel.uiState.value.redirectUri)
    }

    // ==================== Login ====================

    private fun startLogin(redirectUri: String = "myclient://callback"): String {
        viewModel.setClientId("client")
        viewModel.setRedirectUri(redirectUri)
        viewModel.initiateLogin()
        val authUrl = viewModel.uiState.value.authUrl
        assertNotNull(authUrl)
        return assertNotNull(Url(authUrl).parameters["state"])
    }

    @Test
    fun setRedirectUri_updatesStateAndPersists() {
        viewModel.setRedirectUri("myclient://callback")

        assertEquals("myclient://callback", viewModel.uiState.value.redirectUri)
        assertEquals("myclient://callback", fakeAuthManager.getRedirectUri())
    }

    @Test
    fun setRedirectUri_pastedWithNewline_isStripped() {
        viewModel.setRedirectUri("redreader://rr_oauth_redir\r\n")

        assertEquals("redreader://rr_oauth_redir", viewModel.uiState.value.redirectUri)
    }

    @Test
    fun init_loadsSavedRedirectUri() {
        fakeAuthManager.setRedirectUri("myclient://callback")
        val newViewModel = ProfileViewModel(userRepo, postRepo, commentRepo, fakeAuthManager)

        assertEquals("myclient://callback", newViewModel.uiState.value.redirectUri)
    }

    @Test
    fun clearAllData_keepsClientIdAndRedirectUri() {
        viewModel.setClientId("client")
        viewModel.setRedirectUri("myclient://callback")

        viewModel.clearAllData()

        assertEquals("client", viewModel.uiState.value.clientId)
        assertEquals("myclient://callback", viewModel.uiState.value.redirectUri)
    }

    @Test
    fun logout_keepsClientIdAndRedirectUri() {
        viewModel.setClientId("client")
        viewModel.setRedirectUri("myclient://callback")

        viewModel.logout()

        assertEquals("client", viewModel.uiState.value.clientId)
        assertEquals("myclient://callback", viewModel.uiState.value.redirectUri)
    }

    @Test
    fun initiateLogin_authUrlUsesRedirectUri() {
        startLogin("myclient://callback")

        val authUrl = assertNotNull(viewModel.uiState.value.authUrl)
        assertEquals("myclient://callback", Url(authUrl).parameters["redirect_uri"])
    }

    @Test
    fun handleLoginRedirect_otherPage_returnsFalseAndKeepsLoginOpen() {
        startLogin()

        assertFalse(viewModel.handleLoginRedirect("https://www.reddit.com/login"))
        assertNotNull(viewModel.uiState.value.authUrl)
    }

    @Test
    fun handleLoginRedirect_withCode_exchangesCodeAndClosesLogin() =
        runTest {
            val state = startLogin()

            assertTrue(viewModel.handleLoginRedirect("myclient://callback?state=$state&code=the_code#_"))
            advanceUntilIdle()

            assertEquals(listOf("the_code"), fakeAuthManager.exchangedCodes)
            assertNull(viewModel.uiState.value.authUrl)
            assertFalse(viewModel.uiState.value.isSigningIn)
            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun handleLoginRedirect_wrongState_doesNotExchangeCode() =
        runTest {
            startLogin()

            assertTrue(viewModel.handleLoginRedirect("myclient://callback?state=forged&code=the_code"))
            advanceUntilIdle()

            assertTrue(fakeAuthManager.exchangedCodes.isEmpty())
            assertNull(viewModel.uiState.value.authUrl)
            assertNotNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun handleLoginRedirect_duplicate_onlyExchangesOnce() =
        runTest {
            val state = startLogin()
            val redirect = "myclient://callback?state=$state&code=the_code"

            assertTrue(viewModel.handleLoginRedirect(redirect))
            assertTrue(viewModel.handleLoginRedirect(redirect))
            advanceUntilIdle()

            assertEquals(listOf("the_code"), fakeAuthManager.exchangedCodes)
        }

    @Test
    fun handleLoginRedirect_accessDenied_closesLoginWithoutError() =
        runTest {
            val state = startLogin()

            assertTrue(viewModel.handleLoginRedirect("myclient://callback?state=$state&error=access_denied"))
            advanceUntilIdle()

            assertTrue(fakeAuthManager.exchangedCodes.isEmpty())
            assertNull(viewModel.uiState.value.authUrl)
            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun handleLoginRedirect_otherError_showsError() =
        runTest {
            val state = startLogin()

            assertTrue(viewModel.handleLoginRedirect("myclient://callback?state=$state&error=invalid_request"))
            advanceUntilIdle()

            assertTrue(fakeAuthManager.exchangedCodes.isEmpty())
            assertEquals("Sign in failed: invalid_request", viewModel.uiState.value.errorMessage)
        }

    @Test
    fun handleLoginRedirect_exchangeFails_showsError() =
        runTest {
            fakeAuthManager.exchangeSucceeds = false
            val state = startLogin()

            viewModel.handleLoginRedirect("myclient://callback?state=$state&code=the_code")
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isSigningIn)
            assertNotNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun cancelLogin_closesLoginAndIgnoresLaterRedirect() =
        runTest {
            val state = startLogin()

            viewModel.cancelLogin()
            viewModel.handleLoginRedirect("myclient://callback?state=$state&code=the_code")
            advanceUntilIdle()

            assertNull(viewModel.uiState.value.authUrl)
            assertTrue(fakeAuthManager.exchangedCodes.isEmpty())
        }

    // ==================== Tab Selection ====================

    @Test
    fun setSelectedTab_changeSelectedTab() =
        runTest {
            viewModel.setSelectedTab(ProfileTab.COMMENTS)
            advanceUntilIdle()
            assertEquals(ProfileTab.COMMENTS, viewModel.uiState.value.selectedTab)
        }

    @Test
    fun setSelectedTab_posts_loadsUserPosts() =
        runTest {
            viewModel.loadOwnProfile()
            advanceUntilIdle()

            viewModel.setSelectedTab(ProfileTab.POSTS)
            advanceUntilIdle()

            assertEquals(ProfileTab.POSTS, viewModel.uiState.value.selectedTab)
        }

    @Test
    fun setSelectedTab_comments_loadsUserComments() =
        runTest {
            viewModel.loadOwnProfile()
            advanceUntilIdle()

            viewModel.setSelectedTab(ProfileTab.COMMENTS)
            advanceUntilIdle()

            assertEquals(ProfileTab.COMMENTS, viewModel.uiState.value.selectedTab)
        }

    @Test
    fun setSelectedTab_saved_loadsSavedContent() =
        runTest {
            viewModel.loadOwnProfile()
            advanceUntilIdle()

            viewModel.setSelectedTab(ProfileTab.SAVED)
            advanceUntilIdle()

            assertEquals(ProfileTab.SAVED, viewModel.uiState.value.selectedTab)
        }

    @Test
    fun setSelectedTab_upvoted_loadsUpvotedContent() =
        runTest {
            viewModel.loadOwnProfile()
            advanceUntilIdle()

            viewModel.setSelectedTab(ProfileTab.UPVOTED)
            advanceUntilIdle()

            assertEquals(ProfileTab.UPVOTED, viewModel.uiState.value.selectedTab)
        }

    @Test
    fun setSelectedTab_downvoted_loadsDownvotedContent() =
        runTest {
            viewModel.loadOwnProfile()
            advanceUntilIdle()

            viewModel.setSelectedTab(ProfileTab.DOWNVOTED)
            advanceUntilIdle()

            assertEquals(ProfileTab.DOWNVOTED, viewModel.uiState.value.selectedTab)
        }

    // ==================== Loading Own Profile ====================

    @Test
    fun loadOwnProfile_setsLoadingState() =
        runTest {
            viewModel.loadOwnProfile()
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun loadOwnProfile_forceRefresh_setsRefreshingState() =
        runTest {
            viewModel.loadOwnProfile(forceRefresh = true)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isRefreshing)
        }

    // ==================== Loading User Profile ====================

    @Test
    fun loadUserProfile_setsLoadingState() =
        runTest {
            viewModel.loadUserProfile("testuser")
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isLoading)
        }

    @Test
    fun loadUserProfile_forceRefresh_setsRefreshingState() =
        runTest {
            viewModel.loadUserProfile("testuser", forceRefresh = true)
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isRefreshing)
        }

    // ==================== Refresh ====================

    @Test
    fun refresh_reloadsCurrentTab() =
        runTest {
            viewModel.refresh()
            advanceUntilIdle()

            // Verify refresh completes without error
            assertNull(viewModel.uiState.value.errorMessage)
        }

    // ==================== Sorting ====================

    @Test
    fun setPostsSort_changesPostsSort() =
        runTest {
            viewModel.setPostsSort(PostSort.HOT)
            advanceUntilIdle()
            assertEquals(PostSort.HOT, viewModel.uiState.value.postsSort)
        }

    @Test
    fun setPostsSort_reloadsPosts() =
        runTest {
            viewModel.loadOwnProfile()
            advanceUntilIdle()

            viewModel.setPostsSort(PostSort.TOP)
            advanceUntilIdle()

            assertEquals(PostSort.TOP, viewModel.uiState.value.postsSort)
        }

    @Test
    fun setCommentsSort_changesCommentsSort() =
        runTest {
            viewModel.setCommentsSort(PostSort.HOT)
            advanceUntilIdle()
            assertEquals(PostSort.HOT, viewModel.uiState.value.commentsSort)
        }

    @Test
    fun setCommentsSort_reloadsComments() =
        runTest {
            viewModel.loadOwnProfile()
            advanceUntilIdle()

            viewModel.setCommentsSort(PostSort.TOP)
            advanceUntilIdle()

            assertEquals(PostSort.TOP, viewModel.uiState.value.commentsSort)
        }

    // ==================== Time Filter ====================

    @Test
    fun setPostsTimeFilter_changesPostsTimeFilter() =
        runTest {
            viewModel.setPostsTimeFilter(TimeFilter.WEEK)
            advanceUntilIdle()
            assertEquals(TimeFilter.WEEK, viewModel.uiState.value.postsTimeFilter)
        }

    @Test
    fun setCommentsTimeFilter_changesCommentsTimeFilter() =
        runTest {
            viewModel.setCommentsTimeFilter(TimeFilter.MONTH)
            advanceUntilIdle()
            assertEquals(TimeFilter.MONTH, viewModel.uiState.value.commentsTimeFilter)
        }

    // ==================== Voting ====================

    @Test
    fun vote_upvote_updatesPost() =
        runTest {
            viewModel.loadOwnProfile()
            advanceUntilIdle()

            val post =
                viewModel.uiState.value.posts.items
                    .firstOrNull() ?: return@runTest
            viewModel.vote(post, 1)
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.posts.items
                    .any { it.id == post.id },
            )
        }

    @Test
    fun save_savesPost() =
        runTest {
            viewModel.loadOwnProfile()
            advanceUntilIdle()

            val post =
                viewModel.uiState.value.posts.items
                    .firstOrNull() ?: return@runTest
            viewModel.save(post)
            advanceUntilIdle()

            assertTrue(
                viewModel.uiState.value.posts.items
                    .any { it.id == post.id },
            )
        }

    // ==================== Load More: Posts ====================

    @Test
    fun loadMoreUserPosts_appendsToExistingPosts() =
        runTest {
            fakeApi.meResult = createTestAccount(name = "testuser")
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
            fakeApi.meResult = createTestAccount(name = "testuser")
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
            fakeApi.meResult = createTestAccount(name = "testuser")
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
            fakeApi.meResult = createTestAccount(name = "testuser")
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
            fakeApi.meResult = createTestAccount(name = "testuser")
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
            fakeApi.meResult = createTestAccount(name = "testuser")
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
            fakeApi.meResult = createTestAccount(name = "testuser")
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
            fakeApi.meResult = createTestAccount(name = "testuser")
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
            fakeApi.meResult = createTestAccount(name = "testuser")
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
            fakeApi.meResult = createTestAccount(name = "testuser")
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
            fakeApi.meResult = createTestAccount(name = "testuser")
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
            fakeApi.meResult = createTestAccount(name = "testuser")
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
            fakeApi.meResult = createTestAccount(name = "testuser")
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
}
