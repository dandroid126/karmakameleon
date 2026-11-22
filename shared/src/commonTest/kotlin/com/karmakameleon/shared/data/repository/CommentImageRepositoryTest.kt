package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.RedditApiException
import com.karmakameleon.shared.data.api.thing
import com.karmakameleon.shared.data.media.ImageFile
import com.karmakameleon.shared.data.media.ImageFiles
import com.karmakameleon.shared.data.media.ImageReadException
import com.karmakameleon.shared.testing.MockReddit
import com.karmakameleon.shared.testing.form
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class CommentImageRepositoryTest {
    private val reddit = MockReddit()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val markdown = MarkdownRepository(UnconfinedTestDispatcher())

    private val photo = ImageFile("photo.jpeg", "image/jpeg", byteArrayOf(1, 2, 3))

    private val imageFiles =
        object : ImageFiles {
            override suspend fun read(location: String): ImageFile =
                if (location == "content://photo") photo else throw ImageReadException("That file isn't an image")
        }

    private fun TestScope.imageRepo() = CommentImageRepository(reddit.api(ioDispatcher = StandardTestDispatcher(testScheduler)), imageFiles)

    private fun TestScope.commentRepo() = CommentRepository(reddit.api(ioDispatcher = StandardTestDispatcher(testScheduler)), markdown)

    private fun respondWithDocument(document: String) {
        reddit.respond("/api/convert_rte_body_format", """{"output": $document}""")
    }

    // ==================== Reading and uploading ====================

    @Test
    fun read_givesTheImage() =
        runTest {
            assertSame(photo, imageRepo().read("content://photo").getOrThrow())
        }

    @Test
    fun read_failsWithTheReason() =
        runTest {
            val error = imageRepo().read("content://song").exceptionOrNull()

            assertIs<ImageReadException>(error)
            assertEquals("That file isn't an image", error.message)
        }

    @Test
    fun upload_givesTheAssetId() =
        runTest {
            reddit.respondToImageUpload("asset1")

            assertEquals("asset1", imageRepo().upload(photo).getOrThrow())
            assertEquals("photo.jpeg", reddit.request.form()["filepath"])
        }

    @Test
    fun upload_failsWhenTheUploadIsRejected() =
        runTest {
            reddit.respondToImageUpload("asset1")
            reddit.respond(MockReddit.UPLOAD_PATH, "", HttpStatusCode.Forbidden)

            assertIs<IllegalStateException>(imageRepo().upload(photo).exceptionOrNull())
        }

    // ==================== Submitting a comment with images ====================

    @Test
    fun submitComment_withImagesSendsTheConvertedRichtext() =
        runTest {
            val document = """{"document": [{"e": "par", "c": [{"e": "text", "t": "Look"}]}, {"e": "img", "id": "asset1"}]}"""
            respondWithDocument(document)
            reddit.respond("/api/comment", thing("user_comments.json", "phx2cm1").toString())

            val comment = commentRepo().submitComment("t3_post", "Look\n\n![img](asset1)", setOf("asset1")).getOrThrow()

            assertEquals("phx2cm1", comment.id)
            val (convert, post) = reddit.requests
            assertEquals("Look\n\n![img](asset1)", convert.form()["markdown_text"])
            assertEquals(Json.parseToJsonElement(document), Json.parseToJsonElement(post.form()["richtext_json"]!!))
            assertNull(post.form()["text"])
        }

    @Test
    fun submitComment_findsImagesNestedInTheDocument() =
        runTest {
            respondWithDocument(
                """{"document": [{"e": "blockquote", "c": [{"e": "img", "id": "asset2"}]}, {"e": "img", "id": "asset1"}]}""",
            )
            reddit.respond("/api/comment", thing("user_comments.json", "phx2cm1").toString())

            val result = commentRepo().submitComment("t3_post", "text", setOf("asset1", "asset2"))

            assertEquals("phx2cm1", result.getOrThrow().id)
        }

    @Test
    fun submitComment_doesntPostWhenAnImageIsMissingFromTheConvertedText() =
        runTest {
            // The user broke the image's markdown, so Reddit converted it without the image.
            respondWithDocument("""{"document": [{"e": "par", "c": [{"e": "text", "t": "Look ![img(asset1)"}]}]}""")

            val error = commentRepo().submitComment("t3_post", "Look ![img(asset1)", setOf("asset1")).exceptionOrNull()

            assertEquals("An image is missing from the comment's text", error?.message)
            assertEquals(listOf("/api/convert_rte_body_format"), reddit.requests.map { it.url.encodedPath })
        }

    @Test
    fun submitComment_passesOnTheSubredditRefusingTheImage() =
        runTest {
            respondWithDocument("""{"document": [{"e": "img", "id": "asset1"}]}""")
            reddit.respond(
                "/api/comment",
                """{"json": {"errors": [["META_MEDIA_TYPE_NOT_ALLOWED", "This community doesn't allow that.", "richtext_json"]]}}""",
            )

            val error = commentRepo().submitComment("t3_post", "![img](asset1)", setOf("asset1")).exceptionOrNull()

            assertEquals("META_MEDIA_TYPE_NOT_ALLOWED", (error as RedditApiException).errorCode)
        }

    @Test
    fun submitComment_withoutImagesSendsText() =
        runTest {
            reddit.respond(
                "/api/comment",
                """{"json": {"errors": [], "data": {"things": [{"kind": "t1", "data": ${thing("user_comments.json", "phx2cm1")}}]}}}""",
            )

            commentRepo().submitComment("t3_post", "Plain").getOrThrow()

            assertEquals(listOf("/api/comment"), reddit.requests.map { it.url.encodedPath })
            assertEquals("Plain", reddit.request.form()["text"])
        }
}
