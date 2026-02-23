package com.reader.shared.domain.model

import com.reader.shared.createTestPost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostTest {

    @Test
    fun isTextPost_selfHint() {
        val post = createTestPost(postHint = "self", selfText = "text", url = "https://reddit.com/r/test")
        assertTrue(post.isTextPost)
    }

    @Test
    fun isTextPost_redditUrlWithSelfText() {
        val post = createTestPost(url = "https://www.reddit.com/r/test/comments/abc", selfText = "text")
        assertTrue(post.isTextPost)
    }

    @Test
    fun isTextPost_externalUrl() {
        val post = createTestPost(url = "https://example.com", selfText = null, postHint = null)
        assertFalse(post.isTextPost)
    }

    @Test
    fun isImagePost_imageHint() {
        val post = createTestPost(postHint = "image", url = "https://example.com")
        assertTrue(post.isImagePost)
    }

    @Test
    fun isImagePost_jpgExtension() {
        val post = createTestPost(url = "https://i.imgur.com/abc.jpg")
        assertTrue(post.isImagePost)
    }

    @Test
    fun isImagePost_jpegExtension() {
        val post = createTestPost(url = "https://i.imgur.com/abc.jpeg")
        assertTrue(post.isImagePost)
    }

    @Test
    fun isImagePost_pngExtension() {
        val post = createTestPost(url = "https://i.imgur.com/abc.png")
        assertTrue(post.isImagePost)
    }

    @Test
    fun isImagePost_gifExtension() {
        val post = createTestPost(url = "https://i.imgur.com/abc.gif")
        assertTrue(post.isImagePost)
    }

    @Test
    fun isVideoPost_hostedVideoHint() {
        val post = createTestPost(postHint = "hosted:video")
        assertTrue(post.isVideoPost)
    }

    @Test
    fun isVideoPost_richVideoHint() {
        val post = createTestPost(postHint = "rich:video")
        assertTrue(post.isVideoPost)
    }

    @Test
    fun isVideoPost_notVideo() {
        val post = createTestPost(postHint = "image")
        assertFalse(post.isVideoPost)
    }

    @Test
    fun isGallery_withGalleryData() {
        val post = createTestPost(galleryData = GalleryData(items = listOf(
            GalleryItem(mediaId = "m1", id = 1L, caption = null)
        )))
        assertTrue(post.isGallery)
    }

    @Test
    fun isGallery_withoutGalleryData() {
        val post = createTestPost(galleryData = null)
        assertFalse(post.isGallery)
    }

    @Test
    fun isLinkPost_externalLink() {
        val post = createTestPost(
            url = "https://example.com/article",
            postHint = "link",
            selfText = null,
            galleryData = null
        )
        assertTrue(post.isLinkPost)
    }

    @Test
    fun voteState_upvoted() {
        val post = createTestPost(likes = true)
        assertEquals(VoteState.UPVOTED, post.voteState)
    }

    @Test
    fun voteState_downvoted() {
        val post = createTestPost(likes = false)
        assertEquals(VoteState.DOWNVOTED, post.voteState)
    }

    @Test
    fun voteState_none() {
        val post = createTestPost(likes = null)
        assertEquals(VoteState.NONE, post.voteState)
    }

    @Test
    fun createdInstant_convertsFromEpoch() {
        val post = createTestPost(createdUtc = 1700000000L)
        assertEquals(1700000000L, post.createdInstant.epochSeconds)
    }

    @Test
    fun isCrosspost_whenCrosspostParentSet() {
        val post = createTestPost(crosspostParent = "t3_original", isCrosspost = true)
        assertTrue(post.isCrosspost)
    }

    @Test
    fun isCrosspost_whenNoCrosspostParent() {
        val post = createTestPost(crosspostParent = null, isCrosspost = false)
        assertFalse(post.isCrosspost)
    }

    @Test
    fun crosspostParentSubreddit_isNullByDefault() {
        val post = createTestPost()
        assertEquals(null, post.crosspostParentSubreddit)
    }

    @Test
    fun crosspostParentSubreddit_storesValue() {
        val post = createTestPost(
            isCrosspost = true,
            crosspostParent = "t3_original",
            crosspostParentSubreddit = "originalsubreddit"
        )
        assertEquals("originalsubreddit", post.crosspostParentSubreddit)
    }

    @Test
    fun crosspostParentPermalink_isNullByDefault() {
        val post = createTestPost()
        assertEquals(null, post.crosspostParentPermalink)
    }

    @Test
    fun crosspostParentPermalink_storesValue() {
        val post = createTestPost(
            isCrosspost = true,
            crosspostParent = "t3_original",
            crosspostParentPermalink = "/r/originalsubreddit/comments/orig123/original_title/"
        )
        assertEquals("/r/originalsubreddit/comments/orig123/original_title/", post.crosspostParentPermalink)
    }

    // ==================== isDeleted ====================

    @Test
    fun isDeleted_whenAuthorIsDeletedMarker() {
        val post = createTestPost(author = "[deleted]")
        assertTrue(post.isDeleted)
    }

    @Test
    fun isDeleted_whenAuthorIsNormal() {
        val post = createTestPost(author = "someuser")
        assertFalse(post.isDeleted)
    }

    // ==================== isVotingDisabled ====================

    @Test
    fun isVotingDisabled_whenLocked() {
        val post = createTestPost(isLocked = true)
        assertTrue(post.isVotingDisabled)
    }

    @Test
    fun isVotingDisabled_whenDeleted() {
        val post = createTestPost(author = "[deleted]")
        assertTrue(post.isVotingDisabled)
    }

    @Test
    fun isVotingDisabled_whenArchived() {
        val post = createTestPost(isArchived = true)
        assertTrue(post.isVotingDisabled)
    }

    @Test
    fun isVotingDisabled_whenNormal() {
        val post = createTestPost(isLocked = false, isArchived = false, author = "someuser")
        assertFalse(post.isVotingDisabled)
    }

    @Test
    fun isVotingDisabled_whenLockedAndArchived() {
        val post = createTestPost(isLocked = true, isArchived = true)
        assertTrue(post.isVotingDisabled)
    }

    // ==================== votingDisabledReason ====================

    @Test
    fun votingDisabledReason_whenLocked() {
        val post = createTestPost(isLocked = true)
        assertEquals("This post is locked and cannot be voted on", post.votingDisabledReason)
    }

    @Test
    fun votingDisabledReason_whenDeleted() {
        val post = createTestPost(author = "[deleted]")
        assertEquals("This post has been deleted and cannot be voted on", post.votingDisabledReason)
    }

    @Test
    fun votingDisabledReason_whenArchived() {
        val post = createTestPost(isArchived = true)
        assertEquals("This post has been archived and cannot be voted on", post.votingDisabledReason)
    }

    @Test
    fun votingDisabledReason_whenNormal() {
        val post = createTestPost(isLocked = false, isArchived = false, author = "someuser")
        assertNull(post.votingDisabledReason)
    }

    @Test
    fun votingDisabledReason_lockedTakesPriorityOverDeleted() {
        val post = createTestPost(isLocked = true, author = "[deleted]")
        assertEquals("This post is locked and cannot be voted on", post.votingDisabledReason)
    }

    // ==================== isCommentingDisabled ====================

    @Test
    fun isCommentingDisabled_whenLocked() {
        val post = createTestPost(isLocked = true)
        assertTrue(post.isCommentingDisabled)
    }

    @Test
    fun isCommentingDisabled_whenDeleted() {
        val post = createTestPost(author = "[deleted]")
        assertFalse(post.isCommentingDisabled)
    }

    @Test
    fun isCommentingDisabled_whenArchived() {
        val post = createTestPost(isArchived = true)
        assertTrue(post.isCommentingDisabled)
    }

    @Test
    fun isCommentingDisabled_whenNormal() {
        val post = createTestPost(isLocked = false, isArchived = false, author = "someuser")
        assertFalse(post.isCommentingDisabled)
    }

    // ==================== commentingDisabledReason ====================

    @Test
    fun commentingDisabledReason_whenLocked() {
        val post = createTestPost(isLocked = true)
        assertEquals("This post is locked and cannot be commented on", post.commentingDisabledReason)
    }

    @Test
    fun commentingDisabledReason_whenDeleted() {
        val post = createTestPost(author = "[deleted]")
        assertNull(post.commentingDisabledReason)
    }

    @Test
    fun commentingDisabledReason_whenArchived() {
        val post = createTestPost(isArchived = true)
        assertEquals("This post has been archived and cannot be commented on", post.commentingDisabledReason)
    }

    @Test
    fun commentingDisabledReason_whenNormal() {
        val post = createTestPost(isLocked = false, isArchived = false, author = "someuser")
        assertNull(post.commentingDisabledReason)
    }
}
