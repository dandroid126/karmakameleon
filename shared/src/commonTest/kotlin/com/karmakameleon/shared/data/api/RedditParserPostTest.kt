package com.karmakameleon.shared.data.api

import com.karmakameleon.shared.data.api.dto.ThingData
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.domain.model.CommentSort
import com.karmakameleon.shared.domain.model.ImageSource
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.RedditVideo
import com.russhwolf.settings.MapSettings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parses posts from saved Reddit responses (see `RedditParserFixtures.kt`). */
class RedditParserPostTest {
    private val parser = RedditParser(settingsRepository = null)

    private fun post(
        id: String,
        fixture: String = "post_listing.json",
    ): Post = parser.post(thing(fixture, id))

    // ==================== Posts ====================

    @Test
    fun postListing_keepsEveryPostInOrder() {
        val posts = parser.postListing(listing("post_listing.json"))

        assertEquals(listOf("1x65iks", "1xavut5", "1x63bfs", "1x8c7mj"), posts.items.map { it.id })
        assertNull(posts.after)
        assertNull(posts.before)
        assertEquals(2, posts.dist)
    }

    @Test
    fun postListing_skipsThingsThatAreNotPosts() {
        val data = listing("post_listing.json")
        val withComment = data.copy(children = data.children + ThingData("t1", thing("user_comments.json", "phx2cm1")))

        assertEquals(4, parser.postListing(withComment).items.size)
    }

    @Test
    fun post_selfPost() {
        val post = post("1x65iks")

        assertEquals("t3_1x65iks", post.name)
        assertEquals("Enter the Pics Halloween Photo Contest!", post.title)
        assertEquals("pics", post.subreddit)
        assertEquals("t5_2qh0u", post.subredditId)
        assertEquals("/r/pics/comments/1x65iks/enter_the_pics_halloween_photo_contest/", post.permalink)
        assertTrue(post.selfText!!.startsWith("**It's a Halloween Photo Contest!**\n\nPost your best costume,"))
        assertTrue(post.selfTextHtml!!.startsWith("<!-- SC_OFF --><div class=\"md\"><p><strong>"))
        // Reddit sends "self" for a self post's thumbnail, which isn't an image.
        assertNull(post.thumbnail)
        assertNull(post.preview)
        assertEquals(22, post.score)
        assertEquals(0.88f, post.upvoteRatio)
        assertEquals(11, post.numComments)
        assertEquals(1792018110L, post.createdUtc)
        assertTrue(post.isStickied)
        assertEquals("moderator", post.distinguished)
        assertEquals("Contest", post.linkFlairText)
        assertEquals("🍿", post.authorFlairText)
        assertEquals(CommentSort.NEW, post.suggestedSort)
        assertEquals("self.pics", post.domain)
        assertFalse(post.isCrosspost)
    }

    @Test
    fun post_imagePost() {
        val post = post("1xavut5")

        assertEquals("https://i.redd.it/sawv7m0e9nwh1.jpeg", post.url)
        assertEquals("image", post.postHint)
        assertEquals(
            "https://preview.redd.it/sawv7m0e9nwh1.jpeg?width=140&height=93&auto=webp&s=6dfbf1cf7eb88a4ebf866b5aaf601410aaae7116",
            post.thumbnail,
        )
        assertEquals(140, post.thumbnailWidth)
        assertEquals(93, post.thumbnailHeight)
        assertEquals("dark", post.linkFlairTextColor)
        assertFalse(post.isStickied)
        assertNull(post.suggestedSort)

        val preview = assertNotNull(post.preview)
        assertTrue(preview.enabled)
        val image = preview.images.single()
        assertEquals(
            ImageSource("https://preview.redd.it/sawv7m0e9nwh1.jpeg?auto=webp&s=a9f5d091b415e4909d7d9bb38d96923979568fe5", 2400, 1600),
            image.source,
        )
        assertEquals(6, image.resolutions.size)
        assertEquals(
            ImageSource(
                "https://preview.redd.it/sawv7m0e9nwh1.jpeg?width=108&crop=smart&auto=webp&s=63e547069fe9730f9e0caf249bf1638fa94b05c1",
                108,
                72,
            ),
            image.resolutions.first(),
        )
        assertNull(image.mp4Url)
    }

    @Test
    fun post_videoPost() {
        val post = post("1x63bfs")

        assertEquals("https://v.redd.it/d77amxm51ivh1", post.url)
        assertEquals("hosted:video", post.postHint)
        assertEquals(
            RedditVideo(
                fallbackUrl = "https://v.redd.it/d77amxm51ivh1/CMAF_720.mp4?source=fallback",
                height = 1280,
                width = 720,
                duration = 26,
                isGif = false,
            ),
            post.media?.redditVideo,
        )
        assertEquals(
            720,
            post.preview
                ?.images
                ?.single()
                ?.source
                ?.width,
        )
    }

    @Test
    fun post_galleryPost() {
        val post = post("1x8c7mj")

        assertEquals("https://www.reddit.com/gallery/1x8c7mj", post.url)
        val items = assertNotNull(post.galleryData).items
        assertEquals(listOf("j1kjjna0k1wh1", "0gf0sya0k1wh1", "dz7ii0b0k1wh1", "uu5id2b0k1wh1"), items.map { it.mediaId })
        assertEquals(1073160903L, items.first().id)
        assertEquals(
            "https://preview.redd.it/j1kjjna0k1wh1.jpg?width=3024&format=pjpg&auto=webp&s=ed8f889f36f19132b42e7c48c37871a2a48b5d12",
            items.first().url,
        )
        assertTrue(items.none { it.isVideo })
        assertTrue(items.all { it.gifUrl == null })
    }

    @Test
    fun post_gifPreviewHasItsVideoAndMp4() {
        val image =
            thing("post_listing.json", "1xavut5")["preview"]!!
                .jsonObject["images"]!!
                .jsonArray
                .single()
                .jsonObject
        val mp4 =
            buildJsonObject {
                put(
                    "source",
                    buildJsonObject {
                        put("url", "https://preview.redd.it/a.gif?format=mp4&amp;s=1")
                        put("width", 320)
                        put("height", 240)
                    },
                )
            }
        val preview =
            buildJsonObject {
                put("images", JsonArray(listOf(image.with("variants" to buildJsonObject { put("mp4", mp4) }))))
                put("enabled", true)
                put(
                    "reddit_video_preview",
                    buildJsonObject {
                        put("fallback_url", "https://v.redd.it/abc/DASH_480.mp4")
                        put("height", 480)
                        put("width", 640)
                        put("duration", 4)
                        put("is_gif", true)
                    },
                )
            }

        val result = assertNotNull(parser.post(thing("post_listing.json", "1xavut5").with("preview" to preview)).preview)

        assertEquals("https://preview.redd.it/a.gif?format=mp4&s=1", result.images.single().mp4Url)
        assertEquals(
            RedditVideo(fallbackUrl = "https://v.redd.it/abc/DASH_480.mp4", height = 480, width = 640, duration = 4, isGif = true),
            result.redditVideoPreview,
        )
    }

    @Test
    fun post_crosspostTakesTheVideoFromItsParent() {
        val post = post("1xay7fk", "crosspost_listing.json")

        assertTrue(post.isCrosspost)
        assertEquals("t3_1xalxai", post.crosspostParent)
        assertEquals("cats", post.crosspostParentSubreddit)
        assertEquals("/r/cats/comments/1xalxai/purrsonal_trainer/", post.crosspostParentPermalink)
        assertEquals("https://v.redd.it/z6l01py2ukwh1/CMAF_720.mp4?source=fallback", post.media?.redditVideo?.fallbackUrl)
        assertEquals(27, post.media?.redditVideo?.duration)
        // Reddit sends "default" when there's no thumbnail.
        assertNull(post.thumbnail)
    }

    @Test
    fun post_crosspostTakesTheGalleryFromItsParent() {
        val post = post("1xaimd5", "crosspost_listing.json")

        val items = assertNotNull(post.galleryData).items
        assertEquals(listOf("vqzgwq7hojwh1", "p7v3c17hojwh1", "ie4as37hojwh1", "lu1slg7hojwh1"), items.map { it.mediaId })
        assertEquals(
            "https://preview.redd.it/vqzgwq7hojwh1.jpg?width=4284&format=pjpg&auto=webp&s=db0e62c6cb0ae3a13430fa743293ed4eb4c7ef51",
            items.first().url,
        )
        assertEquals("pugs", post.crosspostParentSubreddit)
    }

    @Test
    fun post_crosspostWithoutItsOwnPreviewTakesTheParents() {
        val data = thing("crosspost_listing.json", "1xay7fk").without("preview")

        val preview = assertNotNull(parser.post(data).preview)

        assertEquals(
            405,
            preview.images
                .single()
                .source.width,
        )
    }

    @Test
    fun post_decodesHtmlEntitiesInTheTitle() {
        val data =
            thing(
                "post_listing.json",
                "1xavut5",
            ).with("title" to JsonPrimitive("Tom &amp; Jerry &lt;3 &quot;cats&quot; &#39;n&nbsp;dogs&gt;"))

        assertEquals("Tom & Jerry <3 \"cats\" 'n dogs>", parser.post(data).title)
    }

    @Test
    fun post_resolvesRedditMediaLinksToTheImage() {
        val data =
            thing("post_listing.json", "1xavut5").with(
                "url" to JsonPrimitive("https://www.reddit.com/media?url=https%3A%2F%2Fi.redd.it%2Fabc.jpg%zz"),
            )

        // An escape that isn't hex is left as it is.
        assertEquals("https://i.redd.it/abc.jpg%zz", parser.post(data).url)
    }

    @Test
    fun post_ignoresASuggestedSortTheAppDoesNotKnow() {
        val data = thing("post_listing.json", "1x65iks").with("suggested_sort" to JsonPrimitive("random"))

        assertNull(parser.post(data).suggestedSort)
    }

    @Test
    fun post_galleryWithAnAnimatedImageUsesItsMp4() {
        val metadata =
            buildJsonObject {
                put(
                    "j1kjjna0k1wh1",
                    buildJsonObject {
                        put("status", "valid")
                        put("e", "AnimatedImage")
                        put("m", "image/gif")
                        put(
                            "s",
                            buildJsonObject {
                                put("y", 200)
                                put("x", 300)
                                put("gif", "https://i.redd.it/j1kjjna0k1wh1.gif")
                                put("mp4", "https://preview.redd.it/j1kjjna0k1wh1.gif?format=mp4&amp;s=1")
                            },
                        )
                    },
                )
            }
        val data = thing("post_listing.json", "1x8c7mj").with("media_metadata" to metadata)

        val items = assertNotNull(parser.post(data).galleryData).items

        // The other three images have no metadata now, so they're dropped.
        val item = items.single()
        assertEquals("https://preview.redd.it/j1kjjna0k1wh1.gif?format=mp4&s=1", item.url)
        assertTrue(item.isVideo)
        assertEquals("https://i.redd.it/j1kjjna0k1wh1.gif", item.gifUrl)
    }

    @Test
    fun post_galleryWithoutMetadataKeepsItsItems() {
        val data = thing("post_listing.json", "1x8c7mj").without("media_metadata")

        val items = assertNotNull(parser.post(data).galleryData).items

        assertEquals(4, items.size)
        assertTrue(items.all { it.url == null })
    }

    @Test
    fun post_subredditNsfwComesFromTheCache() {
        val settings = SettingsRepository(MapSettings())
        settings.setSubredditNsfw("Pics", isNsfw = true)
        val parser = RedditParser(settings)

        val pics = parser.post(thing("post_listing.json", "1xavut5"))
        val aww = parser.post(thing("post_listing.json", "1x63bfs"))

        assertTrue(pics.subredditNsfwKnown)
        assertTrue(pics.subredditIsNsfw)
        assertFalse(aww.subredditNsfwKnown)
        assertFalse(aww.subredditIsNsfw)
    }

    @Test
    fun postSubreddits_listsEachSubredditOnce() {
        assertEquals(listOf("pics", "aww"), parser.postSubreddits(listing("post_listing.json")))
    }

    @Test
    fun postSubreddits_skipsPostsThatCannotBeRead() {
        val data = listing("post_listing.json")
        val broken = ThingData("t3", buildJsonObject { put("id", "broken") })

        assertEquals(listOf("pics", "aww"), parser.postSubreddits(data.copy(children = listOf(broken) + data.children)))
    }
}
