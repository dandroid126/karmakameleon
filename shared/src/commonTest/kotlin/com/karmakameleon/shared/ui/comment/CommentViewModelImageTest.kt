package com.karmakameleon.shared.ui.comment

import com.karmakameleon.shared.createTestComment
import com.karmakameleon.shared.createTestSubreddit
import com.karmakameleon.shared.data.api.thing
import com.karmakameleon.shared.data.media.ImageFile
import com.karmakameleon.shared.data.media.ImageFiles
import com.karmakameleon.shared.data.media.ImageReadException
import com.karmakameleon.shared.data.repository.CommentImageRepository
import com.karmakameleon.shared.data.repository.CommentRepository
import com.karmakameleon.shared.data.repository.MarkdownRepository
import com.karmakameleon.shared.testing.MockReddit
import com.karmakameleon.shared.testing.form
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Adding images to a reply, uploading them, and posting the reply with them. */
@OptIn(ExperimentalCoroutinesApi::class)
class CommentViewModelImageTest {
    private val reddit = MockReddit()

    private val photo = ImageFile("photo.jpeg", "image/jpeg", byteArrayOf(1, 2, 3))
    private val gif = ImageFile("funny.gif", "image/gif", byteArrayOf(4, 5))

    private val imageFiles =
        object : ImageFiles {
            override suspend fun read(location: String): ImageFile =
                when (location) {
                    "content://photo" -> photo
                    "content://gif" -> gif
                    else -> throw ImageReadException("That file isn't an image")
                }
        }

    // Serves a JPEG at /cat.jpeg, and an error for anything else. Runs on the test's scheduler, which then waits for it.
    private fun TestScope.downloads() =
        HttpClient(
            MockEngine(
                MockEngineConfig().apply {
                    dispatcher = StandardTestDispatcher(testScheduler)
                    addHandler { request ->
                        if (request.url.encodedPath == "/cat.jpeg") {
                            respond(byteArrayOf(9, 9), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg"))
                        } else {
                            respond("", HttpStatusCode.NotFound)
                        }
                    }
                },
            ),
        )

    private val pics = createTestSubreddit(displayNamePrefixed = "r/pics", allowedMediaInComments = setOf("static", "giphy"))

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(withImages: Boolean = true): CommentViewModel {
        val api = reddit.api(ioDispatcher = StandardTestDispatcher(testScheduler))
        val images = if (withImages) CommentImageRepository(api, imageFiles, downloads()) else null
        return CommentViewModel(CommentRepository(api, MarkdownRepository(Dispatchers.Main)), commentImageRepository = images).apply {
            setCommentSubreddit(pics)
            setReplyingTo("t3_post")
        }
    }

    // Attaches an image and puts its token in the text, like the reply bar does.
    private fun CommentViewModel.attachAndInsert(
        location: String,
        before: String = "Look ",
    ): String? = attachImage(location)?.also { token -> setReplyText(before + token) }

    private val CommentViewModel.images get() = uiState.value.replyImages

    private fun respondToComment() {
        reddit.respond("/api/convert_rte_body_format", """{"output": {"document": [{"e": "img", "id": "asset1"}]}}""")
        reddit.respond("/api/comment", thing("user_comments.json", "phx2cm1").toString())
    }

    // ==================== Adding and uploading ====================

    @Test
    fun attachImage_readsAndUploadsTheImage() =
        runTest {
            reddit.respondToImageUpload("asset1")
            val viewModel = viewModel()

            val token = viewModel.attachImage("content://photo")
            assertEquals("![image](img-1)", token)
            assertEquals(ReplyImageStatus.Reading, viewModel.images.single().status)
            advanceUntilIdle()

            val image = viewModel.images.single()
            assertEquals(ReplyImageStatus.Uploaded("asset1"), image.status)
            assertEquals(photo, image.image)
        }

    @Test
    fun attachImage_givesEachImageItsOwnKey() =
        runTest {
            reddit.respondToImageUpload("asset1")
            val viewModel = viewModel()

            assertEquals("![image](img-1)", viewModel.attachImage("content://photo"))
            assertEquals("![image](img-2)", viewModel.attachImage("content://photo"))
        }

    @Test
    fun attachImage_marksAnImageThatCantBeReadAsFailed() =
        runTest {
            val viewModel = viewModel()

            viewModel.attachAndInsert("content://song")
            advanceUntilIdle()

            assertEquals(ReplyImageStatus.Failed("That file isn't an image"), viewModel.images.single().status)
            assertEquals("That file isn't an image", viewModel.uiState.value.imageMessage)
            viewModel.clearImageMessage()
            assertNull(viewModel.uiState.value.imageMessage)
        }

    @Test
    fun retryImage_uploadsAgainAfterAFailedUpload() =
        runTest {
            reddit.respondToImageUpload("asset1")
            reddit.respond(MockReddit.UPLOAD_PATH, "", HttpStatusCode.InternalServerError)
            val viewModel = viewModel()
            viewModel.attachAndInsert("content://photo")
            advanceUntilIdle()
            assertTrue(viewModel.images.single().status is ReplyImageStatus.Failed)

            reddit.respondToImageUpload("asset1")
            viewModel.retryImage("img-1")
            assertEquals(ReplyImageStatus.Uploading, viewModel.images.single().status)
            advanceUntilIdle()

            assertEquals(ReplyImageStatus.Uploaded("asset1"), viewModel.images.single().status)
        }

    @Test
    fun removeImage_dropsTheImageAndItsToken() =
        runTest {
            reddit.respondToImageUpload("asset1")
            val viewModel = viewModel()
            viewModel.attachAndInsert("content://photo")
            advanceUntilIdle()

            viewModel.removeImage("img-1")

            assertTrue(viewModel.images.isEmpty())
            assertEquals("Look ", viewModel.uiState.value.replyText)
        }

    @Test
    fun attachImageFromUrl_downloadsThenUploads() =
        runTest {
            reddit.respondToImageUpload("asset1")
            val viewModel = viewModel()

            assertEquals("![image](img-1)", viewModel.attachImageFromUrl("https://example.com/cat.jpeg"))
            advanceUntilIdle()

            assertEquals(ReplyImageStatus.Uploaded("asset1"), viewModel.images.single().status)
            assertEquals("cat.jpeg", reddit.request.form()["filepath"])
        }

    @Test
    fun setReplyingTo_startsWithoutImages() =
        runTest {
            reddit.respondToImageUpload("asset1")
            val viewModel = viewModel()
            viewModel.attachAndInsert("content://photo")

            viewModel.setReplyingTo("t1_other")

            assertTrue(viewModel.images.isEmpty())
        }

    @Test
    fun startEditComment_dropsTheReplysImages() =
        runTest {
            reddit.respondToImageUpload("asset1")
            val viewModel = viewModel()
            viewModel.attachAndInsert("content://photo")

            viewModel.startEditComment(createTestComment(id = "c1", body = "Old text"))

            assertTrue(viewModel.images.isEmpty())
        }

    @Test
    fun applyDraft_takesOutTokensForImagesThatAreGone() =
        runTest {
            reddit.respondToImageUpload("asset1")
            val viewModel = viewModel()
            viewModel.attachAndInsert("content://photo")

            viewModel.applyDraft("Old ![image](img-1) and ![image](img-7)")

            assertEquals("Old ![image](img-1) and ", viewModel.uiState.value.replyText)
        }

    @Test
    fun attachImage_skipsKeysAlreadyInTheText() =
        runTest {
            reddit.respondToImageUpload("asset1")
            val viewModel = viewModel()
            viewModel.setReplyText("Typed ![image](img-1)")

            assertEquals("![image](img-2)", viewModel.attachImage("content://photo"))
        }

    // ==================== Where images aren't allowed ====================

    @Test
    fun attachImage_turnsAwayAKnownTypeTheSubredditDoesntAllow() =
        runTest {
            val viewModel = viewModel()

            assertNull(viewModel.attachImage("content://gif", mimeType = "image/gif"))

            assertEquals("r/pics doesn't allow GIFs in comments", viewModel.uiState.value.imageMessage)
            assertTrue(viewModel.images.isEmpty())
            assertTrue(reddit.requests.isEmpty())
        }

    @Test
    fun attachImage_addsTheKeyboardsLinkWhenTheImageIsntAllowed() =
        runTest {
            val viewModel = viewModel()

            val text = viewModel.attachImage("content://gif", mimeType = "image/gif", linkUrl = "https://tenor.com/view/funny")

            assertEquals("[GIF](https://tenor.com/view/funny)", text)
            assertEquals("r/pics doesn't allow GIFs in comments, so it was added as a link", viewModel.uiState.value.imageMessage)
        }

    @Test
    fun attachImage_removesAnImageOnceReadingShowsItsTypeIsntAllowed() =
        runTest {
            val viewModel = viewModel()

            // The photo picker doesn't say the type, so it's only known once the file is read.
            viewModel.attachAndInsert("content://gif")
            advanceUntilIdle()

            assertTrue(viewModel.images.isEmpty())
            assertEquals("Look ", viewModel.uiState.value.replyText)
            assertEquals("r/pics doesn't allow GIFs in comments", viewModel.uiState.value.imageMessage)
        }

    @Test
    fun attachImage_isTurnedAwayWhileEditing() =
        runTest {
            val viewModel = viewModel()
            viewModel.startEditComment(createTestComment(id = "c1", body = "Old"))

            assertNull(viewModel.attachImage("content://photo", "image/jpeg"))

            assertEquals("Images can't be added while editing a comment", viewModel.uiState.value.imageMessage)
            assertFalse(viewModel.uiState.value.canAddImages)
        }

    @Test
    fun attachImage_isTurnedAwayWhereImagesArentSupported() =
        runTest {
            val viewModel = viewModel(withImages = false)

            assertNull(viewModel.attachImage("content://photo", "image/jpeg"))
            assertNull(viewModel.attachImageFromUrl("https://example.com/cat.jpeg"))

            assertEquals("Images can't be added here", viewModel.uiState.value.imageMessage)
            assertFalse(viewModel.uiState.value.supportsImages)
        }

    @Test
    fun canAddImages_onlyWhereTheSubredditAllowsUploads() =
        runTest {
            val viewModel = viewModel()
            assertTrue(viewModel.uiState.value.canAddImages)

            viewModel.setCommentSubreddit(createTestSubreddit(allowedMediaInComments = setOf("animated")))
            assertTrue(viewModel.uiState.value.canAddImages)

            // The button still shows, disabled.
            viewModel.setCommentSubreddit(createTestSubreddit(allowedMediaInComments = setOf("giphy", "video")))
            assertFalse(viewModel.uiState.value.canAddImages)
            assertTrue(viewModel.uiState.value.showsImageButton)

            // Not known yet, so there's no button.
            viewModel.setCommentSubreddit(null)
            assertFalse(viewModel.uiState.value.canAddImages)
            assertFalse(viewModel.uiState.value.showsImageButton)
        }

    @Test
    fun explainImagesDisabled_namesTheSubreddit() =
        runTest {
            val viewModel = viewModel()
            viewModel.setCommentSubreddit(createTestSubreddit(displayNamePrefixed = "r/AskReddit", allowedMediaInComments = setOf("video")))

            viewModel.explainImagesDisabled()

            assertEquals("Inline images are disabled in r/AskReddit", viewModel.uiState.value.imageMessage)
        }

    // ==================== Posting ====================

    @Test
    fun submitReply_sendsTheImagesAsRichtext() =
        runTest {
            reddit.respondToImageUpload("asset1")
            respondToComment()
            val viewModel = viewModel()
            viewModel.attachAndInsert("content://photo")
            advanceUntilIdle()

            assertNull(viewModel.submitReplyWithError(isReplyToPost = true))

            val convert = reddit.requests.single { it.url.encodedPath == "/api/convert_rte_body_format" }
            assertEquals("Look ![img](asset1)", convert.form()["markdown_text"])
            assertTrue(reddit.requests.any { it.url.encodedPath == "/api/comment" && it.form()["richtext_json"] != null })
            assertTrue(viewModel.images.isEmpty())
            assertNull(viewModel.uiState.value.replyingTo)
        }

    @Test
    fun submitReply_waitsForUploadsToFinish() =
        runTest {
            reddit.respondToImageUpload("asset1")
            reddit.holdRequests(MockReddit.UPLOAD_PATH)
            val viewModel = viewModel()
            viewModel.attachAndInsert("content://photo")
            advanceUntilIdle()

            assertEquals("Wait for the images to upload", viewModel.submitReplyWithError(isReplyToPost = true))
            assertEquals("t3_post", viewModel.uiState.value.replyingTo)
        }

    @Test
    fun submitReply_refusesWhileAnImageHasFailed() =
        runTest {
            val viewModel = viewModel()
            viewModel.attachAndInsert("content://song")
            advanceUntilIdle()

            assertEquals("An image didn't upload. Retry or remove it.", viewModel.submitReplyWithError(isReplyToPost = true))
        }

    @Test
    fun submitReply_leavesOutAnImageWhoseTokenWasDeleted() =
        runTest {
            reddit.respondToImageUpload("asset1")
            reddit.respond(
                "/api/comment",
                """{"json": {"errors": [], "data": {"things": [{"kind": "t1", "data": ${thing("user_comments.json", "phx2cm1")}}]}}}""",
            )
            val viewModel = viewModel()
            viewModel.attachAndInsert("content://photo")
            advanceUntilIdle()
            viewModel.setReplyText("Changed my mind")

            assertNull(viewModel.submitReplyWithError(isReplyToPost = true))

            val post = reddit.requests.single { it.url.encodedPath == "/api/comment" }
            assertEquals("Changed my mind", post.form()["text"])
        }

    @Test
    fun submitReply_leavesOutATokenWithNoImage() =
        runTest {
            reddit.respond(
                "/api/comment",
                """{"json": {"errors": [], "data": {"things": [{"kind": "t1", "data": ${thing("user_comments.json", "phx2cm1")}}]}}}""",
            )
            val viewModel = viewModel()
            viewModel.setReplyText("From a draft ![image](img-1)")

            assertNull(viewModel.submitReplyWithError(isReplyToPost = true))

            val post = reddit.requests.single { it.url.encodedPath == "/api/comment" }
            assertEquals("From a draft", post.form()["text"])
        }
}
