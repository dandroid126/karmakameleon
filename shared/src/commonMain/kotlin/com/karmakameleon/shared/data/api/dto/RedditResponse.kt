package com.karmakameleon.shared.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class RedditResponse<T>(
    val kind: String,
    val data: T
)

@Serializable
data class ListingData(
    val after: String? = null,
    val before: String? = null,
    val dist: Int? = null,
    val modhash: String? = null,
    val children: List<ThingData> = emptyList()
)

@Serializable
data class ThingData(
    val kind: String,
    val data: JsonElement
)

@Serializable
data class PostDto(
    val id: String,
    val name: String,
    val title: String,
    val author: String,
    val subreddit: String,
    @SerialName("subreddit_id") val subredditId: String,
    val selftext: String? = null,
    @SerialName("selftext_html") val selftextHtml: String? = null,
    val url: String,
    val permalink: String,
    val thumbnail: String? = null,
    @SerialName("thumbnail_width") val thumbnailWidth: Int? = null,
    @SerialName("thumbnail_height") val thumbnailHeight: Int? = null,
    val preview: PreviewDto? = null,
    val score: Int,
    @SerialName("upvote_ratio") val upvoteRatio: Float = 0f,
    @SerialName("num_comments") val numComments: Int,
    val created: Double,
    @SerialName("created_utc") val createdUtc: Double,
    @SerialName("over_18") val over18: Boolean = false,
    val spoiler: Boolean = false,
    val stickied: Boolean = false,
    val locked: Boolean = false,
    val archived: Boolean = false,
    val saved: Boolean = false,
    val hidden: Boolean = false,
    val likes: Boolean? = null,
    val domain: String,
    @SerialName("post_hint") val postHint: String? = null,
    @SerialName("link_flair_text") val linkFlairText: String? = null,
    @SerialName("link_flair_background_color") val linkFlairBackgroundColor: String? = null,
    @SerialName("link_flair_text_color") val linkFlairTextColor: String? = null,
    @SerialName("author_flair_text") val authorFlairText: String? = null,
    val distinguished: String? = null,
    val media: MediaDto? = null,
    @SerialName("gallery_data") val galleryData: GalleryDataDto? = null,
    @SerialName("media_metadata") val mediaMetadata: Map<String, MediaMetadataDto>? = null,
    @SerialName("crosspost_parent") val crosspostParent: String? = null,
    @SerialName("crosspost_parent_list") val crosspostParentList: List<PostDto>? = null,
    @SerialName("is_gallery") val isGallery: Boolean = false,
    @SerialName("is_video") val isVideo: Boolean = false,
    @SerialName("is_self") val isSelf: Boolean = false,
)

@Serializable
data class PreviewDto(
    val images: List<PreviewImageDto> = emptyList(),
    val enabled: Boolean = false,
    @SerialName("reddit_video_preview") val redditVideoPreview: RedditVideoDto? = null
)

@Serializable
data class PreviewImageDto(
    val source: ImageSourceDto,
    val resolutions: List<ImageSourceDto> = emptyList(),
    val variants: PreviewVariantsDto? = null
)

@Serializable
data class PreviewVariantsDto(
    val mp4: PreviewVariantDto? = null
)

@Serializable
data class PreviewVariantDto(
    val source: ImageSourceDto? = null
)

@Serializable
data class ImageSourceDto(
    val url: String,
    val width: Int,
    val height: Int
)

@Serializable
data class MediaDto(
    @SerialName("reddit_video") val redditVideo: RedditVideoDto? = null
)

@Serializable
data class RedditVideoDto(
    @SerialName("fallback_url") val fallbackUrl: String,
    val height: Int,
    val width: Int,
    val duration: Int = 0,
    @SerialName("is_gif") val isGif: Boolean = false
)

@Serializable
data class GalleryDataDto(
    val items: List<GalleryItemDto> = emptyList()
)

@Serializable
data class GalleryItemDto(
    @SerialName("media_id") val mediaId: String,
    val id: Long,
    val caption: String? = null
)

@Serializable
data class MediaMetadataDto(
    val status: String? = null,
    val e: String? = null,
    val m: String? = null,
    val s: MediaMetadataSourceDto? = null,
    val p: List<MediaMetadataSourceDto>? = null
)

@Serializable
data class MediaMetadataSourceDto(
    val y: Int = 0,
    val x: Int = 0,
    val u: String? = null,
    val mp4: String? = null,
    val gif: String? = null
)
