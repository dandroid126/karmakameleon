package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.domain.model.Comment
import com.karmakameleon.shared.domain.model.withProcessedMedia
import com.karmakameleon.shared.util.markdown.MarkdownParser
import com.karmakameleon.shared.util.runSuspendCatching
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class CommentRepository(
    private val redditApi: RedditApi,
    private val markdownParser: MarkdownParser,
) {
    /**
     * Submits a comment. Images uploaded with [CommentImageRepository.upload] go in [text] as `![img](<asset ID>)`, with
     * their IDs in [imageAssetIds]. A comment with images is sent as richtext, which Reddit converts the markdown to.
     */
    suspend fun submitComment(
        parentId: String,
        text: String,
        imageAssetIds: Set<String> = emptySet(),
    ): Result<Comment> =
        runSuspendCatching {
            val submitted =
                if (imageAssetIds.isEmpty()) {
                    redditApi.submitComment(parentId, text)
                } else {
                    val richtext = redditApi.convertToRichtext(text)
                    // Reddit drops image markdown it doesn't recognize, so check rather than post the comment without one.
                    check(richtextImageIds(richtext).containsAll(imageAssetIds)) { "An image is missing from the comment's text" }
                    redditApi.submitRichtextComment(parentId, richtext)
                }
            submitted ?: error("Failed to submit comment")
        }

    suspend fun editComment(
        comment: Comment,
        text: String,
    ): Result<Comment> =
        runSuspendCatching {
            check(redditApi.editComment(comment.name, text)) { "Edit failed" }
            val shown = text.withProcessedMedia(comment.readyMedia)
            comment.copy(body = shown, bodyBlocks = markdownParser.parse(shown), sourceBody = text)
        }

    suspend fun deleteComment(comment: Comment): Result<Unit> =
        runSuspendCatching {
            check(redditApi.deleteComment(comment.name)) { "Delete failed" }
        }
}

// The asset IDs of the images in a richtext document, which are {"e": "img", "id": "<asset ID>"} elements at any depth.
internal fun richtextImageIds(element: JsonElement): Set<String> =
    when (element) {
        is JsonObject -> {
            val id = (element["id"] as? JsonPrimitive)?.content
            val own = if ((element["e"] as? JsonPrimitive)?.content == "img" && id != null) setOf(id) else emptySet()
            own + element.values.flatMap { richtextImageIds(it) }
        }

        is JsonArray -> {
            element.flatMap { richtextImageIds(it) }.toSet()
        }

        else -> {
            emptySet()
        }
    }
