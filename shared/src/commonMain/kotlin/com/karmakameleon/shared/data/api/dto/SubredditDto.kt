package com.karmakameleon.shared.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubredditDto(
    val id: String,
    val name: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("display_name_prefixed") val displayNamePrefixed: String,
    val title: String,
    val description: String? = null,
    @SerialName("description_html") val descriptionHtml: String? = null,
    @SerialName("public_description") val publicDescription: String? = null,
    val subscribers: Long = 0,
    @SerialName("accounts_active") val activeUserCount: Int? = null,
    val created: Double,
    @SerialName("created_utc") val createdUtc: Double,
    @SerialName("over18") val over18: Boolean = false,
    @SerialName("user_is_subscriber") val userIsSubscriber: Boolean = false,
    @SerialName("user_has_favorited") val userHasFavorited: Boolean = false,
    @SerialName("icon_img") val iconImg: String? = null,
    @SerialName("banner_img") val bannerImg: String? = null,
    @SerialName("community_icon") val communityIcon: String? = null,
    @SerialName("primary_color") val primaryColor: String? = null,
    @SerialName("key_color") val keyColor: String? = null,
    val url: String,
    @SerialName("subreddit_type") val subredditType: String = "public",
    @SerialName("allow_images") val allowImages: Boolean = true,
    @SerialName("allow_videos") val allowVideos: Boolean = true,
    @SerialName("allow_polls") val allowPolls: Boolean = true,
    @SerialName("spoilers_enabled") val spoilersEnabled: Boolean = true,
    @SerialName("user_is_moderator") val userIsModerator: Boolean = false,
    @SerialName("user_is_banned") val userIsBanned: Boolean = false,
    @SerialName("user_is_contributor") val userIsContributor: Boolean = false,
    @SerialName("user_is_muted") val userIsMuted: Boolean = false,
)

@Serializable
data class SubredditRuleDto(
    @SerialName("short_name") val shortName: String,
    val description: String? = null,
    @SerialName("description_html") val descriptionHtml: String? = null,
    val priority: Int = 0,
    @SerialName("violation_reason") val violationReason: String = "",
    val kind: String = "all"
)

@Serializable
data class SubredditRulesResponse(
    val rules: List<SubredditRuleDto> = emptyList(),
    @SerialName("site_rules") val siteRules: List<String> = emptyList()
)
