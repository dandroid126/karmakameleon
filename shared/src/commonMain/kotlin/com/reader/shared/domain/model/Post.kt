package com.reader.shared.domain.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Post(
    val id: String,
    val name: String, // fullname e.g., t3_xxxxx
    val title: String,
    val author: String,
    val subreddit: String,
    val subredditId: String,
    val selfText: String?,
    val selfTextHtml: String?,
    val url: String,
    val permalink: String,
    val thumbnail: String?,
    val thumbnailWidth: Int?,
    val thumbnailHeight: Int?,
    val preview: Preview?,
    val score: Int,
    val upvoteRatio: Float,
    val numComments: Int,
    val created: Long,
    val createdUtc: Long,
    val isNsfw: Boolean,
    val subredditIsNsfw: Boolean = false,
    val subredditNsfwKnown: Boolean = false,
    val isSpoiler: Boolean,
    val isStickied: Boolean,
    val isLocked: Boolean,
    val isArchived: Boolean,
    val isSaved: Boolean,
    val isHidden: Boolean,
    val likes: Boolean?, // null = no vote, true = upvote, false = downvote
    val domain: String,
    val postHint: String?,
    val linkFlairText: String?,
    val linkFlairBackgroundColor: String?,
    val linkFlairTextColor: String?,
    val authorFlairText: String?,
    val distinguished: String?, // moderator, admin, etc.
    val media: Media?,
    val galleryData: GalleryData?,
    val crosspostParent: String?,
    val isCrosspost: Boolean = false,
    val crosspostParentSubreddit: String? = null,
    val crosspostParentPermalink: String? = null,
) {
    val isTextPost: Boolean get() = postHint == "self" || url.contains("reddit.com") && selfText != null
    val isImagePost: Boolean get() = postHint == "image" || url.endsWith(".jpg") || url.endsWith(".jpeg") || url.endsWith(".png") || url.endsWith(".gif")
    val isVideoPost: Boolean get() = postHint == "hosted:video" || postHint == "rich:video"
    val isLinkPost: Boolean get() = !isTextPost && !isImagePost && !isVideoPost && !isGallery
    val isGallery: Boolean get() = galleryData != null
    
    val voteState: VoteState get() = when (likes) {
        true -> VoteState.UPVOTED
        false -> VoteState.DOWNVOTED
        null -> VoteState.NONE
    }
    
    val createdInstant: Instant get() = Instant.fromEpochSeconds(createdUtc)
}

@Serializable
data class Preview(
    val images: List<PreviewImage>,
    val enabled: Boolean,
    val redditVideoPreview: RedditVideo? = null
)

@Serializable
data class PreviewImage(
    val source: ImageSource,
    val resolutions: List<ImageSource>,
    val mp4Url: String? = null
)

@Serializable
data class ImageSource(
    val url: String,
    val width: Int,
    val height: Int
)

@Serializable
data class Media(
    val redditVideo: RedditVideo?
)

@Serializable
data class RedditVideo(
    val fallbackUrl: String,
    val height: Int,
    val width: Int,
    val duration: Int,
    val isGif: Boolean
)

@Serializable
data class GalleryData(
    val items: List<GalleryItem>
)

@Serializable
data class GalleryItem(
    val mediaId: String,
    val id: Long,
    val caption: String?,
    val url: String? = null,
    val isVideo: Boolean = false
)

enum class VoteState {
    NONE, UPVOTED, DOWNVOTED
}

enum class PostSort(val value: String) {
    HOT("hot"),
    NEW("new"),
    TOP("top"),
    RISING("rising"),
    CONTROVERSIAL("controversial"),
    BEST("best")
}

enum class TimeFilter(val value: String) {
    HOUR("hour"),
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year"),
    ALL("all")
}
