package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.FakeAuthManager
import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestAccount
import com.karmakameleon.shared.createTestComment
import com.karmakameleon.shared.createTestListing
import com.karmakameleon.shared.createTestPost
import com.karmakameleon.shared.createTestUser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserRepositoryTest {

    private fun createRepo(
        api: FakeRedditApi = FakeRedditApi(),
        authManager: FakeAuthManager = FakeAuthManager()
    ): Triple<UserRepository, FakeRedditApi, FakeAuthManager> {
        return Triple(UserRepository(api, authManager), api, authManager)
    }

    // ==================== loadCurrentUser ====================

    @Test
    fun loadCurrentUser_returnsSuccess() = runTest {
        val (repo, api, _) = createRepo()
        val account = createTestAccount(name = "testuser")
        api.meResult = account

        val result = repo.loadCurrentUser()
        assertTrue(result.isSuccess)
        assertEquals("testuser", result.getOrThrow().name)
    }

    @Test
    fun loadCurrentUser_updatesCurrentAccount() = runTest {
        val (repo, api, _) = createRepo()
        val account = createTestAccount(name = "testuser")
        api.meResult = account

        repo.loadCurrentUser()
        assertEquals("testuser", repo.currentAccount.value?.name)
    }

    @Test
    fun loadCurrentUser_setsIsLoggedIn() = runTest {
        val (repo, api, _) = createRepo()
        val account = createTestAccount(name = "testuser")
        api.meResult = account

        repo.loadCurrentUser()
        assertTrue(repo.isLoggedIn.value)
    }

    @Test
    fun loadCurrentUser_returnsFailureWhenNull() = runTest {
        val (repo, api, _) = createRepo()
        api.meResult = null

        val result = repo.loadCurrentUser()
        assertTrue(result.isFailure)
    }

    @Test
    fun loadCurrentUser_returnsFailureOnException() = runTest {
        val (repo, api, _) = createRepo()
        api.shouldThrow = Exception("Network error")

        val result = repo.loadCurrentUser()
        assertTrue(result.isFailure)
    }

    // ==================== getUser ====================

    @Test
    fun getUser_returnsSuccess() = runTest {
        val (repo, api, _) = createRepo()
        val user = createTestUser(name = "otheruser")
        api.userResult = user

        val result = repo.getUser("otheruser")
        assertTrue(result.isSuccess)
        assertEquals("otheruser", result.getOrThrow().name)
    }

    @Test
    fun getUser_returnsFailureWhenNull() = runTest {
        val (repo, api, _) = createRepo()
        api.userResult = null

        val result = repo.getUser("nonexistent")
        assertTrue(result.isFailure)
    }

    @Test
    fun getUser_returnsFailureOnException() = runTest {
        val (repo, api, _) = createRepo()
        api.shouldThrow = Exception("Network error")

        val result = repo.getUser("user")
        assertTrue(result.isFailure)
    }

    // ==================== getUserPosts ====================

    @Test
    fun getUserPosts_returnsSuccess() = runTest {
        val (repo, api, _) = createRepo()
        val posts = listOf(createTestPost(id = "p1"), createTestPost(id = "p2"))
        api.userPostsResult = createTestListing(items = posts)

        val result = repo.getUserPosts("testuser")
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().items.size)
    }

    @Test
    fun getUserPosts_returnsFailureOnException() = runTest {
        val (repo, api, _) = createRepo()
        api.shouldThrow = Exception("Network error")

        val result = repo.getUserPosts("testuser")
        assertTrue(result.isFailure)
    }

    // ==================== getUserComments ====================

    @Test
    fun getUserComments_returnsSuccess() = runTest {
        val (repo, api, _) = createRepo()
        val comments = listOf(createTestComment(id = "c1"))
        api.userCommentsResult = createTestListing(items = comments)

        val result = repo.getUserComments("testuser")
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().items.size)
    }

    // ==================== getSavedPosts ====================

    @Test
    fun getSavedPosts_returnsFailureWhenNotLoggedIn() = runTest {
        val (repo, _, _) = createRepo()
        val result = repo.getSavedPosts()
        assertTrue(result.isFailure)
    }

    @Test
    fun getSavedPosts_returnsSuccessWhenLoggedIn() = runTest {
        val (repo, api, _) = createRepo()
        api.meResult = createTestAccount(name = "testuser")
        repo.loadCurrentUser()
        api.savedPostsResult = createTestListing(items = listOf(createTestPost(id = "s1")))

        val result = repo.getSavedPosts()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().items.size)
    }

    // ==================== getSavedComments ====================

    @Test
    fun getSavedComments_returnsFailureWhenNotLoggedIn() = runTest {
        val (repo, _, _) = createRepo()
        val result = repo.getSavedComments()
        assertTrue(result.isFailure)
    }

    @Test
    fun getSavedComments_returnsSuccessWhenLoggedIn() = runTest {
        val (repo, api, _) = createRepo()
        api.meResult = createTestAccount(name = "testuser")
        repo.loadCurrentUser()
        api.savedCommentsResult = createTestListing(items = listOf(createTestComment(id = "sc1")))

        val result = repo.getSavedComments()
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().items.size)
    }

    // ==================== getUpvotedPosts ====================

    @Test
    fun getUpvotedPosts_returnsFailureWhenNotLoggedIn() = runTest {
        val (repo, _, _) = createRepo()
        val result = repo.getUpvotedPosts()
        assertTrue(result.isFailure)
    }

    @Test
    fun getUpvotedPosts_returnsSuccessWhenLoggedIn() = runTest {
        val (repo, api, _) = createRepo()
        api.meResult = createTestAccount(name = "testuser")
        repo.loadCurrentUser()
        api.upvotedPostsResult = createTestListing(items = listOf(createTestPost(id = "u1")))

        val result = repo.getUpvotedPosts()
        assertTrue(result.isSuccess)
    }

    // ==================== getDownvotedPosts ====================

    @Test
    fun getDownvotedPosts_returnsFailureWhenNotLoggedIn() = runTest {
        val (repo, _, _) = createRepo()
        val result = repo.getDownvotedPosts()
        assertTrue(result.isFailure)
    }

    @Test
    fun getDownvotedPosts_returnsSuccessWhenLoggedIn() = runTest {
        val (repo, api, _) = createRepo()
        api.meResult = createTestAccount(name = "testuser")
        repo.loadCurrentUser()
        api.downvotedPostsResult = createTestListing(items = listOf(createTestPost(id = "d1")))

        val result = repo.getDownvotedPosts()
        assertTrue(result.isSuccess)
    }

    // ==================== Auth ====================

    @Test
    fun getAuthorizationUrl_delegatesToAuthManager() {
        val (repo, _, authManager) = createRepo()
        authManager.setClientId("test_id")
        val url = repo.getAuthorizationUrl("state123")
        assertTrue(url.contains("state123"))
    }

    @Test
    fun handleAuthCallback_setsLoggedIn() = runTest {
        val (repo, api, _) = createRepo()
        api.meResult = createTestAccount(name = "testuser")

        val success = repo.handleAuthCallback("auth_code")
        assertTrue(success)
        assertTrue(repo.isLoggedIn.value)
    }

    @Test
    fun logout_clearsState() = runTest {
        val (repo, api, _) = createRepo()
        api.meResult = createTestAccount(name = "testuser")
        repo.loadCurrentUser()

        repo.logout()
        assertFalse(repo.isLoggedIn.value)
        assertNull(repo.currentAccount.value)
    }

    @Test
    fun isLoggedInSync_delegatesToAuthManager() {
        val (repo, _, authManager) = createRepo()
        authManager.fakeIsLoggedIn = true
        assertTrue(repo.isLoggedInSync())
    }
}
