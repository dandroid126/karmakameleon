package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.RedditApiException
import com.karmakameleon.shared.data.api.thing
import com.karmakameleon.shared.data.media.ImageFile
import com.karmakameleon.shared.data.media.ImageFiles
import com.karmakameleon.shared.data.media.ImageReadException
import com.karmakameleon.shared.testing.MockReddit
import com.karmakameleon.shared.testing.form
import com.karmakameleon.shared.util.markdown.MarkdownParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
    private val markdown = MarkdownParser(UnconfinedTestDispatcher())

    private val photo = ImageFile("photo.jpeg", "image/jpeg", byteArrayOf(1, 2, 3))

    private val imageFiles =
        object : ImageFiles {
            override suspend fun read(location: String): ImageFile =
                if (location == "content://photo") photo else throw ImageReadException("That file isn't an image")
        }

    // What the download server answers for each path: a status, a content type and a body.
    private val downloads = mutableMapOf<String, Triple<HttpStatusCode, String?, ByteArray>>()

    private val downloadClient =
        HttpClient(
            MockEngine { request ->
                val (status, type, body) = downloads[request.url.encodedPath] ?: Triple(HttpStatusCode.NotFound, null, ByteArray(0))
                respond(body, status, if (type != null) headersOf(HttpHeaders.ContentType, type) else headersOf())
            },
        )

    private fun TestScope.imageRepo() =
        CommentImageRepository(reddit.api(ioDispatcher = StandardTestDispatcher(testScheduler)), imageFiles, downloadClient, maxBytes = 10)

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

    // ==================== Downloading from a link ====================

    @Test
    fun download_givesTheImageNamedAfterTheLink() =
        runTest {
            downloads["/pics/cat.png"] = Triple(HttpStatusCode.OK, "image/png", byteArrayOf(1, 2))

            val image = imageRepo().download("  https://example.com/pics/cat.png?size=large ").getOrThrow()

            assertEquals("cat.png", image.name)
            assertEquals("image/png", image.mimeType)
            assertEquals(listOf<Byte>(1, 2), image.bytes.toList())
        }

    @Test
    fun download_namesAnImageWhoseLinkHasNoFileName() =
        runTest {
            downloads["/view"] = Triple(HttpStatusCode.OK, "image/webp; charset=binary", byteArrayOf(1))

            assertEquals("image.webp", imageRepo().download("https://example.com/view").getOrThrow().name)
        }

    @Test
    fun download_acceptsAnImageOfExactlyTheLimit() =
        runTest {
            downloads["/big.png"] = Triple(HttpStatusCode.OK, "image/png", ByteArray(10))

            assertEquals(
                10,
                imageRepo()
                    .download("https://example.com/big.png")
                    .getOrThrow()
                    .bytes.size,
            )
        }

    @Test
    fun download_failsWithAReasonTheUserCanRead() =
        runTest {
            downloads["/page"] = Triple(HttpStatusCode.OK, "text/html", "<html>".encodeToByteArray())
            downloads["/big.png"] = Triple(HttpStatusCode.OK, "image/png", ByteArray(11))
            val repo = imageRepo()

            suspend fun failure(url: String) = repo.download(url).exceptionOrNull()?.message

            assertEquals("Enter a link that starts with https://", failure("cat.png"))
            assertEquals("Enter a link that starts with https://", failure("ftp://example.com/cat.png"))
            assertEquals("Enter a link that starts with https://", failure("https://"))
            assertEquals("That link isn't an image", failure("https://example.com/page"))
            assertEquals("Couldn't download the image (HTTP 404)", failure("https://example.com/missing.png"))
            assertEquals("The image is bigger than 0 MB", failure("https://example.com/big.png"))
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
