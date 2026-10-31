package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.FakeRedditApi
import com.karmakameleon.shared.createTestComment
import com.karmakameleon.shared.createTestListing
import com.karmakameleon.shared.createTestMoreComments
import com.karmakameleon.shared.createTestPost
import com.karmakameleon.shared.data.api.CommentOrMore
import com.karmakameleon.shared.domain.model.CommentSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostRepositoryTest {
    // Parses as soon as it's asked to, so the texts are cached by the time a call returns.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val markdown = MarkdownRepository(UnconfinedTestDispatcher())

    private fun createRepo(api: FakeRedditApi = FakeRedditApi()) = PostRepository(api, markdown) to api

    // ==================== getPosts ====================

    @Test
    fun getPosts_returnsSuccess() =
        runTest {
            val (repo, api) = createRepo()
            val posts = listOf(createTestPost(id = "1"), createTestPost(id = "2"))
            api.postsResult = createTestListing(items = posts, after = "after1")

            val result = repo.getPosts()
            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrThrow().items.size)
            assertEquals("after1", result.getOrThrow().after)
        }

    @Test
    fun getPosts_cachesPostsInternally() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "cached1")
            api.postsResult = createTestListing(items = listOf(post))

            repo.getPosts()
            assertEquals(post, repo.getCachedPost("cached1"))
        }

    @Test
    fun getPosts_returnsFailureOnException() =
        runTest {
            val (repo, api) = createRepo()
            api.shouldThrow = Exception("Network error")

            val result = repo.getPosts()
            assertTrue(result.isFailure)
        }

    // ==================== getPost ====================

    @Test
    fun getPost_returnsCachedPost() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1")
            api.postsResult = createTestListing(items = listOf(post))
            repo.getPosts() // cache it

            val result = repo.getPost("testsubreddit", "p1")
            assertTrue(result.isSuccess)
            assertEquals("p1", result.getOrThrow().id)
        }

    @Test
    fun getPost_fetchesFromApiWhenNotCached() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p2")
            api.postResult = post

            val result = repo.getPost("testsubreddit", "p2")
            assertTrue(result.isSuccess)
            assertEquals("p2", result.getOrThrow().id)
        }

    @Test
    fun getPost_returnsFailureWhenNotFound() =
        runTest {
            val (repo, api) = createRepo()
            api.postResult = null

            val result = repo.getPost("testsubreddit", "missing")
            assertTrue(result.isFailure)
        }

    // ==================== resolveShareLink ====================

    @Test
    fun resolveShareLink_returnsThePostFromTheApi() =
        runTest {
            val (repo, api) = createRepo()
            api.resolveShareLinkResult = "Kotlin" to "abc123"

            assertEquals("Kotlin" to "abc123", repo.resolveShareLink("kotlin", "AbCdEf"))
        }

    // ==================== getPostWithComments ====================

    @Test
    fun getPostWithComments_returnsPostAndComments() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1")
            val comment = createTestComment(id = "c1")
            api.postWithCommentsResult = post to listOf(CommentOrMore.CommentItem(comment))

            val result = repo.getPostWithComments("sub", "p1")
            assertTrue(result.isSuccess)
            val (resultPost, resultComments) = result.getOrThrow()
            assertEquals("p1", resultPost.id)
            assertEquals(1, resultComments.size)
        }

    @Test
    fun getPostWithComments_parsesThePostAndEveryCommentBeforeReturning() =
        runTest {
            val (repo, api) = createRepo()
            val reply = createTestComment(id = "c2", body = "a *reply*")
            val comment = createTestComment(id = "c1", body = "a **comment**", replies = listOf(reply))
            api.postWithCommentsResult = createTestPost(id = "p1", selfText = "the post") to listOf(CommentOrMore.CommentItem(comment))

            repo.getPostWithComments("sub", "p1")

            // So the screen doesn't parse them while drawing.
            assertNotNull(markdown.cached("the post"))
            assertNotNull(markdown.cached("a **comment**"))
            assertNotNull(markdown.cached("a *reply*"))
        }

    // ==================== getMoreComments ====================

    @Test
    fun getMoreComments_loadsEachThreadAtTheDepthOfTheMoreEntry() =
        runTest {
            val (repo, api) = createRepo()
            val reply = createTestComment(id = "r1", depth = 1)
            val thread = createTestComment(id = "c1", depth = 0).copy(replies = listOf(reply))
            api.commentThreadResults["c1"] = createTestPost() to listOf(CommentOrMore.CommentItem(thread))
            val more = createTestMoreComments(depth = 2, count = 1, children = listOf("c1"))

            val loaded = repo.getMoreComments("sub", "p1", more).getOrThrow().single() as CommentOrMore.CommentItem

            assertEquals(2, loaded.comment.depth)
            assertEquals(
                3,
                loaded.comment.replies
                    .single()
                    .depth,
            )
            assertNotNull(markdown.cached(thread.body))
        }

    @Test
    fun getMoreComments_keepsWhatItDidNotLoad() =
        runTest {
            val (repo, api) = createRepo()
            val thread = createTestComment(id = "k0")
            api.postWithCommentsResult = createTestPost() to emptyList()
            api.commentThreadResults["k0"] = createTestPost() to listOf(CommentOrMore.CommentItem(thread))
            val more = createTestMoreComments(count = 100, children = List(30) { "k$it" })

            val items = repo.getMoreComments("sub", "p1", more).getOrThrow()

            assertEquals(CommentSort.CONFIDENCE, api.postWithCommentsRequests.first().sort)
            assertEquals(List(20) { "k$it" }.toSet(), api.postWithCommentsRequests.map { it.commentId }.toSet())
            assertEquals(listOf("k0"), items.filterIsInstance<CommentOrMore.CommentItem>().map { it.comment.id })
            val left = (items.last() as CommentOrMore.More).more
            assertEquals(List(10) { "k${it + 20}" }, left.children)
            assertEquals(99, left.count)
        }

    @Test
    fun getMoreComments_doesNotRequestOrKeepCommentsThatCameAsReplies() =
        runTest {
            val (repo, api) = createRepo()
            api.postWithCommentsResult = createTestPost() to emptyList()
            val reply = createTestComment(id = "k6", depth = 1)
            val thread = createTestComment(id = "k0").copy(replies = listOf(reply))
            api.commentThreadResults["k0"] = createTestPost() to listOf(CommentOrMore.CommentItem(thread))
            val more = createTestMoreComments(count = 7, children = List(7) { "k$it" })

            val items = repo.getMoreComments("sub", "p1", more).getOrThrow()

            assertEquals(List(6) { "k$it" }, api.postWithCommentsRequests.map { it.commentId })
            assertEquals(listOf("k0"), items.map { (it as CommentOrMore.CommentItem).comment.id })
        }

    @Test
    fun getMoreComments_leavesCommentsInAThreadsOwnMoreToThatThread() =
        runTest {
            val (repo, api) = createRepo()
            api.postWithCommentsResult = createTestPost() to emptyList()
            val nestedMore = createTestMoreComments(id = "nested", children = listOf("k6"))
            val thread = createTestComment(id = "k0").copy(moreReplies = nestedMore)
            api.commentThreadResults["k0"] = createTestPost() to listOf(CommentOrMore.CommentItem(thread))
            val more = createTestMoreComments(count = 7, children = List(7) { "k$it" })

            val items = repo.getMoreComments("sub", "p1", more).getOrThrow()

            assertEquals(List(6) { "k$it" }, api.postWithCommentsRequests.map { it.commentId })
            assertEquals(listOf("k0"), items.map { (it as CommentOrMore.CommentItem).comment.id })
        }

    @Test
    fun getMoreComments_keepsWhatLoadedWhenARequestFails() =
        runTest {
            val (repo, api) = createRepo()
            // Without a default result, the requests for the other comments fail.
            val thread = createTestComment(id = "k0")
            api.commentThreadResults["k0"] = createTestPost() to listOf(CommentOrMore.CommentItem(thread))
            val more = createTestMoreComments(count = 8, children = List(8) { "k$it" })

            val items = repo.getMoreComments("sub", "p1", more).getOrThrow()

            // It stops after the first few requests, and tries the failed ones again on the next tap.
            assertEquals(5, api.postWithCommentsRequests.size)
            assertEquals(listOf("k0"), items.filterIsInstance<CommentOrMore.CommentItem>().map { it.comment.id })
            assertEquals(List(7) { "k${it + 1}" }, (items.last() as CommentOrMore.More).more.children)
        }

    @Test
    fun getMoreComments_failsWhenARequestFails() =
        runTest {
            val (repo, api) = createRepo()
            api.shouldThrow = Exception("No connection")

            assertTrue(repo.getMoreComments("sub", "p1", createTestMoreComments()).isFailure)
        }

    // ==================== vote ====================

    @Test
    fun vote_upvoteUpdatesPostState() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1", score = 10, likes = null)
            api.voteResult = true

            val result = repo.vote(post, 1)
            assertTrue(result.isSuccess)
            val updated = result.getOrThrow()
            assertEquals(true, updated.likes)
            assertEquals(11, updated.score)
        }

    @Test
    fun vote_downvoteUpdatesPostState() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1", score = 10, likes = null)
            api.voteResult = true

            val result = repo.vote(post, -1)
            assertTrue(result.isSuccess)
            val updated = result.getOrThrow()
            assertEquals(false, updated.likes)
            assertEquals(9, updated.score)
        }

    @Test
    fun vote_removeUpvote() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1", score = 11, likes = true)
            api.voteResult = true

            val result = repo.vote(post, 0)
            assertTrue(result.isSuccess)
            val updated = result.getOrThrow()
            assertNull(updated.likes)
            assertEquals(10, updated.score)
        }

    @Test
    fun vote_removeDownvote() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1", score = 9, likes = false)
            api.voteResult = true

            val result = repo.vote(post, 0)
            assertTrue(result.isSuccess)
            val updated = result.getOrThrow()
            assertNull(updated.likes)
            assertEquals(10, updated.score)
        }

    @Test
    fun vote_switchFromUpToDown() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1", score = 11, likes = true)
            api.voteResult = true

            val result = repo.vote(post, -1)
            assertTrue(result.isSuccess)
            val updated = result.getOrThrow()
            assertEquals(false, updated.likes)
            assertEquals(9, updated.score)
        }

    @Test
    fun vote_switchFromDownToUp() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1", score = 9, likes = false)
            api.voteResult = true

            val result = repo.vote(post, 1)
            assertTrue(result.isSuccess)
            val updated = result.getOrThrow()
            assertEquals(true, updated.likes)
            assertEquals(11, updated.score)
        }

    @Test
    fun vote_failureReturnsError() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1")
            api.voteResult = false

            val result = repo.vote(post, 1)
            assertTrue(result.isFailure)
        }

    @Test
    fun vote_exceptionReturnsFailure() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1")
            api.shouldThrow = Exception("Network error")

            val result = repo.vote(post, 1)
            assertTrue(result.isFailure)
        }

    @Test
    fun vote_redditApiExceptionPreservesMessage() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1")
            api.shouldThrow =
                com.karmakameleon.shared.data.api
                    .RedditApiException("THREAD_LOCKED", "you are not allowed to do that")

            val result = repo.vote(post, 1)
            assertTrue(result.isFailure)
            assertEquals("you are not allowed to do that", result.exceptionOrNull()?.message)
        }

    // ==================== save ====================

    @Test
    fun save_savesUnsavedPost() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1", isSaved = false)
            api.saveResult = true

            val result = repo.save(post)
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isSaved)
        }

    @Test
    fun save_unsavesSavedPost() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1", isSaved = true)
            api.unsaveResult = true

            val result = repo.save(post)
            assertTrue(result.isSuccess)
            assertFalse(result.getOrThrow().isSaved)
        }

    @Test
    fun save_failureReturnsError() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1", isSaved = false)
            api.saveResult = false

            val result = repo.save(post)
            assertTrue(result.isFailure)
        }

    // ==================== hide ====================

    @Test
    fun hide_hidesVisiblePost() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1", isHidden = false)
            api.hideResult = true

            val result = repo.hide(post)
            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow().isHidden)
        }

    @Test
    fun hide_unhidesHiddenPost() =
        runTest {
            val (repo, api) = createRepo()
            val post = createTestPost(id = "p1", isHidden = true)
            api.unhideResult = true

            val result = repo.hide(post)
            assertTrue(result.isSuccess)
            assertFalse(result.getOrThrow().isHidden)
        }

    // ==================== search ====================

    @Test
    fun search_returnsResults() =
        runTest {
            val (repo, api) = createRepo()
            val posts = listOf(createTestPost(id = "s1"), createTestPost(id = "s2"))
            api.searchResult = createTestListing(items = posts)

            val result = repo.search("test query")
            assertTrue(result.isSuccess)
            assertEquals(2, result.getOrThrow().items.size)
        }

    @Test
    fun search_returnsFailureOnError() =
        runTest {
            val (repo, api) = createRepo()
            api.shouldThrow = Exception("Search failed")

            val result = repo.search("test")
            assertTrue(result.isFailure)
        }

    // ==================== submitPost ====================

    @Test
    fun submitPost_returnsPostName() =
        runTest {
            val (repo, api) = createRepo()
            api.submitPostResult = "t3_newpost"

            val result = repo.submitPost("testsub", "Title", text = "Body")
            assertTrue(result.isSuccess)
            assertEquals("t3_newpost", result.getOrThrow())
        }

    @Test
    fun submitPost_linkPost() =
        runTest {
            val (repo, api) = createRepo()
            api.submitPostResult = "t3_linkpost"

            val result = repo.submitPost("testsub", "Title", url = "https://example.com")
            assertTrue(result.isSuccess)
        }

    @Test
    fun submitPost_returnsFailureWhenNull() =
        runTest {
            val (repo, api) = createRepo()
            api.submitPostResult = null

            val result = repo.submitPost("testsub", "Title")
            assertTrue(result.isFailure)
        }

    // ==================== enrichSparsePosts ====================

    @Test
    fun enrichSparsePosts_returnsEmptyForNonSparsePosts() =
        runTest {
            val (repo, api) = createRepo()
            // Returned only if the post is fetched, which it shouldn't be.
            api.fetchPostsByIdsResult = listOf(createTestPost(id = "p1"))
            val post = createTestPost(id = "p1", postHint = "self", selfText = "text")
            val result = repo.enrichSparsePosts(listOf(post))
            assertTrue(result.isEmpty())
        }

    // ==================== preserveCachedPreviewData ====================

    @Test
    fun getPostWithComments_preservesCachedPreviewWhenFreshPostLacksIt() =
        runTest {
            val (repo, api) = createRepo()
            val preview =
                com.karmakameleon.shared.domain.model.Preview(
                    images =
                        listOf(
                            com.karmakameleon.shared.domain.model.PreviewImage(
                                source =
                                    com.karmakameleon.shared.domain.model.ImageSource(
                                        url = "https://preview.redd.it/image.jpg",
                                        width = 1024,
                                        height = 768,
                                    ),
                                resolutions = emptyList(),
                            ),
                        ),
                    enabled = true,
                )
            val cachedPost = createTestPost(id = "p1", preview = preview, thumbnail = "https://thumb.redd.it/thumb.jpg")
            api.postsResult = createTestListing(items = listOf(cachedPost))
            repo.getPosts()

            val freshPost = createTestPost(id = "p1", preview = null, thumbnail = null)
            api.postWithCommentsResult = freshPost to emptyList()

            val result = repo.getPostWithComments("sub", "p1")
            assertTrue(result.isSuccess)
            val resultPost = result.getOrThrow().first
            assertEquals(preview, resultPost.preview)
            assertEquals("https://thumb.redd.it/thumb.jpg", resultPost.thumbnail)
        }

    @Test
    fun getPostWithComments_usesFreshPreviewWhenAvailable() =
        runTest {
            val (repo, api) = createRepo()
            val cachedPreview =
                com.karmakameleon.shared.domain.model.Preview(
                    images =
                        listOf(
                            com.karmakameleon.shared.domain.model.PreviewImage(
                                source =
                                    com.karmakameleon.shared.domain.model
                                        .ImageSource(url = "https://old.jpg", width = 100, height = 100),
                                resolutions = emptyList(),
                            ),
                        ),
                    enabled = true,
                )
            val freshPreview =
                com.karmakameleon.shared.domain.model.Preview(
                    images =
                        listOf(
                            com.karmakameleon.shared.domain.model.PreviewImage(
                                source =
                                    com.karmakameleon.shared.domain.model
                                        .ImageSource(url = "https://new.jpg", width = 200, height = 200),
                                resolutions = emptyList(),
                            ),
                        ),
                    enabled = true,
                )
            val cachedPost = createTestPost(id = "p1", preview = cachedPreview)
            api.postsResult = createTestListing(items = listOf(cachedPost))
            repo.getPosts()

            val freshPost = createTestPost(id = "p1", preview = freshPreview, thumbnail = "https://new-thumb.jpg")
            api.postWithCommentsResult = freshPost to emptyList()

            val result = repo.getPostWithComments("sub", "p1")
            assertTrue(result.isSuccess)
            val resultPost = result.getOrThrow().first
            assertEquals(freshPreview, resultPost.preview)
            assertEquals("https://new-thumb.jpg", resultPost.thumbnail)
        }

    @Test
    fun getPostWithComments_preservesCachedThumbnailWhenFreshLacksIt() =
        runTest {
            val (repo, api) = createRepo()
            val preview =
                com.karmakameleon.shared.domain.model.Preview(
                    images =
                        listOf(
                            com.karmakameleon.shared.domain.model.PreviewImage(
                                source =
                                    com.karmakameleon.shared.domain.model
                                        .ImageSource(url = "https://preview.jpg", width = 100, height = 100),
                                resolutions = emptyList(),
                            ),
                        ),
                    enabled = true,
                )
            val cachedPost =
                createTestPost(id = "p1", preview = null, thumbnail = "https://thumb.jpg", thumbnailWidth = 140, thumbnailHeight = 90)
            api.postsResult = createTestListing(items = listOf(cachedPost))
            repo.getPosts()

            val freshPost = createTestPost(id = "p1", preview = preview, thumbnail = null, thumbnailWidth = null, thumbnailHeight = null)
            api.postWithCommentsResult = freshPost to emptyList()

            val result = repo.getPostWithComments("sub", "p1")
            assertTrue(result.isSuccess)
            val resultPost = result.getOrThrow().first
            assertEquals(preview, resultPost.preview)
            assertEquals("https://thumb.jpg", resultPost.thumbnail)
            assertEquals(140, resultPost.thumbnailWidth)
            assertEquals(90, resultPost.thumbnailHeight)
        }

    // ==================== getCachedPost ====================

    @Test
    fun getCachedPost_returnsNullWhenNotCached() {
        val (repo, _) = createRepo()
        assertNull(repo.getCachedPost("nonexistent"))
    }

    @Test
    fun enrichSparsePosts_cachesTheFilledInPosts() =
        runTest {
            val (repo, api) = createRepo()
            val sparse = createTestPost(id = "p1", url = "https://example.com/article")
            val enriched = sparse.copy(thumbnail = "https://example.com/thumb.jpg")
            api.fetchPostsByIdsResult = listOf(enriched)

            assertEquals(listOf(enriched), repo.enrichSparsePosts(listOf(sparse)))
            assertEquals(enriched, repo.getCachedPost("p1"))
        }

    @Test
    fun enrichSparsePosts_skipsPostsThatHaveTheirDetails() =
        runTest {
            val (repo, api) = createRepo()
            api.fetchPostsByIdsResult = listOf(createTestPost(id = "p1"))

            assertEquals(emptyList(), repo.enrichSparsePosts(listOf(createTestPost(id = "p1", thumbnail = "https://example.com/t.jpg"))))
            assertNull(repo.getCachedPost("p1"))
        }

    @Test
    fun enrichSparsePosts_nothingFound() =
        runTest {
            val (repo, _) = createRepo()

            assertEquals(emptyList(), repo.enrichSparsePosts(listOf(createTestPost(id = "p1", url = "https://example.com/article"))))
            assertNull(repo.getCachedPost("p1"))
        }
}
