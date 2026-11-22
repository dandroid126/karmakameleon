package com.karmakameleon.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Subreddit(
    val id: String,
    val name: String, // fullname e.g., t5_xxxxx
    val displayName: String,
    val displayNamePrefixed: String,
    val title: String,
    val description: String?,
    val descriptionHtml: String?,
    val publicDescription: String?,
    val subscribers: Long,
    val activeUserCount: Int?,
    val created: Long,
    val createdUtc: Long,
    val isNsfw: Boolean,
    val isSubscribed: Boolean,
    val isFavorite: Boolean,
    val iconUrl: String?,
    val bannerUrl: String?,
    val communityIcon: String?,
    val primaryColor: String?,
    val keyColor: String?,
    val url: String,
    val subredditType: String, // public, private, restricted
    val allowImages: Boolean,
    val allowVideos: Boolean,
    val allowPolls: Boolean,
    val spoilersEnabled: Boolean,
    val userIsModerator: Boolean,
    val userIsBanned: Boolean,
    val userIsContributor: Boolean,
    val userIsMuted: Boolean,
    // What comments can hold, like "static" (uploaded images) and "animated" (uploaded GIFs). Also seen: "giphy", "video"
    // and "expression", whose meaning isn't known. A user's profile can have none.
    val allowedMediaInComments: Set<String> = emptySet(),
) {
    /** Whether a comment here can have an uploaded image of this MIME type: a GIF needs "animated", any other image "static". */
    fun allowsCommentImage(mimeType: String): Boolean =
        when {
            mimeType.equals("image/gif", ignoreCase = true) -> "animated" in allowedMediaInComments
            mimeType.startsWith("image/", ignoreCase = true) -> "static" in allowedMediaInComments
            else -> false
        }

    val isPublic: Boolean get() = subredditType == "public"
    val isPrivate: Boolean get() = subredditType == "private"
    val isRestricted: Boolean get() = subredditType == "restricted"
}
