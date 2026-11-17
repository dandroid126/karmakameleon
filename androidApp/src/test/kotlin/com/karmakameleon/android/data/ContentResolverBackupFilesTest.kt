package com.karmakameleon.android.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.os.BundleCompat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
class ContentResolverBackupFilesTest {
    private val provider = Robolectric.setupContentProvider(FakeDocumentsProvider::class.java, AUTHORITY)
    private val contentResolver = RuntimeEnvironment.getApplication().contentResolver
    private val files = ContentResolverBackupFiles(contentResolver)

    private fun location(name: String) = "content://$AUTHORITY/$name"

    @Test
    fun writeThenRead_givesTheText() =
        runBlocking {
            files.write(location("backup.json"), "a much longer first export")
            files.write(location("backup.json"), """{"version":1}""")

            // Writing again replaces all of the old text.
            assertEquals("""{"version":1}""", files.read(location("backup.json")))
        }

    @Test
    fun info_describesTheFile() =
        runBlocking {
            files.write(location("backup.json"), "12345")
            provider.file("backup.json").setLastModified(1_000_000L)

            val info = files.info(location("backup.json"))

            assertEquals("backup.json", info?.name)
            assertEquals(5L, info?.sizeBytes)
            assertEquals(1_000_000L, info?.lastModifiedMillis)
        }

    @Test
    fun info_isNullForAFileThatsGoneOrOutOfReach() =
        runBlocking {
            assertNull(files.info(location("missing.json")))
            assertNull(files.info(location(FakeDocumentsProvider.FORBIDDEN)))
        }

    @Test
    fun delete_deletesTheFile() =
        runBlocking {
            files.write(location("backup.json"), "{}")

            files.delete(location("backup.json"))

            assertFalse(provider.file("backup.json").exists())
        }

    @Test
    fun delete_throwsWhenTheFileCantBeDeleted() {
        assertThrows(FileNotFoundException::class.java) { runBlocking { files.delete(location("missing.json")) } }
    }

    @Test
    fun keepAccess_persistsThePermissionUntilReleased() {
        files.keepAccess(location("backup.json"))
        assertTrue(contentResolver.persistedUriPermissions.any { it.uri.toString() == location("backup.json") })

        files.releaseAccess(location("backup.json"))
        assertTrue(contentResolver.persistedUriPermissions.isEmpty())
    }

    private companion object {
        const val AUTHORITY = "test.documents"
    }
}

/** A document provider that keeps each document as a file in the app's cache. */
class FakeDocumentsProvider : ContentProvider() {
    private val directory by lazy { File(requireNotNull(context).cacheDir, "documents").apply { mkdirs() } }

    fun file(name: String) = File(directory, name)

    private fun file(uri: Uri) = file(requireNotNull(uri.lastPathSegment))

    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (uri.lastPathSegment == FORBIDDEN) throw SecurityException("No access to $uri")
        val cursor = MatrixCursor(projection)
        val file = file(uri)
        if (file.exists()) cursor.addRow(arrayOf<Any>(file.name, file.length(), file.lastModified()))
        return cursor
    }

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor = ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.parseMode(mode))

    // How DocumentsContract.deleteDocument reaches a provider. Both names are hidden constants of DocumentsContract.
    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        if (method == "android:deleteDocument") {
            val uri = requireNotNull(extras?.let { BundleCompat.getParcelable(it, "uri", Uri::class.java) })
            if (!file(uri).delete()) throw FileNotFoundException("No document at $uri")
        }
        return null
    }

    override fun getType(uri: Uri) = "application/json"

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0

    companion object {
        const val FORBIDDEN = "forbidden.json"
    }
}
