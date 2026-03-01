package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.createTestPost
import com.karmakameleon.shared.domain.model.NsfwHistoryMode
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadPostsRepositoryTest {

    private fun createRepo(settings: MapSettings = MapSettings()) = ReadPostsRepository(settings)

    @Test
    fun readPostIds_defaultsToEmpty() {
        val repo = createRepo()
        assertTrue(repo.readPostIds.value.isEmpty())
    }

    @Test
    fun isRead_returnsFalseForUnreadPost() {
        val repo = createRepo()
        assertFalse(repo.isRead("post1"))
    }

    @Test
    fun markAsRead_marksPostAsRead() {
        val repo = createRepo()
        val post = createTestPost(id = "post1")
        repo.markAsRead(post, NsfwHistoryMode.SAVE_ALL)
        assertTrue(repo.isRead("post1"))
    }

    @Test
    fun markAsRead_multiplePosts() {
        val repo = createRepo()
        repo.markAsRead(createTestPost(id = "post1"), NsfwHistoryMode.SAVE_ALL)
        repo.markAsRead(createTestPost(id = "post2"), NsfwHistoryMode.SAVE_ALL)
        repo.markAsRead(createTestPost(id = "post3"), NsfwHistoryMode.SAVE_ALL)
        assertTrue(repo.isRead("post1"))
        assertTrue(repo.isRead("post2"))
        assertTrue(repo.isRead("post3"))
        assertFalse(repo.isRead("post4"))
    }

    @Test
    fun markAsRead_updatesStateFlow() {
        val repo = createRepo()
        val post = createTestPost(id = "post1")
        repo.markAsRead(post, NsfwHistoryMode.SAVE_ALL)
        assertTrue(repo.readPostIds.value.contains("post1"))
    }

    @Test
    fun markAsRead_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = ReadPostsRepository(settings)
        repo1.markAsRead(createTestPost(id = "post1"), NsfwHistoryMode.SAVE_ALL)
        repo1.markAsRead(createTestPost(id = "post2"), NsfwHistoryMode.SAVE_ALL)

        val repo2 = ReadPostsRepository(settings)
        assertTrue(repo2.isRead("post1"))
        assertTrue(repo2.isRead("post2"))
    }

    @Test
    fun markAsRead_idempotent() {
        val repo = createRepo()
        val post = createTestPost(id = "post1")
        repo.markAsRead(post, NsfwHistoryMode.SAVE_ALL)
        repo.markAsRead(post, NsfwHistoryMode.SAVE_ALL)
        assertTrue(repo.isRead("post1"))
        assertTrue(repo.readPostIds.value.size == 1)
    }

    // ==================== NSFW History Mode ====================

    @Test
    fun markAsRead_saveAll_savesNsfwPost() {
        val repo = createRepo()
        val post = createTestPost(id = "nsfw1", isNsfw = true)
        repo.markAsRead(post, NsfwHistoryMode.SAVE_ALL)
        assertTrue(repo.isRead("nsfw1"))
    }

    @Test
    fun markAsRead_saveAll_savesNonNsfwPost() {
        val repo = createRepo()
        val post = createTestPost(id = "post1", isNsfw = false)
        repo.markAsRead(post, NsfwHistoryMode.SAVE_ALL)
        assertTrue(repo.isRead("post1"))
    }

    @Test
    fun markAsRead_saveAll_savesNsfwSubredditPost() {
        val repo = createRepo()
        val post = createTestPost(id = "nsfw1", isNsfw = false, subredditIsNsfw = true)
        repo.markAsRead(post, NsfwHistoryMode.SAVE_ALL)
        assertTrue(repo.isRead("nsfw1"))
    }

    // DONT_SAVE_NSFW_SUBREDDITS: skips posts from NSFW subreddits, not NSFW posts in non-NSFW subreddits

    @Test
    fun markAsRead_dontSaveNsfwSubreddits_skipsPostFromNsfwSubreddit() {
        val repo = createRepo()
        val post = createTestPost(id = "nsfw1", isNsfw = false, subredditIsNsfw = true)
        repo.markAsRead(post, NsfwHistoryMode.DONT_SAVE_NSFW_SUBREDDITS)
        assertFalse(repo.isRead("nsfw1"))
    }

    @Test
    fun markAsRead_dontSaveNsfwSubreddits_savesNsfwPostInNonNsfwSubreddit() {
        val repo = createRepo()
        val post = createTestPost(id = "post1", isNsfw = true, subredditIsNsfw = false)
        repo.markAsRead(post, NsfwHistoryMode.DONT_SAVE_NSFW_SUBREDDITS)
        assertTrue(repo.isRead("post1"))
    }

    @Test
    fun markAsRead_dontSaveNsfwSubreddits_savesNonNsfwPost() {
        val repo = createRepo()
        val post = createTestPost(id = "post1", isNsfw = false, subredditIsNsfw = false)
        repo.markAsRead(post, NsfwHistoryMode.DONT_SAVE_NSFW_SUBREDDITS)
        assertTrue(repo.isRead("post1"))
    }

    // DONT_SAVE_ANY_NSFW: skips NSFW posts only (subreddit NSFW status is irrelevant)

    @Test
    fun markAsRead_dontSaveAnyNsfw_skipsNsfwPost() {
        val repo = createRepo()
        val post = createTestPost(id = "nsfw1", isNsfw = true, subredditIsNsfw = false)
        repo.markAsRead(post, NsfwHistoryMode.DONT_SAVE_ANY_NSFW)
        assertFalse(repo.isRead("nsfw1"))
    }

    @Test
    fun markAsRead_dontSaveAnyNsfw_savesNonNsfwPostFromNsfwSubreddit() {
        val repo = createRepo()
        val post = createTestPost(id = "post1", isNsfw = false, subredditIsNsfw = true)
        repo.markAsRead(post, NsfwHistoryMode.DONT_SAVE_ANY_NSFW)
        assertTrue(repo.isRead("post1"))
    }

    @Test
    fun markAsRead_dontSaveAnyNsfw_savesNonNsfwPost() {
        val repo = createRepo()
        val post = createTestPost(id = "post1", isNsfw = false, subredditIsNsfw = false)
        repo.markAsRead(post, NsfwHistoryMode.DONT_SAVE_ANY_NSFW)
        assertTrue(repo.isRead("post1"))
    }

    // DONT_SAVE_NSFW_SUBREDDITS: unknown subreddit status - conservative behavior

    @Test
    fun markAsRead_dontSaveNsfwSubreddits_unknownSubreddit_skipsNsfwPost() {
        val repo = createRepo()
        val post = createTestPost(id = "nsfw1", isNsfw = true, subredditIsNsfw = false, subredditNsfwKnown = false)
        repo.markAsRead(post, NsfwHistoryMode.DONT_SAVE_NSFW_SUBREDDITS)
        assertFalse(repo.isRead("nsfw1"))
    }

    @Test
    fun markAsRead_dontSaveNsfwSubreddits_unknownSubreddit_savesNonNsfwPost() {
        val repo = createRepo()
        val post = createTestPost(id = "post1", isNsfw = false, subredditIsNsfw = false, subredditNsfwKnown = false)
        repo.markAsRead(post, NsfwHistoryMode.DONT_SAVE_NSFW_SUBREDDITS)
        assertTrue(repo.isRead("post1"))
    }

    // ==================== Purge NSFW Read Posts ====================

    @Test
    fun purgeNsfwReadPosts_removesNsfwPosts() {
        val repo = createRepo()
        repo.markAsRead(createTestPost(id = "nsfw1", isNsfw = true), NsfwHistoryMode.SAVE_ALL)
        repo.markAsRead(createTestPost(id = "post1", isNsfw = false), NsfwHistoryMode.SAVE_ALL)
        repo.purgeNsfwReadPosts()
        assertFalse(repo.isRead("nsfw1"))
        assertTrue(repo.isRead("post1"))
    }

    @Test
    fun purgeNsfwReadPosts_removesNsfwSubredditPosts() {
        val repo = createRepo()
        repo.markAsRead(createTestPost(id = "nsfw1", subredditIsNsfw = true), NsfwHistoryMode.SAVE_ALL)
        repo.markAsRead(createTestPost(id = "post1", subredditIsNsfw = false), NsfwHistoryMode.SAVE_ALL)
        repo.purgeNsfwReadPosts()
        assertFalse(repo.isRead("nsfw1"))
        assertTrue(repo.isRead("post1"))
    }

    @Test
    fun purgeNsfwReadPosts_updatesStateFlow() {
        val repo = createRepo()
        repo.markAsRead(createTestPost(id = "nsfw1", isNsfw = true), NsfwHistoryMode.SAVE_ALL)
        repo.markAsRead(createTestPost(id = "post1", isNsfw = false), NsfwHistoryMode.SAVE_ALL)
        repo.purgeNsfwReadPosts()
        assertFalse(repo.readPostIds.value.contains("nsfw1"))
        assertTrue(repo.readPostIds.value.contains("post1"))
    }

    @Test
    fun purgeNsfwReadPosts_persistsAcrossInstances() {
        val settings = MapSettings()
        val repo1 = ReadPostsRepository(settings)
        repo1.markAsRead(createTestPost(id = "nsfw1", isNsfw = true), NsfwHistoryMode.SAVE_ALL)
        repo1.markAsRead(createTestPost(id = "post1", isNsfw = false), NsfwHistoryMode.SAVE_ALL)
        repo1.purgeNsfwReadPosts()

        val repo2 = ReadPostsRepository(settings)
        assertFalse(repo2.isRead("nsfw1"))
        assertTrue(repo2.isRead("post1"))
    }

    // ==================== isRead with historyMode override ====================

    @Test
    fun isRead_withHistoryMode_saveAll_returnsTrue_forNsfwPost() {
        val repo = createRepo()
        val post = createTestPost(id = "nsfw1", isNsfw = true)
        repo.markAsRead(post, NsfwHistoryMode.SAVE_ALL)
        assertTrue(repo.isRead(post, NsfwHistoryMode.SAVE_ALL))
    }

    @Test
    fun isRead_withHistoryMode_dontSaveAnyNsfw_returnsFalse_forNsfwPost() {
        val repo = createRepo()
        val post = createTestPost(id = "nsfw1", isNsfw = true)
        repo.markAsRead(post, NsfwHistoryMode.SAVE_ALL)
        assertFalse(repo.isRead(post, NsfwHistoryMode.DONT_SAVE_ANY_NSFW))
    }

    @Test
    fun isRead_withHistoryMode_dontSaveNsfwSubreddits_returnsFalse_forNsfwSubredditPost() {
        val repo = createRepo()
        val post = createTestPost(id = "nsfw1", subredditIsNsfw = true)
        repo.markAsRead(post, NsfwHistoryMode.SAVE_ALL)
        assertFalse(repo.isRead(post, NsfwHistoryMode.DONT_SAVE_NSFW_SUBREDDITS))
    }

    @Test
    fun isRead_withHistoryMode_dontSaveNsfwSubreddits_returnsTrue_forNsfwPostInNonNsfwSubreddit() {
        val repo = createRepo()
        val post = createTestPost(id = "post1", isNsfw = true, subredditIsNsfw = false)
        repo.markAsRead(post, NsfwHistoryMode.SAVE_ALL)
        assertTrue(repo.isRead(post, NsfwHistoryMode.DONT_SAVE_NSFW_SUBREDDITS))
    }

    @Test
    fun isRead_withHistoryMode_returnsTrue_forNonNsfwPost() {
        val repo = createRepo()
        val post = createTestPost(id = "post1", isNsfw = false, subredditIsNsfw = false)
        repo.markAsRead(post, NsfwHistoryMode.SAVE_ALL)
        assertTrue(repo.isRead(post, NsfwHistoryMode.DONT_SAVE_ANY_NSFW))
    }

    // ==================== Storage Format Backward Compatibility ====================

    @Test
    fun loadReadPostIds_backwardCompatWithLegacyFormat() {
        val settings = MapSettings()
        settings.putString("read_post_ids", "post1,post2,post3")
        val repo = ReadPostsRepository(settings)
        assertTrue(repo.isRead("post1"))
        assertTrue(repo.isRead("post2"))
        assertTrue(repo.isRead("post3"))
    }

    @Test
    fun markAsRead_persistsWithNewFormat() {
        val settings = MapSettings()
        val repo1 = ReadPostsRepository(settings)
        repo1.markAsRead(createTestPost(id = "post1", isNsfw = true, subredditIsNsfw = false), NsfwHistoryMode.SAVE_ALL)

        val repo2 = ReadPostsRepository(settings)
        assertTrue(repo2.isRead("post1"))
    }
}
