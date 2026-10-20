package com.karmakameleon.shared.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val name: String,
    val created: Double,
    @SerialName("created_utc") val createdUtc: Double,
    @SerialName("link_karma") val linkKarma: Int = 0,
    @SerialName("comment_karma") val commentKarma: Int = 0,
    @SerialName("total_karma") val totalKarma: Int = 0,
    @SerialName("icon_img") val iconImg: String? = null,
    @SerialName("snoovatar_img") val snoovatarImg: String? = null,
    @SerialName("is_gold") val isGold: Boolean = false,
    @SerialName("is_mod") val isMod: Boolean = false,
    val verified: Boolean = false,
    @SerialName("has_verified_email") val hasVerifiedEmail: Boolean = false,
    @SerialName("is_suspended") val isSuspended: Boolean = false,
    @SerialName("is_employee") val isEmployee: Boolean = false,
    val subreddit: UserSubredditDto? = null,
)

@Serializable
data class UserSubredditDto(
    @SerialName("display_name") val displayName: String,
    val title: String? = null,
    @SerialName("public_description") val publicDescription: String? = null,
    val subscribers: Int = 0,
    @SerialName("icon_img") val iconImg: String? = null,
    @SerialName("banner_img") val bannerImg: String? = null,
    @SerialName("over_18") val over18: Boolean = false,
)
