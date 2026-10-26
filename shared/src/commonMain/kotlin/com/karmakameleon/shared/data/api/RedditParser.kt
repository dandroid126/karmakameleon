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
import com.karmakameleon.shared.util.markdown.decodeHtmlEntities
import com.karmakameleon.shared.util.parseRedditLink
import com.karmakameleon.shared.util.runSuspendCatching
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

// "%" and two hex digits.
private const val PERCENT_ESCAPE_LENGTH = 3

/** Turns Reddit's JSON into the app's models. */
internal class RedditParser(
    private val settingsRepository: SettingsRepository?,
) {
    companion object {
        /** Reads Reddit's JSON: it has many fields the app doesn't use, and sends null for some that have a default. */
        val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                coerceInputValues = true
            }
    }

    // ==================== Listings ====================

    fun postListing(data: ListingData): Listing<Post> = data.toListing("t3") { post(it) }

    fun subredditListing(data: ListingData): Listing<Subreddit> =
        data.toListing("t5") { mapSubreddit(json.decodeFromJsonElement<SubredditDto>(it)) }

    fun commentListing(data: ListingData): Listing<Comment> = data.toListing("t1") { comment(it) }

    fun messageListing(data: ListingData): Listing<Message> =
        data.toListing("t4", "t1") { mapMessage(json.decodeFromJsonElement<MessageDto>(it)) }

    private fun <T> ListingData.toListing(
        vararg kinds: String,
        parse: (JsonElement) -> T,
    ): Listing<T> =
        Listing(
            items = children.filter { it.kind in kinds }.map { parse(it.data) },
            after = after,
            before = before,
            dist = dist,
        )

    /** The subreddits of the posts in a listing. */
    fun postSubreddits(data: ListingData): List<String> =
        data.children
            .filter { it.kind == "t3" }
            .mapNotNull { runCatching { json.decodeFromJsonElement<PostDto>(it.data).subreddit }.getOrNull() }
            .distinct()

    // ==================== Things ====================

    fun post(data: JsonElement): Post = mapPost(json.decodeFromJsonElement<PostDto>(data))

    fun comment(
        data: JsonElement,
        depth: Int = 0,
    ): Comment = mapComment(json.decodeFromJsonElement<CommentDto>(data), depth)

    /** Top-level comments and "more" entries, as returned with a post or by morechildren. */
    fun commentsOrMore(children: List<ThingData>): List<CommentOrMore> =
        children.mapNotNull { thing ->
            when (thing.kind) {
                "t1" -> CommentOrMore.CommentItem(comment(thing.data))
                "more" -> CommentOrMore.More(parseMoreComments(json.decodeFromJsonElement<MoreCommentsDto>(thing.data)))
                else -> null
            }
        }

    fun subreddit(dto: SubredditDto): Subreddit = mapSubreddit(dto)

    fun user(dto: UserDto): User = mapUser(dto)

    // ==================== Responses to actions ====================

    /** The first error Reddit reported in a response body, if any. */
    fun parseApiErrors(body: String): RedditApiException? =
        try {
            val jsonResponse = json.parseToJsonElement(body).jsonObject
            val errors =
                jsonResponse["json"]?.jsonObject?.get("errors")?.jsonArray
                    ?: jsonResponse["errors"]?.jsonArray
            if (!errors.isNullOrEmpty()) {
                val firstError = errors.firstOrNull()?.jsonArray
                val errorCode = firstError?.getOrNull(0)?.jsonPrimitive?.content ?: "UNKNOWN"
                val errorMessage = firstError?.getOrNull(1)?.jsonPrimitive?.content ?: "Unknown error"
                RedditApiException(errorCode, errorMessage)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }

    /** The comment in a response to submitting one, or null if it can't be read. */
    fun submittedComment(body: String): Comment? =
        try {
            val data =
                json
                    .parseToJsonElement(body)
                    .jsonObject["json"]
                    ?.jsonObject
                    ?.get("data")
                    ?.jsonObject
            val thing =
                data
                    ?.get("things")
                    ?.jsonArray
                    ?.firstOrNull()
                    ?.jsonObject
            thing?.get("data")?.let { comment(it) }
        } catch (e: IllegalArgumentException) {
            Napier.e("Failed to parse comment response", e)
            null
        }

    /** The fullname of the post in a response to submitting one, or null if it can't be read. */
    fun submittedPostName(body: String): String? =
        try {
            val data =
                json
                    .parseToJsonElement(body)
                    .jsonObject["json"]
                    ?.jsonObject
                    ?.get("data")
                    ?.jsonObject
            data?.get("name")?.jsonPrimitive?.content
        } catch (e: IllegalArgumentException) {
            Napier.e("Failed to parse submit response", e)
            null
        }

    // ==================== Mappers ====================

    private fun parseMoreComments(dto: MoreCommentsDto): MoreComments =
        MoreComments(
            id = dto.id,
            name = dto.name,
            parentId = dto.parentId,
            count = dto.count,
            depth = dto.depth,
            children = dto.children,
        )

    private fun mapPost(dto: PostDto): Post {
        val subredditNsfwKnown = settingsRepository?.isSubredditNsfwCached(dto.subreddit) == true
        val subredditIsNsfw = if (subredditNsfwKnown) settingsRepository.getSubredditNsfw(dto.subreddit) else false
        return Post(
            id = dto.id,
            name = dto.name,
            title = decodeHtmlEntities(dto.title),
            author = dto.author,
            subreddit = dto.subreddit,
            subredditId = dto.subredditId,
            selfText = dto.selftext,
            selfTextHtml = dto.selftextHtml,
            url = resolveRedditMediaUrl(decodeHtmlEntities(dto.url)),
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
            subredditIsNsfw = subredditIsNsfw,
            subredditNsfwKnown = subredditNsfwKnown,
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
            galleryData =
                (dto.galleryData ?: dto.crosspostParentList?.firstOrNull()?.galleryData)?.let {
                    mapGallery(it, dto.mediaMetadata ?: dto.crosspostParentList?.firstOrNull()?.mediaMetadata)
                },
            crosspostParent = dto.crosspostParent,
            isCrosspost = dto.crosspostParent != null,
            crosspostParentSubreddit = dto.crosspostParentList?.firstOrNull()?.subreddit,
            crosspostParentPermalink = dto.crosspostParentList?.firstOrNull()?.permalink,
            suggestedSort =
                dto.suggestedSort?.let { sortValue ->
                    CommentSort.entries.firstOrNull { it.value == sortValue }
                },
        )
    }

    private fun mapPreview(dto: PreviewDto): Preview =
        Preview(
            images =
                dto.images.map { img ->
                    PreviewImage(
                        source =
                            ImageSource(
                                url = decodeHtmlEntities(img.source.url),
                                width = img.source.width,
                                height = img.source.height,
                            ),
                        resolutions =
                            img.resolutions.map { res ->
                                ImageSource(
                                    url = decodeHtmlEntities(res.url),
                                    width = res.width,
                                    height = res.height,
                                )
                            },
                        mp4Url =
                            img.variants
                                ?.mp4
                                ?.source
                                ?.url
                                ?.let { decodeHtmlEntities(it) },
                    )
                },
            enabled = dto.enabled,
            redditVideoPreview =
                dto.redditVideoPreview?.let {
                    RedditVideo(
                        fallbackUrl = it.fallbackUrl,
                        height = it.height,
                        width = it.width,
                        duration = it.duration,
                        isGif = it.isGif,
                    )
                },
        )

    private fun mapMedia(dto: MediaDto): Media =
        Media(
            redditVideo =
                dto.redditVideo?.let {
                    RedditVideo(
                        fallbackUrl = it.fallbackUrl,
                        height = it.height,
                        width = it.width,
                        duration = it.duration,
                        isGif = it.isGif,
                    )
                },
        )

    private fun mapGallery(
        dto: GalleryDataDto,
        metadata: Map<String, MediaMetadataDto>?,
    ): GalleryData =
        GalleryData(
            items =
                dto.items.mapNotNull { item ->
                    val meta = metadata?.get(item.mediaId)
                    val isAnimated = meta?.e == "AnimatedImage"
                    val mp4Url = meta?.s?.mp4?.let { decodeHtmlEntities(it) }
                    val gifUrl = meta?.s?.gif?.let { decodeHtmlEntities(it) }
                    val imageUrl = meta?.s?.u?.let { decodeHtmlEntities(it) }
                    val url = if (isAnimated) mp4Url ?: imageUrl else imageUrl
                    if (url != null || metadata == null) {
                        GalleryItem(
                            mediaId = item.mediaId,
                            id = item.id,
                            caption = item.caption,
                            url = url,
                            isVideo = isAnimated && mp4Url != null,
                            gifUrl = gifUrl,
                        )
                    } else {
                        null
                    }
                },
        )

    private fun mapComment(
        dto: CommentDto,
        depth: Int,
    ): Comment {
        val replies = parseReplies(dto.replies, depth + 1)
        val commentReplies = replies.filterIsInstance<CommentOrMore.CommentItem>().map { it.comment }
        val moreReplies = replies.filterIsInstance<CommentOrMore.More>().firstOrNull()?.more

        return Comment(
            id = dto.id,
            name = dto.name,
            parentId = dto.parentId,
            linkId = dto.linkId,
            author = dto.author,
            body = dto.body,
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
            authorFlairRichtext =
                dto.authorFlairRichtext
                    ?.map { rt ->
                        FlairRichtext(
                            type = rt.e,
                            text = rt.t,
                            url = rt.u,
                        )
                    }.orEmpty(),
            scoreHidden = dto.scoreHidden,
            edited = parseEdited(dto.edited),
            subreddit = dto.subreddit,
            permalink = dto.permalink.orEmpty(),
        )
    }

    private fun parseReplies(
        replies: JsonElement?,
        depth: Int,
    ): List<CommentOrMore> {
        if (replies == null) return emptyList()

        return try {
            when {
                replies is JsonPrimitive && replies.isString && replies.content.isEmpty() -> {
                    emptyList()
                }

                replies is JsonObject -> {
                    val data = replies["data"]?.jsonObject
                    val children = data?.get("children")?.jsonArray ?: return emptyList()
                    children.mapNotNull { child ->
                        val childObj = child.jsonObject
                        val kind = childObj["kind"]?.jsonPrimitive?.content
                        val childData = childObj["data"]
                        when (kind) {
                            "t1" -> {
                                childData?.let { CommentOrMore.CommentItem(comment(it, depth)) }
                            }

                            "more" -> {
                                childData?.let {
                                    val more = json.decodeFromJsonElement<MoreCommentsDto>(it)
                                    CommentOrMore.More(parseMoreComments(more))
                                }
                            }

                            else -> {
                                null
                            }
                        }
                    }
                }

                else -> {
                    emptyList()
                }
            }
        } catch (e: IllegalArgumentException) {
            Napier.e("Failed to parse replies", e)
            emptyList()
        }
    }

    private fun parseEdited(edited: JsonElement?): Long? {
        if (edited == null) return null
        return when {
            edited is JsonPrimitive && edited.booleanOrNull == false -> null
            edited is JsonPrimitive && edited.doubleOrNull != null -> edited.double.toLong()
            else -> null
        }
    }

    private fun mapSubreddit(dto: SubredditDto): Subreddit =
        Subreddit(
            id = dto.id,
            name = dto.name,
            displayName = dto.displayName,
            displayNamePrefixed = dto.displayNamePrefixed,
            title = decodeHtmlEntities(dto.title),
            description = dto.description?.let { decodeHtmlEntities(it) },
            descriptionHtml = dto.descriptionHtml,
            publicDescription = dto.publicDescription?.let { decodeHtmlEntities(it) },
            subscribers = dto.subscribers,
            activeUserCount = dto.activeUserCount,
            created = dto.created.toLong(),
            createdUtc = dto.createdUtc.toLong(),
            isNsfw = dto.over18,
            isSubscribed = dto.userIsSubscriber,
            isFavorite = dto.userHasFavorited,
            iconUrl =
                imageUrl(dto.communityIcon) ?: imageUrl(dto.iconImg),
            bannerUrl = imageUrl(dto.bannerImg),
            communityIcon = dto.communityIcon?.let { decodeHtmlEntities(it) },
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
            userIsMuted = dto.userIsMuted,
        )

    private fun mapUser(dto: UserDto): User =
        User(
            id = dto.id,
            name = dto.name,
            created = dto.created.toLong(),
            createdUtc = dto.createdUtc.toLong(),
            linkKarma = dto.linkKarma,
            commentKarma = dto.commentKarma,
            totalKarma = dto.totalKarma,
            iconUrl = imageUrl(dto.iconImg) ?: imageUrl(dto.snoovatarImg),
            bannerUrl = imageUrl(dto.subreddit?.bannerImg),
            isGold = dto.isGold,
            isMod = dto.isMod,
            isVerified = dto.verified,
            hasVerifiedEmail = dto.hasVerifiedEmail,
            isSuspended = dto.isSuspended,
            isEmployee = dto.isEmployee,
            subreddit = dto.subreddit?.let { mapUserSubreddit(it) },
        )

    private fun mapUserSubreddit(dto: UserSubredditDto): UserSubreddit =
        UserSubreddit(
            displayName = dto.displayName,
            title = dto.title.orEmpty(),
            publicDescription = dto.publicDescription,
            subscribers = dto.subscribers,
            iconUrl = imageUrl(dto.iconImg),
            bannerUrl = imageUrl(dto.bannerImg),
            isNsfw = dto.over18,
        )

    private fun mapMessage(dto: MessageDto): Message =
        Message(
            id = dto.id,
            name = dto.name,
            author = dto.author,
            dest = dto.dest,
            subject = decodeHtmlEntities(dto.subject),
            body = decodeHtmlEntities(dto.body),
            bodyHtml = dto.bodyHtml,
            created = dto.created.toLong(),
            createdUtc = dto.createdUtc.toLong(),
            isNew = dto.new,
            wasComment = dto.wasComment,
            context = dto.context,
            subreddit = dto.subreddit,
            parentId = dto.parentId,
            firstMessageName = dto.firstMessageName,
            replies = emptyList(), // Nested replies aren't parsed.
            linkTitle = dto.linkTitle?.let { decodeHtmlEntities(it) },
            likes = dto.likes,
            type =
                when (dto.type) {
                    "comment_reply" -> {
                        MessageType.COMMENT_REPLY
                    }

                    "post_reply" -> {
                        MessageType.POST_REPLY
                    }

                    "username_mention" -> {
                        MessageType.USERNAME_MENTION
                    }

                    else -> {
                        when {
                            dto.wasComment && dto.context?.contains("/comments/") == true -> MessageType.COMMENT_REPLY
                            dto.wasComment -> MessageType.POST_REPLY
                            dto.subreddit != null -> MessageType.MOD_MESSAGE
                            else -> MessageType.PRIVATE_MESSAGE
                        }
                    }
                },
        )

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
                val hex = encoded.substring(i + 1, i + PERCENT_ESCAPE_LENGTH)
                val code = hex.toIntOrNull(16)
                if (code != null) {
                    sb.append(code.toChar())
                    i += PERCENT_ESCAPE_LENGTH
                    continue
                }
            }
            sb.append(encoded[i])
            i++
        }
        return sb.toString()
    }

    // Reddit sends an empty string, not null, for an image that isn't set.
    private fun imageUrl(url: String?): String? = url?.takeIf { it.isNotEmpty() }?.let { decodeHtmlEntities(it) }
}
