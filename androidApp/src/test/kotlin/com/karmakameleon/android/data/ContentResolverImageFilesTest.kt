package com.karmakameleon.android.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.karmakameleon.shared.data.media.ImageReadException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ContentResolverImageFilesTest {
    private val provider = Robolectric.setupContentProvider(FakeImageProvider::class.java, AUTHORITY)
    private val files = ContentResolverImageFiles(RuntimeEnvironment.getApplication().contentResolver, maxBytes = 10)

    private fun location(name: String) = "content://$AUTHORITY/$name"

    private fun readFails(name: String): String =
        assertThrows(ImageReadException::class.java) { runBlocking { files.read(location(name)) } }.message!!

    @Test
    fun read_givesTheNameTypeAndBytes() =
        runBlocking {
            provider.file("photo.jpeg").writeBytes(byteArrayOf(1, 2, 3))

            val image = files.read(location("photo.jpeg"))

            assertEquals("photo.jpeg", image.name)
            assertEquals("image/jpeg", image.mimeType)
            assertArrayEquals(byteArrayOf(1, 2, 3), image.bytes)
        }

    @Test
    fun read_namesAFileTheProviderHasNoNameFor() =
        runBlocking {
            provider.file("${FakeImageProvider.NAMELESS}.gif").writeBytes(byteArrayOf(1))

            assertEquals("image.gif", files.read(location("${FakeImageProvider.NAMELESS}.gif")).name)
        }

    @Test
    fun read_acceptsAnImageOfExactlyTheLimit() =
        runBlocking {
            provider.file("photo.png").writeBytes(ByteArray(10))

            assertEquals(10, files.read(location("photo.png")).bytes.size)
        }

    @Test
    fun read_refusesAnImageOverTheLimit() {
        provider.file("photo.png").writeBytes(ByteArray(11))

        assertEquals("The image is bigger than 0 MB", readFails("photo.png"))
    }

    @Test
    fun read_refusesWhatIsntAnImage() {
        provider.file("song.mp3").writeBytes(byteArrayOf(1))

        assertEquals("That file isn't an image", readFails("song.mp3"))
    }

    @Test
    fun read_failsForAFileThatsGoneOrOutOfReach() {
        assertEquals("Couldn't read the image", readFails("missing.png"))
        assertEquals("Couldn't read the image", readFails("${FakeImageProvider.FORBIDDEN}.png"))
    }

    private companion object {
        const val AUTHORITY = "com.karmakameleon.test.images"
    }
}

/** Serves files from its cache directory, typed by their extension, like the photo picker's and keyboards' providers. */
class FakeImageProvider : ContentProvider() {
    private val directory by lazy { File(requireNotNull(context).cacheDir, "images").apply { mkdirs() } }

    fun file(name: String) = File(directory, name)

    private fun file(uri: Uri) = file(requireNotNull(uri.lastPathSegment))

    override fun onCreate() = true

    override fun getType(uri: Uri): String? =
        when (uri.lastPathSegment?.substringAfterLast('.')) {
            "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp3" -> "audio/mpeg"
            else -> null
        }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val cursor = MatrixCursor(projection)
        if (uri.lastPathSegment?.startsWith(NAMELESS) == false) cursor.addRow(arrayOf<Any>(file(uri).name))
        return cursor
    }

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        if (uri.lastPathSegment?.startsWith(FORBIDDEN) == true) throw SecurityException("No access to $uri")
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.parseMode(mode))
    }

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    companion object {
        const val NAMELESS = "nameless"
        const val FORBIDDEN = "forbidden"
    }
}
