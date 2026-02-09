package com.reader.shared.domain.model

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

@Serializable
data class Account(
    val id: String,
    val name: String,
    val created: Long,
    val createdUtc: Long,
    val linkKarma: Int,
    val commentKarma: Int,
    val totalKarma: Int,
    val iconUrl: String?,
    val inboxCount: Int,
    val hasMail: Boolean,
    val hasModMail: Boolean,
    val isGold: Boolean,
    val isMod: Boolean,
    val numFriends: Int,
    val over18: Boolean,
    val prefNightMode: Boolean,
    val prefShowNsfw: Boolean,
    val prefNoProfanity: Boolean,
)

@Serializable
data class Trophy(
    val name: String,
    val description: String?,
    val iconUrl: String?,
    val awardId: String?,
)

@Serializable
data class KarmaBreakdown(
    val subreddit: String,
    val linkKarma: Int,
    val commentKarma: Int,
)
