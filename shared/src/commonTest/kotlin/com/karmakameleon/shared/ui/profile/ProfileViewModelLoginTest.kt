package com.karmakameleon.shared.ui.profile

import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.data.api.AuthManager
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.data.repository.MarkdownRepository
import com.karmakameleon.shared.data.repository.PostRepository
import com.karmakameleon.shared.data.repository.UserRepository
import com.karmakameleon.shared.domain.model.PostSort
import com.karmakameleon.shared.domain.model.TimeFilter
import com.karmakameleon.shared.testing.FakeAuthManager
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

/** The profile's settings and signing in. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelLoginTest {
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
        commentRepo = CommentRepository(fakeApi, MarkdownRepository(Dispatchers.Main))
        userRepo = UserRepository(fakeApi, fakeAuthManager, MarkdownRepository(Dispatchers.Main))
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

    @Test
    fun handleLoginRedirect_withoutACodeShowsAnError() {
        viewModel.setClientId("client")
        viewModel.setRedirectUri("myclient://callback")
        viewModel.initiateLogin()
        val state = Url(viewModel.uiState.value.authUrl!!).parameters["state"]

        assertTrue(viewModel.handleLoginRedirect("myclient://callback?state=$state"))

        assertEquals("Sign in failed. Please try again.", viewModel.uiState.value.errorMessage)
    }
}
