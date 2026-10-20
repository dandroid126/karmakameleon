package com.karmakameleon.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val created: Long,
    val createdUtc: Long,
    val linkKarma: Int,
    val commentKarma: Int,
    val totalKarma: Int,
    val iconUrl: String?,
    val bannerUrl: String?,
    val isGold: Boolean,
    val isMod: Boolean,
    val isVerified: Boolean,
    val hasVerifiedEmail: Boolean,
    val isSuspended: Boolean,
    val isEmployee: Boolean,
    val subreddit: UserSubreddit?,
)

@Serializable
data class UserSubreddit(
    val displayName: String,
    val title: String,
    val publicDescription: String?,
    val subscribers: Int,
    val iconUrl: String?,
    val bannerUrl: String?,
    val isNsfw: Boolean,
)
