package com.karmakameleon.android.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.karmakameleon.shared.data.media.ImageFile
import com.karmakameleon.shared.data.media.ImageFiles
import com.karmakameleon.shared.data.media.ImageReadException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** Images at the content URIs that the photo picker and the keyboard hand over. */
class ContentResolverImageFiles(
    private val contentResolver: ContentResolver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxBytes: Int = ImageFiles.MAX_IMAGE_BYTES,
) : ImageFiles {
    override suspend fun read(location: String): ImageFile =
        withContext(dispatcher) {
            val uri = location.toUri()
            try {
                val mimeType = contentResolver.getType(uri)?.takeIf { it.startsWith("image/") } ?: throw ImageReadException(NOT_AN_IMAGE)
                val bytes = contentResolver.openInputStream(uri)?.use { it.readAtMost(maxBytes) } ?: throw ImageReadException(CANT_READ)
                ImageFile(name = displayName(uri) ?: "image.${mimeType.substringAfter('/')}", mimeType = mimeType, bytes = bytes)
            } catch (_: IOException) {
                throw ImageReadException(CANT_READ)
            } catch (_: SecurityException) {
                // The permission to read the file ended, like for an image the keyboard sent a while ago.
                throw ImageReadException(CANT_READ)
            }
        }

    // Not every provider has a name for its files, and some don't answer the query at all.
    private fun displayName(uri: Uri): String? =
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getString(0).takeIf(String::isNotBlank) else null
            }
        } catch (_: IllegalArgumentException) {
            null
        }

    // Stops reading past the limit, so a huge file doesn't fill the memory first.
    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) throw ImageReadException("The image is bigger than ${limit / BYTES_PER_MB} MB")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val BYTES_PER_MB = 1024 * 1024
        const val NOT_AN_IMAGE = "That file isn't an image"
        const val CANT_READ = "Couldn't read the image"
    }
}
