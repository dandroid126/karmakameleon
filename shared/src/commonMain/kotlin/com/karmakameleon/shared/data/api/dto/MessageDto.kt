package com.karmakameleon.shared.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class MessageDto(
    val id: String,
    val name: String,
    val author: String? = null,
    val dest: String = "",
    val subject: String,
    val body: String,
    @SerialName("body_html") val bodyHtml: String,
    val created: Double,
    @SerialName("created_utc") val createdUtc: Double,
    val new: Boolean = false,
    @SerialName("was_comment") val wasComment: Boolean = false,
    val context: String? = null,
    val subreddit: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("first_message_name") val firstMessageName: String? = null,
    val replies: JsonElement? = null,
    val type: String? = null,
    @SerialName("link_title") val linkTitle: String? = null,
    val likes: Boolean? = null,
)
