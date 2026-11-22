package com.karmakameleon.shared.data.api

import com.karmakameleon.shared.testing.MockReddit
import com.karmakameleon.shared.testing.form
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedditApiCommentImageTest {
    private val reddit = MockReddit()

    private fun TestScope.api(): RedditApi = reddit.api(ioDispatcher = StandardTestDispatcher(testScheduler))

    private val document = """{"document": [{"e": "par", "c": [{"e": "text", "t": "Look"}]}, {"e": "img", "id": "asset1"}]}"""

    // ==================== Upload ====================

    @Test
    fun uploadCommentImage_asksForALeaseThenUploadsToItWithoutTheRedditToken() =
        runTest {
            reddit.respondToImageUpload("asset1")

            val assetId = api().uploadCommentImage("photo.jpeg", "image/jpeg", "JPEGDATA".encodeToByteArray())

            assertEquals("asset1", assetId)
            val (lease, upload) = reddit.requests
            assertEquals("/api/media/asset.json", lease.url.encodedPath)
            assertEquals(
                mapOf("filepath" to "photo.jpeg", "mimetype" to "image/jpeg"),
                lease.form().entries().associate {
                    it.key to
                        it.value.single()
                },
            )
            assertEquals(MockReddit.UPLOAD_URL, upload.url.toString())
            assertNull(upload.headers[HttpHeaders.Authorization])
            // Longer than API calls get, for big images on slow connections.
            assertEquals(5 * 60 * 1000L, upload.getCapabilityOrNull(HttpTimeoutCapability)?.requestTimeoutMillis)
            // The lease's fields come first, in order, then the file.
            val body = upload.body.toByteArray().decodeToString()
            val key = body.indexOf("name=\"key\"")
            val policy = body.indexOf("name=\"policy\"")
            val file = body.indexOf("name=\"file\"; filename=\"photo.jpeg\"")
            assertTrue(key in 0 until policy && policy < file, body)
            assertTrue("Content-Type: image/jpeg" in body, body)
            assertTrue("JPEGDATA" in body, body)
        }

    @Test
    fun uploadCommentImage_dropsQuotesAndLineBreaksFromTheFileName() =
        runTest {
            reddit.respondToImageUpload("asset1")

            api().uploadCommentImage("a\"b\r\nc.png", "image/png", byteArrayOf(1))

            assertTrue(
                "filename=\"abc.png\"" in
                    reddit.requests[1]
                        .body
                        .toByteArray()
                        .decodeToString(),
            )
        }

    @Test
    fun uploadCommentImage_failsWhenTheUploadIsRejected() =
        runTest {
            reddit.respondToImageUpload("asset1")
            reddit.respond(MockReddit.UPLOAD_PATH, "<Error>AccessDenied</Error>", HttpStatusCode.Forbidden)

            val error = assertFailsWith<IllegalStateException> { api().uploadCommentImage("photo.jpeg", "image/jpeg", byteArrayOf(1)) }

            assertEquals("Couldn't upload the image (HTTP 403)", error.message)
        }

    @Test
    fun uploadCommentImage_failsWithoutALease() =
        runTest {
            reddit.respond("/api/media/asset.json", """{"args": {"fields": []}}""")

            assertFailsWith<IllegalStateException> { api().uploadCommentImage("photo.jpeg", "image/jpeg", byteArrayOf(1)) }
            assertEquals(1, reddit.requests.size)
        }

    @Test
    fun uploadCommentImage_failsWhenTheLeaseIsRefused() =
        runTest {
            reddit.respond("/api/media/asset.json", "", HttpStatusCode.Forbidden)

            assertFailsWith<IllegalStateException> { api().uploadCommentImage("photo.jpeg", "image/jpeg", byteArrayOf(1)) }
        }

    @Test
    fun uploadCommentImage_throwsAnErrorRedditReports() =
        runTest {
            reddit.respond("/api/media/asset.json", """{"json": {"errors": [["BAD_MIMETYPE", "That type isn't allowed.", "mimetype"]]}}""")

            val error = assertFailsWith<RedditApiException> { api().uploadCommentImage("a.bmp", "image/bmp", byteArrayOf(1)) }

            assertEquals("BAD_MIMETYPE", error.errorCode)
        }

    // ==================== Richtext ====================

    @Test
    fun convertToRichtext_returnsTheDocument() =
        runTest {
            reddit.respond("/api/convert_rte_body_format", """{"output": $document, "output_mode": "rtjson"}""")

            val result = api().convertToRichtext("Look\n\n![img](asset1)")

            assertEquals(Json.parseToJsonElement(document), result)
            val form = reddit.request.form()
            assertEquals("rtjson", form["output_mode"])
            assertEquals("Look\n\n![img](asset1)", form["markdown_text"])
        }

    @Test
    fun convertToRichtext_failsWithoutADocument() =
        runTest {
            reddit.respond("/api/convert_rte_body_format", """{"output": {}}""")

            assertFailsWith<IllegalStateException> { api().convertToRichtext("Look") }
        }

    @Test
    fun convertToRichtext_failsOnAnErrorStatus() =
        runTest {
            reddit.respond("/api/convert_rte_body_format", "", HttpStatusCode.InternalServerError)

            assertFailsWith<IllegalStateException> { api().convertToRichtext("Look") }
        }

    @Test
    fun submitRichtextComment_sendsTheDocumentAndReadsTheBareComment() =
        runTest {
            // A richtext comment comes back as the comment itself, not wrapped like a text comment.
            reddit.respond("/api/comment", thing("user_comments.json", "phx2cm1").toString())

            val result = api().submitRichtextComment("t3_post", Json.parseToJsonElement(document).jsonObject)

            assertEquals("phx2cm1", result?.id)
            val form = reddit.request.form()
            assertEquals("t3_post", form["thing_id"])
            assertEquals(Json.parseToJsonElement(document), Json.parseToJsonElement(form["richtext_json"]!!))
            assertNull(form["text"])
        }

    @Test
    fun submitRichtextComment_throwsWhenTheSubredditDoesntAllowTheImage() =
        runTest {
            reddit.respond(
                "/api/comment",
                """{"json": {"errors": [["META_MEDIA_TYPE_NOT_ALLOWED", "This community doesn't allow that.", "richtext_json"]]}}""",
            )

            val error =
                assertFailsWith<RedditApiException> { api().submitRichtextComment("t3_post", Json.parseToJsonElement(document).jsonObject) }

            assertEquals("META_MEDIA_TYPE_NOT_ALLOWED", error.errorCode)
        }

    @Test
    fun submitRichtextComment_nullForAResponseThatIsntAComment() =
        runTest {
            reddit.respond("/api/comment", """{"name": "t3_notacomment"}""")

            assertNull(api().submitRichtextComment("t3_post", Json.parseToJsonElement(document).jsonObject))
        }
}
