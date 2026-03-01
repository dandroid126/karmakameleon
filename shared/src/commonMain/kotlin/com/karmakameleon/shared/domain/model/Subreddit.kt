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
) {
    val isPublic: Boolean get() = subredditType == "public"
    val isPrivate: Boolean get() = subredditType == "private"
    val isRestricted: Boolean get() = subredditType == "restricted"
}

@Serializable
data class SubredditRule(
    val shortName: String,
    val description: String?,
    val descriptionHtml: String?,
    val priority: Int,
    val violationReason: String,
    val kind: String // link, comment, all
)
