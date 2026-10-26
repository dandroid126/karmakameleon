package com.karmakameleon.shared.data.api

import com.karmakameleon.shared.data.api.dto.MoreChildrenResponse
import com.karmakameleon.shared.data.api.dto.ThingData
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.FlairRichtext
import com.karmakameleon.shared.domain.model.MoreComments
import com.karmakameleon.shared.testing.readFixture
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parses comments from saved Reddit responses (see `RedditParserFixtures.kt`). */
class RedditParserCommentTest {
    private val parser = RedditParser(settingsRepository = null)
    private val json = RedditParser.json

    private fun topLevelComments(): List<CommentOrMore> = parser.commentsOrMore(postWithComments()[1].data.children)

    private fun List<CommentOrMore>.comment(id: String): Comment =
        filterIsInstance<CommentOrMore.CommentItem>().map { it.comment }.first { it.id == id }

    // ==================== Comments ====================

    @Test
    fun commentsOrMore_readsTheTopLevelCommentsAndTheMoreItem() {
        val comments = topLevelComments()

        assertEquals(
            listOf("phx1a8k", "phx1t23", "phx23wk"),
            comments.filterIsInstance<CommentOrMore.CommentItem>().map { it.comment.id },
        )
        val more = assertIs<CommentOrMore.More>(comments.last()).more
        assertEquals("phxe3ej", more.id)
        assertEquals("t1_phxe3ej", more.name)
        assertEquals("t3_1xawzaz", more.parentId)
        assertEquals(15, more.count)
        assertEquals(0, more.depth)
        assertEquals(15, more.children.size)
        assertEquals(listOf("phxe3ej", "phx5ns5"), more.children.take(2))
    }

    @Test
    fun commentsOrMore_skipsKindsItDoesNotKnow() {
        val children = postWithComments()[1].data.children
        val unknown = ThingData("t9", buildJsonObject { put("id", "x") })

        assertEquals(topLevelComments(), parser.commentsOrMore(listOf(unknown) + children))
    }

    @Test
    fun comment_skipsRepliesOfKindsItDoesNotKnow() {
        val data = thing("user_comments.json", "phx2cm1")
        val reply = data.with("id" to JsonPrimitive("reply1"))
        val replies =
            buildJsonObject {
                put("kind", "Listing")
                put(
                    "data",
                    buildJsonObject {
                        put(
                            "children",
                            JsonArray(
                                listOf(
                                    buildJsonObject { put("kind", "t9") },
                                    buildJsonObject {
                                        put("kind", "t1")
                                        put("data", reply)
                                    },
                                ),
                            ),
                        )
                    },
                )
            }

        assertEquals(listOf("reply1"), parser.comment(data.with("replies" to replies)).replies.map { it.id })
    }

    @Test
    fun commentsOrMore_postIsTheFirstListing() {
        val post =
            parser.post(
                postWithComments()[0]
                    .data.children
                    .single()
                    .data,
            )

        assertEquals("1xawzaz", post.id)
        assertEquals("First day of the NBA season... Hit it, Mr. Tesh!", post.title)
    }

    @Test
    fun comment_fields() {
        val comment = topLevelComments().comment("phx1a8k")

        assertEquals("t1_phx1a8k", comment.name)
        assertEquals("t3_1xawzaz", comment.parentId)
        assertEquals("t3_1xawzaz", comment.linkId)
        assertEquals("user25", comment.author)
        assertTrue(comment.body.startsWith("B-B-B-B-B-Basketball! Gimme gimme gimme the ball"))
        assertTrue(comment.bodyHtml.startsWith("<div class=\"md\"><p>B-B-B-B-B-Basketball!"))
        assertEquals(582, comment.score)
        assertEquals(1792514321L, comment.createdUtc)
        assertEquals(0, comment.depth)
        assertFalse(comment.isSubmitter)
        assertNull(comment.edited)
        assertEquals("nba", comment.subreddit)
        assertEquals("/r/nba/comments/1xawzaz/first_day_of_the_nba_season_hit_it_mr_tesh/phx1a8k/", comment.permalink)
        assertEquals(":tbr-2: Tampa Bay Raptors", comment.authorFlairText)
        assertEquals("#edeff1", comment.authorFlairBackgroundColor)
        assertEquals(
            listOf(
                FlairRichtext(type = "emoji", text = null, url = "https://emoji.redditmedia.com/jwxh42fch2261_t5_2qo4s/tbr-2"),
                FlairRichtext(type = "text", text = " Tampa Bay Raptors", url = null),
            ),
            comment.authorFlairRichtext,
        )
    }

    @Test
    fun comment_readsNestedRepliesAndTheirMoreItem() {
        val comment = topLevelComments().comment("phx1a8k")

        val reply = comment.replies.single()
        assertEquals("phx9fad", reply.id)
        assertEquals(1, reply.depth)
        assertTrue(reply.authorFlairRichtext.isEmpty())
        assertEquals(listOf("phxbuu4", "phxa34k"), reply.replies.map { it.id })
        assertEquals(
            MoreComments(id = "phxmjnl", name = "t1_phxmjnl", parentId = "t1_phx9fad", count = 1, depth = 2, children = listOf("phxmjnl")),
            reply.moreReplies,
        )
        // Reddit marks a thread that continues on another page with an empty "more" item.
        val continued = reply.replies.first().moreReplies
        assertEquals("_", continued?.id)
        assertEquals(0, continued?.count)
    }

    @Test
    fun comment_editedByThePostsAuthor() {
        val reply = topLevelComments().comment("phx23wk").replies.single()

        assertEquals("phx3eff", reply.id)
        assertTrue(reply.isSubmitter)
        assertEquals(1792515763L, reply.edited)
        // Reddit sends an empty string, not a listing, when a comment has no replies.
        assertTrue(reply.replies.isEmpty())
        assertNull(reply.moreReplies)
    }

    @Test
    fun comment_editedThatIsNotATimestampIsNull() {
        val data = thing("user_comments.json", "phx2cm1")

        assertNull(parser.comment(data.with("edited" to JsonPrimitive(true))).edited)
        assertNull(parser.comment(data.with("edited" to JsonNull)).edited)
    }

    @Test
    fun comment_repliesThatCannotBeReadAreEmpty() {
        val data = thing("user_comments.json", "phx2cm1")
        val notAListing = buildJsonObject { put("data", JsonArray(emptyList())) }

        assertTrue(parser.comment(data.with("replies" to notAListing)).replies.isEmpty())
        assertTrue(parser.comment(data.with("replies" to buildJsonObject { })).replies.isEmpty())
        assertTrue(parser.comment(data.with("replies" to JsonArray(emptyList()))).replies.isEmpty())
    }

    @Test
    fun comment_keepsTheBodyAsTyped() {
        val body = "Tom &amp; Jerry &lt;3 &nbsp; & <b>"
        val data = thing("user_comments.json", "phx2cm1").with("body" to JsonPrimitive(body))

        assertEquals(body, parser.comment(data).body)
    }

    @Test
    fun commentListing_readsAUsersComments() {
        val comments = parser.commentListing(listing("user_comments.json"))

        val comment = comments.items.single()
        assertEquals("phx2cm1", comment.id)
        assertEquals("t1_php39dw", comment.parentId)
        assertEquals("t3_1wovlgy", comment.linkId)
        assertEquals("Kotlin", comment.subreddit)
        assertEquals("Thanks for sharing this, it would help a lot.", comment.body)
        assertEquals("t1_phx2cm1", comments.after)
    }

    @Test
    fun moreChildren_readsTheCommentsAndMoreItems() {
        val response = json.decodeFromString<MoreChildrenResponse>(readFixture("more_children.json"))

        val things = parser.commentsOrMore(response.json.data!!.things)

        assertTrue(response.json.errors.isEmpty())
        val comments = things.filterIsInstance<CommentOrMore.CommentItem>().map { it.comment }
        assertEquals(listOf("phq1mw9", "phriw2f"), comments.map { it.id })
        assertEquals(listOf(1, 2), comments.map { it.depth })
        assertEquals("t1_phq1mw9", comments[1].parentId)
        val more = assertIs<CommentOrMore.More>(things.last()).more
        assertEquals(6, more.depth)
        assertEquals(listOf("phvwk5j"), more.children)
    }

    // ==================== DTOs ====================

    @Test
    fun dtos_defaultWhatRedditLeavesOut() {
        val minimal =
            buildJsonObject {
                put("id", "abc")
                put("name", "t1_abc")
                put("parent_id", "t3_x")
                put("link_id", "t3_x")
                put("author", "user1")
                put("body", "hi")
                put("body_html", "<p>hi</p>")
                put("created", 1.0)
                put("created_utc", 1.0)
                put("subreddit", "test")
            }

        val comment = parser.comment(minimal)

        assertEquals(0, comment.score)
        assertNull(comment.likes)
        assertTrue(comment.replies.isEmpty())
        assertTrue(comment.authorFlairRichtext.isEmpty())
        assertEquals("", comment.permalink)
    }
}
