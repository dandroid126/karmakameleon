package com.karmakameleon.android.data

import android.content.ContentResolver
import android.content.Intent
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.karmakameleon.shared.data.backup.BackupFileInfo
import com.karmakameleon.shared.data.backup.BackupFiles
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/** Export files at the content URIs that Android's file pickers return. */
class ContentResolverBackupFiles(
    private val contentResolver: ContentResolver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BackupFiles {
    override suspend fun write(
        location: String,
        text: String,
    ) = withContext(dispatcher) {
        // "wt" truncates, so a shorter export doesn't leave the end of an older one behind.
        val stream = contentResolver.openOutputStream(location.toUri(), "wt") ?: throw FileNotFoundException(location)
        stream.bufferedWriter().use { it.write(text) }
    }

    override suspend fun read(location: String): String =
        withContext(dispatcher) {
            val stream = contentResolver.openInputStream(location.toUri()) ?: throw FileNotFoundException(location)
            stream.bufferedReader().use { it.readText() }
        }

    override fun keepAccess(location: String) {
        contentResolver.takePersistableUriPermission(location.toUri(), ACCESS_FLAGS)
    }

    override fun releaseAccess(location: String) {
        contentResolver.releasePersistableUriPermission(location.toUri(), ACCESS_FLAGS)
    }

    override suspend fun info(location: String): BackupFileInfo? =
        withContext(dispatcher) {
            val columns =
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            // A file that's gone or out of reach makes the query throw, or return no rows.
            val cursor =
                try {
                    contentResolver.query(location.toUri(), columns, null, null, null)
                } catch (_: SecurityException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                } ?: return@withContext null
            cursor.use {
                if (!it.moveToFirst()) return@withContext null
                BackupFileInfo(
                    location = location,
                    name = it.getString(0) ?: location.toUri().lastPathSegment.orEmpty(),
                    sizeBytes = if (it.isNull(1)) null else it.getLong(1),
                    lastModifiedMillis = if (it.isNull(2)) null else it.getLong(2),
                )
            }
        }

    override suspend fun delete(location: String) {
        withContext(dispatcher) {
            if (!DocumentsContract.deleteDocument(contentResolver, location.toUri())) {
                throw FileNotFoundException(location)
            }
        }
    }

    private companion object {
        const val ACCESS_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
