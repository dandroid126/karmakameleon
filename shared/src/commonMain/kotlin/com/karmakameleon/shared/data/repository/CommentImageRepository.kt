package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.data.media.ImageFile
import com.karmakameleon.shared.data.media.ImageFiles
import com.karmakameleon.shared.util.runSuspendCatching

/** Reads and uploads the images that go in a comment. [CommentRepository.submitComment] posts the comment with them. */
class CommentImageRepository(
    private val redditApi: RedditApi,
    private val imageFiles: ImageFiles,
) {
    /**
     * Reads an image to attach. Read it as soon as it arrives and keep the result for retries: the app's permission to
     * read something the keyboard sent can end soon after.
     */
    suspend fun read(location: String): Result<ImageFile> = runSuspendCatching { imageFiles.read(location) }

    /** Uploads an image, and returns the asset ID to put in the comment's markdown as `![img](<asset ID>)`. */
    suspend fun upload(image: ImageFile): Result<String> =
        runSuspendCatching { redditApi.uploadCommentImage(image.name, image.mimeType, image.bytes) }
}
