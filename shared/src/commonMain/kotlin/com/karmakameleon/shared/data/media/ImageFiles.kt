package com.karmakameleon.shared.data.media

/** An image to upload: its file name, MIME type and contents. */
class ImageFile(
    val name: String,
    val mimeType: String,
    val bytes: ByteArray,
)

/** Why an image couldn't be read, in words the user can read. */
class ImageReadException(
    message: String,
) : Exception(message)

/**
 * Reads images to attach to a comment. A location is whatever the platform hands over, like an Android content URI from
 * the photo picker or the keyboard.
 */
interface ImageFiles {
    /** Throws [ImageReadException] when the file isn't an image or is bigger than [MAX_IMAGE_BYTES], and when it can't be read. */
    suspend fun read(location: String): ImageFile

    companion object {
        // Reddit's limit for images in comments isn't known. 20 MB is its limit for image posts.
        const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
    }
}
