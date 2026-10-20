package com.karmakameleon.shared.data.api

import com.karmakameleon.shared.data.api.dto.RedditResponse
import com.karmakameleon.shared.data.api.dto.SubredditDto
import com.karmakameleon.shared.data.api.dto.UserDto
import com.karmakameleon.shared.domain.model.MessageType
import com.karmakameleon.shared.readFixture
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parses subreddits, users, messages and the responses to actions (see `RedditParserFixtures.kt`). */
class RedditParserTest {
    private val parser = RedditParser(settingsRepository = null)
    private val json = RedditParser.json

    // ==================== Subreddits ====================

    @Test
    fun subreddit_about() {
        val dto = json.decodeFromString<RedditResponse<SubredditDto>>(readFixture("subreddit_about.json")).data

        val subreddit = parser.subreddit(dto)

        assertEquals("2so2r", subreddit.id)
        assertEquals("t5_2so2r", subreddit.name)
        assertEquals("Kotlin", subreddit.displayName)
        assertEquals("r/Kotlin", subreddit.displayNamePrefixed)
        assertEquals("Kotlin", subreddit.title)
        assertEquals(
            "Discussion about Kotlin, a statically typed programming language for the JVM, Android, JavaScript, and native.",
            subreddit.publicDescription,
        )
        assertTrue(subreddit.description!!.startsWith("Kotlin is a statically typed programming language"))
        assertTrue(subreddit.descriptionHtml!!.startsWith("<!-- SC_OFF --><div class=\"md\">"))
        assertEquals(108935L, subreddit.subscribers)
        assertNull(subreddit.activeUserCount)
        assertEquals(1311424923L, subreddit.createdUtc)
        assertFalse(subreddit.isNsfw)
        // Reddit sends null for these without a logged-in user; they fall back to false.
        assertFalse(subreddit.isSubscribed)
        assertFalse(subreddit.userIsModerator)
        // icon_img is empty, so the community icon is used.
        val communityIcon =
            "https://styles.redditmedia.com/t5_2so2r/styles/communityIcon_mh0wgxpwi9i91.jpg?width=256&s=754d14dc521087882165642f701ce135f112910b"
        assertEquals(communityIcon, subreddit.iconUrl)
        assertEquals(communityIcon, subreddit.communityIcon)
        assertNull(subreddit.bannerUrl)
        assertEquals("/r/Kotlin/", subreddit.url)
        assertEquals("public", subreddit.subredditType)
        assertTrue(subreddit.allowImages)
        assertFalse(subreddit.allowVideos)
    }

    @Test
    fun subreddit_usesTheIconImageWithoutACommunityIcon() {
        val dto = json.decodeFromString<RedditResponse<SubredditDto>>(readFixture("subreddit_about.json")).data

        val subreddit = parser.subreddit(dto.copy(communityIcon = "", iconImg = "https://example.com/a.png?x=1&amp;y=2"))

        assertEquals("https://example.com/a.png?x=1&y=2", subreddit.iconUrl)
    }

    @Test
    fun subredditListing() {
        val subreddits = parser.subredditListing(listing("subreddit_listing.json"))

        val subreddit = subreddits.items.single()
        assertEquals("t5_2qs0k", subreddit.name)
        assertEquals("Home", subreddit.displayName)
        assertEquals(434032L, subreddit.subscribers)
        assertNull(subreddit.iconUrl)
        assertEquals("t5_2qs0k", subreddits.after)
    }

    // ==================== Users ====================

    @Test
    fun user_about() {
        val dto = json.decodeFromString<RedditResponse<UserDto>>(readFixture("user_about.json")).data

        val user = parser.user(dto)

        assertEquals("6l4z3", user.id)
        assertEquals("AutoModerator", user.name)
        assertEquals(1325741068L, user.createdUtc)
        assertEquals(1000, user.linkKarma)
        assertEquals(1000, user.commentKarma)
        assertEquals(2000, user.totalKarma)
        val icon =
            "https://styles.redditmedia.com/t5_1yz875/styles/profileIcon_klqlly9fc4l41.png" +
                "?width=256&height=256&crop=256:256,smart&s=94486fc13b9ca9e154e9e8926e3d8c43ccc80be3"
        assertEquals(icon, user.iconUrl)
        assertTrue(user.isGold)
        assertTrue(user.isMod)
        assertTrue(user.isVerified)
        assertTrue(user.hasVerifiedEmail)
        // Sent as null; falls back to false.
        assertFalse(user.isSuspended)
        assertFalse(user.isEmployee)

        val profile = assertNotNull(user.subreddit)
        assertEquals("u_AutoModerator", profile.displayName)
        assertEquals("", profile.title)
        assertEquals(0, profile.subscribers)
        assertEquals(icon, profile.iconUrl)
        assertFalse(profile.isNsfw)
        // Reddit sends an empty banner_img when there's no banner.
        assertNull(user.bannerUrl)
        assertNull(profile.bannerUrl)
    }

    @Test
    fun user_usesTheSnoovatarWithoutAnIcon() {
        val dto = json.decodeFromString<RedditResponse<UserDto>>(readFixture("user_about.json")).data

        // An unset icon_img is an empty string, like the fixture's snoovatar_img.
        val user = parser.user(dto.copy(iconImg = "", snoovatarImg = "https://example.com/snoo.png", subreddit = null))

        assertEquals("https://example.com/snoo.png", user.iconUrl)
        assertNull(user.subreddit)
        assertNull(user.bannerUrl)
    }

    @Test
    fun user_withoutAnyIconHasNone() {
        val dto = json.decodeFromString<RedditResponse<UserDto>>(readFixture("user_about.json")).data

        assertNull(parser.user(dto.copy(iconImg = "")).iconUrl)
    }

    // ==================== Messages ====================

    @Test
    fun messageListing_readsEachKindOfMessage() {
        val messages = parser.messageListing(listing("message_listing.json"))

        assertEquals(
            listOf(MessageType.COMMENT_REPLY, MessageType.POST_REPLY, MessageType.PRIVATE_MESSAGE, MessageType.MOD_MESSAGE),
            messages.items.map { it.type },
        )
        assertEquals("t4_3c2b1a", messages.after)
    }

    @Test
    fun messageListing_commentReplyFields() {
        val reply = parser.messageListing(listing("message_listing.json")).items.first()

        assertEquals("phx9z01", reply.id)
        assertEquals("t1_phx9z01", reply.name)
        assertEquals("user1", reply.author)
        assertEquals("user2", reply.dest)
        assertEquals("comment reply", reply.subject)
        assertEquals("Coroutines & flows work well here.", reply.body)
        assertEquals("<!-- SC_OFF --><div class=\"md\"><p>Coroutines & flows work well here.</p>\n</div><!-- SC_ON -->", reply.bodyHtml)
        assertEquals(1792515763L, reply.createdUtc)
        assertTrue(reply.isNew)
        assertTrue(reply.wasComment)
        assertEquals("/r/Kotlin/comments/1xa2b3c/is_it_worth_moving_to_kmp/phx9z01/?context=3", reply.context)
        assertEquals("Kotlin", reply.subreddit)
        assertEquals("t1_phx8y00", reply.parentId)
        assertEquals("Is it worth moving to KMP & Compose?", reply.linkTitle)
        assertEquals(true, reply.likes)
        assertTrue(reply.replies.isEmpty())
    }

    @Test
    fun messageListing_modMessageFields() {
        val message = parser.messageListing(listing("message_listing.json")).items.last()

        assertNull(message.author)
        assertEquals("Your post was removed", message.subject)
        assertEquals("t4_3c2b00", message.firstMessageName)
        assertNull(message.linkTitle)
        assertFalse(message.isNew)
    }

    @Test
    fun message_typeComesFromRedditOrWhatTheMessageIsAbout() {
        val data = listing("message_listing.json")

        fun typeOf(
            id: String,
            vararg fields: Pair<String, JsonElement>,
        ): MessageType {
            val thing =
                data.children.first {
                    it.data.jsonObject["id"]
                        ?.jsonPrimitive
                        ?.content == id
                }
            val changed =
                thing.copy(
                    data =
                        thing.data.jsonObject
                            .without("type")
                            .with(*fields),
                )
            return parser
                .messageListing(data.copy(children = listOf(changed)))
                .items
                .single()
                .type
        }

        assertEquals(MessageType.COMMENT_REPLY, typeOf("phx9z01"))
        assertEquals(MessageType.POST_REPLY, typeOf("phx9z01", "context" to JsonPrimitive("")))
        assertEquals(MessageType.MOD_MESSAGE, typeOf("3c2b1a"))
        assertEquals(MessageType.PRIVATE_MESSAGE, typeOf("3c2b1b"))
        assertEquals(MessageType.USERNAME_MENTION, typeOf("phx9z01", "type" to JsonPrimitive("username_mention")))
    }

    // ==================== Responses to actions ====================

    @Test
    fun parseApiErrors_readsTheFirstError() {
        val body = """{"json": {"errors": [["RATELIMIT", "Take a break for 3 minutes.", "ratelimit"], ["OTHER", "x"]]}}"""

        val error = assertNotNull(parser.parseApiErrors(body))

        assertEquals("RATELIMIT", error.errorCode)
        assertEquals("Take a break for 3 minutes.", error.message)
    }

    @Test
    fun parseApiErrors_readsErrorsOutsideTheJsonObject() {
        val error = assertNotNull(parser.parseApiErrors("""{"errors": [["NO_TEXT"]]}"""))

        assertEquals("NO_TEXT", error.errorCode)
        assertEquals("Unknown error", error.message)
    }

    @Test
    fun parseApiErrors_nullWithoutErrors() {
        assertNull(parser.parseApiErrors("""{"json": {"errors": []}}"""))
        assertNull(parser.parseApiErrors("{}"))
        assertNull(parser.parseApiErrors("<html>Too many requests</html>"))
    }

    @Test
    fun submittedComment_readsTheNewComment() {
        val comment = thing("user_comments.json", "phx2cm1")
        val body = """{"json": {"errors": [], "data": {"things": [{"kind": "t1", "data": $comment}]}}}"""

        assertEquals("phx2cm1", parser.submittedComment(body)?.id)
    }

    @Test
    fun submittedComment_nullWhenItCannotBeRead() {
        assertNull(parser.submittedComment("""{"json": {"errors": [], "data": {"things": []}}}"""))
        assertNull(parser.submittedComment("""{"json": {"errors": [], "data": {"things": [{"kind": "t1", "data": {}}]}}}"""))
        assertNull(parser.submittedComment("not json"))
    }

    @Test
    fun submittedPostName_readsTheFullname() {
        val body =
            """{"json": {"errors": [], "data": {"url": "https://www.reddit.com/r/test/comments/abc123/hi/", "id": "abc123", "name": "t3_abc123"}}}"""

        assertEquals("t3_abc123", parser.submittedPostName(body))
    }

    @Test
    fun submittedPostName_nullWhenItCannotBeRead() {
        assertNull(parser.submittedPostName("""{"json": {"errors": []}}"""))
        assertNull(parser.submittedPostName("not json"))
    }
}
