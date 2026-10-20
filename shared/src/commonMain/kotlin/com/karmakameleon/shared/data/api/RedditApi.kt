package com.karmakameleon.shared.data.api

import com.karmakameleon.shared.data.api.dto.CommentDto
import com.karmakameleon.shared.data.api.dto.GalleryDataDto
import com.karmakameleon.shared.data.api.dto.ListingData
import com.karmakameleon.shared.data.api.dto.MediaDto
import com.karmakameleon.shared.data.api.dto.MediaMetadataDto
import com.karmakameleon.shared.data.api.dto.MessageDto
import com.karmakameleon.shared.data.api.dto.MoreChildrenResponse
import com.karmakameleon.shared.data.api.dto.MoreCommentsDto
import com.karmakameleon.shared.data.api.dto.PostDto
import com.karmakameleon.shared.data.api.dto.PreviewDto
import com.karmakameleon.shared.data.api.dto.RedditResponse
import com.karmakameleon.shared.data.api.dto.SubredditDto
import com.karmakameleon.shared.data.api.dto.ThingData
import com.karmakameleon.shared.data.api.dto.UserDto
import com.karmakameleon.shared.data.api.dto.UserSubredditDto
import com.karmakameleon.shared.data.repository.SettingsRepository
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.CommentSort
import com.karmakameleon.shared.domain.model.FlairRichtext
import com.karmakameleon.shared.domain.model.GalleryData
import com.karmakameleon.shared.domain.model.GalleryItem
import com.karmakameleon.shared.domain.model.ImageSource
import com.karmakameleon.shared.domain.model.InboxFilter
import com.karmakameleon.shared.domain.model.Listing
import com.karmakameleon.shared.domain.model.Media
import com.karmakameleon.shared.domain.model.Message
import com.karmakameleon.shared.domain.model.MessageType
import com.karmakameleon.shared.domain.model.MoreComments
import com.karmakameleon.shared.domain.model.Post
import com.karmakameleon.shared.domain.model.PostSort
import com.karmakameleon.shared.domain.model.Preview
import com.karmakameleon.shared.domain.model.PreviewImage
import com.karmakameleon.shared.domain.model.RedditVideo
import com.karmakameleon.shared.domain.model.SearchSort
import com.karmakameleon.shared.domain.model.Subreddit
import com.karmakameleon.shared.domain.model.TimeFilter
import com.karmakameleon.shared.domain.model.User
import com.karmakameleon.shared.domain.model.UserSubreddit
import com.karmakameleon.shared.util.RedditLink
import com.karmakameleon.shared.util.parseRedditLink
import com.karmakameleon.shared.util.runSuspendCatching
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

open class RedditApi(
    private val httpClient: HttpClient,
    private val authManager: AuthManager,
    private val settingsRepository: SettingsRepository? = null,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // Share links are resolved by reading where they redirect to, so this client doesn't follow redirects.
    createShareLinkClient: () -> HttpClient = { createHttpClient(followRedirects = false) },
) {
    private val apiScope = CoroutineScope(ioDispatcher + SupervisorJob())

    private val shareLinkClient by lazy(createShareLinkClient)

    private val json = RedditParser.json

    private val parser = RedditParser(settingsRepository)

    companion object {
        const val BASE_URL = "https://oauth.reddit.com"
        const val WWW_URL = "https://www.reddit.com"
        const val AUTH_URL = "https://www.reddit.com/api/v1"

        // Reddit's /api/info returns at most 100 items per request.
        private const val MAX_SUBREDDITS_PER_INFO_REQUEST = 100
    }

    private suspend fun authenticatedRequest(block: HttpRequestBuilder.() -> Unit): HttpResponse {
        val token =
            authManager.getAccessToken()
                ?: error("No Reddit Client ID configured. Please enter your Client ID in the Profile tab.")
        return httpClient.request {
            block()
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.UserAgent, "KK/1.0.0 (Android)")
        }
    }

    // GETs an API path and decodes the response. raw_json=1 stops Reddit from HTML-escaping the text.
    private suspend inline fun <reified T> getJson(
        path: String,
        crossinline params: HttpRequestBuilder.() -> Unit = {},
    ): T {
        val response =
            authenticatedRequest {
                method = HttpMethod.Get
                url("$BASE_URL$path")
                params()
                parameter("raw_json", 1)
            }
        return json.decodeFromString<T>(response.bodyAsText())
    }

    private suspend fun getListing(
        path: String,
        params: HttpRequestBuilder.() -> Unit = {},
    ): ListingData = getJson<RedditResponse<ListingData>>(path, params).data

    private class FormResponse(
        val isSuccess: Boolean,
        val body: String,
    )

    // POSTs a form to an API path, leaving out fields whose value is null. The body is always read, which releases
    // the connection.
    private suspend fun postForm(
        path: String,
        vararg fields: Pair<String, String?>,
    ): FormResponse {
        val response =
            authenticatedRequest {
                method = HttpMethod.Post
                url("$BASE_URL$path")
                if (fields.isNotEmpty()) {
                    setBody(FormDataContent(Parameters.build { fields.forEach { (name, value) -> value?.let { append(name, it) } } }))
                }
            }
        val body = response.bodyAsText()
        return FormResponse(response.status.isSuccess(), body)
    }

    private fun HttpRequestBuilder.page(
        after: String?,
        limit: Int,
    ) {
        parameter("limit", limit)
        after?.let { parameter("after", it) }
    }

    // The time filter only applies to the top and controversial sorts.
    private fun HttpRequestBuilder.userContent(
        sort: PostSort,
        timeFilter: TimeFilter?,
        after: String?,
        limit: Int,
    ) {
        parameter("sort", sort.value)
        if (sort == PostSort.TOP || sort == PostSort.CONTROVERSIAL) {
            parameter("t", (timeFilter ?: TimeFilter.ALL).value)
        }
        page(after, limit)
    }

    // Parses a page of posts, and looks up in the background whether their subreddits are NSFW when that isn't known.
    private fun parsePostListing(data: ListingData): Listing<Post> {
        val uncachedSubreddits = parser.postSubreddits(data).filter { settingsRepository?.isSubredditNsfwCached(it) != true }
        if (uncachedSubreddits.isNotEmpty()) {
            apiScope.launch {
                fetchSubredditsBatch(uncachedSubreddits)
            }
        }
        return parser.postListing(data)
    }

    // ==================== Posts ====================

    open suspend fun getPosts(
        subreddit: String? = null,
        sort: PostSort = PostSort.HOT,
        time: TimeFilter? = null,
        after: String? = null,
        limit: Int = 25,
    ): Listing<Post> {
        val path = if (subreddit != null) "/r/$subreddit/${sort.value}" else "/${sort.value}"
        return parsePostListing(
            getListing(path) {
                page(after, limit)
                time?.let { parameter("t", it.value) }
            },
        )
    }

    open suspend fun fetchPostsByIds(fullnames: List<String>): List<Post> =
        runSuspendCatching {
            parsePostListing(getListing("/api/info") { parameter("id", fullnames.joinToString(",")) }).items
        }.getOrDefault(emptyList())

    open suspend fun getPost(
        subreddit: String,
        postId: String,
    ): Post? {
        val listings = getJson<List<RedditResponse<ListingData>>>("/r/$subreddit/comments/$postId") { parameter("limit", 1) }
        val postData =
            listings
                .firstOrNull()
                ?.data
                ?.children
                ?.firstOrNull()
                ?.data ?: return null
        return parser.post(postData)
    }

    open suspend fun getPostWithComments(
        subreddit: String,
        postId: String,
        sort: CommentSort? = CommentSort.CONFIDENCE,
        limit: Int = 200,
        commentId: String? = null,
        context: Int? = null,
    ): Pair<Post, List<CommentOrMore>> {
        val commentPath = if (commentId != null) "/comment/$commentId" else ""
        val listings =
            getJson<List<RedditResponse<ListingData>>>("/r/$subreddit/comments/$postId$commentPath") {
                if (sort != null) parameter("sort", sort.value)
                parameter("limit", limit)
                if (commentId != null) {
                    parameter("context", context ?: 0)
                }
            }
        val post =
            parser.post(
                listings[0]
                    .data.children
                    .first()
                    .data,
            )
        // The second listing holds the comments; it is missing when the post has none.
        val commentChildren = listings.getOrNull(1)?.data?.children
        val comments = parser.commentsOrMore(commentChildren.orEmpty())
        return post to comments
    }

    open suspend fun getMoreComments(
        linkId: String,
        children: List<String>,
        sort: CommentSort = CommentSort.CONFIDENCE,
    ): List<CommentOrMore> {
        val response =
            getJson<MoreChildrenResponse>("/api/morechildren") {
                parameter("api_type", "json")
                parameter("link_id", linkId)
                parameter("children", children.joinToString(","))
                parameter("sort", sort.value)
            }
        return response.json.data
            ?.things
            ?.let { parser.commentsOrMore(it) }
            .orEmpty()
    }

    // ==================== Voting ====================

    open suspend fun vote(
        thingId: String,
        direction: Int,
    ): Boolean {
        val response = postForm("/api/vote", "id" to thingId, "dir" to direction.toString())
        if (!response.isSuccess) parser.parseApiErrors(response.body)?.let { throw it }
        return response.isSuccess
    }

    // ==================== Save ====================

    open suspend fun save(thingId: String): Boolean = postForm("/api/save", "id" to thingId).isSuccess

    open suspend fun unsave(thingId: String): Boolean = postForm("/api/unsave", "id" to thingId).isSuccess

    // ==================== Hide ====================

    open suspend fun hide(thingId: String): Boolean = postForm("/api/hide", "id" to thingId).isSuccess

    open suspend fun unhide(thingId: String): Boolean = postForm("/api/unhide", "id" to thingId).isSuccess

    // ==================== Subreddits ====================

    open suspend fun getSubreddit(name: String): Subreddit? = parser.subreddit(getJson<RedditResponse<SubredditDto>>("/r/$name/about").data)

    open suspend fun getSubscribedSubreddits(
        after: String? = null,
        limit: Int = 100,
    ): Listing<Subreddit> = parser.subredditListing(getListing("/subreddits/mine/subscriber") { page(after, limit) })

    open suspend fun subscribe(subredditName: String): Boolean =
        postForm("/api/subscribe", "action" to "sub", "sr_name" to subredditName).isSuccess

    open suspend fun unsubscribe(subredditName: String): Boolean =
        postForm("/api/subscribe", "action" to "unsub", "sr_name" to subredditName).isSuccess

    open suspend fun searchSubreddits(
        query: String,
        limit: Int = 10,
        includeOver18: Boolean = true,
    ): List<Subreddit> {
        val listing =
            getListing("/subreddits/search") {
                parameter("q", query)
                parameter("limit", limit)
                parameter("include_over_18", includeOver18)
            }
        val subreddits = parser.subredditListing(listing).items
        return if (includeOver18) subreddits else subreddits.filter { !it.isNsfw }
    }

    open suspend fun getPopularSubreddits(
        after: String? = null,
        limit: Int = 25,
    ): Listing<Subreddit> = parser.subredditListing(getListing("/subreddits/popular") { page(after, limit) })

    // ==================== User ====================

    open suspend fun getMe(): User? {
        val response =
            authenticatedRequest {
                method = HttpMethod.Get
                url("$BASE_URL/api/v1/me")
                parameter("raw_json", 1)
            }

        val body = response.bodyAsText() // Always consume body to release connection
        if (!response.status.isSuccess()) return null
        return parser.user(json.decodeFromString<UserDto>(body))
    }

    open suspend fun getUser(username: String): User? = parser.user(getJson<RedditResponse<UserDto>>("/user/$username/about").data)

    open suspend fun getUserPosts(
        username: String,
        sort: PostSort = PostSort.NEW,
        timeFilter: TimeFilter? = null,
        after: String? = null,
        limit: Int = 25,
    ): Listing<Post> = parsePostListing(getListing("/user/$username/submitted") { userContent(sort, timeFilter, after, limit) })

    open suspend fun getUserComments(
        username: String,
        sort: PostSort = PostSort.NEW,
        timeFilter: TimeFilter? = null,
        after: String? = null,
        limit: Int = 25,
    ): Listing<Comment> = parser.commentListing(getListing("/user/$username/comments") { userContent(sort, timeFilter, after, limit) })

    open suspend fun getSavedPosts(
        username: String,
        after: String? = null,
        limit: Int = 25,
    ): Listing<Post> =
        parsePostListing(
            getListing("/user/$username/saved") {
                parameter("type", "links")
                page(after, limit)
            },
        )

    open suspend fun getSavedComments(
        username: String,
        after: String? = null,
        limit: Int = 25,
    ): Listing<Comment> =
        parser.commentListing(
            getListing("/user/$username/saved") {
                parameter("type", "comments")
                page(after, limit)
            },
        )

    open suspend fun getUpvotedPosts(
        username: String,
        after: String? = null,
        limit: Int = 25,
    ): Listing<Post> = parsePostListing(getListing("/user/$username/upvoted") { page(after, limit) })

    open suspend fun getDownvotedPosts(
        username: String,
        after: String? = null,
        limit: Int = 25,
    ): Listing<Post> = parsePostListing(getListing("/user/$username/downvoted") { page(after, limit) })

    // ==================== Search ====================

    open suspend fun search(
        query: String,
        subreddit: String? = null,
        sort: SearchSort = SearchSort.RELEVANCE,
        time: TimeFilter = TimeFilter.ALL,
        after: String? = null,
        limit: Int = 25,
    ): Listing<Post> {
        val path = if (subreddit != null) "/r/$subreddit/search" else "/search"
        return parsePostListing(
            getListing(path) {
                parameter("q", query)
                parameter("sort", sort.value)
                parameter("t", time.value)
                parameter("type", "link")
                parameter("restrict_sr", subreddit != null)
                page(after, limit)
            },
        )
    }

    // ==================== Comments ====================

    open suspend fun submitComment(
        parentId: String,
        text: String,
    ): Comment? {
        val response = postForm("/api/comment", "api_type" to "json", "thing_id" to parentId, "text" to text)
        // Reddit reports errors such as rate limits in the body, with a success status too.
        parser.parseApiErrors(response.body)?.let { throw it }
        return if (response.isSuccess) parser.submittedComment(response.body) else null
    }

    open suspend fun editComment(
        thingId: String,
        text: String,
    ): Boolean = postForm("/api/editusertext", "api_type" to "json", "thing_id" to thingId, "text" to text).isSuccess

    open suspend fun deleteComment(thingId: String): Boolean = postForm("/api/del", "id" to thingId).isSuccess

    // ==================== Messages ====================

    open suspend fun getInbox(
        filter: InboxFilter = InboxFilter.ALL,
        after: String? = null,
        limit: Int = 25,
    ): Listing<Message> = parser.messageListing(getListing("/message/${filter.value}") { page(after, limit) })

    open suspend fun sendMessage(
        to: String,
        subject: String,
        body: String,
    ): Boolean = postForm("/api/compose", "api_type" to "json", "to" to to, "subject" to subject, "text" to body).isSuccess

    open suspend fun markMessageRead(thingId: String): Boolean = postForm("/api/read_message", "id" to thingId).isSuccess

    open suspend fun markMessageUnread(thingId: String): Boolean = postForm("/api/unread_message", "id" to thingId).isSuccess

    open suspend fun blockUser(username: String): Boolean = postForm("/api/block_user", "name" to username).isSuccess

    open suspend fun markAllMessagesRead(): Boolean = postForm("/api/read_all_messages").isSuccess

    // ==================== Submit ====================

    open suspend fun submitPost(
        subreddit: String,
        title: String,
        kind: String, // "self", "link", "image", "video"
        text: String? = null,
        url: String? = null,
        nsfw: Boolean = false,
        spoiler: Boolean = false,
        flairId: String? = null,
        flairText: String? = null,
    ): String? {
        val response =
            postForm(
                "/api/submit",
                "api_type" to "json",
                "sr" to subreddit,
                "title" to title,
                "kind" to kind,
                "text" to text,
                "url" to url,
                "nsfw" to nsfw.toString(),
                "spoiler" to spoiler.toString(),
                "flair_id" to flairId,
                "flair_text" to flairText,
            )
        return if (response.isSuccess) parser.submittedPostName(response.body) else null
    }

    // ==================== NSFW status of subreddits ====================

    private suspend fun fetchSubredditsBatch(subredditNames: List<String>) {
        subredditNames.chunked(MAX_SUBREDDITS_PER_INFO_REQUEST).forEach { batch ->
            runSuspendCatching {
                val listing = getListing("/api/info.json") { parameter("sr_name", batch.joinToString(",")) }
                parser.subredditListing(listing).items.forEach { subreddit ->
                    settingsRepository?.setSubredditNsfw(subreddit.displayName, subreddit.isNsfw)
                }
            }.onFailure { e ->
                Napier.e("Failed to batch fetch subreddits", e)
                batch.forEach { fetchAndCacheSubredditNsfw(it) }
            }
        }
    }

    private suspend fun fetchAndCacheSubredditNsfw(subredditName: String) {
        runSuspendCatching {
            val subreddit = getSubreddit(subredditName)
            settingsRepository?.setSubredditNsfw(subredditName, subreddit?.isNsfw ?: false)
        }.onFailure { e ->
            Napier.e("Failed to fetch subreddit NSFW status: $subredditName", e)
            settingsRepository?.setSubredditNsfw(subredditName, false)
        }
    }

    open suspend fun resolveShareLink(
        subreddit: String,
        shareId: String,
    ): Pair<String, String>? =
        runSuspendCatching {
            val response =
                shareLinkClient.get("$WWW_URL/r/$subreddit/s/$shareId") {
                    header(HttpHeaders.UserAgent, "KK/1.0.0 (Android)")
                }
            val locationUrl = response.headers[HttpHeaders.Location] ?: return null
            when (val parsed = parseRedditLink(locationUrl)) {
                is RedditLink.Post -> Pair(parsed.subreddit, parsed.postId)
                else -> null
            }
        }.getOrNull()
}

sealed interface CommentOrMore {
    data class CommentItem(
        val comment: Comment,
    ) : CommentOrMore

    data class More(
        val more: MoreComments,
    ) : CommentOrMore
}
