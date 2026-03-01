package com.karmakameleon.shared.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccessTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String,
)

@Serializable
data class TokenInfo(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
    val scope: String,
)
