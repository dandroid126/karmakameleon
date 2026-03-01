package com.karmakameleon.shared.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class CommentDto(
    val id: String,
    val name: String,
    @SerialName("parent_id") val parentId: String,
    @SerialName("link_id") val linkId: String,
    val author: String,
    val body: String,
    @SerialName("body_html") val bodyHtml: String,
    val score: Int = 0,
    val created: Double,
    @SerialName("created_utc") val createdUtc: Double,
    @SerialName("is_submitter") val isSubmitter: Boolean = false,
    val distinguished: String? = null,
    val stickied: Boolean = false,
    val locked: Boolean = false,
    val archived: Boolean = false,
    val saved: Boolean = false,
    val collapsed: Boolean = false,
    val likes: Boolean? = null,
    val depth: Int = 0,
    val replies: JsonElement? = null, // Can be empty string or object
    @SerialName("author_flair_text") val authorFlairText: String? = null,
    @SerialName("author_flair_background_color") val authorFlairBackgroundColor: String? = null,
    @SerialName("author_flair_richtext") val authorFlairRichtext: List<FlairRichtextDto>? = null,
    @SerialName("score_hidden") val scoreHidden: Boolean = false,
    val edited: JsonElement? = null, // Can be boolean false or timestamp
    val subreddit: String,
    val permalink: String? = null,
)

@Serializable
data class FlairRichtextDto(
    val e: String, // "text" or "emoji"
    val t: String? = null, // text content
    val a: String? = null, // emoji shortcode like ":61910:"
    val u: String? = null  // emoji image URL
)

@Serializable
data class MoreCommentsDto(
    val id: String,
    val name: String,
    @SerialName("parent_id") val parentId: String,
    val count: Int = 0,
    val depth: Int = 0,
    val children: List<String> = emptyList()
)

@Serializable
data class MoreChildrenResponse(
    val json: MoreChildrenJson
)

@Serializable
data class MoreChildrenJson(
    val errors: List<JsonElement> = emptyList(),
    val data: MoreChildrenData? = null
)

@Serializable
data class MoreChildrenData(
    val things: List<ThingData> = emptyList()
)
