package com.karmakameleon.shared.domain.model

import com.karmakameleon.shared.createTestSubreddit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubredditTest {
    @Test
    fun isPublic_publicType() {
        val sub = createTestSubreddit(subredditType = "public")
        assertTrue(sub.isPublic)
        assertFalse(sub.isPrivate)
        assertFalse(sub.isRestricted)
    }

    @Test
    fun isPrivate_privateType() {
        val sub = createTestSubreddit(subredditType = "private")
        assertTrue(sub.isPrivate)
        assertFalse(sub.isPublic)
        assertFalse(sub.isRestricted)
    }

    @Test
    fun isRestricted_restrictedType() {
        val sub = createTestSubreddit(subredditType = "restricted")
        assertTrue(sub.isRestricted)
        assertFalse(sub.isPublic)
        assertFalse(sub.isPrivate)
    }

    @Test
    fun allowsCommentImage_needsStaticForImagesAndAnimatedForGifs() {
        val staticOnly = createTestSubreddit(allowedMediaInComments = setOf("static", "giphy"))
        assertTrue(staticOnly.allowsCommentImage("image/jpeg"))
        assertTrue(staticOnly.allowsCommentImage("IMAGE/PNG"))
        // GIPHY's GIFs are a different thing from an uploaded GIF.
        assertFalse(staticOnly.allowsCommentImage("image/gif"))

        val animatedOnly = createTestSubreddit(allowedMediaInComments = setOf("animated"))
        assertTrue(animatedOnly.allowsCommentImage("image/gif"))
        assertFalse(animatedOnly.allowsCommentImage("image/webp"))
    }

    @Test
    fun allowsCommentImage_falseForWhatIsntAnImage() {
        val sub = createTestSubreddit(allowedMediaInComments = setOf("static", "animated", "video"))
        assertFalse(sub.allowsCommentImage("video/mp4"))
        assertFalse(createTestSubreddit().allowsCommentImage("image/jpeg"))
    }
}
