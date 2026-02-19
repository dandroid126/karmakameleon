package com.reader.shared.data.api

import com.reader.shared.data.api.dto.AccountDto
import com.reader.shared.data.api.dto.CommentDto
import com.reader.shared.data.api.dto.GalleryDataDto
import com.reader.shared.data.api.dto.ListingData
import com.reader.shared.data.api.dto.MediaDto
import com.reader.shared.data.api.dto.MediaMetadataDto
import com.reader.shared.data.api.dto.MessageDto
import com.reader.shared.data.api.dto.MoreChildrenResponse
import com.reader.shared.data.api.dto.MoreCommentsDto
import com.reader.shared.data.api.dto.PostDto
import com.reader.shared.data.api.dto.PreviewDto
import com.reader.shared.data.api.dto.RedditResponse
import com.reader.shared.data.api.dto.SubredditDto
import com.reader.shared.data.api.dto.ThingData
import com.reader.shared.data.api.dto.UserDto
import com.reader.shared.data.api.dto.UserSubredditDto
import com.reader.shared.domain.model.Account
import com.reader.shared.domain.model.Comment
import com.reader.shared.domain.model.CommentSort
import com.reader.shared.domain.model.FlairRichtext
import com.reader.shared.domain.model.GalleryData
import com.reader.shared.domain.model.GalleryItem
import com.reader.shared.domain.model.ImageSource
import com.reader.shared.domain.model.InboxFilter
import com.reader.shared.domain.model.Listing
import com.reader.shared.domain.model.Media
import com.reader.shared.domain.model.Message
import com.reader.shared.domain.model.MessageType
import com.reader.shared.domain.model.MoreComments
import com.reader.shared.domain.model.Post
import com.reader.shared.domain.model.PostSort
import com.reader.shared.domain.model.Preview
import com.reader.shared.domain.model.PreviewImage
import com.reader.shared.domain.model.RedditVideo
import com.reader.shared.domain.model.SearchSort
import com.reader.shared.domain.model.Subreddit
import com.reader.shared.domain.model.TimeFilter
import com.reader.shared.domain.model.User
import com.reader.shared.domain.model.UserSubreddit
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
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
import kotlinx.serialization.json.Json
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

class RedditApi(
    private val httpClient: HttpClient,
    private val authManager: AuthManager,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    companion object {
        const val BASE_URL = "https://oauth.reddit.com"
        const val WWW_URL = "https://www.reddit.com"
        const val AUTH_URL = "https://www.reddit.com/api/v1"
    }

    private suspend fun authenticatedRequest(
        block: HttpRequestBuilder.() -> Unit
    ): HttpResponse {
        val token = authManager.getAccessToken()
            ?: throw IllegalStateException("No Reddit Client ID configured. Please enter your Client ID in the Profile tab.")
        return httpClient.request {
            block()
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.UserAgent, "Reader/1.0.0 (Android)")
        }
    }

    // ==================== Posts ====================

    suspend fun getPosts(
        subreddit: String? = null,
        sort: PostSort = PostSort.HOT,
        time: TimeFilter? = null,
        after: String? = null,
        limit: Int = 25
    ): Listing<Post> {
        val path = if (subreddit != null) "/r/$subreddit/${sort.value}" else "/${sort.value}"
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL$path")
            parameter("limit", limit)
            parameter("raw_json", 1)
            after?.let { parameter("after", it) }
            time?.let { parameter("t", it.value) }
        }
        
        val body = response.bodyAsText()
        val redditResponse = json.decodeFromString<RedditResponse<ListingData>>(body)
        return parsePostListing(redditResponse.data)
    }

    suspend fun fetchPostsByIds(fullnames: List<String>): List<Post> {
        return try {
            val response = authenticatedRequest {
                method = HttpMethod.Get
                url("$BASE_URL/api/info")
                parameter("id", fullnames.joinToString(","))
                parameter("raw_json", 1)
            }
            val body = response.bodyAsText()
            val redditResponse = json.decodeFromString<RedditResponse<ListingData>>(body)
            parsePostListing(redditResponse.data).items
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun getPost(subreddit: String, postId: String): Post? {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/r/$subreddit/comments/$postId")
            parameter("limit", 1)
            parameter("raw_json", 1)
        }
        
        val body = response.bodyAsText()
        val listings = json.decodeFromString<List<RedditResponse<ListingData>>>(body)
        val postData = listings.firstOrNull()?.data?.children?.firstOrNull()?.data ?: return null
        return parsePost(postData)
    }

    suspend fun getPostWithComments(
        subreddit: String,
        postId: String,
        sort: CommentSort = CommentSort.CONFIDENCE,
        limit: Int = 200,
        commentId: String? = null,
        context: Int? = null
    ): Pair<Post, List<CommentOrMore>> {
        val commentPath = if (commentId != null) "/comment/$commentId" else ""
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/r/$subreddit/comments/$postId$commentPath")
            parameter("sort", sort.value)
            parameter("limit", limit)
            parameter("raw_json", 1)
            if (commentId != null) {
                parameter("context", context ?: 0)
            }
        }
        
        val body = response.bodyAsText()
        val listings = json.decodeFromString<List<RedditResponse<ListingData>>>(body)
        
        val post = parsePost(listings[0].data.children.first().data)
        val comments = parseComments(listings.getOrNull(1)?.data?.children ?: emptyList())
        
        return post to comments
    }

    suspend fun getMoreComments(
        linkId: String,
        children: List<String>,
        sort: CommentSort = CommentSort.CONFIDENCE
    ): List<CommentOrMore> {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/api/morechildren")
            parameter("api_type", "json")
            parameter("link_id", linkId)
            parameter("children", children.joinToString(","))
            parameter("sort", sort.value)
            parameter("raw_json", 1)
        }
        
        val body = response.bodyAsText()
        val moreResponse = json.decodeFromString<MoreChildrenResponse>(body)
        return moreResponse.json.data?.things?.mapNotNull { thing ->
            when (thing.kind) {
                "t1" -> CommentOrMore.CommentItem(parseComment(thing.data, 0))
                "more" -> {
                    val more = json.decodeFromJsonElement<MoreCommentsDto>(thing.data)
                    CommentOrMore.More(parseMoreComments(more))
                }
                else -> null
            }
        } ?: emptyList()
    }

    // ==================== Voting ====================

    suspend fun vote(thingId: String, direction: Int): Boolean {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/vote")
            setBody(FormDataContent(Parameters.build {
                append("id", thingId)
                append("dir", direction.toString())
            }))
        }
        response.bodyAsText() // Consume body to release connection
        return response.status.isSuccess()
    }

    // ==================== Save ====================

    suspend fun save(thingId: String): Boolean {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/save")
            setBody(FormDataContent(Parameters.build {
                append("id", thingId)
            }))
        }
        response.bodyAsText() // Consume body to release connection
        return response.status.isSuccess()
    }

    suspend fun unsave(thingId: String): Boolean {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/unsave")
            setBody(FormDataContent(Parameters.build {
                append("id", thingId)
            }))
        }
        response.bodyAsText() // Consume body to release connection
        return response.status.isSuccess()
    }

    // ==================== Hide ====================

    suspend fun hide(thingId: String): Boolean {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/hide")
            setBody(FormDataContent(Parameters.build {
                append("id", thingId)
            }))
        }
        response.bodyAsText() // Consume body to release connection
        return response.status.isSuccess()
    }

    suspend fun unhide(thingId: String): Boolean {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/unhide")
            setBody(FormDataContent(Parameters.build {
                append("id", thingId)
            }))
        }
        response.bodyAsText() // Consume body to release connection
        return response.status.isSuccess()
    }

    // ==================== Subreddits ====================

    suspend fun getSubreddit(name: String): Subreddit? {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/r/$name/about")
            parameter("raw_json", 1)
        }
        
        val body = response.bodyAsText()
        val redditResponse = json.decodeFromString<RedditResponse<SubredditDto>>(body)
        return mapSubreddit(redditResponse.data)
    }

    suspend fun getSubscribedSubreddits(
        after: String? = null,
        limit: Int = 100
    ): Listing<Subreddit> {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/subreddits/mine/subscriber")
            parameter("limit", limit)
            parameter("raw_json", 1)
            after?.let { parameter("after", it) }
        }
        
        val body = response.bodyAsText()
        val redditResponse = json.decodeFromString<RedditResponse<ListingData>>(body)
        return parseSubredditListing(redditResponse.data)
    }

    suspend fun subscribe(subredditName: String): Boolean {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/subscribe")
            setBody(FormDataContent(Parameters.build {
                append("action", "sub")
                append("sr_name", subredditName)
            }))
        }
        response.bodyAsText() // Consume body to release connection
        return response.status.isSuccess()
    }

    suspend fun unsubscribe(subredditName: String): Boolean {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/subscribe")
            setBody(FormDataContent(Parameters.build {
                append("action", "unsub")
                append("sr_name", subredditName)
            }))
        }
        response.bodyAsText() // Consume body to release connection
        return response.status.isSuccess()
    }

    suspend fun searchSubreddits(query: String, limit: Int = 10): List<Subreddit> {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/subreddits/search")
            parameter("q", query)
            parameter("limit", limit)
            parameter("raw_json", 1)
        }
        
        val body = response.bodyAsText()
        val redditResponse = json.decodeFromString<RedditResponse<ListingData>>(body)
        return parseSubredditListing(redditResponse.data).items
    }

    suspend fun getPopularSubreddits(
        after: String? = null,
        limit: Int = 25
    ): Listing<Subreddit> {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/subreddits/popular")
            parameter("limit", limit)
            parameter("raw_json", 1)
            after?.let { parameter("after", it) }
        }
        
        val body = response.bodyAsText()
        val redditResponse = json.decodeFromString<RedditResponse<ListingData>>(body)
        return parseSubredditListing(redditResponse.data)
    }

    // ==================== User ====================

    suspend fun getMe(): Account? {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/api/v1/me")
            parameter("raw_json", 1)
        }
        
        val body = response.bodyAsText() // Always consume body to release connection
        if (!response.status.isSuccess()) return null
        
        val accountDto = json.decodeFromString<AccountDto>(body)
        return mapAccount(accountDto)
    }

    suspend fun getUser(username: String): User? {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/user/$username/about")
            parameter("raw_json", 1)
        }
        
        val body = response.bodyAsText()
        val redditResponse = json.decodeFromString<RedditResponse<UserDto>>(body)
        return mapUser(redditResponse.data)
    }

    suspend fun getUserPosts(
        username: String,
        sort: PostSort = PostSort.NEW,
        timeFilter: TimeFilter? = null,
        after: String? = null,
        limit: Int = 25
    ): Listing<Post> {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/user/$username/submitted")
            parameter("sort", sort.value)
            if (sort == PostSort.TOP || sort == PostSort.CONTROVERSIAL) {
                parameter("t", (timeFilter ?: TimeFilter.ALL).value)
            }
            parameter("limit", limit)
            parameter("raw_json", 1)
            after?.let { parameter("after", it) }
        }
        
        val body = response.bodyAsText()
        val redditResponse = json.decodeFromString<RedditResponse<ListingData>>(body)
        return parsePostListing(redditResponse.data)
    }

    suspend fun getUserComments(
        username: String,
        sort: PostSort = PostSort.NEW,
        timeFilter: TimeFilter? = null,
        after: String? = null,
        limit: Int = 25
    ): Listing<Comment> {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/user/$username/comments")
            parameter("sort", sort.value)
            if (sort == PostSort.TOP || sort == PostSort.CONTROVERSIAL) {
                parameter("t", (timeFilter ?: TimeFilter.ALL).value)
            }
            parameter("limit", limit)
            parameter("raw_json", 1)
            after?.let { parameter("after", it) }
        }
        
        val body = response.bodyAsText()
        val redditResponse = json.decodeFromString<RedditResponse<ListingData>>(body)
        return parseCommentListing(redditResponse.data)
    }

    suspend fun getSavedPosts(
        username: String,
        after: String? = null,
        limit: Int = 25
    ): Listing<Post> {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/user/$username/saved")
            parameter("type", "links")
            parameter("limit", limit)
            parameter("raw_json", 1)
            after?.let { parameter("after", it) }
        }
        
        val body = response.bodyAsText()
        val redditResponse = json.decodeFromString<RedditResponse<ListingData>>(body)
        return parsePostListing(redditResponse.data)
    }

    suspend fun getSavedComments(
        username: String,
        after: String? = null,
        limit: Int = 25
    ): Listing<Comment> {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/user/$username/saved")
            parameter("type", "comments")
            parameter("limit", limit)
            parameter("raw_json", 1)
            after?.let { parameter("after", it) }
        }
        
        val body = response.bodyAsText()
        val redditResponse = json.decodeFromString<RedditResponse<ListingData>>(body)
        return parseCommentListing(redditResponse.data)
    }

    suspend fun getUpvotedPosts(
        username: String,
        after: String? = null,
        limit: Int = 25
    ): Listing<Post> {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/user/$username/upvoted")
            parameter("limit", limit)
            parameter("raw_json", 1)
            after?.let { parameter("after", it) }
        }
        
        val body = response.bodyAsText()
        val redditResponse = json.decodeFromString<RedditResponse<ListingData>>(body)
        return parsePostListing(redditResponse.data)
    }

    suspend fun getDownvotedPosts(
        username: String,
        after: String? = null,
        limit: Int = 25
    ): Listing<Post> {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/user/$username/downvoted")
            parameter("limit", limit)
            parameter("raw_json", 1)
            after?.let { parameter("after", it) }
        }
        
        val body = response.bodyAsText()
        val redditResponse = json.decodeFromString<RedditResponse<ListingData>>(body)
        return parsePostListing(redditResponse.data)
    }

    // ==================== Search ====================

    suspend fun search(
        query: String,
        subreddit: String? = null,
        sort: SearchSort = SearchSort.RELEVANCE,
        time: TimeFilter = TimeFilter.ALL,
        after: String? = null,
        limit: Int = 25
    ): Listing<Post> {
        val path = if (subreddit != null) "/r/$subreddit/search" else "/search"
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL$path")
            parameter("q", query)
            parameter("sort", sort.value)
            parameter("t", time.value)
            parameter("type", "link")
            parameter("limit", limit)
            parameter("raw_json", 1)
            parameter("restrict_sr", subreddit != null)
            after?.let { parameter("after", it) }
        }
        
        val body = response.bodyAsText()
        val redditResponse = json.decodeFromString<RedditResponse<ListingData>>(body)
        return parsePostListing(redditResponse.data)
    }

    // ==================== Comments ====================

    suspend fun submitComment(parentId: String, text: String): Comment? {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/comment")
            setBody(FormDataContent(Parameters.build {
                append("api_type", "json")
                append("thing_id", parentId)
                append("text", text)
            }))
        }
        
        val body = response.bodyAsText() // Always consume body to release connection
        if (!response.status.isSuccess()) return null
        // Parse the response to extract the new comment
        return try {
            val jsonResponse = json.parseToJsonElement(body).jsonObject
            val data = jsonResponse["json"]?.jsonObject?.get("data")?.jsonObject
            val things = data?.get("things")?.jsonArray?.firstOrNull()?.jsonObject
            things?.get("data")?.let { parseComment(it, 0) }
        } catch (e: Exception) {
            Napier.e("Failed to parse comment response", e)
            null
        }
    }

    suspend fun editComment(thingId: String, text: String): Boolean {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/editusertext")
            setBody(FormDataContent(Parameters.build {
                append("api_type", "json")
                append("thing_id", thingId)
                append("text", text)
            }))
        }
        response.bodyAsText() // Consume body to release connection
        return response.status.isSuccess()
    }

    suspend fun deleteComment(thingId: String): Boolean {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/del")
            setBody(FormDataContent(Parameters.build {
                append("id", thingId)
            }))
        }
        response.bodyAsText() // Consume body to release connection
        return response.status.isSuccess()
    }

    // ==================== Messages ====================

    suspend fun getInbox(
        filter: InboxFilter = InboxFilter.ALL,
        after: String? = null,
        limit: Int = 25
    ): Listing<Message> {
        val response = authenticatedRequest {
            method = HttpMethod.Get
            url("$BASE_URL/message/${filter.value}")
            parameter("limit", limit)
            parameter("raw_json", 1)
            after?.let { parameter("after", it) }
        }
        
        val body = response.bodyAsText()
        val redditResponse = json.decodeFromString<RedditResponse<ListingData>>(body)
        return parseMessageListing(redditResponse.data)
    }

    suspend fun sendMessage(to: String, subject: String, body: String): Boolean {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/compose")
            setBody(FormDataContent(Parameters.build {
                append("api_type", "json")
                append("to", to)
                append("subject", subject)
                append("text", body)
            }))
        }
        response.bodyAsText() // Consume body to release connection
        return response.status.isSuccess()
    }

    suspend fun markMessageRead(thingId: String): Boolean {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/read_message")
            setBody(FormDataContent(Parameters.build {
                append("id", thingId)
            }))
        }
        response.bodyAsText() // Consume body to release connection
        return response.status.isSuccess()
    }

    suspend fun markMessageUnread(thingId: String): Boolean {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/unread_message")
            setBody(FormDataContent(Parameters.build {
                append("id", thingId)
            }))
        }
        response.bodyAsText() // Consume body to release connection
        return response.status.isSuccess()
    }

    suspend fun blockUser(username: String): Boolean {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/block_user")
            setBody(FormDataContent(Parameters.build {
                append("name", username)
            }))
        }
        response.bodyAsText() // Consume body to release connection
        return response.status.isSuccess()
    }

    suspend fun markAllMessagesRead(): Boolean {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/read_all_messages")
        }
        response.bodyAsText() // Consume body to release connection
        return response.status.isSuccess()
    }

    // ==================== Submit ====================

    suspend fun submitPost(
        subreddit: String,
        title: String,
        kind: String, // "self", "link", "image", "video"
        text: String? = null,
        url: String? = null,
        nsfw: Boolean = false,
        spoiler: Boolean = false,
        flairId: String? = null,
        flairText: String? = null
    ): String? {
        val response = authenticatedRequest {
            method = HttpMethod.Post
            url("$BASE_URL/api/submit")
            setBody(FormDataContent(Parameters.build {
                append("api_type", "json")
                append("sr", subreddit)
                append("title", title)
                append("kind", kind)
                text?.let { append("text", it) }
                url?.let { append("url", it) }
                append("nsfw", nsfw.toString())
                append("spoiler", spoiler.toString())
                flairId?.let { append("flair_id", it) }
                flairText?.let { append("flair_text", it) }
            }))
        }
        
        val body = response.bodyAsText() // Always consume body to release connection
        if (!response.status.isSuccess()) return null
        
        return try {
            val jsonResponse = json.parseToJsonElement(body).jsonObject
            val data = jsonResponse["json"]?.jsonObject?.get("data")?.jsonObject
            data?.get("name")?.jsonPrimitive?.content
        } catch (e: Exception) {
            Napier.e("Failed to parse submit response", e)
            null
        }
    }

    // ==================== Parsing Helpers ====================

    private fun parsePostListing(data: ListingData): Listing<Post> {
        val posts = data.children.mapNotNull { thing ->
            if (thing.kind == "t3") parsePost(thing.data) else null
        }
        return Listing(
            items = posts,
            after = data.after,
            before = data.before,
            dist = data.dist
        )
    }

    private fun parseSubredditListing(data: ListingData): Listing<Subreddit> {
        val subreddits = data.children.mapNotNull { thing ->
            if (thing.kind == "t5") {
                val dto = json.decodeFromJsonElement<SubredditDto>(thing.data)
                mapSubreddit(dto)
            } else null
        }
        return Listing(
            items = subreddits,
            after = data.after,
            before = data.before,
            dist = data.dist
        )
    }

    private fun parseCommentListing(data: ListingData): Listing<Comment> {
        val comments = data.children.mapNotNull { thing ->
            if (thing.kind == "t1") parseComment(thing.data, 0) else null
        }
        return Listing(
            items = comments,
            after = data.after,
            before = data.before,
            dist = data.dist
        )
    }

    private fun parseMessageListing(data: ListingData): Listing<Message> {
        val messages = data.children.mapNotNull { thing ->
            if (thing.kind == "t4" || thing.kind == "t1") {
                val dto = json.decodeFromJsonElement<MessageDto>(thing.data)
                mapMessage(dto)
            } else null
        }
        return Listing(
            items = messages,
            after = data.after,
            before = data.before,
            dist = data.dist
        )
    }

    private fun parsePost(data: JsonElement): Post {
        val dto = json.decodeFromJsonElement<PostDto>(data)
        return mapPost(dto)
    }

    private fun parseComment(data: JsonElement, depth: Int): Comment {
        val dto = json.decodeFromJsonElement<CommentDto>(data)
        return mapComment(dto, depth)
    }

    private fun parseComments(children: List<ThingData>): List<CommentOrMore> {
        return children.mapNotNull { thing ->
            when (thing.kind) {
                "t1" -> CommentOrMore.CommentItem(parseComment(thing.data, 0))
                "more" -> {
                    val more = json.decodeFromJsonElement<MoreCommentsDto>(thing.data)
                    CommentOrMore.More(parseMoreComments(more))
                }
                else -> null
            }
        }
    }

    private fun parseMoreComments(dto: MoreCommentsDto): MoreComments {
        return MoreComments(
            id = dto.id,
            name = dto.name,
            parentId = dto.parentId,
            count = dto.count,
            depth = dto.depth,
            children = dto.children
        )
    }

    // ==================== Mappers ====================

    private fun mapPost(dto: PostDto): Post {
        return Post(
            id = dto.id,
            name = dto.name,
            title = decodeHtml(dto.title),
            author = dto.author,
            subreddit = dto.subreddit,
            subredditId = dto.subredditId,
            selfText = dto.selftext?.let { decodeHtml(it) },
            selfTextHtml = dto.selftextHtml,
            url = resolveRedditMediaUrl(decodeHtml(dto.url)),
            permalink = dto.permalink,
            thumbnail = dto.thumbnail?.takeIf { it.startsWith("http") },
            thumbnailWidth = dto.thumbnailWidth,
            thumbnailHeight = dto.thumbnailHeight,
            preview = (dto.preview ?: dto.crosspostParentList?.firstOrNull()?.preview)?.let { mapPreview(it) },
            score = dto.score,
            upvoteRatio = dto.upvoteRatio,
            numComments = dto.numComments,
            created = dto.created.toLong(),
            createdUtc = dto.createdUtc.toLong(),
            isNsfw = dto.over18,
            isSpoiler = dto.spoiler,
            isStickied = dto.stickied,
            isLocked = dto.locked,
            isArchived = dto.archived,
            isSaved = dto.saved,
            isHidden = dto.hidden,
            likes = dto.likes,
            domain = dto.domain,
            postHint = dto.postHint,
            linkFlairText = dto.linkFlairText,
            linkFlairBackgroundColor = dto.linkFlairBackgroundColor,
            linkFlairTextColor = dto.linkFlairTextColor,
            authorFlairText = dto.authorFlairText,
            distinguished = dto.distinguished,
            media = (dto.media ?: dto.crosspostParentList?.firstOrNull()?.media)?.let { mapMedia(it) },
            galleryData = (dto.galleryData ?: dto.crosspostParentList?.firstOrNull()?.galleryData)?.let {
                mapGallery(it, dto.mediaMetadata ?: dto.crosspostParentList?.firstOrNull()?.mediaMetadata)
            },
            crosspostParent = dto.crosspostParent,
            isCrosspost = dto.crosspostParent != null
        )
    }

    private fun mapPreview(dto: PreviewDto): Preview {
        return Preview(
            images = dto.images.map { img ->
                PreviewImage(
                    source = ImageSource(
                        url = decodeHtml(img.source.url),
                        width = img.source.width,
                        height = img.source.height
                    ),
                    resolutions = img.resolutions.map { res ->
                        ImageSource(
                            url = decodeHtml(res.url),
                            width = res.width,
                            height = res.height
                        )
                    },
                    mp4Url = img.variants?.mp4?.source?.url?.let { decodeHtml(it) }
                )
            },
            enabled = dto.enabled,
            redditVideoPreview = dto.redditVideoPreview?.let {
                RedditVideo(
                    fallbackUrl = it.fallbackUrl,
                    height = it.height,
                    width = it.width,
                    duration = it.duration,
                    isGif = it.isGif
                )
            }
        )
    }

    private fun mapMedia(dto: MediaDto): Media {
        return Media(
            redditVideo = dto.redditVideo?.let {
                RedditVideo(
                    fallbackUrl = it.fallbackUrl,
                    height = it.height,
                    width = it.width,
                    duration = it.duration,
                    isGif = it.isGif
                )
            }
        )
    }

    private fun mapGallery(dto: GalleryDataDto, metadata: Map<String, MediaMetadataDto>?): GalleryData {
        return GalleryData(
            items = dto.items.mapNotNull { item ->
                val meta = metadata?.get(item.mediaId)
                val isAnimated = meta?.e == "AnimatedImage"
                val mp4Url = meta?.s?.mp4?.let { decodeHtml(it) }
                val imageUrl = meta?.s?.u?.let { decodeHtml(it) }
                val url = if (isAnimated) mp4Url ?: imageUrl else imageUrl
                if (url != null || metadata == null) {
                    GalleryItem(
                        mediaId = item.mediaId,
                        id = item.id,
                        caption = item.caption,
                        url = url,
                        isVideo = isAnimated && mp4Url != null
                    )
                } else null
            }
        )
    }

    private fun mapComment(dto: CommentDto, depth: Int): Comment {
        val replies = parseReplies(dto.replies, depth + 1)
        val commentReplies = replies.filterIsInstance<CommentOrMore.CommentItem>().map { it.comment }
        val moreReplies = replies.filterIsInstance<CommentOrMore.More>().firstOrNull()?.more
        
        return Comment(
            id = dto.id,
            name = dto.name,
            parentId = dto.parentId,
            linkId = dto.linkId,
            author = dto.author,
            body = decodeHtml(dto.body),
            bodyHtml = dto.bodyHtml,
            score = dto.score,
            created = dto.created.toLong(),
            createdUtc = dto.createdUtc.toLong(),
            isSubmitter = dto.isSubmitter,
            distinguished = dto.distinguished,
            isStickied = dto.stickied,
            isLocked = dto.locked,
            isArchived = dto.archived,
            isSaved = dto.saved,
            isCollapsed = dto.collapsed,
            likes = dto.likes,
            depth = dto.depth,
            replies = commentReplies,
            moreReplies = moreReplies,
            authorFlairText = dto.authorFlairText,
            authorFlairBackgroundColor = dto.authorFlairBackgroundColor,
            authorFlairRichtext = dto.authorFlairRichtext?.map { rt ->
                FlairRichtext(
                    type = rt.e,
                    text = rt.t,
                    url = rt.u
                )
            } ?: emptyList(),
            scoreHidden = dto.scoreHidden,
            edited = parseEdited(dto.edited),
            subreddit = dto.subreddit,
            permalink = dto.permalink ?: ""
        )
    }

    private fun parseReplies(replies: JsonElement?, depth: Int): List<CommentOrMore> {
        if (replies == null) return emptyList()
        
        return try {
            when {
                replies is JsonPrimitive && replies.isString && replies.content.isEmpty() -> emptyList()
                replies is JsonObject -> {
                    val data = replies["data"]?.jsonObject
                    val children = data?.get("children")?.jsonArray ?: return emptyList()
                    children.mapNotNull { child ->
                        val childObj = child.jsonObject
                        val kind = childObj["kind"]?.jsonPrimitive?.content
                        val childData = childObj["data"]
                        when (kind) {
                            "t1" -> childData?.let { CommentOrMore.CommentItem(parseComment(it, depth)) }
                            "more" -> childData?.let {
                                val more = json.decodeFromJsonElement<MoreCommentsDto>(it)
                                CommentOrMore.More(parseMoreComments(more))
                            }
                            else -> null
                        }
                    }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            Napier.e("Failed to parse replies", e)
            emptyList()
        }
    }

    private fun parseEdited(edited: JsonElement?): Long? {
        if (edited == null) return null
        return try {
            when {
                edited is JsonPrimitive && edited.booleanOrNull == false -> null
                edited is JsonPrimitive && edited.doubleOrNull != null -> edited.double.toLong()
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun mapSubreddit(dto: SubredditDto): Subreddit {
        return Subreddit(
            id = dto.id,
            name = dto.name,
            displayName = dto.displayName,
            displayNamePrefixed = dto.displayNamePrefixed,
            title = decodeHtml(dto.title),
            description = dto.description?.let { decodeHtml(it) },
            descriptionHtml = dto.descriptionHtml,
            publicDescription = dto.publicDescription?.let { decodeHtml(it) },
            subscribers = dto.subscribers,
            activeUserCount = dto.activeUserCount,
            created = dto.created.toLong(),
            createdUtc = dto.createdUtc.toLong(),
            isNsfw = dto.over18,
            isSubscribed = dto.userIsSubscriber,
            isFavorite = dto.userHasFavorited,
            iconUrl = dto.communityIcon?.takeIf { it.isNotEmpty() }?.let { decodeHtml(it) }
                ?: dto.iconImg?.takeIf { it.isNotEmpty() }?.let { decodeHtml(it) },
            bannerUrl = dto.bannerImg?.takeIf { it.isNotEmpty() }?.let { decodeHtml(it) },
            communityIcon = dto.communityIcon?.let { decodeHtml(it) },
            primaryColor = dto.primaryColor,
            keyColor = dto.keyColor,
            url = dto.url,
            subredditType = dto.subredditType,
            allowImages = dto.allowImages,
            allowVideos = dto.allowVideos,
            allowPolls = dto.allowPolls,
            spoilersEnabled = dto.spoilersEnabled,
            userIsModerator = dto.userIsModerator,
            userIsBanned = dto.userIsBanned,
            userIsContributor = dto.userIsContributor,
            userIsMuted = dto.userIsMuted
        )
    }

    private fun mapUser(dto: UserDto): User {
        return User(
            id = dto.id,
            name = dto.name,
            created = dto.created.toLong(),
            createdUtc = dto.createdUtc.toLong(),
            linkKarma = dto.linkKarma,
            commentKarma = dto.commentKarma,
            totalKarma = dto.totalKarma,
            iconUrl = dto.iconImg?.let { decodeHtml(it) } ?: dto.snoovatarImg?.let { decodeHtml(it) },
            bannerUrl = dto.subreddit?.bannerImg?.let { decodeHtml(it) },
            isGold = dto.isGold,
            isMod = dto.isMod,
            isVerified = dto.verified,
            hasVerifiedEmail = dto.hasVerifiedEmail,
            isSuspended = dto.isSuspended,
            isEmployee = dto.isEmployee,
            subreddit = dto.subreddit?.let { mapUserSubreddit(it) }
        )
    }

    private fun mapUserSubreddit(dto: UserSubredditDto): UserSubreddit {
        return UserSubreddit(
            displayName = dto.displayName,
            title = dto.title ?: "",
            publicDescription = dto.publicDescription,
            subscribers = dto.subscribers,
            iconUrl = dto.iconImg?.let { decodeHtml(it) },
            bannerUrl = dto.bannerImg?.let { decodeHtml(it) },
            isNsfw = dto.over18
        )
    }

    private fun mapAccount(dto: AccountDto): Account {
        return Account(
            id = dto.id,
            name = dto.name,
            created = dto.created.toLong(),
            createdUtc = dto.createdUtc.toLong(),
            linkKarma = dto.linkKarma,
            commentKarma = dto.commentKarma,
            totalKarma = dto.totalKarma,
            iconUrl = dto.iconImg?.let { decodeHtml(it) },
            inboxCount = dto.inboxCount,
            hasMail = dto.hasMail,
            hasModMail = dto.hasModMail,
            isGold = dto.isGold,
            isMod = dto.isMod,
            numFriends = dto.numFriends,
            over18 = dto.over18,
            prefNightMode = dto.prefNightMode,
            prefShowNsfw = dto.prefShowNsfw,
            prefNoProfanity = dto.prefNoProfanity
        )
    }

    private fun mapMessage(dto: MessageDto): Message {
        return Message(
            id = dto.id,
            name = dto.name,
            author = dto.author,
            dest = dto.dest,
            subject = decodeHtml(dto.subject),
            body = decodeHtml(dto.body),
            bodyHtml = dto.bodyHtml,
            created = dto.created.toLong(),
            createdUtc = dto.createdUtc.toLong(),
            isNew = dto.new,
            wasComment = dto.wasComment,
            context = dto.context,
            subreddit = dto.subreddit,
            parentId = dto.parentId,
            firstMessageName = dto.firstMessageName,
            replies = emptyList(), // TODO: Parse nested replies
            linkTitle = dto.linkTitle?.let { decodeHtml(it) },
            likes = dto.likes,
            type = when (dto.type) {
                "comment_reply" -> MessageType.COMMENT_REPLY
                "post_reply" -> MessageType.POST_REPLY
                "username_mention" -> MessageType.USERNAME_MENTION
                else -> when {
                    dto.wasComment && dto.context?.contains("/comments/") == true -> MessageType.COMMENT_REPLY
                    dto.wasComment -> MessageType.POST_REPLY
                    dto.subreddit != null -> MessageType.MOD_MESSAGE
                    else -> MessageType.PRIVATE_MESSAGE
                }
            }
        )
    }

    private fun resolveRedditMediaUrl(url: String): String {
        if (url.startsWith("https://www.reddit.com/media?url=") || url.startsWith("https://reddit.com/media?url=")) {
            val encoded = url.substringAfter("url=")
            return try {
                decodePercent(encoded)
            } catch (_: Exception) {
                url
            }
        }
        return url
    }

    private fun decodePercent(encoded: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < encoded.length) {
            if (encoded[i] == '%' && i + 2 < encoded.length) {
                val hex = encoded.substring(i + 1, i + 3)
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    sb.append(code.toChar())
                    i += 3
                    continue
                }
            }
            sb.append(encoded[i])
            i++
        }
        return sb.toString()
    }

    private fun decodeHtml(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }
}

sealed class CommentOrMore {
    data class CommentItem(val comment: Comment) : CommentOrMore()
    data class More(val more: MoreComments) : CommentOrMore()
}
