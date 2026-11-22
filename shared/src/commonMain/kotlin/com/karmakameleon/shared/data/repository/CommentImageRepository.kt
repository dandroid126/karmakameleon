package com.karmakameleon.shared.data.repository

import com.karmakameleon.shared.data.api.RedditApi
import com.karmakameleon.shared.data.media.ImageFile
import com.karmakameleon.shared.data.media.ImageFiles
import com.karmakameleon.shared.data.media.ImageReadException
import com.karmakameleon.shared.util.runSuspendCatching
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable

/** Reads and uploads the images that go in a comment. [CommentRepository.submitComment] posts the comment with them. */
class CommentImageRepository(
    private val redditApi: RedditApi,
    private val imageFiles: ImageFiles,
    private val httpClient: HttpClient,
    private val maxBytes: Int = ImageFiles.MAX_IMAGE_BYTES,
) {
    /**
     * Reads an image to attach. Read it as soon as it arrives and keep the result for retries: the app's permission to
     * read something the keyboard sent can end soon after.
     */
    suspend fun read(location: String): Result<ImageFile> = runSuspendCatching { imageFiles.read(location) }

    /**
     * Downloads the image at a web address, to upload it to Reddit: a comment can only show images uploaded there. Fails
     * with [ImageReadException] when the address isn't a web link, or doesn't give an image within the size limit.
     */
    suspend fun download(url: String): Result<ImageFile> =
        runSuspendCatching {
            // Url() reads anything, taking a bare word as a path and filling in localhost, so check the scheme and host first.
            val trimmed = url.trim()
            val hasScheme = trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)
            if (!hasScheme || trimmed.substringAfter("://").substringBefore('/').isBlank()) throw ImageReadException(NOT_A_LINK)
            val address = Url(trimmed)
            httpClient.prepareGet(address).execute { response ->
                if (!response.status.isSuccess()) throw ImageReadException("Couldn't download the image (HTTP ${response.status.value})")
                val mimeType = response.contentType()?.withoutParameters()?.toString()
                if (mimeType == null || !mimeType.startsWith("image/")) throw ImageReadException("That link isn't an image")
                if ((response.contentLength() ?: 0) > maxBytes) throw tooBig()
                val bytes = readAtMost(response.bodyAsChannel())
                val lastSegment = address.segments.lastOrNull().orEmpty()
                ImageFile(
                    name = lastSegment.takeIf { '.' in it } ?: "image.${mimeType.substringAfter('/')}",
                    mimeType = mimeType,
                    bytes = bytes,
                )
            }
        }

    /** Uploads an image, and returns the asset ID to put in the comment's markdown as `![img](<asset ID>)`. */
    suspend fun upload(image: ImageFile): Result<String> =
        runSuspendCatching { redditApi.uploadCommentImage(image.name, image.mimeType, image.bytes) }

    // Stops reading past the limit, so a server that sends more than it said can't fill the memory.
    private suspend fun readAtMost(channel: ByteReadChannel): ByteArray {
        var buffer = ByteArray(INITIAL_BUFFER_SIZE)
        var size = 0
        while (true) {
            if (size == buffer.size) buffer = buffer.copyOf(minOf(buffer.size * 2, maxBytes + 1))
            val count = channel.readAvailable(buffer, size, buffer.size - size)
            if (count < 0) break
            size += count
            if (size > maxBytes) throw tooBig()
        }
        return buffer.copyOf(size)
    }

    private fun tooBig() = ImageReadException("The image is bigger than ${maxBytes / BYTES_PER_MB} MB")

    private companion object {
        const val BYTES_PER_MB = 1024 * 1024
        const val INITIAL_BUFFER_SIZE = 64 * 1024
        const val NOT_A_LINK = "Enter a link that starts with https://"
    }
}
